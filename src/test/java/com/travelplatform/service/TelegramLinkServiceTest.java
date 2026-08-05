package com.travelplatform.service;

import com.travelplatform.config.TelegramProperties;
import com.travelplatform.entity.Driver;
import com.travelplatform.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redeeming an onboarding token is a privilege grant: it hands whoever presents the token the
 * ability to accept and reject that driver's trips from then on. These tests cover the four
 * ways that grant must be refused, and that a successful grant burns the token.
 */
class TelegramLinkServiceTest {

    private static final String TOKEN = "a-valid-token";
    private static final String CHAT_ID = "555000111";

    private DriverRepository driverRepository;
    private TelegramLinkService service;

    @BeforeEach
    void setUp() {
        driverRepository = mock(DriverRepository.class);
        TelegramProperties properties = new TelegramProperties();
        properties.setBotUsername("NammaJourneyBot");
        service = new TelegramLinkService(driverRepository, properties);
    }

    private static Driver driverWithToken(Long id, LocalDateTime expiry) {
        Driver driver = new Driver();
        driver.setId(id);
        driver.setName("Murugan");
        driver.setTelegramLinkToken(TOKEN);
        driver.setTelegramLinkTokenExpiresAt(expiry);
        return driver;
    }

    @Test
    @DisplayName("a valid token links the account and is burned so it cannot be reused")
    void validTokenLinksAndIsSingleUse() {
        Driver driver = driverWithToken(7L, LocalDateTime.now().plusHours(1));
        when(driverRepository.findByTelegramLinkToken(TOKEN)).thenReturn(Optional.of(driver));
        when(driverRepository.findByTelegramChatId(CHAT_ID)).thenReturn(Optional.empty());

        TelegramLinkService.RedeemResult result = service.redeem(CHAT_ID, TOKEN);

        assertEquals(TelegramLinkService.Status.SUCCESS, result.status());
        assertEquals("Murugan", result.driverName());
        assertEquals(CHAT_ID, driver.getTelegramChatId());
        assertNotNull(driver.getTelegramLinkedAt());
        // Burned: a QR code photographed off the owner's screen is worthless after first use.
        assertNull(driver.getTelegramLinkToken());
        assertNull(driver.getTelegramLinkTokenExpiresAt());
        verify(driverRepository).save(driver);
    }

    @Test
    @DisplayName("an expired token is refused")
    void expiredTokenIsRefused() {
        Driver driver = driverWithToken(7L, LocalDateTime.now().minusMinutes(1));
        when(driverRepository.findByTelegramLinkToken(TOKEN)).thenReturn(Optional.of(driver));

        TelegramLinkService.RedeemResult result = service.redeem(CHAT_ID, TOKEN);

        assertEquals(TelegramLinkService.Status.EXPIRED, result.status());
        assertNull(driver.getTelegramChatId());
        verify(driverRepository, never()).save(driver);
    }

    @Test
    @DisplayName("an unknown or already-used token is refused")
    void unknownTokenIsRefused() {
        when(driverRepository.findByTelegramLinkToken(anyString())).thenReturn(Optional.empty());

        assertEquals(TelegramLinkService.Status.INVALID, service.redeem(CHAT_ID, "guessed").status());
        verify(driverRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("opening the bot without a link produces no grant")
    void bareStartIsRefused() {
        assertEquals(TelegramLinkService.Status.NO_TOKEN, service.redeem(CHAT_ID, "").status());
        assertEquals(TelegramLinkService.Status.NO_TOKEN, service.redeem(CHAT_ID, null).status());
        verify(driverRepository, never()).findByTelegramLinkToken(anyString());
    }

    @Test
    @DisplayName("a Telegram account already linked to another driver cannot take over a second one")
    void accountAlreadyLinkedElsewhereIsRefused() {
        Driver target = driverWithToken(7L, LocalDateTime.now().plusHours(1));
        Driver incumbent = new Driver();
        incumbent.setId(9L);
        incumbent.setTelegramChatId(CHAT_ID);

        when(driverRepository.findByTelegramLinkToken(TOKEN)).thenReturn(Optional.of(target));
        when(driverRepository.findByTelegramChatId(CHAT_ID)).thenReturn(Optional.of(incumbent));

        TelegramLinkService.RedeemResult result = service.redeem(CHAT_ID, TOKEN);

        assertEquals(TelegramLinkService.Status.ALREADY_LINKED_TO_OTHER, result.status());
        assertNull(target.getTelegramChatId());
        verify(driverRepository, never()).save(target);
    }

    @Test
    @DisplayName("re-linking the same driver from the same account is allowed")
    void relinkingSameDriverIsAllowed() {
        Driver driver = driverWithToken(7L, LocalDateTime.now().plusHours(1));
        driver.setTelegramChatId(CHAT_ID);
        when(driverRepository.findByTelegramLinkToken(TOKEN)).thenReturn(Optional.of(driver));
        when(driverRepository.findByTelegramChatId(CHAT_ID)).thenReturn(Optional.of(driver));

        assertEquals(TelegramLinkService.Status.SUCCESS, service.redeem(CHAT_ID, TOKEN).status());
    }

    @Test
    @DisplayName("issued links are unguessable and each issue revokes the last")
    void issuedTokensAreRandomAndSupersedeEachOther() {
        Driver driver = new Driver();
        driver.setId(7L);
        when(driverRepository.findById(7L)).thenReturn(Optional.of(driver));

        String first = service.createLinkUrl(7L);
        String firstToken = driver.getTelegramLinkToken();
        String second = service.createLinkUrl(7L);
        String secondToken = driver.getTelegramLinkToken();

        assertTrue(first.startsWith("https://t.me/NammaJourneyBot?start="));
        // Not derived from the sequential driver id - that would let anyone walk the range.
        assertTrue(first.length() > "https://t.me/NammaJourneyBot?start=".length() + 20);
        assertNotEquals(firstToken, secondToken);
        assertTrue(driver.getTelegramLinkTokenExpiresAt().isAfter(LocalDateTime.now()));
    }
}
