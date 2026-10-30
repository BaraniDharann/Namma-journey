package com.travelplatform.repository;

import com.travelplatform.entity.RideSafetySession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The single-column updates below exist because the location feed and the after-send callbacks
 * run outside the monitor's transaction. Loading the row and saving it back from there would
 * merge a stale copy over whatever the monitor or an SOS wrote in between - an SOS flag
 * cleared by a GPS ping. Each of these touches one column and nothing else.
 */
@Repository
public interface RideSafetySessionRepository extends JpaRepository<RideSafetySession, UUID> {
    List<RideSafetySession> findByEndedAtIsNull();
    Optional<RideSafetySession> findByShareToken(String shareToken);
    Optional<RideSafetySession> findFirstByUserIdAndEndedAtIsNullOrderByStartedAtDesc(UUID userId);

    @Transactional
    @Modifying
    @Query("update RideSafetySession s set s.lastPointAt = :at where s.bookingId = :id")
    int markPoint(@Param("id") UUID bookingId, @Param("at") LocalDateTime at);

    @Transactional
    @Modifying
    @Query("update RideSafetySession s set s.userLiveMessageId = :messageId where s.bookingId = :id")
    int setUserLiveMessageId(@Param("id") UUID bookingId, @Param("messageId") Long messageId);

    @Transactional
    @Modifying
    @Query("update RideSafetySession s set s.opsLiveMessageId = :messageId where s.bookingId = :id")
    int setOpsLiveMessageId(@Param("id") UUID bookingId, @Param("messageId") Long messageId);

    /** Returns 1 only for the call that actually ended it, so the end is announced once. */
    @Transactional
    @Modifying
    @Query("update RideSafetySession s set s.endedAt = :at where s.bookingId = :id and s.endedAt is null")
    int end(@Param("id") UUID bookingId, @Param("at") LocalDateTime at);
}
