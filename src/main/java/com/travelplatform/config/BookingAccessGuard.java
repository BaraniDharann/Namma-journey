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

        // Owners can monitor any trip; skip the booking lookup for them.
        if (hasRole(auth, "ROLE_OWNER")) return true;

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
        return false;
    }

    private boolean hasRole(Authentication auth, String role) {
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (role.equals(a.getAuthority())) return true;
        }
        return false;
    }
}
