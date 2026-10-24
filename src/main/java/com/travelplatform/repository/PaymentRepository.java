package com.travelplatform.repository;

import com.travelplatform.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByBookingIdAndPurpose(UUID bookingId, Payment.PaymentPurpose purpose);

    /**
     * The booking's fare payment - the whole fare, or its balance after an advance. Every flow that
     * settles a trip (cash, UPI verify, end-trip, summary) means this one, so it keeps the name it
     * had when a booking could only have one payment (V20).
     */
    default Optional<Payment> findByBookingId(UUID bookingId) {
        return findByBookingIdAndPurpose(bookingId, Payment.PaymentPurpose.FARE);
    }

    default Optional<Payment> findAdvance(UUID bookingId) {
        return findByBookingIdAndPurpose(bookingId, Payment.PaymentPurpose.ADVANCE);
    }

    List<Payment> findByStatus(Payment.PaymentStatus status);
    List<Payment> findByUserId(UUID userId);
    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);
    Optional<Payment> findByRazorpayPaymentId(String razorpayPaymentId);
}
