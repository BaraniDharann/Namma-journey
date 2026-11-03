package com.travelplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.travelplatform.dto.AdminOwnerDetail;
import com.travelplatform.dto.AdminOwnerSummary;
import com.travelplatform.dto.OwnerCheckRequest;
import com.travelplatform.entity.City;
import com.travelplatform.entity.Owner;
import com.travelplatform.entity.OwnerAadhaarQr;
import com.travelplatform.entity.OwnerStatus;
import com.travelplatform.entity.OwnerVerificationCheck;
import com.travelplatform.repository.OwnerAadhaarQrRepository;
import com.travelplatform.repository.OwnerRepository;
import com.travelplatform.repository.OwnerVerificationCheckRepository;
import com.travelplatform.storage.SignedUploadUrls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OwnerReviewServiceTest {

    OwnerRepository owners;
    OwnerVerificationCheckRepository checks;
    OwnerAadhaarQrRepository qrs;
    SignedUploadUrls signed;
    OwnerReviewService service;
    final List<OwnerVerificationCheck> savedChecks = new ArrayList<>();

    @BeforeEach
    void setUp() {
        owners = mock(OwnerRepository.class);
        checks = mock(OwnerVerificationCheckRepository.class);
        qrs = mock(OwnerAadhaarQrRepository.class);
        signed = mock(SignedUploadUrls.class);
        when(signed.signIfSensitive(anyString())).thenAnswer(i -> i.getArgument(0) + "?exp=1&sig=abc");
        when(checks.findByOwnerId(any())).thenReturn(savedChecks);
        when(checks.save(any(OwnerVerificationCheck.class))).thenAnswer(i -> {
            OwnerVerificationCheck c = i.getArgument(0);
            savedChecks.removeIf(x -> x.getCheckType() == c.getCheckType());
            savedChecks.add(c);
            return c;
        });
        when(qrs.findById(any())).thenReturn(Optional.empty());

        service = new OwnerReviewService();
        ReflectionTestUtils.setField(service, "ownerRepository", owners);
        ReflectionTestUtils.setField(service, "checkRepository", checks);
        ReflectionTestUtils.setField(service, "aadhaarQrRepository", qrs);
        ReflectionTestUtils.setField(service, "signedUploadUrls", signed);
    }

    static Owner pending(long id, LocalDateTime submittedAt) {
        Owner o = new Owner();
        o.setId(id);
        o.setStatus(OwnerStatus.PENDING_REVIEW);
        o.setEmail("o" + id + "@x.com");
        o.setTravelsName("Travels " + id);
        o.setSubmittedAt(submittedAt);
        City c = new City();
        c.setId(1L);
        c.setName("Bengaluru");
        o.setCities(Set.of(c));
        o.setPanNumber("ABCDE1234F");
        o.setBankAccountNumber("12345678906789");
        o.setAadhaarLast4("0124");
        o.setUpiId("ravi@okaxis");
        o.setPanDocPath("/uploads/owners/pan_x.jpg");
        o.setAadhaarDocPath("/uploads/owners/aadhaar_x.jpg");
        o.setRegistrationDocPath("/uploads/owners/registration_x.jpg");
        return o;
    }

    @Test
    @DisplayName("pending applications come oldest first, and those past 48 hours are overdue")
    void listSortsOldestFirstAndFlagsOverdue() {
        LocalDateTime now = LocalDateTime.now();
        when(owners.findByStatusOrderBySubmittedAtAsc(OwnerStatus.PENDING_REVIEW))
                .thenReturn(List.of(pending(1, now.minusHours(49)), pending(2, now.minusHours(10))));

        List<AdminOwnerSummary> list = service.list(OwnerStatus.PENDING_REVIEW);

        assertEquals(1L, list.get(0).id());
        assertTrue(list.get(0).overdue());
        assertEquals(49, list.get(0).hoursWaiting());
        assertFalse(list.get(1).overdue());
        assertEquals(List.of("Bengaluru"), list.get(0).cities());
    }

    @Test
    @DisplayName("the admin sees masked PAN and account, and documents only through signed links")
    void detailNeverExposesFullSecrets() throws Exception {
        when(owners.findById(1L)).thenReturn(Optional.of(pending(1, LocalDateTime.now())));
        OwnerAadhaarQr qr = new OwnerAadhaarQr();
        qr.setOwnerId(1L);
        qr.setStatus("VALID");
        qr.setName("Ravi Kumar");
        qr.setLast4("0124");
        qr.setPhotoJpeg(new byte[]{(byte) 0xFF, (byte) 0xD8});
        when(qrs.findById(1L)).thenReturn(Optional.of(qr));

        AdminOwnerDetail d = service.detail(1L);
        String json = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(d);

        assertFalse(json.contains("ABCDE1234F"), json);
        assertFalse(json.contains("12345678906789"), json);
        assertEquals("XXXXX1234F", d.panMasked());
        assertEquals("XXXXXX6789", d.bankAccountMasked());
        assertTrue(d.documents().pan().contains("sig="));
        assertTrue(d.documents().aadhaar().contains("sig="));
        assertTrue(d.documents().registration().contains("sig="));
        assertEquals("VALID", d.aadhaarQr().status());
        assertTrue(d.aadhaarQr().last4MatchesTyped());
        assertTrue(d.aadhaarQr().photoDataUrl().startsWith("data:image/jpeg;base64,"));
        assertFalse(d.canApprove());
    }

    @Test
    @DisplayName("a check marked FAILED needs a note saying why")
    void failedCheckNeedsNote() {
        when(owners.findById(1L)).thenReturn(Optional.of(pending(1, LocalDateTime.now())));
        assertThrows(IllegalArgumentException.class, () -> service.recordCheck(1L, OwnerVerificationCheck.Type.PAN,
                new OwnerCheckRequest(OwnerVerificationCheck.Result.FAILED, "  "), 5L));
        verify(checks, never()).save(any());
    }

    @Test
    @DisplayName("every check records which admin made it and when")
    void recordCheckIsAudited() {
        when(owners.findById(1L)).thenReturn(Optional.of(pending(1, LocalDateTime.now())));

        service.recordCheck(1L, OwnerVerificationCheck.Type.BANK,
                new OwnerCheckRequest(OwnerVerificationCheck.Result.VERIFIED, null), 5L);

        ArgumentCaptor<OwnerVerificationCheck> c = ArgumentCaptor.forClass(OwnerVerificationCheck.class);
        verify(checks).save(c.capture());
        assertEquals(1L, c.getValue().getOwnerId());
        assertEquals(OwnerVerificationCheck.Type.BANK, c.getValue().getCheckType());
        assertEquals(5L, c.getValue().getCheckedByAdminId());
        assertNotNull(c.getValue().getCheckedAt());
    }

    @Test
    @DisplayName("checks can only be recorded on a pending application")
    void checksOnlyWhilePending() {
        Owner approved = pending(1, LocalDateTime.now());
        approved.setStatus(OwnerStatus.APPROVED);
        when(owners.findById(1L)).thenReturn(Optional.of(approved));
        assertThrows(IllegalStateException.class, () -> service.recordCheck(1L, OwnerVerificationCheck.Type.PAN,
                new OwnerCheckRequest(OwnerVerificationCheck.Result.VERIFIED, null), 5L));
    }

    // ---- approve / reject / resend ----------------------------------------------------------

    final org.springframework.security.crypto.password.PasswordEncoder encoder =
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4);
    EmailService emails;

    void wireDecisions() {
        emails = mock(EmailService.class);
        when(emails.sendOwnerCredentialsNow(anyString(), any(), anyString(), anyString())).thenReturn(true);
        when(owners.save(any(Owner.class))).thenAnswer(i -> i.getArgument(0));
        when(owners.nextOwnerCode()).thenReturn(7L);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        ReflectionTestUtils.setField(service, "emailService", emails);
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "http://localhost:5173");
    }

    Owner withAllChecks(Owner o, OwnerVerificationCheck.Result bankResult) {
        when(owners.findById(o.getId())).thenReturn(Optional.of(o));
        for (OwnerVerificationCheck.Type t : OwnerVerificationCheck.Type.values()) {
            OwnerVerificationCheck c = new OwnerVerificationCheck();
            c.setOwnerId(o.getId());
            c.setCheckType(t);
            c.setResult(t == OwnerVerificationCheck.Type.BANK ? bankResult : OwnerVerificationCheck.Result.VERIFIED);
            savedChecks.add(c);
        }
        return o;
    }

    @Test
    @DisplayName("approve is refused, naming what is missing, until all four checks are VERIFIED")
    void approveNeedsAllChecksVerified() {
        wireDecisions();
        withAllChecks(pending(1, LocalDateTime.now()), OwnerVerificationCheck.Result.FAILED);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.approve(1L, 5L));
        assertTrue(e.getMessage().contains("BANK"), e.getMessage());
        verify(owners, never()).save(any());
        verify(emails, never()).sendOwnerCredentialsNow(anyString(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("approve assigns the next Owner ID and a one-time password, and emails both")
    void approveAssignsCodeAndOneTimePassword() {
        wireDecisions();
        Owner o = withAllChecks(pending(1, LocalDateTime.now()), OwnerVerificationCheck.Result.VERIFIED);

        OwnerReviewService.ApprovalResult r = service.approve(1L, 5L);

        assertEquals("NJO-0007", r.ownerCode());
        assertTrue(r.credentialsEmailed());
        assertEquals(OwnerStatus.APPROVED, o.getStatus());
        assertEquals("NJO-0007", o.getOwnerCode());
        assertTrue(o.isMustChangePassword());
        assertEquals(5L, o.getReviewedByAdminId());
        assertNotNull(o.getReviewedAt());
        ArgumentCaptor<String> password = ArgumentCaptor.forClass(String.class);
        verify(emails).sendOwnerCredentialsNow(eq("o1@x.com"), eq("Travels 1"), eq("NJO-0007"), password.capture());
        assertEquals(12, password.getValue().length());
        assertTrue(encoder.matches(password.getValue(), o.getPassword()));
    }

    @Test
    @DisplayName("an application already decided cannot be approved again")
    void approveIsOnlyAllowedFromPending() {
        wireDecisions();
        Owner o = withAllChecks(pending(1, LocalDateTime.now()), OwnerVerificationCheck.Result.VERIFIED);
        o.setStatus(OwnerStatus.APPROVED);
        assertThrows(IllegalStateException.class, () -> service.approve(1L, 5L));
        o.setStatus(OwnerStatus.REJECTED);
        assertThrows(IllegalStateException.class, () -> service.approve(1L, 5L));
        verify(owners, never()).nextOwnerCode();
    }

    @Test
    @DisplayName("the approval is written (and its version checked) before the password is emailed")
    void approveFlushesBeforeEmailing() {
        wireDecisions();
        withAllChecks(pending(1, LocalDateTime.now()), OwnerVerificationCheck.Result.VERIFIED);

        service.approve(1L, 5L);

        org.mockito.InOrder order = inOrder(owners, emails);
        order.verify(owners).saveAndFlush(any(Owner.class));
        order.verify(emails).sendOwnerCredentialsNow(anyString(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("if the credentials email fails the approval stands, and the admin is told")
    void approveSurvivesEmailFailure() {
        wireDecisions();
        when(emails.sendOwnerCredentialsNow(anyString(), any(), anyString(), anyString())).thenReturn(false);
        Owner o = withAllChecks(pending(1, LocalDateTime.now()), OwnerVerificationCheck.Result.VERIFIED);

        OwnerReviewService.ApprovalResult r = service.approve(1L, 5L);

        assertFalse(r.credentialsEmailed());
        assertEquals(OwnerStatus.APPROVED, o.getStatus());
    }

    @Test
    @DisplayName("resend makes a new password - the old one is never stored to send again")
    void resendGeneratesANewPassword() {
        wireDecisions();
        Owner o = withAllChecks(pending(1, LocalDateTime.now()), OwnerVerificationCheck.Result.VERIFIED);
        service.approve(1L, 5L);
        String firstHash = o.getPassword();

        OwnerReviewService.ApprovalResult r = service.resendCredentials(1L, 5L);

        assertNotEquals(firstHash, o.getPassword());
        assertTrue(r.credentialsEmailed());
        verify(emails, times(2)).sendOwnerCredentialsNow(anyString(), any(), eq("NJO-0007"), anyString());

        o.setMustChangePassword(false);
        assertThrows(IllegalStateException.class, () -> service.resendCredentials(1L, 5L));
    }

    @Test
    @DisplayName("reject keeps the reason and emails a 14-day single-use resubmit link")
    void rejectStoresReasonAndResubmitTokenHash() {
        wireDecisions();
        Owner o = pending(1, LocalDateTime.now());
        when(owners.findById(1L)).thenReturn(Optional.of(o));

        service.reject(1L, "PAN name does not match", 5L);

        assertEquals(OwnerStatus.REJECTED, o.getStatus());
        assertEquals("PAN name does not match", o.getRejectionReason());
        assertEquals(5L, o.getReviewedByAdminId());
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(emails).sendOwnerRejected(eq("o1@x.com"), eq("Travels 1"), eq("PAN name does not match"), url.capture());
        assertTrue(url.getValue().startsWith("http://localhost:5173/owner/signup?resubmit="), url.getValue());
        String token = url.getValue().substring(url.getValue().indexOf('=') + 1);
        assertEquals(OwnerCredentials.hash(token), o.getResubmitTokenHash());
        assertTrue(o.getResubmitTokenExpiresAt().isAfter(LocalDateTime.now().plusDays(13)));
        assertTrue(o.getResubmitTokenExpiresAt().isBefore(LocalDateTime.now().plusDays(15)));

        assertThrows(IllegalArgumentException.class, () -> service.reject(1L, " ", 5L));
    }

    @Test
    @DisplayName("generated passwords avoid look-alike characters; tokens are URL-safe")
    void credentialsAlphabet() {
        for (int i = 0; i < 50; i++) {
            assertTrue(OwnerCredentials.newPassword().matches("^[A-HJ-NP-Za-km-z2-9]{12}$"));
            assertTrue(OwnerCredentials.newResubmitToken().matches("^[A-Za-z0-9_-]{43}$"));
        }
    }

    @Test
    @DisplayName("approval unlocks only when all four checks are VERIFIED")
    void canApproveOnlyWhenAllFourVerified() {
        when(owners.findById(1L)).thenReturn(Optional.of(pending(1, LocalDateTime.now())));
        for (OwnerVerificationCheck.Type t : List.of(OwnerVerificationCheck.Type.PAN,
                OwnerVerificationCheck.Type.AADHAAR, OwnerVerificationCheck.Type.REGISTRATION_DOC)) {
            service.recordCheck(1L, t, new OwnerCheckRequest(OwnerVerificationCheck.Result.VERIFIED, null), 5L);
        }
        service.recordCheck(1L, OwnerVerificationCheck.Type.BANK,
                new OwnerCheckRequest(OwnerVerificationCheck.Result.FAILED, "name differs"), 5L);
        assertFalse(service.detail(1L).canApprove());

        service.recordCheck(1L, OwnerVerificationCheck.Type.BANK,
                new OwnerCheckRequest(OwnerVerificationCheck.Result.VERIFIED, null), 5L);
        assertTrue(service.detail(1L).canApprove());
    }
}
