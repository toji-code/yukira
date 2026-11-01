"use client";

import React, { useState, useEffect, useMemo } from "react";
import { apiFetch } from "@/lib/api/client";
import { fetchBenchmarkSeries } from "@/lib/api/analysis";
import { BenchmarkObservationSeriesDto } from "@/types/analysis";

interface ObservationItem {
  effectiveDate: string;
  navValue: number;
  sourceArtifactSha256?: string | null;
}

interface NavSeriesResponse {
  observations: ObservationItem[];
  summary: {
    totalObservations: number;
    firstObservationDate: string;
    latestObservationDate: string;
    firstNavValue: number;
    latestNavValue: number;
    hasContinuityGaps?: boolean;
    distinctSourceArtifactsCount?: number;
  };
}

export interface DrawdownEpisodeItem {
  episodeIndex: number;
  peakDate: string;
  troughDate: string;
  recoveryDate: string | null;
  peakNav: number;
  troughNav: number;
  drawdownPercent: number;
  underwaterDays: number;
  recoveryDays: number | null;
  episodeStatus: string;
}

export interface DrawdownEpisodesResponse {
  schemeOptionId: number;
  schemeName: string;
  asOfDate: string;
  maxDrawdown: number;
  maxDrawdownDurationDays: number;
  currentDrawdown: number;
  currentUnderwaterState: string;
  episodes: DrawdownEpisodeItem[];
  governanceNotice: string;
}

export interface HistoricalObservationChartProps {
  schemeOptionId: number;
  schemeName: string;
  benchmarkName?: string;
  benchmarkCode?: string;
  horizon: "3Y" | "5Y" | "10Y";
  asOfDate: string;
  knowledgeCutoffTime: string;
}

type ChartMode = "growth" | "drawdown" | "nav";

export function HistoricalObservationChart({
  schemeOptionId,
  schemeName,
  benchmarkName = "NIFTY 500 TRI",
  benchmarkCode = "NIFTY_500_TRI",
  horizon,
  asOfDate,
  knowledgeCutoffTime,
}: HistoricalObservationChartProps) {
  const [mode, setMode] = useState<ChartMode>("growth");
  const [navData, setNavData] = useState<NavSeriesResponse | null>(null);
  const [benchData, setBenchData] = useState<BenchmarkObservationSeriesDto | null>(null);
  const [episodesData, setEpisodesData] = useState<DrawdownEpisodesResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [hoverIndex, setHoverIndex] = useState<number | null>(null);

  // Derive start date based on requested horizon
  const startDate = useMemo(() => {
    try {
      const d = new Date(asOfDate);
      const years = horizon === "10Y" ? 10 : horizon === "5Y" ? 5 : 3;
      d.setFullYear(d.getFullYear() - years);
      return d.toISOString().split("T")[0];
    } catch {
      return horizon === "10Y" ? "2014-01-15" : horizon === "5Y" ? "2019-01-15" : "2021-01-15";
    }
  }, [asOfDate, horizon]);

  // Fetch NAV series and Benchmark TRI series concurrently
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);

    const navParams = new URLSearchParams({
      startDate,
      analysisCutoff: asOfDate,
      knowledgeCutoff: knowledgeCutoffTime,
    });

    const pNav = apiFetch<NavSeriesResponse>(
      `/api/v1/schemes/options/${schemeOptionId}/historical-series?${navParams.toString()}`
    );

    const pBench = fetchBenchmarkSeries(schemeOptionId, startDate, asOfDate, knowledgeCutoffTime).catch(() => null);

    const pEpisodes = apiFetch<DrawdownEpisodesResponse>(
      `/api/v1/analysis/drawdown-episodes/${schemeOptionId}?asOfDate=${asOfDate}&knowledgeCutoffTime=${knowledgeCutoffTime}`
    ).catch(() => null);

    Promise.all([pNav, pBench, pEpisodes])
      .then(([navRes, benchRes, epRes]) => {
        if (!active) return;
        setNavData(navRes);
        setBenchData(benchRes);
        setEpisodesData(epRes);
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (!active) return;
        setNavData(null);
        setBenchData(null);
        setEpisodesData(null);
        setError(err instanceof Error ? err.message : "Failed to load historical observation series");
        setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [schemeOptionId, startDate, asOfDate, knowledgeCutoffTime, horizon]);

  // Map and align synchronized trading dates
  const alignedSeries = useMemo(() => {
    if (!navData || !navData.observations || navData.observations.length === 0) {
      return { dates: [], navRebased: [], benchRebased: [], drawdowns: [], rawNavs: [] };
    }

    const navMap = new Map<string, number>();
    navData.observations.forEach((obs) => {
      navMap.set(obs.effectiveDate, Number(obs.navValue));
    });

    const benchMap = new Map<string, number>();
    if (benchData && benchData.observations) {
      benchData.observations.forEach((b) => {
        benchMap.set(b.effectiveDate, Number(b.triValue));
      });
    }

    // Sort ascending by date
    const sortedDates = Array.from(navMap.keys()).sort();
    if (sortedDates.length === 0) {
      return { dates: [], navRebased: [], benchRebased: [], drawdowns: [], rawNavs: [] };
    }

    const firstNav = navMap.get(sortedDates[0]) || 1;
    let firstBench: number | null = null;
    for (const d of sortedDates) {
      if (benchMap.has(d)) {
        firstBench = benchMap.get(d)!;
        break;
      }
    }

    // Peak tracking for drawdown calculation
    let peak = -Infinity;
    const drawdowns: number[] = [];
    const navRebased: number[] = [];
    const benchRebased: (number | null)[] = [];
    const rawNavs: number[] = [];

    sortedDates.forEach((d) => {
      const nav = navMap.get(d)!;
      rawNavs.push(nav);

      // Rebased to 100
      navRebased.push((nav / firstNav) * 100);

      // Drawdown calculation: (NAV_t / Peak - 1) * 100%
      if (nav > peak) peak = nav;
      const dd = peak > 0 ? ((nav - peak) / peak) * 100 : 0;
      drawdowns.push(dd);

      // Benchmark rebased to 100
      if (benchMap.has(d) && firstBench && firstBench > 0) {
        benchRebased.push((benchMap.get(d)! / firstBench) * 100);
      } else {
        benchRebased.push(null);
      }
    });

    return { dates: sortedDates, navRebased, benchRebased, drawdowns, rawNavs };
  }, [navData, benchData]);

  // Chart dimensions & coordinates
  const width = 860;
  const height = 320;
  const padding = { top: 24, right: 32, bottom: 36, left: 56 };
  const chartWidth = width - padding.left - padding.right;
  const chartHeight = height - padding.top - padding.bottom;

  const { minVal, maxVal } = useMemo(() => {
    if (alignedSeries.dates.length === 0) return { minVal: 0, maxVal: 100 };

    if (mode === "drawdown") {
      let minDd = 0;
      alignedSeries.drawdowns.forEach((dd) => {
        if (dd < minDd) minDd = dd;
      });
      return { minVal: Math.floor(minDd * 1.1) || -5, maxVal: 2 };
    }

    if (mode === "nav") {
      let minNav = Infinity;
      let maxNav = -Infinity;
      alignedSeries.rawNavs.forEach((v) => {
        if (v < minNav) minNav = v;
        if (v > maxNav) maxNav = v;
      });
      const buf = (maxNav - minNav) * 0.05 || 1;
      return { minVal: Math.max(0, Math.floor(minNav - buf)), maxVal: Math.ceil(maxNav + buf) };
    }

    // Growth (Base 100)
    let minG = 100;
    let maxG = 100;
    alignedSeries.navRebased.forEach((v) => {
      if (v < minG) minG = v;
      if (v > maxG) maxG = v;
    });
    alignedSeries.benchRebased.forEach((v) => {
      if (v !== null) {
        if (v < minG) minG = v;
        if (v > maxG) maxG = v;
      }
    });
    const buf = (maxG - minG) * 0.05 || 5;
    return { minVal: Math.floor(minG - buf), maxVal: Math.ceil(maxG + buf) };
  }, [alignedSeries, mode]);

  const getX = (idx: number) => {
    const total = alignedSeries.dates.length;
    if (total <= 1) return padding.left;
    return padding.left + (idx / (total - 1)) * chartWidth;
  };

  const getY = (val: number) => {
    if (maxVal === minVal) return padding.top + chartHeight / 2;
    return padding.top + chartHeight - ((val - minVal) / (maxVal - minVal)) * chartHeight;
  };

  const navPath = useMemo(() => {
    if (alignedSeries.dates.length === 0) return "";
    const values = mode === "drawdown" ? alignedSeries.drawdowns : mode === "nav" ? alignedSeries.rawNavs : alignedSeries.navRebased;
    return values.map((val, idx) => `${getX(idx).toFixed(1)},${getY(val).toFixed(1)}`).join(" ");
  }, [alignedSeries, mode, minVal, maxVal]);

  const benchPath = useMemo(() => {
    if (mode !== "growth" || alignedSeries.dates.length === 0) return "";
    const points: string[] = [];
    alignedSeries.benchRebased.forEach((val, idx) => {
      if (val !== null) {
        points.push(`${getX(idx).toFixed(1)},${getY(val).toFixed(1)}`);
      }
    });
    return points.join(" ");
  }, [alignedSeries, mode, minVal, maxVal]);

  const maxDrawdownVal = useMemo(() => {
    if (alignedSeries.drawdowns.length === 0) return 0;
    return Math.min(...alignedSeries.drawdowns);
  }, [alignedSeries.drawdowns]);

  const totalFundGrowth = useMemo(() => {
    if (alignedSeries.navRebased.length === 0) return 0;
    return alignedSeries.navRebased[alignedSeries.navRebased.length - 1] - 100;
  }, [alignedSeries.navRebased]);

  const totalBenchGrowth = useMemo(() => {
    if (alignedSeries.benchRebased.length === 0) return null;
    const valid = alignedSeries.benchRebased.filter((v): v is number => v !== null);
    if (valid.length === 0) return null;
    return valid[valid.length - 1] - 100;
  }, [alignedSeries.benchRebased]);

  return (
    <div className="panel border border-border bg-surface rounded-xl p-4 md:p-5 shadow-sm space-y-4">
      {/* HEADER & VIEW MODE SELECTOR */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-border/80 pb-3">
        <div>
          <div className="flex items-center gap-2">
            <span className="font-mono text-[10.5px] font-bold text-accent uppercase tracking-wider">
              {horizon} HISTORICAL OBSERVATION TRAJECTORY
            </span>
            <span className="status-badge state-approved text-[10px]">AUTHENTIC POINT-IN-TIME</span>
          </div>
          <h3 className="text-[16px] font-bold text-text-primary mt-1">
            {mode === "growth"
              ? `Rebased Cumulative Capital Growth (Base 100 · ${horizon})`
              : mode === "drawdown"
              ? `Historical Underwater Drawdown Path (${horizon})`
              : `Raw Official NAV Observations (₹ · ${horizon})`}
          </h3>
          <p className="mono-meta text-[11.5px] mt-0.5 text-text-tertiary">
            Window: {startDate} → {asOfDate} · Strict PIT cutoff: {knowledgeCutoffTime.split("T")[0]}
          </p>
        </div>

        {/* Tab Mode Buttons */}
        <div className="flex items-center bg-surface-raised p-1 rounded-lg border border-border/60">
          <button
            type="button"
            onClick={() => setMode("growth")}
            className={`px-3 py-1.5 rounded-md font-mono text-[11px] font-semibold transition-all ${
              mode === "growth"
                ? "bg-accent text-surface shadow-sm"
                : "text-text-secondary hover:text-text-primary"
            }`}
          >
            Growth (Base 100)
          </button>
          <button
            type="button"
            onClick={() => setMode("drawdown")}
            className={`px-3 py-1.5 rounded-md font-mono text-[11px] font-semibold transition-all ${
              mode === "drawdown"
                ? "bg-accent text-surface shadow-sm"
                : "text-text-secondary hover:text-text-primary"
            }`}
          >
            Drawdown Path (%)
          </button>
          <button
            type="button"
            onClick={() => setMode("nav")}
            className={`px-3 py-1.5 rounded-md font-mono text-[11px] font-semibold transition-all ${
              mode === "nav"
                ? "bg-accent text-surface shadow-sm"
                : "text-text-secondary hover:text-text-primary"
            }`}
          >
            Raw NAV (₹)
          </button>
        </div>
      </div>

      {/* METRIC BANNER OVERVIEW */}
      <div className="flex flex-wrap items-center justify-between gap-4 py-1 px-1 font-mono text-[12px]">
        <div className="flex flex-wrap items-center gap-4">
          <div className="flex items-center gap-1.5">
            <span className="w-2.5 h-2.5 rounded-full bg-accent" />
            <span className="text-text-primary font-semibold">{schemeName}</span>
            {mode === "growth" && (
              <span className={`font-bold ${totalFundGrowth >= 0 ? "text-emerald-400" : "text-rose-400"}`}>
                ({totalFundGrowth >= 0 ? "+" : ""}{totalFundGrowth.toFixed(2)}%)
              </span>
            )}
            {mode === "drawdown" && (
              <span className="text-amber-400 font-bold">
                (Max DD: {maxDrawdownVal.toFixed(2)}%)
              </span>
            )}
            {mode === "nav" && navData?.summary && (
              <span className="text-text-secondary font-bold">
                (₹{navData.summary.firstNavValue.toFixed(2)} → ₹{navData.summary.latestNavValue.toFixed(2)})
              </span>
            )}
          </div>

          {mode === "growth" && benchData && (
            <div className="flex items-center gap-1.5 text-text-tertiary">
              <span className="w-2.5 h-2.5 rounded-full bg-sky-400" />
              <span>{benchmarkName}</span>
              {totalBenchGrowth !== null && (
                <span className={`font-bold ${totalBenchGrowth >= 0 ? "text-sky-300" : "text-rose-300"}`}>
                  ({totalBenchGrowth >= 0 ? "+" : ""}{totalBenchGrowth.toFixed(2)}%)
                </span>
              )}
            </div>
          )}
        </div>

        <div className="text-text-tertiary text-[11px]">
          Observations: <strong className="text-text-primary">{alignedSeries.dates.length}</strong> trading sessions
        </div>
      </div>

      {/* CHART CONTENT AREA */}
      {loading ? (
        <div className="p-12 text-center space-y-3">
          <div className="h-6 w-6 animate-spin rounded-full border-2 border-accent border-t-transparent mx-auto" />
          <p className="font-mono text-[12px] text-text-tertiary">
            Loading authentic {horizon} market observation series…
          </p>
        </div>
      ) : error || alignedSeries.dates.length === 0 ? (
        <div className="p-8 text-center space-y-2 border border-amber-500/20 bg-amber-500/5 rounded-xl">
          <span className="font-mono text-amber-400 font-semibold text-[13px]">
            Insufficient Historical Observations for {horizon} Horizon
          </span>
          <p className="text-[12px] text-text-secondary max-w-[65ch] mx-auto leading-relaxed">
            {error || `This scheme option does not have sufficient continuous point-in-time observations to populate the full ${horizon} lookback window (${startDate} → ${asOfDate}). YUKIRA rejects synthetic interpolation.`}
          </p>
        </div>
      ) : (
        <div className="relative w-full overflow-hidden">
          <svg
            viewBox={`0 0 ${width} ${height}`}
            className="w-full h-auto select-none"
            onMouseLeave={() => setHoverIndex(null)}
            onMouseMove={(e) => {
              const rect = e.currentTarget.getBoundingClientRect();
              const mouseX = e.clientX - rect.left;
              const svgX = (mouseX / rect.width) * width;
              if (svgX >= padding.left && svgX <= width - padding.right && alignedSeries.dates.length > 1) {
                const fraction = (svgX - padding.left) / chartWidth;
                const idx = Math.min(
                  Math.max(0, Math.round(fraction * (alignedSeries.dates.length - 1))),
                  alignedSeries.dates.length - 1
                );
                setHoverIndex(idx);
              }
            }}
          >
            {/* Grid & Reference Lines */}
            {[minVal, mode === "growth" ? 100 : mode === "drawdown" ? 0 : Math.round((minVal + maxVal) / 2), maxVal].map((tick, i) => {
              const y = getY(tick);
              const isBase = (mode === "growth" && tick === 100) || (mode === "drawdown" && tick === 0);
              return (
                <g key={i}>
                  <line
                    x1={padding.left}
                    y1={y}
                    x2={width - padding.right}
                    y2={y}
                    stroke={isBase ? "var(--accent)" : "currentColor"}
                    strokeOpacity={isBase ? 0.4 : 0.08}
                    strokeDasharray={isBase ? "4 4" : undefined}
                  />
                  <text
                    x={padding.left - 8}
                    y={y + 3.5}
                    textAnchor="end"
                    className="fill-text-tertiary font-mono text-[9.5px]"
                  >
                    {mode === "drawdown" ? `${tick}%` : mode === "growth" ? tick : `₹${tick}`}
                  </text>
                </g>
              );
            })}

            {/* X-Axis Date Ticks */}
            {[0, Math.floor(alignedSeries.dates.length / 2), alignedSeries.dates.length - 1].map((idx) => {
              const x = getX(idx);
              const d = alignedSeries.dates[idx];
              return (
                <text
                  key={idx}
                  x={x}
                  y={height - 10}
                  textAnchor={idx === 0 ? "start" : idx === alignedSeries.dates.length - 1 ? "end" : "middle"}
                  className="fill-text-tertiary font-mono text-[10px]"
                >
                  {d}
                </text>
              );
            })}

            {/* Benchmark Polyline (Growth mode only) */}
            {mode === "growth" && benchPath && (
              <polyline
                fill="none"
                stroke="#38BDF8"
                strokeWidth={1.8}
                strokeDasharray="4 2"
                strokeLinejoin="round"
                strokeLinecap="round"
                points={benchPath}
              />
            )}

            {/* Fund Polyline */}
            {navPath && (
              <polyline
                fill="none"
                stroke={mode === "drawdown" ? "#F43F5E" : "var(--accent)"}
                strokeWidth={2.2}
                strokeLinejoin="round"
                strokeLinecap="round"
                points={navPath}
              />
            )}

            {/* Underwater Shading for Drawdown */}
            {mode === "drawdown" && navPath && (
              <polygon
                fill="#F43F5E"
                fillOpacity={0.12}
                points={`${padding.left},${getY(0)} ${navPath} ${getX(alignedSeries.dates.length - 1)},${getY(0)}`}
              />
            )}

            {/* Interactive Hover Crosshair */}
            {hoverIndex !== null && hoverIndex >= 0 && hoverIndex < alignedSeries.dates.length && (
              <g>
                <line
                  x1={getX(hoverIndex)}
                  y1={padding.top}
                  x2={getX(hoverIndex)}
                  y2={height - padding.bottom}
                  stroke="var(--accent)"
                  strokeWidth={1}
                  strokeDasharray="2 2"
                />
                <circle
                  cx={getX(hoverIndex)}
                  cy={getY(
                    mode === "drawdown"
                      ? alignedSeries.drawdowns[hoverIndex]
                      : mode === "nav"
                      ? alignedSeries.rawNavs[hoverIndex]
                      : alignedSeries.navRebased[hoverIndex]
                  )}
                  r={4.5}
                  fill="var(--accent)"
                />
              </g>
            )}
          </svg>

          {/* Hover Floating Details Card */}
          {hoverIndex !== null && hoverIndex >= 0 && hoverIndex < alignedSeries.dates.length && (
            <div
              className="absolute pointer-events-none p-2.5 rounded-lg border border-border bg-surface-raised shadow-xl font-mono text-[11px] z-10"
              style={{
                left: `${Math.min(Math.max(10, (hoverIndex / alignedSeries.dates.length) * 100), 75)}%`,
                top: "16px",
              }}
            >
              <div className="font-bold text-text-primary border-b border-border/60 pb-1 mb-1">
                {alignedSeries.dates[hoverIndex]}
              </div>
              <div className="space-y-0.5">
                <div className="flex items-center justify-between gap-3 text-text-primary">
                  <span>Fund NAV:</span>
                  <span className="font-bold">₹{alignedSeries.rawNavs[hoverIndex].toFixed(2)}</span>
                </div>
                {mode === "growth" && (
                  <div className="flex items-center justify-between gap-3 text-accent">
                    <span>Fund Rebased:</span>
                    <span className="font-bold">{alignedSeries.navRebased[hoverIndex].toFixed(2)}</span>
                  </div>
                )}
                {mode === "growth" && alignedSeries.benchRebased[hoverIndex] !== null && (
                  <div className="flex items-center justify-between gap-3 text-sky-400">
                    <span>Benchmark:</span>
                    <span className="font-bold">{alignedSeries.benchRebased[hoverIndex]!.toFixed(2)}</span>
                  </div>
                )}
                {mode === "drawdown" && (
                  <div className="flex items-center justify-between gap-3 text-rose-400">
                    <span>Drawdown:</span>
                    <span className="font-bold">{alignedSeries.drawdowns[hoverIndex].toFixed(2)}%</span>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      )}

      {/* DRAWDOWN EPISODES BREAKDOWN TABLE (Task 137) */}
      {mode === "drawdown" && episodesData && (
        <div className="pt-4 border-t border-border/70 space-y-3">
          <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-2">
            <div>
              <h4 className="text-xs font-semibold uppercase tracking-wider text-text-primary">
                Historical Drawdown Episodes & Recovery Analysis (Task 137)
              </h4>
              <p className="text-[11px] text-text-tertiary">
                Discrete peak-to-trough-to-recovery episodes. Maximum drawdown (RSK-03) and duration (RSK-04).
              </p>
            </div>
            <div className="flex items-center gap-3 text-xs font-mono">
              <span className="text-rose-400 font-bold">
                Max DD: {(episodesData.maxDrawdown * 100).toFixed(2)}%
              </span>
              <span className="text-text-secondary">
                Max Duration: {episodesData.maxDrawdownDurationDays}d
              </span>
              <span
                className={`px-1.5 py-0.5 rounded text-[10px] font-bold uppercase ${
                  episodesData.currentUnderwaterState === "RECOVERED"
                    ? "bg-emerald-950/60 text-emerald-400 border border-emerald-800/60"
                    : "bg-rose-950/60 text-rose-400 border border-rose-800/60"
                }`}
              >
                {episodesData.currentUnderwaterState}
              </span>
            </div>
          </div>

          {episodesData.episodes && episodesData.episodes.length > 0 ? (
            <div className="overflow-x-auto rounded border border-border/80">
              <table className="w-full text-left font-mono text-[11px]">
                <thead className="bg-surface-raised border-b border-border text-text-tertiary uppercase text-[10px]">
                  <tr>
                    <th className="py-2 px-3">#</th>
                    <th className="py-2 px-3">Peak Date</th>
                    <th className="py-2 px-3">Trough Date</th>
                    <th className="py-2 px-3">Depth</th>
                    <th className="py-2 px-3">Recovery Date</th>
                    <th className="py-2 px-3 text-right">Underwater Days</th>
                    <th className="py-2 px-3 text-right">Recovery Days</th>
                    <th className="py-2 px-3 text-right">State</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/60">
                  {episodesData.episodes.map((ep) => (
                    <tr
                      key={ep.episodeIndex}
                      className="hover:bg-surface-raised/50 transition-colors"
                    >
                      <td className="py-2 px-3 text-text-tertiary font-bold">
                        #{ep.episodeIndex}
                      </td>
                      <td className="py-2 px-3 text-text-primary">
                        {ep.peakDate}{" "}
                        <span className="text-text-tertiary text-[10px]">
                          (₹{ep.peakNav?.toFixed(2)})
                        </span>
                      </td>
                      <td className="py-2 px-3 text-rose-400 font-medium">
                        {ep.troughDate}{" "}
                        <span className="text-text-tertiary text-[10px]">
                          (₹{ep.troughNav?.toFixed(2)})
                        </span>
                      </td>
                      <td className="py-2 px-3 text-rose-400 font-bold">
                        {(ep.drawdownPercent * 100).toFixed(2)}%
                      </td>
                      <td className="py-2 px-3 text-text-secondary">
                        {ep.recoveryDate ? ep.recoveryDate : "—"}
                      </td>
                      <td className="py-2 px-3 text-right text-text-primary font-bold">
                        {ep.underwaterDays}d
                      </td>
                      <td className="py-2 px-3 text-right text-text-tertiary">
                        {ep.recoveryDays !== null ? `${ep.recoveryDays}d` : "—"}
                      </td>
                      <td className="py-2 px-3 text-right">
                        <span
                          className={`px-1.5 py-0.5 rounded text-[10px] font-bold uppercase ${
                            ep.episodeStatus === "RECOVERED"
                              ? "bg-emerald-950/40 text-emerald-400 border border-emerald-800/40"
                              : "bg-amber-950/40 text-amber-400 border border-amber-800/40"
                          }`}
                        >
                          {ep.episodeStatus}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="p-3 bg-surface-raised/40 border border-border/60 rounded text-text-tertiary text-xs">
              No material drawdown episodes exceeding 1.00% detected over this horizon.
            </div>
          )}
        </div>
      )}

      {/* METHODOLOGY DISCLAIMER */}
      <div className="pt-2 border-t border-border/60 flex flex-wrap items-center justify-between gap-2 text-[11px] font-mono text-text-tertiary">
        <span>Normalization Convention: Rebased to 100.00 at {startDate} start observation. Zero synthetic interpolation.</span>
        <span>Benchmark: {benchmarkCode}</span>
      </div>
    </div>
  );
}
