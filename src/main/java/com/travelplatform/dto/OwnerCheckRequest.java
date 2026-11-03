package com.travelplatform.dto;

import com.travelplatform.entity.OwnerVerificationCheck;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The admin's verdict on one checklist item. A FAILED verdict needs a note. */
public record OwnerCheckRequest(@NotNull OwnerVerificationCheck.Result result,
                                @Size(max = 500) String note) {
}
