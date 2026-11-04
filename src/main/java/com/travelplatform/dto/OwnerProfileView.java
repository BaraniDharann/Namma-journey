package com.travelplatform.dto;

import java.util.List;

/** What an owner sees about themselves (GET /api/owner/me): bank details masked, never the full account. */
public record OwnerProfileView(Long id, String ownerCode, String travelsName, String contactName, String phone,
                               String email, List<String> cities, String bankAccountHolder, String bankAccountMasked,
                               String bankIfsc, String bankName, String upiId, String assignmentMode, boolean platform) {
}
