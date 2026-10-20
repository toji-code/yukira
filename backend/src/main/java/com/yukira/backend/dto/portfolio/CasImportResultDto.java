package com.yukira.backend.dto.portfolio;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

public record CasImportResultDto(
    Long importId,
    String sourceFilename,
    String fileType,
    String status, // PENDING_REVIEW, CONFIRMED, FAILED, CANCELLED
    String importQualityState, // VALID, DATA_QUALITY_LIMITED, PARTIAL, FAILED
    String casProvider, // CAMS, KFINTECH, UNKNOWN
    int totalRecordsDetected,
    int resolvedRecordsCount,
    int unresolvedRecordsCount,
    int ambiguousRecordsCount,
    Long sourceArtifactId,
    String sha256Hash,
    LocalDate statementDate,
    String parserVersion,
    ZonedDateTime createdAt,
    ZonedDateTime confirmedAt,
    List<CasImportHoldingDetailDto> holdings,
    List<String> limitations
) {}
