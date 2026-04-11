package com.travelplatform.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {
    
    @Value("${payment.upi.id:sarasarathy86@okicici}")
    private String ownerUpiId;
    
    @Value("${payment.upi.name:Namma Journey}")
    private String ownerName;
    
    @GetMapping("/upi-link")
    public ResponseEntity<Map<String, String>> generateTestUpiLink(
            @RequestParam(defaultValue = "1.00") Double amount) {
        
        try {
            String encodedName = URLEncoder.encode(ownerName, "UTF-8");
            String note = URLEncoder.encode("Test Payment", "UTF-8");
            
            String upiLink = String.format(
                "upi://pay?pa=%s&pn=%s&am=%.2f&cu=INR&tn=%s",
                ownerUpiId, encodedName, amount, note
            );
            
            Map<String, String> response = new HashMap<>();
            response.put("upiLink", upiLink);
            response.put("ownerUpiId", ownerUpiId);
            response.put("ownerName", ownerName);
            response.put("amount", String.format("%.2f", amount));
            response.put("instructions", "Click the UPI link on mobile to test payment");
            
            return ResponseEntity.ok(response);
            
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Error generating UPI link", e);
        }
    }
}
