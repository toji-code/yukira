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
      border: 'border-sky-500/30',
      text: 'text-sky-600 dark:text-sky-400',
      bg: 'bg-sky-500/10',
    },
    risk: {
      border: 'border-amber-500/30',
      text: 'text-amber-600 dark:text-amber-400',
      bg: 'bg-amber-500/10',
    },
    drawdown: {
      border: 'border-rose-500/30',
      text: 'text-rose-600 dark:text-rose-400',
      bg: 'bg-rose-500/10',
    },
    tail: {
      border: 'border-purple-500/30',
      text: 'text-purple-600 dark:text-purple-400',
      bg: 'bg-purple-500/10',
    },
    risk_adjusted: {
      border: 'border-emerald-500/30',
      text: 'text-emerald-600 dark:text-emerald-400',
      bg: 'bg-emerald-500/10',
    },
    portfolio: {
      border: 'border-border',
      text: 'text-text-secondary',
      bg: 'bg-surface-elevated',
    },
  };

  const statusBadgeMap: Record<MetricGovernanceStatus, { label: string; badgeClass: string }> = {
    candidate: {
      label: 'Candidate Methodology',
      badgeClass: 'bg-amber-500/15 text-amber-700 dark:text-amber-300 border-amber-500/30',
    },
    validated: {
      label: 'Validated Methodology',
      badgeClass: 'bg-blue-500/15 text-blue-700 dark:text-blue-300 border-blue-500/30',
    },
    approved: {
      label: 'Approved Methodology',
      badgeClass: 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300 border-emerald-500/30',
    },
    unimplemented: {
      label: 'Candidate Specification',
      badgeClass: 'bg-surface-elevated text-text-muted border-border',
    },
    pending_ingestion: {
      label: 'Pending Feed Ingestion',
      badgeClass: 'bg-surface-elevated text-text-muted border-border',
    },
  };

  const catStyle = categoryColorMap[category];
  const statusBadge = statusBadgeMap[governanceStatus];

  return (
    <article
      className="rounded-xl border border-border bg-surface p-5 backdrop-blur-sm transition-all hover:border-border-subtle hover:bg-surface-elevated flex flex-col justify-between"
      aria-labelledby={`metric-title-${code}`}
    >
      <div>
        {/* Top Header: Primary Metric Name as Heading (Clean investor UI without internal code or governance badge) */}
        <div className="flex flex-wrap items-start justify-between gap-2 pb-3 border-b border-border">
          <div className="space-y-1">
            <h4 id={`metric-title-${code}`} className="text-sm font-semibold text-text-primary tracking-tight">
              {name}
            </h4>
            {period && (
              <div className="font-mono text-[10px] text-text-muted">
                {period}
              </div>
            )}
          </div>

          {categoryLabel && (
            <div className="flex items-center gap-1.5 flex-wrap justify-end">
              <span
                className={`font-mono text-[10px] uppercase font-semibold px-2 py-0.5 rounded border ${catStyle.border} ${catStyle.text} ${catStyle.bg}`}
              >
                {categoryLabel}
              </span>
            </div>
          )}
        </div>

        {/* Value Display / Action State */}
        <div className="my-3 py-1">
          {isCalculated && formattedValue ? (
            <div className="flex items-baseline gap-2">
              <span className="font-mono text-2xl font-bold text-text-primary tracking-tight">
                {formattedValue}
              </span>
              {units && (
                <span className="font-mono text-xs text-text-muted uppercase">
                  {units}
                </span>
              )}
            </div>
          ) : governanceStatus === 'unimplemented' || governanceStatus === 'pending_ingestion' ? (
            <div className="font-mono text-xs text-text-muted italic">
              {disabledReason || 'Under active quantitative review — available in full analytical audit.'}
            </div>
          ) : (
            <div className="font-mono text-xs text-text-muted">
              Ready to execute against point-in-time observations.
            </div>
          )}
        </div>

        {/* Short Analytical Description */}
        <p className="text-xs text-text-secondary leading-relaxed font-sans">
          {description}
        </p>

        {/* Progressive Disclosure Expandable Area: Technical Methodology & Audit Details */}
        {expanded && (
          <div className="mt-4 pt-3 border-t border-border space-y-3 font-mono text-xs text-text-secondary animate-in fade-in duration-150">
            {/* Technical Audit Reference */}
            <div className="flex items-center justify-between gap-2 p-2 rounded bg-surface-elevated border border-border text-[11px]">
              <span className="text-text-muted">
                Internal Identifier: <strong className="text-text-primary">{code}</strong>
              </span>
              <span className={`inline-flex items-center rounded px-2 py-0.5 text-[10px] font-semibold border ${statusBadge.badgeClass}`}>
                {statusBadge.label}
              </span>
            </div>

            {formula && (
              <div className="rounded bg-surface-elevated p-2.5 border border-border">
                <span className="text-text-muted text-[10px] uppercase block mb-1">Formula Specification</span>
                <code className="text-accent text-[11px] break-all">{formula}</code>
              </div>
            )}

            {interpretation && (
              <div>
                <span className="text-text-muted text-[10px] uppercase block mb-0.5 font-sans font-semibold">
                  Statistical Interpretation:
                </span>
                <p className="text-text-secondary text-xs font-sans leading-relaxed">
                  {interpretation}
                </p>
              </div>
            )}

            {limitation && (
              <div className="rounded bg-amber-500/10 p-2.5 border border-amber-500/20 text-amber-800 dark:text-amber-200">
                <span className="text-amber-700 dark:text-amber-400 text-[10px] uppercase font-bold block mb-0.5">
                  Methodology Limitation:
                </span>
                <p className="text-[11px] font-sans text-amber-900 dark:text-amber-300/90 leading-relaxed">
                  {limitation}
                </p>
              </div>
            )}

            {assumptions.length > 0 && (
              <div className="grid grid-cols-2 gap-2 text-[11px]">
                {assumptions.map((assump, idx) => (
                  <div key={idx} className="rounded bg-surface-elevated p-2 border border-border">
                    <span className="text-text-muted text-[10px] block">{assump.label}</span>
                    <span className="text-text-primary font-semibold">{assump.value}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {/* Footer Controls */}
      <div className="mt-4 pt-3 border-t border-border flex items-center justify-between gap-3">
        <button
          type="button"
          onClick={() => setExpanded(!expanded)}
          className="text-[11px] font-mono text-text-muted hover:text-text-primary focus:outline-none focus-visible:underline inline-flex items-center gap-1 transition"
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
              className="inline-flex items-center gap-1 rounded bg-surface-elevated border border-border px-2.5 py-1 text-xs font-mono font-medium text-text-primary hover:bg-surface transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent"
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
              className="inline-flex items-center gap-1.5 rounded-lg bg-accent px-3 py-1.5 font-mono text-xs font-semibold text-accent-foreground shadow-sm transition hover:opacity-90 disabled:opacity-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent"
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
                  <span>Calculate {name}</span>
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
