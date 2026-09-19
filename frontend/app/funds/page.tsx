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
        (s.amc?.name && s.amc.name.toLowerCase().includes(q))
    );
  }, [schemes, searchQuery]);

  return (
    <PageContainer
      title="Mutual Fund Scheme Catalog"
      subtitle="Comprehensive directory of registered mutual fund schemes with registered AMFI/ISIN identifiers."
      breadcrumbs={[{ label: "Funds", href: "/funds" }]}
    >
      {/* Search Bar */}
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="relative max-w-md flex-1">
          <input
            type="text"
            placeholder="Search by scheme name, code, AMC..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full rounded-lg border border-zinc-800 bg-zinc-900/80 px-4 py-2.5 pl-10 text-sm text-zinc-100 placeholder-zinc-500 focus:border-cyan-500 focus:outline-none focus:ring-1 focus:ring-cyan-500"
          />
          <svg
            className="absolute left-3 top-3 h-4 w-4 text-zinc-500"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
        </div>

        <button
          onClick={refreshCatalog}
          className="inline-flex items-center gap-2 rounded-lg border border-zinc-800 bg-zinc-900 px-3.5 py-2 text-xs font-medium text-zinc-300 transition hover:bg-zinc-800 hover:text-zinc-100"
        >
          <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
          Refresh Catalog
        </button>
      </div>

      {/* State View Rendering */}
      {loading ? (
        <StateView
          kind="loading"
          title="Connecting to Backend API"
          message="Retrieving registered mutual fund master records from Spring Boot backend repository..."
        />
      ) : error ? (
        <StateView
          kind="unavailable"
          title="Backend Service Unreachable"
          message={error}
          action={
            <button
              onClick={refreshCatalog}
              className="inline-flex rounded-md bg-zinc-800 px-3 py-1.5 text-xs font-medium text-zinc-200 hover:bg-zinc-700"
            >
              Retry Connection
            </button>
          }
        />
      ) : schemes.length === 0 ? (
        <StateView
          kind="empty"
          title="No Schemes Registered"
          message="The master scheme repository currently contains zero scheme records. Ensure database migrations have been executed."
        />
      ) : filteredSchemes.length === 0 ? (
        <StateView
          kind="empty"
          title="No Matching Schemes Found"
          message={`No scheme records matched your filter query "${searchQuery}".`}
          action={
            <button
              onClick={() => setSearchQuery("")}
              className="inline-flex rounded-md bg-zinc-800 px-3 py-1.5 text-xs font-medium text-zinc-200 hover:bg-zinc-700"
            >
              Clear Filter
            </button>
          }
        />
      ) : (
        <div className="overflow-hidden rounded-xl border border-zinc-800 bg-zinc-900/60 shadow-lg backdrop-blur-sm">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-zinc-800 bg-zinc-950/80 font-mono uppercase tracking-wider text-zinc-400">
                <tr>
                  <th className="px-4 py-3">Code</th>
                  <th className="px-4 py-3">Scheme Name</th>
                  <th className="px-4 py-3">Asset Management Co. (AMC)</th>
                  <th className="px-4 py-3">Inception Date</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-800/60">
                {filteredSchemes.map((scheme) => (
                  <tr
                    key={scheme.id}
                    className="transition hover:bg-zinc-800/30"
                  >
                    <td className="px-4 py-3 font-mono font-medium text-cyan-400">
                      {scheme.code}
                    </td>
                    <td className="px-4 py-3 font-medium text-zinc-100">
                      <Link
                        href={`/funds/${scheme.id}`}
                        className="hover:text-cyan-300 hover:underline"
                      >
                        {scheme.name}
                      </Link>
                    </td>
                    <td className="px-4 py-3 text-zinc-300">
                      {scheme.amc?.name || "—"}
                    </td>
                    <td className="px-4 py-3 font-mono text-zinc-400">
                      {scheme.inceptionDate || "—"}
                    </td>
                    <td className="px-4 py-3">
                      <span className="inline-flex rounded px-1.5 py-0.5 text-[10px] font-medium bg-emerald-500/20 text-emerald-400">
                        {scheme.status || "ACTIVE"}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right">
                      <Link
                        href={`/funds/${scheme.id}`}
                        className="inline-flex items-center gap-1 rounded bg-zinc-800 px-2.5 py-1 text-xs font-medium text-zinc-200 transition hover:bg-zinc-700 hover:text-white"
                      >
                        Details
                        <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                        </svg>
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="border-t border-zinc-800 bg-zinc-950/40 px-4 py-2.5 text-xs text-zinc-500">
            Displaying {filteredSchemes.length} of {schemes.length} total scheme records.
          </div>
        </div>
      )}
    </PageContainer>
  );
}
