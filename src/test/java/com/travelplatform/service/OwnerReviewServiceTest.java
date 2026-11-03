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
