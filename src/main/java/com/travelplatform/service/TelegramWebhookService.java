package com.travelplatform.service;

import com.travelplatform.entity.Driver;
import com.travelplatform.repository.DriverRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.travelplatform.service.TelegramClient.escapeHtml;

/**
 * Inbound half of the Telegram driver-dispatch channel: what arrives on the webhook.
 *
 * <p>Handles exactly two things, and ignores everything else Telegram may deliver:
 *
 * <ul>
 *   <li>{@code /start <token>} - a driver redeeming an onboarding link, which binds their
 *       Telegram account to a driver row.
 *   <li>A press of the Accept / Not available button on a dispatch card.
 * </ul>
 *
 * <p>Authentication of the <em>caller</em> (proving the request really came from Telegram)
 * happens in the controller via the shared webhook secret. Authorisation of the <em>actor</em>
 * happens here: the Telegram user id is resolved to a driver, and the existing
 * {@link DriverService} accept/reject methods then enforce that the booking is actually
 * theirs. Nothing here trusts an id supplied in the update payload.
 */
@Service
public class TelegramWebhookService {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookService.class);

    private static final String ACCEPT_PREFIX = "accept:";
    private static final String REJECT_PREFIX = "reject:";

    private final DriverRepository driverRepository;
    private final DriverService driverService;
    private final TelegramLinkService linkService;
    private final TelegramClient telegramClient;

    public TelegramWebhookService(DriverRepository driverRepository,
                                  DriverService driverService,
                                  TelegramLinkService linkService,
                                  TelegramClient telegramClient) {
        this.driverRepository = driverRepository;
        this.driverService = driverService;
        this.linkService = linkService;
        this.telegramClient = telegramClient;
    }

    /**
     * Entry point for one Telegram update.
     *
     * <p>Never throws. Telegram retries a webhook that does not return 2xx, and it will keep
     * retrying the same malformed update indefinitely, so an unparseable payload is logged
     * and swallowed rather than allowed to become a retry loop.
     */
    public void handleUpdate(Map<String, Object> update) {
        try {
            Map<String, Object> callbackQuery = asMap(update.get("callback_query"));
            if (callbackQuery != null) {
                handleCallbackQuery(callbackQuery);
                return;
            }
            Map<String, Object> message = asMap(update.get("message"));
            if (message != null) {
                handleMessage(message);
            }
        } catch (Exception e) {
            log.error("Failed to handle Telegram update", e);
        }
    }

    private void handleMessage(Map<String, Object> message) {
        String text = message.get("text") instanceof String s ? s.trim() : null;
        Map<String, Object> from = asMap(message.get("from"));
        String telegramUserId = from == null ? null : asId(from.get("id"));
        if (text == null || telegramUserId == null) {
            return;
        }

        if (text.startsWith("/start")) {
            String[] parts = text.split("\\s+", 2);
            String token = parts.length > 1 ? parts[1].trim() : "";
            handleStart(telegramUserId, token);
        } else if (text.startsWith("/status")) {
            String reply = findLinkedDriver(telegramUserId)
                    .map(d -> "✅ Linked as <b>" + escapeHtml(d.getName()) + "</b>. "
                            + "You will get new trips here.")
                    .orElse("❌ This Telegram account is not linked to a driver yet. "
                            + "Ask the owner for your link.");
            telegramClient.sendMessage(telegramUserId, reply, List.of());
        }
    }

    /** Redeems an onboarding token and reports the outcome back into the chat. */
    private void handleStart(String telegramUserId, String token) {
        TelegramLinkService.RedeemResult result = linkService.redeem(telegramUserId, token);
        telegramClient.sendMessage(telegramUserId, startReply(result), List.of());
    }

    private String startReply(TelegramLinkService.RedeemResult result) {
        return switch (result.status()) {
            case NO_TOKEN -> "👋 Welcome to Namma Journey.\n\n"
                    + "To receive trips here, open the personal link the owner gave you. "
                    + "This bot cannot link your account without it.";
            case INVALID -> "❌ This link is not valid or has already been used. "
                    + "Please ask the owner to send you a new one.";
            case EXPIRED -> "❌ This link has expired. "
                    + "Please ask the owner to send you a new one.";
            case ALREADY_LINKED_TO_OTHER -> "❌ This Telegram account is already linked to "
                    + "another driver. Please contact the owner.";
            case SUCCESS -> "✅ <b>Linked successfully, " + escapeHtml(result.driverName()) + "!</b>\n\n"
                    + "New trips will now arrive here. You can accept them with one tap — "
                    + "no need to open the website while you are driving.\n\n"
                    + "Send /status any time to check this connection.";
        };
    }

    private void handleCallbackQuery(Map<String, Object> callbackQuery) {
        String callbackId = callbackQuery.get("id") instanceof String s ? s : null;
        String data = callbackQuery.get("data") instanceof String s ? s : "";
        Map<String, Object> from = asMap(callbackQuery.get("from"));
        String telegramUserId = from == null ? null : asId(from.get("id"));

        Map<String, Object> message = asMap(callbackQuery.get("message"));
        Long messageId = message == null ? null : asLong(message.get("message_id"));

        if (telegramUserId == null) {
            telegramClient.answerCallbackQuery(callbackId, "Could not identify you.");
            return;
        }

        // Identity comes from the Telegram user id on the update, never from the callback
        // payload, so a forged payload cannot act as a different driver.
        Optional<Driver> maybeDriver = findLinkedDriver(telegramUserId);
        if (maybeDriver.isEmpty()) {
            telegramClient.answerCallbackQuery(callbackId, "Your account is no longer linked.");
            return;
        }
        Driver driver = maybeDriver.get();

        boolean accepting = data.startsWith(ACCEPT_PREFIX);
        boolean rejecting = data.startsWith(REJECT_PREFIX);
        if (!accepting && !rejecting) {
            telegramClient.answerCallbackQuery(callbackId, null);
            return;
        }

        String bookingId = data.substring(accepting ? ACCEPT_PREFIX.length() : REJECT_PREFIX.length());
        String chatId = driver.getTelegramChatId();

        try {
            if (accepting) {
                driverService.acceptBooking(driver.getId(), bookingId);
                telegramClient.answerCallbackQuery(callbackId, "Trip accepted");
                telegramClient.editMessageText(chatId, messageId,
                        "✅ <b>Trip accepted.</b>\n\nThe passenger has been told you are coming. "
                                + "Open the app when you are parked to start the trip.");
            } else {
                driverService.rejectBooking(driver.getId(), bookingId);
                telegramClient.answerCallbackQuery(callbackId, "Marked as not available");
                telegramClient.editMessageText(chatId, messageId,
                        "❌ <b>Marked as not available.</b>\n\nThe owner will assign another driver.");
            }
        } catch (Exception e) {
            // acceptBooking/rejectBooking throw when the booking was reassigned, already
            // actioned, or never belonged to this driver. From the driver's side these are all
            // "this card is stale", which is what the card is rewritten to say.
            log.info("Telegram action '{}' by driver {} on booking {} was rejected: {}",
                    accepting ? "accept" : "reject", driver.getId(), bookingId, e.getMessage());
            telegramClient.answerCallbackQuery(callbackId, "This trip is no longer available.");
            telegramClient.editMessageText(chatId, messageId,
                    "⚠️ <b>This trip is no longer available.</b>\n\n"
                            + "It may have been reassigned or already actioned.");
        }
    }

    private Optional<Driver> findLinkedDriver(String telegramUserId) {
        return driverRepository.findByTelegramChatId(telegramUserId);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    /** Telegram ids are JSON numbers; we store and compare them as strings. */
    private static String asId(Object value) {
        if (value instanceof Number n) {
            return String.valueOf(n.longValue());
        }
        return value instanceof String s && !s.isBlank() ? s : null;
    }

    private static Long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }
}
