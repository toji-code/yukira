'use client';

import React, { useState } from 'react';
import Link from 'next/link';

export type FreshnessState =
  | 'CURRENT'
  | 'FRESH'
  | 'STALE'
  | 'PARTIAL'
  | 'UNAVAILABLE'
  | 'UNDETERMINED'
  | string;

export type MethodologyStatus =
  | 'APPROVED'
  | 'VALIDATED'
  | 'CANDIDATE'
  | 'IMPLEMENTED'
  | 'RETIRED'
  | string;

export type CoverageState =
  | 'COMPLETE'
  | 'SUFFICIENT'
  | 'INSUFFICIENT'
  | 'PARTIAL'
  | 'NOT_APPLICABLE'
  | string;

export interface EvidenceQualitySummaryProps {
  /** Optional title for the summary container */
  title?: string;
  /** Optional subtitle or scope identifier */
  subtitle?: string;
  /** Context or asset name (e.g., "HDFC Flexi Cap Fund", "Investor Portfolio") */
  contextName?: string;

  /** Point-in-time observation cutoff (YYYY-MM-DD) */
  asOfDate?: string | null;
  /** Temporal knowledge cutoff timestamp */
  knowledgeCutoff?: string | null;

  /** Evaluated freshness state according to active cadence policy */
  freshnessState?: FreshnessState | null;
  /** Applicable reporting cadence policy description */
  freshnessPolicy?: string | null;
  /** Number of verified observations in historical time-series */
  observationCount?: number | null;
  /** Days lag relative to current calendar date */
  daysLag?: number | null;

  /** Coverage status relative to requested horizon */
  coverageState?: CoverageState | null;
  /** Realized observation window (e.g. "2021-01-01 → 2024-01-15") */
  observationWindow?: string | null;
  /** Requested analytical lookback horizon (e.g., "3Y", "5Y", "10Y") */
  horizonRequested?: string | null;
  /** Specific coverage commentary or threshold verification */
  coverageNote?: string | null;

  /** Completeness of required input feeds (benchmark, risk-free rate, portfolio) */
  completenessState?: 'COMPLETE' | 'PARTIAL' | 'GAPS_DETECTED' | 'INPUT_UNAVAILABLE' | string | null;
  /** Missing inputs or unmapped benchmark series */
  missingInputs?: readonly string[] | string | null;
  /** Completeness explanation */
  completenessNote?: string | null;

  /** Formal quantitative governance lifecycle tier */
  methodologyStatus?: MethodologyStatus | null;
  /** Quantitative algorithm or kernel version */
  methodologyVersion?: string | null;

  /** Plain-language explanation of what this limitation means for relying on or comparing the result */
  interpretationConsequence?: string | React.ReactNode | null;

  /** Supported next investigation action label */
  nextStepLabel?: string;
  /** Supported next investigation route or link */
  nextStepHref?: string;
  /** Supported next investigation handler */
  nextStepAction?: () => void;

  /** Visual layout variant */
  variant?: 'panel' | 'banner' | 'card' | 'compact';
  /** Optional extra CSS classes */
  className?: string;
}

/**
 * Resolves standard badge styling for data freshness.
 */
function getFreshnessBadgeStyle(state?: FreshnessState | null): { className: string; label: string } {
  const norm = (state || 'UNDETERMINED').toUpperCase();
  if (norm === 'CURRENT' || norm === 'FRESH') {
    return { className: 'state-verified', label: 'DATA CURRENT' };
  }
  if (norm === 'STALE') {
    return { className: 'state-candidate', label: 'DATA STALE' };
  }
  if (norm === 'PARTIAL') {
    return { className: 'state-candidate', label: 'PARTIAL EVIDENCE' };
  }
  if (norm === 'UNAVAILABLE') {
    return { className: 'state-unavailable', label: 'UNAVAILABLE' };
  }
  return { className: 'state-info', label: norm || 'UNDETERMINED' };
}

/**
 * Resolves standard badge styling for governance methodology.
 * Strictly decoupled from data freshness: a fresh feed does not validate a formula,
 * and an approved formula run on historical data remains approved.
 */
function getMethodologyBadgeStyle(status?: MethodologyStatus | null): { className: string; label: string } {
  const norm = (status || 'CANDIDATE').toUpperCase();
  if (norm === 'APPROVED') {
    return { className: 'state-approved', label: 'APPROVED METHODOLOGY' };
  }
  if (norm === 'VALIDATED') {
    return { className: 'state-operational', label: 'VALIDATED METHODOLOGY' };
  }
  if (norm === 'CANDIDATE') {
    return { className: 'state-candidate', label: 'CANDIDATE SPECIFICATION' };
  }
  return { className: 'state-info', label: norm };
}

/**
 * Institutional Evidence-Quality Summary
 *
 * Implements a unified, investor-facing summary of observation freshness,
 * coverage sufficiency, completeness, and methodology governance.
 *
 * Epistemic Rules:
 * 1. Freshness vs Methodology Approval are decoupled.
 * 2. Observation Coverage vs Data Completeness are decoupled.
 * 3. Never approximate or fabricate missing observations.
 * 4. Stale valuations must never be described as current market value.
 */
export function EvidenceQualitySummary({
  title,
  subtitle,
  contextName,
  asOfDate,
  knowledgeCutoff,
  freshnessState = 'UNDETERMINED',
  freshnessPolicy,
  observationCount,
  daysLag,
  coverageState,
  observationWindow,
  horizonRequested,
  coverageNote,
  completenessState,
  missingInputs,
  completenessNote,
  methodologyStatus = 'CANDIDATE',
  methodologyVersion,
  interpretationConsequence,
  nextStepLabel = 'Inspect Evidence Lineage',
  nextStepHref,
  nextStepAction,
  variant = 'panel',
  className = '',
}: EvidenceQualitySummaryProps) {
  const [detailsOpen, setDetailsOpen] = useState(false);

  const isHistoricalPilotCutoff = asOfDate === '2024-01-15' || asOfDate?.startsWith('2024-01');
  const freshBadge = getFreshnessBadgeStyle(freshnessState);
  const methodBadge = getMethodologyBadgeStyle(methodologyStatus);

  // Default interpretation consequence if none provided
  const resolvedConsequence = interpretationConsequence || (
    isHistoricalPilotCutoff ? (
      <span>
        Observations end at <strong>2024-01-15</strong> (Phase 2I/2J frozen pilot baseline). This historical series cannot establish current market value or immediate liquidatable worth. All calculations reflect historical market conditions as of the specified cutoffs.
      </span>
    ) : freshnessState === 'STALE' ? (
      <span>
        Observations lag the current market cadence ({daysLag ? `${daysLag} days lag` : 'stale feed'}). Stale valuations must not be treated as current liquidatable values.
      </span>
    ) : freshnessState === 'UNAVAILABLE' ? (
      <span>
        Underlying observations are missing or uningested. No financial metrics can be calculated or estimated.
      </span>
    ) : (
      <span>
        Observations are evaluated through market close on {asOfDate || 'the specified date'}. Point-in-time constraints exclude all future revisions.
      </span>
    )
  );

  // COMPACT VARIANT: Inline disclosure chip/strip
  if (variant === 'compact') {
    return (
      <div
        className={`inline-flex flex-wrap items-center gap-2 rounded-lg border border-border bg-surface-raised/40 px-3 py-1.5 font-mono text-[11px] ${className}`}
        aria-label="Evidence Quality Summary"
      >
        <span className="text-text-muted uppercase text-[10px] font-bold">Evidence:</span>
        <span className={`status-badge ${freshBadge.className} text-[9.5px]`}>
          {asOfDate ? `As-of ${asOfDate}` : freshBadge.label}
        </span>
        {horizonRequested && (
          <span className="text-text-secondary">
            Horizon: <strong>{horizonRequested}</strong>
          </span>
        )}
        <span className={`status-badge ${methodBadge.className} text-[9.5px]`}>
          {methodBadge.label}
        </span>
        {nextStepHref && (
          <Link
            href={nextStepHref}
            className="text-accent hover:text-accent/80 underline font-semibold ml-auto"
          >
            {nextStepLabel} &rarr;
          </Link>
        )}
      </div>
    );
  }

  // BANNER VARIANT: Prominent executive alert strip
  if (variant === 'banner') {
    return (
      <div
        className={`panel p-4 border-l-4 ${
          freshnessState === 'STALE' || isHistoricalPilotCutoff
            ? 'border-l-amber-500 bg-amber-500/5 border-amber-500/20'
            : freshnessState === 'UNAVAILABLE'
            ? 'border-l-rose-500 bg-rose-500/5 border-rose-500/20'
            : 'border-l-accent bg-surface-raised/50 border-border'
        } ${className}`}
        role="region"
        aria-label="Evidence Quality Banner"
      >
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="space-y-1">
            <div className="flex flex-wrap items-center gap-2">
              <span className="eyebrow text-accent">EVIDENCE QUALITY &amp; LIMITATIONS</span>
              <span className={`status-badge ${freshBadge.className} text-[10px]`}>
                {freshBadge.label}
              </span>
              <span className={`status-badge ${methodBadge.className} text-[10px]`}>
                {methodBadge.label}
              </span>
              {asOfDate && (
                <span className="font-mono text-[11px] text-text-primary">
                  As-Of: <strong>{asOfDate}</strong>
                </span>
              )}
            </div>
            <div className="text-[12.5px] leading-relaxed text-text-secondary font-sans pt-0.5">
              {resolvedConsequence}
            </div>
          </div>

          <div className="flex items-center gap-2 shrink-0 self-start sm:self-center">
            {nextStepHref && (
              <Link
                href={nextStepHref}
                className="btn btn-secondary btn-sm font-mono text-[11px] text-accent border-accent/30 hover:bg-accent/10 whitespace-nowrap"
              >
                <span>{nextStepLabel}</span>
                <span>&rarr;</span>
              </Link>
            )}
            {nextStepAction && (
              <button
                type="button"
                onClick={nextStepAction}
                className="btn btn-secondary btn-sm font-mono text-[11px] text-accent border-accent/30 hover:bg-accent/10 whitespace-nowrap"
              >
                <span>{nextStepLabel}</span>
                <span>&rarr;</span>
              </button>
            )}
          </div>
        </div>
      </div>
    );
  }

  // CARD VARIANT: Structured card for candidate items or screener grids
  if (variant === 'card') {
    return (
      <div
        className={`panel p-4 space-y-3 border-l-2 border-l-accent bg-surface rounded-xl ${className}`}
        aria-label="Evidence Quality Card"
      >
        <div className="flex items-start justify-between gap-2 border-b border-border pb-2.5">
          <div>
            <span className="font-mono text-[10px] uppercase tracking-wider text-text-muted font-bold block">
              {contextName ? `Evidence Baseline · ${contextName}` : 'Evidence Baseline'}
            </span>
            <h4 className="font-sans font-bold text-sm text-text-primary mt-0.5">
              {title || 'Observation Freshness & Limitations'}
            </h4>
          </div>
          <div className="flex flex-col items-end gap-1">
            <span className={`status-badge ${freshBadge.className} text-[9.5px]`}>
              {freshBadge.label}
            </span>
            <span className={`status-badge ${methodBadge.className} text-[9.5px]`}>
              {methodBadge.label}
            </span>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-2 font-mono text-[11px]">
          <div>
            <span className="text-text-muted text-[10px] block">Observation As-Of:</span>
            <span className="font-semibold text-text-primary">{asOfDate || 'Not available'}</span>
          </div>
          <div>
            <span className="text-text-muted text-[10px] block">Observation Count:</span>
            <span className="text-text-primary">
              {observationCount !== undefined && observationCount !== null
                ? `${observationCount.toLocaleString()} dates`
                : 'Not reported'}
            </span>
          </div>
          {horizonRequested && (
            <div>
              <span className="text-text-muted text-[10px] block">Horizon Window:</span>
              <span className="text-text-primary">{horizonRequested}</span>
            </div>
          )}
          {coverageState && (
            <div>
              <span className="text-text-muted text-[10px] block">Coverage Sufficiency:</span>
              <span className="text-text-primary">{coverageState}</span>
            </div>
          )}
        </div>

        <div className="p-2.5 rounded bg-surface-raised/50 border border-border/80 text-[11.5px] leading-relaxed text-text-secondary font-sans">
          <strong className="block text-text-primary font-mono text-[10.5px] uppercase mb-0.5">
            Interpretation Consequence:
          </strong>
          {resolvedConsequence}
        </div>

        {nextStepHref && (
          <div className="pt-1">
            <Link
              href={nextStepHref}
              className="font-mono text-[11px] text-accent hover:underline flex items-center gap-1 font-semibold"
            >
              <span>{nextStepLabel}</span>
              <span>&rarr;</span>
            </Link>
          </div>
        )}
      </div>
    );
  }

  // DEFAULT 'panel' VARIANT: Full institutional multi-column due diligence container
  return (
    <section
      className={`panel p-5 space-y-4 rounded-xl border border-border bg-surface ${className}`}
      aria-label="Unified Evidence Quality, Coverage & Limitations Center"
    >
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border pb-3">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <p className="eyebrow text-accent">
              {contextName ? `EVIDENCE QUALITY · ${contextName}` : 'INSTITUTIONAL EVIDENCE QUALITY & LIMITATIONS'}
            </p>
            <span className="font-mono text-[10px] px-2 py-0.5 rounded border border-border bg-surface-raised text-text-secondary">
              Zero Synthetic Backfill · Non-Fabricated
            </span>
          </div>
          <h3 className="mt-1 text-h3 text-text-primary">
            {title || 'Observation Freshness, History Coverage & Analytical Limitations'}
          </h3>
          {subtitle && (
            <p className="mono-meta mt-0.5">
              {subtitle}
            </p>
          )}
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <span className={`status-badge ${freshBadge.className} text-[10.5px]`}>
            {freshBadge.label}
          </span>
          <span className={`status-badge ${methodBadge.className} text-[10.5px]`}>
            {methodBadge.label}
          </span>
          <button
            type="button"
            onClick={() => setDetailsOpen(!detailsOpen)}
            className="btn btn-secondary btn-sm font-mono text-[11px]"
            aria-expanded={detailsOpen}
          >
            {detailsOpen ? 'Hide Methodology Details' : 'Methodology & Cadence Details'}
          </button>
        </div>
      </div>

      {/* 4-Column Key Metrics Grid */}
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4 font-mono text-[11px]">
        {/* Col 1: As-of Date */}
        <div className="panel-inset p-3 bg-surface-raised/40">
          <span className="def-label">Observation Cutoff (As-Of)</span>
          <p className="mt-1 font-semibold text-text-primary text-[14px]">
            {asOfDate || 'Not available'}
          </p>
          <p className="mt-1 text-[11px] text-text-tertiary font-sans">
            {knowledgeCutoff
              ? `Knowledge cutoff: ${knowledgeCutoff.split('T')[0]}`
              : 'Strict point-in-time boundary'}
          </p>
        </div>

        {/* Col 2: Freshness Cadence */}
        <div className="panel-inset p-3 bg-surface-raised/40">
          <span className="def-label">Freshness Cadence Status</span>
          <div className="mt-1 flex items-center gap-2">
            <span className={`status-badge ${freshBadge.className} text-[10px]`}>
              {freshnessState || 'UNDETERMINED'}
            </span>
            {daysLag !== undefined && daysLag !== null && (
              <span className="text-text-muted text-[11px]">({daysLag}d lag)</span>
            )}
          </div>
          <p className="mt-1 text-[11px] text-text-tertiary font-sans truncate" title={freshnessPolicy || 'AMFI daily NAV policy'}>
            {freshnessPolicy || 'AMFI daily NAV cadence policy (≤ 3d lag)'}
          </p>
        </div>

        {/* Col 3: Observation Coverage */}
        <div className="panel-inset p-3 bg-surface-raised/40">
          <span className="def-label">Observation Coverage</span>
          <p className="mt-1 font-semibold text-text-primary text-[13px]">
            {observationCount !== undefined && observationCount !== null
              ? `${observationCount.toLocaleString()} Authentic Dates`
              : 'History uncounted'}
          </p>
          <p className="mt-1 text-[11px] text-text-tertiary font-sans truncate" title={observationWindow || 'Point-in-time ledger window'}>
            {observationWindow || (horizonRequested ? `${horizonRequested} lookback window` : 'Time-series range')}
          </p>
        </div>

        {/* Col 4: Governance Tier */}
        <div className="panel-inset p-3 bg-surface-raised/40">
          <span className="def-label">Methodology Governance</span>
          <div className="mt-1 flex items-center gap-2">
            <span className={`status-badge ${methodBadge.className} text-[10px]`}>
              {methodologyStatus || 'CANDIDATE'}
            </span>
          </div>
          <p className="mt-1 text-[11px] text-text-tertiary font-sans">
            {methodologyVersion ? `Spec version: ${methodologyVersion}` : 'Decoupled from data freshness'}
          </p>
        </div>
      </div>

      {/* Practical Interpretation Consequence (Tri-Partite Callout) */}
      <div className="p-4 rounded-xl border border-border bg-surface-raised/50 space-y-2">
        <div className="flex items-center gap-2">
          <span className="font-mono text-[10.5px] uppercase tracking-wider text-accent font-bold">
            Practical Interpretation Consequence for Investors:
          </span>
        </div>
        <div className="text-[12.5px] leading-relaxed text-text-secondary font-sans">
          {resolvedConsequence}
        </div>
        {coverageNote && (
          <p className="text-[11.5px] font-mono text-text-tertiary border-t border-border/50 pt-2 mt-1">
            Coverage note: {coverageNote}
          </p>
        )}
        {completenessNote && (
          <p className="text-[11.5px] font-mono text-text-tertiary">
            Completeness note: {completenessNote}
          </p>
        )}
        {missingInputs && (
          <p className="text-[11.5px] font-mono text-rose-400">
            Missing required inputs: {Array.isArray(missingInputs) ? missingInputs.join(', ') : missingInputs}
          </p>
        )}
      </div>

      {/* Collapsible Methodology & Cadence Technical Disclosures */}
      {detailsOpen && (
        <div className="p-4 rounded-xl border border-border/80 bg-surface-raised/20 space-y-3 font-mono text-xs animate-fadeIn">
          <div className="flex items-center justify-between border-b border-border/50 pb-2">
            <span className="font-bold text-text-primary uppercase tracking-wider text-[11px]">
              Quantitative Governance &amp; Look-Ahead Bias Rules
            </span>
            <span className="text-text-muted text-[10px]">Authoritative YUKIRA Policy</span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-text-secondary leading-relaxed font-sans text-[12px]">
            <div>
              <strong className="block text-text-primary font-mono text-[11px] mb-1">
                1. Freshness vs Methodology Approval Separation
              </strong>
              <p>
                A metric calculated on fresh market data remains a <code>CANDIDATE</code> specification until validated against external multi-regime benchmarks. Conversely, an <code>APPROVED</code> formula evaluated on historical frozen data (such as 2024-01-15) remains methodologically sound, but its output must be interpreted strictly as historical evidence, never as current valuation.
              </p>
            </div>
            <div>
              <strong className="block text-text-primary font-mono text-[11px] mb-1">
                2. Horizon Sufficiency &amp; Missing Inputs
              </strong>
              <p>
                3-Year metrics require &ge; 700 trading days; 5-Year requires &ge; 1,200 trading days; 10-Year requires &ge; 2,400 trading days. Where benchmark series or risk-free yields are missing or unmapped, benchmark-relative alpha or Sharpe ratios report "Input Unavailable" and are never approximated or substituted with zeros.
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Supported Next Investigation Step Footer */}
      {(nextStepHref || nextStepAction) && (
        <div className="flex flex-wrap items-center justify-between gap-3 pt-2 border-t border-border">
          <span className="font-mono text-[11px] text-text-tertiary">
            Next Supported Allocator Diligence Step:
          </span>
          {nextStepHref ? (
            <Link
              href={nextStepHref}
              className="btn btn-secondary btn-sm font-mono text-[11px] text-accent border-accent/30 hover:bg-accent/10 flex items-center gap-1.5"
            >
              <span>{nextStepLabel}</span>
              <span>&rarr;</span>
            </Link>
          ) : (
            <button
              type="button"
              onClick={nextStepAction}
              className="btn btn-secondary btn-sm font-mono text-[11px] text-accent border-accent/30 hover:bg-accent/10 flex items-center gap-1.5"
            >
              <span>{nextStepLabel}</span>
              <span>&rarr;</span>
            </button>
          )}
        </div>
      )}
    </section>
  );
}
