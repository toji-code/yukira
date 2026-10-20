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
} from '../api/investor';

export type SyncStatus = 'SERVER_SAVED' | 'SAVING' | 'LOCAL_ONLY' | 'SYNC_FAILED';

export interface LocalWatchlistItem {
  id?: number;
  schemeId: number;
  schemeCode?: string;
  schemeName?: string;
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
  createdAt?: string | null;
  updatedAt?: string | null;
}

export function getWatchlistItemKey(schemeId: number, schemeOptionId?: number | null): string {
  return schemeOptionId ? `opt-${schemeOptionId}` : `scheme-${schemeId}`;
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
      if (stored) {
        return JSON.parse(stored);
      }
      // Backward compatibility with ID-only array
      const oldStored = localStorage.getItem('yukira_watchlist');
      if (oldStored) {
        const ids: number[] = JSON.parse(oldStored);
        return ids.map((id) => ({
          schemeId: id,
          schemeName: `Scheme #${id}`,
          schemeCode: `SCHEME_${id}`,
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
    if (!user) {
      const local = loadLocalWatchlist();
      const mapped = local.map((l, idx) => ({
        id: l.id || -(idx + 1),
        schemeId: l.schemeId,
        schemeCode: l.schemeCode || `SCHEME_${l.schemeId}`,
        schemeName: l.schemeName || `Scheme #${l.schemeId}`,
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
        createdAt: l.createdAt || null,
        updatedAt: l.updatedAt || null,
      }));
      setWatchlistItems(mapped);
      setSyncStatus('LOCAL_ONLY');
      return;
    }

    try {
      setSyncStatus('SAVING');
      const serverData = await fetchServerWatchlist();
      setWatchlistItems(serverData);
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
    const mappedLocal: WatchlistResponse[] = localItems.map((l, idx) => ({
      id: l.id || -(idx + 1),
      schemeId: l.schemeId,
      schemeCode: l.schemeCode || `SCHEME_${l.schemeId}`,
      schemeName: l.schemeName || `Scheme #${l.schemeId}`,
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
      createdAt: l.createdAt || null,
      updatedAt: l.updatedAt || null,
    }));

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

        // Migrate local items created while guest if not already on server
        const migratedData = [...serverData];
        const existingKeys = new Set(
          serverData.map((d) => getWatchlistItemKey(d.schemeId, d.schemeOptionId))
        );

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
              });
              migratedData.push(created);
              existingKeys.add(key);
            } catch (migrationErr) {
              console.warn(`Failed to migrate local watchlist entry ${key}`, migrationErr);
            }
          }
        }

        // Clear guest items after migration
        if (mappedLocal.length > 0) {
          try {
            localStorage.removeItem('yukira_watchlist_items');
            localStorage.removeItem('yukira_watchlist');
          } catch {}
        }

        if (active) {
          setWatchlistItems(migratedData);
          setSyncStatus('SERVER_SAVED');
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
  }, [user, isLoading, loadLocalWatchlist]);

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
      const tempItem: WatchlistResponse = {
        id: -Date.now(),
        schemeId,
        schemeCode: metadata?.schemeCode || `SCHEME_${schemeId}`,
        schemeName: metadata?.schemeName || `Scheme #${schemeId}`,
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
          amcName: tempItem.amcName,
          optionType: tempItem.optionType,
          planType: tempItem.planType,
          amfiCode: tempItem.amfiCode,
          isin: tempItem.isin,
          latestNav: tempItem.latestNav,
          latestNavDate: tempItem.latestNavDate,
          yukiraScore: tempItem.yukiraScore,
          yukiraScoreStatus: tempItem.yukiraScoreStatus,
        },
      ];
      saveLocalWatchlist(updatedLocal);

      if (user) {
        try {
          setSyncStatus('SAVING');
          const serverAdded = await addServerWatchlist({
            schemeId,
            schemeOptionId: schemeOptionId || undefined,
          });
          setWatchlistItems((prev) =>
            prev.map((item) =>
              (schemeOptionId ? item.schemeOptionId === schemeOptionId : item.schemeId === schemeId)
                ? serverAdded
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
              updatedAt: new Date().toISOString(),
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
                ? serverUpdated
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
      const tempItem: WatchlistResponse = {
        id: -Date.now(),
        schemeId,
        schemeCode: metadata?.schemeCode || `SCHEME_${schemeId}`,
        schemeName: metadata?.schemeName || `Scheme #${schemeId}`,
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
          });
          setWatchlistItems((prev) =>
            prev.map((item) =>
              (schemeOptionId ? item.schemeOptionId === schemeOptionId : item.schemeId === schemeId)
                ? serverAdded
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
    refreshWatchlist,
    isLoaded,
    syncStatus,
    isServerSynced: syncStatus === 'SERVER_SAVED',
    error,
  };
}
