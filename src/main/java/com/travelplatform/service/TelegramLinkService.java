package com.travelplatform.service;

import com.travelplatform.config.TelegramProperties;
import com.travelplatform.entity.Driver;
import com.travelplatform.repository.DriverRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * Owns the lifecycle that binds a Telegram account to a driver.
 *
 * <p>Deliberately a separate bean from {@link TelegramWebhookService} rather than a private
 * method on it. Redemption must run in a transaction and must evict the driver caches, and
 * Spring applies both through a proxy — a self-invoked method on the webhook service would
 * get neither, silently, with the bug only showing up as a stale link status in the owner UI.
 */
@Service
public class TelegramLinkService {

    private static final Logger log = LoggerFactory.getLogger(TelegramLinkService.class);

    /**
     * Onboarding is a face-to-face step — the owner shows the driver a QR code — so the token
     * only has to outlive that conversation. A day is generous for that and short enough that
     * a screenshot left in a phone gallery stops being useful quickly.
     */
    private static final Duration LINK_TOKEN_TTL = Duration.ofHours(24);

    /** 32 bytes of entropy: not brute-forceable, and 43 base64url chars fits Telegram's 64-char start payload. */
    private static final int LINK_TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final DriverRepository driverRepository;
    private final TelegramProperties properties;

    public TelegramLinkService(DriverRepository driverRepository, TelegramProperties properties) {
        this.driverRepository = driverRepository;
        this.properties = properties;
    }

    /**
     * Mints a fresh onboarding deep link for a driver and returns the {@code t.me} URL.
     *
     * <p>Minting overwrites any previous unredeemed token for that driver, so re-issuing a
     * link after one is mislaid revokes the old QR code rather than leaving two valid ways in.
     */
    @Transactional
    public String createLinkUrl(Long driverId) {
        if (properties.getBotUsername().isBlank()) {
            throw new IllegalStateException(
                    "app.telegram.bot-username is not configured; cannot build a driver link");
        }
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Driver not found: " + driverId));

        byte[] raw = new byte[LINK_TOKEN_BYTES];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        driver.setTelegramLinkToken(token);
        driver.setTelegramLinkTokenExpiresAt(LocalDateTime.now().plus(LINK_TOKEN_TTL));
        driverRepository.save(driver);

        log.info("Issued Telegram link token for driver {}", driverId);
        return "https://t.me/" + properties.getBotUsername() + "?start=" + token;
    }

    /**
     * Redeems an onboarding token, binding {@code telegramUserId} to the driver it was minted for.
     *
     * <p>Evicts the driver caches because the owner's drivers list reports link status: without
     * this, a driver who has just linked still shows as unreachable for the cache TTL — exactly
     * when the owner is standing next to them checking that it worked.
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "allDrivers", allEntries = true),
            @CacheEvict(value = "driverById", allEntries = true)
    })
    public RedeemResult redeem(String telegramUserId, String token) {
        if (token == null || token.isBlank()) {
            return new RedeemResult(Status.NO_TOKEN, null);
        }

        Optional<Driver> match = driverRepository.findByTelegramLinkToken(token);
        if (match.isEmpty()) {
            // Covers both a wrong token and a correct one already redeemed, since redemption
            // clears it. One outcome for both on purpose: distinguishing them would confirm to
            // a guesser when they had hit a real token.
            return new RedeemResult(Status.INVALID, null);
        }

        Driver driver = match.get();
        LocalDateTime expiry = driver.getTelegramLinkTokenExpiresAt();
        if (expiry == null || expiry.isBefore(LocalDateTime.now())) {
            return new RedeemResult(Status.EXPIRED, null);
        }

        // telegram_chat_id is uniquely indexed, so a collision would otherwise surface as a
        // constraint violation at flush. Checking first turns that into a clear refusal and
        // stops one phone quietly taking over a second driver's dispatch channel.
        Optional<Driver> existing = driverRepository.findByTelegramChatId(telegramUserId);
        if (existing.isPresent() && !existing.get().getId().equals(driver.getId())) {
            log.warn("Telegram account linked to driver {} tried to redeem a token for driver {}",
                    existing.get().getId(), driver.getId());
            return new RedeemResult(Status.ALREADY_LINKED_TO_OTHER, null);
        }

        driver.setTelegramChatId(telegramUserId);
        driver.setTelegramLinkedAt(LocalDateTime.now());
        // Single use: clearing the token means a leaked QR code cannot be redeemed again.
        driver.setTelegramLinkToken(null);
        driver.setTelegramLinkTokenExpiresAt(null);
        driverRepository.save(driver);

        log.info("Driver {} linked Telegram successfully", driver.getId());
        return new RedeemResult(Status.SUCCESS, driver.getName());
    }

    public enum Status {
        /** Bot opened without a payload — someone found it by name rather than by link. */
        NO_TOKEN,
        /** Unknown token, or one that has already been used. */
        INVALID,
        EXPIRED,
        /** This Telegram account is already bound to a different driver. */
        ALREADY_LINKED_TO_OTHER,
        SUCCESS
    }

    /** @param driverName populated only on {@link Status#SUCCESS} */
    public record RedeemResult(Status status, String driverName) {
    }
}
