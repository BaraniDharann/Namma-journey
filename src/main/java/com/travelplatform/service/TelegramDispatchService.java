package com.travelplatform.service;

import com.travelplatform.config.TelegramProperties;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.TravelBooking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.travelplatform.service.TelegramClient.escapeHtml;

/**
 * Outbound half of the Telegram driver-dispatch channel: the trip card sent on assignment.
 *
 * <p>Binding a Telegram account to a driver lives in {@link TelegramLinkService}; handling
 * what the driver presses lives in {@link TelegramWebhookService}.
 */
@Service
public class TelegramDispatchService {

    private static final Logger log = LoggerFactory.getLogger(TelegramDispatchService.class);

    private static final DateTimeFormatter TRIP_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final TelegramClient telegramClient;
    private final TelegramProperties properties;

    public TelegramDispatchService(TelegramClient telegramClient, TelegramProperties properties) {
        this.telegramClient = telegramClient;
        this.properties = properties;
    }

    /**
     * Sends the dispatch card for a newly assigned trip.
     *
     * <p>Returns false — never throws — when the driver has not linked Telegram, when the
     * integration is off, or when Telegram rejects the send. The caller has already committed
     * the assignment; this channel is an accelerator on top of the in-app and email
     * notifications, not the system of record.
     */
    public boolean sendTripAssigned(TravelBooking booking, Driver driver) {
        if (!properties.isOperational()) {
            return false;
        }
        if (driver.getTelegramChatId() == null) {
            log.debug("Driver {} has not linked Telegram; dispatch card skipped", driver.getId());
            return false;
        }

        boolean sent = telegramClient.sendMessage(
                driver.getTelegramChatId(),
                buildTripCard(booking),
                List.of(List.of(
                        new TelegramClient.InlineButton("✅ Accept", "accept:" + booking.getId()),
                        new TelegramClient.InlineButton("❌ Not available", "reject:" + booking.getId())
                )));

        if (sent) {
            log.info("Telegram dispatch card sent to driver {} for booking {}", driver.getId(), booking.getId());
        }
        return sent;
    }

    /**
     * Builds the trip card.
     *
     * <p>Every interpolated value is escaped: {@code userName}, {@code userPhone},
     * {@code fromPlace} and {@code toPlace} are all free text a passenger typed into the
     * booking form, and the card is rendered as HTML.
     */
    String buildTripCard(TravelBooking booking) {
        StringBuilder card = new StringBuilder();
        card.append("🚕 <b>New Trip Assigned</b>\n\n");
        card.append("📍 <b>From:</b> ").append(escapeHtml(booking.getFromPlace())).append('\n');
        card.append("🏁 <b>To:</b> ").append(escapeHtml(booking.getToPlace())).append('\n');
        if (booking.getFromDate() != null) {
            card.append("📅 <b>Date:</b> ").append(booking.getFromDate().format(TRIP_DATE)).append('\n');
        }
        card.append("👤 <b>Passenger:</b> ").append(escapeHtml(booking.getUserName()));
        if (booking.getUserPhone() != null) {
            card.append(" — ").append(escapeHtml(booking.getUserPhone()));
        }
        card.append('\n');
        if (booking.getDistanceKm() != null) {
            card.append("🛣 <b>Distance:</b> ").append(String.format("%.0f km", booking.getDistanceKm())).append('\n');
        }
        if (booking.getTotalAmount() != null) {
            card.append("💰 <b>Fare:</b> ₹").append(String.format("%.2f", booking.getTotalAmount())).append('\n');
        }
        card.append("\nTap a button below — no need to open the app.");
        return card.toString();
    }
}
