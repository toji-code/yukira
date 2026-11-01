package com.yukira.backend.dto.portfolio.risk.stress;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HexFormat;

/**
 * Explicit portfolio allocation snapshot used to construct the historical stress proxy (Tasks 48, 62, 63).
 *
 * Epistemic Guardrails:
 * - Historical stress analysis uses the investor's current known-value portfolio weights
 *   to construct a hypothetical constant-weight daily rebalanced proxy.
 * - This is NOT a historical reconstruction of the investor's actual transactions, cash flows,
 *   or past holdings.
 * - Separates known valued capital from total portfolio capital (Task 62).
 * - Distinguishes complete valuation from partial valuation exposure.
 * - Immutable content-addressed snapshot fingerprint guarantees deterministic provenance across runs (Task 63).
 */
public record PortfolioAllocationSnapshotDto(
    String snapshotId,
    LocalDate allocationEffectiveDate,
    LocalDate valuationAsOfDate,
    OffsetDateTime knowledgeCutoff,
    BigDecimal knownValuedCapitalAmount,
    BigDecimal totalPortfolioCapitalAmount,
    BigDecimal knownValuedCapitalPercentage,
    BigDecimal valuedAllocationNormalizedPercentage,
    int valuedPositionCount,
    int unvaluedPositionCount,
    int totalPositionCount,
    int includedConstituentCount,
    int excludedOrUnavailableConstituentCount,
    String unvaluedPositionExposureStatus,
    String weightCalculationConvention,
    BigDecimal weightNormalizationDenominator,
    String snapshotFingerprint,
    String historicalProxyModelDisclaimer,
    boolean isStaticAllocationProxy
) {
    /**
     * Backward-compatible 12-parameter constructor (Task 48 compatibility).
     */
    public PortfolioAllocationSnapshotDto(
        String snapshotId,
        LocalDate allocationEffectiveDate,
        LocalDate valuationAsOfDate,
        OffsetDateTime knowledgeCutoff,
        BigDecimal knownValuedPortfolioDenominator,
        int valuedConstituentCount,
        int unvaluedPositionCount,
        int includedConstituentCount,
        int excludedOrUnavailableConstituentCount,
        String weightCalculationConvention,
        BigDecimal weightNormalizationDenominator,
        String historicalProxyModelDisclaimer
    ) {
        this(
            snapshotId,
            allocationEffectiveDate,
            valuationAsOfDate,
            knowledgeCutoff,
            knownValuedPortfolioDenominator,
            null, // totalPortfolioCapitalAmount
            unvaluedPositionCount == 0 ? BigDecimal.valueOf(100.0) : null,
            BigDecimal.valueOf(100.0), // valuedAllocationNormalizedPercentage
            valuedConstituentCount,
            unvaluedPositionCount,
            valuedConstituentCount + unvaluedPositionCount,
            includedConstituentCount,
            excludedOrUnavailableConstituentCount,
            unvaluedPositionCount == 0 ? "COMPLETE_VALUATION" : "PARTIAL_VALUATION_EXPOSURE_PRESENT",
            weightCalculationConvention,
            weightNormalizationDenominator,
            computeSnapshotFingerprint(snapshotId, allocationEffectiveDate, valuationAsOfDate, valuedConstituentCount, unvaluedPositionCount),
            historicalProxyModelDisclaimer,
            true
        );
    }

    /**
     * Backward-compatible 11-parameter constructor (InvestorPortfolioRiskDiversificationService call compatibility).
     */
    public PortfolioAllocationSnapshotDto(
        String snapshotId,
        LocalDate asOf,
        OffsetDateTime cutoff,
        String weightCalculationConvention,
        int valuedCount,
        int unvaluedCount,
        BigDecimal knownValuedCapitalPercentage,
        boolean isComplete,
        String modelName,
        String disclaimer,
        boolean isStaticAllocationProxy
    ) {
        this(
            snapshotId,
            asOf,
            asOf,
            cutoff,
            null, // knownValuedCapitalAmount
            null, // totalPortfolioCapitalAmount
            knownValuedCapitalPercentage,
            BigDecimal.valueOf(100.0),
            valuedCount,
            unvaluedCount,
            valuedCount + unvaluedCount,
            valuedCount,
            unvaluedCount,
            isComplete ? "COMPLETE_VALUATION" : "PARTIAL_VALUATION_EXPOSURE_PRESENT",
            weightCalculationConvention,
            BigDecimal.valueOf(100.0),
            computeSnapshotFingerprint(snapshotId, asOf, asOf, valuedCount, unvaluedCount),
            disclaimer,
            isStaticAllocationProxy
        );
    }

    /**
     * Computes a deterministic SHA-256 fingerprint for snapshot content integrity.
     */
    public static String computeSnapshotFingerprint(
        String snapshotId,
        LocalDate allocDate,
        LocalDate valDate,
        int valuedCount,
        int unvaluedCount
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = (snapshotId != null ? snapshotId : "") + "|"
                + (allocDate != null ? allocDate.toString() : "") + "|"
                + (valDate != null ? valDate.toString() : "") + "|"
                + valuedCount + "|" + unvaluedCount;
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return "HASH-UNAVAILABLE-" + snapshotId;
        }
    }
}
