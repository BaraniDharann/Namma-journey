package com.travelplatform.service.bus;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Bus booking settings ({@code bus.*}): the GST added to fares at checkout and how long seats are
 * held for payment. The defaults are the spec's (5% GST, a 10-minute hold); tests build it with
 * {@code new BusSettings()}.
 */
@Component
public class BusSettings {

    /** Razorpay Checkout closes before the hold runs out, so a payment is not started on a seat about to be released. */
    static final long MAX_CHECKOUT_SECONDS = 8 * 60;

    private final BigDecimal gstPercent;
    private final int holdMinutes;

    public BusSettings() {
        this(new BigDecimal("5"), 10);
    }

    @Autowired
    public BusSettings(@Value("${bus.gst-percent:5}") BigDecimal gstPercent,
                       @Value("${bus.hold-minutes:10}") int holdMinutes) {
        if (gstPercent == null || gstPercent.signum() < 0 || gstPercent.compareTo(new BigDecimal("28")) > 0) {
            throw new IllegalStateException("bus.gst-percent must be between 0 and 28");
        }
        if (holdMinutes < 2 || holdMinutes > 30) {
            throw new IllegalStateException("bus.hold-minutes must be between 2 and 30");
        }
        this.gstPercent = gstPercent;
        this.holdMinutes = holdMinutes;
    }

    public BigDecimal gstPercent() {
        return gstPercent;
    }

    public int holdMinutes() {
        return holdMinutes;
    }
}
