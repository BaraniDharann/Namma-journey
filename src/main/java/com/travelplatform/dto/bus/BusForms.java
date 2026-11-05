package com.travelplatform.dto.bus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** What the owner's and admin's bus screens send (JSON bodies). Validated in the bus services. */
public final class BusForms {

    private BusForms() {
    }

    /** POST/PUT /api/owner/bus/buses. On an edit, null fields keep their value. */
    public record BusForm(String registrationNumber, String name, Boolean ac, String permitType, List<String> amenities) {
    }

    /** One seat of a layout. Deck 0 = lower, 1 = upper; a sleeper covers grid rows row and row+1. */
    public record SeatForm(String label, Integer deck, Integer row, Integer col, String kind, String berth,
                           String pairLabel, Boolean ladiesOnly) {
    }

    /** PUT /buses/{id}/seats: the whole layout, replacing the old one. */
    public record SeatLayoutForm(List<SeatForm> seats) {
    }

    public record PlaceForm(String name, String district) {
    }

    /** One cancellation slab: cancelling at least minHours before boarding costs percent of the fare. */
    public record Slab(Integer minHours, Integer percent) {
    }

    /** POST/PUT /routes. runDays: 1 = Monday ... 7 = Sunday. */
    public record RouteForm(Long busId, String name, List<Integer> runDays, LocalDate activeFrom, LocalDate activeTo,
                            Integer maxSeatsPerBooking, List<Slab> cancellationPolicy, List<StopForm> stops,
                            List<FareForm> fares) {
    }

    public record StopForm(Integer seq, Long placeId, List<PointForm> points) {
    }

    /** id is optional: sent back for an existing point it keeps that point (and tickets using it). time is "HH:mm". */
    public record PointForm(Long id, String kind, String name, String address, String landmark, Double lat, Double lng,
                            Integer dayOffset, String time) {
    }

    public record FareForm(Integer fromSeq, Integer toSeq, String seatClass, BigDecimal fare) {
    }

    public record CrewForm(String driverName, String driverPhone, String conductorName, String conductorPhone) {
    }

    /** Seat labels, for blocking and unblocking. */
    public record SeatsForm(List<String> seats) {
    }

    public record ReasonForm(String reason) {
    }

    public record FleetTypeForm(String fleetType) {
    }

    public record CommissionForm(BigDecimal percent) {
    }
}
