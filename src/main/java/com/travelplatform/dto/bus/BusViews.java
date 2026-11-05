package com.travelplatform.dto.bus;

import com.travelplatform.entity.BusPlace;
import com.travelplatform.entity.BusSeat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** What the bus screens get back (JSON). Times of day are "HH:mm", dates ISO. */
public final class BusViews {

    private BusViews() {
    }

    public record SeatView(String label, int deck, int row, int col, String kind, String berth, String pairLabel,
                           boolean ladiesOnly, String seatClass) {

        public static SeatView of(BusSeat s) {
            return new SeatView(s.getLabel(), s.getDeck(), s.getRowNo(), s.getColNo(), s.getKind().name(),
                    s.getBerth() == null ? null : s.getBerth().name(), s.getPairLabel(), s.isLadiesOnly(),
                    s.seatClass().name());
        }
    }

    public record DocumentView(String type, String path, LocalDate expiresOn) {
    }

    public record PhotoView(Long id, String path, int position) {
    }

    /** A bus as its owner sees it. layoutLocked: a ticket was sold, so the seats can no longer change. */
    public record BusView(Long id, String registrationNumber, String name, boolean ac, String permitType,
                          List<String> amenities, String status, String rejectionReason, LocalDateTime submittedAt,
                          List<DocumentView> documents, List<PhotoView> photos, List<SeatView> seats,
                          boolean layoutLocked, String typeLabel) {
    }

    /** A bus as the admin reviews it: the owner's view plus whose it is. */
    public record AdminBusView(Long id, String registrationNumber, String name, boolean ac, String permitType,
                               List<String> amenities, String status, String rejectionReason,
                               LocalDateTime submittedAt, List<DocumentView> documents, List<PhotoView> photos,
                               List<SeatView> seats, boolean layoutLocked, String typeLabel, Long ownerId,
                               String ownerCode, String travelsName) {

        public static AdminBusView of(BusView b, Long ownerId, String ownerCode, String travelsName) {
            return new AdminBusView(b.id(), b.registrationNumber(), b.name(), b.ac(), b.permitType(), b.amenities(),
                    b.status(), b.rejectionReason(), b.submittedAt(), b.documents(), b.photos(), b.seats(),
                    b.layoutLocked(), b.typeLabel(), ownerId, ownerCode, travelsName);
        }
    }

    public record PlaceView(Long id, String name, String district) {

        public static PlaceView of(BusPlace p) {
            return new PlaceView(p.getId(), p.getName(), p.getDistrict());
        }
    }

    public record PointView(Long id, String kind, String name, String address, String landmark, Double lat, Double lng,
                            int dayOffset, String time) {
    }

    public record StopView(int seq, Long placeId, String placeName, List<PointView> points) {
    }

    public record FareView(int fromSeq, int toSeq, String seatClass, BigDecimal fare) {
    }

    /** runDays: 1 = Monday ... 7 = Sunday. */
    public record RouteView(Long id, Long busId, String busName, String name, List<Integer> runDays,
                            LocalDate activeFrom, LocalDate activeTo, int maxSeatsPerBooking,
                            List<BusForms.Slab> cancellationPolicy, String status, List<StopView> stops,
                            List<FareView> fares) {
    }

    /** A trip on the owner's Trips list. amountCollected: fares (without GST) of passengers still travelling. */
    public record TripView(Long id, Long routeId, String routeName, Long busId, String busName,
                           String registrationNumber, LocalDate travelDate, LocalDateTime departureAt,
                           LocalDateTime arrivalAt, String status, int seatsTotal, int seatsSold,
                           BigDecimal amountCollected) {
    }

    public record TripStopView(int seq, String placeName) {
    }

    /** Who has a seat for which stretch. Passenger fields are null for a BLOCKED seat. */
    public record AllocationView(int fromSeq, int toSeq, String status, String gender, String ticketNo,
                                 String passengerName, Integer age, String phone, String boardingPoint,
                                 String droppingPoint) {
    }

    public record TripSeatView(String label, int deck, int row, int col, String kind, String berth, String pairLabel,
                               boolean ladiesOnly, String seatClass, List<AllocationView> allocations) {

        public static TripSeatView of(SeatView s, List<AllocationView> allocations) {
            return new TripSeatView(s.label(), s.deck(), s.row(), s.col(), s.kind(), s.berth(), s.pairLabel(),
                    s.ladiesOnly(), s.seatClass(), allocations);
        }
    }

    /** One trip with its seat chart, crew and stops. */
    public record TripDetail(Long id, Long routeId, String routeName, Long busId, String busName,
                             String registrationNumber, LocalDate travelDate, LocalDateTime departureAt,
                             LocalDateTime arrivalAt, String status, int seatsTotal, int seatsSold,
                             BigDecimal amountCollected, String driverName, String driverPhone,
                             String conductorName, String conductorPhone, String cancelReason,
                             List<TripStopView> stops, List<TripSeatView> seats) {
    }
}
