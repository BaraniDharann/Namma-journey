package com.travelplatform.repository;

import com.travelplatform.entity.BusSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface BusSeatRepository extends JpaRepository<BusSeat, Long> {

    List<BusSeat> findByBusIdOrderByDeckAscRowNoAscColNoAsc(Long busId);

    long countByBusId(Long busId);

    List<BusSeat> findByBusIdIn(Collection<Long> busIds);

    /** Seats per bus, as [busId, count] rows. */
    @Query("SELECT s.busId, COUNT(s) FROM BusSeat s WHERE s.busId IN :busIds GROUP BY s.busId")
    List<Object[]> countByBusIds(@Param("busIds") Collection<Long> busIds);

    /** Runs at once (not at flush), so the replacement seats can reuse the same labels. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM BusSeat s WHERE s.busId = :busId")
    int deleteByBusId(@Param("busId") Long busId);
}
