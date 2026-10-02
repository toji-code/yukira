import Link from "next/link";
import {
  PageContainer,
  SectionHeading,
  MetricGrid,
} from "@/components/layout/PageContainer";

/**
 * Governance state tokens for the specification catalog. These mirror the
 * `status-badge` state vocabulary used across metric tiles: hue encodes
 * epistemic state only, never metric category.
 */
type GovernanceState = "Approved Standard" | "Candidate Spec" | "Awaiting Holdings Feed";

const GOVERNANCE_STATE_CLASS: Record<GovernanceState, string> = {
  "Approved Standard": "status-badge state-approved",
  "Candidate Spec": "status-badge state-candidate",
  "Awaiting Holdings Feed": "status-badge state-unavailable",
};

interface MetricSpec {
  name: string;
  code: string;
  definition: string;
  state: GovernanceState;
}

interface DimensionSpec {
  ordinal: string;
  title: string;
  scope: string;
  open: boolean;
  metrics: MetricSpec[];
  supportingPrimitive?: {
    label: string;
    body: string;
    badge: string;
  };
}

const DIMENSIONS: DimensionSpec[] = [
  {
    ordinal: "1",
    title: "Return Quality (6 Metrics)",
    scope: "CAGR • Rolling Returns • Active Outperformance",
    open: true,
    supportingPrimitive: {
      label: "Supporting Primitive: RET-02 (Simple Period Return)",
      body: "Point-to-point percentage return R = (NAV_end / NAV_start) - 1. Used for window verification.",
      badge: "Verified Primitive",
    },
    metrics: [
      { name: "1Y CAGR", code: "RET-01", definition: "12-Month Compound Annual Growth Rate", state: "Candidate Spec" },
      { name: "3Y CAGR", code: "RET-03", definition: "36-Month Compound Annual Growth Rate", state: "Approved Standard" },
      { name: "5Y CAGR", code: "RET-04", definition: "60-Month Compound Annual Growth Rate", state: "Candidate Spec" },
      { name: "Rolling Return Mean", code: "RET-05", definition: "3Y Rolling CAGR Mean Distribution", state: "Candidate Spec" },
      { name: "Outperformance %", code: "RET-06", definition: "Percentage of Rolling Windows Beating TRI", state: "Candidate Spec" },
      { name: "Active Return", code: "RET-07", definition: "3Y Annualized Active Geometric Excess", state: "Candidate Spec" },
    ],
  },
  {
    ordinal: "2",
    title: "Risk & Tail Impairment (7 Metrics)",
    scope: "Volatility • Semideviation • Drawdowns • VaR",
    open: true,
    metrics: [
      { name: "Volatility", code: "RSK-01", definition: "Annualized Return Dispersion (√252, N-1)", state: "Approved Standard" },
      { name: "Semideviation", code: "RSK-02", definition: "Downside Deviation (MAR = 0.0%)", state: "Candidate Spec" },
      { name: "Max Drawdown", code: "RSK-03", definition: "Peak-to-Trough Worst Impairment", state: "Candidate Spec" },
      { name: "Drawdown Days", code: "RSK-04", definition: "Max Duration Peak to Prior High", state: "Candidate Spec" },
      { name: "Ulcer Index", code: "RSK-05", definition: "Root-Mean-Square Stress Severity", state: "Candidate Spec" },
      { name: "Historical VaR", code: "RSK-06", definition: "95% Empirical Loss Cutoff", state: "Candidate Spec" },
      { name: "Expected Shortfall", code: "RSK-07", definition: "CVaR 95% Mean Tail Loss", state: "Candidate Spec" },
    ],
  },
  {
    ordinal: "3",
    title: "Risk-Adjusted Returns (4 Metrics)",
    scope: "Sharpe • Sortino • Treynor • Information Ratio",
    open: false,
    metrics: [
      { name: "Sharpe Ratio", code: "RAT-01", definition: "Excess Return per Unit of Total Risk", state: "Approved Standard" },
      { name: "Sortino Ratio", code: "RAT-02", definition: "Excess Return per Downside Risk", state: "Candidate Spec" },
      { name: "Treynor Ratio", code: "RAT-03", definition: "Excess Return per Systematic Beta", state: "Approved Standard" },
      { name: "Information Ratio", code: "RAT-04", definition: "Active Alpha per Tracking Error", state: "Candidate Spec" },
    ],
  },
  {
    ordinal: "4",
    title: "Market Sensitivity & Capture (5 Metrics)",
    scope: "Beta • Downside Beta • Capture Ratios",
    open: false,
    metrics: [
      { name: "Portfolio Beta", code: "MKT-01", definition: "OLS Regression Slope vs NIFTY 50", state: "Approved Standard" },
      { name: "Downside Beta", code: "MKT-02", definition: "Sensitivity When Market Declines", state: "Approved Standard" },
      { name: "Upside Capture", code: "MKT-03", definition: "Benchmark Gain Participation", state: "Candidate Spec" },
      { name: "Downside Capture", code: "MKT-04", definition: "Benchmark Loss Participation", state: "Candidate Spec" },
      { name: "Capture Spread", code: "MKT-05", definition: "Upside Ratio Minus Downside Ratio", state: "Candidate Spec" },
    ],
  },
  {
    ordinal: "5",
    title: "Relative Benchmark & Alpha (3 Metrics)",
    scope: "Tracking Error • Jensen's Alpha • Mean Active Return",
    open: false,
    metrics: [
      { name: "Tracking Error", code: "REL-02", definition: "Annualized Standard Deviation of Excess Returns", state: "Candidate Spec" },
      { name: "Jensen's Alpha", code: "REL-03", definition: "Unexplained Excess Intercept Above CAPM Expectation", state: "Candidate Spec" },
      { name: "Annualized Mean Active Return", code: "REL-06", definition: "Mean Daily Excess Return Scaled Annualized", state: "Candidate Spec" },
    ],
  },
  {
    ordinal: "6 & 7",
    title: "Portfolio Structure & Governance (6 Metrics)",
    scope: "Concentration • Turnover • Expense Ratio",
    open: false,
    metrics: [
      { name: "Top-10 Concentration", code: "PRT-01", definition: "Weight Sum of Top 10 Holdings", state: "Awaiting Holdings Feed" },
      { name: "Active Share", code: "PRT-03", definition: "Portfolio Weight Divergence from Benchmark", state: "Awaiting Holdings Feed" },
      { name: "Direct Plan TER", code: "GOV-01", definition: "Total Expense Ratio for Direct Plan", state: "Candidate Spec" },
    ],
  },
];

const APPROVED_SLICES: Array<[string, string]> = [
  ["Compound Growth Rate (RET-03):", "Governed by approved 365.25/D annualization convention."],
  ["Annualized Volatility (RSK-01):", "Governed by approved √252, N-1 sample variance standard."],
  ["Sharpe Ratio (RAT-01):", "Governed by approved standard using FBIL 91-day T-Bill risk-free benchmark."],
  ["Treynor Ratio (RAT-03):", "Governed by approved excess-return and systematic beta standards."],
  ["Portfolio Beta (MKT-01):", "Governed by approved excess-return OLS regression against NIFTY 50 TRI."],
  ["Downside Beta (MKT-02):", "Governed by approved negative benchmark trading day conditioning."],
];

const CANDIDATE_SLICES: Array<[string, string]> = [
  ["Downside Semideviation (RSK-02):", "Implemented; divisor convention under empirical review."],
  ["Maximum Drawdown 3Y (RSK-03):", "Implemented candidate algorithm."],
  ["Drawdown Duration (RSK-04):", "Implemented candidate algorithm."],
  ["Ulcer Index (RSK-05):", "Implemented candidate algorithm."],
  ["Historical VaR 95% (RSK-06):", "Implemented candidate algorithm."],
  ["Expected Shortfall 95% (RSK-07):", "Implemented candidate algorithm."],
  ["Simple Period Return (RET-02):", "Operational verification primitive."],
];

const DETERMINISM_POINTS: Array<[string, string]> = [
  ["Zero LLM Calculations:", "Artificial intelligence is never permitted to calculate, estimate, or adjust financial figures."],
  ["Statutory Annualization:", "Normalized across 365.25 calendar days per year for CAGR and √252 trading days for volatility."],
  ["No Imputation:", "Missing observations are explicitly flagged as data gaps rather than smoothed with synthetic estimates."],
];

const LINEAGE_POINTS: Array<[string, string]> = [
  ["Risk-Free Rate:", "Grounded in the FBIL 91-Day Treasury Bill benchmark index."],
  ["Market Sensitivity:", "Regression against the NIFTY 50 Total Returns Index (TRI)."],
  ["6-Dimensional Quality:", "Every observation is audited for quality, verification, revision, freshness, presence, and integrity."],
];

const CAPABILITIES: Array<[string, string]> = [
  ["Multi-Dimensional Risk", "Beyond standard deviation: isolates downside volatility, tail risk (VaR & Expected Shortfall), and capital impairment duration."],
  ["Point-in-Time Integrity", "Evaluates historical queries strictly as of the knowledge cutoff timestamp. Prevents retroactively revised data from leaking into past evaluation dates."],
  ["Cryptographic Provenance", "Every calculation run records SHA-256 digests of raw ingestion payloads, exact formulas, engine versions, and observation timestamps."],
];

const LIFECYCLE_TIERS: Array<{
  tier: string;
  headline: string;
  body: string;
  status: string;
  badge: string;
}> = [
  {
    tier: "Tier 1: Implemented",
    headline: "Executable Code",
    body: "The algorithm is implemented in the Python Quantitative Engine and verified against deterministic unit tests. Given valid point-in-time input observations, it produces verifiable numbers.",
    status: "Status: 13 Analytical Slices Executable",
    badge: "state-accent",
  },
  {
    tier: "Tier 2: Validated",
    headline: "Empirical Verification",
    body: "The methodology has been tested against multi-year historical data across bull, bear, and sideways regimes; reconciled against independent institutional vendor datasets; and verified for statistical soundness.",
    status: "Status: Multi-cycle Empirical Regime Testing",
    badge: "state-candidate",
  },
  {
    tier: "Tier 3: Approved",
    headline: "Governance Authorization",
    body: "Formally authorized by governance review for live decision support. Approved standards define explicit annualization, denominator, and benchmark conventions.",
    status: "Status: 5 Approved Core Standards",
    badge: "state-approved",
  },
];

function RuleList({ items }: { items: Array<[string, string]> }) {
  return (
    <ul className="space-y-2">
      {items.map(([term, detail]) => (
        <li key={term} className="flex gap-2 text-[13px] leading-[1.5] text-text-secondary">
          <span aria-hidden className="mt-[7px] h-px w-3 flex-none bg-accent" />
          <span>
            <strong className="font-semibold text-text-primary">{term}</strong>{" "}
            {detail}
          </span>
        </li>
      ))}
    </ul>
  );
}

export default function MethodologyPage() {
  return (
    <PageContainer
      eyebrow="Governance"
      title="Methodology Governance & Quantitative Architecture"
      subtitle="Strict epistemic separation of software implementation, empirical validation, and institutional production approval."
      breadcrumbs={[
        { label: "Home", href: "/" },
        { label: "Methodology", href: "/methodology" },
      ]}
      width="wide"
    >
      {/* Epistemic Mandate Alert */}
      <section className="panel state-candidate p-4 md:p-5">
        <div className="flex items-start gap-3">
          <span className="status-badge state-candidate mt-0.5 h-auto shrink-0">
            <span className="status-dot" aria-hidden />
            Mandate
          </span>
          <div className="min-w-0 space-y-2">
            <p className="eyebrow">Core Epistemic Governance Mandate</p>
            <p className="data-value-md">
              IMPLEMENTED ≠ VALIDATED ≠ APPROVED PRODUCTION METHODOLOGY
            </p>
            <p className="max-w-[76ch] text-[13px] leading-[1.55] text-text-secondary">
              In YUKIRA, implementing an algorithm in code does{" "}
              <strong className="font-semibold text-text-primary">not</strong> mean the
              methodology has been validated for production decision support. No metric
              constitutes an investment recommendation, rating, or commercial advice.
            </p>
          </div>
        </div>
      </section>

      {/* Governance Summary Grid */}
      <section className="mt-8">
        <SectionHeading
          ordinal="00"
          title="Authoritative Governance Status"
          description="Counts describe registry state, not performance. They are not quality scores."
        />
        <MetricGrid min={220} className="mt-3" >
          <div className="metric-tile">
            <p className="def-label">Engine Capabilities</p>
            <p className="data-value-md text-accent">13 Analytical Slices</p>
            <p className="mono-meta mt-1">Return, Volatility, Drawdown, VaR, Alpha</p>
          </div>
          <div className="metric-tile">
            <p className="def-label">Approved Standards</p>
            <p className="data-value-md text-approved-fg">5 Foundation Specs</p>
            <p className="mono-meta mt-1">CAGR, Volatility, Sharpe, Treynor, Beta</p>
          </div>
          <div className="metric-tile">
            <p className="def-label">Candidate / Deferred</p>
            <p className="data-value-md text-candidate-fg">4 Under Review</p>
            <p className="mono-meta mt-1">Semideviation, Drawdown Regimes</p>
          </div>
          <div className="metric-tile">
            <p className="def-label">Commercial Advice</p>
            <p className="data-value-md">STRICTLY ZERO</p>
            <p className="mono-meta mt-1">Zero star ratings • Zero tips</p>
          </div>
        </MetricGrid>
      </section>

      {/* SECTION 1: What YUKIRA's Methodology Does */}
      <section className="mt-10 space-y-3">
        <SectionHeading
          ordinal="01"
          title="What YUKIRA's Methodology Does"
          description="Substitutes point-in-time quantitative evidence for point-to-point returns and promotional ratings."
        />
        <div className="panel p-4 md:p-5 space-y-4">
          <p className="prose-measure text-[13px] leading-[1.6] text-text-secondary">
            Traditional investment portals present trailing point-to-point returns,
            promotional star ratings, and marketing-driven performance claims. YUKIRA
            replaces these heuristics with institutional-grade, point-in-time quantitative
            evidence.
          </p>
          <div className="grid gap-4 md:grid-cols-3">
            {CAPABILITIES.map(([heading, body]) => (
              <div key={heading} className="panel-inset p-3">
                <p className="def-label">{heading}</p>
                <p className="text-[13px] leading-[1.5] text-text-secondary">{body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* SECTION 2: How the Framework Works */}
      <section className="mt-10 space-y-3">
        <SectionHeading
          ordinal="02"
          title="How the Quantitative Framework Works"
          description="Deterministic computation and auditable lineage are structural, not stylistic."
        />
        <div className="grid gap-4 md:grid-cols-2">
          <div className="panel p-4 md:p-5 space-y-3">
            <p className="def-label">Deterministic Financial Computation</p>
            <p className="text-[13px] leading-[1.55] text-text-secondary">
              All financial metrics are computed strictly by deterministic mathematical
              algorithms implemented in our Python Quantitative Engine and verified through
              automated test suites.
            </p>
            <RuleList items={DETERMINISM_POINTS} />
          </div>

          <div className="panel p-4 md:p-5 space-y-3">
            <p className="def-label">Authoritative Benchmarks & Data Lineage</p>
            <p className="text-[13px] leading-[1.55] text-text-secondary">
              Metrics are benchmarked against official institutional market infrastructure and
              verified against official regulatory feeds.
            </p>
            <RuleList items={LINEAGE_POINTS} />
          </div>
        </div>
      </section>

      {/* SECTION 3: The Three Lifecycle Tiers */}
      <section className="mt-10 space-y-3">
        <SectionHeading
          ordinal="03"
          title="The Three Lifecycle Tiers"
          description="A metric reaches production only by traversing all three tiers."
        />
        <div className="grid gap-4 md:grid-cols-3">
          {LIFECYCLE_TIERS.map((tier) => (
            <div key={tier.tier} className="panel p-4">
              <div className="flex items-center justify-between gap-2">
                <p className="def-label mb-0">{tier.tier}</p>
                <span className={`status-badge ${tier.badge}`}>
                  <span className="status-dot" aria-hidden />
                  {tier.tier.split(": ")[0]}
                </span>
              </div>
              <h3 className="mt-2 text-[15px] font-semibold tracking-[-0.01em] text-text-primary">
                {tier.headline}
              </h3>
              <p className="mt-2 text-[13px] leading-[1.55] text-text-secondary">
                {tier.body}
              </p>
              <div className="metric-rule my-3" aria-hidden />
              <p className="mono-meta">{tier.status}</p>
            </div>
          ))}
        </div>
      </section>

      {/* SECTION 4: Implemented Analytical Slices Breakdown */}
      <section className="mt-10 space-y-3">
        <SectionHeading
          ordinal="04"
          title="Governance Status of 13 Implemented Analytical Slices"
          description="Implemented vertical slices do not share one identical governance state. They map to approved, candidate, or deferred methodologies."
          action={
            <span className="status-badge state-accent shrink-0">
              Standard Governance Alignment
            </span>
          }
        />
        <div className="grid gap-4 md:grid-cols-2">
          <div className="panel state-approved p-4">
            <div className="flex items-center gap-2">
              <span className="status-badge state-approved">
                <span className="status-dot" aria-hidden />
                Approved
              </span>
              <p className="eyebrow">Slices Executing Approved Methodologies</p>
            </div>
            <p className="mt-2 text-[13px] leading-[1.5] text-text-secondary">
              These analytical vertical slices execute algorithms governed by approved
              methodology standards:
            </p>
            <div className="mt-3">
              <RuleList items={APPROVED_SLICES} />
            </div>
          </div>

          <div className="panel state-candidate p-4">
            <div className="flex items-center gap-2">
              <span className="status-badge state-candidate">
                <span className="status-dot" aria-hidden />
                Candidate
              </span>
              <p className="eyebrow">Candidate Slices &amp; Deferred Components</p>
            </div>
            <p className="mt-2 text-[13px] leading-[1.5] text-text-secondary">
              These slices are implemented in code but operate as candidate specifications or
              contain deferred methodology decisions:
            </p>
            <div className="mt-3">
              <RuleList items={CANDIDATE_SLICES} />
            </div>
          </div>
        </div>
      </section>

      {/* SECTION 5: Full Metric Specification Inventory (Progressive Disclosure) */}
      <section className="mt-10 space-y-3">
        <SectionHeading
          ordinal="05"
          title="Quantitative Metric Specification Catalog"
          description="Complete inventory of 30 quantitative metrics across 7 analytical dimensions. Expand each dimension for exact formulas, lookback windows, and governance statuses."
        />
        <div className="space-y-3">
          {DIMENSIONS.map((dimension) => (
            <details
              key={dimension.ordinal}
              className="group panel"
              open={dimension.open}
            >
              <summary className="flex cursor-pointer list-none items-center justify-between gap-3 p-4">
                <div className="flex min-w-0 flex-col gap-1">
                  <div className="flex items-baseline gap-2">
                    <span className="tab-ordinal">{dimension.ordinal}</span>
                    <span className="text-[15px] font-semibold tracking-[-0.01em] text-text-primary">
                      {dimension.title}
                    </span>
                  </div>
                  <span className="mono-meta">{dimension.scope}</span>
                </div>
                <div className="flex shrink-0 items-center gap-3">
                  <span className="status-badge state-unavailable">
                    {dimension.metrics.length} listed
                  </span>
                  <span
                    aria-hidden
                    className="text-[11px] text-text-tertiary transition-transform group-open:rotate-180"
                  >
                    ▼
                  </span>
                </div>
              </summary>
              <div className="border-t border-border">
                <div className="scroll-region">
                  <table className="data-table">
                    <caption className="sr-only">
                      {dimension.title} specification catalog with governance states
                    </caption>
                    <thead>
                      <tr>
                        <th scope="col">Metric</th>
                        <th scope="col">Code</th>
                        <th scope="col">Definition</th>
                        <th scope="col">Governance State</th>
                      </tr>
                    </thead>
                    <tbody>
                      {dimension.metrics.map((metric) => (
                        <tr key={metric.code}>
                          <td className="key whitespace-nowrap font-medium">{metric.name}</td>
                          <td className="num text-left">{metric.code}</td>
                          <td>{metric.definition}</td>
                          <td>
                            <span className={GOVERNANCE_STATE_CLASS[metric.state]}>
                              {metric.state}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                {dimension.supportingPrimitive && (
                  <div className="flex flex-col gap-2 border-t border-border px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
                    <div className="min-w-0">
                      <p className="data-value-sm text-accent">
                        {dimension.supportingPrimitive.label}
                      </p>
                      <p className="mt-1 max-w-[76ch] text-[13px] leading-[1.5] text-text-secondary">
                        {dimension.supportingPrimitive.body}
                      </p>
                    </div>
                    <span className="status-badge state-operational shrink-0 self-start sm:self-auto">
                      <span className="status-dot" aria-hidden />
                      {dimension.supportingPrimitive.badge}
                    </span>
                  </div>
                )}
              </div>
            </details>
          ))}
        </div>
      </section>

      {/* Navigation Links */}
      <nav className="mt-10 flex flex-col items-stretch gap-3 border-t border-border pt-6 sm:flex-row sm:items-center sm:justify-between">
        <Link href="/" className="text-link">
          &larr; Return to Overview
        </Link>
        <Link href="/funds" className="btn btn-primary self-start">
          Explore Fund Catalog &rarr;
        </Link>
      </nav>
    </PageContainer>
  );
}