"use client";

import React, { useState, useMemo } from "react";
import { WatchlistResponse } from "@/lib/api/investor";
import {
  ResearchFollowUpIssue,
  FOLLOW_UP_STATUS_CONFIG,
  FOLLOW_UP_OUTCOMES_CONFIG,
} from "@/lib/utils/researchFollowUp";
import { PortfolioSummaryDto } from "@/lib/api/portfolio";
import {
  ReviewItemKey,
  loadAllReviewChecklists,
  InvestigationReviewChecklistRecord,
} from "@/lib/utils/researchReviewChecklist";
import {
  loadEvidenceSnapshots,
  computeEvidenceChanges,
  EvidenceChangeSummary,
} from "@/lib/utils/evidenceChange";
import { getWatchlistItemKey } from "@/lib/hooks/useWatchlist";
import {
  evaluateCrossFundCoverage,
  filterCrossFundCoverageItems,
  CrossFundCoverageFilter,
  FundCoverageOverviewItem,
  RESEARCH_COVERAGE_STATES,
} from "@/lib/utils/researchCoverage";
import {
  InvestigationHandoffContext,
  createInvestigationHandoff,
  formatSessionRelativeTime,
  getBriefSectionTitle,
} from "@/lib/utils/researchSession";

export interface ResearchCoverageOverviewProps {
  items: WatchlistResponse[];
  issues: ResearchFollowUpIssue[];
  userId?: string | null;
  portfolioSummary?: PortfolioSummaryDto | null;
  portfolioLoading?: boolean;
  externalSearchQuery?: string;
  activeHandoff?: InvestigationHandoffContext | null;
  onNavigateToBrief: (
    schemeId: number,
    schemeOptionId?: number | null,
    section?: "coverage" | "checklist" | "A" | "B" | "C" | "D" | "E" | "F" | "G" | "H",
    issue?: ResearchFollowUpIssue,
    handoff?: InvestigationHandoffContext | null
  ) => void;
  onOpenIssueModal: (issue: ResearchFollowUpIssue, handoff?: InvestigationHandoffContext | null) => void;
  onCreateIssue?: (
    fund: WatchlistResponse,
    briefSection?: "A" | "B" | "C" | "D" | "E" | "F" | "G",
    checklistKey?: ReviewItemKey,
    title?: string,
    questionOrReason?: string,
    handoff?: InvestigationHandoffContext | null
  ) => void;
}

export function ResearchCoverageOverview({
  items,
  issues,
  userId,
  portfolioSummary,
  portfolioLoading,
  externalSearchQuery,
  activeHandoff,
  onNavigateToBrief,
  onOpenIssueModal,
  onCreateIssue,
}: ResearchCoverageOverviewProps) {
  const [activeFilter, setActiveFilter] = useState<CrossFundCoverageFilter>("ALL");
  const [isExpanded, setIsExpanded] = useState<boolean>(true);

  // Load review checklists for all saved funds
  const checklistsMap = useMemo<Record<string, InvestigationReviewChecklistRecord | null>>(() => {
    return loadAllReviewChecklists(userId);
  }, [userId]);

  // Load evidence snapshots for all saved funds
  const snapshotsMap = useMemo(() => {
    return loadEvidenceSnapshots();
  }, [items]);

  // Pre-calculate evidence change summaries for fast evaluation
  const evidenceSummariesMap = useMemo<Record<string, EvidenceChangeSummary | null>>(() => {
    const map: Record<string, EvidenceChangeSummary | null> = {};
    items.forEach((item) => {
      const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
      const snap = snapshotsMap[key] || null;
      map[key] = computeEvidenceChanges(item, null, snap);
    });
    return map;
  }, [items, snapshotsMap]);

  // Evaluate deterministic cross-fund coverage and workflow prioritization
  const { items: evaluatedItems, summary } = useMemo(() => {
    return evaluateCrossFundCoverage(
      items,
      issues,
      checklistsMap,
      evidenceSummariesMap,
      portfolioSummary,
      portfolioLoading
    );
  }, [items, issues, checklistsMap, evidenceSummariesMap, portfolioSummary, portfolioLoading]);

  // Filter items based on active tab and optional search query
  const filteredItems = useMemo<FundCoverageOverviewItem[]>(() => {
    return filterCrossFundCoverageItems(evaluatedItems, activeFilter, externalSearchQuery);
  }, [evaluatedItems, activeFilter, externalSearchQuery]);

  if (!items || items.length === 0) {
    return null;
  }

  return (
    <div
      role="region"
      aria-label="Cross-Fund Research Coverage & Investigation Prioritization"
      className="panel mb-6 p-4 bg-surface border border-border/80 rounded-xl space-y-4 font-mono shadow-xs"
    >
      {/* Header and Epistemic Disclosure */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 pb-3 border-b border-border/60">
        <div className="space-y-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="flex h-5 w-5 items-center justify-center rounded bg-accent/20 text-accent text-xs font-bold shrink-0">
              🔍
            </span>
            <h3 className="text-xs font-bold uppercase tracking-wider text-text-cream-bright">
              Cross-Fund Research Coverage &amp; Prioritized Investigation Entry
            </h3>
            <span
              className={`status-badge text-[10px] font-mono font-bold ${
                summary.fundsWithActionableGaps === 0
                  ? "bg-emerald-500/15 border-emerald-500/30 text-emerald-300"
                  : "bg-amber-500/15 border-amber-500/30 text-amber-300"
              }`}
            >
              {summary.fundsWithActionableGaps} of {summary.totalFundsAssessed} Funds with Actionable Gaps
            </span>
            {activeFilter !== "ALL" && (
              <span className="status-badge state-candidate text-[9.5px] text-amber-300">
                FILTER: {activeFilter.replace(/_/g, " ")}
              </span>
            )}
          </div>
          <p className="text-[11.5px] text-text-muted font-sans leading-relaxed">
            Consolidated diligence coverage across your saved mutual funds. Prioritization reflects investigative due-diligence order (overdue work, active inquiries, data gaps), never fund quality or investment suitability.
          </p>
        </div>

        <div className="flex items-center gap-2 shrink-0 self-start md:self-center">
          {activeFilter !== "ALL" && (
            <button
              type="button"
              onClick={() => setActiveFilter("ALL")}
              className="btn btn-ghost btn-xs text-[11px] text-accent hover:underline shrink-0"
              title="Clear coverage filter"
            >
              Show All ({summary.totalFundsAssessed}) ✕
            </button>
          )}
          <button
            type="button"
            onClick={() => setIsExpanded((v) => !v)}
            className="btn btn-ghost btn-xs text-[11px] text-text-secondary px-2 py-0.5 border border-border/70 rounded font-mono"
          >
            {isExpanded ? "Collapse ▴" : "Expand ▾"}
          </button>
        </div>
      </div>

      {isExpanded && (
        <>
          {/* Epistemic Boundary Notice */}
          <div className="p-2.5 rounded bg-surface-raised/40 border border-border/60 text-[11px] font-mono text-text-muted flex items-start gap-2">
            <span className="text-accent shrink-0 text-xs mt-0.5">ℹ️</span>
            <div className="space-y-0.5 font-sans leading-relaxed">
              <p>
                <strong className="font-mono text-text-primary text-[10.5px]">EPISTEMIC BOUNDARY:</strong> Coverage triage measures whether key due-diligence questions have recorded analysis or supporting evidence.
              </p>
              <p className="text-[10.5px] text-text-tertiary">
                • <strong>Prioritization Order:</strong> Indicates which diligence tasks need attention next (e.g. overdue deadlines, active inquiries, missing evidence baselines). It does <em>not</em> rank fund performance, quality, or risk.<br />
                • <strong>Documentation Gap:</strong> Missing notes or unreviewed checklist items reflect unrecorded inquiry, not a negative fund finding.<br />
                • <strong>No Identified Gap:</strong> The dimension is documented; does not certify numerical correctness, fund safety, or algorithmic validation.
              </p>
            </div>
          </div>

          {/* Actionable Coverage Metric Filter Tabs */}
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-6 gap-2 text-xs">
            {/* 1. All Saved Funds */}
            <button
              type="button"
              onClick={() => setActiveFilter("ALL")}
              className={`p-2.5 rounded-lg border text-left transition-all flex flex-col justify-between ${
                activeFilter === "ALL"
                  ? "bg-accent/20 border-accent/80 ring-1 ring-accent/50 shadow-xs"
                  : "bg-surface-raised/40 hover:bg-surface-raised/70 border-border/80"
              }`}
            >
              <span className="text-[10px] text-text-muted uppercase font-bold">All Saved</span>
              <div className="text-base font-bold text-text-primary mt-1">
                {summary.totalFundsAssessed}
              </div>
              <span className="text-[9.5px] text-text-tertiary block mt-0.5">Total options</span>
            </button>

            {/* 2. Actionable Gaps */}
            <button
              type="button"
              onClick={() => setActiveFilter("ACTIONABLE_GAPS")}
              className={`p-2.5 rounded-lg border text-left transition-all flex flex-col justify-between ${
                activeFilter === "ACTIONABLE_GAPS"
                  ? "bg-amber-500/20 border-amber-400/80 ring-1 ring-amber-400/50 shadow-xs"
                  : summary.fundsWithActionableGaps > 0
                  ? "bg-surface-raised/40 hover:bg-surface-raised/70 border-border/80 hover:border-amber-400/40"
                  : "bg-surface-raised/20 border-border/40 opacity-70"
              }`}
            >
              <div className="flex items-center justify-between">
                <span className="text-[10px] text-text-muted uppercase font-bold">Actionable Gaps</span>
                <span className="text-[11px]">{summary.fundsWithActionableGaps > 0 ? "⚠️" : "✓"}</span>
              </div>
              <div className="text-base font-bold text-amber-400 mt-1">
                {summary.fundsWithActionableGaps}
              </div>
              <span className="text-[9.5px] text-text-tertiary block mt-0.5">
                {summary.totalActionableGapsAcrossFunds} total gaps
              </span>
            </button>

            {/* 3. Open Investigations */}
            <button
              type="button"
              onClick={() => setActiveFilter("OPEN_INVESTIGATIONS")}
              className={`p-2.5 rounded-lg border text-left transition-all flex flex-col justify-between ${
                activeFilter === "OPEN_INVESTIGATIONS"
                  ? "bg-purple-500/20 border-purple-400/80 ring-1 ring-purple-400/50 shadow-xs"
                  : summary.fundsWithOpenInvestigations > 0
                  ? "bg-surface-raised/40 hover:bg-surface-raised/70 border-border/80 hover:border-purple-400/40"
                  : "bg-surface-raised/20 border-border/40 opacity-70"
              }`}
            >
              <div className="flex items-center justify-between">
                <span className="text-[10px] text-text-muted uppercase font-bold">Open Inquiries</span>
                <span className="text-[11px]">{summary.fundsWithOpenInvestigations > 0 ? "📌" : "—"}</span>
              </div>
              <div className="text-base font-bold text-purple-300 mt-1">
                {summary.fundsWithOpenInvestigations}
              </div>
              <span className="text-[9.5px] text-text-tertiary block mt-0.5">
                {summary.totalOpenInvestigationsAcrossFunds} active issues
              </span>
            </button>

            {/* 4. Missing Evidence / Drift */}
            <button
              type="button"
              onClick={() => setActiveFilter("DATA_OR_PROVENANCE_LIMITS")}
              className={`p-2.5 rounded-lg border text-left transition-all flex flex-col justify-between ${
                activeFilter === "DATA_OR_PROVENANCE_LIMITS"
                  ? "bg-sky-500/20 border-sky-400/80 ring-1 ring-sky-400/50 shadow-xs"
                  : summary.fundsWithMissingEvidence > 0
                  ? "bg-surface-raised/40 hover:bg-surface-raised/70 border-border/80 hover:border-sky-400/40"
                  : "bg-surface-raised/20 border-border/40 opacity-70"
              }`}
            >
              <div className="flex items-center justify-between">
                <span className="text-[10px] text-text-muted uppercase font-bold">Data / Drift</span>
                <span className="text-[11px]">{summary.fundsWithMissingEvidence > 0 ? "🔍" : "✓"}</span>
              </div>
              <div className="text-base font-bold text-sky-400 mt-1">
                {summary.fundsWithMissingEvidence}
              </div>
              <span className="text-[9.5px] text-text-tertiary block mt-0.5">Unavailable or drifted</span>
            </button>

            {/* 5. Residual Uncertainty */}
            <button
              type="button"
              onClick={() => setActiveFilter("RECORDED_UNCERTAINTY")}
              className={`p-2.5 rounded-lg border text-left transition-all flex flex-col justify-between ${
                activeFilter === "RECORDED_UNCERTAINTY"
                  ? "bg-yellow-500/20 border-yellow-400/80 ring-1 ring-yellow-400/50 shadow-xs"
                  : summary.fundsWithResidualUncertainty > 0
                  ? "bg-surface-raised/40 hover:bg-surface-raised/70 border-border/80 hover:border-yellow-400/40"
                  : "bg-surface-raised/20 border-border/40 opacity-70"
              }`}
            >
              <div className="flex items-center justify-between">
                <span className="text-[10px] text-text-muted uppercase font-bold">Uncertainty</span>
                <span className="text-[11px]">{summary.fundsWithResidualUncertainty > 0 ? "⚖️" : "—"}</span>
              </div>
              <div className="text-base font-bold text-yellow-300 mt-1">
                {summary.fundsWithResidualUncertainty}
              </div>
              <span className="text-[9.5px] text-text-tertiary block mt-0.5">Explicit residual notes</span>
            </button>

            {/* 6. Documented / No Identified Gaps */}
            <button
              type="button"
              onClick={() => setActiveFilter("NO_IDENTIFIED_GAPS")}
              className={`p-2.5 rounded-lg border text-left transition-all flex flex-col justify-between ${
                activeFilter === "NO_IDENTIFIED_GAPS"
                  ? "bg-emerald-500/20 border-emerald-400/80 ring-1 ring-emerald-400/50 shadow-xs"
                  : summary.fundsWithNoIdentifiedGaps > 0
                  ? "bg-surface-raised/40 hover:bg-surface-raised/70 border-border/80 hover:border-emerald-400/40"
                  : "bg-surface-raised/20 border-border/40 opacity-70"
              }`}
              title="Filter for funds with all 8 diligence dimensions documented (does not imply fund safety)"
            >
              <div className="flex items-center justify-between">
                <span className="text-[10px] text-text-muted uppercase font-bold">Documented</span>
                <span className="text-[11px]">✓</span>
              </div>
              <div className="text-base font-bold text-emerald-400 mt-1">
                {summary.fundsWithNoIdentifiedGaps}
              </div>
              <span className="text-[9.5px] text-text-tertiary block mt-0.5">8/8 documented</span>
            </button>
          </div>

          {/* Prioritized Fund Overview Cards */}
          <div className="space-y-3 pt-2 border-t border-border/50">
            {filteredItems.length === 0 ? (
              <div className="p-6 text-center text-text-muted text-xs bg-surface-raised/30 rounded-lg border border-border/60 space-y-2">
                <p>No saved fund options match the selected coverage filter ({activeFilter.replace(/_/g, " ")}).</p>
                <button
                  type="button"
                  onClick={() => setActiveFilter("ALL")}
                  className="btn btn-secondary btn-xs text-accent font-mono"
                >
                  Reset to All Saved Funds ↺
                </button>
              </div>
            ) : (
              filteredItems.map((item) => {
                const isActiveHandoff = Boolean(
                  activeHandoff &&
                  activeHandoff.schemeId === item.schemeId &&
                  (activeHandoff.schemeOptionId === undefined ||
                    activeHandoff.schemeOptionId === null ||
                    item.schemeOptionId === undefined ||
                    item.schemeOptionId === null ||
                    activeHandoff.schemeOptionId === item.schemeOptionId)
                );

                return (
                  <div
                    key={item.key}
                    className={`p-3.5 rounded-xl border transition-all ${
                      isActiveHandoff
                        ? "bg-accent/5 border-accent/70 shadow-sm ring-1 ring-accent/30"
                        : item.priorityTier <= 2
                        ? "bg-surface-raised/80 border-rose-500/40 hover:border-rose-500/60"
                        : item.priorityTier === 3
                        ? "bg-surface-raised/80 border-amber-500/40 hover:border-amber-500/60"
                        : item.priorityTier === 4
                        ? "bg-surface-raised/70 border-purple-500/30 hover:border-purple-500/50"
                        : item.hasActionableGaps
                        ? "bg-surface-raised/50 border-border/80 hover:border-border"
                        : "bg-surface-raised/30 border-border/50 hover:border-emerald-500/40"
                    } space-y-3`}
                  >
                    {/* Active Handoff Continuity Callout */}
                    {isActiveHandoff && activeHandoff && (
                      <div className="p-2 rounded-lg bg-accent/15 border border-accent/40 flex flex-col sm:flex-row sm:items-center justify-between gap-2 text-xs font-mono">
                        <div className="flex items-center gap-2 min-w-0">
                          <span className="flex h-2 w-2 rounded-full bg-accent animate-pulse shrink-0" />
                          <span className="text-[10.5px] text-accent font-bold uppercase truncate">
                            Active Investigation Handoff: {activeHandoff.dimensionTitle || `Sec ${activeHandoff.briefSection || "Coverage"}`}
                          </span>
                        </div>
                        <button
                          type="button"
                          onClick={() => {
                            if (activeHandoff.followUpIssueId) {
                              const matchIss = item.activeIssues.find((i) => i.id === activeHandoff.followUpIssueId);
                              if (matchIss) {
                                onOpenIssueModal(matchIss, activeHandoff);
                                return;
                              }
                            }
                            onNavigateToBrief(
                              item.schemeId,
                              item.schemeOptionId,
                              activeHandoff.briefSection || "coverage",
                              undefined,
                              activeHandoff
                            );
                          }}
                          className="btn btn-primary btn-xs text-[10px] font-mono shrink-0 py-0.5 px-2 self-end sm:self-center"
                        >
                          Resume Active Context ↗
                        </button>
                      </div>
                    )}

                    {/* Card Top Row: Identification & Badges */}
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2.5">
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className={`text-[10px] font-mono px-2 py-0.5 rounded border font-semibold ${item.priorityBadgeClass}`}>
                            {item.priorityTierLabel}
                          </span>
                          <span
                            className={`text-[10px] font-mono px-2 py-0.5 rounded border font-semibold ${
                              item.hasActionableGaps
                                ? "bg-amber-500/15 text-amber-300 border-amber-500/40"
                                : "bg-emerald-500/15 text-emerald-300 border-emerald-500/40"
                            }`}
                          >
                            {item.documentedCount}/{item.totalDimensions} Documented
                            {item.hasActionableGaps ? ` (${item.actionableGapCount} gaps)` : " ✓"}
                          </span>
                        </div>

                        <h4 className="text-[13px] font-bold text-text-primary mt-1 font-sans truncate">
                          {item.schemeName}
                        </h4>

                        <div className="flex flex-wrap items-center gap-x-2.5 gap-y-1 text-[11px] text-text-muted font-mono mt-0.5">
                          <span>
                            Option #{item.schemeOptionId || item.schemeId}:{" "}
                            <strong className="text-text-primary">{item.planType} • {item.optionType}</strong>
                          </span>
                          {item.schemeCode && (
                            <>
                              <span>•</span>
                              <span>Code: <strong className="text-text-primary">{item.schemeCode}</strong></span>
                            </>
                          )}
                          {item.amfiCode && (
                            <>
                              <span>•</span>
                              <span>AMFI: <strong className="text-text-primary">{item.amfiCode}</strong></span>
                            </>
                          )}
                          {item.isin && (
                            <>
                              <span>•</span>
                              <span>ISIN: <strong className="text-text-primary">{item.isin}</strong></span>
                            </>
                          )}
                        </div>
                      </div>

                      {/* Top Quick Actions */}
                      <div className="shrink-0 flex items-center gap-2 self-end sm:self-center">
                        <button
                          type="button"
                          onClick={() => {
                            const handoff = createInvestigationHandoff({
                              fund: {
                                schemeId: item.schemeId,
                                schemeOptionId: item.schemeOptionId,
                                schemeName: item.schemeName,
                                schemeCode: item.schemeCode,
                                planType: item.planType,
                                optionType: item.optionType,
                                amfiCode: item.amfiCode,
                                isin: item.isin,
                              },
                              briefSection: "coverage",
                              originatingAction: "COVERAGE_OVERVIEW_TRIAGE",
                            });
                            onNavigateToBrief(item.schemeId, item.schemeOptionId, "coverage", undefined, handoff);
                          }}
                          className="btn btn-secondary btn-xs text-accent border-accent/40 hover:bg-accent/10 font-mono text-[11px]"
                          title="Open detailed Research Coverage & Gaps analysis for this exact fund option"
                        >
                          Coverage &amp; Gaps 🔍 ↗
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            const handoff = createInvestigationHandoff({
                              fund: {
                                schemeId: item.schemeId,
                                schemeOptionId: item.schemeOptionId,
                                schemeName: item.schemeName,
                                schemeCode: item.schemeCode,
                                planType: item.planType,
                                optionType: item.optionType,
                                amfiCode: item.amfiCode,
                                isin: item.isin,
                              },
                              briefSection: "A",
                              originatingAction: "COVERAGE_OVERVIEW_TRIAGE",
                            });
                            onNavigateToBrief(item.schemeId, item.schemeOptionId, "A", undefined, handoff);
                          }}
                          className="btn btn-ghost btn-xs text-text-secondary hover:text-text-primary border border-border/80 font-mono text-[11px]"
                          title="Open full Fund Research Brief starting at Section A"
                        >
                          Brief ↗
                        </button>
                      </div>
                    </div>

                    {/* 8 Diligence Dimension Mini Status Bar */}
                    <div className="p-2 rounded bg-surface/70 border border-border/50">
                      <div className="flex flex-wrap items-center justify-between gap-1.5 text-[10px] font-mono">
                        <span className="text-text-tertiary uppercase text-[9.5px]">8 Diligence Dimensions:</span>
                        <div className="flex flex-wrap items-center gap-1.5">
                          {item.report.dimensions.map((dim) => {
                            const stateMeta = RESEARCH_COVERAGE_STATES[dim.state];
                            return (
                              <button
                                key={dim.key}
                                type="button"
                                onClick={() => {
                                  const handoff = createInvestigationHandoff({
                                    fund: {
                                      schemeId: item.schemeId,
                                      schemeOptionId: item.schemeOptionId,
                                      schemeName: item.schemeName,
                                      schemeCode: item.schemeCode,
                                      planType: item.planType,
                                      optionType: item.optionType,
                                      amfiCode: item.amfiCode,
                                      isin: item.isin,
                                    },
                                    briefSection: dim.briefSection,
                                    dimensionKey: dim.key,
                                    dimensionTitle: dim.title,
                                    coverageState: dim.state,
                                    issue: dim.activeIssue || null,
                                    originatingAction: "COVERAGE_DIMENSION_DEEP_LINK",
                                  });
                                  onNavigateToBrief(item.schemeId, item.schemeOptionId, dim.briefSection, dim.activeIssue || undefined, handoff);
                                }}
                                className={`px-1.5 py-0.5 rounded border text-[9.5px] font-mono transition-opacity hover:opacity-80 flex items-center gap-1 ${stateMeta.badgeClass}`}
                                title={`Sec ${dim.briefSection} (${dim.title}): ${dim.explanation} — Click to inspect in Brief`}
                              >
                                <span>Sec {dim.briefSection}</span>
                                <span className="font-bold">
                                  {dim.state === "DOCUMENTED"
                                    ? "✓"
                                    : dim.state === "INVESTIGATION_PENDING"
                                    ? "⏳"
                                    : dim.state === "EVIDENCE_UNAVAILABLE"
                                    ? "✕"
                                    : dim.state === "UNCERTAIN"
                                    ? "?"
                                    : "○"}
                                </span>
                              </button>
                            );
                          })}
                        </div>
                      </div>
                    </div>

                    {/* Explainability & Suggested Next Diligence Step Callout */}
                    <div className="p-2.5 rounded-lg bg-surface/90 border border-border/70 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs">
                      <div className="space-y-1 min-w-0 flex-1 font-sans">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="mono-meta text-accent font-bold uppercase text-[9.5px]">
                            WORKFLOW ATTENTION CONTEXT
                          </span>
                          {item.allWorkflowReasons.length > 1 && (
                            <span className="text-[9.5px] font-mono text-text-tertiary">
                              ({item.allWorkflowReasons.length} attention factors)
                            </span>
                          )}
                        </div>
                        <p className="text-[12px] text-text-primary leading-relaxed">
                          <strong>Why attention needed: </strong>
                          {item.primaryWorkflowReason}
                        </p>
                        <p className="text-[11.5px] text-text-secondary leading-relaxed font-sans">
                          <strong className="font-mono text-[10px] text-accent uppercase">Suggested Next Diligence Step: </strong>
                          {item.suggestedNextStep}
                        </p>
                      </div>

                      {/* Contextual Action Button */}
                      <div className="shrink-0 flex items-center gap-2 self-end sm:self-center">
                        {item.nextActionType === "VIEW_ISSUE" && item.activeIssues[0] && (
                          <button
                            type="button"
                            onClick={() => {
                              const activeIss = item.activeIssues[0];
                              const handoff = createInvestigationHandoff({
                                fund: {
                                  schemeId: item.schemeId,
                                  schemeOptionId: item.schemeOptionId,
                                  schemeName: item.schemeName,
                                  schemeCode: item.schemeCode,
                                  planType: item.planType,
                                  optionType: item.optionType,
                                  amfiCode: item.amfiCode,
                                  isin: item.isin,
                                },
                                briefSection: (activeIss.briefSection as any) || "coverage",
                                dimensionKey: activeIss.checklistKey,
                                dimensionTitle: activeIss.title,
                                issue: activeIss,
                                originatingAction: "INVESTIGATION_QUEUE_RESUME",
                              });
                              onOpenIssueModal(activeIss, handoff);
                            }}
                            className="btn btn-secondary btn-xs text-amber-300 border-amber-500/40 hover:bg-amber-500/10 font-mono text-[11px]"
                            title="Open the active investigation modal for this inquiry"
                          >
                            Review Issue #{item.activeIssues[0].id.slice(-6)} ↗
                          </button>
                        )}

                        {item.nextActionType === "REASSESS_EVIDENCE" && (
                          <button
                            type="button"
                            onClick={() => {
                              const handoff = createInvestigationHandoff({
                                fund: {
                                  schemeId: item.schemeId,
                                  schemeOptionId: item.schemeOptionId,
                                  schemeName: item.schemeName,
                                  schemeCode: item.schemeCode,
                                  planType: item.planType,
                                  optionType: item.optionType,
                                  amfiCode: item.amfiCode,
                                  isin: item.isin,
                                },
                                briefSection: "F",
                                originatingAction: "EVIDENCE_HISTORY_REVIEW",
                              });
                              onNavigateToBrief(item.schemeId, item.schemeOptionId, "F", undefined, handoff);
                            }}
                            className="btn btn-secondary btn-xs text-amber-300 border-amber-500/40 hover:bg-amber-500/10 font-mono text-[11px]"
                            title="Audit historical evidence changes in Section F"
                          >
                            Audit Sec F History ↗
                          </button>
                        )}

                        {item.nextActionType === "CREATE_ISSUE" && onCreateIssue && (
                          <button
                            type="button"
                            onClick={() => {
                              const handoff = createInvestigationHandoff({
                                fund: {
                                  schemeId: item.schemeId,
                                  schemeOptionId: item.schemeOptionId,
                                  schemeName: item.schemeName,
                                  schemeCode: item.schemeCode,
                                  planType: item.planType,
                                  optionType: item.optionType,
                                  amfiCode: item.amfiCode,
                                  isin: item.isin,
                                },
                                briefSection: item.mostUrgentGap?.briefSection || "A",
                                dimensionKey: item.mostUrgentGap?.key,
                                dimensionTitle: item.mostUrgentGap?.title,
                                originatingAction: "LOG_INQUIRY_FROM_COVERAGE",
                              });
                              onCreateIssue(
                                item.fund,
                                item.mostUrgentGap?.briefSection || "A",
                                item.mostUrgentGap?.key,
                                `Inquiry: ${item.mostUrgentGap?.title || "Diligence Area"}`,
                                `Investigate coverage gap in ${item.mostUrgentGap?.title}: ${item.mostUrgentGap?.explanation}`,
                                handoff
                              );
                            }}
                            className="btn btn-secondary btn-xs text-accent border-accent/40 hover:bg-accent/10 font-mono text-[11px]"
                            title="Log a new follow-up inquiry prefilled with dimension context"
                          >
                            + Log Inquiry
                          </button>
                        )}

                        <button
                          type="button"
                          onClick={() => {
                            const handoff = createInvestigationHandoff({
                              fund: {
                                schemeId: item.schemeId,
                                schemeOptionId: item.schemeOptionId,
                                schemeName: item.schemeName,
                                schemeCode: item.schemeCode,
                                planType: item.planType,
                                optionType: item.optionType,
                                amfiCode: item.amfiCode,
                                isin: item.isin,
                              },
                              briefSection: "coverage",
                              originatingAction: "COVERAGE_OVERVIEW_TRIAGE",
                            });
                            onNavigateToBrief(item.schemeId, item.schemeOptionId, "coverage", undefined, handoff);
                          }}
                          className="btn btn-ghost btn-xs text-accent border border-accent/30 hover:bg-accent/10 font-mono text-[11px]"
                        >
                          Triage in Brief ↗
                        </button>
                      </div>
                    </div>

                    {/* Linked Follow-Up Inquiries Row (if active) */}
                    {item.activeIssues.length > 0 && (
                      <div className="pt-1.5 border-t border-border/40 space-y-1 font-mono text-xs">
                        <span className="text-[10px] text-text-muted uppercase font-bold block">
                          Linked Inquiries ({item.activeIssues.length}):
                        </span>
                        <div className="flex flex-wrap gap-2">
                          {item.activeIssues.slice(0, 3).map((iss) => {
                            const statusCfg = FOLLOW_UP_STATUS_CONFIG[iss.status];
                            const outcomeCfg = iss.outcomeType ? FOLLOW_UP_OUTCOMES_CONFIG[iss.outcomeType] : null;
                            return (
                              <button
                                key={iss.id}
                                type="button"
                                onClick={() => {
                                  const handoff = createInvestigationHandoff({
                                    fund: {
                                      schemeId: item.schemeId,
                                      schemeOptionId: item.schemeOptionId,
                                      schemeName: item.schemeName,
                                      schemeCode: item.schemeCode,
                                      planType: item.planType,
                                      optionType: item.optionType,
                                      amfiCode: item.amfiCode,
                                      isin: item.isin,
                                    },
                                    briefSection: (iss.briefSection as any) || "coverage",
                                    dimensionKey: iss.checklistKey,
                                    dimensionTitle: iss.title,
                                    issue: iss,
                                    originatingAction: "INVESTIGATION_QUEUE_RESUME",
                                  });
                                  onOpenIssueModal(iss, handoff);
                                }}
                                className="p-1.5 rounded bg-surface border border-border/60 hover:border-accent/40 text-left text-[10.5px] transition-colors flex items-center gap-1.5"
                                title={`Click to view/edit inquiry #${iss.id.slice(-6)}: "${iss.title}"`}
                              >
                                <span className="font-bold text-text-primary truncate max-w-[200px]">
                                  #{iss.id.slice(-6)}: {iss.title}
                                </span>
                                <span className={`px-1 py-0.2 rounded text-[8.5px] border ${statusCfg?.badgeClass || ""}`}>
                                  {statusCfg?.label || iss.status}
                                </span>
                                {outcomeCfg && (
                                  <span className={`px-1 py-0.2 rounded text-[8.5px] border ${outcomeCfg.badgeClass}`}>
                                    {outcomeCfg.shortLabel}
                                  </span>
                                )}
                              </button>
                            );
                          })}
                          {item.activeIssues.length > 3 && (
                            <button
                              type="button"
                              onClick={() => {
                                const handoff = createInvestigationHandoff({
                                  fund: {
                                    schemeId: item.schemeId,
                                    schemeOptionId: item.schemeOptionId,
                                    schemeName: item.schemeName,
                                    schemeCode: item.schemeCode,
                                    planType: item.planType,
                                    optionType: item.optionType,
                                    amfiCode: item.amfiCode,
                                    isin: item.isin,
                                  },
                                  briefSection: "coverage",
                                  originatingAction: "COVERAGE_OVERVIEW_TRIAGE",
                                });
                                onNavigateToBrief(item.schemeId, item.schemeOptionId, "coverage", undefined, handoff);
                              }}
                              className="text-[10px] text-accent hover:underline self-center px-1"
                            >
                              +{item.activeIssues.length - 3} more in Coverage view ↗
                            </button>
                          )}
                        </div>
                      </div>
                    )}
                  </div>
                );
              })
            )}
          </div>
        </>
      )}
    </div>
  );
}
