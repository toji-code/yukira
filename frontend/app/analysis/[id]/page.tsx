"use client";

import { use, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { PageContainer } from "@/components/layout/PageContainer";
import { StateView } from "@/components/epistemic/StateView";
import { MethodologyBadge } from "@/components/epistemic/MethodologyBadge";
import { DataQualityBadge } from "@/components/epistemic/DataQualityBadge";
import { EpistemicBanner } from "@/components/epistemic/EpistemicBanner";
import { ProgressiveDisclosure } from "@/components/disclosure/ProgressiveDisclosure";
import { MetricValueDisplay } from "@/components/primitives/MetricValueDisplay";
import { fetchAnalysis, executeAnalysis, executeProfileAnalysis } from "@/lib/api/analysis";
import { AnalysisResponse, AnalyticalProfileResponse } from "@/types/analysis";
import { formatDateTime } from "@/lib/utils/formatters";
import { InstitutionalProfileView } from "@/components/analysis/InstitutionalProfileView";

interface PageProps {
  params: Promise<{ id: string }>;
}

export default function CalculationAnalysisPage({ params }: PageProps) {
  const resolvedParams = use(params);
  const runId = parseInt(resolvedParams.id, 10);
  const router = useRouter();

  const [data, setData] = useState<AnalysisResponse | AnalyticalProfileResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Parameterized run launcher state
  const [showParamPanel, setShowParamPanel] = useState(false);
  const [customStartDate, setCustomStartDate] = useState("2024-01-01");
  const [customEndDate, setCustomEndDate] = useState("2024-01-15");
  const [customCutoff, setCustomCutoff] = useState("2024-01-31T23:59:59+05:30");
  const [executingCustom, setExecutingCustom] = useState(false);
  const [customError, setCustomError] = useState<string | null>(null);

  const reloadAnalysis = () => {
    if (isNaN(runId)) {
      setError("Invalid calculation run ID");
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    fetchAnalysis<AnalysisResponse | AnalyticalProfileResponse>(runId)
      .then((res) => {
        setData(res);
        if ("returnMetrics" in res) {
          setCustomStartDate(res.context.startDate);
          setCustomEndDate(res.context.asOfDate);
          setCustomCutoff(res.context.knowledgeCutoffTime);
        } else {
          const start = res.period?.requestedStartDate || res.window?.requestedStartDate;
          const end = res.period?.requestedEndDate || res.window?.requestedEndDate;
          if (start) setCustomStartDate(start);
          if (end) setCustomEndDate(end);
          if (res.pit?.knowledgeCutoffTime) setCustomCutoff(res.pit.knowledgeCutoffTime);
        }
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : "Failed to load calculation run analysis");
      })
      .finally(() => {
        setLoading(false);
      });
  };

  useEffect(() => {
    if (isNaN(runId)) {
      return;
    }
    let active = true;
    fetchAnalysis<AnalysisResponse | AnalyticalProfileResponse>(runId)
      .then((res) => {
        if (active) {
          setData(res);
          if ("returnMetrics" in res) {
            setCustomStartDate(res.context.startDate);
            setCustomEndDate(res.context.asOfDate);
            setCustomCutoff(res.context.knowledgeCutoffTime);
          } else {
            const start = res.period?.requestedStartDate || res.window?.requestedStartDate;
            const end = res.period?.requestedEndDate || res.window?.requestedEndDate;
            if (start) setCustomStartDate(start);
            if (end) setCustomEndDate(end);
            if (res.pit?.knowledgeCutoffTime) setCustomCutoff(res.pit.knowledgeCutoffTime);
          }
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (active) {
          setError(err instanceof Error ? err.message : "Failed to load calculation run analysis");
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [runId]);

  const handleExecuteParameterizedRun = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!data) return;

    setExecutingCustom(true);
    setCustomError(null);

    try {
      if ("returnMetrics" in data) {
        const response = await executeProfileAnalysis({
          schemeOptionId: data.context.schemeOptionId,
          asOfDate: customEndDate,
          knowledgeCutoffTime: customCutoff,
          methodologyTag: "APPROVED_M2N",
        });
        setShowParamPanel(false);
        router.push(`/analysis/${response.provenance.calculationRunId}`);
      } else {
        if (!data.identity.schemeOptionId) return;
        const response = await executeAnalysis(data.result.metricCode, {
          schemeOptionId: data.identity.schemeOptionId,
          startDate: customStartDate,
          endDate: customEndDate,
          knowledgeCutoffTime: customCutoff,
          methodologyTag: "CANDIDATE_V1",
        });
        setShowParamPanel(false);
        router.push(`/analysis/${response.provenance.calculationRunId}`);
      }
    } catch (err: unknown) {
      setCustomError(err instanceof Error ? err.message : "Calculation failed");
    } finally {
      setExecutingCustom(false);
    }
  };

  if (loading) {
    return (
      <PageContainer title="Quantitative Analysis Workspace">
        <StateView
          kind="loading"
          title="Retrieving Authoritative Quantitative Audit"
          message={`Fetching calculation run #${runId} and cryptographic audit manifest from backend...`}
        />
      </PageContainer>
    );
  }

  if (error || !data) {
    return (
      <PageContainer title="Analysis Run Unavailable">
        <StateView
          kind="unavailable"
          title="Calculation Run Unavailable"
          message={error || `Calculation Run #${runId} could not be retrieved from the backend.`}
          action={
            <button
              onClick={reloadAnalysis}
              className="inline-flex rounded-md bg-zinc-800 px-3.5 py-2 text-xs font-mono font-medium text-zinc-200 hover:bg-zinc-700"
            >
              Retry Retrieval
            </button>
          }
        />
      </PageContainer>
    );
  }

  // Phase 2R Unified Profile Branch
  if ("returnMetrics" in data) {
    return (
      <PageContainer
        title={`Analysis Run #${data.provenance.calculationRunId}`}
        subtitle={`${data.context.schemeName} • Option #${data.context.schemeOptionId || "—"} (${data.context.optionType})`}
        breadcrumbs={[
          { label: "Home", href: "/" },
          { label: "Funds", href: "/funds" },
          { label: `Run #${data.provenance.calculationRunId}`, href: `/analysis/${data.provenance.calculationRunId}` },
        ]}
      >
        {showParamPanel && (
          <form
            onSubmit={handleExecuteParameterizedRun}
            className="mb-8 rounded-xl border border-zinc-800 bg-zinc-900/90 p-5 font-mono text-xs space-y-4 animate-in fade-in duration-150"
          >
            <div className="flex items-center justify-between border-b border-zinc-800 pb-3">
              <h3 className="font-semibold text-zinc-100 uppercase tracking-wider">
                Execute Parameterized Institutional Profile Run
              </h3>
              <span className="text-zinc-500 text-[10px]">
                Option #{data.context.schemeOptionId} &bull; 16 Deterministic Metrics
              </span>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-zinc-400 mb-1 text-[11px]">Analysis Cutoff Date (T)</label>
                <input
                  type="date"
                  value={customEndDate}
                  onChange={(e) => setCustomEndDate(e.target.value)}
                  required
                  className="w-full rounded border border-zinc-700 bg-zinc-950 px-3 py-1.5 text-zinc-200 focus:border-cyan-500 focus:outline-none"
                />
              </div>
              <div>
                <label className="block text-zinc-400 mb-1 text-[11px]">Knowledge Cutoff Time (ISO 8601)</label>
                <input
                  type="text"
                  value={customCutoff}
                  onChange={(e) => setCustomCutoff(e.target.value)}
                  required
                  className="w-full rounded border border-zinc-700 bg-zinc-950 px-3 py-1.5 text-zinc-200 focus:border-cyan-500 focus:outline-none"
                />
              </div>
            </div>

            {customError && (
              <div className="rounded bg-rose-500/10 border border-rose-500/30 p-2 text-rose-300 text-xs">
                {customError}
              </div>
            )}

            <div className="flex justify-end gap-3 pt-2">
              <button
                type="button"
                onClick={() => setShowParamPanel(false)}
                className="rounded px-3 py-1.5 text-zinc-400 hover:text-zinc-200 font-mono"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={executingCustom}
                className="rounded-lg bg-cyan-600 px-4 py-1.5 font-semibold text-white hover:bg-cyan-500 disabled:opacity-50 font-mono"
              >
                {executingCustom ? "Executing 16-Metric Profile..." : "Dispatch Profile Run"}
              </button>
            </div>
          </form>
        )}

        <InstitutionalProfileView data={data} onRerunRequested={() => setShowParamPanel(!showParamPanel)} />
      </PageContainer>
    );
  }

  const isCalculated = data.result.calculationStatus === "CALCULATED";

  const isInsufficient = data.result.calculationStatus === "INSUFFICIENT_DATA" || data.limitations.insufficientEvidence;
  const isRiskMetric = data.result.metricCode.startsWith("RSK-");
  const hasWindow = Boolean(data.window);
  const rskMethodology = isRiskMetric ? (data.methodology as { annualizationConvention?: string; denominatorConvention?: string }) : null;

  const getMetricInterpretation = (code: string): string => {
    switch (code) {
      case "RSK-01":
        return "Annualized standard deviation of daily returns: measures total historical return dispersion around the sample mean. Treats upside gains and downside drawdowns with equal mathematical penalty.";
      case "RSK-02":
        return "Annualized downside semideviation: measures dispersion strictly among negative trading days below zero return (MAR = 0.0). Isolates asymmetric downside volatility.";
      case "RSK-03":
        return "3-Year maximum drawdown: peak-to-trough worst observed decline from cumulative running peak NAV. Realized historical capital impairment only.";
      case "RSK-04":
        return "Maximum drawdown duration: longest calendar days between a peak and subsequent full recovery to prior high.";
      case "RSK-05":
        return "Ulcer Index: quadratic root-mean-square of percentage drawdowns from running peak NAV. Evaluates compound holding stress (depth × duration).";
      case "RSK-06":
        return "Historical Value at Risk (95%): empirical 5th percentile daily loss threshold over the 36-month lookback window.";
      case "RSK-07":
        return "Expected Shortfall (CVaR 95%): conditional mean of daily returns strictly below the 5th percentile VaR cutoff.";
      case "RAT-01":
        return "Sharpe Ratio (3Y): annualized excess return per unit of total risk above the FBIL 91-day T-bill risk-free benchmark.";
      case "RAT-02":
        return "Treynor Ratio (3Y): annualized excess return per unit of systematic equity market risk (Beta).";
      case "REL-01":
        return "Equity Beta (3Y): slope coefficient from single-index excess-return OLS regression against the NIFTY 50 TRI benchmark.";
      case "REL-04":
        return "Downside Beta (3Y): market sensitivity conditioned exclusively on trading days where the benchmark experienced negative returns (R_b < 0).";
      case "RET-03":
        return "3-Year CAGR: compound annualized growth rate normalized across 36 calendar months using 365.25 calendar days per year convention.";
      case "RET-02":
      default:
        return "Discrete period return: percentage capital appreciation between start and end net asset value observations.";
    }
  };

  const getMetricLimitation = (code: string): string => {
    switch (code) {
      case "RSK-01":
        return "Phase 2N approved annualization (M2N-01, √252, N-1). Assumes stationary trading-day scaling; realized historical volatility does not forecast future volatility.";
      case "RSK-02":
        return "Candidate methodology (CANDIDATE_V1). MAR set to 0.0% (M2N-04 divisor convention remains deferred). Realized downside dispersion only.";
      case "RSK-03":
        return "Candidate methodology (CANDIDATE_V1). Historical worst-case decline does not place a mathematical upper bound on future market cycle drawdowns.";
      case "RSK-04":
        return "Candidate methodology (CANDIDATE_V1). Unrecovered drawdowns are strictly censored as of knowledge cutoff timestamp.";
      case "RSK-05":
        return "Candidate methodology (CANDIDATE_V1). Quadratic weighting penalizes deep drawdowns more heavily than shallow ones.";
      case "RSK-06":
        return "Candidate methodology (CANDIDATE_V1). Non-parametric empirical quantile; does not describe severity in the remaining 5% tail.";
      case "RSK-07":
        return "Candidate methodology (CANDIDATE_V1). Sub-sample tail average subject to estimation variance during calm market regimes.";
      case "RAT-01":
        return "Phase 2N approved methodology (M2N-01, M2N-02). Symmetrical standard deviation penalty treats upside and downside dispersion equally.";
      case "RAT-02":
        return "Phase 2N approved methodology (M2N-01, M2N-02, M2N-05, M2N-06). Applicable only when systematic Beta > 0; meaningful for diversified equity portfolios.";
      case "REL-01":
        return "Phase 2N approved methodology (M2N-01, M2N-06). Linear excess-return OLS model assuming constant market sensitivity; beta varies across market regimes.";
      case "REL-04":
        return "Phase 2N approved methodology (M2N-07). Requires a minimum of 100 negative benchmark trading days in lookback window.";
      case "RET-03":
        return "Phase 2N approved annualization (M2N-01, 365.25/D). Point-to-point annualized CAGR masks multi-month intermediate drawdowns and volatility.";
      case "RET-02":
      default:
        return "Candidate methodology (CANDIDATE_V1). Discrete period return only; unvalidated for live investor decision support.";
    }
  };

  const getMetricObservation = (code: string): string => {
    if (!isCalculated || data.result.numericValue === null || data.result.numericValue === undefined) {
      return `Metric ${code} status: ${data.result.calculationStatus}. ${data.result.errorMessage || "Minimum observation threshold not met."}`;
    }

    if (code === "RSK-04") {
      return `${code} ${data.result.metricName}: ${data.result.numericValue} calendar days elapsed.`;
    }

    if (code === "RSK-05") {
      return `${code} ${data.result.metricName}: ${Number(data.result.numericValue).toFixed(2)} points.`;
    }

    if (code === "RAT-01" || code === "RAT-02") {
      return `${code} ${data.result.metricName}: ${Number(data.result.numericValue).toFixed(2)}x ratio (${data.result.numericValue}).`;
    }

    if (code === "REL-01" || code === "REL-04") {
      return `${code} ${data.result.metricName}: ${Number(data.result.numericValue).toFixed(4)} beta coefficient (${data.result.numericValue}).`;
    }

    return `${code} ${data.result.metricName}: ${(Number(data.result.numericValue) * 100).toFixed(2)}% (${data.result.numericValue}).`;
  };

  return (
    <PageContainer
      title={`Analysis Run #${data.provenance.calculationRunId}`}
      subtitle={`${data.identity.schemeName} • Option #${data.identity.schemeOptionId || "—"} (${data.identity.optionType})`}
      breadcrumbs={[
        { label: "Home", href: "/" },
        { label: "Funds", href: "/funds" },
        { label: `Run #${data.provenance.calculationRunId}`, href: `/analysis/${data.provenance.calculationRunId}` },
      ]}
      action={
        <Link
          href={`/funds/${data.identity.schemeOptionId || 1}`}
          className="inline-flex items-center gap-1.5 rounded-lg border border-zinc-800 bg-zinc-900 px-3.5 py-2 text-xs font-mono text-zinc-300 hover:bg-zinc-800 hover:text-white transition"
        >
          <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
          </svg>
          Fund Detail Workspace
        </Link>
      }
    >
      {/* 1. TOP STATUS & CONTROLS BAR */}
      <div className="mb-6 flex flex-wrap items-center justify-between gap-4 border-b border-zinc-800 pb-4">
        <div className="flex flex-wrap items-center gap-3">
          <span className="font-mono text-xs text-zinc-400">Execution Status:</span>
          <span
            className={`inline-flex items-center rounded-md px-2.5 py-1 text-xs font-mono font-semibold ${
              isCalculated
                ? "bg-emerald-500/15 text-emerald-400 border border-emerald-500/30"
                : isInsufficient
                ? "bg-amber-500/15 text-amber-400 border border-amber-500/30"
                : "bg-rose-500/15 text-rose-400 border border-rose-500/30"
            }`}
          >
            {data.result.calculationStatus}
          </span>

          <MethodologyBadge
            status={data.methodology.approvalStatus}
            convention={data.methodology.methodologyVersion}
          />
        </div>

        <button
          onClick={() => setShowParamPanel(!showParamPanel)}
          className="rounded-lg border border-zinc-700 bg-zinc-800/90 px-3 py-1.5 text-xs font-mono font-medium text-zinc-200 hover:bg-zinc-700 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-cyan-500"
        >
          {showParamPanel ? "Close Parameter Controls" : `Re-run Parameterized ${data.result.metricCode}`}
        </button>
      </div>

      {/* Parameterized Calculation Drawer */}
      {showParamPanel && (
        <form
          onSubmit={handleExecuteParameterizedRun}
          className="mb-8 rounded-xl border border-zinc-800 bg-zinc-900/90 p-5 font-mono text-xs space-y-4 animate-in fade-in duration-150"
        >
          <div className="flex items-center justify-between border-b border-zinc-800 pb-3">
            <h3 className="font-semibold text-zinc-100 uppercase tracking-wider">
              Execute Parameterized {data.result.metricCode} Run
            </h3>
            <span className="text-zinc-500 text-[10px]">
              Option #{data.identity.schemeOptionId} &bull; Deterministic Execution
            </span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {!hasWindow && (
              <div>
                <label className="block text-zinc-400 mb-1 text-[11px]">Requested Start Date</label>
                <input
                  type="date"
                  value={customStartDate}
                  onChange={(e) => setCustomStartDate(e.target.value)}
                  required
                  className="w-full rounded border border-zinc-700 bg-zinc-950 px-3 py-1.5 text-zinc-200 focus:border-cyan-500 focus:outline-none"
                />
              </div>
            )}
            <div>
              <label className="block text-zinc-400 mb-1 text-[11px]">
                {hasWindow ? "Analysis Cutoff Date (T)" : "Requested End Date"}
              </label>
              <input
                type="date"
                value={customEndDate}
                onChange={(e) => setCustomEndDate(e.target.value)}
                required
                className="w-full rounded border border-zinc-700 bg-zinc-950 px-3 py-1.5 text-zinc-200 focus:border-cyan-500 focus:outline-none"
              />
            </div>
            <div>
              <label className="block text-zinc-400 mb-1 text-[11px]">Knowledge Cutoff Time (ISO 8601)</label>
              <input
                type="text"
                value={customCutoff}
                onChange={(e) => setCustomCutoff(e.target.value)}
                required
                className="w-full rounded border border-zinc-700 bg-zinc-950 px-3 py-1.5 text-zinc-200 focus:border-cyan-500 focus:outline-none"
              />
            </div>
          </div>

          {customError && (
            <div className="rounded bg-rose-500/10 border border-rose-500/30 p-2 text-rose-300 text-xs">
              {customError}
            </div>
          )}

          <div className="flex justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={() => setShowParamPanel(false)}
              className="rounded px-3 py-1.5 text-zinc-400 hover:text-zinc-200 font-mono"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={executingCustom}
              className="rounded-lg bg-cyan-600 px-4 py-1.5 font-semibold text-white hover:bg-cyan-500 disabled:opacity-50 font-mono"
            >
              {executingCustom ? "Executing Python Kernel..." : "Dispatch Run"}
            </button>
          </div>
        </form>
      )}

      {/* 2. EPISTEMIC TRI-PARTITE DISTINCTION: Observation vs. Interpretation vs. Limitation */}
      <div className="mb-8">
        <EpistemicBanner
          observation={getMetricObservation(data.result.metricCode)}
          interpretation={getMetricInterpretation(data.result.metricCode)}
          limitation={getMetricLimitation(data.result.metricCode)}
        />
      </div>

      {/* 3. PROGRESSIVE DISCLOSURE ARCHITECTURE */}
      <ProgressiveDisclosure
        level1={
          <div className="space-y-6">
            {/* Primary KPI Card */}
            <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-6 backdrop-blur-sm">
              <div className="flex flex-col md:flex-row md:items-center justify-between gap-6">
                <div className="space-y-3">
                  <MetricValueDisplay
                    label={`${data.result.metricCode} • ${data.result.metricName}`}
                    value={data.result.numericValue}
                    units={data.result.units || "PERCENTAGE"}
                    isCandidate={data.methodology.approvalStatus !== "APPROVED"}
                    candidateConvention={data.methodology.methodologyVersion}
                    description={getMetricInterpretation(data.result.metricCode)}
                  />
                  <div className="font-mono text-xs text-zinc-400">
                    Engine Status: <strong className="text-zinc-200">{data.result.calculationStatus}</strong>
                  </div>
                </div>

                <div className="rounded-xl border border-zinc-800 bg-zinc-950/70 p-4 font-mono text-xs space-y-2">
                  <div className="text-zinc-500 uppercase tracking-wider text-[10px] font-bold">
                    {hasWindow ? "36-Month Lookback Window" : "Evaluation Window"}
                  </div>
                  {hasWindow && data.window ? (
                    <>
                      <div>
                        <span className="text-zinc-500">Target Range: </span>
                        <span className="text-zinc-300 font-semibold">
                          {data.window.requestedStartDate || "—"} &rarr; {data.window.requestedEndDate || "—"}
                        </span>
                      </div>
                      <div>
                        <span className="text-zinc-500">Resolved Dates: </span>
                        <span className="text-cyan-400 font-semibold">
                          {data.window.actualStartDate || "—"} &rarr; {data.window.actualEndDate || "—"}
                        </span>
                      </div>
                      <div>
                        <span className="text-zinc-500">Observation Count: </span>
                        <span className="text-emerald-400 font-semibold">
                          {data.window.observationCount} days ({data.window.minObservationsRequired} required)
                        </span>
                      </div>
                    </>
                  ) : data.period ? (
                    <>
                      <div>
                        <span className="text-zinc-500">Requested Dates: </span>
                        <span className="text-zinc-300 font-semibold">
                          {data.period.requestedStartDate || "—"} &rarr; {data.period.requestedEndDate || "—"}
                        </span>
                      </div>
                      <div>
                        <span className="text-zinc-500">Resolved Dates: </span>
                        <span className="text-cyan-400 font-semibold">
                          {data.period.selectedStartDate || "—"} &rarr; {data.period.selectedEndDate || "—"}
                        </span>
                      </div>
                    </>
                  ) : null}
                </div>
              </div>

              {isInsufficient && data.result.errorMessage && (
                <div className="mt-6 rounded-lg border border-amber-500/30 bg-amber-500/10 p-4 text-xs text-amber-300 font-mono">
                  <strong className="uppercase">Evidence Limitation: </strong>
                  {data.result.errorMessage}
                </div>
              )}
            </div>

            {/* Scheme Metadata Strip */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 font-mono text-xs">
              <div className="rounded-lg border border-zinc-800 bg-zinc-900/40 p-3">
                <span className="text-zinc-500 text-[10px] uppercase block">AMFI Code</span>
                <span className="text-zinc-200 font-semibold">{data.identity.amfiCode || "—"}</span>
              </div>
              <div className="rounded-lg border border-zinc-800 bg-zinc-900/40 p-3">
                <span className="text-zinc-500 text-[10px] uppercase block">ISIN</span>
                <span className="text-zinc-200 font-semibold">{data.identity.isin || "—"}</span>
              </div>
              <div className="rounded-lg border border-zinc-800 bg-zinc-900/40 p-3">
                <span className="text-zinc-500 text-[10px] uppercase block">Option Type</span>
                <span className="text-zinc-200 font-semibold">{data.identity.optionType}</span>
              </div>
              <div className="rounded-lg border border-zinc-800 bg-zinc-900/40 p-3">
                <span className="text-zinc-500 text-[10px] uppercase block">Candidate Version</span>
                <span className="text-amber-400 font-semibold">{data.methodology.methodologyVersion}</span>
              </div>
            </div>
          </div>
        }
        level2={
          <div className="space-y-6">
            <h3 className="font-mono text-xs uppercase tracking-wider text-zinc-400">
              Evidence Base & Methodology Resolution
            </h3>

            {/* Window / Period Evidence Details */}
            {hasWindow && data.window ? (
              <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-5 font-mono text-xs space-y-3">
                <div className="flex items-center justify-between pb-3 border-b border-zinc-800">
                  <span className="font-semibold text-zinc-300 uppercase tracking-wider">
                    36-Month Trading Continuity
                  </span>
                  <span className="text-[10px] text-emerald-400 bg-emerald-500/10 border border-emerald-500/20 px-2 py-0.5 rounded">
                    Sufficient History ({data.window.observationCount} &ge; {data.window.minObservationsRequired} required)
                  </span>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4 pt-2">
                  <div>
                    <span className="text-zinc-500 text-[10px] uppercase block">Window Start</span>
                    <span className="text-cyan-400 font-semibold mt-1 block">{data.window.actualStartDate}</span>
                    <span className="text-zinc-500 text-[10px]">Requested: {data.window.requestedStartDate}</span>
                  </div>
                  <div>
                    <span className="text-zinc-500 text-[10px] uppercase block">Window End (As-Of Cutoff)</span>
                    <span className="text-cyan-400 font-semibold mt-1 block">{data.window.actualEndDate}</span>
                    <span className="text-zinc-500 text-[10px]">Requested: {data.window.requestedEndDate}</span>
                  </div>
                  <div>
                    <span className="text-zinc-500 text-[10px] uppercase block">Trading Continuity</span>
                    <span className="text-zinc-200 font-semibold mt-1 block">{data.window.observationCount} trading dates</span>
                    <span className="text-zinc-500 text-[10px]">Zero synthetic imputation</span>
                  </div>
                </div>
              </div>
            ) : data.period ? (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6 font-mono text-xs">
                <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-5">
                  <div className="flex items-center justify-between pb-3 border-b border-zinc-800">
                    <span className="font-semibold text-zinc-300 uppercase tracking-wider">
                      Start Observation (NAV_start)
                    </span>
                    <span className="text-[10px] text-zinc-500 bg-zinc-800 px-2 py-0.5 rounded">
                      {data.period.startSubstituted
                        ? `${data.period.startLookbackDaysUsed}d lookback substitution`
                        : "Exact date match"}
                    </span>
                  </div>
                  <div className="mt-4 space-y-2">
                    <div className="flex justify-between">
                      <span className="text-zinc-500">Requested Target:</span>
                      <span className="text-zinc-300">{data.period.requestedStartDate || "—"}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-zinc-500">Selected Effective:</span>
                      <span className="text-cyan-400 font-semibold">{data.period.selectedStartDate || "—"}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-zinc-500">Lookback Days Used:</span>
                      <span className="text-zinc-300">{data.period.startLookbackDaysUsed} / 4 calendar days max</span>
                    </div>
                  </div>
                </div>

                <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-5">
                  <div className="flex items-center justify-between pb-3 border-b border-zinc-800">
                    <span className="font-semibold text-zinc-300 uppercase tracking-wider">
                      End Observation (NAV_end)
                    </span>
                    <span className="text-[10px] text-zinc-500 bg-zinc-800 px-2 py-0.5 rounded">
                      {data.period.endSubstituted
                        ? `${data.period.endLookbackDaysUsed}d lookback substitution`
                        : "Exact date match"}
                    </span>
                  </div>
                  <div className="mt-4 space-y-2">
                    <div className="flex justify-between">
                      <span className="text-zinc-500">Requested Target:</span>
                      <span className="text-zinc-300">{data.period.requestedEndDate || "—"}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-zinc-500">Selected Effective:</span>
                      <span className="text-cyan-400 font-semibold">{data.period.selectedEndDate || "—"}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-zinc-500">Lookback Days Used:</span>
                      <span className="text-zinc-300">{data.period.endLookbackDaysUsed} / 4 calendar days max</span>
                    </div>
                  </div>
                </div>
              </div>
            ) : null}

            {/* Methodology Specification Panel */}
            <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-5 font-mono text-xs space-y-2">
              <span className="font-semibold text-zinc-300 uppercase tracking-wider block mb-2">
                Methodology Specification ({data.methodology.methodologyCode})
              </span>
              <div className="text-zinc-400">
                <span className="text-zinc-500">Formula: </span>
                <span className="text-zinc-200">{data.methodology.formulaDisclosure}</span>
              </div>
              {isRiskMetric && rskMethodology?.annualizationConvention ? (
                <>
                  <div className="text-zinc-400">
                    <span className="text-zinc-500">Annualization: </span>
                    <span className="text-amber-300 font-semibold">{rskMethodology.annualizationConvention} (Candidate — not validated)</span>
                  </div>
                  {rskMethodology.denominatorConvention && (
                    <div className="text-zinc-400">
                      <span className="text-zinc-500">Denominator: </span>
                      <span className="text-amber-300 font-semibold">{rskMethodology.denominatorConvention} (Candidate — not validated)</span>
                    </div>
                  )}
                </>
              ) : (
                <div className="text-zinc-400">
                  <span className="text-zinc-500">Window Rule: </span>
                  <span className="text-zinc-200">{data.methodology.lookbackSpecification || "36 calendar months candidate analytical window"}</span>
                </div>
              )}
              <div className="text-zinc-400">
                <span className="text-zinc-500">Knowledge Cutoff: </span>
                <span className="text-zinc-200">{formatDateTime(data.pit.knowledgeCutoffTime)}</span>
              </div>
            </div>
          </div>
        }
        level3={
          <div className="space-y-6">
            <h3 className="font-mono text-xs uppercase tracking-wider text-zinc-400">
              Institutional Audit & Deep Provenance
            </h3>

            {/* 6-Dimension Quality States */}
            <div>
              <span className="font-mono text-xs text-zinc-400 block mb-3 uppercase tracking-wider">
                Approved 6-Dimensional Data Quality States
              </span>
              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
                {data.quality.dimensions.map((dim) => (
                  <div key={dim.dimension} className="rounded-lg border border-zinc-800 bg-zinc-900/40 p-3 font-mono text-xs">
                    <span className="text-zinc-500 text-[10px] uppercase block mb-1">{dim.dimension}</span>
                    <DataQualityBadge
                      type={
                        dim.dimension.toLowerCase() === "quality" ? "assessment"
                        : dim.dimension.toLowerCase() === "verification" ? "verification"
                        : dim.dimension.toLowerCase() === "revision" ? "revision"
                        : dim.dimension.toLowerCase() === "freshness" ? "temporal"
                        : dim.dimension.toLowerCase() === "presence" ? "presence"
                        : "integrity"
                      }
                      status={dim.state}
                    />
                    <p className="mt-2 text-[10px] text-zinc-500 line-clamp-2" title={dim.description}>
                      {dim.description}
                    </p>
                  </div>
                ))}
              </div>
            </div>

            {/* Run Provenance Details */}
            <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-5 font-mono text-xs space-y-4">
              <div className="flex items-center justify-between border-b border-zinc-800 pb-3">
                <span className="font-semibold text-zinc-300 uppercase tracking-wider">
                  Calculation Run #{data.provenance.calculationRunId}
                </span>
                <span className="text-zinc-500 text-[11px]">
                  Started: {formatDateTime(data.provenance.executionStartedAt)}
                </span>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-zinc-400">
                <div>
                  <span className="text-zinc-500 text-[10px] uppercase block mb-1">
                    Input Snapshot SHA-256 Digest
                  </span>
                  <span className="text-zinc-200 break-all select-all block bg-zinc-950 p-2 rounded border border-zinc-800">
                    {data.provenance.inputSnapshotSha256 || "—"}
                  </span>
                </div>
                <div>
                  <span className="text-zinc-500 text-[10px] uppercase block mb-1">
                    Quant Engine Version
                  </span>
                  <span className="text-zinc-200 block bg-zinc-950 p-2 rounded border border-zinc-800">
                    {data.provenance.quantEngineVersion}
                  </span>
                </div>
              </div>
            </div>

            {/* Input Observations Lineage Table */}
            <div>
              <span className="font-mono text-xs text-zinc-400 block mb-3 uppercase tracking-wider">
                Authoritative Input Observations ({data.provenance.inputObservations.length} Lineage Records)
              </span>

              {data.provenance.inputObservations.length === 0 ? (
                <div className="rounded-lg border border-zinc-800 bg-zinc-900/30 p-4 font-mono text-xs text-zinc-500 text-center">
                  Zero input observations recorded for this run.
                </div>
              ) : (
                <div className="overflow-x-auto rounded-xl border border-zinc-800 bg-zinc-900/60 font-mono text-xs">
                  <table className="w-full text-left">
                    <thead className="border-b border-zinc-800 bg-zinc-950/60 uppercase tracking-wider text-zinc-400">
                      <tr>
                        <th className="px-4 py-3">Role</th>
                        <th className="px-4 py-3">Obs ID</th>
                        <th className="px-4 py-3">Effective Date</th>
                        <th className="px-4 py-3">Rev Seq</th>
                        <th className="px-4 py-3">NAV Value (INR)</th>
                        <th className="px-4 py-3">Quality</th>
                        <th className="px-4 py-3">Temporal Status</th>
                        <th className="px-4 py-3">Source Artifact Hash</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-zinc-800/60">
                      {data.provenance.inputObservations.map((obs) => (
                        <tr key={obs.observationId} className="hover:bg-zinc-800/30">
                          <td className="px-4 py-3 font-semibold text-cyan-400">
                            {obs.role}
                          </td>
                          <td className="px-4 py-3 text-zinc-400">
                            #{obs.observationId}
                          </td>
                          <td className="px-4 py-3 text-zinc-200">
                            {obs.effectiveDate}
                          </td>
                          <td className="px-4 py-3 text-zinc-400">
                            v{obs.revisionSeq}
                          </td>
                          <td className="px-4 py-3 text-zinc-100 font-bold">
                            {obs.navValue !== null && obs.navValue !== undefined
                              ? Number(obs.navValue).toFixed(4)
                              : "—"}
                          </td>
                          <td className="px-4 py-3">
                            <DataQualityBadge type="assessment" status={obs.qualityAssessment} size="sm" />
                          </td>
                          <td className="px-4 py-3 text-zinc-400">
                            {obs.temporalStatus}
                          </td>
                          <td className="px-4 py-3 font-mono text-[10px] text-zinc-500">
                            {obs.sourceArtifactSha256
                              ? obs.sourceArtifactSha256.substring(0, 12) + "..."
                              : "unlinked"}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            {/* Source Artifacts Provenance */}
            {data.provenance.sourceArtifacts.length > 0 && (
              <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-5 font-mono text-xs space-y-3">
                <span className="font-semibold text-zinc-300 uppercase tracking-wider block">
                  Raw Ingestion Source Artifacts
                </span>
                {data.provenance.sourceArtifacts.map((art) => (
                  <div key={art.sourceArtifactId} className="rounded-lg border border-zinc-800 bg-zinc-950 p-3 space-y-1">
                    <div className="flex justify-between">
                      <span className="text-zinc-400">Artifact #{art.sourceArtifactId}</span>
                      <span className="text-zinc-500 text-[10px]">{art.byteSize} bytes</span>
                    </div>
                    <div className="text-zinc-500 text-[11px] break-all">
                      SHA-256: <span className="text-zinc-300 select-all">{art.sha256Hash}</span>
                    </div>
                    <div className="text-zinc-500 text-[10px]">
                      Source: {art.sourceUrl} | Retrieved: {formatDateTime(art.retrievalTimestamp)}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        }
      />
    </PageContainer>
  );
}
