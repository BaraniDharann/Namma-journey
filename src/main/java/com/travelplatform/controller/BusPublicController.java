package com.travelplatform.controller;

import com.travelplatform.dto.bus.BusViews.PlaceView;
import com.travelplatform.service.bus.BusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The public side of the bus module, no login needed (SecurityConfig). */
@RestController
@RequestMapping("/api/bus")
@RequiredArgsConstructor
public class BusPublicController {

    private final BusService busService;

    /** Town autocomplete: up to 10 towns matching q. */
    @GetMapping("/places")
    public List<PlaceView> places(@RequestParam(required = false) String q) {
        return busService.searchPlaces(q);
    }
}
