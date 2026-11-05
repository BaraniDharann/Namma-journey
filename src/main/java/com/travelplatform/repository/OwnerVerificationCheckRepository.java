package com.travelplatform.repository;

import com.travelplatform.entity.OwnerVerificationCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OwnerVerificationCheckRepository
        extends JpaRepository<OwnerVerificationCheck, OwnerVerificationCheck.Key> {

    List<OwnerVerificationCheck> findByOwnerId(Long ownerId);

    void deleteByOwnerId(Long ownerId);
}
