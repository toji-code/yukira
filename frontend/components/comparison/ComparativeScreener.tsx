"use client";

import React, { useState, useMemo } from "react";
import Link from "next/link";
import {
  UniverseScreenerResponse,
  ScreenerItem,
  ScreenerMetricDefinition,
  ScreenerMetricValue
} from "@/lib/api/schemes";
import { useWatchlist } from "@/lib/hooks/useWatchlist";
import { ResearchNoteModal } from "@/components/watchlist/ResearchNoteModal";
import { WatchlistResponse } from "@/lib/api/investor";

interface ComparativeScreenerProps {
  screenerData: UniverseScreenerResponse;
  selectedOptionIds: number[];
  onToggleSelect: (optionId: number) => void;
  onLaunchDetailedComparison?: (optionIds: number[], horizon?: string) => void;
  isLoading?: boolean;
  activeHorizon?: string; // "3Y" | "5Y" | "10Y"
  onHorizonChange?: (horizon: string) => void;
}

type SortField =
  | "name"
  | "category"
  | "dates"
  | "status"
  | "RET-02"
  | "RET-03"
  | "RSK-01"
  | "RSK-03"
  | "MKT-01"
  | "MKT-02"
  | "RAT-04"
  | "REL-01";

type SortDirection = "asc" | "desc";

export function ComparativeScreener({
  screenerData,
  selectedOptionIds,
  onToggleSelect,
  onLaunchDetailedComparison,
  isLoading = false,
  activeHorizon = "3Y",
  onHorizonChange
}: ComparativeScreenerProps) {
  // Metadata Filter States
  const [selectedCategory, setSelectedCategory] = useState<string>("ALL");
  const [selectedAmc, setSelectedAmc] = useState<string>("ALL");
  const [selectedPlan, setSelectedPlan] = useState<"ALL" | "DIRECT" | "REGULAR">("ALL");
  const [selectedOptionType, setSelectedOptionType] = useState<"ALL" | "GROWTH" | "IDCW">("ALL");
  const [eligibilityFilter, setEligibilityFilter] = useState<"ALL" | "CALCULATED" | "ELIGIBLE" | "PARTIAL">("ALL");
  const [searchQuery, setSearchQuery] = useState<string>("");

  // Analytical Metric Filter States (Section 3B)
  const [showAnalyticalFilters, setShowAnalyticalFilters] = useState<boolean>(false);
  const [minCagr, setMinCagr] = useState<string>("");
  const [maxVolatility, setMaxVolatility] = useState<string>("");
  const [maxDrawdown, setMaxDrawdown] = useState<string>("");
  const [minBeta, setMinBeta] = useState<string>("");
  const [maxBeta, setMaxBeta] = useState<string>("");
  const [minInfoRatio, setMinInfoRatio] = useState<string>("");

  // Table Interactivity States
  const [sortField, setSortField] = useState<SortField>("dates");
  const [sortDirection, setSortDirection] = useState<SortDirection>("desc");
  const [activeMetricModal, setActiveMetricModal] = useState<ScreenerMetricDefinition | null>(null);
  const [expandedRowId, setExpandedRowId] = useState<number | null>(null);

  // Research Workspace & Due Diligence Integration (Phase B6)
  const { watchlistItems, isWatchlisted, toggleWatchlist, saveResearchNote } = useWatchlist();
  const [editingFund, setEditingFund] = useState<WatchlistResponse | null>(null);
  const [batchSaveMessage, setBatchSaveMessage] = useState<string | null>(null);

  // Derived effective horizon
  const effectiveHorizon = screenerData.horizon || activeHorizon;
  const horizonThreshold = screenerData.horizonThreshold ?? (effectiveHorizon === "10Y" ? 2400 : effectiveHorizon === "5Y" ? 1200 : 700);

  // Unique AMCs extracted from universe items
  const uniqueAmcs = useMemo(() => {
    const amcs = new Set<string>();
    screenerData.items.forEach((item) => {
      if (item.amcName && item.amcName.trim().length > 0 && item.amcName !== "Unknown AMC") {
        amcs.add(item.amcName.trim());
      }
    });
    return Array.from(amcs).sort((a, b) => a.localeCompare(b));
  }, [screenerData.items]);

  // Category Tabs Configuration
  const categories = useMemo(() => [
    { key: "ALL", label: "All Categories", count: screenerData.totalReferenceOptions, calculated: screenerData.calculatedOptionsCount, bmCode: null },
    { key: "FLEXI_CAP", label: "Flexi Cap", count: screenerData.categorySummaries?.FLEXI_CAP?.totalOptions ?? 47, calculated: screenerData.categorySummaries?.FLEXI_CAP?.calculatedCount ?? 0, bmCode: "NIFTY_500_TRI" },
    { key: "LARGE_CAP", label: "Large Cap", count: screenerData.categorySummaries?.LARGE_CAP?.totalOptions ?? 39, calculated: screenerData.categorySummaries?.LARGE_CAP?.calculatedCount ?? 0, bmCode: "NIFTY_100_TRI" },
    { key: "LARGE_AND_MID_CAP", label: "Large & Mid Cap", count: screenerData.categorySummaries?.LARGE_AND_MID_CAP?.totalOptions ?? 36, calculated: screenerData.categorySummaries?.LARGE_AND_MID_CAP?.calculatedCount ?? 0, bmCode: "NIFTY_LARGEMIDCAP_250_TRI" },
    { key: "MID_CAP", label: "Mid Cap", count: screenerData.categorySummaries?.MID_CAP?.totalOptions ?? 34, calculated: screenerData.categorySummaries?.MID_CAP?.calculatedCount ?? 0, bmCode: "NIFTY_MIDCAP_150_TRI" },
    { key: "SMALL_CAP", label: "Small Cap", count: screenerData.categorySummaries?.SMALL_CAP?.totalOptions ?? 37, calculated: screenerData.categorySummaries?.SMALL_CAP?.calculatedCount ?? 0, bmCode: "NIFTY_SMALLCAP_250_TRI" },
  ], [screenerData]);

  // Active analytical filter count
  const activeAnalyticalFilterCount = useMemo(() => {
    let count = 0;
    if (minCagr !== "") count++;
    if (maxVolatility !== "") count++;
    if (maxDrawdown !== "") count++;
    if (minBeta !== "") count++;
    if (maxBeta !== "") count++;
    if (minInfoRatio !== "") count++;
    return count;
  }, [minCagr, maxVolatility, maxDrawdown, minBeta, maxBeta, minInfoRatio]);

  // Reset analytical filters
  const handleClearAnalyticalFilters = () => {
    setMinCagr("");
    setMaxVolatility("");
    setMaxDrawdown("");
    setMinBeta("");
    setMaxBeta("");
    setMinInfoRatio("");
  };

  // Reset all filters
  const handleResetAllFilters = () => {
    setSelectedCategory("ALL");
    setSelectedAmc("ALL");
    setSelectedPlan("ALL");
    setSelectedOptionType("ALL");
    setEligibilityFilter("ALL");
    setSearchQuery("");
    handleClearAnalyticalFilters();
  };

  // Helper to determine plan type
  const getItemPlanType = (item: ScreenerItem): "DIRECT" | "REGULAR" => {
    if (item.planType) {
      return item.planType.toUpperCase().includes("DIRECT") ? "DIRECT" : "REGULAR";
    }
    const nameUpper = item.schemeName.toUpperCase();
    if (nameUpper.includes("DIRECT")) return "DIRECT";
    return "REGULAR";
  };

  // Helper to determine option type
  const getItemOptionType = (item: ScreenerItem): "GROWTH" | "IDCW" => {
    if (item.optionType) {
      return item.optionType.toUpperCase().includes("GROWTH") ? "GROWTH" : "IDCW";
    }
    const nameUpper = item.schemeName.toUpperCase();
    if (nameUpper.includes("IDCW") || nameUpper.includes("DIVIDEND")) return "IDCW";
    return "GROWTH";
  };

  // Batch save selected screener options to workspace without losing active filters
  const handleBatchSaveToWorkspace = async () => {
    let savedCount = 0;
    for (const optId of selectedOptionIds) {
      const item = screenerData.items.find((i) => i.schemeOptionId === optId);
      if (item) {
        const sId = item.schemeId || item.schemeOptionId;
        if (!isWatchlisted(sId, optId)) {
          await toggleWatchlist(sId, optId, {
            schemeName: item.schemeName,
            category: item.category,
            amcName: item.amcName,
            optionType: getItemOptionType(item),
            planType: getItemPlanType(item),
            amfiCode: item.amfiCode,
            isin: item.isin,
          });
          savedCount++;
        }
      }
    }
    setBatchSaveMessage(
      savedCount > 0
        ? `Saved ${savedCount} fund${savedCount > 1 ? "s" : ""} to Research Workspace`
        : "Selected funds are already saved in Research Workspace"
    );
    setTimeout(() => setBatchSaveMessage(null), 3500);
  };

  // Combined Filtering Logic with Explicit Metric Availability & Eligibility Rules
  const { filteredItems, excludedByAnalyticalCount } = useMemo(() => {
    let excludedAnalytical = 0;

    const filtered = screenerData.items.filter((item) => {
      // 1. Category Filter
      if (selectedCategory !== "ALL" && item.category !== selectedCategory) {
        return false;
      }

      // 2. AMC Filter
      if (selectedAmc !== "ALL" && item.amcName !== selectedAmc) {
        return false;
      }

      // 3. Plan Type Filter (Direct vs Regular)
      if (selectedPlan !== "ALL") {
        const itemPlan = getItemPlanType(item);
        if (itemPlan !== selectedPlan) {
          return false;
        }
      }

      // 4. Option Type Filter (Growth vs IDCW)
      if (selectedOptionType !== "ALL") {
        const itemOption = getItemOptionType(item);
        if (itemOption !== selectedOptionType) {
          return false;
        }
      }

      // 5. Horizon Eligibility / Hydration Filter
      const isEligible = item.isEligibleForHorizon ?? item.isEligibleFor3Y;
      if (eligibilityFilter === "CALCULATED") {
        if (item.calculationStatus !== "CALCULATED" && item.calculationStatus !== "PARTIALLY_CALCULATED" && (item.validMetricCount ?? 0) === 0) {
          return false;
        }
      } else if (eligibilityFilter === "ELIGIBLE") {
        if (!isEligible) {
          return false;
        }
      } else if (eligibilityFilter === "PARTIAL") {
        if (isEligible) {
          return false;
        }
      }

      // 6. Text Search Filter (name, AMC, AMFI code, ISIN)
      if (searchQuery.trim().length > 0) {
        const q = searchQuery.toLowerCase().trim();
        const matchesName = item.schemeName.toLowerCase().includes(q);
        const matchesAmc = item.amcName.toLowerCase().includes(q);
        const matchesAmfi = item.amfiCode?.toLowerCase().includes(q) ?? false;
        const matchesIsin = item.isin?.toLowerCase().includes(q) ?? false;
        if (!matchesName && !matchesAmc && !matchesAmfi && !matchesIsin) {
          return false;
        }
      }

      // 7. Analytical Filters (Section 3B: Strictly deterministic, no zero-substitution)
      if (activeAnalyticalFilterCount > 0) {
        // Min CAGR (RET-03)
        if (minCagr !== "") {
          const val = item.metrics["RET-03"]?.numericValue;
          const status = item.metrics["RET-03"]?.calculationStatus;
          if (val === null || val === undefined || status !== "CALCULATED") {
            excludedAnalytical++;
            return false;
          }
          const cagrPct = val * 100.0;
          if (cagrPct < parseFloat(minCagr)) {
            excludedAnalytical++;
            return false;
          }
        }

        // Max Volatility (RSK-01)
        if (maxVolatility !== "") {
          const val = item.metrics["RSK-01"]?.numericValue;
          const status = item.metrics["RSK-01"]?.calculationStatus;
          if (val === null || val === undefined || status !== "CALCULATED") {
            excludedAnalytical++;
            return false;
          }
          const volPct = val * 100.0;
          if (volPct > parseFloat(maxVolatility)) {
            excludedAnalytical++;
            return false;
          }
        }

        // Max Drawdown (RSK-03: negative value, e.g. -20% -> no worse than -20%)
        if (maxDrawdown !== "") {
          const val = item.metrics["RSK-03"]?.numericValue;
          const status = item.metrics["RSK-03"]?.calculationStatus;
          if (val === null || val === undefined || status !== "CALCULATED") {
            excludedAnalytical++;
            return false;
          }
          const mddPct = val * 100.0;
          const limit = parseFloat(maxDrawdown);
          const normalizedLimit = limit > 0 ? -limit : limit; // ensure negative threshold
          if (mddPct < normalizedLimit) {
            excludedAnalytical++;
            return false;
          }
        }

        // Min Beta (MKT-01)
        if (minBeta !== "") {
          const val = item.metrics["MKT-01"]?.numericValue;
          const status = item.metrics["MKT-01"]?.calculationStatus;
          if (val === null || val === undefined || status !== "CALCULATED") {
            excludedAnalytical++;
            return false;
          }
          if (val < parseFloat(minBeta)) {
            excludedAnalytical++;
            return false;
          }
        }

        // Max Beta (MKT-01)
        if (maxBeta !== "") {
          const val = item.metrics["MKT-01"]?.numericValue;
          const status = item.metrics["MKT-01"]?.calculationStatus;
          if (val === null || val === undefined || status !== "CALCULATED") {
            excludedAnalytical++;
            return false;
          }
          if (val > parseFloat(maxBeta)) {
            excludedAnalytical++;
            return false;
          }
        }

        // Min Information Ratio (RAT-04)
        if (minInfoRatio !== "") {
          const val = item.metrics["RAT-04"]?.numericValue;
          const status = item.metrics["RAT-04"]?.calculationStatus;
          if (val === null || val === undefined || status !== "CALCULATED") {
            excludedAnalytical++;
            return false;
          }
          if (val < parseFloat(minInfoRatio)) {
            excludedAnalytical++;
            return false;
          }
        }
      }

      return true;
    });

    return { filteredItems: filtered, excludedByAnalyticalCount: excludedAnalytical };
  }, [
    screenerData.items,
    selectedCategory,
    selectedAmc,
    selectedPlan,
    selectedOptionType,
    eligibilityFilter,
    searchQuery,
    activeAnalyticalFilterCount,
    minCagr,
    maxVolatility,
    maxDrawdown,
    minBeta,
    maxBeta,
    minInfoRatio
  ]);

  // Sorting Logic with Deterministic Missing-Value Partitioning
  const sortedItems = useMemo(() => {
    return [...filteredItems].sort((a, b) => {
      if (sortField === "name") {
        const valA = a.schemeName.toLowerCase();
        const valB = b.schemeName.toLowerCase();
        if (valA < valB) return sortDirection === "asc" ? -1 : 1;
        if (valA > valB) return sortDirection === "asc" ? 1 : -1;
        return a.schemeOptionId - b.schemeOptionId;
      }

      if (sortField === "category") {
        const valA = a.categoryDisplayName.toLowerCase();
        const valB = b.categoryDisplayName.toLowerCase();
        if (valA < valB) return sortDirection === "asc" ? -1 : 1;
        if (valA > valB) return sortDirection === "asc" ? 1 : -1;
        return a.schemeOptionId - b.schemeOptionId;
      }

      if (sortField === "dates") {
        const valA = a.distinctNavDates;
        const valB = b.distinctNavDates;
        if (valA < valB) return sortDirection === "asc" ? -1 : 1;
        if (valA > valB) return sortDirection === "asc" ? 1 : -1;
        return a.schemeOptionId - b.schemeOptionId;
      }

      if (sortField === "status") {
        const valA = a.calculationStatus ?? "";
        const valB = b.calculationStatus ?? "";
        if (valA < valB) return sortDirection === "asc" ? -1 : 1;
        if (valA > valB) return sortDirection === "asc" ? 1 : -1;
        return a.schemeOptionId - b.schemeOptionId;
      }

      // Metric sort: Deterministic missing-value partitioning.
      // Missing values always partition to the bottom regardless of sortDirection.
      const numA = a.metrics[sortField]?.numericValue;
      const numB = b.metrics[sortField]?.numericValue;
      const hasA = numA !== undefined && numA !== null && a.metrics[sortField]?.calculationStatus === "CALCULATED";
      const hasB = numB !== undefined && numB !== null && b.metrics[sortField]?.calculationStatus === "CALCULATED";

      if (!hasA && !hasB) return a.schemeOptionId - b.schemeOptionId;
      if (!hasA) return 1; // missing values always sink to the bottom
      if (!hasB) return -1;

      if (numA! < numB!) return sortDirection === "asc" ? -1 : 1;
      if (numA! > numB!) return sortDirection === "asc" ? 1 : -1;
      return a.schemeOptionId - b.schemeOptionId;
    });
  }, [filteredItems, sortField, sortDirection]);

  const handleSort = (field: SortField) => {
    if (sortField === field) {
      setSortDirection((prev) => (prev === "asc" ? "desc" : "asc"));
    } else {
      setSortField(field);
      setSortDirection("desc");
    }
  };

  const getSortIndicator = (field: SortField) => {
    if (sortField !== field) return "↕";
    return sortDirection === "asc" ? "↑" : "↓";
  };

  const getPercentileBadgeStyle = (
    percentileRank: number | null | undefined,
    rankingDirection: string | null | undefined
  ): string => {
    if (percentileRank === null || percentileRank === undefined) {
      return "bg-surface-navy-raised text-text-cream-muted/50 border-border-gold-subtle/30";
    }

    if (rankingDirection === "LOWER_IS_BETTER") {
      // Risk metrics (Volatility, Downside Beta, Tracking Error):
      // Lower percentile = lower risk / defensive -> calm green
      // Higher percentile = higher risk -> rose
      if (percentileRank <= 30) {
        return "bg-emerald-500/15 text-emerald-300 border-emerald-500/30";
      }
      if (percentileRank <= 70) {
        return "bg-cyan-500/15 text-cyan-300 border-cyan-500/30";
      }
      return "bg-rose-500/15 text-rose-300 border-rose-500/30";
    }

    if (rankingDirection === "HIGHER_IS_BETTER") {
      // Return and efficiency metrics:
      // Higher percentile = higher return / shallower drawdown -> green
      if (percentileRank >= 70) {
        return "bg-emerald-500/15 text-emerald-300 border-emerald-500/30";
      }
      if (percentileRank >= 30) {
        return "bg-cyan-500/15 text-cyan-300 border-cyan-500/30";
      }
      return "bg-rose-500/15 text-rose-300 border-rose-500/30";
    }

    return "bg-cyan-500/15 text-cyan-300 border-cyan-500/30";
  };

  const getPercentileTooltip = (
    metric: ScreenerMetricValue,
    categoryDisplayName: string,
    asOfDate: string
  ): string => {
    if (metric.percentileRank !== null && metric.percentileRank !== undefined) {
      const dirText =
        metric.rankingDirection === "HIGHER_IS_BETTER"
          ? "Higher value indicates stronger relative performance"
          : metric.rankingDirection === "LOWER_IS_BETTER"
          ? "Lower value indicates lower risk or tighter benchmark tracking"
          : "Descriptive market sensitivity metric";

      const cohortStatusText =
        metric.peerCohortStatus === "PRODUCTION_CALIBRATED"
          ? `Peer Cohort Sample Size: ADEQUATE (Valid Peers N = ${metric.peerDenominator ?? 15} >= 15)`
          : metric.peerCohortStatus === "CONFIDENCE_LIMITED_SMALL_COHORT"
          ? `Peer Cohort Sample Size: LIMITED_SMALL_COHORT (Valid Peers N = ${metric.peerDenominator ?? "N/A"} < 15)\n⚠️ ${metric.unavailableReason || "Small peer cohort (N < 15 required for cohort sample-size adequacy)"}`
          : `Peer Cohort Status: ${metric.peerCohortStatus ?? "UNAVAILABLE"}`;

      return (
        `${metric.metricCode} Category Percentile: ${metric.formattedPercentile} (${metric.percentileRank}th percentile)\n` +
        `Category: ${categoryDisplayName} (Peer Cohort N = ${metric.peerDenominator ?? "N/A"})\n` +
        `Evaluation Date: ${asOfDate} (${metric.periodType})\n` +
        `Ranking Direction: ${metric.rankingDirection ?? "N/A"}\n` +
        `Interpretation: ${dirText}\n` +
        `${cohortStatusText}\n` +
        `Note: Empirical category percentile relative to peer cohort (DEC-V1-02); describes historical sample distribution only, not predictive investment advice or methodology approval.`
      );
    }

    return metric.unavailableReason || "Percentile ranking not available for this observation";
  };

  const renderMetricCell = (
    item: ScreenerItem,
    metricCode: string,
    valueColorClass: string
  ) => {
    const metric = item.metrics[metricCode];
    if (!metric) {
      return (
        <span className="text-text-cream-muted/40 font-mono" title={item.exclusionReason ?? "Calculation pending"}>
          —
        </span>
      );
    }

    if (metric.numericValue === null || metric.calculationStatus !== "CALCULATED") {
      const reason = metric.errorMessage || metric.unavailableReason || `Status: ${metric.calculationStatus}`;
      const isRestricted = metric.calculationStatus === "RESTRICTED" || reason.includes("restricted") || reason.includes("Governance");
      return (
        <div className="flex flex-col items-end gap-0.5" title={reason}>
          <span className="text-text-cream-muted/50 font-mono text-xs">
            N/A
          </span>
          <span
            className={`font-mono text-[8px] cursor-help px-1 rounded border ${
              isRestricted
                ? "bg-purple-500/10 text-purple-300 border-purple-500/20"
                : "bg-amber-500/10 text-amber-400/80 border-amber-500/20"
            }`}
            title={reason}
          >
            {isRestricted ? "RESTRICTED" : metric.calculationStatus === "INSUFFICIENT_DATA" ? "INSUF" : metric.calculationStatus}
          </span>
        </div>
      );
    }

    return (
      <div className="flex flex-col items-end gap-0.5">
        <span className={`${valueColorClass} font-medium`}>
          {metric.formattedValue}
        </span>
        {metric.formattedPercentile ? (
          <span
            className={`font-mono text-[9px] px-1.5 py-0.2 rounded font-bold border transition-colors cursor-help ${getPercentileBadgeStyle(
              metric.percentileRank,
              metric.rankingDirection
            )}`}
            title={getPercentileTooltip(metric, item.categoryDisplayName, screenerData.asOfDate)}
          >
            {metric.formattedPercentile}
          </span>
        ) : metric.unavailableReason ? (
          <span
            className="font-mono text-[8px] text-text-cream-muted/50 cursor-help"
            title={metric.unavailableReason}
          >
            P—
          </span>
        ) : null}
      </div>
    );
  };

  return (
    <div className="space-y-6">
      {/* Horizon Switcher & Governance Context Bar */}
      <div className="rounded-xl border border-border-gold-subtle/50 bg-surface-midnight p-4 shadow-sm">
        <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-mono text-[10.5px] uppercase tracking-wider text-text-cream-muted font-bold mr-1">
              Evaluation Horizon:
            </span>
            {(["3Y", "5Y", "10Y"] as const).map((h) => {
              const isCurrent = effectiveHorizon === h;
              const isFrozenApproved = h === "3Y";
              const thresholdText = h === "3Y" ? "≥ 700d" : h === "5Y" ? "≥ 1,200d" : "≥ 2,400d";
              return (
                <button
                  key={h}
                  type="button"
                  onClick={() => onHorizonChange?.(h)}
                  disabled={!onHorizonChange || isLoading}
                  className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg font-mono text-xs font-semibold transition-all border ${
                    isCurrent
                      ? "bg-accent text-surface-midnight border-accent shadow-sm"
                      : "bg-surface-navy-raised/50 text-text-cream-muted hover:text-text-cream-bright border-border-gold-subtle/40 hover:bg-surface-navy-raised"
                  }`}
                >
                  <span>{h}</span>
                  <span className={`text-[10px] px-1 rounded ${isCurrent ? "bg-surface-midnight/20 text-surface-midnight" : "text-text-cream-muted"}`}>
                    {thresholdText}
                  </span>
                  <span className={`text-[9px] px-1 rounded font-bold uppercase tracking-wider ${
                    isCurrent
                      ? "bg-surface-midnight/30 text-surface-midnight"
                      : isFrozenApproved
                      ? "text-emerald-400 bg-emerald-500/10 border border-emerald-500/20"
                      : "text-amber-400 bg-amber-500/10 border border-amber-500/20"
                  }`}>
                    {isFrozenApproved ? "APPROVED" : "CANDIDATE"}
                  </span>
                </button>
              );
            })}
          </div>

          <div className="flex items-center gap-2 text-xs font-mono text-text-cream-muted">
            <span className="hidden sm:inline">Reference Cutoff:</span>
            <span className="font-semibold text-text-cream-bright px-2 py-0.5 rounded bg-surface-navy-raised border border-border-gold-subtle/30">
              {screenerData.asOfDate}
            </span>
            {screenerData.horizonStartDate && (
              <span className="text-[11px] text-text-cream-muted/70">
                (Window: {screenerData.horizonStartDate} → {screenerData.asOfDate})
              </span>
            )}
          </div>
        </div>

        {/* Multi-cycle Horizon Governance Disclaimer */}
        {effectiveHorizon !== "3Y" && (
          <div className="mt-3 rounded-lg border border-purple-500/30 bg-purple-500/10 p-2.5 text-[11.5px] text-purple-200">
            <span className="font-mono font-bold uppercase text-[10px] tracking-wider text-purple-300 block mb-0.5">
              ⚠️ {effectiveHorizon} Multi-Cycle Candidate Horizon Notice
            </span>
            Multi-cycle evaluation ({effectiveHorizon}) requires {horizonThreshold} distinct trading days. Relative risk-adjusted measures (e.g. Information Ratio RAT-04) operate under candidate governance restrictions pending multi-cycle benchmark TRI recalibration (Phase B1/B2 governance rules).
          </div>
        )}
      </div>

      {/* Top Screener Summary & Universe Breakdown */}
      <div className="rounded-xl border border-border-gold-subtle/40 bg-surface-midnight/80 p-5 backdrop-blur-[16px] shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <div className="flex items-center gap-2">
              <span className="font-mono text-[10px] uppercase tracking-wider text-cyan-400 font-semibold px-2 py-0.5 rounded bg-cyan-400/10 border border-cyan-400/20">
                Institutional Screener &amp; Shortlist Engine
              </span>
              <span className="font-mono text-[10px] text-text-cream-muted">
                {effectiveHorizon} Evaluation Window
              </span>
            </div>
            <h2 className="mt-1 font-sans text-xl font-bold tracking-tight text-text-cream-bright">
              Mutual Fund Universe Screener
            </h2>
            <p className="mt-0.5 text-xs text-text-cream-muted max-w-2xl">
              193 registered equity options evaluated across authentic NAV observations, candidate benchmark TRIs, and non-parametric empirical category distributions. Select up to 4 funds for direct side-by-side comparison.
            </p>
          </div>

          {/* Quick Stats Badges */}
          <div className="flex flex-wrap items-center gap-2 font-mono text-[11px]">
            <div className="rounded-lg border border-border-gold-subtle/50 bg-surface-navy-raised/50 px-3 py-1.5 text-center">
              <div className="text-text-cream-muted text-[10px] uppercase">Fund Universe</div>
              <div className="font-bold text-text-cream-bright">{screenerData.totalReferenceOptions} Options</div>
            </div>
            <div className="rounded-lg border border-emerald-500/30 bg-emerald-500/10 px-3 py-1.5 text-center text-emerald-300">
              <div className="text-emerald-400/80 text-[10px] uppercase">{effectiveHorizon} Eligible (≥ {horizonThreshold}d)</div>
              <div className="font-bold">{screenerData.eligibleOptionsCount} Options</div>
            </div>
            <div className="rounded-lg border border-amber-500/30 bg-amber-500/10 px-3 py-1.5 text-center text-amber-300">
              <div className="text-amber-400/80 text-[10px] uppercase">{effectiveHorizon} Partial (&lt; {horizonThreshold}d)</div>
              <div className="font-bold">{screenerData.partialOptionsCount} Options</div>
            </div>
            <div className="rounded-lg border border-cyan-500/30 bg-cyan-500/10 px-3 py-1.5 text-center text-cyan-300">
              <div className="text-cyan-400/80 text-[10px] uppercase">Hydrated Runs</div>
              <div className="font-bold">{screenerData.calculatedOptionsCount} Calculated</div>
            </div>
          </div>
        </div>

        {/* Category Tabs */}
        <div className="mt-5 flex flex-wrap gap-1.5 border-t border-border-gold-subtle/20 pt-4">
          {categories.map((cat) => {
            const isSelected = selectedCategory === cat.key;
            return (
              <button
                key={cat.key}
                type="button"
                onClick={() => setSelectedCategory(cat.key)}
                className={`flex items-center gap-2 rounded-lg px-3 py-1.5 text-xs font-medium transition-all ${
                  isSelected
                    ? "bg-primary text-surface-midnight font-bold shadow"
                    : "bg-surface-navy-raised/40 text-text-cream-muted hover:text-text-cream-bright hover:bg-surface-navy-raised/80 border border-border-gold-subtle/30"
                }`}
              >
                <span>{cat.label}</span>
                <span className={`rounded px-1.5 py-0.2 font-mono text-[10px] ${
                  isSelected ? "bg-surface-midnight/20 text-surface-midnight" : "bg-surface-midnight/40 text-text-cream-muted"
                }`}>
                  {cat.count}
                </span>
                {cat.bmCode && (
                  <span className={`font-mono text-[9px] px-1 rounded ${
                    isSelected ? "bg-surface-midnight/30 text-surface-midnight" : "text-cyan-400/80"
                  }`}>
                    {cat.bmCode.replace("_TRI", "")}
                  </span>
                )}
              </button>
            );
          })}
        </div>

        {/* Primary Filter Bar: Search, AMC, Plan, Option, Eligibility */}
        <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-12 items-center">
          {/* Text Search Field */}
          <div className="lg:col-span-4 relative">
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search scheme, AMC, AMFI (e.g. 118955), ISIN..."
              className="w-full rounded-lg border border-border-gold-subtle/50 bg-surface-midnight/90 px-3.5 py-2 text-xs text-text-cream-bright placeholder-text-cream-muted/60 focus:border-cyan-400 focus:outline-none"
            />
            {searchQuery && (
              <button
                type="button"
                onClick={() => setSearchQuery("")}
                className="absolute right-2.5 top-2 text-xs text-text-cream-muted hover:text-text-cream-bright"
              >
                ✕
              </button>
            )}
          </div>

          {/* AMC Selector Dropdown */}
          <div className="lg:col-span-3">
            <select
              value={selectedAmc}
              onChange={(e) => setSelectedAmc(e.target.value)}
              className="w-full rounded-lg border border-border-gold-subtle/50 bg-surface-midnight px-3 py-2 text-xs text-text-cream-bright focus:border-cyan-400 focus:outline-none"
            >
              <option value="ALL">All AMCs ({uniqueAmcs.length})</option>
              {uniqueAmcs.map((amc) => (
                <option key={amc} value={amc}>
                  {amc}
                </option>
              ))}
            </select>
          </div>

          {/* Plan Selector (Direct vs Regular) */}
          <div className="lg:col-span-2">
            <select
              value={selectedPlan}
              onChange={(e) => setSelectedPlan(e.target.value as any)}
              className="w-full rounded-lg border border-border-gold-subtle/50 bg-surface-midnight px-3 py-2 text-xs text-text-cream-bright focus:border-cyan-400 focus:outline-none"
            >
              <option value="ALL">Plan: All Plans</option>
              <option value="DIRECT">Direct Plan</option>
              <option value="REGULAR">Regular Plan</option>
            </select>
          </div>

          {/* Option Selector (Growth vs IDCW) */}
          <div className="lg:col-span-2">
            <select
              value={selectedOptionType}
              onChange={(e) => setSelectedOptionType(e.target.value as any)}
              className="w-full rounded-lg border border-border-gold-subtle/50 bg-surface-midnight px-3 py-2 text-xs text-text-cream-bright focus:border-cyan-400 focus:outline-none"
            >
              <option value="ALL">Option: All Types</option>
              <option value="GROWTH">Growth Option</option>
              <option value="IDCW">IDCW / Dividend</option>
            </select>
          </div>

          {/* Analytical Filters Toggle Button */}
          <div className="lg:col-span-1 flex justify-end">
            <button
              type="button"
              onClick={() => setShowAnalyticalFilters((prev) => !prev)}
              className={`w-full flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg text-xs font-semibold transition-all border ${
                showAnalyticalFilters || activeAnalyticalFilterCount > 0
                  ? "bg-cyan-500/20 text-cyan-300 border-cyan-500/40"
                  : "bg-surface-navy-raised/50 text-text-cream-muted border-border-gold-subtle/40 hover:text-text-cream-bright"
              }`}
              title="Toggle analytical metric filters"
            >
              <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6V4m0 2a2 2 0 100 4m0-4a2 2 0 110 4m-6 8a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4m6 6v10m6-2a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4" />
              </svg>
              <span>Metrics</span>
              {activeAnalyticalFilterCount > 0 && (
                <span className="w-4 h-4 rounded-full bg-cyan-400 text-surface-midnight text-[10px] font-bold flex items-center justify-center">
                  {activeAnalyticalFilterCount}
                </span>
              )}
            </button>
          </div>
        </div>

        {/* Collapsible Horizon-Aware Analytical Metric Filters Panel (Section 3B) */}
        {showAnalyticalFilters && (
          <div className="mt-4 rounded-xl border border-cyan-500/30 bg-surface-navy-raised/60 p-4 space-y-3">
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 border-b border-border-gold-subtle/20 pb-2.5">
              <div>
                <span className="font-mono text-[10.5px] uppercase tracking-wider text-cyan-300 font-bold block">
                  Deterministic Quantitative Filters ({effectiveHorizon} Horizon)
                </span>
                <p className="text-[11px] text-text-cream-muted">
                  Filter by validated metrics. Excludes funds missing sufficient history for the {effectiveHorizon} evaluation window without converting missing metrics to zero.
                </p>
              </div>
              {activeAnalyticalFilterCount > 0 && (
                <button
                  type="button"
                  onClick={handleClearAnalyticalFilters}
                  className="font-mono text-[11px] text-amber-400 hover:text-amber-300 underline"
                >
                  Clear Metric Filters ({activeAnalyticalFilterCount})
                </button>
              )}
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
              {/* Min CAGR */}
              <div>
                <label className="block text-[10px] font-mono uppercase text-text-cream-muted mb-1">
                  Min CAGR % (RET-03)
                </label>
                <input
                  type="number"
                  step="0.5"
                  placeholder="e.g. 15.0"
                  value={minCagr}
                  onChange={(e) => setMinCagr(e.target.value)}
                  className="w-full rounded-md border border-border-gold-subtle/50 bg-surface-midnight px-2.5 py-1.5 text-xs text-text-cream-bright font-mono focus:border-cyan-400 focus:outline-none"
                />
              </div>

              {/* Max Volatility */}
              <div>
                <label className="block text-[10px] font-mono uppercase text-text-cream-muted mb-1">
                  Max Volatility % (RSK-01)
                </label>
                <input
                  type="number"
                  step="0.5"
                  placeholder="e.g. 16.0"
                  value={maxVolatility}
                  onChange={(e) => setMaxVolatility(e.target.value)}
                  className="w-full rounded-md border border-border-gold-subtle/50 bg-surface-midnight px-2.5 py-1.5 text-xs text-text-cream-bright font-mono focus:border-cyan-400 focus:outline-none"
                />
              </div>

              {/* Max Drawdown */}
              <div>
                <label className="block text-[10px] font-mono uppercase text-text-cream-muted mb-1">
                  Max Drawdown (RSK-03)
                </label>
                <input
                  type="number"
                  step="1"
                  placeholder="e.g. -20"
                  value={maxDrawdown}
                  onChange={(e) => setMaxDrawdown(e.target.value)}
                  className="w-full rounded-md border border-border-gold-subtle/50 bg-surface-midnight px-2.5 py-1.5 text-xs text-text-cream-bright font-mono focus:border-cyan-400 focus:outline-none"
                />
              </div>

              {/* Min Beta */}
              <div>
                <label className="block text-[10px] font-mono uppercase text-text-cream-muted mb-1">
                  Min Beta (MKT-01)
                </label>
                <input
                  type="number"
                  step="0.05"
                  placeholder="e.g. 0.80"
                  value={minBeta}
                  onChange={(e) => setMinBeta(e.target.value)}
                  className="w-full rounded-md border border-border-gold-subtle/50 bg-surface-midnight px-2.5 py-1.5 text-xs text-text-cream-bright font-mono focus:border-cyan-400 focus:outline-none"
                />
              </div>

              {/* Max Beta */}
              <div>
                <label className="block text-[10px] font-mono uppercase text-text-cream-muted mb-1">
                  Max Beta (MKT-01)
                </label>
                <input
                  type="number"
                  step="0.05"
                  placeholder="e.g. 1.15"
                  value={maxBeta}
                  onChange={(e) => setMaxBeta(e.target.value)}
                  className="w-full rounded-md border border-border-gold-subtle/50 bg-surface-midnight px-2.5 py-1.5 text-xs text-text-cream-bright font-mono focus:border-cyan-400 focus:outline-none"
                />
              </div>

              {/* Min Information Ratio */}
              <div>
                <label className="block text-[10px] font-mono uppercase text-text-cream-muted mb-1">
                  Min Info Ratio (RAT-04)
                </label>
                <input
                  type="number"
                  step="0.1"
                  placeholder="e.g. 0.50"
                  value={minInfoRatio}
                  onChange={(e) => setMinInfoRatio(e.target.value)}
                  className="w-full rounded-md border border-border-gold-subtle/50 bg-surface-midnight px-2.5 py-1.5 text-xs text-text-cream-bright font-mono focus:border-cyan-400 focus:outline-none"
                />
              </div>
            </div>

            {/* Explanatory badge for missing metrics exclusion */}
            <div className="flex items-center gap-2 text-[11px] font-mono text-cyan-300/80 bg-cyan-500/10 px-3 py-1.5 rounded-lg border border-cyan-500/20">
              <svg className="w-3.5 h-3.5 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <span>
                Zero Client-Side Math Discipline: Funds missing metrics for the active horizon are excluded rather than assigned fabricated zeros or default values.
              </span>
            </div>
          </div>
        )}

        {/* History Sufficiency Segment Tabs */}
        <div className="mt-4 flex flex-wrap items-center justify-between gap-3 pt-3 border-t border-border-gold-subtle/20 text-xs">
          <div className="flex items-center gap-1 bg-surface-midnight/90 p-1 rounded-lg border border-border-gold-subtle/40">
            <button
              type="button"
              onClick={() => setEligibilityFilter("ALL")}
              className={`px-2.5 py-1 rounded text-xs transition-colors ${
                eligibilityFilter === "ALL"
                  ? "bg-surface-navy-raised text-text-cream-bright font-semibold"
                  : "text-text-cream-muted hover:text-text-cream-bright"
              }`}
            >
              All Options ({filteredItems.length})
            </button>
            <button
              type="button"
              onClick={() => setEligibilityFilter("CALCULATED")}
              className={`px-2.5 py-1 rounded text-xs transition-colors ${
                eligibilityFilter === "CALCULATED"
                  ? "bg-cyan-500/20 text-cyan-300 font-semibold border border-cyan-500/30"
                  : "text-text-cream-muted hover:text-text-cream-bright"
              }`}
            >
              Hydrated ({screenerData.calculatedOptionsCount})
            </button>
            <button
              type="button"
              onClick={() => setEligibilityFilter("ELIGIBLE")}
              className={`px-2.5 py-1 rounded text-xs transition-colors ${
                eligibilityFilter === "ELIGIBLE"
                  ? "bg-emerald-500/20 text-emerald-300 font-semibold"
                  : "text-text-cream-muted hover:text-text-cream-bright"
              }`}
            >
              Eligible (≥ {horizonThreshold}d)
            </button>
            <button
              type="button"
              onClick={() => setEligibilityFilter("PARTIAL")}
              className={`px-2.5 py-1 rounded text-xs transition-colors ${
                eligibilityFilter === "PARTIAL"
                  ? "bg-amber-500/20 text-amber-300 font-semibold"
                  : "text-text-cream-muted hover:text-text-cream-bright"
              }`}
            >
              Partial History (&lt; {horizonThreshold}d)
            </button>
          </div>

          {/* Active Filter Indicators & Reset CTA */}
          <div className="flex items-center gap-2">
            {(selectedCategory !== "ALL" || selectedAmc !== "ALL" || selectedPlan !== "ALL" || selectedOptionType !== "ALL" || searchQuery || activeAnalyticalFilterCount > 0) && (
              <button
                type="button"
                onClick={handleResetAllFilters}
                className="font-mono text-xs text-text-cream-muted hover:text-rose-400 transition-colors flex items-center gap-1"
              >
                <span>Reset Filters</span>
                <span className="text-[10px]">✕</span>
              </button>
            )}
            <span className="font-mono text-[11px] text-text-cream-muted">
              Showing <strong className="text-text-cream-bright">{sortedItems.length}</strong> of {screenerData.totalReferenceOptions} funds
            </span>
          </div>
        </div>
      </div>

      {/* Main Screener Table */}
      <div className="overflow-hidden rounded-xl border border-border-gold-subtle/40 bg-surface-midnight shadow-md">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="border-b border-border-gold-subtle/30 bg-surface-navy-raised/80 font-mono text-[11px] text-text-cream-muted">
                <th className="p-3 w-10 text-center">
                  <span className="sr-only">Select for Comparison</span>
                </th>
                <th
                  onClick={() => handleSort("name")}
                  className="p-3 cursor-pointer hover:text-text-cream-bright min-w-[220px]"
                >
                  <div className="flex items-center gap-1">
                    <span>Fund &amp; Share Class</span>
                    <span className="text-cyan-400">{getSortIndicator("name")}</span>
                  </div>
                </th>
                <th
                  onClick={() => handleSort("category")}
                  className="p-3 cursor-pointer hover:text-text-cream-bright min-w-[120px]"
                >
                  <div className="flex items-center gap-1">
                    <span>Category</span>
                    <span className="text-cyan-400">{getSortIndicator("category")}</span>
                  </div>
                </th>
                <th
                  onClick={() => handleSort("dates")}
                  className="p-3 cursor-pointer hover:text-text-cream-bright text-right min-w-[110px]"
                >
                  <div className="flex items-center justify-end gap-1">
                    <span>{effectiveHorizon} History</span>
                    <span className="text-cyan-400">{getSortIndicator("dates")}</span>
                  </div>
                </th>
                <th className="p-3 min-w-[140px]">
                  <span>Candidate Benchmark</span>
                </th>
                <th
                  onClick={() => handleSort("RET-02")}
                  className="p-3 cursor-pointer hover:text-text-cream-bright text-right min-w-[100px]"
                >
                  <div className="flex items-center justify-end gap-1">
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        setActiveMetricModal(screenerData.metricDefinitions.find((d) => d.metricCode === "RET-02") ?? null);
                      }}
                      className="text-text-cream-muted/70 hover:text-cyan-400"
                      title="Click for methodology & formula"
                    >
                      ⓘ
                    </button>
                    <span>Period Return</span>
                    <span className="text-cyan-400">{getSortIndicator("RET-02")}</span>
                  </div>
                </th>
                <th
                  onClick={() => handleSort("RET-03")}
                  className="p-3 cursor-pointer hover:text-text-cream-bright text-right min-w-[100px]"
                >
                  <div className="flex items-center justify-end gap-1">
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        setActiveMetricModal(screenerData.metricDefinitions.find((d) => d.metricCode === "RET-03") ?? null);
                      }}
                      className="text-text-cream-muted/70 hover:text-cyan-400"
                      title="Click for methodology & formula"
                    >
                      ⓘ
                    </button>
                    <span>CAGR ({effectiveHorizon})</span>
                    <span className="text-cyan-400">{getSortIndicator("RET-03")}</span>
                  </div>
                </th>
                <th
                  onClick={() => handleSort("RSK-01")}
                  className="p-3 cursor-pointer hover:text-text-cream-bright text-right min-w-[100px]"
                >
                  <div className="flex items-center justify-end gap-1">
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        setActiveMetricModal(screenerData.metricDefinitions.find((d) => d.metricCode === "RSK-01") ?? null);
                      }}
                      className="text-text-cream-muted/70 hover:text-cyan-400"
                      title="Click for methodology & formula"
                    >
                      ⓘ
                    </button>
                    <span>Volatility</span>
                    <span className="text-cyan-400">{getSortIndicator("RSK-01")}</span>
                  </div>
                </th>
                <th
                  onClick={() => handleSort("RSK-03")}
                  className="p-3 cursor-pointer hover:text-text-cream-bright text-right min-w-[100px]"
                >
                  <div className="flex items-center justify-end gap-1">
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        setActiveMetricModal(screenerData.metricDefinitions.find((d) => d.metricCode === "RSK-03") ?? null);
                      }}
                      className="text-text-cream-muted/70 hover:text-cyan-400"
                      title="Click for methodology & formula"
                    >
                      ⓘ
                    </button>
                    <span>Max DD</span>
                    <span className="text-cyan-400">{getSortIndicator("RSK-03")}</span>
                  </div>
                </th>
                <th
                  onClick={() => handleSort("MKT-01")}
                  className="p-3 cursor-pointer hover:text-text-cream-bright text-right min-w-[90px]"
                >
                  <div className="flex items-center justify-end gap-1">
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        setActiveMetricModal(screenerData.metricDefinitions.find((d) => d.metricCode === "MKT-01") ?? null);
                      }}
                      className="text-text-cream-muted/70 hover:text-cyan-400"
                      title="Click for methodology & formula"
                    >
                      ⓘ
                    </button>
                    <span>Beta</span>
                    <span className="text-cyan-400">{getSortIndicator("MKT-01")}</span>
                  </div>
                </th>
                <th
                  onClick={() => handleSort("MKT-02")}
                  className="p-3 cursor-pointer hover:text-text-cream-bright text-right min-w-[90px]"
                >
                  <div className="flex items-center justify-end gap-1">
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        setActiveMetricModal(screenerData.metricDefinitions.find((d) => d.metricCode === "MKT-02") ?? null);
                      }}
                      className="text-text-cream-muted/70 hover:text-cyan-400"
                      title="Click for methodology & formula"
                    >
                      ⓘ
                    </button>
                    <span>Down Beta</span>
                    <span className="text-cyan-400">{getSortIndicator("MKT-02")}</span>
                  </div>
                </th>
                <th
                  onClick={() => handleSort("RAT-04")}
                  className="p-3 cursor-pointer hover:text-text-cream-bright text-right min-w-[90px]"
                >
                  <div className="flex items-center justify-end gap-1">
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        setActiveMetricModal(screenerData.metricDefinitions.find((d) => d.metricCode === "RAT-04") ?? null);
                      }}
                      className="text-text-cream-muted/70 hover:text-cyan-400"
                      title="Click for methodology & formula"
                    >
                      ⓘ
                    </button>
                    <span>Info Ratio</span>
                    <span className="text-cyan-400">{getSortIndicator("RAT-04")}</span>
                  </div>
                </th>
                <th
                  onClick={() => handleSort("REL-01")}
                  className="p-3 cursor-pointer hover:text-text-cream-bright text-right min-w-[90px]"
                >
                  <div className="flex items-center justify-end gap-1">
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        setActiveMetricModal(screenerData.metricDefinitions.find((d) => d.metricCode === "REL-01") ?? null);
                      }}
                      className="text-text-cream-muted/70 hover:text-cyan-400"
                      title="Click for methodology & formula"
                    >
                      ⓘ
                    </button>
                    <span>Track Err</span>
                    <span className="text-cyan-400">{getSortIndicator("REL-01")}</span>
                  </div>
                </th>
                <th className="p-3 text-center min-w-[80px]">
                  <span>Action</span>
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border-gold-subtle/10 font-sans">
              {sortedItems.length === 0 ? (
                <tr>
                  <td colSpan={14} className="p-10 text-center space-y-3">
                    <div className="w-12 h-12 rounded-full bg-surface-navy-raised flex items-center justify-center mx-auto text-text-cream-muted">
                      🔍
                    </div>
                    <h4 className="font-semibold text-text-cream-bright">No Funds Match Current Filter Criteria</h4>
                    <p className="text-xs text-text-cream-muted max-w-md mx-auto">
                      {activeAnalyticalFilterCount > 0
                        ? `${excludedByAnalyticalCount} funds were excluded by analytical metric thresholds because their metrics were outside the criteria or unavailable for the ${effectiveHorizon} window.`
                        : "No funds in the reference universe match the specified category, AMC, plan, or search query."}
                    </p>
                    <button
                      type="button"
                      onClick={handleResetAllFilters}
                      className="mt-2 inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-surface-navy-raised hover:bg-surface-navy-hover text-xs font-semibold text-primary border border-border-gold-subtle transition-all"
                    >
                      Reset All Filters
                    </button>
                  </td>
                </tr>
              ) : (
                sortedItems.map((item) => {
                  const isSelected = selectedOptionIds.includes(item.schemeOptionId);
                  const isEligible = item.isEligibleForHorizon ?? item.isEligibleFor3Y;
                  const isRowExpanded = expandedRowId === item.schemeOptionId;
                  const itemPlan = getItemPlanType(item);
                  const itemOption = getItemOptionType(item);
                  const sId = item.schemeId || item.schemeOptionId;
                  const isSaved = isWatchlisted(sId, item.schemeOptionId);

                  return (
                    <React.Fragment key={item.schemeOptionId}>
                      <tr
                        className={`hover:bg-surface-navy-raised/40 transition-colors ${
                          isSelected ? "bg-cyan-500/5" : ""
                        }`}
                      >
                        {/* Checkbox */}
                        <td className="p-3 text-center">
                          <input
                            type="checkbox"
                            checked={isSelected}
                            onChange={() => onToggleSelect(item.schemeOptionId)}
                            className="h-3.5 w-3.5 rounded border-border-gold-subtle/50 text-cyan-500 focus:ring-0 focus:ring-offset-0 bg-surface-midnight cursor-pointer"
                            aria-label={`Select ${item.schemeName} for comparison`}
                          />
                        </td>

                        {/* Fund Name & Exact Share Class Identity */}
                        <td className="p-3">
                          <div className="flex flex-col">
                            <div className="flex items-center gap-1.5">
                              <Link
                                href={`/funds/${item.schemeId || item.schemeOptionId}?option=${item.schemeOptionId}${item.latestCalculationRunId ? `&run=${item.latestCalculationRunId}` : ""}`}
                                className="font-medium text-text-cream-bright hover:text-cyan-400 transition-colors line-clamp-1"
                                title={`View analytical fund profile for ${item.schemeName}`}
                              >
                                {item.schemeName}
                              </Link>
                              <button
                                type="button"
                                onClick={() => setExpandedRowId(isRowExpanded ? null : item.schemeOptionId)}
                                className="text-text-cream-muted/50 hover:text-cyan-300 text-[10px] px-1 rounded hover:bg-surface-navy-raised"
                                title="Expand point-in-time evidence and share class details"
                              >
                                {isRowExpanded ? "▲" : "▼"}
                              </button>
                            </div>

                            <div className="flex flex-wrap items-center gap-1.5 text-[10px] text-text-cream-muted mt-0.5">
                              <span className="font-semibold text-text-cream-muted/90">{item.amcName}</span>
                              <span className="text-text-cream-muted/40">•</span>
                              <span className="font-mono text-[9px] px-1 rounded bg-surface-navy-raised border border-border-gold-subtle/30 text-text-cream-bright">
                                {itemPlan}
                              </span>
                              <span className="font-mono text-[9px] px-1 rounded bg-surface-navy-raised border border-border-gold-subtle/30 text-text-cream-bright">
                                {itemOption}
                              </span>
                              {item.amfiCode && (
                                <span className="font-mono text-text-cream-muted/60">
                                  AMFI: {item.amfiCode}
                                </span>
                              )}

                              {/* Hydration / Calculation Status Badge */}
                              {item.calculationStatus === "CALCULATED" ? (
                                item.latestCalculationRunId ? (
                                  <Link
                                    href={`/analysis/${item.latestCalculationRunId}`}
                                    className="font-mono text-[9px] text-emerald-400 bg-emerald-500/10 px-1.5 py-0.2 rounded border border-emerald-500/30 hover:border-emerald-400 hover:underline"
                                    title={`Verified canonical metrics persisted (Run #${item.latestCalculationRunId}). Click to inspect calculation lineage.`}
                                  >
                                    HYDRATED
                                  </Link>
                                ) : (
                                  <span
                                    className="font-mono text-[9px] text-emerald-400 bg-emerald-500/10 px-1.5 py-0.2 rounded border border-emerald-500/30"
                                    title="Verified canonical metrics persisted"
                                  >
                                    HYDRATED
                                  </span>
                                )
                              ) : item.calculationStatus === "PARTIALLY_CALCULATED" ? (
                                <span
                                  className="font-mono text-[9px] text-cyan-300 bg-cyan-500/10 px-1.5 py-0.2 rounded border border-cyan-500/30"
                                  title={`Partial calculation (${item.validMetricCount ?? "?"}/8 metrics calculated)`}
                                >
                                  PARTIAL ({item.validMetricCount ?? "?"}/8)
                                </span>
                              ) : isEligible ? (
                                <span
                                  className="font-mono text-[9px] text-cyan-400/80 bg-cyan-500/10 px-1.5 py-0.2 rounded border border-cyan-500/20"
                                  title={`Eligible for ${effectiveHorizon} batch calculation; run pending`}
                                >
                                  UNHYDRATED
                                </span>
                              ) : (
                                <span
                                  className="font-mono text-[9px] text-amber-400/70 bg-amber-500/10 px-1.5 py-0.2 rounded border border-amber-500/20"
                                  title={item.exclusionReason ?? `Insufficient trading history for ${effectiveHorizon} window`}
                                >
                                  INSUFFICIENT {effectiveHorizon}
                                </span>
                              )}
                            </div>
                          </div>
                        </td>

                        {/* Category */}
                        <td className="p-3">
                          <span className="inline-block rounded bg-surface-navy-raised/80 px-2 py-0.5 font-mono text-[10px] text-text-cream-bright border border-border-gold-subtle/30">
                            {item.categoryDisplayName}
                          </span>
                        </td>

                        {/* Distinct NAV Dates & History Status */}
                        <td className="p-3 text-right font-mono">
                          <div className="flex flex-col items-end">
                            <span
                              className={`font-semibold ${isEligible ? "text-emerald-400" : "text-amber-400"}`}
                              title={`${effectiveHorizon} Evaluation Window: ${item.distinctNavDates} trading days (Threshold ≥ ${horizonThreshold}d required).`}
                            >
                              {item.distinctNavDates} d
                            </span>
                            <span
                              className={`text-[9px] uppercase tracking-wide ${
                                isEligible ? "text-emerald-500/80" : "text-amber-500/80 cursor-help"
                              }`}
                              title={item.exclusionReason ?? `Sufficient historical trading days for ${effectiveHorizon} statistical metrics`}
                            >
                              {isEligible ? `${effectiveHorizon} ELIGIBLE` : `${effectiveHorizon} PARTIAL`}
                            </span>
                            {item.allTimeNavDates != null && item.allTimeNavDates !== item.distinctNavDates && (
                              <span
                                className="text-[8px] text-text-cream-muted/70 cursor-help"
                                title={`Total all-time historical observations: ${item.allTimeNavDates} days from ${item.earliestNavDate ?? "N/A"} to ${item.latestNavDate ?? "N/A"}`}
                              >
                                all-time: {item.allTimeNavDates}d
                              </span>
                            )}
                          </div>
                        </td>

                        {/* Candidate Benchmark */}
                        <td className="p-3">
                          <div className="flex flex-col">
                            <span className="font-mono text-[10px] text-text-cream-bright">
                              {item.candidateBenchmarkCode}
                            </span>
                            <span
                              className="font-mono text-[8px] text-amber-400/90 tracking-wide uppercase"
                              title="Candidate methodology assignment under Phase 2H; not formally approved by review committee."
                            >
                              CANDIDATE MAPPING
                            </span>
                          </div>
                        </td>

                        {/* RET-02 (Simple Period Return) */}
                        <td className="p-3 text-right font-mono">
                          {renderMetricCell(item, "RET-02", "text-emerald-400")}
                        </td>

                        {/* RET-03 (CAGR) */}
                        <td className="p-3 text-right font-mono">
                          {renderMetricCell(item, "RET-03", "text-text-cream-bright")}
                        </td>

                        {/* RSK-01 (Volatility) */}
                        <td className="p-3 text-right font-mono">
                          {renderMetricCell(item, "RSK-01", "text-text-cream-bright")}
                        </td>

                        {/* RSK-03 (Max Drawdown) */}
                        <td className="p-3 text-right font-mono">
                          {renderMetricCell(item, "RSK-03", "text-rose-400")}
                        </td>

                        {/* MKT-01 (Beta) */}
                        <td className="p-3 text-right font-mono">
                          {renderMetricCell(item, "MKT-01", "text-cyan-400")}
                        </td>

                        {/* MKT-02 (Downside Beta) */}
                        <td className="p-3 text-right font-mono">
                          {renderMetricCell(item, "MKT-02", "text-cyan-400")}
                        </td>

                        {/* RAT-04 (Info Ratio) */}
                        <td className="p-3 text-right font-mono">
                          {renderMetricCell(item, "RAT-04", "text-primary")}
                        </td>

                        {/* REL-01 (Tracking Error) */}
                        <td className="p-3 text-right font-mono">
                          {renderMetricCell(item, "REL-01", "text-text-cream-bright")}
                        </td>

                        {/* Action Link & Workspace Bookmark */}
                        <td className="p-3 text-center">
                          <div className="flex items-center justify-center gap-1.5">
                            <button
                              type="button"
                              onClick={() =>
                                toggleWatchlist(sId, item.schemeOptionId, {
                                  schemeName: item.schemeName,
                                  category: item.category,
                                  amcName: item.amcName,
                                  optionType: itemOption,
                                  planType: itemPlan,
                                  amfiCode: item.amfiCode,
                                  isin: item.isin,
                                })
                              }
                              className={`p-1 rounded transition-colors ${
                                isSaved
                                  ? "text-accent bg-accent/15 hover:bg-accent/25"
                                  : "text-text-cream-muted/40 hover:text-text-cream-bright hover:bg-surface-navy-raised"
                              }`}
                              title={isSaved ? "Saved in Research Workspace (Click to remove)" : "Save to Research Workspace"}
                              aria-label={`${isSaved ? "Remove" : "Save"} ${item.schemeName} in workspace`}
                            >
                              <svg
                                className="w-3.5 h-3.5"
                                fill={isSaved ? "currentColor" : "none"}
                                viewBox="0 0 24 24"
                                stroke="currentColor"
                              >
                                <path
                                  strokeLinecap="round"
                                  strokeLinejoin="round"
                                  strokeWidth={isSaved ? 1 : 2}
                                  d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z"
                                />
                              </svg>
                            </button>
                            <Link
                              href={`/funds/${sId}?option=${item.schemeOptionId}`}
                              className="inline-block rounded border border-border-gold-subtle/50 bg-surface-navy-raised/50 px-2 py-1 text-[10px] font-semibold text-text-cream-bright hover:border-cyan-400 hover:text-cyan-400 transition-colors whitespace-nowrap"
                              title={`Inspect profile for ${item.schemeName}`}
                            >
                              Profile →
                            </Link>
                          </div>
                        </td>
                      </tr>

                      {/* Expandable Evidence Row Drawer */}
                      {isRowExpanded && (
                        <tr className="bg-surface-navy-raised/30 border-b border-border-gold-subtle/20">
                          <td colSpan={14} className="p-4">
                            <div className="grid grid-cols-1 md:grid-cols-4 gap-4 text-xs">
                              {/* Scheme Option Identity Verification */}
                              <div className="panel p-3 bg-surface-midnight/90 space-y-1.5">
                                <span className="font-mono text-[10px] uppercase text-cyan-300 font-bold block">
                                  01. Share Class Registration
                                </span>
                                <div className="space-y-1 font-mono text-[11px] text-text-cream-muted">
                                  <div><span className="text-text-cream-muted/60">Option ID:</span> #{item.schemeOptionId}</div>
                                  <div><span className="text-text-cream-muted/60">Plan:</span> {itemPlan}</div>
                                  <div><span className="text-text-cream-muted/60">Option:</span> {itemOption}</div>
                                  <div><span className="text-text-cream-muted/60">AMFI Code:</span> {item.amfiCode || "N/A"}</div>
                                  <div><span className="text-text-cream-muted/60">ISIN:</span> {item.isin || "N/A"}</div>
                                </div>
                              </div>

                              {/* Point-in-Time Historical Coverage */}
                              <div className="panel p-3 bg-surface-midnight/90 space-y-1.5">
                                <span className="font-mono text-[10px] uppercase text-cyan-300 font-bold block">
                                  02. Historical Evidence ({effectiveHorizon})
                                </span>
                                <div className="space-y-1 font-mono text-[11px] text-text-cream-muted">
                                  <div>
                                    <span className="text-text-cream-muted/60">Window Trading Days:</span>{" "}
                                    <strong className={isEligible ? "text-emerald-400" : "text-amber-400"}>
                                      {item.distinctNavDates} / {horizonThreshold}d
                                    </strong>
                                  </div>
                                  <div>
                                    <span className="text-text-cream-muted/60">All-Time Coverage:</span>{" "}
                                    {item.allTimeNavDates ?? "N/A"} days
                                  </div>
                                  <div>
                                    <span className="text-text-cream-muted/60">Earliest NAV:</span>{" "}
                                    {item.earliestNavDate ?? "N/A"}
                                  </div>
                                  <div>
                                    <span className="text-text-cream-muted/60">Latest NAV:</span>{" "}
                                    {item.latestNavDate ?? "N/A"}
                                  </div>
                                  <div>
                                    <span className="text-text-cream-muted/60">Freshness Status:</span>{" "}
                                    {item.dataFreshnessStatus ?? "CURRENT"}
                                  </div>
                                </div>
                              </div>

                              {/* Candidate Benchmark Mapping & Governance */}
                              <div className="panel p-3 bg-surface-midnight/90 space-y-1.5">
                                <span className="font-mono text-[10px] uppercase text-cyan-300 font-bold block">
                                  03. Benchmark Alignment
                                </span>
                                <div className="space-y-1 font-mono text-[11px] text-text-cream-muted">
                                  <div><span className="text-text-cream-muted/60">Benchmark:</span> {item.candidateBenchmarkCode}</div>
                                  <div><span className="text-text-cream-muted/60">TRI Name:</span> {item.candidateBenchmarkName}</div>
                                  <div><span className="text-text-cream-muted/60">Governance Status:</span> <span className="text-amber-300">CANDIDATE_MAPPING</span></div>
                                  <div><span className="text-text-cream-muted/60">TRI Coverage:</span> {item.benchmarkCoverageStatus} ({item.benchmarkObservationCount} obs)</div>
                                </div>
                              </div>

                              {/* Fast Actions & Profiling */}
                              <div className="panel p-3 bg-surface-midnight/90 flex flex-col justify-between space-y-2">
                                <div>
                                  <span className="font-mono text-[10px] uppercase text-cyan-300 font-bold block">
                                    04. Verification &amp; Workspace
                                  </span>
                                  {item.exclusionReason && (
                                    <p className="mt-1 text-[11px] text-amber-300/90 font-mono">
                                      ⚠️ {item.exclusionReason}
                                    </p>
                                  )}
                                  <div className="mt-1.5 flex items-center justify-between text-[10.5px] font-mono">
                                    <span className="text-text-cream-muted/60">Workspace:</span>
                                    {isSaved ? (
                                      <span className="text-emerald-400 font-semibold flex items-center gap-1">
                                        <span>✓ Saved</span>
                                        <Link href="/watchlist" className="text-cyan-400 hover:underline text-[9.5px]">
                                          (View)
                                        </Link>
                                      </span>
                                    ) : (
                                      <span className="text-text-cream-muted/50">Not Saved</span>
                                    )}
                                  </div>
                                </div>
                                <div className="space-y-1.5 pt-2">
                                  <button
                                    type="button"
                                    onClick={() => {
                                      const watchItem = watchlistItems.find(
                                        (w) => w.schemeOptionId === item.schemeOptionId
                                      ) || {
                                        id: -item.schemeOptionId,
                                        schemeId: sId,
                                        schemeOptionId: item.schemeOptionId,
                                        schemeName: item.schemeName,
                                        schemeCode: `SCHEME_${sId}`,
                                        category: item.category,
                                        amcName: item.amcName,
                                        optionType: itemOption,
                                        planType: itemPlan,
                                        amfiCode: item.amfiCode,
                                        isin: item.isin,
                                        yukiraScoreStatus: "CANDIDATE",
                                      };
                                      setEditingFund(watchItem as WatchlistResponse);
                                    }}
                                    className="w-full text-center block px-3 py-1 rounded-lg bg-surface-navy-raised text-accent hover:underline font-mono text-[10.5px] border border-border-gold-subtle/40"
                                  >
                                    {isSaved ? "Edit Research Note" : "+ Record Research Question"}
                                  </button>
                                  <Link
                                    href={`/funds/${sId}?option=${item.schemeOptionId}`}
                                    className="w-full text-center block px-3 py-1.5 rounded-lg bg-primary text-surface-midnight font-semibold text-xs hover:bg-primary/90 transition-all shadow-sm"
                                  >
                                    Open Analytical Profile →
                                  </Link>
                                  {item.latestCalculationRunId && (
                                    <Link
                                      href={`/analysis/${item.latestCalculationRunId}`}
                                      className="w-full text-center block px-3 py-1 rounded-lg bg-surface-navy-raised text-text-cream-muted hover:text-text-cream-bright font-mono text-[10.5px] border border-border-gold-subtle/30"
                                    >
                                      Inspect Calculation Lineage (Run #{item.latestCalculationRunId})
                                    </Link>
                                  )}
                                </div>
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

      {/* Multi-Fund Comparison Floating Dock (Section 5) */}
      {selectedOptionIds.length > 0 && (
        <div className="sticky bottom-4 z-40 rounded-2xl border-2 border-primary bg-surface-midnight/95 p-4 shadow-2xl backdrop-blur-md glow-gold">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
            <div className="flex items-center gap-3">
              <span className="w-8 h-8 rounded-full bg-primary text-surface-midnight font-mono font-bold text-sm flex items-center justify-center shrink-0">
                {selectedOptionIds.length}
              </span>
              <div>
                <h4 className="font-sans text-sm font-bold text-text-cream-bright">
                  Selected for Direct Comparison ({selectedOptionIds.length} of 4 funds)
                </h4>
                <p className="font-mono text-[11px] text-text-cream-muted">
                  Common {effectiveHorizon} evaluation window · Aligned reference date {screenerData.asOfDate}
                </p>
              </div>
            </div>

            {/* Selected Fund Chips */}
            <div className="hidden lg:flex items-center gap-2 overflow-x-auto max-w-xl">
              {selectedOptionIds.map((optId) => {
                const optItem = screenerData.items.find((i) => i.schemeOptionId === optId);
                return (
                  <span
                    key={optId}
                    className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-surface-navy-raised border border-border-gold-subtle text-[11px] text-text-cream-bright whitespace-nowrap"
                  >
                    <span className="line-clamp-1 max-w-[120px]">{optItem?.schemeName || `Fund #${optId}`}</span>
                    <button
                      type="button"
                      onClick={() => onToggleSelect(optId)}
                      className="text-text-cream-muted hover:text-rose-400 text-xs font-bold"
                      title="Deselect fund"
                    >
                      ×
                    </button>
                  </span>
                );
              })}
            </div>

            {/* Actions */}
            <div className="flex flex-wrap items-center gap-2 shrink-0">
              {batchSaveMessage && (
                <span className="text-[11px] font-mono text-accent font-semibold px-2 py-1 rounded bg-accent/15 border border-accent/30 animate-pulse">
                  {batchSaveMessage}
                </span>
              )}
              <button
                type="button"
                onClick={handleBatchSaveToWorkspace}
                className="px-3 py-2 rounded-xl border border-accent/40 bg-accent/15 text-accent hover:bg-accent/25 text-xs font-mono font-semibold transition-all flex items-center gap-1.5 shadow-sm"
                title="Save selected funds and share classes to your Investor Research Workspace without resetting filters"
              >
                <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z" />
                </svg>
                <span>Save to Workspace</span>
              </button>
              <button
                type="button"
                onClick={() => selectedOptionIds.forEach((id) => onToggleSelect(id))}
                className="px-3 py-2 rounded-lg text-xs font-mono text-text-cream-muted hover:text-rose-400 transition-colors"
              >
                Clear Selection
              </button>
              {onLaunchDetailedComparison && (
                <button
                  type="button"
                  onClick={() => onLaunchDetailedComparison(selectedOptionIds, effectiveHorizon)}
                  disabled={selectedOptionIds.length < 2}
                  className={`px-4 py-2 rounded-xl font-sans text-xs font-bold transition-all shadow-md ${
                    selectedOptionIds.length >= 2
                      ? "bg-primary text-surface-midnight hover:bg-primary/90"
                      : "bg-surface-navy-raised text-text-cream-muted cursor-not-allowed opacity-60"
                  }`}
                >
                  {selectedOptionIds.length >= 2
                    ? `Compare ${selectedOptionIds.length} Funds (${effectiveHorizon}) →`
                    : "Select At Least 2 Funds"}
                </button>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Governance & Epistemic Disclaimers Box */}
      <div className="rounded-xl border border-amber-500/20 bg-amber-500/5 p-4 text-xs">
        <div className="flex items-center gap-2 text-amber-300 font-semibold font-mono text-[11px] uppercase tracking-wider">
          <span>⚠️ Governance &amp; Methodological Disclaimers (Phase 2H)</span>
        </div>
        <ul className="mt-2 space-y-1 text-text-cream-muted font-sans list-disc list-inside">
          {screenerData.governanceDisclaimers.map((disc, idx) => (
            <li key={idx}>{disc}</li>
          ))}
        </ul>
      </div>

      {/* Metric Definition Modal / Drawer */}
      {activeMetricModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-surface-midnight/80 backdrop-blur-sm p-4">
          <div className="relative w-full max-w-lg rounded-xl border border-border-gold-subtle bg-surface-midnight p-6 shadow-2xl">
            <button
              type="button"
              onClick={() => setActiveMetricModal(null)}
              className="absolute right-4 top-4 text-text-cream-muted hover:text-text-cream-bright text-lg font-mono"
            >
              ✕
            </button>
            <div className="flex items-center gap-2">
              <span className="font-mono text-xs font-bold text-cyan-400 px-2 py-0.5 rounded bg-cyan-400/10 border border-cyan-400/20">
                {activeMetricModal.metricCode}
              </span>
              <span
                className={`font-mono text-[10px] px-2 py-0.5 rounded ${
                  activeMetricModal.epistemicStatus === "OPERATIONAL_BASELINE" || activeMetricModal.epistemicStatus === "APPROVED_M2N"
                    ? "bg-emerald-500/10 text-emerald-300 border border-emerald-500/30"
                    : "bg-amber-500/10 text-amber-300 border border-amber-500/30"
                }`}
              >
                {activeMetricModal.epistemicStatus}
              </span>
            </div>
            <h3 className="mt-2 text-lg font-bold text-text-cream-bright">
              {activeMetricModal.metricName}
            </h3>

            <div className="mt-4 space-y-3 text-xs text-text-cream-muted">
              <div>
                <span className="font-semibold text-text-cream-bright">Formula &amp; Methodology:</span>
                <p className="mt-1 font-mono text-[11px] bg-surface-navy-raised/80 p-2.5 rounded border border-border-gold-subtle/30 text-text-cream-bright">
                  {activeMetricModal.formulaDescription}
                </p>
              </div>

              <div className="grid grid-cols-2 gap-3 pt-2">
                <div>
                  <span className="font-semibold text-text-cream-bright">Category:</span>
                  <div className="font-mono text-[11px] text-text-cream-muted mt-0.5">{activeMetricModal.category}</div>
                </div>
                <div>
                  <span className="font-semibold text-text-cream-bright">Units:</span>
                  <div className="font-mono text-[11px] text-text-cream-muted mt-0.5">{activeMetricModal.units}</div>
                </div>
                <div>
                  <span className="font-semibold text-text-cream-bright">Evaluation Window:</span>
                  <div className="font-mono text-[11px] text-text-cream-muted mt-0.5">{activeMetricModal.evaluationWindow}</div>
                </div>
                <div>
                  <span className="font-semibold text-text-cream-bright">Benchmark Dependent:</span>
                  <div className="font-mono text-[11px] text-text-cream-muted mt-0.5">
                    {activeMetricModal.benchmarkDependent ? "Yes (Category TRI)" : "No (Absolute Return)"}
                  </div>
                </div>
              </div>

              {activeMetricModal.rankingDirection && (
                <div className="pt-2 border-t border-border-gold-subtle/20 space-y-1.5">
                  <div className="flex items-center gap-2">
                    <span className="font-semibold text-text-cream-bright">Ranking Direction:</span>
                    <span className="font-mono text-[10px] px-2 py-0.5 rounded font-bold bg-cyan-500/10 text-cyan-300 border border-cyan-500/30">
                      {activeMetricModal.rankingDirection}
                    </span>
                  </div>
                  {activeMetricModal.percentileInterpretation && (
                    <p className="text-[11px] text-text-cream-muted">
                      {activeMetricModal.percentileInterpretation}
                    </p>
                  )}
                </div>
              )}

              <div className="pt-2 border-t border-border-gold-subtle/20 space-y-1">
                <span className="font-semibold text-text-cream-bright">Category Percentile Engine (Phase 2H / DEC-V1-02):</span>
                <div className="font-mono text-[10px] bg-surface-navy-raised/80 p-2.5 rounded border border-border-gold-subtle/30 text-text-cream-muted space-y-1">
                  <div className="text-text-cream-bright font-semibold">Empirical Midpoint ECDF Formula:</div>
                  <div className="text-cyan-300">PR = [ (count(y &lt; x) + 0.5 × count(y == x)) / N ] × 100</div>
                  <div className="text-[9px] text-text-cream-muted/80 leading-relaxed pt-1">
                    • Same-Category Isolation: Peer ranking is strictly confined to identical category options.<br />
                    • Denominator N: Evaluated over valid calculated peers (missing/uncalculated excluded).<br />
                    • Reliability Thresholds: Minimum N ≥ 3 required; N &lt; 15 flagged as small cohort.
                  </div>
                </div>
              </div>
            </div>

            <div className="mt-6 flex justify-end">
              <button
                type="button"
                onClick={() => setActiveMetricModal(null)}
                className="rounded-lg bg-surface-navy-raised px-4 py-1.5 text-xs font-semibold text-text-cream-bright hover:bg-surface-navy-raised/80 border border-border-gold-subtle/50"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Research Note Modal for due diligence hypotheses */}
      <ResearchNoteModal
        isOpen={Boolean(editingFund)}
        onClose={() => setEditingFund(null)}
        fund={editingFund}
        onSave={saveResearchNote}
      />
    </div>
  );
}
