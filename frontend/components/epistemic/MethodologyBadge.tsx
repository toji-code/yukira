import React from 'react';

interface MethodologyBadgeProps {
  status: 'CANDIDATE' | 'APPROVED' | 'UNRESOLVED' | string;
  convention?: string;
  size?: 'sm' | 'md';
}

export function MethodologyBadge({
  status,
  convention,
  size = 'sm',
}: MethodologyBadgeProps) {
  const isApproved = status === 'APPROVED';
  const sizeClasses = size === 'sm' ? 'px-2 py-0.5 text-[11px]' : 'px-2.5 py-1 text-xs';

  return (
    <span
      className={`inline-flex items-center gap-1.5 font-mono font-medium rounded border ${sizeClasses} ${
        isApproved
          ? 'bg-emerald-50 text-emerald-700 border-emerald-300 dark:bg-emerald-950/40 dark:text-emerald-300 dark:border-emerald-700'
          : 'bg-amber-50 text-amber-800 border-amber-300 dark:bg-amber-950/40 dark:text-amber-300 dark:border-amber-700'
      }`}
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
      <span className="w-1.5 h-1.5 rounded-full bg-current" />
      <span>{status}</span>
      {convention && <span className="opacity-70 text-[10px]">({convention})</span>}
    </span>
  );
}
