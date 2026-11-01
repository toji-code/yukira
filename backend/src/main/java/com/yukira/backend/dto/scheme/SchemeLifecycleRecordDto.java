package com.yukira.backend.dto.scheme;

import java.time.LocalDate;
import java.time.ZonedDateTime;

public record SchemeLifecycleRecordDto(
    Long id,
    Long schemeId,
    String schemeName,
    String lifecycleStatus,
    LocalDate effectiveDate,
    Long successorSchemeId,
    String successorSchemeName,
    Long predecessorSchemeId,
    String predecessorSchemeName,
    String regulatoryCircularRef,
    String remarks,
    ZonedDateTime createdAt
) {}

