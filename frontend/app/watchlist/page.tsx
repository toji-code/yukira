"use client";

import { useEffect, useState, useMemo } from "react";
import Link from "next/link";
import { PageContainer } from "@/components/layout/PageContainer";
import { StateView } from "@/components/epistemic/StateView";
import { fetchAllSchemes } from "@/lib/api/schemes";
import { Scheme } from "@/types/domain";
import { FundCard } from "@/components/primitives/FundCard";
import { useWatchlist } from "@/lib/hooks/useWatchlist";

type SortOption = "name_asc" | "name_desc" | "inception_asc" | "inception_desc";

export default function WatchlistPage() {
  const [schemes, setSchemes] = useState<Scheme[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [sortBy, setSortBy] = useState<SortOption>("name_asc");
  const { watchlistedIds, toggleWatchlist, isLoaded } = useWatchlist();

  const refreshWatchlist = () => {
    setLoading(true);
    setError(null);
    fetchAllSchemes()
      .then((data) => {
        setSchemes(data);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : "Failed to load watchlist data");
      })
      .finally(() => {
        setLoading(false);
      });
  };

  useEffect(() => {
    let active = true;
    fetchAllSchemes()
      .then((data) => {
        if (active) {
          setSchemes(data);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (active) {
          setError(err instanceof Error ? err.message : "Failed to load watchlist data");
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, []);

  const watchlistedSchemes = useMemo(() => {
    return schemes.filter((s) => watchlistedIds.includes(s.id));
  }, [schemes, watchlistedIds]);

  const filteredAndSortedSchemes = useMemo(() => {
    const q = searchQuery.toLowerCase().trim();
    let result = watchlistedSchemes;
    
    if (q) {
      result = result.filter(
        (s) =>
          s.name.toLowerCase().includes(q) ||
          s.code.toLowerCase().includes(q) ||
          (s.amc && s.amc.name && s.amc.name.toLowerCase().includes(q))
      );
    }

    return [...result].sort((a, b) => {
      if (sortBy === "name_asc") return a.name.localeCompare(b.name);
      if (sortBy === "name_desc") return b.name.localeCompare(a.name);
      if (sortBy === "inception_asc") {
        const da = a.inceptionDate ? new Date(a.inceptionDate).getTime() : 0;
        const db = b.inceptionDate ? new Date(b.inceptionDate).getTime() : 0;
        return da - db;
      }
      if (sortBy === "inception_desc") {
        const da = a.inceptionDate ? new Date(a.inceptionDate).getTime() : 0;
        const db = b.inceptionDate ? new Date(b.inceptionDate).getTime() : 0;
        return db - da;
      }
      return 0;
    });
  }, [watchlistedSchemes, searchQuery, sortBy]);

  return (
    <PageContainer
      title="My Watchlist"
      subtitle="Tracked mutual funds and saved analytical entities."
      breadcrumbs={[
        { label: "Home", href: "/" },
        { label: "Watchlist", href: "/watchlist" },
      ]}
      action={
        <div className="flex items-center gap-2">
          <Link href="/funds" className="btn btn-secondary">
            <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
            <span>Discover Funds</span>
          </Link>
          <button onClick={refreshWatchlist} disabled={loading || !isLoaded} className="btn btn-secondary">
            <svg
              className={`h-3.5 w-3.5 ${loading ? "animate-spin" : ""}`}
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              aria-hidden="true"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
              />
            </svg>
            <span>Refresh</span>
          </button>
        </div>
      }
    >
      {/* Search / sort / result count */}
      <div className="panel mb-5">
        <div className="grid grid-cols-1 gap-3 px-4 py-3 lg:grid-cols-[minmax(0,1fr)_180px_auto] lg:items-end">
          <div>
            <label htmlFor="watch-search" className="field-label">
              Search
            </label>
            <input
              id="watch-search"
              type="text"
              placeholder="Search your watchlist…"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="field field-mono"
              aria-label="Search watchlist"
              disabled={watchlistedSchemes.length === 0}
            />
          </div>

          <div>
            <label htmlFor="watch-sort" className="field-label">
              Sort
            </label>
            <select
              id="watch-sort"
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value as SortOption)}
              className="field field-mono"
              aria-label="Sort watchlist"
              disabled={watchlistedSchemes.length === 0}
            >
              <option value="name_asc">Name (A–Z)</option>
              <option value="name_desc">Name (Z–A)</option>
              <option value="inception_desc">Newest First</option>
              <option value="inception_asc">Oldest First</option>
            </select>
          </div>

          <div className="pb-2 lg:text-right">
            <span className="eyebrow block">Result Set</span>
            <span className="data-value-sm mt-1 inline-block">
              {filteredAndSortedSchemes.length} of {watchlistedSchemes.length}
            </span>
            <span className="mono-meta ml-2">saved</span>
          </div>
        </div>

        {searchQuery && (
          <div className="border-t border-border px-4 py-2">
            <button onClick={() => setSearchQuery("")} className="btn btn-ghost btn-sm">
              Clear search filter
            </button>
          </div>
        )}
      </div>

      {/* State View Rendering */}
      {loading || !isLoaded ? (
        <StateView
          kind="loading"
          title="Loading Watchlist"
          message="Retrieving your saved mutual fund schemes..."
        />
      ) : error ? (
        <StateView
          kind="unavailable"
          title="Watchlist Unavailable"
          message={`Connection to the backend service failed: ${error}. Verify that the Spring Boot backend is active.`}
          action={
            <button onClick={refreshWatchlist} className="btn btn-secondary btn-sm">
              Retry Connection
            </button>
          }
        />
      ) : watchlistedSchemes.length === 0 ? (
        <StateView
          kind="empty"
          title="Your Watchlist is Empty"
          message="You haven't saved any funds yet. Browse the fund discovery catalog to add schemes to your watchlist for quick access."
          action={
            <Link href="/funds" className="btn btn-secondary btn-sm">
              Discover Funds
            </Link>
          }
        />
      ) : filteredAndSortedSchemes.length === 0 ? (
        <StateView
          kind="empty"
          title="No Matching Saved Funds"
          message={`No saved schemes matched your search query "${searchQuery}".`}
          action={
            <button onClick={() => setSearchQuery("")} className="btn btn-secondary btn-sm">
              Clear Filter
            </button>
          }
        />
      ) : (
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
          {filteredAndSortedSchemes.map((scheme) => (
            <FundCard
              key={scheme.id}
              scheme={scheme}
              isWatchlisted={watchlistedIds.includes(scheme.id)}
              onToggleWatchlist={toggleWatchlist}
            />
          ))}
        </div>
      )}
    </PageContainer>
  );
}
