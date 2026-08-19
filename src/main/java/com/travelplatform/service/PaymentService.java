package com.travelplatform.service;

import com.travelplatform.dto.CashPaymentRequest;
import com.travelplatform.dto.PaymentRequest;
import com.travelplatform.dto.PaymentResponse;
import com.travelplatform.entity.Payment;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.repository.PaymentRepository;
import com.travelplatform.repository.TravelBookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
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
    
    @Value("${payment.upi.id}")
    private String ownerUpiId;
    
    @Value("${payment.upi.name:Namma Journey}")
    private String ownerName;
    
    private static final int UPI_LINK_EXPIRY_MINUTES = 5;

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

        // Check existing payment
        java.util.Optional<Payment> existing = paymentRepository.findByBookingId(bookingId);
        if (existing.isPresent()) {
            Payment p = existing.get();
            if (p.getStatus() == Payment.PaymentStatus.VERIFIED) {
                throw new RuntimeException("Payment already completed");
            }
            // If UPI link is still active (not expired), return it
            if (p.getPaymentMethod() == Payment.PaymentMethod.UPI
                    && p.getUpiLinkExpiresAt() != null
                    && p.getUpiLinkExpiresAt().isAfter(LocalDateTime.now())) {
                PaymentResponse response = mapToResponse(p);
                String deepLink = generateUpiDeepLink(p.getAmount(), bookingId.toString());
                response.setUpiDeepLink(deepLink);
                response.setUpiQrCode(generateQrCodeBase64(deepLink));
                response.setMessage("Scan QR code or tap to open UPI app.");
                return response;
            }
            // Link expired or failed — refresh expiry and return new link
            p.setUpiLinkExpiresAt(LocalDateTime.now().plusMinutes(UPI_LINK_EXPIRY_MINUTES));
            p.setStatus(Payment.PaymentStatus.PENDING);
            Payment refreshed = paymentRepository.save(p);
            String deepLink = generateUpiDeepLink(refreshed.getAmount(), bookingId.toString());
            PaymentResponse response = mapToResponse(refreshed);
            response.setUpiDeepLink(deepLink);
            response.setUpiQrCode(generateQrCodeBase64(deepLink));
            response.setMessage("New QR generated. Valid for " + UPI_LINK_EXPIRY_MINUTES + " minutes.");
            return response;
        }

        // New payment
        Payment payment = new Payment();
        payment.setBookingId(bookingId);
        payment.setUserId(userId);
        payment.setAmount(booking.getTotalAmount());
        payment.setPaymentMethod(Payment.PaymentMethod.valueOf(request.getPaymentMethod()));
        payment.setStatus(Payment.PaymentStatus.PENDING);
        payment.setDriverId(booking.getDriverId());
        payment.setCreatedAt(LocalDateTime.now());

        if (Payment.PaymentMethod.valueOf(request.getPaymentMethod()) == Payment.PaymentMethod.UPI) {
            payment.setUpiLinkExpiresAt(LocalDateTime.now().plusMinutes(UPI_LINK_EXPIRY_MINUTES));
        }

        Payment saved = paymentRepository.save(payment);

        PaymentResponse response = mapToResponse(saved);
        if (saved.getPaymentMethod() == Payment.PaymentMethod.UPI) {
            String deepLink = generateUpiDeepLink(saved.getAmount(), bookingId.toString());
            response.setUpiDeepLink(deepLink);
            response.setUpiQrCode(generateQrCodeBase64(deepLink));
            response.setMessage("Scan QR code or tap to open UPI app. Valid for " + UPI_LINK_EXPIRY_MINUTES + " minutes.");
        } else {
            response.setMessage("Pay cash to driver. Driver will mark payment received.");
        }

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
        
        Payment payment = paymentRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new RuntimeException("Payment not initiated"));
        
        if (payment.getPaymentMethod() != Payment.PaymentMethod.CASH) {
            throw new RuntimeException("This is not a cash payment");
        }
        
        payment.setPaymentDate(LocalDateTime.now());
        payment.setStatus(Payment.PaymentStatus.VERIFIED);
        payment.setVerifiedDate(LocalDateTime.now());
        Payment updated = paymentRepository.save(payment);
        
        booking.setStatus(TravelBooking.BookingStatus.COMPLETED);
        bookingRepository.save(booking);
        locationTrackingService.removeLocation(bookingId.toString());

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
    
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "dailyRevenue",   allEntries = true),
            @CacheEvict(value = "monthlyRevenue", allEntries = true),
            @CacheEvict(value = "yearlyRevenue",  allEntries = true),
            @CacheEvict(value = "monthlyRevenueSeries", allEntries = true)
    })
    public PaymentResponse verifyPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));
        
        payment.setStatus(Payment.PaymentStatus.VERIFIED);
        payment.setVerifiedDate(LocalDateTime.now());
        Payment verified = paymentRepository.save(payment);
        
        TravelBooking booking = bookingRepository.findById(payment.getBookingId())
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        booking.setStatus(TravelBooking.BookingStatus.COMPLETED);
        bookingRepository.save(booking);
        locationTrackingService.removeLocation(payment.getBookingId().toString());

        notificationService.notifyPaymentReceived(booking);

        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(verified.getId());
        response.setBookingId(verified.getBookingId());
        response.setAmount(verified.getAmount());
        response.setPaymentMethod(verified.getPaymentMethod().name());
        response.setStatus(verified.getStatus().name());
        response.setMessage("Payment verified. Trip completed. RCM calculation will be processed.");
        
        return response;
    }
    
    public List<PaymentResponse> getPendingPayments() {
        return paymentRepository.findByStatus(Payment.PaymentStatus.PENDING)
                .stream()
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
    public PaymentResponse generateTripEndQr(Long driverId, UUID bookingId) {
        TravelBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        if (booking.getDriverId() == null || !booking.getDriverId().equals(driverId)) {
            throw new RuntimeException("Unauthorized: Booking not assigned to you");
        }
        java.util.Optional<Payment> existing = paymentRepository.findByBookingId(bookingId);
        if (existing.isPresent() && existing.get().getStatus() == Payment.PaymentStatus.VERIFIED) {
            throw new RuntimeException("Payment already completed");
        }
        Payment payment = existing.orElseGet(() -> {
            Payment p = new Payment();
            p.setBookingId(bookingId);
            p.setUserId(booking.getUserId());
            p.setAmount(booking.getTotalAmount());
            p.setPaymentMethod(Payment.PaymentMethod.UPI);
            p.setStatus(Payment.PaymentStatus.PENDING);
            p.setDriverId(driverId);
            p.setCreatedAt(LocalDateTime.now());
            return p;
        });
        payment.setUpiLinkExpiresAt(LocalDateTime.now().plusMinutes(UPI_LINK_EXPIRY_MINUTES));
        payment.setStatus(Payment.PaymentStatus.PENDING);
        Payment saved = paymentRepository.save(payment);

        // This call is the moment the trip actually ends on the ground, so it is where the
        // passenger and owner get told - previously nobody was notified until payment landed,
        // which for a UPI trip could be long after the passenger had walked away.
        // notifyTripEnded is idempotent, so regenerating an expired QR does not re-notify.
        driverRepository.findById(driverId)
                .ifPresent(driver -> notificationService.notifyTripEnded(booking, driver));

        String deepLink = generateUpiDeepLink(saved.getAmount(), bookingId.toString());
        PaymentResponse response = mapToResponse(saved);
        response.setUpiDeepLink(deepLink);
        response.setUpiQrCode(generateQrCodeBase64(deepLink));
        response.setMessage("QR generated. Valid for " + UPI_LINK_EXPIRY_MINUTES + " minutes.");
        return response;
    }
    
    private String generateUpiDeepLink(Double amount, String bookingRef) {
        try {
            String encodedName = URLEncoder.encode(ownerName, "UTF-8");
            String note = URLEncoder.encode("Booking:" + bookingRef, "UTF-8");
            return String.format("upi://pay?pa=%s&pn=%s&am=%.2f&cu=INR&tn=%s",
                    ownerUpiId, encodedName, amount, note);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Error generating UPI link", e);
        }
    }

    private String generateQrCodeBase64(String upiDeepLink) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(upiDeepLink, BarcodeFormat.QR_CODE, 300, 300);
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
        response.setUpiLinkExpired(
            payment.getUpiLinkExpiresAt() != null &&
            payment.getUpiLinkExpiresAt().isBefore(LocalDateTime.now())
        );
        return response;
    }
}
