package com.travelplatform.security;

import com.travelplatform.config.JwtAuthenticationFilter;
import com.travelplatform.config.JwtUtil;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.User;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.OwnerRepository;
import com.travelplatform.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A JWT used to stay valid for its whole 24 hours no matter what: after a password reset (the
 * moment someone recovers a hijacked account), after logout, after the owner deleted a driver.
 * Tokens now carry the account's token version; resetting or logging out bumps it, and a deleted
 * account has no version at all.
 */
class TokenRevocationTest {

    private JwtUtil jwt;
    private UserRepository users;
    private DriverRepository drivers;
    private TokenVersionService versions;

    private final UUID userId = UUID.randomUUID();
    private User user;

    @BeforeEach
    void setUp() {
        jwt = new JwtUtil();
        ReflectionTestUtils.setField(jwt, "secret", "test-only-secret-0123456789abcdef0123456789");
        ReflectionTestUtils.setField(jwt, "expiration", 3_600_000L);
        ReflectionTestUtils.invokeMethod(jwt, "initSigningKey");

        users = mock(UserRepository.class);
        drivers = mock(DriverRepository.class);
        versions = new TokenVersionService(users, drivers, mock(OwnerRepository.class));

        user = new User();
        user.setId(userId);
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("tokens carry the account's token version")
    void tokenCarriesVersion() {
        String token = jwt.generateToken(userId.toString(), "ROLE_USER", 3L);
        assertEquals(3L, jwt.extractTokenVersion(token));
    }

    @Test
    @DisplayName("revoking bumps the version, so every token issued before is no longer current")
    void revokeInvalidatesOlderTokens() {
        assertTrue(versions.isCurrent("ROLE_USER", userId.toString(), 0L));

        versions.revokeUser(user);

        assertEquals(1L, user.getTokenVersion());
        assertFalse(versions.isCurrent("ROLE_USER", userId.toString(), 0L), "the cached old version must be dropped");
        assertTrue(versions.isCurrent("ROLE_USER", userId.toString(), 1L));
    }

    @Test
    @DisplayName("a deleted driver's token is never current again")
    void deletedAccountIsNeverCurrent() {
        when(drivers.findById(7L)).thenReturn(Optional.empty());
        assertFalse(versions.isCurrent("ROLE_DRIVER", "7", 0L));

        Driver d = new Driver();
        d.setId(8L);
        when(drivers.findById(8L)).thenReturn(Optional.of(d));
        assertTrue(versions.isCurrent("ROLE_DRIVER", "8", 0L));
    }

    @Test
    @DisplayName("the request filter refuses a validly signed token whose version is stale")
    void filterRejectsStaleTokens() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "jwtUtil", jwt);
        ReflectionTestUtils.setField(filter, "tokenVersionService", versions);

        String stale = jwt.generateToken(userId.toString(), "ROLE_USER", 0L);
        versions.revokeUser(user);
        filter.doFilter(request(stale), new MockHttpServletResponse(), new MockFilterChain());
        assertNull(SecurityContextHolder.getContext().getAuthentication(), "stale token must not authenticate");

        String fresh = jwt.generateToken(userId.toString(), "ROLE_USER", 1L);
        filter.doFilter(request(fresh), new MockHttpServletResponse(), new MockFilterChain());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("a password reset logs out every existing session of that account")
    void passwordResetRevokesTokens() {
        com.travelplatform.service.OtpService otp = mock(com.travelplatform.service.OtpService.class);
        when(otp.verifyOtp("kavya@x.com", "123456")).thenReturn(true);
        when(users.findByEmail("kavya@x.com")).thenReturn(Optional.of(user));
        com.travelplatform.service.UserService service = new com.travelplatform.service.UserService(
                null, null, null, null, users, otp,
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4), null, null,
                new org.springframework.transaction.support.TransactionTemplate(
                        mock(org.springframework.transaction.PlatformTransactionManager.class)));
        ReflectionTestUtils.setField(service, "tokenVersionService", versions);

        com.travelplatform.dto.ForgotPasswordRequest reset = new com.travelplatform.dto.ForgotPasswordRequest();
        reset.setEmail("kavya@x.com");
        reset.setOtp("123456");
        reset.setNewPassword("NewPass#2026");
        service.forgotPassword(reset);

        assertFalse(versions.isCurrent("ROLE_USER", userId.toString(), 0L), "the pre-reset token must be dead");
    }

    private static MockHttpServletRequest request(String token) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/api/user/x/bookings");
        r.addHeader("Authorization", "Bearer " + token);
        return r;
    }
}
