package com.travelplatform.repository;

import com.travelplatform.entity.Notification;
import com.travelplatform.service.NotificationPurgeJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The notification bell's queries against real Postgres and the real migrations: the list is
 * capped and newest first, the cursor pages backwards, the unread count is served by the partial
 * index, and the retention purge deletes in bounded batches - owner bucket included - without
 * touching anything still inside its window.
 *
 * <p>Not wrapped in a test transaction: the purge commits each batch on its own, as in production.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotificationBellQueriesTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 12, 0);

    @Autowired
    private NotificationRepository notifications;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        notifications.deleteAllInBatch();
    }

    private Notification row(String recipientId, String role, LocalDateTime createdAt, boolean read) {
        Notification n = new Notification();
        n.setRecipientId(recipientId);
        n.setRecipientRole(role);
        n.setType("TEST");
        n.setTitle("t");
        n.setMessage("m");
        n.setRead(read);
        n.setCreatedAt(createdAt);
        return n;
    }

    private void ownerRows(int count, LocalDateTime newest, boolean read) {
        List<Notification> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(row("owner", "ROLE_OWNER", newest.minusMinutes(i), read));
        }
        notifications.saveAll(rows);
    }

    @Test
    @DisplayName("the list returns only the latest page, newest first, however large the bucket is")
    void listIsCappedNewestFirst() {
        ownerRows(120, NOW, false);

        List<Notification> page = notifications.findByRecipientIdAndRecipientRoleOrderByCreatedAtDesc(
                "owner", "ROLE_OWNER", PageRequest.of(0, 50));

        assertEquals(50, page.size());
        assertEquals(NOW, page.get(0).getCreatedAt());
        assertEquals(NOW.minusMinutes(49), page.get(49).getCreatedAt());
    }

    @Test
    @DisplayName("the before cursor returns the next older page and never repeats a row")
    void cursorPagesBackwards() {
        ownerRows(70, NOW, false);
        List<Notification> first = notifications.findByRecipientIdAndRecipientRoleOrderByCreatedAtDesc(
                "owner", "ROLE_OWNER", PageRequest.of(0, 50));

        List<Notification> second = notifications.findByRecipientIdAndRecipientRoleAndCreatedAtBeforeOrderByCreatedAtDesc(
                "owner", "ROLE_OWNER", first.get(first.size() - 1).getCreatedAt(), PageRequest.of(0, 50));

        assertEquals(20, second.size());
        assertEquals(NOW.minusMinutes(50), second.get(0).getCreatedAt());
        Set<Object> firstIds = first.stream().map(Notification::getId).collect(Collectors.toSet());
        assertTrue(second.stream().noneMatch(n -> firstIds.contains(n.getId())));
    }

    @Test
    @DisplayName("the unread count counts only this recipient's unread rows")
    void unreadCount() {
        ownerRows(5, NOW, false);
        ownerRows(7, NOW.minusDays(1), true);
        notifications.save(row("u-1", "ROLE_USER", NOW, false));

        assertEquals(5, notifications.countByRecipientIdAndRecipientRoleAndReadFalse("owner", "ROLE_OWNER"));
        assertEquals(1, notifications.countByRecipientIdAndRecipientRoleAndReadFalse("u-1", "ROLE_USER"));
    }

    @Test
    @DisplayName("the migration adds the partial unread index and the created_at index the purge needs")
    void indexesExist() {
        List<String> defs = jdbc.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'notifications'", String.class);
        assertTrue(defs.stream().anyMatch(d -> d.contains("idx_notifications_unread") && d.contains("WHERE")),
                () -> "no partial unread index in " + defs);
        assertTrue(defs.stream().anyMatch(d -> d.contains("idx_notifications_created_at")),
                () -> "no created_at index in " + defs);
    }

    @Test
    @DisplayName("purge deletes read rows past 30 days and any row past 90, owner bucket included, and keeps the rest")
    void purgeByAge() {
        Notification freshUnread = notifications.save(row("owner", "ROLE_OWNER", NOW.minusDays(1), false));
        Notification freshRead = notifications.save(row("owner", "ROLE_OWNER", NOW.minusDays(10), true));
        Notification oldUnread = notifications.save(row("owner", "ROLE_OWNER", NOW.minusDays(45), false));
        notifications.save(row("owner", "ROLE_OWNER", NOW.minusDays(45), true));        // read, past 30
        notifications.save(row("u-1", "ROLE_USER", NOW.minusDays(31), true));            // read, past 30
        notifications.save(row("owner", "ROLE_OWNER", NOW.minusDays(120), false));       // past 90, unread
        notifications.save(row("d-1", "ROLE_DRIVER", NOW.minusDays(91), false));         // past 90, unread

        int removed = new NotificationPurgeJob(jdbc, 30, 90, 5000, 200).purge(NOW);

        assertEquals(4, removed);
        Set<Object> left = notifications.findAll().stream().map(Notification::getId).collect(Collectors.toSet());
        assertEquals(Set.of(freshUnread.getId(), freshRead.getId(), oldUnread.getId()), left);
    }

    @Test
    @DisplayName("purge works through a backlog in batches of the configured size and stops at the per-run cap")
    void purgeIsBatched() {
        ownerRows(25, NOW.minusDays(100), false);

        // Batches of 10, at most 2 per rule per run: 20 go now, the last 5 next run.
        NotificationPurgeJob job = new NotificationPurgeJob(jdbc, 30, 90, 10, 2);
        assertEquals(20, job.purge(NOW));
        assertEquals(5, notifications.count());
        assertEquals(5, job.purge(NOW));
        assertEquals(0, notifications.count());
    }
}
