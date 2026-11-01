"use client";

import React, { useState, useMemo, useEffect } from "react";
import {
  ResearchFollowUpIssue,
  FollowUpStatus,
  FollowUpCategory,
  FollowUpOutcomeType,
  FollowUpWorkspaceSummary,
  FOLLOW_UP_CATEGORIES_CONFIG,
  FOLLOW_UP_STATUS_CONFIG,
  FOLLOW_UP_OUTCOMES_CONFIG,
  computeFollowUpSummary,
  isTargetDatePassed,
  evaluateFindingEvidenceTraceability,
  evaluateInvestigationClosureState,
  evaluateIssueDependencies,
} from "@/lib/utils/researchFollowUp";
import { WatchlistResponse } from "@/lib/api/investor";
import {
  formatSessionRelativeTime,
  getBriefSectionTitle,
  InvestigationHandoffContext,
} from "@/lib/utils/researchSession";
import {
  evaluateFollowUpIssueSearch,
  FollowUpSearchResult,
} from "@/lib/utils/researchSearch";
import { ResearchDossierModal } from "@/components/watchlist/ResearchDossierModal";
import {
  buildInvestigationActionQueue,
  QueuedInvestigationItem,
  InvestigationActionQueueSummary,
  QueueInclusionReason,
} from "@/lib/utils/investigationActionQueue";

export interface ResearchFollowUpWorkspaceProps {
  issues: ResearchFollowUpIssue[];
  savedItems?: WatchlistResponse[];
  externalSearchQuery?: string;
  onOpenIssueModal: (
    issue: ResearchFollowUpIssue,
    handoff?: InvestigationHandoffContext | null,
    modalOpts?: { initialReassessmentMode?: boolean; initialShowTimeline?: boolean; initialDependencyId?: string | null }
  ) => void;
  onNavigateToBrief: (
    schemeId: number,
    schemeOptionId?: number | null,
    section?: "coverage" | "checklist" | "A" | "B" | "C" | "D" | "E" | "F" | "G" | "H",
    issue?: ResearchFollowUpIssue
  ) => void;
}

type FollowUpFilter =
  | "ALL"
  | "UNRESOLVED"
  | "OPEN"
  | "IN_PROGRESS"
  | "WAITING"
  | "ADDRESSED_BLOCKERS"
  | "OVERDUE"
  | "EVIDENCE_DRIFT"
  | "REOPENED"
  | "RESOLVED"
  | "DISMISSED";

type BriefSectionFilter = "ALL" | "A" | "B" | "C" | "D" | "E" | "F" | "G";

type AttributeFilter =
  | "ALL"
  | "WITH_RESIDUAL_UNCERTAINTY"
  | "WITH_PRIOR_OUTCOME"
  | "RECORDED_PROVENANCE"
  | "UNRECORDED_PROVENANCE"
  | "EVIDENCE_DRIFT";

export function ResearchFollowUpWorkspace({
  issues,
  savedItems,
  externalSearchQuery,
  onOpenIssueModal,
  onNavigateToBrief,
}: ResearchFollowUpWorkspaceProps) {
  const [searchQuery, setSearchQuery] = useState<string>(externalSearchQuery || "");
  const [viewMode, setViewMode] = useState<"ACTION_QUEUE" | "ALL_ISSUES">("ACTION_QUEUE");
  const [queueSubFilter, setQueueSubFilter] = useState<
    "ALL" | "OVERDUE" | "REOPENED" | "EVIDENCE_DRIFT" | "OPEN_IN_PROGRESS" | "WAITING" | "ADDRESSED_BLOCKERS"
  >("ALL");
  const [activeFilter, setActiveFilter] = useState<FollowUpFilter>("UNRESOLVED");
  const [selectedCategory, setSelectedCategory] = useState<string>("ALL");
  const [selectedFundOption, setSelectedFundOption] = useState<string>("ALL");
  const [selectedSection, setSelectedSection] = useState<BriefSectionFilter>("ALL");
  const [selectedAttribute, setSelectedAttribute] = useState<AttributeFilter>("ALL");
  const [isExpanded, setIsExpanded] = useState<boolean>(true);

  // Phase F21: Dossier assembly & portable export state
  const [selectedIssueIds, setSelectedIssueIds] = useState<string[]>([]);
  const [isDossierModalOpen, setIsDossierModalOpen] = useState<boolean>(false);

  // Synchronize when external search query changes from parent watchlist page
  useEffect(() => {
    if (externalSearchQuery !== undefined) {
      setSearchQuery(externalSearchQuery);
    }
  }, [externalSearchQuery]);

  const summary: FollowUpWorkspaceSummary = useMemo(() => {
    return computeFollowUpSummary(issues);
  }, [issues]);

  const evidenceDriftCount = useMemo(() => {
    return issues.filter(
      (i) =>
        (i.category === "EVIDENCE_LIMITATION" ||
          i.briefSection === "F" ||
          i.title.toLowerCase().includes("drift") ||
          i.title.toLowerCase().includes("reassessment")) &&
        (i.status === "OPEN" || i.status === "IN_PROGRESS" || i.status === "WAITING")
    ).length;
  }, [issues]);

  const residualUncertaintyCount = useMemo(() => {
    return issues.filter((i) => Boolean(i.residualUncertainty && i.residualUncertainty.trim().length > 0)).length;
  }, [issues]);

  const reopenedCount = useMemo(() => {
    return issues.filter((i) => Boolean(i.priorResolutionNote || i.reopenedAt)).length;
  }, [issues]);

  const dismissedCount = useMemo(() => {
    return issues.filter((i) => i.status === "DISMISSED").length;
  }, [issues]);

  const fundOptions = useMemo(() => {
    const map = new Map<
      string,
      {
        schemeName: string;
        planType?: string | null;
        optionType?: string | null;
        schemeOptionId?: number | null;
        schemeId: number;
      }
    >();
    issues.forEach((iss) => {
      const key = `${iss.schemeId}-${iss.schemeOptionId ?? "none"}`;
      if (!map.has(key)) {
        map.set(key, {
          schemeName: iss.schemeName,
          planType: iss.planType,
          optionType: iss.optionType,
          schemeOptionId: iss.schemeOptionId,
          schemeId: iss.schemeId,
        });
      }
    });
    return Array.from(map.entries()).map(([key, val]) => ({ key, ...val }));
  }, [issues]);

  // Phase F20: Search Evaluation across all genuinely persisted fields
  const searchResultsMap = useMemo(() => {
    const map = new Map<string, FollowUpSearchResult>();
    issues.forEach((iss) => {
      map.set(iss.id, evaluateFollowUpIssueSearch(iss, searchQuery));
    });
    return map;
  }, [issues, searchQuery]);

  // Phase F23: Consolidated Investigation Action Queue & Evidence Triage
  const actionQueueResult = useMemo(() => {
    return buildInvestigationActionQueue(issues, savedItems);
  }, [issues, savedItems]);

  const { queuedItems: actionQueue, summary: queueSummary } = actionQueueResult;

  const queueItemMap = useMemo(() => {
    return new Map(actionQueue.map((item) => [item.issue.id, item]));
  }, [actionQueue]);

  const filteredActionQueue = useMemo(() => {
    return actionQueue.filter((item) => {
      // 1. Search Query Filter
      if (searchQuery.trim()) {
        const searchRes = searchResultsMap.get(item.issue.id);
        if (!searchRes?.isMatch) {
          return false;
        }
      }

      // 2. Fund Option Scope
      if (selectedFundOption !== "ALL") {
        const key = `${item.issue.schemeId}-${item.issue.schemeOptionId ?? "none"}`;
        if (key !== selectedFundOption) {
          return false;
        }
      }

      // 3. Queue Sub-Filter
      if (queueSubFilter === "OVERDUE") {
        return item.allReasons.some((r) => r.code === "OVERDUE_TARGET_DATE");
      }
      if (queueSubFilter === "REOPENED") {
        return item.allReasons.some((r) => r.code === "REOPENED_INQUIRY");
      }
      if (queueSubFilter === "EVIDENCE_DRIFT") {
        return item.allReasons.some(
          (r) => r.code === "EVIDENCE_DRIFT" || r.code === "SUPERSEDED_RUN"
        );
      }
      if (queueSubFilter === "OPEN_IN_PROGRESS") {
        return item.allReasons.some((r) => r.code === "OPEN_IN_PROGRESS");
      }
      if (queueSubFilter === "WAITING") {
        return item.allReasons.some((r) => r.code === "WAITING_DEPENDENCY");
      }
      if (queueSubFilter === "ADDRESSED_BLOCKERS") {
        return item.allReasons.some((r) => r.code === "DEPENDENCY_ADDRESSED_REVIEW_NEEDED");
      }

      return true;
    });
  }, [actionQueue, searchQuery, searchResultsMap, selectedFundOption, queueSubFilter]);

  const filteredIssues = useMemo(() => {
    return issues.filter((issue) => {
      // 1. Search Query Filter
      if (searchQuery.trim()) {
        const searchRes = searchResultsMap.get(issue.id);
        if (!searchRes?.isMatch) {
          return false;
        }
      }

      // 2. Fund Option Scope
      if (selectedFundOption !== "ALL") {
        const key = `${issue.schemeId}-${issue.schemeOptionId ?? "none"}`;
        if (key !== selectedFundOption) {
          return false;
        }
      }

      // 3. Category Filter
      if (selectedCategory !== "ALL" && issue.category !== selectedCategory) {
        return false;
      }

      // 4. Brief Section Filter
      if (selectedSection !== "ALL" && issue.briefSection !== selectedSection) {
        return false;
      }

      // 5. Special Attribute Filter
      if (selectedAttribute === "WITH_RESIDUAL_UNCERTAINTY") {
        if (!issue.residualUncertainty || !issue.residualUncertainty.trim()) return false;
      } else if (selectedAttribute === "WITH_PRIOR_OUTCOME") {
        if (!issue.priorResolutionNote && !issue.reopenedAt) return false;
      } else if (selectedAttribute === "RECORDED_PROVENANCE") {
        if (issue.evidenceRunId === null && issue.evidenceRunId === undefined && !issue.evidenceAsOfDate) return false;
      } else if (selectedAttribute === "UNRECORDED_PROVENANCE") {
        if ((issue.evidenceRunId !== null && issue.evidenceRunId !== undefined) || issue.evidenceAsOfDate) return false;
      } else if (selectedAttribute === "EVIDENCE_DRIFT") {
        const prov = evaluateFindingEvidenceTraceability(issue);
        if (prov.status !== "HISTORICAL_EVIDENCE_CHANGED") return false;
      }

      // 6. Status / Lifecycle Filter
      if (activeFilter === "ALL") return true;
      if (activeFilter === "UNRESOLVED") {
        return issue.status === "OPEN" || issue.status === "IN_PROGRESS" || issue.status === "WAITING";
      }
      if (activeFilter === "ADDRESSED_BLOCKERS") {
        const depEval = evaluateIssueDependencies(issue);
        return depEval.state === "ALL_ADDRESSED_REVIEW_NEEDED" || depEval.state === "READY_TO_RESUME";
      }
      if (activeFilter === "OVERDUE") {
        const isUnresolved = issue.status === "OPEN" || issue.status === "IN_PROGRESS" || issue.status === "WAITING";
        return isUnresolved && isTargetDatePassed(issue.targetDate);
      }
      if (activeFilter === "EVIDENCE_DRIFT") {
        const isUnresolved = issue.status === "OPEN" || issue.status === "IN_PROGRESS" || issue.status === "WAITING";
        const isDrift =
          issue.category === "EVIDENCE_LIMITATION" ||
          issue.briefSection === "F" ||
          issue.title.toLowerCase().includes("drift") ||
          issue.title.toLowerCase().includes("reassessment");
        return isUnresolved && isDrift;
      }
      if (activeFilter === "REOPENED") {
        return Boolean(issue.priorResolutionNote || issue.reopenedAt);
      }
      return issue.status === activeFilter;
    });
  }, [
    issues,
    searchQuery,
    searchResultsMap,
    selectedFundOption,
    selectedCategory,
    selectedSection,
    selectedAttribute,
    activeFilter,
  ]);

  const activeVisibleIssues = useMemo(() => {
    if (viewMode === "ACTION_QUEUE") {
      return filteredActionQueue.map((item) => item.issue);
    }
    return filteredIssues;
  }, [viewMode, filteredActionQueue, filteredIssues]);

  const hasActiveFilters =
    Boolean(searchQuery.trim()) ||
    activeFilter !== "UNRESOLVED" ||
    selectedCategory !== "ALL" ||
    selectedFundOption !== "ALL" ||
    selectedSection !== "ALL" ||
    selectedAttribute !== "ALL";

  const handleResetFilters = () => {
    setSearchQuery("");
    setActiveFilter("UNRESOLVED");
    setSelectedCategory("ALL");
    setSelectedFundOption("ALL");
    setSelectedSection("ALL");
    setSelectedAttribute("ALL");
  };

  const handleResetQueueFilters = () => {
    setSearchQuery("");
    setSelectedFundOption("ALL");
    setQueueSubFilter("ALL");
  };

  // Phase F21: Dossier assembly selection handlers
  const handleToggleSelectIssue = (id: string) => {
    setSelectedIssueIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]
    );
  };

  const handleSelectAllVisible = () => {
    const visibleIds = activeVisibleIssues.map((i) => i.id);
    const allSelected = visibleIds.length > 0 && visibleIds.every((id) => selectedIssueIds.includes(id));
    if (allSelected) {
      setSelectedIssueIds((prev) => prev.filter((id) => !visibleIds.includes(id)));
    } else {
      setSelectedIssueIds((prev) => Array.from(new Set([...prev, ...visibleIds])));
    }
  };

  const handleClearSelection = () => {
    setSelectedIssueIds([]);
  };

  const handleRemoveFromDossier = (id: string) => {
    setSelectedIssueIds((prev) => prev.filter((item) => item !== id));
  };

  const allVisibleSelected =
    activeVisibleIssues.length > 0 && activeVisibleIssues.every((i) => selectedIssueIds.includes(i.id));

  const selectedIssuesForDossier = useMemo(() => {
    return issues.filter((iss) => selectedIssueIds.includes(iss.id));
  }, [issues, selectedIssueIds]);

  if (issues.length === 0) {
    return null;
  }

  return (
    <div
      role="region"
      aria-label="Research Review Follow-Up and Unresolved-Issue Tracking"
      className="panel mb-6 p-4 bg-surface border border-border/80 rounded-xl space-y-3 font-mono shadow-xs"
    >
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-2.5 pb-2.5 border-b border-border/60">
        <div className="space-y-0.5">
          <div className="flex flex-wrap items-center gap-2">
            <span className="flex h-5 w-5 items-center justify-center rounded bg-accent/20 text-accent text-xs font-bold shrink-0">
              ⚡
            </span>
            <h3 className="text-xs font-bold uppercase tracking-wider text-text-cream-bright">
              Research Follow-Up &amp; Investigation Action Workspace
            </h3>

            {/* Clickable Action Queue Badge */}
            <button
              type="button"
              onClick={() => {
                setIsExpanded(true);
                setViewMode("ACTION_QUEUE");
                setQueueSubFilter("ALL");
              }}
              className={`status-badge text-[10px] font-mono font-bold transition-all cursor-pointer ${
                queueSummary.totalActionableCount > 0
                  ? "bg-accent/20 border-accent/40 text-accent hover:bg-accent/30"
                  : "bg-surface-raised border-border text-text-muted"
              }`}
              title="Click to view prioritized Investigation Action Queue"
            >
              ⚡ {queueSummary.totalActionableCount} In Action Queue
            </button>

            {/* Unresolved Count */}
            <span
              className={`status-badge text-[10px] font-mono font-bold ${
                summary.unresolvedCount > 0
                  ? "bg-amber-500/15 border-amber-500/30 text-amber-300"
                  : "bg-emerald-500/15 border-emerald-500/30 text-emerald-300"
              }`}
            >
              {summary.unresolvedCount} Active {summary.unresolvedCount === 1 ? "Inquiry" : "Inquiries"}
            </span>

            {/* Overdue Badge */}
            {queueSummary.overdueCount > 0 && (
              <button
                type="button"
                onClick={() => {
                  setIsExpanded(true);
                  setViewMode("ACTION_QUEUE");
                  setQueueSubFilter("OVERDUE");
                }}
                className="status-badge text-[9.5px] font-mono font-bold bg-rose-500/15 border-rose-500/30 text-rose-300 hover:bg-rose-500/25 cursor-pointer"
                title="Click to view overdue investigations in Action Queue"
              >
                ⚠️ {queueSummary.overdueCount} Target Date Passed
              </button>
            )}

            {/* Evidence Drift Badge */}
            {queueSummary.evidenceDriftCount > 0 && (
              <button
                type="button"
                onClick={() => {
                  setIsExpanded(true);
                  setViewMode("ACTION_QUEUE");
                  setQueueSubFilter("EVIDENCE_DRIFT");
                }}
                className="status-badge text-[9.5px] font-mono font-bold bg-amber-500/15 border-amber-500/30 text-amber-300 hover:bg-amber-500/25 cursor-pointer"
                title="Click to view evidence drift & superseded runs in Action Queue"
              >
                ⚠️ {queueSummary.evidenceDriftCount} Evidence Drift
              </button>
            )}

            {/* Reopened Badge */}
            {queueSummary.reopenedCount > 0 && (
              <button
                type="button"
                onClick={() => {
                  setIsExpanded(true);
                  setViewMode("ACTION_QUEUE");
                  setQueueSubFilter("REOPENED");
                }}
                className="status-badge text-[9.5px] font-mono font-bold bg-purple-500/15 border-purple-500/30 text-purple-300 hover:bg-purple-500/25 cursor-pointer"
                title="Click to view reopened inquiries in Action Queue"
              >
                🔄 {queueSummary.reopenedCount} Reopened
              </button>
            )}

            {/* Residual Uncertainty Badge */}
            {residualUncertaintyCount > 0 && (
              <span className="status-badge text-[9.5px] font-mono font-bold bg-yellow-500/15 border-yellow-500/30 text-yellow-300">
                ⚠️ {residualUncertaintyCount} With Uncertainty
              </span>
            )}
          </div>
          <p className="text-[11.5px] text-text-muted font-sans leading-relaxed">
            Prioritize active investigations, triage evidence drift, and track unanswered diligence inquiries across research review through to documented resolution.
          </p>
        </div>

        <button
          type="button"
          onClick={() => setIsExpanded((v) => !v)}
          className="btn btn-ghost btn-xs text-[11px] text-accent hover:underline self-start md:self-center shrink-0 cursor-pointer"
        >
          {isExpanded
            ? "Collapse Workspace ▴"
            : `Expand Workspace (${viewMode === "ACTION_QUEUE" ? queueSummary.totalActionableCount + " queued" : issues.length + " total"}) ▾`}
        </button>
      </div>

      {isExpanded && (
        <div className="space-y-3 pt-1">
          {/* Phase F23: Primary View Mode Switcher (Action Queue vs Full Archive) */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 p-1 bg-surface-raised/80 rounded-lg border border-border/70">
            <button
              type="button"
              onClick={() => {
                setViewMode("ACTION_QUEUE");
                setQueueSubFilter("ALL");
              }}
              className={`p-2.5 rounded-md text-left transition-all cursor-pointer flex items-center justify-between gap-2 ${
                viewMode === "ACTION_QUEUE"
                  ? "bg-surface border border-accent/40 text-text-primary shadow-xs ring-1 ring-accent/20"
                  : "hover:bg-surface/50 text-text-secondary border border-transparent"
              }`}
            >
              <div className="min-w-0">
                <div className="flex items-center gap-1.5 font-bold text-xs text-text-cream-bright">
                  <span className="text-accent">⚡</span>
                  <span>Investigation Action Queue</span>
                  <span
                    className={`px-1.5 py-0.2 rounded text-[10px] font-mono font-bold ${
                      queueSummary.totalActionableCount > 0
                        ? "bg-accent/20 text-accent border border-accent/30"
                        : "bg-surface text-text-muted border border-border"
                    }`}
                  >
                    {queueSummary.totalActionableCount}
                  </span>
                </div>
                <p className="text-[10.5px] text-text-muted font-sans truncate">
                  Prioritized due-diligence triage: overdue dates, reopened outcomes, drift, open actions
                </p>
              </div>
              <span className="text-accent text-xs shrink-0 font-bold">
                {viewMode === "ACTION_QUEUE" ? "● Active" : "→"}
              </span>
            </button>

            <button
              type="button"
              onClick={() => setViewMode("ALL_ISSUES")}
              className={`p-2.5 rounded-md text-left transition-all cursor-pointer flex items-center justify-between gap-2 ${
                viewMode === "ALL_ISSUES"
                  ? "bg-surface border border-accent/40 text-text-primary shadow-xs ring-1 ring-accent/20"
                  : "hover:bg-surface/50 text-text-secondary border border-transparent"
              }`}
            >
              <div className="min-w-0">
                <div className="flex items-center gap-1.5 font-bold text-xs text-text-cream-bright">
                  <span className="text-text-muted">📋</span>
                  <span>Complete Issue Archive</span>
                  <span className="px-1.5 py-0.2 rounded text-[10px] font-mono font-bold bg-surface text-text-muted border border-border">
                    {issues.length}
                  </span>
                </div>
                <p className="text-[10.5px] text-text-muted font-sans truncate">
                  Browse all active, waiting, resolved, and dismissed follow-up records
                </p>
              </div>
              <span className="text-accent text-xs shrink-0 font-bold">
                {viewMode === "ALL_ISSUES" ? "● Active" : "→"}
              </span>
            </button>
          </div>

          {/* ========================================================================= */}
          {/* VIEW MODE 1: INVESTIGATION ACTION QUEUE (PHASE F23)                      */}
          {/* ========================================================================= */}
          {viewMode === "ACTION_QUEUE" && (
            <div className="space-y-3">
              {/* Epistemic Boundary Notice Banner */}
              <div className="p-2.5 rounded-lg bg-accent/5 border border-accent/25 text-[11px] font-mono text-text-secondary flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                <div className="space-y-0.5">
                  <div className="flex items-center gap-1.5 text-accent font-bold text-[10.5px] uppercase tracking-wide">
                    <span>⚡ Deterministic Workflow Triage</span>
                    <span className="text-text-muted lowercase font-normal">
                      • attention priority only (not investment advice or financial risk score)
                    </span>
                  </div>
                  <p className="text-[10.5px] text-text-muted font-sans leading-relaxed">
                    Prioritizes investigations by overdue target dates (Tier 1), reopened outcomes (Tier 2), evidence drift (Tier 3), active inquiries (Tier 4), waiting dependencies (Tier 5), and residual uncertainty (Tier 6).
                  </p>
                </div>
                <div className="text-[10px] font-mono text-text-muted shrink-0 text-right">
                  <span>Ordered deterministically • No LLM scoring</span>
                </div>
              </div>

              {/* Action Queue Search & Fund Scope Bar */}
              <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-2">
                <div className="relative flex-1">
                  <input
                    type="text"
                    placeholder="Search queued investigations, diligence questions, next actions, uncertainty, run IDs..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="w-full pl-8 pr-7 py-1.5 rounded-lg bg-surface-raised border border-border text-text-primary text-xs placeholder:text-text-muted focus:outline-hidden focus:border-accent font-sans"
                  />
                  <span className="absolute left-2.5 top-2 text-text-muted text-xs">🔍</span>
                  {searchQuery && (
                    <button
                      type="button"
                      onClick={() => setSearchQuery("")}
                      className="absolute right-2.5 top-1.5 text-text-muted hover:text-text-primary text-xs cursor-pointer"
                      title="Clear search query"
                    >
                      ✕
                    </button>
                  )}
                </div>

                {/* Fund Option Scope Dropdown */}
                {fundOptions.length > 1 && (
                  <div className="flex items-center gap-1.5 shrink-0 text-[11px]">
                    <span className="text-text-tertiary">Fund:</span>
                    <select
                      value={selectedFundOption}
                      onChange={(e) => setSelectedFundOption(e.target.value)}
                      className="px-2 py-1 rounded bg-surface-raised border border-border text-text-primary text-[11px] focus:outline-hidden max-w-[200px] truncate"
                    >
                      <option value="ALL">All Funds ({actionQueue.length})</option>
                      {fundOptions.map((f) => (
                        <option key={f.key} value={f.key}>
                          {f.schemeName} ({f.planType || "DIR"} • {f.optionType || "GR"})
                        </option>
                      ))}
                    </select>
                  </div>
                )}
              </div>

              {/* Action Queue Sub-Filter Buttons */}
              <div className="flex flex-wrap items-center gap-1.5 text-xs">
                <button
                  type="button"
                  onClick={() => setQueueSubFilter("ALL")}
                  className={`px-2.5 py-1 rounded text-[11px] font-semibold border transition-all cursor-pointer ${
                    queueSubFilter === "ALL"
                      ? "bg-accent/20 border-accent text-accent shadow-xs"
                      : "bg-surface-raised border-border text-text-secondary hover:text-text-primary"
                  }`}
                >
                  All Actionable ({queueSummary.totalActionableCount})
                </button>

                {queueSummary.overdueCount > 0 && (
                  <button
                    type="button"
                    onClick={() => setQueueSubFilter("OVERDUE")}
                    className={`px-2.5 py-1 rounded text-[11px] font-semibold border transition-all cursor-pointer ${
                      queueSubFilter === "OVERDUE"
                        ? "bg-rose-500/20 border-rose-500/80 text-rose-300 shadow-xs"
                        : "bg-surface-raised border-border text-rose-400 hover:text-rose-300"
                    }`}
                  >
                    ⚠️ Overdue Target Date ({queueSummary.overdueCount})
                  </button>
                )}

                {queueSummary.reopenedCount > 0 && (
                  <button
                    type="button"
                    onClick={() => setQueueSubFilter("REOPENED")}
                    className={`px-2.5 py-1 rounded text-[11px] font-semibold border transition-all cursor-pointer ${
                      queueSubFilter === "REOPENED"
                        ? "bg-purple-500/20 border-purple-500/80 text-purple-300 shadow-xs"
                        : "bg-surface-raised border-border text-purple-300/80 hover:text-purple-200"
                    }`}
                  >
                    🔄 Reopened Inquiries ({queueSummary.reopenedCount})
                  </button>
                )}

                {queueSummary.evidenceDriftCount > 0 && (
                  <button
                    type="button"
                    onClick={() => setQueueSubFilter("EVIDENCE_DRIFT")}
                    className={`px-2.5 py-1 rounded text-[11px] font-semibold border transition-all cursor-pointer ${
                      queueSubFilter === "EVIDENCE_DRIFT"
                        ? "bg-amber-500/25 border-amber-500/80 text-amber-300 shadow-xs"
                        : "bg-surface-raised border-amber-500/30 text-amber-300 hover:bg-amber-500/10"
                    }`}
                  >
                    ⚠️ Evidence Drift / Superseded ({queueSummary.evidenceDriftCount})
                  </button>
                )}

                <button
                  type="button"
                  onClick={() => setQueueSubFilter("OPEN_IN_PROGRESS")}
                  className={`px-2.5 py-1 rounded text-[11px] font-semibold border transition-all cursor-pointer ${
                    queueSubFilter === "OPEN_IN_PROGRESS"
                      ? "bg-accent/20 border-accent/80 text-accent shadow-xs"
                      : "bg-surface-raised border-border text-text-secondary hover:text-text-primary"
                  }`}
                >
                  Active Open / In Progress ({queueSummary.openInProgressCount})
                </button>

                {queueSummary.waitingCount > 0 && (
                  <button
                    type="button"
                    onClick={() => setQueueSubFilter("WAITING")}
                    className={`px-2.5 py-1 rounded text-[11px] font-semibold border transition-all cursor-pointer ${
                      queueSubFilter === "WAITING"
                        ? "bg-indigo-500/20 border-indigo-500/80 text-indigo-300 shadow-xs"
                        : "bg-surface-raised border-border text-text-secondary hover:text-text-primary"
                    }`}
                  >
                    ⏳ Waiting on Dependency ({queueSummary.waitingCount})
                  </button>
                )}

                {queueSummary.dependencyAddressedCount > 0 && (
                  <button
                    type="button"
                    onClick={() => setQueueSubFilter("ADDRESSED_BLOCKERS")}
                    className={`px-2.5 py-1 rounded text-[11px] font-semibold border transition-all cursor-pointer ${
                      queueSubFilter === "ADDRESSED_BLOCKERS"
                        ? "bg-emerald-500/20 border-emerald-500/80 text-emerald-300 shadow-xs"
                        : "bg-surface-raised border-emerald-500/40 text-emerald-300/80 hover:text-emerald-200"
                    }`}
                  >
                    ✓ Addressed Blockers ({queueSummary.dependencyAddressedCount})
                  </button>
                )}
              </div>

              {/* Active Filter Chips Bar (Queue) */}
              {(Boolean(searchQuery.trim()) || selectedFundOption !== "ALL" || queueSubFilter !== "ALL") && (
                <div className="flex flex-wrap items-center gap-1.5 text-[10px] text-text-muted bg-surface-raised/40 p-1.5 rounded border border-border/60">
                  <span className="font-bold text-text-secondary uppercase">Active:</span>
                  {searchQuery.trim() && (
                    <span className="px-1.5 py-0.5 rounded bg-accent/15 border border-accent/30 text-accent flex items-center gap-1">
                      Query: &ldquo;{searchQuery}&rdquo;
                      <button type="button" onClick={() => setSearchQuery("")} className="hover:text-text-primary cursor-pointer">✕</button>
                    </span>
                  )}
                  {queueSubFilter !== "ALL" && (
                    <span className="px-1.5 py-0.5 rounded bg-surface border border-border text-text-primary flex items-center gap-1">
                      Filter: {queueSubFilter}
                      <button type="button" onClick={() => setQueueSubFilter("ALL")} className="hover:text-text-primary cursor-pointer">✕</button>
                    </span>
                  )}
                  {selectedFundOption !== "ALL" && (
                    <span className="px-1.5 py-0.5 rounded bg-surface border border-border text-text-primary flex items-center gap-1">
                      Fund Scope Filtered
                      <button type="button" onClick={() => setSelectedFundOption("ALL")} className="hover:text-text-primary cursor-pointer">✕</button>
                    </span>
                  )}
                  <button
                    type="button"
                    onClick={handleResetQueueFilters}
                    className="ml-auto text-text-secondary hover:text-accent underline text-[10px] font-semibold cursor-pointer"
                  >
                    Reset Queue Filters ↺
                  </button>
                </div>
              )}

              {/* Dossier Assembly & Export Action Bar (Queue Mode) */}
              {actionQueue.length > 0 && (
                <div className="flex flex-wrap items-center justify-between gap-2 p-2.5 rounded-lg bg-surface-raised/60 border border-border">
                  <div className="flex flex-wrap items-center gap-2">
                    <button
                      type="button"
                      onClick={handleSelectAllVisible}
                      className="btn btn-secondary btn-xs text-[11px] font-mono cursor-pointer"
                      title="Toggle selection of all currently visible queued records"
                    >
                      {allVisibleSelected ? "Deselect Visible" : `Select All Visible (${filteredActionQueue.length})`}
                    </button>
                    {selectedIssueIds.length > 0 && (
                      <button
                        type="button"
                        onClick={handleClearSelection}
                        className="btn btn-ghost btn-xs text-[11px] font-mono text-text-muted hover:text-text-primary cursor-pointer"
                      >
                        Clear Selection ({selectedIssueIds.length})
                      </button>
                    )}
                    <span className="text-[11px] font-mono text-text-secondary">
                      {selectedIssueIds.length} of {issues.length} record{issues.length !== 1 ? "s" : ""} selected for dossier
                    </span>
                  </div>

                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => setIsDossierModalOpen(true)}
                      disabled={selectedIssueIds.length === 0}
                      className={`btn btn-xs sm:btn-sm text-xs font-semibold flex items-center gap-1.5 transition-all cursor-pointer ${
                        selectedIssueIds.length > 0
                          ? "btn-primary shadow-xs"
                          : "btn-secondary opacity-50 cursor-not-allowed"
                      }`}
                      title={
                        selectedIssueIds.length > 0
                          ? `Assemble and export research dossier from ${selectedIssueIds.length} selected record(s)`
                          : "Select at least one record using checkboxes to assemble research dossier"
                      }
                    >
                      <span>Assemble Research Dossier ({selectedIssueIds.length})</span>
                      <span>📑</span>
                    </button>
                  </div>
                </div>
              )}

              {/* Queued Cards List */}
              {filteredActionQueue.length === 0 ? (
                <div className="p-6 rounded-lg bg-surface-raised/40 border border-border/80 text-center space-y-2">
                  <span className="text-2xl block">
                    {searchQuery.trim() || queueSubFilter !== "ALL" ? "🔍" : "✅"}
                  </span>
                  <p className="font-bold text-text-primary text-xs">
                    {searchQuery.trim()
                      ? `No Queued Investigations Match "${searchQuery}"`
                      : queueSubFilter !== "ALL"
                      ? `No Investigations Match Sub-Filter "${queueSubFilter}"`
                      : "No Actionable Inquiries Currently Pending"}
                  </p>
                  <p className="text-text-muted text-[11px] max-w-md mx-auto leading-relaxed font-sans">
                    {searchQuery.trim() || queueSubFilter !== "ALL"
                      ? "Try broadening your query or clearing sub-filters to inspect other queued items."
                      : "All documented follow-up inquiries are either resolved, dismissed, or awaiting future diligence dates without recorded evidence drift. You can review the complete history in the Complete Issue Archive."}
                  </p>
                  {searchQuery.trim() || queueSubFilter !== "ALL" || selectedFundOption !== "ALL" ? (
                    <button
                      type="button"
                      onClick={handleResetQueueFilters}
                      className="btn btn-secondary btn-xs text-accent font-mono text-[10.5px] mt-1 cursor-pointer"
                    >
                      Reset Queue Filters ↺
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={() => setViewMode("ALL_ISSUES")}
                      className="btn btn-secondary btn-xs text-accent font-mono text-[10.5px] mt-1 cursor-pointer"
                    >
                      Switch to Complete Archive ({issues.length}) →
                    </button>
                  )}
                </div>
              ) : (
                <div className="grid grid-cols-1 gap-3">
                  {filteredActionQueue.map((item) => {
                    const issue = item.issue;
                    const catMeta = FOLLOW_UP_CATEGORIES_CONFIG[issue.category] || FOLLOW_UP_CATEGORIES_CONFIG.OTHER;
                    const statusMeta = FOLLOW_UP_STATUS_CONFIG[issue.status] || FOLLOW_UP_STATUS_CONFIG.OPEN;
                    const isSelected = selectedIssueIds.includes(issue.id);
                    const searchRes = searchResultsMap.get(issue.id);

                    const tierBadgeColors: Record<number, string> = {
                      1: "bg-rose-500/20 text-rose-300 border-rose-500/40",
                      2: "bg-purple-500/20 text-purple-300 border-purple-500/40",
                      3: "bg-amber-500/20 text-amber-300 border-amber-500/40",
                      4: "bg-accent/20 text-accent border-accent/40",
                      5: "bg-indigo-500/20 text-indigo-300 border-indigo-500/40",
                      6: "bg-yellow-500/20 text-yellow-300 border-yellow-500/40",
                    };

                    return (
                      <div
                        key={issue.id}
                        className={`p-3.5 rounded-lg bg-surface border transition-all flex flex-col md:flex-row md:items-start justify-between gap-3 text-xs ${
                          isSelected
                            ? "border-accent ring-1 ring-accent/30 bg-accent/[0.02]"
                            : item.isOverdue
                            ? "border-rose-500/40 hover:border-rose-500/60"
                            : item.priorityTier === 2
                            ? "border-purple-500/40 hover:border-purple-500/60"
                            : item.priorityTier === 3
                            ? "border-amber-500/40 hover:border-amber-500/60"
                            : "border-border/80 hover:border-border"
                        }`}
                      >
                        <div className="space-y-2 flex-1 min-w-0">
                          {/* Priority Tier, Badges & Checkbox */}
                          <div className="flex flex-wrap items-center gap-1.5">
                            <label
                              className="flex items-center gap-1.5 cursor-pointer select-none"
                              title="Select record for research dossier"
                            >
                              <input
                                type="checkbox"
                                checked={isSelected}
                                onChange={() => handleToggleSelectIssue(issue.id)}
                                className="rounded border-border text-accent focus:ring-accent h-3.5 w-3.5 bg-surface-raised cursor-pointer"
                              />
                            </label>

                            {/* Primary Priority Tier Badge */}
                            <span
                              className={`text-[9.5px] px-2 py-0.5 rounded border font-mono font-bold ${
                                tierBadgeColors[item.priorityTier] || "bg-surface-raised text-text-primary border-border"
                              }`}
                              title={`Workflow Attention Priority Tier ${item.priorityTier} of 6`}
                            >
                              ⚡ Tier {item.priorityTier}: {item.primaryReason.label}
                            </span>

                            {/* Additional Reason Badges if multiple conditions met */}
                            {item.allReasons.slice(1).map((r) => (
                              <span
                                key={r.code}
                                className={`text-[9px] px-1.5 py-0.2 rounded border font-semibold ${r.badgeClass}`}
                                title={r.explanation}
                              >
                                + {r.shortLabel}
                              </span>
                            ))}

                            {/* Lifecycle Status Badge */}
                            <span className={`text-[9.5px] px-1.5 py-0.2 rounded border font-semibold ${statusMeta.badgeClass}`}>
                              {statusMeta.label}
                            </span>

                            {/* Closure State Badge */}
                            {item.closureState && (
                              <span className={`text-[9.5px] px-1.5 py-0.2 rounded border font-semibold ${item.closureState.badgeClass}`}>
                                {item.closureState.label}
                              </span>
                            )}

                            {/* Category Badge */}
                            <span className={`text-[9.5px] px-1.5 py-0.2 rounded border font-semibold ${catMeta.badgeClass}`}>
                              {catMeta.shortLabel}
                            </span>

                            {/* Traceability Badge */}
                            <span
                              className={`text-[9.5px] px-1.5 py-0.2 rounded border font-semibold font-mono ${item.provenance.statusBadgeClass}`}
                              title={item.provenance.statusDescription}
                            >
                              {item.provenance.statusBadgeLabel}
                            </span>

                            {/* Preserved Record Banner (if not in active watchlist) */}
                            {item.isPreservedWithoutWatchlist && (
                              <span
                                className="status-badge state-candidate text-[9.5px] text-amber-300"
                                title="This inquiry is preserved from historical research, but its exact fund option is not in your active watchlist."
                              >
                                Preserved record (not in watchlist)
                              </span>
                            )}
                          </div>

                          {/* Fund Option Identity & Section Scope */}
                          <div className="text-[10.5px] text-text-secondary flex flex-wrap items-center gap-1.5">
                            <strong className="text-text-primary">{item.schemeName}</strong>
                            <span>•</span>
                            <span>Option #{item.schemeOptionId || item.schemeId} ({item.planType} • {item.optionType})</span>
                            {item.amfiCode && <span>• AMFI: {item.amfiCode}</span>}
                            {item.isin && <span>• ISIN: {item.isin}</span>}
                            {issue.briefSection && (
                              <>
                                <span>•</span>
                                <span className="text-accent font-semibold">{getBriefSectionTitle(issue.briefSection)}</span>
                              </>
                            )}
                            {issue.targetDate && (
                              <>
                                <span>•</span>
                                <span
                                  className={`font-mono font-bold ${
                                    item.isOverdue ? "text-rose-400" : "text-text-primary"
                                  }`}
                                >
                                  Target: {issue.targetDate} {item.isOverdue ? "(Overdue)" : ""}
                                </span>
                              </>
                            )}
                            <span>•</span>
                            <span className="text-text-muted">Updated {formatSessionRelativeTime(issue.updatedAt)}</span>
                          </div>

                          {/* Search Match Highlight */}
                          {searchQuery.trim() && searchRes?.primaryMatchLabel && (
                            <div className="p-1 rounded bg-accent/10 border border-accent/30 text-[10.5px] font-mono text-accent flex flex-wrap items-center gap-1.5">
                              <span className="font-bold uppercase text-[9px] bg-accent/20 px-1 py-0.2 rounded">
                                SEARCH MATCH
                              </span>
                              <span>{searchRes.primaryMatchLabel}</span>
                            </div>
                          )}

                          {/* Issue Title & Diligence Question */}
                          <div className="space-y-1">
                            <h4 className="font-bold text-text-primary text-[12.5px] leading-snug">
                              {issue.title}
                            </h4>
                            <p className="text-[11.5px] text-text-secondary font-sans leading-relaxed">
                              <strong className="text-text-primary font-mono text-[10px] uppercase">Diligence Question:</strong>{" "}
                              {issue.questionOrReason}
                            </p>
                          </div>

                          {/* Box 1: Why Attention Needed (Explains Triage Inclusion) */}
                          <div className="p-2 rounded bg-surface-raised/70 border border-border/80 text-[11px] font-sans text-text-primary space-y-1">
                            <div className="flex items-center gap-1.5 text-accent font-mono text-[10px] uppercase font-bold">
                              <span>💡 Why Attention Needed</span>
                            </div>
                            <p className="leading-relaxed text-text-secondary">
                              {item.whyAttentionNeeded}
                            </p>
                          </div>

                          {/* Box 2: Suggested / Planned Next Action */}
                          <div className="p-2 rounded bg-accent/5 border border-accent/30 text-[11px] font-sans text-text-primary space-y-1">
                            <div className="flex flex-wrap items-center justify-between gap-1.5">
                              <div className="flex items-center gap-1.5 text-accent font-mono text-[10px] uppercase font-bold">
                                <span>🎯 Planned Action / Next Step</span>
                              </div>
                              {item.isWaiting && (
                                <span className="text-[9px] px-1.5 py-0.2 rounded bg-indigo-500/20 text-indigo-300 border border-indigo-500/40 font-mono font-bold">
                                  ⏳ WAITING ON DEPENDENCY
                                </span>
                              )}
                            </div>
                            <p className="leading-relaxed text-text-primary font-medium">
                              {item.suggestedNextStep}
                            </p>
                            {issue.nextAction && item.suggestedNextStep !== issue.nextAction && (
                              <p className="text-[10px] text-text-muted font-mono pt-0.5">
                                Recorded next action: &ldquo;{issue.nextAction}&rdquo;
                              </p>
                            )}
                          </div>

                          {/* Box 3: Evidence Context & Provenance Comparison Strip */}
                          <div className="p-2 rounded bg-surface-raised/50 border border-border/70 text-[10.5px] font-mono text-text-secondary space-y-1">
                            <div className="flex flex-wrap items-center justify-between gap-1.5 text-[9.5px]">
                              <span className="font-bold text-text-primary uppercase">Evidence Context &amp; Provenance:</span>
                              {item.isCutoffDrifted ? (
                                <span className="text-amber-300 font-bold">⚠️ Observation Cutoff Drift Detected</span>
                              ) : item.isRunSuperseded ? (
                                <span className="text-blue-300 font-bold">⚠️ Newer Calculation Run Available</span>
                              ) : !item.hasActiveContext ? (
                                <span className="text-text-muted">ℹ️ Active workspace comparison unavailable</span>
                              ) : item.provenance.status === "PROVENANCE_NOT_RECORDED" ? (
                                <span className="text-text-muted">❓ Historical provenance not recorded</span>
                              ) : (
                                <span className="text-emerald-400">✓ Evidence context matches active workspace</span>
                              )}
                            </div>
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-1 text-[10px] pt-0.5 border-t border-border/40">
                              <div>
                                <span className="text-text-muted">Recorded: </span>
                                <span className="text-text-primary">
                                  {issue.evidenceRunId ? `Run #${issue.evidenceRunId}` : "Run not recorded"}
                                  {issue.evidenceAsOfDate ? ` (Cutoff ${issue.evidenceAsOfDate})` : " (Cutoff not recorded)"}
                                </span>
                              </div>
                              <div>
                                <span className="text-text-muted">Active: </span>
                                <span className="text-text-primary">
                                  {item.activeNavDate ? `Latest NAV ${item.activeNavDate}` : "NAV date unavailable"}
                                  {item.activeRunId ? ` (Run #${item.activeRunId})` : ""}
                                </span>
                              </div>
                            </div>
                          </div>

                          {/* Phase F29: Investigation Prerequisites & Blocker Status Strip */}
                          {item.dependencyEvaluation && (item.dependencyEvaluation.totalDependencies > 0 || issue.status === "WAITING") && (
                            <div className="p-2 rounded bg-surface-raised/70 border border-border/80 text-[10.5px] font-mono space-y-1.5">
                              <div className="flex flex-wrap items-center justify-between gap-1.5">
                                <div className="flex items-center gap-1.5">
                                  <span>⏳</span>
                                  <span className="font-bold uppercase text-[9.5px] text-text-primary">
                                    Prerequisites &amp; Blockers:
                                  </span>
                                  <span className={`text-[8.5px] px-1.5 py-0.2 rounded border font-semibold ${item.dependencyEvaluation.badgeClass}`}>
                                    {item.dependencyEvaluation.stateLabel}
                                  </span>
                                </div>
                                <span className="text-[9.5px] text-text-muted">
                                  {item.dependencyEvaluation.awaitingCount} Awaiting • {item.dependencyEvaluation.addressedCount} Addressed
                                </span>
                              </div>

                              <p className="text-[10.5px] font-sans text-text-secondary leading-snug">
                                {item.dependencyEvaluation.description}
                              </p>

                              {/* Active Blockers list */}
                              {item.dependencyEvaluation.activeBlockers.length > 0 && (
                                <div className="space-y-1 pt-1 border-t border-border/40 text-[10px]">
                                  {item.dependencyEvaluation.activeBlockers.map((b) => (
                                    <div key={b.id} className="flex items-start justify-between gap-1.5 text-amber-300 font-sans">
                                      <span className="truncate">
                                        • <strong>{b.description}</strong> {b.expectedSource ? `(Source: ${b.expectedSource})` : ""}
                                      </span>
                                      <button
                                        type="button"
                                        onClick={() => onOpenIssueModal(issue, null, { initialDependencyId: b.id })}
                                        className="text-emerald-400 hover:underline shrink-0 font-mono text-[9.5px]"
                                      >
                                        Address Blocker ↗
                                      </button>
                                    </div>
                                  ))}
                                </div>
                              )}

                              {/* Addressed Blocker preview if ready to review */}
                              {item.dependencyEvaluation.addressedBlockers.length > 0 && item.dependencyEvaluation.activeBlockers.length === 0 && (
                                <div className="pt-1 border-t border-border/40 text-[10px] text-emerald-300 font-sans flex items-center justify-between gap-1.5">
                                  <span className="truncate">
                                    ✓ All {item.dependencyEvaluation.addressedCount} prerequisite(s) addressed. Ready for empirical review and resumption.
                                  </span>
                                  <button
                                    type="button"
                                    onClick={() => onOpenIssueModal(issue, null)}
                                    className="text-accent hover:underline shrink-0 font-mono text-[9.5px]"
                                  >
                                    Resume Review ↗
                                  </button>
                                </div>
                              )}
                            </div>
                          )}

                          {/* Preserved prior outcome note if reopened */}
                          {issue.priorResolutionNote && (
                            <div className="p-2 rounded bg-purple-950/20 border border-purple-500/30 text-[11px] font-sans text-purple-200/90 space-y-0.5">
                              <div className="flex flex-wrap items-center gap-1.5 text-[9.5px] font-mono text-purple-300 uppercase font-bold">
                                <span>🔄 Preserved Prior Outcome (Requires Renewed Review)</span>
                                {issue.priorOutcomeType && FOLLOW_UP_OUTCOMES_CONFIG[issue.priorOutcomeType] && (
                                  <span className={`px-1 py-0.2 rounded text-[8.5px] border ${FOLLOW_UP_OUTCOMES_CONFIG[issue.priorOutcomeType].badgeClass}`}>
                                    {FOLLOW_UP_OUTCOMES_CONFIG[issue.priorOutcomeType].shortLabel}
                                  </span>
                                )}
                                {issue.priorResolvedAt && (
                                  <span className="text-text-muted lowercase font-normal">
                                    (resolved {formatSessionRelativeTime(issue.priorResolvedAt)})
                                  </span>
                                )}
                              </div>
                              <p className="leading-relaxed line-clamp-2 text-[11px] text-text-secondary">
                                {issue.priorResolutionNote}
                              </p>
                              {issue.reopenReason && (
                                <p className="text-[10px] text-purple-300 font-sans pt-0.5 border-t border-purple-500/20">
                                  <span className="font-bold font-mono text-[9px] uppercase">Renewal Rationale:</span> {issue.reopenReason}
                                </p>
                              )}
                            </div>
                          )}

                          {/* Residual uncertainty callout if present */}
                          {issue.residualUncertainty && (
                            <div className="p-1.5 rounded bg-yellow-500/10 border border-yellow-500/30 text-[10.5px] font-sans text-yellow-300 flex items-start gap-1.5">
                              <span className="font-mono text-[9px] uppercase font-bold text-yellow-400 shrink-0">
                                Residual Uncertainty:
                              </span>
                              <span className="leading-relaxed">{issue.residualUncertainty}</span>
                            </div>
                          )}

                          {/* Phase F28: Preserved Reassessment Disposition & History */}
                          {issue.reassessmentDisposition && (
                            <div className="p-1.5 rounded bg-purple-950/20 border border-purple-500/30 text-[10.5px] font-sans text-purple-200/90 space-y-0.5">
                              <div className="flex flex-wrap items-center gap-1.5 text-[9px] font-mono text-purple-300 uppercase font-bold">
                                <span>⚡ Reassessment Recorded</span>
                                <span className="px-1 py-0.2 rounded text-[8px] border border-purple-500/40 bg-purple-900/30">
                                  {issue.reassessmentDisposition}
                                </span>
                                {issue.reassessedAt && (
                                  <span className="text-text-muted lowercase font-normal">
                                    ({formatSessionRelativeTime(issue.reassessedAt)})
                                  </span>
                                )}
                              </div>
                              {issue.reassessmentRationale && (
                                <p className="leading-relaxed line-clamp-2 text-[10.5px] text-text-secondary">
                                  {issue.reassessmentRationale}
                                </p>
                              )}
                            </div>
                          )}
                        </div>

                        {/* Interactive Workflow Action Buttons */}
                        <div className="flex flex-row md:flex-col items-end gap-1.5 shrink-0 self-start">
                          <button
                            type="button"
                            disabled={item.isPreservedWithoutWatchlist}
                            onClick={() =>
                              onNavigateToBrief(
                                item.schemeId,
                                item.schemeOptionId,
                                issue.briefSection || "B",
                                issue
                              )
                            }
                            className={`btn btn-xs text-[10.5px] font-mono whitespace-nowrap transition-all ${
                              item.isPreservedWithoutWatchlist
                                ? "btn-secondary opacity-50 cursor-not-allowed text-text-muted"
                                : "btn-secondary text-accent border-accent/40 hover:bg-accent/10 cursor-pointer"
                            }`}
                            title={
                              item.isPreservedWithoutWatchlist
                                ? "Direct brief navigation is unavailable because this exact fund option is not in your active watchlist. Never redirected to different share classes."
                                : "Resume active research in Brief (inspects current data; historical state is not reconstructed)"
                            }
                          >
                            {item.isPreservedWithoutWatchlist
                              ? "Brief Unavailable ↗"
                              : issue.priorResolutionNote || issue.reopenedAt
                              ? `Resume Reopened (Sec ${issue.briefSection || "B"}) ↗`
                              : item.primaryReason.code === "EVIDENCE_DRIFT"
                              ? `Resume Drift (Sec ${issue.briefSection || "F"}) ↗`
                              : `Resume In Brief (Sec ${issue.briefSection || "B"}) ↗`}
                          </button>

                          {/* Phase F28: Contextual Reassessment Action */}
                          {(item.isCutoffDrifted || item.isRunSuperseded || issue.priorResolutionNote || issue.reopenedAt || issue.reassessmentDisposition) && (
                            <button
                              type="button"
                              onClick={() =>
                                onOpenIssueModal(issue, null, {
                                  initialReassessmentMode: Boolean(item.isCutoffDrifted || item.isRunSuperseded || issue.reopenedAt),
                                  initialShowTimeline: true,
                                })
                              }
                              className="btn btn-secondary btn-xs text-[10.5px] font-mono text-purple-300 border-purple-500/40 hover:bg-purple-950/30 whitespace-nowrap cursor-pointer"
                              title="Inspect investigation timeline, distinguish prior outcome from current evidence, and record reassessment"
                            >
                              {item.isCutoffDrifted || item.isRunSuperseded ? "Begin Reassessment ⚡" : "Review Evidence History 📜"}
                            </button>
                          )}

                          {/* Phase F29: Contextual Dependency / Blocker Resolution Action */}
                          {item.dependencyEvaluation?.activeBlockers && item.dependencyEvaluation.activeBlockers.length > 0 && (
                            <button
                              type="button"
                              onClick={() =>
                                onOpenIssueModal(issue, null, {
                                  initialDependencyId: item.dependencyEvaluation?.activeBlockers[0]?.id || null,
                                })
                              }
                              className="btn btn-secondary btn-xs text-[10.5px] font-mono text-emerald-400 border-emerald-500/40 hover:bg-emerald-950/30 whitespace-nowrap cursor-pointer"
                              title="Record received disclosure, evidence, or prerequisite change to address blocker"
                            >
                              Address Blocker ✓
                            </button>
                          )}

                          {item.dependencyEvaluation?.state === "ALL_ADDRESSED_REVIEW_NEEDED" && (
                            <button
                              type="button"
                              onClick={() => onOpenIssueModal(issue, null)}
                              className="btn btn-primary btn-xs text-[10.5px] font-mono text-white bg-emerald-600 hover:bg-emerald-500 border-emerald-500 whitespace-nowrap cursor-pointer"
                              title="All prerequisites addressed. Review evidence and resume investigation."
                            >
                              Resume Review ⚡
                            </button>
                          )}

                          <button
                            type="button"
                            onClick={() => onOpenIssueModal(issue)}
                            className="btn btn-ghost btn-xs text-[10.5px] font-mono text-text-secondary hover:text-text-primary border border-border/80 whitespace-nowrap cursor-pointer"
                            title="Update issue details, record progress, or transition lifecycle status"
                          >
                            Update / Resolve ✎
                          </button>

                          {!item.isPreservedWithoutWatchlist && (
                            <button
                              type="button"
                              onClick={() =>
                                onNavigateToBrief(
                                  item.schemeId,
                                  item.schemeOptionId,
                                  "coverage",
                                  issue
                                )
                              }
                              className="btn btn-ghost btn-xs text-[10.5px] font-mono text-text-secondary hover:text-accent border border-border/80 whitespace-nowrap cursor-pointer"
                              title="Triage research coverage gaps for this fund option"
                            >
                              Coverage 🔍
                            </button>
                          )}

                          <button
                            type="button"
                            onClick={() => {
                              if (!selectedIssueIds.includes(issue.id)) {
                                setSelectedIssueIds((prev) => [...prev, issue.id]);
                              }
                              setIsDossierModalOpen(true);
                            }}
                            className="btn btn-ghost btn-xs text-[10.5px] font-mono text-text-muted hover:text-accent border border-border/80 whitespace-nowrap cursor-pointer"
                            title="Assemble portable research dossier for this finding"
                          >
                            Dossier 📑
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}

          {/* ========================================================================= */}
          {/* VIEW MODE 2: COMPLETE ISSUE ARCHIVE (EXISTING WORKSPACE FUNCTIONALITY)    */}
          {/* ========================================================================= */}
          {viewMode === "ALL_ISSUES" && (
            <div className="space-y-3">
              {/* Phase F20: Search Bar for Follow-Up Inquiries & Findings */}
          <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-2">
            <div className="relative flex-1">
              <input
                type="text"
                placeholder="Search inquiries, diligence questions, outcome notes, residual uncertainty, run IDs..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full pl-8 pr-7 py-1.5 rounded-lg bg-surface-raised border border-border text-text-primary text-xs placeholder:text-text-muted focus:outline-hidden focus:border-accent font-sans"
              />
              <span className="absolute left-2.5 top-2 text-text-muted text-xs">🔍</span>
              {searchQuery && (
                <button
                  type="button"
                  onClick={() => setSearchQuery("")}
                  className="absolute right-2.5 top-1.5 text-text-muted hover:text-text-primary text-xs"
                  title="Clear search query"
                >
                  ✕
                </button>
              )}
            </div>
            {searchQuery.trim() && (
              <div className="text-[11px] font-mono text-accent flex items-center gap-1.5 shrink-0 bg-accent/10 px-2.5 py-1 rounded border border-accent/20">
                <span>{filteredIssues.length} of {issues.length} matching</span>
                <button
                  type="button"
                  onClick={() => setSearchQuery("")}
                  className="text-text-muted hover:text-text-primary underline ml-1 text-[10px]"
                >
                  Clear ✕
                </button>
              </div>
            )}
          </div>

          {/* Filter Pills */}
          <div className="flex flex-wrap items-center gap-1.5 text-xs">
            <button
              type="button"
              onClick={() => setActiveFilter("UNRESOLVED")}
              className={`px-2.5 py-1 rounded text-[11px] font-semibold border transition-all ${
                activeFilter === "UNRESOLVED"
                  ? "bg-accent/20 border-accent text-accent shadow-xs"
                  : "bg-surface-raised border-border text-text-secondary hover:text-text-primary"
              }`}
            >
              Active ({summary.unresolvedCount})
            </button>
            <button
              type="button"
              onClick={() => setActiveFilter("OPEN")}
              className={`px-2.5 py-1 rounded text-[11px] font-semibold border transition-all ${
                activeFilter === "OPEN"
                  ? "bg-amber-500/20 border-amber-500/80 text-amber-300 shadow-xs"
                  : "bg-surface-raised border-border text-text-secondary hover:text-text-primary"
              }`}
            >
              Open ({summary.openCount})
            </button>
            <button
              type="button"
              onClick={() => setActiveFilter("IN_PROGRESS")}
              className={`px-2.5 py-1 rounded text-[11px] font-semibold border transition-all ${
                activeFilter === "IN_PROGRESS"
                  ? "bg-blue-500/20 border-blue-500/80 text-blue-300 shadow-xs"
                  : "bg-surface-raised border-border text-text-secondary hover:text-text-primary"
              }`}
            >
              In Progress ({summary.inProgressCount})
            </button>
            <button
              type="button"
              onClick={() => setActiveFilter("WAITING")}
              className={`px-2.5 py-1 rounded text-[11px] font-semibold border transition-all ${
                activeFilter === "WAITING"
                  ? "bg-purple-500/20 border-purple-500/80 text-purple-300 shadow-xs"
                  : "bg-surface-raised border-border text-text-secondary hover:text-text-primary"
              }`}
            >
              Waiting ({summary.waitingCount})
            </button>
            {issues.some((iss) => {
              const depEval = evaluateIssueDependencies(iss);
              return depEval.state === "ALL_ADDRESSED_REVIEW_NEEDED" || depEval.state === "READY_TO_RESUME";
            }) && (
              <button
                type="button"
                onClick={() => setActiveFilter("ADDRESSED_BLOCKERS")}
                className={`px-2.5 py-1 rounded text-[11px] font-semibold border transition-all cursor-pointer ${
                  activeFilter === "ADDRESSED_BLOCKERS"
                    ? "bg-emerald-500/20 border-emerald-500/80 text-emerald-300 shadow-xs"
                    : "bg-surface-raised border-emerald-500/40 text-emerald-300 hover:text-emerald-200"
                }`}
              >
                ✓ Addressed Blockers ({
                  issues.filter((iss) => {
                    const depEval = evaluateIssueDependencies(iss);
                    return depEval.state === "ALL_ADDRESSED_REVIEW_NEEDED" || depEval.state === "READY_TO_RESUME";
                  }).length
                })
              </button>
            )}
            {summary.targetDatePassedCount > 0 && (
              <button
                type="button"
                onClick={() => setActiveFilter("OVERDUE")}
                className={`px-2.5 py-1 rounded text-[11px] font-semibold border transition-all ${
                  activeFilter === "OVERDUE"
                    ? "bg-rose-500/20 border-rose-500/80 text-rose-300 shadow-xs"
                    : "bg-surface-raised border-border text-rose-400 hover:text-rose-300"
                }`}
              >
                Target Passed ({summary.targetDatePassedCount})
              </button>
            )}
            {evidenceDriftCount > 0 && (
              <button
                type="button"
                onClick={() => setActiveFilter("EVIDENCE_DRIFT")}
                className={`px-2.5 py-1 rounded text-[11px] font-semibold border transition-all ${
                  activeFilter === "EVIDENCE_DRIFT"
                    ? "bg-amber-500/25 border-amber-500/80 text-amber-300 shadow-xs"
                    : "bg-surface-raised border-amber-500/30 text-amber-300 hover:bg-amber-500/10"
                }`}
              >
                ⚠️ Evidence Drift ({evidenceDriftCount})
              </button>
            )}
            {reopenedCount > 0 && (
              <button
                type="button"
                onClick={() => setActiveFilter("REOPENED")}
                className={`px-2.5 py-1 rounded text-[11px] font-semibold border transition-all ${
                  activeFilter === "REOPENED"
                    ? "bg-indigo-500/20 border-indigo-500/80 text-indigo-300 shadow-xs"
                    : "bg-surface-raised border-border text-indigo-300/80 hover:text-indigo-200"
                }`}
              >
                🔄 Reopened ({reopenedCount})
              </button>
            )}
            <button
              type="button"
              onClick={() => setActiveFilter("RESOLVED")}
              className={`px-2.5 py-1 rounded text-[11px] font-semibold border transition-all ${
                activeFilter === "RESOLVED"
                  ? "bg-emerald-500/20 border-emerald-500/80 text-emerald-300 shadow-xs"
                  : "bg-surface-raised border-border text-text-secondary hover:text-text-primary"
              }`}
            >
              Resolved ({summary.resolvedCount})
            </button>
            {dismissedCount > 0 && (
              <button
                type="button"
                onClick={() => setActiveFilter("DISMISSED")}
                className={`px-2.5 py-1 rounded text-[11px] font-semibold border transition-all ${
                  activeFilter === "DISMISSED"
                    ? "bg-surface-raised border-border text-text-secondary shadow-xs"
                    : "bg-surface-raised border-border/60 text-text-muted hover:text-text-secondary"
                }`}
              >
                Dismissed ({dismissedCount})
              </button>
            )}
            <button
              type="button"
              onClick={() => setActiveFilter("ALL")}
              className={`px-2.5 py-1 rounded text-[11px] font-semibold border transition-all ${
                activeFilter === "ALL"
                  ? "bg-surface-raised border-accent text-accent font-bold shadow-xs"
                  : "bg-surface-raised border-border text-text-secondary hover:text-text-primary"
              }`}
            >
              All ({summary.totalIssues})
            </button>
          </div>

          {/* Phase F20: Scope & Dimension Filter Dropdowns */}
          <div className="flex flex-wrap items-center justify-between gap-2 text-[11px] text-text-muted pt-1 border-t border-border/40">
            <div className="flex flex-wrap items-center gap-2">
              {/* Fund Option Scope */}
              {fundOptions.length > 1 && (
                <div className="flex items-center gap-1.5">
                  <span className="text-text-tertiary">Fund:</span>
                  <select
                    value={selectedFundOption}
                    onChange={(e) => setSelectedFundOption(e.target.value)}
                    className="px-2 py-0.5 rounded bg-surface-raised border border-border text-text-primary text-[11px] focus:outline-hidden max-w-[190px] truncate"
                  >
                    <option value="ALL">All Funds ({issues.length})</option>
                    {fundOptions.map((f) => (
                      <option key={f.key} value={f.key}>
                        {f.schemeName} ({f.planType || "DIR"} • {f.optionType || "GR"})
                      </option>
                    ))}
                  </select>
                </div>
              )}

              {/* Research Brief Section Scope */}
              <div className="flex items-center gap-1.5">
                <span className="text-text-tertiary">Section:</span>
                <select
                  value={selectedSection}
                  onChange={(e) => setSelectedSection(e.target.value as BriefSectionFilter)}
                  className="px-2 py-0.5 rounded bg-surface-raised border border-border text-text-primary text-[11px] focus:outline-hidden max-w-[170px] truncate"
                >
                  <option value="ALL">All Brief Sections</option>
                  <option value="A">Sec A: Fund Identity</option>
                  <option value="B">Sec B: Period Returns & Risk</option>
                  <option value="C">Sec C: Rolling Returns</option>
                  <option value="D">Sec D: Construction & Outcomes</option>
                  <option value="E">Sec E: Holdings Concentration</option>
                  <option value="F">Sec F: History & Drift</option>
                  <option value="G">Sec G: Portfolio Context</option>
                </select>
              </div>

              {/* Follow-Up Category Scope */}
              <div className="flex items-center gap-1.5">
                <span className="text-text-tertiary">Category:</span>
                <select
                  value={selectedCategory}
                  onChange={(e) => setSelectedCategory(e.target.value)}
                  className="px-2 py-0.5 rounded bg-surface-raised border border-border text-text-primary text-[11px] focus:outline-hidden"
                >
                  <option value="ALL">All Categories</option>
                  {(Object.keys(FOLLOW_UP_CATEGORIES_CONFIG) as FollowUpCategory[]).map((cat) => (
                    <option key={cat} value={cat}>
                      {FOLLOW_UP_CATEGORIES_CONFIG[cat].shortLabel}
                    </option>
                  ))}
                </select>
              </div>

              {/* Special Attributes Filter */}
              <div className="flex items-center gap-1.5">
                <span className="text-text-tertiary">Attributes:</span>
                <select
                  value={selectedAttribute}
                  onChange={(e) => setSelectedAttribute(e.target.value as AttributeFilter)}
                  className="px-2 py-0.5 rounded bg-surface-raised border border-border text-text-primary text-[11px] focus:outline-hidden"
                >
                  <option value="ALL">All Attributes</option>
                  <option value="WITH_RESIDUAL_UNCERTAINTY">⚠️ Has Residual Uncertainty ({residualUncertaintyCount})</option>
                  <option value="WITH_PRIOR_OUTCOME">🔄 Preserved Prior Outcome ({reopenedCount})</option>
                  <option value="RECORDED_PROVENANCE">📊 Recorded Provenance (Run/Cutoff)</option>
                  <option value="UNRECORDED_PROVENANCE">❓ Unrecorded Provenance</option>
                  <option value="EVIDENCE_DRIFT">⚠️ Evidence Drift Detected</option>
                </select>
              </div>
            </div>

            {hasActiveFilters && (
              <button
                type="button"
                onClick={handleResetFilters}
                className="text-text-secondary hover:text-accent underline text-[11px] font-semibold"
              >
                Reset All Filters ↺
              </button>
            )}
          </div>

          {/* Active Filter Chips Bar */}
          {hasActiveFilters && (
            <div className="flex flex-wrap items-center gap-1.5 text-[10px] text-text-muted bg-surface-raised/40 p-1.5 rounded border border-border/60">
              <span className="font-bold text-text-secondary uppercase">Active:</span>
              {searchQuery.trim() && (
                <span className="px-1.5 py-0.5 rounded bg-accent/15 border border-accent/30 text-accent flex items-center gap-1">
                  Query: &ldquo;{searchQuery}&rdquo;
                  <button type="button" onClick={() => setSearchQuery("")} className="hover:text-text-primary">✕</button>
                </span>
              )}
              {activeFilter !== "UNRESOLVED" && (
                <span className="px-1.5 py-0.5 rounded bg-surface border border-border text-text-primary flex items-center gap-1">
                  Status: {activeFilter}
                  <button type="button" onClick={() => setActiveFilter("UNRESOLVED")} className="hover:text-text-primary">✕</button>
                </span>
              )}
              {selectedFundOption !== "ALL" && (
                <span className="px-1.5 py-0.5 rounded bg-surface border border-border text-text-primary flex items-center gap-1">
                  Fund Scope Filtered
                  <button type="button" onClick={() => setSelectedFundOption("ALL")} className="hover:text-text-primary">✕</button>
                </span>
              )}
              {selectedSection !== "ALL" && (
                <span className="px-1.5 py-0.5 rounded bg-surface border border-border text-text-primary flex items-center gap-1">
                  Sec {selectedSection}
                  <button type="button" onClick={() => setSelectedSection("ALL")} className="hover:text-text-primary">✕</button>
                </span>
              )}
              {selectedCategory !== "ALL" && (
                <span className="px-1.5 py-0.5 rounded bg-surface border border-border text-text-primary flex items-center gap-1">
                  Category: {selectedCategory}
                  <button type="button" onClick={() => setSelectedCategory("ALL")} className="hover:text-text-primary">✕</button>
                </span>
              )}
              {selectedAttribute !== "ALL" && (
                <span className="px-1.5 py-0.5 rounded bg-amber-500/15 border border-amber-500/30 text-amber-300 flex items-center gap-1">
                  Attr: {selectedAttribute.replace(/_/g, " ")}
                  <button type="button" onClick={() => setSelectedAttribute("ALL")} className="hover:text-text-primary">✕</button>
                </span>
              )}
              <span className="ml-auto text-[9.5px] text-text-tertiary">
                Showing {filteredIssues.length} of {issues.length}
              </span>
            </div>
          )}

          {/* Phase F21: Research Dossier Assembly & Export Action Bar */}
          {issues.length > 0 && (
            <div className="flex flex-wrap items-center justify-between gap-2 p-2.5 rounded-lg bg-surface-raised/60 border border-border">
              <div className="flex flex-wrap items-center gap-2">
                <button
                  type="button"
                  onClick={handleSelectAllVisible}
                  className="btn btn-secondary btn-xs text-[11px] font-mono"
                  title="Toggle selection of all currently visible records matching active search and filters"
                >
                  {allVisibleSelected ? "Deselect Visible" : `Select All Visible (${filteredIssues.length})`}
                </button>
                {selectedIssueIds.length > 0 && (
                  <button
                    type="button"
                    onClick={handleClearSelection}
                    className="btn btn-ghost btn-xs text-[11px] font-mono text-text-muted hover:text-text-primary"
                  >
                    Clear Selection ({selectedIssueIds.length})
                  </button>
                )}
                <span className="text-[11px] font-mono text-text-secondary">
                  {selectedIssueIds.length} of {issues.length} record{issues.length !== 1 ? "s" : ""} selected for dossier
                </span>
              </div>

              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => setIsDossierModalOpen(true)}
                  disabled={selectedIssueIds.length === 0}
                  className={`btn btn-xs sm:btn-sm text-xs font-semibold flex items-center gap-1.5 transition-all ${
                    selectedIssueIds.length > 0
                      ? "btn-primary shadow-xs"
                      : "btn-secondary opacity-50 cursor-not-allowed"
                  }`}
                  title={
                    selectedIssueIds.length > 0
                      ? `Assemble and export research dossier from ${selectedIssueIds.length} selected record(s)`
                      : "Select at least one record using checkboxes to assemble research dossier"
                  }
                >
                  <span>Assemble Research Dossier ({selectedIssueIds.length})</span>
                  <span>📑</span>
                </button>
              </div>
            </div>
          )}

          {/* Issue Cards & Empty States */}
          {filteredIssues.length === 0 ? (
            <div className="p-6 rounded-lg bg-surface-raised/40 border border-border/80 text-center space-y-2">
              <span className="text-2xl block">{searchQuery.trim() ? "🔍" : "📋"}</span>
              <p className="font-bold text-text-primary text-xs">
                {searchQuery.trim()
                  ? `No Follow-Up Records Match "${searchQuery}"`
                  : hasActiveFilters
                  ? "No Follow-Up Inquiries Match Active Filters"
                  : "No Follow-Up Inquiries Recorded"}
              </p>
              <p className="text-text-muted text-[11px] max-w-md mx-auto leading-relaxed font-sans">
                {searchQuery.trim()
                  ? `No follow-up issues, diligence questions, outcome notes, or residual uncertainties match your search term. Try searching for fund names, calculation run IDs, or specific diligence topics.`
                  : hasActiveFilters
                  ? "Try broadening your filters (Status, Section, Category, or Attributes) to view documented findings and inquiries."
                  : "No inquiries or findings have been documented yet. As you review funds in the Research Brief, create follow-up issues to track unanswered questions and residual uncertainties."}
              </p>
              {hasActiveFilters && (
                <button
                  type="button"
                  onClick={handleResetFilters}
                  className="btn btn-secondary btn-xs text-accent font-mono text-[10.5px] mt-1"
                >
                  Reset All Filters ↺
                </button>
              )}
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-2.5">
              {filteredIssues.map((issue) => {
                const catMeta = FOLLOW_UP_CATEGORIES_CONFIG[issue.category] || FOLLOW_UP_CATEGORIES_CONFIG.OTHER;
                const statusMeta = FOLLOW_UP_STATUS_CONFIG[issue.status] || FOLLOW_UP_STATUS_CONFIG.OPEN;
                const prov = evaluateFindingEvidenceTraceability(issue);
                const closureState = evaluateInvestigationClosureState(issue);
                const searchRes = searchResultsMap.get(issue.id);
                const queuedItem = queueItemMap.get(issue.id);
                const isOverdue =
                  (issue.status === "OPEN" || issue.status === "IN_PROGRESS" || issue.status === "WAITING") &&
                  isTargetDatePassed(issue.targetDate);
                const isEvidenceDriftIssue =
                  issue.category === "EVIDENCE_LIMITATION" ||
                  issue.briefSection === "F" ||
                  issue.title.toLowerCase().includes("drift") ||
                  issue.title.toLowerCase().includes("reassessment");
                const isMissingInWorkspace =
                  Boolean(savedItems && savedItems.length > 0) &&
                  !savedItems?.some((item) =>
                    issue.schemeOptionId !== undefined && issue.schemeOptionId !== null
                      ? item.schemeOptionId === issue.schemeOptionId
                      : item.schemeId === issue.schemeId
                  );

                const isSelected = selectedIssueIds.includes(issue.id);

                return (
                  <div
                    key={issue.id}
                    className={`p-3 rounded-lg bg-surface border transition-all flex flex-col md:flex-row md:items-start justify-between gap-3 text-xs ${
                      isSelected
                        ? "border-accent ring-1 ring-accent/30 bg-accent/[0.02]"
                        : "border-border/80 hover:border-border"
                    }`}
                  >
                    <div className="space-y-1.5 flex-1 min-w-0">
                      {/* Checkbox, Fund Title & Badges */}
                      <div className="flex flex-wrap items-center gap-1.5">
                        <label className="flex items-center gap-1.5 cursor-pointer select-none" title="Select record for research dossier">
                          <input
                            type="checkbox"
                            checked={isSelected}
                            onChange={() => handleToggleSelectIssue(issue.id)}
                            className="rounded border-border text-accent focus:ring-accent h-3.5 w-3.5 bg-surface-raised cursor-pointer"
                          />
                        </label>
                        <span className="font-bold text-text-primary text-[12px]">
                          {issue.title}
                        </span>

                        {/* Action Queue status pill if queued */}
                        {queuedItem && (
                          <button
                            type="button"
                            onClick={() => {
                              setViewMode("ACTION_QUEUE");
                            }}
                            className="text-[9px] px-1.5 py-0.2 rounded bg-accent/20 text-accent border border-accent/40 font-mono font-bold hover:bg-accent/30 cursor-pointer"
                            title={`This item is active in the Action Queue (Priority Tier ${queuedItem.priorityTier}: ${queuedItem.primaryReason.label}). Click to open in queue.`}
                          >
                            ⚡ In Queue (Tier {queuedItem.priorityTier})
                          </button>
                        )}
                        <span className={`text-[9.5px] px-1.5 py-0.2 rounded border font-semibold ${catMeta.badgeClass}`}>
                          {catMeta.shortLabel}
                        </span>
                        <span className={`text-[9.5px] px-1.5 py-0.2 rounded border font-semibold ${statusMeta.badgeClass}`}>
                          {statusMeta.label}
                        </span>
                        <span className={`text-[9.5px] px-1.5 py-0.2 rounded border font-semibold ${closureState.badgeClass}`}>
                          {closureState.label}
                        </span>
                        {issue.outcomeType && FOLLOW_UP_OUTCOMES_CONFIG[issue.outcomeType] && (
                          <span
                            className={`text-[9.5px] px-1.5 py-0.2 rounded border font-semibold ${FOLLOW_UP_OUTCOMES_CONFIG[issue.outcomeType].badgeClass}`}
                            title={FOLLOW_UP_OUTCOMES_CONFIG[issue.outcomeType].description}
                          >
                            {FOLLOW_UP_OUTCOMES_CONFIG[issue.outcomeType].shortLabel}
                          </span>
                        )}
                        <span
                          className={`text-[9.5px] px-1.5 py-0.2 rounded border font-semibold font-mono ${prov.statusBadgeClass}`}
                          title={prov.statusDescription}
                        >
                          {prov.statusBadgeLabel}
                        </span>
                        {issue.residualUncertainty && (
                          <span
                            className="text-[9.5px] px-1.5 py-0.2 rounded bg-amber-500/15 text-amber-300 border border-amber-500/30 font-semibold"
                            title={`Residual Uncertainty: ${issue.residualUncertainty}`}
                          >
                            ⚠️ Residual Uncertainty
                          </span>
                        )}
                        {(issue.priorResolutionNote || issue.reopenedAt) && (
                          <span
                            className="text-[9.5px] px-1.5 py-0.2 rounded bg-indigo-500/20 text-indigo-300 border border-indigo-500/40 font-bold"
                            title="This inquiry was reopened after a prior outcome was recorded"
                          >
                            🔄 Reopened Inquiry
                          </span>
                        )}
                        {isEvidenceDriftIssue && (
                          <span className="text-[9.5px] px-1.5 py-0.2 rounded bg-amber-500/20 text-amber-300 border border-amber-500/40 font-bold">
                            ⚠️ Reassessment Inquiry
                          </span>
                        )}
                        {isOverdue && (
                          <span className="text-[9.5px] px-1.5 py-0.2 rounded bg-rose-500/20 text-rose-300 border border-rose-500/40 font-semibold">
                            ⚠️ Target Passed ({issue.targetDate})
                          </span>
                        )}
                        {isMissingInWorkspace && (
                          <span className="status-badge state-candidate text-[9.5px] text-amber-300">
                            Preserved record (fund option not in saved workspace)
                          </span>
                        )}
                      </div>

                      {/* Phase F20: Search Match Snippet Highlight */}
                      {searchQuery.trim() && searchRes?.primaryMatchLabel && (
                        <div className="p-1 rounded bg-accent/10 border border-accent/30 text-[10.5px] font-mono text-accent flex flex-wrap items-center gap-1.5">
                          <span className="font-bold uppercase text-[9px] bg-accent/20 px-1 py-0.2 rounded">
                            SEARCH MATCH
                          </span>
                          <span>{searchRes.primaryMatchLabel}</span>
                        </div>
                      )}

                      {/* Fund Option Identity & Timestamps */}
                      <div className="text-[10.5px] text-text-secondary flex flex-wrap items-center gap-1.5">
                        <strong className="text-text-primary">{issue.schemeName}</strong>
                        <span>•</span>
                        <span>Option #{issue.schemeOptionId || issue.schemeId} ({issue.planType || "DIRECT"} • {issue.optionType || "GROWTH"})</span>
                        {issue.amfiCode && <span>• AMFI: {issue.amfiCode}</span>}
                        {issue.isin && <span>• ISIN: {issue.isin}</span>}
                        {issue.briefSection && (
                          <>
                            <span>•</span>
                            <span className="text-accent font-semibold">{getBriefSectionTitle(issue.briefSection)}</span>
                          </>
                        )}
                        <span>•</span>
                        <span className="font-mono text-[10px] text-text-muted" title={prov.statusDescription}>
                          Evidence: {issue.evidenceRunId ? `Run #${issue.evidenceRunId}` : "Run not recorded"}
                          {issue.evidenceAsOfDate ? ` (Cutoff ${issue.evidenceAsOfDate})` : " (Cutoff not recorded)"}
                        </span>
                        {issue.targetDate && !isOverdue && (
                          <>
                            <span>•</span>
                            <span>Target: {issue.targetDate}</span>
                          </>
                        )}
                        <span>•</span>
                        <span className="text-text-muted">Updated {formatSessionRelativeTime(issue.updatedAt)}</span>
                      </div>

                      {/* Preserved prior outcome note if reopened */}
                      {issue.priorResolutionNote && (
                        <div className="p-2 rounded bg-indigo-950/30 border border-indigo-500/30 text-[11px] font-sans text-indigo-200/90 space-y-0.5">
                          <div className="flex flex-wrap items-center gap-1.5 text-[9.5px] font-mono text-indigo-300 uppercase font-bold">
                            <span>🔄 Preserved Prior Outcome</span>
                            {issue.priorOutcomeType && FOLLOW_UP_OUTCOMES_CONFIG[issue.priorOutcomeType] && (
                              <span className={`px-1 py-0.2 rounded text-[8.5px] border ${FOLLOW_UP_OUTCOMES_CONFIG[issue.priorOutcomeType].badgeClass}`}>
                                {FOLLOW_UP_OUTCOMES_CONFIG[issue.priorOutcomeType].shortLabel}
                              </span>
                            )}
                            {(issue.priorEvidenceRunId || issue.priorEvidenceAsOfDate) && (
                              <span className="text-indigo-300/80 font-mono text-[9px]">
                                • Prior Evidence: {issue.priorEvidenceRunId ? `Run #${issue.priorEvidenceRunId}` : "Run not recorded"}
                                {issue.priorEvidenceAsOfDate ? ` (Cutoff ${issue.priorEvidenceAsOfDate})` : " (Cutoff not recorded)"}
                              </span>
                            )}
                            {issue.priorResolvedAt && (
                              <span className="text-text-muted lowercase font-normal">
                                (recorded {formatSessionRelativeTime(issue.priorResolvedAt)})
                              </span>
                            )}
                          </div>
                          <p className="leading-relaxed line-clamp-2 text-[11px] text-text-secondary">
                            {issue.priorResolutionNote}
                          </p>
                          {issue.reopenReason && (
                            <p className="text-[10.5px] text-indigo-300 font-sans pt-1 border-t border-indigo-500/30">
                              <span className="font-bold font-mono text-[9px] uppercase">Renewal Rationale:</span> {issue.reopenReason}
                            </p>
                          )}
                        </div>
                      )}

                      {/* Question / Concern */}
                      <p className="text-[11.5px] text-text-secondary font-sans leading-relaxed">
                        <strong className="text-text-primary font-mono text-[10px] uppercase">Diligence Question:</strong>{" "}
                        {issue.questionOrReason}
                      </p>

                      {/* Next Action */}
                      <p className="text-[11.5px] text-text-muted font-sans leading-relaxed">
                        <strong className="text-accent font-mono text-[10px] uppercase">Planned Action:</strong>{" "}
                        {issue.nextAction}
                      </p>

                      {/* Resolution note if resolved or dismissed */}
                      {issue.resolutionNote && (
                        <div className="p-2 rounded bg-surface-raised/80 border border-emerald-500/30 text-[11px] font-sans text-text-primary space-y-1">
                          <div className="flex flex-wrap items-center justify-between gap-1.5">
                            <strong className="text-emerald-400 font-mono text-[10px] uppercase block">
                              {issue.status === "RESOLVED" ? "Empirical Resolution Outcome:" : "Dismissal Rationale:"}
                            </strong>
                            {issue.outcomeType && FOLLOW_UP_OUTCOMES_CONFIG[issue.outcomeType] && (
                              <span className={`text-[9px] px-1.5 py-0.2 rounded border font-semibold font-mono ${FOLLOW_UP_OUTCOMES_CONFIG[issue.outcomeType].badgeClass}`}>
                                {FOLLOW_UP_OUTCOMES_CONFIG[issue.outcomeType].label}
                              </span>
                            )}
                          </div>
                          <p className="leading-relaxed">{issue.resolutionNote}</p>
                          {issue.closureLimitationRationale && (
                            <div className="pt-1 border-t border-border/40 text-[10.5px] text-amber-300 font-sans">
                              <span className="font-mono text-[9px] uppercase font-bold text-amber-400">Accepted Limitation Rationale: </span>
                              {issue.closureLimitationRationale}
                            </div>
                          )}
                          {issue.residualUncertainty && (
                            <div className="pt-1 border-t border-border/40 text-[10.5px] text-amber-300 font-sans">
                              <span className="font-mono text-[9px] uppercase font-bold text-amber-400">Residual Uncertainty: </span>
                              {issue.residualUncertainty}
                            </div>
                          )}
                        </div>
                      )}

                      {/* Phase F28: Preserved Reassessment Disposition & History */}
                      {issue.reassessmentDisposition && (
                        <div className="p-2 rounded bg-purple-950/20 border border-purple-500/30 text-[11px] font-sans text-purple-200/90 space-y-0.5">
                          <div className="flex flex-wrap items-center gap-1.5 text-[9.5px] font-mono text-purple-300 uppercase font-bold">
                            <span>⚡ Reassessment Recorded</span>
                            <span className="px-1 py-0.2 rounded text-[8.5px] border border-purple-500/40 bg-purple-900/30">
                              {issue.reassessmentDisposition}
                            </span>
                            {issue.reassessedAt && (
                              <span className="text-text-muted lowercase font-normal">
                                ({formatSessionRelativeTime(issue.reassessedAt)})
                              </span>
                            )}
                          </div>
                          {issue.reassessmentRationale && (
                            <p className="leading-relaxed line-clamp-2 text-[11px] text-text-secondary">
                              {issue.reassessmentRationale}
                            </p>
                          )}
                        </div>
                      )}

                      {/* Phase F29: Investigation Prerequisites & Blocker Continuity */}
                      {(() => {
                        const depEval = evaluateIssueDependencies(issue);
                        if (depEval.totalDependencies === 0 && issue.status !== "WAITING") return null;
                        return (
                          <div className="p-2 rounded bg-surface-raised/70 border border-border/80 text-[10.5px] font-mono space-y-1">
                            <div className="flex flex-wrap items-center justify-between gap-1.5">
                              <div className="flex items-center gap-1.5">
                                <span>⏳</span>
                                <span className="font-bold uppercase text-[9.5px] text-text-primary">
                                  Prerequisites &amp; Blockers:
                                </span>
                                <span className={`text-[8.5px] px-1.5 py-0.2 rounded border font-semibold ${depEval.badgeClass}`}>
                                  {depEval.stateLabel}
                                </span>
                              </div>
                              <span className="text-[9.5px] text-text-muted">
                                {depEval.awaitingCount} Awaiting • {depEval.addressedCount} Addressed
                              </span>
                            </div>
                            <p className="text-[10.5px] font-sans text-text-secondary leading-snug">
                              {depEval.description}
                            </p>
                          </div>
                        );
                      })()}
                    </div>

                    {/* Actions */}
                    <div className="flex flex-row md:flex-col items-end gap-1.5 shrink-0 self-start">
                      <button
                        type="button"
                        onClick={() =>
                          onNavigateToBrief(
                            issue.schemeId,
                            issue.schemeOptionId,
                            issue.briefSection || "B",
                            issue
                          )
                        }
                        className="btn btn-secondary btn-xs text-[10.5px] font-mono text-accent border-accent/40 hover:bg-accent/10 whitespace-nowrap"
                        title="Inspect current research evidence in Brief (Section navigates to current data; historical state is not reconstructed)"
                      >
                        {issue.priorResolutionNote || issue.reopenedAt
                          ? `Resume Reopened Inquiry (Sec ${issue.briefSection || "B"}) ↗`
                          : isEvidenceDriftIssue
                          ? `Resume Reassessment (Sec ${issue.briefSection || "B"}) ↗`
                          : `Resume In Brief (Sec ${issue.briefSection || "B"}) ↗`}
                      </button>

                      {/* Phase F28: Contextual Reassessment or Timeline Trigger */}
                      {(prov.status === "HISTORICAL_EVIDENCE_CHANGED" || prov.status === "HISTORICAL_RUN_SUPERSEDED" || issue.priorResolutionNote || issue.reopenedAt || issue.reassessmentDisposition) && (
                        <button
                          type="button"
                          onClick={() =>
                            onOpenIssueModal(issue, null, {
                              initialReassessmentMode: Boolean(prov.status === "HISTORICAL_EVIDENCE_CHANGED" || prov.status === "HISTORICAL_RUN_SUPERSEDED" || issue.reopenedAt),
                              initialShowTimeline: true,
                            })
                          }
                          className="btn btn-secondary btn-xs text-[10.5px] font-mono text-purple-300 border-purple-500/40 hover:bg-purple-950/30 whitespace-nowrap cursor-pointer"
                          title="Review evidence timeline and record evidence-aware reassessment disposition"
                        >
                          {prov.status === "HISTORICAL_EVIDENCE_CHANGED" || prov.status === "HISTORICAL_RUN_SUPERSEDED" ? "Begin Reassessment ⚡" : "Review History 📜"}
                        </button>
                      )}

                      {/* Phase F29: Contextual Dependency Action */}
                      {evaluateIssueDependencies(issue).activeBlockers.length > 0 && (
                        <button
                          type="button"
                          onClick={() =>
                            onOpenIssueModal(issue, null, {
                              initialDependencyId: evaluateIssueDependencies(issue).activeBlockers[0]?.id || null,
                            })
                          }
                          className="btn btn-secondary btn-xs text-[10.5px] font-mono text-emerald-400 border-emerald-500/40 hover:bg-emerald-950/30 whitespace-nowrap cursor-pointer"
                          title="Record received disclosure or evidence to address blocker"
                        >
                          Address Blocker ✓
                        </button>
                      )}

                      <button
                        type="button"
                        onClick={() => onOpenIssueModal(issue)}
                        className="btn btn-ghost btn-xs text-[10.5px] font-mono text-text-secondary hover:text-text-primary border border-border/80 whitespace-nowrap"
                      >
                        {issue.status === "RESOLVED" || issue.status === "DISMISSED" ? "View Outcome / Reopen ✎" : "Update / Resolve ✎"}
                      </button>

                      <button
                        type="button"
                        onClick={() =>
                          onNavigateToBrief(
                            issue.schemeId,
                            issue.schemeOptionId,
                            "coverage",
                            issue
                          )
                        }
                        className="btn btn-ghost btn-xs text-[10.5px] font-mono text-text-secondary hover:text-accent border border-border/80 whitespace-nowrap"
                        title="Triage research coverage gaps for this fund option"
                      >
                        Coverage 🔍
                      </button>

                      <button
                        type="button"
                        onClick={() => {
                          if (!selectedIssueIds.includes(issue.id)) {
                            setSelectedIssueIds((prev) => [...prev, issue.id]);
                          }
                          setIsDossierModalOpen(true);
                        }}
                        className="btn btn-ghost btn-xs text-[10.5px] font-mono text-text-muted hover:text-accent border border-border/80 whitespace-nowrap"
                        title="Assemble research dossier for this finding"
                      >
                        Dossier 📑
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

          {/* Epistemic Boundary Notice & Investigation Triage Policy */}
          <div className="pt-2.5 border-t border-border/40 text-[10.5px] font-mono text-text-muted space-y-1">
            <p className="text-text-secondary leading-relaxed">
              *Prior findings represent historical investor-authored research records. The Investigation Action Queue surfaces investigations requiring operational attention based strictly on recorded workflow states, target dates, reopened outcomes, and explicit evidence drift. Workflow prioritization does NOT represent financial risk scoring, fund suitability, or expected return.
            </p>
            <div className="flex flex-wrap items-center justify-between gap-2 text-text-muted text-[9.5px]">
              <span>Triage operates strictly on persisted dates, classifications, and recorded evidence run identifiers.</span>
              <span>Zero financial risk scoring • Deterministic priority tiers • No automated status mutation</span>
            </div>
          </div>
        </div>
      )}

      {/* Phase F21 & F22: Portable Research Dossier Assembly, Continuity & Export Modal */}
      <ResearchDossierModal
        isOpen={isDossierModalOpen}
        onClose={() => setIsDossierModalOpen(false)}
        selectedIssues={selectedIssuesForDossier}
        savedItems={savedItems}
        onRemoveIssueFromSelection={handleRemoveFromDossier}
        onClearSelection={handleClearSelection}
        onOpenIssueModal={onOpenIssueModal}
        onNavigateToBrief={onNavigateToBrief}
      />
    </div>
  );
}
