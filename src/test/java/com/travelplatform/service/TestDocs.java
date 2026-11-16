package com.travelplatform.service;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Document-check stand-ins for tests of the signup flows. */
final class TestDocs {

    /** A 12-digit number that passes the Verhoeff check. */
    static final String VALID_AADHAAR = validAadhaar("23412341234");

    private TestDocs() {
    }

    /** A checker that finds every Aadhaar genuine and matching. */
    static IdentityDocumentChecker passing() {
        return answering(new IdentityDocumentChecker.Result(IdentityDocumentChecker.Outcome.PASS, null,
                List.of("QR: VALID"), null));
    }

    /** A checker that refuses every Aadhaar with {@code reason}. */
    static IdentityDocumentChecker rejecting(String reason) {
        return answering(new IdentityDocumentChecker.Result(IdentityDocumentChecker.Outcome.REJECT, reason,
                List.of("QR: INVALID_SIGNATURE"), null));
    }

    static IdentityDocumentChecker answering(IdentityDocumentChecker.Result result) {
        IdentityDocumentChecker checker = mock(IdentityDocumentChecker.class);
        when(checker.aadhaar(any(), any(), any())).thenReturn(result);
        when(checker.pan(any(), any())).thenReturn(result);
        return checker;
    }

    static String validAadhaar(String first11) {
        for (int d = 0; d <= 9; d++) {
            if (DriverApplicationChecks.validAadhaar(first11 + d)) {
                return first11 + d;
            }
        }
        throw new AssertionError("no check digit validates " + first11);
    }
}
