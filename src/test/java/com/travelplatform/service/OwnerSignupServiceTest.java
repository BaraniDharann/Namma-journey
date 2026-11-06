package com.travelplatform.service;

import com.travelplatform.dto.OwnerSignupForm;
import com.travelplatform.entity.City;
import com.travelplatform.entity.Owner;
import com.travelplatform.entity.OwnerAadhaarQr;
import com.travelplatform.entity.OwnerStatus;
import com.travelplatform.repository.CityRepository;
import com.travelplatform.repository.OwnerAadhaarQrRepository;
import com.travelplatform.repository.OwnerRepository;
import com.travelplatform.security.FieldEncryption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OwnerSignupServiceTest {

    static final byte[] AADHAAR_BYTES = {(byte) 0xFF, (byte) 0xD8, 9, 9, 9};

    OwnerRepository owners;
    CityRepository cities;
    OwnerAadhaarQrRepository qrs;
    FileStorageService files;
    AadhaarQrVerifier verifier;
    EmailService emails;
    OwnerSignupService service;

    static final AadhaarSecureQr.Result VALID_QR = new AadhaarSecureQr.Result(AadhaarSecureQr.Status.VALID,
            "V2", "0124", "Ravi Kumar", "15-08-1985", "M", null, null, null, null, null, null, null,
            null, "Karnataka", "560041", null, null, null);

    @BeforeEach
    void setUp() {
        FieldEncryption.configure("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        owners = mock(OwnerRepository.class);
        cities = mock(CityRepository.class);
        qrs = mock(OwnerAadhaarQrRepository.class);
        files = mock(FileStorageService.class);
        verifier = mock(AadhaarQrVerifier.class);
        emails = mock(EmailService.class);

        City blr = new City();
        blr.setId(1L);
        blr.setName("Bengaluru");
        blr.setActive(true);
        when(cities.findAllById(List.of(1L))).thenReturn(List.of(blr));
        when(cities.findAllById(List.of(99L))).thenReturn(List.of());
        when(owners.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(owners.findByPanHash(anyString())).thenReturn(Optional.empty());
        when(owners.save(any(Owner.class))).thenAnswer(i -> {
            Owner o = i.getArgument(0);
            if (o.getId() == null) o.setId(42L);
            return o;
        });
        when(files.storeImage(any(), eq("owners"), anyString()))
                .thenAnswer(i -> "/uploads/owners/" + i.getArgument(2) + "_x.jpg");
        when(verifier.verifyImage(any())).thenReturn(VALID_QR);

        service = new OwnerSignupService();
        ReflectionTestUtils.setField(service, "ownerRepository", owners);
        ReflectionTestUtils.setField(service, "cityRepository", cities);
        ReflectionTestUtils.setField(service, "aadhaarQrRepository", qrs);
        ReflectionTestUtils.setField(service, "fileStorageService", files);
        ReflectionTestUtils.setField(service, "aadhaarQrVerifier", verifier);
        ReflectionTestUtils.setField(service, "emailService", emails);
    }

    static MultipartFile img(String name, byte[] bytes) {
        return new MockMultipartFile(name, name + ".jpg", "image/jpeg", bytes);
    }

    static OwnerSignupForm form() {
        OwnerSignupForm f = new OwnerSignupForm();
        f.setTravelsName("Ravi Travels");
        f.setContactName("Ravi Kumar");
        f.setEmail("Ravi@Example.com");
        f.setPhone("9876543210");
        f.setCityIds(List.of(1L));
        f.setPanNumber("abcde1234f");
        f.setAadhaarNumber("2345 6789 0124");
        f.setBankAccountHolder("Ravi Kumar");
        f.setBankAccountNumber("12345678906789");
        f.setBankIfsc("sbin0001234");
        f.setBankName("SBI");
        f.setUpiId("ravi@okaxis");
        f.setAadhaarConsent(true);
        f.setRegistrationDoc(img("registrationDoc", new byte[]{1}));
        f.setPanDoc(img("panDoc", new byte[]{2}));
        f.setAadhaarDoc(img("aadhaarDoc", AADHAAR_BYTES));
        return f;
    }

    @Test
    @DisplayName("signup saves a pending owner: no password, no Owner ID, encrypted KYC, QR result kept")
    void signupCreatesPendingOwnerWithEncryptedKycAndQrResult() {
        OwnerSignupService.SignupResult r = service.signup(form());

        ArgumentCaptor<Owner> saved = ArgumentCaptor.forClass(Owner.class);
        verify(owners).save(saved.capture());
        Owner o = saved.getValue();
        assertEquals(OwnerStatus.PENDING_REVIEW, o.getStatus());
        assertNull(o.getPassword());
        assertNull(o.getOwnerCode());
        assertEquals("ravi@example.com", o.getEmail());
        assertEquals("ABCDE1234F", o.getPanNumber());
        assertEquals(OwnerApplicationChecks.panHash("ABCDE1234F"), o.getPanHash());
        assertEquals("0124", o.getAadhaarLast4());
        assertEquals("SBIN0001234", o.getBankIfsc());
        assertNotNull(o.getAadhaarConsentAt());
        assertNotNull(o.getSubmittedAt());
        assertEquals(1, o.getCities().size());
        assertEquals("/uploads/owners/aadhaar_x.jpg", o.getAadhaarDocPath());
        assertEquals("/uploads/owners/pan_x.jpg", o.getPanDocPath());
        assertEquals("/uploads/owners/registration_x.jpg", o.getRegistrationDocPath());

        ArgumentCaptor<OwnerAadhaarQr> qr = ArgumentCaptor.forClass(OwnerAadhaarQr.class);
        verify(qrs).save(qr.capture());
        assertEquals(42L, qr.getValue().getOwnerId());
        assertEquals("VALID", qr.getValue().getStatus());
        assertEquals("Ravi Kumar", qr.getValue().getName());
        assertEquals("0124", qr.getValue().getLast4());

        assertEquals(42L, r.ownerId());
        assertEquals(o.getSubmittedAt().plus(Duration.ofHours(48)), r.reviewBy());
    }

    @Test
    @DisplayName("the QR is read from the bytes as uploaded - before compression could blur it")
    void aadhaarQrRunsOnTheOriginalUploadBytes() {
        service.signup(form());
        verify(verifier).verifyImage(AADHAAR_BYTES);
    }

    @Test
    @DisplayName("the admin is alerted and the owner told it was received")
    void queuesAdminAndOwnerEmails() {
        service.signup(form());
        verify(emails).sendAdminNewOwnerApplication(any(Owner.class), any(LocalDateTime.class));
        verify(emails).sendOwnerApplicationReceived(eq("ravi@example.com"), eq("Ravi Travels"), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("an email or PAN already registered is refused, however it is typed")
    void duplicateEmailOrPanIsRefusedIgnoringCaseAndSpaces() {
        Owner other = new Owner();
        other.setId(7L);
        other.setStatus(OwnerStatus.APPROVED);
        when(owners.findByEmailIgnoreCase("ravi@example.com")).thenReturn(Optional.of(other));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.signup(form()));
        assertEquals("This email is already registered", e.getMessage());

        when(owners.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(owners.findByPanHash(OwnerApplicationChecks.panHash("ABCDE1234F"))).thenReturn(Optional.of(other));
        OwnerSignupForm f = form();
        f.setPanNumber(" ABCDE 1234F ");
        e = assertThrows(IllegalArgumentException.class, () -> service.signup(f));
        assertEquals("This PAN is already registered", e.getMessage());

        verify(owners, never()).save(any());
    }

    @Test
    @DisplayName("an owner can serve a state, a city or all of India with no City-ride zones chosen")
    void serviceAreasWithoutZones() {
        OwnerSignupForm f = form();
        f.setCityIds(null);
        f.setServiceAreas(List.of("STATE:tamil nadu", "CITY:Tamil Nadu:Madurai", "CITY:Kerala:Kochi", "CITY::Ooty"));
        service.signup(f);

        ArgumentCaptor<Owner> saved = ArgumentCaptor.forClass(Owner.class);
        verify(owners).save(saved.capture());
        Owner o = saved.getValue();
        assertTrue(o.getCities().isEmpty());
        // Madurai is inside all of Tamil Nadu, so it is not kept twice.
        assertEquals(java.util.Set.of("STATE:Tamil Nadu", "CITY:Kerala:Kochi", "CITY::Ooty"), o.getServiceAreas());
        assertEquals(List.of("All of Tamil Nadu", "Kochi, Kerala", "Ooty"), ServiceAreas.describe(o));

        assertRefused(x -> { x.setCityIds(null); x.setServiceAreas(List.of("STATE:Atlantis")); }, "state");
        assertRefused(x -> { x.setCityIds(null); x.setServiceAreas(List.of()); }, "serve");
    }

    @Test
    @DisplayName("bad numbers, unknown cities, missing consent or documents are refused before anything is stored")
    void invalidNumbersAreRefusedBeforeAnythingIsStored() {
        assertRefused(f -> f.setPanNumber("ABCD1234F"), "PAN");
        assertRefused(f -> f.setBankIfsc("SBIN1001234"), "IFSC");
        assertRefused(f -> f.setAadhaarNumber("234567890123"), "Aadhaar");
        assertRefused(f -> f.setCityIds(List.of(99L)), "city");
        assertRefused(f -> f.setCityIds(List.of()), "city");
        assertRefused(f -> f.setPhone("12345"), "phone");
        assertRefused(f -> f.setUpiId("not-a-upi"), "UPI");
        assertRefused(f -> f.setBankAccountNumber("12ab"), "account");
        assertRefused(f -> f.setAadhaarConsent(false), "consent");
        assertRefused(f -> f.setPanDoc(null), "PAN card");
        assertRefused(f -> f.setAadhaarDoc(img("aadhaarDoc", new byte[5 * 1024 * 1024 + 1])), "5 MB");

        verify(files, never()).storeImage(any(), anyString(), anyString());
        verify(verifier, never()).verifyImage(any());
        verify(owners, never()).save(any());
    }

    private void assertRefused(java.util.function.Consumer<OwnerSignupForm> breakIt, String mentions) {
        OwnerSignupForm f = form();
        breakIt.accept(f);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.signup(f));
        assertTrue(e.getMessage().contains(mentions), "message should mention " + mentions + ": " + e.getMessage());
    }

    // ---- resubmit after rejection ----------------------------------------------------------

    static final String TOKEN = "tok-123";

    Owner rejected(LocalDateTime tokenExpires) {
        com.travelplatform.repository.OwnerVerificationCheckRepository checks =
                mock(com.travelplatform.repository.OwnerVerificationCheckRepository.class);
        ReflectionTestUtils.setField(service, "checkRepository", checks);
        Owner o = new Owner();
        o.setId(42L);
        o.setStatus(OwnerStatus.REJECTED);
        o.setEmail("ravi@example.com");
        o.setTravelsName("Ravi Travels");
        o.setContactName("Ravi Kumar");
        o.setPhone("9876543210");
        City c = new City();
        c.setId(1L);
        o.setCities(new java.util.HashSet<>(List.of(c)));
        o.setPanNumber("ABCDE1234F");
        o.setPanHash(OwnerApplicationChecks.panHash("ABCDE1234F"));
        o.setBankAccountHolder("Ravi Kumar");
        o.setBankAccountNumber("12345678906789");
        o.setBankIfsc("SBIN0001234");
        o.setBankName("SBI");
        o.setUpiId("ravi@okaxis");
        o.setRegistrationDocPath("/uploads/owners/registration_old.jpg");
        o.setPanDocPath("/uploads/owners/pan_old.jpg");
        o.setAadhaarDocPath("/uploads/owners/aadhaar_old.jpg");
        o.setRejectionReason("PAN unclear");
        o.setAdminRemindedAt(LocalDateTime.now().minusDays(1));
        o.setResubmitTokenHash(OwnerCredentials.hash(TOKEN));
        o.setResubmitTokenExpiresAt(tokenExpires);
        when(owners.findByResubmitTokenHash(OwnerCredentials.hash(TOKEN))).thenReturn(Optional.of(o));
        when(owners.findByEmailIgnoreCase("ravi@example.com")).thenReturn(Optional.of(o));
        when(owners.findByPanHash(o.getPanHash())).thenReturn(Optional.of(o));
        return o;
    }

    @Test
    @DisplayName("the resubmit form is pre-filled, but never with the account number, UPI or Aadhaar")
    void prefillOmitsSecrets() throws Exception {
        rejected(LocalDateTime.now().plusDays(3));

        com.travelplatform.dto.OwnerResubmitPrefill p = service.prefill(TOKEN);

        assertEquals("Ravi Travels", p.travelsName());
        assertEquals(List.of(1L), p.cityIds());
        assertEquals("XXXXX1234F", p.panMasked());
        assertEquals("PAN unclear", p.rejectionReason());
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(p);
        assertFalse(json.contains("12345678906789"));
        assertFalse(json.contains("ravi@okaxis"));
        assertFalse(json.contains("ABCDE1234F"));
    }

    @Test
    @DisplayName("resubmitting reopens the same application as pending and uses up the link")
    void resubmitReopensSameRowAndClearsToken() {
        Owner o = rejected(LocalDateTime.now().plusDays(3));
        com.travelplatform.repository.OwnerVerificationCheckRepository checks =
                (com.travelplatform.repository.OwnerVerificationCheckRepository) ReflectionTestUtils.getField(service, "checkRepository");
        OwnerSignupForm f = form();
        f.setRegistrationDoc(null);
        f.setPanDoc(null);
        f.setAadhaarDoc(null);

        OwnerSignupService.SignupResult r = service.resubmit(TOKEN, f);

        assertEquals(42L, r.ownerId());
        assertEquals(OwnerStatus.PENDING_REVIEW, o.getStatus());
        assertNull(o.getResubmitTokenHash());
        assertNull(o.getResubmitTokenExpiresAt());
        assertNull(o.getRejectionReason());
        assertNull(o.getAdminRemindedAt());
        assertNotNull(o.getSubmittedAt());
        verify(checks).deleteByOwnerId(42L);
        verify(emails).sendAdminNewOwnerApplication(eq(o), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("documents not sent again are kept; the Aadhaar QR is re-read only for a new Aadhaar image")
    void resubmitKeepsDocumentsNotReplaced() {
        Owner o = rejected(LocalDateTime.now().plusDays(3));
        OwnerSignupForm f = form();
        f.setRegistrationDoc(null);
        f.setAadhaarDoc(null);

        service.resubmit(TOKEN, f);

        verify(files, times(1)).storeImage(any(), eq("owners"), anyString());
        assertEquals("/uploads/owners/pan_x.jpg", o.getPanDocPath());
        assertEquals("/uploads/owners/registration_old.jpg", o.getRegistrationDocPath());
        assertEquals("/uploads/owners/aadhaar_old.jpg", o.getAadhaarDocPath());
        verify(verifier, never()).verifyImage(any());
        verify(qrs, never()).save(any());
    }

    @Test
    @DisplayName("an expired or already used link is refused")
    void expiredOrUsedTokenIsRefused() {
        rejected(LocalDateTime.now().minusMinutes(1));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.prefill(TOKEN));
        assertEquals("This link has expired. Please contact support.", e.getMessage());

        e = assertThrows(IllegalArgumentException.class, () -> service.resubmit("unknown", form()));
        assertEquals("This link has expired. Please contact support.", e.getMessage());
    }

    @Test
    @DisplayName("the owner may keep their own email and PAN when resubmitting")
    void resubmitMayKeepOwnPanAndEmail() {
        rejected(LocalDateTime.now().plusDays(3));
        assertDoesNotThrow(() -> service.resubmit(TOKEN, form()));
    }

    @Test
    @DisplayName("a rejected owner whose link expired can simply apply again with the same email")
    void freshSignupReusesOwnRejectedApplication() {
        Owner o = rejected(LocalDateTime.now().minusDays(1));
        com.travelplatform.repository.OwnerVerificationCheckRepository checks =
                (com.travelplatform.repository.OwnerVerificationCheckRepository) ReflectionTestUtils.getField(service, "checkRepository");

        OwnerSignupService.SignupResult r = service.signup(form());

        assertEquals(42L, r.ownerId());
        assertEquals(OwnerStatus.PENDING_REVIEW, o.getStatus());
        assertNull(o.getResubmitTokenHash());
        assertNull(o.getRejectionReason());
        verify(checks).deleteByOwnerId(42L);
    }

    @Test
    @DisplayName("a PAN held only by a rejected application (maybe someone else's misuse) does not block its real holder")
    void panHeldByRejectedApplicationIsReleased() {
        Owner squatter = new Owner();
        squatter.setId(7L);
        squatter.setEmail("someone-else@example.com");
        squatter.setStatus(OwnerStatus.REJECTED);
        squatter.setPanHash(OwnerApplicationChecks.panHash("ABCDE1234F"));
        when(owners.findByPanHash(OwnerApplicationChecks.panHash("ABCDE1234F"))).thenReturn(Optional.of(squatter));

        assertDoesNotThrow(() -> service.signup(form()));

        assertNull(squatter.getPanHash());
        verify(owners).saveAndFlush(squatter);
    }

    @Test
    @DisplayName("an Aadhaar photo whose QR cannot be read still signs up - the admin falls back to the app")
    void signupSucceedsWhenAadhaarQrUnreadable() {
        when(verifier.verifyImage(any())).thenReturn(AadhaarSecureQr.Result.of(AadhaarSecureQr.Status.UNREADABLE));

        service.signup(form());

        verify(owners).save(any(Owner.class));
        ArgumentCaptor<OwnerAadhaarQr> qr = ArgumentCaptor.forClass(OwnerAadhaarQr.class);
        verify(qrs).save(qr.capture());
        assertEquals("UNREADABLE", qr.getValue().getStatus());
        assertNull(qr.getValue().getName());
    }
}
