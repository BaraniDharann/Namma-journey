package com.travelplatform.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.LinkedHashMap;
import java.util.Map;

/** Sends a due job to SQS as {@code {"id", "type", "payload"}}; {@link SqsJobWorker} runs it. */
class SqsOutboxTransport implements OutboxTransport {

    private final SqsClient sqs;
    private final String queueUrl;
    private final ObjectMapper objectMapper;

    SqsOutboxTransport(SqsClient sqs, String queueUrl, ObjectMapper objectMapper) {
        this.sqs = sqs;
        this.queueUrl = queueUrl;
        this.objectMapper = objectMapper;
    }

    @Override
    public void deliver(OutboxJob job) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", job.id());
        body.put("type", job.type());
        body.put("payload", job.payload());
        sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(objectMapper.writeValueAsString(body))
                .build());
    }
}
