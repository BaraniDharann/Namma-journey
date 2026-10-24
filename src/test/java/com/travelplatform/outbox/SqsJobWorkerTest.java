package com.travelplatform.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SqsJobWorkerTest {

    private static final String QUEUE = "https://sqs.ap-south-1.amazonaws.com/123/namma-jobs";

    private SqsClient sqs;
    private JobHandler handler;
    private SqsJobWorker worker;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        sqs = mock(SqsClient.class);
        handler = mock(JobHandler.class);
        org.mockito.Mockito.when(handler.type()).thenReturn("email");
        worker = new SqsJobWorker(sqs, QUEUE, new JobRunner(List.of(handler)), objectMapper);
    }

    private static Message message(String body) {
        return Message.builder().messageId("m-1").receiptHandle("rh-1").body(body).build();
    }

    @Test
    @DisplayName("the transport's message is what the worker runs: id, type and payload round-trip")
    void roundTrip() throws Exception {
        new SqsOutboxTransport(sqs, QUEUE, objectMapper).deliver(new OutboxJob(42, "email", "{\"to\":\"a@b.c\"}", 1));
        org.mockito.ArgumentCaptor<SendMessageRequest> sent = org.mockito.ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqs).sendMessage(sent.capture());
        assertEquals(QUEUE, sent.getValue().queueUrl());

        worker.handle(message(sent.getValue().messageBody()));

        verify(handler).handle("{\"to\":\"a@b.c\"}");
        verify(sqs).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    @DisplayName("a failed job stays on the queue for SQS to retry and eventually dead-letter")
    void failureLeavesMessage() throws Exception {
        doThrow(new IllegalStateException("smtp down")).when(handler).handle(anyString());

        worker.handle(message("{\"id\":1,\"type\":\"email\",\"payload\":\"{}\"}"));

        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    @DisplayName("an unreadable message is dropped: no retry could ever succeed")
    void garbageIsDropped() throws Exception {
        worker.handle(message("not json"));

        verify(handler, never()).handle(anyString());
        verify(sqs).deleteMessage(any(DeleteMessageRequest.class));
    }
}
