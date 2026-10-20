package com.yukira.backend.dto.watchlist;

import java.math.BigDecimal;

public record WatchlistResponse(
    Long id,
    Long schemeId,
    String schemeCode,
    String schemeName,
    String amcName,
    Long schemeOptionId,
    String optionType,
    String planType,
    String amfiCode,
    String isin,
    BigDecimal latestNav,
    String latestNavDate,
    Double yukiraScore,
    String yukiraScoreStatus,
    String reasonSaved,
    String investigationQuestion,
    String nextStep,
    String createdAt,
    String updatedAt
) {}
