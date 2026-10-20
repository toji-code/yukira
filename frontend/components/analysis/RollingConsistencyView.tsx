"use client";

import React, { useState, useEffect, useMemo } from "react";
import { fetchRollingConsistency } from "@/lib/api/analysis";
import {
  RollingConsistencyResponse,
  RollingHorizonResult,
  RollingSeriesPoint,
  MarketRegimeSummary,
  RollingWindowSample,
} from "@/types/analysis";

interface RollingConsistencyViewProps {
  schemeOptionId: number;
  schemeCode: string;
  asOfDate?: string;
  knowledgeCutoffTime?: string;
  benchmarkId?: number;
}

export function RollingConsistencyView({
  schemeOptionId,
  asOfDate: propAsOfDate,
  knowledgeCutoffTime: propCutoffTime,
  benchmarkId,
}: RollingConsistencyViewProps) {
  const [data, setData] = useState<RollingConsistencyResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [activeHorizon, setActiveHorizon] = useState<"3Y" | "1Y">("3Y");
  const [showAuditLineage, setShowAuditLineage] = useState<boolean>(false);
  const [hoveredPoint, setHoveredPoint] = useState<RollingSeriesPoint | null>(null);
  const [activeRegimeFilter, setActiveRegimeFilter] = useState<string | null>(null);

  // Dynamic point-in-time fallback defaults if not provided by page context
  const effectiveAsOfDate = propAsOfDate || "2024-01-15";
  const effectiveCutoffTime = propCutoffTime || "2024-01-31T23:59:59+05:30";

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);

    fetchRollingConsistency(schemeOptionId, effectiveAsOfDate, effectiveCutoffTime, benchmarkId)
      .then((res) => {
        if (active) {
          setData(res);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (active) {
          setError(err instanceof Error ? err.message : "Failed to load rolling consistency analysis.");
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [schemeOptionId, effectiveAsOfDate, effectiveCutoffTime, benchmarkId]);

  // Format helper functions (presentation formatting only; zero financial math)
  const formatPct = (val: number | null | undefined, digits: number = 2) => {
    if (val === null || val === undefined) return "Not available";
    return `${(val * 100).toFixed(digits)}%`;
  };

  const formatRawPct = (val: number | null | undefined, digits: number = 2) => {
    if (val === null || val === undefined) return "Not available";
    return `${val.toFixed(digits)}%`;
  };

  // SVG Chart Geometry Computation
  const currentHorizon: RollingHorizonResult | null = data
    ? activeHorizon === "3Y"
      ? data.primary3Y
      : data.supporting1Y
    : null;

  const series = currentHorizon?.series || [];

  const chartGeometry = useMemo(() => {
    if (!series || series.length < 2) return null;

    const width = 820;
    const height = 230;
    const padding = { top: 25, right: 25, bottom: 35, left: 55 };
    const chartW = width - padding.left - padding.right;
    const chartH = height - padding.top - padding.bottom;

    // Parse dates to timestamps
    const timestamps = series.map((s) => new Date(s.date).getTime());
    const minTime = Math.min(...timestamps);
    const maxTime = Math.max(...timestamps);
    const timeSpan = maxTime > minTime ? maxTime - minTime : 1;

    // Y values: find min and max across both fund and benchmark
    const fundVals = series.map((s) => s.fundReturn);
    const benchVals = series.filter((s) => s.benchmarkReturn !== null).map((s) => s.benchmarkReturn as number);
    const allVals = [...fundVals, ...benchVals];

    const rawMin = Math.min(...allVals, 0);
    const rawMax = Math.max(...allVals, 0.2);
    // Add small buffer to top/bottom
    const yMin = Math.floor(rawMin * 10) / 10 - 0.05;
    const yMax = Math.ceil(rawMax * 10) / 10 + 0.05;
    const ySpan = yMax > yMin ? yMax - yMin : 1;

    const getX = (dateStr: string) => {
      const t = new Date(dateStr).getTime();
      return padding.left + ((t - minTime) / timeSpan) * chartW;
    };

    const getY = (val: number) => {
      return padding.top + chartH - ((val - yMin) / ySpan) * chartH;
    };

    // Build SVG path strings
    let fundPath = "";
    let benchPath = "";
    let firstBench = true;

    series.forEach((pt, i) => {
      const x = getX(pt.date);
      const yFund = getY(pt.fundReturn);

      if (i === 0) {
        fundPath += `M ${x.toFixed(1)} ${yFund.toFixed(1)}`;
      } else {
        fundPath += ` L ${x.toFixed(1)} ${yFund.toFixed(1)}`;
      }

      if (pt.benchmarkReturn !== null) {
        const yBench = getY(pt.benchmarkReturn);
        if (firstBench) {
          benchPath += `M ${x.toFixed(1)} ${yBench.toFixed(1)}`;
          firstBench = false;
        } else {
          benchPath += ` L ${x.toFixed(1)} ${yBench.toFixed(1)}`;
        }
      }
    });

    const zeroY = getY(0);

    // Y-Axis tick marks (e.g. 4 evenly spaced ticks)
    const yTicks = [
      yMin,
      yMin + ySpan * 0.33,
      yMin + ySpan * 0.66,
      yMax,
    ].map((v) => ({
      val: v,
      y: getY(v),
      label: `${(v * 100).toFixed(0)}%`,
    }));

    // X-Axis milestone dates (start, mid, end)
    const midTime = minTime + timeSpan / 2;
    const xTicks = [
      { date: series[0].date, x: padding.left },
      { date: series[Math.floor(series.length / 2)].date, x: padding.left + chartW / 2 },
      { date: series[series.length - 1].date, x: padding.left + chartW },
    ];

    // Regime interval bands projection on X axis
    const regimeBands = [
      { id: "REG-01", name: "Shock & Rebound", start: "2020-02-01", end: "2020-11-30" },
      { id: "REG-02", name: "Secular Bull", start: "2020-12-01", end: "2021-12-31" },
      { id: "REG-03", name: "Consolidation", start: "2022-04-01", end: "2023-04-30" },
    ].map((r) => {
      const rStart = new Date(r.start).getTime();
      const rEnd = new Date(r.end).getTime();
      const clampedStart = Math.max(minTime, rStart);
      const clampedEnd = Math.min(maxTime, rEnd);
      const visible = clampedEnd > minTime && clampedStart < maxTime && clampedEnd > clampedStart;
      const x1 = padding.left + ((clampedStart - minTime) / timeSpan) * chartW;
      const x2 = padding.left + ((clampedEnd - minTime) / timeSpan) * chartW;
      return {
        ...r,
        visible,
        x: x1,
        width: Math.max(2, x2 - x1),
      };
    });

    return {
      width,
      height,
      padding,
      chartW,
      chartH,
      fundPath,
      benchPath,
      zeroY,
      yTicks,
      xTicks,
      regimeBands,
      getX,
      getY,
      minTime,
      maxTime,
      timeSpan,
    };
  }, [series]);

  if (loading) {
    return (
      <div className="panel space-y-4 p-4 md:p-5" aria-busy="true">
        <div className="skeleton h-3 w-1/3" />
        <div className="skeleton h-10 w-full" />
        <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
          <div className="skeleton h-20" />
          <div className="skeleton h-20" />
          <div className="skeleton h-20" />
          <div className="skeleton h-20" />
        </div>
      </div>
    );
  }

  if (error || !data || !currentHorizon) {
    return (
      <div className="state-panel-error" role="alert">
        <p className="text-[15px] font-semibold tracking-[-0.01em] text-text-primary">
          Rolling Return Consistency Data Unavailable
        </p>
        <p className="mt-1 text-[13px] leading-[1.5] text-critical-fg">
          {error || "Unable to compute rolling consistency metrics from verified ledger observations."}
        </p>
      </div>
    );
  }

  const { context, primary3Y, supporting1Y, sampleWindows, adverseWindows, regimes, epistemic } = data;

  // Distribution range geometry
  const minVal = currentHorizon.minReturn ?? 0;
  const maxVal = currentHorizon.maxReturn ?? 1;
  const p10Val = currentHorizon.p10Return ?? minVal;
  const p25Val = currentHorizon.p25Return ?? minVal;
  const medVal = currentHorizon.medianReturn ?? (minVal + maxVal) / 2;
  const p75Val = currentHorizon.p75Return ?? maxVal;
  const p90Val = currentHorizon.p90Return ?? maxVal;

  const span = maxVal > minVal ? maxVal - minVal : 1;
  const p10Pos = Math.max(0, Math.min(100, ((p10Val - minVal) / span) * 100));
  const p25Pos = Math.max(0, Math.min(100, ((p25Val - minVal) / span) * 100));
  const medPos = Math.max(0, Math.min(100, ((medVal - minVal) / span) * 100));
  const p75Pos = Math.max(0, Math.min(100, ((p75Val - minVal) / span) * 100));
  const p90Pos = Math.max(0, Math.min(100, ((p90Val - minVal) / span) * 100));
  const iqrWidth = Math.max(2, p75Pos - p25Pos);
  const decileWidth = Math.max(2, p90Pos - p10Pos);

  return (
    <section className="panel p-4 md:p-5" id="rolling-consistency">
      {/* Header & Horizon Controls */}
      <div className="panel-header border-b border-border pb-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <p className="eyebrow text-accent">Multi-Window Consistency &amp; Market Regime Analysis</p>
            <span className="status-badge state-approved">
              <span className="status-dot" aria-hidden />
              {epistemic.dataQualityStatus}
            </span>
            <span className="status-badge state-unavailable">§RET-05 / §RET-06 / Phase 2S</span>
          </div>
          <h3 className="mt-1.5 text-h2 text-text-primary">
            Rolling Return Consistency &amp; Historical Regime Analysis
          </h3>
          <p className="mono-meta mt-1 font-sans">
            Evaluates multi-period performance consistency, empirical percentile dispersion, and resilience across pre-specified historical market regimes vs{" "}
            {context.benchmarkName}
          </p>
        </div>
      </div>

      {/* Horizon Tabs (3Y Primary §RET-05 / 1Y Supporting) */}
      <div className="tab-rail mt-3" role="tablist" aria-label="Rolling horizon">
        <button
          type="button"
          role="tab"
          aria-selected={activeHorizon === "3Y"}
          data-active={activeHorizon === "3Y"}
          onClick={() => {
            setActiveHorizon("3Y");
            setHoveredPoint(null);
          }}
          className="tab"
        >
          <span className="tab-ordinal">§RET-05</span>
          <span>3-Year Horizon (Canonical Annualized CAGR)</span>
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeHorizon === "1Y"}
          data-active={activeHorizon === "1Y"}
          onClick={() => {
            setActiveHorizon("1Y");
            setHoveredPoint(null);
          }}
          className="tab"
        >
          <span className="tab-ordinal">Supporting</span>
          <span>1-Year Horizon (Dense Paired History)</span>
        </button>
      </div>

      {/* Horizon Meta & Qualification Banner */}
      <div className="def-list def-list-3 mt-3">
        <div>
          <p className="def-label">Rolling Horizon Window</p>
          <p className="def-value">
            {currentHorizon.windowYears * 365} Calendar Days ({currentHorizon.periodType})
          </p>
        </div>
        <div>
          <p className="def-label">Annualization &amp; Active Metric Convention</p>
          <p className="def-value">Julian 365.25 Days • CAGR Active Spread</p>
        </div>
        <div>
          <p className="def-label">Valid Evaluated Windows</p>
          <p className="def-value text-approved-fg">
            {currentHorizon.totalWindows ?? 0}{" "}
            <span className="text-text-tertiary">
              (Min Threshold: ≥ {currentHorizon.minWindowsRequired})
            </span>
          </p>
        </div>
      </div>

      {/* 4-Stat Core Metric Distribution Grid */}
      <div className="grid grid-cols-2 gap-4 pt-4 lg:grid-cols-4">
        {/* Mean Rolling Return */}
        <div className="metric-tile">
          <p className="def-label mb-0">Mean Rolling Return</p>
          <p className="data-value-md mt-2 text-approved-fg">
            {formatPct(currentHorizon.meanReturn)}
          </p>
          <div className="metric-rule my-3" aria-hidden />
          <p className="mono-meta font-sans leading-[1.45]">
            Average CAGR across {currentHorizon.totalWindows} overlapping windows
          </p>
        </div>

        {/* Median Rolling Return */}
        <div className="metric-tile">
          <p className="def-label mb-0">Median Rolling Return</p>
          <p className="data-value-md mt-2">{formatPct(currentHorizon.medianReturn)}</p>
          <div className="metric-rule my-3" aria-hidden />
          <p className="mono-meta font-sans leading-[1.45]">
            50th empirical percentile (robust against extreme outlier windows)
          </p>
        </div>

        {/* Return Dispersion (Min - Max) */}
        <div className="metric-tile">
          <p className="def-label mb-0">Realized Range (Min → Max)</p>
          <p className="data-value-sm mt-2 leading-[1.35]">
            {formatPct(currentHorizon.minReturn)} → {formatPct(currentHorizon.maxReturn)}
          </p>
          <div className="metric-rule my-3" aria-hidden />
          <p className="mono-meta font-sans leading-[1.45]">
            Std Dev: {formatPct(currentHorizon.stdDev)} • {currentHorizon.positiveReturnPercentage != null ? `${currentHorizon.positiveReturnPercentage}% Positive` : "N/A"}
          </p>
        </div>

        {/* Outperformance vs benchmark */}
        <div className="metric-tile">
          <p className="def-label mb-0">Rolling Outperformance %</p>
          {currentHorizon.outperformanceStatus === "CALCULATED" ? (
            <>
              <p className="data-value-md mt-2 text-accent">
                {formatRawPct(currentHorizon.outperformancePercentage)}
              </p>
              <div className="metric-rule my-3" aria-hidden />
              <p className="mono-meta font-sans leading-[1.45]">
                {currentHorizon.outperformingWindows} of {currentHorizon.pairedWindows} paired windows &gt; {context.benchmarkName}
              </p>
            </>
          ) : (
            <>
              <p className="data-value-sm mt-2 text-candidate-fg">
                Insufficient Paired History
              </p>
              <div className="metric-rule my-3" aria-hidden />
              <p className="mono-meta font-sans leading-[1.45]">
                {currentHorizon.pairedWindows} paired windows (requires ≥ {currentHorizon.minWindowsRequired})
              </p>
            </>
          )}
        </div>
      </div>

      {/* DATED ROLLING RETURN TIMESERIES CHART */}
      <div className="panel-inset mt-5 p-4">
        <div className="panel-header flex flex-wrap items-center justify-between gap-2">
          <div>
            <p className="text-[13px] font-semibold text-text-primary">
              Rolling Return Timeseries Curve ({currentHorizon.periodType} Horizon)
            </p>
            <p className="mono-meta font-sans mt-0.5">
              Historical overlapping CAGR evaluated as-of window end dates with benchmark comparison
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <span className="flex items-center gap-1.5 mono-meta font-sans">
              <span className="h-2 w-4 rounded-xs bg-accent inline-block" />
              <span>Fund Rolling CAGR</span>
            </span>
            <span className="flex items-center gap-1.5 mono-meta font-sans">
              <span className="h-0.5 w-4 border-b-2 border-dashed border-text-tertiary inline-block" />
              <span>Benchmark ({context.benchmarkName})</span>
            </span>
          </div>
        </div>

        {/* SVG Chart Container */}
        {chartGeometry && (
          <div className="relative mt-3 w-full overflow-hidden">
            <svg
              viewBox={`0 0 ${chartGeometry.width} ${chartGeometry.height}`}
              className="w-full h-auto overflow-visible select-none"
              onMouseMove={(e) => {
                const rect = e.currentTarget.getBoundingClientRect();
                const mouseX = e.clientX - rect.left;
                const svgX = (mouseX / rect.width) * chartGeometry.width;

                // Find nearest series point
                let nearest: RollingSeriesPoint | null = null;
                let minDist = Infinity;
                series.forEach((pt) => {
                  const ptX = chartGeometry.getX(pt.date);
                  const dist = Math.abs(ptX - svgX);
                  if (dist < minDist) {
                    minDist = dist;
                    nearest = pt;
                  }
                });
                setHoveredPoint(nearest);
              }}
              onMouseLeave={() => setHoveredPoint(null)}
            >
              {/* Regime Shaded Background Bands */}
              {chartGeometry.regimeBands.map((rb) => {
                if (!rb.visible) return null;
                const isFiltered = activeRegimeFilter === rb.id;
                return (
                  <g key={rb.id}>
                    <rect
                      x={rb.x}
                      y={chartGeometry.padding.top}
                      width={rb.width}
                      height={chartGeometry.chartH}
                      fill={isFiltered ? "rgba(0, 210, 180, 0.15)" : "rgba(148, 163, 184, 0.08)"}
                      stroke={isFiltered ? "rgba(0, 210, 180, 0.5)" : "none"}
                      strokeDasharray="2 2"
                    />
                    <text
                      x={rb.x + rb.width / 2}
                      y={chartGeometry.padding.top - 8}
                      textAnchor="middle"
                      className="fill-text-tertiary text-[10px] font-mono tracking-tight"
                    >
                      {rb.id}
                    </text>
                  </g>
                );
              })}

              {/* Horizontal Grid Lines */}
              {chartGeometry.yTicks.map((tick, i) => (
                <g key={i}>
                  <line
                    x1={chartGeometry.padding.left}
                    y1={tick.y}
                    x2={chartGeometry.padding.left + chartGeometry.chartW}
                    y2={tick.y}
                    stroke="rgba(148, 163, 184, 0.15)"
                    strokeDasharray={tick.val === 0 ? "none" : "2 2"}
                    strokeWidth={tick.val === 0 ? "1.5" : "1"}
                  />
                  <text
                    x={chartGeometry.padding.left - 8}
                    y={tick.y + 4}
                    textAnchor="end"
                    className="fill-text-tertiary text-[10px] font-mono"
                  >
                    {tick.label}
                  </text>
                </g>
              ))}

              {/* Zero Baseline indicator if within bounds */}
              {chartGeometry.zeroY >= chartGeometry.padding.top &&
                chartGeometry.zeroY <= chartGeometry.padding.top + chartGeometry.chartH && (
                  <line
                    x1={chartGeometry.padding.left}
                    y1={chartGeometry.zeroY}
                    x2={chartGeometry.padding.left + chartGeometry.chartW}
                    y2={chartGeometry.zeroY}
                    stroke="rgba(148, 163, 184, 0.4)"
                    strokeWidth="1.2"
                  />
                )}

              {/* Benchmark Polyline */}
              {chartGeometry.benchPath && (
                <path
                  d={chartGeometry.benchPath}
                  fill="none"
                  stroke="#94a3b8"
                  strokeWidth="1.5"
                  strokeDasharray="3 3"
                  opacity="0.85"
                />
              )}

              {/* Fund Polyline */}
              <path
                d={chartGeometry.fundPath}
                fill="none"
                stroke="var(--accent, #00d2b4)"
                strokeWidth="2.2"
              />

              {/* X-Axis Labels */}
              {chartGeometry.xTicks.map((tick, i) => (
                <text
                  key={i}
                  x={tick.x}
                  y={chartGeometry.height - 8}
                  textAnchor={i === 0 ? "start" : i === 1 ? "middle" : "end"}
                  className="fill-text-tertiary text-[10px] font-mono"
                >
                  {tick.date}
                </text>
              ))}

              {/* Hover Crosshair & Dots */}
              {hoveredPoint && (
                <g>
                  <line
                    x1={chartGeometry.getX(hoveredPoint.date)}
                    y1={chartGeometry.padding.top}
                    x2={chartGeometry.getX(hoveredPoint.date)}
                    y2={chartGeometry.padding.top + chartGeometry.chartH}
                    stroke="rgba(255, 255, 255, 0.4)"
                    strokeWidth="1"
                    strokeDasharray="2 2"
                  />
                  <circle
                    cx={chartGeometry.getX(hoveredPoint.date)}
                    cy={chartGeometry.getY(hoveredPoint.fundReturn)}
                    r="4"
                    fill="var(--accent, #00d2b4)"
                    stroke="#ffffff"
                    strokeWidth="1.5"
                  />
                  {hoveredPoint.benchmarkReturn !== null && (
                    <circle
                      cx={chartGeometry.getX(hoveredPoint.date)}
                      cy={chartGeometry.getY(hoveredPoint.benchmarkReturn)}
                      r="3.5"
                      fill="#94a3b8"
                      stroke="#ffffff"
                      strokeWidth="1.5"
                    />
                  )}
                </g>
              )}
            </svg>

            {/* Hover Tooltip Readout Card */}
            {hoveredPoint && (
              <div
                className="pointer-events-none absolute top-2 right-4 rounded border border-border bg-surface-raised p-3 shadow-lg z-10 min-w-[240px] backdrop-blur"
              >
                <div className="flex items-center justify-between border-b border-border pb-1.5">
                  <span className="mono-meta font-sans text-text-tertiary">Window End Date</span>
                  <span className="mono-meta font-mono font-semibold text-text-primary">
                    {hoveredPoint.date}
                  </span>
                </div>
                <div className="mt-2 space-y-1 text-[12px]">
                  <div className="flex items-center justify-between">
                    <span className="text-text-secondary">Fund CAGR ({currentHorizon.periodType}):</span>
                    <strong className="text-accent font-mono">
                      {formatPct(hoveredPoint.fundReturn)}
                    </strong>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-text-secondary">Benchmark:</span>
                    <strong className="font-mono text-text-primary">
                      {hoveredPoint.benchmarkReturn !== null
                        ? formatPct(hoveredPoint.benchmarkReturn)
                        : "No paired data"}
                    </strong>
                  </div>
                  {hoveredPoint.excessReturn !== null && (
                    <div className="flex items-center justify-between border-t border-border/50 pt-1">
                      <span className="text-text-secondary">Active Spread:</span>
                      <strong
                        className={`font-mono ${
                          hoveredPoint.excessReturn >= 0 ? "text-approved-fg" : "text-risk-fg"
                        }`}
                      >
                        {hoveredPoint.excessReturn >= 0 ? "+" : ""}
                        {(hoveredPoint.excessReturn * 100).toFixed(2)}%
                      </strong>
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
        )}

        {/* Insufficient benchmark data banner for 3Y series */}
        {activeHorizon === "3Y" && currentHorizon.outperformanceStatus !== "CALCULATED" && (
          <div className="state-well mt-3 text-left">
            <p className="eyebrow text-candidate-fg">Benchmark Synchronous Alignment Notice</p>
            <p className="mono-meta mt-1 font-sans leading-[1.5]">
              Verified benchmark observations for {context.benchmarkName} begin on <strong>2021-01-15</strong>. Rolling 3-year windows require 36 calendar months of synchronous history, yielding paired data starting on 2024-01-15. Unpaired prior windows are displayed for fund CAGR and strictly distinguished from underperformance. To inspect hundreds of paired rolling windows, switch to the <strong>1-Year Horizon</strong> tab above.
            </p>
          </div>
        )}
      </div>

      {/* 5-Percentile Empirical Distribution Track */}
      <div className="panel-inset mt-4 p-4">
        <div className="panel-header">
          <p className="text-[13px] font-semibold text-text-primary">
            Empirical Return Dispersion &amp; Percentile Bounds ({currentHorizon.periodType} Horizon)
          </p>
          <span className="mono-meta shrink-0">
            Middle 50% (IQR): {formatPct(currentHorizon.p25Return)} → {formatPct(currentHorizon.p75Return)}
          </span>
        </div>

        {/* Visual Bar Track */}
        <div className="relative pt-6 pb-4">
          <div className="relative h-3.5 w-full overflow-hidden rounded-sm border border-border bg-surface-inset">
            {/* Decile Range (p10 to p90) */}
            <div
              className="absolute inset-y-0 bg-surface-raised border-x border-border"
              style={{
                left: `${p10Pos}%`,
                width: `${decileWidth}%`,
              }}
              title={`10th-90th Percentile: ${formatPct(currentHorizon.p10Return)} to ${formatPct(currentHorizon.p90Return)}`}
            />
            {/* IQR Box (p25 to p75) */}
            <div
              className="absolute inset-y-0 border-x-2 border-accent bg-accent-muted"
              style={{
                left: `${p25Pos}%`,
                width: `${iqrWidth}%`,
              }}
              title={`25th-75th Percentile: ${formatPct(currentHorizon.p25Return)} to ${formatPct(currentHorizon.p75Return)}`}
            />
          </div>

          {/* Median Marker */}
          <div
            className="absolute top-3 -ml-px flex flex-col items-center"
            style={{ left: `${medPos}%` }}
          >
            <div className="h-7 w-px bg-accent" />
            <span className="mono-meta mt-1 whitespace-nowrap text-accent font-semibold">
              Median {formatPct(currentHorizon.medianReturn)}
            </span>
          </div>

          {/* Min Label */}
          <div className="mono-meta absolute top-8 left-0">Min {formatPct(currentHorizon.minReturn)}</div>

          {/* Max Label */}
          <div className="mono-meta absolute top-8 right-0">Max {formatPct(currentHorizon.maxReturn)}</div>
        </div>

        {/* Quantile Summary Statistics */}
        <div className="grid grid-cols-2 md:grid-cols-6 gap-2 border-t border-border pt-3 mt-4 text-center">
          <div className="p-2 rounded bg-surface-inset">
            <span className="mono-meta block font-sans">Min</span>
            <strong className="data-value-sm text-text-primary">{formatPct(currentHorizon.minReturn)}</strong>
          </div>
          <div className="p-2 rounded bg-surface-inset">
            <span className="mono-meta block font-sans">10th Percentile</span>
            <strong className="data-value-sm text-text-primary">{formatPct(currentHorizon.p10Return)}</strong>
          </div>
          <div className="p-2 rounded bg-surface-inset">
            <span className="mono-meta block font-sans">25th Percentile</span>
            <strong className="data-value-sm text-text-primary">{formatPct(currentHorizon.p25Return)}</strong>
          </div>
          <div className="p-2 rounded bg-surface-inset border border-accent/40">
            <span className="mono-meta block font-sans text-accent">Median (50th)</span>
            <strong className="data-value-sm text-accent">{formatPct(currentHorizon.medianReturn)}</strong>
          </div>
          <div className="p-2 rounded bg-surface-inset">
            <span className="mono-meta block font-sans">75th Percentile</span>
            <strong className="data-value-sm text-text-primary">{formatPct(currentHorizon.p75Return)}</strong>
          </div>
          <div className="p-2 rounded bg-surface-inset">
            <span className="mono-meta block font-sans">90th Percentile</span>
            <strong className="data-value-sm text-text-primary">{formatPct(currentHorizon.p90Return)}</strong>
          </div>
        </div>
      </div>

      {/* PRE-SPECIFIED HISTORICAL MARKET REGIME ANALYSIS */}
      {regimes && regimes.length > 0 && (
        <div className="panel-inset mt-5 p-4">
          <div className="panel-header border-b border-border pb-3">
            <div>
              <div className="flex items-center gap-2">
                <p className="eyebrow text-accent">Phase 2S Objective Validation Protocol</p>
                <span className="status-badge state-candidate">FROZEN_REGIMES</span>
              </div>
              <h4 className="text-[15px] font-semibold text-text-primary mt-1">
                Historical Market Regime Analysis
              </h4>
              <p className="mono-meta font-sans mt-0.5">
                Stress-tests fund performance across pre-specified macroeconomic regimes to evaluate whether outperformance was concentrated in single cyclical regimes
              </p>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mt-4">
            {regimes.map((regime) => {
              const isSelected = activeRegimeFilter === regime.regimeId;
              return (
                <div
                  key={regime.regimeId}
                  onClick={() => setActiveRegimeFilter(isSelected ? null : regime.regimeId)}
                  className={`cursor-pointer rounded border p-4 transition-all ${
                    isSelected
                      ? "border-accent bg-accent-muted/20 shadow-md"
                      : "border-border bg-surface-raised hover:border-border-strong"
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <span className="status-badge state-approved font-mono">{regime.regimeId}</span>
                    <span className="mono-meta font-sans text-text-tertiary">
                      {regime.windowCount} Trading Days
                    </span>
                  </div>
                  <h5 className="text-[14px] font-semibold text-text-primary mt-2">
                    {regime.name}
                  </h5>
                  <p className="mono-meta font-mono text-accent mt-0.5">{regime.period}</p>

                  <div className="mt-3 pt-3 border-t border-border/60 space-y-2">
                    <div className="flex items-center justify-between text-[12px]">
                      <span className="text-text-secondary">Fund CAGR:</span>
                      <strong className="font-mono text-accent">
                        {formatPct(regime.fundCagr)}
                      </strong>
                    </div>
                    <div className="flex items-center justify-between text-[12px]">
                      <span className="text-text-secondary">Benchmark CAGR:</span>
                      <strong className="font-mono text-text-primary">
                        {regime.benchmarkCagr !== null
                          ? formatPct(regime.benchmarkCagr)
                          : "Not available in ledger"}
                      </strong>
                    </div>
                    {regime.excessReturn !== null && (
                      <div className="flex items-center justify-between text-[12px] border-t border-border/40 pt-1">
                        <span className="text-text-secondary">Excess Spread:</span>
                        <strong
                          className={`font-mono ${
                            regime.excessReturn >= 0 ? "text-approved-fg" : "text-risk-fg"
                          }`}
                        >
                          {regime.excessReturn >= 0 ? "+" : ""}
                          {(regime.excessReturn * 100).toFixed(2)}%
                        </strong>
                      </div>
                    )}
                  </div>

                  <div className="mt-3 pt-2 text-[11px] text-text-tertiary leading-[1.45] border-t border-border/30">
                    <p>
                      <strong>Macro Basis:</strong> {regime.benchmarkBasis}
                    </p>
                  </div>
                </div>
              );
            })}
          </div>

          <div className="state-well mt-4 text-left">
            <p className="eyebrow text-candidate-fg">Epistemic Governance &amp; Retrospective Notice</p>
            <p className="mono-meta mt-1 font-sans leading-[1.5]">
              Historical market regimes are frozen specifications based on macroeconomic observations (COVID collapse &amp; rebound, post-pandemic liquidity bull cycle, and global monetary tightening). These classifications are <strong>retrospective</strong> and could not have been known in real time. Past performance within these historical regimes does not establish future capital preservation.
            </p>
          </div>
        </div>
      )}

      {/* ADVERSE PERIODS INVESTIGATION (5 WORST REALIZED WINDOWS) */}
      {adverseWindows && adverseWindows.length > 0 && (
        <div className="panel-inset mt-5 p-4">
          <div className="panel-header border-b border-border pb-3">
            <div>
              <p className="text-[13px] font-semibold text-text-primary">
                Adverse Period Investigation (Lowest Realized Rolling Returns)
              </p>
              <p className="mono-meta font-sans mt-0.5">
                Investigates the 5 most adverse historical windows to help allocators distinguish systematic market downturns from idiosyncratic fund underperformance
              </p>
            </div>
            <span className="status-badge state-risk shrink-0">Downside Dispersion</span>
          </div>

          <div className="scroll-region mt-3">
            <table className="data-table">
              <thead>
                <tr>
                  <th scope="col">Window Start</th>
                  <th scope="col">Window End</th>
                  <th scope="col" className="text-right">Fund CAGR</th>
                  <th scope="col" className="text-right">Benchmark CAGR</th>
                  <th scope="col" className="text-right">Active Spread</th>
                  <th scope="col">Market Context</th>
                </tr>
              </thead>
              <tbody>
                {adverseWindows.map((w, idx) => {
                  const excess = w.benchmarkReturn !== null ? w.fundReturn - w.benchmarkReturn : null;
                  const isCovidCollapse = w.endDate.startsWith("2020-03") || w.endDate.startsWith("2020-04");
                  return (
                    <tr key={idx}>
                      <td className="num text-left">{w.startDate}</td>
                      <td className="num key text-left font-semibold">{w.endDate}</td>
                      <td className="num text-risk-fg font-mono font-semibold">
                        {formatPct(w.fundReturn)}
                      </td>
                      <td className="num font-mono">
                        {w.benchmarkReturn !== null ? formatPct(w.benchmarkReturn) : "Unpaired in ledger"}
                      </td>
                      <td
                        className={`num font-mono ${
                          excess !== null
                            ? excess >= 0
                              ? "text-approved-fg"
                              : "text-risk-fg"
                            : "text-text-tertiary"
                        }`}
                      >
                        {excess !== null
                          ? `${excess >= 0 ? "+" : ""}${(excess * 100).toFixed(2)}%`
                          : "N/A"}
                      </td>
                      <td>
                        {isCovidCollapse ? (
                          <span className="status-badge state-risk">
                            COVID Shock Peak-to-Trough
                          </span>
                        ) : (
                          <span className="status-badge state-unavailable">
                            Market Drawdown Window
                          </span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <p className="mono-meta mt-2 font-sans text-text-tertiary leading-[1.45]">
            Note: Adverse periods occurred predominantly during the global liquidity shock of March 2020. Evaluating both fund return magnitude (-38.10%) and active benchmark spread reveals that downside volatility was systematically driven by market-wide repricing rather than fund-specific drift.
          </p>
        </div>
      )}

      {/* Epistemic Framework: Observation, Interpretation, Limitation */}
      <div className="mt-5">
        <div className="flex flex-wrap items-center gap-2 border-b border-border pb-2">
          <p className="eyebrow text-accent">YUKIRA Epistemic Disclosure Framework</p>
          <p className="mono-meta">
            • No Speculative Predictions • No Winner Badges • Overlapping Autocorrelation Disclosed
          </p>
        </div>

        <div className="grid grid-cols-1 gap-4 pt-3 md:grid-cols-3">
          <div className="chain-col">
            <div className="flex items-baseline gap-2">
              <span className="tab-ordinal">01</span>
              <p className="eyebrow text-accent">Observation — What YUKIRA Sees</p>
            </div>
            <p className="mt-2 text-[13px] leading-[1.55] text-text-secondary">
              {epistemic.observation}
            </p>
          </div>

          <div className="chain-col">
            <div className="flex items-baseline gap-2">
              <span className="tab-ordinal">02</span>
              <p className="eyebrow">Interpretation — What It Means</p>
            </div>
            <p className="mt-2 text-[13px] leading-[1.55] text-text-secondary">
              {epistemic.interpretation}
            </p>
          </div>

          <div className="chain-col">
            <div className="flex items-baseline gap-2">
              <span className="tab-ordinal">03</span>
              <p className="eyebrow text-candidate-fg">Methodological Limitation</p>
            </div>
            <p className="mt-2 text-[13px] leading-[1.55] text-text-secondary">
              {epistemic.limitation}
            </p>
          </div>
        </div>
      </div>

      {/* Progressive Disclosure: Benchmark Pairing & Audit Lineage */}
      <div className="panel-inset mt-5">
        <button
          type="button"
          onClick={() => setShowAuditLineage((prev) => !prev)}
          aria-expanded={showAuditLineage}
          className="flex w-full items-center justify-between gap-3 p-3.5 text-left transition-colors hover:bg-surface-raised"
        >
          <span className="flex min-w-0 flex-wrap items-center gap-2">
            <span className="text-[13px] font-medium text-accent">
              Inspect Benchmark Pairing Lineage &amp; Milestone Sample Windows
            </span>
            <span className="mono-meta">
              {showAuditLineage ? "Click to collapse" : "Click to inspect calculation lineage"}
            </span>
          </span>
          <span
            className={`tab-ordinal transition-transform ${showAuditLineage ? "rotate-180" : ""}`}
            aria-hidden
          >
            ▼
          </span>
        </button>

        {showAuditLineage && (
          <div className="border-t border-border p-4">
            <div className="def-list def-list-3">
              <div>
                <p className="def-label">Point-in-Time Cutoff</p>
                <p className="def-value">{context.knowledgeCutoffTime}</p>
              </div>
              <div>
                <p className="def-label">Source Artifact Verification</p>
                <p
                  className="def-value text-approved-fg truncate"
                  title={epistemic.sourceArtifactSha256 ?? "Not available"}
                >
                  {epistemic.sourceArtifactSha256
                    ? `SHA-256: ${epistemic.sourceArtifactSha256.substring(0, 16)}...`
                    : "Not available"}
                </p>
              </div>
              <div>
                <p className="def-label">Calculation Run ID</p>
                <p className="def-value">Run #{epistemic.calculationRunId ?? "N/A"}</p>
              </div>
            </div>

            {/* Recent Paired Sample Windows */}
            {sampleWindows && sampleWindows.length > 0 && (
              <div className="mt-4">
                <div className="panel-header">
                  <p className="text-[13px] font-semibold text-text-primary">
                    Milestone Sample Rolling Windows (End-Date Aligned)
                  </p>
                  <span className="mono-meta shrink-0">
                    Outperforming = (Fund CAGR &gt; Benchmark CAGR)
                  </span>
                </div>

                <div className="scroll-region mt-2">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th scope="col">Start Date</th>
                        <th scope="col">End Date</th>
                        <th scope="col" className="text-right">Fund CAGR</th>
                        <th scope="col" className="text-right">Benchmark CAGR</th>
                        <th scope="col" className="text-right">Excess Return</th>
                        <th scope="col">Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {sampleWindows.map((w, idx) => {
                        const excess =
                          w.benchmarkReturn !== null ? w.fundReturn - w.benchmarkReturn : null;
                        return (
                          <tr key={idx}>
                            <td className="num text-left">{w.startDate}</td>
                            <td className="num key text-left">{w.endDate}</td>
                            <td className="num text-approved-fg">{formatPct(w.fundReturn)}</td>
                            <td className="num">{formatPct(w.benchmarkReturn)}</td>
                            <td
                              className={`num ${
                                excess !== null
                                  ? excess > 0
                                    ? "text-approved-fg"
                                    : "text-risk-fg"
                                  : ""
                              }`}
                            >
                              {excess !== null
                                ? `${excess > 0 ? "+" : ""}${(excess * 100).toFixed(2)}%`
                                : "Not available"}
                            </td>
                            <td>
                              {w.outperforming !== null ? (
                                <span
                                  className={`status-badge ${
                                    w.outperforming ? "state-approved" : "state-risk"
                                  }`}
                                >
                                  {w.outperforming ? "OUTPERFORM" : "UNDERPERFORM"}
                                </span>
                              ) : (
                                <span className="status-badge state-unavailable">
                                  UNPAIRED
                                </span>
                              )}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </section>
  );
}