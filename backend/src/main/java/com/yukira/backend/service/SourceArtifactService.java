package com.yukira.backend.service;

import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.repository.SourceArtifactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service managing cryptographic source artifacts, authority tiers, storage states,
 * authenticity verification, and strict investor-supplied data isolation (Tasks 265, 266, 324-330, 345, 371-373).
 */
@Service
@Transactional(readOnly = true)
public class SourceArtifactService {

    private final SourceArtifactRepository sourceArtifactRepository;
    private final SourceArtifactStorageService storageService;

    public SourceArtifactService(
            SourceArtifactRepository sourceArtifactRepository,
            SourceArtifactStorageService storageService
    ) {
        this.sourceArtifactRepository = sourceArtifactRepository;
        this.storageService = storageService;
    }

    public record SourceArtifactSummaryDto(
        Long id,
        String dataSourceCode,
        OffsetDateTime retrievalTimestamp,
        String artifactType,
        String sha256Hash,
        Long byteSize,
        String storageUri,
        boolean isCryptographicallyVerified,
        String storageBackend,
        String ingestionStatus,
        String sourceAuthorityTier, // TIER_1_OFFICIAL_REGULATOR, TIER_2_OFFICIAL_AMC, TIER_3_BENCHMARK_PROVIDER, TIER_4_INVESTOR_SUPPLIED
        String storageState, // INLINE, HOT_BLOB, COLD_BLOB, EXTERNAL_CONFIRMED
        boolean isInvestorPrivate,
        Long owningInvestorId,
        String authenticityStatus, // OFFICIAL_VERIFIED, UNVERIFIED_THIRD_PARTY, MANUALLY_ATTESTED, SUSPECT_TAMPERED
        String corroborationStatus, // INDEPENDENTLY_CORROBORATED, SINGLE_SOURCE_UNCHECKED, DISCREPANCY_FLAGGED
        String publisherIdentity,
        String retentionClassification // PERMANENT_REGULATORY, TRANSACTION_EVIDENCE_8_YEARS, EPHEMERAL_WORKSPACE_90_DAYS
    ) {}

    public record SourceArtifactDetailDto(
        Long id,
        String dataSourceCode,
        OffsetDateTime retrievalTimestamp,
        String artifactType,
        String sha256Hash,
        Long byteSize,
        String storageUri,
        boolean isCryptographicallyVerified,
        String previewText,
        String storageBackend,
        String ingestionStatus,
        String sourceAuthorityTier,
        String storageState,
        boolean isInvestorPrivate,
        Long owningInvestorId,
        String authenticityStatus,
        String corroborationStatus,
        String publisherIdentity,
        String retrievalOrigin,
        String retentionClassification,
        String retentionPolicyRef,
        boolean deletionEligibility
    ) {}

    public Optional<SourceArtifactDetailDto> getArtifactByHash(String sha256Hash) {
        return sourceArtifactRepository.findBySha256Hash(sha256Hash)
            .map(this::toDetailDto);
    }

    public Optional<SourceArtifactDetailDto> getArtifactByHash(String sha256Hash, Long requestingInvestorId) {
        return sourceArtifactRepository.findBySha256Hash(sha256Hash)
            .map(artifact -> {
                enforceInvestorAccess(artifact, requestingInvestorId);
                return toDetailDto(artifact);
            });
    }

    public Optional<SourceArtifactDetailDto> getArtifactById(Long id) {
        return sourceArtifactRepository.findById(id)
            .map(this::toDetailDto);
    }

    public Optional<SourceArtifactDetailDto> getArtifactById(Long id, Long requestingInvestorId) {
        return sourceArtifactRepository.findById(id)
            .map(artifact -> {
                enforceInvestorAccess(artifact, requestingInvestorId);
                return toDetailDto(artifact);
            });
    }

    public Optional<byte[]> getArtifactPayload(String sha256Hash) {
        return sourceArtifactRepository.findBySha256Hash(sha256Hash)
            .flatMap(artifact -> {
                if (artifact.getSha256Hash() != null) {
                    Optional<byte[]> diskBytes = storageService.retrieveArtifact(artifact.getSha256Hash());
                    if (diskBytes.isPresent()) {
                        return diskBytes;
                    }
                }
                return Optional.ofNullable(artifact.getPayloadBlob());
            });
    }

    public Optional<byte[]> getArtifactPayload(String sha256Hash, Long requestingInvestorId) {
        return sourceArtifactRepository.findBySha256Hash(sha256Hash)
            .flatMap(artifact -> {
                enforceInvestorAccess(artifact, requestingInvestorId);
                if (artifact.getSha256Hash() != null) {
                    Optional<byte[]> diskBytes = storageService.retrieveArtifact(artifact.getSha256Hash());
                    if (diskBytes.isPresent()) {
                        return diskBytes;
                    }
                }
                return Optional.ofNullable(artifact.getPayloadBlob());
            });
    }

    public List<SourceArtifactSummaryDto> listRecentArtifacts(int limit) {
        return sourceArtifactRepository.findAll().stream()
            .filter(a -> !Boolean.TRUE.equals(a.getIsInvestorPrivate())) // Public query never leaks investor-private CAS records
            .sorted((a, b) -> b.getRetrievalTimestamp().compareTo(a.getRetrievalTimestamp()))
            .limit(limit)
            .map(this::toSummaryDto)
            .collect(Collectors.toList());
    }

    public List<SourceArtifactSummaryDto> listInvestorPrivateArtifacts(Long investorId) {
        return sourceArtifactRepository.findAll().stream()
            .filter(a -> Boolean.TRUE.equals(a.getIsInvestorPrivate()) &&
                         a.getOwningInvestor() != null &&
                         a.getOwningInvestor().getId().equals(investorId))
            .sorted((a, b) -> b.getRetrievalTimestamp().compareTo(a.getRetrievalTimestamp()))
            .map(this::toSummaryDto)
            .collect(Collectors.toList());
    }

    @Transactional
    public void updateArtifactGovernance(
        Long artifactId,
        String authenticityStatus,
        String corroborationStatus,
        String publisherIdentity,
        String retentionClassification,
        String retentionPolicyRef
    ) {
        sourceArtifactRepository.findById(artifactId).ifPresent(artifact -> {
            if (authenticityStatus != null) artifact.setAuthenticityStatus(authenticityStatus);
            if (corroborationStatus != null) artifact.setCorroborationStatus(corroborationStatus);
            if (publisherIdentity != null) artifact.setPublisherIdentity(publisherIdentity);
            if (retentionClassification != null) artifact.setRetentionClassification(retentionClassification);
            if (retentionPolicyRef != null) artifact.setRetentionPolicyRef(retentionPolicyRef);
            sourceArtifactRepository.save(artifact);
        });
    }

    private void enforceInvestorAccess(SourceArtifact artifact, Long requestingInvestorId) {
        if (Boolean.TRUE.equals(artifact.getIsInvestorPrivate())) {
            Long ownerId = artifact.getOwningInvestor() != null ? artifact.getOwningInvestor().getId() : null;
            if (ownerId == null || !ownerId.equals(requestingInvestorId)) {
                throw new SecurityException("INVESTOR_DATA_ISOLATION_VIOLATION: Access denied to private CAS source artifact.");
            }
        }
    }

    private SourceArtifactSummaryDto toSummaryDto(SourceArtifact artifact) {
        boolean verified = verifyIntegrity(artifact);
        String dsCode = artifact.getDataSource() != null ? artifact.getDataSource().getCode() : "UNKNOWN";
        Long ownerId = artifact.getOwningInvestor() != null ? artifact.getOwningInvestor().getId() : null;
        return new SourceArtifactSummaryDto(
            artifact.getId(),
            dsCode,
            artifact.getRetrievalTimestamp(),
            artifact.getArtifactType(),
            artifact.getSha256Hash(),
            artifact.getByteSize(),
            artifact.getStorageUri(),
            verified,
            artifact.getStorageBackend() != null ? artifact.getStorageBackend() : "DATABASE_BLOB",
            artifact.getIngestionStatus() != null ? artifact.getIngestionStatus() : "INGESTED",
            artifact.getSourceAuthorityTier() != null ? artifact.getSourceAuthorityTier() : "TIER_1_OFFICIAL_REGULATOR",
            artifact.getStorageState() != null ? artifact.getStorageState() : "HOT_BLOB",
            Boolean.TRUE.equals(artifact.getIsInvestorPrivate()),
            ownerId,
            artifact.getAuthenticityStatus() != null ? artifact.getAuthenticityStatus() : "OFFICIAL_VERIFIED",
            artifact.getCorroborationStatus() != null ? artifact.getCorroborationStatus() : "SINGLE_SOURCE_UNCHECKED",
            artifact.getPublisherIdentity() != null ? artifact.getPublisherIdentity() : "AMFI",
            artifact.getRetentionClassification() != null ? artifact.getRetentionClassification() : "PERMANENT_REGULATORY"
        );
    }

    private SourceArtifactDetailDto toDetailDto(SourceArtifact artifact) {
        boolean verified = verifyIntegrity(artifact);
        String dsCode = artifact.getDataSource() != null ? artifact.getDataSource().getCode() : "UNKNOWN";
        Long ownerId = artifact.getOwningInvestor() != null ? artifact.getOwningInvestor().getId() : null;
        byte[] payload = null;
        if (artifact.getSha256Hash() != null) {
            payload = storageService.retrieveArtifact(artifact.getSha256Hash()).orElse(artifact.getPayloadBlob());
        } else {
            payload = artifact.getPayloadBlob();
        }

        String preview = null;
        if (payload != null && payload.length > 0) {
            int previewLen = Math.min(payload.length, 1024);
            preview = new String(payload, 0, previewLen, java.nio.charset.StandardCharsets.UTF_8);
        }

        return new SourceArtifactDetailDto(
            artifact.getId(),
            dsCode,
            artifact.getRetrievalTimestamp(),
            artifact.getArtifactType(),
            artifact.getSha256Hash(),
            artifact.getByteSize(),
            artifact.getStorageUri(),
            verified,
            preview,
            artifact.getStorageBackend() != null ? artifact.getStorageBackend() : "DATABASE_BLOB",
            artifact.getIngestionStatus() != null ? artifact.getIngestionStatus() : "INGESTED",
            artifact.getSourceAuthorityTier() != null ? artifact.getSourceAuthorityTier() : "TIER_1_OFFICIAL_REGULATOR",
            artifact.getStorageState() != null ? artifact.getStorageState() : "HOT_BLOB",
            Boolean.TRUE.equals(artifact.getIsInvestorPrivate()),
            ownerId,
            artifact.getAuthenticityStatus() != null ? artifact.getAuthenticityStatus() : "OFFICIAL_VERIFIED",
            artifact.getCorroborationStatus() != null ? artifact.getCorroborationStatus() : "SINGLE_SOURCE_UNCHECKED",
            artifact.getPublisherIdentity() != null ? artifact.getPublisherIdentity() : "AMFI",
            artifact.getRetrievalOrigin(),
            artifact.getRetentionClassification() != null ? artifact.getRetentionClassification() : "PERMANENT_REGULATORY",
            artifact.getRetentionPolicyRef() != null ? artifact.getRetentionPolicyRef() : "SEBI_RECORD_KEEPING_CIRCULAR",
            Boolean.TRUE.equals(artifact.getDeletionEligibility())
        );
    }

    private boolean verifyIntegrity(SourceArtifact artifact) {
        if (artifact.getSha256Hash() == null) {
            return false;
        }
        Optional<byte[]> diskBytes = storageService.retrieveArtifact(artifact.getSha256Hash());
        if (diskBytes.isPresent()) {
            return true; // Already verified by retrieveArtifact
        }
        if (artifact.getPayloadBlob() != null) {
            return storageService.verifyIntegrity(artifact.getPayloadBlob(), artifact.getSha256Hash());
        }
        return false;
    }
}
