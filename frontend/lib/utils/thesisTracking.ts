/**
 * YUKIRA Quantitative Intelligence Platform
 * Phase F7: Investor Research Decision Log and Thesis Tracking
 *
 * Implements lightweight research thesis and decision tracking,
 * preserving investor-authored rationale, supporting evidence,
 * unresolved questions, and reconsideration conditions without
 * confusing hypotheses with calculated facts or fabricating audit events.
 */

import { DueDiligenceStatus } from "@/lib/api/investor";
import { getWatchlistItemKey } from "@/lib/hooks/useWatchlist";

export interface ResearchThesisRecord {
  key: string; // schemeId:schemeOptionId formatted via getWatchlistItemKey
  schemeId: number;
  schemeOptionId?: number | null;
  schemeCode?: string;
  schemeName?: string;
  // Field 1: Research objective (why investigating) - mirrors reasonSaved
  researchObjective?: string | null;
  // Field 2: Working thesis / hypothesis conclusion
  workingThesis?: string | null;
  // Field 3: Key supporting evidence considered
  supportingEvidence?: string | null;
  // Field 4: Unresolved uncertainties & missing info - mirrors investigationQuestion
  unresolvedQuestions?: string | null;
  // Field 5: Reconsideration conditions / invalidation criteria
  reconsiderationConditions?: string | null;
  // Field 6: Next investigation step - mirrors nextStep
  nextStep?: string | null;
  // Associated due-diligence status
  status: DueDiligenceStatus;
  // Timestamps
  recordedAt: string;
  updatedAt: string;
  // Baseline snapshot reference if linked
  baselineSnapshotRunId?: number | null;
  baselineSnapshotAsOfDate?: string | null;
}

const THESES_STORAGE_KEY = "yukira_research_theses";

/**
 * Loads all stored thesis records from local browser storage.
 */
export function loadAllTheses(): Record<string, ResearchThesisRecord> {
  if (typeof window === "undefined") return {};
  try {
    const raw = window.localStorage.getItem(THESES_STORAGE_KEY);
    return raw ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
}

/**
 * Retrieves the working thesis record for a specific fund share class.
 */
export function getThesisRecord(
  schemeId: number,
  schemeOptionId?: number | null
): ResearchThesisRecord | null {
  const key = getWatchlistItemKey(schemeId, schemeOptionId);
  const all = loadAllTheses();
  return all[key] || null;
}

/**
 * Persists a research thesis record to browser storage.
 */
export function saveThesisRecord(record: ResearchThesisRecord): void {
  if (typeof window === "undefined") return;
  try {
    const all = loadAllTheses();
    all[record.key] = {
      ...record,
      updatedAt: new Date().toISOString(),
    };
    window.localStorage.setItem(THESES_STORAGE_KEY, JSON.stringify(all));
  } catch (err) {
    console.warn("Failed to persist research thesis record to browser storage", err);
  }
}

/**
 * Removes a research thesis record from browser storage.
 */
export function deleteThesisRecord(
  schemeId: number,
  schemeOptionId?: number | null
): void {
  if (typeof window === "undefined") return;
  try {
    const key = getWatchlistItemKey(schemeId, schemeOptionId);
    const all = loadAllTheses();
    if (all[key]) {
      delete all[key];
      window.localStorage.setItem(THESES_STORAGE_KEY, JSON.stringify(all));
    }
  } catch {}
}

/**
 * Checks whether an investor has authored an explicit working thesis.
 */
export function hasActiveThesis(
  record: ResearchThesisRecord | null | undefined
): boolean {
  if (!record || !record.workingThesis) return false;
  return record.workingThesis.trim().length > 0;
}
