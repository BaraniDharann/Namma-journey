package com.travelplatform.storage;

import com.travelplatform.security.FieldEncryption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.util.List;

/**
 * One-off move from {@code app.storage.type=db} to {@code s3}: copies every file in
 * {@code stored_files} into the bucket under the same key, so the paths saved on drivers and
 * trips keep loading after the switch.
 *
 * <p>Safe to run on every start while {@code app.storage.s3.import-from-db} is on: keys already in
 * the bucket are skipped. The database rows are left in place, so switching back to {@code db}
 * still works; delete them once the bucket is confirmed.
 */
public class DatabaseToS3Import {

    private static final Logger log = LoggerFactory.getLogger(DatabaseToS3Import.class);

    public record Result(int copied, int skipped, int failed) {
    }

    private final JdbcTemplate jdbc;
    private final S3FileStore s3;

    public DatabaseToS3Import(JdbcTemplate jdbc, S3FileStore s3) {
        this.jdbc = jdbc;
        this.s3 = s3;
    }

    public Result run() {
        if (!FieldEncryption.isConfigured()) {
            // The rows are ciphertext; without the key there is nothing that can be copied.
            log.error("Upload import skipped: APP_ENCRYPTION_KEY is not set, stored_files cannot be decrypted");
            return new Result(0, 0, 0);
        }
        // Keys first, then one file at a time: never the whole table in memory.
        List<String> keys = jdbc.queryForList("SELECT file_key FROM stored_files ORDER BY file_key", String.class);
        int copied = 0;
        int skipped = 0;
        int failed = 0;
        for (String key : keys) {
            try {
                if (s3.exists(key)) {
                    skipped++;
                    continue;
                }
                var rows = jdbc.query("SELECT content_type, data FROM stored_files WHERE file_key = ?",
                        (rs, i) -> new Object[]{rs.getString(1), rs.getBytes(2)}, key);
                if (rows.isEmpty()) {
                    continue;
                }
                byte[] plain = FieldEncryption.decryptBytes((byte[]) rows.get(0)[1]);
                s3.put(key, new ByteArrayInputStream(plain), plain.length, (String) rows.get(0)[0]);
                copied++;
            } catch (RuntimeException e) {
                failed++;
                // Folder only, as in FileStorageService: the key is the file's address.
                log.warn("Upload import failed for a file in {}: {}", folderOf(key), e.toString());
            }
        }
        log.info("Upload import into S3 finished: {} copied, {} already there, {} failed (of {})",
                copied, skipped, failed, keys.size());
        return new Result(copied, skipped, failed);
    }

    private static String folderOf(String key) {
        int slash = key.lastIndexOf('/');
        return slash < 0 ? key : key.substring(0, slash);
    }
}
