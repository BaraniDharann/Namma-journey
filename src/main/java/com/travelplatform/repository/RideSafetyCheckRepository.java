package com.travelplatform.repository;

import com.travelplatform.entity.RideSafetyCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RideSafetyCheckRepository extends JpaRepository<RideSafetyCheck, Long> {
    /** The question currently waiting for an answer on this trip, if any. */
    Optional<RideSafetyCheck> findFirstByBookingIdAndResponseIsNullOrderBySentAtDesc(UUID bookingId);

    /** Set after the Telegram send returns; one column, so it cannot undo an answer saved meanwhile. */
    @Transactional
    @Modifying
    @Query("update RideSafetyCheck c set c.telegramMessageId = :messageId where c.id = :id")
    int setTelegramMessageId(@Param("id") Long checkId, @Param("messageId") Long messageId);
}
