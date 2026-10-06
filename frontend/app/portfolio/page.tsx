'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { PageContainer, SectionHeading } from '@/components/layout/PageContainer';
import { SchemeOption } from '@/types/domain';
import { fetchAllSchemeOptions } from '@/lib/api/schemes';
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

export default function PortfolioPage() {
  const [summary, setSummary] = useState<PortfolioSummaryDto | null>(null);
  const [options, setOptions] = useState<SchemeOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isImportModalOpen, setIsImportModalOpen] = useState(false);
  const [isReportModalOpen, setIsReportModalOpen] = useState(false);

  // Form state
  const [selectedOptionId, setSelectedOptionId] = useState<string>('');
  const [unitsInput, setUnitsInput] = useState<string>('');
  const [costBasisInput, setCostBasisInput] = useState<string>('');

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

  const handleAddHolding = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedOptionId || !unitsInput) return;

    try {
      setSubmitting(true);
      setError(null);
      const optionId = parseInt(selectedOptionId, 10);
      const units = parseFloat(unitsInput);
      const costBasis = costBasisInput ? parseFloat(costBasisInput) : undefined;

      await addPortfolioHolding(optionId, units, costBasis);

      // Reset form
      setSelectedOptionId('');
      setUnitsInput('');
      setCostBasisInput('');

      // Reload summary
      const updatedSummary = await fetchPortfolioSummary();
      setSummary(updatedSummary);
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to add holding');
    } finally {
      setSubmitting(false);
    }
  };

  const handleRemoveHolding = async (holding: PortfolioHoldingDto) => {
    try {
      setSubmitting(true);
      setError(null);
      await removePortfolioHolding(holding.schemeOptionId);
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

        <div className="flex items-center gap-2">
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
            onClick={() => setIsImportModalOpen(true)}
            className="px-4 py-2 rounded bg-accent text-surface-dark font-mono text-[12px] font-medium hover:bg-accent/90 flex items-center gap-2"
          >
            <span>+ Import External Mutual Funds</span>
          </button>
        </div>
      </div>

      <PortfolioReportModal
        isOpen={isReportModalOpen}
        onClose={() => setIsReportModalOpen(false)}
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
      {/* Overview Top Metric Cards */}
      <div className="mb-6 grid grid-cols-1 gap-3 sm:grid-cols-5">
        <div className="metric-tile">
          <span className="eyebrow">Total Holdings</span>
          <div className="metric-rule" />
          <p className="data-value-lg">{summary ? summary.totalHoldings : 0}</p>
          <p className="mt-auto pt-2 font-mono text-[11px] text-text-tertiary">
            Externally tracked scheme options
          </p>
        </div>

        <div className="metric-tile">
          <span className="eyebrow">Total Available Value</span>
          <div className="metric-rule" />
          {summary && summary.totalAvailableValue !== null ? (
            <p className="data-value-lg">{formatCurrency(summary.totalAvailableValue)}</p>
          ) : (
            <p className="data-value-lg data-unavailable">Valuation Unavailable</p>
          )}
          <p className="mt-auto pt-2 font-mono text-[11px] text-text-tertiary">
            Deterministic valuation from NAV ledger
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
              <span className="data-value-lg data-unavailable">â€”</span>
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

        <div className="metric-tile">
          <span className="eyebrow">Valuation Coverage</span>
          <div className="metric-rule" />
          <div className="flex items-center gap-2">
            <span className="data-value-lg font-mono">
              {summary ? `${summary.valuedHoldingsCount}/${summary.totalHoldings}` : '0/0'}
            </span>
            {summary && (
              <span className={`status-badge ${
                summary.valuationCoverageState === 'VALUATION_COMPLETE' ? 'state-verified' :
                summary.valuationCoverageState === 'VALUATION_PARTIAL' ? 'state-candidate' : 'state-unavailable'
              }`}>
                {summary.valuationCoverageState}
              </span>
            )}
          </div>
          <p className="mt-auto pt-2 font-mono text-[11px] text-text-tertiary">
            Holdings with verified NAV data
          </p>
        </div>

        <div className="metric-tile">
          <span className="eyebrow">Analytical Coverage</span>
          <div className="metric-rule" />
          <div className="flex items-center gap-2">
            <span className="data-value-lg font-mono">
              {summary ? `${summary.scoredHoldingsCount}/${summary.totalHoldings}` : '0/0'}
            </span>
            <span className="status-badge state-candidate">YUKIRA_SCORE_V1</span>
          </div>
          <p className="mt-auto pt-2 font-mono text-[11px] text-text-tertiary">
            Funds with analytical quality scores
          </p>
        </div>
      </div>

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
                        : 'â€”'}
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
                              {c.portfolioWeight !== null ? `${c.portfolioWeight.toFixed(2)}%` : 'â€”'}
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
                              {hEv.yukiraScore !== null ? hEv.yukiraScore.toFixed(2) : 'â€”'}
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
                                    N/A â€” insufficient data
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
                    <p key={idx}>â€¢ {lim}</p>
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
                      : 'â€”'}
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
                      : 'â€”'}
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
                      : 'â€”'}
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
                      : 'â€”'}
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
                    <div key={holding.id} className="panel">
                      <div className="panel-header flex-wrap gap-2">
                        <div className="min-w-0 flex-1">
                          <Link
                            href={`/funds/${holding.schemeOptionId}`}
                            className="text-[15px] font-semibold text-text-primary hover:text-accent"
                          >
                            {holding.fundName}
                          </Link>
                          <div className="mt-1.5 flex flex-wrap items-center gap-2 font-mono text-[11px]">
                            <span className="status-badge state-verified">{holding.planType}</span>
                            <span className="status-badge state-verified">{holding.optionType}</span>
                            <span className="text-text-tertiary">AMC: {holding.amcName}</span>
                            <span className="text-text-tertiary">AMFI: {holding.amfiCode}</span>
                            <span className="text-text-tertiary">ISIN: {holding.isin}</span>
                          </div>
                        </div>

                        <div className="flex items-center gap-2 shrink-0">
                          <Link
                            href={`/funds/${holding.schemeOptionId}#scorecard`}
                            className="btn btn-secondary btn-sm flex items-center gap-1"
                          >
                            <span>Open Scorecard</span>
                            <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
                            </svg>
                          </Link>
                          <Link
                            href={`/funds/${holding.schemeOptionId}`}
                            className="btn btn-secondary btn-sm"
                          >
                            Profile
                          </Link>
                          <button
                            onClick={() => handleRemoveHolding(holding)}
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
                          <dt className="def-label">Available Value</dt>
                          <dd className="data-value-sm font-mono">
                            {holding.availableValue !== null ? (
                              formatCurrency(holding.availableValue)
                            ) : (
                              <span className="text-text-disabled">Unavailable</span>
                            )}
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
                            <span>â€¢</span>
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
                                    {dim.score !== null && dim.score !== undefined ? dim.score.toFixed(2) : "â€”"}
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
                <h3 className="eyebrow mb-3">Exposure by Category</h3>
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
                <h3 className="eyebrow mb-3">Exposure by AMC</h3>
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

          {/* Data Quality & Investigation Panel */}
          {summary && (
            <div className="panel p-4 space-y-4">
              <h3 className="eyebrow text-accent">Data Quality Evidence & Investigation</h3>

              {summary.dataQualityLimitations.length > 0 && (
                <div>
                  <h4 className="text-[12px] font-semibold text-text-secondary mb-1.5">Data Quality Limitations</h4>
                  <ul className="list-disc list-inside space-y-1 text-[12px] text-text-tertiary font-mono">
                    {summary.dataQualityLimitations.map((lim, idx) => (
                      <li key={idx}>{lim}</li>
                    ))}
                  </ul>
                </div>
              )}

              {summary.investigationQuestions.length > 0 && (
                <div className="border-t border-border pt-3">
                  <h4 className="text-[12px] font-semibold text-text-secondary mb-1.5">Actionable Investigation Questions</h4>
                  <ul className="list-disc list-inside space-y-1 text-[12px] text-accent font-mono">
                    {summary.investigationQuestions.map((q, idx) => (
                      <li key={idx}>{q}</li>
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
                      <option value="">â€” Select exact fund option â€”</option>
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

                  <button
                    type="submit"
                    disabled={submitting || !selectedOptionId || !unitsInput}
                    className="btn btn-primary w-full"
                  >
                    {submitting ? 'Adding Holdingâ€¦' : 'Add External Holding'}
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
    </PageContainer>
  );
}
