package com.travelplatform.repository;

import com.travelplatform.entity.DriverDuesPayment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DriverDuesPaymentRepository extends JpaRepository<DriverDuesPayment, Long> {

    List<DriverDuesPayment> findByDriverIdOrderBySubmittedAtDesc(Long driverId, Pageable page);

    List<DriverDuesPayment> findByStatusOrderBySubmittedAtAsc(DriverDuesPayment.Status status);

    /** A UTR already claimed (pending or confirmed) cannot be claimed again; V24's unique index backs this. */
    @Query("SELECT COUNT(p) > 0 FROM DriverDuesPayment p WHERE UPPER(p.utr) = UPPER(:utr) AND p.status <> 'REJECTED'")
    boolean utrInUse(@Param("utr") String utr);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM DriverDuesPayment p WHERE p.driverId = :driverId AND p.status = 'PENDING'")
    BigDecimal pendingTotal(@Param("driverId") Long driverId);

    /** PENDING -> decided, only once: a second press (Telegram and website at once) updates nothing. */
    @Transactional
    @Modifying
    @Query("UPDATE DriverDuesPayment p SET p.status = :to, p.decidedAt = :at, p.decidedBy = :by "
            + "WHERE p.id = :id AND p.status = 'PENDING'")
    int decide(@Param("id") Long id, @Param("to") DriverDuesPayment.Status to,
               @Param("at") LocalDateTime at, @Param("by") String by);

    @Transactional
    @Modifying
    @Query("UPDATE DriverDuesPayment p SET p.telegramMessageId = :messageId WHERE p.id = :id")
    int setTelegramMessageId(@Param("id") Long id, @Param("messageId") Long messageId);
}
