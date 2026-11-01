"use client";

import React, { useState, useEffect } from "react";
import {
  WatchlistResponse,
  WatchlistNoteRequest,
  DueDiligenceStatus,
  DUE_DILIGENCE_STATUS_MAP,
  InvestorResearchThesisHistoryDto,
  fetchWatchlistThesisHistory,
} from "@/lib/api/investor";

interface ResearchNoteModalProps {
  isOpen: boolean;
  onClose: () => void;
  fund: WatchlistResponse | null;
  onSave: (schemeId: number, note: WatchlistNoteRequest, schemeOptionId?: number | null) => Promise<void>;
  prefill?: {
    reasonSaved?: string;
    investigationQuestion?: string;
    nextStep?: string;
    workingThesis?: string;
    supportingEvidence?: string;
    reconsiderationConditions?: string;
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
  const [workingThesis, setWorkingThesis] = useState("");
  const [supportingEvidence, setSupportingEvidence] = useState("");
  const [investigationQuestion, setInvestigationQuestion] = useState("");
  const [reconsiderationConditions, setReconsiderationConditions] = useState("");
  const [nextStep, setNextStep] = useState("");
  const [investigationStatus, setInvestigationStatus] = useState<DueDiligenceStatus>("TO_INVESTIGATE");
  const [thesisStatus, setThesisStatus] = useState<string>("ACTIVE_HYPOTHESIS");
  const [associatedFindingId, setAssociatedFindingId] = useState<string>("");
  const [modalTab, setModalTab] = useState<"edit" | "history">("edit");
  const [historyList, setHistoryList] = useState<InvestorResearchThesisHistoryDto[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (modalTab === "history" && fund?.id) {
      setHistoryLoading(true);
      fetchWatchlistThesisHistory(fund.id)
        .then((items) => setHistoryList(items || []))
        .catch(() => setHistoryList([]))
        .finally(() => setHistoryLoading(false));
    }
  }, [modalTab, fund?.id]);

  useEffect(() => {
    if (fund) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setReasonSaved(fund.reasonSaved || prefill?.reasonSaved || "");
      setWorkingThesis(fund.workingThesis || prefill?.workingThesis || "");
      setSupportingEvidence(fund.supportingEvidence || prefill?.supportingEvidence || "");
      setInvestigationQuestion(fund.investigationQuestion || prefill?.investigationQuestion || "");
      setReconsiderationConditions(fund.reconsiderationConditions || prefill?.reconsiderationConditions || "");
      setNextStep(fund.nextStep || prefill?.nextStep || "");
      setInvestigationStatus(fund.investigationStatus || "TO_INVESTIGATE");
      setThesisStatus(fund.thesisStatus || "ACTIVE_HYPOTHESIS");
      setAssociatedFindingId(fund.associatedFindingId || "");
      setError(null);
    }
  }, [fund, prefill]);

  if (!isOpen || !fund) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (reasonSaved.length > 1000) {
      setError("Research objective exceeds the maximum limit of 1,000 characters.");
      return;
    }
    if (workingThesis.length > 1000) {
      setError("Working thesis exceeds the maximum limit of 1,000 characters.");
      return;
    }
    if (supportingEvidence.length > 1000) {
      setError("Supporting evidence exceeds the maximum limit of 1,000 characters.");
      return;
    }
    if (investigationQuestion.length > 1000) {
      setError("Investigation question exceeds the maximum limit of 1,000 characters.");
      return;
    }
    if (reconsiderationConditions.length > 1000) {
      setError("Reconsideration conditions exceed the maximum limit of 1,000 characters.");
      return;
    }
    if (nextStep.length > 1000) {
      setError("Next step exceeds the maximum limit of 1,000 characters.");
      return;
    }

    try {
      setSaving(true);
      setError(null);
      await onSave(
        fund.schemeId,
        {
          reasonSaved: reasonSaved.trim() || undefined,
          workingThesis: workingThesis.trim() || undefined,
          supportingEvidence: supportingEvidence.trim() || undefined,
          investigationQuestion: investigationQuestion.trim() || undefined,
          reconsiderationConditions: reconsiderationConditions.trim() || undefined,
          nextStep: nextStep.trim() || undefined,
          investigationStatus,
          thesisStatus: thesisStatus.trim() || undefined,
          associatedFindingId: associatedFindingId.trim() || undefined,
        },
        fund.schemeOptionId
      );
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
      await onSave(
        fund.schemeId,
        {
          reasonSaved: undefined,
          workingThesis: undefined,
          supportingEvidence: undefined,
          investigationQuestion: undefined,
          reconsiderationConditions: undefined,
          nextStep: undefined,
          investigationStatus: "TO_INVESTIGATE",
        },
        fund.schemeOptionId
      );
      setReasonSaved("");
      setWorkingThesis("");
      setSupportingEvidence("");
      setInvestigationQuestion("");
      setReconsiderationConditions("");
      setNextStep("");
      setInvestigationStatus("TO_INVESTIGATE");
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
      <div className="panel max-w-lg w-full bg-surface border border-border shadow-2xl rounded-xl overflow-hidden animate-in fade-in zoom-in-95 duration-150 max-h-[90vh] flex flex-col">
        <div className="panel-header border-b border-border p-4 flex items-center justify-between shrink-0">
          <div>
            <span className="eyebrow block">Investor Due Diligence</span>
            <h3 id="note-modal-title" className="text-sm font-semibold text-text-primary mt-0.5">
              Research Thesis &amp; Note: {fund.schemeName}
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

        {/* TAB TOGGLE: EDIT vs REVISION HISTORY */}
        <div className="flex border-b border-border bg-surface-raised px-4 pt-2 gap-4 shrink-0">
          <button
            type="button"
            onClick={() => setModalTab("edit")}
            className={`pb-2 text-[12.5px] font-medium border-b-2 transition-colors ${
              modalTab === "edit"
                ? "border-accent text-text-primary font-bold"
                : "border-transparent text-text-secondary hover:text-text-primary"
            }`}
          >
            Edit Thesis &amp; Due Diligence
          </button>
          <button
            type="button"
            onClick={() => setModalTab("history")}
            className={`pb-2 text-[12.5px] font-medium border-b-2 transition-colors flex items-center gap-1.5 ${
              modalTab === "history"
                ? "border-accent text-text-primary font-bold"
                : "border-transparent text-text-secondary hover:text-text-primary"
            }`}
          >
            <span>Revision History</span>
            <span className="text-[10px] bg-surface px-1.5 py-0.5 rounded font-mono border border-border">
              {historyList.length > 0 ? historyList.length : "Audit"}
            </span>
          </button>
        </div>

        {modalTab === "history" ? (
          <div className="p-4 space-y-3 overflow-y-auto flex-1">
            <div className="flex items-center justify-between border-b border-border/70 pb-2">
              <span className="text-xs font-semibold uppercase tracking-wider text-text-secondary">
                Thesis Revision Log (Task 152)
              </span>
              <span className="text-[11px] font-mono text-text-tertiary">
                Append-only audit trail
              </span>
            </div>

            {historyLoading ? (
              <div className="animate-pulse space-y-2 py-4">
                <div className="h-12 bg-surface-raised rounded w-full"></div>
                <div className="h-12 bg-surface-raised rounded w-full"></div>
              </div>
            ) : historyList.length === 0 ? (
              <div className="p-6 text-center text-text-tertiary text-xs bg-surface-raised/40 rounded border border-border/60">
                No prior revisions recorded. Every thesis update is cryptographically appended with timestamp and reason.
              </div>
            ) : (
              <div className="space-y-3">
                {historyList.map((h, i) => (
                  <div
                    key={h.id || i}
                    className="p-3 bg-surface-raised rounded border border-border/80 space-y-2 text-xs"
                  >
                    <div className="flex items-center justify-between font-mono text-[11px] text-text-tertiary border-b border-border/50 pb-1.5">
                      <span>{new Date(h.changedAt).toLocaleString()}</span>
                      {h.newStatus && (
                        <span className="px-1.5 py-0.5 bg-accent/20 text-accent rounded font-bold">
                          {h.newStatus}
                        </span>
                      )}
                    </div>
                    {h.newThesis && (
                      <div>
                        <span className="text-[10px] uppercase font-bold text-text-tertiary block mb-0.5">
                          Thesis Update:
                        </span>
                        <p className="text-text-primary font-mono bg-surface p-2 rounded border border-border/60">
                          {h.newThesis}
                        </p>
                      </div>
                    )}
                    {h.previousThesis && (
                      <div>
                        <span className="text-[10px] uppercase font-bold text-text-tertiary block mb-0.5">
                          Previous Thesis:
                        </span>
                        <p className="text-text-secondary font-mono bg-surface/50 p-2 rounded border border-border/40 line-through opacity-70">
                          {h.previousThesis}
                        </p>
                      </div>
                    )}
                    {h.changeReason && (
                      <div className="text-[11px] text-text-secondary italic">
                        Reason: {h.changeReason}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}

            <div className="pt-3 border-t border-border flex justify-end">
              <button
                type="button"
                onClick={() => setModalTab("edit")}
                className="btn btn-primary btn-sm"
              >
                Back to Edit
              </button>
            </div>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="p-4 space-y-4 overflow-y-auto flex-1">
          {error && (
            <div className="state-panel-error p-3 text-[12px] font-mono text-critical-fg bg-critical-bg/20 border border-critical-border rounded">
              {error}
            </div>
          )}

          {/* Due-Diligence Progress State Selector */}
          <div className="p-3 bg-surface-raised/50 border border-border rounded-lg space-y-2">
            <div className="flex items-center justify-between">
              <label className="field-label text-[12px] font-semibold text-text-primary">
                Due-Diligence Progress State
              </label>
              <span className={`text-[10px] font-mono px-2 py-0.5 rounded border ${DUE_DILIGENCE_STATUS_MAP[investigationStatus].badgeClass}`}>
                {DUE_DILIGENCE_STATUS_MAP[investigationStatus].badgeLabel}
              </span>
            </div>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-1.5 text-xs font-mono">
              {(Object.keys(DUE_DILIGENCE_STATUS_MAP) as DueDiligenceStatus[]).map((statusKey) => {
                const meta = DUE_DILIGENCE_STATUS_MAP[statusKey];
                const isSelected = investigationStatus === statusKey;
                return (
                  <button
                    key={statusKey}
                    type="button"
                    onClick={() => setInvestigationStatus(statusKey)}
                    className={`px-2 py-1.5 rounded text-[11px] font-semibold border text-center transition-all ${
                      isSelected
                        ? meta.colorClass + " ring-1 ring-accent/60 shadow-xs"
                        : "bg-surface border-border/70 text-text-secondary hover:text-text-primary hover:bg-surface-raised"
                    }`}
                  >
                    {meta.label}
                  </button>
                );
              })}
            </div>
            <p className="text-[11px] text-text-tertiary leading-snug">
              {DUE_DILIGENCE_STATUS_MAP[investigationStatus].description}{" "}
              <span className="italic opacity-80">(Personal research workflow record only — does not alter quantitative metrics or imply fund approval).</span>
            </p>
          </div>

          {/* 1. Research Objective (Reason Saved) */}
          <div>
            <div className="flex items-center justify-between mb-1">
              <label htmlFor="reason-saved" className="field-label text-[12px]">
                Why are you investigating this fund? (Research Objective)
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

          {/* 2. Working Thesis / Conclusion (F7 New) */}
          <div className="p-3 bg-surface-raised/40 border border-accent/30 rounded-lg space-y-2">
            <div className="flex items-center justify-between">
              <div>
                <label htmlFor="working-thesis" className="field-label text-[12px] text-accent font-semibold flex items-center gap-1.5">
                  <span>Working Thesis / Hypothesis</span>
                  <span className="status-badge state-candidate text-[9px]">INVESTOR-AUTHORED</span>
                </label>
                <p className="text-[10.5px] text-text-secondary mt-0.5">
                  What do you currently believe the available evidence indicates? (Personal hypothesis, not a financial fact).
                </p>
              </div>
              <span className="mono-meta text-[10px] shrink-0">
                {workingThesis.length}/1000
              </span>
            </div>
            <textarea
              id="working-thesis"
              rows={3}
              maxLength={1000}
              value={workingThesis}
              onChange={(e) => setWorkingThesis(e.target.value)}
              placeholder="e.g. Robust downside resilience during market drawdowns offsets modest lag in strong bull rallies, indicating suitability as a defensive core flexi-cap holding."
              className="field w-full text-[12.5px] leading-relaxed resize-y"
            />

            {/* Thesis Lifecycle Status Selector */}
            <div className="pt-2 border-t border-border/50">
              <label className="field-label text-[11.5px] text-accent mb-1.5 block font-semibold">
                Thesis Lifecycle Stage
              </label>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-1.5 font-mono text-[11px]">
                {[
                  { key: "ACTIVE_HYPOTHESIS", label: "Active Hypothesis", desc: "Under active testing" },
                  { key: "CONFIRMED", label: "Confirmed", desc: "Corroborated by evidence" },
                  { key: "REFUTED", label: "Refuted", desc: "Counter-evidence observed" },
                  { key: "MONITORING", label: "Monitoring", desc: "Continuous surveillance" },
                ].map((item) => (
                  <button
                    key={item.key}
                    type="button"
                    onClick={() => setThesisStatus(item.key)}
                    className={`p-1.5 rounded border text-center transition-all ${
                      thesisStatus === item.key
                        ? "bg-accent/15 border-accent text-accent font-bold"
                        : "bg-surface border-border text-text-secondary hover:text-text-primary"
                    }`}
                  >
                    <span>{item.label}</span>
                  </button>
                ))}
              </div>
            </div>

            {/* Associated Finding / Lineage Code */}
            <div className="pt-2 border-t border-border/50">
              <div className="flex items-center justify-between mb-1">
                <label htmlFor="associated-finding" className="field-label text-[11.5px] text-text-secondary">
                  Associated Stress Finding / Metric ID (Optional Link)
                </label>
                <span className="mono-meta text-[10px]">
                  {associatedFindingId.length}/100
                </span>
              </div>
              <input
                id="associated-finding"
                type="text"
                maxLength={100}
                value={associatedFindingId}
                onChange={(e) => setAssociatedFindingId(e.target.value)}
                placeholder="e.g. STR-FINDING-GFC_2008-01, RET-02, finding:105"
                className="field w-full text-[12px] font-mono"
              />
            </div>

            {/* 3. Supporting Evidence References */}
            <div className="pt-2 border-t border-border/50">
              <div className="flex items-center justify-between mb-1">
                <label htmlFor="supporting-evidence" className="field-label text-[11.5px] text-text-secondary">
                  Key Supporting Evidence Considered
                </label>
                <span className="mono-meta text-[10px]">
                  {supportingEvidence.length}/1000
                </span>
              </div>
              <textarea
                id="supporting-evidence"
                rows={2}
                maxLength={1000}
                value={supportingEvidence}
                onChange={(e) => setSupportingEvidence(e.target.value)}
                placeholder="e.g. RET-02 (+2.45% in Jan 2024), RSK-01 (14.2% annualized volatility), lower maximum drawdown vs NIFTY 500 TRI."
                className="field w-full text-[12px] leading-relaxed resize-y font-mono"
              />
            </div>

            {/* 4. Reconsideration Conditions / Invalidation Criteria */}
            <div className="pt-2 border-t border-border/50">
              <div className="flex items-center justify-between mb-1">
                <label htmlFor="reconsideration-conditions" className="field-label text-[11.5px] text-amber-300">
                  Conditions for Reconsidering this Thesis
                </label>
                <span className="mono-meta text-[10px]">
                  {reconsiderationConditions.length}/1000
                </span>
              </div>
              <textarea
                id="reconsideration-conditions"
                rows={2}
                maxLength={1000}
                value={reconsiderationConditions}
                onChange={(e) => setReconsiderationConditions(e.target.value)}
                placeholder="e.g. Revisit if 3Y annualized volatility rises above 18%, downside capture ratio exceeds 95%, or primary fund manager changes."
                className="field w-full text-[12px] leading-relaxed resize-y"
              />
            </div>
          </div>

          {/* 5. Unresolved Uncertainties */}
          <div>
            <div className="flex items-center justify-between mb-1">
              <label htmlFor="investigation-question" className="field-label text-[12px]">
                What question remains unanswered? (Unresolved Uncertainty)
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
              rows={2}
              maxLength={1000}
              value={investigationQuestion}
              onChange={(e) => setInvestigationQuestion(e.target.value)}
              placeholder="e.g. Does the higher upside capture come at the expense of excessive downside semideviation during market drawdowns?"
              className="field w-full text-[12.5px] leading-relaxed resize-y"
            />
          </div>

          {/* 6. Actionable Next Step */}
          <div>
            <div className="flex items-center justify-between mb-1">
              <label htmlFor="next-step" className="field-label text-[12px]">
                Next investigation step (Action to take)
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
              {(fund.reasonSaved || fund.investigationQuestion || fund.nextStep || fund.workingThesis) && (
                <button
                  type="button"
                  onClick={handleClearNote}
                  disabled={saving}
                  className="btn btn-ghost btn-sm text-critical-fg hover:bg-critical-bg/20"
                >
                  Clear Thesis &amp; Note
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
                <span>Save Thesis &amp; Note</span>
              </button>
            </div>
          </div>
        </form>
        )}
      </div>
    </div>
  );
}
