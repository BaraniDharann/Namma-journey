package com.travelplatform.entity;

/** Where an owner's application stands. Only APPROVED owners can log in. */
public enum OwnerStatus {
    PENDING_REVIEW,
    APPROVED,
    REJECTED
}
