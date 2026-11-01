'use client';

import React, { useState, useEffect } from 'react';
import { getConditionHistory, MonitoringConditionHistoryDto } from '@/lib/api/monitoring';

interface ConditionHistoryModalProps {
  conditionId: number | null;
  conditionName: string;
  isOpen: boolean;
  onClose: () => void;
}

export function ConditionHistoryModal({
  conditionId,
  conditionName,
  isOpen,
  onClose,
}: ConditionHistoryModalProps) {
  const [history, setHistory] = useState<MonitoringConditionHistoryDto[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (isOpen && conditionId) {
      loadHistory(conditionId);
    }
  }, [isOpen, conditionId]);

  async function loadHistory(id: number) {
    setLoading(true);
    try {
      const records = await getConditionHistory(id);
      setHistory(records);
    } catch (e) {
      console.error(e);
      setHistory([]);
    } finally {
      setLoading(false);
    }
  }

  if (!isOpen || !conditionId) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-sm p-4">
      <div className="relative w-full max-w-2xl bg-zinc-950 border border-zinc-800 rounded-xl shadow-2xl flex flex-col max-h-[80vh] overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-zinc-800 bg-zinc-900/40">
          <div>
            <h3 className="text-sm font-semibold text-zinc-100 flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-cyan-500" />
              Condition Execution Ledger
            </h3>
            <p className="text-xs text-zinc-400 mt-0.5 truncate max-w-md">
              {conditionName} (Condition #{conditionId})
            </p>
          </div>
          <button
            onClick={onClose}
            className="text-zinc-400 hover:text-zinc-200 p-1.5 rounded-lg hover:bg-zinc-800/60 transition-colors"
          >
            ✕
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-6">
          {loading ? (
            <div className="py-12 text-center text-xs text-zinc-500">Retrieving execution ledger...</div>
          ) : history.length === 0 ? (
            <div className="py-12 text-center text-xs text-zinc-500">
              No historical execution runs recorded for this condition yet.
            </div>
          ) : (
            <div className="space-y-3">
              {history.map((h) => {
                const outcomeColor =
                  h.evaluationOutcome === 'TRIGGERED'
                    ? 'text-rose-400 border-rose-500/30 bg-rose-500/10'
                    : h.evaluationOutcome === 'NOT_TRIGGERED'
                    ? 'text-emerald-400 border-emerald-500/30 bg-emerald-500/10'
                    : 'text-amber-400 border-amber-500/30 bg-amber-500/10';

                return (
                  <div
                    key={h.id}
                    className="p-3.5 bg-zinc-900/40 border border-zinc-800 rounded-lg text-xs space-y-2"
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span className={`px-2 py-0.5 rounded text-[10px] font-semibold border ${outcomeColor}`}>
                          {h.evaluationOutcome}
                        </span>
                        <span className="text-zinc-300 font-mono text-[11px]">
                          {new Date(h.evaluatedAt).toLocaleString()}
                        </span>
                      </div>
                      <span className="text-[10px] text-zinc-500 font-mono">
                        Cutoff: {h.observationCutoff || 'LATEST'}
                      </span>
                    </div>

                    {h.withholdingReason && (
                      <div className="text-[11px] text-amber-300/90 bg-amber-950/20 p-2 rounded border border-amber-900/30">
                        <strong className="font-semibold text-amber-200">Withholding Reason: </strong>
                        {h.withholdingReason}
                      </div>
                    )}

                    {h.sourceContext && (
                      <div className="text-[10px] text-zinc-400 font-mono truncate">
                        Context: {h.sourceContext}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

