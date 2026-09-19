import React from 'react';
import {
  QualityAssessment,
  RevisionStatus,
  TemporalStatus,
  PresenceStatus,
} from '@/types/quality';
import {
  getQualityAssessmentStyle,
  getRevisionStatusStyle,
  getTemporalStatusStyle,
  getPresenceStatusStyle,
  QualityBadgeStyle,
} from '@/lib/utils/quality';

interface DataQualityBadgeProps {
  type: 'assessment' | 'revision' | 'temporal' | 'presence';
  status: QualityAssessment | RevisionStatus | TemporalStatus | PresenceStatus | string;
  size?: 'sm' | 'md';
}

export function DataQualityBadge({ type, status, size = 'sm' }: DataQualityBadgeProps) {
  let style: QualityBadgeStyle;

  switch (type) {
    case 'assessment':
      style = getQualityAssessmentStyle(status as QualityAssessment);
      break;
    case 'revision':
      style = getRevisionStatusStyle(status as RevisionStatus);
      break;
    case 'temporal':
      style = getTemporalStatusStyle(status as TemporalStatus);
      break;
    case 'presence':
      style = getPresenceStatusStyle(status as PresenceStatus);
      break;
    default:
      style = {
        label: String(status),
        className: 'bg-zinc-100 text-zinc-700 border-zinc-200 dark:bg-zinc-800 dark:text-zinc-300 dark:border-zinc-700',
        tooltip: 'General status',
      };
  }

  const sizeClasses = size === 'sm' ? 'px-2 py-0.5 text-[11px]' : 'px-2.5 py-1 text-xs';

  return (
    <span
      className={`inline-flex items-center font-mono font-medium rounded border ${sizeClasses} ${style.className}`}
      title={style.tooltip}
      role="status"
      aria-label={`${type}: ${style.label}`}
    >
      {style.label}
    </span>
  );
}
