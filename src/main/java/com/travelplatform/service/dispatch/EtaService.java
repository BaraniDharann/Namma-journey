package com.travelplatform.service.dispatch;

import java.util.Map;

/**
 * How long each candidate driver needs to reach a pickup. One call per ride, all its candidates at
 * once, because the road-network version (OSRM's {@code /table}, phase C) answers a whole row of
 * the matrix in one request for the price of one.
 */
public interface EtaService {

    /**
     * @return seconds to the pickup per driver. A driver missing from the result has no route
     *         (an island, a closed road) and must not be offered the ride.
     */
    Map<Long, Integer> etaSeconds(GeoPoint pickup, Map<Long, GeoPoint> drivers);
}
