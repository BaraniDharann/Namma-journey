package com.travelplatform.dto;

import com.travelplatform.entity.OwnerStatus;

import java.time.LocalDateTime;
import java.util.List;

/** One row of the admin's owner-application list. */
public record AdminOwnerSummary(Long id, String ownerCode, OwnerStatus status, String travelsName,
                                List<String> cities, LocalDateTime submittedAt, long hoursWaiting,
                                boolean overdue) {
}
