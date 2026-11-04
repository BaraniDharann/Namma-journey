package com.travelplatform.dto;

import java.util.List;

/**
 * What an owner sees about themselves (GET /api/owner/me): bank details masked, never the full account.
 * fleetType (CARS, BUSES or BOTH) decides which screens the owner menu shows.
 */
public record OwnerProfileView(Long id, String ownerCode, String travelsName, String contactName, String phone,
                               String email, List<String> cities, String bankAccountHolder, String bankAccountMasked,
                               String bankIfsc, String bankName, String upiId, String assignmentMode, boolean platform,
                               String fleetType) {
}
