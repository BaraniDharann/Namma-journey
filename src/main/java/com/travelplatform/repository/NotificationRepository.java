package com.travelplatform.repository;

import com.travelplatform.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByRecipientIdAndRecipientRoleOrderByCreatedAtDesc(String recipientId, String recipientRole);
    long countByRecipientIdAndRecipientRoleAndReadFalse(String recipientId, String recipientRole);
    List<Notification> findByRecipientIdAndRecipientRole(String recipientId, String recipientRole);

    /** Dedupe guard for notifications that must fire once per booking, however often the trigger is called. */
    boolean existsByBookingIdAndType(String bookingId, String type);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.recipientId = :recipientId AND n.recipientRole = :role AND n.read = false")
    int markAllAsReadBulk(@Param("recipientId") String recipientId, @Param("role") String role);
}
