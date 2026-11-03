package com.travelplatform.config;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The travels owner a request is being served for. Bound by {@link OwnerScopeFilter} for
 * ROLE_OWNER requests only; {@link OwnerScopedDataSource} switches that request's database
 * connections to the owner's row-level-security scope (V28).
 *
 * <p>Thread-bound: work handed to another thread (async email, after-commit jobs) runs unscoped,
 * like any background job.
 */
@Component
public class OwnerScope {

    /** The owner, and whether they are the platform owner (NJO-0001), who also manages City rides. */
    public record Bound(long ownerId, boolean platform) {
    }

    private static final ThreadLocal<Bound> CURRENT = new ThreadLocal<>();

    public static void bind(long ownerId, boolean platform) {
        CURRENT.set(new Bound(ownerId, platform));
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static Optional<Bound> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** The calling owner's id. Refused (403) outside an owner's request. */
    public Long ownerId() {
        return current().map(Bound::ownerId)
                .orElseThrow(() -> new AccessDeniedException("Owner login required"));
    }

    public boolean isPlatform() {
        return current().map(Bound::platform).orElse(false);
    }

    /** Platform screens (cities, city fares, independent drivers, payouts, dues, server health): NJO-0001 only. */
    public void requirePlatform() {
        if (!isPlatform()) {
            throw new AccessDeniedException("Only the NammaJourney platform account can open this");
        }
    }
}
