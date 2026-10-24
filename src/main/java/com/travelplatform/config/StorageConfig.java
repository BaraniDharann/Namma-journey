package com.travelplatform.config;

import com.travelplatform.storage.FileStore;
import com.travelplatform.storage.LocalFileStore;
import com.travelplatform.storage.S3FileStore;
import com.travelplatform.storage.S3StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Chooses where uploads live: app.storage.type = local (default, one server) or s3 (shared by
 * every instance - required before running more than one backend).
 */
@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    /**
     * Same folders as before, so files already on disk keep loading: driver documents in
     * file.upload.dir, new trip photos beside them inside the uploads volume, and photos from
     * before this change in the old ./driverphoto folder (read-only in practice - nothing writes
     * there any more).
     */
    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
    public FileStore localFileStore(@Value("${file.upload.dir:uploads/drivers}") String driverDir,
                                    @Value("${app.storage.local.trips-dir:uploads/trips}") String tripsDir) {
        Map<String, Path> roots = new LinkedHashMap<>();
        roots.put("uploads/drivers/", Path.of(driverDir));
        roots.put("uploads/trips/", Path.of(tripsDir));
        roots.put("driverphoto/", Path.of("driverphoto"));
        log.info("Uploads stored on local disk ({}, {}) - single instance only", driverDir, tripsDir);
        return new LocalFileStore(roots);
    }

    /**
     * Uploads in Postgres, encrypted (V22): survives redeploys on hosts with no persistent disk and
     * needs no object storage. Shared by every instance, since they share the database.
     */
    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "db")
    public FileStore databaseFileStore(org.springframework.jdbc.core.JdbcTemplate jdbc) {
        log.info("Uploads stored encrypted in the database (stored_files)");
        return new com.travelplatform.storage.DatabaseFileStore(jdbc);
    }

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
    public FileStore s3FileStore(@Value("${app.storage.s3.bucket}") String bucket,
                                 @Value("${app.storage.s3.region:auto}") String region,
                                 @Value("${app.storage.s3.endpoint:}") String endpoint,
                                 @Value("${app.storage.s3.access-key:}") String accessKey,
                                 @Value("${app.storage.s3.secret-key:}") String secretKey,
                                 @Value("${app.storage.s3.path-style:false}") boolean pathStyle,
                                 @Value("${app.storage.s3.signed-url-ttl:10m}") Duration signedUrlTtl) {
        S3StorageProperties props = new S3StorageProperties(
                bucket, region, endpoint, accessKey, secretKey, pathStyle, signedUrlTtl);
        log.info("Uploads stored in S3-compatible bucket '{}'{}", bucket,
                endpoint.isBlank() ? "" : " at " + endpoint);
        return new S3FileStore(S3FileStore.client(props), S3FileStore.presigner(props), props);
    }
}
