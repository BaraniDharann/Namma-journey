package com.travelplatform.service;

import com.travelplatform.entity.OwnerTripRate;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A TripRateService where every category of the platform owner costs the same, for tests about something else. */
final class TestTripRates {

    static final long PLATFORM_OWNER = 38L;

    private TestTripRates() {
    }

    static TripRateService flat(String perKm, String perHour) {
        TripRateService rates = mock(TripRateService.class);
        when(rates.platformOwnerId()).thenReturn(PLATFORM_OWNER);
        when(rates.rate(any(), any())).thenAnswer(i -> {
            OwnerTripRate r = new OwnerTripRate();
            r.setOwnerId(i.getArgument(0));
            r.setCategory(i.getArgument(1));
            r.setPerKm(new BigDecimal(perKm));
            r.setPerHour(new BigDecimal(perHour));
            return r;
        });
        return rates;
    }

    /** What the assignment query returns: a driver id and a car id. */
    static com.travelplatform.repository.DriverRepository.TripPair tripPair(long driverId, long vehicleId) {
        return new com.travelplatform.repository.DriverRepository.TripPair() {
            @Override
            public Long getDriverId() {
                return driverId;
            }

            @Override
            public Long getVehicleId() {
                return vehicleId;
            }
        };
    }
}
