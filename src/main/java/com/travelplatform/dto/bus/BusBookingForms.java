package com.travelplatform.dto.bus;

import java.util.List;

/** What the customer's bus screens and the payout screens send (JSON bodies). Validated in the bus services. */
public final class BusBookingForms {

    private BusBookingForms() {
    }

    /** POST /api/bus/holds. */
    public record HoldForm(Long tripId, Long fromPlaceId, Long toPlaceId, Long boardingPointId, Long droppingPointId,
                           String contactPhone, String contactEmail, String extraPhone,
                           List<PassengerForm> passengers) {
    }

    /** gender is "M" or "F". */
    public record PassengerForm(String seatLabel, String name, Integer age, String gender) {
    }

    /** POST /api/bus/tickets/{id}/verify: what Razorpay Checkout handed the browser. */
    public record VerifyForm(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) {
    }

    public record ReviewForm(Integer rating, String comment) {
    }

    public record UtrForm(String utr) {
    }
}
