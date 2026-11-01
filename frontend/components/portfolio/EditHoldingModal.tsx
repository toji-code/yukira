'use client';

import React, { useState, useEffect } from 'react';
import { PortfolioHoldingDto, addPortfolioHolding } from '@/lib/api/portfolio';
import { formatCurrency } from '@/lib/utils/formatters';

interface EditHoldingModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  holding: PortfolioHoldingDto | null;
}

export function EditHoldingModal({
  isOpen,
  onClose,
  onSuccess,
  holding,
}: EditHoldingModalProps) {
  const [unitsInput, setUnitsInput] = useState<string>('');
  const [costBasisInput, setCostBasisInput] = useState<string>('');
  const [reasonSavedInput, setReasonSavedInput] = useState<string>('');
  const [questionInput, setQuestionInput] = useState<string>('');
  const [nextStepInput, setNextStepInput] = useState<string>('');

  const [submitting, setSubmitting] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (holding) {
      setUnitsInput(holding.units ? String(holding.units) : '');
      setCostBasisInput(
        holding.costBasisAmount !== null && holding.costBasisAmount !== undefined
          ? String(holding.costBasisAmount)
          : ''
      );
      setReasonSavedInput(holding.reasonSaved || '');
      setQuestionInput(holding.investigationQuestion || '');
      setNextStepInput(holding.nextStep || '');
      setError(null);
    }
  }, [holding]);

  if (!isOpen || !holding) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const units = parseFloat(unitsInput);
    if (isNaN(units) || units <= 0) {
      setError('Units must be a positive number greater than 0');
      return;
    }

    let costBasis: number | null = null;
    if (costBasisInput.trim().length > 0) {
      costBasis = parseFloat(costBasisInput);
      if (isNaN(costBasis) || costBasis < 0) {
        setError('Cost basis cannot be negative');
        return;
      }
    }

    try {
      setSubmitting(true);
      setError(null);

      await addPortfolioHolding(
        holding.schemeOptionId,
        units,
        costBasis,
        reasonSavedInput.trim() || null,
        questionInput.trim() || null,
        nextStepInput.trim() || null
      );

      onSuccess();
      onClose();
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to update holding');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="edit-holding-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4"
    >
      <div className="panel w-full max-w-xl max-h-[90vh] flex flex-col bg-surface border-border shadow-2xl overflow-hidden animate-in fade-in zoom-in-95 duration-150">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-border p-4 bg-surface-raised">
          <div>
            <h3 id="edit-holding-title" className="text-[14px] font-semibold text-text-primary flex items-center gap-2">
              <span>Edit Position & Research Context</span>
              <span className="status-badge state-verified text-[10px]">PERSISTED HOLDING</span>
            </h3>
            <p className="font-mono text-[11px] text-text-tertiary mt-0.5">
              Update units, purchase cost basis, and attached investigation questions
            </p>
          </div>
          <button
            onClick={onClose}
            disabled={submitting}
            className="text-text-tertiary hover:text-text-primary text-[16px] leading-none px-1"
            aria-label="Close modal"
          >
            ✕
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto p-4 space-y-4">
          {/* Identity Card */}
          <div className="panel-inset p-3 bg-surface border border-border/80 space-y-1.5">
            <span className="eyebrow text-accent">Mutual Fund Scheme Option</span>
            <div className="font-sans font-semibold text-[13px] text-text-primary">
              {holding.fundName}
            </div>
            <div className="flex flex-wrap items-center gap-2 font-mono text-[11px] text-text-tertiary pt-1">
              <span className="status-badge state-verified">{holding.planType}</span>
              <span className="status-badge state-verified">{holding.optionType}</span>
              <span>AMFI: {holding.amfiCode}</span>
              <span>•</span>
              <span>ISIN: {holding.isin}</span>
              <span>•</span>
              <span>AMC: {holding.amcName}</span>
            </div>
          </div>

          {error && (
            <div className="state-panel-error text-[12px] font-mono">
              {error}
            </div>
          )}

          {/* Position Numbers */}
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <label htmlFor="edit-units" className="field-label">
                Units Held <span className="text-accent">*</span>
              </label>
              <input
                id="edit-units"
                type="number"
                step="0.0001"
                min="0.0001"
                value={unitsInput}
                onChange={(e) => setUnitsInput(e.target.value)}
                className="field field-mono"
                required
              />
              <p className="mt-1 font-mono text-[10px] text-text-tertiary">
                Current authoritative units: {holding.units}
              </p>
            </div>

            <div>
              <label htmlFor="edit-cost-basis" className="field-label">
                Cost Basis / Unit (Optional)
              </label>
              <input
                id="edit-cost-basis"
                type="number"
                step="0.01"
                min="0"
                value={costBasisInput}
                onChange={(e) => setCostBasisInput(e.target.value)}
                className="field field-mono"
                placeholder="e.g. 1500.00"
              />
              <p className="mt-1 font-mono text-[10px] text-text-tertiary">
                Leave blank if cost basis is unknown.
              </p>
            </div>
          </div>

          {/* Research Notes & Context */}
          <div className="space-y-3 pt-2 border-t border-border">
            <span className="eyebrow text-accent">Investor Research Context & Notes</span>

            <div>
              <label htmlFor="edit-reason-saved" className="field-label">
                Allocation Thesis / Reason Held (Optional)
              </label>
              <input
                id="edit-reason-saved"
                type="text"
                value={reasonSavedInput}
                onChange={(e) => setReasonSavedInput(e.target.value)}
                className="field"
                placeholder="e.g. Core long-term domestic equity allocation with consistent downside control"
              />
            </div>

            <div>
              <label htmlFor="edit-investigation-question" className="field-label">
                Key Investigation Question (Optional)
              </label>
              <textarea
                id="edit-investigation-question"
                rows={2}
                value={questionInput}
                onChange={(e) => setQuestionInput(e.target.value)}
                className="field text-[12px]"
                placeholder="e.g. How has recent manager transition affected downside capture in market drawdowns?"
              />
            </div>

            <div>
              <label htmlFor="edit-next-step" className="field-label">
                Next Verification Checkpoint (Optional)
              </label>
              <input
                id="edit-next-step"
                type="text"
                value={nextStepInput}
                onChange={(e) => setNextStepInput(e.target.value)}
                className="field"
                placeholder="e.g. Recheck 3Y Maximum Drawdown vs Nifty 500 benchmark on next NAV release"
              />
            </div>
          </div>

          <div className="flex items-center justify-end gap-2 pt-4 border-t border-border">
            <button
              type="button"
              onClick={onClose}
              disabled={submitting}
              className="btn btn-secondary btn-sm"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting || !unitsInput}
              className="btn btn-primary btn-sm flex items-center gap-1.5"
            >
              {submitting ? (
                <>
                  <span className="inline-block h-3 w-3 animate-spin rounded-full border-2 border-surface-dark border-t-transparent" />
                  <span>Updating...</span>
                </>
              ) : (
                <span>Save Position Changes</span>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
