"use client";

import React, { useState, useEffect, useMemo } from "react";
import {
  ActiveShareTrajectoryResponseDto,
  ActiveShareObservationDto,
  ActiveShareConstituentInputDto,
  ActiveShareChangeBreakdownDto,
  fetchActiveShareTrajectory,
  BenchmarkFactsheetArchiveResponseDto,
  fetchBenchmarkFactsheetArchive,
} from "@/lib/api/portfolio";

interface ActiveShareTrajectoryViewProps {
  schemeOptionId: number;
  benchmarkCode?: string;
  effectiveAsOfDate?: string;
  effectiveCutoffTime?: string;
  onRefresh?: () => void;
}

type ChangeFilter =
  | "ALL"
  | "PORTFOLIO_WEIGHT_CHANGE"
  | "BENCHMARK_MEMBERSHIP_CHANGE"
  | "CLASSIFICATION_CHANGE"
  | "COVERAGE_IDENTITY_CHANGE"
  | "UNRESOLVED_CHANGE";

type ConstituentFilter = "ALL" | "OFF_BENCHMARK" | "IN_BENCHMARK_WEIGHTED" | "IN_BENCHMARK_UNWEIGHTED";

export function ActiveShareTrajectoryView({
  schemeOptionId,
  benchmarkCode,
  effectiveAsOfDate,
  effectiveCutoffTime,
  onRefresh,
}: ActiveShareTrajectoryViewProps) {
  const [data, setData] = useState<ActiveShareTrajectoryResponseDto | null>(null);
  const [archiveData, setArchiveData] = useState<BenchmarkFactsheetArchiveResponseDto | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [archiveLoading, setArchiveLoading] = useState<boolean>(false);
  const [archiveExpanded, setArchiveExpanded] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Selected observation for constituent inspection
  const [selectedObsDate, setSelectedObsDate] = useState<string | null>(null);

  // Filtering states
  const [changeFilter, setChangeFilter] = useState<ChangeFilter>("ALL");
  const [constituentFilter, setConstituentFilter] = useState<ConstituentFilter>("ALL");
  const [constituentSearch, setConstituentSearch] = useState<string>("");

  useEffect(() => {
    let isMounted = true;
    Promise.resolve().then(() => {
      if (!isMounted) return;
      setLoading(true);
      setError(null);
      setArchiveLoading(true);
    });

    fetchActiveShareTrajectory(
      schemeOptionId,
      effectiveAsOfDate,
      undefined,
      undefined,
      effectiveCutoffTime
    )
      .then((res) => {
        if (!isMounted) return;
        setData(res);
        if (res.observations && res.observations.length > 0) {
          // Default to latest observation
          setSelectedObsDate(res.observations[res.observations.length - 1].portfolioDate);
        }
      })
      .catch((err) => {
        if (!isMounted) return;
        setError(err.message || "Failed to load active share trajectory");
      })
      .finally(() => {
        if (isMounted) setLoading(false);
      });

    // Also fetch authoritative benchmark factsheet archive
    fetchBenchmarkFactsheetArchive(benchmarkCode || "NIFTY_500_TRI", effectiveCutoffTime)
      .then((res) => {
        if (!isMounted) return;
        setArchiveData(res);
      })
      .catch((err) => {
        console.warn("Failed to load benchmark factsheet archive:", err);
      })
      .finally(() => {
        if (isMounted) setArchiveLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, [schemeOptionId, benchmarkCode, effectiveAsOfDate, effectiveCutoffTime]);

  const selectedObservation = useMemo<ActiveShareObservationDto | null>(() => {
    if (!data || !data.observations || data.observations.length === 0) return null;
    if (!selectedObsDate) return data.observations[data.observations.length - 1];
    return data.observations.find((o) => o.portfolioDate === selectedObsDate) || data.observations[data.observations.length - 1];
  }, [data, selectedObsDate]);

  // Filtered Apparent Changes across 5 categories
  const filteredChanges = useMemo<ActiveShareChangeBreakdownDto[]>(() => {
    if (!data?.apparentChanges) return [];
    if (changeFilter === "ALL") return data.apparentChanges;
    return data.apparentChanges.filter((c) => c.changeCategory === changeFilter);
  }, [data, changeFilter]);

  // Filtered Constituent Inputs
  const filteredConstituents = useMemo<ActiveShareConstituentInputDto[]>(() => {
    if (!selectedObservation?.constituentInputs) return [];
    const query = constituentSearch.trim().toLowerCase();

    return selectedObservation.constituentInputs.filter((c) => {
      // Filter category
      if (constituentFilter === "OFF_BENCHMARK" && c.isBenchmarkMember) return false;
      if (constituentFilter === "IN_BENCHMARK_WEIGHTED" && (!c.isBenchmarkMember || c.benchmarkWeightPct == null)) return false;
      if (constituentFilter === "IN_BENCHMARK_UNWEIGHTED" && (!c.isBenchmarkMember || c.benchmarkWeightPct != null)) return false;

      // Text search
      if (query) {
        const matchesName = c.securityName.toLowerCase().includes(query);
        const matchesIsin = c.isin.toLowerCase().includes(query);
        return matchesName || matchesIsin;
      }
      return true;
    });
  }, [selectedObservation, constituentFilter, constituentSearch]);

  if (loading) {
    return (
      <div className="panel space-y-4 p-5 md:p-6" aria-busy="true">
        <div className="flex items-center justify-between border-b border-border/60 pb-3">
          <div className="skeleton h-5 w-1/3" />
          <div className="skeleton h-4 w-1/4" />
        </div>
        <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
          <div className="skeleton h-24 w-full" />
          <div className="skeleton h-24 w-full" />
          <div className="skeleton h-24 w-full" />
          <div className="skeleton h-24 w-full" />
        </div>
        <div className="skeleton h-64 w-full" />
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="panel p-6 text-center space-y-3">
        <div className="text-danger text-2xl font-bold">⚠️</div>
        <h3 className="text-sm font-semibold text-text-primary">Failed to Load Active Share Diagnostics</h3>
        <p className="text-xs text-text-secondary max-w-md mx-auto">
          {error || "An unexpected error occurred while communicating with the quantitative verification engine."}
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

  const isGated = data.overallStatus === "GATED_INCOMPLETE_BENCHMARK_WEIGHTS";
  const isPartial = data.overallStatus === "VERIFIED_PARTIAL";
  const isComplete = data.overallStatus === "VERIFIED_COMPLETE";

  return (
    <div className="space-y-6">
      {/* 1. Prominent Epistemic Status Banner */}
      <div
        className={`panel p-4 md:p-5 border-l-4 ${
          isGated
            ? "border-l-warning bg-warning/5"
            : isPartial
            ? "border-l-accent bg-accent/5"
            : isComplete
            ? "border-l-success bg-success/5"
            : "border-l-border bg-surface-elevated/40"
        }`}
      >
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <span className="text-xs font-bold uppercase tracking-wider text-text-secondary">
                Governance Status:
              </span>
              <span
                className={`badge text-[11px] font-bold uppercase tracking-wider ${
                  isGated
                    ? "badge-warning"
                    : isPartial
                    ? "badge-accent"
                    : isComplete
                    ? "badge-success"
                    : "badge-neutral"
                }`}
              >
                {data.overallStatus.replace(/_/g, " ")}
              </span>
              <span className="text-[11px] font-mono text-text-muted">
                Run: {data.calculationRunId.slice(0, 8)} | {data.methodologyVersion}
              </span>
            </div>
            <p className="text-xs text-text-secondary leading-relaxed max-w-4xl">
              {data.statusReason}
            </p>
          </div>

          {onRefresh && (
            <button
              onClick={onRefresh}
              className="text-[11px] text-primary hover:underline font-mono self-start sm:self-auto shrink-0"
            >
              ↻ Re-verify PIT
            </button>
          )}
        </div>

        {/* Missing Data Requirements Box for Gated State */}
        {data.missingDataRequirements && data.missingDataRequirements.length > 0 && (
          <div className="mt-3 pt-3 border-t border-border/40 text-[11px] text-text-secondary">
            <span className="font-semibold text-warning">Requirements to Unlock Definitive Active Share:</span>
            <ul className="list-disc list-inside mt-1 space-y-0.5 text-text-muted font-mono">
              {data.missingDataRequirements.map((req, idx) => (
                <li key={idx}>{req}</li>
              ))}
            </ul>
          </div>
        )}
      </div>

      {/* 2. Top Metric Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4">
        {/* Active Share Card */}
        <div className="panel p-4 space-y-1.5 border-l-4 border-l-primary">
          <span className="mono-meta text-[11px] text-text-secondary uppercase tracking-wider">
            Point-in-Time Active Share
          </span>
          <div className="flex items-baseline gap-2">
            {selectedObservation?.activeSharePct != null ? (
              <span className="text-2xl font-bold font-mono text-text-primary">
                {selectedObservation.activeSharePct.toFixed(2)}%
              </span>
            ) : (
              <span className="text-xl font-bold font-mono text-warning">
                GATED
              </span>
            )}
            <span className="text-[11px] text-text-secondary">
              vs {data.benchmarkCode}
            </span>
          </div>
          <div className="text-[11px] text-text-muted">
            {selectedObservation?.activeShareLowerBoundPct != null ? (
              <span>
                Verified Lower Bound:{" "}
                <strong className="text-text-primary font-mono">
                  ≥ {selectedObservation.activeShareLowerBoundPct.toFixed(2)}%
                </strong>
              </span>
            ) : (
              <span>Incomplete benchmark tail</span>
            )}
          </div>
        </div>

        {/* Off-Benchmark Allocation */}
        <div className="panel p-4 space-y-1.5 border-l-4 border-l-accent">
          <span className="mono-meta text-[11px] text-text-secondary uppercase tracking-wider">
            Off-Benchmark Allocation
          </span>
          <div className="flex items-baseline gap-2">
            <span className="text-2xl font-bold font-mono text-text-primary">
              {selectedObservation?.offBenchmarkAllocationPct != null
                ? `${selectedObservation.offBenchmarkAllocationPct.toFixed(2)}%`
                : "—"}
            </span>
            <span className="text-[11px] text-text-secondary">of fund capital</span>
          </div>
          <div className="text-[11px] text-text-muted">
            Constituents outside {data.benchmarkCode}
          </div>
        </div>

        {/* Benchmark Weight Coverage */}
        <div className="panel p-4 space-y-1.5 border-l-4 border-l-warning">
          <span className="mono-meta text-[11px] text-text-secondary uppercase tracking-wider">
            Benchmark Weight Coverage
          </span>
          <div className="flex items-baseline gap-2">
            <span className="text-2xl font-bold font-mono text-text-primary">
              {selectedObservation?.benchmarkWeightCoveragePct != null
                ? `${selectedObservation.benchmarkWeightCoveragePct.toFixed(2)}%`
                : "—"}
            </span>
            <span className="text-[11px] text-text-secondary">disclosed</span>
          </div>
          <div className="text-[11px] text-text-muted">
            {selectedObservation?.benchmarkConstituentsCount} index members (top-heavyweight tail)
          </div>
        </div>

        {/* Temporal Compatibility & PIT Cutoff */}
        <div className="panel p-4 space-y-1.5 border-l-4 border-l-border">
          <span className="mono-meta text-[11px] text-text-secondary uppercase tracking-wider">
            Point-in-Time Cutoffs
          </span>
          <div className="text-xs space-y-1">
            <div className="flex justify-between">
              <span className="text-text-muted">Fund Date:</span>
              <span className="font-mono text-text-primary">{selectedObservation?.portfolioDate || "—"}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-text-muted">BM Date:</span>
              <span className="font-mono text-text-primary">{selectedObservation?.benchmarkDate || "—"}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-text-muted">Date Gap:</span>
              <span className={`font-mono ${
                (selectedObservation?.snapshotDateDiffDays ?? 0) <= 45 ? "text-success font-semibold" : "text-warning font-semibold"
              }`}>
                {selectedObservation?.snapshotDateDiffDays} days{" "}
                {(selectedObservation?.snapshotDateDiffDays ?? 0) <= 45 ? "(≤45d)" : "(>45d mismatch)"}
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* 2b. Point-in-Time Benchmark Factsheet Archive & Coverage Audit Panel */}
      <div className="panel p-5 space-y-4 border border-border/80">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-border/60 pb-3">
          <div>
            <div className="flex items-center gap-2">
              <h4 className="text-sm font-semibold text-text-primary">
                Point-in-Time Benchmark Factsheet Archive &amp; Coverage Audit
              </h4>
              <span className="badge badge-primary text-[10px] font-bold">
                {archiveData?.totalSnapshotsArchived || 0} AUTHENTIC FACTSHEETS
              </span>
            </div>
            <p className="text-xs text-text-secondary mt-0.5">
              Auditable archive of official NSE Indices constituent publications. Enforces 5 independent coverage dimensions without synthetic tail fabrication.
            </p>
          </div>
          <button
            onClick={() => setArchiveExpanded(!archiveExpanded)}
            className="px-2.5 py-1 text-xs rounded border border-border bg-surface-elevated hover:bg-surface-elevated/80 text-text-secondary transition-colors self-start sm:self-auto shrink-0 font-mono"
          >
            {archiveExpanded ? "▲ Hide Factsheet Ledger" : "▼ Inspect Archived Factsheets"}
          </button>
        </div>

        {archiveExpanded && (
          <div className="space-y-4">
            {/* Archive Ledger Table */}
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs border-collapse font-mono">
                <thead>
                  <tr className="border-b border-border text-text-secondary font-medium uppercase tracking-wider text-[10px]">
                    <th className="py-2.5 px-3">Effective Date</th>
                    <th className="py-2.5 px-3">Availability Time (PIT)</th>
                    <th className="py-2.5 px-3">Publisher</th>
                    <th className="py-2.5 px-3 text-right">Members</th>
                    <th className="py-2.5 px-3 text-right">Mapped ISINs</th>
                    <th className="py-2.5 px-3 text-right">Disclosed Weight</th>
                    <th className="py-2.5 px-3">Weight Status</th>
                    <th className="py-2.5 px-3">Source Hash (SHA-256)</th>
                    <th className="py-2.5 px-3">Notes</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/40 text-[11px]">
                  {archiveData?.snapshots && archiveData.snapshots.length > 0 ? (
                    archiveData.snapshots.map((snap) => (
                      <tr key={snap.effectiveDate} className="hover:bg-surface-elevated/40">
                        <td className="py-2.5 px-3 text-text-primary font-bold">
                          {snap.effectiveDate}
                        </td>
                        <td className="py-2.5 px-3 text-text-muted">
                          {snap.availabilityTime ? snap.availabilityTime.slice(0, 19).replace("T", " ") : "—"}
                        </td>
                        <td className="py-2.5 px-3 text-text-secondary">
                          {snap.sourcePublisher}
                        </td>
                        <td className="py-2.5 px-3 text-right">
                          <span className="font-semibold text-text-primary">
                            {snap.totalConstituentsCount}
                          </span>
                          <span className="text-[10px] text-success block">
                            {snap.membershipCoverageStatus ? snap.membershipCoverageStatus.replace(/_/g, " ") : "—"}
                          </span>
                        </td>
                        <td className="py-2.5 px-3 text-right">
                          <span className="text-text-primary font-semibold">
                            {snap.identifierMappingCoveragePct != null ? `${snap.identifierMappingCoveragePct.toFixed(1)}%` : "—"}
                          </span>
                          <span className="text-[10px] text-text-muted block">
                            {snap.mappingCoverageStatus ? snap.mappingCoverageStatus.replace(/_/g, " ") : "—"}
                          </span>
                        </td>
                        <td className="py-2.5 px-3 text-right font-bold text-accent">
                          {snap.disclosedWeightSumPct != null ? `${snap.disclosedWeightSumPct.toFixed(2)}%` : "—"}
                          <span className="text-[10px] text-text-muted font-normal block">
                            {snap.weightedConstituentsCount} stocks ({snap.unknownWeightConstituentsCount} tail)
                          </span>
                        </td>
                        <td className="py-2.5 px-3">
                          <span
                            className={`badge text-[9px] uppercase font-semibold ${
                              snap.weightCoverageStatus === "VERIFIED_COMPLETE"
                                ? "badge-success"
                                : "badge-warning"
                            }`}
                          >
                            {snap.weightCoverageStatus === "GATED_INCOMPLETE_BENCHMARK_WEIGHTS"
                              ? "Gated (Tail Withheld)"
                              : snap.weightCoverageStatus}
                          </span>
                        </td>
                        <td className="py-2.5 px-3 text-text-muted font-mono text-[10px]">
                          <span title={snap.sourceArtifactSha256}>
                            {snap.sourceArtifactSha256 ? `${snap.sourceArtifactSha256.slice(0, 10)}...` : "—"}
                          </span>
                        </td>
                        <td className="py-2.5 px-3 text-text-secondary font-sans text-[11px] max-w-xs truncate" title={snap.notes}>
                          {snap.notes}
                        </td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td colSpan={9} className="py-6 text-center text-text-muted">
                        {archiveLoading ? "Loading benchmark factsheet archive..." : "No benchmark factsheet snapshots available."}
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

            {/* Statutory Gaps and Disclosures */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3 pt-2 border-t border-border/40 text-xs text-text-secondary">
              <div className="p-3 rounded bg-surface-elevated/40 border border-border/40 space-y-1">
                <span className="font-semibold text-text-primary uppercase tracking-wider text-[10px]">
                  Known Historical Composition Gaps:
                </span>
                <ul className="list-disc list-inside space-y-0.5 text-text-muted text-[11px]">
                  {archiveData?.historicalCoverageGaps?.map((gap: string, i: number) => (
                    <li key={i}>{gap}</li>
                  ))}
                </ul>
              </div>

              <div className="p-3 rounded bg-surface-elevated/40 border border-border/40 space-y-1">
                <span className="font-semibold text-text-primary uppercase tracking-wider text-[10px]">
                  Epistemic &amp; Licensing Discipline (DEP-05):
                </span>
                <ul className="list-disc list-inside space-y-0.5 text-text-muted text-[11px]">
                  {archiveData?.governanceDisclosures?.map((disc: string, i: number) => (
                    <li key={i}>{disc}</li>
                  ))}
                  <li>
                    Tail constituents (<strong className="text-warning">~490 members</strong>) have undisclosed weights in public factsheets; licensing requires commercial index subscription.
                  </li>
                </ul>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* 3. Historical Trajectory Table */}
      <div className="panel p-5 space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-border/60 pb-3">
          <div>
            <h4 className="text-sm font-semibold text-text-primary">
              Historical Observation Trajectory &amp; Point-in-Time Pairings
            </h4>
            <p className="text-xs text-text-secondary">
              Chronological ledger of eligible fund disclosures and Point-in-Time benchmark pairings. Gaps remain visible; missing periods are not interpolated.
            </p>
          </div>
          <span className="mono-meta text-[11px] text-text-muted">
            {data.eligibleObservationsCount} observations | {data.skippedPeriodsCount} skipped
          </span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs border-collapse">
            <thead>
              <tr className="border-b border-border text-text-secondary font-medium uppercase tracking-wider text-[10px]">
                <th className="py-2.5 px-3">Select</th>
                <th className="py-2.5 px-3">Fund Date</th>
                <th className="py-2.5 px-3">Benchmark Snapshot</th>
                <th className="py-2.5 px-3">Date Gap</th>
                <th className="py-2.5 px-3 text-right">Off-BM Allocation</th>
                <th className="py-2.5 px-3 text-right">Active Share</th>
                <th className="py-2.5 px-3 text-right">Lower Bound</th>
                <th className="py-2.5 px-3 text-right">Holdings</th>
                <th className="py-2.5 px-3 text-right">Disclosed Weights</th>
                <th className="py-2.5 px-3 text-right">Mapping</th>
                <th className="py-2.5 px-3">Coverage Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/40 font-mono">
              {data.observations.map((obs) => {
                const isSelected = selectedObsDate === obs.portfolioDate;
                const isExactOrClose = obs.snapshotDateDiffDays <= 45;
                return (
                  <tr
                    key={obs.portfolioDate}
                    onClick={() => setSelectedObsDate(obs.portfolioDate)}
                    className={`cursor-pointer transition-colors ${
                      isSelected
                        ? "bg-primary/10 font-medium"
                        : "hover:bg-surface-elevated/60"
                    }`}
                  >
                    <td className="py-2.5 px-3">
                      <input
                        type="radio"
                        name="selectedObservation"
                        checked={isSelected}
                        onChange={() => setSelectedObsDate(obs.portfolioDate)}
                        className="text-primary focus:ring-0 cursor-pointer"
                      />
                    </td>
                    <td className="py-2.5 px-3 text-text-primary font-bold">
                      {obs.portfolioDate}
                    </td>
                    <td className="py-2.5 px-3 text-text-secondary">
                      {obs.benchmarkDate || "—"}
                    </td>
                    <td className="py-2.5 px-3">
                      <span
                        className={`${
                          isExactOrClose
                            ? "text-success font-semibold"
                            : "text-warning font-semibold"
                        }`}
                      >
                        {obs.snapshotDateDiffDays}d{" "}
                        <span className="text-[9px] uppercase">
                          {isExactOrClose ? "(≤45d)" : "(>45d)"}
                        </span>
                      </span>
                    </td>
                    <td className="py-2.5 px-3 text-right font-bold text-accent">
                      {obs.offBenchmarkAllocationPct.toFixed(2)}%
                    </td>
                    <td className="py-2.5 px-3 text-right">
                      {obs.activeSharePct != null ? (
                        <span className="text-text-primary font-bold">
                          {obs.activeSharePct.toFixed(2)}%
                        </span>
                      ) : (
                        <span className="text-warning text-[10px] uppercase font-semibold">
                          Gated
                        </span>
                      )}
                    </td>
                    <td className="py-2.5 px-3 text-right text-text-primary font-semibold">
                      {obs.activeShareLowerBoundPct != null
                        ? `≥ ${obs.activeShareLowerBoundPct.toFixed(2)}%`
                        : "—"}
                    </td>
                    <td className="py-2.5 px-3 text-right text-text-secondary">
                      {obs.fundHoldingsCount}
                    </td>
                    <td className="py-2.5 px-3 text-right">
                      <span
                        className={`${
                          obs.benchmarkWeightCoveragePct >= 99.5
                            ? "text-success font-bold"
                            : "text-warning"
                        }`}
                      >
                        {obs.benchmarkDisclosedWeightSum != null
                          ? `${obs.benchmarkDisclosedWeightSum.toFixed(2)}%`
                          : `${obs.benchmarkWeightCoveragePct.toFixed(2)}%`}
                      </span>
                    </td>
                    <td className="py-2.5 px-3 text-right text-text-secondary">
                      {obs.securityMappingCoveragePct.toFixed(1)}%
                    </td>
                    <td className="py-2.5 px-3">
                      <span
                        className={`badge text-[9px] uppercase ${
                          obs.status === "VERIFIED_COMPLETE"
                            ? "badge-success"
                            : obs.status === "GATED_INCOMPLETE_BENCHMARK_WEIGHTS"
                            ? "badge-warning"
                            : "badge-neutral"
                        }`}
                      >
                        {obs.status === "GATED_INCOMPLETE_BENCHMARK_WEIGHTS"
                          ? "Gated Weights"
                          : obs.status.replace(/_/g, " ")}
                      </span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        {/* Selected Observation Audit Evidence Strip */}
        {selectedObservation && (
          <div className="p-3.5 rounded bg-surface-elevated/40 border border-border/60 text-xs space-y-2">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-border/40 pb-2">
              <span className="font-semibold text-text-primary">
                Point-in-Time Audit Evidence for Snapshot: {selectedObservation.portfolioDate}
              </span>
              <span className="text-[11px] font-mono text-text-muted">
                Status: <strong className="text-text-primary">{selectedObservation.status}</strong>
              </span>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-3 text-[11px] font-mono">
              <div>
                <span className="text-text-muted block">Fund Filing Date:</span>
                <span className="text-text-primary font-bold">{selectedObservation.portfolioDate}</span>
                <span className="text-[10px] text-text-muted block">
                  Avail: {selectedObservation.portfolioAvailabilityTime ? selectedObservation.portfolioAvailabilityTime.slice(0, 19).replace("T", " ") : "—"}
                </span>
              </div>

              <div>
                <span className="text-text-muted block">Benchmark Snapshot:</span>
                <span className="text-text-primary font-bold">{selectedObservation.benchmarkDate || "—"}</span>
                <span className="text-[10px] text-text-muted block">
                  Avail: {selectedObservation.benchmarkAvailabilityTime ? selectedObservation.benchmarkAvailabilityTime.slice(0, 19).replace("T", " ") : "—"}
                </span>
              </div>

              <div>
                <span className="text-text-muted block">Temporal Gap &amp; Comparability:</span>
                <span className={`font-bold ${selectedObservation.snapshotDateDiffDays <= 45 ? "text-success" : "text-warning"}`}>
                  {selectedObservation.snapshotDateDiffDays} days ({selectedObservation.snapshotDateDiffDays <= 45 ? "Temporally Comparable" : "Temporal Mismatch"})
                </span>
                <span className="text-[10px] text-text-muted block">
                  Threshold: ≤ 45 days
                </span>
              </div>

              <div>
                <span className="text-text-muted block">Benchmark Artifact SHA-256:</span>
                <span className="text-text-secondary truncate block" title={selectedObservation.benchmarkSourceArtifactSha256 || "—"}>
                  {selectedObservation.benchmarkSourceArtifactSha256 ? `${selectedObservation.benchmarkSourceArtifactSha256.slice(0, 14)}...` : "—"}
                </span>
                <span className="text-[10px] text-text-muted block truncate" title={selectedObservation.benchmarkSourceStorageUri || "—"}>
                  {selectedObservation.benchmarkSourceStorageUri ? "NSE Indices Verified URI" : "—"}
                </span>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* 4. Five Categories of Apparent Changes (Without Inventing Causality) */}
      <div className="panel p-5 space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-border/60 pb-3">
          <div>
            <h4 className="text-sm font-semibold text-text-primary">
              Observed Portfolio &amp; Benchmark Changes Across Snapshots
            </h4>
            <p className="text-xs text-text-secondary">
              Epistemically separates five distinct sources of apparent change. Holding-weight changes are not interpreted as manager trading.
            </p>
          </div>
          <span className="mono-meta text-[11px] text-text-muted">
            Total Changes Recorded: {data.apparentChanges.length}
          </span>
        </div>

        {/* Change Category Filters */}
        <div className="flex flex-wrap items-center gap-1.5 text-xs">
          {[
            { id: "ALL", label: `All (${data.apparentChanges.length})` },
            {
              id: "PORTFOLIO_WEIGHT_CHANGE",
              label: `Portfolio Weight Changes (${
                data.apparentChanges.filter((c) => c.changeCategory === "PORTFOLIO_WEIGHT_CHANGE").length
              })`,
            },
            {
              id: "BENCHMARK_MEMBERSHIP_CHANGE",
              label: `Benchmark Membership Changes (${
                data.apparentChanges.filter((c) => c.changeCategory === "BENCHMARK_MEMBERSHIP_CHANGE").length
              })`,
            },
            {
              id: "CLASSIFICATION_CHANGE",
              label: `Classification Shifts (${
                data.apparentChanges.filter((c) => c.changeCategory === "CLASSIFICATION_CHANGE").length
              })`,
            },
            {
              id: "COVERAGE_IDENTITY_CHANGE",
              label: `Coverage / Identity (${
                data.apparentChanges.filter((c) => c.changeCategory === "COVERAGE_IDENTITY_CHANGE").length
              })`,
            },
            {
              id: "UNRESOLVED_CHANGE",
              label: `Unresolved (${
                data.apparentChanges.filter((c) => c.changeCategory === "UNRESOLVED_CHANGE").length
              })`,
            },
          ].map((btn) => (
            <button
              key={btn.id}
              onClick={() => setChangeFilter(btn.id as ChangeFilter)}
              className={`px-2.5 py-1 rounded text-xs transition-colors ${
                changeFilter === btn.id
                  ? "bg-primary text-primary-fg font-semibold shadow-sm"
                  : "bg-surface-elevated hover:bg-surface-elevated/80 text-text-secondary"
              }`}
            >
              {btn.label}
            </button>
          ))}
        </div>

        {/* Changes List / Grid */}
        {filteredChanges.length === 0 ? (
          <div className="p-6 text-center text-xs text-text-muted">
            No apparent changes found matching category &quot;{changeFilter}&quot;.
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3 max-h-96 overflow-y-auto pr-1">
            {filteredChanges.map((change, idx) => (
              <div
                key={idx}
                className="p-3.5 rounded border border-border/70 bg-surface-elevated/40 space-y-2 text-xs"
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="space-y-0.5">
                    <span className="font-semibold text-text-primary">
                      {change.securityName}
                    </span>
                    <span className="font-mono text-[11px] text-text-muted block">
                      ISIN: {change.isin}
                    </span>
                  </div>
                  <span
                    className={`badge text-[9px] uppercase font-semibold ${
                      change.changeCategory === "PORTFOLIO_WEIGHT_CHANGE"
                        ? "badge-primary"
                        : change.changeCategory === "BENCHMARK_MEMBERSHIP_CHANGE"
                        ? "badge-warning"
                        : change.changeCategory === "CLASSIFICATION_CHANGE"
                        ? "badge-accent"
                        : "badge-neutral"
                    }`}
                  >
                    {change.changeCategory.replace(/_/g, " ")}
                  </span>
                </div>

                <p className="text-text-secondary leading-snug">
                  {change.description}
                </p>

                <div className="flex items-center justify-between pt-1 border-t border-border/30 text-[10px] text-text-muted font-mono">
                  <span>
                    Period: {change.earlierDate} → {change.laterDate}
                  </span>
                  <span>Source: {change.sourceEvidence}</span>
                </div>

                <div className="text-[10px] text-text-muted italic bg-surface-elevated/60 p-1.5 rounded border border-border/20">
                  ⚠️ {change.epistemicCaution}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 5. Descriptive Off-Benchmark Allocation vs Active Returns (Non-Causal) */}
      <div className="panel p-5 space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-border/60 pb-3">
          <div>
            <h4 className="text-sm font-semibold text-text-primary">
              Descriptive Association: Off-Benchmark Allocation &amp; Active Returns
            </h4>
            <p className="text-xs text-text-secondary">
              Observed subsequent/contemporaneous active return (R_A = R_p - R_b) alongside measured off-benchmark allocation.
            </p>
          </div>
          <span className="badge badge-warning text-[10px] uppercase font-semibold">
            DESCRIPTIVE ASSOCIATION ONLY — NON-CAUSAL
          </span>
        </div>

        <div className="p-3 bg-warning/10 border border-warning/30 rounded text-xs text-text-secondary space-y-1">
          <span className="font-semibold text-warning">Strict Non-Causality Boundary (AGENTS.md):</span>
          <p>
            Off-benchmark allocation describes portfolio composition relative to index membership. It does NOT establish alpha causation, manager skill, or predictive power. Differences between portfolio and benchmark weights must never be described as verified causal attribution.
          </p>
        </div>

        {data.returnAssociations.length === 0 ? (
          <div className="p-4 text-center text-xs text-text-muted">
            Insufficient consecutive snapshot pairings to establish return associations under current cutoffs.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="border-b border-border text-text-secondary font-medium uppercase tracking-wider text-[10px]">
                  <th className="py-2.5 px-3">Period</th>
                  <th className="py-2.5 px-3">Horizon Days</th>
                  <th className="py-2.5 px-3 text-right">Start Off-BM</th>
                  <th className="py-2.5 px-3 text-right">End Off-BM</th>
                  <th className="py-2.5 px-3 text-right">Fund Return (R_p)</th>
                  <th className="py-2.5 px-3 text-right">Benchmark Return (R_b)</th>
                  <th className="py-2.5 px-3 text-right">Active Return (R_A)</th>
                  <th className="py-2.5 px-3">Epistemic Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/40 font-mono">
                {data.returnAssociations.map((assoc, idx) => (
                  <tr key={idx} className="hover:bg-surface-elevated/40">
                    <td className="py-2.5 px-3 font-semibold text-text-primary">
                      {assoc.periodStartDate} → {assoc.periodEndDate}
                    </td>
                    <td className="py-2.5 px-3 text-text-secondary">
                      {assoc.sampleDays}d
                    </td>
                    <td className="py-2.5 px-3 text-right font-bold text-accent">
                      {assoc.startOffBenchmarkAllocationPct != null
                        ? `${assoc.startOffBenchmarkAllocationPct.toFixed(2)}%`
                        : "—"}
                    </td>
                    <td className="py-2.5 px-3 text-right font-bold text-accent">
                      {assoc.endOffBenchmarkAllocationPct != null
                        ? `${assoc.endOffBenchmarkAllocationPct.toFixed(2)}%`
                        : "—"}
                    </td>
                    <td className="py-2.5 px-3 text-right">
                      {assoc.fundReturnPct != null ? (
                        <span className={assoc.fundReturnPct >= 0 ? "text-success font-semibold" : "text-danger font-semibold"}>
                          {assoc.fundReturnPct >= 0 ? "+" : ""}{assoc.fundReturnPct.toFixed(2)}%
                        </span>
                      ) : "—"}
                    </td>
                    <td className="py-2.5 px-3 text-right">
                      {assoc.benchmarkReturnPct != null ? (
                        <span className={assoc.benchmarkReturnPct >= 0 ? "text-success font-semibold" : "text-danger font-semibold"}>
                          {assoc.benchmarkReturnPct >= 0 ? "+" : ""}{assoc.benchmarkReturnPct.toFixed(2)}%
                        </span>
                      ) : "—"}
                    </td>
                    <td className="py-2.5 px-3 text-right">
                      {assoc.activeReturnPct != null ? (
                        <span className={`font-bold ${
                          assoc.activeReturnPct >= 0 ? "text-success" : "text-danger"
                        }`}>
                          {assoc.activeReturnPct >= 0 ? "+" : ""}{assoc.activeReturnPct.toFixed(2)}%
                        </span>
                      ) : "—"}
                    </td>
                    <td className="py-2.5 px-3">
                      <span className="badge badge-neutral text-[9px] uppercase">
                        Descriptive Only
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* 6. Inspectable Constituent-Level Inputs for Selected Snapshot */}
      <div className="panel p-5 space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-border/60 pb-3">
          <div>
            <h4 className="text-sm font-semibold text-text-primary">
              Inspectable Constituent-Level Inputs — Snapshot: {selectedObservation?.portfolioDate}
            </h4>
            <p className="text-xs text-text-secondary">
              Auditable security weights across fund disclosure and {data.benchmarkCode} constituent ledger.
            </p>
          </div>
          <span className="mono-meta text-[11px] text-text-muted">
            Showing {filteredConstituents.length} of {selectedObservation?.constituentInputs.length || 0} constituents
          </span>
        </div>

        {/* Filter and Search Bar */}
        <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-1.5 text-xs">
            {[
              { id: "ALL", label: "All Constituents" },
              { id: "OFF_BENCHMARK", label: "Off-Benchmark Only" },
              { id: "IN_BENCHMARK_WEIGHTED", label: "In Benchmark (Weighted)" },
              { id: "IN_BENCHMARK_UNWEIGHTED", label: "In Benchmark (Unweighted Tail)" },
            ].map((btn) => (
              <button
                key={btn.id}
                onClick={() => setConstituentFilter(btn.id as ConstituentFilter)}
                className={`px-2.5 py-1 rounded text-xs transition-colors ${
                  constituentFilter === btn.id
                    ? "bg-primary text-primary-fg font-semibold shadow-sm"
                    : "bg-surface-elevated hover:bg-surface-elevated/80 text-text-secondary"
                }`}
              >
                {btn.label}
              </button>
            ))}
          </div>

          <input
            type="text"
            placeholder="Search security or ISIN..."
            value={constituentSearch}
            onChange={(e) => setConstituentSearch(e.target.value)}
            className="px-3 py-1.5 text-xs rounded border border-border bg-surface text-text-primary placeholder:text-text-muted w-full sm:w-60 focus:outline-none focus:ring-1 focus:ring-primary"
          />
        </div>

        {/* Constituent Table */}
        <div className="overflow-x-auto max-h-96 overflow-y-auto">
          <table className="w-full text-left text-xs border-collapse">
            <thead className="sticky top-0 bg-surface border-b border-border z-10">
              <tr className="text-text-secondary font-medium uppercase tracking-wider text-[10px]">
                <th className="py-2 px-3">Security Name</th>
                <th className="py-2 px-3">ISIN</th>
                <th className="py-2 px-3 text-right">Portfolio Weight</th>
                <th className="py-2 px-3 text-right">Benchmark Weight</th>
                <th className="py-2 px-3 text-right">Discrepancy |wp - wb|</th>
                <th className="py-2 px-3">Benchmark Membership</th>
                <th className="py-2 px-3">Market Cap</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/40 font-mono">
              {filteredConstituents.length === 0 ? (
                <tr>
                  <td colSpan={7} className="py-6 text-center text-text-muted">
                    No constituents found matching current filters.
                  </td>
                </tr>
              ) : (
                filteredConstituents.map((c) => (
                  <tr key={c.isin} className="hover:bg-surface-elevated/40">
                    <td className="py-2 px-3 font-sans font-medium text-text-primary">
                      {c.securityName}
                    </td>
                    <td className="py-2 px-3 text-text-muted text-[11px]">
                      {c.isin}
                    </td>
                    <td className="py-2 px-3 text-right text-text-primary font-bold">
                      {c.portfolioWeightPct != null ? `${c.portfolioWeightPct.toFixed(2)}%` : "0.00%"}
                    </td>
                    <td className="py-2 px-3 text-right">
                      {c.benchmarkWeightPct != null ? (
                        <span className="text-text-primary font-bold">
                          {c.benchmarkWeightPct.toFixed(2)}%
                        </span>
                      ) : c.isBenchmarkMember ? (
                        <span className="text-warning text-[10px] italic">
                          Withheld (tail)
                        </span>
                      ) : (
                        <span className="text-text-muted">0.00%</span>
                      )}
                    </td>
                    <td className="py-2 px-3 text-right font-bold">
                      {c.absoluteDiscrepancyPct != null ? (
                        <span className="text-primary">
                          {c.absoluteDiscrepancyPct.toFixed(2)}%
                        </span>
                      ) : (
                        <span className="text-text-muted text-[11px]">—</span>
                      )}
                    </td>
                    <td className="py-2 px-3">
                      {c.isBenchmarkMember ? (
                        <span className="badge badge-success text-[9px] uppercase">
                          In Benchmark
                        </span>
                      ) : (
                        <span className="badge badge-accent text-[9px] uppercase">
                          Off-Benchmark
                        </span>
                      )}
                    </td>
                    <td className="py-2 px-3">
                      <span className="badge badge-neutral text-[9px] uppercase">
                        {c.marketCapCategory.replace(/_/g, " ")}
                      </span>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
