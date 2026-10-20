"use client";

import React, { useState, useEffect, useMemo } from "react";
import {
  fetchHoldingLongevity,
  HoldingLongevityResponseDto,
} from "@/lib/api/portfolio";

interface HoldingLongevityViewProps {
  schemeOptionId: number;
  asOfDate?: string;
  knowledgeCutoffTime?: string;
  onInvestigateQuestion?: (question: string, reason?: string) => void;
}

type SubTab = "TENURE_DISTRIBUTION" | "PERSISTENT_HOLDINGS" | "COMPOSITION_CHANGES" | "TURNOVER_AUDIT";

export function HoldingLongevityView({
  schemeOptionId,
  asOfDate: propAsOfDate,
  knowledgeCutoffTime: propCutoffTime,
  onInvestigateQuestion,
}: HoldingLongevityViewProps) {
  const [data, setData] = useState<HoldingLongevityResponseDto | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedSubTab, setSelectedSubTab] = useState<SubTab>("TENURE_DISTRIBUTION");
  const [searchQuery, setSearchQuery] = useState<string>("");
  const [statusFilter, setStatusFilter] = useState<string>("ALL");
  const [expandedSecurities, setExpandedSecurities] = useState<Set<string>>(new Set());
  const [expandedTransitions, setExpandedTransitions] = useState<Set<string>>(new Set());
  const [showMethodologyInfo, setShowMethodologyInfo] = useState<boolean>(false);

  const effectiveAsOfDate = propAsOfDate || "2026-10-01";
  const effectiveCutoffTime = propCutoffTime || "2026-10-10T23:59:59+05:30";

  useEffect(() => {
    let isMounted = true;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLoading(true);
    setError(null);

    fetchHoldingLongevity(schemeOptionId, effectiveAsOfDate, undefined, undefined, effectiveCutoffTime)
      .then((res) => {
        if (!isMounted) return;
        setData(res);
        if (res.transitions && res.transitions.length > 0) {
          // Auto-expand first transition
          const firstKey = `${res.transitions[0].earlierDate}::${res.transitions[0].laterDate}`;
          setExpandedTransitions(new Set([firstKey]));
        }
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (!isMounted) return;
        setError(err instanceof Error ? err.message : "Failed to load holding longevity analysis.");
        setLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, [schemeOptionId, effectiveAsOfDate, effectiveCutoffTime]);

  const toggleSecurityExpand = (key: string) => {
    setExpandedSecurities((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  };

  const toggleTransitionExpand = (key: string) => {
    setExpandedTransitions((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  };

  // Filtered Holdings
  const allHoldings = data?.allHoldings;
  const filteredHoldings = useMemo(() => {
    if (!allHoldings) return [];
    return allHoldings.filter((item) => {
      const matchesSearch =
        searchQuery === "" ||
        item.canonicalName.toLowerCase().includes(searchQuery.toLowerCase()) ||
        (item.isin && item.isin.toLowerCase().includes(searchQuery.toLowerCase()));

      const matchesStatus =
        statusFilter === "ALL" ||
        (statusFilter === "PERSISTENT" && item.persistenceCategory === "PERSISTENT") ||
        (statusFilter === "CONTINUOUS" && item.presenceStatus === "CONTINUOUS") ||
        (statusFilter === "INTERMITTENT" && item.presenceStatus === "INTERMITTENT") ||
        (statusFilter === "NEWLY_OBSERVED" && item.presenceStatus === "NEWLY_OBSERVED") ||
        (statusFilter === "POTENTIAL_EXIT" && item.presenceStatus === "POTENTIAL_EXIT") ||
        (statusFilter === "PARTIAL_DISCLOSURE" && item.presenceStatus === "NOT_OBSERVED_IN_PARTIAL_DISCLOSURE");

      return matchesSearch && matchesStatus;
    });
  }, [allHoldings, searchQuery, statusFilter]);

  if (loading) {
    return (
      <div className="rounded-xl border border-border bg-card p-8 text-center animate-pulse">
        <div className="mx-auto h-8 w-64 rounded bg-muted mb-4" />
        <p className="text-sm text-muted-foreground">
          Evaluating point-in-time holding longevity &amp; portfolio turnover under knowledge cutoff...
        </p>
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-6">
        <div className="flex items-center gap-3">
          <span className="text-destructive font-semibold">Holding Longevity Unavailable</span>
        </div>
        <p className="mt-2 text-sm text-muted-foreground">
          {error || "Unable to retrieve point-in-time holding history for the requested horizon."}
        </p>
      </div>
    );
  }

  if (data.analysisStatus === "INSUFFICIENT_DATA") {
    return (
      <div className="rounded-xl border border-amber-500/30 bg-amber-500/5 p-6">
        <h4 className="font-semibold text-amber-500">Insufficient Disclosed Snapshots</h4>
        <p className="mt-1 text-sm text-muted-foreground">{data.statusReason}</p>
        {data.investigationQuestions && data.investigationQuestions.length > 0 && (
          <div className="mt-4 pt-4 border-t border-border">
            <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">Due Diligence</span>
            <ul className="mt-2 space-y-1 text-xs text-muted-foreground list-disc list-inside">
              {data.investigationQuestions.map((q, idx) => (
                <li key={idx}>{q}</li>
              ))}
            </ul>
          </div>
        )}
      </div>
    );
  }

  // Calculate high-level summary statistics
  const over3YearsBucket = data.tenureBuckets.find((b) => b.bucketKey === "OVER_3_YEARS");
  const years1To3Bucket = data.tenureBuckets.find((b) => b.bucketKey === "YEARS_1_TO_3");
  const under6MBucket = data.tenureBuckets.find((b) => b.bucketKey === "UNDER_6_MONTHS");

  return (
    <div className="space-y-6">
      {/* HEADER SECTION */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between rounded-xl border border-border bg-card/60 p-5 backdrop-blur-sm">
        <div>
          <div className="flex items-center gap-2">
            <h3 className="text-lg font-bold tracking-tight text-foreground">
              Point-in-Time Holding Longevity &amp; Position Persistence
            </h3>
            <span className="rounded-full bg-emerald-500/10 px-2.5 py-0.5 text-xs font-medium text-emerald-500 border border-emerald-500/20">
              PIT Holding Ledger
            </span>
          </div>
          <p className="mt-1 text-xs text-muted-foreground">
            Descriptive tenure distribution, core position continuity, and verified portfolio composition shifts across{" "}
            <span className="font-medium text-foreground">{data.eligibleSnapshotsCount} eligible snapshots</span> ({data.observationWindowStartDate} to {data.observationWindowEndDate}).
          </p>
        </div>

        <div className="flex items-center gap-2 self-start sm:self-auto">
          <button
            type="button"
            onClick={() => setShowMethodologyInfo(!showMethodologyInfo)}
            className="rounded-lg border border-border bg-secondary/50 px-3 py-1.5 text-xs font-medium text-muted-foreground hover:bg-secondary hover:text-foreground transition-colors"
          >
            {showMethodologyInfo ? "Hide Epistemic Rules" : "Epistemic Guardrails"}
          </button>
        </div>
      </div>

      {/* EPISTEMIC GUARDRAILS DRAWER */}
      {showMethodologyInfo && (
        <div className="rounded-xl border border-blue-500/30 bg-blue-500/5 p-5 text-xs text-muted-foreground space-y-3">
          <div className="flex items-center gap-2 text-blue-500 font-semibold">
            <span>Constitutional Methodological Principles (AGENTS.md &amp; Phase 2K)</span>
          </div>
          <ul className="list-disc list-inside space-y-1.5 leading-relaxed">
            <li>
              <strong className="text-foreground">Descriptive Portfolio Observation:</strong> Measures empirical presence across published portfolio snapshots. Never generates manager-skill scores or predictive forecasts.
            </li>
            <li>
              <strong className="text-foreground">First &amp; Last Observed Dates:</strong> The first observed date is NOT necessarily the purchase date; the last observed date is NOT necessarily the sale date.
            </li>
            <li>
              <strong className="text-foreground">Scope Shift Awareness:</strong> Disappearance from a partial top-holdings disclosure is strictly marked as <code className="text-foreground font-mono">NOT_OBSERVED_IN_PARTIAL_DISCLOSURE</code>, never assumed to be an exit.
            </li>
            <li>
              <strong className="text-foreground">Strict Turnover Gating:</strong> Actual regulatory portfolio turnover is gated with <code className="text-foreground font-mono">TURNOVER_UNAVAILABLE_TRANSACTION_DATA_MISSING</code> because monthly snapshots report month-end holdings, not transaction logs.
            </li>
            <li>
              <strong className="text-foreground">Descriptive Composition Change Formula:</strong> Defined as <code className="text-foreground font-mono">1/2 * SUM(|w_t - w_{`{t-1}`}|)</code> for consecutive comparable full-portfolio disclosures (&gt; 90% weight coverage, &le; 45 day cadence).
            </li>
          </ul>
        </div>
      )}

      {/* SUMMARY KPI CARDS */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <div className="rounded-xl border border-border bg-card p-4">
          <span className="text-xs text-muted-foreground font-medium">Core Tenures (&gt; 3 Years)</span>
          <div className="mt-1.5 text-2xl font-bold tracking-tight text-foreground">
            {over3YearsBucket?.portfolioWeightSharePct ?? 0}%
          </div>
          <p className="mt-1 text-xs text-muted-foreground">
            {over3YearsBucket?.holdingsCount ?? 0} holdings in &gt; 3y tenure
          </p>
        </div>

        <div className="rounded-xl border border-border bg-card p-4">
          <span className="text-xs text-muted-foreground font-medium">Mid Tenures (1–3 Years)</span>
          <div className="mt-1.5 text-2xl font-bold tracking-tight text-foreground">
            {years1To3Bucket?.portfolioWeightSharePct ?? 0}%
          </div>
          <p className="mt-1 text-xs text-muted-foreground">
            {years1To3Bucket?.holdingsCount ?? 0} holdings in 1–3y tenure
          </p>
        </div>

        <div className="rounded-xl border border-border bg-card p-4">
          <span className="text-xs text-muted-foreground font-medium">Recent Tenures (&lt; 6 Months)</span>
          <div className="mt-1.5 text-2xl font-bold tracking-tight text-foreground">
            {under6MBucket?.portfolioWeightSharePct ?? 0}%
          </div>
          <p className="mt-1 text-xs text-muted-foreground">
            {under6MBucket?.holdingsCount ?? 0} recently observed positions
          </p>
        </div>

        <div className="rounded-xl border border-border bg-card p-4">
          <span className="text-xs text-muted-foreground font-medium">Avg Monthly Composition Shift</span>
          <div className="mt-1.5 text-2xl font-bold tracking-tight text-foreground">
            {data.turnoverEvaluation.averageMonthlyCompositionChangePct}%
          </div>
          <p className="mt-1 text-xs text-muted-foreground">
            Descriptive 1/2 &Sigma;|w| across full disclosures
          </p>
        </div>
      </div>

      {/* SUB-TABS NAVIGATION */}
      <div className="flex border-b border-border space-x-6 text-sm font-medium">
        <button
          type="button"
          onClick={() => setSelectedSubTab("TENURE_DISTRIBUTION")}
          className={`pb-3 transition-colors relative ${
            selectedSubTab === "TENURE_DISTRIBUTION"
              ? "text-foreground font-semibold border-b-2 border-primary"
              : "text-muted-foreground hover:text-foreground"
          }`}
        >
          Tenure Distribution
        </button>
        <button
          type="button"
          onClick={() => setSelectedSubTab("PERSISTENT_HOLDINGS")}
          className={`pb-3 transition-colors relative ${
            selectedSubTab === "PERSISTENT_HOLDINGS"
              ? "text-foreground font-semibold border-b-2 border-primary"
              : "text-muted-foreground hover:text-foreground"
          }`}
        >
          Holdings Persistence ({data.allHoldings.length})
        </button>
        <button
          type="button"
          onClick={() => setSelectedSubTab("COMPOSITION_CHANGES")}
          className={`pb-3 transition-colors relative ${
            selectedSubTab === "COMPOSITION_CHANGES"
              ? "text-foreground font-semibold border-b-2 border-primary"
              : "text-muted-foreground hover:text-foreground"
          }`}
        >
          Composition Changes ({data.transitions.length})
        </button>
        <button
          type="button"
          onClick={() => setSelectedSubTab("TURNOVER_AUDIT")}
          className={`pb-3 transition-colors relative ${
            selectedSubTab === "TURNOVER_AUDIT"
              ? "text-foreground font-semibold border-b-2 border-primary"
              : "text-muted-foreground hover:text-foreground"
          }`}
        >
          Turnover Audit
        </button>
      </div>

      {/* SUB-TAB 1: TENURE DISTRIBUTION */}
      {selectedSubTab === "TENURE_DISTRIBUTION" && (
        <div className="space-y-6">
          {/* Visual Stacked Progress Bar */}
          <div className="rounded-xl border border-border bg-card p-5 space-y-3">
            <div className="flex items-center justify-between text-xs">
              <span className="font-semibold text-foreground">Disclosed Portfolio Weight by Observed Tenure Bucket</span>
              <span className="text-muted-foreground font-mono">
                Latest Snapshot: {data.observationWindowEndDate}
              </span>
            </div>

            <div className="h-6 w-full rounded-lg overflow-hidden flex bg-secondary/50 border border-border/40">
              {data.tenureBuckets.map((bucket) => {
                const colors: Record<string, string> = {
                  OVER_3_YEARS: "bg-emerald-500",
                  YEARS_1_TO_3: "bg-blue-500",
                  MONTHS_6_TO_12: "bg-amber-500",
                  UNDER_6_MONTHS: "bg-rose-500",
                };
                const color = colors[bucket.bucketKey] || "bg-muted";
                const width = `${bucket.portfolioWeightSharePct}%`;

                if (bucket.portfolioWeightSharePct <= 0) return null;

                return (
                  <div
                    key={bucket.bucketKey}
                    style={{ width }}
                    className={`${color} relative group transition-all hover:opacity-90 flex items-center justify-center text-[10px] font-bold text-white overflow-hidden`}
                    title={`${bucket.bucketLabel}: ${bucket.portfolioWeightSharePct}% (${bucket.holdingsCount} holdings)`}
                  >
                    {bucket.portfolioWeightSharePct >= 8 && `${bucket.portfolioWeightSharePct}%`}
                  </div>
                );
              })}
            </div>

            <div className="flex flex-wrap items-center gap-4 pt-1 text-xs">
              <div className="flex items-center gap-1.5">
                <span className="h-2.5 w-2.5 rounded-full bg-emerald-500" />
                <span className="text-muted-foreground">More than 3 Years</span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="h-2.5 w-2.5 rounded-full bg-blue-500" />
                <span className="text-muted-foreground">1 to 3 Years</span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="h-2.5 w-2.5 rounded-full bg-amber-500" />
                <span className="text-muted-foreground">6 to 12 Months</span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="h-2.5 w-2.5 rounded-full bg-rose-500" />
                <span className="text-muted-foreground">Less than 6 Months</span>
              </div>
            </div>
          </div>

          {/* Tenure Buckets Cards */}
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            {data.tenureBuckets.map((bucket) => {
              const borderColors: Record<string, string> = {
                OVER_3_YEARS: "border-emerald-500/30",
                YEARS_1_TO_3: "border-blue-500/30",
                MONTHS_6_TO_12: "border-amber-500/30",
                UNDER_6_MONTHS: "border-rose-500/30",
              };
              const tagBg: Record<string, string> = {
                OVER_3_YEARS: "bg-emerald-500/10 text-emerald-500",
                YEARS_1_TO_3: "bg-blue-500/10 text-blue-500",
                MONTHS_6_TO_12: "bg-amber-500/10 text-amber-500",
                UNDER_6_MONTHS: "bg-rose-500/10 text-rose-500",
              };

              return (
                <div
                  key={bucket.bucketKey}
                  className={`rounded-xl border ${borderColors[bucket.bucketKey] || "border-border"} bg-card p-5 space-y-3`}
                >
                  <div className="flex items-center justify-between">
                    <div>
                      <span className={`inline-block px-2.5 py-0.5 rounded-full text-xs font-semibold ${tagBg[bucket.bucketKey] || "bg-muted"}`}>
                        {bucket.bucketLabel}
                      </span>
                      <h4 className="mt-1 font-semibold text-foreground text-sm">
                        {bucket.holdingsCount} Reported Position(s)
                      </h4>
                    </div>
                    <div className="text-right">
                      <div className="text-xl font-bold font-mono text-foreground">
                        {bucket.totalReportedWeightPct.toFixed(2)}%
                      </div>
                      <span className="text-xs text-muted-foreground">
                        {bucket.portfolioWeightSharePct}% of disclosed assets
                      </span>
                    </div>
                  </div>

                  {bucket.topHoldings && bucket.topHoldings.length > 0 && (
                    <div className="pt-2 border-t border-border/50">
                      <span className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wider">
                        Largest Positions in Bucket
                      </span>
                      <div className="mt-2 flex flex-wrap gap-1.5">
                        {bucket.topHoldings.map((h, i) => (
                          <span
                            key={i}
                            className="rounded-md bg-secondary/60 px-2 py-1 text-xs font-medium text-foreground border border-border/40"
                          >
                            {h}
                          </span>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* SUB-TAB 2: HOLDINGS PERSISTENCE */}
      {selectedSubTab === "PERSISTENT_HOLDINGS" && (
        <div className="space-y-4">
          {/* Search & Filter Toolbar */}
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between rounded-xl border border-border bg-card p-4">
            <div className="relative flex-1">
              <input
                type="text"
                placeholder="Search holding by name or ISIN..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full rounded-lg border border-border bg-background px-3 py-1.5 text-xs text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-primary"
              />
            </div>

            <div className="flex items-center gap-2">
              <span className="text-xs text-muted-foreground shrink-0 font-medium">Presence:</span>
              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                className="rounded-lg border border-border bg-background px-2.5 py-1.5 text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
              >
                <option value="ALL">All Statuses ({data.allHoldings.length})</option>
                <option value="PERSISTENT">Persistent (&ge; 75% observations)</option>
                <option value="CONTINUOUS">Continuous Presence</option>
                <option value="INTERMITTENT">Intermittent Presence</option>
                <option value="NEWLY_OBSERVED">Newly Observed in Latest</option>
                <option value="POTENTIAL_EXIT">Potential Exit</option>
                <option value="PARTIAL_DISCLOSURE">Not Observed in Partial</option>
              </select>
            </div>
          </div>

          {/* Holdings Table */}
          <div className="rounded-xl border border-border bg-card overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-muted/40 border-b border-border text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                  <tr>
                    <th className="py-3 px-4">Security / ISIN</th>
                    <th className="py-3 px-3">Status</th>
                    <th className="py-3 px-3">Observed Tenure</th>
                    <th className="py-3 px-3 text-center">Appearances</th>
                    <th className="py-3 px-3 text-right">Latest Weight</th>
                    <th className="py-3 px-3 text-right">Avg Weight</th>
                    <th className="py-3 px-3 text-right">Range (Min–Max)</th>
                    <th className="py-3 px-3 text-center">History</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/60">
                  {filteredHoldings.length === 0 ? (
                    <tr>
                      <td colSpan={8} className="py-8 text-center text-muted-foreground">
                        No holdings match the selected search and filter criteria.
                      </td>
                    </tr>
                  ) : (
                    filteredHoldings.map((item) => {
                      const itemKey = item.isin || item.canonicalName;
                      const isExpanded = expandedSecurities.has(itemKey);

                      const statusBadges: Record<string, string> = {
                        CONTINUOUS: "bg-emerald-500/10 text-emerald-500 border-emerald-500/20",
                        PERSISTENT: "bg-emerald-500/10 text-emerald-500 border-emerald-500/20",
                        INTERMITTENT: "bg-blue-500/10 text-blue-500 border-blue-500/20",
                        NEWLY_OBSERVED: "bg-purple-500/10 text-purple-500 border-purple-500/20",
                        POTENTIAL_EXIT: "bg-rose-500/10 text-rose-500 border-rose-500/20",
                        NOT_OBSERVED_IN_PARTIAL_DISCLOSURE: "bg-amber-500/10 text-amber-500 border-amber-500/20",
                      };

                      const badgeStyle = statusBadges[item.presenceStatus] || "bg-muted text-muted-foreground";

                      return (
                        <React.Fragment key={itemKey}>
                          <tr className="hover:bg-muted/30 transition-colors">
                            <td className="py-3 px-4 font-medium text-foreground">
                              <div className="flex items-center gap-2">
                                <div>
                                  <div className="font-semibold">{item.canonicalName}</div>
                                  <div className="text-[10px] text-muted-foreground font-mono">
                                    {item.isin || "ISIN Unresolved"}
                                  </div>
                                </div>
                                {item.isTop10Holding && (
                                  <span className="rounded bg-primary/10 px-1.5 py-0.5 text-[9px] font-bold text-primary border border-primary/20">
                                    Top 10
                                  </span>
                                )}
                              </div>
                            </td>

                            <td className="py-3 px-3">
                              <span className={`inline-block px-2 py-0.5 rounded-full text-[10px] font-semibold border ${badgeStyle}`}>
                                {item.presenceStatus === "NOT_OBSERVED_IN_PARTIAL_DISCLOSURE"
                                  ? "Partial Scope Gap"
                                  : item.presenceStatus.replace(/_/g, " ")}
                              </span>
                            </td>

                            <td className="py-3 px-3 font-mono text-muted-foreground">
                              <div>{item.observedTenureDays} days</div>
                              <div className="text-[10px] text-muted-foreground/80">
                                {item.observedTenureMonths > 0 ? `~${item.observedTenureMonths} mos` : "< 1 mo"} ({item.observedTenureBucket.replace(/_/g, " ")})
                              </div>
                            </td>

                            <td className="py-3 px-3 text-center font-mono">
                              <span className="font-semibold text-foreground">{item.appearanceCount}</span>
                              <span className="text-muted-foreground"> / {data.eligibleSnapshotsCount}</span>
                              <div className="text-[10px] text-muted-foreground">({item.appearanceRatioPct}%)</div>
                            </td>

                            <td className="py-3 px-3 text-right font-mono font-bold text-foreground">
                              {item.latestReportedWeightPct > 0 ? `${item.latestReportedWeightPct.toFixed(2)}%` : "—"}
                              {item.latestRank && (
                                <div className="text-[10px] font-normal text-muted-foreground">#{item.latestRank}</div>
                              )}
                            </td>

                            <td className="py-3 px-3 text-right font-mono text-muted-foreground">
                              {item.averageObservedWeightPct.toFixed(2)}%
                              <div className="text-[10px] text-muted-foreground/70">
                                &plusmn;{item.weightStandardDeviationPct.toFixed(2)} pp
                              </div>
                            </td>

                            <td className="py-3 px-3 text-right font-mono text-muted-foreground">
                              {item.minObservedWeightPct.toFixed(2)}% – {item.maxObservedWeightPct.toFixed(2)}%
                              <div className="text-[10px] text-muted-foreground/70">&Delta; {item.weightRangePct.toFixed(2)} pp</div>
                            </td>

                            <td className="py-3 px-3 text-center">
                              <button
                                type="button"
                                onClick={() => toggleSecurityExpand(itemKey)}
                                className="rounded px-2 py-1 text-[11px] font-medium text-primary hover:bg-primary/10 transition-colors"
                              >
                                {isExpanded ? "Hide" : "View"}
                              </button>
                            </td>
                          </tr>

                          {/* EXPANDABLE WEIGHT TRAJECTORY */}
                          {isExpanded && (
                            <tr className="bg-muted/15">
                              <td colSpan={8} className="p-4 border-t border-b border-border/40">
                                <div className="space-y-3">
                                  <div className="flex items-center justify-between text-xs">
                                    <span className="font-semibold text-foreground">
                                      Disclosed Weight Trajectory across Historical Portfolio Dates
                                    </span>
                                    <span className="text-xs text-muted-foreground">
                                      First Observed: {item.firstObservedDate} &bull; Last Observed: {item.lastObservedDate}
                                    </span>
                                  </div>

                                  <div className="grid grid-cols-2 sm:grid-cols-4 md:grid-cols-6 gap-2">
                                    {item.weightTrajectory.map((pt) => (
                                      <div
                                        key={pt.portfolioDate}
                                        className={`rounded-lg border p-2 text-center text-xs ${
                                          pt.isPresent
                                            ? "border-primary/30 bg-primary/5 text-foreground"
                                            : "border-border/40 bg-secondary/20 text-muted-foreground/60"
                                        }`}
                                      >
                                        <div className="font-mono text-[10px] text-muted-foreground">
                                          {pt.portfolioDate}
                                        </div>
                                        <div className="mt-1 font-bold font-mono">
                                          {pt.isPresent ? `${pt.reportedWeightPct.toFixed(2)}%` : "Absent"}
                                        </div>
                                        {pt.holdingRank && (
                                          <div className="text-[9px] text-muted-foreground">Rank #{pt.holdingRank}</div>
                                        )}
                                        <div className="text-[8px] text-muted-foreground uppercase mt-0.5">
                                          {pt.disclosureScope === "FULL_PORTFOLIO_DISCLOSURE" ? "Full" : "Top 10"}
                                        </div>
                                      </div>
                                    ))}
                                  </div>
                                </div>
                              </td>
                            </tr>
                          )}
                        </React.Fragment>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* SUB-TAB 3: COMPOSITION CHANGES */}
      {selectedSubTab === "COMPOSITION_CHANGES" && (
        <div className="space-y-4">
          <div className="rounded-xl border border-border bg-card p-4">
            <h4 className="text-sm font-semibold text-foreground">Chronological Portfolio Composition Transitions</h4>
            <p className="mt-1 text-xs text-muted-foreground">
              Decomposes holding entry, exit, and continuing weight adjustments between consecutive eligible disclosures.
              Transitions involving partial disclosures or interval gaps &gt; 45 days are strictly gated.
            </p>
          </div>

          <div className="space-y-4">
            {data.transitions.map((t) => {
              const transitionKey = `${t.earlierDate}::${t.laterDate}`;
              const isExpanded = expandedTransitions.has(transitionKey);

              const statusColor: Record<string, string> = {
                COMPARABLE_FULL_DISCLOSURE: "bg-emerald-500/10 text-emerald-500 border-emerald-500/20",
                NON_COMPARABLE_DISCLOSURE_SCOPE_SHIFT: "bg-amber-500/10 text-amber-500 border-amber-500/20",
                NON_COMPARABLE_TEMPORAL_GAP: "bg-rose-500/10 text-rose-500 border-rose-500/20",
              };

              return (
                <div key={transitionKey} className="rounded-xl border border-border bg-card overflow-hidden">
                  <div
                    onClick={() => toggleTransitionExpand(transitionKey)}
                    className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between p-4 cursor-pointer hover:bg-muted/30 transition-colors"
                  >
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-bold font-mono text-sm text-foreground">
                          {t.earlierDate} &rarr; {t.laterDate}
                        </span>
                        <span className="text-xs text-muted-foreground">({t.daysBetween} days)</span>
                        <span className={`px-2 py-0.5 rounded-full text-[10px] font-semibold border ${statusColor[t.comparabilityStatus] || "bg-muted text-muted-foreground"}`}>
                          {t.comparabilityStatus.replace(/_/g, " ")}
                        </span>
                      </div>
                      <p className="mt-1 text-xs text-muted-foreground">{t.comparabilityReason}</p>
                    </div>

                    <div className="flex items-center gap-4 text-right">
                      {t.isComparable ? (
                        <div>
                          <div className="text-sm font-bold font-mono text-foreground">
                            {t.halfSumCompositionChangePct.toFixed(2)}%
                          </div>
                          <span className="text-[10px] text-muted-foreground">Descriptive Shift (1/2 &Sigma;|w|)</span>
                        </div>
                      ) : (
                        <span className="rounded bg-muted px-2 py-1 text-xs text-muted-foreground font-mono">
                          Shift Gated
                        </span>
                      )}
                      <span className="text-xs text-primary font-medium">{isExpanded ? "▲" : "▼"}</span>
                    </div>
                  </div>

                  {isExpanded && (
                    <div className="border-t border-border p-4 bg-muted/10 space-y-4">
                      {/* Metric decomposition chips */}
                      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 text-xs">
                        <div className="rounded-lg border border-border/50 bg-card p-3">
                          <span className="text-muted-foreground">New Holding Entries</span>
                          <div className="mt-1 text-base font-bold text-foreground">
                            {t.newEntriesCount} ({t.newEntriesWeightPct.toFixed(2)}%)
                          </div>
                          {t.topEntries && t.topEntries.length > 0 && (
                            <p className="mt-1 text-[11px] text-muted-foreground/80 truncate">
                              {t.topEntries.join(", ")}
                            </p>
                          )}
                        </div>

                        <div className="rounded-lg border border-border/50 bg-card p-3">
                          <span className="text-muted-foreground">Complete Holding Exits</span>
                          <div className="mt-1 text-base font-bold text-foreground">
                            {t.completeExitsCount} ({t.completeExitsWeightPct.toFixed(2)}%)
                          </div>
                          {t.topExits && t.topExits.length > 0 && (
                            <p className="mt-1 text-[11px] text-muted-foreground/80 truncate">
                              {t.topExits.join(", ")}
                            </p>
                          )}
                        </div>

                        <div className="rounded-lg border border-border/50 bg-card p-3">
                          <span className="text-muted-foreground">Continuing Weight Ups</span>
                          <div className="mt-1 text-base font-bold text-emerald-500">
                            +{t.continuingWeightIncreaseSumPct.toFixed(2)} pp
                          </div>
                          <span className="text-[10px] text-muted-foreground">Across {t.continuingPositionsCount} continuing positions</span>
                        </div>

                        <div className="rounded-lg border border-border/50 bg-card p-3">
                          <span className="text-muted-foreground">Continuing Weight Downs</span>
                          <div className="mt-1 text-base font-bold text-rose-500">
                            -{t.continuingWeightDecreaseSumPct.toFixed(2)} pp
                          </div>
                          <span className="text-[10px] text-muted-foreground">Price/flow/rebalance adjustments</span>
                        </div>
                      </div>

                      {/* Largest continuing adjustments */}
                      {t.largestWeightChanges && t.largestWeightChanges.length > 0 && (
                        <div>
                          <span className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                            Largest Continuing Weight Adjustments
                          </span>
                          <div className="mt-1.5 flex flex-wrap gap-1.5">
                            {t.largestWeightChanges.map((change, i) => (
                              <span
                                key={i}
                                className="rounded bg-secondary/80 px-2 py-1 text-xs font-medium text-foreground border border-border/40 font-mono"
                              >
                                {change}
                              </span>
                            ))}
                          </div>
                        </div>
                      )}

                      <div className="flex items-center justify-between pt-2 border-t border-border/40 text-[11px] text-muted-foreground">
                        <span>
                          Scope Transition: <strong className="text-foreground">{t.earlierDisclosureScope}</strong> &rarr; <strong className="text-foreground">{t.laterDisclosureScope}</strong>
                        </span>
                        <span>
                          Reconciliation Residual: <strong className="font-mono text-foreground">{t.unresolvedDiscrepancyPct.toFixed(2)} pp</strong>
                        </span>
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* SUB-TAB 4: TURNOVER AUDIT */}
      {selectedSubTab === "TURNOVER_AUDIT" && (
        <div className="space-y-6">
          <div className="rounded-xl border border-amber-500/30 bg-amber-500/5 p-5 space-y-3">
            <div className="flex items-center gap-2 text-amber-500 font-semibold">
              <span>Regulatory Turnover Calculation Audit</span>
            </div>
            <p className="text-xs text-muted-foreground leading-relaxed">
              {data.turnoverEvaluation.turnoverReason}
            </p>
            <div className="rounded-lg bg-card/80 border border-border p-3 text-xs font-mono text-foreground">
              Formula: {data.turnoverEvaluation.descriptiveChangeFormula}
            </div>
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <div className="rounded-xl border border-border bg-card p-4">
              <span className="text-xs text-muted-foreground font-medium">Turnover Status</span>
              <div className="mt-1 text-sm font-bold text-amber-500 font-mono">
                {data.turnoverEvaluation.turnoverStatus}
              </div>
              <p className="mt-1 text-xs text-muted-foreground">
                Gated pending transaction buy/sell logs
              </p>
            </div>

            <div className="rounded-xl border border-border bg-card p-4">
              <span className="text-xs text-muted-foreground font-medium">Avg Monthly Composition Shift</span>
              <div className="mt-1 text-xl font-bold font-mono text-foreground">
                {data.turnoverEvaluation.averageMonthlyCompositionChangePct.toFixed(2)}%
              </div>
              <p className="mt-1 text-xs text-muted-foreground">
                Evaluated across {data.turnoverEvaluation.comparableTransitionsCount} comparable transition(s)
              </p>
            </div>

            <div className="rounded-xl border border-border bg-card p-4">
              <span className="text-xs text-muted-foreground font-medium">Annualized Descriptive Shift</span>
              <div className="mt-1 text-xl font-bold font-mono text-foreground">
                {data.turnoverEvaluation.annualizedDescriptiveChangePct.toFixed(2)}%
              </div>
              <p className="mt-1 text-xs text-muted-foreground">
                12-month extrapolation of observed weight changes
              </p>
            </div>
          </div>

          {/* Limitations and Disclaimers */}
          <div className="rounded-xl border border-border bg-card p-5 space-y-3">
            <h4 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              Turnover Limitations &amp; Epistemic Boundaries
            </h4>
            <ul className="list-disc list-inside space-y-1.5 text-xs text-muted-foreground">
              {data.turnoverEvaluation.limitationsAndDisclaimers.map((disc, idx) => (
                <li key={idx}>{disc}</li>
              ))}
            </ul>
          </div>
        </div>
      )}

      {/* LAYER 6: ALLOCATOR DUE DILIGENCE & INVESTIGATION QUESTIONS */}
      {data.investigationQuestions && data.investigationQuestions.length > 0 && (
        <div className="rounded-xl border border-border bg-card p-5 space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              Layer 6: Allocator Due Diligence &amp; Position Inquiry
            </span>
            <span className="text-[11px] text-muted-foreground font-mono">
              Calculation Run: {data.calculationRunId.slice(0, 8)}
            </span>
          </div>

          <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2">
            {data.investigationQuestions.map((question, idx) => (
              <div
                key={idx}
                className="rounded-lg border border-border/60 bg-secondary/30 p-3 text-xs flex items-start justify-between gap-3 hover:border-primary/40 transition-colors"
              >
                <p className="text-muted-foreground leading-relaxed flex-1">{question}</p>
                {onInvestigateQuestion && (
                  <button
                    type="button"
                    onClick={() =>
                      onInvestigateQuestion(
                        question,
                        "Holding longevity and position persistence due diligence"
                      )
                    }
                    className="shrink-0 text-primary hover:underline font-medium text-[11px]"
                  >
                    Investigate
                  </button>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* DATA QUALITY WARNINGS */}
      {data.dataQualityWarnings && data.dataQualityWarnings.length > 0 && (
        <div className="rounded-xl border border-amber-500/20 bg-amber-500/5 p-4 text-xs text-amber-500/90 space-y-1">
          <span className="font-semibold uppercase tracking-wider text-[10px]">Data Quality Disclosures:</span>
          <ul className="list-disc list-inside space-y-0.5 text-muted-foreground text-xs">
            {data.dataQualityWarnings.map((w, idx) => (
              <li key={idx}>{w}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
