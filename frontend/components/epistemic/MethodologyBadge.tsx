import React from 'react';

interface MethodologyBadgeProps {
  status: 'CANDIDATE' | 'APPROVED' | 'UNRESOLVED' | string;
  convention?: string;
  size?: 'sm' | 'md';
}

const STATE_CLASS: Record<string, string> = {
  APPROVED: 'state-approved',
  CANDIDATE: 'state-candidate',
  UNRESOLVED: 'state-unavailable',
};

function resolveStateClass(status: string): string {
  const direct = STATE_CLASS[status];
  if (direct) return direct;
  const upper = status.toUpperCase();
  if (STATE_CLASS[upper]) return STATE_CLASS[upper];
  if (upper.includes('IMPLEMENT') || upper.includes('OPERATIONAL')) return 'state-operational';
  if (upper.includes('NOT_VALID') || upper.includes('UNAVAILABLE')) return 'state-unavailable';
  return 'state-info';
}

/**
 * Governance lifecycle marker.
 *
 * Implementation is not validation and this badge must never imply otherwise.
 * The status token is always visible on the metric tile rather than hidden
 * behind a disclosure.
 */
export function MethodologyBadge({
  status,
  convention,
  size = 'sm',
}: MethodologyBadgeProps) {
  const isApproved = status === 'APPROVED';

  return (
    <span
      className={`status-badge ${resolveStateClass(status)} ${size === 'md' ? 'h-6 px-2 text-[11px]' : ''}`}
      title={
        isApproved
          ? 'Officially approved production methodology'
          : convention
            ? `Candidate / Unapproved Methodology: ${convention}`
            : 'Candidate / Unapproved parameter convention'
      }
      role="status"
      aria-label={`Methodology status: ${status}`}
    >
      <span className="status-dot" aria-hidden />
      {status}
      {convention && <span className="opacity-70">{convention}</span>}
    </span>
  );
}