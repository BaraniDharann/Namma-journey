package com.travelplatform.controller;

import com.travelplatform.dto.DriverLocationDTO;
import com.travelplatform.service.LocationTrackingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class LocationWebSocketController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private LocationTrackingService locationTrackingService;

    @MessageMapping("/location.update")
    public void updateLocation(DriverLocationDTO dto) {
        locationTrackingService.updateLocation(dto);
        messagingTemplate.convertAndSend(
                "/topic/booking/" + dto.getBookingId() + "/location", dto);
    }
}
