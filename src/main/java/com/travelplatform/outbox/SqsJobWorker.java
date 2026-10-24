package com.travelplatform.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;

/**
 * Runs jobs from the SQS queue on one background thread, long-polling so an idle queue costs
 * about three requests a minute.
 *
 * <p>A message is deleted only after its handler succeeds. A failure leaves it on the queue: it
 * reappears after the visibility timeout and is retried, and after the queue's
 * {@code maxReceiveCount} SQS moves it to the dead-letter queue (configured on the queue - see
 * DEPLOYMENT_CHECKLIST.md). A message this instance cannot even parse is deleted, since no
 * retry would ever succeed.
 */
public class SqsJobWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SqsJobWorker.class);

    /** Matches the outbox lease: longer than any handler takes. */
    private static final int VISIBILITY_SECONDS = (int) OutboxStore.LEASE.toSeconds();
    private static final int WAIT_SECONDS = 20;

    private final SqsClient sqs;
    private final String queueUrl;
    private final JobRunner runner;
    private final ObjectMapper objectMapper;

    private volatile boolean running;
    private Thread thread;

    public SqsJobWorker(SqsClient sqs, String queueUrl, JobRunner runner, ObjectMapper objectMapper) {
        this.sqs = sqs;
        this.queueUrl = queueUrl;
        this.runner = runner;
        this.objectMapper = objectMapper;
    }

    @Override
    public void start() {
        running = true;
        thread = new Thread(this::loop, "sqs-job-worker");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void loop() {
        while (running) {
            try {
                pollOnce();
            } catch (RuntimeException e) {
                if (!running) {
                    return;
                }
                log.warn("SQS receive failed, backing off: {}", e.toString());
                sleep(5_000);
            }
        }
    }

    void pollOnce() {
        List<Message> messages = sqs.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(WAIT_SECONDS)
                .visibilityTimeout(VISIBILITY_SECONDS)
                .messageSystemAttributeNames(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT)
                .build()).messages();
        messages.forEach(this::handle);
    }

    void handle(Message message) {
        JsonNode body;
        try {
            body = objectMapper.readTree(message.body());
        } catch (Exception e) {
            log.error("Dropping unreadable SQS message {}: {}", message.messageId(), e.toString());
            delete(message);
            return;
        }
        String type = body.path("type").asText("");
        long jobId = body.path("id").asLong(-1);
        try {
            runner.run(type, body.path("payload").asText(""));
            delete(message);
        } catch (Exception e) {
            String receives = message.attributes().get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT);
            log.warn("Job {} ({}) failed on receive {}, SQS will retry: {}", jobId, type, receives, e.toString());
        }
    }

    private void delete(Message message) {
        sqs.deleteMessage(DeleteMessageRequest.builder().queueUrl(queueUrl).receiptHandle(message.receiptHandle()).build());
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
