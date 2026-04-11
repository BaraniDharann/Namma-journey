package com.travelplatform.service;

import com.travelplatform.dto.ReviewRequest;
import com.travelplatform.dto.ReviewResponse;
import com.travelplatform.entity.Review;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.entity.Driver;
import com.travelplatform.repository.ReviewRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.repository.DriverRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {
    
    private final ReviewRepository reviewRepository;
    private final TravelBookingRepository bookingRepository;
    private final DriverRepository driverRepository;
    
    public ReviewResponse submitReview(UUID userId, UUID bookingId, ReviewRequest request) {
        TravelBooking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));
        
        if (!booking.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized to review this booking");
        }
        
        if (booking.getStatus() != TravelBooking.BookingStatus.COMPLETED) {
            throw new RuntimeException("Can only review completed bookings");
        }
        
        if (reviewRepository.findByBookingId(booking.getId()).isPresent()) {
            throw new RuntimeException("Review already submitted for this booking");
        }
        
        Driver driver = driverRepository.findById(booking.getDriverId())
            .orElseThrow(() -> new RuntimeException("Driver not found"));
        
        Review review = new Review();
        review.setBookingId(booking.getId());
        review.setUserId(userId);
        review.setUserName(booking.getUserName());
        review.setDriverId(booking.getDriverId());
        review.setDriverName(driver.getName());
        review.setRating(request.getRating());
        review.setFeedback(request.getFeedback());
        
        Review saved = reviewRepository.save(review);
        ReviewResponse response = mapToResponse(saved);
        response.setFromPlace(booking.getFromPlace());
        response.setToPlace(booking.getToPlace());
        return response;
    }
    
    public List<ReviewResponse> getAllReviews() {
        return reviewRepository.findAllByOrderByCreatedAtDesc()
            .stream()
            .map(this::mapToResponseWithBooking)
            .collect(Collectors.toList());
    }

    private ReviewResponse mapToResponse(Review review) {
        return new ReviewResponse(
            review.getId(),
            review.getBookingId(),
            review.getUserName(),
            review.getDriverName(),
            review.getRating(),
            review.getFeedback(),
            null,
            null,
            review.getCreatedAt()
        );
    }

    private ReviewResponse mapToResponseWithBooking(Review review) {
        String fromPlace = null;
        String toPlace = null;
        try {
            TravelBooking booking = bookingRepository.findById(review.getBookingId()).orElse(null);
            if (booking != null) {
                fromPlace = booking.getFromPlace();
                toPlace = booking.getToPlace();
            }
        } catch (Exception ignored) {}
        return new ReviewResponse(
            review.getId(),
            review.getBookingId(),
            review.getUserName(),
            review.getDriverName(),
            review.getRating(),
            review.getFeedback(),
            fromPlace,
            toPlace,
            review.getCreatedAt()
        );
    }
}
