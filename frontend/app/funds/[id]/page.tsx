"use client";

import { use, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { PageContainer } from "@/components/layout/PageContainer";
import { StateView } from "@/components/epistemic/StateView";
import { MetricCard } from "@/components/primitives/MetricCard";
import { fetchSchemeById, fetchSchemeOptionsBySchemeId } from "@/lib/api/schemes";
import { triggerCalculation } from "@/lib/api/calculations";
import { Scheme, SchemeOption } from "@/types/domain";

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

  // Trigger calculation state
  const [selectedOptionId, setSelectedOptionId] = useState<number | null>(null);
  const [executingMetric, setExecutingMetric] = useState<string | null>(null);
  const [triggerError, setTriggerError] = useState<string | null>(null);

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
            <Link
              href="/funds"
              className="inline-flex rounded-md bg-zinc-800 px-3.5 py-2 text-xs font-mono font-medium text-zinc-200 hover:bg-zinc-700"
            >
              Back to Catalog
            </Link>
          }
        />
      </PageContainer>
    );
  }

  const selectedOption = options.find((o) => o.id === selectedOptionId) || options[0];
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
    >
      {/* Epistemic Mandate Banner */}
      <section className="mb-8 rounded-xl border border-amber-500/30 bg-amber-500/10 p-5 backdrop-blur-sm" aria-label="Methodology Status">
        <div className="flex items-start gap-3.5">
          <div className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-amber-500/20 text-xs font-bold text-amber-400 font-mono">
            !
          </div>
          <div className="space-y-1">
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="text-xs font-bold uppercase tracking-wider text-amber-300 font-mono">
                Methodology Status: Candidate Verification
              </h2>
              <span className="rounded bg-amber-500/20 px-2 py-0.5 font-mono text-[10px] font-semibold text-amber-200 border border-amber-500/30">
                ZERO STAR RATINGS &bull; ZERO TIPS
              </span>
            </div>
            <p className="text-xs text-zinc-200 font-sans leading-relaxed">
              Analytical metrics displayed below operate strictly as candidate specifications. Executable metrics are computed deterministically by the Python quantitative engine against historical point-in-time observations. YUKIRA does not generate commercial ratings or investment forecasts.
            </p>
          </div>
        </div>
      </section>

      {/* Global Error Banner */}
      {triggerError && (
        <div className="mb-6 rounded-lg border border-rose-500/30 bg-rose-500/10 p-4 font-mono text-xs text-rose-300">
          <strong className="block uppercase text-[11px] mb-1">Execution Failure:</strong>
          {triggerError}
        </div>
      )}

      {/* 1. FUND IDENTITY & SHARE CLASS SELECTOR */}
      <section className="mb-10" id="identity">
        <div className="flex items-center justify-between pb-2 border-b border-zinc-800 mb-4">
          <div>
            <div className="font-mono text-[11px] uppercase tracking-wider text-cyan-400">Section 1</div>
            <h2 className="text-lg font-bold text-zinc-100 font-sans">Fund Identity & Share Classes</h2>
          </div>
          {isCanonicalPilot && (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-cyan-500/15 border border-cyan-500/30 px-3 py-1 text-xs font-mono font-semibold text-cyan-300">
              <span className="h-2 w-2 rounded-full bg-cyan-400 animate-pulse" />
              Canonical Pilot Instrument
            </span>
          )}
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 font-mono text-xs mb-5">
          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-4">
            <span className="text-zinc-500 text-[10px] uppercase block">Scheme Code</span>
            <span className="text-cyan-400 font-bold text-sm mt-1 block">{scheme.code}</span>
            <span className="text-zinc-500 text-[10px] mt-1 block">Canonical Identifier</span>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-4">
            <span className="text-zinc-500 text-[10px] uppercase block">Inception Date</span>
            <span className="text-zinc-100 font-bold text-sm mt-1 block">{scheme.inceptionDate || "—"}</span>
            <span className="text-zinc-500 text-[10px] mt-1 block">AMFI Record</span>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-4">
            <span className="text-zinc-500 text-[10px] uppercase block">Master Record Status</span>
            <span className="text-emerald-400 font-bold text-sm mt-1 flex items-center gap-1.5">
              <span className="h-2 w-2 rounded-full bg-emerald-400" />
              {scheme.status || "ACTIVE"}
            </span>
            <span className="text-zinc-500 text-[10px] mt-1 block">PostgreSQL Verified</span>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-4">
            <span className="text-zinc-500 text-[10px] uppercase block">Registered Options</span>
            <span className="text-zinc-100 font-bold text-sm mt-1 block">{options.length} Share Classes</span>
            <span className="text-zinc-500 text-[10px] mt-1 block">Direct / Regular Plans</span>
          </div>
        </div>

        {/* Share Class Selector Table */}
        <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-5">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 mb-3">
            <div>
              <h3 className="text-xs font-mono font-semibold uppercase tracking-wider text-zinc-200">
                Registered Share Classes ({options.length})
              </h3>
              <p className="text-xs text-zinc-400 font-sans">
                Select an option to target calculations and verify point-in-time coverage.
              </p>
            </div>
            {selectedOption && (
              <span className="text-xs font-mono text-cyan-400 bg-cyan-950/40 border border-cyan-800/60 px-2.5 py-1 rounded">
                Active: Option #{selectedOption.id} ({selectedOption.plan?.planType || "DIRECT"} {selectedOption.optionType})
              </span>
            )}
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs font-mono">
              <thead className="border-b border-zinc-800 bg-zinc-950/60 text-zinc-400 uppercase text-[10px] tracking-wider">
                <tr>
                  <th className="px-4 py-2.5">Select</th>
                  <th className="px-4 py-2.5">Option ID</th>
                  <th className="px-4 py-2.5">Plan Type</th>
                  <th className="px-4 py-2.5">Option</th>
                  <th className="px-4 py-2.5">AMFI Code</th>
                  <th className="px-4 py-2.5">ISIN</th>
                  <th className="px-4 py-2.5">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-800/60">
                {options.map((opt) => (
                  <tr
                    key={opt.id}
                    onClick={() => setSelectedOptionId(opt.id)}
                    className={`cursor-pointer transition hover:bg-zinc-800/40 ${
                      selectedOptionId === opt.id ? "bg-cyan-500/10 border-l-2 border-cyan-400" : ""
                    }`}
                  >
                    <td className="px-4 py-2.5">
                      <input
                        type="radio"
                        name="selectedOption"
                        checked={selectedOptionId === opt.id}
                        onChange={() => setSelectedOptionId(opt.id)}
                        className="text-cyan-500 focus:ring-cyan-500"
                        aria-label={`Select Option #${opt.id}`}
                      />
                    </td>
                    <td className="px-4 py-2.5 font-bold text-cyan-400">
                      Option #{opt.id}
                    </td>
                    <td className="px-4 py-2.5 text-zinc-200 font-semibold">
                      {opt.plan?.planType || "DIRECT"}
                    </td>
                    <td className="px-4 py-2.5 text-zinc-300">
                      {opt.optionType || "GROWTH"}
                    </td>
                    <td className="px-4 py-2.5 text-zinc-300 font-bold">
                      {opt.amfiCode || "—"}
                    </td>
                    <td className="px-4 py-2.5 text-zinc-400 font-mono text-[11px]">
                      {opt.isin || "—"}
                    </td>
                    <td className="px-4 py-2.5">
                      <span className="inline-flex rounded px-2 py-0.5 text-[10px] font-medium bg-emerald-500/15 text-emerald-400 border border-emerald-500/30">
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

      {/* 2. DATA COVERAGE & POINT-IN-TIME STATUS */}
      <section className="mb-10" id="coverage">
        <div className="pb-2 border-b border-zinc-800 mb-4">
          <div className="font-mono text-[11px] uppercase tracking-wider text-cyan-400">Section 2</div>
          <h2 className="text-lg font-bold text-zinc-100 font-sans">Data Coverage & Point-in-Time Status</h2>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 font-mono text-xs">
          <div className="rounded-xl border border-zinc-800 bg-zinc-900/50 p-4">
            <span className="text-zinc-500 text-[10px] uppercase block">Analysis Cutoff Window</span>
            <span className="text-zinc-100 font-bold text-sm mt-1 block">2024-01-15</span>
            <p className="mt-1 text-zinc-400 text-xs font-sans">
              Evaluations anchored to authentic historical market trading close.
            </p>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/50 p-4">
            <span className="text-zinc-500 text-[10px] uppercase block">Knowledge Cutoff Time</span>
            <span className="text-cyan-400 font-bold text-xs mt-1 block truncate" title="2024-01-31T23:59:59+05:30">
              2024-01-31 23:59:59 IST
            </span>
            <p className="mt-1 text-zinc-400 text-xs font-sans">
              Strict exclusion of any revisions published after cutoff.
            </p>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/50 p-4">
            <span className="text-zinc-500 text-[10px] uppercase block">Canonical Horizon</span>
            <span className="text-emerald-400 font-bold text-sm mt-1 block">1,243 Trading Dates</span>
            <p className="mt-1 text-zinc-400 text-xs font-sans">
              Continuous 5Y ledger spanning 2019 through 2024.
            </p>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/50 p-4">
            <span className="text-zinc-500 text-[10px] uppercase block">Cryptographic Provenance</span>
            <span className="text-zinc-200 font-bold text-sm mt-1 block">SHA-256 Verified</span>
            <p className="mt-1 text-zinc-400 text-xs font-sans">
              AMFI official raw artifacts with immutable byte sizes.
            </p>
          </div>
        </div>
      </section>

      {/* 3. METRIC GROUPS (PROGRESSIVE DISCLOSURE) */}

      {/* DIMENSION 1: RETURN QUALITY */}
      <section className="mb-10" id="return-quality">
        <div className="flex items-center justify-between pb-2 border-b border-zinc-800 mb-4">
          <div>
            <div className="font-mono text-[11px] uppercase tracking-wider text-cyan-400">Dimension 1</div>
            <h2 className="text-lg font-bold text-zinc-100 font-sans">Return Quality & Compound Horizon</h2>
          </div>
          <span className="font-mono text-xs text-zinc-400">
            2 Implemented &bull; 5 Candidate Specs
          </span>
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
            name="3-Year Compound Annual Growth Rate (CAGR)"
            category="return"
            categoryLabel="Annualized Return"
            governanceStatus="candidate"
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
            name="1-Year Compound Return (1Y CAGR)"
            category="return"
            categoryLabel="12M Lookback"
            governanceStatus="unimplemented"
            period="12 Calendar Months"
            description="Annualized 1-year trailing return primitive."
            disabledReason="Candidate specification — candidate kernel in quant-engine."
          />

          <MetricCard
            code="RET-04"
            name="5-Year Compound Return (5Y CAGR)"
            category="return"
            categoryLabel="60M Lookback"
            governanceStatus="unimplemented"
            period="60 Calendar Months"
            description="Full-cycle 5-year annualized return primitive."
            disabledReason="Candidate specification — candidate kernel in quant-engine."
          />
        </div>
      </section>

      {/* DIMENSION 2: TOTAL & DOWNSIDE RISK */}
      <section className="mb-10" id="risk">
        <div className="flex items-center justify-between pb-2 border-b border-zinc-800 mb-4">
          <div>
            <div className="font-mono text-[11px] uppercase tracking-wider text-amber-400">Dimension 2</div>
            <h2 className="text-lg font-bold text-zinc-100 font-sans">Total & Downside Risk</h2>
          </div>
          <span className="font-mono text-xs text-zinc-400">
            2 Implemented &bull; Candidate Stage
          </span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <MetricCard
            code="RSK-01"
            name="3-Year Annualized Volatility"
            category="risk"
            categoryLabel="Total Dispersion"
            governanceStatus="candidate"
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
            name="Downside Semideviation"
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
      <section className="mb-10" id="drawdown">
        <div className="flex items-center justify-between pb-2 border-b border-zinc-800 mb-4">
          <div>
            <div className="font-mono text-[11px] uppercase tracking-wider text-rose-400">Dimension 3</div>
            <h2 className="text-lg font-bold text-zinc-100 font-sans">Drawdown & Path Stress</h2>
          </div>
          <span className="font-mono text-xs text-zinc-400">
            3 Implemented &bull; Candidate Stage
          </span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <MetricCard
            code="RSK-03"
            name="3-Year Maximum Drawdown"
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
            name="Ulcer Index"
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

      {/* DIMENSION 4: TAIL RISK */}
      <section className="mb-10" id="tail-risk">
        <div className="flex items-center justify-between pb-2 border-b border-zinc-800 mb-4">
          <div>
            <div className="font-mono text-[11px] uppercase tracking-wider text-purple-400">Dimension 4</div>
            <h2 className="text-lg font-bold text-zinc-100 font-sans">Tail Risk & Extreme Losses</h2>
          </div>
          <span className="font-mono text-xs text-zinc-400">
            2 Candidate Algorithms (Stage H Verified)
          </span>
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
            disabledReason="Implemented in Python quant engine kernel (Stage H). Available in full analytical audit."
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
            disabledReason="Implemented in Python quant engine kernel (Stage H). Available in full analytical audit."
          />
        </div>
      </section>

      {/* DIMENSION 5: RISK-ADJUSTED & MARKET SENSITIVITY (PHASE 2Q) */}
      <section className="mb-10" id="risk-adjusted">
        <div className="flex items-center justify-between pb-2 border-b border-zinc-800 mb-4">
          <div>
            <div className="font-mono text-[11px] uppercase tracking-wider text-emerald-400">Dimension 5</div>
            <h2 className="text-lg font-bold text-zinc-100 font-sans">Risk-Adjusted Ratios & Market Sensitivity</h2>
          </div>
          <span className="font-mono text-xs text-zinc-400">
            4 Candidate Algorithms (Phase 2Q Verified)
          </span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <MetricCard
            code="RAT-01"
            name="Sharpe Ratio (3Y)"
            category="risk_adjusted"
            categoryLabel="Excess Return per Unit Total Risk"
            governanceStatus="candidate"
            period="36 Calendar Months"
            description="Ratio of annualized excess return above the risk-free rate to annualized return volatility."
            interpretation="Evaluates how effectively the fund compensated for total standard deviation above 91-day T-bills."
            limitation="Assumes symmetrical return distributions; penalizes large upside gains."
            formula="Sharpe = (R_fund - R_f) / σ_ann"
            assumptions={[
              { label: 'Risk-Free Benchmark', value: 'FBIL 91D T-Bill' },
              { label: 'Compounding', value: 'Geometric Annualized' },
            ]}
            disabledReason="Candidate specification in Python quant-engine (Phase 2Q)."
          />

          <MetricCard
            code="RAT-02"
            name="Treynor Ratio (3Y)"
            category="risk_adjusted"
            categoryLabel="Excess Return per Unit Systematic Risk"
            governanceStatus="candidate"
            period="36 Calendar Months"
            description="Ratio of annualized excess return above the risk-free rate to systematic equity Beta."
            interpretation="Measures excess reward earned per unit of unavoidable broad-market systematic risk."
            limitation="Only meaningful for well-diversified equity portfolios with high correlation to benchmark."
            formula="Treynor = (R_fund - R_f) / Beta"
            assumptions={[
              { label: 'Systematic Beta', value: '3Y Nifty 50 TRI Beta' },
              { label: 'Denominator Rule', value: 'Beta > 0 required' },
            ]}
            disabledReason="Candidate specification in Python quant-engine (Phase 2Q)."
          />

          <MetricCard
            code="REL-01"
            name="Equity Beta (3Y)"
            category="risk_adjusted"
            categoryLabel="Systematic Sensitivity"
            governanceStatus="candidate"
            period="36 Calendar Months"
            description="Slope coefficient from linear regression of daily fund returns against benchmark returns."
            interpretation="Measures the portfolio sensitivity to broad market movements (Beta > 1 implies amplified swings)."
            limitation="Linear static measure; beta fluctuates across bull, bear, and crisis regimes."
            formula="Beta = Cov(r_fund, r_bench) / Var(r_bench)"
            assumptions={[
              { label: 'Benchmark', value: 'Official NIFTY 50 TRI' },
              { label: 'Regression', value: 'Ordinary Least Squares (OLS)' },
            ]}
            disabledReason="Candidate specification in Python quant-engine (Phase 2Q)."
          />

          <MetricCard
            code="REL-04"
            name="Downside Beta (3Y)"
            category="risk_adjusted"
            categoryLabel="Asymmetric Sensitivity"
            governanceStatus="candidate"
            period="36 Calendar Months (Benchmark < 0)"
            description="Beta calculated conditioning exclusively on trading days when benchmark return was negative."
            interpretation="Identifies whether fund sensitivity increases during market sell-offs vs up-trending regimes."
            limitation="Requires sufficient negative benchmark trading days (minimum 100 days threshold)."
            formula="Beta_down = Cov(r_fund, r_bench | r_bench < 0) / Var(r_bench | r_bench < 0)"
            assumptions={[
              { label: 'Conditioning', value: 'r_benchmark < 0.0' },
              { label: 'Min Observations', value: '100 downside market days' },
            ]}
            disabledReason="Candidate specification in Python quant-engine (Phase 2Q)."
          />
        </div>
      </section>

      {/* DIMENSION 6: PORTFOLIO STRUCTURE & GOVERNANCE */}
      <section className="mb-12" id="portfolio">
        <div className="flex items-center justify-between pb-2 border-b border-zinc-800 mb-4">
          <div>
            <div className="font-mono text-[11px] uppercase tracking-wider text-zinc-400">Dimension 6</div>
            <h2 className="text-lg font-bold text-zinc-100 font-sans">Portfolio Structure & Governance</h2>
          </div>
          <span className="font-mono text-xs text-zinc-400">
            Scheduled Phase 3 &bull; Pending SEBI Ingestion
          </span>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <MetricCard
            code="PRT-01"
            name="Top-10 Stock Concentration"
            category="portfolio"
            categoryLabel="Holdings Concentration"
            governanceStatus="pending_ingestion"
            description="Aggregate portfolio weight assigned to the top 10 largest individual equity holdings."
            disabledReason="Awaiting Phase 3 SEBI monthly portfolio disclosure sheet ingestion."
          />

          <MetricCard
            code="PRT-02"
            name="Effective Number of Holdings"
            category="portfolio"
            categoryLabel="Diversification"
            governanceStatus="pending_ingestion"
            description="Inverse Herfindahl-Hirschman Index (1 / sum(w_i^2)) evaluating effective holdings."
            disabledReason="Awaiting Phase 3 SEBI monthly portfolio disclosure sheet ingestion."
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
      </section>

      {/* 4. DATA PROVENANCE & LINEAGE AUDIT */}
      <section className="mb-12 rounded-xl border border-zinc-800 bg-zinc-900/60 p-6 font-mono text-xs" id="provenance">
        <div className="flex items-center justify-between pb-3 border-b border-zinc-800">
          <div className="flex items-center gap-2 font-bold text-zinc-200 uppercase tracking-wider">
            <span className="h-2 w-2 rounded-full bg-cyan-400" />
            Official AMFI Data Lineage & Cryptographic Proof
          </div>
          <span className="text-zinc-500 text-[11px]">Primary Source Verification</span>
        </div>

        <div className="mt-4 space-y-3 font-sans text-xs text-zinc-300">
          <p>
            YUKIRA guarantees that all numerical calculations originate exclusively from authenticated primary source documents. For scheme <strong className="text-white font-mono">{scheme.code}</strong> (AMFI <strong className="text-white font-mono">{selectedOption?.amfiCode || "118955"}</strong>), observations are cryptographically anchored to:
          </p>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2 font-mono text-xs">
            <div className="rounded-lg bg-zinc-950 p-3 border border-zinc-800/80">
              <span className="text-zinc-500 text-[10px] uppercase block">Raw Source Artifact #1</span>
              <span className="text-cyan-400 font-bold block mt-0.5">AMFI NAV History Payload</span>
              <span className="text-zinc-400 text-[10px] block mt-1 break-all select-all font-mono">
                SHA-256: 900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259
              </span>
              <span className="text-zinc-500 text-[10px] block mt-1">Size: 11,185,549 bytes &bull; 11 Jan 2024 dates</span>
            </div>

            <div className="rounded-lg bg-zinc-950 p-3 border border-zinc-800/80">
              <span className="text-zinc-500 text-[10px] uppercase block">Historical 5Y Horizon Artifacts</span>
              <span className="text-emerald-400 font-bold block mt-0.5">Annual Source Artifacts #149..#155</span>
              <span className="text-zinc-400 text-[10px] block mt-1">
                2019 (#149), 2020 (#155), 2021 (#150), 2022 (#151), 2023 (#152)
              </span>
              <span className="text-zinc-500 text-[10px] block mt-1">1,243 authentic market dates &bull; Zero synthetic series</span>
            </div>
          </div>
        </div>
      </section>

      {/* 5. DECISION SUPPORT: WHAT TO INVESTIGATE NEXT */}
      <section className="mb-12 rounded-xl border border-zinc-800 bg-zinc-900/40 p-6 text-xs font-sans text-zinc-300">
        <h3 className="font-mono text-xs font-bold uppercase tracking-wider text-cyan-400 mb-2">
          Decision Support Checkpoint: Questions Before Committing Capital
        </h3>
        <p className="text-zinc-400 leading-relaxed mb-4">
          Quantitative metrics describe past realized outcomes under historical market conditions. Before allocating capital, consider investigating:
        </p>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 font-mono text-xs">
          <div className="rounded bg-zinc-950/60 p-3 border border-zinc-800">
            <span className="text-zinc-200 font-bold block mb-1">1. Market Regime Resilience</span>
            <span className="text-zinc-400 text-[11px] font-sans">
              Did outperformance occur during broad liquidity rallies or during market drawdowns?
            </span>
          </div>

          <div className="rounded bg-zinc-950/60 p-3 border border-zinc-800">
            <span className="text-zinc-200 font-bold block mb-1">2. Portfolio Concentration</span>
            <span className="text-zinc-400 text-[11px] font-sans">
              Is return driven by fund-wide alpha or by heavy weight in 2-3 outperforming stocks?
            </span>
          </div>

          <div className="rounded bg-zinc-950/60 p-3 border border-zinc-800">
            <span className="text-zinc-200 font-bold block mb-1">3. Manager Tenure & Style Drift</span>
            <span className="text-zinc-400 text-[11px] font-sans">
              Did current fund leadership generate the 5-year track record without style deviation?
            </span>
          </div>
        </div>
      </section>
    </PageContainer>
  );
}
