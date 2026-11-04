package com.travelplatform.service.bus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.outbox.JobHandler;
import com.travelplatform.service.TelegramClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sends a bus ticket message to a customer's Telegram chat. Queued through the outbox by
 * BusNotifier, so a message is only sent for a booking change that committed.
 *
 * <p>Best effort, like the ride updates: Telegram refusing (bot not configured, chat blocked) is
 * logged and the job counts as done, so a blocked chat does not retry for a day. Email carries the
 * same content.
 */
@Component
public class BusTelegramJobHandler implements JobHandler {

    public static final String TYPE = "bus-telegram";

    private static final Logger log = LoggerFactory.getLogger(BusTelegramJobHandler.class);

    /** The job payload: an HTML message (already escaped) for one chat. */
    public record Message(String chatId, String html) {
    }

    private final TelegramClient telegram;
    private final ObjectMapper objectMapper;

    public BusTelegramJobHandler(TelegramClient telegram, ObjectMapper objectMapper) {
        this.telegram = telegram;
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void handle(String payload) throws Exception {
        Message m = objectMapper.readValue(payload, Message.class);
        if (!telegram.sendMessage(m.chatId(), m.html(), List.of())) {
            log.info("Bus ticket Telegram message to chat {} was not delivered", m.chatId());
        }
    }
}
