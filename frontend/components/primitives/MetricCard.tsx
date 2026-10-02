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

/**
 * Governance state is the only thing allowed to own colour here.
 *
 * The previous implementation gave every metric CATEGORY its own hue, which
 * spent the whole colour budget on decoration and left nothing for the states
 * that actually matter. Category is now carried by the eyebrow label alone.
 */
const STATUS_BADGE: Record<MetricGovernanceStatus, { label: string; className: string }> = {
  candidate: { label: 'Candidate', className: 'state-candidate' },
  validated: { label: 'Validated', className: 'state-operational' },
  approved: { label: 'Approved', className: 'state-approved' },
  unimplemented: { label: 'Candidate spec', className: 'state-unavailable' },
  pending_ingestion: { label: 'Pending ingestion', className: 'state-unavailable' },
};

/** Capital-impairment semantics are the only place a coloured left rule appears. */
const RISK_LEFT_RULE: Partial<Record<MetricCategory, boolean>> = {
  drawdown: true,
  tail: true,
};

/**
 * The core analytical unit.
 *
 * Reads top-to-bottom as an evidence chain:
 *   eyebrow category + ALWAYS-VISIBLE governance badge
 *   -> metric name
 *   -> value + context
 *   -> interpretation
 *   -> one disclosure row for formula / assumptions / limitation
 */
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

  const statusBadge = STATUS_BADGE[governanceStatus];
  const showRiskRule = RISK_LEFT_RULE[category] === true;

  const hasValue = isCalculated && formattedValue !== undefined && formattedValue !== '';
  const pendingReason =
    disabledReason ||
    'Under active quantitative review — available in full analytical audit.';
  const contextLine = period || (units ? units.toUpperCase() : null);

  const disclosureParts = [
    formula ? 'Formula' : null,
    assumptions.length > 0 ? 'Assumptions' : null,
    limitation ? 'Limitation' : null,
  ].filter((part): part is string => part !== null);

  return (
    <article
      className={`metric-tile ${showRiskRule ? 'metric-risk-rule' : ''}`}
      aria-labelledby={`metric-title-${code}`}
    >
      <div className="flex items-start justify-between gap-3">
        <p className="eyebrow">{categoryLabel || category}</p>
        <span className={`status-badge ${statusBadge.className}`} title={`Governance: ${statusBadge.label}`}>
          <span className="status-dot" aria-hidden />
          {statusBadge.label}
        </span>
      </div>

      <h4
        id={`metric-title-${code}`}
        className="mt-2 text-[15px] font-semibold leading-[1.4] text-text-primary"
      >
        {name}
      </h4>

      <div className="metric-rule my-3" role="presentation" />

      <div className="min-h-[52px]">
        {hasValue ? (
          <>
            <p className="data-value-lg">{formattedValue}</p>
            {contextLine && <p className="mono-meta mt-1">{contextLine}</p>}
          </>
        ) : governanceStatus === 'unimplemented' || governanceStatus === 'pending_ingestion' ? (
          <>
            <p className="data-unavailable">Not available</p>
            <p className="mt-1 text-[13px] leading-[1.5] text-text-tertiary">{pendingReason}</p>
          </>
        ) : (
          <>
            <p className="data-unavailable">Not calculated</p>
            <p className="mt-1 text-[13px] leading-[1.5] text-text-tertiary">
              No run has produced this metric for the selected cutoffs.
            </p>
          </>
        )}
      </div>

      <div className="metric-rule my-3" role="presentation" />

      <p className="text-[13px] leading-[1.55] text-text-secondary">{description}</p>

      {expanded && (
        <div className="mt-3 space-y-3 border-t border-border pt-3">
          <div className="flex items-center justify-between gap-2">
            <span className="mono-meta">
              Metric identifier <span className="text-text-primary">{code}</span>
            </span>
            <span className={`status-badge ${statusBadge.className}`}>{statusBadge.label}</span>
          </div>

          {formula && (
            <div className="panel-inset p-2.5">
              <p className="def-label">Formula specification</p>
              <code className="mt-1 block font-mono text-[11px] break-all text-accent">{formula}</code>
            </div>
          )}

          {interpretation && (
            <div>
              <p className="def-label">Statistical interpretation</p>
              <p className="mt-1 text-[13px] leading-[1.55] text-text-secondary">{interpretation}</p>
            </div>
          )}

          {limitation && (
            <div className="rounded-sm border-l-2 pl-3" style={{ borderLeftColor: 'var(--candidate-fg)' }}>
              <p className="def-label" style={{ color: 'var(--candidate-fg)' }}>
                Methodology limitation
              </p>
              <p className="mt-1 text-[13px] leading-[1.55] text-text-secondary">{limitation}</p>
            </div>
          )}

          {assumptions.length > 0 && (
            <div>
              <p className="def-label">Assumptions</p>
              <div className="mt-1.5 grid grid-cols-1 gap-2 sm:grid-cols-2">
                {assumptions.map((assumption, idx) => (
                  <div key={idx} className="panel-inset p-2">
                    <p className="mono-meta">{assumption.label}</p>
                    <p className="mt-0.5 font-mono text-[11px] text-text-primary">
                      {assumption.value}
                    </p>
                  </div>
                ))}
              </div>
            </div>
          )}

          <Link href="/methodology" className="text-link inline-block">
            Why this matters →
          </Link>
        </div>
      )}

      <div className="mt-auto pt-3">
        <div className="flex items-center justify-between gap-3 border-t border-border pt-3">
          {(disclosureParts.length > 0 || interpretation) && (
            <button
              type="button"
              onClick={() => setExpanded(!expanded)}
              className="btn btn-accent btn-sm -ml-3"
              aria-expanded={expanded}
            >
              <span className="hidden sm:inline">
                {expanded ? 'Hide detail' : disclosureParts.join(' · ') || 'Detail'}
              </span>
              <span className="sm:hidden">{expanded ? 'Hide' : 'Detail'}</span>
              <svg
                className={`h-3 w-3 transition-transform ${expanded ? 'rotate-180' : ''}`}
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                aria-hidden
              >
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
              </svg>
            </button>
          )}

          <div className="flex items-center gap-2">
            {runId && (
              <Link href={`/analysis/${runId}`} className="btn btn-secondary btn-sm">
                Audit run #{runId}
              </Link>
            )}

            {canExecute && onExecute && (
              <button
                type="button"
                onClick={onExecute}
                disabled={isExecuting}
                className="btn btn-primary btn-sm"
              >
                {isExecuting ? 'Calculating…' : `Calculate ${name}`}
              </button>
            )}
          </div>
        </div>
      </div>
    </article>
  );
}