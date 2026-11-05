-- The notification bell's steady load: every signed-in screen asks for its unread count, and the
-- retention job (NotificationPurgeJob) deletes old rows by age. The list itself is served by
-- idx_notifications_recipient (V6) and is now capped (latest 50), so it needs nothing new.
--
-- Plain CREATE INDEX, like V6: the notifications table is still small enough that the brief write
-- block is well under a second. CONCURRENTLY would also need Flyway's transactional lock turned
-- off (it otherwise waits on Flyway's own lock transaction and hangs), which is not worth it here.

-- Unread count: COUNT(*) WHERE recipient_id = ? AND recipient_role = ? AND read = false.
-- Holds only unread rows - a small slice that stays small because people read their alerts - so
-- the count touches a handful of index entries instead of the recipient's whole history. The
-- shared "owner" bucket is the one that matters: every owner screen asks for it.
CREATE INDEX IF NOT EXISTS idx_notifications_unread
    ON public.notifications (recipient_id, recipient_role)
    WHERE read = false;

-- Retention: the purge picks rows by created_at in bounded batches. Without this each batch
-- would be a sequential scan of the whole table.
CREATE INDEX IF NOT EXISTS idx_notifications_created_at
    ON public.notifications (created_at);
