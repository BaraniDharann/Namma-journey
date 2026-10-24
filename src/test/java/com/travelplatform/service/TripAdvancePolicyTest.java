package com.travelplatform.service;

import com.travelplatform.entity.TravelBooking;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TripAdvancePolicyTest {

    private final TripAdvancePolicy policy = new TripAdvancePolicy(20, 30, 24);

    private static TravelBooking trip(TravelBooking.BookingStatus status, LocalDate from) {
        TravelBooking b = new TravelBooking();
        b.setServiceMode(TravelBooking.ServiceMode.TRIP);
        b.setStatus(status);
        b.setFromDate(from);
        return b;
    }

    @Test
    @DisplayName("20 % to the whole rupee, never below a rupee or above the fare")
    void advanceAmount() {
        assertEquals(new BigDecimal("500.00"), policy.advanceFor(new BigDecimal("2500.50")));
        assertEquals(new BigDecimal("200.00"), policy.advanceFor(new BigDecimal("999.00")));
        assertEquals(new BigDecimal("1.00"), policy.advanceFor(new BigDecimal("2.00")));
        assertEquals(new BigDecimal("0.50"), policy.advanceFor(new BigDecimal("0.50")));
    }

    @Test
    @DisplayName("only Trips need an advance, and 0 % switches it off")
    void appliesToTripsOnly() {
        TravelBooking city = trip(TravelBooking.BookingStatus.PENDING, LocalDate.now());
        city.setServiceMode(TravelBooking.ServiceMode.CITY);
        assertFalse(policy.applies(city));
        assertTrue(policy.applies(trip(TravelBooking.BookingStatus.PENDING, LocalDate.now())));
        assertFalse(new TripAdvancePolicy(0, 30, 24).applies(trip(TravelBooking.BookingStatus.PENDING, LocalDate.now())));
    }

    @Test
    @DisplayName("confirmed trip: refunded until 24 h before the first day starts, kept after")
    void confirmedRefundWindow() {
        TravelBooking b = trip(TravelBooking.BookingStatus.CONFIRMED, LocalDate.of(2026, 11, 10));
        assertTrue(policy.refundsAdvanceOnCancel(b, LocalDateTime.of(2026, 11, 8, 23, 59)));
        assertFalse(policy.refundsAdvanceOnCancel(b, LocalDateTime.of(2026, 11, 9, 0, 0)));
        assertFalse(policy.refundsAdvanceOnCancel(b, LocalDateTime.of(2026, 11, 9, 18, 0)));
    }

    @Test
    @DisplayName("no car was ever committed: the advance always comes back")
    void unconfirmedAlwaysRefunded() {
        TravelBooking b = trip(TravelBooking.BookingStatus.PENDING, LocalDate.of(2026, 11, 10));
        assertTrue(policy.refundsAdvanceOnCancel(b, LocalDateTime.of(2026, 11, 9, 23, 0)));
    }
}
