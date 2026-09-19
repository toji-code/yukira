"use client";

import { use, useEffect, useState, useMemo } from "react";
import { PageContainer } from "@/components/layout/PageContainer";
import { StateView } from "@/components/epistemic/StateView";
import { ProvenanceCard } from "@/components/epistemic/ProvenanceCard";
import { MethodologyBadge } from "@/components/epistemic/MethodologyBadge";
import { ProgressiveDisclosure } from "@/components/disclosure/ProgressiveDisclosure";
import { MetricValueDisplay } from "@/components/primitives/MetricValueDisplay";
import { MetricComparison } from "@/components/primitives/MetricComparison";
import { TimeSeriesPrimitive } from "@/components/primitives/TimeSeriesPrimitive";
import { DrawdownPrimitive } from "@/components/primitives/DrawdownPrimitive";
import { fetchCalculationRun, fetchRunMetricResults } from "@/lib/api/calculations";
import { CalculationRun, MetricResult } from "@/types/calculation";
import { formatDate, parseDiagnostics } from "@/lib/utils/formatters";

interface PageProps {
  params: Promise<{ id: string }>;
}

export default function CalculationAnalysisPage({ params }: PageProps) {
  const resolvedParams = use(params);
  const runId = parseInt(resolvedParams.id, 10);

  const [run, setRun] = useState<CalculationRun | null>(null);
  const [metrics, setMetrics] = useState<MetricResult[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const reloadData = () => {
    setLoading(true);
    setError(null);
    Promise.all([
      fetchCalculationRun(runId),
      fetchRunMetricResults(runId),
    ])
      .then(([runData, metricsData]) => {
        setRun(runData);
        setMetrics(metricsData);
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
    Promise.all([
      fetchCalculationRun(runId),
      fetchRunMetricResults(runId),
    ])
      .then(([runData, metricsData]) => {
        if (active) {
          setRun(runData);
          setMetrics(metricsData);
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

  // Index metrics by code for rapid lookup
  const metricsMap = useMemo(() => {
    const map: Record<string, MetricResult> = {};
    for (const m of metrics) {
      map[m.metricCode] = m;
    }
    return map;
  }, [metrics]);

  if (loading) {
    return (
      <PageContainer title="Quantitative Analysis">
        <StateView
          kind="loading"
          title="Retrieving Quantitative Audit"
          message={`Fetching calculation run #${runId} and verified Quant Engine metric results...`}
        />
      </PageContainer>
    );
  }

  if (error || !run) {
    return (
      <PageContainer title="Run Unavailable">
        <StateView
          kind="unavailable"
          title="Calculation Run Unavailable"
          message={error || `Calculation Run #${runId} could not be retrieved from the backend.`}
          action={
            <button
              onClick={reloadData}
              className="inline-flex rounded-md bg-zinc-800 px-3 py-1.5 text-xs font-medium text-zinc-200 hover:bg-zinc-700"
            >
              Try Again
            </button>
          }
        />
      </PageContainer>
    );
  }

  // Scheme and option info
  const optionName = run.schemeOption?.optionType
    ? `Option #${run.schemeOption.id} (${run.schemeOption.optionType})`
    : `Option #${run.schemeOptionId || run.schemeOption?.id || "—"}`;
  const schemeName = run.schemeOption?.plan?.scheme?.name || "Target Scheme Option";
  const benchmarkName = run.benchmark?.name || run.benchmark?.benchmarkCode || "Mandated Benchmark";

  return (
    <PageContainer
      title={`Analysis Run #${run.id}`}
      subtitle={`${schemeName} — ${optionName}`}
      breadcrumbs={[
        { label: "Funds", href: "/funds" },
        { label: `Run #${run.id}`, href: `/analysis/${run.id}` },
      ]}
    >
      {/* Top Provenance & Status Header */}
      <div className="mb-8 grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <ProvenanceCard run={run} />
        </div>

        {/* Methodology Version Card */}
        <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-6 backdrop-blur-sm">
          <h3 className="text-xs font-mono uppercase tracking-wider text-zinc-400">
            Methodology Framework
          </h3>
          <div className="mt-3 flex items-center justify-between">
            <span className="font-mono text-sm font-semibold text-zinc-200">
              {run.methodologyVersion?.versionTag || "CANDIDATE-V1"}
            </span>
            <MethodologyBadge
              status={run.methodologyVersion?.approvalStatus || "CANDIDATE"}
              convention={run.methodologyVersion?.versionTag}
            />
          </div>
          <p className="mt-2 text-xs leading-relaxed text-zinc-400">
            Standard candidate quantitative parameter suite. All conventions and risk-free curves are subject to empirical review.
          </p>
          <div className="mt-4 border-t border-zinc-800/80 pt-3 text-[11px] font-mono text-zinc-500">
            <div>Engine Hash: {run.engineSoftwareVersion?.substring(0, 10) || "untracked"}</div>
            <div className="mt-0.5">Calculated: {formatDate(run.executionStartedAt)}</div>
          </div>
        </div>
      </div>

      {/* Epistemic Alert */}
      <div className="mb-8 rounded-lg border border-amber-500/20 bg-amber-500/5 px-4 py-3 text-xs text-zinc-300">
        <span className="font-semibold text-amber-400">EPISTEMIC GOVERNANCE:</span> All quantitative values below are candidate indicators calculated via the Python Quant Engine. Project YUKIRA has zero empirical findings and an empty approved production methodology.
      </div>

      {/* 3-Level Progressive Disclosure */}
      <ProgressiveDisclosure
        level1={
          <div className="space-y-8">
            {/* Primary Return & Risk KPI Grid */}
            <div>
              <h4 className="text-xs font-mono uppercase tracking-wider text-zinc-400">
                Core Candidate Return & Volatility Indicators
              </h4>
              <div className="mt-3 grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
                <MetricValueDisplay
                  label="CAGR (3Y)"
                  value={metricsMap["RET-01"]?.numericValue ?? metricsMap["CAGR"]?.numericValue}
                  units="PERCENTAGE"
                  isCandidate={true}
                  description="Compound Annual Growth Rate over evaluated window."
                />
                <MetricValueDisplay
                  label="Annualized Volatility"
                  value={metricsMap["RSK-01"]?.numericValue ?? metricsMap["VOLATILITY"]?.numericValue}
                  units="PERCENTAGE"
                  isCandidate={true}
                  description="Annualized standard deviation of returns (252 trading days)."
                />
                <MetricValueDisplay
                  label="Sharpe Ratio (Candidate)"
                  value={metricsMap["RSK-02"]?.numericValue ?? metricsMap["SHARPE"]?.numericValue}
                  units="RATIO"
                  isCandidate={true}
                  description="Excess return per unit of volatility against candidate risk-free curve."
                />
                <MetricValueDisplay
                  label="Maximum Drawdown"
                  value={metricsMap["RSK-03"]?.numericValue ?? metricsMap["MAX_DRAWDOWN"]?.numericValue}
                  units="PERCENTAGE"
                  isCandidate={true}
                  description="Peak-to-trough maximum observed decline over evaluation horizon."
                />
              </div>
            </div>

            {/* Benchmark Relative Risk Indicators */}
            <div>
              <h4 className="text-xs font-mono uppercase tracking-wider text-zinc-400">
                Benchmark Relative Characteristics vs. {benchmarkName}
              </h4>
              <div className="mt-3 grid grid-cols-1 gap-4 md:grid-cols-3">
                <MetricComparison
                  title="Beta to Benchmark"
                  metricCode="RSK-04"
                  schemeValue={metricsMap["RSK-04"]?.numericValue ?? metricsMap["BETA"]?.numericValue}
                  benchmarkValue={1.0}
                  units="RATIO"
                  schemeLabel={optionName}
                  benchmarkLabel={benchmarkName}
                />
                <MetricComparison
                  title="Alpha (Candidate)"
                  metricCode="RET-03"
                  schemeValue={metricsMap["RET-03"]?.numericValue ?? metricsMap["ALPHA"]?.numericValue}
                  benchmarkValue={0.0}
                  units="PERCENTAGE"
                  schemeLabel={optionName}
                  benchmarkLabel={benchmarkName}
                />
                <MetricComparison
                  title="Tracking Error"
                  metricCode="RSK-05"
                  schemeValue={metricsMap["RSK-05"]?.numericValue ?? metricsMap["TRACKING_ERROR"]?.numericValue}
                  benchmarkValue={0.0}
                  units="PERCENTAGE"
                  schemeLabel={optionName}
                  benchmarkLabel={benchmarkName}
                />
              </div>
            </div>
          </div>
        }
        level2={
          <div className="space-y-8">
            <div>
              <h4 className="text-xs font-mono uppercase tracking-wider text-zinc-400">
                Evidence & Calculation Conventions
              </h4>
              <p className="mt-1 text-xs text-zinc-400">
                Mathematical conventions, observation window bounds, and quantitative parameter settings applied during this orchestrator execution.
              </p>

              <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-3 font-mono text-xs">
                <div className="rounded-lg border border-zinc-800 bg-zinc-950/60 p-4">
                  <div className="text-zinc-500">{"// Annualization Convention"}</div>
                  <div className="mt-1 font-semibold text-zinc-200">252 Trading Days</div>
                  <div className="mt-1 text-zinc-400">sqrt(252) for standard deviation</div>
                </div>
                <div className="rounded-lg border border-zinc-800 bg-zinc-950/60 p-4">
                  <div className="text-zinc-500">{"// Return Compounding"}</div>
                  <div className="mt-1 font-semibold text-zinc-200">Discrete / Simple Returns</div>
                  <div className="mt-1 text-zinc-400">r_t = (P_t / P_t-1) - 1</div>
                </div>
                <div className="rounded-lg border border-zinc-800 bg-zinc-950/60 p-4">
                  <div className="text-zinc-500">{"// As-Of Date"}</div>
                  <div className="mt-1 font-semibold text-zinc-200">{run.asOfDate}</div>
                  <div className="mt-1 text-zinc-400">Cutoff: {formatDate(run.knowledgeCutoffTime)}</div>
                </div>
              </div>
            </div>

            {/* Visual Primitives Demo */}
            <div>
              <h4 className="text-xs font-mono uppercase tracking-wider text-zinc-400">
                Visual Analytics Primitives (Native SVG)
              </h4>
              <div className="mt-3 grid grid-cols-1 gap-6 lg:grid-cols-2">
                <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-5">
                  <TimeSeriesPrimitive
                    title="Historical NAV Trajectory"
                    data={[]}
                    height={200}
                    valueLabel="INR"
                  />
                </div>
                <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-5">
                  <DrawdownPrimitive
                    title="Underwater Drawdown Profile"
                    data={[]}
                    height={200}
                  />
                </div>
              </div>
            </div>
          </div>
        }
        level3={
          <div className="space-y-6">
            <div>
              <h4 className="text-xs font-mono uppercase tracking-wider text-zinc-400">
                Institutional Metric Audit Table ({metrics.length} Verified Records)
              </h4>
              <p className="mt-1 text-xs text-zinc-400">
                Complete record of authoritative outputs from the Python Quant Engine stored in the PostgreSQL database.
              </p>
            </div>

            {metrics.length === 0 ? (
              <StateView
                kind="empty"
                title="Zero Metric Results"
                message="No metric results were stored for this calculation run. Check calculation run logs or error messages."
              />
            ) : (
              <div className="overflow-x-auto rounded-xl border border-zinc-800 bg-zinc-900/60">
                <table className="w-full text-left text-xs">
                  <thead className="border-b border-zinc-800 bg-zinc-950/60 font-mono uppercase tracking-wider text-zinc-400">
                    <tr>
                      <th className="px-4 py-3">Metric Code</th>
                      <th className="px-4 py-3">Period</th>
                      <th className="px-4 py-3">Raw Value</th>
                      <th className="px-4 py-3">Units</th>
                      <th className="px-4 py-3">Calculation Status</th>
                      <th className="px-4 py-3">Diagnostics</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-zinc-800/60 font-mono">
                    {metrics.map((m) => {
                      const diag = parseDiagnostics(m.diagnostics);
                      return (
                        <tr key={m.id} className="transition hover:bg-zinc-800/30">
                          <td className="px-4 py-3 font-semibold text-cyan-400">
                            {m.metricCode}
                          </td>
                          <td className="px-4 py-3 text-zinc-400">
                            {m.periodType || "—"}
                          </td>
                          <td className="px-4 py-3 text-zinc-200">
                            {m.numericValue !== null && m.numericValue !== undefined
                              ? m.numericValue.toFixed(6)
                              : "—"}
                          </td>
                          <td className="px-4 py-3 text-zinc-400">
                            {m.units || "—"}
                          </td>
                          <td className="px-4 py-3">
                            <span
                              className={`inline-flex rounded px-1.5 py-0.5 text-[10px] font-medium ${
                                m.calculationStatus === "CALCULATED"
                                  ? "bg-emerald-500/20 text-emerald-400 border border-emerald-500/30"
                                  : "bg-amber-500/20 text-amber-400 border border-amber-500/30"
                              }`}
                            >
                              {m.calculationStatus}
                            </span>
                          </td>
                          <td className="px-4 py-3 text-[11px] text-zinc-500">
                            {diag ? JSON.stringify(diag) : "—"}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        }
      />
    </PageContainer>
  );
}
