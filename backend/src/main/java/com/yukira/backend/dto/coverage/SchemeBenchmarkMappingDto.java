package com.yukira.backend.dto.coverage;

import java.time.LocalDate;
import java.time.ZonedDateTime;

public record SchemeBenchmarkMappingDto(
    Long id,
    Long schemeId,
    String schemeName,
    Long benchmarkId,
    String benchmarkCode,
    String benchmarkName,
    String mappingRole,
    String mappingSource,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    Boolean isTri,
    String provenanceReference,
    ZonedDateTime createdAt
) {}

