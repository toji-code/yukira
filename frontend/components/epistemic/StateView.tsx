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
}

export function StateView({ kind, title, message, action }: StateViewProps) {
  const configs: Record<
    StateViewKind,
    {
      defaultTitle: string;
      defaultMessage: string;
      containerClass: string;
      iconColor: string;
      renderIcon: () => React.ReactNode;
    }
  > = {
    loading: {
      defaultTitle: 'Loading Analysis…',
      defaultMessage: 'Querying point-in-time calculation engine and bitemporal observations.',
      containerClass: 'border-border bg-surface',
      iconColor: 'text-accent',
      renderIcon: () => (
        <svg className="h-8 w-8 animate-spin" fill="none" viewBox="0 0 24 24">
          <circle className="opacity-20" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" />
          <path className="opacity-80" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
        </svg>
      ),
    },
    empty: {
      defaultTitle: 'No Validated Data Available',
      defaultMessage: 'No validated data is currently available for the requested parameters.',
      containerClass: 'border-border border-dashed bg-surface',
      iconColor: 'text-text-muted',
      renderIcon: () => (
        <svg className="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-3.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4" />
        </svg>
      ),
    },
    insufficient_evidence: {
      defaultTitle: 'Insufficient Evidence',
      defaultMessage: 'Insufficient validated evidence for this analysis. Minimum observation threshold not met.',
      containerClass: 'border-amber-500/30 border-dashed bg-amber-500/5',
      iconColor: 'text-amber-600 dark:text-amber-400',
      renderIcon: () => (
        <svg className="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
        </svg>
      ),
    },
    data_quality_limitation: {
      defaultTitle: 'Data Quality Constraint',
      defaultMessage: 'Analysis is constrained by data-quality limitations or pending observation restatements.',
      containerClass: 'border-amber-500/30 bg-amber-500/10',
      iconColor: 'text-amber-600 dark:text-amber-400',
      renderIcon: () => (
        <svg className="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
        </svg>
      ),
    },
    unavailable: {
      defaultTitle: 'Analysis Service Unavailable',
      defaultMessage: 'Analysis service is temporarily unavailable. Verify that the Spring Boot backend is active.',
      containerClass: 'border-danger/30 bg-danger/5',
      iconColor: 'text-danger',
      renderIcon: () => (
        <svg className="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M18.364 5.636a9 9 0 010 12.728m0 0l-2.829-2.829m2.829 2.829L21 21M15.536 8.464a5 5 0 010 7.072m0 0l-2.829-2.829m-4.243 4.243a9 9 0 01-12.728-12.728m0 0l2.829 2.829m-2.829-2.829L3 3m5.464 12.536a5 5 0 01-.707-.707m0 0L4.93 12.001M12 12h.01" />
        </svg>
      ),
    },
    error: {
      defaultTitle: 'Processing Failure',
      defaultMessage: 'An unexpected error occurred while executing the analytical workflow.',
      containerClass: 'border-danger/40 bg-danger/10',
      iconColor: 'text-danger',
      renderIcon: () => (
        <svg className="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
      ),
    },
  };

  const cfg = configs[kind];

  return (
    <div
      className={`rounded-xl p-8 text-center border ${cfg.containerClass} my-4 transition-all`}
      role="status"
      aria-live="polite"
    >
      <div className={`flex justify-center mb-3 ${cfg.iconColor}`} aria-hidden="true">
        {cfg.renderIcon()}
      </div>
      <h3 className="text-sm font-semibold text-text-primary font-mono mb-1">
        {title || cfg.defaultTitle}
      </h3>
      <p className="text-xs text-text-secondary max-w-md mx-auto mb-4 leading-relaxed font-sans">
        {message || cfg.defaultMessage}
      </p>
      {action && <div className="mt-2 flex justify-center">{action}</div>}
    </div>
  );
}
