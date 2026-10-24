package com.travelplatform.repository;

import com.travelplatform.dto.MonthlyRevenue;
import com.travelplatform.dto.RevenueTotals;
import com.travelplatform.dto.RevenueTrip;
import com.travelplatform.entity.Payment;
import com.travelplatform.entity.TravelBooking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Revenue is money actually received: VERIFIED payments, counted on the day they were verified,
 * at the amount collected. It used to add up booked fares of COMPLETED bookings by the day they
 * were booked - counting a fare in the wrong month, and at whatever the (then editable) booking
 * total said rather than what was paid.
 *
 * <p>Summed by Postgres over NUMERIC money, against real Flyway migrations, with Hibernate
 * validating every entity against the migrated schema.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class RevenueQueriesTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private TravelBookingRepository bookings;
    @Autowired
    private PaymentRepository payments;

    @BeforeEach
    void clean() {
        payments.deleteAll();
        bookings.deleteAll();
    }

    /** A completed booking whose payment has the given status, amount and verification time. */
    private UUID paidTrip(String amount, double km, int days, Payment.PaymentStatus status, LocalDateTime verifiedAt) {
        TravelBooking b = new TravelBooking();
        b.setUserId(UUID.randomUUID());
        b.setUserName("Test");
        b.setUserPhone("9876543210");
        b.setFromPlace("Chennai");
        b.setToPlace("Madurai");
        b.setFromDate(LocalDate.of(2026, 2, 1));
        b.setToDate(LocalDate.of(2026, 2, 1));
        b.setTravelDays(days);
        b.setTravelMembers(2);
        b.setAcType("AC");
        b.setDistanceKm(km);
        b.setEstimatedTimeMinutes(60L);
        b.setTotalAmount(new BigDecimal("99999.00")); // deliberately NOT what was paid
        b.setBookingType(TravelBooking.BookingType.DISTANCE_BASED);
        b.setBookingDate(LocalDateTime.of(2026, 1, 20, 9, 0)); // booked in January
        b.setStatus(TravelBooking.BookingStatus.COMPLETED);
        b = bookings.save(b);

        Payment p = new Payment();
        p.setBookingId(b.getId());
        p.setUserId(b.getUserId());
        p.setAmount(new BigDecimal(amount));
        p.setPaymentMethod(Payment.PaymentMethod.CASH);
        p.setStatus(status);
        p.setCreatedAt(LocalDateTime.of(2026, 1, 20, 9, 0));
        p.setVerifiedDate(verifiedAt);
        payments.save(p);
        return b.getId();
    }

    private static LocalDateTime march(int day) {
        return LocalDateTime.of(2026, 3, day, 10, 0);
    }

    @Test
    @DisplayName("only verified payments count, at the amount paid, in the month they were paid")
    void revenueIsMoneyReceived() {
        paidTrip("0.10", 1.5, 1, Payment.PaymentStatus.VERIFIED, march(5));
        paidTrip("0.20", 2.5, 2, Payment.PaymentStatus.VERIFIED, march(6));
        paidTrip("6457.32", 461.237, 1, Payment.PaymentStatus.VERIFIED, march(7));
        paidTrip("999.00", 10, 1, Payment.PaymentStatus.PENDING, null);       // not paid yet
        paidTrip("500.00", 10, 1, Payment.PaymentStatus.VERIFIED, LocalDateTime.of(2026, 4, 2, 9, 0)); // April

        RevenueTotals totals = bookings.revenueTotals(
                LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 4, 1, 0, 0));

        assertEquals(3, totals.trips());
        assertEquals(new BigDecimal("6457.62"), totals.revenue(), "0.10 + 0.20 + 6457.32, exactly");
        assertEquals(465.237, totals.distanceKm(), 1e-9);
        assertEquals(4, totals.travelDays());

        List<RevenueTrip> trips = bookings.revenueTrips(
                LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 4, 1, 0, 0));
        assertEquals(3, trips.size());
        assertEquals(new BigDecimal("0.10"), trips.get(0).totalAmount(), "the amount paid, not the booking total");
    }

    @Test
    @DisplayName("a Trip paid as advance + balance is one trip, with both payments in the money")
    void advanceAndBalanceAreOneTrip() {
        UUID trip = paidTrip("800.00", 120, 2, Payment.PaymentStatus.VERIFIED, march(10));
        Payment advance = new Payment();
        advance.setBookingId(trip);
        advance.setUserId(UUID.randomUUID());
        advance.setAmount(new BigDecimal("200.00"));
        advance.setPaymentMethod(Payment.PaymentMethod.RAZORPAY);
        advance.setPurpose(Payment.PaymentPurpose.ADVANCE);
        advance.setStatus(Payment.PaymentStatus.VERIFIED);
        advance.setCreatedAt(march(1));
        advance.setVerifiedDate(march(1));
        payments.save(advance);

        RevenueTotals totals = bookings.revenueTotals(
                LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 4, 1, 0, 0));

        assertEquals(1, totals.trips());
        assertEquals(new BigDecimal("1000.00"), totals.revenue());
        assertEquals(120.0, totals.distanceKm(), 1e-9);
        assertEquals(2, totals.travelDays());
        assertEquals(1, bookings.monthlyRevenue(
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2027, 1, 1, 0, 0)).get(0).trips());
    }

    @Test
    @DisplayName("a payment verified at exactly midnight on the 1st belongs to the new month only")
    void periodEndIsExclusive() {
        paidTrip("100.00", 1, 1, Payment.PaymentStatus.VERIFIED, LocalDateTime.of(2026, 4, 1, 0, 0));

        assertEquals(0, bookings.revenueTotals(LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 4, 1, 0, 0)).trips());
        assertEquals(BigDecimal.ZERO, bookings.revenueTotals(LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 4, 1, 0, 0)).revenue());
        assertEquals(1, bookings.revenueTotals(LocalDateTime.of(2026, 4, 1, 0, 0), LocalDateTime.of(2026, 5, 1, 0, 0)).trips());
    }

    @Test
    @DisplayName("the twelve-bar chart is one GROUP BY on the month of payment")
    void monthlySeriesByPaymentMonth() {
        paidTrip("100.00", 10, 1, Payment.PaymentStatus.VERIFIED, LocalDateTime.of(2026, 1, 15, 9, 0));
        paidTrip("250.50", 20, 1, Payment.PaymentStatus.VERIFIED, LocalDateTime.of(2026, 1, 20, 9, 0));
        paidTrip("75.25", 5, 1, Payment.PaymentStatus.VERIFIED, LocalDateTime.of(2026, 12, 31, 23, 59));
        paidTrip("10.00", 1, 1, Payment.PaymentStatus.VERIFIED, LocalDateTime.of(2027, 1, 1, 0, 0));

        List<MonthlyRevenue> months = bookings.monthlyRevenue(
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2027, 1, 1, 0, 0));

        assertEquals(2, months.size());
        assertEquals(1, months.get(0).month());
        assertEquals(2, months.get(0).trips());
        assertEquals(new BigDecimal("350.50"), months.get(0).revenue());
        assertEquals(12, months.get(1).month());
        assertEquals(new BigDecimal("75.25"), months.get(1).revenue());
    }
}
