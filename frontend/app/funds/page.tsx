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
type ScoreFilterOption = "ALL" | "SCORE_AVAILABLE" | "SCORE_UNAVAILABLE";

export default function FundsCatalogPage() {
  const [schemes, setSchemes] = useState<Scheme[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [sortBy, setSortBy] = useState<SortOption>("name_asc");
  const [scoreFilter, setScoreFilter] = useState<ScoreFilterOption>("ALL");
  const { watchlistedIds, toggleWatchlist } = useWatchlist();

  const refreshCatalog = () => {
    setLoading(true);
    setError(null);
    fetchAllSchemes()
      .then((data) => {
        setSchemes(data);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : "Failed to load mutual fund catalog");
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
          setError(err instanceof Error ? err.message : "Failed to load mutual fund catalog");
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, []);

  const filteredAndSortedSchemes = useMemo(() => {
    const q = searchQuery.toLowerCase().trim();
    let result = schemes;

    if (q) {
      result = result.filter(
        (s) =>
          s.name.toLowerCase().includes(q) ||
          s.code.toLowerCase().includes(q) ||
          (s.amc && s.amc.name && s.amc.name.toLowerCase().includes(q))
      );
    }

    if (scoreFilter === "SCORE_AVAILABLE") {
      result = result.filter(
        (s) =>
          s.yukiraScore &&
          s.yukiraScore.score !== null &&
          s.yukiraScore.score !== undefined &&
          s.yukiraScore.status !== "INSUFFICIENT_DATA"
      );
    } else if (scoreFilter === "SCORE_UNAVAILABLE") {
      result = result.filter(
        (s) =>
          !s.yukiraScore ||
          s.yukiraScore.score === null ||
          s.yukiraScore.score === undefined ||
          s.yukiraScore.status === "INSUFFICIENT_DATA"
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
  }, [schemes, searchQuery, sortBy, scoreFilter]);

  return (
    <PageContainer
      title="Fund Discovery Catalog"
      subtitle="Research directory of registered mutual fund schemes from official AMFI master records."
      breadcrumbs={[
        { label: "Home", href: "/" },
        { label: "Funds", href: "/funds" },
      ]}
      action={
        <div className="flex items-center gap-2">
          <Link href="/compare" className="btn btn-secondary">
            <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01" />
            </svg>
            <span>Compare Funds</span>
          </Link>
          <button onClick={refreshCatalog} disabled={loading} className="btn btn-secondary">
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
      {/* Verified master records mandate */}
      <div className="panel mb-5">
        <div className="panel-header">
          <p className="eyebrow">Verified Master Records Mandate</p>
          <span className="mono-meta">PostgreSQL 17 · ddl-auto=validate</span>
        </div>
        <p className="max-w-[86ch] px-4 py-3 text-[12.5px] leading-[1.6] text-text-secondary">
          This catalog reflects authoritative master entities populated via official AMFI ingestion
          feeds. If no records are found or the backend is offline, YUKIRA refuses to generate
          fabricated placeholder funds.
        </p>
      </div>

      {/* Search / sort / filter / result count */}
      <div className="panel mb-5">
        <div className="grid grid-cols-1 gap-3 px-4 py-3 lg:grid-cols-[minmax(0,1fr)_180px_180px_auto] lg:items-end">
          <div>
            <label htmlFor="fund-search" className="field-label">
              Search
            </label>
            <input
              id="fund-search"
              type="text"
              placeholder="Scheme name, code, or AMC…"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="field field-mono"
              aria-label="Search mutual funds"
            />
          </div>

          <div>
            <label htmlFor="score-filter" className="field-label">
              YUKIRA Score
            </label>
            <select
              id="score-filter"
              value={scoreFilter}
              onChange={(e) => setScoreFilter(e.target.value as ScoreFilterOption)}
              className="field field-mono"
              aria-label="Filter schemes by YUKIRA score availability"
            >
              <option value="ALL">All Schemes</option>
              <option value="SCORE_AVAILABLE">Score Available</option>
              <option value="SCORE_UNAVAILABLE">Score Unavailable</option>
            </select>
          </div>

          <div>
            <label htmlFor="fund-sort" className="field-label">
              Sort
            </label>
            <select
              id="fund-sort"
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value as SortOption)}
              className="field field-mono"
              aria-label="Sort schemes"
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
              {filteredAndSortedSchemes.length} of {schemes.length}
            </span>
            <span className="mono-meta ml-2">schemes</span>
          </div>
        </div>

        {(searchQuery || scoreFilter !== "ALL") && (
          <div className="border-t border-border px-4 py-2 flex items-center gap-2">
            <button
              onClick={() => {
                setSearchQuery("");
                setScoreFilter("ALL");
              }}
              className="btn btn-ghost btn-sm"
            >
              Clear filters
            </button>
          </div>
        )}
      </div>

      {/* State View Rendering */}
      {loading ? (
        <StateView
          kind="loading"
          title="Connecting to Backend Master Records"
          message="Retrieving verified mutual fund schemes from Spring Boot backend repository..."
        />
      ) : error ? (
        <StateView
          kind="unavailable"
          title="Fund Catalog Unavailable"
          message={`Connection to the backend service failed: ${error}. Verify that the Spring Boot backend is active.`}
          action={
            <button onClick={refreshCatalog} className="btn btn-secondary btn-sm">
              Retry Connection
            </button>
          }
        />
      ) : schemes.length === 0 ? (
        <StateView
          kind="empty"
          title="No Scheme Records Found"
          message="The master scheme repository currently contains zero scheme records. Ensure database migrations and AMFI data ingestion have been executed."
        />
      ) : filteredAndSortedSchemes.length === 0 ? (
        <StateView
          kind="empty"
          title="No Matching Schemes Found"
          message={`No registered scheme records matched your search query "${searchQuery}".`}
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
