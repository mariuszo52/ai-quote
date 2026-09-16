package com.aiquote.backend.file;

import java.io.InputStream;

/**
 * Object storage abstraction. MinioStorageService is the only implementation
 * (MinIO in dev, S3-compatible in prod) — callers never touch the SDK directly,
 * so swapping providers later means adding an implementation, not touching callers.
 */
public interface StorageService {

    /**
     * Stores the content and returns an opaque storage key that can later be
     * passed to {@link #retrieve(String)}.
     */
    String store(Long companyId, String originalFilename, String contentType, InputStream content, long size);

    InputStream retrieve(String storageKey);

    /** Best-effort delete — used when replacing/removing a company logo (Etap 17). */
    void delete(String storageKey);
}
