'use client';

import React, { useState, useEffect, useMemo } from 'react';
import Link from 'next/link';
import {
  fetchInvestorPortfolioIntelligence,
  InvestorPortfolioIntelligenceResponseDto,
  PortfolioHoldingDto,
  ConsolidatedPortfolioSecurityDto,
  PortfolioResearchFindingDto,
  addPortfolioHolding
} from '@/lib/api/portfolio';
import { formatCurrency, formatNumber } from '@/lib/utils/formatters';

interface PortfolioIntelligenceSectionProps {
  holdings?: PortfolioHoldingDto[];
  onHoldingUpdated?: () => void;
}

export function PortfolioIntelligenceSection({
  holdings = [],
  onHoldingUpdated
}: PortfolioIntelligenceSectionProps) {
  const [allocationMode, setAllocationMode] = useState<'ACTUAL' | 'EXPLORATORY_EQUAL_WEIGHT'>('ACTUAL');
  const [intelligence, setIntelligence] = useState<InvestorPortfolioIntelligenceResponseDto | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Active Sub-Tab
  const [activeTab, setActiveTab] = useState<
    'lookThrough' | 'allocation' | 'concentration' | 'sectors' | 'overlap' | 'drift' | 'brief' | 'riskAndPerf'
  >('lookThrough');

  // Search & Filter state for Underlying Securities
  const [securitySearch, setSecuritySearch] = useState<string>('');
  const [showRepeatedOnly, setShowRepeatedOnly] = useState<boolean>(false);
  const [expandedSecurityName, setExpandedSecurityName] = useState<string | null>(null);

  // Follow-up modal state
  const [followUpFinding, setFollowUpFinding] = useState<PortfolioResearchFindingDto | null>(null);
  const [selectedHoldingIdForFollowUp, setSelectedHoldingIdForFollowUp] = useState<number | null>(null);
  const [followUpQuestion, setFollowUpQuestion] = useState<string>('');
  const [followUpNextStep, setFollowUpNextStep] = useState<string>('');
  const [followUpSubmitting, setFollowUpSubmitting] = useState<boolean>(false);
  const [followUpSuccessMsg, setFollowUpSuccessMsg] = useState<string | null>(null);

  const loadIntelligence = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await fetchInvestorPortfolioIntelligence({ allocationMode });
      setIntelligence(data);
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to calculate portfolio intelligence');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadIntelligence();
  }, [allocationMode]);

  // Filtered underlying securities
  const filteredSecurities = useMemo(() => {
    if (!intelligence?.underlyingSecurities) return [];
    let list = intelligence.underlyingSecurities;
    if (showRepeatedOnly) {
      list = list.filter(s => s.isRepeatedAcrossFunds);
    }
    if (securitySearch.trim()) {
      const q = securitySearch.toLowerCase();
      list = list.filter(
        s =>
          s.canonicalName.toLowerCase().includes(q) ||
          (s.isin && s.isin.toLowerCase().includes(q)) ||
          s.macroSector.toLowerCase().includes(q)
      );
    }
    return list;
  }, [intelligence?.underlyingSecurities, showRepeatedOnly, securitySearch]);

  const handleOpenFollowUp = (finding: PortfolioResearchFindingDto) => {
    setFollowUpFinding(finding);
    setFollowUpQuestion(finding.suggestedInvestigation || finding.headline);
    setFollowUpNextStep(finding.actionableFollowUpPrompt || 'Review portfolio weighting and risk overlap.');
    if (holdings.length > 0) {
      setSelectedHoldingIdForFollowUp(holdings[0].id);
    }
    setFollowUpSuccessMsg(null);
  };

  const handleSaveFollowUp = async () => {
    if (!selectedHoldingIdForFollowUp) return;
    const targetHolding = holdings.find(h => h.id === selectedHoldingIdForFollowUp);
    if (!targetHolding) return;
    try {
      setFollowUpSubmitting(true);
      await addPortfolioHolding(
        targetHolding.schemeOptionId,
        targetHolding.units,
        targetHolding.costBasisAmount,
        targetHolding.reasonSaved || null,
        followUpQuestion.trim() || null,
        followUpNextStep.trim() || null
      );
      setFollowUpSuccessMsg('Investigation follow-up logged to workspace.');
      if (onHoldingUpdated) onHoldingUpdated();
      setTimeout(() => {
        setFollowUpFinding(null);
        setFollowUpSuccessMsg(null);
      }, 1400);
    } catch (err: unknown) {
      alert((err as Error).message || 'Failed to save follow-up note');
    } finally {
      setFollowUpSubmitting(false);
    }
  };

  return (
    <div className="bg-surface border border-border rounded-xl p-6 shadow-sm text-text-primary dark:bg-slate-900 dark:border-slate-800 dark:text-slate-100 space-y-6">
      {/* ------------------------------------------------------------------- */}
      {/* 1. SECTION HEADER & ALLOCATION CONTROLS */}
      {/* ------------------------------------------------------------------- */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between border-b border-border dark:border-slate-800 pb-4 gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-xl font-bold tracking-tight text-text-primary dark:text-white">
              Portfolio-Level Investment Intelligence
            </h2>
            <span className="text-xs px-2.5 py-0.5 rounded-full bg-cyan-500/10 text-cyan-500 border border-cyan-500/30 font-mono">
              POINT-IN-TIME
            </span>
            <span className="text-xs px-2.5 py-0.5 rounded-full bg-amber-500/10 text-amber-500 border border-amber-500/30 font-mono">
              CANDIDATE METHODOLOGY
            </span>
          </div>
          <p className="text-sm text-text-secondary dark:text-slate-400 mt-1">
            Consolidated underlying securities look-through, concentration & HHI, sector distribution, cross-fund duplicated exposure, and research brief.
          </p>
        </div>

        {/* Allocation Mode Selector */}
        <div className="flex items-center gap-3">
          <div className="flex items-center p-1 bg-surface-raised dark:bg-slate-950 rounded-lg border border-border dark:border-slate-800 text-xs">
            <button
              onClick={() => setAllocationMode('ACTUAL')}
              className={`px-3 py-1.5 rounded-md font-medium transition-colors ${
                allocationMode === 'ACTUAL'
                  ? 'bg-primary text-surface-dark font-semibold shadow dark:bg-cyan-600 dark:text-white'
                  : 'text-text-secondary hover:text-text-primary dark:text-slate-400 dark:hover:text-white'
              }`}
            >
              Actual Capital Allocation
            </button>
            <button
              onClick={() => setAllocationMode('EXPLORATORY_EQUAL_WEIGHT')}
              className={`px-3 py-1.5 rounded-md font-medium transition-colors ${
                allocationMode === 'EXPLORATORY_EQUAL_WEIGHT'
                  ? 'bg-amber-600 text-white font-semibold shadow'
                  : 'text-text-secondary hover:text-text-primary dark:text-slate-400 dark:hover:text-white'
              }`}
            >
              Exploratory Equal-Weight
            </button>
          </div>

          <button
            onClick={loadIntelligence}
            disabled={loading}
            className="px-3 py-1.5 text-xs font-medium border border-border dark:border-slate-700 rounded-lg hover:bg-surface-hover dark:hover:bg-slate-800 transition-colors disabled:opacity-50"
          >
            {loading ? 'Evaluating...' : '↻ Refresh'}
          </button>
        </div>
      </div>

      {/* Exploratory Warning Banner */}
      {allocationMode === 'EXPLORATORY_EQUAL_WEIGHT' && (
        <div className="p-3.5 bg-amber-500/10 border border-amber-500/30 rounded-lg text-amber-600 dark:text-amber-400 text-xs flex items-start gap-2.5">
          <span className="font-bold uppercase tracking-wider">Exploratory Mode Active:</span>
          <span>
            Look-through exposure is computed assuming equal allocation across all positions rather than actual capital invested.
            Use for exploratory portfolio structure analysis only. Switch to <strong>Actual Capital Allocation</strong> to reflect true portfolio values.
          </span>
        </div>
      )}

      {/* Error Banner */}
      {error && (
        <div className="p-4 bg-rose-500/10 border border-rose-500/30 rounded-lg text-rose-500 text-sm">
          Failed to load portfolio intelligence: {error}
        </div>
      )}

      {/* ------------------------------------------------------------------- */}
      {/* 2. EXECUTIVE KPI STRIP */}
      {/* ------------------------------------------------------------------- */}
      {intelligence && (
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
          <div className="p-3.5 bg-surface-raised dark:bg-slate-950 rounded-lg border border-border dark:border-slate-800">
            <div className="text-[11px] font-mono text-text-secondary dark:text-slate-400 uppercase tracking-wider">
              {allocationMode === 'ACTUAL' ? 'Total Portfolio Value' : 'Portfolio Weighting'}
            </div>
            <div className="text-lg font-bold text-text-primary dark:text-white mt-1">
              {allocationMode === 'ACTUAL'
                ? formatCurrency(intelligence.valuationBasis.totalMarketValue)
                : 'Equal-Weight (100%)'}
            </div>
            <div className="text-[11px] text-text-secondary dark:text-slate-500 mt-0.5">
              {intelligence.valuationBasis.valuedPositionsCount} of {intelligence.valuationBasis.totalPositionsCount} positions valued
            </div>
          </div>

          <div className="p-3.5 bg-surface-raised dark:bg-slate-950 rounded-lg border border-border dark:border-slate-800">
            <div className="text-[11px] font-mono text-text-secondary dark:text-slate-400 uppercase tracking-wider">
              Look-Through Coverage
            </div>
            <div className="text-lg font-bold text-cyan-600 dark:text-cyan-400 mt-1">
              {formatNumber(intelligence.coverageSummary.usableDisclosedPct, 1)}%
            </div>
            <div className="text-[11px] text-text-secondary dark:text-slate-500 mt-0.5">
              Residual/Cash: {formatNumber(intelligence.coverageSummary.undisclosedResidualPct, 1)}%
            </div>
          </div>

          <div className="p-3.5 bg-surface-raised dark:bg-slate-950 rounded-lg border border-border dark:border-slate-800">
            <div className="text-[11px] font-mono text-text-secondary dark:text-slate-400 uppercase tracking-wider">
              Top 10 Concentration
            </div>
            <div className="text-lg font-bold text-text-primary dark:text-white mt-1">
              {formatNumber(intelligence.concentrationAnalysis.top10Concentration.portfolioExposurePct, 1)}%
            </div>
            <div className="text-[11px] text-amber-500 mt-0.5 font-medium">
              Top 5: {formatNumber(intelligence.concentrationAnalysis.top5Concentration.portfolioExposurePct, 1)}%
            </div>
          </div>

          <div className="p-3.5 bg-surface-raised dark:bg-slate-950 rounded-lg border border-border dark:border-slate-800">
            <div className="text-[11px] font-mono text-text-secondary dark:text-slate-400 uppercase tracking-wider">
              HHI & Effective Size
            </div>
            <div className="text-lg font-bold text-text-primary dark:text-white mt-1">
              {formatNumber(intelligence.concentrationAnalysis.herfindahlHirschmanIndex, 0)}
            </div>
            <div className="text-[11px] text-text-secondary dark:text-slate-500 mt-0.5">
              Effective N: {formatNumber(intelligence.concentrationAnalysis.effectiveNumberOfConstituents, 1)} stocks
            </div>
          </div>

          <div className="p-3.5 bg-surface-raised dark:bg-slate-950 rounded-lg border border-border dark:border-slate-800">
            <div className="text-[11px] font-mono text-text-secondary dark:text-slate-400 uppercase tracking-wider">
              Average Overlap
            </div>
            <div className="text-lg font-bold text-text-primary dark:text-white mt-1">
              {formatNumber(intelligence.overlapAndDuplication.averagePairwiseOverlapPct, 1)}%
            </div>
            <div className="text-[11px] text-text-secondary dark:text-slate-500 mt-0.5">
              Max: {formatNumber(intelligence.overlapAndDuplication.maximumPairwiseOverlapPct, 1)}%
            </div>
          </div>

          <div className="p-3.5 bg-surface-raised dark:bg-slate-950 rounded-lg border border-border dark:border-slate-800">
            <div className="text-[11px] font-mono text-text-secondary dark:text-slate-400 uppercase tracking-wider">
              Repeated Holdings
            </div>
            <div className="text-lg font-bold text-indigo-600 dark:text-indigo-400 mt-1">
              {intelligence.overlapAndDuplication.repeatedSecuritiesCount} stocks
            </div>
            <div className="text-[11px] text-text-secondary dark:text-slate-500 mt-0.5">
              {formatNumber(intelligence.overlapAndDuplication.repeatedSecuritiesCombinedPortfolioPct, 1)}% of portfolio
            </div>
          </div>
        </div>
      )}

      {/* ------------------------------------------------------------------- */}
      {/* 3. NAVIGATION SUB-TABS */}
      {/* ------------------------------------------------------------------- */}
      <div className="flex flex-wrap gap-1.5 border-b border-border dark:border-slate-800 pb-2">
        {[
          { id: 'lookThrough', label: '1. Consolidated Look-Through' },
          { id: 'allocation', label: '2. Capital Allocation & Valuation' },
          { id: 'concentration', label: '3. Concentration & HHI' },
          { id: 'sectors', label: '4. Sectors & Asset Classes' },
          { id: 'overlap', label: '5. Overlap & Repeated Securities' },
          { id: 'drift', label: '6. Exposure Drift' },
          { id: 'brief', label: '7. Portfolio Research Brief' },
          { id: 'riskAndPerf', label: '8. Risk & Performance Guardrails' },
        ].map(tab => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id as any)}
            className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-colors ${
              activeTab === tab.id
                ? 'bg-primary text-surface-dark dark:bg-cyan-600 dark:text-white shadow-sm'
                : 'text-text-secondary hover:text-text-primary hover:bg-surface-hover dark:text-slate-400 dark:hover:text-slate-200 dark:hover:bg-slate-800'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {loading && !intelligence && (
        <div className="py-16 text-center text-text-secondary dark:text-slate-400 text-sm animate-pulse">
          Aggregating portfolio positions and computing point-in-time security look-through...
        </div>
      )}

      {/* ------------------------------------------------------------------- */}
      {/* TAB 1: CONSOLIDATED LOOK-THROUGH */}
      {/* ------------------------------------------------------------------- */}
      {intelligence && activeTab === 'lookThrough' && (
        <div className="space-y-4">
          <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 bg-surface-raised dark:bg-slate-950 p-3.5 rounded-lg border border-border dark:border-slate-800">
            <div className="flex-1 w-full sm:w-auto">
              <input
                type="text"
                placeholder="Search company, ISIN, or sector..."
                value={securitySearch}
                onChange={e => setSecuritySearch(e.target.value)}
                className="w-full sm:w-80 px-3 py-1.5 text-xs bg-surface dark:bg-slate-900 border border-border dark:border-slate-700 rounded-lg text-text-primary dark:text-white focus:outline-none focus:ring-1 focus:ring-cyan-500"
              />
            </div>
            <div className="flex items-center gap-3">
              <label className="flex items-center gap-2 text-xs text-text-secondary dark:text-slate-400 cursor-pointer">
                <input
                  type="checkbox"
                  checked={showRepeatedOnly}
                  onChange={e => setShowRepeatedOnly(e.target.checked)}
                  className="rounded border-border dark:border-slate-700 text-cyan-600 focus:ring-cyan-500"
                />
                <span>Show repeated holdings only (≥ 2 funds)</span>
              </label>
              <span className="text-xs text-text-secondary dark:text-slate-500 font-mono">
                {filteredSecurities.length} underlying securities
              </span>
            </div>
          </div>

          <div className="overflow-x-auto border border-border dark:border-slate-800 rounded-lg">
            <table className="w-full text-left text-xs">
              <thead className="bg-surface-raised dark:bg-slate-950 border-b border-border dark:border-slate-800 text-text-secondary dark:text-slate-400 font-mono uppercase tracking-wider">
                <tr>
                  <th className="py-2.5 px-3">#</th>
                  <th className="py-2.5 px-3">Underlying Security</th>
                  <th className="py-2.5 px-3">Sector</th>
                  <th className="py-2.5 px-3 text-right">Exposure Value</th>
                  <th className="py-2.5 px-3 text-right">Portfolio Exposure %</th>
                  <th className="py-2.5 px-3 text-right">Disclosed %</th>
                  <th className="py-2.5 px-3 text-center">Funds</th>
                  <th className="py-2.5 px-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border dark:divide-slate-800">
                {filteredSecurities.map((sec, idx) => {
                  const isExpanded = expandedSecurityName === sec.canonicalName;
                  return (
                    <React.Fragment key={sec.canonicalName}>
                      <tr className="hover:bg-surface-hover dark:hover:bg-slate-800/50 transition-colors">
                        <td className="py-2.5 px-3 font-mono text-text-secondary dark:text-slate-500">
                          {idx + 1}
                        </td>
                        <td className="py-2.5 px-3 font-medium text-text-primary dark:text-white">
                          <div className="flex items-center gap-2">
                            <span>{sec.canonicalName}</span>
                            {sec.isRepeatedAcrossFunds && (
                              <span className="px-1.5 py-0.5 rounded text-[10px] font-semibold bg-indigo-500/15 text-indigo-500 border border-indigo-500/30">
                                REPEATED
                              </span>
                            )}
                          </div>
                          {sec.isin && (
                            <div className="text-[10px] font-mono text-text-secondary dark:text-slate-500">
                              ISIN: {sec.isin}
                            </div>
                          )}
                        </td>
                        <td className="py-2.5 px-3 text-text-secondary dark:text-slate-300">
                          {sec.macroSector}
                        </td>
                        <td className="py-2.5 px-3 text-right font-mono font-medium">
                          {allocationMode === 'ACTUAL'
                            ? formatCurrency(sec.exposureValue)
                            : `—`}
                        </td>
                        <td className="py-2.5 px-3 text-right font-mono font-bold text-cyan-600 dark:text-cyan-400">
                          {formatNumber(sec.portfolioExposurePct, 2)}%
                        </td>
                        <td className="py-2.5 px-3 text-right font-mono text-text-secondary dark:text-slate-400">
                          {formatNumber(sec.disclosedExposurePct, 2)}%
                        </td>
                        <td className="py-2.5 px-3 text-center">
                          <span className="px-2 py-0.5 rounded-full text-xs font-mono bg-surface-raised dark:bg-slate-800 border border-border dark:border-slate-700">
                            {sec.contributingFundsCount}
                          </span>
                        </td>
                        <td className="py-2.5 px-3 text-right">
                          <button
                            onClick={() =>
                              setExpandedSecurityName(isExpanded ? null : sec.canonicalName)
                            }
                            className="text-xs text-primary hover:underline dark:text-cyan-400"
                          >
                            {isExpanded ? 'Hide' : 'Breakdown'}
                          </button>
                        </td>
                      </tr>

                      {/* Expandable Breakdown Row */}
                      {isExpanded && (
                        <tr className="bg-surface-raised/50 dark:bg-slate-950/60">
                          <td colSpan={8} className="p-3.5 pl-10">
                            <div className="text-xs font-semibold text-text-secondary dark:text-slate-300 mb-2">
                              Contributing Funds & Disclosed Weights:
                            </div>
                            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
                              {sec.contributions.map((c, cIdx) => (
                                <div
                                  key={cIdx}
                                  className="p-2.5 bg-surface dark:bg-slate-900 border border-border dark:border-slate-800 rounded-lg text-xs"
                                >
                                  <div className="font-semibold text-text-primary dark:text-white truncate">
                                    {c.fundName}
                                  </div>
                                  <div className="flex justify-between mt-1 text-[11px] text-text-secondary dark:text-slate-400">
                                    <span>Weight in Fund:</span>
                                    <span className="font-mono font-bold text-text-primary dark:text-slate-200">
                                      {formatNumber(c.weightInFundPct, 2)}%
                                    </span>
                                  </div>
                                  {allocationMode === 'ACTUAL' && (
                                    <div className="flex justify-between text-[11px] text-text-secondary dark:text-slate-400">
                                      <span>Exposure Contribution:</span>
                                      <span className="font-mono text-cyan-600 dark:text-cyan-400 font-medium">
                                        {formatCurrency(c.contributionValue)}
                                      </span>
                                    </div>
                                  )}
                                </div>
                              ))}
                            </div>
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* ------------------------------------------------------------------- */}
      {/* TAB 2: CAPITAL ALLOCATION & VALUATION BASIS */}
      {/* ------------------------------------------------------------------- */}
      {intelligence && activeTab === 'allocation' && (
        <div className="space-y-4">
          <div className="p-4 bg-surface-raised dark:bg-slate-950 rounded-lg border border-border dark:border-slate-800 text-xs space-y-1.5">
            <div className="font-bold text-text-primary dark:text-white text-sm">
              Point-in-Time Capital Valuation Basis
            </div>
            <p className="text-text-secondary dark:text-slate-400">
              Evaluated as of <strong>{intelligence.valuationBasis.asOfDate}</strong> using information published on or before <strong>{intelligence.valuationBasis.knowledgeCutoff}</strong>.
            </p>
            {intelligence.valuationBasis.valuationDisclaimers.map((d, dIdx) => (
              <div key={dIdx} className="text-amber-600 dark:text-amber-400">
                • {d}
              </div>
            ))}
          </div>

          <div className="overflow-x-auto border border-border dark:border-slate-800 rounded-lg">
            <table className="w-full text-left text-xs">
              <thead className="bg-surface-raised dark:bg-slate-950 border-b border-border dark:border-slate-800 text-text-secondary dark:text-slate-400 font-mono uppercase tracking-wider">
                <tr>
                  <th className="py-2.5 px-3">Fund / Scheme</th>
                  <th className="py-2.5 px-3">Category</th>
                  <th className="py-2.5 px-3 text-right">Units</th>
                  <th className="py-2.5 px-3 text-right">Latest NAV (Date)</th>
                  <th className="py-2.5 px-3 text-right">Market Value</th>
                  <th className="py-2.5 px-3 text-right">Portfolio Weight %</th>
                  <th className="py-2.5 px-3 text-center">Valuation State</th>
                  <th className="py-2.5 px-3 text-right">Holdings Disclosed %</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border dark:divide-slate-800">
                {intelligence.valuationBasis.positions.map((pos) => (
                  <tr key={pos.schemeOptionId} className="hover:bg-surface-hover dark:hover:bg-slate-800/50">
                    <td className="py-2.5 px-3">
                      <div className="font-semibold text-text-primary dark:text-white">
                        {pos.fundName}
                      </div>
                      <div className="text-[11px] text-text-secondary dark:text-slate-400">
                        {pos.amcName} • {pos.planType} / {pos.optionType}
                      </div>
                    </td>
                    <td className="py-2.5 px-3 text-text-secondary dark:text-slate-300">
                      {pos.category}
                    </td>
                    <td className="py-2.5 px-3 text-right font-mono">
                      {formatNumber(pos.units, 3)}
                    </td>
                    <td className="py-2.5 px-3 text-right font-mono">
                      {pos.latestNav !== null ? (
                        <>
                          <div>₹{formatNumber(pos.latestNav, 4)}</div>
                          <div className="text-[10px] text-text-secondary dark:text-slate-500">
                            {pos.navEffectiveDate || '—'}
                          </div>
                        </>
                      ) : (
                        <span className="text-rose-500">Unavailable</span>
                      )}
                    </td>
                    <td className="py-2.5 px-3 text-right font-mono font-bold">
                      {pos.marketValue !== null ? formatCurrency(pos.marketValue) : '—'}
                    </td>
                    <td className="py-2.5 px-3 text-right font-mono text-cyan-600 dark:text-cyan-400 font-semibold">
                      {pos.portfolioWeightPct !== null ? `${formatNumber(pos.portfolioWeightPct, 2)}%` : '—'}
                    </td>
                    <td className="py-2.5 px-3 text-center">
                      <span
                        className={`px-2 py-0.5 rounded text-[10px] font-semibold ${
                          pos.valuationState === 'VALUATION_AVAILABLE'
                            ? 'bg-emerald-500/15 text-emerald-500 border border-emerald-500/30'
                            : 'bg-rose-500/15 text-rose-500 border border-rose-500/30'
                        }`}
                      >
                        {pos.valuationState === 'VALUATION_AVAILABLE' ? 'VALUED' : 'UNVALUED'}
                      </span>
                    </td>
                    <td className="py-2.5 px-3 text-right font-mono">
                      {pos.holdingsDisclosedSumPct !== null ? (
                        <div>
                          <span className="font-medium">{formatNumber(pos.holdingsDisclosedSumPct, 1)}%</span>
                          <div className="text-[10px] text-text-secondary dark:text-slate-500">
                            {pos.holdingsPortfolioDate || '—'}
                          </div>
                        </div>
                      ) : (
                        <span className="text-amber-500">No Disclosures</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* ------------------------------------------------------------------- */}
      {/* TAB 3: CONCENTRATION & HHI */}
      {/* ------------------------------------------------------------------- */}
      {intelligence && activeTab === 'concentration' && (
        <div className="space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="p-4 bg-surface-raised dark:bg-slate-950 rounded-lg border border-border dark:border-slate-800 space-y-2">
              <div className="text-xs font-mono text-text-secondary dark:text-slate-400 uppercase tracking-wider">
                Top 5 Securities
              </div>
              <div className="text-2xl font-bold text-text-primary dark:text-white">
                {formatNumber(intelligence.concentrationAnalysis.top5Concentration.portfolioExposurePct, 2)}%
              </div>
              <div className="text-xs text-text-secondary dark:text-slate-400">
                Disclosed Denominator: {formatNumber(intelligence.concentrationAnalysis.top5Concentration.disclosedExposurePct, 2)}%
              </div>
              <div className="border-t border-border dark:border-slate-800 pt-2 text-[11px] text-text-secondary dark:text-slate-400 space-y-1">
                {intelligence.concentrationAnalysis.top5Concentration.securities.map((s, i) => (
                  <div key={i} className="flex justify-between">
                    <span className="truncate pr-2">{s.canonicalName}</span>
                    <span className="font-mono font-medium">{formatNumber(s.portfolioExposurePct, 2)}%</span>
                  </div>
                ))}
              </div>
            </div>

            <div className="p-4 bg-surface-raised dark:bg-slate-950 rounded-lg border border-border dark:border-slate-800 space-y-2">
              <div className="text-xs font-mono text-text-secondary dark:text-slate-400 uppercase tracking-wider">
                Top 10 Securities
              </div>
              <div className="text-2xl font-bold text-text-primary dark:text-white">
                {formatNumber(intelligence.concentrationAnalysis.top10Concentration.portfolioExposurePct, 2)}%
              </div>
              <div className="text-xs text-text-secondary dark:text-slate-400">
                Disclosed Denominator: {formatNumber(intelligence.concentrationAnalysis.top10Concentration.disclosedExposurePct, 2)}%
              </div>
              <div className="border-t border-border dark:border-slate-800 pt-2 text-[11px] text-text-secondary dark:text-slate-400 space-y-1">
                {intelligence.concentrationAnalysis.top10Concentration.securities.slice(5, 10).map((s, i) => (
                  <div key={i} className="flex justify-between">
                    <span className="truncate pr-2">{s.canonicalName}</span>
                    <span className="font-mono font-medium">{formatNumber(s.portfolioExposurePct, 2)}%</span>
                  </div>
                ))}
              </div>
            </div>

            <div className="p-4 bg-surface-raised dark:bg-slate-950 rounded-lg border border-border dark:border-slate-800 space-y-2">
              <div className="text-xs font-mono text-text-secondary dark:text-slate-400 uppercase tracking-wider">
                Concentration Index & Effective Size
              </div>
              <div className="flex items-baseline gap-2">
                <span className="text-2xl font-bold text-cyan-600 dark:text-cyan-400">
                  {formatNumber(intelligence.concentrationAnalysis.herfindahlHirschmanIndex, 0)}
                </span>
                <span className="text-xs font-mono text-text-secondary dark:text-slate-400">HHI</span>
              </div>
              <div className="text-xs font-semibold text-text-primary dark:text-white">
                {intelligence.concentrationAnalysis.concentrationClassification}
              </div>
              <p className="text-[11px] text-text-secondary dark:text-slate-400">
                {intelligence.concentrationAnalysis.hhiInterpretation}
              </p>
              <div className="border-t border-border dark:border-slate-800 pt-2 text-[11px] text-text-secondary dark:text-slate-400">
                Largest single position: <strong>{intelligence.concentrationAnalysis.largestSecurityName}</strong> ({formatNumber(intelligence.concentrationAnalysis.largestIndividualSecurityPct, 2)}%)
              </div>
            </div>
          </div>

          <div className="p-3 bg-surface-raised/50 dark:bg-slate-950/40 rounded-lg border border-border dark:border-slate-800 text-xs text-text-secondary dark:text-slate-400">
            <strong>Denominator Basis Note:</strong> {intelligence.concentrationAnalysis.denominatorBasisNote}
          </div>
        </div>
      )}

      {/* ------------------------------------------------------------------- */}
      {/* TAB 4: SECTORS & ASSET CLASSES */}
      {/* ------------------------------------------------------------------- */}
      {intelligence && activeTab === 'sectors' && (
        <div className="space-y-6">
          <div className="space-y-3">
            <h3 className="text-sm font-bold text-text-primary dark:text-white uppercase tracking-wider font-mono">
              Consolidated Macro-Sectors
            </h3>
            <div className="overflow-x-auto border border-border dark:border-slate-800 rounded-lg">
              <table className="w-full text-left text-xs">
                <thead className="bg-surface-raised dark:bg-slate-950 border-b border-border dark:border-slate-800 text-text-secondary dark:text-slate-400 font-mono uppercase tracking-wider">
                  <tr>
                    <th className="py-2.5 px-3">Sector</th>
                    <th className="py-2.5 px-3 text-right">Exposure Value</th>
                    <th className="py-2.5 px-3 text-right">Portfolio %</th>
                    <th className="py-2.5 px-3 text-right">Disclosed %</th>
                    <th className="py-2.5 px-3 text-center">Securities</th>
                    <th className="py-2.5 px-3 text-center">Funds</th>
                    <th className="py-2.5 px-3">Top Underlying Constituents</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border dark:divide-slate-800">
                  {intelligence.sectorAndAssetClass.sectors.map((sec) => (
                    <tr key={sec.sectorName} className="hover:bg-surface-hover dark:hover:bg-slate-800/50">
                      <td className="py-2.5 px-3 font-semibold text-text-primary dark:text-white">
                        {sec.sectorName}
                      </td>
                      <td className="py-2.5 px-3 text-right font-mono">
                        {allocationMode === 'ACTUAL' ? formatCurrency(sec.exposureValue) : '—'}
                      </td>
                      <td className="py-2.5 px-3 text-right font-mono font-bold text-cyan-600 dark:text-cyan-400">
                        {formatNumber(sec.portfolioExposurePct, 2)}%
                      </td>
                      <td className="py-2.5 px-3 text-right font-mono text-text-secondary dark:text-slate-400">
                        {formatNumber(sec.disclosedExposurePct, 2)}%
                      </td>
                      <td className="py-2.5 px-3 text-center font-mono">
                        {sec.securitiesCount}
                      </td>
                      <td className="py-2.5 px-3 text-center font-mono">
                        {sec.contributingFundsCount}
                      </td>
                      <td className="py-2.5 px-3 text-text-secondary dark:text-slate-400 truncate max-w-xs">
                        {sec.topSecurities.map((s) => s.canonicalName).join(', ')}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* Asset Class Distribution */}
          <div className="space-y-3">
            <h3 className="text-sm font-bold text-text-primary dark:text-white uppercase tracking-wider font-mono">
              Asset Class Allocation
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
              {intelligence.sectorAndAssetClass.assetClasses.map((ac) => (
                <div
                  key={ac.assetClass}
                  className="p-3.5 bg-surface-raised dark:bg-slate-950 rounded-lg border border-border dark:border-slate-800 space-y-1"
                >
                  <div className="text-xs font-mono text-text-secondary dark:text-slate-400 uppercase tracking-wider">
                    {ac.assetClass}
                  </div>
                  <div className="text-xl font-bold text-text-primary dark:text-white">
                    {formatNumber(ac.portfolioExposurePct, 2)}%
                  </div>
                  {allocationMode === 'ACTUAL' && (
                    <div className="text-xs font-mono text-cyan-600 dark:text-cyan-400">
                      {formatCurrency(ac.exposureValue)}
                    </div>
                  )}
                  <p className="text-[11px] text-text-secondary dark:text-slate-500 pt-1 border-t border-border dark:border-slate-800">
                    {ac.disclosureNote}
                  </p>
                </div>
              ))}
            </div>
            <div className="p-3 bg-surface-raised/50 dark:bg-slate-950/40 rounded-lg border border-border dark:border-slate-800 text-xs text-text-secondary dark:text-slate-400">
              {intelligence.sectorAndAssetClass.assetClassDisclaimer}
            </div>
          </div>
        </div>
      )}

      {/* ------------------------------------------------------------------- */}
      {/* TAB 5: OVERLAP & REPEATED SECURITIES */}
      {/* ------------------------------------------------------------------- */}
      {intelligence && activeTab === 'overlap' && (
        <div className="space-y-6">
          {/* Interpretive Guidance */}
          <div className="p-3.5 bg-cyan-500/10 border border-cyan-500/30 rounded-lg text-cyan-600 dark:text-cyan-400 text-xs leading-relaxed">
            <strong>Analytical Interpretation:</strong> {intelligence.overlapAndDuplication.interpretiveGuidance}
          </div>

          {/* Repeated Underlying Holdings Table */}
          <div className="space-y-3">
            <h3 className="text-sm font-bold text-text-primary dark:text-white uppercase tracking-wider font-mono">
              Repeated Underlying Holdings (Held across ≥ 2 Schemes)
            </h3>
            <div className="overflow-x-auto border border-border dark:border-slate-800 rounded-lg">
              <table className="w-full text-left text-xs">
                <thead className="bg-surface-raised dark:bg-slate-950 border-b border-border dark:border-slate-800 text-text-secondary dark:text-slate-400 font-mono uppercase tracking-wider">
                  <tr>
                    <th className="py-2.5 px-3">Security Name</th>
                    <th className="py-2.5 px-3">Sector</th>
                    <th className="py-2.5 px-3 text-center">Funds Count</th>
                    <th className="py-2.5 px-3 text-right">Combined Value</th>
                    <th className="py-2.5 px-3 text-right">Portfolio Exposure %</th>
                    <th className="py-2.5 px-3">Contributing Funds</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border dark:divide-slate-800">
                  {intelligence.overlapAndDuplication.repeatedSecurities.map((rep) => (
                    <tr key={rep.canonicalName} className="hover:bg-surface-hover dark:hover:bg-slate-800/50">
                      <td className="py-2.5 px-3 font-semibold text-text-primary dark:text-white">
                        {rep.canonicalName}
                        {rep.isin && <div className="text-[10px] font-mono text-text-secondary dark:text-slate-500">{rep.isin}</div>}
                      </td>
                      <td className="py-2.5 px-3 text-text-secondary dark:text-slate-300">
                        {rep.sector}
                      </td>
                      <td className="py-2.5 px-3 text-center">
                        <span className="px-2 py-0.5 rounded-full text-xs font-mono bg-indigo-500/15 text-indigo-500 font-bold border border-indigo-500/30">
                          {rep.contributingFundsCount}
                        </span>
                      </td>
                      <td className="py-2.5 px-3 text-right font-mono">
                        {allocationMode === 'ACTUAL' ? formatCurrency(rep.totalExposureValue) : '—'}
                      </td>
                      <td className="py-2.5 px-3 text-right font-mono font-bold text-cyan-600 dark:text-cyan-400">
                        {formatNumber(rep.portfolioExposurePct, 2)}%
                      </td>
                      <td className="py-2.5 px-3 text-text-secondary dark:text-slate-400">
                        <div className="flex flex-wrap gap-1">
                          {rep.contributions.map((c, i) => (
                            <span key={i} className="px-1.5 py-0.5 rounded text-[10px] bg-surface-raised dark:bg-slate-800 border border-border dark:border-slate-700">
                              {c.fundName} ({formatNumber(c.weightInFundPct, 1)}%)
                            </span>
                          ))}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* Pairwise Overlap Matrix */}
          <div className="space-y-3">
            <h3 className="text-sm font-bold text-text-primary dark:text-white uppercase tracking-wider font-mono">
              Pairwise Overlap Matrix (Intersection of Holdings)
            </h3>
            <div className="overflow-x-auto border border-border dark:border-slate-800 rounded-lg">
              <table className="w-full text-left text-xs">
                <thead className="bg-surface-raised dark:bg-slate-950 border-b border-border dark:border-slate-800 text-text-secondary dark:text-slate-400 font-mono uppercase tracking-wider">
                  <tr>
                    <th className="py-2.5 px-3">Fund A</th>
                    <th className="py-2.5 px-3">Fund B</th>
                    <th className="py-2.5 px-3 text-right">Overlap Weight %</th>
                    <th className="py-2.5 px-3 text-center">Shared Stocks</th>
                    <th className="py-2.5 px-3 text-right">Disclosure Timing Delta</th>
                    <th className="py-2.5 px-3 text-center">Comparability</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border dark:divide-slate-800">
                  {intelligence.overlapAndDuplication.pairwiseOverlapMatrix.map((pair, idx) => (
                    <tr key={idx} className="hover:bg-surface-hover dark:hover:bg-slate-800/50">
                      <td className="py-2.5 px-3 font-medium text-text-primary dark:text-white">
                        {pair.fundAName}
                      </td>
                      <td className="py-2.5 px-3 font-medium text-text-primary dark:text-white">
                        {pair.fundBName}
                      </td>
                      <td className="py-2.5 px-3 text-right font-mono font-bold text-cyan-600 dark:text-cyan-400">
                        {formatNumber(pair.overlapWeightPct, 2)}%
                      </td>
                      <td className="py-2.5 px-3 text-center font-mono">
                        {pair.sharedSecurityCount}
                      </td>
                      <td className="py-2.5 px-3 text-right font-mono text-text-secondary dark:text-slate-400">
                        {pair.temporalDeltaDays} days
                      </td>
                      <td className="py-2.5 px-3 text-center">
                        <span
                          className={`px-2 py-0.5 rounded text-[10px] font-semibold ${
                            pair.comparabilityState === 'COMPARABLE_DATES'
                              ? 'bg-emerald-500/15 text-emerald-500 border border-emerald-500/30'
                              : 'bg-amber-500/15 text-amber-500 border border-amber-500/30'
                          }`}
                        >
                          {pair.comparabilityState === 'COMPARABLE_DATES' ? 'COMPARABLE' : 'NON-COMPARABLE'}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* ------------------------------------------------------------------- */}
      {/* TAB 6: EXPOSURE DRIFT */}
      {/* ------------------------------------------------------------------- */}
      {intelligence && activeTab === 'drift' && (
        <div className="space-y-4">
          <div className="p-4 bg-surface-raised dark:bg-slate-950 rounded-lg border border-border dark:border-slate-800 space-y-2">
            <div className="font-bold text-text-primary dark:text-white text-sm">
              Portfolio Exposure Drift & Disclosure Timing
            </div>
            <p className="text-xs text-text-secondary dark:text-slate-400">
              {intelligence.exposureDrift.driftDisclaimer}
            </p>
          </div>

          {intelligence.exposureDrift.driftStatus !== 'ANALYSIS_AVAILABLE' ? (
            <div className="p-8 text-center bg-surface-raised/40 dark:bg-slate-950/40 rounded-lg border border-dashed border-border dark:border-slate-800 space-y-2">
              <div className="text-sm font-semibold text-text-primary dark:text-white">
                Insufficient Historical Monthly Snapshots
              </div>
              <p className="text-xs text-text-secondary dark:text-slate-400 max-w-lg mx-auto">
                Calculating multi-period exposure drift requires at least two distinct monthly portfolio disclosure snapshots for constituent funds in the database.
                As monthly AMFI/AMC portfolio disclosures are ingested, drift analytics will activate automatically.
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="p-4 bg-surface-raised dark:bg-slate-950 rounded-lg border border-border dark:border-slate-800 space-y-3">
                <div className="text-xs font-mono text-emerald-500 uppercase tracking-wider font-bold">
                  Top Increased Underlying Holdings
                </div>
                <div className="space-y-2">
                  {intelligence.exposureDrift.topIncreasedSecurities.map((item, i) => (
                    <div key={i} className="flex justify-between items-center text-xs">
                      <span className="text-text-primary dark:text-white">{item.canonicalName}</span>
                      <span className="font-mono font-bold text-emerald-500">
                        +{formatNumber(item.weightDeltaPct, 2)}%
                      </span>
                    </div>
                  ))}
                </div>
              </div>

              <div className="p-4 bg-surface-raised dark:bg-slate-950 rounded-lg border border-border dark:border-slate-800 space-y-3">
                <div className="text-xs font-mono text-rose-500 uppercase tracking-wider font-bold">
                  Top Decreased Underlying Holdings
                </div>
                <div className="space-y-2">
                  {intelligence.exposureDrift.topDecreasedSecurities.map((item, i) => (
                    <div key={i} className="flex justify-between items-center text-xs">
                      <span className="text-text-primary dark:text-white">{item.canonicalName}</span>
                      <span className="font-mono font-bold text-rose-500">
                        {formatNumber(item.weightDeltaPct, 2)}%
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* ------------------------------------------------------------------- */}
      {/* TAB 7: PORTFOLIO RESEARCH BRIEF & FOLLOW-UP */}
      {/* ------------------------------------------------------------------- */}
      {intelligence && activeTab === 'brief' && (
        <div className="space-y-4">
          <div className="p-4 bg-cyan-500/10 border border-cyan-500/30 rounded-lg space-y-1.5">
            <div className="text-xs font-mono text-cyan-600 dark:text-cyan-400 uppercase tracking-wider">
              Portfolio Research Brief • Generated Point-in-Time
            </div>
            <div className="text-sm font-bold text-text-primary dark:text-white">
              {intelligence.researchBrief.portfolioSummaryHeadline}
            </div>
          </div>

          <div className="space-y-3">
            <h3 className="text-sm font-bold text-text-primary dark:text-white uppercase tracking-wider font-mono">
              Categorized Portfolio Findings
            </h3>
            <div className="grid grid-cols-1 gap-3">
              {intelligence.researchBrief.findings.map((f) => (
                <div
                  key={f.findingId}
                  className="p-4 bg-surface-raised dark:bg-slate-950 rounded-lg border border-border dark:border-slate-800 space-y-2"
                >
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <span
                        className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider ${
                          f.findingType === 'FACT'
                            ? 'bg-blue-500/15 text-blue-500 border border-blue-500/30'
                            : f.findingType === 'CALCULATED_EXPOSURE'
                            ? 'bg-cyan-500/15 text-cyan-500 border border-cyan-500/30'
                            : f.findingType === 'INTERPRETATION'
                            ? 'bg-indigo-500/15 text-indigo-500 border border-indigo-500/30'
                            : f.findingType === 'RISK_LIMITATION'
                            ? 'bg-amber-500/15 text-amber-500 border border-amber-500/30'
                            : 'bg-slate-500/15 text-slate-400 border border-slate-500/30'
                        }`}
                      >
                        {f.findingType}
                      </span>
                      <span className="text-xs font-mono text-text-secondary dark:text-slate-400 uppercase">
                        {f.category}
                      </span>
                    </div>

                    <button
                      onClick={() => handleOpenFollowUp(f)}
                      className="px-2.5 py-1 text-xs font-medium bg-primary/10 text-primary border border-primary/30 rounded-md hover:bg-primary/20 dark:bg-cyan-500/15 dark:text-cyan-400 dark:border-cyan-500/30 transition-colors"
                    >
                      + Log Follow-Up to Workspace
                    </button>
                  </div>

                  <div className="font-semibold text-text-primary dark:text-white text-sm">
                    {f.headline}
                  </div>
                  <p className="text-xs text-text-secondary dark:text-slate-300 leading-relaxed">
                    {f.detailedEvidence}
                  </p>
                  <div className="pt-2 border-t border-border dark:border-slate-800/80 text-xs text-text-secondary dark:text-slate-400">
                    <strong>Suggested Investigation:</strong> {f.suggestedInvestigation}
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Recommended Investor Questions */}
          <div className="p-4 bg-surface-raised dark:bg-slate-950 rounded-lg border border-border dark:border-slate-800 space-y-2">
            <div className="text-xs font-bold font-mono text-text-primary dark:text-white uppercase tracking-wider">
              Recommended Investor Follow-Up Questions
            </div>
            <div className="space-y-1.5 text-xs text-text-secondary dark:text-slate-300">
              {intelligence.researchBrief.recommendedInvestorQuestions.map((q, i) => (
                <div key={i} className="flex items-start gap-2">
                  <span className="text-cyan-500 font-bold">›</span>
                  <span>{q}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* ------------------------------------------------------------------- */}
      {/* TAB 8: RISK CONTEXT & INVESTOR PERFORMANCE BOUNDARIES */}
      {/* ------------------------------------------------------------------- */}
      {intelligence && activeTab === 'riskAndPerf' && (
        <div className="space-y-6">
          {/* Strict Risk Scope Guardrail */}
          <div className="p-4 bg-amber-500/10 border border-amber-500/30 rounded-lg space-y-2 text-xs">
            <div className="font-bold text-amber-600 dark:text-amber-400 text-sm flex items-center gap-2">
              <span>⚠️ Institutional Risk Boundary: No Synthetic Portfolio Volatility</span>
            </div>
            <p className="text-text-secondary dark:text-slate-300 leading-relaxed">
              {intelligence.riskContext.portfolioVolatilityDisclaimer}
            </p>
            <p className="text-text-secondary dark:text-slate-300 leading-relaxed">
              {intelligence.riskContext.portfolioSharpeDisclaimer}
            </p>
            <div className="pt-2 border-t border-amber-500/20 text-[11px] text-amber-500 font-mono">
              Governance Status: {intelligence.riskContext.governanceNote}
            </div>
          </div>

          {/* Constituent Fund Standalone Risk Profile Table */}
          <div className="space-y-3">
            <h3 className="text-sm font-bold text-text-primary dark:text-white uppercase tracking-wider font-mono">
              Constituent Fund Standalone Quantitative Risks (Point-in-Time)
            </h3>
            <div className="overflow-x-auto border border-border dark:border-slate-800 rounded-lg">
              <table className="w-full text-left text-xs">
                <thead className="bg-surface-raised dark:bg-slate-950 border-b border-border dark:border-slate-800 text-text-secondary dark:text-slate-400 font-mono uppercase tracking-wider">
                  <tr>
                    <th className="py-2.5 px-3">Fund Name</th>
                    <th className="py-2.5 px-3 text-right">Portfolio Weight %</th>
                    <th className="py-2.5 px-3 text-right">Ann. Volatility (RSK-01)</th>
                    <th className="py-2.5 px-3 text-right">Downside Semideviation</th>
                    <th className="py-2.5 px-3 text-right">Max Drawdown (RSK-03)</th>
                    <th className="py-2.5 px-3 text-right">Beta to Benchmark</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border dark:divide-slate-800">
                  {intelligence.riskContext.constituentRisks.map((c) => (
                    <tr key={c.schemeOptionId} className="hover:bg-surface-hover dark:hover:bg-slate-800/50">
                      <td className="py-2.5 px-3 font-semibold text-text-primary dark:text-white">
                        {c.fundName}
                      </td>
                      <td className="py-2.5 px-3 text-right font-mono font-medium">
                        {formatNumber(c.portfolioWeightPct, 2)}%
                      </td>
                      <td className="py-2.5 px-3 text-right font-mono text-cyan-600 dark:text-cyan-400">
                        {c.annualizedVolatility !== null ? `${formatNumber(c.annualizedVolatility * 100, 2)}%` : '—'}
                      </td>
                      <td className="py-2.5 px-3 text-right font-mono text-text-secondary dark:text-slate-400">
                        {c.downsideSemideviation !== null ? `${formatNumber(c.downsideSemideviation * 100, 2)}%` : '—'}
                      </td>
                      <td className="py-2.5 px-3 text-right font-mono text-rose-500 font-medium">
                        {c.maxDrawdownPct !== null ? `${formatNumber(c.maxDrawdownPct, 2)}%` : '—'}
                      </td>
                      <td className="py-2.5 px-3 text-right font-mono text-text-secondary dark:text-slate-400">
                        {c.betaToCategoryBenchmark !== null ? formatNumber(c.betaToCategoryBenchmark, 2) : '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* Performance Distinction Guardrail */}
          <div className="p-4 bg-surface-raised dark:bg-slate-950 rounded-lg border border-border dark:border-slate-800 space-y-3">
            <div className="font-bold text-text-primary dark:text-white text-sm">
              Investor Performance Distinction (NAV Trailing Return vs Money-Weighted Return)
            </div>
            <p className="text-xs text-text-secondary dark:text-slate-300 leading-relaxed">
              {intelligence.investorPerformance.moneyWeightedReturnExplanation}
            </p>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 pt-2">
              <div className="p-3 bg-surface dark:bg-slate-900 border border-border dark:border-slate-800 rounded-lg">
                <div className="text-[11px] font-mono text-text-secondary dark:text-slate-400 uppercase">
                  Invested Capital
                </div>
                <div className="text-lg font-bold text-text-primary dark:text-white mt-1">
                  {intelligence.investorPerformance.totalInvestedAmount !== null
                    ? formatCurrency(intelligence.investorPerformance.totalInvestedAmount)
                    : 'Transaction History Absent'}
                </div>
              </div>
              <div className="p-3 bg-surface dark:bg-slate-900 border border-border dark:border-slate-800 rounded-lg">
                <div className="text-[11px] font-mono text-text-secondary dark:text-slate-400 uppercase">
                  Current Market Value
                </div>
                <div className="text-lg font-bold text-cyan-600 dark:text-cyan-400 mt-1">
                  {formatCurrency(intelligence.investorPerformance.currentPortfolioMarketValue)}
                </div>
              </div>
              <div className="p-3 bg-surface dark:bg-slate-900 border border-border dark:border-slate-800 rounded-lg">
                <div className="text-[11px] font-mono text-text-secondary dark:text-slate-400 uppercase">
                  Investor XIRR (Personal Return)
                </div>
                <div className="text-lg font-bold text-amber-500 mt-1">
                  Not Available
                </div>
                <div className="text-[10px] text-text-secondary dark:text-slate-500">
                  Requires complete dated transaction cash flows
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ------------------------------------------------------------------- */}
      {/* 4. FOLLOW-UP LOGGING MODAL */}
      {/* ------------------------------------------------------------------- */}
      {followUpFinding && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4">
          <div className="bg-surface dark:bg-slate-900 border border-border dark:border-slate-800 rounded-xl p-6 max-w-lg w-full shadow-2xl text-text-primary dark:text-slate-100 space-y-4">
            <div className="flex items-center justify-between border-b border-border dark:border-slate-800 pb-3">
              <h3 className="font-bold text-base text-text-primary dark:text-white">
                Log Follow-Up Investigation
              </h3>
              <button
                onClick={() => setFollowUpFinding(null)}
                className="text-text-secondary hover:text-text-primary dark:text-slate-400 dark:hover:text-white text-lg font-bold"
              >
                ✕
              </button>
            </div>

            <div className="space-y-3 text-xs">
              <div>
                <label className="block text-text-secondary dark:text-slate-400 uppercase font-mono mb-1">
                  Associate with Portfolio Holding
                </label>
                <select
                  value={selectedHoldingIdForFollowUp || ''}
                  onChange={(e) => setSelectedHoldingIdForFollowUp(Number(e.target.value))}
                  className="w-full bg-surface-raised dark:bg-slate-950 border border-border dark:border-slate-700 rounded-lg p-2 text-text-primary dark:text-white"
                >
                  {holdings.map((h) => (
                    <option key={h.id} value={h.id}>
                      {h.fundName} ({h.amcName})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-text-secondary dark:text-slate-400 uppercase font-mono mb-1">
                  Investigation Question
                </label>
                <textarea
                  value={followUpQuestion}
                  onChange={(e) => setFollowUpQuestion(e.target.value)}
                  rows={3}
                  className="w-full bg-surface-raised dark:bg-slate-950 border border-border dark:border-slate-700 rounded-lg p-2 text-text-primary dark:text-white focus:outline-none focus:ring-1 focus:ring-cyan-500"
                />
              </div>

              <div>
                <label className="block text-text-secondary dark:text-slate-400 uppercase font-mono mb-1">
                  Next Step / Follow-Up Action
                </label>
                <input
                  type="text"
                  value={followUpNextStep}
                  onChange={(e) => setFollowUpNextStep(e.target.value)}
                  className="w-full bg-surface-raised dark:bg-slate-950 border border-border dark:border-slate-700 rounded-lg p-2 text-text-primary dark:text-white focus:outline-none focus:ring-1 focus:ring-cyan-500"
                />
              </div>

              {followUpSuccessMsg && (
                <div className="p-2.5 bg-emerald-500/10 border border-emerald-500/30 rounded text-emerald-500 font-semibold text-center">
                  {followUpSuccessMsg}
                </div>
              )}
            </div>

            <div className="flex justify-end gap-2 pt-2 border-t border-border dark:border-slate-800">
              <button
                type="button"
                onClick={() => setFollowUpFinding(null)}
                className="px-4 py-2 text-xs font-semibold rounded-lg border border-border dark:border-slate-700 hover:bg-surface-hover dark:hover:bg-slate-800 text-text-secondary dark:text-slate-300"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleSaveFollowUp}
                disabled={followUpSubmitting}
                className="px-4 py-2 text-xs font-semibold rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white shadow disabled:opacity-50"
              >
                {followUpSubmitting ? 'Saving...' : 'Save Follow-Up'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
