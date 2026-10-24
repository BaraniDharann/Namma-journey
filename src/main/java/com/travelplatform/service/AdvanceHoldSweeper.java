package com.travelplatform.service;

import com.travelplatform.entity.Payment;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.repository.PaymentRepository;
import com.travelplatform.repository.TravelBookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Releases Trips whose advance was never paid. An unpaid booking still holds its driver's dates
 * ({@code no_driver_overlap}), so without this a passenger who closed the payment screen would
 * block a car for days.
 *
 * <p>Each booking is released in its own transaction. If the advance lands at the same instant,
 * {@code @Version} lets only one side win: either the payment settles first and this skips the
 * booking, or this releases it first and the payment is refunded automatically
 * (RazorpayPaymentService.apply).
 */
@Component
public class AdvanceHoldSweeper {

    private static final Logger log = LoggerFactory.getLogger(AdvanceHoldSweeper.class);

    private final TravelBookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final NotificationService notificationService;
    private final TransactionTemplate tx;

    public AdvanceHoldSweeper(TravelBookingRepository bookingRepository, PaymentRepository paymentRepository,
                              NotificationService notificationService, TransactionTemplate tx) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.notificationService = notificationService;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${app.trip.advance-sweep-interval-ms:60000}",
               initialDelayString = "${app.trip.advance-sweep-initial-delay-ms:30000}")
    public void sweep() {
        List<UUID> expired = bookingRepository.findExpiredAdvanceHolds(LocalDateTime.now());
        for (UUID id : expired) {
            try {
                tx.executeWithoutResult(s -> release(id));
            } catch (OptimisticLockingFailureException raced) {
                log.info("Hold on booking {} changed while releasing it (advance just paid?); leaving it", id);
            }
        }
    }

    void release(UUID bookingId) {
        TravelBooking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != TravelBooking.BookingStatus.PENDING || !booking.isAdvanceDue()) {
            return;
        }
        paymentRepository.findAdvance(bookingId)
                .filter(p -> p.getStatus() == Payment.PaymentStatus.PENDING)
                .ifPresent(p -> {
                    p.setStatus(Payment.PaymentStatus.FAILED);
                    paymentRepository.save(p);
                });
        booking.setStatus(TravelBooking.BookingStatus.CANCELLED);
        booking.setHoldExpiresAt(null);
        bookingRepository.saveAndFlush(booking);
        notificationService.notifyHoldReleased(booking);
        log.info("Released booking {}: advance not paid in time", bookingId);
    }
}
