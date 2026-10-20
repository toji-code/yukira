"use client";

import React, { useState, useEffect, useMemo } from "react";
import {
  BenchmarkCompositionComparisonResponseDto,
  ActiveSecurityPositionDto,
  BenchmarkDivergenceResponseDto,
  BenchmarkReconstitutionResponseDto,
  PeriodDivergenceSummaryDto,
  ApparentChangeBreakdownDto,
  ReconstitutionMemberDto,
  fetchBenchmarkDivergence,
  fetchBenchmarkReconstitution,
} from "@/lib/api/portfolio";
import { ActiveShareTrajectoryView } from "./ActiveShareTrajectoryView";
import { SectorDriftView } from "./SectorDriftView";

interface BenchmarkCompositionSectionProps {
  comparison: BenchmarkCompositionComparisonResponseDto | null;
  loading: boolean;
  schemeOptionId?: number;
  effectiveAsOfDate?: string;
  effectiveCutoffTime?: string;
  onRefresh?: () => void;
}

type SectionViewMode =
  | "SNAPSHOT_COMPOSITION"
  | "ACTIVE_SHARE_TRAJECTORY"
  | "MULTI_PERIOD_DIVERGENCE"
  | "INDEX_RECONSTITUTION"
  | "SECTOR_DRIFT"
  | "BRINSON_MANIFEST";

type PositionFilter = "ALL" | "OVERWEIGHT" | "UNDERWEIGHT" | "BENCHMARK_ONLY" | "FUND_ONLY" | "UNWEIGHTED";
type ApparentChangeFilter = "ALL" | "FUND_PORTFOLIO_CHANGE" | "BENCHMARK_MEMBERSHIP_CHANGE" | "CLASSIFICATION_COVERAGE_CHANGE";
type ReconstitutionFilter = "ALL" | "ENTERED" | "EXITED" | "RETAINED";

export function BenchmarkCompositionSection({
  comparison,
  loading,
  schemeOptionId: propSchemeOptionId,
  effectiveAsOfDate,
  effectiveCutoffTime,
  onRefresh,
}: BenchmarkCompositionSectionProps) {
  const [viewMode, setViewMode] = useState<SectionViewMode>("SNAPSHOT_COMPOSITION");
  const [positionFilter, setPositionFilter] = useState<PositionFilter>("ALL");
  const [showFullLineage, setShowFullLineage] = useState<boolean>(false);

  // Multi-period Divergence state
  const [divergenceData, setDivergenceData] = useState<BenchmarkDivergenceResponseDto | null>(null);
  const [divergenceLoading, setDivergenceLoading] = useState<boolean>(false);
  const [divergenceError, setDivergenceError] = useState<string | null>(null);
  const [apparentChangeFilter, setApparentChangeFilter] = useState<ApparentChangeFilter>("ALL");

  // Index Reconstitution state
  const [reconstitutionData, setReconstitutionData] = useState<BenchmarkReconstitutionResponseDto | null>(null);
  const [reconstitutionLoading, setReconstitutionLoading] = useState<boolean>(false);
  const [reconstitutionError, setReconstitutionError] = useState<string | null>(null);
  const [reconstitutionFilter, setReconstitutionFilter] = useState<ReconstitutionFilter>("ENTERED");
  const [reconstitutionSearch, setReconstitutionSearch] = useState<string>("");

  const resolvedSchemeOptionId = propSchemeOptionId ?? comparison?.schemeOptionId;

  // Fetch Multi-period Divergence when entering view
  useEffect(() => {
    if (viewMode === "MULTI_PERIOD_DIVERGENCE" && resolvedSchemeOptionId && !divergenceData && !divergenceLoading) {
      setDivergenceLoading(true);
      setDivergenceError(null);
      fetchBenchmarkDivergence(resolvedSchemeOptionId, undefined, undefined, effectiveCutoffTime)
        .then((res) => setDivergenceData(res))
        .catch((err) => setDivergenceError(err.message || "Failed to load multi-period divergence"))
        .finally(() => setDivergenceLoading(false));
    }
  }, [viewMode, resolvedSchemeOptionId, divergenceData, divergenceLoading, effectiveCutoffTime]);

  // Fetch Index Reconstitution when entering view
  useEffect(() => {
    if (viewMode === "INDEX_RECONSTITUTION" && resolvedSchemeOptionId && !reconstitutionData && !reconstitutionLoading) {
      setReconstitutionLoading(true);
      setReconstitutionError(null);
      fetchBenchmarkReconstitution(resolvedSchemeOptionId, comparison?.benchmarkCode, undefined, undefined, effectiveCutoffTime)
        .then((res) => setReconstitutionData(res))
        .catch((err) => setReconstitutionError(err.message || "Failed to load benchmark reconstitution"))
        .finally(() => setReconstitutionLoading(false));
    }
  }, [viewMode, resolvedSchemeOptionId, comparison?.benchmarkCode, reconstitutionData, reconstitutionLoading, effectiveCutoffTime]);

  // Filtered positions for Snapshot Composition
  const filteredPositions = useMemo(() => {
    if (!comparison?.activePositions) return [];
    return comparison.activePositions.filter((pos: ActiveSecurityPositionDto) => {
      if (positionFilter === "ALL") return true;
      if (positionFilter === "OVERWEIGHT") return pos.positionStatus === "OVERWEIGHT";
      if (positionFilter === "UNDERWEIGHT") return pos.positionStatus === "UNDERWEIGHT";
      if (positionFilter === "BENCHMARK_ONLY") return pos.positionStatus === "BENCHMARK_ONLY";
      if (positionFilter === "FUND_ONLY") return pos.positionStatus === "FUND_ONLY";
      if (positionFilter === "UNWEIGHTED") return pos.positionStatus === "IN_BENCHMARK_UNWEIGHTED" || pos.benchmarkWeightPct == null;
      return true;
    });
  }, [comparison?.activePositions, positionFilter]);

  // Filtered Apparent Changes for Multi-period Divergence
  const filteredApparentChanges = useMemo(() => {
    if (!divergenceData?.apparentChanges) return [];
    return divergenceData.apparentChanges.filter((change: ApparentChangeBreakdownDto) => {
      if (apparentChangeFilter === "ALL") return true;
      return change.changeCategory === apparentChangeFilter;
    });
  }, [divergenceData?.apparentChanges, apparentChangeFilter]);

  // Filtered Reconstitution Constituents
  const filteredReconstitutionList = useMemo(() => {
    if (!reconstitutionData) return [];
    let list: ReconstitutionMemberDto[] = [];
    if (reconstitutionFilter === "ENTERED") list = reconstitutionData.enteredConstituents;
    else if (reconstitutionFilter === "EXITED") list = reconstitutionData.exitedConstituents;
    else if (reconstitutionFilter === "RETAINED") list = reconstitutionData.retainedConstituents;
    else {
      list = [
        ...reconstitutionData.enteredConstituents,
        ...reconstitutionData.exitedConstituents,
        ...reconstitutionData.retainedConstituents,
      ];
    }

    if (!reconstitutionSearch.trim()) return list;
    const query = reconstitutionSearch.toLowerCase();
    return list.filter(
      (item) =>
        item.companyName.toLowerCase().includes(query) ||
        (item.symbol && item.symbol.toLowerCase().includes(query)) ||
        item.isin.toLowerCase().includes(query) ||
        (item.industry && item.industry.toLowerCase().includes(query))
    );
  }, [reconstitutionData, reconstitutionFilter, reconstitutionSearch]);

  if (loading) {
    return (
      <div className="panel space-y-4 p-5 md:p-6" aria-busy="true">
        <div className="flex items-center justify-between border-b border-border/60 pb-3">
          <div className="skeleton h-4 w-1/3" />
          <div className="skeleton h-3 w-1/5" />
        </div>
        <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
          <div className="skeleton h-20 w-full" />
          <div className="skeleton h-20 w-full" />
          <div className="skeleton h-20 w-full" />
          <div className="skeleton h-20 w-full" />
        </div>
        <div className="skeleton h-64 w-full" />
      </div>
    );
  }

  if (!comparison || comparison.fundHoldingsCount === 0) {
    return (
      <div className="panel p-6 text-center space-y-3">
        <div className="text-warning text-2xl font-bold">⚠️</div>
        <h3 className="text-sm font-semibold text-text-primary">Benchmark Composition Diagnostics Unavailable</h3>
        <p className="text-xs text-text-secondary max-w-md mx-auto">
          {comparison?.brinsonBlockerReason ||
            "No disclosed portfolio snapshot or benchmark constituent record was found under the current Point-in-Time knowledge cutoff."}
        </p>
        {onRefresh && (
          <button
            onClick={onRefresh}
            className="px-3 py-1.5 rounded text-xs bg-surface-elevated hover:bg-surface-elevated/80 border border-border text-text-primary transition-colors"
          >
            Retry Verification
          </button>
        )}
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* View Mode Navigation Tabs */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border/60 pb-3">
        <div className="flex flex-wrap items-center gap-1.5 text-xs">
          <button
            onClick={() => setViewMode("SNAPSHOT_COMPOSITION")}
            className={`px-3 py-1.5 rounded font-medium transition-colors ${
              viewMode === "SNAPSHOT_COMPOSITION"
                ? "bg-primary text-primary-fg font-semibold shadow-sm"
                : "bg-surface-elevated hover:bg-surface-elevated/80 text-text-secondary"
            }`}
          >
            Snapshot Composition &amp; Spread
          </button>
          <button
            onClick={() => setViewMode("ACTIVE_SHARE_TRAJECTORY")}
            className={`px-3 py-1.5 rounded font-medium transition-colors ${
              viewMode === "ACTIVE_SHARE_TRAJECTORY"
                ? "bg-primary text-primary-fg font-semibold shadow-sm"
                : "bg-surface-elevated hover:bg-surface-elevated/80 text-text-secondary"
            }`}
          >
            Active Share &amp; Off-BM Trajectory
          </button>
          <button
            onClick={() => setViewMode("MULTI_PERIOD_DIVERGENCE")}
            className={`px-3 py-1.5 rounded font-medium transition-colors ${
              viewMode === "MULTI_PERIOD_DIVERGENCE"
                ? "bg-primary text-primary-fg font-semibold shadow-sm"
                : "bg-surface-elevated hover:bg-surface-elevated/80 text-text-secondary"
            }`}
          >
            Multi-Period Divergence &amp; 3 Sources of Change
          </button>
          <button
            onClick={() => setViewMode("INDEX_RECONSTITUTION")}
            className={`px-3 py-1.5 rounded font-medium transition-colors ${
              viewMode === "INDEX_RECONSTITUTION"
                ? "bg-primary text-primary-fg font-semibold shadow-sm"
                : "bg-surface-elevated hover:bg-surface-elevated/80 text-text-secondary"
            }`}
          >
            Index Reconstitution &amp; Turnover
          </button>
          <button
            onClick={() => setViewMode("SECTOR_DRIFT")}
            className={`px-3 py-1.5 rounded font-medium transition-colors ${
              viewMode === "SECTOR_DRIFT"
                ? "bg-primary text-primary-fg font-semibold shadow-sm"
                : "bg-surface-elevated hover:bg-surface-elevated/80 text-text-secondary"
            }`}
          >
            Sector &amp; Industry Drift
          </button>
          <button
            onClick={() => setViewMode("BRINSON_MANIFEST")}
            className={`px-3 py-1.5 rounded font-medium transition-colors ${
              viewMode === "BRINSON_MANIFEST"
                ? "bg-warning text-warning-fg font-semibold shadow-sm"
                : "bg-surface-elevated hover:bg-surface-elevated/80 text-text-secondary"
            }`}
          >
            Attribution Readiness Gate (Gated)
          </button>
        </div>

        {onRefresh && (
          <button
            onClick={onRefresh}
            className="text-[11px] text-primary hover:underline font-mono"
            title="Re-verify point-in-time calculation"
          >
            ↻ Refresh Data
          </button>
        )}
      </div>

      {/* ========================================================================= */}
      {/* VIEW 1: SNAPSHOT COMPOSITION & SPREAD */}
      {/* ========================================================================= */}
      {viewMode === "SNAPSHOT_COMPOSITION" && (
        <div className="space-y-6">
          {/* Key Metric Diagnostic Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4">
            {/* Overlap Card */}
            <div className="panel p-4 space-y-1.5 border-l-4 border-l-primary">
              <span className="mono-meta text-[11px] text-text-secondary uppercase tracking-wider">
                Holdings Overlap
              </span>
              <div className="flex items-baseline gap-2">
                <span className="text-2xl font-bold font-mono text-text-primary">
                  {comparison.holdingsOverlapPct != null ? `${comparison.holdingsOverlapPct.toFixed(2)}%` : "—"}
                </span>
                <span className="text-[11px] text-text-secondary">with {comparison.benchmarkCode}</span>
              </div>
              <div className="text-[11px] text-text-muted">
                Common: <strong className="text-text-primary">{comparison.commonConstituentCount}</strong> | Fund-Only: <strong className="text-text-primary">{comparison.fundSpecificCount}</strong>
              </div>
            </div>

            {/* Benchmark Coverage */}
            <div className="panel p-4 space-y-1.5">
              <span className="mono-meta text-[11px] text-text-secondary uppercase tracking-wider">
                Benchmark Composition
              </span>
              <div className="flex items-baseline gap-2">
                <span className="text-2xl font-bold font-mono text-text-primary">
                  {comparison.benchmarkConstituentCount}
                </span>
                <span className="text-[11px] text-text-secondary">Constituents</span>
              </div>
              <div className="text-[11px] text-text-muted flex items-center justify-between">
                <span>Mapped: <strong className="text-text-primary">{comparison.mappedConstituentCount}</strong></span>
                <span>Unmapped: <strong className="text-text-primary">{comparison.unmappedConstituentCount}</strong></span>
              </div>
            </div>

            {/* Weight Disclosure Status */}
            <div className="panel p-4 space-y-1.5">
              <span className="mono-meta text-[11px] text-text-secondary uppercase tracking-wider">
                Weight Disclosure
              </span>
              <div className="flex items-center gap-2">
                <span className={`badge text-[10px] font-semibold tracking-wide ${
                  comparison.weightCoverageStatus === "TOP_WEIGHTS_DISCLOSED"
                    ? "badge-warning"
                    : comparison.weightCoverageStatus === "MEMBERSHIP_ONLY"
                    ? "badge-neutral"
                    : "badge-success"
                }`}>
                  {comparison.weightCoverageStatus === "TOP_WEIGHTS_DISCLOSED"
                    ? "TOP WEIGHTS DISCLOSED"
                    : comparison.weightCoverageStatus}
                </span>
              </div>
              <div className="text-[11px] text-text-muted">
                Disclosed: <strong className="text-text-primary font-mono">{comparison.sumDisclosedBenchmarkWeightsPct.toFixed(2)}%</strong> of benchmark
              </div>
            </div>

            {/* Portfolio Date & PIT Cutoff */}
            <div className="panel p-4 space-y-1.5">
              <span className="mono-meta text-[11px] text-text-secondary uppercase tracking-wider">
                Point-in-Time Parameters
              </span>
              <div className="text-xs space-y-1">
                <div className="flex justify-between">
                  <span className="text-text-secondary">Portfolio Date:</span>
                  <span className="font-mono font-medium text-text-primary">{comparison.portfolioDate}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-text-secondary">Benchmark Date:</span>
                  <span className="font-mono font-medium text-text-primary">{comparison.benchmarkCompositionDate}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-text-secondary">Fund Holdings:</span>
                  <span className="font-mono font-medium text-text-primary">{comparison.fundHoldingsCount} stocks ({comparison.sumFundWeightsPct.toFixed(2)}%)</span>
                </div>
              </div>
            </div>
          </div>

          {/* Institutional Epistemic Demarcation */}
          <div className="p-4 rounded-lg bg-surface-elevated/40 border border-border/80 space-y-3">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-border/60 pb-2">
              <div className="flex items-center gap-2">
                <span className="badge badge-warning text-[10px] font-semibold tracking-wider">
                  {comparison.brinsonAttributionStatus}
                </span>
                <span className="text-xs font-semibold text-text-primary">
                  Constituent Return Attribution Readiness Gate
                </span>
              </div>
              <span className="mono-meta text-[10px] text-text-muted">
                Governance Standard: Phase 2H / AGENTS.md §7 &amp; §11
              </span>
            </div>

            <div className="text-xs leading-relaxed space-y-2 text-text-secondary">
              <p>
                <strong className="text-text-primary">Epistemic Demarcation:</strong> {comparison.investorOrientation}
              </p>
              <div className="p-2.5 rounded bg-warning-bg/10 border border-warning/30 text-warning-fg text-[11px] leading-relaxed">
                <strong className="text-warning font-semibold">Attribution Blocker:</strong> {comparison.brinsonBlockerReason}
              </div>
            </div>
          </div>

          {/* Active Security Positions Table */}
          <div className="panel p-5 space-y-4">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 border-b border-border/60 pb-3">
              <div>
                <h4 className="text-sm font-bold text-text-primary">
                  Active Security Positions (w<sub>p</sub> - w<sub>b</sub>)
                </h4>
                <p className="text-[11px] text-text-secondary">
                  Direct point-in-time comparison of fund holding weights against verified benchmark constituent weights.
                </p>
              </div>

              {/* Filter Pills */}
              <div className="flex flex-wrap items-center gap-1.5 text-[11px]">
                <button
                  onClick={() => setPositionFilter("ALL")}
                  className={`px-2.5 py-1 rounded transition-colors ${
                    positionFilter === "ALL"
                      ? "bg-primary text-primary-fg font-semibold"
                      : "bg-surface-elevated hover:bg-surface-elevated/80 text-text-secondary"
                  }`}
                >
                  All ({comparison.activePositions.length})
                </button>
                <button
                  onClick={() => setPositionFilter("OVERWEIGHT")}
                  className={`px-2.5 py-1 rounded transition-colors ${
                    positionFilter === "OVERWEIGHT"
                      ? "bg-success text-success-fg font-semibold"
                      : "bg-surface-elevated hover:bg-surface-elevated/80 text-text-secondary"
                  }`}
                >
                  Overweight
                </button>
                <button
                  onClick={() => setPositionFilter("UNDERWEIGHT")}
                  className={`px-2.5 py-1 rounded transition-colors ${
                    positionFilter === "UNDERWEIGHT"
                      ? "bg-warning text-warning-fg font-semibold"
                      : "bg-surface-elevated hover:bg-surface-elevated/80 text-text-secondary"
                  }`}
                >
                  Underweight
                </button>
                <button
                  onClick={() => setPositionFilter("BENCHMARK_ONLY")}
                  className={`px-2.5 py-1 rounded transition-colors ${
                    positionFilter === "BENCHMARK_ONLY"
                      ? "bg-error text-error-fg font-semibold"
                      : "bg-surface-elevated hover:bg-surface-elevated/80 text-text-secondary"
                  }`}
                >
                  Benchmark Not Held
                </button>
                <button
                  onClick={() => setPositionFilter("FUND_ONLY")}
                  className={`px-2.5 py-1 rounded transition-colors ${
                    positionFilter === "FUND_ONLY"
                      ? "bg-neutral text-neutral-fg font-semibold"
                      : "bg-surface-elevated hover:bg-surface-elevated/80 text-text-secondary"
                  }`}
                >
                  Fund Only
                </button>
                <button
                  onClick={() => setPositionFilter("UNWEIGHTED")}
                  className={`px-2.5 py-1 rounded transition-colors ${
                    positionFilter === "UNWEIGHTED"
                      ? "bg-amber-600 text-white font-semibold"
                      : "bg-surface-elevated hover:bg-surface-elevated/80 text-text-secondary"
                  }`}
                >
                  In BM (Unweighted Tail)
                </button>
              </div>
            </div>

            <div className="overflow-x-auto -mx-5 px-5">
              <table className="w-full text-left text-xs border-collapse">
                <thead>
                  <tr className="border-b border-border/60 text-text-secondary text-[11px] uppercase tracking-wider">
                    <th className="py-2 pr-3">Security Name</th>
                    <th className="py-2 px-2">ISIN</th>
                    <th className="py-2 px-2 text-right">Fund Weight</th>
                    <th className="py-2 px-2 text-right">Benchmark</th>
                    <th className="py-2 px-2 text-right">Active Spread (&Delta;w)</th>
                    <th className="py-2 px-2">Market Cap</th>
                    <th className="py-2 px-2">Industry</th>
                    <th className="py-2 pl-2 text-center">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/40 font-mono">
                  {filteredPositions.map((pos: ActiveSecurityPositionDto) => {
                    const isOverweight = pos.activeWeightPct > 0;
                    const isUnderweight = pos.activeWeightPct < 0;
                    const hasBenchmarkWeight = pos.benchmarkWeightPct != null;

                    return (
                      <tr key={pos.isin} className="hover:bg-surface-elevated/30 transition-colors">
                        <td className="py-2 pr-3 font-sans font-medium text-text-primary">
                          {pos.companyName}
                          {pos.symbol && (
                            <span className="block font-mono text-[10px] text-text-muted">{pos.symbol}</span>
                          )}
                        </td>
                        <td className="py-2 px-2 text-[10px] text-text-muted">{pos.isin}</td>
                        <td className="py-2 px-2 text-right font-medium text-text-primary">
                          {pos.fundWeightPct > 0 ? `${pos.fundWeightPct.toFixed(2)}%` : "—"}
                        </td>
                        <td className="py-2 px-2 text-right text-text-secondary">
                          {hasBenchmarkWeight ? `${pos.benchmarkWeightPct!.toFixed(2)}%` : (
                            <span className="text-[10px] italic text-text-muted">Unweighted Tail</span>
                          )}
                        </td>
                        <td className={`py-2 px-2 text-right font-bold ${
                          !hasBenchmarkWeight
                            ? "text-text-muted"
                            : isOverweight
                            ? "text-success"
                            : isUnderweight
                            ? "text-warning"
                            : "text-text-muted"
                        }`}>
                          {hasBenchmarkWeight ? (
                            pos.activeWeightPct > 0 ? `+${pos.activeWeightPct.toFixed(2)}%` : `${pos.activeWeightPct.toFixed(2)}%`
                          ) : (
                            "—"
                          )}
                        </td>
                        <td className="py-2 px-2 font-sans text-[11px]">
                          <span className={`badge text-[9px] ${
                            pos.marketCapCategory === "LARGE_CAP"
                              ? "badge-primary"
                              : pos.marketCapCategory === "MID_CAP"
                              ? "badge-warning"
                              : pos.marketCapCategory === "SMALL_CAP"
                              ? "badge-neutral"
                              : "badge-ghost"
                          }`}>
                            {pos.marketCapCategory}
                          </span>
                        </td>
                        <td className="py-2 px-2 font-sans text-[11px] text-text-secondary truncate max-w-[140px]" title={pos.industry}>
                          {pos.industry}
                        </td>
                        <td className="py-2 pl-2 text-center font-sans">
                          <span className={`badge text-[9px] font-semibold ${
                            pos.positionStatus === "OVERWEIGHT"
                              ? "badge-success"
                              : pos.positionStatus === "UNDERWEIGHT"
                              ? "badge-warning"
                              : pos.positionStatus === "BENCHMARK_ONLY"
                              ? "badge-error"
                              : pos.positionStatus === "IN_BENCHMARK_UNWEIGHTED"
                              ? "badge-neutral"
                              : "badge-ghost"
                          }`}>
                            {pos.positionStatus === "IN_BENCHMARK_UNWEIGHTED" ? "IN BM (UNWEIGHTED)" : pos.positionStatus}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>

          {/* Sector & Market-Cap Exposure Spreads */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Sector Exposure Spreads */}
            <div className="panel p-5 space-y-4">
              <div className="border-b border-border/60 pb-2">
                <h4 className="text-sm font-bold text-text-primary">
                  Sector Exposure Spreads (&Delta;w<sub>sector</sub>)
                </h4>
                <p className="text-[11px] text-text-secondary">
                  Macro industry allocation differences between portfolio and benchmark constituents.
                </p>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs border-collapse">
                  <thead>
                    <tr className="border-b border-border/60 text-text-secondary text-[11px] uppercase tracking-wider">
                      <th className="py-2 pr-2">Sector / Industry</th>
                      <th className="py-2 px-2 text-right">Fund</th>
                      <th className="py-2 px-2 text-right">Benchmark</th>
                      <th className="py-2 px-2 text-right">Spread (&Delta;w)</th>
                      <th className="py-2 pl-2 text-right">Stocks</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/40 font-mono">
                    {comparison.sectorExposures.map((sec) => (
                      <tr key={sec.sectorName} className="hover:bg-surface-elevated/30 transition-colors">
                        <td className="py-2 pr-2 font-sans font-medium text-text-primary truncate max-w-[150px]" title={sec.sectorName}>
                          {sec.sectorName}
                        </td>
                        <td className="py-2 px-2 text-right text-text-primary">
                          {sec.fundSectorWeightPct.toFixed(2)}%
                        </td>
                        <td className="py-2 px-2 text-right text-text-secondary">
                          {sec.benchmarkSectorWeightPct > 0 ? `${sec.benchmarkSectorWeightPct.toFixed(2)}%` : "—"}
                        </td>
                        <td className={`py-2 px-2 text-right font-bold ${
                          sec.activeSectorExposurePct > 0
                            ? "text-success"
                            : sec.activeSectorExposurePct < 0
                            ? "text-warning"
                            : "text-text-muted"
                        }`}>
                          {sec.activeSectorExposurePct > 0 ? `+${sec.activeSectorExposurePct.toFixed(2)}%` : `${sec.activeSectorExposurePct.toFixed(2)}%`}
                        </td>
                        <td className="py-2 pl-2 text-right text-[10px] text-text-muted">
                          {sec.fundStockCount} / {sec.benchmarkStockCount}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Market-Cap Exposure Comparison */}
            <div className="panel p-5 space-y-4">
              <div className="border-b border-border/60 pb-2">
                <h4 className="text-sm font-bold text-text-primary">
                  Market-Cap Tier Distribution
                </h4>
                <p className="text-[11px] text-text-secondary">
                  Point-in-Time AMFI-classified capitalization tiers comparing fund against benchmark.
                </p>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs border-collapse">
                  <thead>
                    <tr className="border-b border-border/60 text-text-secondary text-[11px] uppercase tracking-wider">
                      <th className="py-2 pr-2">Tier</th>
                      <th className="py-2 px-2 text-right">Fund Weight</th>
                      <th className="py-2 px-2 text-right">Benchmark Weight</th>
                      <th className="py-2 pl-2 text-right">Active Bias (&Delta;w)</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/40 font-mono">
                    {comparison.marketCapExposures.map((mcap) => (
                      <tr key={mcap.category} className="hover:bg-surface-elevated/30 transition-colors">
                        <td className="py-2 pr-2 font-sans font-medium text-text-primary">
                          <span className={`badge text-[10px] ${
                            mcap.category === "LARGE_CAP"
                              ? "badge-primary"
                              : mcap.category === "MID_CAP"
                              ? "badge-warning"
                              : mcap.category === "SMALL_CAP"
                              ? "badge-neutral"
                              : "badge-ghost"
                          }`}>
                            {mcap.category}
                          </span>
                        </td>
                        <td className="py-2 px-2 text-right font-medium text-text-primary">
                          {mcap.fundWeightPct.toFixed(2)}%
                        </td>
                        <td className="py-2 px-2 text-right text-text-secondary">
                          {mcap.benchmarkWeightPct > 0 ? `${mcap.benchmarkWeightPct.toFixed(2)}%` : "—"}
                        </td>
                        <td className={`py-2 pl-2 text-right font-bold ${
                          mcap.activeExposurePct > 0
                            ? "text-success"
                            : mcap.activeExposurePct < 0
                            ? "text-warning"
                            : "text-text-muted"
                        }`}>
                          {mcap.activeExposurePct > 0 ? `+${mcap.activeExposurePct.toFixed(2)}%` : `${mcap.activeExposurePct.toFixed(2)}%`}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {/* Due Diligence Prompts */}
              {comparison.dueDiligencePrompts && comparison.dueDiligencePrompts.length > 0 && (
                <div className="mt-4 pt-3 border-t border-border/60 space-y-2">
                  <span className="mono-meta text-[10px] text-text-muted uppercase tracking-wider">
                    Investigator Verification Prompts
                  </span>
                  <ul className="list-disc list-inside space-y-1 text-[11px] text-text-secondary">
                    {comparison.dueDiligencePrompts.map((prompt: string, idx: number) => (
                      <li key={idx} className="leading-relaxed">{prompt}</li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          </div>

          {/* Cryptographic Provenance */}
          <div className="panel p-4 space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-text-primary">
                Data Lineage &amp; Authoritative Source Provenance
              </span>
              <button
                onClick={() => setShowFullLineage(!showFullLineage)}
                className="text-[11px] text-primary hover:underline font-medium"
              >
                {showFullLineage ? "Hide Lineage Details" : "Inspect Cryptographic Evidence"}
              </button>
            </div>

            {showFullLineage && (
              <div className="p-3 rounded bg-surface-elevated/40 border border-border/60 space-y-2 text-[11px] font-mono text-text-secondary">
                <div>
                  <span className="text-text-muted">Benchmark Code:</span> {comparison.benchmarkCode} ({comparison.benchmarkName})
                </div>
                <div>
                  <span className="text-text-muted">Composition Date:</span> {comparison.benchmarkCompositionDate}
                </div>
                <div>
                  <span className="text-text-muted">Source URL:</span>{" "}
                  <a
                    href={comparison.sourceUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="text-primary hover:underline break-all"
                  >
                    {comparison.sourceUrl}
                  </a>
                </div>
                <div>
                  <span className="text-text-muted">Source Artifact SHA-256:</span>{" "}
                  <span className="break-all font-bold text-text-primary">{comparison.sourceArtifactSha256}</span>
                </div>
                <div>
                  <span className="text-text-muted">Knowledge Cutoff:</span> {comparison.knowledgeCutoff}
                </div>
                <div>
                  <span className="text-text-muted">Methodology Engine:</span> {comparison.methodologyVersion}
                </div>
              </div>
            )}

            <div className="p-3 rounded bg-surface-elevated/20 border border-border/50 text-[10px] leading-relaxed text-text-muted">
              <strong className="text-text-secondary">Official Licensing Notice:</strong> NIFTY 500 is an official trademark and commercial benchmark product of NSE Indices Limited. Official index membership and top weightings are published periodically by NSE Indices. Complete daily constituent weights across all 500 stocks represent proprietary commercial intellectual property. In compliance with YUKIRA Epistemic Directives (AGENTS.md), missing tail weights are never synthetically approximated or fabricated.
            </div>
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* VIEW 2: MULTI-PERIOD DIVERGENCE & THREE SOURCES OF APPARENT CHANGE */}
      {/* ========================================================================= */}
      {viewMode === "MULTI_PERIOD_DIVERGENCE" && (
        <div className="space-y-6">
          {divergenceLoading && (
            <div className="panel p-8 text-center space-y-3">
              <div className="skeleton h-6 w-1/4 mx-auto" />
              <div className="skeleton h-24 w-full" />
              <div className="skeleton h-48 w-full" />
            </div>
          )}

          {divergenceError && (
            <div className="panel p-6 text-center space-y-2 border-warning/50">
              <span className="text-warning text-xl">⚠️</span>
              <h4 className="text-sm font-bold text-text-primary">Divergence Calculation Notice</h4>
              <p className="text-xs text-text-secondary">{divergenceError}</p>
            </div>
          )}

          {divergenceData && !divergenceLoading && (
            <div className="space-y-6">
              {/* Divergence Header Summary */}
              <div className="panel p-5 space-y-3">
                <div className="flex flex-col md:flex-row md:items-center justify-between gap-2 border-b border-border/60 pb-3">
                  <div>
                    <h3 className="text-sm font-bold text-text-primary">
                      Multi-Period Disclosed Portfolio vs. Benchmark Divergence
                    </h3>
                    <p className="text-xs text-text-secondary mt-0.5">
                      Tracking holdings overlap, off-benchmark capital allocation, and separating manager rebalancing from index reconstitution.
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="badge badge-warning text-[10px] font-semibold">
                      {divergenceData.overallStatus}
                    </span>
                    <span className="font-mono text-[11px] text-text-muted">
                      {divergenceData.startDate} → {divergenceData.endDate}
                    </span>
                  </div>
                </div>

                <p className="text-xs text-text-secondary leading-relaxed bg-surface-elevated/30 p-3 rounded border border-border/50">
                  <strong className="text-text-primary">Epistemic Scope:</strong> {divergenceData.coverageExplanation}
                </p>
              </div>

              {/* Multi-Period Observations Table */}
              <div className="panel p-5 space-y-4">
                <h4 className="text-sm font-bold text-text-primary">
                  Chronological Disclosure Periods &amp; Benchmark Alignment
                </h4>

                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs border-collapse font-mono">
                    <thead>
                      <tr className="border-b border-border/60 text-text-secondary text-[11px] uppercase tracking-wider font-sans">
                        <th className="py-2.5 px-3">Fund Disclosure</th>
                        <th className="py-2.5 px-3">Benchmark Snapshot</th>
                        <th className="py-2.5 px-3 text-right">Timing Gap</th>
                        <th className="py-2.5 px-3 text-right">Holdings</th>
                        <th className="py-2.5 px-3 text-right">Overlap %</th>
                        <th className="py-2.5 px-3 text-right">Common Stocks</th>
                        <th className="py-2.5 px-3 text-right">Fund-Only Stocks</th>
                        <th className="py-2.5 px-3 text-right">Off-BM Capital</th>
                        <th className="py-2.5 px-3 text-right">Disclosed BM Wt</th>
                        <th className="py-2.5 pl-3 text-center">Status</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border/40">
                      {divergenceData.periods.map((p: PeriodDivergenceSummaryDto, idx: number) => {
                        const hasStaleness = !p.snapshotDateCompatible || p.snapshotDateDifferenceDays > 45;

                        return (
                          <tr key={idx} className="hover:bg-surface-elevated/30 transition-colors">
                            <td className="py-2.5 px-3 font-sans font-medium text-text-primary">{p.portfolioDate}</td>
                            <td className="py-2.5 px-3 text-text-secondary">{p.benchmarkDate}</td>
                            <td className="py-2.5 px-3 text-right">
                              {p.snapshotDateDifferenceDays === 0 ? (
                                <span className="text-success font-medium">0 days</span>
                              ) : (
                                <span className={hasStaleness ? "text-warning font-bold" : "text-text-secondary"}>
                                  {p.snapshotDateDifferenceDays} days
                                </span>
                              )}
                            </td>
                            <td className="py-2.5 px-3 text-right">{p.fundHoldingsCount}</td>
                            <td className="py-2.5 px-3 text-right font-bold text-primary">
                              {p.overlapPct.toFixed(2)}%
                            </td>
                            <td className="py-2.5 px-3 text-right">{p.commonCount}</td>
                            <td className="py-2.5 px-3 text-right text-warning">{p.fundOnlyCount}</td>
                            <td className="py-2.5 px-3 text-right font-bold text-warning-fg">
                              {p.offBenchmarkCapitalAllocationPct.toFixed(2)}%
                            </td>
                            <td className="py-2.5 px-3 text-right text-text-secondary">
                              {p.benchmarkDisclosedWeightPct.toFixed(2)}%
                            </td>
                            <td className="py-2.5 pl-3 text-center font-sans">
                              <span className={`badge text-[9px] font-semibold ${
                                p.periodStatus === "VERIFIED_COMPLETE"
                                  ? "badge-success"
                                  : p.periodStatus === "VERIFIED_PARTIAL"
                                  ? "badge-primary"
                                  : p.periodStatus === "UNVERIFIED_SOURCE"
                                  ? "badge-warning"
                                  : "badge-neutral"
                              }`}>
                                {p.periodStatus}
                              </span>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>

                {divergenceData.periods.some((p) => p.warningMessage) && (
                  <div className="p-3 rounded bg-warning-bg/15 border border-warning/40 text-warning-fg text-xs space-y-1">
                    <strong className="font-semibold">Point-in-Time Timing Warnings:</strong>
                    <ul className="list-disc list-inside space-y-0.5 text-[11px]">
                      {divergenceData.periods
                        .filter((p) => p.warningMessage)
                        .map((p, i) => (
                          <li key={i}>{p.warningMessage}</li>
                        ))}
                    </ul>
                  </div>
                )}
              </div>

              {/* THE THREE SOURCES OF APPARENT CHANGE */}
              <div className="panel p-5 space-y-4 border-l-4 border-l-accent">
                <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 border-b border-border/60 pb-3">
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="badge badge-accent text-[10px] font-semibold">METHODOLOGY DISCIPLINE</span>
                      <h4 className="text-sm font-bold text-text-primary">
                        Separation of Three Sources of Apparent Change
                      </h4>
                    </div>
                    <p className="text-xs text-text-secondary mt-1">
                      Distinguishing genuine portfolio rebalancing from index constituent reconstitution and classification updates.
                    </p>
                  </div>

                  {/* Filter Pills */}
                  <div className="flex flex-wrap items-center gap-1.5 text-[11px]">
                    <button
                      onClick={() => setApparentChangeFilter("ALL")}
                      className={`px-2.5 py-1 rounded transition-colors ${
                        apparentChangeFilter === "ALL"
                          ? "bg-primary text-primary-fg font-semibold"
                          : "bg-surface-elevated hover:bg-surface-elevated/80 text-text-secondary"
                      }`}
                    >
                      All ({divergenceData.apparentChanges.length})
                    </button>
                    <button
                      onClick={() => setApparentChangeFilter("FUND_PORTFOLIO_CHANGE")}
                      className={`px-2.5 py-1 rounded transition-colors ${
                        apparentChangeFilter === "FUND_PORTFOLIO_CHANGE"
                          ? "bg-blue-600 text-white font-semibold"
                          : "bg-surface-elevated hover:bg-surface-elevated/80 text-text-secondary"
                      }`}
                    >
                      Fund Changes
                    </button>
                    <button
                      onClick={() => setApparentChangeFilter("BENCHMARK_MEMBERSHIP_CHANGE")}
                      className={`px-2.5 py-1 rounded transition-colors ${
                        apparentChangeFilter === "BENCHMARK_MEMBERSHIP_CHANGE"
                          ? "bg-purple-600 text-white font-semibold"
                          : "bg-surface-elevated hover:bg-surface-elevated/80 text-text-secondary"
                      }`}
                    >
                      Benchmark Changes
                    </button>
                    <button
                      onClick={() => setApparentChangeFilter("CLASSIFICATION_COVERAGE_CHANGE")}
                      className={`px-2.5 py-1 rounded transition-colors ${
                        apparentChangeFilter === "CLASSIFICATION_COVERAGE_CHANGE"
                          ? "bg-amber-600 text-white font-semibold"
                          : "bg-surface-elevated hover:bg-surface-elevated/80 text-text-secondary"
                      }`}
                    >
                      Classification Changes
                    </button>
                  </div>
                </div>

                {filteredApparentChanges.length === 0 ? (
                  <div className="p-4 rounded bg-surface text-center text-xs text-text-muted">
                    No apparent changes detected matching the selected filter.
                  </div>
                ) : (
                  <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3.5">
                    {filteredApparentChanges.map((change: ApparentChangeBreakdownDto, idx: number) => {
                      const isFund = change.changeCategory === "FUND_PORTFOLIO_CHANGE";
                      const isBm = change.changeCategory === "BENCHMARK_MEMBERSHIP_CHANGE";
                      const isClass = change.changeCategory === "CLASSIFICATION_COVERAGE_CHANGE";

                      return (
                        <div
                          key={idx}
                          className="p-4 rounded-lg bg-surface-elevated/40 border border-border/80 space-y-2.5 hover:border-border transition-colors"
                        >
                          <div className="flex items-center justify-between">
                            <span className={`badge text-[9px] font-semibold ${
                              isFund
                                ? "bg-blue-600/20 text-blue-400 border border-blue-500/30"
                                : isBm
                                ? "bg-purple-600/20 text-purple-400 border border-purple-500/30"
                                : "bg-amber-600/20 text-amber-400 border border-amber-500/30"
                            }`}>
                              {isFund ? "1. FUND PORTFOLIO" : isBm ? "2. BENCHMARK MEMBERSHIP" : "3. CLASSIFICATION / COVERAGE"}
                            </span>
                            {change.impactWeightPct != null && (
                              <span className="mono-num text-xs font-bold text-text-primary">
                                &Delta;w: {change.impactWeightPct > 0 ? `+${change.impactWeightPct.toFixed(2)}%` : `${change.impactWeightPct.toFixed(2)}%`}
                              </span>
                            )}
                          </div>

                          <div>
                            <h5 className="text-xs font-bold text-text-primary">{change.securityName}</h5>
                            {change.isin && <span className="font-mono text-[10px] text-text-muted">{change.isin}</span>}
                          </div>

                          <div className="p-2 rounded bg-surface/60 border border-border/40 text-[11px] space-y-1">
                            <div className="text-text-secondary flex justify-between">
                              <span className="text-text-muted">Earlier State:</span>
                              <span className="font-medium text-text-primary">{change.earlierState}</span>
                            </div>
                            <div className="text-text-secondary flex justify-between">
                              <span className="text-text-muted">Later State:</span>
                              <span className="font-medium text-text-primary">{change.laterState}</span>
                            </div>
                          </div>

                          <p className="text-xs text-text-primary leading-relaxed">{change.description}</p>

                          <div className="text-[10px] text-text-muted italic bg-surface-elevated/20 p-2 rounded border border-border/30">
                            {change.epistemicNote}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>

              {/* Investigator Due Diligence Prompts */}
              {divergenceData.dueDiligencePrompts && divergenceData.dueDiligencePrompts.length > 0 && (
                <div className="panel p-4 space-y-2">
                  <span className="mono-meta text-[10px] text-text-muted uppercase tracking-wider">
                    Due Diligence Verification Questions
                  </span>
                  <ul className="list-disc list-inside space-y-1 text-xs text-text-secondary">
                    {divergenceData.dueDiligencePrompts.map((prompt: string, idx: number) => (
                      <li key={idx} className="leading-relaxed">{prompt}</li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* ========================================================================= */}
      {/* VIEW 3: BENCHMARK INDEX RECONSTITUTION & TURNOVER */}
      {/* ========================================================================= */}
      {viewMode === "INDEX_RECONSTITUTION" && (
        <div className="space-y-6">
          {reconstitutionLoading && (
            <div className="panel p-8 text-center space-y-3">
              <div className="skeleton h-6 w-1/4 mx-auto" />
              <div className="skeleton h-24 w-full" />
              <div className="skeleton h-48 w-full" />
            </div>
          )}

          {reconstitutionError && (
            <div className="panel p-6 text-center space-y-2 border-warning/50">
              <span className="text-warning text-xl">⚠️</span>
              <h4 className="text-sm font-bold text-text-primary">Reconstitution Analysis Unavailable</h4>
              <p className="text-xs text-text-secondary">{reconstitutionError}</p>
            </div>
          )}

          {reconstitutionData && !reconstitutionLoading && (
            <div className="space-y-6">
              {/* Header Metric Cards */}
              <div className="grid grid-cols-2 md:grid-cols-5 gap-3.5">
                <div className="panel p-3.5 space-y-1 border-l-4 border-l-primary">
                  <span className="mono-meta text-[10px] text-text-muted uppercase">Interval</span>
                  <div className="text-sm font-bold text-text-primary truncate">
                    {reconstitutionData.intervalDays} Days
                  </div>
                  <span className="mono-meta text-[9px] text-text-muted block truncate">
                    {reconstitutionData.earlierDate} → {reconstitutionData.laterDate}
                  </span>
                </div>

                <div className="panel p-3.5 space-y-1">
                  <span className="mono-meta text-[10px] text-text-muted uppercase">Turnover Rate</span>
                  <div className="text-xl font-bold font-mono text-text-primary">
                    {reconstitutionData.turnoverPct.toFixed(1)}%
                  </div>
                  <span className="mono-meta text-[9px] text-text-muted block">Two-Point Transition</span>
                </div>

                <div className="panel p-3.5 space-y-1">
                  <span className="mono-meta text-[10px] text-text-muted uppercase">Retained Stocks</span>
                  <div className="text-xl font-bold font-mono text-primary">
                    {reconstitutionData.retainedCount}
                  </div>
                  <span className="mono-meta text-[9px] text-text-muted block">Persistent Members</span>
                </div>

                <div className="panel p-3.5 space-y-1">
                  <span className="mono-meta text-[10px] text-text-muted uppercase">Net Entered</span>
                  <div className="text-xl font-bold font-mono text-success">
                    +{reconstitutionData.enteredCount}
                  </div>
                  <span className="mono-meta text-[9px] text-text-muted block">New Constituents</span>
                </div>

                <div className="panel p-3.5 space-y-1">
                  <span className="mono-meta text-[10px] text-text-muted uppercase">Net Exited</span>
                  <div className="text-xl font-bold font-mono text-error">
                    -{reconstitutionData.exitedCount}
                  </div>
                  <span className="mono-meta text-[9px] text-text-muted block">Removed Constituents</span>
                </div>
              </div>

              {/* Two-Point Transition Governance Notice */}
              <div className="p-4 rounded-lg bg-surface-elevated/40 border border-border/80 space-y-2">
                <div className="flex items-center gap-2">
                  <span className="badge badge-warning text-[10px] font-semibold">
                    {reconstitutionData.provenanceStatus}
                  </span>
                  <span className="text-xs font-bold text-text-primary">
                    Archival Provenance Notice
                  </span>
                </div>
                <p className="text-xs text-text-secondary leading-relaxed">
                  {reconstitutionData.investorNotice}
                </p>
              </div>

              {/* Reconstitution Search & Filter Table */}
              <div className="panel p-5 space-y-4">
                <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 border-b border-border/60 pb-3">
                  <div>
                    <h4 className="text-sm font-bold text-text-primary">
                      Constituent Ledger by Status
                    </h4>
                    <p className="text-xs text-text-secondary mt-0.5">
                      Canonical identifier matching preserves continuity across stock splits and corporate actions.
                    </p>
                  </div>

                  <div className="flex flex-wrap items-center gap-2">
                    <input
                      type="text"
                      placeholder="Search constituent or ISIN..."
                      value={reconstitutionSearch}
                      onChange={(e) => setReconstitutionSearch(e.target.value)}
                      className="px-2.5 py-1 text-xs rounded bg-surface border border-border/60 text-text-primary placeholder:text-text-muted w-48 focus:outline-none focus:border-primary"
                    />

                    {/* Filter Pills */}
                    <div className="flex items-center gap-1 text-[11px]">
                      <button
                        onClick={() => setReconstitutionFilter("ENTERED")}
                        className={`px-2.5 py-1 rounded transition-colors ${
                          reconstitutionFilter === "ENTERED"
                            ? "bg-success text-success-fg font-semibold"
                            : "bg-surface-elevated hover:bg-surface-elevated/80 text-text-secondary"
                        }`}
                      >
                        Entered ({reconstitutionData.enteredCount})
                      </button>
                      <button
                        onClick={() => setReconstitutionFilter("EXITED")}
                        className={`px-2.5 py-1 rounded transition-colors ${
                          reconstitutionFilter === "EXITED"
                            ? "bg-error text-error-fg font-semibold"
                            : "bg-surface-elevated hover:bg-surface-elevated/80 text-text-secondary"
                        }`}
                      >
                        Exited ({reconstitutionData.exitedCount})
                      </button>
                      <button
                        onClick={() => setReconstitutionFilter("RETAINED")}
                        className={`px-2.5 py-1 rounded transition-colors ${
                          reconstitutionFilter === "RETAINED"
                            ? "bg-primary text-primary-fg font-semibold"
                            : "bg-surface-elevated hover:bg-surface-elevated/80 text-text-secondary"
                        }`}
                      >
                        Retained ({reconstitutionData.retainedCount})
                      </button>
                    </div>
                  </div>
                </div>

                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs border-collapse font-mono">
                    <thead>
                      <tr className="border-b border-border/60 text-text-secondary text-[11px] uppercase tracking-wider font-sans">
                        <th className="py-2.5 px-3">Company / Security</th>
                        <th className="py-2.5 px-3">Symbol</th>
                        <th className="py-2.5 px-3">ISIN</th>
                        <th className="py-2.5 px-3">Industry / Sector</th>
                        <th className="py-2.5 px-3 text-right">Observation Date</th>
                        <th className="py-2.5 px-3 text-center">Status</th>
                        <th className="py-2.5 pl-3">Notes &amp; Lineage</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border/40">
                      {filteredReconstitutionList.slice(0, 100).map((member: ReconstitutionMemberDto, idx: number) => {
                        const isEntered = member.changeType === "ENTERED";
                        const isExited = member.changeType === "EXITED";

                        return (
                          <tr key={idx} className="hover:bg-surface-elevated/30 transition-colors">
                            <td className="py-2 px-3 font-sans font-medium text-text-primary">
                              {member.companyName}
                            </td>
                            <td className="py-2 px-3 text-text-secondary">{member.symbol || "—"}</td>
                            <td className="py-2 px-3 text-[11px] text-text-muted">{member.isin}</td>
                            <td className="py-2 px-3 font-sans text-text-secondary truncate max-w-[150px]">
                              {member.industry || "—"}
                            </td>
                            <td className="py-2 px-3 text-right text-text-secondary">{member.effectiveDate}</td>
                            <td className="py-2 px-3 text-center font-sans">
                              <span className={`badge text-[9px] font-semibold ${
                                isEntered ? "badge-success" : isExited ? "badge-error" : "badge-neutral"
                              }`}>
                                {member.changeType}
                              </span>
                            </td>
                            <td className="py-2 pl-3 font-sans text-[11px] text-text-muted">
                              {member.notes || "Canonical Security Matched"}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>

                {filteredReconstitutionList.length > 100 && (
                  <div className="p-2 text-center text-xs text-text-muted border-t border-border/40">
                    Showing top 100 constituents out of {filteredReconstitutionList.length}. Use the search bar above to locate specific securities.
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      )}

      {/* ========================================================================= */}
      {/* VIEW 4: BRINSON ATTRIBUTION READINESS GATE & DATA DEPENDENCIES */}
      {/* ========================================================================= */}
      {viewMode === "BRINSON_MANIFEST" && (
        <div className="space-y-6">
          <div className="p-5 rounded-lg bg-surface-elevated/40 border border-warning/50 space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-border/60 pb-3">
              <div className="flex items-center gap-2.5">
                <span className="badge badge-warning text-xs font-bold tracking-wider">
                  BLOCKED_PENDING_STOCK_RETURNS
                </span>
                <h3 className="text-sm font-bold text-text-primary">
                  Brinson-Fachler Multi-Period Attribution Readiness Gate
                </h3>
              </div>
              <span className="mono-meta text-[11px] text-text-muted">
                Reference: docs/006-validation/brinson_data_dependency_manifest.md
              </span>
            </div>

            <p className="text-xs text-text-secondary leading-relaxed">
              Constituent-level Brinson-Fachler return attribution decomposes active portfolio returns into Allocation, Selection, and Interaction components:
            </p>

            <div className="p-3 rounded bg-surface font-mono text-xs text-text-primary border border-border/60 overflow-x-auto">
              R<sub>p</sub> - R<sub>b</sub> = &sum; (w<sub>p,j</sub> - w<sub>b,j</sub>)(R<sub>b,j</sub> - R<sub>b</sub>) + &sum; w<sub>b,j</sub>(R<sub>p,j</sub> - R<sub>b,j</sub>) + &sum; (w<sub>p,j</sub> - w<sub>b,j</sub>)(R<sub>p,j</sub> - R<sub>b,j</sub>)
            </div>

            <p className="text-xs text-warning-fg leading-relaxed bg-warning-bg/10 p-3 rounded border border-warning/30">
              <strong>YUKIRA Epistemic Guardrail:</strong> In compliance with AGENTS.md §6 (&quot;The Deterministic Financial Calculation Rule&quot;) and §7 (&quot;Strict AI Boundary&quot;), YUKIRA strictly prohibits approximating or simulating missing individual equity returns. Benchmark composition diagnostics (measuring weights and overlap) are fully operational, but return attribution is gated until genuine corporate-action-adjusted equity price series are licensed.
            </p>
          </div>

          {/* Dependency Matrix Table */}
          <div className="panel p-5 space-y-4">
            <h4 className="text-sm font-bold text-text-primary">
              Attribution Data Prerequisites &amp; Readiness Checklist
            </h4>

            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs border-collapse">
                <thead>
                  <tr className="border-b border-border/60 text-text-secondary text-[11px] uppercase tracking-wider">
                    <th className="py-2.5 px-3">Dependency ID</th>
                    <th className="py-2.5 px-3">Data Requirement</th>
                    <th className="py-2.5 px-3">Grain &amp; Scope</th>
                    <th className="py-2.5 px-3 text-center">Status in YUKIRA</th>
                    <th className="py-2.5 pl-3">Commercial Provider / Actionable Route</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/40 font-mono">
                  <tr className="hover:bg-surface-elevated/30">
                    <td className="py-2.5 px-3 font-bold text-text-primary">DEP-01</td>
                    <td className="py-2.5 px-3 font-sans text-text-primary">Disclosed Portfolio Holdings</td>
                    <td className="py-2.5 px-3 text-text-secondary">Monthly Point-in-Time equity weights</td>
                    <td className="py-2.5 px-3 text-center font-sans">
                      <span className="badge badge-success text-[10px]">PRESENT &amp; VERIFIED</span>
                    </td>
                    <td className="py-2.5 pl-3 font-sans text-text-secondary text-[11px]">
                      AMFI / AMC Direct Ingestion Pipeline
                    </td>
                  </tr>

                  <tr className="hover:bg-surface-elevated/30">
                    <td className="py-2.5 px-3 font-bold text-text-primary">DEP-02</td>
                    <td className="py-2.5 px-3 font-sans text-text-primary">AMFI Market-Cap Classifications</td>
                    <td className="py-2.5 px-3 text-text-secondary">Semi-annual official rankings (V5)</td>
                    <td className="py-2.5 px-3 text-center font-sans">
                      <span className="badge badge-success text-[10px]">PRESENT &amp; VERIFIED</span>
                    </td>
                    <td className="py-2.5 pl-3 font-sans text-text-secondary text-[11px]">
                      AMFI India Semi-Annual Portal Ingestion
                    </td>
                  </tr>

                  <tr className="hover:bg-surface-elevated/30">
                    <td className="py-2.5 px-3 font-bold text-text-primary">DEP-03</td>
                    <td className="py-2.5 px-3 font-sans text-text-primary">Benchmark Index Total Returns</td>
                    <td className="py-2.5 px-3 text-text-secondary">Daily TRI NAV / Level series</td>
                    <td className="py-2.5 px-3 text-center font-sans">
                      <span className="badge badge-success text-[10px]">PRESENT &amp; VERIFIED</span>
                    </td>
                    <td className="py-2.5 pl-3 font-sans text-text-secondary text-[11px]">
                      NSE Indices TRI Historical Ingestion
                    </td>
                  </tr>

                  <tr className="hover:bg-surface-elevated/30">
                    <td className="py-2.5 px-3 font-bold text-text-primary">DEP-04</td>
                    <td className="py-2.5 px-3 font-sans text-text-primary">Benchmark Constituent Membership</td>
                    <td className="py-2.5 px-3 text-text-secondary">500 constituents with ISINs</td>
                    <td className="py-2.5 px-3 text-center font-sans">
                      <span className="badge badge-success text-[10px]">PRESENT &amp; VERIFIED</span>
                    </td>
                    <td className="py-2.5 pl-3 font-sans text-text-secondary text-[11px]">
                      NSE Indices Archival &amp; Live Ingestion
                    </td>
                  </tr>

                  <tr className="hover:bg-surface-elevated/30">
                    <td className="py-2.5 px-3 font-bold text-warning-fg">DEP-05</td>
                    <td className="py-2.5 px-3 font-sans text-text-primary">Complete Benchmark Weights</td>
                    <td className="py-2.5 px-3 text-text-secondary">All 500 constituents weighted (sum=100%)</td>
                    <td className="py-2.5 px-3 text-center font-sans">
                      <span className="badge badge-warning text-[10px]">PARTIAL (TOP 10 ONLY)</span>
                    </td>
                    <td className="py-2.5 pl-3 font-sans text-text-secondary text-[11px]">
                      Requires NSE Indices Commercial Data Subscription
                    </td>
                  </tr>

                  <tr className="hover:bg-surface-elevated/30">
                    <td className="py-2.5 px-3 font-bold text-error">DEP-06</td>
                    <td className="py-2.5 px-3 font-sans text-text-primary">Adjusted Stock Return Series</td>
                    <td className="py-2.5 px-3 text-text-secondary">Daily corporate-action-adjusted prices</td>
                    <td className="py-2.5 px-3 text-center font-sans">
                      <span className="badge badge-error text-[10px]">ABSENT (BLOCKER)</span>
                    </td>
                    <td className="py-2.5 pl-3 font-sans text-text-secondary text-[11px]">
                      NSE Data &amp; Analytics (Infotech) / CRISIL EOD Feed
                    </td>
                  </tr>

                  <tr className="hover:bg-surface-elevated/30">
                    <td className="py-2.5 px-3 font-bold text-warning-fg">DEP-07</td>
                    <td className="py-2.5 px-3 font-sans text-text-primary">Corporate Action Adjustment Ledger</td>
                    <td className="py-2.5 px-3 text-text-secondary">Dividends, bonus, splits, rights</td>
                    <td className="py-2.5 px-3 text-center font-sans">
                      <span className="badge badge-warning text-[10px]">PARTIAL (SPLITS MAPPED)</span>
                    </td>
                    <td className="py-2.5 pl-3 font-sans text-text-secondary text-[11px]">
                      NSE / BSE Corporate Actions Feed
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* VIEW 5: ACTIVE SHARE & OFF-BENCHMARK ALLOCATION TRAJECTORY */}
      {/* ========================================================================= */}
      {viewMode === "ACTIVE_SHARE_TRAJECTORY" && resolvedSchemeOptionId && (
        <ActiveShareTrajectoryView
          schemeOptionId={resolvedSchemeOptionId}
          benchmarkCode={comparison.benchmarkCode}
          effectiveAsOfDate={effectiveAsOfDate}
          effectiveCutoffTime={effectiveCutoffTime}
          onRefresh={onRefresh}
        />
      )}

      {/* ========================================================================= */}
      {/* VIEW 6: POINT-IN-TIME SECTOR & INDUSTRY EXPOSURE DRIFT */}
      {/* ========================================================================= */}
      {viewMode === "SECTOR_DRIFT" && resolvedSchemeOptionId && (
        <SectorDriftView
          schemeOptionId={resolvedSchemeOptionId}
          effectiveAsOfDate={effectiveAsOfDate}
          effectiveCutoffTime={effectiveCutoffTime}
          onRefresh={onRefresh}
        />
      )}
    </div>
  );
}
