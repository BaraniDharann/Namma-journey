package com.travelplatform.service;

import com.travelplatform.dto.CashPaymentRequest;
import com.travelplatform.dto.PaymentRequest;
import com.travelplatform.dto.PaymentResponse;
import com.travelplatform.entity.Payment;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.repository.PaymentRepository;
import com.travelplatform.repository.TravelBookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

@Service
@RequiredArgsConstructor
public class PaymentService {
    
    private final PaymentRepository paymentRepository;
    private final TravelBookingRepository bookingRepository;
    private final com.travelplatform.repository.DriverRepository driverRepository;
    private final NotificationService notificationService;
    private final LocationTrackingService locationTrackingService;


    /**
     * What the passenger still owes for the trip: the fare, less a paid advance (V20). Every fare
     * payment - online, UPI QR or cash - is created for this amount, and cash collection is checked
     * against it, so the advance is never charged twice.
     */
    public static BigDecimal amountDue(TravelBooking booking) {
        BigDecimal total = booking.getTotalAmount();
        if (total == null) {
            return null;
        }
        if (booking.getAdvancePaidAt() != null && booking.getAdvanceAmount() != null) {
            return total.subtract(booking.getAdvanceAmount()).max(BigDecimal.ZERO);
        }
        return total;
    }

    // Field-injected so the tests that build PaymentService by hand are unaffected.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private DriverPresenceService presenceService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private DriverLedgerService ledgerService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.travelplatform.service.dispatch.DriverStateUpdates driverStates;

    /**
     * A ride is done and paid: an independent driver's share (or the commission they owe on cash)
     * goes into their ledger in this same transaction (V21). No-op for Trips and fleet drivers.
     */
    private void recordEarnings(TravelBooking booking, Payment fare) {
        if (ledgerService != null) {
            ledgerService.recordCompletedRide(booking, fare);
        }
    }

    /**
     * The ride is over on the ground. The booking stays STARTED until it is paid, but the driver
     * is free from now (V13), and the drop-off is where they are: that is where the next Ride Now
     * search should find them. Their phone's next position report replaces it with the real fix.
     */
    private void markTripEnded(TravelBooking booking, Long driverId) {
        if (booking.getTripEndedAt() == null) {
            booking.setTripEndedAt(LocalDateTime.now());
            bookingRepository.save(booking);
        }
        if (driverStates != null) {
            driverStates.freed(driverId, booking); // free for the next City ride while payment settles
        }
        if (presenceService != null && booking.getToLat() != null && booking.getToLon() != null
                && driverRepository.findById(driverId)
                        .map(d -> d.getStatus() == com.travelplatform.entity.Driver.Status.ACTIVE)
                        .orElse(false)) {
            presenceService.report(driverId, booking.getToLat(), booking.getToLon());
        }
    }

    @Transactional
    public PaymentResponse initiatePayment(UUID userId, UUID bookingId, PaymentRequest request) {
        TravelBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (!booking.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized access");
        }

        if (booking.getStatus() != TravelBooking.BookingStatus.CONFIRMED) {
            throw new RuntimeException("Booking must be confirmed before payment");
        }

        // Every non-cash payment goes through Razorpay (the "Pay online" checkout, or the driver's
        // end-of-ride QR, which is a Razorpay Payment Link). This endpoint only records "I will pay
        // cash"; a plain UPI QR to a fixed UPI ID is no longer issued anywhere.
        Payment.PaymentMethod method = parseMethod(request);

        java.util.Optional<Payment> existing = paymentRepository.findByBookingId(bookingId);
        if (existing.isPresent()) {
            Payment p = existing.get();
            if (p.getStatus() == Payment.PaymentStatus.VERIFIED) {
                throw new RuntimeException("Payment already completed");
            }
            rememberUpiReference(p, request);
            // A passenger who opened online checkout (or was shown the driver's QR) and then hands
            // over cash: the open Razorpay order/link stays on the row, so if it is paid after all
            // the webhook still finds it and refunds the second charge.
            p.setPaymentMethod(method);
            p.setUpiLinkExpiresAt(null);
            PaymentResponse response = mapToResponse(paymentRepository.save(p));
            response.setMessage("Pay cash to driver. Driver will mark payment received.");
            return response;
        }

        // The booking is the only source of truth for what is owed. The request deliberately
        // carries no amount: if it did, a customer could name their own fare.
        Payment payment = new Payment();
        payment.setBookingId(bookingId);
        payment.setUserId(userId);
        payment.setAmount(amountDue(booking));
        payment.setPaymentMethod(method);
        payment.setStatus(Payment.PaymentStatus.PENDING);
        payment.setDriverId(booking.getDriverId());
        payment.setCreatedAt(LocalDateTime.now());
        rememberUpiReference(payment, request);

        PaymentResponse response = mapToResponse(paymentRepository.save(payment));
        response.setMessage("Pay cash to driver. Driver will mark payment received.");
        return response;
    }
    
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "dailyRevenue",   allEntries = true),
            @CacheEvict(value = "monthlyRevenue", allEntries = true),
            @CacheEvict(value = "yearlyRevenue",  allEntries = true),
            @CacheEvict(value = "monthlyRevenueSeries", allEntries = true)
    })
    public PaymentResponse markCashReceived(Long driverId, UUID bookingId, CashPaymentRequest request) {
        TravelBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (booking.getDriverId() == null || !booking.getDriverId().equals(driverId)) {
            throw new RuntimeException("Unauthorized: Booking not assigned to you");
        }
        
        // A passenger can hand over cash before the driver taps "End ride", so there may be no
        // payment row yet: raise it here for the amount the booking says is due.
        Payment payment = paymentRepository.findByBookingId(bookingId).orElseGet(() -> {
            Payment p = new Payment();
            p.setBookingId(bookingId);
            p.setUserId(booking.getUserId());
            p.setAmount(amountDue(booking));
            p.setPaymentMethod(Payment.PaymentMethod.CASH);
            p.setStatus(Payment.PaymentStatus.PENDING);
            p.setDriverId(driverId);
            p.setCreatedAt(LocalDateTime.now());
            return p;
        });

        // Idempotency guard. Without it a driver could call this repeatedly on a settled
        // booking: each call re-stamped the payment, re-completed the booking, re-sent the
        // "payment received" notification and evicted the revenue caches again, so one cash
        // fare could be counted into owner revenue as many times as the endpoint was hit.
        if (payment.getStatus() == Payment.PaymentStatus.VERIFIED) {
            throw new RuntimeException("Payment already completed for this booking");
        }

        // Cash changes hands at the end of a ride, so the ride must have started. Accepting any
        // non-completed state let a confirmed trip that never happened be settled and booked
        // into revenue.
        if (booking.getStatus() != TravelBooking.BookingStatus.STARTED) {
            throw new RuntimeException("Cash can only be collected on a trip that has started");
        }

        // "End ride" always raises a UPI QR, and a passenger may also have opened online checkout.
        // If they then pay the driver in cash, that unpaid UPI/online invoice becomes a cash one -
        // refusing it ("This is not a cash payment") left the driver with no way to finish the
        // ride. Already-paid rows were stopped above, so nothing settled is ever switched.
        if (payment.getPaymentMethod() != Payment.PaymentMethod.CASH) {
            payment.setPaymentMethod(Payment.PaymentMethod.CASH);
            payment.setUpiLinkExpiresAt(null);
        }

        // amountReceived arrived on the request but was never looked at: the payment was marked
        // VERIFIED for the full fare no matter what the driver typed. A driver could then report
        // collecting 1 rupee on a 2500 rupee trip and the booking would settle as paid in full,
        // with owner revenue reporting the full fare against cash that never arrived. Compare it
        // to the amount actually due instead. Money is NUMERIC now (V8), so the comparison is exact
        // to the paisa - no floating-point tolerance to reason about. compareTo, not equals:
        // 2500 and 2500.00 are the same amount with different scales.
        BigDecimal received = request != null ? request.getAmountReceived() : null;
        if (received == null) {
            throw new IllegalArgumentException("Amount received is required");
        }
        if (Money.paise(received).compareTo(payment.getAmount()) != 0) {
            throw new IllegalArgumentException(String.format(
                    Locale.ROOT,
                    "Amount received (%.2f) does not match the amount due (%.2f)",
                    received, payment.getAmount()));
        }
        
        payment.setPaymentDate(LocalDateTime.now());
        payment.setStatus(Payment.PaymentStatus.VERIFIED);
        payment.setVerifiedDate(LocalDateTime.now());
        // The driver is the actor for cash: they are the one attesting the money changed hands.
        payment.setVerifiedBy("driver:" + driverId);
        Payment updated = paymentRepository.save(payment);
        
        booking.setStatus(TravelBooking.BookingStatus.COMPLETED);
        bookingRepository.save(booking);
        locationTrackingService.removeLocation(bookingId.toString());
        recordEarnings(booking, updated);

        notificationService.notifyPaymentReceived(booking);

        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(updated.getId());
        response.setBookingId(bookingId);
        response.setAmount(updated.getAmount());
        response.setPaymentMethod(updated.getPaymentMethod().name());
        response.setStatus(updated.getStatus().name());
        response.setMessage("Cash payment received. Trip completed. RCM calculation will be processed.");
        
        return response;
    }
    
    /** A payment an owner may act on: their own Trip's, or for the platform owner a City ride's. Else not found. */
    @Transactional(readOnly = true)
    public void requireVisibleTo(UUID paymentId, Long ownerId, boolean platform) {
        paymentRepository.findById(paymentId)
                .filter(p -> p.getOwnerId() != null ? p.getOwnerId().equals(ownerId) : platform)
                .orElseThrow(() -> new com.travelplatform.exception.ResourceNotFoundException("Payment not found"));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "dailyRevenue",   allEntries = true),
            @CacheEvict(value = "monthlyRevenue", allEntries = true),
            @CacheEvict(value = "yearlyRevenue",  allEntries = true),
            @CacheEvict(value = "monthlyRevenueSeries", allEntries = true)
    })
    public PaymentResponse verifyPayment(UUID paymentId, String verifiedBy) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        // This is the manual step where an owner confirms a UPI transfer really landed, and it
        // is what completes the trip and books the fare into revenue. It used to accept any
        // payment in any state: re-posting it against an already-settled payment re-completed
        // the booking, re-notified the passenger and evicted the revenue caches again, so one
        // fare could be recognised repeatedly. It also accepted CASH payments, bypassing the
        // driver-side collection check entirely.
        if (payment.getStatus() == Payment.PaymentStatus.VERIFIED) {
            throw new RuntimeException("Payment already verified");
        }
        if (payment.getPaymentMethod() != Payment.PaymentMethod.UPI) {
            throw new IllegalArgumentException(
                    "Only UPI payments are verified here; cash is settled by the assigned driver");
        }
        
        TravelBooking booking = bookingRepository.findById(payment.getBookingId())
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        // Only a live booking's payment can be accepted: a cancelled one has to be refunded, not
        // booked as revenue.
        if (booking.getStatus() != TravelBooking.BookingStatus.CONFIRMED
                && booking.getStatus() != TravelBooking.BookingStatus.STARTED) {
            throw new RuntimeException("This booking is " + booking.getStatus() + "; its payment cannot be verified");
        }

        return settle(payment, booking, "owner:" + verifiedBy);
    }

    /**
     * Marks a payment received and moves the trip on: shared by the owner's manual UPI check and
     * Razorpay's signed confirmation, so both settle a fare identically. Callers have already
     * checked the payment is not VERIFIED and the booking is CONFIRMED or STARTED, and carry the
     * revenue cache evictions themselves.
     */
    PaymentResponse settle(Payment payment, TravelBooking booking, String actor) {
        payment.setStatus(Payment.PaymentStatus.VERIFIED);
        payment.setVerifiedDate(LocalDateTime.now());
        if (payment.getPaymentDate() == null) {
            payment.setPaymentDate(LocalDateTime.now());
        }
        payment.setVerifiedBy(actor);
        Payment verified = paymentRepository.save(payment);

        // Paying settles the money, not the trip. A started trip being paid for is over; a
        // prepayment on a CONFIRMED booking leaves the ride still to happen - it completes when the
        // driver ends it (generateTripEndQr). This used to mark prepaid trips COMPLETED before the
        // car had even arrived.
        boolean tripOver = booking.getStatus() == TravelBooking.BookingStatus.STARTED;
        if (tripOver) {
            booking.setStatus(TravelBooking.BookingStatus.COMPLETED);
            bookingRepository.save(booking);
            locationTrackingService.removeLocation(payment.getBookingId().toString());
            recordEarnings(booking, verified);
        }

        notificationService.notifyPaymentReceived(booking);

        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(verified.getId());
        response.setBookingId(verified.getBookingId());
        response.setAmount(verified.getAmount());
        response.setPaymentMethod(verified.getPaymentMethod().name());
        response.setStatus(verified.getStatus().name());
        response.setMessage(tripOver ? "Payment verified. Trip completed." : "Prepayment verified. The trip completes when the driver ends it.");
        
        return response;
    }
    
    /**
     * Payments waiting for one owner to confirm: their Trips' payments, or with {@code ownerId}
     * null the platform's City-ride payments (V27).
     */
    public List<PaymentResponse> getPendingPayments(Long ownerId) {
        // Online payments confirm themselves by signature; they never wait on the owner, and the
        // owner's verify refuses them anyway.
        return paymentRepository.findByStatus(Payment.PaymentStatus.PENDING)
                .stream()
                .filter(p -> p.getPaymentMethod() != Payment.PaymentMethod.RAZORPAY)
                .filter(p -> java.util.Objects.equals(p.getOwnerId(), ownerId))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    public List<PaymentResponse> getUserPayments(UUID userId) {
        return paymentRepository.findByUserId(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "dailyRevenue",   allEntries = true),
            @CacheEvict(value = "monthlyRevenue", allEntries = true),
            @CacheEvict(value = "yearlyRevenue",  allEntries = true),
            @CacheEvict(value = "monthlyRevenueSeries", allEntries = true)
    })
    public PaymentResponse generateTripEndQr(Long driverId, UUID bookingId) {
        TravelBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        if (booking.getDriverId() == null || !booking.getDriverId().equals(driverId)) {
            throw new RuntimeException("Unauthorized: Booking not assigned to you");
        }
        // A trip that never started cannot end. This used to raise a payment and tell the
        // passenger "trip ended - payment due" for bookings the driver had not even accepted.
        if (booking.getStatus() != TravelBooking.BookingStatus.STARTED) {
            throw new RuntimeException("Start the trip before ending it");
        }
        markTripEnded(booking, driverId);
        java.util.Optional<Payment> existing = paymentRepository.findByBookingId(bookingId);
        if (existing.isPresent() && existing.get().getStatus() == Payment.PaymentStatus.VERIFIED) {
            // Prepaid: the money is already in, so ending the ride is what completes it. This
            // used to throw "Payment already completed", leaving prepaid trips stuck in STARTED.
            booking.setStatus(TravelBooking.BookingStatus.COMPLETED);
            bookingRepository.save(booking);
            locationTrackingService.removeLocation(bookingId.toString());
            recordEarnings(booking, existing.get());
            PaymentResponse response = mapToResponse(existing.get());
            response.setMessage("Already paid. Trip completed.");
            return response;
        }
        Payment payment = existing.orElseGet(() -> {
            Payment p = new Payment();
            p.setBookingId(bookingId);
            p.setUserId(booking.getUserId());
            p.setAmount(amountDue(booking));
            p.setPaymentMethod(Payment.PaymentMethod.RAZORPAY);
            p.setStatus(Payment.PaymentStatus.PENDING);
            p.setDriverId(driverId);
            p.setCreatedAt(LocalDateTime.now());
            return p;
        });
        // The driver is about to show a Razorpay QR (RazorpayPaymentService.tripEndQr), so this is
        // an online payment now, whatever the passenger picked before. Any open checkout order id
        // stays: if that earlier checkout completes after all, its webhook still settles this row.
        payment.setPaymentMethod(Payment.PaymentMethod.RAZORPAY);
        payment.setStatus(Payment.PaymentStatus.PENDING);
        Payment saved = paymentRepository.save(payment);

        // This call is the moment the trip actually ends on the ground, so it is where the
        // passenger and owner get told - previously nobody was notified until payment landed,
        // which for a UPI trip could be long after the passenger had walked away.
        // notifyTripEnded is idempotent, so regenerating an expired QR does not re-notify.
        driverRepository.findById(driverId)
                .ifPresent(driver -> notificationService.notifyTripEnded(booking, driver));

        // No QR here: RazorpayPaymentService.tripEndQr adds the Payment Link outside this
        // transaction, so a slow Razorpay never holds a database connection.
        PaymentResponse response = mapToResponse(saved);
        response.setMessage("Trip ended. Payment due.");
        return response;
    }
    
    /** How close (in metres) the driver's phone must be to the drop point for the trip to end itself. */
    public static final double ARRIVAL_RADIUS_METRES = 150;

    /**
     * The driver's live position reached the drop point, so the trip ends and completes on its
     * own: the driver does not tap "End trip", "Cash received" or wait for Razorpay. A prepaid
     * fare is already settled; anything still owed is recorded as paid to the driver at the drop
     * (cash in hand), which puts the platform commission on the driver's dues ledger as usual.
     *
     * <p>Called on every location report, so it returns false quickly for anything that is not a
     * started trip of this driver at its drop point. Two reports racing here are stopped by the
     * booking's {@code @Version}: the second save fails and the caller ignores it.
     *
     * @return true when this call completed the trip
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "dailyRevenue",   allEntries = true),
            @CacheEvict(value = "monthlyRevenue", allEntries = true),
            @CacheEvict(value = "yearlyRevenue",  allEntries = true),
            @CacheEvict(value = "monthlyRevenueSeries", allEntries = true)
    })
    public boolean completeOnArrival(Long driverId, UUID bookingId, double lat, double lon) {
        TravelBooking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null || booking.getDriverId() == null || !booking.getDriverId().equals(driverId)
                || booking.getStatus() != TravelBooking.BookingStatus.STARTED
                || booking.getToLat() == null || booking.getToLon() == null) {
            return false;
        }
        double metres = TripRules.distanceKm(lat, lon, booking.getToLat(), booking.getToLon()) * 1000;
        if (metres > ARRIVAL_RADIUS_METRES) {
            return false;
        }
        markTripEnded(booking, driverId);
        driverRepository.findById(driverId)
                .ifPresent(driver -> notificationService.notifyTripEnded(booking, driver));

        java.util.Optional<Payment> existing = paymentRepository.findByBookingId(bookingId);
        if (existing.isPresent() && existing.get().getStatus() == Payment.PaymentStatus.VERIFIED) {
            booking.setStatus(TravelBooking.BookingStatus.COMPLETED);
            bookingRepository.save(booking);
            locationTrackingService.removeLocation(bookingId.toString());
            recordEarnings(booking, existing.get());
            return true;
        }
        Payment payment = existing.orElseGet(() -> {
            Payment p = new Payment();
            p.setBookingId(bookingId);
            p.setUserId(booking.getUserId());
            p.setAmount(amountDue(booking));
            p.setDriverId(driverId);
            p.setCreatedAt(LocalDateTime.now());
            return p;
        });
        payment.setPaymentMethod(Payment.PaymentMethod.CASH);
        settle(payment, booking, "auto-arrival:driver:" + driverId);
        return true;
    }

    /**
     * Keeps the UPI reference (UTR) a passenger supplies, so the owner has something to match
     * against the bank statement when verifying. It arrived on the request and was thrown away.
     * Trimmed and length-capped; only letters and digits, as UPI references are.
     */
    private static void rememberUpiReference(Payment payment, PaymentRequest request) {
        String ref = request == null ? null : request.getUpiTransactionId();
        if (ref == null) {
            return;
        }
        ref = ref.trim();
        if (!ref.isEmpty() && ref.length() <= 64 && ref.matches("[A-Za-z0-9]+")) {
            payment.setUpiTransactionId(ref);
        }
    }

    private Payment.PaymentMethod parseMethod(PaymentRequest request) {
        String raw = request != null ? request.getPaymentMethod() : null;
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Payment method is required (CASH, or pay online)");
        }
        // Only cash is recorded here. Online payment (UPI apps, cards) goes through Razorpay's own
        // order/verify endpoints; the old plain UPI QR to a fixed UPI ID is gone.
        if (!"CASH".equalsIgnoreCase(raw.trim())) {
            throw new IllegalArgumentException("Pay online (UPI, card) through Razorpay, or choose cash");
        }
        return Payment.PaymentMethod.CASH;
    }

    /** A PNG data URL of a QR code for {@code text} (the Razorpay Payment Link the driver shows). */
    public static String qrPng(String text) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, 300, 300);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Error generating QR code", e);
        }
    }
    
    private PaymentResponse mapToResponse(Payment payment) {
        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(payment.getId());
        response.setBookingId(payment.getBookingId());
        response.setAmount(payment.getAmount());
        response.setPaymentMethod(payment.getPaymentMethod().name());
        response.setStatus(payment.getStatus().name());
        response.setUpiTransactionId(payment.getUpiTransactionId());
        response.setCreatedAt(payment.getCreatedAt());
        response.setUpiLinkExpiresAt(payment.getUpiLinkExpiresAt());
        response.setPurpose(payment.getPurpose() == null ? "FARE" : payment.getPurpose().name());
        response.setUpiLinkExpired(
            payment.getUpiLinkExpiresAt() != null &&
            payment.getUpiLinkExpiresAt().isBefore(LocalDateTime.now())
        );
        return response;
    }
}
