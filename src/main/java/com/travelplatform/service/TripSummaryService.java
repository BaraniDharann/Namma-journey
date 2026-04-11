package com.travelplatform.service;

import com.travelplatform.dto.DriverDetailsResponse;
import com.travelplatform.dto.TripSummaryResponse;
import com.travelplatform.entity.Payment;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.PaymentRepository;
import com.travelplatform.repository.ReviewRepository;
import com.travelplatform.repository.TravelBookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TripSummaryService {

    private final TravelBookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final ReviewRepository reviewRepository;
    private final DriverRepository driverRepository;

    public TripSummaryResponse getTripSummary(UUID userId, UUID bookingId) {
        TravelBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (!booking.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized access");
        }

        TripSummaryResponse summary = new TripSummaryResponse();
        summary.setBookingId(booking.getId());
        summary.setUserName(booking.getUserName());
        summary.setUserPhone(booking.getUserPhone());
        summary.setFromPlace(booking.getFromPlace());
        summary.setToPlace(booking.getToPlace());
        summary.setFromDate(booking.getFromDate());
        summary.setToDate(booking.getToDate());
        summary.setTravelDays(booking.getTravelDays());
        summary.setTravelMembers(booking.getTravelMembers());
        summary.setAcType(booking.getAcType());
        summary.setDistanceKm(booking.getDistanceKm());
        summary.setTotalAmount(booking.getTotalAmount());
        summary.setStatus(booking.getStatus().name());
        summary.setBookingDate(booking.getBookingDate());
        summary.setEstimatedTimeFormatted(formatTime(booking.getEstimatedTimeMinutes()));

        if (booking.getDriverId() != null) {
            driverRepository.findById(booking.getDriverId()).ifPresent(driver -> {
                DriverDetailsResponse d = new DriverDetailsResponse();
                d.setDriverId(driver.getId());
                d.setName(driver.getName());
                d.setMobile(driver.getMobile());
                d.setPhoto(driver.getPhoto());
                d.setLicenseNumber(driver.getLicenseNumber());
                summary.setDriver(d);
            });
        }

        paymentRepository.findByBookingId(bookingId).ifPresent(payment -> {
            summary.setPaymentMethod(payment.getPaymentMethod().name());
            summary.setPaymentStatus(payment.getStatus().name());
            summary.setPaymentVerifiedAt(payment.getVerifiedDate());
        });

        summary.setReviewSubmitted(reviewRepository.findByBookingId(bookingId).isPresent());

        return summary;
    }

    private String formatTime(Long minutes) {
        long hours = minutes / 60;
        long mins = minutes % 60;
        return String.format("%d hours %d minutes", hours, mins);
    }
}
