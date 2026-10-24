package com.travelplatform.service;

import com.travelplatform.entity.Driver;
import com.travelplatform.entity.DriverLedgerEntry;
import com.travelplatform.entity.Payment;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.repository.DriverLedgerRepository;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.security.FieldEncryption;
import com.travelplatform.security.FieldEncryptionTest;
import org.junit.jupiter.api.AfterEach;
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

class DriverLedgerServiceTest {

    private DriverLedgerRepository ledger;
    private DriverLedgerService service;
    private Driver driver;
    private TravelBooking ride;

    @BeforeEach
    void setUp() {
        ledger = mock(DriverLedgerRepository.class);
        DriverRepository drivers = mock(DriverRepository.class);
        CityService cities = mock(CityService.class);
        service = new DriverLedgerService(ledger, drivers, cities);

        driver = new Driver();
        driver.setId(5L);
        driver.setName("Ravi");
        driver.setFleet(Driver.Fleet.INDEPENDENT);
        when(drivers.findById(5L)).thenReturn(Optional.of(driver));
        when(cities.commissionPercent(3L)).thenReturn(new BigDecimal("12.50"));
        when(ledger.save(any(DriverLedgerEntry.class))).thenAnswer(i -> i.getArgument(0));

        ride = new TravelBooking();
        ride.setId(UUID.randomUUID());
        ride.setServiceMode(TravelBooking.ServiceMode.CITY);
        ride.setCityId(3L);
        ride.setDriverId(5L);
    }

    @AfterEach
    void noKey() {
        FieldEncryption.configure(null);
    }

    private static Payment fare(String amount, Payment.PaymentMethod method) {
        Payment p = new Payment();
        p.setAmount(new BigDecimal(amount));
        p.setPaymentMethod(method);
        return p;
    }

    private DriverLedgerEntry saved() {
        ArgumentCaptor<DriverLedgerEntry> c = ArgumentCaptor.forClass(DriverLedgerEntry.class);
        verify(ledger).save(c.capture());
        return c.getValue();
    }

    @Test
    @DisplayName("paid online: the driver is owed the fare less the city's commission")
    void onlineRideEarns() {
        service.recordCompletedRide(ride, fare("240.00", Payment.PaymentMethod.RAZORPAY));

        DriverLedgerEntry e = saved();
        assertEquals(DriverLedgerEntry.Type.RIDE_EARNING, e.getType());
        assertEquals(new BigDecimal("30.00"), e.getCommission());
        assertEquals(new BigDecimal("210.00"), e.getAmount());
        assertEquals(new BigDecimal("12.50"), e.getCommissionPercent());
    }

    @Test
    @DisplayName("paid in cash: the driver kept the fare and owes the commission")
    void cashRideOwesCommission() {
        service.recordCompletedRide(ride, fare("99.99", Payment.PaymentMethod.CASH));

        DriverLedgerEntry e = saved();
        assertEquals(DriverLedgerEntry.Type.CASH_COMMISSION, e.getType());
        assertEquals(new BigDecimal("-12.50"), e.getAmount());
    }

    @Test
    @DisplayName("owner-fleet drivers and Trips never enter the ledger")
    void outOfScopeRidesIgnored() {
        driver.setFleet(Driver.Fleet.OWNER_FLEET);
        service.recordCompletedRide(ride, fare("240.00", Payment.PaymentMethod.RAZORPAY));
        driver.setFleet(Driver.Fleet.INDEPENDENT);
        ride.setServiceMode(TravelBooking.ServiceMode.TRIP);
        service.recordCompletedRide(ride, fare("240.00", Payment.PaymentMethod.RAZORPAY));
        verify(ledger, never()).save(any());
    }

    @Test
    @DisplayName("a ride is recorded once, however often completion runs")
    void rideRecordedOnce() {
        when(ledger.existsByBookingIdAndType(ride.getId(), DriverLedgerEntry.Type.RIDE_EARNING)).thenReturn(true);
        service.recordCompletedRide(ride, fare("240.00", Payment.PaymentMethod.UPI));
        verify(ledger, never()).save(any());
    }

    @Test
    @DisplayName("payouts: never more than owed, always with a UTR, recorded as money out")
    void payoutRules() {
        when(ledger.balanceOf(5L)).thenReturn(new BigDecimal("500.00"));

        assertThrows(IllegalArgumentException.class, () -> service.recordTransfer(5L, DriverLedgerEntry.Type.PAYOUT,
                new BigDecimal("500.01"), "UTR123456", null, "owner:o"));
        assertThrows(IllegalArgumentException.class, () -> service.recordTransfer(5L, DriverLedgerEntry.Type.PAYOUT,
                new BigDecimal("100"), "", null, "owner:o"));

        DriverLedgerEntry e = service.recordTransfer(5L, DriverLedgerEntry.Type.PAYOUT,
                new BigDecimal("500.00"), "utr123456", "Oct week 4", "owner:o");
        assertEquals(new BigDecimal("-500.00"), e.getAmount());
        assertEquals("UTR123456", e.getReference());
        verify(ledger, org.mockito.Mockito.atLeastOnce()).lockDriver(5L);
    }

    @Test
    @DisplayName("collections: only what the driver owes")
    void collectionRules() {
        when(ledger.balanceOf(5L)).thenReturn(new BigDecimal("-40.00"));
        assertThrows(IllegalArgumentException.class, () -> service.recordTransfer(5L, DriverLedgerEntry.Type.COLLECTION,
                new BigDecimal("41"), "UTR123456", null, "owner:o"));
        assertEquals(new BigDecimal("40.00"), service.recordTransfer(5L, DriverLedgerEntry.Type.COLLECTION,
                new BigDecimal("40.00"), "UTR123456", null, "owner:o").getAmount());
    }

    @Test
    @DisplayName("UPI IDs are validated, stored lower-case, and refused when encryption is not set up")
    void payoutUpi() {
        assertThrows(IllegalArgumentException.class, () -> service.setPayoutUpi(5L, "not a upi"));
        assertThrows(IllegalStateException.class, () -> service.setPayoutUpi(5L, "ravi@okaxis"));

        FieldEncryption.configure(FieldEncryptionTest.KEY);
        service.setPayoutUpi(5L, " Ravi.K@OkAxis ");
        assertEquals("ravi.k@okaxis", driver.getPayoutUpiId());
    }

    @Test
    @DisplayName("the driver sees a masked UPI ID")
    void masked() {
        assertEquals("ra••••@okaxis", DriverLedgerService.mask("ravi.k@okaxis"));
        assertEquals("••@ybl", DriverLedgerService.mask("ab@ybl"));
    }
}
