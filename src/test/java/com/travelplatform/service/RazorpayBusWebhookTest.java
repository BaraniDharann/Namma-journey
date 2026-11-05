package com.travelplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.config.RazorpayProperties;
import com.travelplatform.entity.BusPayment;
import com.travelplatform.entity.BusRefund;
import com.travelplatform.repository.BusPaymentRepository;
import com.travelplatform.repository.BusRefundRepository;
import com.travelplatform.repository.PaymentRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.service.bus.BusBookingService;
import com.travelplatform.service.bus.BusRefundJobHandler;
import com.travelplatform.service.bus.BusRefunds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The one Razorpay webhook passes bus orders and refunds to the bus module; the bus refund job. */
class RazorpayBusWebhookTest {

    private PaymentRepository carPayments;
    private BusBookingService busBooking;
    private BusRefunds busRefunds;
    private RazorpayPaymentService service;

    @BeforeEach
    void setUp() {
        carPayments = mock(PaymentRepository.class);
        busBooking = mock(BusBookingService.class);
        busRefunds = mock(BusRefunds.class);
        service = new RazorpayPaymentService(carPayments, mock(TravelBookingRepository.class), mock(PaymentService.class),
                mock(RazorpayGateway.class), new RazorpayProperties(), new ObjectMapper(),
                new org.springframework.transaction.support.TransactionTemplate(new NoOpTransactionManager()));
        service.setBusModule(busBooking, busRefunds);
    }

    private static byte[] json(String s) {
        return s.replace('\'', '"').getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("payment.captured / order.paid / payment.failed for an order the car side does not know go to the bus module")
    void ordersGoToBus() {
        when(busBooking.onPaymentCaptured("order_b", "pay_b", 68250L)).thenReturn(true);
        service.handleWebhook(json("{'event':'payment.captured','payload':{'payment':{'entity':"
                + "{'id':'pay_b','order_id':'order_b','amount':68250}}}}"));
        verify(busBooking).onPaymentCaptured("order_b", "pay_b", 68250L);

        service.handleWebhook(json("{'event':'payment.failed','payload':{'payment':{'entity':"
                + "{'id':'pay_c','order_id':'order_c','amount':100}}}}"));
        verify(busBooking).onPaymentFailed("order_c");
    }

    @Test
    @DisplayName("refund.processed / refund.failed for a bus payment update bus_refunds, not the car payment")
    void refundsGoToBus() {
        when(carPayments.findByRazorpayPaymentId("pay_b")).thenReturn(Optional.empty());
        when(busRefunds.isBusPayment("pay_b")).thenReturn(true);
        service.handleWebhook(json("{'event':'refund.processed','payload':{'refund':{'entity':"
                + "{'id':'rfnd_1','payment_id':'pay_b','amount':61750}}}}"));
        verify(busRefunds).processed("pay_b", "rfnd_1", 61750L);
        service.handleWebhook(json("{'event':'refund.failed','payload':{'refund':{'entity':"
                + "{'id':'rfnd_2','payment_id':'pay_b','amount':100}}}}"));
        verify(busRefunds).failed("pay_b", "rfnd_2", 100L);

        when(busRefunds.isBusPayment("pay_car")).thenReturn(false);
        service.handleWebhook(json("{'event':'refund.processed','payload':{'refund':{'entity':"
                + "{'id':'rfnd_3','payment_id':'pay_car','amount':100}}}}"));
        verify(busRefunds, never()).processed(eq("pay_car"), anyString(), anyLong());
    }

    @Test
    @DisplayName("the bus refund job asks Razorpay for the partial amount once and records the refund id")
    void refundJob() throws Exception {
        BusRefundRepository refunds = mock(BusRefundRepository.class);
        BusPaymentRepository payments = mock(BusPaymentRepository.class);
        RazorpayGateway gateway = mock(RazorpayGateway.class);
        ObjectMapper json = new ObjectMapper();
        BusRefundJobHandler handler = new BusRefundJobHandler(refunds, payments, gateway, json);

        BusPayment p = new BusPayment();
        p.setId(UUID.randomUUID());
        p.setRazorpayPaymentId("pay_b");
        BusRefund r = new BusRefund();
        r.setId(UUID.randomUUID());
        r.setTicketId(UUID.randomUUID());
        r.setPaymentId(p.getId());
        r.setAmount(new BigDecimal("617.50"));
        when(refunds.findById(r.getId())).thenReturn(Optional.of(r));
        when(payments.findById(p.getId())).thenReturn(Optional.of(p));
        when(gateway.refund(eq("pay_b"), eq(61750L), anyMap())).thenReturn("rfnd_9");

        String payload = json.writeValueAsString(new BusRefundJobHandler.RefundRequest(r.getId()));
        handler.handle(payload);
        assertEquals("rfnd_9", r.getRazorpayRefundId());
        handler.handle(payload); // a repeat does nothing: the refund id is already there
        verify(gateway).refund(eq("pay_b"), eq(61750L), anyMap());
        verify(refunds).save(any());
    }
}
