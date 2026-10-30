package com.travelplatform.repository;

import com.travelplatform.entity.DriverLedgerEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface DriverLedgerRepository extends JpaRepository<DriverLedgerEntry, Long> {

    /**
     * Serialises payouts for one driver until the transaction ends: the balance check and the
     * insert in DriverLedgerService.recordTransfer must not interleave with another one.
     */
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(:driverId)", nativeQuery = true)
    Integer lockDriver(@Param("driverId") long driverId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM DriverLedgerEntry e WHERE e.driverId = :driverId")
    BigDecimal balanceOf(@Param("driverId") Long driverId);

    List<DriverLedgerEntry> findByDriverIdOrderByCreatedAtDescIdDesc(Long driverId, Pageable page);

    boolean existsByBookingIdAndType(UUID bookingId, DriverLedgerEntry.Type type);

    /** A transfer already recorded under this UTR (references are stored upper-case). */
    boolean existsByReference(String reference);

    /** [driverId, balance] for every driver with any ledger history, for the owner's payout screen. */
    @Query("SELECT e.driverId, SUM(e.amount) FROM DriverLedgerEntry e GROUP BY e.driverId")
    List<Object[]> balances();
}
