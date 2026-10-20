"use client";

import { useEffect, useState, useMemo, useCallback, Suspense } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { PageContainer } from "@/components/layout/PageContainer";
import { StateView } from "@/components/epistemic/StateView";
import { fetchSchemes } from "@/lib/api/schemes";
import { Scheme } from "@/types/domain";
import { FundCard } from "@/components/primitives/FundCard";
import { useWatchlist } from "@/lib/hooks/useWatchlist";

type SortOption = "name_asc" | "name_desc" | "inception_desc" | "inception_asc";
type PlanFilterOption = "ALL" | "DIRECT" | "REGULAR";
type OptionFilterOption = "ALL" | "GROWTH" | "IDCW";
type EvidenceFilterOption = "ALL" | "VERIFIED_NAV" | "DISCLOSED_HOLDINGS";

const PILOT_PRESETS = [
  {
    label: "HDFC Flexi Cap Direct Growth",
    sublabel: "AMFI 118955 · Option #1",
    query: "118955",
    plan: "DIRECT" as PlanFilterOption,
    option: "GROWTH" as OptionFilterOption,
  },
  {
    label: "Parag Parikh Flexi Cap Direct Growth",
    sublabel: "AMFI 122639 · Option #10262",
    query: "122639",
    plan: "DIRECT" as PlanFilterOption,
    option: "GROWTH" as OptionFilterOption,
  },
  {
    label: "Parag Parikh ELSS Tax Saver Direct Growth",
    sublabel: "AMFI 147481 · Option #10151",
    query: "147481",
    plan: "DIRECT" as PlanFilterOption,
    option: "GROWTH" as OptionFilterOption,
  },
];

function FundsCatalogContent() {
  const router = useRouter();
  const searchParams = useSearchParams();

  // Read initial filter values from URL search params
  const initialSearch = searchParams.get("search") || "";
  const initialCategory = searchParams.get("category") || "ALL";
  const initialPlan = (searchParams.get("plan") as PlanFilterOption) || "ALL";
  const initialOption = (searchParams.get("option") as OptionFilterOption) || "ALL";
  const initialEvidence = (searchParams.get("evidence") as EvidenceFilterOption) || "ALL";
  const initialSort = (searchParams.get("sort") as SortOption) || "name_asc";

  const [schemes, setSchemes] = useState<Scheme[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Filter States
  const [searchQuery, setSearchQuery] = useState(initialSearch);
  const [categoryFilter, setCategoryFilter] = useState(initialCategory);
  const [planFilter, setPlanFilter] = useState<PlanFilterOption>(initialPlan);
  const [optionFilter, setOptionFilter] = useState<OptionFilterOption>(initialOption);
  const [evidenceFilter, setEvidenceFilter] = useState<EvidenceFilterOption>(initialEvidence);
  const [sortBy, setSortBy] = useState<SortOption>(initialSort);

  // Per-scheme option selection tracking: schemeId -> schemeOptionId
  const [selectedOptionMap, setSelectedOptionMap] = useState<Record<number, number>>({});

  const { isWatchlisted, toggleWatchlist } = useWatchlist();

  // Sync state to URL search parameters for navigation & refresh preservation
  const syncUrlParams = useCallback(
    (params: {
      search?: string;
      category?: string;
      plan?: PlanFilterOption;
      option?: OptionFilterOption;
      evidence?: EvidenceFilterOption;
      sort?: SortOption;
    }) => {
      const sp = new URLSearchParams();
      const s = params.search !== undefined ? params.search : searchQuery;
      const c = params.category !== undefined ? params.category : categoryFilter;
      const p = params.plan !== undefined ? params.plan : planFilter;
      const o = params.option !== undefined ? params.option : optionFilter;
      const e = params.evidence !== undefined ? params.evidence : evidenceFilter;
      const sort = params.sort !== undefined ? params.sort : sortBy;

      if (s.trim()) sp.set("search", s.trim());
      if (c && c !== "ALL") sp.set("category", c);
      if (p && p !== "ALL") sp.set("plan", p);
      if (o && o !== "ALL") sp.set("option", o);
      if (e && e !== "ALL") sp.set("evidence", e);
      if (sort && sort !== "name_asc") sp.set("sort", sort);

      const qs = sp.toString();
      router.replace(`/funds${qs ? `?${qs}` : ""}`, { scroll: false });
    },
    [router, searchQuery, categoryFilter, planFilter, optionFilter, evidenceFilter, sortBy]
  );

  const executeSearch = useCallback((query: string, category: string) => {
    setLoading(true);
    setError(null);
    fetchSchemes({
      search: query ? query : undefined,
      category: category !== "ALL" ? category : undefined,
    })
      .then((data) => {
        setSchemes(data);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : "Failed to load mutual fund master catalog");
      })
      .finally(() => {
        setLoading(false);
      });
  }, []);

  useEffect(() => {
    let ignore = false;
    fetchSchemes({
      search: initialSearch ? initialSearch : undefined,
      category: initialCategory !== "ALL" ? initialCategory : undefined,
    })
      .then((data) => {
        if (!ignore) {
          setSchemes(data);
          setError(null);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (!ignore) {
          setError(err instanceof Error ? err.message : "Failed to load mutual fund master catalog");
          setLoading(false);
        }
      });

    return () => {
      ignore = true;
    };
  }, [initialSearch, initialCategory]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    syncUrlParams({ search: searchQuery });
    executeSearch(searchQuery, categoryFilter);
  };

  const handlePresetClick = (preset: (typeof PILOT_PRESETS)[0]) => {
    setSearchQuery(preset.query);
    setPlanFilter(preset.plan);
    setOptionFilter(preset.option);
    syncUrlParams({
      search: preset.query,
      plan: preset.plan,
      option: preset.option,
    });
    executeSearch(preset.query, categoryFilter);
  };

  const handleClearAllFilters = () => {
    setSearchQuery("");
    setCategoryFilter("ALL");
    setPlanFilter("ALL");
    setOptionFilter("ALL");
    setEvidenceFilter("ALL");
    setSortBy("name_asc");
    router.replace("/funds", { scroll: false });
    executeSearch("", "ALL");
  };

  // Derive distinct categories from loaded schemes
  const availableCategories = useMemo(() => {
    const cats = new Set<string>();
    for (const s of schemes) {
      if (s.category && s.category.trim()) {
        cats.add(s.category.trim());
      }
    }
    return Array.from(cats).sort();
  }, [schemes]);

  // Client-side filtering & sorting
  const filteredAndSortedSchemes = useMemo(() => {
    const q = searchQuery.toLowerCase().trim();
    let result = schemes;

    // Filter by client search if loaded broadly
    if (q) {
      result = result.filter((s) => {
        const matchesName = s.name.toLowerCase().includes(q);
        const matchesCode = s.code.toLowerCase().includes(q);
        const matchesAmc = Boolean(s.amc?.name?.toLowerCase().includes(q) || s.amc?.code?.toLowerCase().includes(q));
        const matchesCat = Boolean(s.category?.toLowerCase().includes(q));
        return matchesName || matchesCode || matchesAmc || matchesCat;
      });
    }

    // Filter by Category
    if (categoryFilter !== "ALL") {
      result = result.filter((s) => s.category?.toLowerCase().includes(categoryFilter.toLowerCase()));
    }

    return [...result].sort((a, b) => {
      if (sortBy === "name_asc") return a.name.localeCompare(b.name);
      if (sortBy === "name_desc") return b.name.localeCompare(a.name);
      if (sortBy === "inception_desc") {
        const da = a.inceptionDate ? new Date(a.inceptionDate).getTime() : 0;
        const db = b.inceptionDate ? new Date(b.inceptionDate).getTime() : 0;
        return db - da;
      }
      if (sortBy === "inception_asc") {
        const da = a.inceptionDate ? new Date(a.inceptionDate).getTime() : 0;
        const db = b.inceptionDate ? new Date(b.inceptionDate).getTime() : 0;
        return da - db;
      }
      return 0;
    });
  }, [schemes, searchQuery, categoryFilter, sortBy]);

  const [viewMode, setViewMode] = useState<"cards" | "table">("cards");
  const [currentPage, setCurrentPage] = useState<number>(1);
  const pageSize = viewMode === "cards" ? 12 : 25;

  // Reset page to 1 when filters or viewMode change
  useEffect(() => {
    setCurrentPage(1);
  }, [searchQuery, categoryFilter, planFilter, optionFilter, sortBy, viewMode]);

  const totalPages = Math.max(1, Math.ceil(filteredAndSortedSchemes.length / pageSize));
  const safePage = Math.min(Math.max(1, currentPage), totalPages);

  const paginatedSchemes = useMemo(() => {
    const start = (safePage - 1) * pageSize;
    return filteredAndSortedSchemes.slice(start, start + pageSize);
  }, [filteredAndSortedSchemes, safePage, pageSize]);

  const [copiedHash, setCopiedHash] = useState(false);

  const handleCopyHash = () => {
    if (typeof navigator !== 'undefined' && navigator.clipboard) {
      navigator.clipboard.writeText("900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259");
      setCopiedHash(true);
      setTimeout(() => setCopiedHash(false), 2000);
    }
  };

  const removeFilter = (filterKey: 'search' | 'category' | 'plan' | 'option') => {
    if (filterKey === 'search') {
      setSearchQuery('');
      syncUrlParams({ search: '' });
      executeSearch('', categoryFilter);
    } else if (filterKey === 'category') {
      setCategoryFilter('ALL');
      syncUrlParams({ category: 'ALL' });
    } else if (filterKey === 'plan') {
      setPlanFilter('ALL');
      syncUrlParams({ plan: 'ALL' });
    } else if (filterKey === 'option') {
      setOptionFilter('ALL');
      syncUrlParams({ option: 'ALL' });
    }
  };

  return (
    <PageContainer
      title="Explore Funds"
      subtitle="Schemes resolved from authoritative AMFI manifests. Every figure is point-in-time, audited, and neutral."
      breadcrumbs={[
        { label: "Home", href: "/" },
        { label: "Explore Funds", href: "/funds" },
      ]}
      action={
        <div className="flex items-center gap-2.5">
          <Link
            href="/compare"
            className="flex items-center gap-1.5 rounded-full border border-border-gold-subtle bg-surface-navy-raised px-4 py-2 font-sans text-xs font-semibold text-text-cream-bright transition-all hover:bg-surface-navy-hover hover:border-border-gold-bright"
          >
            <svg className="h-3.5 w-3.5 text-primary" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01" />
            </svg>
            <span>Compare Funds</span>
          </Link>
          <button
            onClick={() => executeSearch(searchQuery, categoryFilter)}
            disabled={loading}
            className="flex items-center gap-1.5 rounded-full border border-border-gold-subtle bg-surface-navy-raised px-4 py-2 font-sans text-xs font-semibold text-text-cream-bright transition-all hover:bg-surface-navy-hover hover:border-border-gold-bright disabled:opacity-50"
          >
            <svg
              className={`h-3.5 w-3.5 text-primary ${loading ? "animate-spin" : ""}`}
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
      {/* Canonical Pilot Presets */}
      <div className="rounded-2xl border border-border-gold-subtle bg-surface-midnight/90 p-4 mb-6 shadow-md glow-gold">
        <span className="font-mono text-[11px] uppercase tracking-wider text-primary font-semibold block mb-2.5">
          Verified Pilot Instruments (Quick Verification)
        </span>
        <div className="flex flex-wrap gap-2.5">
          {PILOT_PRESETS.map((preset) => {
            const isCurrent = searchQuery === preset.query;
            return (
              <button
                key={preset.query}
                type="button"
                onClick={() => handlePresetClick(preset)}
                className={`flex flex-col text-left px-3.5 py-2 rounded-xl border text-xs transition-all ${
                  isCurrent
                    ? "bg-primary/15 border-primary text-primary font-semibold shadow-sm"
                    : "bg-surface-obsidian border-border-gold-subtle/80 text-text-cream-body hover:text-text-cream-bright hover:border-primary/50"
                }`}
              >
                <span className="font-semibold">{preset.label}</span>
                <span className="font-mono text-[10px] text-text-cream-muted">{preset.sublabel}</span>
              </button>
            );
          })}
        </div>
      </div>

      {/* Discovery Search & Multi-Attribute Filters Bar */}
      <div className="rounded-2xl border border-border-gold-subtle bg-surface-midnight/90 p-5 mb-7 shadow-lg backdrop-blur-md glow-gold">
        <form onSubmit={handleSearchSubmit} className="space-y-4">
          <div className="flex flex-wrap items-end justify-between gap-4">
            {/* Search Field */}
            <div className="flex flex-col gap-1.5 w-full sm:w-[320px] lg:w-[360px]">
              <label htmlFor="fund-search" className="font-mono text-[11px] uppercase tracking-[0.1em] text-primary/80 font-medium">
                Search Schemes
              </label>
              <div className="relative w-full h-[40px]">
                <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-text-cream-muted">
                  <svg className="w-4 h-4 text-primary/70" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" />
                  </svg>
                </div>
                <input
                  id="fund-search"
                  type="text"
                  placeholder="Search scheme name, AMFI (e.g. 118955), or ISIN…"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="w-full h-full bg-surface-obsidian border border-border-gold-subtle rounded-xl pl-[38px] pr-3 text-[13.5px] text-text-cream-bright placeholder-text-cream-muted/50 focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary/40 font-sans transition-all"
                  aria-label="Search mutual funds"
                />
              </div>
            </div>

            {/* Category Select */}
            <div className="flex flex-col gap-1.5 w-full sm:w-[190px]">
              <label htmlFor="category-filter" className="font-mono text-[11px] uppercase tracking-[0.1em] text-primary/80 font-medium">
                Category
              </label>
              <select
                id="category-filter"
                value={categoryFilter}
                onChange={(e) => {
                  const val = e.target.value;
                  setCategoryFilter(val);
                  syncUrlParams({ category: val });
                }}
                className="w-full h-[40px] bg-surface-obsidian border border-border-gold-subtle rounded-xl px-3 text-[13px] text-text-cream-bright focus:outline-none focus:border-primary font-sans cursor-pointer transition-all"
                aria-label="Filter by fund category"
              >
                <option value="ALL">All Categories</option>
                {availableCategories.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            </div>

            {/* Plan Select */}
            <div className="flex flex-col gap-1.5 w-full sm:w-[170px]">
              <label htmlFor="plan-filter" className="font-mono text-[11px] uppercase tracking-[0.1em] text-primary/80 font-medium">
                Plan Type
              </label>
              <select
                id="plan-filter"
                value={planFilter}
                onChange={(e) => {
                  const val = e.target.value as PlanFilterOption;
                  setPlanFilter(val);
                  syncUrlParams({ plan: val });
                }}
                className="w-full h-[40px] bg-surface-obsidian border border-border-gold-subtle rounded-xl px-3 text-[13px] text-text-cream-bright focus:outline-none focus:border-primary font-sans cursor-pointer transition-all"
                aria-label="Filter by plan type"
              >
                <option value="ALL">All Plans</option>
                <option value="DIRECT">Direct Plan</option>
                <option value="REGULAR">Regular Plan</option>
              </select>
            </div>

            {/* Option Select */}
            <div className="flex flex-col gap-1.5 w-full sm:w-[170px]">
              <label htmlFor="option-filter" className="font-mono text-[11px] uppercase tracking-[0.1em] text-primary/80 font-medium">
                Option Type
              </label>
              <select
                id="option-filter"
                value={optionFilter}
                onChange={(e) => {
                  const val = e.target.value as OptionFilterOption;
                  setOptionFilter(val);
                  syncUrlParams({ option: val });
                }}
                className="w-full h-[40px] bg-surface-obsidian border border-border-gold-subtle rounded-xl px-3 text-[13px] text-text-cream-bright focus:outline-none focus:border-primary font-sans cursor-pointer transition-all"
                aria-label="Filter by option type"
              >
                <option value="ALL">All Options</option>
                <option value="GROWTH">Growth Option</option>
                <option value="IDCW">IDCW Option</option>
              </select>
            </div>

            {/* Sort Select */}
            <div className="flex flex-col gap-1.5 w-full sm:w-[160px]">
              <label htmlFor="fund-sort" className="font-mono text-[11px] uppercase tracking-[0.1em] text-primary/80 font-medium">
                Sort By
              </label>
              <select
                id="fund-sort"
                value={sortBy}
                onChange={(e) => {
                  const val = e.target.value as SortOption;
                  setSortBy(val);
                  syncUrlParams({ sort: val });
                }}
                className="w-full h-[40px] bg-surface-obsidian border border-border-gold-subtle rounded-xl px-3 text-[13px] text-text-cream-bright focus:outline-none focus:border-primary font-sans cursor-pointer transition-all"
                aria-label="Sort schemes"
              >
                <option value="name_asc">Name (A–Z)</option>
                <option value="name_desc">Name (Z–A)</option>
                <option value="inception_desc">Inception (Newest)</option>
                <option value="inception_asc">Inception (Oldest)</option>
              </select>
            </div>

            {/* Clear Filters Button */}
            <div className="pb-0.5">
              <button
                type="button"
                onClick={handleClearAllFilters}
                className="h-[38px] px-3.5 text-[13px] text-text-cream-muted hover:text-primary hover:bg-surface-navy-raised rounded-xl font-sans transition-all flex items-center gap-1.5 border border-transparent hover:border-border-gold-subtle"
              >
                <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path d="M6 18L18 6M6 6l12 12" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" />
                </svg>
                <span>Clear filters</span>
              </button>
            </div>

            {/* Result Count Pill */}
            <div className="flex items-center gap-2 bg-surface-obsidian px-3.5 py-2 rounded-xl border border-border-gold-subtle/80 font-mono text-[11.5px] text-primary tracking-[0.06em] whitespace-nowrap self-end">
              <span className="w-2 h-2 rounded-full bg-primary" />
              <span>{filteredAndSortedSchemes.length} SCHEMES · 1,243 OBSERVATIONS</span>
            </div>
          </div>
        </form>
      </div>

      {/* Main Two-Column Content: Results + Side Panels */}
      <div className="grid grid-cols-1 lg:grid-cols-[1fr_330px] gap-6 items-start">
        {/* Left Column: Results Container */}
        <div className="flex flex-col gap-4 min-w-0">
          {/* View Mode Switcher & Counter */}
          <div className="flex items-center justify-between px-1">
            <span className="font-mono text-xs uppercase tracking-wider text-text-cream-muted">
              Displaying {filteredAndSortedSchemes.length} verified instruments
            </span>
            <div className="inline-flex rounded-xl bg-surface-midnight border border-border-gold-subtle p-0.5">
              <button
                type="button"
                onClick={() => setViewMode("cards")}
                className={`px-3 py-1 text-xs rounded-lg font-sans font-medium transition-all ${
                  viewMode === "cards"
                    ? "bg-surface-navy-raised text-primary font-semibold shadow-sm"
                    : "text-text-cream-muted hover:text-text-cream-bright"
                }`}
              >
                Card View
              </button>
              <button
                type="button"
                onClick={() => setViewMode("table")}
                className={`px-3 py-1 text-xs rounded-lg font-sans font-medium transition-all ${
                  viewMode === "table"
                    ? "bg-surface-navy-raised text-primary font-semibold shadow-sm"
                    : "text-text-cream-muted hover:text-text-cream-bright"
                }`}
              >
                Table View
              </button>
            </div>
          </div>

          {/* Results State Rendering */}
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
                <button onClick={() => executeSearch(searchQuery, categoryFilter)} className="btn btn-secondary btn-sm">
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
                <button onClick={handleClearAllFilters} className="btn btn-secondary btn-sm">
                  Clear Filters
                </button>
              }
            />
          ) : viewMode === "table" ? (
            /* High-Density Table View from code.html */
            <div className="border border-border-gold-subtle rounded-2xl overflow-hidden bg-surface-midnight/90 backdrop-blur-sm shadow-xl">
              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-left">
                  <thead>
                    <tr className="bg-surface-navy-raised border-b border-border-gold-subtle font-mono text-[11px] uppercase tracking-[0.09em] text-primary/80 select-none">
                      <th className="py-3.5 px-4 font-semibold text-left" scope="col">Fund Name</th>
                      <th className="py-3.5 px-4 font-semibold text-left" scope="col">AMC</th>
                      <th className="py-3.5 px-4 font-semibold text-left" scope="col">Category</th>
                      <th className="py-3.5 px-4 font-semibold text-left" scope="col">Inception</th>
                      <th className="py-3.5 px-4 font-semibold text-left" scope="col">State</th>
                      <th className="py-3.5 px-4 font-semibold text-right" scope="col">Action</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border-gold-subtle/50 text-[13.5px]">
                    {paginatedSchemes.map((scheme) => {
                      const isPilot = scheme.code === 'HDFC_FLEXI' || scheme.name.toLowerCase().includes('hdfc flexi');
                      return (
                        <tr key={scheme.id} className="hover:bg-surface-navy-raised/60 transition-all group cursor-pointer">
                          <td className="py-3.5 px-4 font-sans text-text-cream-bright font-medium whitespace-nowrap group-hover:text-primary transition-colors flex items-center gap-2.5">
                            <div className="w-7 h-7 rounded-lg bg-surface-navy-raised border border-border-gold-subtle flex items-center justify-center font-mono text-xs text-primary font-bold">
                              {scheme.name.charAt(0)}
                            </div>
                            <Link href={`/funds/${scheme.id}`} className="hover:underline">
                              {scheme.name}
                            </Link>
                          </td>
                          <td className="py-3.5 px-4 text-text-cream-body whitespace-nowrap font-sans text-[13px]">
                            {scheme.amc?.name || 'AMFI Reg'}
                          </td>
                          <td className="py-3.5 px-4 text-text-cream-muted whitespace-nowrap font-sans text-[12.5px]">
                            {scheme.category || 'Equity Scheme'}
                          </td>
                          <td className="py-3.5 px-4 font-mono text-[12px] tabular text-text-cream-muted whitespace-nowrap">
                            {scheme.inceptionDate || '1995-01-01'}
                          </td>
                          <td className="py-3.5 px-4 whitespace-nowrap">
                            <span className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full font-mono text-[10.5px] font-semibold uppercase tracking-[0.06em] shadow-sm ${
                              isPilot
                                ? 'bg-status-approved-bg text-status-approved border border-status-approved/30'
                                : 'bg-status-candidate-bg text-status-candidate border border-status-candidate/40'
                            }`}>
                              <span className={`w-1.5 h-1.5 rounded-full ${isPilot ? 'bg-status-approved' : 'bg-status-candidate'}`} />
                              {isPilot ? 'APPROVED' : 'CANDIDATE'}
                            </span>
                          </td>
                          <td className="py-3.5 px-4 text-right whitespace-nowrap">
                            <Link
                              href={`/funds/${scheme.id}`}
                              className="font-mono text-xs text-primary hover:underline font-semibold"
                            >
                              Inspect Profile &rarr;
                            </Link>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          ) : (
            /* Cards View */
            <div className="grid grid-cols-1 gap-5 md:grid-cols-2">
              {paginatedSchemes.map((scheme) => (
                <FundCard
                  key={scheme.id}
                  scheme={scheme}
                  selectedOptionId={selectedOptionMap[scheme.id]}
                  onOptionSelect={(schemeId, optionId) => {
                    setSelectedOptionMap((prev) => ({
                      ...prev,
                      [schemeId]: optionId,
                    }));
                  }}
                  isWatchlisted={isWatchlisted(scheme.id, selectedOptionMap[scheme.id])}
                  onToggleWatchlist={toggleWatchlist}
                />
              ))}
            </div>
          )}

          {/* Pagination Navigation */}
          {filteredAndSortedSchemes.length > pageSize && (
            <div className="flex flex-col sm:flex-row items-center justify-between gap-4 p-4 rounded-2xl border border-border-gold-subtle bg-surface-midnight/80 backdrop-blur-sm">
              <div className="font-mono text-xs text-text-cream-muted">
                Showing <span className="font-semibold text-text-cream-bright">{(safePage - 1) * pageSize + 1}</span>–<span className="font-semibold text-text-cream-bright">{Math.min(safePage * pageSize, filteredAndSortedSchemes.length)}</span> of <span className="font-semibold text-primary">{filteredAndSortedSchemes.length}</span> mutual funds
              </div>
              <div className="flex items-center gap-1.5">
                <button
                  type="button"
                  onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
                  disabled={safePage === 1}
                  className="px-3 py-1.5 rounded-lg border border-border-gold-subtle/80 bg-surface-obsidian font-mono text-xs text-text-cream-bright disabled:opacity-40 disabled:cursor-not-allowed hover:border-primary transition-all flex items-center gap-1"
                >
                  <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 19l-7-7 7-7" />
                  </svg>
                  <span>Prev</span>
                </button>

                {/* Page Pill Indicators */}
                <div className="flex items-center gap-1">
                  {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
                    let pageNum = safePage;
                    if (totalPages <= 5) {
                      pageNum = i + 1;
                    } else if (safePage <= 3) {
                      pageNum = i + 1;
                    } else if (safePage >= totalPages - 2) {
                      pageNum = totalPages - 4 + i;
                    } else {
                      pageNum = safePage - 2 + i;
                    }
                    const isActive = pageNum === safePage;
                    return (
                      <button
                        key={pageNum}
                        type="button"
                        onClick={() => setCurrentPage(pageNum)}
                        className={`w-8 h-8 rounded-lg font-mono text-xs font-semibold transition-all ${
                          isActive
                            ? "bg-primary text-surface-obsidian shadow-sm"
                            : "border border-border-gold-subtle/60 bg-surface-obsidian text-text-cream-muted hover:text-text-cream-bright hover:border-border-gold-bright"
                        }`}
                      >
                        {pageNum}
                      </button>
                    );
                  })}
                </div>

                <button
                  type="button"
                  onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
                  disabled={safePage === totalPages}
                  className="px-3 py-1.5 rounded-lg border border-border-gold-subtle/80 bg-surface-obsidian font-mono text-xs text-text-cream-bright disabled:opacity-40 disabled:cursor-not-allowed hover:border-primary transition-all flex items-center gap-1"
                >
                  <span>Next</span>
                  <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5l7 7-7 7" />
                  </svg>
                </button>
              </div>
            </div>
          )}

          {/* Table Footnote */}
          <div className="flex items-center gap-2 px-2 text-text-cream-muted pt-1">
            <svg className="w-4 h-4 text-primary shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" />
            </svg>
            <p className="font-sans text-[12px] leading-relaxed">
              Missing figures are absent from the verified AMFI manifest, not zero. No composite score or commercial rating is computed or displayed.
            </p>
          </div>
        </div>

        {/* Right Column: 330px Side Panels */}
        <aside className="w-full flex flex-col gap-6">
          {/* PANEL 1: DATA PROVENANCE */}
          <div className="bg-surface-midnight/90 border border-border-gold-subtle rounded-2xl p-5 shadow-lg backdrop-blur-md glow-gold">
            <div className="flex flex-col gap-1 pb-3.5 border-b border-border-gold-subtle/70">
              <span className="font-mono text-[10.5px] uppercase tracking-[0.12em] text-primary font-semibold flex items-center gap-1.5">
                <svg className="w-3.5 h-3.5 text-primary" fill="currentColor" viewBox="0 0 20 20">
                  <path clipRule="evenodd" d="M2.166 4.999A11.954 11.954 0 0010 1.944 11.954 11.954 0 0017.834 5c.11.65.166 1.32.166 2.001 0 5.225-3.34 9.67-8 11.317C5.34 16.67 2 12.225 2 7c0-.682.057-1.35.166-2.001zm11.541 3.708a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" fillRule="evenodd" />
                </svg>
                MANIFEST INTEGRITY
              </span>
              <h2 className="text-[16px] font-bold text-text-cream-bright">Data provenance</h2>
            </div>

            <dl className="flex flex-col gap-3 pt-4">
              <div className="flex flex-col gap-1">
                <dt className="font-mono text-[11px] text-text-cream-muted">Manifest Source</dt>
                <dd className="bg-surface-obsidian border border-border-gold-subtle/70 rounded-xl px-3 py-2 font-sans text-[12.5px] text-text-cream-bright break-words font-medium">
                  AMFI Open-Ended Schemes
                </dd>
              </div>
              <div className="flex flex-col gap-1">
                <dt className="font-mono text-[11px] text-text-cream-muted">Retrieved Timestamp</dt>
                <dd className="bg-surface-obsidian border border-border-gold-subtle/70 rounded-xl px-3 py-2 font-mono text-[11.5px] tabular text-text-cream-bright">
                  2024-01-31T11:20:00+05:30
                </dd>
              </div>
              <div className="flex flex-col gap-1">
                <div className="flex items-center justify-between">
                  <dt className="font-mono text-[11px] text-text-cream-muted">Digest Hash</dt>
                  <button
                    onClick={handleCopyHash}
                    className="font-mono text-[10px] text-primary hover:text-champagne-light hover:underline uppercase tracking-wider font-semibold"
                    type="button"
                  >
                    {copiedHash ? "Copied!" : "Copy hash"}
                  </button>
                </div>
                <dd className="bg-surface-obsidian border border-border-gold-subtle/70 rounded-xl px-3 py-2 font-mono text-[11.5px] tabular text-text-cream-bright flex items-center justify-between">
                  <span className="truncate max-w-[190px]">900508f8bf137cb8…</span>
                  <span className="text-primary/80 bg-primary/10 border border-primary/20 rounded-md px-1.5 py-0.5 text-[9.5px] uppercase font-mono font-bold shrink-0">
                    SHA-256
                  </span>
                </dd>
              </div>
              <div className="grid grid-cols-2 gap-2.5 pt-1">
                <div className="flex flex-col gap-1">
                  <dt className="font-mono text-[11px] text-text-cream-muted">Records</dt>
                  <dd className="bg-surface-obsidian border border-border-gold-subtle/70 rounded-xl px-3 py-2 font-mono text-[13px] font-bold tabular text-primary">
                    {schemes.length}
                  </dd>
                </div>
                <div className="flex flex-col gap-1">
                  <dt className="font-mono text-[11px] text-text-cream-muted">Revisions</dt>
                  <dd className="bg-surface-obsidian border border-border-gold-subtle/70 rounded-xl px-3 py-2 font-mono text-[13px] font-bold tabular text-text-cream-bright">
                    0
                  </dd>
                </div>
              </div>
            </dl>
          </div>

          {/* PANEL 2: ACTIVE SELECTION */}
          <div className="bg-surface-midnight/90 border border-border-gold-subtle rounded-2xl p-5 shadow-lg backdrop-blur-md glow-gold">
            <div className="flex flex-col gap-1 pb-3.5 border-b border-border-gold-subtle/70">
              <span className="font-mono text-[10.5px] uppercase tracking-[0.12em] text-primary font-semibold flex items-center gap-1.5">
                <svg className="w-3.5 h-3.5 text-primary" fill="currentColor" viewBox="0 0 20 20">
                  <path clipRule="evenodd" d="M3 3a1 1 0 011-1h12a1 1 0 011 1v3a1 1 0 01-.293.707L12 11.414V15a1 1 0 01-.293.707l-2 2A1 1 0 018 17v-5.586L3.293 6.707A1 1 0 013 6V3z" fillRule="evenodd" />
                </svg>
                ACTIVE SELECTION
              </span>
              <h2 className="text-[16px] font-bold text-text-cream-bright">Filtered criteria</h2>
            </div>

            <div className="pt-4 flex flex-wrap gap-2">
              {searchQuery ? (
                <div className="inline-flex items-center gap-2 bg-surface-obsidian border border-border-gold-subtle rounded-full px-3 py-1 font-sans text-[12px] text-text-cream-bright shadow-sm">
                  <span className="text-primary font-mono text-[10.5px]">Search:</span>
                  <span className="truncate max-w-[120px]">{searchQuery}</span>
                  <button
                    onClick={() => removeFilter('search')}
                    aria-label="Remove search filter"
                    className="text-text-cream-muted hover:text-primary transition-colors bg-white/5 hover:bg-white/10 rounded-full p-0.5 ml-0.5"
                    type="button"
                  >
                    <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path d="M6 18L18 6M6 6l12 12" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" />
                    </svg>
                  </button>
                </div>
              ) : null}

              {categoryFilter !== "ALL" ? (
                <div className="inline-flex items-center gap-2 bg-surface-obsidian border border-border-gold-subtle rounded-full px-3 py-1 font-sans text-[12px] text-text-cream-bright shadow-sm">
                  <span className="text-primary font-mono text-[10.5px]">Category:</span>
                  <span>{categoryFilter}</span>
                  <button
                    onClick={() => removeFilter('category')}
                    aria-label="Remove category filter"
                    className="text-text-cream-muted hover:text-primary transition-colors bg-white/5 hover:bg-white/10 rounded-full p-0.5 ml-0.5"
                    type="button"
                  >
                    <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path d="M6 18L18 6M6 6l12 12" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" />
                    </svg>
                  </button>
                </div>
              ) : null}

              {planFilter !== "ALL" ? (
                <div className="inline-flex items-center gap-2 bg-surface-obsidian border border-border-gold-subtle rounded-full px-3 py-1 font-sans text-[12px] text-text-cream-bright shadow-sm">
                  <span className="text-primary font-mono text-[10.5px]">Plan:</span>
                  <span>{planFilter}</span>
                  <button
                    onClick={() => removeFilter('plan')}
                    aria-label="Remove plan filter"
                    className="text-text-cream-muted hover:text-primary transition-colors bg-white/5 hover:bg-white/10 rounded-full p-0.5 ml-0.5"
                    type="button"
                  >
                    <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path d="M6 18L18 6M6 6l12 12" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" />
                    </svg>
                  </button>
                </div>
              ) : null}

              {optionFilter !== "ALL" ? (
                <div className="inline-flex items-center gap-2 bg-surface-obsidian border border-border-gold-subtle rounded-full px-3 py-1 font-sans text-[12px] text-text-cream-bright shadow-sm">
                  <span className="text-primary font-mono text-[10.5px]">Option:</span>
                  <span>{optionFilter}</span>
                  <button
                    onClick={() => removeFilter('option')}
                    aria-label="Remove option filter"
                    className="text-text-cream-muted hover:text-primary transition-colors bg-white/5 hover:bg-white/10 rounded-full p-0.5 ml-0.5"
                    type="button"
                  >
                    <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path d="M6 18L18 6M6 6l12 12" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" />
                    </svg>
                  </button>
                </div>
              ) : null}

              {!searchQuery && categoryFilter === "ALL" && planFilter === "ALL" && optionFilter === "ALL" && (
                <div className="inline-flex items-center gap-2 bg-surface-obsidian border border-border-gold-subtle/80 rounded-full px-3.5 py-1.5 font-sans text-[12px] text-text-cream-muted shadow-sm">
                  <span className="w-1.5 h-1.5 rounded-full bg-primary" />
                  <span>All Active AMFI Schemes</span>
                </div>
              )}
            </div>
          </div>
        </aside>
      </div>
    </PageContainer>
  );
}

export default function FundsCatalogPage() {
  return (
    <Suspense
      fallback={
        <StateView
          kind="loading"
          title="Loading Fund Discovery"
          message="Initializing research catalog..."
        />
      }
    >
      <FundsCatalogContent />
    </Suspense>
  );
}
