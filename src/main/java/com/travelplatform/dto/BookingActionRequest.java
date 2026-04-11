package com.travelplatform.dto;

public class BookingActionRequest {
    private String action;

    public BookingActionRequest() {}

    public BookingActionRequest(String action) {
        this.action = action;
    }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
}
