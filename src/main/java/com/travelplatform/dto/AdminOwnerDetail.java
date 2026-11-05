package com.travelplatform.dto;

import com.travelplatform.entity.OwnerStatus;
import com.travelplatform.entity.OwnerVerificationCheck;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * An owner application as the admin reviews it. PAN and bank account are masked; documents are
 * short-lived signed links; nothing here holds a full Aadhaar number.
 */
public record AdminOwnerDetail(Long id, String ownerCode, OwnerStatus status, String travelsName,
                               String contactName, String email, String phone, List<String> cities,
                               String panMasked, String aadhaarLast4, LocalDateTime aadhaarConsentAt,
                               String bankAccountHolder, String bankAccountMasked, String bankIfsc,
                               String bankName, String upiId, LocalDateTime submittedAt,
                               LocalDateTime reviewBy, boolean overdue, String rejectionReason,
                               boolean mustChangePassword, Documents documents, AadhaarQr aadhaarQr,
                               Map<OwnerVerificationCheck.Type, Check> checks, boolean canApprove,
                               java.math.BigDecimal busCommissionPercent) {

    public record Documents(String registration, String pan, String aadhaar) {
    }

    /** What the UIDAI Secure QR on the Aadhaar upload said; null if none was read. */
    public record AadhaarQr(String status, String name, String dob, String gender, String state,
                            String pincode, String last4, boolean last4MatchesTyped, String photoDataUrl) {
    }

    public record Check(OwnerVerificationCheck.Result result, String note, LocalDateTime checkedAt,
                        Long checkedByAdminId) {
    }
}
