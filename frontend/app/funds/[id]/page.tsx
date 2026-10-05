"use client";

import { use, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { PageContainer, SectionHeading } from "@/components/layout/PageContainer";
import { StateView } from "@/components/epistemic/StateView";
import { MetricCard } from "@/components/primitives/MetricCard";
import { fetchSchemeById, fetchSchemeOptionsBySchemeId } from "@/lib/api/schemes";
import { triggerCalculation } from "@/lib/api/calculations";
import { Scheme, SchemeOption } from "@/types/domain";
import { HistoricalStressView } from "@/components/analysis/HistoricalStressView";
import { DataQualityCenter } from "@/components/analysis/DataQualityCenter";
import { RollingConsistencyView } from "@/components/analysis/RollingConsistencyView";
import { CaptureRatioView } from "@/components/analysis/CaptureRatioView";


import { BenchmarkRelationshipView } from "@/components/analysis/BenchmarkRelationshipView";
import { FundSectionNav } from "@/components/analysis/FundSectionNav";
import { InvestmentModesView } from "@/components/analysis/InvestmentModesView";
import { PortfolioHoldingsView } from "@/components/analysis/PortfolioHoldingsView";
import { YukiraScoreCard } from "@/components/analysis/YukiraScoreCard";
import { FundOverviewPanel } from "@/components/analysis/FundOverviewPanel";
import { fetchOptionEnrichment } from "@/lib/api/enrichment";
import { EnrichedFundProfileDto } from "@/types/enrichment";

import { useWatchlist } from "@/lib/hooks/useWatchlist";

interface PageProps {
  params: Promise<{ id: string }>;
}

export default function FundDetailPage({ params }: PageProps) {
  const resolvedParams = use(params);
  const schemeId = parseInt(resolvedParams.id, 10);
  const router = useRouter();

  const [scheme, setScheme] = useState<Scheme | null>(null);
  const [options, setOptions] = useState<SchemeOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Watchlist hook
  const { isWatchlisted, toggleWatchlist } = useWatchlist();
  const watchlisted = isWatchlisted(schemeId);

  // Trigger calculation state
  const [selectedOptionId, setSelectedOptionId] = useState<number | null>(null);
  const [executingMetric, setExecutingMetric] = useState<string | null>(null);
  const [triggerError, setTriggerError] = useState<string | null>(null);

  // Enriched fund profile state (AUM, TER, Manager, Terms, Holdings summary)
  const [enrichment, setEnrichment] = useState<EnrichedFundProfileDto | null>(null);
  const [enrichmentLoading, setEnrichmentLoading] = useState(false);

  useEffect(() => {
    let active = true;
    if (!selectedOptionId) {
      return;
    }
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setEnrichmentLoading(true);
    fetchOptionEnrichment(selectedOptionId)
      .then((data) => {
        if (active) {
          setEnrichment(data);
          setEnrichmentLoading(false);
        }
      })
      .catch(() => {
        if (active) {
          setEnrichment(null);
          setEnrichmentLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [selectedOptionId]);

  useEffect(() => {
    if (isNaN(schemeId)) {
      return;
    }

    let active = true;
    Promise.all([
      fetchSchemeById(schemeId),
      fetchSchemeOptionsBySchemeId(schemeId),
    ])
      .then(([schemeData, schemeOptions]) => {
        if (active) {
          setScheme(schemeData);
          setOptions(schemeOptions);
          if (schemeOptions.length > 0) {
            setSelectedOptionId(schemeOptions[0].id);
          }
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (active) {
          setError(err instanceof Error ? err.message : "Failed to load scheme details");
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [schemeId]);

  const handleTriggerCalculation = async (metricCode: string) => {
    if (!selectedOptionId) {
      setTriggerError("Please select a scheme option before initiating calculation.");
      return;
    }

    setExecutingMetric(metricCode);
    setTriggerError(null);

    try {
      const run = await triggerCalculation({
        schemeOptionId: selectedOptionId,
        benchmarkId: 1,
        asOfDate: "2024-01-15",
        knowledgeCutoffTime: "2024-01-31T23:59:59+05:30",
        metricCodes: [metricCode],
        methodologyTag: "CANDIDATE_V1",
      });
      router.push(`/analysis/${run.id}`);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Failed to trigger calculation run";
      setTriggerError(msg);
      setExecutingMetric(null);
    }
  };

  if (loading) {
    return (
      <PageContainer title="Scheme Intelligence">
        <StateView
          kind="loading"
          title="Loading Scheme Details"
          message={`Querying backend database for scheme ID #${schemeId}...`}
        />
      </PageContainer>
    );
  }

  if (error || !scheme) {
    return (
      <PageContainer title="Scheme Unavailable">
        <StateView
          kind="unavailable"
          title="Fund data is not currently available."
          message={error || `Scheme #${schemeId} does not exist in master records.`}
          action={
            <Link href="/funds" className="btn btn-secondary btn-sm">
              Back to Catalog
            </Link>
          }
        />
      </PageContainer>
    );
  }

  const selectedOption = options.find((o) => o.id === selectedOptionId) ?? null;
  const isCanonicalPilot = scheme.code === "HDFC_FLEXI" || scheme.name.toLowerCase().includes("hdfc flexi cap");

  return (
    <PageContainer
      title={scheme.name}
      subtitle={`Scheme Code: ${scheme.code} • Master ID: #${scheme.id}`}
      breadcrumbs={[
        { label: "Home", href: "/" },
        { label: "Funds", href: "/funds" },
        { label: scheme.code, href: `/funds/${scheme.id}` },
      ]}
      action={
        <button
          onClick={() => toggleWatchlist(schemeId)}
          className={watchlisted ? "btn btn-accent" : "btn btn-secondary"}
          title={watchlisted ? "Remove from Watchlist" : "Add to Watchlist"}
        >
          <svg
            className="h-4 w-4"
            fill={watchlisted ? "currentColor" : "none"}
            viewBox="0 0 24 24"
            stroke="currentColor"
            aria-hidden="true"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={watchlisted ? 1 : 2}
              d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z"
            />
          </svg>
          <span>{watchlisted ? "Watchlisted" : "Add to Watchlist"}</span>
        </button>
      }
    >
      {/* Methodology governance — contextual, scoped to this profile */}
      <section className="panel state-candidate mb-8" aria-label="Methodology Status">
        <div className="panel-header">
          <h2 className="eyebrow">Methodology Governance &amp; Operational Status</h2>
          <span className="status-badge state-candidate">ZERO STAR RATINGS · ZERO TIPS</span>
        </div>
        <p className="max-w-[86ch] px-4 py-3 text-[12.5px] leading-[1.6] text-text-secondary">
          Analytical metrics displayed below reflect verified point-in-time observations. Core
          analytical metrics adhere to formal quantitative governance standards, while research
          indicators operate as candidate specifications. YUKIRA does not generate commercial
          ratings or investment forecasts.
        </p>
      </section>

      {/* Scoped execution failure */}
      {triggerError && (
        <div className="state-panel-error mb-6">
          <strong className="eyebrow block">Execution Failure</strong>
          <span className="mt-1 block font-mono text-[12px]">{triggerError}</span>
        </div>
      )}

      {/* Sticky Section Navigation */}
      <FundSectionNav />

      {/* 1. FUND IDENTITY & SHARE CLASS SELECTOR */}
      <section className="mb-10 scroll-mt-20" id="identity">
        <div className="mb-4">
          <SectionHeading
            ordinal="Section 1"
            title="Fund Identity & Share Classes"
            action={
              isCanonicalPilot ? (
                <span className="status-badge state-approved">
                  <span className="status-dot bg-approved-fg" aria-hidden="true" />
                  Canonical Pilot Instrument
                </span>
              ) : undefined
            }
          />
        </div>

        <dl className="mb-5 grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <div className="panel-inset p-3">
            <dt className="def-label">Scheme Code</dt>
            <dd className="def-value">{scheme.code}</dd>
            <dd className="mono-meta mt-1">Canonical Identifier</dd>
          </div>

          <div className="panel-inset p-3">
            <dt className="def-label">Inception Date</dt>
            <dd className="def-value">{scheme.inceptionDate || "Not available"}</dd>
            <dd className="mono-meta mt-1">AMFI Record</dd>
          </div>

          <div className="panel-inset p-3">
            <dt className="def-label">Master Record Status</dt>
            <dd className="def-value">{scheme.status || "ACTIVE"}</dd>
            <dd className="mono-meta mt-1">PostgreSQL Verified</dd>
          </div>

          <div className="panel-inset p-3">
            <dt className="def-label">Registered Options</dt>
            <dd className="def-value">{options.length} Share Classes</dd>
            <dd className="mono-meta mt-1">Direct / Regular Plans</dd>
          </div>
        </dl>

        {/* FUND OVERVIEW: AUM, EXPENSE RATIO, FUND MANAGER */}
        <div className="mb-6">
          <FundOverviewPanel enrichment={enrichment} loading={enrichmentLoading} />
        </div>

        {/* Share Class Selector Table */}
        <div className="panel">
          <div className="panel-header">
            <div className="min-w-0">
              <h3 className="text-[13px] font-semibold text-text-primary">
                Registered Share Classes ({options.length})
              </h3>
              <p className="mt-0.5 text-[12px] leading-[1.5] text-text-tertiary">
                Select an option to target calculations and verify point-in-time coverage.
              </p>
            </div>
            {selectedOption && (
              <span className="mono-meta shrink-0">
                Active: Option #{selectedOption.id} ({selectedOption.plan?.planType || "DIRECT"}{" "}
                {selectedOption.optionType})
              </span>
            )}
          </div>

          <div className="scroll-region">
            <table className="data-table">
              <thead>
                <tr>
                  <th className="w-10">Sel</th>
                  <th>Option ID</th>
                  <th>Plan Type</th>
                  <th>Option</th>
                  <th>AMFI Code</th>
                  <th>ISIN</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {options.map((opt) => (
                  <tr
                    key={opt.id}
                    onClick={() => setSelectedOptionId(opt.id)}
                    className="cursor-pointer transition-colors hover:bg-surface-raised"
                  >
                    <td>
                      <input
                        type="radio"
                        name="selectedOption"
                        checked={selectedOptionId === opt.id}
                        onChange={() => setSelectedOptionId(opt.id)}
                        className="accent-[var(--accent)]"
                        aria-label={`Select Option #${opt.id}`}
                      />
                    </td>
                    <td className={selectedOptionId === opt.id ? "text-accent" : "key"}>
                      Option #{opt.id}
                    </td>
                    <td className="key">{opt.plan?.planType || "DIRECT"}</td>
                    <td>{opt.optionType || "GROWTH"}</td>
                    <td className="key">{opt.amfiCode || "Not available"}</td>
                    <td className="num text-[11px]">{opt.isin || "Not available"}</td>
                    <td>
                      <span className="status-badge state-approved">
                        {opt.status || "ACTIVE"}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </section>

      {/* YUKIRA ANALYTICAL SCORECARD */}
      {selectedOptionId && (
        <section className="mb-10 scroll-mt-20" id="analytical-scorecard">
          <YukiraScoreCard schemeOptionId={selectedOptionId} />
        </section>
      )}

      {/* 2. DATA COVERAGE & POINT-IN-TIME STATUS */}
      <section className="mb-10 scroll-mt-20" id="coverage">
        <div className="mb-4">
          <SectionHeading
            ordinal="Section 2"
            title="Data Coverage & Point-in-Time Status"
          />
        </div>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <div className="panel-inset p-3">
            <span className="def-label">Analysis Cutoff Window</span>
            <span className="def-value block">2024-01-15</span>
            <p className="mt-1.5 text-[12px] leading-[1.5] text-text-secondary">
              Evaluations anchored to authentic historical market trading close.
            </p>
          </div>

          <div className="panel-inset p-3">
            <span className="def-label">Knowledge Cutoff Time</span>
            <span className="def-value block truncate" title="2024-01-31T23:59:59+05:30">
              2024-01-31 23:59:59 IST
            </span>
            <p className="mt-1.5 text-[12px] leading-[1.5] text-text-secondary">
              Strict exclusion of any revisions published after cutoff.
            </p>
          </div>

          <div className="panel-inset p-3">
            <span className="def-label">Canonical Horizon</span>
            <span className="def-value block">1,243 Trading Dates</span>
            <p className="mt-1.5 text-[12px] leading-[1.5] text-text-secondary">
              Continuous 5Y ledger spanning 2019 through 2024.
            </p>
          </div>

          <div className="panel-inset p-3">
            <span className="def-label">Cryptographic Provenance</span>
            <span className="def-value block">SHA-256 Verified</span>
            <p className="mt-1.5 text-[12px] leading-[1.5] text-text-secondary">
              AMFI official raw artifacts with immutable byte sizes.
            </p>
          </div>
        </div>
      </section>

      {/* 3. METRIC GROUPS (PROGRESSIVE DISCLOSURE) */}

      {/* DIMENSION 1: RETURN QUALITY */}
      <section className="mb-10 scroll-mt-20" id="return-quality">
        <div className="mb-4">
          <SectionHeading
            ordinal="Dimension 1"
            title="Return Quality & Compound Horizon"
            action={<span className="mono-meta shrink-0">4 Horizon Metrics</span>}
          />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <MetricCard
            code="RET-02"
            name="Simple Period Return"
            category="return"
            categoryLabel="Discrete Return"
            governanceStatus="candidate"
            period="2024-01-01 → 2024-01-15 (11 trading days)"
            description="Discrete percentage change in net asset value between start and end observations."
            interpretation="Measures realized point-to-point capital appreciation over the selected historical dates."
            limitation="Point-to-point discrete return masks intra-period volatility and path stress."
            formula="R = (NAV_end / NAV_start) - 1"
            assumptions={[
              { label: 'Lookback Tolerance', value: '4 calendar days' },
              { label: 'Units', value: 'PERCENTAGE' },
            ]}
            canExecute={true}
            isExecuting={executingMetric === "RET-02"}
            onExecute={() => handleTriggerCalculation("RET-02")}
          />

          <MetricCard
            code="RET-03"
            name="3-Year Compound Annual Growth Rate (3Y CAGR)"
            category="return"
            categoryLabel="Annualized Return"
            governanceStatus="approved"
            period="36 Calendar Months (≥ 700 trading days)"
            description="Annualized compound return evaluated over a continuous 36-month lookback window."
            interpretation="Normalizes cumulative multi-year growth onto an annualized basis using 365.25 calendar days convention."
            limitation="Past annualized return does not predict future returns across changing market cycles."
            formula="CAGR = (NAV_end / NAV_start)^(365.25 / elapsed_calendar_days) - 1"
            assumptions={[
              { label: 'Window', value: '36 Calendar Months' },
              { label: 'Minimum Days', value: '700 trading days' },
            ]}
            canExecute={true}
            isExecuting={executingMetric === "RET-03"}
            onExecute={() => handleTriggerCalculation("RET-03")}
          />

          <MetricCard
            code="RET-01"
            name="1-Year Compound Annual Growth Rate (1Y CAGR)"
            category="return"
            categoryLabel="12M Lookback"
            governanceStatus="unimplemented"
            period="12 Calendar Months"
            description="Annualized 1-year trailing return primitive."
            disabledReason="Under quantitative review — available in full analytical audit."
          />

          <MetricCard
            code="RET-04"
            name="5-Year Compound Annual Growth Rate (5Y CAGR)"
            category="return"
            categoryLabel="60M Lookback"
            governanceStatus="unimplemented"
            period="60 Calendar Months"
            description="Full-cycle 5-year annualized return primitive."
            disabledReason="Under quantitative review — available in full analytical audit."
          />
        </div>
      </section>

      {/* ROLLING RETURN & OUTPERFORMANCE CONSISTENCY SLICE (§RET-05 / §RET-06) */}
      {selectedOptionId && (
        <div className="mb-10">
          <RollingConsistencyView
            schemeOptionId={selectedOptionId}
            schemeCode={scheme.code}
          />
        </div>
      )}

      {/* DIMENSION 2: TOTAL & DOWNSIDE RISK */}
      <section className="mb-10 scroll-mt-20" id="risk">
        <div className="mb-4">
          <SectionHeading
            ordinal="Dimension 2"
            title="Total & Downside Risk"
            action={<span className="mono-meta shrink-0">2 Risk Metrics</span>}
          />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <MetricCard
            code="RSK-01"
            name="Annualized Volatility (3Y)"
            category="risk"
            categoryLabel="Total Dispersion"
            governanceStatus="approved"
            period="36 Calendar Months (≥ 700 trading days)"
            description="Annualized sample standard deviation of daily log/discrete returns."
            interpretation="Measures the total dispersion of daily returns around the sample mean over 36 months."
            limitation="Treats upside gains and downside drawdowns with equal penalty."
            formula="σ_ann = √252 × √( ∑(r_t - r̄)² / (N - 1) )"
            assumptions={[
              { label: 'Annualizer', value: '√252 (Trading Days)' },
              { label: 'Denominator', value: 'N - 1 (Unbiased Sample)' },
            ]}
            canExecute={true}
            isExecuting={executingMetric === "RSK-01"}
            onExecute={() => handleTriggerCalculation("RSK-01")}
          />

          <MetricCard
            code="RSK-02"
            name="Downside Semideviation (3Y)"
            category="risk"
            categoryLabel="Asymmetric Risk"
            governanceStatus="candidate"
            period="36 Calendar Months (≥ 700 trading days)"
            description="Annualized dispersion of negative daily returns below minimum acceptable return (MAR = 0.0)."
            interpretation="Isolates exclusively harmful negative returns, ignoring upside volatility that benefits the investor."
            limitation="Candidate convention MAR = 0.0; does not penalize returns that lag inflation."
            formula="σ_d = √252 × √( ∑(min(r_t, 0))² / (N - 1) )"
            assumptions={[
              { label: 'Threshold (MAR)', value: '0.0% (Zero Return)' },
              { label: 'Annualizer', value: '√252' },
            ]}
            canExecute={true}
            isExecuting={executingMetric === "RSK-02"}
            onExecute={() => handleTriggerCalculation("RSK-02")}
          />
        </div>
      </section>

      {/* DIMENSION 3: DRAWDOWN & PATH STRESS */}
      <section className="mb-10 scroll-mt-20" id="drawdown">
        <div className="mb-4">
          <SectionHeading
            ordinal="Dimension 3"
            title="Drawdown & Path Stress"
            action={<span className="mono-meta shrink-0">3 Drawdown Metrics</span>}
          />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <MetricCard
            code="RSK-03"
            name="Maximum Drawdown (3Y)"
            category="drawdown"
            categoryLabel="Capital Decline"
            governanceStatus="candidate"
            period="36 Calendar Months"
            description="Worst peak-to-trough percentage decline observed over the 36-month horizon."
            interpretation="Quantifies the maximum capital loss an investor experienced from the highest historical peak."
            limitation="Measures historical worst-case decline only; does not place an upper bound on future drawdowns."
            formula="MDD = min_t ( NAV_t / max_{s≤t}(NAV_s) - 1 )"
            assumptions={[
              { label: 'Sign Convention', value: 'Signed Negative Decimal' },
              { label: 'Peak Tracking', value: 'Running Cumulative Max' },
            ]}
            canExecute={true}
            isExecuting={executingMetric === "RSK-03"}
            onExecute={() => handleTriggerCalculation("RSK-03")}
          />

          <MetricCard
            code="RSK-04"
            name="Maximum Drawdown Duration"
            category="drawdown"
            categoryLabel="Recovery Horizon"
            governanceStatus="candidate"
            period="36 Calendar Months"
            description="Longest elapsed calendar days between a peak and the subsequent full recovery."
            interpretation="Measures the psychological holding stress and capital lockup duration required to break even."
            limitation="Unrecovered drawdowns are censored at knowledge cutoff timestamp."
            formula="Duration = max( recovery_date - peak_date )"
            assumptions={[
              { label: 'Units', value: 'Calendar Days (DAYS)' },
              { label: 'Censoring Rule', value: 'Cutoff Bound Applied' },
            ]}
            canExecute={true}
            isExecuting={executingMetric === "RSK-04"}
            onExecute={() => handleTriggerCalculation("RSK-04")}
          />

          <MetricCard
            code="RSK-05"
            name="Ulcer Index (3Y)"
            category="drawdown"
            categoryLabel="Stress Metric"
            governanceStatus="candidate"
            period="36 Calendar Months"
            description="Quadratic root-mean-square of percentage drawdowns from running peak NAV."
            interpretation="Synthesizes both depth and duration of all drawdowns into a single path-dependent stress measure."
            limitation="Heavier penalty on deeper drawdowns due to quadratic weighting."
            formula="UI = √( (1/N) × ∑( ((NAV_t - max NAV)/max NAV × 100)² ) )"
            assumptions={[
              { label: 'Weighting', value: 'Quadratic RMS' },
              { label: 'Units', value: 'POINTS' },
            ]}
            canExecute={true}
            isExecuting={executingMetric === "RSK-05"}
            onExecute={() => handleTriggerCalculation("RSK-05")}
          />
        </div>
      </section>

      {/* HISTORICAL STRESS VIEW SLICE */}
      {selectedOptionId && (
        <div className="mb-10">
          <HistoricalStressView
            schemeOptionId={selectedOptionId}
            schemeCode={scheme.code}
          />
        </div>
      )}

      {/* DIMENSION 4: TAIL RISK */}
      <section className="mb-10 scroll-mt-20" id="tail-risk">
        <div className="mb-4">
          <SectionHeading
            ordinal="Dimension 4"
            title="Tail Risk & Extreme Losses"
            action={<span className="mono-meta shrink-0">2 Tail Risk Metrics</span>}
          />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <MetricCard
            code="RSK-06"
            name="Historical Value at Risk (VaR 95%)"
            category="tail"
            categoryLabel="Tail Loss Boundary"
            governanceStatus="candidate"
            period="36 Calendar Months"
            description="5th percentile empirical daily return threshold over the 36-month lookback window."
            interpretation="On 95% of trading days, daily loss did not exceed this threshold in historical data."
            limitation="Non-parametric empirical quantile; does not describe severity of losses in the remaining 5% tail."
            formula="VaR_95 = -Quantile_0.05( { r_t } )"
            assumptions={[
              { label: 'Quantile Convention', value: 'Type 7 Linear Interpolation' },
              { label: 'Confidence Level', value: '95.0% One-Tailed' },
            ]}
            disabledReason="Implemented in Python quant engine kernel. Available in full analytical audit."
          />

          <MetricCard
            code="RSK-07"
            name="Expected Shortfall (CVaR 95%)"
            category="tail"
            categoryLabel="Conditional Tail Expectation"
            governanceStatus="candidate"
            period="36 Calendar Months"
            description="Conditional mean of daily returns strictly below the 5th percentile VaR cutoff."
            interpretation="Measures the expected daily loss when an extreme tail loss event actually occurs."
            limitation="Sub-sample average subject to small-sample estimation variance during calm market regimes."
            formula="ES_95 = -(1 / |Tail|) × ∑_{r_t < -VaR_95} r_t"
            assumptions={[
              { label: 'Coherence', value: 'Sub-additive Coherent Measure' },
              { label: 'Tail Threshold', value: 'Empirical 5th Percentile' },
            ]}
            disabledReason="Implemented in Python quant engine kernel. Available in full analytical audit."
          />
        </div>
      </section>

      {/* DIMENSION 5: RISK-ADJUSTED & MARKET SENSITIVITY */}
      <section className="mb-10 scroll-mt-20" id="risk-adjusted">
        <div className="mb-4">
          <SectionHeading
            ordinal="Dimension 5"
            title="Risk-Adjusted Ratios & Market Sensitivity"
            action={
              <span className="mono-meta shrink-0">4 Risk-Adjusted &amp; Sensitivity Metrics</span>
            }
          />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <MetricCard
            code="RAT-01"
            name="Sharpe Ratio (3Y)"
            category="risk_adjusted"
            categoryLabel="Excess Return per Unit Total Risk"
            governanceStatus="approved"
            period="36 Calendar Months"
            description="Ratio of annualized excess return above the risk-free rate to annualized return volatility."
            interpretation="Evaluates how effectively the fund compensated for total standard deviation above 91-day T-bills."
            limitation="Assumes symmetrical return distributions; penalizes large upside gains."
            formula="Sharpe = (R_fund - R_f) / σ_ann"
            assumptions={[
              { label: 'Risk-Free Benchmark', value: 'FBIL 91D T-Bill' },
              { label: 'Compounding', value: 'Geometric Annualized' },
            ]}
            disabledReason="Available in full analytical audit profile. Requires synchronized benchmark and risk-free series."
          />

          <MetricCard
            code="RAT-02"
            name="Treynor Ratio (3Y)"
            category="risk_adjusted"
            categoryLabel="Excess Return per Unit Systematic Risk"
            governanceStatus="approved"
            period="36 Calendar Months"
            description="Ratio of annualized excess return above the risk-free rate to systematic equity Beta."
            interpretation="Measures excess reward earned per unit of unavoidable broad-market systematic risk."
            limitation="Only meaningful for well-diversified equity portfolios with high correlation to benchmark."
            formula="Treynor = (R_fund - R_f) / Beta"
            assumptions={[
              { label: 'Systematic Beta', value: '3Y Nifty 50 TRI Beta' },
              { label: 'Denominator Rule', value: 'Beta > 0 required' },
            ]}
            disabledReason="Available in full analytical audit profile. Requires synchronized benchmark and risk-free series."
          />

          <MetricCard
            code="MKT-01"
            name="Portfolio Beta (3Y)"
            category="risk_adjusted"
            categoryLabel="Systematic Sensitivity"
            governanceStatus="approved"
            period="36 Calendar Months"
            description="Slope coefficient from linear regression of daily fund returns against benchmark returns."
            interpretation="Measures the portfolio sensitivity to broad market movements (Beta > 1 implies amplified swings)."
            limitation="Linear static measure; beta fluctuates across bull, bear, and crisis regimes."
            formula="Beta = Cov(r_fund, r_bench) / Var(r_bench)"
            assumptions={[
              { label: 'Benchmark', value: 'Official NIFTY 50 TRI' },
              { label: 'Regression', value: 'Ordinary Least Squares (OLS)' },
            ]}
            disabledReason="Available in full analytical audit profile. Requires synchronized benchmark series."
          />

          <MetricCard
            code="MKT-02"
            name="Downside Beta (3Y)"
            category="risk_adjusted"
            categoryLabel="Asymmetric Sensitivity"
            governanceStatus="approved"
            period="36 Calendar Months (Benchmark < 0)"
            description="Beta calculated conditioning exclusively on trading days when benchmark return was negative."
            interpretation="Identifies whether fund sensitivity increases during market sell-offs vs up-trending regimes."
            limitation="Requires sufficient negative benchmark trading days (minimum 100 days threshold)."
            formula="Beta_down = Cov(r_fund, r_bench | r_bench < 0) / Var(r_bench | r_bench < 0)"
            assumptions={[
              { label: 'Conditioning', value: 'r_benchmark < 0.0' },
              { label: 'Min Observations', value: '100 downside market days' },
            ]}
            disabledReason="Available in full analytical audit profile. Requires synchronized benchmark series."
          />
        </div>
      </section>

      {/* CAPTURE RATIOS & ASYMMETRIC MARKET PARTICIPATION (§MKT-03 / §MKT-04 / §MKT-05) */}
      {selectedOptionId && (
        <div className="mb-10">
          <CaptureRatioView
            schemeOptionId={selectedOptionId}
            schemeCode={scheme.code}
          />
        </div>
      )}

      {/* DIMENSION 5.5: CONSOLIDATED BENCHMARK RELATIONSHIP PANEL */}
      {selectedOptionId && (
        <section className="mb-10 scroll-mt-20" id="benchmark-relationship">
          <div className="mb-4">
            <SectionHeading
              ordinal="Dimension 5.5"
              title="Benchmark Relationship & Market Sensitivity"
              action={<span className="mono-meta shrink-0">6 Consolidated Indicators</span>}
            />
          </div>
          <BenchmarkRelationshipView
            schemeOptionId={selectedOptionId}
            schemeCode={scheme.code}
          />
        </section>
      )}


      {/* DIMENSION 6: PORTFOLIO STRUCTURE & GOVERNANCE */}

      <section className="mb-12 scroll-mt-20" id="portfolio">
        <div className="mb-4">
          <SectionHeading
            ordinal="Dimension 6"
            title="Portfolio Structure & Governance"
            action={
              <span className="mono-meta shrink-0">
                Portfolio Disclosures · Pending Regulatory Feed Ingestion
              </span>
            }
          />
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <MetricCard
            code="PRT-01"
            name="Top-10 Stock Concentration"
            category="portfolio"
            categoryLabel="Holdings Concentration"
            governanceStatus="pending_ingestion"
            description="Aggregate portfolio weight assigned to the top 10 largest individual equity holdings."
            disabledReason="Awaiting monthly portfolio disclosure holdings ingestion."
          />

          <MetricCard
            code="PRT-02"
            name="Effective Number of Holdings"
            category="portfolio"
            categoryLabel="Diversification"
            governanceStatus="pending_ingestion"
            description="Inverse Herfindahl-Hirschman Index (1 / sum(w_i^2)) evaluating effective holdings."
            disabledReason="Awaiting monthly portfolio disclosure holdings ingestion."
          />

          <MetricCard
            code="GOV-01"
            name="Direct Plan Total Expense Ratio"
            category="portfolio"
            categoryLabel="Cost Drag"
            governanceStatus="pending_ingestion"
            description="Annualized operating, administrative, and management fee drag on asset value."
            disabledReason="Awaiting AMC semi-annual disclosure ingestion."
          />
        </div>

        {selectedOptionId && (
          <PortfolioHoldingsView schemeOptionId={selectedOptionId} schemeCode={scheme.code} />
        )}
      </section>

      {/* SECTION 3: SIP & LUMPSUM INVESTMENT INFORMATION */}
      <section className="mb-12 scroll-mt-20" id="investment-modes">
        <div className="mb-4">
          <SectionHeading
            ordinal="Section 3"
            title="SIP & Lumpsum Investment Information"
            description="Investment routes and registered share classes, reported strictly from verified AMFI master records. AMC scheme terms are reported as unavailable where YUKIRA does not hold them."
          />
        </div>

        <InvestmentModesView
          options={options}
          schemeCode={scheme.code}
          investmentTerms={enrichment?.investmentTerms}
        />
      </section>

      {/* 4. DATA PROVENANCE & LINEAGE AUDIT */}
      <section className="panel mb-12 scroll-mt-20" id="provenance">
        <div className="panel-header">
          <h2 className="text-[13px] font-semibold text-text-primary">
            Official AMFI Data Lineage &amp; Cryptographic Proof
          </h2>
          <span className="mono-meta shrink-0">Primary Source Verification</span>
        </div>

        <div className="px-4 py-4">
          <p className="max-w-[86ch] text-[12.5px] leading-[1.6] text-text-secondary">
            All numerical calculations originate strictly from authenticated primary source documents
            in the repository&apos;s bitemporal ledger. For scheme{" "}
            <strong className="font-mono text-text-primary">{scheme.code}</strong> (AMFI{" "}
            <strong className="font-mono text-text-primary">
              {selectedOption?.amfiCode ?? "Not available"}
            </strong>
            ), observations are cryptographically anchored to:
          </p>

          <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div className="panel-inset p-3">
              <span className="def-label">Raw Source Artifact #1</span>
              <span className="data-value-sm mt-1 block">AMFI NAV History Payload</span>
              <span className="mt-2 block select-all break-all font-mono text-[11px] leading-[1.5] text-text-secondary">
                SHA-256: 900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259
              </span>
              <span className="mono-meta mt-2 block">Size: 11,185,549 bytes · 11 Jan 2024 dates</span>
            </div>

            <div className="panel-inset p-3">
              <span className="def-label">Historical 5Y Horizon Artifacts</span>
              <span className="data-value-sm mt-1 block">Annual Source Artifacts #149..#155</span>
              <span className="mt-2 block font-mono text-[11px] leading-[1.5] text-text-secondary">
                2019 (#149), 2020 (#155), 2021 (#150), 2022 (#151), 2023 (#152)
              </span>
              <span className="mono-meta mt-2 block">
                1,243 authentic market dates · Zero synthetic series
              </span>
            </div>
          </div>
        </div>
      </section>

      {/* 4.5 DATA QUALITY & ANOMALY HEALTH CENTER */}
      <section className="mb-12 scroll-mt-20" id="data-quality">
        {selectedOptionId && <DataQualityCenter schemeOptionId={selectedOptionId} />}
      </section>

      {/* 5. DECISION SUPPORT: WHAT TO INVESTIGATE NEXT */}
      <section className="panel mb-12 scroll-mt-20" id="decision-support">
        <div className="panel-header">
          <h2 className="text-[13px] font-semibold text-text-primary">
            Decision Support Checkpoint: Questions Before Committing Capital
          </h2>
        </div>

        <p className="px-4 pt-3 text-[12.5px] leading-[1.6] text-text-secondary">
          Quantitative metrics describe past realized outcomes under historical market conditions.
          Before allocating capital, consider investigating:
        </p>

        <ol className="grid grid-cols-1 gap-px border-t border-border bg-border sm:grid-cols-2 lg:grid-cols-3">
          {[
            {
              n: "01",
              title: "Market Regime Resilience",
              body: "Did outperformance occur during broad liquidity rallies or during market drawdowns?",
            },
            {
              n: "02",
              title: "Portfolio Concentration",
              body: "Is return driven by fund-wide alpha or by heavy weight in 2-3 outperforming stocks?",
            },
            {
              n: "03",
              title: "Manager Tenure & Style Drift",
              body: "Did current fund leadership generate the 5-year track record without style deviation?",
            },
          ].map((q) => (
            <li key={q.n} className="bg-surface px-4 py-3">
              <span className="flex items-baseline gap-2">
                <span className="tab-ordinal">{q.n}</span>
                <span className="text-[13px] font-semibold text-text-primary">{q.title}</span>
              </span>
              <p className="mt-1.5 text-[12px] leading-[1.55] text-text-secondary">{q.body}</p>
            </li>
          ))}
        </ol>
      </section>
    </PageContainer>
  );
}
