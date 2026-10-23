package com.travelplatform.service;

import com.travelplatform.dto.DriverDetailsResponse;
import com.travelplatform.entity.Driver;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.DriverRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The owner's review of self-signed-up drivers, and the driver's way back in after a rejection.
 * Driver has no @Version column, so every move checks the current state inside the transaction
 * instead, and each move is a conditional UPDATE on the expected status, so a stale decision is refused (409).
 */
@Service
public class DriverVerificationService {

    private static final Logger log = LoggerFactory.getLogger(DriverVerificationService.class);

    @Autowired
    private DriverRepository driverRepository;
    @Autowired
    private FileStorageService fileStorageService;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private OwnerService ownerService;
    @Autowired
    private AfterCommitDispatcher dispatcher;
    // Optional so hand-built tests need not supply it; drops the 60 s approval memo on a decision.
    @Autowired(required = false)
    private DriverService driverService;

    /** Only INDEPENDENT drivers ever go through review; owner-created ones are approved from the start. */
    @Transactional(readOnly = true)
    public List<DriverDetailsResponse> applications(Driver.Verification status) {
        Driver.Verification wanted = status == null ? Driver.Verification.UNDER_REVIEW : status;
        return driverRepository.findByFleetAndVerificationStatusOrderBySubmittedAtAsc(Driver.Fleet.INDEPENDENT, wanted)
                .stream().map(ownerService::mapToDriverDetails).toList();
    }
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "allDrivers", allEntries = true),
            @CacheEvict(value = "driverById", allEntries = true)
    })
    public DriverDetailsResponse approve(Long driverId, Long ownerId) {
        Driver d = load(driverId);
        requireUnderReview(d);
        decide(d, Driver.Verification.APPROVED, null, ownerId);
        evictApproval(driverId);
        dispatcher.dispatch("notice", () -> notifySafely(() -> notificationService.notifyDriverApproved(d), driverId));
        return ownerService.mapToDriverDetails(d);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "allDrivers", allEntries = true),
            @CacheEvict(value = "driverById", allEntries = true)
    })
    public DriverDetailsResponse reject(Long driverId, String reason, Long ownerId) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.length() < 5 || trimmed.length() > 500) {
            throw new IllegalArgumentException("Give a reason of 5 to 500 characters");
        }
        Driver d = load(driverId);
        requireUnderReview(d);
        decide(d, Driver.Verification.REJECTED, trimmed, ownerId);
        evictApproval(driverId);
        dispatcher.dispatch("notice", () -> notifySafely(() -> notificationService.notifyDriverRejected(d), driverId));
        return ownerService.mapToDriverDetails(d);
    }

    /**
     * The WHERE on the expected status makes the move atomic: a second owner (or a double click)
     * that read the same UNDER_REVIEW row updates nothing and is refused.
     */
    private void decide(Driver d, Driver.Verification to, String reason, Long ownerId) {
        LocalDateTime now = LocalDateTime.now();
        int rows = driverRepository.decide(d.getId(), Driver.Verification.UNDER_REVIEW, to, reason, now, ownerId);
        if (rows == 0) {
            throw stale();
        }
        d.setVerificationStatus(to);
        d.setRejectionReason(reason);
        d.setReviewedAt(now);
        d.setReviewedBy(ownerId);
    }

    // Immediate, not after commit: a stale "approved" can only outlive the decision by a few ms.
    private void evictApproval(Long driverId) {
        if (driverService != null) {
            driverService.evictApproval(driverId);
        }
    }

    private static OptimisticLockingFailureException stale() {
        return new OptimisticLockingFailureException(
                "This application was already decided or changed by someone else. Refresh and check again.");
    }

    /** Any subset of the four documents; the rest keep what is already on file. */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "allDrivers", allEntries = true),
            @CacheEvict(value = "driverById", allEntries = true)
    })
    public DriverDetailsResponse resubmit(Long driverId, MultipartFile photo, MultipartFile license,
                                          MultipartFile aadhaar, MultipartFile rc) {
        Driver d = load(driverId);
        if (d.getVerificationStatus() != Driver.Verification.REJECTED) {
            throw new IllegalArgumentException("Documents can be re-uploaded only after a rejection");
        }
        if (missing(photo) && missing(license) && missing(aadhaar) && missing(rc)) {
            throw new IllegalArgumentException("Upload at least one document");
        }

        // Same order as sign-up: files first, then the row. New files are removed if anything fails
        // or the transaction rolls back; the replaced ones are removed only after the commit.
        List<String> stored = new ArrayList<>();
        List<String> replaced = new ArrayList<>();
        String newPhoto = d.getPhoto(), newLicense = d.getLicensePhoto(),
                newAadhaar = d.getAadhaarPhoto(), newRc = d.getRcPhoto();
        LocalDateTime now = LocalDateTime.now();
        try {
            if (!missing(photo)) {
                replaced.add(newPhoto);
                newPhoto = store(photo, "photo", stored);
            }
            if (!missing(license)) {
                replaced.add(newLicense);
                newLicense = store(license, "license", stored);
            }
            if (!missing(aadhaar)) {
                replaced.add(newAadhaar);
                newAadhaar = store(aadhaar, "aadhaar", stored);
            }
            if (!missing(rc)) {
                replaced.add(newRc);
                newRc = store(rc, "rc", stored);
            }
            if (driverRepository.resubmitDocuments(driverId, Driver.Verification.UNDER_REVIEW, Driver.Verification.REJECTED, now, newPhoto, newLicense, newAadhaar, newRc) == 0) {
                throw stale();
            }
        } catch (RuntimeException e) {
            deleteAll(stored);
            throw e;
        }
        deleteIfRolledBack(stored);
        d.setPhoto(newPhoto);
        d.setLicensePhoto(newLicense);
        d.setAadhaarPhoto(newAadhaar);
        d.setRcPhoto(newRc);
        d.setVerificationStatus(Driver.Verification.UNDER_REVIEW);
        d.setRejectionReason(null);
        d.setSubmittedAt(now);
        dispatcher.dispatch("cleanup", () -> deleteAll(replaced));
        dispatcher.dispatch("notice", () -> notifySafely(() -> notificationService.notifyDriverApplication(d, true), driverId));
        return ownerService.mapToDriverDetails(d);
    }

    /** A commit that fails after the update leaves the new files orphaned; remove them then too. */
    private void deleteIfRolledBack(List<String> stored) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    deleteAll(stored);
                }
            }
        });
    }

    @Transactional(readOnly = true)
    public Map<String, Object> status(Long driverId) {
        Driver d = load(driverId);
        List<String> missingDocs = new ArrayList<>();
        if (blank(d.getPhoto())) missingDocs.add("photo");
        if (blank(d.getLicensePhoto())) missingDocs.add("licensePhoto");
        if (blank(d.getAadhaarPhoto())) missingDocs.add("aadhaarPhoto");
        if (blank(d.getRcPhoto())) missingDocs.add("rcPhoto");
        Map<String, Object> out = new HashMap<>();
        out.put("status", d.getVerificationStatus().name());
        out.put("rejectionReason", d.getRejectionReason());
        out.put("missingDocuments", missingDocs);
        return out;
    }

    private Driver load(Long driverId) {
        return driverRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + driverId));
    }

    // IllegalArgumentException, not IllegalStateException: GlobalExceptionHandler shows its message
    // as a 400, while any other RuntimeException subclass becomes a generic 500.
    private static void requireUnderReview(Driver d) {
        if (d.getVerificationStatus() != Driver.Verification.UNDER_REVIEW) {
            throw new IllegalArgumentException("This application is not waiting for review");
        }
    }

    private static void stamp(Driver d, Long ownerId) {
        d.setReviewedAt(LocalDateTime.now());
        d.setReviewedBy(ownerId);
    }

    private String store(MultipartFile file, String kind, List<String> stored) {
        String path = fileStorageService.storeFile(file, kind);
        stored.add(path);
        return path;
    }

    private void deleteAll(List<String> paths) {
        for (String path : paths) {
            if (blank(path)) continue;
            try {
                fileStorageService.delete(path);
            } catch (RuntimeException ignored) {
                // Cleanup is best-effort and must not hide the real outcome.
            }
        }
    }

    /** The decision is saved; a missed notice must not turn it into an error. */
    private void notifySafely(Runnable notice, Long driverId) {
        try {
            notice.run();
        } catch (RuntimeException e) {
            log.warn("Notice for driver {} failed: {}", driverId, e.toString());
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean missing(MultipartFile f) {
        return f == null || f.isEmpty();
    }
}
