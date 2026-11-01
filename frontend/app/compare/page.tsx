"use client";

import React, { useEffect, useState, useMemo, Suspense, useCallback } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import { PageContainer, SectionHeading } from "@/components/layout/PageContainer";
import { StateView } from "@/components/epistemic/StateView";
import {
  fetchAllSchemes,
  fetchSchemeOptionsBySchemeId,
  fetchUniverseScreener,
  UniverseScreenerResponse
} from "@/lib/api/schemes";
import { ComparativeScreener } from "@/components/comparison/ComparativeScreener";
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
import { EvidenceQualitySummary } from "@/components/epistemic/EvidenceQualitySummary";
import { ComparisonInterpretationSummary } from "@/components/comparison/ComparisonInterpretationSummary";
import { ComparisonEvidenceModal } from "@/components/comparison/ComparisonEvidenceModal";
import { ComparisonCompatibilityBanner } from "@/components/comparison/ComparisonCompatibilityBanner";
import { PairwiseHoldingsOverlapModal } from "@/components/comparison/PairwiseHoldingsOverlapModal";
import { fetchPairwiseHoldingsOverlap, PairwiseHoldingsOverlap } from "@/lib/api/research";

const CANONICAL_DIMENSIONS = [
  { code: 'RETURN_QUALITY', name: 'Return Quality', weight: '30%' },
  { code: 'RISK_QUALITY', name: 'Risk Quality', weight: '25%' },
  { code: 'BENCHMARK_RELATIVE_QUALITY', name: 'Benchmark-Relative Quality', weight: '25%' },
  { code: 'CONSISTENCY_DOWNSIDE_QUALITY', name: 'Consistency & Downside Quality', weight: '20%' },
];

export interface MetricGroupDef {
  id: string;
  categoryKey: CategoryFilter;
  title: string;
  ordinal: string;
  description: string;
  codes: string[];
}

export const CANONICAL_METRIC_GROUPS: MetricGroupDef[] = [
  {
    id: "return",
    categoryKey: "PERFORMANCE",
    title: "Return Characteristics & Capital Compounding",
    ordinal: "A",
    description: "Discrete boundary return (RET-02), 3-year compound annual growth rate (RET-03), and 3-year annualized active return vs benchmark (RET-07).",
    codes: ["RET-02", "RET-03", "RET-07"],
  },
  {
    id: "risk",
    categoryKey: "RISK",
    title: "Volatility & Downside Risk Profile",
    ordinal: "B",
    description: "3-year annualized return volatility (RSK-01), downside semideviation below zero (RSK-02), 3-year maximum peak-to-trough drawdown (RSK-03), maximum drawdown recovery duration (RSK-04), and Ulcer Index (RSK-05).",
    codes: ["RSK-01", "RSK-02", "RSK-03", "RSK-04", "RSK-05"],
  },
  {
    id: "risk-adjusted",
    categoryKey: "RISK",
    title: "Risk-Adjusted Efficiency & Capital Preservation",
    ordinal: "C",
    description: "Excess return per unit of total dispersion (Sharpe Ratio RAT-01) and active return per unit of benchmark tracking error (Information Ratio RAT-04).",
    codes: ["RAT-01", "RAT-04"],
  },
  {
    id: "benchmark",
    categoryKey: "BENCHMARK",
    title: "Benchmark Sensitivity & Systematic Market Alpha",
    ordinal: "D",
    description: "Systematic market beta (MKT-01), conditional downside market beta (MKT-02), capture spread (MKT-05), annualized tracking error (REL-01), and CAPM Jensen's alpha (REL-02) vs category benchmark.",
    codes: ["MKT-01", "MKT-02", "MKT-05", "REL-01", "REL-02"],
  },
  {
    id: "portfolio",
    categoryKey: "PORTFOLIO",
    title: "Portfolio Concentration & Construction Structure",
    ordinal: "E",
    description: "Top 5 equity holdings concentration (CONC-01), top 10 equity holdings concentration (CONC-02), and Herfindahl-Hirschman concentration index (CONC-03).",
    codes: ["CONC-01", "CONC-02", "CONC-03"],
  },
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
  onAddCandidate,
  onReplaceCandidate,
  onOpenScreener,
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
  onAddCandidate?: (schemeId: number) => void;
  onReplaceCandidate?: (oldSchemeId: number, newSchemeId: number) => void;
  onOpenScreener?: () => void;
}) {
  const [activeCategory, setActiveCategory] = useState<CategoryFilter>('ALL');
  const [showMasterMatrix, setShowMasterMatrix] = useState(false);
  const { watchlistItems, isWatchlisted, toggleWatchlist, saveResearchNote } = useWatchlist();
  const [investigationTarget, setInvestigationTarget] = useState<{
    fund: WatchlistResponse;
    prefill: {
      investigationQuestion: string;
      reasonSaved: string;
    };
  } | null>(null);
  const [isNoteModalOpen, setIsNoteModalOpen] = useState(false);

  // Evidence Explorer Modal State
  const [evidenceMetric, setEvidenceMetric] = useState<ComparisonMetric | null>(null);
  const [isEvidenceModalOpen, setIsEvidenceModalOpen] = useState(false);

  // Candidate Shortlist Slot Modification State
  const [replacingSlotIndex, setReplacingSlotIndex] = useState<number | null>(null);
  const [addingSlotActive, setAddingSlotActive] = useState(false);
  const [shortlistSaveNotice, setShortlistSaveNotice] = useState<string | null>(null);

  // Pairwise Overlap & Correlation States
  const [overlapStates, setOverlapStates] = useState<Map<string, OverlapPairState>>(new Map());
  const [activeOverlapData, setActiveOverlapData] = useState<PairwiseHoldingsOverlap | null>(null);
  const [isOverlapModalOpen, setIsOverlapModalOpen] = useState(false);
  const [overlapModalLoading, setOverlapModalLoading] = useState(false);

  const handleOpenDetailedOverlap = async (optIdA: number, optIdB: number) => {
    setIsOverlapModalOpen(true);
    setOverlapModalLoading(true);
    try {
      const data = await fetchPairwiseHoldingsOverlap(optIdA, optIdB);
      setActiveOverlapData(data);
    } catch {
      setActiveOverlapData(null);
    } finally {
      setOverlapModalLoading(false);
    }
  };
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

  const renderMetricGroupTable = (groupDef: MetricGroupDef) => {
    const groupMetrics = comparison.metrics?.filter((m) => groupDef.codes.includes(m.metricCode)) || [];
    if (groupMetrics.length === 0) return null;

    return (
      <div className="panel scroll-region">
        <div className="scroll-region overflow-x-auto">
          <table className="data-table w-full">
            <thead>
              <tr className="bg-surface-raised/80 border-b border-border">
                <th className="w-1/3 text-left py-2.5 px-3">
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-[10px] font-bold text-accent px-1.5 py-0.5 rounded bg-accent/10 border border-accent/20">
                      GROUP {groupDef.ordinal}
                    </span>
                    <span className="font-semibold text-text-primary text-[12.5px]">
                      {groupDef.title}
                    </span>
                  </div>
                </th>
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
              {groupMetrics.map((metric) => (
                <MetricRow
                  key={metric.metricCode}
                  metric={metric}
                  funds={comparison.funds}
                  onInvestigateFund={handleInvestigateFund}
                  onOpenEvidenceModal={(m) => {
                    setEvidenceMetric(m);
                    setIsEvidenceModalOpen(true);
                  }}
                  runId={comparison.runId || 1}
                />
              ))}
            </tbody>
          </table>
        </div>
      </div>
    );
  };

  return (
    <div className="space-y-10">
      {/* 6 Institutional Comparison Dimensions Quick-Jump Navigation Bar */}
      <nav aria-label="Comparison Sections" className="flex flex-wrap items-center gap-1.5 p-2 bg-surface-raised/90 backdrop-blur rounded-lg border border-border text-[11px] font-mono sticky top-0 z-30 shadow-sm">
        <span className="text-text-muted px-1.5 font-bold uppercase text-[10px]">Navigate:</span>
        <a href="#section-overview" className="px-2.5 py-1 rounded bg-surface hover:bg-surface-raised border border-border text-text-secondary hover:text-text-primary transition-colors">
          A. Overview &amp; Compatibility
        </a>
        <a href="#section-performance" className="px-2.5 py-1 rounded bg-surface hover:bg-surface-raised border border-border text-text-secondary hover:text-text-primary transition-colors">
          B. Performance
        </a>
        <a href="#section-risk" className="px-2.5 py-1 rounded bg-surface hover:bg-surface-raised border border-border text-text-secondary hover:text-text-primary transition-colors">
          C. Risk &amp; Downside
        </a>
        <a href="#section-portfolio" className="px-2.5 py-1 rounded bg-surface hover:bg-surface-raised border border-border text-text-secondary hover:text-text-primary transition-colors">
          D. Portfolio Construction
        </a>
        <a href="#section-quality" className="px-2.5 py-1 rounded bg-surface hover:bg-surface-raised border border-border text-text-secondary hover:text-text-primary transition-colors">
          E. Evidence Quality
        </a>
        <a href="#section-investigation" className="px-2.5 py-1 rounded bg-accent/15 border border-accent/30 text-accent font-semibold hover:bg-accent/25 transition-colors">
          F. Investigation &amp; Findings
        </a>
      </nav>

      {/* =========================================================================
          SECTION A: COMPARISON OVERVIEW, ELIGIBILITY & COMPATIBILITY
         ========================================================================= */}
      <section id="section-overview" className="space-y-6">
        {/* Top Control Banner */}
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

        {/* Institutional Comparability Assessment Banner */}
        <ComparisonCompatibilityBanner
          compatibilitySummary={comparison.compatibility}
          commonSupportedPeriod={comparison.commonPeriod}
        />

        {/* Selected Scheme Identity & Profile Cards */}
        <div className="space-y-3">
          <SectionHeading
            ordinal="A. Overview"
            title="Scheme Identity, Category & Benchmark Registration"
            description="Verified share class registration metadata, SEBI category classification, primary benchmark mapping, and point-in-time analytical quality scores."
          />

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
              const availableOptions = schemeOptions.get(targetSchemeId) || [];

              return (
                <div key={fund.schemeOptionId} className="panel flex flex-col justify-between p-4 space-y-4 border-l-2 border-l-accent shadow-sm">
                  <div className="space-y-3">
                    <div className="flex items-start justify-between gap-2">
                      <div className="space-y-0.5">
                        <span className="mono-meta block font-bold text-accent">{fund.amcName || 'AMC Identity Verified'}</span>
                        <h4 className="text-[14px] font-semibold text-text-primary leading-tight">
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

                    {/* Category & Subcategory Badge */}
                    {(fund.category || fund.subcategory) && (
                      <div className="flex flex-wrap items-center gap-1.5 text-[10.5px] font-mono text-text-secondary">
                        <span className="px-1.5 py-0.5 rounded bg-surface border border-border text-accent font-semibold">
                          {fund.category || 'Equity'}
                        </span>
                        {fund.subcategory && <span>• {fund.subcategory}</span>}
                      </div>
                    )}

                    {/* Registration Badges */}
                    <div className="mono-meta flex flex-wrap gap-1.5 items-center">
                      <span className="badge badge-outline">{fund.planType || 'DIRECT'}</span>
                      <span className="badge badge-outline">{fund.optionType || 'GROWTH'}</span>
                      <span>AMFI: {fund.amfiCode || 'N/A'}</span>
                      {fund.isin && <span className="text-text-tertiary font-mono">• {fund.isin}</span>}
                    </div>

                    {/* Benchmark & Inception Registration Panel */}
                    <div className="panel-inset p-2.5 bg-surface rounded text-[11px] font-mono space-y-1 border border-border/60">
                      <div className="flex items-center justify-between text-text-secondary">
                        <span className="text-text-muted text-[10px] uppercase">Primary Benchmark:</span>
                        <span className="text-text-primary font-semibold truncate max-w-[170px]" title={fund.primaryBenchmarkName || 'NIFTY 500 TRI'}>
                          {fund.primaryBenchmarkName || 'NIFTY 500'} ({fund.primaryBenchmarkReturnVariant || 'TRI'})
                        </span>
                      </div>
                      {fund.inceptionDate && (
                        <div className="flex items-center justify-between text-text-secondary">
                          <span className="text-text-muted text-[10px] uppercase">Inception Date:</span>
                          <span className="text-text-primary">{fund.inceptionDate}</span>
                        </div>
                      )}
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

                    {/* Scorecard Panel */}
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

                  <div className="space-y-1.5 mt-2">
                    <Link
                      href={scorecardHref}
                      className="btn btn-secondary btn-sm w-full justify-center text-center"
                    >
                      <span>View Detailed Analytical Profile</span>
                      <svg className="h-3.5 w-3.5 ml-1" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
                      </svg>
                    </Link>

                    <div className="flex items-center gap-1.5">
                      <button
                        type="button"
                        onClick={() => {
                          toggleWatchlist(targetSchemeId, fund.schemeOptionId, {
                            schemeName: fund.schemeName,
                            amcName: fund.amcName,
                            optionType: fund.optionType,
                            planType: fund.planType,
                            amfiCode: fund.amfiCode,
                            isin: fund.isin,
                          });
                        }}
                        className={`btn btn-sm flex-1 text-[11px] font-mono justify-center ${
                          isWatchlisted(targetSchemeId, fund.schemeOptionId)
                            ? "btn-accent"
                            : "btn-secondary"
                        }`}
                        title={
                          isWatchlisted(targetSchemeId, fund.schemeOptionId)
                            ? "Saved in Research Workspace (Click to remove)"
                            : "Save to Research Workspace"
                        }
                      >
                        <svg
                          className="h-3.5 w-3.5 mr-1"
                          fill={isWatchlisted(targetSchemeId, fund.schemeOptionId) ? "currentColor" : "none"}
                          viewBox="0 0 24 24"
                          stroke="currentColor"
                        >
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            strokeWidth={isWatchlisted(targetSchemeId, fund.schemeOptionId) ? 1 : 2}
                            d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z"
                          />
                        </svg>
                        <span>
                          {isWatchlisted(targetSchemeId, fund.schemeOptionId)
                            ? "Saved in Workspace"
                            : "Save to Workspace"}
                        </span>
                      </button>

                      {isWatchlisted(targetSchemeId, fund.schemeOptionId) && (
                        <Link
                          href="/watchlist"
                          className="btn btn-ghost btn-sm text-[11px] text-accent hover:underline px-2"
                          title="View fund research in Workspace"
                        >
                          Workspace →
                        </Link>
                      )}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Candidate Shortlist Slot Control Bar */}
        <section className="panel p-5 bg-surface space-y-4 border-l-4 border-l-accent shadow-sm">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border pb-3">
            <div>
              <div className="flex items-center gap-2">
                <span className="mono-meta text-accent font-bold uppercase tracking-wider text-[11px]">
                  Research Shortlist Control Bar
                </span>
                <span className="px-1.5 py-0.5 rounded bg-accent/10 border border-accent/20 text-accent font-mono text-[10px] font-semibold">
                  {comparison.funds.length} of 3 Slots Active
                </span>
              </div>
              <h3 className="text-h3 text-text-primary mt-0.5">
                Multi-Candidate Investigation Shortlist
              </h3>
              <p className="mono-meta text-[11px]">
                Preserves exact share-class registration, plan type, and option type across side-by-side analysis.
              </p>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              <button
                type="button"
                onClick={() => {
                  let newSaved = 0;
                  let existingPreserved = 0;
                  comparison.funds.forEach((fund) => {
                    const targetSchemeId = fund.schemeId || OPTION_TO_SCHEME_MAP[fund.schemeOptionId] || 1;
                    if (!isWatchlisted(targetSchemeId, fund.schemeOptionId)) {
                      toggleWatchlist(targetSchemeId, fund.schemeOptionId, {
                        schemeName: fund.schemeName,
                        amcName: fund.amcName,
                        optionType: fund.optionType,
                        planType: fund.planType,
                        amfiCode: fund.amfiCode,
                        isin: fund.isin,
                        reasonSaved: `Comparative audit shortlist as of ${asOfDate}`,
                        investigationQuestion: `Comparative diligence: evaluate ${fund.schemeName} spread vs peer shortlist candidates.`,
                      });
                      newSaved++;
                    } else {
                      existingPreserved++;
                    }
                  });
                  setShortlistSaveNotice(
                    newSaved > 0
                      ? `Saved ${newSaved} candidate(s) to Research Workspace (${existingPreserved} existing fund notes preserved).`
                      : `All ${existingPreserved} candidate(s) already saved in Research Workspace (existing notes preserved).`
                  );
                }}
                className="btn btn-secondary btn-sm font-mono text-xs flex items-center gap-1.5"
                title="Save all compared candidates into your Research Workspace"
              >
                <svg className="w-3.5 h-3.5 text-accent" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z" />
                </svg>
                <span>Save Shortlist to Workspace</span>
              </button>

              {onOpenScreener && (
                <button
                  type="button"
                  onClick={onOpenScreener}
                  className="btn btn-ghost btn-sm font-mono text-xs text-text-secondary hover:text-text-primary"
                >
                  + Screener
                </button>
              )}
            </div>
          </div>

          {/* Shortlist Save Notice Banner */}
          {shortlistSaveNotice && (
            <div className="p-3 bg-emerald-500/10 border border-emerald-500/30 rounded flex items-center justify-between text-xs text-emerald-400 font-mono">
              <span className="flex items-center gap-1.5">
                <span>✓</span>
                <span>{shortlistSaveNotice}</span>
              </span>
              <Link
                href="/watchlist?tab=COMPARATIVE"
                className="underline font-bold text-accent hover:text-text-primary ml-2 shrink-0"
              >
                View in Workspace →
              </Link>
            </div>
          )}

          {/* 3 Candidate Slots */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {comparison.funds.map((fund, idx) => {
              const scheme = allSchemes.find(
                (s) => (fund.schemeId ? s.id === fund.schemeId : false) || (fund.schemeCode ? s.code === fund.schemeCode : false)
              );
              const targetSchemeId = scheme?.id || fund.schemeId || OPTION_TO_SCHEME_MAP[fund.schemeOptionId] || 1;
              const availableOptions = schemeOptions.get(targetSchemeId) || [];
              const isReplacing = replacingSlotIndex === idx;

              return (
                <div
                  key={fund.schemeOptionId}
                  className="panel-inset p-3.5 bg-surface-raised rounded-lg space-y-2.5 border border-border flex flex-col justify-between"
                >
                  <div className="space-y-1.5">
                    <div className="flex items-center justify-between gap-1">
                      <span className="mono-meta text-[10px] text-accent font-bold uppercase tracking-wider">
                        Slot 0{idx + 1} • Candidate
                      </span>
                      {comparison.funds.length > 2 && (
                        <button
                          onClick={() => onRemoveOption(fund.schemeOptionId)}
                          className="text-text-muted hover:text-rose-400 p-0.5 text-xs font-mono"
                          title="Remove candidate from comparison"
                          aria-label={`Remove ${fund.schemeName}`}
                        >
                          ✕ Remove
                        </button>
                      )}
                    </div>

                    <h4 className="text-[13px] font-semibold text-text-primary leading-tight">
                      {fund.schemeName}
                    </h4>
                    <div className="mono-meta text-[10.5px] flex flex-wrap items-center gap-1.5">
                      <span className="badge badge-outline text-[9px]">{fund.planType || 'DIRECT'}</span>
                      <span className="badge badge-outline text-[9px]">{fund.optionType || 'GROWTH'}</span>
                      <span>AMFI: {fund.amfiCode || 'N/A'}</span>
                      {fund.isin && <span className="text-text-tertiary">• {fund.isin}</span>}
                    </div>
                  </div>

                  <div className="space-y-2 pt-2 border-t border-border/40 font-mono text-[11px]">
                    {/* Share Class Option Swap */}
                    {availableOptions.length > 1 && (
                      <div>
                        <label htmlFor={`swap-slot-${fund.schemeOptionId}`} className="block text-[9.5px] text-text-muted uppercase mb-0.5">
                          Share Class Option:
                        </label>
                        <select
                          id={`swap-slot-${fund.schemeOptionId}`}
                          value={fund.schemeOptionId}
                          onChange={(e) => onSwapOption(targetSchemeId, fund.schemeOptionId, Number(e.target.value))}
                          className="field field-mono text-[10.5px] py-0.5 px-1.5 w-full bg-surface"
                        >
                          {availableOptions.map((opt) => (
                            <option key={opt.id} value={opt.id}>
                              {opt.plan?.planType || 'DIRECT'} • {opt.optionType || 'GROWTH'} (AMFI: {opt.amfiCode || 'N/A'})
                            </option>
                          ))}
                        </select>
                      </div>
                    )}

                    {/* Replace Fund Action */}
                    {isReplacing ? (
                      <div className="p-2 bg-surface rounded border border-accent/40 space-y-1.5">
                        <span className="text-[10px] text-accent block font-bold">Replace with Scheme:</span>
                        <select
                          onChange={(e) => {
                            const newId = Number(e.target.value);
                            if (newId && onReplaceCandidate) {
                              onReplaceCandidate(targetSchemeId, newId);
                              setReplacingSlotIndex(null);
                            }
                          }}
                          defaultValue=""
                          className="field field-mono text-[10.5px] py-1 px-1.5 w-full bg-surface-raised"
                        >
                          <option value="" disabled>Select alternative scheme…</option>
                          {watchlistItems.some((w) => !comparison.funds.some((f) => f.schemeId === w.schemeId)) && (
                            <optgroup label="Saved in Research Workspace">
                              {watchlistItems
                                .filter((w) => !comparison.funds.some((f) => f.schemeId === w.schemeId))
                                .map((w) => (
                                  <option key={`replace-saved-${w.schemeId}`} value={w.schemeId}>
                                    ★ {w.schemeName} ({w.planType || 'DIRECT'} {w.optionType || 'GROWTH'})
                                  </option>
                                ))}
                            </optgroup>
                          )}
                          <optgroup label="All Catalog Schemes">
                            {allSchemes
                              .filter((s) => !comparison.funds.some((f) => f.schemeId === s.id))
                              .map((s) => (
                                <option key={s.id} value={s.id}>
                                  {s.name} ({s.code})
                                </option>
                              ))}
                          </optgroup>
                        </select>
                        <button
                          type="button"
                          onClick={() => setReplacingSlotIndex(null)}
                          className="text-[10px] text-text-muted hover:text-text-primary block"
                        >
                          Cancel
                        </button>
                      </div>
                    ) : (
                      <div className="flex items-center justify-between pt-0.5">
                        <button
                          type="button"
                          onClick={() => setReplacingSlotIndex(idx)}
                          className="text-link text-[10.5px] hover:underline"
                        >
                          ⇄ Replace Candidate
                        </button>
                        <Link
                          href={`/funds/${targetSchemeId}?option=${fund.schemeOptionId}`}
                          className="text-text-secondary hover:text-text-primary text-[10.5px]"
                        >
                          Profile →
                        </Link>
                      </div>
                    )}
                  </div>
                </div>
              );
            })}

            {/* Slot 3: Available to Add when exactly 2 funds are active */}
            {comparison.funds.length === 2 && (
              <div className="panel-inset p-3.5 bg-surface-raised/50 rounded-lg border border-dashed border-border flex flex-col justify-between items-center text-center space-y-3">
                <div className="space-y-1">
                  <span className="mono-meta text-[10px] text-text-muted font-bold uppercase tracking-wider block">
                    Slot 03 • Available Slot
                  </span>
                  <h4 className="text-[13px] font-semibold text-text-primary">
                    + Add 3rd Candidate to Shortlist
                  </h4>
                  <p className="mono-meta text-[10.5px] max-w-[220px]">
                    Institutional focus supports up to 3 candidates for head-to-head evidence comparison.
                  </p>
                </div>

                {addingSlotActive ? (
                  <div className="w-full space-y-1.5 text-left font-mono">
                    <select
                      onChange={(e) => {
                        const newId = Number(e.target.value);
                        if (newId && onAddCandidate) {
                          onAddCandidate(newId);
                          setAddingSlotActive(false);
                        }
                      }}
                      defaultValue=""
                      className="field field-mono text-[10.5px] py-1 px-1.5 w-full bg-surface"
                    >
                      <option value="" disabled>Select 3rd scheme…</option>
                      {watchlistItems.some((w) => !comparison.funds.some((f) => f.schemeId === w.schemeId)) && (
                        <optgroup label="Saved in Research Workspace">
                          {watchlistItems
                            .filter((w) => !comparison.funds.some((f) => f.schemeId === w.schemeId))
                            .map((w) => (
                              <option key={`add-saved-${w.schemeId}`} value={w.schemeId}>
                                ★ {w.schemeName} ({w.planType || 'DIRECT'} {w.optionType || 'GROWTH'})
                              </option>
                            ))}
                        </optgroup>
                      )}
                      <optgroup label="All Catalog Schemes">
                        {allSchemes
                          .filter((s) => !comparison.funds.some((f) => f.schemeId === s.id))
                          .map((s) => (
                            <option key={s.id} value={s.id}>
                              {s.name} ({s.code})
                            </option>
                          ))}
                      </optgroup>
                    </select>
                    <div className="flex justify-between text-[10px]">
                      <button
                        type="button"
                        onClick={() => setAddingSlotActive(false)}
                        className="text-text-muted hover:text-text-primary"
                      >
                        Cancel
                      </button>
                      {onOpenScreener && (
                        <button
                          type="button"
                          onClick={onOpenScreener}
                          className="text-accent hover:underline"
                        >
                          Browse Screener →
                        </button>
                      )}
                    </div>
                  </div>
                ) : (
                  <button
                    type="button"
                    onClick={() => setAddingSlotActive(true)}
                    className="btn btn-secondary btn-sm font-mono text-xs w-full justify-center"
                  >
                    <span>Select 3rd Candidate</span>
                  </button>
                )}
              </div>
            )}
          </div>
        </section>
      </section>

      {/* =========================================================================
          SECTION B: PERFORMANCE COMPARISON
         ========================================================================= */}
      <section id="section-performance" className="space-y-6">
        <SectionHeading
          ordinal="B. Performance"
          title="Relative Compounding & Benchmark Co-movement"
          description="Cumulative growth trajectories, benchmark-relative active return, and annualized return metrics evaluated over aligned observation windows."
        />

        {/* Cumulative Growth & Return Divergence */}
        <CumulativeGrowthComparison
          funds={comparison.funds}
          asOfDate={asOfDate}
          knowledgeCutoff={knowledgeCutoff}
        />

        {/* Primary Benchmark-Relative Analysis (NIFTY 500 TRI) */}
        <BenchmarkRelativeComparison
          comparison={comparison}
          asOfDate={asOfDate}
          knowledgeCutoff={knowledgeCutoff}
        />

        {/* Group A: Return Characteristics & Capital Compounding */}
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <span className="mono-meta text-[11px] font-bold text-accent uppercase">
              Standardized Return Metric Matrix
            </span>
            <span className="text-[10.5px] font-mono text-text-muted">
              Discrete boundary (RET-02), 3Y CAGR (RET-03), and 3Y Active Return (RET-07)
            </span>
          </div>
          {renderMetricGroupTable(CANONICAL_METRIC_GROUPS[0])}
        </div>
      </section>

      {/* =========================================================================
          SECTION C: RISK AND DOWNSIDE COMPARISON
         ========================================================================= */}
      <section id="section-risk" className="space-y-6">
        <SectionHeading
          ordinal="C. Risk & Downside"
          title="Dispersion, Tail Risk & Capital Preservation"
          description="Annualized volatility, downside semideviation, maximum drawdown, recovery duration, and systematic benchmark sensitivity."
        />

        {/* Group B: Volatility & Downside Risk Profile */}
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <span className="mono-meta text-[11px] font-bold text-accent uppercase">
              Tail Risk & Downside Semideviation
            </span>
            <span className="text-[10.5px] font-mono text-text-muted">
              Preserves exact units (percentage, days, basis points) and sign conventions
            </span>
          </div>
          {renderMetricGroupTable(CANONICAL_METRIC_GROUPS[1])}
        </div>

        {/* Group C: Risk-Adjusted Efficiency & Capital Preservation */}
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <span className="mono-meta text-[11px] font-bold text-accent uppercase">
              Risk-Adjusted Efficiency Ratios
            </span>
            <span className="text-[10.5px] font-mono text-text-muted">
              Sharpe Ratio (RAT-01) and Information Ratio (RAT-04) vs benchmark
            </span>
          </div>
          {renderMetricGroupTable(CANONICAL_METRIC_GROUPS[2])}
        </div>

        {/* Group D: Benchmark Sensitivity & Systematic Market Alpha */}
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <span className="mono-meta text-[11px] font-bold text-accent uppercase">
              Systematic Market Beta & Capture Spread
            </span>
            <span className="text-[10.5px] font-mono text-text-muted">
              Evaluated against canonical category benchmark (NIFTY 500 TRI)
            </span>
          </div>
          {renderMetricGroupTable(CANONICAL_METRIC_GROUPS[3])}
        </div>

        {/* Epistemic Risk Disclaimer */}
        <div className="p-3 bg-surface border border-border rounded-lg text-xs font-mono text-text-secondary flex items-start gap-2.5">
          <span className="text-accent text-sm">ℹ</span>
          <p className="leading-relaxed">
            <strong>Risk Evaluation Standard:</strong> Risk metrics represent empirical historical dispersion. They are never summed into composite risk grades or optimization signals. Missing observations or truncated historical regimes are explicitly flagged rather than filled with synthetic proxies.
          </p>
        </div>
      </section>

      {/* =========================================================================
          SECTION D: PORTFOLIO CONSTRUCTION & LOOK-THROUGH
         ========================================================================= */}
      <section id="section-portfolio" className="space-y-6">
        <SectionHeading
          ordinal="D. Portfolio Construction"
          title="Concentration, Look-Through Overlap & Style Drift"
          description="Security-level overlap, concentration indices, within-fund holdings shifts, and return co-movement."
        />

        {/* Group E: Portfolio Concentration Metrics */}
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <span className="mono-meta text-[11px] font-bold text-accent uppercase">
              Holdings Concentration Structure
            </span>
            <span className="text-[10.5px] font-mono text-text-muted">
              Top 5 (CONC-01), Top 10 (CONC-02), and Herfindahl-Hirschman Index (CONC-03)
            </span>
          </div>
          {renderMetricGroupTable(CANONICAL_METRIC_GROUPS[4])}
        </div>

        {/* Pairwise Portfolio Holdings Overlap */}
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <span className="mono-meta text-[11px] font-bold text-accent uppercase">
              Security-Level Overlap &amp; Shared Holdings
            </span>
            <span className="text-[10.5px] font-mono text-text-muted">
              Computed deterministically from source-disclosed monthly portfolios
            </span>
          </div>

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
                    <span className={`status-badge ${
                      data.qualityState === 'COMPLETE'
                        ? 'state-approved'
                        : data.qualityState === 'DATA_QUALITY_LIMITED'
                        ? 'state-candidate'
                        : 'border-rose-500/30 text-rose-400 bg-rose-500/10'
                    }`}>
                      {data.qualityState}
                    </span>
                  </div>

                  <div className="panel-inset p-3 bg-surface-raised rounded space-y-2">
                    <div className="flex items-baseline justify-between">
                      <div>
                        <span className="def-label block text-[10px]">Active Holdings Overlap</span>
                        {data.overlapPercentage !== null && data.overlapPercentage !== undefined ? (
                          <div className="flex items-baseline gap-1.5 mt-0.5">
                            <span className="text-2xl font-mono font-bold text-text-primary">
                              {data.overlapPercentage.toFixed(2)}%
                            </span>
                            <span className="mono-meta text-[11px]">portfolio weight</span>
                          </div>
                        ) : (
                          <div className="text-rose-400 font-mono text-[14px] font-semibold mt-1">
                            Unavailable
                          </div>
                        )}
                      </div>
                      <div className="text-right">
                        <span className="def-label block text-[10px]">Intersecting Holdings</span>
                        <span className="font-mono text-[14px] font-semibold text-text-primary mt-0.5 block">
                          {data.overlappingSecuritiesCount} securities
                        </span>
                      </div>
                    </div>

                    <div className="grid grid-cols-2 gap-2 text-[10.5px] font-mono text-text-secondary border-t border-border pt-2">
                      <div>
                        <span className="block text-text-muted text-[9.5px]">DISCLOSURE A DATE</span>
                        <span>{data.portfolioDateA || 'Not available'}</span>
                      </div>
                      <div>
                        <span className="block text-text-muted text-[9.5px]">DISCLOSURE B DATE</span>
                        <span>{data.portfolioDateB || 'Not available'}</span>
                      </div>
                    </div>
                  </div>

                  <div className="text-[12px] leading-[1.55] text-text-secondary">
                    <span className="def-label block text-[10px] text-accent mb-0.5">EMPIRICAL INTERPRETATION</span>
                    {data.interpretation}
                  </div>

                  {data.limitations && data.limitations.length > 0 && (
                    <ul className="list-disc list-inside text-[10.5px] font-mono text-amber-300 space-y-0.5">
                      {data.limitations.map((lim, lIdx) => (
                        <li key={lIdx}>{lim}</li>
                      ))}
                    </ul>
                  )}

                  <div className="flex items-center flex-wrap gap-4">
                    {topSecurities.length > 0 && (
                      <button
                        type="button"
                        onClick={() => toggleOverlapExpanded(pairKey)}
                        className="text-link text-[11px] font-mono flex items-center gap-1 hover:underline"
                      >
                        <span>{isExpanded ? 'Hide' : 'Inspect'} Top Shared Securities ({topSecurities.length})</span>
                        <span>{isExpanded ? '▲' : '▼'}</span>
                      </button>
                    )}
                    <button
                      type="button"
                      onClick={() => handleOpenDetailedOverlap(fundA.schemeOptionId, fundB.schemeOptionId)}
                      className="text-accent text-[11px] font-mono flex items-center gap-1 hover:underline"
                    >
                      <span>Pairwise Overlap &amp; Active Share Inspector ↗</span>
                    </button>
                  </div>
                  {topSecurities.length > 0 && isExpanded && (
                        <div className="mt-2.5 overflow-x-auto">
                          <table className="data-table w-full text-[11px] font-mono">
                            <thead>
                              <tr className="bg-surface">
                                <th className="text-left py-1.5 px-2">Security Name</th>
                                <th className="text-right py-1.5 px-2">Fund A Weight</th>
                                <th className="text-right py-1.5 px-2">Fund B Weight</th>
                                <th className="text-right py-1.5 px-2">Min Overlap</th>
                              </tr>
                            </thead>
                            <tbody className="divide-y divide-border">
                              {topSecurities.map((sec, secIdx) => {
                                const minWeight = Math.min(sec.weightA ?? 0, sec.weightB ?? 0);
                                return (
                                  <tr key={secIdx} className="hover:bg-surface-raised">
                                    <td className="py-1.5 px-2 font-medium text-text-primary">
                                      {sec.securityName}
                                      {sec.isin && <span className="block text-[9.5px] text-text-muted">{sec.isin}</span>}
                                    </td>
                                    <td className="text-right py-1.5 px-2 font-mono">
                                      {sec.weightA !== null && sec.weightA !== undefined ? `${sec.weightA.toFixed(2)}%` : '—'}
                                    </td>
                                    <td className="text-right py-1.5 px-2 font-mono">
                                      {sec.weightB !== null && sec.weightB !== undefined ? `${sec.weightB.toFixed(2)}%` : '—'}
                                    </td>
                                    <td className="text-right py-1.5 px-2 font-mono text-accent font-semibold">
                                      {minWeight.toFixed(2)}%
                                    </td>
                                  </tr>
                                );
                              })}
                            </tbody>
                          </table>
                        </div>
                  )}

                  <div className="pt-2 border-t border-border flex items-center justify-between">
                    <span className="mono-meta text-[10px]">Due Diligence Workflow</span>
                    <button
                      onClick={() =>
                        handleInvestigateFund(
                          fundA,
                          `Investigate ${data.overlapPercentage?.toFixed(1) ?? 'N/A'}% portfolio overlap between ${fundA.schemeName} and ${fundB.schemeName} across ${data.overlappingSecuritiesCount} shared securities. Verify if high common factor exposure negates intended diversification.`,
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
        </div>

        {/* Within-Fund Holdings Drift & Sector Shifts */}
        <WithinFundDriftComparison
          funds={comparison.funds}
          knowledgeCutoff={comparison.period?.knowledgeCutoffTime || comparison.knowledgeCutoff}
          ordinal="04. Portfolio Drift"
        />

        {/* Pairwise Daily Return Correlation */}
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <span className="mono-meta text-[11px] font-bold text-accent uppercase">
              Synchronous Return Co-Movement
            </span>
            <span className="text-[10.5px] font-mono text-text-muted">
              Pearson correlation calculated deterministically over intersecting trading days
            </span>
          </div>

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

                  <div className="text-[12px] leading-[1.55] text-text-secondary">
                    <span className="def-label block text-[10px] text-accent mb-0.5">EMPIRICAL INTERPRETATION</span>
                    {data.interpretation}
                  </div>

                  <div className="p-2.5 rounded bg-surface border border-border text-[11px] font-mono space-y-1">
                    <div className="flex items-center gap-1.5 text-amber-300">
                      <span className="status-badge state-candidate text-[9.5px]">CANDIDATE METHODOLOGY</span>
                      <span className="text-[10px]">Not validated for production investment advice</span>
                    </div>
                    <p className="text-text-muted text-[10.5px]">
                      Pearson correlation reflects synchronous historical daily price co-movement only. High or low correlation does not imply diversification benefit or downside protection under market stress.
                    </p>
                  </div>

                  {data.limitations && data.limitations.length > 0 && (
                    <ul className="list-disc list-inside text-[10.5px] font-mono text-amber-300 space-y-0.5">
                      {data.limitations.map((lim, lIdx) => (
                        <li key={lIdx}>{lim}</li>
                      ))}
                    </ul>
                  )}

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
        </div>
      </section>

      {/* =========================================================================
          SECTION E: EVIDENCE QUALITY, PROVENANCE & GOVERNANCE
         ========================================================================= */}
      <section id="section-quality" className="space-y-6">
        <SectionHeading
          ordinal="E. Evidence Quality"
          title="Observation Freshness, Coverage & Governance"
          description="Synchronicity of market NAV observations, holdings disclosure recency, calculation provenance, and methodology governance status."
        />

        {/* Unified Cross-Fund Evidence Quality Banner */}
        <EvidenceQualitySummary
          variant="banner"
          title="Cross-Fund Observation Freshness & Comparability Limitations"
          asOfDate={asOfDate}
          knowledgeCutoff={knowledgeCutoff}
          freshnessState={comparison.coverageSummary?.differingAsOfDates ? 'PARTIAL' : (asOfDate === '2024-01-15' ? 'STALE' : 'CURRENT')}
          coverageState={comparison.coverageSummary?.completeRiskEvidenceCount === comparison.coverageSummary?.totalSelectedFunds ? 'COMPLETE' : 'PARTIAL'}
          coverageNote={comparison.coverageSummary?.differingAsOfDates ? 'Caution: Selected funds evaluate across differing observation cutoffs.' : 'All compared funds share an aligned observation cutoff date.'}
          completenessState={comparison.coverageSummary?.missingMetricsCount ? 'PARTIAL' : 'COMPLETE'}
          missingInputs={comparison.coverageSummary?.missingMetricsCount ? `${comparison.coverageSummary.missingMetricsCount} metric observations missing or uncalculated` : null}
          methodologyStatus={comparison.methodologyStatus || 'CANDIDATE'}
          interpretationConsequence={
            comparison.coverageSummary?.differingAsOfDates ? (
              <span>
                <strong>Comparability Limitation:</strong> Selected funds use materially different observation cutoff dates. Comparing returns or risk metrics across unsynchronized time periods introduces look-ahead bias and distortion. Ensure funds share an identical evaluation horizon before drawing allocation conclusions.
              </span>
            ) : asOfDate === '2024-01-15' ? (
              <span>
                <strong>Historical Audit Horizon Active:</strong> This comparison evaluates historical observations through <strong>2024-01-15</strong> (Phase 2I/2J frozen canonical pilot). Stale historical NAVs cannot establish present-day relative performance or current portfolio suitability.
              </span>
            ) : (
              <span>
                All {comparison.funds.length} compared share classes evaluate under an aligned point-in-time horizon cutoff ({asOfDate}) with look-ahead bias strictly excluded.
              </span>
            )
          }
          nextStepLabel="Inspect Primary Fund Profile"
          nextStepHref={`/funds/${comparison.funds[0]?.schemeId || 1}?option=${comparison.funds[0]?.schemeOptionId || 1}#coverage`}
        />

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

        {/* Analytical Score Dimensions Side-by-Side */}
        <div className="panel p-4 space-y-3">
          <div className="flex items-center justify-between border-b border-border pb-2.5">
            <div>
              <span className="mono-meta text-[10.5px] text-accent font-bold uppercase">
                Dimension Score Breakdown
              </span>
              <h4 className="text-[13.5px] font-semibold text-text-primary mt-0.5">
                4 Institutional Analytical Quality Dimensions
              </h4>
            </div>
            <span className="status-badge state-candidate text-[10px]">CANDIDATE SCORECARD</span>
          </div>

          <div className="scroll-region overflow-x-auto">
            <table className="data-table w-full text-xs">
              <thead>
                <tr>
                  <th className="w-1/3">Quality Dimension</th>
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
                {CANONICAL_DIMENSIONS.map((dim) => (
                  <tr key={dim.code} className="hover:bg-surface-raised">
                    <td>
                      <div className="font-medium text-text-primary">{dim.name}</div>
                      <div className="mono-meta flex items-center gap-1.5 mt-0.5">
                        <span>Weight: {dim.weight}</span>
                        <span>•</span>
                        <span className="text-text-tertiary">{dim.code}</span>
                      </div>
                    </td>
                    {comparison.funds.map((fund) => {
                      const scoreDim = fund.yukiraScore?.dimensions?.find((d) => (d.dimensionCode || d.dimension) === dim.code);
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

        {/* Epistemic Observational Horizon & Comparability Notice */}
        <div className="p-3.5 rounded-xl border border-amber-500/30 bg-amber-500/5 text-xs font-mono dark:bg-amber-950/20 dark:border-amber-800/60 flex items-start gap-3">
          <span className="text-amber-500 text-sm mt-0.5">⚠</span>
          <div className="space-y-1">
            <p className="font-semibold text-text-primary">
              Observational Horizon &amp; Comparability Notice
            </p>
            <p className="text-text-secondary leading-relaxed">
              YUKIRA enforces bitemporal point-in-time boundaries (Knowledge Cutoff: {knowledgeCutoff}). Metrics are computed exclusively from observations published on or before this timestamp. Differences in fund inception dates (such as PPFAS ELSS Tax Saver starting July 2021) limit valid overlapping periods and are explicitly documented under observation compatibility.
            </p>
          </div>
        </div>
      </section>

      {/* =========================================================================
          SECTION F: INVESTIGATION & DUE DILIGENCE
         ========================================================================= */}
      <section id="section-investigation" className="space-y-6">
        <SectionHeading
          ordinal="F. Investigation & Due Diligence"
          title="Meaningful Differences, Unknowns & Due Diligence"
          description="Structured difference explanations distinguishing genuine differences from data limitations, with 1-click workspace investigation."
        />

        {/* Structured Comparison Difference Explanations & Diligence Findings */}
        <ComparisonInterpretationSummary
          comparison={comparison}
          asOfDate={asOfDate}
          knowledgeCutoff={knowledgeCutoff}
          onInvestigateFund={handleInvestigateFund}
        />

        {/* Next Diligence Actions & Research Continuity */}
        <div className="panel p-6 bg-surface-raised space-y-4 border-l-4 border-l-accent shadow-sm">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border pb-3">
            <div>
              <span className="mono-meta text-accent font-bold uppercase tracking-wider text-[11px]">
                Due Diligence Workflow Actions
              </span>
              <h3 className="text-h3 text-text-primary mt-0.5">
                Continue Your Due-Diligence Investigation
              </h3>
              <p className="text-text-secondary text-[12.5px] mt-0.5 leading-relaxed">
                Transition from side-by-side evidence comparison into dedicated investor research workflows.
              </p>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3 font-mono text-[11.5px]">
            {/* Action 1: Save Shortlist to Research Workspace */}
            <div className="panel-inset p-3 bg-surface rounded flex flex-col justify-between space-y-2 border border-border">
              <div>
                <span className="mono-meta text-[10px] text-accent font-bold uppercase block">
                  Workflow Action 01
                </span>
                <h5 className="font-semibold text-text-primary text-[12.5px] mt-0.5">
                  Save Shortlist to Workspace
                </h5>
                <p className="text-[11px] text-text-secondary mt-1 font-sans">
                  Preserve all {comparison.funds.length} candidates with comparative diligence questions in your saved research.
                </p>
              </div>
              <Link
                href="/watchlist"
                className="btn btn-secondary btn-sm text-[11px] justify-center mt-2"
              >
                Open Research Workspace →
              </Link>
            </div>

            {/* Action 2: Inspect in Evidence Explorer */}
            <div className="panel-inset p-3 bg-surface rounded flex flex-col justify-between space-y-2 border border-border">
              <div>
                <span className="mono-meta text-[10px] text-accent font-bold uppercase block">
                  Workflow Action 02
                </span>
                <h5 className="font-semibold text-text-primary text-[12.5px] mt-0.5">
                  Evidence Explorer Audit
                </h5>
                <p className="text-[11px] text-text-secondary mt-1 font-sans">
                  Inspect raw boundary observations, SHA-256 artifacts, and calculation run #{comparison.runId || 1}.
                </p>
              </div>
              <Link
                href={`/analysis/${comparison.runId || 1}`}
                className="btn btn-secondary btn-sm text-[11px] justify-center mt-2"
              >
                Evidence Explorer Run #{comparison.runId || 1} →
              </Link>
            </div>

            {/* Action 3: Screener Shortlist Refinement */}
            <div className="panel-inset p-3 bg-surface rounded flex flex-col justify-between space-y-2 border border-border">
              <div>
                <span className="mono-meta text-[10px] text-accent font-bold uppercase block">
                  Workflow Action 03
                </span>
                <h5 className="font-semibold text-text-primary text-[12.5px] mt-0.5">
                  Universe Screener
                </h5>
                <p className="text-[11px] text-text-secondary mt-1 font-sans">
                  Screen across 193 Direct-Growth options to replace candidates or identify new peer shortlists.
                </p>
              </div>
              {onOpenScreener ? (
                <button
                  type="button"
                  onClick={onOpenScreener}
                  className="btn btn-secondary btn-sm text-[11px] justify-center mt-2"
                >
                  Open Screener →
                </button>
              ) : (
                <Link
                  href="/compare?tab=screener"
                  className="btn btn-secondary btn-sm text-[11px] justify-center mt-2"
                >
                  Open Screener →
                </Link>
              )}
            </div>

            {/* Action 4: Primary Analytical Profile */}
            <div className="panel-inset p-3 bg-surface rounded flex flex-col justify-between space-y-2 border border-border">
              <div>
                <span className="mono-meta text-[10px] text-accent font-bold uppercase block">
                  Workflow Action 04
                </span>
                <h5 className="font-semibold text-text-primary text-[12.5px] mt-0.5">
                  Fund Profile &amp; Ledger
                </h5>
                <p className="text-[11px] text-text-secondary mt-1 font-sans">
                  Review complete institutional scorecard, holding longevity, and rolling consistency for primary candidate.
                </p>
              </div>
              <Link
                href={`/funds/${comparison.funds[0]?.schemeId || 1}?option=${comparison.funds[0]?.schemeOptionId || 1}`}
                className="btn btn-secondary btn-sm text-[11px] justify-center mt-2"
              >
                Primary Profile →
              </Link>
            </div>
          </div>
        </div>

        {/* Regulatory & Methodology Epistemic Notice */}
        <div className="panel state-candidate">
          <div className="panel-header">
            <h4 className="eyebrow">YUKIRA Comparability &amp; Epistemic Standards</h4>
          </div>
          <ul className="space-y-1.5 px-4 py-3 text-[12.5px] leading-[1.6] text-text-secondary">
            <li>All compared funds evaluated under identical knowledge cutoffs ({knowledgeCutoff}) and analysis endpoints ({asOfDate}).</li>
            <li>Historical performance does not predict future returns across changing economic regimes.</li>
            <li>YUKIRA does not generate investment recommendations, BUY/SELL/HOLD advice, composite star ratings, or performance rankings.</li>
            <li>Observation window length differences (e.g., PPFAS ELSS history starting July 2021) and partial holdings snapshots are explicitly flagged under comparability status.</li>
          </ul>
        </div>
      </section>

      {/* =========================================================================
          MASTER CONSOLIDATED QUANTITATIVE MATRIX (OPTIONAL EXPANDABLE OVERVIEW)
         ========================================================================= */}
      <section className="pt-2 border-t border-border">
        <div className="flex items-center justify-between pb-2">
          <button
            type="button"
            onClick={() => setShowMasterMatrix(!showMasterMatrix)}
            className="flex items-center gap-2 text-xs font-mono font-bold text-accent hover:underline"
          >
            <span>{showMasterMatrix ? '▲ Hide' : '▼ Inspect'} Consolidated Quantitative Matrix (All 5 Metric Groups)</span>
          </button>
          <span className="mono-meta text-[10.5px]">Filter across categories</span>
        </div>

        {showMasterMatrix && (
          <div className="space-y-4 pt-3">
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
                  {cat === 'ALL' && 'All Metric Groups'}
                  {cat === 'PERFORMANCE' && 'Performance & Compounding'}
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
                    {(() => {
                      const groupsToRender = activeCategory === 'ALL'
                        ? CANONICAL_METRIC_GROUPS.map((group) => ({
                            group,
                            metrics: comparison.metrics?.filter((m) => group.codes.includes(m.metricCode)) || [],
                          })).filter((g) => g.metrics.length > 0)
                        : [{
                            group: CANONICAL_METRIC_GROUPS.find((g) => g.categoryKey === activeCategory) || {
                              id: activeCategory.toLowerCase(),
                              categoryKey: activeCategory,
                              title: `${activeCategory} Metrics`,
                              ordinal: '1',
                              description: 'Filtered quantitative metrics',
                              codes: [],
                            },
                            metrics: filteredMetrics,
                          }];

                      return groupsToRender.map(({ group, metrics: groupMetrics }) => (
                        <React.Fragment key={group.id}>
                          <tr className="bg-surface-raised/80 border-t-2 border-border/80">
                            <td colSpan={comparison.funds.length + 2} className="py-2.5 px-3">
                              <div className="flex flex-wrap items-center justify-between gap-2">
                                <div className="flex items-center gap-2">
                                  <span className="font-mono text-[10px] font-bold text-accent px-1.5 py-0.5 rounded bg-accent/10 border border-accent/20">
                                    GROUP {group.ordinal}
                                  </span>
                                  <span className="font-semibold text-text-primary text-[12.5px]">
                                    {group.title}
                                  </span>
                                </div>
                                <span className="mono-meta text-[10.5px] text-text-secondary">
                                  {group.description}
                                </span>
                              </div>
                            </td>
                          </tr>
                          {groupMetrics.map((metric) => (
                            <MetricRow
                              key={metric.metricCode}
                              metric={metric}
                              funds={comparison.funds}
                              onInvestigateFund={handleInvestigateFund}
                              onOpenEvidenceModal={(m) => {
                                setEvidenceMetric(m);
                                setIsEvidenceModalOpen(true);
                              }}
                              runId={comparison.runId || 1}
                            />
                          ))}
                        </React.Fragment>
                      ));
                    })()}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}
      </section>

      <PairwiseHoldingsOverlapModal
        overlap={activeOverlapData}
        isOpen={isOverlapModalOpen}
        onClose={() => setIsOverlapModalOpen(false)}
        isLoading={overlapModalLoading}
      />

      {/* Comparison Evidence Explorer Modal */}
      <ComparisonEvidenceModal
        isOpen={isEvidenceModalOpen}
        onClose={() => {
          setIsEvidenceModalOpen(false);
          setEvidenceMetric(null);
        }}
        metric={evidenceMetric}
        funds={comparison.funds}
        comparison={comparison}
        onInvestigateFund={handleInvestigateFund}
      />

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
  onOpenEvidenceModal,
  runId,
}: {
  metric: ComparisonMetric;
  funds: ComparisonResponse["funds"];
  onInvestigateFund?: (fund: ComparisonFund, question: string, reason: string) => void;
  onOpenEvidenceModal?: (metric: ComparisonMetric) => void;
  runId?: number;
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
            <div className="flex flex-wrap items-center gap-2 mt-1">
              <button
                onClick={() => setExpanded(!expanded)}
                className="text-link inline-flex items-center gap-1 text-[11px]"
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
              {onOpenEvidenceModal && (
                <button
                  type="button"
                  onClick={() => onOpenEvidenceModal(metric)}
                  className="text-accent hover:underline font-mono text-[10.5px] inline-flex items-center gap-1"
                  title="Inspect formula, assumptions, and observation bounds in Evidence Explorer modal"
                >
                  <svg className="w-3 h-3 text-accent" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                  </svg>
                  <span>Evidence Explorer</span>
                </button>
              )}
            </div>
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
            const isInsufficient = result.calculationStatus === "INSUFFICIENT_DATA";
            return (
              <td key={fund.schemeOptionId}>
                <span className={`status-badge ${isInsufficient ? 'state-candidate' : 'border-rose-500/30 text-rose-400 bg-rose-500/10'} text-[9.5px]`}>
                  {isInsufficient ? "Insufficient Data" : "Not Available"}
                </span>
                {result.limitationReason && (
                  <div className="mono-meta mt-1 normal-case text-[10px] text-amber-400 font-mono">
                    {result.limitationReason}
                  </div>
                )}
                {result.errorMessage && (
                  <div className="mono-meta mt-1 normal-case text-[10px] text-text-tertiary font-mono">
                    {result.errorMessage}
                  </div>
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
                  <Link
                    href={`/analysis/${runId || 1}?metric=${metric.metricCode}`}
                    className="text-link text-[11px] font-mono block mt-2 hover:underline"
                  >
                    Open in Evidence Explorer (Run #{runId || 1}) →
                  </Link>
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
  const { watchlistItems, isWatchlisted, toggleWatchlist } = useWatchlist();
  const [unresolvableCandidateIds, setUnresolvableCandidateIds] = useState<string[]>([]);

  const [allSchemes, setAllSchemes] = useState<Scheme[]>([]);
  const [selectedSchemeIds, setSelectedSchemeIds] = useState<number[]>([]);
  const [schemeOptions, setSchemeOptions] = useState<Map<number, SchemeOption[]>>(new Map());
  const [selectedOptionIds, setSelectedOptionIds] = useState<Map<number, number>>(new Map());

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");

  const [asOfDate, setAsOfDate] = useState<string>(() => {
    return searchParams.get("asOfDate") || (searchParams.get("ids") ? "2024-01-15" : "2026-10-01");
  });
  const [knowledgeCutoff, setKnowledgeCutoff] = useState<string>(() => {
    return searchParams.get("knowledgeCutoff") || (searchParams.get("ids") ? "2024-01-31T23:59:59+05:30" : "2026-10-15T23:59:59+05:30");
  });

  const [viewMode, setViewMode] = useState<"SCREENER" | "DEEP_COMPARE">(() => {
    const tabParam = searchParams.get("tab");
    if (tabParam === "screener") return "SCREENER";
    if (tabParam === "compare" || searchParams.get("ids") || searchParams.get("funds")) return "DEEP_COMPARE";
    return "SCREENER";
  });

  const [screenerData, setScreenerData] = useState<UniverseScreenerResponse | null>(null);
  const [screenerLoading, setScreenerLoading] = useState(false);
  const [screenerError, setScreenerError] = useState<string | null>(null);
  const [screenerSelectedOptions, setScreenerSelectedOptions] = useState<number[]>([]);
  const [screenerHorizon, setScreenerHorizon] = useState<string>(() => {
    return searchParams.get("horizon") || "3Y";
  });

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
    const unresolvable: string[] = [];

    if (idsParam) {
      const parsedIds = idsParam.split(",").map((s) => s.trim()).filter(Boolean);
      parsedIds.forEach((rawStr) => {
        const rawId = Number(rawStr);
        if (isNaN(rawId)) {
          unresolvable.push(rawStr);
          return;
        }

        // 1. Direct canonical map
        const schemeFromOption = OPTION_TO_SCHEME_MAP[rawId];
        // 2. Direct catalog match
        const schemeFromCatalog = allSchemes.find((s) => s.id === rawId);
        // 3. Saved research match
        const savedItem = watchlistItems.find((w) => w.schemeOptionId === rawId || w.schemeId === rawId);

        let resolvedSchemeId: number | null = null;
        let resolvedOptionId: number | null = null;

        if (schemeFromOption) {
          resolvedSchemeId = schemeFromOption;
          resolvedOptionId = rawId;
        } else if (schemeFromCatalog) {
          resolvedSchemeId = schemeFromCatalog.id;
          resolvedOptionId = SCHEME_TO_DEFAULT_OPTION_MAP[resolvedSchemeId] || resolvedSchemeId;
        } else if (savedItem) {
          resolvedSchemeId = savedItem.schemeId;
          resolvedOptionId = savedItem.schemeOptionId || SCHEME_TO_DEFAULT_OPTION_MAP[savedItem.schemeId] || savedItem.schemeId;
        } else if (allSchemes.length > 0) {
          // If catalog is loaded and rawId matches neither scheme nor option nor saved item
          unresolvable.push(rawStr);
          return;
        } else {
          // Fallback while catalog is still loading
          resolvedSchemeId = rawId;
          resolvedOptionId = SCHEME_TO_DEFAULT_OPTION_MAP[rawId] || rawId;
        }

        if (resolvedSchemeId && !initialSchemeIds.includes(resolvedSchemeId)) {
          initialSchemeIds.push(resolvedSchemeId);
          initialOptionMap.set(resolvedSchemeId, resolvedOptionId || resolvedSchemeId);
        }
      });
    } else if (fundsParam) {
      const parsedFunds = fundsParam.split(",").map((s) => s.trim()).filter(Boolean);
      parsedFunds.forEach((rawStr) => {
        const rawFundId = Number(rawStr);
        if (isNaN(rawFundId)) {
          unresolvable.push(rawStr);
          return;
        }
        const resolvedSchemeId = OPTION_TO_SCHEME_MAP[rawFundId] || rawFundId;
        const defaultOpt = OPTION_TO_SCHEME_MAP[rawFundId] ? rawFundId : SCHEME_TO_DEFAULT_OPTION_MAP[rawFundId];

        if (allSchemes.length > 0 && !allSchemes.some((s) => s.id === resolvedSchemeId)) {
          unresolvable.push(rawStr);
          return;
        }

        if (!initialSchemeIds.includes(resolvedSchemeId)) {
          initialSchemeIds.push(resolvedSchemeId);
        }
        if (defaultOpt) {
          initialOptionMap.set(resolvedSchemeId, defaultOpt);
        }
      });

      if (optionsParam) {
        optionsParam.split(",").forEach((part) => {
          const [sIdStr, oIdStr] = part.split(":").map((s) => s.trim());
          const sId = Number(sIdStr);
          const oId = Number(oIdStr);
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
      // Avoid auto-pairing every fund with the canonical pilot when user navigates to comparison
      initialSchemeIds = [];
    }

    setUnresolvableCandidateIds(unresolvable);

    queueMicrotask(() => {
      setSelectedSchemeIds(initialSchemeIds.slice(0, 3));
      setSelectedOptionIds(initialOptionMap);
    });
  }, [searchParams, allSchemes, watchlistItems]);

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
    if (viewMode === "DEEP_COMPARE" && optionIds.length >= 2) {
      executeComparisonAction(optionIds, newAsOfDate, newCutoff);
    }
  };

  // Fetch universe screener data
  useEffect(() => {
    let cancelled = false;
    setScreenerLoading(true);
    setScreenerError(null);

    fetchUniverseScreener({ asOfDate, horizon: screenerHorizon })
      .then((data) => {
        if (!cancelled) {
          setScreenerData(data);
          setScreenerLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setScreenerError(err instanceof Error ? err.message : "Failed to load universe screener");
          setScreenerLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [asOfDate, knowledgeCutoff, screenerHorizon]);

  const handleToggleScreenerOption = (optId: number) => {
    setScreenerSelectedOptions((prev) => {
      if (prev.includes(optId)) {
        return prev.filter((id) => id !== optId);
      }
      if (prev.length >= 4) {
        return [...prev.slice(1), optId];
      }
      return [...prev, optId];
    });
  };

  const handleLaunchDetailedComparison = (optionIds: number[], horizon?: string) => {
    if (optionIds.length < 2) return;
    const targetIds = optionIds.slice(0, 4);
    const resolvedSchemes: number[] = [];
    const resolvedMap = new Map<number, number>();

    targetIds.forEach((optId) => {
      const item = screenerData?.items.find((i) => i.schemeOptionId === optId);
      const sId = item?.schemeId || OPTION_TO_SCHEME_MAP[optId] || optId;
      if (!resolvedSchemes.includes(sId)) {
        resolvedSchemes.push(sId);
      }
      resolvedMap.set(sId, optId);
    });

    const chosenHorizon = horizon || screenerHorizon;
    setSelectedSchemeIds(resolvedSchemes);
    setSelectedOptionIds(resolvedMap);
    setViewMode("DEEP_COMPARE");
    updateUrl(resolvedSchemes, resolvedMap, chosenHorizon);
    executeComparisonAction(targetIds, asOfDate, knowledgeCutoff);
  };

  // Auto-execute comparison once selected options are populated (e.g. from URL or pilots)
  useEffect(() => {
    if (viewMode === "DEEP_COMPARE" && selectedSchemeIds.length >= 2 && selectedOptionIds.size >= selectedSchemeIds.length && !comparison && !comparing && !compareError) {
      const optionIds = selectedSchemeIds
        .map((sId) => selectedOptionIds.get(sId))
        .filter((oId): oId is number => oId !== undefined);
      if (optionIds.length >= 2) {
        queueMicrotask(() => {
          executeComparisonAction(optionIds, asOfDate, knowledgeCutoff);
        });
      }
    }
  }, [viewMode, selectedSchemeIds, selectedOptionIds, comparison, comparing, compareError, executeComparisonAction, asOfDate, knowledgeCutoff]);

  const updateUrl = (schemeIds: number[], optionMap: Map<number, number>, horizon?: string) => {
    const optIds = schemeIds
      .map((sId) => optionMap.get(sId))
      .filter((oId): oId is number => oId !== undefined);
    const h = horizon || screenerHorizon;
    const newUrl = `/compare?ids=${optIds.join(",")}${h ? `&horizon=${encodeURIComponent(h)}` : ""}`;
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

  const handleAddCandidate = (newSchemeId: number) => {
    if (selectedSchemeIds.length >= 3) return;
    const defaultOpt = SCHEME_TO_DEFAULT_OPTION_MAP[newSchemeId];
    const existingOpts = schemeOptions.get(newSchemeId);
    const chosenOpt = defaultOpt || existingOpts?.[0]?.id || newSchemeId;

    const nextSchemeIds = [...selectedSchemeIds, newSchemeId];
    const nextOptionMap = new Map(selectedOptionIds);
    nextOptionMap.set(newSchemeId, chosenOpt);

    setSelectedSchemeIds(nextSchemeIds);
    setSelectedOptionIds(nextOptionMap);
    updateUrl(nextSchemeIds, nextOptionMap);

    const optionIds = nextSchemeIds
      .map((sId) => nextOptionMap.get(sId))
      .filter((oId): oId is number => oId !== undefined);
    executeComparisonAction(optionIds, asOfDate, knowledgeCutoff);
  };

  const handleReplaceCandidate = (oldSchemeId: number, newSchemeId: number) => {
    const nextSchemeIds = selectedSchemeIds.map((id) => (id === oldSchemeId ? newSchemeId : id));
    const nextOptionMap = new Map(selectedOptionIds);
    nextOptionMap.delete(oldSchemeId);

    const defaultOpt = SCHEME_TO_DEFAULT_OPTION_MAP[newSchemeId];
    const existingOpts = schemeOptions.get(newSchemeId);
    const chosenOpt = defaultOpt || existingOpts?.[0]?.id || newSchemeId;
    nextOptionMap.set(newSchemeId, chosenOpt);

    setSelectedSchemeIds(nextSchemeIds);
    setSelectedOptionIds(nextOptionMap);
    updateUrl(nextSchemeIds, nextOptionMap);

    const optionIds = nextSchemeIds
      .map((sId) => nextOptionMap.get(sId))
      .filter((oId): oId is number => oId !== undefined);
    executeComparisonAction(optionIds, asOfDate, knowledgeCutoff);
  };

  const handleLoadPilotBaseline = () => {
    setSelectedSchemeIds([...CANONICAL_PILOT_SCHEME_IDS]);
    const pilotMap = new Map<number, number>();
    pilotMap.set(1, 1);
    pilotMap.set(1720, 10262);
    pilotMap.set(1694, 10151);
    setSelectedOptionIds(pilotMap);
    setViewMode("DEEP_COMPARE");
    setAsOfDate("2024-01-15");
    setKnowledgeCutoff("2024-01-31T23:59:59+05:30");
    updateUrl(CANONICAL_PILOT_SCHEME_IDS, pilotMap, "3Y");
    executeComparisonAction([1, 10262, 10151], "2024-01-15", "2024-01-31T23:59:59+05:30");
  };

  const handleClearShortlist = () => {
    setSelectedSchemeIds([]);
    setSelectedOptionIds(new Map());
    setComparison(null);
    if (typeof window !== "undefined") {
      window.history.replaceState(null, "", "/compare?tab=compare");
    }
  };

  const comparativeSavedItems = useMemo(() => {
    return watchlistItems.filter((i) => {
      const text = `${i.reasonSaved || ""} ${i.investigationQuestion || ""}`.toLowerCase();
      return text.includes("comparative") || text.includes("shortlist") || text.includes("peer");
    });
  }, [watchlistItems]);

  const handleAddSavedCandidateToShortlist = (item: WatchlistResponse) => {
    if (selectedSchemeIds.includes(item.schemeId)) {
      toggleScheme(item.schemeId);
      return;
    }
    if (selectedSchemeIds.length >= 3) return;

    const chosenOpt = item.schemeOptionId || SCHEME_TO_DEFAULT_OPTION_MAP[item.schemeId] || item.schemeId;
    const nextSchemeIds = [...selectedSchemeIds, item.schemeId];
    const nextOptionMap = new Map(selectedOptionIds);
    nextOptionMap.set(item.schemeId, chosenOpt);

    setSelectedSchemeIds(nextSchemeIds);
    setSelectedOptionIds(nextOptionMap);
    updateUrl(nextSchemeIds, nextOptionMap);

    if (nextSchemeIds.length >= 2) {
      const optionIds = nextSchemeIds
        .map((sId) => nextOptionMap.get(sId))
        .filter((oId): oId is number => oId !== undefined);
      executeComparisonAction(optionIds, asOfDate, knowledgeCutoff);
    }
  };

  const handleResumeSavedComparativeShortlist = () => {
    const targetItems = comparativeSavedItems.slice(0, 3);
    if (targetItems.length < 2) return;

    const schemeIds = targetItems.map((i) => i.schemeId);
    const optMap = new Map<number, number>();
    targetItems.forEach((i) => {
      const optId = i.schemeOptionId || SCHEME_TO_DEFAULT_OPTION_MAP[i.schemeId] || i.schemeId;
      optMap.set(i.schemeId, optId);
    });

    setSelectedSchemeIds(schemeIds);
    setSelectedOptionIds(optMap);
    setViewMode("DEEP_COMPARE");
    updateUrl(schemeIds, optMap);

    const optionIds = schemeIds
      .map((sId) => optMap.get(sId))
      .filter((oId): oId is number => oId !== undefined);
    executeComparisonAction(optionIds, asOfDate, knowledgeCutoff);
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
      {/* Top Level Navigation Tabs: Screener vs Deep Compare */}
      <div className="flex items-center gap-2 border-b border-border mb-6">
        <button
          type="button"
          onClick={() => setViewMode("SCREENER")}
          className={`flex items-center gap-2 px-4 py-2.5 font-mono text-xs uppercase tracking-wider font-semibold transition-all border-b-2 -mb-[2px] ${
            viewMode === "SCREENER"
              ? "border-accent text-accent bg-accent/5"
              : "border-transparent text-text-muted hover:text-text-primary"
          }`}
        >
          <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 4a1 1 0 011-1h16a1 1 0 011 1v2.586a1 1 0 01-.293.707l-6.414 6.414a1 1 0 00-.293.707V17l-4 4v-6.586a1 1 0 00-.293-.707L3.293 7.293A1 1 0 013 6.586V4z" />
          </svg>
          <span>Universe Screener (193 Options · 5 Categories)</span>
          {screenerData && (
            <span className="ml-1 px-1.5 py-0.5 rounded bg-surface border border-border text-[10px] text-text-secondary">
              {screenerData.eligibleOptionsCount} Eligible
            </span>
          )}
        </button>

        <button
          type="button"
          onClick={() => setViewMode("DEEP_COMPARE")}
          className={`flex items-center gap-2 px-4 py-2.5 font-mono text-xs uppercase tracking-wider font-semibold transition-all border-b-2 -mb-[2px] ${
            viewMode === "DEEP_COMPARE"
              ? "border-accent text-accent bg-accent/5"
              : "border-transparent text-text-muted hover:text-text-primary"
          }`}
        >
          <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
          </svg>
          <span>Deep Head-to-Head Comparison</span>
          {comparison && (
            <span className="ml-1 px-1.5 py-0.5 rounded bg-emerald-500/15 border border-emerald-500/30 text-[10px] text-emerald-400">
              {comparison.funds.length} Active
            </span>
          )}
        </button>
      </div>

      {viewMode === "SCREENER" ? (
        <>
          {screenerLoading && !screenerData ? (
            <div className="panel p-12 text-center space-y-3">
              <svg className="h-8 w-8 animate-spin text-accent mx-auto" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
              </svg>
              <h3 className="text-h3 text-text-primary">Loading Reference Universe Screener…</h3>
              <p className="mono-meta">Aggregating 193 Direct-Growth options and authentic TRI benchmarks across 5 categories.</p>
            </div>
          ) : screenerError ? (
            <div className="state-panel-error p-6">
              <strong className="eyebrow block">Screener Error</strong>
              <span className="mt-1 block font-mono text-xs">{screenerError}</span>
            </div>
          ) : screenerData ? (
            <ComparativeScreener
              screenerData={screenerData}
              selectedOptionIds={screenerSelectedOptions}
              onToggleSelect={handleToggleScreenerOption}
              onLaunchDetailedComparison={handleLaunchDetailedComparison}
              isLoading={screenerLoading}
              activeHorizon={screenerHorizon}
              onHorizonChange={(h) => {
                setScreenerHorizon(h);
                if (typeof window !== "undefined") {
                  const url = new URL(window.location.href);
                  url.searchParams.set("horizon", h);
                  window.history.replaceState(null, "", url.toString());
                }
              }}
            />
          ) : null}
        </>
      ) : (
        <>
          <section className="panel state-candidate mb-8">
            <div className="panel-header">
              <h2 className="eyebrow">Institutional Multi-Fund Comparison Framework</h2>
            </div>
            <p className="max-w-[86ch] px-4 py-3 text-[12.5px] leading-[1.6] text-text-secondary">
              Compare two or three exact mutual-fund options using verified point-in-time observations across common-horizon performance, downside risk, benchmark sensitivity (vs NIFTY 500 TRI), portfolio concentration, pairwise holdings overlap, and synchronous return correlation. Evaluated under the strict YUKIRA Comparability Contract.
            </p>
          </section>

      {unresolvableCandidateIds.length > 0 && (
        <div className="panel p-4 mb-6 bg-amber-500/10 border border-amber-500/30 rounded-lg flex items-start gap-3 text-xs">
          <span className="text-amber-400 font-bold shrink-0 mt-0.5 text-base">⚠️</span>
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <span className="status-badge state-candidate text-[10px] font-mono">UNRESOLVED SHORTLIST CANDIDATE</span>
              <strong className="text-text-primary">Requested Option Not Located in Catalog</strong>
            </div>
            <p className="text-text-secondary leading-relaxed font-mono">
              The requested identifier(s) <span className="text-amber-300 font-bold">[{unresolvableCandidateIds.join(", ")}]</span> could not be resolved against verified registered instruments in the catalog. In accordance with YUKIRA epistemic rules, no surrogate funds were substituted.
            </p>
          </div>
        </div>
      )}

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
          {/* Shortlist Candidate Selection Workspace */}
          <section className="panel p-5 bg-surface space-y-4 mb-8 border-l-4 border-l-accent shadow-sm">
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border pb-3">
              <div>
                <div className="flex items-center gap-2">
                  <span className="mono-meta text-accent font-bold uppercase tracking-wider text-[11px]">
                    Shortlist Candidate Workspace
                  </span>
                  <span className="px-1.5 py-0.5 rounded bg-accent/10 border border-accent/20 text-accent font-mono text-[10px] font-semibold">
                    {selectedSchemeIds.length} of 3 Slots Filled
                  </span>
                </div>
                <h3 className="text-h3 text-text-primary mt-0.5">
                  Build Your Evidence Comparison Shortlist
                </h3>
                <p className="mono-meta text-[11px]">
                  Select 2 to 3 distinct mutual-fund share classes to perform deterministic, point-in-time quantitative comparison.
                </p>
              </div>

              <div className="flex flex-wrap items-center gap-2">
                <button
                  type="button"
                  onClick={() => setViewMode("SCREENER")}
                  className="btn btn-secondary btn-sm font-mono text-xs flex items-center gap-1.5"
                >
                  <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 4a1 1 0 011-1h16a1 1 0 011 1v2.586a1 1 0 01-.293.707l-6.414 6.414a1 1 0 00-.293.707V17l-4 4v-6.586a1 1 0 00-.293-.707L3.293 7.293A1 1 0 013 6.586V4z" />
                  </svg>
                  <span>Select from Universe Screener</span>
                </button>

                <button
                  type="button"
                  onClick={handleLoadPilotBaseline}
                  className="btn btn-ghost btn-sm font-mono text-xs text-accent hover:underline flex items-center gap-1.5"
                  title="Load the 3 canonical pilot funds (HDFC Flexi Cap + Parag Parikh Flexi Cap + Parag Parikh ELSS)"
                >
                  <span>Load Canonical Pilot Baseline →</span>
                </button>

                {selectedSchemeIds.length > 0 && (
                  <button
                    type="button"
                    onClick={handleClearShortlist}
                    className="btn btn-ghost btn-sm font-mono text-xs text-text-muted hover:text-rose-400"
                  >
                    Clear All
                  </button>
                )}
              </div>
            </div>

            {/* 3 Slot Overview Cards */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3 font-mono text-[11px]">
              {[0, 1, 2].map((slotIdx) => {
                const schemeId = selectedSchemeIds[slotIdx];
                const scheme = schemeId ? allSchemes.find((s) => s.id === schemeId) : null;
                const optId = schemeId ? selectedOptionIds.get(schemeId) : null;
                const opts = schemeId ? schemeOptions.get(schemeId) || [] : [];
                const selectedOpt = optId ? opts.find((o) => o.id === optId) : opts[0];
                const isRequired = slotIdx < 2;

                if (scheme) {
                  return (
                    <div
                      key={slotIdx}
                      className="panel-inset p-3 bg-surface-raised rounded border border-accent/40 space-y-2 flex flex-col justify-between"
                    >
                      <div className="space-y-1">
                        <div className="flex items-center justify-between">
                          <span className="mono-meta text-[10px] text-accent font-bold uppercase">
                            Slot 0{slotIdx + 1} • {isRequired ? "Required Candidate" : "Optional Candidate"}
                          </span>
                          <button
                            type="button"
                            onClick={() => toggleScheme(scheme.id)}
                            className="text-text-muted hover:text-rose-400 text-[10px]"
                            title="Remove candidate"
                          >
                            ✕ Remove
                          </button>
                        </div>
                        <h4 className="font-semibold text-text-primary text-[12.5px] truncate">
                          {scheme.name}
                        </h4>
                        <div className="mono-meta text-[10px]">
                          <span>{scheme.amc?.name || "Verified AMC"}</span>
                          {selectedOpt && (
                            <span className="text-text-primary ml-1">
                              • {selectedOpt.plan?.planType || "DIRECT"} • {selectedOpt.optionType || "GROWTH"}
                            </span>
                          )}
                        </div>
                      </div>
                    </div>
                  );
                }

                return (
                  <div
                    key={slotIdx}
                    className="panel-inset p-3 bg-surface rounded border border-dashed border-border flex flex-col justify-center items-center text-center space-y-1 py-4"
                  >
                    <span className="mono-meta text-[10px] text-text-muted uppercase">
                      Slot 0{slotIdx + 1} • {isRequired ? "Required Candidate" : "Optional 3rd Candidate"}
                    </span>
                    <span className="text-[12px] text-text-secondary font-medium">
                      {isRequired ? "Select Candidate Below" : "Available for 3-Fund Comparison"}
                    </span>
                  </div>
                );
              })}
            </div>
          </section>

          {/* Resume from Saved Research Workspace Section */}
          {watchlistItems.length > 0 && (
            <section className="panel p-5 bg-surface space-y-4 mb-8 border-l-4 border-l-accent shadow-sm">
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border pb-3">
                <div>
                  <div className="flex items-center gap-2">
                    <span className="mono-meta text-accent font-bold uppercase tracking-wider text-[11px]">
                      Saved Research Workspace Candidates
                    </span>
                    <span className="status-badge state-operational font-mono text-[10px]">
                      {watchlistItems.length} saved {watchlistItems.length === 1 ? "fund" : "funds"}
                    </span>
                  </div>
                  <h3 className="text-h3 text-text-primary mt-0.5">
                    Resume Previous Comparison Shortlist
                  </h3>
                  <p className="mono-meta text-[11px]">
                    Quickly load saved candidates from your research workspace directly into comparison slots. Reopening evaluates fresh point-in-time calculation runs under the verified knowledge cutoff.
                  </p>
                </div>

                <div className="flex flex-wrap items-center gap-2">
                  {comparativeSavedItems.length >= 2 && (
                    <button
                      type="button"
                      onClick={handleResumeSavedComparativeShortlist}
                      className="btn btn-primary btn-sm font-mono text-xs flex items-center gap-1.5 shadow"
                      title="Resume all candidates sharing comparative research notes"
                    >
                      <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
                      </svg>
                      <span>Resume Comparative Shortlist ({Math.min(comparativeSavedItems.length, 3)})</span>
                    </button>
                  )}
                  <Link
                    href="/watchlist"
                    className="btn btn-secondary btn-sm font-mono text-xs flex items-center gap-1.5"
                  >
                    <span>Manage Workspace →</span>
                  </Link>
                </div>
              </div>

              {/* Cards for each saved research item */}
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                {watchlistItems.map((item) => {
                  const isSelected = selectedSchemeIds.includes(item.schemeId);
                  const isComparative =
                    (item.reasonSaved || "").toLowerCase().includes("comparative") ||
                    (item.investigationQuestion || "").toLowerCase().includes("comparative");

                  return (
                    <div
                      key={`saved-cand-${item.schemeOptionId || item.schemeId}`}
                      className={`panel-inset p-3.5 bg-surface-raised/50 rounded-lg border text-xs space-y-2 flex flex-col justify-between transition-all ${
                        isSelected
                          ? "border-accent ring-1 ring-accent/30 bg-surface-raised/80"
                          : "border-border hover:border-border-accent"
                      }`}
                    >
                      <div className="space-y-1.5">
                        <div className="flex items-center justify-between gap-1">
                          <span className="mono-meta text-[10.5px] text-accent font-bold font-mono">
                            {item.schemeCode}
                          </span>
                          {isComparative && (
                            <span className="status-badge state-candidate text-[9.5px] font-mono text-accent">
                              COMPARATIVE SHORTLIST
                            </span>
                          )}
                        </div>
                        <h4 className="font-semibold text-text-primary leading-tight text-[13px]">
                          {item.schemeName}
                        </h4>
                        <div className="mono-meta text-[10.5px] flex flex-wrap items-center gap-1">
                          <span>{item.amcName || "Verified AMC"}</span>
                          <span>•</span>
                          <span className="text-text-secondary">
                            {item.planType || "DIRECT"} {item.optionType || "GROWTH"}
                          </span>
                          {item.schemeOptionId && (
                            <>
                              <span>•</span>
                              <span className="text-text-tertiary">Opt #{item.schemeOptionId}</span>
                            </>
                          )}
                          {item.amfiCode && (
                            <>
                              <span>•</span>
                              <span className="text-text-tertiary">AMFI: {item.amfiCode}</span>
                            </>
                          )}
                        </div>
                        {(item.investigationQuestion || item.reasonSaved) && (
                          <div className="pt-1 text-[11px] text-text-muted border-t border-border/40 font-sans">
                            <span className="mono-meta text-[9.5px] text-text-tertiary block font-bold uppercase">
                              Saved Note / Hypothesis:
                            </span>
                            <p className="line-clamp-2 italic text-text-secondary mt-0.5">
                              &ldquo;{item.investigationQuestion || item.reasonSaved}&rdquo;
                            </p>
                          </div>
                        )}
                      </div>

                      <div className="pt-2 border-t border-border/40 flex items-center justify-between">
                        <button
                          type="button"
                          onClick={() => handleAddSavedCandidateToShortlist(item)}
                          disabled={!isSelected && selectedSchemeIds.length >= 3}
                          className={`btn btn-xs font-mono text-[11px] ${
                            isSelected
                              ? "bg-accent/20 text-accent border border-accent/40 font-semibold"
                              : selectedSchemeIds.length >= 3
                              ? "opacity-50 cursor-not-allowed btn-secondary"
                              : "btn-secondary text-text-secondary hover:text-text-primary"
                          }`}
                          title={
                            isSelected
                              ? "Remove candidate from shortlist"
                              : selectedSchemeIds.length >= 3
                              ? "Shortlist capacity reached (3 slots occupied)"
                              : "Load this candidate into shortlist"
                          }
                        >
                          {isSelected ? "✓ In Shortlist" : "+ Add to Shortlist"}
                        </button>
                        <Link
                          href={`/funds/${item.schemeId}${item.schemeOptionId ? `?option=${item.schemeOptionId}` : ""}`}
                          className="text-[10.5px] text-text-muted hover:text-text-primary font-mono"
                        >
                          Profile →
                        </Link>
                      </div>
                    </div>
                  );
                })}
              </div>
            </section>
          )}

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
                  <span>Compare Selected Funds ({selectedSchemeIds.length})</span>
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
          onAddCandidate={handleAddCandidate}
          onReplaceCandidate={handleReplaceCandidate}
          onOpenScreener={() => setViewMode("SCREENER")}
        />
      )}
        </>
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
