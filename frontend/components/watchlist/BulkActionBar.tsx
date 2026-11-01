"use client";

import { useState } from "react";
import { WatchlistResponse, DueDiligenceStatus, DUE_DILIGENCE_STATUS_MAP } from "@/lib/api/investor";
import {
  BulkActionType,
  BulkActionPreview,
  BulkActionOutcome,
  calculateBulkActionPreview,
  executeBulkStatusUpdate,
  executeBulkShortlistAdd,
  executeBulkShortlistRemove,
} from "@/lib/utils/bulkResearchActions";

export interface BulkActionBarProps {
  selectedItems: WatchlistResponse[];
  visibleItems: WatchlistResponse[];
  currentShortlistKeys: string[];
  isOperating: boolean;
  onClearSelection: () => void;
  onSelectAllVisible: () => void;
  onReconcileToVisible: () => void;
  onUpdateInvestigationStatus: (
    schemeId: number,
    schemeOptionId: number | null | undefined,
    status: DueDiligenceStatus
  ) => void | Promise<void>;
  onUpdateShortlist: (newShortlist: string[]) => void;
  onLaunchCompare: (items: WatchlistResponse[]) => void;
  outcome: BulkActionOutcome | null;
  onDismissOutcome: () => void;
  onSetOutcome: (outcome: BulkActionOutcome | null) => void;
  onDeselectKeys: (keys: string[]) => void;
}

export function BulkActionBar({
  selectedItems,
  visibleItems,
  currentShortlistKeys,
  isOperating,
  onClearSelection,
  onSelectAllVisible,
  onReconcileToVisible,
  onUpdateInvestigationStatus,
  onUpdateShortlist,
  onLaunchCompare,
  outcome,
  onDismissOutcome,
  onSetOutcome,
  onDeselectKeys,
}: BulkActionBarProps) {
  const [activePreview, setActivePreview] = useState<BulkActionPreview | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [statusMenuOpen, setStatusMenuOpen] = useState(false);
  const [shortlistMenuOpen, setShortlistMenuOpen] = useState(false);
  const [shortlistAddMode, setShortlistAddMode] = useState<"fill_available" | "replace_all">("fill_available");

  const selectedCount = selectedItems.length;
  const visibleSelectedCount = selectedItems.filter((s) =>
    visibleItems.some(
      (v) => v.schemeId === s.schemeId && v.schemeOptionId === s.schemeOptionId
    )
  ).length;
  const hiddenCount = selectedCount - visibleSelectedCount;

  // Trigger preview for an action
  const handleInitiateAction = (action: BulkActionType) => {
    setStatusMenuOpen(false);
    setShortlistMenuOpen(false);
    const preview = calculateBulkActionPreview(
      action,
      selectedItems,
      currentShortlistKeys,
      4
    );
    setActivePreview(preview);
  };

  // Confirm and execute the previewed action
  const handleConfirmAction = async () => {
    if (!activePreview || isSubmitting) return;
    setIsSubmitting(true);

    try {
      if (activePreview.targetStatus) {
        // Status transition
        const resultOutcome = await executeBulkStatusUpdate(
          activePreview,
          onUpdateInvestigationStatus
        );
        onSetOutcome(resultOutcome);

        // Deselect successful items, retain failed items for retry
        const successfulKeys = activePreview.eligibleItems
          .filter((item) => !resultOutcome.failedKeys.includes(item.schemeOptionId ? `opt-${item.schemeOptionId}` : `scheme-${item.schemeId}`))
          .map((item) => (item.schemeOptionId ? `opt-${item.schemeOptionId}` : `scheme-${item.schemeId}`));

        if (successfulKeys.length > 0) {
          onDeselectKeys(successfulKeys);
        }
      } else if (activePreview.action === "SHORTLIST_ADD") {
        // Add to shortlist
        const { newShortlist, outcome: resultOutcome } = executeBulkShortlistAdd(
          activePreview,
          currentShortlistKeys,
          shortlistAddMode,
          4
        );
        onUpdateShortlist(newShortlist);
        onSetOutcome(resultOutcome);

        // Deselect all eligible items that were processed
        const eligibleKeys = activePreview.eligibleItems.map((i) =>
          i.schemeOptionId ? `opt-${i.schemeOptionId}` : `scheme-${i.schemeId}`
        );
        onDeselectKeys(eligibleKeys);
      } else if (activePreview.action === "SHORTLIST_REMOVE") {
        // Remove from shortlist
        const { newShortlist, outcome: resultOutcome } = executeBulkShortlistRemove(
          activePreview,
          currentShortlistKeys
        );
        onUpdateShortlist(newShortlist);
        onSetOutcome(resultOutcome);

        const eligibleKeys = activePreview.eligibleItems.map((i) =>
          i.schemeOptionId ? `opt-${i.schemeOptionId}` : `scheme-${i.schemeId}`
        );
        onDeselectKeys(eligibleKeys);
      }
    } finally {
      setIsSubmitting(false);
      setActivePreview(null);
    }
  };

  if (selectedCount === 0 && !outcome) {
    return null;
  }

  return (
    <div className="mb-6 space-y-3">
      {/* 1. Operation Outcome Feedback Banner */}
      {outcome && (
        <div
          role="region"
          aria-label="Bulk action outcome notification"
          className={`panel p-4 rounded-xl border transition-all ${
            outcome.failedCount > 0
              ? "bg-rose-950/20 border-rose-500/40 text-rose-200"
              : outcome.successCount > 0
              ? "bg-emerald-950/20 border-emerald-500/40 text-emerald-200"
              : "bg-surface-raised border-border text-text-secondary"
          }`}
        >
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div className="flex items-start gap-3">
              <span className="text-lg shrink-0 mt-0.5">
                {outcome.failedCount > 0
                  ? "⚠️"
                  : outcome.successCount > 0
                  ? "✓"
                  : "ℹ️"}
              </span>
              <div className="space-y-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-xs font-mono uppercase font-bold tracking-wider">
                    {outcome.actionLabel}:
                  </span>
                  <span className="text-xs font-semibold text-text-primary">
                    {outcome.successCount} succeeded
                  </span>
                  {outcome.skippedCount > 0 && (
                    <span className="text-xs text-text-tertiary">
                      • {outcome.skippedCount} skipped
                    </span>
                  )}
                  {outcome.failedCount > 0 && (
                    <span className="text-xs text-rose-400 font-bold">
                      • {outcome.failedCount} failed
                    </span>
                  )}
                </div>
                <p className="text-xs text-text-secondary leading-relaxed">
                  {outcome.persistenceNote}
                </p>
                {outcome.skippedItems.length > 0 && (
                  <div className="pt-1 text-[11px] font-mono text-text-muted flex flex-wrap gap-2">
                    <span className="font-semibold text-text-tertiary">Skipped Details:</span>
                    {outcome.skippedItems.slice(0, 3).map((item) => (
                      <span key={item.key} className="bg-surface px-1.5 py-0.5 rounded border border-border/50">
                        {item.schemeName}: {item.reason}
                      </span>
                    ))}
                    {outcome.skippedItems.length > 3 && (
                      <span>(+ {outcome.skippedItems.length - 3} more)</span>
                    )}
                  </div>
                )}
                {outcome.failedItems.length > 0 && (
                  <div className="pt-1 text-[11px] font-mono text-rose-300">
                    <span className="font-semibold">Failed items remain selected for retry: </span>
                    {outcome.failedItems.map((f) => f.schemeName).join(", ")}
                  </div>
                )}
              </div>
            </div>
            <div className="flex items-center gap-2 shrink-0 self-end sm:self-center">
              <button
                type="button"
                onClick={onDismissOutcome}
                className="btn btn-ghost btn-xs text-xs text-text-muted hover:text-text-primary px-2.5 py-1"
                aria-label="Dismiss notification"
              >
                ✕ Dismiss
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 2. Floating / Sticky Selection Bar */}
      {selectedCount > 0 && (
        <div
          role="toolbar"
          aria-label="Research Workspace Bulk Action Toolbar"
          className="panel p-3.5 bg-surface-navy-raised/95 border-2 border-accent/60 rounded-xl shadow-xl flex flex-col md:flex-row md:items-center justify-between gap-3 font-mono text-xs"
        >
          {/* Left: Count, Visibility Info & Reconciliation */}
          <div className="flex flex-wrap items-center gap-3">
            <div className="flex items-center gap-2">
              <span className="flex h-6 w-6 items-center justify-center rounded-md bg-accent/20 text-accent font-bold text-xs">
                {selectedCount}
              </span>
              <span className="font-bold text-text-cream-bright text-xs">
                {selectedCount === 1 ? "1 Investigation Selected" : `${selectedCount} Investigations Selected`}
              </span>
            </div>

            {/* Visibility Badge & Indicator */}
            {hiddenCount > 0 ? (
              <div className="flex items-center gap-1.5 px-2 py-0.5 rounded bg-amber-500/15 border border-amber-500/40 text-amber-300 text-[11px]">
                <span>⚠️ {visibleSelectedCount} visible, {hiddenCount} hidden by filters</span>
                <button
                  type="button"
                  onClick={onReconcileToVisible}
                  className="ml-1 text-accent hover:underline font-bold"
                  title="Deselect items that are not currently visible under active filters"
                >
                  [Keep Visible Only]
                </button>
              </div>
            ) : (
              <span className="text-[11px] text-text-tertiary">
                (All {selectedCount} visible in current view)
              </span>
            )}

            {/* Selection Toggles */}
            <div className="flex items-center gap-2 pl-1 border-l border-border/60">
              <button
                type="button"
                onClick={onSelectAllVisible}
                disabled={isOperating}
                className="btn btn-ghost btn-xs text-[11px] text-accent hover:underline py-0.5"
                title="Select all visible mutual funds under current filters"
              >
                Select Visible ({visibleItems.length})
              </button>
              <button
                type="button"
                onClick={onClearSelection}
                disabled={isOperating}
                className="btn btn-ghost btn-xs text-[11px] text-text-muted hover:text-text-primary py-0.5"
                title="Clear current selection"
              >
                Clear
              </button>
            </div>
          </div>

          {/* Right: Bulk Action Dropdowns & Primary Triggers */}
          <div className="flex flex-wrap items-center gap-2">
            {/* Direct Compare Trigger if 2 to 4 candidates selected */}
            {selectedCount >= 2 && selectedCount <= 4 && (
              <button
                type="button"
                onClick={() => onLaunchCompare(selectedItems)}
                disabled={isOperating}
                className="btn btn-primary btn-xs font-semibold flex items-center gap-1.5 shadow-sm"
                title="Open side-by-side evidence comparison using the selected mutual funds"
              >
                <span>Compare {selectedCount} Selected</span>
                <span className="text-xs">↗</span>
              </button>
            )}

            {/* Due Diligence Status Transition Menu */}
            <div className="relative">
              <button
                type="button"
                onClick={() => {
                  setStatusMenuOpen((prev) => !prev);
                  setShortlistMenuOpen(false);
                }}
                disabled={isOperating}
                className="btn btn-secondary btn-xs flex items-center gap-1.5 text-text-primary hover:border-accent"
                aria-haspopup="true"
                aria-expanded={statusMenuOpen}
              >
                <span>Set Status ({selectedCount})</span>
                <span className="text-[10px]">▼</span>
              </button>

              {statusMenuOpen && (
                <div
                  className="absolute right-0 mt-1 w-56 rounded-lg bg-surface border border-accent/40 shadow-2xl py-1.5 z-30 space-y-0.5"
                  role="menu"
                >
                  <div className="px-3 py-1 text-[10px] uppercase font-bold text-text-tertiary border-b border-border/50">
                    Bulk Due-Diligence Status
                  </div>
                  {(["TO_INVESTIGATE", "IN_PROGRESS", "NEEDS_FOLLOW_UP", "REVIEWED"] as DueDiligenceStatus[]).map(
                    (st) => {
                      const meta = DUE_DILIGENCE_STATUS_MAP[st];
                      const actionType: BulkActionType =
                        st === "TO_INVESTIGATE"
                          ? "STATUS_TO_INVESTIGATE"
                          : st === "IN_PROGRESS"
                          ? "STATUS_IN_PROGRESS"
                          : st === "NEEDS_FOLLOW_UP"
                          ? "STATUS_NEEDS_FOLLOW_UP"
                          : "STATUS_REVIEWED";

                      return (
                        <button
                          key={st}
                          type="button"
                          role="menuitem"
                          onClick={() => handleInitiateAction(actionType)}
                          className="w-full text-left px-3 py-1.5 text-xs hover:bg-surface-raised flex items-center justify-between gap-2 transition-colors"
                        >
                          <span className="font-semibold text-text-primary">
                            Mark {meta.label}
                          </span>
                          <span className={`text-[9.5px] px-1.5 py-0.2 rounded border font-mono ${meta.badgeClass}`}>
                            {meta.badgeLabel}
                          </span>
                        </button>
                      );
                    }
                  )}
                </div>
              )}
            </div>

            {/* Comparison Shortlist Menu */}
            <div className="relative">
              <button
                type="button"
                onClick={() => {
                  setShortlistMenuOpen((prev) => !prev);
                  setStatusMenuOpen(false);
                }}
                disabled={isOperating}
                className="btn btn-secondary btn-xs flex items-center gap-1.5 text-text-primary hover:border-accent"
                aria-haspopup="true"
                aria-expanded={shortlistMenuOpen}
              >
                <span>Shortlist ({selectedCount})</span>
                <span className="text-[10px]">▼</span>
              </button>

              {shortlistMenuOpen && (
                <div
                  className="absolute right-0 mt-1 w-64 rounded-lg bg-surface border border-accent/40 shadow-2xl py-1.5 z-30 space-y-0.5"
                  role="menu"
                >
                  <div className="px-3 py-1 text-[10px] uppercase font-bold text-text-tertiary border-b border-border/50">
                    Comparative Shortlist (Max 4)
                  </div>
                  <button
                    type="button"
                    role="menuitem"
                    onClick={() => handleInitiateAction("SHORTLIST_ADD")}
                    className="w-full text-left px-3 py-2 text-xs hover:bg-surface-raised flex flex-col gap-0.5 transition-colors"
                  >
                    <span className="font-semibold text-accent">+ Add to Compare Shortlist</span>
                    <span className="text-[10px] text-text-tertiary font-normal">
                      Current: {currentShortlistKeys.length} of 4 slots occupied
                    </span>
                  </button>
                  <button
                    type="button"
                    role="menuitem"
                    onClick={() => handleInitiateAction("SHORTLIST_REMOVE")}
                    className="w-full text-left px-3 py-2 text-xs hover:bg-surface-raised flex flex-col gap-0.5 text-rose-300 hover:text-rose-200 transition-colors"
                  >
                    <span className="font-semibold">- Remove from Shortlist</span>
                    <span className="text-[10px] text-text-tertiary font-normal">
                      Removes selected items if present in shortlist
                    </span>
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* 3. Compact Bulk-Action Confirmation & Preview Modal */}
      {activePreview && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="bulk-preview-title"
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-xs animate-in fade-in"
        >
          <div className="panel w-full max-w-lg bg-surface border-2 border-accent/60 rounded-xl shadow-2xl p-5 space-y-4 font-mono text-xs max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between border-b border-border/60 pb-3">
              <div className="flex items-center gap-2">
                <span className="text-accent text-base">⚡</span>
                <h3 id="bulk-preview-title" className="text-sm font-bold text-text-cream-bright font-sans">
                  {activePreview.actionLabel}
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setActivePreview(null)}
                disabled={isSubmitting}
                className="text-text-muted hover:text-text-primary text-sm p-1"
                aria-label="Close preview dialog"
              >
                ✕
              </button>
            </div>

            {/* Scope & Count Breakdown */}
            <div className="grid grid-cols-3 gap-2 text-center">
              <div className="p-2.5 rounded-lg bg-surface-raised border border-border">
                <span className="text-[10px] text-text-tertiary uppercase block">Selected</span>
                <span className="text-base font-bold text-text-primary mt-0.5 block">
                  {activePreview.totalSelected}
                </span>
              </div>
              <div className="p-2.5 rounded-lg bg-emerald-500/10 border border-emerald-500/30">
                <span className="text-[10px] text-emerald-400 uppercase block">Eligible to Act</span>
                <span className="text-base font-bold text-emerald-300 mt-0.5 block">
                  {activePreview.eligibleCount}
                </span>
              </div>
              <div className="p-2.5 rounded-lg bg-surface-raised border border-border">
                <span className="text-[10px] text-text-tertiary uppercase block">Skipped</span>
                <span className="text-base font-bold text-text-secondary mt-0.5 block">
                  {activePreview.skippedCount}
                </span>
              </div>
            </div>

            {/* Shortlist Limit Constraint Notice & Mode Selector */}
            {activePreview.shortlistLimitWarning && (
              <div className="p-3 rounded-lg bg-amber-500/10 border border-amber-500/40 space-y-2 text-amber-200">
                <div className="flex items-start gap-2">
                  <span className="text-amber-400 font-bold shrink-0">ℹ️</span>
                  <div>
                    <span className="font-bold text-[11px] uppercase tracking-wider block">
                      Shortlist Capacity Constraint (Max 4 Candidates)
                    </span>
                    <p className="text-[11.5px] font-sans mt-0.5 leading-snug">
                      The comparative screener supports up to 4 candidates. Shortlist currently has{" "}
                      <strong>{activePreview.shortlistLimitWarning.currentCount} of 4</strong> slots filled.
                      Adding all {activePreview.shortlistLimitWarning.attemptedAddCount} selected items would exceed capacity by{" "}
                      <strong>{activePreview.shortlistLimitWarning.excessCount}</strong> candidates.
                    </p>
                  </div>
                </div>

                <div className="pt-2 border-t border-amber-500/30 space-y-1.5">
                  <label className="text-[10px] uppercase font-bold text-amber-300 block">
                    Choose resolution method:
                  </label>
                  <label className="flex items-center gap-2 cursor-pointer">
                    <input
                      type="radio"
                      name="shortlist-mode"
                      checked={shortlistAddMode === "fill_available"}
                      onChange={() => setShortlistAddMode("fill_available")}
                      className="accent-[var(--accent)]"
                    />
                    <span>
                      Add first {activePreview.shortlistLimitWarning.availableSlots} candidates (fill available slots)
                    </span>
                  </label>
                  <label className="flex items-center gap-2 cursor-pointer">
                    <input
                      type="radio"
                      name="shortlist-mode"
                      checked={shortlistAddMode === "replace_all"}
                      onChange={() => setShortlistAddMode("replace_all")}
                      className="accent-[var(--accent)]"
                    />
                    <span>Replace existing shortlist with first 4 selected candidates</span>
                  </label>
                </div>
              </div>
            )}

            {/* Skipped Items Disclosure */}
            {activePreview.skippedItems.length > 0 && (
              <div className="p-3 rounded-lg bg-surface-raised border border-border space-y-1.5">
                <span className="text-[10.5px] uppercase font-bold text-text-tertiary block">
                  Skipped Candidates ({activePreview.skippedItems.length}):
                </span>
                <ul className="space-y-1 text-[11px] text-text-secondary max-h-28 overflow-y-auto pr-1">
                  {activePreview.skippedItems.map((item) => (
                    <li key={item.key} className="flex items-start justify-between gap-2 border-b border-border/30 pb-0.5">
                      <span className="text-text-primary font-semibold truncate">{item.schemeName}</span>
                      <span className="text-text-muted shrink-0 text-[10px]">({item.reason})</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {/* Eligible Items Sample List */}
            {activePreview.eligibleItems.length > 0 && (
              <div className="p-3 rounded-lg bg-surface-raised border border-border space-y-1">
                <span className="text-[10.5px] uppercase font-bold text-text-tertiary block">
                  Candidate Targets ({activePreview.eligibleItems.length}):
                </span>
                <div className="flex flex-wrap gap-1.5 max-h-24 overflow-y-auto pr-1 text-[10.5px]">
                  {activePreview.eligibleItems.map((item) => (
                    <span
                      key={item.schemeOptionId ? `opt-${item.schemeOptionId}` : `scheme-${item.schemeId}`}
                      className="px-2 py-0.5 rounded bg-surface border border-border text-text-primary"
                    >
                      {item.schemeName} ({item.planType || "DIR"} • {item.optionType || "GR"})
                    </span>
                  ))}
                </div>
              </div>
            )}

            {/* Epistemic Consequence Notice */}
            <div className="p-3 rounded-lg bg-surface-raised/60 border border-border/80 text-[11px] text-text-secondary leading-relaxed font-sans">
              <span className="text-[10px] font-mono uppercase font-bold text-accent block mb-0.5">
                Epistemic Integrity Disclosure
              </span>
              {activePreview.consequenceNotice}
            </div>

            {/* Dialog Actions */}
            <div className="flex items-center justify-end gap-2 pt-2 border-t border-border/60">
              <button
                type="button"
                onClick={() => setActivePreview(null)}
                disabled={isSubmitting}
                className="btn btn-secondary btn-sm"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleConfirmAction}
                disabled={isSubmitting || activePreview.eligibleCount === 0}
                className="btn btn-primary btn-sm flex items-center gap-1.5 shadow-md"
              >
                {isSubmitting ? (
                  <>
                    <span className="animate-spin text-xs">⟳</span>
                    <span>Updating {activePreview.eligibleCount} Items...</span>
                  </>
                ) : (
                  <span>
                    Confirm {activePreview.actionLabel} ({activePreview.eligibleCount})
                  </span>
                )}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
