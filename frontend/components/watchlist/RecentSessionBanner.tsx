"use client";

import { useMemo } from "react";
import {
  ResumableResearchSession,
  formatSessionRelativeTime,
  getBriefSectionTitle,
  deriveHandoffExplainableRationale,
} from "@/lib/utils/researchSession";
import { WatchlistResponse } from "@/lib/api/investor";

export interface RecentSessionBannerProps {
  session: ResumableResearchSession | null;
  savedItems: WatchlistResponse[];
  onResumeSession: (session: ResumableResearchSession) => void;
  onClearSession: () => void;
  onOpenIssueById?: (issueId: string) => void;
}

export function RecentSessionBanner({
  session,
  savedItems,
  onResumeSession,
  onClearSession,
  onOpenIssueById,
}: RecentSessionBannerProps) {
  // Check whether the referenced scheme option currently exists in the workspace
  const matchingWorkspaceItem = useMemo(() => {
    if (!session || !session.schemeId) return null;
    return (
      savedItems.find((item) =>
        session.schemeOptionId !== undefined && session.schemeOptionId !== null
          ? item.schemeOptionId === session.schemeOptionId
          : item.schemeId === session.schemeId
      ) || null
    );
  }, [session, savedItems]);

  if (!session) return null;

  const isFundSession =
    session.destination === "BRIEF" ||
    session.destination === "NOTE" ||
    session.destination === "PROFILE";
  const isWorkspaceItemMissing = isFundSession && !matchingWorkspaceItem;
  const handoff = session.handoffContext;
  const rationale = handoff
    ? deriveHandoffExplainableRationale(handoff.lifecycleStage)
    : null;

  return (
    <div
      role="region"
      aria-label="Recent Research Session Resumption and Investigation Handoff"
      className="panel mb-6 p-3.5 bg-surface-navy-raised/95 border border-accent/50 rounded-xl shadow-lg flex flex-col md:flex-row md:items-center justify-between gap-3 text-xs font-mono animate-in fade-in duration-150"
    >
      {/* Left: Investigation Context & Identity */}
      <div className="flex items-start gap-3 min-w-0 flex-1">
        <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-accent/20 text-accent font-bold text-sm shrink-0 mt-0.5">
          {session.followUpIssueId ? "📌" : handoff ? "🔍" : "⚡"}
        </span>
        <div className="min-w-0 space-y-1.5 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            {handoff?.originatingAction === "REASSESSMENT_START" ? (
              <span className="mono-meta text-amber-300 font-bold uppercase tracking-wider text-[10px] bg-amber-500/20 px-2 py-0.5 rounded border border-amber-500/40">
                ⚡ REASSESSMENT CONTINUITY
              </span>
            ) : handoff && rationale ? (
              <span
                className={`mono-meta font-bold uppercase tracking-wider text-[10px] px-2 py-0.5 rounded border ${rationale.badgeClass}`}
                title={`Investigation Lifecycle: ${handoff.lifecycleStage}`}
              >
                {rationale.badgeLabel}
              </span>
            ) : session.followUpIssueId ? (
              <span className="mono-meta text-amber-300 font-bold uppercase tracking-wider text-[10px] bg-amber-500/20 px-1.5 py-0.5 rounded border border-amber-500/40">
                RESUME FOLLOW-UP ISSUE
              </span>
            ) : (
              <span className="mono-meta text-accent font-bold uppercase tracking-wider text-[10px]">
                RESUME LAST INVESTIGATION
              </span>
            )}

            {handoff?.dimensionTitle && (
              <span className="text-[10px] px-1.5 py-0.2 rounded border bg-surface text-accent font-bold border-accent/40">
                {handoff.dimensionTitle}
              </span>
            )}

            {handoff?.dependencyDescription && (
              <span
                className={`text-[10px] px-1.5 py-0.2 rounded border font-bold ${
                  handoff.dependencyStatus === "ADDRESSED"
                    ? "bg-cyan-500/20 text-cyan-300 border-cyan-500/40"
                    : "bg-purple-500/20 text-purple-300 border-purple-500/40"
                }`}
                title={`Investigation Prerequisite: ${handoff.dependencyDescription}`}
              >
                {handoff.dependencyStatus === "ADDRESSED" ? "✓ PREREQUISITE ADDRESSED" : "⏳ AWAITING PREREQUISITE"}
              </span>
            )}

            <span className="text-text-muted text-[10px]">•</span>
            <span className="text-text-tertiary text-[10.5px]">
              {formatSessionRelativeTime(session.updatedAt)}
            </span>
            {isWorkspaceItemMissing && (
              <span className="status-badge state-candidate text-[9.5px] text-amber-300">
                NOT IN ACTIVE SAVED WORKSPACE (PRESERVED)
              </span>
            )}
          </div>

          {/* Fund Name and Destination Summary */}
          <div className="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-text-primary">
            {session.schemeName ? (
              <span className="font-bold text-sm text-text-cream-bright truncate">
                {session.schemeName}
              </span>
            ) : session.destination === "WORKSPACE" ? (
              <span className="font-bold text-sm text-text-cream-bright">
                Workspace: {session.savedViewName || "Configured Research View"}
              </span>
            ) : (
              <span className="font-bold text-sm text-text-cream-bright">
                Previous Research Context
              </span>
            )}

            {(session.planType || session.optionType) && (
              <span className="text-[11px] text-text-secondary font-semibold">
                ({session.planType || "DIRECT"} • {session.optionType || "GROWTH"}
                {session.schemeOptionId ? ` • Opt #${session.schemeOptionId}` : ""})
              </span>
            )}
          </div>

          {/* Follow-up issue inquiry title if present */}
          {session.followUpIssueTitle && (
            <div className="text-[11.5px] font-mono text-amber-300 font-bold flex items-center gap-1.5">
              <span>📌 Inquiry:</span>
              <span className="text-text-cream-bright">&ldquo;{session.followUpIssueTitle}&rdquo;</span>
            </div>
          )}

          {/* Phase F27: Handoff Deterministic Explainable Rationale & Next Step */}
          {handoff && (
            <div className="space-y-0.5 text-[11px] font-sans">
              <p className="text-text-primary leading-snug">
                <strong className="font-mono text-[10px] text-amber-300 uppercase">Why attention needed: </strong>
                {handoff.whyAttentionNeeded || rationale?.whyAttentionNeeded}
              </p>
              <p className="text-text-secondary leading-snug">
                <strong className="font-mono text-[10px] text-accent uppercase">Suggested Next Diligence Step: </strong>
                {handoff.suggestedNextStep || rationale?.suggestedNextStep}
              </p>
            </div>
          )}

          {/* Destination Description & Navigation Context */}
          <p className="text-[11.5px] text-text-secondary leading-snug font-sans">
            {session.destination === "BRIEF" && (
              <span>
                Resume in <strong>Fund Research Brief</strong> ({getBriefSectionTitle(session.briefSection)}).
              </span>
            )}
            {session.destination === "NOTE" && (
              <span>
                Resume editing <strong>Due Diligence Notes &amp; Working Thesis</strong>.
              </span>
            )}
            {session.destination === "PROFILE" && (
              <span>
                Revisit verified <strong>Fund Profile &amp; Evidence Coverage</strong>.
              </span>
            )}
            {session.destination === "COMPARE" && (
              <span>
                Resume <strong>Side-by-Side Evidence Comparison</strong> ({session.comparisonCandidateKeys?.length || 2} candidates).
              </span>
            )}
            {session.destination === "WORKSPACE" && (
              <span>
                Re-apply configured filters: <strong>{session.filterTab?.replace(/_/g, " ")}</strong>
                {session.searchQuery ? ` • Query: "${session.searchQuery}"` : ""}
                {session.selectedAmc && session.selectedAmc !== "ALL" ? ` • AMC: ${session.selectedAmc}` : ""}.
              </span>
            )}
            {isWorkspaceItemMissing && (
              <span className="text-amber-300 ml-1">
                (This fund option is not in your active saved workspace; its stored identity and historical inquiries are preserved without substitution).
              </span>
            )}
          </p>

          {/* Provenance comparison strip if recorded */}
          {handoff && (handoff.evidenceRunId || handoff.evidenceAsOfDate) && (
            <div className="text-[10px] font-mono text-text-muted flex flex-wrap items-center gap-2 pt-0.5 border-t border-border/40">
              <span>
                Evidentiary Baseline: {handoff.evidenceRunId ? `Run #${handoff.evidenceRunId}` : ""}
                {handoff.evidenceAsOfDate ? ` (Cutoff ${handoff.evidenceAsOfDate})` : ""}
              </span>
              {handoff.isCutoffDrifted && (
                <span className="text-amber-300 font-bold">⚠️ Observation Cutoff Drifted</span>
              )}
              {handoff.isRunSuperseded && (
                <span className="text-blue-300 font-bold">⚠️ Run Superseded</span>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Right: Actions */}
      <div className="flex flex-col sm:flex-row items-end sm:items-center gap-2 shrink-0 self-end md:self-center">
        {session.followUpIssueId && onOpenIssueById && (
          <button
            type="button"
            onClick={() => onOpenIssueById(session.followUpIssueId!)}
            className="btn btn-secondary btn-sm text-amber-300 border-amber-500/40 hover:bg-amber-500/10 font-mono text-xs whitespace-nowrap"
            title="Open active inquiry details and review evidence history"
          >
            {handoff?.originatingAction === "REASSESSMENT_START" ? "Review Evidence History 📜" : "Review Issue Modal ↗"}
          </button>
        )}

        <button
          type="button"
          onClick={() => onResumeSession(session)}
          className="btn btn-primary btn-sm flex items-center gap-1.5 font-semibold shadow-xs whitespace-nowrap"
          title="Resume this specific investigation context"
        >
          <span>
            {handoff?.originatingAction === "REASSESSMENT_START"
              ? "Resume Reassessment ⚡"
              : session.followUpIssueId
              ? "Resume Inquiry In Brief →"
              : handoff?.briefSection === "coverage"
              ? "Resume Coverage Triage →"
              : session.destination === "BRIEF"
              ? "Resume Diligence In Brief →"
              : session.destination === "NOTE"
              ? "Resume Thesis ✎"
              : session.destination === "PROFILE"
              ? "Open Profile →"
              : session.destination === "COMPARE"
              ? "Resume Comparison →"
              : "Restore View →"}
          </span>
        </button>

        <button
          type="button"
          onClick={onClearSession}
          className="btn btn-ghost btn-sm text-[11px] text-text-muted hover:text-text-primary px-2 py-1"
          title="Dismiss recent session context (will not affect saved funds, notes, or views)"
          aria-label="Dismiss recent session"
        >
          ✕ Dismiss
        </button>
      </div>
    </div>
  );
}
