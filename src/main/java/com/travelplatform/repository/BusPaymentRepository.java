package com.travelplatform.repository;

import com.travelplatform.entity.BusPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusPaymentRepository extends JpaRepository<BusPayment, UUID> {

    List<BusPayment> findByTicketIdOrderByCreatedAtDesc(UUID ticketId);

    Optional<BusPayment> findByRazorpayOrderId(String razorpayOrderId);

    Optional<BusPayment> findByRazorpayPaymentId(String razorpayPaymentId);

    List<BusPayment> findByTicketIdInAndStatus(Collection<UUID> ticketIds, BusPayment.Status status);

    /**
     * Money that arrived but whose ticket was never confirmed and nothing was refunded: the
     * webhook and the checkout callback were both lost, or failed half way. The sweeper settles them.
     */
    @Query("SELECT p FROM BusPayment p, BusTicket t WHERE t.id = p.ticketId "
            + "AND p.status = com.travelplatform.entity.BusPayment.Status.CAPTURED "
            + "AND t.status IN (com.travelplatform.entity.BusTicket.Status.PENDING, "
            + "com.travelplatform.entity.BusTicket.Status.EXPIRED, com.travelplatform.entity.BusTicket.Status.SERVICE_CANCELLED) "
            + "AND NOT EXISTS (SELECT 1 FROM BusRefund r WHERE r.paymentId = p.id)")
    List<BusPayment> findCapturedUnsettled();
}
