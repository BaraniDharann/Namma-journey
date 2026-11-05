package com.travelplatform.repository;

import com.travelplatform.entity.DriverVerificationCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DriverVerificationCheckRepository
        extends JpaRepository<DriverVerificationCheck, DriverVerificationCheck.Key> {

    List<DriverVerificationCheck> findByDriverId(Long driverId);
}
