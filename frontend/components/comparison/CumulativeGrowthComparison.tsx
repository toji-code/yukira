"use client";

import { useEffect, useState, useMemo } from "react";
import { ComparisonFund } from "@/types/comparison";
import { apiFetch } from "@/lib/api/client";

interface ObservationItem {
  effectiveDate: string;
  navValue: number;
}

interface AnalyticalSeriesResponse {
  observations: ObservationItem[];
  summary: {
    totalObservations: number;
    firstObservationDate: string;
    latestObservationDate: string;
    firstNavValue: number;
    latestNavValue: number;
  };
}

interface FundGrowthSeries {
  fund: ComparisonFund;
  color: string;
  observations: ObservationItem[];
  rebased: { date: string; value: number; rawNav: number }[];
  totalGrowthPct: number;
  loading: boolean;
  error: string | null;
}

const FUND_COLORS = [
  "#F59E0B", // Gold / Amber (YUKIRA accent)
  "#10B981", // Emerald
  "#8B5CF6", // Violet
  "#38BDF8", // Sky
];

export function CumulativeGrowthComparison({
  funds,
  asOfDate,
  knowledgeCutoff,
}: {
  funds: ComparisonFund[];
  asOfDate: string;
  knowledgeCutoff: string;
}) {
  const [seriesData, setSeriesData] = useState<Map<number, FundGrowthSeries>>(new Map());
  const [hoverIndex, setHoverIndex] = useState<number | null>(null);

  // 3-year lookback date from asOfDate
  const startDate = useMemo(() => {
    try {
      const d = new Date(asOfDate);
      d.setFullYear(d.getFullYear() - 3);
      return d.toISOString().split("T")[0];
    } catch {
      return "2021-01-15";
    }
  }, [asOfDate]);

  // Fetch historical observation series for all compared funds
  useEffect(() => {
    if (!funds || funds.length === 0) return;

    let isMounted = true;

    funds.forEach((fund, idx) => {
      const color = FUND_COLORS[idx % FUND_COLORS.length];
      const optionId = fund.schemeOptionId;

      // Initial loading state
      setSeriesData((prev) => {
        const next = new Map(prev);
        if (!next.has(optionId)) {
          next.set(optionId, {
            fund,
            color,
            observations: [],
            rebased: [],
            totalGrowthPct: 0,
            loading: true,
            error: null,
          });
        }
        return next;
      });

      const params = new URLSearchParams({
        startDate,
        analysisCutoff: asOfDate,
        knowledgeCutoff,
      });

      apiFetch<AnalyticalSeriesResponse>(
        `/api/v1/schemes/options/${optionId}/historical-series?${params.toString()}`
      )
        .then((res) => {
          if (!isMounted) return;
          const obs = res.observations || [];
          setSeriesData((prev) => {
            const next = new Map(prev);
            next.set(optionId, {
              fund,
              color,
              observations: obs,
              rebased: [],
              totalGrowthPct: 0,
              loading: false,
              error: null,
            });
            return next;
          });
        })
        .catch((err: unknown) => {
          if (!isMounted) return;
          setSeriesData((prev) => {
            const next = new Map(prev);
            next.set(optionId, {
              fund,
              color,
              observations: [],
              rebased: [],
              totalGrowthPct: 0,
              loading: false,
              error: err instanceof Error ? err.message : "Failed to load historical series",
            });
            return next;
          });
        });
    });

    return () => {
      isMounted = false;
    };
  }, [funds, startDate, asOfDate, knowledgeCutoff]);

  // Determine common intersection dates and rebase each series to 100 on common start date
  const processedSeries = useMemo(() => {
    const list: FundGrowthSeries[] = [];
    const dateSets: Set<string>[] = [];

    funds.forEach((f) => {
      const s = seriesData.get(f.schemeOptionId);
      if (s && !s.loading && !s.error && s.observations.length > 0) {
        list.push(s);
        dateSets.push(new Set(s.observations.map((o) => o.effectiveDate)));
      }
    });

    if (list.length === 0) return { seriesList: [], commonDates: [] };

    // Find dates present in all active series (intersection)
    const firstSet = dateSets[0];
    const commonDates = Array.from(firstSet)
      .filter((d) => dateSets.every((s) => s.has(d)))
      .sort();

    if (commonDates.length === 0) {
      return { seriesList: [], commonDates: [] };
    }

    const rebasedList = list.map((s) => {
      const obsMap = new Map<string, number>();
      s.observations.forEach((o) => obsMap.set(o.effectiveDate, o.navValue));

      const baseVal = obsMap.get(commonDates[0]) || 1;
      const rebased = commonDates.map((d) => {
        const raw = obsMap.get(d) || baseVal;
        return {
          date: d,
          value: (raw / baseVal) * 100,
          rawNav: raw,
        };
      });

      const finalVal = rebased[rebased.length - 1].value;
      const totalGrowthPct = ((finalVal - 100) / 100) * 100;

      return {
        ...s,
        rebased,
        totalGrowthPct,
      };
    });

    return { seriesList: rebasedList, commonDates };
  }, [funds, seriesData]);

  const { seriesList, commonDates } = processedSeries;
  const isLoading = funds.some((f) => seriesData.get(f.schemeOptionId)?.loading);
  const hasErrors = funds.every((f) => seriesData.get(f.schemeOptionId)?.error);

  // SVG dimensions & scaling
  const width = 800;
  const height = 300;
  const padding = { top: 20, right: 30, bottom: 35, left: 50 };
  const chartWidth = width - padding.left - padding.right;
  const chartHeight = height - padding.top - padding.bottom;

  const { minVal, maxVal } = useMemo(() => {
    let min = 100;
    let max = 100;
    seriesList.forEach((s) => {
      s.rebased.forEach((pt) => {
        if (pt.value < min) min = pt.value;
        if (pt.value > max) max = pt.value;
      });
    });
    // Add 5% headroom
    const buffer = (max - min) * 0.05 || 5;
    return { minVal: Math.floor(min - buffer), maxVal: Math.ceil(max + buffer) };
  }, [seriesList]);

  const getX = (idx: number) => {
    if (commonDates.length <= 1) return padding.left;
    return padding.left + (idx / (commonDates.length - 1)) * chartWidth;
  };

  const getY = (val: number) => {
    if (maxVal === minVal) return padding.top + chartHeight / 2;
    return padding.top + chartHeight - ((val - minVal) / (maxVal - minVal)) * chartHeight;
  };

  return (
    <section className="space-y-4">
      {/* Section Header */}
      <div className="flex flex-wrap items-end justify-between gap-3 border-b border-border pb-3">
        <div>
          <div className="flex items-center gap-2">
            <span className="tab-ordinal font-mono text-[11px] font-bold text-accent">04. RETURN DIVERGENCE</span>
            <span className="status-badge state-verified text-[10px]">POINT-IN-TIME CUMULATIVE GROWTH</span>
          </div>
          <h3 className="text-h2 text-text-primary mt-1">
            Rebased Cumulative Growth (Base 100)
          </h3>
          <p className="mono-meta mt-0.5">
            Normalized cumulative growth of 100 currency units invested on common start date ({commonDates[0] || startDate} → {commonDates[commonDates.length - 1] || asOfDate}).
          </p>
        </div>
        <div className="flex items-center gap-4 font-mono text-[11px]">
          {seriesList.map((s) => (
            <div key={s.fund.schemeOptionId} className="flex items-center gap-1.5">
              <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: s.color }} />
              <span className="text-text-primary font-semibold truncate max-w-[150px]">{s.fund.schemeName}</span>
              <span className="text-text-secondary">({s.totalGrowthPct >= 0 ? "+" : ""}{s.totalGrowthPct.toFixed(1)}%)</span>
            </div>
          ))}
        </div>
      </div>

      {isLoading ? (
        <div className="panel p-8 text-center space-y-3">
          <div className="h-6 w-6 animate-spin rounded-full border-2 border-accent border-t-transparent mx-auto" />
          <p className="font-mono text-[12px] text-text-muted">Loading historical observations for compared funds…</p>
        </div>
      ) : hasErrors || commonDates.length === 0 ? (
        <div className="panel p-6 text-center space-y-2 border-l-2 border-l-amber-500">
          <span className="font-mono text-amber-400 font-semibold text-[13px]">Insufficient Intersecting Historical Observations</span>
          <p className="text-[12px] text-text-secondary max-w-[70ch] mx-auto">
            Unable to establish a synchronized daily calendar intersection across all selected funds. One or more funds lack historical daily NAV records over this horizon.
          </p>
        </div>
      ) : (
        <div className="panel p-4 bg-surface space-y-4">
          {/* SVG Chart Container */}
          <div className="relative w-full overflow-hidden">
            <svg
              viewBox={`0 0 ${width} ${height}`}
              className="w-full h-auto select-none"
              onMouseLeave={() => setHoverIndex(null)}
              onMouseMove={(e) => {
                const rect = e.currentTarget.getBoundingClientRect();
                const mouseX = e.clientX - rect.left;
                const svgX = (mouseX / rect.width) * width;
                if (svgX >= padding.left && svgX <= width - padding.right && commonDates.length > 1) {
                  const fraction = (svgX - padding.left) / chartWidth;
                  const idx = Math.min(
                    Math.max(0, Math.round(fraction * (commonDates.length - 1))),
                    commonDates.length - 1
                  );
                  setHoverIndex(idx);
                }
              }}
            >
              <defs>
                {/* Horizontal Baseline (100) */}
                <linearGradient id="grid-grad" x1="0%" y1="0%" x2="100%" y2="0%">
                  <stop offset="0%" stopColor="var(--border)" stopOpacity="0.2" />
                  <stop offset="100%" stopColor="var(--border)" stopOpacity="0.8" />
                </linearGradient>
              </defs>

              {/* Y-Axis Grid Lines & Labels */}
              {[minVal, 100, Math.round((minVal + maxVal) / 2), maxVal].map((tick, i) => {
                const y = getY(tick);
                return (
                  <g key={i}>
                    <line
                      x1={padding.left}
                      y1={y}
                      x2={width - padding.right}
                      y2={y}
                      stroke={tick === 100 ? "var(--accent)" : "currentColor"}
                      strokeOpacity={tick === 100 ? 0.35 : 0.08}
                      strokeDasharray={tick === 100 ? "3 3" : undefined}
                    />
                    <text
                      x={padding.left - 8}
                      y={y + 3.5}
                      textAnchor="end"
                      className="fill-text-muted font-mono text-[9.5px]"
                    >
                      {tick}
                    </text>
                  </g>
                );
              })}

              {/* X-Axis Date Labels (First, Middle, Last) */}
              {[0, Math.floor(commonDates.length / 2), commonDates.length - 1].map((idx) => {
                const x = getX(idx);
                const d = commonDates[idx];
                return (
                  <text
                    key={idx}
                    x={x}
                    y={height - 10}
                    textAnchor={idx === 0 ? "start" : idx === commonDates.length - 1 ? "end" : "middle"}
                    className="fill-text-muted font-mono text-[10px]"
                  >
                    {d}
                  </text>
                );
              })}

              {/* Rebased Growth Lines for each fund */}
              {seriesList.map((s) => {
                const points = s.rebased.map((pt, i) => `${getX(i)},${getY(pt.value)}`).join(" ");
                return (
                  <polyline
                    key={s.fund.schemeOptionId}
                    fill="none"
                    stroke={s.color}
                    strokeWidth={2}
                    strokeLinejoin="round"
                    strokeLinecap="round"
                    points={points}
                  />
                );
              })}

              {/* Hover Crosshair & Indicators */}
              {hoverIndex !== null && hoverIndex >= 0 && hoverIndex < commonDates.length && (
                <g>
                  {/* Vertical Crosshair Line */}
                  <line
                    x1={getX(hoverIndex)}
                    y1={padding.top}
                    x2={getX(hoverIndex)}
                    y2={height - padding.bottom}
                    stroke="var(--accent)"
                    strokeWidth={1}
                    strokeDasharray="2 2"
                    strokeOpacity={0.7}
                  />
                  {/* Circle Dots on each series */}
                  {seriesList.map((s) => {
                    const pt = s.rebased[hoverIndex];
                    if (!pt) return null;
                    return (
                      <circle
                        key={s.fund.schemeOptionId}
                        cx={getX(hoverIndex)}
                        cy={getY(pt.value)}
                        r={4}
                        fill={s.color}
                        stroke="#0F172A"
                        strokeWidth={2}
                      />
                    );
                  })}
                </g>
              )}
            </svg>

            {/* Hover Tooltip Overlay */}
            {hoverIndex !== null && hoverIndex >= 0 && hoverIndex < commonDates.length && (
              <div
                className="pointer-events-none absolute top-2 right-4 panel-inset p-2.5 bg-surface-raised/95 backdrop-blur-sm border border-border shadow-lg font-mono text-[11px] space-y-1 min-w-[200px]"
              >
                <div className="text-text-muted font-bold text-[10px] pb-1 border-b border-border/50">
                  DATE: {commonDates[hoverIndex]}
                </div>
                {seriesList.map((s) => {
                  const pt = s.rebased[hoverIndex];
                  if (!pt) return null;
                  const diff = pt.value - 100;
                  return (
                    <div key={s.fund.schemeOptionId} className="flex items-center justify-between gap-3">
                      <span className="flex items-center gap-1.5 truncate max-w-[130px]">
                        <span className="h-2 w-2 rounded-full shrink-0" style={{ backgroundColor: s.color }} />
                        <span className="truncate">{s.fund.schemeName}</span>
                      </span>
                      <span className="font-semibold text-text-primary shrink-0">
                        {pt.value.toFixed(2)} ({diff >= 0 ? "+" : ""}{diff.toFixed(1)}%)
                      </span>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* Bottom Side-by-Side Summary Cards */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3 pt-2 border-t border-border/60">
            {seriesList.map((s) => (
              <div
                key={s.fund.schemeOptionId}
                className="panel-inset p-3 bg-surface-raised rounded font-mono text-[11px] space-y-1 border-t-2"
                style={{ borderTopColor: s.color }}
              >
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-text-primary truncate max-w-[180px]">{s.fund.schemeName}</span>
                  <span className="font-bold" style={{ color: s.color }}>
                    {s.totalGrowthPct >= 0 ? "+" : ""}{s.totalGrowthPct.toFixed(2)}%
                  </span>
                </div>
                <div className="flex justify-between text-text-secondary text-[10px]">
                  <span>Initial NAV ({commonDates[0]}):</span>
                  <span>₹{s.rebased[0]?.rawNav?.toFixed(2) || "—"}</span>
                </div>
                <div className="flex justify-between text-text-secondary text-[10px]">
                  <span>Terminal NAV ({commonDates[commonDates.length - 1]}):</span>
                  <span>₹{s.rebased[s.rebased.length - 1]?.rawNav?.toFixed(2) || "—"}</span>
                </div>
                <div className="flex justify-between text-text-muted text-[9.5px] pt-0.5 border-t border-border/40">
                  <span>Common Trading Days:</span>
                  <span>N = {commonDates.length} observations</span>
                </div>
              </div>
            ))}
          </div>

          {/* Epistemic Label */}
          <div className="text-[10.5px] font-mono text-text-muted leading-relaxed">
            * <span className="font-semibold">Pure Point-in-Time Discipline:</span> Rebased curves reflect exact daily AMFI NAV closing prices without interpolation, linear filling, or look-ahead revision leakage. If a trading day was a market holiday for one instrument, only synchronized paired trading dates are plotted.
          </div>
        </div>
      )}
    </section>
  );
}
