package com.travelplatform.repository;

import com.travelplatform.entity.TravelBooking;
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
 * "My bookings" came back in whatever order Postgres stored the rows. An UPDATE writes a new row
 * version at the end of the table, so a booking that had just been confirmed jumped to the last
 * page of the 6-per-page list - found by the end-to-end suite, where a freshly confirmed trip sat
 * on page 4. Newest first, always.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class BookingListOrderTest {

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

    private TravelBooking booking(UUID user, LocalDateTime bookedAt, String to) {
        TravelBooking b = new TravelBooking();
        b.setUserId(user);
        b.setUserName("Kavya");
        b.setUserPhone("9876543210");
        b.setFromPlace("Chennai");
        b.setToPlace(to);
        b.setFromDate(LocalDate.of(2026, 12, 1));
        b.setToDate(LocalDate.of(2026, 12, 1));
        b.setTravelDays(1);
        b.setTravelMembers(2);
        b.setAcType("AC");
        b.setDistanceKm(100.0);
        b.setEstimatedTimeMinutes(120L);
        b.setTotalAmount(new BigDecimal("1400.00"));
        b.setBookingType(TravelBooking.BookingType.DISTANCE_BASED);
        b.setBookingDate(bookedAt);
        b.setStatus(TravelBooking.BookingStatus.PENDING);
        return bookings.saveAndFlush(b);
    }

    @Test
    @DisplayName("a traveller's bookings are newest first, even after an older one is updated")
    void newestFirstRegardlessOfUpdates() {
        UUID user = UUID.randomUUID();
        TravelBooking oldest = booking(user, LocalDateTime.of(2026, 10, 1, 9, 0), "Vellore");
        booking(user, LocalDateTime.of(2026, 10, 2, 9, 0), "Madurai");
        booking(user, LocalDateTime.of(2026, 10, 3, 9, 0), "Ooty");

        // Updating the oldest moves its row to the end of the table in Postgres.
        oldest.setStatus(TravelBooking.BookingStatus.CONFIRMED);
        bookings.saveAndFlush(oldest);

        List<String> order = bookings.findByUserIdOrderByBookingDateDesc(user).stream()
                .map(TravelBooking::getToPlace).toList();

        assertEquals(List.of("Ooty", "Madurai", "Vellore"), order);
    }
}
