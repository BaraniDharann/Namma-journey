package com.travelplatform.repository;

import com.travelplatform.entity.BusReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusReviewRepository extends JpaRepository<BusReview, Long> {

    Optional<BusReview> findByTicketId(UUID ticketId);

    List<BusReview> findByTicketIdIn(Collection<UUID> ticketIds);

    /** Ratings per bus, as [busId, average, count] rows. */
    @Query("SELECT r.busId, AVG(r.rating), COUNT(r) FROM BusReview r WHERE r.busId IN :busIds GROUP BY r.busId")
    List<Object[]> statsByBusIds(@Param("busIds") Collection<Long> busIds);
}
