package com.travelplatform.outbox;

import io.micrometer.core.instrument.Metrics;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Routes a job to its {@link JobHandler}, whichever transport delivered it. */
@Component
public class JobRunner {

    private final Map<String, JobHandler> handlers;

    public JobRunner(List<JobHandler> handlers) {
        this.handlers = handlers.stream().collect(Collectors.toMap(JobHandler::type, Function.identity()));
    }

    /** Throws when the job failed and should be retried. */
    public void run(String type, String payload) throws Exception {
        JobHandler handler = handlers.get(type);
        if (handler == null) {
            // A job written by a newer version that this instance does not know yet: retrying
            // lets an instance that does know it pick it up.
            throw new IllegalStateException("No handler for job type " + type);
        }
        try {
            handler.handle(payload);
            count(type, "done");
        } catch (Exception e) {
            count(type, "failed");
            throw e;
        }
    }

    /** namma_outbox_jobs_total{type,outcome} - the "failed" and "dead" rates are alerted on. */
    static void count(String type, String outcome) {
        Metrics.counter("namma.outbox.jobs", "type", type, "outcome", outcome).increment();
    }
}
