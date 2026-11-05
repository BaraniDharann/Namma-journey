package com.travelplatform.service;

import com.travelplatform.config.OwnerScope;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.DriverVerificationCheck;
import com.travelplatform.exception.ConflictException;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.DriverVerificationCheckRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An owner approves the drivers they add, after checking the licence (Sarathi) and the RC
 * (Vahan / mParivahan) themselves. The driver's login is emailed only on approval.
 */
class OwnerDriverReviewServiceTest {

    private static final long ME = 7L;

    private DriverRepository drivers;
    private DriverVerificationCheckRepository checks;
    private EmailService email;
    private OwnerDriverReviewService service;
    private final List<DriverVerificationCheck> saved = new ArrayList<>();
    private Driver driver;

    @BeforeEach
    void setUp() {
        drivers = mock(DriverRepository.class);
        checks = mock(DriverVerificationCheckRepository.class);
        email = mock(EmailService.class);
        service = new OwnerDriverReviewService(drivers, checks, email, new BCryptPasswordEncoder(4));
        OwnerScope.bind(ME, false);

        driver = new Driver();
        driver.setId(5L);
        driver.setName("Ravi");
        driver.setEmail("ravi@x.com");
        driver.setMobile("9000000001");
        driver.setOwnerId(ME);
        driver.setVerificationStatus(Driver.Verification.UNDER_REVIEW);
        when(drivers.findById(5L)).thenReturn(Optional.of(driver));
        when(drivers.save(any(Driver.class))).thenAnswer(i -> i.getArgument(0));
        when(checks.save(any(DriverVerificationCheck.class))).thenAnswer(i -> {
            saved.removeIf(c -> c.getType() == i.<DriverVerificationCheck>getArgument(0).getType());
            saved.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(checks.findByDriverId(5L)).thenAnswer(i -> List.copyOf(saved));
    }

    @AfterEach
    void unbind() {
        OwnerScope.clear();
    }

    @Test
    @DisplayName("approval needs both the licence and the RC checked and verified")
    void approveNeedsBothChecks() {
        assertThrows(ConflictException.class, () -> service.approve(5L));
        service.recordCheck(5L, DriverVerificationCheck.Type.LICENCE, DriverVerificationCheck.Result.VERIFIED, null);
        assertThrows(ConflictException.class, () -> service.approve(5L));
        service.recordCheck(5L, DriverVerificationCheck.Type.RC, DriverVerificationCheck.Result.FAILED, "RC expired");
        assertThrows(ConflictException.class, () -> service.approve(5L));
        verify(email, never()).sendDriverCredentials(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("approving emails the driver a fresh login")
    void approveEmailsCredentials() {
        service.recordCheck(5L, DriverVerificationCheck.Type.LICENCE, DriverVerificationCheck.Result.VERIFIED, null);
        service.recordCheck(5L, DriverVerificationCheck.Type.RC, DriverVerificationCheck.Result.VERIFIED, "ok");

        service.approve(5L);

        assertEquals(Driver.Verification.APPROVED, driver.getVerificationStatus());
        assertTrue(driver.isFirstLogin());
        verify(email).sendDriverCredentials(eq("ravi@x.com"), eq("Ravi"), eq("9000000001"), anyString());
    }

    @Test
    @DisplayName("a failed check needs a note saying what was wrong")
    void failedCheckNeedsNote() {
        assertThrows(IllegalArgumentException.class, () -> service.recordCheck(5L,
                DriverVerificationCheck.Type.LICENCE, DriverVerificationCheck.Result.FAILED, "  "));
        verify(checks, never()).save(any());
    }

    @Test
    @DisplayName("rejecting needs a reason, and sends no login")
    void rejectNeedsReason() {
        assertThrows(IllegalArgumentException.class, () -> service.reject(5L, "no"));
        service.reject(5L, "Licence does not match the name");
        assertEquals(Driver.Verification.REJECTED, driver.getVerificationStatus());
        verify(email, never()).sendDriverCredentials(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("another owner's driver is not found")
    void otherOwnersDriver() {
        driver.setOwnerId(8L);
        assertThrows(ResourceNotFoundException.class, () -> service.recordCheck(5L,
                DriverVerificationCheck.Type.LICENCE, DriverVerificationCheck.Result.VERIFIED, null));
        assertThrows(ResourceNotFoundException.class, () -> service.approve(5L));
    }

    @Test
    @DisplayName("an approved driver is not approved again")
    void approveOnlyOnce() {
        driver.setVerificationStatus(Driver.Verification.APPROVED);
        assertThrows(ConflictException.class, () -> service.approve(5L));
    }
}
