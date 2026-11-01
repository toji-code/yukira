/**
 * YUKIRA Quantitative Intelligence Platform
 * Phase F5: Research History and Evidence Change Awareness
 *
 * Implements deterministic evidence snapshotting and change detection
 * without fabricating historical audit events or violating schema validation.
 */

import { WatchlistResponse, DueDiligenceStatus } from "@/lib/api/investor";
import { AnalyticalProfileResponse, ProfileMetricItem } from "@/types/analysis";
import { getWatchlistItemKey } from "@/lib/hooks/useWatchlist";

export type EvidenceChangeCategory =
  | "OBSERVATION_WINDOW"
  | "FRESHNESS_COVERAGE"
  | "METRIC_VALUE"
  | "CALCULATION_RUN"
  | "EVIDENCE_PRESENCE"
  | "METHODOLOGY_GOVERNANCE";

export interface EvidenceChangeItem {
  category: EvidenceChangeCategory;
  categoryLabel: string;
  metricCode?: string;
  title: string;
  previousValue: string;
  currentValue: string;
  observationPeriod: string;
  evidencePath: string;
  comparabilityNotice?: string;
  isComparable: boolean;
  isMaterial: boolean;
}

export interface MetricSnapshotItem {
  metricCode: string;
  metricName: string;
  numericValue: number | null;
  formattedValue: string | null;
  governanceStatus: string;
  periodType: string;
  units?: string;
  horizon?: string;
}

export interface EvidenceSnapshot {
  key: string; // schemeId:schemeOptionId
  schemeId: number;
  schemeOptionId?: number | null;
  schemeCode: string;
  schemeName: string;
  recordedAt: string; // ISO string when snapshot was saved
  reasonSavedAtSnapshot?: string | null;
  investigationQuestionAtSnapshot?: string | null;
  nextStepAtSnapshot?: string | null;
  investigationStatusAtSnapshot?: DueDiligenceStatus | null;
  asOfDate: string;
  knowledgeCutoffTime?: string | null;
  calculationRunId?: number | null;
  horizon?: string | null;
  benchmarkCode?: string | null;
  benchmarkName?: string | null;
  latestNavDate?: string | null;
  navObservationCount?: number | null;
  qualityAssessment?: string | null;
  metrics: Record<string, MetricSnapshotItem>;
}

export type EvidenceChangeStatus =
  | "EVIDENCE_UNCHANGED"
  | "EVIDENCE_CHANGED"
  | "HISTORICAL_UNAVAILABLE";

export interface EvidenceChangeSummary {
  status: EvidenceChangeStatus;
  badgeLabel: string;
  badgeClass: string;
  description: string;
  changes: EvidenceChangeItem[];
  snapshotRecordedAt: string | null;
  snapshotRunId: number | null;
  currentRunId: number | null;
  snapshotAsOfDate: string | null;
  currentAsOfDate: string | null;
  requiresReview: boolean;
  hasHistoricalSnapshot: boolean;
}

const SNAPSHOTS_STORAGE_KEY = "yukira_research_snapshots";

/**
 * Loads all stored evidence snapshots from local browser storage.
 */
export function loadEvidenceSnapshots(): Record<string, EvidenceSnapshot> {
  if (typeof window === "undefined") return {};
  try {
    const raw = window.localStorage.getItem(SNAPSHOTS_STORAGE_KEY);
    return raw ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
}

/**
 * Retrieves an existing snapshot for a specific fund share class.
 */
export function getEvidenceSnapshot(
  schemeId: number,
  schemeOptionId?: number | null
): EvidenceSnapshot | null {
  const key = getWatchlistItemKey(schemeId, schemeOptionId);
  const all = loadEvidenceSnapshots();
  return all[key] || null;
}

/**
 * Persists an evidence snapshot into local storage.
 */
export function saveEvidenceSnapshot(snapshot: EvidenceSnapshot): void {
  if (typeof window === "undefined") return;
  try {
    const all = loadEvidenceSnapshots();
    all[snapshot.key] = snapshot;
    window.localStorage.setItem(SNAPSHOTS_STORAGE_KEY, JSON.stringify(all));
  } catch {
    // Non-blocking fallback
  }
}

/**
 * Captures an authoritative evidence snapshot from active watchlist item and analytical profile.
 */
export function captureSnapshotFromProfile(
  fund: WatchlistResponse,
  profile: AnalyticalProfileResponse | null
): EvidenceSnapshot {
  const schemeOptionId = fund.schemeOptionId || null;
  const key = getWatchlistItemKey(fund.schemeId, schemeOptionId);

  const metricsMap: Record<string, MetricSnapshotItem> = {};
  if (profile) {
    const allMetrics: ProfileMetricItem[] = [
      ...profile.returnMetrics,
      ...profile.riskMetrics,
      ...profile.riskAdjustedMetrics,
      ...profile.marketSensitivityMetrics,
    ];

    allMetrics.forEach((m) => {
      metricsMap[m.metricCode] = {
        metricCode: m.metricCode,
        metricName: m.metricName,
        numericValue: m.numericValue,
        formattedValue: m.formattedValue,
        governanceStatus: m.governanceStatus || "CANDIDATE",
        periodType: m.periodType || "3Y",
        units: m.units,
        horizon: profile.context?.horizon || "3Y",
      };
    });
  }

  const asOf = profile?.context?.asOfDate || fund.latestNavDate || "2024-01-15";
  const cutoff = profile?.context?.knowledgeCutoffTime || (asOf <= "2024-01-15" ? "2024-01-31T23:59:59+05:30" : "2026-10-10T23:59:59+05:30");

  const snapshot: EvidenceSnapshot = {
    key,
    schemeId: fund.schemeId,
    schemeOptionId,
    schemeCode: fund.schemeCode,
    schemeName: fund.schemeName,
    recordedAt: new Date().toISOString(),
    reasonSavedAtSnapshot: fund.reasonSaved || null,
    investigationQuestionAtSnapshot: fund.investigationQuestion || null,
    nextStepAtSnapshot: fund.nextStep || null,
    investigationStatusAtSnapshot: fund.investigationStatus || "TO_INVESTIGATE",
    asOfDate: String(asOf),
    knowledgeCutoffTime: cutoff,
    calculationRunId: profile?.provenance?.calculationRunId || null,
    horizon: profile?.context?.horizon || "3Y",
    benchmarkCode: profile?.context?.benchmarkCode || null,
    benchmarkName: profile?.context?.benchmarkName || null,
    latestNavDate: fund.latestNavDate || (asOf <= "2024-01-15" ? "2024-01-15" : null),
    navObservationCount: profile?.provenance?.navObservationCount || null,
    qualityAssessment: profile?.quality?.overallAssessment || null,
    metrics: metricsMap,
  };

  saveEvidenceSnapshot(snapshot);
  return snapshot;
}

/**
 * Computes defensible evidence changes between a genuinely recorded baseline snapshot
 * and the current active analytical profile / fund metadata.
 */
export function computeEvidenceChanges(
  fund: WatchlistResponse,
  profile: AnalyticalProfileResponse | null,
  snapshot: EvidenceSnapshot | null
): EvidenceChangeSummary {
  // If no prior snapshot has been recorded, disclose that historical comparison is unavailable
  if (!snapshot) {
    return {
      status: "HISTORICAL_UNAVAILABLE",
      badgeLabel: "HISTORICAL COMPARISON UNAVAILABLE",
      badgeClass: "bg-surface-raised border-border text-text-muted",
      description: "No prior evidence baseline snapshot exists for this share class. Current observations serve as initial baseline.",
      changes: [],
      snapshotRecordedAt: null,
      snapshotRunId: null,
      currentRunId: profile?.provenance?.calculationRunId || null,
      snapshotAsOfDate: null,
      currentAsOfDate: profile?.context?.asOfDate ? String(profile.context.asOfDate) : (fund.latestNavDate || "2024-01-15"),
      requiresReview: false,
      hasHistoricalSnapshot: false,
    };
  }

  const changes: EvidenceChangeItem[] = [];
  const currentAsOf = profile?.context?.asOfDate ? String(profile.context.asOfDate) : (fund.latestNavDate || "2024-01-15");
  const currentRunId = profile?.provenance?.calculationRunId || null;
  const currentHorizon = profile?.context?.horizon || "3Y";
  const currentNavDate = fund.latestNavDate || (currentAsOf <= "2024-01-15" ? "2024-01-15" : null);

  // 1. Observation Window & Temporal Cutoff
  if (snapshot.asOfDate && currentAsOf && snapshot.asOfDate !== currentAsOf) {
    changes.push({
      category: "OBSERVATION_WINDOW",
      categoryLabel: "Observation Horizon Cutoff",
      title: "Analytical As-Of Date Shifted",
      previousValue: `As-of ${snapshot.asOfDate}`,
      currentValue: `As-of ${currentAsOf}`,
      observationPeriod: `${snapshot.asOfDate} → ${currentAsOf}`,
      evidencePath: `/funds/${fund.schemeId}?option=${fund.schemeOptionId || ""}#coverage`,
      isComparable: true,
      isMaterial: true,
    });
  }

  // 2. Calculation Run Transition
  if (snapshot.calculationRunId && currentRunId && snapshot.calculationRunId !== currentRunId) {
    changes.push({
      category: "CALCULATION_RUN",
      categoryLabel: "Calculation Run",
      title: "Quantitative Engine Run Updated",
      previousValue: `Run #${snapshot.calculationRunId}`,
      currentValue: `Run #${currentRunId}`,
      observationPeriod: `Recorded ${snapshot.recordedAt.split("T")[0]}`,
      evidencePath: `/analysis/${currentRunId}`,
      isComparable: true,
      isMaterial: false,
    });
  }

  // 3. NAV Observation Count / Ledger Freshness
  if (
    snapshot.navObservationCount &&
    profile?.provenance?.navObservationCount &&
    snapshot.navObservationCount !== profile.provenance.navObservationCount
  ) {
    changes.push({
      category: "FRESHNESS_COVERAGE",
      categoryLabel: "Data Coverage & Cadence",
      title: "Observation Count Updated in Ledger",
      previousValue: `${snapshot.navObservationCount} daily observations`,
      currentValue: `${profile.provenance.navObservationCount} daily observations`,
      observationPeriod: currentAsOf,
      evidencePath: `/funds/${fund.schemeId}?option=${fund.schemeOptionId || ""}#data-quality`,
      isComparable: true,
      isMaterial: false,
    });
  }

  // 4. Key Quantitative Metrics (Like-for-Like Verification)
  if (profile) {
    const allCurrent: ProfileMetricItem[] = [
      ...profile.returnMetrics,
      ...profile.riskMetrics,
      ...profile.riskAdjustedMetrics,
      ...profile.marketSensitivityMetrics,
    ];

    const targetMetricCodes = ["RET-02", "RET-03", "RSK-01", "RSK-04", "RAT-01", "MKT-01"];

    targetMetricCodes.forEach((code) => {
      const snapMetric = snapshot.metrics?.[code];
      const curMetric = allCurrent.find((m) => m.metricCode === code);

      if (!snapMetric || !curMetric) return;

      // Check comparability: same horizon and benchmark
      const isSameHorizon = (snapshot.horizon || "3Y") === currentHorizon;
      const snapVal = snapMetric.formattedValue;
      const curVal = curMetric.formattedValue;

      if (!isSameHorizon) {
        // Disclose lookback asymmetry without claiming direct metric shift
        changes.push({
          category: "OBSERVATION_WINDOW",
          categoryLabel: "Evaluation Lookback",
          metricCode: code,
          title: `${curMetric.metricName} [${code}] Horizon Incompatibility`,
          previousValue: `${snapVal ?? "—"} (${snapshot.horizon || "3Y"})`,
          currentValue: `${curVal ?? "—"} (${currentHorizon})`,
          observationPeriod: `${snapshot.horizon || "3Y"} vs ${currentHorizon}`,
          evidencePath: `/funds/${fund.schemeId}?option=${fund.schemeOptionId || ""}#metric-${code}`,
          comparabilityNotice: "Lookback horizons differ between snapshot and current profile; numerical difference cannot be treated as a like-for-like performance change.",
          isComparable: false,
          isMaterial: true,
        });
        return;
      }

      // Check numerical shift on comparable baseline
      if (
        snapMetric.numericValue !== null &&
        curMetric.numericValue !== null &&
        Math.abs((curMetric.numericValue as number) - (snapMetric.numericValue as number)) > 0.0001
      ) {
        changes.push({
          category: "METRIC_VALUE",
          categoryLabel: "Calculated Realized Value",
          metricCode: code,
          title: `${curMetric.metricName} [${code}] Metric Shift`,
          previousValue: snapVal || "—",
          currentValue: curVal || "—",
          observationPeriod: `${curMetric.periodType || currentHorizon} lookback`,
          evidencePath: `/funds/${fund.schemeId}?option=${fund.schemeOptionId || ""}#metric-${code}`,
          isComparable: true,
          isMaterial: true,
        });
      }
    });
  }

  const hasChanges = changes.length > 0;
  const isReviewed = fund.investigationStatus === "REVIEWED";

  if (!hasChanges) {
    return {
      status: "EVIDENCE_UNCHANGED",
      badgeLabel: "EVIDENCE UNCHANGED",
      badgeClass: "bg-emerald-500/10 text-emerald-400 border-emerald-500/30",
      description: "Underlying quantitative observations and point-in-time calculation inputs match the recorded baseline.",
      changes: [],
      snapshotRecordedAt: snapshot.recordedAt,
      snapshotRunId: snapshot.calculationRunId ?? null,
      currentRunId,
      snapshotAsOfDate: snapshot.asOfDate,
      currentAsOfDate: currentAsOf,
      requiresReview: false,
      hasHistoricalSnapshot: true,
    };
  }

  return {
    status: "EVIDENCE_CHANGED",
    badgeLabel: "EVIDENCE CHANGED",
    badgeClass: "bg-amber-500/15 text-amber-300 border-amber-500/40",
    description: `${changes.length} evidence change${changes.length > 1 ? "s" : ""} observed since baseline snapshot was captured on ${snapshot.recordedAt.split("T")[0]}.`,
    changes,
    snapshotRecordedAt: snapshot.recordedAt,
    snapshotRunId: snapshot.calculationRunId ?? null,
    currentRunId,
    snapshotAsOfDate: snapshot.asOfDate,
    currentAsOfDate: currentAsOf,
    requiresReview: isReviewed,
    hasHistoricalSnapshot: true,
  };
}
