package com.travelplatform.service;

import com.travelplatform.config.TelegramProperties;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.TravelBooking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The dispatch card is rendered by Telegram as HTML and is built from text a passenger typed
 * into the booking form. Escaping is therefore not cosmetic: without it a passenger could put
 * markup in a place name and control what the driver's card says, including planting a link.
 */
class TelegramDispatchServiceTest {

    private TelegramClient telegramClient;
    private TelegramProperties properties;
    private TelegramDispatchService service;

    @BeforeEach
    void setUp() {
        telegramClient = mock(TelegramClient.class);
        properties = new TelegramProperties();
        properties.setEnabled(true);
        properties.setBotToken("123:abc");
        service = new TelegramDispatchService(telegramClient, properties);
    }

    private static TravelBooking booking(String userName, String fromPlace, String toPlace) {
        TravelBooking booking = new TravelBooking();
        booking.setId(UUID.randomUUID());
        booking.setUserName(userName);
        booking.setUserPhone("9876543210");
        booking.setFromPlace(fromPlace);
        booking.setToPlace(toPlace);
        booking.setFromDate(LocalDate.of(2026, 7, 28));
        booking.setDistanceKm(32.0);
        booking.setTotalAmount(850.0);
        return booking;
    }

    @Test
    @DisplayName("passenger-supplied markup is escaped, not rendered")
    void passengerSuppliedMarkupIsEscaped() {
        String card = service.buildTripCard(
                booking("<b>Boss</b>", "<a href=\"http://evil.example\">Free trip</a>", "Yercaud"));

        // The raw tags must not survive into the message body.
        assertFalse(card.contains("<a href="), "anchor tag survived escaping");
        assertFalse(card.contains("<b>Boss</b>"), "passenger name was rendered as markup");
        // ...but the text is still readable to the driver.
        assertTrue(card.contains("&lt;b&gt;Boss&lt;/b&gt;"));
        assertTrue(card.contains("Yercaud"));
    }

    @Test
    @DisplayName("an ordinary booking renders the details a driver needs to decide")
    void ordinaryBookingRendersDetails() {
        String card = service.buildTripCard(booking("Ravi", "Salem", "Yercaud"));

        assertTrue(card.contains("Salem"));
        assertTrue(card.contains("Yercaud"));
        assertTrue(card.contains("Ravi"));
        assertTrue(card.contains("9876543210"));
        assertTrue(card.contains("28 Jul 2026"));
        assertTrue(card.contains("32 km"));
        assertTrue(card.contains("850.00"));
    }

    @Test
    @DisplayName("a driver who has not linked Telegram is skipped without error")
    void unlinkedDriverIsSkipped() {
        Driver driver = new Driver();
        driver.setId(7L);
        driver.setTelegramChatId(null);

        assertFalse(service.sendTripAssigned(booking("Ravi", "Salem", "Yercaud"), driver));
        verifyNoInteractions(telegramClient);
    }

    @Test
    @DisplayName("with the integration switched off nothing is sent, even for a linked driver")
    void disabledIntegrationSendsNothing() {
        properties.setEnabled(false);
        Driver driver = new Driver();
        driver.setId(7L);
        driver.setTelegramChatId("555000111");

        assertFalse(service.sendTripAssigned(booking("Ravi", "Salem", "Yercaud"), driver));
        verifyNoInteractions(telegramClient);
    }

    @Test
    @DisplayName("escapeHtml neutralises every character Telegram treats as markup")
    void escapeHtmlCoversAllMarkupCharacters() {
        String escaped = TelegramClient.escapeHtml("<&>\"'");
        assertTrue(escaped.equals("&lt;&amp;&gt;&quot;&#39;"), "unexpected escaping: " + escaped);
        // Ampersand must be escaped first or the other entities get double-encoded.
        assertFalse(TelegramClient.escapeHtml("a & b").contains("&amp;amp;"));
    }
}
