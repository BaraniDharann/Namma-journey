package com.travelplatform.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Background job delivery ({@code app.outbox.*}).
 *
 * <p>{@code transport=in-process} (default) runs jobs on this server straight from the outbox
 * table - no extra infrastructure, and nothing is lost across restarts because the table is the
 * queue. {@code transport=sqs} hands each job to an AWS SQS queue instead, so any number of
 * instances share the work and failed jobs land in the queue's dead-letter queue.
 */
@Component
@ConfigurationProperties(prefix = "app.outbox")
public class OutboxProperties {

    private String transport = "in-process";
    private long pollIntervalMs = 2000;
    private int batchSize = 20;
    /** In-process only: after this many failed attempts a job is marked DEAD. SQS uses its redrive policy. */
    private int maxAttempts = 8;
    /** Jobs of one batch delivered at once, so one slow SMTP or Razorpay call cannot hold up the rest. */
    private int workers = 4;
    private final Sqs sqs = new Sqs();

    public boolean usesSqs() {
        return "sqs".equalsIgnoreCase(transport);
    }

    public static class Sqs {
        private String queueUrl = "";
        private String region = "ap-south-1";
        /** Optional. Blank uses the AWS default chain (env AWS_ACCESS_KEY_ID, IAM role...). */
        private String accessKey = "";
        private String secretKey = "";
        /** Optional endpoint override, e.g. LocalStack at http://localhost:4566. */
        private String endpoint = "";

        public String getQueueUrl() { return queueUrl; }
        public void setQueueUrl(String queueUrl) { this.queueUrl = queueUrl == null ? "" : queueUrl.trim(); }
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey == null ? "" : accessKey.trim(); }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey == null ? "" : secretKey.trim(); }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint == null ? "" : endpoint.trim(); }
    }

    public String getTransport() { return transport; }
    public void setTransport(String transport) { this.transport = transport; }
    public long getPollIntervalMs() { return pollIntervalMs; }
    public void setPollIntervalMs(long pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public int getWorkers() { return workers; }
    public void setWorkers(int workers) { this.workers = workers; }
    public Sqs getSqs() { return sqs; }
}
