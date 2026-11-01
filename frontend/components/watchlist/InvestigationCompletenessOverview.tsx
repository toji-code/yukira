"use client";

import { useMemo } from "react";
import { InvestigationCompletenessSummary } from "@/lib/utils/researchCompleteness";
import { FilterTab } from "@/lib/utils/savedResearchViews";

export interface InvestigationCompletenessOverviewProps {
  summary: InvestigationCompletenessSummary;
  activeTab: FilterTab;
  onSelectTab: (tab: FilterTab) => void;
}

export function InvestigationCompletenessOverview({
  summary,
  activeTab,
  onSelectTab,
}: InvestigationCompletenessOverviewProps) {
  const isAnyCompletenessTabActive = useMemo(() => {
    return (
      activeTab === "INCOMPLETE_RECORDS" ||
      activeTab === "WITHOUT_THESIS" ||
      activeTab === "MISSING_QUESTION" ||
      activeTab === "MISSING_NEXT_STEP" ||
      activeTab === "MISSING_SUPPORTING_EVIDENCE" ||
      activeTab === "MISSING_RECONSIDERATION_CONDITIONS" ||
      activeTab === "REVIEW_NEEDED"
    );
  }, [activeTab]);

  if (summary.totalInvestigations === 0) {
    return null;
  }

  return (
    <div
      role="region"
      aria-label="Research Documentation Coverage and Investigation Completeness"
      className="panel mb-6 p-4 bg-surface border border-border/80 rounded-xl space-y-3 font-mono shadow-xs"
    >
      {/* Header and Epistemic Disclosure */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-2.5 pb-2.5 border-b border-border/60">
        <div className="space-y-0.5">
          <div className="flex flex-wrap items-center gap-2">
            <span className="flex h-5 w-5 items-center justify-center rounded bg-accent/20 text-accent text-xs font-bold shrink-0">
              📑
            </span>
            <h3 className="text-xs font-bold uppercase tracking-wider text-text-cream-bright">
              Research Coverage &amp; Investigation Completeness
            </h3>
            <span
              className={`status-badge text-[10px] font-mono font-bold ${
                summary.fullyDocumentedCount === summary.totalInvestigations
                  ? "bg-emerald-500/15 border-emerald-500/30 text-emerald-300"
                  : "bg-surface-raised border-border text-text-secondary"
              }`}
            >
              {summary.fullyDocumentedCount} of {summary.totalInvestigations} Fully Documented (6/6 inputs)
            </span>
            {isAnyCompletenessTabActive && (
              <span className="status-badge state-candidate text-[9.5px] text-amber-300">
                FILTER APPLIED: {activeTab.replace(/_/g, " ")}
              </span>
            )}
          </div>
          <p className="text-[11.5px] text-text-muted font-sans leading-relaxed">
            Descriptive documentation presence tracking across your saved mutual funds. Field presence measures recorded inquiry, not analytical correctness, fund quality, or investment suitability.
          </p>
        </div>

        {isAnyCompletenessTabActive && (
          <button
            type="button"
            onClick={() => onSelectTab("ALL")}
            className="btn btn-ghost btn-xs text-[11px] text-accent hover:underline self-start md:self-center shrink-0"
            title="Clear completeness filter and display all saved funds"
          >
            Show All ({summary.totalInvestigations}) ✕
          </button>
        )}
      </div>

      {/* Actionable Coverage Metric Pills */}
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-7 gap-2 pt-1 text-xs">
        {/* 1. Missing Thesis */}
        <button
          type="button"
          onClick={() => onSelectTab("WITHOUT_THESIS")}
          className={`p-2.5 rounded-lg border text-left transition-all flex flex-col justify-between ${
            activeTab === "WITHOUT_THESIS"
              ? "bg-amber-500/20 border-amber-400/80 ring-1 ring-amber-400/50 shadow-xs"
              : summary.missingThesisCount > 0
              ? "bg-surface-raised/40 hover:bg-surface-raised/70 border-border/80 hover:border-amber-400/40"
              : "bg-surface-raised/20 border-border/40 opacity-70"
          }`}
          title="Filter for funds lacking an investor-authored working thesis"
        >
          <div className="flex items-center justify-between">
            <span className="text-[10px] text-text-muted uppercase font-bold">Missing Thesis</span>
            <span className="text-[11px]">{summary.missingThesisCount > 0 ? "⚠️" : "✓"}</span>
          </div>
          <div className="mt-1 flex items-baseline gap-1.5">
            <span
              className={`text-base font-bold font-mono ${
                summary.missingThesisCount > 0 ? "text-amber-400" : "text-text-muted"
              }`}
            >
              {summary.missingThesisCount}
            </span>
            <span className="text-[10px] text-text-tertiary">/ {summary.totalInvestigations}</span>
          </div>
          <span className="text-[9.5px] text-text-muted mt-0.5 truncate">Hypothesis unrecorded</span>
        </button>

        {/* 2. Missing Question */}
        <button
          type="button"
          onClick={() => onSelectTab("MISSING_QUESTION")}
          className={`p-2.5 rounded-lg border text-left transition-all flex flex-col justify-between ${
            activeTab === "MISSING_QUESTION"
              ? "bg-amber-500/20 border-amber-400/80 ring-1 ring-amber-400/50 shadow-xs"
              : summary.missingQuestionCount > 0
              ? "bg-surface-raised/40 hover:bg-surface-raised/70 border-border/80 hover:border-amber-400/40"
              : "bg-surface-raised/20 border-border/40 opacity-70"
          }`}
          title="Filter for funds lacking an explicit due-diligence question"
        >
          <div className="flex items-center justify-between">
            <span className="text-[10px] text-text-muted uppercase font-bold">Missing Question</span>
            <span className="text-[11px]">{summary.missingQuestionCount > 0 ? "❓" : "✓"}</span>
          </div>
          <div className="mt-1 flex items-baseline gap-1.5">
            <span
              className={`text-base font-bold font-mono ${
                summary.missingQuestionCount > 0 ? "text-amber-400" : "text-text-muted"
              }`}
            >
              {summary.missingQuestionCount}
            </span>
            <span className="text-[10px] text-text-tertiary">/ {summary.totalInvestigations}</span>
          </div>
          <span className="text-[9.5px] text-text-muted mt-0.5 truncate">Diligence question</span>
        </button>

        {/* 3. Missing Next Step */}
        <button
          type="button"
          onClick={() => onSelectTab("MISSING_NEXT_STEP")}
          className={`p-2.5 rounded-lg border text-left transition-all flex flex-col justify-between ${
            activeTab === "MISSING_NEXT_STEP"
              ? "bg-amber-500/20 border-amber-400/80 ring-1 ring-amber-400/50 shadow-xs"
              : summary.missingNextStepCount > 0
              ? "bg-surface-raised/40 hover:bg-surface-raised/70 border-border/80 hover:border-amber-400/40"
              : "bg-surface-raised/20 border-border/40 opacity-70"
          }`}
          title="Filter for funds lacking an actionable next step"
        >
          <div className="flex items-center justify-between">
            <span className="text-[10px] text-text-muted uppercase font-bold">Missing Next Step</span>
            <span className="text-[11px]">{summary.missingNextStepCount > 0 ? "↳" : "✓"}</span>
          </div>
          <div className="mt-1 flex items-baseline gap-1.5">
            <span
              className={`text-base font-bold font-mono ${
                summary.missingNextStepCount > 0 ? "text-amber-400" : "text-text-muted"
              }`}
            >
              {summary.missingNextStepCount}
            </span>
            <span className="text-[10px] text-text-tertiary">/ {summary.totalInvestigations}</span>
          </div>
          <span className="text-[9.5px] text-text-muted mt-0.5 truncate">Action plan pending</span>
        </button>

        {/* 4. Missing Supporting Evidence */}
        <button
          type="button"
          onClick={() => onSelectTab("MISSING_SUPPORTING_EVIDENCE")}
          className={`p-2.5 rounded-lg border text-left transition-all flex flex-col justify-between ${
            activeTab === "MISSING_SUPPORTING_EVIDENCE"
              ? "bg-accent/20 border-accent ring-1 ring-accent/50 shadow-xs"
              : summary.missingSupportingEvidenceCount > 0
              ? "bg-surface-raised/40 hover:bg-surface-raised/70 border-border/80 hover:border-accent/40"
              : "bg-surface-raised/20 border-border/40 opacity-70"
          }`}
          title="Filter for funds lacking documented supporting evidence notes"
        >
          <div className="flex items-center justify-between">
            <span className="text-[10px] text-text-muted uppercase font-bold">Missing Evidence</span>
            <span className="text-[11px]">{summary.missingSupportingEvidenceCount > 0 ? "📝" : "✓"}</span>
          </div>
          <div className="mt-1 flex items-baseline gap-1.5">
            <span
              className={`text-base font-bold font-mono ${
                summary.missingSupportingEvidenceCount > 0 ? "text-accent" : "text-text-muted"
              }`}
            >
              {summary.missingSupportingEvidenceCount}
            </span>
            <span className="text-[10px] text-text-tertiary">/ {summary.totalInvestigations}</span>
          </div>
          <span className="text-[9.5px] text-text-muted mt-0.5 truncate">Evidence notes absent</span>
        </button>

        {/* 5. Missing Reconsideration Triggers */}
        <button
          type="button"
          onClick={() => onSelectTab("MISSING_RECONSIDERATION_CONDITIONS")}
          className={`p-2.5 rounded-lg border text-left transition-all flex flex-col justify-between ${
            activeTab === "MISSING_RECONSIDERATION_CONDITIONS"
              ? "bg-accent/20 border-accent ring-1 ring-accent/50 shadow-xs"
              : summary.missingReconsiderationCount > 0
              ? "bg-surface-raised/40 hover:bg-surface-raised/70 border-border/80 hover:border-accent/40"
              : "bg-surface-raised/20 border-border/40 opacity-70"
          }`}
          title="Filter for funds lacking explicit invalidation / reconsideration criteria"
        >
          <div className="flex items-center justify-between">
            <span className="text-[10px] text-text-muted uppercase font-bold">Missing Triggers</span>
            <span className="text-[11px]">{summary.missingReconsiderationCount > 0 ? "⚖️" : "✓"}</span>
          </div>
          <div className="mt-1 flex items-baseline gap-1.5">
            <span
              className={`text-base font-bold font-mono ${
                summary.missingReconsiderationCount > 0 ? "text-accent" : "text-text-muted"
              }`}
            >
              {summary.missingReconsiderationCount}
            </span>
            <span className="text-[10px] text-text-tertiary">/ {summary.totalInvestigations}</span>
          </div>
          <span className="text-[9.5px] text-text-muted mt-0.5 truncate">Invalidation criteria</span>
        </button>

        {/* 6. Incomplete Records Filter */}
        <button
          type="button"
          onClick={() => onSelectTab("INCOMPLETE_RECORDS")}
          className={`p-2.5 rounded-lg border text-left transition-all flex flex-col justify-between ${
            activeTab === "INCOMPLETE_RECORDS"
              ? "bg-rose-500/20 border-rose-400 ring-1 ring-rose-400/50 shadow-xs"
              : summary.incompleteDocumentationCount > 0
              ? "bg-surface-raised/40 hover:bg-surface-raised/70 border-border/80 hover:border-rose-400/40"
              : "bg-surface-raised/20 border-border/40 opacity-70"
          }`}
          title="Filter for all records with 1 or more missing documentation inputs"
        >
          <div className="flex items-center justify-between">
            <span className="text-[10px] text-text-muted uppercase font-bold">Any Input Missing</span>
            <span className="text-[11px]">{summary.incompleteDocumentationCount > 0 ? "📋" : "✓"}</span>
          </div>
          <div className="mt-1 flex items-baseline gap-1.5">
            <span
              className={`text-base font-bold font-mono ${
                summary.incompleteDocumentationCount > 0 ? "text-rose-400" : "text-emerald-400"
              }`}
            >
              {summary.incompleteDocumentationCount}
            </span>
            <span className="text-[10px] text-text-tertiary">/ {summary.totalInvestigations}</span>
          </div>
          <span className="text-[9.5px] text-text-muted mt-0.5 truncate">&lt; 6 inputs recorded</span>
        </button>

        {/* 7. Evidence Review Needed */}
        <button
          type="button"
          onClick={() => onSelectTab("REVIEW_NEEDED")}
          className={`p-2.5 rounded-lg border text-left transition-all flex flex-col justify-between ${
            activeTab === "REVIEW_NEEDED"
              ? "bg-rose-500/20 border-rose-400 ring-1 ring-rose-400/50 shadow-xs"
              : summary.evidenceReviewNeededCount > 0
              ? "bg-surface-raised/40 hover:bg-surface-raised/70 border-border/80 hover:border-rose-400/40"
              : "bg-surface-raised/20 border-border/40 opacity-70"
          }`}
          title="Filter for funds where underlying market observations changed since baseline review"
        >
          <div className="flex items-center justify-between">
            <span className="text-[10px] text-rose-400 uppercase font-bold">Review Needed</span>
            <span className="text-[11px]">{summary.evidenceReviewNeededCount > 0 ? "⚠️" : "✓"}</span>
          </div>
          <div className="mt-1 flex items-baseline gap-1.5">
            <span
              className={`text-base font-bold font-mono ${
                summary.evidenceReviewNeededCount > 0 ? "text-rose-400" : "text-text-muted"
              }`}
            >
              {summary.evidenceReviewNeededCount}
            </span>
            <span className="text-[10px] text-text-tertiary">/ {summary.totalInvestigations}</span>
          </div>
          <span className="text-[9.5px] text-text-muted mt-0.5 truncate">Evidence divergence</span>
        </button>
      </div>

      {/* Provenance & Epistemic Legend */}
      <div className="pt-2 border-t border-border/40 flex flex-wrap items-center justify-between gap-2 text-[10.5px] text-text-tertiary font-sans">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
          <span className="inline-flex items-center gap-1">
            <span className="text-sky-400">☁️</span>
            <span><strong>Server Synchronized:</strong> Objectives, Questions, Next Steps, Due-Diligence Status</span>
          </span>
          <span>•</span>
          <span className="inline-flex items-center gap-1">
            <span className="text-accent">💾</span>
            <span><strong>Browser Local:</strong> Working Theses, Supporting Evidence, Reconsideration Triggers</span>
          </span>
          <span>•</span>
          <span className="inline-flex items-center gap-1">
            <span className="text-amber-400">⏳</span>
            <span><strong>Historical Baseline:</strong> Canonical pilot cutoff evaluated through 2024-01-15</span>
          </span>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-2 pt-1 border-t border-border/40 text-[10.5px] text-text-muted">
          <span>Click any card above to focus the workspace on that missing input.</span>
          <span className="text-text-tertiary">
            📋 <strong>Evidence Review:</strong> Documenting an input does not certify evidence inspection. Open any Fund Research Brief to record verified review activity.
          </span>
        </div>
      </div>
    </div>
  );
}
