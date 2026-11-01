'use client';

import React from 'react';

export interface MetricDefinition {
  metricCode: string;
  canonicalName: string;
  categoryLabel?: string;
  governanceStatus?: string;
  mathematicalDefinition?: string;
  formulaDisplay?: string;
  financialMeaning?: string;
  investigationQuestion?: string;
  unit?: string;
  limitations?: string;
}

interface Props {
  metric: MetricDefinition | null;
  isOpen: boolean;
  onClose: () => void;
}

export function MetricDefinitionDrawer({ metric, isOpen, onClose }: Props) {
  if (!isOpen || !metric) return null;

  const isApproved = metric.governanceStatus === 'APPROVED' || metric.metricCode === 'RET-02';

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/60 backdrop-blur-xs">
      <div className="relative w-full max-w-lg bg-slate-900 border-l border-slate-800 h-full overflow-y-auto p-6 shadow-2xl animate-in slide-in-from-right duration-200">
        <div className="flex items-start justify-between pb-4 border-b border-slate-800">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <span className="font-mono text-xs px-2 py-0.5 rounded bg-blue-950 text-blue-400 border border-blue-800 font-semibold">
                {metric.metricCode}
              </span>
              <span
                className={`text-xs px-2 py-0.5 rounded font-semibold ${
                  isApproved
                    ? 'bg-emerald-950 text-emerald-400 border border-emerald-800'
                    : 'bg-amber-950 text-amber-400 border border-amber-800'
                }`}
              >
                {isApproved ? 'OPERATIONAL BASELINE (APPROVED)' : 'CANDIDATE METHODOLOGY'}
              </span>
            </div>
            <h2 className="text-xl font-bold text-slate-100">{metric.canonicalName}</h2>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-slate-100 p-1 rounded hover:bg-slate-800 transition-colors"
          >
            ✕
          </button>
        </div>

        <div className="mt-6 space-y-6 text-sm">
          {/* Mathematical Formula */}
          <div>
            <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-2">
              Mathematical Formulation
            </h3>
            <div className="bg-slate-950 p-3 rounded-lg border border-slate-800 font-mono text-emerald-400 overflow-x-auto">
              {metric.formulaDisplay || metric.mathematicalDefinition || 'Deterministic formula grounded in pure Python kernel.'}
            </div>
          </div>

          {/* Financial Interpretation */}
          <div>
            <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
              Financial Purpose & Meaning
            </h3>
            <p className="text-slate-300 leading-relaxed">
              {metric.financialMeaning || 'Evaluates investment return or risk over point-in-time observation intervals.'}
            </p>
          </div>

          {/* Epistemic Question */}
          <div>
            <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
              Investor Investigation Checkpoint
            </h3>
            <div className="bg-slate-950/60 p-3 rounded border border-blue-900/40 text-blue-300">
              {metric.investigationQuestion || 'Before committing capital, verify whether historical outcomes reflect persistent skill or temporary regime exposure.'}
            </div>
          </div>

          {/* Methodology Warning */}
          <div className="p-3 rounded bg-amber-950/30 border border-amber-900/50 text-amber-300/90 text-xs leading-relaxed">
            <span className="font-semibold block mb-1">Methodology Governance Rule:</span>
            RET-02 (Simple Period Return) is the only approved operational baseline. Candidate metrics provide
            empirical research context and must not be treated as automated buy/sell recommendations.
          </div>

          {metric.limitations && (
            <div>
              <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                Known Empirical Limitations
              </h3>
              <p className="text-slate-400 text-xs leading-relaxed">{metric.limitations}</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

