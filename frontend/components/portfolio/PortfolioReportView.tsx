'use client';

import React, { useState } from 'react';
import {
  fetchPortfolioReport,
  PortfolioReportDto,
  PortfolioGoalAlignmentRequest
} from '@/lib/api/portfolio';
import { formatCurrency } from '@/lib/utils/formatters';

interface PortfolioReportModalProps {
  isOpen: boolean;
  onClose: () => void;
  goalRequest?: PortfolioGoalAlignmentRequest;
}

export function PortfolioReportModal({ isOpen, onClose, goalRequest }: PortfolioReportModalProps) {
  const [report, setReport] = useState<PortfolioReportDto | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const handleGenerateReport = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchPortfolioReport(goalRequest);
      setReport(data);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to generate portfolio evidence report';
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  React.useEffect(() => {
    if (!isOpen) return;

    let isMounted = true;

    async function loadReport() {
      try {
        const data = await fetchPortfolioReport(goalRequest);
        if (isMounted) {
          setReport(data);
          setError(null);
        }
      } catch (err: unknown) {
        if (isMounted) {
          const message = err instanceof Error ? err.message : 'Failed to generate portfolio evidence report';
          setError(message);
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    }

    loadReport();

    return () => {
      isMounted = false;
    };
  }, [isOpen, goalRequest]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-background/80 backdrop-blur-sm flex items-center justify-center p-4 print:p-0 print:bg-white print:static print:inset-auto">
      <style jsx global>{`
        @media print {
          body * {
            visibility: hidden;
          }
          #print-report-container, #print-report-container * {
            visibility: visible;
          }
          #print-report-container {
            position: absolute;
            left: 0;
            top: 0;
            width: 100%;
            background: white !important;
            color: black !important;
          }
          .no-print {
            display: none !important;
          }
        }
      `}</style>

      <div className="bg-surface border border-border rounded-xl max-w-5xl w-full max-h-[90vh] flex flex-col shadow-2xl overflow-hidden print:max-h-none print:border-none print:shadow-none print:bg-white print:text-black">
        {/* Modal Header */}
        <div className="p-4 border-b border-border flex items-center justify-between bg-surface-inset no-print">
          <div className="flex items-center space-x-3">
            <div className="p-2 bg-accent/10 border border-accent/20 rounded-lg text-accent">
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
            </div>
            <div>
              <h2 className="text-lg font-semibold text-text-primary">YUKIRA Portfolio Evidence Report V1</h2>
              <p className="text-xs text-text-secondary">Point-in-Time analytical snapshot & auditable evidence</p>
            </div>
          </div>
          <div className="flex items-center space-x-3">
            {report && (
              <button
                onClick={() => window.print()}
                className="btn btn-primary btn-sm flex items-center space-x-1.5"
              >
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 17h2a2 2 0 002-2v-4a2 2 0 00-2-2H5a2 2 0 00-2 2v4a2 2 0 002 2h2m2 4h6a2 2 0 002-2v-4a2 2 0 00-2-2H9a2 2 0 00-2 2v4a2 2 0 002 2zm8-12V5a2 2 0 00-2-2H9a2 2 0 00-2 2v4h10z" />
                </svg>
                <span>Print / Save PDF</span>
              </button>
            )}
            <button
              onClick={onClose}
              className="text-text-secondary hover:text-text-primary p-1.5 rounded-lg hover:bg-surface-raised transition-colors"
            >
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        </div>

        {/* Modal Body / Printable Report */}
        <div className="p-6 overflow-y-auto space-y-6 print:p-0 print:space-y-6" id="print-report-container">
          {loading && (
            <div className="py-16 text-center text-text-tertiary no-print">
              <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-accent mb-3"></div>
              <p className="text-sm">Assembling portfolio evidence report...</p>
            </div>
          )}

          {error && (
            <div className="p-4 bg-risk-bg border border-risk-border rounded-xl text-risk-fg text-sm flex items-center justify-between no-print">
              <span>{error}</span>
              <button
                onClick={handleGenerateReport}
                className="btn btn-secondary btn-sm"
              >
                Retry
              </button>
            </div>
          )}

          {report && (
            <div className="space-y-6 print:space-y-6 print:text-black">
              {/* SECTION 1: Report Metadata & Header */}
              <div className="panel bg-surface-inset p-5 print:bg-slate-50 print:border-slate-300">
                <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-border pb-4 print:border-slate-300">
                  <div>
                    <div className="flex items-center space-x-2">
                      <span className="text-xl font-bold tracking-tight text-text-primary print:text-black">YUKIRA</span>
                      <span className="status-badge state-candidate font-mono print:border-slate-400 print:text-slate-800">
                        EVIDENCE REPORT V1
                      </span>
                    </div>
                    <p className="text-xs text-text-secondary mt-1 print:text-slate-600">
                      Quantitative Investment Intelligence Snapshot
                    </p>
                  </div>
                  <div className="text-right text-xs text-text-secondary font-mono space-y-0.5 print:text-slate-700">
                    <div><span className="text-text-tertiary">Report ID:</span> {report.reportMetadata.reportId}</div>
                    <div><span className="text-text-tertiary">Generated:</span> {report.reportMetadata.generationTimestamp}</div>
                  </div>
                </div>

                <div className="grid grid-cols-2 md:grid-cols-4 gap-4 pt-4 text-xs font-mono">
                  <div>
                    <span className="text-text-tertiary block print:text-slate-600">Valuation As-Of</span>
                    <span className="font-semibold text-text-primary print:text-slate-900">{report.reportMetadata.valuationAsOfDate}</span>
                  </div>
                  <div>
                    <span className="text-text-tertiary block print:text-slate-600">Knowledge Cutoff</span>
                    <span className="font-semibold text-text-primary print:text-slate-900">{report.reportMetadata.knowledgeCutoff}</span>
                  </div>
                  <div>
                    <span className="text-text-tertiary block print:text-slate-600">Score Methodology</span>
                    <span className="font-semibold text-text-primary print:text-slate-900">{report.reportMetadata.scoreVersion} ({report.reportMetadata.scoreMethodologyStatus})</span>
                  </div>
                  <div>
                    <span className="text-text-tertiary block print:text-slate-600">Reference Population</span>
                    <span className="font-semibold text-text-primary print:text-slate-900">{report.reportMetadata.referencePopulation}</span>
                  </div>
                </div>

                <div className="mt-3 pt-3 border-t border-border text-[11px] text-text-secondary print:border-slate-300 print:text-slate-700">
                  <span className="font-medium text-candidate-fg print:text-amber-700">Environment Note:</span> {report.reportMetadata.databaseEnvironment}
                </div>
              </div>

              {/* SECTION 2: Investor Portfolio Summary */}
              <div className="panel p-5 space-y-4 print:bg-white print:border-slate-300">
                <h3 className="text-xs font-semibold text-text-primary uppercase tracking-wider print:text-slate-900">
                  1. Investor Portfolio Summary
                </h3>

                <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                  <div className="p-3 bg-surface-inset border border-border rounded-lg print:bg-slate-50 print:border-slate-200">
                    <span className="text-xs text-text-secondary print:text-slate-600">Covered Portfolio Value</span>
                    <p className="text-lg font-bold text-text-primary mt-1 print:text-slate-900">
                      {report.portfolioSummary.totalAvailableValue !== null
                        ? formatCurrency(report.portfolioSummary.totalAvailableValue)
                        : 'Unavailable'}
                    </p>
                    <span className="text-[10px] text-text-tertiary font-mono print:text-slate-600">
                      {report.portfolioSummary.valuedHoldingsCount} of {report.portfolioSummary.totalHoldings} holdings valued
                    </span>
                  </div>

                  <div className="p-3 bg-surface-inset border border-border rounded-lg print:bg-slate-50 print:border-slate-200">
                    <span className="text-xs text-text-secondary print:text-slate-600">Total Invested Amount</span>
                    <p className="text-lg font-bold text-text-primary mt-1 print:text-slate-900">
                      {report.portfolioSummary.totalInvestedAmount !== null
                        ? formatCurrency(report.portfolioSummary.totalInvestedAmount)
                        : 'N/A (No cost basis)'}
                    </p>
                    <span className="text-[10px] text-text-tertiary font-mono print:text-slate-600">
                      Cost basis provided
                    </span>
                  </div>

                  <div className="p-3 bg-surface-inset border border-border rounded-lg print:bg-slate-50 print:border-slate-200">
                    <span className="text-xs text-text-secondary print:text-slate-600">Total Gain / Loss</span>
                    <p className={`text-lg font-bold mt-1 ${
                      (report.portfolioSummary.totalAbsoluteGainLoss ?? 0) >= 0 ? 'text-approved-fg print:text-emerald-700' : 'text-risk-fg print:text-rose-700'
                    }`}>
                      {report.portfolioSummary.totalAbsoluteGainLoss !== null
                        ? `${(report.portfolioSummary.totalAbsoluteGainLoss ?? 0) >= 0 ? '+' : ''}${formatCurrency(report.portfolioSummary.totalAbsoluteGainLoss)}`
                        : 'Unavailable'}
                    </p>
                    <span className="text-[10px] text-text-tertiary font-mono print:text-slate-600">
                      {report.portfolioSummary.totalAbsoluteGainLossPercentage !== null
                        ? `${report.portfolioSummary.totalAbsoluteGainLossPercentage >= 0 ? '+' : ''}${report.portfolioSummary.totalAbsoluteGainLossPercentage.toFixed(2)}%`
                        : 'P&L coverage incomplete'}
                    </span>
                  </div>

                  <div className="p-3 bg-surface-inset border border-border rounded-lg print:bg-slate-50 print:border-slate-200">
                    <span className="text-xs text-text-secondary print:text-slate-600">Portfolio Score V1</span>
                    <p className="text-lg font-bold text-accent mt-1 print:text-indigo-800">
                      {report.portfolioSummary.portfolioAnalyticalScore?.portfolioScore !== null
                        ? report.portfolioSummary.portfolioAnalyticalScore?.portfolioScore?.toFixed(2)
                        : 'Unscored'}
                    </p>
                    <span className="text-[10px] text-text-tertiary font-mono print:text-slate-600">
                      {report.portfolioSummary.portfolioAnalyticalScore?.scoreState ?? 'UNAVAILABLE'} state
                    </span>
                  </div>
                </div>
              </div>

              {/* SECTION 3: Holdings & Investor Economics */}
              <div className="panel p-5 space-y-4 print:bg-white print:border-slate-300">
                <div className="flex items-center justify-between">
                  <h3 className="text-xs font-semibold text-text-primary uppercase tracking-wider print:text-slate-900">
                    2. Portfolio Holdings & Investor Economics
                  </h3>
                  <span className="text-xs text-text-secondary font-mono print:text-slate-600">
                    Exact scheme_option_id identity preserved
                  </span>
                </div>

                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs border-collapse font-sans">
                    <thead>
                      <tr className="border-b border-border text-text-secondary bg-surface-inset print:bg-slate-100 print:text-slate-800 print:border-slate-300 font-mono">
                        <th className="p-2.5 font-medium">Scheme & Class</th>
                        <th className="p-2.5 font-medium">Identifiers</th>
                        <th className="p-2.5 font-medium text-right">Units & Cost</th>
                        <th className="p-2.5 font-medium text-right">NAV & Date</th>
                        <th className="p-2.5 font-medium text-right">Valuation & P&L</th>
                        <th className="p-2.5 font-medium text-center">Score V1</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border print:divide-slate-200 font-mono">
                      {report.holdingDetails.map((h) => (
                        <tr key={h.schemeOptionId} className="hover:bg-surface-raised transition-colors">
                          <td className="p-2.5 align-top font-sans">
                            <div className="font-semibold text-text-primary print:text-slate-900">{h.fundName}</div>
                            <div className="text-[11px] text-text-secondary print:text-slate-600 font-mono mt-0.5">
                              {h.amcName} • {h.planType} • {h.optionType}
                            </div>
                          </td>
                          <td className="p-2.5 align-top font-mono text-[11px] text-text-secondary print:text-slate-800 space-y-0.5">
                            <div><span className="text-text-tertiary">OptID:</span> {h.schemeOptionId}</div>
                            <div><span className="text-text-tertiary">AMFI:</span> {h.amfiCode}</div>
                            <div><span className="text-text-tertiary">ISIN:</span> {h.isin}</div>
                          </td>
                          <td className="p-2.5 align-top text-right font-mono text-text-primary print:text-slate-900">
                            <div>{h.units.toFixed(4)} units</div>
                            <div className="text-[11px] text-text-secondary print:text-slate-600">
                              Cost: {h.costBasisAmount !== null ? formatCurrency(h.costBasisAmount) : 'N/A'}
                            </div>
                            <div className="text-[11px] text-text-tertiary print:text-slate-600">
                              Inv: {h.investedAmount !== null ? formatCurrency(h.investedAmount) : 'N/A'}
                            </div>
                          </td>
                          <td className="p-2.5 align-top text-right font-mono text-text-primary print:text-slate-900">
                            <div>{h.navValue !== null ? `₹${h.navValue.toFixed(4)}` : 'N/A'}</div>
                            <div className="text-[11px] text-text-secondary print:text-slate-600">{h.navAsOfDate ?? 'Unknown'}</div>
                          </td>
                          <td className="p-2.5 align-top text-right font-mono">
                            <div className="font-semibold text-text-primary print:text-slate-900">
                              {h.availableValue !== null ? formatCurrency(h.availableValue) : 'Valuation Unavailable'}
                            </div>
                            <div className={`text-[11px] ${
                              (h.absoluteGainLoss ?? 0) >= 0 ? 'text-approved-fg print:text-emerald-700' : 'text-risk-fg print:text-rose-700'
                            }`}>
                              {h.absoluteGainLoss !== null
                                ? `${(h.absoluteGainLoss ?? 0) >= 0 ? '+' : ''}${formatCurrency(h.absoluteGainLoss)} (${(h.absoluteGainLossPercentage ?? 0) >= 0 ? '+' : ''}${h.absoluteGainLossPercentage?.toFixed(2)}%)`
                                : 'P&L N/A'}
                            </div>
                            <div className="text-[10px] text-text-tertiary print:text-slate-600">
                              Weight: {h.portfolioWeightPercentage !== null ? `${h.portfolioWeightPercentage.toFixed(2)}%` : 'N/A'}
                            </div>
                          </td>
                          <td className="p-2.5 align-top text-center font-mono">
                            {h.scoreDetail.available && h.scoreDetail.scoreValue !== null ? (
                              <div>
                                <span className="status-badge state-approved font-bold text-xs print:bg-slate-100 print:text-slate-900">
                                  {h.scoreDetail.scoreValue.toFixed(2)}
                                </span>
                                <div className="text-[10px] text-text-secondary mt-0.5 print:text-slate-600">{h.scoreDetail.status}</div>
                              </div>
                            ) : (
                              <span className="text-text-tertiary text-[11px]">Score Unavailable</span>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* SECTION 4: Portfolio Analytical Score Breakdown */}
              {report.portfolioSummary.portfolioAnalyticalScore && (
                <div className="panel p-5 space-y-4 print:bg-white print:border-slate-300">
                  <h3 className="text-xs font-semibold text-text-primary uppercase tracking-wider print:text-slate-900">
                    3. Portfolio Analytical Score (YUKIRA_SCORE_V1)
                  </h3>

                  <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs font-mono">
                    <div className="p-3 bg-surface-inset border border-border rounded-lg print:bg-slate-50 print:border-slate-200">
                      <span className="text-text-secondary print:text-slate-600 block">Portfolio Score</span>
                      <span className="text-2xl font-extrabold text-accent print:text-indigo-800">
                        {report.portfolioSummary.portfolioAnalyticalScore.portfolioScore !== null
                          ? report.portfolioSummary.portfolioAnalyticalScore.portfolioScore.toFixed(2)
                          : 'Unavailable'}
                      </span>
                    </div>

                    <div className="p-3 bg-surface-inset border border-border rounded-lg print:bg-slate-50 print:border-slate-200">
                      <span className="text-text-secondary print:text-slate-600 block">Scored Coverage Weight</span>
                      <span className="text-2xl font-bold text-text-primary print:text-slate-900">
                        {report.portfolioSummary.portfolioAnalyticalScore.coveredPortfolioWeight !== null
                          ? `${report.portfolioSummary.portfolioAnalyticalScore.coveredPortfolioWeight.toFixed(2)}%`
                          : '0%'}
                      </span>
                    </div>

                    <div className="p-3 bg-surface-inset border border-border rounded-lg print:bg-slate-50 print:border-slate-200">
                      <span className="text-text-secondary print:text-slate-600 block">Contributing Holdings</span>
                      <span className="text-2xl font-bold text-text-primary print:text-slate-900">
                        {report.portfolioSummary.portfolioAnalyticalScore.coveredHoldingCount} of {report.portfolioSummary.portfolioAnalyticalScore.totalHoldingCount}
                      </span>
                    </div>
                  </div>
                </div>
              )}

              {/* SECTION 5: Scored Holdings Dimension Drill-down */}
              {report.holdingDetails.some(h => h.scoreDetail.available && h.scoreDetail.dimensions.length > 0) && (
                <div className="panel p-5 space-y-4 print:bg-white print:border-slate-300">
                  <h3 className="text-xs font-semibold text-text-primary uppercase tracking-wider print:text-slate-900">
                    4. Fund-Level Score Dimension Breakdown
                  </h3>

                  <div className="space-y-4">
                    {report.holdingDetails.filter(h => h.scoreDetail.available).map(h => (
                      <div key={h.schemeOptionId} className="p-4 bg-surface-inset border border-border rounded-lg print:bg-slate-50 print:border-slate-200 space-y-3">
                        <div className="flex items-center justify-between text-xs">
                          <span className="font-semibold text-text-primary print:text-slate-900">{h.fundName} (OptID: {h.schemeOptionId})</span>
                          <span className="font-mono font-bold text-accent print:text-indigo-800">
                            Fund Score: {h.scoreDetail.scoreValue?.toFixed(2)} ({h.scoreDetail.confidence} Confidence)
                          </span>
                        </div>

                        <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-xs font-mono">
                          {h.scoreDetail.dimensions.map(dim => (
                            <div key={dim.dimensionCode} className="p-2 bg-surface border border-border rounded print:bg-white print:border-slate-300">
                              <div className="text-[11px] text-text-secondary print:text-slate-700 font-sans truncate">{dim.dimensionName}</div>
                              <div className="text-sm font-bold text-text-primary print:text-slate-900 mt-0.5">
                                {dim.scoreValue !== null ? dim.scoreValue.toFixed(2) : 'N/A'}
                              </div>
                              <div className="text-[10px] text-text-tertiary print:text-slate-600">
                                Weight: {dim.weightPercentage !== null ? `${dim.weightPercentage.toFixed(1)}%` : 'N/A'}
                              </div>
                            </div>
                          ))}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* SECTION 6: Fund-Level Risk Evidence */}
              <div className="panel p-5 space-y-4 print:bg-white print:border-slate-300">
                <div className="flex items-center justify-between">
                  <h3 className="text-xs font-semibold text-text-primary uppercase tracking-wider print:text-slate-900">
                    5. Fund-Level Risk & Quality Evidence
                  </h3>
                  <span className="text-[11px] text-candidate-fg font-mono print:text-amber-800">
                    Fund-level evidence only — No portfolio-level risk model
                  </span>
                </div>

                <div className="space-y-4">
                  {report.holdingDetails.map(h => (
                    <div key={h.schemeOptionId} className="p-4 bg-surface-inset border border-border rounded-lg print:bg-slate-50 print:border-slate-200 space-y-3">
                      <div className="text-xs font-semibold text-text-primary print:text-slate-900">
                        {h.fundName} <span className="font-mono text-text-tertiary text-[11px]">(scheme_option_id: {h.schemeOptionId})</span>
                      </div>

                      {h.riskEvidence && h.riskEvidence.metrics.length > 0 ? (
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-2.5 text-xs font-mono">
                          {h.riskEvidence.metrics.map(m => (
                            <div key={m.metricCode} className="p-2 bg-surface border border-border rounded print:bg-white print:border-slate-300">
                              <div className="text-[10px] text-text-secondary print:text-slate-700 truncate">{m.metricCode}: {m.metricName}</div>
                              <div className="text-xs font-bold text-text-primary print:text-slate-900 mt-0.5">
                                {m.formattedValue ?? 'Unavailable'}
                              </div>
                              <div className="text-[9px] text-text-tertiary print:text-slate-600">
                                As-of: {m.asOfDate ?? 'Unknown'}
                              </div>
                            </div>
                          ))}
                        </div>
                      ) : (
                        <p className="text-xs text-text-tertiary italic">No fund-level risk evidence metrics available in dataset.</p>
                      )}
                    </div>
                  ))}
                </div>
              </div>

              {/* SECTION 7: Goal Alignment Evidence (Optional / Conditional) */}
              {report.goalAlignment ? (
                <div className="panel p-5 space-y-4 print:bg-white print:border-slate-300">
                  <h3 className="text-xs font-semibold text-text-primary uppercase tracking-wider print:text-slate-900">
                    6. Goal Alignment Evidence V1
                  </h3>

                  <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-xs font-mono bg-surface-inset p-3 rounded-lg border border-border print:bg-slate-50 print:border-slate-200">
                    <div>
                      <span className="text-text-tertiary block">Goal Category</span>
                      <span className="font-semibold text-text-primary print:text-slate-900">{report.goalAlignment.goalRequirements.goalCategory ?? 'Not Specified'}</span>
                    </div>
                    <div>
                      <span className="text-text-tertiary block">Horizon</span>
                      <span className="font-semibold text-text-primary print:text-slate-900">{report.goalAlignment.goalRequirements.horizonYears ? `${report.goalAlignment.goalRequirements.horizonYears} years` : 'Not Specified'}</span>
                    </div>
                    <div>
                      <span className="text-text-tertiary block">Risk Tolerance</span>
                      <span className="font-semibold text-text-primary print:text-slate-900">{report.goalAlignment.goalRequirements.riskTolerance ?? 'Not Specified'}</span>
                    </div>
                    <div>
                      <span className="text-text-tertiary block">Coverage State</span>
                      <span className="font-semibold text-text-primary print:text-slate-900">{report.goalAlignment.coverageState}</span>
                    </div>
                  </div>
                </div>
              ) : (
                <div className="p-4 bg-surface-inset border border-border rounded-xl text-xs text-text-secondary print:bg-slate-50 print:border-slate-200 print:text-slate-700">
                  <span className="font-semibold text-text-primary print:text-slate-900">Goal Alignment:</span> Goal alignment not evaluated.
                </div>
              )}

              {/* SECTION 8: Target Allocation & Drift Evidence */}
              <div className="panel p-5 space-y-4 print:bg-white print:border-slate-300">
                <div className="flex items-center justify-between">
                  <h3 className="text-xs font-semibold text-text-primary uppercase tracking-wider print:text-slate-900">
                    7. Target Allocation & Drift Evidence
                  </h3>
                  <span className="text-[11px] text-text-secondary font-mono print:text-slate-700">
                    Investor-defined target only
                  </span>
                </div>

                {report.targetDriftAnalysis ? (
                  <>
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-xs font-mono">
                      <div className="p-3 bg-surface-inset border border-border rounded-lg print:bg-slate-50 print:border-slate-200">
                        <span className="text-text-secondary block print:text-slate-600">Drift Status</span>
                        <span className="text-lg font-bold text-text-primary print:text-slate-900">
                          {report.targetDriftAnalysis.status}
                        </span>
                      </div>
                      <div className="p-3 bg-surface-inset border border-border rounded-lg print:bg-slate-50 print:border-slate-200">
                        <span className="text-text-secondary block print:text-slate-600">Target Weight</span>
                        <span className="text-lg font-bold text-text-primary print:text-slate-900">
                          {report.targetDriftAnalysis.status === 'NO_TARGET'
                            ? 'Not available'
                            : `${report.targetDriftAnalysis.totalTargetWeightPercentage.toFixed(2)}%`}
                        </span>
                      </div>
                      <div className="p-3 bg-surface-inset border border-border rounded-lg print:bg-slate-50 print:border-slate-200">
                        <span className="text-text-secondary block print:text-slate-600">Current Weight</span>
                        <span className="text-lg font-bold text-text-primary print:text-slate-900">
                          {report.targetDriftAnalysis.status === 'NO_TARGET'
                            ? 'Not available'
                            : `${report.targetDriftAnalysis.totalCurrentWeightPercentage.toFixed(2)}%`}
                        </span>
                      </div>
                      <div className="p-3 bg-surface-inset border border-border rounded-lg print:bg-slate-50 print:border-slate-200">
                        <span className="text-text-secondary block print:text-slate-600">Absolute Drift</span>
                        <span className="text-lg font-bold text-text-primary print:text-slate-900">
                          {report.targetDriftAnalysis.status === 'NO_TARGET'
                            ? 'Not available'
                            : `${report.targetDriftAnalysis.totalAbsoluteDriftPercentagePoints.toFixed(2)} pp`}
                        </span>
                      </div>
                    </div>

                    {report.targetDriftAnalysis.itemDrifts.length > 0 ? (
                      <div className="overflow-x-auto rounded border border-border print:border-slate-300">
                        <table className="w-full text-left text-xs font-mono">
                          <thead className="bg-surface-inset text-text-secondary print:bg-slate-100 print:text-slate-800">
                            <tr>
                              <th className="p-2 font-medium">Target</th>
                              <th className="p-2 font-medium text-right">Target Weight</th>
                              <th className="p-2 font-medium text-right">Current Weight</th>
                              <th className="p-2 font-medium text-right">Drift</th>
                              <th className="p-2 font-medium">State</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-border print:divide-slate-200">
                            {report.targetDriftAnalysis.itemDrifts.map((item, idx) => (
                              <tr key={`${item.displayName}-${idx}`}>
                                <td className="p-2 text-text-primary print:text-slate-900">{item.displayName}</td>
                                <td className="p-2 text-right text-text-primary print:text-slate-900">{item.targetWeightPercentage.toFixed(2)}%</td>
                                <td className="p-2 text-right text-text-primary print:text-slate-900">{item.currentWeightPercentage.toFixed(2)}%</td>
                                <td className="p-2 text-right text-text-primary print:text-slate-900">{item.driftPercentagePoints.toFixed(2)} pp</td>
                                <td className="p-2 text-text-secondary print:text-slate-700">{item.mappingState} / {item.driftDirection}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    ) : (
                      <p className="text-xs text-text-secondary print:text-slate-700">
                        {report.targetDriftAnalysis.status === 'NO_TARGET'
                          ? 'Not available: no investor-defined target allocation is persisted for this portfolio.'
                          : 'No target drift rows available for this report.'}
                      </p>
                    )}
                  </>
                ) : (
                  <p className="text-xs text-text-secondary print:text-slate-700">Not available: target allocation drift evidence was not included in this report response.</p>
                )}
              </div>
              {/* SECTION 9: Data Quality Limitations & Provenance */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="panel p-5 space-y-3 print:bg-white print:border-slate-300">
                  <h3 className="text-xs font-semibold text-text-primary uppercase tracking-wider print:text-slate-900">
                    8. Data Quality & Limitations
                  </h3>
                  <ul className="space-y-1.5 text-xs text-text-secondary print:text-slate-800 list-disc list-inside">
                    {report.dataQualityLimitations.map((lim, idx) => (
                      <li key={idx} className="leading-relaxed">{lim}</li>
                    ))}
                  </ul>
                </div>

                <div className="panel p-5 space-y-3 print:bg-white print:border-slate-300">
                  <h3 className="text-xs font-semibold text-text-primary uppercase tracking-wider print:text-slate-900">
                    9. Questions Worth Investigating
                  </h3>
                  <ul className="space-y-1.5 text-xs text-text-secondary print:text-slate-800 list-disc list-inside">
                    {report.investigationQuestions.map((q, idx) => (
                      <li key={idx} className="leading-relaxed">{q}</li>
                    ))}
                  </ul>
                </div>
              </div>

              {/* SECTION 10: Epistemic Disclaimers & Regulatory Boundary */}
              <div className="panel bg-surface-inset p-5 space-y-3 print:bg-slate-50 print:border-slate-300">
                <h3 className="text-xs font-bold text-candidate-fg uppercase tracking-wider print:text-amber-800">
                  Important Regulatory & Epistemic Disclaimers
                </h3>
                <div className="space-y-2 text-[11px] text-text-tertiary print:text-slate-700 leading-relaxed font-sans">
                  {report.disclaimers.map((disc, idx) => (
                    <p key={idx} className="border-l-2 border-candidate-fg/40 pl-2.5 print:border-amber-600">{disc}</p>
                  ))}
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
