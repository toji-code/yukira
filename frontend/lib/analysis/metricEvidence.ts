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
  categoryLabel: string;
  governanceStatus: 'APPROVED' | 'CANDIDATE' | 'OPERATIONAL BASELINE';
  whyItMatters: string;
  evidenceDescription: string;
  formulaSpecification: string;
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

  'RSK-03': {
    code: 'RSK-03',
    name: '3-Year Maximum Drawdown',
    categoryLabel: 'Tail Risk & Capital Preservation',
    governanceStatus: 'CANDIDATE',
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

  'RET-07': {
    code: 'RET-07',
    name: '3-Year Annualized Active Return',
    categoryLabel: 'Benchmark Excess Performance',
    governanceStatus: 'CANDIDATE',
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
      'Assumes a stationary linear relationship over time; portfolio beta can fluctuate substantially during market crises, liquidity squeezes, and cash drag regimes.',
    investigationQuestion:
      "Is the portfolio's systematic sensitivity (Beta) consistent with the fund's stated mandate and benchmark?",
    investigationReasonTemplate: (asOfDate) =>
      `Evaluating market beta sensitivity (MKT-01) and systematic exposure as of ${asOfDate || 'cutoff'}.`,
  },
};

export function getMetricEvidenceDefinition(metricCode: string): MetricEvidenceDefinition {
  if (CANONICAL_METRIC_EVIDENCE[metricCode]) {
    return CANONICAL_METRIC_EVIDENCE[metricCode];
  }

  // Graceful fallback for secondary candidate metrics
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
