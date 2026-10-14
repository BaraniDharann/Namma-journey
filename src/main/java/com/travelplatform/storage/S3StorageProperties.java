package com.travelplatform.storage;

import java.time.Duration;

/**
 * Connection settings for S3-compatible storage (app.storage.s3.* in application.yml).
 *
 * @param endpoint      blank for AWS S3; the account endpoint for Cloudflare R2
 *                      (https://ACCOUNT_ID.r2.cloudflarestorage.com) or a MinIO URL
 * @param pathStyle     true for MinIO; R2 and AWS work with either
 * @param signedUrlTtl  how long an image link handed to a browser stays valid
 */
public record S3StorageProperties(String bucket, String region, String endpoint,
                                  String accessKey, String secretKey, boolean pathStyle,
                                  Duration signedUrlTtl) {
}
