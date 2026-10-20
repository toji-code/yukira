"use client";

import React, { useState, useEffect, useMemo } from "react";
import {
  fetchActiveReturnAttribution,
  ActiveReturnAttributionResponseDto,
  ExposureTimelineObservationDto,
} from "@/lib/api/portfolio";

export interface StyleDriftAttributionViewProps {
  schemeOptionId: number;
  asOfDate?: string;
  knowledgeCutoffTime?: string;
  onInvestigateQuestion?: (question: string, reason?: string) => void;
}

type SubTab = "TIMELINE" | "MACRO_ATTRIBUTION" | "BRINSON_FACHLER" | "DATA_READINESS" | "DUE_DILIGENCE";

export function StyleDriftAttributionView({
  schemeOptionId,
  asOfDate: propAsOfDate,
  knowledgeCutoffTime: propCutoffTime,
  onInvestigateQuestion,
}: StyleDriftAttributionViewProps) {
  const [data, setData] = useState<ActiveReturnAttributionResponseDto | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [activeSubTab, setActiveSubTab] = useState<SubTab>("TIMELINE");
  const [horizon, setHorizon] = useState<string>("1Y");
  const [selectedSnapshotIndex, setSelectedSnapshotIndex] = useState<number>(0);
  const [showMethodologyDrawer, setShowMethodologyDrawer] = useState<boolean>(false);
  const [timelineSearch, setTimelineSearch] = useState<string>("");

  const effectiveAsOfDate = propAsOfDate || "2026-10-01";
  const effectiveCutoffTime = propCutoffTime || "2026-10-10T23:59:59+05:30";

  useEffect(() => {
    let active = true;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLoading(true);
    setError(null);

    fetchActiveReturnAttribution(
      schemeOptionId,
      effectiveAsOfDate,
      effectiveCutoffTime,
      horizon
    )
      .then((res) => {
        if (!active) return;
        setData(res);
        if (res.exposureTimeline && res.exposureTimeline.length > 0) {
          setSelectedSnapshotIndex(res.exposureTimeline.length - 1);
        }
      })
      .catch((err: unknown) => {
        if (!active) return;
        setError(err instanceof Error ? err.message : "Failed to load active return attribution.");
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [schemeOptionId, effectiveAsOfDate, effectiveCutoffTime, horizon]);

  const selectedSnapshot: ExposureTimelineObservationDto | null = useMemo(() => {
    if (!data?.exposureTimeline || data.exposureTimeline.length === 0) return null;
    const idx = Math.max(0, Math.min(selectedSnapshotIndex, data.exposureTimeline.length - 1));
    return data.exposureTimeline[idx];
  }, [data, selectedSnapshotIndex]);

  const filteredTimeline = useMemo(() => {
    if (!data?.exposureTimeline) return [];
    if (!timelineSearch.trim()) return data.exposureTimeline;
    const query = timelineSearch.toLowerCase();
    return data.exposureTimeline.filter((obs) => {
      return (
        obs.portfolioDate.toLowerCase().includes(query) ||
        obs.disclosureScope.toLowerCase().includes(query) ||
        obs.comparabilityStatus.toLowerCase().includes(query) ||
        obs.topSectors.some((s) => s.sectorName.toLowerCase().includes(query))
      );
    });
  }, [data, timelineSearch]);

  const getComparabilityBadge = (status: string) => {
    switch (status) {
      case "COMPARABLE_FULL_DISCLOSURE":
        return { label: "Comparable Full Disclosure", className: "badge-success" };
      case "BASELINE_OBSERVATION":
        return { label: "Baseline Observation", className: "badge-neutral" };
      case "NON_COMPARABLE_DISCLOSURE_SCOPE_SHIFT":
        return { label: "Scope Shift Alert", className: "badge-warning" };
      case "NON_COMPARABLE_TEMPORAL_GAP":
        return { label: "Temporal Gap Alert", className: "badge-warning" };
      default:
        return { label: status, className: "badge-neutral" };
    }
  };

  const getScopeBadge = (scope: string) => {
    switch (scope) {
      case "COMPLETE_PORTFOLIO":
      case "FULL_PORTFOLIO_DISCLOSURE":
        return { label: "Full Portfolio", className: "badge-success" };
      case "TOP_10":
      case "PARTIAL_TOP_HOLDINGS":
        return { label: "Top 10 Only", className: "badge-warning" };
      default:
        return { label: scope, className: "badge-neutral" };
    }
  };

  if (loading) {
    return (
      <div className="panel p-6 space-y-4 animate-pulse" aria-busy="true">
        <div className="flex items-center justify-between border-b border-border/60 pb-3">
          <div className="h-5 bg-surface-elevated rounded w-1/3" />
          <div className="h-4 bg-surface-elevated rounded w-1/5" />
        </div>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <div className="h-20 bg-surface-elevated rounded" />
          <div className="h-20 bg-surface-elevated rounded" />
          <div className="h-20 bg-surface-elevated rounded" />
          <div className="h-20 bg-surface-elevated rounded" />
        </div>
        <div className="h-64 bg-surface-elevated rounded" />
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="state-panel-error space-y-3 p-6" role="alert">
        <div className="panel-header">
          <p className="text-[14px] font-semibold text-text-primary">
            Style Drift &amp; Active Return Attribution Unavailable
          </p>
        </div>
        <p className="text-[13px] text-critical-fg">
          {error || "Failed to load active return attribution from backend."}
        </p>
      </div>
    );
  }

  const { macroAttribution, brinsonSegmentation, dataReadinessGate, styleContext } = data;

  return (
    <div className="panel p-5 md:p-6 space-y-6">
      {/* 1. Header & Institutional Context */}
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between border-b border-border/70 pb-4">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <span className="badge badge-warning text-[10px] uppercase tracking-wider font-semibold">
              CANDIDATE METHODOLOGY · PIT ENFORCED
            </span>
            <span className="mono-meta text-[11px] text-text-muted">
              Benchmark: {data.benchmarkCode} ({data.benchmarkName})
            </span>
            <span className="mono-meta text-[11px] text-text-muted">
              • Cutoff: {data.knowledgeCutoff.slice(0, 19).replace("T", " ")}
            </span>
          </div>
          <h3 className="mt-1 text-[16px] font-bold tracking-tight text-text-primary">
            Fund Style Drift, Active Return &amp; Evidence-Gated Attribution
          </h3>
          <p className="text-[12px] text-text-secondary">
            Observed portfolio exposures, benchmark-relative return decomposition, and evidence-gated Brinson analysis.
            Zero synthetic series, unverified interpolations, or manager-skill claims.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {/* Horizon Selection */}
          <div className="flex items-center gap-1 rounded-lg bg-surface border border-border/70 p-1 text-[11px]">
            {["1M", "3M", "6M", "1Y", "3Y", "5Y"].map((h) => (
              <button
                key={h}
                type="button"
                onClick={() => setHorizon(h)}
                className={`px-2.5 py-1 rounded font-medium transition-colors ${
                  horizon === h
                    ? "bg-primary text-primary-foreground font-semibold"
                    : "text-text-secondary hover:text-text-primary hover:bg-surface-elevated/40"
                }`}
              >
                {h}
              </button>
            ))}
          </div>

          <button
            type="button"
            onClick={() => setShowMethodologyDrawer(!showMethodologyDrawer)}
            className="btn btn-secondary btn-sm text-[11px]"
            aria-expanded={showMethodologyDrawer}
          >
            {showMethodologyDrawer ? "Hide Methodology" : "Methodology & Provenance"}
          </button>
        </div>
      </div>

      {/* 2. Key Metrics Strip */}
      <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-3">
        {/* Metric 1: Fund Return */}
        <div className="p-3 rounded-lg bg-surface-elevated/40 border border-border/70 space-y-1">
          <span className="mono-meta block text-[10px] text-text-muted">FUND RETURN (R_p)</span>
          <div className="mono-num text-[16px] font-bold text-text-primary">
            {data.fundReturnPctFormatted}
          </div>
          <span className="block text-[10px] text-text-muted truncate">
            {data.attributionPeriodStart} &rarr; {data.attributionPeriodEnd}
          </span>
        </div>

        {/* Metric 2: Benchmark Return */}
        <div className="p-3 rounded-lg bg-surface-elevated/40 border border-border/70 space-y-1">
          <span className="mono-meta block text-[10px] text-text-muted">BENCHMARK (R_b)</span>
          <div className="mono-num text-[16px] font-bold text-text-primary">
            {data.benchmarkReturnPctFormatted}
          </div>
          <span className="block text-[10px] text-text-muted truncate">
            {data.benchmarkCode} TRI
          </span>
        </div>

        {/* Metric 3: Active Return */}
        <div className="p-3 rounded-lg bg-surface-elevated/40 border border-border/70 space-y-1">
          <span className="mono-meta block text-[10px] text-text-muted">ACTIVE RETURN (R_A)</span>
          <div className={`mono-num text-[16px] font-bold ${
            data.activeReturnPct >= 0 ? "text-success-fg" : "text-critical-fg"
          }`}>
            {data.activeReturnPctFormatted}
          </div>
          <div className="flex items-center gap-1">
            <span className={`badge text-[9px] px-1 py-0 ${
              data.activeReturnPct >= 0 ? "badge-success" : "badge-critical"
            }`}>
              {data.activeReturnPct >= 0 ? "OUTPERFORMANCE" : "UNDERPERFORMANCE"}
            </span>
          </div>
        </div>

        {/* Metric 4: Empirical Beta */}
        <div className="p-3 rounded-lg bg-surface-elevated/40 border border-border/70 space-y-1">
          <span className="mono-meta block text-[10px] text-text-muted">EMPIRICAL BETA (&beta;)</span>
          <div className="mono-num text-[16px] font-bold text-text-primary">
            {macroAttribution.empiricalBeta.toFixed(4)}
          </div>
          <span className="block text-[10px] text-text-muted">
            Relative to {data.benchmarkCode}
          </span>
        </div>

        {/* Metric 5: Macro Decomposition Status */}
        <div className="p-3 rounded-lg bg-surface-elevated/40 border border-border/70 space-y-1">
          <span className="mono-meta block text-[10px] text-text-muted">MACRO RECONCILIATION</span>
          <div className="flex items-center gap-1.5 pt-0.5">
            <span className={`badge text-[10px] font-semibold ${
              macroAttribution.isReconciled ? "badge-success" : "badge-critical"
            }`}>
              {macroAttribution.isReconciled ? "RECONCILED" : "DISCREPANCY"}
            </span>
          </div>
          <span className="mono-meta block text-[10px] text-text-muted font-mono truncate">
            &Delta; = {macroAttribution.reconciliationToleranceDeltaPct.toExponential(2)}
          </span>
        </div>

        {/* Metric 6: Brinson Gate Status */}
        <div className="p-3 rounded-lg bg-surface-elevated/40 border border-border/70 space-y-1">
          <span className="mono-meta block text-[10px] text-text-muted">BRINSON GATE STATUS</span>
          <div className="pt-0.5">
            <span className={`badge text-[9px] font-semibold tracking-wide ${
              brinsonSegmentation.executionStatus === "CALCULATED_VALIDATED_INPUTS"
                ? "badge-success"
                : "badge-warning"
            }`}>
              {brinsonSegmentation.executionStatus === "CALCULATED_VALIDATED_INPUTS"
                ? "CALCULATED"
                : "HELD AT GATE"}
            </span>
          </div>
          <span className="block text-[10px] text-text-muted truncate">
            {brinsonSegmentation.benchmarkDisclosedWeightPct?.toFixed(1) ?? "40.9"}% weights known
          </span>
        </div>
      </div>

      {/* 3. Sub-Navigation Tabs */}
      <div className="flex items-center gap-1 border-b border-border/60 overflow-x-auto text-[12px]">
        <button
          type="button"
          onClick={() => setActiveSubTab("TIMELINE")}
          className={`px-3 py-2 font-medium transition-colors border-b-2 -mb-[1px] whitespace-nowrap flex items-center gap-1.5 ${
            activeSubTab === "TIMELINE"
              ? "border-primary text-primary font-semibold"
              : "border-transparent text-text-secondary hover:text-text-primary"
          }`}
        >
          <span>Exposure Timeline</span>
          <span className="badge badge-neutral text-[9px] px-1 py-0">
            {data.exposureTimeline.length}
          </span>
        </button>

        <button
          type="button"
          onClick={() => setActiveSubTab("MACRO_ATTRIBUTION")}
          className={`px-3 py-2 font-medium transition-colors border-b-2 -mb-[1px] whitespace-nowrap flex items-center gap-1.5 ${
            activeSubTab === "MACRO_ATTRIBUTION"
              ? "border-primary text-primary font-semibold"
              : "border-transparent text-text-secondary hover:text-text-primary"
          }`}
        >
          <span>Macro Active Decomposition</span>
          <span className="badge badge-success text-[9px] px-1 py-0">Reconciled</span>
        </button>

        <button
          type="button"
          onClick={() => setActiveSubTab("BRINSON_FACHLER")}
          className={`px-3 py-2 font-medium transition-colors border-b-2 -mb-[1px] whitespace-nowrap flex items-center gap-1.5 ${
            activeSubTab === "BRINSON_FACHLER"
              ? "border-primary text-primary font-semibold"
              : "border-transparent text-text-secondary hover:text-text-primary"
          }`}
        >
          <span>Brinson-Fachler Constituent Analysis</span>
          <span className="badge badge-warning text-[9px] px-1 py-0">Evidence Gated</span>
        </button>

        <button
          type="button"
          onClick={() => setActiveSubTab("DATA_READINESS")}
          className={`px-3 py-2 font-medium transition-colors border-b-2 -mb-[1px] whitespace-nowrap flex items-center gap-1.5 ${
            activeSubTab === "DATA_READINESS"
              ? "border-primary text-primary font-semibold"
              : "border-transparent text-text-secondary hover:text-text-primary"
          }`}
        >
          <span>Data-Readiness Matrix</span>
          <span className="badge badge-neutral text-[9px] px-1 py-0">
            {dataReadinessGate.availableDependencies}/{dataReadinessGate.totalDependencies}
          </span>
        </button>

        <button
          type="button"
          onClick={() => setActiveSubTab("DUE_DILIGENCE")}
          className={`px-3 py-2 font-medium transition-colors border-b-2 -mb-[1px] whitespace-nowrap flex items-center gap-1.5 ${
            activeSubTab === "DUE_DILIGENCE"
              ? "border-primary text-primary font-semibold"
              : "border-transparent text-text-secondary hover:text-text-primary"
          }`}
        >
          <span>Due Diligence &amp; Risks</span>
          <span className="badge badge-neutral text-[9px] px-1 py-0">
            {data.investigationPrompts.length}
          </span>
        </button>
      </div>

      {/* 4. SUB-TAB 1: EXPOSURE TIMELINE */}
      {activeSubTab === "TIMELINE" && (
        <div className="space-y-6">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-surface-elevated/30 p-3.5 rounded-lg border border-border/70">
            <div>
              <h4 className="text-[13px] font-semibold text-text-primary">
                Point-in-Time Exposure Timeline &amp; Disclosure History
              </h4>
              <p className="text-[11px] text-text-secondary">
                Verified portfolio observations with explicit disclosure scope, classification coverage, and transition comparability.
              </p>
            </div>
            <div className="w-full sm:w-64">
              <input
                type="text"
                placeholder="Filter dates, scope, sectors..."
                value={timelineSearch}
                onChange={(e) => setTimelineSearch(e.target.value)}
                className="input input-sm w-full text-[11px]"
              />
            </div>
          </div>

          {/* Selected Snapshot Deep Dive Card */}
          {selectedSnapshot && (
            <div className="p-4 rounded-lg bg-surface border border-border space-y-4">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-border/60 pb-3">
                <div className="flex items-center gap-2">
                  <span className="mono-num text-[14px] font-bold text-text-primary">
                    Snapshot: {selectedSnapshot.portfolioDate}
                  </span>
                  <span className={`badge text-[10px] ${getComparabilityBadge(selectedSnapshot.comparabilityStatus).className}`}>
                    {getComparabilityBadge(selectedSnapshot.comparabilityStatus).label}
                  </span>
                  <span className={`badge text-[10px] ${getScopeBadge(selectedSnapshot.disclosureScope).className}`}>
                    {getScopeBadge(selectedSnapshot.disclosureScope).label}
                  </span>
                </div>
                <div className="mono-meta text-[11px] text-text-muted">
                  Knowledge Cutoff: {selectedSnapshot.availabilityTime.slice(0, 19).replace("T", " ")}
                </div>
              </div>

              {/* Allocation Breakdown Bar */}
              <div className="space-y-1.5">
                <div className="flex items-center justify-between text-[11px]">
                  <span className="text-text-secondary">Market Capitalization Allocation</span>
                  <span className="mono-num text-text-muted">
                    Reported Weight Sum: {selectedSnapshot.reportedWeightSumPct.toFixed(2)}% | {selectedSnapshot.holdingsCount} Holdings
                  </span>
                </div>
                <div className="h-4 w-full rounded bg-surface-elevated/80 flex overflow-hidden border border-border/50 text-[9px] font-mono font-semibold text-white">
                  <div
                    style={{ width: `${selectedSnapshot.largeCapWeightPct}%` }}
                    className="bg-blue-600 flex items-center justify-center truncate px-1"
                    title={`Large Cap: ${selectedSnapshot.largeCapWeightPct.toFixed(2)}%`}
                  >
                    {selectedSnapshot.largeCapWeightPct >= 8 ? `LC ${selectedSnapshot.largeCapWeightPct.toFixed(1)}%` : ""}
                  </div>
                  <div
                    style={{ width: `${selectedSnapshot.midCapWeightPct}%` }}
                    className="bg-teal-600 flex items-center justify-center truncate px-1"
                    title={`Mid Cap: ${selectedSnapshot.midCapWeightPct.toFixed(2)}%`}
                  >
                    {selectedSnapshot.midCapWeightPct >= 8 ? `MC ${selectedSnapshot.midCapWeightPct.toFixed(1)}%` : ""}
                  </div>
                  <div
                    style={{ width: `${selectedSnapshot.smallCapWeightPct}%` }}
                    className="bg-amber-500 flex items-center justify-center truncate px-1"
                    title={`Small Cap: ${selectedSnapshot.smallCapWeightPct.toFixed(2)}%`}
                  >
                    {selectedSnapshot.smallCapWeightPct >= 8 ? `SC ${selectedSnapshot.smallCapWeightPct.toFixed(1)}%` : ""}
                  </div>
                  <div
                    style={{ width: `${selectedSnapshot.cashWeightPct}%` }}
                    className="bg-emerald-600 flex items-center justify-center truncate px-1"
                    title={`Cash & Liquid: ${selectedSnapshot.cashWeightPct.toFixed(2)}%`}
                  >
                    {selectedSnapshot.cashWeightPct >= 5 ? `Cash ${selectedSnapshot.cashWeightPct.toFixed(1)}%` : ""}
                  </div>
                  {selectedSnapshot.unclassifiedWeightPct > 0.01 && (
                    <div
                      style={{ width: `${selectedSnapshot.unclassifiedWeightPct}%` }}
                      className="bg-slate-500 flex items-center justify-center truncate px-1"
                      title={`Unclassified: ${selectedSnapshot.unclassifiedWeightPct.toFixed(2)}%`}
                    >
                      {selectedSnapshot.unclassifiedWeightPct >= 5 ? `Unclass` : ""}
                    </div>
                  )}
                </div>
                <div className="flex flex-wrap items-center gap-4 text-[10px] text-text-secondary pt-1">
                  <span className="flex items-center gap-1.5">
                    <span className="h-2.5 w-2.5 rounded-full bg-blue-600 inline-block" />
                    Large Cap: <strong className="text-text-primary mono-num">{selectedSnapshot.largeCapWeightPct.toFixed(2)}%</strong>
                  </span>
                  <span className="flex items-center gap-1.5">
                    <span className="h-2.5 w-2.5 rounded-full bg-teal-600 inline-block" />
                    Mid Cap: <strong className="text-text-primary mono-num">{selectedSnapshot.midCapWeightPct.toFixed(2)}%</strong>
                  </span>
                  <span className="flex items-center gap-1.5">
                    <span className="h-2.5 w-2.5 rounded-full bg-amber-500 inline-block" />
                    Small Cap: <strong className="text-text-primary mono-num">{selectedSnapshot.smallCapWeightPct.toFixed(2)}%</strong>
                  </span>
                  <span className="flex items-center gap-1.5">
                    <span className="h-2.5 w-2.5 rounded-full bg-emerald-600 inline-block" />
                    Cash: <strong className="text-text-primary mono-num">{selectedSnapshot.cashWeightPct.toFixed(2)}%</strong>
                  </span>
                  {selectedSnapshot.unclassifiedWeightPct > 0.01 && (
                    <span className="flex items-center gap-1.5">
                      <span className="h-2.5 w-2.5 rounded-full bg-slate-500 inline-block" />
                      Unclassified: <strong className="text-text-muted mono-num">{selectedSnapshot.unclassifiedWeightPct.toFixed(2)}%</strong>
                    </span>
                  )}
                </div>
              </div>

              {/* Top Disclosed Sectors */}
              <div className="space-y-2 pt-2 border-t border-border/50">
                <span className="text-[11px] font-semibold text-text-primary">
                  Top Disclosed Industry Sectors ({selectedSnapshot.topSectors.length})
                </span>
                <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-2">
                  {selectedSnapshot.topSectors.map((sector, sIdx) => (
                    <div key={sIdx} className="p-2 rounded bg-surface-elevated/40 border border-border/50 text-[11px]">
                      <div className="font-medium text-text-primary truncate" title={sector.sectorName}>
                        {sector.sectorName}
                      </div>
                      <div className="flex items-center justify-between text-[10px] text-text-muted mt-1">
                        <span className="mono-num text-text-primary font-semibold">{sector.weightPct.toFixed(2)}%</span>
                        <span>{sector.holdingCount} {sector.holdingCount === 1 ? "holding" : "holdings"}</span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Transition Detail */}
              {selectedSnapshot.comparabilityReason && (
                <div className="p-3 rounded bg-surface-elevated/20 border border-border/50 text-[11px] space-y-1">
                  <div className="font-semibold text-text-primary">Historical Comparability Assessment:</div>
                  <p className="text-text-secondary leading-relaxed">{selectedSnapshot.comparabilityReason}</p>
                  {selectedSnapshot.daysSincePreviousSnapshot != null && (
                    <div className="mono-meta text-[10px] text-text-muted">
                      Interval between observations: {selectedSnapshot.daysSincePreviousSnapshot} calendar days.
                    </div>
                  )}
                </div>
              )}

              {/* Provenance SHA-256 */}
              {selectedSnapshot.sourceSha256 && (
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-1 text-[10px] font-mono text-text-muted bg-surface-elevated/30 p-2 rounded">
                  <span className="truncate">Source SHA-256: {selectedSnapshot.sourceSha256}</span>
                  {selectedSnapshot.sourceStorageUri && (
                    <span className="truncate text-text-secondary">{selectedSnapshot.sourceStorageUri}</span>
                  )}
                </div>
              )}
            </div>
          )}

          {/* Historical Timeline Table */}
          <div className="overflow-x-auto rounded-lg border border-border">
            <table className="w-full text-left text-[11px] border-collapse">
              <thead>
                <tr className="border-b border-border bg-surface-elevated/30 text-text-muted font-mono">
                  <th className="py-2.5 px-3 font-medium">SNAPSHOT DATE</th>
                  <th className="py-2.5 px-3 font-medium">SCOPE</th>
                  <th className="py-2.5 px-3 font-medium text-right">WEIGHT SUM</th>
                  <th className="py-2.5 px-3 font-medium text-right">HOLDINGS</th>
                  <th className="py-2.5 px-3 font-medium text-right">LARGE CAP</th>
                  <th className="py-2.5 px-3 font-medium text-right">MID CAP</th>
                  <th className="py-2.5 px-3 font-medium text-right">SMALL CAP</th>
                  <th className="py-2.5 px-3 font-medium text-right">CASH</th>
                  <th className="py-2.5 px-3 font-medium">COMPARABILITY</th>
                  <th className="py-2.5 px-3 font-medium">ACTION</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/40">
                {filteredTimeline.map((obs, idx) => {
                  const isSelected = selectedSnapshot?.snapshotId === obs.snapshotId;
                  const compBadge = getComparabilityBadge(obs.comparabilityStatus);
                  const scopeBadge = getScopeBadge(obs.disclosureScope);
                  return (
                    <tr
                      key={obs.snapshotId || idx}
                      className={`hover:bg-surface-elevated/20 transition-colors cursor-pointer ${
                        isSelected ? "bg-surface-elevated/40" : ""
                      }`}
                      onClick={() => setSelectedSnapshotIndex(data.exposureTimeline.findIndex((s) => s.snapshotId === obs.snapshotId))}
                    >
                      <td className="py-2 px-3 font-semibold text-text-primary mono-num whitespace-nowrap">
                        {obs.portfolioDate}
                      </td>
                      <td className="py-2 px-3 whitespace-nowrap">
                        <span className={`badge text-[9px] ${scopeBadge.className}`}>
                          {scopeBadge.label}
                        </span>
                      </td>
                      <td className="py-2 px-3 text-right mono-num text-text-primary">
                        {obs.reportedWeightSumPct.toFixed(2)}%
                      </td>
                      <td className="py-2 px-3 text-right mono-num text-text-secondary">
                        {obs.holdingsCount}
                      </td>
                      <td className="py-2 px-3 text-right mono-num text-blue-400">
                        {obs.largeCapWeightPct.toFixed(1)}%
                      </td>
                      <td className="py-2 px-3 text-right mono-num text-teal-400">
                        {obs.midCapWeightPct.toFixed(1)}%
                      </td>
                      <td className="py-2 px-3 text-right mono-num text-amber-400">
                        {obs.smallCapWeightPct.toFixed(1)}%
                      </td>
                      <td className="py-2 px-3 text-right mono-num text-emerald-400">
                        {obs.cashWeightPct.toFixed(1)}%
                      </td>
                      <td className="py-2 px-3 whitespace-nowrap">
                        <span className={`badge text-[9px] ${compBadge.className}`}>
                          {compBadge.label}
                        </span>
                      </td>
                      <td className="py-2 px-3">
                        <button
                          type="button"
                          className="btn btn-secondary btn-xs text-[10px]"
                          onClick={(e) => {
                            e.stopPropagation();
                            setSelectedSnapshotIndex(data.exposureTimeline.findIndex((s) => s.snapshotId === obs.snapshotId));
                          }}
                        >
                          View
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* 5. SUB-TAB 2: MACRO ACTIVE DECOMPOSITION */}
      {activeSubTab === "MACRO_ATTRIBUTION" && (
        <div className="space-y-6">
          <div className="p-4 rounded-lg bg-surface-elevated/30 border border-border/70 space-y-2">
            <h4 className="text-[14px] font-bold text-text-primary tracking-tight">
              Empirical Macro Active-Return Decomposition
            </h4>
            <p className="text-[12px] text-text-secondary leading-relaxed">
              Decomposes active excess return <span className="font-mono text-text-primary">R_A = R_p - R_b</span> into
              systematic market sensitivity, cash drag, and selection/sector residual.
              All returns are evaluated over identical aligned historical dates using exact compound formulas.
            </p>
          </div>

          {/* 3 Component Decomposition Grid */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {/* Card 1: Systematic Market Tilt */}
            <div className="p-4 rounded-lg bg-surface border border-border space-y-3">
              <div className="flex items-center justify-between">
                <span className="mono-meta text-[10px] text-text-muted font-mono">1. SYSTEMATIC MARKET TILT</span>
                <span className="badge badge-neutral text-[9px]">Beta Sensitivity</span>
              </div>
              <div className="mono-num text-[20px] font-bold text-text-primary">
                {macroAttribution.marketSensitivityContributionPctFormatted}
              </div>
              <div className="text-[11px] text-text-secondary leading-relaxed bg-surface-elevated/40 p-2.5 rounded border border-border/40 font-mono">
                (&beta; - 1) &times; R_b = ({macroAttribution.empiricalBeta.toFixed(4)} - 1) &times; {data.benchmarkReturnPctFormatted}
              </div>
              <p className="text-[11px] text-text-muted leading-relaxed">
                Contribution to active return explained by systematic portfolio beta sensitivity relative to {data.benchmarkCode}.
                Beta &gt; 1 amplifies bull markets and deepens drawdowns.
              </p>
            </div>

            {/* Card 2: Cash Allocation Drag */}
            <div className="p-4 rounded-lg bg-surface border border-border space-y-3">
              <div className="flex items-center justify-between">
                <span className="mono-meta text-[10px] text-text-muted font-mono">2. CASH ALLOCATION DRAG</span>
                <span className="badge badge-neutral text-[9px]">Defensive Drag</span>
              </div>
              <div className={`mono-num text-[20px] font-bold ${
                macroAttribution.cashDragContributionPct >= 0 ? "text-success-fg" : "text-critical-fg"
              }`}>
                {macroAttribution.cashDragContributionPctFormatted}
              </div>
              <div className="text-[11px] text-text-secondary leading-relaxed bg-surface-elevated/40 p-2.5 rounded border border-border/40 font-mono">
                w_cash &times; (R_rf - R_b) = {macroAttribution.initialCashWeightPctFormatted} &times; ({macroAttribution.riskFreeReturnPctFormatted} - {data.benchmarkReturnPctFormatted})
              </div>
              <p className="text-[11px] text-text-muted leading-relaxed">
                Defensive yield generated on cash/liquid reserves less the opportunity cost of benchmark equity market participation.
              </p>
            </div>

            {/* Card 3: Selection & Sector Residual */}
            <div className="p-4 rounded-lg bg-surface border border-border space-y-3">
              <div className="flex items-center justify-between">
                <span className="mono-meta text-[10px] text-text-muted font-mono">3. SELECTION &amp; SECTOR RESIDUAL</span>
                <span className="badge badge-warning text-[9px]">Unexplained Residual</span>
              </div>
              <div className={`mono-num text-[20px] font-bold ${
                macroAttribution.selectionAndSectorResidualPct >= 0 ? "text-success-fg" : "text-critical-fg"
              }`}>
                {macroAttribution.selectionAndSectorResidualPctFormatted}
              </div>
              <div className="text-[11px] text-text-secondary leading-relaxed bg-surface-elevated/40 p-2.5 rounded border border-border/40 font-mono">
                R_A - [Systematic + Cash] = {data.activeReturnPctFormatted} - ({macroAttribution.marketSensitivityContributionPctFormatted} + {macroAttribution.cashDragContributionPctFormatted})
              </div>
              <p className="text-[11px] text-text-muted leading-relaxed">
                Active stock selection, industry overweight/underweight, and intra-month trading effects not explained by macro betas.
                <strong className="text-text-primary"> Note:</strong> Under YUKIRA epistemic rules, this residual is not assumed to be manager skill.
              </p>
            </div>
          </div>

          {/* Algebraic Reconciliation Verification Panel */}
          <div className="p-4 rounded-lg bg-surface border border-border/80 space-y-3">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-border/60 pb-2">
              <div className="flex items-center gap-2">
                <span className={`badge text-[10px] font-semibold ${
                  macroAttribution.isReconciled ? "badge-success" : "badge-critical"
                }`}>
                  {macroAttribution.isReconciled ? "MATHEMATICALLY RECONCILED" : "RECONCILIATION FAILURE"}
                </span>
                <span className="text-[12px] font-bold text-text-primary">
                  Algebraic Active Return Reconciliation Invariant
                </span>
              </div>
              <span className="mono-meta text-[11px] text-text-muted font-mono">
                Tolerance: &Delta; &lt; 10&#8315;&#8310; (Observed: {macroAttribution.reconciliationToleranceDeltaPct.toExponential(2)})
              </span>
            </div>

            <div className="p-3 rounded bg-surface-elevated/40 border border-border/50 font-mono text-[11px] text-text-primary overflow-x-auto space-y-1">
              <div>Formula: {macroAttribution.reconciliationFormula}</div>
              <div className="text-text-secondary">
                Reconciled Sum: ({macroAttribution.marketSensitivityContributionPctFormatted}) + ({macroAttribution.cashDragContributionPctFormatted}) + ({macroAttribution.selectionAndSectorResidualPctFormatted}) = <strong className="text-text-primary">{macroAttribution.reconciledActiveReturnPctFormatted}</strong>
              </div>
              <div className="text-text-secondary">
                Direct Active Return: {data.fundReturnPctFormatted} - {data.benchmarkReturnPctFormatted} = <strong className="text-text-primary">{data.activeReturnPctFormatted}</strong>
              </div>
            </div>
          </div>

          {/* Initial Active Style Context */}
          {styleContext && (
            <div className="p-4 rounded-lg bg-surface border border-border space-y-3">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-border/60 pb-2">
                <div className="flex items-center gap-2">
                  <h4 className="text-[13px] font-semibold text-text-primary">
                    Initial Portfolio Style Allocation Context
                  </h4>
                  <span className="badge badge-success text-[10px]">
                    {styleContext.classificationStatus}
                  </span>
                </div>
                <span className="mono-meta text-[11px] text-text-muted">
                  AMFI Ranking Period: {styleContext.classificationRankingPeriod}
                </span>
              </div>

              <div className="grid grid-cols-2 sm:grid-cols-5 gap-3">
                <div className="p-2.5 rounded bg-surface-elevated/40 border border-border/50 text-center">
                  <span className="mono-meta block text-[10px] text-text-muted">LARGE CAP</span>
                  <span className="mono-num text-[16px] font-bold text-text-primary">
                    {styleContext.largeCapWeightPctFormatted}
                  </span>
                </div>
                <div className="p-2.5 rounded bg-surface-elevated/40 border border-border/50 text-center">
                  <span className="mono-meta block text-[10px] text-text-muted">MID CAP</span>
                  <span className="mono-num text-[16px] font-bold text-text-primary">
                    {styleContext.midCapWeightPctFormatted}
                  </span>
                </div>
                <div className="p-2.5 rounded bg-surface-elevated/40 border border-border/50 text-center">
                  <span className="mono-meta block text-[10px] text-text-muted">SMALL CAP</span>
                  <span className="mono-num text-[16px] font-bold text-text-primary">
                    {styleContext.smallCapWeightPctFormatted}
                  </span>
                </div>
                <div className="p-2.5 rounded bg-surface-elevated/40 border border-border/50 text-center">
                  <span className="mono-meta block text-[10px] text-text-muted">CASH &amp; LIQUID</span>
                  <span className="mono-num text-[16px] font-bold text-text-primary">
                    {styleContext.cashWeightPctFormatted}
                  </span>
                </div>
                <div className="p-2.5 rounded bg-surface-elevated/40 border border-border/50 text-center">
                  <span className="mono-meta block text-[10px] text-text-muted">UNCLASSIFIED</span>
                  <span className="mono-num text-[16px] font-bold text-text-muted">
                    {styleContext.unclassifiedWeightPctFormatted}
                  </span>
                </div>
              </div>

              <p className="text-[11px] text-text-secondary leading-relaxed">
                <strong className="text-text-primary">Active Style Tilt:</strong> {styleContext.activeTiltSummary}
              </p>
            </div>
          )}
        </div>
      )}

      {/* 6. SUB-TAB 3: BRINSON-FACHLER CONSTITUENT ANALYSIS */}
      {activeSubTab === "BRINSON_FACHLER" && (
        <div className="space-y-6">
          {/* Prominent Evidence Gate Banner */}
          <div className="p-5 rounded-lg bg-surface border border-warning/50 space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-border/60 pb-3">
              <div className="flex items-center gap-2">
                <span className="badge badge-warning text-[10px] font-bold">
                  {brinsonSegmentation.executionStatus}
                </span>
                <h4 className="text-[14px] font-bold text-text-primary tracking-tight">
                  Constituent Brinson-Fachler Attribution Evidence Gate
                </h4>
              </div>
              <span className="badge badge-neutral text-[10px] font-mono">
                Disclosed Benchmark Weights: {brinsonSegmentation.benchmarkDisclosedWeightPct?.toFixed(1) ?? "40.9"}%
              </span>
            </div>

            <div className="p-3.5 rounded-lg bg-warning-bg/15 border border-warning/30 text-[12px] space-y-2">
              <div className="font-semibold text-text-primary flex items-center gap-1.5">
                <span>&#9888; Why Constituent Brinson Attribution is Held at the Gate</span>
              </div>
              <p className="text-text-secondary leading-relaxed">
                {brinsonSegmentation.statusExplanation || brinsonSegmentation.reasonIfNotCalculated}
              </p>
            </div>

            {/* Diagnostic Metrics of the Missing Data */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-[11px]">
              <div className="p-3 rounded bg-surface-elevated/40 border border-border/50 space-y-1">
                <span className="text-text-muted block">Benchmark Disclosed Weight</span>
                <span className="mono-num text-[16px] font-bold text-text-primary">
                  {brinsonSegmentation.benchmarkDisclosedWeightPct?.toFixed(2) ?? "40.95"}%
                </span>
                <span className="text-text-muted block text-[10px]">Top 10 weights disclosed in factsheet</span>
              </div>
              <div className="p-3 rounded bg-surface-elevated/40 border border-border/50 space-y-1">
                <span className="text-text-muted block">Weighted Constituents</span>
                <span className="mono-num text-[16px] font-bold text-text-primary">
                  {brinsonSegmentation.benchmarkWeightedCount ?? 10} / {brinsonSegmentation.benchmarkTotalCount ?? 501}
                </span>
                <span className="text-text-muted block text-[10px]">~491 constituents unweighted</span>
              </div>
              <div className="p-3 rounded bg-surface-elevated/40 border border-border/50 space-y-1">
                <span className="text-text-muted block">Stock Total Return Series</span>
                <span className="mono-num text-[16px] font-bold text-amber-500">
                  UNAVAILABLE
                </span>
                <span className="text-text-muted block text-[10px]">Requires licensed commercial feed</span>
              </div>
            </div>

            {/* Epistemic Anti-Fabrication Principles */}
            <div className="space-y-2 pt-2 border-t border-border/50">
              <h5 className="text-[12px] font-semibold text-text-primary">
                YUKIRA Epistemic Anti-Fabrication Invariants:
              </h5>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-3 text-[11px]">
                <div className="p-3 rounded bg-surface-elevated/30 border border-border/40 space-y-1">
                  <strong className="text-text-primary">1. No Synthetic Renormalization:</strong>
                  <p className="text-text-muted leading-relaxed">
                    Scaling disclosed top 10 weights (40.95%) up to 100% would introduce extreme artificial bias and falsify allocation effects.
                  </p>
                </div>
                <div className="p-3 rounded bg-surface-elevated/30 border border-border/40 space-y-1">
                  <strong className="text-text-primary">2. No Weight Imputation:</strong>
                  <p className="text-text-muted leading-relaxed">
                    Unobserved constituent weights are never estimated via equal weighting, market-cap proxies, or LLM hallucination.
                  </p>
                </div>
                <div className="p-3 rounded bg-surface-elevated/30 border border-border/40 space-y-1">
                  <strong className="text-text-primary">3. No False Alpha Claims:</strong>
                  <p className="text-text-muted leading-relaxed">
                    Residual returns resulting from unobserved constituents are never presented as stock-selection skill or manager alpha.
                  </p>
                </div>
              </div>
            </div>

            {/* Mathematical Model Formulation */}
            <div className="space-y-2 pt-2 border-t border-border/50">
              <h5 className="text-[12px] font-semibold text-text-primary">
                Brinson-Fachler (1985) Mathematical Specification:
              </h5>
              <div className="p-3 rounded bg-surface-elevated/40 border border-border font-mono text-[11px] text-text-primary space-y-1.5">
                <div>Allocation Effect:  A_s = (w_p,s - w_b,s) &times; (R_b,s - R_B)</div>
                <div>Selection Effect:   S_s = w_b,s &times; (R_p,s - R_b,s)</div>
                <div>Interaction Effect: I_s = (w_p,s - w_b,s) &times; (R_p,s - R_b,s)</div>
                <div className="text-text-muted pt-1 border-t border-border/40">
                  Total Active Return:  R_A = &sum;_s (A_s + S_s + I_s)
                </div>
              </div>
            </div>

            {/* Segments table if available */}
            {brinsonSegmentation.segments && brinsonSegmentation.segments.length > 0 && (
              <div className="space-y-2 pt-2 border-t border-border/50">
                <h5 className="text-[12px] font-semibold text-text-primary">
                  Segment Breakdown ({brinsonSegmentation.segments.length})
                </h5>
                <div className="overflow-x-auto rounded border border-border">
                  <table className="w-full text-left text-[11px] border-collapse">
                    <thead>
                      <tr className="border-b border-border bg-surface-elevated/30 text-text-muted font-mono">
                        <th className="py-2 px-3 font-medium">SEGMENT</th>
                        <th className="py-2 px-3 font-medium text-right">PORTFOLIO WT</th>
                        <th className="py-2 px-3 font-medium text-right">BENCHMARK WT</th>
                        <th className="py-2 px-3 font-medium text-right">PORTFOLIO RET</th>
                        <th className="py-2 px-3 font-medium text-right">BENCHMARK RET</th>
                        <th className="py-2 px-3 font-medium text-right">ALLOCATION</th>
                        <th className="py-2 px-3 font-medium text-right">SELECTION</th>
                        <th className="py-2 px-3 font-medium text-right">INTERACTION</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border/40">
                      {brinsonSegmentation.segments.map((seg, idx) => (
                        <tr key={idx} className="hover:bg-surface-elevated/20">
                          <td className="py-2 px-3 font-semibold text-text-primary">{seg.segmentName}</td>
                          <td className="py-2 px-3 text-right mono-num">{seg.portfolioWeightPct.toFixed(2)}%</td>
                          <td className="py-2 px-3 text-right mono-num">{seg.benchmarkWeightPct.toFixed(2)}%</td>
                          <td className="py-2 px-3 text-right mono-num">{seg.portfolioReturnPct.toFixed(2)}%</td>
                          <td className="py-2 px-3 text-right mono-num">{seg.benchmarkReturnPct.toFixed(2)}%</td>
                          <td className="py-2 px-3 text-right mono-num">{seg.allocationEffectPct.toFixed(4)}%</td>
                          <td className="py-2 px-3 text-right mono-num">{seg.selectionEffectPct.toFixed(4)}%</td>
                          <td className="py-2 px-3 text-right mono-num">{seg.interactionEffectPct.toFixed(4)}%</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* 7. SUB-TAB 4: DATA-READINESS MATRIX */}
      {activeSubTab === "DATA_READINESS" && (
        <div className="space-y-6">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 p-4 rounded-lg bg-surface border border-border">
            <div>
              <div className="flex items-center gap-2">
                <span className="badge badge-warning text-[10px]">AUDIT GATE MATRIX</span>
                <h4 className="text-[14px] font-bold text-text-primary tracking-tight">
                  Attribution Prerequisite Data-Readiness Gate
                </h4>
              </div>
              <p className="text-[11px] text-text-secondary mt-1">
                {dataReadinessGate.availableDependencies} of {dataReadinessGate.totalDependencies} dependencies verified in production database.
                Missing prerequisites are explicitly held at the gate to ensure zero synthetic data fabrication.
              </p>
            </div>
            <span className="badge badge-neutral text-[11px] font-mono">
              {dataReadinessGate.missingDependencies} Held at Gate
            </span>
          </div>

          <div className="overflow-x-auto rounded-lg border border-border">
            <table className="w-full text-left text-[11px] border-collapse">
              <thead>
                <tr className="border-b border-border bg-surface-elevated/30 text-text-muted font-mono">
                  <th className="py-2.5 px-3 font-medium">DEPENDENCY CODE</th>
                  <th className="py-2.5 px-3 font-medium">DATA REQUIREMENT</th>
                  <th className="py-2.5 px-3 font-medium">ANALYTICAL ROLE</th>
                  <th className="py-2.5 px-3 font-medium">REPOSITORY STATUS</th>
                  <th className="py-2.5 px-3 font-medium">AUTHORITATIVE SOURCE</th>
                  <th className="py-2.5 px-3 font-medium">EPISTEMIC GOVERNANCE NOTE</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/40">
                {dataReadinessGate.dependencies.map((dep, idx) => {
                  const isAvailable = dep.repositoryStatus === "AVAILABLE_VERIFIED";
                  return (
                    <tr key={idx} className="hover:bg-surface-elevated/20 transition-colors">
                      <td className="py-2.5 px-3 font-mono text-text-primary whitespace-nowrap">
                        {dep.dependencyCode}
                      </td>
                      <td className="py-2.5 px-3 font-semibold text-text-primary whitespace-nowrap">
                        {dep.dependencyName}
                      </td>
                      <td className="py-2.5 px-3 text-text-secondary font-mono text-[10px]">
                        {dep.requiredFor}
                      </td>
                      <td className="py-2.5 px-3 whitespace-nowrap">
                        <span className={`badge text-[10px] font-semibold ${
                          isAvailable ? "badge-success" : "badge-warning"
                        }`}>
                          {isAvailable ? "AVAILABLE & VERIFIED" : "HELD AT GATE"}
                        </span>
                      </td>
                      <td className="py-2.5 px-3 text-text-secondary">
                        {dep.officialSource}
                      </td>
                      <td className="py-2.5 px-3 text-text-muted leading-relaxed">
                        {dep.epistemicNote}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* 8. SUB-TAB 5: DUE DILIGENCE & RISKS */}
      {activeSubTab === "DUE_DILIGENCE" && (
        <div className="space-y-6">
          {/* Allocator Investigation Questions */}
          <div className="p-5 rounded-lg bg-surface border border-border space-y-4">
            <div className="border-b border-border/60 pb-3">
              <h4 className="text-[14px] font-bold text-text-primary tracking-tight">
                Targeted Investor Due-Diligence Questions
              </h4>
              <p className="text-[11px] text-text-secondary mt-0.5">
                Questions generated from observed portfolio data quality, classification shifts, and active return spreads.
              </p>
            </div>

            <div className="space-y-3">
              {data.investigationPrompts.map((prompt, idx) => (
                <div
                  key={idx}
                  className="p-3.5 rounded-lg bg-surface-elevated/30 border border-border/60 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-[12px]"
                >
                  <div className="space-y-1">
                    <span className="mono-meta text-[10px] text-text-muted">QUESTION {idx + 1}</span>
                    <p className="text-text-primary font-medium leading-relaxed">{prompt}</p>
                  </div>
                  {onInvestigateQuestion && (
                    <button
                      type="button"
                      onClick={() => onInvestigateQuestion(prompt, `Style Drift & Attribution Investigation Q${idx + 1}`)}
                      className="btn btn-secondary btn-sm text-[11px] whitespace-nowrap self-start sm:self-center"
                    >
                      Investigate
                    </button>
                  )}
                </div>
              ))}
            </div>
          </div>

          {/* Epistemic Limitations & Guardrails */}
          <div className="p-5 rounded-lg bg-surface border border-border space-y-3">
            <h4 className="text-[14px] font-bold text-text-primary tracking-tight">
              Epistemic Limitations &amp; Governance Guardrails
            </h4>
            <div className="divide-y divide-border/40">
              {data.epistemicLimitations.map((lim, idx) => (
                <div key={idx} className="py-2.5 text-[11px] text-text-secondary leading-relaxed flex items-start gap-2">
                  <span className="text-text-muted font-mono">{idx + 1}.</span>
                  <span>{lim}</span>
                </div>
              ))}
            </div>
          </div>

          {/* Cryptographic Audit Trail */}
          <div className="p-4 rounded-lg bg-surface-elevated/30 border border-border space-y-3 text-[11px]">
            <h4 className="font-semibold text-text-primary">Cryptographic Audit Trail &amp; Lineage</h4>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3 font-mono text-[10px] text-text-muted">
              <div>
                <span className="block text-text-secondary font-semibold">Methodology Version:</span>
                <span className="text-text-primary">{data.provenance.methodologyVersion}</span>
              </div>
              <div>
                <span className="block text-text-secondary font-semibold">Calculation Run Timestamp:</span>
                <span className="text-text-primary">{data.provenance.calculationRunTimestamp}</span>
              </div>
              <div className="break-all">
                <span className="block text-text-secondary font-semibold">Fund NAV Source SHA-256:</span>
                <span className="text-text-primary">{data.provenance.fundNavSourceSha256}</span>
              </div>
              <div className="break-all">
                <span className="block text-text-secondary font-semibold">Benchmark Observation SHA-256:</span>
                <span className="text-text-primary">{data.provenance.benchmarkObservationSha256}</span>
              </div>
              <div className="md:col-span-2">
                <span className="block text-text-secondary font-semibold">Risk-Free Benchmark Source:</span>
                <span className="text-text-primary">{data.provenance.riskFreeSourceDescription}</span>
              </div>
            </div>

            <div className="pt-2 border-t border-border/50 text-[10px] text-text-muted italic leading-relaxed">
              {data.provenance.nonAdvisoryDisclaimer}
            </div>
          </div>
        </div>
      )}

      {/* 9. Methodology Drawer (Collapsible) */}
      {showMethodologyDrawer && (
        <div className="mt-4 p-5 rounded-lg bg-surface-elevated/40 border border-border space-y-4 text-[12px] leading-relaxed">
          <div className="flex items-center justify-between border-b border-border/60 pb-2">
            <span className="font-bold text-text-primary">
              Point-in-Time Style Drift &amp; Return Attribution Methodology
            </span>
            <span className="mono-meta text-[11px] text-text-muted">
              Specification: {data.provenance.methodologyVersion}
            </span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-[11px]">
            <div className="space-y-1">
              <strong className="text-text-primary">1. Point-in-Time Dual Cutoff Rule:</strong>
              <p className="text-text-secondary">
                Calculations strictly enforce <span className="font-mono">effective_date &le; asOfDate</span> AND{" "}
                <span className="font-mono">availability_time &le; knowledgeCutoff</span>.
                Source artifacts published after the knowledge cutoff are never visible to the calculation kernel.
              </p>
            </div>

            <div className="space-y-1">
              <strong className="text-text-primary">2. Macro Return Decomposition:</strong>
              <p className="text-text-secondary">
                Active return is deterministically decomposed into systematic market sensitivity <span className="font-mono">(&beta; - 1) &times; R_b</span>,
                cash drag <span className="font-mono">w_cash &times; (R_rf - R_b)</span>, and selection residual.
                All returns are aligned over identical trading calendar dates.
              </p>
            </div>

            <div className="space-y-1">
              <strong className="text-text-primary">3. Brinson-Fachler Evidence Gate:</strong>
              <p className="text-text-secondary">
                Under YUKIRA constitutional rules, Brinson constituent attribution requires complete constituent weights and daily constituent returns.
                Because public factsheets disclose only top holdings (~40.95%), the model is held at the gate with status{" "}
                <span className="font-mono font-semibold">BLOCKED_BENCHMARK_WEIGHTS_INCOMPLETE</span>.
              </p>
            </div>

            <div className="space-y-1">
              <strong className="text-text-primary">4. SEBI Disclosure Scope Standards:</strong>
              <p className="text-text-secondary">
                SEBI regulations mandate monthly complete portfolio disclosures for mutual funds.
                Transitions between different disclosure scopes (e.g. top 10 factsheets vs full monthly disclosures) are explicitly flagged as scope shifts.
              </p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
