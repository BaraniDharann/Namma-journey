package com.travelplatform.repository;

import com.travelplatform.entity.Otp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface OtpRepository extends JpaRepository<Otp, Long> {
    Optional<Otp> findTopByEmailAndVerifiedFalseAndExpiryTimeAfterOrderByCreatedAtDesc(
        String email, LocalDateTime currentTime);
    
    void deleteByEmailAndVerifiedTrue(String email);
    void deleteByExpiryTimeBefore(LocalDateTime currentTime);
}
