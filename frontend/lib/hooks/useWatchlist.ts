import { useState, useEffect } from 'react';
import { useUser } from '@auth0/nextjs-auth0/client';
import { addServerWatchlist, fetchServerWatchlist, removeServerWatchlist } from '../api/investor';

export function useWatchlist() {
  const [watchlistedIds, setWatchlistedIds] = useState<number[]>([]);
  const [isLoaded, setIsLoaded] = useState(false);
  const { user, isLoading } = useUser();

  useEffect(() => {
    let active = true;
    const load = async () => {
      let localIds: number[] = [];
      try {
        const stored = localStorage.getItem('yukira_watchlist');
        if (stored) {
          localIds = JSON.parse(stored);
        }
      } catch (e) {
        console.error('Failed to load local watchlist', e);
      }

      if (!isLoading && user) {
        try {
          const serverData = await fetchServerWatchlist();
          const serverIds = serverData.map((d) => d.schemeId);
          
          // Migrate local to server
          const newIds = localIds.filter(id => !serverIds.includes(id));
          for (const id of newIds) {
            await addServerWatchlist(id);
            serverIds.push(id);
          }
          if (newIds.length > 0) {
            localStorage.removeItem('yukira_watchlist');
          }
          
          if (active) setWatchlistedIds(serverIds);
        } catch (e) {
          console.error('Failed to load/sync server watchlist', e);
          if (active) setWatchlistedIds(localIds);
        }
      } else if (!isLoading) {
        if (active) setWatchlistedIds(localIds);
      }
      if (!isLoading && active) setIsLoaded(true);
    };
    load();
    return () => { active = false; };
  }, [user, isLoading]);

  const toggleWatchlist = async (schemeId: number) => {
    const isSaved = watchlistedIds.includes(schemeId);
    
    // Optimistic UI update
    setWatchlistedIds((prev) => isSaved ? prev.filter(id => id !== schemeId) : [...prev, schemeId]);

    if (user) {
      try {
        if (isSaved) {
          await removeServerWatchlist(schemeId);
        } else {
          await addServerWatchlist(schemeId);
        }
      } catch (e) {
        console.error('Failed to sync watchlist toggle to server', e);
        // Revert on failure
        setWatchlistedIds((prev) => isSaved ? [...prev, schemeId] : prev.filter(id => id !== schemeId));
      }
    } else {
      setWatchlistedIds((prev) => {
        try {
          localStorage.setItem('yukira_watchlist', JSON.stringify(prev));
        } catch {}
        return prev;
      });
    }
  };

  const isWatchlisted = (schemeId: number) => watchlistedIds.includes(schemeId);

  return { watchlistedIds, isWatchlisted, toggleWatchlist, isLoaded };
}
