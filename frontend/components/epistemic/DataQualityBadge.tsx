import React from 'react';
import {
  QualityAssessment,
  RevisionStatus,
  TemporalStatus,
  PresenceStatus,
  VerificationStatus,
  IntegrityCondition,
} from '@/types/quality';
import {
  getQualityAssessmentStyle,
  getRevisionStatusStyle,
  getTemporalStatusStyle,
  getPresenceStatusStyle,
  getVerificationStatusStyle,
  getIntegrityConditionStyle,
  QualityBadgeStyle,
} from '@/lib/utils/quality';

interface DataQualityBadgeProps {
  type: 'assessment' | 'revision' | 'temporal' | 'presence' | 'verification' | 'integrity';
  status:
    | QualityAssessment
    | RevisionStatus
    | TemporalStatus
    | PresenceStatus
    | VerificationStatus
    | IntegrityCondition
    | string;
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
    case 'verification':
      style = getVerificationStatusStyle(status as VerificationStatus);
      break;
    case 'integrity':
      style = getIntegrityConditionStyle(status as IntegrityCondition);
      break;
    default:
      style = {
        label: String(status),
        className: 'state-unavailable',
        tooltip: 'General status',
      };
  }

  return (
    <span
      className={`status-badge ${style.className} ${size === 'md' ? 'h-6 px-2 text-[11px]' : ''}`}
      title={style.tooltip}
      role="status"
      aria-label={`${type}: ${style.label}`}
    >
      <span className="status-dot" aria-hidden />
      {style.label}
    </span>
  );
}