package com.travelplatform.service;

import com.travelplatform.entity.Driver;
import com.travelplatform.entity.DriverLedgerEntry;
import com.travelplatform.entity.Owner;
import com.travelplatform.entity.OwnerLedgerEntry;
import com.travelplatform.entity.Payment;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.repository.DriverLedgerRepository;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.OwnerLedgerRepository;
import com.travelplatform.repository.OwnerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** City rides in a travels owner's City car: the money is the owner's, less the city's commission (V35). */
class OwnerLedgerServiceTest {

    private static final long OWNER = 7L;
    private static final long PLATFORM = 1L;

    private OwnerLedgerRepository ledger;
    private OwnerRepository owners;
    private DriverLedgerRepository driverLedger;
    private DriverLedgerService driverLedgerService;
    private OwnerLedgerService service;
    private Driver driver;
    private TravelBooking ride;

    @BeforeEach
    void setUp() {
        ledger = mock(OwnerLedgerRepository.class);
        owners = mock(OwnerRepository.class);
        CityService cities = mock(CityService.class);
        TripRateService rates = mock(TripRateService.class);
        when(rates.platformOwnerId()).thenReturn(PLATFORM);
        when(cities.commissionPercent(3L)).thenReturn(new BigDecimal("10.00"));
        when(ledger.save(any(OwnerLedgerEntry.class))).thenAnswer(i -> i.getArgument(0));
        service = new OwnerLedgerService(ledger, owners, cities);
        service.setTripRates(rates);

        driver = new Driver();
        driver.setId(11L);
        driver.setFleet(Driver.Fleet.OWNER_FLEET);
        driver.setOwnerId(OWNER);
        DriverRepository drivers = mock(DriverRepository.class);
        when(drivers.findById(11L)).thenReturn(Optional.of(driver));
        driverLedger = mock(DriverLedgerRepository.class);
        driverLedgerService = new DriverLedgerService(driverLedger, drivers, cities);
        driverLedgerService.setOwnerLedger(service);

        ride = new TravelBooking();
        ride.setId(UUID.randomUUID());
        ride.setServiceMode(TravelBooking.ServiceMode.CITY);
        ride.setCityId(3L);
        ride.setDriverId(11L);
        ride.setVehicleId(21L);
    }

    private static Payment fare(String amount, Payment.PaymentMethod method) {
        Payment p = new Payment();
        p.setAmount(new BigDecimal(amount));
        p.setPaymentMethod(method);
        return p;
    }

    private OwnerLedgerEntry saved() {
        ArgumentCaptor<OwnerLedgerEntry> c = ArgumentCaptor.forClass(OwnerLedgerEntry.class);
        verify(ledger).save(c.capture());
        return c.getValue();
    }

    @Test
    @DisplayName("an online-paid ride in an owner's City car owes the owner the fare less commission, not the driver")
    void onlineRideGoesToTheOwner() {
        driverLedgerService.recordCompletedRide(ride, fare("200.00", Payment.PaymentMethod.UPI));

        OwnerLedgerEntry e = saved();
        assertEquals(OWNER, e.getOwnerId());
        assertEquals(DriverLedgerEntry.Type.RIDE_EARNING, e.getType());
        assertEquals(new BigDecimal("180.00"), e.getAmount());
        assertEquals(new BigDecimal("20.00"), e.getCommission());
        assertEquals(21L, e.getVehicleId());
        verify(driverLedger, never()).save(any());
    }

    @Test
    @DisplayName("a cash ride leaves the fare with the owner's driver, so the owner owes the commission")
    void cashRideOwesCommission() {
        driverLedgerService.recordCompletedRide(ride, fare("150.00", Payment.PaymentMethod.CASH));

        OwnerLedgerEntry e = saved();
        assertEquals(DriverLedgerEntry.Type.CASH_COMMISSION, e.getType());
        assertEquals(new BigDecimal("-15.00"), e.getAmount());
    }

    @Test
    @DisplayName("the same ride is never recorded twice")
    void idempotent() {
        when(ledger.existsByBookingIdAndType(ride.getId(), DriverLedgerEntry.Type.RIDE_EARNING)).thenReturn(true);
        driverLedgerService.recordCompletedRide(ride, fare("200.00", Payment.PaymentMethod.UPI));
        verify(ledger, never()).save(any());
    }

    @Test
    @DisplayName("the platform owner's own City cars owe nobody")
    void platformFleetSkipped() {
        driver.setOwnerId(PLATFORM);
        driverLedgerService.recordCompletedRide(ride, fare("200.00", Payment.PaymentMethod.UPI));
        verify(ledger, never()).save(any());
    }

    @Test
    @DisplayName("a payout is never more than the owner is owed, and needs a UTR")
    void payoutLimits() {
        Owner o = new Owner();
        o.setId(OWNER);
        o.setTravelsName("Sri Travels");
        when(owners.findById(OWNER)).thenReturn(Optional.of(o));
        when(ledger.balanceOf(OWNER)).thenReturn(new BigDecimal("180.00"));

        assertThrows(IllegalArgumentException.class, () -> service.recordTransfer(OWNER, DriverLedgerEntry.Type.PAYOUT,
                new BigDecimal("200.00"), "123456789012", null, "owner:NJO-0001"));
        assertThrows(IllegalArgumentException.class, () -> service.recordTransfer(OWNER, DriverLedgerEntry.Type.PAYOUT,
                new BigDecimal("100.00"), "", null, "owner:NJO-0001"));

        OwnerLedgerEntry e = service.recordTransfer(OWNER, DriverLedgerEntry.Type.PAYOUT,
                new BigDecimal("180.00"), "123456789012", "Nov", "owner:NJO-0001");
        assertEquals(new BigDecimal("-180.00"), e.getAmount());
    }
}
