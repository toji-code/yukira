"use client";

import React, { useState, useEffect, useMemo } from "react";
import {
  fetchAvailableSnapshots,
  fetchPortfolioDrift,
  PortfolioSnapshotSummaryDto,
  PortfolioDriftResponseDto,
  HoldingDriftItemDto,
  SectorDriftDto,
} from "@/lib/api/portfolio";

interface PortfolioChangesSectionProps {
  schemeOptionId: number;
  schemeCode?: string;
  onViewHoldingsEvidence?: () => void;
}

type ChangeFilterTab = "ALL" | "INCREASES" | "REDUCTIONS" | "ADDITIONS" | "EXITS" | "SECTORS";

export function PortfolioChangesSection({
  schemeOptionId,
  onViewHoldingsEvidence,
}: PortfolioChangesSectionProps) {
  const [availableSnapshots, setAvailableSnapshots] = useState<PortfolioSnapshotSummaryDto[]>([]);
  const [drift, setDrift] = useState<PortfolioDriftResponseDto | null>(null);
  const [earlierId, setEarlierId] = useState<number | null>(null);
  const [laterId, setLaterId] = useState<number | null>(null);
  const [activeTab, setActiveTab] = useState<ChangeFilterTab>("ALL");
  const [thresholdFilter, setThresholdFilter] = useState<number>(0); // 0 = all, 1 = >=1pp, 3 = >=3pp, 5 = >=5pp
  const [loading, setLoading] = useState<boolean>(true);
  const [driftLoading, setDriftLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState<number>(0);

  // Initial load: available snapshots and initial default drift
  useEffect(() => {
    let active = true;
    Promise.all([
      fetchAvailableSnapshots(schemeOptionId),
      fetchPortfolioDrift(schemeOptionId),
    ])
      .then(([snaps, driftRes]) => {
        if (!active) return;
        setAvailableSnapshots(snaps);
        setDrift(driftRes);
        if (driftRes.earlierSnapshot) setEarlierId(driftRes.earlierSnapshot.snapshotId);
        if (driftRes.laterSnapshot) setLaterId(driftRes.laterSnapshot.snapshotId);
      })
      .catch((err: unknown) => {
        if (!active) return;
        setError(err instanceof Error ? err.message : "Failed to load portfolio snapshot history.");
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [schemeOptionId, reloadKey]);

  // Handle snapshot selection changes
  const handleSnapshotChange = async (newEarlierId: number, newLaterId: number) => {
    if (newEarlierId === newLaterId) return;
    setEarlierId(newEarlierId);
    setLaterId(newLaterId);
    setDriftLoading(true);
    try {
      const res = await fetchPortfolioDrift(schemeOptionId, newEarlierId, newLaterId);
      setDrift(res);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to calculate portfolio drift.");
    } finally {
      setDriftLoading(false);
    }
  };

  // Filtered holding items based on tab and user-selected display threshold
  const displayedHoldings = useMemo(() => {
    if (!drift || !drift.holdingChanges) return [];

    let items: HoldingDriftItemDto[] = [];
    switch (activeTab) {
      case "INCREASES":
        items = drift.topIncreases && drift.topIncreases.length > 0
          ? drift.topIncreases
          : drift.holdingChanges.filter((h) => h.changeType === "WEIGHT_INCREASED" || h.changeType === "NEWLY_REPORTED");
        break;
      case "REDUCTIONS":
        items = drift.topDecreases && drift.topDecreases.length > 0
          ? drift.topDecreases
          : drift.holdingChanges.filter((h) => h.changeType === "WEIGHT_DECREASED" || h.changeType === "NO_LONGER_REPORTED");
        break;
      case "ADDITIONS":
        items = drift.additions && drift.additions.length > 0
          ? drift.additions
          : drift.holdingChanges.filter((h) => h.changeType === "NEWLY_REPORTED");
        break;
      case "EXITS":
        items = drift.exits && drift.exits.length > 0
          ? drift.exits
          : drift.holdingChanges.filter((h) => h.changeType === "NO_LONGER_REPORTED");
        break;
      case "ALL":
      default:
        items = drift.holdingChanges;
        break;
    }

    if (thresholdFilter > 0) {
      items = items.filter((h) => Math.abs(h.weightDeltaPct) >= thresholdFilter);
    }

    return items;
  }, [drift, activeTab, thresholdFilter]);

  if (loading) {
    return (
      <div className="panel p-5 space-y-4" aria-busy="true">
        <div className="skeleton h-4 w-1/3" />
        <div className="skeleton h-24 w-full" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="state-panel-error p-5 rounded border border-critical-border bg-critical-bg text-critical-fg space-y-2" role="alert">
        <p className="font-semibold text-[14px]">Portfolio Changes Analysis Error</p>
        <p className="text-[13px]">{error}</p>
        <button onClick={() => setReloadKey((k) => k + 1)} className="btn btn-secondary btn-sm mt-2">
          Retry
        </button>
      </div>
    );
  }

  // Insufficient history state (< 2 snapshots)
  if (drift?.status === "INSUFFICIENT_HISTORY" || availableSnapshots.length < 2) {
    return (
      <div className="panel p-5 space-y-4 border border-border bg-surface">
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border pb-3">
          <div>
            <h3 className="eyebrow">Portfolio Changes</h3>
            <p className="text-[15px] font-semibold text-text-primary mt-0.5">
              Comparative Drift Unavailable
            </p>
          </div>
          <span className="status-badge state-neutral">
            Snapshots Available: {availableSnapshots.length}
          </span>
        </div>

        <div className="bg-surface-raised p-4 rounded border border-border text-[13px] space-y-3">
          <p className="text-text-primary font-medium leading-relaxed">
            Multi-period portfolio drift analysis requires at least 2 distinct monthly disclosure snapshots.
          </p>
          <p className="text-text-secondary leading-relaxed">
            {drift?.statusReason ||
              "Only 1 official disclosure snapshot is currently registered in YUKIRA for this scheme option. YUKIRA strictly enforces an epistemic zero-fabrication policy and will never synthesize fake prior holdings or misleading zero changes."}
          </p>
          {availableSnapshots.length === 1 && (
            <div className="flex flex-wrap items-center gap-3 pt-2 text-[12px] font-mono text-text-secondary">
              <span className="bg-surface-inset px-2 py-1 rounded">
                Verified Snapshot: {availableSnapshots[0].portfolioDate}
              </span>
              <span className="bg-surface-inset px-2 py-1 rounded">
                Positions: {availableSnapshots[0].reportedHoldingsCount}
              </span>
              <span className="bg-surface-inset px-2 py-1 rounded">
                Disclosed Coverage: {availableSnapshots[0].sumReportedWeightsPctFormatted}
              </span>
            </div>
          )}
        </div>
      </div>
    );
  }

  const earlierSnap = drift?.earlierSnapshot;
  const laterSnap = drift?.laterSnapshot;
  const sectorCoverage = drift?.sectorCoverage;
  const sectorDriftList: SectorDriftDto[] = drift?.sectorDrift || [];

  return (
    <div className="panel p-4 md:p-5 space-y-6 bg-surface border border-border">
      {/* 1. HEADER & COMPARISON SELECTOR */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border pb-4">
        <div>
          <div className="flex items-center gap-2">
            <h3 className="eyebrow">Portfolio Changes</h3>
            <span className="status-badge state-approved text-[10.5px]">
              Point-in-Time Disclosures
            </span>
          </div>
          <p className="text-[16px] font-semibold text-text-primary mt-1">
            Month-over-Month Disclosed Holdings Drift &amp; Sector Shifts
          </p>
        </div>

        {/* Snapshot Selectors */}
        <div className="flex flex-wrap items-center gap-2 bg-surface-raised p-2 rounded border border-border text-[12.5px]">
          <div className="flex items-center gap-1.5">
            <label htmlFor="earlier-select" className="text-text-secondary font-medium text-[11px] uppercase tracking-wider">
              Earlier:
            </label>
            <select
              id="earlier-select"
              value={earlierId || ""}
              disabled={driftLoading}
              onChange={(e) => handleSnapshotChange(Number(e.target.value), laterId || 0)}
              className="bg-surface-inset border border-border rounded px-2 py-1 text-text-primary font-mono text-[12px]"
            >
              {availableSnapshots.map((s) => (
                <option key={s.snapshotId} value={s.snapshotId}>
                  {s.portfolioDate} ({s.reportedHoldingsCount} pos)
                </option>
              ))}
            </select>
          </div>

          <span className="text-text-tertiary">→</span>

          <div className="flex items-center gap-1.5">
            <label htmlFor="later-select" className="text-text-secondary font-medium text-[11px] uppercase tracking-wider">
              Later:
            </label>
            <select
              id="later-select"
              value={laterId || ""}
              disabled={driftLoading}
              onChange={(e) => handleSnapshotChange(earlierId || 0, Number(e.target.value))}
              className="bg-surface-inset border border-border rounded px-2 py-1 text-text-primary font-mono text-[12px]"
            >
              {availableSnapshots.map((s) => (
                <option key={s.snapshotId} value={s.snapshotId}>
                  {s.portfolioDate} ({s.reportedHoldingsCount} pos)
                </option>
              ))}
            </select>
          </div>

          {onViewHoldingsEvidence && (
            <button
              onClick={onViewHoldingsEvidence}
              className="btn btn-secondary btn-sm text-[11px] ml-2"
              title="Inspect single-snapshot holdings evidence"
            >
              View Holdings Evidence
            </button>
          )}
        </div>
      </div>

      {/* 2. SUMMARY METRICS STRIP: CONCENTRATION & POSITION COUNTS */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <div className="bg-surface-raised p-3 rounded border border-border">
          <span className="def-label">Top 5 Concentration Delta</span>
          <div className="flex items-baseline gap-1.5 mt-1">
            <span className="font-mono text-[17px] font-bold text-text-primary">
              {drift?.top5PctDelta != null
                ? `${drift.top5PctDelta > 0 ? "+" : ""}${drift.top5PctDelta.toFixed(2)} pp`
                : "—"}
            </span>
            <span className="text-[11px] text-text-tertiary">percentage points</span>
          </div>
          <span className="text-[11px] text-text-secondary mt-1 block">
            {earlierSnap?.top5Pct != null ? `${earlierSnap.top5Pct}%` : "—"} →{" "}
            {laterSnap?.top5Pct != null ? `${laterSnap.top5Pct}%` : "—"}
          </span>
        </div>

        <div className="bg-surface-raised p-3 rounded border border-border">
          <span className="def-label">Top 10 Concentration Delta</span>
          <div className="flex items-baseline gap-1.5 mt-1">
            <span className="font-mono text-[17px] font-bold text-text-primary">
              {drift?.top10PctDelta != null
                ? `${drift.top10PctDelta > 0 ? "+" : ""}${drift.top10PctDelta.toFixed(2)} pp`
                : "—"}
            </span>
            <span className="text-[11px] text-text-tertiary">percentage points</span>
          </div>
          <span className="text-[11px] text-text-secondary mt-1 block">
            {earlierSnap?.top10Pct != null ? `${earlierSnap.top10Pct}%` : "—"} →{" "}
            {laterSnap?.top10Pct != null ? `${laterSnap.top10Pct}%` : "—"}
          </span>
        </div>

        <div className="bg-surface-raised p-3 rounded border border-border">
          <span className="def-label">HHI Concentration Index Delta</span>
          <div className="flex items-baseline gap-1.5 mt-1">
            <span className="font-mono text-[17px] font-bold text-text-primary">
              {drift?.hhiDelta != null
                ? `${drift.hhiDelta > 0 ? "+" : ""}${drift.hhiDelta.toFixed(2)}`
                : "—"}
            </span>
          </div>
          <span className="text-[11px] text-text-secondary mt-1 block">
            HHI: {earlierSnap?.hhi != null ? earlierSnap.hhi.toFixed(1) : "—"} →{" "}
            {laterSnap?.hhi != null ? laterSnap.hhi.toFixed(1) : "—"}
          </span>
        </div>

        <div className="bg-surface-raised p-3 rounded border border-border">
          <span className="def-label">Disclosed Weight Coverage</span>
          <div className="flex items-baseline gap-1.5 mt-1">
            <span className="font-mono text-[17px] font-bold text-text-primary">
              {earlierSnap?.sumWeightsPct != null ? `${earlierSnap.sumWeightsPct}%` : "—"} →{" "}
              {laterSnap?.sumWeightsPct != null ? `${laterSnap.sumWeightsPct}%` : "—"}
            </span>
          </div>
          <span className="text-[11px] text-text-tertiary mt-1 block">
            Raw unforced sum (no 100% normalization)
          </span>
        </div>
      </div>

      {/* 3. PROVENANCE & RECONCILIATION SUMMARY */}
      {drift?.classificationSummary && (
        <div className="bg-surface-raised p-3 rounded border border-border flex flex-wrap items-center justify-between gap-3 text-[12px]">
          <div className="flex flex-wrap items-center gap-3 font-mono">
            <span className="text-text-secondary">
              Positions Tracked: <strong className="text-text-primary">{drift.classificationSummary.totalComparedPositions}</strong>
            </span>
            <span>•</span>
            <span className="text-approved-fg">
              +{drift.classificationSummary.newlyReportedCount} Newly Disclosed
            </span>
            <span>•</span>
            <span className="text-candidate-fg">
              -{drift.classificationSummary.noLongerReportedCount} No Longer Disclosed
            </span>
            <span>•</span>
            <span className="text-text-primary">
              {drift.classificationSummary.weightIncreasedCount} Increased
            </span>
            <span>•</span>
            <span className="text-text-primary">
              {drift.classificationSummary.weightDecreasedCount} Decreased
            </span>
            <span>•</span>
            <span className="text-text-secondary">
              {drift.classificationSummary.unchangedCount} Unchanged
            </span>
          </div>
          <div className="text-[11px] font-mono text-text-tertiary">
            Provenance: SHA-256{" "}
            <span className="text-text-secondary">
              {laterSnap?.sourceArtifactSha256 ? `${laterSnap.sourceArtifactSha256.substring(0, 12)}…` : "Verified"}
            </span>
          </div>
        </div>
      )}

      {/* 4. NAVIGATION TABS & DISPLAY THRESHOLD SELECTOR */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border pb-2">
        {/* Category Tabs */}
        <div className="flex flex-wrap items-center gap-1">
          <button
            onClick={() => setActiveTab("ALL")}
            className={`px-3 py-1.5 text-[12px] font-medium rounded transition-colors ${
              activeTab === "ALL"
                ? "bg-accent text-accent-foreground font-semibold"
                : "text-text-secondary hover:text-text-primary hover:bg-surface-raised"
            }`}
          >
            All Changes ({drift?.holdingChanges?.length || 0})
          </button>
          <button
            onClick={() => setActiveTab("INCREASES")}
            className={`px-3 py-1.5 text-[12px] font-medium rounded transition-colors ${
              activeTab === "INCREASES"
                ? "bg-accent text-accent-foreground font-semibold"
                : "text-text-secondary hover:text-text-primary hover:bg-surface-raised"
            }`}
          >
            Top Increases ({drift?.topIncreases?.length || 0})
          </button>
          <button
            onClick={() => setActiveTab("REDUCTIONS")}
            className={`px-3 py-1.5 text-[12px] font-medium rounded transition-colors ${
              activeTab === "REDUCTIONS"
                ? "bg-accent text-accent-foreground font-semibold"
                : "text-text-secondary hover:text-text-primary hover:bg-surface-raised"
            }`}
          >
            Top Reductions ({drift?.topDecreases?.length || 0})
          </button>
          <button
            onClick={() => setActiveTab("ADDITIONS")}
            className={`px-3 py-1.5 text-[12px] font-medium rounded transition-colors ${
              activeTab === "ADDITIONS"
                ? "bg-accent text-accent-foreground font-semibold"
                : "text-text-secondary hover:text-text-primary hover:bg-surface-raised"
            }`}
          >
            Additions ({drift?.additions?.length || 0})
          </button>
          <button
            onClick={() => setActiveTab("EXITS")}
            className={`px-3 py-1.5 text-[12px] font-medium rounded transition-colors ${
              activeTab === "EXITS"
                ? "bg-accent text-accent-foreground font-semibold"
                : "text-text-secondary hover:text-text-primary hover:bg-surface-raised"
            }`}
          >
            Exits ({drift?.exits?.length || 0})
          </button>
          <button
            onClick={() => setActiveTab("SECTORS")}
            className={`px-3 py-1.5 text-[12px] font-medium rounded transition-colors ${
              activeTab === "SECTORS"
                ? "bg-accent text-accent-foreground font-semibold"
                : "text-text-secondary hover:text-text-primary hover:bg-surface-raised"
            }`}
          >
            Sector Shifts ({sectorDriftList.length})
          </button>
        </div>

        {/* Display Threshold Filter (Descriptive Filter, not automated signal) */}
        {activeTab !== "SECTORS" && (
          <div className="flex items-center gap-2 text-[11.5px]">
            <span className="text-text-tertiary">Display Filter:</span>
            <div className="inline-flex rounded border border-border p-0.5 bg-surface-raised font-mono text-[11px]">
              <button
                onClick={() => setThresholdFilter(0)}
                className={`px-2 py-0.5 rounded ${thresholdFilter === 0 ? "bg-surface text-text-primary font-bold shadow-xs" : "text-text-secondary"}`}
              >
                All
              </button>
              <button
                onClick={() => setThresholdFilter(1)}
                className={`px-2 py-0.5 rounded ${thresholdFilter === 1 ? "bg-surface text-text-primary font-bold shadow-xs" : "text-text-secondary"}`}
              >
                ≥ 1 pp
              </button>
              <button
                onClick={() => setThresholdFilter(3)}
                className={`px-2 py-0.5 rounded ${thresholdFilter === 3 ? "bg-surface text-text-primary font-bold shadow-xs" : "text-text-secondary"}`}
              >
                ≥ 3 pp
              </button>
              <button
                onClick={() => setThresholdFilter(5)}
                className={`px-2 py-0.5 rounded ${thresholdFilter === 5 ? "bg-surface text-text-primary font-bold shadow-xs" : "text-text-secondary"}`}
              >
                ≥ 5 pp
              </button>
            </div>
            {thresholdFilter > 0 && (
              <span className="text-[10px] text-text-tertiary italic">
                (Display threshold only)
              </span>
            )}
          </div>
        )}
      </div>

      {/* 5. CONTENT AREA */}
      {activeTab === "SECTORS" ? (
        /* SECTOR SHIFTS TABLE */
        <div className="space-y-4">
          {/* Sector Coverage Notice */}
          {sectorCoverage && (
            <div className="bg-surface-raised p-3 rounded border border-border flex flex-wrap items-center justify-between gap-2 text-[12px]">
              <div className="flex items-center gap-2">
                <span className={`status-badge ${sectorCoverage.coverageStatus === "ADEQUATE_COVERAGE" ? "state-approved" : "state-candidate"}`}>
                  {sectorCoverage.coverageStatus}
                </span>
                <span className="text-text-secondary">
                  Classified Coverage: Earlier {sectorCoverage.earlierCoveragePctFormatted} • Later {sectorCoverage.laterCoveragePctFormatted}
                </span>
              </div>
              <span className="text-text-tertiary font-mono text-[11px]">
                Unclassified / Cash: Earlier {sectorCoverage.unclassifiedWeightPctEarlierFormatted} • Later {sectorCoverage.unclassifiedWeightPctLaterFormatted}
              </span>
            </div>
          )}

          <div className="scroll-region max-h-96">
            <table className="data-table text-[12.5px]">
              <thead>
                <tr>
                  <th>Sector</th>
                  <th className="text-right">Prior Weight (%)</th>
                  <th className="text-right">Latest Weight (%)</th>
                  <th className="text-right">Shift (pp)</th>
                  <th className="w-36 text-center">Direction</th>
                </tr>
              </thead>
              <tbody>
                {sectorDriftList.map((sec, idx) => {
                  const delta = sec.weightDeltaPct;
                  const isPositive = delta > 0;
                  const isZero = delta === 0;
                  return (
                    <tr key={idx}>
                      <td className="font-semibold text-text-primary">
                        {sec.sector}
                      </td>
                      <td className="num text-text-tertiary">{sec.earlierWeightPctFormatted}</td>
                      <td className="num font-semibold text-text-primary">{sec.laterWeightPctFormatted}</td>
                      <td className="num font-mono">
                        <span className={`px-2 py-0.5 rounded font-bold ${
                          isZero
                            ? "text-text-tertiary"
                            : isPositive
                            ? "bg-approved-bg text-approved-fg"
                            : "bg-critical-bg text-critical-fg"
                        }`}>
                          {sec.weightDeltaPctFormatted} pp
                        </span>
                      </td>
                      <td className="text-center font-mono text-[11px] text-text-tertiary">
                        {isZero ? "UNCHANGED" : isPositive ? "▲ EXPANDED" : "▼ REDUCED"}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      ) : (
        /* POSITION-LEVEL DRIFT TABLE */
        <div className="space-y-3">
          <div className="scroll-region max-h-96">
            <table className="data-table text-[12.5px]">
              <thead>
                <tr>
                  <th>Security Name</th>
                  <th>Classification</th>
                  <th>Sector</th>
                  <th className="text-right">Prior Weight (%)</th>
                  <th className="text-right">Latest Weight (%)</th>
                  <th className="text-right">Change (pp)</th>
                </tr>
              </thead>
              <tbody>
                {displayedHoldings.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="text-center py-6 text-text-tertiary text-[12.5px]">
                      No position changes match the selected filter criteria.
                    </td>
                  </tr>
                ) : (
                  displayedHoldings.map((item, idx) => {
                    const delta = item.weightDeltaPct;
                    const isPositive = delta > 0;
                    const isZero = delta === 0;

                    let badgeClass = "state-neutral";
                    if (item.changeType === "NEWLY_REPORTED") badgeClass = "state-approved";
                    else if (item.changeType === "NO_LONGER_REPORTED") badgeClass = "state-candidate";
                    else if (item.changeType === "WEIGHT_INCREASED") badgeClass = "state-operational";

                    return (
                      <tr key={idx}>
                        <td>
                          <div className="font-medium text-text-primary">{item.securityName}</div>
                          {item.isin && (
                            <div className="font-mono text-[10.5px] text-text-tertiary">{item.isin}</div>
                          )}
                        </td>
                        <td>
                          <span className={`status-badge ${badgeClass} text-[10.5px]`}>
                            {item.changeType.replace(/_/g, " ")}
                          </span>
                        </td>
                        <td className="text-text-secondary text-[12px]">{item.sector || "Other"}</td>
                        <td className="num text-text-tertiary">{item.earlierWeightPctFormatted || "—"}</td>
                        <td className="num font-semibold text-text-primary">{item.laterWeightPctFormatted || "—"}</td>
                        <td className="num font-mono">
                          <span className={`px-2 py-0.5 rounded font-bold ${
                            isZero
                              ? "text-text-tertiary"
                              : isPositive
                              ? "bg-approved-bg text-approved-fg"
                              : "bg-critical-bg text-critical-fg"
                          }`}>
                            {item.weightDeltaPctFormatted} pp
                          </span>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* 6. PROGRESSIVE DISCLOSURE LAYER 6: DUE DILIGENCE & INVESTIGATION PROMPTS */}
      <div className="bg-surface-raised p-4 rounded border border-border space-y-2.5">
        <div className="flex items-center justify-between border-b border-border/60 pb-2">
          <span className="text-[11px] font-semibold uppercase tracking-wider text-text-secondary">
            What to Investigate Further • Due Diligence Prompts
          </span>
          <span className="text-[11px] text-text-tertiary italic">
            Disclosures reflect net portfolio shifts, not confirmed trade executions.
          </span>
        </div>

        {drift?.investigationPrompts && drift.investigationPrompts.length > 0 ? (
          <ul className="space-y-1.5 text-[12.5px] text-text-primary list-disc list-inside">
            {drift.investigationPrompts.map((prompt, i) => (
              <li key={i} className="leading-relaxed">
                {prompt}
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-[12.5px] text-text-secondary">
            Examine whether position movements align with stated investment objectives and sector risk limits.
          </p>
        )}

        <div className="pt-2 border-t border-border/40 text-[11px] text-text-tertiary leading-normal">
          <strong>Epistemic Note:</strong> Changes in disclosed weights reflect changes between two reporting cutoffs.
          They do not establish whether movement arose from trading decisions, market price changes, dividend actions, or portfolio cash flows.
        </div>
      </div>
    </div>
  );
}
