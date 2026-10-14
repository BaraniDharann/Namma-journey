package com.travelplatform.service;

import com.travelplatform.entity.Notification;
import com.travelplatform.repository.NotificationRepository;
import com.travelplatform.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The owner's notifications live in a shared "owner" bucket. Any signed-in passenger or driver
 * could mark them read - silently hiding "new booking" and "trip needs a driver" alerts.
 */
class NotificationServiceReadTest {

    private final NotificationRepository repo = mock(NotificationRepository.class);
    private final NotificationService service = new NotificationService(repo, mock(EmailService.class),
            mock(UserRepository.class), mock(TelegramDispatchService.class), new AfterCommitDispatcher(Runnable::run));

    private Notification stored(String recipient) {
        Notification n = new Notification();
        n.setRecipientId(recipient);
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(n));
        n.setId(id);
        return n;
    }

    @Test
    @DisplayName("only the owner may mark owner notifications read")
    void ownerBucketNeedsTheOwner() {
        Notification n = stored("owner");
        assertThrows(AccessDeniedException.class, () -> service.markAsRead(n.getId(), "u-1", false));
        assertFalse(n.isRead());

        assertDoesNotThrow(() -> service.markAsRead(n.getId(), "1", true));
        assertTrue(n.isRead());
    }

    @Test
    @DisplayName("people may mark their own notifications, never someone else's")
    void ownNotificationsOnly() {
        Notification mine = stored("u-1");
        Notification theirs = stored("u-2");
        assertDoesNotThrow(() -> service.markAsRead(mine.getId(), "u-1", false));
        assertThrows(AccessDeniedException.class, () -> service.markAsRead(theirs.getId(), "u-1", false));
    }
}
