'use client';

import React, { useState, useMemo, Suspense } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { PageContainer } from '@/components/layout/PageContainer';
import { StateView } from '@/components/epistemic/StateView';
import {
  evaluateGoalDiscovery,
  GoalDiscoveryRequest,
  GoalDiscoveryResponse,
  GoalDiscoveryResult,
} from '@/lib/api/discovery';
import { useUser } from '@auth0/nextjs-auth0/client';
import { useWatchlist } from '@/lib/hooks/useWatchlist';
import { ResearchNoteModal } from '@/components/watchlist/ResearchNoteModal';
import { WatchlistResponse } from '@/lib/api/investor';
import { EvidenceQualitySummary } from '@/components/epistemic/EvidenceQualitySummary';

interface ResearchObjective {
  id: string;
  title: string;
  tagline: string;
  description: string;
  defaultGoalCategory: string;
  suggestedHorizon: number;
  suggestedRisk: string;
  defaultPlan: string;
  defaultOption: string;
  prefillReason: string;
  prefillQuestion: string;
  primaryWorkflowLabel: string;
  primaryWorkflowHref: string;
}

const RESEARCH_OBJECTIVES: ResearchObjective[] = [
  {
    id: 'SHORTLIST_COMPARE',
    title: 'Shortlist Comparison & Divergence',
    tagline: 'Multi-fund active risk & overlap audit',
    description:
      'Compare 2–3 shortlisted mutual funds against the canonical market pilot to evaluate active share, benchmark divergence, and portfolio holdings overlap.',
    defaultGoalCategory: 'WEALTH_CREATION',
    suggestedHorizon: 5,
    suggestedRisk: 'MODERATE',
    defaultPlan: 'DIRECT',
    defaultOption: 'GROWTH',
    prefillReason: 'Investigating candidate fund for active divergence, overlap, and correlation against canonical pilot.',
    prefillQuestion: 'How much active share and security overlap does this candidate exhibit relative to the canonical market pilot?',
    primaryWorkflowLabel: 'Launch Multi-Fund Screener',
    primaryWorkflowHref: '/compare',
  },
  {
    id: 'DOWNSIDE_RISK',
    title: 'Downside Risk & Capital Impairment',
    tagline: 'Ulcer index, drawdown stress & recovery',
    description:
      'Evaluate downside semideviation, maximum historical drawdown duration, and downside beta to protect capital during severe market corrections.',
    defaultGoalCategory: 'WEALTH_CREATION',
    suggestedHorizon: 5,
    suggestedRisk: 'CONSERVATIVE',
    defaultPlan: 'DIRECT',
    defaultOption: 'GROWTH',
    prefillReason: 'Auditing downside path stress, Ulcer Index, and capital recovery duration across historical market corrections.',
    prefillQuestion: 'What was the maximum peak-to-trough drawdown duration for this fund, and does downside beta exceed 1.0?',
    primaryWorkflowLabel: 'Explore Risk Profiles',
    primaryWorkflowHref: '/funds',
  },
  {
    id: 'PORTFOLIO_AUDIT',
    title: 'Existing Portfolio Holding Due Diligence',
    tagline: 'Valuation completeness, freshness & drift audit',
    description:
      'Investigate a mutual fund you currently own. Audit whether NAV observations are current or historical (2024-01-15 cutoff) and inspect factor exposure.',
    defaultGoalCategory: 'CUSTOM',
    suggestedHorizon: 5,
    suggestedRisk: 'MODERATE',
    defaultPlan: 'DIRECT',
    defaultOption: 'GROWTH',
    prefillReason: 'Conducting due diligence on existing portfolio holding for valuation freshness and data completeness.',
    prefillQuestion: 'Are underlying NAV observations evaluated through current dates or historical cutoff (2024-01-15)?',
    primaryWorkflowLabel: 'Review Portfolio Holdings',
    primaryWorkflowHref: '/portfolio',
  },
  {
    id: 'CONSISTENCY_CAPTURE',
    title: 'Return Consistency & Fee Drag',
    tagline: 'Rolling returns & capture ratio asymmetry',
    description:
      'Examine rolling CAGR consistency across 3Y/5Y horizons, upside/downside capture ratios, and direct versus regular plan expense drag.',
    defaultGoalCategory: 'WEALTH_CREATION',
    suggestedHorizon: 5,
    suggestedRisk: 'AGGRESSIVE',
    defaultPlan: 'DIRECT',
    defaultOption: 'GROWTH',
    prefillReason: 'Evaluating rolling return stability and capture ratio asymmetry to verify genuine manager alpha.',
    prefillQuestion: 'Does the fund maintain consistent rolling performance across regimes or rely on a single historical cycle?',
    primaryWorkflowLabel: 'Compare Return Quality',
    primaryWorkflowHref: '/compare',
  },
  {
    id: 'PRE_ALLOCATION_DILIGENCE',
    title: 'Pre-Allocation Diligence Checkpoint',
    tagline: 'Thesis formation before committing capital',
    description:
      'Formulate a structured verification hypothesis, record due diligence questions, and organize verification steps before committing capital.',
    defaultGoalCategory: 'WEALTH_CREATION',
    suggestedHorizon: 7,
    suggestedRisk: 'MODERATE',
    defaultPlan: 'DIRECT',
    defaultOption: 'GROWTH',
    prefillReason: 'Pre-allocation due diligence checkpoint: formulating verification hypothesis before committing capital.',
    prefillQuestion: 'Has the current fund manager been in place for the full 5-year observation horizon?',
    primaryWorkflowLabel: 'Open Research Workspace',
    primaryWorkflowHref: '/watchlist',
  },
];

const HORIZON_OPTIONS = [
  { years: 2, label: '< 3 years', note: '1–2 year empirical evaluation window' },
  { years: 5, label: '3–5 years', note: 'Standard 3Y evaluation baseline' },
  { years: 7, label: '5–10 years', note: 'Full 5Y multi-cycle evaluation' },
  { years: 15, label: '10+ years', note: 'Long-term structural cycle' },
];

const RISK_OPTIONS = [
  { id: 'CONSERVATIVE', label: 'Conservative', description: 'Prioritizes downside capital protection and lower volatility' },
  { id: 'MODERATE', label: 'Moderate', description: 'Balanced growth with managed market drawdown' },
  { id: 'AGGRESSIVE', label: 'Aggressive', description: 'Accepts higher market volatility for long-term equity returns' },
];

const PLAN_OPTIONS = [
  { id: 'DIRECT', label: 'Direct Plan', note: 'Eliminates distributor commissions' },
  { id: 'REGULAR', label: 'Regular Plan', note: 'Includes distributor commission drag' },
  { id: 'ANY', label: 'Any Plan', note: 'Evaluate both plan structures' },
];

const OPTION_OPTIONS = [
  { id: 'GROWTH', label: 'Growth Option', note: 'Capital compounding without dividend distribution' },
  { id: 'IDCW', label: 'IDCW Option', note: 'Income distribution cum capital withdrawal' },
  { id: 'ANY', label: 'Any Option', note: 'Evaluate both option types' },
];

const CATEGORY_OPTIONS = [
  { id: 'ANY', label: 'Any Category' },
  { id: 'Equity', label: 'Equity Schemes' },
  { id: 'Hybrid', label: 'Hybrid Schemes' },
  { id: 'Debt', label: 'Debt Schemes' },
];

const MODE_OPTIONS = [
  { id: 'SIP', label: 'SIP (Systematic Investment Plan)', description: 'Recurring periodic contributions' },
  { id: 'LUMPSUM', label: 'Lumpsum', description: 'One-time initial capital allocation' },
  { id: 'EITHER', label: 'Either / Flexible', description: 'No strict investment mode requirement' },
];

function GoalDiscoveryContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { user } = useUser();
  const { watchlistItems, isWatchlisted, saveResearchNote } = useWatchlist();

  // Research Objective Selector
  const [selectedObjectiveId, setSelectedObjectiveId] = useState<string>('SHORTLIST_COMPARE');
  const currentObjective = useMemo(() => {
    return RESEARCH_OBJECTIVES.find((o) => o.id === selectedObjectiveId) || RESEARCH_OBJECTIVES[0];
  }, [selectedObjectiveId]);

  // Form criteria state
  const [goalCategory, setGoalCategory] = useState<string>(currentObjective.defaultGoalCategory);
  const [horizonYears, setHorizonYears] = useState<number>(currentObjective.suggestedHorizon);
  const [riskTolerance, setRiskTolerance] = useState<string>(currentObjective.suggestedRisk);
  const [planPreference, setPlanPreference] = useState<string>(currentObjective.defaultPlan);
  const [optionPreference, setOptionPreference] = useState<string>(currentObjective.defaultOption);
  const [fundCategory, setFundCategory] = useState<string>('ANY');
  const [investmentMode, setInvestmentMode] = useState<string>('SIP');
  const [customInvestorQuestion, setCustomInvestorQuestion] = useState<string>('');

  // Async evaluation state
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [response, setResponse] = useState<GoalDiscoveryResponse | null>(null);

  // Active Research Note Modal State
  const [noteTargetFund, setNoteTargetFund] = useState<WatchlistResponse | null>(null);
  const [isNoteModalOpen, setIsNoteModalOpen] = useState<boolean>(false);

  // When objective changes, update suggestions cleanly
  const handleSelectObjective = (obj: ResearchObjective) => {
    setSelectedObjectiveId(obj.id);
    setGoalCategory(obj.defaultGoalCategory);
    setHorizonYears(obj.suggestedHorizon);
    setRiskTolerance(obj.suggestedRisk);
    setPlanPreference(obj.defaultPlan);
    setOptionPreference(obj.defaultOption);
    setCustomInvestorQuestion('');
  };

  const handleEvaluate = async () => {
    setLoading(true);
    setError(null);

    const req: GoalDiscoveryRequest = {
      goalCategory,
      horizonYears,
      riskTolerance,
      investmentMode,
      fundCategory,
    };

    try {
      const data = await evaluateGoalDiscovery(req);
      setResponse(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to evaluate goal discovery criteria.');
    } finally {
      setLoading(false);
    }
  };

  // Open note modal for saving a candidate with pre-filled objective
  const handleOpenSaveNote = (result: GoalDiscoveryResult) => {
    const existing = watchlistItems.find(
      (item) => (item.schemeOptionId && result.schemeOptionId)
        ? item.schemeOptionId === result.schemeOptionId
        : item.schemeId === result.schemeId
    );

    // If investor previously recorded notes, preserve them; otherwise use discovery archetype prefill
    const questionToPrefill =
      existing?.investigationQuestion ||
      customInvestorQuestion.trim() ||
      (result.investigationQuestions && result.investigationQuestions.length > 0
        ? result.investigationQuestions[0]
        : currentObjective.prefillQuestion);

    const reasonToPrefill =
      existing?.reasonSaved ||
      `${currentObjective.title}: ${currentObjective.prefillReason}`;

    const nextStepToPrefill =
      existing?.nextStep ||
      'Verify downside semideviation and audit raw AMFI source artifacts.';

    const tempFund: WatchlistResponse = existing || {
      id: -Date.now(),
      schemeId: result.schemeId,
      schemeOptionId: result.schemeOptionId,
      schemeName: result.fundName,
      schemeCode: `SCHEME_${result.schemeId}`,
      amcName: result.amcName,
      category: result.category,
      planType: result.planType,
      optionType: result.optionType,
      amfiCode: result.amfiCode,
      isin: result.isin,
      reasonSaved: reasonToPrefill,
      investigationQuestion: questionToPrefill,
      nextStep: nextStepToPrefill,
    };

    setNoteTargetFund(tempFund);
    setIsNoteModalOpen(true);
  };

  // Eligible Option IDs for top multi-compare
  const eligibleOptionIds = useMemo(() => {
    if (!response?.results) return [];
    return response.results
      .filter((r) => r.resultState === 'ELIGIBLE' || r.resultState === 'PARTIALLY_EVALUATED')
      .slice(0, 3)
      .map((r) => r.schemeOptionId);
  }, [response?.results]);

  return (
    <PageContainer
      title="Investor Goal & Objective Discovery"
      subtitle="Translate your investment question or due diligence objective into a structured, evidence-grounded research workflow."
      breadcrumbs={[
        { label: 'Home', href: '/' },
        { label: 'Discover', href: '/discover' },
      ]}
    >
      {/* Product Philosophy & Non-Advisory Mandate Banner */}
      <section className="mb-8 rounded-2xl border border-border-gold-subtle bg-surface-midnight/90 p-5 shadow-xl backdrop-blur-sm glow-gold">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-border-gold-subtle/50 pb-3 mb-3">
          <div className="flex items-center gap-2">
            <span className="h-2 w-2 rounded-full bg-primary" />
            <span className="font-mono text-[11px] uppercase tracking-[0.14em] text-primary font-semibold">
              Research Organization · Evidence-First Checkpoint
            </span>
          </div>
          <span className="font-mono text-[10px] uppercase tracking-wider text-text-cream-muted bg-surface-navy-raised px-2.5 py-1 rounded border border-border-gold-subtle/50">
            NO BUY/SELL ADVICE · NO RETURN FORECASTS
          </span>
        </div>
        <p className="text-xs sm:text-[13px] leading-relaxed text-text-cream-body font-sans">
          YUKIRA Goal Discovery helps allocators frame an investment inquiry, inspect exact scheme-options, and verify quantitative evidence. This tool is non-advisory and does NOT generate commercial fund rankings, return predictions, or automated recommendations.
        </p>
      </section>

      {/* Direct Fast-Path Research Hub */}
      <section className="mb-8" aria-label="Fast-Path Research Hub">
        <div className="flex items-center justify-between gap-4 mb-3">
          <h2 className="text-sm font-bold uppercase tracking-wider font-mono text-primary">
            Direct Research Workflows
          </h2>
          <span className="font-mono text-[11px] text-text-cream-muted">
            Have a specific task in mind?
          </span>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
          <Link
            href="/compare"
            className="p-3.5 rounded-xl border border-border-gold-subtle bg-surface-midnight hover:border-primary/60 transition-all flex flex-col justify-between group shadow-sm"
          >
            <div>
              <span className="font-mono text-[10px] text-primary font-semibold uppercase">01 · Multi-Fund</span>
              <h3 className="text-xs font-bold text-text-cream-bright mt-0.5 group-hover:text-primary transition-colors">
                Compare Candidate Funds
              </h3>
              <p className="text-[11px] text-text-cream-muted mt-1 leading-snug">
                Screen side-by-side across return, risk, and overlap metrics.
              </p>
            </div>
            <span className="font-mono text-[10.5px] text-primary mt-3 block font-medium">Open Screener &rarr;</span>
          </Link>

          <Link
            href="/portfolio"
            className="p-3.5 rounded-xl border border-border-gold-subtle bg-surface-midnight hover:border-primary/60 transition-all flex flex-col justify-between group shadow-sm"
          >
            <div>
              <span className="font-mono text-[10px] text-primary font-semibold uppercase">02 · Audit</span>
              <h3 className="text-xs font-bold text-text-cream-bright mt-0.5 group-hover:text-primary transition-colors">
                Audit Existing Holdings
              </h3>
              <p className="text-[11px] text-text-cream-muted mt-1 leading-snug">
                Verify valuation completeness and NAV observation freshness.
              </p>
            </div>
            <span className="font-mono text-[10.5px] text-primary mt-3 block font-medium">Open Portfolio &rarr;</span>
          </Link>

          <Link
            href="/funds"
            className="p-3.5 rounded-xl border border-border-gold-subtle bg-surface-midnight hover:border-primary/60 transition-all flex flex-col justify-between group shadow-sm"
          >
            <div>
              <span className="font-mono text-[10px] text-primary font-semibold uppercase">03 · Universe</span>
              <h3 className="text-xs font-bold text-text-cream-bright mt-0.5 group-hover:text-primary transition-colors">
                Browse Mutual Fund Catalog
              </h3>
              <p className="text-[11px] text-text-cream-muted mt-1 leading-snug">
                Filter by AMC, category, plan, and option with full lineage.
              </p>
            </div>
            <span className="font-mono text-[10.5px] text-primary mt-3 block font-medium">Search Catalog &rarr;</span>
          </Link>

          <Link
            href="/watchlist"
            className="p-3.5 rounded-xl border border-border-gold-subtle bg-surface-midnight hover:border-primary/60 transition-all flex flex-col justify-between group shadow-sm"
          >
            <div>
              <span className="font-mono text-[10px] text-primary font-semibold uppercase">04 · Notes</span>
              <h3 className="text-xs font-bold text-text-cream-bright mt-0.5 group-hover:text-primary transition-colors">
                Saved Research Workspace
              </h3>
              <p className="text-[11px] text-text-cream-muted mt-1 leading-snug">
                Review your saved due diligence questions and hypotheses.
              </p>
            </div>
            <span className="font-mono text-[10.5px] text-primary mt-3 block font-medium">Open Workspace &rarr;</span>
          </Link>
        </div>
      </section>

      {/* Structured Research Objective Selector */}
      <section className="mb-8 rounded-2xl border border-border-gold-subtle bg-surface-midnight/90 p-6 shadow-xl backdrop-blur-sm glow-gold">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-border-gold-subtle/50 pb-4 mb-5">
          <div>
            <span className="font-mono text-[11px] uppercase tracking-[0.14em] text-primary font-semibold">
              Step 1 · Articulate Research Objective
            </span>
            <h2 className="text-lg font-bold text-text-cream-bright mt-0.5">
              What Are You Trying to Investigate?
            </h2>
          </div>
          <span className="font-mono text-[11px] text-text-cream-muted">
            Select an investigation archetype
          </span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-5 gap-3 mb-5">
          {RESEARCH_OBJECTIVES.map((obj) => {
            const isSelected = selectedObjectiveId === obj.id;
            return (
              <button
                key={obj.id}
                type="button"
                onClick={() => handleSelectObjective(obj)}
                className={`p-4 rounded-xl border text-left transition-all flex flex-col justify-between ${
                  isSelected
                    ? 'border-primary bg-surface-navy-raised text-text-cream-bright shadow-md glow-gold-sm'
                    : 'border-border-gold-subtle/60 bg-surface-obsidian/60 hover:border-border-gold-bright text-text-cream-muted'
                }`}
              >
                <div>
                  <span className={`font-mono text-[10px] uppercase font-bold tracking-wider ${isSelected ? 'text-primary' : 'text-text-cream-muted'}`}>
                    {obj.tagline}
                  </span>
                  <h3 className="text-xs font-bold mt-1 text-text-cream-bright leading-snug">
                    {obj.title}
                  </h3>
                  <p className="text-[11px] leading-relaxed mt-2 text-text-cream-body">
                    {obj.description}
                  </p>
                </div>
                <div className="mt-3 pt-2 border-t border-border-gold-subtle/40 font-mono text-[10px]">
                  {isSelected ? (
                    <span className="text-primary font-bold">Active Objective ✓</span>
                  ) : (
                    <span className="text-text-cream-muted">Select objective &rarr;</span>
                  )}
                </div>
              </button>
            );
          })}
        </div>

        {/* Selected Objective Context Strip */}
        <div className="p-4 rounded-xl bg-surface-obsidian border border-border-gold-subtle/60 flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="space-y-1">
            <span className="font-mono text-[10.5px] uppercase tracking-wider text-primary font-semibold">
              Active Investigation Context: {currentObjective.title}
            </span>
            <p className="text-xs text-text-cream-body font-sans">
              Prefill hypothesis: <em className="text-text-cream-bright">"{currentObjective.prefillReason}"</em>
            </p>
          </div>
          <Link
            href={currentObjective.primaryWorkflowHref}
            className="rounded-full bg-surface-navy-raised px-4 py-2 font-sans text-xs font-semibold text-text-cream-bright border border-border-gold-subtle hover:text-primary hover:border-border-gold-bright transition-all shrink-0 self-start md:self-auto"
          >
            {currentObjective.primaryWorkflowLabel} &rarr;
          </Link>
        </div>
      </section>

      {/* Research Constraints & Parameter Configurator */}
      <section className="mb-8 rounded-2xl border border-border-gold-subtle bg-surface-midnight/90 p-6 shadow-xl backdrop-blur-sm glow-gold">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-border-gold-subtle/50 pb-4 mb-5">
          <div>
            <span className="font-mono text-[11px] uppercase tracking-[0.14em] text-primary font-semibold">
              Step 2 · Calibrate Research Parameters
            </span>
            <h2 className="text-lg font-bold text-text-cream-bright mt-0.5">
              Empirical Constraints & Exact Options
            </h2>
          </div>
          <span className="font-mono text-[11px] text-text-cream-muted">
            Deterministic matching on backend
          </span>
        </div>

        <div className="space-y-6">
          {/* Horizon Selection */}
          <div>
            <div className="flex items-center justify-between gap-2 mb-2">
              <label className="font-sans text-xs font-semibold text-text-cream-bright uppercase tracking-wider">
                Evaluation Horizon Baseline
              </label>
              <span className="font-mono text-[10.5px] text-text-cream-muted">
                Empirical window for calculating realized return & risk
              </span>
            </div>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
              {HORIZON_OPTIONS.map((h) => {
                const isSelected = horizonYears === h.years;
                return (
                  <button
                    key={h.years}
                    type="button"
                    onClick={() => setHorizonYears(h.years)}
                    className={`p-3 rounded-xl border text-left transition-all ${
                      isSelected
                        ? 'border-primary bg-surface-navy-raised text-text-cream-bright shadow-sm'
                        : 'border-border-gold-subtle/60 bg-surface-obsidian/60 hover:border-border-gold-bright text-text-cream-muted'
                    }`}
                  >
                    <span className="font-mono text-sm font-bold text-text-cream-bright block">{h.label}</span>
                    <span className="text-[11px] text-text-cream-muted mt-0.5 block">{h.note}</span>
                  </button>
                );
              })}
            </div>
            <p className="mt-2 text-[11px] font-mono text-text-cream-muted">
              Note: Horizon is an analysis evaluation parameter; it does not constitute suitability or guaranteed capital protection.
            </p>
          </div>

          {/* Scheme-Option Precision Grid */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6 pt-4 border-t border-border-gold-subtle/40">
            {/* Plan Preference */}
            <div>
              <label className="font-sans text-xs font-semibold text-text-cream-bright uppercase tracking-wider block mb-2">
                Plan Structure (Direct vs Regular)
              </label>
              <div className="space-y-2">
                {PLAN_OPTIONS.map((p) => (
                  <label
                    key={p.id}
                    className={`flex items-start gap-2.5 p-2.5 rounded-lg border text-xs cursor-pointer transition-all ${
                      planPreference === p.id
                        ? 'border-primary bg-surface-navy-raised text-text-cream-bright'
                        : 'border-border-gold-subtle/50 bg-surface-obsidian text-text-cream-muted hover:border-border-gold-subtle'
                    }`}
                  >
                    <input
                      type="radio"
                      name="planPreference"
                      checked={planPreference === p.id}
                      onChange={() => setPlanPreference(p.id)}
                      className="mt-0.5 accent-[#d4af37]"
                    />
                    <div>
                      <span className="font-semibold block text-text-cream-bright">{p.label}</span>
                      <span className="text-[11px] text-text-cream-muted">{p.note}</span>
                    </div>
                  </label>
                ))}
              </div>
            </div>

            {/* Option Preference */}
            <div>
              <label className="font-sans text-xs font-semibold text-text-cream-bright uppercase tracking-wider block mb-2">
                Option Type (Growth vs IDCW)
              </label>
              <div className="space-y-2">
                {OPTION_OPTIONS.map((o) => (
                  <label
                    key={o.id}
                    className={`flex items-start gap-2.5 p-2.5 rounded-lg border text-xs cursor-pointer transition-all ${
                      optionPreference === o.id
                        ? 'border-primary bg-surface-navy-raised text-text-cream-bright'
                        : 'border-border-gold-subtle/50 bg-surface-obsidian text-text-cream-muted hover:border-border-gold-subtle'
                    }`}
                  >
                    <input
                      type="radio"
                      name="optionPreference"
                      checked={optionPreference === o.id}
                      onChange={() => setOptionPreference(o.id)}
                      className="mt-0.5 accent-[#d4af37]"
                    />
                    <div>
                      <span className="font-semibold block text-text-cream-bright">{o.label}</span>
                      <span className="text-[11px] text-text-cream-muted">{o.note}</span>
                    </div>
                  </label>
                ))}
              </div>
            </div>

            {/* Fund Category Focus */}
            <div>
              <label className="font-sans text-xs font-semibold text-text-cream-bright uppercase tracking-wider block mb-2">
                Asset Category Focus
              </label>
              <div className="space-y-2">
                {CATEGORY_OPTIONS.map((c) => (
                  <label
                    key={c.id}
                    className={`flex items-center gap-2.5 p-2.5 rounded-lg border text-xs cursor-pointer transition-all ${
                      fundCategory === c.id
                        ? 'border-primary bg-surface-navy-raised text-text-cream-bright'
                        : 'border-border-gold-subtle/50 bg-surface-obsidian text-text-cream-muted hover:border-border-gold-subtle'
                    }`}
                  >
                    <input
                      type="radio"
                      name="fundCategory"
                      checked={fundCategory === c.id}
                      onChange={() => setFundCategory(c.id)}
                      className="accent-[#d4af37]"
                    />
                    <span className="font-medium text-text-cream-bright">{c.label}</span>
                  </label>
                ))}
              </div>
            </div>
          </div>

          {/* Optional Diligence Question Formulation */}
          <div className="pt-4 border-t border-border-gold-subtle/40">
            <label className="font-sans text-xs font-semibold text-text-cream-bright uppercase tracking-wider block mb-1">
              Custom Investigation Question (Optional)
            </label>
            <p className="text-[11px] text-text-cream-muted mb-2 font-sans">
              Record a specific due diligence question you want carried forward into saved research notes.
            </p>
            <input
              type="text"
              value={customInvestorQuestion}
              onChange={(e) => setCustomInvestorQuestion(e.target.value)}
              placeholder={`e.g. ${currentObjective.prefillQuestion}`}
              className="w-full rounded-xl border border-border-gold-subtle bg-surface-obsidian px-4 py-2.5 font-sans text-xs text-text-cream-bright placeholder:text-text-cream-muted/50 focus:border-primary focus:outline-none"
            />
          </div>

          {/* Action Row */}
          <div className="pt-4 border-t border-border-gold-subtle/50 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div className="flex items-center gap-2 text-xs text-text-cream-muted font-mono">
              <span className="w-2 h-2 rounded-full bg-primary" />
              <span>Deterministic backend evaluation · Zero synthetic simulation</span>
            </div>

            <div className="flex flex-wrap items-center gap-3">
              <Link
                href={`/funds?category=${fundCategory !== 'ANY' ? fundCategory : 'ALL'}&plan=${planPreference}&option=${optionPreference}`}
                className="rounded-full bg-surface-navy-raised px-4 py-2.5 font-sans text-xs font-semibold text-text-cream-bright border border-border-gold-subtle hover:text-primary hover:border-border-gold-bright transition-all"
              >
                Browse in Catalog &rarr;
              </Link>

              <button
                type="button"
                onClick={handleEvaluate}
                disabled={loading}
                className="flex items-center justify-center gap-2 rounded-full bg-gradient-to-r from-[#d4af37] via-[#f2ca50] to-[#e6be44] px-6 py-2.5 font-sans text-xs font-semibold text-[#1e1704] glow-gold-sm transition-all duration-200 hover:scale-[1.02] hover:from-[#e1bc42] hover:to-[#ffd868] shadow-md active:scale-[0.98] disabled:opacity-50"
              >
                {loading ? (
                  <>
                    <svg className="animate-spin h-3.5 w-3.5 text-[#1e1704]" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                    </svg>
                    <span>Evaluating Master Records…</span>
                  </>
                ) : (
                  <span>Evaluate Scheme Candidates</span>
                )}
              </button>
            </div>
          </div>
        </div>
      </section>

      {/* Error View */}
      {error && (
        <div className="mb-8">
          <StateView
            kind="unavailable"
            title="Discovery Evaluation Failed"
            message={error}
          />
        </div>
      )}

      {/* Evaluation Results Section */}
      {response && (
        <section className="mb-12" id="results" aria-label="Discovery Results">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-border-gold-subtle pb-4 mb-6">
            <div>
              <span className="font-mono text-[11px] uppercase tracking-[0.14em] text-primary font-semibold">
                Step 3 · Verified Analytical Candidates
              </span>
              <h2 className="text-xl font-bold text-text-cream-bright sm:text-2xl mt-0.5">
                Evaluated Scheme Options ({response.results.length} of {response.totalEvaluated})
              </h2>
              <p className="text-xs text-text-cream-body font-sans mt-1">
                Sorted by eligibility state, data completeness, and alphabetical fund identity. Not an evaluative ranking.
              </p>
            </div>

            {eligibleOptionIds.length >= 2 && (
              <Link
                href={`/compare?ids=${eligibleOptionIds.join(',')}`}
                className="flex items-center gap-1.5 rounded-full bg-gradient-to-r from-[#d4af37] via-[#f2ca50] to-[#e6be44] px-5 py-2 font-sans text-xs font-semibold text-[#1e1704] glow-gold-sm transition-all hover:scale-[1.02]"
              >
                <span>Compare Top {eligibleOptionIds.length} in Screener</span>
                <span>&rarr;</span>
              </Link>
            )}
          </div>

          {/* Unified Discovery Evidence Quality & Limitations Banner */}
          <div className="mb-6">
            <EvidenceQualitySummary
              variant="banner"
              title="Goal Discovery Evidence Quality & Historical Pilot Limitations"
              asOfDate={response.results[0]?.evidenceState?.navAsOfDate || '2024-01-15'}
              freshnessState={
                (response.results[0]?.evidenceState?.navAsOfDate === '2024-01-15' || !response.results[0]?.evidenceState?.navAsOfDate)
                  ? 'STALE'
                  : 'CURRENT'
              }
              coverageState={response.results.every((r) => r.resultState === 'ELIGIBLE') ? 'COMPLETE' : 'PARTIAL'}
              methodologyStatus="CANDIDATE"
              interpretationConsequence={
                <span>
                  <strong>Candidate Discovery Results:</strong> Goal matching applies deterministic filters against historical market data (as of {response.results[0]?.evidenceState?.navAsOfDate || '2024-01-15'}). These matches are objective investigative starting points, <strong>NOT</strong> investment recommendations or suitability certifications. Verify recent disclosures and downside risk before allocating capital.
                </span>
              }
              nextStepLabel={eligibleOptionIds.length >= 2 ? "Compare Candidates in Screener" : "Launch Multi-Fund Screener"}
              nextStepHref={eligibleOptionIds.length >= 2 ? `/compare?ids=${eligibleOptionIds.join(',')}` : "/compare"}
            />
          </div>

          {response.results.length === 0 ? (
            <StateView
              kind="empty"
              title="No Matching Schemes Found"
              message="No mutual fund scheme options in the registry matched the specified criteria. Try loosening category or horizon baseline parameters."
            />
          ) : (
            <div className="space-y-5">
              {response.results.map((result) => {
                const isSaved = isWatchlisted(result.schemeId, result.schemeOptionId);
                return (
                  <DiscoveryResultCard
                    key={result.schemeOptionId}
                    result={result}
                    isSaved={isSaved}
                    onOpenNote={() => handleOpenSaveNote(result)}
                    objectiveTitle={currentObjective.title}
                    suggestedQuestion={customInvestorQuestion || (result.investigationQuestions && result.investigationQuestions.length > 0 ? result.investigationQuestions[0] : currentObjective.prefillQuestion)}
                  />
                );
              })}
            </div>
          )}
        </section>
      )}

      {/* Structured Research Note Modal */}
      <ResearchNoteModal
        isOpen={isNoteModalOpen}
        onClose={() => {
          setIsNoteModalOpen(false);
          setNoteTargetFund(null);
        }}
        fund={noteTargetFund}
        onSave={async (schemeId, note, schemeOptionId) => {
          await saveResearchNote(schemeId, note, schemeOptionId);
          setIsNoteModalOpen(false);
          setNoteTargetFund(null);
        }}
        prefill={{
          reasonSaved: noteTargetFund?.reasonSaved || `${currentObjective.title}: ${currentObjective.prefillReason}`,
          investigationQuestion: noteTargetFund?.investigationQuestion || customInvestorQuestion || currentObjective.prefillQuestion,
          nextStep: 'Verify downside semideviation and audit raw AMFI source artifacts.',
        }}
      />
    </PageContainer>
  );
}

function DiscoveryResultCard({
  result,
  isSaved,
  onOpenNote,
  objectiveTitle,
  suggestedQuestion,
}: {
  result: GoalDiscoveryResult;
  isSaved: boolean;
  onOpenNote: () => void;
  objectiveTitle?: string;
  suggestedQuestion?: string;
}) {
  const getBadgeStyle = (state: string) => {
    switch (state) {
      case 'ELIGIBLE':
        return 'bg-status-approved-bg text-status-approved border-status-approved/30';
      case 'PARTIALLY_EVALUATED':
        return 'bg-status-candidate-bg text-status-candidate border-status-candidate/30';
      case 'INSUFFICIENT_DATA':
        return 'bg-status-stale-bg text-status-stale border-status-stale/30';
      case 'NOT_ELIGIBLE':
        return 'bg-status-risk/10 text-status-risk border-status-risk/30';
      default:
        return 'bg-surface-navy-raised text-text-cream-muted border-border-gold-subtle';
    }
  };

  return (
    <div className="rounded-2xl border border-border-gold-subtle bg-surface-midnight/90 p-5 shadow-lg backdrop-blur-sm glow-gold transition-all hover:border-border-gold-bright/60">
      {/* Card Header */}
      <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-4 border-b border-border-gold-subtle/50 pb-4 mb-4">
        <div className="space-y-1 max-w-2xl">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="font-sans font-bold text-base text-text-cream-bright">
              {result.fundName}
            </h3>
            <span className={`font-mono text-[10px] px-2 py-0.5 rounded-full border font-bold uppercase tracking-wider ${getBadgeStyle(result.resultState)}`}>
              {result.resultState.replace('_', ' ')}
            </span>
            {isSaved && (
              <span className="font-mono text-[10px] px-2 py-0.5 rounded-full border border-primary/40 bg-surface-navy-raised text-primary font-bold">
                In Workspace ✓
              </span>
            )}
          </div>
          <p className="font-sans text-xs text-text-cream-body">
            {result.amcName} · {result.category} {result.subcategory ? `(${result.subcategory})` : ''} · Plan: <strong className="text-text-cream-bright">{result.planType}</strong> · Option: <strong className="text-text-cream-bright">{result.optionType}</strong>
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2 shrink-0">
          <div className="text-right font-mono text-[11px] text-text-cream-muted hidden sm:block">
            <span>AMFI: {result.amfiCode}</span>
            <span className="block text-[10px] text-text-cream-muted/70">ISIN: {result.isin}</span>
          </div>
        </div>
      </div>

      {/* Main Body */}
      <div className="space-y-4">
        {/* YUKIRA Analytical Quality Score Panel */}
        <div className="p-3.5 rounded-xl border border-border-gold-subtle/60 bg-surface-obsidian flex flex-wrap items-center justify-between gap-3 text-xs">
          <div className="flex items-center gap-3">
            <span className="font-mono text-[10.5px] uppercase tracking-wider text-text-cream-muted font-semibold">
              YUKIRA Quality Score:
            </span>
            {result.analyticalScore.available ? (
              <div className="flex items-center gap-2">
                <span className="font-mono text-base font-bold text-primary tabular">
                  {result.analyticalScore.scoreValue?.toFixed(1)} / 100
                </span>
                <span className="font-mono text-[10px] px-2 py-0.5 rounded border border-border-gold-subtle bg-surface-navy-raised text-text-cream-muted">
                  Confidence: {result.analyticalScore.confidence}
                </span>
                {result.analyticalScore.status && result.analyticalScore.status !== 'NO_SCORE' && (
                  <span className="font-mono text-[10px] px-2 py-0.5 rounded border border-status-candidate/40 bg-status-candidate-bg text-status-candidate font-bold">
                    {result.analyticalScore.status}
                  </span>
                )}
              </div>
            ) : (
              <span className="font-mono text-xs text-text-cream-muted">
                Score Unavailable · Candidate Baseline
              </span>
            )}
          </div>
          <div className="font-mono text-[11px] text-text-cream-muted/70">
            Engine v{result.analyticalScore.scoreVersion} · NAV Cutoff: {result.evidenceState.navAsOfDate}
          </div>
        </div>

        {/* Unified Evidence Quality Compact Strip */}
        <div className="flex flex-wrap items-center justify-between gap-2 p-2.5 rounded-lg bg-surface-obsidian/50 border border-border-gold-subtle/40 font-mono text-[11px]">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-text-cream-muted text-[10px] uppercase font-bold">Evidence As-Of:</span>
            <span className="text-text-cream-bright font-semibold">{result.evidenceState.navAsOfDate}</span>
            <span className={`status-badge ${result.evidenceState.navAsOfDate === '2024-01-15' ? 'state-candidate' : 'state-verified'} text-[9px]`}>
              {result.evidenceState.navAsOfDate === '2024-01-15' ? 'HISTORICAL BASELINE' : 'CURRENT'}
            </span>
            <span className="text-text-cream-muted">•</span>
            <span className="text-text-cream-muted text-[10px]">Quality:</span>
            <span className="text-text-cream-body">{result.evidenceState.dataQualitySummary}</span>
          </div>
          <Link
            href={`/funds/${result.schemeId}?option=${result.schemeOptionId}#data-quality`}
            className="text-primary hover:underline text-[10.5px] font-semibold"
          >
            Audit Evidence →
          </Link>
        </div>

        {/* Criteria Evaluation Matrix */}
        <div>
          <h4 className="font-mono text-[10.5px] uppercase tracking-wider text-text-cream-muted font-bold mb-2">
            Deterministic Criteria Breakdown (Why It Appears)
          </h4>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2.5">
            {Object.entries(result.criteria).map(([key, evalItem]) => {
              const isMatch = evalItem.state === 'MATCH' || evalItem.state === 'SUPPORTED' || evalItem.state === 'AVAILABLE';
              const isUnknown = evalItem.state === 'UNKNOWN' || evalItem.state === 'NOT_APPLICABLE';
              return (
                <div key={key} className="p-3 rounded-xl border border-border-gold-subtle/40 bg-surface-obsidian/70 text-xs space-y-1">
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-mono text-[11px] uppercase font-bold text-text-cream-bright capitalize">{key}</span>
                    <span
                      className={`font-mono text-[9.5px] px-1.5 py-0.5 rounded border font-bold ${
                        isMatch
                          ? 'bg-status-approved-bg text-status-approved border-status-approved/30'
                          : isUnknown
                          ? 'bg-status-candidate-bg text-status-candidate border-status-candidate/30'
                          : 'bg-status-risk/10 text-status-risk border-status-risk/30'
                      }`}
                    >
                      {evalItem.state}
                    </span>
                  </div>
                  <p className="text-[11px] leading-snug text-text-cream-muted font-sans">
                    {evalItem.explanation}
                  </p>
                </div>
              );
            })}
          </div>
        </div>

        {/* Structured Investigation Questions */}
        {result.investigationQuestions && result.investigationQuestions.length > 0 && (
          <div className="p-3.5 rounded-xl border border-border-gold-subtle/60 bg-surface-navy-raised/50 text-xs space-y-1.5">
            <span className="font-mono text-[11px] uppercase tracking-wider text-primary font-bold block">
              Diligence Questions for Allocator Investigation:
            </span>
            <ul className="list-disc list-inside space-y-1 text-text-cream-body text-xs font-sans">
              {result.investigationQuestions.map((q, idx) => (
                <li key={idx}>{q}</li>
              ))}
            </ul>
          </div>
        )}

        {/* Action Row: Real Multi-Workflow Transitions */}
        <div className="pt-3 border-t border-border-gold-subtle/50 flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-2">
            <Link
              href={`/funds/${result.schemeId}?option=${result.schemeOptionId}`}
              className="rounded-full bg-surface-navy-raised px-4 py-2 font-sans text-xs font-semibold text-text-cream-bright border border-border-gold-subtle hover:text-primary hover:border-border-gold-bright transition-all"
            >
              Fund Profile &rarr;
            </Link>

            <Link
              href={`/compare?funds=${result.schemeId}&options=${result.schemeId}:${result.schemeOptionId}`}
              className="rounded-full bg-surface-navy-raised px-4 py-2 font-sans text-xs font-semibold text-text-cream-bright border border-border-gold-subtle hover:text-primary hover:border-border-gold-bright transition-all"
              title="Open comparative screener with this candidate pre-selected"
            >
              Compare Candidate &rarr;
            </Link>

            <Link
              href={`/portfolio?addOption=${result.schemeOptionId}${
                objectiveTitle ? `&reason=${encodeURIComponent(objectiveTitle)}` : ''
              }${
                suggestedQuestion ? `&question=${encodeURIComponent(suggestedQuestion)}` : ''
              }`}
              className="rounded-full px-3 py-2 font-sans text-xs font-semibold text-text-cream-muted hover:text-primary transition-colors"
              title="Track this candidate in portfolio with pre-filled research objective and question"
            >
              Audit in Portfolio &rarr;
            </Link>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onOpenNote}
              className="flex items-center gap-1.5 rounded-full bg-surface-navy-raised px-4 py-2 font-sans text-xs font-semibold text-primary border border-border-gold-subtle hover:border-border-gold-bright transition-all"
            >
              <span>{isSaved ? 'Edit Research Note' : 'Save to Due Diligence'}</span>
              <span>✎</span>
            </button>

            {isSaved && (
              <Link
                href={`/watchlist#saved-${result.schemeOptionId}`}
                className="font-mono text-[11px] text-text-cream-muted hover:text-primary transition-colors underline"
                title="Inspect saved notes in Research Workspace"
              >
                View in Workspace &rarr;
              </Link>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

export default function GoalDiscoveryPage() {
  return (
    <Suspense fallback={<StateView kind="loading" title="Loading..." message="Initializing goal discovery..." />}>
      <GoalDiscoveryContent />
    </Suspense>
  );
}
