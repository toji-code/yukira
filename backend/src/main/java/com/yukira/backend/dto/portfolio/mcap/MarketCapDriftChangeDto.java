package com.yukira.backend.dto.portfolio.mcap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Mathematical decomposition of style drift between consecutive portfolio disclosures.
 *
 * Epistemic & Mathematical Rules:
 * 1. \Delta W_{obs} = \Delta W_{weight} + \Delta W_{reclass} + \Delta W_{disclosure} + \Delta W_{unresolved}
 * 2. isReconciled verifies |\Delta W_{obs} - sum(components)| <= tolerance (0.02 pp).
 * 3. Driver classification distinguishes portfolio adjustment, security entry/exit,
 *    classification shift, disclosure scope shift, and unresolved discrepancies.
 * 4. Weight changes must NEVER be attributed solely to manager skill or purchases.
 */
public record MarketCapDriftChangeDto(
    LocalDate earlierDate,
    LocalDate laterDate,
    String category,
    BigDecimal earlierWeightPct,
    BigDecimal laterWeightPct,
    BigDecimal deltaWeightPct,
    BigDecimal weightChangeComponent,
    BigDecimal reclassificationComponent,
    BigDecimal disclosureScopeComponent,
    BigDecimal unresolvedComponent,
    boolean isReconciled,
    BigDecimal reconciliationTolerance,
    String primaryDriver,
    boolean isComparable,
    String nonComparableReason,
    String explanation,
    BigDecimal earlierCoveragePct,
    BigDecimal laterCoveragePct,
    int earlierHoldingCount,
    int laterHoldingCount,
    List<MarketCapReclassificationItemDto> reclassifiedHoldings
) {}
