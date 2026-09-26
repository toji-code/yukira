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
              className="inline-flex rounded-md border border-border bg-surface px-3.5 py-2 text-xs font-mono font-medium text-text-primary hover:bg-surface-elevated transition"
            >
              Retry Retrieval
            </button>
          }
        />
      </PageContainer>
    );
  }

  // Unified Profile Branch
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
            className="mb-8 rounded-xl border border-border bg-surface-elevated p-5 font-mono text-xs space-y-4 animate-in fade-in duration-150"
          >
            <div className="flex items-center justify-between border-b border-border pb-3">
              <h3 className="font-semibold text-text-primary uppercase tracking-wider">
                Execute Parameterized Institutional Profile Run
              </h3>
              <span className="text-text-muted text-[10px]">
                Option #{data.context.schemeOptionId} &bull; 16 Deterministic Metrics
              </span>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-text-muted mb-1 text-[11px]">Analysis Cutoff Date (T)</label>
                <input
                  type="date"
                  value={customEndDate}
                  onChange={(e) => setCustomEndDate(e.target.value)}
                  required
                  className="w-full rounded-md border border-border bg-surface px-3 py-1.5 text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                />
              </div>
              <div>
                <label className="block text-text-muted mb-1 text-[11px]">Knowledge Cutoff Time (ISO 8601)</label>
                <input
                  type="text"
                  value={customCutoff}
                  onChange={(e) => setCustomCutoff(e.target.value)}
                  required
                  className="w-full rounded-md border border-border bg-surface px-3 py-1.5 text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                />
              </div>
            </div>

            {customError && (
              <div className="rounded-md bg-danger/10 border border-danger/30 p-2 text-danger text-xs">
                {customError}
              </div>
            )}

            <div className="flex justify-end gap-3 pt-2">
              <button
                type="button"
                onClick={() => setShowParamPanel(false)}
                className="rounded-md px-3 py-1.5 text-text-muted hover:text-text-primary font-mono transition"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={executingCustom}
                className="rounded-md bg-accent px-4 py-1.5 font-semibold text-accent-foreground hover:opacity-90 disabled:opacity-50 font-mono transition"
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
        return "Sortino Ratio (3Y): annualized excess return per unit of downside semideviation below risk-free benchmark.";
      case "RAT-03":
        return "Treynor Ratio (3Y): annualized excess return per unit of systematic equity market risk (Beta).";
      case "REL-01":
        return "Tracking Error (3Y): annualized standard deviation of excess returns relative to benchmark.";
      case "REL-02":
        return "Jensen's Alpha (3Y): annualized intercept from single-index excess-return regression.";
      case "MKT-01":
        return "Equity Beta (3Y): slope coefficient from single-index excess-return OLS regression against the NIFTY 50 TRI benchmark.";
      case "MKT-02":
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
        return "Approved annualization (√252 trading-day convention, N-1 sample variance). Assumes stationary trading-day scaling; realized historical volatility does not forecast future volatility.";
      case "RSK-02":
        return "Candidate methodology. Minimum Acceptable Return (MAR) set to 0.0%. Divisor convention under empirical review. Realized downside dispersion only.";
      case "RSK-03":
        return "Candidate methodology. Historical worst-case decline does not place a mathematical upper bound on future market cycle drawdowns.";
      case "RSK-04":
        return "Candidate methodology. Unrecovered drawdowns are strictly censored as of knowledge cutoff timestamp.";
      case "RSK-05":
        return "Candidate methodology. Quadratic weighting penalizes deep drawdowns more heavily than shallow ones.";
      case "RSK-06":
        return "Candidate methodology. Non-parametric empirical quantile; does not describe severity in the remaining 5% tail.";
      case "RSK-07":
        return "Candidate methodology. Sub-sample tail average subject to estimation variance during calm market regimes.";
      case "RAT-01":
        return "Approved methodology. Symmetrical standard deviation penalty treats upside and downside dispersion equally.";
      case "RAT-02":
        return "Candidate methodology. Downside semideviation divisor convention under empirical regime testing.";
      case "RAT-03":
        return "Approved methodology. Applicable only when systematic Beta > 0; meaningful for diversified equity portfolios.";
      case "REL-01":
        return "Candidate methodology. Benchmark tracking error subject to index synchronization tolerances.";
      case "REL-02":
        return "Candidate methodology. Jensen's Alpha assumes stationary single-index capital asset pricing dynamics.";
      case "MKT-01":
        return "Approved methodology. Linear excess-return OLS model assuming constant market sensitivity; beta varies across market regimes.";
      case "MKT-02":
        return "Approved methodology. Requires a minimum of 100 negative benchmark trading days in lookback window.";
      case "RET-03":
        return "Approved annualization (365.25/D convention). Point-to-point annualized CAGR masks multi-month intermediate drawdowns and volatility.";
      case "RET-02":
      default:
        return "Candidate methodology. Discrete period return only; unvalidated for live investor decision support.";
    }
  };

  const getMetricObservation = (code: string): string => {
    if (!isCalculated || data.result.numericValue === null || data.result.numericValue === undefined) {
      return `${data.result.metricName} status: ${data.result.calculationStatus}. ${data.result.errorMessage || "Minimum observation threshold not met."}`;
    }

    if (code === "RSK-04") {
      return `${data.result.metricName}: ${data.result.numericValue} calendar days elapsed.`;
    }

    if (code === "RSK-05") {
      return `${data.result.metricName}: ${Number(data.result.numericValue).toFixed(2)} points.`;
    }

    if (code === "RAT-01" || code === "RAT-02" || code === "RAT-03") {
      return `${data.result.metricName}: ${Number(data.result.numericValue).toFixed(2)}x ratio (${data.result.numericValue}).`;
    }

    if (code === "MKT-01" || code === "MKT-02") {
      return `${data.result.metricName}: ${Number(data.result.numericValue).toFixed(4)} beta coefficient (${data.result.numericValue}).`;
    }

    return `${data.result.metricName}: ${(Number(data.result.numericValue) * 100).toFixed(2)}% (${data.result.numericValue}).`;
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
          className="inline-flex items-center gap-1.5 rounded-lg border border-border bg-surface px-3.5 py-2 text-xs font-mono text-text-secondary hover:bg-surface-elevated hover:text-text-primary transition"
        >
          <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
          </svg>
          Fund Detail Workspace
        </Link>
      }
    >
      {/* 1. TOP STATUS & CONTROLS BAR */}
      <div className="mb-6 flex flex-wrap items-center justify-between gap-4 border-b border-border pb-4">
        <div className="flex flex-wrap items-center gap-3">
          <span className="font-mono text-xs text-text-muted">Execution Status:</span>
          <span
            className={`inline-flex items-center rounded-md px-2.5 py-1 text-xs font-mono font-semibold ${
              isCalculated
                ? "bg-success/15 text-success border border-success/30"
                : isInsufficient
                ? "bg-warning/15 text-warning border border-warning/30"
                : "bg-danger/15 text-danger border border-danger/30"
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
          className="rounded-lg border border-border bg-surface px-3 py-1.5 text-xs font-mono font-medium text-text-secondary hover:bg-surface-elevated hover:text-text-primary transition focus:outline-none focus-visible:ring-2 focus-visible:ring-accent"
        >
          {showParamPanel ? "Close Parameter Controls" : "Re-run Parameterized Analysis"}
        </button>
      </div>

      {/* Parameterized Calculation Drawer */}
      {showParamPanel && (
        <form
          onSubmit={handleExecuteParameterizedRun}
          className="mb-8 rounded-xl border border-border bg-surface-elevated p-5 font-mono text-xs space-y-4 animate-in fade-in duration-150"
        >
          <div className="flex items-center justify-between border-b border-border pb-3">
            <h3 className="font-semibold text-text-primary uppercase tracking-wider">
              Execute Parameterized Analysis Run
            </h3>
            <span className="text-text-muted text-[10px]">
              Option #{data.identity.schemeOptionId} &bull; Deterministic Execution
            </span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {!hasWindow && (
              <div>
                <label className="block text-text-muted mb-1 text-[11px]">Requested Start Date</label>
                <input
                  type="date"
                  value={customStartDate}
                  onChange={(e) => setCustomStartDate(e.target.value)}
                  required
                  className="w-full rounded-md border border-border bg-surface px-3 py-1.5 text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                />
              </div>
            )}
            <div>
              <label className="block text-text-muted mb-1 text-[11px]">
                {hasWindow ? "Analysis Cutoff Date (T)" : "Requested End Date"}
              </label>
              <input
                type="date"
                value={customEndDate}
                onChange={(e) => setCustomEndDate(e.target.value)}
                required
                className="w-full rounded-md border border-border bg-surface px-3 py-1.5 text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
              />
            </div>
            <div>
              <label className="block text-text-muted mb-1 text-[11px]">Knowledge Cutoff Time (ISO 8601)</label>
              <input
                type="text"
                value={customCutoff}
                onChange={(e) => setCustomCutoff(e.target.value)}
                required
                className="w-full rounded-md border border-border bg-surface px-3 py-1.5 text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
              />
            </div>
          </div>

          {customError && (
            <div className="rounded-md bg-danger/10 border border-danger/30 p-2 text-danger text-xs">
              {customError}
            </div>
          )}

          <div className="flex justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={() => setShowParamPanel(false)}
              className="rounded-md px-3 py-1.5 text-text-muted hover:text-text-primary font-mono transition"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={executingCustom}
              className="rounded-md bg-accent px-4 py-1.5 font-semibold text-accent-foreground hover:opacity-90 disabled:opacity-50 font-mono transition"
            >
              {executingCustom ? "Executing Quantitative Engine..." : "Dispatch Run"}
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
            <div className="rounded-xl border border-border bg-card p-6 shadow-xs">
              <div className="flex flex-col md:flex-row md:items-center justify-between gap-6">
                <div className="space-y-3">
                  <MetricValueDisplay
                    label={data.result.metricName}
                    value={data.result.numericValue}
                    units={data.result.units || "PERCENTAGE"}
                    isCandidate={data.methodology.approvalStatus !== "APPROVED"}
                    candidateConvention={data.methodology.methodologyVersion}
                    description={getMetricInterpretation(data.result.metricCode)}
                  />
                  <div className="font-mono text-xs text-text-muted">
                    Engine Status: <strong className="text-text-primary">{data.result.calculationStatus}</strong>
                  </div>
                </div>

                <div className="rounded-xl border border-border bg-surface-elevated p-4 font-mono text-xs space-y-2">
                  <div className="text-text-muted uppercase tracking-wider text-[10px] font-bold">
                    {hasWindow ? "36-Month Lookback Window" : "Evaluation Window"}
                  </div>
                  {hasWindow && data.window ? (
                    <>
                      <div>
                        <span className="text-text-muted">Target Range: </span>
                        <span className="text-text-secondary font-semibold">
                          {data.window.requestedStartDate || "—"} &rarr; {data.window.requestedEndDate || "—"}
                        </span>
                      </div>
                      <div>
                        <span className="text-text-muted">Resolved Dates: </span>
                        <span className="text-accent font-semibold">
                          {data.window.actualStartDate || "—"} &rarr; {data.window.actualEndDate || "—"}
                        </span>
                      </div>
                      <div>
                        <span className="text-text-muted">Observation Count: </span>
                        <span className="text-success font-semibold">
                          {data.window.observationCount} days ({data.window.minObservationsRequired} required)
                        </span>
                      </div>
                    </>
                  ) : data.period ? (
                    <>
                      <div>
                        <span className="text-text-muted">Requested Dates: </span>
                        <span className="text-text-secondary font-semibold">
                          {data.period.requestedStartDate || "—"} &rarr; {data.period.requestedEndDate || "—"}
                        </span>
                      </div>
                      <div>
                        <span className="text-text-muted">Resolved Dates: </span>
                        <span className="text-accent font-semibold">
                          {data.period.selectedStartDate || "—"} &rarr; {data.period.selectedEndDate || "—"}
                        </span>
                      </div>
                    </>
                  ) : null}
                </div>
              </div>

              {isInsufficient && data.result.errorMessage && (
                <div className="mt-6 rounded-lg border border-warning/30 bg-warning/10 p-4 text-xs text-warning font-mono">
                  <strong className="uppercase">Evidence Limitation: </strong>
                  {data.result.errorMessage}
                </div>
              )}
            </div>

            {/* Scheme Metadata Strip */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 font-mono text-xs">
              <div className="rounded-lg border border-border bg-surface-elevated p-3">
                <span className="text-text-muted text-[10px] uppercase block">AMFI Code</span>
                <span className="text-text-primary font-semibold">{data.identity.amfiCode || "—"}</span>
              </div>
              <div className="rounded-lg border border-border bg-surface-elevated p-3">
                <span className="text-text-muted text-[10px] uppercase block">ISIN</span>
                <span className="text-text-primary font-semibold">{data.identity.isin || "—"}</span>
              </div>
              <div className="rounded-lg border border-border bg-surface-elevated p-3">
                <span className="text-text-muted text-[10px] uppercase block">Option Type</span>
                <span className="text-text-primary font-semibold">{data.identity.optionType}</span>
              </div>
              <div className="rounded-lg border border-border bg-surface-elevated p-3">
                <span className="text-text-muted text-[10px] uppercase block">Methodology Version</span>
                <span className="text-warning font-semibold">{data.methodology.methodologyVersion}</span>
              </div>
            </div>
          </div>
        }
        level2={
          <div className="space-y-6">
            <h3 className="font-mono text-xs uppercase tracking-wider text-text-muted">
              Evidence Base & Methodology Resolution
            </h3>

            {/* Window / Period Evidence Details */}
            {hasWindow && data.window ? (
              <div className="rounded-xl border border-border bg-surface-elevated p-5 font-mono text-xs space-y-3">
                <div className="flex items-center justify-between pb-3 border-b border-border">
                  <span className="font-semibold text-text-primary uppercase tracking-wider">
                    36-Month Trading Continuity
                  </span>
                  <span className="text-[10px] text-success bg-success/10 border border-success/20 px-2 py-0.5 rounded">
                    Sufficient History ({data.window.observationCount} &ge; {data.window.minObservationsRequired} required)
                  </span>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4 pt-2">
                  <div>
                    <span className="text-text-muted text-[10px] uppercase block">Window Start</span>
                    <span className="text-accent font-semibold mt-1 block">{data.window.actualStartDate}</span>
                    <span className="text-text-muted text-[10px]">Requested: {data.window.requestedStartDate}</span>
                  </div>
                  <div>
                    <span className="text-text-muted text-[10px] uppercase block">Window End (As-Of Cutoff)</span>
                    <span className="text-accent font-semibold mt-1 block">{data.window.actualEndDate}</span>
                    <span className="text-text-muted text-[10px]">Requested: {data.window.requestedEndDate}</span>
                  </div>
                  <div>
                    <span className="text-text-muted text-[10px] uppercase block">Trading Continuity</span>
                    <span className="text-text-primary font-semibold mt-1 block">{data.window.observationCount} trading dates</span>
                    <span className="text-text-muted text-[10px]">Zero synthetic imputation</span>
                  </div>
                </div>
              </div>
            ) : data.period ? (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6 font-mono text-xs">
                <div className="rounded-xl border border-border bg-surface-elevated p-5">
                  <div className="flex items-center justify-between pb-3 border-b border-border">
                    <span className="font-semibold text-text-primary uppercase tracking-wider">
                      Start Observation (NAV_start)
                    </span>
                    <span className="text-[10px] text-text-muted bg-surface px-2 py-0.5 rounded border border-border">
                      {data.period.startSubstituted
                        ? `${data.period.startLookbackDaysUsed}d lookback substitution`
                        : "Exact date match"}
                    </span>
                  </div>
                  <div className="mt-4 space-y-2">
                    <div className="flex justify-between">
                      <span className="text-text-muted">Requested Target:</span>
                      <span className="text-text-secondary">{data.period.requestedStartDate || "—"}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-text-muted">Selected Effective:</span>
                      <span className="text-accent font-semibold">{data.period.selectedStartDate || "—"}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-text-muted">Lookback Days Used:</span>
                      <span className="text-text-secondary">{data.period.startLookbackDaysUsed} / 4 calendar days max</span>
                    </div>
                  </div>
                </div>

                <div className="rounded-xl border border-border bg-surface-elevated p-5">
                  <div className="flex items-center justify-between pb-3 border-b border-border">
                    <span className="font-semibold text-text-primary uppercase tracking-wider">
                      End Observation (NAV_end)
                    </span>
                    <span className="text-[10px] text-text-muted bg-surface px-2 py-0.5 rounded border border-border">
                      {data.period.endSubstituted
                        ? `${data.period.endLookbackDaysUsed}d lookback substitution`
                        : "Exact date match"}
                    </span>
                  </div>
                  <div className="mt-4 space-y-2">
                    <div className="flex justify-between">
                      <span className="text-text-muted">Requested Target:</span>
                      <span className="text-text-secondary">{data.period.requestedEndDate || "—"}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-text-muted">Selected Effective:</span>
                      <span className="text-accent font-semibold">{data.period.selectedEndDate || "—"}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-text-muted">Lookback Days Used:</span>
                      <span className="text-text-secondary">{data.period.endLookbackDaysUsed} / 4 calendar days max</span>
                    </div>
                  </div>
                </div>
              </div>
            ) : null}

            {/* Methodology Specification Panel */}
            <div className="rounded-xl border border-border bg-surface-elevated p-5 font-mono text-xs space-y-2">
              <span className="font-semibold text-text-primary uppercase tracking-wider block mb-2">
                Methodology Specification ({data.methodology.methodologyCode})
              </span>
              <div>
                <span className="text-text-muted">Formula: </span>
                <span className="text-text-primary">{data.methodology.formulaDisclosure}</span>
              </div>
              {isRiskMetric && rskMethodology?.annualizationConvention ? (
                <>
                  <div>
                    <span className="text-text-muted">Annualization: </span>
                    <span className="text-warning font-semibold">{rskMethodology.annualizationConvention} (Candidate — not validated)</span>
                  </div>
                  {rskMethodology.denominatorConvention && (
                    <div>
                      <span className="text-text-muted">Denominator: </span>
                      <span className="text-warning font-semibold">{rskMethodology.denominatorConvention} (Candidate — not validated)</span>
                    </div>
                  )}
                </>
              ) : (
                <div>
                  <span className="text-text-muted">Window Rule: </span>
                  <span className="text-text-primary">{data.methodology.lookbackSpecification || "36 calendar months candidate analytical window"}</span>
                </div>
              )}
              <div>
                <span className="text-text-muted">Knowledge Cutoff: </span>
                <span className="text-text-primary">{formatDateTime(data.pit.knowledgeCutoffTime)}</span>
              </div>
            </div>
          </div>
        }
        level3={
          <div className="space-y-6">
            <h3 className="font-mono text-xs uppercase tracking-wider text-text-muted">
              Institutional Audit & Deep Provenance
            </h3>

            {/* 6-Dimension Quality States */}
            <div>
              <span className="font-mono text-xs text-text-muted block mb-3 uppercase tracking-wider">
                Authoritative 6-Dimensional Data Quality States
              </span>
              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
                {data.quality.dimensions.map((dim) => (
                  <div key={dim.dimension} className="rounded-lg border border-border bg-surface-elevated p-3 font-mono text-xs">
                    <span className="text-text-muted text-[10px] uppercase block mb-1">{dim.dimension}</span>
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
                    <p className="mt-2 text-[10px] text-text-muted line-clamp-2" title={dim.description}>
                      {dim.description}
                    </p>
                  </div>
                ))}
              </div>
            </div>

            {/* Run Provenance Details */}
            <div className="rounded-xl border border-border bg-surface-elevated p-5 font-mono text-xs space-y-4">
              <div className="flex items-center justify-between border-b border-border pb-3">
                <span className="font-semibold text-text-primary uppercase tracking-wider">
                  Calculation Run #{data.provenance.calculationRunId}
                </span>
                <span className="text-text-muted text-[11px]">
                  Started: {formatDateTime(data.provenance.executionStartedAt)}
                </span>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-text-secondary">
                <div>
                  <span className="text-text-muted text-[10px] uppercase block mb-1">
                    Input Snapshot SHA-256 Digest
                  </span>
                  <span className="text-text-primary break-all select-all block bg-surface p-2 rounded-md border border-border font-mono text-[11px]">
                    {data.provenance.inputSnapshotSha256 || "—"}
                  </span>
                </div>
                <div>
                  <span className="text-text-muted text-[10px] uppercase block mb-1">
                    Quantitative Engine Version
                  </span>
                  <span className="text-text-primary block bg-surface p-2 rounded-md border border-border font-mono text-[11px]">
                    {data.provenance.quantEngineVersion}
                  </span>
                </div>
              </div>
            </div>

            {/* Input Observations Lineage Table */}
            <div>
              <span className="font-mono text-xs text-text-muted block mb-3 uppercase tracking-wider">
                Authoritative Input Observations ({data.provenance.inputObservations.length} Lineage Records)
              </span>

              {data.provenance.inputObservations.length === 0 ? (
                <div className="rounded-lg border border-border bg-surface-elevated p-4 font-mono text-xs text-text-muted text-center">
                  Zero input observations recorded for this run.
                </div>
              ) : (
                <div className="overflow-x-auto rounded-xl border border-border bg-surface-elevated font-mono text-xs">
                  <table className="w-full text-left">
                    <thead className="border-b border-border bg-surface uppercase tracking-wider text-text-muted text-[11px]">
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
                    <tbody className="divide-y divide-border/60">
                      {data.provenance.inputObservations.map((obs) => (
                        <tr key={obs.observationId} className="hover:bg-surface/50 transition">
                          <td className="px-4 py-3 font-semibold text-accent">
                            {obs.role}
                          </td>
                          <td className="px-4 py-3 text-text-muted">
                            #{obs.observationId}
                          </td>
                          <td className="px-4 py-3 text-text-secondary">
                            {obs.effectiveDate}
                          </td>
                          <td className="px-4 py-3 text-text-muted">
                            v{obs.revisionSeq}
                          </td>
                          <td className="px-4 py-3 text-text-primary font-bold">
                            {obs.navValue !== null && obs.navValue !== undefined
                              ? Number(obs.navValue).toFixed(4)
                              : "—"}
                          </td>
                          <td className="px-4 py-3">
                            <DataQualityBadge type="assessment" status={obs.qualityAssessment} size="sm" />
                          </td>
                          <td className="px-4 py-3 text-text-muted">
                            {obs.temporalStatus}
                          </td>
                          <td className="px-4 py-3 font-mono text-[10px] text-text-muted">
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
              <div className="rounded-xl border border-border bg-surface-elevated p-5 font-mono text-xs space-y-3">
                <span className="font-semibold text-text-primary uppercase tracking-wider block">
                  Raw Ingestion Source Artifacts
                </span>
                {data.provenance.sourceArtifacts.map((art) => (
                  <div key={art.sourceArtifactId} className="rounded-lg border border-border bg-surface p-3 space-y-1">
                    <div className="flex justify-between">
                      <span className="text-text-secondary">Artifact #{art.sourceArtifactId}</span>
                      <span className="text-text-muted text-[10px]">{art.byteSize} bytes</span>
                    </div>
                    <div className="text-text-muted text-[11px] break-all">
                      SHA-256: <span className="text-text-primary font-mono select-all">{art.sha256Hash}</span>
                    </div>
                    <div className="text-text-muted text-[10px]">
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
