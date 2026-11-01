'use client';

import React from 'react';
import { PortfolioHoldingDto } from '@/lib/api/portfolio';
import { formatCurrency } from '@/lib/utils/formatters';

interface ConfirmRemoveModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => Promise<void>;
  holding: PortfolioHoldingDto | null;
  submitting?: boolean;
}

export function ConfirmRemoveModal({
  isOpen,
  onClose,
  onConfirm,
  holding,
  submitting = false,
}: ConfirmRemoveModalProps) {
  if (!isOpen || !holding) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="confirm-remove-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4"
    >
      <div className="panel w-full max-w-md bg-surface border-border shadow-2xl overflow-hidden animate-in fade-in zoom-in-95 duration-150">
        <div className="flex items-center justify-between border-b border-border p-4 bg-surface-raised">
          <div className="flex items-center gap-2">
            <span className="text-rose-500 font-mono text-base">⚠️</span>
            <h3 id="confirm-remove-title" className="text-[14px] font-semibold text-text-primary">
              Confirm Holding Removal
            </h3>
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

        <div className="p-4 space-y-4">
          <p className="text-[13px] text-text-secondary leading-relaxed">
            Are you sure you want to remove this mutual fund holding from your portfolio? This will remove the tracked position and its associated valuation calculations.
          </p>

          <div className="panel-inset p-3 bg-surface border border-border/80 space-y-2">
            <div className="font-sans font-semibold text-[13px] text-text-primary">
              {holding.fundName}
            </div>
            <div className="grid grid-cols-2 gap-2 font-mono text-[11px] text-text-tertiary">
              <div>
                <span className="text-text-muted block text-[10px]">Scheme Option ID:</span>
                <span className="text-text-secondary font-semibold">#{holding.schemeOptionId}</span>
              </div>
              <div>
                <span className="text-text-muted block text-[10px]">Plan & Option:</span>
                <span className="text-text-secondary">{holding.planType} • {holding.optionType}</span>
              </div>
              <div>
                <span className="text-text-muted block text-[10px]">Units Held:</span>
                <span className="text-text-secondary font-semibold">{holding.units}</span>
              </div>
              <div>
                <span className="text-text-muted block text-[10px]">Available Value:</span>
                <span className="text-text-secondary font-semibold">
                  {holding.availableValue !== null ? formatCurrency(holding.availableValue) : 'Unavailable'}
                </span>
              </div>
            </div>

            {(holding.reasonSaved || holding.investigationQuestion) && (
              <div className="pt-2 border-t border-border/60 font-mono text-[10px] text-text-tertiary">
                <span className="text-amber-400">Notice:</span> Attached research notes will also be unlinked.
              </div>
            )}
          </div>

          <div className="panel-inset p-2.5 bg-rose-500/10 border border-rose-500/20 text-[11px] font-mono text-rose-300">
            Explicit confirmation is required to prevent accidental data loss. This change will take effect immediately.
          </div>
        </div>

        <div className="flex items-center justify-end gap-2 border-t border-border p-4 bg-surface-raised">
          <button
            type="button"
            onClick={onClose}
            disabled={submitting}
            className="btn btn-secondary btn-sm"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={submitting}
            className="btn btn-danger btn-sm flex items-center gap-1.5"
          >
            {submitting ? (
              <>
                <span className="inline-block h-3 w-3 animate-spin rounded-full border-2 border-white border-t-transparent" />
                <span>Removing...</span>
              </>
            ) : (
              <span>Confirm Removal</span>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
