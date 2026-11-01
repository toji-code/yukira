/**
 * YUKIRA Quantitative Intelligence Platform
 * Phase F9: Unified Research Search, Filtering, and Resumption
 *
 * Implements client-side unified search across all stored research attributes,
 * investor-authored hypotheses, due-diligence questions, thesis records,
 * and deterministic attention signals with explainable matched field attribution.
 *
 * Epistemic Guardrail: Search relevance and research completeness NEVER imply
 * investment quality, returns, or suitability. Searches operate strictly on
 * verifiable stored strings and point-in-time metadata.
 */

import { WatchlistResponse } from "@/lib/api/investor";
import { ResolvedAttentionItem } from "@/lib/utils/researchAttention";
import { EvidenceChangeSummary } from "@/lib/utils/evidenceChange";
import {
  ResearchFollowUpIssue,
  FOLLOW_UP_CATEGORIES_CONFIG,
  FOLLOW_UP_STATUS_CONFIG,
  FOLLOW_UP_OUTCOMES_CONFIG,
} from "@/lib/utils/researchFollowUp";
import { getBriefSectionTitle } from "@/lib/utils/researchSession";

export interface MatchedFieldInfo {
  fieldId: string;
  label: string;
  snippet?: string;
}

export interface ResearchSearchResult {
  item: WatchlistResponse;
  isMatch: boolean;
  matchedFields: MatchedFieldInfo[];
  primaryMatchLabel: string | null;
}

export interface FollowUpSearchResult {
  issue: ResearchFollowUpIssue;
  isMatch: boolean;
  matchedFields: MatchedFieldInfo[];
  primaryMatchLabel: string | null;
}

/**
 * Extracts a concise snippet centered around the query term with surrounding context.
 */
function extractSnippet(text: string, query: string, maxLength = 80): string {
  const lowerText = text.toLowerCase();
  const lowerQuery = query.toLowerCase();
  const index = lowerText.indexOf(lowerQuery);
  if (index === -1) return text.slice(0, maxLength);

  const start = Math.max(0, index - 20);
  const end = Math.min(text.length, index + query.length + 40);
  const prefix = start > 0 ? "…" : "";
  const suffix = end < text.length ? "…" : "";

  return `${prefix}${text.slice(start, end).trim()}${suffix}`;
}

/**
 * Evaluates a single saved research item against a search query across all 15+ stored fields
 * plus any associated follow-up diligence inquiries.
 * Returns match status, list of matched fields, and an explainable primary match summary.
 */
export function evaluateResearchItemSearch(
  item: WatchlistResponse,
  query: string,
  resolvedAttention?: ResolvedAttentionItem | null,
  evidenceSummary?: EvidenceChangeSummary | null,
  itemFollowUpIssues?: ResearchFollowUpIssue[]
): ResearchSearchResult {
  const trimmed = query.trim().toLowerCase();
  if (!trimmed) {
    return {
      item,
      isMatch: true,
      matchedFields: [],
      primaryMatchLabel: null,
    };
  }

  const matches: MatchedFieldInfo[] = [];

  // 1. Fund & Scheme Names
  if (item.schemeName && item.schemeName.toLowerCase().includes(trimmed)) {
    matches.push({
      fieldId: "schemeName",
      label: "Fund Name",
      snippet: extractSnippet(item.schemeName, trimmed),
    });
  }

  if (item.schemeCode && item.schemeCode.toLowerCase().includes(trimmed)) {
    matches.push({
      fieldId: "schemeCode",
      label: "Fund Code",
      snippet: item.schemeCode,
    });
  }

  // 2. Exact Option Identifiers
  if (item.amfiCode && item.amfiCode.toLowerCase().includes(trimmed)) {
    matches.push({
      fieldId: "amfiCode",
      label: "AMFI Code",
      snippet: item.amfiCode,
    });
  }

  if (item.isin && item.isin.toLowerCase().includes(trimmed)) {
    matches.push({
      fieldId: "isin",
      label: "ISIN",
      snippet: item.isin,
    });
  }

  if (item.schemeOptionId && String(item.schemeOptionId).includes(trimmed)) {
    matches.push({
      fieldId: "schemeOptionId",
      label: "Option ID",
      snippet: `#${item.schemeOptionId}`,
    });
  }

  const planOptionStr = `${item.planType || ""} ${item.optionType || ""}`.trim().toLowerCase();
  if (planOptionStr && planOptionStr.includes(trimmed)) {
    matches.push({
      fieldId: "planOption",
      label: "Plan & Option",
      snippet: `${item.planType || "DIRECT"} • ${item.optionType || "GROWTH"}`,
    });
  }

  // 3. Category & AMC
  if (item.category && item.category.toLowerCase().includes(trimmed)) {
    matches.push({
      fieldId: "category",
      label: "Category",
      snippet: item.category.replace(/_/g, " "),
    });
  }

  if (item.amcName && item.amcName.toLowerCase().includes(trimmed)) {
    matches.push({
      fieldId: "amcName",
      label: "AMC Name",
      snippet: item.amcName,
    });
  }

  // 4. Investor Research Objective
  if (item.reasonSaved && item.reasonSaved.toLowerCase().includes(trimmed)) {
    matches.push({
      fieldId: "reasonSaved",
      label: "Research Objective",
      snippet: extractSnippet(item.reasonSaved, trimmed),
    });
  }

  // 5. Investigation Question / Diligence Hypothesis
  if (item.investigationQuestion && item.investigationQuestion.toLowerCase().includes(trimmed)) {
    matches.push({
      fieldId: "investigationQuestion",
      label: "Investigation Question",
      snippet: extractSnippet(item.investigationQuestion, trimmed),
    });
  }

  // 6. Working Thesis (Phase F7)
  if (item.workingThesis && item.workingThesis.toLowerCase().includes(trimmed)) {
    matches.push({
      fieldId: "workingThesis",
      label: "Working Thesis",
      snippet: extractSnippet(item.workingThesis, trimmed),
    });
  }

  // 7. Supporting Evidence Notes (Phase F7)
  if (item.supportingEvidence && item.supportingEvidence.toLowerCase().includes(trimmed)) {
    matches.push({
      fieldId: "supportingEvidence",
      label: "Supporting Evidence Notes",
      snippet: extractSnippet(item.supportingEvidence, trimmed),
    });
  }

  // 8. Reconsideration Conditions (Phase F7)
  if (item.reconsiderationConditions && item.reconsiderationConditions.toLowerCase().includes(trimmed)) {
    matches.push({
      fieldId: "reconsiderationConditions",
      label: "Reconsideration Conditions",
      snippet: extractSnippet(item.reconsiderationConditions, trimmed),
    });
  }

  // 9. Investor-Specified Next Step
  if (item.nextStep && item.nextStep.toLowerCase().includes(trimmed)) {
    matches.push({
      fieldId: "nextStep",
      label: "Next Diligence Step",
      snippet: extractSnippet(item.nextStep, trimmed),
    });
  }

  // 10. Due-Diligence Status
  const statusStr = (item.investigationStatus || "TO_INVESTIGATE").toLowerCase().replace(/_/g, " ");
  if (statusStr.includes(trimmed)) {
    matches.push({
      fieldId: "investigationStatus",
      label: "Due-Diligence Status",
      snippet: statusStr.toUpperCase(),
    });
  }

  // 11. Attention Reasons & Signals (Phase F8)
  if (resolvedAttention) {
    const primaryText = `${resolvedAttention.primaryReason.label} ${resolvedAttention.primaryReason.description}`.toLowerCase();
    if (primaryText.includes(trimmed)) {
      matches.push({
        fieldId: "attentionReason",
        label: "Attention Reason",
        snippet: resolvedAttention.primaryReason.label,
      });
    }
  }

  // 12. Evidence Change Details (Phase F5)
  if (evidenceSummary) {
    const evText = `${evidenceSummary.badgeLabel} ${evidenceSummary.description}`.toLowerCase();
    if (evText.includes(trimmed)) {
      matches.push({
        fieldId: "evidenceChange",
        label: "Evidence Status",
        snippet: evidenceSummary.badgeLabel,
      });
    }
  }

  // 13. Associated Follow-Up Issues & Diligence Inquiries (Phase F20)
  if (itemFollowUpIssues && itemFollowUpIssues.length > 0) {
    const matchedFollowUps = itemFollowUpIssues
      .map((iss) => evaluateFollowUpIssueSearch(iss, query))
      .filter((res) => res.isMatch && res.matchedFields.length > 0);
    if (matchedFollowUps.length > 0) {
      const topIssueMatch = matchedFollowUps[0];
      matches.push({
        fieldId: "followUpIssue",
        label: `Follow-Up Record`,
        snippet: `${topIssueMatch.issue.title}${topIssueMatch.primaryMatchLabel ? ` (${topIssueMatch.primaryMatchLabel})` : ""}`,
      });
    }
  }

  const isMatch = matches.length > 0;

  // Determine primary match label to explain why the item appeared
  let primaryMatchLabel: string | null = null;
  if (isMatch) {
    // Priority order for explainability: Thesis/Question > Objective/NextStep > Fund Identifiers > Status
    const priorityOrder = [
      "workingThesis",
      "investigationQuestion",
      "reconsiderationConditions",
      "supportingEvidence",
      "followUpIssue",
      "nextStep",
      "reasonSaved",
      "schemeName",
      "schemeCode",
      "amfiCode",
      "isin",
      "attentionReason",
      "evidenceChange",
    ];

    const sortedMatches = [...matches].sort((a, b) => {
      const idxA = priorityOrder.indexOf(a.fieldId);
      const idxB = priorityOrder.indexOf(b.fieldId);
      const valA = idxA === -1 ? 999 : idxA;
      const valB = idxB === -1 ? 999 : idxB;
      return valA - valB;
    });

    const top = sortedMatches[0];
    if (top.snippet && top.fieldId !== "schemeName") {
      primaryMatchLabel = `${top.label}: "${top.snippet}"`;
    } else {
      primaryMatchLabel = top.label;
    }
  }

  return {
    item,
    isMatch,
    matchedFields: matches,
    primaryMatchLabel,
  };
}

/**
 * Phase F20: Evaluates a single Research Follow-Up issue against a search query across all
 * genuinely persisted fields (title, questions, actions, outcome notes, residual uncertainty,
 * run IDs, cutoffs, prior outcomes, and exact instrument identifiers).
 * Returns match status, list of matched fields, and an explainable primary match snippet.
 */
export function evaluateFollowUpIssueSearch(
  issue: ResearchFollowUpIssue,
  query: string
): FollowUpSearchResult {
  const trimmed = query.trim().toLowerCase();
  if (!trimmed) {
    return {
      issue,
      isMatch: true,
      matchedFields: [],
      primaryMatchLabel: null,
    };
  }

  const matches: MatchedFieldInfo[] = [];

  // 1. Issue Title
  if (issue.title && issue.title.toLowerCase().includes(trimmed)) {
    matches.push({
      fieldId: "title",
      label: "Issue Title",
      snippet: extractSnippet(issue.title, trimmed),
    });
  }

  // 2. Diligence Question / Concern
  if (issue.questionOrReason && issue.questionOrReason.toLowerCase().includes(trimmed)) {
    matches.push({
      fieldId: "questionOrReason",
      label: "Diligence Question",
      snippet: extractSnippet(issue.questionOrReason, trimmed),
    });
  }

  // 3. Planned Next Action
  if (issue.nextAction && issue.nextAction.toLowerCase().includes(trimmed)) {
    matches.push({
      fieldId: "nextAction",
      label: "Planned Next Action",
      snippet: extractSnippet(issue.nextAction, trimmed),
    });
  }

  // 4. Resolution Note / Outcome Note
  if (issue.resolutionNote && issue.resolutionNote.toLowerCase().includes(trimmed)) {
    matches.push({
      fieldId: "resolutionNote",
      label: "Outcome Note",
      snippet: extractSnippet(issue.resolutionNote, trimmed),
    });
  }

  // 5. Structured Outcome Type
  if (issue.outcomeType) {
    const outcomeMeta = FOLLOW_UP_OUTCOMES_CONFIG[issue.outcomeType];
    const outcomeStr = `${issue.outcomeType} ${outcomeMeta?.label || ""} ${outcomeMeta?.shortLabel || ""}`.toLowerCase();
    if (outcomeStr.includes(trimmed)) {
      matches.push({
        fieldId: "outcomeType",
        label: "Outcome Classification",
        snippet: outcomeMeta?.label || issue.outcomeType,
      });
    }
  }

  // 6. Residual Uncertainty (Phase F18 & F19)
  if (issue.residualUncertainty && issue.residualUncertainty.toLowerCase().includes(trimmed)) {
    matches.push({
      fieldId: "residualUncertainty",
      label: "Residual Uncertainty",
      snippet: extractSnippet(issue.residualUncertainty, trimmed),
    });
  }

  // 7. Preserved Prior Resolution Note (Phase F18)
  if (issue.priorResolutionNote && issue.priorResolutionNote.toLowerCase().includes(trimmed)) {
    matches.push({
      fieldId: "priorResolutionNote",
      label: "Preserved Prior Outcome",
      snippet: extractSnippet(issue.priorResolutionNote, trimmed),
    });
  }

  // 7b. Renewal Rationale / Reopen Reason (Phase F24)
  if (issue.reopenReason && issue.reopenReason.toLowerCase().includes(trimmed)) {
    matches.push({
      fieldId: "reopenReason",
      label: "Renewal Rationale",
      snippet: extractSnippet(issue.reopenReason, trimmed),
    });
  }

  // 7c. Closure Limitation Rationale (Phase F24)
  if (issue.closureLimitationRationale && issue.closureLimitationRationale.toLowerCase().includes(trimmed)) {
    matches.push({
      fieldId: "closureLimitationRationale",
      label: "Closure Limitation Rationale",
      snippet: extractSnippet(issue.closureLimitationRationale, trimmed),
    });
  }

  // 8. Preserved Prior Outcome Type (Phase F18)
  if (issue.priorOutcomeType) {
    const priorMeta = FOLLOW_UP_OUTCOMES_CONFIG[issue.priorOutcomeType];
    const priorStr = `${issue.priorOutcomeType} ${priorMeta?.label || ""}`.toLowerCase();
    if (priorStr.includes(trimmed)) {
      matches.push({
        fieldId: "priorOutcomeType",
        label: "Prior Outcome Type",
        snippet: priorMeta?.label || issue.priorOutcomeType,
      });
    }
  }

  // 9. Follow-Up Category
  if (issue.category) {
    const catMeta = FOLLOW_UP_CATEGORIES_CONFIG[issue.category];
    const catStr = `${issue.category} ${catMeta?.label || ""} ${catMeta?.shortLabel || ""}`.toLowerCase();
    if (catStr.includes(trimmed)) {
      matches.push({
        fieldId: "category",
        label: "Category",
        snippet: catMeta?.label || issue.category,
      });
    }
  }

  // 10. Lifecycle Status
  if (issue.status) {
    const statusMeta = FOLLOW_UP_STATUS_CONFIG[issue.status];
    const statusStr = `${issue.status} ${statusMeta?.label || ""}`.toLowerCase();
    if (statusStr.includes(trimmed)) {
      matches.push({
        fieldId: "status",
        label: "Lifecycle Status",
        snippet: statusMeta?.label || issue.status,
      });
    }
  }

  // Reopened state keyword check
  if ((issue.priorResolutionNote || issue.reopenedAt) && "reopened".includes(trimmed)) {
    matches.push({
      fieldId: "reopenedState",
      label: "Reopened State",
      snippet: "Reopened Inquiry",
    });
  }

  // 11. Brief Section & Title
  if (issue.briefSection) {
    const sectionTitle = getBriefSectionTitle(issue.briefSection);
    const secStr = `section ${issue.briefSection} sec ${issue.briefSection} ${sectionTitle}`.toLowerCase();
    if (secStr.includes(trimmed)) {
      matches.push({
        fieldId: "briefSection",
        label: "Research Brief Section",
        snippet: `Section ${issue.briefSection}: ${sectionTitle}`,
      });
    }
  }

  // 12. Checklist Dimension
  if (issue.checklistKey && issue.checklistKey.toLowerCase().replace(/_/g, " ").includes(trimmed)) {
    matches.push({
      fieldId: "checklistKey",
      label: "Checklist Dimension",
      snippet: issue.checklistKey.replace(/_/g, " "),
    });
  }

  // 13. Fund Identity
  if (issue.schemeName && issue.schemeName.toLowerCase().includes(trimmed)) {
    matches.push({
      fieldId: "schemeName",
      label: "Fund Name",
      snippet: extractSnippet(issue.schemeName, trimmed),
    });
  }

  if (issue.schemeCode && issue.schemeCode.toLowerCase().includes(trimmed)) {
    matches.push({
      fieldId: "schemeCode",
      label: "Fund Code",
      snippet: issue.schemeCode,
    });
  }

  if (issue.amfiCode && issue.amfiCode.toLowerCase().includes(trimmed)) {
    matches.push({
      fieldId: "amfiCode",
      label: "AMFI Code",
      snippet: issue.amfiCode,
    });
  }

  if (issue.isin && issue.isin.toLowerCase().includes(trimmed)) {
    matches.push({
      fieldId: "isin",
      label: "ISIN",
      snippet: issue.isin,
    });
  }

  if (issue.schemeOptionId && String(issue.schemeOptionId).includes(trimmed)) {
    matches.push({
      fieldId: "schemeOptionId",
      label: "Option ID",
      snippet: `#${issue.schemeOptionId}`,
    });
  }

  const planOptionStr = `${issue.planType || ""} ${issue.optionType || ""}`.trim().toLowerCase();
  if (planOptionStr && planOptionStr.includes(trimmed)) {
    matches.push({
      fieldId: "planOption",
      label: "Plan & Option",
      snippet: `${issue.planType || "DIRECT"} • ${issue.optionType || "GROWTH"}`,
    });
  }

  // 14. Evidence Calculation Run ID & Cutoff Date (Phase F19)
  if (issue.evidenceRunId) {
    const runStr = `run #${issue.evidenceRunId} run ${issue.evidenceRunId} ${issue.evidenceRunId}`.toLowerCase();
    if (runStr.includes(trimmed)) {
      matches.push({
        fieldId: "evidenceRunId",
        label: "Evidence Calculation Run",
        snippet: `Run #${issue.evidenceRunId}`,
      });
    }
  }

  if (issue.evidenceAsOfDate && issue.evidenceAsOfDate.toLowerCase().includes(trimmed)) {
    matches.push({
      fieldId: "evidenceAsOfDate",
      label: "Evidence Cutoff Date",
      snippet: issue.evidenceAsOfDate,
    });
  }

  if (issue.priorEvidenceRunId) {
    const priorRunStr = `prior run #${issue.priorEvidenceRunId} run ${issue.priorEvidenceRunId} ${issue.priorEvidenceRunId}`.toLowerCase();
    if (priorRunStr.includes(trimmed)) {
      matches.push({
        fieldId: "priorEvidenceRunId",
        label: "Prior Evidence Run",
        snippet: `Run #${issue.priorEvidenceRunId}`,
      });
    }
  }

  if (issue.priorEvidenceAsOfDate && issue.priorEvidenceAsOfDate.toLowerCase().includes(trimmed)) {
    matches.push({
      fieldId: "priorEvidenceAsOfDate",
      label: "Prior Evidence Cutoff",
      snippet: issue.priorEvidenceAsOfDate,
    });
  }

  const isMatch = matches.length > 0;

  // Determine primary match label to explain why the follow-up record appeared
  let primaryMatchLabel: string | null = null;
  if (isMatch) {
    const priorityOrder = [
      "residualUncertainty",
      "resolutionNote",
      "closureLimitationRationale",
      "priorResolutionNote",
      "reopenReason",
      "questionOrReason",
      "title",
      "nextAction",
      "outcomeType",
      "evidenceRunId",
      "evidenceAsOfDate",
      "priorEvidenceRunId",
      "priorEvidenceAsOfDate",
      "schemeName",
      "amfiCode",
      "isin",
      "briefSection",
      "category",
      "status",
      "reopenedState",
    ];

    const sortedMatches = [...matches].sort((a, b) => {
      const idxA = priorityOrder.indexOf(a.fieldId);
      const idxB = priorityOrder.indexOf(b.fieldId);
      const valA = idxA === -1 ? 999 : idxA;
      const valB = idxB === -1 ? 999 : idxB;
      return valA - valB;
    });

    const top = sortedMatches[0];
    if (top.snippet && top.fieldId !== "schemeName") {
      primaryMatchLabel = `${top.label}: "${top.snippet}"`;
    } else {
      primaryMatchLabel = top.label;
    }
  }

  return {
    issue,
    isMatch,
    matchedFields: matches,
    primaryMatchLabel,
  };
}
