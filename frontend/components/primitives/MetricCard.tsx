'use client';

import React, { useState } from 'react';
import Link from 'next/link';

export type MetricCategory =
  | 'return'
  | 'risk'
  | 'drawdown'
  | 'tail'
  | 'risk_adjusted'
  | 'portfolio';

export type MetricGovernanceStatus =
  | 'candidate'
  | 'validated'
  | 'approved'
  | 'unimplemented'
  | 'pending_ingestion';

export interface MetricCardProps {
  code: string;
  name: string;
  category: MetricCategory;
  categoryLabel?: string;
  governanceStatus: MetricGovernanceStatus;
  value?: string | number | null;
  formattedValue?: string;
  units?: string;
  period?: string;
  formula?: string;
  description: string;
  interpretation?: string;
  limitation?: string;
  assumptions?: Array<{ label: string; value: string }>;
  isCalculated?: boolean;
  canExecute?: boolean;
  isExecuting?: boolean;
  onExecute?: () => void;
  runId?: number | null;
  disabledReason?: string;
}

export function MetricCard({
  code,
  name,
  category,
  categoryLabel,
  governanceStatus,
  formattedValue,
  units,
  period,
  formula,
  description,
  interpretation,
  limitation,
  assumptions = [],
  isCalculated = false,
  canExecute = false,
  isExecuting = false,
  onExecute,
  runId,
  disabledReason,
}: MetricCardProps) {
  const [expanded, setExpanded] = useState(false);

  const categoryColorMap: Record<MetricCategory, { border: string; text: string; bg: string }> = {
    return: {
      border: 'border-cyan-500/30',
      text: 'text-cyan-400',
      bg: 'bg-cyan-500/10',
    },
    risk: {
      border: 'border-amber-500/30',
      text: 'text-amber-400',
      bg: 'bg-amber-500/10',
    },
    drawdown: {
      border: 'border-rose-500/30',
      text: 'text-rose-400',
      bg: 'bg-rose-500/10',
    },
    tail: {
      border: 'border-purple-500/30',
      text: 'text-purple-400',
      bg: 'bg-purple-500/10',
    },
    risk_adjusted: {
      border: 'border-emerald-500/30',
      text: 'text-emerald-400',
      bg: 'bg-emerald-500/10',
    },
    portfolio: {
      border: 'border-zinc-700',
      text: 'text-zinc-400',
      bg: 'bg-zinc-800/40',
    },
  };

  const statusBadgeMap: Record<MetricGovernanceStatus, { label: string; badgeClass: string }> = {
    candidate: {
      label: 'Candidate Methodology',
      badgeClass: 'bg-amber-500/15 text-amber-300 border-amber-500/30',
    },
    validated: {
      label: 'Validated Methodology',
      badgeClass: 'bg-blue-500/15 text-blue-300 border-blue-500/30',
    },
    approved: {
      label: 'Approved Methodology',
      badgeClass: 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30',
    },
    unimplemented: {
      label: 'Candidate Specification',
      badgeClass: 'bg-zinc-800 text-zinc-400 border-zinc-700/60',
    },
    pending_ingestion: {
      label: 'Pending Feed Ingestion',
      badgeClass: 'bg-zinc-800/80 text-zinc-400 border-zinc-700/40',
    },
  };

  const catStyle = categoryColorMap[category];
  const statusBadge = statusBadgeMap[governanceStatus];

  return (
    <article
      className="rounded-xl border border-zinc-800/90 bg-zinc-900/60 p-5 backdrop-blur-sm transition-all hover:border-zinc-700 flex flex-col justify-between"
      aria-labelledby={`metric-title-${code}`}
    >
      <div>
        {/* Top Header: Code, Category, Governance Status */}
        <div className="flex flex-wrap items-center justify-between gap-2 pb-3 border-b border-zinc-800/60">
          <div className="flex items-center gap-2">
            <span className="font-mono text-xs font-bold text-zinc-100 bg-zinc-800 px-2 py-0.5 rounded border border-zinc-700/80">
              {code}
            </span>
            {categoryLabel && (
              <span
                className={`font-mono text-[10px] uppercase font-semibold px-2 py-0.5 rounded border ${catStyle.border} ${catStyle.text} ${catStyle.bg}`}
              >
                {categoryLabel}
              </span>
            )}
          </div>

          <span
            className={`font-mono text-[10px] font-medium px-2 py-0.5 rounded border ${statusBadge.badgeClass}`}
          >
            {statusBadge.label}
          </span>
        </div>

        {/* Title & Period */}
        <div className="mt-3">
          <h4 id={`metric-title-${code}`} className="text-sm font-semibold text-zinc-100 tracking-tight">
            {name}
          </h4>
          {period && (
            <div className="mt-0.5 font-mono text-[11px] text-zinc-400">
              Horizon: {period}
            </div>
          )}
        </div>

        {/* Value Display / Action State */}
        <div className="my-3 py-2">
          {isCalculated && formattedValue ? (
            <div className="flex items-baseline gap-2">
              <span className="font-mono text-2xl font-bold text-zinc-100 tracking-tight">
                {formattedValue}
              </span>
              {units && (
                <span className="font-mono text-xs text-zinc-400">
                  {units}
                </span>
              )}
            </div>
          ) : governanceStatus === 'unimplemented' || governanceStatus === 'pending_ingestion' ? (
            <div className="font-mono text-xs text-zinc-400 italic">
              {disabledReason || 'Candidate specification — not wired into operational execution path.'}
            </div>
          ) : (
            <div className="font-mono text-xs text-zinc-400">
              Ready to execute against point-in-time observations.
            </div>
          )}
        </div>

        {/* Short Analytical Description */}
        <p className="text-xs text-zinc-300 leading-relaxed font-sans">
          {description}
        </p>

        {/* Progressive Disclosure Expandable Area */}
        {expanded && (
          <div className="mt-4 pt-3 border-t border-zinc-800/80 space-y-3 font-mono text-xs text-zinc-300 animate-in fade-in duration-150">
            {formula && (
              <div className="rounded bg-zinc-950/80 p-2.5 border border-zinc-800">
                <span className="text-zinc-400 text-[10px] uppercase block mb-1">Formula Specification</span>
                <code className="text-cyan-300 text-[11px] break-all">{formula}</code>
              </div>
            )}

            {interpretation && (
              <div>
                <span className="text-zinc-400 text-[10px] uppercase block mb-0.5 font-sans font-semibold">
                  Statistical Interpretation:
                </span>
                <p className="text-zinc-300 text-xs font-sans leading-relaxed">
                  {interpretation}
                </p>
              </div>
            )}

            {limitation && (
              <div className="rounded bg-amber-500/10 p-2.5 border border-amber-500/20 text-amber-200">
                <span className="text-amber-400 text-[10px] uppercase font-bold block mb-0.5">
                  Methodology Limitation:
                </span>
                <p className="text-[11px] font-sans text-zinc-300 leading-relaxed">
                  {limitation}
                </p>
              </div>
            )}

            {assumptions.length > 0 && (
              <div className="grid grid-cols-2 gap-2 text-[11px]">
                {assumptions.map((assump, idx) => (
                  <div key={idx} className="rounded bg-zinc-950/60 p-2 border border-zinc-800/80">
                    <span className="text-zinc-400 text-[10px] block">{assump.label}</span>
                    <span className="text-zinc-200 font-semibold">{assump.value}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {/* Footer Controls */}
      <div className="mt-4 pt-3 border-t border-zinc-800/60 flex items-center justify-between gap-3">
        <button
          type="button"
          onClick={() => setExpanded(!expanded)}
          className="text-[11px] font-mono text-zinc-400 hover:text-zinc-200 focus:outline-none focus-visible:underline inline-flex items-center gap-1"
          aria-expanded={expanded}
        >
          <span>{expanded ? 'Hide Methodology' : 'Methodology Details'}</span>
          <svg
            className={`h-3 w-3 transition-transform ${expanded ? 'rotate-180' : ''}`}
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
          </svg>
        </button>

        <div className="flex items-center gap-2">
          {runId && (
            <Link
              href={`/analysis/${runId}`}
              className="inline-flex items-center gap-1 rounded bg-zinc-800 px-2.5 py-1 text-xs font-mono font-medium text-zinc-200 hover:bg-zinc-700 hover:text-white transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-cyan-500"
            >
              Audit Run #{runId}
              <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
              </svg>
            </Link>
          )}

          {canExecute && onExecute && (
            <button
              type="button"
              onClick={onExecute}
              disabled={isExecuting}
              className="inline-flex items-center gap-1.5 rounded-lg bg-cyan-600 px-3 py-1.5 font-mono text-xs font-semibold text-white shadow-sm shadow-cyan-600/20 transition hover:bg-cyan-500 disabled:opacity-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-cyan-500"
            >
              {isExecuting ? (
                <>
                  <svg className="h-3.5 w-3.5 animate-spin" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                  </svg>
                  <span>Calculating...</span>
                </>
              ) : (
                <>
                  <span>Execute {code}</span>
                  <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
                  </svg>
                </>
              )}
            </button>
          )}
        </div>
      </div>
    </article>
  );
}
