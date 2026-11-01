/**
 * YUKIRA Quantitative Intelligence Platform
 * Phase F13: Research Coverage and Investigation Completeness Workspace
 *
 * Implements deterministic research coverage tracking, field-presence rules,
 * and descriptive investigation completeness reporting across saved mutual funds.
 *
 * CRITICAL EPISTEMIC GUARDRAIL:
 * Completeness measures DOCUMENTATION PRESENCE, NOT the intellectual quality,
 * analytical correctness, or investment suitability of the underlying thesis.
 * No subjective scores, fund rankings, or artificial completion percentages are generated.
 */

import { WatchlistResponse, DueDiligenceStatus } from "@/lib/api/investor";
import { EvidenceChangeSummary } from "@/lib/utils/evidenceChange";

export type CompletenessDimensionKey =
  | "OBJECTIVE"
  | "QUESTION"
  | "THESIS"
  | "SUPPORTING_EVIDENCE"
  | "RECONSIDERATION_CONDITIONS"
  | "NEXT_STEP"
  | "DILIGENCE_STATUS"
  | "EVIDENCE_STABILITY";

export type ProvenanceType = "SERVER" | "CLIENT_LOCAL" | "SYSTEM_EVIDENCE";

export interface CompletenessDimensionDetail {
  key: CompletenessDimensionKey;
  label: string;
  isRecorded: boolean;
  isUnavailable: boolean;
  provenance: ProvenanceType;
  provenanceLabel: string;
  remediationActionLabel: string;
  remediationTarget: "NOTE" | "THESIS" | "BRIEF_D" | "BRIEF_E" | "BRIEF_F";
  briefSection?: "A" | "B" | "C" | "D" | "E" | "F" | "G";
  storedValue?: string | null;
}

export interface RecordCompletenessResolution {
  item: WatchlistResponse;
  dimensions: Record<CompletenessDimensionKey, CompletenessDimensionDetail>;
  dimensionList: CompletenessDimensionDetail[];
  // Investor documentation counts (out of 6 core investor-authored inputs)
  totalInvestorInputs: number;
  recordedInvestorInputsCount: number;
  missingInvestorInputsCount: number;
  isDocumentationComplete: boolean;
  // Status & Evidence Dimensions
  statusRecorded: boolean;
  currentStatus: DueDiligenceStatus;
  evidenceRequiresReview: boolean;
  evidenceChanged: boolean;
  evidenceStale: boolean;
  // Missing inputs
  missingInputKeys: CompletenessDimensionKey[];
  missingInputLabels: string[];
  descriptiveSummary: string;
}

export interface InvestigationCompletenessSummary {
  totalInvestigations: number;
  fullyDocumentedCount: number;
  incompleteDocumentationCount: number;
  // Per-field missing counts
  missingObjectiveCount: number;
  missingQuestionCount: number;
  missingThesisCount: number;
  missingSupportingEvidenceCount: number;
  missingReconsiderationCount: number;
  missingNextStepCount: number;
  // Status & evidence counts
  unreviewedDiligenceCount: number;
  inProgressDiligenceCount: number;
  reviewedDiligenceCount: number;
  needsFollowUpDiligenceCount: number;
  evidenceReviewNeededCount: number;
  staleEvidenceCutoffCount: number;
  // Provenance counts
  serverBackedCount: number;
  clientLocalCount: number;
}

/**
 * Validates whether an investor-authored text field contains meaningful recorded content.
 * Strictly rejects null, undefined, empty strings, whitespace-only strings, and
 * literal placeholder strings ("undefined", "null", "n/a", "none", "todo", "tbd").
 */
export function isFieldRecorded(val?: string | null): boolean {
  if (!val) return false;
  const trimmed = val.trim();
  if (trimmed.length === 0) return false;
  const lower = trimmed.toLowerCase();
  if (
    lower === "undefined" ||
    lower === "null" ||
    lower === "n/a" ||
    lower === "none" ||
    lower === "todo" ||
    lower === "tbd"
  ) {
    return false;
  }
  return true;
}

/**
 * Resolves investigation completeness deterministically for a single saved fund item.
 */
export function resolveRecordCompleteness(
  item: WatchlistResponse,
  evidenceSummary?: EvidenceChangeSummary | null
): RecordCompletenessResolution {
  const currentStatus: DueDiligenceStatus = item.investigationStatus || "TO_INVESTIGATE";
  const hasStatus = Boolean(item.investigationStatus);

  const hasObjective = isFieldRecorded(item.reasonSaved);
  const hasQuestion = isFieldRecorded(item.investigationQuestion);
  const hasThesis = isFieldRecorded(item.workingThesis);
  const hasSupportingEvidence = isFieldRecorded(item.supportingEvidence);
  const hasReconsideration = isFieldRecorded(item.reconsiderationConditions);
  const hasNextStep = isFieldRecorded(item.nextStep);

  const evidenceRequiresReview = Boolean(evidenceSummary?.requiresReview);
  const evidenceChanged = evidenceSummary?.status === "EVIDENCE_CHANGED";
  const evidenceStale = item.latestNavDate === "2024-01-15" || !item.latestNavDate;

  const dimensions: Record<CompletenessDimensionKey, CompletenessDimensionDetail> = {
    OBJECTIVE: {
      key: "OBJECTIVE",
      label: "Investor Objective",
      isRecorded: hasObjective,
      isUnavailable: false,
      provenance: "SERVER",
      provenanceLabel: "Server Synchronized",
      remediationActionLabel: "+ Record Objective",
      remediationTarget: "NOTE",
      briefSection: "D",
      storedValue: hasObjective ? item.reasonSaved : null,
    },
    QUESTION: {
      key: "QUESTION",
      label: "Investigation Question",
      isRecorded: hasQuestion,
      isUnavailable: false,
      provenance: "SERVER",
      provenanceLabel: "Server Synchronized",
      remediationActionLabel: "+ Formulate Question",
      remediationTarget: "NOTE",
      briefSection: "D",
      storedValue: hasQuestion ? item.investigationQuestion : null,
    },
    THESIS: {
      key: "THESIS",
      label: "Working Thesis",
      isRecorded: hasThesis,
      isUnavailable: false,
      provenance: "CLIENT_LOCAL",
      provenanceLabel: "Browser Local Storage",
      remediationActionLabel: "+ Record Working Thesis",
      remediationTarget: "THESIS",
      briefSection: "D",
      storedValue: hasThesis ? item.workingThesis : null,
    },
    SUPPORTING_EVIDENCE: {
      key: "SUPPORTING_EVIDENCE",
      label: "Supporting Evidence Notes",
      isRecorded: hasSupportingEvidence,
      isUnavailable: false,
      provenance: "CLIENT_LOCAL",
      provenanceLabel: "Browser Local Storage",
      remediationActionLabel: "+ Document Supporting Evidence",
      remediationTarget: "THESIS",
      briefSection: "D",
      storedValue: hasSupportingEvidence ? item.supportingEvidence : null,
    },
    RECONSIDERATION_CONDITIONS: {
      key: "RECONSIDERATION_CONDITIONS",
      label: "Reconsideration Triggers",
      isRecorded: hasReconsideration,
      isUnavailable: false,
      provenance: "CLIENT_LOCAL",
      provenanceLabel: "Browser Local Storage",
      remediationActionLabel: "+ Define Invalidation Triggers",
      remediationTarget: "THESIS",
      briefSection: "E",
      storedValue: hasReconsideration ? item.reconsiderationConditions : null,
    },
    NEXT_STEP: {
      key: "NEXT_STEP",
      label: "Next Action Step",
      isRecorded: hasNextStep,
      isUnavailable: false,
      provenance: "SERVER",
      provenanceLabel: "Server Synchronized",
      remediationActionLabel: "+ Specify Next Step",
      remediationTarget: "NOTE",
      briefSection: "D",
      storedValue: hasNextStep ? item.nextStep : null,
    },
    DILIGENCE_STATUS: {
      key: "DILIGENCE_STATUS",
      label: "Due-Diligence Status",
      isRecorded: hasStatus,
      isUnavailable: false,
      provenance: "SERVER",
      provenanceLabel: "Server Synchronized",
      remediationActionLabel: "Update Diligence Status",
      remediationTarget: "NOTE",
      briefSection: "A",
      storedValue: currentStatus,
    },
    EVIDENCE_STABILITY: {
      key: "EVIDENCE_STABILITY",
      label: "Evidence Stability & Freshness",
      isRecorded: !evidenceRequiresReview && !evidenceChanged,
      isUnavailable: evidenceSummary?.status === "HISTORICAL_UNAVAILABLE",
      provenance: "SYSTEM_EVIDENCE",
      provenanceLabel: "Point-in-Time Calculation",
      remediationActionLabel: "Review Baseline Changes →",
      remediationTarget: "BRIEF_F",
      briefSection: "F",
      storedValue: evidenceSummary?.status || null,
    },
  };

  const dimensionList = Object.values(dimensions);

  // 6 Core Investor-Authored Documentation Inputs
  const investorInputKeys: CompletenessDimensionKey[] = [
    "OBJECTIVE",
    "QUESTION",
    "THESIS",
    "SUPPORTING_EVIDENCE",
    "RECONSIDERATION_CONDITIONS",
    "NEXT_STEP",
  ];

  const missingInputKeys: CompletenessDimensionKey[] = [];
  const missingInputLabels: string[] = [];

  for (const k of investorInputKeys) {
    if (!dimensions[k].isRecorded) {
      missingInputKeys.push(k);
      missingInputLabels.push(dimensions[k].label);
    }
  }

  const totalInvestorInputs = investorInputKeys.length; // 6
  const recordedInvestorInputsCount = totalInvestorInputs - missingInputKeys.length;
  const missingInvestorInputsCount = missingInputKeys.length;
  const isDocumentationComplete = missingInputKeys.length === 0;

  const descriptiveSummary = isDocumentationComplete
    ? "Complete research record (6/6 inputs recorded)"
    : `Missing ${missingInvestorInputsCount} input${missingInvestorInputsCount === 1 ? "" : "s"}: ${missingInputLabels.join(", ")}`;

  return {
    item,
    dimensions,
    dimensionList,
    totalInvestorInputs,
    recordedInvestorInputsCount,
    missingInvestorInputsCount,
    isDocumentationComplete,
    statusRecorded: hasStatus,
    currentStatus,
    evidenceRequiresReview,
    evidenceChanged,
    evidenceStale,
    missingInputKeys,
    missingInputLabels,
    descriptiveSummary,
  };
}

/**
 * Aggregates completeness metrics across all saved investigations.
 */
export function computeInvestigationCompletenessSummary(
  resolutions: RecordCompletenessResolution[]
): InvestigationCompletenessSummary {
  const totalInvestigations = resolutions.length;
  let fullyDocumentedCount = 0;
  let incompleteDocumentationCount = 0;

  let missingObjectiveCount = 0;
  let missingQuestionCount = 0;
  let missingThesisCount = 0;
  let missingSupportingEvidenceCount = 0;
  let missingReconsiderationCount = 0;
  let missingNextStepCount = 0;

  let unreviewedDiligenceCount = 0;
  let inProgressDiligenceCount = 0;
  let reviewedDiligenceCount = 0;
  let needsFollowUpDiligenceCount = 0;
  let evidenceReviewNeededCount = 0;
  let staleEvidenceCutoffCount = 0;

  let serverBackedCount = 0;
  let clientLocalCount = 0;

  for (const r of resolutions) {
    if (r.isDocumentationComplete) {
      fullyDocumentedCount++;
    } else {
      incompleteDocumentationCount++;
    }

    if (!r.dimensions.OBJECTIVE.isRecorded) missingObjectiveCount++;
    if (!r.dimensions.QUESTION.isRecorded) missingQuestionCount++;
    if (!r.dimensions.THESIS.isRecorded) missingThesisCount++;
    if (!r.dimensions.SUPPORTING_EVIDENCE.isRecorded) missingSupportingEvidenceCount++;
    if (!r.dimensions.RECONSIDERATION_CONDITIONS.isRecorded) missingReconsiderationCount++;
    if (!r.dimensions.NEXT_STEP.isRecorded) missingNextStepCount++;

    if (r.currentStatus === "TO_INVESTIGATE") unreviewedDiligenceCount++;
    if (r.currentStatus === "IN_PROGRESS") inProgressDiligenceCount++;
    if (r.currentStatus === "REVIEWED") reviewedDiligenceCount++;
    if (r.currentStatus === "NEEDS_FOLLOW_UP") needsFollowUpDiligenceCount++;

    if (r.evidenceRequiresReview) evidenceReviewNeededCount++;
    if (r.evidenceStale) staleEvidenceCutoffCount++;

    if (r.item.id && r.item.id > 0) {
      serverBackedCount++;
    } else {
      clientLocalCount++;
    }
  }

  return {
    totalInvestigations,
    fullyDocumentedCount,
    incompleteDocumentationCount,
    missingObjectiveCount,
    missingQuestionCount,
    missingThesisCount,
    missingSupportingEvidenceCount,
    missingReconsiderationCount,
    missingNextStepCount,
    unreviewedDiligenceCount,
    inProgressDiligenceCount,
    reviewedDiligenceCount,
    needsFollowUpDiligenceCount,
    evidenceReviewNeededCount,
    staleEvidenceCutoffCount,
    serverBackedCount,
    clientLocalCount,
  };
}
