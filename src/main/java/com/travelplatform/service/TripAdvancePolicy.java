package com.travelplatform.service;

import com.travelplatform.entity.TravelBooking;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * The rules for Trip advances, in one place: how much, how long an unpaid hold lasts, and when a
 * cancelled advance is refunded.
 *
 * <p>A Trip blocks a car for whole days, so a passenger who books and never turns up costs the
 * owner real money. The advance makes the booking real: until it is paid, the car is only held,
 * and the hold lapses after {@code hold-minutes}.
 */
@Component
public class TripAdvancePolicy {

    /** Trip dates are Indian calendar days; "24 hours before the trip" is counted from IST midnight. */
    static final ZoneId TRIP_ZONE = ZoneId.of("Asia/Kolkata");

    private final int percent;
    private final Duration hold;
    private final Duration freeCancellation;

    public TripAdvancePolicy(@Value("${app.trip.advance-percent:20}") int percent,
                             @Value("${app.trip.advance-hold-minutes:30}") int holdMinutes,
                             @Value("${app.trip.advance-refund-hours:24}") int refundHours) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("app.trip.advance-percent must be 0-100");
        }
        this.percent = percent;
        this.hold = Duration.ofMinutes(holdMinutes);
        this.freeCancellation = Duration.ofHours(refundHours);
    }

    /** Whether a booking like this needs an advance. 0 % switches advances off entirely. */
    public boolean applies(TravelBooking booking) {
        return percent > 0 && booking.getServiceMode() == TravelBooking.ServiceMode.TRIP;
    }

    /** The advance for a fare, to the whole rupee (UPI apps and receipts read better that way). */
    public BigDecimal advanceFor(BigDecimal total) {
        BigDecimal advance = total.multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.UNNECESSARY);
        // Never ask for less than a rupee, nor more than the fare itself.
        return advance.max(BigDecimal.ONE.setScale(2)).min(total);
    }

    public LocalDateTime holdUntil(LocalDateTime now) {
        return now.plus(hold);
    }

    /**
     * Whether cancelling now gets the advance back. Always, if no car was ever committed (the
     * booking never got CONFIRMED); otherwise only until 24 h before the trip's first day begins.
     */
    public boolean refundsAdvanceOnCancel(TravelBooking booking, LocalDateTime nowInTripZone) {
        if (booking.getStatus() != TravelBooking.BookingStatus.CONFIRMED) {
            return true;
        }
        LocalDateTime tripStarts = booking.getFromDate().atStartOfDay();
        return nowInTripZone.isBefore(tripStarts.minus(freeCancellation));
    }

    public static LocalDateTime nowInTripZone() {
        return LocalDateTime.now(TRIP_ZONE);
    }

    public int percent() {
        return percent;
    }
}
