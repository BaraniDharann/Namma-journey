package com.travelplatform.repository;

import com.travelplatform.entity.OwnerAadhaarQr;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OwnerAadhaarQrRepository extends JpaRepository<OwnerAadhaarQr, Long> {
}
