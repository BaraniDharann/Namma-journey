package com.travelplatform.storage;

import java.io.InputStream;
import java.util.Optional;

/**
 * Where uploaded images live. Keys are the public URL path without its leading slash
 * ("uploads/drivers/license_ab12.jpg"), so the paths already stored in the database keep working
 * whichever implementation is active.
 *
 * <p>{@link LocalFileStore} is the single-server default. {@link S3FileStore} puts files in
 * S3-compatible object storage (AWS S3, Cloudflare R2, MinIO), which every instance shares - the
 * requirement for running more than one backend.
 */
public interface FileStore {

    void put(String key, InputStream content, long size, String contentType);

    Optional<ServedFile> serve(String key);

    /** Removes a stored file; a missing key is not an error. */
    void delete(String key);
}
