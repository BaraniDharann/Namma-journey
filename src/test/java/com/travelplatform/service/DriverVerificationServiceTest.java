package com.travelplatform.service;

import com.travelplatform.dto.DriverDetailsResponse;
import com.travelplatform.entity.Driver;
import com.travelplatform.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DriverVerificationServiceTest {

    private DriverRepository drivers;
    private FileStorageService files;
    private NotificationService notifications;
    private OwnerService ownerService;
    private DriverVerificationService service;

    private final MultipartFile img = new MockMultipartFile("f", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});

    @BeforeEach
    void setUp() {
        drivers = mock(DriverRepository.class);
        files = mock(FileStorageService.class);
        notifications = mock(NotificationService.class);
        ownerService = mock(OwnerService.class);
        when(files.storeFile(any(), anyString())).thenAnswer(i -> "uploads/drivers/new_" + i.getArgument(1) + ".jpg");
        when(drivers.save(any(Driver.class))).thenAnswer(i -> i.getArgument(0));
        when(ownerService.mapToDriverDetails(any(Driver.class))).thenAnswer(i -> {
            Driver d = i.getArgument(0);
            DriverDetailsResponse r = new DriverDetailsResponse();
            r.setDriverId(d.getId());
            return r;
        });

        service = new DriverVerificationService();
        ReflectionTestUtils.setField(service, "driverRepository", drivers);
        ReflectionTestUtils.setField(service, "fileStorageService", files);
        ReflectionTestUtils.setField(service, "notificationService", notifications);
        ReflectionTestUtils.setField(service, "ownerService", ownerService);
    }

    private Driver driver(long id, Driver.Verification v) {
        Driver d = new Driver();
        d.setId(id);
        d.setName("Ravi");
        d.setFleet(Driver.Fleet.INDEPENDENT);
        d.setVerificationStatus(v);
        d.setPhoto("uploads/drivers/photo_old.jpg");
        d.setLicensePhoto("uploads/drivers/license_old.jpg");
        d.setAadhaarPhoto("uploads/drivers/aadhaar_old.jpg");
        d.setRcPhoto("uploads/drivers/rc_old.jpg");
        d.setRejectionReason(v == Driver.Verification.REJECTED ? "Blurry licence" : null);
        when(drivers.findById(id)).thenReturn(Optional.of(d));
        return d;
    }

    @Test
    void approveOnlyFromUnderReview() {
        driver(1, Driver.Verification.APPROVED);
        driver(2, Driver.Verification.REJECTED);
        assertThrows(IllegalArgumentException.class, () -> service.approve(1L, 9L));
        assertThrows(IllegalArgumentException.class, () -> service.approve(2L, 9L));
        verify(drivers, never()).save(any());
    }

    @Test
    void approveMarksApprovedAndRecordsReviewer() {
        Driver d = driver(1, Driver.Verification.UNDER_REVIEW);
        service.approve(1L, 9L);
        assertEquals(Driver.Verification.APPROVED, d.getVerificationStatus());
        assertEquals(9L, d.getReviewedBy());
        assertNotNull(d.getReviewedAt());
    }

    @Test
    void rejectNeedsReasonOf5To500() {
        driver(1, Driver.Verification.UNDER_REVIEW);
        assertThrows(IllegalArgumentException.class, () -> service.reject(1L, null, 9L));
        assertThrows(IllegalArgumentException.class, () -> service.reject(1L, "  no ", 9L));
        assertThrows(IllegalArgumentException.class, () -> service.reject(1L, "x".repeat(501), 9L));
        assertDoesNotThrow(() -> service.reject(1L, "x".repeat(500), 9L));
    }

    @Test
    void rejectRecordsReasonAndReviewer() {
        Driver d = driver(1, Driver.Verification.UNDER_REVIEW);
        service.reject(1L, "  Licence is blurry  ", 9L);
        assertEquals(Driver.Verification.REJECTED, d.getVerificationStatus());
        assertEquals("Licence is blurry", d.getRejectionReason());
        assertEquals(9L, d.getReviewedBy());
        assertNotNull(d.getReviewedAt());
        verify(notifications).notifyDriverRejected(d);
    }

    @Test
    void resubmitOnlyWhenRejected() {
        driver(1, Driver.Verification.UNDER_REVIEW);
        driver(2, Driver.Verification.APPROVED);
        assertThrows(IllegalArgumentException.class, () -> service.resubmit(1L, img, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.resubmit(2L, img, null, null, null));
        verify(files, never()).storeFile(any(), anyString());
    }

    @Test
    void resubmitNeedsAtLeastOneFile() {
        driver(1, Driver.Verification.REJECTED);
        assertThrows(IllegalArgumentException.class, () -> service.resubmit(1L, null, null, null, null));
    }

    @Test
    void partialReuploadKeepsOthers() {
        Driver d = driver(1, Driver.Verification.REJECTED);
        service.resubmit(1L, null, null, null, img);
        assertEquals("uploads/drivers/new_rc.jpg", d.getRcPhoto());
        assertEquals("uploads/drivers/photo_old.jpg", d.getPhoto());
        assertEquals("uploads/drivers/license_old.jpg", d.getLicensePhoto());
        assertEquals("uploads/drivers/aadhaar_old.jpg", d.getAadhaarPhoto());
        assertEquals(Driver.Verification.UNDER_REVIEW, d.getVerificationStatus());
        assertNull(d.getRejectionReason());
        verify(files).delete("uploads/drivers/rc_old.jpg");
        verify(files, never()).delete("uploads/drivers/photo_old.jpg");
        verify(notifications).notifyDriverApplication(d, true);
    }

    @Test
    void failedSaveDeletesNewFilesAndKeepsOld() {
        driver(1, Driver.Verification.REJECTED);
        when(drivers.save(any(Driver.class))).thenThrow(new RuntimeException("db down"));
        assertThrows(RuntimeException.class, () -> service.resubmit(1L, null, null, null, img));
        verify(files).delete("uploads/drivers/new_rc.jpg");
        verify(files, never()).delete("uploads/drivers/rc_old.jpg");
    }

    @Test
    void approvalNotifiesDriver() {
        Driver d = driver(1, Driver.Verification.UNDER_REVIEW);
        service.approve(1L, 9L);
        verify(notifications).notifyDriverApproved(d);
    }

    @Test
    void applicationsOldestFirst() {
        Driver newer = driver(1, Driver.Verification.UNDER_REVIEW);
        newer.setSubmittedAt(LocalDateTime.of(2026, 1, 2, 0, 0));
        Driver older = driver(2, Driver.Verification.UNDER_REVIEW);
        older.setSubmittedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        // The repository sorts by submittedAt; the stub returns what that query would.
        when(drivers.findByFleetAndVerificationStatusOrderBySubmittedAtAsc(
                Driver.Fleet.INDEPENDENT, Driver.Verification.UNDER_REVIEW))
                .thenReturn(List.of(older, newer));
        List<DriverDetailsResponse> out = service.applications(Driver.Verification.UNDER_REVIEW);
        assertEquals(List.of(2L, 1L), out.stream().map(DriverDetailsResponse::getDriverId).toList());
    }

    @Test
    void statusListsMissingDocuments() {
        Driver d = driver(1, Driver.Verification.REJECTED);
        d.setRcPhoto(" ");
        d.setPhoto(null);
        Map<String, Object> s = service.status(1L);
        assertEquals("REJECTED", s.get("status"));
        assertEquals("Blurry licence", s.get("rejectionReason"));
        assertEquals(List.of("photo", "rcPhoto"), s.get("missingDocuments"));
    }
}
