package com.travelplatform.controller;

import com.travelplatform.dto.bus.BusBookingForms;
import com.travelplatform.dto.bus.BusBookingViews.CancelQuote;
import com.travelplatform.dto.bus.BusBookingViews.PayView;
import com.travelplatform.dto.bus.BusBookingViews.TicketView;
import com.travelplatform.dto.bus.BusForms;
import com.travelplatform.idempotency.IdempotencyFilter;
import com.travelplatform.service.bus.BusBookingService;
import com.travelplatform.service.bus.BusTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * A signed-in customer's bus bookings: holding seats, paying, tickets, cancelling and reviews.
 * Every call acts on the caller's own tickets; anyone else's is "not found".
 */
@RestController
@RequestMapping("/api/bus")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
public class BusCustomerController {

    private final BusBookingService bookingService;
    private final BusTicketService ticketService;

    /** Holds the seats for payment: a PENDING ticket. Send an Idempotency-Key; a retry returns the same ticket. */
    @PostMapping("/holds")
    public ResponseEntity<TicketView> hold(@RequestBody BusBookingForms.HoldForm form,
                                           @RequestHeader(value = IdempotencyFilter.HEADER, required = false) String key,
                                           Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.hold(me(authentication), key, form));
    }

    @GetMapping("/tickets")
    public List<TicketView> tickets(Authentication authentication) {
        return ticketService.list(me(authentication));
    }

    @GetMapping("/tickets/{id}")
    public TicketView ticket(@PathVariable UUID id, Authentication authentication) {
        return ticketService.get(me(authentication), id);
    }

    /** A Razorpay order for the ticket's total, opened with Checkout. */
    @PostMapping("/tickets/{id}/pay")
    public PayView pay(@PathVariable UUID id, Authentication authentication) {
        return bookingService.pay(me(authentication), id);
    }

    @PostMapping("/tickets/{id}/verify")
    public TicketView verify(@PathVariable UUID id, @RequestBody BusBookingForms.VerifyForm form,
                             Authentication authentication) {
        return bookingService.verify(me(authentication), id, form);
    }

    /** Checkout closed or failed: the seats go back on sale. */
    @PostMapping("/tickets/{id}/release")
    public TicketView release(@PathVariable UUID id, Authentication authentication) {
        return bookingService.release(me(authentication), id);
    }

    /** ?seats=U3,U4: charge and refund per seat if cancelled now. */
    @GetMapping("/tickets/{id}/cancel-quote")
    public CancelQuote cancelQuote(@PathVariable UUID id, @RequestParam(required = false) String seats,
                                   Authentication authentication) {
        return ticketService.cancelQuote(me(authentication), id, seats);
    }

    /** JSON {seats: [label]}. */
    @PostMapping("/tickets/{id}/cancel")
    public TicketView cancel(@PathVariable UUID id, @RequestBody BusForms.SeatsForm form, Authentication authentication) {
        return ticketService.cancel(me(authentication), id, form == null ? null : form.seats());
    }

    /** JSON {rating 1-5, comment}: once, after a completed journey. */
    @PostMapping("/tickets/{id}/review")
    public TicketView review(@PathVariable UUID id, @RequestBody BusBookingForms.ReviewForm form,
                             Authentication authentication) {
        return ticketService.review(me(authentication), id, form);
    }

    private static UUID me(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
