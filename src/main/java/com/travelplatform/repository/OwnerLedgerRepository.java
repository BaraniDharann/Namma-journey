package com.travelplatform.repository;

import com.travelplatform.entity.DriverLedgerEntry;
import com.travelplatform.entity.OwnerLedgerEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface OwnerLedgerRepository extends JpaRepository<OwnerLedgerEntry, Long> {

    /** Serialises transfers for one owner; the key is offset so it never meets a driver's lock. */
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(35, CAST(:ownerId AS integer))", nativeQuery = true)
    Integer lockOwner(@Param("ownerId") long ownerId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM OwnerLedgerEntry e WHERE e.ownerId = :ownerId")
    BigDecimal balanceOf(@Param("ownerId") Long ownerId);

    List<OwnerLedgerEntry> findByOwnerIdOrderByCreatedAtDescIdDesc(Long ownerId, Pageable page);

    boolean existsByBookingIdAndType(UUID bookingId, DriverLedgerEntry.Type type);

    boolean existsByReference(String reference);

    /** [ownerId, balance, ride count] for every owner with City-ride history (the platform's payout screen). */
    @Query("SELECT e.ownerId, SUM(e.amount), SUM(CASE WHEN e.bookingId IS NULL THEN 0 ELSE 1 END) "
            + "FROM OwnerLedgerEntry e GROUP BY e.ownerId")
    List<Object[]> balances();
}
