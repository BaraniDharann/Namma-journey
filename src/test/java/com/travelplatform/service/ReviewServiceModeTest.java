package com.travelplatform.service;

import com.travelplatform.dto.ReviewResponse;
import com.travelplatform.entity.Review;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.ReviewRepository;
import com.travelplatform.repository.TravelBookingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReviewServiceModeTest {

    @Test
    @DisplayName("public reviews say whether they were for a City ride or a Trip")
    void reviewsCarryTheRideType() {
        ReviewRepository reviews = mock(ReviewRepository.class);
        TravelBookingRepository bookings = mock(TravelBookingRepository.class);
        ReviewService service = new ReviewService(reviews, bookings, mock(DriverRepository.class));

        UUID cityId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID goneId = UUID.randomUUID();
        when(reviews.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(review(cityId), review(tripId), review(goneId)));
        when(bookings.findById(cityId)).thenReturn(Optional.of(booking(TravelBooking.ServiceMode.CITY)));
        when(bookings.findById(tripId)).thenReturn(Optional.of(booking(TravelBooking.ServiceMode.TRIP)));
        when(bookings.findById(goneId)).thenReturn(Optional.empty());

        List<ReviewResponse> out = service.getAllReviews();

        assertEquals("CITY", out.get(0).getServiceMode());
        assertEquals("TRIP", out.get(1).getServiceMode());
        assertEquals("TRIP", out.get(2).getServiceMode());
    }

    private static Review review(UUID bookingId) {
        Review r = new Review();
        r.setId(UUID.randomUUID());
        r.setBookingId(bookingId);
        r.setRating(5);
        return r;
    }

    private static TravelBooking booking(TravelBooking.ServiceMode mode) {
        TravelBooking b = new TravelBooking();
        b.setServiceMode(mode);
        b.setFromPlace("A");
        b.setToPlace("B");
        return b;
    }
}
