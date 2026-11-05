package com.travelplatform.service;

import com.travelplatform.repository.NotificationRepository;
import com.travelplatform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The bell's list is always a bounded page: whatever limit a client sends, the repository is
 * asked for at most MAX_PAGE_SIZE rows, and never for the unbounded history.
 */
class NotificationServicePagingTest {

    private NotificationRepository repository;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        service = new NotificationService(repository, mock(EmailService.class), mock(UserRepository.class),
                mock(TelegramDispatchService.class), new AfterCommitDispatcher(Runnable::run));
    }

    private static Pageable size(int n) {
        return argThat(p -> p.getPageNumber() == 0 && p.getPageSize() == n);
    }

    @Test
    @DisplayName("no limit asks for the default page of 50")
    void defaultPage() {
        service.getNotifications("owner", "ROLE_OWNER", null, null);
        verify(repository).findByRecipientIdAndRecipientRoleOrderByCreatedAtDesc(eq("owner"), eq("ROLE_OWNER"),
                size(NotificationService.DEFAULT_PAGE_SIZE));
        verify(repository, never()).findByRecipientIdAndRecipientRoleOrderByCreatedAtDesc(anyString(), anyString());
    }

    @Test
    @DisplayName("an oversized limit is clamped to the maximum and a non-positive one to a single row")
    void limitIsClamped() {
        service.getNotifications("u-1", "ROLE_USER", 100_000, null);
        verify(repository).findByRecipientIdAndRecipientRoleOrderByCreatedAtDesc(eq("u-1"), eq("ROLE_USER"),
                size(NotificationService.MAX_PAGE_SIZE));

        service.getNotifications("u-2", "ROLE_USER", 0, null);
        verify(repository).findByRecipientIdAndRecipientRoleOrderByCreatedAtDesc(eq("u-2"), eq("ROLE_USER"), size(1));
    }

    @Test
    @DisplayName("a before cursor asks for the older page instead of the latest one")
    void beforeCursor() {
        LocalDateTime before = LocalDateTime.of(2026, 11, 1, 9, 30);
        service.getNotifications("owner", "ROLE_OWNER", 20, before);
        verify(repository).findByRecipientIdAndRecipientRoleAndCreatedAtBeforeOrderByCreatedAtDesc(
                eq("owner"), eq("ROLE_OWNER"), eq(before), size(20));
        verify(repository, never()).findByRecipientIdAndRecipientRoleOrderByCreatedAtDesc(
                anyString(), anyString(), any(Pageable.class));
    }
}
