package com.yukira.backend.dto.watchlist;

import java.math.BigDecimal;

public record WatchlistResponse(
    Long id,
    Long schemeId,
    String schemeCode,
    String schemeName,
    String amcName,
    String category,
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
    String updatedAt,
    Boolean isHeldInPortfolio,
    Long portfolioHoldingId,
    BigDecimal holdingUnits,
    String heldDifferentVariantNotice,
    String thesisStatus,
    String associatedFindingId
) {
    public WatchlistResponse(
        Long id,
        Long schemeId,
        String schemeCode,
        String schemeName,
        String amcName,
        String category,
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
        String updatedAt,
        Boolean isHeldInPortfolio,
        Long portfolioHoldingId,
        BigDecimal holdingUnits,
        String heldDifferentVariantNotice
    ) {
        this(
            id, schemeId, schemeCode, schemeName, amcName, category, schemeOptionId, optionType, planType,
            amfiCode, isin, latestNav, latestNavDate, yukiraScore, yukiraScoreStatus, reasonSaved,
            investigationQuestion, nextStep, createdAt, updatedAt, isHeldInPortfolio, portfolioHoldingId, holdingUnits, heldDifferentVariantNotice,
            "ACTIVE_HYPOTHESIS", null
        );
    }

    public WatchlistResponse(
        Long id,
        Long schemeId,
        String schemeCode,
        String schemeName,
        String amcName,
        String category,
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
    ) {
        this(
            id, schemeId, schemeCode, schemeName, amcName, category, schemeOptionId, optionType, planType,
            amfiCode, isin, latestNav, latestNavDate, yukiraScore, yukiraScoreStatus, reasonSaved,
            investigationQuestion, nextStep, createdAt, updatedAt, false, null, null, null,
            "ACTIVE_HYPOTHESIS", null
        );
    }

    public WatchlistResponse(
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
    ) {
        this(id, schemeId, schemeCode, schemeName, amcName, null, schemeOptionId, optionType, planType, amfiCode, isin, latestNav, latestNavDate, yukiraScore, yukiraScoreStatus, reasonSaved, investigationQuestion, nextStep, createdAt, updatedAt);
    }
}
