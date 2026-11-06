package com.travelplatform.dto;

import java.util.List;

/**
 * What a rejected owner's resubmit form starts with. Bank account, UPI and Aadhaar are typed
 * again: whoever holds the link should not be shown them.
 */
public record OwnerResubmitPrefill(String travelsName, String contactName, String email, String phone,
                                   List<Long> cityIds, List<String> serviceAreas, String bankAccountHolder, String bankIfsc,
                                   String bankName, String panMasked, String rejectionReason) {
}
