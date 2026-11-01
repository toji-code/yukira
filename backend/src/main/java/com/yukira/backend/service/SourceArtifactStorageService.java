package com.yukira.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Durable Cryptographic Source Artifact Storage Service (Task 266).
 * Persists raw, unadulterated source payloads directly to content-addressed disk storage
 * keyed by cryptographic SHA-256 digest, preventing memory exhaustion and preserving immutable provenance.
 */
@Service
public class SourceArtifactStorageService {

    private static final Logger log = LoggerFactory.getLogger(SourceArtifactStorageService.class);

    private final Path storageDirectory;

    public SourceArtifactStorageService(
            @Value("${yukira.storage.artifact-dir:artifacts}") String artifactDir
    ) {
        this.storageDirectory = Paths.get(artifactDir).toAbsolutePath().normalize();
        initStorageDirectory();
    }

    private void initStorageDirectory() {
        try {
            if (!Files.exists(storageDirectory)) {
                Files.createDirectories(storageDirectory);
                log.info("Initialized source artifact storage directory at {}", storageDirectory);
            }
        } catch (IOException e) {
            log.error("Failed to initialize source artifact storage directory at {}", storageDirectory, e);
        }
    }

    /**
     * Calculates the SHA-256 hex digest of a byte array.
     */
    public String calculateSha256(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Cannot compute SHA-256 of null payload");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    /**
     * Verifies that the payload produces the expected SHA-256 hash.
     */
    public boolean verifyIntegrity(byte[] data, String expectedHash) {
        if (data == null || expectedHash == null) {
            return false;
        }
        String calculated = calculateSha256(data);
        return calculated.equalsIgnoreCase(expectedHash);
    }

    /**
     * Persists the raw payload to content-addressed disk storage.
     * Enforces strict cryptographic integrity verification prior to writing.
     *
     * @param payload Raw source bytes
     * @param expectedSha256 Expected SHA-256 hash
     * @return Content-addressed URI string
     */
    public String persistArtifact(byte[] payload, String expectedSha256) {
        if (payload == null || payload.length == 0) {
            throw new IllegalArgumentException("Refusing to persist empty or null artifact payload");
        }
        if (!verifyIntegrity(payload, expectedSha256)) {
            String actual = calculateSha256(payload);
            throw new SecurityException("Cryptographic verification failed: expected SHA-256 " 
                    + expectedSha256 + " but computed " + actual);
        }

        Path targetFile = storageDirectory.resolve(expectedSha256.toLowerCase());
        try {
            if (!Files.exists(targetFile)) {
                Files.write(targetFile, payload, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                log.info("Persisted immutable source artifact to {} ({} bytes, sha256={})", 
                        targetFile, payload.length, expectedSha256);
            }
            return targetFile.toUri().toString();
        } catch (IOException e) {
            log.error("Failed to write source artifact to disk: {}", targetFile, e);
            throw new RuntimeException("Artifact storage failure for hash " + expectedSha256, e);
        }
    }

    /**
     * Reads the raw payload from disk by its SHA-256 hash and re-verifies cryptographic integrity.
     */
    public Optional<byte[]> retrieveArtifact(String sha256) {
        if (sha256 == null || sha256.isBlank()) {
            return Optional.empty();
        }
        Path targetFile = storageDirectory.resolve(sha256.toLowerCase());
        if (!Files.exists(targetFile)) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(targetFile);
            if (!verifyIntegrity(bytes, sha256)) {
                log.error("Stored artifact corrupted on disk: hash mismatch for file {}", targetFile);
                return Optional.empty();
            }
            return Optional.of(bytes);
        } catch (IOException e) {
            log.error("Failed to read artifact file from disk: {}", targetFile, e);
            return Optional.empty();
        }
    }
}

