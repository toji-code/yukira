/**
 * YUKIRA Quantitative Intelligence Platform
 * Phase F21 & F22: Research Dossier Assembly, Version Context, and Investigation Continuity
 *
 * Implements deterministic assembly, version comparison, and client-side portable export
 * (Markdown and self-contained HTML) of investor-authored research records, follow-up findings,
 * residual uncertainties, evidentiary provenance, and actionable continuity roadmaps.
 *
 * CRITICAL EPISTEMIC GUARDRAILS:
 * 1. The dossier is an EVIDENCE-PRESERVING RESEARCH ARTIFACT, not an investment recommendation or rating.
 * 2. Findings are investor-authored due diligence notes tied to exact share classes and point-in-time calculation runs.
 * 3. Exact (schemeId, schemeOptionId) identity is strictly preserved without cross-variant conflation.
 * 4. Missing or incomplete provenance is explicitly labeled as "Not Recorded", never synthesized.
 * 5. All user-authored text is treated as untrusted and safely escaped in HTML export to prevent script injection.
 * 6. Historical observations and calculation cutoffs (e.g. 2024-01-15) are strictly distinguished from the
 *    export generation timestamp and any active workspace cutoffs.
 * 7. Continuity states are grounded strictly in recorded lifecycle states, next actions, and target dates.
 */

import {
  ResearchFollowUpIssue,
  FOLLOW_UP_CATEGORIES_CONFIG,
  FOLLOW_UP_STATUS_CONFIG,
  FOLLOW_UP_OUTCOMES_CONFIG,
  FindingProvenanceSummary,
  evaluateFindingEvidenceTraceability,
  isTargetDatePassed,
  evaluateInvestigationClosureState,
  InvestigationClosureResolution,
  InvestigationDependency,
  IssueDependencyEvaluation,
  evaluateIssueDependencies,
  DEPENDENCY_CATEGORY_CONFIG,
  DEPENDENCY_STATUS_CONFIG,
} from "@/lib/utils/researchFollowUp";
import { WatchlistResponse } from "@/lib/api/investor";
import { getBriefSectionTitle } from "@/lib/utils/researchSession";
import { getEvidenceSnapshot } from "@/lib/utils/evidenceChange";

export interface ResearchDossierItem {
  issue: ResearchFollowUpIssue;
  schemeId: number;
  schemeOptionId: number | null | undefined;
  schemeName: string;
  schemeCode: string;
  planType: string;
  optionType: string;
  amfiCode: string | null;
  isin: string | null;
  categoryLabel: string;
  categoryDescription: string;
  statusLabel: string;
  outcomeLabel: string | null;
  briefSectionLabel: string | null;
  checklistKey: string | null;
  provenance: FindingProvenanceSummary;
  isPreservedWithoutWatchlist: boolean;

  // Phase F22: Version Context & Active Evidence Comparison
  activeNavDate?: string | null;
  activeRunId?: number | null;
  isCutoffDrifted: boolean;
  isRunDrifted: boolean;
  isOverdue: boolean;
  hasReopenedHistory: boolean;
  continuityState: "ACTION_REQUIRED" | "WAITING_DEPENDENCY" | "RESOLVED_HISTORICAL" | "DISMISSED_HISTORICAL";
  continuityRationale: string;
  closureState?: InvestigationClosureResolution;
  reopenReason?: string | null;
  closureLimitationRationale?: string | null;

  // Phase F29: Investigation Dependency Tracking
  dependencies?: InvestigationDependency[];
  dependencyEvaluation?: IssueDependencyEvaluation;
}

export interface FundOptionDossierSummary {
  schemeName: string;
  schemeId: number;
  schemeOptionId: number | null | undefined;
  planType: string;
  optionType: string;
  amfiCode: string | null;
  isin: string | null;
  recordCount: number;
  hasResidualUncertainty: boolean;
  unresolvedCount: number;
  activeNavDate?: string | null;
}

export interface ContinuityActionItem {
  issueId: string;
  title: string;
  schemeName: string;
  schemeOptionId: number | null | undefined;
  planType: string;
  optionType: string;
  briefSection?: "A" | "B" | "C" | "D" | "E" | "F" | "G" | "H";
  briefSectionTitle?: string;
  nextAction: string;
  questionOrReason: string;
  targetDate?: string | null;
  isOverdue: boolean;
  status: string;
  categoryLabel: string;
}

export interface ReopenedInquiryItem {
  issueId: string;
  title: string;
  schemeName: string;
  schemeOptionId: number | null | undefined;
  planType: string;
  optionType: string;
  reopenedAt?: string | null;
  priorOutcomeLabel?: string | null;
  priorResolutionNote?: string | null;
  priorEvidenceRunId?: number | null;
  priorEvidenceAsOfDate?: string | null;
  reopenReason?: string | null;
}

export interface InvestigationContinuitySummary {
  openCount: number;
  inProgressCount: number;
  waitingCount: number;
  reopenedCount: number;
  resolvedHistoricalCount: number;
  dismissedHistoricalCount: number;
  overdueCount: number;
  unrecordedProvenanceCount: number;
  evidenceDriftCount: number;
  awaitingDependenciesCount: number;
  addressedDependenciesCount: number;
  nextActionItems: ContinuityActionItem[];
  waitingItems: ContinuityActionItem[];
  reopenedItems: ReopenedInquiryItem[];
}

export interface ResearchDossierMetadata {
  title: string;
  generatedAt: string; // ISO string
  authorNotes?: string;
  totalRecords: number;
  totalFundOptions: number;
  unresolvedCount: number;
  resolvedCount: number;
  residualUncertaintyCount: number;
  withRecordedProvenanceCount: number;
  reopenedCount: number;
  fundOptionList: FundOptionDossierSummary[];
  continuitySummary: InvestigationContinuitySummary;
}

export interface ResearchDossierDocument {
  metadata: ResearchDossierMetadata;
  items: ResearchDossierItem[];
  epistemicDisclaimer: string;
}

export const DOSSIER_EPISTEMIC_DISCLAIMER =
  "CONFIDENTIAL INVESTOR RESEARCH DOSSIER — FOR DUE DILIGENCE AND RESEARCH AUDIT ONLY.\n" +
  "This dossier compiles investor-authored empirical due diligence inquiries, recorded findings, residual uncertainties, " +
  "and point-in-time calculation provenance. Inclusion of a fund option or finding does NOT constitute an investment recommendation, " +
  "fund rating, endorsement, or suitability determination. Historical evidence context is anchored to recorded calculation run IDs and " +
  "point-in-time market cutoffs (such as the canonical pilot cutoff 2024-01-15). Prior findings do not automatically apply to other funds, " +
  "different share classes, or subsequent market periods.";

/**
 * Escapes special HTML characters to prevent XSS in portable HTML export.
 */
export function escapeHtml(str: string | null | undefined): string {
  if (!str) return "";
  return str
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

/**
 * Sanitizes a title string into a safe filesystem filename.
 */
export function sanitizeDossierFilename(title: string, extension: "md" | "html"): string {
  const safe = title
    .toLowerCase()
    .replace(/[^a-z0-9_-]/g, "_")
    .replace(/_+/g, "_")
    .replace(/^_|_$/g, "")
    .substring(0, 60);
  const dateStr = new Date().toISOString().slice(0, 10);
  return `${safe || "yukira_research_dossier"}_${dateStr}.${extension}`;
}

/**
 * Deterministically assembles a research dossier from selected follow-up issues and workspace context,
 * incorporating Phase F22 version context and investigation continuity evaluations.
 */
export function assembleResearchDossier(
  issues: ResearchFollowUpIssue[],
  savedItems?: WatchlistResponse[],
  customTitle?: string,
  authorNotes?: string
): ResearchDossierDocument {
  const nowIso = new Date().toISOString();
  const title = (customTitle && customTitle.trim()) || "YUKIRA Research & Follow-Up Investigation Dossier";

  const enrichedItems: ResearchDossierItem[] = issues.map((issue) => {
    const matchingWatchlistItem = savedItems?.find((item) =>
      issue.schemeOptionId !== undefined && issue.schemeOptionId !== null
        ? item.schemeOptionId === issue.schemeOptionId
        : item.schemeId === issue.schemeId
    );

    const isPreservedWithoutWatchlist = Boolean(savedItems && savedItems.length > 0) && !matchingWatchlistItem;

    const catConfig = FOLLOW_UP_CATEGORIES_CONFIG[issue.category] || FOLLOW_UP_CATEGORIES_CONFIG.OTHER;
    const statusConfig = FOLLOW_UP_STATUS_CONFIG[issue.status] || FOLLOW_UP_STATUS_CONFIG.OPEN;
    const outcomeConfig = issue.outcomeType ? FOLLOW_UP_OUTCOMES_CONFIG[issue.outcomeType] : null;
    const briefSectionLabel = issue.briefSection ? getBriefSectionTitle(issue.briefSection) : null;

    // Phase F22: Resolve active application evidence context from watchlist or snapshot
    const activeNavDate = matchingWatchlistItem?.latestNavDate || null;
    const snapshot = getEvidenceSnapshot(issue.schemeId, issue.schemeOptionId);
    const activeRunId = snapshot?.calculationRunId || null;

    const provenance = evaluateFindingEvidenceTraceability(
      issue,
      activeRunId,
      activeNavDate,
      0
    );

    const isCutoffDrifted = Boolean(
      issue.evidenceAsOfDate && activeNavDate && issue.evidenceAsOfDate !== activeNavDate
    );
    const isRunDrifted = Boolean(
      issue.evidenceRunId && activeRunId && issue.evidenceRunId !== activeRunId
    );
    const isOverdue =
      (issue.status === "OPEN" || issue.status === "IN_PROGRESS" || issue.status === "WAITING") &&
      isTargetDatePassed(issue.targetDate);
    const hasReopenedHistory = Boolean(issue.priorResolutionNote || issue.reopenedAt || issue.priorOutcomeType);

    // Continuity state classification
    let continuityState: "ACTION_REQUIRED" | "WAITING_DEPENDENCY" | "RESOLVED_HISTORICAL" | "DISMISSED_HISTORICAL";
    let continuityRationale: string;

    if (issue.status === "WAITING") {
      continuityState = "WAITING_DEPENDENCY";
      continuityRationale = "Investigation is paused awaiting subsequent NAV publication, AMC factsheet release, or market cycle data.";
    } else if (issue.status === "OPEN" || issue.status === "IN_PROGRESS") {
      continuityState = "ACTION_REQUIRED";
      if (hasReopenedHistory) {
        continuityRationale = "Inquiry was reopened for renewed diligence following a prior historical outcome.";
      } else if (isOverdue) {
        continuityRationale = `Diligence target date passed (${issue.targetDate}); investigation requires review.`;
      } else if (isCutoffDrifted) {
        continuityRationale = `Active workspace observation cutoff (${activeNavDate}) has advanced past recorded cutoff (${issue.evidenceAsOfDate}). Reassessment recommended.`;
      } else {
        continuityRationale = issue.nextAction ? `Active inquiry with recorded next action: "${issue.nextAction}"` : "Active inquiry requiring empirical investigation.";
      }
    } else if (issue.status === "RESOLVED") {
      continuityState = "RESOLVED_HISTORICAL";
      if (issue.residualUncertainty && issue.residualUncertainty.trim().length > 0) {
        continuityRationale = "Resolved with documented residual uncertainty. May warrant reassessment if market conditions change.";
      } else {
        continuityRationale = "Documented outcome recorded by investor. Retained for historical audit.";
      }
    } else {
      continuityState = "DISMISSED_HISTORICAL";
      continuityRationale = "Investor explicitly discontinued this line of inquiry.";
    }

    return {
      issue,
      schemeId: issue.schemeId,
      schemeOptionId: issue.schemeOptionId,
      schemeName: issue.schemeName || matchingWatchlistItem?.schemeName || `Scheme #${issue.schemeId}`,
      schemeCode: issue.schemeCode || matchingWatchlistItem?.schemeCode || `SCHEME_${issue.schemeId}`,
      planType: (issue.planType || matchingWatchlistItem?.planType || "DIRECT").toUpperCase(),
      optionType: (issue.optionType || matchingWatchlistItem?.optionType || "GROWTH").toUpperCase(),
      amfiCode: issue.amfiCode || matchingWatchlistItem?.amfiCode || null,
      isin: issue.isin || matchingWatchlistItem?.isin || null,
      categoryLabel: catConfig.label,
      categoryDescription: catConfig.description,
      statusLabel: statusConfig.label,
      outcomeLabel: outcomeConfig ? outcomeConfig.label : null,
      briefSectionLabel,
      checklistKey: issue.checklistKey || null,
      provenance,
      isPreservedWithoutWatchlist,
      activeNavDate,
      activeRunId,
      isCutoffDrifted,
      isRunDrifted,
      isOverdue,
      hasReopenedHistory,
      continuityState,
      continuityRationale,
      closureState: evaluateInvestigationClosureState(issue),
      reopenReason: issue.reopenReason || null,
      closureLimitationRationale: issue.closureLimitationRationale || null,
      dependencies: issue.dependencies || [],
      dependencyEvaluation: evaluateIssueDependencies(issue),
    };
  });

  // Calculate fund options summary
  const fundMap = new Map<string, FundOptionDossierSummary>();
  enrichedItems.forEach((item) => {
    const key = `${item.schemeId}-${item.schemeOptionId ?? "none"}`;
    const existing = fundMap.get(key);
    const hasUncertainty = Boolean(item.issue.residualUncertainty && item.issue.residualUncertainty.trim().length > 0);
    const isUnresolved = item.issue.status === "OPEN" || item.issue.status === "IN_PROGRESS" || item.issue.status === "WAITING";

    if (!existing) {
      fundMap.set(key, {
        schemeName: item.schemeName,
        schemeId: item.schemeId,
        schemeOptionId: item.schemeOptionId,
        planType: item.planType,
        optionType: item.optionType,
        amfiCode: item.amfiCode,
        isin: item.isin,
        recordCount: 1,
        hasResidualUncertainty: hasUncertainty,
        unresolvedCount: isUnresolved ? 1 : 0,
        activeNavDate: item.activeNavDate,
      });
    } else {
      existing.recordCount += 1;
      if (hasUncertainty) existing.hasResidualUncertainty = true;
      if (isUnresolved) existing.unresolvedCount += 1;
    }
  });

  const fundOptionList = Array.from(fundMap.values());

  // Compile Phase F22 Investigation Continuity Roadmap
  const nextActionItems: ContinuityActionItem[] = enrichedItems
    .filter((i) => i.continuityState === "ACTION_REQUIRED")
    .map((i) => ({
      issueId: i.issue.id,
      title: i.issue.title,
      schemeName: i.schemeName,
      schemeOptionId: i.schemeOptionId,
      planType: i.planType,
      optionType: i.optionType,
      briefSection: i.issue.briefSection,
      briefSectionTitle: i.briefSectionLabel || undefined,
      nextAction: i.issue.nextAction || "No next action specified.",
      questionOrReason: i.issue.questionOrReason || "No question recorded.",
      targetDate: i.issue.targetDate,
      isOverdue: i.isOverdue,
      status: i.statusLabel,
      categoryLabel: i.categoryLabel,
    }));

  const waitingItems: ContinuityActionItem[] = enrichedItems
    .filter((i) => i.continuityState === "WAITING_DEPENDENCY")
    .map((i) => ({
      issueId: i.issue.id,
      title: i.issue.title,
      schemeName: i.schemeName,
      schemeOptionId: i.schemeOptionId,
      planType: i.planType,
      optionType: i.optionType,
      briefSection: i.issue.briefSection,
      briefSectionTitle: i.briefSectionLabel || undefined,
      nextAction: i.issue.nextAction || "Awaiting market/cutoff data.",
      questionOrReason: i.issue.questionOrReason || "No question recorded.",
      targetDate: i.issue.targetDate,
      isOverdue: i.isOverdue,
      status: i.statusLabel,
      categoryLabel: i.categoryLabel,
    }));

  const reopenedItems: ReopenedInquiryItem[] = enrichedItems
    .filter((i) => i.hasReopenedHistory)
    .map((i) => ({
      issueId: i.issue.id,
      title: i.issue.title,
      schemeName: i.schemeName,
      schemeOptionId: i.schemeOptionId,
      planType: i.planType,
      optionType: i.optionType,
      reopenedAt: i.issue.reopenedAt,
      priorOutcomeLabel: i.issue.priorOutcomeType && FOLLOW_UP_OUTCOMES_CONFIG[i.issue.priorOutcomeType]
        ? FOLLOW_UP_OUTCOMES_CONFIG[i.issue.priorOutcomeType].label
        : null,
      priorResolutionNote: i.issue.priorResolutionNote,
      priorEvidenceRunId: i.issue.priorEvidenceRunId,
      priorEvidenceAsOfDate: i.issue.priorEvidenceAsOfDate,
      reopenReason: i.issue.reopenReason || null,
    }));

  const continuitySummary: InvestigationContinuitySummary = {
    openCount: enrichedItems.filter((i) => i.issue.status === "OPEN").length,
    inProgressCount: enrichedItems.filter((i) => i.issue.status === "IN_PROGRESS").length,
    waitingCount: enrichedItems.filter((i) => i.issue.status === "WAITING").length,
    reopenedCount: reopenedItems.length,
    resolvedHistoricalCount: enrichedItems.filter((i) => i.issue.status === "RESOLVED").length,
    dismissedHistoricalCount: enrichedItems.filter((i) => i.issue.status === "DISMISSED").length,
    overdueCount: enrichedItems.filter((i) => i.isOverdue).length,
    unrecordedProvenanceCount: enrichedItems.filter(
      (i) => i.issue.evidenceRunId === null && i.issue.evidenceRunId === undefined && !i.issue.evidenceAsOfDate
    ).length,
    evidenceDriftCount: enrichedItems.filter((i) => i.isCutoffDrifted || i.isRunDrifted).length,
    awaitingDependenciesCount: enrichedItems.reduce(
      (acc, item) => acc + (item.dependencies?.filter((d) => d.status === "AWAITING").length || 0),
      0
    ),
    addressedDependenciesCount: enrichedItems.reduce(
      (acc, item) => acc + (item.dependencies?.filter((d) => d.status === "ADDRESSED").length || 0),
      0
    ),
    nextActionItems,
    waitingItems,
    reopenedItems,
  };

  const metadata: ResearchDossierMetadata = {
    title,
    generatedAt: nowIso,
    authorNotes: authorNotes?.trim() || undefined,
    totalRecords: enrichedItems.length,
    totalFundOptions: fundOptionList.length,
    unresolvedCount: enrichedItems.filter(
      (i) => i.issue.status === "OPEN" || i.issue.status === "IN_PROGRESS" || i.issue.status === "WAITING"
    ).length,
    resolvedCount: enrichedItems.filter(
      (i) => i.issue.status === "RESOLVED" || i.issue.status === "DISMISSED"
    ).length,
    residualUncertaintyCount: enrichedItems.filter(
      (i) => Boolean(i.issue.residualUncertainty && i.issue.residualUncertainty.trim().length > 0)
    ).length,
    withRecordedProvenanceCount: enrichedItems.filter(
      (i) => (i.issue.evidenceRunId !== null && i.issue.evidenceRunId !== undefined) || Boolean(i.issue.evidenceAsOfDate)
    ).length,
    reopenedCount: reopenedItems.length,
    fundOptionList,
    continuitySummary,
  };

  return {
    metadata,
    items: enrichedItems,
    epistemicDisclaimer: DOSSIER_EPISTEMIC_DISCLAIMER,
  };
}

/**
 * Generates an institutional-grade GitHub Flavored Markdown document from a research dossier,
 * including Phase F22 Investigation Continuity and Version Context roadmap sections.
 */
export function generateDossierMarkdown(dossier: ResearchDossierDocument): string {
  const { metadata, items, epistemicDisclaimer } = dossier;
  const { continuitySummary } = metadata;
  const lines: string[] = [];

  // 1. Cover & Header
  lines.push(`# ${metadata.title}`);
  lines.push("");
  lines.push(`> **YUKIRA Quantitative Investment Intelligence Platform — Empirical Research Dossier**`);
  lines.push(`> **Dossier Generated At:** \`${metadata.generatedAt}\``);
  lines.push(`> **Dossier Scope:** **${metadata.totalRecords}** research record(s) across **${metadata.totalFundOptions}** fund share class(es)`);
  lines.push("");

  if (metadata.authorNotes) {
    lines.push("### Investor Review & Audit Context");
    lines.push(metadata.authorNotes);
    lines.push("");
  }

  // 2. Epistemic Warning Banner
  lines.push("---");
  lines.push("### ⚠️ Epistemic Notice & Evidentiary Scope");
  lines.push(epistemicDisclaimer);
  lines.push("");

  // 3. Dossier Scope Summary Table
  lines.push("### Executive Dossier Overview");
  lines.push("");
  lines.push("| Metric Dimension | Count | Details / Methodology Context |");
  lines.push("| :--- | :--- | :--- |");
  lines.push(`| **Total Documented Inquiries** | ${metadata.totalRecords} | Diligence questions and findings selected for this dossier |`);
  lines.push(`| **Unique Fund Share Classes** | ${metadata.totalFundOptions} | Exact \`(schemeId, schemeOptionId)\` tuples preserved |`);
  lines.push(`| **Active Inquiries Requiring Action** | ${continuitySummary.openCount + continuitySummary.inProgressCount} | Inquiries with status \`OPEN\` or \`IN_PROGRESS\` |`);
  lines.push(`| **Inquiries Awaiting Dependencies** | ${continuitySummary.waitingCount} | Status \`WAITING\` on market data, cutoff, or AMC factsheets |`);
  lines.push(`| **Target Diligence Date Passed** | ${continuitySummary.overdueCount} | Active inquiries whose target date has lapsed |`);
  lines.push(`| **Resolved Historical Findings** | ${continuitySummary.resolvedHistoricalCount} | Documented outcomes with factual notes recorded by investor |`);
  lines.push(`| **With Residual Uncertainty** | ${metadata.residualUncertaintyCount} | Inquiries with documented evidentiary or analytical limitations |`);
  lines.push(`| **Recorded Evidence Provenance** | ${metadata.withRecordedProvenanceCount} | Records anchored to an authoritative calculation run ID or cutoff |`);
  lines.push(`| **Reopened Historical Inquiries** | ${metadata.reopenedCount} | Inquiries reopened after a prior outcome note was preserved |`);
  lines.push(`| **Observation Cutoff Drift Detected** | ${continuitySummary.evidenceDriftCount} | Records where active application cutoff has advanced |`);
  lines.push("");

  // 4. Fund Options Inventory
  lines.push("### Fund Share Classes in Scope");
  lines.push("");
  lines.push("| Scheme Name | Option ID | Plan | Option | AMFI Code | ISIN | Inquiries | Active | Uncertainty? | Active Cutoff |");
  lines.push("| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |");
  metadata.fundOptionList.forEach((f) => {
    lines.push(
      `| **${f.schemeName}** | \`#${f.schemeOptionId ?? f.schemeId}\` | \`${f.planType}\` | \`${f.optionType}\` | \`${f.amfiCode || "—"}\` | \`${f.isin || "—"}\` | ${f.recordCount} | ${f.unresolvedCount} | ${f.hasResidualUncertainty ? "⚠️ Yes" : "None"} | \`${f.activeNavDate || "Context Unavailable"}\` |`
    );
  });
  lines.push("");

  // 5. Phase F22: Investigation Continuity & Actionable Next Steps Section
  lines.push("---");
  lines.push("## Investigation Continuity & Actionable Next Steps");
  lines.push("");
  lines.push(
    "This section outlines outstanding due diligence steps, inquiries awaiting market cycle data, and reopened investigations requiring renewed attention. It connects historical findings to active follow-up work without conflating past evidence with current market states."
  );
  lines.push("");

  if (continuitySummary.nextActionItems.length > 0) {
    lines.push("### 1. Outstanding Investigation Actions");
    lines.push("");
    lines.push("| Priority | Inquiry Title | Fund Share Class | Section | Target Date | Actionable Next Step |");
    lines.push("| :--- | :--- | :--- | :--- | :--- | :--- |");
    continuitySummary.nextActionItems.forEach((item, idx) => {
      const overdueTag = item.isOverdue ? " ⚠️ **OVERDUE**" : "";
      lines.push(
        `| **#${idx + 1}** | **${item.title}** | ${item.schemeName} (\`#${item.schemeOptionId ?? "—"}\` • \`${item.planType}\`) | Sec ${item.briefSection || "—"} | \`${item.targetDate || "No Date"}\`${overdueTag} | ${item.nextAction} |`
      );
    });
    lines.push("");
  } else {
    lines.push("### 1. Outstanding Investigation Actions");
    lines.push("*No open or in-progress investigation actions currently outstanding in this dossier.*");
    lines.push("");
  }

  if (continuitySummary.waitingItems.length > 0) {
    lines.push("### 2. Inquiries Awaiting Dependencies");
    lines.push("");
    lines.push("| Inquiry Title | Fund Share Class | Dependency / Next Action | Target Date |");
    lines.push("| :--- | :--- | :--- | :--- |");
    continuitySummary.waitingItems.forEach((w) => {
      lines.push(
        `| **${w.title}** | ${w.schemeName} (\`#${w.schemeOptionId ?? "—"}\`) | ${w.nextAction} | \`${w.targetDate || "No Date"}\` |`
      );
    });
    lines.push("");
  }

  if (continuitySummary.reopenedItems.length > 0) {
    lines.push("### 3. Reopened Inquiries Requiring Renewed Due Diligence");
    lines.push("");
    lines.push("| Inquiry Title | Fund Share Class | Reopened At | Prior Outcome | Prior Evidence Context |");
    lines.push("| :--- | :--- | :--- | :--- | :--- |");
    continuitySummary.reopenedItems.forEach((r) => {
      lines.push(
        `| **${r.title}** | ${r.schemeName} (\`#${r.schemeOptionId ?? "—"}\`) | \`${r.reopenedAt ? r.reopenedAt.slice(0, 10) : "Recorded"}\` | ${r.priorOutcomeLabel || "Prior Outcome"} | Run \`#${r.priorEvidenceRunId || "Unrecorded"}\` (Cutoff: \`${r.priorEvidenceAsOfDate || "Unrecorded"}\`) |`
      );
    });
    lines.push("");
  }

  // 6. Detailed Inquiry Records
  lines.push("---");
  lines.push("## Detailed Investigation Records & Evidentiary Lineage");
  lines.push("");

  items.forEach((item, idx) => {
    const { issue, provenance } = item;
    lines.push(`### ${idx + 1}. ${issue.title}`);
    lines.push("");

    // Identity block
    lines.push(`- **Fund Share Class:** ${item.schemeName} (Option \`#${item.schemeOptionId ?? item.schemeId}\` • \`${item.planType}\` • \`${item.optionType}\`)`);
    if (item.amfiCode || item.isin) {
      lines.push(`- **Identifiers:** AMFI: \`${item.amfiCode || "—"}\` | ISIN: \`${item.isin || "—"}\``);
    }
    if (item.isPreservedWithoutWatchlist) {
      lines.push(`- **Storage Lineage:** *Preserved record (fund option is not currently in active saved workspace — view-only historical record)*`);
    }
    lines.push(`- **Investigation Category:** ${item.categoryLabel}`);
    lines.push(`- **Lifecycle Status:** \`${item.statusLabel}\``);
    if (item.closureState) {
      lines.push(`- **Closure State:** \`${item.closureState.label}\``);
    }
    lines.push(`- **Continuity Posture:** \`${item.continuityState}\` — *${item.continuityRationale}*`);
    if (item.briefSectionLabel) {
      lines.push(`- **Research Brief Location:** Section ${issue.briefSection} (${item.briefSectionLabel})`);
    }
    if (issue.checklistKey) {
      lines.push(`- **Checklist Dimension:** \`${issue.checklistKey}\``);
    }
    if (issue.targetDate) {
      const overdueTag = item.isOverdue ? " (⚠️ **Diligence Target Lapsed**)" : "";
      lines.push(`- **Target Diligence Date:** \`${issue.targetDate}\`${overdueTag}`);
    }

    // Phase F22: Temporal Version Context
    lines.push("");
    lines.push(`#### Temporal Version Context`);
    lines.push(`| Version Dimension | Timestamp / Value | Epistemic Significance |`);
    lines.push(`| :--- | :--- | :--- |`);
    lines.push(`| **Dossier Generated** | \`${metadata.generatedAt}\` | Time this portable document was assembled |`);
    lines.push(`| **Inquiry Recorded** | \`${issue.createdAt}\` | Timestamp inquiry was authored |`);
    lines.push(`| **Inquiry Last Updated** | \`${issue.updatedAt}\` | Timestamp of latest investor edit |`);
    if (issue.resolvedAt) {
      lines.push(`| **Outcome Documented** | \`${issue.resolvedAt}\` | Timestamp of factual outcome recording |`);
    }
    if (issue.reopenedAt) {
      lines.push(`| **Inquiry Reopened** | \`${issue.reopenedAt}\` | Timestamp investigation was reopened |`);
    }
    lines.push(`| **Recorded Calculation Run** | ${issue.evidenceRunId ? `Run \`#${issue.evidenceRunId}\`` : "*Unrecorded*"} | Historical engine execution supporting finding |`);
    lines.push(`| **Recorded Observation Cutoff** | \`${issue.evidenceAsOfDate || "Unrecorded"}\` | Market observation cutoff when finding was authored |`);
    lines.push(`| **Active Workspace Cutoff** | \`${item.activeNavDate || "Context Unavailable"}\` | Latest observation cutoff in active workspace |`);
    if (item.isCutoffDrifted) {
      lines.push(`| **Cutoff Comparability** | ⚠️ **CUTOFF DRIFT DETECTED** | Active cutoff has advanced past historical cutoff (\`${issue.evidenceAsOfDate}\` vs \`${item.activeNavDate}\`) |`);
    }
    lines.push("");

    // Original Question
    lines.push(`#### Diligence Question / Investigation Reason`);
    lines.push(issue.questionOrReason ? issue.questionOrReason : "*No explicit question text recorded.*");
    lines.push("");

    // Next Action
    lines.push(`#### Actionable Next Investigation Step`);
    lines.push(issue.nextAction ? issue.nextAction : "*No actionable next step specified.*");
    lines.push("");

    // Phase F29: Investigation Dependencies & Prerequisites
    if (item.dependencies && item.dependencies.length > 0) {
      lines.push(`#### ⏳ Investigation Prerequisites & Blockers (${item.dependencyEvaluation?.stateLabel || "Dependencies"})`);
      lines.push(`| Prerequisite Description | Category | Status | Reason Blocked | Expected Source / Received Evidence |`);
      lines.push(`| :--- | :--- | :--- | :--- | :--- |`);
      item.dependencies.forEach((dep) => {
        const catCfg = DEPENDENCY_CATEGORY_CONFIG[dep.category];
        const statusCfg = DEPENDENCY_STATUS_CONFIG[dep.status];
        const evidenceOrSource = dep.status === "ADDRESSED"
          ? `Received: ${dep.receivedInfoOrChange || "Yes"}${dep.resolutionEvidenceReference ? " (" + dep.resolutionEvidenceReference + ")" : ""}`
          : (dep.expectedSource ? `Expected: ${dep.expectedSource}` : "External");
        lines.push(
          `| **${dep.description}** | ${catCfg?.shortLabel || dep.category} | \`${statusCfg?.shortLabel || dep.status}\` | ${dep.reasonWhyBlocked} | ${evidenceOrSource} |`
        );
      });
      lines.push("");
    }

    // Evidence Provenance
    lines.push(`#### Evidentiary Provenance & Traceability Context`);
    lines.push(`- **Traceability Status:** \`${provenance.statusBadgeLabel}\` — *${provenance.statusDescription}*`);
    lines.push(`- **Recorded Calculation Run:** ${issue.evidenceRunId ? `Run \`#${issue.evidenceRunId}\`` : "*Calculation run not recorded*"}`);
    lines.push(`- **Observation Cutoff Date:** ${issue.evidenceAsOfDate ? `\`${issue.evidenceAsOfDate}\`` : "*Observation cutoff not recorded*"}`);
    lines.push("");

    // Current Outcome (if recorded)
    if (issue.resolutionNote || issue.outcomeType) {
      lines.push(`#### Documented Investigation Outcome`);
      if (item.outcomeLabel) {
        lines.push(`- **Outcome Classification:** **${item.outcomeLabel}**`);
      }
      if (issue.closureLimitationRationale) {
        lines.push(`- **Closure Limitation Rationale:** *${issue.closureLimitationRationale}*`);
      }
      if (issue.resolutionNote) {
        lines.push("");
        lines.push(`> ${issue.resolutionNote.replace(/\n/g, "\n> ")}`);
        lines.push("");
      }
    }

    // Residual Uncertainty
    if (issue.residualUncertainty && issue.residualUncertainty.trim().length > 0) {
      lines.push(`#### ⚠️ Residual Uncertainty & Documented Limitations`);
      lines.push(`> **Uncertainty Note:** ${issue.residualUncertainty.replace(/\n/g, "\n> ")}`);
      lines.push("");
    }

    // Prior Outcome / Reopened Investigation Audit Trail
    if (item.hasReopenedHistory) {
      lines.push(`#### 🔄 Historical Reopened Inquiry Audit Trail`);
      lines.push(`This inquiry was previously resolved and subsequently reopened for renewed due diligence.`);
      if (issue.reopenedAt) {
        lines.push(`- **Reopened At:** \`${issue.reopenedAt}\``);
      }
      if (issue.reopenReason) {
        lines.push(`- **Renewal Rationale:** *${issue.reopenReason}*`);
      }
      if (issue.priorOutcomeType && FOLLOW_UP_OUTCOMES_CONFIG[issue.priorOutcomeType]) {
        lines.push(`- **Prior Outcome Classification:** ${FOLLOW_UP_OUTCOMES_CONFIG[issue.priorOutcomeType].label}`);
      }
      if (issue.priorEvidenceRunId || issue.priorEvidenceAsOfDate) {
        lines.push(
          `- **Prior Evidence Context:** Run \`#${issue.priorEvidenceRunId || "Unrecorded"}\` (Cutoff: \`${issue.priorEvidenceAsOfDate || "Unrecorded"}\`)`
        );
      }
      if (issue.priorResolutionNote) {
        lines.push(`- **Prior Resolution Note:**`);
        lines.push(`  > ${issue.priorResolutionNote.replace(/\n/g, "\n  > ")}`);
      }
      lines.push("");
    }

    lines.push("---");
    lines.push("");
  });

  // Footer Disclaimer
  lines.push("## Methodological & Epistemic Governance Constraints");
  lines.push("");
  lines.push(
    "1. **Deterministic Calculation Standard:** All quantitative metrics referenced in YUKIRA derive from pure mathematical algorithms executed under fixed point-in-time constraints. Financial math is never estimated, approximated, or synthesized by Large Language Models."
  );
  lines.push(
    "2. **Point-in-Time Integrity:** Historical evaluations adhere to strict knowledge cutoffs. A calculation cutoff of `2024-01-15` reflects observations available up to that market cutoff and must not be represented as real-time or current market performance."
  );
  lines.push(
    "3. **Investor-Authored Record:** Inquiry titles, questions, next actions, resolution notes, and residual uncertainties reflect the personal due-diligence work of the investor and have not been validated by third-party auditors."
  );
  lines.push(
    "4. **Investigation Continuity Rule:** Reopening an investigation or reviewing an older dossier does not reconstruct historical state or imply that evidence was refreshed. Reassessment must be conducted against authoritative current calculation runs."
  );
  lines.push("");
  lines.push(`*Generated deterministically by YUKIRA Quantitative Intelligence Platform — ${metadata.generatedAt}*`);

  return lines.join("\n");
}

/**
 * Generates a clean, standalone, responsive, print-friendly HTML dossier document,
 * complete with Phase F22 Investigation Continuity and Version Context cards.
 */
export function generateDossierHtml(dossier: ResearchDossierDocument): string {
  const { metadata, items, epistemicDisclaimer } = dossier;
  const { continuitySummary } = metadata;

  // HTML Action items list
  const nextActionsHtml = continuitySummary.nextActionItems.length > 0
    ? `
      <div class="continuity-actions-list">
        ${continuitySummary.nextActionItems.map((act, i) => `
          <div class="action-card ${act.isOverdue ? 'action-overdue' : ''}">
            <div class="action-card-header">
              <span class="action-num">#${i + 1}</span>
              <strong class="action-title">${escapeHtml(act.title)}</strong>
              <span class="badge badge-status">${escapeHtml(act.status)}</span>
              ${act.isOverdue ? '<span class="badge badge-overdue">⚠️ TARGET LAPSED</span>' : ''}
              <span class="action-fund">${escapeHtml(act.schemeName)} (<code>#${escapeHtml(String(act.schemeOptionId ?? '—'))}</code> • ${escapeHtml(act.planType)})</span>
            </div>
            <div class="action-body">
              <div class="action-target"><span class="label">Target Date:</span> <code>${escapeHtml(act.targetDate || 'No date set')}</code></div>
              <div class="action-step"><span class="label">Action:</span> <strong>${escapeHtml(act.nextAction)}</strong></div>
            </div>
          </div>
        `).join('')}
      </div>
    `
    : '<p class="empty-note">No outstanding investigation actions in this dossier.</p>';

  // HTML Reopened items list
  const reopenedHtml = continuitySummary.reopenedItems.length > 0
    ? `
      <div class="continuity-reopened-list">
        ${continuitySummary.reopenedItems.map((r) => `
          <div class="reopened-card">
            <div class="reopened-header">
              <strong class="action-title">${escapeHtml(r.title)}</strong>
              <span class="badge badge-reopened">🔄 REOPENED</span>
              <span class="action-fund">${escapeHtml(r.schemeName)} (<code>#${escapeHtml(String(r.schemeOptionId ?? '—'))}</code>)</span>
            </div>
            <div class="reopened-body">
              <div><span class="label">Reopened At:</span> <code>${escapeHtml(r.reopenedAt ? r.reopenedAt.slice(0, 10) : 'Recorded')}</code></div>
              <div><span class="label">Prior Outcome:</span> <strong>${escapeHtml(r.priorOutcomeLabel || 'Prior Outcome')}</strong></div>
              ${r.priorResolutionNote ? `<div class="prior-note">${escapeHtml(r.priorResolutionNote)}</div>` : ''}
            </div>
          </div>
        `).join('')}
      </div>
    `
    : '';

  const htmlItems = items
    .map((item, idx) => {
      const { issue, provenance } = item;

      return `
      <section class="dossier-card" id="record-${idx + 1}">
        <div class="card-header">
          <div class="card-title-row">
            <span class="card-index">#${idx + 1}</span>
            <h3 class="card-title">${escapeHtml(issue.title)}</h3>
          </div>
          <div class="card-badges">
            <span class="badge badge-category">${escapeHtml(item.categoryLabel)}</span>
            <span class="badge badge-status">${escapeHtml(item.statusLabel)}</span>
            ${item.outcomeLabel ? `<span class="badge badge-outcome">${escapeHtml(item.outcomeLabel)}</span>` : ""}
            <span class="badge badge-provenance">${escapeHtml(provenance.statusBadgeLabel)}</span>
            ${issue.residualUncertainty ? `<span class="badge badge-uncertainty">⚠️ Residual Uncertainty</span>` : ""}
            ${item.hasReopenedHistory ? `<span class="badge badge-reopened">🔄 Reopened</span>` : ""}
            ${item.isOverdue ? `<span class="badge badge-overdue">⚠️ Target Passed</span>` : ""}
            ${item.isCutoffDrifted ? `<span class="badge badge-drift">⚠️ Cutoff Drifted</span>` : ""}
          </div>
        </div>

        <div class="identity-box">
          <div class="identity-item">
            <span class="label">Fund Share Class:</span>
            <strong>${escapeHtml(item.schemeName)}</strong>
          </div>
          <div class="identity-item">
            <span class="label">Share Class ID:</span>
            <code>#${escapeHtml(String(item.schemeOptionId ?? item.schemeId))}</code>
          </div>
          <div class="identity-item">
            <span class="label">Plan / Option:</span>
            <span>${escapeHtml(item.planType)} • ${escapeHtml(item.optionType)}</span>
          </div>
          ${item.amfiCode ? `<div class="identity-item"><span class="label">AMFI:</span> <code>${escapeHtml(item.amfiCode)}</code></div>` : ""}
          ${item.isin ? `<div class="identity-item"><span class="label">ISIN:</span> <code>${escapeHtml(item.isin)}</code></div>` : ""}
          ${item.briefSectionLabel ? `<div class="identity-item"><span class="label">Brief Section:</span> <span>Sec ${escapeHtml(issue.briefSection)} (${escapeHtml(item.briefSectionLabel)})</span></div>` : ""}
          ${item.isPreservedWithoutWatchlist ? `<div class="identity-item lineage-warning"><span>⚠️ Preserved record (fund option not in active saved workspace — view-only)</span></div>` : ""}
        </div>

        <!-- Version & Temporal Context -->
        <div class="version-strip">
          <div class="version-item"><span class="v-label">Recorded Cutoff:</span> <code>${escapeHtml(issue.evidenceAsOfDate || "Unrecorded")}</code></div>
          <div class="version-item"><span class="v-label">Active Cutoff:</span> <code>${escapeHtml(item.activeNavDate || "Unavailable")}</code></div>
          <div class="version-item"><span class="v-label">Continuity:</span> <span class="v-state">${escapeHtml(item.continuityState)}</span></div>
          <div class="version-item"><span class="v-label">Last Edit:</span> <code>${escapeHtml(issue.updatedAt.slice(0, 10))}</code></div>
        </div>

        <div class="content-section">
          <h4 class="section-subheading">Diligence Question / Reason:</h4>
          <p class="section-text question-text">${escapeHtml(issue.questionOrReason) || "<em>No question recorded.</em>"}</p>
        </div>

        <div class="content-section">
          <h4 class="section-subheading">Actionable Next Investigation Step:</h4>
          <p class="section-text next-step-text">${escapeHtml(issue.nextAction) || "<em>No next step specified.</em>"}</p>
        </div>

        ${item.dependencies && item.dependencies.length > 0 ? `
        <div class="content-section dependencies-section">
          <h4 class="section-subheading">⏳ Prerequisites &amp; Blockers (${escapeHtml(item.dependencyEvaluation?.stateLabel || 'Dependencies')}):</h4>
          <div class="dependencies-list" style="margin-top: 0.35rem; display: flex; flex-direction: column; gap: 0.35rem;">
            ${item.dependencies.map((dep) => `
              <div style="background: rgba(255,255,255,0.02); border: 1px solid var(--border); border-radius: 4px; padding: 0.5rem; font-size: 0.75rem;">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.25rem;">
                  <strong>${escapeHtml(dep.description)}</strong>
                  <span class="badge ${dep.status === 'ADDRESSED' ? 'badge-complete' : 'badge-status'}">${escapeHtml(dep.status)}</span>
                </div>
                <div style="color: var(--text-muted); font-size: 0.7rem;">
                  <span><strong>Reason:</strong> ${escapeHtml(dep.reasonWhyBlocked)}</span>
                  ${dep.expectedSource ? `<span> • <strong>Expected from:</strong> ${escapeHtml(dep.expectedSource)}</span>` : ''}
                  ${dep.receivedInfoOrChange ? `<div style="color: var(--accent); margin-top: 0.15rem;"><strong>Received:</strong> ${escapeHtml(dep.receivedInfoOrChange)}</div>` : ''}
                  ${dep.resolutionRationale ? `<div style="color: var(--text-secondary); margin-top: 0.15rem;"><strong>Resolution Rationale:</strong> ${escapeHtml(dep.resolutionRationale)}</div>` : ''}
                </div>
              </div>
            `).join('')}
          </div>
        </div>
        ` : ""}

        <div class="content-section provenance-section">
          <h4 class="section-subheading">Evidentiary Provenance:</h4>
          <div class="provenance-details">
            <div class="prov-badge ${provenance.status === "HISTORICAL_EVIDENCE_CHANGED" || item.isCutoffDrifted ? "prov-drift" : ""}">
              <strong>${escapeHtml(provenance.statusBadgeLabel)}:</strong> ${escapeHtml(provenance.statusDescription)}
            </div>
            <div class="prov-meta">
              <span><strong>Recorded Run:</strong> ${issue.evidenceRunId ? `Run #${escapeHtml(String(issue.evidenceRunId))}` : "Run not recorded"}</span>
              <span>•</span>
              <span><strong>Observation Cutoff:</strong> ${issue.evidenceAsOfDate ? escapeHtml(issue.evidenceAsOfDate) : "Cutoff not recorded"}</span>
              <span>•</span>
              <span><strong>Created:</strong> ${escapeHtml(issue.createdAt.slice(0, 10))}</span>
              ${item.isCutoffDrifted ? `<span class="warn">• ⚠️ Active Cutoff is ${escapeHtml(item.activeNavDate || '')}</span>` : ""}
            </div>
          </div>
        </div>

        ${issue.resolutionNote || issue.outcomeType ? `
        <div class="content-section outcome-section">
          <h4 class="section-subheading">Documented Outcome:</h4>
          ${item.outcomeLabel ? `<div class="outcome-classification"><strong>Outcome:</strong> ${escapeHtml(item.outcomeLabel)}</div>` : ""}
          ${issue.closureLimitationRationale ? `<div style="font-size: 0.75rem; color: var(--amber); margin-bottom: 0.35rem;"><strong>Accepted Limitation Rationale:</strong> ${escapeHtml(issue.closureLimitationRationale)}</div>` : ""}
          ${issue.resolutionNote ? `<div class="outcome-note-box">${escapeHtml(issue.resolutionNote)}</div>` : ""}
        </div>
        ` : ""}

        ${issue.residualUncertainty ? `
        <div class="content-section uncertainty-section">
          <h4 class="section-subheading uncertainty-heading">⚠️ Residual Uncertainty &amp; Documented Limitations:</h4>
          <div class="uncertainty-box">${escapeHtml(issue.residualUncertainty)}</div>
        </div>
        ` : ""}

        ${item.hasReopenedHistory ? `
        <div class="content-section reopened-section">
          <h4 class="section-subheading reopened-heading">🔄 Preserved Historical Prior Outcome (Reopened Inquiry):</h4>
          <div class="reopened-box">
            ${issue.reopenedAt ? `<div><strong>Reopened At:</strong> ${escapeHtml(issue.reopenedAt.slice(0, 10))}</div>` : ""}
            ${issue.reopenReason ? `<div><strong>Renewal Rationale:</strong> ${escapeHtml(issue.reopenReason)}</div>` : ""}
            ${issue.priorOutcomeType && FOLLOW_UP_OUTCOMES_CONFIG[issue.priorOutcomeType] ? `<div><strong>Prior Classification:</strong> ${escapeHtml(FOLLOW_UP_OUTCOMES_CONFIG[issue.priorOutcomeType].label)}</div>` : ""}
            ${issue.priorEvidenceRunId || issue.priorEvidenceAsOfDate ? `<div><strong>Prior Evidence Run:</strong> #${escapeHtml(String(issue.priorEvidenceRunId || "Unrecorded"))} (Cutoff: ${escapeHtml(issue.priorEvidenceAsOfDate || "Unrecorded")})</div>` : ""}
            ${issue.priorResolutionNote ? `<div class="prior-note">${escapeHtml(issue.priorResolutionNote)}</div>` : ""}
          </div>
        </div>
        ` : ""}
      </section>
      `;
    })
    .join("\n");

  const fundRows = metadata.fundOptionList
    .map(
      (f) => `
      <tr>
        <td><strong>${escapeHtml(f.schemeName)}</strong></td>
        <td><code>#${escapeHtml(String(f.schemeOptionId ?? f.schemeId))}</code></td>
        <td><span class="tag">${escapeHtml(f.planType)}</span></td>
        <td><span class="tag">${escapeHtml(f.optionType)}</span></td>
        <td><code>${escapeHtml(f.amfiCode || "—")}</code></td>
        <td><code>${escapeHtml(f.isin || "—")}</code></td>
        <td class="text-center">${f.recordCount}</td>
        <td class="text-center">${f.unresolvedCount}</td>
        <td class="text-center">${f.hasResidualUncertainty ? '<span class="warn">⚠️ Yes</span>' : "None"}</td>
        <td><code>${escapeHtml(f.activeNavDate || "Context Unavailable")}</code></td>
      </tr>
    `
    )
    .join("\n");

  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${escapeHtml(metadata.title)}</title>
  <style>
    :root {
      --bg: #0b0f19;
      --surface: #111827;
      --surface-raised: #1f2937;
      --border: #374151;
      --text-primary: #f3f4f6;
      --text-secondary: #9ca3af;
      --text-muted: #6b7280;
      --accent: #38bdf8;
      --accent-dim: rgba(56, 189, 248, 0.15);
      --amber: #fbbf24;
      --amber-dim: rgba(251, 191, 36, 0.15);
      --emerald: #34d399;
      --emerald-dim: rgba(52, 211, 153, 0.15);
      --rose: #f87171;
      --rose-dim: rgba(248, 113, 113, 0.15);
      --purple: #c084fc;
      --purple-dim: rgba(192, 132, 252, 0.15);
    }
    @media (prefers-color-scheme: light) {
      :root {
        --bg: #f9fafb;
        --surface: #ffffff;
        --surface-raised: #f3f4f6;
        --border: #e5e7eb;
        --text-primary: #111827;
        --text-secondary: #4b5563;
        --text-muted: #6b7280;
        --accent: #0284c7;
        --accent-dim: rgba(2, 132, 199, 0.12);
        --amber: #d97706;
        --amber-dim: rgba(217, 119, 6, 0.12);
        --emerald: #059669;
        --emerald-dim: rgba(5, 150, 105, 0.12);
        --rose: #dc2626;
        --rose-dim: rgba(220, 38, 38, 0.12);
        --purple: #7c3aed;
        --purple-dim: rgba(124, 58, 237, 0.12);
      }
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
      background: var(--bg);
      color: var(--text-primary);
      line-height: 1.5;
      padding: 2rem 1rem;
    }
    .container {
      max-width: 960px;
      margin: 0 auto;
    }
    header {
      border-bottom: 2px solid var(--border);
      padding-bottom: 1.5rem;
      margin-bottom: 2rem;
    }
    .brand-eyebrow {
      font-size: 0.75rem;
      font-weight: 700;
      color: var(--accent);
      letter-spacing: 0.08em;
      text-transform: uppercase;
      font-family: monospace;
    }
    h1 {
      font-size: 1.75rem;
      font-weight: 800;
      margin-top: 0.35rem;
      margin-bottom: 0.5rem;
    }
    .meta-bar {
      font-size: 0.8rem;
      color: var(--text-secondary);
      font-family: monospace;
    }
    .author-notes-card {
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 8px;
      padding: 1rem;
      margin-top: 1rem;
      font-size: 0.85rem;
    }
    .author-notes-card h3 {
      font-size: 0.75rem;
      text-transform: uppercase;
      color: var(--text-muted);
      margin-bottom: 0.25rem;
      font-family: monospace;
    }
    .disclaimer-banner {
      background: var(--amber-dim);
      border: 1px solid var(--amber);
      color: var(--text-primary);
      border-radius: 8px;
      padding: 1rem;
      margin-bottom: 2rem;
      font-size: 0.8rem;
      line-height: 1.4;
    }
    .disclaimer-banner strong { color: var(--amber); }
    .stats-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
      gap: 0.75rem;
      margin-bottom: 2rem;
    }
    .stat-card {
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 6px;
      padding: 0.75rem;
    }
    .stat-label {
      font-size: 0.7rem;
      color: var(--text-muted);
      text-transform: uppercase;
      font-weight: 600;
      display: block;
    }
    .stat-value {
      font-size: 1.4rem;
      font-weight: 700;
      font-family: monospace;
      margin-top: 0.2rem;
    }
    .stat-accent { color: var(--accent); }
    .stat-amber { color: var(--amber); }
    .stat-emerald { color: var(--emerald); }
    .section-title {
      font-size: 1.15rem;
      font-weight: 700;
      margin-top: 2rem;
      margin-bottom: 0.75rem;
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
    .section-desc {
      font-size: 0.8rem;
      color: var(--text-secondary);
      margin-bottom: 1rem;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.8rem;
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 6px;
      overflow: hidden;
      margin-bottom: 2rem;
    }
    th, td {
      padding: 0.6rem 0.8rem;
      border-bottom: 1px solid var(--border);
      text-align: left;
    }
    th {
      background: var(--surface-raised);
      font-weight: 600;
      color: var(--text-secondary);
      font-size: 0.75rem;
      text-transform: uppercase;
    }
    td code { font-family: monospace; font-size: 0.75rem; }
    .text-center { text-align: center; }
    .tag {
      font-size: 0.7rem;
      padding: 0.15rem 0.4rem;
      border-radius: 4px;
      background: var(--surface-raised);
      font-family: monospace;
    }
    .warn { color: var(--amber); font-weight: 600; }
    .dossier-card {
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 8px;
      padding: 1.25rem;
      margin-bottom: 1.5rem;
      page-break-inside: avoid;
    }
    .card-header {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      align-items: flex-start;
      gap: 0.5rem;
      border-bottom: 1px solid var(--border);
      padding-bottom: 0.75rem;
      margin-bottom: 0.75rem;
    }
    .card-title-row {
      display: flex;
      align-items: baseline;
      gap: 0.5rem;
    }
    .card-index {
      font-family: monospace;
      font-size: 0.8rem;
      color: var(--accent);
      font-weight: 700;
    }
    .card-title {
      font-size: 1rem;
      font-weight: 700;
    }
    .card-badges {
      display: flex;
      flex-wrap: wrap;
      gap: 0.35rem;
    }
    .badge {
      font-size: 0.65rem;
      font-weight: 600;
      padding: 0.15rem 0.4rem;
      border-radius: 4px;
      border: 1px solid;
    }
    .badge-category { background: var(--accent-dim); color: var(--accent); border-color: var(--accent); }
    .badge-status { background: var(--surface-raised); color: var(--text-secondary); border-color: var(--border); }
    .badge-outcome { background: var(--emerald-dim); color: var(--emerald); border-color: var(--emerald); }
    .badge-provenance { background: var(--surface-raised); color: var(--text-muted); border-color: var(--border); }
    .badge-uncertainty { background: var(--amber-dim); color: var(--amber); border-color: var(--amber); font-weight: 700; }
    .badge-reopened { background: var(--purple-dim); color: var(--purple); border-color: var(--purple); font-weight: 700; }
    .badge-overdue { background: var(--rose-dim); color: var(--rose); border-color: var(--rose); font-weight: 700; }
    .badge-drift { background: var(--amber-dim); color: var(--amber); border-color: var(--amber); }
    .identity-box {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem;
      background: var(--surface-raised);
      padding: 0.5rem 0.75rem;
      border-radius: 6px;
      font-size: 0.75rem;
      margin-bottom: 0.5rem;
    }
    .identity-item { display: flex; gap: 0.25rem; align-items: baseline; }
    .identity-item .label { color: var(--text-muted); }
    .lineage-warning { color: var(--amber); font-weight: 600; }
    .version-strip {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem;
      background: rgba(56, 189, 248, 0.05);
      border: 1px solid rgba(56, 189, 248, 0.2);
      padding: 0.4rem 0.75rem;
      border-radius: 6px;
      font-size: 0.7rem;
      font-family: monospace;
      margin-bottom: 0.75rem;
    }
    .version-item { display: flex; gap: 0.35rem; align-items: baseline; }
    .v-label { color: var(--text-muted); text-transform: uppercase; font-weight: 600; }
    .v-state { color: var(--accent); font-weight: 700; }
    .content-section { margin-top: 0.75rem; }
    .section-subheading {
      font-size: 0.7rem;
      text-transform: uppercase;
      font-weight: 700;
      color: var(--text-muted);
      margin-bottom: 0.2rem;
      font-family: monospace;
    }
    .section-text {
      font-size: 0.85rem;
      color: var(--text-primary);
      padding-left: 0.5rem;
      border-left: 2px solid var(--border);
    }
    .question-text { border-left-color: var(--accent); }
    .next-step-text { border-left-color: var(--emerald); }
    .provenance-details {
      background: var(--surface-raised);
      padding: 0.5rem 0.75rem;
      border-radius: 6px;
      font-size: 0.75rem;
      border: 1px solid var(--border);
    }
    .prov-meta { margin-top: 0.25rem; color: var(--text-muted); font-family: monospace; font-size: 0.7rem; }
    .prov-drift { color: var(--amber); font-weight: 600; }
    .outcome-section {
      background: var(--surface-raised);
      padding: 0.6rem 0.75rem;
      border-radius: 6px;
      border-left: 3px solid var(--emerald);
    }
    .outcome-classification { font-size: 0.75rem; margin-bottom: 0.25rem; }
    .outcome-note-box { font-size: 0.85rem; color: var(--text-primary); line-height: 1.4; }
    .uncertainty-section {
      background: var(--amber-dim);
      border: 1px solid var(--amber);
      padding: 0.6rem 0.75rem;
      border-radius: 6px;
    }
    .uncertainty-heading { color: var(--amber); }
    .uncertainty-box { font-size: 0.85rem; color: var(--text-primary); }
    .reopened-section {
      background: var(--purple-dim);
      border: 1px solid var(--purple);
      padding: 0.6rem 0.75rem;
      border-radius: 6px;
    }
    .reopened-heading { color: var(--purple); }
    .reopened-box { font-size: 0.8rem; color: var(--text-primary); }
    .prior-note { margin-top: 0.25rem; padding-left: 0.5rem; border-left: 2px solid var(--purple); font-style: italic; }
    .action-card {
      background: var(--surface);
      border: 1px solid var(--border);
      border-left: 3px solid var(--accent);
      border-radius: 6px;
      padding: 0.75rem;
      margin-bottom: 0.5rem;
      font-size: 0.8rem;
    }
    .action-overdue { border-left-color: var(--rose); background: rgba(248, 113, 113, 0.05); }
    .action-card-header {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 0.5rem;
      margin-bottom: 0.35rem;
    }
    .action-num { font-family: monospace; color: var(--accent); font-weight: 700; }
    .action-fund { color: var(--text-secondary); font-size: 0.75rem; }
    .action-body { font-size: 0.75rem; color: var(--text-primary); }
    .action-body .label { color: var(--text-muted); font-size: 0.7rem; text-transform: uppercase; }
    .reopened-card {
      background: var(--surface);
      border: 1px solid var(--purple);
      border-radius: 6px;
      padding: 0.75rem;
      margin-bottom: 0.5rem;
      font-size: 0.8rem;
    }
    .reopened-header { display: flex; flex-wrap: wrap; align-items: baseline; gap: 0.5rem; margin-bottom: 0.35rem; }
    .reopened-body { font-size: 0.75rem; color: var(--text-primary); }
    .empty-note { font-style: italic; color: var(--text-muted); font-size: 0.8rem; margin-bottom: 1rem; }
    footer {
      border-top: 2px solid var(--border);
      padding-top: 1.5rem;
      margin-top: 3rem;
      font-size: 0.75rem;
      color: var(--text-muted);
      line-height: 1.5;
    }
    footer h4 { color: var(--text-secondary); margin-bottom: 0.5rem; font-size: 0.8rem; }
    @media print {
      body { background: #ffffff; color: #111827; padding: 0; }
      .container { max-width: 100%; }
      .dossier-card { border: 1px solid #d1d5db; break-inside: avoid; }
    }
  </style>
</head>
<body>
  <div class="container">
    <header>
      <span class="brand-eyebrow">YUKIRA Quantitative Intelligence Platform</span>
      <h1>${escapeHtml(metadata.title)}</h1>
      <div class="meta-bar">
        <span>Generated: <strong>${escapeHtml(metadata.generatedAt)}</strong></span>
        <span> • </span>
        <span>Scope: <strong>${metadata.totalRecords} Records</strong> across <strong>${metadata.totalFundOptions} Share Classes</strong></span>
      </div>
      ${metadata.authorNotes ? `
      <div class="author-notes-card">
        <h3>Investor Review &amp; Audit Notes:</h3>
        <p>${escapeHtml(metadata.authorNotes)}</p>
      </div>
      ` : ""}
    </header>

    <div class="disclaimer-banner">
      <strong>⚠️ Evidentiary &amp; Regulatory Notice:</strong>
      ${escapeHtml(epistemicDisclaimer)}
    </div>

    <section>
      <h2 class="section-title">📊 Executive Dossier Scope</h2>
      <div class="stats-grid">
        <div class="stat-card">
          <span class="stat-label">Total Records</span>
          <div class="stat-value stat-accent">${metadata.totalRecords}</div>
        </div>
        <div class="stat-card">
          <span class="stat-label">Fund Share Classes</span>
          <div class="stat-value">${metadata.totalFundOptions}</div>
        </div>
        <div class="stat-card">
          <span class="stat-label">Action Required</span>
          <div class="stat-value stat-amber">${continuitySummary.openCount + continuitySummary.inProgressCount}</div>
        </div>
        <div class="stat-card">
          <span class="stat-label">Waiting Dependencies</span>
          <div class="stat-value">${continuitySummary.waitingCount}</div>
        </div>
        <div class="stat-card">
          <span class="stat-label">Residual Uncertainty</span>
          <div class="stat-value stat-amber">${metadata.residualUncertaintyCount}</div>
        </div>
        <div class="stat-card">
          <span class="stat-label">Cutoff Drift Detected</span>
          <div class="stat-value stat-amber">${continuitySummary.evidenceDriftCount}</div>
        </div>
      </div>
    </section>

    <!-- Phase F22: Investigation Continuity & Action Roadmap -->
    <section>
      <h2 class="section-title">🧭 Investigation Continuity &amp; Action Roadmap</h2>
      <p class="section-desc">
        Connects historical diligence findings to active follow-up steps without confusing past evidence with current market conditions.
      </p>

      <h3 style="font-size: 0.9rem; margin-top: 1rem; margin-bottom: 0.5rem; color: var(--accent);">
        1. Outstanding Investigation Actions (${continuitySummary.nextActionItems.length})
      </h3>
      ${nextActionsHtml}

      ${continuitySummary.reopenedItems.length > 0 ? `
      <h3 style="font-size: 0.9rem; margin-top: 1.25rem; margin-bottom: 0.5rem; color: var(--purple);">
        2. Reopened Inquiries Requiring Renewed Diligence (${continuitySummary.reopenedItems.length})
      </h3>
      ${reopenedHtml}
      ` : ''}
    </section>

    <section>
      <h2 class="section-title">📑 Fund Share Classes in Scope</h2>
      <table>
        <thead>
          <tr>
            <th>Scheme Name</th>
            <th>Option ID</th>
            <th>Plan</th>
            <th>Option</th>
            <th>AMFI</th>
            <th>ISIN</th>
            <th class="text-center">Inquiries</th>
            <th class="text-center">Unresolved</th>
            <th class="text-center">Uncertainty</th>
            <th>Active Cutoff</th>
          </tr>
        </thead>
        <tbody>
          ${fundRows}
        </tbody>
      </table>
    </section>

    <section>
      <h2 class="section-title">🔍 Detailed Investigation Records &amp; Evidentiary Lineage</h2>
      ${htmlItems}
    </section>

    <footer>
      <h4>Methodological &amp; Epistemic Governance Constraints</h4>
      <p>1. <strong>Deterministic Math:</strong> All quantitative metrics in YUKIRA derive from pure mathematical algorithms executed under fixed point-in-time constraints. Financial calculations are never approximated or performed by AI.</p>
      <p>2. <strong>Point-in-Time Integrity:</strong> Historical evaluations adhere to strict knowledge cutoffs (such as the canonical pilot cutoff 2024-01-15). Historical records are never backfilled with subsequent observations.</p>
      <p>3. <strong>Investor Lineage:</strong> Findings reflect investor-authored diligence notes and do not imply formal endorsement, rating, or suitability approval.</p>
      <p>4. <strong>Investigation Continuity:</strong> Reopening an inquiry or reviewing an older dossier does not reconstruct historical state. Reassessment must be conducted against authoritative current calculation runs.</p>
      <p style="margin-top: 0.5rem; font-family: monospace;">Generated by YUKIRA Quantitative Intelligence Platform — ${escapeHtml(metadata.generatedAt)}</p>
    </footer>
  </div>
</body>
</html>`;
}

/**
 * Triggers a browser file download for generated text/HTML/Markdown artifacts.
 */
export function downloadDossierFile(content: string, filename: string, mimeType: string): void {
  if (typeof window === "undefined") return;
  const blob = new Blob([content], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  URL.revokeObjectURL(url);
}
