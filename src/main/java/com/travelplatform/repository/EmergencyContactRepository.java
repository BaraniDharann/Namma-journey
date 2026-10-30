package com.travelplatform.repository;

import com.travelplatform.entity.EmergencyContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmergencyContactRepository extends JpaRepository<EmergencyContact, Long> {
    List<EmergencyContact> findByUserIdOrderByCreatedAtAsc(UUID userId);
    long countByUserId(UUID userId);
    Optional<EmergencyContact> findByInviteToken(String inviteToken);
    List<EmergencyContact> findByTelegramChatId(String telegramChatId);
}
