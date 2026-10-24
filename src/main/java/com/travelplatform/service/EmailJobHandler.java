package com.travelplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.outbox.JobHandler;
import org.springframework.stereotype.Component;

/** Sends the notification emails EmailService queued. A failed SMTP send throws and is retried. */
@Component
public class EmailJobHandler implements JobHandler {

    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    public EmailJobHandler(EmailService emailService, ObjectMapper objectMapper) {
        this.emailService = emailService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return EmailService.EMAIL_JOB;
    }

    @Override
    public void handle(String payload) throws Exception {
        emailService.sendNow(objectMapper.readValue(payload, EmailService.EmailMessage.class));
    }
}
