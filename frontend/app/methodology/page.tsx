import Link from "next/link";
import { PageContainer } from "@/components/layout/PageContainer";

export default function MethodologyPage() {
  return (
    <PageContainer
      title="Methodology Governance & Quantitative Architecture"
      subtitle="Strict epistemic separation of software implementation, empirical validation, and institutional production approval."
      breadcrumbs={[
        { label: "Home", href: "/" },
        { label: "Methodology", href: "/methodology" },
      ]}
    >
      {/* Epistemic Mandate Alert */}
      <div className="mb-8 rounded-xl border border-warning/30 bg-warning/10 p-5">
        <div className="flex items-start gap-3">
          <div className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-warning/20 text-xs font-bold text-warning font-mono">
            !
          </div>
          <div className="space-y-1">
            <h3 className="text-xs font-bold uppercase tracking-wider text-warning font-mono">
              Core Epistemic Governance Mandate
            </h3>
            <p className="text-xs font-semibold leading-relaxed text-text-primary">
              IMPLEMENTED ≠ VALIDATED ≠ APPROVED PRODUCTION METHODOLOGY
            </p>
            <p className="text-xs leading-relaxed text-text-secondary">
              In YUKIRA, implementing an algorithm in code does <strong>not</strong> mean the methodology has been validated for production decision support. No metric constitutes an investment recommendation, rating, or commercial advice.
            </p>
          </div>
        </div>
      </div>

      {/* Governance Summary Grid */}
      <div className="mb-10 rounded-xl border border-border bg-card p-6 font-mono text-xs shadow-xs">
        <h3 className="text-xs font-semibold uppercase tracking-wider text-text-muted mb-4">
          Authoritative Governance Status
        </h3>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <div className="rounded-lg border border-border bg-surface-elevated p-3.5">
            <span className="text-text-muted text-[10px] uppercase block">Engine Capabilities</span>
            <span className="text-accent font-bold text-sm mt-1 block">13 Analytical Slices</span>
            <span className="text-[10px] text-text-muted mt-1 block">Return, Volatility, Drawdown, VaR, Alpha</span>
          </div>

          <div className="rounded-lg border border-success/30 bg-success/10 p-3.5">
            <span className="text-text-muted text-[10px] uppercase block">Approved Standards</span>
            <span className="text-success font-bold text-sm mt-1 block">5 Foundation Specs</span>
            <span className="text-[10px] text-text-muted mt-1 block">CAGR, Volatility, Sharpe, Treynor, Beta</span>
          </div>

          <div className="rounded-lg border border-warning/30 bg-warning/10 p-3.5">
            <span className="text-text-muted text-[10px] uppercase block">Candidate / Deferred</span>
            <span className="text-warning font-bold text-sm mt-1 block">4 Under Review</span>
            <span className="text-[10px] text-text-muted mt-1 block">Semideviation, Drawdown Regimes</span>
          </div>

          <div className="rounded-lg border border-border bg-surface-elevated p-3.5">
            <span className="text-text-muted text-[10px] uppercase block">Commercial Advice</span>
            <span className="text-text-primary font-bold text-sm mt-1 block">STRICTLY ZERO</span>
            <span className="text-[10px] text-text-muted mt-1 block">Zero star ratings &bull; Zero tips</span>
          </div>
        </div>
      </div>

      {/* SECTION 1: What YUKIRA's Methodology Does */}
      <section className="mb-12 space-y-4">
        <h2 className="text-xl font-bold text-text-primary">
          1. What YUKIRA&apos;s Methodology Does
        </h2>
        <div className="rounded-xl border border-border bg-card p-6 shadow-xs space-y-4">
          <p className="text-sm leading-relaxed text-text-secondary">
            Traditional investment portals present trailing point-to-point returns, promotional star ratings, and marketing-driven performance claims. YUKIRA replaces these heuristics with institutional-grade, point-in-time quantitative evidence.
          </p>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 pt-2">
            <div className="rounded-lg border border-border bg-surface-elevated p-4 space-y-2">
              <h4 className="font-semibold text-text-primary text-xs uppercase tracking-wider font-mono">
                Multi-Dimensional Risk
              </h4>
              <p className="text-xs text-text-secondary leading-relaxed">
                Beyond standard deviation: isolates downside volatility, tail risk (VaR &amp; Expected Shortfall), and capital impairment duration.
              </p>
            </div>
            <div className="rounded-lg border border-border bg-surface-elevated p-4 space-y-2">
              <h4 className="font-semibold text-text-primary text-xs uppercase tracking-wider font-mono">
                Point-in-Time Integrity
              </h4>
              <p className="text-xs text-text-secondary leading-relaxed">
                Evaluates historical queries strictly as of the knowledge cutoff timestamp. Prevents retroactively revised data from leaking into past evaluation dates.
              </p>
            </div>
            <div className="rounded-lg border border-border bg-surface-elevated p-4 space-y-2">
              <h4 className="font-semibold text-text-primary text-xs uppercase tracking-wider font-mono">
                Cryptographic Provenance
              </h4>
              <p className="text-xs text-text-secondary leading-relaxed">
                Every calculation run records SHA-256 digests of raw ingestion payloads, exact formulas, engine versions, and observation timestamps.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* SECTION 2: How the Framework Works */}
      <section className="mb-12 space-y-4">
        <h2 className="text-xl font-bold text-text-primary">
          2. How the Quantitative Framework Works
        </h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div className="rounded-xl border border-border bg-card p-6 shadow-xs space-y-3">
            <h3 className="text-base font-semibold text-text-primary">
              Deterministic Financial Computation
            </h3>
            <p className="text-xs leading-relaxed text-text-secondary">
              All financial metrics are computed strictly by deterministic mathematical algorithms implemented in our Python Quantitative Engine and verified through automated test suites.
            </p>
            <ul className="space-y-2 text-xs text-text-secondary pt-2">
              <li className="flex items-start gap-2">
                <span className="text-accent font-bold">&bull;</span>
                <span><strong>Zero LLM Calculations:</strong> Artificial intelligence is never permitted to calculate, estimate, or adjust financial figures.</span>
              </li>
              <li className="flex items-start gap-2">
                <span className="text-accent font-bold">&bull;</span>
                <span><strong>Statutory Annualization:</strong> Normalized across 365.25 calendar days per year for CAGR and √252 trading days for volatility.</span>
              </li>
              <li className="flex items-start gap-2">
                <span className="text-accent font-bold">&bull;</span>
                <span><strong>No Imputation:</strong> Missing observations are explicitly flagged as data gaps rather than smoothed with synthetic estimates.</span>
              </li>
            </ul>
          </div>

          <div className="rounded-xl border border-border bg-card p-6 shadow-xs space-y-3">
            <h3 className="text-base font-semibold text-text-primary">
              Authoritative Benchmarks &amp; Data Lineage
            </h3>
            <p className="text-xs leading-relaxed text-text-secondary">
              Metrics are benchmarked against official institutional market infrastructure and verified against official regulatory feeds.
            </p>
            <ul className="space-y-2 text-xs text-text-secondary pt-2">
              <li className="flex items-start gap-2">
                <span className="text-accent font-bold">&bull;</span>
                <span><strong>Risk-Free Rate:</strong> Grounded in the FBIL 91-Day Treasury Bill benchmark index.</span>
              </li>
              <li className="flex items-start gap-2">
                <span className="text-accent font-bold">&bull;</span>
                <span><strong>Market Sensitivity:</strong> Regression against the NIFTY 50 Total Returns Index (TRI).</span>
              </li>
              <li className="flex items-start gap-2">
                <span className="text-accent font-bold">&bull;</span>
                <span><strong>6-Dimensional Quality:</strong> Every observation is audited for quality, verification, revision, freshness, presence, and integrity.</span>
              </li>
            </ul>
          </div>
        </div>
      </section>

      {/* SECTION 3: The Three Lifecycle Tiers */}
      <section className="mb-12 space-y-4">
        <h2 className="text-xl font-bold text-text-primary">
          3. The Three Lifecycle Tiers
        </h2>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <div className="rounded-xl border border-accent/30 bg-accent/5 p-5">
            <div className="flex items-center gap-2 font-mono text-xs font-bold text-accent uppercase">
              <span className="h-2.5 w-2.5 rounded-full bg-accent" />
              Tier 1: Implemented
            </div>
            <h3 className="mt-2 text-base font-semibold text-text-primary">Executable Code</h3>
            <p className="mt-2 text-xs leading-relaxed text-text-secondary">
              The algorithm is implemented in the Python Quantitative Engine and verified against deterministic unit tests. Given valid point-in-time input observations, it produces verifiable numbers.
            </p>
            <div className="mt-4 border-t border-accent/20 pt-3 text-[11px] font-mono text-accent">
              Status: 13 Analytical Slices Executable
            </div>
          </div>

          <div className="rounded-xl border border-warning/30 bg-warning/5 p-5">
            <div className="flex items-center gap-2 font-mono text-xs font-bold text-warning uppercase">
              <span className="h-2.5 w-2.5 rounded-full bg-warning" />
              Tier 2: Validated
            </div>
            <h3 className="mt-2 text-base font-semibold text-text-primary">Empirical Verification</h3>
            <p className="mt-2 text-xs leading-relaxed text-text-secondary">
              The methodology has been tested against multi-year historical data across bull, bear, and sideways regimes; reconciled against independent institutional vendor datasets; and verified for statistical soundness.
            </p>
            <div className="mt-4 border-t border-warning/20 pt-3 text-[11px] font-mono text-warning">
              Status: Multi-cycle Empirical Regime Testing
            </div>
          </div>

          <div className="rounded-xl border border-success/30 bg-success/5 p-5">
            <div className="flex items-center gap-2 font-mono text-xs font-bold text-success uppercase">
              <span className="h-2.5 w-2.5 rounded-full bg-success" />
              Tier 3: Approved
            </div>
            <h3 className="mt-2 text-base font-semibold text-text-primary">Governance Authorization</h3>
            <p className="mt-2 text-xs leading-relaxed text-text-secondary">
              Formally authorized by governance review for live decision support. Approved standards define explicit annualization, denominator, and benchmark conventions.
            </p>
            <div className="mt-4 border-t border-success/20 pt-3 text-[11px] font-mono text-success">
              Status: 5 Approved Core Standards
            </div>
          </div>
        </div>
      </section>

      {/* SECTION 4: Implemented Analytical Slices Breakdown */}
      <section className="mb-12 rounded-xl border border-border bg-card p-6 font-mono text-xs shadow-xs">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between pb-3 border-b border-border mb-4 gap-2">
          <div>
            <h2 className="text-sm font-bold text-text-primary uppercase tracking-wider">
              Governance Status of 13 Implemented Analytical Slices
            </h2>
            <p className="text-text-muted font-sans text-xs mt-1">
              Implemented vertical slices do not share one identical governance state. They map to approved, candidate, or deferred methodologies.
            </p>
          </div>
          <span className="text-[10px] text-accent bg-accent/10 px-2.5 py-1 rounded border border-accent/30 shrink-0 self-start sm:self-center">
            Standard Governance Alignment
          </span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="rounded-lg border border-success/30 bg-success/5 p-4 space-y-2">
            <div className="flex items-center gap-2 font-bold text-success text-xs uppercase">
              <span className="h-2 w-2 rounded-full bg-success" />
              Slices Executing Approved Methodologies
            </div>
            <p className="text-text-secondary font-sans text-xs leading-relaxed">
              These analytical vertical slices execute algorithms governed by approved methodology standards:
            </p>
            <ul className="space-y-1.5 text-text-secondary text-[11px]">
              <li>&bull; <strong className="text-text-primary">Compound Growth Rate (RET-03):</strong> Governed by approved 365.25/D annualization convention.</li>
              <li>&bull; <strong className="text-text-primary">Annualized Volatility (RSK-01):</strong> Governed by approved &radic;252, N-1 sample variance standard.</li>
              <li>&bull; <strong className="text-text-primary">Sharpe Ratio (RAT-01):</strong> Governed by approved standard using FBIL 91-day T-Bill risk-free benchmark.</li>
              <li>&bull; <strong className="text-text-primary">Treynor Ratio (RAT-03):</strong> Governed by approved excess-return and systematic beta standards.</li>
              <li>&bull; <strong className="text-text-primary">Portfolio Beta (MKT-01):</strong> Governed by approved excess-return OLS regression against NIFTY 50 TRI.</li>
              <li>&bull; <strong className="text-text-primary">Downside Beta (MKT-02):</strong> Governed by approved negative benchmark trading day conditioning.</li>
            </ul>
          </div>

          <div className="rounded-lg border border-warning/30 bg-warning/5 p-4 space-y-2">
            <div className="flex items-center gap-2 font-bold text-warning text-xs uppercase">
              <span className="h-2 w-2 rounded-full bg-warning" />
              Candidate Slices &amp; Deferred Components
            </div>
            <p className="text-text-secondary font-sans text-xs leading-relaxed">
              These slices are implemented in code but operate as candidate specifications or contain deferred methodology decisions:
            </p>
            <ul className="space-y-1.5 text-text-secondary text-[11px]">
              <li>&bull; <strong className="text-text-primary">Downside Semideviation (RSK-02):</strong> Implemented; divisor convention under empirical review.</li>
              <li>&bull; <strong className="text-text-primary">Maximum Drawdown 3Y (RSK-03):</strong> Implemented candidate algorithm.</li>
              <li>&bull; <strong className="text-text-primary">Drawdown Duration (RSK-04):</strong> Implemented candidate algorithm.</li>
              <li>&bull; <strong className="text-text-primary">Ulcer Index (RSK-05):</strong> Implemented candidate algorithm.</li>
              <li>&bull; <strong className="text-text-primary">Historical VaR 95% (RSK-06):</strong> Implemented candidate algorithm.</li>
              <li>&bull; <strong className="text-text-primary">Expected Shortfall 95% (RSK-07):</strong> Implemented candidate algorithm.</li>
              <li>&bull; <strong className="text-text-primary">Simple Period Return (RET-02):</strong> Operational verification primitive.</li>
            </ul>
          </div>
        </div>
      </section>

      {/* SECTION 5: Full 30-Metric Specification Inventory (Progressive Disclosure) */}
      <section className="mb-12 space-y-4">
        <div>
          <h2 className="text-xl font-bold text-text-primary">
            4. Quantitative Metric Specification Catalog
          </h2>
          <p className="text-xs text-text-muted mt-1 max-w-3xl">
            Complete inventory of 30 quantitative metrics across 7 analytical dimensions. Expand each dimension for exact formulas, lookback windows, and governance statuses.
          </p>
        </div>

        <div className="space-y-4">
          {/* Dimension 1 */}
          <details className="group rounded-xl border border-border bg-card p-5 shadow-xs transition open:pb-6" open>
            <summary className="flex items-center justify-between cursor-pointer font-mono text-xs list-none">
              <div className="flex items-center gap-2">
                <span className="text-accent font-bold uppercase">1. Return Quality (6 Metrics)</span>
                <span className="text-[10px] text-text-muted bg-surface-elevated px-2 py-0.5 rounded border border-border">
                  CAGR &bull; Rolling Returns &bull; Active Outperformance
                </span>
              </div>
              <span className="text-text-muted group-open:rotate-180 transition-transform">▼</span>
            </summary>
            <div className="mt-4 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 font-mono text-xs">
              <div className="bg-surface-elevated p-3 rounded-lg border border-border">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-text-primary">1Y CAGR</span>
                  <span className="text-[10px] text-text-muted">RET-01</span>
                </div>
                <span className="text-[11px] text-text-secondary font-sans block mt-1">12-Month Compound Annual Growth Rate</span>
                <span className="text-[10px] text-warning block mt-2">Candidate Spec</span>
              </div>
              <div className="bg-surface-elevated p-3 rounded-lg border border-border">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-text-primary">3Y CAGR</span>
                  <span className="text-[10px] text-text-muted">RET-03</span>
                </div>
                <span className="text-[11px] text-text-secondary font-sans block mt-1">36-Month Compound Annual Growth Rate</span>
                <span className="text-[10px] text-success block mt-2">Approved Standard</span>
              </div>
              <div className="bg-surface-elevated p-3 rounded-lg border border-border">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-text-primary">5Y CAGR</span>
                  <span className="text-[10px] text-text-muted">RET-04</span>
                </div>
                <span className="text-[11px] text-text-secondary font-sans block mt-1">60-Month Compound Annual Growth Rate</span>
                <span className="text-[10px] text-warning block mt-2">Candidate Spec</span>
              </div>
              <div className="bg-surface-elevated p-3 rounded-lg border border-border">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-text-primary">Rolling Return Mean</span>
                  <span className="text-[10px] text-text-muted">RET-05</span>
                </div>
                <span className="text-[11px] text-text-secondary font-sans block mt-1">3Y Rolling CAGR Mean Distribution</span>
                <span className="text-[10px] text-warning block mt-2">Candidate Spec</span>
              </div>
              <div className="bg-surface-elevated p-3 rounded-lg border border-border">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-text-primary">Outperformance %</span>
                  <span className="text-[10px] text-text-muted">RET-06</span>
                </div>
                <span className="text-[11px] text-text-secondary font-sans block mt-1">Percentage of Rolling Windows Beating TRI</span>
                <span className="text-[10px] text-warning block mt-2">Candidate Spec</span>
              </div>
              <div className="bg-surface-elevated p-3 rounded-lg border border-border">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-text-primary">Active Return</span>
                  <span className="text-[10px] text-text-muted">RET-07</span>
                </div>
                <span className="text-[11px] text-text-secondary font-sans block mt-1">3Y Annualized Active Geometric Excess</span>
                <span className="text-[10px] text-warning block mt-2">Candidate Spec</span>
              </div>
            </div>
            <div className="mt-3 p-3 rounded-lg border border-accent/30 bg-accent/5 font-mono text-[11px] flex flex-col sm:flex-row sm:items-center justify-between gap-2">
              <div>
                <span className="font-bold text-accent">Supporting Primitive: RET-02 (Simple Period Return)</span>
                <span className="text-text-muted font-sans block">Point-to-point percentage return R = (NAV_end / NAV_start) - 1. Used for window verification.</span>
              </div>
              <span className="text-[10px] text-accent bg-accent/10 px-2 py-0.5 rounded border border-accent/20 shrink-0">
                Verified Primitive
              </span>
            </div>
          </details>

          {/* Dimension 2 */}
          <details className="group rounded-xl border border-border bg-card p-5 shadow-xs transition open:pb-6" open>
            <summary className="flex items-center justify-between cursor-pointer font-mono text-xs list-none">
              <div className="flex items-center gap-2">
                <span className="text-warning font-bold uppercase">2. Risk &amp; Tail Impairment (7 Metrics)</span>
                <span className="text-[10px] text-text-muted bg-surface-elevated px-2 py-0.5 rounded border border-border">
                  Volatility &bull; Semideviation &bull; Drawdowns &bull; VaR
                </span>
              </div>
              <span className="text-text-muted group-open:rotate-180 transition-transform">▼</span>
            </summary>
            <div className="mt-4 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 font-mono text-xs">
              <div className="bg-surface-elevated p-3 rounded-lg border border-border">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-text-primary">Volatility</span>
                  <span className="text-[10px] text-text-muted">RSK-01</span>
                </div>
                <span className="text-[11px] text-text-secondary font-sans block mt-1">Annualized Return Dispersion (√252, N-1)</span>
                <span className="text-[10px] text-success block mt-2">Approved Standard</span>
              </div>
              <div className="bg-surface-elevated p-3 rounded-lg border border-border">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-text-primary">Semideviation</span>
                  <span className="text-[10px] text-text-muted">RSK-02</span>
                </div>
                <span className="text-[11px] text-text-secondary font-sans block mt-1">Downside Deviation (MAR = 0.0%)</span>
                <span className="text-[10px] text-warning block mt-2">Candidate Spec</span>
              </div>
              <div className="bg-surface-elevated p-3 rounded-lg border border-border">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-text-primary">Max Drawdown</span>
                  <span className="text-[10px] text-text-muted">RSK-03</span>
                </div>
                <span className="text-[11px] text-text-secondary font-sans block mt-1">Peak-to-Trough Worst Impairment</span>
                <span className="text-[10px] text-warning block mt-2">Candidate Spec</span>
              </div>
              <div className="bg-surface-elevated p-3 rounded-lg border border-border">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-text-primary">Drawdown Days</span>
                  <span className="text-[10px] text-text-muted">RSK-04</span>
                </div>
                <span className="text-[11px] text-text-secondary font-sans block mt-1">Max Duration Peak to Prior High</span>
                <span className="text-[10px] text-warning block mt-2">Candidate Spec</span>
              </div>
              <div className="bg-surface-elevated p-3 rounded-lg border border-border">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-text-primary">Ulcer Index</span>
                  <span className="text-[10px] text-text-muted">RSK-05</span>
                </div>
                <span className="text-[11px] text-text-secondary font-sans block mt-1">Root-Mean-Square Stress Severity</span>
                <span className="text-[10px] text-warning block mt-2">Candidate Spec</span>
              </div>
              <div className="bg-surface-elevated p-3 rounded-lg border border-border">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-text-primary">Historical VaR</span>
                  <span className="text-[10px] text-text-muted">RSK-06</span>
                </div>
                <span className="text-[11px] text-text-secondary font-sans block mt-1">95% Empirical Loss Cutoff</span>
                <span className="text-[10px] text-warning block mt-2">Candidate Spec</span>
              </div>
              <div className="bg-surface-elevated p-3 rounded-lg border border-border">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-text-primary">Expected Shortfall</span>
                  <span className="text-[10px] text-text-muted">RSK-07</span>
                </div>
                <span className="text-[11px] text-text-secondary font-sans block mt-1">CVaR 95% Mean Tail Loss</span>
                <span className="text-[10px] text-warning block mt-2">Candidate Spec</span>
              </div>
            </div>
          </details>

          {/* Dimension 3 */}
          <details className="group rounded-xl border border-border bg-card p-5 shadow-xs transition open:pb-6">
            <summary className="flex items-center justify-between cursor-pointer font-mono text-xs list-none">
              <div className="flex items-center gap-2">
                <span className="text-success font-bold uppercase">3. Risk-Adjusted Returns (4 Metrics)</span>
                <span className="text-[10px] text-text-muted bg-surface-elevated px-2 py-0.5 rounded border border-border">
                  Sharpe &bull; Sortino &bull; Treynor &bull; Information Ratio
                </span>
              </div>
              <span className="text-text-muted group-open:rotate-180 transition-transform">▼</span>
            </summary>
            <div className="mt-4 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 font-mono text-xs">
              <div className="bg-surface-elevated p-3 rounded-lg border border-border">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-text-primary">Sharpe Ratio</span>
                  <span className="text-[10px] text-text-muted">RAT-01</span>
                </div>
                <span className="text-[11px] text-text-secondary font-sans block mt-1">Excess Return per Unit of Total Risk</span>
                <span className="text-[10px] text-success block mt-2">Approved Standard</span>
              </div>
              <div className="bg-surface-elevated p-3 rounded-lg border border-border">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-text-primary">Sortino Ratio</span>
                  <span className="text-[10px] text-text-muted">RAT-02</span>
                </div>
                <span className="text-[11px] text-text-secondary font-sans block mt-1">Excess Return per Downside Risk</span>
                <span className="text-[10px] text-warning block mt-2">Candidate Spec</span>
              </div>
              <div className="bg-surface-elevated p-3 rounded-lg border border-border">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-text-primary">Treynor Ratio</span>
                  <span className="text-[10px] text-text-muted">RAT-03</span>
                </div>
                <span className="text-[11px] text-text-secondary font-sans block mt-1">Excess Return per Systematic Beta</span>
                <span className="text-[10px] text-success block mt-2">Approved Standard</span>
              </div>
              <div className="bg-surface-elevated p-3 rounded-lg border border-border">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-text-primary">Information Ratio</span>
                  <span className="text-[10px] text-text-muted">RAT-04</span>
                </div>
                <span className="text-[11px] text-text-secondary font-sans block mt-1">Active Alpha per Tracking Error</span>
                <span className="text-[10px] text-warning block mt-2">Candidate Spec</span>
              </div>
            </div>
          </details>

          {/* Dimension 4 */}
          <details className="group rounded-xl border border-border bg-card p-5 shadow-xs transition open:pb-6">
            <summary className="flex items-center justify-between cursor-pointer font-mono text-xs list-none">
              <div className="flex items-center gap-2">
                <span className="text-accent font-bold uppercase">4. Market Sensitivity &amp; Capture (5 Metrics)</span>
                <span className="text-[10px] text-text-muted bg-surface-elevated px-2 py-0.5 rounded border border-border">
                  Beta &bull; Downside Beta &bull; Capture Ratios
                </span>
              </div>
              <span className="text-text-muted group-open:rotate-180 transition-transform">▼</span>
            </summary>
            <div className="mt-4 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3 font-mono text-xs">
              <div className="bg-surface-elevated p-3 rounded-lg border border-border">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-text-primary">Portfolio Beta</span>
                  <span className="text-[10px] text-text-muted">MKT-01</span>
                </div>
                <span className="text-[11px] text-text-secondary font-sans block mt-1">OLS Regression Slope vs NIFTY 50</span>
                <span className="text-[10px] text-success block mt-2">Approved Standard</span>
              </div>
              <div className="bg-surface-elevated p-3 rounded-lg border border-border">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-text-primary">Downside Beta</span>
                  <span className="text-[10px] text-text-muted">MKT-02</span>
                </div>
                <span className="text-[11px] text-text-secondary font-sans block mt-1">Sensitivity When Market Declines</span>
                <span className="text-[10px] text-success block mt-2">Approved Standard</span>
              </div>
              <div className="bg-surface-elevated p-3 rounded-lg border border-border">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-text-primary">Upside Capture</span>
                  <span className="text-[10px] text-text-muted">MKT-03</span>
                </div>
                <span className="text-[11px] text-text-secondary font-sans block mt-1">Benchmark Gain Participation</span>
                <span className="text-[10px] text-warning block mt-2">Candidate Spec</span>
              </div>
              <div className="bg-surface-elevated p-3 rounded-lg border border-border">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-text-primary">Downside Capture</span>
                  <span className="text-[10px] text-text-muted">MKT-04</span>
                </div>
                <span className="text-[11px] text-text-secondary font-sans block mt-1">Benchmark Loss Participation</span>
                <span className="text-[10px] text-warning block mt-2">Candidate Spec</span>
              </div>
              <div className="bg-surface-elevated p-3 rounded-lg border border-border">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-text-primary">Capture Spread</span>
                  <span className="text-[10px] text-text-muted">MKT-05</span>
                </div>
                <span className="text-[11px] text-text-secondary font-sans block mt-1">Upside Ratio Minus Downside Ratio</span>
                <span className="text-[10px] text-warning block mt-2">Candidate Spec</span>
              </div>
            </div>
          </details>

          {/* Dimension 5 */}
          <details className="group rounded-xl border border-border bg-card p-5 shadow-xs transition open:pb-6">
            <summary className="flex items-center justify-between cursor-pointer font-mono text-xs list-none">
              <div className="flex items-center gap-2">
                <span className="text-accent font-bold uppercase">5. Relative Benchmark &amp; Alpha (2 Metrics)</span>
                <span className="text-[10px] text-text-muted bg-surface-elevated px-2 py-0.5 rounded border border-border">
                  Tracking Error &bull; Jensen&apos;s Alpha
                </span>
              </div>
              <span className="text-text-muted group-open:rotate-180 transition-transform">▼</span>
            </summary>
            <div className="mt-4 grid grid-cols-1 sm:grid-cols-2 gap-3 font-mono text-xs">
              <div className="bg-surface-elevated p-3 rounded-lg border border-border">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-text-primary">Tracking Error</span>
                  <span className="text-[10px] text-text-muted">REL-01</span>
                </div>
                <span className="text-[11px] text-text-secondary font-sans block mt-1">Annualized Standard Deviation of Excess Returns</span>
                <span className="text-[10px] text-warning block mt-2">Candidate Spec</span>
              </div>
              <div className="bg-surface-elevated p-3 rounded-lg border border-border">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-text-primary">Jensen&apos;s Alpha</span>
                  <span className="text-[10px] text-text-muted">REL-02</span>
                </div>
                <span className="text-[11px] text-text-secondary font-sans block mt-1">Unexplained Excess Intercept Above CAPM Expectation</span>
                <span className="text-[10px] text-warning block mt-2">Candidate Spec</span>
              </div>
            </div>
          </details>

          {/* Dimension 6 & 7 */}
          <details className="group rounded-xl border border-border bg-card p-5 shadow-xs transition open:pb-6">
            <summary className="flex items-center justify-between cursor-pointer font-mono text-xs list-none">
              <div className="flex items-center gap-2">
                <span className="text-text-muted font-bold uppercase">6 &amp; 7. Portfolio Structure &amp; Governance (6 Metrics)</span>
                <span className="text-[10px] text-text-muted bg-surface-elevated px-2 py-0.5 rounded border border-border">
                  Concentration &bull; Turnover &bull; Expense Ratio
                </span>
              </div>
              <span className="text-text-muted group-open:rotate-180 transition-transform">▼</span>
            </summary>
            <div className="mt-4 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 font-mono text-xs">
              <div className="bg-surface-elevated p-3 rounded-lg border border-border">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-text-primary">Top-10 Concentration</span>
                  <span className="text-[10px] text-text-muted">PRT-01</span>
                </div>
                <span className="text-[11px] text-text-secondary font-sans block mt-1">Weight Sum of Top 10 Holdings</span>
                <span className="text-[10px] text-text-muted block mt-2">Awaiting Holdings Feed</span>
              </div>
              <div className="bg-surface-elevated p-3 rounded-lg border border-border">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-text-primary">Active Share</span>
                  <span className="text-[10px] text-text-muted">PRT-03</span>
                </div>
                <span className="text-[11px] text-text-secondary font-sans block mt-1">Portfolio Weight Divergence from Benchmark</span>
                <span className="text-[10px] text-text-muted block mt-2">Awaiting Holdings Feed</span>
              </div>
              <div className="bg-surface-elevated p-3 rounded-lg border border-border">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-text-primary">Direct Plan TER</span>
                  <span className="text-[10px] text-text-muted">GOV-01</span>
                </div>
                <span className="text-[11px] text-text-secondary font-sans block mt-1">Total Expense Ratio for Direct Plan</span>
                <span className="text-[10px] text-warning block mt-2">Candidate Spec</span>
              </div>
            </div>
          </details>
        </div>
      </section>

      {/* Navigation Links */}
      <div className="flex items-center justify-between border-t border-border pt-6 font-mono text-xs">
        <Link
          href="/"
          className="text-text-muted hover:text-text-primary transition"
        >
          &larr; Return to Overview
        </Link>
        <Link
          href="/funds"
          className="inline-flex items-center gap-1.5 rounded-lg bg-accent px-4 py-2 text-accent-foreground font-semibold hover:opacity-90 transition shadow-xs"
        >
          Explore Fund Catalog &rarr;
        </Link>
      </div>
    </PageContainer>
  );
}
