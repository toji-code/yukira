import { useState, useEffect, useCallback, useRef } from 'react';
import { useUser } from '@auth0/nextjs-auth0/client';
import {
  addServerWatchlist,
  fetchServerWatchlist,
  removeServerWatchlist,
  removeServerWatchlistByOption,
  updateServerWatchlistNote,
  updateServerWatchlistNoteByOption,
  WatchlistResponse,
  WatchlistNoteRequest,
  DueDiligenceStatus,
} from '../api/investor';
import {
  loadAllTheses,
  saveThesisRecord,
  deleteThesisRecord,
  getThesisRecord,
} from '../utils/thesisTracking';

export type SyncStatus = 'SERVER_SAVED' | 'SAVING' | 'LOCAL_ONLY' | 'SYNC_FAILED';

export interface LocalWatchlistItem {
  id?: number;
  schemeId: number;
  schemeCode?: string;
  schemeName?: string;
  category?: string | null;
  amcName?: string | null;
  schemeOptionId?: number | null;
  optionType?: string | null;
  planType?: string | null;
  amfiCode?: string | null;
  isin?: string | null;
  latestNav?: number | null;
  latestNavDate?: string | null;
  yukiraScore?: number | null;
  yukiraScoreStatus?: string | null;
  reasonSaved?: string | null;
  investigationQuestion?: string | null;
  nextStep?: string | null;
  investigationStatus?: DueDiligenceStatus | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  // F7 Investor Thesis Fields
  workingThesis?: string | null;
  supportingEvidence?: string | null;
  reconsiderationConditions?: string | null;
  thesisUpdatedAt?: string | null;
}

export function getWatchlistItemKey(schemeId: number, schemeOptionId?: number | null): string {
  return schemeOptionId ? `opt-${schemeOptionId}` : `scheme-${schemeId}`;
}

export function getResearchProgressMap(): Record<string, DueDiligenceStatus> {
  if (typeof window === 'undefined') return {};
  try {
    const stored = localStorage.getItem('yukira_research_progress');
    if (stored) {
      return JSON.parse(stored);
    }
  } catch {}
  return {};
}

export function saveResearchProgressEntry(key: string, status: DueDiligenceStatus): void {
  if (typeof window === 'undefined') return;
  try {
    const current = getResearchProgressMap();
    current[key] = status;
    localStorage.setItem('yukira_research_progress', JSON.stringify(current));
  } catch {}
}

export function useWatchlist() {
  const [watchlistItems, setWatchlistItems] = useState<WatchlistResponse[]>([]);
  const [isLoaded, setIsLoaded] = useState(false);
  const [syncStatus, setSyncStatus] = useState<SyncStatus>('LOCAL_ONLY');
  const [error, setError] = useState<string | null>(null);
  const { user, isLoading } = useUser();
  const previousUserRef = useRef<string | null>(null);

  const loadLocalWatchlist = useCallback((): LocalWatchlistItem[] => {
    try {
      const stored = localStorage.getItem('yukira_watchlist_items');
      const progressMap = getResearchProgressMap();
      const thesesMap = loadAllTheses();
      if (stored) {
        const parsed: LocalWatchlistItem[] = JSON.parse(stored);
        return parsed.map((item) => {
          const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
          const thesis = thesesMap[key];
          return {
            ...item,
            investigationStatus: item.investigationStatus || progressMap[key] || 'TO_INVESTIGATE',
            workingThesis: item.workingThesis || thesis?.workingThesis || null,
            supportingEvidence: item.supportingEvidence || thesis?.supportingEvidence || null,
            reconsiderationConditions: item.reconsiderationConditions || thesis?.reconsiderationConditions || null,
            thesisUpdatedAt: item.thesisUpdatedAt || thesis?.updatedAt || null,
          };
        });
      }
      // Backward compatibility with ID-only array
      const oldStored = localStorage.getItem('yukira_watchlist');
      if (oldStored) {
        const ids: number[] = JSON.parse(oldStored);
        return ids.map((id) => ({
          schemeId: id,
          schemeName: `Scheme #${id}`,
          schemeCode: `SCHEME_${id}`,
          investigationStatus: progressMap[`scheme-${id}`] || 'TO_INVESTIGATE',
        }));
      }
    } catch (e) {
      console.error('Failed to load local watchlist from storage', e);
    }
    return [];
  }, []);

  const saveLocalWatchlist = useCallback((items: LocalWatchlistItem[]) => {
    try {
      localStorage.setItem('yukira_watchlist_items', JSON.stringify(items));
      localStorage.setItem('yukira_watchlist', JSON.stringify(items.map((i) => i.schemeId)));
    } catch (e) {
      console.error('Failed to save local watchlist', e);
    }
  }, []);

  const refreshWatchlist = useCallback(async () => {
    setError(null);
    const progressMap = getResearchProgressMap();
    const thesesMap = loadAllTheses();
    if (!user) {
      const local = loadLocalWatchlist();
      const mapped = local.map((l, idx) => {
        const key = getWatchlistItemKey(l.schemeId, l.schemeOptionId);
        const thesis = thesesMap[key];
        return {
          id: l.id || -(idx + 1),
          schemeId: l.schemeId,
          schemeCode: l.schemeCode || `SCHEME_${l.schemeId}`,
          schemeName: l.schemeName || `Scheme #${l.schemeId}`,
          category: l.category || null,
          amcName: l.amcName || null,
          schemeOptionId: l.schemeOptionId || null,
          optionType: l.optionType || 'GROWTH',
          planType: l.planType || 'DIRECT',
          amfiCode: l.amfiCode || null,
          isin: l.isin || null,
          latestNav: l.latestNav || null,
          latestNavDate: l.latestNavDate || null,
          yukiraScore: l.yukiraScore || null,
          yukiraScoreStatus: l.yukiraScoreStatus || 'CANDIDATE',
          reasonSaved: l.reasonSaved || null,
          investigationQuestion: l.investigationQuestion || null,
          nextStep: l.nextStep || null,
          investigationStatus: l.investigationStatus || progressMap[key] || 'TO_INVESTIGATE',
          workingThesis: l.workingThesis || thesis?.workingThesis || null,
          supportingEvidence: l.supportingEvidence || thesis?.supportingEvidence || null,
          reconsiderationConditions: l.reconsiderationConditions || thesis?.reconsiderationConditions || null,
          thesisUpdatedAt: l.thesisUpdatedAt || thesis?.updatedAt || null,
          createdAt: l.createdAt || null,
          updatedAt: l.updatedAt || null,
        };
      });
      setWatchlistItems(mapped);
      setSyncStatus('LOCAL_ONLY');
      return;
    }

    try {
      setSyncStatus('SAVING');
      const serverData = await fetchServerWatchlist();
      const enriched = serverData.map((d) => {
        const key = getWatchlistItemKey(d.schemeId, d.schemeOptionId);
        const thesis = thesesMap[key];
        return {
          ...d,
          investigationStatus: d.investigationStatus || progressMap[key] || 'TO_INVESTIGATE',
          workingThesis: d.workingThesis || thesis?.workingThesis || null,
          supportingEvidence: d.supportingEvidence || thesis?.supportingEvidence || null,
          reconsiderationConditions: d.reconsiderationConditions || thesis?.reconsiderationConditions || null,
          thesisUpdatedAt: d.thesisUpdatedAt || thesis?.updatedAt || null,
        };
      });
      setWatchlistItems(enriched);
      setSyncStatus('SERVER_SAVED');
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Server synchronization failed';
      setError(msg);
      setSyncStatus('SYNC_FAILED');
      // Do not wipe locally loaded items if server fetch fails
    }
  }, [user, loadLocalWatchlist]);

  // Handle initialization and user transitions (preventing cross-user leakage)
  useEffect(() => {
    let active = true;

    if (isLoading) return;

    const currentUserId = user?.sub || null;
    const previousUserId = previousUserRef.current;

    // Detect user sign-out or account switch
    if (previousUserId !== null && previousUserId !== currentUserId) {
      setWatchlistItems([]);
    }
    previousUserRef.current = currentUserId;

    const localItems = loadLocalWatchlist();
    const progressMap = getResearchProgressMap();
    const thesesMap = loadAllTheses();
    const mappedLocal: WatchlistResponse[] = localItems.map((l, idx) => {
      const key = getWatchlistItemKey(l.schemeId, l.schemeOptionId);
      const thesis = thesesMap[key];
      return {
        id: l.id || -(idx + 1),
        schemeId: l.schemeId,
        schemeCode: l.schemeCode || `SCHEME_${l.schemeId}`,
        schemeName: l.schemeName || `Scheme #${l.schemeId}`,
        category: l.category || null,
        amcName: l.amcName || null,
        schemeOptionId: l.schemeOptionId || null,
        optionType: l.optionType || 'GROWTH',
        planType: l.planType || 'DIRECT',
        amfiCode: l.amfiCode || null,
        isin: l.isin || null,
        latestNav: l.latestNav || null,
        latestNavDate: l.latestNavDate || null,
        yukiraScore: l.yukiraScore || null,
        yukiraScoreStatus: l.yukiraScoreStatus || 'CANDIDATE',
        reasonSaved: l.reasonSaved || null,
        investigationQuestion: l.investigationQuestion || null,
        nextStep: l.nextStep || null,
        investigationStatus: l.investigationStatus || progressMap[key] || 'TO_INVESTIGATE',
        workingThesis: l.workingThesis || thesis?.workingThesis || null,
        supportingEvidence: l.supportingEvidence || thesis?.supportingEvidence || null,
        reconsiderationConditions: l.reconsiderationConditions || thesis?.reconsiderationConditions || null,
        thesisUpdatedAt: l.thesisUpdatedAt || thesis?.updatedAt || null,
        createdAt: l.createdAt || null,
        updatedAt: l.updatedAt || null,
      };
    });

    const init = async () => {
      await Promise.resolve();
      if (!active) return;

      if (!user) {
        setWatchlistItems(mappedLocal);
        setSyncStatus('LOCAL_ONLY');
        setIsLoaded(true);
        return;
      }

      try {
        const serverData = await fetchServerWatchlist();
        const thesesMap = loadAllTheses();
        const enrichedServerData = serverData.map((d) => {
          const key = getWatchlistItemKey(d.schemeId, d.schemeOptionId);
          const thesis = thesesMap[key];
          return {
            ...d,
            investigationStatus: d.investigationStatus || progressMap[key] || 'TO_INVESTIGATE',
            workingThesis: d.workingThesis || thesis?.workingThesis || null,
            supportingEvidence: d.supportingEvidence || thesis?.supportingEvidence || null,
            reconsiderationConditions: d.reconsiderationConditions || thesis?.reconsiderationConditions || null,
            thesisUpdatedAt: d.thesisUpdatedAt || thesis?.updatedAt || null,
          };
        });

        // Migrate local items created while guest if not already on server
        const migratedData = [...enrichedServerData];
        const existingKeys = new Set(
          serverData.map((d) => getWatchlistItemKey(d.schemeId, d.schemeOptionId))
        );
        const successfullyHandledKeys = new Set<string>();

        for (const local of mappedLocal) {
          const key = getWatchlistItemKey(local.schemeId, local.schemeOptionId);
          if (!existingKeys.has(key)) {
            try {
              const created = await addServerWatchlist({
                schemeId: local.schemeId,
                schemeOptionId: local.schemeOptionId || undefined,
                reasonSaved: local.reasonSaved || undefined,
                investigationQuestion: local.investigationQuestion || undefined,
                nextStep: local.nextStep || undefined,
                investigationStatus: local.investigationStatus || undefined,
              });
              migratedData.push({
                ...created,
                investigationStatus: local.investigationStatus || 'TO_INVESTIGATE',
                workingThesis: local.workingThesis || null,
                supportingEvidence: local.supportingEvidence || null,
                reconsiderationConditions: local.reconsiderationConditions || null,
                thesisUpdatedAt: local.thesisUpdatedAt || null,
              });
              existingKeys.add(key);
              successfullyHandledKeys.add(key);
            } catch (migrationErr) {
              console.warn(`Failed to migrate local watchlist entry ${key}`, migrationErr);
            }
          } else {
            // Server-authored record already exists for this exact scheme option.
            // Preserving existing server-authored notes deterministically without overwriting.
            successfullyHandledKeys.add(key);
          }
        }

        // Only clear guest entries from local storage that were successfully migrated or confirmed on server
        const remainingLocal = localItems.filter((item) => {
          const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
          return !successfullyHandledKeys.has(key);
        });

        if (remainingLocal.length === 0) {
          try {
            localStorage.removeItem('yukira_watchlist_items');
            localStorage.removeItem('yukira_watchlist');
          } catch {}
        } else {
          saveLocalWatchlist(remainingLocal);
        }

        if (active) {
          setWatchlistItems(migratedData);
          if (remainingLocal.length > 0) {
            setError(`${remainingLocal.length} local items could not be synchronized to your account and remain in browser storage.`);
            setSyncStatus('SYNC_FAILED');
          } else {
            setSyncStatus('SERVER_SAVED');
          }
          setIsLoaded(true);
        }
      } catch (err: unknown) {
        if (active) {
          // If server fails, keep local items visible and flag SYNC_FAILED
          setWatchlistItems(mappedLocal);
          setError(err instanceof Error ? err.message : 'Failed to retrieve saved research from server.');
          setSyncStatus('SYNC_FAILED');
          setIsLoaded(true);
        }
      }
    };

    init();

    return () => {
      active = false;
    };
  }, [user, isLoading, loadLocalWatchlist, saveLocalWatchlist]);

  const isWatchlisted = useCallback(
    (schemeId: number, schemeOptionId?: number | null) => {
      if (schemeOptionId) {
        return watchlistItems.some((i) => i.schemeOptionId === schemeOptionId);
      }
      return watchlistItems.some((i) => i.schemeId === schemeId);
    },
    [watchlistItems]
  );

  const toggleWatchlist = async (
    schemeId: number,
    schemeOptionId?: number | null,
    metadata?: Partial<WatchlistResponse>
  ) => {
    const existing = watchlistItems.find((i) =>
      schemeOptionId ? i.schemeOptionId === schemeOptionId : i.schemeId === schemeId
    );
    const isSaved = Boolean(existing);

    if (isSaved && existing) {
      // Remove operation
      const remaining = watchlistItems.filter((i) =>
        schemeOptionId ? i.schemeOptionId !== schemeOptionId : i.schemeId !== schemeId
      );
      setWatchlistItems(remaining);

      // Update local storage
      const local = loadLocalWatchlist().filter((i) =>
        schemeOptionId ? i.schemeOptionId !== schemeOptionId : i.schemeId !== schemeId
      );
      saveLocalWatchlist(local);
      deleteThesisRecord(schemeId, schemeOptionId);

      if (user) {
        try {
          setSyncStatus('SAVING');
          if (schemeOptionId) {
            await removeServerWatchlistByOption(schemeOptionId);
          } else {
            await removeServerWatchlist(schemeId);
          }
          setSyncStatus('SERVER_SAVED');
        } catch (err: unknown) {
          const msg = err instanceof Error ? err.message : 'Failed to remove from server';
          setError(msg);
          setSyncStatus('SYNC_FAILED');
        }
      } else {
        setSyncStatus('LOCAL_ONLY');
      }
    } else {
      // Add operation
      const status = metadata?.investigationStatus || 'TO_INVESTIGATE';
      const key = getWatchlistItemKey(schemeId, schemeOptionId);
      saveResearchProgressEntry(key, status);

      const tempItem: WatchlistResponse = {
        id: -Date.now(),
        schemeId,
        schemeCode: metadata?.schemeCode || `SCHEME_${schemeId}`,
        schemeName: metadata?.schemeName || `Scheme #${schemeId}`,
        category: metadata?.category || null,
        amcName: metadata?.amcName || null,
        schemeOptionId: schemeOptionId || null,
        optionType: metadata?.optionType || 'GROWTH',
        planType: metadata?.planType || 'DIRECT',
        amfiCode: metadata?.amfiCode || null,
        isin: metadata?.isin || null,
        latestNav: metadata?.latestNav || null,
        latestNavDate: metadata?.latestNavDate || null,
        yukiraScore: metadata?.yukiraScore || null,
        yukiraScoreStatus: metadata?.yukiraScoreStatus || 'CANDIDATE',
        reasonSaved: metadata?.reasonSaved || null,
        investigationQuestion: metadata?.investigationQuestion || null,
        nextStep: metadata?.nextStep || null,
        investigationStatus: status,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };

      setWatchlistItems((prev) => [...prev, tempItem]);

      const local = loadLocalWatchlist();
      const updatedLocal: LocalWatchlistItem[] = [
        ...local.filter((i) => (schemeOptionId ? i.schemeOptionId !== schemeOptionId : i.schemeId !== schemeId)),
        {
          schemeId,
          schemeOptionId: schemeOptionId || undefined,
          schemeCode: tempItem.schemeCode,
          schemeName: tempItem.schemeName,
          category: tempItem.category,
          amcName: tempItem.amcName,
          optionType: tempItem.optionType,
          planType: tempItem.planType,
          amfiCode: tempItem.amfiCode,
          isin: tempItem.isin,
          latestNav: tempItem.latestNav,
          latestNavDate: tempItem.latestNavDate,
          yukiraScore: tempItem.yukiraScore,
          yukiraScoreStatus: tempItem.yukiraScoreStatus,
          reasonSaved: tempItem.reasonSaved,
          investigationQuestion: tempItem.investigationQuestion,
          nextStep: tempItem.nextStep,
          investigationStatus: status,
        },
      ];
      saveLocalWatchlist(updatedLocal);

      if (user) {
        try {
          setSyncStatus('SAVING');
          const serverAdded = await addServerWatchlist({
            schemeId,
            schemeOptionId: schemeOptionId || undefined,
            investigationStatus: status,
          });
          setWatchlistItems((prev) =>
            prev.map((item) =>
              (schemeOptionId ? item.schemeOptionId === schemeOptionId : item.schemeId === schemeId)
                ? { ...serverAdded, investigationStatus: status }
                : item
            )
          );
          setSyncStatus('SERVER_SAVED');
        } catch (err: unknown) {
          const msg = err instanceof Error ? err.message : 'Server write failed; saved locally in browser.';
          setError(msg);
          setSyncStatus('SYNC_FAILED');
        }
      } else {
        setSyncStatus('LOCAL_ONLY');
      }
    }
  };

  const removeFromWatchlist = async (schemeId: number, schemeOptionId?: number | null) => {
    await toggleWatchlist(schemeId, schemeOptionId);
  };

  const updateInvestigationStatus = useCallback(
    (schemeId: number, schemeOptionId: number | null | undefined, status: DueDiligenceStatus) => {
      const key = getWatchlistItemKey(schemeId, schemeOptionId);
      saveResearchProgressEntry(key, status);

      setWatchlistItems((prev) =>
        prev.map((item) => {
          const matches = schemeOptionId ? item.schemeOptionId === schemeOptionId : item.schemeId === schemeId;
          if (matches) {
            return {
              ...item,
              investigationStatus: status,
              updatedAt: new Date().toISOString(),
            };
          }
          return item;
        })
      );

      const local = loadLocalWatchlist();
      const updatedLocal = local.map((item) => {
        const matches = schemeOptionId ? item.schemeOptionId === schemeOptionId : item.schemeId === schemeId;
        if (matches) {
          return {
            ...item,
            investigationStatus: status,
            updatedAt: new Date().toISOString(),
          };
        }
        return item;
      });
      saveLocalWatchlist(updatedLocal);
    },
    [loadLocalWatchlist, saveLocalWatchlist]
  );

  const saveResearchNote = async (
    schemeId: number,
    note: WatchlistNoteRequest,
    schemeOptionId?: number | null,
    metadata?: Partial<WatchlistResponse>
  ) => {
    const existing = watchlistItems.find((i) =>
      schemeOptionId ? i.schemeOptionId === schemeOptionId : i.schemeId === schemeId
    );

    if (existing) {
      const key = getWatchlistItemKey(schemeId, schemeOptionId);
      const targetStatus: DueDiligenceStatus =
        note.investigationStatus !== undefined && note.investigationStatus !== null
          ? note.investigationStatus
          : existing.investigationStatus || 'TO_INVESTIGATE';

      saveResearchProgressEntry(key, targetStatus);

      const existingThesis = getThesisRecord(schemeId, schemeOptionId);
      const newWorkingThesis = note.workingThesis !== undefined ? note.workingThesis : (existing.workingThesis || existingThesis?.workingThesis || null);
      const newSupportingEvidence = note.supportingEvidence !== undefined ? note.supportingEvidence : (existing.supportingEvidence || existingThesis?.supportingEvidence || null);
      const newReconsiderationConditions = note.reconsiderationConditions !== undefined ? note.reconsiderationConditions : (existing.reconsiderationConditions || existingThesis?.reconsiderationConditions || null);
      const nowIso = new Date().toISOString();

      if (note.workingThesis !== undefined || note.supportingEvidence !== undefined || note.reconsiderationConditions !== undefined) {
        saveThesisRecord({
          key,
          schemeId,
          schemeOptionId,
          schemeCode: existing.schemeCode,
          schemeName: existing.schemeName,
          researchObjective: note.reasonSaved !== undefined ? note.reasonSaved : (existing.reasonSaved || null),
          workingThesis: newWorkingThesis,
          supportingEvidence: newSupportingEvidence,
          unresolvedQuestions: note.investigationQuestion !== undefined ? note.investigationQuestion : (existing.investigationQuestion || null),
          reconsiderationConditions: newReconsiderationConditions,
          nextStep: note.nextStep !== undefined ? note.nextStep : (existing.nextStep || null),
          status: targetStatus,
          recordedAt: existingThesis?.recordedAt || nowIso,
          updatedAt: nowIso,
        });
      }

      // Optimistic state update for existing item
      setWatchlistItems((prev) =>
        prev.map((item) => {
          const matches = schemeOptionId ? item.schemeOptionId === schemeOptionId : item.schemeId === schemeId;
          if (matches) {
            return {
              ...item,
              reasonSaved: note.reasonSaved !== undefined ? note.reasonSaved : item.reasonSaved,
              investigationQuestion: note.investigationQuestion !== undefined ? note.investigationQuestion : item.investigationQuestion,
              nextStep: note.nextStep !== undefined ? note.nextStep : item.nextStep,
              investigationStatus: targetStatus,
              workingThesis: newWorkingThesis,
              supportingEvidence: newSupportingEvidence,
              reconsiderationConditions: newReconsiderationConditions,
              thesisUpdatedAt: nowIso,
              updatedAt: nowIso,
            };
          }
          return item;
        })
      );

      // Update local storage copy
      const local = loadLocalWatchlist();
      const updatedLocal = local.map((item) => {
        const matches = schemeOptionId ? item.schemeOptionId === schemeOptionId : item.schemeId === schemeId;
        if (matches) {
          return {
            ...item,
            reasonSaved: note.reasonSaved !== undefined ? note.reasonSaved : item.reasonSaved,
            investigationQuestion: note.investigationQuestion !== undefined ? note.investigationQuestion : item.investigationQuestion,
            nextStep: note.nextStep !== undefined ? note.nextStep : item.nextStep,
            investigationStatus: targetStatus,
            workingThesis: newWorkingThesis,
            supportingEvidence: newSupportingEvidence,
            reconsiderationConditions: newReconsiderationConditions,
            thesisUpdatedAt: nowIso,
          };
        }
        return item;
      });
      saveLocalWatchlist(updatedLocal);

      if (user) {
        try {
          setSyncStatus('SAVING');
          let serverUpdated: WatchlistResponse;
          if (schemeOptionId) {
            serverUpdated = await updateServerWatchlistNoteByOption(schemeOptionId, note);
          } else {
            serverUpdated = await updateServerWatchlistNote(schemeId, note);
          }
          setWatchlistItems((prev) =>
            prev.map((item) =>
              (schemeOptionId ? item.schemeOptionId === schemeOptionId : item.schemeId === schemeId)
                ? {
                    ...serverUpdated,
                    investigationStatus: targetStatus,
                    workingThesis: newWorkingThesis,
                    supportingEvidence: newSupportingEvidence,
                    reconsiderationConditions: newReconsiderationConditions,
                    thesisUpdatedAt: nowIso,
                  }
                : item
            )
          );
          setSyncStatus('SERVER_SAVED');
        } catch (err: unknown) {
          const msg = err instanceof Error ? err.message : 'Server write failed; saved locally in browser.';
          setError(msg);
          setSyncStatus('SYNC_FAILED');
        }
      } else {
        setSyncStatus('LOCAL_ONLY');
      }
    } else {
      // Fund is being investigated without prior watchlist bookmark — add directly with notes
      const key = getWatchlistItemKey(schemeId, schemeOptionId);
      const targetStatus: DueDiligenceStatus = note.investigationStatus || 'TO_INVESTIGATE';
      saveResearchProgressEntry(key, targetStatus);
      const nowIso = new Date().toISOString();

      if (note.workingThesis || note.supportingEvidence || note.reconsiderationConditions) {
        saveThesisRecord({
          key,
          schemeId,
          schemeOptionId,
          schemeCode: metadata?.schemeCode,
          schemeName: metadata?.schemeName,
          researchObjective: note.reasonSaved || null,
          workingThesis: note.workingThesis || null,
          supportingEvidence: note.supportingEvidence || null,
          unresolvedQuestions: note.investigationQuestion || null,
          reconsiderationConditions: note.reconsiderationConditions || null,
          nextStep: note.nextStep || null,
          status: targetStatus,
          recordedAt: nowIso,
          updatedAt: nowIso,
        });
      }

      const tempItem: WatchlistResponse = {
        id: -Date.now(),
        schemeId,
        schemeCode: metadata?.schemeCode || `SCHEME_${schemeId}`,
        schemeName: metadata?.schemeName || `Scheme #${schemeId}`,
        category: metadata?.category || null,
        amcName: metadata?.amcName || null,
        schemeOptionId: schemeOptionId || null,
        optionType: metadata?.optionType || 'GROWTH',
        planType: metadata?.planType || 'DIRECT',
        amfiCode: metadata?.amfiCode || null,
        isin: metadata?.isin || null,
        latestNav: metadata?.latestNav || null,
        latestNavDate: metadata?.latestNavDate || null,
        yukiraScore: metadata?.yukiraScore || null,
        yukiraScoreStatus: metadata?.yukiraScoreStatus || 'CANDIDATE',
        reasonSaved: note.reasonSaved || null,
        investigationQuestion: note.investigationQuestion || null,
        nextStep: note.nextStep || null,
        investigationStatus: targetStatus,
        workingThesis: note.workingThesis || null,
        supportingEvidence: note.supportingEvidence || null,
        reconsiderationConditions: note.reconsiderationConditions || null,
        thesisUpdatedAt: nowIso,
        createdAt: nowIso,
        updatedAt: nowIso,
      };

      setWatchlistItems((prev) => [...prev, tempItem]);

      const local = loadLocalWatchlist();
      const updatedLocal: LocalWatchlistItem[] = [
        ...local.filter((i) => (schemeOptionId ? i.schemeOptionId !== schemeOptionId : i.schemeId !== schemeId)),
        {
          schemeId,
          schemeOptionId: schemeOptionId || undefined,
          schemeCode: tempItem.schemeCode,
          schemeName: tempItem.schemeName,
          category: tempItem.category,
          amcName: tempItem.amcName,
          optionType: tempItem.optionType,
          planType: tempItem.planType,
          amfiCode: tempItem.amfiCode,
          isin: tempItem.isin,
          latestNav: tempItem.latestNav,
          latestNavDate: tempItem.latestNavDate,
          yukiraScore: tempItem.yukiraScore,
          yukiraScoreStatus: tempItem.yukiraScoreStatus,
          reasonSaved: tempItem.reasonSaved,
          investigationQuestion: tempItem.investigationQuestion,
          nextStep: tempItem.nextStep,
          investigationStatus: targetStatus,
          workingThesis: tempItem.workingThesis,
          supportingEvidence: tempItem.supportingEvidence,
          reconsiderationConditions: tempItem.reconsiderationConditions,
          thesisUpdatedAt: tempItem.thesisUpdatedAt,
          createdAt: tempItem.createdAt,
          updatedAt: tempItem.updatedAt,
        },
      ];
      saveLocalWatchlist(updatedLocal);

      if (user) {
        try {
          setSyncStatus('SAVING');
          const serverAdded = await addServerWatchlist({
            schemeId,
            schemeOptionId: schemeOptionId || undefined,
            reasonSaved: note.reasonSaved,
            investigationQuestion: note.investigationQuestion,
            nextStep: note.nextStep,
            investigationStatus: targetStatus,
          });
          setWatchlistItems((prev) =>
            prev.map((item) =>
              (schemeOptionId ? item.schemeOptionId === schemeOptionId : item.schemeId === schemeId)
                ? { ...serverAdded, investigationStatus: targetStatus }
                : item
            )
          );
          setSyncStatus('SERVER_SAVED');
        } catch (err: unknown) {
          const msg = err instanceof Error ? err.message : 'Server write failed; saved locally in browser.';
          setError(msg);
          setSyncStatus('SYNC_FAILED');
        }
      } else {
        setSyncStatus('LOCAL_ONLY');
      }
    }
  };

  const watchlistedIds = Array.from(new Set(watchlistItems.map((i) => i.schemeId)));

  return {
    watchlistItems,
    watchlistedIds,
    isWatchlisted,
    toggleWatchlist,
    removeFromWatchlist,
    saveResearchNote,
    updateInvestigationStatus,
    refreshWatchlist,
    isLoaded,
    syncStatus,
    isServerSynced: syncStatus === 'SERVER_SAVED',
    error,
  };
}
