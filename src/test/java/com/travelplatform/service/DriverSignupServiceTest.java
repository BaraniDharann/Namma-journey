package com.travelplatform.service;

import com.travelplatform.config.JwtUtil;
import com.travelplatform.dto.DriverLoginRequest;
import com.travelplatform.dto.DriverSelfSignupForm;
import com.travelplatform.entity.Driver;
import com.travelplatform.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
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
        f.setAadhaarNumber("123412341234");
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
        verifyNoInteractions(notifications); // the owner hears only after the email is confirmed
        assertEquals("ravi@x.com", result.get("email"));
        assertEquals("We've emailed you a code to confirm your email.", result.get("message"));
    }

    @Test
    void duplicateRefusedBeforeAnyFileStored() {
        Driver verified = holder(50L, true, Driver.Fleet.INDEPENDENT);
        when(drivers.findByMobile("9000000001")).thenReturn(Optional.of(verified));
        assertEquals("Mobile number already registered", refusal());
        when(drivers.findByMobile(anyString())).thenReturn(Optional.empty());

        when(drivers.findByEmail("ravi@x.com")).thenReturn(Optional.of(verified));
        assertEquals("Email already registered", refusal());
        when(drivers.findByEmail(anyString())).thenReturn(Optional.empty());

        when(drivers.findByLicenseNumber("DL-1")).thenReturn(Optional.of(verified));
        assertEquals("License number already registered", refusal());
        when(drivers.findByLicenseNumber(anyString())).thenReturn(Optional.empty());

        when(drivers.findByAadhaarNumber("123412341234")).thenReturn(Optional.of(verified));
        assertEquals("Aadhaar number already registered", refusal());
        when(drivers.findByAadhaarNumber(anyString())).thenReturn(Optional.empty());

        when(drivers.findByVehicleKey("KA01AB1234")).thenReturn(Optional.of(verified));
        assertEquals("Vehicle number already registered", refusal());

        verify(files, never()).storeFile(any(), anyString());
        verify(drivers, never()).save(any());
    }

    private Driver holder(long id, boolean verified, Driver.Fleet fleet) {
        Driver d = new Driver();
        d.setId(id);
        d.setFleet(fleet);
        d.setEmailVerified(verified);
        d.setPhoto("old/photo_" + id);
        d.setLicensePhoto("old/license_" + id);
        d.setAadhaarPhoto("old/aadhaar_" + id);
        d.setRcPhoto("old/rc_" + id);
        return d;
    }

    @Test
    void ownerFleetDriverConflictAlwaysRefused() {
        when(drivers.findByMobile("9000000001")).thenReturn(Optional.of(holder(60L, false, Driver.Fleet.OWNER_FLEET)));
        assertEquals("Mobile number already registered", refusal());
        verify(drivers, never()).delete(any());
        verify(drivers, never()).save(any());
    }

    @Test
    void resignupReplacesUnverifiedConflictAndDeletesItsFilesAfterCommit() {
        Driver old = holder(40L, false, Driver.Fleet.INDEPENDENT);
        when(drivers.findByEmail("ravi@x.com")).thenReturn(Optional.of(old));
        when(drivers.findByMobile("9000000001")).thenReturn(Optional.of(old)); // same row twice: deleted once

        signup(form());

        InOrder order = inOrder(drivers);
        order.verify(drivers).delete(old);
        order.verify(drivers).flush();
        order.verify(drivers).save(any(Driver.class));
        verify(drivers, times(1)).delete(any());
        verify(files).delete("old/photo_40");
        verify(files).delete("old/license_40");
        verify(files).delete("old/aadhaar_40");
        verify(files).delete("old/rc_40");
        verify(otp).sendOtp("ravi@x.com");
    }

    @Test
    void failedResignupKeepsOldFilesAndRemovesNewOnes() {
        Driver old = holder(40L, false, Driver.Fleet.INDEPENDENT);
        when(drivers.findByEmail("ravi@x.com")).thenReturn(Optional.of(old));
        when(drivers.save(any(Driver.class))).thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup"));

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> signup(form()));

        verify(files, never()).delete("old/photo_40");
        verify(files).delete("uploads/drivers/photo_x.jpg");
    }

    @Test
    void mobileMustBeIndianNumber() {
        for (String bad : new String[]{"1234567890", "98765", "98765432101", "98765abcde"}) {
            DriverSelfSignupForm f = form();
            f.setMobile(bad);
            assertEquals("Invalid Indian mobile number",
                    assertThrows(IllegalArgumentException.class, () -> signup(f)).getMessage());
        }
        verify(files, never()).storeFile(any(), anyString());
    }

    @Test
    void aadhaarMustBeTwelveDigitsAndSpacesAreStripped() {
        DriverSelfSignupForm bad = form();
        bad.setAadhaarNumber("1234 5678");
        assertEquals("Invalid Aadhaar number",
                assertThrows(IllegalArgumentException.class, () -> signup(bad)).getMessage());

        DriverSelfSignupForm ok = form();
        ok.setAadhaarNumber("1234 5678 9012");
        signup(ok);
        ArgumentCaptor<Driver> saved = ArgumentCaptor.forClass(Driver.class);
        verify(drivers).save(saved.capture());
        assertEquals("123456789012", saved.getValue().getAadhaarNumber());
    }

    @Test
    void emailAndLicenceAreNormalised() {
        DriverSelfSignupForm f = form();
        f.setEmail("  Ravi@X.COM ");
        f.setLicenseNumber("  dl-1 ");
        signup(f);
        ArgumentCaptor<Driver> saved = ArgumentCaptor.forClass(Driver.class);
        verify(drivers).save(saved.capture());
        assertEquals("ravi@x.com", saved.getValue().getEmail());
        assertEquals("DL-1", saved.getValue().getLicenseNumber());
        verify(drivers).findByEmail("ravi@x.com");
        verify(drivers).findByLicenseNumber("DL-1");
        verify(otp).sendOtp("ravi@x.com");
    }

    @Test
    void purgeDeletesOnlyOldUnverifiedIndependentRowsAndTheirFiles() {
        Driver old = holder(41L, false, Driver.Fleet.INDEPENDENT);
        when(drivers.findByFleetAndEmailVerifiedFalseAndSubmittedAtBefore(eq(Driver.Fleet.INDEPENDENT), any()))
                .thenReturn(List.of(old));
        ArgumentCaptor<java.time.LocalDateTime> cutoff = ArgumentCaptor.forClass(java.time.LocalDateTime.class);

        assertEquals(1, service.purgeUnverified());

        verify(drivers).findByFleetAndEmailVerifiedFalseAndSubmittedAtBefore(eq(Driver.Fleet.INDEPENDENT), cutoff.capture());
        assertTrue(cutoff.getValue().isBefore(java.time.LocalDateTime.now().minusHours(47)));
        assertTrue(cutoff.getValue().isAfter(java.time.LocalDateTime.now().minusHours(49)));
        verify(drivers).delete(old);
        verify(files).delete("old/photo_41");
        verify(files).delete("old/rc_41");
    }

    @Test
    void purgeSkipsRowsThatTurnedVerifiedOrOwnerFleet() {
        when(drivers.findByFleetAndEmailVerifiedFalseAndSubmittedAtBefore(any(), any()))
                .thenReturn(List.of(holder(42L, true, Driver.Fleet.INDEPENDENT), holder(43L, false, Driver.Fleet.OWNER_FLEET)));
        assertEquals(0, service.purgeUnverified());
        verify(drivers, never()).delete(any());
        verify(files, never()).delete(anyString());
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
        verify(notifications).notifyDriverApplication(d, false);
    }

    @Test
    void verifyEmailLowercasesTheAddress() {
        Driver d = pending();
        when(otp.verifyOtp("ravi@x.com", "123456")).thenReturn(true);
        when(jwt.generateToken(anyString(), anyString(), anyLong())).thenReturn("tok");
        service.verifyEmail(" Ravi@X.com ", "123456");
        assertTrue(d.isEmailVerified());
    }

    @Test
    void verifyEmailRefusedWhenAlreadyVerified() {
        Driver d = pending();
        d.setEmailVerified(true);
        when(otp.verifyOtp(anyString(), anyString())).thenReturn(true);

        assertThrows(BadCredentialsException.class, () -> service.verifyEmail("ravi@x.com", "123456"));
        verify(jwt, never()).generateToken(anyString(), anyString(), anyLong());
        verifyNoInteractions(notifications);
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

    @Test
    void thirdFileRejectedDeletesEarlierFilesAndSavesNothing() {
        when(files.storeFile(any(), eq("aadhaar"))).thenThrow(new IllegalArgumentException("Only images are allowed"));

        assertThrows(IllegalArgumentException.class, () -> signup(form()));

        verify(files).delete("uploads/drivers/photo_x.jpg");
        verify(files).delete("uploads/drivers/license_x.jpg");
        verify(files, times(2)).delete(anyString());
        verify(drivers, never()).save(any());
    }

    @Test
    void saveFailureDeletesAllFourFilesAndPropagates() {
        when(drivers.save(any(Driver.class))).thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup"));
        doThrow(new RuntimeException("disk")).when(files).delete("uploads/drivers/photo_x.jpg");

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> signup(form()));

        verify(files).delete("uploads/drivers/photo_x.jpg");
        verify(files).delete("uploads/drivers/license_x.jpg");
        verify(files).delete("uploads/drivers/aadhaar_x.jpg");
        verify(files).delete("uploads/drivers/rc_x.jpg");
        verify(otp, never()).sendOtp(anyString());
    }
}
