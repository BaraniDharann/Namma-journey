package com.travelplatform.repository;

import com.travelplatform.entity.BusPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusPaymentRepository extends JpaRepository<BusPayment, UUID> {

    List<BusPayment> findByTicketIdOrderByCreatedAtDesc(UUID ticketId);

    Optional<BusPayment> findByRazorpayOrderId(String razorpayOrderId);

    Optional<BusPayment> findByRazorpayPaymentId(String razorpayPaymentId);
}
