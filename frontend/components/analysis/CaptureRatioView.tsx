"use client";

import React, { useState, useEffect, useMemo } from "react";
import Link from "next/link";
import { fetchCaptureRatios } from "@/lib/api/analysis";
import {
  CaptureRatioResponse,
  HistoricalStressEvent,
  StressTrajectoryPoint,
} from "@/types/analysis";

interface CaptureRatioViewProps {
  schemeOptionId: number;
  schemeCode: string;
  asOfDate?: string;
  knowledgeCutoffTime?: string;
  benchmarkId?: number;
}

export function CaptureRatioView({
  schemeOptionId,
  schemeCode,
  asOfDate: propAsOfDate,
  knowledgeCutoffTime: propCutoffTime,
  benchmarkId,
}: CaptureRatioViewProps) {
  const [data, setData] = useState<CaptureRatioResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedEventId, setSelectedEventId] = useState<string>("STRESS-02");
  const [showAuditLineage, setShowAuditLineage] = useState<boolean>(false);
  const [hoveredPoint, setHoveredPoint] = useState<StressTrajectoryPoint | null>(null);

  // Dynamic point-in-time fallback defaults if not provided by page context
  const effectiveAsOfDate = propAsOfDate || "2024-01-15";
  const effectiveCutoffTime = propCutoffTime || "2024-01-31T23:59:59+05:30";

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);

    fetchCaptureRatios(schemeOptionId, effectiveAsOfDate, effectiveCutoffTime, benchmarkId)
      .then((res) => {
        if (active) {
          setData(res);
          // If selected event is outside observation horizon, switch to first verified event
          const hasEvents = res.stressEvents && res.stressEvents.length > 0;
          if (hasEvents) {
            const currentSelected = res.stressEvents!.find((e) => e.eventId === selectedEventId);
            if (!currentSelected || currentSelected.coverageStatus === "OUTSIDE_OBSERVATION_HORIZON") {
              const firstValid = res.stressEvents!.find(
                (e) => e.coverageStatus === "VERIFIED_COMPLETE_COVERAGE" || e.coverageStatus === "BENCHMARK_UNAVAILABLE"
              );
              if (firstValid) {
                setSelectedEventId(firstValid.eventId);
              }
            }
          }
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (active) {
          setError(err instanceof Error ? err.message : "Failed to load capture ratio analysis.");
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
    return `${val.toFixed(digits)}%`;
  };

  const formatSignedPct = (val: number | null | undefined, digits: number = 2) => {
    if (val === null || val === undefined) return "Not available";
    const sign = val > 0 ? "+" : "";
    return `${sign}${val.toFixed(digits)}%`;
  };

  const formatDecimalPct = (val: number | null | undefined, digits: number = 2) => {
    if (val === null || val === undefined) return "Not available";
    const sign = val > 0 ? "+" : "";
    return `${sign}${(val * 100).toFixed(digits)}%`;
  };

  const formatRatio = (val: number | null | undefined) => {
    if (val === null || val === undefined) return "Not available";
    return `${val.toFixed(2)}%`;
  };

  // Selected stress event resolution
  const stressEvents: HistoricalStressEvent[] = data?.stressEvents || [];
  const selectedEvent: HistoricalStressEvent | null =
    stressEvents.find((e) => e.eventId === selectedEventId) ||
    stressEvents[0] ||
    null;

  // Trajectory SVG Chart Geometry
  const trajectory = selectedEvent?.trajectory || [];

  const chartGeometry = useMemo(() => {
    if (!trajectory || trajectory.length < 2) return null;

    const width = 820;
    const height = 230;
    const padding = { top: 25, right: 30, bottom: 40, left: 55 };
    const chartW = width - padding.left - padding.right;
    const chartH = height - padding.top - padding.bottom;

    const timestamps = trajectory.map((p) => new Date(p.date).getTime());
    const minTime = Math.min(...timestamps);
    const maxTime = Math.max(...timestamps);
    const timeSpan = maxTime > minTime ? maxTime - minTime : 1;

    // Y values: gather fundIndexed and benchmarkIndexed
    const fundVals = trajectory.map((p) => p.fundIndexed).filter((v): v is number => v !== null);
    const benchVals = trajectory.map((p) => p.benchmarkIndexed).filter((v): v is number => v !== null);
    const allVals = [...fundVals, ...benchVals, 100.0];

    const rawMin = Math.min(...allVals);
    const rawMax = Math.max(...allVals);
    // Pad Y axis slightly
    const yMin = Math.floor(rawMin - 2);
    const yMax = Math.ceil(rawMax + 2);
    const ySpan = yMax > yMin ? yMax - yMin : 1;

    const getX = (dateStr: string) => {
      const t = new Date(dateStr).getTime();
      return padding.left + ((t - minTime) / timeSpan) * chartW;
    };

    const getY = (val: number) => {
      return padding.top + chartH - ((val - yMin) / ySpan) * chartH;
    };

    // Build SVG paths
    let fundPath = "";
    let benchPath = "";
    let firstBench = true;

    trajectory.forEach((pt, i) => {
      const x = getX(pt.date);
      if (pt.fundIndexed !== null) {
        const yFund = getY(pt.fundIndexed);
        if (i === 0) fundPath += `M ${x.toFixed(1)} ${yFund.toFixed(1)}`;
        else fundPath += ` L ${x.toFixed(1)} ${yFund.toFixed(1)}`;
      }

      if (pt.benchmarkIndexed !== null) {
        const yBench = getY(pt.benchmarkIndexed);
        if (firstBench) {
          benchPath += `M ${x.toFixed(1)} ${yBench.toFixed(1)}`;
          firstBench = false;
        } else {
          benchPath += ` L ${x.toFixed(1)} ${yBench.toFixed(1)}`;
        }
      }
    });

    const baselineY = getY(100.0);

    // 4 Y-ticks
    const yTicks = [
      yMin,
      yMin + ySpan * 0.33,
      yMin + ySpan * 0.66,
      yMax,
    ].map((v) => ({
      val: v,
      y: getY(v),
      label: `${v.toFixed(0)}`,
    }));

    // 3 X-ticks (Start, Mid, End)
    const midIdx = Math.floor(trajectory.length / 2);
    const xTicks = [
      { date: trajectory[0].date, x: padding.left },
      { date: trajectory[midIdx].date, x: padding.left + chartW / 2 },
      { date: trajectory[trajectory.length - 1].date, x: padding.left + chartW },
    ];

    return {
      width,
      height,
      padding,
      chartW,
      chartH,
      fundPath,
      benchPath,
      baselineY,
      yTicks,
      xTicks,
      getX,
      getY,
    };
  }, [trajectory]);

  if (loading) {
    return (
      <div className="panel space-y-4 p-4 md:p-5" aria-busy="true">
        <div className="skeleton h-3 w-1/3" />
        <div className="skeleton h-10 w-full" />
        <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
          <div className="skeleton h-28" />
          <div className="skeleton h-28" />
          <div className="skeleton h-28" />
        </div>
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="state-panel-error" role="alert">
        <p className="text-[15px] font-semibold tracking-[-0.01em] text-text-primary">
          Capture Ratio Analysis Unavailable
        </p>
        <p className="mt-1 text-[13px] leading-[1.5] text-critical-fg">
          {error || "Unable to compute capture ratio metrics from verified ledger observations."}
        </p>
      </div>
    );
  }

  const { context, metrics, epistemic } = data;

  // Safe visual gauge widths (0 to 150% visual scale)
  const ucWidth =
    metrics.upsideCaptureRatio != null
      ? Math.min(100, Math.max(5, (metrics.upsideCaptureRatio / 150) * 100))
      : 0;
  const dcWidth =
    metrics.downsideCaptureRatio != null
      ? Math.min(100, Math.max(5, (metrics.downsideCaptureRatio / 150) * 100))
      : 0;
  const baselineWidth = (100 / 150) * 100; // 66.67% represents 100% benchmark parity

  return (
    <section className="panel p-4 md:p-5" id="capture-ratios">
      {/* 1. Header & Mandate */}
      <div className="panel-header border-b border-border pb-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <p className="eyebrow text-accent">Market Participation &amp; Stress Analysis</p>
            <span className="status-badge state-approved">
              <span className="status-dot" aria-hidden />
              {epistemic.dataQualityStatus}
            </span>
            <span className="status-badge state-candidate">§MKT-03 / §MKT-04 / §MKT-05</span>
          </div>
          <h3 className="mt-1.5 text-h2 text-text-primary">
            Downside Capture, Upside Participation &amp; Stress-Event Analysis
          </h3>
          <p className="mono-meta mt-1 font-sans">
            Evaluates asymmetric benchmark participation and empirical behavior during pre-specified historical market drawdowns vs{" "}
            <span className="text-text-primary font-medium">{context.benchmarkName}</span>
          </p>
        </div>

        {/* Quick Asymmetry Indicator Badge & Cross-Links */}
        <div className="flex flex-wrap items-center gap-2 self-start md:self-auto">
          <div className="panel-inset shrink-0 px-3 py-2 text-right">
            <p className="def-label mb-0">Capture Asymmetry</p>
            {metrics.captureSpread != null ? (
              <p
                className={`data-value-md ${
                  metrics.captureSpread >= 0 ? "text-approved-fg" : "text-candidate-fg"
                }`}
              >
                {metrics.captureSpread >= 0 ? "+" : ""}
                {metrics.captureSpread.toFixed(2)} pp
              </p>
            ) : (
              <p className="data-unavailable">Insufficient Data</p>
            )}
          </div>
          <div className="flex flex-col gap-1">
            <a
              href="#rolling-consistency"
              className="tab text-xs px-2.5 py-1 text-center"
              title="Inspect rolling CAGR consistency"
            >
              Rolling Consistency →
            </a>
            <Link
              href={`/compare?funds=${schemeOptionId}`}
              className="tab text-xs px-2.5 py-1 text-center"
              title="Compare capture metrics across peers"
            >
              Compare Peers →
            </Link>
          </div>
        </div>
      </div>

      {/* Observation Window & Data Coverage Banner */}
      <div className="def-list def-list-4 mt-3">
        <div>
          <p className="def-label">Aligned Observation Window</p>
          <p className="def-value">
            {context.startDate ?? "Not available"} → {context.endDate ?? "Not available"}
          </p>
        </div>
        <div>
          <p className="def-label">Paired Trading Days</p>
          <p className="def-value">{metrics.totalPairedDays} Days</p>
        </div>
        <div>
          <p className="def-label">Up Days (Threshold ≥{metrics.minUpDaysRequired})</p>
          <p className="def-value text-approved-fg">{metrics.upDaysCount} Days</p>
        </div>
        <div>
          <p className="def-label">Down Days (Threshold ≥{metrics.minDownDaysRequired})</p>
          <p className="def-value text-risk-fg">{metrics.downDaysCount} Days</p>
        </div>
      </div>

      {/* 2. Core 3-Card Capture Metric Grid */}
      <div className="grid grid-cols-1 gap-4 pt-4 md:grid-cols-3">
        {/* Card 1: Upside Capture (MKT-03) */}
        <div className="metric-tile">
          <div className="panel-header">
            <p className="def-label mb-0">Upside Capture Ratio (3Y)</p>
            <span className="status-badge state-candidate shrink-0">§MKT-03</span>
          </div>

          {metrics.upsideStatus === "CALCULATED" ? (
            <>
              <p className="data-value-lg mt-2 text-approved-fg">
                {formatRatio(metrics.upsideCaptureRatio)}
              </p>
              <div className="metric-rule my-3" aria-hidden />
              <div className="space-y-1">
                <div className="flex items-baseline justify-between gap-3">
                  <span className="mono-meta font-sans">Fund Up Cumulative</span>
                  <span className="data-value-sm text-approved-fg">
                    {formatDecimalPct(metrics.fundUpCumulativeReturn)}
                  </span>
                </div>
                <div className="flex items-baseline justify-between gap-3">
                  <span className="mono-meta font-sans">Index Up Cumulative</span>
                  <span className="data-value-sm">
                    {formatDecimalPct(metrics.benchUpCumulativeReturn)}
                  </span>
                </div>
              </div>
              <p className="mono-meta mt-2 border-t border-border pt-2 font-sans leading-[1.45]">
                Compounded across {metrics.upDaysCount} positive benchmark days (threshold: ≥{metrics.minUpDaysRequired})
              </p>
            </>
          ) : (
            <div className="pt-2">
              <p className="data-value-md text-candidate-fg">Insufficient Positive Days</p>
              <p className="mono-meta mt-1 font-sans leading-[1.45]">
                {metrics.upDaysCount} positive days found; minimum {metrics.minUpDaysRequired} required by methodology.
              </p>
            </div>
          )}
        </div>

        {/* Card 2: Downside Capture (MKT-04) */}
        <div className="metric-tile metric-risk-rule">
          <div className="panel-header">
            <p className="def-label mb-0">Downside Capture Ratio (3Y)</p>
            <span className="status-badge state-candidate shrink-0">§MKT-04</span>
          </div>

          {metrics.downsideStatus === "CALCULATED" ? (
            <>
              <p className="data-value-lg mt-2 text-risk-fg">
                {formatRatio(metrics.downsideCaptureRatio)}
              </p>
              <div className="metric-rule my-3" aria-hidden />
              <div className="space-y-1">
                <div className="flex items-baseline justify-between gap-3">
                  <span className="mono-meta font-sans">Fund Down Cumulative</span>
                  <span className="data-value-sm text-risk-fg">
                    {formatDecimalPct(metrics.fundDownCumulativeReturn)}
                  </span>
                </div>
                <div className="flex items-baseline justify-between gap-3">
                  <span className="mono-meta font-sans">Index Down Cumulative</span>
                  <span className="data-value-sm">
                    {formatDecimalPct(metrics.benchDownCumulativeReturn)}
                  </span>
                </div>
              </div>
              <p className="mono-meta mt-2 border-t border-border pt-2 font-sans leading-[1.45]">
                Compounded across {metrics.downDaysCount} negative benchmark days (threshold: ≥{metrics.minDownDaysRequired})
              </p>
              {metrics.isInverseCaptureGain && (
                <div className="state-panel-error mt-3">
                  <p className="eyebrow text-critical-fg">INVERSE_CAPTURE_GAIN</p>
                  <p className="mt-1 text-[13px] leading-[1.5] text-critical-fg">
                    Fund produced positive cumulative gain during down-market days.
                  </p>
                </div>
              )}
            </>
          ) : (
            <div className="pt-2">
              <p className="data-value-md text-candidate-fg">Insufficient Negative Days</p>
              <p className="mono-meta mt-1 font-sans leading-[1.45]">
                {metrics.downDaysCount} negative days found; minimum {metrics.minDownDaysRequired} required by methodology.
              </p>
            </div>
          )}
        </div>

        {/* Card 3: Capture Spread (MKT-05) */}
        <div className="metric-tile">
          <div className="panel-header">
            <p className="def-label mb-0">Capture Spread (3Y)</p>
            <span className="status-badge state-candidate shrink-0">§MKT-05</span>
          </div>

          {metrics.spreadStatus === "CALCULATED" && metrics.captureSpread != null ? (
            <>
              <p
                className={`data-value-lg mt-2 ${
                  metrics.captureSpread >= 0 ? "text-approved-fg" : "text-candidate-fg"
                }`}
              >
                {metrics.captureSpread >= 0 ? "+" : ""}
                {metrics.captureSpread.toFixed(2)} pp
              </p>
              <div className="metric-rule my-3" aria-hidden />
              <div className="space-y-1">
                <div className="flex items-baseline justify-between gap-3">
                  <span className="mono-meta font-sans">Spread Formula</span>
                  <span className="data-value-sm">Upside Capture - Downside Capture</span>
                </div>
                <div className="flex items-baseline justify-between gap-3">
                  <span className="mono-meta font-sans">Constituents</span>
                  <span className="data-value-sm">
                    {formatPct(metrics.upsideCaptureRatio)} - {formatPct(metrics.downsideCaptureRatio)}
                  </span>
                </div>
              </div>
              <p className="mono-meta mt-2 border-t border-border pt-2 font-sans leading-[1.45]">
                Measures market participation asymmetry without evaluative ranking or composite score.
              </p>
            </>
          ) : (
            <div className="pt-2">
              <p className="data-value-md text-candidate-fg">Spread Undefined</p>
              <p className="mono-meta mt-1 font-sans leading-[1.45]">
                Both upside and downside capture ratios must meet minimum sample size requirements.
              </p>
            </div>
          )}
        </div>
      </div>

      {/* 3. Visual Asymmetric Participation Display vs Parity Baseline */}
      {metrics.upsideStatus === "CALCULATED" && metrics.downsideStatus === "CALCULATED" && (
        <div className="panel-inset mt-4 p-4">
          <div className="panel-header">
            <div>
              <p className="text-[13px] font-semibold text-text-primary">
                Market Participation vs Benchmark Parity Baseline (100.00%)
              </p>
              <p className="mono-meta mt-0.5">
                Values above 100% indicate amplified movement; values below 100% indicate dampened movement.
              </p>
            </div>
            <span className="mono-meta shrink-0">Scale: 0% → 150%</span>
          </div>

          <div className="space-y-4 pt-3">
            {/* Upside Participation Bar */}
            <div className="space-y-1">
              <div className="flex items-baseline justify-between gap-3">
                <span className="text-[13px] font-medium text-approved-fg">
                  Bull Market Participation (Upside Capture)
                </span>
                <span className="data-value-sm text-approved-fg">{formatRatio(metrics.upsideCaptureRatio)}</span>
              </div>
              <div className="relative h-3.5 w-full overflow-hidden rounded-sm border border-border bg-surface-inset">
                <div
                  className="absolute top-0 bottom-0 z-10 w-0.5 bg-text-primary"
                  style={{ left: `${baselineWidth}%` }}
                  title="Benchmark Parity (100%)"
                />
                <div
                  className="h-full bg-approved-fg transition-all duration-300"
                  style={{ width: `${ucWidth}%` }}
                />
              </div>
              <div className="flex justify-between gap-2 text-[11px] text-text-tertiary">
                <span>0%</span>
                <span className="text-text-secondary font-medium">100% Benchmark Parity</span>
                <span>150%</span>
              </div>
            </div>

            {/* Downside Absorption Bar */}
            <div className="space-y-1">
              <div className="flex items-baseline justify-between gap-3">
                <span className="text-[13px] font-medium text-risk-fg">
                  Bear Market Absorption (Downside Capture)
                </span>
                <span className="data-value-sm text-risk-fg">{formatRatio(metrics.downsideCaptureRatio)}</span>
              </div>
              <div className="relative h-3.5 w-full overflow-hidden rounded-sm border border-border bg-surface-inset">
                <div
                  className="absolute top-0 bottom-0 z-10 w-0.5 bg-text-primary"
                  style={{ left: `${baselineWidth}%` }}
                  title="Benchmark Parity (100%)"
                />
                <div
                  className="h-full bg-risk-fg transition-all duration-300"
                  style={{ width: `${dcWidth}%` }}
                />
              </div>
              <div className="flex justify-between gap-2 text-[11px] text-text-tertiary">
                <span>0% (Full Capital Defense)</span>
                <span className="text-text-secondary font-medium">100% Benchmark Parity</span>
                <span>150% (Amplified Loss)</span>
              </div>
            </div>
          </div>

          <div className="metric-rule my-3" aria-hidden />
          <p className="text-[12px] leading-[1.5] text-text-tertiary">
            <strong className="text-text-secondary font-medium">Defensive Asymmetry Interpretation:</strong> A lower downside capture ratio is not automatically superior. It must be evaluated alongside total realized return and upside capture. If lower downside capture stems from persistent high cash allocation, it may cause structural drag during sustained bull regimes.
          </p>
        </div>
      )}

      {/* 4. Historical Stress-Event Analysis Section */}
      <div className="mt-8 border-t border-border pt-6">
        <div className="panel-header pb-2">
          <div>
            <div className="flex items-center gap-2">
              <span className="status-dot text-risk-fg" aria-hidden />
              <p className="eyebrow text-risk-fg">Empirical Stress Replay</p>
            </div>
            <h4 className="mt-1 text-[16px] font-semibold text-text-primary">
              Historical Stress-Event Asymmetry &amp; Peak-to-Trough Drawdown
            </h4>
            <p className="mono-meta mt-0.5 font-sans">
              Replays dated historical stress episodes using real observations. Retrospective classification only — no predictive claim.
            </p>
          </div>

          {/* Event Selector Tab Rail */}
          <div className="tab-rail self-start md:self-auto" role="tablist" aria-label="Stress episodes">
            {stressEvents.map((event) => (
              <button
                key={event.eventId}
                type="button"
                role="tab"
                aria-selected={selectedEventId === event.eventId}
                onClick={() => setSelectedEventId(event.eventId)}
                className="tab"
                data-active={selectedEventId === event.eventId ? "true" : undefined}
              >
                {event.eventId}
              </button>
            ))}
          </div>
        </div>

        {/* 4a. Stress Event Comparison Table */}
        <div className="mt-4 overflow-x-auto">
          <table className="w-full text-left text-[13px] border-collapse" aria-label="Historical stress events table">
            <thead>
              <tr className="border-b border-border text-text-tertiary text-[11px] uppercase tracking-wider">
                <th className="pb-2 font-medium">Stress Episode</th>
                <th className="pb-2 font-medium">Dates &amp; Trading Days</th>
                <th className="pb-2 text-right font-medium">Fund Return</th>
                <th className="pb-2 text-right font-medium">Benchmark Return</th>
                <th className="pb-2 text-right font-medium">Excess Spread</th>
                <th className="pb-2 text-right font-medium">Fund Max Drawdown</th>
                <th className="pb-2 text-right font-medium">Bench Max Drawdown</th>
                <th className="pb-2 text-right font-medium">Coverage Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {stressEvents.map((ev) => {
                const isSelected = ev.eventId === selectedEventId;
                const isAvailable = ev.coverageStatus !== "OUTSIDE_OBSERVATION_HORIZON";
                const isComplete = ev.coverageStatus === "VERIFIED_COMPLETE_COVERAGE";
                const isBenchMissing = ev.coverageStatus === "BENCHMARK_UNAVAILABLE";

                return (
                  <tr
                    key={ev.eventId}
                    onClick={() => setSelectedEventId(ev.eventId)}
                    className={`cursor-pointer transition-colors hover:bg-surface-raised ${
                      isSelected ? "bg-surface-raised font-medium" : ""
                    }`}
                  >
                    <td className="py-2.5 pr-2">
                      <div className="flex items-center gap-1.5">
                        <span className="mono-meta text-accent">{ev.eventId}</span>
                        <span className="text-text-primary">{ev.name}</span>
                      </div>
                    </td>
                    <td className="py-2.5 pr-2 mono-meta font-sans">
                      {ev.startDate} → {ev.endDate} ({ev.tradingDays} days)
                    </td>
                    <td
                      className={`py-2.5 pr-2 text-right ${
                        ev.fundReturn !== null && ev.fundReturn < 0 ? "text-risk-fg" : "text-approved-fg"
                      }`}
                    >
                      {formatDecimalPct(ev.fundReturn)}
                    </td>
                    <td
                      className={`py-2.5 pr-2 text-right ${
                        ev.benchmarkReturn !== null && ev.benchmarkReturn < 0 ? "text-risk-fg" : ""
                      }`}
                    >
                      {isBenchMissing ? (
                        <span className="status-badge state-unavailable text-[10px]">Benchmark Unavailable</span>
                      ) : (
                        formatDecimalPct(ev.benchmarkReturn)
                      )}
                    </td>
                    <td
                      className={`py-2.5 pr-2 text-right ${
                        ev.excessReturn !== null && ev.excessReturn > 0
                          ? "text-approved-fg font-semibold"
                          : ev.excessReturn !== null && ev.excessReturn < 0
                          ? "text-risk-fg"
                          : ""
                      }`}
                    >
                      {formatDecimalPct(ev.excessReturn)}
                    </td>
                    <td className="py-2.5 pr-2 text-right text-risk-fg">
                      {formatDecimalPct(ev.fundMaxDrawdown)}
                    </td>
                    <td className="py-2.5 pr-2 text-right text-risk-fg">
                      {isBenchMissing ? "—" : formatDecimalPct(ev.benchmarkMaxDrawdown)}
                    </td>
                    <td className="py-2.5 text-right">
                      {isComplete ? (
                        <span className="status-badge state-approved">Verified</span>
                      ) : isBenchMissing ? (
                        <span className="status-badge state-candidate">Fund Only</span>
                      ) : !isAvailable ? (
                        <span className="status-badge state-unavailable">Outside Horizon</span>
                      ) : (
                        <span className="status-badge state-candidate">Partial</span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        {/* 4b. Selected Stress Event Replay & Trajectory Chart */}
        {selectedEvent && (
          <div className="panel-inset mt-4 p-4">
            <div className="panel-header border-b border-border pb-3">
              <div>
                <div className="flex items-center gap-2">
                  <span className="status-badge state-accent">{selectedEvent.eventId}</span>
                  <h5 className="text-[14px] font-semibold text-text-primary">{selectedEvent.name}</h5>
                </div>
                <p className="mt-1 text-[12px] leading-[1.5] text-text-secondary">
                  <strong className="text-text-primary">Classification Rule:</strong> {selectedEvent.classificationBasis}
                </p>
              </div>

              <div className="panel-inset shrink-0 px-3 py-1.5 text-right">
                <p className="def-label mb-0">Event Window</p>
                <p className="def-value mono-meta">
                  {selectedEvent.startDate} → {selectedEvent.endDate} ({selectedEvent.tradingDays} Days)
                </p>
              </div>
            </div>

            {/* Coverage Limitation Warning Callouts */}
            {selectedEvent.coverageStatus === "BENCHMARK_UNAVAILABLE" && (
              <div className="state-panel-error mt-3">
                <p className="eyebrow text-candidate-fg">BENCHMARK DATA UNAVAILABLE FOR THIS INTERVAL</p>
                <p className="mt-1 text-[13px] leading-[1.5] text-text-secondary">
                  The verified benchmark ledger for {context.benchmarkName} starts on 2021-01-15. Fund observations are verified from AMFI raw reports, but synchronous benchmark return cannot be paired without verified raw index data. Missing benchmark data is strictly distinguished from underperformance.
                </p>
              </div>
            )}

            {selectedEvent.coverageStatus === "OUTSIDE_OBSERVATION_HORIZON" && (
              <div className="state-well mt-3">
                <p className="eyebrow text-candidate-fg">EPISODE POST-DATES POINT-IN-TIME CUTOFF</p>
                <p className="mt-1 text-[13px] leading-[1.5] text-text-secondary">
                  This stress episode occurred after the selected historical analysis cutoff ({effectiveAsOfDate}). Observations are excluded to prevent look-ahead bias. To inspect this episode, adjust the analysis horizon selector to October 2026.
                </p>
              </div>
            )}

            {/* Interactive SVG Trajectory Chart */}
            {chartGeometry && selectedEvent.coverageStatus !== "OUTSIDE_OBSERVATION_HORIZON" && (
              <div className="mt-4">
                <div className="flex flex-wrap items-center justify-between gap-2 pb-2">
                  <p className="text-[12px] font-medium text-text-secondary">
                    Normalized Trajectory Replay (Inception Base = 100.00)
                  </p>
                  <div className="flex items-center gap-4 text-[12px]">
                    <div className="flex items-center gap-1.5">
                      <span className="h-2.5 w-2.5 rounded-full bg-accent" />
                      <span className="text-text-primary font-medium">{context.fundName}</span>
                    </div>
                    {selectedEvent.coverageStatus !== "BENCHMARK_UNAVAILABLE" && (
                      <div className="flex items-center gap-1.5">
                        <span className="h-2.5 w-2.5 rounded-full bg-text-tertiary" />
                        <span className="text-text-secondary">{context.benchmarkName}</span>
                      </div>
                    )}
                  </div>
                </div>

                <div className="relative overflow-hidden rounded-sm border border-border bg-surface-inset">
                  <svg
                    viewBox={`0 0 ${chartGeometry.width} ${chartGeometry.height}`}
                    className="w-full h-auto"
                    aria-label="Stress event normalized trajectory chart"
                  >
                    {/* Y-axis gridlines & labels */}
                    {chartGeometry.yTicks.map((t, idx) => (
                      <g key={idx}>
                        <line
                          x1={chartGeometry.padding.left}
                          y1={t.y}
                          x2={chartGeometry.width - chartGeometry.padding.right}
                          y2={t.y}
                          stroke="currentColor"
                          className="text-border"
                          strokeDasharray="3 3"
                        />
                        <text
                          x={chartGeometry.padding.left - 8}
                          y={t.y + 4}
                          textAnchor="end"
                          className="text-[10px] fill-text-tertiary font-mono"
                        >
                          {t.label}
                        </text>
                      </g>
                    ))}

                    {/* Baseline Parity line (100.0) */}
                    <line
                      x1={chartGeometry.padding.left}
                      y1={chartGeometry.baselineY}
                      x2={chartGeometry.width - chartGeometry.padding.right}
                      y2={chartGeometry.baselineY}
                      stroke="currentColor"
                      className="text-text-tertiary"
                      strokeWidth={1.5}
                    />

                    {/* Benchmark Path */}
                    {chartGeometry.benchPath && (
                      <path
                        d={chartGeometry.benchPath}
                        fill="none"
                        stroke="currentColor"
                        className="text-text-tertiary"
                        strokeWidth={1.8}
                        strokeDasharray="4 2"
                      />
                    )}

                    {/* Fund Path */}
                    {chartGeometry.fundPath && (
                      <path
                        d={chartGeometry.fundPath}
                        fill="none"
                        stroke="currentColor"
                        className="text-accent"
                        strokeWidth={2.4}
                      />
                    )}

                    {/* X-axis milestone dates */}
                    {chartGeometry.xTicks.map((xt, idx) => (
                      <text
                        key={idx}
                        x={xt.x}
                        y={chartGeometry.height - 12}
                        textAnchor={idx === 0 ? "start" : idx === 2 ? "end" : "middle"}
                        className="text-[10px] fill-text-tertiary font-mono"
                      >
                        {xt.date}
                      </text>
                    ))}
                  </svg>
                </div>

                {/* Trajectory Stat Insets */}
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-3">
                  <div className="panel-inset p-2.5">
                    <p className="def-label">Fund Period Return</p>
                    <p className={`data-value-sm ${selectedEvent.fundReturn && selectedEvent.fundReturn < 0 ? "text-risk-fg" : "text-approved-fg"}`}>
                      {formatDecimalPct(selectedEvent.fundReturn)}
                    </p>
                  </div>
                  <div className="panel-inset p-2.5">
                    <p className="def-label">Benchmark Period Return</p>
                    <p className="data-value-sm">
                      {selectedEvent.coverageStatus === "BENCHMARK_UNAVAILABLE" ? "Unavailable" : formatDecimalPct(selectedEvent.benchmarkReturn)}
                    </p>
                  </div>
                  <div className="panel-inset p-2.5">
                    <p className="def-label">Fund Max Drawdown</p>
                    <p className="data-value-sm text-risk-fg">{formatDecimalPct(selectedEvent.fundMaxDrawdown)}</p>
                  </div>
                  <div className="panel-inset p-2.5">
                    <p className="def-label">Benchmark Max Drawdown</p>
                    <p className="data-value-sm text-risk-fg">
                      {selectedEvent.coverageStatus === "BENCHMARK_UNAVAILABLE" ? "Unavailable" : formatDecimalPct(selectedEvent.benchmarkMaxDrawdown)}
                    </p>
                  </div>
                </div>
              </div>
            )}

            <p className="mt-3 text-[12px] leading-[1.5] text-text-tertiary">
              <strong className="text-text-secondary font-medium">Non-Persistence Disclaimer:</strong> {selectedEvent.retrospectiveDisclaimer}
            </p>
          </div>
        )}
      </div>

      {/* 5. Six-Part Progressive Disclosure Epistemic Framework */}
      <div className="mt-8 border-t border-border pt-6">
        <h4 className="text-[14px] font-semibold text-text-primary mb-3">
          Epistemic Evaluation: Observation, Interpretation &amp; Limitations
        </h4>

        <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
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
              <p className="eyebrow">Interpretation — Neutral Economic Meaning</p>
            </div>
            <p className="mt-2 text-[13px] leading-[1.55] text-text-secondary">
              {epistemic.interpretation}
            </p>
          </div>

          <div className="chain-col">
            <div className="flex items-baseline gap-2">
              <span className="tab-ordinal">03</span>
              <p className="eyebrow text-candidate-fg">Methodology Limitation</p>
            </div>
            <p className="mt-2 text-[13px] leading-[1.55] text-text-secondary">
              {epistemic.limitation}
            </p>
          </div>
        </div>

        {/* What Could Make This Wrong & Follow-Up Questions */}
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 mt-4">
          <div className="panel-inset p-4">
            <div className="flex items-baseline gap-2">
              <span className="status-dot text-candidate-fg" aria-hidden />
              <p className="eyebrow text-candidate-fg">What Could Make This Interpretation Wrong?</p>
            </div>
            <ul className="mt-2 space-y-1.5 text-[13px] leading-[1.5] text-text-secondary list-disc pl-4">
              <li>
                <strong>Regime-Shift Vulnerability:</strong> A fund that displayed defensive resilience during rate-hike consolidation may have benefited from value sector tailwinds that reverse in future liquidity crises.
              </li>
              <li>
                <strong>Non-Stationary Beta:</strong> Portfolio manager changes, style drift toward higher-beta midcaps, or increased concentration invalidate historical capture statistics.
              </li>
              <li>
                <strong>Compounding Artifact:</strong> Ratios calculated over non-contiguous subsets of calendar days do not represent an investable trading path.
              </li>
            </ul>
          </div>

          <div className="panel-inset p-4">
            <div className="flex items-baseline gap-2">
              <span className="status-dot text-accent" aria-hidden />
              <p className="eyebrow text-accent">Evidence-Based Questions for the Investor</p>
            </div>
            <ul className="mt-2 space-y-1.5 text-[13px] leading-[1.5] text-text-secondary list-disc pl-4">
              <li>
                <em>Did the fund hold elevated cash reserves during the stress periods, or was capital protected through stock selection?</em>
              </li>
              <li>
                <em>Did downside defense cause excessive drag when the market subsequently rebounded?</em>
              </li>
              <li>
                <em>How consistent was the downside capture across individual rolling 1Y windows versus the full 3Y aggregate?</em>
              </li>
            </ul>
          </div>
        </div>
      </div>

      {/* 6. Expandable Technical Lineage & Audit Drawer */}
      <div className="panel-inset mt-6">
        <button
          type="button"
          onClick={() => setShowAuditLineage((prev) => !prev)}
          aria-expanded={showAuditLineage}
          className="flex w-full items-center justify-between gap-3 p-3.5 text-left transition-colors hover:bg-surface-raised"
        >
          <span className="flex min-w-0 flex-wrap items-center gap-2">
            <span className="text-[13px] font-medium text-accent">
              Calculation Lineage &amp; Bitemporal Provenance
            </span>
            <span className="mono-meta">
              {showAuditLineage ? "Click to collapse" : "Click to inspect exact calculation provenance"}
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
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div className="def-list">
                <div>
                  <p className="def-label">Benchmark Alignment &amp; Zero Substitution</p>
                  <p className="def-value font-sans leading-[1.45]">{epistemic.benchmarkLineage}</p>
                </div>
                <div>
                  <p className="def-label">Total Paired Days</p>
                  <p className="def-value">
                    {metrics.totalPairedDays} calendar days (Up: {metrics.upDaysCount}, Down: {metrics.downDaysCount}, Flat: {metrics.flatDaysCount})
                  </p>
                </div>
                <div>
                  <p className="def-label">Flat Return Days (Rb = 0)</p>
                  <p className="def-value">
                    {metrics.flatDaysCount} days explicitly excluded per methodology
                  </p>
                </div>
              </div>

              <div className="def-list">
                <div>
                  <p className="def-label">Point-in-Time Cutoff Applied</p>
                  <p className="def-value font-mono text-[12px]">{context.knowledgeCutoffTime}</p>
                </div>
                <div>
                  <p className="def-label">Calculation Run ID</p>
                  <p className="def-value">{epistemic.calculationRunId ?? "Ephemeral In-Memory Audit"}</p>
                </div>
                <div>
                  <p className="def-label">Source Artifact SHA-256</p>
                  <p className="def-value text-accent select-all break-all font-mono text-[11px]">
                    {epistemic.sourceArtifactSha256}
                  </p>
                </div>
                <div>
                  <p className="def-label">Governance Status</p>
                  <p className="def-value text-candidate-fg">CANDIDATE / REQUIRES VALIDATION</p>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </section>
  );
}