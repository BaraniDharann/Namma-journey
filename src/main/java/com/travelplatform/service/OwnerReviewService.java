package com.travelplatform.service;

import com.travelplatform.dto.AdminOwnerDetail;
import com.travelplatform.dto.AdminOwnerSummary;
import com.travelplatform.dto.OwnerCheckRequest;
import com.travelplatform.entity.City;
import com.travelplatform.entity.Owner;
import com.travelplatform.entity.OwnerStatus;
import com.travelplatform.entity.OwnerVerificationCheck;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.OwnerAadhaarQrRepository;
import com.travelplatform.repository.OwnerRepository;
import com.travelplatform.repository.OwnerVerificationCheckRepository;
import com.travelplatform.storage.SignedUploadUrls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The admin's side of owner onboarding: the queue of applications, one application in full, and
 * the four-item checklist the admin works through on free official sources before approving.
 */
@Service
public class OwnerReviewService {

    private static final Logger log = LoggerFactory.getLogger(OwnerReviewService.class);

    static final Duration OVERDUE_AFTER = Duration.ofHours(48);

    @Autowired
    private OwnerRepository ownerRepository;

    @Autowired
    private OwnerVerificationCheckRepository checkRepository;

    @Autowired
    private OwnerAadhaarQrRepository aadhaarQrRepository;

    @Autowired
    private SignedUploadUrls signedUploadUrls;

    @Transactional(readOnly = true)
    public List<AdminOwnerSummary> list(OwnerStatus status) {
        LocalDateTime now = LocalDateTime.now();
        return ownerRepository.findByStatusOrderBySubmittedAtAsc(status).stream()
                .map(o -> {
                    long hours = o.getSubmittedAt() == null ? 0 : Duration.between(o.getSubmittedAt(), now).toHours();
                    boolean overdue = o.getStatus() == OwnerStatus.PENDING_REVIEW && o.getSubmittedAt() != null
                            && o.getSubmittedAt().plus(OVERDUE_AFTER).isBefore(now);
                    return new AdminOwnerSummary(o.getId(), o.getOwnerCode(), o.getStatus(), o.getTravelsName(),
                            cityNames(o), o.getSubmittedAt(), hours, overdue);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminOwnerDetail detail(Long id) {
        Owner o = find(id);
        Map<OwnerVerificationCheck.Type, AdminOwnerDetail.Check> checks = checks(id);
        LocalDateTime reviewBy = o.getSubmittedAt() == null ? null : o.getSubmittedAt().plus(OVERDUE_AFTER);
        boolean overdue = o.getStatus() == OwnerStatus.PENDING_REVIEW && reviewBy != null
                && reviewBy.isBefore(LocalDateTime.now());

        AdminOwnerDetail.AadhaarQr qr = aadhaarQrRepository.findById(id)
                .map(q -> new AdminOwnerDetail.AadhaarQr(q.getStatus(), q.getName(), q.getDob(), q.getGender(),
                        q.getState(), q.getPincode(), q.getLast4(),
                        q.getLast4() != null && q.getLast4().equals(o.getAadhaarLast4()),
                        q.getPhotoJpeg() == null ? null
                                : "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(q.getPhotoJpeg())))
                .orElse(null);

        return new AdminOwnerDetail(o.getId(), o.getOwnerCode(), o.getStatus(), o.getTravelsName(),
                o.getContactName(), o.getEmail(), o.getPhone(), cityNames(o),
                OwnerApplicationChecks.maskPan(o.getPanNumber()), o.getAadhaarLast4(), o.getAadhaarConsentAt(),
                o.getBankAccountHolder(), OwnerApplicationChecks.maskAccount(o.getBankAccountNumber()),
                o.getBankIfsc(), o.getBankName(), o.getUpiId(), o.getSubmittedAt(), reviewBy, overdue,
                o.getRejectionReason(), o.isMustChangePassword(),
                new AdminOwnerDetail.Documents(sign(o.getRegistrationDocPath()), sign(o.getPanDocPath()),
                        sign(o.getAadhaarDocPath())),
                qr, checks, allVerified(checks) && o.getStatus() == OwnerStatus.PENDING_REVIEW);
    }

    @Transactional
    public void recordCheck(Long ownerId, OwnerVerificationCheck.Type type, OwnerCheckRequest request, Long adminId) {
        Owner o = find(ownerId);
        if (o.getStatus() != OwnerStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Only a pending application can be checked");
        }
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        if (request.result() == OwnerVerificationCheck.Result.FAILED && note == null) {
            throw new IllegalArgumentException("Say why the check failed - the owner will see it as the reason");
        }
        OwnerVerificationCheck c = new OwnerVerificationCheck();
        c.setOwnerId(ownerId);
        c.setCheckType(type);
        c.setResult(request.result());
        c.setNote(note);
        c.setCheckedByAdminId(adminId);
        c.setCheckedAt(LocalDateTime.now());
        checkRepository.save(c);
        // The audit line. The note can name the person, so it stays out of the log.
        log.info("Owner {} check {} = {} by admin {}", ownerId, type, request.result(), adminId);
    }

    /** The checklist items not yet VERIFIED, in checklist order; empty means approval may go ahead. */
    List<OwnerVerificationCheck.Type> unverified(Long ownerId) {
        Map<OwnerVerificationCheck.Type, AdminOwnerDetail.Check> checks = checks(ownerId);
        return java.util.Arrays.stream(OwnerVerificationCheck.Type.values())
                .filter(t -> checks.get(t) == null || checks.get(t).result() != OwnerVerificationCheck.Result.VERIFIED)
                .toList();
    }

    Owner find(Long id) {
        return ownerRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Owner application not found"));
    }

    private Map<OwnerVerificationCheck.Type, AdminOwnerDetail.Check> checks(Long ownerId) {
        Map<OwnerVerificationCheck.Type, AdminOwnerDetail.Check> map = new EnumMap<>(OwnerVerificationCheck.Type.class);
        for (OwnerVerificationCheck c : checkRepository.findByOwnerId(ownerId)) {
            map.put(c.getCheckType(), new AdminOwnerDetail.Check(c.getResult(), c.getNote(), c.getCheckedAt(),
                    c.getCheckedByAdminId()));
        }
        return map;
    }

    private static boolean allVerified(Map<OwnerVerificationCheck.Type, AdminOwnerDetail.Check> checks) {
        return java.util.Arrays.stream(OwnerVerificationCheck.Type.values())
                .allMatch(t -> checks.get(t) != null && checks.get(t).result() == OwnerVerificationCheck.Result.VERIFIED);
    }

    private String sign(String path) {
        return path == null ? null : signedUploadUrls.signIfSensitive(path);
    }

    private static List<String> cityNames(Owner o) {
        return o.getCities() == null ? List.of() : o.getCities().stream().map(City::getName).sorted().toList();
    }
}
