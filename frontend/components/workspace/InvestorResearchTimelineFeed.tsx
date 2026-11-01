'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { fetchUnifiedTimeline, InvestorResearchTimelineEventDto } from '@/lib/api/investor';

export function InvestorResearchTimelineFeed() {
  const [timeline, setTimeline] = useState<InvestorResearchTimelineEventDto[]>([]);
  const [filterType, setFilterType] = useState<string>('ALL');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    loadFeed();
  }, []);

  async function loadFeed() {
    setLoading(true);
    try {
      const data = await fetchUnifiedTimeline(100);
      setTimeline(data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }

  const filtered = timeline.filter((e) => {
    if (filterType === 'ALL') return true;
    return e.eventType.startsWith(filterType);
  });

  return (
    <div className="space-y-4">
      {/* Header & Filter Bar */}
      <div className="flex flex-wrap items-center justify-between gap-3 p-4 bg-zinc-900/40 border border-zinc-800 rounded-xl">
        <div>
          <h3 className="text-sm font-semibold text-zinc-100 flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-sky-500" />
            Unified Investor Research Timeline
          </h3>
          <p className="text-xs text-zinc-400 mt-0.5">
            Cross-entity continuity feed across watchlists, thesis logs, portfolio snapshots, and monitoring (Task 191).
          </p>
        </div>
        <div className="flex items-center gap-1.5">
          {['ALL', 'WATCHLIST', 'THESIS', 'SNAPSHOT', 'MONITORING', 'ACTION'].map((f) => (
            <button
              key={f}
              onClick={() => setFilterType(f)}
              className={`px-2.5 py-1 text-[11px] font-medium rounded-md transition-colors ${
                filterType === f
                  ? 'bg-sky-500/20 text-sky-300 border border-sky-500/30'
                  : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800/60'
              }`}
            >
              {f}
            </button>
          ))}
        </div>
      </div>

      {/* Feed List */}
      {loading ? (
        <div className="py-12 text-center text-xs text-zinc-500">Aggregating research feed...</div>
      ) : filtered.length === 0 ? (
        <div className="py-12 text-center text-xs text-zinc-500 p-6 border border-zinc-800 rounded-xl bg-zinc-950">
          No research events recorded matching filter. As you analyze funds, record theses, and configure alerts, your unified research timeline forms automatically.
        </div>
      ) : (
        <div className="relative border-l border-zinc-800 ml-4 space-y-4 py-2">
          {filtered.map((item) => {
            const isWatchlist = item.eventType.includes('WATCHLIST');
            const isThesis = item.eventType.includes('THESIS');
            const isSnapshot = item.eventType.includes('SNAPSHOT');
            const isMonitoring = item.eventType.includes('MONITORING');

            const badgeColor = isWatchlist
              ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30'
              : isThesis
              ? 'bg-purple-500/10 text-purple-400 border-purple-500/30'
              : isSnapshot
              ? 'bg-sky-500/10 text-sky-400 border-sky-500/30'
              : isMonitoring
              ? 'bg-rose-500/10 text-rose-400 border-rose-500/30'
              : 'bg-zinc-800 text-zinc-300 border-zinc-700';

            return (
              <div key={item.eventId} className="relative pl-6">
                <span className="absolute -left-1.5 top-1.5 w-3 h-3 rounded-full bg-zinc-900 border-2 border-zinc-700" />
                <div className="p-3.5 bg-zinc-900/30 border border-zinc-800 rounded-lg hover:border-zinc-700/80 transition-colors">
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <span className={`px-2 py-0.5 rounded text-[10px] font-semibold border ${badgeColor}`}>
                        {item.eventType}
                      </span>
                      <span className="text-xs font-medium text-zinc-200">{item.title}</span>
                    </div>
                    <span className="text-[10px] text-zinc-500 font-mono">
                      {new Date(item.occurredAt).toLocaleString()}
                    </span>
                  </div>

                  {item.summary && <p className="text-xs text-zinc-400 mt-1.5">{item.summary}</p>}

                  {item.referenceUrl && (
                    <Link
                      href={item.referenceUrl}
                      className="inline-block text-[11px] text-sky-400 hover:text-sky-300 font-medium mt-2"
                    >
                      View Entity →
                    </Link>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

