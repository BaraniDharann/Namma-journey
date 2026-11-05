package com.travelplatform.service;

import com.travelplatform.entity.Owner;
import com.travelplatform.entity.OwnerStatus;
import com.travelplatform.repository.OwnerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OwnerReviewReminderJobTest {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    final Clock clock = Clock.fixed(Instant.parse("2026-11-05T06:30:00Z"), IST);
    final LocalDateTime now = LocalDateTime.now(clock);

    @Test
    @DisplayName("an application waiting 36 hours gets one reminder email to the admin")
    void remindsOnceAfter36Hours() {
        OwnerRepository owners = mock(OwnerRepository.class);
        EmailService emails = mock(EmailService.class);
        Owner o = new Owner();
        o.setId(3L);
        o.setStatus(OwnerStatus.PENDING_REVIEW);
        o.setSubmittedAt(now.minusHours(37));
        when(owners.findByStatusAndSubmittedAtBeforeAndAdminRemindedAtIsNull(OwnerStatus.PENDING_REVIEW, now.minusHours(36)))
                .thenReturn(List.of(o));
        OwnerReviewReminderJob job = new OwnerReviewReminderJob();
        ReflectionTestUtils.setField(job, "ownerRepository", owners);
        ReflectionTestUtils.setField(job, "emailService", emails);

        job.remind(clock);

        verify(emails).sendAdminReviewReminder(o, o.getSubmittedAt().plusHours(48));
        assertEquals(now, o.getAdminRemindedAt());
        verify(owners).save(o);
    }

    @Test
    @DisplayName("already-reminded applications are not picked up again")
    void doesNotRemindTwice() {
        OwnerRepository owners = mock(OwnerRepository.class);
        EmailService emails = mock(EmailService.class);
        when(owners.findByStatusAndSubmittedAtBeforeAndAdminRemindedAtIsNull(any(), any())).thenReturn(List.of());
        OwnerReviewReminderJob job = new OwnerReviewReminderJob();
        ReflectionTestUtils.setField(job, "ownerRepository", owners);
        ReflectionTestUtils.setField(job, "emailService", emails);

        job.remind(clock);

        // The query itself excludes reminded rows (AdminRemindedAtIsNull), only for PENDING_REVIEW.
        verify(owners).findByStatusAndSubmittedAtBeforeAndAdminRemindedAtIsNull(eq(OwnerStatus.PENDING_REVIEW), eq(now.minusHours(36)));
        verifyNoInteractions(emails);
    }
}
