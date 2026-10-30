package com.travelplatform.controller;

import com.travelplatform.service.RideSafetyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public "watch my trip" page's data. No login - it is opened by family from a forwarded
 * link - so the random share token is the whole permission (under /api/public, see SecurityConfig).
 */
@RestController
@RequestMapping("/api/public/trips")
@RequiredArgsConstructor
public class SharedTripController {

    private final RideSafetyService safetyService;

    @GetMapping("/{token}")
    public ResponseEntity<RideSafetyService.SharedTrip> sharedTrip(@PathVariable String token) {
        return safetyService.sharedTrip(token)
                // A live position: never cached by a proxy or the browser.
                .map(t -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(t))
                .orElse(ResponseEntity.notFound().build());
    }
}
