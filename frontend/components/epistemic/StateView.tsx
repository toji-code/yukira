import React from 'react';

export type StateViewKind =
  | 'loading'
  | 'empty'
  | 'insufficient_evidence'
  | 'data_quality_limitation'
  | 'unavailable'
  | 'error';

interface StateViewProps {
  kind: StateViewKind;
  title?: string;
  message?: string;
  action?: React.ReactNode;
  /** Mono reason code surfaced on error and unavailable states. */
  reasonCode?: string;
}

interface StateConfig {
  defaultTitle: string;
  defaultMessage: string;
  /** Which visual treatment the state gets. */
  treatment: 'skeleton' | 'well' | 'note' | 'critical';
  badgeClass?: string;
  badgeLabel?: string;
}

/**
 * System-state surface.
 *
 * Loading renders skeletons matching the final layout geometry rather than a
 * spinner. Errors are scoped inline with a reason code. Empty and unavailable
 * states are dashed inset wells whose headline is the literal "Not available" —
 * never a zero, never a blank.
 */
export function StateView({ kind, title, message, action, reasonCode }: StateViewProps) {
  const configs: Record<StateViewKind, StateConfig> = {
    loading: {
      defaultTitle: 'Resolving point-in-time observations',
      defaultMessage: 'Querying the bitemporal ledger against the requested cutoffs.',
      treatment: 'skeleton',
    },
    empty: {
      defaultTitle: 'Not available',
      defaultMessage: 'No validated observation exists for the requested parameters.',
      treatment: 'well',
      badgeClass: 'state-unavailable',
      badgeLabel: 'Empty',
    },
    insufficient_evidence: {
      defaultTitle: 'Insufficient evidence',
      defaultMessage:
        'The minimum observation threshold for this methodology is not met. No value is reported.',
      treatment: 'well',
      badgeClass: 'state-candidate',
      badgeLabel: 'Insufficient evidence',
    },
    data_quality_limitation: {
      defaultTitle: 'Data quality constraint',
      defaultMessage:
        'The result is constrained by data-quality flags or a pending observation restatement. Treat it as provisional.',
      treatment: 'note',
      badgeClass: 'state-candidate',
      badgeLabel: 'Provisional',
    },
    unavailable: {
      defaultTitle: 'Not available',
      defaultMessage:
        'This surface depends on an evidence layer that did not respond. No value has been substituted.',
      treatment: 'critical',
      badgeClass: 'state-critical',
      badgeLabel: 'Unreachable',
    },
    error: {
      defaultTitle: 'Processing failure',
      defaultMessage:
        'The analytical workflow did not complete. The rest of this page remains usable.',
      treatment: 'critical',
      badgeClass: 'state-critical',
      badgeLabel: 'Error',
    },
  };

  const cfg = configs[kind];

  if (cfg.treatment === 'skeleton') {
    return (
      <div className="py-2" role="status" aria-live="polite">
        <p className="mono-meta">{title || cfg.defaultTitle}</p>
        <div className="mt-3 space-y-2" aria-hidden>
          <div className="skeleton h-3 w-1/3" />
          <div className="skeleton h-3 w-2/3" />
          <div className="skeleton h-3 w-1/2" />
          <div className="skeleton h-3 w-1/4" />
        </div>
        <p className="sr-only">{message || cfg.defaultMessage}</p>
      </div>
    );
  }

  if (cfg.treatment === 'critical') {
    return (
      <div className="state-panel-error" role="alert">
        <div className="flex flex-wrap items-center gap-2">
          <span className={`status-badge ${cfg.badgeClass}`}>
            <span className="status-dot" aria-hidden />
            {cfg.badgeLabel}
          </span>
          <h3 className="text-[15px] font-semibold text-text-primary">
            {title || cfg.defaultTitle}
          </h3>
          {reasonCode && <span className="mono-meta">reason_code {reasonCode}</span>}
        </div>
        <p className="mt-2 max-w-[68ch] text-[13px] leading-[1.55] text-text-secondary">
          {message || cfg.defaultMessage}
        </p>
        {action && <div className="mt-3">{action}</div>}
      </div>
    );
  }

  return (
    <div
      className={cfg.treatment === 'well' ? 'state-well' : 'panel-inset p-4'}
      role="status"
      aria-live="polite"
    >
      <div className="flex flex-wrap items-center justify-center gap-2">
        {cfg.badgeClass && cfg.badgeLabel && (
          <span className={`status-badge ${cfg.badgeClass}`}>
            <span className="status-dot" aria-hidden />
            {cfg.badgeLabel}
          </span>
        )}
        <span className="font-mono text-[13px] font-medium text-text-disabled">
          {title || cfg.defaultTitle}
        </span>
        {reasonCode && <span className="mono-meta">{reasonCode}</span>}
      </div>
      <p className="mx-auto mt-2 max-w-[52ch] text-[13px] leading-[1.55] text-text-secondary">
        {message || cfg.defaultMessage}
      </p>
      {action && <div className="mt-4 flex justify-center">{action}</div>}
    </div>
  );
}