package com.travelplatform.controller;

import com.travelplatform.entity.Vehicle;
import com.travelplatform.service.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** A travels owner's cars (V27). Every call acts on the calling owner's own cars only. */
@RestController
@RequestMapping("/api/owner/vehicles")
@RequiredArgsConstructor
public class OwnerFleetController {

    private final VehicleService vehicleService;
    private final com.travelplatform.service.CityCarService cityCarService;

    @GetMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<Vehicle>> list() {
        return ResponseEntity.ok(vehicleService.list());
    }

    /** Multipart: category, registrationNumber, model, seats, ac, and the RC photo. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Vehicle> create(
            @RequestPart("category") String category,
            @RequestPart("registrationNumber") String registrationNumber,
            @RequestPart(value = "model", required = false) String model,
            @RequestPart("seats") String seats,
            @RequestPart(value = "ac", required = false) String ac,
            @RequestPart(value = "rcPhoto", required = false) MultipartFile rcPhoto) {
        Integer seatCount;
        try {
            seatCount = Integer.valueOf(seats.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Seats must be a number");
        }
        Boolean hasAc = ac == null || ac.isBlank() ? null : Boolean.valueOf(ac.trim());
        return ResponseEntity.status(HttpStatus.CREATED).body(vehicleService.create(
                new VehicleService.VehicleForm(category, registrationNumber, model, seatCount, hasAc), rcPhoto));
    }

    /** JSON {category?, registrationNumber?, model?, seats?, ac?}: blank fields keep their value. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Vehicle> update(@PathVariable Long id, @RequestBody VehicleService.VehicleForm form) {
        return ResponseEntity.ok(vehicleService.update(id, form));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Vehicle> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(vehicleService.deactivate(id));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Vehicle> activate(@PathVariable Long id) {
        return ResponseEntity.ok(vehicleService.activate(id));
    }

    /** What the car is used for: City rides, Travel trips or neither. */
    public record ModeForm(String mode, Long driverId) {
    }

    /** JSON {mode: CITY|TRAVEL|OFF, driverId (for CITY)}. */
    @PutMapping("/{id}/mode")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Vehicle> setMode(@PathVariable Long id, @RequestBody ModeForm form) {
        if (form == null) {
            throw new IllegalArgumentException("Choose City, Travel or Off");
        }
        return ResponseEntity.ok(cityCarService.setMode(id, form.mode(), form.driverId()));
    }
}
