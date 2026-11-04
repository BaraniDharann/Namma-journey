package com.travelplatform.service.bus;

import com.travelplatform.config.OwnerScope;
import com.travelplatform.dto.bus.BusForms.CrewForm;
import com.travelplatform.dto.bus.BusViews.TripDetail;
import com.travelplatform.entity.BusSeat;
import com.travelplatform.entity.BusSeatAllocation;
import com.travelplatform.entity.BusTicket;
import com.travelplatform.entity.BusTrip;
import com.travelplatform.exception.ConflictException;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.BusPlaceRepository;
import com.travelplatform.repository.BusRepository;
import com.travelplatform.repository.BusRoutePointRepository;
import com.travelplatform.repository.BusRouteRepository;
import com.travelplatform.repository.BusRouteStopRepository;
import com.travelplatform.repository.BusSeatAllocationRepository;
import com.travelplatform.repository.BusSeatRepository;
import com.travelplatform.repository.BusTicketPassengerRepository;
import com.travelplatform.repository.BusTicketRepository;
import com.travelplatform.repository.BusTripRepository;
import com.travelplatform.entity.BusRouteStop;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** An owner's trip: blocking seats, the crew, and cancelling the whole trip. */
class BusTripServiceTest {

    private static final long ME = 7L;

    private BusTripRepository trips;
    private BusSeatRepository seats;
    private BusSeatAllocationRepository allocations;
    private BusTicketRepository tickets;
    private BusTripService service;
    private BusTrip trip;

    @BeforeEach
    void setUp() {
        trips = mock(BusTripRepository.class);
        seats = mock(BusSeatRepository.class);
        allocations = mock(BusSeatAllocationRepository.class);
        tickets = mock(BusTicketRepository.class);
        BusRouteStopRepository stops = mock(BusRouteStopRepository.class);
        service = new BusTripService(trips, mock(BusRouteRepository.class), mock(BusRepository.class), seats, stops,
                mock(BusRoutePointRepository.class), mock(BusPlaceRepository.class), allocations, tickets,
                mock(BusTicketPassengerRepository.class));

        trip = new BusTrip();
        trip.setId(50L);
        trip.setOwnerId(ME);
        trip.setRouteId(1L);
        trip.setBusId(9L);
        trip.setTravelDate(LocalDate.of(2026, 11, 10));
        trip.setDepartureAt(trip.getTravelDate().atTime(21, 0));
        trip.setArrivalAt(trip.getTravelDate().plusDays(1).atTime(5, 0));
        when(trips.findById(50L)).thenReturn(Optional.of(trip));
        when(trips.save(any(BusTrip.class))).thenAnswer(i -> i.getArgument(0));
        when(trips.saveAndFlush(any(BusTrip.class))).thenAnswer(i -> i.getArgument(0));
        when(seats.findByBusIdOrderByDeckAscRowNoAscColNoAsc(9L)).thenReturn(List.of(seat("U1"), seat("U2")));
        List<BusRouteStop> routeStops = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            BusRouteStop s = new BusRouteStop();
            s.setId(100L + i);
            s.setSeq(i);
            s.setPlaceId(1L + i);
            routeStops.add(s);
        }
        when(stops.findByRouteIdOrderBySeqAsc(1L)).thenReturn(routeStops);
        OwnerScope.bind(ME, false);
    }

    @AfterEach
    void unbind() {
        OwnerScope.clear();
    }

    private static BusSeat seat(String label) {
        BusSeat s = new BusSeat();
        s.setLabel(label);
        s.setDeck(1);
        s.setKind(BusSeat.Kind.SLEEPER);
        s.setBerth(BusSeat.Berth.SINGLE);
        return s;
    }

    @Test
    @DisplayName("another owner's trip is not found")
    void otherOwnersTrip() {
        trip.setOwnerId(99L);
        assertThrows(ResourceNotFoundException.class, () -> service.detail(50L));
    }

    @Test
    @DisplayName("blocking holds the seat over the whole route; a taken or unknown seat is refused")
    @SuppressWarnings("unchecked")
    void block() {
        service.block(50L, List.of("u1"));

        ArgumentCaptor<List<BusSeatAllocation>> saved = ArgumentCaptor.forClass(List.class);
        verify(allocations).saveAllAndFlush(saved.capture());
        BusSeatAllocation a = saved.getValue().get(0);
        assertEquals("U1", a.getSeatLabel());
        assertEquals(0, a.getFromSeq());
        assertEquals(2, a.getToSeq());
        assertEquals(BusSeatAllocation.Status.BLOCKED, a.getStatus());

        BusSeatAllocation sold = new BusSeatAllocation();
        sold.setSeatLabel("U2");
        sold.setStatus(BusSeatAllocation.Status.CONFIRMED);
        when(allocations.findByTripIdAndStatusInOrderBySeatLabelAscFromSeqAsc(eq(50L), anyCollection()))
                .thenReturn(List.of(sold));
        assertThrows(ConflictException.class, () -> service.block(50L, List.of("U2")));
        assertThrows(IllegalArgumentException.class, () -> service.block(50L, List.of("Z9")));
    }

    @Test
    @DisplayName("crew phones are tidied to 10 digits; a bad one is refused")
    void crew() {
        TripDetail d = service.setCrew(50L, new CrewForm(" Ravi ", "+91 98765 43210", null, ""));
        assertEquals("Ravi", d.driverName());
        assertEquals("9876543210", d.driverPhone());
        assertEquals(null, d.conductorPhone());
        assertThrows(IllegalArgumentException.class, () -> service.setCrew(50L, new CrewForm("R", "12345", null, null)));
    }

    @Test
    @DisplayName("cancelling a trip releases every seat and service-cancels every live ticket")
    void cancel() {
        BusTicket paid = new BusTicket();
        paid.setStatus(BusTicket.Status.CONFIRMED);
        BusTicket held = new BusTicket();
        held.setStatus(BusTicket.Status.PENDING);
        when(tickets.findByTripIdAndStatusIn(eq(50L), eq(BusTripService.LIVE))).thenReturn(List.of(paid, held));

        assertThrows(IllegalArgumentException.class, () -> service.cancel(50L, " "));
        TripDetail d = service.cancel(50L, "Bus breakdown");

        assertEquals("CANCELLED", d.status());
        assertEquals("Bus breakdown", d.cancelReason());
        verify(allocations).cancelLive(50L);
        assertEquals(BusTicket.Status.SERVICE_CANCELLED, paid.getStatus());
        assertEquals(BusTicket.Status.SERVICE_CANCELLED, held.getStatus());
        verify(tickets).saveAll(anyList());

        // Already cancelled: refused, nothing more changes.
        assertThrows(ConflictException.class, () -> service.cancel(50L, "again"));
        verify(allocations).cancelLive(anyLong());
        verify(allocations, never()).saveAllAndFlush(any());
    }
}
