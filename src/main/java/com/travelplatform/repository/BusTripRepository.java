package com.travelplatform.repository;

import com.travelplatform.entity.BusTrip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BusTripRepository extends JpaRepository<BusTrip, Long> {

    List<BusTrip> findByOwnerIdAndTravelDateBetweenOrderByDepartureAtAscIdAsc(Long ownerId, LocalDate from, LocalDate to);

    /**
     * Creates the route's trip for a date unless it already exists (uq_bus_trips_route_date), so trip
     * creation can run any number of times. Returns 1 if a trip was created.
     */
    @Modifying
    @Query(value = "INSERT INTO bus_trips (route_id, bus_id, owner_id, travel_date, departure_at, arrival_at, status, created_at) "
            + "VALUES (:routeId, :busId, :ownerId, :travelDate, :departureAt, :arrivalAt, 'SCHEDULED', now()) "
            + "ON CONFLICT (route_id, travel_date) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("routeId") Long routeId,
                       @Param("busId") Long busId,
                       @Param("ownerId") Long ownerId,
                       @Param("travelDate") LocalDate travelDate,
                       @Param("departureAt") LocalDateTime departureAt,
                       @Param("arrivalAt") LocalDateTime arrivalAt);

    /**
     * Search: SCHEDULED trips of PUBLISHED routes running between {@code start} and {@code end} that
     * stop at {@code fromPlace} and later at {@code toPlace} and have a fare for that pair, as
     * [trip, fromSeq, toSeq] rows. Boarding times are checked by the caller.
     */
    @Query("SELECT t, sf.seq, st.seq FROM BusTrip t, BusRoute r, BusRouteStop sf, BusRouteStop st "
            + "WHERE r.id = t.routeId AND sf.routeId = r.id AND st.routeId = r.id "
            + "AND sf.placeId = :fromPlace AND st.placeId = :toPlace AND sf.seq < st.seq "
            + "AND t.status = com.travelplatform.entity.BusTrip.Status.SCHEDULED "
            + "AND r.status = com.travelplatform.entity.BusRoute.Status.PUBLISHED "
            + "AND t.travelDate BETWEEN :start AND :end "
            + "AND EXISTS (SELECT 1 FROM BusRouteFare f WHERE f.routeId = r.id AND f.fromSeq = sf.seq AND f.toSeq = st.seq)")
    List<Object[]> searchCandidates(@Param("fromPlace") Long fromPlace, @Param("toPlace") Long toPlace,
                                    @Param("start") LocalDate start, @Param("end") LocalDate end);

    /**
     * Locks the trip row. Seat holds on one trip take it first and so run one after another: two
     * customers inserting the same seat at once would otherwise each wait on the other's row in the
     * exclusion constraint, and Postgres would abort one with a deadlock instead of a clean 409.
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM BusTrip t WHERE t.id = :id")
    java.util.Optional<BusTrip> lockById(@Param("id") Long id);

    /** Trips whose bus has left its first stop. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE BusTrip t SET t.status = com.travelplatform.entity.BusTrip.Status.DEPARTED "
            + "WHERE t.status = com.travelplatform.entity.BusTrip.Status.SCHEDULED AND t.departureAt <= :now")
    int markDeparted(@Param("now") LocalDateTime now);

    @Query("SELECT t.id FROM BusTrip t WHERE t.status = com.travelplatform.entity.BusTrip.Status.DEPARTED "
            + "AND t.arrivalAt <= :arrivedBy")
    List<Long> findArrivedIds(@Param("arrivedBy") LocalDateTime arrivedBy);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE BusTrip t SET t.status = com.travelplatform.entity.BusTrip.Status.COMPLETED "
            + "WHERE t.id IN :ids AND t.status = com.travelplatform.entity.BusTrip.Status.DEPARTED")
    int markCompleted(@Param("ids") java.util.Collection<Long> ids);

    /** COMPLETED trips that arrived after {@code since} and have no payout yet. */
    @Query("SELECT t.id FROM BusTrip t WHERE t.status = com.travelplatform.entity.BusTrip.Status.COMPLETED "
            + "AND t.arrivalAt >= :since AND NOT EXISTS (SELECT 1 FROM BusOwnerPayout p WHERE p.tripId = t.id) "
            + "ORDER BY t.arrivalAt")
    List<Long> findCompletedWithoutPayout(@Param("since") LocalDateTime since);
}
