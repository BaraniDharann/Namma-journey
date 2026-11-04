package com.travelplatform.dto.bus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * What the customer's bus screens and the payout screens get back (JSON). Field names are the
 * contract in the bus spec's appendix ("JSON contract for parts 2 and 3"); the frontend reads them
 * as they are.
 */
public final class BusBookingViews {

    private BusBookingViews() {
    }

    /** A boarding or dropping point on a search card. */
    public record PointBrief(Long id, String name, LocalDateTime at) {
    }

    /** A boarding or dropping point on the seat map and the ticket. */
    public record PointDetail(Long id, String name, String address, String landmark, LocalDateTime at) {
    }

    /** One bus on the search results. */
    public record SearchResult(Long tripId, Long ownerId, String travelsName, String busName, String typeLabel,
                               boolean ac, boolean hasSleeper, boolean hasSeater, int fromSeq, int toSeq,
                               LocalDateTime departureAt, LocalDateTime arrivalAt, long durationMinutes,
                               Double ratingAvg, long ratingCount, BigDecimal minFare, int seatsLeft,
                               int singleSeatsLeft, List<String> amenities, List<PointBrief> boardingPoints,
                               List<PointBrief> droppingPoints) {
    }

    /** A seat on the customer's seat map. fare is null (and state SOLD) when the class has no price for the pair. */
    public record MapSeat(String label, int deck, int row, int col, String kind, String berth, String seatClass,
                          String pairLabel, BigDecimal fare, String state) {
    }

    public record StopBrief(int seq, String placeName) {
    }

    public record PhotoBrief(Long id, String path) {
    }

    /** until: the last moment this slab applies; null for the last slab. */
    public record SlabView(int minHours, int percent, LocalDateTime until) {
    }

    public record SeatMap(Long tripId, String travelsName, String busName, String typeLabel, LocalDate travelDate,
                          int fromSeq, int toSeq, String fromPlace, String toPlace, LocalDateTime departureAt,
                          LocalDateTime arrivalAt, int maxSeatsPerBooking, BigDecimal gstPercent, List<String> amenities,
                          List<PhotoBrief> photos, Double ratingAvg, long ratingCount, List<StopBrief> stops,
                          List<PointDetail> boardingPoints, List<PointDetail> droppingPoints, List<MapSeat> seats,
                          List<SlabView> cancellationSlabs) {
    }

    public record PassengerView(String seatLabel, String seatClass, String name, int age, String gender,
                                BigDecimal fare, BigDecimal gst, String status, BigDecimal cancelCharge,
                                BigDecimal refundAmount) {
    }

    public record RefundView(UUID id, BigDecimal amount, String status, LocalDateTime createdAt) {
    }

    public record ReviewView(int rating, String comment) {
    }

    public record TicketView(UUID id, String ticketNo, String status, Long tripId, String tripStatus,
                             String travelsName, String busName, String typeLabel, LocalDate travelDate,
                             String fromPlace, String toPlace, PointDetail boardingPoint, PointDetail droppingPoint,
                             LocalDateTime holdExpiresAt, BigDecimal fareTotal, BigDecimal gstPercent,
                             BigDecimal gstTotal, BigDecimal totalAmount, String contactPhone, String contactEmail,
                             String extraPhone, List<PassengerView> passengers, List<SlabView> cancellationSlabs,
                             List<RefundView> refunds, String registrationNumber, String driverName,
                             String driverPhone, String conductorName, String conductorPhone, boolean canCancel,
                             boolean canReview, ReviewView review, LocalDateTime createdAt,
                             LocalDateTime confirmedAt) {
    }

    /** What the browser opens Razorpay Checkout with. */
    public record PayView(UUID paymentId, UUID ticketId, String keyId, String orderId, BigDecimal amount,
                          long amountPaise, String currency, String merchantName, String description,
                          long timeoutSeconds) {
    }

    public record QuoteSeat(String seatLabel, BigDecimal fare, BigDecimal gst, int chargePercent, BigDecimal charge,
                            BigDecimal refund) {
    }

    public record CancelQuote(List<QuoteSeat> seats, BigDecimal totalRefund, double hoursBeforeBoarding) {
    }

    /** A payout; the bank and UPI fields are null in the owner's own view. */
    public record PayoutView(Long id, Long tripId, LocalDate travelDate, String routeName, String busName,
                             BigDecimal faresTravelled, BigDecimal chargesKept, BigDecimal gross,
                             BigDecimal commissionPercent, BigDecimal commission, BigDecimal net, String status,
                             String utr, LocalDateTime paidAt, LocalDateTime createdAt, Long ownerId,
                             String ownerCode, String travelsName, String upiId, String bankAccountHolder,
                             String bankAccountNumber, String bankIfsc, String bankName) {
    }
}
