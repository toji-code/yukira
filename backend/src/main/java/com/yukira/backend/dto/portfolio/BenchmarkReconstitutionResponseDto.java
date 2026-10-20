package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BenchmarkReconstitutionResponseDto(
    Long benchmarkId,
    String benchmarkCode,
    String benchmarkName,
    LocalDate fromDate,
    LocalDate toDate,
    long daysBetween,
    String observationType, // "TWO_POINT_OBSERVED_TRANSITION" vs "OFFICIAL_INDEX_RECONSTITUTION"
    String comparisonCoverageStatus, // "VERIFIED_PARTIAL", "VERIFIED_COMPLETE", "UNVERIFIED_SOURCE", "UNAVAILABLE"
    int totalFromCount,
    int totalToCount,
    int retainedCount,
    int enteredCount,
    int exitedCount,
    BigDecimal turnoverPct,
    int unresolvedCount,
    List<ReconstitutionMemberDto> enteredConstituents,
    List<ReconstitutionMemberDto> exitedConstituents,
    List<ReconstitutionMemberDto> retainedConstituents,
    String epistemicDisclaimer,
    String sourceArtifactSha256From,
    String sourceArtifactSha256To,
    String knowledgeCutoff
) {}
