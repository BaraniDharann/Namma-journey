package com.travelplatform.service;

import com.travelplatform.config.TelegramProperties;
import com.travelplatform.entity.EmergencyContact;
import com.travelplatform.entity.User;
import com.travelplatform.repository.EmergencyContactRepository;
import com.travelplatform.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Binds Telegram accounts to passengers and to their emergency contacts, so the same bot that
 * dispatches drivers can carry ride updates and safety alerts to them.
 *
 * <p>Follows {@link TelegramLinkService} exactly: a random, single-use, expiring token in a
 * {@code t.me/<bot>?start=<token>} link, redeemed when the person taps Start. Tokens from the
 * three tables never collide in practice (256 random bits each), so the webhook simply tries
 * driver, then passenger, then contact.
 */
@Service
public class RiderTelegramLinkService {

    private static final Logger log = LoggerFactory.getLogger(RiderTelegramLinkService.class);

    /** The passenger links from their own phone, on the spot; an hour is plenty. */
    private static final Duration USER_TOKEN_TTL = Duration.ofHours(1);

    /** A contact gets the invite forwarded and may open it hours later. */
    private static final Duration CONTACT_TOKEN_TTL = Duration.ofDays(7);

    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final EmergencyContactRepository contactRepository;
    private final TelegramProperties properties;

    public RiderTelegramLinkService(UserRepository userRepository,
                                    EmergencyContactRepository contactRepository,
                                    TelegramProperties properties) {
        this.userRepository = userRepository;
        this.contactRepository = contactRepository;
        this.properties = properties;
    }

    /** True when the server can actually hand out Telegram links. */
    public boolean available() {
        return properties.isOperational() && !properties.getBotUsername().isBlank();
    }

    @Transactional
    public String createUserLinkUrl(UUID userId) {
        requireAvailable();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        String token = newToken();
        user.setTelegramLinkToken(token);
        user.setTelegramLinkTokenExpiresAt(LocalDateTime.now().plus(USER_TOKEN_TTL));
        userRepository.save(user);
        return deepLink(token);
    }

    /** A fresh invite for one contact; any older invite for them stops working. */
    @Transactional
    public String createContactInviteUrl(EmergencyContact contact) {
        requireAvailable();
        String token = newToken();
        contact.setInviteToken(token);
        contact.setInviteTokenExpiresAt(LocalDateTime.now().plus(CONTACT_TOKEN_TTL));
        contactRepository.save(contact);
        return deepLink(token);
    }

    @Transactional(readOnly = true)
    public boolean isUserLinked(UUID userId) {
        return userRepository.findById(userId)
                .map(u -> u.getTelegramChatId() != null && !u.getTelegramChatId().isBlank())
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isChatLinked(String telegramUserId) {
        return userRepository.findByTelegramChatId(telegramUserId).isPresent();
    }

    /**
     * Redeems a passenger token. Empty when the token is not a passenger token at all, so the
     * caller can go on to try it as a contact invite.
     */
    @Transactional
    public Optional<Redeemed> redeemUser(String telegramUserId, String token) {
        Optional<User> match = userRepository.findByTelegramLinkToken(token);
        if (match.isEmpty()) {
            return Optional.empty();
        }
        User user = match.get();
        if (user.getTelegramLinkTokenExpiresAt() == null
                || user.getTelegramLinkTokenExpiresAt().isBefore(LocalDateTime.now())) {
            return Optional.of(new Redeemed(Outcome.EXPIRED, null, null));
        }
        // Unique index on telegram_chat_id: one Telegram account, one passenger. Linking a second
        // passenger from the same phone would hand the first one's safety alerts to the second.
        Optional<User> existing = userRepository.findByTelegramChatId(telegramUserId);
        if (existing.isPresent() && !existing.get().getId().equals(user.getId())) {
            return Optional.of(new Redeemed(Outcome.ALREADY_LINKED_TO_OTHER, null, null));
        }
        user.setTelegramChatId(telegramUserId);
        user.setTelegramLinkedAt(LocalDateTime.now());
        user.setTelegramLinkToken(null);
        user.setTelegramLinkTokenExpiresAt(null);
        userRepository.save(user);
        log.info("Passenger {} linked Telegram", user.getId());
        return Optional.of(new Redeemed(Outcome.USER_LINKED, user.getName(), null));
    }

    /** Redeems a contact invite. Empty when the token is not one. */
    @Transactional
    public Optional<Redeemed> redeemContact(String telegramUserId, String token) {
        Optional<EmergencyContact> match = contactRepository.findByInviteToken(token);
        if (match.isEmpty()) {
            return Optional.empty();
        }
        EmergencyContact contact = match.get();
        if (contact.getInviteTokenExpiresAt() == null
                || contact.getInviteTokenExpiresAt().isBefore(LocalDateTime.now())) {
            return Optional.of(new Redeemed(Outcome.EXPIRED, null, null));
        }
        // No uniqueness here on purpose: one person can be the emergency contact of several
        // passengers (a parent for two children), and each binding is its own row.
        contact.setTelegramChatId(telegramUserId);
        contact.setTelegramLinkedAt(LocalDateTime.now());
        contact.setInviteToken(null);
        contact.setInviteTokenExpiresAt(null);
        contactRepository.save(contact);
        String passengerName = userRepository.findById(contact.getUserId()).map(User::getName).orElse("A passenger");
        log.info("Emergency contact {} of passenger {} linked Telegram", contact.getId(), contact.getUserId());
        return Optional.of(new Redeemed(Outcome.CONTACT_LINKED, contact.getName(), passengerName));
    }

    /** The passenger (or a contact) blocked the bot: stop sending there. */
    @Transactional
    public void unlinkChat(String chatId) {
        if (chatId == null || chatId.isBlank()) {
            return;
        }
        userRepository.findByTelegramChatId(chatId).ifPresent(user -> {
            user.setTelegramChatId(null);
            user.setTelegramLinkedAt(null);
            userRepository.save(user);
            log.info("Passenger {} blocked the Telegram bot; unlinked", user.getId());
        });
        for (EmergencyContact contact : contactRepository.findByTelegramChatId(chatId)) {
            contact.setTelegramChatId(null);
            contact.setTelegramLinkedAt(null);
            contactRepository.save(contact);
        }
    }

    /** A send was refused with 403; see {@link TelegramLinkService#onChatBlocked}. */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onChatBlocked(TelegramChatBlockedEvent event) {
        unlinkChat(event.chatId());
    }

    private void requireAvailable() {
        if (!available()) {
            throw new IllegalStateException("Telegram is not configured on this server");
        }
    }

    private String deepLink(String token) {
        return "https://t.me/" + properties.getBotUsername() + "?start=" + token;
    }

    private static String newToken() {
        byte[] raw = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    public enum Outcome {
        USER_LINKED,
        CONTACT_LINKED,
        EXPIRED,
        ALREADY_LINKED_TO_OTHER
    }

    /**
     * @param name          the passenger's name (USER_LINKED) or the contact's (CONTACT_LINKED)
     * @param passengerName for CONTACT_LINKED, whose contact they now are
     */
    public record Redeemed(Outcome outcome, String name, String passengerName) {
    }
}
