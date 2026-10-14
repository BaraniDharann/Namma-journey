package com.travelplatform.storage;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * Uploads in S3-compatible object storage (AWS S3, Cloudflare R2, MinIO), shared by every backend
 * instance. Local disk ties each file to the one server that received it - a second instance, or
 * a redeploy without the volume, and photos vanish.
 *
 * <p>Images are not streamed through the app: {@link #serve} returns a pre-signed GET URL that
 * expires after {@code signedUrlTtl}, and the browser is redirected there. The bucket itself
 * stays private - no public-read ACL, so there is no permanent URL to an Aadhaar scan to leak.
 */
public class S3FileStore implements FileStore {

    /** The same folders LocalFileStore serves; nothing else in the bucket is reachable via the app. */
    private static final List<String> ALLOWED_PREFIXES = List.of("uploads/", "driverphoto/");

    private final S3Client s3;
    private final S3Presigner presigner;
    private final S3StorageProperties props;

    public S3FileStore(S3Client s3, S3Presigner presigner, S3StorageProperties props) {
        this.s3 = s3;
        this.presigner = presigner;
        this.props = props;
    }

    @Override
    public void put(String key, InputStream content, long size, String contentType) {
        if (!isValidKey(key)) {
            throw new IllegalArgumentException("Not a storable key: " + key);
        }
        s3.putObject(PutObjectRequest.builder()
                        .bucket(props.bucket())
                        .key(key)
                        .contentType(contentType)
                        // Keys are random per upload and never overwritten, so a browser may cache
                        // the image for the life of the signed URL it was fetched with.
                        .cacheControl("private, max-age=" + props.signedUrlTtl().toSeconds())
                        .build(),
                RequestBody.fromInputStream(content, size));
    }

    @Override
    public Optional<ServedFile> serve(String key) {
        if (!isValidKey(key)) {
            return Optional.empty();
        }
        // No HEAD request first: a missing key simply yields a signed URL that S3 answers with
        // 404, which saves a round-trip on every image of every page.
        URI url = URI.create(presigner.presignGetObject(r -> r
                        .signatureDuration(props.signedUrlTtl())
                        .getObjectRequest(GetObjectRequest.builder().bucket(props.bucket()).key(key).build()))
                .url().toString());
        return Optional.of(new ServedFile.Redirect(url));
    }

    @Override
    public void delete(String key) {
        if (isValidKey(key)) {
            s3.deleteObject(b -> b.bucket(props.bucket()).key(key));
        }
    }

    private static boolean isValidKey(String key) {
        return key != null
                && !key.contains("..")
                && !key.contains("\\")
                && ALLOWED_PREFIXES.stream().anyMatch(key::startsWith);
    }

    public static S3Client client(S3StorageProperties props) {
        var builder = S3Client.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(credentials(props))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(props.pathStyle()).build());
        if (props.endpoint() != null && !props.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(props.endpoint()));
        }
        return builder.build();
    }

    public static S3Presigner presigner(S3StorageProperties props) {
        var builder = S3Presigner.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(credentials(props))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(props.pathStyle()).build());
        if (props.endpoint() != null && !props.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(props.endpoint()));
        }
        return builder.build();
    }

    /** Explicit keys when configured (R2, MinIO); otherwise the AWS default chain (IAM role, env). */
    private static AwsCredentialsProvider credentials(S3StorageProperties props) {
        if (props.accessKey() != null && !props.accessKey().isBlank()) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(props.accessKey(), props.secretKey()));
        }
        return DefaultCredentialsProvider.create();
    }
}
