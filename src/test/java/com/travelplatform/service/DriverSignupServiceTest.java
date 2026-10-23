package com.travelplatform.service;

import com.travelplatform.config.JwtUtil;
import com.travelplatform.dto.DriverLoginRequest;
import com.travelplatform.dto.DriverSelfSignupForm;
import com.travelplatform.entity.Driver;
import com.travelplatform.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DriverSignupServiceTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private DriverRepository drivers;
    private FileStorageService files;
    private OtpService otp;
    private JwtUtil jwt;
    private AfterCommitDispatcher dispatcher;
    private NotificationService notifications;
    private DriverSignupService service;

    private final MultipartFile img = new MockMultipartFile("f", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});

    @BeforeEach
    void setUp() {
        drivers = mock(DriverRepository.class);
        files = mock(FileStorageService.class);
        otp = mock(OtpService.class);
        jwt = mock(JwtUtil.class);
        dispatcher = mock(AfterCommitDispatcher.class);
        notifications = mock(NotificationService.class);
        // Run dispatched work straight away so the test can see the email go out.
        doAnswer(i -> {
            ((Runnable) i.getArgument(1)).run();
            return null;
        }).when(dispatcher).dispatch(anyString(), any(Runnable.class));
        when(files.storeFile(any(), anyString())).thenAnswer(i -> "uploads/drivers/" + i.getArgument(1) + "_x.jpg");
        when(drivers.save(any(Driver.class))).thenAnswer(i -> {
            Driver d = i.getArgument(0);
            d.setId(7L);
            return d;
        });

        service = new DriverSignupService();
        ReflectionTestUtils.setField(service, "driverRepository", drivers);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        ReflectionTestUtils.setField(service, "fileStorageService", files);
        ReflectionTestUtils.setField(service, "otpService", otp);
        ReflectionTestUtils.setField(service, "jwtUtil", jwt);
        ReflectionTestUtils.setField(service, "dispatcher", dispatcher);
        ReflectionTestUtils.setField(service, "notificationService", notifications);
    }

    private static DriverSelfSignupForm form() {
        DriverSelfSignupForm f = new DriverSelfSignupForm();
        f.setName("Ravi");
        f.setMobile("9000000001");
        f.setEmail("ravi@x.com");
        f.setPassword("longenough");
        f.setLicenseNumber("DL-1");
        f.setAadhaarNumber("1234");
        f.setVehicleType("AUTO");
        f.setVehicleNumber("ka 01 ab 1234");
        f.setVehicleModel("Bajaj");
        return f;
    }

    private Map<String, Object> signup(DriverSelfSignupForm f) {
        return service.signup(f, img, img, img, img);
    }

    @Test
    void signupCreatesIndependentDriverUnderReview() {
        Map<String, Object> result = signup(form());

        ArgumentCaptor<Driver> saved = ArgumentCaptor.forClass(Driver.class);
        verify(drivers).save(saved.capture());
        Driver d = saved.getValue();
        assertEquals(Driver.Fleet.INDEPENDENT, d.getFleet());
        assertEquals(Driver.Verification.UNDER_REVIEW, d.getVerificationStatus());
        assertEquals(Driver.Status.INACTIVE, d.getStatus());
        assertFalse(d.isServesTrip());
        assertFalse(d.isFirstLogin());
        assertFalse(d.isEmailVerified());
        assertEquals("KA 01 AB 1234", d.getVehicleNumber());
        assertEquals(com.travelplatform.entity.VehicleType.AUTO, d.getVehicleType());
        assertEquals("uploads/drivers/photo_x.jpg", d.getPhoto());
        assertEquals("uploads/drivers/license_x.jpg", d.getLicensePhoto());
        assertEquals("uploads/drivers/aadhaar_x.jpg", d.getAadhaarPhoto());
        assertEquals("uploads/drivers/rc_x.jpg", d.getRcPhoto());
        assertNotNull(d.getSubmittedAt());
        assertTrue(encoder.matches("longenough", d.getPassword()));
        verify(otp).sendOtp("ravi@x.com");
        verify(notifications).notifyDriverApplication(d, false);
        assertEquals("ravi@x.com", result.get("email"));
        assertEquals("We've emailed you a code to confirm your email.", result.get("message"));
    }

    @Test
    void duplicateRefusedBeforeAnyFileStored() {
        when(drivers.existsByMobile("9000000001")).thenReturn(true);
        assertEquals("Mobile number already registered", refusal());
        when(drivers.existsByMobile(anyString())).thenReturn(false);

        when(drivers.existsByEmail("ravi@x.com")).thenReturn(true);
        assertEquals("Email already registered", refusal());
        when(drivers.existsByEmail(anyString())).thenReturn(false);

        when(drivers.existsByLicenseNumber("DL-1")).thenReturn(true);
        assertEquals("License number already registered", refusal());
        when(drivers.existsByLicenseNumber(anyString())).thenReturn(false);

        when(drivers.existsByAadhaarNumber("1234")).thenReturn(true);
        assertEquals("Aadhaar number already registered", refusal());
        when(drivers.existsByAadhaarNumber(anyString())).thenReturn(false);

        when(drivers.existsByVehicleNumberIgnoreCase("KA 01 AB 1234")).thenReturn(true);
        assertEquals("Vehicle number already registered", refusal());

        verify(files, never()).storeFile(any(), anyString());
        verify(drivers, never()).save(any());
    }

    private String refusal() {
        return assertThrows(IllegalArgumentException.class, () -> signup(form())).getMessage();
    }

    @Test
    void missingDocumentRefused() {
        MultipartFile empty = new MockMultipartFile("f", new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> service.signup(form(), img, img, img, null));
        assertThrows(IllegalArgumentException.class, () -> service.signup(form(), img, img, empty, img));
        verify(files, never()).storeFile(any(), anyString());
    }

    @Test
    void numberPlateRequired() {
        DriverSelfSignupForm f = form();
        f.setVehicleNumber("  ");
        assertThrows(IllegalArgumentException.class, () -> signup(f));
        verify(files, never()).storeFile(any(), anyString());
    }

    @Test
    void shortPasswordRefused() {
        DriverSelfSignupForm f = form();
        f.setPassword("short");
        assertThrows(IllegalArgumentException.class, () -> signup(f));
        verify(drivers, never()).save(any());
    }

    private Driver pending() {
        Driver d = new Driver();
        d.setId(7L);
        d.setEmail("ravi@x.com");
        d.setFleet(Driver.Fleet.INDEPENDENT);
        d.setEmailVerified(false);
        when(drivers.findByEmail("ravi@x.com")).thenReturn(Optional.of(d));
        return d;
    }

    @Test
    void verifyEmailIssuesToken() {
        Driver d = pending();
        when(otp.verifyOtp("ravi@x.com", "123456")).thenReturn(true);
        when(jwt.generateToken(eq("7"), anyString(), anyLong())).thenReturn("tok");

        Map<String, Object> r = service.verifyEmail("ravi@x.com", "123456");

        assertEquals("tok", r.get("token"));
        assertEquals(false, r.get("firstLogin"));
        assertTrue(d.isEmailVerified());
        verify(drivers).save(d);
    }

    @Test
    void verifyEmailWrongCodeRefused() {
        Driver d = pending();
        when(otp.verifyOtp(anyString(), anyString())).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> service.verifyEmail("ravi@x.com", "000000"));
        assertFalse(d.isEmailVerified());
        verify(drivers, never()).save(any());
    }

    @Test
    void loginWithUnverifiedEmailResendsCode() {
        Driver d = pending();
        d.setPassword(encoder.encode("longenough"));
        d.setFirstLogin(false);
        when(drivers.findByMobile("9000000001")).thenReturn(Optional.of(d));
        DriverAuthService auth = new DriverAuthService();
        ReflectionTestUtils.setField(auth, "driverRepository", drivers);
        ReflectionTestUtils.setField(auth, "passwordEncoder", encoder);
        ReflectionTestUtils.setField(auth, "otpService", otp);
        ReflectionTestUtils.setField(auth, "jwtUtil", jwt);
        DriverLoginRequest req = new DriverLoginRequest();
        req.setMobile("9000000001");
        req.setPassword("longenough");

        Map<String, Object> r = auth.login(req);

        assertEquals(true, r.get("emailUnverified"));
        assertNull(r.get("token"));
        verify(otp).sendOtpUnlessRecent("ravi@x.com");
        verify(jwt, never()).generateToken(anyString(), anyString(), anyLong());
    }
}
