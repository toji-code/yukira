package com.yukira.backend.dto.portfolio;

import java.time.ZonedDateTime;
import java.util.List;

public record CasImportConfirmResponseDto(
    Long importId,
    String status, // CONFIRMED
    int holdingsAddedCount,
    int holdingsUpdatedCount,
    int holdingsUnchangedCount,
    int unresolvedSkippedCount,
    ZonedDateTime confirmedAt,
    List<String> messages
) {}
