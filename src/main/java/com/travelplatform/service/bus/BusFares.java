package com.travelplatform.service.bus;

import com.travelplatform.dto.bus.BusBookingViews.SlabView;
import com.travelplatform.dto.bus.BusForms.Slab;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Bus money rules: GST per seat, the cancellation charge from a ticket's slabs, and the slabs as
 * clock times for the customer. Every amount is rounded half-up to paise, per seat, the same way
 * the frontend shows it (busBooking.js fareBreakup).
 */
public final class BusFares {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private BusFares() {
    }

    public static BigDecimal money(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
    }

    /** The GST on one seat's fare. */
    public static BigDecimal gst(BigDecimal fare, BigDecimal gstPercent) {
        return percentOf(fare, gstPercent);
    }

    /** percent of amount, rounded half-up to paise. */
    public static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        return money(amount.multiply(percent).divide(HUNDRED, 8, RoundingMode.HALF_UP));
    }

    /**
     * The charge percentage for cancelling {@code minutesBeforeBoarding} minutes before boarding:
     * the first slab (highest minHours first) whose minHours is at or below the time left. 47h59m
     * left falls under a 48-hour slab's next one; exactly 48h00m is in the 48-hour slab.
     */
    public static int chargePercent(List<Slab> slabs, long minutesBeforeBoarding) {
        for (Slab s : sorted(slabs)) {
            if (minutesBeforeBoarding >= (long) s.minHours() * 60) {
                return s.percent();
            }
        }
        return 100;
    }

    /**
     * The slabs as the customer reads them: each applies until {@code boardingAt - minHours}; the
     * last one (0 hours) until boarding itself, shown as null.
     */
    public static List<SlabView> slabViews(List<Slab> slabs, LocalDateTime boardingAt) {
        List<Slab> list = sorted(slabs);
        List<SlabView> views = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Slab s = list.get(i);
            LocalDateTime until = i == list.size() - 1 || boardingAt == null ? null : boardingAt.minusHours(s.minHours());
            views.add(new SlabView(s.minHours(), s.percent(), until));
        }
        return views;
    }

    private static List<Slab> sorted(List<Slab> slabs) {
        List<Slab> list = new ArrayList<>(slabs == null || slabs.isEmpty() ? BusCancellationPolicy.DEFAULT : slabs);
        list.removeIf(s -> s == null || s.minHours() == null || s.percent() == null);
        list.sort(Comparator.comparing(Slab::minHours).reversed());
        return list;
    }
}
