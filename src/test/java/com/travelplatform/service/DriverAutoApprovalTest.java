package com.travelplatform.service;

import com.travelplatform.entity.Driver;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Self-signup drivers are approved on their own after the delay - but only when the automatic
 * checks pass, only while still under review, and never over the owner's Reject or Hold.
 */
class DriverAutoApprovalTest {

    /** 11 digits + the one Verhoeff check digit that makes them valid, worked out below. */
    private static final String AADHAAR_BASE = "23412341234";

    private DriverRepository drivers;
    private DriverVerificationService verification;
    private OwnerTelegramAlerts alerts;
    private DriverAutoApprovalService service;
    private Driver driver;

    @BeforeEach
    void setUp() {
        drivers = mock(DriverRepository.class);
        verification = mock(DriverVerificationService.class);
        alerts = mock(OwnerTelegramAlerts.class);
        TransactionTemplate tx = mock(TransactionTemplate.class);
        doAnswer(inv -> {
            ((Consumer<?>) inv.getArgument(0)).accept(null);
            return null;
        }).when(tx).executeWithoutResult(any());
        when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));
        service = new DriverAutoApprovalService(drivers, verification, alerts, mock(NotificationRepository.class), tx);

        driver = new Driver();
        driver.setId(12L);
        driver.setName("Barani");
        driver.setMobile("9876543210");
        driver.setFleet(Driver.Fleet.INDEPENDENT);
        driver.setVerificationStatus(Driver.Verification.UNDER_REVIEW);
        driver.setEmailVerified(true);
        driver.setAutoApproveAt(LocalDateTime.now().minusMinutes(1));
        driver.setPhoto("p");
        driver.setLicensePhoto("l");
        driver.setAadhaarPhoto("a");
        driver.setRcPhoto("r");
        driver.setVehicleNumber("TN01AB1234");
        driver.setLicenseNumber("TN01 20190001234");
        driver.setAadhaarNumber(validAadhaar());
        when(drivers.findById(12L)).thenReturn(Optional.of(driver));
    }

    private static String validAadhaar() {
        for (int d = 0; d <= 9; d++) {
            if (DriverApplicationChecks.validAadhaar(AADHAAR_BASE + d)) {
                return AADHAAR_BASE + d;
            }
        }
        throw new AssertionError("no check digit validates");
    }

    @Test
    @DisplayName("Verhoeff: exactly one last digit makes a valid Aadhaar; 0/1 starts and wrong lengths never do")
    void aadhaarCheckDigit() {
        int valid = 0;
        for (int d = 0; d <= 9; d++) {
            if (DriverApplicationChecks.validAadhaar(AADHAAR_BASE + d)) valid++;
        }
        assertEquals(1, valid);
        assertFalse(DriverApplicationChecks.validAadhaar("1" + validAadhaar().substring(1)));
        assertFalse(DriverApplicationChecks.validAadhaar("12345"));
        assertTrue(DriverApplicationChecks.validAadhaar(validAadhaar().substring(0, 4) + " " + validAadhaar().substring(4)));
    }

    @Test
    @DisplayName("Licence: the standard state-RTO-year-serial format, with or without a separator")
    void licenceFormat() {
        assertTrue(DriverApplicationChecks.validLicence("TN0120190001234"));
        assertTrue(DriverApplicationChecks.validLicence("ka05 20150012345"));
        assertTrue(DriverApplicationChecks.validLicence("MH12-20080012345"));
        assertFalse(DriverApplicationChecks.validLicence("ABC123"));
        assertFalse(DriverApplicationChecks.validLicence("TN01 18990001234"));
    }

    @Test
    @DisplayName("Deadline passed and checks pass: approved")
    void approvedWhenChecksPass() {
        service.autoApproveOne(12L);

        verify(verification).approve(12L, null);
        verify(drivers, never()).holdAutoApproval(anyLong(), any());
    }

    @Test
    @DisplayName("A failed check holds it for the owner instead of approving")
    void heldWhenACheckFails() {
        driver.setAadhaarNumber("234123412340".equals(validAadhaar()) ? "234123412341" : "234123412340");

        service.autoApproveOne(12L);

        verify(verification, never()).approve(anyLong(), any());
        verify(drivers).holdAutoApproval(12L, Driver.Verification.UNDER_REVIEW);
        verify(alerts).send(any(), any(), any());
    }

    @Test
    @DisplayName("Not yet due, held (no deadline), or already decided: left alone")
    void notDueOrHeldOrDecidedIsLeftAlone() {
        driver.setAutoApproveAt(LocalDateTime.now().plusMinutes(5));
        service.autoApproveOne(12L);

        driver.setAutoApproveAt(null);
        service.autoApproveOne(12L);

        driver.setAutoApproveAt(LocalDateTime.now().minusMinutes(1));
        driver.setVerificationStatus(Driver.Verification.REJECTED);
        service.autoApproveOne(12L);

        verify(verification, never()).approve(anyLong(), any());
    }

    @Test
    @DisplayName("Owner-fleet drivers never go through automatic approval")
    void ownerFleetIsNeverAutoApproved() {
        driver.setFleet(Driver.Fleet.OWNER_FLEET);

        service.autoApproveOne(12L);

        verify(verification, never()).approve(anyLong(), any());
    }

    @Test
    @DisplayName("Telegram Reject rejects with a reason the driver can act on")
    void telegramReject() {
        OwnerTelegramAlerts.Outcome out = service.handleTelegram(OwnerTelegramAlerts.REJECT + 12, "Priya");

        verify(verification).reject(12L, DriverAutoApprovalService.TELEGRAM_REJECT_REASON, null);
        assertEquals("Rejected", out.toast());
    }

    @Test
    @DisplayName("Telegram Hold stops the clock and keeps Approve / Reject on the message")
    void telegramHold() {
        when(drivers.holdAutoApproval(12L, Driver.Verification.UNDER_REVIEW)).thenReturn(1);

        OwnerTelegramAlerts.Outcome out = service.handleTelegram(OwnerTelegramAlerts.HOLD + 12, "Priya");

        assertEquals("Held", out.toast());
        assertEquals(2, out.buttons().get(0).size());
    }

    @Test
    @DisplayName("Pressing a button on an application already decided says so instead of failing")
    void alreadyDecided() {
        when(verification.approve(eq(12L), any())).thenThrow(new IllegalArgumentException("This application is not waiting for review"));

        assertEquals("Already decided", service.handleTelegram(OwnerTelegramAlerts.APPROVE + 12, "Priya").toast());
        assertTrue(service.handleTelegram(OwnerTelegramAlerts.HOLD + 12, "Priya").buttons().isEmpty());
    }

    @Test
    @DisplayName("The sweep only asks for INDEPENDENT applications under review whose time has come")
    void sweepQueriesDueApplications() {
        when(drivers.findByFleetAndVerificationStatusAndEmailVerifiedTrueAndAutoApproveAtLessThanEqual(
                eq(Driver.Fleet.INDEPENDENT), eq(Driver.Verification.UNDER_REVIEW), any())).thenReturn(List.of(driver));

        service.sweep();

        verify(verification).approve(12L, null);
    }
}
