package com.travelplatform.repository;

import com.travelplatform.entity.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {
    Optional<Driver> findByMobile(String mobile);
    Optional<Driver> findByEmail(String email);
    Optional<Driver> findByTelegramChatId(String telegramChatId);
    Optional<Driver> findByTelegramLinkToken(String telegramLinkToken);
    boolean existsByMobile(String mobile);
    boolean existsByEmail(String email);
    boolean existsByLicenseNumber(String licenseNumber);
    boolean existsByAadhaarNumber(String aadhaarNumber);
    
    @Query("SELECT d FROM Driver d WHERE d.status = 'ACTIVE' AND d.id NOT IN " +
           "(SELECT tb.driverId FROM TravelBooking tb WHERE tb.driverId IS NOT NULL " +
           "AND ((tb.fromDate <= :toDate AND tb.toDate >= :fromDate) " +
           "AND tb.status IN ('PENDING', 'CONFIRMED', 'STARTED')))")
    List<Driver> findAvailableDrivers(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    /**
     * Availability, minus a caller-supplied exclusion list - used when reassigning a trip
     * that one or more drivers have already declined.
     *
     * <p>The exclusion list must never be empty: {@code NOT IN ()} is not valid SQL. Callers
     * reach this method only after recording at least one rejection, so it always has a member.
     */
    @Query("SELECT d FROM Driver d WHERE d.status = 'ACTIVE' " +
           "AND d.id NOT IN :excludedDriverIds AND d.id NOT IN " +
           "(SELECT tb.driverId FROM TravelBooking tb WHERE tb.driverId IS NOT NULL " +
           "AND ((tb.fromDate <= :toDate AND tb.toDate >= :fromDate) " +
           "AND tb.status IN ('PENDING', 'CONFIRMED', 'STARTED')))")
    List<Driver> findAvailableDriversExcluding(@Param("fromDate") LocalDate fromDate,
                                               @Param("toDate") LocalDate toDate,
                                               @Param("excludedDriverIds") List<Long> excludedDriverIds);

    @Query("SELECT COUNT(tb) FROM TravelBooking tb WHERE tb.driverId = :driverId " +
           "AND tb.status IN ('PENDING', 'CONFIRMED', 'STARTED') " +
           "AND tb.fromDate <= :toDate AND tb.toDate >= :fromDate " +
           "AND (:excludeBookingId IS NULL OR tb.id <> :excludeBookingId)")
    long countOverlappingActiveBookings(@Param("driverId") Long driverId,
                                        @Param("fromDate") LocalDate fromDate,
                                        @Param("toDate") LocalDate toDate,
                                        @Param("excludeBookingId") java.util.UUID excludeBookingId);
}
