"use client";

import { useEffect, useState, useMemo } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { PageContainer } from "@/components/layout/PageContainer";
import { StateView } from "@/components/epistemic/StateView";
import { fetchAllSchemes } from "@/lib/api/schemes";
import { Scheme } from "@/types/domain";
import { useWatchlist, getWatchlistItemKey } from "@/lib/hooks/useWatchlist";
import { WatchlistResponse } from "@/lib/api/investor";
import { ResearchNoteModal } from "@/components/watchlist/ResearchNoteModal";
import { formatCurrency } from "@/lib/utils/formatters";

type SortOption = "name_asc" | "name_desc" | "inception_asc" | "inception_desc" | "notes_first";

export default function WatchlistPage() {
  const router = useRouter();
  const [schemes, setSchemes] = useState<Scheme[]>([]);
  const [loadingSchemes, setLoadingSchemes] = useState(true);
  const [searchQuery, setSearchQuery] = useState("");
  const [sortBy, setSortBy] = useState<SortOption>("name_asc");
  const [selectedForCompare, setSelectedForCompare] = useState<string[]>([]);

  // Active note editing modal state
  const [editingFund, setEditingFund] = useState<WatchlistResponse | null>(null);

  const {
    watchlistItems,
    removeFromWatchlist,
    saveResearchNote,
    refreshWatchlist,
    isLoaded,
    syncStatus,
    error,
  } = useWatchlist();

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
        amcName: item.amcName || scheme?.amc?.name || null,
        yukiraScore: item.yukiraScore !== undefined && item.yukiraScore !== null
          ? item.yukiraScore
          : (scheme?.yukiraScore?.score ?? null),
        yukiraScoreStatus: item.yukiraScoreStatus || (scheme?.yukiraScore?.status ?? null),
      };
    });
  }, [watchlistItems, schemes]);

  const filteredAndSortedItems = useMemo(() => {
    const q = searchQuery.toLowerCase().trim();
    let result = displayItems;

    if (q) {
      result = result.filter(
        (item) =>
          item.schemeName.toLowerCase().includes(q) ||
          item.schemeCode.toLowerCase().includes(q) ||
          (item.amcName && item.amcName.toLowerCase().includes(q)) ||
          (item.reasonSaved && item.reasonSaved.toLowerCase().includes(q)) ||
          (item.investigationQuestion && item.investigationQuestion.toLowerCase().includes(q))
      );
    }

    return [...result].sort((a, b) => {
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
  }, [displayItems, searchQuery, sortBy]);

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

    const query = optMappings
      ? `/compare?funds=${ids}&options=${encodeURIComponent(optMappings)}`
      : `/compare?funds=${ids}`;
    router.push(query);
  };

  const notesCount = useMemo(() => {
    return displayItems.filter((i) => i.reasonSaved || i.investigationQuestion || i.nextStep).length;
  }, [displayItems]);

  const scoredCount = useMemo(() => {
    return displayItems.filter(
      (i) => i.yukiraScore !== null && i.yukiraScore !== undefined && i.yukiraScoreStatus !== "INSUFFICIENT_DATA"
    ).length;
  }, [displayItems]);

  return (
    <PageContainer
      title="Investor Research Workspace"
      subtitle="Organized mutual fund investigations, persistent research questions, and point-in-time evidence comparison."
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
            title="Refresh saved funds"
          >
            <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            <span>Refresh</span>
          </button>
        </div>
      }
    >
      {/* Persistence Status Alert if Sync Failed */}
      {syncStatus === "SYNC_FAILED" && (
        <div className="panel mb-6 p-4 bg-critical-bg/15 border border-critical-border rounded-lg flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2.5">
            <span className="status-badge state-rejected text-[11px]">SYNC FAILED</span>
            <span className="text-[12.5px] text-text-primary">
              {error || "Backend write failed. Your research is preserved locally in browser storage."}
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

      {/* Workspace Summary Metric Strip */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 mb-6">
        <div className="panel p-3.5 bg-surface">
          <span className="eyebrow block">Saved Investigations</span>
          <p className="data-value-lg mt-1 font-mono">{displayItems.length}</p>
          <span className="mono-meta text-[11px] block mt-0.5">Tracked scheme options</span>
        </div>

        <div className="panel p-3.5 bg-surface">
          <span className="eyebrow block">Research Notes</span>
          <p className="data-value-lg mt-1 font-mono text-accent">{notesCount}</p>
          <span className="mono-meta text-[11px] block mt-0.5">Funds with active questions</span>
        </div>

        <div className="panel p-3.5 bg-surface">
          <span className="eyebrow block">Scored Funds</span>
          <p className="data-value-lg mt-1 font-mono">{scoredCount} / {displayItems.length}</p>
          <span className="mono-meta text-[11px] block mt-0.5">YUKIRA analytical score ready</span>
        </div>

        <div className="panel p-3.5 bg-surface">
          <span className="eyebrow block">Persistence Mode</span>
          <div className="flex items-center gap-1.5 mt-1.5">
            {syncStatus === "SERVER_SAVED" && (
              <span className="status-badge state-approved">SERVER SAVED</span>
            )}
            {syncStatus === "SAVING" && (
              <span className="status-badge state-candidate animate-pulse">SAVING…</span>
            )}
            {syncStatus === "LOCAL_ONLY" && (
              <span className="status-badge state-candidate">LOCAL ONLY</span>
            )}
            {syncStatus === "SYNC_FAILED" && (
              <span className="status-badge state-rejected">SYNC FAILED</span>
            )}
          </div>
          <span className="mono-meta text-[11px] block mt-1">
            {syncStatus === "SERVER_SAVED" && "Isolated & confirmed by backend account"}
            {syncStatus === "SAVING" && "Writing changes to server…"}
            {syncStatus === "LOCAL_ONLY" && "Persisted in browser storage (Sign in to sync)"}
            {syncStatus === "SYNC_FAILED" && "Cached in browser storage (Retry available)"}
          </span>
        </div>
      </div>

      {/* Comparison Selector & Filter Toolbar */}
      <div className="panel mb-6">
        <div className="p-4 flex flex-wrap items-center justify-between gap-3 border-b border-border">
          <div className="flex flex-wrap items-center gap-3">
            <div className="flex items-center gap-2">
              <span className="eyebrow">Comparison Selection:</span>
              <span className="status-badge state-operational font-mono text-[11px]">
                {selectedForCompare.length} of 4 selected
              </span>
            </div>
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
            {selectedForCompare.length === 1 && (
              <span className="text-text-tertiary text-[12px] italic">
                Select 1 more fund to activate comparison
              </span>
            )}
          </div>

          {selectedForCompare.length > 0 && (
            <button
              onClick={() => setSelectedForCompare([])}
              className="btn btn-ghost btn-sm text-[11px] text-text-tertiary hover:text-text-primary"
            >
              Clear Comparison Selection
            </button>
          )}
        </div>

        {/* Search & Sort Controls */}
        <div className="grid grid-cols-1 gap-3 p-4 lg:grid-cols-[minmax(0,1fr)_200px_auto] lg:items-end">
          <div>
            <label htmlFor="search-input" className="field-label text-[12px]">
              Search Saved Funds &amp; Research Notes
            </label>
            <input
              id="search-input"
              type="text"
              placeholder="Search scheme name, code, AMC, or research questions…"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="field field-mono w-full text-[13px]"
              disabled={displayItems.length === 0}
            />
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
              <option value="name_asc">Name (A–Z)</option>
              <option value="name_desc">Name (Z–A)</option>
              <option value="notes_first">Active Notes First</option>
            </select>
          </div>

          <div className="pb-1 lg:text-right">
            <span className="eyebrow block">Filtered View</span>
            <span className="data-value-sm font-mono mt-0.5 inline-block">
              {filteredAndSortedItems.length} of {displayItems.length}
            </span>
            <span className="mono-meta ml-1.5">items</span>
          </div>
        </div>

        {searchQuery && (
          <div className="border-t border-border px-4 py-2 bg-surface-raised/40">
            <button onClick={() => setSearchQuery("")} className="btn btn-ghost btn-sm text-[11px]">
              Clear search filter
            </button>
          </div>
        )}
      </div>

      {/* State View Handling */}
      {!isLoaded || loadingSchemes ? (
        <StateView
          kind="loading"
          title="Loading Research Workspace"
          message="Retrieving your saved mutual funds, investigation notes, and verified data dates..."
        />
      ) : displayItems.length === 0 ? (
        <StateView
          kind="empty"
          title="Your Research Workspace is Empty"
          message="Save mutual funds from the discovery catalog or fund profiles to organize your research, record investigation questions, and perform point-in-time comparisons."
          action={
            <Link href="/funds" className="btn btn-primary btn-sm">
              Discover Pilot Mutual Funds
            </Link>
          }
        />
      ) : filteredAndSortedItems.length === 0 ? (
        <StateView
          kind="empty"
          title="No Matching Funds Found"
          message={`No saved funds or notes matched your query "${searchQuery}".`}
          action={
            <button onClick={() => setSearchQuery("")} className="btn btn-secondary btn-sm">
              Clear Filter
            </button>
          }
        />
      ) : (
        <div className="space-y-4">
          {filteredAndSortedItems.map((item) => {
            const itemKey = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
            const isComparing = selectedForCompare.includes(itemKey);
            const hasNotes = Boolean(item.reasonSaved || item.investigationQuestion || item.nextStep);

            return (
              <div
                key={itemKey}
                className={`panel p-4 md:p-5 transition-all border ${
                  isComparing ? "border-accent ring-1 ring-accent/30 bg-surface-raised/40" : "hover:border-text-primary/30"
                }`}
              >
                <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                  {/* Left Column: Fund Identity & Badges */}
                  <div className="min-w-0 flex-1 space-y-3">
                    <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] font-mono">
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
                      <span className="text-text-tertiary">•</span>
                      <span className="text-text-secondary">{item.planType || "DIRECT"} {item.optionType || "GROWTH"}</span>
                      {item.schemeOptionId && (
                        <>
                          <span className="text-text-tertiary">•</span>
                          <span className="text-text-secondary">Option #{item.schemeOptionId}</span>
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
                    </div>

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
                          <span className="text-text-tertiary">Unavailable</span>
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
                    </div>

                    {/* Research Note Section */}
                    <div className="panel-inset p-3 bg-surface-raised/60 rounded-lg space-y-2 border border-border">
                      <div className="flex items-center justify-between">
                        <span className="eyebrow text-[10px] flex items-center gap-1.5 text-accent">
                          <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                          </svg>
                          <span>Investigation Context &amp; Notes</span>
                        </span>

                        <button
                          onClick={() => setEditingFund(item)}
                          className="btn btn-ghost btn-sm text-[11px] text-accent hover:underline py-0.5 px-2"
                        >
                          {hasNotes ? "Edit Note" : "+ Record Research Question"}
                        </button>
                      </div>

                      {hasNotes ? (
                        <div className="space-y-1.5 text-[12px] leading-relaxed">
                          {item.reasonSaved && (
                            <div>
                              <span className="mono-meta text-[10px] text-text-tertiary block">WHY INVESTIGATING:</span>
                              <p className="text-text-primary font-sans">{item.reasonSaved}</p>
                            </div>
                          )}
                          {item.investigationQuestion && (
                            <div className="pt-1">
                              <span className="mono-meta text-[10px] text-accent block">UNANSWERED QUESTION:</span>
                              <p className="text-text-primary font-sans font-medium">{item.investigationQuestion}</p>
                            </div>
                          )}
                          {item.nextStep && (
                            <div className="pt-1">
                              <span className="mono-meta text-[10px] text-text-tertiary block">NEXT STEP:</span>
                              <p className="text-text-secondary font-sans">{item.nextStep}</p>
                            </div>
                          )}
                        </div>
                      ) : (
                        <p className="text-[11.5px] font-mono text-text-tertiary italic">
                          No research notes recorded yet. Click &ldquo;Record Research Question&rdquo; to attach due-diligence context.
                        </p>
                      )}
                    </div>
                  </div>

                  {/* Right Column: Actions */}
                  <div className="flex flex-row lg:flex-col items-center lg:items-end gap-2 shrink-0 pt-2 lg:pt-0">
                    <Link
                      href={`/funds/${item.schemeId}${item.schemeOptionId ? `?option=${item.schemeOptionId}` : ""}`}
                      className="btn btn-secondary btn-sm"
                    >
                      <span>Open Profile</span>
                    </Link>

                    <Link
                      href={`/compare?funds=${item.schemeOptionId || item.schemeId}`}
                      className="btn btn-secondary btn-sm"
                    >
                      <span>Compare</span>
                    </Link>

                    <button
                      onClick={() => removeFromWatchlist(item.schemeId, item.schemeOptionId)}
                      className="btn btn-ghost btn-sm text-text-tertiary hover:text-critical-fg"
                      title="Remove from Workspace"
                      aria-label={`Remove ${item.schemeName} from workspace`}
                    >
                      <span>Remove</span>
                    </button>
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
    </PageContainer>
  );
}
