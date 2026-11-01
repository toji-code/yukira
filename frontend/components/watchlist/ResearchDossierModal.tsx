"use client";

import React, { useState, useMemo } from "react";
import {
  ResearchFollowUpIssue,
  FOLLOW_UP_OUTCOMES_CONFIG,
} from "@/lib/utils/researchFollowUp";
import { WatchlistResponse } from "@/lib/api/investor";
import {
  assembleResearchDossier,
  generateDossierMarkdown,
  generateDossierHtml,
  downloadDossierFile,
  sanitizeDossierFilename,
  ResearchDossierDocument,
} from "@/lib/utils/researchDossier";

export interface ResearchDossierModalProps {
  isOpen: boolean;
  onClose: () => void;
  selectedIssues: ResearchFollowUpIssue[];
  savedItems?: WatchlistResponse[];
  onRemoveIssueFromSelection?: (issueId: string) => void;
  onClearSelection?: () => void;
  onOpenIssueModal?: (issue: ResearchFollowUpIssue) => void;
  onNavigateToBrief?: (
    schemeId: number,
    schemeOptionId?: number | null,
    section?: "coverage" | "checklist" | "A" | "B" | "C" | "D" | "E" | "F" | "G" | "H",
    issue?: ResearchFollowUpIssue
  ) => void;
}

export function ResearchDossierModal({
  isOpen,
  onClose,
  selectedIssues,
  savedItems,
  onRemoveIssueFromSelection,
  onClearSelection,
  onOpenIssueModal,
  onNavigateToBrief,
}: ResearchDossierModalProps) {
  const [dossierTitle, setDossierTitle] = useState<string>("YUKIRA Research & Follow-Up Investigation Dossier");
  const [authorNotes, setAuthorNotes] = useState<string>("");
  const [exportFormat, setExportFormat] = useState<"md" | "html">("md");
  const [previewTab, setPreviewTab] = useState<"RECORDS" | "CONTINUITY" | "PREVIEW_RAW">("RECORDS");
  const [copyFeedback, setCopyFeedback] = useState<boolean>(false);

  const dossier: ResearchDossierDocument = useMemo(() => {
    return assembleResearchDossier(selectedIssues, savedItems, dossierTitle, authorNotes);
  }, [selectedIssues, savedItems, dossierTitle, authorNotes]);

  const generatedContent = useMemo(() => {
    if (exportFormat === "md") {
      return generateDossierMarkdown(dossier);
    } else {
      return generateDossierHtml(dossier);
    }
  }, [dossier, exportFormat]);

  if (!isOpen) return null;

  const handleDownload = () => {
    if (selectedIssues.length === 0) return;
    const filename = sanitizeDossierFilename(dossierTitle, exportFormat);
    const mimeType = exportFormat === "md" ? "text/markdown;charset=utf-8" : "text/html;charset=utf-8";
    downloadDossierFile(generatedContent, filename, mimeType);
  };

  const handleCopyToClipboard = () => {
    if (typeof navigator === "undefined" || !navigator.clipboard) return;
    const md = generateDossierMarkdown(dossier);
    navigator.clipboard.writeText(md).then(() => {
      setCopyFeedback(true);
      setTimeout(() => setCopyFeedback(false), 2200);
    });
  };

  const { continuitySummary } = dossier.metadata;
  const activeContinuityCount =
    continuitySummary.openCount +
    continuitySummary.inProgressCount +
    continuitySummary.waitingCount +
    continuitySummary.reopenedCount;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-4 bg-background/80 backdrop-blur-xs overflow-y-auto">
      <div className="relative w-full max-w-4xl bg-surface border border-border rounded-xl shadow-2xl flex flex-col max-h-[92vh] overflow-hidden my-auto animate-in fade-in zoom-in-95 duration-150">
        {/* Header */}
        <div className="px-5 py-4 border-b border-border bg-surface-raised/40 flex items-start justify-between gap-3">
          <div className="space-y-1">
            <div className="flex flex-wrap items-center gap-2">
              <span className="mono-meta text-accent font-bold uppercase tracking-wider text-[11px]">
                Research Dossier Assembly &amp; Continuity
              </span>
              <span className="status-badge state-operational font-mono text-[10px]">
                EVIDENCE-PRESERVING RESEARCH ARTIFACT
              </span>
              <span className="text-[10px] px-2 py-0.5 rounded bg-surface border border-border text-text-secondary font-mono">
                {selectedIssues.length} Record{selectedIssues.length !== 1 ? "s" : ""} Selected
              </span>
              {activeContinuityCount > 0 && (
                <span className="text-[10px] px-2 py-0.5 rounded bg-amber-500/15 border border-amber-500/30 text-amber-300 font-mono">
                  {activeContinuityCount} Active Diligence Need{activeContinuityCount !== 1 ? "s" : ""}
                </span>
              )}
            </div>
            <h2 className="text-base sm:text-lg font-bold text-text-primary tracking-tight">
              Assemble, Revisit &amp; Export Research Dossier
            </h2>
            <p className="text-xs text-text-secondary">
              Review documented inquiries, distinguish historical calculation cutoffs from active app evidence, track outstanding diligence actions, and seamlessly resume investigations.
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-lg text-text-muted hover:text-text-primary hover:bg-surface-raised transition-colors shrink-0"
            title="Close dossier modal"
          >
            ✕
          </button>
        </div>

        {/* Scrollable Content Body */}
        <div className="p-5 overflow-y-auto space-y-4 flex-1">
          {/* Epistemic Notice */}
          <div className="p-3 rounded-lg bg-amber-500/10 border border-amber-500/30 text-xs text-amber-200/95 space-y-1 font-sans">
            <div className="flex items-center gap-1.5 font-bold font-mono text-[11px] text-amber-300 uppercase tracking-wide">
              <span>⚠️ Evidentiary Scope &amp; Non-Recommendation Notice</span>
            </div>
            <p className="leading-relaxed">
              This dossier compiles personal investor-authored diligence inquiries, recorded observations, and point-in-time calculation provenance. Inclusion in this dossier does <strong>not</strong> constitute an investment recommendation, rating, or validation. Historical evidence contexts are tied to recorded calculation runs and fixed market cutoffs (e.g. 2024-01-15). Active application evidence is compared only when identifiable.
            </p>
          </div>

          {/* Dossier Scope Statistics Overview */}
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2">
            <div className="p-2.5 rounded-lg bg-surface-raised border border-border/80">
              <span className="mono-meta text-[10px] text-text-muted uppercase block">Selected Records</span>
              <span className="text-lg font-bold font-mono text-accent">{dossier.metadata.totalRecords}</span>
            </div>
            <div className="p-2.5 rounded-lg bg-surface-raised border border-border/80">
              <span className="mono-meta text-[10px] text-text-muted uppercase block">Share Classes</span>
              <span className="text-lg font-bold font-mono text-text-primary">{dossier.metadata.totalFundOptions}</span>
            </div>
            <div className="p-2.5 rounded-lg bg-surface-raised border border-border/80">
              <span className="mono-meta text-[10px] text-text-muted uppercase block">Active Inquiries</span>
              <span className="text-lg font-bold font-mono text-amber-300">{dossier.metadata.unresolvedCount}</span>
            </div>
            <div className="p-2.5 rounded-lg bg-surface-raised border border-border/80">
              <span className="mono-meta text-[10px] text-text-muted uppercase block">Waiting / Overdue</span>
              <span className="text-lg font-bold font-mono text-rose-300">
                {continuitySummary.waitingCount + continuitySummary.overdueCount}
              </span>
            </div>
            <div className="p-2.5 rounded-lg bg-surface-raised border border-border/80">
              <span className="mono-meta text-[10px] text-text-muted uppercase block">Evidence Drift</span>
              <span className="text-lg font-bold font-mono text-purple-300">
                {continuitySummary.evidenceDriftCount}
              </span>
            </div>
            <div className="p-2.5 rounded-lg bg-surface-raised border border-border/80">
              <span className="mono-meta text-[10px] text-text-muted uppercase block">Resolved Historical</span>
              <span className="text-lg font-bold font-mono text-emerald-400">{dossier.metadata.resolvedCount}</span>
            </div>
          </div>

          {/* Configuration Form: Title, Notes, Format */}
          <div className="p-3.5 rounded-lg bg-surface-raised/40 border border-border space-y-3">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <div className="md:col-span-2 space-y-1">
                <label className="text-[11px] font-semibold text-text-secondary uppercase tracking-wider font-mono block">
                  Dossier Title
                </label>
                <input
                  type="text"
                  value={dossierTitle}
                  onChange={(e) => setDossierTitle(e.target.value)}
                  placeholder="e.g. HDFC Flexi Cap Due Diligence & Anomaly Dossier"
                  className="w-full px-3 py-1.5 rounded-md bg-surface border border-border text-text-primary text-xs focus:outline-hidden focus:border-accent font-sans"
                />
              </div>

              <div className="space-y-1">
                <label className="text-[11px] font-semibold text-text-secondary uppercase tracking-wider font-mono block">
                  Export Format
                </label>
                <div className="grid grid-cols-2 gap-1.5">
                  <button
                    type="button"
                    onClick={() => setExportFormat("md")}
                    className={`px-3 py-1.5 rounded-md text-xs font-semibold border transition-all ${
                      exportFormat === "md"
                        ? "bg-accent/20 border-accent text-accent font-bold"
                        : "bg-surface border-border text-text-secondary hover:text-text-primary"
                    }`}
                  >
                    Markdown (.md)
                  </button>
                  <button
                    type="button"
                    onClick={() => setExportFormat("html")}
                    className={`px-3 py-1.5 rounded-md text-xs font-semibold border transition-all ${
                      exportFormat === "html"
                        ? "bg-accent/20 border-accent text-accent font-bold"
                        : "bg-surface border-border text-text-secondary hover:text-text-primary"
                    }`}
                  >
                    Standalone HTML
                  </button>
                </div>
              </div>
            </div>

            <div className="space-y-1">
              <label className="text-[11px] font-semibold text-text-secondary uppercase tracking-wider font-mono block">
                Investor Audit &amp; Diligence Context (Optional)
              </label>
              <textarea
                value={authorNotes}
                onChange={(e) => setAuthorNotes(e.target.value)}
                rows={2}
                placeholder="Add optional reviewer commentary, investment hypothesis context, or evaluation scope notes..."
                className="w-full px-3 py-1.5 rounded-md bg-surface border border-border text-text-primary text-xs focus:outline-hidden focus:border-accent font-sans leading-relaxed resize-none"
              />
            </div>
          </div>

          {/* Navigation Tabs for Preview */}
          <div className="flex items-center justify-between border-b border-border pb-1">
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setPreviewTab("RECORDS")}
                className={`px-3 py-1 rounded-t-md text-xs font-semibold transition-colors border-b-2 ${
                  previewTab === "RECORDS"
                    ? "border-accent text-accent bg-surface-raised/30"
                    : "border-transparent text-text-secondary hover:text-text-primary"
                }`}
              >
                Selected Records ({selectedIssues.length})
              </button>
              <button
                type="button"
                onClick={() => setPreviewTab("CONTINUITY")}
                className={`px-3 py-1 rounded-t-md text-xs font-semibold transition-colors border-b-2 flex items-center gap-1.5 ${
                  previewTab === "CONTINUITY"
                    ? "border-accent text-accent bg-surface-raised/30"
                    : "border-transparent text-text-secondary hover:text-text-primary"
                }`}
              >
                <span>Investigation Continuity &amp; Next Steps</span>
                <span className="text-[10px] px-1.5 py-0.2 rounded bg-accent/20 text-accent font-mono font-bold">
                  {continuitySummary.nextActionItems.length + continuitySummary.waitingItems.length + continuitySummary.reopenedItems.length}
                </span>
              </button>
              <button
                type="button"
                onClick={() => setPreviewTab("PREVIEW_RAW")}
                className={`px-3 py-1 rounded-t-md text-xs font-semibold transition-colors border-b-2 ${
                  previewTab === "PREVIEW_RAW"
                    ? "border-accent text-accent bg-surface-raised/30"
                    : "border-transparent text-text-secondary hover:text-text-primary"
                }`}
              >
                Export Preview ({exportFormat.toUpperCase()})
              </button>
            </div>

            {selectedIssues.length > 0 && onClearSelection && (
              <button
                type="button"
                onClick={onClearSelection}
                className="text-[11px] text-text-muted hover:text-rose-400 underline font-mono"
              >
                Clear Selection
              </button>
            )}
          </div>

          {/* Tab 1: Records Review List */}
          {previewTab === "RECORDS" && (
            <div className="space-y-3">
              {dossier.items.length === 0 ? (
                <div className="p-8 text-center border border-dashed border-border rounded-lg bg-surface-raised/20 space-y-2">
                  <span className="text-3xl block">📋</span>
                  <p className="font-bold text-xs text-text-primary">No Records Selected for Dossier</p>
                  <p className="text-[11px] text-text-muted max-w-md mx-auto">
                    Select follow-up issues in the research workspace using checkboxes, or click &ldquo;Select All Matching&rdquo; to curate an exportable dossier.
                  </p>
                </div>
              ) : (
                dossier.items.map((item, idx) => {
                  const { issue, provenance } = item;
                  return (
                    <div
                      key={issue.id}
                      className="p-3.5 rounded-lg bg-surface border border-border/80 hover:border-border transition-all flex flex-col gap-3 text-xs"
                    >
                      <div className="space-y-2 flex-1 min-w-0">
                        {/* Title and Badges */}
                        <div className="flex flex-wrap items-center gap-1.5">
                          <span className="font-mono text-accent font-bold text-[11px]">#{idx + 1}</span>
                          <span className="font-bold text-text-primary text-[12px]">{issue.title}</span>
                          <span className="text-[9.5px] px-1.5 py-0.2 rounded border font-semibold bg-accent/15 text-accent border-accent/40">
                            {item.categoryLabel}
                          </span>
                          <span className="text-[9.5px] px-1.5 py-0.2 rounded border font-semibold bg-surface-raised text-text-secondary border-border">
                            {item.statusLabel}
                          </span>
                          {item.outcomeLabel && (
                            <span className="text-[9.5px] px-1.5 py-0.2 rounded border font-semibold bg-emerald-500/15 text-emerald-300 border-emerald-500/40">
                              {item.outcomeLabel}
                            </span>
                          )}
                          <span
                            className={`text-[9.5px] px-1.5 py-0.2 rounded border font-semibold font-mono ${provenance.statusBadgeClass}`}
                            title={provenance.statusDescription}
                          >
                            {provenance.statusBadgeLabel}
                          </span>
                          {issue.residualUncertainty && (
                            <span className="text-[9.5px] px-1.5 py-0.2 rounded bg-amber-500/15 text-amber-300 border border-amber-500/30 font-semibold">
                              ⚠️ Residual Uncertainty
                            </span>
                          )}
                          {item.hasReopenedHistory && (
                            <span className="text-[9.5px] px-1.5 py-0.2 rounded bg-purple-500/15 text-purple-300 border border-purple-500/30 font-bold">
                              🔄 Reopened Inquiry
                            </span>
                          )}
                          {item.isOverdue && (
                            <span className="text-[9.5px] px-1.5 py-0.2 rounded bg-rose-500/15 text-rose-300 border border-rose-500/30 font-bold">
                              ⏰ Overdue
                            </span>
                          )}
                        </div>

                        {/* Scheme Identity Context */}
                        <div className="text-[10.5px] text-text-secondary flex flex-wrap items-center gap-1.5">
                          <strong className="text-text-primary">{item.schemeName}</strong>
                          <span>•</span>
                          <span>Option #{item.schemeOptionId || item.schemeId} ({item.planType} • {item.optionType})</span>
                          {item.amfiCode && (
                            <>
                              <span>•</span>
                              <span className="font-mono text-text-muted">AMFI: {item.amfiCode}</span>
                            </>
                          )}
                          {item.isin && (
                            <>
                              <span>•</span>
                              <span className="font-mono text-text-muted">ISIN: {item.isin}</span>
                            </>
                          )}
                          {item.briefSectionLabel && (
                            <>
                              <span>•</span>
                              <span className="text-accent">Sec {issue.briefSection} ({item.briefSectionLabel})</span>
                            </>
                          )}
                        </div>

                        {/* Question Preview */}
                        <div className="text-[11px] text-text-secondary leading-relaxed pl-2 border-l-2 border-accent/40 font-sans">
                          <span className="font-semibold text-text-primary">Question / Concern: </span>
                          {issue.questionOrReason || <em className="text-text-muted">No question recorded.</em>}
                        </div>

                        {/* Outcome Note & Residual Uncertainty */}
                        {issue.resolutionNote && (
                          <div className="text-[11px] text-text-secondary leading-relaxed pl-2 border-l-2 border-emerald-500/50 font-sans">
                            <span className="font-semibold text-emerald-400">Recorded Outcome: </span>
                            {issue.resolutionNote}
                          </div>
                        )}
                        {issue.residualUncertainty && (
                          <div className="text-[10.5px] text-amber-300/90 pl-2 border-l-2 border-amber-500/50 font-sans">
                            <span className="font-bold text-amber-400 font-mono text-[9.5px] uppercase">Residual Uncertainty: </span>
                            {issue.residualUncertainty}
                          </div>
                        )}

                        {/* Preserved Prior Outcome for Reopened Inquiries */}
                        {issue.priorResolutionNote && (
                          <div className="text-[10.5px] text-purple-200/90 pl-2 border-l-2 border-purple-500/50 font-sans bg-purple-500/5 py-1 pr-2 rounded-r">
                            <span className="font-bold text-purple-300 font-mono text-[9.5px] uppercase">Preserved Prior Outcome: </span>
                            {issue.priorResolutionNote}
                            {issue.priorEvidenceRunId && (
                              <span className="text-purple-400 font-mono text-[9px] ml-1.5">
                                (Prior Run #{issue.priorEvidenceRunId}{issue.priorEvidenceAsOfDate ? ` as of ${issue.priorEvidenceAsOfDate}` : ""})
                              </span>
                            )}
                          </div>
                        )}

                        {/* Next Action & Target Date */}
                        {issue.nextAction && (
                          <div className="text-[11px] bg-accent/5 border border-accent/20 rounded p-2 text-text-primary flex flex-col sm:flex-row sm:items-center justify-between gap-1.5">
                            <div>
                              <span className="font-bold text-accent font-mono text-[10px] uppercase block">Recorded Next Action:</span>
                              <span>{issue.nextAction}</span>
                            </div>
                            {issue.targetDate && (
                              <div className="text-right shrink-0">
                                <span className={`font-mono text-[10px] px-1.5 py-0.5 rounded border ${
                                  item.isOverdue
                                    ? "bg-rose-500/15 text-rose-300 border-rose-500/30 font-bold"
                                    : "bg-surface-raised text-text-secondary border-border"
                                }`}>
                                  Target: {issue.targetDate}
                                </span>
                              </div>
                            )}
                          </div>
                        )}

                        {/* Phase F22: Version Context & Active Evidence Comparison Strip */}
                        <div className="p-2 rounded bg-surface-raised/40 border border-border/60 text-[10px] font-mono space-y-1">
                          <div className="flex flex-wrap items-center justify-between gap-2 text-text-muted">
                            <div className="flex flex-wrap items-center gap-1.5">
                              <span>Cutoff: <strong className="text-text-primary">{issue.evidenceAsOfDate || "Not recorded"}</strong></span>
                              <span>•</span>
                              <span>Run: <strong className="text-text-primary">{issue.evidenceRunId ? `#${issue.evidenceRunId}` : "Not recorded"}</strong></span>
                              <span>•</span>
                              <span>Created: {issue.createdAt.slice(0, 10)}</span>
                              {issue.updatedAt && issue.updatedAt !== issue.createdAt && (
                                <>
                                  <span>•</span>
                                  <span>Updated: {issue.updatedAt.slice(0, 10)}</span>
                                </>
                              )}
                              {issue.reopenedAt && (
                                <>
                                  <span>•</span>
                                  <span className="text-purple-300 font-bold">Reopened: {issue.reopenedAt.slice(0, 10)}</span>
                                </>
                              )}
                            </div>
                            <div className="flex items-center gap-1.5">
                              <span>Active App Cutoff:</span>
                              <strong className={item.isCutoffDrifted ? "text-amber-300 font-bold" : "text-text-secondary"}>
                                {item.activeNavDate || "Not loaded"}
                              </strong>
                              {item.activeRunId && (
                                <span className="text-text-muted">(Run #{item.activeRunId})</span>
                              )}
                            </div>
                          </div>

                          {/* Version Drift Alerts */}
                          {item.isCutoffDrifted && (
                            <div className="text-amber-300 bg-amber-500/10 px-1.5 py-0.5 rounded border border-amber-500/25 flex items-center gap-1">
                              <span>⚠️</span>
                              <span>Observation Cutoff Drift: Finding evaluated as of {issue.evidenceAsOfDate}; active workspace has observations through {item.activeNavDate}. Verification against active data advised.</span>
                            </div>
                          )}
                          {item.isPreservedWithoutWatchlist && (
                            <div className="text-text-muted bg-surface px-1.5 py-0.5 rounded border border-border flex items-center gap-1">
                              <span>🔒</span>
                              <span>Preserved Record: Fund option is not in active saved workspace. Direct Brief navigation is unavailable; historical record is preserved without substitution.</span>
                            </div>
                          )}
                        </div>
                      </div>

                      {/* Record Action Buttons: Resume in Brief, Edit/Reassess, Remove */}
                      <div className="flex flex-wrap items-center justify-between pt-2 border-t border-border/50 gap-2">
                        <div className="flex flex-wrap items-center gap-2">
                          {onNavigateToBrief && !item.isPreservedWithoutWatchlist && (
                            <button
                              type="button"
                              onClick={() => {
                                onClose();
                                onNavigateToBrief(
                                  issue.schemeId,
                                  issue.schemeOptionId,
                                  issue.briefSection,
                                  issue
                                );
                              }}
                              className="btn btn-secondary btn-xs text-[10.5px] font-mono flex items-center gap-1 hover:text-accent font-semibold"
                              title={`Resume investigation in Fund Research Brief (Section ${issue.briefSection || "A"})`}
                            >
                              <span>Resume in Brief {issue.briefSection ? `(Sec ${issue.briefSection})` : ""}</span>
                              <span>↗</span>
                            </button>
                          )}
                          {item.isPreservedWithoutWatchlist && (
                            <span className="text-[10px] font-mono text-text-muted italic">
                              Brief navigation unavailable (fund option not in active workspace)
                            </span>
                          )}
                          {onOpenIssueModal && (
                            <button
                              type="button"
                              onClick={() => {
                                onClose();
                                onOpenIssueModal(issue);
                              }}
                              className="btn btn-ghost btn-xs text-[10.5px] font-mono text-text-secondary hover:text-text-primary border border-border hover:bg-surface-raised"
                              title="Open issue editing modal to update status, outcome, or next actions"
                            >
                              <span>Edit / Reassess ✎</span>
                            </button>
                          )}
                        </div>

                        {onRemoveIssueFromSelection && (
                          <button
                            type="button"
                            onClick={() => onRemoveIssueFromSelection(issue.id)}
                            className="px-2 py-0.5 rounded bg-surface-raised hover:bg-rose-500/10 text-text-muted hover:text-rose-400 border border-border hover:border-rose-500/30 text-[10.5px] font-mono transition-colors"
                            title="Remove this record from the exported dossier"
                          >
                            Remove ✕
                          </button>
                        )}
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          )}

          {/* Tab 2: Phase F22 Investigation Continuity & Action Roadmap */}
          {previewTab === "CONTINUITY" && (
            <div className="space-y-4">
              {/* Continuity Dashboard Summary Banner */}
              <div className="p-3.5 rounded-lg bg-surface-raised/40 border border-border space-y-2">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="font-mono text-accent font-bold text-xs uppercase tracking-wider">
                    Investigation Continuity &amp; Action Roadmap
                  </span>
                  <span className="text-[10px] font-mono text-text-muted">
                    Deterministic Status Resolution • Zero Inferred Transitions
                  </span>
                </div>
                <p className="text-xs text-text-secondary leading-relaxed">
                  Track open diligence dependencies, outstanding next actions, and reopened inquiry histories across your selected dossier records. Use direct action links to continue diligence without losing historical context.
                </p>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 pt-2">
                  <div className="p-2 rounded bg-surface border border-border">
                    <span className="text-[9.5px] font-mono text-text-muted uppercase block">Action Required</span>
                    <span className="text-base font-bold font-mono text-amber-300">
                      {continuitySummary.openCount + continuitySummary.inProgressCount}
                    </span>
                  </div>
                  <div className="p-2 rounded bg-surface border border-border">
                    <span className="text-[9.5px] font-mono text-text-muted uppercase block">Waiting on Dependency</span>
                    <span className="text-base font-bold font-mono text-purple-300">
                      {continuitySummary.waitingCount}
                    </span>
                  </div>
                  <div className="p-2 rounded bg-surface border border-border">
                    <span className="text-[9.5px] font-mono text-text-muted uppercase block">Reopened Inquiries</span>
                    <span className="text-base font-bold font-mono text-accent">
                      {continuitySummary.reopenedCount}
                    </span>
                  </div>
                  <div className="p-2 rounded bg-surface border border-border">
                    <span className="text-[9.5px] font-mono text-text-muted uppercase block">Historical Outcomes</span>
                    <span className="text-base font-bold font-mono text-emerald-400">
                      {continuitySummary.resolvedHistoricalCount + continuitySummary.dismissedHistoricalCount}
                    </span>
                  </div>
                </div>
              </div>

              {/* Outstanding Next Actions List */}
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <h3 className="text-xs font-bold text-text-primary uppercase tracking-wide font-mono flex items-center gap-1.5">
                    <span>📌 Outstanding Diligence Next Actions</span>
                    <span className="text-[10px] px-1.5 py-0.2 rounded bg-accent/20 text-accent font-mono font-bold">
                      {continuitySummary.nextActionItems.length}
                    </span>
                  </h3>
                </div>

                {continuitySummary.nextActionItems.length === 0 ? (
                  <div className="p-4 rounded-lg bg-surface border border-dashed border-border text-center text-xs text-text-muted">
                    No open or in-progress inquiries with outstanding next actions in this dossier.
                  </div>
                ) : (
                  <div className="space-y-2">
                    {continuitySummary.nextActionItems.map((act) => {
                      const matchingItem = dossier.items.find((i) => i.issue.id === act.issueId);
                      const isPreserved = matchingItem?.isPreservedWithoutWatchlist;
                      return (
                        <div
                          key={act.issueId}
                          className="p-3 rounded-lg bg-surface border border-border flex flex-col sm:flex-row sm:items-start justify-between gap-3 text-xs"
                        >
                          <div className="space-y-1.5 flex-1 min-w-0">
                            <div className="flex flex-wrap items-center gap-1.5">
                              <span className="font-bold text-text-primary text-[11.5px]">{act.title}</span>
                              <span className="text-[9px] px-1.5 py-0.2 rounded bg-surface-raised border border-border font-mono text-text-secondary">
                                {act.status}
                              </span>
                              <span className="text-[9px] px-1.5 py-0.2 rounded bg-accent/15 border border-accent/40 font-mono text-accent">
                                {act.categoryLabel}
                              </span>
                              {act.isOverdue && (
                                <span className="text-[9px] px-1.5 py-0.2 rounded bg-rose-500/15 border border-rose-500/30 font-mono text-rose-300 font-bold">
                                  OVERDUE
                                </span>
                              )}
                            </div>
                            <div className="text-[10.5px] text-text-secondary flex flex-wrap items-center gap-1.5">
                              <strong className="text-text-primary">{act.schemeName}</strong>
                              <span>•</span>
                              <span>Option #{act.schemeOptionId} ({act.planType} • {act.optionType})</span>
                              {act.briefSection && (
                                <>
                                  <span>•</span>
                                  <span className="text-accent font-mono">Sec {act.briefSection}</span>
                                </>
                              )}
                            </div>
                            <div className="p-2 rounded bg-accent/5 border border-accent/20 text-[11px] text-text-primary font-sans leading-relaxed">
                              <span className="font-bold text-accent font-mono text-[9.5px] uppercase block">Next Action:</span>
                              {act.nextAction}
                            </div>
                            {act.targetDate && (
                              <div className="text-[10px] font-mono text-text-muted">
                                Target Completion Date: <strong className={act.isOverdue ? "text-rose-300" : "text-text-secondary"}>{act.targetDate}</strong>
                              </div>
                            )}
                          </div>

                          <div className="shrink-0 flex sm:flex-col gap-1.5 items-end">
                            {onNavigateToBrief && !isPreserved && (
                              <button
                                type="button"
                                onClick={() => {
                                  onClose();
                                  if (matchingItem) {
                                    onNavigateToBrief(
                                      matchingItem.issue.schemeId,
                                      matchingItem.issue.schemeOptionId,
                                      matchingItem.issue.briefSection,
                                      matchingItem.issue
                                    );
                                  }
                                }}
                                className="btn btn-secondary btn-xs text-[10px] font-mono font-semibold"
                                title="Resume in Fund Research Brief"
                              >
                                Resume ↗
                              </button>
                            )}
                            {onOpenIssueModal && matchingItem && (
                              <button
                                type="button"
                                onClick={() => {
                                  onClose();
                                  onOpenIssueModal(matchingItem.issue);
                                }}
                                className="btn btn-ghost btn-xs text-[10px] font-mono border border-border"
                                title="Edit issue"
                              >
                                Edit ✎
                              </button>
                            )}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>

              {/* Inquiries Waiting on Dependencies */}
              {continuitySummary.waitingItems.length > 0 && (
                <div className="space-y-2">
                  <h3 className="text-xs font-bold text-purple-300 uppercase tracking-wide font-mono flex items-center gap-1.5">
                    <span>⏳ Inquiries Waiting on External Dependencies</span>
                    <span className="text-[10px] px-1.5 py-0.2 rounded bg-purple-500/20 text-purple-300 font-mono font-bold">
                      {continuitySummary.waitingItems.length}
                    </span>
                  </h3>
                  <div className="space-y-2">
                    {continuitySummary.waitingItems.map((item) => {
                      const matchingItem = dossier.items.find((i) => i.issue.id === item.issueId);
                      return (
                        <div
                          key={item.issueId}
                          className="p-3 rounded-lg bg-surface border border-purple-500/30 flex flex-col sm:flex-row sm:items-start justify-between gap-3 text-xs"
                        >
                          <div className="space-y-1 flex-1 min-w-0">
                            <div className="flex flex-wrap items-center gap-1.5">
                              <span className="font-bold text-text-primary text-[11.5px]">{item.title}</span>
                              <span className="text-[9px] px-1.5 py-0.2 rounded bg-purple-500/15 border border-purple-500/30 font-mono text-purple-300 font-bold">
                                WAITING
                              </span>
                            </div>
                            <div className="text-[10.5px] text-text-secondary">
                              <strong>{item.schemeName}</strong> ({item.planType} • {item.optionType})
                            </div>
                            <p className="text-[11px] text-text-secondary italic">
                              Waiting Condition: {item.questionOrReason || "External dependency pending."}
                            </p>
                            {item.nextAction && (
                              <div className="text-[10.5px] text-accent">
                                <span className="font-bold font-mono">Next step when resolved: </span>
                                {item.nextAction}
                              </div>
                            )}
                          </div>
                          {onOpenIssueModal && matchingItem && (
                            <button
                              type="button"
                              onClick={() => {
                                onClose();
                                onOpenIssueModal(matchingItem.issue);
                              }}
                              className="btn btn-ghost btn-xs text-[10px] font-mono border border-border shrink-0"
                            >
                              Update Status ✎
                            </button>
                          )}
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}

              {/* Reopened Inquiries History */}
              {continuitySummary.reopenedItems.length > 0 && (
                <div className="space-y-2">
                  <h3 className="text-xs font-bold text-accent uppercase tracking-wide font-mono flex items-center gap-1.5">
                    <span>🔄 Reopened Inquiries with Preserved Prior Outcomes</span>
                    <span className="text-[10px] px-1.5 py-0.2 rounded bg-accent/20 text-accent font-mono font-bold">
                      {continuitySummary.reopenedItems.length}
                    </span>
                  </h3>
                  <div className="space-y-2">
                    {continuitySummary.reopenedItems.map((item) => (
                      <div
                        key={item.issueId}
                        className="p-3 rounded-lg bg-surface border border-accent/30 space-y-1 text-xs"
                      >
                        <div className="flex flex-wrap items-center justify-between gap-1.5">
                          <span className="font-bold text-text-primary text-[11.5px]">{item.title}</span>
                          {item.reopenedAt && (
                            <span className="text-[10px] font-mono text-purple-300">
                              Reopened: {item.reopenedAt.slice(0, 10)}
                            </span>
                          )}
                        </div>
                        <div className="text-[10.5px] text-text-secondary">
                          <strong>{item.schemeName}</strong> ({item.planType} • {item.optionType})
                        </div>
                        <div className="p-2 rounded bg-surface-raised/40 border border-border/80 text-[10.5px] font-sans space-y-1">
                          <div className="font-mono text-[9.5px] uppercase font-bold text-emerald-400">
                            Preserved Historical Outcome: {item.priorOutcomeLabel || "Documented"}
                          </div>
                          <div className="text-text-secondary leading-relaxed">
                            {item.priorResolutionNote || "No prior outcome note recorded."}
                          </div>
                          {item.priorEvidenceRunId && (
                            <div className="text-[9.5px] font-mono text-text-muted">
                              Evaluated under Run #{item.priorEvidenceRunId}
                              {item.priorEvidenceAsOfDate ? ` as of ${item.priorEvidenceAsOfDate}` : ""}
                            </div>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Epistemic Continuity Safeguard Note */}
              <div className="p-3 rounded-lg bg-surface-raised/30 border border-border text-[11px] text-text-muted font-sans space-y-1">
                <span className="font-bold font-mono text-[10px] text-text-secondary uppercase block">
                  Auditable Continuity &amp; Version Safeguard
                </span>
                <p className="leading-relaxed">
                  Continuity tracking does not alter recorded historical outcomes or calculation run IDs. Reopening an inquiry creates a continuation branch while strictly preserving the previous outcome and prior calculation lineage.
                </p>
              </div>
            </div>
          )}

          {/* Tab 3: Export Raw Preview */}
          {previewTab === "PREVIEW_RAW" && (
            <div className="space-y-2">
              <div className="flex items-center justify-between text-xs text-text-muted font-mono">
                <span>Format: {exportFormat === "md" ? "GitHub Flavored Markdown (.md)" : "Self-Contained HTML5 (.html)"}</span>
                <span>Length: {generatedContent.length.toLocaleString()} characters</span>
              </div>
              <pre className="p-4 rounded-lg bg-background border border-border text-text-secondary text-[11px] font-mono max-h-[50vh] overflow-auto whitespace-pre-wrap leading-relaxed select-all">
                {generatedContent}
              </pre>
            </div>
          )}
        </div>

        {/* Modal Footer Actions */}
        <div className="px-5 py-3 border-t border-border bg-surface-raised/40 flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={handleCopyToClipboard}
              disabled={selectedIssues.length === 0}
              className="btn btn-secondary btn-sm text-xs font-mono disabled:opacity-50"
              title="Copy markdown export to clipboard"
            >
              {copyFeedback ? "✓ Copied to Clipboard!" : "📋 Copy Markdown"}
            </button>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onClose}
              className="btn btn-ghost btn-sm text-xs text-text-secondary"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={handleDownload}
              disabled={selectedIssues.length === 0}
              className="btn btn-primary btn-sm text-xs font-semibold flex items-center gap-1.5 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <span>Download Dossier ({exportFormat.toUpperCase()})</span>
              <span>⤓</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
