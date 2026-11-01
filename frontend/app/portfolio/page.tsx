'use client';

import React, { useState, useEffect, Suspense } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { PageContainer, SectionHeading } from '@/components/layout/PageContainer';
import { SchemeOption } from '@/types/domain';
import { fetchAllSchemeOptions } from '@/lib/api/schemes';
import { useUser } from '@auth0/nextjs-auth0/client';
import { ConfirmRemoveModal } from '@/components/portfolio/ConfirmRemoveModal';
import { EditHoldingModal } from '@/components/portfolio/EditHoldingModal';
import {
  fetchPortfolioSummary,
  addPortfolioHolding,
  removePortfolioHolding,
  PortfolioSummaryDto,
  PortfolioHoldingDto
} from '@/lib/api/portfolio';
import { formatCurrency, formatPercentage } from '@/lib/utils/formatters';
import { PortfolioGoalAlignmentView } from '@/components/portfolio/PortfolioGoalAlignmentView';
import { AddHoldingModal } from '@/components/portfolio/AddHoldingModal';
import { PortfolioReportModal } from '@/components/portfolio/PortfolioReportView';
import { PortfolioDriftView } from '@/components/portfolio/PortfolioDriftView';
import { PortfolioComparisonView } from '@/components/portfolio/PortfolioComparisonView';
import { PortfolioScoreHistoryView } from '@/components/portfolio/PortfolioScoreHistoryView';
import { GroundedAiInterpretationPanel } from '@/components/analysis/GroundedAiInterpretationPanel';
import { DataFreshnessIndicator } from '@/components/portfolio/DataFreshnessIndicator';
import { PortfolioIntelligenceSection } from '@/components/portfolio/PortfolioIntelligenceSection';
import { PortfolioRiskDiversificationSection } from '@/components/portfolio/PortfolioRiskDiversificationSection';
import { PortfolioMonitoringPanel } from '@/components/monitoring/PortfolioMonitoringPanel';
import { TransactionsAndPerformanceSection } from '@/components/portfolio/TransactionsAndPerformanceSection';
import { CasImportModal } from '@/components/portfolio/CasImportModal';
import { ResearchPortfolioView } from '@/components/portfolio/ResearchPortfolioView';
import { EvidenceQualitySummary } from '@/components/epistemic/EvidenceQualitySummary';
import { PortfolioCashFlowTimeline } from '@/components/portfolio/PortfolioCashFlowTimeline';
import { FifoTaxLotsAndDriftSection } from '@/components/portfolio/FifoTaxLotsAndDriftSection';

function PortfolioPageContent() {
  const { user } = useUser();
  const searchParams = useSearchParams();
  const tabParam = searchParams.get('tab') || searchParams.get('view');
  const [userSelectedMode, setUserSelectedMode] = useState<'holdings' | 'transactions' | 'research' | 'timeline' | 'accounting' | null>(null);
  const portfolioMode = userSelectedMode ?? (tabParam === 'transactions' ? 'transactions' : tabParam === 'research' ? 'research' : tabParam === 'timeline' ? 'timeline' : tabParam === 'accounting' || tabParam === 'lots' ? 'accounting' : 'holdings');
  const setPortfolioMode = (mode: 'holdings' | 'transactions' | 'research' | 'timeline' | 'accounting') => setUserSelectedMode(mode);

  const [summary, setSummary] = useState<PortfolioSummaryDto | null>(null);
  const [options, setOptions] = useState<SchemeOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isImportModalOpen, setIsImportModalOpen] = useState(false);
  const [isReportModalOpen, setIsReportModalOpen] = useState(false);
  const [isCasModalOpen, setIsCasModalOpen] = useState(false);

  // Edit & explicit removal modal state
  const [holdingToEdit, setHoldingToEdit] = useState<PortfolioHoldingDto | null>(null);
  const [holdingToRemove, setHoldingToRemove] = useState<PortfolioHoldingDto | null>(null);
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [isRemoveModalOpen, setIsRemoveModalOpen] = useState(false);

  // Form state
  const [selectedOptionId, setSelectedOptionId] = useState<string>('');
  const [unitsInput, setUnitsInput] = useState<string>('');
  const [costBasisInput, setCostBasisInput] = useState<string>('');
  const [reasonSavedInput, setReasonSavedInput] = useState<string>('');
  const [questionInput, setQuestionInput] = useState<string>('');
  const [nextStepInput, setNextStepInput] = useState<string>('');

  useEffect(() => {
    let ignore = false;
    async function init() {
      try {
        setLoading(true);
        setError(null);
        const [sumData, optData] = await Promise.all([
          fetchPortfolioSummary(),
          fetchAllSchemeOptions()
        ]);
        if (!ignore) {
          setSummary(sumData);
          setOptions(optData);
          setLoading(false);
        }
      } catch (err: unknown) {
        if (!ignore) {
          setError((err as Error).message || 'Failed to load portfolio data');
          setLoading(false);
        }
      }
    }
    init();
    return () => {
      ignore = true;
    };
  }, []);

  // Auto-open Add Holding modal if addOption query param is present, prefilling research context
  useEffect(() => {
    const addOptionParam = searchParams.get('addOption');
    if (addOptionParam) {
      setSelectedOptionId(addOptionParam);
      const reasonParam = searchParams.get('reason');
      const questionParam = searchParams.get('question');
      const nextStepParam = searchParams.get('nextStep');
      if (reasonParam) setReasonSavedInput(reasonParam);
      if (questionParam) setQuestionInput(questionParam);
      if (nextStepParam) setNextStepInput(nextStepParam);
      setIsImportModalOpen(true);
    }
  }, [searchParams]);

  // Smooth scroll to target holding if hash is provided (e.g. /portfolio#holding-118955)
  useEffect(() => {
    if (!loading && typeof window !== 'undefined' && window.location.hash) {
      const targetId = window.location.hash.slice(1);
      const el = document.getElementById(targetId);
      if (el) {
        setTimeout(() => {
          el.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }, 100);
      }
    }
  }, [loading]);

  const savedResearchCount = React.useMemo(() => {
    if (!summary?.holdings) return 0;
    return summary.holdings.filter((h) => Boolean(h.isSavedInWatchlist)).length;
  }, [summary?.holdings]);

  const completeHoldingsCount = React.useMemo(() => {
    if (!summary?.holdings) return 0;
    return summary.holdings.filter((h) => h.holdingCompletenessStatus === 'COMPLETE').length;
  }, [summary?.holdings]);

  const handleAddHolding = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedOptionId || !unitsInput) return;

    try {
      setSubmitting(true);
      setError(null);
      const optionId = parseInt(selectedOptionId, 10);
      const units = parseFloat(unitsInput);
      const costBasis = costBasisInput ? parseFloat(costBasisInput) : undefined;

      await addPortfolioHolding(
        optionId,
        units,
        costBasis,
        reasonSavedInput.trim() || undefined,
        questionInput.trim() || undefined,
        nextStepInput.trim() || undefined
      );

      // Reset form
      setSelectedOptionId('');
      setUnitsInput('');
      setCostBasisInput('');
      setReasonSavedInput('');
      setQuestionInput('');
      setNextStepInput('');

      // Reload summary
      const updatedSummary = await fetchPortfolioSummary();
      setSummary(updatedSummary);
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to add holding');
    } finally {
      setSubmitting(false);
    }
  };

  const confirmRemoveHolding = async () => {
    if (!holdingToRemove) return;
    try {
      setSubmitting(true);
      setError(null);
      await removePortfolioHolding(holdingToRemove.schemeOptionId);
      setIsRemoveModalOpen(false);
      setHoldingToRemove(null);
      const updatedSummary = await fetchPortfolioSummary();
      setSummary(updatedSummary);
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to remove holding');
    } finally {
      setSubmitting(false);
    }
  };

  const conc = summary?.concentrationAnalysis;
  const pScore = summary?.portfolioAnalyticalScore;

  return (
    <PageContainer
      title="My Portfolio"
      subtitle="Track externally held mutual funds with deterministic valuation, portfolio quality aggregation, and analytical evidence."
    >
      {/* Portfolio Main Mode Selector */}
      <div className="mb-6 flex border-b border-border">
        <button
          onClick={() => setPortfolioMode('holdings')}
          className={`flex items-center gap-2 px-4 py-2.5 font-mono text-[12px] font-semibold border-b-2 transition-colors ${
            portfolioMode === 'holdings'
              ? 'border-accent text-accent'
              : 'border-transparent text-text-tertiary hover:text-text-primary'
          }`}
        >
          <span>📋 Tracked External Holdings</span>
        </button>
        <button
          onClick={() => setPortfolioMode('transactions')}
          className={`flex items-center gap-2 px-4 py-2.5 font-mono text-[12px] font-semibold border-b-2 transition-colors ${
            portfolioMode === 'transactions'
              ? 'border-accent text-accent'
              : 'border-transparent text-text-tertiary hover:text-text-primary'
          }`}
        >
          <span>📈 Transactions & Performance (XIRR)</span>
          <span className="status-badge state-verified text-[9px]">DETERMINISTIC</span>
        </button>
        <button
          onClick={() => setPortfolioMode('research')}
          className={`flex items-center gap-2 px-4 py-2.5 font-mono text-[12px] font-semibold border-b-2 transition-colors ${
            portfolioMode === 'research'
              ? 'border-accent text-accent'
              : 'border-transparent text-text-tertiary hover:text-text-primary'
          }`}
        >
          <span>🔬 Multi-Fund Look-Through & Research</span>
          <span className="status-badge state-verified text-[9px]">INTERACTIVE</span>
        </button>
        <button
          onClick={() => setPortfolioMode('timeline')}
          className={`flex items-center gap-2 px-4 py-2.5 font-mono text-[12px] font-semibold border-b-2 transition-colors ${
            portfolioMode === 'timeline'
              ? 'border-accent text-accent'
              : 'border-transparent text-text-tertiary hover:text-text-primary'
          }`}
        >
          <span>⏱️ Cash Flow Timeline & Snapshots</span>
          <span className="status-badge state-verified text-[9px]">PIT HISTORICAL</span>
        </button>
        <button
          onClick={() => setPortfolioMode('accounting')}
          className={`flex items-center gap-2 px-4 py-2.5 font-mono text-[12px] font-semibold border-b-2 transition-colors ${
            portfolioMode === 'accounting'
              ? 'border-accent text-accent'
              : 'border-transparent text-text-tertiary hover:text-text-primary'
          }`}
        >
          <span>🧾 FIFO Tax Lots &amp; Drift</span>
          <span className="status-badge state-verified text-[9px]">TAX ACCOUNTING</span>
        </button>
      </div>

      {portfolioMode === 'accounting' ? (
        <FifoTaxLotsAndDriftSection />
      ) : portfolioMode === 'research' ? (
        <ResearchPortfolioView />
      ) : portfolioMode === 'transactions' ? (
        <div className="space-y-8">
          <TransactionsAndPerformanceSection
            holdings={summary?.holdings || []}
            availableOptions={options}
            onDataChanged={async () => {
              const updated = await fetchPortfolioSummary();
              setSummary(updated);
            }}
          />
          <FifoTaxLotsAndDriftSection />
        </div>
      ) : portfolioMode === 'timeline' ? (
        <PortfolioCashFlowTimeline />
      ) : (
        <>
          {/* Portfolio Quick Actions Header */}
          <div className="mb-6 flex flex-wrap items-center justify-between gap-3 border-b border-border pb-4">
            <div>
              <div className="flex items-center gap-3">
                <span className="font-mono text-[11px] text-text-tertiary">
                  PORTFOLIO HOLDINGS INGESTION V1
                </span>
            <DataFreshnessIndicator />
          </div>
          <p className="font-sans text-[13px] font-medium text-text-secondary">
            Ingest external mutual fund holdings by exact scheme_option_id
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <button
            onClick={() => setIsReportModalOpen(true)}
            className="px-4 py-2 rounded bg-surface border border-border text-text-primary font-mono text-[12px] font-medium hover:bg-surface-hover flex items-center gap-2"
          >
            <svg className="w-4 h-4 text-accent" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
            <span>Export Evidence Report</span>
          </button>

          <button
            onClick={() => setIsCasModalOpen(true)}
            className="px-4 py-2 rounded bg-cyan-600 text-white font-mono text-[12px] font-semibold hover:bg-cyan-500 flex items-center gap-2 shadow"
          >
            <span>📥 Import CAS Portfolio</span>
          </button>

          <button
            onClick={() => setIsImportModalOpen(true)}
            className="px-4 py-2 rounded bg-accent text-surface-dark font-mono text-[12px] font-medium hover:bg-accent/90 flex items-center gap-2"
          >
            <span>+ Add Holding</span>
          </button>
        </div>
      </div>

      <PortfolioReportModal
        isOpen={isReportModalOpen}
        onClose={() => setIsReportModalOpen(false)}
      />

      <CasImportModal
        isOpen={isCasModalOpen}
        onClose={() => setIsCasModalOpen(false)}
        onSuccess={async () => {
          const updated = await fetchPortfolioSummary();
          setSummary(updated);
        }}
      />

      <AddHoldingModal
        isOpen={isImportModalOpen}
        onClose={() => setIsImportModalOpen(false)}
        existingHoldings={summary?.holdings || []}
        onSuccess={async () => {
          const updated = await fetchPortfolioSummary();
          setSummary(updated);
        }}
      />

      <ConfirmRemoveModal
        isOpen={isRemoveModalOpen}
        onClose={() => {
          setIsRemoveModalOpen(false);
          setHoldingToRemove(null);
        }}
        onConfirm={confirmRemoveHolding}
        holding={holdingToRemove}
        submitting={submitting}
      />

      <EditHoldingModal
        isOpen={isEditModalOpen}
        onClose={() => {
          setIsEditModalOpen(false);
          setHoldingToEdit(null);
        }}
        onSuccess={async () => {
          const updated = await fetchPortfolioSummary();
          setSummary(updated);
        }}
        holding={holdingToEdit}
      />

      {/* Investor Workspace Identity & Tenant Isolation Banner */}
      <div className="mb-6 panel-inset p-3 bg-surface border border-border/80 flex flex-wrap items-center justify-between gap-3 text-[12px] font-mono">
        <div className="flex items-center gap-2 text-text-secondary">
          <span className="text-accent">●</span>
          <span>Tenant Workspace:</span>
          {user ? (
            <span className="text-text-primary font-semibold">
              {user.email || user.name || 'Private Investor Portfolio'}
            </span>
          ) : (
            <span className="text-text-tertiary">
              Standard Account Portfolio Session (Isolated)
            </span>
          )}
        </div>
        <div className="flex items-center gap-3 text-text-tertiary text-[11px]">
          <span>Authority: <strong>Deterministic (nav_observation ledger)</strong></span>
          <span>•</span>
          <span>Capacity: <strong>Entitlement-enforced</strong></span>
        </div>
      </div>

      {/* Research & Data Completeness Integration Strip */}
      <div className="mb-6 panel p-3.5 bg-surface-raised/40 border border-border flex flex-col md:flex-row md:items-center justify-between gap-3 text-xs font-mono">
        <div className="flex items-start gap-2.5">
          <span className="text-accent text-sm">🔬</span>
          <div className="space-y-0.5">
            <span className="font-semibold text-text-primary">Research &amp; Holding Data Completeness Integration:</span>
            <p className="text-text-secondary leading-relaxed font-sans text-[12px]">
              {savedResearchCount} of {summary?.totalHoldings ?? 0} holdings have matching entries in your Investor Research Workspace. {completeHoldingsCount} of {summary?.totalHoldings ?? 0} positions have complete valuation and cost-basis data.
            </p>
          </div>
        </div>
        <Link
          href="/watchlist"
          className="btn btn-secondary btn-sm shrink-0 whitespace-nowrap self-start md:self-auto text-accent border-accent/30 hover:bg-accent/10"
          title="Open Investor Research Workspace to review saved due-diligence hypotheses"
        >
          <span>Open Research Workspace (Watchlist) →</span>
        </Link>
      </div>

      {/* Overview Top Metric Cards */}
      <div className="mb-6 grid grid-cols-1 gap-3 sm:grid-cols-5">
        <div className="metric-tile">
          <span className="eyebrow">Holdings & Schemes</span>
          <div className="metric-rule" />
          <div className="flex items-baseline gap-2">
            <p className="data-value-lg">{summary ? summary.totalHoldings : 0}</p>
            {summary && summary.distinctSchemeOptionsCount !== undefined && (
              <span className="font-mono text-[11px] text-text-tertiary">
                ({summary.distinctSchemeOptionsCount} schemes)
              </span>
            )}
          </div>
          <p className="mt-auto pt-2 font-mono text-[11px] text-text-tertiary">
            Exact mutual-fund scheme options
          </p>
        </div>

        <div className="metric-tile">
          <span className="eyebrow">Current Market Value</span>
          <div className="metric-rule" />
          {summary && summary.totalCurrentMarketValue !== null && summary.totalCurrentMarketValue !== undefined ? (
            <p className="data-value-lg text-emerald-400">{formatCurrency(summary.totalCurrentMarketValue)}</p>
          ) : (
            <div className="flex items-center gap-2">
              <span className="data-value-lg text-text-disabled">—</span>
              <span className="status-badge state-candidate text-[9px]">VALUATION STALE</span>
            </div>
          )}
          <p className="mt-auto pt-2 font-mono text-[11px] text-text-tertiary">
            {summary && summary.totalCurrentMarketValue !== null && summary.totalCurrentMarketValue !== undefined
              ? 'Valued with fresh observations (≤ 3d lag)'
              : 'Zero positions meet 3-day freshness threshold'}
          </p>
        </div>

        <div className="metric-tile">
          <span className="eyebrow">Last-Known Valuation</span>
          <div className="metric-rule" />
          {summary && (summary.totalLastKnownValue !== null || summary.totalAvailableValue !== null) ? (
            <div className="flex items-baseline gap-2">
              <p className="data-value-lg text-amber-300">
                {formatCurrency(summary.totalLastKnownValue ?? summary.totalAvailableValue ?? 0)}
              </p>
              <span className="status-badge state-candidate text-[9px]">LAST KNOWN</span>
            </div>
          ) : (
            <p className="data-value-lg data-unavailable">Valuation Unavailable</p>
          )}
          <p className="mt-auto pt-2 font-mono text-[11px] text-text-tertiary">
            As of {summary?.valuationAsOfDate || 'historical NAV cutoff'}
          </p>
        </div>

        <div className="metric-tile">
          <span className="eyebrow">Freshness Breakdown</span>
          <div className="metric-rule" />
          <div className="flex items-center gap-1.5 font-mono text-[12px] font-semibold">
            <span className="text-emerald-400">{summary?.currentValuedHoldingsCount ?? 0} Fresh</span>
            <span className="text-text-tertiary">/</span>
            <span className="text-amber-300">{summary?.staleValuedHoldingsCount ?? (summary?.valuedHoldingsCount ?? 0)} Stale</span>
            <span className="text-text-tertiary">/</span>
            <span className="text-rose-400">{summary?.unavailableHoldingsCount ?? (summary ? summary.totalHoldings - summary.valuedHoldingsCount : 0)} Unvalued</span>
          </div>
          <div className="mt-1">
            {summary && (
              <span className={`status-badge text-[10px] ${
                summary.valuationFreshnessState === 'PORTFOLIO_CURRENT' ? 'state-verified' :
                summary.valuationFreshnessState === 'PORTFOLIO_STALE' ? 'state-candidate' : 'state-unavailable'
              }`}>
                {summary.valuationFreshnessState ?? 'PORTFOLIO_UNVALUED'}
              </span>
            )}
          </div>
          <p className="mt-auto pt-2 font-mono text-[11px] text-text-tertiary">
            AMFI daily reporting freshness policy (3d)
          </p>
        </div>

        <div className="metric-tile">
          <span className="eyebrow">Portfolio Analytical Score</span>
          <div className="metric-rule" />
          <div className="flex items-center gap-2">
            {pScore && pScore.portfolioScore !== null && pScore.portfolioScore !== undefined ? (
              <span className="data-value-lg font-mono text-accent">
                {pScore.portfolioScore.toFixed(2)}
              </span>
            ) : (
              <span className="data-value-lg data-unavailable">—</span>
            )}
            {pScore && (
              <span className={`status-badge text-[10px] ${
                pScore.scoreState === 'COMPLETE' ? 'state-verified' :
                pScore.scoreState === 'PARTIAL' ? 'state-candidate' : 'state-unavailable'
              }`}>
                {pScore.scoreState}
              </span>
            )}
          </div>
          <p className="mt-auto pt-2 font-mono text-[11px] text-text-tertiary">
            {pScore?.coveredPortfolioWeight !== null && pScore?.coveredPortfolioWeight !== undefined
              ? `${pScore.coveredPortfolioWeight.toFixed(2)}% exposure covered`
              : 'Exposure-weighted YUKIRA aggregation'}
          </p>
        </div>
      </div>

      {/* Unified Portfolio Evidence Quality, Freshness & Valuation Center */}
      {summary && (
        <div className="mb-6">
          <EvidenceQualitySummary
            variant="panel"
            title="Portfolio Valuation Freshness, Coverage & Asset Limitations"
            subtitle={`${summary.totalHoldings} Tracked Positions · ${summary.distinctSchemeOptionsCount ?? summary.totalHoldings} Registered Schemes`}
            contextName="Tracked Investor Portfolio"
            asOfDate={summary.valuationAsOfDate || '2024-01-15'}
            freshnessState={
              summary.valuationFreshnessState === 'PORTFOLIO_CURRENT'
                ? 'CURRENT'
                : summary.valuationFreshnessState === 'PORTFOLIO_STALE'
                ? 'STALE'
                : 'UNAVAILABLE'
            }
            freshnessPolicy="AMFI Daily NAV Cadence Policy (≤ 3 calendar days lag)"
            observationCount={summary.totalHoldings}
            coverageState={
              summary.valuationCoverageState === 'VALUATION_COMPLETE'
                ? 'COMPLETE'
                : summary.valuationCoverageState === 'VALUATION_PARTIAL'
                ? 'PARTIAL'
                : 'INSUFFICIENT'
            }
            coverageNote={`${summary.currentValuedHoldingsCount ?? 0} Fresh, ${summary.staleValuedHoldingsCount ?? summary.valuedHoldingsCount} Stale (Last Known), ${summary.unavailableHoldingsCount ?? 0} Unvalued.`}
            completenessState={
              summary.valuationCoverageState === 'VALUATION_COMPLETE'
                ? 'COMPLETE'
                : 'PARTIAL'
            }
            completenessNote={
              summary.valuationCoverageMessage ||
              `${summary.valuedHoldingsCount} of ${summary.totalHoldings} holdings have recorded NAV ledger observations. Unvalued positions are excluded from totals rather than guessed.`
            }
            methodologyStatus="APPROVED"
            methodologyVersion="Deterministic Portfolio Valuation Ledger (Units × Realized NAV)"
            interpretationConsequence={
              summary.valuationFreshnessState === 'PORTFOLIO_STALE' || summary.valuationAsOfDate === '2024-01-15' ? (
                <span>
                  <strong>Valuation Freshness Limitation:</strong> Holdings are evaluated at last-known historical NAV observations ({summary.valuationAsOfDate || '2024-01-15'}). Under AMFI rules, observations older than 3 days are classified as <strong>STALE</strong>. The total of <strong>{formatCurrency(summary.totalLastKnownValue ?? summary.totalAvailableValue ?? 0)}</strong> is a mathematically exact historical calculation, but it <strong>MUST NOT</strong> be described or treated as current market equity value.
                </span>
              ) : summary.valuationFreshnessState === 'PORTFOLIO_CURRENT' ? (
                <span>
                  All holdings are valued with fresh NAV observations meeting the strict AMFI 3-day freshness threshold.
                </span>
              ) : (
                <span>
                  NAV observations are missing for some external holdings. Total portfolio value reflects only valued positions; no values are approximated.
                </span>
              )
            }
            nextStepLabel="Inspect Data Quality & Cadence in Evidence Explorer"
            nextStepHref="/funds/1#data-quality"
          />
        </div>
      )}

      {/* Valuation Coverage Notice Banners */}
      {summary && summary.valuationCoverageState === 'VALUATION_PARTIAL' && (
        <div className="mb-6 panel-inset p-3.5 bg-amber-500/10 border border-amber-500/30 flex items-start gap-3">
          <span className="text-amber-400 text-base leading-none mt-0.5">⚠️</span>
          <div className="space-y-1">
            <h4 className="text-[12px] font-semibold text-amber-300 font-mono">
              Partial Valuation Notice
            </h4>
            <p className="text-[12px] text-text-secondary leading-relaxed">
              {summary.valuationCoverageMessage || `${summary.valuedHoldingsCount} of ${summary.totalHoldings} holdings have authoritative NAV data. Total portfolio value and allocation calculations reflect ONLY the valued positions.`}
            </p>
          </div>
        </div>
      )}

      {summary && summary.valuationCoverageState === 'VALUATION_UNAVAILABLE' && summary.totalHoldings > 0 && (
        <div className="mb-6 panel-inset p-3.5 bg-rose-500/10 border border-rose-500/30 flex items-start gap-3">
          <span className="text-rose-400 text-base leading-none mt-0.5">ℹ️</span>
          <div className="space-y-1">
            <h4 className="text-[12px] font-semibold text-rose-300 font-mono">
              Valuation Unavailable
            </h4>
            <p className="text-[12px] text-text-secondary leading-relaxed">
              {summary.valuationCoverageMessage || 'None of the tracked holdings currently have NAV observations recorded in the database. Market value cannot be calculated deterministically.'}
            </p>
          </div>
        </div>
      )}

      {summary && summary.holdings.length > 0 && (
        <div className="space-y-6 mb-6">
          <GroundedAiInterpretationPanel isPortfolio />
          <PortfolioScoreHistoryView />
        </div>
      )}

      {error && (
        <div className="state-panel-error mb-6">
          <span className="font-mono text-[12px]">Error: {error}</span>
        </div>
      )}

      {/* Main Grid Section */}
      <div className="grid grid-cols-1 gap-8 lg:grid-cols-3">
        {/* Holdings & Analytics Left Column */}
        <div className="space-y-6 lg:col-span-2">

          {/* Portfolio Analytical Score V1 Drill-Down Section */}
          {summary && summary.holdings.length > 0 && pScore && (
            <div className="panel p-4 space-y-4 border-l-4 border-l-accent">
              <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border pb-3">
                <div>
                  <h3 className="text-[14px] font-semibold text-text-primary flex items-center gap-2">
                    <span>Portfolio Analytical Score V1</span>
                    <span className="status-badge state-verified text-[10px]">EXPOSURE-WEIGHTED AGGREGATION</span>
                  </h3>
                  <p className="font-mono text-[11px] text-text-tertiary mt-0.5">
                    Aggregated analytical quality score derived from existing fund-level YUKIRA Analytical Scores weighted by portfolio exposure.
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <span className={`status-badge ${
                    pScore.scoreState === 'COMPLETE' ? 'state-verified' :
                    pScore.scoreState === 'PARTIAL' ? 'state-candidate' : 'state-unavailable'
                  }`}>
                    {pScore.scoreState} COVERAGE
                  </span>
                  <span className="mono-meta text-[11px]">As of {pScore.asOfDate || '2024-01-15'}</span>
                </div>
              </div>

              {/* Headline Score & Coverage summary stats */}
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-3 font-mono text-[11px]">
                <div className="panel-inset p-3 bg-surface">
                  <span className="text-text-muted text-[10px] block">Headline Portfolio Score</span>
                  <div className="flex items-baseline gap-1.5 mt-1">
                    <span className="text-2xl font-bold text-accent">
                      {pScore.portfolioScore !== null && pScore.portfolioScore !== undefined
                        ? pScore.portfolioScore.toFixed(2)
                        : '—'}
                    </span>
                    <span className="text-text-tertiary text-[11px]">/ 100</span>
                  </div>
                  <p className="text-text-tertiary text-[10px] mt-1">
                    {pScore.coveredHoldingCount} of {pScore.totalHoldingCount} holdings contributing
                  </p>
                </div>

                <div className="panel-inset p-3 bg-surface">
                  <span className="text-text-muted text-[10px] block">Portfolio Score Coverage</span>
                  <div className="flex items-baseline gap-1.5 mt-1">
                    <span className="text-xl font-bold text-text-primary">
                      {pScore.coveredPortfolioWeight !== null && pScore.coveredPortfolioWeight !== undefined
                        ? `${pScore.coveredPortfolioWeight.toFixed(2)}%`
                        : '0%'}
                    </span>
                  </div>
                  <p className="text-text-tertiary text-[10px] mt-1">
                    {pScore.coveredPortfolioValue !== null ? formatCurrency(pScore.coveredPortfolioValue) : 'â‚¹0'} covered exposure
                  </p>
                </div>

                <div className="panel-inset p-3 bg-surface">
                  <span className="text-text-muted text-[10px] block">Methodology & Status</span>
                  <p className="font-semibold text-text-primary text-[12px] mt-1">
                    {pScore.scoreVersion}
                  </p>
                  <span className="status-badge state-candidate text-[9px] mt-1">
                    {pScore.methodologyStatus} METHODOLOGY
                  </span>
                </div>
              </div>

              {/* Contributing Holdings Drill-down Table */}
              {pScore.contributingHoldings && pScore.contributingHoldings.length > 0 && (
                <div className="space-y-2 pt-2">
                  <h4 className="text-[12px] font-semibold text-text-secondary flex items-center justify-between">
                    <span>Contributing Fund Scores ({pScore.contributingHoldings.length})</span>
                    <span className="font-mono text-[10px] font-normal text-text-tertiary">How this score is formed</span>
                  </h4>

                  <div className="overflow-x-auto rounded border border-border">
                    <table className="w-full text-left font-mono text-[11px]">
                      <thead className="bg-surface-raised text-text-muted text-[10px] border-b border-border">
                        <tr>
                          <th className="py-2 px-3">Fund Option</th>
                          <th className="py-2 px-3 text-right">Fund Score</th>
                          <th className="py-2 px-3 text-right">Portfolio Share</th>
                          <th className="py-2 px-3 text-right">Score Weight</th>
                          <th className="py-2 px-3 text-right">Score Contribution</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-border bg-surface">
                        {pScore.contributingHoldings.map(c => (
                          <tr key={c.schemeOptionId} className="hover:bg-surface-raised/50">
                            <td className="py-2 px-3 font-sans font-medium text-text-primary">
                              <Link href={`/funds/${c.schemeOptionId}`} className="hover:text-accent">
                                {c.fundName}
                              </Link>
                              <span className="block font-mono text-[10px] text-text-tertiary">As of {c.scoreAsOfDate}</span>
                            </td>
                            <td className="py-2 px-3 text-right font-semibold text-accent">
                              {c.fundScore.toFixed(2)}
                            </td>
                            <td className="py-2 px-3 text-right text-text-secondary">
                              {c.portfolioWeight !== null ? `${c.portfolioWeight.toFixed(2)}%` : '—'}
                            </td>
                            <td className="py-2 px-3 text-right text-text-secondary">
                              {c.normalizedScoreWeight.toFixed(2)}%
                            </td>
                            <td className="py-2 px-3 text-right font-semibold text-text-primary">
                              +{c.scoreContribution.toFixed(2)}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}

              {/* Excluded Holdings Breakdown */}
              {pScore.excludedHoldings && pScore.excludedHoldings.length > 0 && (
                <div className="space-y-2 pt-2 border-t border-border">
                  <h4 className="text-[12px] font-semibold text-text-secondary flex items-center justify-between">
                    <span>Excluded Holdings ({pScore.excludedHoldings.length})</span>
                    <span className="font-mono text-[10px] font-normal text-text-tertiary">Reason for non-contribution</span>
                  </h4>
                  <div className="space-y-1.5 font-mono text-[11px]">
                    {pScore.excludedHoldings.map(ex => (
                      <div key={ex.schemeOptionId} className="flex items-center justify-between panel-inset p-2 bg-surface">
                        <span className="font-sans font-medium text-text-secondary">{ex.fundName}</span>
                        <div className="flex items-center gap-2">
                          {ex.portfolioWeight !== null && (
                            <span className="text-text-tertiary">{ex.portfolioWeight.toFixed(2)}% share</span>
                          )}
                          <span className="status-badge state-candidate text-[9px]">
                            {ex.exclusionReason === 'NO_VALUATION' ? 'NAV Valuation Unavailable' :
                             ex.exclusionReason === 'NO_FUND_SCORE' ? 'Score Snapshot Unavailable' :
                             ex.exclusionReason === 'INSUFFICIENT_SCORE_DATA' ? 'Insufficient Observation History' :
                             ex.exclusionReason === 'NOT_APPLICABLE' ? 'Not Evaluated under YUKIRA_SCORE_V1' : ex.exclusionReason}
                          </span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Portfolio Risk & Quality Evidence Drill-Down V1 Section */}
          {summary && summary.holdings.length > 0 && summary.portfolioRiskEvidence && (
            <div className="panel p-4 space-y-4 border-l-4 border-l-blue-500">
              <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border pb-3">
                <div>
                  <h3 className="text-[14px] font-semibold text-text-primary flex items-center gap-2">
                    <span>Portfolio Risk & Quality Evidence Drill-Down V1</span>
                    <span className="status-badge state-verified text-[10px]">FUND-LEVEL RISK EVIDENCE</span>
                  </h3>
                  <p className="font-mono text-[11px] text-text-tertiary mt-0.5">
                    Factual risk and quality characteristics of held mutual funds. Exposes canonical fund-level metrics without fabricating a composite portfolio risk score.
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <span className={`status-badge ${
                    summary.portfolioRiskEvidence.coverageState === 'COMPLETE' ? 'state-verified' :
                    summary.portfolioRiskEvidence.coverageState === 'PARTIAL' ? 'state-candidate' : 'state-unavailable'
                  }`}>
                    {summary.portfolioRiskEvidence.coverageState} RISK COVERAGE
                  </span>
                </div>
              </div>

              {/* Risk Data Coverage Metric Cards */}
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 font-mono text-[11px]">
                <div className="panel-inset p-2.5 bg-surface">
                  <span className="text-text-muted text-[10px] block">Covered Exposure</span>
                  <p className="font-semibold text-text-primary text-[13px] mt-1">
                    {summary.portfolioRiskEvidence.holdingsWithRiskEvidenceCount} of {summary.portfolioRiskEvidence.totalHoldingCount} Holdings
                  </p>
                  <p className="text-accent text-[12px] font-bold mt-0.5">
                    {summary.portfolioRiskEvidence.coveredPortfolioWeight !== null
                      ? `${summary.portfolioRiskEvidence.coveredPortfolioWeight.toFixed(2)}% exposure`
                      : '0%'}
                  </p>
                </div>

                <div className="panel-inset p-2.5 bg-surface">
                  <span className="text-text-muted text-[10px] block">Volatility Coverage</span>
                  <p className="font-semibold text-text-primary text-[13px] mt-1">
                    {summary.portfolioRiskEvidence.volatilityCoverageCount} of {summary.portfolioRiskEvidence.totalHoldingCount} Funds
                  </p>
                  <p className="text-text-tertiary text-[10px] mt-0.5">3Y Annualized Volatility</p>
                </div>

                <div className="panel-inset p-2.5 bg-surface">
                  <span className="text-text-muted text-[10px] block">Max Drawdown Coverage</span>
                  <p className="font-semibold text-text-primary text-[13px] mt-1">
                    {summary.portfolioRiskEvidence.drawdownCoverageCount} of {summary.portfolioRiskEvidence.totalHoldingCount} Funds
                  </p>
                  <p className="text-text-tertiary text-[10px] mt-0.5">3Y Peak-to-Trough Decline</p>
                </div>

                <div className="panel-inset p-2.5 bg-surface">
                  <span className="text-text-muted text-[10px] block">Beta & Downside Beta</span>
                  <p className="font-semibold text-text-primary text-[13px] mt-1">
                    {summary.portfolioRiskEvidence.betaCoverageCount} of {summary.portfolioRiskEvidence.totalHoldingCount} Funds
                  </p>
                  <p className="text-text-tertiary text-[10px] mt-0.5">Benchmark Co-Movement</p>
                </div>
              </div>

              {/* Holding Risk Evidence List */}
              {summary.portfolioRiskEvidence.holdingRiskEvidences && summary.portfolioRiskEvidence.holdingRiskEvidences.length > 0 && (
                <div className="space-y-3 pt-2">
                  <h4 className="text-[12px] font-semibold text-text-secondary flex items-center justify-between">
                    <span>Holding-Level Risk & Quality Breakdown</span>
                    <span className="font-mono text-[10px] font-normal text-text-tertiary">Canonical persisted metrics</span>
                  </h4>

                  <div className="space-y-3 font-mono text-[11px]">
                    {summary.portfolioRiskEvidence.holdingRiskEvidences.map(hEv => (
                      <div key={hEv.schemeOptionId} className="panel-inset p-3 bg-surface space-y-2 border border-border">
                        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border pb-2">
                          <div className="flex items-center gap-2">
                            <Link href={`/funds/${hEv.schemeOptionId}`} className="font-sans font-semibold text-[13px] text-text-primary hover:text-accent">
                              {hEv.fundName}
                            </Link>
                            {hEv.portfolioWeight !== null && (
                              <span className="status-badge state-verified text-[10px]">
                                {hEv.portfolioWeight.toFixed(2)}% SHARE
                              </span>
                            )}
                          </div>
                          <div className="flex items-center gap-2">
                            <span className="text-text-tertiary text-[10px]">YUKIRA Score:</span>
                            <span className="font-bold text-accent">
                              {hEv.yukiraScore !== null ? hEv.yukiraScore.toFixed(2) : '—'}
                            </span>
                            <span className="status-badge state-candidate text-[9px]">{hEv.scoreStatus}</span>
                            <span className="text-text-tertiary text-[10px]">As of {hEv.asOfDate || '2024-01-15'}</span>
                          </div>
                        </div>

                        {/* Metric Grid */}
                        <div className="grid grid-cols-2 gap-2 sm:grid-cols-4 pt-1">
                          {hEv.metrics && hEv.metrics.map(m => (
                            <div key={m.metricCode} className="p-2 bg-surface-raised rounded border border-border/50">
                              <span className="text-text-tertiary text-[10px] block truncate">{m.metricName}</span>
                              <div className="mt-1 flex items-baseline justify-between">
                                {m.availabilityState === 'AVAILABLE' && m.formattedValue ? (
                                  <span className={`font-semibold text-[12px] ${
                                    m.metricCode === 'RSK-03' ? 'text-rose-400' :
                                    m.metricCode === 'RSK-01' ? 'text-amber-300' : 'text-text-primary'
                                  }`}>
                                    {m.formattedValue}
                                  </span>
                                ) : (
                                  <span className="text-text-disabled text-[10px] italic">
                                    N/A — insufficient data
                                  </span>
                                )}
                                {m.observationCount && (
                                  <span className="text-[9px] text-text-tertiary">N={m.observationCount}</span>
                                )}
                              </div>
                            </div>
                          ))}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Analytical Investigation Prompts */}
              {summary.portfolioRiskEvidence.investigationPrompts && summary.portfolioRiskEvidence.investigationPrompts.length > 0 && (
                <div className="space-y-2 pt-2 border-t border-border">
                  <h4 className="text-[12px] font-semibold text-accent flex items-center gap-2">
                    <span>Factual Analytical Investigation Prompts</span>
                    <span className="mono-meta text-[10px]">DATA-DRIVEN QUESTIONS</span>
                  </h4>
                  <ul className="list-disc list-inside space-y-1 font-mono text-[11px] text-text-secondary">
                    {summary.portfolioRiskEvidence.investigationPrompts.map((promptText, idx) => (
                      <li key={idx} className="leading-relaxed">{promptText}</li>
                    ))}
                  </ul>
                </div>
              )}

              {/* Data Limitations */}
              {summary.portfolioRiskEvidence.dataLimitations && summary.portfolioRiskEvidence.dataLimitations.length > 0 && (
                <div className="space-y-1 pt-2 border-t border-border font-mono text-[10px] text-text-tertiary">
                  <span className="font-semibold text-text-secondary block">Methodological Limitations:</span>
                  {summary.portfolioRiskEvidence.dataLimitations.map((lim, idx) => (
                    <p key={idx}>• {lim}</p>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* Portfolio Goal Alignment & Horizon Planning V1 Section */}
          {summary && (
            <PortfolioGoalAlignmentView hasHoldings={summary.holdings.length > 0} />
          )}

          {/* Portfolio Analytical Rebalancing & Drift Inspection V1 Section */}
          {summary && (
            <PortfolioDriftView hasHoldings={summary.holdings.length > 0} />
          )}

          {/* Portfolio Fund Comparison & Evidence Matrix V1 Section */}
          {summary && (
            <PortfolioComparisonView holdings={summary.holdings || []} />
          )}

          {/* Portfolio Monitoring & Evidence Drift (Task 12) */}
          <PortfolioMonitoringPanel />

          {/* Institutional Portfolio Intelligence & Look-Through Exposure Section */}
          <PortfolioIntelligenceSection
            holdings={summary?.holdings || []}
            onHoldingUpdated={async () => {
              const updated = await fetchPortfolioSummary();
              setSummary(updated);
            }}
          />

          {/* Institutional Portfolio Risk & Diversification Intelligence (Task 16) */}
          <PortfolioRiskDiversificationSection
            holdings={summary?.holdings || []}
            onHoldingUpdated={async () => {
              const updated = await fetchPortfolioSummary();
              setSummary(updated);
            }}
          />

          {/* Investor Performance & Transaction Ledger (Task 10) */}
          <TransactionsAndPerformanceSection
            holdings={summary?.holdings || []}
            availableOptions={options}
            onDataChanged={async () => {
              const updated = await fetchPortfolioSummary();
              setSummary(updated);
            }}
          />

          {/* Portfolio Analytical Exposure & Concentration Section */}
          {summary && summary.holdings.length > 0 && conc && (
            <div className="panel p-4 space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border pb-3">
                <div>
                  <h3 className="text-[14px] font-semibold text-text-primary flex items-center gap-2">
                    <span>Portfolio Exposure & Concentration Analytics</span>
                    <span className="status-badge state-verified text-[10px]">DESCRIPTIVE ANALYSIS</span>
                  </h3>
                  <p className="font-mono text-[11px] text-text-tertiary mt-0.5">
                    Factual exposure share derived strictly from backend portfolio valuations as of NAV observation dates.
                  </p>
                </div>
                {conc.concentrationState && (
                  <span className={`status-badge ${
                    conc.concentrationState === 'COMPLETE' ? 'state-verified' :
                    conc.concentrationState === 'PARTIAL' ? 'state-candidate' : 'state-unavailable'
                  }`}>
                    {conc.concentrationState} COVERAGE
                  </span>
                )}
              </div>

              {/* Concentration Overview Key Metrics */}
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 font-mono text-[11px]">
                <div className="panel-inset p-2.5 bg-surface">
                  <span className="text-text-muted text-[10px] block">Top Holding Exposure</span>
                  <p className="font-semibold text-text-primary text-[13px] mt-1 truncate">
                    {conc.topHoldingName || 'N/A'}
                  </p>
                  <p className="text-accent text-[12px] font-bold mt-0.5">
                    {conc.topHoldingWeight !== null && conc.topHoldingWeight !== undefined
                      ? `${conc.topHoldingWeight.toFixed(2)}% share`
                      : '—'}
                  </p>
                </div>

                <div className="panel-inset p-2.5 bg-surface">
                  <span className="text-text-muted text-[10px] block">Top 3 Combined Exposure</span>
                  <p className="font-semibold text-text-primary text-[13px] mt-1">
                    Top 3 Holdings
                  </p>
                  <p className="text-accent text-[12px] font-bold mt-0.5">
                    {conc.top3HoldingsWeight !== null && conc.top3HoldingsWeight !== undefined
                      ? `${conc.top3HoldingsWeight.toFixed(2)}% combined`
                      : '—'}
                  </p>
                </div>

                <div className="panel-inset p-2.5 bg-surface">
                  <span className="text-text-muted text-[10px] block">Largest AMC Share</span>
                  <p className="font-semibold text-text-primary text-[13px] mt-1 truncate">
                    {conc.topAmcName || 'N/A'}
                  </p>
                  <p className="text-accent text-[12px] font-bold mt-0.5">
                    {conc.topAmcWeight !== null && conc.topAmcWeight !== undefined
                      ? `${conc.topAmcWeight.toFixed(2)}% share`
                      : '—'}
                  </p>
                </div>

                <div className="panel-inset p-2.5 bg-surface">
                  <span className="text-text-muted text-[10px] block">Largest Category Share</span>
                  <p className="font-semibold text-text-primary text-[13px] mt-1 truncate">
                    {conc.topCategoryName || 'N/A'}
                  </p>
                  <p className="text-accent text-[12px] font-bold mt-0.5">
                    {conc.topCategoryWeight !== null && conc.topCategoryWeight !== undefined
                      ? `${conc.topCategoryWeight.toFixed(2)}% share`
                      : '—'}
                  </p>
                </div>
              </div>

              {conc.concentrationState === 'PARTIAL' && (
                <div className="panel-inset p-2.5 bg-amber-500/10 border-amber-500/20 font-mono text-[11px] text-amber-300">
                  Notice: Portfolio exposure percentages represent covered valuation universe ({summary.valuedHoldingsCount} of {summary.totalHoldings} holdings with available NAV).
                </div>
              )}
            </div>
          )}

          {/* Holdings List Section */}
          <div>
            <div className="mb-4">
              <SectionHeading
                ordinal="01"
                title="Current Holdings"
                description="Externally tracked scheme options mapped to canonical scheme_option_id."
              />
            </div>

            {loading ? (
              <div className="space-y-3">
                <div className="skeleton h-28 w-full" />
                <div className="skeleton h-28 w-full" />
              </div>
            ) : !summary || summary.holdings.length === 0 ? (
              <div className="state-well p-8 text-center">
                <p className="font-mono text-[13px] text-text-secondary">No external holdings added yet.</p>
                <p className="mt-1 font-mono text-[11px] text-text-tertiary">
                  Select a scheme option below to add your first externally held position.
                </p>
              </div>
            ) : (
              <div className="space-y-4">
                {summary.holdings.map(holding => {
                  const yScore = holding.yukiraScore;
                  const scoreStatus = yScore?.status || (holding.analyticalScore?.available ? holding.analyticalScore.status : "NOT_AVAILABLE");
                  const scoreVal = yScore?.score !== undefined ? yScore.score : holding.analyticalScore?.scoreValue;

                  return (
                    <div
                      key={holding.id}
                      id={`holding-${holding.schemeOptionId || holding.id}`}
                      className="panel transition-colors hover:border-text-primary/30"
                    >
                      <div className="panel-header flex-wrap gap-2">
                        <div className="min-w-0 flex-1">
                          <div className="flex items-center gap-2 flex-wrap">
                            <Link
                              href={`/funds/${holding.schemeOptionId}?option=${holding.schemeOptionId}&from=/portfolio`}
                              className="text-[15px] font-semibold text-text-primary hover:text-accent"
                            >
                              {holding.fundName}
                            </Link>

                            {holding.isSavedInWatchlist ? (
                              <Link
                                href={`/watchlist#saved-${holding.schemeOptionId}`}
                                className="status-badge state-approved text-[9px] hover:underline font-bold"
                                title="Saved in Investor Research Workspace. Click to view research notes."
                              >
                                🔬 SAVED IN RESEARCH ↗
                              </Link>
                            ) : (
                              <button
                                onClick={() => {
                                  setHoldingToEdit(holding);
                                  setIsEditModalOpen(true);
                                }}
                                className="status-badge text-[9px] font-mono text-text-tertiary bg-surface-raised border border-border/50 hover:text-text-primary cursor-pointer"
                                title="Click to record research thesis or investigation questions"
                              >
                                + ATTACH RESEARCH THESIS
                              </button>
                            )}

                            <span className={`status-badge text-[9px] font-mono ${
                              holding.holdingCompletenessStatus === 'COMPLETE' ? 'state-approved' :
                              holding.holdingCompletenessStatus === 'ATTENTION_REQUIRED' ? 'state-rejected' : 'state-candidate'
                            }`}>
                              DATA: {holding.holdingCompletenessStatus || 'PARTIAL'}
                            </span>
                          </div>

                          <div className="mt-1.5 flex flex-wrap items-center gap-2 font-mono text-[11px]">
                            <span className="status-badge state-verified">{holding.planType}</span>
                            <span className="status-badge state-verified">{holding.optionType}</span>
                            <span className={`status-badge text-[10px] ${
                              holding.valuationFreshnessStatus === 'VALUATION_CURRENT' ? 'state-verified' :
                              holding.valuationFreshnessStatus === 'VALUATION_STALE' ? 'state-candidate' : 'state-unavailable'
                            }`}>
                              {holding.valuationFreshnessStatus === 'VALUATION_CURRENT' ? 'VALUATION CURRENT' :
                               holding.valuationFreshnessStatus === 'VALUATION_STALE' ? 'VALUATION STALE (LAST KNOWN)' :
                               holding.valuationFreshnessStatus === 'VALUATION_UNDETERMINED' ? 'FRESHNESS UNDETERMINED' : 'VALUATION UNAVAILABLE'}
                            </span>
                            <span className="text-text-tertiary">AMC: {holding.amcName}</span>
                            <span className="text-text-tertiary">AMFI: {holding.amfiCode}</span>
                            <span className="text-text-tertiary">ISIN: {holding.isin}</span>
                          </div>
                        </div>

                        <div className="flex items-center gap-2 shrink-0">
                          {holding.isSavedInWatchlist ? (
                            <Link
                              href={`/watchlist#saved-${holding.schemeOptionId}`}
                              className="btn btn-secondary btn-sm flex items-center gap-1 text-[11px] text-accent border-accent/30 hover:bg-accent/10"
                              title="Inspect saved notes in Research Workspace"
                            >
                              <span>Research</span>
                              <span className="text-[10px]">↗</span>
                            </Link>
                          ) : (
                            <button
                              onClick={() => {
                                setHoldingToEdit(holding);
                                setIsEditModalOpen(true);
                              }}
                              className="btn btn-secondary btn-sm text-[11px] text-accent"
                              title="Record investment thesis or questions"
                            >
                              + Thesis
                            </button>
                          )}
                          <button
                            onClick={() => {
                              setHoldingToEdit(holding);
                              setIsEditModalOpen(true);
                            }}
                            className="btn btn-secondary btn-sm"
                          >
                            Edit
                          </button>
                          <Link
                            href={`/funds/${holding.schemeOptionId}?option=${holding.schemeOptionId}&from=/portfolio#scorecard`}
                            className="btn btn-secondary btn-sm flex items-center gap-1"
                          >
                            <span>Scorecard</span>
                            <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
                            </svg>
                          </Link>
                          <Link
                            href={`/funds/${holding.schemeOptionId}?option=${holding.schemeOptionId}&from=/portfolio`}
                            className="btn btn-secondary btn-sm"
                          >
                            Profile
                          </Link>
                          <button
                            onClick={() => {
                              setHoldingToRemove(holding);
                              setIsRemoveModalOpen(true);
                            }}
                            disabled={submitting}
                            className="btn btn-danger btn-sm"
                            aria-label="Remove holding"
                          >
                            Remove
                          </button>
                        </div>
                      </div>

                      {/* Holding Performance & Exposure Details */}
                      <dl className="grid grid-cols-2 gap-3 border-t border-border px-4 py-3.5 sm:grid-cols-5">
                        <div>
                          <dt className="def-label">Units Held</dt>
                          <dd className="data-value-sm font-mono">{holding.units}</dd>
                        </div>

                        <div>
                          <dt className="def-label">Latest NAV</dt>
                          <dd className="data-value-sm font-mono">
                            {holding.navValue !== null ? (
                              <>
                                {formatCurrency(holding.navValue)}
                                {holding.navAsOfDate && (
                                  <span className="block text-[10px] text-text-tertiary">
                                    As of {holding.navAsOfDate}
                                  </span>
                                )}
                              </>
                            ) : (
                              <span className="text-text-disabled">Unavailable</span>
                            )}
                          </dd>
                        </div>

                        <div>
                          <dt className="def-label">Valuation (Last Known)</dt>
                          <dd className="data-value-sm font-mono">
                            {holding.lastKnownValue !== null && holding.lastKnownValue !== undefined ? (
                              <span className="text-amber-300 font-semibold">{formatCurrency(holding.lastKnownValue)}</span>
                            ) : holding.availableValue !== null ? (
                              <span className="text-amber-300 font-semibold">{formatCurrency(holding.availableValue)}</span>
                            ) : (
                              <span className="text-text-disabled">Unavailable</span>
                            )}
                            <span className="block text-[10px] text-text-tertiary">
                              {holding.valuationFreshnessStatus === 'VALUATION_CURRENT'
                                ? 'Current Value'
                                : holding.valuationFreshnessStatus === 'VALUATION_STALE'
                                ? 'Last Known (Historical)'
                                : 'Unvalued'}
                            </span>
                          </dd>
                        </div>

                        <div>
                          <dt className="def-label">Portfolio Share</dt>
                          <dd className="data-value-sm font-mono">
                            {holding.portfolioWeight !== null && holding.portfolioWeight !== undefined ? (
                              <span className="font-semibold text-accent">
                                {holding.portfolioWeight.toFixed(2)}%
                              </span>
                            ) : (
                              <span className="text-text-disabled">N/A</span>
                            )}
                          </dd>
                        </div>

                        <div>
                          <dt className="def-label">YUKIRA Score</dt>
                          <dd className="data-value-sm font-mono">
                            {scoreVal !== null && scoreVal !== undefined ? (
                              <div className="flex items-center gap-1.5">
                                <span className="font-semibold text-accent text-sm">
                                  {scoreVal.toFixed(2)}
                                </span>
                                <span className={`status-badge text-[10px] ${scoreStatus === 'AVAILABLE' ? 'state-approved' : 'state-candidate'}`}>
                                  {scoreStatus}
                                </span>
                              </div>
                            ) : (
                              <span className="status-badge state-candidate text-[10px]">
                                {scoreStatus === "INSUFFICIENT_DATA" ? "INSUFFICIENT DATA" : scoreStatus === "NOT_APPLICABLE" ? "NOT APPLICABLE" : "UNAVAILABLE"}
                              </span>
                            )}
                          </dd>
                        </div>

                        {/* Optional Cost Basis & Gain/Loss row */}
                        {holding.investedAmount !== null && (
                          <>
                            <div className="border-t border-border pt-2">
                              <dt className="def-label">Cost Basis / Unit</dt>
                              <dd className="data-value-sm font-mono">
                                {holding.costBasisAmount !== null ? formatCurrency(holding.costBasisAmount) : 'N/A'}
                              </dd>
                            </div>
                            <div className="border-t border-border pt-2">
                              <dt className="def-label">Invested Amount</dt>
                              <dd className="data-value-sm font-mono">
                                {formatCurrency(holding.investedAmount)}
                              </dd>
                            </div>
                            <div className="border-t border-border pt-2 col-span-3">
                              <dt className="def-label">Absolute Gain / Loss</dt>
                              <dd className="data-value-sm font-mono">
                                {holding.absoluteGainLoss !== null ? (
                                  <span className={holding.absoluteGainLoss >= 0 ? 'text-emerald-400' : 'text-rose-400'}>
                                    {holding.absoluteGainLoss >= 0 ? '+' : ''}{formatCurrency(holding.absoluteGainLoss)}
                                    {holding.absoluteGainLossPercentage !== null && (
                                      <span className="ml-1.5 text-[11px]">
                                        ({holding.absoluteGainLossPercentage >= 0 ? '+' : ''}
                                        {formatPercentage(holding.absoluteGainLossPercentage)})
                                      </span>
                                    )}
                                  </span>
                                ) : (
                                  <span className="text-text-disabled">N/A</span>
                                )}
                              </dd>
                            </div>
                          </>
                        )}
                      </dl>

                      {/* Holding Data Completeness & Action Checklist */}
                      <div className="border-t border-border px-4 py-3 bg-surface-raised/20 space-y-2.5">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <div className="flex items-center gap-2">
                            <span className="eyebrow text-[10px] text-text-tertiary">
                              DATA COMPLETENESS &amp; ACTION CHECKLIST
                            </span>
                            <span className={`status-badge text-[9px] font-mono ${
                              holding.holdingCompletenessStatus === 'COMPLETE' ? 'state-approved' :
                              holding.holdingCompletenessStatus === 'ATTENTION_REQUIRED' ? 'state-rejected' : 'state-candidate'
                            }`}>
                              {holding.holdingCompletenessStatus || 'PARTIAL'}
                            </span>
                          </div>
                          <span className="font-mono text-[10px] text-text-tertiary">
                            {holding.missingAttributes && holding.missingAttributes.length > 0
                              ? `${holding.missingAttributes.length} item(s) require attention`
                              : 'All core attributes verified'}
                          </span>
                        </div>

                        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-2 font-mono text-[11px]">
                          {/* 1. Identity & Market Codes */}
                          <div className="panel-inset p-2.5 bg-surface border border-border/70 flex flex-col justify-between">
                            <div>
                              <div className="flex items-center justify-between">
                                <span className="text-text-muted text-[10px]">Scheme Option Identity</span>
                                {holding.schemeOptionId ? (
                                  <span className="text-emerald-400 font-bold text-[10px]">✓ USABLE</span>
                                ) : (
                                  <span className="text-rose-400 font-bold text-[10px]">✕ MISSING</span>
                                )}
                              </div>
                              <p className="text-[11px] text-text-primary mt-1 font-sans font-semibold">
                                Option #{holding.schemeOptionId}
                              </p>
                              <p className="text-[10px] text-text-tertiary mt-0.5 truncate">
                                AMFI: {holding.amfiCode !== 'N/A' ? holding.amfiCode : 'Missing'} • ISIN: {holding.isin !== 'N/A' ? holding.isin : 'Missing'}
                              </p>
                            </div>
                            <div className="pt-2 mt-auto border-t border-border/40 flex items-center justify-between">
                              <span className="text-[9px] text-text-tertiary">Catalog Verified</span>
                              <Link
                                href={`/funds/${holding.schemeOptionId}`}
                                className="text-[10px] text-accent hover:underline"
                              >
                                View Fund →
                              </Link>
                            </div>
                          </div>

                          {/* 2. Position Units & Cost Basis */}
                          <div className="panel-inset p-2.5 bg-surface border border-border/70 flex flex-col justify-between">
                            <div>
                              <div className="flex items-center justify-between">
                                <span className="text-text-muted text-[10px]">Units &amp; Cost Basis</span>
                                {holding.costBasisAmount !== null && holding.costBasisAmount !== undefined ? (
                                  <span className="text-emerald-400 font-bold text-[10px]">✓ COMPLETE</span>
                                ) : (
                                  <span className="text-amber-400 font-bold text-[10px]">⚠️ COST BASIS MISSING</span>
                                )}
                              </div>
                              <p className="text-[11px] text-text-primary mt-1 font-sans font-semibold">
                                {holding.units} units recorded
                              </p>
                              <p className="text-[10px] text-text-tertiary mt-0.5">
                                {holding.costBasisAmount !== null && holding.costBasisAmount !== undefined
                                  ? `Cost: ${formatCurrency(holding.costBasisAmount)} / unit`
                                  : 'Gain/loss cannot be computed without cost'}
                              </p>
                            </div>
                            <div className="pt-2 mt-auto border-t border-border/40 flex items-center justify-between">
                              <span className="text-[9px] text-text-tertiary">
                                {holding.costBasisAmount ? 'Gain/loss enabled' : 'Optional cost'}
                              </span>
                              <button
                                onClick={() => {
                                  setHoldingToEdit(holding);
                                  setIsEditModalOpen(true);
                                }}
                                className="text-[10px] text-accent hover:underline font-mono"
                              >
                                {holding.costBasisAmount ? 'Edit Cost' : '+ Add Cost'}
                              </button>
                            </div>
                          </div>

                          {/* 3. Valuation NAV & Freshness */}
                          <div className="panel-inset p-2.5 bg-surface border border-border/70 flex flex-col justify-between">
                            <div>
                              <div className="flex items-center justify-between">
                                <span className="text-text-muted text-[10px]">Valuation &amp; Freshness</span>
                                {holding.valuationFreshnessStatus === 'VALUATION_CURRENT' ? (
                                  <span className="text-emerald-400 font-bold text-[10px]">✓ CURRENT</span>
                                ) : holding.valuationFreshnessStatus === 'VALUATION_STALE' ? (
                                  <span className="text-amber-300 font-bold text-[10px]">⏳ HISTORICAL</span>
                                ) : (
                                  <span className="text-rose-400 font-bold text-[10px]">✕ UNAVAILABLE</span>
                                )}
                              </div>
                              <p className="text-[11px] text-text-primary mt-1 font-sans font-semibold">
                                {holding.navValue !== null ? formatCurrency(holding.navValue) : 'Observation missing'}
                              </p>
                              <p className="text-[10px] text-text-tertiary mt-0.5">
                                {holding.valuationFreshnessStatus === 'VALUATION_STALE'
                                  ? `As of ${holding.navAsOfDate || 'historical cutoff'} (> 3d lag)`
                                  : holding.valuationFreshnessStatus === 'VALUATION_CURRENT'
                                  ? `As of ${holding.navAsOfDate || 'today'} (within 3d)`
                                  : 'Valuation cannot be evaluated'}
                              </p>
                            </div>
                            <div className="pt-2 mt-auto border-t border-border/40 flex items-center justify-between">
                              <span className="text-[9px] text-text-tertiary">Ledger Provenance</span>
                              <Link
                                href={`/funds/${holding.schemeOptionId}`}
                                className="text-[10px] text-accent hover:underline"
                              >
                                Evidence →
                              </Link>
                            </div>
                          </div>

                          {/* 4. Research Thesis & Investigation Question */}
                          <div className="panel-inset p-2.5 bg-surface border border-border/70 flex flex-col justify-between">
                            <div>
                              <div className="flex items-center justify-between">
                                <span className="text-text-muted text-[10px]">Research Context</span>
                                {holding.reasonSaved ? (
                                  <span className="text-emerald-400 font-bold text-[10px]">✓ RECORDED</span>
                                ) : (
                                  <span className="text-amber-400 font-bold text-[10px]">⚠️ THESIS MISSING</span>
                                )}
                              </div>
                              <p className="text-[11px] text-text-primary mt-1 font-sans font-semibold truncate" title={holding.reasonSaved || 'No investment thesis documented'}>
                                {holding.reasonSaved ? holding.reasonSaved : 'No thesis documented'}
                              </p>
                              <p className="text-[10px] text-text-tertiary mt-0.5 truncate">
                                {holding.investigationQuestion ? holding.investigationQuestion : 'No active due-diligence question'}
                              </p>
                            </div>
                            <div className="pt-2 mt-auto border-t border-border/40 flex items-center justify-between">
                              <span className="text-[9px] text-text-tertiary">
                                {holding.isSavedInWatchlist ? 'Synced' : 'Holding only'}
                              </span>
                              <button
                                onClick={() => {
                                  setHoldingToEdit(holding);
                                  setIsEditModalOpen(true);
                                }}
                                className="text-[10px] text-accent hover:underline font-mono"
                              >
                                {holding.reasonSaved ? 'Edit Thesis' : '+ Add Thesis'}
                              </button>
                            </div>
                          </div>
                        </div>
                      </div>

                      {/* Valuation Arithmetic & Provenance Breakdown */}
                      <div className="border-t border-border/80 px-4 py-3 bg-surface-raised/40 space-y-2.5 font-mono text-[11px]">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <div className="flex flex-wrap items-center gap-2">
                            <span className="text-text-tertiary">Valuation Math:</span>
                            {holding.valuationState === 'VALUATION_AVAILABLE' && holding.navValue !== null ? (
                              <span className="text-text-primary">
                                {holding.units} units × {formatCurrency(holding.navValue)} NAV (effective {holding.navAsOfDate || 'N/A'}) = <strong className="text-accent">{formatCurrency(holding.lastKnownValue ?? holding.availableValue ?? 0)}</strong>
                              </span>
                            ) : (
                              <span className="text-amber-400">
                                NAV observation unavailable in database. Valuation cannot be evaluated.
                              </span>
                            )}
                          </div>
                          <div className="flex items-center gap-2">
                            <span className={`status-badge text-[10px] ${
                              holding.valuationFreshnessStatus === 'VALUATION_CURRENT' ? 'state-verified' :
                              holding.valuationFreshnessStatus === 'VALUATION_STALE' ? 'state-candidate' : 'state-unavailable'
                            }`}>
                              {holding.valuationFreshnessStatus === 'VALUATION_CURRENT' ? 'FRESH DATA' :
                               holding.valuationFreshnessStatus === 'VALUATION_STALE' ? 'STALE (HISTORICAL)' :
                               holding.valuationFreshnessStatus === 'VALUATION_UNDETERMINED' ? 'UNDETERMINED' : 'UNAVAILABLE'}
                            </span>
                          </div>
                        </div>

                        {/* Freshness Explanation */}
                        {holding.freshnessExplanation && (
                          <p className="text-[11px] text-text-secondary font-sans leading-relaxed">
                            ℹ️ {holding.freshnessExplanation}
                          </p>
                        )}

                        {/* Provenance Metadata & Evidence Inspection */}
                        <div className="pt-2 border-t border-border/60 grid grid-cols-1 sm:grid-cols-4 gap-2 text-[10px] text-text-tertiary">
                          <div>
                            <span className="block text-text-muted">Effective Date:</span>
                            <span className="font-mono text-text-secondary">{holding.navAsOfDate || 'N/A'}</span>
                          </div>
                          <div>
                            <span className="block text-text-muted">Availability Time:</span>
                            <span className="font-mono text-text-secondary truncate block" title={holding.navAvailabilityTime || 'N/A'}>
                              {holding.navAvailabilityTime ? holding.navAvailabilityTime.substring(0, 19).replace('T', ' ') : 'N/A'}
                            </span>
                          </div>
                          <div>
                            <span className="block text-text-muted">Ingestion Time:</span>
                            <span className="font-mono text-text-secondary truncate block" title={holding.navIngestionTime || 'N/A'}>
                              {holding.navIngestionTime ? holding.navIngestionTime.substring(0, 19).replace('T', ' ') : 'N/A'}
                            </span>
                          </div>
                          <div>
                            <span className="block text-text-muted">Source Artifact Hash:</span>
                            <span className="font-mono text-text-secondary truncate block" title={holding.sourceArtifactHash || 'N/A'}>
                              {holding.sourceArtifactHash ? `${holding.sourceArtifactHash.substring(0, 12)}...` : 'N/A'}
                            </span>
                          </div>
                        </div>

                        <div className="flex items-center justify-between pt-1 text-[10px]">
                          <span className="text-text-muted">
                            Revision Seq: <strong>{holding.revisionSeq ?? 1}</strong> • Quality: <strong className="text-emerald-400">{holding.qualityAssessment || 'VALID'}</strong>
                          </span>
                          <Link
                            href={`/funds/${holding.schemeOptionId}`}
                            className="text-accent hover:underline flex items-center gap-1 font-mono"
                          >
                            <span>Inspect Provenance in Fund Profile</span>
                            <span>→</span>
                          </Link>
                        </div>
                      </div>

                      {/* Attached Investor Research Notes & Questions */}
                      {(holding.reasonSaved || holding.investigationQuestion || holding.nextStep) ? (
                        <div className="border-t border-border px-4 py-3 bg-surface space-y-2">
                          <div className="flex items-center justify-between">
                            <span className="eyebrow text-accent flex items-center gap-1.5">
                              <span>Investor Research Context &amp; Notes</span>
                              {holding.isSavedInWatchlist && (
                                <Link
                                  href={`/watchlist#saved-${holding.schemeOptionId}`}
                                  className="status-badge state-approved text-[9px] hover:underline font-bold"
                                  title="Synchronized with Investor Research Workspace"
                                >
                                  SYNCED WITH WATCHLIST ↗
                                </Link>
                              )}
                            </span>
                            <div className="flex items-center gap-2">
                              <Link
                                href="/watchlist"
                                className="text-[11px] font-mono text-text-tertiary hover:text-accent underline"
                              >
                                View in Workspace
                              </Link>
                              <button
                                onClick={() => {
                                  setHoldingToEdit(holding);
                                  setIsEditModalOpen(true);
                                }}
                                className="text-[11px] font-mono text-accent hover:underline font-semibold"
                              >
                                Edit Notes
                              </button>
                            </div>
                          </div>
                          <div className="grid grid-cols-1 gap-2 sm:grid-cols-3 font-mono text-[11px]">
                            {holding.reasonSaved && (
                              <div className="panel-inset p-2.5 bg-surface-raised">
                                <span className="text-text-muted text-[10px] block">Reason Tracked / Thesis</span>
                                <p className="text-text-secondary mt-1 font-sans text-[12px]">{holding.reasonSaved}</p>
                              </div>
                            )}
                            {holding.investigationQuestion && (
                              <div className="panel-inset p-2.5 bg-surface-raised">
                                <span className="text-text-muted text-[10px] block">Key Investigation Question</span>
                                <p className="text-amber-300 mt-1 font-sans text-[12px]">{holding.investigationQuestion}</p>
                              </div>
                            )}
                            {holding.nextStep && (
                              <div className="panel-inset p-2.5 bg-surface-raised">
                                <span className="text-text-muted text-[10px] block">Next Verification Checkpoint</span>
                                <p className="text-accent mt-1 font-sans text-[12px]">{holding.nextStep}</p>
                              </div>
                            )}
                          </div>
                        </div>
                      ) : (
                        <div className="border-t border-border px-4 py-2.5 bg-surface/80 flex flex-wrap items-center justify-between gap-2 text-[11px] font-mono">
                          <div className="flex items-center gap-2 text-text-tertiary">
                            <span>📝</span>
                            <span>No investment thesis or investigation questions attached to this holding.</span>
                          </div>
                          <button
                            onClick={() => {
                              setHoldingToEdit(holding);
                              setIsEditModalOpen(true);
                            }}
                            className="text-accent hover:underline font-semibold"
                          >
                            + Record Investment Thesis &amp; Questions
                          </button>
                        </div>
                      )}

                      {/* YUKIRA Analytical Quality & Dimensions Breakdown Panel */}
                      <div className="border-t border-border bg-surface-raised p-3.5 space-y-3">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <div className="flex items-center gap-2">
                            <span className="eyebrow text-accent">YUKIRA Analytical Score Card</span>
                            <span className="mono-meta text-[10px]">
                              As of {yScore?.asOfDate || holding.analyticalScore?.asOfDate || "2024-01-15"}
                            </span>
                          </div>
                          <div className="flex items-center gap-2 font-mono text-[11px] text-text-tertiary">
                            <span>Confidence: {yScore?.confidence !== null && yScore?.confidence !== undefined ? `${yScore.confidence.toFixed(0)}/100` : (holding.analyticalScore?.confidence || "N/A")}</span>
                            <span>•</span>
                            <span>{yScore?.scoreVersion || holding.analyticalScore?.methodologyVersion || "YUKIRA_SCORE_V1"} ({yScore?.methodologyStatus || "CANDIDATE"})</span>
                          </div>
                        </div>

                        {/* Dimensions Breakdown Grid */}
                        {yScore?.dimensions && yScore.dimensions.length > 0 ? (
                          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4 font-mono text-[11px] pt-1">
                            {yScore.dimensions.map((dim) => (
                              <div key={dim.dimension} className="panel-inset p-2 bg-surface">
                                <span className="text-text-muted text-[10px] block truncate">{dim.dimensionName}</span>
                                <div className="flex items-baseline justify-between mt-1">
                                  <span className="font-semibold text-text-primary text-[12px]">
                                    {dim.score !== null && dim.score !== undefined ? dim.score.toFixed(2) : "—"}
                                  </span>
                                  <span className="text-[9px] text-text-tertiary">W: {dim.weight * 100}%</span>
                                </div>
                              </div>
                            ))}
                          </div>
                        ) : (
                          <div className="font-mono text-[11px] text-text-tertiary">
                            {scoreStatus === "INSUFFICIENT_DATA"
                              ? "Analytical dimensions unavailable due to insufficient historical observation window."
                              : scoreStatus === "NOT_APPLICABLE"
                              ? "Analytical quality score is not currently evaluated for this fund option type."
                              : "Analytical quality score snapshot is unavailable."}
                          </div>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>

            )}
          </div>

          {/* Allocation & Exposure Concentration Breakdown */}
          {summary && summary.holdings.length > 0 && (
            <div className="grid grid-cols-1 gap-6 sm:grid-cols-2">
              {/* Category Allocation */}
              <div className="panel p-4">
                <h3 className="eyebrow mb-1">Exposure by Category</h3>
                <p className="font-mono text-[10px] text-text-tertiary mb-3">
                  Allocations describe the valued last-known universe ({formatCurrency(summary.totalLastKnownValue ?? summary.totalAvailableValue ?? 0)}). Unvalued positions are excluded from the denominator. If input NAVs are stale, percentages reflect historical weights rather than live trading allocations.
                </p>
                {summary.categoryAllocations.length === 0 ? (
                  <p className="text-[12px] text-text-tertiary">No category valuations available.</p>
                ) : (
                  <div className="space-y-2.5">
                    {summary.categoryAllocations.map(cat => (
                      <div key={cat.category}>
                        <div className="flex justify-between font-mono text-[12px]">
                          <span className="text-text-secondary">{cat.category} ({cat.fundCount} fund)</span>
                          <span className="text-text-primary">{formatCurrency(cat.totalValue)} ({cat.percentageShare.toFixed(2)}%)</span>
                        </div>
                        <div className="mt-1 h-1.5 w-full bg-border rounded-full overflow-hidden">
                          <div
                            className="h-full bg-accent"
                            style={{ width: `${Math.min(cat.percentageShare, 100)}%` }}
                          />
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* AMC Concentration */}
              <div className="panel p-4">
                <h3 className="eyebrow mb-1">Exposure by AMC</h3>
                <p className="font-mono text-[10px] text-text-tertiary mb-3">
                  Allocations describe the valued last-known universe ({formatCurrency(summary.totalLastKnownValue ?? summary.totalAvailableValue ?? 0)}). Unvalued positions are excluded from the denominator. If input NAVs are stale, percentages reflect historical weights rather than live trading allocations.
                </p>
                {summary.amcAllocations.length === 0 ? (
                  <p className="text-[12px] text-text-tertiary">No AMC valuations available.</p>
                ) : (
                  <div className="space-y-2.5">
                    {summary.amcAllocations.map(amc => (
                      <div key={amc.amcName}>
                        <div className="flex justify-between font-mono text-[12px]">
                          <span className="text-text-secondary">{amc.amcName} ({amc.fundCount} fund)</span>
                          <span className="text-text-primary">{formatCurrency(amc.totalValue)} ({amc.percentageShare.toFixed(2)}%)</span>
                        </div>
                        <div className="mt-1 h-1.5 w-full bg-border rounded-full overflow-hidden">
                          <div
                            className="h-full bg-blue-500"
                            style={{ width: `${Math.min(amc.percentageShare, 100)}%` }}
                          />
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Data-Driven Investigation Prompts Panel */}
          {summary && (
            <div className="panel p-4 space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border pb-3">
                <div>
                  <h3 className="eyebrow text-accent">Data-Driven Investigation Prompts</h3>
                  <p className="font-mono text-[11px] text-text-tertiary mt-0.5">
                    Objective observations distinguishing verified facts from potential interpretations and verification checkpoints.
                  </p>
                </div>
                <span className="status-badge state-candidate text-[10px]">
                  {summary.structuredInvestigations?.length || 0} PROMPTS IDENTIFIED
                </span>
              </div>

              {/* Structured prompt cards */}
              {summary.structuredInvestigations && summary.structuredInvestigations.length > 0 ? (
                <div className="space-y-3 font-mono text-[11px]">
                  {summary.structuredInvestigations.map((item, idx) => (
                    <div key={idx} className="panel-inset p-3 bg-surface space-y-2 border border-border">
                      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 pb-2">
                        <span className={`status-badge text-[10px] ${
                          item.category === 'VALUATION_MISSING' ? 'state-unavailable' :
                          item.category === 'PORTFOLIO_COVERAGE' ? 'state-candidate' :
                          item.category === 'CONCENTRATION_OBSERVATION' ? 'state-verified' : 'state-candidate'
                        }`}>
                          {item.category.replace(/_/g, ' ')}
                        </span>
                        {item.schemeOptionId && (
                          <div className="flex items-center gap-2">
                            <Link
                              href={`/funds/${item.schemeOptionId}`}
                              className="text-accent hover:underline text-[11px] font-sans font-medium"
                            >
                              Fund Profile →
                            </Link>
                            <Link
                              href={`/funds/${item.schemeOptionId}#scorecard`}
                              className="text-text-tertiary hover:text-text-primary text-[11px] font-sans"
                            >
                              Evidence Explorer →
                            </Link>
                          </div>
                        )}
                      </div>

                      <div className="grid grid-cols-1 gap-2 sm:grid-cols-3 pt-1">
                        <div>
                          <span className="text-text-muted text-[10px] block font-sans font-medium">Fact / Observation</span>
                          <p className="text-text-primary mt-0.5 font-sans text-[12px] leading-relaxed">
                            {item.fact}
                          </p>
                        </div>
                        <div>
                          <span className="text-text-muted text-[10px] block font-sans font-medium">Analytical Interpretation</span>
                          <p className="text-text-secondary mt-0.5 font-sans text-[12px] leading-relaxed">
                            {item.interpretation}
                          </p>
                        </div>
                        <div>
                          <span className="text-text-muted text-[10px] block font-sans font-medium">Suggested Next Step</span>
                          <p className="text-accent mt-0.5 font-sans text-[12px] leading-relaxed">
                            {item.suggestedAction}
                          </p>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-[12px] font-mono text-text-tertiary">
                  No critical data limitations or high concentration flags identified for current holdings.
                </p>
              )}

              {/* General data quality limitations */}
              {summary.dataQualityLimitations.length > 0 && (
                <div className="border-t border-border pt-3">
                  <h4 className="text-[12px] font-semibold text-text-secondary mb-1.5">Data Quality Limitations</h4>
                  <ul className="list-disc list-inside space-y-1 text-[12px] text-text-tertiary font-mono">
                    {summary.dataQualityLimitations.map((lim, idx) => (
                      <li key={idx}>{lim}</li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Add Holding Form Right Column */}
        <div>
          <div className="mb-4">
            <SectionHeading ordinal="02" title="Add External Holding" />
          </div>

          <form onSubmit={handleAddHolding} className="panel">
            <div className="space-y-4 p-4">
              {loading ? (
                <div className="skeleton h-9 w-full" aria-label="Loading scheme options" />
              ) : (
                <>
                  <div>
                    <label htmlFor="fund-select" className="field-label">
                      Select Scheme Option
                    </label>
                    <select
                      id="fund-select"
                      value={selectedOptionId}
                      onChange={(e) => setSelectedOptionId(e.target.value)}
                      className="field"
                      required
                    >
                      <option value="">— Select exact fund option —</option>
                      {options.map(opt => (
                        <option key={opt.id} value={opt.id}>
                          {opt.plan?.scheme?.name} - {opt.plan?.planType} ({opt.optionType}) [AMFI: {opt.amfiCode || 'N/A'}]
                        </option>
                      ))}
                    </select>
                    <p className="mt-1 font-mono text-[10px] text-text-tertiary">
                      Select exact Direct/Regular and Growth/IDCW option.
                    </p>
                  </div>

                  <div>
                    <label htmlFor="units-input" className="field-label">
                      Units Held
                    </label>
                    <input
                      id="units-input"
                      type="number"
                      step="0.0001"
                      min="0.0001"
                      value={unitsInput}
                      onChange={(e) => setUnitsInput(e.target.value)}
                      className="field field-mono"
                      placeholder="e.g. 100.0000"
                      required
                    />
                  </div>

                  <div>
                    <label htmlFor="cost-basis-input" className="field-label">
                      Cost Basis / Purchase Price per Unit (Optional)
                    </label>
                    <input
                      id="cost-basis-input"
                      type="number"
                      step="0.01"
                      min="0"
                      value={costBasisInput}
                      onChange={(e) => setCostBasisInput(e.target.value)}
                      className="field field-mono"
                      placeholder="e.g. 1500.00"
                    />
                    <p className="mt-1 font-mono text-[10px] text-text-tertiary">
                      Leave blank if cost basis is unknown. Gain/loss analytics will evaluate only when cost basis is provided.
                    </p>
                  </div>

                  <div className="space-y-3 pt-2 border-t border-border">
                    <span className="eyebrow text-accent">Research Context & Notes (Optional)</span>
                    <div>
                      <label htmlFor="reason-saved-input" className="field-label">
                        Allocation Thesis / Reason Tracked
                      </label>
                      <input
                        id="reason-saved-input"
                        type="text"
                        value={reasonSavedInput}
                        onChange={(e) => setReasonSavedInput(e.target.value)}
                        className="field"
                        placeholder="e.g. Core active large-cap position"
                      />
                    </div>
                    <div>
                      <label htmlFor="question-input" className="field-label">
                        Investigation Question
                      </label>
                      <input
                        id="question-input"
                        type="text"
                        value={questionInput}
                        onChange={(e) => setQuestionInput(e.target.value)}
                        className="field"
                        placeholder="e.g. Assess downside drawdown vs benchmark"
                      />
                    </div>
                    <div>
                      <label htmlFor="next-step-input" className="field-label">
                        Next Verification Checkpoint
                      </label>
                      <input
                        id="next-step-input"
                        type="text"
                        value={nextStepInput}
                        onChange={(e) => setNextStepInput(e.target.value)}
                        className="field"
                        placeholder="e.g. Check quarterly rolling CAGR consistency"
                      />
                    </div>
                  </div>

                  <button
                    type="submit"
                    disabled={submitting || !selectedOptionId || !unitsInput}
                    className="btn btn-primary w-full"
                  >
                    {submitting ? 'Adding Holding...' : 'Add External Holding'}
                  </button>
                </>
              )}

              <div className="panel-inset p-3 text-[11px] leading-[1.6] text-text-tertiary">
                <strong className="text-text-secondary">Epistemic Notice:</strong> YUKIRA operates on a
                read-only deterministic analysis engine. Externally added holdings are stored in your investor
                account and connected directly to YUKIRA analytical ledgers. No broker integration or transaction execution is supported.
              </div>
            </div>
          </form>
        </div>
      </div>
        </>
      )}
    </PageContainer>
  );
}

export default function PortfolioPage() {
  return (
    <Suspense
      fallback={
        <PageContainer
          title="My Portfolio"
          subtitle="Loading portfolio..."
        >
          <div className="p-8 text-center font-mono text-[12px] text-text-tertiary">
            Initializing portfolio view...
          </div>
        </PageContainer>
      }
    >
      <PortfolioPageContent />
    </Suspense>
  );
}
