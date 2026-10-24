package com.travelplatform.storage;

import com.travelplatform.security.FieldEncryption;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;

/**
 * Uploads kept in Postgres, encrypted ({@code app.storage.type=db}, V22).
 *
 * <p>For hosts without a persistent disk and deployments without object storage: files survive
 * every redeploy, are covered by database backups, and an identity scan in a dump or backup is
 * ciphertext. Files arrive already compressed (ImageCompressor), so rows stay a few hundred KB.
 *
 * <p>Fails closed: with no APP_ENCRYPTION_KEY, {@link #put} refuses rather than storing an
 * Aadhaar scan in the clear.
 */
public class DatabaseFileStore implements FileStore {

    /** A compressed photo is well under this; anything larger did not come through ImageCompressor. */
    static final int MAX_BYTES = 8 * 1024 * 1024;

    private final JdbcTemplate jdbc;

    public DatabaseFileStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void put(String key, InputStream content, long size, String contentType) {
        requireValidKey(key);
        if (!FieldEncryption.isConfigured()) {
            throw new IllegalStateException("Uploads are not set up yet (APP_ENCRYPTION_KEY missing)");
        }
        byte[] plain;
        try {
            plain = content.readNBytes(MAX_BYTES + 1);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + key, e);
        }
        if (plain.length > MAX_BYTES) {
            throw new IllegalArgumentException("File is too large to store");
        }
        byte[] sealed = FieldEncryption.encryptBytes(plain);
        jdbc.update("""
                INSERT INTO stored_files (file_key, content_type, size_bytes, data) VALUES (?, ?, ?, ?)
                ON CONFLICT (file_key) DO UPDATE
                SET content_type = EXCLUDED.content_type, size_bytes = EXCLUDED.size_bytes,
                    data = EXCLUDED.data, created_at = now()
                """, key, contentType, plain.length, sealed);
    }

    @Override
    public Optional<ServedFile> serve(String key) {
        if (!isValidKey(key)) {
            return Optional.empty();
        }
        List<byte[]> rows = jdbc.query("SELECT data FROM stored_files WHERE file_key = ?",
                (rs, i) -> rs.getBytes(1), key);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        byte[] plain = FieldEncryption.decryptBytes(rows.get(0));
        return Optional.of(new ServedFile.Stream(new ByteArrayResource(plain)));
    }

    @Override
    public void delete(String key) {
        if (isValidKey(key)) {
            jdbc.update("DELETE FROM stored_files WHERE file_key = ?", key);
        }
    }

    /** Same shape the local store accepts: our own upload folders, no traversal, no encoding tricks. */
    static boolean isValidKey(String key) {
        return key != null && key.length() <= 512
                && (key.startsWith("uploads/") || key.startsWith("driverphoto/"))
                && !key.contains("..") && !key.contains("\\") && !key.contains("%");
    }

    private static void requireValidKey(String key) {
        if (!isValidKey(key)) {
            throw new IllegalArgumentException("Not a storable key: " + key);
        }
    }
}
