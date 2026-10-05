package com.yukira.backend.dto.enrichment;

import java.util.List;

public record EnrichedFundProfileDto(
    Long schemeId,
    Long schemeOptionId,
    String schemeName,
    String schemeCode,
    String planType,
    String optionType,
    String amfiCode,
    String isin,
    AumDto aum,
    ExpenseRatioDto expenseRatio,
    List<FundManagerDto> fundManagers,
    InvestmentTermsDto investmentTerms,
    HoldingsSummaryDto holdingsSummary,
    String epistemicStatus
) {}
