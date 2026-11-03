package com.travelplatform.service;

import com.travelplatform.dto.RouteInfo;
import com.travelplatform.dto.TravelBookingRequest;
import com.travelplatform.entity.City;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.entity.User;
import com.travelplatform.entity.VehicleType;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.repository.TripDriverPhotoRepository;
import com.travelplatform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** City rides are priced by vehicle and dispatched by nearness; Trips go to a suitable car by date. */
class UserServiceCityTripTest {

    private static final UUID USER_ID = UUID.fromString("bbbbbbbb-1111-2222-3333-444444444444");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 23, 18, 30);

    private TravelBookingRepository bookingRepository;
    private DriverRepository driverRepository;
    private RoutingService routingService;
    private DispatchService dispatchService;
    private CityFareService cityFareService;
    private CityService cityService;
    private UserService service;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(TravelBookingRepository.class);
        driverRepository = mock(DriverRepository.class);
        routingService = mock(RoutingService.class);
        OwnerService ownerService = mock(OwnerService.class);
        UserRepository userRepository = mock(UserRepository.class);
        TripDriverPhotoRepository photoRepository = mock(TripDriverPhotoRepository.class);

        service = new UserService(bookingRepository, routingService, driverRepository, ownerService,
                userRepository, mock(OtpService.class), mock(PasswordEncoder.class), mock(NotificationService.class),
                photoRepository, new TransactionTemplate(new NoOpTransactionManager()));

        dispatchService = mock(DispatchService.class);
        when(dispatchService.now()).thenReturn(NOW);
        cityFareService = mock(CityFareService.class);
        when(cityFareService.maxKm()).thenReturn(50.0);
        when(cityFareService.fare(any(), anyDouble(), anyLong())).thenReturn(new BigDecimal("87.50"));
        ReflectionTestUtils.setField(service, "dispatchService", dispatchService);
        ReflectionTestUtils.setField(service, "cityFareService", cityFareService);
        // Every pickup is inside the test city unless a test says otherwise; Trips never ask.
        cityService = mock(CityService.class);
        City testCity = new City();
        testCity.setId(1L);
        when(cityService.cityFor(anyDouble(), anyDouble())).thenReturn(Optional.of(testCity));
        ReflectionTestUtils.setField(service, "cityService", cityService);

        User user = new User();
        user.setId(USER_ID);
        user.setPhone("9876543210");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(photoRepository.findByBookingId(any())).thenReturn(Optional.empty());
        when(driverRepository.findById(any())).thenReturn(Optional.empty());
        when(bookingRepository.saveAndFlush(any(TravelBooking.class))).thenAnswer(i -> {
            TravelBooking b = i.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });
        route(12.0, 35L);

        // Trips are priced by the booked owner's rate for the car category (V27); until customers
        // choose an owner, every Trip is the platform owner's.
        tripRates = mock(TripRateService.class);
        when(tripRates.platformOwnerId()).thenReturn(PLATFORM_OWNER);
        when(tripRates.rate(eq(PLATFORM_OWNER), any())).thenAnswer(i -> rate(i.getArgument(1), "14.0", "200.00"));
        ReflectionTestUtils.setField(service, "tripRates", tripRates);
    }

    private static final long PLATFORM_OWNER = 38L;
    private TripRateService tripRates;

    private static com.travelplatform.entity.OwnerTripRate rate(com.travelplatform.entity.CarCategory c, String km, String hour) {
        com.travelplatform.entity.OwnerTripRate r = new com.travelplatform.entity.OwnerTripRate();
        r.setOwnerId(PLATFORM_OWNER);
        r.setCategory(c);
        r.setPerKm(new BigDecimal(km));
        r.setPerHour(new BigDecimal(hour));
        return r;
    }

    private void route(double km, long minutes) {
        when(routingService.calculateRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RouteInfo(km, minutes, "route"));
    }

    private static TravelBookingRequest city(String vehicle, int passengers) {
        TravelBookingRequest r = base();
        r.setServiceMode("CITY");
        r.setVehicleType(vehicle);
        r.setTravelMembers(passengers);
        // Whatever the form held, a City ride is for today.
        r.setFromDate(LocalDate.of(2026, 12, 1));
        r.setToDate(LocalDate.of(2026, 12, 3));
        r.setBookingType("HOUR_BASED");
        r.setBookingHours(5);
        return r;
    }

    private static TravelBookingRequest base() {
        TravelBookingRequest r = new TravelBookingRequest();
        r.setUserName("Kavya");
        r.setUserPhone("9876543210");
        r.setFromPlace("T Nagar");
        r.setToPlace("Adyar");
        r.setFromLat(13.04);
        r.setFromLon(80.23);
        r.setToLat(13.00);
        r.setToLon(80.25);
        r.setFromDate(LocalDate.of(2026, 11, 1));
        r.setToDate(LocalDate.of(2026, 11, 2));
        r.setTravelMembers(4);
        r.setAcType("AC");
        return r;
    }

    private TravelBooking saved() {
        ArgumentCaptor<TravelBooking> captor = ArgumentCaptor.forClass(TravelBooking.class);
        verify(bookingRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("a City ride is for today, priced by its vehicle, and handed to dispatch with no driver")
    void cityRide() {
        service.createBooking(USER_ID, city("bike", 1));

        TravelBooking b = saved();
        assertEquals(TravelBooking.ServiceMode.CITY, b.getServiceMode());
        assertEquals(VehicleType.BIKE, b.getVehicleType());
        assertEquals(TravelBooking.DispatchMode.RIDE_NOW, b.getDispatchMode());
        assertEquals(TravelBooking.DispatchState.SEARCHING, b.getDispatchState());
        assertEquals(NOW.toLocalDate(), b.getFromDate());
        assertEquals(NOW.toLocalDate(), b.getToDate());
        assertEquals(TravelBooking.BookingType.DISTANCE_BASED, b.getBookingType());
        assertEquals(new BigDecimal("87.50"), b.getTotalAmount());
        assertNull(b.getDriverId());
        verify(cityFareService).fare(VehicleType.BIKE, 12.0, 35L);
        verify(dispatchService).enqueueAfterCommit(b);
        verify(driverRepository, never()).findFirstFreeTripDriver(any(), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("a City ride records the city its pickup is in")
    void cityRideRecordsItsCity() {
        service.createBooking(USER_ID, city("auto", 2));

        assertEquals(1L, saved().getCityId());
    }

    @Test
    @DisplayName("a City ride from outside every city is refused and pointed at Trips")
    void cityRideOutsideEveryCityIsRefused() {
        when(cityService.cityFor(anyDouble(), anyDouble())).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createBooking(USER_ID, city("CAR", 2)));
        assertTrue(ex.getMessage().contains("Trip"));
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("a City ride longer than the city limit is refused and pointed at Trips")
    void cityRideTooFar() {
        route(64.0, 90L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createBooking(USER_ID, city("CAR", 2)));
        assertTrue(ex.getMessage().contains("Trip"));
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("a bike carries one passenger and an auto three")
    void passengerLimits() {
        assertThrows(IllegalArgumentException.class, () -> service.createBooking(USER_ID, city("BIKE", 2)));
        assertThrows(IllegalArgumentException.class, () -> service.createBooking(USER_ID, city("AUTO", 4)));
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("a City ride must say which vehicle")
    void cityRideNeedsVehicle() {
        assertThrows(IllegalArgumentException.class, () -> service.createBooking(USER_ID, city(null, 1)));
        assertThrows(IllegalArgumentException.class, () -> service.createBooking(USER_ID, city("TRUCK", 1)));
    }

    @Test
    @DisplayName("a Trip is by car, and an AC trip passes over a non-AC car")
    void acTripNeedsAcCar() {
        route(460.0, 480L);
        // The AC match is in the query now: an AC trip asks only for AC cars, and car 1 is not one.
        when(driverRepository.findFirstFreeTripDriver(any(), any(), eq(true), eq(List.of(-1L))))
                .thenReturn(Optional.of(driver(2L, true)));
        when(driverRepository.findFirstFreeTripDriver(any(), any(), eq(false), eq(List.of(-1L))))
                .thenReturn(Optional.of(driver(1L, false)));

        service.createBooking(USER_ID, base());

        TravelBooking b = saved();
        assertEquals(TravelBooking.ServiceMode.TRIP, b.getServiceMode());
        assertEquals(VehicleType.CAR, b.getVehicleType());
        assertEquals(TravelBooking.DispatchMode.SCHEDULED, b.getDispatchMode());
        assertEquals(2L, b.getDriverId());
        verify(dispatchService, never()).enqueueAfterCommit(any());
    }

    @Test
    @DisplayName("a non-AC trip takes the first free car, AC or not")
    void nonAcTripTakesAnyCar() {
        route(460.0, 480L);
        when(driverRepository.findFirstFreeTripDriver(any(), any(), eq(false), eq(List.of(-1L))))
                .thenReturn(Optional.of(driver(1L, false)));
        TravelBookingRequest r = base();
        r.setAcType("NON_AC");

        service.createBooking(USER_ID, r);

        assertEquals(1L, saved().getDriverId());
    }

    @Test
    @DisplayName("an AC trip with only non-AC cars free is left for the owner")
    void acTripWithNoAcCar() {
        route(460.0, 480L);
        when(driverRepository.findFirstFreeTripDriver(any(), any(), eq(true), eq(List.of(-1L)))).thenReturn(Optional.empty());

        service.createBooking(USER_ID, base());

        assertNull(saved().getDriverId());
        verify(driverRepository, never()).findFirstFreeTripDriver(any(), any(), anyBoolean(), eq(List.of(1L)));
    }

    private static Driver driver(long id, boolean ac) {
        Driver d = new Driver();
        d.setId(id);
        d.setName("Driver " + id);
        d.setStatus(Driver.Status.ACTIVE);
        d.setVehicleAc(ac);
        return d;
    }

    @Test
    @DisplayName("a Trip is the platform owner's, priced by that owner's rate for the booked car category")
    void tripPricedByOwnersCategoryRate() {
        when(tripRates.rate(PLATFORM_OWNER, com.travelplatform.entity.CarCategory.SUV))
                .thenReturn(rate(com.travelplatform.entity.CarCategory.SUV, "18.00", "250.00"));
        route(100.0, 120L);
        TravelBookingRequest r = base();
        r.setCarCategory("SUV");

        service.createBooking(USER_ID, r);

        TravelBooking b = saved();
        assertEquals(PLATFORM_OWNER, b.getOwnerId());
        assertEquals(com.travelplatform.entity.CarCategory.SUV, b.getCarCategory());
        assertEquals(new BigDecimal("1800.00"), b.getTotalAmount());
    }

    @Test
    @DisplayName("an hourly Trip uses the category's per-hour rate")
    void hourlyTripUsesCategoryPerHour() {
        when(tripRates.rate(PLATFORM_OWNER, com.travelplatform.entity.CarCategory.SEDAN))
                .thenReturn(rate(com.travelplatform.entity.CarCategory.SEDAN, "12.00", "250.00"));
        TravelBookingRequest r = base();
        r.setBookingType("HOUR_BASED");
        r.setBookingHours(4);

        service.createBooking(USER_ID, r);

        TravelBooking b = saved();
        assertEquals(com.travelplatform.entity.CarCategory.SEDAN, b.getCarCategory(), "no category given means a Sedan");
        assertEquals(new BigDecimal("1000.00"), b.getTotalAmount());
        assertEquals(new BigDecimal("250.00"), b.getPricePerHourAtBooking());
    }

    @Test
    @DisplayName("a car category the owner has not priced is refused")
    void unpricedCategoryRefused() {
        when(tripRates.rate(PLATFORM_OWNER, com.travelplatform.entity.CarCategory.TEMPO_TRAVELLER))
                .thenThrow(new IllegalArgumentException("This car type is not available"));
        TravelBookingRequest r = base();
        r.setCarCategory("TEMPO_TRAVELLER");

        assertThrows(IllegalArgumentException.class, () -> service.createBooking(USER_ID, r));
    }

    @Test
    @DisplayName("a City ride belongs to no owner and has no car category")
    void cityRideHasNoOwner() {
        service.createBooking(USER_ID, city("CAR", 2));

        TravelBooking b = saved();
        assertNull(b.getOwnerId());
        assertNull(b.getCarCategory());
    }
}
