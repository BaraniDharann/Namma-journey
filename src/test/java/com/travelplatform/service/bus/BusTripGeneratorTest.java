package com.travelplatform.service.bus;

import com.travelplatform.entity.Bus;
import com.travelplatform.entity.BusDocument;
import com.travelplatform.entity.BusRoute;
import com.travelplatform.entity.BusRoutePoint;
import com.travelplatform.entity.BusRouteStop;
import com.travelplatform.repository.BusDocumentRepository;
import com.travelplatform.repository.BusRepository;
import com.travelplatform.repository.BusRoutePointRepository;
import com.travelplatform.repository.BusRouteRepository;
import com.travelplatform.repository.BusRouteStopRepository;
import com.travelplatform.repository.BusTripRepository;
import com.travelplatform.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Which trips a route gets: run days, active dates, an approved bus and valid documents. */
class BusTripGeneratorTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    /** Wednesday 4 Nov 2026, 10:00. */
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);

    private BusRepository buses;
    private BusDocumentRepository documents;
    private BusRouteStopRepository stops;
    private BusRoutePointRepository points;
    private BusTripRepository trips;
    private NotificationService notifications;
    private BusTripGenerator generator;
    private Bus bus;

    @BeforeEach
    void setUp() {
        buses = mock(BusRepository.class);
        documents = mock(BusDocumentRepository.class);
        stops = mock(BusRouteStopRepository.class);
        points = mock(BusRoutePointRepository.class);
        trips = mock(BusTripRepository.class);
        notifications = mock(NotificationService.class);
        generator = new BusTripGenerator(mock(BusRouteRepository.class), buses, documents, stops, points, trips,
                notifications, null);
        generator.setClock(Clock.fixed(TODAY.atTime(10, 0).atZone(IST).toInstant(), IST));

        bus = new Bus();
        bus.setId(9L);
        bus.setName("Night Rider");
        bus.setRegistrationNumber("TN01AB1234");
        bus.setStatus(Bus.Status.APPROVED);
        when(buses.findById(9L)).thenReturn(Optional.of(bus));
        documentsValidUntil(TODAY.plusYears(1));

        when(stops.findByRouteIdOrderBySeqAsc(1L)).thenReturn(List.of(stop(100L, 0), stop(101L, 1)));
        when(points.findByStopIdInOrderByDayOffsetAscTimeAscIdAsc(any())).thenReturn(List.of(
                point(100L, 0, "21:30"), point(100L, 0, "21:00"), point(101L, 1, "05:00"), point(101L, 1, "05:45")));
        when(trips.insertIfAbsent(anyLong(), anyLong(), anyLong(), any(), any(), any())).thenReturn(1);
    }

    private void documentsValidUntil(LocalDate lastDay) {
        List<BusDocument> docs = new ArrayList<>();
        for (BusDocument.Type t : BusDocument.Type.values()) {
            BusDocument d = new BusDocument();
            d.setBusId(9L);
            d.setType(t);
            d.setExpiresOn(t == BusDocument.Type.INSURANCE ? lastDay : TODAY.plusYears(2));
            docs.add(d);
        }
        when(documents.findByBusIdOrderByTypeAsc(9L)).thenReturn(docs);
    }

    private static BusRouteStop stop(long id, int seq) {
        BusRouteStop s = new BusRouteStop();
        s.setId(id);
        s.setSeq(seq);
        return s;
    }

    private static BusRoutePoint point(long stopId, int day, String time) {
        BusRoutePoint p = new BusRoutePoint();
        p.setStopId(stopId);
        p.setDayOffset(day);
        p.setTime(LocalTime.parse(time));
        return p;
    }

    private static BusRoute route(short runDays) {
        BusRoute r = new BusRoute();
        r.setId(1L);
        r.setOwnerId(7L);
        r.setBusId(9L);
        r.setRunDays(runDays);
        r.setActiveFrom(TODAY.minusDays(30));
        r.setStatus(BusRoute.Status.PUBLISHED);
        return r;
    }

    private List<LocalDate> createdDates() {
        ArgumentCaptor<LocalDate> dates = ArgumentCaptor.forClass(LocalDate.class);
        verify(trips, org.mockito.Mockito.atLeast(0)).insertIfAbsent(eq(1L), eq(9L), eq(7L), dates.capture(), any(), any());
        return dates.getAllValues();
    }

    @Test
    @DisplayName("a daily route gets 60 trips from today, departing at the earliest first-stop time")
    void dailyRoute() {
        assertEquals(60, generator.generate(route((short) 127)));

        List<LocalDate> dates = createdDates();
        assertEquals(60, dates.size());
        assertEquals(TODAY, dates.get(0));
        assertEquals(TODAY.plusDays(59), dates.get(59));
        verify(trips).insertIfAbsent(1L, 9L, 7L, TODAY, TODAY.atTime(21, 0), TODAY.plusDays(1).atTime(5, 45));
    }

    @Test
    @DisplayName("only on run days (bit 0 = Monday) and within the active dates")
    void runDaysAndActiveDates() {
        BusRoute r = route((short) 0b0000001); // Mondays
        r.setActiveTo(TODAY.plusDays(14));
        generator.generate(r);

        // Mondays after Wednesday 4 Nov, up to 18 Nov: 9 and 16 Nov.
        assertEquals(List.of(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 16)), createdDates());
    }

    @Test
    @DisplayName("no trips past the day the first document expires")
    void stopsAtDocumentExpiry() {
        documentsValidUntil(TODAY.plusDays(2));
        generator.generate(route((short) 127));

        assertEquals(List.of(TODAY, TODAY.plusDays(1), TODAY.plusDays(2)), createdDates());
    }

    @Test
    @DisplayName("no trips for an unapproved bus, a bus missing a document, or a route not published")
    void nothingWhenNotReady() {
        bus.setStatus(Bus.Status.PENDING);
        assertEquals(0, generator.generate(route((short) 127)));

        bus.setStatus(Bus.Status.APPROVED);
        when(documents.findByBusIdOrderByTypeAsc(9L)).thenReturn(List.of());
        assertEquals(0, generator.generate(route((short) 127)));

        documentsValidUntil(TODAY.plusYears(1));
        BusRoute paused = route((short) 127);
        paused.setStatus(BusRoute.Status.PAUSED);
        assertEquals(0, generator.generate(paused));

        verify(trips, never()).insertIfAbsent(anyLong(), anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("a trip whose departure has already passed today is not created")
    void skipsDepartedToday() {
        generator.setClock(Clock.fixed(TODAY.atTime(21, 15).atZone(IST).toInstant(), IST));
        generator.generate(route((short) 127));

        assertEquals(TODAY.plusDays(1), createdDates().get(0));
    }

    @Test
    @DisplayName("running again counts only the trips the database actually created")
    void idempotentCount() {
        when(trips.insertIfAbsent(anyLong(), anyLong(), anyLong(), any(), any(), any())).thenReturn(0);
        assertEquals(0, generator.generate(route((short) 127)));
    }

    @Test
    @DisplayName("an approved bus's document expiring in 15 days is told to its owner")
    void expiryReminder() {
        BusDocument d = new BusDocument();
        d.setBusId(9L);
        d.setOwnerId(7L);
        d.setType(BusDocument.Type.PERMIT);
        d.setExpiresOn(TODAY.plusDays(15));
        when(documents.findApprovedExpiringOn(TODAY.plusDays(15))).thenReturn(List.of(d));

        generator.remindExpiringDocuments();

        ArgumentCaptor<String> bus = ArgumentCaptor.forClass(String.class);
        verify(notifications, times(1)).notifyBusDocumentExpiring(eq(7L), bus.capture(), anyString(), eq(TODAY.plusDays(15)));
        assertTrue(bus.getValue().contains("TN01AB1234"));
    }

    @Test
    @DisplayName("departure and arrival come from the first and last stop's points")
    void plannerTimes() {
        LocalDate d = LocalDate.of(2026, 12, 1);
        assertEquals(LocalDateTime.of(2026, 12, 1, 21, 0),
                BusTripPlanner.departure(d, List.of(point(1, 0, "21:30"), point(1, 0, "21:00"))));
        assertEquals(LocalDateTime.of(2026, 12, 2, 5, 45),
                BusTripPlanner.arrival(d, List.of(point(2, 1, "05:00"), point(2, 1, "05:45"))));
        assertTrue(BusTripPlanner.runsOn((short) 0b1000000, LocalDate.of(2026, 11, 8))); // Sunday
        assertTrue(BusTripPlanner.runningDates((short) 127, TODAY, null, null, TODAY, TODAY.plusDays(5)).isEmpty());
    }
}
