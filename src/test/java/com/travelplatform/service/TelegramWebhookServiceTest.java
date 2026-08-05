package com.travelplatform.service;

import com.travelplatform.entity.Driver;
import com.travelplatform.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Once a webhook call is known to come from Telegram, the remaining question is <em>who</em>
 * is acting. The payload cannot answer that: a caller who obtained the webhook secret, or a
 * driver crafting a callback by hand, controls every field in it.
 *
 * <p>So identity is taken only from the Telegram user id on the update and resolved against
 * a stored link, and the booking id is then handed to the existing accept/reject methods,
 * which independently enforce that the trip belongs to that driver. These tests hold that
 * line in place.
 */
class TelegramWebhookServiceTest {

    private static final String LINKED_CHAT_ID = "555000111";
    private static final Long DRIVER_ID = 7L;
    private static final String BOOKING_ID = UUID.randomUUID().toString();

    private DriverRepository driverRepository;
    private DriverService driverService;
    private TelegramLinkService linkService;
    private TelegramClient telegramClient;
    private TelegramWebhookService service;

    @BeforeEach
    void setUp() {
        driverRepository = mock(DriverRepository.class);
        driverService = mock(DriverService.class);
        linkService = mock(TelegramLinkService.class);
        telegramClient = mock(TelegramClient.class);
        service = new TelegramWebhookService(driverRepository, driverService, linkService, telegramClient);
    }

    private Driver linkedDriver() {
        Driver driver = new Driver();
        driver.setId(DRIVER_ID);
        driver.setName("Murugan");
        driver.setTelegramChatId(LINKED_CHAT_ID);
        return driver;
    }

    private static Map<String, Object> callbackUpdate(String fromId, String data) {
        return Map.of("callback_query", Map.of(
                "id", "cb-1",
                "data", data,
                "from", Map.of("id", Long.parseLong(fromId)),
                "message", Map.of("message_id", 42, "chat", Map.of("id", Long.parseLong(fromId)))));
    }

    @Test
    @DisplayName("Accept is applied under the driver the Telegram account is linked to")
    void acceptUsesTheLinkedDriverIdentity() {
        when(driverRepository.findByTelegramChatId(LINKED_CHAT_ID)).thenReturn(Optional.of(linkedDriver()));

        service.handleUpdate(callbackUpdate(LINKED_CHAT_ID, "accept:" + BOOKING_ID));

        verify(driverService).acceptBooking(DRIVER_ID, BOOKING_ID);
    }

    @Test
    @DisplayName("Not-available is applied under the driver the Telegram account is linked to")
    void rejectUsesTheLinkedDriverIdentity() {
        when(driverRepository.findByTelegramChatId(LINKED_CHAT_ID)).thenReturn(Optional.of(linkedDriver()));

        service.handleUpdate(callbackUpdate(LINKED_CHAT_ID, "reject:" + BOOKING_ID));

        verify(driverService).rejectBooking(DRIVER_ID, BOOKING_ID);
    }

    @Test
    @DisplayName("an unlinked Telegram account cannot action any booking")
    void unlinkedAccountCannotAct() {
        when(driverRepository.findByTelegramChatId(anyString())).thenReturn(Optional.empty());

        service.handleUpdate(callbackUpdate("999888777", "accept:" + BOOKING_ID));

        verifyNoInteractions(driverService);
    }

    @Test
    @DisplayName("a stale card is reported to the driver instead of surfacing as an error")
    void staleBookingIsHandledGracefully() {
        when(driverRepository.findByTelegramChatId(LINKED_CHAT_ID)).thenReturn(Optional.of(linkedDriver()));
        when(driverService.acceptBooking(anyLong(), anyString()))
                .thenThrow(new RuntimeException("This booking is not assigned to you"));

        // The driver pressed Accept on a trip that was reassigned while the card sat in their
        // chat. Telegram re-delivers anything that does not return 2xx, so this must not throw.
        assertDoesNotThrow(() -> service.handleUpdate(callbackUpdate(LINKED_CHAT_ID, "accept:" + BOOKING_ID)));

        verify(telegramClient).editMessageText(eq(LINKED_CHAT_ID), eq(42L), anyString());
    }

    @Test
    @DisplayName("an unrecognised callback payload is ignored")
    void unknownCallbackDataIsIgnored() {
        when(driverRepository.findByTelegramChatId(LINKED_CHAT_ID)).thenReturn(Optional.of(linkedDriver()));

        service.handleUpdate(callbackUpdate(LINKED_CHAT_ID, "delete_everything:1"));

        verify(driverService, never()).acceptBooking(anyLong(), anyString());
        verify(driverService, never()).rejectBooking(anyLong(), anyString());
    }

    @Test
    @DisplayName("a malformed update is swallowed rather than left to be retried forever")
    void malformedUpdateDoesNotThrow() {
        assertDoesNotThrow(() -> service.handleUpdate(Map.of("callback_query", "not-an-object")));
        assertDoesNotThrow(() -> service.handleUpdate(Map.of()));
        assertDoesNotThrow(() -> service.handleUpdate(Map.of("message", Map.of("text", "/start"))));
    }

    @Test
    @DisplayName("/start hands the payload to the link service for redemption")
    void startDelegatesRedemption() {
        when(linkService.redeem(anyString(), anyString()))
                .thenReturn(new TelegramLinkService.RedeemResult(TelegramLinkService.Status.SUCCESS, "Murugan"));

        service.handleUpdate(Map.of("message", Map.of(
                "text", "/start abc123",
                "from", Map.of("id", 555000111L))));

        verify(linkService).redeem("555000111", "abc123");
    }

    @Test
    @DisplayName("/start with no payload still reaches the link service, which decides the outcome")
    void bareStartIsDelegatedToo() {
        when(linkService.redeem(anyString(), any()))
                .thenReturn(new TelegramLinkService.RedeemResult(TelegramLinkService.Status.NO_TOKEN, null));

        service.handleUpdate(Map.of("message", Map.of(
                "text", "/start",
                "from", Map.of("id", 555000111L))));

        verify(linkService).redeem("555000111", "");
    }
}
