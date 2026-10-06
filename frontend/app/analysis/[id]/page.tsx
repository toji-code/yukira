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
import { YukiraScoreCard } from "@/components/analysis/YukiraScoreCard";
import { GroundedAiInterpretationPanel } from "@/components/analysis/GroundedAiInterpretationPanel";

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
            <button onClick={reloadAnalysis} className="btn btn-secondary">
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
            className="panel mt-6 space-y-4 p-4 md:p-5"
          >
            <div className="panel-header border-b border-border pb-3">
              <p className="eyebrow">Execute Parameterized Institutional Profile Run</p>
              <span className="mono-meta shrink-0">
                Option #{data.context.schemeOptionId} • 16 Deterministic Metrics
              </span>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="field-label" htmlFor="profile-analysis-cutoff">
                  Analysis Cutoff Date (T)
                </label>
                <input
                  id="profile-analysis-cutoff"
                  type="date"
                  value={customEndDate}
                  onChange={(e) => setCustomEndDate(e.target.value)}
                  required
                  className="field"
                />
              </div>
              <div>
                <label className="field-label" htmlFor="profile-knowledge-cutoff">
                  Knowledge Cutoff Time (ISO 8601)
                </label>
                <input
                  id="profile-knowledge-cutoff"
                  type="text"
                  value={customCutoff}
                  onChange={(e) => setCustomCutoff(e.target.value)}
                  required
                  className="field field-mono"
                />
              </div>
            </div>

            {customError && (
              <div className="state-panel-error">
                <p className="eyebrow text-critical-fg">Run Dispatch Failure</p>
                <p className="mt-1 text-[13px] leading-[1.5] text-critical-fg">{customError}</p>
              </div>
            )}

            <div className="flex flex-wrap items-center justify-end gap-2 pt-1">
              <button
                type="button"
                onClick={() => setShowParamPanel(false)}
                className="btn btn-ghost"
              >
                Cancel
              </button>
              <button type="submit" disabled={executingCustom} className="btn btn-primary">
                {executingCustom ? "Executing 16-Metric Profile..." : "Dispatch Profile Run"}
              </button>
            </div>
          </form>
        )}

        <InstitutionalProfileView data={data} onRerunRequested={() => setShowParamPanel(!showParamPanel)} />

        {data.context.schemeOptionId && (
          <div className="mt-8">
            <YukiraScoreCard
              schemeOptionId={data.context.schemeOptionId}
              calculationRunId={data.provenance.calculationRunId}
            />
          </div>
        )}

        <div className="mt-8">
          <GroundedAiInterpretationPanel runId={runId} />
        </div>
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
        return "Beta (3Y): slope coefficient from OLS regression of daily fund excess returns against benchmark excess returns.";
      case "REL-02":
        return "Tracking Error (3Y): annualized standard deviation of excess returns relative to benchmark.";
      case "REL-03":
        return "Jensen's Alpha (3Y): annualized intercept from single-index excess-return regression.";
      case "REL-04":
        return "Downside Beta (3Y): market sensitivity conditioned exclusively on negative benchmark trading days.";
      case "REL-05":
        return "Upside Beta (3Y): market sensitivity conditioned exclusively on positive benchmark trading days.";
      case "REL-06":
        return "Annualized Mean Active Return (3Y): mean daily active return scaled by 252 trading days.";
      case "RAT-04":
        return "Information Ratio (3Y): ratio of annualized mean active return to annualized tracking error.";
      case "MKT-01":
        return "Equity Beta (3Y): slope coefficient from single-index excess-return OLS regression against benchmark.";
      case "MKT-02":
        return "Downside Beta (3Y): market sensitivity conditioned exclusively on trading days where benchmark return is negative.";
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
      case "RAT-04":
        return "Candidate methodology. Undefined when tracking error is zero.";
      case "REL-01":
        return "Approved/Candidate methodology. Linear excess-return OLS model assuming constant market sensitivity.";
      case "REL-02":
        return "Candidate methodology. Benchmark tracking error subject to index synchronization tolerances.";
      case "REL-03":
        return "Candidate methodology. Jensen's Alpha assumes stationary single-index capital asset pricing dynamics.";
      case "REL-04":
        return "Candidate methodology. Requires minimum downside benchmark trading days.";
      case "REL-05":
        return "Candidate methodology. Requires minimum upside benchmark trading days.";
      case "REL-06":
        return "Candidate methodology. Mean active daily return scaled to annual horizon.";
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
          className="btn btn-secondary"
        >
          <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden>
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
          </svg>
          Fund Detail Workspace
        </Link>
      }
    >
      {/* 1. TOP STATUS & CONTROLS BAR */}
      <div className="mt-5 flex flex-wrap items-center justify-between gap-3 border-b border-border pb-3">
        <div className="flex flex-wrap items-center gap-2">
          <span className="eyebrow">Execution Status</span>
          <span
            className={`status-badge ${
              isCalculated
                ? "state-operational"
                : isInsufficient
                  ? "state-candidate"
                  : "state-critical"
            }`}
          >
            <span className="status-dot" aria-hidden />
            {data.result.calculationStatus}
          </span>

          <MethodologyBadge
            status={data.methodology.approvalStatus}
            convention={data.methodology.methodologyVersion}
          />
        </div>

        <button
          onClick={() => setShowParamPanel(!showParamPanel)}
          className="btn btn-secondary"
          aria-expanded={showParamPanel}
        >
          {showParamPanel ? "Close Parameter Controls" : "Re-run Parameterized Analysis"}
        </button>
      </div>

      {/* Parameterized Calculation Drawer */}
      {showParamPanel && (
        <form
          onSubmit={handleExecuteParameterizedRun}
          className="panel mt-5 space-y-4 p-4 md:p-5"
        >
          <div className="panel-header border-b border-border pb-3">
            <p className="eyebrow">Execute Parameterized Analysis Run</p>
            <span className="mono-meta shrink-0">
              Option #{data.identity.schemeOptionId} • Deterministic Execution
            </span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {!hasWindow && (
              <div>
                <label className="field-label" htmlFor="run-start-date">
                  Requested Start Date
                </label>
                <input
                  id="run-start-date"
                  type="date"
                  value={customStartDate}
                  onChange={(e) => setCustomStartDate(e.target.value)}
                  required
                  className="field"
                />
              </div>
            )}
            <div>
              <label className="field-label" htmlFor="run-end-date">
                {hasWindow ? "Analysis Cutoff Date (T)" : "Requested End Date"}
              </label>
              <input
                id="run-end-date"
                type="date"
                value={customEndDate}
                onChange={(e) => setCustomEndDate(e.target.value)}
                required
                className="field"
              />
            </div>
            <div>
              <label className="field-label" htmlFor="run-knowledge-cutoff">
                Knowledge Cutoff Time (ISO 8601)
              </label>
              <input
                id="run-knowledge-cutoff"
                type="text"
                value={customCutoff}
                onChange={(e) => setCustomCutoff(e.target.value)}
                required
                className="field field-mono"
              />
            </div>
          </div>

          {customError && (
            <div className="state-panel-error">
              <p className="eyebrow text-critical-fg">Run Dispatch Failure</p>
              <p className="mt-1 text-[13px] leading-[1.5] text-critical-fg">{customError}</p>
            </div>
          )}

          <div className="flex flex-wrap items-center justify-end gap-2 pt-1">
            <button
              type="button"
              onClick={() => setShowParamPanel(false)}
              className="btn btn-ghost"
            >
              Cancel
            </button>
            <button type="submit" disabled={executingCustom} className="btn btn-primary">
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
          <div className="space-y-4">
            {/* Primary KPI Card */}
            <div className="panel p-4 md:p-5">
              <div className="flex flex-col gap-6 lg:flex-row lg:items-start lg:justify-between">
                <div className="min-w-0 space-y-3">
                  <MetricValueDisplay
                    label={data.result.metricName}
                    value={data.result.numericValue}
                    units={data.result.units || "PERCENTAGE"}
                    isCandidate={data.methodology.approvalStatus !== "APPROVED"}
                    candidateConvention={data.methodology.methodologyVersion}
                    description={getMetricInterpretation(data.result.metricCode)}
                  />
                  <p className="mono-meta">
                    Engine Status:{" "}
                    <span className="text-text-primary">{data.result.calculationStatus}</span>
                  </p>
                </div>

                <div className="panel-inset w-full p-4 lg:max-w-[380px]">
                  <p className="def-label">
                    {hasWindow ? "36-Month Lookback Window" : "Evaluation Window"}
                  </p>
                  <div className="space-y-2">
                    {hasWindow && data.window ? (
                      <>
                        <div>
                          <p className="mono-meta">Target Range</p>
                          <p className="data-value-sm">
                            {data.window.requestedStartDate || "—"} → {data.window.requestedEndDate || "—"}
                          </p>
                        </div>
                        <div>
                          <p className="mono-meta">Resolved Dates</p>
                          <p className="data-value-sm text-accent">
                            {data.window.actualStartDate || "—"} → {data.window.actualEndDate || "—"}
                          </p>
                        </div>
                        <div>
                          <p className="mono-meta">Observation Count</p>
                          <p className="data-value-sm text-operational-fg">
                            {data.window.observationCount} days ({data.window.minObservationsRequired} required)
                          </p>
                        </div>
                      </>
                    ) : data.period ? (
                      <>
                        <div>
                          <p className="mono-meta">Requested Dates</p>
                          <p className="data-value-sm">
                            {data.period.requestedStartDate || "—"} → {data.period.requestedEndDate || "—"}
                          </p>
                        </div>
                        <div>
                          <p className="mono-meta">Resolved Dates</p>
                          <p className="data-value-sm text-accent">
                            {data.period.selectedStartDate || "—"} → {data.period.selectedEndDate || "—"}
                          </p>
                        </div>
                      </>
                    ) : null}
                  </div>
                </div>
              </div>

              {isInsufficient && data.result.errorMessage && (
                <div className="state-panel-error mt-4">
                  <p className="eyebrow text-candidate-fg">Evidence Limitation</p>
                  <p className="mt-1 text-[13px] leading-[1.5] text-candidate-fg">
                    {data.result.errorMessage}
                  </p>
                </div>
              )}
            </div>

            {/* Scheme Metadata Strip */}
            <div className="def-list def-list-4">
              <div>
                <p className="def-label">AMFI Code</p>
                <p className="def-value">{data.identity.amfiCode || "—"}</p>
              </div>
              <div>
                <p className="def-label">ISIN</p>
                <p className="def-value">{data.identity.isin || "—"}</p>
              </div>
              <div>
                <p className="def-label">Option Type</p>
                <p className="def-value">{data.identity.optionType}</p>
              </div>
              <div>
                <p className="def-label">Methodology Version</p>
                <p className="def-value text-candidate-fg">{data.methodology.methodologyVersion}</p>
              </div>
            </div>
          </div>
        }
        level2={
          <div className="space-y-4">
            <p className="eyebrow">Evidence Base &amp; Methodology Resolution</p>

            {/* Window / Period Evidence Details */}
            {hasWindow && data.window ? (
              <div className="panel p-4 md:p-5">
                <div className="panel-header border-b border-border pb-3">
                  <p className="eyebrow">36-Month Trading Continuity</p>
                  <span className="status-badge state-approved shrink-0">
                    <span className="status-dot" aria-hidden />
                    Sufficient History ({data.window.observationCount} &ge; {data.window.minObservationsRequired} required)
                  </span>
                </div>
                <div className="def-list def-list-3 mt-3">
                  <div>
                    <p className="def-label">Window Start</p>
                    <p className="def-value text-accent">{data.window.actualStartDate}</p>
                    <p className="mono-meta mt-1">Requested: {data.window.requestedStartDate}</p>
                  </div>
                  <div>
                    <p className="def-label">Window End (As-Of Cutoff)</p>
                    <p className="def-value text-accent">{data.window.actualEndDate}</p>
                    <p className="mono-meta mt-1">Requested: {data.window.requestedEndDate}</p>
                  </div>
                  <div>
                    <p className="def-label">Trading Continuity</p>
                    <p className="def-value">{data.window.observationCount} trading dates</p>
                    <p className="mono-meta mt-1">Zero synthetic imputation</p>
                  </div>
                </div>
              </div>
            ) : data.period ? (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="panel p-4 md:p-5">
                  <div className="panel-header border-b border-border pb-3">
                    <p className="eyebrow">Start Observation (NAV_start)</p>
                    <span className="status-badge state-unavailable shrink-0">
                      {data.period.startSubstituted
                        ? `${data.period.startLookbackDaysUsed}d lookback substitution`
                        : "Exact date match"}
                    </span>
                  </div>
                  <div className="mt-3 space-y-2">
                    <div className="flex items-baseline justify-between gap-3">
                      <span className="mono-meta">Requested Target</span>
                      <span className="data-value-sm">{data.period.requestedStartDate || "—"}</span>
                    </div>
                    <div className="flex items-baseline justify-between gap-3">
                      <span className="mono-meta">Selected Effective</span>
                      <span className="data-value-sm text-accent">{data.period.selectedStartDate || "—"}</span>
                    </div>
                    <div className="flex items-baseline justify-between gap-3">
                      <span className="mono-meta">Lookback Days Used</span>
                      <span className="data-value-sm">
                        {data.period.startLookbackDaysUsed} / 4 calendar days max
                      </span>
                    </div>
                  </div>
                </div>

                <div className="panel p-4 md:p-5">
                  <div className="panel-header border-b border-border pb-3">
                    <p className="eyebrow">End Observation (NAV_end)</p>
                    <span className="status-badge state-unavailable shrink-0">
                      {data.period.endSubstituted
                        ? `${data.period.endLookbackDaysUsed}d lookback substitution`
                        : "Exact date match"}
                    </span>
                  </div>
                  <div className="mt-3 space-y-2">
                    <div className="flex items-baseline justify-between gap-3">
                      <span className="mono-meta">Requested Target</span>
                      <span className="data-value-sm">{data.period.requestedEndDate || "—"}</span>
                    </div>
                    <div className="flex items-baseline justify-between gap-3">
                      <span className="mono-meta">Selected Effective</span>
                      <span className="data-value-sm text-accent">{data.period.selectedEndDate || "—"}</span>
                    </div>
                    <div className="flex items-baseline justify-between gap-3">
                      <span className="mono-meta">Lookback Days Used</span>
                      <span className="data-value-sm">
                        {data.period.endLookbackDaysUsed} / 4 calendar days max
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            ) : null}

            {/* Methodology Specification Panel */}
            <div className="panel p-4 md:p-5">
              <p className="eyebrow border-b border-border pb-3">
                Methodology Specification ({data.methodology.methodologyCode})
              </p>
              <div className="def-list mt-3">
                <div>
                  <p className="def-label">Formula</p>
                  <p className="def-value">{data.methodology.formulaDisclosure}</p>
                </div>
                {isRiskMetric && rskMethodology?.annualizationConvention ? (
                  <>
                    <div>
                      <p className="def-label">Annualization</p>
                      <p className="def-value text-candidate-fg">
                        {rskMethodology.annualizationConvention} (Candidate — not validated)
                      </p>
                    </div>
                    {rskMethodology.denominatorConvention && (
                      <div>
                        <p className="def-label">Denominator</p>
                        <p className="def-value text-candidate-fg">
                          {rskMethodology.denominatorConvention} (Candidate — not validated)
                        </p>
                      </div>
                    )}
                  </>
                ) : (
                  <div>
                    <p className="def-label">Window Rule</p>
                    <p className="def-value">
                      {data.methodology.lookbackSpecification ||
                        "36 calendar months candidate analytical window"}
                    </p>
                  </div>
                )}
                <div>
                  <p className="def-label">Knowledge Cutoff</p>
                  <p className="def-value">{formatDateTime(data.pit.knowledgeCutoffTime)}</p>
                </div>
              </div>
            </div>
          </div>
        }
        level3={
          <div className="space-y-4">
            <p className="eyebrow">Institutional Audit &amp; Deep Provenance</p>

            {/* 6-Dimension Quality States */}
            <div>
              <p className="eyebrow mb-3">Authoritative 6-Dimensional Data Quality States</p>
              <div className="grid grid-cols-2 gap-3 lg:grid-cols-3">
                {data.quality.dimensions.map((dim) => (
                  <div key={dim.dimension} className="metric-tile">
                    <p className="def-label">{dim.dimension}</p>
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
                    <p className="mono-meta mt-2 line-clamp-2" title={dim.description}>
                      {dim.description}
                    </p>
                  </div>
                ))}
              </div>
            </div>

            {/* Run Provenance Details */}
            <div className="panel p-4 md:p-5">
              <div className="panel-header border-b border-border pb-3">
                <p className="eyebrow">Calculation Run #{data.provenance.calculationRunId}</p>
                <span className="mono-meta shrink-0">
                  Started: {formatDateTime(data.provenance.executionStartedAt)}
                </span>
              </div>

              <div className="grid grid-cols-1 gap-4 pt-3 md:grid-cols-2">
                <div>
                  <p className="def-label">Input Snapshot SHA-256 Digest</p>
                  <p className="def-value select-all break-all">
                    {data.provenance.inputSnapshotSha256 || "—"}
                  </p>
                </div>
                <div>
                  <p className="def-label">Quantitative Engine Version</p>
                  <p className="def-value">{data.provenance.quantEngineVersion}</p>
                </div>
              </div>
            </div>

            {/* Input Observations Lineage Table */}
            <div>
              <p className="eyebrow mb-3">
                Authoritative Input Observations ({data.provenance.inputObservations.length} Lineage Records)
              </p>

              {data.provenance.inputObservations.length === 0 ? (
                <div className="state-well">
                  <p className="eyebrow">Zero Lineage Records</p>
                  <p className="mt-1 text-[13px] leading-[1.5] text-text-secondary">
                    Zero input observations recorded for this run.
                  </p>
                </div>
              ) : (
                <div className="panel scroll-region">
                  <table className="data-table">
                    <caption className="sr-only">
                      Authoritative input observation lineage for this calculation run
                    </caption>
                    <thead>
                      <tr>
                        <th scope="col">Role</th>
                        <th scope="col">Obs ID</th>
                        <th scope="col">Effective Date</th>
                        <th scope="col">Rev Seq</th>
                        <th scope="col" className="text-right">NAV Value (INR)</th>
                        <th scope="col">Quality</th>
                        <th scope="col">Temporal Status</th>
                        <th scope="col">Source Artifact Hash</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.provenance.inputObservations.map((obs) => (
                        <tr key={obs.observationId}>
                          <td className="key whitespace-nowrap font-medium text-accent">{obs.role}</td>
                          <td className="num text-left text-text-tertiary">#{obs.observationId}</td>
                          <td className="num text-left">{obs.effectiveDate}</td>
                          <td className="num text-left text-text-tertiary">v{obs.revisionSeq}</td>
                          <td className="num">
                            {obs.navValue !== null && obs.navValue !== undefined
                              ? Number(obs.navValue).toFixed(4)
                              : "Not available"}
                          </td>
                          <td>
                            <DataQualityBadge type="assessment" status={obs.qualityAssessment} size="sm" />
                          </td>
                          <td className="num text-left text-text-tertiary">{obs.temporalStatus}</td>
                          <td className="num text-left text-text-tertiary">
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
              <div className="panel p-4 md:p-5">
                <p className="eyebrow border-b border-border pb-3">
                  Raw Ingestion Source Artifacts
                </p>
                <div className="mt-3 space-y-3">
                  {data.provenance.sourceArtifacts.map((art) => (
                    <div key={art.sourceArtifactId} className="panel-inset p-3">
                      <div className="flex items-baseline justify-between gap-3">
                        <span className="data-value-sm">Artifact #{art.sourceArtifactId}</span>
                        <span className="mono-meta shrink-0">{art.byteSize} bytes</span>
                      </div>
                      <p className="mt-1 break-all">
                        <span className="mono-meta">SHA-256</span>{" "}
                        <span className="data-value-sm select-all break-all">{art.sha256Hash}</span>
                      </p>
                      <p className="mono-meta mt-1 break-all">
                        Source: {art.sourceUrl} | Retrieved: {formatDateTime(art.retrievalTimestamp)}
                      </p>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        }
      />

      {data.identity.schemeOptionId && (
        <div className="mt-8">
          <YukiraScoreCard schemeOptionId={data.identity.schemeOptionId} />
        </div>
      )}

      <div className="mt-8">
        <GroundedAiInterpretationPanel runId={runId} />
      </div>
    </PageContainer>
  );
}
