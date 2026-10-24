package com.travelplatform.service.dispatch;

/**
 * Where a driver stands for City rides right now. Only an IDLE driver is a candidate in a round;
 * the IDLE -> OFFERED step is a compare-and-set, which is what stops two rounds (or two rides in
 * one round) handing the same driver two offers.
 */
public enum DriverState {
    /** Free: may be offered a ride. */
    IDLE,
    /** Holding one offer until its deadline. */
    OFFERED,
    /** On a ride, or committed to one today. */
    BUSY
}
