package com.travelplatform.controller;

import com.travelplatform.dto.bus.BusForms;
import com.travelplatform.dto.bus.BusViews.AdminBusView;
import com.travelplatform.service.bus.BusAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** The admin's side of the bus module: reviewing buses and each owner's bus commission. ROLE_ADMIN only. */
@RestController
@RequestMapping("/api/admin/bus")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminBusController {

    private final BusAdminService busAdminService;

    /** ?status=PENDING for the review queue; no status for every bus. */
    @GetMapping("/buses")
    public List<AdminBusView> buses(@RequestParam(required = false) String status) {
        return busAdminService.list(status);
    }

    @GetMapping("/buses/{id}")
    public AdminBusView bus(@PathVariable Long id) {
        return busAdminService.detail(id);
    }

    @PostMapping("/buses/{id}/approve")
    public AdminBusView approve(@PathVariable Long id, Authentication authentication) {
        return busAdminService.approve(id, AdminOwnerController.adminId(authentication));
    }

    /** {reason}: shown to the owner. */
    @PostMapping("/buses/{id}/reject")
    public AdminBusView reject(@PathVariable Long id, @RequestBody BusForms.ReasonForm form,
                               Authentication authentication) {
        return busAdminService.reject(id, form == null ? null : form.reason(), AdminOwnerController.adminId(authentication));
    }

    /** {percent}: the platform's share of this owner's bus fares, 0 to 50. */
    @PutMapping("/owners/{id}/commission")
    public Map<String, Object> commission(@PathVariable Long id, @RequestBody BusForms.CommissionForm form) {
        BigDecimal percent = busAdminService.setCommission(id, form == null ? null : form.percent());
        return Map.of("ownerId", id, "busCommissionPercent", percent);
    }
}
