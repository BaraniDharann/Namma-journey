package com.travelplatform.repository;

import com.travelplatform.entity.Owner;
import com.travelplatform.entity.OwnerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OwnerRepository extends JpaRepository<Owner, Long> {
    Optional<Owner> findByEmail(String email);
    boolean existsByEmail(String email);

    Optional<Owner> findByEmailIgnoreCase(String email);

    Optional<Owner> findByOwnerCode(String ownerCode);

    boolean existsByPanHash(String panHash);

    Optional<Owner> findByPanHash(String panHash);

    List<Owner> findByStatusOrderBySubmittedAtAsc(OwnerStatus status);

    /** Pending applications past the reminder cutoff that have not been reminded about yet. */
    List<Owner> findByStatusAndSubmittedAtBeforeAndAdminRemindedAtIsNull(OwnerStatus status, LocalDateTime cutoff);

    Optional<Owner> findByResubmitTokenHash(String resubmitTokenHash);

    @Query(value = "select nextval('owner_code_seq')", nativeQuery = true)
    long nextOwnerCode();
}
