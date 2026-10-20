"use client";

import React, { useState, useEffect, useMemo, useCallback } from "react";
import { fetchBetaDynamics } from "@/lib/api/analysis";
import {
  BetaDynamicsResponse,
  RollingBetaPoint,
} from "@/types/analysis";

interface BenchmarkBetaDynamicsProps {
  schemeOptionId: number;
  schemeCode?: string;
  asOfDate?: string;
  knowledgeCutoffTime?: string;
  onInvestigate?: (question: string, reason?: string) => void;
}

type ActiveViewTab = "chart" | "regimes" | "stability";

export function BenchmarkBetaDynamics({
  schemeOptionId,
  schemeCode,
  asOfDate = "2024-01-15",
  knowledgeCutoffTime = "2024-01-31T23:59:59+05:30",
  onInvestigate,
}: BenchmarkBetaDynamicsProps) {
  const [data, setData] = useState<BetaDynamicsResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const [horizon, setHorizon] = useState<"3Y" | "1Y">("3Y");
  const [activeTab, setActiveTab] = useState<ActiveViewTab>("chart");
  const [showDownsideLine, setShowDownsideLine] = useState<boolean>(true);
  const [showUpsideLine, setShowUpsideLine] = useState<boolean>(true);
  const [hoveredPoint, setHoveredPoint] = useState<RollingBetaPoint | null>(null);
  const [showAuditLineage, setShowAuditLineage] = useState<boolean>(false);
  const [showEpistemic, setShowEpistemic] = useState<boolean>(true);

  useEffect(() => {
    let active = true;

    fetchBetaDynamics(schemeOptionId, asOfDate, knowledgeCutoffTime, undefined, horizon)
      .then((res) => {
        if (active) {
          setData(res);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (active) {
          setError(err instanceof Error ? err.message : "Failed to load beta dynamics analysis.");
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [schemeOptionId, asOfDate, knowledgeCutoffTime, horizon]);

  const formatBeta = (val: number | null | undefined, digits: number = 4) => {
    if (val === null || val === undefined || isNaN(Number(val))) return "—";
    return Number(val).toFixed(digits);
  };

  const formatPct = (val: number | null | undefined) => {
    if (val === null || val === undefined || isNaN(Number(val))) return "—";
    return `${Number(val).toFixed(2)}%`;
  };

  // SVG Chart Dimensions & Coordinate Mapping
  const chartWidth = 650;
  const chartHeight = 220;
  const padding = useMemo(() => ({ top: 20, right: 30, bottom: 35, left: 45 }), []);

  const rollingSeries = data?.rollingSeries;
  const validSeries = useMemo(() => {
    if (!rollingSeries) return [];
    return rollingSeries.filter((p) => p.beta !== null);
  }, [rollingSeries]);

  const { minVal, maxVal, parityY } = useMemo(() => {
    if (!validSeries || validSeries.length === 0) {
      return { minVal: 0.5, maxVal: 1.5, parityY: 110 };
    }
    let min = 0.5;
    let max = 1.5;
    for (const p of validSeries) {
      if (p.beta !== null) {
        if (p.beta < min) min = p.beta;
        if (p.beta > max) max = p.beta;
      }
      if (p.downsideBeta !== null) {
        if (p.downsideBeta < min) min = p.downsideBeta;
        if (p.downsideBeta > max) max = p.downsideBeta;
      }
      if (p.upsideBeta !== null) {
        if (p.upsideBeta < min) min = p.upsideBeta;
        if (p.upsideBeta > max) max = p.upsideBeta;
      }
    }
    // Add margin
    min = Math.max(0.0, Math.floor(min * 10) / 10 - 0.1);
    max = Math.ceil(max * 10) / 10 + 0.1;
    if (max - min < 0.4) {
      max = min + 0.5;
    }

    const plotHeight = chartHeight - padding.top - padding.bottom;
    const pY = padding.top + ((max - 1.0) / (max - min)) * plotHeight;
    return { minVal: min, maxVal: max, parityY: Math.max(padding.top, Math.min(chartHeight - padding.bottom, pY)) };
  }, [validSeries, chartHeight, padding]);

  const getY = useCallback((val: number) => {
    const plotHeight = chartHeight - padding.top - padding.bottom;
    return padding.top + ((maxVal - val) / (maxVal - minVal)) * plotHeight;
  }, [chartHeight, padding, maxVal, minVal]);

  const getX = useCallback((idx: number, total: number) => {
    const plotWidth = chartWidth - padding.left - padding.right;
    if (total <= 1) return padding.left + plotWidth / 2;
    return padding.left + (idx / (total - 1)) * plotWidth;
  }, [chartWidth, padding]);

  const betaPath = useMemo(() => {
    if (validSeries.length < 2) return "";
    return validSeries
      .map((p, i) => `${i === 0 ? "M" : "L"} ${getX(i, validSeries.length).toFixed(1)} ${getY(p.beta!).toFixed(1)}`)
      .join(" ");
  }, [validSeries, getX, getY]);

  const downBetaPath = useMemo(() => {
    const downPoints = validSeries.filter((p) => p.downsideBeta !== null);
    if (downPoints.length < 2) return "";
    return downPoints
      .map((p, i) => `${i === 0 ? "M" : "L"} ${getX(validSeries.indexOf(p), validSeries.length).toFixed(1)} ${getY(p.downsideBeta!).toFixed(1)}`)
      .join(" ");
  }, [validSeries, getX, getY]);

  const upBetaPath = useMemo(() => {
    const upPoints = validSeries.filter((p) => p.upsideBeta !== null);
    if (upPoints.length < 2) return "";
    return upPoints
      .map((p, i) => `${i === 0 ? "M" : "L"} ${getX(validSeries.indexOf(p), validSeries.length).toFixed(1)} ${getY(p.upsideBeta!).toFixed(1)}`)
      .join(" ");
  }, [validSeries, getX, getY]);

  if (loading) {
    return (
      <div className="panel space-y-4 p-4 md:p-5" id="beta-dynamics" aria-busy="true">
        <div className="skeleton h-3 w-1/3" />
        <div className="skeleton h-8 w-2/3" />
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <div className="skeleton h-28" />
          <div className="skeleton h-28" />
          <div className="skeleton h-28" />
          <div className="skeleton h-28" />
        </div>
        <div className="skeleton h-64 w-full" />
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="state-panel-error" id="beta-dynamics" role="alert">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="text-[15px] font-semibold text-text-primary">
            Benchmark Beta Dynamics &amp; Exposure Stability Unavailable
          </p>
        </div>
        <p className="mt-1 text-[13px] leading-[1.5] text-critical-fg">
          {error || "Unable to compute rolling beta series from verified ledger observations."}
        </p>
        <p className="mono-meta mt-2 text-text-secondary">
          Historical cutoff: {asOfDate} · Knowledge cutoff: {knowledgeCutoffTime}
        </p>
      </div>
    );
  }

  const { context, metrics, stability, regimes = [], epistemic } = data;

  const parityWidth = (1.0 / 2.0) * 100;
  const betaBarWidth = (val: number | null | undefined) => {
    if (val === null || val === undefined) return 0;
    return Math.min(100, Math.max(4, (Math.abs(val) / 2.0) * 100));
  };

  // Rule-based diagnostic observations
  const stabilityAssessment = (() => {
    if (!stability || stability.validWindows === 0) {
      return "Insufficient rolling windows to evaluate long-term beta stability.";
    }
    const iqr = stability.iqrBeta !== null ? Number(stability.iqrBeta) : null;
    const change = stability.betaChange !== null ? Math.abs(Number(stability.betaChange)) : 0;
    if (iqr !== null && iqr < 0.10 && change < 0.15) {
      return "High Historical Stability: Systematic beta has moved in a tight band across rolling windows with low dispersion.";
    } else if (iqr !== null && iqr > 0.25) {
      return "Regime Sensitive: Systematic beta displays wide dispersion across cycles, indicating shifting portfolio beta or sector tilt.";
    }
    return "Moderate Cyclical Variation: Systematic beta fluctuates moderately around its central median across market environments.";
  })();

  const asymmetryAssessment = (() => {
    if (metrics.upsideBeta !== null && metrics.downsideBeta !== null) {
      const spread = Number(metrics.upsideBeta) - Number(metrics.downsideBeta);
      if (spread > 0.05) {
        return `Positive Participation Asymmetry (+${spread.toFixed(4)}): The fund participated more heavily in benchmark advances (β_up = ${formatBeta(metrics.upsideBeta)}) than declines (β_down = ${formatBeta(metrics.downsideBeta)}).`;
      } else if (spread < -0.05) {
        return `Downside Elasticity Alert (${spread.toFixed(4)}): The fund exhibited higher sensitivity during benchmark sell-offs (β_down = ${formatBeta(metrics.downsideBeta)}) than during market advances (β_up = ${formatBeta(metrics.upsideBeta)}).`;
      }
      return `Symmetric Participation (Spread ${spread.toFixed(4)}): Market sensitivity was roughly balanced between advances and declines.`;
    }
    return "Conditional asymmetry requires sufficient positive (≥100) and negative (≥100) benchmark trading days.";
  })();

  return (
    <section className="panel p-4 md:p-6" id="beta-dynamics">
      {/* ── HEADER ──────────────────────────────────────────────────────── */}
      <div className="panel-header border-b border-border pb-4">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <span className="eyebrow text-accent">Systematic Risk &amp; Co-movement</span>
            <span className="status-badge state-approved">
              <span className="status-dot" aria-hidden />
              {epistemic.dataQualityStatus}
            </span>
            <span className="status-badge state-candidate">§MKT-01 / §MKT-02 / §MKT-06</span>
          </div>
          <h2 className="mt-1.5 text-h2 text-text-primary">
            Rolling Market Beta &amp; Exposure Stability
          </h2>
          <p className="mono-meta mt-1 font-sans">
            Deterministic systematic sensitivity vs {context.benchmarkName} · Synchronous daily alignment · No synthetic interpolation
          </p>
        </div>

        {/* Horizon Toggle */}
        <div className="flex flex-wrap items-center gap-2 pt-2 sm:pt-0">
          <span className="mono-meta mr-1">Rolling Window:</span>
          <div className="inline-flex rounded-sm border border-border p-0.5 bg-surface-inset">
            <button
              type="button"
              onClick={() => setHorizon("3Y")}
              className={`px-3 py-1 text-[12px] font-medium transition-colors ${
                horizon === "3Y"
                  ? "bg-accent text-navy-950 font-semibold rounded-xs shadow-xs"
                  : "text-text-secondary hover:text-text-primary"
              }`}
            >
              36M (Primary)
            </button>
            <button
              type="button"
              onClick={() => setHorizon("1Y")}
              className={`px-3 py-1 text-[12px] font-medium transition-colors ${
                horizon === "1Y"
                  ? "bg-accent text-navy-950 font-semibold rounded-xs shadow-xs"
                  : "text-text-secondary hover:text-text-primary"
              }`}
            >
              12M (Supporting)
            </button>
          </div>
        </div>
      </div>

      {/* ── 1. EXECUTIVE METRIC TILES ───────────────────────────────────── */}
      <div className="grid grid-cols-1 gap-4 pt-5 sm:grid-cols-2 lg:grid-cols-4">
        {/* Metric 1: Standard Beta */}
        <div className="metric-tile">
          <div className="panel-header">
            <p className="def-label mb-0">Standard Beta ({horizon})</p>
            <span className="status-badge state-candidate shrink-0">§MKT-01</span>
          </div>
          <div className="mt-2">
            <p className="data-value-lg text-text-primary">{formatBeta(metrics.standardBeta)}</p>
            <p className="mono-meta mt-1 font-sans">
              Full-sample linear sensitivity vs {context.benchmarkName}. β = 1.0 indicates market parity.
            </p>
          </div>
          <div className="mt-3">
            <div className="relative h-2 w-full overflow-hidden rounded-xs border border-border bg-surface-inset">
              <div
                className="absolute top-0 bottom-0 z-10 w-0.5 bg-text-tertiary"
                style={{ left: `${parityWidth}%` }}
                title="Market Parity (β = 1.0)"
              />
              <div
                className="h-full bg-accent transition-all"
                style={{ width: `${betaBarWidth(metrics.standardBeta)}%` }}
              />
            </div>
            <div className="flex justify-between mt-1 text-[10px] text-text-tertiary">
              <span>0.0</span>
              <span className="text-text-secondary font-medium">1.0 Parity</span>
              <span>2.0+</span>
            </div>
          </div>
        </div>

        {/* Metric 2: Downside Beta */}
        <div className="metric-tile metric-risk-rule">
          <div className="panel-header">
            <p className="def-label mb-0">Downside Beta (Rb &lt; 0)</p>
            <span className="status-badge state-candidate shrink-0">§MKT-02</span>
          </div>
          <div className="mt-2">
            <p className="data-value-lg text-risk-fg">{formatBeta(metrics.downsideBeta)}</p>
            <p className="mono-meta mt-1 font-sans">
              Sensitivity conditioned exclusively on {metrics.downDaysCount} market decline days (threshold: ≥{metrics.minDownRequired}).
            </p>
          </div>
          <div className="mt-3">
            <div className="relative h-2 w-full overflow-hidden rounded-xs border border-border bg-surface-inset">
              <div
                className="absolute top-0 bottom-0 z-10 w-0.5 bg-text-tertiary"
                style={{ left: `${parityWidth}%` }}
                title="Market Parity (β = 1.0)"
              />
              <div
                className="h-full bg-risk-fg transition-all"
                style={{ width: `${betaBarWidth(metrics.downsideBeta)}%` }}
              />
            </div>
            <div className="flex justify-between mt-1 text-[10px] text-text-tertiary">
              <span>0.0 (immune)</span>
              <span className="text-text-secondary font-medium">1.0 Parity</span>
              <span>2.0 (fragile)</span>
            </div>
          </div>
        </div>

        {/* Metric 3: Upside Beta */}
        <div className="metric-tile">
          <div className="panel-header">
            <p className="def-label mb-0">Upside Beta (Rb &gt; 0)</p>
            <span className="status-badge state-candidate shrink-0">Candidate</span>
          </div>
          <div className="mt-2">
            <p className="data-value-lg text-approved-fg">{formatBeta(metrics.upsideBeta)}</p>
            <p className="mono-meta mt-1 font-sans">
              Participation conditioned on {metrics.upDaysCount} positive benchmark advance days (threshold: ≥{metrics.minUpRequired}).
            </p>
          </div>
          <div className="mt-3">
            <div className="relative h-2 w-full overflow-hidden rounded-xs border border-border bg-surface-inset">
              <div
                className="absolute top-0 bottom-0 z-10 w-0.5 bg-text-tertiary"
                style={{ left: `${parityWidth}%` }}
                title="Market Parity (β = 1.0)"
              />
              <div
                className="h-full bg-approved-fg transition-all"
                style={{ width: `${betaBarWidth(metrics.upsideBeta)}%` }}
              />
            </div>
            <div className="flex justify-between mt-1 text-[10px] text-text-tertiary">
              <span>0.0 (lagging)</span>
              <span className="text-text-secondary font-medium">1.0 Parity</span>
              <span>2.0 (amplifying)</span>
            </div>
          </div>
        </div>

        {/* Metric 4: Beta Asymmetry Spread */}
        <div className="metric-tile">
          <div className="panel-header">
            <p className="def-label mb-0">Beta Asymmetry Spread</p>
            <span className="status-badge state-candidate shrink-0">β_up - β_down</span>
          </div>
          <div className="mt-2">
            {metrics.betaAsymmetrySpread !== null ? (
              <p
                className={`data-value-lg ${
                  metrics.betaAsymmetrySpread >= 0 ? "text-approved-fg" : "text-risk-fg"
                }`}
              >
                {metrics.betaAsymmetrySpread >= 0 ? "+" : ""}
                {formatBeta(metrics.betaAsymmetrySpread)}
              </p>
            ) : (
              <p className="data-unavailable">Insufficient Data</p>
            )}
            <p className="mono-meta mt-1 font-sans">
              Difference between advance sensitivity and decline sensitivity. Positive spread reflects favorable asymmetry.
            </p>
          </div>
          <div className="mt-3 border-t border-border pt-2 text-[11px] text-text-secondary">
            {metrics.totalPairedDays} paired trading days ({metrics.flatDaysCount} flat days excluded)
          </div>
        </div>
      </div>

      {/* ── 2. VIEW TABS ─────────────────────────────────────────────────── */}
      <div className="tab-rail mt-6" role="tablist" aria-label="Beta Dynamics views">
        <button
          type="button"
          role="tab"
          onClick={() => setActiveTab("chart")}
          data-active={activeTab === "chart"}
          aria-selected={activeTab === "chart"}
          className="tab"
        >
          <span className="tab-ordinal">01</span>
          <span>Rolling Beta Dynamics Chart</span>
        </button>
        <button
          type="button"
          role="tab"
          onClick={() => setActiveTab("stability")}
          data-active={activeTab === "stability"}
          aria-selected={activeTab === "stability"}
          className="tab"
        >
          <span className="tab-ordinal">02</span>
          <span>Exposure Stability Summary</span>
        </button>
        <button
          type="button"
          role="tab"
          onClick={() => setActiveTab("regimes")}
          data-active={activeTab === "regimes"}
          aria-selected={activeTab === "regimes"}
          className="tab"
        >
          <span className="tab-ordinal">03</span>
          <span>Market Regime Sensitivity</span>
        </button>
      </div>

      {/* ── TAB 1: ROLLING BETA CHART ────────────────────────────────────── */}
      {activeTab === "chart" && (
        <div className="panel-inset mt-4 p-4 md:p-5">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border pb-3">
            <div>
              <h3 className="text-[14px] font-semibold text-text-primary">
                Rolling {horizon} Systematic Sensitivity Path
              </h3>
              <p className="mono-meta mt-0.5 font-sans">
                {validSeries.length} chronological rolling windows · Hover to inspect point-in-time co-movement
              </p>
            </div>

            {/* Series Toggles */}
            <div className="flex flex-wrap items-center gap-3 text-[12px]">
              <span className="inline-flex items-center gap-1.5 font-medium text-accent">
                <span className="inline-block h-2.5 w-2.5 rounded-full bg-accent" />
                Full Beta (§MKT-01)
              </span>
              <button
                type="button"
                onClick={() => setShowDownsideLine((prev) => !prev)}
                className={`inline-flex items-center gap-1.5 font-medium transition-opacity ${
                  showDownsideLine ? "text-risk-fg opacity-100" : "text-text-tertiary opacity-50"
                }`}
              >
                <span className="inline-block h-2 w-2 rounded-xs border border-risk-fg bg-risk-fg/30" />
                Downside Beta (Rb &lt; 0)
              </button>
              <button
                type="button"
                onClick={() => setShowUpsideLine((prev) => !prev)}
                className={`inline-flex items-center gap-1.5 font-medium transition-opacity ${
                  showUpsideLine ? "text-approved-fg opacity-100" : "text-text-tertiary opacity-50"
                }`}
              >
                <span className="inline-block h-2 w-2 rounded-xs border border-approved-fg bg-approved-fg/30" />
                Upside Beta (Rb &gt; 0)
              </button>
            </div>
          </div>

          {/* SVG Chart */}
          {validSeries.length > 0 ? (
            <div className="mt-4 relative overflow-x-auto">
              <svg
                viewBox={`0 0 ${chartWidth} ${chartHeight}`}
                className="w-full h-auto min-w-[550px] select-none"
                style={{ maxHeight: "280px" }}
              >
                {/* Y-Axis Gridlines & Labels */}
                {[minVal, (minVal + 1.0) / 2, 1.0, (1.0 + maxVal) / 2, maxVal].map((tick, i) => {
                  const y = getY(tick);
                  return (
                    <g key={i}>
                      <line
                        x1={padding.left}
                        y1={y}
                        x2={chartWidth - padding.right}
                        y2={y}
                        stroke="currentColor"
                        strokeOpacity="0.1"
                        strokeDasharray="2,2"
                      />
                      <text
                        x={padding.left - 8}
                        y={y + 3}
                        textAnchor="end"
                        className="text-[10px] fill-text-tertiary font-mono"
                      >
                        {tick.toFixed(2)}
                      </text>
                    </g>
                  );
                })}

                {/* Market Parity Baseline (β = 1.00) */}
                <line
                  x1={padding.left}
                  y1={parityY}
                  x2={chartWidth - padding.right}
                  y2={parityY}
                  stroke="#D4AF37"
                  strokeWidth="1.5"
                  strokeDasharray="4,4"
                  strokeOpacity="0.75"
                />
                <text
                  x={chartWidth - padding.right}
                  y={parityY - 4}
                  textAnchor="end"
                  className="text-[10px] fill-accent font-semibold tracking-wide"
                >
                  Market Parity (β = 1.00)
                </text>

                {/* Upside Beta Polyline */}
                {showUpsideLine && upBetaPath && (
                  <path
                    d={upBetaPath}
                    fill="none"
                    stroke="#10B981"
                    strokeWidth="1.5"
                    strokeDasharray="3,3"
                    strokeOpacity="0.8"
                  />
                )}

                {/* Downside Beta Polyline */}
                {showDownsideLine && downBetaPath && (
                  <path
                    d={downBetaPath}
                    fill="none"
                    stroke="#F43F5E"
                    strokeWidth="1.5"
                    strokeDasharray="3,3"
                    strokeOpacity="0.85"
                  />
                )}

                {/* Full Standard Beta Polyline */}
                {betaPath && (
                  <path
                    d={betaPath}
                    fill="none"
                    stroke="#D4AF37"
                    strokeWidth="2.5"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                )}

                {/* Interactive Points & Hit Targets */}
                {validSeries.map((p, i) => {
                  const x = getX(i, validSeries.length);
                  const y = getY(p.beta!);
                  const isHovered = hoveredPoint?.windowEnd === p.windowEnd;
                  return (
                    <g key={i}>
                      <circle
                        cx={x}
                        cy={y}
                        r={isHovered ? 4.5 : 2}
                        className={isHovered ? "fill-accent stroke-surface stroke-2" : "fill-accent/70"}
                      />
                      {/* Transparent hit area for hover */}
                      <rect
                        x={x - 8}
                        y={padding.top}
                        width="16"
                        height={chartHeight - padding.top - padding.bottom}
                        fill="transparent"
                        className="cursor-pointer"
                        onMouseEnter={() => setHoveredPoint(p)}
                        onMouseLeave={() => setHoveredPoint(null)}
                      />
                    </g>
                  );
                })}

                {/* X-Axis Dates */}
                {validSeries.length > 0 && (
                  <>
                    <text
                      x={padding.left}
                      y={chartHeight - 10}
                      textAnchor="start"
                      className="text-[10px] fill-text-tertiary font-mono"
                    >
                      {validSeries[0].windowEnd}
                    </text>
                    {validSeries.length > 2 && (
                      <text
                        x={getX(Math.floor(validSeries.length / 2), validSeries.length)}
                        y={chartHeight - 10}
                        textAnchor="middle"
                        className="text-[10px] fill-text-tertiary font-mono"
                      >
                        {validSeries[Math.floor(validSeries.length / 2)].windowEnd}
                      </text>
                    )}
                    <text
                      x={chartWidth - padding.right}
                      y={chartHeight - 10}
                      textAnchor="end"
                      className="text-[10px] fill-text-tertiary font-mono"
                    >
                      {validSeries[validSeries.length - 1].windowEnd}
                    </text>
                  </>
                )}
              </svg>

              {/* Tooltip Card */}
              {hoveredPoint && (
                <div className="panel absolute top-2 right-4 z-20 w-64 p-3 shadow-lg border border-accent/40 bg-surface-raised">
                  <div className="flex items-center justify-between border-b border-border pb-1 mb-2">
                    <span className="mono-meta text-[11px] font-semibold text-text-primary">
                      Window Ending: {hoveredPoint.windowEnd}
                    </span>
                    <span className="mono-meta text-[10px]">{hoveredPoint.pairedDays} paired days</span>
                  </div>
                  <div className="space-y-1 text-[12px]">
                    <div className="flex justify-between">
                      <span className="text-accent font-medium">Standard Beta:</span>
                      <span className="font-mono font-semibold">{formatBeta(hoveredPoint.beta)}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-risk-fg">Downside Beta:</span>
                      <span className="font-mono">{formatBeta(hoveredPoint.downsideBeta)} ({hoveredPoint.downDays}d)</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-approved-fg">Upside Beta:</span>
                      <span className="font-mono">{formatBeta(hoveredPoint.upsideBeta)} ({hoveredPoint.upDays}d)</span>
                    </div>
                    {hoveredPoint.betaSpread !== null && (
                      <div className="flex justify-between border-t border-border pt-1 font-medium">
                        <span className="text-text-secondary">Asymmetry Spread:</span>
                        <span className={`font-mono ${hoveredPoint.betaSpread >= 0 ? "text-approved-fg" : "text-risk-fg"}`}>
                          {hoveredPoint.betaSpread >= 0 ? "+" : ""}
                          {formatBeta(hoveredPoint.betaSpread)}
                        </span>
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>
          ) : (
            <div className="p-8 text-center text-text-secondary">
              <p className="text-[14px]">Insufficient rolling observations for {horizon} horizon.</p>
              <p className="mono-meta mt-1">Requires at least {horizon === "3Y" ? "500" : "200"} paired trading days.</p>
            </div>
          )}

          {/* Analytical Footnote */}
          <div className="mt-3 space-y-1 border-t border-border pt-2 text-[12px] text-text-secondary">
            <div><strong>Exposure Stability:</strong> {stabilityAssessment}</div>
            <div><strong>Participation Asymmetry:</strong> {asymmetryAssessment}</div>
          </div>
        </div>
      )}

      {/* ── TAB 2: EXPOSURE STABILITY SUMMARY ────────────────────────────── */}
      {activeTab === "stability" && (
        <div className="panel-inset mt-4 p-4 md:p-5">
          <div className="border-b border-border pb-3">
            <h3 className="text-[14px] font-semibold text-text-primary">
              Descriptive Exposure Stability &amp; Dispersion Analysis
            </h3>
            <p className="mono-meta mt-0.5 font-sans">
              Summary statistics over {stability?.validWindows ?? 0} valid rolling {horizon} windows (excluding missing windows without interpolation)
            </p>
          </div>

          {stability ? (
            <div className="grid grid-cols-1 gap-4 pt-4 sm:grid-cols-2 lg:grid-cols-3">
              <div className="metric-tile">
                <p className="def-label mb-1">Median Beta &amp; Dispersion (IQR)</p>
                <div className="flex items-baseline gap-2">
                  <span className="data-value-lg text-text-primary">{formatBeta(stability.medianBeta)}</span>
                  <span className="mono-meta font-mono text-text-secondary">
                    IQR: {formatBeta(stability.iqrBeta, 3)}
                  </span>
                </div>
                <p className="mono-meta mt-2 font-sans">
                  Central tendency across rolling windows. Interquartile range (Q3 - Q1) captures core dispersion free of extreme outliers.
                </p>
              </div>

              <div className="metric-tile">
                <p className="def-label mb-1">Historical Range (Min → Max)</p>
                <div className="flex items-baseline gap-2">
                  <span className="data-value-md text-text-primary font-mono">
                    {formatBeta(stability.minBeta, 2)} → {formatBeta(stability.maxBeta, 2)}
                  </span>
                </div>
                <p className="mono-meta mt-2 font-sans">
                  Min: {formatBeta(stability.minBeta)} on {stability.minBetaDate ?? "—"}
                  <br />
                  Max: {formatBeta(stability.maxBeta)} on {stability.maxBetaDate ?? "—"}
                </p>
              </div>

              <div className="metric-tile">
                <p className="def-label mb-1">Period Drift (Start → End)</p>
                <div className="flex items-baseline gap-2">
                  <span className="data-value-md text-text-primary font-mono">
                    {formatBeta(stability.startBeta, 2)} → {formatBeta(stability.endBeta, 2)}
                  </span>
                  <span className={`text-[12px] font-mono font-medium ${stability.betaChange && stability.betaChange >= 0 ? "text-accent" : "text-approved-fg"}`}>
                    ({stability.betaChange && stability.betaChange >= 0 ? "+" : ""}{formatBeta(stability.betaChange, 3)})
                  </span>
                </div>
                <p className="mono-meta mt-2 font-sans">
                  Shift from initial rolling window ({stability.startDate ?? "—"}) to latest available window ({stability.endDate ?? "—"}).
                </p>
              </div>

              <div className="metric-tile">
                <p className="def-label mb-1">Parity Exposure Breakdown</p>
                <div className="flex items-center gap-3 mt-1">
                  <div>
                    <span className="text-[11px] text-text-secondary">Above 1.0 (Amplifying)</span>
                    <p className="data-value-md text-accent">{formatPct(stability.pctAboveParity)}</p>
                  </div>
                  <div className="border-l border-border pl-3">
                    <span className="text-[11px] text-text-secondary">Below 1.0 (Defensive)</span>
                    <p className="data-value-md text-approved-fg">{formatPct(stability.pctBelowParity)}</p>
                  </div>
                </div>
                <p className="mono-meta mt-2 font-sans">
                  Proportion of valid rolling windows operating with higher vs lower systematic sensitivity than the market.
                </p>
              </div>

              <div className="metric-tile">
                <p className="def-label mb-1">Average Downside Sensitivity</p>
                <p className="data-value-lg text-risk-fg">{formatBeta(stability.meanDownsideBeta)}</p>
                <p className="mono-meta mt-2 font-sans">
                  Mean downside beta across rolling windows where negative benchmark sample criteria were fulfilled.
                </p>
              </div>

              <div className="metric-tile">
                <p className="def-label mb-1">Average Upside Sensitivity &amp; Spread</p>
                <div className="flex items-baseline gap-2">
                  <span className="data-value-lg text-approved-fg">{formatBeta(stability.meanUpsideBeta)}</span>
                  <span className={`text-[12px] font-mono font-medium ${stability.upsideDownsideSpread && stability.upsideDownsideSpread >= 0 ? "text-approved-fg" : "text-risk-fg"}`}>
                    ({stability.upsideDownsideSpread && stability.upsideDownsideSpread >= 0 ? "+" : ""}{formatBeta(stability.upsideDownsideSpread, 3)})
                  </span>
                </div>
                <p className="mono-meta mt-2 font-sans">
                  Average upside participation minus downside sensitivity. Net positive spread denotes persistent advance asymmetry.
                </p>
              </div>
            </div>
          ) : (
            <p className="p-6 text-center text-text-secondary">No exposure stability data available for {horizon} horizon.</p>
          )}

          <div className="mt-4 border-t border-border pt-3 text-[12px] text-text-secondary leading-[1.5]">
            <p>
              <strong>Statistical Caveat:</strong> Rolling windows overlap heavily and are statistically dependent. A high window count does not denote independent experimental trials. Beta estimates above or below 1.0 represent historical sensitivity under the documented sample, not future volatility projections.
            </p>
          </div>
        </div>
      )}

      {/* ── TAB 3: MARKET REGIME SENSITIVITY TABLE ──────────────────────── */}
      {activeTab === "regimes" && (
        <div className="panel-inset mt-4 p-4 md:p-5">
          <div className="border-b border-border pb-3">
            <h3 className="text-[14px] font-semibold text-text-primary">
              Pre-Specified Historical Market Regime Comparison
            </h3>
            <p className="mono-meta mt-0.5 font-sans">
              Market sensitivity evaluated across 4 pre-specified historical market regimes · Neutral retrospective benchmarks
            </p>
          </div>

          <div className="mt-4 overflow-x-auto">
            <table className="w-full text-left text-[13px] border-collapse min-w-[700px]">
              <thead>
                <tr className="border-b border-border text-text-secondary text-[11px] font-semibold uppercase tracking-wider">
                  <th className="py-2.5 pr-4">Regime &amp; Basis</th>
                  <th className="py-2.5 px-3">Interval</th>
                  <th className="py-2.5 px-3">Paired (Up/Down)</th>
                  <th className="py-2.5 px-3">Full Beta</th>
                  <th className="py-2.5 px-3">Upside (Rb &gt; 0)</th>
                  <th className="py-2.5 px-3">Downside (Rb &lt; 0)</th>
                  <th className="py-2.5 px-3">Rolling Beta Range</th>
                  <th className="py-2.5 pl-3">Coverage Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border font-sans">
                {regimes.map((reg) => (
                  <tr key={reg.regimeId} className="hover:bg-surface-raised transition-colors">
                    <td className="py-3 pr-4">
                      <span className="font-semibold text-text-primary block">{reg.regimeName}</span>
                      <span className="mono-meta text-[11px] block">{reg.description}</span>
                    </td>
                    <td className="py-3 px-3 mono-meta whitespace-nowrap">
                      {reg.startDate} → {reg.endDate}
                    </td>
                    <td className="py-3 px-3 font-mono">
                      {reg.pairedDays > 0 ? (
                        <span>
                          {reg.pairedDays}d ({reg.upDays} / {reg.downDays})
                        </span>
                      ) : (
                        <span className="text-text-tertiary">—</span>
                      )}
                    </td>
                    <td className="py-3 px-3 font-mono font-medium">
                      {formatBeta(reg.fullPeriodBeta)}
                    </td>
                    <td className="py-3 px-3 font-mono text-approved-fg">
                      {formatBeta(reg.upsideBeta)}
                    </td>
                    <td className="py-3 px-3 font-mono text-risk-fg">
                      {formatBeta(reg.downsideBeta)}
                    </td>
                    <td className="py-3 px-3 font-mono text-text-secondary">
                      {reg.minRollingBeta !== null && reg.maxRollingBeta !== null ? (
                        <span>
                          {formatBeta(reg.minRollingBeta, 2)} → {formatBeta(reg.maxRollingBeta, 2)}
                        </span>
                      ) : (
                        "—"
                      )}
                    </td>
                    <td className="py-3 pl-3">
                      <span
                        className={`status-badge ${
                          reg.coverageStatus === "VERIFIED_COMPLETE_COVERAGE"
                            ? "state-approved"
                            : reg.coverageStatus === "BENCHMARK_UNAVAILABLE"
                            ? "state-unavailable"
                            : reg.coverageStatus === "OUTSIDE_OBSERVATION_HORIZON"
                            ? "state-candidate"
                            : "state-candidate"
                        }`}
                      >
                        {reg.coverageStatus.replaceAll("_", " ")}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="mt-4 border-t border-border pt-3 text-[12px] text-text-secondary leading-[1.5]">
            <p>
              <strong>Governance Transparency:</strong> Regime definitions are pre-specified and frozen; they are not retrospectively tailored to highlight favorable performance. Periods before {data.context.benchmarkName} inception are designated <code>BENCHMARK_UNAVAILABLE</code> and are strictly distinguished from fund underperformance.
            </p>
          </div>
        </div>
      )}

      {/* ── 3. SIX-PART EPISTEMIC DISCLOSURE FRAMEWORK ───────────────────── */}
      <div className="panel-inset mt-5">
        <button
          type="button"
          onClick={() => setShowEpistemic((prev) => !prev)}
          className="flex w-full items-center justify-between p-3.5 text-left transition-colors hover:bg-surface-raised"
        >
          <span className="flex items-center gap-2">
            <span className="text-[13px] font-semibold text-accent">
              Epistemic Framework &amp; Investor Due Diligence
            </span>
            <span className="mono-meta">
              {showEpistemic ? "Click to collapse" : "Click to inspect observation, interpretation, and investigation questions"}
            </span>
          </span>
          <span className={`tab-ordinal transition-transform ${showEpistemic ? "rotate-180" : ""}`} aria-hidden>
            ▼
          </span>
        </button>

        {showEpistemic && (
          <div className="border-t border-border p-4 md:p-5 space-y-4">
            <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
              <div className="chain-col">
                <div className="flex items-baseline gap-2">
                  <span className="tab-ordinal">01</span>
                  <p className="eyebrow text-accent">What YUKIRA Sees (Observation)</p>
                </div>
                <p className="mt-2 text-[13px] leading-[1.55] text-text-secondary">
                  {epistemic.observation}
                </p>
              </div>

              <div className="chain-col">
                <div className="flex items-baseline gap-2">
                  <span className="tab-ordinal">02</span>
                  <p className="eyebrow">What It Means (Interpretation)</p>
                </div>
                <p className="mt-2 text-[13px] leading-[1.55] text-text-secondary">
                  {epistemic.interpretation}
                </p>
              </div>

              <div className="chain-col">
                <div className="flex items-baseline gap-2">
                  <span className="tab-ordinal">03</span>
                  <p className="eyebrow text-candidate-fg">What Could Invalidate This (Risks)</p>
                </div>
                <p className="mt-2 text-[13px] leading-[1.55] text-text-secondary">
                  {epistemic.limitation}
                </p>
              </div>
            </div>

            {/* Investor Due Diligence Prompts */}
            <div className="rounded-xs border border-border bg-surface p-3.5">
              <p className="eyebrow text-accent mb-2">What The Allocator Should Investigate Next</p>
              <div className="space-y-2">
                <div className="flex items-start justify-between gap-3 text-[13px]">
                  <p className="text-text-secondary">
                    1. <strong>Cash Drag vs Factor Tilt:</strong> Did the fund&apos;s lower downside beta stem from active cash allocation or structural defensive sector exposure (e.g., FMCG, Utilities)?
                  </p>
                  <button
                    type="button"
                    onClick={() =>
                      onInvestigate
                        ? onInvestigate("Investigating cash allocation and sector tilt drivers of downside beta", "Beta Analysis")
                        : null
                    }
                    className="btn btn-sm btn-secondary shrink-0 text-[11px]"
                  >
                    Save Due Diligence
                  </button>
                </div>
                <div className="flex items-start justify-between gap-3 text-[13px]">
                  <p className="text-text-secondary">
                    2. <strong>Regime Sensitivity Shift:</strong> Did the fund increase beta during the post-tightening rally, and has that elevated market vulnerability?
                  </p>
                  <button
                    type="button"
                    onClick={() =>
                      onInvestigate
                        ? onInvestigate("Examining shift in systematic beta across monetary tightening vs secular expansion", "Beta Analysis")
                        : null
                    }
                    className="btn btn-sm btn-secondary shrink-0 text-[11px]"
                  >
                    Save Due Diligence
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* ── 4. BITEMPORAL AUDIT & LINEAGE DRAWER ─────────────────────────── */}
      <div className="panel-inset mt-4">
        <button
          type="button"
          onClick={() => setShowAuditLineage((prev) => !prev)}
          className="flex w-full items-center justify-between p-3.5 text-left transition-colors hover:bg-surface-raised"
        >
          <span className="flex items-center gap-2">
            <span className="text-[13px] font-medium text-accent">
              Bitemporal Audit Trail &amp; Cryptographic Lineage
            </span>
            <span className="mono-meta">
              {showAuditLineage ? "Click to collapse" : "Click to view SHA-256 and run IDs"}
            </span>
          </span>
          <span className={`tab-ordinal transition-transform ${showAuditLineage ? "rotate-180" : ""}`} aria-hidden>
            ▼
          </span>
        </button>

        {showAuditLineage && (
          <div className="grid grid-cols-1 gap-4 border-t border-border p-4 md:grid-cols-2">
            <div className="def-list">
              <div>
                <p className="def-label">Benchmark Alignment Lineage</p>
                <p className="def-value font-sans leading-[1.45]">{epistemic.benchmarkLineage}</p>
              </div>
              <div>
                <p className="def-label">Paired Trading Days</p>
                <p className="def-value">{metrics.totalPairedDays} Common Trading Days</p>
              </div>
              <div>
                <p className="def-label">Flat Days (Rb = 0.0)</p>
                <p className="def-value font-sans leading-[1.45]">
                  {metrics.flatDaysCount} flat benchmark observations isolated and excluded
                </p>
              </div>
            </div>

            <div className="def-list">
              <div>
                <p className="def-label">Scheme Option &amp; Code</p>
                <p className="def-value font-mono">Option #{context.schemeOptionId} {schemeCode ? `(${schemeCode})` : ""}</p>
              </div>
              <div>
                <p className="def-label">Knowledge Cutoff Time</p>
                <p className="def-value font-mono">{context.knowledgeCutoffTime}</p>
              </div>
              <div>
                <p className="def-label">Calculation Run ID</p>
                <p className="def-value font-mono">
                  {epistemic.calculationRunId ?? "Ephemeral In-Memory Audit"}
                </p>
              </div>
              <div>
                <p className="def-label">Source Artifact SHA-256</p>
                <p className="def-value text-accent font-mono select-all break-all">
                  {epistemic.sourceArtifactSha256 ?? "Not available"}
                </p>
              </div>
              <div>
                <p className="def-label">Governance State</p>
                <p className="def-value text-candidate-fg">
                  CANDIDATE METHODOLOGY (§MKT-01 / §MKT-02)
                </p>
              </div>
            </div>
          </div>
        )}
      </div>
    </section>
  );
}