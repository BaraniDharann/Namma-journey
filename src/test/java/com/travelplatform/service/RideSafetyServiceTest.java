package com.travelplatform.service;

import com.travelplatform.config.SafetyProperties;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.EmergencyContact;
import com.travelplatform.entity.Notification;
import com.travelplatform.entity.RideLocationPoint;
import com.travelplatform.entity.RideSafetyCheck;
import com.travelplatform.entity.RideSafetySession;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.entity.User;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.EmergencyContactRepository;
import com.travelplatform.repository.NotificationRepository;
import com.travelplatform.repository.RideLocationPointRepository;
import com.travelplatform.repository.RideSafetyCheckRepository;
import com.travelplatform.repository.RideSafetySessionRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The safety rules that matter most if they break: an answer only counts from the passenger
 * whose trip it is, Unsafe reaches the owner and the contacts, silence is escalated, the
 * passenger is never promised police, and long outstation trips are not pestered.
 */
class RideSafetyServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID BOOKING_ID = UUID.randomUUID();
    private static final String RIDER_CHAT = "111";
    private static final String CONTACT_CHAT = "222";

    private RideSafetySessionRepository sessions;
    private RideSafetyCheckRepository checks;
    private RideLocationPointRepository trail;
    private EmergencyContactRepository contacts;
    private TravelBookingRepository bookings;
    private UserRepository users;
    private NotificationRepository notifications;
    private LocationTrackingService locations;
    private TelegramClient telegram;
    private SafetyProperties props;
    private RideSafetyService service;

    private TravelBooking booking;
    private RideSafetySession session;

    @BeforeEach
    void setUp() {
        sessions = mock(RideSafetySessionRepository.class);
        checks = mock(RideSafetyCheckRepository.class);
        trail = mock(RideLocationPointRepository.class);
        contacts = mock(EmergencyContactRepository.class);
        bookings = mock(TravelBookingRepository.class);
        users = mock(UserRepository.class);
        DriverRepository drivers = mock(DriverRepository.class);
        notifications = mock(NotificationRepository.class);
        locations = mock(LocationTrackingService.class);
        telegram = mock(TelegramClient.class);
        props = new SafetyProperties();
        props.setOpsChatId("-100999");

        // Sends run inline, as if the transaction had just committed.
        AfterCommitDispatcher dispatcher = mock(AfterCommitDispatcher.class);
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(1)).run();
            return null;
        }).when(dispatcher).dispatch(anyString(), any());

        service = new RideSafetyService(sessions, checks, trail, contacts, bookings, users, drivers,
                notifications, locations, telegram, dispatcher, props, mock(PlatformTransactionManager.class));

        booking = new TravelBooking();
        booking.setId(BOOKING_ID);
        booking.setUserId(USER_ID);
        booking.setUserName("Priya Raman");
        booking.setFromPlace("Koramangala");
        booking.setToPlace("Airport");
        booking.setToLat(13.1986);
        booking.setToLon(77.7066);
        booking.setDriverId(5L);
        booking.setStatus(TravelBooking.BookingStatus.STARTED);
        booking.setServiceMode(TravelBooking.ServiceMode.CITY);
        booking.setEstimatedTimeMinutes(40L);
        when(bookings.findById(BOOKING_ID)).thenReturn(Optional.of(booking));

        User rider = new User();
        rider.setId(USER_ID);
        rider.setName("Priya Raman");
        rider.setTelegramChatId(RIDER_CHAT);
        when(users.findById(USER_ID)).thenReturn(Optional.of(rider));
        when(users.findByTelegramChatId(RIDER_CHAT)).thenReturn(Optional.of(rider));

        Driver driver = new Driver();
        driver.setId(5L);
        driver.setName("Murugan");
        driver.setMobile("9876543210");
        driver.setVehicleNumber("KA01AB1234");
        when(drivers.findById(5L)).thenReturn(Optional.of(driver));

        session = new RideSafetySession();
        session.setBookingId(BOOKING_ID);
        session.setUserId(USER_ID);
        session.setShareToken("tok");
        session.setStartedAt(LocalDateTime.now().minusMinutes(15));
        session.setNextCheckAt(LocalDateTime.now().plusMinutes(10));
        session.setLastPointAt(LocalDateTime.now().minusSeconds(10));
        when(sessions.findById(BOOKING_ID)).thenReturn(Optional.of(session));
        when(sessions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(checks.save(any())).thenAnswer(inv -> {
            RideSafetyCheck c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(77L);
            }
            return c;
        });

        EmergencyContact mum = new EmergencyContact();
        mum.setUserId(USER_ID);
        mum.setName("Amma");
        mum.setTelegramChatId(CONTACT_CHAT);
        EmergencyContact notJoined = new EmergencyContact();
        notJoined.setUserId(USER_ID);
        notJoined.setName("Friend");
        when(contacts.findByUserIdOrderByCreatedAtAsc(USER_ID)).thenReturn(List.of(mum, notJoined));
    }

    private RideSafetyCheck openCheck(LocalDateTime sentAt) {
        RideSafetyCheck check = new RideSafetyCheck();
        check.setId(77L);
        check.setBookingId(BOOKING_ID);
        check.setReason(RideSafetyCheck.Reason.SCHEDULED);
        check.setSentAt(sentAt);
        when(checks.findById(77L)).thenReturn(Optional.of(check));
        when(checks.findFirstByBookingIdAndResponseIsNullOrderBySentAtDesc(BOOKING_ID)).thenReturn(Optional.of(check));
        return check;
    }

    private List<Notification> savedNotifications() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("Safe from the passenger closes the check and alerts nobody")
    void safeClosesTheCheck() {
        RideSafetyCheck check = openCheck(LocalDateTime.now());

        service.answerFromTelegram(RIDER_CHAT, "safe:77", 9L);

        assertEquals(RideSafetyCheck.Response.SAFE, check.getResponse());
        assertNull(session.getSosAt());
        verify(notifications, never()).save(any());
    }

    @Test
    @DisplayName("Unsafe raises SOS: owner notified, linked contacts alerted, passenger told to call 112")
    void unsafeRaisesSos() {
        RideSafetyCheck check = openCheck(LocalDateTime.now());

        service.answerFromTelegram(RIDER_CHAT, "unsafe:77", 9L);

        assertEquals(RideSafetyCheck.Response.UNSAFE, check.getResponse());
        assertNotNull(session.getSosAt());
        assertTrue(savedNotifications().stream().anyMatch(n -> "SOS".equals(n.getType()) && "owner".equals(n.getRecipientId())));
        verify(telegram).sendMessage(eq("-100999"), any(), anyList());
        verify(telegram).sendMessage(eq(CONTACT_CHAT), any(), anyList());

        ArgumentCaptor<String> toRider = ArgumentCaptor.forClass(String.class);
        verify(telegram, atLeastOnce()).sendMessage(eq(RIDER_CHAT), toRider.capture(), anyList());
        String reply = String.join("\n", toRider.getAllValues());
        assertTrue(reply.contains("112"));
        // We cannot dispatch police, so we must never say they are coming.
        assertFalse(reply.toLowerCase().contains("police will"));
    }

    @Test
    @DisplayName("A second SOS alerts the owner again but does not re-alarm the family")
    void repeatedSosSparesContacts() {
        session.setSosAt(LocalDateTime.now().minusMinutes(1));
        when(checks.findFirstByBookingIdAndResponseIsNullOrderBySentAtDesc(BOOKING_ID)).thenReturn(Optional.empty());

        service.sos(USER_ID, BOOKING_ID);

        verify(telegram).sendMessage(eq("-100999"), any(), anyList());
        verify(telegram, never()).sendMessage(eq(CONTACT_CHAT), any(), anyList());
    }

    @Test
    @DisplayName("An answer from a Telegram account that is not the trip's passenger is refused")
    void answerFromAnotherPassengerIsRefused() {
        RideSafetyCheck check = openCheck(LocalDateTime.now());
        User stranger = new User();
        stranger.setId(UUID.randomUUID());
        stranger.setTelegramChatId("999");
        when(users.findByTelegramChatId("999")).thenReturn(Optional.of(stranger));

        assertThrows(AccessDeniedException.class, () -> service.answerFromTelegram("999", "unsafe:77", 9L));

        assertNull(check.getResponse());
        assertNull(session.getSosAt());
    }

    @Test
    @DisplayName("SOS from the app for someone else's booking is refused")
    void sosOnAnotherPassengersTripIsRefused() {
        assertThrows(AccessDeniedException.class, () -> service.sos(UUID.randomUUID(), BOOKING_ID));
        assertNull(session.getSosAt());
    }

    @Test
    @DisplayName("No answer after the reminder window: asked again, once")
    void unansweredIsRemindedOnce() {
        RideSafetyCheck check = openCheck(LocalDateTime.now().minusMinutes(3));

        service.monitorOne(BOOKING_ID);

        assertNotNull(check.getRemindedAt());
        assertNull(check.getResponse());
        verify(telegram).sendMessageForId(eq(RIDER_CHAT), any(), anyList());
    }

    @Test
    @DisplayName("Still no answer at the escalation window: owner alerted, contacts left alone")
    void silenceIsEscalatedToTheOwner() {
        RideSafetyCheck check = openCheck(LocalDateTime.now().minusMinutes(5));
        check.setRemindedAt(LocalDateTime.now().minusMinutes(3));

        service.monitorOne(BOOKING_ID);

        assertEquals(RideSafetyCheck.Response.NO_RESPONSE, check.getResponse());
        assertTrue(savedNotifications().stream().anyMatch(n -> "SAFETY_NO_RESPONSE".equals(n.getType())));
        verify(telegram, never()).sendMessage(eq(CONTACT_CHAT), any(), anyList());
    }

    @Test
    @DisplayName("A scheduled check goes out when it is due, with Safe and Unsafe buttons")
    void scheduledCheckIsSent() {
        session.setNextCheckAt(LocalDateTime.now().minusSeconds(1));

        service.monitorOne(BOOKING_ID);

        ArgumentCaptor<RideSafetyCheck> saved = ArgumentCaptor.forClass(RideSafetyCheck.class);
        verify(checks).save(saved.capture());
        assertEquals(RideSafetyCheck.Reason.SCHEDULED, saved.getValue().getReason());
        verify(telegram).sendMessageForId(eq(RIDER_CHAT), any(), anyList());
        assertTrue(session.getNextCheckAt().isAfter(LocalDateTime.now()));
    }

    @Test
    @DisplayName("Car parked in one spot for the whole window, away from the drop: STOPPED check")
    void stoppedAwayFromDropIsDetected() {
        List<RideLocationPoint> points = new ArrayList<>();
        for (int i = 15; i >= 0; i--) {
            points.add(new RideLocationPoint(BOOKING_ID, 12.9352, 77.6245, LocalDateTime.now().minusSeconds(i * 20L)));
        }
        when(trail.findByBookingIdAndRecordedAtAfterOrderByRecordedAtAsc(eq(BOOKING_ID), any())).thenReturn(points);

        assertEquals(RideSafetyCheck.Reason.STOPPED, service.anomaly(session, booking, LocalDateTime.now()));
    }

    @Test
    @DisplayName("Waiting at the drop is the trip ending, not a stop worth asking about")
    void stoppedAtTheDropIsNotAnAnomaly() {
        List<RideLocationPoint> points = new ArrayList<>();
        for (int i = 15; i >= 0; i--) {
            points.add(new RideLocationPoint(BOOKING_ID, 13.1985, 77.7065, LocalDateTime.now().minusSeconds(i * 20L)));
        }
        when(trail.findByBookingIdAndRecordedAtAfterOrderByRecordedAtAsc(eq(BOOKING_ID), any())).thenReturn(points);

        assertNull(service.anomaly(session, booking, LocalDateTime.now()));
    }

    @Test
    @DisplayName("Driver's phone silent past the limit: GPS_SILENT check")
    void gpsSilenceIsDetected() {
        session.setLastPointAt(LocalDateTime.now().minusMinutes(4));

        assertEquals(RideSafetyCheck.Reason.GPS_SILENT, service.anomaly(session, booking, LocalDateTime.now()));
    }

    @Test
    @DisplayName("Trip past 1.5x its estimate: OVERDUE, asked once")
    void overdueIsAskedOnce() {
        session.setStartedAt(LocalDateTime.now().minusMinutes(70)); // estimate 40 min -> overdue at 60

        assertEquals(RideSafetyCheck.Reason.OVERDUE, service.anomaly(session, booking, LocalDateTime.now()));

        // Asked 5 minutes ago, after it went overdue (10 minutes ago). Cooldown shortened so it is
        // the asked-once rule, not the cooldown, that keeps this quiet.
        props.setAnomalyCooldown(java.time.Duration.ofMinutes(1));
        session.setLastCheckAt(LocalDateTime.now().minusMinutes(5));
        assertNull(service.anomaly(session, booking, LocalDateTime.now()));
    }

    @Test
    @DisplayName("Outstation trips get SOS and the live map, but no automatic checks")
    void outstationTripsAreNotAutoChecked() {
        booking.setServiceMode(TravelBooking.ServiceMode.TRIP);
        session.setNextCheckAt(LocalDateTime.now().minusMinutes(1));

        service.monitorOne(BOOKING_ID);

        verify(checks, never()).save(any());
    }

    @Test
    @DisplayName("A trip no longer STARTED has its session closed by the monitor")
    void finishedTripClosesTheSession() {
        booking.setStatus(TravelBooking.BookingStatus.COMPLETED);

        service.monitorOne(BOOKING_ID);

        assertNotNull(session.getEndedAt());
        verify(checks, never()).save(any());
    }

    @Test
    @DisplayName("The share link stops working an hour after the trip ends")
    void shareLinkExpiresAfterTheTrip() {
        when(sessions.findByShareToken("tok")).thenReturn(Optional.of(session));
        assertTrue(service.sharedTrip("tok").isPresent());

        session.setEndedAt(LocalDateTime.now().minusHours(2));
        assertTrue(service.sharedTrip("tok").isEmpty());
    }
}
