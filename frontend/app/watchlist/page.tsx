"use client";

import { useEffect, useState, useMemo, Suspense } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { PageContainer } from "@/components/layout/PageContainer";
import { StateView } from "@/components/epistemic/StateView";
import { fetchAllSchemes } from "@/lib/api/schemes";
import { Scheme } from "@/types/domain";
import { useWatchlist, getWatchlistItemKey } from "@/lib/hooks/useWatchlist";
import { WatchlistResponse, DueDiligenceStatus, DUE_DILIGENCE_STATUS_MAP } from "@/lib/api/investor";
import { ResearchNoteModal } from "@/components/watchlist/ResearchNoteModal";
import { FundResearchBriefModal } from "@/components/watchlist/FundResearchBriefModal";
import { formatCurrency, formatDateTime } from "@/lib/utils/formatters";
import { InvestorOnboardingGuide } from "@/components/onboarding/InvestorOnboardingGuide";
import { EvidenceQualitySummary } from "@/components/epistemic/EvidenceQualitySummary";
import {
  getEvidenceSnapshot,
  computeEvidenceChanges,
  loadEvidenceSnapshots,
} from "@/lib/utils/evidenceChange";
import {
  resolveAttentionItem,
  compareAttentionItems,
  computeResearchAttentionSummary,
  ResolvedAttentionItem,
} from "@/lib/utils/researchAttention";
import { ResearchAttentionSummary } from "@/components/watchlist/ResearchAttentionSummary";
import {
  evaluateResearchItemSearch,
  ResearchSearchResult,
} from "@/lib/utils/researchSearch";
import { BulkActionBar } from "@/components/watchlist/BulkActionBar";
import { BulkActionOutcome } from "@/lib/utils/bulkResearchActions";
import { useUser } from "@auth0/nextjs-auth0/client";
import { SavedViewsBar } from "@/components/watchlist/SavedViewsBar";
import {
  SavedResearchView,
  WorkspaceConfiguration,
  loadSavedViews,
  DEFAULT_VIEW_CONFIG,
  isValidFilterTab,
  isValidSortOption,
  isViewActive,
} from "@/lib/utils/savedResearchViews";
import { RecentSessionBanner } from "@/components/watchlist/RecentSessionBanner";
import {
  ResumableResearchSession,
  InvestigationHandoffContext,
  loadRecentResearchSession,
  saveRecentResearchSession,
  clearRecentResearchSession,
  loadInvestigationHandoff,
  saveInvestigationHandoff,
  clearInvestigationHandoff,
  createInvestigationHandoff,
  getBriefSectionTitle,
} from "@/lib/utils/researchSession";
import { InvestigationCompletenessOverview } from "@/components/watchlist/InvestigationCompletenessOverview";
import {
  resolveRecordCompleteness,
  computeInvestigationCompletenessSummary,
  RecordCompletenessResolution,
  isFieldRecorded,
} from "@/lib/utils/researchCompleteness";
import {
  loadAllReviewChecklists,
  computeChecklistProgress,
  ChecklistProgressSummary,
  ReviewItemKey,
} from "@/lib/utils/researchReviewChecklist";
import {
  ResearchFollowUpIssue,
  loadAllFollowUpIssues,
  isTargetDatePassed,
} from "@/lib/utils/researchFollowUp";
import { ResearchFollowUpWorkspace } from "@/components/watchlist/ResearchFollowUpWorkspace";
import { ResearchFollowUpModal } from "@/components/watchlist/ResearchFollowUpModal";
import { ResearchCoverageOverview } from "@/components/watchlist/ResearchCoverageOverview";
import { evaluateFundResearchCoverage } from "@/lib/utils/researchCoverage";

type SortOption =
  | "attention_priority"
  | "needs_followup_first"
  | "to_investigate_first"
  | "updated_desc"
  | "created_desc"
  | "name_asc"
  | "name_desc"
  | "notes_first";

export type FilterTab =
  | "ALL"
  | "ATTENTION_QUEUE"
  | "WITH_THESIS"
  | "WITHOUT_THESIS"
  | "INCOMPLETE_RECORDS"
  | "MISSING_QUESTION"
  | "MISSING_NEXT_STEP"
  | "MISSING_SUPPORTING_EVIDENCE"
  | "MISSING_RECONSIDERATION_CONDITIONS"
  | "STALE_EVIDENCE"
  | "NEEDS_FOLLOW_UP"
  | "TO_INVESTIGATE"
  | "IN_PROGRESS"
  | "REVIEWED"
  | "EVIDENCE_CHANGED"
  | "REVIEW_NEEDED"
  | "HISTORICAL_UNAVAILABLE"
  | "COMPARATIVE"
  | "SHORTLIST_SELECTED"
  | "HELD_IN_PORTFOLIO"
  | "OPTION_MISMATCH"
  | "QUESTIONS"
  | "NOTES"
  | "DIRECT_GROWTH"
  | "REGULAR_PLANS"
  | "WITH_FOLLOW_UP_ISSUES"
  | "WITH_RESIDUAL_UNCERTAINTY"
  | "INVESTIGATION_ACTION_QUEUE"
  | "ACTIONABLE_COVERAGE_GAPS";

export function isComparativeItem(item: WatchlistResponse): boolean {
  const text = `${item.reasonSaved || ""} ${item.investigationQuestion || ""}`.toLowerCase();
  return text.includes("comparative") || text.includes("shortlist") || text.includes("peer");
}

function WatchlistContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const initialSearch = searchParams.get("q") || searchParams.get("search") || "";
  const tabParam = searchParams.get("tab") || searchParams.get("filter");
  const amcParam = searchParams.get("amc") || "ALL";
  const initialTab: FilterTab =
    tabParam === "attention" || tabParam === "ATTENTION_QUEUE" || tabParam === "queue"
      ? "ATTENTION_QUEUE"
      : (tabParam as FilterTab) || "ALL";

  const [schemes, setSchemes] = useState<Scheme[]>([]);
  const [loadingSchemes, setLoadingSchemes] = useState(true);
  const [searchQuery, setSearchQuery] = useState(initialSearch);
  const initialSort: SortOption =
    searchParams.get("sort") === "attention" || initialTab === "ATTENTION_QUEUE"
      ? "attention_priority"
      : (searchParams.get("sort") as SortOption) || "updated_desc";
  const [sortBy, setSortBy] = useState<SortOption>(initialSort);
  const [filterTab, setFilterTab] = useState<FilterTab>(initialTab);
  const [selectedAmc, setSelectedAmc] = useState<string>(amcParam);
  const [selectedForCompare, setSelectedForCompare] = useState<string[]>([]);
  // Phase F10: Bulk research workspace multi-selection & outcome state
  const [selectedItemKeys, setSelectedItemKeys] = useState<string[]>([]);
  const [bulkOutcome, setBulkOutcome] = useState<BulkActionOutcome | null>(null);
  const [isBulkOperating, setIsBulkOperating] = useState<boolean>(false);
  // Phase F11: Saved Research Views state
  const { user } = useUser();
  const [savedViews, setSavedViews] = useState<SavedResearchView[]>([]);
  const [activeSavedViewId, setActiveSavedViewId] = useState<string | null>(null);
  const [viewNotification, setViewNotification] = useState<{
    message: string;
    kind: "success" | "info" | "warning";
  } | null>(null);

  // Phase F12: Resumable Research Session state (scoped to browser / authenticated user)
  const [recentSession, setRecentSession] = useState<ResumableResearchSession | null>(null);

  // Active note editing modal state
  const [editingFund, setEditingFund] = useState<WatchlistResponse | null>(null);

  // Active research brief modal state
  const [briefFund, setBriefFund] = useState<WatchlistResponse | null>(null);
  const [briefInitialSection, setBriefInitialSection] = useState<"coverage" | "checklist" | "A" | "B" | "C" | "D" | "E" | "F" | "G" | "H">("A");
  const [briefInitialIssueId, setBriefInitialIssueId] = useState<string | null>(null);
  const [reviewChecklistVersion, setReviewChecklistVersion] = useState(0);

  // Phase F15: Research Follow-Up issues state
  const [followUpIssues, setFollowUpIssues] = useState<ResearchFollowUpIssue[]>([]);
  const [followUpRefreshVersion, setFollowUpRefreshVersion] = useState<number>(0);
  const [activeFollowUpModalIssue, setActiveFollowUpModalIssue] = useState<ResearchFollowUpIssue | null>(null);
  const [followUpModalFund, setFollowUpModalFund] = useState<WatchlistResponse | null>(null);
  const [isFollowUpModalOpen, setIsFollowUpModalOpen] = useState<boolean>(false);
  const [followUpModalInitialReassessment, setFollowUpModalInitialReassessment] = useState<boolean>(false);
  const [followUpModalInitialShowTimeline, setFollowUpModalInitialShowTimeline] = useState<boolean>(false);
  const [followUpModalInitialDependencyId, setFollowUpModalInitialDependencyId] = useState<string | null>(null);
  const [followUpModalPrefill, setFollowUpModalPrefill] = useState<{
    title?: string;
    briefSection?: "A" | "B" | "C" | "D" | "E" | "F" | "G" | "H";
    checklistKey?: ReviewItemKey;
    questionOrReason?: string;
  } | undefined>(undefined);

  // Destructive removal confirmation state (prevents accidental loss of investor-authored notes)
  const [confirmingDeleteKey, setConfirmingDeleteKey] = useState<string | null>(null);

  // Hash anchor targeting state for smooth cross-workflow navigation
  const [highlightedKey, setHighlightedKey] = useState<string | null>(null);

  const {
    watchlistItems,
    removeFromWatchlist,
    saveResearchNote,
    updateInvestigationStatus,
    refreshWatchlist,
    isLoaded,
    syncStatus,
    error,
  } = useWatchlist();

  // Handle cross-workflow deep linking via hash (e.g. /watchlist#saved-10262 from Portfolio or Discover)
  useEffect(() => {
    if (isLoaded && typeof window !== "undefined" && window.location.hash) {
      const targetId = window.location.hash.slice(1);
      const el = document.getElementById(targetId);
      if (el) {
        setTimeout(() => {
          el.scrollIntoView({ behavior: "smooth", block: "center" });
          setHighlightedKey(targetId);
        }, 150);
      }
    }
  }, [isLoaded]);

  // Current workspace configuration object for Saved Views comparison
  const currentWorkspaceConfig: WorkspaceConfiguration = useMemo(
    () => ({
      searchQuery,
      filterTab,
      selectedAmc,
      sortBy,
    }),
    [searchQuery, filterTab, selectedAmc, sortBy]
  );

  // Phase F11: Load saved research views scoped by authenticated user / guest identity
  useEffect(() => {
    if (typeof window !== "undefined") {
      const loaded = loadSavedViews(user?.sub);
      setSavedViews(loaded);
    }
  }, [user?.sub]);

  // Phase F12 & F27: Load recent research session partitioned by authenticated user / guest identity
  useEffect(() => {
    if (typeof window !== "undefined") {
      const session = loadRecentResearchSession(user?.sub);
      if (session && !session.handoffContext) {
        const storedHandoff = loadInvestigationHandoff(user?.sub);
        if (storedHandoff) {
          session.handoffContext = storedHandoff;
        }
      }
      setRecentSession(session);
    }
  }, [user?.sub]);

  // Phase F11: Auto-dismiss view notification after 6 seconds
  useEffect(() => {
    if (!viewNotification) return;
    const timer = setTimeout(() => {
      setViewNotification(null);
    }, 6000);
    return () => clearTimeout(timer);
  }, [viewNotification]);

  // Phase F15: Load all follow-up issues partitioned by user identity
  useEffect(() => {
    if (typeof window !== "undefined") {
      const issues = loadAllFollowUpIssues(user?.sub);
      setFollowUpIssues(issues);
    }
  }, [user?.sub, followUpRefreshVersion]);

  const handleOpenIssueModal = (
    issue: ResearchFollowUpIssue,
    handoff?: InvestigationHandoffContext | null,
    modalOpts?: {
      initialReassessmentMode?: boolean;
      initialShowTimeline?: boolean;
      initialDependencyId?: string | null;
    }
  ) => {
    const matchingFund = watchlistItems.find(
      (w) =>
        w.schemeId === issue.schemeId &&
        (issue.schemeOptionId === undefined ||
          issue.schemeOptionId === null ||
          w.schemeOptionId === issue.schemeOptionId)
    );

    setActiveFollowUpModalIssue(issue);
    setFollowUpModalFund(
      matchingFund || {
        id: issue.schemeId,
        schemeId: issue.schemeId,
        schemeOptionId: issue.schemeOptionId,
        schemeCode: issue.schemeCode || String(issue.schemeId),
        schemeName: issue.schemeName,
        planType: issue.planType,
        optionType: issue.optionType,
        amfiCode: issue.amfiCode,
        isin: issue.isin,
        investigationStatus: "NEEDS_FOLLOW_UP",
      }
    );
    setFollowUpModalInitialReassessment(
      Boolean(modalOpts?.initialReassessmentMode || handoff?.originatingAction === "REASSESSMENT_START")
    );
    setFollowUpModalInitialShowTimeline(
      Boolean(modalOpts?.initialShowTimeline || handoff?.originatingAction === "EVIDENCE_HISTORY_REVIEW")
    );
    setFollowUpModalInitialDependencyId(
      modalOpts?.initialDependencyId || handoff?.dependencyId || null
    );
    setIsFollowUpModalOpen(true);

    const effectiveHandoff =
      handoff ||
      createInvestigationHandoff({
        fund: {
          schemeId: issue.schemeId,
          schemeOptionId: issue.schemeOptionId,
          schemeName: issue.schemeName,
          schemeCode: issue.schemeCode,
          planType: issue.planType,
          optionType: issue.optionType,
          amfiCode: issue.amfiCode,
          isin: issue.isin,
        },
        briefSection: (issue.briefSection as any) || "B",
        dimensionTitle: issue.title,
        issue: issue,
        originatingAction: modalOpts?.initialReassessmentMode
          ? "REASSESSMENT_START"
          : modalOpts?.initialShowTimeline
            ? "EVIDENCE_HISTORY_REVIEW"
            : "INVESTIGATION_QUEUE_RESUME",
        reassessmentReason: modalOpts?.initialReassessmentMode
          ? "Explicit investor reassessment initiated from investigation history"
          : undefined,
        priorOutcomeType: issue.priorOutcomeType || issue.outcomeType || undefined,
        priorResolutionNote: issue.priorResolutionNote || issue.resolutionNote || undefined,
        priorEvidenceRunId: issue.priorEvidenceRunId || issue.evidenceRunId || undefined,
        priorEvidenceAsOfDate: issue.priorEvidenceAsOfDate || issue.evidenceAsOfDate || undefined,
      });

  recordMeaningfulSession(
    "BRIEF",
    `Inquiry #${issue.id.slice(-6)}: ${issue.title}`,
    {
      schemeId: issue.schemeId,
      schemeOptionId: issue.schemeOptionId,
      briefSection: (issue.briefSection as any) || "A",
      followUpIssueId: issue.id,
      followUpIssueTitle: issue.title,
      handoffContext: effectiveHandoff,
    }
  );
};

const handleOpenCreateIssueForFund = (
  fund: WatchlistResponse,
  briefSection?: "A" | "B" | "C" | "D" | "E" | "F" | "G",
  checklistKey?: ReviewItemKey,
  title?: string,
  questionOrReason?: string,
  handoff?: InvestigationHandoffContext | null
) => {
  setActiveFollowUpModalIssue(null);
  setFollowUpModalFund(fund);
  setFollowUpModalPrefill({
    briefSection,
    checklistKey,
    title,
    questionOrReason,
  });
  setIsFollowUpModalOpen(true);

  const effectiveHandoff =
    handoff ||
    createInvestigationHandoff({
      fund: {
        schemeId: fund.schemeId,
        schemeOptionId: fund.schemeOptionId,
        schemeName: fund.schemeName,
        planType: fund.planType || undefined,
        optionType: fund.optionType || undefined,
      },
      briefSection: briefSection || "A",
      dimensionKey: checklistKey,
      dimensionTitle: title,
      coverageState: "INVESTIGATION_PENDING",
      originatingAction: "LOG_INQUIRY_FROM_COVERAGE",
    });

  recordMeaningfulSession(
    "BRIEF",
    `New Inquiry: ${title || fund.schemeName}`,
    {
      item: fund,
      briefSection: briefSection || "A",
      handoffContext: effectiveHandoff,
    }
  );
};

const handleNavigateFromIssueToBrief = (
  schemeId: number,
  schemeOptionId?: number | null,
  section?: "coverage" | "checklist" | "A" | "B" | "C" | "D" | "E" | "F" | "G" | "H",
  issue?: ResearchFollowUpIssue,
  handoff?: InvestigationHandoffContext | null
) => {
  const targetFund = watchlistItems.find(
    (w) =>
      w.schemeId === schemeId &&
      (schemeOptionId === undefined ||
        schemeOptionId === null ||
        w.schemeOptionId === schemeOptionId)
  );

  const effectiveHandoff =
    handoff ||
    (targetFund
      ? createInvestigationHandoff({
        fund: {
          schemeId: targetFund.schemeId,
          schemeOptionId: targetFund.schemeOptionId,
          schemeName: targetFund.schemeName,
          planType: targetFund.planType || undefined,
          optionType: targetFund.optionType || undefined,
        },
        briefSection: section || "A",
        dimensionKey: issue?.checklistKey,
        issue: issue,
        originatingAction: issue ? "INVESTIGATION_QUEUE_RESUME" : "COVERAGE_OVERVIEW_TRIAGE",
      })
      : null);

  if (targetFund) {
    setBriefInitialIssueId(issue?.id || null);
    setBriefInitialSection(section || "checklist");
    setBriefFund(targetFund);
    recordMeaningfulSession(
      "BRIEF",
      `Research Brief (${getBriefSectionTitle(section)})`,
      {
        item: targetFund,
        briefSection: section === "checklist" || section === "coverage" ? "A" : section,
        followUpIssueId: issue?.id || null,
        followUpIssueTitle: issue?.title || null,
        handoffContext: effectiveHandoff,
      }
    );
  } else {
    if (effectiveHandoff) {
      saveInvestigationHandoff(effectiveHandoff, user?.sub);
    }
    setViewNotification({
      message: `Notice: Referenced fund option (${issue?.schemeName || "Fund"} #${schemeOptionId || schemeId}) is preserved in your records but not currently available in your active saved workspace. Stored identity is retained without substitution.`,
      kind: "info",
    });
  }
};

const handleOpenIssueById = (issueId: string) => {
  const issue =
    followUpIssues.find((i) => i.id === issueId) ||
    loadAllFollowUpIssues(user?.sub).find((i) => i.id === issueId);
  if (issue) {
    handleOpenIssueModal(issue);
  } else {
    setViewNotification({
      message: `Referenced follow-up inquiry #${issueId.slice(-6)} was not found in your preserved records.`,
      kind: "warning",
    });
  }
};

// Phase F11: Restore saved view from URL query param if present
useEffect(() => {
  if (!isLoaded || savedViews.length === 0) return;
  const viewParam = searchParams.get("view");
  if (viewParam) {
    const match = savedViews.find(
      (v) => v.id === viewParam || v.name.toLowerCase() === viewParam.toLowerCase()
    );
    if (match) {
      setSearchQuery(match.searchQuery);
      setFilterTab(isValidFilterTab(match.filterTab) ? match.filterTab : "ALL");
      setSelectedAmc(match.selectedAmc || "ALL");
      setSortBy(isValidSortOption(match.sortBy) ? match.sortBy : "updated_desc");
      setActiveSavedViewId(match.id);
    }
  }
}, [isLoaded, savedViews, searchParams]);

// Phase F9 & F11: Synchronize search query, filter tab, AMC, sort option, and active saved view to browser URL without full reload
useEffect(() => {
  if (typeof window === "undefined" || !isLoaded) return;
  const params = new URLSearchParams();
  if (searchQuery.trim()) params.set("q", searchQuery.trim());
  if (filterTab !== "ALL") params.set("tab", filterTab);
  if (selectedAmc !== "ALL") params.set("amc", selectedAmc);
  if (sortBy !== "updated_desc") params.set("sort", sortBy);
  if (activeSavedViewId) {
    const match = savedViews.find((v) => v.id === activeSavedViewId);
    if (match && isViewActive(match, currentWorkspaceConfig)) {
      params.set("view", match.id);
    }
  }
  const qs = params.toString();
  const newUrl = qs ? `${window.location.pathname}?${qs}` : window.location.pathname;
  window.history.replaceState(null, "", newUrl);
}, [searchQuery, filterTab, selectedAmc, sortBy, activeSavedViewId, savedViews, currentWorkspaceConfig, isLoaded]);

useEffect(() => {
  let active = true;
  fetchAllSchemes()
    .then((data) => {
      if (active) {
        setSchemes(data);
        setLoadingSchemes(false);
      }
    })
    .catch(() => {
      if (active) {
        setLoadingSchemes(false);
      }
    });

  return () => {
    active = false;
  };
}, []);

// Map watchlist items with catalog enrichment if available
const displayItems: WatchlistResponse[] = useMemo(() => {
  return watchlistItems.map((item) => {
    const scheme = schemes.find((s) => s.id === item.schemeId);
    return {
      ...item,
      schemeName: item.schemeName || scheme?.name || `Scheme #${item.schemeId}`,
      schemeCode: item.schemeCode || scheme?.code || `SCHEME_${item.schemeId}`,
      category: item.category || scheme?.category || null,
      amcName: item.amcName || scheme?.amc?.name || null,
      yukiraScore:
        item.yukiraScore !== undefined && item.yukiraScore !== null
          ? item.yukiraScore
          : (scheme?.yukiraScore?.score ?? null),
      yukiraScoreStatus: item.yukiraScoreStatus || (scheme?.yukiraScore?.status ?? null),
    };
  });
}, [watchlistItems, schemes]);

// Handle cross-workflow deep linking to research brief via query param (e.g. /watchlist?brief=118955&section=G)
useEffect(() => {
  const briefParam = searchParams.get("brief");
  const sectionParam = searchParams.get("section");
  if (briefParam && displayItems.length > 0) {
    const match = displayItems.find(
      (i) =>
        String(i.schemeOptionId) === briefParam ||
        String(i.schemeId) === briefParam ||
        i.amfiCode === briefParam
    );
    if (match) {
      if (sectionParam === "F" || sectionParam === "changes" || sectionParam === "history") {
        setBriefInitialSection("F");
      } else if (sectionParam === "G" || sectionParam === "portfolio" || sectionParam === "holding") {
        setBriefInitialSection("G");
      } else {
        setBriefInitialSection("A");
      }
      setBriefFund(match);
    }
  }
}, [searchParams, displayItems]);

// Unique AMCs extracted from saved items
const uniqueAmcs = useMemo(() => {
  const amcs = new Set<string>();
  displayItems.forEach((d) => {
    if (d.amcName && d.amcName.trim().length > 0) {
      amcs.add(d.amcName.trim());
    }
  });
  return Array.from(amcs).sort((a, b) => a.localeCompare(b));
}, [displayItems]);

// Load evidence snapshots for change detection
const snapshotsMap = useMemo(() => {
  return loadEvidenceSnapshots();
}, [watchlistItems, isLoaded]);

// Pre-calculate evidence change summaries for fast filtering and metric counts
const itemSummariesMap = useMemo(() => {
  const map: Record<string, ReturnType<typeof computeEvidenceChanges>> = {};
  displayItems.forEach((item) => {
    const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
    const snap = snapshotsMap[key] || null;
    map[key] = computeEvidenceChanges(item, null, snap);
  });
  return map;
}, [displayItems, snapshotsMap]);

// Phase F8: Resolve Attention metadata deterministically for all saved items
const resolvedAttentionMap = useMemo(() => {
  const map: Record<string, ResolvedAttentionItem> = {};
  displayItems.forEach((item) => {
    const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
    const evSummary = itemSummariesMap[key] || null;
    map[key] = resolveAttentionItem(item, evSummary);
  });
  return map;
}, [displayItems, itemSummariesMap]);

const allResolvedAttentionItems = useMemo(() => {
  return Object.values(resolvedAttentionMap);
}, [resolvedAttentionMap]);

const attentionSummary = useMemo(() => {
  return computeResearchAttentionSummary(allResolvedAttentionItems);
}, [allResolvedAttentionItems]);

// Phase F13: Pre-calculate Research Coverage & Investigation Completeness across 8 dimensions
const recordCompletenessMap = useMemo(() => {
  const map: Record<string, RecordCompletenessResolution> = {};
  displayItems.forEach((item) => {
    const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
    const evSummary = itemSummariesMap[key] || null;
    map[key] = resolveRecordCompleteness(item, evSummary);
  });
  return map;
}, [displayItems, itemSummariesMap]);

const completenessSummary = useMemo(() => {
  return computeInvestigationCompletenessSummary(Object.values(recordCompletenessMap));
}, [recordCompletenessMap]);

// Phase F14: Pre-calculate Review Checklist Progress across all saved funds
const recordReviewProgressMap = useMemo<Record<string, ChecklistProgressSummary>>(() => {
  const allChecklists = loadAllReviewChecklists(user?.sub);
  const map: Record<string, ChecklistProgressSummary> = {};
  displayItems.forEach((item) => {
    const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
    const chk = allChecklists[key] || null;
    const evSummary = itemSummariesMap[key] || null;
    map[key] = computeChecklistProgress(chk, item, evSummary, null);
  });
  return map;
}, [displayItems, itemSummariesMap, user?.sub, reviewChecklistVersion]);

// Phase F17: Count of funds with sections requiring renewed evidence-drift reassessment
const fundsNeedingReassessmentCount = useMemo(() => {
  return Object.values(recordReviewProgressMap).filter((p) => (p.reassessmentNeededCount || 0) > 0).length;
}, [recordReviewProgressMap]);

// Phase F15 & F20: Pre-calculate fund follow-up issues map for exact scheme-option retrieval
const fundIssuesMap = useMemo(() => {
  const map = new Map<string, ResearchFollowUpIssue[]>();
  for (const item of displayItems) {
    const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
    const issues = followUpIssues.filter(
      (iss) =>
        iss.schemeId === item.schemeId &&
        (iss.schemeOptionId === undefined || iss.schemeOptionId === null || iss.schemeOptionId === item.schemeOptionId)
    );
    map.set(key, issues);
  }
  return map;
}, [displayItems, followUpIssues]);

// Phase F15: Pre-calculate fund open follow-up issues count map
const fundOpenIssuesCountMap = useMemo(() => {
  const map = new Map<string, number>();
  for (const issue of followUpIssues) {
    if (issue.status === "OPEN" || issue.status === "IN_PROGRESS" || issue.status === "WAITING") {
      const key = getWatchlistItemKey(issue.schemeId, issue.schemeOptionId);
      map.set(key, (map.get(key) || 0) + 1);
    }
  }
  return map;
}, [followUpIssues]);

const fundsWithFollowUpCount = useMemo(() => {
  return displayItems.filter((item) => {
    const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
    return (fundOpenIssuesCountMap.get(key) || 0) > 0;
  }).length;
}, [displayItems, fundOpenIssuesCountMap]);

// Phase F20: Count of funds with follow-up issues containing explicit residual uncertainty
const fundsWithResidualUncertaintyCount = useMemo(() => {
  return displayItems.filter((item) => {
    const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
    const issues = fundIssuesMap.get(key) || [];
    return issues.some((iss) => Boolean(iss.residualUncertainty && iss.residualUncertainty.trim().length > 0));
  }).length;
}, [displayItems, fundIssuesMap]);

// Phase F26: Count of funds with actionable coverage gaps across the 8 diligence dimensions
const fundsWithActionableGapsCount = useMemo(() => {
  const allChecklists = loadAllReviewChecklists(user?.sub);
  return displayItems.filter((item) => {
    const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
    const chk = allChecklists[key] || null;
    const issues = fundIssuesMap.get(key) || [];
    const ev = itemSummariesMap[key] || null;
    const report = evaluateFundResearchCoverage(item, issues, chk, ev);
    return report.hasActionableGaps;
  }).length;
}, [displayItems, fundIssuesMap, itemSummariesMap, user?.sub, reviewChecklistVersion, followUpRefreshVersion]);

// Phase F9 & F20: Pre-calculate Unified Search Results across all 15+ stored fields and follow-up records
const searchResultsMap = useMemo(() => {
  const map: Record<string, ResearchSearchResult> = {};
  displayItems.forEach((item) => {
    const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
    const resAttention = resolvedAttentionMap[key] || null;
    const evSummary = itemSummariesMap[key] || null;
    const itemIssues = fundIssuesMap.get(key) || [];
    map[key] = evaluateResearchItemSearch(item, searchQuery, resAttention, evSummary, itemIssues);
  });
  return map;
}, [displayItems, searchQuery, resolvedAttentionMap, itemSummariesMap, fundIssuesMap]);

// Combined Filtering and Sorting
const filteredAndSortedItems = useMemo(() => {
  const q = searchQuery.toLowerCase().trim();
  let result = displayItems;

  // 1. Tab Filter
  if (filterTab === "ATTENTION_QUEUE") {
    result = result.filter((item) => {
      const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
      return resolvedAttentionMap[key]?.isInAttentionQueue;
    });
  } else if (filterTab === "INCOMPLETE_RECORDS") {
    result = result.filter((item) => {
      const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
      return !recordCompletenessMap[key]?.isDocumentationComplete;
    });
  } else if (filterTab === "MISSING_QUESTION") {
    result = result.filter((item) => {
      const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
      return !recordCompletenessMap[key]?.dimensions.QUESTION.isRecorded;
    });
  } else if (filterTab === "MISSING_NEXT_STEP") {
    result = result.filter((item) => {
      const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
      return !recordCompletenessMap[key]?.dimensions.NEXT_STEP.isRecorded;
    });
  } else if (filterTab === "MISSING_SUPPORTING_EVIDENCE") {
    result = result.filter((item) => {
      const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
      return !recordCompletenessMap[key]?.dimensions.SUPPORTING_EVIDENCE.isRecorded;
    });
  } else if (filterTab === "MISSING_RECONSIDERATION_CONDITIONS") {
    result = result.filter((item) => {
      const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
      return !recordCompletenessMap[key]?.dimensions.RECONSIDERATION_CONDITIONS.isRecorded;
    });
  } else if (filterTab === "SHORTLIST_SELECTED") {
    result = result.filter((item) => selectedForCompare.includes(getWatchlistItemKey(item.schemeId, item.schemeOptionId)));
  } else if (filterTab === "OPTION_MISMATCH") {
    result = result.filter((item) => Boolean(item.heldDifferentVariantNotice));
  } else if (filterTab === "STALE_EVIDENCE") {
    result = result.filter((item) => item.latestNavDate === "2024-01-15" || !item.latestNavDate);
  } else if (filterTab === "WITHOUT_THESIS") {
    result = result.filter((item) => {
      const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
      return !recordCompletenessMap[key]?.dimensions.THESIS.isRecorded;
    });
  } else if (filterTab === "WITH_THESIS") {
    result = result.filter((item) => {
      const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
      return recordCompletenessMap[key]?.dimensions.THESIS.isRecorded;
    });
  } else if (filterTab === "NEEDS_FOLLOW_UP") {
    result = result.filter((item) => (item.investigationStatus || "TO_INVESTIGATE") === "NEEDS_FOLLOW_UP");
  } else if (filterTab === "TO_INVESTIGATE") {
    result = result.filter((item) => (item.investigationStatus || "TO_INVESTIGATE") === "TO_INVESTIGATE");
  } else if (filterTab === "IN_PROGRESS") {
    result = result.filter((item) => item.investigationStatus === "IN_PROGRESS");
  } else if (filterTab === "REVIEWED") {
    result = result.filter((item) => item.investigationStatus === "REVIEWED");
  } else if (filterTab === "EVIDENCE_CHANGED") {
    result = result.filter((item) => {
      const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
      return itemSummariesMap[key]?.status === "EVIDENCE_CHANGED";
    });
  } else if (filterTab === "REVIEW_NEEDED") {
    result = result.filter((item) => {
      const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
      return itemSummariesMap[key]?.requiresReview;
    });
  } else if (filterTab === "HISTORICAL_UNAVAILABLE") {
    result = result.filter((item) => {
      const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
      return itemSummariesMap[key]?.status === "HISTORICAL_UNAVAILABLE";
    });
  } else if (filterTab === "COMPARATIVE") {
    result = result.filter(isComparativeItem);
  } else if (filterTab === "HELD_IN_PORTFOLIO") {
    result = result.filter((item) => Boolean(item.isHeldInPortfolio));
  } else if (filterTab === "QUESTIONS") {
    result = result.filter((item) => Boolean(item.investigationQuestion && item.investigationQuestion.trim().length > 0));
  } else if (filterTab === "NOTES") {
    result = result.filter((item) => Boolean(item.reasonSaved || item.investigationQuestion || item.nextStep || item.workingThesis));
  } else if (filterTab === "DIRECT_GROWTH") {
    result = result.filter(
      (item) =>
        (item.planType || "DIRECT").toUpperCase() === "DIRECT" &&
        (item.optionType || "GROWTH").toUpperCase() === "GROWTH"
    );
  } else if (filterTab === "REGULAR_PLANS") {
    result = result.filter(
      (item) =>
        (item.planType || "").toUpperCase() === "REGULAR" ||
        (item.optionType || "").toUpperCase() === "IDCW"
    );
  } else if (filterTab === "WITH_FOLLOW_UP_ISSUES") {
    result = result.filter((item) => {
      const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
      return (fundOpenIssuesCountMap.get(key) || 0) > 0;
    });
  } else if (filterTab === "WITH_RESIDUAL_UNCERTAINTY") {
    result = result.filter((item) => {
      const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
      const issues = fundIssuesMap.get(key) || [];
      return issues.some((iss) => Boolean(iss.residualUncertainty && iss.residualUncertainty.trim().length > 0));
    });
  } else if (filterTab === "INVESTIGATION_ACTION_QUEUE") {
    result = result.filter((item) => {
      const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
      const issues = fundIssuesMap.get(key) || [];
      return issues.some(
        (iss) =>
          iss.status === "OPEN" ||
          iss.status === "IN_PROGRESS" ||
          iss.status === "WAITING"
      );
    });
  } else if (filterTab === "ACTIONABLE_COVERAGE_GAPS") {
    const allChecklists = loadAllReviewChecklists(user?.sub);
    result = result.filter((item) => {
      const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
      const chk = allChecklists[key] || null;
      const issues = fundIssuesMap.get(key) || [];
      const ev = itemSummariesMap[key] || null;
      const report = evaluateFundResearchCoverage(item, issues, chk, ev);
      return report.hasActionableGaps;
    });
  }

  // 2. AMC Filter
  if (selectedAmc !== "ALL") {
    result = result.filter((item) => item.amcName === selectedAmc);
  }

  // 3. Search Query Filter (via Unified Research Search Engine across 15+ stored fields)
  if (q) {
    result = result.filter((item) => {
      const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
      return searchResultsMap[key]?.isMatch;
    });
  }

  // 4. Sorting
  return [...result].sort((a, b) => {
    if (sortBy === "attention_priority" || (filterTab === "ATTENTION_QUEUE" && sortBy === "updated_desc")) {
      const resA = resolvedAttentionMap[getWatchlistItemKey(a.schemeId, a.schemeOptionId)];
      const resB = resolvedAttentionMap[getWatchlistItemKey(b.schemeId, b.schemeOptionId)];
      if (resA && resB) {
        return compareAttentionItems(resA, resB);
      }
    }
    if (sortBy === "needs_followup_first") {
      const aFollowup = (a.investigationStatus || "TO_INVESTIGATE") === "NEEDS_FOLLOW_UP";
      const bFollowup = (b.investigationStatus || "TO_INVESTIGATE") === "NEEDS_FOLLOW_UP";
      if (aFollowup && !bFollowup) return -1;
      if (!aFollowup && bFollowup) return 1;
      return a.schemeName.localeCompare(b.schemeName);
    }
    if (sortBy === "to_investigate_first") {
      const aToInv = (a.investigationStatus || "TO_INVESTIGATE") === "TO_INVESTIGATE";
      const bToInv = (b.investigationStatus || "TO_INVESTIGATE") === "TO_INVESTIGATE";
      if (aToInv && !bToInv) return -1;
      if (!aToInv && bToInv) return 1;
      return a.schemeName.localeCompare(b.schemeName);
    }
    if (sortBy === "updated_desc") {
      const timeA = a.updatedAt ? new Date(a.updatedAt).getTime() : 0;
      const timeB = b.updatedAt ? new Date(b.updatedAt).getTime() : 0;
      if (timeB !== timeA) return timeB - timeA;
      return a.schemeName.localeCompare(b.schemeName);
    }
    if (sortBy === "created_desc") {
      const timeA = a.createdAt ? new Date(a.createdAt).getTime() : 0;
      const timeB = b.createdAt ? new Date(b.createdAt).getTime() : 0;
      if (timeB !== timeA) return timeB - timeA;
      return a.schemeName.localeCompare(b.schemeName);
    }
    if (sortBy === "notes_first") {
      const aHasNote = Boolean(a.reasonSaved || a.investigationQuestion || a.nextStep);
      const bHasNote = Boolean(b.reasonSaved || b.investigationQuestion || b.nextStep);
      if (aHasNote && !bHasNote) return -1;
      if (!aHasNote && bHasNote) return 1;
      return a.schemeName.localeCompare(b.schemeName);
    }
    if (sortBy === "name_asc") return a.schemeName.localeCompare(b.schemeName);
    if (sortBy === "name_desc") return b.schemeName.localeCompare(a.schemeName);
    return 0;
  });
}, [displayItems, filterTab, selectedAmc, searchQuery, sortBy, itemSummariesMap, resolvedAttentionMap, searchResultsMap, selectedForCompare]);

const toggleCompareSelection = (itemKey: string) => {
  setSelectedForCompare((prev) => {
    if (prev.includes(itemKey)) {
      return prev.filter((k) => k !== itemKey);
    }
    if (prev.length >= 4) {
      return prev;
    }
    return [...prev, itemKey];
  });
};

// Phase F12 & F27: Record meaningful research activity without redundant writes
const recordMeaningfulSession = (
  destination: "BRIEF" | "NOTE" | "PROFILE" | "COMPARE" | "WORKSPACE",
  destinationLabel: string,
  opts?: {
    item?: WatchlistResponse | null;
    schemeId?: number;
    schemeOptionId?: number | null;
    briefSection?: "coverage" | "checklist" | "A" | "B" | "C" | "D" | "E" | "F" | "G" | "H";
    followUpIssueId?: string | null;
    followUpIssueTitle?: string | null;
    comparisonCandidateKeys?: string[];
    searchQuery?: string;
    filterTab?: FilterTab;
    selectedAmc?: string;
    sortBy?: SortOption;
    savedViewId?: string | null;
    savedViewName?: string | null;
    handoffContext?: InvestigationHandoffContext | null;
  }
) => {
  const session: ResumableResearchSession = {
    id: `sess-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`,
    destination,
    destinationLabel,
    schemeId: opts?.item?.schemeId ?? opts?.schemeId,
    schemeOptionId: opts?.item?.schemeOptionId ?? opts?.schemeOptionId,
    schemeCode: opts?.item?.schemeCode,
    schemeName: opts?.item?.schemeName,
    category: opts?.item?.category,
    amcName: opts?.item?.amcName,
    planType: opts?.item?.planType,
    optionType: opts?.item?.optionType,
    amfiCode: opts?.item?.amfiCode,
    isin: opts?.item?.isin,
    briefSection: opts?.briefSection,
    followUpIssueId: opts?.followUpIssueId,
    followUpIssueTitle: opts?.followUpIssueTitle,
    comparisonCandidateKeys: opts?.comparisonCandidateKeys,
    searchQuery: opts?.searchQuery ?? (searchQuery.trim() || undefined),
    filterTab: opts?.filterTab ?? filterTab,
    selectedAmc: opts?.selectedAmc ?? (selectedAmc !== "ALL" ? selectedAmc : undefined),
    sortBy: opts?.sortBy ?? sortBy,
    savedViewId: opts?.savedViewId ?? activeSavedViewId,
    savedViewName: opts?.savedViewName,
    handoffContext: opts?.handoffContext !== undefined ? opts.handoffContext : undefined,
    updatedAt: new Date().toISOString(),
  };
  saveRecentResearchSession(session, user?.sub);
  if (opts?.handoffContext) {
    saveInvestigationHandoff(opts.handoffContext, user?.sub);
  }
  setRecentSession(session);
};

const handleOpenBrief = (
  item: WatchlistResponse,
  section: "coverage" | "checklist" | "A" | "B" | "C" | "D" | "E" | "F" | "G" | "H" = "A",
  issueId?: string | null,
  handoff?: InvestigationHandoffContext | null
) => {
  setBriefInitialIssueId(issueId || null);
  setBriefInitialSection(section);
  setBriefFund(item);

  const effectiveHandoff =
    handoff ||
    createInvestigationHandoff({
      fund: {
        schemeId: item.schemeId,
        schemeOptionId: item.schemeOptionId,
        schemeName: item.schemeName,
        planType: item.planType || undefined,
        optionType: item.optionType || undefined,
      },
      briefSection: section,
      coverageState: "INVESTIGATION_PENDING",
      originatingAction: "COVERAGE_OVERVIEW_TRIAGE",
    });

  recordMeaningfulSession(
    "BRIEF",
    section === "coverage"
      ? "Research Brief (Coverage & Gaps)"
      : section === "checklist"
        ? "Research Brief (Review Checklist)"
        : `Research Brief (${getBriefSectionTitle(section)})`,
    {
      item,
      briefSection: section,
      followUpIssueId: issueId || null,
      handoffContext: effectiveHandoff,
    }
  );
};

const handleOpenEditNote = (item: WatchlistResponse) => {
  setEditingFund(item);
  recordMeaningfulSession("NOTE", "Due Diligence Notes & Thesis", { item });
};

const handleResumeRecentSession = (session: ResumableResearchSession) => {
  const linkedIssueId = session.handoffContext?.followUpIssueId || session.followUpIssueId;

  const match = displayItems.find((item) =>
    session.schemeOptionId !== undefined && session.schemeOptionId !== null
      ? item.schemeOptionId === session.schemeOptionId
      : item.schemeId === session.schemeId
  );

  // Section 2.B Strict Resumption Hierarchy
  // Tier 1: Exact existing follow-up issue when linked
  if (linkedIssueId) {
    const matchIssue =
      followUpIssues.find((i) => i.id === linkedIssueId) ||
      loadAllFollowUpIssues(user?.sub).find((i) => i.id === linkedIssueId);
    if (matchIssue) {
      handleOpenIssueModal(matchIssue, session.handoffContext);
      return;
    }
  }

  if (session.destination === "BRIEF" || session.handoffContext) {
    // Tier 2 & 3: Match in saved workspace -> navigate to Brief with section / coverage / dimension
    if (match) {
      const targetSection =
        session.handoffContext?.briefSection || session.briefSection || "coverage";
      handleOpenBrief(match, targetSection, linkedIssueId, session.handoffContext);
      return;
    }

    // If fund is no longer in active saved workspace, check public profile fallback preserving identity
    if (session.schemeId) {
      setViewNotification({
        message: `Notice: "${session.schemeName || "Fund"}" (#${session.schemeOptionId || session.schemeId}) is preserved in your records but not currently in your active saved workspace. Navigating to verified profile.`,
        kind: "info",
      });
      router.push(
        `/funds/${session.schemeId}${session.schemeOptionId ? `?option=${session.schemeOptionId}` : ""}`
      );
      return;
    }

    setViewNotification({
      message: "Unable to resume investigation: the referenced scheme option identity is unavailable.",
      kind: "warning",
    });
    return;
  }

  if (session.destination === "NOTE") {
    if (match) {
      handleOpenEditNote(match);
    } else if (session.schemeId) {
      setViewNotification({
        message: `Notice: "${session.schemeName || "Fund"}" (#${session.schemeOptionId || session.schemeId}) is preserved in records but no longer in your active saved workspace. Navigating to verified profile.`,
        kind: "info",
      });
      router.push(
        `/funds/${session.schemeId}${session.schemeOptionId ? `?option=${session.schemeOptionId}` : ""}`
      );
    } else {
      setViewNotification({
        message: "Unable to resume notes: fund record is not available.",
        kind: "warning",
      });
    }
    return;
  }

  if (session.destination === "PROFILE") {
    if (session.schemeId) {
      router.push(
        `/funds/${session.schemeId}${session.schemeOptionId ? `?option=${session.schemeOptionId}` : ""}`
      );
    } else {
      setViewNotification({
        message: "Unable to open profile: missing scheme identity.",
        kind: "warning",
      });
    }
    return;
  }

  if (session.destination === "COMPARE") {
    if (session.comparisonCandidateKeys && session.comparisonCandidateKeys.length >= 2) {
      const matched = displayItems.filter((d) =>
        session.comparisonCandidateKeys?.includes(getWatchlistItemKey(d.schemeId, d.schemeOptionId))
      );
      if (matched.length >= 2) {
        handleLaunchCompareWithItems(matched);
      } else {
        router.push("/compare");
      }
    } else {
      router.push("/compare");
    }
    return;
  }

  // Tier 4: General research workspace fallback
  if (session.destination === "WORKSPACE") {
    if (session.searchQuery !== undefined) setSearchQuery(session.searchQuery);
    if (session.filterTab && isValidFilterTab(session.filterTab)) setFilterTab(session.filterTab);
    if (session.selectedAmc) setSelectedAmc(session.selectedAmc);
    if (session.sortBy && isValidSortOption(session.sortBy)) setSortBy(session.sortBy);
    if (session.savedViewId) setActiveSavedViewId(session.savedViewId);
    setViewNotification({
      message: "Restored previous workspace search and filter configuration.",
      kind: "success",
    });
  }
};

const handleClearRecentSession = () => {
  clearRecentResearchSession(user?.sub);
  clearInvestigationHandoff(user?.sub);
  setRecentSession(null);
  setViewNotification({
    message: "Recent research session context was cleared.",
    kind: "info",
  });
};

const handleLaunchCompare = () => {
  if (selectedForCompare.length < 2) return;
  const selectedObjects = displayItems.filter((d) =>
    selectedForCompare.includes(getWatchlistItemKey(d.schemeId, d.schemeOptionId))
  );
  const ids = selectedObjects.map((d) => d.schemeId).join(",");
  const optMappings = selectedObjects
    .map((d) => (d.schemeOptionId ? `${d.schemeId}:${d.schemeOptionId}` : null))
    .filter(Boolean)
    .join(",");
  const optIds = selectedObjects
    .map((d) => d.schemeOptionId)
    .filter(Boolean)
    .join(",");

  const query = optMappings
    ? `/compare?funds=${ids}&options=${encodeURIComponent(optMappings)}&ids=${optIds}&tab=compare`
    : `/compare?funds=${ids}&ids=${optIds}&tab=compare`;
  recordMeaningfulSession("COMPARE", `Side-by-Side Evidence Comparison (${selectedObjects.length} candidates)`, {
    comparisonCandidateKeys: selectedForCompare,
  });
  router.push(query);
};

const comparativeItems = useMemo(() => {
  return displayItems.filter(isComparativeItem);
}, [displayItems]);

const comparativeCount = comparativeItems.length;

const handleSelectComparativeShortlist = () => {
  const keys = comparativeItems
    .slice(0, 4)
    .map((d) => getWatchlistItemKey(d.schemeId, d.schemeOptionId));
  setSelectedForCompare(keys);
};

// Phase F10: Bulk multi-selection helpers and handlers
const visibleItemKeys = useMemo(() => {
  return filteredAndSortedItems.map((item) =>
    getWatchlistItemKey(item.schemeId, item.schemeOptionId)
  );
}, [filteredAndSortedItems]);

const selectedObjects = useMemo(() => {
  return displayItems.filter((item) =>
    selectedItemKeys.includes(getWatchlistItemKey(item.schemeId, item.schemeOptionId))
  );
}, [displayItems, selectedItemKeys]);

const toggleItemSelection = (itemKey: string) => {
  setSelectedItemKeys((prev) =>
    prev.includes(itemKey) ? prev.filter((k) => k !== itemKey) : [...prev, itemKey]
  );
};

const handleSelectAllVisible = () => {
  setSelectedItemKeys((prev) => {
    const combined = new Set([...prev, ...visibleItemKeys]);
    return Array.from(combined);
  });
};

const handleClearSelection = () => {
  setSelectedItemKeys([]);
};

const handleReconcileToVisible = () => {
  setSelectedItemKeys((prev) => prev.filter((k) => visibleItemKeys.includes(k)));
};

const handleDeselectKeys = (keys: string[]) => {
  setSelectedItemKeys((prev) => prev.filter((k) => !keys.includes(k)));
};

const handleLaunchCompareWithItems = (items: WatchlistResponse[]) => {
  if (items.length < 2) return;
  const ids = items.map((d) => d.schemeId).join(",");
  const optMappings = items
    .map((d) => (d.schemeOptionId ? `${d.schemeId}:${d.schemeOptionId}` : null))
    .filter(Boolean)
    .join(",");
  const optIds = items
    .map((d) => d.schemeOptionId)
    .filter(Boolean)
    .join(",");

  const query = optMappings
    ? `/compare?funds=${ids}&options=${encodeURIComponent(optMappings)}&ids=${optIds}&tab=compare`
    : `/compare?funds=${ids}&ids=${optIds}&tab=compare`;
  recordMeaningfulSession("COMPARE", `Side-by-Side Evidence Comparison (${items.length} candidates)`, {
    comparisonCandidateKeys: items.map((d) => getWatchlistItemKey(d.schemeId, d.schemeOptionId)),
  });
  router.push(query);
};

const handleApplySavedView = (view: SavedResearchView) => {
  if (selectedItemKeys.length > 0) {
    setSelectedItemKeys([]);
    setViewNotification({
      message: `Loaded view "${view.name}". Workspace selection was cleared to prevent accidental actions on hidden records.`,
      kind: "info",
    });
  } else {
    setViewNotification({
      message: `Loaded view "${view.name}".`,
      kind: "success",
    });
  }

  setSearchQuery(view.searchQuery);
  setFilterTab(isValidFilterTab(view.filterTab) ? view.filterTab : "ALL");
  setSelectedAmc(view.selectedAmc || "ALL");
  setSortBy(isValidSortOption(view.sortBy) ? view.sortBy : "updated_desc");
  setActiveSavedViewId(view.id);
};

const handleResetDefaultView = () => {
  if (selectedItemKeys.length > 0) {
    setSelectedItemKeys([]);
    setViewNotification({
      message: "Reset to default workspace view. Selection cleared.",
      kind: "info",
    });
  } else {
    setViewNotification({
      message: "Reset workspace to All Funds (default view).",
      kind: "info",
    });
  }
  setSearchQuery(DEFAULT_VIEW_CONFIG.searchQuery);
  setFilterTab(DEFAULT_VIEW_CONFIG.filterTab);
  setSelectedAmc(DEFAULT_VIEW_CONFIG.selectedAmc);
  setSortBy(DEFAULT_VIEW_CONFIG.sortBy);
  setActiveSavedViewId(null);
};

const handleConfirmRemoval = async (schemeId: number, schemeOptionId?: number | null) => {
  await removeFromWatchlist(schemeId, schemeOptionId);
  const removedKey = getWatchlistItemKey(schemeId, schemeOptionId);
  setSelectedItemKeys((prev) => prev.filter((k) => k !== removedKey));
  setSelectedForCompare((prev) => prev.filter((k) => k !== removedKey));
  setConfirmingDeleteKey(null);
};

const needsFollowUpCount = useMemo(() => {
  return displayItems.filter((i) => (i.investigationStatus || "TO_INVESTIGATE") === "NEEDS_FOLLOW_UP").length;
}, [displayItems]);

const toInvestigateCount = useMemo(() => {
  return displayItems.filter((i) => (i.investigationStatus || "TO_INVESTIGATE") === "TO_INVESTIGATE").length;
}, [displayItems]);

const inProgressCount = useMemo(() => {
  return displayItems.filter((i) => i.investigationStatus === "IN_PROGRESS").length;
}, [displayItems]);

const reviewedCount = useMemo(() => {
  return displayItems.filter((i) => i.investigationStatus === "REVIEWED").length;
}, [displayItems]);

const questionsCount = useMemo(() => {
  return displayItems.filter((i) => Boolean(i.investigationQuestion && i.investigationQuestion.trim().length > 0)).length;
}, [displayItems]);

const heldInPortfolioCount = useMemo(() => {
  return displayItems.filter((i) => Boolean(i.isHeldInPortfolio)).length;
}, [displayItems]);

const notesCount = useMemo(() => {
  return displayItems.filter((i) => i.reasonSaved || i.investigationQuestion || i.nextStep || i.workingThesis).length;
}, [displayItems]);

const thesisCount = useMemo(() => {
  return displayItems.filter((i) => Boolean(i.workingThesis && i.workingThesis.trim().length > 0)).length;
}, [displayItems]);

const missingQuestionCount = completenessSummary.missingQuestionCount;
const missingNextStepCount = completenessSummary.missingNextStepCount;
const missingSupportingEvidenceCount = completenessSummary.missingSupportingEvidenceCount;
const missingReconsiderationCount = completenessSummary.missingReconsiderationCount;

const optionMismatchCount = useMemo(() => {
  return displayItems.filter((i) => Boolean(i.heldDifferentVariantNotice)).length;
}, [displayItems]);

const shortlistSelectedCount = selectedForCompare.length;

const scoredCount = useMemo(() => {
  return displayItems.filter(
    (i) => i.yukiraScore !== null && i.yukiraScore !== undefined && i.yukiraScoreStatus !== "INSUFFICIENT_DATA"
  ).length;
}, [displayItems]);

const evidenceChangedCount = useMemo(() => {
  return Object.values(itemSummariesMap).filter((s) => s.status === "EVIDENCE_CHANGED").length;
}, [itemSummariesMap]);

const reviewNeededCount = useMemo(() => {
  return Object.values(itemSummariesMap).filter((s) => s.requiresReview).length;
}, [itemSummariesMap]);

const historicalUnavailableCount = useMemo(() => {
  return Object.values(itemSummariesMap).filter((s) => s.status === "HISTORICAL_UNAVAILABLE").length;
}, [itemSummariesMap]);

return (
  <PageContainer
    title="Investor Research Workspace"
    subtitle="Organized mutual fund investigations, persistent research hypotheses, due-diligence questions, and point-in-time evidence comparison."
    breadcrumbs={[
      { label: "Home", href: "/" },
      { label: "Research Workspace", href: "/watchlist" },
    ]}
    action={
      <div className="flex items-center gap-2">
        <Link href="/funds" className="btn btn-secondary">
          <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
          <span>Discover Funds</span>
        </Link>
        <button
          onClick={() => refreshWatchlist()}
          disabled={!isLoaded}
          className="btn btn-secondary"
          title="Refresh saved funds and synchronize research notes"
        >
          <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
          <span>Refresh</span>
        </button>
      </div>
    }
  >
    {/* Onboarding Workflow Guide */}
    <InvestorOnboardingGuide className="mb-6" />

    {/* Guest Mode Persistence Banner */}
    {syncStatus === "LOCAL_ONLY" && (
      <div className="panel mb-6 p-4 bg-surface-navy-raised/60 border border-border-gold-subtle rounded-lg flex flex-col md:flex-row md:items-center justify-between gap-3">
        <div className="flex items-start gap-3">
          <span className="flex h-7 w-7 items-center justify-center rounded-full bg-accent/20 text-xs font-bold text-accent shrink-0 mt-0.5">
            💾
          </span>
          <div>
            <div className="flex items-center gap-2">
              <span className="status-badge state-candidate text-[10px] font-mono">BROWSER STORAGE</span>
              <h3 className="text-xs font-semibold text-text-cream-bright">
                Local Workspace Mode
              </h3>
            </div>
            <p className="text-xs text-text-cream-muted mt-1 leading-relaxed">
              Your saved funds and due-diligence hypotheses are preserved in this browser. Sign in to permanently synchronize your research to an authenticated investor account across devices.
            </p>
          </div>
        </div>
        <a
          href="/auth/login?returnTo=/watchlist"
          className="rounded-full bg-gradient-to-r from-[#d4af37] via-[#f2ca50] to-[#e6be44] px-4 py-1.5 text-xs font-semibold text-[#1e1704] transition-all hover:scale-[1.02] shrink-0 text-center"
        >
          Sign In to Sync Research
        </a>
      </div>
    )}

    {/* Persistence Status Alert if Sync Failed */}
    {syncStatus === "SYNC_FAILED" && (
      <div className="panel mb-6 p-4 bg-critical-bg/15 border border-critical-border rounded-lg flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2.5">
          <span className="status-badge state-rejected text-[11px]">SYNC FAILED</span>
          <span className="text-[12.5px] text-text-primary">
            {error || "Backend write failed. Your research notes and saved funds are preserved safely in browser local storage."}
          </span>
        </div>
        <button
          onClick={() => refreshWatchlist()}
          className="btn btn-secondary btn-sm"
        >
          Retry Server Sync
        </button>
      </div>
    )}

    {/* Unified Evidence Quality & Research Integrity Banner */}
    <div className="mb-6">
      <EvidenceQualitySummary
        variant="banner"
        title="Saved Research Evidence Freshness & Historical Audit Disclosures"
        freshnessState="STALE"
        asOfDate="2024-01-15"
        methodologyStatus="CANDIDATE"
        interpretationConsequence={
          <span>
            <strong>Research Integrity Disclosures:</strong> Saving a fund preserves your due-diligence hypotheses and point-in-time notes. The canonical pilot dataset evaluates observations through <strong>2024-01-15</strong>. Reopening a saved fund profile re-evaluates fresh point-in-time calculation runs under the active market knowledge cutoff.
          </span>
        }
        nextStepLabel="Launch Comparative Screener"
        nextStepHref="/compare"
      />
    </div>

    {/* Phase F8: Research Attention Workspace & Priority Queue Summary */}
    <ResearchAttentionSummary
      summary={attentionSummary}
      activeTab={filterTab}
      onSelectTab={(tab) => {
        setFilterTab(tab);
        if (tab === "ATTENTION_QUEUE") {
          setSortBy("attention_priority");
        }
      }}
    />

    {/* Phase F12 & F27: Resumable Recent Research Investigation Banner with Continuity */}
    <RecentSessionBanner
      session={recentSession}
      savedItems={displayItems}
      onResumeSession={handleResumeRecentSession}
      onClearSession={handleClearRecentSession}
      onOpenIssueById={handleOpenIssueById}
    />

    {/* Workspace Summary Metric Strip */}
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-7 mb-6">
      <div className="panel p-3 bg-surface">
        <span className="eyebrow block">Saved Investigations</span>
        <p className="data-value-lg mt-1 font-mono">{displayItems.length}</p>
        <span className="mono-meta text-[11px] block mt-0.5">Tracked scheme options</span>
      </div>

      <div className="panel p-3 bg-surface border-l-2 border-l-rose-500/80">
        <span className="eyebrow block text-rose-400">Needs Follow-Up</span>
        <p className="data-value-lg mt-1 font-mono text-rose-400">{needsFollowUpCount}</p>
        <span className="mono-meta text-[11px] block mt-0.5">Stale evidence / inquiries</span>
      </div>

      <div className="panel p-3 bg-surface border-l-2 border-l-amber-500/80">
        <span className="eyebrow block text-amber-400">To Investigate</span>
        <p className="data-value-lg mt-1 font-mono text-amber-400">{toInvestigateCount}</p>
        <span className="mono-meta text-[11px] block mt-0.5">Awaiting initial review</span>
      </div>

      <div className="panel p-3 bg-surface border-l-2 border-l-sky-500/80">
        <span className="eyebrow block text-sky-400">In Progress</span>
        <p className="data-value-lg mt-1 font-mono text-sky-400">{inProgressCount}</p>
        <span className="mono-meta text-[11px] block mt-0.5">Active diligence</span>
      </div>

      <div className="panel p-3 bg-surface border-l-2 border-l-emerald-500/80">
        <span className="eyebrow block text-emerald-400">Reviewed</span>
        <p className="data-value-lg mt-1 font-mono text-emerald-400">{reviewedCount}</p>
        <span className="mono-meta text-[11px] block mt-0.5">Evidence inspected</span>
      </div>

      <div className={`panel p-3 bg-surface ${evidenceChangedCount > 0 || fundsNeedingReassessmentCount > 0 ? "border-l-2 border-l-amber-500/80" : ""}`}>
        <span className="eyebrow block text-amber-400">Evidence Changes</span>
        <p className="data-value-lg mt-1 font-mono text-amber-400">{evidenceChangedCount}</p>
        <span className="mono-meta text-[11px] block mt-0.5">
          {fundsNeedingReassessmentCount > 0
            ? `${fundsNeedingReassessmentCount} reassessment needed`
            : reviewNeededCount > 0
              ? `${reviewNeededCount} review needed`
              : `${historicalUnavailableCount} initial baseline`}
        </span>
      </div>

      <div className="panel p-3 bg-surface">
        <span className="eyebrow block">Held in Portfolio</span>
        <p className="data-value-lg mt-1 font-mono text-emerald-400">
          {heldInPortfolioCount} <span className="text-text-tertiary text-sm font-normal">/ {displayItems.length}</span>
        </p>
        <span className="mono-meta text-[11px] block mt-0.5">Matching portfolio positions</span>
      </div>
    </div>

    {/* Phase F13: Investigation Documentation Coverage & Completeness Overview */}
    <InvestigationCompletenessOverview
      summary={completenessSummary}
      activeTab={filterTab}
      onSelectTab={(tab) => setFilterTab(tab)}
    />

    {/* Phase F26 & F27: Cross-Fund Research Coverage Overview and Prioritized Investigation Entry */}
    <ResearchCoverageOverview
      items={displayItems}
      issues={followUpIssues}
      userId={user?.sub}
      portfolioSummary={null}
      portfolioLoading={false}
      externalSearchQuery={searchQuery}
      activeHandoff={recentSession?.handoffContext || null}
      onNavigateToBrief={handleNavigateFromIssueToBrief}
      onOpenIssueModal={handleOpenIssueModal}
      onCreateIssue={handleOpenCreateIssueForFund}
    />

    {/* Phase F15 & F20: Research Review Follow-Up & Unresolved-Issue Tracking Workspace */}
    <ResearchFollowUpWorkspace
      issues={followUpIssues}
      savedItems={displayItems}
      externalSearchQuery={searchQuery}
      onOpenIssueModal={handleOpenIssueModal}
      onNavigateToBrief={handleNavigateFromIssueToBrief}
    />

    {/* Workspace Controls & Organization Panel */}
    <div className="panel mb-6">
      {/* Comparison Resumption & Candidate Selection Panel */}
      <div className="p-4 border-b border-border bg-surface-raised/30 space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <span className="mono-meta text-accent font-bold uppercase tracking-wider text-[11px]">
                Comparison Shortlist Resumption
              </span>
              <span className="status-badge state-operational font-mono text-[11px]">
                {selectedForCompare.length} of 3 primary slots selected (max 4)
              </span>
              {selectedForCompare.length >= 2 && selectedForCompare.length <= 3 && (
                <span className="status-badge state-approved text-[10px]">READY TO RESUME</span>
              )}
              {selectedForCompare.length === 4 && (
                <span className="status-badge state-candidate text-[10px] text-amber-300">EXTENDED 4-CANDIDATE COMPARISON</span>
              )}
            </div>
            <p className="text-xs text-text-secondary">
              Select 2 or 3 saved mutual funds to re-launch side-by-side evidence comparison. Reopening a comparison evaluates fresh point-in-time metrics under the active knowledge cutoff.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            {comparativeCount >= 2 && selectedForCompare.length === 0 && (
              <button
                type="button"
                onClick={handleSelectComparativeShortlist}
                className="btn btn-secondary btn-sm text-accent border-accent/40 hover:bg-accent/10 font-mono text-xs flex items-center gap-1.5"
                title="Auto-select funds sharing comparative due-diligence context"
              >
                <svg className="w-3.5 h-3.5 text-accent" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
                </svg>
                <span>Select Comparative Shortlist ({Math.min(comparativeCount, 4)})</span>
              </button>
            )}

            {selectedForCompare.length >= 2 && (
              <button
                onClick={handleLaunchCompare}
                className="btn btn-primary btn-sm flex items-center gap-1.5 shadow"
              >
                <span>Compare {selectedForCompare.length} Saved Funds</span>
                <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
                </svg>
              </button>
            )}

            {selectedForCompare.length > 0 && (
              <button
                onClick={() => setSelectedForCompare([])}
                className="btn btn-ghost btn-sm text-[11px] text-text-tertiary hover:text-text-primary"
              >
                Clear Selection
              </button>
            )}
          </div>
        </div>

        {/* Selected Candidates Preview Chips */}
        {selectedForCompare.length > 0 && (
          <div className="flex flex-wrap items-center gap-2 pt-2 border-t border-border/50 text-xs font-mono">
            <span className="text-text-muted uppercase text-[10px] font-bold">Selected Shortlist:</span>
            {displayItems
              .filter((d) => selectedForCompare.includes(getWatchlistItemKey(d.schemeId, d.schemeOptionId)))
              .map((d) => (
                <span
                  key={getWatchlistItemKey(d.schemeId, d.schemeOptionId)}
                  className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-surface border border-accent/40 text-text-primary shadow-xs"
                >
                  <span className="font-semibold text-accent">{d.schemeName}</span>
                  <span className="text-[10px] text-text-tertiary">
                    ({d.planType || "DIRECT"} • {d.optionType || "GROWTH"})
                  </span>
                  <button
                    type="button"
                    onClick={() => toggleCompareSelection(getWatchlistItemKey(d.schemeId, d.schemeOptionId))}
                    className="text-text-muted hover:text-rose-400 ml-1 text-xs"
                    aria-label={`Deselect ${d.schemeName}`}
                  >
                    ✕
                  </button>
                </span>
              ))}
            {selectedForCompare.length === 1 && (
              <span className="text-text-tertiary text-[11px] italic">
                (Select 1 more candidate to enable comparison)
              </span>
            )}
            {selectedForCompare.length === 4 && (
              <span className="text-amber-400/90 text-[11px]">
                ℹ️ Primary workspace layout displays 3 side-by-side candidates. The 4th candidate is evaluated in calculations and metrics.
              </span>
            )}
          </div>
        )}
      </div>

      {/* Phase F11: Saved Research Views Bar */}
      <SavedViewsBar
        currentConfig={currentWorkspaceConfig}
        savedViews={savedViews}
        activeSavedViewId={activeSavedViewId}
        userId={user?.sub}
        onApplyView={handleApplySavedView}
        onResetDefault={handleResetDefaultView}
        onSavedViewsChange={setSavedViews}
        onSetActiveSavedViewId={setActiveSavedViewId}
        onNotify={(message, kind = "info") => setViewNotification({ message, kind })}
      />

      {/* Phase F11: View Notification Alert */}
      {viewNotification && (
        <div
          role="status"
          aria-live="polite"
          className={`px-4 py-2 border-b text-xs font-mono flex items-center justify-between gap-2 ${viewNotification.kind === "warning"
              ? "bg-amber-500/15 border-amber-500/40 text-amber-200"
              : viewNotification.kind === "success"
                ? "bg-emerald-500/15 border-emerald-500/40 text-emerald-200"
                : "bg-surface-raised border-border text-text-primary"
            }`}
        >
          <div className="flex items-center gap-2">
            <span>{viewNotification.kind === "warning" ? "⚠️" : viewNotification.kind === "success" ? "✓" : "ℹ️"}</span>
            <span>{viewNotification.message}</span>
          </div>
          <button
            type="button"
            onClick={() => setViewNotification(null)}
            className="text-text-muted hover:text-text-primary text-xs p-0.5"
            aria-label="Dismiss view notification"
          >
            ✕
          </button>
        </div>
      )}

      {/* Organization Filter Tabs */}
      <div className="flex flex-wrap items-center gap-1 px-4 pt-3 border-b border-border text-xs font-mono">
        <button
          onClick={() => {
            setFilterTab("ATTENTION_QUEUE");
            setSortBy("attention_priority");
          }}
          className={`px-3 py-1.5 rounded-t-md font-semibold transition-colors border-b-2 flex items-center gap-1.5 ${filterTab === "ATTENTION_QUEUE"
              ? "border-amber-400 text-amber-300 bg-amber-500/15"
              : "border-transparent text-text-secondary hover:text-amber-200 hover:bg-surface-raised/20"
            }`}
        >
          <span className="text-amber-400">⚡</span>
          <span>Attention Queue</span>
          {attentionSummary.attentionQueueCount > 0 ? (
            <span className="bg-amber-500/25 text-amber-300 text-[10px] px-1.5 py-0.2 rounded-full font-bold">
              {attentionSummary.attentionQueueCount}
            </span>
          ) : (
            <span className="text-[10px] opacity-70">({attentionSummary.attentionQueueCount})</span>
          )}
        </button>
        <button
          onClick={() => setFilterTab("ALL")}
          className={`px-3 py-1.5 rounded-t-md font-semibold transition-colors border-b-2 ${filterTab === "ALL"
              ? "border-accent text-accent bg-surface-raised/40"
              : "border-transparent text-text-secondary hover:text-text-primary hover:bg-surface-raised/20"
            }`}
        >
          All Saved ({displayItems.length})
        </button>
        <button
          onClick={() => setFilterTab("WITH_THESIS")}
          className={`px-3 py-1.5 rounded-t-md font-semibold transition-colors border-b-2 flex items-center gap-1.5 ${filterTab === "WITH_THESIS"
              ? "border-accent text-accent bg-accent/15"
              : "border-transparent text-text-secondary hover:text-text-primary hover:bg-surface-raised/20"
            }`}
        >
          <span>With Thesis</span>
          {thesisCount > 0 ? (
            <span className="bg-accent/20 text-accent text-[10px] px-1.5 py-0.2 rounded-full font-bold">
              {thesisCount}
            </span>
          ) : (
            <span className="text-[10px] opacity-70">({thesisCount})</span>
          )}
        </button>
        <button
          onClick={() => setFilterTab("NEEDS_FOLLOW_UP")}
          className={`px-3 py-1.5 rounded-t-md font-semibold transition-colors border-b-2 flex items-center gap-1.5 ${filterTab === "NEEDS_FOLLOW_UP"
              ? "border-rose-400 text-rose-400 bg-rose-500/10"
              : "border-transparent text-text-secondary hover:text-rose-300 hover:bg-surface-raised/20"
            }`}
        >
          <span>Needs Follow-Up</span>
          {needsFollowUpCount > 0 ? (
            <span className="bg-rose-500/20 text-rose-300 text-[10px] px-1.5 py-0.2 rounded-full font-bold">
              {needsFollowUpCount}
            </span>
          ) : (
            <span className="text-[10px] opacity-70">({needsFollowUpCount})</span>
          )}
        </button>
        <button
          onClick={() => setFilterTab("TO_INVESTIGATE")}
          className={`px-3 py-1.5 rounded-t-md font-semibold transition-colors border-b-2 flex items-center gap-1.5 ${filterTab === "TO_INVESTIGATE"
              ? "border-amber-400 text-amber-400 bg-amber-500/10"
              : "border-transparent text-text-secondary hover:text-amber-300 hover:bg-surface-raised/20"
            }`}
        >
          <span>To Investigate</span>
          <span className="text-[10px] opacity-70">({toInvestigateCount})</span>
        </button>
        <button
          onClick={() => setFilterTab("IN_PROGRESS")}
          className={`px-3 py-1.5 rounded-t-md font-semibold transition-colors border-b-2 flex items-center gap-1.5 ${filterTab === "IN_PROGRESS"
              ? "border-sky-400 text-sky-400 bg-sky-500/10"
              : "border-transparent text-text-secondary hover:text-sky-300 hover:bg-surface-raised/20"
            }`}
        >
          <span>In Progress</span>
          <span className="text-[10px] opacity-70">({inProgressCount})</span>
        </button>
        <button
          onClick={() => setFilterTab("REVIEWED")}
          className={`px-3 py-1.5 rounded-t-md font-semibold transition-colors border-b-2 flex items-center gap-1.5 ${filterTab === "REVIEWED"
              ? "border-emerald-400 text-emerald-400 bg-emerald-500/10"
              : "border-transparent text-text-secondary hover:text-emerald-300 hover:bg-surface-raised/20"
            }`}
        >
          <span>Reviewed</span>
          <span className="text-[10px] opacity-70">({reviewedCount})</span>
        </button>
        <button
          onClick={() => setFilterTab("EVIDENCE_CHANGED")}
          className={`px-3 py-1.5 rounded-t-md font-semibold transition-colors border-b-2 flex items-center gap-1.5 ${filterTab === "EVIDENCE_CHANGED"
              ? "border-amber-400 text-amber-400 bg-amber-500/10"
              : "border-transparent text-text-secondary hover:text-amber-300 hover:bg-surface-raised/20"
            }`}
        >
          <span>Evidence Changed</span>
          {evidenceChangedCount > 0 ? (
            <span className="bg-amber-500/20 text-amber-300 text-[10px] px-1.5 py-0.2 rounded-full font-bold">
              {evidenceChangedCount}
            </span>
          ) : (
            <span className="text-[10px] opacity-70">({evidenceChangedCount})</span>
          )}
        </button>
        <button
          onClick={() => setFilterTab("REVIEW_NEEDED")}
          className={`px-3 py-1.5 rounded-t-md font-semibold transition-colors border-b-2 flex items-center gap-1.5 ${filterTab === "REVIEW_NEEDED"
              ? "border-rose-400 text-rose-400 bg-rose-500/10"
              : "border-transparent text-text-secondary hover:text-rose-300 hover:bg-surface-raised/20"
            }`}
        >
          <span>Review Needed</span>
          {reviewNeededCount > 0 ? (
            <span className="bg-rose-500/20 text-rose-300 text-[10px] px-1.5 py-0.2 rounded-full font-bold">
              {reviewNeededCount}
            </span>
          ) : (
            <span className="text-[10px] opacity-70">({reviewNeededCount})</span>
          )}
        </button>
        <button
          onClick={() => setFilterTab("HISTORICAL_UNAVAILABLE")}
          className={`px-3 py-1.5 rounded-t-md font-semibold transition-colors border-b-2 ${filterTab === "HISTORICAL_UNAVAILABLE"
              ? "border-accent text-accent bg-surface-raised/40"
              : "border-transparent text-text-secondary hover:text-text-primary hover:bg-surface-raised/20"
            }`}
        >
          Baseline Only ({historicalUnavailableCount})
        </button>
        <button
          onClick={() => setFilterTab("COMPARATIVE")}
          className={`px-3 py-1.5 rounded-t-md font-semibold transition-colors border-b-2 ${filterTab === "COMPARATIVE"
              ? "border-accent text-accent bg-surface-raised/40"
              : "border-transparent text-text-secondary hover:text-text-primary hover:bg-surface-raised/20"
            }`}
        >
          Comparative Research ({comparativeCount})
        </button>
        <button
          onClick={() => setFilterTab("HELD_IN_PORTFOLIO")}
          className={`px-3 py-1.5 rounded-t-md font-semibold transition-colors border-b-2 ${filterTab === "HELD_IN_PORTFOLIO"
              ? "border-accent text-accent bg-surface-raised/40"
              : "border-transparent text-text-secondary hover:text-text-primary hover:bg-surface-raised/20"
            }`}
        >
          Held in Portfolio ({heldInPortfolioCount})
        </button>
        <button
          onClick={() => setFilterTab("QUESTIONS")}
          className={`px-3 py-1.5 rounded-t-md font-semibold transition-colors border-b-2 ${filterTab === "QUESTIONS"
              ? "border-accent text-accent bg-surface-raised/40"
              : "border-transparent text-text-secondary hover:text-text-primary hover:bg-surface-raised/20"
            }`}
        >
          Active Questions ({questionsCount})
        </button>
        <button
          onClick={() => setFilterTab("NOTES")}
          className={`px-3 py-1.5 rounded-t-md font-semibold transition-colors border-b-2 ${filterTab === "NOTES"
              ? "border-accent text-accent bg-surface-raised/40"
              : "border-transparent text-text-secondary hover:text-text-primary hover:bg-surface-raised/20"
            }`}
        >
          With Notes ({notesCount})
        </button>
        <button
          onClick={() => setFilterTab("DIRECT_GROWTH")}
          className={`px-3 py-1.5 rounded-t-md font-semibold transition-colors border-b-2 ${filterTab === "DIRECT_GROWTH"
              ? "border-accent text-accent bg-surface-raised/40"
              : "border-transparent text-text-secondary hover:text-text-primary hover:bg-surface-raised/20"
            }`}
        >
          Direct Growth
        </button>
        <button
          onClick={() => setFilterTab("REGULAR_PLANS")}
          className={`px-3 py-1.5 rounded-t-md font-semibold transition-colors border-b-2 ${filterTab === "REGULAR_PLANS"
              ? "border-accent text-accent bg-surface-raised/40"
              : "border-transparent text-text-secondary hover:text-text-primary hover:bg-surface-raised/20"
            }`}
        >
          Regular / IDCW
        </button>
        <button
          onClick={() => setFilterTab("ACTIONABLE_COVERAGE_GAPS")}
          className={`px-3 py-1.5 rounded-t-md font-semibold transition-colors border-b-2 flex items-center gap-1.5 ${filterTab === "ACTIONABLE_COVERAGE_GAPS"
              ? "border-rose-400 text-rose-300 bg-rose-500/15"
              : "border-transparent text-text-secondary hover:text-rose-200 hover:bg-surface-raised/20"
            }`}
        >
          <span>Coverage Gaps</span>
          {fundsWithActionableGapsCount > 0 ? (
            <span className="bg-rose-500/20 text-rose-300 text-[10px] px-1.5 py-0.2 rounded-full font-bold">
              {fundsWithActionableGapsCount}
            </span>
          ) : (
            <span className="text-[10px] opacity-70">({fundsWithActionableGapsCount})</span>
          )}
        </button>
        <button
          onClick={() => setFilterTab("WITH_FOLLOW_UP_ISSUES")}
          className={`px-3 py-1.5 rounded-t-md font-semibold transition-colors border-b-2 flex items-center gap-1.5 ${filterTab === "WITH_FOLLOW_UP_ISSUES"
              ? "border-amber-400 text-amber-300 bg-amber-500/15"
              : "border-transparent text-text-secondary hover:text-amber-200 hover:bg-surface-raised/20"
            }`}
        >
          <span>With Follow-Up Issues</span>
          {fundsWithFollowUpCount > 0 ? (
            <span className="bg-amber-500/20 text-amber-300 text-[10px] px-1.5 py-0.2 rounded-full font-bold">
              {fundsWithFollowUpCount}
            </span>
          ) : (
            <span className="text-[10px] opacity-70">({fundsWithFollowUpCount})</span>
          )}
        </button>
        {fundsWithResidualUncertaintyCount > 0 && (
          <button
            onClick={() => setFilterTab("WITH_RESIDUAL_UNCERTAINTY")}
            className={`px-3 py-1.5 rounded-t-md font-semibold transition-colors border-b-2 flex items-center gap-1.5 ${filterTab === "WITH_RESIDUAL_UNCERTAINTY"
                ? "border-purple-400 text-purple-300 bg-purple-500/15"
                : "border-transparent text-text-secondary hover:text-purple-200 hover:bg-surface-raised/20"
              }`}
          >
            <span>Residual Uncertainty</span>
            <span className="bg-purple-500/20 text-purple-300 text-[10px] px-1.5 py-0.2 rounded-full font-bold">
              {fundsWithResidualUncertaintyCount}
            </span>
          </button>
        )}
        {attentionSummary.incompleteRecordCount > 0 && (
          <button
            onClick={() => setFilterTab("INCOMPLETE_RECORDS")}
            className={`px-3 py-1.5 rounded-t-md font-semibold transition-colors border-b-2 flex items-center gap-1.5 ${filterTab === "INCOMPLETE_RECORDS"
                ? "border-amber-400 text-amber-300 bg-amber-500/15"
                : "border-transparent text-text-secondary hover:text-amber-200 hover:bg-surface-raised/20"
              }`}
          >
            <span>Incomplete</span>
            <span className="text-[10px] opacity-70">({attentionSummary.incompleteRecordCount})</span>
          </button>
        )}
        {missingQuestionCount > 0 && (
          <button
            onClick={() => setFilterTab("MISSING_QUESTION")}
            className={`px-3 py-1.5 rounded-t-md font-semibold transition-colors border-b-2 flex items-center gap-1.5 ${filterTab === "MISSING_QUESTION"
                ? "border-amber-400 text-amber-300 bg-amber-500/15"
                : "border-transparent text-text-secondary hover:text-amber-200 hover:bg-surface-raised/20"
              }`}
          >
            <span>No Question</span>
            <span className="text-[10px] opacity-70">({missingQuestionCount})</span>
          </button>
        )}
        {missingNextStepCount > 0 && (
          <button
            onClick={() => setFilterTab("MISSING_NEXT_STEP")}
            className={`px-3 py-1.5 rounded-t-md font-semibold transition-colors border-b-2 flex items-center gap-1.5 ${filterTab === "MISSING_NEXT_STEP"
                ? "border-amber-400 text-amber-300 bg-amber-500/15"
                : "border-transparent text-text-secondary hover:text-amber-200 hover:bg-surface-raised/20"
              }`}
          >
            <span>No Next Step</span>
            <span className="text-[10px] opacity-70">({missingNextStepCount})</span>
          </button>
        )}
        {missingSupportingEvidenceCount > 0 && (
          <button
            onClick={() => setFilterTab("MISSING_SUPPORTING_EVIDENCE")}
            className={`px-3 py-1.5 rounded-t-md font-semibold transition-colors border-b-2 flex items-center gap-1.5 ${filterTab === "MISSING_SUPPORTING_EVIDENCE"
                ? "border-accent text-accent bg-accent/15"
                : "border-transparent text-text-secondary hover:text-accent/90 hover:bg-surface-raised/20"
              }`}
          >
            <span>No Evidence</span>
            <span className="text-[10px] opacity-70">({missingSupportingEvidenceCount})</span>
          </button>
        )}
        {missingReconsiderationCount > 0 && (
          <button
            onClick={() => setFilterTab("MISSING_RECONSIDERATION_CONDITIONS")}
            className={`px-3 py-1.5 rounded-t-md font-semibold transition-colors border-b-2 flex items-center gap-1.5 ${filterTab === "MISSING_RECONSIDERATION_CONDITIONS"
                ? "border-accent text-accent bg-accent/15"
                : "border-transparent text-text-secondary hover:text-accent/90 hover:bg-surface-raised/20"
              }`}
          >
            <span>No Triggers</span>
            <span className="text-[10px] opacity-70">({missingReconsiderationCount})</span>
          </button>
        )}
        {shortlistSelectedCount > 0 && (
          <button
            onClick={() => setFilterTab("SHORTLIST_SELECTED")}
            className={`px-3 py-1.5 rounded-t-md font-semibold transition-colors border-b-2 flex items-center gap-1.5 ${filterTab === "SHORTLIST_SELECTED"
                ? "border-accent text-accent bg-accent/15"
                : "border-transparent text-text-secondary hover:text-text-primary hover:bg-surface-raised/20"
              }`}
          >
            <span>In Shortlist</span>
            <span className="bg-accent/20 text-accent text-[10px] px-1.5 py-0.2 rounded-full font-bold">
              {shortlistSelectedCount}
            </span>
          </button>
        )}
        {optionMismatchCount > 0 && (
          <button
            onClick={() => setFilterTab("OPTION_MISMATCH")}
            className={`px-3 py-1.5 rounded-t-md font-semibold transition-colors border-b-2 flex items-center gap-1.5 ${filterTab === "OPTION_MISMATCH"
                ? "border-amber-400 text-amber-300 bg-amber-500/15"
                : "border-transparent text-text-secondary hover:text-amber-200 hover:bg-surface-raised/20"
              }`}
          >
            <span>Option Mismatch</span>
            <span className="text-[10px] opacity-70">({optionMismatchCount})</span>
          </button>
        )}
      </div>

      {/* Search, AMC Filter, & Sort Controls */}
      <div className="grid grid-cols-1 gap-3 p-4 sm:grid-cols-2 lg:grid-cols-[minmax(0,1fr)_180px_180px_auto] lg:items-end">
        <div>
          <label htmlFor="search-input" className="field-label text-[12px]">
            Unified Research Search (Names, ISINs, Theses, Notes, Status)
          </label>
          <div className="relative">
            <input
              id="search-input"
              type="text"
              placeholder="Search scheme name, ISIN, AMFI, working thesis, question, next step…"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="field field-mono w-full text-[13px] pr-8"
              disabled={displayItems.length === 0}
            />
            {searchQuery && (
              <button
                type="button"
                onClick={() => setSearchQuery("")}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-text-muted hover:text-text-primary text-xs p-1"
                aria-label="Clear search query"
                title="Clear search"
              >
                ✕
              </button>
            )}
          </div>
        </div>

        <div>
          <label htmlFor="amc-select" className="field-label text-[12px]">
            Filter AMC
          </label>
          <select
            id="amc-select"
            value={selectedAmc}
            onChange={(e) => setSelectedAmc(e.target.value)}
            className="field field-mono w-full text-[13px]"
            disabled={displayItems.length === 0 || uniqueAmcs.length === 0}
          >
            <option value="ALL">All AMCs ({uniqueAmcs.length})</option>
            {uniqueAmcs.map((amc) => (
              <option key={amc} value={amc}>
                {amc}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="sort-select" className="field-label text-[12px]">
            Sort Order
          </label>
          <select
            id="sort-select"
            value={sortBy}
            onChange={(e) => setSortBy(e.target.value as SortOption)}
            className="field field-mono w-full text-[13px]"
            disabled={displayItems.length === 0}
          >
            <option value="attention_priority">⚡ Attention Priority (Actionable First)</option>
            <option value="needs_followup_first">Needs Follow-Up First</option>
            <option value="to_investigate_first">To Investigate First</option>
            <option value="updated_desc">Recently Updated</option>
            <option value="created_desc">Date Saved</option>
            <option value="notes_first">Active Notes First</option>
            <option value="name_asc">Name (A–Z)</option>
            <option value="name_desc">Name (Z–A)</option>
          </select>
        </div>

        <div className="pb-1 lg:text-right space-y-1">
          <span className="eyebrow block">Filtered View</span>
          <span className="data-value-sm font-mono mt-0.5 inline-block">
            {filteredAndSortedItems.length} of {displayItems.length}
          </span>
          <span className="mono-meta ml-1.5">funds</span>
          <div className="pt-0.5 flex flex-wrap items-center justify-end gap-1.5 text-[11px] font-mono">
            {filteredAndSortedItems.length > 0 && (
              <button
                type="button"
                onClick={handleSelectAllVisible}
                className="btn btn-ghost btn-xs text-[10.5px] text-accent hover:underline py-0.5 px-1.5"
                title="Select all mutual funds currently displayed under active filters"
              >
                Select Visible
              </button>
            )}
            {selectedItemKeys.length > 0 && (
              <span className="text-accent font-semibold text-[10.5px]">
                ({selectedItemKeys.length} selected)
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Interactive Active Filters & Breadcrumbs Bar */}
      {(searchQuery || selectedAmc !== "ALL" || filterTab !== "ALL") && (
        <div className="border-t border-border px-4 py-2.5 bg-surface-raised/30 flex flex-wrap items-center justify-between gap-2 text-xs font-mono">
          <div className="flex flex-wrap items-center gap-1.5">
            <span className="text-[10.5px] text-text-tertiary uppercase font-bold mr-1">
              Active filters:
            </span>
            {searchQuery && (
              <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded bg-accent/15 border border-accent/40 text-accent text-[11px]">
                <span>Query: &ldquo;{searchQuery}&rdquo;</span>
                <button
                  type="button"
                  onClick={() => setSearchQuery("")}
                  className="hover:text-text-primary text-accent/80 hover:text-accent font-bold"
                  aria-label="Clear query filter"
                  title="Remove query filter"
                >
                  ✕
                </button>
              </span>
            )}
            {filterTab !== "ALL" && (
              <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded bg-surface border border-border text-text-primary text-[11px]">
                <span>Filter: {filterTab.replace(/_/g, " ")}</span>
                <button
                  type="button"
                  onClick={() => setFilterTab("ALL")}
                  className="text-text-muted hover:text-text-primary font-bold"
                  aria-label="Clear tab filter"
                  title="Reset to All Saved"
                >
                  ✕
                </button>
              </span>
            )}
            {selectedAmc !== "ALL" && (
              <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded bg-surface border border-border text-text-primary text-[11px]">
                <span>AMC: {selectedAmc}</span>
                <button
                  type="button"
                  onClick={() => setSelectedAmc("ALL")}
                  className="text-text-muted hover:text-text-primary font-bold"
                  aria-label="Clear AMC filter"
                  title="Reset to All AMCs"
                >
                  ✕
                </button>
              </span>
            )}
          </div>

          <div className="flex items-center gap-2">
            <span className="text-[11px] text-text-tertiary">
              Showing {filteredAndSortedItems.length} of {displayItems.length} investigations
            </span>
            <button
              type="button"
              onClick={() => {
                setSearchQuery("");
                setSelectedAmc("ALL");
                setFilterTab("ALL");
              }}
              className="btn btn-ghost btn-xs text-[11px] text-accent hover:underline py-0.5"
              title="Clear all search queries and filters"
            >
              Reset All Filters
            </button>
          </div>
        </div>
      )}
    </div>

    {/* Phase F10: Research Workspace Bulk Actions and Safe Organization Toolbar */}
    <BulkActionBar
      selectedItems={selectedObjects}
      visibleItems={filteredAndSortedItems}
      currentShortlistKeys={selectedForCompare}
      isOperating={isBulkOperating}
      onClearSelection={handleClearSelection}
      onSelectAllVisible={handleSelectAllVisible}
      onReconcileToVisible={handleReconcileToVisible}
      onUpdateInvestigationStatus={async (schemeId, schemeOptionId, status) => {
        setIsBulkOperating(true);
        try {
          await Promise.resolve(updateInvestigationStatus(schemeId, schemeOptionId, status));
        } finally {
          setIsBulkOperating(false);
        }
      }}
      onUpdateShortlist={(newShortlist) => setSelectedForCompare(newShortlist)}
      onLaunchCompare={handleLaunchCompareWithItems}
      outcome={bulkOutcome}
      onDismissOutcome={() => setBulkOutcome(null)}
      onSetOutcome={(res) => setBulkOutcome(res)}
      onDeselectKeys={handleDeselectKeys}
    />

    {/* State View Handling */}
    {!isLoaded || loadingSchemes ? (
      <StateView
        kind="loading"
        title="Loading Research Workspace"
        message="Retrieving your saved mutual funds, investigation hypotheses, and verified observation dates..."
      />
    ) : displayItems.length === 0 ? (
      <StateView
        kind="empty"
        title="Your Research Workspace is Empty"
        message="Save mutual funds from fund profiles or the comparative screener to organize your research, record investigation hypotheses, and perform point-in-time comparisons."
        action={
          <div className="flex items-center gap-2">
            <Link href="/funds" className="btn btn-primary btn-sm">
              Explore Pilot Mutual Funds
            </Link>
            <Link href="/compare" className="btn btn-secondary btn-sm">
              Open Comparative Screener
            </Link>
          </div>
        }
      />
    ) : filteredAndSortedItems.length === 0 ? (
      <div className="panel p-8 text-center bg-surface border border-border rounded-xl space-y-4 max-w-xl mx-auto my-8">
        <div className="w-12 h-12 rounded-full bg-surface-raised border border-border mx-auto flex items-center justify-center text-xl">
          {searchQuery.trim() ? "🔍" : "📋"}
        </div>
        <div>
          <h3 className="text-base font-bold text-text-primary">
            {searchQuery.trim() && filterTab === "ALL" && selectedAmc === "ALL"
              ? `No Saved Research Matches "${searchQuery}"`
              : !searchQuery.trim()
                ? `No Saved Funds Under Filter "${filterTab.replace(/_/g, " ")}"`
                : `No Results for "${searchQuery}" with Active Filters`}
          </h3>
          <p className="text-xs text-text-secondary mt-1.5 max-w-md mx-auto leading-relaxed">
            {searchQuery.trim() && filterTab === "ALL" && selectedAmc === "ALL"
              ? `Unified search checked fund names, codes, ISINs, AMFIs, research objectives, working theses, questions, next steps, and evidence flags.`
              : !searchQuery.trim()
                ? `There are no saved research items currently matching the selected category (${filterTab.replace(/_/g, " ")}).`
                : `No saved funds match query "${searchQuery}" within the active filter "${filterTab.replace(/_/g, " ")}"${selectedAmc !== "ALL" ? ` and AMC "${selectedAmc}"` : ""}.`}
          </p>
        </div>
        <div className="flex flex-wrap items-center justify-center gap-2 pt-2">
          {searchQuery.trim() && (
            <button
              type="button"
              onClick={() => setSearchQuery("")}
              className="btn btn-secondary btn-sm font-mono text-xs"
            >
              Clear Search Query
            </button>
          )}
          {(filterTab !== "ALL" || selectedAmc !== "ALL") && (
            <button
              type="button"
              onClick={() => {
                setFilterTab("ALL");
                setSelectedAmc("ALL");
              }}
              className="btn btn-secondary btn-sm font-mono text-xs"
            >
              Clear Filters
            </button>
          )}
          <button
            type="button"
            onClick={() => {
              setSearchQuery("");
              setFilterTab("ALL");
              setSelectedAmc("ALL");
            }}
            className="btn btn-primary btn-sm font-mono text-xs"
          >
            Reset All (Show All {displayItems.length} Funds)
          </button>
        </div>
      </div>
    ) : (
      <div className="space-y-4">
        {/* Attention Queue Context Banner when active */}
        {filterTab === "ATTENTION_QUEUE" && (
          <div className="panel p-3.5 bg-amber-500/10 border border-amber-500/30 rounded-lg flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs font-mono">
            <div className="flex items-start gap-2.5">
              <span className="text-amber-400 text-base">⚡</span>
              <div>
                <span className="font-bold text-amber-300 block uppercase tracking-wider text-[11px]">
                  FOCUSED RESEARCH ATTENTION QUEUE ACTIVE ({filteredAndSortedItems.length} items)
                </span>
                <p className="text-text-secondary text-[11.5px] font-sans mt-0.5">
                  Ordered deterministically by inquiry precedence: <strong>Follow-Up &gt; Reconsideration &gt; Stale Cutoff &gt; Unreviewed &gt; Active Next Steps &gt; Incomplete Records</strong>. Not a fund quality score or investment rating.
                </p>
              </div>
            </div>
            <button
              type="button"
              onClick={() => setFilterTab("ALL")}
              className="btn btn-secondary btn-xs text-text-secondary hover:text-text-primary whitespace-nowrap self-start sm:self-auto"
            >
              View All Saved Funds ({displayItems.length})
            </button>
          </div>
        )}

        {filteredAndSortedItems.map((item) => {
          const itemKey = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
          const isComparing = selectedForCompare.includes(itemKey);
          const hasNotes = Boolean(item.reasonSaved || item.investigationQuestion || item.nextStep || item.workingThesis);
          const hasThesis = Boolean(item.workingThesis && item.workingThesis.trim().length > 0);
          const isConfirmingDelete = confirmingDeleteKey === itemKey;
          const currentStatus: DueDiligenceStatus = item.investigationStatus || "TO_INVESTIGATE";
          const statusMeta = DUE_DILIGENCE_STATUS_MAP[currentStatus];
          const itemChangeSummary = itemSummariesMap[itemKey] || computeEvidenceChanges(item, null, null);
          const resolvedAttention = resolvedAttentionMap[itemKey];
          const recordCompleteness = recordCompletenessMap[itemKey];
          const searchMatch = searchResultsMap[itemKey];

          const cardAnchorId = `saved-${item.schemeOptionId || item.schemeId}`;
          const isHighlighted = highlightedKey === cardAnchorId;
          const isSelected = selectedItemKeys.includes(itemKey);

          return (
            <div
              key={itemKey}
              id={cardAnchorId}
              className={`panel p-4 md:p-5 transition-all border scroll-mt-24 ${isHighlighted
                  ? "border-accent ring-2 ring-accent/60 bg-surface-raised/60 shadow-lg"
                  : isSelected
                    ? "border-accent ring-1 ring-accent/50 bg-surface-raised/40 shadow-xs"
                    : isComparing
                      ? "border-accent/70 ring-1 ring-accent/20 bg-surface-raised/30"
                      : "hover:border-text-primary/30"
                }`}
            >
              <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                {/* Left Column: Fund Identity, Plan, Badges & Notes */}
                <div className="min-w-0 flex-1 space-y-3">
                  <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] font-mono">
                    {/* Phase F10: Per-card bulk selection checkbox */}
                    <label className="flex items-center gap-1.5 cursor-pointer select-none px-2 py-0.5 rounded bg-surface-raised/70 hover:bg-surface-raised border border-border/80 text-xs">
                      <input
                        type="checkbox"
                        checked={isSelected}
                        onChange={() => toggleItemSelection(itemKey)}
                        className="accent-[var(--accent)] h-3.5 w-3.5 cursor-pointer"
                        aria-label={`Select ${item.schemeName} for bulk research action`}
                      />
                      <span className={isSelected ? "text-accent font-bold" : "text-text-secondary"}>
                        {isSelected ? "Selected" : "Select"}
                      </span>
                    </label>

                    <span className="text-text-tertiary">•</span>

                    <label className="flex items-center gap-1.5 cursor-pointer select-none">
                      <input
                        type="checkbox"
                        checked={isComparing}
                        disabled={!isComparing && selectedForCompare.length >= 4}
                        onChange={() => toggleCompareSelection(itemKey)}
                        className="accent-[var(--accent)] h-3.5 w-3.5"
                        aria-label={`Select ${item.schemeName} for comparison`}
                      />
                      <span className={isComparing ? "text-accent font-semibold" : "text-text-secondary"}>
                        Select for Compare
                      </span>
                    </label>

                    <span className="text-text-tertiary">•</span>
                    <span className="font-semibold text-accent">{item.schemeCode}</span>

                    {item.category && (
                      <>
                        <span className="text-text-tertiary">•</span>
                        <span className="status-badge state-operational text-[10px] uppercase">
                          {item.category.replace(/_/g, " ")}
                        </span>
                      </>
                    )}

                    <span className="text-text-tertiary">•</span>
                    <span className="text-text-secondary font-semibold">
                      {item.planType || "DIRECT"} {item.optionType || "GROWTH"}
                    </span>

                    {item.schemeOptionId && (
                      <>
                        <span className="text-text-tertiary">•</span>
                        <span className="text-text-muted">Option #{item.schemeOptionId}</span>
                      </>
                    )}

                    {item.amfiCode && (
                      <>
                        <span className="text-text-tertiary">•</span>
                        <span className="text-text-tertiary">AMFI: {item.amfiCode}</span>
                      </>
                    )}

                    {item.isin && (
                      <>
                        <span className="text-text-tertiary">•</span>
                        <span className="text-text-tertiary">ISIN: {item.isin}</span>
                      </>
                    )}

                    {/* Due Diligence Investigation Status Badge */}
                    <span className="text-text-tertiary">•</span>
                    <span className={`status-badge text-[10px] font-mono font-bold ${statusMeta.badgeClass}`}>
                      {statusMeta.badgeLabel}
                    </span>

                    {/* Phase F8: Primary Research Attention Reason Badge */}
                    {resolvedAttention && (
                      <>
                        <span className="text-text-tertiary">•</span>
                        <span
                          className={`status-badge text-[10px] font-mono font-semibold px-2 py-0.5 rounded border ${resolvedAttention.primaryReason.badgeClass}`}
                          title={resolvedAttention.primaryReason.description}
                        >
                          {resolvedAttention.primaryReason.label}
                        </span>
                      </>
                    )}

                    {/* Phase F13: Research Documentation Completeness Badge */}
                    {recordCompleteness && (
                      <>
                        <span className="text-text-tertiary">•</span>
                        <span
                          className={`status-badge text-[9.5px] font-mono px-1.5 py-0.5 rounded border ${recordCompleteness.isDocumentationComplete
                              ? "bg-emerald-500/10 border-emerald-500/30 text-emerald-300 font-semibold"
                              : "bg-surface-raised border-border text-text-muted"
                            }`}
                          title={recordCompleteness.descriptiveSummary}
                        >
                          Documentation: {recordCompleteness.recordedInvestorInputsCount}/6
                        </span>
                      </>
                    )}

                    {/* Phase F7: Working Thesis Badge */}
                    {hasThesis && (
                      <>
                        <span className="text-text-tertiary">•</span>
                        <span
                          className="status-badge state-candidate text-[10px] font-mono text-accent bg-accent/10 border border-accent/40 font-semibold"
                          title="Investor-authored working thesis documented"
                        >
                          💡 THESIS RECORDED
                        </span>
                      </>
                    )}

                    {/* Evidence Change Awareness Badge */}
                    <span className="text-text-tertiary">•</span>
                    <span
                      className={`status-badge text-[10px] font-mono font-semibold px-2 py-0.5 rounded border ${itemChangeSummary.badgeClass
                        }`}
                      title={itemChangeSummary.description}
                    >
                      {itemChangeSummary.badgeLabel}
                    </span>

                    {/* Comparative Research Shortlist Context Badge */}
                    {isComparativeItem(item) && (
                      <>
                        <span className="text-text-tertiary">•</span>
                        <span className="status-badge state-candidate text-[10px] font-mono text-accent bg-accent/10 border border-accent/30 font-semibold">
                          📊 COMPARATIVE SHORTLIST
                        </span>
                      </>
                    )}

                    {/* Explicit Portfolio Holding Connection Badge */}
                    {item.isHeldInPortfolio ? (
                      <>
                        <span className="text-text-tertiary">•</span>
                        <button
                          type="button"
                          onClick={() => {
                            setBriefInitialSection("G");
                            setBriefFund(item);
                          }}
                          className="status-badge state-approved text-[10px] font-bold hover:brightness-110 cursor-pointer"
                          title="Inspect exact portfolio holding relationship in Research Brief"
                        >
                          ● HELD IN PORTFOLIO
                        </button>
                        {item.holdingUnits !== null && item.holdingUnits !== undefined && (
                          <span className="text-emerald-400 font-semibold">
                            ({item.holdingUnits} units)
                          </span>
                        )}
                      </>
                    ) : item.heldDifferentVariantNotice ? (
                      <>
                        <span className="text-text-tertiary">•</span>
                        <button
                          type="button"
                          onClick={() => {
                            setBriefInitialSection("G");
                            setBriefFund(item);
                          }}
                          className="status-badge state-candidate text-[10px] font-mono text-amber-300 hover:brightness-110 cursor-pointer"
                          title="Inspect held variant vs researched plan in Research Brief"
                        >
                          ⚠️ {item.heldDifferentVariantNotice}
                        </button>
                      </>
                    ) : (
                      <>
                        <span className="text-text-tertiary">•</span>
                        <span className="status-badge text-[10px] font-mono text-text-tertiary bg-surface-raised border border-border/60">
                          RESEARCH ONLY
                        </span>
                      </>
                    )}
                  </div>

                  {/* Phase F9: Search Match Attribution */}
                  {searchQuery.trim() && searchMatch && searchMatch.matchedFields.length > 0 && (
                    <div className="p-2 sm:p-2.5 rounded-lg bg-accent/10 border border-accent/30 text-xs font-mono flex flex-wrap items-center justify-between gap-2">
                      <div className="flex flex-wrap items-center gap-1.5">
                        <span className="text-accent font-bold uppercase text-[10px] tracking-wider">
                          🔍 Matched:
                        </span>
                        <span className="text-text-primary font-medium">
                          {searchMatch.primaryMatchLabel}
                        </span>
                      </div>
                      {searchMatch.matchedFields.length > 1 && (
                        <span className="text-[10px] text-text-tertiary">
                          (+ {searchMatch.matchedFields.length - 1} other {searchMatch.matchedFields.length === 2 ? "field" : "fields"})
                        </span>
                      )}
                    </div>
                  )}

                  <h3 className="text-base sm:text-lg font-bold tracking-tight text-text-primary">
                    <Link
                      href={`/funds/${item.schemeId}${item.schemeOptionId ? `?option=${item.schemeOptionId}` : ""}`}
                      className="transition-colors hover:text-accent"
                    >
                      {item.schemeName}
                    </Link>
                  </h3>

                  <div className="flex flex-wrap items-center gap-x-5 gap-y-1 text-xs text-text-secondary font-mono">
                    {item.amcName && (
                      <p>
                        AMC <strong className="text-text-primary">{item.amcName}</strong>
                      </p>
                    )}
                    <p>
                      Latest NAV{" "}
                      {item.latestNav !== null && item.latestNav !== undefined ? (
                        <strong className="text-text-primary">{formatCurrency(item.latestNav)}</strong>
                      ) : (
                        <span className="text-text-tertiary">Historical</span>
                      )}
                      {item.latestNavDate && (
                        <span className="text-text-tertiary ml-1">({item.latestNavDate})</span>
                      )}
                    </p>
                    {item.yukiraScore !== null && item.yukiraScore !== undefined && (
                      <p>
                        YUKIRA Score{" "}
                        <strong className="text-accent">{item.yukiraScore.toFixed(2)} / 100</strong>
                        <span className="status-badge state-approved ml-1 text-[10px]">{item.yukiraScoreStatus || "CANDIDATE"}</span>
                      </p>
                    )}
                    {item.createdAt && (
                      <p className="text-text-tertiary text-[11px]">
                        Saved: <span className="text-text-secondary">{formatDateTime(item.createdAt)}</span>
                      </p>
                    )}
                    {item.updatedAt && item.updatedAt !== item.createdAt && (
                      <p className="text-text-tertiary text-[11px]">
                        Updated: <span className="text-text-secondary">{formatDateTime(item.updatedAt)}</span>
                      </p>
                    )}
                  </div>

                  {/* Unified Evidence Quality Compact Disclosure */}
                  <div className="pt-0.5">
                    <EvidenceQualitySummary
                      variant="compact"
                      asOfDate={item.latestNavDate || "2024-01-15"}
                      freshnessState={item.latestNavDate === "2024-01-15" || !item.latestNavDate ? "STALE" : "CURRENT"}
                      methodologyStatus={item.yukiraScoreStatus || "CANDIDATE"}
                      nextStepLabel="Resume Diligence in Profile"
                      nextStepHref={`/funds/${item.schemeId}${item.schemeOptionId ? `?option=${item.schemeOptionId}` : ""}#coverage`}
                    />
                  </div>

                  {/* Phase F8: Secondary Attention Flags & Completeness Bar */}
                  {resolvedAttention && (
                    <div className="p-2.5 rounded-lg bg-surface-raised/40 border border-border/70 space-y-1.5 text-xs font-mono">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <div className="flex items-center gap-2">
                          <span className="text-[10px] text-accent uppercase font-bold">
                            Research Attention Priority:
                          </span>
                          <span className={`px-2 py-0.5 rounded text-[10.5px] border ${resolvedAttention.primaryReason.badgeClass}`}>
                            {resolvedAttention.primaryReason.label}
                          </span>
                        </div>
                        <span className="text-[10.5px] text-text-tertiary">
                          Record: <strong className={resolvedAttention.completeness.isComplete ? "text-emerald-400" : "text-amber-400"}>{resolvedAttention.completeness.completedCount}/5 elements</strong>
                        </span>
                      </div>

                      <p className="text-[11.5px] text-text-secondary font-sans leading-snug">
                        {resolvedAttention.primaryReason.description}
                      </p>

                      {resolvedAttention.secondaryReasons.length > 0 && (
                        <div className="pt-1 border-t border-border/40 flex flex-wrap items-center gap-1.5 text-[10px]">
                          <span className="text-text-tertiary uppercase font-bold">Additional Attention Factors:</span>
                          {resolvedAttention.secondaryReasons.map((sec) => (
                            <span
                              key={sec.code}
                              className={`px-1.5 py-0.2 rounded border ${sec.badgeClass}`}
                              title={sec.description}
                            >
                              {sec.shortLabel}
                            </span>
                          ))}
                        </div>
                      )}

                      <div className="pt-1 text-[10.5px] text-text-muted flex flex-wrap items-center justify-between gap-1">
                        <span>Status: <em>{resolvedAttention.completeness.descriptiveStatus}</em></span>
                        <span className="italic text-[10px]">Attention precedence reflects research tasks, not investment quality</span>
                      </div>
                    </div>
                  )}

                  {/* Evidence Change Review Callout (when item is REVIEWED but underlying evidence changed) */}
                  {itemChangeSummary.requiresReview && (
                    <div className="panel p-3 bg-amber-500/10 border border-amber-500/40 rounded-lg flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs">
                      <div className="flex items-start gap-2.5">
                        <span className="text-amber-400 font-bold shrink-0 mt-0.5">⚠️</span>
                        <div>
                          <span className="font-bold text-amber-300 block font-mono text-[11px] uppercase tracking-wider">
                            EVIDENCE CHANGES DETECTED SINCE PREVIOUS REVIEW
                          </span>
                          <p className="text-text-secondary text-[11.5px] mt-0.5">
                            Underlying market observations or calculation runs have updated since this fund was reviewed. Inspect verified differences to decide if your thesis requires follow-up.
                          </p>
                        </div>
                      </div>
                      <button
                        type="button"
                        onClick={() => {
                          setBriefInitialSection("F");
                          setBriefFund(item);
                        }}
                        className="btn btn-secondary btn-xs text-amber-300 border-amber-500/50 hover:bg-amber-500/20 whitespace-nowrap self-start sm:self-auto font-mono"
                      >
                        Review Evidence Changes →
                      </button>
                    </div>
                  )}

                  {/* Due-Diligence Investigation Workflow Bar */}
                  <div className="panel p-3 bg-surface border border-border/80 rounded-lg space-y-2">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="text-[10px] font-mono text-text-tertiary font-bold tracking-wider uppercase">
                          INVESTIGATION PROGRESS:
                        </span>
                        <span
                          className={`status-badge text-[10.5px] font-mono font-bold px-2 py-0.5 rounded border ${statusMeta.badgeClass
                            }`}
                        >
                          {statusMeta.badgeLabel}
                        </span>
                        {recordReviewProgressMap[itemKey] && (
                          <button
                            type="button"
                            onClick={() => handleOpenBrief(item, "checklist")}
                            className={`status-badge text-[10px] font-mono font-bold px-2 py-0.5 rounded border flex items-center gap-1 hover:opacity-85 transition-opacity ${recordReviewProgressMap[itemKey].statusBadgeClass}`}
                            title={
                              recordReviewProgressMap[itemKey].reassessmentNeededCount > 0
                                ? `${recordReviewProgressMap[itemKey].reassessmentNeededCount} section(s) require renewed reassessment due to underlying evidence drift`
                                : "Open Research Brief to view evidence review checklist"
                            }
                          >
                            <span>📋 {recordReviewProgressMap[itemKey].statusBadgeLabel}</span>
                          </button>
                        )}
                        {(fundOpenIssuesCountMap.get(itemKey) || 0) > 0 && (
                          <button
                            type="button"
                            onClick={() => handleOpenBrief(item, "checklist")}
                            className="status-badge text-[10px] font-mono font-bold px-2 py-0.5 rounded border border-rose-500/40 bg-rose-500/15 text-rose-300 flex items-center gap-1 hover:opacity-85 transition-opacity"
                            title="Open Research Brief to view and manage unresolved follow-up issues"
                          >
                            <span>⚠️ {fundOpenIssuesCountMap.get(itemKey)} Open Issue{(fundOpenIssuesCountMap.get(itemKey) || 0) > 1 ? "s" : ""}</span>
                          </button>
                        )}
                      </div>

                      {/* Quick inline status switcher */}
                      <div className="flex flex-wrap items-center gap-1 text-[10.5px] font-mono">
                        <span className="text-text-muted text-[10px] mr-1 hidden sm:inline">Set State:</span>
                        {(["TO_INVESTIGATE", "IN_PROGRESS", "REVIEWED", "NEEDS_FOLLOW_UP"] as DueDiligenceStatus[]).map((st) => {
                          const isCurrent = currentStatus === st;
                          const meta = DUE_DILIGENCE_STATUS_MAP[st];
                          return (
                            <button
                              key={st}
                              type="button"
                              onClick={() => updateInvestigationStatus(item.schemeId, item.schemeOptionId, st)}
                              disabled={isCurrent}
                              className={`px-2 py-0.5 rounded text-[10px] font-semibold border transition-all ${isCurrent
                                  ? `${meta.colorClass} border-current cursor-default font-bold ring-1 ring-accent/30`
                                  : "bg-surface-raised text-text-tertiary border-border/60 hover:text-text-primary hover:border-text-secondary"
                                }`}
                              title={`Mark investigation as ${meta.label}`}
                            >
                              {meta.label}
                            </button>
                          );
                        })}
                      </div>
                    </div>

                    {/* Epistemic description & contextual next step guidance */}
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 text-[11.5px] pt-1.5 border-t border-border/40">
                      <p className="text-text-secondary leading-snug">
                        <span className="text-text-primary font-medium">{statusMeta.description}</span>{" "}
                        {currentStatus === "REVIEWED" && (
                          <span className="text-text-muted italic">
                            (Review records investor research activity only — does not alter quantitative metrics, validate methodology, or guarantee investment outcomes).
                          </span>
                        )}
                        {currentStatus === "NEEDS_FOLLOW_UP" && (
                          <span className="text-rose-400 font-mono text-[11px] font-medium">
                            [Follow-up required: examine missing/stale observations or formulate further question.]
                          </span>
                        )}
                        {currentStatus === "TO_INVESTIGATE" && (
                          <span className="text-amber-400/90 font-mono text-[11px]">
                            [Awaiting initial review: inspect evidence and formulate hypotheses before committing capital.]
                          </span>
                        )}
                      </p>

                      <div className="flex items-center gap-2 shrink-0 self-start sm:self-auto">
                        {currentStatus === "REVIEWED" && (item.latestNavDate === "2024-01-15" || !item.latestNavDate) && (
                          <button
                            type="button"
                            onClick={() => updateInvestigationStatus(item.schemeId, item.schemeOptionId, "NEEDS_FOLLOW_UP")}
                            className="btn btn-ghost btn-xs text-rose-400 hover:bg-rose-500/10 text-[10.5px] font-mono border border-rose-500/30"
                            title="Flag this fund for follow-up due to stale historical cutoff"
                          >
                            Flag Stale Evidence →
                          </button>
                        )}
                        {currentStatus === "NEEDS_FOLLOW_UP" && (
                          <button
                            type="button"
                            onClick={() => handleOpenEditNote(item)}
                            className="btn btn-secondary btn-xs text-rose-400 hover:bg-rose-500/10 text-[10.5px] font-mono border-rose-500/40"
                            title="Record what remains to be investigated"
                          >
                            + Record Follow-Up Note
                          </button>
                        )}
                      </div>
                    </div>
                  </div>

                  {/* Unified Investor Research Context & Evidence Panel */}
                  <div className="panel-inset p-3.5 bg-surface-raised/60 rounded-lg space-y-2.5 border border-border">
                    <div className="flex items-center justify-between border-b border-border/50 pb-2">
                      <span className="eyebrow text-[10px] flex items-center gap-1.5 text-accent">
                        <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                        </svg>
                        <span>INVESTOR RESEARCH CONTEXT &amp; DUE DILIGENCE</span>
                      </span>

                      <button
                        onClick={() => handleOpenEditNote(item)}
                        className="btn btn-ghost btn-sm text-[11px] text-accent hover:underline py-0.5 px-2"
                      >
                        {hasNotes || hasThesis ? "Edit Research Thesis & Notes" : "+ Record Research Thesis"}
                      </button>
                    </div>

                    {/* Phase F13: Explicit Missing Inputs Callout & Remediation Actions */}
                    {recordCompleteness && recordCompleteness.missingInvestorInputsCount > 0 && (
                      <div className="p-2.5 rounded-lg bg-surface/80 border border-border/80 flex flex-wrap items-center justify-between gap-2 text-xs font-mono">
                        <div className="flex items-center gap-2">
                          <span className="text-amber-400 font-bold text-xs">⚠️</span>
                          <span className="text-text-muted text-[10.5px]">
                            Missing Inputs ({recordCompleteness.missingInvestorInputsCount}/6):
                          </span>
                          <span className="text-text-secondary text-[11px] font-sans font-medium">
                            {recordCompleteness.missingInputLabels.join(" • ")}
                          </span>
                        </div>
                        <div className="flex flex-wrap items-center gap-1.5">
                          {!recordCompleteness.dimensions.QUESTION.isRecorded && (
                            <button
                              type="button"
                              onClick={() => handleOpenEditNote(item)}
                              className="btn btn-secondary btn-xs text-[10px] font-mono text-accent hover:bg-accent/10 border-accent/40"
                              title="Formulate due-diligence question for this fund"
                            >
                              + Question
                            </button>
                          )}
                          {!recordCompleteness.dimensions.THESIS.isRecorded && (
                            <button
                              type="button"
                              onClick={() => handleOpenEditNote(item)}
                              className="btn btn-secondary btn-xs text-[10px] font-mono text-amber-300 hover:bg-amber-500/10 border-amber-500/40"
                              title="Document investor working hypothesis"
                            >
                              + Thesis
                            </button>
                          )}
                          {!recordCompleteness.dimensions.NEXT_STEP.isRecorded && (
                            <button
                              type="button"
                              onClick={() => handleOpenEditNote(item)}
                              className="btn btn-secondary btn-xs text-[10px] font-mono text-text-secondary hover:text-text-primary border-border"
                              title="Specify next actionable investigation step"
                            >
                              + Next Step
                            </button>
                          )}
                          {!recordCompleteness.dimensions.SUPPORTING_EVIDENCE.isRecorded && (
                            <button
                              type="button"
                              onClick={() => handleOpenEditNote(item)}
                              className="btn btn-secondary btn-xs text-[10px] font-mono text-text-secondary hover:text-text-primary border-border"
                              title="Document supporting evidence notes"
                            >
                              + Evidence Notes
                            </button>
                          )}
                          {!recordCompleteness.dimensions.RECONSIDERATION_CONDITIONS.isRecorded && (
                            <button
                              type="button"
                              onClick={() => handleOpenEditNote(item)}
                              className="btn btn-secondary btn-xs text-[10px] font-mono text-text-secondary hover:text-text-primary border-border"
                              title="Define invalidation triggers and reconsideration criteria"
                            >
                              + Invalidation Triggers
                            </button>
                          )}
                        </div>
                      </div>
                    )}

                    {/* Investor-Authored Due Diligence Notes & Research Thesis */}
                    {hasNotes || hasThesis ? (
                      <div className="space-y-2.5 text-[12px] leading-relaxed">
                        {/* Working Thesis Callout */}
                        {item.workingThesis && (
                          <div className="p-3 rounded-lg bg-surface border border-accent/30 space-y-1.5">
                            <div className="flex items-center justify-between">
                              <div className="flex items-center gap-2">
                                <span className="mono-meta text-[10px] text-accent font-bold uppercase tracking-wider block">
                                  WORKING THESIS (INVESTOR HYPOTHESIS):
                                </span>
                                <span className="status-badge state-candidate text-[9px]">HYPOTHESIS</span>
                              </div>
                              {item.thesisUpdatedAt && (
                                <span className="text-[10px] font-mono text-text-muted">
                                  Revised: {item.thesisUpdatedAt.split("T")[0]}
                                </span>
                              )}
                            </div>
                            <p className="text-text-primary font-sans font-medium text-[12.5px] leading-snug pl-1">
                              &ldquo;{item.workingThesis}&rdquo;
                            </p>
                            {item.reconsiderationConditions && (
                              <div className="pt-1.5 text-[11px] font-mono border-t border-border/40 space-y-0.5">
                                <span className="text-text-muted text-[9.5px] uppercase font-bold block">
                                  Reconsideration Triggers / Invalidation:
                                </span>
                                <p className="text-amber-300/90 font-sans text-[11.5px]">{item.reconsiderationConditions}</p>
                              </div>
                            )}
                            {item.supportingEvidence && (
                              <div className="pt-1 text-[11px] font-mono border-t border-border/40 space-y-0.5">
                                <span className="text-text-muted text-[9.5px] uppercase font-bold block">
                                  Supporting Evidence Considered:
                                </span>
                                <p className="text-text-secondary font-sans text-[11.5px]">{item.supportingEvidence}</p>
                              </div>
                            )}
                          </div>
                        )}

                        {item.reasonSaved && (
                          <div>
                            <span className="mono-meta text-[10px] text-text-tertiary block font-bold">INVESTOR OBJECTIVE:</span>
                            <p className="text-text-primary font-sans">{item.reasonSaved}</p>
                          </div>
                        )}
                        {item.investigationQuestion && (
                          <div className="pt-0.5">
                            <div className="flex items-center gap-2">
                              <span className="mono-meta text-[10px] text-accent block font-bold">
                                DILIGENCE HYPOTHESIS / INVESTIGATION QUESTION:
                              </span>
                              {currentStatus === "TO_INVESTIGATE" && (
                                <span className="text-[9.5px] font-mono text-amber-400 bg-amber-500/10 px-1.5 py-0.2 rounded border border-amber-500/30 font-semibold">
                                  UNANSWERED
                                </span>
                              )}
                              {currentStatus === "REVIEWED" && (
                                <span className="text-[9.5px] font-mono text-emerald-400 bg-emerald-500/10 px-1.5 py-0.2 rounded border border-emerald-500/30 font-semibold">
                                  REVIEWED
                                </span>
                              )}
                              {currentStatus === "NEEDS_FOLLOW_UP" && (
                                <span className="text-[9.5px] font-mono text-rose-400 bg-rose-500/10 px-1.5 py-0.2 rounded border border-rose-500/30 font-semibold">
                                  FOLLOW-UP NEEDED
                                </span>
                              )}
                            </div>
                            <p className="text-text-primary font-sans font-medium mt-0.5">{item.investigationQuestion}</p>
                          </div>
                        )}
                        {item.nextStep && (
                          <div className="pt-0.5">
                            <span className="mono-meta text-[10px] text-text-tertiary block font-bold">INVESTOR-SPECIFIED NEXT STEP:</span>
                            <p className="text-text-secondary font-sans">{item.nextStep}</p>
                          </div>
                        )}
                      </div>
                    ) : (
                      <p className="text-[11.5px] font-mono text-text-tertiary italic">
                        No investor hypotheses recorded yet. Click &ldquo;+ Record Research Thesis&rdquo; to formulate why this fund is being audited before committing capital.
                      </p>
                    )}

                    {/* System Evidence Context & Continuity Guidance (Distinct from Investor Notes) */}
                    <div className="mt-2.5 pt-2.5 border-t border-border/70 flex flex-col sm:flex-row sm:items-center justify-between gap-2.5 text-[11px] font-mono">
                      <div className="flex items-start gap-2">
                        <span className="text-accent shrink-0 mt-0.5">↳</span>
                        <div>
                          <span className="mono-meta text-[10px] text-accent font-bold uppercase tracking-wider block">
                            SYSTEM EVIDENCE STATUS &amp; NEXT INVESTIGATION ACTION
                          </span>
                          {item.isHeldInPortfolio ? (
                            <p className="text-text-secondary leading-snug">
                              <strong className="text-emerald-400">Portfolio Matched:</strong> Exact scheme option is logged in your portfolio ({item.holdingUnits ?? "tracked"} units). Verify holding completeness and valuation freshness.
                            </p>
                          ) : item.heldDifferentVariantNotice ? (
                            <p className="text-text-secondary leading-snug">
                              <strong className="text-amber-300">Variant Alert:</strong> You hold a different option ({item.heldDifferentVariantNotice}). Inspect expense drag and NAV divergence in Fund Profile.
                            </p>
                          ) : item.latestNavDate && item.latestNavDate <= "2024-01-15" ? (
                            <p className="text-text-secondary leading-snug">
                              <strong className="text-text-primary">Historical NAV Cutoff ({item.latestNavDate}):</strong> NAV observations end at canonical audit baseline. Evaluate metrics under verified point-in-time cutoff.
                            </p>
                          ) : item.yukiraScoreStatus === "INSUFFICIENT_DATA" ? (
                            <p className="text-text-secondary leading-snug">
                              <strong className="text-text-muted">Limited Observation Depth:</strong> Insufficient history for complete score. Inspect metric-level observations in Evidence Explorer.
                            </p>
                          ) : (
                            <p className="text-text-secondary leading-snug">
                              <strong className="text-text-primary">Profile Ready:</strong> Verified deterministic calculation profile available. Inspect 16 quantitative metrics and benchmark comparison.
                            </p>
                          )}
                        </div>
                      </div>

                      {/* Direct Contextual Action Link */}
                      <div className="shrink-0 flex items-center gap-2 self-start sm:self-auto">
                        <button
                          type="button"
                          onClick={() => handleOpenBrief(item, "A")}
                          className="btn btn-ghost btn-xs text-[11px] font-mono text-accent hover:underline px-2 py-0.5 border border-accent/30 rounded"
                          title="Open consolidated evidence-led research brief"
                        >
                          Research Brief ↗
                        </button>
                        {item.isHeldInPortfolio ? (
                          <Link
                            href={`/portfolio#holding-${item.schemeOptionId || item.schemeId}`}
                            className="btn btn-secondary btn-xs text-[11px] text-accent border-accent/30 hover:bg-accent/10 whitespace-nowrap"
                          >
                            View in Portfolio →
                          </Link>
                        ) : item.heldDifferentVariantNotice ? (
                          <Link
                            href={`/funds/${item.schemeId}${item.schemeOptionId ? `?option=${item.schemeOptionId}` : ""}`}
                            onClick={() =>
                              recordMeaningfulSession("PROFILE", "Plan Divergence in Profile", { item })
                            }
                            className="btn btn-secondary btn-xs text-[11px] text-amber-300 border-amber-500/30 hover:bg-amber-500/10 whitespace-nowrap"
                          >
                            Compare Plans in Profile →
                          </Link>
                        ) : (
                          <Link
                            href={`/funds/${item.schemeId}${item.schemeOptionId ? `?option=${item.schemeOptionId}` : ""}`}
                            onClick={() =>
                              recordMeaningfulSession("PROFILE", "Fund Profile & Evidence Coverage", { item })
                            }
                            className="btn btn-secondary btn-xs text-[11px] text-accent border-accent/30 hover:bg-accent/10 whitespace-nowrap"
                          >
                            Inspect Evidence →
                          </Link>
                        )}
                      </div>
                    </div>
                  </div>

                  {/* Inline Removal Confirmation Banner (Protection against accidental note loss) */}
                  {isConfirmingDelete && (
                    <div className="panel p-3 bg-critical-bg/15 border border-critical-border rounded-lg flex flex-wrap items-center justify-between gap-3 text-xs">
                      <div className="flex items-center gap-2">
                        <span className="text-critical-fg font-bold">⚠️ Remove Saved Fund?</span>
                        <span className="text-text-primary">
                          Removing <strong className="text-text-primary">{item.schemeName}</strong> will delete its associated research notes and due-diligence questions.
                        </span>
                      </div>
                      <div className="flex items-center gap-2">
                        <button
                          onClick={() => handleConfirmRemoval(item.schemeId, item.schemeOptionId)}
                          className="btn btn-sm bg-critical-fg text-surface-midnight font-bold hover:opacity-90 px-3 py-1 rounded"
                        >
                          Confirm Removal
                        </button>
                        <button
                          onClick={() => setConfirmingDeleteKey(null)}
                          className="btn btn-secondary btn-sm px-3 py-1"
                        >
                          Cancel
                        </button>
                      </div>
                    </div>
                  )}
                </div>

                {/* Right Column: Actions */}
                <div className="flex flex-row lg:flex-col items-center lg:items-end gap-2 shrink-0 pt-2 lg:pt-0">
                  {/* Phase F8: Primary Attention Quick Action Button */}
                  {resolvedAttention && resolvedAttention.isInAttentionQueue && (
                    <button
                      type="button"
                      onClick={() => {
                        if (resolvedAttention.primaryReason.suggestedBriefSection) {
                          handleOpenBrief(item, resolvedAttention.primaryReason.suggestedBriefSection);
                        } else {
                          handleOpenEditNote(item);
                        }
                      }}
                      className="btn btn-primary btn-sm w-full lg:w-36 text-center justify-center font-mono text-[11px] shadow-xs"
                      title={resolvedAttention.primaryReason.description}
                    >
                      <span>{resolvedAttention.primaryReason.suggestedActionLabel}</span>
                    </button>
                  )}

                  <button
                    type="button"
                    onClick={() => handleOpenBrief(item, "A")}
                    className="btn btn-secondary btn-sm w-full lg:w-36 text-center justify-center flex items-center gap-1.5 font-medium border-accent/40 text-accent hover:bg-accent/10 shadow-xs"
                    title="View consolidated evidence-led research brief for this scheme option"
                  >
                    <svg className="h-3.5 w-3.5 text-accent" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                    </svg>
                    <span>Research Brief →</span>
                  </button>

                  <button
                    type="button"
                    onClick={() => handleOpenEditNote(item)}
                    className="btn btn-ghost btn-xs text-text-secondary hover:text-text-primary border border-border/80 w-full lg:w-36 text-center justify-center font-mono text-[11px]"
                    title="Edit or resume investor-authored research objective, hypothesis question, and working thesis"
                  >
                    <span>Resume Diligence ✎</span>
                  </button>

                  {itemChangeSummary.changes.length > 0 && (
                    <button
                      type="button"
                      onClick={() => handleOpenBrief(item, "F")}
                      className="btn btn-ghost btn-xs text-amber-400 border border-amber-500/40 hover:bg-amber-500/10 w-full lg:w-36 text-center justify-center font-mono text-[11px] font-semibold"
                      title="Inspect detected evidence changes against baseline snapshot"
                    >
                      Changes ({itemChangeSummary.changes.length}) ↗
                    </button>
                  )}

                  {(item.isHeldInPortfolio || item.heldDifferentVariantNotice) && (
                    <button
                      type="button"
                      onClick={() => handleOpenBrief(item, "G")}
                      className="btn btn-ghost btn-xs text-emerald-400 border border-emerald-500/40 hover:bg-emerald-500/10 w-full lg:w-36 text-center justify-center font-mono text-[11px] font-semibold"
                      title="Inspect holding identity and portfolio relationship in Research Brief"
                    >
                      {item.isHeldInPortfolio ? "Holding Context ↗" : "Variant Context ↗"}
                    </button>
                  )}

                  <Link
                    href={`/funds/${item.schemeId}${item.schemeOptionId ? `?option=${item.schemeOptionId}` : ""}`}
                    onClick={() =>
                      recordMeaningfulSession("PROFILE", "Fund Profile & Evidence Coverage", { item })
                    }
                    className="btn btn-primary btn-sm w-full lg:w-36 text-center justify-center shadow-sm"
                  >
                    <span>Open Profile →</span>
                  </Link>

                  {item.isHeldInPortfolio ? (
                    <Link
                      href={`/portfolio#holding-${item.schemeOptionId || item.schemeId}`}
                      className="btn btn-secondary btn-sm w-full lg:w-36 text-center justify-center text-accent border-accent/40 hover:bg-accent/10"
                      title="Navigate directly to this holding in Portfolio workspace"
                    >
                      <span>View in Portfolio →</span>
                    </Link>
                  ) : (
                    <Link
                      href={`/portfolio?addOption=${item.schemeOptionId || ""}&addScheme=${item.schemeId}${item.reasonSaved ? `&reason=${encodeURIComponent(item.reasonSaved)}` : ""
                        }${item.investigationQuestion ? `&question=${encodeURIComponent(item.investigationQuestion)}` : ""
                        }${item.nextStep ? `&nextStep=${encodeURIComponent(item.nextStep)}` : ""
                        }`}
                      className="btn btn-secondary btn-sm w-full lg:w-36 text-center justify-center text-[12px] text-text-secondary hover:text-text-primary"
                      title="Add this saved fund to your tracked portfolio holdings with pre-filled research context"
                    >
                      <span>+ Track in Portfolio</span>
                    </Link>
                  )}

                  <Link
                    href={`/compare?funds=${item.schemeId}${item.schemeOptionId ? `&options=${item.schemeId}:${item.schemeOptionId}&ids=${item.schemeOptionId}&tab=compare` : "&tab=compare"}`}
                    className="btn btn-secondary btn-sm w-full lg:w-36 text-center justify-center"
                    title="Open comparative workspace with this fund pre-selected"
                  >
                    <span>Compare Fund</span>
                  </Link>

                  <button
                    type="button"
                    onClick={() => toggleCompareSelection(itemKey)}
                    disabled={!isComparing && selectedForCompare.length >= 4}
                    className={`btn btn-sm w-full lg:w-36 text-center justify-center font-mono text-[11px] ${isComparing
                        ? "bg-accent/20 text-accent border border-accent/50 font-semibold"
                        : "btn-secondary text-text-secondary hover:text-text-primary"
                      }`}
                    title={
                      isComparing
                        ? "Remove candidate from shortlist"
                        : selectedForCompare.length >= 4
                          ? "Maximum 4 candidates selected"
                          : "Add candidate to comparison shortlist"
                    }
                  >
                    {isComparing ? "✓ In Shortlist" : "+ Compare Candidate"}
                  </button>

                  {!isConfirmingDelete && (
                    <button
                      onClick={() => setConfirmingDeleteKey(itemKey)}
                      className="btn btn-ghost btn-sm text-text-tertiary hover:text-critical-fg text-[11px]"
                      title="Remove from Workspace"
                      aria-label={`Remove ${item.schemeName} from workspace`}
                    >
                      <span>Remove from Workspace</span>
                    </button>
                  )}
                </div>
              </div>
            </div>
          );
        })}
      </div>
    )}

      {/* Research Note Modal */}
      <ResearchNoteModal
        isOpen={Boolean(editingFund)}
        onClose={() => setEditingFund(null)}
        fund={editingFund}
        onSave={saveResearchNote}
      />

        {/* Fund Research Brief Modal */}
        <FundResearchBriefModal
          isOpen={Boolean(briefFund)}
          onClose={() => {
            setBriefFund(null);
            setBriefInitialSection("A");
            setBriefInitialIssueId(null);
          }}
          fund={briefFund}
          initialSection={briefInitialSection}
          initialIssueId={briefInitialIssueId}
          userId={user?.sub}
          onReviewChecklistChange={() => {
            setReviewChecklistVersion((v) => v + 1);
            setFollowUpRefreshVersion((v) => v + 1);
          }}
          onEditNote={(f) => {
            setBriefFund(null);
            handleOpenEditNote(f);
          }}
          onUpdateStatus={updateInvestigationStatus}
        />

        {/* Phase F15: Research Follow-Up Issue Creation/Editing Modal */}
        {isFollowUpModalOpen && followUpModalFund && (
          <ResearchFollowUpModal
            isOpen={isFollowUpModalOpen}
            onClose={() => {
              setIsFollowUpModalOpen(false);
              setActiveFollowUpModalIssue(null);
              setFollowUpModalFund(null);
              setFollowUpModalPrefill(undefined);
              setFollowUpModalInitialReassessment(false);
              setFollowUpModalInitialShowTimeline(false);
              setFollowUpModalInitialDependencyId(null);
            }}
            fund={followUpModalFund}
            issue={activeFollowUpModalIssue}
            prefill={followUpModalPrefill}
            userId={user?.sub}
            initialReassessmentMode={followUpModalInitialReassessment}
            initialShowTimeline={followUpModalInitialShowTimeline}
            initialDependencyId={followUpModalInitialDependencyId}
            evidenceContext={{
              calculationRunId:
                snapshotsMap[getWatchlistItemKey(followUpModalFund.schemeId, followUpModalFund.schemeOptionId)]
                  ?.calculationRunId || null,
              asOfDate: followUpModalFund.latestNavDate || "2024-01-15",
            }}
            evidenceSummary={
              itemSummariesMap[getWatchlistItemKey(followUpModalFund.schemeId, followUpModalFund.schemeOptionId)] || null
            }
            onSaved={() => {
              setFollowUpRefreshVersion((v) => v + 1);
              setReviewChecklistVersion((v) => v + 1);
            }}
            onDeleted={() => {
              setFollowUpRefreshVersion((v) => v + 1);
              setReviewChecklistVersion((v) => v + 1);
            }}
            onNavigateToBrief={(iss) => {
              setIsFollowUpModalOpen(false);
              setActiveFollowUpModalIssue(null);
              setFollowUpModalFund(null);
              setFollowUpModalPrefill(undefined);
              handleNavigateFromIssueToBrief(
                iss.schemeId,
                iss.schemeOptionId,
                iss.briefSection || "B",
                iss
              );
            }}
          />
        )}
      </PageContainer>
    );
}

    export default function WatchlistPage() {
  return (
    <Suspense fallback={<StateView kind="loading" title="Loading..." message="Initializing research workspace..." />}>
      <WatchlistContent />
    </Suspense>
    );
}
