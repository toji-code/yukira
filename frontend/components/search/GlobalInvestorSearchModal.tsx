'use client';

import React, { useState, useEffect, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { globalSearch, GlobalSearchResultDto } from '@/lib/api/investor';

interface GlobalInvestorSearchModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export function GlobalInvestorSearchModal({ isOpen, onClose }: GlobalInvestorSearchModalProps) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<GlobalSearchResultDto[]>([]);
  const [loading, setLoading] = useState(false);
  const router = useRouter();
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (isOpen) {
      setTimeout(() => inputRef.current?.focus(), 50);
    } else {
      setQuery('');
      setResults([]);
    }
  }, [isOpen]);

  useEffect(() => {
    if (!query.trim() || query.trim().length < 2) {
      setResults([]);
      return;
    }

    const timer = setTimeout(async () => {
      setLoading(true);
      try {
        const res = await globalSearch(query.trim());
        setResults(res);
      } catch (err) {
        console.error(err);
      } finally {
        setLoading(false);
      }
    }, 200);

    return () => clearTimeout(timer);
  }, [query]);

  if (!isOpen) return null;

  function handleSelect(url: string) {
    onClose();
    router.push(url);
  }

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center pt-24 bg-black/75 backdrop-blur-sm p-4">
      <div className="relative w-full max-w-xl bg-zinc-950 border border-zinc-800 rounded-xl shadow-2xl overflow-hidden flex flex-col">
        {/* Search Input Bar */}
        <div className="flex items-center px-4 py-3 border-b border-zinc-800 bg-zinc-900/50">
          <span className="text-zinc-500 mr-2.5">🔍</span>
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search mutual funds, saved comparisons, actions, monitoring alerts..."
            className="flex-1 bg-transparent text-sm text-zinc-100 placeholder-zinc-500 focus:outline-none"
          />
          <button
            onClick={onClose}
            className="text-xs text-zinc-500 hover:text-zinc-300 px-2 py-1 bg-zinc-800/60 rounded"
          >
            ESC
          </button>
        </div>

        {/* Results List */}
        <div className="max-h-96 overflow-y-auto p-2">
          {loading ? (
            <div className="py-8 text-center text-xs text-zinc-500">Searching across entities...</div>
          ) : query.trim().length < 2 ? (
            <div className="py-8 text-center text-xs text-zinc-500">
              Type at least 2 characters to search funds, comparisons, and actions.
            </div>
          ) : results.length === 0 ? (
            <div className="py-8 text-center text-xs text-zinc-500">
              No matching funds or research entities found for &quot;{query}&quot;.
            </div>
          ) : (
            <div className="space-y-1">
              {results.map((r, i) => (
                <div
                  key={i}
                  onClick={() => handleSelect(r.targetUrl)}
                  className="flex items-center justify-between p-3 rounded-lg hover:bg-zinc-900 cursor-pointer transition-colors border border-transparent hover:border-zinc-800"
                >
                  <div className="truncate mr-3">
                    <div className="text-xs font-medium text-zinc-200 truncate">{r.title}</div>
                    <div className="text-[11px] text-zinc-500 truncate">{r.subtitle}</div>
                  </div>
                  <span
                    className={`px-2 py-0.5 rounded text-[10px] font-semibold uppercase tracking-wider shrink-0 ${
                      r.badgeVariant === 'SUCCESS'
                        ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30'
                        : r.badgeVariant === 'WARNING'
                        ? 'bg-amber-500/10 text-amber-400 border border-amber-500/30'
                        : r.badgeVariant === 'PURPLE'
                        ? 'bg-purple-500/10 text-purple-400 border border-purple-500/30'
                        : 'bg-zinc-800 text-zinc-300'
                    }`}
                  >
                    {r.badgeText}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

