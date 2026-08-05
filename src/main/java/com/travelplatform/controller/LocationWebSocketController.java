package com.travelplatform.controller;

import com.travelplatform.dto.DriverLocationDTO;
import com.travelplatform.service.LocationTrackingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class LocationWebSocketController {

    @Autowired
    private LocationTrackingService locationTrackingService;

    /**
     * Storing and broadcasting both live in the service now, so the REST fallback in
     * {@code DriverController#updateLocationRest} produces the same push this frame does.
     * That path previously only wrote to the store, so a driver whose STOMP connection had
     * dropped kept updating a position nobody was pushed.
     */
    @MessageMapping("/location.update")
    public void updateLocation(DriverLocationDTO dto, Principal principal) {
        // Mirrors the REST endpoint's check. The channel interceptor authorises SUBSCRIBE
        // frames but not SEND, so without this any authenticated session could publish
        // positions onto another driver's booking topic.
        if (principal == null || dto == null || dto.getDriverId() == null
                || !principal.getName().equals(dto.getDriverId().toString())) {
            throw new AccessDeniedException("Cannot update location for another driver");
        }
        locationTrackingService.updateLocation(dto);
    }
}
