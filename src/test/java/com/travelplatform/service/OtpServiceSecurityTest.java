package com.travelplatform.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.travelplatform.entity.Otp;
import com.travelplatform.repository.OtpRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * OTPs guard signup and every password reset. They were guessable without limit, generated with
 * java.util.Random, and written to the logs - including the CORRECT code on every wrong guess, so
 * one deliberate miss plus log access was an account takeover.
 */
class OtpServiceSecurityTest {

    private static final String EMAIL = "victim@example.com";

    private OtpRepository repo;
    private OtpService service;
    private ListAppender<ILoggingEvent> logs;
    private Otp current;

    @BeforeEach
    void setUp() {
        repo = mock(OtpRepository.class);
        service = new OtpService();
        ReflectionTestUtils.setField(service, "otpRepository", repo);
        ReflectionTestUtils.setField(service, "emailService", mock(EmailService.class));
        ReflectionTestUtils.setField(service, "otpExpiryMinutes", 5);
        ReflectionTestUtils.setField(service, "testMode", false);

        current = new Otp();
        current.setEmail(EMAIL);
        current.setOtp("482913");
        current.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        current.setExpiryTime(LocalDateTime.now().plusMinutes(4));
        when(repo.findTopByEmailAndVerifiedFalseAndExpiryTimeAfterOrderByCreatedAtDesc(anyString(), any()))
                .thenAnswer(i -> current.isVerified() ? Optional.empty() : Optional.of(current));
        when(repo.save(any(Otp.class))).thenAnswer(i -> i.getArgument(0));

        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(OtpService.class)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(OtpService.class)).detachAppender(logs);
    }

    @Test
    @DisplayName("after 5 wrong guesses the code is dead - even the right code no longer works")
    void attemptsAreCapped() {
        for (int i = 0; i < 5; i++) {
            assertFalse(service.verifyOtp(EMAIL, "000000"));
        }
        assertFalse(service.verifyOtp(EMAIL, "482913"), "a 6th try with the real code must still fail");
    }

    @Test
    @DisplayName("each wrong guess is counted on the stored code")
    void wrongGuessesAreCounted() {
        service.verifyOtp(EMAIL, "111111");
        service.verifyOtp(EMAIL, "222222");
        assertEquals(2, current.getAttempts());
    }

    @Test
    @DisplayName("the right code within the limit verifies once")
    void correctCodeVerifiesOnce() {
        assertTrue(service.verifyOtp(EMAIL, "482913"));
        assertFalse(service.verifyOtp(EMAIL, "482913"), "a used code cannot be replayed");
    }

    @Test
    @DisplayName("no log line ever contains an OTP - not the guess, not the expected code")
    void codesNeverReachTheLogs() {
        service.verifyOtp(EMAIL, "111111");
        service.verifyOtp(EMAIL, "482913");
        for (ILoggingEvent e : logs.list) {
            String line = e.getFormattedMessage();
            assertFalse(line.contains("482913") || line.contains("111111"), "OTP leaked to logs: " + line);
            assertFalse(line.contains(EMAIL), "full email in logs: " + line);
        }
    }

    @Test
    @DisplayName("a new code cannot be requested again within 30 seconds (no inbox flooding)")
    void sendIsThrottledPerEmail() {
        Otp justSent = new Otp();
        justSent.setCreatedAt(LocalDateTime.now().minusSeconds(5));
        when(repo.findTopByEmailOrderByCreatedAtDesc(EMAIL)).thenReturn(Optional.of(justSent));

        assertThrows(com.travelplatform.exception.TooManyRequestsException.class, () -> service.sendOtp(EMAIL));
    }

    @Test
    @DisplayName("background sends (driver login, reset by mobile) skip quietly during the cooldown instead of failing")
    void quietSendSkipsDuringCooldown() {
        Otp justSent = new Otp();
        justSent.setCreatedAt(LocalDateTime.now().minusSeconds(5));
        when(repo.findTopByEmailOrderByCreatedAtDesc(EMAIL)).thenReturn(Optional.of(justSent));

        assertFalse(service.sendOtpUnlessRecent(EMAIL), "nothing new is sent - the earlier code is still valid");
        org.mockito.Mockito.verify(repo, org.mockito.Mockito.never()).save(any(Otp.class));
    }

    @Test
    @DisplayName("the attempt counter commits on its own, so a failing caller cannot roll it back")
    void verifyRunsInItsOwnTransaction() throws Exception {
        Transactional tx = OtpService.class.getMethod("verifyOtp", String.class, String.class)
                .getAnnotation(Transactional.class);
        assertEquals(Propagation.REQUIRES_NEW, tx.propagation());
    }
}
