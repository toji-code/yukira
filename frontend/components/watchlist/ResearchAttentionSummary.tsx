"use client";

import React from "react";
import { ResearchAttentionSummary as AttentionSummaryType } from "@/lib/utils/researchAttention";

interface ResearchAttentionSummaryProps {
  summary: AttentionSummaryType;
  activeTab: string;
  onSelectTab: (tab: any) => void;
  className?: string;
}

export function ResearchAttentionSummary({
  summary,
  activeTab,
  onSelectTab,
  className = "",
}: ResearchAttentionSummaryProps) {
  if (summary.totalSaved === 0) return null;

  return (
    <section
      aria-label="Research Attention and Priority Queue"
      className={`panel mb-6 p-4 sm:p-5 bg-surface-raised/40 border border-border shadow-xs rounded-xl space-y-4 ${className}`}
    >
      {/* Header and Epistemic Demarcation */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border/70 pb-3">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <span className="mono-meta text-accent font-bold uppercase tracking-wider text-[11px]">
              YUKIRA Research Attention Workspace
            </span>
            <span className="status-badge state-operational font-mono text-[10.5px]">
              {summary.attentionQueueCount} of {summary.totalSaved} require inquiry attention
            </span>
            {summary.attentionQueueCount === 0 && (
              <span className="status-badge state-approved text-[10px]">ALL INQUIRIES STABLE</span>
            )}
          </div>
          <h2 className="text-sm sm:text-base font-bold text-text-primary">
            Research Priority &amp; Attention Queue
          </h2>
          <p className="text-xs text-text-secondary leading-relaxed max-w-3xl">
            Deterministic inquiry triage based on outstanding investor follow-ups, verified evidence changes, and incomplete research records.{" "}
            <strong className="text-text-primary">Epistemic Guardrail:</strong> This queue orders an investor&rsquo;s research attention—it does <em>not</em> evaluate fund quality, investment attractiveness, or portfolio suitability.
          </p>
        </div>

        {/* Primary Focus Button: View Queue */}
        <button
          type="button"
          onClick={() => onSelectTab("ATTENTION_QUEUE")}
          className={`btn btn-sm font-mono text-xs flex items-center gap-2 shadow-xs transition-all ${
            activeTab === "ATTENTION_QUEUE"
              ? "btn-primary ring-2 ring-accent/40"
              : "btn-secondary text-accent border-accent/40 hover:bg-accent/10"
          }`}
        >
          <span className="text-amber-400">⚡</span>
          <span>Open Attention Queue ({summary.attentionQueueCount})</span>
        </button>
      </div>

      {/* Actionable Category Chips / Stat Cards */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2.5 font-mono text-xs">
        {/* Card 1: Needs Follow-Up */}
        <button
          type="button"
          onClick={() => onSelectTab("NEEDS_FOLLOW_UP")}
          className={`p-2.5 rounded-lg border text-left transition-all flex flex-col justify-between ${
            activeTab === "NEEDS_FOLLOW_UP"
              ? "bg-rose-500/15 border-rose-500/50 ring-1 ring-rose-500/40"
              : "bg-surface border-border hover:border-rose-500/40 hover:bg-surface-raised"
          }`}
        >
          <span className="text-[10px] text-text-tertiary uppercase font-bold block">
            1. Follow-Up Needed
          </span>
          <div className="flex items-baseline justify-between mt-1">
            <span className="text-base font-bold text-rose-400">{summary.needsFollowUpCount}</span>
            <span className="text-[10px] text-text-muted">Rank #1</span>
          </div>
          <span className="text-[10px] text-text-secondary font-sans mt-0.5 leading-snug">
            Explicit investor flag
          </span>
        </button>

        {/* Card 2: Reconsideration Check */}
        <button
          type="button"
          onClick={() => onSelectTab("REVIEW_NEEDED")}
          className={`p-2.5 rounded-lg border text-left transition-all flex flex-col justify-between ${
            activeTab === "REVIEW_NEEDED"
              ? "bg-amber-500/15 border-amber-500/50 ring-1 ring-amber-500/40"
              : "bg-surface border-border hover:border-amber-500/40 hover:bg-surface-raised"
          }`}
        >
          <span className="text-[10px] text-text-tertiary uppercase font-bold block">
            2. Reconsideration
          </span>
          <div className="flex items-baseline justify-between mt-1">
            <span className="text-base font-bold text-amber-300">{summary.reviewNeededCount}</span>
            <span className="text-[10px] text-text-muted">Rank #2</span>
          </div>
          <span className="text-[10px] text-text-secondary font-sans mt-0.5 leading-snug">
            Evidence changed post-review
          </span>
        </button>

        {/* Card 3: Stale Historical Cutoff */}
        <button
          type="button"
          onClick={() => onSelectTab("STALE_EVIDENCE")}
          className={`p-2.5 rounded-lg border text-left transition-all flex flex-col justify-between ${
            activeTab === "STALE_EVIDENCE"
              ? "bg-amber-500/15 border-amber-500/50 ring-1 ring-amber-500/40"
              : "bg-surface border-border hover:border-amber-500/40 hover:bg-surface-raised"
          }`}
        >
          <span className="text-[10px] text-text-tertiary uppercase font-bold block">
            3. Stale Cutoff
          </span>
          <div className="flex items-baseline justify-between mt-1">
            <span className="text-base font-bold text-amber-400">{summary.staleEvidenceCount}</span>
            <span className="text-[10px] text-text-muted">Rank #3</span>
          </div>
          <span className="text-[10px] text-text-secondary font-sans mt-0.5 leading-snug">
            2024-01-15 pilot cutoff
          </span>
        </button>

        {/* Card 4: To Investigate */}
        <button
          type="button"
          onClick={() => onSelectTab("TO_INVESTIGATE")}
          className={`p-2.5 rounded-lg border text-left transition-all flex flex-col justify-between ${
            activeTab === "TO_INVESTIGATE"
              ? "bg-sky-500/15 border-sky-500/50 ring-1 ring-sky-500/40"
              : "bg-surface border-border hover:border-sky-500/40 hover:bg-surface-raised"
          }`}
        >
          <span className="text-[10px] text-text-tertiary uppercase font-bold block">
            4. Unreviewed
          </span>
          <div className="flex items-baseline justify-between mt-1">
            <span className="text-base font-bold text-sky-400">{summary.toInvestigateCount}</span>
            <span className="text-[10px] text-text-muted">Rank #4</span>
          </div>
          <span className="text-[10px] text-text-secondary font-sans mt-0.5 leading-snug">
            Initial review pending
          </span>
        </button>

        {/* Card 5: Incomplete Records */}
        <button
          type="button"
          onClick={() => onSelectTab("INCOMPLETE_RECORDS")}
          className={`p-2.5 rounded-lg border text-left transition-all flex flex-col justify-between ${
            activeTab === "INCOMPLETE_RECORDS"
              ? "bg-accent/15 border-accent/50 ring-1 ring-accent/40"
              : "bg-surface border-border hover:border-accent/40 hover:bg-surface-raised"
          }`}
        >
          <span className="text-[10px] text-text-tertiary uppercase font-bold block">
            5. Incomplete Record
          </span>
          <div className="flex items-baseline justify-between mt-1">
            <span className="text-base font-bold text-accent">{summary.incompleteRecordCount}</span>
            <span className="text-[10px] text-text-muted">Rank #6</span>
          </div>
          <span className="text-[10px] text-text-secondary font-sans mt-0.5 leading-snug">
            Missing question or step
          </span>
        </button>

        {/* Card 6: No Thesis Documented */}
        <button
          type="button"
          onClick={() => onSelectTab("WITHOUT_THESIS")}
          className={`p-2.5 rounded-lg border text-left transition-all flex flex-col justify-between ${
            activeTab === "WITHOUT_THESIS"
              ? "bg-accent/15 border-accent/50 ring-1 ring-accent/40"
              : "bg-surface border-border hover:border-border-gold-subtle hover:bg-surface-raised"
          }`}
        >
          <span className="text-[10px] text-text-tertiary uppercase font-bold block">
            6. No Thesis
          </span>
          <div className="flex items-baseline justify-between mt-1">
            <span className="text-base font-bold text-text-secondary">{summary.missingThesisCount}</span>
            <span className="text-[10px] text-text-muted">Rank #7</span>
          </div>
          <span className="text-[10px] text-text-secondary font-sans mt-0.5 leading-snug">
            Unrecorded hypothesis
          </span>
        </button>
      </div>

      {/* Epistemic Integrity Footnote */}
      <div className="pt-2 border-t border-border/50 flex flex-col sm:flex-row sm:items-center justify-between gap-2 text-[11px] font-mono text-text-muted">
        <span>
          *Attention precedence strictly evaluates investor due-diligence tasks and temporal data freshness. Zero automated buy/sell recommendations, zero suitability scoring.
        </span>
        <button
          type="button"
          onClick={() => onSelectTab("ALL")}
          className="text-accent hover:underline whitespace-nowrap self-start sm:self-auto"
        >
          View All Saved Inquiries ({summary.totalSaved}) →
        </button>
      </div>
    </section>
  );
}
