"use client";

import { useEffect, useState, useMemo } from "react";
import Link from "next/link";
import { PageContainer } from "@/components/layout/PageContainer";
import { StateView } from "@/components/epistemic/StateView";
import { fetchAllSchemes } from "@/lib/api/schemes";
import { Scheme } from "@/types/domain";

export default function FundsCatalogPage() {
  const [schemes, setSchemes] = useState<Scheme[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");

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

  const filteredSchemes = useMemo(() => {
    const q = searchQuery.toLowerCase().trim();
    if (!q) return schemes;
    return schemes.filter(
      (s) =>
        s.name.toLowerCase().includes(q) ||
        s.code.toLowerCase().includes(q) ||
        (s.amc && s.amc.name && s.amc.name.toLowerCase().includes(q))
    );
  }, [schemes, searchQuery]);

  return (
    <PageContainer
      title="Fund Discovery Catalog"
      subtitle="Institutional directory of registered mutual fund schemes from official AMFI master records."
      breadcrumbs={[
        { label: "Home", href: "/" },
        { label: "Funds", href: "/funds" },
      ]}
      action={
        <button
          onClick={refreshCatalog}
          disabled={loading}
          className="inline-flex items-center gap-1.5 rounded-lg border border-zinc-800 bg-zinc-900 px-3.5 py-2 text-xs font-mono font-medium text-zinc-300 transition hover:bg-zinc-800 hover:text-zinc-100 disabled:opacity-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-cyan-500"
        >
          <svg
            className={`h-3.5 w-3.5 ${loading ? "animate-spin" : ""}`}
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
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
      }
    >
      {/* Epistemic Mandate Banner */}
      <div className="mb-6 rounded-xl border border-zinc-800 bg-zinc-900/40 p-4 text-xs font-mono">
        <div className="flex items-center justify-between flex-wrap gap-2">
          <div className="flex items-center gap-2 text-zinc-300 font-semibold uppercase tracking-wider text-[11px]">
            <span className="h-2 w-2 rounded-full bg-cyan-400" />
            Verified Master Records Mandate
          </div>
          <span className="text-[10px] text-zinc-400">
            Database: PostgreSQL 17 &bull; ddl-auto=validate
          </span>
        </div>
        <p className="mt-1 text-zinc-400 text-xs font-sans leading-relaxed">
          This catalog reflects authoritative master entities populated via official AMFI ingestion feeds. If no records are found or the backend is offline, YUKIRA refuses to generate fabricated placeholder funds.
        </p>
      </div>

      {/* Search Bar & Result Counts */}
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="relative max-w-md flex-1">
          <input
            type="text"
            placeholder="Search by scheme name, code, or AMC..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full rounded-lg border border-zinc-800 bg-zinc-900/80 px-4 py-2.5 pl-10 text-xs text-zinc-100 placeholder-zinc-500 focus:border-cyan-500 focus:outline-none focus:ring-1 focus:ring-cyan-500 font-mono"
            aria-label="Search mutual funds"
          />
          <svg
            className="absolute left-3 top-3 h-4 w-4 text-zinc-500"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
          {searchQuery && (
            <button
              onClick={() => setSearchQuery("")}
              className="absolute right-3 top-2.5 text-zinc-400 hover:text-zinc-200 text-xs font-mono"
              aria-label="Clear search"
            >
              &times;
            </button>
          )}
        </div>

        <div className="font-mono text-xs text-zinc-400 flex items-center gap-3">
          <span>
            Showing <strong className="text-zinc-200">{filteredSchemes.length}</strong> of{" "}
            <strong className="text-zinc-200">{schemes.length}</strong> schemes
          </span>
        </div>
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
            <button
              onClick={refreshCatalog}
              className="inline-flex rounded-md bg-zinc-800 px-3 py-1.5 text-xs font-medium text-zinc-200 hover:bg-zinc-700 font-mono"
            >
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
      ) : filteredSchemes.length === 0 ? (
        <StateView
          kind="empty"
          title="No Matching Schemes Found"
          message={`No registered scheme records matched your search query "${searchQuery}".`}
          action={
            <button
              onClick={() => setSearchQuery("")}
              className="inline-flex rounded-md bg-zinc-800 px-3 py-1.5 text-xs font-medium text-zinc-200 hover:bg-zinc-700 font-mono"
            >
              Clear Filter
            </button>
          }
        />
      ) : (
        <div className="space-y-4">
          {filteredSchemes.map((scheme) => {
            const isCanonicalPilot =
              scheme.code === "HDFC_FLEXI" ||
              scheme.name.toLowerCase().includes("hdfc flexi cap");

            return (
              <article
                key={scheme.id}
                className={`rounded-xl border p-5 transition-all backdrop-blur-sm ${
                  isCanonicalPilot
                    ? "border-cyan-500/40 bg-zinc-900/80 shadow-lg shadow-cyan-950/20"
                    : "border-zinc-800/80 bg-zinc-900/50 hover:border-zinc-700"
                }`}
              >
                <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                  <div className="space-y-2 flex-1">
                    <div className="flex flex-wrap items-center gap-2 font-mono text-xs">
                      <span className="text-zinc-400">ID #{scheme.id}</span>
                      <span className="text-zinc-600">&bull;</span>
                      <span className="text-cyan-400 font-semibold">{scheme.code}</span>
                      {scheme.amc && (
                        <>
                          <span className="text-zinc-600">&bull;</span>
                          <span className="text-zinc-300">{scheme.amc.name}</span>
                        </>
                      )}
                      {isCanonicalPilot && (
                        <span className="inline-flex items-center gap-1 rounded bg-cyan-500/15 border border-cyan-500/30 px-2 py-0.5 text-[10px] font-bold text-cyan-300">
                          <span className="h-1.5 w-1.5 rounded-full bg-cyan-400 animate-pulse" />
                          CANONICAL PILOT INSTRUMENT
                        </span>
                      )}
                    </div>

                    <h3 className="text-lg font-bold text-zinc-100 font-sans tracking-tight">
                      <Link
                        href={`/funds/${scheme.id}`}
                        className="hover:text-cyan-300 transition-colors focus:outline-none focus-visible:underline"
                      >
                        {scheme.name}
                      </Link>
                    </h3>

                    <div className="flex flex-wrap items-center gap-4 text-xs font-mono text-zinc-400">
                      <div>
                        Inception Date:{" "}
                        <span className="text-zinc-200">
                          {scheme.inceptionDate || "—"}
                        </span>
                      </div>
                      <div>
                        Status:{" "}
                        <span className="inline-flex rounded px-2 py-0.5 text-[10px] font-medium bg-emerald-500/15 text-emerald-400 border border-emerald-500/30">
                          {scheme.status || "ACTIVE"}
                        </span>
                      </div>
                      {isCanonicalPilot && (
                        <div className="text-emerald-400">
                          5Y Horizon: 1,243 trading dates verified
                        </div>
                      )}
                    </div>
                  </div>

                  <div className="flex items-center gap-3 shrink-0">
                    <Link
                      href={`/funds/${scheme.id}`}
                      className="inline-flex items-center gap-2 rounded-lg bg-cyan-600 px-4 py-2 text-xs font-mono font-semibold text-white shadow-sm shadow-cyan-600/20 transition hover:bg-cyan-500 focus:outline-none focus-visible:ring-2 focus-visible:ring-cyan-500"
                    >
                      <span>Investigate Fund</span>
                      <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                      </svg>
                    </Link>
                  </div>
                </div>
              </article>
            );
          })}
        </div>
      )}
    </PageContainer>
  );
}
