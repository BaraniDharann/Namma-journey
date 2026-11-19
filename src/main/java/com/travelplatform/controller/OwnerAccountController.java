package com.travelplatform.controller;

import com.travelplatform.dto.AuthResponse;
import com.travelplatform.service.OwnerAuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The owner's own account. Reachable with the setup token an owner gets on the emailed password. */
@RestController
@RequestMapping("/api/owner")
public class OwnerAccountController {

    @Autowired
    private OwnerAuthService ownerAuthService;

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }

    @Autowired
    private com.travelplatform.service.OwnerService ownerService;

    /** My own profile, and whether I am the platform owner (which shows the platform screens). */
    @org.springframework.web.bind.annotation.GetMapping("/me")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('OWNER')")
    public com.travelplatform.dto.OwnerProfileView me() {
        com.travelplatform.config.OwnerScope scope = new com.travelplatform.config.OwnerScope();
        return ownerService.profile(scope.ownerId(), scope.isPlatform());
    }

    @PostMapping("/change-password")
    public AuthResponse changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication authentication) {
        return ownerAuthService.changePassword(Long.valueOf(authentication.getName()),
                request.currentPassword(), request.newPassword());
    }

    @Autowired
    private com.travelplatform.service.ProfileEditService profileEditService;

    @Autowired
    private com.travelplatform.service.AccountClosureService accountClosureService;

    /** Travels name, contact person and phone - free to change. */
    @org.springframework.web.bind.annotation.PutMapping("/me")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('OWNER')")
    public com.travelplatform.dto.OwnerProfileView updateMe(@RequestBody com.travelplatform.service.ProfileEditService.OwnerDetails request) {
        com.travelplatform.config.OwnerScope scope = new com.travelplatform.config.OwnerScope();
        profileEditService.updateOwnerDetails(scope.ownerId(), request);
        return ownerService.profile(scope.ownerId(), scope.isPlatform());
    }

    /** Bank and UPI: needs the current password, and emails the owner an alert. */
    @org.springframework.web.bind.annotation.PutMapping("/me/payout")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('OWNER')")
    public com.travelplatform.dto.OwnerProfileView updatePayout(@RequestBody com.travelplatform.service.ProfileEditService.OwnerPayout request) {
        com.travelplatform.config.OwnerScope scope = new com.travelplatform.config.OwnerScope();
        profileEditService.updateOwnerPayout(scope.ownerId(), request);
        return ownerService.profile(scope.ownerId(), scope.isPlatform());
    }

    /** What closing the account would also switch off (drivers, cars, bus routes, packages). */
    @org.springframework.web.bind.annotation.GetMapping("/me/closure-preview")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('OWNER')")
    public com.travelplatform.service.AccountClosureService.OwnerClosurePreview closurePreview() {
        return accountClosureService.previewOwner(new com.travelplatform.config.OwnerScope().ownerId());
    }

    public record CloseAccountRequest(String password) {
    }

    /** Closes the owner account (see AccountClosureService). The platform account cannot be closed. */
    @PostMapping("/me/close")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('OWNER')")
    public java.util.Map<String, String> closeMe(@RequestBody CloseAccountRequest request) {
        com.travelplatform.config.OwnerScope scope = new com.travelplatform.config.OwnerScope();
        accountClosureService.closeOwner(scope.ownerId(), scope.isPlatform(), request.password());
        return java.util.Map.of("message", "Your account is closed");
    }
}
