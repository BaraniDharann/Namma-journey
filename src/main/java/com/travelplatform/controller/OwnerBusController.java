package com.travelplatform.controller;

import com.travelplatform.dto.bus.BusForms;
import com.travelplatform.dto.bus.BusViews.BusView;
import com.travelplatform.dto.bus.BusViews.PlaceView;
import com.travelplatform.dto.bus.BusViews.RouteView;
import com.travelplatform.dto.bus.BusViews.TripDetail;
import com.travelplatform.dto.bus.BusViews.TripView;
import com.travelplatform.service.bus.BusRouteService;
import com.travelplatform.service.bus.BusService;
import com.travelplatform.service.bus.BusTripService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * A travels owner's buses, routes and trips (V30). Every call acts on the calling owner's own rows
 * only; another owner's bus, route or trip is "not found".
 */
@RestController
@RequestMapping("/api/owner/bus")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class OwnerBusController {

    private final BusService busService;
    private final BusRouteService routeService;
    private final BusTripService tripService;

    // ---------------------------------------------------------------------------------------------
    // Buses
    // ---------------------------------------------------------------------------------------------

    @GetMapping("/buses")
    public List<BusView> buses() {
        return busService.list();
    }

    /** JSON {registrationNumber, name, ac, permitType, amenities}: a new DRAFT bus. */
    @PostMapping("/buses")
    public ResponseEntity<BusView> createBus(@RequestBody BusForms.BusForm form) {
        return ResponseEntity.status(HttpStatus.CREATED).body(busService.create(form));
    }

    @GetMapping("/buses/{id}")
    public BusView bus(@PathVariable Long id) {
        return busService.get(id);
    }

    @PutMapping("/buses/{id}")
    public BusView updateBus(@PathVariable Long id, @RequestBody BusForms.BusForm form) {
        return busService.update(id, form);
    }

    /** {seats: [...]}: replaces the whole layout. */
    @PutMapping("/buses/{id}/seats")
    public BusView seats(@PathVariable Long id, @RequestBody BusForms.SeatLayoutForm form) {
        return busService.replaceSeats(id, form == null ? null : form.seats());
    }

    /** Multipart: file, expiresOn (yyyy-MM-dd). Type: RC, PERMIT, INSURANCE or FITNESS. */
    @PostMapping(value = "/buses/{id}/documents/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BusView document(@PathVariable Long id, @PathVariable String type,
                            @RequestPart("file") MultipartFile file,
                            @RequestParam("expiresOn") String expiresOn) {
        return busService.uploadDocument(id, type, file, expiresOn);
    }

    @PostMapping(value = "/buses/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BusView photo(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return busService.addPhoto(id, file);
    }

    @DeleteMapping("/buses/{id}/photos/{photoId}")
    public BusView deletePhoto(@PathVariable Long id, @PathVariable Long photoId) {
        return busService.deletePhoto(id, photoId);
    }

    @PostMapping("/buses/{id}/submit")
    public BusView submit(@PathVariable Long id) {
        return busService.submit(id);
    }

    /** {name, district}: adds a missing town, or returns the one already there. */
    @PostMapping("/places")
    public PlaceView addPlace(@RequestBody BusForms.PlaceForm form) {
        return busService.addPlace(form);
    }

    // ---------------------------------------------------------------------------------------------
    // Routes
    // ---------------------------------------------------------------------------------------------

    @GetMapping("/routes")
    public List<RouteView> routes() {
        return routeService.list();
    }

    @PostMapping("/routes")
    public ResponseEntity<RouteView> createRoute(@RequestBody BusForms.RouteForm form) {
        return ResponseEntity.status(HttpStatus.CREATED).body(routeService.create(form));
    }

    @GetMapping("/routes/{id}")
    public RouteView route(@PathVariable Long id) {
        return routeService.get(id);
    }

    @PutMapping("/routes/{id}")
    public RouteView updateRoute(@PathVariable Long id, @RequestBody BusForms.RouteForm form) {
        return routeService.update(id, form);
    }

    @PostMapping("/routes/{id}/publish")
    public RouteView publish(@PathVariable Long id) {
        return routeService.publish(id);
    }

    @PostMapping("/routes/{id}/pause")
    public RouteView pause(@PathVariable Long id) {
        return routeService.pause(id);
    }

    // ---------------------------------------------------------------------------------------------
    // Trips
    // ---------------------------------------------------------------------------------------------

    /** Trips travelling between from and to (yyyy-MM-dd); default today and the next 14 days. */
    @GetMapping("/trips")
    public List<TripView> trips(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return tripService.list(from, to);
    }

    @GetMapping("/trips/{id}")
    public TripDetail trip(@PathVariable Long id) {
        return tripService.detail(id);
    }

    @PutMapping("/trips/{id}/crew")
    public TripDetail crew(@PathVariable Long id, @RequestBody BusForms.CrewForm form) {
        return tripService.setCrew(id, form);
    }

    @PostMapping("/trips/{id}/blocks")
    public TripDetail block(@PathVariable Long id, @RequestBody BusForms.SeatsForm form) {
        return tripService.block(id, form == null ? null : form.seats());
    }

    @PostMapping("/trips/{id}/unblock")
    public TripDetail unblock(@PathVariable Long id, @RequestBody BusForms.SeatsForm form) {
        return tripService.unblock(id, form == null ? null : form.seats());
    }

    @GetMapping("/trips/{id}/manifest.csv")
    public ResponseEntity<byte[]> manifest(@PathVariable Long id) {
        byte[] body = tripService.manifestCsv(id).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"trip-" + id + "-manifest.csv\"")
                .body(body);
    }

    @PostMapping("/trips/{id}/cancel")
    public TripDetail cancel(@PathVariable Long id, @RequestBody BusForms.ReasonForm form) {
        return tripService.cancel(id, form == null ? null : form.reason());
    }
}
