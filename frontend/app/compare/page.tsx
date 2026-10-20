"use client";

import { useEffect, useState, useMemo, Suspense, useCallback } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import { PageContainer, SectionHeading } from "@/components/layout/PageContainer";
import { StateView } from "@/components/epistemic/StateView";
import { fetchAllSchemes, fetchSchemeOptionsBySchemeId } from "@/lib/api/schemes";
import {
  executeComparison,
  fetchPairwiseOverlap,
  fetchPairwiseCorrelation
} from "@/lib/api/comparison";
import { Scheme, SchemeOption } from "@/types/domain";
import {
  ComparisonResponse,
  ComparisonMetric,
  ComparisonFund,
  PortfolioOverlapResponseDto,
  PairwiseCorrelationDto
} from "@/types/comparison";
import { useWatchlist } from "@/lib/hooks/useWatchlist";
import { ResearchNoteModal } from "@/components/watchlist/ResearchNoteModal";
import { WatchlistResponse } from "@/lib/api/investor";
import { WithinFundDriftComparison } from "@/components/comparison/WithinFundDriftComparison";
import { BenchmarkRelativeComparison } from "@/components/comparison/BenchmarkRelativeComparison";
import { CumulativeGrowthComparison } from "@/components/comparison/CumulativeGrowthComparison";

const CANONICAL_DIMENSIONS = [
  { code: 'RETURN_QUALITY', name: 'Return Quality', weight: '30%' },
  { code: 'RISK_QUALITY', name: 'Risk Quality', weight: '25%' },
  { code: 'BENCHMARK_RELATIVE_QUALITY', name: 'Benchmark-Relative Quality', weight: '25%' },
  { code: 'CONSISTENCY_DOWNSIDE_QUALITY', name: 'Consistency & Downside Quality', weight: '20%' },
];

// Pre-selected Canonical Pilot Schemes:
// Scheme 1: HDFC Flexi Cap Fund (Direct Growth Option ID: 1, AMFI: 118955, ISIN: INF179K01UT0)
// Scheme 1720: Parag Parikh Flexi Cap Fund (Direct Growth Option ID: 10262, AMFI: 122639, ISIN: INF879O01027)
// Scheme 1694: Parag Parikh ELSS Tax Saver Fund (Direct Growth Option ID: 10151, AMFI: 147481, ISIN: INF879O01100)
const CANONICAL_PILOT_SCHEME_IDS = [1, 1720, 1694];
const OPTION_TO_SCHEME_MAP: Record<number, number> = {
  1: 1,
  10262: 1720,
  10151: 1694,
};
const SCHEME_TO_DEFAULT_OPTION_MAP: Record<number, number> = {
  1: 1,
  1720: 10262,
  1694: 10151,
};

type CategoryFilter = 'ALL' | 'PERFORMANCE' | 'RISK' | 'BENCHMARK' | 'PORTFOLIO';

function ComparabilityBadge({ status, reason }: { status?: string; reason?: string | null }) {
  const normStatus = status || 'COMPARABLE';
  let badgeClass = 'border-emerald-500/30 text-emerald-400 bg-emerald-500/10';
  let label = 'COMPARABLE';

  if (normStatus === 'COMPARABLE_WITH_LIMITATIONS') {
    badgeClass = 'border-amber-500/30 text-amber-300 bg-amber-500/10';
    label = 'LIMITATIONS';
  } else if (normStatus === 'INSUFFICIENT_DATA') {
    badgeClass = 'border-rose-500/30 text-rose-400 bg-rose-500/10';
    label = 'INSUFFICIENT DATA';
  } else if (normStatus === 'NOT_COMPARABLE') {
    badgeClass = 'border-rose-500/30 text-rose-400 bg-rose-500/10';
    label = 'NOT COMPARABLE';
  }

  return (
    <span
      className={`inline-flex items-center rounded border px-1.5 py-0.5 font-mono text-[10px] font-semibold uppercase tracking-wider ${badgeClass}`}
      title={reason || undefined}
    >
      {label}
    </span>
  );
}

interface OverlapPairState {
  loading: boolean;
  data: PortfolioOverlapResponseDto | null;
  error: string | null;
}

interface CorrelationPairState {
  loading: boolean;
  data: PairwiseCorrelationDto | null;
  error: string | null;
}

function ComparisonView({
  comparison,
  allSchemes,
  schemeOptions,
  asOfDate,
  knowledgeCutoff,
  onReset,
  onSwapOption,
  onRemoveOption,
  onHorizonChange,
}: {
  comparison: ComparisonResponse;
  allSchemes: Scheme[];
  schemeOptions: Map<number, SchemeOption[]>;
  asOfDate: string;
  knowledgeCutoff: string;
  onReset: () => void;
  onSwapOption: (schemeId: number, oldOptionId: number, newOptionId: number) => void;
  onRemoveOption: (optionId: number) => void;
  onHorizonChange: (asOfDate: string, knowledgeCutoff: string) => void;
}) {
  const [activeCategory, setActiveCategory] = useState<CategoryFilter>('ALL');
  const { watchlistItems, saveResearchNote } = useWatchlist();
  const [investigationTarget, setInvestigationTarget] = useState<{
    fund: WatchlistResponse;
    prefill: {
      investigationQuestion: string;
      reasonSaved: string;
    };
  } | null>(null);
  const [isNoteModalOpen, setIsNoteModalOpen] = useState(false);

  // Pairwise Overlap & Correlation States
  const [overlapStates, setOverlapStates] = useState<Map<string, OverlapPairState>>(new Map());
  const [correlationStates, setCorrelationStates] = useState<Map<string, CorrelationPairState>>(new Map());
  const [expandedOverlapPairs, setExpandedOverlapPairs] = useState<Set<string>>(new Set());

  // Generate distinct unordered pairs from compared funds
  const fundPairs = useMemo(() => {
    const funds = comparison.funds || [];
    const pairs: [ComparisonFund, ComparisonFund][] = [];
    for (let i = 0; i < funds.length; i++) {
      for (let j = i + 1; j < funds.length; j++) {
        pairs.push([funds[i], funds[j]]);
      }
    }
    return pairs;
  }, [comparison.funds]);

  // Fetch pairwise overlap and correlation for all pairs
  useEffect(() => {
    const cutoff = comparison.period?.knowledgeCutoffTime || comparison.knowledgeCutoff || '2024-01-31T23:59:59+05:30';

    fundPairs.forEach(([fundA, fundB]) => {
      const pairKey = `${fundA.schemeOptionId}-${fundB.schemeOptionId}`;

      // Overlap fetch
      setOverlapStates((prev) => {
        const next = new Map(prev);
        if (!next.has(pairKey)) {
          next.set(pairKey, { loading: true, data: null, error: null });
        }
        return next;
      });

      fetchPairwiseOverlap(fundA.schemeOptionId, fundB.schemeOptionId, cutoff)
        .then((data) => {
          setOverlapStates((prev) => new Map(prev).set(pairKey, { loading: false, data, error: null }));
        })
        .catch((err: unknown) => {
          setOverlapStates((prev) =>
            new Map(prev).set(pairKey, {
              loading: false,
              data: null,
              error: err instanceof Error ? err.message : 'Overlap calculation failed',
            })
          );
        });

      // Correlation fetch
      setCorrelationStates((prev) => {
        const next = new Map(prev);
        if (!next.has(pairKey)) {
          next.set(pairKey, { loading: true, data: null, error: null });
        }
        return next;
      });

      fetchPairwiseCorrelation(fundA.schemeOptionId, fundB.schemeOptionId, cutoff)
        .then((data) => {
          setCorrelationStates((prev) => new Map(prev).set(pairKey, { loading: false, data, error: null }));
        })
        .catch((err: unknown) => {
          setCorrelationStates((prev) =>
            new Map(prev).set(pairKey, {
              loading: false,
              data: null,
              error: err instanceof Error ? err.message : 'Correlation calculation failed',
            })
          );
        });
    });
  }, [fundPairs, comparison.period?.knowledgeCutoffTime, comparison.knowledgeCutoff]);

  const toggleOverlapExpanded = (pairKey: string) => {
    setExpandedOverlapPairs((prev) => {
      const next = new Set(prev);
      if (next.has(pairKey)) next.delete(pairKey);
      else next.add(pairKey);
      return next;
    });
  };

  const handleInvestigateFund = (fund: ComparisonFund, question: string, reason: string) => {
    const existing = watchlistItems.find(
      (w) =>
        (fund.schemeOptionId && w.schemeOptionId === fund.schemeOptionId) ||
        (fund.schemeId ? w.schemeId === fund.schemeId : false)
    );

    const targetFund: WatchlistResponse = {
      id: existing?.id ?? -(fund.schemeOptionId || fund.schemeId || 1),
      schemeId: fund.schemeId || fund.schemeOptionId || 1,
      schemeCode: fund.schemeCode || fund.amfiCode || `SCHEME_${fund.schemeOptionId}`,
      schemeName: fund.schemeName,
      amcName: fund.amcName,
      schemeOptionId: fund.schemeOptionId || null,
      optionType: fund.optionType || "GROWTH",
      planType: fund.planType || "DIRECT",
      amfiCode: fund.amfiCode || null,
      isin: fund.isin || null,
      latestNav: null,
      latestNavDate: null,
      yukiraScore: fund.yukiraScore ? fund.yukiraScore.score : null,
      yukiraScoreStatus: fund.yukiraScore ? fund.yukiraScore.status : "CANDIDATE",
      reasonSaved: existing?.reasonSaved || null,
      investigationQuestion: existing?.investigationQuestion || null,
      nextStep: existing?.nextStep || null,
      createdAt: existing?.createdAt || null,
      updatedAt: existing?.updatedAt || null,
    };

    setInvestigationTarget({
      fund: targetFund,
      prefill: {
        investigationQuestion: question,
        reasonSaved: reason,
      },
    });
    setIsNoteModalOpen(true);
  };

  const filteredMetrics = useMemo(() => {
    if (!comparison.metrics) return [];
    if (activeCategory === 'ALL') return comparison.metrics;
    if (activeCategory === 'PERFORMANCE') {
      return comparison.metrics.filter(m => m.category === 'RETURN_BENCHMARK' || m.metricCode.startsWith('RET-'));
    }
    if (activeCategory === 'RISK') {
      return comparison.metrics.filter(m => m.category === 'RISK_TAIL' || m.category === 'RISK_ADJUSTED' || m.metricCode.startsWith('RSK-') || m.metricCode.startsWith('RAT-'));
    }
    if (activeCategory === 'BENCHMARK') {
      return comparison.metrics.filter(m => m.category === 'MARKET_SENSITIVITY_ALPHA' || m.category === 'BENCHMARK_ALPHA' || m.metricCode.startsWith('MKT-') || m.metricCode.startsWith('REL-'));
    }
    if (activeCategory === 'PORTFOLIO') {
      return comparison.metrics.filter(m => m.category === 'PORTFOLIO_CONSTRUCTION' || m.metricCode.startsWith('CONC-'));
    }
    return comparison.metrics;
  }, [comparison.metrics, activeCategory]);

  return (
    <div className="space-y-8">
      {/* Comparison Top Banner */}
      <div className="flex flex-wrap items-end justify-between gap-3 border-b border-border pb-4">
        <div>
          <div className="flex items-center gap-2">
            <h3 className="text-h2 text-text-primary">
              Mutual Fund Evidence Comparison
            </h3>
            <span className="status-badge state-verified text-[10px]">COMPARABILITY CONTRACT</span>
          </div>
          <p className="mono-meta mt-1">
            Analysis Cutoff: <span className="text-text-primary font-semibold">{asOfDate}</span> • Knowledge Cutoff: <span className="text-text-secondary">{knowledgeCutoff}</span>
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {/* Temporal Horizon Selector */}
          <div className="flex items-center gap-1 font-mono text-[11px] bg-surface rounded border border-border p-1">
            <span className="text-text-muted px-1.5 uppercase font-bold text-[10px]">Horizon:</span>
            <button
              onClick={() => onHorizonChange("2024-01-15", "2024-01-31T23:59:59+05:30")}
              className={`px-2 py-0.5 rounded transition-colors ${
                asOfDate === "2024-01-15"
                  ? "bg-accent text-surface-dark font-bold shadow-sm"
                  : "text-text-secondary hover:text-text-primary"
              }`}
            >
              2024-01-15 (Pilot Baseline)
            </button>
            <button
              onClick={() => onHorizonChange("2026-10-01", "2026-10-15T23:59:59+05:30")}
              className={`px-2 py-0.5 rounded transition-colors ${
                asOfDate === "2026-10-01"
                  ? "bg-accent text-surface-dark font-bold shadow-sm"
                  : "text-text-secondary hover:text-text-primary"
              }`}
            >
              2026-10-01 (Latest Market)
            </button>
          </div>
          <button onClick={onReset} className="btn btn-secondary btn-sm">
            <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
            </svg>
            <span>Modify Selection</span>
          </button>
        </div>
      </div>

      {/* Coverage Summary Stats */}
      {comparison.coverageSummary && (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 font-mono text-[11px]">
          <div className="panel p-3 bg-surface">
            <span className="text-text-muted text-[10px] block">Selected Share Classes</span>
            <p className="font-semibold text-text-primary text-[14px] mt-0.5">
              {comparison.coverageSummary.totalSelectedFunds} Exact Options
            </p>
          </div>
          <div className="panel p-3 bg-surface">
            <span className="text-text-muted text-[10px] block">Scored Funds</span>
            <p className="font-semibold text-text-primary text-[14px] mt-0.5">
              {comparison.coverageSummary.scoredFundsCount} of {comparison.coverageSummary.totalSelectedFunds} Evaluated
            </p>
          </div>
          <div className="panel p-3 bg-surface">
            <span className="text-text-muted text-[10px] block">Complete Risk Evidence</span>
            <p className="font-semibold text-text-primary text-[14px] mt-0.5">
              {comparison.coverageSummary.completeRiskEvidenceCount} of {comparison.coverageSummary.totalSelectedFunds} Verified
            </p>
          </div>
          <div className="panel p-3 bg-surface">
            <span className="text-text-muted text-[10px] block">Data Quality &amp; Alignment</span>
            <p className="font-semibold text-text-primary text-[14px] mt-0.5">
              {comparison.coverageSummary.differingAsOfDates ? 'Dates Differ' : 'Aligned Cutoff'}
            </p>
          </div>
        </div>
      )}

      {/* Primary Section 01: Fund Identity Header Cards */}
      <section className="space-y-4">
        <div className="flex items-center justify-between">
          <SectionHeading
            ordinal="01. Identity & Profile"
            title="Authoritative Scheme Identity & Quality Scorecard"
            description="Verified share class registration metadata, ISIN identity, and point-in-time YUKIRA Analytical Quality Scores."
          />
        </div>

        <div className="grid gap-4 grid-cols-1 md:grid-cols-3">
          {comparison.funds.map((fund) => {
            const scheme = allSchemes.find(
              (s) => (fund.schemeId ? s.id === fund.schemeId : false) || (fund.schemeCode ? s.code === fund.schemeCode : false)
            );
            const targetSchemeId = scheme?.id || fund.schemeId || OPTION_TO_SCHEME_MAP[fund.schemeOptionId] || 1;
            const scorecardHref = `/funds/${targetSchemeId}?option=${fund.schemeOptionId}#scorecard`;
            const yukiraScore = fund.yukiraScore;
            const scoreStatus = yukiraScore?.status || "NOT_EVALUATED";
            const scoreValue = yukiraScore?.score;

            // Available alternative options for swapping
            const availableOptions = schemeOptions.get(targetSchemeId) || [];

            return (
              <div key={fund.schemeOptionId} className="panel flex flex-col justify-between p-4 space-y-4 border-l-2 border-l-accent">
                <div className="space-y-3">
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <span className="mono-meta block font-bold text-accent">{fund.amcName || 'AMC Identity Verified'}</span>
                      <h4 className="text-[14px] font-semibold text-text-primary mt-0.5 leading-tight">
                        {fund.schemeName}
                      </h4>
                    </div>
                    {comparison.funds.length > 2 && (
                      <button
                        onClick={() => onRemoveOption(fund.schemeOptionId)}
                        className="text-text-muted hover:text-rose-400 p-1"
                        title="Remove fund from comparison"
                        aria-label={`Remove ${fund.schemeName} from comparison`}
                      >
                        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                        </svg>
                      </button>
                    )}
                  </div>

                  <div className="mono-meta flex flex-wrap gap-1.5 items-center">
                    <span className="badge badge-outline">{fund.planType || 'DIRECT'}</span>
                    <span className="badge badge-outline">{fund.optionType || 'GROWTH'}</span>
                    <span>AMFI: {fund.amfiCode || 'N/A'}</span>
                    {fund.isin && <span className="text-text-tertiary font-mono">• {fund.isin}</span>}
                  </div>

                  {/* Share Class Option Swap Dropdown */}
                  {availableOptions.length > 1 && (
                    <div className="pt-1">
                      <label htmlFor={`swap-opt-${fund.schemeOptionId}`} className="block text-[10px] font-mono text-text-muted uppercase mb-1">
                        Swap Share Class
                      </label>
                      <select
                        id={`swap-opt-${fund.schemeOptionId}`}
                        value={fund.schemeOptionId}
                        onChange={(e) => onSwapOption(targetSchemeId, fund.schemeOptionId, Number(e.target.value))}
                        className="field field-mono text-[11px] py-1 px-2 w-full"
                      >
                        {availableOptions.map((opt) => (
                          <option key={opt.id} value={opt.id}>
                            {opt.plan?.planType || 'DIRECT'} • {opt.optionType || 'GROWTH'} (AMFI: {opt.amfiCode || 'N/A'})
                          </option>
                        ))}
                      </select>
                    </div>
                  )}

                  <div className="panel-inset p-3 space-y-2 bg-surface-raised rounded-md">
                    <div className="flex items-center justify-between">
                      <span className="def-label">Analytical Score</span>
                      <span className={`status-badge ${scoreStatus === 'AVAILABLE' ? 'state-approved' : 'state-candidate'}`}>
                        {scoreStatus}
                      </span>
                    </div>

                    <div className="flex items-baseline gap-1.5">
                      {scoreValue !== null && scoreValue !== undefined ? (
                        <>
                          <span className="text-2xl font-mono font-bold text-text-primary">
                            {scoreValue.toFixed(2)}
                          </span>
                          <span className="mono-meta text-[13px]">/ 100</span>
                        </>
                      ) : (
                        <span className="text-[13px] font-mono text-text-muted">
                          {scoreStatus === "INSUFFICIENT_DATA"
                            ? "Insufficient Data"
                            : "Unavailable"}
                        </span>
                      )}
                    </div>

                    <div className="grid grid-cols-2 gap-2 text-[11px] font-mono text-text-secondary border-t border-border pt-2 mt-2">
                      <div>
                        <span className="block text-text-muted text-[10px]">CONFIDENCE</span>
                        <span>
                          {yukiraScore?.confidence !== null && yukiraScore?.confidence !== undefined
                            ? `${yukiraScore.confidence.toFixed(0)} / 100`
                            : "N/A"}
                        </span>
                      </div>
                      <div>
                        <span className="block text-text-muted text-[10px]">AS OF DATE</span>
                        <span>{yukiraScore?.asOfDate || comparison.period?.endDate || '2024-01-15'}</span>
                      </div>
                    </div>
                  </div>

                  {/* Evidence Freshness & Usable Periods */}
                  {(() => {
                    const selectedOpt = availableOptions.find((o) => o.id === fund.schemeOptionId) || availableOptions[0];
                    return (
                      <div className="panel-inset p-3 space-y-1.5 bg-surface-raised rounded-md font-mono text-[11px]" data-testid={`compare-freshness-${fund.schemeOptionId}`}>
                        <div className="flex items-center justify-between pb-1 border-b border-border/40">
                          <span className="text-text-muted text-[10px] uppercase font-bold">Evidence &amp; Freshness</span>
                          <div className="flex items-center gap-1">
                            <span className={`px-1.5 py-0.5 rounded text-[9.5px] uppercase font-semibold border ${
                              selectedOpt?.navFreshnessState === 'CURRENT'
                                ? 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30'
                                : 'bg-amber-500/15 text-amber-300 border-amber-500/30'
                            }`}>
                              NAV: {selectedOpt?.navFreshnessState || (fund.analyticalScore ? 'STALE' : 'UNKNOWN')}
                            </span>
                            <span className={`px-1.5 py-0.5 rounded text-[9.5px] uppercase font-semibold border ${
                              selectedOpt?.holdingsFreshnessState === 'CURRENT'
                                ? 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30'
                                : 'bg-amber-500/15 text-amber-300 border-amber-500/30'
                            }`}>
                              Holdings: {selectedOpt?.holdingsFreshnessState || 'PARTIAL'}
                            </span>
                          </div>
                        </div>
                        <div className="flex justify-between text-text-secondary">
                          <span className="text-text-muted">Market NAV:</span>
                          <span className="text-text-primary">
                            {selectedOpt?.latestNavDate ? `${selectedOpt.latestNavDate} (${selectedOpt.navObservationCount?.toLocaleString()} dates)` : 'As of 2024-01-15'}
                          </span>
                        </div>
                        <div className="flex justify-between text-text-secondary">
                          <span className="text-text-muted">Disclosed Portfolio:</span>
                          <span className="text-text-primary">
                            {selectedOpt?.latestHoldingsDate
                              ? `${selectedOpt.latestHoldingsDate} (${selectedOpt.holdingsCount} pos · ${selectedOpt.holdingsRawSumPercentage?.toFixed(1) ?? '—'}% raw)`
                              : 'As of 2024-01-31 (10 pos)'}
                          </span>
                        </div>
                        <div className="flex justify-between text-text-secondary">
                          <span className="text-text-muted">Snapshot History:</span>
                          <span className="text-text-primary">
                            {selectedOpt?.snapshotCount !== undefined ? `${selectedOpt.snapshotCount} snapshot${selectedOpt.snapshotCount === 1 ? ' (drift requires ≥2)' : 's'}` : '1 snapshot (drift requires ≥2)'}
                          </span>
                        </div>
                      </div>
                    );
                  })()}
                </div>

                <Link
                  href={scorecardHref}
                  className="btn btn-secondary btn-sm w-full justify-center text-center mt-2"
                >
                  <span>View Detailed Analytical Profile</span>
                  <svg className="h-3.5 w-3.5 ml-1" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
                  </svg>
                </Link>
              </div>
            );
          })}
        </div>

        {/* Epistemic Observational Horizon & Comparability Notice */}
        <div className="p-3.5 rounded-xl border border-amber-500/30 bg-amber-500/5 text-xs font-mono dark:bg-amber-950/20 dark:border-amber-800/60 flex items-start gap-3">
          <span className="text-amber-500 text-sm mt-0.5">⚠</span>
          <div className="space-y-1">
            <p className="font-semibold text-text-primary">
              Observational Horizon &amp; Comparability Notice
            </p>
            <p className="text-text-secondary leading-relaxed">
              Quantitative comparisons require equivalent observation regimes. Do not assume metrics are directly comparable when usable date ranges, publication cadences, or holdings disclosure percentages differ. Disclosed holdings cover partial exposure (e.g. 48%–55%) and are never normalized to 100%.
            </p>
          </div>
        </div>
      </section>

      {/* Section 02: Analytical Score Dimensions Side-by-Side */}
      <section className="space-y-4">
        <SectionHeading
          ordinal="02. Quality Dimensions"
          title="Analytical Score Dimension Comparison"
          description="Side-by-side performance across 4 institutional quality dimensions."
        />

        <div className="panel scroll-region mb-6">
          <div className="scroll-region overflow-x-auto">
            <table className="data-table w-full">
              <thead>
                <tr>
                  <th className="w-1/3">Score Dimension</th>
                  {comparison.funds.map((fund) => (
                    <th key={fund.schemeOptionId} className="text-left">
                      <span className="block text-[12px] font-semibold text-text-primary">
                        {fund.schemeName}
                      </span>
                      <span className="mono-meta block">{fund.planType} • {fund.optionType}</span>
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {CANONICAL_DIMENSIONS.map((dimDef) => (
                  <tr key={dimDef.code} className="transition-colors hover:bg-surface-raised">
                    <td>
                      <div className="space-y-0.5">
                        <div className="text-[12.5px] font-semibold text-text-primary">
                          {dimDef.name}
                        </div>
                        <div className="mono-meta">Weight: {dimDef.weight}</div>
                      </div>
                    </td>
                    {comparison.funds.map((fund) => {
                      const scoreDim = fund.yukiraScore?.dimensions?.find(
                        (d) => d.dimension === dimDef.code || d.dimensionName === dimDef.name
                      );

                      if (!scoreDim || scoreDim.score === null || scoreDim.score === undefined) {
                        return (
                          <td key={fund.schemeOptionId} className="data-unavailable">
                            <span className="mono-meta">Not available</span>
                          </td>
                        );
                      }

                      return (
                        <td key={fund.schemeOptionId}>
                          <div className="data-value-md">{scoreDim.score.toFixed(2)}</div>
                          <div className="mono-meta mt-1">Status: {scoreDim.status}</div>
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </section>

      {/* Section 03: Primary Benchmark-Relative Analysis (NIFTY 500 TRI) */}
      <BenchmarkRelativeComparison
        comparison={comparison}
        asOfDate={asOfDate}
        knowledgeCutoff={knowledgeCutoff}
      />

      {/* Section 04: Cumulative Growth & Return Divergence */}
      <CumulativeGrowthComparison
        funds={comparison.funds}
        asOfDate={asOfDate}
        knowledgeCutoff={knowledgeCutoff}
      />

      {/* Section 05: Detailed Metric Matrix with Categorized Tabs */}
      <section className="space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <SectionHeading
            ordinal="05. Quantitative Metrics"
            title="Canonical Quantitative Comparison Matrix"
            description="Vectorized return, risk, market sensitivity, and portfolio concentration metrics evaluated under strict Comparability Contract."
          />
        </div>

        {/* Category Filters */}
        <div className="flex flex-wrap gap-2 font-mono text-[11px] border-b border-border pb-3">
          {(['ALL', 'PERFORMANCE', 'RISK', 'BENCHMARK', 'PORTFOLIO'] as CategoryFilter[]).map((cat) => (
            <button
              key={cat}
              onClick={() => setActiveCategory(cat)}
              className={`rounded px-2.5 py-1 transition-colors ${
                activeCategory === cat
                  ? 'bg-accent text-surface-dark font-bold'
                  : 'bg-surface hover:bg-surface-raised text-text-secondary border border-border'
              }`}
            >
              {cat === 'ALL' && 'All Metrics'}
              {cat === 'PERFORMANCE' && 'Performance (Common Horizon)'}
              {cat === 'RISK' && 'Volatility & Downside Risk'}
              {cat === 'BENCHMARK' && 'Benchmark Sensitivity (NIFTY 500)'}
              {cat === 'PORTFOLIO' && 'Portfolio Concentration'}
            </button>
          ))}
        </div>

        <div className="panel scroll-region">
          <div className="scroll-region overflow-x-auto">
            <table className="data-table w-full">
              <thead>
                <tr>
                  <th className="w-1/3">Quantitative Metric</th>
                  <th className="w-28 text-left">Comparability</th>
                  {comparison.funds.map((fund) => (
                    <th key={fund.schemeOptionId} className="text-left">
                      <span className="block text-[12px] font-semibold text-text-primary">
                        {fund.schemeName}
                      </span>
                      <span className="mono-meta block">{fund.planType} • {fund.optionType}</span>
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {filteredMetrics.map((metric) => (
                  <MetricRow
                    key={metric.metricCode}
                    metric={metric}
                    funds={comparison.funds}
                    onInvestigateFund={handleInvestigateFund}
                  />
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </section>

      {/* Section 06: Pairwise Portfolio Holdings Overlap */}
      <section className="space-y-4">
        <SectionHeading
          ordinal="06. Portfolio Overlap"
          title="Pairwise Portfolio Holdings Overlap"
          description="Exact security-level intersection and source-disclosed holdings weights. Respects reporting dates and flags partial or stale disclosures."
        />

        <div className="grid gap-4 grid-cols-1 lg:grid-cols-2">
          {fundPairs.map(([fundA, fundB]) => {
            const pairKey = `${fundA.schemeOptionId}-${fundB.schemeOptionId}`;
            const state = overlapStates.get(pairKey);
            const isExpanded = expandedOverlapPairs.has(pairKey);

            if (!state || state.loading) {
              return (
                <div key={pairKey} className="panel p-4 space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-[13px] text-text-primary">
                      {fundA.schemeName} vs {fundB.schemeName}
                    </span>
                    <span className="status-badge state-candidate text-[10px]">CALCULATING OVERLAP…</span>
                  </div>
                  <div className="h-16 animate-pulse bg-surface-raised rounded" />
                </div>
              );
            }

            if (state.error || !state.data) {
              return (
                <div key={pairKey} className="panel p-4 space-y-2 border-l-2 border-l-rose-500">
                  <span className="font-semibold text-[13px] text-text-primary">
                    {fundA.schemeName} vs {fundB.schemeName}
                  </span>
                  <div className="state-panel-error p-3 text-[11px] font-mono">
                    Overlap calculation failed: {state.error || 'Unavailable'}
                  </div>
                </div>
              );
            }

            const data = state.data;
            const isUnavailable = data.qualityState === 'UNAVAILABLE';
            const topSecurities = data.topOverlappingSecurities || data.overlappingSecurities || [];

            return (
              <div
                key={pairKey}
                className={`panel p-4 space-y-4 border-l-2 ${
                  isUnavailable
                    ? 'border-l-rose-500'
                    : data.qualityState === 'DATA_QUALITY_LIMITED'
                    ? 'border-l-amber-500'
                    : 'border-l-accent'
                }`}
              >
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <span className="mono-meta block text-accent font-semibold">PAIRWISE HOLDINGS OVERLAP</span>
                    <h4 className="text-[13.5px] font-semibold text-text-primary mt-0.5">
                      {fundA.schemeName} <span className="text-text-muted font-normal">vs</span> {fundB.schemeName}
                    </h4>
                  </div>
                  <ComparabilityBadge
                    status={isUnavailable ? 'INSUFFICIENT_DATA' : data.qualityState === 'DATA_QUALITY_LIMITED' ? 'COMPARABLE_WITH_LIMITATIONS' : 'COMPARABLE'}
                    reason={data.limitations?.join('; ')}
                  />
                </div>

                {/* Overlap Percentage Metric & Summary */}
                <div className="panel-inset p-3 bg-surface-raised rounded space-y-2">
                  <div className="flex items-baseline justify-between">
                    <div>
                      <span className="def-label block text-[10px]">Holdings Overlap Measure</span>
                      {isUnavailable ? (
                        <div className="text-rose-400 font-mono text-[14px] font-semibold mt-1">
                          Disclosure Unavailable
                        </div>
                      ) : (
                        <div className="flex items-baseline gap-1.5 mt-0.5">
                          <span className="text-2xl font-mono font-bold text-text-primary">
                            {data.formattedOverlapPercentage || `${data.overlapPercentage.toFixed(2)}%`}
                          </span>
                          <span className="mono-meta text-[11px]">overlap weight</span>
                        </div>
                      )}
                    </div>
                    {!isUnavailable && (
                      <div className="text-right">
                        <span className="def-label block text-[10px]">Common Holdings</span>
                        <span className="font-mono text-[14px] font-semibold text-text-primary mt-0.5 block">
                          {data.commonSecurityCount ?? data.commonSecuritiesCount ?? 0} positions
                        </span>
                      </div>
                    )}
                  </div>

                  {/* Disclosed Snapshot Dates */}
                  <div className="grid grid-cols-2 gap-2 text-[10.5px] font-mono text-text-secondary border-t border-border pt-2">
                    <div>
                      <span className="block text-text-muted text-[9.5px]">FUND A SNAPSHOT</span>
                      <span>{data.portfolioDateA || 'Not available'}</span>
                    </div>
                    <div>
                      <span className="block text-text-muted text-[9.5px]">FUND B SNAPSHOT</span>
                      <span>{data.portfolioDateB || 'Not available'}</span>
                    </div>
                  </div>
                </div>

                {/* Limitations Disclosures */}
                {data.limitations && data.limitations.length > 0 && (
                  <div className="p-2.5 rounded bg-amber-500/10 border border-amber-500/20 text-[11px] font-mono space-y-1">
                    <span className="text-amber-300 font-semibold block text-[10px] uppercase">
                      Comparability Limitations &amp; Disclosures
                    </span>
                    <ul className="list-disc list-inside space-y-0.5 text-amber-200">
                      {data.limitations.map((lim, idx) => (
                        <li key={idx}>{lim}</li>
                      ))}
                    </ul>
                  </div>
                )}

                {/* Overlap Interpretation & Weight Preservation */}
                <p className="text-[11.5px] leading-[1.5] text-text-secondary">
                  Measures the minimum aggregate weight of identical securities held by both funds:
                  <span className="font-mono text-[11px] text-text-primary ml-1">∑ min(w_A, w_B)</span>.
                  Disclosed weights are preserved exactly as reported by each fund and never normalized to force 100%.
                </p>

                {/* Expandable Top Overlapping Securities Table */}
                {!isUnavailable && topSecurities.length > 0 && (
                  <div className="space-y-2">
                    <button
                      onClick={() => toggleOverlapExpanded(pairKey)}
                      className="text-link text-[11px] inline-flex items-center gap-1 font-mono"
                    >
                      {isExpanded ? 'Hide' : 'View'} Top Overlapping Securities ({topSecurities.length})
                      <svg
                        className={`h-3 w-3 transition-transform ${isExpanded ? 'rotate-180' : ''}`}
                        fill="none"
                        viewBox="0 0 24 24"
                        stroke="currentColor"
                      >
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                      </svg>
                    </button>

                    {isExpanded && (
                      <div className="panel-inset overflow-x-auto p-2 bg-surface rounded">
                        <table className="w-full font-mono text-[11px]">
                          <thead>
                            <tr className="border-b border-border text-left text-text-muted text-[10px]">
                              <th className="pb-1">Security / ISIN</th>
                              <th className="pb-1 text-right">Weight in A</th>
                              <th className="pb-1 text-right">Weight in B</th>
                              <th className="pb-1 text-right text-accent">Overlap</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-border/60">
                            {topSecurities.map((sec, sIdx) => (
                              <tr key={sIdx} className="hover:bg-surface-raised">
                                <td className="py-1 pr-2">
                                  <span className="font-semibold text-text-primary block truncate max-w-[160px]">
                                    {sec.securityName}
                                  </span>
                                  {sec.isin && <span className="text-[9.5px] text-text-tertiary">{sec.isin}</span>}
                                </td>
                                <td className="py-1 text-right text-text-secondary">
                                  {sec.formattedWeightA || `${sec.fundAWeightPct ?? sec.weightInA}%`}
                                </td>
                                <td className="py-1 text-right text-text-secondary">
                                  {sec.formattedWeightB || `${sec.fundBWeightPct ?? sec.weightInB}%`}
                                </td>
                                <td className="py-1 text-right font-bold text-accent">
                                  {sec.formattedOverlapContribution || `${sec.overlapContributionPct ?? sec.overlapContribution}%`}
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    )}
                  </div>
                )}

                {/* Due Diligence Investigation Action */}
                <div className="pt-2 border-t border-border flex items-center justify-between">
                  <span className="mono-meta text-[10px]">Due Diligence Workflow</span>
                  <button
                    onClick={() =>
                      handleInvestigateFund(
                        fundA,
                        `Evaluate portfolio holdings overlap between ${fundA.schemeName} and ${fundB.schemeName} (${isUnavailable ? 'Holdings unavailable' : data.formattedOverlapPercentage + ' overlap'}) given reporting date differences and common positions.`,
                        `Portfolio Overlap Audit vs ${fundB.schemeName}`
                      )
                    }
                    className="btn btn-secondary btn-sm text-[10.5px] flex items-center gap-1"
                  >
                    <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z" />
                    </svg>
                    <span>Investigate Overlap</span>
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      </section>

      {/* Section 07: Within-Fund Holdings Drift & Sector Shifts */}
      <WithinFundDriftComparison
        funds={comparison.funds}
        knowledgeCutoff={comparison.period?.knowledgeCutoffTime || comparison.knowledgeCutoff}
        ordinal="07. Portfolio Drift"
      />

      {/* Section 08: Pairwise Return Co-movement & Correlation */}
      <section className="space-y-4">
        <SectionHeading
          ordinal="08. Return Co-movement"
          title="Pairwise Daily Return Correlation"
          description="Pearson correlation computed deterministically from synchronous daily NAV returns on intersecting trading dates."
        />

        <div className="grid gap-4 grid-cols-1 lg:grid-cols-2">
          {fundPairs.map(([fundA, fundB]) => {
            const pairKey = `${fundA.schemeOptionId}-${fundB.schemeOptionId}`;
            const state = correlationStates.get(pairKey);

            if (!state || state.loading) {
              return (
                <div key={pairKey} className="panel p-4 space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-[13px] text-text-primary">
                      {fundA.schemeName} vs {fundB.schemeName}
                    </span>
                    <span className="status-badge state-candidate text-[10px]">CALCULATING CORRELATION…</span>
                  </div>
                  <div className="h-16 animate-pulse bg-surface-raised rounded" />
                </div>
              );
            }

            if (state.error || !state.data) {
              return (
                <div key={pairKey} className="panel p-4 space-y-2 border-l-2 border-l-rose-500">
                  <span className="font-semibold text-[13px] text-text-primary">
                    {fundA.schemeName} vs {fundB.schemeName}
                  </span>
                  <div className="state-panel-error p-3 text-[11px] font-mono">
                    Correlation calculation failed: {state.error || 'Unavailable'}
                  </div>
                </div>
              );
            }

            const data = state.data;
            const isCalc = data.calculationStatus === 'CALCULATED';

            return (
              <div
                key={pairKey}
                className={`panel p-4 space-y-4 border-l-2 ${
                  !isCalc
                    ? 'border-l-rose-500'
                    : data.comparabilityStatus === 'COMPARABLE_WITH_LIMITATIONS'
                    ? 'border-l-amber-500'
                    : 'border-l-accent'
                }`}
              >
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <span className="mono-meta block text-accent font-semibold">PAIRWISE RETURN CORRELATION</span>
                    <h4 className="text-[13.5px] font-semibold text-text-primary mt-0.5">
                      {fundA.schemeName} <span className="text-text-muted font-normal">vs</span> {fundB.schemeName}
                    </h4>
                  </div>
                  <ComparabilityBadge
                    status={data.comparabilityStatus}
                    reason={data.limitations?.join('; ')}
                  />
                </div>

                {/* Correlation Metric Display */}
                <div className="panel-inset p-3 bg-surface-raised rounded space-y-2">
                  <div className="flex items-baseline justify-between">
                    <div>
                      <span className="def-label block text-[10px]">Pearson Correlation Coefficient (r)</span>
                      {isCalc && data.correlation !== null ? (
                        <div className="flex items-baseline gap-1.5 mt-0.5">
                          <span className="text-2xl font-mono font-bold text-text-primary">
                            {data.formattedCorrelation || (data.correlation >= 0 ? `+${data.correlation.toFixed(4)}` : data.correlation.toFixed(4))}
                          </span>
                          <span className="mono-meta text-[11px]">daily returns</span>
                        </div>
                      ) : (
                        <div className="text-rose-400 font-mono text-[14px] font-semibold mt-1">
                          Insufficient Data
                        </div>
                      )}
                    </div>
                    <div className="text-right">
                      <span className="def-label block text-[10px]">Paired Trading Days</span>
                      <span className="font-mono text-[14px] font-semibold text-text-primary mt-0.5 block">
                        N = {data.pairedObservationsCount}
                      </span>
                    </div>
                  </div>

                  {/* Common Observation Window */}
                  <div className="grid grid-cols-2 gap-2 text-[10.5px] font-mono text-text-secondary border-t border-border pt-2">
                    <div>
                      <span className="block text-text-muted text-[9.5px]">COMMON START DATE</span>
                      <span>{data.commonStartDate || 'Not available'}</span>
                    </div>
                    <div>
                      <span className="block text-text-muted text-[9.5px]">COMMON END DATE</span>
                      <span>{data.commonEndDate || 'Not available'}</span>
                    </div>
                  </div>
                </div>

                {/* Qualitative Interpretation */}
                <div className="text-[12px] leading-[1.55] text-text-secondary">
                  <span className="def-label block text-[10px] text-accent mb-0.5">EMPIRICAL INTERPRETATION</span>
                  {data.interpretation}
                </div>

                {/* Candidate Methodology Disclaimer */}
                <div className="p-2.5 rounded bg-surface border border-border text-[11px] font-mono space-y-1">
                  <div className="flex items-center gap-1.5 text-amber-300">
                    <span className="status-badge state-candidate text-[9.5px]">CANDIDATE METHODOLOGY</span>
                    <span className="text-[10px]">Not validated for production investment advice</span>
                  </div>
                  <p className="text-text-muted text-[10.5px]">
                    Pearson correlation reflects synchronous historical daily price co-movement only. High or low correlation does not imply diversification benefit or downside protection under market stress.
                  </p>
                </div>

                {/* Limitations */}
                {data.limitations && data.limitations.length > 0 && (
                  <ul className="list-disc list-inside text-[10.5px] font-mono text-amber-300 space-y-0.5">
                    {data.limitations.map((lim, idx) => (
                      <li key={idx}>{lim}</li>
                    ))}
                  </ul>
                )}

                {/* Due Diligence Investigation Action */}
                <div className="pt-2 border-t border-border flex items-center justify-between">
                  <span className="mono-meta text-[10px]">Due Diligence Workflow</span>
                  <button
                    onClick={() =>
                      handleInvestigateFund(
                        fundA,
                        `Analyze daily return correlation between ${fundA.schemeName} and ${fundB.schemeName} (r = ${data.formattedCorrelation || 'N/A'} over ${data.pairedObservationsCount} trading days) to determine whether market co-movement compromises portfolio diversification.`,
                        `Return Co-movement Audit vs ${fundB.schemeName}`
                      )
                    }
                    className="btn btn-secondary btn-sm text-[10.5px] flex items-center gap-1"
                  >
                    <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z" />
                    </svg>
                    <span>Investigate Co-movement</span>
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      </section>

      {/* Regulatory & Methodology Epistemic Notice */}
      <section className="panel state-candidate">
        <div className="panel-header">
          <h4 className="eyebrow">YUKIRA Comparability &amp; Epistemic Standards</h4>
        </div>
        <ul className="space-y-1.5 px-4 py-3 text-[12.5px] leading-[1.6] text-text-secondary">
          <li>All compared funds evaluated under identical knowledge cutoffs (2024-01-31T23:59:59+05:30) and analysis endpoints (2024-01-15).</li>
          <li>Historical performance does not predict future returns across changing economic regimes.</li>
          <li>YUKIRA does not generate investment recommendations, BUY/SELL/HOLD advice, composite star ratings, or performance rankings.</li>
          <li>Observation window length differences (e.g., PPFAS ELSS history starting July 2021) and partial holdings snapshots are explicitly flagged under comparability status.</li>
        </ul>
      </section>

      {investigationTarget && (
        <ResearchNoteModal
          isOpen={isNoteModalOpen}
          onClose={() => {
            setIsNoteModalOpen(false);
            setInvestigationTarget(null);
          }}
          fund={investigationTarget.fund}
          prefill={investigationTarget.prefill}
          onSave={async (schemeId, note, schemeOptionId) => {
            await saveResearchNote(schemeId, note, schemeOptionId, {
              schemeCode: investigationTarget.fund.schemeCode,
              schemeName: investigationTarget.fund.schemeName,
              amcName: investigationTarget.fund.amcName,
              optionType: investigationTarget.fund.optionType,
              planType: investigationTarget.fund.planType,
            });
            setIsNoteModalOpen(false);
            setInvestigationTarget(null);
          }}
        />
      )}
    </div>
  );
}

function MetricRow({
  metric,
  funds,
  onInvestigateFund,
}: {
  metric: ComparisonMetric;
  funds: ComparisonResponse["funds"];
  onInvestigateFund?: (fund: ComparisonFund, question: string, reason: string) => void;
}) {
  const [expanded, setExpanded] = useState(false);

  return (
    <>
      <tr className="transition-colors hover:bg-surface-raised">
        <td>
          <div className="space-y-0.5">
            <div className="text-[12.5px] font-semibold leading-[1.35] text-text-primary">
              {metric.metricName}
            </div>
            <div className="mono-meta flex items-center gap-2">
              <span className="font-bold text-accent">{metric.metricCode}</span>
              <span>•</span>
              <span className="uppercase text-[10px]">{metric.governanceStatus}</span>
            </div>
            <button
              onClick={() => setExpanded(!expanded)}
              className="text-link mt-1 inline-flex items-center gap-1 text-[11px]"
              aria-expanded={expanded}
            >
              {expanded ? "Hide" : "Show"} Details &amp; Methodology
              <svg
                className={`h-2.5 w-2.5 transition-transform ${expanded ? "rotate-180" : ""}`}
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                aria-hidden="true"
              >
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
              </svg>
            </button>
          </div>
        </td>

        {/* Overall Metric Comparability Badge */}
        <td>
          <ComparabilityBadge
            status={metric.overallComparabilityStatus}
            reason={metric.overallComparabilityReason}
          />
        </td>

        {/* Fund Result Cells */}
        {funds.map((fund) => {
          const result = metric.results.find((r) => r.schemeOptionId === fund.schemeOptionId);
          if (!result) {
            return (
              <td key={fund.schemeOptionId} className="data-unavailable">
                <span className="mono-meta">Not available</span>
              </td>
            );
          }

          if (result.calculationStatus !== "CALCULATED") {
            return (
              <td key={fund.schemeOptionId}>
                <span className="status-badge state-candidate text-[9.5px]">
                  {result.calculationStatus === "INSUFFICIENT_DATA" ? "Insufficient Data" : "Failed"}
                </span>
                {result.errorMessage && (
                  <div className="mono-meta mt-1.5 normal-case text-[10px] text-text-tertiary">{result.errorMessage}</div>
                )}
              </td>
            );
          }

          const displayValue = result.formattedValue && result.formattedValue.trim() !== ""
            ? result.formattedValue
            : result.numericValue !== null && result.numericValue !== undefined
            ? result.units === 'PERCENTAGE'
              ? `${(result.numericValue * 100).toFixed(2)}%`
              : result.units === 'BASIS_POINTS'
              ? `${result.numericValue.toFixed(0)} bps`
              : result.units === 'DAYS'
              ? `${result.numericValue.toFixed(0)} days`
              : result.numericValue.toFixed(2)
            : '—';

          return (
            <td key={fund.schemeOptionId}>
              <div className="data-value-md font-mono font-bold text-text-primary">{displayValue}</div>
              <div className="mono-meta mt-0.5 flex flex-wrap items-center gap-1.5">
                <span>{result.units}</span>
                {result.dataDate && (
                  <span className="text-[9.5px] text-text-tertiary font-mono">({result.dataDate})</span>
                )}
                {result.comparabilityStatus && result.comparabilityStatus !== 'COMPARABLE' && (
                  <span className="text-amber-400 font-bold" title={result.limitationReason || undefined}>
                    *
                  </span>
                )}
              </div>
            </td>
          );
        })}
      </tr>

      {expanded && (
        <tr className="bg-surface-inset">
          <td colSpan={funds.length + 2}>
            <div className="space-y-4 p-4">
              <div className="grid gap-4 sm:grid-cols-3">
                <div>
                  <span className="tab-ordinal block">01 Formula &amp; Specification</span>
                  <p className="mt-1 text-[12px] leading-[1.55] text-text-secondary font-mono">
                    {metric.description}
                  </p>
                  <p className="mono-meta mt-1 text-[11px]">
                    Period: <span className="text-text-primary">{metric.period || "Standard Horizon"}</span>
                  </p>
                </div>
                <div>
                  <span className="tab-ordinal block">02 Investor Interpretation</span>
                  <p className="mt-1 text-[12px] leading-[1.55] text-text-secondary">
                    {metric.interpretation}
                  </p>
                </div>
                <div className="state-candidate p-2.5 rounded bg-amber-500/10 border border-amber-500/20">
                  <span className="tab-ordinal block text-amber-300">03 Material Limitations</span>
                  <p className="mt-1 text-[12px] leading-[1.55] text-amber-200">
                    {metric.limitations}
                  </p>
                </div>
              </div>

              {metric.overallComparabilityReason && (
                <div className="panel-inset p-2.5 bg-surface font-mono text-[11px] text-text-secondary">
                  <span className="def-label text-accent block">Comparability Assessment:</span>
                  <span>{metric.overallComparabilityReason}</span>
                </div>
              )}

              {/* Fund-by-Fund Traceability & Investigation */}
              <div className="border-t border-border/60 pt-3">
                <span className="def-label text-[11px] text-accent block mb-2">
                  Fund Evidence Traceability &amp; Due Diligence Actions
                </span>
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                  {funds.map((fund) => {
                    const result = metric.results.find((r) => r.schemeOptionId === fund.schemeOptionId);
                    const compStatus = result?.comparabilityStatus || "COMPARABLE";
                    const hasLimit = compStatus !== "COMPARABLE";
                    const question = `For ${fund.schemeName}: How does ${metric.metricName} compare against peer dispersion given ${hasLimit ? result?.limitationReason || "differing observation periods" : "observed risk-adjusted spread"}?`;
                    const reason = `Comparative audit of ${metric.metricName} against peers`;

                    return (
                      <div
                        key={fund.schemeOptionId}
                        className="panel-inset p-3 bg-surface rounded flex flex-col justify-between space-y-2 border border-border/60"
                      >
                        <div>
                          <div className="flex items-center justify-between gap-1 mb-1">
                            <span className="text-[12px] font-semibold text-text-primary truncate">
                              {fund.schemeName}
                            </span>
                            <ComparabilityBadge status={compStatus} reason={result?.limitationReason} />
                          </div>
                          <div className="mono-meta text-[10.5px] space-y-0.5">
                            <div>Coverage: {result?.startDate && result?.endDate ? `${result.startDate} → ${result.endDate}` : "Common Analysis Period"}</div>
                            <div>Calc Status: <span className="font-semibold">{result?.calculationStatus || "CALCULATED"}</span></div>
                            {result?.limitationReason && (
                              <div className="text-amber-400 mt-1">{result.limitationReason}</div>
                            )}
                          </div>
                        </div>

                        <div className="pt-2 border-t border-border/40 flex items-center justify-between gap-2">
                          <Link
                            href={`/funds/${fund.schemeId || OPTION_TO_SCHEME_MAP[fund.schemeOptionId] || 1}?option=${fund.schemeOptionId}`}
                            className="text-link text-[11px] hover:underline"
                          >
                            Full Profile Evidence &rarr;
                          </Link>
                          {onInvestigateFund && (
                            <button
                              type="button"
                              onClick={() => onInvestigateFund(fund, question, reason)}
                              className="btn btn-secondary btn-sm text-[10.5px] flex items-center gap-1"
                              title={`Investigate ${metric.metricName} for ${fund.schemeName} in Research Workspace`}
                            >
                              <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z" />
                              </svg>
                              <span>Investigate</span>
                            </button>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>
          </td>
        </tr>
      )}
    </>
  );
}

function CompareContent() {
  const searchParams = useSearchParams();

  const [allSchemes, setAllSchemes] = useState<Scheme[]>([]);
  const [selectedSchemeIds, setSelectedSchemeIds] = useState<number[]>([]);
  const [schemeOptions, setSchemeOptions] = useState<Map<number, SchemeOption[]>>(new Map());
  const [selectedOptionIds, setSelectedOptionIds] = useState<Map<number, number>>(new Map());

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");

  const [asOfDate, setAsOfDate] = useState<string>("2024-01-15");
  const [knowledgeCutoff, setKnowledgeCutoff] = useState<string>("2024-01-31T23:59:59+05:30");

  const [comparison, setComparison] = useState<ComparisonResponse | null>(null);
  const [comparing, setComparing] = useState(false);
  const [compareError, setCompareError] = useState<string | null>(null);

  // Initialize schemes from API
  useEffect(() => {
    fetchAllSchemes()
      .then((data) => {
        setAllSchemes(data);
        setLoading(false);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : "Failed to load schemes");
        setLoading(false);
      });
  }, []);

  // Parse URL search params: supports ?ids=1,10262, ?funds=1,1720, and ?options=1:1,1720:10262
  useEffect(() => {
    const idsParam = searchParams.get("ids");
    const fundsParam = searchParams.get("funds");
    const optionsParam = searchParams.get("options");

    let initialSchemeIds: number[] = [];
    const initialOptionMap = new Map<number, number>();

    if (idsParam) {
      const parsedIds = idsParam.split(",").map(Number).filter((n) => !isNaN(n));
      parsedIds.forEach((rawId) => {
        // Raw id might be an option ID or a scheme ID
        const resolvedSchemeId = OPTION_TO_SCHEME_MAP[rawId] || rawId;
        const resolvedOptionId = OPTION_TO_SCHEME_MAP[rawId] ? rawId : SCHEME_TO_DEFAULT_OPTION_MAP[rawId] || rawId;

        if (!initialSchemeIds.includes(resolvedSchemeId)) {
          initialSchemeIds.push(resolvedSchemeId);
        }
        initialOptionMap.set(resolvedSchemeId, resolvedOptionId);
      });
    } else if (fundsParam) {
      const parsedFunds = fundsParam.split(",").map(Number).filter((n) => !isNaN(n));
      parsedFunds.forEach((rawFundId) => {
        const resolvedSchemeId = OPTION_TO_SCHEME_MAP[rawFundId] || rawFundId;
        const defaultOpt = OPTION_TO_SCHEME_MAP[rawFundId] ? rawFundId : SCHEME_TO_DEFAULT_OPTION_MAP[rawFundId];

        if (!initialSchemeIds.includes(resolvedSchemeId)) {
          initialSchemeIds.push(resolvedSchemeId);
        }
        if (defaultOpt) {
          initialOptionMap.set(resolvedSchemeId, defaultOpt);
        }
      });

      if (optionsParam) {
        optionsParam.split(",").forEach((part) => {
          const [sId, oId] = part.split(":").map(Number);
          if (!isNaN(sId) && !isNaN(oId)) {
            const actualSchemeId = OPTION_TO_SCHEME_MAP[sId] || sId;
            initialOptionMap.set(actualSchemeId, oId);
            if (!initialSchemeIds.includes(actualSchemeId)) {
              initialSchemeIds.push(actualSchemeId);
            }
          }
        });
      }
    } else {
      // Default to the 3 Canonical Pilot Schemes
      initialSchemeIds = [...CANONICAL_PILOT_SCHEME_IDS];
      initialOptionMap.set(1, 1);
      initialOptionMap.set(1720, 10262);
      initialOptionMap.set(1694, 10151);
    }

    queueMicrotask(() => {
      setSelectedSchemeIds(initialSchemeIds.slice(0, 3));
      setSelectedOptionIds(initialOptionMap);
    });
  }, [searchParams]);

  // Load options for selected schemes
  useEffect(() => {
    const unvisited = selectedSchemeIds.filter((id) => !schemeOptions.has(id));
    if (unvisited.length === 0) return;

    Promise.all(unvisited.map((schemeId) => fetchSchemeOptionsBySchemeId(schemeId)))
      .then((results) => {
        setSchemeOptions((prev) => {
          const next = new Map(prev);
          unvisited.forEach((id, idx) => next.set(id, results[idx]));
          return next;
        });

        setSelectedOptionIds((prev) => {
          const next = new Map(prev);
          unvisited.forEach((id, idx) => {
            const opts = results[idx];
            if (opts.length > 0 && !next.has(id)) {
              const defaultOpt = SCHEME_TO_DEFAULT_OPTION_MAP[id];
              const match = defaultOpt && opts.some((o) => o.id === defaultOpt) ? defaultOpt : opts[0].id;
              next.set(id, match);
            }
          });
          return next;
        });
      })
      .catch(() => {});
  }, [selectedSchemeIds, schemeOptions]);

  const executeComparisonAction = useCallback(async (
    optionIds: number[],
    targetAsOfDate: string = asOfDate,
    targetCutoff: string = knowledgeCutoff
  ) => {
    if (optionIds.length < 2) {
      setCompareError("Select at least 2 distinct mutual-fund options to compare.");
      return;
    }

    setComparing(true);
    setCompareError(null);

    try {
      const result = await executeComparison({
        schemeOptionIds: optionIds,
        asOfDate: targetAsOfDate,
        knowledgeCutoffTime: targetCutoff,
        metricCodes: [
          "RET-02", "RET-03", "RET-07",
          "RSK-01", "RSK-02", "RSK-03", "RSK-04", "RSK-05",
          "RAT-01", "RAT-04",
          "MKT-01", "MKT-02", "MKT-05",
          "REL-01", "REL-02",
          "CONC-01", "CONC-02", "CONC-03"
        ],
      });
      setComparison(result);
    } catch (err: unknown) {
      setCompareError(err instanceof Error ? err.message : "Comparison execution failed");
    } finally {
      setComparing(false);
    }
  }, [asOfDate, knowledgeCutoff]);

  const handleHorizonChange = (newAsOfDate: string, newCutoff: string) => {
    setAsOfDate(newAsOfDate);
    setKnowledgeCutoff(newCutoff);
    const optionIds = selectedSchemeIds
      .map((sId) => selectedOptionIds.get(sId))
      .filter((oId): oId is number => oId !== undefined);
    if (optionIds.length >= 2) {
      executeComparisonAction(optionIds, newAsOfDate, newCutoff);
    }
  };

  // Auto-execute comparison once selected options are populated (e.g. from URL or pilots)
  useEffect(() => {
    if (selectedSchemeIds.length >= 2 && selectedOptionIds.size >= selectedSchemeIds.length && !comparison && !comparing && !compareError) {
      const optionIds = selectedSchemeIds
        .map((sId) => selectedOptionIds.get(sId))
        .filter((oId): oId is number => oId !== undefined);
      if (optionIds.length >= 2) {
        queueMicrotask(() => {
          executeComparisonAction(optionIds, asOfDate, knowledgeCutoff);
        });
      }
    }
  }, [selectedSchemeIds, selectedOptionIds, comparison, comparing, compareError, executeComparisonAction, asOfDate, knowledgeCutoff]);

  const updateUrl = (schemeIds: number[], optionMap: Map<number, number>) => {
    const optIds = schemeIds
      .map((sId) => optionMap.get(sId))
      .filter((oId): oId is number => oId !== undefined);
    const newUrl = `/compare?ids=${optIds.join(",")}`;
    window.history.replaceState(null, "", newUrl);
  };

  const handleCompare = () => {
    const optionIds = selectedSchemeIds
      .map((sId) => selectedOptionIds.get(sId))
      .filter((oId): oId is number => oId !== undefined);

    updateUrl(selectedSchemeIds, selectedOptionIds);
    executeComparisonAction(optionIds, asOfDate, knowledgeCutoff);
  };

  const handleSwapOption = (schemeId: number, oldOptionId: number, newOptionId: number) => {
    const nextMap = new Map(selectedOptionIds);
    nextMap.set(schemeId, newOptionId);
    setSelectedOptionIds(nextMap);
    updateUrl(selectedSchemeIds, nextMap);

    const optionIds = selectedSchemeIds
      .map((sId) => nextMap.get(sId))
      .filter((oId): oId is number => oId !== undefined);
    executeComparisonAction(optionIds, asOfDate, knowledgeCutoff);
  };

  const handleRemoveOption = (optionIdToRemove: number) => {
    let schemeIdToRemove: number | null = null;
    selectedOptionIds.forEach((optId, sId) => {
      if (optId === optionIdToRemove) schemeIdToRemove = sId;
    });

    if (schemeIdToRemove !== null) {
      const nextSchemeIds = selectedSchemeIds.filter((id) => id !== schemeIdToRemove);
      const nextOptionMap = new Map(selectedOptionIds);
      nextOptionMap.delete(schemeIdToRemove);

      setSelectedSchemeIds(nextSchemeIds);
      setSelectedOptionIds(nextOptionMap);
      updateUrl(nextSchemeIds, nextOptionMap);

      if (nextSchemeIds.length >= 2) {
        const optionIds = nextSchemeIds
          .map((sId) => nextOptionMap.get(sId))
          .filter((oId): oId is number => oId !== undefined);
        executeComparisonAction(optionIds, asOfDate, knowledgeCutoff);
      } else {
        setComparison(null);
      }
    }
  };


  const filteredSchemes = useMemo(() => {
    const q = searchQuery.toLowerCase().trim();
    if (!q) return allSchemes;
    return allSchemes.filter(
      (s) =>
        s.name.toLowerCase().includes(q) ||
        s.code.toLowerCase().includes(q) ||
        (s.amc && s.amc.name && s.amc.name.toLowerCase().includes(q))
    );
  }, [allSchemes, searchQuery]);

  const toggleScheme = (schemeId: number) => {
    setSelectedSchemeIds((prev) => {
      if (prev.includes(schemeId)) {
        const updated = prev.filter((id) => id !== schemeId);
        setSelectedOptionIds((optMap) => {
          const newMap = new Map(optMap);
          newMap.delete(schemeId);
          return newMap;
        });
        return updated;
      }
      if (prev.length >= 3) return prev; // Limit to maximum 3 funds for institutional focus
      return [...prev, schemeId];
    });
  };

  const selectedSchemes = selectedSchemeIds
    .map((id) => allSchemes.find((s) => s.id === id))
    .filter((s): s is Scheme => s !== undefined);

  if (loading) {
    return <StateView kind="loading" title="Loading Mutual Funds" message="Retrieving verified mutual fund catalog..." />;
  }

  if (error) {
    return <StateView kind="unavailable" title="Catalog Unavailable" message={error} />;
  }

  return (
    <>
      <section className="panel state-candidate mb-8">
        <div className="panel-header">
          <h2 className="eyebrow">Institutional Multi-Fund Comparison Framework</h2>
        </div>
        <p className="max-w-[86ch] px-4 py-3 text-[12.5px] leading-[1.6] text-text-secondary">
          Compare two or three exact mutual-fund options using verified point-in-time observations across common-horizon performance, downside risk, benchmark sensitivity (vs NIFTY 500 TRI), portfolio concentration, pairwise holdings overlap, and synchronous return correlation. Evaluated under the strict YUKIRA Comparability Contract.
        </p>
      </section>

      {compareError && (
        <div className="state-panel-error mb-6">
          <strong className="eyebrow block">Comparison Error</strong>
          <span className="mt-1 block font-mono text-[12px]">{compareError}</span>
        </div>
      )}

      {comparing && !comparison && (
        <div className="panel p-8 text-center space-y-3 mb-8">
          <svg className="h-8 w-8 animate-spin text-accent mx-auto" fill="none" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
          </svg>
          <h3 className="text-h3 text-text-primary">Executing Quantitative Calculations…</h3>
          <p className="mono-meta">Evaluating point-in-time metrics across common observation horizons.</p>
        </div>
      )}

      {!comparison ? (
        <>
          <section className="mb-8">
            <div className="mb-4">
              <SectionHeading
                ordinal="Step 1"
                title="Select Mutual Funds (2–3)"
                description={`${selectedSchemeIds.length} of 3 slots occupied`}
              />
            </div>

            <div className="mb-4 max-w-md">
              <label htmlFor="compare-search" className="field-label">
                Search Catalog
              </label>
              <input
                id="compare-search"
                type="text"
                placeholder="Scheme name, code, or AMC…"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="field field-mono"
              />
            </div>

            <div className="scroll-region grid max-h-96 grid-cols-1 gap-px overflow-y-auto border border-border bg-border">
              {filteredSchemes.map((scheme) => {
                const isSelected = selectedSchemeIds.includes(scheme.id);
                const isDisabled = !isSelected && selectedSchemeIds.length >= 3;

                return (
                  <label
                    key={scheme.id}
                    className={`flex cursor-pointer items-start gap-3 bg-surface p-3 transition-colors ${
                      isSelected ? "bg-accent-muted" : isDisabled ? "opacity-50" : "hover:bg-surface-raised"
                    }`}
                  >
                    <input
                      type="checkbox"
                      checked={isSelected}
                      disabled={isDisabled}
                      onChange={() => !isDisabled && toggleScheme(scheme.id)}
                      className="mt-0.5 accent-[var(--accent)]"
                    />
                    <span className="min-w-0 flex-1">
                      <span className="mono-meta block">{scheme.code}</span>
                      <span className="mt-0.5 block text-[13px] font-semibold text-text-primary">
                        {scheme.name}
                      </span>
                      <span className="mono-meta mt-1 block">
                        {scheme.amc?.name || "Not available"} • Inception:{" "}
                        {scheme.inceptionDate || "Not available"}
                      </span>
                    </span>
                  </label>
                );
              })}
            </div>
          </section>

          {selectedSchemes.length > 0 && (
            <section className="mb-8">
              <div className="mb-4">
                <SectionHeading
                  ordinal="Step 2"
                  title={`Select Registered Share Classes (${selectedSchemes.length} funds)`}
                  description="Share class selection determines which registered option each metric is evaluated against."
                />
              </div>

              <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
                {selectedSchemes.map((scheme) => {
                  const options = schemeOptions.get(scheme.id) || [];
                  const selectedOption = selectedOptionIds.get(scheme.id);

                  return (
                    <div key={scheme.id} className="panel">
                      <div className="panel-header">
                        <h4 className="text-[13px] font-semibold text-text-primary">{scheme.name}</h4>
                        <span className="mono-meta shrink-0">{options.length} options</span>
                      </div>
                      {options.length === 0 ? (
                        <p className="mono-meta px-4 py-3 normal-case">Loading options…</p>
                      ) : (
                        <ul className="divide-y divide-border">
                          {options.map((opt) => (
                            <li key={opt.id}>
                              <label className="flex cursor-pointer items-center gap-2.5 px-4 py-2 transition-colors hover:bg-surface-raised">
                                <input
                                  type="radio"
                                  name={`option-${scheme.id}`}
                                  checked={selectedOption === opt.id}
                                  onChange={() =>
                                    setSelectedOptionIds(
                                      (prev) => new Map(prev).set(scheme.id, opt.id)
                                    )
                                  }
                                  className="accent-[var(--accent)]"
                                />
                                <span className="font-mono text-[12px] text-text-primary">
                                  {opt.plan?.planType || "DIRECT"} • {opt.optionType || "GROWTH"} •
                                  AMFI: {opt.amfiCode || "Not available"}
                                </span>
                              </label>
                            </li>
                          ))}
                        </ul>
                      )}
                    </div>
                  );
                })}
              </div>
            </section>
          )}

          <div className="flex items-center gap-3">
            <button
              onClick={handleCompare}
              disabled={comparing || selectedSchemeIds.length < 2 || selectedOptionIds.size < selectedSchemeIds.length}
              className="btn btn-primary"
            >
              {comparing ? (
                <>
                  <svg className="h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24" aria-hidden="true">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                  </svg>
                  <span>Calculating Quantitative Metrics…</span>
                </>
              ) : (
                <>
                  <span>Compare Selected Funds</span>
                  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                  </svg>
                </>
              )}
            </button>
            <div className="flex flex-wrap items-center gap-3">
              <span className="mono-meta">
                {selectedSchemeIds.length < 2
                  ? "Select at least 2 funds (max 3)"
                  : `Cutoff: ${asOfDate} · Knowledge: ${knowledgeCutoff}`}
              </span>
              <div className="flex items-center gap-1 font-mono text-[11px] bg-surface rounded border border-border p-1">
                <span className="text-text-muted px-1.5 uppercase font-bold text-[10px]">Horizon:</span>
                <button
                  type="button"
                  onClick={() => {
                    setAsOfDate("2024-01-15");
                    setKnowledgeCutoff("2024-01-31T23:59:59+05:30");
                  }}
                  className={`px-2 py-0.5 rounded transition-colors ${
                    asOfDate === "2024-01-15"
                      ? "bg-accent text-surface-dark font-bold shadow-sm"
                      : "text-text-secondary hover:text-text-primary"
                  }`}
                >
                  2024-01-15 (Pilot)
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setAsOfDate("2026-10-01");
                    setKnowledgeCutoff("2026-10-15T23:59:59+05:30");
                  }}
                  className={`px-2 py-0.5 rounded transition-colors ${
                    asOfDate === "2026-10-01"
                      ? "bg-accent text-surface-dark font-bold shadow-sm"
                      : "text-text-secondary hover:text-text-primary"
                  }`}
                >
                  2026-10-01 (Latest)
                </button>
              </div>
            </div>
          </div>
        </>
      ) : (
        <ComparisonView
          comparison={comparison}
          allSchemes={allSchemes}
          schemeOptions={schemeOptions}
          asOfDate={asOfDate}
          knowledgeCutoff={knowledgeCutoff}
          onReset={() => setComparison(null)}
          onSwapOption={handleSwapOption}
          onRemoveOption={handleRemoveOption}
          onHorizonChange={handleHorizonChange}
        />
      )}
    </>
  );
}

export default function ComparePage() {
  return (
    <PageContainer
      title="Multi-Fund Comparison"
      subtitle="Compare quantitative metrics, portfolio concentration, holdings overlap, and co-movement across mutual funds using verified point-in-time observations."
      breadcrumbs={[
        { label: "Home", href: "/" },
        { label: "Funds", href: "/funds" },
        { label: "Compare", href: "/compare" },
      ]}
    >
      <Suspense fallback={<StateView kind="loading" title="Loading..." message="Initializing comparison view..." />}>
        <CompareContent />
      </Suspense>
    </PageContainer>
  );
}
