package com.travelplatform.repository;

import com.travelplatform.entity.PackageBooking;
import com.travelplatform.entity.Payment;
import com.travelplatform.entity.Review;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Two requests changing the same row must not silently overwrite each other. Payment had no
 * version column, so the owner's "verified" could be overwritten back to "pending" by a QR refresh
 * that had read the row a moment earlier. Two simultaneous review submissions both passed the
 * "already reviewed?" check and both inserted.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ConcurrentWriteSafetyTest {

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
    private PaymentRepository payments;
    @Autowired
    private PackageBookingRepository packageBookings;
    @Autowired
    private ReviewRepository reviews;
    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("a payment saved from a stale read is refused, not silently applied")
    void staleWriteToPaymentIsRefused() {
        Payment p = new Payment();
        p.setBookingId(UUID.randomUUID());
        p.setUserId(UUID.randomUUID());
        p.setAmount(new BigDecimal("2500.00"));
        p.setPaymentMethod(Payment.PaymentMethod.UPI);
        p.setStatus(Payment.PaymentStatus.PENDING);
        p.setCreatedAt(LocalDateTime.now());
        UUID id = payments.saveAndFlush(p).getId();
        em.clear();

        Payment ownerCopy = payments.findById(id).orElseThrow();
        em.detach(ownerCopy);
        Payment qrRefreshCopy = payments.findById(id).orElseThrow();
        em.detach(qrRefreshCopy);

        ownerCopy.setStatus(Payment.PaymentStatus.VERIFIED);
        payments.saveAndFlush(ownerCopy);

        qrRefreshCopy.setStatus(Payment.PaymentStatus.PENDING);
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> payments.saveAndFlush(qrRefreshCopy));
    }

    @Test
    @DisplayName("a package booking saved from a stale read is refused")
    void staleWriteToPackageBookingIsRefused() {
        PackageBooking b = new PackageBooking();
        b.setPackageId(1L);
        b.setUserId(UUID.randomUUID());
        b.setUserName("Kavya");
        b.setUserEmail("k@x.com");
        b.setUserPhone("9876543210");
        b.setNumberOfPersons(2);
        b.setTravelDate(LocalDate.now().plusDays(10));
        b.setTotalAmount(new BigDecimal("8000.00"));
        b.setStatus(PackageBooking.BookingStatus.PENDING);
        b.setPackageName("Ooty");
        b.setPackageCategory("HILL_STATION");
        UUID id = packageBookings.saveAndFlush(b).getId();
        em.clear();

        PackageBooking a1 = packageBookings.findById(id).orElseThrow();
        em.detach(a1);
        PackageBooking a2 = packageBookings.findById(id).orElseThrow();
        em.detach(a2);

        a1.setStatus(PackageBooking.BookingStatus.CONFIRMED);
        packageBookings.saveAndFlush(a1);
        a2.setStatus(PackageBooking.BookingStatus.CANCELLED);
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> packageBookings.saveAndFlush(a2));
    }

    @Test
    @DisplayName("a booking can only ever have one review")
    void oneReviewPerBooking() {
        UUID booking = UUID.randomUUID();
        reviews.saveAndFlush(review(booking));
        assertThrows(DataIntegrityViolationException.class, () -> reviews.saveAndFlush(review(booking)));
    }

    private static Review review(UUID bookingId) {
        Review r = new Review();
        r.setBookingId(bookingId);
        r.setUserId(UUID.randomUUID());
        r.setDriverId(7L);
        r.setRating(5);
        r.setDriverName("Murugan");
        r.setUserName("Kavya");
        r.setCreatedAt(LocalDateTime.now());
        return r;
    }
}
