package com.travelplatform.service;

import com.travelplatform.entity.Owner;
import com.travelplatform.entity.OwnerStatus;
import com.travelplatform.repository.OwnerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * NammaJourney promises owners a decision within 48 hours. An application still waiting after
 * 36 gets one reminder email to the admin, with 12 hours left to act.
 */
@Component
public class OwnerReviewReminderJob {

    private static final Logger log = LoggerFactory.getLogger(OwnerReviewReminderJob.class);

    static final Duration REMIND_AFTER = Duration.ofHours(36);

    @Autowired
    private OwnerRepository ownerRepository;

    @Autowired
    private EmailService emailService;

    @Scheduled(fixedRate = 3_600_000, initialDelay = 300_000)
    public void remind() {
        remind(Clock.systemDefaultZone());
    }

    @Transactional
    void remind(Clock clock) {
        LocalDateTime now = LocalDateTime.now(clock);
        for (Owner o : ownerRepository.findByStatusAndSubmittedAtBeforeAndAdminRemindedAtIsNull(
                OwnerStatus.PENDING_REVIEW, now.minus(REMIND_AFTER))) {
            emailService.sendAdminReviewReminder(o, o.getSubmittedAt().plus(OwnerSignupService.REVIEW_WINDOW));
            o.setAdminRemindedAt(now);
            ownerRepository.save(o);
            log.info("Reminded the admin about owner application {}", o.getId());
        }
    }
}
