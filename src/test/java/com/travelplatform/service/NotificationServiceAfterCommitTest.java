package com.travelplatform.service;

import com.travelplatform.entity.Driver;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.entity.User;
import com.travelplatform.repository.NotificationRepository;
import com.travelplatform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The in-app notification row belongs to the transaction - it commits or rolls back with the
 * booking. Email and Telegram leave the building, so they must wait for the commit.
 */
class NotificationServiceAfterCommitTest {

    private NotificationRepository notificationRepository;
    private EmailService emailService;
    private TelegramDispatchService telegram;
    private NotificationService service;
    private final TransactionTemplate tx = new TransactionTemplate(new NoOpTransactionManager());

    private TravelBooking booking;
    private Driver driver;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        emailService = mock(EmailService.class);
        telegram = mock(TelegramDispatchService.class);
        UserRepository userRepository = mock(UserRepository.class);
        // Direct executor: once dispatched, the send runs immediately, so the test sees it.
        service = new NotificationService(notificationRepository, emailService, userRepository, telegram,
                new AfterCommitDispatcher(Runnable::run));

        booking = new TravelBooking();
        booking.setId(UUID.randomUUID());
        booking.setUserId(UUID.randomUUID());
        booking.setUserName("Kavya");
        booking.setFromPlace("Chennai");
        booking.setToPlace("Madurai");
        booking.setFromDate(LocalDate.of(2026, 11, 1));
        booking.setTotalAmount(new java.math.BigDecimal("4500.0"));

        driver = new Driver();
        driver.setId(7L);
        driver.setName("Murugan");
        driver.setEmail("murugan@example.com");

        User user = new User();
        user.setEmail("kavya@example.com");
        user.setName("Kavya");
        when(userRepository.findById(any())).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("driver assignment: the row is written in the transaction, email and Telegram only after commit")
    void driverAssignedWaitsForCommit() {
        tx.executeWithoutResult(status -> {
            service.notifyDriverAssigned(booking, driver);
            verify(notificationRepository).save(any());
            verify(telegram, never()).sendTripAssigned(any(), any());
            verify(emailService, never()).sendTripAssignedEmail(anyString(), anyString(), any());
        });

        verify(telegram).sendTripAssigned(booking, driver);
        verify(emailService).sendTripAssignedEmail("murugan@example.com", "Murugan", booking);
    }

    @Test
    @DisplayName("a rolled-back booking never reaches the driver's phone or inbox")
    void rollbackSendsNothing() {
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
            service.notifyDriverAssigned(booking, driver);
            throw new IllegalStateException("insert refused");
        }));

        verifyNoInteractions(telegram);
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("every passenger email waits for commit too")
    void passengerEmailsWaitForCommit() {
        tx.executeWithoutResult(status -> {
            service.notifyTripAccepted(booking, driver);
            service.notifyTripRejected(booking, driver, null);
            service.notifyTripEnded(booking, driver);
            service.notifyPaymentReceived(booking);
            verifyNoInteractions(emailService);
        });

        verify(emailService).sendTripAcceptedEmail(anyString(), anyString(), any(), any());
        verify(emailService).sendTripRejectedEmail(anyString(), anyString(), any());
        verify(emailService).sendTripEndedEmail(anyString(), anyString(), any());
        verify(emailService).sendPaymentConfirmationEmail(anyString(), anyString(), any());
    }
}
