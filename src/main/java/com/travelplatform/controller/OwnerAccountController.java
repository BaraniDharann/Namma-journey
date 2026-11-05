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
}
