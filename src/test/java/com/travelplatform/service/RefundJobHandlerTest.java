package com.travelplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.entity.Payment;
import com.travelplatform.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefundJobHandlerTest {

    private PaymentRepository payments;
    private RazorpayGateway gateway;
    private RefundJobHandler handler;
    private Payment payment;
    private String job;

    @BeforeEach
    void setUp() throws Exception {
        payments = mock(PaymentRepository.class);
        gateway = mock(RazorpayGateway.class);
        ObjectMapper objectMapper = new ObjectMapper();
        handler = new RefundJobHandler(payments, gateway, objectMapper);

        payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setBookingId(UUID.randomUUID());
        payment.setAmount(new BigDecimal("1200.00"));
        payment.setPaymentMethod(Payment.PaymentMethod.RAZORPAY);
        payment.setStatus(Payment.PaymentStatus.REFUND_PENDING);
        payment.setRazorpayPaymentId("pay_1");
        when(payments.findById(payment.getId())).thenReturn(Optional.of(payment));
        job = objectMapper.writeValueAsString(new RefundJobHandler.RefundRequest(payment.getId()));
    }

    @Test
    @DisplayName("refunds the full fare in paise and remembers the refund id")
    void refundsFullFare() throws Exception {
        when(gateway.refund(eq("pay_1"), eq(120000L), anyMap())).thenReturn("rfnd_1");

        handler.handle(job);

        assertEquals("rfnd_1", payment.getRazorpayRefundId());
        verify(payments).save(payment);
    }

    @Test
    @DisplayName("a repeat after a refund was requested does not refund twice")
    void repeatIsSkipped() throws Exception {
        payment.setRazorpayRefundId("rfnd_1");
        handler.handle(job);
        verify(gateway, never()).refund(anyString(), anyLong(), anyMap());
    }

    @Test
    @DisplayName("already refunded (webhook won the race) is skipped")
    void refundedIsSkipped() throws Exception {
        payment.setStatus(Payment.PaymentStatus.REFUNDED);
        handler.handle(job);
        verify(gateway, never()).refund(anyString(), anyLong(), anyMap());
    }

    @Test
    @DisplayName("Razorpay saying 'already fully refunded' counts as done")
    void alreadyFullyRefundedIsDone() throws Exception {
        when(gateway.refund(anyString(), anyLong(), anyMap())).thenReturn(null);
        handler.handle(job);
        assertNull(payment.getRazorpayRefundId());
        verify(payments, never()).save(any());
    }

    @Test
    @DisplayName("Razorpay being down throws, so the outbox retries")
    void gatewayFailureRetries() {
        when(gateway.refund(anyString(), anyLong(), anyMap())).thenThrow(new org.springframework.web.client.ResourceAccessException("timeout"));
        assertThrows(Exception.class, () -> handler.handle(job));
    }
}
