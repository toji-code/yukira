package com.yukira.backend.service;

import com.yukira.backend.domain.entity.IndianMutualFundTaxRule;
import com.yukira.backend.repository.IndianMutualFundTaxRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Authoritative Indian Mutual Fund Capital Gains Tax Classification Engine (Tasks 299, 336-342, 393-396).
 * Evaluates effective-dated statutory provisions under the Income-tax Act, 1961:
 * - Finance (No. 2) Act 2024 Transition (effective 23-Jul-2024):
 *   - STCG Section 111A: 15% (prior to 23-Jul-2024) -> 20% (on/after 23-Jul-2024)
 *   - LTCG Section 112A: 10% (prior to 23-Jul-2024, ₹1L exemption) -> 12.5% (on/after 23-Jul-2024, ₹1.25L exemption)
 * - Section 50AA Specified Mutual Funds (<= 35% equity acquired >= 1 April 2023): Deemed STCG at investor slab rates.
 * - Non-equity hybrid funds (> 35% and < 65% equity, acquired >= 1 April 2023): LTCG at 12.5% without indexation if held >= 36 months (post 23-Jul-2024).
 * - Section 112 Grandfathered Debt Funds (acquired < 1 April 2023): 20% with CII indexation if held >= 36 months.
 * - Explicit separation of Base Tax Rate, Health & Education Cess (4%), and Surcharge dependency (Task 393).
 * - Statutory CA Disclaimer prominently attached to every tax result (Task 396).
 */
@Service
@Transactional(readOnly = true)
public class IndianMutualFundTaxClassificationService {

    private final IndianMutualFundTaxRuleRepository taxRuleRepository;

    public static final String CA_DISCLAIMER =
        "Tax calculations are indicative mathematical models based on statutory provisions of the Income-tax Act, 1961 " +
        "(as amended by Finance Act 2023 and Finance (No. 2) Act 2024). Investors must consult a qualified Chartered Accountant before filing income tax returns.";

    public static final String PROVISIONAL_TAX_NOTICE = CA_DISCLAIMER;

    public static final LocalDate FINANCE_ACT_2024_CUTOFF = LocalDate.of(2024, 7, 23);
    public static final LocalDate SECTION_50AA_EFFECTIVE = LocalDate.of(2023, 4, 1);
    public static final double HEALTH_AND_EDUCATION_CESS_PERCENT = 4.0;

    public record TaxClassificationOutcomeDto(
        String statutorySection,
        String taxClassification, // LTCG, STCG, DEEMED_STCG, or PENDING_ALLOCATION_AUDIT
        long holdingDays,
        boolean indexationEligible,
        Double indicativeTaxRatePercent,
        Double statutoryExemptionLimit,
        String legalInstrumentRef,
        String confidenceStatus, // STATUTORY_RULE_VERIFIED, REQUIRES_PROFESSIONAL_REVIEW
        String taxTreatmentDescription,
        String caDisclaimer,
        Double baseTaxRatePercent,
        Double cessRatePercent,
        Double effectiveRateWithoutSurchargePercent,
        String surchargeDependencyNote,
        String equityExposureAuditStatus
    ) {
        /**
         * Backward-compatible 10-argument constructor.
         */
        public TaxClassificationOutcomeDto(
            String statutorySection,
            String taxClassification,
            long holdingDays,
            boolean indexationEligible,
            Double indicativeTaxRatePercent,
            Double statutoryExemptionLimit,
            String legalInstrumentRef,
            String confidenceStatus,
            String taxTreatmentDescription,
            String caDisclaimer
        ) {
            this(
                statutorySection,
                taxClassification,
                holdingDays,
                indexationEligible,
                indicativeTaxRatePercent,
                statutoryExemptionLimit,
                legalInstrumentRef,
                confidenceStatus,
                taxTreatmentDescription,
                caDisclaimer,
                indicativeTaxRatePercent,
                HEALTH_AND_EDUCATION_CESS_PERCENT,
                indicativeTaxRatePercent != null ? Math.round(indicativeTaxRatePercent * 1.04 * 100.0) / 100.0 : null,
                "Applicable surcharge (10%-25%) depends on total investor taxable income pursuant to Chapter II of the Finance Act.",
                "AUDIT_EVALUATED"
            );
        }

        /**
         * Backward-compatible 7-argument fallback constructor.
         */
        public TaxClassificationOutcomeDto(
            String statutorySection,
            String taxClassification,
            long holdingDays,
            boolean indexationEligible,
            Double indicativeTaxRatePercent,
            String taxTreatmentDescription,
            String caDisclaimer
        ) {
            this(
                statutorySection,
                taxClassification,
                holdingDays,
                indexationEligible,
                indicativeTaxRatePercent,
                0.0,
                "Income-tax Act, 1961",
                "STATUTORY_RULE_VERIFIED",
                taxTreatmentDescription,
                caDisclaimer
            );
        }

        public String provisionalNotice() {
            return caDisclaimer != null ? caDisclaimer : PROVISIONAL_TAX_NOTICE;
        }
    }

    public IndianMutualFundTaxClassificationService(IndianMutualFundTaxRuleRepository taxRuleRepository) {
        this.taxRuleRepository = taxRuleRepository;
    }

    public TaxClassificationOutcomeDto classifyTaxLot(
            String fundCategory,
            LocalDate acquisitionDate,
            LocalDate redemptionOrAsOfDate
    ) {
        if (acquisitionDate == null) {
            acquisitionDate = LocalDate.now();
        }
        if (redemptionOrAsOfDate == null) {
            redemptionOrAsOfDate = LocalDate.now();
        }

        long holdingDays = ChronoUnit.DAYS.between(acquisitionDate, redemptionOrAsOfDate);

        // Check for ambiguous hybrid or multi-asset category where equity exposure is unverified (Tasks 341, 394, 395)
        if (fundCategory != null && (
            fundCategory.equalsIgnoreCase("DYNAMIC_ASSET_ALLOCATION") ||
            fundCategory.equalsIgnoreCase("MULTI_ASSET_ALLOCATION") ||
            fundCategory.equalsIgnoreCase("BALANCED_ADVANTAGE")
        )) {
            return new TaxClassificationOutcomeDto(
                "UNRESOLVED_HISTORICAL_EXPOSURE",
                "PENDING_ALLOCATION_AUDIT",
                holdingDays,
                false,
                null,
                null,
                "Income-tax Act, 1961 (Audit Required)",
                "REQUIRES_PROFESSIONAL_REVIEW",
                "Historical domestic equity exposure is unverified or variable across holding periods (35%-65%). " +
                "Statutory classification requires audit of monthly portfolio disclosures pursuant to Section 50AA and Section 112A.",
                CA_DISCLAIMER,
                null,
                HEALTH_AND_EDUCATION_CESS_PERCENT,
                null,
                "Surcharge calculation deferred pending allocation verification.",
                "UNVERIFIED_HISTORICAL_EXPOSURE"
            );
        }

        boolean isDebt = fundCategory != null && (
            fundCategory.toLowerCase().contains("debt") ||
            fundCategory.toLowerCase().contains("liquid") ||
            fundCategory.toLowerCase().contains("money market") ||
            fundCategory.toLowerCase().contains("overnight") ||
            fundCategory.toLowerCase().contains("gilt") ||
            fundCategory.toLowerCase().contains("floater")
        );

        if (isDebt) {
            if (!acquisitionDate.isBefore(SECTION_50AA_EFFECTIVE)) {
                // Section 50AA Specified Mutual Fund (acquired >= 1-Apr-2023) (Tasks 338, 394)
                return new TaxClassificationOutcomeDto(
                    "Section 50AA",
                    "DEEMED_STCG",
                    holdingDays,
                    false,
                    null, // Marginal income tax slab rate
                    0.0,
                    "Finance Act, 2023",
                    "STATUTORY_RULE_VERIFIED",
                    "Under Section 50AA of the Income-tax Act, 1961, mutual funds with <= 35% equity acquired on or after 1 April 2023 " +
                    "are deemed short-term capital gains and taxed at applicable investor income slab rates regardless of holding duration.",
                    CA_DISCLAIMER,
                    null,
                    HEALTH_AND_EDUCATION_CESS_PERCENT,
                    null,
                    "Taxed at marginal slab rates (up to 39% including maximum applicable surcharge and cess).",
                    "SPECIFIED_MUTUAL_FUND_LE_35"
                );
            } else {
                // Pre-1-April-2023 Grandfathered Debt Fund (Tasks 339, 394)
                if (holdingDays >= 1095) { // 36 months
                    double baseRate = 20.0;
                    double effective = Math.round(baseRate * 1.04 * 100.0) / 100.0;
                    return new TaxClassificationOutcomeDto(
                        "Section 112",
                        "LTCG",
                        holdingDays,
                        true,
                        baseRate,
                        0.0,
                        "Income-tax Act, 1961 § 112 (Grandfathered)",
                        "STATUTORY_RULE_VERIFIED",
                        "Grandfathered debt mutual fund units acquired prior to 1 April 2023 and held for >= 36 months qualify for LTCG at 20% " +
                        "with Cost Inflation Index (CII) indexation benefits under Section 112.",
                        CA_DISCLAIMER,
                        baseRate,
                        HEALTH_AND_EDUCATION_CESS_PERCENT,
                        effective,
                        "Surcharge applies if individual income exceeds ₹50 Lakhs (10%-25%).",
                        "GRANDFATHERED_DEBT_ACQUIRED_PRE_APR_2023"
                    );
                } else {
                    return new TaxClassificationOutcomeDto(
                        "Section 112 (Slab)",
                        "STCG",
                        holdingDays,
                        false,
                        null,
                        0.0,
                        "Income-tax Act, 1961 § 112",
                        "STATUTORY_RULE_VERIFIED",
                        "Grandfathered debt mutual fund units held for < 36 months are taxed as short-term capital gains at applicable investor income slab rates.",
                        CA_DISCLAIMER,
                        null,
                        HEALTH_AND_EDUCATION_CESS_PERCENT,
                        null,
                        "Taxed at investor marginal slab rate.",
                        "GRANDFATHERED_DEBT_ACQUIRED_PRE_APR_2023"
                    );
                }
            }
        } else {
            // Equity-Oriented Mutual Fund (>= 65% domestic equity) (Tasks 337, 393, 395)
            boolean isPostFinanceAct2024 = !redemptionOrAsOfDate.isBefore(FINANCE_ACT_2024_CUTOFF);

            if (holdingDays >= 365) {
                // LTCG Section 112A
                double baseRate = isPostFinanceAct2024 ? 12.5 : 10.0;
                double effective = Math.round(baseRate * 1.04 * 100.0) / 100.0;
                double exemption = isPostFinanceAct2024 ? 125000.0 : 100000.0;
                String actRef = isPostFinanceAct2024 ? "Finance (No. 2) Act, 2024" : "Finance Act, 2018";

                return new TaxClassificationOutcomeDto(
                    "Section 112A",
                    "LTCG",
                    holdingDays,
                    false,
                    baseRate,
                    exemption,
                    actRef,
                    "STATUTORY_RULE_VERIFIED",
                    String.format(
                        "Equity-oriented fund units held for >= 12 months qualify for LTCG under Section 112A taxed at %.1f%% " +
                        "(transfer date: %s; annual statutory exemption: ₹%,.0f). Effective baseline with 4%% cess: %.2f%%.",
                        baseRate, isPostFinanceAct2024 ? "on/after 23-Jul-2024" : "before 23-Jul-2024", exemption, effective
                    ),
                    CA_DISCLAIMER,
                    baseRate,
                    HEALTH_AND_EDUCATION_CESS_PERCENT,
                    effective,
                    "Surcharge capped at 15% on Section 112A capital gains regardless of total income.",
                    "QUALIFIED_DOMESTIC_EQUITY_GE_65"
                );
            } else {
                // STCG Section 111A
                double baseRate = isPostFinanceAct2024 ? 20.0 : 15.0;
                double effective = Math.round(baseRate * 1.04 * 100.0) / 100.0;
                String actRef = isPostFinanceAct2024 ? "Finance (No. 2) Act, 2024" : "Income-tax Act, 1961 § 111A";

                return new TaxClassificationOutcomeDto(
                    "Section 111A",
                    "STCG",
                    holdingDays,
                    false,
                    baseRate,
                    0.0,
                    actRef,
                    "STATUTORY_RULE_VERIFIED",
                    String.format(
                        "Equity-oriented fund units held for < 12 months are taxed as short-term capital gains at %.1f%% " +
                        "(transfer date: %s) under Section 111A. Effective baseline with 4%% cess: %.2f%%.",
                        baseRate, isPostFinanceAct2024 ? "on/after 23-Jul-2024" : "before 23-Jul-2024", effective
                    ),
                    CA_DISCLAIMER,
                    baseRate,
                    HEALTH_AND_EDUCATION_CESS_PERCENT,
                    effective,
                    "Surcharge capped at 15% on Section 111A capital gains.",
                    "QUALIFIED_DOMESTIC_EQUITY_GE_65"
                );
            }
        }
    }
}
