package com.travelplatform.service;

import com.travelplatform.config.ExternalHttpClients;
import com.travelplatform.config.TelegramProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin wrapper over the Telegram Bot HTTP API.
 *
 * <p>Two rules hold for everything in here:
 *
 * <ol>
 *   <li><strong>Never throw.</strong> Telegram is a third party on the booking write path.
 *       A dispatch push failing must not roll back an assignment that already happened in
 *       our database, so failures are logged and reported as a boolean.
 *   <li><strong>Never interpolate untrusted text.</strong> Messages are sent as HTML and
 *       carry passenger-supplied place names, so everything variable goes through
 *       {@link #escapeHtml(String)}. Without it a passenger could type markup into a place
 *       name and control what the driver's dispatch card says - including planting a link.
 * </ol>
 */
@Component
public class TelegramClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramClient.class);

    /**
     * Telegram rejects callback_data longer than 64 <em>bytes</em>. Our longest payload is
     * "accept:" plus a 36-char UUID = 43, but a future prefix change could silently cross
     * the line and the API error is easy to miss, so it is asserted at build time instead.
     */
    private static final int MAX_CALLBACK_DATA_BYTES = 64;

    private final TelegramProperties properties;
    private final RestTemplate restTemplate;

    public TelegramClient(TelegramProperties properties) {
        this.properties = properties;
        // Timeout-bounded: an unresponsive Telegram must not pin the request thread that is
        // completing a booking assignment. See ExternalHttpClients.
        this.restTemplate = ExternalHttpClients.forThirdPartyApis();
    }

    /**
     * Sends an HTML message, optionally with inline buttons.
     *
     * @param buttons rows of (label, callbackData) pairs; may be empty for a plain message
     * @return true if Telegram accepted the message
     */
    public boolean sendMessage(String chatId, String html, List<List<InlineButton>> buttons) {
        if (!properties.isOperational()) {
            log.debug("Telegram disabled or unconfigured; skipping message to chat {}", chatId);
            return false;
        }
        if (chatId == null || chatId.isBlank()) {
            return false;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", html);
        body.put("parse_mode", "HTML");
        // Place names often expand into link previews that push the buttons off-screen.
        body.put("disable_web_page_preview", true);
        if (buttons != null && !buttons.isEmpty()) {
            body.put("reply_markup", Map.of("inline_keyboard", toKeyboard(buttons)));
        }

        return call("sendMessage", body);
    }

    /**
     * Acknowledges a button press.
     *
     * <p>Telegram shows a loading spinner on the button until this is called; skipping it
     * leaves the driver staring at a control that looks stuck even though the trip was
     * accepted. The toast is also the fastest feedback the driver gets, so it carries the
     * outcome.
     */
    public boolean answerCallbackQuery(String callbackQueryId, String text) {
        if (!properties.isOperational() || callbackQueryId == null || callbackQueryId.isBlank()) {
            return false;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("callback_query_id", callbackQueryId);
        if (text != null && !text.isBlank()) {
            body.put("text", text);
            body.put("show_alert", false);
        }
        return call("answerCallbackQuery", body);
    }

    /**
     * Rewrites an already-delivered dispatch card and drops its buttons.
     *
     * <p>Called once a trip is accepted or rejected so the card reflects the outcome and
     * cannot be pressed again. Best-effort: the authoritative guard against double action is
     * the booking state check in the service, not the absence of a button.
     */
    public boolean editMessageText(String chatId, Long messageId, String html) {
        if (!properties.isOperational() || messageId == null) {
            return false;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("text", html);
        body.put("parse_mode", "HTML");
        body.put("disable_web_page_preview", true);
        return call("editMessageText", body);
    }

    private List<List<Map<String, String>>> toKeyboard(List<List<InlineButton>> buttons) {
        List<List<Map<String, String>>> keyboard = new ArrayList<>();
        for (List<InlineButton> row : buttons) {
            List<Map<String, String>> keyboardRow = new ArrayList<>();
            for (InlineButton button : row) {
                int bytes = button.callbackData().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                if (bytes > MAX_CALLBACK_DATA_BYTES) {
                    // Telegram would reject the whole message, losing the dispatch entirely.
                    // Dropping one button still delivers a readable card to the driver.
                    log.error("callback_data '{}' is {} bytes, over Telegram's {} limit; button dropped",
                            button.callbackData(), bytes, MAX_CALLBACK_DATA_BYTES);
                    continue;
                }
                keyboardRow.add(Map.of("text", button.label(), "callback_data", button.callbackData()));
            }
            if (!keyboardRow.isEmpty()) {
                keyboard.add(keyboardRow);
            }
        }
        return keyboard;
    }

    private boolean call(String method, Map<String, Object> body) {
        String url = properties.getApiBaseUrl() + "/bot" + properties.getBotToken() + "/" + method;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            return true;
        } catch (RestClientException e) {
            // The bot token appears in the URL, so the message - which may echo it - is never
            // logged. Only the method name and the exception type are safe to record.
            log.warn("Telegram {} failed: {}", method, e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Escapes the five characters Telegram's HTML parse mode treats as markup.
     *
     * <p>Applied to every value that originated outside this system - passenger names, place
     * names typed into the booking form - before it reaches a message body.
     */
    public static String escapeHtml(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /** A single inline keyboard button: visible label plus the opaque payload sent back on press. */
    public record InlineButton(String label, String callbackData) {
    }
}
