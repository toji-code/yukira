/**
 * YUKIRA Quantitative Investment Intelligence Platform
 * Authoritative Metric-to-Evidence Definitions & Canonical Investigation Questions.
 *
 * Epistemic Rules:
 * 1. Zero frontend math: descriptive metadata and evidence linkages only.
 * 2. Realized descriptive metrics must NEVER be converted into investment advice or buy/sell recommendations.
 * 3. Candidate methodology must be explicitly flagged with candidate disclaimers.
 * 4. Missing metadata must resolve to explicit unavailable states, never fabricated.
 */

export interface MetricEvidenceDefinition {
  code: string;
  name: string;
  canonicalName?: string;
  analyticalDimension?: string;
  categoryLabel: string;
  governanceStatus: 'APPROVED' | 'CANDIDATE' | 'OPERATIONAL BASELINE' | 'OPERATIONAL_BASELINE' | 'IMPLEMENTED_CANDIDATE' | 'CANDIDATE_SPECIFICATION';
  rankingDirection?: 'HIGHER_IS_BETTER' | 'LOWER_IS_BETTER' | 'NEUTRAL';
  unit?: string;
  isDiagnostic?: boolean;
  parentMetricCode?: string | null;
  aliases?: string[];
  whyItMatters: string;
  evidenceDescription: string;
  mathematicalDefinition?: string;
  formulaSpecification: string;
  requiredInputs?: string[];
  returnConvention?: string;
  annualizationConvention?: string;
  measurementHorizon?: string;
  benchmarkDependency?: string;
  primaryBenchmarkCode?: string;
  riskFreeDependency?: string;
  riskFreeRateProxy?: string;
  availabilityRequirements?: string;
  missingDataBehavior?: string;
  methodologyVersion?: string;
  assumptions: Array<{ label: string; value: string }>;
  limitations: string;
  investigationQuestion: string;
  investigationReasonTemplate: (asOfDate?: string) => string;
}

export const CANONICAL_METRIC_EVIDENCE: Record<string, MetricEvidenceDefinition> = {
  'RET-03': {
    code: 'RET-03',
    name: 'Compound Annual Growth Rate (3Y CAGR)',
    categoryLabel: 'Annualized Compound Return',
    governanceStatus: 'CANDIDATE',
    rankingDirection: 'HIGHER_IS_BETTER',
    whyItMatters:
      'Normalizes cumulative multi-year capital growth onto an annualized geometric basis over leap-adjusted Julian trading years (365.25 days). It reveals how investor capital compounded over a continuous 36-month horizon.',
    evidenceDescription:
      '36 calendar months of official AMFI net asset values (minimum 700 trading days). Start and end dates are anchored to historical market trading close.',
    formulaSpecification:
      'CAGR = (NAV_end / NAV_start)^(365.25 / elapsed_calendar_days) - 1.0',
    assumptions: [
      { label: 'Annualization Convention', value: '365.25 Julian calendar days' },
      { label: 'Minimum History Threshold', value: '≥ 700 trading days' },
      { label: 'Reinvestment Assumption', value: 'Gross dividends reinvested (Growth option)' },
    ],
    limitations:
      'Conceals sub-period volatility, severe intermediate drawdowns, and the impact of cash-flow timing. Past annualized compound return provides zero statistical guarantee or forecast of future market cycle performance.',
    investigationQuestion:
      "Does the fund's 3-year compound growth depend on a single outlier quarter, or is it sustained across market regimes?",
    investigationReasonTemplate: (asOfDate) =>
      `Evaluating 3-Year CAGR (RET-03) growth consistency and intra-period path as of ${asOfDate || 'analysis cutoff'}.`,
  },

  'RET-02': {
    code: 'RET-02',
    name: 'Simple Period Return',
    categoryLabel: 'Point-to-Point Discrete Return',
    governanceStatus: 'OPERATIONAL BASELINE',
    rankingDirection: 'HIGHER_IS_BETTER',
    whyItMatters:
      'Direct percentage change in Net Asset Value between two discrete operational boundary dates. Serves as the immutable raw verification baseline for all multi-period compound and risk calculations.',
    evidenceDescription:
      'Exact boundary NAV observations cryptographically linked to official AMFI source artifacts (SHA-256 verified).',
    formulaSpecification:
      'R = (NAV_end - NAV_start) / NAV_start',
    assumptions: [
      { label: 'Lookback Tolerance', value: '4 calendar days for holiday adjustment' },
      { label: 'Annualization', value: 'None (Unannualized discrete period)' },
    ],
    limitations:
      'Highly sensitive to the arbitrary choice of start and end boundary dates. Does not reflect the intra-period volatility, cash flows, or path-dependent stress experienced between endpoints.',
    investigationQuestion:
      'Does the discrete period return hold up when evaluated over alternative operational boundary dates?',
    investigationReasonTemplate: (asOfDate) =>
      `Verifying raw point-to-point boundary return (RET-02) as of ${asOfDate || 'cutoff'}.`,
  },

  'RSK-01': {
    code: 'RSK-01',
    name: '3-Year Annualized Volatility',
    categoryLabel: 'Total Return Dispersion',
    governanceStatus: 'CANDIDATE',
    rankingDirection: 'LOWER_IS_BETTER',
    whyItMatters:
      'Quantifies the total dispersion of daily percentage returns around the sample mean, annualized by standard market convention (√252). Measures the magnitude of day-to-day value swings an investor must tolerate.',
    evidenceDescription:
      'Sample standard deviation with N-1 Bessel correction computed across continuous daily trading returns over 36 months.',
    formulaSpecification:
      'Volatility = Stdev_sample(daily_returns) * sqrt(252)',
    assumptions: [
      { label: 'Annualization Factor', value: '√252 trading days' },
      { label: 'Degrees of Freedom', value: 'N - 1 sample variance' },
      { label: 'Return Distribution', value: 'Stationary trading-day return distribution' },
    ],
    limitations:
      'Treats upside gains and downside drawdowns with equal mathematical penalty. Assumes stationary return variance; realized historical volatility does not bound future market volatility shocks.',
    investigationQuestion:
      'Is the observed annualized volatility driven by a few isolated market events, or does the fund consistently exhibit higher return dispersion than peers?',
    investigationReasonTemplate: (asOfDate) =>
      `Examining total return volatility (RSK-01) and dispersion trade-offs as of ${asOfDate || 'cutoff'}.`,
  },

  'RSK-02': {
    code: 'RSK-02',
    name: 'Downside Semideviation (3Y)',
    categoryLabel: 'Asymmetric Downside Risk',
    governanceStatus: 'CANDIDATE',
    rankingDirection: 'LOWER_IS_BETTER',
    whyItMatters:
      'Isolates exclusively negative deviations below a minimum acceptable return threshold (MAR = 0.0%), ignoring positive volatility that benefits investors.',
    evidenceDescription:
      'Continuous daily returns over 36 months filtered for observations below zero, scaled by √252.',
    formulaSpecification:
      'Downside Semideviation = sqrt( sum(min(r_t - MAR, 0)^2) / (N - 1) ) * sqrt(252)',
    assumptions: [
      { label: 'Minimum Acceptable Return (MAR)', value: '0.0% (Zero return baseline)' },
      { label: 'Annualization Factor', value: '√252 trading days' },
    ],
    limitations:
      'Candidate methodology. Setting MAR = 0 does not penalize returns that fail to match inflation or risk-free cash yields.',
    investigationQuestion:
      'Does downside semideviation capture asymmetric crash risk better than total volatility for this portfolio?',
    investigationReasonTemplate: (asOfDate) =>
      `Assessing downside semideviation (RSK-02) and asymmetric risk as of ${asOfDate || 'cutoff'}.`,
  },

  'RSK-03': {
    code: 'RSK-03',
    name: '3-Year Maximum Drawdown',
    categoryLabel: 'Tail Risk & Capital Preservation',
    governanceStatus: 'CANDIDATE',
    rankingDirection: 'LOWER_IS_BETTER',
    whyItMatters:
      'Captures the single worst peak-to-trough percentage decline observed across the continuous 36-month window. Evaluates historical capital impairment and psychological stress capacity for allocators.',
    evidenceDescription:
      'Continuous daily tracking of running cumulative peak NAV and subsequent deepest percentage erosion before complete recovery.',
    formulaSpecification:
      'MaxDD = min_t ( (NAV_t - Running_Peak_NAV_t) / Running_Peak_NAV_t )',
    assumptions: [
      { label: 'Peak Identification', value: 'Continuous cumulative running maximum' },
      { label: 'Window', value: '36 Calendar Months' },
    ],
    limitations:
      'Candidate methodology under empirical review. A single path-dependent worst historical decline does not describe how often intermediate corrections occurred, nor does it place an upper bound on future crisis drawdowns.',
    investigationQuestion:
      'How long did the fund take to recover from its 3-year maximum drawdown, and was downside risk actively mitigated during the decline?',
    investigationReasonTemplate: (asOfDate) =>
      `Investigating peak-to-trough maximum drawdown (RSK-03) depth and recovery characteristics as of ${asOfDate || 'cutoff'}.`,
  },

  'RSK-04': {
    code: 'RSK-04',
    name: 'Maximum Drawdown Duration',
    categoryLabel: 'Recovery & Psychological Stress',
    governanceStatus: 'CANDIDATE',
    rankingDirection: 'LOWER_IS_BETTER',
    whyItMatters:
      'Measures the longest elapsed time (in calendar days) from a peak NAV decline until the previous peak is fully surpassed. Quantifies the duration of capital lockup and investor patience required.',
    evidenceDescription:
      'Peak-to-recovery calendar day duration tracked across all intermediate drawdown cycles in the 36-month lookback window.',
    formulaSpecification:
      'MaxDD Duration = max( t_recovery - t_peak ) in calendar days',
    assumptions: [
      { label: 'Units', value: 'Calendar Days' },
      { label: 'Censoring Bound', value: 'Censored at knowledge cutoff timestamp' },
    ],
    limitations:
      'Candidate methodology. Ongoing unrecovered drawdowns are right-censored at analysis cutoff.',
    investigationQuestion:
      'Did the fund endure prolonged recovery phases compared to category peers during broad market drawdowns?',
    investigationReasonTemplate: (asOfDate) =>
      `Evaluating maximum drawdown duration (RSK-04) recovery timeline as of ${asOfDate || 'cutoff'}.`,
  },

  'RSK-05': {
    code: 'RSK-05',
    name: 'Ulcer Index (3Y)',
    categoryLabel: 'Comprehensive Stress Metric',
    governanceStatus: 'CANDIDATE',
    rankingDirection: 'LOWER_IS_BETTER',
    whyItMatters:
      'Measures both the depth and duration of all drawdowns throughout the 3-year window. Combines frequency, severity, and length of underwater periods into a single index.',
    evidenceDescription:
      'Quadratic root-mean-square of percentage drawdowns from running peak NAV across daily observations.',
    formulaSpecification:
      'Ulcer Index = sqrt( (1 / N) * sum( ( (NAV_t - Peak_t) / Peak_t * 100 )^2 ) )',
    assumptions: [
      { label: 'Weighting', value: 'Quadratic RMS' },
      { label: 'Scale', value: 'Percentage points' },
    ],
    limitations:
      'Candidate methodology. Heavy quadratic penalty on deep drawdowns may obscure multiple shallow corrections.',
    investigationQuestion:
      'Does the Ulcer Index indicate persistent capital stress even when headline maximum drawdown appears acceptable?',
    investigationReasonTemplate: (asOfDate) =>
      `Inspecting Ulcer Index (RSK-05) stress profile as of ${asOfDate || 'cutoff'}.`,
  },

  'RSK-06': {
    code: 'RSK-06',
    name: 'Historical Value at Risk (VaR 95%)',
    categoryLabel: 'Empirical Tail Loss Boundary',
    governanceStatus: 'CANDIDATE',
    rankingDirection: 'LOWER_IS_BETTER',
    whyItMatters:
      'Represents the 5th percentile worst daily return observed over 36 months. Indicates that on 95% of trading days, daily losses did not exceed this threshold.',
    evidenceDescription:
      'Empirical distribution of 36 months of daily discrete returns (Type 7 linear quantile interpolation).',
    formulaSpecification:
      'VaR_95 = -Quantile_0.05( { r_t } )',
    assumptions: [
      { label: 'Confidence Level', value: '95.0% One-Tailed' },
      { label: 'Quantile Convention', value: 'Linear interpolation (Type 7)' },
    ],
    limitations:
      'Candidate methodology. Non-parametric empirical quantile does not specify how severe losses can become beyond the 5% threshold.',
    investigationQuestion:
      'Does the 95% daily VaR threshold accurately represent tail liquidity risk during severe market stress?',
    investigationReasonTemplate: (asOfDate) =>
      `Reviewing historical 95% Value at Risk (RSK-06) boundary as of ${asOfDate || 'cutoff'}.`,
  },

  'RSK-07': {
    code: 'RSK-07',
    name: 'Expected Shortfall (CVaR 95%)',
    categoryLabel: 'Conditional Tail Expectation',
    governanceStatus: 'CANDIDATE',
    rankingDirection: 'LOWER_IS_BETTER',
    whyItMatters:
      'Measures the average daily loss incurred specifically when the 95% VaR threshold is breached. Addresses the severity of losses in the extreme 5% left tail.',
    evidenceDescription:
      'Arithmetic conditional mean of all daily returns falling strictly below the 5th percentile cutoff.',
    formulaSpecification:
      'CVaR_95 = -(1 / N_tail) * sum_{r_t < -VaR_95}( r_t )',
    assumptions: [
      { label: 'Sub-additive Property', value: 'Coherent risk measure' },
      { label: 'Tail Conditioning', value: 'r_t < -VaR_95' },
    ],
    limitations:
      'Candidate methodology. Sensitive to small-sample estimation noise in calm regimes with few tail observations.',
    investigationQuestion:
      'When extreme tail events occur, does the fund experience significantly steeper losses than category peers?',
    investigationReasonTemplate: (asOfDate) =>
      `Assessing Expected Shortfall (RSK-07) conditional tail loss severity as of ${asOfDate || 'cutoff'}.`,
  },

  'RET-07': {
    code: 'RET-07',
    name: '3-Year Annualized Active Return',
    categoryLabel: 'Benchmark Excess Performance',
    governanceStatus: 'CANDIDATE',
    rankingDirection: 'HIGHER_IS_BETTER',
    whyItMatters:
      'The difference between the fund compound growth rate (3Y CAGR) and its primary institutional benchmark (NIFTY 500 TRI) over an identical 36-month calendar horizon. Identifies whether active management created value after all fund management expenses.',
    evidenceDescription:
      'Paired daily observations of fund NAV and official benchmark Total Return Index evaluated strictly under point-in-time knowledge cutoff.',
    formulaSpecification:
      'Active Return = Fund_3Y_CAGR - Benchmark_3Y_CAGR',
    assumptions: [
      { label: 'Benchmark Proxy', value: 'NIFTY 500 Total Return Index (TRI)' },
      { label: 'Calendar Synchronization', value: 'Identical trading-day alignment' },
    ],
    limitations:
      'Candidate methodology. Raw excess return is unadjusted for systematic market sensitivity (Beta); excess performance may result from aggressive mid/small-cap factor tilts rather than security selection skill.',
    investigationQuestion:
      'Is the benchmark-relative active return generated by security selection skill or structural market beta and style tilts?',
    investigationReasonTemplate: (asOfDate) =>
      `Analyzing active return (RET-07) outperformance durability vs NIFTY 500 TRI as of ${asOfDate || 'cutoff'}.`,
  },

  'MKT-01': {
    code: 'MKT-01',
    name: 'Portfolio Beta (3Y)',
    categoryLabel: 'Market Sensitivity & Systematic Risk',
    governanceStatus: 'CANDIDATE',
    rankingDirection: 'NEUTRAL',
    whyItMatters:
      'Measures the linear sensitivity of fund excess returns relative to benchmark (NIFTY 500 TRI) excess returns above the risk-free rate. Quantifies how much market exposure the allocator is purchasing.',
    evidenceDescription:
      'Ordinary Least Squares (OLS) regression slope of daily fund excess returns on daily benchmark excess returns over 36 months.',
    formulaSpecification:
      'Beta = Cov(R_p - R_f, R_b - R_f) / Var(R_b - R_f)',
    assumptions: [
      { label: 'Risk-Free Proxy', value: 'FBIL 91-Day T-Bill (M2N-02)' },
      { label: 'Benchmark', value: 'NIFTY 500 TRI' },
      { label: 'Model', value: 'Single-Index CAPM OLS' },
    ],
    limitations:
      'Candidate methodology. Assumes a stationary linear relationship over time; portfolio beta can fluctuate substantially during market crises, liquidity squeezes, and cash drag regimes.',
    investigationQuestion:
      "Is the portfolio's systematic sensitivity (Beta) consistent with the fund's stated mandate and benchmark?",
    investigationReasonTemplate: (asOfDate) =>
      `Evaluating market beta sensitivity (MKT-01) and systematic exposure as of ${asOfDate || 'cutoff'}.`,
  },

  'MKT-02': {
    code: 'MKT-02',
    name: 'Downside Beta (3Y)',
    categoryLabel: 'Asymmetric Downside Sensitivity',
    governanceStatus: 'CANDIDATE',
    rankingDirection: 'LOWER_IS_BETTER',
    whyItMatters:
      'Evaluates portfolio sensitivity conditioned exclusively on market trading days when the benchmark excess return is negative. Detects whether fund losses accelerate during market sell-offs.',
    evidenceDescription:
      'Conditional OLS regression of daily excess returns on negative benchmark excess return days (minimum 100 days).',
    formulaSpecification:
      'Downside Beta = Cov(R_p - R_f, R_b - R_f | R_b - R_f < 0) / Var(R_b - R_f | R_b - R_f < 0)',
    assumptions: [
      { label: 'Conditioning Rule', value: 'Benchmark excess return < 0' },
      { label: 'Minimum Sample Size', value: '≥ 100 downside market days' },
    ],
    limitations:
      'Candidate methodology. Reduced sample size during sustained bull markets increases parameter estimation variance.',
    investigationQuestion:
      'Is downside beta significantly lower than overall beta, confirming defensive resilience in down markets?',
    investigationReasonTemplate: (asOfDate) =>
      `Evaluating downside beta (MKT-02) asymmetry during market declines as of ${asOfDate || 'cutoff'}.`,
  },

  'RAT-01': {
    code: 'RAT-01',
    name: 'Sharpe Ratio (3Y)',
    categoryLabel: 'Total Risk-Adjusted Reward',
    governanceStatus: 'CANDIDATE',
    rankingDirection: 'HIGHER_IS_BETTER',
    whyItMatters:
      'Quantifies the annualized excess return earned per unit of total annualized return volatility above the risk-free cash rate (FBIL 91-Day T-Bill).',
    evidenceDescription:
      'Geometric annualized return and annualized sample standard deviation paired with daily FBIL 91-day T-bill yields.',
    formulaSpecification:
      'Sharpe = (Fund_3Y_CAGR - Rf_Annualized) / Volatility_3Y',
    assumptions: [
      { label: 'Risk-Free Benchmark', value: 'FBIL 91-Day Treasury Bill' },
      { label: 'Volatility Measure', value: '3Y Annualized Sample Volatility (RSK-01)' },
    ],
    limitations:
      'Candidate methodology. Penalizes upside volatility equally with downside losses; assumes normal return distributions.',
    investigationQuestion:
      'Is the Sharpe ratio supported by genuine stock selection or by carrying elevated market beta?',
    investigationReasonTemplate: (asOfDate) =>
      `Assessing Sharpe ratio (RAT-01) excess return efficiency as of ${asOfDate || 'cutoff'}.`,
  },

  'RAT-02': {
    code: 'RAT-02',
    name: 'Sortino Ratio (3Y)',
    categoryLabel: 'Downside Risk-Adjusted Reward',
    governanceStatus: 'CANDIDATE',
    rankingDirection: 'HIGHER_IS_BETTER',
    whyItMatters:
      'Evaluates excess return earned per unit of downside semideviation below zero, avoiding penalizing positive return spikes.',
    evidenceDescription:
      'Annualized excess return above risk-free rate divided by downside semideviation with MAR = 0.0%.',
    formulaSpecification:
      'Sortino = (Fund_3Y_CAGR - Rf_Annualized) / Downside_Semideviation',
    assumptions: [
      { label: 'MAR Baseline', value: '0.0% (Zero return)' },
      { label: 'Denominator', value: 'Downside Semideviation (RSK-02)' },
    ],
    limitations:
      'Candidate methodology. Divisor convention and MAR sensitivity under empirical review.',
    investigationQuestion:
      'Does the Sortino ratio indicate superior asymmetric return delivery compared to total-volatility Sharpe?',
    investigationReasonTemplate: (asOfDate) =>
      `Evaluating Sortino ratio (RAT-02) downside efficiency as of ${asOfDate || 'cutoff'}.`,
  },

  'RAT-03': {
    code: 'RAT-03',
    name: 'Treynor Ratio (3Y)',
    categoryLabel: 'Systematic Risk-Adjusted Reward',
    governanceStatus: 'CANDIDATE',
    rankingDirection: 'HIGHER_IS_BETTER',
    whyItMatters:
      'Measures excess return delivered per unit of systematic equity market risk (Beta). Focuses on undiversifiable market risk.',
    evidenceDescription:
      'Annualized excess return above 91-day T-bills divided by 3-year single-index equity Beta.',
    formulaSpecification:
      'Treynor = (Fund_3Y_CAGR - Rf_Annualized) / Beta_3Y',
    assumptions: [
      { label: 'Denominator Rule', value: 'Beta > 0 required' },
      { label: 'Systematic Beta', value: '3Y Single-Index Beta (MKT-01)' },
    ],
    limitations:
      'Candidate methodology. Meaningful primarily for diversified portfolios with high market correlation.',
    investigationQuestion:
      'Does the portfolio generate sufficient excess return for its systematic broad-market factor loading?',
    investigationReasonTemplate: (asOfDate) =>
      `Examining Treynor ratio (RAT-03) systematic risk efficiency as of ${asOfDate || 'cutoff'}.`,
  },

  'RAT-04': {
    code: 'RAT-04',
    name: 'Information Ratio (3Y)',
    categoryLabel: 'Active Risk-Adjusted Reward',
    governanceStatus: 'CANDIDATE',
    rankingDirection: 'HIGHER_IS_BETTER',
    whyItMatters:
      'Measures the consistency of active outperformance by dividing annualized active return by annualized tracking error against the benchmark.',
    evidenceDescription:
      'Ratio of 3-year annualized active return (RET-07) to annualized benchmark tracking error (REL-01).',
    formulaSpecification:
      'Information Ratio = Active_Return_3Y / Tracking_Error_3Y',
    assumptions: [
      { label: 'Denominator Rule', value: 'Tracking error > 0' },
      { label: 'Benchmark', value: 'Category Reference Benchmark (NIFTY 500 TRI)' },
    ],
    limitations:
      'Candidate methodology. High tracking error from concentrated bets can depress the ratio even during strong outperformance periods.',
    investigationQuestion:
      'Is active outperformance achieved with consistent discipline, or does high tracking error indicate erratic benchmark divergence?',
    investigationReasonTemplate: (asOfDate) =>
      `Assessing Information Ratio (RAT-04) active management consistency as of ${asOfDate || 'cutoff'}.`,
  },

  'REL-01': {
    code: 'REL-01',
    name: 'Tracking Error (3Y)',
    categoryLabel: 'Active Risk & Benchmark Divergence',
    governanceStatus: 'CANDIDATE',
    rankingDirection: 'LOWER_IS_BETTER',
    whyItMatters:
      'Measures the volatility of daily active returns (fund return minus benchmark return), annualized by √252. Quantifies how closely the fund tracks its assigned benchmark.',
    evidenceDescription:
      'Sample standard deviation of daily active return differences over 36 months, scaled by √252.',
    formulaSpecification:
      'Tracking Error = Stdev_sample( r_fund,t - r_bench,t ) * sqrt(252)',
    assumptions: [
      { label: 'Annualization Factor', value: '√252 trading days' },
      { label: 'Active Baseline', value: 'Daily paired differences' },
    ],
    limitations:
      'Candidate methodology. High tracking error reflects active portfolio bets; it measures deviation, not whether deviations are beneficial or harmful.',
    investigationQuestion:
      'Does the tracking error reflect intentional security selection divergence or passive drift and cash drag?',
    investigationReasonTemplate: (asOfDate) =>
      `Evaluating Tracking Error (REL-01) active risk budget as of ${asOfDate || 'cutoff'}.`,
  },

  'REL-02': {
    code: 'REL-02',
    name: "Jensen's Alpha (3Y)",
    categoryLabel: 'Risk-Adjusted Active Alpha',
    governanceStatus: 'CANDIDATE',
    rankingDirection: 'HIGHER_IS_BETTER',
    whyItMatters:
      'Represents the annualized intercept from the single-index Capital Asset Pricing Model regression. Quantifies abnormal excess return generated beyond what is explained by systematic benchmark beta.',
    evidenceDescription:
      'Annualized intercept (alpha * 252) from OLS regression of daily fund excess returns on benchmark excess returns above 91-day T-bills.',
    formulaSpecification:
      "Jensen's Alpha = (R_p - R_f) - Beta * (R_b - R_f)",
    assumptions: [
      { label: 'Model', value: 'Single-Index CAPM OLS' },
      { label: 'Scaling', value: 'Annualized (252 trading days)' },
    ],
    limitations:
      'Candidate methodology. Single-factor model does not adjust for multi-factor size, value, momentum, or quality premiums.',
    investigationQuestion:
      "Is Jensen's Alpha statistically significant, or is it an artifact of cyclical market style factors?",
    investigationReasonTemplate: (asOfDate) =>
      `Evaluating Jensen's Alpha (REL-02) risk-adjusted value add as of ${asOfDate || 'cutoff'}.`,
  },

  'MKT-03': {
    code: 'MKT-03',
    name: 'Upside Capture Ratio (3Y)',
    categoryLabel: 'Up-Market Participation',
    governanceStatus: 'CANDIDATE',
    rankingDirection: 'HIGHER_IS_BETTER',
    whyItMatters:
      'Measures the fund compound return relative to benchmark compound return conditioned strictly on market days when the benchmark is positive. Evaluates how effectively the manager participates in bull markets.',
    evidenceDescription:
      'Geometric compound returns evaluated on positive benchmark days over 36 months.',
    formulaSpecification:
      'Upside Capture = Prod(1 + R_p | R_b > 0) / Prod(1 + R_b | R_b > 0)',
    assumptions: [
      { label: 'Conditioning Rule', value: 'Benchmark daily return R_b > 0' },
      { label: 'Compounding', value: 'Geometric subset product' },
    ],
    limitations:
      'Candidate methodology. Conditioned on historical bull market days; sensitive to outlier market surges and unadjusted for cash drag.',
    investigationQuestion:
      'Does high upside capture come from aggressive beta loading, or does the fund select outperforming equities during market rallies?',
    investigationReasonTemplate: (asOfDate) =>
      `Evaluating upside capture ratio (MKT-03) market rally participation as of ${asOfDate || 'cutoff'}.`,
  },

  'MKT-04': {
    code: 'MKT-04',
    name: 'Downside Capture Ratio (3Y)',
    categoryLabel: 'Down-Market Resilience',
    governanceStatus: 'CANDIDATE',
    rankingDirection: 'LOWER_IS_BETTER',
    whyItMatters:
      'Measures the fund compound decline relative to benchmark decline conditioned strictly on negative benchmark days. Ratios below 100% indicate capital defense during market pullbacks.',
    evidenceDescription:
      'Geometric compound returns evaluated on negative benchmark days over 36 months.',
    formulaSpecification:
      'Downside Capture = Prod(1 + R_p | R_b < 0) / Prod(1 + R_b | R_b < 0)',
    assumptions: [
      { label: 'Conditioning Rule', value: 'Benchmark daily return R_b < 0' },
      { label: 'Capital Preservation Target', value: 'Ratio < 100% indicates defensive cushion' },
    ],
    limitations:
      'Candidate methodology. Conditioned on negative benchmark days; does not bound losses during systemic liquidity crunches.',
    investigationQuestion:
      'Does the fund demonstrate disciplined downside containment across different types of market corrections?',
    investigationReasonTemplate: (asOfDate) =>
      `Assessing downside capture ratio (MKT-04) defensive cushion as of ${asOfDate || 'cutoff'}.`,
  },

  'MKT-05': {
    code: 'MKT-05',
    name: 'Capture Spread (3Y)',
    categoryLabel: 'Asymmetric Capture Spread',
    governanceStatus: 'CANDIDATE',
    rankingDirection: 'HIGHER_IS_BETTER',
    whyItMatters:
      'Difference between upside capture and downside capture (Upside Capture % - Downside Capture %). Positive spread indicates favorable upside participation relative to downside losses.',
    evidenceDescription:
      'Arithmetic spread between MKT-03 and MKT-04 over the identical 36-month lookback window.',
    formulaSpecification:
      'Capture Spread = Upside_Capture_Ratio - Downside_Capture_Ratio',
    assumptions: [
      { label: 'Units', value: 'Percentage Points' },
      { label: 'Asymmetry Goal', value: 'Spread > 0 indicates positive return asymmetry' },
    ],
    limitations:
      'Candidate methodology. Combines two separate conditional subset estimates; parameter uncertainty compounds during low-volatility regimes.',
    investigationQuestion:
      'Is the capture spread positive and statistically significant, confirming genuine manager asymmetry across market cycles?',
    investigationReasonTemplate: (asOfDate) =>
      `Inspecting capture spread (MKT-05) return asymmetry as of ${asOfDate || 'cutoff'}.`,
  },

  'RET-01': {
    code: 'RET-01',
    name: '1-Year Compound Return',
    categoryLabel: 'Short-Horizon Compound Growth',
    governanceStatus: 'CANDIDATE',
    rankingDirection: 'HIGHER_IS_BETTER',
    whyItMatters:
      'Annualized geometric return over 12 calendar months (minimum 240 trading days). Reflects cyclical, shorter-term performance.',
    evidenceDescription:
      '12 calendar months of official AMFI net asset values anchored to market trading close.',
    formulaSpecification:
      'RET_01 = (NAV_end / NAV_start)^(365.25 / elapsed_calendar_days) - 1.0',
    assumptions: [
      { label: 'Horizon', value: '1 Year (12 calendar months)' },
      { label: 'Threshold', value: '≥ 240 trading days' },
    ],
    limitations:
      'Heavily influenced by transient macroeconomic regimes, quarterly momentum, and single-year sectoral rotations.',
    investigationQuestion:
      'Is the 1-year performance consistent with the fund’s longer multi-cycle 3Y and 5Y compounding record?',
    investigationReasonTemplate: (asOfDate) =>
      `Evaluating 1-Year return (RET-01) consistency as of ${asOfDate || 'cutoff'}.`,
  },

  'RET-04': {
    code: 'RET-04',
    name: '5-Year Compound Growth Rate (5Y CAGR)',
    categoryLabel: 'Multi-Cycle Compound Growth',
    governanceStatus: 'CANDIDATE',
    rankingDirection: 'HIGHER_IS_BETTER',
    whyItMatters:
      'Geometric annualized capital growth across 60 calendar months (minimum 1,200 trading days). Evaluates fund compounding across a complete multi-year market cycle.',
    evidenceDescription:
      '60 calendar months of official AMFI NAVs evaluated under strict point-in-time knowledge cutoff.',
    formulaSpecification:
      'RET_04 = (NAV_end / NAV_start)^(365.25 / elapsed_calendar_days) - 1.0',
    assumptions: [
      { label: 'Horizon', value: '5 Years (60 calendar months)' },
      { label: 'Threshold', value: '≥ 1,200 trading days' },
    ],
    limitations:
      'Candidate methodology (Phase B1). Masks intermediate portfolio manager transitions, mandate shifts, and AUM scale drag.',
    investigationQuestion:
      'Has the fund sustained its 5-year compound growth under the current portfolio manager tenure?',
    investigationReasonTemplate: (asOfDate) =>
      `Auditing 5-Year CAGR (RET-04) multi-cycle persistence as of ${asOfDate || 'cutoff'}.`,
  },

  'RET-05': {
    code: 'RET-05',
    name: '3-Year Rolling Return Mean',
    categoryLabel: 'Rolling Return Central Tendency',
    governanceStatus: 'CANDIDATE',
    rankingDirection: 'HIGHER_IS_BETTER',
    whyItMatters:
      'Arithmetic average of all continuous 3-year rolling compound returns calculated daily across historical data. Eliminates point-to-point endpoint bias.',
    evidenceDescription:
      'Daily rolled 36-month window evaluation across all available point-in-time trading days.',
    formulaSpecification:
      'Rolling Mean = (1 / K) * sum_{k=1}^K CAGR_3Y(t_k)',
    assumptions: [
      { label: 'Rolling Window', value: '36 Calendar Months' },
      { label: 'Step Size', value: '1 Trading Day' },
    ],
    limitations:
      'Candidate methodology. Rolling windows overlap heavily, inducing significant positive autocorrelation in the sample mean.',
    investigationQuestion:
      'Does rolling 3-year return distribution show tight dispersion around the mean, or wide dispersion between bull and bear regimes?',
    investigationReasonTemplate: (asOfDate) =>
      `Evaluating 3-Year rolling return central tendency (RET-05) as of ${asOfDate || 'cutoff'}.`,
  },

  'RET-06': {
    code: 'RET-06',
    name: 'Rolling Outperformance Percentage',
    categoryLabel: 'Rolling Benchmark Consistency',
    governanceStatus: 'CANDIDATE',
    rankingDirection: 'HIGHER_IS_BETTER',
    whyItMatters:
      'Percentage of continuous 3-year rolling windows where the fund outperformed its benchmark (NIFTY 500 TRI). Quantifies probability of excess return.',
    evidenceDescription:
      'Paired daily rolling 3Y windows comparing fund CAGR against benchmark CAGR.',
    formulaSpecification:
      'Outperformance % = (Count(CAGR_fund > CAGR_bench) / Total_Rolling_Windows) * 100',
    assumptions: [
      { label: 'Benchmark', value: 'NIFTY 500 TRI' },
      { label: 'Baseline', value: 'Excess return > 0.0%' },
    ],
    limitations:
      'Candidate methodology. High historical rolling outperformance percentage does not guarantee future outperformance.',
    investigationQuestion:
      'During periods of underperformance, what market factor tilts or sectoral positions drove benchmark lag?',
    investigationReasonTemplate: (asOfDate) =>
      `Reviewing rolling benchmark outperformance consistency (RET-06) as of ${asOfDate || 'cutoff'}.`,
  },

  'PRT-01': {
    code: 'PRT-01',
    name: 'Top-10 Portfolio Concentration',
    categoryLabel: 'Portfolio Concentration & Conviction',
    governanceStatus: 'CANDIDATE',
    rankingDirection: 'LOWER_IS_BETTER',
    unit: 'PERCENTAGE',
    aliases: ['CONC-02', 'HOLDINGS_CONCENTRATION_TOP10', 'TOP10_CONCENTRATION'],
    whyItMatters:
      'Cumulative portfolio percentage weight allocated to the ten largest equity holdings. Measures idiosyncratic single-stock exposure and portfolio conviction.',
    evidenceDescription:
      'Monthly portfolio disclosures verified against official AMC regulatory filings.',
    formulaSpecification:
      'Top-10 Weight = sum_{i=1}^{10} Weight_i',
    assumptions: [
      { label: 'Source', value: 'Monthly AMC portfolio disclosure' },
      { label: 'Threshold', value: '10 Largest equity holdings' },
    ],
    limitations:
      'Candidate metric. Exposes single-stock concentration risk; does not capture underlying factor or sector co-dependence among top holdings.',
    investigationQuestion:
      'Is the top-10 concentration driven by high conviction in defensive compounders or concentrated cyclical bets?',
    investigationReasonTemplate: (asOfDate) =>
      `Evaluating portfolio top-10 concentration (PRT-01) as of ${asOfDate || 'cutoff'}.`,
  },

  'CONC-01': {
    code: 'CONC-01',
    name: 'Top 5 Equity Holdings Concentration',
    categoryLabel: 'Portfolio Concentration & Conviction',
    governanceStatus: 'CANDIDATE',
    rankingDirection: 'LOWER_IS_BETTER',
    unit: 'PERCENTAGE',
    aliases: ['HOLDINGS_CONCENTRATION_TOP5', 'TOP5_CONCENTRATION'],
    whyItMatters:
      'Cumulative weight of the five largest individual stock holdings. Detects acute single-issuer concentration.',
    evidenceDescription:
      'Monthly portfolio disclosure verified against regulatory filing data.',
    formulaSpecification:
      'sum(w_i, i=1..5)',
    assumptions: [
      { label: 'Source', value: 'Monthly statutory disclosure' },
    ],
    limitations:
      'Disclosed weights represent month-end point-in-time snapshots and do not capture intra-month trading shifts.',
    investigationQuestion:
      'Does top 5 concentration represent high-conviction security selection or excessive idiosyncratic vulnerability?',
    investigationReasonTemplate: (asOfDate) =>
      `Auditing Top 5 holdings concentration (CONC-01) as of ${asOfDate || 'cutoff'}.`,
  },

  'CONC-03': {
    code: 'CONC-03',
    name: 'Herfindahl-Hirschman Index (HHI)',
    categoryLabel: 'Position Concentration',
    governanceStatus: 'CANDIDATE',
    rankingDirection: 'LOWER_IS_BETTER',
    unit: 'POINTS',
    aliases: ['PORTFOLIO_HHI', 'HHI'],
    whyItMatters:
      'Sum of squared percentage weights of all disclosed holdings. Higher scores denote concentrated exposure (> 2,500 indicates high concentration).',
    evidenceDescription:
      'Calculated from authoritative monthly portfolio disclosure holdings.',
    formulaSpecification:
      'HHI = sum( (100 * w_i)^2 )',
    assumptions: [
      { label: 'Scale', value: '0 to 10,000 points' },
    ],
    limitations:
      'Calculated strictly from disclosed equity holdings; residual cash and unlisted assets are omitted.',
    investigationQuestion:
      'Does the HHI score indicate balanced diversification across holdings or concentrated stock bets?',
    investigationReasonTemplate: (asOfDate) =>
      `Auditing portfolio Herfindahl-Hirschman Index (CONC-03) as of ${asOfDate || 'cutoff'}.`,
  },

  'PRT-02': {
    code: 'PRT-02',
    name: 'Effective Number of Holdings',
    categoryLabel: 'Constituent Dispersion',
    governanceStatus: 'CANDIDATE_SPECIFICATION',
    rankingDirection: 'HIGHER_IS_BETTER',
    unit: 'COUNT',
    aliases: ['EFFECTIVE_HOLDINGS_COUNT', 'PRT_02'],
    whyItMatters:
      'Inverse Herfindahl-Hirschman constituent count (1 / sum(w_i^2)) representing the equivalent number of equal-weighted holdings.',
    evidenceDescription:
      'Authoritative monthly portfolio disclosure snapshot with verified constituent weights.',
    formulaSpecification:
      'N_eff = 1 / sum(w_i^2)',
    assumptions: [
      { label: 'Derivation', value: '10,000 / HHI' },
    ],
    limitations:
      'Candidate specification awaiting multi-cycle calibration. Masks correlation between constituent securities.',
    investigationQuestion:
      'How does the effective number of holdings compare to the actual reported constituent count?',
    investigationReasonTemplate: (asOfDate) =>
      `Auditing effective constituent count (PRT-02) as of ${asOfDate || 'cutoff'}.`,
  },

  'PRT-03': {
    code: 'PRT-03',
    name: 'Active Share',
    categoryLabel: 'Benchmark Divergence',
    governanceStatus: 'CANDIDATE_SPECIFICATION',
    rankingDirection: 'NEUTRAL',
    unit: 'PERCENTAGE',
    aliases: ['ACTIVE_SHARE', 'PRT_03'],
    whyItMatters:
      'Proportion of fund equity portfolio holdings differing from primary benchmark constituent weights (0.5 * sum(|w_p - w_b|)).',
    evidenceDescription:
      'Synchronous monthly portfolio disclosure and verified benchmark constituent weight file.',
    formulaSpecification:
      'Active_Share = 0.5 * sum( |w_p,i - w_b,i| )',
    assumptions: [
      { label: 'Benchmark', value: 'NIFTY 500 TRI' },
      { label: 'Range', value: '0% to 100%' },
    ],
    limitations:
      'Candidate specification not yet wired into production calculation runs. High active share does not guarantee outperformance.',
    investigationQuestion:
      'Is high active share driven by high-conviction security selection or passive sector allocation bets?',
    investigationReasonTemplate: (asOfDate) =>
      `Evaluating Active Share (PRT-03) benchmark divergence as of ${asOfDate || 'cutoff'}.`,
  },

  'PRT-04': {
    code: 'PRT-04',
    name: 'Monthly Weight Turnover',
    categoryLabel: 'Portfolio Churn & Stability',
    governanceStatus: 'CANDIDATE_SPECIFICATION',
    rankingDirection: 'LOWER_IS_BETTER',
    unit: 'PERCENTAGE',
    aliases: ['PORTFOLIO_TURNOVER', 'PRT_04'],
    whyItMatters:
      'Fraction of portfolio assets reallocated between consecutive monthly statutory disclosure snapshots.',
    evidenceDescription:
      'Two consecutive authoritative monthly portfolio disclosure snapshots.',
    formulaSpecification:
      'Turnover = 0.5 * sum( |w_t,i - w_t-1,i| )',
    assumptions: [
      { label: 'Frequency', value: 'Monthly snapshot delta' },
    ],
    limitations:
      'Candidate specification under empirical review. Misses intraday or intra-month position round-tripping.',
    investigationQuestion:
      'Is portfolio turnover driven by disciplined profit taking or aggressive strategy churn?',
    investigationReasonTemplate: (asOfDate) =>
      `Auditing monthly portfolio weight turnover (PRT-04) as of ${asOfDate || 'cutoff'}.`,
  },

  'PRT-05': {
    code: 'PRT-05',
    name: 'Cash & Equivalent Allocation %',
    categoryLabel: 'Liquidity Cushion',
    governanceStatus: 'CANDIDATE_SPECIFICATION',
    rankingDirection: 'NEUTRAL',
    unit: 'PERCENTAGE',
    aliases: ['CASH_ALLOCATION_PCT', 'PRT_05'],
    whyItMatters:
      'Proportion of total scheme net assets held in cash, reverse repos, TREPS, and liquid cash equivalents.',
    evidenceDescription:
      'Authoritative monthly portfolio disclosure snapshot.',
    formulaSpecification:
      'Cash_Pct = Net_Assets_Cash / Total_Net_Assets * 100.0',
    assumptions: [
      { label: 'Classification', value: 'TREPS, Reverse Repo, Net Current Assets' },
    ],
    limitations:
      'Candidate specification. Cash drag can penalize upside capture during rapid bull markets.',
    investigationQuestion:
      'Is cash deployment counter-cyclical, providing dry powder during market pullbacks?',
    investigationReasonTemplate: (asOfDate) =>
      `Auditing cash and liquid asset allocation (PRT-05) as of ${asOfDate || 'cutoff'}.`,
  },

  'GOV-01': {
    code: 'GOV-01',
    name: 'Direct Plan TER',
    categoryLabel: 'Governance & Expense Ratio',
    governanceStatus: 'CANDIDATE_SPECIFICATION',
    rankingDirection: 'LOWER_IS_BETTER',
    unit: 'PERCENTAGE',
    aliases: ['DIRECT_TER', 'GOV_01'],
    whyItMatters:
      'Declared point-in-time Total Expense Ratio for the Direct Plan option.',
    evidenceDescription:
      'Statutory AMC expense disclosure.',
    formulaSpecification:
      'TER = Annualized_Operating_Expenses / Scheme_AUM * 100.0',
    assumptions: [
      { label: 'Plan', value: 'Direct Plan strictly; distributor commissions excluded' },
    ],
    limitations:
      'Candidate specification. TER changes periodically based on scheme AUM tier slabs.',
    investigationQuestion:
      'How does the direct plan expense ratio compare against identical-mandate category peers?',
    investigationReasonTemplate: (asOfDate) =>
      `Auditing Direct Plan Total Expense Ratio (GOV-01) as of ${asOfDate || 'cutoff'}.`,
  },

  'PORTFOLIO_DRIFT': {
    code: 'PORTFOLIO_DRIFT',
    name: 'Portfolio Target Allocation Drift',
    categoryLabel: 'Asset Allocation Drift',
    governanceStatus: 'OPERATIONAL_BASELINE',
    rankingDirection: 'LOWER_IS_BETTER',
    unit: 'PERCENTAGE',
    isDiagnostic: true,
    aliases: ['PORTFOLIO_ALLOCATION_DRIFT', 'ALLOCATION_DRIFT'],
    whyItMatters:
      'Maximum divergence between actual investor portfolio allocation and configured target bands.',
    evidenceDescription:
      'Active investor portfolio holdings compared against verified target allocation configuration.',
    formulaSpecification:
      'max_c ( |Actual_Weight_c - Target_Weight_c| )',
    assumptions: [
      { label: 'Scope', value: 'Investor private portfolio level' },
    ],
    limitations:
      'Informative monitoring diagnostic; YUKIRA never generates automated rebalancing orders or trading advice.',
    investigationQuestion:
      'Which asset category or fund drove the allocation drift away from target bands?',
    investigationReasonTemplate: (asOfDate) =>
      `Evaluating portfolio allocation drift (PORTFOLIO_DRIFT) as of ${asOfDate || 'cutoff'}.`,
  },

  'PORTFOLIO_UNVALUED_POSITIONS': {
    code: 'PORTFOLIO_UNVALUED_POSITIONS',
    name: 'Unvalued Portfolio Positions',
    categoryLabel: 'Valuation Completeness',
    governanceStatus: 'OPERATIONAL_BASELINE',
    rankingDirection: 'LOWER_IS_BETTER',
    unit: 'COUNT',
    isDiagnostic: true,
    aliases: ['UNVALUED_POSITIONS', 'UNVALUED_HOLDINGS_COUNT'],
    whyItMatters:
      'Count of portfolio holdings lacking an effective NAV observation as of the cutoff date.',
    evidenceDescription:
      'Active investor portfolio holdings checked against PIT NAV availability ledger.',
    formulaSpecification:
      'Count(Holding_i with NAV == NULL)',
    assumptions: [
      { label: 'Rule', value: 'Unvalued positions are never silently filled with zero' },
    ],
    limitations:
      'Indicates pending NAV ingestion or unmapped scheme codes; portfolio totals exclude unvalued positions.',
    investigationQuestion:
      'Which specific portfolio positions lack point-in-time valuation data?',
    investigationReasonTemplate: (asOfDate) =>
      `Auditing unvalued portfolio positions as of ${asOfDate || 'cutoff'}.`,
  },

  'NAV_FRESHNESS': {
    code: 'NAV_FRESHNESS',
    name: 'NAV Observation Freshness',
    categoryLabel: 'Market Data Recency',
    governanceStatus: 'OPERATIONAL_BASELINE',
    rankingDirection: 'LOWER_IS_BETTER',
    unit: 'DAYS',
    isDiagnostic: true,
    aliases: ['NAV_STALENESS', 'DATA_FRESHNESS_NAV'],
    whyItMatters:
      'Calendar days elapsed since the latest verified NAV observation was published and recorded.',
    evidenceDescription:
      'Verified point-in-time NAV ledger records.',
    formulaSpecification:
      'today - latest_verified_effective_date',
    assumptions: [
      { label: 'Cadence', value: 'T+1 business day publishing cadence for Indian open-ended mutual funds' },
    ],
    limitations:
      'Market closures and bank holidays naturally extend elapsed calendar days without indicating data failure.',
    investigationQuestion:
      'Has the fund delayed its statutory daily NAV publication beyond standard T+1 cutoffs?',
    investigationReasonTemplate: (asOfDate) =>
      `Checking NAV observation freshness as of ${asOfDate || 'cutoff'}.`,
  },

  'HOLDINGS_FRESHNESS': {
    code: 'HOLDINGS_FRESHNESS',
    name: 'Monthly Holdings Disclosure Freshness',
    categoryLabel: 'Disclosure Recency',
    governanceStatus: 'OPERATIONAL_BASELINE',
    rankingDirection: 'LOWER_IS_BETTER',
    unit: 'DAYS',
    isDiagnostic: true,
    aliases: ['HOLDINGS_STALENESS', 'DATA_FRESHNESS_HOLDINGS'],
    whyItMatters:
      'Calendar days elapsed since the statutory month-end holdings disclosure date.',
    evidenceDescription:
      'Authoritative monthly portfolio disclosure snapshots.',
    formulaSpecification:
      'today - statutory_reporting_date',
    assumptions: [
      { label: 'Grace Period', value: 'SEBI mandates disclosure within 10 calendar days of month close' },
    ],
    limitations:
      'Holdings disclosures are monthly; intra-month trading activity remains private to the AMC.',
    investigationQuestion:
      'Is the monthly holdings disclosure current or has the AMC exceeded the reporting window?',
    investigationReasonTemplate: (asOfDate) =>
      `Checking monthly holdings disclosure freshness as of ${asOfDate || 'cutoff'}.`,
  },

  'UNCLASSIFIED_EXPOSURE': {
    code: 'UNCLASSIFIED_EXPOSURE',
    name: 'Unclassified Holdings Exposure',
    categoryLabel: 'Look-Through Integrity',
    governanceStatus: 'CANDIDATE',
    rankingDirection: 'LOWER_IS_BETTER',
    unit: 'PERCENTAGE',
    isDiagnostic: true,
    aliases: ['UNMAPPED_EXPOSURE', 'UNCLASSIFIED_WEIGHT_PCT'],
    whyItMatters:
      'Percentage of disclosed fund assets lacking verified security identifier mapping or sector classification.',
    evidenceDescription:
      'Monthly portfolio disclosure holdings matched against security master ledger.',
    formulaSpecification:
      'Unclassified_Weight / Total_Disclosed_Assets * 100.0',
    assumptions: [
      { label: 'Threshold', value: 'Exposure > 5.0% triggers unclassified exposure warning' },
    ],
    limitations:
      'Limits look-through concentration and overlap precision; does not invalidate total fund NAV.',
    investigationQuestion:
      'Which underlying securities in the portfolio lack ISIN or market classification?',
    investigationReasonTemplate: (asOfDate) =>
      `Auditing unclassified holdings exposure as of ${asOfDate || 'cutoff'}.`,
  },
};

const METRIC_ALIASES: Record<string, string> = {
  'CONC-02': 'PRT-01',
  'HOLDINGS_CONCENTRATION_TOP10': 'PRT-01',
  'TOP10_CONCENTRATION': 'PRT-01',
  'CONC-01': 'CONC-01',
  'HOLDINGS_CONCENTRATION_TOP5': 'CONC-01',
  'TOP5_CONCENTRATION': 'CONC-01',
  'CONC-03': 'CONC-03',
  'PORTFOLIO_HHI': 'CONC-03',
  'HHI': 'CONC-03',
  'PORTFOLIO_ALLOCATION_DRIFT': 'PORTFOLIO_DRIFT',
  'ALLOCATION_DRIFT': 'PORTFOLIO_DRIFT',
  'CAGR_3Y': 'RET-03',
  'CAGR_1Y': 'RET-01',
  'CAGR_5Y': 'RET-04',
  'VOLATILITY_3Y': 'RSK-01',
  'MAX_DRAWDOWN_3Y': 'RSK-03',
  'MAX_DD_3Y': 'RSK-03',
  'DRAWDOWN_DURATION': 'RSK-04',
  'MAX_DD_DURATION': 'RSK-04',
  'SHARPE_3Y': 'RAT-01',
  'SORTINO_3Y': 'RAT-02',
  'TREYNOR_3Y': 'RAT-03',
  'INFORMATION_RATIO_3Y': 'RAT-04',
  'BETA_3Y': 'MKT-01',
  'DOWNSIDE_BETA_3Y': 'MKT-02',
  'UPSIDE_CAPTURE_3Y': 'MKT-03',
  'DOWNSIDE_CAPTURE_3Y': 'MKT-04',
  'CAPTURE_SPREAD_3Y': 'MKT-05',
  'TRACKING_ERROR_3Y': 'REL-01',
  'JENSENS_ALPHA_3Y': 'REL-02',
};

export function getMetricEvidenceDefinition(metricCode: string): MetricEvidenceDefinition {
  if (!metricCode) return createFallbackDefinition('UNKNOWN');
  const upper = metricCode.trim().toUpperCase();
  const canonical = METRIC_ALIASES[upper] || upper;

  if (CANONICAL_METRIC_EVIDENCE[canonical]) {
    return CANONICAL_METRIC_EVIDENCE[canonical];
  }
  if (CANONICAL_METRIC_EVIDENCE[upper]) {
    return CANONICAL_METRIC_EVIDENCE[upper];
  }

  return createFallbackDefinition(metricCode);
}

function createFallbackDefinition(metricCode: string): MetricEvidenceDefinition {
  return {
    code: metricCode,
    name: `Metric ${metricCode}`,
    categoryLabel: 'Quantitative Indicator',
    governanceStatus: 'CANDIDATE',
    whyItMatters:
      'Evaluates empirical risk or return characteristics derived from point-in-time daily market observations.',
    evidenceDescription:
      'Point-in-time daily observations evaluated under strict analysis and knowledge cutoffs.',
    formulaSpecification:
      'Deterministic quantitative algorithm defined in quant engine kernel.',
    assumptions: [
      { label: 'Observation Threshold', value: 'Statutory minimum paired observations' },
      { label: 'Cutoff Discipline', value: 'Strict Point-in-Time invariance' },
    ],
    limitations:
      'Candidate specification. Realized historical performance does not predict future returns across changing market cycles.',
    investigationQuestion:
      'Does the observed metric result warrant closer due diligence against peer funds under identical cutoff dates?',
    investigationReasonTemplate: (asOfDate) =>
      `Conducting due diligence on ${metricCode} as of ${asOfDate || 'cutoff'}.`,
  };
}

export interface EvidenceInvestigationQuestionItem {
  id: string;
  category: 'METHODOLOGY' | 'SUFFICIENCY' | 'BENCHMARK' | 'WINDOW' | 'QUALITY';
  categoryLabel: string;
  title: string;
  question: string;
  reason: string;
}

export function getMetricEvidenceInvestigationQuestions(
  metricCode: string,
  context: {
    metricName?: string;
    asOfDate?: string;
    startDate?: string;
    horizon?: string;
    benchmarkCode?: string;
    observationCount?: number | null;
    observationThreshold?: number | null;
    calculationStatus?: string | null;
    limitations?: string | null;
  }
): EvidenceInvestigationQuestionItem[] {
  const def = getMetricEvidenceDefinition(metricCode);
  const questions: EvidenceInvestigationQuestionItem[] = [];

  const h = context.horizon || '3Y';
  const asOf = context.asOfDate || 'analysis cutoff';
  const start = context.startDate || 'start';
  const name = context.metricName || def.name;
  const bm = context.benchmarkCode || 'NIFTY 500 TRI';

  // 1. Primary Methodology Due Diligence Question
  questions.push({
    id: `${metricCode}-primary`,
    category: 'METHODOLOGY',
    categoryLabel: 'Core Methodology',
    title: 'Methodology Due Diligence',
    question: def.investigationQuestion,
    reason: def.investigationReasonTemplate(asOf),
  });

  // 2. Horizon & History Sufficiency Question
  const isInsufficient =
    context.calculationStatus === 'INSUFFICIENT_DATA' ||
    (context.observationCount != null &&
      context.observationThreshold != null &&
      context.observationCount < context.observationThreshold);

  if (isInsufficient) {
    questions.push({
      id: `${metricCode}-sufficiency`,
      category: 'SUFFICIENCY',
      categoryLabel: 'Data Sufficiency',
      title: 'Observation Coverage Shortfall',
      question: `Why is ${name} insufficient under the ${h} horizon (found ${context.observationCount ?? 0} vs ${context.observationThreshold ?? 'required'} trading days)?`,
      reason: `Investigating history coverage constraint and missing observation dates for ${metricCode} (${h}) as of ${asOf}.`,
    });
  } else {
    questions.push({
      id: `${metricCode}-sufficiency`,
      category: 'SUFFICIENCY',
      categoryLabel: 'Multi-Horizon Consistency',
      title: 'Multi-Cycle Persistence',
      question: `Does ${name} hold up consistently when evaluated across alternative lookback horizons (3Y, 5Y, and 10Y)?`,
      reason: `Evaluating horizon stability and multi-cycle consistency for ${metricCode} as of ${asOf}.`,
    });
  }

  // 3. Benchmark Appropriateness Question (for benchmark-relative metrics)
  const isBenchmarkRelative = [
    'RET-07', 'RAT-01', 'RAT-02', 'RAT-03', 'RAT-04',
    'MKT-01', 'MKT-02', 'MKT-03', 'MKT-04', 'MKT-05',
    'REL-01', 'REL-02', 'REL-03', 'RET-06'
  ].includes(metricCode);

  if (isBenchmarkRelative) {
    questions.push({
      id: `${metricCode}-benchmark`,
      category: 'BENCHMARK',
      categoryLabel: 'Benchmark Mapping',
      title: 'Benchmark Appropriateness',
      question: `Is ${bm} (Candidate Mapping) appropriate for this fund's category and mandate, or does it introduce style/factor tilt distortion?`,
      reason: `Auditing candidate benchmark assignment (${bm}) and mandate alignment for ${metricCode} as of ${asOf}.`,
    });
  }

  // 4. Evaluation Window Dependency Question
  questions.push({
    id: `${metricCode}-window`,
    category: 'WINDOW',
    categoryLabel: 'Window Dependency',
    title: 'Evaluation Window Sensitivity',
    question: `Does the observed ${name} result depend materially on the selected evaluation window (${start} to ${asOf})?`,
    reason: `Testing boundary date sensitivity and start-date dependency for ${metricCode} (${start} → ${asOf}).`,
  });

  // 5. Data Quality & Source Lineage Question
  questions.push({
    id: `${metricCode}-quality`,
    category: 'QUALITY',
    categoryLabel: 'Audit Lineage',
    title: 'Data Quality & Provenance',
    question: `Which data-quality limitation or temporal reporting lag most affects the certainty of this ${metricCode} calculation?`,
    reason: `Auditing underlying data quality, revision sequence, and observation lineage for ${metricCode} as of ${asOf}.`,
  });

  return questions;
}
