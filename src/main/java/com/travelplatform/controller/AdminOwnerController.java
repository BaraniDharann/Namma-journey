package com.travelplatform.controller;

import com.travelplatform.dto.AdminOwnerDetail;
import com.travelplatform.dto.AdminOwnerSummary;
import com.travelplatform.dto.OwnerCheckRequest;
import com.travelplatform.entity.OwnerStatus;
import com.travelplatform.entity.OwnerVerificationCheck;
import com.travelplatform.service.OwnerReviewService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** The admin reviewing travels owner applications. ROLE_ADMIN only (SecurityConfig). */
@RestController
@RequestMapping("/api/admin/owners")
public class AdminOwnerController {

    @Autowired
    private OwnerReviewService ownerReviewService;

    @GetMapping
    public List<AdminOwnerSummary> list(@RequestParam(defaultValue = "PENDING_REVIEW") OwnerStatus status) {
        return ownerReviewService.list(status);
    }

    @GetMapping("/{id}")
    public AdminOwnerDetail detail(@PathVariable Long id) {
        return ownerReviewService.detail(id);
    }

    @PutMapping("/{id}/checks/{type}")
    public ResponseEntity<AdminOwnerDetail> recordCheck(@PathVariable Long id,
                                                        @PathVariable OwnerVerificationCheck.Type type,
                                                        @Valid @RequestBody OwnerCheckRequest request,
                                                        Authentication authentication) {
        ownerReviewService.recordCheck(id, type, request, adminId(authentication));
        return ResponseEntity.ok(ownerReviewService.detail(id));
    }

    @PostMapping("/{id}/approve")
    public OwnerReviewService.ApprovalResult approve(@PathVariable Long id, Authentication authentication) {
        return ownerReviewService.approve(id, adminId(authentication));
    }

    public record RejectRequest(@jakarta.validation.constraints.NotBlank
                                @jakarta.validation.constraints.Size(max = 500) String reason) {
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request,
                                       Authentication authentication) {
        ownerReviewService.reject(id, request.reason(), adminId(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/resend-credentials")
    public OwnerReviewService.ApprovalResult resendCredentials(@PathVariable Long id, Authentication authentication) {
        return ownerReviewService.resendCredentials(id, adminId(authentication));
    }

    static Long adminId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
