package com.yukira.backend.dto.portfolio.risk.stress;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Immutable DTO representing a persistent historical stress investigation finding (Tasks 80, 81, 101, 102).
 */
public record InvestorStressFindingDto(
    Long id,
    Long investorId,
    Long portfolioId,
    String findingId,
    int findingVersion,
    String calculationRunCode,
    String snapshotId,
    String snapshotFingerprint,
    String episodeCode,
    String episodeName,
    String findingType,
    String findingSeverity,
    String findingHeadline,
    String findingDetail,
    String methodologyVersion,
    String governanceStatus,
    OffsetDateTime knowledgeCutoffDate,
    LocalDate asOfDate,
    String lifecycleStatus,
    Long supersededByFindingId,
    String transitionReason,
    String transitionBy,
    String investorNotes,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    /**
     * Backward-compatible 22-parameter constructor.
     */
    public InvestorStressFindingDto(
        Long id,
        Long investorId,
        Long portfolioId,
        String findingId,
        int findingVersion,
        String calculationRunCode,
        String snapshotId,
        String snapshotFingerprint,
        String episodeCode,
        String episodeName,
        String findingType,
        String findingSeverity,
        String findingHeadline,
        String findingDetail,
        String methodologyVersion,
        String governanceStatus,
        OffsetDateTime knowledgeCutoffDate,
        LocalDate asOfDate,
        String lifecycleStatus,
        Long supersededByFindingId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
        this(id, investorId, portfolioId, findingId, findingVersion, calculationRunCode,
             snapshotId, snapshotFingerprint, episodeCode, episodeName, findingType, findingSeverity,
             findingHeadline, findingDetail, methodologyVersion, governanceStatus, knowledgeCutoffDate,
             asOfDate, lifecycleStatus, supersededByFindingId, null, "SYSTEM", null, createdAt, updatedAt);
    }
}
