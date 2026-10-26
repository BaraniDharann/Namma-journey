package com.travelplatform.service.dispatch;

import com.travelplatform.entity.Driver;
import com.travelplatform.entity.TravelBooking;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The one place the services tell the driver state store what just happened to a driver, so the
 * rules live here once instead of in every service: writes land after commit, and only a booking
 * that occupies the driver <em>today</em> moves their City-ride state. Accepting a Trip for next
 * month must not take a driver out of today's City rides, nor cancelling one put them back in.
 */
@Component
public class DriverStateUpdates {

    private final DriverStateStore store;

    /** Booking dates are Indian calendar days. */
    private Clock clock = Clock.system(ZoneId.of("Asia/Kolkata"));

    public DriverStateUpdates(DriverStateStore store) {
        this.store = store;
    }

    /** Tests pin "today" to their own clock; a ride dated by a fixed clock must not age out on the real one. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    /** Vehicle, seats, City/Trip switches, online or offline. */
    public void profileChanged(Driver driver) {
        if (driver != null && driver.getId() != null) {
            AfterCommit.run("profile", () -> store.putProfile(driver));
        }
    }

    /** The driver accepted, was assigned, or started this booking: busy if it is today's. */
    public void took(TravelBooking booking) {
        Long driverId = booking.getDriverId();
        if (driverId != null && occupiesToday(booking)) {
            AfterCommit.run("busy", () -> store.setState(driverId, DriverState.BUSY));
        }
    }

    /** This booking no longer holds the driver (ended, cancelled, declined, timed out): free again if it was today's. */
    public void freed(Long driverId, TravelBooking booking) {
        if (driverId != null && occupiesToday(booking)) {
            AfterCommit.run("idle", () -> store.setState(driverId, DriverState.IDLE));
        }
    }

    private boolean occupiesToday(TravelBooking booking) {
        LocalDate today = LocalDate.now(clock);
        return (booking.getFromDate() == null || !booking.getFromDate().isAfter(today))
                && (booking.getToDate() == null || !booking.getToDate().isBefore(today));
    }
}
