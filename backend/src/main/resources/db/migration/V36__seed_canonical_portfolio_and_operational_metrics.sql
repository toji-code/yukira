-- V36: Seed Canonical Portfolio Structure & Operational Data-Quality Metric Definitions
-- Enforces Phase 2H frozen methodology Table 4.1 candidates (PRT-01..PRT-05, GOV-01)
-- and operational data quality / monitoring evidence fields.

INSERT INTO metric_definition (
    metric_code, metric_name, analytical_dimension, metric_category,
    purpose, formula_display, units, default_frequency,
    benchmark_required, risk_free_required, point_in_time_required
) VALUES
(
    'PRT-01',
    'Top 10 Equity Holdings Concentration',
    'PORTFOLIO_CONSTRUCTION',
    'CONCENTRATION',
    'Sum of disclosed portfolio weights of the top 10 largest equity holdings.',
    'sum(w_i, i=1..10)',
    'PERCENTAGE',
    'MONTHLY',
    FALSE,
    FALSE,
    TRUE
),
(
    'PRT-02',
    'Effective Number of Holdings',
    'PORTFOLIO_CONSTRUCTION',
    'CONCENTRATION',
    'Inverse Herfindahl-Hirschman constituent count (1 / sum(w_i^2)) representing equivalent equal-weighted holdings count.',
    '1 / sum(w_i^2)',
    'COUNT',
    'MONTHLY',
    FALSE,
    FALSE,
    TRUE
),
(
    'PRT-03',
    'Active Share',
    'PORTFOLIO_CONSTRUCTION',
    'BENCHMARK_RELATIVE',
    'Proportion of fund equity portfolio holdings differing from primary benchmark constituent weights.',
    '0.5 * sum(|w_p,i - w_b,i|)',
    'PERCENTAGE',
    'MONTHLY',
    TRUE,
    FALSE,
    TRUE
),
(
    'PRT-04',
    'Monthly Weight Turnover',
    'PORTFOLIO_CONSTRUCTION',
    'CHURN',
    'Fraction of portfolio assets reallocated between consecutive monthly statutory disclosure snapshots.',
    '0.5 * sum(|w_t,i - w_t-1,i|)',
    'PERCENTAGE',
    'MONTHLY',
    FALSE,
    FALSE,
    TRUE
),
(
    'PRT-05',
    'Cash & Equivalent Allocation %',
    'PORTFOLIO_CONSTRUCTION',
    'ALLOCATION',
    'Proportion of total scheme net assets held in cash, reverse repos, TREPS, and liquid cash equivalents.',
    'Net_Assets_Cash / Total_Net_Assets * 100.0',
    'PERCENTAGE',
    'MONTHLY',
    FALSE,
    FALSE,
    TRUE
),
(
    'GOV-01',
    'Direct Plan TER',
    'GOVERNANCE_EXPENSE',
    'EXPENSE',
    'Declared point-in-time Total Expense Ratio for the Direct Plan option.',
    'Operating Expenses / Scheme AUM * 100.0',
    'PERCENTAGE',
    'POINT_IN_TIME',
    FALSE,
    FALSE,
    TRUE
),
(
    'PORTFOLIO_DRIFT',
    'Portfolio Target Allocation Drift',
    'OPERATIONAL_DATA_QUALITY',
    'ALLOCATION_DRIFT',
    'Maximum percentage point divergence between actual investor portfolio allocation and configured target bands.',
    'max_c(|Actual_Weight_c - Target_Weight_c|)',
    'PERCENTAGE',
    'POINT_IN_TIME',
    FALSE,
    FALSE,
    TRUE
),
(
    'PORTFOLIO_UNVALUED_POSITIONS',
    'Unvalued Portfolio Positions',
    'OPERATIONAL_DATA_QUALITY',
    'DATA_INTEGRITY',
    'Count of portfolio holdings lacking an effective NAV observation as of analysis cutoff date.',
    'Count(Holding_i with NAV == NULL)',
    'COUNT',
    'POINT_IN_TIME',
    FALSE,
    FALSE,
    TRUE
),
(
    'NAV_FRESHNESS',
    'NAV Observation Freshness',
    'OPERATIONAL_DATA_QUALITY',
    'FRESHNESS',
    'Calendar days elapsed since the latest verified NAV observation was published and recorded.',
    'today - latest_verified_effective_date',
    'DAYS',
    'DAILY',
    FALSE,
    FALSE,
    TRUE
),
(
    'HOLDINGS_FRESHNESS',
    'Monthly Holdings Disclosure Freshness',
    'OPERATIONAL_DATA_QUALITY',
    'FRESHNESS',
    'Calendar days elapsed since statutory month-end holdings disclosure date.',
    'today - statutory_reporting_date',
    'DAYS',
    'MONTHLY',
    FALSE,
    FALSE,
    TRUE
),
(
    'UNCLASSIFIED_EXPOSURE',
    'Unclassified Holdings Exposure',
    'OPERATIONAL_DATA_QUALITY',
    'COVERAGE',
    'Percentage of disclosed fund assets lacking verified security identifier mapping or sector classification.',
    'Unclassified_Weight / Total_Disclosed_Assets * 100.0',
    'PERCENTAGE',
    'MONTHLY',
    FALSE,
    FALSE,
    TRUE
)
ON CONFLICT (metric_code) DO NOTHING;

