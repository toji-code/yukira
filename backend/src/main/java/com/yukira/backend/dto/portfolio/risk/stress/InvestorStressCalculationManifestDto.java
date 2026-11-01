package com.yukira.backend.dto.portfolio.risk.stress;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Immutable DTO representing a persistent stress calculation manifest (Task 85).
 */
public record InvestorStressCalculationManifestDto(
    Long id,
    Long calculationRunId,
    String calculationRunCode,
    Long portfolioId,
    Long investorId,
    String snapshotId,
    String snapshotFingerprint,
    String inputHash,
    String methodologyVersion,
    String governanceStatus,
    OffsetDateTime knowledgeCutoff,
    LocalDate analysisCutoffDate,
    String manifestPayloadJson,
    OffsetDateTime createdAt
) {}

