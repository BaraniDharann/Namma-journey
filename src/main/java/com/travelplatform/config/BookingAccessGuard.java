package com.travelplatform.config;

import com.travelplatform.entity.TravelBooking;
import com.travelplatform.repository.TravelBookingRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Central check: may this authenticated principal see a given booking's live
 * location? Shared by the WebSocket SUBSCRIBE interceptor and the REST location
 * endpoint so both surfaces enforce the same rule and can't drift apart.
 *
 * Rule:
 *  - OWNER  -> may view any booking (operations/monitoring)
 *  - USER   -> only if they are the booking's customer   (booking.userId)
 *  - DRIVER -> only if they are the assigned driver       (booking.driverId)
 */
@Component
public class BookingAccessGuard {

    private final TravelBookingRepository bookingRepository;

    public BookingAccessGuard(TravelBookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    /** Overload usable directly from @PreAuthorize SpEL: @bookingAccessGuard.canAccess(authentication, #bookingId). */
    public boolean canAccess(Authentication auth, String bookingId) {
        if (auth == null || !auth.isAuthenticated()) return false;
        return canAccess(auth.getName(), auth, bookingId);
    }

    public boolean canAccess(String principalId, Authentication auth, String bookingId) {
        if (principalId == null || bookingId == null || auth == null) return false;

        UUID bookingUuid;
        try {
            bookingUuid = UUID.fromString(bookingId);
        } catch (IllegalArgumentException ex) {
            return false; // malformed id -> deny
        }

        TravelBooking booking = bookingRepository.findById(bookingUuid).orElse(null);
        if (booking == null) return false;

        if (hasRole(auth, "ROLE_USER")) {
            return booking.getUserId() != null && booking.getUserId().toString().equals(principalId);
        }
        if (hasRole(auth, "ROLE_DRIVER")) {
            return booking.getDriverId() != null && booking.getDriverId().toString().equals(principalId);
        }
        // A travels owner watches their own Trips; the platform owner also watches City rides (V27).
        // Checked by id, not by the request's OwnerScope: a WebSocket subscribe has no HTTP request.
        if (hasRole(auth, "ROLE_OWNER")) {
            if (booking.getOwnerId() != null) {
                return booking.getOwnerId().toString().equals(principalId);
            }
            return tripRates != null && tripRates.platformOwnerId().toString().equals(principalId);
        }
        return false;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.travelplatform.service.TripRateService tripRates;

    /**
     * May this driver publish positions for this booking? Only the assigned driver, and only while
     * the trip is live (CONFIRMED on the way to pickup, or STARTED). Checking just the driverId in
     * the message body let any driver paint positions onto any passenger's map.
     */
    public boolean canReportLocation(Long driverId, String bookingId) {
        if (driverId == null || bookingId == null) return false;
        UUID bookingUuid;
        try {
            bookingUuid = UUID.fromString(bookingId);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return bookingRepository.findById(bookingUuid)
                .filter(b -> driverId.equals(b.getDriverId()))
                .filter(b -> b.getStatus() == TravelBooking.BookingStatus.CONFIRMED
                        || b.getStatus() == TravelBooking.BookingStatus.STARTED)
                // Ended but unpaid is still STARTED. A sender left running on it would keep
                // overwriting where Ride Now thinks the driver is, now that they may be on the next ride.
                .filter(b -> b.getTripEndedAt() == null)
                .isPresent();
    }

    private boolean hasRole(Authentication auth, String role) {
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (role.equals(a.getAuthority())) return true;
        }
        return false;
    }
}
