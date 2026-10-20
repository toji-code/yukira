"use client";

import React, { useState, useEffect } from "react";
import { WatchlistResponse, WatchlistNoteRequest } from "@/lib/api/investor";

interface ResearchNoteModalProps {
  isOpen: boolean;
  onClose: () => void;
  fund: WatchlistResponse | null;
  onSave: (schemeId: number, note: WatchlistNoteRequest, schemeOptionId?: number | null) => Promise<void>;
  prefill?: {
    reasonSaved?: string;
    investigationQuestion?: string;
    nextStep?: string;
  };
}

export function ResearchNoteModal({
  isOpen,
  onClose,
  fund,
  onSave,
  prefill,
}: ResearchNoteModalProps) {
  const [reasonSaved, setReasonSaved] = useState("");
  const [investigationQuestion, setInvestigationQuestion] = useState("");
  const [nextStep, setNextStep] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (fund) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setReasonSaved(fund.reasonSaved || prefill?.reasonSaved || "");
      setInvestigationQuestion(fund.investigationQuestion || prefill?.investigationQuestion || "");
      setNextStep(fund.nextStep || prefill?.nextStep || "");
      setError(null);
    }
  }, [fund, prefill]);

  if (!isOpen || !fund) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (reasonSaved.length > 1000) {
      setError("Reason saved exceeds the maximum limit of 1,000 characters.");
      return;
    }
    if (investigationQuestion.length > 1000) {
      setError("Investigation question exceeds the maximum limit of 1,000 characters.");
      return;
    }
    if (nextStep.length > 1000) {
      setError("Next step exceeds the maximum limit of 1,000 characters.");
      return;
    }

    try {
      setSaving(true);
      setError(null);
      await onSave(fund.schemeId, {
        reasonSaved: reasonSaved.trim() || undefined,
        investigationQuestion: investigationQuestion.trim() || undefined,
        nextStep: nextStep.trim() || undefined,
      }, fund.schemeOptionId);
      onClose();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to save research note");
    } finally {
      setSaving(false);
    }
  };

  const handleClearNote = async () => {
    try {
      setSaving(true);
      setError(null);
      await onSave(fund.schemeId, {
        reasonSaved: undefined,
        investigationQuestion: undefined,
        nextStep: undefined,
      }, fund.schemeOptionId);
      setReasonSaved("");
      setInvestigationQuestion("");
      setNextStep("");
      onClose();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to clear note");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="note-modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm"
    >
      <div className="panel max-w-lg w-full bg-surface border border-border shadow-2xl rounded-xl overflow-hidden animate-in fade-in zoom-in-95 duration-150">
        <div className="panel-header border-b border-border p-4 flex items-center justify-between">
          <div>
            <span className="eyebrow block">Investor Due Diligence</span>
            <h3 id="note-modal-title" className="text-sm font-semibold text-text-primary mt-0.5">
              Research Note: {fund.schemeName}
            </h3>
            <span className="mono-meta text-[11px] block mt-0.5">
              {fund.schemeCode} • Option #{fund.schemeOptionId || fund.schemeId} ({fund.planType || "DIRECT"} {fund.optionType || "GROWTH"})
            </span>
          </div>
          <button
            onClick={onClose}
            className="btn btn-ghost btn-sm text-text-tertiary hover:text-text-primary"
            aria-label="Close modal"
          >
            ✕
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-4 space-y-4">
          {error && (
            <div className="state-panel-error p-3 text-[12px] font-mono text-critical-fg bg-critical-bg/20 border border-critical-border rounded">
              {error}
            </div>
          )}

          <div>
            <div className="flex items-center justify-between mb-1">
              <label htmlFor="reason-saved" className="field-label text-[12px]">
                Why are you investigating this fund?
              </label>
              <span className="mono-meta text-[10px]">
                {reasonSaved.length}/1000
              </span>
            </div>
            <textarea
              id="reason-saved"
              rows={2}
              maxLength={1000}
              value={reasonSaved}
              onChange={(e) => setReasonSaved(e.target.value)}
              placeholder="e.g. Evaluating 3-year compound annual growth and downside resilience vs benchmark..."
              className="field w-full text-[12.5px] leading-relaxed resize-y"
            />
          </div>

          <div>
            <div className="flex items-center justify-between mb-1">
              <label htmlFor="investigation-question" className="field-label text-[12px]">
                What question remains unanswered?
              </label>
              <span className="mono-meta text-[10px]">
                {investigationQuestion.length}/1000
              </span>
            </div>
            {prefill?.investigationQuestion && fund.investigationQuestion && fund.investigationQuestion !== prefill.investigationQuestion && (
              <div className="mb-2 p-2.5 bg-surface-raised border border-accent/30 rounded text-[11.5px]">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <span className="mono-meta text-accent font-semibold block text-[10px]">Suggested Metric Question:</span>
                    <p className="text-text-primary text-[12px] mt-0.5 leading-snug">{prefill.investigationQuestion}</p>
                  </div>
                  <button
                    type="button"
                    onClick={() => setInvestigationQuestion(prefill.investigationQuestion!)}
                    className="btn btn-secondary btn-xs shrink-0 text-[10.5px]"
                  >
                    Replace with this
                  </button>
                </div>
              </div>
            )}
            <textarea
              id="investigation-question"
              rows={3}
              maxLength={1000}
              value={investigationQuestion}
              onChange={(e) => setInvestigationQuestion(e.target.value)}
              placeholder="e.g. Does the higher upside capture come at the expense of excessive downside semideviation during market drawdowns?"
              className="field w-full text-[12.5px] leading-relaxed resize-y"
            />
          </div>

          <div>
            <div className="flex items-center justify-between mb-1">
              <label htmlFor="next-step" className="field-label text-[12px]">
                Next investigation step (optional)
              </label>
              <span className="mono-meta text-[10px]">
                {nextStep.length}/1000
              </span>
            </div>
            <input
              id="next-step"
              type="text"
              maxLength={1000}
              value={nextStep}
              onChange={(e) => setNextStep(e.target.value)}
              placeholder="e.g. Compare against Parag Parikh Flexi Cap under identical 2024-01-15 cutoff."
              className="field w-full text-[12.5px]"
            />
          </div>

          <div className="border-t border-border pt-3 flex items-center justify-between">
            <div>
              {(fund.reasonSaved || fund.investigationQuestion || fund.nextStep) && (
                <button
                  type="button"
                  onClick={handleClearNote}
                  disabled={saving}
                  className="btn btn-ghost btn-sm text-critical-fg hover:bg-critical-bg/20"
                >
                  Clear Note
                </button>
              )}
            </div>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={onClose}
                disabled={saving}
                className="btn btn-secondary btn-sm"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={saving}
                className="btn btn-primary btn-sm flex items-center gap-1.5"
              >
                {saving && (
                  <svg className="h-3 w-3 animate-spin" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                  </svg>
                )}
                <span>Save Note</span>
              </button>
            </div>
          </div>
        </form>
      </div>
    </div>
  );
}
