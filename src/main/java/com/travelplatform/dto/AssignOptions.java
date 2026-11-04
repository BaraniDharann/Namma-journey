package com.travelplatform.dto;

import java.util.List;

/** What an owner may put on one Trip: their free approved drivers and their free cars that suit it. */
public record AssignOptions(List<DriverOption> drivers, List<VehicleOption> vehicles) {

    /** {@code defaultVehicleId} is the driver's usual car, pre-selected when they are picked. */
    public record DriverOption(Long id, String name, Long defaultVehicleId) {
    }

    public record VehicleOption(Long id, String registrationNumber, String model, int seats, boolean ac) {
    }
}
