package com.travelplatform.storage;

import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Files on this server's disk - the original behaviour, for a single instance.
 *
 * <p>Each key prefix maps to a directory, mirroring the URL-to-folder layout that already exists
 * ({@code uploads/drivers/} lives in {@code file.upload.dir}), so nothing already uploaded moves.
 * A resolved path must stay inside its directory: "..", encoded separators and unknown prefixes
 * are refused, so a crafted URL can never read outside the upload folders.
 */
public class LocalFileStore implements FileStore {

    private final Map<String, Path> roots = new LinkedHashMap<>();

    public LocalFileStore(Map<String, Path> prefixToDirectory) {
        prefixToDirectory.forEach((prefix, dir) -> roots.put(prefix, dir.toAbsolutePath().normalize()));
    }

    @Override
    public void put(String key, InputStream content, long size, String contentType) {
        Path target = resolve(key).orElseThrow(() -> new IllegalArgumentException("Not a storable key: " + key));
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store " + key, e);
        }
    }

    @Override
    public Optional<ServedFile> serve(String key) {
        return resolve(key)
                .filter(Files::isRegularFile)
                .map(path -> new ServedFile.Stream(new FileSystemResource(path)));
    }

    @Override
    public void delete(String key) {
        resolve(key).ifPresent(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to delete " + key, e);
            }
        });
    }

    private Optional<Path> resolve(String key) {
        if (key == null || key.contains("..") || key.contains("\\") || key.contains("%")) {
            return Optional.empty();
        }
        for (Map.Entry<String, Path> root : roots.entrySet()) {
            if (key.startsWith(root.getKey())) {
                try {
                    Path resolved = root.getValue().resolve(key.substring(root.getKey().length())).normalize();
                    return resolved.startsWith(root.getValue()) ? Optional.of(resolved) : Optional.empty();
                } catch (InvalidPathException e) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }
}
