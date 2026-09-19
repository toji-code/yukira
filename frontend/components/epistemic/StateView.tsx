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
    { defaultTitle: string; defaultMessage: string; borderClass: string; icon: string }
  > = {
    loading: {
      defaultTitle: 'Loading Analysis…',
      defaultMessage: 'Querying point-in-time calculation engine and bitemporal observations.',
      borderClass: 'border-zinc-200 dark:border-zinc-800',
      icon: '⏳',
    },
    empty: {
      defaultTitle: 'No Validated Data Available',
      defaultMessage: 'No validated data is currently available for the requested parameters.',
      borderClass: 'border-dashed border-zinc-300 dark:border-zinc-700',
      icon: '📂',
    },
    insufficient_evidence: {
      defaultTitle: 'Insufficient Evidence',
      defaultMessage: 'Insufficient validated evidence for this analysis. Minimum observation threshold not met.',
      borderClass: 'border-dashed border-amber-300 dark:border-amber-800 bg-amber-50/30 dark:bg-amber-950/10',
      icon: '⚠️',
    },
    data_quality_limitation: {
      defaultTitle: 'Data Quality Constraint',
      defaultMessage: 'Analysis is constrained by data-quality limitations or pending observation restatements.',
      borderClass: 'border-amber-300 dark:border-amber-700 bg-amber-50/20 dark:bg-amber-950/10',
      icon: '🔍',
    },
    unavailable: {
      defaultTitle: 'Analysis Service Unavailable',
      defaultMessage: 'Analysis service is temporarily unavailable. Verify that the Spring Boot backend is active.',
      borderClass: 'border-rose-300 dark:border-rose-800 bg-rose-50/30 dark:bg-rose-950/10',
      icon: '📡',
    },
    error: {
      defaultTitle: 'Processing Failure',
      defaultMessage: 'An unexpected error occurred while executing the analytical workflow.',
      borderClass: 'border-rose-300 dark:border-rose-800 bg-rose-50/30 dark:bg-rose-950/10',
      icon: '❌',
    },
  };

  const cfg = configs[kind];

  return (
    <div
      className={`rounded-lg p-8 text-center border ${cfg.borderClass} my-4 transition-all`}
      role="status"
      aria-live="polite"
    >
      <div className="text-3xl mb-3" aria-hidden="true">
        {cfg.icon}
      </div>
      <h3 className="text-base font-semibold text-zinc-900 dark:text-zinc-100 mb-1">
        {title || cfg.defaultTitle}
      </h3>
      <p className="text-sm text-zinc-600 dark:text-zinc-400 max-w-md mx-auto mb-4">
        {message || cfg.defaultMessage}
      </p>
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
}
