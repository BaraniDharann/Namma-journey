package com.travelplatform.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

/** Picks the job transport from {@code app.outbox.transport}. */
@Configuration
public class OutboxTransportConfig {

    private static final Logger log = LoggerFactory.getLogger(OutboxTransportConfig.class);

    @Bean
    @ConditionalOnProperty(name = "app.outbox.transport", havingValue = "in-process", matchIfMissing = true)
    public OutboxTransport inProcessOutboxTransport(JobRunner runner) {
        log.info("Background jobs run in-process from the outbox table");
        return job -> runner.run(job.type(), job.payload());
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "app.outbox.transport", havingValue = "sqs")
    public SqsClient outboxSqsClient(OutboxProperties properties) {
        OutboxProperties.Sqs sqs = properties.getSqs();
        if (sqs.getQueueUrl().isBlank()) {
            // Fail at startup, not on the first booking: jobs would pile up undelivered.
            throw new IllegalStateException("app.outbox.transport=sqs needs app.outbox.sqs.queue-url (OUTBOX_SQS_QUEUE_URL)");
        }
        var builder = SqsClient.builder()
                .region(Region.of(sqs.getRegion()))
                .credentialsProvider(credentials(sqs));
        if (!sqs.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(sqs.getEndpoint()));
        }
        log.info("Background jobs go through SQS queue {}", sqs.getQueueUrl());
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.outbox.transport", havingValue = "sqs")
    public OutboxTransport sqsOutboxTransport(SqsClient outboxSqsClient, OutboxProperties properties,
                                              com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new SqsOutboxTransport(outboxSqsClient, properties.getSqs().getQueueUrl(), objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "app.outbox.transport", havingValue = "sqs")
    public SqsJobWorker sqsJobWorker(SqsClient outboxSqsClient, OutboxProperties properties, JobRunner runner,
                                     com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new SqsJobWorker(outboxSqsClient, properties.getSqs().getQueueUrl(), runner, objectMapper);
    }

    private static AwsCredentialsProvider credentials(OutboxProperties.Sqs sqs) {
        if (!sqs.getAccessKey().isBlank()) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(sqs.getAccessKey(), sqs.getSecretKey()));
        }
        return DefaultCredentialsProvider.create();
    }
}
