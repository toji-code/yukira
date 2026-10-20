"use client";

import React, { useState, useEffect, useMemo } from "react";
import {
  fetchSectorDrift,
  SectorDriftResponseDto,
  SectorDriftObservationDto,
  SectorDriftChangeDto,
  IndustryDriftChangeDto,
  SectorActiveComparisonDto,
  FundIndustryExposureDto,
  SecurityExposureWaterfallDto,
} from "@/lib/api/portfolio";

interface SectorDriftViewProps {
  schemeOptionId: number;
  effectiveAsOfDate?: string;
  effectiveCutoffTime?: string;
  onRefresh?: () => void;
}

type ViewSubTab = "CURRENT_BREAKDOWN" | "DRIFT_TRAJECTORY" | "SHIFT_DISENTANGLEMENT" | "EVIDENCE_AUDIT";
type DisentanglementScope = "SECTOR" | "INDUSTRY";

export function SectorDriftView({
  schemeOptionId,
  effectiveAsOfDate,
  effectiveCutoffTime,
  onRefresh,
}: SectorDriftViewProps) {
  const [data, setData] = useState<SectorDriftResponseDto | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [activeSubTab, setActiveSubTab] = useState<ViewSubTab>("CURRENT_BREAKDOWN");
  const [selectedSnapshotIndex, setSelectedSnapshotIndex] = useState<number>(0);
  const [sectorSearchQuery, setSectorSearchQuery] = useState<string>("");
  const [expandedSectors, setExpandedSectors] = useState<Set<string>>(new Set());
  const [expandedIndustries, setExpandedIndustries] = useState<Set<string>>(new Set());
  const [disentanglementScope, setDisentanglementScope] = useState<DisentanglementScope>("SECTOR");

  useEffect(() => {
    let isMounted = true;
    Promise.resolve().then(() => {
      if (!isMounted) return;
      setLoading(true);
      setError(null);
    });

    fetchSectorDrift(schemeOptionId, effectiveAsOfDate, undefined, undefined, effectiveCutoffTime)
      .then((res) => {
        if (isMounted) {
          setData(res);
          // Default to latest chronological observation
          if (res.observations && res.observations.length > 0) {
            const latestIdx = res.observations.length - 1;
            setSelectedSnapshotIndex(latestIdx);

            // Auto-expand top sector so drill-down is immediately discoverable
            const latestObs = res.observations[latestIdx];
            if (latestObs.sectors && latestObs.sectors.length > 0) {
              const topSector = latestObs.sectors[0].sectorName;
              setExpandedSectors(new Set([topSector]));
              // Also expand first industry under top sector
              const firstInd = latestObs.industries?.find((i) => i.parentSectorName === topSector);
              if (firstInd) {
                setExpandedIndustries(new Set([`${topSector}::${firstInd.industryName}`]));
              }
            }
          }
          setLoading(false);
        }
      })
      .catch((err) => {
        if (isMounted) {
          setError(err.message || "Failed to load sector drift trajectory");
          setLoading(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, [schemeOptionId, effectiveAsOfDate, effectiveCutoffTime]);

  // Selected Observation
  const selectedObs: SectorDriftObservationDto | null = useMemo(() => {
    if (!data?.observations || data.observations.length === 0) return null;
    const idx = Math.max(0, Math.min(selectedSnapshotIndex, data.observations.length - 1));
    return data.observations[idx];
  }, [data, selectedSnapshotIndex]);

  // Filtered Sector Comparisons for selected observation
  const filteredComparisons = useMemo(() => {
    if (!selectedObs?.sectorComparisons) return [];
    if (!sectorSearchQuery.trim()) return selectedObs.sectorComparisons;
    const q = sectorSearchQuery.toLowerCase().trim();
    return selectedObs.sectorComparisons.filter((c: SectorActiveComparisonDto) => {
      const matchSector = c.sectorName.toLowerCase().includes(q);
      const matchIndustry = selectedObs.industries?.some(
        (ind) => ind.parentSectorName === c.sectorName && ind.industryName.toLowerCase().includes(q)
      );
      const matchSecurity = selectedObs.securityWaterfall?.some(
        (sec) => sec.parentSectorName === c.sectorName && (
          sec.companyName.toLowerCase().includes(q) ||
          (sec.isin && sec.isin.toLowerCase().includes(q)) ||
          (sec.symbol && sec.symbol.toLowerCase().includes(q))
        )
      );
      return matchSector || matchIndustry || matchSecurity;
    });
  }, [selectedObs, sectorSearchQuery]);

  // Unique sectors across all historical snapshots for trajectory table
  const allUniqueSectors = useMemo(() => {
    if (!data?.observations) return [];
    const set = new Set<string>();
    for (const obs of data.observations) {
      for (const s of obs.sectors) {
        set.add(s.sectorName);
      }
    }
    return Array.from(set).sort((a, b) => {
      // Sort by latest observation weight descending
      const latest = data.observations[data.observations.length - 1];
      const wA = latest?.sectors.find((s) => s.sectorName === a)?.fundWeightPct ?? 0;
      const wB = latest?.sectors.find((s) => s.sectorName === b)?.fundWeightPct ?? 0;
      return wB - wA;
    });
  }, [data]);

  // Toggles
  const toggleSector = (sectorName: string) => {
    setExpandedSectors((prev) => {
      const next = new Set(prev);
      if (next.has(sectorName)) {
        next.delete(sectorName);
      } else {
        next.add(sectorName);
      }
      return next;
    });
  };

  const toggleIndustry = (sectorName: string, industryName: string) => {
    const key = `${sectorName}::${industryName}`;
    setExpandedIndustries((prev) => {
      const next = new Set(prev);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  };

  const expandAllSectors = () => {
    if (!selectedObs) return;
    const allSecs = new Set(selectedObs.sectors.map((s) => s.sectorName));
    setExpandedSectors(allSecs);
    if (selectedObs.industries) {
      const allInds = new Set(selectedObs.industries.map((i) => `${i.parentSectorName}::${i.industryName}`));
      setExpandedIndustries(allInds);
    }
  };

  const collapseAllSectors = () => {
    setExpandedSectors(new Set());
    setExpandedIndustries(new Set());
  };

  if (loading) {
    return (
      <div className="p-8 rounded-lg border border-border bg-surface/50 space-y-4 animate-pulse">
        <div className="h-6 bg-surface-elevated rounded w-1/3"></div>
        <div className="h-4 bg-surface-elevated rounded w-2/3"></div>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 pt-4">
          <div className="h-20 bg-surface-elevated rounded"></div>
          <div className="h-20 bg-surface-elevated rounded"></div>
          <div className="h-20 bg-surface-elevated rounded"></div>
          <div className="h-20 bg-surface-elevated rounded"></div>
        </div>
        <div className="h-64 bg-surface-elevated rounded mt-4"></div>
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="p-6 rounded-lg border border-danger/40 bg-danger/10 text-text-primary space-y-3">
        <div className="flex items-center gap-2">
          <span className="badge badge-danger text-xs font-semibold">API ERROR</span>
          <h4 className="text-sm font-semibold">Unable to Load Point-in-Time Sector Drift</h4>
        </div>
        <p className="text-xs text-text-secondary">
          {error || "An unexpected error occurred while communicating with the quantitative calculation orchestrator."}
        </p>
        <button
          onClick={() => {
            setLoading(true);
            setError(null);
            fetchSectorDrift(schemeOptionId, effectiveAsOfDate, undefined, undefined, effectiveCutoffTime)
              .then((res) => {
                setData(res);
                setLoading(false);
              })
              .catch((err) => {
                setError(err.message);
                setLoading(false);
              });
          }}
          className="px-3 py-1.5 rounded text-xs font-medium bg-surface-elevated border border-border hover:bg-surface transition-colors"
        >
          Retry Calculation
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* 1. Header & Governance Ribbon */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 p-5 rounded-lg border border-border bg-surface-elevated/40">
        <div>
          <div className="flex items-center gap-2.5 flex-wrap">
            <h3 className="text-base font-semibold text-text-primary">
              Point-in-Time Sector &amp; Industry Exposure Drift
            </h3>
            <span
              className={`badge text-[11px] font-semibold tracking-wide ${
                data.overallStatus === "VERIFIED_COMPLETE"
                  ? "badge-success"
                  : "badge-warning"
              }`}
            >
              {data.overallStatus === "VERIFIED_COMPLETE"
                ? "VERIFIED COMPLETE"
                : "PARTIAL SECTOR VIEW (BENCHMARK GATED)"}
            </span>
            <span className="mono-meta text-[11px] px-2 py-0.5 rounded bg-surface border border-border/80 text-text-muted">
              {data.methodologyVersion}
            </span>
          </div>
          <p className="text-xs text-text-secondary mt-1 max-w-3xl leading-relaxed">
            Deterministic aggregation of fund sector and sub-industry weights, point-in-time benchmark constituent classifications, and security-level active exposure waterfall.
            Definitive benchmark active spreads are gated when benchmark constituent weights are partially disclosed.
          </p>
        </div>

        <div className="flex items-center gap-2 shrink-0">
          <button
            onClick={() => {
              if (onRefresh) onRefresh();
              setLoading(true);
              fetchSectorDrift(schemeOptionId, effectiveAsOfDate, undefined, undefined, effectiveCutoffTime)
                .then(setData)
                .catch((e) => setError(e.message))
                .finally(() => setLoading(false));
            }}
            className="px-3 py-1.5 rounded text-xs font-medium bg-surface border border-border hover:bg-surface-elevated transition-colors text-text-secondary hover:text-text-primary"
          >
            Refresh
          </button>
        </div>
      </div>

      {/* 2. Honest Gating & Methodology Transparency Banner */}
      {selectedObs?.observationStatus === "GATED_INCOMPLETE_BENCHMARK_WEIGHTS" && (
        <div className="p-4 rounded-lg border border-warning/50 bg-warning/10 space-y-2">
          <div className="flex items-center gap-2">
            <span className="text-warning-fg text-sm">⚠️</span>
            <span className="text-xs font-semibold text-warning-fg uppercase tracking-wider">
              Constitutional Governance Gate: Definitive Sector &amp; Industry Active Weights Gated
            </span>
          </div>
          <p className="text-xs text-text-secondary leading-relaxed">
            {selectedObs.statusReason}
          </p>
          <div className="flex flex-wrap items-center gap-4 pt-1 text-[11px] text-text-muted mono-meta">
            <span>Benchmark Disclosed Weight: <strong className="text-text-primary font-mono">{selectedObs.benchmarkWeightCoveragePct.toFixed(2)}%</strong> (Top {selectedObs.benchmarkWeightedCount} heavyweights)</span>
            <span>Undisclosed Tail: <strong className="text-text-primary font-mono">{(100 - selectedObs.benchmarkWeightCoveragePct).toFixed(2)}%</strong> ({selectedObs.benchmarkConstituentsCount - selectedObs.benchmarkWeightedCount} tail members)</span>
            <span>Zero Fake Data: Unweighted benchmark tail is <strong>never converted to 0.00%</strong></span>
          </div>
        </div>
      )}

      {selectedObs?.portfolioDisclosureScope === "PARTIAL_TOP_HOLDINGS" && (
        <div className="p-4 rounded-lg border border-amber-500/40 bg-amber-500/10 space-y-2">
          <div className="flex items-center gap-2">
            <span className="text-amber-400 text-sm">ℹ️</span>
            <span className="text-xs font-semibold text-amber-300 uppercase tracking-wider">
              Incomplete Portfolio Disclosure ({selectedObs.portfolioDate})
            </span>
          </div>
          <p className="text-xs text-text-secondary leading-relaxed">
            {selectedObs.statusReason}
          </p>
        </div>
      )}

      {/* 3. Snapshot Selector Strip */}
      <div className="space-y-2">
        <div className="flex items-center justify-between text-xs">
          <span className="font-semibold text-text-primary uppercase tracking-wider text-[11px]">
            Historical Snapshot Selection ({data.observations.length} Available Observations)
          </span>
          <span className="text-[11px] text-text-muted mono-meta">
            Knowledge Cutoff: {data.knowledgeCutoff}
          </span>
        </div>

        <div className="flex items-center gap-2 overflow-x-auto pb-1 scrollbar-thin">
          {data.observations.map((obs, idx) => {
            const isSelected = idx === selectedSnapshotIndex;
            return (
              <button
                key={obs.portfolioSnapshotId || obs.portfolioDate}
                onClick={() => setSelectedSnapshotIndex(idx)}
                className={`px-3.5 py-2 rounded-lg text-xs font-medium border whitespace-nowrap transition-all flex flex-col items-start gap-0.5 ${
                  isSelected
                    ? "bg-primary/15 border-primary text-primary shadow-sm"
                    : "bg-surface border-border/80 text-text-secondary hover:text-text-primary hover:border-border"
                }`}
              >
                <div className="flex items-center gap-1.5 font-mono font-semibold">
                  <span>{obs.portfolioDate}</span>
                  {obs.portfolioDisclosureScope === "PARTIAL_TOP_HOLDINGS" && (
                    <span className="text-[9px] px-1 py-0.2 rounded bg-amber-500/20 text-amber-300 border border-amber-500/30">
                      Top 10
                    </span>
                  )}
                </div>
                <div className="text-[10px] text-text-muted">
                  {obs.fundHoldingsCount} holdings | {obs.sumFundWeightsPct.toFixed(1)}% assets
                </div>
              </button>
            );
          })}
        </div>
      </div>

      {/* 4. Selected Observation Metadata Summary */}
      {selectedObs && (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
          <div className="p-3 rounded-lg border border-border/70 bg-surface-elevated/30">
            <span className="mono-meta block text-[10px] text-text-muted">PORTFOLIO DATE</span>
            <span className="mono-num text-sm font-bold text-text-primary mt-0.5 block">
              {selectedObs.portfolioDate}
            </span>
            <span className="text-[10px] text-text-muted block mt-0.5">
              Scope: {selectedObs.portfolioDisclosureScope === "FULL_PORTFOLIO_DISCLOSURE" ? "Full Portfolio" : "Top 10 Only"}
            </span>
          </div>

          <div className="p-3 rounded-lg border border-border/70 bg-surface-elevated/30">
            <span className="mono-meta block text-[10px] text-text-muted">BENCHMARK DATE</span>
            <span className="mono-num text-sm font-bold text-accent mt-0.5 block">
              {selectedObs.benchmarkDate || "Unavailable"}
            </span>
            <span className="text-[10px] text-text-muted block mt-0.5">
              Diff: {selectedObs.snapshotDateDiffDays >= 0 ? `${selectedObs.snapshotDateDiffDays} days` : "N/A"}
              {selectedObs.isTemporallyComparable ? " (Aligned)" : " (Mismatch)"}
            </span>
          </div>

          <div className="p-3 rounded-lg border border-border/70 bg-surface-elevated/30">
            <span className="mono-meta block text-[10px] text-text-muted">FUND CLASSIFICATION</span>
            <span className="mono-num text-sm font-bold text-text-primary mt-0.5 block">
              {selectedObs.sectors.length} Sectors / {selectedObs.industries?.length ?? selectedObs.sectors.length} Industries
            </span>
            <span className="text-[10px] text-text-muted block mt-0.5">
              {selectedObs.fundHoldingsCount} Holdings ({selectedObs.fundClassificationCoveragePct.toFixed(1)}% Verified)
            </span>
          </div>

          <div className="p-3 rounded-lg border border-border/70 bg-surface-elevated/30">
            <span className="mono-meta block text-[10px] text-text-muted">BENCHMARK WEIGHT DISCLOSED</span>
            <span className="mono-num text-sm font-bold text-warning-fg mt-0.5 block">
              {selectedObs.benchmarkWeightCoveragePct.toFixed(2)}%
            </span>
            <span className="text-[10px] text-text-muted block mt-0.5">
              {selectedObs.benchmarkWeightedCount} / {selectedObs.benchmarkConstituentsCount} Constituents
            </span>
          </div>

          <div className="p-3 rounded-lg border border-border/70 bg-surface-elevated/30">
            <span className="mono-meta block text-[10px] text-text-muted">WATERFALL COVERAGE</span>
            <span className="mono-num text-sm font-bold text-success-fg mt-0.5 block">
              {selectedObs.securityWaterfall?.length ?? 0} Securities
            </span>
            <span className="text-[10px] text-text-muted block mt-0.5">
              Tri-Category Decomposed
            </span>
          </div>

          <div className="p-3 rounded-lg border border-border/70 bg-surface-elevated/30">
            <span className="mono-meta block text-[10px] text-text-muted">ACTIVE SPREAD STATUS</span>
            <span className={`text-[12px] font-bold mt-0.5 block ${
              selectedObs.observationStatus === "VERIFIED_COMPLETE" ? "text-success-fg" : "text-amber-400"
            }`}>
              {selectedObs.observationStatus === "VERIFIED_COMPLETE" ? "Complete Spread" : "Gated (Lower Bound)"}
            </span>
            <span className="text-[10px] text-text-muted block mt-0.5">
              {selectedObs.observationStatus === "VERIFIED_COMPLETE" ? "Full active weight" : "Tail undisclosed"}
            </span>
          </div>
        </div>
      )}

      {/* 5. Navigation Subtabs */}
      <div className="flex border-b border-border text-xs">
        <button
          onClick={() => setActiveSubTab("CURRENT_BREAKDOWN")}
          className={`px-4 py-2.5 font-medium transition-colors border-b-2 -mb-[1px] ${
            activeSubTab === "CURRENT_BREAKDOWN"
              ? "border-primary text-primary"
              : "border-transparent text-text-secondary hover:text-text-primary"
          }`}
        >
          Sector &amp; Industry Drill-Down &amp; Waterfall
        </button>
        <button
          onClick={() => setActiveSubTab("DRIFT_TRAJECTORY")}
          className={`px-4 py-2.5 font-medium transition-colors border-b-2 -mb-[1px] ${
            activeSubTab === "DRIFT_TRAJECTORY"
              ? "border-primary text-primary"
              : "border-transparent text-text-secondary hover:text-text-primary"
          }`}
        >
          Historical Sector Drift Trajectory ({data.observations.length} Snapshots)
        </button>
        <button
          onClick={() => setActiveSubTab("SHIFT_DISENTANGLEMENT")}
          className={`px-4 py-2.5 font-medium transition-colors border-b-2 -mb-[1px] ${
            activeSubTab === "SHIFT_DISENTANGLEMENT"
              ? "border-primary text-primary"
              : "border-transparent text-text-secondary hover:text-text-primary"
          }`}
        >
          Observed Shift Disentanglement ({data.sectorDriftChanges.length} Sectors / {data.industryDriftChanges?.length ?? 0} Industries)
        </button>
        <button
          onClick={() => setActiveSubTab("EVIDENCE_AUDIT")}
          className={`px-4 py-2.5 font-medium transition-colors border-b-2 -mb-[1px] ${
            activeSubTab === "EVIDENCE_AUDIT"
              ? "border-primary text-primary"
              : "border-transparent text-text-secondary hover:text-text-primary"
          }`}
        >
          Evidence &amp; Benchmark Audit
        </button>
      </div>

      {/* 6. TAB 1: CURRENT BREAKDOWN & BENCHMARK COMPARISON & WATERFALL */}
      {activeSubTab === "CURRENT_BREAKDOWN" && selectedObs && (
        <div className="space-y-6">
          {/* Visual Sector Bar */}
          <div className="p-4 rounded-lg border border-border bg-surface-elevated/20 space-y-3">
            <div className="flex items-center justify-between text-xs">
              <span className="font-semibold text-text-primary">
                Fund Sector Allocation Breakdown ({selectedObs.portfolioDate})
              </span>
              <span className="mono-meta text-[11px] text-text-muted">
                Sum Reported Weights: {selectedObs.sumFundWeightsPct.toFixed(2)}%
              </span>
            </div>

            {/* Proportional Segmented Bar */}
            <div className="flex h-5 w-full overflow-hidden rounded bg-surface border border-border/40">
              {selectedObs.sectors.slice(0, 8).map((sec, i) => {
                const colors = [
                  "bg-blue-600",
                  "bg-emerald-600",
                  "bg-amber-600",
                  "bg-purple-600",
                  "bg-cyan-600",
                  "bg-rose-600",
                  "bg-indigo-600",
                  "bg-teal-600",
                ];
                const widthPct = Math.max(1, (sec.fundWeightPct / selectedObs.sumFundWeightsPct) * 100);
                return (
                  <div
                    key={sec.sectorName}
                    style={{ width: `${widthPct}%` }}
                    className={`${colors[i % colors.length]} transition-all`}
                    title={`${sec.sectorName}: ${sec.fundWeightPct.toFixed(2)}% (${sec.holdingCount} holdings)`}
                  />
                );
              })}
            </div>

            {/* Bar Legend */}
            <div className="flex flex-wrap items-center gap-x-4 gap-y-1.5 pt-1 text-[11px]">
              {selectedObs.sectors.slice(0, 8).map((sec, i) => {
                const dotColors = [
                  "bg-blue-500",
                  "bg-emerald-500",
                  "bg-amber-500",
                  "bg-purple-500",
                  "bg-cyan-500",
                  "bg-rose-500",
                  "bg-indigo-500",
                  "bg-teal-500",
                ];
                return (
                  <div key={sec.sectorName} className="flex items-center gap-1.5">
                    <span className={`w-2.5 h-2.5 rounded-full ${dotColors[i % dotColors.length]}`} />
                    <span className="text-text-secondary">{sec.sectorName}:</span>
                    <span className="font-mono font-medium text-text-primary">{sec.fundWeightPct.toFixed(2)}%</span>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Controls: Search & Expand/Collapse */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div className="relative w-full max-w-sm">
              <input
                type="text"
                placeholder="Search sector, sub-industry, stock or ISIN..."
                value={sectorSearchQuery}
                onChange={(e) => setSectorSearchQuery(e.target.value)}
                className="w-full px-3 py-1.5 text-xs rounded-md bg-surface border border-border focus:outline-none focus:border-primary text-text-primary placeholder:text-text-muted"
              />
              {sectorSearchQuery && (
                <button
                  onClick={() => setSectorSearchQuery("")}
                  className="absolute right-2 top-1.5 text-xs text-text-muted hover:text-text-primary"
                >
                  ✕
                </button>
              )}
            </div>

            <div className="flex items-center gap-2">
              <button
                onClick={expandAllSectors}
                className="px-2.5 py-1 text-[11px] font-medium rounded bg-surface border border-border hover:bg-surface-elevated text-text-secondary hover:text-text-primary transition-colors"
              >
                Expand All
              </button>
              <button
                onClick={collapseAllSectors}
                className="px-2.5 py-1 text-[11px] font-medium rounded bg-surface border border-border hover:bg-surface-elevated text-text-secondary hover:text-text-primary transition-colors"
              >
                Collapse All
              </button>
              <span className="text-xs text-text-muted ml-2">
                Showing {filteredComparisons.length} of {selectedObs.sectorComparisons.length} sectors
              </span>
            </div>
          </div>

          {/* Expandable Hierarchical Breakdown Table */}
          <div className="overflow-x-auto rounded-lg border border-border bg-surface">
            <table className="w-full text-left text-xs">
              <thead className="bg-surface-elevated/80 border-b border-border text-[11px] text-text-muted font-mono uppercase tracking-wider">
                <tr>
                  <th className="py-2.5 px-3">Classification / Entity</th>
                  <th className="py-2.5 px-3 text-right">Fund Weight</th>
                  <th className="py-2.5 px-3 text-center">Fund Holdings</th>
                  <th className="py-2.5 px-3 text-right">Benchmark Disclosed</th>
                  <th className="py-2.5 px-3 text-center">Benchmark Constituents</th>
                  <th className="py-2.5 px-3 text-right">Active Spread</th>
                  <th className="py-2.5 px-3">Classification Provenance &amp; Disclosures</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/60">
                {filteredComparisons.map((c) => {
                  const isSectorExpanded = expandedSectors.has(c.sectorName);
                  const fundSecDto = selectedObs.sectors.find((s) => s.sectorName === c.sectorName);

                  // Sub-industries under this macro sector
                  const subIndustries = selectedObs.industries?.filter(
                    (i) => i.parentSectorName === c.sectorName
                  ) ?? [];

                  return (
                    <React.Fragment key={c.sectorName}>
                      {/* Macro Sector Level Row */}
                      <tr className="hover:bg-surface-elevated/30 transition-colors bg-surface font-medium">
                        <td className="py-3 px-3 text-text-primary">
                          <div className="flex items-center gap-2">
                            <button
                              onClick={() => toggleSector(c.sectorName)}
                              className="w-5 h-5 flex items-center justify-center rounded text-text-muted hover:text-text-primary hover:bg-surface-elevated transition-colors text-xs font-mono"
                              aria-label={isSectorExpanded ? "Collapse sector" : "Expand sector"}
                            >
                              {isSectorExpanded ? "▼" : "▶"}
                            </button>
                            <span className="font-semibold text-text-primary">{c.sectorName}</span>
                            {subIndustries.length > 0 && (
                              <span className="text-[10px] px-1.5 py-0.5 rounded bg-surface-elevated border border-border/80 text-text-muted font-mono">
                                {subIndustries.length} {subIndustries.length === 1 ? "industry" : "industries"}
                              </span>
                            )}
                          </div>
                        </td>

                        <td className="py-3 px-3 text-right font-mono font-semibold text-text-primary">
                          {c.fundWeightPct.toFixed(2)}%
                        </td>

                        <td className="py-3 px-3 text-center text-text-secondary font-mono">
                          {c.fundHoldingCount > 0 ? c.fundHoldingCount : "—"}
                        </td>

                        <td className="py-3 px-3 text-right font-mono">
                          {c.benchmarkDisclosedWeightPct !== null ? (
                            <span className="text-accent font-medium">
                              {c.benchmarkDisclosedWeightPct.toFixed(2)}%
                              <span className="text-[10px] text-text-muted block font-sans">
                                (Lower bound)
                              </span>
                            </span>
                          ) : (
                            <span className="text-text-muted">Undisclosed</span>
                          )}
                        </td>

                        <td className="py-3 px-3 text-center text-text-muted font-mono text-[11px]">
                          {c.benchmarkConstituentCount > 0 ? (
                            <span>
                              {c.benchmarkWeightedConstituentCount} weighted / {c.benchmarkConstituentCount} total
                            </span>
                          ) : (
                            "0 in BM"
                          )}
                        </td>

                        <td className="py-3 px-3 text-right">
                          {c.definitiveActiveWeightPct !== null ? (
                            <span
                              className={`font-mono font-bold ${
                                c.definitiveActiveWeightPct >= 0 ? "text-success-fg" : "text-danger"
                              }`}
                            >
                              {c.definitiveActiveWeightPct >= 0 ? "+" : ""}
                              {c.definitiveActiveWeightPct.toFixed(2)}%
                            </span>
                          ) : (
                            <span
                              className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-semibold bg-warning/15 text-warning-fg border border-warning/30"
                              title={c.statusReason}
                            >
                              Gated
                            </span>
                          )}
                        </td>

                        <td className="py-3 px-3 text-[11px] text-text-secondary max-w-xs">
                          <div className="flex items-center gap-1.5 mb-0.5">
                            {fundSecDto?.classificationProvenance === "BENCHMARK_PIT_CLASSIFICATION" && (
                              <span className="badge text-[9px] badge-success">PIT BENCHMARK</span>
                            )}
                            {fundSecDto?.classificationProvenance === "FUND_DISCLOSURE_MAPPED" && (
                              <span className="badge text-[9px] badge-accent">FUND MAPPED</span>
                            )}
                            {fundSecDto?.classificationProvenance === "ASSET_CLASS_CASH" && (
                              <span className="badge text-[9px] badge-info">CASH/TREPS</span>
                            )}
                            {fundSecDto?.classificationProvenance === "ASSET_CLASS_DEBT" && (
                              <span className="badge text-[9px] badge-info">SOVEREIGN DEBT</span>
                            )}
                            {fundSecDto?.classificationProvenance === "UNCLASSIFIED" && (
                              <span className="badge text-[9px] badge-warning">UNCLASSIFIED</span>
                            )}
                          </div>
                          {fundSecDto?.topHoldings && fundSecDto.topHoldings.length > 0 && (
                            <div className="truncate text-text-muted font-mono text-[10px]" title={fundSecDto.topHoldings.join(", ")}>
                              {fundSecDto.topHoldings.join(", ")}
                            </div>
                          )}
                        </td>
                      </tr>

                      {/* Sub-Industry Level Drill-Down (Rendered when Macro Sector is Expanded) */}
                      {isSectorExpanded && subIndustries.map((ind: FundIndustryExposureDto) => {
                        const indKey = `${c.sectorName}::${ind.industryName}`;
                        const isIndExpanded = expandedIndustries.has(indKey);
                        const indComp = selectedObs.industryComparisons?.find(
                          (ic) => ic.parentSectorName === c.sectorName && ic.industryName === ind.industryName
                        );

                        // Securities under this sub-industry
                        const securitiesInInd = selectedObs.securityWaterfall?.filter(
                          (sec) => sec.parentSectorName === c.sectorName && sec.industryName === ind.industryName
                        ) ?? [];

                        return (
                          <React.Fragment key={indKey}>
                            <tr className="bg-surface-elevated/20 hover:bg-surface-elevated/40 transition-colors border-l-2 border-primary/50">
                              <td className="py-2.5 px-3 pl-8 text-text-primary">
                                <div className="flex items-center gap-2">
                                  <button
                                    onClick={() => toggleIndustry(c.sectorName, ind.industryName)}
                                    className="w-4 h-4 flex items-center justify-center rounded text-text-muted hover:text-text-primary transition-colors text-[10px] font-mono"
                                    aria-label={isIndExpanded ? "Collapse industry waterfall" : "Expand industry waterfall"}
                                  >
                                    {isIndExpanded ? "▼" : "▶"}
                                  </button>
                                  <span className="text-xs font-medium text-text-primary">
                                    {ind.industryName}
                                  </span>
                                  <span className="text-[9px] px-1.5 py-0.2 rounded bg-surface border border-border/80 text-text-muted font-mono">
                                    {ind.classificationLevel.replace(/_/g, " ")}
                                  </span>
                                  {securitiesInInd.length > 0 && (
                                    <span className="text-[9px] text-text-muted">
                                      ({securitiesInInd.length} {securitiesInInd.length === 1 ? "holding" : "holdings"})
                                    </span>
                                  )}
                                </div>
                              </td>

                              <td className="py-2.5 px-3 text-right font-mono font-medium text-text-primary">
                                {ind.fundWeightPct.toFixed(2)}%
                              </td>

                              <td className="py-2.5 px-3 text-center text-text-secondary font-mono text-[11px]">
                                {ind.holdingCount}
                              </td>

                              <td className="py-2.5 px-3 text-right font-mono text-[11px]">
                                {indComp?.benchmarkDisclosedWeightPct !== null && indComp?.benchmarkDisclosedWeightPct !== undefined ? (
                                  <span className="text-accent font-medium">
                                    {indComp.benchmarkDisclosedWeightPct.toFixed(2)}%
                                    <span className="text-[9px] text-text-muted block font-sans">
                                      (Lower bound)
                                    </span>
                                  </span>
                                ) : (
                                  <span className="text-text-muted">Undisclosed</span>
                                )}
                              </td>

                              <td className="py-2.5 px-3 text-center text-text-muted font-mono text-[11px]">
                                {indComp && indComp.benchmarkConstituentCount > 0 ? (
                                  <span>
                                    {indComp.benchmarkWeightedConstituentCount} wtd / {indComp.benchmarkConstituentCount} BM
                                  </span>
                                ) : (
                                  "0 in BM"
                                )}
                              </td>

                              <td className="py-2.5 px-3 text-right">
                                {indComp?.definitiveActiveWeightPct !== null && indComp?.definitiveActiveWeightPct !== undefined ? (
                                  <span
                                    className={`font-mono font-bold ${
                                      indComp.definitiveActiveWeightPct >= 0 ? "text-success-fg" : "text-danger"
                                    }`}
                                  >
                                    {indComp.definitiveActiveWeightPct >= 0 ? "+" : ""}
                                    {indComp.definitiveActiveWeightPct.toFixed(2)}%
                                  </span>
                                ) : (
                                  <span
                                    className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[9px] font-semibold bg-warning/15 text-warning-fg border border-warning/30"
                                    title={indComp?.statusReason || "Benchmark constituent tail unweighted"}
                                  >
                                    Gated
                                  </span>
                                )}
                              </td>

                              <td className="py-2.5 px-3 text-[10px] text-text-muted">
                                <div className="flex items-center gap-1.5">
                                  <span className="mono-meta">{ind.classificationSource}</span>
                                  <span>•</span>
                                  <span>{ind.classificationCoveragePct.toFixed(0)}% Cov</span>
                                </div>
                                {ind.topHoldings && ind.topHoldings.length > 0 && (
                                  <div className="truncate text-text-muted font-mono text-[10px] mt-0.5">
                                    {ind.topHoldings.slice(0, 2).join(", ")}
                                  </div>
                                )}
                              </td>
                            </tr>

                            {/* Security-Level Active Exposure Waterfall (Rendered when Sub-Industry is Expanded) */}
                            {isIndExpanded && (
                              <tr className="bg-surface-elevated/40">
                                <td colSpan={7} className="p-3 pl-12 pr-4">
                                  <div className="rounded-lg border border-border/80 bg-surface overflow-hidden shadow-sm">
                                    <div className="p-2.5 bg-surface-elevated border-b border-border flex items-center justify-between text-xs">
                                      <div className="flex items-center gap-2">
                                        <span className="font-semibold text-text-primary text-[11px] uppercase tracking-wider">
                                          Security-Level Active Exposure Waterfall — {ind.industryName}
                                        </span>
                                        <span className="text-[10px] px-1.5 py-0.2 rounded bg-surface border border-border text-text-muted font-mono">
                                          {securitiesInInd.length} Constituents
                                        </span>
                                      </div>
                                      <div className="text-[10px] text-text-muted mono-meta">
                                        Tri-Category Decomposition Rule (§AGENTS.md)
                                      </div>
                                    </div>

                                    {securitiesInInd.length === 0 ? (
                                      <div className="p-4 text-xs text-text-muted text-center italic">
                                        No individual security records found under this classification.
                                      </div>
                                    ) : (
                                      <table className="w-full text-left text-xs">
                                        <thead className="bg-surface-elevated/40 border-b border-border/60 text-[10px] text-text-muted font-mono uppercase tracking-wider">
                                          <tr>
                                            <th className="py-2 px-3">Security &amp; ISIN</th>
                                            <th className="py-2 px-3 text-right">Fund Weight</th>
                                            <th className="py-2 px-3 text-right">Benchmark Weight</th>
                                            <th className="py-2 px-3 text-right">Active Spread (ΔW)</th>
                                            <th className="py-2 px-3">Exposure Waterfall Category</th>
                                            <th className="py-2 px-3">Membership &amp; Inclusion Reason</th>
                                          </tr>
                                        </thead>
                                        <tbody className="divide-y divide-border/40 font-mono text-[11px]">
                                          {securitiesInInd.map((sec: SecurityExposureWaterfallDto, sIdx: number) => {
                                            return (
                                              <tr key={sec.isin || `${sec.companyName}-${sIdx}`} className="hover:bg-surface-elevated/30 transition-colors">
                                                <td className="py-2 px-3">
                                                  <div className="font-sans font-medium text-text-primary text-xs">
                                                    {sec.companyName}
                                                  </div>
                                                  <div className="text-[10px] text-text-muted flex items-center gap-2 mt-0.5">
                                                    <span>ISIN: {sec.isin || "Unavailable"}</span>
                                                    {sec.symbol && <span>• Symbol: {sec.symbol}</span>}
                                                  </div>
                                                </td>

                                                <td className="py-2 px-3 text-right font-semibold text-text-primary">
                                                  {sec.fundWeightPct > 0 ? `${sec.fundWeightPct.toFixed(2)}%` : "0.00%"}
                                                </td>

                                                <td className="py-2 px-3 text-right">
                                                  {sec.benchmarkWeightPct !== null ? (
                                                    <span className="text-accent font-medium">
                                                      {sec.benchmarkWeightPct.toFixed(2)}%
                                                    </span>
                                                  ) : sec.membershipStatus === "OFF_BENCHMARK" ? (
                                                    <span className="text-text-muted">0.00% (Absent)</span>
                                                  ) : (
                                                    <span className="text-text-muted italic text-[10px]" title="Tail constituent in index; weight not disclosed in factsheet">
                                                      Undisclosed (Tail)
                                                    </span>
                                                  )}
                                                </td>

                                                <td className="py-2 px-3 text-right font-bold">
                                                  {sec.activeSpreadPct !== null ? (
                                                    <span className={sec.activeSpreadPct >= 0 ? "text-success-fg" : "text-danger"}>
                                                      {sec.activeSpreadPct >= 0 ? "+" : ""}
                                                      {sec.activeSpreadPct.toFixed(2)}%
                                                    </span>
                                                  ) : (
                                                    <span className="text-text-muted font-normal text-[10px]" title="Active spread withheld because benchmark weight is unknown (Zero Fake Data rule)">
                                                      —
                                                    </span>
                                                  )}
                                                </td>

                                                <td className="py-2 px-3 font-sans">
                                                  {sec.exposureCategory === "COMPARABLE_ACTIVE_EXPOSURE" && (
                                                    <span className="badge text-[9px] badge-success" title="Both portfolio and benchmark weights verified. Spread is mathematically valid.">
                                                      COMPARABLE ACTIVE
                                                    </span>
                                                  )}
                                                  {sec.exposureCategory === "BENCHMARK_MEMBER_WEIGHT_UNKNOWN" && (
                                                    <span className="badge text-[9px] badge-warning" title="Verified constituent of index, but factsheet withheld constituent weight. Spread strictly withheld.">
                                                      BM MEMBER (UNWEIGHTED)
                                                    </span>
                                                  )}
                                                  {sec.exposureCategory === "OFF_BENCHMARK_HOLDING" && (
                                                    <span className="badge text-[9px] badge-accent" title="Verified absent from complete 500-constituent benchmark snapshot. Complete active overweight.">
                                                      OFF-BENCHMARK
                                                    </span>
                                                  )}
                                                  {sec.exposureCategory === "UNRESOLVED_MEMBERSHIP" && (
                                                    <span className="badge text-[9px] badge-secondary" title="Security identifier or classification unresolved. Cannot classify as off-benchmark.">
                                                      ISIN UNRESOLVED
                                                    </span>
                                                  )}
                                                </td>

                                                <td className="py-2 px-3 font-sans text-[10px] text-text-secondary max-w-xs">
                                                  <div>{sec.inclusionReason}</div>
                                                  <div className="text-[9px] text-text-muted mt-0.5">
                                                    Prov: {sec.classificationProvenance}
                                                  </div>
                                                </td>
                                              </tr>
                                            );
                                          })}
                                        </tbody>
                                      </table>
                                    )}
                                  </div>
                                </td>
                              </tr>
                            )}
                          </React.Fragment>
                        );
                      })}
                    </React.Fragment>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* 7. TAB 2: HISTORICAL SECTOR DRIFT TRAJECTORY */}
      {activeSubTab === "DRIFT_TRAJECTORY" && (
        <div className="space-y-5">
          <div className="text-xs text-text-secondary leading-relaxed">
            Observed chronological sector allocation series across all available portfolio snapshots.
            YUKIRA reports exact point-in-time disclosures without interpolating unobserved calendar intervals.
          </div>

          <div className="overflow-x-auto rounded-lg border border-border bg-surface">
            <table className="w-full text-left text-xs">
              <thead className="bg-surface-elevated/80 border-b border-border text-[11px] text-text-muted font-mono uppercase tracking-wider">
                <tr>
                  <th className="py-3 px-3 sticky left-0 bg-surface-elevated z-10">Sector Name</th>
                  {data.observations.map((obs) => (
                    <th key={obs.portfolioDate} className="py-3 px-3 text-right whitespace-nowrap">
                      <div>{obs.portfolioDate}</div>
                      <span className="text-[9px] font-sans font-normal text-text-muted block">
                        {obs.portfolioDisclosureScope === "PARTIAL_TOP_HOLDINGS" ? "Top 10" : "Full Portfolio"}
                      </span>
                    </th>
                  ))}
                  <th className="py-3 px-3 text-right">Net Shift (First → Last)</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/60">
                {allUniqueSectors.map((secName) => {
                  const firstObs = data.observations[0];
                  const lastObs = data.observations[data.observations.length - 1];
                  const firstWeight = firstObs?.sectors.find((s) => s.sectorName === secName)?.fundWeightPct ?? 0;
                  const lastWeight = lastObs?.sectors.find((s) => s.sectorName === secName)?.fundWeightPct ?? 0;
                  const netShift = lastWeight - firstWeight;

                  return (
                    <tr key={secName} className="hover:bg-surface-elevated/30 transition-colors">
                      <td className="py-2.5 px-3 font-medium text-text-primary sticky left-0 bg-surface z-10 whitespace-nowrap">
                        {secName}
                      </td>

                      {data.observations.map((obs) => {
                        const secDto = obs.sectors.find((s) => s.sectorName === secName);
                        const w = secDto?.fundWeightPct ?? 0;

                        return (
                          <td key={obs.portfolioDate} className="py-2.5 px-3 text-right font-mono">
                            {w > 0 ? (
                              <span className="font-semibold text-text-primary">{w.toFixed(2)}%</span>
                            ) : (
                              <span className="text-text-muted text-[11px]">0.00%</span>
                            )}
                          </td>
                        );
                      })}

                      <td className="py-2.5 px-3 text-right font-mono font-bold">
                        <span className={netShift > 0.05 ? "text-success-fg" : netShift < -0.05 ? "text-danger" : "text-text-muted"}>
                          {netShift > 0.05 ? "+" : ""}
                          {netShift.toFixed(2)} pp
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

      {/* 8. TAB 3: SHIFT DISENTANGLEMENT */}
      {activeSubTab === "SHIFT_DISENTANGLEMENT" && (
        <div className="space-y-4">
          <div className="p-3.5 rounded-lg border border-border/80 bg-surface-elevated/20 text-xs text-text-secondary leading-relaxed">
            <strong className="text-text-primary">Apparent Change Disentanglement Principle: </strong>
            Changes in reported sector and sub-industry weights arise from multiple empirical factors including manager rebalancing,
            relative market prices of underlying holdings, fund subscriptions/redemptions, corporate actions, and changes in disclosure scope.
            YUKIRA categorizes observed shifts objectively and refuses to attribute weight drift solely to manager active skill.
          </div>

          {/* Toggle between Macro-Sector and Sub-Industry Shifts */}
          <div className="flex items-center gap-2 border-b border-border pb-2">
            <button
              onClick={() => setDisentanglementScope("SECTOR")}
              className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors ${
                disentanglementScope === "SECTOR"
                  ? "bg-primary text-white"
                  : "bg-surface border border-border text-text-secondary hover:text-text-primary"
              }`}
            >
              Macro-Sector Shifts ({data.sectorDriftChanges.length})
            </button>
            <button
              onClick={() => setDisentanglementScope("INDUSTRY")}
              className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors ${
                disentanglementScope === "INDUSTRY"
                  ? "bg-primary text-white"
                  : "bg-surface border border-border text-text-secondary hover:text-text-primary"
              }`}
            >
              Sub-Industry Shifts ({data.industryDriftChanges?.length ?? 0})
            </button>
          </div>

          {disentanglementScope === "SECTOR" ? (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              {data.sectorDriftChanges.map((change: SectorDriftChangeDto, idx: number) => {
                const isPositive = change.deltaWeightPct >= 0;

                return (
                  <div
                    key={`${change.earlierDate}-${change.laterDate}-${change.sectorName}-${idx}`}
                    className="p-4 rounded-lg border border-border bg-surface space-y-2 hover:border-border/80 transition-colors"
                  >
                    <div className="flex items-center justify-between gap-2">
                      <div className="flex items-center gap-2">
                        <span className="font-semibold text-text-primary text-xs">
                          {change.sectorName}
                        </span>
                        <span
                          className={`badge text-[9px] font-semibold tracking-wide ${
                            change.changeDriver === "DISCLOSURE_SCOPE_SHIFT"
                              ? "badge-warning"
                              : change.changeDriver === "SECURITY_ENTRY_EXIT"
                              ? "badge-accent"
                              : "badge-success"
                          }`}
                        >
                          {change.changeDriver.replace(/_/g, " ")}
                        </span>
                      </div>

                      <span
                        className={`font-mono text-xs font-bold ${
                          isPositive ? "text-success-fg" : "text-danger"
                        }`}
                      >
                        {isPositive ? "+" : ""}
                        {change.deltaWeightPct.toFixed(2)} pp
                      </span>
                    </div>

                    <div className="flex items-center gap-3 text-[11px] text-text-muted mono-meta">
                      <span>
                        {change.earlierDate} ({change.earlierWeightPct.toFixed(2)}%, {change.earlierHoldingCount} holdings)
                      </span>
                      <span>→</span>
                      <span>
                        {change.laterDate} ({change.laterWeightPct.toFixed(2)}%, {change.laterHoldingCount} holdings)
                      </span>
                    </div>

                    <p className="text-xs text-text-secondary leading-relaxed pt-1">
                      {change.driverExplanation}
                    </p>
                  </div>
                );
              })}
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              {(!data.industryDriftChanges || data.industryDriftChanges.length === 0) ? (
                <div className="col-span-2 p-6 rounded-lg border border-border bg-surface text-center text-xs text-text-muted italic">
                  No chronological sub-industry drift changes recorded.
                </div>
              ) : (
                data.industryDriftChanges.map((change: IndustryDriftChangeDto, idx: number) => {
                  const isPositive = change.deltaWeightPct >= 0;

                  return (
                    <div
                      key={`${change.earlierDate}-${change.laterDate}-${change.parentSectorName}-${change.industryName}-${idx}`}
                      className="p-4 rounded-lg border border-border bg-surface space-y-2 hover:border-border/80 transition-colors"
                    >
                      <div className="flex items-center justify-between gap-2">
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="font-semibold text-text-primary text-xs">
                              {change.industryName}
                            </span>
                            <span
                              className={`badge text-[9px] font-semibold tracking-wide ${
                                change.changeDriver === "DISCLOSURE_SCOPE_SHIFT"
                                  ? "badge-warning"
                                  : change.changeDriver === "SECURITY_ENTRY_EXIT"
                                  ? "badge-accent"
                                  : "badge-success"
                              }`}
                            >
                              {change.changeDriver.replace(/_/g, " ")}
                            </span>
                          </div>
                          <span className="text-[10px] text-text-muted block mt-0.5">
                            Sector: {change.parentSectorName}
                          </span>
                        </div>

                        <span
                          className={`font-mono text-xs font-bold ${
                            isPositive ? "text-success-fg" : "text-danger"
                          }`}
                        >
                          {isPositive ? "+" : ""}
                          {change.deltaWeightPct.toFixed(2)} pp
                        </span>
                      </div>

                      <div className="flex items-center gap-3 text-[11px] text-text-muted mono-meta">
                        <span>
                          {change.earlierDate} ({change.earlierWeightPct.toFixed(2)}%, {change.earlierHoldingCount} holdings)
                        </span>
                        <span>→</span>
                        <span>
                          {change.laterDate} ({change.laterWeightPct.toFixed(2)}%, {change.laterHoldingCount} holdings)
                        </span>
                      </div>

                      <p className="text-xs text-text-secondary leading-relaxed pt-1">
                        {change.driverExplanation}
                      </p>
                    </div>
                  );
                })
              )}
            </div>
          )}
        </div>
      )}

      {/* 9. TAB 4: EVIDENCE & BENCHMARK AUDIT */}
      {activeSubTab === "EVIDENCE_AUDIT" && (
        <div className="space-y-6">
          {/* Tri-Category Waterfall Principles */}
          <div className="p-4 rounded-lg border border-border bg-surface space-y-2">
            <h4 className="text-xs font-semibold text-text-primary uppercase tracking-wider">
              Tri-Category Security-Level Active Exposure Waterfall Standards
            </h4>
            <p className="text-xs text-text-secondary leading-relaxed">
              In accordance with YUKIRA constitutional engineering principles, security-level active weight comparisons are strictly segmented into three verified states:
            </p>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3 pt-2">
              <div className="p-3 rounded border border-border/80 bg-surface-elevated/30 space-y-1">
                <span className="badge badge-success text-[10px]">1. COMPARABLE ACTIVE EXPOSURE</span>
                <p className="text-xs text-text-secondary leading-relaxed pt-1">
                  Both portfolio weight and benchmark weight are verifiable from authentic sources (e.g. top-weighted benchmark factsheet heavyweights like Reliance or HDFC Bank).
                  Active spread (<strong className="font-mono">ΔW = Wp - Wb</strong>) is mathematically verified.
                </p>
              </div>

              <div className="p-3 rounded border border-border/80 bg-surface-elevated/30 space-y-1">
                <span className="badge badge-warning text-[10px]">2. BM MEMBER (WEIGHT UNKNOWN)</span>
                <p className="text-xs text-text-secondary leading-relaxed pt-1">
                  The security is verified as a constituent of NIFTY 500, but publisher factsheets withhold its exact weight.
                  Active spread is <strong className="font-semibold text-text-primary">strictly withheld (em dash)</strong>. YUKIRA never treats unweighted index members as 0.00%.
                </p>
              </div>

              <div className="p-3 rounded border border-border/80 bg-surface-elevated/30 space-y-1">
                <span className="badge badge-accent text-[10px]">3. OFF-BENCHMARK HOLDING</span>
                <p className="text-xs text-text-secondary leading-relaxed pt-1">
                  The security is verified as demonstrably absent from a complete (&ge; 400 constituent) point-in-time benchmark snapshot.
                  Its benchmark weight is genuine 0.00%, producing an active spread of <strong className="font-mono">+Wp</strong>.
                </p>
              </div>
            </div>
          </div>

          {/* Missing Data Requirements Strip */}
          <div className="p-4 rounded-lg border border-border bg-surface space-y-2">
            <h4 className="text-xs font-semibold text-text-primary uppercase tracking-wider">
              Data Quality Dependencies &amp; Required Market Feeds
            </h4>
            <p className="text-xs text-text-secondary leading-relaxed">
              To unlock full constituent-level active weights and Brinson-Fachler sector attribution, the following verified commercial data feeds are required:
            </p>
            <ul className="space-y-1.5 pt-1">
              {data.missingDataRequirements.map((req, i) => (
                <li key={i} className="flex items-start gap-2 text-xs text-text-secondary">
                  <span className="text-accent font-mono">[{i + 1}]</span>
                  <span>{req}</span>
                </li>
              ))}
            </ul>
          </div>

          {/* Epistemic Limitations */}
          <div className="p-4 rounded-lg border border-border bg-surface space-y-2">
            <h4 className="text-xs font-semibold text-text-primary uppercase tracking-wider">
              Methodology Governance &amp; Constitutional Epistemic Boundaries
            </h4>
            <ul className="space-y-1.5">
              {data.dataQualityLimitations.map((lim, i) => (
                <li key={i} className="flex items-start gap-2 text-xs text-text-secondary leading-relaxed">
                  <span className="text-text-muted font-mono">•</span>
                  <span>{lim}</span>
                </li>
              ))}
            </ul>
          </div>

          {/* Allocator Due-Diligence Investigation Prompts */}
          <div className="p-4 rounded-lg border border-border bg-surface-elevated/20 space-y-2">
            <h4 className="text-xs font-semibold text-text-primary uppercase tracking-wider">
              Targeted Allocator Due-Diligence Investigation Prompts
            </h4>
            <ul className="space-y-2 pt-1">
              {data.investigationPrompts.map((prompt, i) => (
                <li key={i} className="p-2.5 rounded bg-surface border border-border/60 text-xs text-text-secondary flex items-start gap-2.5">
                  <span className="text-primary font-bold">Q{i + 1}:</span>
                  <span className="leading-relaxed">{prompt}</span>
                </li>
              ))}
            </ul>
          </div>
        </div>
      )}
    </div>
  );
}

