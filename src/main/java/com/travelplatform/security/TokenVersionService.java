package com.travelplatform.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.Owner;
import com.travelplatform.entity.User;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.OwnerRepository;
import com.travelplatform.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Decides whether a signed JWT is still current, which is what makes tokens revocable.
 *
 * <p>Each account has a token_version (V10); every token carries the version it was issued at.
 * Resetting a password or logging out bumps the account's version, so all earlier tokens stop
 * working - including one an attacker copied before the victim reset their password. A deleted
 * account has no version, so its tokens die with it.
 *
 * <p>Versions are cached per account for {@link #CACHE_TTL} so this is not a database query on
 * every request. The instance that bumps a version drops its own cache entry immediately; other
 * instances notice within the TTL.
 */
@Service
public class TokenVersionService {

    static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final UserRepository users;
    private final DriverRepository drivers;
    private final OwnerRepository owners;

    private final Cache<String, Long> versions = Caffeine.newBuilder()
            .expireAfterWrite(CACHE_TTL)
            .maximumSize(100_000)
            .build();

    public TokenVersionService(UserRepository users, DriverRepository drivers, OwnerRepository owners) {
        this.users = users;
        this.drivers = drivers;
        this.owners = owners;
    }

    /** True if the account still exists and the token was issued at its current version. */
    public boolean isCurrent(String role, String principal, long tokenVersion) {
        if (role == null || principal == null) {
            return false;
        }
        Long current = versions.get(role + ":" + principal, key -> lookup(role, principal));
        return current != null && current == tokenVersion;
    }

    public void revokeUser(User user) {
        user.setTokenVersion(user.getTokenVersion() + 1);
        users.save(user);
        versions.invalidate("ROLE_USER:" + user.getId());
    }

    public void revokeDriver(Driver driver) {
        driver.setTokenVersion(driver.getTokenVersion() + 1);
        drivers.save(driver);
        versions.invalidate("ROLE_DRIVER:" + driver.getId());
    }

    public void revokeOwner(Owner owner) {
        owner.setTokenVersion(owner.getTokenVersion() + 1);
        owners.save(owner);
        versions.invalidate("ROLE_OWNER:" + owner.getId());
    }

    private Long lookup(String role, String principal) {
        try {
            return switch (role) {
                case "ROLE_USER" -> users.findById(UUID.fromString(principal)).map(User::getTokenVersion).orElse(null);
                case "ROLE_DRIVER" -> drivers.findById(Long.valueOf(principal)).map(Driver::getTokenVersion).orElse(null);
                case "ROLE_OWNER" -> owners.findById(Long.valueOf(principal)).map(Owner::getTokenVersion).orElse(null);
                default -> null;
            };
        } catch (IllegalArgumentException malformedId) {
            return null;
        }
    }
}
