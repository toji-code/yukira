'use client';

import React, { useState, useEffect, useCallback, useMemo } from 'react';
import Link from 'next/link';
import {
  ResearchPortfolioFundItemDto,
  PortfolioRiskDriversResponseDto,
  fetchPortfolioRiskDrivers,
  ConsolidatedSecurityExposureDto,
  PairwiseOverlapResultDto,
  ConsolidatedSectorExposureDto,
  ConsolidatedMarketCapExposureDto,
  RiskDriverFindingDto,
} from '@/lib/api/portfolio';
import { SchemeOption } from '@/types/domain';

interface RiskDriversSectionProps {
  selectedFunds: ResearchPortfolioFundItemDto[];
  availableSchemes?: SchemeOption[];
  asOfDate: string;
}

export function RiskDriversSection({
  selectedFunds,
  availableSchemes = [],
  asOfDate,
}: RiskDriversSectionProps) {
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<PortfolioRiskDriversResponseDto | null>(null);

  // Filter state for Section A & C
  const [securitySearch, setSecuritySearch] = useState<string>('');
  const [selectedCategoryFilter, setSelectedCategoryFilter] = useState<string>('ALL');
  const [expandedSecurityKey, setExpandedSecurityKey] = useState<string | null>(null);
  const [findingsSeverityFilter, setFindingsSeverityFilter] = useState<string>('ALL');

  // Trigger evaluation
  const runEvaluation = useCallback(async (fundsToRun = selectedFunds, date = asOfDate) => {
    if (fundsToRun.length === 0) {
      setData(null);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const res = await fetchPortfolioRiskDrivers({
        funds: fundsToRun,
        asOfDate: date,
      });
      setData(res);
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to evaluate portfolio risk drivers.');
    } finally {
      setLoading(false);
    }
  }, [selectedFunds, asOfDate]);

  useEffect(() => {
    if (selectedFunds.length === 0) {
      setData(null);
      return;
    }
    let ignore = false;
    const timer = setTimeout(() => {
      setLoading(true);
      setError(null);
    }, 0);

    fetchPortfolioRiskDrivers({
      funds: selectedFunds,
      asOfDate,
    })
      .then((res) => {
        if (!ignore) {
          setData(res);
        }
      })
      .catch((err: unknown) => {
        if (!ignore) {
          setError((err as Error).message || 'Failed to evaluate portfolio risk drivers.');
        }
      })
      .finally(() => {
        if (!ignore) {
          setLoading(false);
        }
      });

    return () => {
      ignore = true;
      clearTimeout(timer);
    };
  }, [selectedFunds, asOfDate]);

  // Helper to resolve fund name
  const getFundName = (optId: number, fallback: string) => {
    const opt = availableSchemes.find((s) => s.id === optId);
    return opt?.plan?.scheme?.name || fallback;
  };

  // Filtered underlying securities for Section A
  const filteredSecurities = useMemo(() => {
    if (!data?.underlyingSecurities) return [];
    return data.underlyingSecurities.filter((sec: ConsolidatedSecurityExposureDto) => {
      // Category / Sector filter
      if (selectedCategoryFilter !== 'ALL') {
        const matchesMcap = sec.marketCapCategory === selectedCategoryFilter;
        const matchesSector = sec.sector === selectedCategoryFilter;
        if (!matchesMcap && !matchesSector) return false;
      }
      // Text search
      if (securitySearch.trim()) {
        const term = securitySearch.toLowerCase();
        const matchesName = sec.canonicalName?.toLowerCase().includes(term);
        const matchesIsin = sec.isin?.toLowerCase().includes(term);
        const matchesSector = sec.sector?.toLowerCase().includes(term);
        return matchesName || matchesIsin || matchesSector;
      }
      return true;
    });
  }, [data?.underlyingSecurities, selectedCategoryFilter, securitySearch]);

  // Filtered findings for Section D
  const filteredFindings = useMemo(() => {
    if (!data?.findings) return [];
    if (findingsSeverityFilter === 'ALL') return data.findings;
    return data.findings.filter((f) => f.severity === findingsSeverityFilter);
  }, [data?.findings, findingsSeverityFilter]);

  // 1. EMPTY STATE
  if (selectedFunds.length === 0) {
    return (
      <div className="panel p-8 text-center space-y-3">
        <span className="text-3xl">🎯</span>
        <h3 className="text-[14px] font-semibold text-text-primary">No Constituent Funds Selected</h3>
        <p className="font-mono text-[11px] text-text-tertiary max-w-md mx-auto">
          Please select mutual funds in your portfolio builder above to investigate underlying security concentrations, cross-fund overlap, and exposure risk drivers.
        </p>
      </div>
    );
  }

  // 2. LOADING SKELETON
  if (loading && !data) {
    return (
      <div className="space-y-4">
        <div className="panel p-6 space-y-4 animate-pulse">
          <div className="h-4 bg-surface-raised rounded w-1/3" />
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            {[1, 2, 3, 4].map((i) => (
              <div key={i} className="h-16 bg-surface-raised rounded" />
            ))}
          </div>
          <div className="h-48 bg-surface-raised rounded" />
        </div>
      </div>
    );
  }

  // 3. ERROR STATE
  if (error && !data) {
    return (
      <div className="panel p-6 border-l-4 border-rose-500 space-y-3">
        <div className="flex items-center justify-between">
          <h3 className="text-[13px] font-semibold text-rose-400">Risk Drivers Evaluation Failed</h3>
          <button
            onClick={() => runEvaluation()}
            className="text-[11px] font-mono px-3 py-1 bg-surface-raised hover:bg-surface-raised/80 rounded border border-border-subtle"
          >
            Retry Analysis
          </button>
        </div>
        <p className="font-mono text-[11px] text-text-secondary">{error}</p>
      </div>
    );
  }

  // 4. UNAVAILABLE STATE
  if (data?.evaluationStatus === 'UNAVAILABLE') {
    return (
      <div className="panel p-8 text-center space-y-3">
        <span className="text-3xl">⚠️</span>
        <h3 className="text-[14px] font-semibold text-text-primary">Concentration Analysis Unavailable</h3>
        <p className="font-mono text-[11px] text-amber-400 max-w-lg mx-auto">
          {data.unavailabilityReason || 'Holdings data could not be retrieved for the selected funds as of this cutoff date.'}
        </p>
        <div className="pt-2">
          <button
            onClick={() => runEvaluation()}
            className="text-[11px] font-mono px-3 py-1.5 bg-surface-raised hover:bg-surface-raised/80 rounded border border-border-subtle"
          >
            Re-evaluate
          </button>
        </div>
      </div>
    );
  }

  const summary = data?.concentrationSummary;

  return (
    <div className="space-y-6">
      {/* HEADER BAR */}
      <div className="panel p-4 flex flex-col md:flex-row items-start md:items-center justify-between gap-3">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <h2 className="text-[15px] font-semibold text-text-primary">
              Risk Drivers & Concentration Investigation
            </h2>
            <span className="px-2 py-0.5 text-[9px] font-mono font-bold uppercase rounded bg-indigo-500/10 text-indigo-400 border border-indigo-500/30">
              Phase 2P
            </span>
            {data?.isFullyAllocated ? (
              <span className="px-2 py-0.5 text-[9px] font-mono rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/30">
                100% Allocated
              </span>
            ) : (
              <span className="px-2 py-0.5 text-[9px] font-mono rounded bg-amber-500/10 text-amber-400 border border-amber-500/30">
                Partial: {data?.totalAllocationPct?.toFixed(1)}%
              </span>
            )}
          </div>
          <p className="text-[11px] font-mono text-text-tertiary">
            As of: {data?.asOfDate} | Knowledge Cutoff: {data?.knowledgeCutoff?.substring(0, 10)} | Look-Through Holdings Source
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => runEvaluation()}
            disabled={loading}
            className="text-[10px] font-mono px-3 py-1.5 bg-surface-raised hover:bg-surface-raised/80 border border-border-subtle rounded text-text-primary disabled:opacity-50"
          >
            {loading ? 'Evaluating...' : '↻ Refresh Analysis'}
          </button>
        </div>
      </div>

      {/* ZERO-RENORMALIZATION DISCLOSURE BANNER */}
      <div className="panel p-3.5 bg-brand-navy/30 border-l-4 border-indigo-400 flex flex-col md:flex-row items-start md:items-center justify-between gap-2 text-[11px] font-mono">
        <div className="space-y-0.5">
          <div className="flex items-center gap-2">
            <span className="font-bold text-text-primary">Institutional Look-Through Principle:</span>
            <span className="text-indigo-300">Zero Renormalization Enforced</span>
          </div>
          <p className="text-text-tertiary text-[10px]">
            Incomplete holdings disclosures are explicitly recorded as unclassified/unmodeled weight ({summary?.unclassifiedWeightPct?.toFixed(2)}%) rather than scaled to 100%. Holdings weights describe static equity allocations, not forward volatility contributions.
          </p>
        </div>
        <div className="flex items-center gap-2 text-[10px] whitespace-nowrap">
          <span className="px-2 py-1 bg-surface-raised/70 rounded border border-border-subtle text-emerald-300">
            Equity: {summary?.disclosedEquityCoveragePct?.toFixed(1)}%
          </span>
          <span className="px-2 py-1 bg-surface-raised/70 rounded border border-border-subtle text-amber-300">
            Cash/Debt: {summary?.cashAndEquivalentsWeightPct?.toFixed(1)}%
          </span>
        </div>
      </div>

      {/* SECTION A: UNDERLYING SECURITY CONCENTRATION */}
      <div className="panel p-5 space-y-5">
        <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-2 pb-3 border-b border-border-subtle">
          <div>
            <h3 className="text-[13px] font-semibold text-text-primary flex items-center gap-2">
              <span>Section A — Underlying Security Concentration</span>
              <span className="text-[10px] font-mono text-text-tertiary">
                ({data?.underlyingSecurities?.length || 0} distinct securities)
              </span>
            </h3>
            <p className="text-[10px] font-mono text-text-tertiary">
              Ranked underlying equity positions aggregated across constituent fund holdings according to portfolio allocation weights.
            </p>
          </div>
          {/* Active category filter tag */}
          {selectedCategoryFilter !== 'ALL' && (
            <div className="flex items-center gap-2 text-[10px] font-mono bg-indigo-500/10 text-indigo-300 px-2.5 py-1 rounded border border-indigo-500/30">
              <span>Filter: <strong>{selectedCategoryFilter}</strong></span>
              <button
                onClick={() => setSelectedCategoryFilter('ALL')}
                className="text-text-tertiary hover:text-text-primary font-bold ml-1"
                title="Clear category filter"
              >
                ✕
              </button>
            </div>
          )}
        </div>

        {/* COMPACT CONCENTRATION KPI ROW */}
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2.5">
          <div className="p-3 bg-surface-raised/50 rounded border border-border-subtle space-y-1">
            <span className="text-[9px] font-mono text-text-tertiary uppercase">Top 5 Holdings</span>
            <p className="text-[15px] font-mono font-bold text-text-primary">
              {summary?.top5WeightPct?.toFixed(2)}%
            </p>
            <span className="text-[9px] font-mono text-text-tertiary block">
              {summary && summary.top5WeightPct >= 30 ? 'Concentrated' : 'Diversified'}
            </span>
          </div>

          <div className="p-3 bg-surface-raised/50 rounded border border-border-subtle space-y-1">
            <span className="text-[9px] font-mono text-text-tertiary uppercase">Top 10 Holdings</span>
            <p className="text-[15px] font-mono font-bold text-text-primary">
              {summary?.top10WeightPct?.toFixed(2)}%
            </p>
            <span className="text-[9px] font-mono text-text-tertiary block">
              {summary && summary.top10WeightPct >= 45 ? 'Heavy top tier' : 'Moderate weight'}
            </span>
          </div>

          <div className="p-3 bg-surface-raised/50 rounded border border-border-subtle space-y-1">
            <span className="text-[9px] font-mono text-text-tertiary uppercase">Largest Holding</span>
            <p className="text-[15px] font-mono font-bold text-indigo-400">
              {summary?.largestSecurityWeightPct?.toFixed(2)}%
            </p>
            <span className="text-[9px] font-mono text-text-tertiary truncate block" title={summary?.largestSecurityName}>
              {summary?.largestSecurityName || 'None'}
            </span>
          </div>

          <div className="p-3 bg-surface-raised/50 rounded border border-border-subtle space-y-1">
            <span className="text-[9px] font-mono text-text-tertiary uppercase">Distinct Securities</span>
            <p className="text-[15px] font-mono font-bold text-text-primary">
              {summary?.distinctSecuritiesCount}
            </p>
            <span className="text-[9px] font-mono text-text-tertiary block">
              {summary?.sharedSecuritiesCount} held by multiple funds
            </span>
          </div>

          <div className="p-3 bg-surface-raised/50 rounded border border-border-subtle space-y-1">
            <span className="text-[9px] font-mono text-text-tertiary uppercase">Disclosed Equity</span>
            <p className="text-[15px] font-mono font-bold text-emerald-400">
              {summary?.disclosedEquityCoveragePct?.toFixed(1)}%
            </p>
            <span className="text-[9px] font-mono text-text-tertiary block">
              {summary?.unclassifiedWeightPct?.toFixed(1)}% unmodeled
            </span>
          </div>

          <div className="p-3 bg-surface-raised/50 rounded border border-border-subtle space-y-1">
            <span className="text-[9px] font-mono text-text-tertiary uppercase">Concentration HHI</span>
            <p className="text-[15px] font-mono font-bold text-text-primary">
              {summary?.herfindahlHirschmanIndex?.toFixed(0)}
            </p>
            <span className="text-[9px] font-mono text-text-tertiary block capitalize">
              {summary?.hhiInterpretation?.toLowerCase()?.replace('_', ' ')}
            </span>
          </div>
        </div>

        {/* SEARCH & FILTER BAR */}
        <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3 pt-1">
          <div className="relative flex-1 max-w-md">
            <input
              type="text"
              placeholder="Search by security name, ISIN, or sector..."
              value={securitySearch}
              onChange={(e) => setSecuritySearch(e.target.value)}
              className="w-full px-3 py-1.5 text-[11px] font-mono bg-surface-raised/60 border border-border-subtle rounded text-text-primary placeholder:text-text-tertiary focus:outline-none focus:border-indigo-500"
            />
            {securitySearch && (
              <button
                onClick={() => setSecuritySearch('')}
                className="absolute right-2.5 top-2 text-[10px] text-text-tertiary hover:text-text-primary"
              >
                ✕
              </button>
            )}
          </div>
          <div className="flex items-center gap-1.5 overflow-x-auto text-[10px] font-mono">
            <span className="text-text-tertiary mr-1">M-Cap Filter:</span>
            {['ALL', 'LARGE_CAP', 'MID_CAP', 'SMALL_CAP'].map((cat) => (
              <button
                key={cat}
                onClick={() => setSelectedCategoryFilter(cat)}
                className={`px-2 py-1 rounded transition-colors ${
                  selectedCategoryFilter === cat
                    ? 'bg-indigo-600 text-white font-bold'
                    : 'bg-surface-raised text-text-tertiary hover:text-text-primary border border-border-subtle'
                }`}
              >
                {cat.replace('_', ' ')}
              </button>
            ))}
          </div>
        </div>

        {/* RANKED SECURITIES TABLE */}
        <div className="overflow-x-auto border border-border-subtle rounded">
          <table className="w-full text-left text-[11px] font-mono">
            <thead className="bg-surface-raised/70 border-b border-border-subtle text-text-tertiary uppercase text-[9px]">
              <tr>
                <th className="py-2.5 px-3 w-12 text-center">#</th>
                <th className="py-2.5 px-3">Security Name & ISIN</th>
                <th className="py-2.5 px-3">Sector</th>
                <th className="py-2.5 px-3">Market Cap</th>
                <th className="py-2.5 px-3 text-right">Consolidated Weight</th>
                <th className="py-2.5 px-3 text-center">Funds</th>
                <th className="py-2.5 px-3 text-right">Contributions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border-subtle">
              {filteredSecurities.length === 0 ? (
                <tr>
                  <td colSpan={7} className="py-8 text-center text-text-tertiary">
                    No underlying securities match the current search or category filter.
                  </td>
                </tr>
              ) : (
                filteredSecurities.slice(0, 50).map((sec: ConsolidatedSecurityExposureDto, idx: number) => {
                  const key = sec.isin || sec.canonicalName;
                  const isExpanded = expandedSecurityKey === key;
                  return (
                    <React.Fragment key={key}>
                      <tr className="hover:bg-surface-raised/40 transition-colors">
                        <td className="py-2 px-3 text-center text-text-tertiary text-[10px]">
                          {idx + 1}
                        </td>
                        <td className="py-2 px-3">
                          <div className="font-semibold text-text-primary text-[11px]">
                            {sec.canonicalName}
                          </div>
                          <div className="text-[9px] text-text-tertiary font-mono">
                            {sec.isin || 'ISIN Not Disclosed'}
                          </div>
                        </td>
                        <td className="py-2 px-3">
                          <button
                            onClick={() => setSelectedCategoryFilter(sec.sector)}
                            className="text-text-secondary hover:text-indigo-400 hover:underline text-[10px] text-left"
                            title="Click to filter by this sector"
                          >
                            {sec.sector}
                          </button>
                        </td>
                        <td className="py-2 px-3">
                          <span
                            className={`px-1.5 py-0.5 text-[9px] rounded font-mono ${
                              sec.marketCapCategory === 'LARGE_CAP'
                                ? 'bg-blue-500/10 text-blue-400 border border-blue-500/20'
                                : sec.marketCapCategory === 'MID_CAP'
                                ? 'bg-amber-500/10 text-amber-400 border border-amber-500/20'
                                : sec.marketCapCategory === 'SMALL_CAP'
                                ? 'bg-purple-500/10 text-purple-400 border border-purple-500/20'
                                : 'bg-surface-raised text-text-tertiary'
                            }`}
                          >
                            {sec.marketCapCategory ? sec.marketCapCategory.replace('_', ' ') : 'Unclassified'}
                          </span>
                        </td>
                        <td className="py-2 px-3 text-right">
                          <span className="font-bold text-[12px] text-indigo-300">
                            {sec.consolidatedWeightPct?.toFixed(2)}%
                          </span>
                        </td>
                        <td className="py-2 px-3 text-center">
                          <span
                            className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${
                              sec.fundCount >= 2
                                ? 'bg-indigo-500/20 text-indigo-300 border border-indigo-500/40'
                                : 'bg-surface-raised text-text-tertiary'
                            }`}
                            title={sec.fundCount >= 2 ? 'Held by multiple constituent funds' : 'Held by 1 fund'}
                          >
                            {sec.fundCount}
                          </span>
                        </td>
                        <td className="py-2 px-3 text-right">
                          <button
                            onClick={() => setExpandedSecurityKey(isExpanded ? null : key)}
                            className="text-[10px] font-mono text-text-tertiary hover:text-text-primary underline"
                          >
                            {isExpanded ? 'Hide Details' : `Inspect (${sec.contributions?.length || 0})`}
                          </button>
                        </td>
                      </tr>
                      {/* EXPANDED FUND CONTRIBUTIONS ROW */}
                      {isExpanded && (
                        <tr className="bg-surface-raised/20">
                          <td colSpan={7} className="p-3">
                            <div className="bg-surface-raised/60 p-3 rounded border border-border-subtle space-y-2 text-[10px] font-mono">
                              <span className="font-bold text-text-secondary block">
                                Constituent Fund Breakdown for {sec.canonicalName}:
                              </span>
                              <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-2">
                                {sec.contributions?.map((c) => (
                                  <div
                                    key={c.schemeOptionId}
                                    className="p-2 bg-surface-raised rounded border border-border-subtle space-y-1"
                                  >
                                    <div className="font-semibold text-text-primary truncate" title={c.fundName}>
                                      {c.fundName}
                                    </div>
                                    <div className="flex justify-between text-text-tertiary">
                                      <span>Weight in Fund:</span>
                                      <span className="text-text-secondary">{c.holdingWeightInFundPct?.toFixed(2)}%</span>
                                    </div>
                                    <div className="flex justify-between text-text-tertiary">
                                      <span>Fund Portfolio Weight:</span>
                                      <span className="text-text-secondary">{c.fundWeightInPortfolioPct?.toFixed(1)}%</span>
                                    </div>
                                    <div className="flex justify-between pt-1 border-t border-border-subtle font-bold">
                                      <span className="text-indigo-400">Net Contribution:</span>
                                      <span className="text-indigo-300">{c.effectiveWeightContributionPct?.toFixed(2)}%</span>
                                    </div>
                                  </div>
                                ))}
                              </div>
                            </div>
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
        {filteredSecurities.length > 50 && (
          <p className="text-[10px] font-mono text-text-tertiary text-center">
            Showing top 50 of {filteredSecurities.length} matching securities. Use search to find specific tickers.
          </p>
        )}
      </div>

      {/* SECTION B: CROSS-FUND HOLDINGS OVERLAP */}
      <div className="panel p-5 space-y-4">
        <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-2 pb-3 border-b border-border-subtle">
          <div>
            <h3 className="text-[13px] font-semibold text-text-primary flex items-center gap-2">
              <span>Section B — Cross-Fund Holdings Overlap</span>
              <span className="text-[10px] font-mono text-text-tertiary">
                ({data?.pairwiseOverlap?.length || 0} pairwise combinations)
              </span>
            </h3>
            <p className="text-[10px] font-mono text-text-tertiary">
              Quantitative commonality matrix measuring shared security weights between constituent fund portfolios.
            </p>
          </div>
          <div className="text-[10px] font-mono text-text-tertiary bg-surface-raised px-2.5 py-1 rounded border border-border-subtle">
            Formula: O_ij = ∑ min(w_i,s, w_j,s) (Cash Excluded)
          </div>
        </div>

        {/* OVERLAP METHODOLOGY WARNING */}
        <div className="p-3 bg-surface-raised/40 rounded border border-border-subtle text-[10px] font-mono text-text-tertiary space-y-1">
          <p>
            <strong className="text-text-secondary">Methodological Distinction:</strong> This metric calculates <em>pairwise static holdings overlap</em>. It does not measure or imply future return correlation, as active managers trade dynamically and manage sector beta differently.
          </p>
        </div>

        {/* PAIRWISE OVERLAP CARDS */}
        {data?.pairwiseOverlap?.length === 0 ? (
          <p className="text-[11px] font-mono text-text-tertiary py-4 text-center">
            Pairwise overlap requires at least 2 constituent mutual funds.
          </p>
        ) : (
          <div className="space-y-4">
            {data?.pairwiseOverlap?.map((pair: PairwiseOverlapResultDto, idx: number) => {
              const isAsynchronous = pair.temporalDeltaDays > 30;
              return (
                <div
                  key={idx}
                  className="p-4 bg-surface-raised/50 rounded border border-border-subtle space-y-3"
                >
                  <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2">
                    <div className="space-y-0.5">
                      <div className="flex items-center gap-2 font-semibold text-text-primary text-[12px]">
                        <span>{pair.fundAName}</span>
                        <span className="text-text-tertiary font-mono">⟷</span>
                        <span>{pair.fundBName}</span>
                      </div>
                      <div className="text-[10px] font-mono text-text-tertiary">
                        Snapshots: {pair.observationDateA} vs {pair.observationDateB} (Lag: {pair.temporalDeltaDays} days)
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <span
                        className={`px-2 py-0.5 text-[9px] font-mono rounded ${
                          isAsynchronous
                            ? 'bg-amber-500/20 text-amber-300 border border-amber-500/40'
                            : 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30'
                        }`}
                      >
                        {pair.comparabilityState}
                      </span>
                      <div className="text-right">
                        <span className="text-[16px] font-mono font-bold text-indigo-400">
                          {pair.overlapWeightPct?.toFixed(2)}%
                        </span>
                        <span className="text-[9px] font-mono text-text-tertiary block">
                          {pair.sharedSecurityCount} shared stocks
                        </span>
                      </div>
                    </div>
                  </div>

                  {isAsynchronous && (
                    <div className="p-2 bg-amber-500/10 rounded border border-amber-500/30 text-[10px] font-mono text-amber-300">
                      ⚠️ Reporting Lag Warning: Snapshot dates differ by {pair.temporalDeltaDays} days. High overlap figures may reflect month-end reporting timing differences.
                    </div>
                  )}

                  {/* TOP SHARED POSITIONS TABLE */}
                  {pair.topSharedSecurities && pair.topSharedSecurities.length > 0 && (
                    <div className="pt-2">
                      <span className="text-[10px] font-mono text-text-tertiary uppercase block mb-1">
                        Largest Shared Underlying Positions:
                      </span>
                      <div className="overflow-x-auto border border-border-subtle rounded">
                        <table className="w-full text-left text-[10px] font-mono">
                          <thead className="bg-surface-raised/70 border-b border-border-subtle text-text-tertiary uppercase text-[9px]">
                            <tr>
                              <th className="py-1.5 px-2.5">Shared Security</th>
                              <th className="py-1.5 px-2.5">Sector</th>
                              <th className="py-1.5 px-2.5 text-right">Weight in Fund A</th>
                              <th className="py-1.5 px-2.5 text-right">Weight in Fund B</th>
                              <th className="py-1.5 px-2.5 text-right font-bold text-indigo-300">Overlap Weight</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-border-subtle">
                            {pair.topSharedSecurities.slice(0, 8).map((sh, sIdx) => (
                              <tr key={sIdx} className="hover:bg-surface-raised/40">
                                <td className="py-1 px-2.5 font-semibold text-text-primary">
                                  {sh.securityName}
                                </td>
                                <td className="py-1 px-2.5 text-text-secondary">{sh.sector}</td>
                                <td className="py-1 px-2.5 text-right text-text-tertiary">
                                  {sh.weightInA?.toFixed(2)}%
                                </td>
                                <td className="py-1 px-2.5 text-right text-text-tertiary">
                                  {sh.weightInB?.toFixed(2)}%
                                </td>
                                <td className="py-1 px-2.5 text-right font-bold text-indigo-400">
                                  {sh.overlapWeightPct?.toFixed(2)}%
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* SECTION C: SECTOR AND MARKET-CAP CONCENTRATION */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        {/* SECTOR ALLOCATION */}
        <div className="panel p-5 space-y-4">
          <div className="flex items-center justify-between pb-2 border-b border-border-subtle">
            <div>
              <h3 className="text-[13px] font-semibold text-text-primary">
                Consolidated Sector Allocation
              </h3>
              <p className="text-[10px] font-mono text-text-tertiary">
                Macro-sector distribution. Click a sector to filter Section A.
              </p>
            </div>
          </div>

          <div className="space-y-3">
            {data?.sectors?.map((sec: ConsolidatedSectorExposureDto, idx: number) => {
              const isSelected = selectedCategoryFilter === sec.macroSector;
              return (
                <div
                  key={idx}
                  onClick={() => setSelectedCategoryFilter(isSelected ? 'ALL' : sec.macroSector)}
                  className={`p-2.5 rounded border transition-colors cursor-pointer ${
                    isSelected
                      ? 'bg-indigo-600/15 border-indigo-500/50'
                      : 'bg-surface-raised/40 border-border-subtle hover:bg-surface-raised/70'
                  }`}
                >
                  <div className="flex items-center justify-between text-[11px] font-mono mb-1">
                    <span className="font-semibold text-text-primary flex items-center gap-1.5">
                      <span>{sec.macroSector}</span>
                      {isSelected && (
                        <span className="text-[8px] bg-indigo-500 text-white px-1 py-0.2 rounded font-bold">
                          ACTIVE
                        </span>
                      )}
                    </span>
                    <span className="font-bold text-indigo-300">
                      {sec.consolidatedWeightPct?.toFixed(2)}%
                    </span>
                  </div>
                  {/* Progress bar */}
                  <div className="w-full bg-surface-raised rounded-full h-1.5 overflow-hidden">
                    <div
                      className="bg-indigo-500 h-1.5 rounded-full transition-all duration-500"
                      style={{ width: `${Math.min(100, sec.consolidatedWeightPct || 0)}%` }}
                    />
                  </div>
                  <div className="flex items-center justify-between text-[9px] font-mono text-text-tertiary mt-1">
                    <span>{sec.classifiedSecuritiesCount} securities</span>
                    <span>Click to filter securities ↗</span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* MARKET-CAP ALLOCATION */}
        <div className="panel p-5 space-y-4">
          <div className="flex items-center justify-between pb-2 border-b border-border-subtle">
            <div>
              <h3 className="text-[13px] font-semibold text-text-primary">
                Market-Cap Distribution (AMFI PIT)
              </h3>
              <p className="text-[10px] font-mono text-text-tertiary">
                Point-in-time SEBI/AMFI market capitalization categorization.
              </p>
            </div>
          </div>

          {/* COMPOSITE DISTRIBUTION BAR */}
          <div className="space-y-1.5">
            <span className="text-[10px] font-mono text-text-tertiary">Portfolio Capitalization Mix:</span>
            <div className="w-full h-3 bg-surface-raised rounded-full overflow-hidden flex">
              {data?.marketCaps?.map((m: ConsolidatedMarketCapExposureDto, idx: number) => {
                const color =
                  m.category === 'LARGE_CAP'
                    ? 'bg-blue-500'
                    : m.category === 'MID_CAP'
                    ? 'bg-amber-500'
                    : m.category === 'SMALL_CAP'
                    ? 'bg-purple-500'
                    : 'bg-emerald-500';
                return (
                  <div
                    key={idx}
                    className={`${color} h-full transition-all duration-500`}
                    style={{ width: `${m.consolidatedWeightPct || 0}%` }}
                    title={`${m.category}: ${m.consolidatedWeightPct?.toFixed(1)}%`}
                  />
                );
              })}
            </div>
          </div>

          {/* MARKET CAP BREAKDOWN CARDS */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 pt-1">
            {data?.marketCaps?.map((m: ConsolidatedMarketCapExposureDto, idx: number) => {
              const isSelected = selectedCategoryFilter === m.category;
              return (
                <div
                  key={idx}
                  onClick={() => setSelectedCategoryFilter(isSelected ? 'ALL' : m.category)}
                  className={`p-3 rounded border transition-colors cursor-pointer ${
                    isSelected
                      ? 'bg-indigo-600/15 border-indigo-500/50'
                      : 'bg-surface-raised/40 border-border-subtle hover:bg-surface-raised/70'
                  }`}
                >
                  <div className="flex items-center justify-between text-[11px] font-mono mb-1">
                    <span className="font-semibold text-text-primary capitalize">
                      {m.category?.toLowerCase()?.replace('_', ' ')}
                    </span>
                    <span className="font-bold text-text-primary">
                      {m.consolidatedWeightPct?.toFixed(2)}%
                    </span>
                  </div>
                  <div className="flex items-center justify-between text-[9px] font-mono text-text-tertiary">
                    <span>{m.securitiesCount} securities</span>
                    <span className="text-indigo-400">Filter ↗</span>
                  </div>
                </div>
              );
            })}
          </div>

          <div className="p-3 bg-surface-raised/40 rounded border border-border-subtle text-[10px] font-mono text-text-tertiary space-y-1">
            <span className="text-text-secondary font-bold">Point-in-Time Governance:</span>
            <p>
              Securities are classified using AMFI semi-annual market-cap categorizations valid as of {data?.asOfDate}, avoiding survivorship and look-ahead classification drift.
            </p>
          </div>
        </div>
      </div>

      {/* SECTION D: INVESTIGATION FINDINGS */}
      <div className="panel p-5 space-y-4">
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2 pb-3 border-b border-border-subtle">
          <div>
            <h3 className="text-[13px] font-semibold text-text-primary flex items-center gap-2">
              <span>Section D — Institutional Risk Investigation Findings</span>
              <span className="text-[10px] font-mono text-text-tertiary">
                ({data?.findings?.length || 0} diagnostic triggers)
              </span>
            </h3>
            <p className="text-[10px] font-mono text-text-tertiary">
              Transparent deterministic diagnostic flags based on centralized portfolio concentration and overlap rules.
            </p>
          </div>
          {/* Findings Filter */}
          <div className="flex items-center gap-1.5 text-[10px] font-mono">
            <span className="text-text-tertiary">Severity:</span>
            {['ALL', 'HIGH', 'MEDIUM', 'INFO'].map((sev) => (
              <button
                key={sev}
                onClick={() => setFindingsSeverityFilter(sev)}
                className={`px-2 py-0.5 rounded transition-colors ${
                  findingsSeverityFilter === sev
                    ? 'bg-surface-raised text-text-primary font-bold border border-indigo-400'
                    : 'text-text-tertiary hover:text-text-primary'
                }`}
              >
                {sev}
              </button>
            ))}
          </div>
        </div>

        {/* FINDINGS CARDS */}
        {filteredFindings.length === 0 ? (
          <p className="text-[11px] font-mono text-text-tertiary py-4 text-center">
            No diagnostic triggers match the current severity filter.
          </p>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3.5">
            {filteredFindings.map((finding: RiskDriverFindingDto) => {
              const isHigh = finding.severity === 'HIGH';
              const isMedium = finding.severity === 'MEDIUM';
              const isWarning = finding.severity === 'WARNING';
              const borderClass = isHigh
                ? 'border-rose-500/40 bg-rose-500/5'
                : isMedium || isWarning
                ? 'border-amber-500/40 bg-amber-500/5'
                : 'border-border-subtle bg-surface-raised/40';

              const badgeClass = isHigh
                ? 'bg-rose-500/20 text-rose-300 border-rose-500/40'
                : isMedium || isWarning
                ? 'bg-amber-500/20 text-amber-300 border-amber-500/40'
                : 'bg-indigo-500/10 text-indigo-300 border-indigo-500/30';

              return (
                <div
                  key={finding.id}
                  className={`p-4 rounded border ${borderClass} space-y-3 flex flex-col justify-between`}
                >
                  <div className="space-y-2">
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-[9px] font-mono uppercase font-bold text-text-tertiary">
                        {finding.category.replace('_', ' ')}
                      </span>
                      <span className={`px-2 py-0.5 text-[9px] font-mono font-bold rounded border ${badgeClass}`}>
                        {finding.severity}
                      </span>
                    </div>

                    <h4 className="text-[12px] font-bold text-text-primary">
                      {finding.title}
                    </h4>

                    {/* COMPARATIVE PILLS: OBSERVED VS THRESHOLD */}
                    <div className="flex flex-wrap items-center gap-2 pt-1 text-[10px] font-mono">
                      <div className="px-2 py-0.5 bg-surface-raised rounded border border-border-subtle">
                        <span className="text-text-tertiary">Observed: </span>
                        <strong className="text-text-primary">{finding.observedValue}</strong>
                      </div>
                      <div className="px-2 py-0.5 bg-surface-raised rounded border border-border-subtle">
                        <span className="text-text-tertiary">Threshold: </span>
                        <span className="text-text-secondary">{finding.threshold}</span>
                      </div>
                    </div>

                    <p className="text-[11px] text-text-secondary font-mono leading-relaxed">
                      {finding.explanation}
                    </p>
                  </div>

                  {/* ALLOCATOR INVESTIGATION PROMPT */}
                  <div className="pt-2 border-t border-border-subtle/60 text-[10px] font-mono space-y-1 bg-surface-raised/50 -mx-4 -mb-4 p-3 rounded-b">
                    <span className="font-bold text-indigo-300 block">
                      🔎 What to Investigate Next:
                    </span>
                    <p className="text-text-tertiary leading-relaxed">
                      {finding.investigationPrompt}
                    </p>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* NON-ADVISORY DISCLAIMER */}
        <div className="pt-3 border-t border-border-subtle text-[10px] font-mono text-text-tertiary text-center">
          Findings are deterministic diagnostic prompts generated from verified published portfolio disclosures. YUKIRA does not issue BUY, SELL, or HOLD investment advice.
        </div>
      </div>
    </div>
  );
}
