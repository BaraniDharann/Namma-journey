package com.travelplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TravelBookingResponse {
    private UUID bookingId;
    private String userName;
    private String userPhone;
    private String fromPlace;
    private String toPlace;
    private LocalDate fromDate;
    private LocalDate toDate;
    private Integer travelDays;
    private Integer travelMembers;
    private String acType;
    private Double fromLat;
    private Double fromLon;
    private Double toLat;
    private Double toLon;
    private Double distanceKm;
    private Long estimatedTimeMinutes;
    private String estimatedTimeFormatted;
    private java.math.BigDecimal totalAmount;
    private String routeDetails;
    private LocalDateTime bookingDate;
    private String status;
    private String bookingType;
    private Integer bookingHours;
    private java.math.BigDecimal pricePerHourAtBooking;
    private String driverEndTripPhoto;
    private DriverDetailsResponse driver;
    private String dispatchMode;
    private String dispatchState;
    private LocalDateTime offerExpiresAt;
    /**
     * Seconds left on the driver's offer, worked out here. offerExpiresAt is an Indian wall-clock
     * time with no zone, which a browser in another zone would read hours off; a countdown in
     * seconds needs no clock agreement at all.
     */
    private Long offerSecondsLeft;
    private Double offerDistanceKm;
    private LocalDateTime tripEndedAt;
    private String serviceMode;
    private String vehicleType;

    /** Copies the Ride Now fields; shared by every service that maps a booking. */
    public TravelBookingResponse withDispatch(com.travelplatform.entity.TravelBooking booking) {
        this.dispatchMode = booking.getDispatchMode() == null ? null : booking.getDispatchMode().name();
        this.dispatchState = booking.getDispatchState() == null ? null : booking.getDispatchState().name();
        this.offerExpiresAt = booking.getOfferExpiresAt();
        this.offerSecondsLeft = booking.getOfferExpiresAt() == null ? null
                : Math.max(0, java.time.Duration.between(
                        LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")), booking.getOfferExpiresAt()).getSeconds());
        this.offerDistanceKm = booking.getOfferDistanceKm();
        this.tripEndedAt = booking.getTripEndedAt();
        this.serviceMode = booking.getServiceMode() == null ? null : booking.getServiceMode().name();
        this.vehicleType = booking.getVehicleType() == null ? null : booking.getVehicleType().name();
        return this;
    }
}
