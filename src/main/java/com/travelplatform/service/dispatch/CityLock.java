package com.travelplatform.service.dispatch;

/**
 * Which server runs a city's matching rounds. Exactly one at a time, or a ride could be offered
 * to two drivers by two servers. A lock expires a few seconds after its holder stops renewing it,
 * so a dead server's cities are taken over without anyone having to notice it died.
 */
public interface CityLock {

    /** Takes the city if nobody holds it. */
    boolean tryAcquire(long cityId);

    /** Extends a lock this server holds; false means it was lost (expired and taken, or never held). */
    boolean renew(long cityId);

    /** Lets the city go, if this server holds it. */
    void release(long cityId);

    /** "This server is alive", once per tick; what {@link #liveServers()} counts. */
    void heartbeat();

    /** Servers that beat recently, at least 1. Spreads cities: each keeps about its share. */
    int liveServers();
}
