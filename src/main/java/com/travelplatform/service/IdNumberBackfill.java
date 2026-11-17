package com.travelplatform.service;

import com.travelplatform.security.FieldEncryption;
import com.travelplatform.security.IdNumberHash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Converts ID numbers saved before V37 into their one-way form, once, at startup:
 * drivers' readable Aadhaar numbers and owners' encrypted PANs become bcrypt + keyed hash + the
 * masked tail, and the readable value is erased. Rows already converted are skipped, so running
 * again (every start) does nothing.
 *
 * <p>Needs APP_ENCRYPTION_KEY (for the keyed hash and to read the old encrypted PANs). Without it
 * nothing is converted and an error is logged; the old values stay until the key is set.
 */
@Component
public class IdNumberBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IdNumberBackfill.class);

    private final JdbcTemplate jdbc;

    public IdNumberBackfill(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            convert();
        } catch (RuntimeException e) {
            // Never block the app from starting; the next start tries again.
            log.error("Hashing stored Aadhaar/PAN numbers failed; will retry on next start", e);
        }
    }

    void convert() {
        int drivers = count("SELECT count(*) FROM drivers WHERE aadhaar_number IS NOT NULL");
        int owners = count("SELECT count(*) FROM owners WHERE pan_number IS NOT NULL");
        if (drivers == 0 && owners == 0) {
            return;
        }
        if (!FieldEncryption.isConfigured()) {
            log.error("{} driver Aadhaar and {} owner PAN numbers are still stored readable: set "
                    + "APP_ENCRYPTION_KEY so they can be hashed", drivers, owners);
            return;
        }
        int d = convertDrivers();
        int o = convertOwners();
        log.info("Hashed {} driver Aadhaar number(s) and {} owner PAN(s); readable copies erased", d, o);
    }

    private int convertDrivers() {
        int done = 0;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, aadhaar_number FROM drivers WHERE aadhaar_number IS NOT NULL");
        for (Map<String, Object> row : rows) {
            Object id = row.get("id");
            String digits = IdNumberHash.aadhaarDigits((String) row.get("aadhaar_number"));
            if (digits == null || digits.length() < 4) {
                // Not a usable number: drop it rather than keep it readable.
                jdbc.update("UPDATE drivers SET aadhaar_number = NULL WHERE id = ?", id);
                log.warn("Driver {} had an unusable Aadhaar value; erased it", id);
                continue;
            }
            done += jdbc.update("UPDATE drivers SET aadhaar_hash = ?, aadhaar_bcrypt = ?, aadhaar_last4 = ?, "
                            + "aadhaar_number = NULL WHERE id = ? AND aadhaar_number IS NOT NULL",
                    OwnerApplicationChecks.aadhaarHash(digits), IdNumberHash.bcrypt(digits),
                    digits.substring(digits.length() - 4), id);
        }
        return done;
    }

    private int convertOwners() {
        int done = 0;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, pan_number FROM owners WHERE pan_number IS NOT NULL");
        for (Map<String, Object> row : rows) {
            Object id = row.get("id");
            String stored = (String) row.get("pan_number");
            String pan;
            try {
                // Written by EncryptedStringConverter ("v1:..."); a very old row may be plain.
                pan = OwnerApplicationChecks.normalisePan(stored.startsWith("v1:") ? FieldEncryption.decrypt(stored) : stored);
            } catch (RuntimeException e) {
                log.warn("Owner {}: stored PAN could not be read ({}); left as it is", id, e.getMessage());
                continue;
            }
            try {
                done += jdbc.update("UPDATE owners SET pan_bcrypt = ?, pan_masked = ?, "
                                + "pan_hash = COALESCE(pan_hash, ?), pan_number = NULL "
                                + "WHERE id = ? AND pan_number IS NOT NULL",
                        IdNumberHash.bcrypt(pan), OwnerApplicationChecks.maskPan(pan),
                        OwnerApplicationChecks.panHash(pan), id);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // Another owner already holds this PAN's hash (a released, rejected application):
                // keep the hash where it is and still erase the readable PAN here.
                done += jdbc.update("UPDATE owners SET pan_bcrypt = ?, pan_masked = ?, pan_number = NULL "
                                + "WHERE id = ? AND pan_number IS NOT NULL",
                        IdNumberHash.bcrypt(pan), OwnerApplicationChecks.maskPan(pan), id);
            }
        }
        return done;
    }

    private int count(String sql) {
        Integer n = jdbc.queryForObject(sql, Integer.class);
        return n == null ? 0 : n;
    }
}
