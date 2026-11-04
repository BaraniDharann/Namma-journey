package com.travelplatform.repository;

import com.travelplatform.entity.BusRefund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BusRefundRepository extends JpaRepository<BusRefund, UUID> {

    List<BusRefund> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);

    List<BusRefund> findByStatusOrderByCreatedAtAsc(BusRefund.Status status);
}
