package com.travelplatform.service;

import com.travelplatform.config.OwnerScope;
import com.travelplatform.dto.bus.BusViews.TripView;
import com.travelplatform.entity.Bus;
import com.travelplatform.entity.BusRoutePoint;
import com.travelplatform.entity.BusRouteStop;
import com.travelplatform.entity.BusTrip;
import com.travelplatform.entity.Vehicle;
import com.travelplatform.entity.VehicleExpense;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.BusRepository;
import com.travelplatform.repository.BusRoutePointRepository;
import com.travelplatform.repository.BusRouteStopRepository;
import com.travelplatform.repository.BusTripRepository;
import com.travelplatform.repository.VehicleExpenseRepository;
import com.travelplatform.repository.VehicleRepository;
import com.travelplatform.service.bus.BusTripService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpenseServiceTest {

    private static final long ME = 7L;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);

    private VehicleExpenseRepository expenses;
    private VehicleRepository cars;
    private BusRepository buses;
    private BusTripRepository trips;
    private BusRouteStopRepository stops;
    private BusRoutePointRepository points;
    private BusTripService busTrips;
    private ExpenseService service;

    @BeforeEach
    void setUp() {
        expenses = mock(VehicleExpenseRepository.class);
        cars = mock(VehicleRepository.class);
        buses = mock(BusRepository.class);
        trips = mock(BusTripRepository.class);
        stops = mock(BusRouteStopRepository.class);
        points = mock(BusRoutePointRepository.class);
        busTrips = mock(BusTripService.class);
        when(expenses.save(any(VehicleExpense.class))).thenAnswer(i -> i.getArgument(0));
        service = new ExpenseService(expenses, cars, buses, trips, stops, points, busTrips);
        service.setClock(Clock.fixed(ZonedDateTime.of(TODAY, LocalTime.of(18, 0), IST).toInstant(), IST));
        OwnerScope.bind(ME, false);
    }

    @AfterEach
    void unbind() {
        OwnerScope.clear();
    }

    private static Vehicle car(long id, long owner) {
        Vehicle v = new Vehicle();
        v.setId(id);
        v.setOwnerId(owner);
        v.setRegistrationNumber("TN01AB1234");
        v.setModel("Innova");
        return v;
    }

    private static Bus bus(long id, long owner) {
        Bus b = new Bus();
        b.setId(id);
        b.setOwnerId(owner);
        b.setName("Namma Express");
        b.setRegistrationNumber("TN01BU9999");
        return b;
    }

    private static TripView tripView(long id, long routeId, long busId, int total, int sold, String collected) {
        return new TripView(id, routeId, "Chennai - Madurai", busId, "Namma Express", "TN01BU9999", TODAY,
                TODAY.atTime(21, 30), TODAY.plusDays(1).atTime(6, 0), "SCHEDULED", total, sold, new BigDecimal(collected));
    }

    @Test
    @DisplayName("a car expense is saved for my own car, rounded to paise")
    void addsCarExpense() {
        when(cars.findById(3L)).thenReturn(Optional.of(car(3, ME)));
        ExpenseService.ExpenseView v = service.add(new ExpenseService.ExpenseForm("car", 3L, null, TODAY,
                "fuel", new BigDecimal("1500.456"), "  Full tank "));
        assertEquals("CAR", v.vehicleKind());
        assertEquals("FUEL", v.category());
        assertEquals(new BigDecimal("1500.46"), v.amount());
        assertEquals("Full tank", v.note());
        assertEquals("TN01AB1234 · Innova", v.vehicleLabel());
    }

    @Test
    @DisplayName("another owner's car is not found, and nothing is saved")
    void refusesOtherOwnersCar() {
        when(cars.findById(3L)).thenReturn(Optional.of(car(3, 99)));
        assertThrows(ResourceNotFoundException.class, () -> service.add(new ExpenseService.ExpenseForm("CAR", 3L,
                null, TODAY, "FUEL", BigDecimal.TEN, null)));
        verify(expenses, never()).save(any());
    }

    @Test
    @DisplayName("future dates, zero amounts and unknown categories are refused")
    void validates() {
        when(cars.findById(3L)).thenReturn(Optional.of(car(3, ME)));
        assertThrows(IllegalArgumentException.class, () -> service.add(new ExpenseService.ExpenseForm("CAR", 3L,
                null, TODAY.plusDays(1), "FUEL", BigDecimal.TEN, null)));
        assertThrows(IllegalArgumentException.class, () -> service.add(new ExpenseService.ExpenseForm("CAR", 3L,
                null, TODAY, "FUEL", BigDecimal.ZERO, null)));
        assertThrows(IllegalArgumentException.class, () -> service.add(new ExpenseService.ExpenseForm("CAR", 3L,
                null, TODAY, "SNACKS", BigDecimal.TEN, null)));
        assertThrows(IllegalArgumentException.class, () -> service.add(new ExpenseService.ExpenseForm("TRAIN", 3L,
                null, TODAY, "FUEL", BigDecimal.TEN, null)));
    }

    @Test
    @DisplayName("a bus expense may name a trip, but only a trip of that same bus")
    void tripMustBeOnThatBus() {
        when(buses.findById(5L)).thenReturn(Optional.of(bus(5, ME)));
        BusTrip t = new BusTrip();
        t.setId(40L);
        t.setOwnerId(ME);
        t.setBusId(6L);
        when(trips.findById(40L)).thenReturn(Optional.of(t));
        assertThrows(IllegalArgumentException.class, () -> service.add(new ExpenseService.ExpenseForm("BUS", 5L,
                40L, TODAY, "TOLL", BigDecimal.TEN, null)));
        t.setBusId(5L);
        assertEquals(40L, service.add(new ExpenseService.ExpenseForm("BUS", 5L, 40L, TODAY, "TOLL",
                BigDecimal.TEN, null)).busTripId());
    }

    @Test
    @DisplayName("profit per vehicle = revenue - expenses, with every vehicle listed even at zero")
    void summary() {
        when(cars.findByOwnerIdOrderByIdAsc(ME)).thenReturn(List.of(car(3, ME), car(4, ME)));
        when(buses.findByOwnerIdOrderByIdAsc(ME)).thenReturn(List.of(bus(5, ME)));
        when(expenses.carRevenue(eq(ME), any(), any())).thenReturn(List.<Object[]>of(new Object[]{3L, new BigDecimal("5000")}));
        when(expenses.busRevenue(eq(ME), any(), any())).thenReturn(List.<Object[]>of(new Object[]{5L, new BigDecimal("20000")}));
        VehicleExpense fuel = new VehicleExpense();
        fuel.setVehicleKind(VehicleExpense.Kind.CAR);
        fuel.setVehicleId(3L);
        fuel.setCategory(VehicleExpense.Category.FUEL);
        fuel.setAmount(new BigDecimal("1200"));
        VehicleExpense pay = new VehicleExpense();
        pay.setVehicleKind(VehicleExpense.Kind.BUS);
        pay.setVehicleId(5L);
        pay.setCategory(VehicleExpense.Category.DRIVER_PAY);
        pay.setAmount(new BigDecimal("3000"));
        when(expenses.findByOwnerIdAndSpentOnBetweenOrderBySpentOnDescIdDesc(eq(ME), any(), any())).thenReturn(List.of(fuel, pay));

        ExpenseService.ProfitSummary s = service.summary(null, null, null);
        assertEquals(TODAY.withDayOfMonth(1), s.from());
        assertEquals(3, s.vehicles().size());
        assertEquals(0, new BigDecimal("25000").compareTo(s.revenue()));
        assertEquals(0, new BigDecimal("4200").compareTo(s.expenses()));
        assertEquals(0, new BigDecimal("20800").compareTo(s.profit()));
        ExpenseService.VehicleProfit firstCar = s.vehicles().get(0);
        assertEquals(0, new BigDecimal("3800").compareTo(firstCar.profit()));
        assertEquals(0, new BigDecimal("1200").compareTo(firstCar.byCategory().get("FUEL")));
        assertEquals(0, BigDecimal.ZERO.compareTo(s.vehicles().get(1).profit()));
    }

    @Test
    @DisplayName("bus day: booking closes at the last boarding point; profit = fares - that trip's expenses")
    void busDay() {
        when(busTrips.list(TODAY, TODAY)).thenReturn(List.of(tripView(40, 9, 5, 36, 28, "22400")));
        BusRouteStop first = new BusRouteStop();
        first.setId(100L);
        first.setRouteId(9L);
        BusRouteStop second = new BusRouteStop();
        second.setId(101L);
        second.setRouteId(9L);
        when(stops.findByRouteIdInOrderByRouteIdAscSeqAsc(anyCollection())).thenReturn(List.of(first, second));
        BusRoutePoint board1 = point(100L, BusRoutePoint.Kind.BOARDING, LocalTime.of(21, 30));
        BusRoutePoint board2 = point(101L, BusRoutePoint.Kind.BOTH, LocalTime.of(22, 15));
        BusRoutePoint drop = point(101L, BusRoutePoint.Kind.DROPPING, LocalTime.of(23, 50));
        when(points.findByStopIdInOrderByDayOffsetAscTimeAscIdAsc(anyCollection())).thenReturn(List.of(board1, board2, drop));
        VehicleExpense diesel = new VehicleExpense();
        diesel.setBusTripId(40L);
        diesel.setAmount(new BigDecimal("9800"));
        when(expenses.findByOwnerIdAndBusTripIdIn(eq(ME), anyCollection())).thenReturn(List.of(diesel));

        ExpenseService.BusToday day = service.busDay(null);
        assertEquals(1, day.trips());
        ExpenseService.TodayTrip t = day.list().get(0);
        assertEquals(LocalDateTime.of(TODAY, LocalTime.of(22, 15)), t.bookingClosesAt());
        assertTrue(t.bookingOpen());
        assertEquals(8, t.seatsOpen());
        assertEquals(0, new BigDecimal("12600").compareTo(t.profit()));
        assertEquals(0, new BigDecimal("12600").compareTo(day.profit()));
    }

    @Test
    @DisplayName("bus day with no trips is an empty, zeroed day")
    void emptyDay() {
        when(busTrips.list(TODAY, TODAY)).thenReturn(List.of());
        ExpenseService.BusToday day = service.busDay(null);
        assertEquals(0, day.trips());
        assertFalse(day.list().iterator().hasNext());
        verify(expenses, never()).findByOwnerIdAndBusTripIdIn(anyLong(), anyCollection());
    }

    private static BusRoutePoint point(long stopId, BusRoutePoint.Kind kind, LocalTime time) {
        BusRoutePoint p = new BusRoutePoint();
        p.setStopId(stopId);
        p.setKind(kind);
        p.setTime(time);
        p.setDayOffset(0);
        return p;
    }
}
