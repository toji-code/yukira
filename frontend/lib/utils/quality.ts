import {
  QualityAssessment,
  VerificationStatus,
  RevisionStatus,
  TemporalStatus,
  PresenceStatus,
  IntegrityCondition,
} from '@/types/quality';

export interface QualityBadgeStyle {
  label: string;
  className: string;
  tooltip: string;
}

export function getQualityAssessmentStyle(status: QualityAssessment): QualityBadgeStyle {
  switch (status) {
    case 'VALID':
      return {
        label: 'Valid',
        className: 'bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-400 dark:border-emerald-800',
        tooltip: 'Conforms to schema and historical sanity thresholds.',
      };
    case 'SUSPICIOUS':
      return {
        label: 'Suspicious',
        className: 'bg-amber-50 text-amber-700 border-amber-200 dark:bg-amber-950/40 dark:text-amber-400 dark:border-amber-800',
        tooltip: 'Statistical anomaly detected (requires verification).',
      };
    case 'INVALID':
      return {
        label: 'Invalid',
        className: 'bg-rose-50 text-rose-700 border-rose-200 dark:bg-rose-950/40 dark:text-rose-400 dark:border-rose-800',
        tooltip: 'Rejected by validation rules (excluded from calculation).',
      };
    default:
      return {
        label: String(status),
        className: 'bg-zinc-100 text-zinc-700 border-zinc-200 dark:bg-zinc-800 dark:text-zinc-300 dark:border-zinc-700',
        tooltip: 'Unknown quality status.',
      };
  }
}

export function getRevisionStatusStyle(status: RevisionStatus): QualityBadgeStyle {
  switch (status) {
    case 'ORIGINAL':
      return {
        label: 'Original',
        className: 'bg-zinc-50 text-zinc-600 border-zinc-200 dark:bg-zinc-900 dark:text-zinc-400 dark:border-zinc-800',
        tooltip: 'Original observation as first published.',
      };
    case 'REVISED':
      return {
        label: 'Revised',
        className: 'bg-sky-50 text-sky-700 border-sky-200 dark:bg-sky-950/40 dark:text-sky-400 dark:border-sky-800',
        tooltip: 'Authoritative revision after validation (does not imply prior error).',
      };
    case 'SUPERSEDED':
      return {
        label: 'Superseded',
        className: 'bg-purple-50 text-purple-700 border-purple-200 dark:bg-purple-950/40 dark:text-purple-400 dark:border-purple-800',
        tooltip: 'Replaced by subsequent authoritative revision.',
      };
    default:
      return {
        label: String(status),
        className: 'bg-zinc-100 text-zinc-700 border-zinc-200 dark:bg-zinc-800 dark:text-zinc-300 dark:border-zinc-700',
        tooltip: 'Unknown revision status.',
      };
  }
}

export function getTemporalStatusStyle(status: TemporalStatus): QualityBadgeStyle {
  switch (status) {
    case 'CURRENT':
      return {
        label: 'Current',
        className: 'bg-zinc-50 text-zinc-700 border-zinc-200 dark:bg-zinc-900 dark:text-zinc-300 dark:border-zinc-800',
        tooltip: 'Within expected observation latency window.',
      };
    case 'STALE':
      return {
        label: 'Stale',
        className: 'bg-amber-50 text-amber-700 border-amber-200 dark:bg-amber-950/40 dark:text-amber-400 dark:border-amber-800',
        tooltip: 'Missing expected updates; historical data unchanged.',
      };
    default:
      return {
        label: String(status),
        className: 'bg-zinc-100 text-zinc-700 border-zinc-200 dark:bg-zinc-800 dark:text-zinc-300 dark:border-zinc-700',
        tooltip: 'Unknown temporal freshness status.',
      };
  }
}

export function getSourceAvailabilitySemanticStyle(semantic: string): QualityBadgeStyle {
  switch (semantic) {
    case 'HISTORICAL_BACKFILL':
    case 'CONVENTION_EOD_HISTORICAL_CUTOFF':
      return {
        label: 'Historical Backfill (EOD Cutoff)',
        className: 'bg-indigo-50 text-indigo-700 border-indigo-200 dark:bg-indigo-950/40 dark:text-indigo-400 dark:border-indigo-800',
        tooltip: 'Analytical EOD cutoff convention applied for historical batch ingestion. Factual source availability timestamp is unrecorded upstream.',
      };
    default:
      return {
        label: String(semantic),
        className: 'bg-zinc-100 text-zinc-700 border-zinc-200 dark:bg-zinc-800 dark:text-zinc-300 dark:border-zinc-700',
        tooltip: 'Source availability semantic.',
      };
  }
}

export function getPresenceStatusStyle(status: PresenceStatus): QualityBadgeStyle {
  switch (status) {
    case 'AVAILABLE':
      return {
        label: 'Available',
        className: 'bg-zinc-50 text-zinc-700 border-zinc-200 dark:bg-zinc-900 dark:text-zinc-300 dark:border-zinc-800',
        tooltip: 'Observation present in source dataset.',
      };
    case 'MISSING':
      return {
        label: 'Missing',
        className: 'bg-neutral-100 text-neutral-600 border-dashed border-neutral-300 dark:bg-neutral-900 dark:text-neutral-400 dark:border-neutral-700',
        tooltip: 'Expected observation not found in dataset.',
      };
    case 'NOT_APPLICABLE':
      return {
        label: 'N/A',
        className: 'bg-zinc-50 text-zinc-400 border-zinc-200 dark:bg-zinc-900 dark:text-zinc-500 dark:border-zinc-800',
        tooltip: 'Metric not applicable for this entity or period.',
      };
    default:
      return {
        label: String(status),
        className: 'bg-zinc-100 text-zinc-700 border-zinc-200 dark:bg-zinc-800 dark:text-zinc-300 dark:border-zinc-700',
        tooltip: 'Unknown presence status.',
      };
  }
}

export function getVerificationStatusStyle(status: VerificationStatus): QualityBadgeStyle {
  switch (status) {
    case 'VERIFIED':
      return {
        label: 'Verified',
        className: 'bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-400 dark:border-emerald-800',
        tooltip: 'Observation independently reconciled and verified against external source.',
      };
    case 'UNVERIFIED':
      return {
        label: 'Unverified',
        className: 'bg-amber-50 text-amber-700 border-amber-200 dark:bg-amber-950/40 dark:text-amber-400 dark:border-amber-800',
        tooltip: 'Raw observation not yet reconciled against independent secondary source.',
      };
    default:
      return {
        label: String(status),
        className: 'bg-zinc-100 text-zinc-700 border-zinc-200 dark:bg-zinc-800 dark:text-zinc-300 dark:border-zinc-700',
        tooltip: 'Unknown verification status.',
      };
  }
}

export function getIntegrityConditionStyle(status: IntegrityCondition): QualityBadgeStyle {
  switch (status) {
    case 'NONE':
      return {
        label: 'Normal',
        className: 'bg-zinc-50 text-zinc-600 border-zinc-200 dark:bg-zinc-900 dark:text-zinc-400 dark:border-zinc-800',
        tooltip: 'No integrity anomalies detected.',
      };
    case 'DUPLICATE':
      return {
        label: 'Duplicate',
        className: 'bg-amber-50 text-amber-700 border-amber-200 dark:bg-amber-950/40 dark:text-amber-400 dark:border-amber-800',
        tooltip: 'Duplicate observations detected across ingestion channels.',
      };
    case 'CONFLICTING':
      return {
        label: 'Conflicting',
        className: 'bg-rose-50 text-rose-700 border-rose-200 dark:bg-rose-950/40 dark:text-rose-400 dark:border-rose-800',
        tooltip: 'Conflicting observation values observed for the same bitemporal key.',
      };
    default:
      return {
        label: String(status),
        className: 'bg-zinc-100 text-zinc-700 border-zinc-200 dark:bg-zinc-800 dark:text-zinc-300 dark:border-zinc-700',
        tooltip: 'Unknown integrity condition.',
      };
  }
}
