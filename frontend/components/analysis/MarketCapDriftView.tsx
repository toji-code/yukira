"use client";

import React, { useState, useEffect, useMemo } from "react";
import {
  fetchMarketCapDrift,
  MarketCapDriftResponseDto,
  MarketCapObservationDto,
} from "@/lib/api/portfolio";

interface MarketCapDriftViewProps {
  schemeOptionId: number;
  asOfDate?: string;
  knowledgeCutoffTime?: string;
  onInvestigateQuestion?: (question: string, reason?: string) => void;
}

type SubTab = "ALLOCATION" | "WATERFALL" | "TIMELINE" | "RECLASSIFICATIONS";

export function MarketCapDriftView({
  schemeOptionId,
  asOfDate: propAsOfDate,
  knowledgeCutoffTime: propCutoffTime,
  onInvestigateQuestion,
}: MarketCapDriftViewProps) {
  const [data, setData] = useState<MarketCapDriftResponseDto | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedSubTab, setSelectedSubTab] = useState<SubTab>("ALLOCATION");
  const [selectedSnapshotIndex, setSelectedSnapshotIndex] = useState<number>(0);
  const [expandedTransitions, setExpandedTransitions] = useState<Set<string>>(new Set());
  const [showMethodologyInfo, setShowMethodologyInfo] = useState<boolean>(false);
  const [showNormalized, setShowNormalized] = useState<boolean>(false);

  const effectiveAsOfDate = propAsOfDate || "2026-10-01";
  const effectiveCutoffTime = propCutoffTime || "2026-10-10T23:59:59+05:30";

  useEffect(() => {
    let isMounted = true;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLoading(true);
    setError(null);

    fetchMarketCapDrift(schemeOptionId, effectiveAsOfDate, undefined, undefined, effectiveCutoffTime)
      .then((res) => {
        if (!isMounted) return;
        setData(res);
        if (res.observations && res.observations.length > 0) {
          const latestIdx = res.observations.length - 1;
          setSelectedSnapshotIndex(latestIdx);
          // Auto-expand latest transition
          if (res.transitions && res.transitions.length > 0) {
            const firstTransKey = `${res.transitions[0].earlierDate}::${res.transitions[0].laterDate}::${res.transitions[0].category}`;
            setExpandedTransitions(new Set([firstTransKey]));
          }
        }
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (!isMounted) return;
        setError(err instanceof Error ? err.message : "Failed to load market-cap drift analysis.");
        setLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, [schemeOptionId, effectiveAsOfDate, effectiveCutoffTime]);

  // Selected Observation
  const selectedObs: MarketCapObservationDto | null = useMemo(() => {
    if (!data?.observations || data.observations.length === 0) return null;
    const idx = Math.max(0, Math.min(selectedSnapshotIndex, data.observations.length - 1));
    return data.observations[idx];
  }, [data, selectedSnapshotIndex]);

  const toggleTransition = (key: string) => {
    setExpandedTransitions((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  };

  const expandAllTransitions = () => {
    if (!data?.transitions) return;
    const all = new Set(data.transitions.map((t) => `${t.earlierDate}::${t.laterDate}::${t.category}`));
    setExpandedTransitions(all);
  };

  const collapseAllTransitions = () => {
    setExpandedTransitions(new Set());
  };

  const formatPct = (val: number | null | undefined): string => {
    if (val === null || val === undefined) return "—";
    return `${val >= 0 ? "+" : ""}${val.toFixed(2)} pp`;
  };

  const getBucketBarColor = (category: string) => {
    switch (category) {
      case "LARGE_CAP":
        return "bg-blue-600";
      case "MID_CAP":
        return "bg-teal-600";
      case "SMALL_CAP":
        return "bg-amber-500";
      case "CASH":
        return "bg-emerald-600";
      case "DEBT_OTHER":
        return "bg-purple-600";
      default:
        return "bg-slate-500";
    }
  };

  if (loading) {
    return (
      <div className="panel p-6 space-y-4 animate-pulse">
        <div className="h-6 bg-surface-elevated rounded w-1/3"></div>
        <div className="h-4 bg-surface-elevated rounded w-2/3"></div>
        <div className="grid grid-cols-2 md:grid-cols-6 gap-3 pt-4">
          {[1, 2, 3, 4, 5, 6].map((i) => (
            <div key={i} className="h-20 bg-surface-elevated rounded"></div>
          ))}
        </div>
        <div className="h-48 bg-surface-elevated rounded mt-4"></div>
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="panel p-6 border-danger/30 bg-danger/5">
        <div className="flex items-center gap-2 text-danger font-semibold text-[13px]">
          <span>⚠ Point-in-Time Market-Cap Analysis Unavailable</span>
        </div>
        <p className="mt-1 text-[12px] text-text-secondary">
          {error || "No portfolio snapshot observations found under Point-in-Time cutoffs."}
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* 1. Header & Institutional Context */}
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between border-b border-border/70 pb-4">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <span className="badge badge-success text-[10px] uppercase tracking-wider">
              {data.observations[0]?.classificationStatus === "VERIFIED_HISTORICAL_PIT"
                ? "AMFI Point-in-Time Verified"
                : "SEBI Reference Standard"}
            </span>
            <span className="mono-meta text-[11px] text-text-muted">
              Benchmark: {data.benchmarkCode} ({data.benchmarkName})
            </span>
            <span className="mono-meta text-[11px] text-text-muted">
              • Cutoff: {data.knowledgeCutoff.slice(0, 19).replace("T", " ")}
            </span>
          </div>
          <h3 className="mt-1 text-[16px] font-bold tracking-tight text-text-primary">
            Market-Cap Style Drift &amp; Allocation Decomposition
          </h3>
          <p className="text-[12px] text-text-secondary">
            Descriptive portfolio construction analysis separating active weight adjustments from AMFI semi-annual ranking shifts.
            Zero synthetic series or manager-skill score conversions.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setShowMethodologyInfo(!showMethodologyInfo)}
            className="btn btn-secondary btn-sm text-[11px]"
            aria-expanded={showMethodologyInfo}
          >
            {showMethodologyInfo ? "Hide Methodology" : "Methodology & Provenance"}
          </button>
        </div>
      </div>

      {/* Methodology & Lineage Drawer */}
      {showMethodologyInfo && (
        <div className="p-4 rounded-lg border border-border bg-surface-elevated/30 text-[12px] space-y-3">
          <div className="font-semibold text-text-primary flex items-center justify-between">
            <span>Deterministic Market-Cap Style Drift Methodology (Phase 2K)</span>
            <span className="mono-meta text-[10px] text-text-muted">{data.methodologyVersion}</span>
          </div>
          <p className="text-text-secondary leading-relaxed">
            Market-cap style categorization maps reported portfolio holdings directly to historical semi-annual AMFI ranking ledgers
            under strict point-in-time constraints (<code className="font-mono text-accent">effectiveFrom &le; portfolioDate &le; effectiveTo</code> and{" "}
            <code className="font-mono text-accent">availabilityTime &le; knowledgeCutoff</code>).
          </p>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3 pt-2">
            <div className="p-2.5 rounded bg-surface border border-border/50">
              <span className="font-semibold text-text-primary block text-[11px]">Mathematical Drift Decomposition</span>
              <p className="text-[11.5px] text-text-muted mt-1">
                For each category, observed change is decomposed into:
                <br />
                <span className="font-mono text-[11px] text-accent">
                  &Delta;W_obs = &Delta;W_weight + &Delta;W_reclass + &Delta;W_disclosure + &Delta;W_unresolved
                </span>
                <br />
                Reconciles within documented numerical tolerance (&plusmn;0.02 pp).
              </p>
            </div>
            <div className="p-2.5 rounded bg-surface border border-border/50">
              <span className="font-semibold text-text-primary block text-[11px]">Strict Benchmark Completeness Gate</span>
              <p className="text-[11.5px] text-text-muted mt-1">
                Because factsheet constituent weights only cover ~33% of NIFTY 500, active style comparisons are strictly gated with{" "}
                <code className="font-mono text-warning">GATED_INCOMPLETE_BENCHMARK_WEIGHTS</code> to prevent artificial active overweight distortion.
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Snapshot Date Selector */}
      {data.observations.length > 1 && (
        <div className="flex flex-wrap items-center justify-between gap-3 p-3 rounded-lg bg-surface border border-border/60">
          <div className="flex items-center gap-2">
            <span className="text-[11px] font-semibold text-text-muted uppercase tracking-wider">
              Disclosed Portfolio Date:
            </span>
            <div className="flex flex-wrap gap-1.5">
              {data.observations.map((obs, idx) => {
                const isSelected = idx === selectedSnapshotIndex;
                return (
                  <button
                    key={obs.snapshotId}
                    type="button"
                    onClick={() => setSelectedSnapshotIndex(idx)}
                    className={`px-2.5 py-1 text-[11.5px] font-medium rounded transition-colors ${
                      isSelected
                        ? "bg-primary text-primary-fg shadow-sm"
                        : "bg-surface-elevated/60 text-text-secondary hover:text-text-primary hover:bg-surface-elevated"
                    }`}
                  >
                    {obs.portfolioDate}
                    {obs.sourceRankingPeriod ? (
                      <span className="ml-1.5 text-[9.5px] opacity-80 font-mono">
                        ({obs.sourceRankingPeriod})
                      </span>
                    ) : null}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="flex items-center gap-2 text-[11.5px]">
            <span className="text-text-muted">Display Weights:</span>
            <button
              type="button"
              onClick={() => setShowNormalized(false)}
              className={`px-2 py-0.5 rounded text-[11px] font-medium ${
                !showNormalized ? "bg-surface-elevated text-text-primary font-bold border border-border" : "text-text-muted"
              }`}
            >
              Raw Reported
            </button>
            <button
              type="button"
              onClick={() => setShowNormalized(true)}
              className={`px-2 py-0.5 rounded text-[11px] font-medium ${
                showNormalized ? "bg-surface-elevated text-text-primary font-bold border border-border" : "text-text-muted"
              }`}
            >
              Normalized to 100%
            </button>
          </div>
        </div>
      )}

      {/* 2. Sub-Tabs */}
      <div className="flex items-center gap-1 border-b border-border/60 overflow-x-auto text-[12px]">
        <button
          type="button"
          onClick={() => setSelectedSubTab("ALLOCATION")}
          className={`px-3 py-2 font-medium transition-colors border-b-2 -mb-[1px] whitespace-nowrap ${
            selectedSubTab === "ALLOCATION"
              ? "border-primary text-primary font-semibold"
              : "border-transparent text-text-secondary hover:text-text-primary"
          }`}
        >
          Observed Allocation &amp; Reconciliation
        </button>
        <button
          type="button"
          onClick={() => setSelectedSubTab("WATERFALL")}
          className={`px-3 py-2 font-medium transition-colors border-b-2 -mb-[1px] whitespace-nowrap ${
            selectedSubTab === "WATERFALL"
              ? "border-primary text-primary font-semibold"
              : "border-transparent text-text-secondary hover:text-text-primary"
          }`}
        >
          Style-Drift Decomposition Waterfall ({data.transitions.length})
        </button>
        <button
          type="button"
          onClick={() => setSelectedSubTab("TIMELINE")}
          className={`px-3 py-2 font-medium transition-colors border-b-2 -mb-[1px] whitespace-nowrap ${
            selectedSubTab === "TIMELINE"
              ? "border-primary text-primary font-semibold"
              : "border-transparent text-text-secondary hover:text-text-primary"
          }`}
        >
          Chronological Trajectory ({data.observations.length} Snapshots)
        </button>
        <button
          type="button"
          onClick={() => setSelectedSubTab("RECLASSIFICATIONS")}
          className={`px-3 py-2 font-medium transition-colors border-b-2 -mb-[1px] whitespace-nowrap ${
            selectedSubTab === "RECLASSIFICATIONS"
              ? "border-primary text-primary font-semibold"
              : "border-transparent text-text-secondary hover:text-text-primary"
          }`}
        >
          AMFI Reclassifications ({data.allReclassifiedHoldings.length})
        </button>
      </div>

      {/* SUB-TAB 1: OBSERVED ALLOCATION & RECONCILIATION */}
      {selectedSubTab === "ALLOCATION" && selectedObs && (
        <div className="space-y-6">
          {/* Summary Metric Tiles */}
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
            {selectedObs.bucketExposures.map((bucket) => {
              const weightVal = showNormalized ? bucket.normalizedWeightPct : bucket.reportedWeightPct;
              return (
                <div
                  key={bucket.category}
                  className="bg-surface-elevated/40 p-3.5 rounded-lg border border-border/60 flex flex-col justify-between"
                >
                  <div>
                    <span className="mono-meta block text-[10px] text-text-muted uppercase">
                      {bucket.category.replace("_", " ")}
                    </span>
                    <span className="mono-num text-[18px] font-bold text-text-primary mt-1 block">
                      {weightVal.toFixed(2)}%
                    </span>
                  </div>
                  <div className="mt-2 pt-2 border-t border-border/40 text-[11px] text-text-secondary flex items-center justify-between">
                    <span>{bucket.holdingCount} holdings</span>
                    <span className="text-[10px] opacity-75">{bucket.categoryLabel.split("(")[0].trim()}</span>
                  </div>
                </div>
              );
            })}
          </div>

          {/* Segmented Visual Allocation Bar */}
          <div className="bg-surface-elevated/20 p-4 rounded-lg border border-border/60 space-y-3">
            <div className="flex items-center justify-between text-[12px]">
              <span className="font-semibold text-text-primary">Portfolio Market-Cap &amp; Asset Class Composition</span>
              <span className="mono-meta text-[11px] text-text-muted">
                Observed: {selectedObs.portfolioDate} | Weight Coverage: {selectedObs.classificationCoverageWeightPct.toFixed(1)}% | Holdings: {selectedObs.totalHoldingsCount}
              </span>
            </div>

            <div className="flex h-6 w-full overflow-hidden rounded bg-surface border border-border/60">
              {selectedObs.bucketExposures
                .filter((b) => b.reportedWeightPct > 0)
                .map((b) => {
                  const w = showNormalized ? b.normalizedWeightPct : b.reportedWeightPct;
                  return (
                    <div
                      key={b.category}
                      style={{ width: `${Math.min(100, Math.max(0, w))}%` }}
                      className={`${getBucketBarColor(b.category)} transition-all relative group flex items-center justify-center`}
                      title={`${b.categoryLabel}: ${w.toFixed(2)}% (${b.holdingCount} holdings)`}
                    >
                      {w >= 6 && (
                        <span className="text-[10px] font-bold text-white tracking-wider px-1 truncate drop-shadow-sm">
                          {b.category.split("_")[0]} {w.toFixed(1)}%
                        </span>
                      )}
                    </div>
                  );
                })}
            </div>

            {/* Legend */}
            <div className="flex flex-wrap items-center gap-4 text-[11.5px] pt-1">
              {selectedObs.bucketExposures.map((b) => (
                <div key={b.category} className="flex items-center gap-1.5">
                  <span className={`w-3 h-3 rounded-xs ${getBucketBarColor(b.category)}`} />
                  <span className="text-text-secondary">{b.categoryLabel}</span>
                  <span className="mono-num font-semibold text-text-primary">
                    {(showNormalized ? b.normalizedWeightPct : b.reportedWeightPct).toFixed(2)}%
                  </span>
                </div>
              ))}
            </div>
          </div>

          {/* Portfolio-Weight Reconciliation & Provenance Audit Card */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="p-4 rounded-lg bg-surface border border-border/70 space-y-3">
              <span className="def-label">Portfolio-Weight Reconciliation Audit</span>
              <div className="space-y-2 text-[12px]">
                <div className="flex justify-between py-1 border-b border-border/40">
                  <span className="text-text-secondary">Sum of Disclosed Holdings Weights:</span>
                  <span className="mono-num font-bold text-text-primary">
                    {selectedObs.sumReportedWeightsPct.toFixed(2)}%
                  </span>
                </div>
                <div className="flex justify-between py-1 border-b border-border/40">
                  <span className="text-text-secondary">Reconciliation Excess / Deficit relative to 100%:</span>
                  <span
                    className={`mono-num font-bold ${
                      Math.abs(selectedObs.weightReconciliationDeltaPct) > 0.05 ? "text-warning" : "text-success"
                    }`}
                  >
                    {selectedObs.weightReconciliationDeltaPct >= 0 ? "+" : ""}
                    {selectedObs.weightReconciliationDeltaPct.toFixed(2)} pp
                  </span>
                </div>
                <div className="flex justify-between py-1 border-b border-border/40">
                  <span className="text-text-secondary">Disclosure Completeness Scope:</span>
                  <span className="badge badge-secondary text-[10px]">
                    {selectedObs.portfolioDisclosureScope}
                  </span>
                </div>
                <div className="flex justify-between py-1">
                  <span className="text-text-secondary">Unclassified / Unresolved Holdings:</span>
                  <span className="mono-num text-text-muted">
                    {selectedObs.unclassifiedWeightPct.toFixed(2)}% ({selectedObs.unclassifiedHoldingCount} holdings)
                  </span>
                </div>
              </div>
              <p className="text-[11px] text-text-muted leading-relaxed">
                YUKIRA never silently renormalizes partial portfolio disclosures to 100%. Raw disclosed weights reflect official AMC disclosures including cash netting, accrued liabilities, and derivative margins.
              </p>
            </div>

            <div className="p-4 rounded-lg bg-surface border border-border/70 space-y-3">
              <span className="def-label">AMFI Point-in-Time Classification Provenance</span>
              <div className="space-y-2 text-[12px]">
                <div className="flex justify-between py-1 border-b border-border/40">
                  <span className="text-text-secondary">Applicable AMFI Ranking Period:</span>
                  <span className="font-semibold text-text-primary">
                    {selectedObs.sourceRankingPeriod || "Approximate Reference"}
                  </span>
                </div>
                <div className="flex justify-between py-1 border-b border-border/40">
                  <span className="text-text-secondary">Effective Classification Window:</span>
                  <span className="mono-meta text-text-primary">
                    {selectedObs.effectiveFrom || "—"} to {selectedObs.effectiveTo || "—"}
                  </span>
                </div>
                <div className="flex justify-between py-1 border-b border-border/40">
                  <span className="text-text-secondary">AMFI Publication Date:</span>
                  <span className="mono-meta text-text-primary">
                    {selectedObs.sourcePublicationDate || "—"}
                  </span>
                </div>
                <div className="flex justify-between py-1">
                  <span className="text-text-secondary">Cryptographic Source Lineage:</span>
                  <span className="mono-meta text-[10px] text-accent truncate max-w-[200px]" title={selectedObs.classificationSourceArtifactSha256 || ""}>
                    {selectedObs.classificationSourceArtifactSha256 ? `SHA: ${selectedObs.classificationSourceArtifactSha256.slice(0, 16)}...` : "System Reference"}
                  </span>
                </div>
              </div>
              <p className="text-[11px] text-text-muted leading-relaxed">
                Every market-cap rank is anchored to the exact SEBI/AMFI publication period active on the observation date under the investor&apos;s knowledge cutoff.
              </p>
            </div>
          </div>

          {/* Detailed Holding Buckets Table */}
          <div className="overflow-x-auto rounded-lg border border-border bg-surface">
            <table className="w-full text-left text-[12px]">
              <thead className="border-b border-border bg-surface-elevated/40 text-[11px] font-semibold text-text-secondary">
                <tr>
                  <th className="py-2.5 px-3">Market-Cap Category / Asset Class</th>
                  <th className="py-2.5 px-3 text-right">Holdings</th>
                  <th className="py-2.5 px-3 text-right">Raw Reported Wt</th>
                  <th className="py-2.5 px-3 text-right">Normalized Wt</th>
                  <th className="py-2.5 px-3">Top Disclosed Positions</th>
                  <th className="py-2.5 px-3">Classification Provenance</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/60">
                {selectedObs.bucketExposures.map((b) => (
                  <tr key={b.category} className="hover:bg-surface-elevated/20 transition-colors">
                    <td className="py-2.5 px-3 font-medium text-text-primary flex items-center gap-2">
                      <span className={`w-2.5 h-2.5 rounded-full ${getBucketBarColor(b.category)}`} />
                      <div>
                        <span>{b.categoryLabel}</span>
                        <span className="block mono-meta text-[10px] text-text-muted">{b.classificationSource}</span>
                      </div>
                    </td>
                    <td className="py-2.5 px-3 text-right mono-num font-semibold text-text-primary">
                      {b.holdingCount}
                    </td>
                    <td className="py-2.5 px-3 text-right mono-num font-bold text-text-primary">
                      {b.reportedWeightPct.toFixed(2)}%
                    </td>
                    <td className="py-2.5 px-3 text-right mono-num text-text-secondary">
                      {b.normalizedWeightPct.toFixed(2)}%
                    </td>
                    <td className="py-2.5 px-3 text-text-secondary text-[11px]">
                      {b.topHoldings.length > 0 ? b.topHoldings.join(", ") : "—"}
                    </td>
                    <td className="py-2.5 px-3">
                      <span className="badge badge-secondary text-[9.5px]">
                        {b.provenance}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Benchmark Comparison Card (Strictly Gated) */}
          {selectedObs.benchmarkComparison && (
            <div className="p-4 rounded-lg border border-border bg-surface space-y-3">
              <div className="flex flex-col md:flex-row md:items-center justify-between gap-2 border-b border-border/50 pb-3">
                <div>
                  <span className="def-label">Benchmark Market-Cap Positioning</span>
                  <h4 className="text-[13px] font-bold text-text-primary mt-0.5">
                    {selectedObs.benchmarkComparison.benchmarkName} ({selectedObs.benchmarkComparison.benchmarkCode})
                  </h4>
                </div>
                <div>
                  <span className="badge badge-warning text-[10.5px]">
                    {selectedObs.benchmarkComparison.gatingStatus}
                  </span>
                </div>
              </div>

              {/* Gating Alert Banner */}
              <div className="p-3 rounded bg-warning/10 border border-warning/30 text-[11.5px] space-y-1.5">
                <div className="font-semibold text-warning flex items-center gap-1.5">
                  <span>⚠ Strict Benchmark Completeness Gate Active</span>
                </div>
                <p className="text-text-secondary leading-relaxed">
                  {selectedObs.benchmarkComparison.gatingReason}
                </p>
                <div className="pt-1 flex flex-wrap items-center gap-4 text-[11px] text-text-muted">
                  <span>Disclosed Benchmark Weight: <strong>{selectedObs.benchmarkComparison.benchmarkWeightCoveragePct.toFixed(2)}%</strong></span>
                  <span>Weighted Index Members: <strong>{selectedObs.benchmarkComparison.weightedConstituentsCount} / {selectedObs.benchmarkComparison.benchmarkConstituentsCount}</strong></span>
                  <span>Observation Date: <strong>{selectedObs.benchmarkComparison.benchmarkDate || "—"}</strong></span>
                </div>
              </div>

              {/* Lower Bound Exposures Table */}
              <div className="pt-2">
                <span className="text-[11px] font-semibold text-text-muted uppercase tracking-wider block mb-2">
                  Disclosed Benchmark Lower-Bound Weights (Partial Top Holdings)
                </span>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                  <div className="p-2.5 rounded bg-surface-elevated/40 border border-border/50">
                    <span className="mono-meta text-[10px] text-text-muted block">BENCHMARK LARGE-CAP (LOWER BOUND)</span>
                    <span className="mono-num text-[15px] font-bold text-blue-500 mt-0.5 block">
                      {selectedObs.benchmarkComparison.benchmarkLargeCapDisclosedWeightPct.toFixed(2)}%
                    </span>
                    <span className="text-[10px] text-text-muted">From top disclosed heavyweights</span>
                  </div>
                  <div className="p-2.5 rounded bg-surface-elevated/40 border border-border/50">
                    <span className="mono-meta text-[10px] text-text-muted block">BENCHMARK MID-CAP (LOWER BOUND)</span>
                    <span className="mono-num text-[15px] font-bold text-teal-500 mt-0.5 block">
                      {selectedObs.benchmarkComparison.benchmarkMidCapDisclosedWeightPct.toFixed(2)}%
                    </span>
                    <span className="text-[10px] text-text-muted">Unweighted in partial factsheet</span>
                  </div>
                  <div className="p-2.5 rounded bg-surface-elevated/40 border border-border/50">
                    <span className="mono-meta text-[10px] text-text-muted block">BENCHMARK SMALL-CAP (LOWER BOUND)</span>
                    <span className="mono-num text-[15px] font-bold text-amber-500 mt-0.5 block">
                      {selectedObs.benchmarkComparison.benchmarkSmallCapDisclosedWeightPct.toFixed(2)}%
                    </span>
                    <span className="text-[10px] text-text-muted">Unweighted in partial factsheet</span>
                  </div>
                </div>
                <p className="mt-2 text-[10.5px] text-text-muted italic">
                  * Note: YUKIRA strictly refuses to subtract partial benchmark lower bounds from fund weights to manufacture artificial active weights. Benchmark active style spread will unlock when authentic full index weights are ingested.
                </p>
              </div>
            </div>
          )}
        </div>
      )}

      {/* SUB-TAB 2: STYLE-DRIFT DECOMPOSITION WATERFALL */}
      {selectedSubTab === "WATERFALL" && (
        <div className="space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 p-3 rounded bg-surface-elevated/20 border border-border/60">
            <div>
              <span className="font-semibold text-text-primary text-[12.5px]">Historical Transition Decompositions</span>
              <p className="text-[11.5px] text-text-secondary mt-0.5">
                Decomposes observed exposure changes into portfolio weight adjustments, AMFI ranking reclassifications, and disclosure scope changes.
              </p>
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={expandAllTransitions}
                className="btn btn-secondary btn-sm text-[11px]"
              >
                Expand All
              </button>
              <button
                type="button"
                onClick={collapseAllTransitions}
                className="btn btn-secondary btn-sm text-[11px]"
              >
                Collapse All
              </button>
            </div>
          </div>

          {data.transitions.length === 0 ? (
            <div className="p-6 text-center text-text-muted border border-dashed border-border rounded-lg">
              <span>At least 2 chronological portfolio disclosures are required to compute style drift transitions.</span>
            </div>
          ) : (
            <div className="space-y-3">
              {data.transitions.map((t) => {
                const key = `${t.earlierDate}::${t.laterDate}::${t.category}`;
                const isExpanded = expandedTransitions.has(key);

                return (
                  <div
                    key={key}
                    className="rounded-lg border border-border bg-surface transition-colors overflow-hidden"
                  >
                    <div
                      onClick={() => toggleTransition(key)}
                      className="p-3.5 flex flex-col md:flex-row md:items-center justify-between gap-3 cursor-pointer hover:bg-surface-elevated/30"
                    >
                      <div className="flex items-center gap-3">
                        <span className={`w-3 h-3 rounded-full shrink-0 ${getBucketBarColor(t.category)}`} />
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="font-bold text-text-primary text-[13px]">
                              {t.category.replace("_", " ")}
                            </span>
                            <span className="mono-meta text-[11px] text-text-muted">
                              {t.earlierDate} &rarr; {t.laterDate}
                            </span>
                            <span
                              className={`badge text-[9.5px] ${
                                t.primaryDriver === "MARKET_CAP_RECLASSIFICATION"
                                  ? "badge-warning"
                                  : t.primaryDriver === "DISCLOSURE_SCOPE_SHIFT"
                                  ? "badge-danger"
                                  : "badge-secondary"
                              }`}
                            >
                              {t.primaryDriver.replace(/_/g, " ")}
                            </span>
                          </div>
                          <span className="text-[11.5px] text-text-secondary mt-0.5 block">
                            {t.earlierWeightPct.toFixed(2)}% &rarr; {t.laterWeightPct.toFixed(2)}% ({t.earlierHoldingCount} &rarr; {t.laterHoldingCount} holdings)
                          </span>
                        </div>
                      </div>

                      <div className="flex items-center gap-4">
                        <div className="text-right">
                          <span className="mono-meta text-[10px] text-text-muted block">OBSERVED DRIFT</span>
                          <span
                            className={`mono-num text-[14px] font-bold ${
                              t.deltaWeightPct > 0 ? "text-success" : t.deltaWeightPct < 0 ? "text-danger" : "text-text-muted"
                            }`}
                          >
                            {formatPct(t.deltaWeightPct)}
                          </span>
                        </div>
                        <span className="text-text-muted text-[13px]">
                          {isExpanded ? "▲" : "▼"}
                        </span>
                      </div>
                    </div>

                    {/* Expandable Waterfall Detail */}
                    {isExpanded && (
                      <div className="p-4 border-t border-border bg-surface-elevated/10 space-y-4 text-[12px]">
                        {/* Explanation */}
                        <div className="p-3 rounded bg-surface border border-border/60">
                          <span className="font-semibold text-text-primary block text-[11.5px] mb-1">
                            Evidence Explanation:
                          </span>
                          <p className="text-text-secondary leading-relaxed text-[11.5px]">
                            {t.explanation}
                          </p>
                        </div>

                        {/* Mathematical Decomposition Breakdown */}
                        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                          <div className="p-3 rounded bg-surface border border-border/60">
                            <span className="mono-meta text-[10px] text-text-muted block">PORTFOLIO WEIGHT COMPONENT</span>
                            <span className="mono-num text-[14px] font-bold text-text-primary mt-0.5 block">
                              {formatPct(t.weightChangeComponent)}
                            </span>
                            <span className="text-[10px] text-text-muted mt-1 block">
                              Rebalancing &amp; market price changes
                            </span>
                          </div>

                          <div className="p-3 rounded bg-surface border border-border/60">
                            <span className="mono-meta text-[10px] text-text-muted block">RECLASSIFICATION COMPONENT</span>
                            <span
                              className={`mono-num text-[14px] font-bold mt-0.5 block ${
                                t.reclassificationComponent !== 0 ? "text-warning" : "text-text-muted"
                              }`}
                            >
                              {formatPct(t.reclassificationComponent)}
                            </span>
                            <span className="text-[10px] text-text-muted mt-1 block">
                              AMFI ranking period shifts
                            </span>
                          </div>

                          <div className="p-3 rounded bg-surface border border-border/60">
                            <span className="mono-meta text-[10px] text-text-muted block">DISCLOSURE SCOPE COMPONENT</span>
                            <span
                              className={`mono-num text-[14px] font-bold mt-0.5 block ${
                                t.disclosureScopeComponent !== 0 ? "text-danger" : "text-text-muted"
                              }`}
                            >
                              {formatPct(t.disclosureScopeComponent)}
                            </span>
                            <span className="text-[10px] text-text-muted mt-1 block">
                              Coverage shifts or unobserved tail
                            </span>
                          </div>

                          <div className="p-3 rounded bg-surface border border-border/60">
                            <span className="mono-meta text-[10px] text-text-muted block">RECONCILIATION AUDIT</span>
                            <span className="mono-num text-[13px] font-semibold text-success mt-0.5 block">
                              {t.isReconciled ? "✓ Verified Reconciled" : "⚠ Discrepancy"}
                            </span>
                            <span className="text-[10px] text-text-muted mt-1 block">
                              Tolerance: &le; &plusmn;{t.reconciliationTolerance.toFixed(2)} pp
                            </span>
                          </div>
                        </div>

                        {/* Comparability Warning if Applicable */}
                        {!t.isComparable && (
                          <div className="p-3 rounded bg-danger/10 border border-danger/30 text-[11.5px] text-danger">
                            <strong>Non-Comparable Disclosure Transition:</strong> {t.nonComparableReason}
                          </div>
                        )}

                        {/* Reclassified Holdings in this Transition */}
                        {t.reclassifiedHoldings && t.reclassifiedHoldings.length > 0 && (
                          <div className="pt-2">
                            <span className="font-semibold text-text-primary text-[11.5px] block mb-1.5">
                              Holdings Reclassified by AMFI in this Transition:
                            </span>
                            <div className="space-y-1.5">
                              {t.reclassifiedHoldings.map((r, rIdx) => (
                                <div
                                  key={rIdx}
                                  className="p-2 rounded bg-surface border border-border/50 flex flex-col sm:flex-row sm:items-center justify-between text-[11px] gap-2"
                                >
                                  <div>
                                    <span className="font-bold text-text-primary">{r.securityName}</span>
                                    <span className="mono-meta text-text-muted ml-2">({r.isin})</span>
                                    <span className="block text-text-secondary mt-0.5">{r.explanation}</span>
                                  </div>
                                  <div className="mono-num font-semibold text-text-primary text-right shrink-0">
                                    {r.earlierWeightPct.toFixed(2)}% &rarr; {r.laterWeightPct.toFixed(2)}% ({formatPct(r.weightDeltaPct)})
                                  </div>
                                </div>
                              ))}
                            </div>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* SUB-TAB 3: CHRONOLOGICAL TRAJECTORY */}
      {selectedSubTab === "TIMELINE" && (
        <div className="space-y-4">
          <div className="overflow-x-auto rounded-lg border border-border bg-surface">
            <table className="w-full text-left text-[12px]">
              <thead className="border-b border-border bg-surface-elevated/40 text-[11px] font-semibold text-text-secondary">
                <tr>
                  <th className="py-2.5 px-3">Portfolio Date</th>
                  <th className="py-2.5 px-3">AMFI Ranking Period</th>
                  <th className="py-2.5 px-3 text-right">Large-Cap</th>
                  <th className="py-2.5 px-3 text-right">Mid-Cap</th>
                  <th className="py-2.5 px-3 text-right">Small-Cap</th>
                  <th className="py-2.5 px-3 text-right">Cash &amp; Liquid</th>
                  <th className="py-2.5 px-3 text-right">Debt / Other</th>
                  <th className="py-2.5 px-3 text-right">Sum Disclosed</th>
                  <th className="py-2.5 px-3 text-right">Holdings</th>
                  <th className="py-2.5 px-3">Scope</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/60">
                {data.observations.map((obs) => {
                  const getW = (cat: string) =>
                    obs.bucketExposures.find((b) => b.category === cat)?.reportedWeightPct ?? 0;

                  return (
                    <tr
                      key={obs.snapshotId}
                      className="hover:bg-surface-elevated/20 transition-colors"
                    >
                      <td className="py-2.5 px-3 font-semibold text-text-primary">
                        {obs.portfolioDate}
                      </td>
                      <td className="py-2.5 px-3 mono-meta text-accent">
                        {obs.sourceRankingPeriod || "Approximate Reference"}
                      </td>
                      <td className="py-2.5 px-3 text-right mono-num font-bold text-blue-500">
                        {getW("LARGE_CAP").toFixed(2)}%
                      </td>
                      <td className="py-2.5 px-3 text-right mono-num font-bold text-teal-500">
                        {getW("MID_CAP").toFixed(2)}%
                      </td>
                      <td className="py-2.5 px-3 text-right mono-num font-bold text-amber-500">
                        {getW("SMALL_CAP").toFixed(2)}%
                      </td>
                      <td className="py-2.5 px-3 text-right mono-num text-emerald-500">
                        {getW("CASH").toFixed(2)}%
                      </td>
                      <td className="py-2.5 px-3 text-right mono-num text-purple-500">
                        {getW("DEBT_OTHER").toFixed(2)}%
                      </td>
                      <td className="py-2.5 px-3 text-right mono-num font-semibold text-text-primary">
                        {obs.sumReportedWeightsPct.toFixed(2)}%
                      </td>
                      <td className="py-2.5 px-3 text-right mono-num text-text-muted">
                        {obs.totalHoldingsCount}
                      </td>
                      <td className="py-2.5 px-3">
                        <span className="badge badge-secondary text-[9.5px]">
                          {obs.portfolioDisclosureScope.replace("_", " ")}
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* SUB-TAB 4: AMFI RECLASSIFICATIONS SPOTLIGHT */}
      {selectedSubTab === "RECLASSIFICATIONS" && (
        <div className="space-y-4">
          <div className="p-3.5 rounded-lg bg-surface-elevated/20 border border-border/60 text-[12px]">
            <span className="font-semibold text-text-primary block text-[12.5px]">
              Securities Reclassified Across AMFI Ranking Cycles
            </span>
            <p className="text-text-secondary mt-1 text-[11.5px] leading-relaxed">
              Under SEBI circular guidelines, AMFI publishes updated market-cap rankings semi-annually (ended June 30 and December 31).
              When a company shifts rank (e.g. 101st&ndash;250th to top 100), its classification in mutual fund portfolios changes automatically without the fund manager buying or selling a single share.
            </p>
          </div>

          {data.allReclassifiedHoldings.length === 0 ? (
            <div className="p-6 text-center text-text-muted border border-dashed border-border rounded-lg">
              <span>No portfolio holdings shifted market-cap categories across the observed ranking periods.</span>
            </div>
          ) : (
            <div className="overflow-x-auto rounded-lg border border-border bg-surface">
              <table className="w-full text-left text-[12px]">
                <thead className="border-b border-border bg-surface-elevated/40 text-[11px] font-semibold text-text-secondary">
                  <tr>
                    <th className="py-2.5 px-3">Security Name</th>
                    <th className="py-2.5 px-3">ISIN</th>
                    <th className="py-2.5 px-3">Earlier Category (Rank)</th>
                    <th className="py-2.5 px-3">New Category (Rank)</th>
                    <th className="py-2.5 px-3 text-right">Holding Weight Delta</th>
                    <th className="py-2.5 px-3">Ranking Update</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/60">
                  {data.allReclassifiedHoldings.map((r, idx) => (
                    <tr key={idx} className="hover:bg-surface-elevated/20 transition-colors">
                      <td className="py-2.5 px-3 font-bold text-text-primary">
                        {r.securityName}
                      </td>
                      <td className="py-2.5 px-3 mono-meta text-text-muted">
                        {r.isin}
                      </td>
                      <td className="py-2.5 px-3">
                        <span className="badge badge-secondary text-[10px]">
                          {r.earlierCategory} {r.earlierRank ? `(#${r.earlierRank})` : ""}
                        </span>
                      </td>
                      <td className="py-2.5 px-3">
                        <span className="badge badge-warning text-[10px]">
                          {r.laterCategory} {r.laterRank ? `(#${r.laterRank})` : ""}
                        </span>
                      </td>
                      <td className="py-2.5 px-3 text-right mono-num font-semibold text-text-primary">
                        {r.earlierWeightPct.toFixed(2)}% &rarr; {r.laterWeightPct.toFixed(2)}% ({formatPct(r.weightDeltaPct)})
                      </td>
                      <td className="py-2.5 px-3 mono-meta text-accent text-[11px]">
                        {r.rankingPeriodChange}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* 3. Decision Support & Investigation Checkpoint */}
      <div className="p-4 rounded-lg bg-surface border border-border space-y-3">
        <div className="flex items-center justify-between">
          <span className="def-label">Allocator Due Diligence Checkpoint</span>
          <span className="mono-meta text-[10px] text-text-muted">Descriptive Portfolio Construction</span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          {data.investigationQuestions.map((q, idx) => (
            <div key={idx} className="p-3 rounded bg-surface-elevated/30 border border-border/50 flex flex-col justify-between">
              <p className="text-[12px] text-text-secondary leading-relaxed">{q}</p>
              {onInvestigateQuestion && (
                <div className="mt-3 pt-2 border-t border-border/40 flex justify-end">
                  <button
                    type="button"
                    onClick={() => onInvestigateQuestion(q, "Market-Cap Style Drift Due Diligence")}
                    className="btn btn-secondary btn-sm text-[10.5px]"
                  >
                    Investigate in Workspace
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>

        {data.dataQualityWarnings && data.dataQualityWarnings.length > 0 && (
          <div className="pt-2">
            <span className="mono-meta text-[10px] text-warning block mb-1">DATA QUALITY OBSERVATIONS:</span>
            <ul className="list-disc list-inside text-[11px] text-text-muted space-y-0.5">
              {data.dataQualityWarnings.map((w, wIdx) => (
                <li key={wIdx}>{w}</li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </div>
  );
}
