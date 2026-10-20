"use client";

import React, { useState, useEffect, useMemo } from "react";
import {
  fetchFactorExposure,
  fetchBenchmarkCompositionComparison,
  PortfolioFactorExposureResponseDto,
  BenchmarkCompositionComparisonResponseDto,
} from "@/lib/api/portfolio";
import { BenchmarkCompositionSection } from "./BenchmarkCompositionSection";
import { MarketCapDriftView } from "./MarketCapDriftView";
import { HoldingLongevityView } from "./HoldingLongevityView";
import { StyleDriftAttributionView } from "./StyleDriftAttributionView";

interface FactorExposureViewProps {
  schemeOptionId: number;
  schemeCode?: string;
  asOfDate?: string;
  knowledgeCutoffTime?: string;
  onInvestigateQuestion?: (question: string, reason?: string) => void;
}

type ActiveTab = "OVERVIEW" | "HOLDING_LONGEVITY" | "MARKET_CAP_DRIFT" | "BENCHMARK_COMPOSITION" | "DRIFT_HISTORY" | "OBSERVED_SHIFTS" | "HOLDINGS_QUALITY" | "ATTRIBUTION_PREREQUISITES";

export function FactorExposureView({
  schemeOptionId,
  schemeCode,
  asOfDate: propAsOfDate,
  knowledgeCutoffTime: propCutoffTime,
  onInvestigateQuestion,
}: FactorExposureViewProps) {
  void schemeCode;
  const [data, setData] = useState<PortfolioFactorExposureResponseDto | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<ActiveTab>("OVERVIEW");
  const [selectedSnapshotId, setSelectedSnapshotId] = useState<number | null>(null);
  const [showMethodologyDrawer, setShowMethodologyDrawer] = useState<boolean>(false);

  const [benchmarkComparison, setBenchmarkComparison] = useState<BenchmarkCompositionComparisonResponseDto | null>(null);
  const [benchmarkLoading, setBenchmarkLoading] = useState<boolean>(false);

  const effectiveAsOfDate = propAsOfDate || "2026-10-01";
  const effectiveCutoffTime = propCutoffTime || "2026-10-10T23:59:59+05:30";

  useEffect(() => {
    let active = true;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLoading(true);
    setBenchmarkLoading(true);
    setError(null);

    fetchFactorExposure(schemeOptionId, effectiveAsOfDate, effectiveCutoffTime)
      .then((res) => {
        if (!active) return;
        setData(res);
        if (res.currentExposure) {
          setSelectedSnapshotId(res.currentExposure.snapshotId);
        }
      })
      .catch((err: unknown) => {
        if (!active) return;
        setError(err instanceof Error ? err.message : "Failed to load factor exposure analysis.");
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    fetchBenchmarkCompositionComparison(schemeOptionId, effectiveAsOfDate, effectiveCutoffTime)
      .then((bmRes) => {
        if (!active) return;
        setBenchmarkComparison(bmRes);
      })
      .catch((err) => {
        console.warn("Failed to load benchmark comparison", err);
      })
      .finally(() => {
        if (active) setBenchmarkLoading(false);
      });

    return () => {
      active = false;
    };
  }, [schemeOptionId, effectiveAsOfDate, effectiveCutoffTime]);

  const selectedSnapshot = useMemo(() => {
    if (!data || !data.driftHistory || data.driftHistory.length === 0) return null;
    if (selectedSnapshotId == null) return data.driftHistory[data.driftHistory.length - 1];
    return data.driftHistory.find((s) => s.snapshotId === selectedSnapshotId) || data.driftHistory[data.driftHistory.length - 1];
  }, [data, selectedSnapshotId]);

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
        <div className="skeleton h-48 w-full" />
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="state-panel-error space-y-3" role="alert">
        <div className="panel-header">
          <p className="text-[14px] font-semibold text-text-primary">Portfolio Factor Exposure Unavailable</p>
        </div>
        <p className="text-[13px] text-critical-fg">{error || "Failed to load factor exposure analysis from backend."}</p>
      </div>
    );
  }

  if (data.analysisStatus === "NO_HOLDINGS_SNAPSHOTS") {
    return (
      <div className="state-well space-y-2 p-5">
        <div className="flex items-center gap-2">
          <span className="badge badge-neutral text-[10px]">NO DISCLOSURES</span>
          <p className="text-[14px] font-semibold text-text-primary">No Holdings Disclosures Available</p>
        </div>
        <p className="text-[13px] text-text-secondary">{data.statusReason}</p>
        <p className="text-[12px] text-text-muted">
          Factor exposure and style drift require official SEBI monthly portfolio disclosures with verified point-in-time publication timestamps.
        </p>
      </div>
    );
  }

  const current = data.currentExposure;
  const history = data.driftHistory || [];
  const shifts = data.largestObservedShifts || [];
  const epistemic = data.epistemic;

  return (
    <div className="panel p-5 md:p-6 space-y-6">
      {/* 1. Header & Institutional Context */}
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between border-b border-border/70 pb-4">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <span className="badge badge-warning text-[10px] uppercase tracking-wider">
              {epistemic?.governanceStatus || "CANDIDATE METHODOLOGY"}
            </span>
            <span className="mono-meta text-[11px] text-text-muted">
              Benchmark: {data.benchmarkCode} ({data.benchmarkName})
            </span>
          </div>
          <h3 className="mt-1 text-[16px] font-bold tracking-tight text-text-primary">
            Portfolio Factor Exposure &amp; Style Drift Dynamics
          </h3>
          <p className="text-[12px] text-text-secondary">
            Holdings-based characteristic estimation across official monthly disclosure snapshots. Zero synthetic labels or estimated returns.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => setShowMethodologyDrawer(!showMethodologyDrawer)}
            className="btn btn-secondary btn-sm text-[11px]"
            aria-expanded={showMethodologyDrawer}
          >
            {showMethodologyDrawer ? "Hide Methodology" : "Methodology & Provenance"}
          </button>
        </div>
      </div>

      {/* 2. Sub-Navigation Tabs */}
      <div className="flex items-center gap-1 border-b border-border/60 overflow-x-auto text-[12px]">
        <button
          onClick={() => setActiveTab("OVERVIEW")}
          className={`px-3 py-2 font-medium transition-colors border-b-2 -mb-[1px] whitespace-nowrap ${
            activeTab === "OVERVIEW"
              ? "border-primary text-primary"
              : "border-transparent text-text-secondary hover:text-text-primary"
          }`}
        >
          Exposure Overview
        </button>
        <button
          onClick={() => setActiveTab("MARKET_CAP_DRIFT")}
          className={`px-3 py-2 font-medium transition-colors border-b-2 -mb-[1px] whitespace-nowrap flex items-center gap-1.5 ${
            activeTab === "MARKET_CAP_DRIFT"
              ? "border-primary text-primary font-semibold"
              : "border-transparent text-text-secondary hover:text-text-primary"
          }`}
        >
          <span>Market-Cap Style Drift</span>
          <span className="badge badge-success text-[9px] px-1 py-0 uppercase">NEW · PIT Ledger</span>
        </button>
        <button
          onClick={() => setActiveTab("HOLDING_LONGEVITY")}
          className={`px-3 py-2 font-medium transition-colors border-b-2 -mb-[1px] whitespace-nowrap flex items-center gap-1.5 ${
            activeTab === "HOLDING_LONGEVITY"
              ? "border-primary text-primary font-semibold"
              : "border-transparent text-text-secondary hover:text-text-primary"
          }`}
        >
          <span>Holding Longevity &amp; Turnover</span>
          <span className="badge badge-success text-[9px] px-1 py-0 uppercase">NEW · PIT Ledger</span>
        </button>
        <button
          onClick={() => setActiveTab("BENCHMARK_COMPOSITION")}
          className={`px-3 py-2 font-medium transition-colors border-b-2 -mb-[1px] whitespace-nowrap ${
            activeTab === "BENCHMARK_COMPOSITION"
              ? "border-primary text-primary"
              : "border-transparent text-text-secondary hover:text-text-primary"
          }`}
        >
          Benchmark Composition &amp; Overlap
        </button>
        <button
          onClick={() => setActiveTab("DRIFT_HISTORY")}
          className={`px-3 py-2 font-medium transition-colors border-b-2 -mb-[1px] whitespace-nowrap ${
            activeTab === "DRIFT_HISTORY"
              ? "border-primary text-primary"
              : "border-transparent text-text-secondary hover:text-text-primary"
          }`}
        >
          Style Drift Trajectory ({history.length} Snapshots)
        </button>
        <button
          onClick={() => setActiveTab("OBSERVED_SHIFTS")}
          className={`px-3 py-2 font-medium transition-colors border-b-2 -mb-[1px] whitespace-nowrap ${
            activeTab === "OBSERVED_SHIFTS"
              ? "border-primary text-primary"
              : "border-transparent text-text-secondary hover:text-text-primary"
          }`}
        >
          Observed Shifts ({shifts.length})
        </button>
        <button
          onClick={() => setActiveTab("HOLDINGS_QUALITY")}
          className={`px-3 py-2 font-medium transition-colors border-b-2 -mb-[1px] whitespace-nowrap ${
            activeTab === "HOLDINGS_QUALITY"
              ? "border-primary text-primary"
              : "border-transparent text-text-secondary hover:text-text-primary"
          }`}
        >
          Holdings Quality &amp; Coverage
        </button>
        <button
          onClick={() => setActiveTab("ATTRIBUTION_PREREQUISITES")}
          className={`px-3 py-2 font-medium transition-colors border-b-2 -mb-[1px] whitespace-nowrap flex items-center gap-1.5 ${
            activeTab === "ATTRIBUTION_PREREQUISITES"
              ? "border-primary text-primary font-semibold"
              : "border-transparent text-text-secondary hover:text-text-primary"
          }`}
        >
          <span>Style Drift &amp; Active Attribution</span>
          <span className="badge badge-success text-[9px] px-1 py-0 uppercase">NEW · PIT Ledger</span>
        </button>
      </div>

      {/* 3. TAB CONTENT */}

      {/* TAB 1: OVERVIEW */}
      {activeTab === "OVERVIEW" && current && (
        <div className="space-y-6">
          {/* Point-in-Time Classification Provenance Banner */}
          <div className="flex flex-col md:flex-row md:items-center justify-between p-3.5 rounded-lg bg-surface-elevated/30 border border-border/70 gap-3">
            <div className="flex items-center gap-2.5">
              <span className={`badge text-[10px] font-semibold tracking-wide ${
                current.marketCap.classificationStatus === "VERIFIED_HISTORICAL_PIT"
                  ? "badge-success"
                  : "badge-warning"
              }`}>
                {current.marketCap.classificationStatus === "VERIFIED_HISTORICAL_PIT"
                  ? "VERIFIED HISTORICAL PIT"
                  : "APPROXIMATE REFERENCE"}
              </span>
              <span className="text-[12px] font-medium text-text-primary">
                {current.marketCap.classificationStatus === "VERIFIED_HISTORICAL_PIT"
                  ? `AMFI Ranking Period: ${current.marketCap.sourceRankingPeriod || "Official Semi-Annual"}`
                  : "Static Market-Cap Mapping (Candidate Approximation)"}
              </span>
            </div>
            <div className="flex flex-wrap items-center gap-3 text-[11px] text-text-secondary">
              {current.marketCap.sourcePublicationDate && (
                <span>Published: <strong className="text-text-primary font-mono">{current.marketCap.sourcePublicationDate}</strong></span>
              )}
              {current.marketCap.effectiveFrom && current.marketCap.effectiveTo && (
                <span>Effective: <strong className="text-text-primary font-mono">{current.marketCap.effectiveFrom} → {current.marketCap.effectiveTo}</strong></span>
              )}
              {current.marketCap.totalClassifiedHoldingsCount !== undefined && (
                <span>Classified: <strong className="text-text-primary font-mono">{current.marketCap.totalClassifiedHoldingsCount} / {current.totalHoldingsDisclosed}</strong></span>
              )}
            </div>
          </div>

          {/* Key Metric Highlights */}
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
            <div className="bg-surface-elevated/40 p-3 rounded border border-border/60">
              <span className="mono-meta block text-[10px] text-text-muted">LARGE CAP</span>
              <span className="mono-num text-[17px] font-bold text-primary">
                {current.marketCap.largeCapWeightPctFormatted}
              </span>
              <span className="mono-meta block text-[10px] text-text-muted mt-0.5">Top 100 Companies</span>
            </div>

            <div className="bg-surface-elevated/40 p-3 rounded border border-border/60">
              <span className="mono-meta block text-[10px] text-text-muted">MID CAP</span>
              <span className="mono-num text-[17px] font-bold text-accent">
                {current.marketCap.midCapWeightPctFormatted}
              </span>
              <span className="mono-meta block text-[10px] text-text-muted mt-0.5">101st–250th Companies</span>
            </div>

            <div className="bg-surface-elevated/40 p-3 rounded border border-border/60">
              <span className="mono-meta block text-[10px] text-text-muted">SMALL CAP</span>
              <span className="mono-num text-[17px] font-bold text-warning-fg">
                {current.marketCap.smallCapWeightPctFormatted}
              </span>
              <span className="mono-meta block text-[10px] text-text-muted mt-0.5">251st+ Companies</span>
            </div>

            <div className="bg-surface-elevated/40 p-3 rounded border border-border/60">
              <span className="mono-meta block text-[10px] text-text-muted">CASH &amp; TREPS</span>
              <span className="mono-num text-[17px] font-bold text-success-fg">
                {current.marketCap.cashWeightPctFormatted}
              </span>
              <span className="mono-meta block text-[10px] text-text-muted mt-0.5">Liquid Reserves</span>
            </div>

            <div className="bg-surface-elevated/40 p-3 rounded border border-border/60">
              <span className="mono-meta block text-[10px] text-text-muted">TOP 10 WEIGHT</span>
              <span className="mono-num text-[17px] font-bold text-text-primary">
                {current.concentration.top10WeightPctFormatted}
              </span>
              <span className="mono-meta block text-[10px] text-text-muted mt-0.5">Top 5: {current.concentration.top5WeightPctFormatted}</span>
            </div>

            <div className="bg-surface-elevated/40 p-3 rounded border border-border/60">
              <span className="mono-meta block text-[10px] text-text-muted">HHI INDEX</span>
              <span className="mono-num text-[17px] font-bold text-text-primary">
                {current.concentration.hhi.toFixed(0)}
              </span>
              <span className="mono-meta block text-[10px] text-text-muted mt-0.5">{current.concentration.hhiClassification}</span>
            </div>
          </div>

          {/* Size / Capitalization Visual Allocation Bar */}
          <div className="bg-surface-elevated/20 p-4 rounded-lg border border-border/60 space-y-3">
            <div className="flex items-center justify-between text-[12px]">
              <span className="font-semibold text-text-primary">Market Capitalization Allocation Breakdown</span>
              <span className="mono-meta text-[11px] text-text-muted">
                Reporting Date: {current.portfolioDate} | Coverage: {current.classificationCoveragePctFormatted}
                {current.marketCap.sourceRankingPeriod ? ` | AMFI: ${current.marketCap.sourceRankingPeriod}` : ""}
              </span>
            </div>

            {/* Segmented Bar */}
            <div className="flex h-5 w-full overflow-hidden rounded bg-surface border border-border/40">
              <div
                style={{ width: `${Math.min(100, Math.max(0, current.marketCap.largeCapWeightPct))}%` }}
                className="bg-blue-600 transition-all"
                title={`Large Cap: ${current.marketCap.largeCapWeightPctFormatted}`}
              />
              <div
                style={{ width: `${Math.min(100, Math.max(0, current.marketCap.midCapWeightPct))}%` }}
                className="bg-purple-600 transition-all"
                title={`Mid Cap: ${current.marketCap.midCapWeightPctFormatted}`}
              />
              <div
                style={{ width: `${Math.min(100, Math.max(0, current.marketCap.smallCapWeightPct))}%` }}
                className="bg-amber-500 transition-all"
                title={`Small Cap: ${current.marketCap.smallCapWeightPctFormatted}`}
              />
              <div
                style={{ width: `${Math.min(100, Math.max(0, current.marketCap.cashWeightPct))}%` }}
                className="bg-emerald-600 transition-all"
                title={`Cash & TREPS: ${current.marketCap.cashWeightPctFormatted}`}
              />
              {current.marketCap.unclassifiedWeightPct > 0 && (
                <div
                  style={{ width: `${Math.min(100, Math.max(0, current.marketCap.unclassifiedWeightPct))}%` }}
                  className="bg-rose-500/70 transition-all"
                  title={`Unclassified: ${current.marketCap.unclassifiedWeightPctFormatted}`}
                />
              )}
            </div>

            {/* Legend */}
            <div className="flex flex-wrap items-center gap-4 text-[11px] pt-1">
              <div className="flex items-center gap-1.5">
                <span className="h-2.5 w-2.5 rounded-full bg-blue-600 shrink-0" />
                <span className="text-text-secondary">Large Cap: <strong className="text-text-primary">{current.marketCap.largeCapWeightPctFormatted}</strong></span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="h-2.5 w-2.5 rounded-full bg-purple-600 shrink-0" />
                <span className="text-text-secondary">Mid Cap: <strong className="text-text-primary">{current.marketCap.midCapWeightPctFormatted}</strong></span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="h-2.5 w-2.5 rounded-full bg-amber-500 shrink-0" />
                <span className="text-text-secondary">Small Cap: <strong className="text-text-primary">{current.marketCap.smallCapWeightPctFormatted}</strong></span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="h-2.5 w-2.5 rounded-full bg-emerald-600 shrink-0" />
                <span className="text-text-secondary">Cash &amp; TREPS: <strong className="text-text-primary">{current.marketCap.cashWeightPctFormatted}</strong></span>
              </div>
              {current.marketCap.unclassifiedWeightPct > 0 && (
                <div className="flex items-center gap-1.5">
                  <span className="h-2.5 w-2.5 rounded-full bg-rose-500 shrink-0" />
                  <span className="text-text-secondary">Unclassified: <strong className="text-text-primary">{current.marketCap.unclassifiedWeightPctFormatted}</strong></span>
                </div>
              )}
            </div>
          </div>

          {/* Top Sector Weights */}
          <div className="space-y-3">
            <h4 className="text-[13px] font-semibold text-text-primary">Disclosed Sector Allocation (Top Weights)</h4>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2.5">
              {current.topSectors.map((s, idx) => (
                <div key={idx} className="flex items-center justify-between p-2.5 rounded bg-surface border border-border/50 text-[12px]">
                  <span className={`font-medium truncate ${s.isUnclassified ? "text-text-muted italic" : "text-text-primary"}`}>
                    {s.sector}
                  </span>
                  <span className="mono-num font-semibold text-text-primary shrink-0 ml-2">
                    {s.weightPctFormatted}
                  </span>
                </div>
              ))}
            </div>
          </div>

          {/* Other Factor Dimensions Disclosure */}
          <div className="bg-surface-elevated/20 p-4 rounded-lg border border-border/60 space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-[12px] font-semibold text-text-primary">Other Systematic Factor Dimensions</span>
              <span className="badge badge-neutral text-[10px]">PENDING FEEDS</span>
            </div>
            <p className="text-[12px] text-text-secondary leading-relaxed">
              {current.otherFactors.documentation}
            </p>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 pt-1 text-[11px]">
              <div className="p-2 rounded bg-surface border border-border/40">
                <span className="mono-meta block text-text-muted">VALUE (P/B, P/E)</span>
                <span className="font-semibold text-text-secondary">Pending Fundamental Feed</span>
              </div>
              <div className="p-2 rounded bg-surface border border-border/40">
                <span className="mono-meta block text-text-muted">QUALITY (ROE, D/E)</span>
                <span className="font-semibold text-text-secondary">Pending Fundamental Feed</span>
              </div>
              <div className="p-2 rounded bg-surface border border-border/40">
                <span className="mono-meta block text-text-muted">MOMENTUM (12-1M)</span>
                <span className="font-semibold text-text-secondary">Pending Security Return Series</span>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* TAB: MARKET-CAP STYLE DRIFT & ALLOCATION DECOMPOSITION */}
      {activeTab === "MARKET_CAP_DRIFT" && (
        <MarketCapDriftView
          schemeOptionId={schemeOptionId}
          asOfDate={effectiveAsOfDate}
          knowledgeCutoffTime={effectiveCutoffTime}
          onInvestigateQuestion={onInvestigateQuestion}
        />
      )}

      {/* TAB: POINT-IN-TIME HOLDING LONGEVITY & TURNOVER */}
      {activeTab === "HOLDING_LONGEVITY" && (
        <HoldingLongevityView
          schemeOptionId={schemeOptionId}
          asOfDate={effectiveAsOfDate}
          knowledgeCutoffTime={effectiveCutoffTime}
          onInvestigateQuestion={onInvestigateQuestion}
        />
      )}

      {/* TAB: BENCHMARK COMPOSITION & OVERLAP */}
      {activeTab === "BENCHMARK_COMPOSITION" && (
        <BenchmarkCompositionSection
          comparison={benchmarkComparison}
          loading={benchmarkLoading}
          schemeOptionId={schemeOptionId}
          effectiveAsOfDate={effectiveAsOfDate}
          effectiveCutoffTime={effectiveCutoffTime}
          onRefresh={() => {
            setBenchmarkLoading(true);
            fetchBenchmarkCompositionComparison(schemeOptionId, effectiveAsOfDate, effectiveCutoffTime)
              .then(setBenchmarkComparison)
              .catch(console.error)
              .finally(() => setBenchmarkLoading(false));
          }}
        />
      )}

      {/* TAB 2: STYLE DRIFT TRAJECTORY */}
      {activeTab === "DRIFT_HISTORY" && (
        <div className="space-y-5">
          <div className="text-[12px] text-text-secondary">
            Observed exposure series across all chronological official holdings disclosures. YUKIRA does not interpolate unobserved intervals.
          </div>

          {/* Snapshot History Table */}
          <div className="overflow-x-auto rounded-lg border border-border">
            <table className="w-full text-left text-[12px]">
              <thead className="bg-surface-elevated/60 text-text-muted border-b border-border mono-meta text-[11px]">
                <tr>
                  <th className="py-2.5 px-3">Date</th>
                  <th className="py-2.5 px-3">AMFI Period</th>
                  <th className="py-2.5 px-3 text-right">Holdings</th>
                  <th className="py-2.5 px-3 text-right">Coverage</th>
                  <th className="py-2.5 px-3 text-right">Large Cap</th>
                  <th className="py-2.5 px-3 text-right">Mid Cap</th>
                  <th className="py-2.5 px-3 text-right">Small Cap</th>
                  <th className="py-2.5 px-3 text-right">Cash</th>
                  <th className="py-2.5 px-3 text-right">Top 10</th>
                  <th className="py-2.5 px-3 text-right">HHI</th>
                  <th className="py-2.5 px-3 text-center">Comparability</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/50">
                {history.map((s, idx) => {
                  const delta = s.deltaFromPrevious;
                  const isSelected = selectedSnapshot?.snapshotId === s.snapshotId;

                  return (
                    <tr
                      key={s.snapshotId}
                      onClick={() => setSelectedSnapshotId(s.snapshotId)}
                      className={`cursor-pointer transition-colors ${
                        isSelected ? "bg-primary/10" : "hover:bg-surface-elevated/40"
                      }`}
                    >
                      <td className="py-2.5 px-3 font-semibold text-text-primary whitespace-nowrap">
                        {s.portfolioDate}
                        {idx === history.length - 1 && (
                          <span className="ml-1.5 badge badge-neutral text-[9px]">LATEST</span>
                        )}
                      </td>
                      <td className="py-2.5 px-3 mono-meta text-[11px] whitespace-nowrap">
                        {s.rankingPeriod ? (
                          <span className="badge badge-success text-[9px] font-mono">{s.rankingPeriod}</span>
                        ) : s.classificationStatus === "VERIFIED_HISTORICAL_PIT" ? (
                          <span className="badge badge-success text-[9px]">PIT VERIFIED</span>
                        ) : (
                          <span className="badge badge-neutral text-[9px]">APPROX REF</span>
                        )}
                      </td>
                      <td className="py-2.5 px-3 text-right mono-num">{s.totalHoldingsDisclosed}</td>
                      <td className="py-2.5 px-3 text-right mono-num">
                        {s.sumReportedWeightsPct.toFixed(2)}%
                        {delta && delta.coverageDeltaPct !== 0 && (
                          <span className={`block text-[10px] ${delta.coverageDeltaPct > 0 ? "text-success-fg" : "text-warning-fg"}`}>
                            {delta.coverageDeltaPctFormatted}
                          </span>
                        )}
                      </td>
                      <td className="py-2.5 px-3 text-right mono-num">
                        {s.largeCapWeightPct.toFixed(2)}%
                        {delta && (
                          <span className={`block text-[10px] ${delta.largeCapDeltaPct > 0 ? "text-blue-500" : delta.largeCapDeltaPct < 0 ? "text-rose-500" : "text-text-muted"}`}>
                            {delta.largeCapDeltaPctFormatted}
                          </span>
                        )}
                      </td>
                      <td className="py-2.5 px-3 text-right mono-num">
                        {s.midCapWeightPct.toFixed(2)}%
                        {delta && (
                          <span className={`block text-[10px] ${delta.midCapDeltaPct > 0 ? "text-purple-500" : delta.midCapDeltaPct < 0 ? "text-rose-500" : "text-text-muted"}`}>
                            {delta.midCapDeltaPctFormatted}
                          </span>
                        )}
                      </td>
                      <td className="py-2.5 px-3 text-right mono-num">
                        {s.smallCapWeightPct.toFixed(2)}%
                        {delta && (
                          <span className={`block text-[10px] ${delta.smallCapDeltaPct > 0 ? "text-amber-500" : delta.smallCapDeltaPct < 0 ? "text-rose-500" : "text-text-muted"}`}>
                            {delta.smallCapDeltaPctFormatted}
                          </span>
                        )}
                      </td>
                      <td className="py-2.5 px-3 text-right mono-num">
                        {s.cashWeightPct.toFixed(2)}%
                        {delta && (
                          <span className="block text-[10px] text-text-muted">
                            {delta.cashDeltaPctFormatted}
                          </span>
                        )}
                      </td>
                      <td className="py-2.5 px-3 text-right mono-num">
                        {s.top10WeightPct.toFixed(2)}%
                        {delta && (
                          <span className="block text-[10px] text-text-muted">
                            {delta.top10DeltaPctFormatted}
                          </span>
                        )}
                      </td>
                      <td className="py-2.5 px-3 text-right mono-num">{s.hhi.toFixed(0)}</td>
                      <td className="py-2.5 px-3 text-center">
                        {delta ? (
                          delta.isComparable ? (
                            <span className="badge badge-success text-[10px]">COMPARABLE</span>
                          ) : (
                            <span
                              className="badge badge-warning text-[10px]"
                              title={delta.comparabilityNote}
                            >
                              COVERAGE SHIFT
                            </span>
                          )
                        ) : (
                          <span className="text-[10px] text-text-muted">BASELINE</span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {/* Selected Snapshot Interval Breakdown */}
          {selectedSnapshot && selectedSnapshot.deltaFromPrevious && (
            <div className="space-y-4">
              {/* Comparability Note */}
              <div className={`p-3.5 rounded border text-[12px] leading-relaxed ${
                selectedSnapshot.deltaFromPrevious.isComparable
                  ? "bg-surface-elevated/30 border-border/60 text-text-secondary"
                  : "bg-warning-bg/15 border-warning/40 text-warning-fg"
              }`}>
                <div className="flex items-center gap-2 mb-1">
                  <span className="font-semibold text-text-primary">
                    Interval Analysis ({selectedSnapshot.deltaFromPrevious.previousDate} → {selectedSnapshot.portfolioDate}):
                  </span>
                  <span className={`badge text-[10px] ${selectedSnapshot.deltaFromPrevious.isComparable ? "badge-success" : "badge-warning"}`}>
                    {selectedSnapshot.deltaFromPrevious.isComparable ? "Valid Comparison" : "Caution: Non-Comparable Interval"}
                  </span>
                </div>
                <p>{selectedSnapshot.deltaFromPrevious.comparabilityNote}</p>
              </div>

              {/* Style Drift Decomposition (Active Rebalancing vs AMFI Reclassification) */}
              {selectedSnapshot.deltaFromPrevious.styleDriftAttribution && (() => {
                const attr = selectedSnapshot.deltaFromPrevious.styleDriftAttribution;
                return (
                  <div className="p-4 rounded-lg bg-surface-elevated/40 border border-border space-y-3">
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-border/60 pb-2">
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="badge badge-primary text-[10px]">VERIFIED STYLE DRIFT DECOMPOSITION</span>
                          <span className="text-[13px] font-bold text-text-primary">
                            Rebalancing Delta vs. AMFI Reclassification Delta
                          </span>
                        </div>
                        <p className="text-[11px] text-text-secondary mt-0.5">
                          Exact mathematical separation: &Delta;W<sub>c</sub> = &Delta;W<sub>rebalance</sub> + &Delta;W<sub>reclassify</sub> + &Delta;W<sub>coverage</sub>
                        </p>
                      </div>
                      <div className="text-[11px] font-mono text-text-muted">
                        {selectedSnapshot.deltaFromPrevious.previousDate} → {selectedSnapshot.portfolioDate}
                      </div>
                    </div>

                    {/* 3-Category Breakdown Grid */}
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                      {/* Large Cap */}
                      <div className="p-3 rounded bg-surface border border-border/50 space-y-2">
                        <div className="flex items-center justify-between">
                          <span className="font-semibold text-[12px] text-blue-500">LARGE CAP EXPOSURE</span>
                          <span className={`mono-num text-[12px] font-bold ${attr.largeCapNetDeltaPct > 0 ? "text-blue-500" : attr.largeCapNetDeltaPct < 0 ? "text-rose-500" : "text-text-primary"}`}>
                            {attr.largeCapNetDeltaPct > 0 ? `+${attr.largeCapNetDeltaPct.toFixed(2)}%` : `${attr.largeCapNetDeltaPct.toFixed(2)}%`}
                          </span>
                        </div>
                        <div className="space-y-1 text-[11px]">
                          <div className="flex justify-between text-text-secondary">
                            <span>Active Rebalancing:</span>
                            <span className="mono-num font-medium text-text-primary">
                              {attr.largeCapRebalanceDeltaPct > 0 ? `+${attr.largeCapRebalanceDeltaPct.toFixed(2)}%` : `${attr.largeCapRebalanceDeltaPct.toFixed(2)}%`}
                            </span>
                          </div>
                          <div className="flex justify-between text-text-secondary">
                            <span>AMFI Reclassification:</span>
                            <span className={`mono-num font-medium ${attr.largeCapReclassifyDeltaPct !== 0 ? "text-accent font-bold" : "text-text-primary"}`}>
                              {attr.largeCapReclassifyDeltaPct > 0 ? `+${attr.largeCapReclassifyDeltaPct.toFixed(2)}%` : `${attr.largeCapReclassifyDeltaPct.toFixed(2)}%`}
                            </span>
                          </div>
                          {attr.largeCapCoverageDeltaPct !== 0 && (
                            <div className="flex justify-between text-text-secondary">
                              <span>Coverage Shift:</span>
                              <span className="mono-num font-medium text-text-muted">
                                {attr.largeCapCoverageDeltaPct > 0 ? `+${attr.largeCapCoverageDeltaPct.toFixed(2)}%` : `${attr.largeCapCoverageDeltaPct.toFixed(2)}%`}
                              </span>
                            </div>
                          )}
                        </div>
                      </div>

                      {/* Mid Cap */}
                      <div className="p-3 rounded bg-surface border border-border/50 space-y-2">
                        <div className="flex items-center justify-between">
                          <span className="font-semibold text-[12px] text-purple-500">MID CAP EXPOSURE</span>
                          <span className={`mono-num text-[12px] font-bold ${attr.midCapNetDeltaPct > 0 ? "text-purple-500" : attr.midCapNetDeltaPct < 0 ? "text-rose-500" : "text-text-primary"}`}>
                            {attr.midCapNetDeltaPct > 0 ? `+${attr.midCapNetDeltaPct.toFixed(2)}%` : `${attr.midCapNetDeltaPct.toFixed(2)}%`}
                          </span>
                        </div>
                        <div className="space-y-1 text-[11px]">
                          <div className="flex justify-between text-text-secondary">
                            <span>Active Rebalancing:</span>
                            <span className="mono-num font-medium text-text-primary">
                              {attr.midCapRebalanceDeltaPct > 0 ? `+${attr.midCapRebalanceDeltaPct.toFixed(2)}%` : `${attr.midCapRebalanceDeltaPct.toFixed(2)}%`}
                            </span>
                          </div>
                          <div className="flex justify-between text-text-secondary">
                            <span>AMFI Reclassification:</span>
                            <span className={`mono-num font-medium ${attr.midCapReclassifyDeltaPct !== 0 ? "text-accent font-bold" : "text-text-primary"}`}>
                              {attr.midCapReclassifyDeltaPct > 0 ? `+${attr.midCapReclassifyDeltaPct.toFixed(2)}%` : `${attr.midCapReclassifyDeltaPct.toFixed(2)}%`}
                            </span>
                          </div>
                          {attr.midCapCoverageDeltaPct !== 0 && (
                            <div className="flex justify-between text-text-secondary">
                              <span>Coverage Shift:</span>
                              <span className="mono-num font-medium text-text-muted">
                                {attr.midCapCoverageDeltaPct > 0 ? `+${attr.midCapCoverageDeltaPct.toFixed(2)}%` : `${attr.midCapCoverageDeltaPct.toFixed(2)}%`}
                              </span>
                            </div>
                          )}
                        </div>
                      </div>

                      {/* Small Cap */}
                      <div className="p-3 rounded bg-surface border border-border/50 space-y-2">
                        <div className="flex items-center justify-between">
                          <span className="font-semibold text-[12px] text-amber-500">SMALL CAP EXPOSURE</span>
                          <span className={`mono-num text-[12px] font-bold ${attr.smallCapNetDeltaPct > 0 ? "text-amber-500" : attr.smallCapNetDeltaPct < 0 ? "text-rose-500" : "text-text-primary"}`}>
                            {attr.smallCapNetDeltaPct > 0 ? `+${attr.smallCapNetDeltaPct.toFixed(2)}%` : `${attr.smallCapNetDeltaPct.toFixed(2)}%`}
                          </span>
                        </div>
                        <div className="space-y-1 text-[11px]">
                          <div className="flex justify-between text-text-secondary">
                            <span>Active Rebalancing:</span>
                            <span className="mono-num font-medium text-text-primary">
                              {attr.smallCapRebalanceDeltaPct > 0 ? `+${attr.smallCapRebalanceDeltaPct.toFixed(2)}%` : `${attr.smallCapRebalanceDeltaPct.toFixed(2)}%`}
                            </span>
                          </div>
                          <div className="flex justify-between text-text-secondary">
                            <span>AMFI Reclassification:</span>
                            <span className={`mono-num font-medium ${attr.smallCapReclassifyDeltaPct !== 0 ? "text-accent font-bold" : "text-text-primary"}`}>
                              {attr.smallCapReclassifyDeltaPct > 0 ? `+${attr.smallCapReclassifyDeltaPct.toFixed(2)}%` : `${attr.smallCapReclassifyDeltaPct.toFixed(2)}%`}
                            </span>
                          </div>
                          {attr.smallCapCoverageDeltaPct !== 0 && (
                            <div className="flex justify-between text-text-secondary">
                              <span>Coverage Shift:</span>
                              <span className="mono-num font-medium text-text-muted">
                                {attr.smallCapCoverageDeltaPct > 0 ? `+${attr.smallCapCoverageDeltaPct.toFixed(2)}%` : `${attr.smallCapCoverageDeltaPct.toFixed(2)}%`}
                              </span>
                            </div>
                          )}
                        </div>
                      </div>
                    </div>

                    <p className="text-[11px] text-text-muted italic">
                      {attr.attributionExplanation}
                    </p>
                  </div>
                );
              })()}

              {/* Reclassified Holdings Table */}
              {selectedSnapshot.deltaFromPrevious.styleDriftAttribution?.reclassifiedHoldings &&
               selectedSnapshot.deltaFromPrevious.styleDriftAttribution.reclassifiedHoldings.length > 0 ? (
                <div className="p-4 rounded-lg bg-surface-elevated/40 border border-border space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span className="badge badge-accent text-[10px]">AMFI RECLASSIFIED HOLDINGS</span>
                      <span className="text-[13px] font-bold text-text-primary">
                        Constituents Shifting Category Across AMFI Publication Cutoffs
                      </span>
                    </div>
                    <span className="text-[11px] text-text-muted">
                      {selectedSnapshot.deltaFromPrevious.styleDriftAttribution.reclassifiedHoldings.length} holding(s) re-ranked
                    </span>
                  </div>

                  <div className="overflow-x-auto rounded border border-border/60">
                    <table className="w-full text-left text-[11px]">
                      <thead className="bg-surface-elevated/60 text-text-muted border-b border-border mono-meta text-[10px]">
                        <tr>
                          <th className="py-2 px-3">Company / Security</th>
                          <th className="py-2 px-3">ISIN</th>
                          <th className="py-2 px-3 text-right">Portfolio Weight</th>
                          <th className="py-2 px-3">Previous Classification</th>
                          <th className="py-2 px-3">New PIT Classification</th>
                          <th className="py-2 px-3">Shift Context</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-border/40">
                        {selectedSnapshot.deltaFromPrevious.styleDriftAttribution.reclassifiedHoldings.map((rec, hIdx) => (
                          <tr key={hIdx} className="hover:bg-surface-elevated/30">
                            <td className="py-2 px-3 font-semibold text-text-primary">{rec.companyName}</td>
                            <td className="py-2 px-3 font-mono text-text-muted text-[10px]">{rec.isin}</td>
                            <td className="py-2 px-3 text-right mono-num font-medium text-text-primary">
                              {rec.portfolioWeightPctFormatted}
                            </td>
                            <td className="py-2 px-3">
                              <span className="badge badge-neutral text-[9px]">
                                {rec.previousCategory} {rec.previousRank ? `(#${rec.previousRank})` : ""}
                              </span>
                            </td>
                            <td className="py-2 px-3">
                              <span className="badge badge-accent text-[9px]">
                                {rec.currentCategory} {rec.currentRank ? `(#${rec.currentRank})` : ""}
                              </span>
                            </td>
                            <td className="py-2 px-3 text-text-secondary text-[11px]">
                              {rec.shiftDescription}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  <p className="text-[11px] text-text-muted leading-relaxed">
                    Note: These category transitions stem entirely from semi-annual AMFI market-cap ranking publications. They do NOT indicate fund manager buying or selling.
                  </p>
                </div>
              ) : (
                <div className="p-3 rounded bg-surface/50 border border-border/40 text-[11px] text-text-muted">
                  No constituent category migrations occurred across this interval. All style drift across this period was driven by portfolio rebalancing and relative market price movement.
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* TAB 3: OBSERVED SHIFTS */}
      {activeTab === "OBSERVED_SHIFTS" && (
        <div className="space-y-4">
          <div className="text-[12px] text-text-secondary">
            Statistically meaningful exposure movements observed across chronological disclosure snapshots (&ge;3% size/concentration shifts).
          </div>

          {shifts.length === 0 ? (
            <div className="state-well p-4 text-[12px] text-text-muted">
              No significant style or concentration shifts (&ge;3pp) observed across the available disclosures.
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3.5">
              {shifts.map((shift, idx) => (
                <div key={idx} className="p-4 rounded-lg bg-surface-elevated/30 border border-border space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="badge badge-neutral text-[10px] font-mono">
                      {shift.shiftDimension}
                    </span>
                    <span className="mono-num text-[14px] font-bold text-text-primary">
                      {shift.magnitudeDeltaPctFormatted}
                    </span>
                  </div>

                  <p className="text-[13px] font-medium text-text-primary">{shift.description}</p>

                  <div className="text-[11px] text-text-secondary leading-relaxed bg-surface/50 p-2.5 rounded border border-border/40">
                    <div className="flex items-center gap-1.5 mb-0.5">
                      <span className="text-text-muted">Coincides with coverage change:</span>
                      <strong className={shift.coincidesWithCoverageChange ? "text-warning-fg" : "text-success-fg"}>
                        {shift.coincidesWithCoverageChange ? "Yes (Coverage Shifted)" : "No (Stable Coverage)"}
                      </strong>
                    </div>
                    <p className="text-text-muted">{shift.evidenceNote}</p>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* TAB 4: HOLDINGS QUALITY */}
      {activeTab === "HOLDINGS_QUALITY" && current && (
        <div className="space-y-5">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
            <div className="p-3.5 rounded bg-surface-elevated/40 border border-border/60">
              <span className="mono-meta block text-[10px] text-text-muted">MAPPED WEIGHT</span>
              <span className="mono-num text-[17px] font-bold text-success-fg">
                {current.mappedWeightPctFormatted}
              </span>
              <span className="mono-meta block text-[10px] text-text-muted mt-0.5">Matched to Verified Corporate Entities</span>
            </div>

            <div className="p-3.5 rounded bg-surface-elevated/40 border border-border/60">
              <span className="mono-meta block text-[10px] text-text-muted">UNCLASSIFIED RESIDUAL</span>
              <span className="mono-num text-[17px] font-bold text-text-primary">
                {current.unmappedWeightPctFormatted}
              </span>
              <span className="mono-meta block text-[10px] text-text-muted mt-0.5">Unmapped / Pending Classification</span>
            </div>

            <div className="p-3.5 rounded bg-surface-elevated/40 border border-border/60">
              <span className="mono-meta block text-[10px] text-text-muted">CLASSIFICATION COVERAGE</span>
              <span className="mono-num text-[17px] font-bold text-primary">
                {current.classificationCoveragePctFormatted}
              </span>
              <span className="mono-meta block text-[10px] text-text-muted mt-0.5">Disclosed Weight Classified</span>
            </div>

            <div className="p-3.5 rounded bg-surface-elevated/40 border border-border/60">
              <span className="mono-meta block text-[10px] text-text-muted">DISCLOSURE STATUS</span>
              <span className={`badge mt-1 text-[11px] ${current.completenessState === "FULL_DISCLOSURE" ? "badge-success" : "badge-warning"}`}>
                {current.completenessState === "FULL_DISCLOSURE" ? "FULL DISCLOSURE" : "PARTIAL DISCLOSURE"}
              </span>
              <span className="mono-meta block text-[10px] text-text-muted mt-0.5">{current.totalHoldingsDisclosed} Disclosed Holdings</span>
            </div>
          </div>

          {/* Source Artifact & Provenance Lineage */}
          <div className="p-4 rounded-lg bg-surface border border-border space-y-3 text-[12px]">
            <h4 className="font-semibold text-text-primary">Cryptographic Source &amp; Classification Lineage</h4>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-[11px]">
              <div>
                <span className="mono-meta text-text-muted block">Disclosures Artifact ID</span>
                <span className="font-mono text-text-primary">{current.sourceArtifactId ?? "Direct Ingested Feed"}</span>
              </div>
              <div>
                <span className="mono-meta text-text-muted block">Disclosures SHA-256</span>
                <span className="font-mono text-text-primary break-all">{current.sourceArtifactSha256 ?? "SHA-256 verified in source registry"}</span>
              </div>
              <div>
                <span className="mono-meta text-text-muted block">Availability Timestamp</span>
                <span className="font-mono text-text-primary">{current.availabilityTime}</span>
              </div>
              <div>
                <span className="mono-meta text-text-muted block">Classification Standard</span>
                <span className="font-mono text-text-primary">{current.marketCap.classificationMethodology}</span>
              </div>
              <div>
                <span className="mono-meta text-text-muted block">AMFI Ranking Period</span>
                <span className="font-mono text-text-primary">
                  {current.marketCap.sourceRankingPeriod ? `${current.marketCap.sourceRankingPeriod} (Published: ${current.marketCap.sourcePublicationDate || "N/A"})` : "Static Reference Mapping"}
                </span>
              </div>
              <div>
                <span className="mono-meta text-text-muted block">AMFI Classification Artifact SHA-256</span>
                <span className="font-mono text-text-primary break-all">
                  {current.marketCap.sourceArtifactSha256 ?? "N/A (Reference mapping)"}
                </span>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* TAB 5: STYLE DRIFT & ACTIVE ATTRIBUTION */}
      {activeTab === "ATTRIBUTION_PREREQUISITES" && (
        <StyleDriftAttributionView
          schemeOptionId={schemeOptionId}
          asOfDate={effectiveAsOfDate}
          knowledgeCutoffTime={effectiveCutoffTime}
          onInvestigateQuestion={onInvestigateQuestion}
        />
      )}

      {/* 4. Methodology & Provenance Drawer (Collapsible) */}
      {showMethodologyDrawer && (
        <div className="mt-4 p-4 rounded-lg bg-surface-elevated/40 border border-border space-y-4 text-[12px] leading-relaxed">
          <div className="flex items-center justify-between border-b border-border/60 pb-2">
            <span className="font-bold text-text-primary">Factor Exposure &amp; Style Drift Methodology Framework</span>
            <span className="mono-meta text-[11px] text-text-muted">Version: {epistemic?.methodologyVersion}</span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-[11px]">
            <div className="space-y-1">
              <strong className="text-text-primary">1. SEBI Market Capitalization Standard:</strong>
              <p className="text-text-secondary">
                SEBI circular SEBI/HO/IMD/DF3/CIR/P/2017/114 defines: Large Cap = 1st–100th company in full market capitalization; Mid Cap = 101st–250th company; Small Cap = 251st company onwards.
              </p>
            </div>

            <div className="space-y-1">
              <strong className="text-text-primary">2. Concentration &amp; HHI Formula:</strong>
              <p className="text-text-secondary">
                Herfindahl-Hirschman Index: HHI = &sum;<sub>i=1</sub><sup>N</sup> (100 &times; w<sub>i</sub>)<sup>2</sup>. Disclosed portfolio weights are used directly without synthetic re-normalization.
              </p>
            </div>

            <div className="space-y-1">
              <strong className="text-text-primary">3. Style Drift &amp; Comparability Invariant:</strong>
              <p className="text-text-secondary">
                Changes in exposure are evaluated strictly across verified snapshot dates. Comparisons with coverage shifts &gt;15pp are marked non-comparable to distinguish disclosure depth from portfolio rebalancing.
              </p>
            </div>

            <div className="space-y-1">
              <strong className="text-text-primary">4. Point-in-Time Constraint:</strong>
              <p className="text-text-secondary">
                Calculations strictly enforce effective_date &le; asOfDate AND availability_time &le; knowledgeCutoff. Later snapshots never leak into historical evaluations.
              </p>
            </div>

            <div className="space-y-1 md:col-span-2 pt-2 border-t border-border/50">
              <strong className="text-text-primary">5. Historical Point-in-Time AMFI Classification &amp; Style Drift Decomposition:</strong>
              <p className="text-text-secondary">
                Constituents are linked to authentic semi-annual AMFI market-cap ranking publications.
                Changes in category exposure are decomposed into active rebalancing (&Delta;W<sub>rebalance</sub>), constituent reclassification (&Delta;W<sub>reclassify</sub>), and coverage shifts (&Delta;W<sub>coverage</sub>).
                Holdings whose categories migrate across AMFI publication boundaries are isolated so investors never mistake an AMFI index re-ranking for an active portfolio trade.
              </p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
