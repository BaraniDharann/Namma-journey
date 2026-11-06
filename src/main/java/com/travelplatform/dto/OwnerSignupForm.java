package com.travelplatform.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * A travels owner's application, as multipart form fields plus three document images. Checked in
 * full by {@code OwnerSignupService}, which has the field-by-field messages.
 */
@Data
@NoArgsConstructor
public class OwnerSignupForm {
    private String travelsName;
    private String contactName;
    private String email;
    private String phone;
    /** City-ride zones (the earlier form); optional now that serviceAreas exists. */
    private List<Long> cityIds;
    /** INDIA, STATE:&lt;state&gt; or CITY:&lt;state&gt;:&lt;city&gt; - see ServiceAreas. */
    private List<String> serviceAreas;
    private String panNumber;
    /** Validated, then only its last 4 digits are kept. */
    private String aadhaarNumber;
    private String bankAccountHolder;
    private String bankAccountNumber;
    private String bankIfsc;
    private String bankName;
    private String upiId;
    /** "I consent to NammaJourney verifying my Aadhaar offline" - required by the Aadhaar Act. */
    private boolean aadhaarConsent;

    private MultipartFile registrationDoc;
    private MultipartFile panDoc;
    private MultipartFile aadhaarDoc;
}
