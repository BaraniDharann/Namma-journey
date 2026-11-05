package com.travelplatform.service.bus;

import com.travelplatform.dto.bus.BusBookingViews.SlabView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** GST per seat, the cancellation slab picked for the time left, and the slabs as clock times. */
class BusFaresTest {

    @Test
    @DisplayName("GST is worked out per seat and rounded half-up to paise")
    void gst() {
        assertEquals(new BigDecimal("32.50"), BusFares.gst(new BigDecimal("650.00"), new BigDecimal("5")));
        assertEquals(new BigDecimal("40.03"), BusFares.gst(new BigDecimal("800.50"), new BigDecimal("5"))); // 40.025
        assertEquals(new BigDecimal("0.05"), BusFares.gst(new BigDecimal("1.00"), new BigDecimal("5")));
        assertEquals(new BigDecimal("0.00"), BusFares.gst(new BigDecimal("650.00"), BigDecimal.ZERO));
    }

    @Test
    @DisplayName("the slab is chosen by the minutes left: 47h59m is under 48h, 48h00m and 48h01m are in it")
    void slabBoundaries() {
        List<com.travelplatform.dto.bus.BusForms.Slab> slabs = BusCancellationPolicy.DEFAULT; // 48:10, 24:25, 12:50, 0:100
        assertEquals(25, BusFares.chargePercent(slabs, 47 * 60 + 59));
        assertEquals(10, BusFares.chargePercent(slabs, 48 * 60));
        assertEquals(10, BusFares.chargePercent(slabs, 48 * 60 + 1));
        assertEquals(50, BusFares.chargePercent(slabs, 23 * 60 + 59));
        assertEquals(100, BusFares.chargePercent(slabs, 30));
        assertEquals(100, BusFares.chargePercent(slabs, 0));
    }

    @Test
    @DisplayName("each slab applies until boarding minus its hours; the last one until boarding (null)")
    void slabViews() {
        LocalDateTime boarding = LocalDateTime.of(2026, 11, 10, 21, 30);
        List<SlabView> views = BusFares.slabViews(BusCancellationPolicy.DEFAULT, boarding);
        assertEquals(4, views.size());
        assertEquals(LocalDateTime.of(2026, 11, 8, 21, 30), views.get(0).until());
        assertEquals(LocalDateTime.of(2026, 11, 9, 21, 30), views.get(1).until());
        assertEquals(LocalDateTime.of(2026, 11, 10, 9, 30), views.get(2).until());
        assertNull(views.get(3).until());
        assertEquals(100, views.get(3).percent());
    }
}
