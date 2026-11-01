"use client";

import React, { useState, useEffect, useMemo } from "react";
import { WatchlistResponse } from "@/lib/api/investor";
import {
  ResearchFollowUpIssue,
  FollowUpCategory,
  FollowUpStatus,
  FollowUpOutcomeType,
  FindingProvenanceSummary,
  FOLLOW_UP_CATEGORIES_CONFIG,
  FOLLOW_UP_STATUS_CONFIG,
  FOLLOW_UP_OUTCOMES_CONFIG,
  saveFollowUpIssue,
  deleteFollowUpIssue,
  getFundFollowUpIssues,
  evaluateFindingEvidenceTraceability,
  evaluateInvestigationClosureState,
  evaluateInvestigationClosureGuidance,
  InvestigationClosureGuidance,
  InvestigationClosureState,
  ReassessmentDispositionType,
  ReassessmentRecord,
  REASSESSMENT_DISPOSITION_CONFIG,
  InvestigationTimelineEvent,
  buildInvestigationTimeline,
  evaluateReassessmentState,
  ReassessmentStateEvaluation,
  InvestigationDependency,
  DependencyCategory,
  DependencyStatus,
  DEPENDENCY_CATEGORY_CONFIG,
  DEPENDENCY_STATUS_CONFIG,
  IssueDependencyEvaluation,
  evaluateIssueDependencies,
  recordInvestigationDependency,
  resolveInvestigationDependency,
  discontinueInvestigationDependency,
} from "@/lib/utils/researchFollowUp";
import { ReviewItemKey, CHECKLIST_ITEMS_CONFIG } from "@/lib/utils/researchReviewChecklist";
import { getBriefSectionTitle } from "@/lib/utils/researchSession";
import { EvidenceChangeSummary } from "@/lib/utils/evidenceChange";

export interface ResearchFollowUpModalProps {
  isOpen: boolean;
  onClose: () => void;
  fund: WatchlistResponse | null;
  issue?: ResearchFollowUpIssue | null;
  prefill?: {
    category?: FollowUpCategory;
    title?: string;
    briefSection?: "A" | "B" | "C" | "D" | "E" | "F" | "G" | "H";
    checklistKey?: ReviewItemKey;
    questionOrReason?: string;
  };
  evidenceContext?: {
    calculationRunId?: number | null;
    asOfDate?: string | null;
  };
  evidenceSummary?: EvidenceChangeSummary | null;
  userId?: string | null;
  initialReassessmentMode?: boolean;
  initialShowTimeline?: boolean;
  initialDependencyId?: string | null;
  onSaved: (savedIssue: ResearchFollowUpIssue) => void;
  onDeleted?: (issueId: string) => void;
  onNavigateToBrief?: (issue: ResearchFollowUpIssue) => void;
}

export function ResearchFollowUpModal({
  isOpen,
  onClose,
  fund,
  issue,
  prefill,
  evidenceContext,
  evidenceSummary,
  userId,
  initialReassessmentMode,
  initialShowTimeline,
  initialDependencyId,
  onSaved,
  onDeleted,
  onNavigateToBrief,
}: ResearchFollowUpModalProps) {
  const [activeIssue, setActiveIssue] = useState<ResearchFollowUpIssue | null>(issue || null);
  const [title, setTitle] = useState("");
  const [category, setCategory] = useState<FollowUpCategory>("EVIDENCE_LIMITATION");
  const [briefSection, setBriefSection] = useState<"A" | "B" | "C" | "D" | "E" | "F" | "G" | "H">("B");
  const [questionOrReason, setQuestionOrReason] = useState("");
  const [nextAction, setNextAction] = useState("");
  const [targetDate, setTargetDate] = useState("");
  const [status, setStatus] = useState<FollowUpStatus>("OPEN");
  const [outcomeType, setOutcomeType] = useState<FollowUpOutcomeType>("ANSWERED");
  const [resolutionNote, setResolutionNote] = useState("");
  const [residualUncertainty, setResidualUncertainty] = useState("");
  const [hasResidualUncertainty, setHasResidualUncertainty] = useState<boolean>(false);
  const [reopenReason, setReopenReason] = useState("");
  const [closureLimitationRationale, setClosureLimitationRationale] = useState("");
  const [isTimelineExpanded, setIsTimelineExpanded] = useState<boolean>(initialShowTimeline || false);
  const [isReassessmentMode, setIsReassessmentMode] = useState<boolean>(initialReassessmentMode || false);
  const [reassessmentDisposition, setReassessmentDisposition] = useState<ReassessmentDispositionType>("PRIOR_OUTCOME_CONFIRMED");
  const [reassessmentRationale, setReassessmentRationale] = useState<string>("");
  const [revisedOutcomeType, setRevisedOutcomeType] = useState<FollowUpOutcomeType>("ANSWERED");
  const [error, setError] = useState<string | null>(null);

  // Phase F29: Investigation Dependencies & Blocker Continuity States
  const [resolvingDepId, setResolvingDepId] = useState<string | null>(initialDependencyId || null);
  const [resolutionReceivedInfo, setResolutionReceivedInfo] = useState<string>("");
  const [resolutionRationaleText, setResolutionRationaleText] = useState<string>("");
  const [resolutionEvidenceRef, setResolutionEvidenceRef] = useState<string>("");
  const [resolutionNextAction, setResolutionNextAction] = useState<"RESUME_IN_PROGRESS" | "CONTINUE_WAITING" | "KEEP_CURRENT_STATUS">("RESUME_IN_PROGRESS");

  const [isAddingDependency, setIsAddingDependency] = useState<boolean>(false);
  const [newDepDescription, setNewDepDescription] = useState<string>("");
  const [newDepCategory, setNewDepCategory] = useState<DependencyCategory>("DISCLOSURE_OR_FILING");
  const [newDepReason, setNewDepReason] = useState<string>("");
  const [newDepExpectedSource, setNewDepExpectedSource] = useState<string>("");
  const [newDepEvidenceRef, setNewDepEvidenceRef] = useState<string>("");
  const [newDepSetWaiting, setNewDepSetWaiting] = useState<boolean>(true);

  // Load existing issues for this fund share class to prevent accidental duplicate inquiries
  const existingIssuesForFund = useMemo(() => {
    if (!fund || !isOpen) return [];
    return getFundFollowUpIssues(fund.schemeId, fund.schemeOptionId, userId);
  }, [fund, isOpen, userId]);

  const matchingSectionIssues = useMemo(() => {
    if (!existingIssuesForFund.length || activeIssue) return [];
    return existingIssuesForFund.filter(
      (i) =>
        (i.status === "OPEN" || i.status === "IN_PROGRESS" || i.status === "WAITING") &&
        (i.briefSection === briefSection || (prefill?.checklistKey && i.checklistKey === prefill.checklistKey))
    );
  }, [existingIssuesForFund, briefSection, prefill?.checklistKey, activeIssue]);

  useEffect(() => {
    setActiveIssue(issue || null);
    if (issue) {
      setTitle(issue.title || "");
      setCategory(issue.category || "EVIDENCE_LIMITATION");
      setBriefSection(issue.briefSection || "B");
      setQuestionOrReason(issue.questionOrReason || "");
      setNextAction(issue.nextAction || "");
      setTargetDate(issue.targetDate || "");
      setStatus(issue.status || "OPEN");
      setOutcomeType(issue.outcomeType || (issue.status === "DISMISSED" ? "DISMISSED" : "ANSWERED"));
      setResolutionNote(issue.resolutionNote || "");
      setResidualUncertainty(issue.residualUncertainty || "");
      setHasResidualUncertainty(
        issue.hasResidualUncertainty ??
          Boolean(
            (issue.residualUncertainty && issue.residualUncertainty.trim().length > 0) ||
            issue.outcomeType === "PARTIALLY_ANSWERED" ||
            issue.outcomeType === "DATA_UNAVAILABLE"
          )
      );
      setReopenReason(issue.reopenReason || "");
      setClosureLimitationRationale(issue.closureLimitationRationale || "");
      setIsTimelineExpanded(initialShowTimeline || false);
      setIsReassessmentMode(initialReassessmentMode || false);
      setReassessmentDisposition(issue.reassessmentDisposition || "PRIOR_OUTCOME_CONFIRMED");
      setReassessmentRationale(issue.reassessmentRationale || "");
      setRevisedOutcomeType(issue.outcomeType || "ANSWERED");
      setResolvingDepId(initialDependencyId || null);
      setError(null);
    } else if (fund) {
      setTitle(prefill?.title || "");
      setCategory(prefill?.category || "EVIDENCE_LIMITATION");
      setBriefSection(prefill?.briefSection || "B");
      setQuestionOrReason(prefill?.questionOrReason || "");
      setNextAction("");
      setTargetDate("");
      setStatus("OPEN");
      setOutcomeType("ANSWERED");
      setResolutionNote("");
      setResidualUncertainty("");
      setHasResidualUncertainty(false);
      setReopenReason("");
      setClosureLimitationRationale("");
      setIsTimelineExpanded(false);
      setIsReassessmentMode(false);
      setReassessmentDisposition("PRIOR_OUTCOME_CONFIRMED");
      setReassessmentRationale("");
      setRevisedOutcomeType("ANSWERED");
      setResolvingDepId(null);
      setError(null);
    }
  }, [issue, fund, prefill, isOpen, initialShowTimeline, initialReassessmentMode, initialDependencyId]);

  const isResolvingOrDismissing = status === "RESOLVED" || status === "DISMISSED";
  const effectiveIssue = activeIssue || issue;
  const wasClosedInitially = effectiveIssue?.status === "RESOLVED" || effectiveIssue?.status === "DISMISSED";
  const isNowActive = status === "OPEN" || status === "IN_PROGRESS" || status === "WAITING";
  const isReopeningNow = wasClosedInitially && isNowActive;

  // Phase F19: Compute evidentiary provenance and traceability status
  const provenanceSummary: FindingProvenanceSummary | null = useMemo(() => {
    if (!effectiveIssue) return null;
    return evaluateFindingEvidenceTraceability(
      effectiveIssue,
      evidenceContext?.calculationRunId,
      evidenceContext?.asOfDate,
      evidenceSummary?.changes?.length
    );
  }, [effectiveIssue, evidenceContext, evidenceSummary]);

  // Phase F24: Evidence-Aware Closure Guidance & Readiness Evaluation
  const closureGuidance: InvestigationClosureGuidance | null = useMemo(() => {
    if (!fund) return null;
    const baseIssue: ResearchFollowUpIssue = effectiveIssue || {
      id: "preview-temp",
      schemeId: fund.schemeId,
      schemeOptionId: fund.schemeOptionId,
      schemeName: fund.schemeName,
      title: title.trim(),
      category,
      status,
      questionOrReason: questionOrReason.trim(),
      nextAction: nextAction.trim(),
      outcomeType: status === "DISMISSED" ? "DISMISSED" : outcomeType,
      hasResidualUncertainty,
      residualUncertainty: residualUncertainty.trim() || null,
      closureLimitationRationale: closureLimitationRationale.trim() || null,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };

    const previewIssue: ResearchFollowUpIssue = {
      ...baseIssue,
      status,
      outcomeType: status === "DISMISSED" ? "DISMISSED" : outcomeType,
      nextAction: nextAction.trim(),
      hasResidualUncertainty: status === "RESOLVED" ? Boolean(hasResidualUncertainty) : false,
      residualUncertainty: residualUncertainty.trim() || null,
      closureLimitationRationale: closureLimitationRationale.trim() || null,
    };

    return evaluateInvestigationClosureGuidance(
      previewIssue,
      provenanceSummary,
      nextAction.trim()
    );
  }, [
    effectiveIssue,
    fund,
    title,
    category,
    status,
    outcomeType,
    nextAction,
    hasResidualUncertainty,
    residualUncertainty,
    closureLimitationRationale,
    provenanceSummary,
  ]);

  // Phase F28: Evidence-Aware Reassessment & Three-Concept Evaluation
  const reassessmentEval: ReassessmentStateEvaluation | null = useMemo(() => {
    if (!effectiveIssue) return null;
    return evaluateReassessmentState(effectiveIssue, provenanceSummary);
  }, [effectiveIssue, provenanceSummary]);

  // Phase F28: Compact Investigation Timeline Event Builder
  const timelineEvents: InvestigationTimelineEvent[] = useMemo(() => {
    if (!effectiveIssue) return [];
    return buildInvestigationTimeline(effectiveIssue, provenanceSummary);
  }, [effectiveIssue, provenanceSummary]);

  // Phase F29: Evaluate structured issue dependencies & multi-blocker blocking state
  const dependencyEval: IssueDependencyEvaluation | null = useMemo(() => {
    if (!effectiveIssue) return null;
    return evaluateIssueDependencies(effectiveIssue);
  }, [effectiveIssue]);

  // Phase F29: Handler to add a structured prerequisite to an existing issue
  const handleAddDependency = (e: React.FormEvent) => {
    e.preventDefault();
    if (!effectiveIssue || !fund) return;
    if (!newDepDescription.trim()) {
      setError("Please describe what missing information, disclosure, or prerequisite is blocking this investigation.");
      return;
    }
    if (!newDepReason.trim()) {
      setError("Please document why this investigation cannot proceed without this prerequisite.");
      return;
    }

    const updated = recordInvestigationDependency(
      effectiveIssue.id,
      {
        description: newDepDescription.trim(),
        category: newDepCategory,
        reasonWhyBlocked: newDepReason.trim(),
        expectedSource: newDepExpectedSource.trim() || null,
        evidenceReference: newDepEvidenceRef.trim() || null,
        evidenceRunId: provenanceSummary?.currentRunId ?? null,
        evidenceAsOfDate: provenanceSummary?.currentAsOfDate ?? null,
        setIssueWaiting: newDepSetWaiting,
      },
      userId
    );

    if (updated) {
      setActiveIssue(updated);
      if (newDepSetWaiting) {
        setStatus("WAITING");
      }
      onSaved(updated);
      setIsAddingDependency(false);
      setNewDepDescription("");
      setNewDepReason("");
      setNewDepExpectedSource("");
      setNewDepEvidenceRef("");
      setError(null);
    }
  };

  // Phase F29: Handler to confirm blocker resolution with empirical info received
  const handleConfirmResolveDependency = (depId: string) => {
    if (!effectiveIssue) return;
    if (!resolutionReceivedInfo.trim()) {
      setError("Please document what information, disclosure, or prerequisite was received.");
      return;
    }

    const updated = resolveInvestigationDependency({
      issueId: effectiveIssue.id,
      dependencyId: depId,
      receivedInfoOrChange: resolutionReceivedInfo.trim(),
      resolutionRationale: resolutionRationaleText.trim() || null,
      resolutionEvidenceReference: resolutionEvidenceRef.trim() || null,
      resolutionRunId: provenanceSummary?.currentRunId ?? null,
      resolutionAsOfDate: provenanceSummary?.currentAsOfDate ?? null,
      nextIssueAction: resolutionNextAction,
      userId,
    });

    if (updated) {
      setActiveIssue(updated);
      setStatus(updated.status);
      onSaved(updated);
      setResolvingDepId(null);
      setResolutionReceivedInfo("");
      setResolutionRationaleText("");
      setResolutionEvidenceRef("");
      setResolutionNextAction("RESUME_IN_PROGRESS");
      setError(null);
    }
  };

  // Phase F29: Handler to discontinue prerequisite
  const handleDiscontinueDependency = (depId: string) => {
    if (!effectiveIssue) return;
    const reason = window.prompt("Reason for discontinuing this prerequisite (optional):") || "";
    const updated = discontinueInvestigationDependency(effectiveIssue.id, depId, reason, userId);
    if (updated) {
      setActiveIssue(updated);
      setStatus(updated.status);
      onSaved(updated);
      setError(null);
    }
  };

  // Phase F28: Handler to record explicit reassessment disposition without erasing history
  const handleRecordReassessment = (e: React.FormEvent) => {
    e.preventDefault();
    if (!effectiveIssue || !fund) return;
    if (!reassessmentRationale.trim()) {
      setError("Please provide an explicit rationale for this reassessment disposition.");
      return;
    }

    const newRecord: ReassessmentRecord = {
      id: `reassess-${Date.now()}-${Math.random().toString(36).substring(2, 6)}`,
      reassessedAt: new Date().toISOString(),
      disposition: reassessmentDisposition,
      rationale: reassessmentRationale.trim(),
      priorOutcomeType: effectiveIssue.outcomeType || effectiveIssue.priorOutcomeType || null,
      priorResolutionNote: effectiveIssue.resolutionNote || effectiveIssue.priorResolutionNote || null,
      priorEvidenceRunId: effectiveIssue.evidenceRunId || effectiveIssue.priorEvidenceRunId || null,
      priorEvidenceAsOfDate: effectiveIssue.evidenceAsOfDate || effectiveIssue.priorEvidenceAsOfDate || null,
      evidenceRunId: provenanceSummary?.currentRunId || null,
      evidenceAsOfDate: provenanceSummary?.currentAsOfDate || null,
      revisedOutcomeType: reassessmentDisposition === "OUTCOME_REVISED" ? revisedOutcomeType : null,
    };

    const updatedHistory = [...(effectiveIssue.reassessmentHistory || []), newRecord];

    let newStatus = effectiveIssue.status;
    let newOutcomeType = effectiveIssue.outcomeType;
    let newResolutionNote = effectiveIssue.resolutionNote;
    let newEvidenceRunId = effectiveIssue.evidenceRunId;
    let newEvidenceAsOfDate = effectiveIssue.evidenceAsOfDate;

    if (reassessmentDisposition === "PRIOR_OUTCOME_CONFIRMED") {
      newStatus = effectiveIssue.status === "OPEN" || effectiveIssue.status === "IN_PROGRESS" || effectiveIssue.status === "WAITING" ? "RESOLVED" : effectiveIssue.status;
      if (provenanceSummary?.currentRunId) {
        newEvidenceRunId = provenanceSummary.currentRunId;
      }
      if (provenanceSummary?.currentAsOfDate) {
        newEvidenceAsOfDate = provenanceSummary.currentAsOfDate;
      }
    } else if (reassessmentDisposition === "OUTCOME_REVISED") {
      newStatus = revisedOutcomeType === "DISMISSED" ? "DISMISSED" : "RESOLVED";
      newOutcomeType = revisedOutcomeType;
      newResolutionNote = reassessmentRationale.trim();
      if (provenanceSummary?.currentRunId) {
        newEvidenceRunId = provenanceSummary.currentRunId;
      }
      if (provenanceSummary?.currentAsOfDate) {
        newEvidenceAsOfDate = provenanceSummary.currentAsOfDate;
      }
    } else if (reassessmentDisposition === "EVIDENCE_INSUFFICIENT") {
      newStatus = "IN_PROGRESS";
    } else if (reassessmentDisposition === "DEFERRED_DEPENDENCY") {
      newStatus = "WAITING";
    }

    const updatedIssue: ResearchFollowUpIssue = {
      ...effectiveIssue,
      status: newStatus,
      outcomeType: newOutcomeType,
      resolutionNote: newResolutionNote,
      evidenceRunId: newEvidenceRunId,
      evidenceAsOfDate: newEvidenceAsOfDate,
      priorOutcomeType: effectiveIssue.outcomeType || effectiveIssue.priorOutcomeType || null,
      priorResolutionNote: effectiveIssue.resolutionNote || effectiveIssue.priorResolutionNote || null,
      priorEvidenceRunId: effectiveIssue.evidenceRunId || effectiveIssue.priorEvidenceRunId || null,
      priorEvidenceAsOfDate: effectiveIssue.evidenceAsOfDate || effectiveIssue.priorEvidenceAsOfDate || null,
      priorResolvedAt: effectiveIssue.resolvedAt || effectiveIssue.priorResolvedAt || null,
      reassessmentDisposition,
      reassessmentRationale: reassessmentRationale.trim(),
      reassessedAt: newRecord.reassessedAt,
      reassessmentHistory: updatedHistory,
      updatedAt: new Date().toISOString(),
    };

    saveFollowUpIssue(updatedIssue, userId);
    setActiveIssue(updatedIssue);
    onSaved(updatedIssue);
    setIsReassessmentMode(false);
    setReassessmentRationale("");
    setError(null);
  };

  if (!isOpen || !fund) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) {
      setError("Please provide a concise issue title.");
      return;
    }
    if (!questionOrReason.trim()) {
      setError("Please document the question or reason requiring follow-up.");
      return;
    }
    if (!nextAction.trim()) {
      setError("Please specify the next investigation action.");
      return;
    }
    if (isReopeningNow && !reopenReason.trim()) {
      setError("Please document the rationale or reason for reopening this investigation.");
      return;
    }
    if (isResolvingOrDismissing && !resolutionNote.trim()) {
      setError(
        status === "RESOLVED"
          ? "Please record an empirical outcome or resolution note before marking this issue resolved."
          : "Please document the reason for dismissing this follow-up inquiry."
      );
      return;
    }
    if (status === "RESOLVED" && hasResidualUncertainty && !residualUncertainty.trim()) {
      setError("Please document the specific residual uncertainty or missing disclosures that remain unresolved.");
      return;
    }
    if (
      isResolvingOrDismissing &&
      closureGuidance &&
      closureGuidance.materialLimitations.length > 0 &&
      !closureLimitationRationale.trim()
    ) {
      setError(
        "Please provide an explicit explanation of why this investigation is considered sufficiently addressed despite the outstanding limitations (e.g. pending next action, dependency, or evidence drift)."
      );
      return;
    }

    const currentIssueId = effectiveIssue?.id;

    // Phase F18 & F24: When reopening a previously resolved/dismissed issue, preserve prior outcome facts
    const priorResolutionNote = isReopeningNow
      ? effectiveIssue.resolutionNote || effectiveIssue.priorResolutionNote || null
      : effectiveIssue?.priorResolutionNote || null;
    const priorResolvedAt = isReopeningNow
      ? effectiveIssue.resolvedAt || effectiveIssue.priorResolvedAt || null
      : effectiveIssue?.priorResolvedAt || null;
    const priorOutcomeType = isReopeningNow
      ? effectiveIssue.outcomeType || effectiveIssue.priorOutcomeType || null
      : effectiveIssue?.priorOutcomeType || null;
    const reopenedAt = isReopeningNow
      ? new Date().toISOString()
      : effectiveIssue?.reopenedAt || null;
    const reopenReasonVal = isReopeningNow
      ? reopenReason.trim() || null
      : effectiveIssue?.reopenReason || null;

    // Phase F19: Preserve evidentiary provenance and record current calculation run/asOfDate
    const priorEvidenceRunId = isReopeningNow
      ? effectiveIssue.evidenceRunId || effectiveIssue.priorEvidenceRunId || null
      : effectiveIssue?.priorEvidenceRunId || null;
    const priorEvidenceAsOfDate = isReopeningNow
      ? effectiveIssue.evidenceAsOfDate || effectiveIssue.priorEvidenceAsOfDate || null
      : effectiveIssue?.priorEvidenceAsOfDate || null;

    const evidenceRunId = isResolvingOrDismissing
      ? (effectiveIssue?.evidenceRunId || evidenceContext?.calculationRunId || null)
      : (isReopeningNow ? null : (effectiveIssue?.evidenceRunId || evidenceContext?.calculationRunId || null));
    const evidenceAsOfDate = isResolvingOrDismissing
      ? (effectiveIssue?.evidenceAsOfDate || evidenceContext?.asOfDate || null)
      : (isReopeningNow ? null : (effectiveIssue?.evidenceAsOfDate || evidenceContext?.asOfDate || null));

    const newIssue: ResearchFollowUpIssue = {
      id: currentIssueId || `fui-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`,
      schemeId: fund.schemeId,
      schemeOptionId: fund.schemeOptionId,
      schemeCode: fund.schemeCode,
      schemeName: fund.schemeName,
      planType: fund.planType ?? undefined,
      optionType: fund.optionType ?? undefined,
      amfiCode: fund.amfiCode,
      isin: fund.isin,
      title: title.trim(),
      category,
      briefSection,
      checklistKey: effectiveIssue?.checklistKey || prefill?.checklistKey,
      questionOrReason: questionOrReason.trim(),
      nextAction: nextAction.trim(),
      targetDate: targetDate.trim() || null,
      status,
      resolutionNote: isResolvingOrDismissing
        ? resolutionNote.trim() || null
        : (isReopeningNow ? null : effectiveIssue?.resolutionNote || null),
      outcomeType: isResolvingOrDismissing
        ? (status === "DISMISSED" ? "DISMISSED" : outcomeType)
        : (isReopeningNow ? null : effectiveIssue?.outcomeType || null),
      residualUncertainty: isResolvingOrDismissing
        ? (hasResidualUncertainty || outcomeType === "PARTIALLY_ANSWERED" || outcomeType === "DATA_UNAVAILABLE"
            ? residualUncertainty.trim() || null
            : null)
        : (isReopeningNow ? null : effectiveIssue?.residualUncertainty || null),
      hasResidualUncertainty: isResolvingOrDismissing
        ? (status === "RESOLVED" ? Boolean(hasResidualUncertainty) : false)
        : (isReopeningNow ? null : effectiveIssue?.hasResidualUncertainty ?? null),
      resolvedAt: isResolvingOrDismissing
        ? (effectiveIssue?.resolvedAt && !isReopeningNow ? effectiveIssue.resolvedAt : new Date().toISOString())
        : null,
      evidenceRunId,
      evidenceAsOfDate,
      priorResolutionNote,
      priorResolvedAt,
      priorOutcomeType,
      priorEvidenceRunId,
      priorEvidenceAsOfDate,
      reopenedAt,
      reopenReason: reopenReasonVal,
      closureLimitationRationale: isResolvingOrDismissing
        ? (closureLimitationRationale.trim() || null)
        : (isReopeningNow ? null : effectiveIssue?.closureLimitationRationale || null),
      reassessmentDisposition: effectiveIssue?.reassessmentDisposition || null,
      reassessmentRationale: effectiveIssue?.reassessmentRationale || null,
      reassessedAt: effectiveIssue?.reassessedAt || null,
      reassessmentHistory: effectiveIssue?.reassessmentHistory || [],
      dependencies: effectiveIssue?.dependencies || [],
      createdAt: effectiveIssue?.createdAt || new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };

    saveFollowUpIssue(newIssue, userId);
    onSaved(newIssue);
    onClose();
  };

  const handleDelete = () => {
    const currentIssueId = activeIssue?.id || issue?.id;
    if (!currentIssueId) return;
    deleteFollowUpIssue(currentIssueId, userId);
    if (onDeleted) {
      onDeleted(currentIssueId);
    }
    onClose();
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="follow-up-modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-5 bg-black/80 backdrop-blur-md"
    >
      <div className="panel max-w-2xl w-full bg-surface border border-border shadow-2xl rounded-xl overflow-hidden animate-in fade-in zoom-in-95 duration-150 max-h-[92vh] flex flex-col font-mono text-xs">
        {/* Header */}
        <div className="panel-header border-b border-border p-4 flex items-center justify-between shrink-0 bg-surface-raised/40">
          <div>
            <div className="flex items-center gap-2">
              <span className="text-accent text-sm">🔍</span>
              <h3 id="follow-up-modal-title" className="text-sm font-bold uppercase tracking-wider text-text-primary">
                {activeIssue ? "Edit Research Follow-Up Issue" : "Log Research Follow-Up Issue"}
              </h3>
            </div>
            <p className="text-[11.5px] text-text-secondary mt-0.5 font-sans">
              Record a specific inquiry, unexplained result, or data limitation to investigate before committing capital.
            </p>
          </div>
          <button
            onClick={onClose}
            className="btn btn-ghost btn-xs text-text-tertiary hover:text-text-primary text-base p-1"
            aria-label="Close follow-up modal"
          >
            ✕
          </button>
        </div>

        {/* Modal Body */}
        <form onSubmit={handleSubmit} className="p-4 sm:p-5 space-y-4 overflow-y-auto flex-1">
          {error && (
            <div className="p-2.5 rounded bg-rose-500/15 border border-rose-500/40 text-rose-300 text-xs">
              ⚠️ {error}
            </div>
          )}

          {/* Scheme Option Identity Pill */}
          <div className="p-2 rounded bg-surface-raised/60 border border-border/80 text-[11px] flex flex-wrap items-center gap-2 text-text-secondary">
            <span className="text-text-primary font-bold">{fund.schemeName}</span>
            <span>•</span>
            <span>Option #{fund.schemeOptionId || fund.schemeId} ({fund.planType || "DIRECT"} • {fund.optionType || "GROWTH"})</span>
            {fund.amfiCode && <span>• AMFI: {fund.amfiCode}</span>}
            {fund.isin && <span>• ISIN: {fund.isin}</span>}
          </div>

          {/* Finding Evidence Provenance & Traceability Card (Phase F19) */}
          {effectiveIssue && provenanceSummary && (
            <div className="p-3 rounded-lg bg-surface-raised/70 border border-border/80 space-y-2 text-xs font-mono">
              <div className="flex flex-wrap items-center justify-between gap-1.5 border-b border-border/50 pb-1.5">
                <div className="flex items-center gap-1.5">
                  <span className="text-accent text-[11px]">🏷️</span>
                  <span className="font-bold uppercase tracking-wider text-[11px] text-text-cream-bright">
                    Evidence Provenance &amp; Audit Traceability
                  </span>
                </div>
                <span className={`text-[9.5px] px-1.5 py-0.2 rounded border font-semibold ${provenanceSummary.statusBadgeClass}`}>
                  {provenanceSummary.statusBadgeLabel}
                </span>
              </div>

              {/* Provenance Metadata Grid */}
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-[10.5px]">
                <div className="p-1.5 rounded bg-surface border border-border/60">
                  <span className="text-text-muted block text-[9px] uppercase">Calculation Run</span>
                  <span className="font-bold text-text-primary">
                    {provenanceSummary.recordedRunId ? `Run #${provenanceSummary.recordedRunId}` : "Not recorded"}
                  </span>
                </div>
                <div className="p-1.5 rounded bg-surface border border-border/60">
                  <span className="text-text-muted block text-[9px] uppercase">Observation Cutoff</span>
                  <span className="font-bold text-text-primary">
                    {provenanceSummary.recordedAsOfDate || "Not recorded"}
                  </span>
                </div>
                <div className="p-1.5 rounded bg-surface border border-border/60">
                  <span className="text-text-muted block text-[9px] uppercase">Brief Section</span>
                  <span className="font-bold text-accent truncate block">
                    Sec {effectiveIssue.briefSection || "A"}: {getBriefSectionTitle(effectiveIssue.briefSection)}
                  </span>
                </div>
                <div className="p-1.5 rounded bg-surface border border-border/60">
                  <span className="text-text-muted block text-[9px] uppercase">Checklist Link</span>
                  <span className="font-bold text-text-primary truncate block">
                    {effectiveIssue.checklistKey
                      ? CHECKLIST_ITEMS_CONFIG.find((c) => c.key === effectiveIssue.checklistKey)?.shortLabel || effectiveIssue.checklistKey
                      : "Unlinked"}
                  </span>
                </div>
              </div>

              {/* Context Description */}
              <p className="text-[11px] text-text-secondary font-sans leading-relaxed">
                {provenanceSummary.statusDescription}
              </p>

              {/* Timestamps */}
              <div className="pt-1 border-t border-border/40 text-[10px] text-text-muted font-mono flex flex-wrap items-center justify-between gap-1.5">
                <span>Inquiry Created: {new Date(effectiveIssue.createdAt).toLocaleDateString()}</span>
                {effectiveIssue.resolvedAt && (
                  <span>Outcome Documented: {new Date(effectiveIssue.resolvedAt).toLocaleDateString()}</span>
                )}
                {effectiveIssue.reopenedAt && (
                  <span className="text-indigo-300">Reopened: {new Date(effectiveIssue.reopenedAt).toLocaleDateString()}</span>
                )}
              </div>

              {/* Prior Outcome Provenance if Reopened */}
              {provenanceSummary.hasPriorOutcome && effectiveIssue.priorResolutionNote && (
                <div className="p-2 rounded bg-indigo-950/20 border border-indigo-500/30 text-[10.5px] font-sans text-indigo-200/90 space-y-0.5">
                  <div className="flex flex-wrap items-center gap-1.5 text-[9px] font-mono text-indigo-300 uppercase font-bold">
                    <span>Preserved Prior Outcome Provenance</span>
                    {effectiveIssue.priorOutcomeType && (
                      <span className="px-1 py-0.2 rounded border border-indigo-500/40">
                        {effectiveIssue.priorOutcomeType}
                      </span>
                    )}
                    {effectiveIssue.priorResolvedAt && (
                      <span className="text-text-muted font-normal lowercase">
                        ({new Date(effectiveIssue.priorResolvedAt).toLocaleDateString()})
                      </span>
                    )}
                    {effectiveIssue.priorEvidenceRunId && (
                      <span className="text-indigo-300 font-normal">
                        • Run #{effectiveIssue.priorEvidenceRunId}
                      </span>
                    )}
                    {effectiveIssue.priorEvidenceAsOfDate && (
                      <span className="text-indigo-300 font-normal">
                        • Cutoff: {effectiveIssue.priorEvidenceAsOfDate}
                      </span>
                    )}
                  </div>
                  <p className="line-clamp-2">{effectiveIssue.priorResolutionNote}</p>
                </div>
              )}
            </div>
          )}

          {/* Phase F28: Evidence-Aware Reassessment & Three-Concept Distinction Card */}
          {effectiveIssue && reassessmentEval && (
            <div className="p-3.5 rounded-lg bg-surface-raised/80 border border-border/90 space-y-3 text-xs font-mono">
              <div className="flex flex-wrap items-center justify-between gap-1.5 border-b border-border/50 pb-2">
                <div className="flex items-center gap-1.5">
                  <span className="text-purple-400 text-sm">⚖️</span>
                  <span className="font-bold uppercase tracking-wider text-[11px] text-text-cream-bright">
                    Reassessment Continuity &amp; Evidentiary Status
                  </span>
                </div>
                <div className="flex items-center gap-1.5">
                  <span className={`text-[9.5px] px-2 py-0.5 rounded border font-semibold ${reassessmentEval.badgeClass}`}>
                    {reassessmentEval.statusLabel}
                  </span>
                </div>
              </div>

              {/* Three-Concept Architecture Grid (Section 2.B) */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-2 text-[10.5px]">
                {/* Concept 1: Previous Recorded Outcome */}
                <div className="p-2.5 rounded bg-surface border border-border/70 space-y-1">
                  <span className="text-[9px] uppercase tracking-wider font-bold text-text-muted block">
                    1. Recorded Conclusion
                  </span>
                  <div className="font-semibold text-text-primary text-[11px]">
                    {reassessmentEval.previousRecordedOutcome?.outcomeType ? (
                      <span className="text-emerald-400">
                        {FOLLOW_UP_OUTCOMES_CONFIG[reassessmentEval.previousRecordedOutcome.outcomeType]?.label || reassessmentEval.previousRecordedOutcome.outcomeType}
                      </span>
                    ) : (
                      <span className="text-text-muted italic">No prior outcome (Under investigation)</span>
                    )}
                  </div>
                  {reassessmentEval.previousRecordedOutcome?.resolutionNote && (
                    <p className="text-[10px] text-text-secondary font-sans line-clamp-2 italic">
                      &quot;{reassessmentEval.previousRecordedOutcome.resolutionNote}&quot;
                    </p>
                  )}
                  <div className="text-[9px] text-text-muted pt-1 border-t border-border/30">
                    Baseline: {reassessmentEval.previousRecordedOutcome?.evidenceRunId ? `Run #${reassessmentEval.previousRecordedOutcome.evidenceRunId}` : "Run unrecorded"}
                    {reassessmentEval.previousRecordedOutcome?.evidenceAsOfDate ? ` (${reassessmentEval.previousRecordedOutcome.evidenceAsOfDate})` : ""}
                  </div>
                </div>

                {/* Concept 2: Current Evidentiary Status */}
                <div className="p-2.5 rounded bg-surface border border-border/70 space-y-1">
                  <span className="text-[9px] uppercase tracking-wider font-bold text-text-muted block">
                    2. Current Evidence State
                  </span>
                  <div className="font-semibold text-text-primary text-[11px] flex items-center gap-1">
                    <span className={
                      reassessmentEval.currentEvidenceState.isDrifted || reassessmentEval.currentEvidenceState.isSuperseded
                        ? "text-amber-400"
                        : "text-emerald-400"
                    }>
                      {reassessmentEval.currentEvidenceState.label}
                    </span>
                  </div>
                  <p className="text-[10px] text-text-secondary font-sans leading-tight">
                    {reassessmentEval.currentEvidenceState.description}
                  </p>
                  <div className="text-[9px] text-text-muted pt-1 border-t border-border/30">
                    Active: {reassessmentEval.currentEvidenceState.currentRunId ? `Run #${reassessmentEval.currentEvidenceState.currentRunId}` : "Run unrecorded"}
                    {reassessmentEval.currentEvidenceState.currentAsOfDate ? ` (${reassessmentEval.currentEvidenceState.currentAsOfDate})` : ""}
                  </div>
                </div>

                {/* Concept 3: Reassessment Status */}
                <div className="p-2.5 rounded bg-surface border border-border/70 space-y-1">
                  <span className="text-[9px] uppercase tracking-wider font-bold text-text-muted block">
                    3. Reassessment Review
                  </span>
                  <div className="font-semibold text-text-primary text-[11px]">
                    {reassessmentEval.status === "REASSESSMENT_RECOMMENDED" ? (
                      <span className="text-amber-300 font-bold">⚡ Reassessment Recommended</span>
                    ) : reassessmentEval.status === "REASSESSMENT_RECORDED" ? (
                      <span className="text-emerald-400">✓ Reassessment Recorded</span>
                    ) : reassessmentEval.status === "EVIDENCE_INSUFFICIENT" ? (
                      <span className="text-rose-300">⚠️ Evidence Insufficient</span>
                    ) : reassessmentEval.status === "DEFERRED_DEPENDENCY" ? (
                      <span className="text-blue-300">⏳ Deferred Dependency</span>
                    ) : (
                      <span className="text-text-muted">{reassessmentEval.statusLabel}</span>
                    )}
                  </div>
                  <p className="text-[10px] text-text-secondary font-sans leading-tight">
                    {reassessmentEval.explanation}
                  </p>
                  {reassessmentEval.reassessmentStatus.hasReassessmentRecorded && reassessmentEval.reassessmentStatus.latestReassessedAt && (
                    <div className="text-[9px] text-text-muted pt-1 border-t border-border/30">
                      Reviewed: {new Date(reassessmentEval.reassessmentStatus.latestReassessedAt).toLocaleDateString()}
                    </div>
                  )}
                </div>
              </div>

              {/* Interactive Controls: Toggle Timeline & Start Reassessment */}
              <div className="flex flex-wrap items-center justify-between gap-2 pt-1 border-t border-border/40">
                <button
                  type="button"
                  onClick={() => setIsTimelineExpanded((v) => !v)}
                  className="btn btn-ghost btn-xs text-[10.5px] font-mono text-text-secondary hover:text-text-primary flex items-center gap-1.5"
                >
                  <span>{isTimelineExpanded ? "▼ Hide Timeline" : "▶ Show Timeline"}</span>
                  <span className="bg-surface-raised px-1.5 py-0.2 rounded border border-border text-[9.5px]">
                    {timelineEvents.length} milestone{timelineEvents.length === 1 ? "" : "s"}
                  </span>
                </button>

                {!isReassessmentMode ? (
                  <button
                    type="button"
                    onClick={() => setIsReassessmentMode(true)}
                    className="btn btn-secondary btn-xs text-[10.5px] font-mono text-purple-300 border-purple-500/40 hover:bg-purple-950/30 flex items-center gap-1"
                    title="Record explicit review of changed evidence without overwriting historical outcome"
                  >
                    <span>⚡ Record Reassessment Disposition</span>
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={() => setIsReassessmentMode(false)}
                    className="btn btn-ghost btn-xs text-[10.5px] font-mono text-text-muted hover:text-text-primary"
                  >
                    ✕ Cancel Reassessment
                  </button>
                )}
              </div>

              {/* Reassessment Disposition Form (Section 2.D) */}
              {isReassessmentMode && (
                <div className="p-3 rounded-lg bg-purple-950/20 border border-purple-500/40 space-y-3 mt-2">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-1.5">
                      <span className="text-purple-300 font-bold uppercase text-[10.5px]">
                        Record Reassessment Disposition
                      </span>
                      <span className="text-[9px] px-1 py-0.2 rounded bg-purple-500/20 text-purple-200 border border-purple-500/30">
                        Preserves History
                      </span>
                    </div>
                    <span className="text-[10px] text-text-muted">
                      Does not erase prior conclusions
                    </span>
                  </div>

                  <p className="text-[10.5px] text-text-secondary font-sans leading-relaxed">
                    Select the evidence review outcome. Prior conclusions, timestamps, and baseline calculation runs are preserved in the inquiry audit trail.
                  </p>

                  {/* Disposition Selector */}
                  <div className="space-y-1.5">
                    <label className="text-[10px] font-bold text-text-muted uppercase block">
                      Reassessment Disposition <span className="text-rose-400">*</span>
                    </label>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-1.5">
                      {(Object.keys(REASSESSMENT_DISPOSITION_CONFIG) as ReassessmentDispositionType[]).map((dispKey) => {
                        const cfg = REASSESSMENT_DISPOSITION_CONFIG[dispKey];
                        const isSelected = reassessmentDisposition === dispKey;
                        return (
                          <button
                            key={dispKey}
                            type="button"
                            onClick={() => setReassessmentDisposition(dispKey)}
                            className={`p-2 rounded text-left border transition-all ${
                              isSelected
                                ? "bg-purple-900/30 border-purple-400 text-text-primary"
                                : "bg-surface border-border/70 text-text-secondary hover:border-border"
                            }`}
                          >
                            <div className="flex items-center justify-between mb-0.5">
                              <span className="font-bold text-[10.5px] text-text-primary">
                                {cfg.label}
                              </span>
                              <span className={`text-[8.5px] px-1 py-0.2 rounded border font-mono ${cfg.badgeClass}`}>
                                {cfg.shortLabel}
                              </span>
                            </div>
                            <p className="text-[9.5px] text-text-muted font-sans line-clamp-2">
                              {cfg.description}
                            </p>
                          </button>
                        );
                      })}
                    </div>
                  </div>

                  {/* If OUTCOME_REVISED: show revised outcome selector */}
                  {reassessmentDisposition === "OUTCOME_REVISED" && (
                    <div className="space-y-1 pt-1 border-t border-purple-500/20">
                      <label className="text-[10px] font-bold text-purple-300 uppercase block">
                        Revised Outcome Classification <span className="text-rose-400">*</span>
                      </label>
                      <select
                        value={revisedOutcomeType}
                        onChange={(e) => setRevisedOutcomeType(e.target.value as FollowUpOutcomeType)}
                        className="w-full px-2.5 py-1.5 rounded bg-surface border border-purple-500/40 text-text-primary text-xs font-mono"
                      >
                        {(Object.keys(FOLLOW_UP_OUTCOMES_CONFIG) as FollowUpOutcomeType[]).map((k) => (
                          <option key={k} value={k}>
                            {FOLLOW_UP_OUTCOMES_CONFIG[k].label}
                          </option>
                        ))}
                      </select>
                    </div>
                  )}

                  {/* Rationale Input */}
                  <div className="space-y-1">
                    <label className="text-[10px] font-bold text-text-muted uppercase block">
                      Reassessment Rationale &amp; Evidence Audit Note <span className="text-rose-400">*</span>
                    </label>
                    <textarea
                      value={reassessmentRationale}
                      onChange={(e) => setReassessmentRationale(e.target.value)}
                      placeholder="Document what was reviewed from the updated calculation run or drifted observation cutoff, why this disposition was reached, and how prior conclusions were evaluated..."
                      rows={3}
                      maxLength={1000}
                      className="w-full px-3 py-1.5 rounded bg-surface border border-purple-500/40 text-text-primary text-xs focus:outline-hidden focus:border-purple-400 font-sans"
                      required
                    />
                  </div>

                  {/* Submit Reassessment Button */}
                  <div className="flex items-center justify-end gap-2 pt-1 border-t border-purple-500/20">
                    <button
                      type="button"
                      onClick={() => setIsReassessmentMode(false)}
                      className="btn btn-ghost btn-xs text-xs font-mono"
                    >
                      Cancel
                    </button>
                    <button
                      type="button"
                      onClick={handleRecordReassessment}
                      className="btn btn-primary btn-xs text-xs font-mono bg-purple-600 hover:bg-purple-500 border-purple-500 text-white"
                    >
                      Save Reassessment Disposition ⚡
                    </button>
                  </div>
                </div>
              )}

              {/* Compact Investigation Timeline (Section 2.A) */}
              {isTimelineExpanded && (
                <div className="p-3 rounded-lg bg-surface border border-border/70 space-y-2.5 animate-in fade-in duration-150">
                  <div className="flex items-center justify-between border-b border-border/40 pb-1.5">
                    <span className="font-bold uppercase tracking-wider text-[10px] text-text-cream-bright flex items-center gap-1.5">
                      <span>📜</span> Investigation History Timeline
                    </span>
                    <span className="text-[9.5px] text-text-muted">
                      Chronological Order ({timelineEvents.length} events)
                    </span>
                  </div>

                  {timelineEvents.length === 0 ? (
                    <p className="text-[10.5px] text-text-muted font-sans italic py-2 text-center">
                      No structured historical milestones recorded for this inquiry.
                    </p>
                  ) : (
                    <div className="space-y-2 relative before:absolute before:left-3 before:top-2 before:bottom-2 before:w-px before:bg-border/60 pl-6">
                      {timelineEvents.map((evt) => (
                        <div key={evt.id} className="relative group text-[10.5px]">
                          {/* Timeline bullet */}
                          <div className="absolute -left-6 top-1 w-2.5 h-2.5 rounded-full border border-surface bg-text-muted group-hover:bg-accent transition-colors" />

                          <div className="p-2 rounded bg-surface-raised/60 border border-border/60 space-y-1">
                            <div className="flex flex-wrap items-center justify-between gap-1">
                              <div className="flex items-center gap-1.5">
                                <span className={`text-[8.5px] px-1.5 py-0.2 rounded border font-semibold font-mono ${evt.badgeClass}`}>
                                  {evt.label}
                                </span>
                                <span className="font-bold text-text-primary">
                                  {evt.summary}
                                </span>
                              </div>
                              <span className="text-[9px] text-text-muted font-mono">
                                {evt.timestamp ? new Date(evt.timestamp).toLocaleString() : "Undated"}
                              </span>
                            </div>

                            {evt.details && (
                              <p className="text-[10.5px] text-text-secondary font-sans leading-relaxed">
                                {evt.details}
                              </p>
                            )}

                            {/* Event Metadata Badges */}
                            {(evt.provenanceRunId || evt.provenanceAsOfDate || evt.disposition) && (
                              <div className="flex flex-wrap items-center gap-2 pt-1 border-t border-border/30 text-[9px] font-mono text-text-muted">
                                {evt.provenanceRunId && <span>Run #{evt.provenanceRunId}</span>}
                                {evt.provenanceAsOfDate && <span>Cutoff: {evt.provenanceAsOfDate}</span>}
                                {evt.disposition && (
                                  <span className="text-purple-300">
                                    Disposition: {evt.disposition}
                                  </span>
                                )}
                              </div>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>
          )}

          {/* Phase F29: Investigation Prerequisites & Blocker Continuity Card */}
          {effectiveIssue && (
            <div className="p-3.5 rounded-lg bg-surface-raised/80 border border-border/90 space-y-3 text-xs font-mono">
              <div className="flex flex-wrap items-center justify-between gap-1.5 border-b border-border/50 pb-2">
                <div className="flex items-center gap-1.5">
                  <span className="text-amber-400 text-sm">⏳</span>
                  <span className="font-bold uppercase tracking-wider text-[11px] text-text-cream-bright">
                    Prerequisites &amp; Blocker Continuity
                  </span>
                </div>
                <div className="flex items-center gap-2">
                  {dependencyEval && (
                    <span className={`text-[9.5px] px-2 py-0.5 rounded border font-semibold ${dependencyEval.badgeClass}`}>
                      {dependencyEval.stateLabel}
                    </span>
                  )}
                  <button
                    type="button"
                    onClick={() => setIsAddingDependency(!isAddingDependency)}
                    className="btn btn-secondary btn-xs text-accent border-accent/40 hover:bg-accent/10 text-[10.5px] font-mono"
                  >
                    {isAddingDependency ? "✕ Cancel" : "+ Add Prerequisite"}
                  </button>
                </div>
              </div>

              {/* Status explanation / Guidance banner */}
              {dependencyEval && (
                <div className="p-2 rounded bg-surface border border-border/60 text-[10.5px] font-sans text-text-secondary flex items-start justify-between gap-2">
                  <div className="space-y-0.5">
                    <p className="leading-relaxed">
                      <strong>Current Blocker Posture:</strong> {dependencyEval.description}
                    </p>
                    <p className="text-[10px] text-text-muted">
                      {dependencyEval.suggestedAction}
                    </p>
                  </div>
                  <div className="text-[9.5px] font-mono shrink-0 text-text-muted text-right space-y-0.5">
                    <div>Awaiting: <span className="text-amber-400 font-bold">{dependencyEval.awaitingCount}</span></div>
                    <div>Addressed: <span className="text-emerald-400 font-bold">{dependencyEval.addressedCount}</span></div>
                  </div>
                </div>
              )}

              {/* Add Prerequisite Form */}
              {isAddingDependency && (
                <div className="p-3 rounded-lg bg-amber-950/20 border border-amber-500/40 space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="text-amber-300 font-bold uppercase text-[10.5px]">
                      Record Required Prerequisite / External Blocker
                    </span>
                    <span className="text-[9.5px] text-text-muted">
                      Explicit Investor Expectation
                    </span>
                  </div>
                  <p className="text-[10px] text-text-muted font-sans leading-relaxed">
                    Identify what missing disclosure, AMC commentary, or external prerequisite is needed before this investigation can proceed.
                  </p>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                    <div className="space-y-1">
                      <label className="text-[10px] font-bold text-text-muted uppercase block">
                        Dependency Category <span className="text-rose-400">*</span>
                      </label>
                      <select
                        value={newDepCategory}
                        onChange={(e) => setNewDepCategory(e.target.value as DependencyCategory)}
                        className="w-full px-2.5 py-1.5 rounded bg-surface border border-border text-text-primary text-xs font-mono"
                      >
                        {(Object.keys(DEPENDENCY_CATEGORY_CONFIG) as DependencyCategory[]).map((k) => (
                          <option key={k} value={k}>
                            {DEPENDENCY_CATEGORY_CONFIG[k].label}
                          </option>
                        ))}
                      </select>
                      <span className="text-[9px] text-text-muted block font-sans">
                        {DEPENDENCY_CATEGORY_CONFIG[newDepCategory]?.description}
                      </span>
                    </div>

                    <div className="space-y-1">
                      <label className="text-[10px] font-bold text-text-muted uppercase block">
                        Expected Source / Party (Optional)
                      </label>
                      <input
                        type="text"
                        value={newDepExpectedSource}
                        onChange={(e) => setNewDepExpectedSource(e.target.value)}
                        placeholder="e.g. AMC Monthly Portfolio Disclosure, AMFI Bulletin, Risk Team"
                        className="w-full px-2.5 py-1.5 rounded bg-surface border border-border text-text-primary text-xs font-mono"
                      />
                    </div>
                  </div>

                  <div className="space-y-1">
                    <label className="text-[10px] font-bold text-text-muted uppercase block">
                      Description of Missing Information or Prerequisite <span className="text-rose-400">*</span>
                    </label>
                    <textarea
                      value={newDepDescription}
                      onChange={(e) => setNewDepDescription(e.target.value)}
                      placeholder="e.g. Detailed portfolio breakdown for Jan 2024 to verify whether midcap exposure breach was active or passive drift..."
                      rows={2}
                      maxLength={500}
                      className="w-full px-2.5 py-1.5 rounded bg-surface border border-border text-text-primary text-xs font-sans"
                      required
                    />
                  </div>

                  <div className="space-y-1">
                    <label className="text-[10px] font-bold text-text-muted uppercase block">
                      Reason Investigation Cannot Proceed <span className="text-rose-400">*</span>
                    </label>
                    <textarea
                      value={newDepReason}
                      onChange={(e) => setNewDepReason(e.target.value)}
                      placeholder="e.g. Cannot calculate sector concentration attribution without audited holding weights..."
                      rows={2}
                      maxLength={500}
                      className="w-full px-2.5 py-1.5 rounded bg-surface border border-border text-text-primary text-xs font-sans"
                      required
                    />
                  </div>

                  <div className="space-y-1">
                    <label className="text-[10px] font-bold text-text-muted uppercase block">
                      Associated Evidence Reference (Optional)
                    </label>
                    <input
                      type="text"
                      value={newDepEvidenceRef}
                      onChange={(e) => setNewDepEvidenceRef(e.target.value)}
                      placeholder="e.g. Artifact SHA-256 900508f8... or Table NAV_HISTORY"
                      className="w-full px-2.5 py-1.5 rounded bg-surface border border-border text-text-primary text-xs font-mono"
                    />
                  </div>

                  <div className="flex items-center gap-2 pt-1 border-t border-amber-500/20">
                    <label className="flex items-center gap-2 cursor-pointer text-[10.5px] font-sans text-text-secondary">
                      <input
                        type="checkbox"
                        checked={newDepSetWaiting}
                        onChange={(e) => setNewDepSetWaiting(e.target.checked)}
                        className="rounded border-border"
                      />
                      <span>Transition workflow lifecycle state to <strong>Waiting on Dependency</strong></span>
                    </label>
                  </div>

                  <div className="flex items-center justify-end gap-2 pt-1">
                    <button
                      type="button"
                      onClick={() => setIsAddingDependency(false)}
                      className="btn btn-ghost btn-xs text-xs font-mono"
                    >
                      Cancel
                    </button>
                    <button
                      type="button"
                      onClick={handleAddDependency}
                      className="btn btn-primary btn-xs text-xs font-mono bg-amber-600 hover:bg-amber-500 border-amber-500 text-white"
                    >
                      Record Prerequisite ⏳
                    </button>
                  </div>
                </div>
              )}

              {/* Dependencies List */}
              {(!effectiveIssue.dependencies || effectiveIssue.dependencies.length === 0) ? (
                <div className="p-3 rounded bg-surface border border-border/60 text-center space-y-1">
                  <p className="text-[11px] text-text-muted font-sans italic">
                    No structured prerequisites recorded for this inquiry.
                  </p>
                  <p className="text-[10px] text-text-tertiary font-sans">
                    If this investigation is blocked by missing disclosures, external verification, or AMC response, record an explicit prerequisite above.
                  </p>
                </div>
              ) : (
                <div className="space-y-2.5">
                  {effectiveIssue.dependencies.map((dep) => {
                    const catCfg = DEPENDENCY_CATEGORY_CONFIG[dep.category] || DEPENDENCY_CATEGORY_CONFIG.OTHER_PREREQUISITE;
                    const statCfg = DEPENDENCY_STATUS_CONFIG[dep.status] || DEPENDENCY_STATUS_CONFIG.AWAITING;
                    const isResolvingThis = resolvingDepId === dep.id;

                    return (
                      <div
                        key={dep.id}
                        className={`p-3 rounded-lg border transition-all ${
                          dep.status === "AWAITING"
                            ? "bg-surface border-amber-500/40"
                            : dep.status === "ADDRESSED"
                            ? "bg-surface border-emerald-500/40"
                            : "bg-surface/50 border-border/50 opacity-75"
                        }`}
                      >
                        {/* Dep Header */}
                        <div className="flex flex-wrap items-center justify-between gap-1.5 border-b border-border/40 pb-1.5 mb-2">
                          <div className="flex items-center gap-1.5">
                            <span className={`text-[8.5px] px-1.5 py-0.2 rounded border font-mono ${statCfg.badgeClass}`}>
                              {statCfg.label}
                            </span>
                            <span className={`text-[8.5px] px-1.5 py-0.2 rounded border font-mono ${catCfg.badgeClass}`}>
                              {catCfg.label}
                            </span>
                            <span className="text-[10px] text-text-muted font-mono">
                              Recorded: {new Date(dep.recordedAt).toLocaleDateString()}
                            </span>
                          </div>

                          <div className="flex items-center gap-1">
                            {dep.status === "AWAITING" && !isResolvingThis && (
                              <button
                                type="button"
                                onClick={() => setResolvingDepId(dep.id)}
                                className="btn btn-secondary btn-xs text-emerald-400 border-emerald-500/40 hover:bg-emerald-500/10 text-[10px] font-mono"
                              >
                                ✓ Address Blocker
                              </button>
                            )}
                            {dep.status === "AWAITING" && (
                              <button
                                type="button"
                                onClick={() => handleDiscontinueDependency(dep.id)}
                                className="btn btn-ghost btn-xs text-text-tertiary hover:text-text-primary text-[10px] font-mono"
                                title="Discontinue if no longer relevant"
                              >
                                Discontinue
                              </button>
                            )}
                          </div>
                        </div>

                        {/* Dep Details */}
                        <div className="space-y-1.5 text-[11px]">
                          <div>
                            <span className="text-[9.5px] font-bold text-text-muted uppercase block font-mono">Prerequisite Description:</span>
                            <p className="font-sans text-text-primary leading-relaxed font-medium">
                              {dep.description}
                            </p>
                          </div>

                          <div className="p-2 rounded bg-surface-raised/60 border border-border/50 text-[10.5px] space-y-1">
                            <div className="text-text-secondary font-sans">
                              <span className="font-mono text-[9px] uppercase font-bold text-text-muted">Why Blocked: </span>
                              {dep.reasonWhyBlocked}
                            </div>
                            {dep.expectedSource && (
                              <div className="text-text-secondary font-sans">
                                <span className="font-mono text-[9px] uppercase font-bold text-text-muted">Expected Source: </span>
                                <span className="text-accent">{dep.expectedSource}</span>
                              </div>
                            )}
                            {dep.evidenceReference && (
                              <div className="text-text-secondary font-mono text-[10px]">
                                <span className="text-[9px] uppercase font-bold text-text-muted">Evidence Ref: </span>
                                <span className="text-text-primary">{dep.evidenceReference}</span>
                              </div>
                            )}
                          </div>

                          {/* Addressed Blocker Record */}
                          {dep.status === "ADDRESSED" && (
                            <div className="p-2.5 rounded bg-emerald-950/20 border border-emerald-500/40 text-[10.5px] font-sans text-emerald-200/90 space-y-1">
                              <div className="flex flex-wrap items-center justify-between gap-1 text-[9px] font-mono text-emerald-300 font-bold uppercase">
                                <span>✓ Addressed Prerequisite Record</span>
                                <span>{dep.addressedAt ? new Date(dep.addressedAt).toLocaleDateString() : ""}</span>
                              </div>
                              <div className="leading-relaxed">
                                <span className="font-bold text-emerald-300 font-mono text-[9.5px]">Information Received: </span>
                                {dep.receivedInfoOrChange || "Received"}
                              </div>
                              {dep.resolutionRationale && (
                                <div className="leading-relaxed text-[10px] text-emerald-300/80">
                                  <span className="font-bold font-mono text-[9px] uppercase">Rationale: </span>
                                  {dep.resolutionRationale}
                                </div>
                              )}
                              {dep.resolutionEvidenceReference && (
                                <div className="text-[9.5px] font-mono text-emerald-400">
                                  Ref: {dep.resolutionEvidenceReference}
                                </div>
                              )}
                              <p className="text-[9px] text-text-tertiary pt-0.5 border-t border-emerald-500/20 italic font-mono">
                                *Recording received information does not imply mathematical validation or acceptance of investment risk.
                              </p>
                            </div>
                          )}

                          {/* Inline Blocker Resolution Form */}
                          {isResolvingThis && (
                            <div className="p-3 rounded-lg bg-emerald-950/25 border border-emerald-500/50 space-y-2.5 mt-2 animate-in fade-in duration-150">
                              <div className="flex items-center justify-between">
                                <div className="flex items-center gap-1.5">
                                  <span className="text-emerald-300 font-bold uppercase text-[10.5px]">
                                    Record Blocker Resolution Evidence
                                  </span>
                                  <span className="text-[9px] px-1 py-0.2 rounded bg-emerald-500/20 text-emerald-200 border border-emerald-500/30 font-mono">
                                    Workflow Continuity
                                  </span>
                                </div>
                                <span className="text-[10px] text-text-muted">
                                  Preserves Investigation History
                                </span>
                              </div>

                              <p className="text-[10.5px] text-emerald-200/90 font-sans leading-relaxed">
                                Document the received disclosure, evidence, or decision that addresses this prerequisite.
                              </p>

                              <div className="space-y-1">
                                <label className="text-[10px] font-bold text-text-muted uppercase block">
                                  Information / Evidence Received <span className="text-rose-400">*</span>
                                </label>
                                <textarea
                                  value={resolutionReceivedInfo}
                                  onChange={(e) => setResolutionReceivedInfo(e.target.value)}
                                  placeholder="e.g. Received AMC Jan 2024 fact sheet confirming cash allocation remained below 4% through the volatility episode..."
                                  rows={2}
                                  maxLength={500}
                                  className="w-full px-2.5 py-1.5 rounded bg-surface border border-emerald-500/40 text-text-primary text-xs font-sans"
                                  required
                                />
                              </div>

                              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                                <div className="space-y-1">
                                  <label className="text-[10px] font-bold text-text-muted uppercase block">
                                    Evidence Reference / Source Identifier (Optional)
                                  </label>
                                  <input
                                    type="text"
                                    value={resolutionEvidenceRef}
                                    onChange={(e) => setResolutionEvidenceRef(e.target.value)}
                                    placeholder="e.g. AMC Factsheet 2024-01 p.14 or Run #42"
                                    className="w-full px-2.5 py-1.5 rounded bg-surface border border-emerald-500/40 text-text-primary text-xs font-mono"
                                  />
                                </div>

                                <div className="space-y-1">
                                  <label className="text-[10px] font-bold text-text-muted uppercase block">
                                    Next Investigation Action <span className="text-rose-400">*</span>
                                  </label>
                                  <select
                                    value={resolutionNextAction}
                                    onChange={(e) => setResolutionNextAction(e.target.value as any)}
                                    className="w-full px-2.5 py-1.5 rounded bg-surface border border-emerald-500/40 text-text-primary text-xs font-mono"
                                  >
                                    <option value="RESUME_IN_PROGRESS">Resume Investigation (In Progress)</option>
                                    <option value="CONTINUE_WAITING">Continue Waiting (Other Blockers Remain)</option>
                                    <option value="KEEP_CURRENT_STATUS">Keep Current Status (Awaiting Review)</option>
                                  </select>
                                </div>
                              </div>

                              <div className="space-y-1">
                                <label className="text-[10px] font-bold text-text-muted uppercase block">
                                  Resolution Rationale (Optional)
                                </label>
                                <input
                                  type="text"
                                  value={resolutionRationaleText}
                                  onChange={(e) => setResolutionRationaleText(e.target.value)}
                                  placeholder="e.g. Clarification provides sufficient granularity to conclude risk attribution."
                                  className="w-full px-2.5 py-1.5 rounded bg-surface border border-emerald-500/40 text-text-primary text-xs font-sans"
                                />
                              </div>

                              <div className="p-2 rounded bg-amber-500/10 border border-amber-500/30 text-[9.5px] font-sans text-amber-200/90 leading-tight">
                                ⚠️ <strong>Epistemic Guardrail:</strong> Recording received information does not automatically validate financial metrics, certify evidence authenticity, or mark the overall investigation resolved.
                              </div>

                              <div className="flex items-center justify-end gap-2 pt-1 border-t border-emerald-500/20">
                                <button
                                  type="button"
                                  onClick={() => setResolvingDepId(null)}
                                  className="btn btn-ghost btn-xs text-xs font-mono"
                                >
                                  Cancel
                                </button>
                                <button
                                  type="button"
                                  onClick={() => handleConfirmResolveDependency(dep.id)}
                                  className="btn btn-primary btn-xs text-xs font-mono bg-emerald-600 hover:bg-emerald-500 border-emerald-500 text-white"
                                >
                                  Confirm Blocker Addressed ✓
                                </button>
                              </div>
                            </div>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}

          {/* Duplicate Follow-Up Inquiry Avoidance Callout */}
          {matchingSectionIssues.length > 0 && !activeIssue && (
            <div className="p-3 rounded-lg bg-amber-500/10 border border-amber-500/40 text-xs font-mono space-y-2">
              <div className="flex items-center justify-between">
                <span className="font-bold text-amber-300">
                  ⚠️ Active Follow-Up Issue Already Exists for Section {briefSection}:
                </span>
                <span className="text-[10px] text-amber-300">
                  {matchingSectionIssues.length} open issue{matchingSectionIssues.length > 1 ? "s" : ""}
                </span>
              </div>
              <div className="space-y-1.5">
                {matchingSectionIssues.map((mIssue) => (
                  <div key={mIssue.id} className="p-2 rounded bg-surface border border-border flex items-center justify-between gap-2">
                    <div className="min-w-0">
                      <strong className="text-text-primary block truncate">{mIssue.title}</strong>
                      <span className="text-[10px] text-text-muted">
                        Status: {mIssue.status} • Target: {mIssue.targetDate || "None"}
                      </span>
                    </div>
                    <button
                      type="button"
                      onClick={() => {
                        setActiveIssue(mIssue);
                        setTitle(mIssue.title || "");
                        setCategory(mIssue.category || "EVIDENCE_LIMITATION");
                        setBriefSection(mIssue.briefSection || "B");
                        setQuestionOrReason(mIssue.questionOrReason || "");
                        setNextAction(mIssue.nextAction || "");
                        setTargetDate(mIssue.targetDate || "");
                        setStatus(mIssue.status || "OPEN");
                        setResolutionNote(mIssue.resolutionNote || "");
                      }}
                      className="btn btn-secondary btn-xs text-accent border-accent/40 hover:bg-accent/10 shrink-0 text-[10.5px]"
                    >
                      Load Existing Issue ↗
                    </button>
                  </div>
                ))}
              </div>
              <p className="text-[10.5px] text-text-secondary font-sans leading-relaxed">
                You can load and update the existing inquiry above to prevent duplicates, or proceed below to record a distinct follow-up issue.
              </p>
            </div>
          )}

          {activeIssue && !issue && (
            <div className="p-2.5 rounded-lg bg-accent/10 border border-accent/30 text-[11px] font-mono flex items-center justify-between gap-2">
              <span className="text-text-primary">
                Loaded existing issue: <strong className="text-accent">{activeIssue.title}</strong>
              </span>
              <button
                type="button"
                onClick={() => {
                  setActiveIssue(null);
                  setTitle(prefill?.title || "");
                  setCategory(prefill?.category || "EVIDENCE_LIMITATION");
                  setBriefSection(prefill?.briefSection || "B");
                  setQuestionOrReason(prefill?.questionOrReason || "");
                  setNextAction("");
                  setTargetDate("");
                  setStatus("OPEN");
                  setResolutionNote("");
                }}
                className="text-text-muted hover:text-text-primary underline text-[10px]"
              >
                Switch to New Issue ✕
              </button>
            </div>
          )}

          {/* Issue Title */}
          <div className="space-y-1">
            <label className="text-[11px] font-bold text-text-primary uppercase block">
              Issue Title <span className="text-rose-400">*</span>
            </label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. Drawdown anomaly during 2022 rate hike cycle"
              maxLength={140}
              className="w-full px-3 py-1.5 rounded bg-surface-raised border border-border text-text-primary text-xs focus:outline-hidden focus:border-accent"
              required
            />
          </div>

          {/* Category & Relevant Brief Section */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div className="space-y-1">
              <label className="text-[11px] font-bold text-text-primary uppercase block">
                Issue Category <span className="text-rose-400">*</span>
              </label>
              <select
                value={category}
                onChange={(e) => {
                  const cat = e.target.value as FollowUpCategory;
                  setCategory(cat);
                  const defSec = FOLLOW_UP_CATEGORIES_CONFIG[cat]?.defaultBriefSection;
                  if (defSec && !issue) setBriefSection(defSec);
                }}
                className="w-full px-3 py-1.5 rounded bg-surface-raised border border-border text-text-primary text-xs focus:outline-hidden focus:border-accent"
              >
                {(Object.keys(FOLLOW_UP_CATEGORIES_CONFIG) as FollowUpCategory[]).map((cat) => (
                  <option key={cat} value={cat}>
                    {FOLLOW_UP_CATEGORIES_CONFIG[cat].label}
                  </option>
                ))}
              </select>
              <span className="text-[10px] text-text-muted block font-sans">
                {FOLLOW_UP_CATEGORIES_CONFIG[category]?.description}
              </span>
            </div>

            <div className="space-y-1">
              <label className="text-[11px] font-bold text-text-primary uppercase block">
                Associated Research Section
              </label>
              <select
                value={briefSection}
                onChange={(e) => setBriefSection(e.target.value as any)}
                className="w-full px-3 py-1.5 rounded bg-surface-raised border border-border text-text-primary text-xs focus:outline-hidden focus:border-accent"
              >
                <option value="A">{getBriefSectionTitle("A")}</option>
                <option value="B">{getBriefSectionTitle("B")}</option>
                <option value="C">{getBriefSectionTitle("C")}</option>
                <option value="D">{getBriefSectionTitle("D")}</option>
                <option value="E">{getBriefSectionTitle("E")}</option>
                <option value="F">{getBriefSectionTitle("F")}</option>
                <option value="G">{getBriefSectionTitle("G")}</option>
                <option value="H">{getBriefSectionTitle("H")}</option>
              </select>
            </div>
          </div>

          {/* Question or Reason Requiring Follow-Up */}
          <div className="space-y-1">
            <label className="text-[11px] font-bold text-text-primary uppercase block">
              Diligence Question / Concern <span className="text-rose-400">*</span>
            </label>
            <textarea
              value={questionOrReason}
              onChange={(e) => setQuestionOrReason(e.target.value)}
              placeholder="What observation or inconsistency needs to be verified? (e.g. Navigated 2022 with significantly lower beta than stated mandate.)"
              rows={3}
              maxLength={1000}
              className="w-full px-3 py-1.5 rounded bg-surface-raised border border-border text-text-primary text-xs focus:outline-hidden focus:border-accent font-sans"
              required
            />
          </div>

          {/* Next Investigation Action */}
          <div className="space-y-1">
            <label className="text-[11px] font-bold text-text-primary uppercase block">
              Next Investigation Action <span className="text-rose-400">*</span>
            </label>
            <textarea
              value={nextAction}
              onChange={(e) => setNextAction(e.target.value)}
              placeholder="What specific step will you take to resolve this? (e.g. Inspect rolling 1Y volatility against Nifty 50 TRI in Historical Evidence.)"
              rows={2}
              maxLength={500}
              className="w-full px-3 py-1.5 rounded bg-surface-raised border border-border text-text-primary text-xs focus:outline-hidden focus:border-accent font-sans"
              required
            />
          </div>

          {/* Status & Resolution Mode Selector */}
          <div className="p-3 rounded-lg bg-surface-raised/50 border border-border/80 space-y-2.5">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="space-y-1">
                <label className="text-[11px] font-bold text-text-primary uppercase block">
                  Workflow Lifecycle State <span className="text-rose-400">*</span>
                </label>
                <select
                  value={status}
                  onChange={(e) => {
                    const nextSt = e.target.value as FollowUpStatus;
                    setStatus(nextSt);
                    if (nextSt === "DISMISSED") {
                      setOutcomeType("DISMISSED");
                    } else if (nextSt === "RESOLVED" && outcomeType === "DISMISSED") {
                      setOutcomeType("ANSWERED");
                    }
                  }}
                  className="w-full px-3 py-1.5 rounded bg-surface-raised border border-border text-text-primary text-xs focus:outline-hidden focus:border-accent"
                >
                  <option value="OPEN">Still Investigating: Open Diligence</option>
                  <option value="IN_PROGRESS">Still Investigating: In Progress</option>
                  <option value="WAITING">Waiting on Dependency</option>
                  <option value="RESOLVED">Resolved (Record Empirical Outcome)</option>
                  <option value="DISMISSED">Dismissed (Stop Pursuing Issue)</option>
                </select>
                <span className="text-[10px] text-text-muted block font-sans">
                  {FOLLOW_UP_STATUS_CONFIG[status]?.description}
                </span>
              </div>

              <div className="space-y-1">
                <label className="text-[11px] font-bold text-text-primary uppercase block">
                  Optional Target Date
                </label>
                <input
                  type="date"
                  value={targetDate}
                  onChange={(e) => setTargetDate(e.target.value)}
                  className="w-full px-3 py-1.5 rounded bg-surface-raised border border-border text-text-primary text-xs focus:outline-hidden focus:border-accent"
                />
                <span className="text-[10px] text-text-muted block font-sans">
                  Personal diligence milestone; does not imply investment urgency.
                </span>
              </div>
            </div>

            {/* Resolution Sub-Mode (Complete vs With Uncertainty) */}
            {status === "RESOLVED" && (
              <div className="pt-2 border-t border-border/50 space-y-1.5">
                <label className="text-[11px] font-bold text-emerald-400 uppercase block font-mono">
                  Closure Completeness Mode
                </label>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs">
                  <button
                    type="button"
                    onClick={() => setHasResidualUncertainty(false)}
                    className={`p-2 rounded border text-left flex flex-col gap-0.5 cursor-pointer transition-all ${
                      !hasResidualUncertainty
                        ? "bg-emerald-500/15 border-emerald-500 text-emerald-300 shadow-xs"
                        : "bg-surface border-border text-text-muted hover:text-text-primary"
                    }`}
                  >
                    <div className="font-bold text-[11px] flex items-center gap-1.5">
                      <span>✓</span>
                      <span>Resolved: Complete</span>
                    </div>
                    <span className="text-[10px] font-sans opacity-90 leading-tight">
                      Documented question addressed sufficiently by inspected evidence without remaining material limitations.
                    </span>
                  </button>

                  <button
                    type="button"
                    onClick={() => setHasResidualUncertainty(true)}
                    className={`p-2 rounded border text-left flex flex-col gap-0.5 cursor-pointer transition-all ${
                      hasResidualUncertainty
                        ? "bg-amber-500/15 border-amber-500 text-amber-300 shadow-xs"
                        : "bg-surface border-border text-text-muted hover:text-text-primary"
                    }`}
                  >
                    <div className="font-bold text-[11px] flex items-center gap-1.5">
                      <span>⚠️</span>
                      <span>Resolved with Residual Uncertainty</span>
                    </div>
                    <span className="text-[10px] font-sans opacity-90 leading-tight">
                      Outcome documented but with explicit remaining empirical uncertainty or pending subsequent disclosures.
                    </span>
                  </button>
                </div>
              </div>
            )}
          </div>

          {/* Phase F18 & F24: Reopening in-progress notification with mandatory Renewal Rationale */}
          {isReopeningNow && (
            <div className="p-3 rounded-lg bg-amber-500/15 border border-amber-500/40 text-amber-200 text-xs font-mono space-y-2">
              <div className="flex items-center gap-1.5 font-bold text-amber-300">
                <span>🔄</span>
                <span>REOPENING INQUIRY FOR RENEWED INVESTIGATION</span>
              </div>
              <p className="font-sans text-[11.5px] leading-relaxed text-amber-200/90">
                The prior resolution outcome below will be permanently preserved in the audit record. Updating status to {FOLLOW_UP_STATUS_CONFIG[status].label} establishes a clean active inquiry slate.
              </p>
              <div className="space-y-1 pt-1.5 border-t border-amber-500/30">
                <label className="text-[11px] font-bold text-amber-300 uppercase block font-mono">
                  Rationale for Renewed Investigation <span className="text-rose-400">*</span>
                </label>
                <textarea
                  value={reopenReason}
                  onChange={(e) => setReopenReason(e.target.value)}
                  placeholder="Document why this previously closed issue is being reopened (e.g. Subsequent AMC monthly portfolio discloses sudden reallocation; or new calculation run indicates unexplained deviation)."
                  rows={2}
                  maxLength={500}
                  className="w-full px-3 py-1.5 rounded bg-surface border border-amber-500/40 text-text-primary text-xs focus:outline-hidden focus:border-accent font-sans"
                  required
                />
              </div>
            </div>
          )}

          {/* Phase F18 & F24: Preserved Prior Outcome Callout (Audit Record) */}
          {(effectiveIssue?.priorResolutionNote || (isReopeningNow && effectiveIssue?.resolutionNote)) && (
            <div className="p-3 rounded-lg bg-surface-raised/80 border border-border/80 space-y-2 text-xs font-mono">
              <div className="flex flex-wrap items-center justify-between gap-1 border-b border-border/50 pb-1.5">
                <div className="flex items-center gap-2 font-bold text-text-primary">
                  <span>🔄</span>
                  <span className="text-[11px] uppercase tracking-wider text-text-cream-bright">Preserved Prior Outcome Record</span>
                  {effectiveIssue?.priorOutcomeType && FOLLOW_UP_OUTCOMES_CONFIG[effectiveIssue.priorOutcomeType] && (
                    <span className={`text-[9.5px] px-1.5 py-0.2 rounded border font-semibold ${FOLLOW_UP_OUTCOMES_CONFIG[effectiveIssue.priorOutcomeType].badgeClass}`}>
                      {FOLLOW_UP_OUTCOMES_CONFIG[effectiveIssue.priorOutcomeType].shortLabel}
                    </span>
                  )}
                </div>
                {effectiveIssue?.priorResolvedAt && (
                  <span className="text-[10px] text-text-muted">
                    Resolved on {new Date(effectiveIssue.priorResolvedAt).toLocaleDateString()}
                  </span>
                )}
              </div>
              <p className="text-[11.5px] text-text-secondary font-sans leading-relaxed">
                {effectiveIssue?.priorResolutionNote || effectiveIssue?.resolutionNote}
              </p>
              {effectiveIssue?.reopenReason && (
                <div className="p-1.5 rounded bg-purple-950/30 border border-purple-500/30 text-[10.5px] text-purple-200/90 font-sans">
                  <span className="font-mono text-[9px] uppercase font-bold text-purple-300">Renewal Rationale: </span>
                  {effectiveIssue.reopenReason}
                </div>
              )}
              <div className="text-[10px] text-text-muted font-sans flex flex-wrap items-center justify-between gap-2 pt-1 border-t border-border/40">
                <span>Prior finding remains permanently documented in the audit trail.</span>
                {effectiveIssue?.reopenedAt && (
                  <span>Reopened on {new Date(effectiveIssue.reopenedAt).toLocaleDateString()}</span>
                )}
              </div>
            </div>
          )}

          {/* Phase F24: Evidence-Aware Closure Guidance & Readiness Review */}
          {isResolvingOrDismissing && closureGuidance && (
            <div className="p-3.5 rounded-lg bg-surface-raised/80 border border-emerald-500/40 space-y-3 font-mono text-xs">
              <div className="flex flex-wrap items-center justify-between gap-1.5 border-b border-border/50 pb-2">
                <div className="flex items-center gap-1.5">
                  <span className="text-emerald-400 text-sm">⚖️</span>
                  <span className="font-bold uppercase tracking-wider text-[11px] text-text-cream-bright">
                    Evidence-Aware Closure Guidance &amp; Audit Review
                  </span>
                </div>
                <span
                  className={`text-[9.5px] px-2 py-0.5 rounded border font-semibold ${
                    closureGuidance.canResolveCleanly
                      ? "bg-emerald-500/20 text-emerald-300 border-emerald-500/40"
                      : "bg-amber-500/20 text-amber-300 border-amber-500/40"
                  }`}
                >
                  {closureGuidance.canResolveCleanly ? "✓ Ready for Standard Closure" : "⚠️ Closure with Accepted Limitations"}
                </span>
              </div>

              {/* Review of Existing Record State */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-[10.5px]">
                <div className="p-2 rounded bg-surface border border-border/60 space-y-0.5">
                  <span className="text-text-muted text-[9.5px] uppercase block">Original Diligence Question</span>
                  <p className="font-sans text-text-primary line-clamp-2 leading-relaxed">
                    {questionOrReason || "Not recorded"}
                  </p>
                </div>

                <div className="p-2 rounded bg-surface border border-border/60 space-y-0.5">
                  <span className="text-text-muted text-[9.5px] uppercase block">Recorded Next Action</span>
                  <p className="font-sans text-text-primary line-clamp-2 leading-relaxed">
                    {nextAction || "Not recorded"}
                  </p>
                </div>

                <div className="p-2 rounded bg-surface border border-border/60 space-y-0.5">
                  <span className="text-text-muted text-[9.5px] uppercase block">Evidence Provenance &amp; Cutoff</span>
                  <span className="font-sans text-text-primary block font-medium">
                    {provenanceSummary ? provenanceSummary.statusBadgeLabel : "Unrecorded"}{" "}
                    (Run #{effectiveIssue?.evidenceRunId || evidenceContext?.calculationRunId || "—"}, Cutoff: {effectiveIssue?.evidenceAsOfDate || evidenceContext?.asOfDate || "—"})
                  </span>
                </div>

                <div className="p-2 rounded bg-surface border border-border/60 space-y-0.5">
                  <span className="text-text-muted text-[9.5px] uppercase block">Workflow Posture</span>
                  <span className="font-sans text-text-primary block font-medium">
                    {effectiveIssue?.status === "WAITING" ? "Dependency Pending" : targetDate ? `Target: ${targetDate}` : "No outstanding dependency"}
                    {isReopeningNow ? " (Reopening)" : effectiveIssue?.priorResolutionNote ? " (Reopened previously)" : ""}
                  </span>
                </div>
              </div>

              {/* Outstanding Blockers / Limitations Warning */}
              {Boolean(closureGuidance.blockers && closureGuidance.blockers.length > 0) && (
                <div className="p-2.5 rounded bg-amber-500/10 border border-amber-500/40 space-y-2">
                  <div className="flex items-center gap-1.5 font-bold text-amber-300 text-[10.5px]">
                    <span>⚠️</span>
                    <span>OUTSTANDING LIMITATIONS DETECTED BEFORE CLOSURE:</span>
                  </div>
                  <ul className="space-y-1 text-[11px] font-sans text-amber-200/90 pl-3 list-disc">
                    {closureGuidance.blockers?.map((b) => (
                      <li key={b.code}>
                        <strong>{b.label}:</strong> {b.explanation}
                      </li>
                    ))}
                  </ul>

                  {/* Required Rationale for Overriding Limitations */}
                  <div className="pt-2 border-t border-amber-500/30 space-y-1">
                    <label className="text-[10.5px] font-bold text-amber-300 uppercase block font-mono">
                      Closure Limitation Rationale <span className="text-rose-400">*</span>
                    </label>
                    <textarea
                      value={closureLimitationRationale}
                      onChange={(e) => setClosureLimitationRationale(e.target.value)}
                      placeholder="Explain why this inquiry is considered sufficiently addressed despite the above limitations (e.g. Next action was rendered obsolete by new quarterly filing; or evidence drift does not affect the isolated drawdown finding)."
                      rows={2}
                      maxLength={500}
                      className="w-full px-3 py-1.5 rounded bg-surface border border-amber-500/50 text-text-primary text-xs focus:outline-hidden focus:border-accent font-sans"
                      required
                    />
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Resolution / Outcome Note (conditionally required when resolving or dismissing) */}
          {isResolvingOrDismissing && (
            <div className="p-3.5 rounded-lg bg-surface-raised/80 border border-emerald-500/40 space-y-3">
              {/* Outcome classification */}
              <div className="space-y-1.5">
                <label className="text-[11px] font-bold text-emerald-300 uppercase block">
                  Investigation Outcome Classification <span className="text-rose-400">*</span>
                </label>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  {(Object.keys(FOLLOW_UP_OUTCOMES_CONFIG) as FollowUpOutcomeType[])
                    .filter((ot) => (status === "DISMISSED" ? ot === "DISMISSED" : ot !== "DISMISSED"))
                    .map((ot) => {
                      const conf = FOLLOW_UP_OUTCOMES_CONFIG[ot];
                      const isSelected = outcomeType === ot;
                      return (
                        <button
                          key={ot}
                          type="button"
                          onClick={() => setOutcomeType(ot)}
                          className={`p-2 rounded border text-left flex flex-col justify-between gap-1 transition-all ${
                            isSelected
                              ? "bg-accent/15 border-accent text-accent shadow-xs"
                              : "bg-surface border-border/80 text-text-secondary hover:text-text-primary"
                          }`}
                        >
                          <div className="flex items-center justify-between gap-1">
                            <span className="font-bold text-[11px]">{conf.label}</span>
                            <span className={`text-[9px] px-1 rounded border font-mono ${conf.badgeClass}`}>
                              {conf.shortLabel}
                            </span>
                          </div>
                          <span className="text-[10px] text-text-muted leading-tight font-sans">
                            {conf.description}
                          </span>
                        </button>
                      );
                    })}
                </div>
              </div>

              {/* Resolution Note */}
              <div className="space-y-1">
                <label className="text-[11px] font-bold text-text-primary uppercase block">
                  {status === "RESOLVED" ? "Empirical Findings & Resolution Note *" : "Dismissal Explanation *"}
                </label>
                <textarea
                  value={resolutionNote}
                  onChange={(e) => setResolutionNote(e.target.value)}
                  placeholder={
                    status === "RESOLVED"
                      ? "Document what was verified from inspected evidence, which question was answered or partially answered, and the factual basis for this outcome."
                      : "Document why you decided not to pursue this inquiry further."
                  }
                  rows={3}
                  maxLength={1000}
                  className="w-full px-3 py-1.5 rounded bg-surface border border-border text-text-primary text-xs focus:outline-hidden focus:border-accent font-sans"
                  required
                />
              </div>

              {/* Residual Uncertainty (required if hasResidualUncertainty, or recommended for partial outcomes) */}
              {status === "RESOLVED" && (hasResidualUncertainty || outcomeType === "PARTIALLY_ANSWERED" || outcomeType === "DATA_UNAVAILABLE") && (
                <div className="space-y-1 pt-1 border-t border-border/40">
                  <div className="flex items-center justify-between">
                    <label className="text-[10.5px] font-bold text-amber-300 uppercase block">
                      Residual Uncertainty &amp; Missing Disclosures {hasResidualUncertainty ? <span className="text-rose-400">*</span> : "(Recommended)"}
                    </label>
                    <span className="text-[10px] text-text-muted font-sans">
                      {hasResidualUncertainty ? "Required for uncertainty closure" : "Recommended for partial outcomes"}
                    </span>
                  </div>
                  <textarea
                    value={residualUncertainty}
                    onChange={(e) => setResidualUncertainty(e.target.value)}
                    placeholder="Specify what remains uncertain, unverified, or pending future disclosures (e.g. Awaiting subsequent AMC portfolio filing to verify whether cash drag was transient)."
                    rows={2}
                    maxLength={500}
                    className="w-full px-3 py-1.5 rounded bg-surface border border-border text-text-primary text-xs focus:outline-hidden focus:border-accent font-sans"
                    required={hasResidualUncertainty}
                  />
                </div>
              )}
            </div>
          )}

          {/* Epistemic Guardrail Notice & Continuity Guidance */}
          <div className="p-2.5 rounded bg-surface-raised/40 border border-border/60 text-[10.5px] text-text-muted leading-relaxed font-sans space-y-1">
            <p>
              *Factual Decoupling: Documenting this finding records your personal review activity. It does <em>not</em> alter quantitative calculations, automatically mark checklist items reviewed, or modify your research thesis.
            </p>
            <p className="text-text-tertiary">
              Closure represents an explicit investor due-diligence determination. It does NOT constitute mathematical proof, algorithmic validation, or an investment recommendation.
            </p>
          </div>

          {/* Actions */}
          <div className="flex flex-wrap items-center justify-between gap-2 pt-2 border-t border-border">
            <div className="flex items-center gap-2">
              {(activeIssue || issue) && (
                <button
                  type="button"
                  onClick={handleDelete}
                  className="btn btn-ghost btn-xs text-rose-400 hover:bg-rose-500/10 text-[11px]"
                >
                  Delete Issue
                </button>
              )}
              {(activeIssue || issue) && onNavigateToBrief && (
                <button
                  type="button"
                  onClick={() => {
                    onNavigateToBrief((activeIssue || issue)!);
                    onClose();
                  }}
                  className="btn btn-secondary btn-xs text-accent border-accent/40 hover:bg-accent/10 font-mono text-[10.5px]"
                  title="Open Research Brief focused on this issue's section"
                >
                  Inspect Evidence in Brief (Sec {briefSection}) ↗
                </button>
              )}
            </div>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={onClose}
                className="btn btn-ghost btn-sm text-xs font-mono"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="btn btn-primary btn-sm text-xs font-mono"
              >
                {activeIssue || issue ? "Update Follow-Up" : "Save Follow-Up"}
              </button>
            </div>
          </div>
        </form>
      </div>
    </div>
  );
}
