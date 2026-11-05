package com.travelplatform.controller;

import com.travelplatform.dto.bus.BusBookingViews.SearchResult;
import com.travelplatform.dto.bus.BusBookingViews.SeatMap;
import com.travelplatform.dto.bus.BusViews.PlaceView;
import com.travelplatform.service.bus.BusSearchService;
import com.travelplatform.service.bus.BusService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** The public side of the bus module, no login needed (SecurityConfig). */
@RestController
@RequestMapping("/api/bus")
@RequiredArgsConstructor
public class BusPublicController {

    private final BusService busService;
    private final BusSearchService searchService;

    /** Town autocomplete: up to 10 towns matching q. */
    @GetMapping("/places")
    public List<PlaceView> places(@RequestParam(required = false) String q) {
        return busService.searchPlaces(q);
    }

    /** Buses from town {@code from} to town {@code to} boarding on {@code date}; the page sorts and filters. */
    @GetMapping("/search")
    public List<SearchResult> search(@RequestParam Long from, @RequestParam Long to,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return searchService.search(from, to, date);
    }

    /** The seat map of a trip for the stretch from {@code from} to {@code to}. */
    @GetMapping("/trips/{id}")
    public SeatMap seatMap(@PathVariable Long id, @RequestParam Long from, @RequestParam Long to) {
        return searchService.seatMap(id, from, to);
    }
}
