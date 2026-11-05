package com.travelplatform.repository;

import com.travelplatform.entity.BusDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BusDocumentRepository extends JpaRepository<BusDocument, BusDocument.Key> {

    List<BusDocument> findByBusIdOrderByTypeAsc(Long busId);

    /** Documents of approved buses that expire on this date (the owner is reminded 15 days ahead). */
    @Query("SELECT d FROM BusDocument d WHERE d.expiresOn = :date AND EXISTS "
            + "(SELECT 1 FROM Bus b WHERE b.id = d.busId AND b.status = com.travelplatform.entity.Bus.Status.APPROVED)")
    List<BusDocument> findApprovedExpiringOn(@Param("date") LocalDate date);
}
