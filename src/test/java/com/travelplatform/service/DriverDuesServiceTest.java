package com.travelplatform.service;

import com.travelplatform.entity.Driver;
import com.travelplatform.entity.DriverDuesPayment;
import com.travelplatform.entity.DriverLedgerEntry;
import com.travelplatform.repository.DriverDuesPaymentRepository;
import com.travelplatform.repository.DriverLedgerRepository;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A driver's "I paid" is only a claim: it can never exceed what they owe, never reuse a UTR, and
 * moves the balance only when the owner confirms it - once.
 */
class DriverDuesServiceTest {

    private static final long DRIVER = 12L;

    private DriverDuesPaymentRepository payments;
    private DriverLedgerRepository ledger;
    private DriverLedgerService ledgerService;
    private DriverDuesService service;
    private Driver driver;

    @BeforeEach
    void setUp() {
        payments = mock(DriverDuesPaymentRepository.class);
        ledger = mock(DriverLedgerRepository.class);
        ledgerService = mock(DriverLedgerService.class);
        DriverRepository drivers = mock(DriverRepository.class);
        service = new DriverDuesService(payments, ledger, ledgerService, drivers, mock(NotificationRepository.class),
                mock(OwnerTelegramAlerts.class), mock(TelegramClient.class), mock(AfterCommitDispatcher.class),
                mock(TransactionTemplate.class));
        ReflectionTestUtils.setField(service, "ownerUpiId", "owner@okaxis");

        driver = new Driver();
        driver.setId(DRIVER);
        driver.setName("Barani");
        driver.setMobile("9876543210");
        driver.setFleet(Driver.Fleet.INDEPENDENT);
        when(drivers.findById(DRIVER)).thenReturn(Optional.of(driver));

        when(ledger.balanceOf(DRIVER)).thenReturn(new BigDecimal("-450.00")); // owes 450
        when(payments.pendingTotal(DRIVER)).thenReturn(BigDecimal.ZERO);
        when(payments.findByDriverIdOrderBySubmittedAtDesc(eq(DRIVER), any())).thenReturn(List.of());
        when(payments.save(any())).thenAnswer(inv -> {
            DriverDuesPayment p = inv.getArgument(0);
            p.setId(5L);
            return p;
        });
    }

    private DriverDuesPayment pending() {
        DriverDuesPayment p = new DriverDuesPayment();
        p.setId(5L);
        p.setDriverId(DRIVER);
        p.setAmount(new BigDecimal("450.00"));
        p.setUtr("431245678901");
        when(payments.findById(5L)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    @DisplayName("The UPI link carries the owner's UPI ID and exactly what is owed")
    void viewBuildsTheUpiLink() {
        DriverDuesService.DuesView v = service.view(DRIVER);

        assertEquals(0, v.payable().compareTo(new BigDecimal("450.00")));
        assertTrue(v.upiLink().startsWith("upi://pay?pa=owner%40okaxis"));
        assertTrue(v.upiLink().contains("am=450.00"));
    }

    @Test
    @DisplayName("Nothing owed: no payment link")
    void noLinkWhenNothingOwed() {
        when(ledger.balanceOf(DRIVER)).thenReturn(new BigDecimal("120.00")); // the platform owes them

        assertNull(service.view(DRIVER).upiLink());
    }

    @Test
    @DisplayName("A claim saves as PENDING and does not touch the ledger")
    void submitIsOnlyAClaim() {
        service.submit(DRIVER, new BigDecimal("450.00"), "431245678901");

        verify(payments).save(any());
        verify(ledgerService, never()).recordTransfer(anyLong(), any(), any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Claiming more than owed - counting claims already waiting - is refused")
    void cannotClaimMoreThanOwed() {
        assertThrows(IllegalArgumentException.class, () -> service.submit(DRIVER, new BigDecimal("451.00"), "431245678901"));

        when(payments.pendingTotal(DRIVER)).thenReturn(new BigDecimal("400.00"));
        assertThrows(IllegalArgumentException.class, () -> service.submit(DRIVER, new BigDecimal("100.00"), "431245678902"));
        verify(payments, never()).save(any());
    }

    @Test
    @DisplayName("A UTR already claimed, or already recorded by the owner, cannot be used again")
    void utrCannotBeReused() {
        when(payments.utrInUse("431245678901")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.submit(DRIVER, new BigDecimal("100.00"), "431245678901"));

        when(ledger.existsByReference("431245678902")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.submit(DRIVER, new BigDecimal("100.00"), "431245678902"));
        verify(payments, never()).save(any());
    }

    @Test
    @DisplayName("Owner-fleet drivers cannot submit commission payments")
    void ownerFleetRefused() {
        driver.setFleet(Driver.Fleet.OWNER_FLEET);
        assertThrows(IllegalArgumentException.class, () -> service.submit(DRIVER, new BigDecimal("10.00"), "431245678901"));
    }

    @Test
    @DisplayName("Confirm writes the COLLECTION ledger entry with the UTR")
    void confirmRecordsTheCollection() {
        pending();
        when(payments.decide(eq(5L), eq(DriverDuesPayment.Status.CONFIRMED), any(), any())).thenReturn(1);

        service.confirm(5L, "owner:1");

        verify(ledgerService).recordTransfer(DRIVER, DriverLedgerEntry.Type.COLLECTION, new BigDecimal("450.00"),
                "431245678901", "Paid in the app", "owner:1");
    }

    @Test
    @DisplayName("A second decision on the same claim (Telegram and website at once) is refused")
    void decidedOnlyOnce() {
        pending();
        when(payments.decide(eq(5L), any(), any(), any())).thenReturn(0);

        assertThrows(IllegalArgumentException.class, () -> service.confirm(5L, "owner:1"));
        verify(ledgerService, never()).recordTransfer(anyLong(), any(), any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Not received leaves the balance alone")
    void rejectDoesNotTouchTheLedger() {
        pending();
        when(payments.decide(eq(5L), eq(DriverDuesPayment.Status.REJECTED), any(), any())).thenReturn(1);

        service.reject(5L, "owner:1");

        verify(ledgerService, never()).recordTransfer(anyLong(), any(), any(), anyString(), anyString(), anyString());
    }
}
