package com.travelplatform.service;

import com.travelplatform.dto.DriverLocationDTO;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class LocationTrackingService {

    private final ConcurrentHashMap<String, DriverLocationDTO> activeLocations = new ConcurrentHashMap<>();

    public DriverLocationDTO updateLocation(DriverLocationDTO dto) {
        activeLocations.put(dto.getBookingId(), dto);
        return dto;
    }

    public DriverLocationDTO getLocation(String bookingId) {
        return activeLocations.get(bookingId);
    }

    public void removeLocation(String bookingId) {
        activeLocations.remove(bookingId);
    }
}
