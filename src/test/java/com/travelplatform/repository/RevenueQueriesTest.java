package com.travelplatform.repository;

import com.travelplatform.dto.MonthlyRevenue;
import com.travelplatform.dto.RevenueTotals;
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
 * Revenue used to load every completed booking as a full entity and add the fares up as doubles
 * in Java - memory proportional to a year's trips, and floating-point dust in the totals. It is
 * now summed by Postgres over NUMERIC columns: exact to the paisa, one row back.
 *
 * <p>Runs Flyway against real Postgres and lets Hibernate validate every entity against the
 * migrated schema, so a money column whose type does not match its entity fails here.
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
    private TravelBookingRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private void booking(String amount, double km, int days, LocalDateTime bookedAt, TravelBooking.BookingStatus status) {
        TravelBooking b = new TravelBooking();
        b.setUserId(UUID.randomUUID());
        b.setUserName("Test");
        b.setUserPhone("9876543210");
        b.setFromPlace("Chennai");
        b.setToPlace("Madurai");
        b.setFromDate(LocalDate.of(2026, 3, 1));
        b.setToDate(LocalDate.of(2026, 3, 1));
        b.setTravelDays(days);
        b.setTravelMembers(2);
        b.setAcType("AC");
        b.setDistanceKm(km);
        b.setEstimatedTimeMinutes(60L);
        b.setTotalAmount(new BigDecimal(amount));
        b.setBookingType(TravelBooking.BookingType.DISTANCE_BASED);
        b.setBookingDate(bookedAt);
        b.setStatus(status);
        repository.save(b);
    }

    private static final TravelBooking.BookingStatus DONE = TravelBooking.BookingStatus.COMPLETED;

    @Test
    @DisplayName("totals are exact to the paisa - 0.10 + 0.20 is 0.30, not 0.30000000000000004")
    void totalsAreExact() {
        booking("0.10", 1.5, 1, LocalDateTime.of(2026, 3, 5, 10, 0), DONE);
        booking("0.20", 2.5, 2, LocalDateTime.of(2026, 3, 6, 10, 0), DONE);
        booking("6457.32", 461.237, 1, LocalDateTime.of(2026, 3, 7, 10, 0), DONE);
        booking("999.00", 10, 1, LocalDateTime.of(2026, 3, 8, 10, 0), TravelBooking.BookingStatus.CANCELLED);

        RevenueTotals totals = repository.revenueTotals(DONE,
                LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 4, 1, 0, 0));

        assertEquals(3, totals.trips());
        assertEquals(new BigDecimal("6457.62"), totals.revenue());
        assertEquals(465.237, totals.distanceKm(), 1e-9);
        assertEquals(4, totals.travelDays());
    }

    @Test
    @DisplayName("a booking at exactly midnight on the 1st belongs to the new month only")
    void periodEndIsExclusive() {
        booking("100.00", 1, 1, LocalDateTime.of(2026, 4, 1, 0, 0), DONE);

        RevenueTotals march = repository.revenueTotals(DONE,
                LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 4, 1, 0, 0));
        RevenueTotals april = repository.revenueTotals(DONE,
                LocalDateTime.of(2026, 4, 1, 0, 0), LocalDateTime.of(2026, 5, 1, 0, 0));

        assertEquals(0, march.trips());
        assertEquals(BigDecimal.ZERO, march.revenue());
        assertEquals(1, april.trips());
    }

    @Test
    @DisplayName("the twelve-bar chart is one GROUP BY, not a year of rows")
    void monthlySeriesIsGroupedInSql() {
        booking("100.00", 10, 1, LocalDateTime.of(2026, 1, 15, 9, 0), DONE);
        booking("250.50", 20, 1, LocalDateTime.of(2026, 1, 20, 9, 0), DONE);
        booking("75.25", 5, 1, LocalDateTime.of(2026, 12, 31, 23, 59), DONE);
        booking("10.00", 1, 1, LocalDateTime.of(2027, 1, 1, 0, 0), DONE);

        List<MonthlyRevenue> months = repository.monthlyRevenue(DONE,
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2027, 1, 1, 0, 0));

        assertEquals(2, months.size());
        assertEquals(1, months.get(0).month());
        assertEquals(2, months.get(0).trips());
        assertEquals(new BigDecimal("350.50"), months.get(0).revenue());
        assertEquals(12, months.get(1).month());
        assertEquals(new BigDecimal("75.25"), months.get(1).revenue());
    }
}
