package com.travelplatform.service;

import com.travelplatform.config.JwtUtil;
import com.travelplatform.dto.DriverLoginRequest;
import com.travelplatform.dto.ForgotPasswordRequest;
import com.travelplatform.dto.OwnerLoginRequest;
import com.travelplatform.dto.UserLoginRequest;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.Owner;
import com.travelplatform.entity.User;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.OwnerRepository;
import com.travelplatform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Login and password-reset errors used to say "Owner not found with email: x" or "Driver not
 * found with mobile: y" for unknown accounts and something else for a wrong password, so anyone
 * could test which emails and phone numbers have accounts - the first step of a targeted attack.
 * Every failure now reads the same, whatever was wrong.
 */
class AuthEnumerationTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private OtpService otp;

    @BeforeEach
    void setUp() {
        otp = mock(OtpService.class);
    }

    /** Runs a failing call and returns "ExceptionType: message". */
    private static String failure(Runnable call) {
        RuntimeException e = assertThrows(RuntimeException.class, call::run);
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    @Test
    @DisplayName("user login: unknown email, wrong password and Google-only account fail identically")
    void userLogin() {
        UserRepository users = mock(UserRepository.class);
        UserAuthService service = new UserAuthService();
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        ReflectionTestUtils.setField(service, "jwtUtil", mock(JwtUtil.class));

        User withPassword = new User();
        withPassword.setPassword(encoder.encode("right-password"));
        User googleOnly = new User();
        when(users.findByEmail("known@x.com")).thenReturn(Optional.of(withPassword));
        when(users.findByEmail("google@x.com")).thenReturn(Optional.of(googleOnly));
        when(users.findByEmail("nobody@x.com")).thenReturn(Optional.empty());

        String unknown = failure(() -> service.login(login("nobody@x.com", "whatever")));
        assertEquals(unknown, failure(() -> service.login(login("known@x.com", "wrong"))));
        assertEquals(unknown, failure(() -> service.login(login("google@x.com", "whatever"))));
    }

    @Test
    @DisplayName("driver login: unknown mobile and wrong password fail identically")
    void driverLogin() {
        DriverRepository drivers = mock(DriverRepository.class);
        DriverAuthService service = new DriverAuthService();
        ReflectionTestUtils.setField(service, "driverRepository", drivers);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        Driver d = new Driver();
        d.setPassword(encoder.encode("right-password"));
        when(drivers.findByMobile("9000000001")).thenReturn(Optional.of(d));
        when(drivers.findByMobile("9000000002")).thenReturn(Optional.empty());

        DriverLoginRequest unknown = new DriverLoginRequest();
        unknown.setMobile("9000000002");
        unknown.setPassword("x");
        DriverLoginRequest wrong = new DriverLoginRequest();
        wrong.setMobile("9000000001");
        wrong.setPassword("x");

        assertEquals(failure(() -> service.login(unknown)), failure(() -> service.login(wrong)));
    }

    @Test
    @DisplayName("owner login: unknown email and wrong password fail identically")
    void ownerLogin() {
        OwnerRepository owners = mock(OwnerRepository.class);
        OwnerAuthService service = new OwnerAuthService();
        ReflectionTestUtils.setField(service, "ownerRepository", owners);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        Owner o = new Owner();
        o.setPassword(encoder.encode("right-password"));
        when(owners.findByEmail("owner@x.com")).thenReturn(Optional.of(o));
        when(owners.findByEmail("nobody@x.com")).thenReturn(Optional.empty());

        OwnerLoginRequest unknown = new OwnerLoginRequest();
        unknown.setEmail("nobody@x.com");
        unknown.setPassword("x");
        OwnerLoginRequest wrong = new OwnerLoginRequest();
        wrong.setEmail("owner@x.com");
        wrong.setPassword("x");

        assertEquals(failure(() -> service.login(unknown)), failure(() -> service.login(wrong)));
    }

    @Test
    @DisplayName("user password reset: an unknown email looks exactly like a wrong code")
    void userResetDoesNotRevealAccounts() {
        UserRepository users = mock(UserRepository.class);
        UserService service = new UserService(null, null, null, null, users, otp, encoder, null, null,
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
        when(users.findByEmail("known@x.com")).thenReturn(Optional.of(new User()));
        when(users.findByEmail("nobody@x.com")).thenReturn(Optional.empty());
        when(otp.verifyOtp(anyString(), anyString())).thenReturn(false);
        when(otp.verifyOtp("nobody@x.com", "123456")).thenReturn(true);

        String wrongCode = failure(() -> service.forgotPassword(reset("known@x.com", "000000")));
        assertEquals(wrongCode, failure(() -> service.forgotPassword(reset("nobody@x.com", "123456"))));
    }

    @Test
    @DisplayName("driver set-password by OTP only works on a first login, not as a back-door reset")
    void driverOtpPasswordOnlyOnFirstLogin() {
        DriverRepository drivers = mock(DriverRepository.class);
        DriverAuthService service = new DriverAuthService();
        ReflectionTestUtils.setField(service, "driverRepository", drivers);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        ReflectionTestUtils.setField(service, "otpService", otp);
        Driver established = new Driver();
        established.setFirstLogin(false);
        when(drivers.findByEmail("d@x.com")).thenReturn(Optional.of(established));
        when(otp.verifyOtp("d@x.com", "123456")).thenReturn(true);

        assertThrows(RuntimeException.class, () -> service.verifyOtpAndSetPassword("d@x.com", "123456", "NewPass#2026"));
        verify(drivers, never()).save(established);
    }

    private static UserLoginRequest login(String email, String password) {
        UserLoginRequest r = new UserLoginRequest();
        r.setLoginType(User.LoginType.EMAIL);
        r.setEmail(email);
        r.setPassword(password);
        return r;
    }

    private static ForgotPasswordRequest reset(String email, String code) {
        ForgotPasswordRequest r = new ForgotPasswordRequest();
        r.setEmail(email);
        r.setOtp(code);
        r.setNewPassword("NewPass#2026");
        return r;
    }

    @SuppressWarnings("unused")
    private static Map<String, Object> unused() {
        return Map.of();
    }
}
