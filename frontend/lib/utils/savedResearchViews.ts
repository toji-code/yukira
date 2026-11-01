/**
 * YUKIRA Quantitative Intelligence Platform
 * Phase F11: Saved Research Views and Resumable Workspace Context
 *
 * Implements deterministic serialization, validation, and browser-local persistence
 * for personal research-workspace configurations (search queries, filters, AMC scopes, sort orders).
 *
 * CRITICAL EPISTEMIC INTEGRITY RULES:
 * 1. A saved view is a reusable filter configuration, NOT a copied snapshot of fund data or financial metrics.
 * 2. Underlying research records, due-diligence statuses, evidence baselines, and attention queues
 *    are never mutated or frozen when saving or restoring a view.
 * 3. Loading a saved view dynamically queries current live workspace records under the restored parameters.
 */

export type SortOption =
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

export const VALID_FILTER_TABS: readonly FilterTab[] = [
  "ALL",
  "ATTENTION_QUEUE",
  "WITH_THESIS",
  "WITHOUT_THESIS",
  "INCOMPLETE_RECORDS",
  "MISSING_QUESTION",
  "MISSING_NEXT_STEP",
  "MISSING_SUPPORTING_EVIDENCE",
  "MISSING_RECONSIDERATION_CONDITIONS",
  "STALE_EVIDENCE",
  "NEEDS_FOLLOW_UP",
  "TO_INVESTIGATE",
  "IN_PROGRESS",
  "REVIEWED",
  "EVIDENCE_CHANGED",
  "REVIEW_NEEDED",
  "HISTORICAL_UNAVAILABLE",
  "COMPARATIVE",
  "SHORTLIST_SELECTED",
  "HELD_IN_PORTFOLIO",
  "OPTION_MISMATCH",
  "QUESTIONS",
  "NOTES",
  "DIRECT_GROWTH",
  "REGULAR_PLANS",
  "WITH_FOLLOW_UP_ISSUES",
  "WITH_RESIDUAL_UNCERTAINTY",
  "INVESTIGATION_ACTION_QUEUE",
  "ACTIONABLE_COVERAGE_GAPS",
] as const;

export const VALID_SORT_OPTIONS: readonly SortOption[] = [
  "attention_priority",
  "needs_followup_first",
  "to_investigate_first",
  "updated_desc",
  "created_desc",
  "name_asc",
  "name_desc",
  "notes_first",
] as const;

export interface WorkspaceConfiguration {
  searchQuery: string;
  filterTab: FilterTab;
  selectedAmc: string;
  sortBy: SortOption;
}

export interface SavedResearchView extends WorkspaceConfiguration {
  id: string; // Unique stable identifier, e.g. "view-1729950000000-abcd"
  name: string; // User-defined display name
  description?: string; // Optional short summary
  createdAt: string; // ISO timestamp
  updatedAt: string; // ISO timestamp
}

export const DEFAULT_VIEW_CONFIG: WorkspaceConfiguration = {
  searchQuery: "",
  filterTab: "ALL",
  selectedAmc: "ALL",
  sortBy: "updated_desc",
};

const STORAGE_PREFIX = "yukira_saved_views_";

/**
 * Derives a safe, namespaced local-storage key to partition saved views by user identity.
 * Prevents configuration leakage between authenticated accounts on shared devices.
 */
export function getSavedViewsStorageKey(userId?: string | null): string {
  if (userId && typeof userId === "string" && userId.trim().length > 0) {
    return `${STORAGE_PREFIX}${encodeURIComponent(userId.trim())}`;
  }
  return `${STORAGE_PREFIX}guest`;
}

/**
 * Validates whether a value is a supported FilterTab.
 */
export function isValidFilterTab(tab: unknown): tab is FilterTab {
  return typeof tab === "string" && (VALID_FILTER_TABS as readonly string[]).includes(tab);
}

/**
 * Validates whether a value is a supported SortOption.
 */
export function isValidSortOption(sort: unknown): sort is SortOption {
  return typeof sort === "string" && (VALID_SORT_OPTIONS as readonly string[]).includes(sort);
}

/**
 * Sanitizes and validates a raw view object. Gracefully defaults unknown or obsolete dimensions.
 */
export function sanitizeSavedView(raw: unknown): SavedResearchView | null {
  if (!raw || typeof raw !== "object") return null;
  const item = raw as Record<string, unknown>;

  if (typeof item.id !== "string" || !item.id.trim()) return null;
  if (typeof item.name !== "string" || !item.name.trim()) return null;

  const searchQuery = typeof item.searchQuery === "string" ? item.searchQuery : "";
  const filterTab: FilterTab = isValidFilterTab(item.filterTab) ? item.filterTab : "ALL";
  const selectedAmc = typeof item.selectedAmc === "string" ? item.selectedAmc : "ALL";
  const sortBy: SortOption = isValidSortOption(item.sortBy) ? item.sortBy : "updated_desc";

  const createdAt = typeof item.createdAt === "string" ? item.createdAt : new Date().toISOString();
  const updatedAt = typeof item.updatedAt === "string" ? item.updatedAt : createdAt;

  return {
    id: item.id.trim(),
    name: item.name.trim(),
    description: typeof item.description === "string" ? item.description.trim() : undefined,
    searchQuery,
    filterTab,
    selectedAmc,
    sortBy,
    createdAt,
    updatedAt,
  };
}

/**
 * Loads and sanitizes all saved research views from browser storage for the current user/guest scope.
 */
export function loadSavedViews(userId?: string | null): SavedResearchView[] {
  if (typeof window === "undefined") return [];
  const key = getSavedViewsStorageKey(userId);

  try {
    const raw = window.localStorage.getItem(key);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];

    const sanitized = parsed
      .map(sanitizeSavedView)
      .filter((v): v is SavedResearchView => v !== null);

    // Return newest updated first
    return sanitized.sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime());
  } catch (e) {
    console.error("Failed to load saved research views from storage", e);
    return [];
  }
}

/**
 * Persists saved research views to local browser storage with error resilience.
 */
export function persistSavedViews(views: SavedResearchView[], userId?: string | null): boolean {
  if (typeof window === "undefined") return false;
  const key = getSavedViewsStorageKey(userId);

  try {
    window.localStorage.setItem(key, JSON.stringify(views));
    return true;
  } catch (e) {
    console.error("Failed to persist saved research views to storage", e);
    return false;
  }
}

/**
 * Validates a proposed view name. Enforces non-empty string and case-insensitive uniqueness.
 */
export function validateViewName(
  name: string,
  existingViews: SavedResearchView[],
  currentViewId?: string | null
): { valid: boolean; error?: string } {
  const trimmed = name.trim();
  if (!trimmed) {
    return { valid: false, error: "View name cannot be empty." };
  }
  if (trimmed.length > 60) {
    return { valid: false, error: "View name must be 60 characters or fewer." };
  }

  const normalized = trimmed.toLowerCase();
  const isDuplicate = existingViews.some(
    (v) => v.id !== currentViewId && v.name.trim().toLowerCase() === normalized
  );

  if (isDuplicate) {
    return { valid: false, error: `A saved view named "${trimmed}" already exists.` };
  }

  return { valid: true };
}

/**
 * Generates a unique stable identifier for a saved view.
 */
export function generateViewId(): string {
  const timestamp = Date.now();
  const randomSuffix = Math.random().toString(36).substring(2, 8);
  return `view-${timestamp}-${randomSuffix}`;
}

/**
 * Checks whether a given view configuration strictly matches the active workspace configuration.
 */
export function isViewActive(
  viewConfig: WorkspaceConfiguration,
  current: WorkspaceConfiguration
): boolean {
  return (
    viewConfig.searchQuery.trim().toLowerCase() === current.searchQuery.trim().toLowerCase() &&
    viewConfig.filterTab === current.filterTab &&
    viewConfig.selectedAmc === current.selectedAmc &&
    viewConfig.sortBy === current.sortBy
  );
}

/**
 * Checks whether current workspace matches the default/unfiltered view.
 */
export function isDefaultWorkspace(current: WorkspaceConfiguration): boolean {
  return isViewActive(DEFAULT_VIEW_CONFIG, current);
}

/**
 * Summarizes the active configuration dimensions of a view in a compact human-readable line.
 */
export function formatViewSummary(config: WorkspaceConfiguration): string {
  const parts: string[] = [];
  if (config.filterTab !== "ALL") {
    parts.push(`Filter: ${config.filterTab.replace(/_/g, " ")}`);
  }
  if (config.searchQuery.trim()) {
    parts.push(`Search: "${config.searchQuery.trim()}"`);
  }
  if (config.selectedAmc !== "ALL") {
    parts.push(`AMC: ${config.selectedAmc}`);
  }
  if (config.sortBy !== "updated_desc") {
    parts.push(`Sort: ${config.sortBy.replace(/_/g, " ")}`);
  }
  return parts.length > 0 ? parts.join(" • ") : "All Saved Funds (Default)";
}
