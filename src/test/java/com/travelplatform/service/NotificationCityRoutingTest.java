package com.travelplatform.service;

import com.travelplatform.entity.Driver;
import com.travelplatform.entity.Notification;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.entity.User;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.NotificationRepository;
import com.travelplatform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Found on live: Lokesh booked a City ride and owner NJO-0001 got "New Booking Received",
 * "Ride Now needs a driver" and "Booking Cancelled" for a ride that was never theirs.
 * A City ride goes to whichever driver is nearest the passenger; only the people on that ride
 * hear about it: the passenger, the driver, and the driver's owner when the driver works for one.
 * The platform hears only when nobody nearby took it.
 */
class NotificationCityRoutingTest {

    private static final long PLATFORM = 1L;
    private static final long OWNER_X = 42L;

    private NotificationRepository notifications;
    private DriverRepository drivers;
    private NotificationService service;
    private Driver ownersDriver;
    private Driver independent;

    @BeforeEach
    void setUp() {
        notifications = mock(NotificationRepository.class);
        drivers = mock(DriverRepository.class);
        UserRepository users = mock(UserRepository.class);
        User lokesh = new User();
        lokesh.setEmail("lokesh@example.com");
        lokesh.setName("Lokesh");
        when(users.findById(any())).thenReturn(Optional.of(lokesh));

        service = new NotificationService(notifications, mock(EmailService.class), users,
                mock(TelegramDispatchService.class), new AfterCommitDispatcher(Runnable::run));
        TripRateService rates = mock(TripRateService.class);
        when(rates.platformOwnerId()).thenReturn(PLATFORM);
        ReflectionTestUtils.setField(service, "tripRates", rates);
        ReflectionTestUtils.setField(service, "driverRepository", drivers);

        ownersDriver = driver(7L, OWNER_X);
        independent = driver(8L, null);
        when(drivers.findById(7L)).thenReturn(Optional.of(ownersDriver));
        when(drivers.findById(8L)).thenReturn(Optional.of(independent));
    }

    private static Driver driver(long id, Long ownerId) {
        Driver d = new Driver();
        d.setId(id);
        d.setName("Driver " + id);
        d.setMobile("9597965911");
        d.setOwnerId(ownerId);
        return d;
    }

    private static TravelBooking cityRide(Long driverId, TravelBooking.DispatchState state) {
        TravelBooking b = new TravelBooking();
        b.setId(UUID.randomUUID());
        b.setUserId(UUID.randomUUID());
        b.setUserName("Lokesh");
        b.setFromPlace("Ayyangar Kulam");
        b.setToPlace("Kanchipuram Bus Stand");
        b.setFromDate(LocalDate.of(2026, 11, 18));
        b.setTotalAmount(new BigDecimal("120"));
        b.setServiceMode(TravelBooking.ServiceMode.CITY);
        b.setOwnerId(null);
        b.setDriverId(driverId);
        b.setDispatchState(state);
        return b;
    }

    private List<Notification> saved() {
        ArgumentCaptor<Notification> c = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, atLeast(0)).save(c.capture());
        return c.getAllValues();
    }

    private List<String> ownerRecipients() {
        return saved().stream().filter(n -> "ROLE_OWNER".equals(n.getRecipientRole()))
                .map(Notification::getRecipientId).toList();
    }

    @Test
    @DisplayName("booking a City ride tells no owner: the nearest drivers get the offer")
    void cityBookedTellsNoOwner() {
        service.notifyBookingCreated(cityRide(null, TravelBooking.DispatchState.SEARCHING));
        assertThat(ownerRecipients()).isEmpty();
    }

    @Test
    @DisplayName("an owner's driver takes the City ride: that owner is told, not the platform")
    void ownersDriverAccepts() {
        service.notifyTripAccepted(cityRide(7L, TravelBooking.DispatchState.MATCHED), ownersDriver);
        assertThat(ownerRecipients()).containsExactly(String.valueOf(OWNER_X));
    }

    @Test
    @DisplayName("an independent driver takes it: no owner is told")
    void independentAccepts() {
        service.notifyTripAccepted(cityRide(8L, TravelBooking.DispatchState.MATCHED), independent);
        assertThat(ownerRecipients()).isEmpty();
    }

    @Test
    @DisplayName("nobody nearby took it: only the platform, which can assign it")
    void noDriverGoesToPlatform() {
        service.notifyNoDriverNearby(cityRide(null, TravelBooking.DispatchState.NO_DRIVER));
        assertThat(ownerRecipients()).containsExactly(String.valueOf(PLATFORM));
    }

    @Test
    @DisplayName("cancelling: before anyone took it no owner; after, the driver and the driver's owner")
    void cancelRouting() {
        service.notifyBookingCancelled(cityRide(null, TravelBooking.DispatchState.SEARCHING));
        assertThat(ownerRecipients()).isEmpty();

        service.notifyBookingCancelled(cityRide(7L, TravelBooking.DispatchState.MATCHED));
        assertThat(ownerRecipients()).containsExactly(String.valueOf(OWNER_X));
        assertThat(saved()).anyMatch(n -> "ROLE_DRIVER".equals(n.getRecipientRole()) && "7".equals(n.getRecipientId()));
    }

    @Test
    @DisplayName("cancelling a ride the platform was asked to place tells the platform")
    void cancelAfterNoDriver() {
        service.notifyBookingCancelled(cityRide(null, TravelBooking.DispatchState.NO_DRIVER));
        assertThat(ownerRecipients()).containsExactly(String.valueOf(PLATFORM));
    }

    @Test
    @DisplayName("paid: the owner of the driver's car, or no owner for an independent driver")
    void paymentRouting() {
        service.notifyPaymentReceived(cityRide(8L, TravelBooking.DispatchState.MATCHED));
        assertThat(ownerRecipients()).isEmpty();
        service.notifyPaymentReceived(cityRide(7L, TravelBooking.DispatchState.MATCHED));
        assertThat(ownerRecipients()).containsExactly(String.valueOf(OWNER_X));
    }

    @Test
    @DisplayName("a driver turning down a City offer is not an owner's task")
    void cityRejectTellsNoOwner() {
        service.notifyTripRejected(cityRide(7L, TravelBooking.DispatchState.SEARCHING), ownersDriver, null);
        assertThat(ownerRecipients()).isEmpty();
    }

    @Test
    @DisplayName("Trips are unchanged: the Trip's owner hears about it")
    void tripStillGoesToItsOwner() {
        TravelBooking trip = cityRide(null, null);
        trip.setServiceMode(TravelBooking.ServiceMode.TRIP);
        trip.setOwnerId(5L);
        service.notifyBookingCreated(trip);
        assertThat(ownerRecipients()).containsExactly("5");
    }
}
