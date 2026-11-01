'use client';

import React, { useState } from 'react';
import {
  reverseTransaction,
  InvestorTransactionDto
} from '@/lib/api/transactions';
import { formatCurrency, formatNumber } from '@/lib/utils/formatters';

interface ReverseTransactionModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (reversedTx: InvestorTransactionDto) => void;
  transaction: InvestorTransactionDto | null;
}

export function ReverseTransactionModal({
  isOpen,
  onClose,
  onSuccess,
  transaction
}: ReverseTransactionModalProps) {
  const [reversalReason, setReversalReason] = useState<string>('');
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen || !transaction) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!reversalReason.trim() || reversalReason.trim().length < 5) {
      setError('Reversal reason is required and must be at least 5 characters long.');
      return;
    }

    setSubmitting(true);
    setError(null);

    try {
      const result = await reverseTransaction(transaction.id, {
        reversalReason: reversalReason.trim()
      });
      onSuccess(result);
      onClose();
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to reverse transaction.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm">
      <div className="relative w-full max-w-lg bg-zinc-900 border border-zinc-800 rounded-xl shadow-2xl p-6 text-zinc-100">
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-zinc-800">
          <div className="flex items-center gap-2">
            <span className="p-1.5 bg-rose-500/10 border border-rose-500/30 rounded-lg text-rose-400 text-sm">
              ⚠
            </span>
            <h2 className="text-base font-semibold text-zinc-100">Reverse Ledger Transaction</h2>
          </div>
          <button
            onClick={onClose}
            className="text-zinc-400 hover:text-zinc-200 p-1 rounded-lg hover:bg-zinc-800 transition"
          >
            ✕
          </button>
        </div>

        {/* Transaction Summary Card */}
        <div className="my-4 p-3.5 bg-zinc-950/70 border border-zinc-800 rounded-lg space-y-2 text-xs">
          <div className="flex justify-between items-center">
            <span className="text-zinc-400 font-medium">Scheme</span>
            <span className="text-zinc-200 font-semibold truncate max-w-[280px]">
              {transaction.schemeName}
            </span>
          </div>
          <div className="flex justify-between items-center font-mono">
            <span className="text-zinc-400">Transaction Date</span>
            <span className="text-zinc-300">{transaction.transactionDate}</span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-zinc-400">Type & Flow</span>
            <span className="text-amber-400 font-mono font-medium">
              {transaction.transactionType} ({transaction.externalCashFlowType})
            </span>
          </div>
          <div className="flex justify-between items-center font-mono">
            <span className="text-zinc-400">Units / NAV</span>
            <span className="text-zinc-300">
              {transaction.units != null ? formatNumber(transaction.units, 4) : 'N/A'} units @ ₹{transaction.nav != null ? formatNumber(transaction.nav, 4) : 'N/A'}
            </span>
          </div>
          <div className="flex justify-between items-center font-mono">
            <span className="text-zinc-400">Net Amount</span>
            <span className="text-emerald-400 font-semibold">
              {transaction.netAmount != null ? formatCurrency(transaction.netAmount) : 'N/A'}
            </span>
          </div>
        </div>

        {/* Audit Warning */}
        <div className="p-3 bg-amber-950/40 border border-amber-800/60 rounded-lg text-amber-300 text-xs mb-4">
          <p className="font-semibold mb-1">Non-Destructive Ledger Reversal:</p>
          <p className="text-amber-300/80 leading-relaxed">
            Ledger transactions are never physically deleted. Reversing this record will set{' '}
            <code className="text-amber-200 bg-amber-950 px-1 py-0.5 rounded">is_reversed = true</code> and
            create a compensating reversal entry (<code className="text-amber-200 bg-amber-950 px-1 py-0.5 rounded">REVERSAL_ENTRY</code>) to preserve historical auditability.
          </p>
        </div>

        {error && (
          <div className="p-3 bg-rose-950/60 border border-rose-800/80 rounded-lg text-rose-300 text-xs mb-4">
            <span className="font-bold">Error:</span> {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-medium text-zinc-300 mb-1">
              Reversal Reason <span className="text-rose-400">*</span>
            </label>
            <textarea
              rows={3}
              required
              placeholder="e.g. Duplicate transaction recorded from CAS statement dated 2024-01-15; correcting double entry."
              value={reversalReason}
              onChange={(e) => setReversalReason(e.target.value)}
              className="w-full px-3 py-2 bg-zinc-950 border border-zinc-700 rounded-lg text-xs text-zinc-100 focus:outline-none focus:border-rose-500"
            />
          </div>

          <div className="flex items-center justify-end gap-3 pt-3 border-t border-zinc-800">
            <button
              type="button"
              onClick={onClose}
              disabled={submitting}
              className="px-4 py-2 text-xs font-medium text-zinc-400 hover:text-zinc-200 transition"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="px-4 py-2 bg-rose-600 hover:bg-rose-500 text-white rounded-lg text-xs font-medium transition disabled:opacity-50"
            >
              {submitting ? 'Reversing...' : 'Confirm Reversal'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

