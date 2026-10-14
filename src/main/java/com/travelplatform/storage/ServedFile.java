package com.travelplatform.storage;

import org.springframework.core.io.Resource;

import java.net.URI;

/** How a stored file reaches the browser: streamed by this app, or fetched straight from object storage. */
public sealed interface ServedFile {

    /** Bytes this application streams itself (local disk). */
    record Stream(Resource resource) implements ServedFile {
    }

    /** A short-lived signed URL the browser is redirected to (S3 / R2). */
    record Redirect(URI location) implements ServedFile {
    }
}
