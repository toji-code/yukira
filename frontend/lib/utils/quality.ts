import {
  QualityAssessment,
  VerificationStatus,
  RevisionStatus,
  TemporalStatus,
  PresenceStatus,
  IntegrityCondition,
} from '@/types/quality';

/**
 * Six-dimensional data quality taxonomy presentation.
 *
 * `label` and `tooltip` are epistemic content and are reproduced verbatim from
 * the frozen taxonomy — they are never paraphrased. Only the visual encoding
 * changed: colour now means *state*, never category. Every dimension resolves
 * into the same semantic vocabulary used by governance badges, so the two
 * systems cannot drift apart visually.
 */
export interface QualityBadgeStyle {
  label: string;
  className: string;
  tooltip: string;
}

const UNKNOWN: QualityBadgeStyle['className'] = 'state-unavailable';

export function getQualityAssessmentStyle(status: QualityAssessment): QualityBadgeStyle {
  switch (status) {
    case 'VALID':
      return {
        label: 'Valid',
        className: 'state-approved',
        tooltip: 'Conforms to schema and historical sanity thresholds.',
      };
    case 'SUSPICIOUS':
      return {
        label: 'Suspicious',
        className: 'state-candidate',
        tooltip: 'Statistical anomaly detected (requires verification).',
      };
    case 'INVALID':
      return {
        label: 'Invalid',
        className: 'state-critical',
        tooltip: 'Rejected by validation rules (excluded from calculation).',
      };
    default:
      return {
        label: String(status),
        className: UNKNOWN,
        tooltip: 'Unknown quality status.',
      };
  }
}

export function getRevisionStatusStyle(status: RevisionStatus): QualityBadgeStyle {
  switch (status) {
    case 'ORIGINAL':
      return {
        label: 'Original',
        className: 'state-info',
        tooltip: 'Original observation as first published.',
      };
    case 'REVISED':
      return {
        label: 'Revised',
        className: 'state-operational',
        tooltip: 'Authoritative revision after validation (does not imply prior error).',
      };
    case 'SUPERSEDED':
      return {
        label: 'Superseded',
        className: 'state-unavailable',
        tooltip: 'Replaced by subsequent authoritative revision.',
      };
    default:
      return {
        label: String(status),
        className: UNKNOWN,
        tooltip: 'Unknown revision status.',
      };
  }
}

export function getTemporalStatusStyle(status: TemporalStatus): QualityBadgeStyle {
  switch (status) {
    case 'CURRENT':
      return {
        label: 'Current',
        className: 'state-info',
        tooltip: 'Within expected observation latency window.',
      };
    case 'STALE':
      return {
        label: 'Stale',
        className: 'state-candidate',
        tooltip: 'Missing expected updates; historical data unchanged.',
      };
    default:
      return {
        label: String(status),
        className: UNKNOWN,
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
        className: 'state-candidate',
        tooltip:
          'Analytical EOD cutoff convention applied for historical batch ingestion. Factual source availability timestamp is unrecorded upstream.',
      };
    default:
      return {
        label: String(semantic),
        className: UNKNOWN,
        tooltip: 'Source availability semantic.',
      };
  }
}

export function getPresenceStatusStyle(status: PresenceStatus): QualityBadgeStyle {
  switch (status) {
    case 'AVAILABLE':
      return {
        label: 'Available',
        className: 'state-info',
        tooltip: 'Observation present in source dataset.',
      };
    case 'MISSING':
      return {
        label: 'Missing',
        // Dashed rule: expected-but-absent. Structurally distinct from NOT_APPLICABLE,
        // which was never applicable at all. Both carry the `unavailable` semantic hue.
        className: 'state-unavailable border-dashed',
        tooltip: 'Expected observation not found in dataset.',
      };
    case 'NOT_APPLICABLE':
      return {
        label: 'N/A',
        className: 'state-unavailable',
        tooltip: 'Metric not applicable for this entity or period.',
      };
    default:
      return {
        label: String(status),
        className: UNKNOWN,
        tooltip: 'Unknown presence status.',
      };
  }
}

export function getVerificationStatusStyle(status: VerificationStatus): QualityBadgeStyle {
  switch (status) {
    case 'VERIFIED':
      return {
        label: 'Verified',
        className: 'state-approved',
        tooltip: 'Observation independently reconciled and verified against external source.',
      };
    case 'UNVERIFIED':
      return {
        label: 'Unverified',
        className: 'state-candidate',
        tooltip: 'Raw observation not yet reconciled against independent secondary source.',
      };
    default:
      return {
        label: String(status),
        className: UNKNOWN,
        tooltip: 'Unknown verification status.',
      };
  }
}

export function getIntegrityConditionStyle(status: IntegrityCondition): QualityBadgeStyle {
  switch (status) {
    case 'NONE':
      return {
        label: 'Normal',
        className: 'state-info',
        tooltip: 'No integrity anomalies detected.',
      };
    case 'DUPLICATE':
      return {
        label: 'Duplicate',
        className: 'state-candidate',
        tooltip: 'Duplicate observations detected across ingestion channels.',
      };
    case 'CONFLICTING':
      return {
        label: 'Conflicting',
        className: 'state-risk',
        tooltip: 'Conflicting observation values observed for the same bitemporal key.',
      };
    default:
      return {
        label: String(status),
        className: UNKNOWN,
        tooltip: 'Unknown integrity condition.',
      };
  }
}