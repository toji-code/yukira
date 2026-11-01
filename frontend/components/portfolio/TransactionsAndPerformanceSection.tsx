'use client';

import React, { useState, useEffect, useMemo } from 'react';
import {
  InvestorTransactionDto,
  PortfolioHoldingsReconciliationResponseDto,
  PortfolioInvestorPerformanceResponseDto,
  CasImportTransactionReviewDto,
  fetchInvestorTransactions,
  fetchHoldingsReconciliation,
  fetchInvestorPerformance,
  fetchCasImportTransactions,
  TransactionType,
  ReconciliationStatus,
  XirrStatus
} from '@/lib/api/transactions';
import { PortfolioHoldingDto } from '@/lib/api/portfolio';
import { formatCurrency, formatNumber, formatPercent } from '@/lib/utils/formatters';
import { ManualTransactionModal } from './ManualTransactionModal';
import { ReverseTransactionModal } from './ReverseTransactionModal';
import { SchemeOption } from '@/types/domain';
import { PerformanceAttributionSection } from './PerformanceAttributionSection';


interface TransactionsAndPerformanceSectionProps {
  holdings?: PortfolioHoldingDto[];
  availableOptions?: SchemeOption[];
  onDataChanged?: () => void;
}

export function TransactionsAndPerformanceSection({
  holdings = [],
  availableOptions = [],
  onDataChanged
}: TransactionsAndPerformanceSectionProps) {
  // Main sub-tabs
  const [activeTab, setActiveTab] = useState<'history' | 'reconciliation' | 'performance' | 'attribution' | 'casReview'>('performance');

  // Transaction History State
  const [transactions, setTransactions] = useState<InvestorTransactionDto[]>([]);
  const [loadingTx, setLoadingTx] = useState<boolean>(true);
  const [txError, setTxError] = useState<string | null>(null);

  // Filters for History
  const [filterSchemeOptionId, setFilterSchemeOptionId] = useState<string>('');
  const [filterType, setFilterType] = useState<string>('');
  const [filterStartDate, setFilterStartDate] = useState<string>('');
  const [filterEndDate, setFilterEndDate] = useState<string>('');

  // Modals state
  const [isManualModalOpen, setIsManualModalOpen] = useState<boolean>(false);
  const [selectedTxToReverse, setSelectedTxToReverse] = useState<InvestorTransactionDto | null>(null);
  const [isReverseModalOpen, setIsReverseModalOpen] = useState<boolean>(false);

  // Holdings Reconciliation State
  const [reconciliation, setReconciliation] = useState<PortfolioHoldingsReconciliationResponseDto | null>(null);
  const [loadingRecon, setLoadingRecon] = useState<boolean>(false);
  const [reconError, setReconError] = useState<string | null>(null);

  // Investor Performance State
  const [performance, setPerformance] = useState<PortfolioInvestorPerformanceResponseDto | null>(null);
  const [loadingPerf, setLoadingPerf] = useState<boolean>(false);
  const [perfError, setPerfError] = useState<string | null>(null);
  const [showCashFlowSchedule, setShowCashFlowSchedule] = useState<boolean>(false);
  const [selectedSchemeSchedule, setSelectedSchemeSchedule] = useState<number | null>(null);

  // CAS Import Review State
  const [importBatchIdInput, setImportBatchIdInput] = useState<string>('1');
  const [importTransactions, setImportTransactions] = useState<CasImportTransactionReviewDto[]>([]);
  const [loadingImportTx, setLoadingImportTx] = useState<boolean>(false);
  const [importTxError, setImportTxError] = useState<string | null>(null);

  // 1. Fetch Transactions
  const loadTransactions = async () => {
    try {
      setLoadingTx(true);
      setTxError(null);
      const data = await fetchInvestorTransactions({
        schemeOptionId: filterSchemeOptionId ? parseInt(filterSchemeOptionId) : undefined,
        transactionType: filterType || undefined,
        startDate: filterStartDate || undefined,
        endDate: filterEndDate || undefined,
      });
      setTransactions(data);
    } catch (err: unknown) {
      setTxError((err as Error).message || 'Failed to load transaction history.');
    } finally {
      setLoadingTx(false);
    }
  };

  // 2. Fetch Reconciliation
  const loadReconciliation = async () => {
    try {
      setLoadingRecon(true);
      setReconError(null);
      const data = await fetchHoldingsReconciliation();
      setReconciliation(data);
    } catch (err: unknown) {
      setReconError((err as Error).message || 'Failed to reconcile holdings.');
    } finally {
      setLoadingRecon(false);
    }
  };

  // 3. Fetch Performance
  const loadPerformance = async () => {
    try {
      setLoadingPerf(true);
      setPerfError(null);
      const data = await fetchInvestorPerformance();
      setPerformance(data);
    } catch (err: unknown) {
      setPerfError((err as Error).message || 'Failed to calculate investor performance.');
    } finally {
      setLoadingPerf(false);
    }
  };

  // 4. Fetch CAS Import Transactions
  const loadImportTransactions = async (batchId: number) => {
    try {
      setLoadingImportTx(true);
      setImportTxError(null);
      const data = await fetchCasImportTransactions(batchId);
      setImportTransactions(data);
    } catch (err: unknown) {
      setImportTxError((err as Error).message || 'Failed to load import transactions.');
    } finally {
      setLoadingImportTx(false);
    }
  };

  // Initial load
  useEffect(() => {
    loadTransactions();
    loadPerformance();
    loadReconciliation();
  }, []);

  // Reload history when filters change
  useEffect(() => {
    loadTransactions();
  }, [filterSchemeOptionId, filterType, filterStartDate, filterEndDate]);

  const handleTransactionCreated = () => {
    loadTransactions();
    loadPerformance();
    loadReconciliation();
    if (onDataChanged) onDataChanged();
  };

  const handleTransactionReversed = () => {
    loadTransactions();
    loadPerformance();
    loadReconciliation();
    if (onDataChanged) onDataChanged();
  };

  // Status Badge Helpers
  const renderReconBadge = (status: ReconciliationStatus) => {
    switch (status) {
      case 'RECONCILED':
        return <span className="px-2 py-0.5 rounded text-[11px] font-mono font-medium bg-emerald-500/20 text-emerald-300 border border-emerald-500/40">RECONCILED</span>;
      case 'PARTIALLY_RECONCILED':
        return <span className="px-2 py-0.5 rounded text-[11px] font-mono font-medium bg-sky-500/20 text-sky-300 border border-sky-500/40">PARTIAL</span>;
      case 'INSUFFICIENT_HISTORY':
        return <span className="px-2 py-0.5 rounded text-[11px] font-mono font-medium bg-amber-500/20 text-amber-300 border border-amber-500/40">INSUFFICIENT HISTORY</span>;
      case 'SOURCE_CONFLICT':
        return <span className="px-2 py-0.5 rounded text-[11px] font-mono font-medium bg-purple-500/20 text-purple-300 border border-purple-500/40">CONFLICT</span>;
      case 'UNRECONCILED':
      default:
        return <span className="px-2 py-0.5 rounded text-[11px] font-mono font-medium bg-rose-500/20 text-rose-300 border border-rose-500/40">UNRECONCILED</span>;
    }
  };

  const renderXirrBadge = (status: XirrStatus) => {
    switch (status) {
      case 'AVAILABLE':
        return <span className="px-2 py-0.5 rounded text-[11px] font-mono font-medium bg-emerald-500/20 text-emerald-300 border border-emerald-500/40">AVAILABLE</span>;
      case 'INSUFFICIENT_CASH_FLOWS':
        return <span className="px-2 py-0.5 rounded text-[11px] font-mono font-medium bg-amber-500/20 text-amber-300 border border-amber-500/40">INSUFFICIENT FLOWS</span>;
      case 'INSUFFICIENT_HISTORY':
        return <span className="px-2 py-0.5 rounded text-[11px] font-mono font-medium bg-zinc-500/20 text-zinc-300 border border-zinc-500/40">&lt; 30 DAYS</span>;
      case 'MISSING_ENDING_VALUATION':
      case 'INCOMPLETE_PORTFOLIO_VALUATION':
        return <span className="px-2 py-0.5 rounded text-[11px] font-mono font-medium bg-rose-500/20 text-rose-300 border border-rose-500/40">MISSING VALUATION</span>;
      case 'NO_VALID_ROOT':
      case 'NON_CONVERGENT':
      default:
        return <span className="px-2 py-0.5 rounded text-[11px] font-mono font-medium bg-rose-500/20 text-rose-300 border border-rose-500/40">NON-CONVERGENT</span>;
    }
  };

  const renderTxTypeBadge = (type: TransactionType) => {
    let color = 'bg-zinc-800 text-zinc-300 border-zinc-700';
    if (type.includes('PURCHASE') || type === 'SWITCH_IN') {
      color = 'bg-emerald-950/60 text-emerald-400 border-emerald-800/80';
    } else if (type.includes('REDEMPTION') || type === 'SWITCH_OUT') {
      color = 'bg-rose-950/60 text-rose-400 border-rose-800/80';
    } else if (type.includes('DIVIDEND')) {
      color = 'bg-sky-950/60 text-sky-400 border-sky-800/80';
    } else if (type === 'REVERSAL') {
      color = 'bg-amber-950/60 text-amber-400 border-amber-800/80';
    }
    return (
      <span className={`px-2 py-0.5 rounded text-[10px] font-mono font-semibold border ${color}`}>
        {type}
      </span>
    );
  };

  const renderSourceBadge = (source: string) => {
    switch (source) {
      case 'CAS_IMPORT':
        return <span className="px-1.5 py-0.5 rounded text-[10px] font-mono bg-purple-950/60 text-purple-300 border border-purple-800/60">CAS</span>;
      case 'MANUAL_ENTRY':
        return <span className="px-1.5 py-0.5 rounded text-[10px] font-mono bg-blue-950/60 text-blue-300 border border-blue-800/60">MANUAL</span>;
      case 'REVERSAL_ENTRY':
        return <span className="px-1.5 py-0.5 rounded text-[10px] font-mono bg-amber-950/60 text-amber-300 border border-amber-800/60">REVERSAL</span>;
      default:
        return <span className="px-1.5 py-0.5 rounded text-[10px] font-mono bg-zinc-800 text-zinc-400">{source}</span>;
    }
  };

  return (
    <div className="mt-8 bg-zinc-900 border border-zinc-800 rounded-xl overflow-hidden shadow-xl">
      {/* Section Header */}
      <div className="p-6 border-b border-zinc-800 bg-zinc-950/40">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2">
              <span className="px-2 py-0.5 bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 rounded text-xs font-mono font-medium">
                TASK 10 OPERATIONAL
              </span>
              <h2 className="text-xl font-bold text-zinc-100 tracking-tight">
                Investor Performance & Transaction Ledger
              </h2>
            </div>
            <p className="text-xs text-zinc-400 mt-1 max-w-3xl">
              Measuring the actual performance of invested capital through point-in-time cash-flow modeling,
              FIFO cost-basis matching, deterministic XIRR calculation, and strict holdings reconciliation.
            </p>
          </div>

          {/* Quick Actions */}
          <div className="flex items-center gap-2">
            <button
              onClick={() => setIsManualModalOpen(true)}
              className="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg text-xs font-medium transition shadow-sm flex items-center gap-1.5"
            >
              <span>+ Record Transaction</span>
            </button>
            <button
              onClick={() => {
                loadTransactions();
                loadPerformance();
                loadReconciliation();
              }}
              className="px-3 py-1.5 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 rounded-lg text-xs font-medium transition"
              title="Refresh ledger and performance calculations"
            >
              ↻ Refresh
            </button>
          </div>
        </div>

        {/* Sub-Navigation Tabs */}
        <div className="flex items-center gap-1 mt-6 border-b border-zinc-800">
          <button
            onClick={() => setActiveTab('performance')}
            className={`px-4 py-2.5 text-xs font-medium border-b-2 transition -mb-px flex items-center gap-2 ${
              activeTab === 'performance'
                ? 'border-emerald-500 text-emerald-400 bg-emerald-500/5'
                : 'border-transparent text-zinc-400 hover:text-zinc-200 hover:border-zinc-700'
            }`}
          >
            <span>Investor Performance & XIRR</span>
            {performance && performance.portfolioXirrStatus === 'AVAILABLE' && (
              <span className="w-2 h-2 rounded-full bg-emerald-400" />
            )}
          </button>

          <button
            onClick={() => setActiveTab('attribution')}
            className={`px-4 py-2.5 text-xs font-medium border-b-2 transition -mb-px flex items-center gap-2 ${
              activeTab === 'attribution'
                ? 'border-emerald-500 text-emerald-400 bg-emerald-500/5'
                : 'border-transparent text-zinc-400 hover:text-zinc-200 hover:border-zinc-700'
            }`}
          >
            <span>Attribution & Contribution</span>
            <span className="px-1.5 py-0.2 bg-sky-950 text-sky-400 border border-sky-800/60 rounded text-[10px] font-mono font-bold">
              TASK 11
            </span>
          </button>

          <button
            onClick={() => setActiveTab('history')}
            className={`px-4 py-2.5 text-xs font-medium border-b-2 transition -mb-px flex items-center gap-2 ${
              activeTab === 'history'
                ? 'border-emerald-500 text-emerald-400 bg-emerald-500/5'
                : 'border-transparent text-zinc-400 hover:text-zinc-200 hover:border-zinc-700'
            }`}
          >
            <span>Transaction Ledger</span>
            <span className="px-1.5 py-0.2 bg-zinc-800 text-zinc-300 rounded text-[10px] font-mono">
              {transactions.length}
            </span>
          </button>

          <button
            onClick={() => setActiveTab('reconciliation')}
            className={`px-4 py-2.5 text-xs font-medium border-b-2 transition -mb-px flex items-center gap-2 ${
              activeTab === 'reconciliation'
                ? 'border-emerald-500 text-emerald-400 bg-emerald-500/5'
                : 'border-transparent text-zinc-400 hover:text-zinc-200 hover:border-zinc-700'
            }`}
          >
            <span>Holdings Reconciliation</span>
            {reconciliation && (
              <span className="px-1.5 py-0.2 bg-zinc-800 text-zinc-300 rounded text-[10px] font-mono">
                {reconciliation.reconciledCount}/{reconciliation.totalHoldingsEvaluated}
              </span>
            )}
          </button>

          <button
            onClick={() => setActiveTab('casReview')}
            className={`px-4 py-2.5 text-xs font-medium border-b-2 transition -mb-px ${
              activeTab === 'casReview'
                ? 'border-emerald-500 text-emerald-400 bg-emerald-500/5'
                : 'border-transparent text-zinc-400 hover:text-zinc-200 hover:border-zinc-700'
            }`}
          >
            <span>CAS Import Review</span>
          </button>
        </div>
      </div>

      {/* Tab Contents */}
      <div className="p-6">
        {/* ============================================================== */}
        {/* SUB-TAB: ATTRIBUTION & CONTRIBUTION (TASK 11) */}
        {/* ============================================================== */}
        {activeTab === 'attribution' && (
          <PerformanceAttributionSection
            availableOptions={availableOptions}
            onRefreshRequested={() => {
              loadTransactions();
              loadPerformance();
              loadReconciliation();
              if (onDataChanged) onDataChanged();
            }}
          />
        )}

        {/* ============================================================== */}
        {/* SUB-TAB 1: INVESTOR PERFORMANCE & XIRR */}
        {/* ============================================================== */}
        {activeTab === 'performance' && (
          <div className="space-y-6">
            {loadingPerf ? (
              <div className="p-12 text-center text-zinc-500 text-xs">
                Computing deterministic cash-flow schedules and XIRR...
              </div>
            ) : perfError ? (
              <div className="p-4 bg-rose-950/40 border border-rose-800 rounded-lg text-rose-300 text-xs">
                {perfError}
              </div>
            ) : performance ? (
              <>
                {/* High-Level Metric Cards Grid */}
                <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                  {/* Portfolio XIRR Card */}
                  <div className="p-4 bg-zinc-950/70 border border-zinc-800 rounded-xl relative overflow-hidden">
                    <div className="flex items-center justify-between text-xs text-zinc-400 mb-1">
                      <span>Portfolio XIRR</span>
                      {renderXirrBadge(performance.portfolioXirrStatus)}
                    </div>
                    <div className="text-2xl font-bold font-mono text-emerald-400">
                      {performance.portfolioXirrPercent != null
                        ? `${performance.portfolioXirrPercent > 0 ? '+' : ''}${performance.portfolioXirrPercent.toFixed(2)}%`
                        : '—'}
                    </div>
                    <p className="text-[11px] text-zinc-500 mt-1 truncate">
                      {performance.portfolioXirrNotes}
                    </p>
                  </div>

                  {/* Invested Capital */}
                  <div className="p-4 bg-zinc-950/70 border border-zinc-800 rounded-xl">
                    <div className="text-xs text-zinc-400 mb-1">Total Invested Capital</div>
                    <div className="text-xl font-bold font-mono text-zinc-100">
                      {formatCurrency(performance.totalPortfolioInvestedCapital)}
                    </div>
                    <div className="text-[11px] text-zinc-500 mt-1 font-mono">
                      Redeemed: {formatCurrency(performance.totalPortfolioRedeemedCapital)}
                    </div>
                  </div>

                  {/* Current Valuation & Unrealized Gain */}
                  <div className="p-4 bg-zinc-950/70 border border-zinc-800 rounded-xl">
                    <div className="text-xs text-zinc-400 mb-1">Current Market Valuation</div>
                    <div className="text-xl font-bold font-mono text-zinc-100">
                      {formatCurrency(performance.totalPortfolioMarketValue)}
                    </div>
                    <div className={`text-[11px] font-mono mt-1 ${
                      performance.totalPortfolioUnrealizedGains >= 0 ? 'text-emerald-400' : 'text-rose-400'
                    }`}>
                      Unrealized: {performance.totalPortfolioUnrealizedGains >= 0 ? '+' : ''}
                      {formatCurrency(performance.totalPortfolioUnrealizedGains)}
                    </div>
                  </div>

                  {/* Realized Gains & Dividends */}
                  <div className="p-4 bg-zinc-950/70 border border-zinc-800 rounded-xl">
                    <div className="text-xs text-zinc-400 mb-1">Realized Gains (FIFO)</div>
                    <div className={`text-xl font-bold font-mono ${
                      performance.totalPortfolioRealizedGains >= 0 ? 'text-emerald-400' : 'text-rose-400'
                    }`}>
                      {performance.totalPortfolioRealizedGains >= 0 ? '+' : ''}
                      {formatCurrency(performance.totalPortfolioRealizedGains)}
                    </div>
                    <div className="text-[11px] text-zinc-500 mt-1 font-mono">
                      Dividends: {formatCurrency(performance.totalPortfolioDividendIncome)}
                    </div>
                  </div>
                </div>

                {/* Performance Comparison & Methodology Callout */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {/* XIRR vs Buy-and-Hold CAGR Context */}
                  <div className="p-4 bg-zinc-950/50 border border-zinc-800 rounded-xl space-y-2">
                    <div className="flex items-center gap-2 text-xs font-semibold text-zinc-200">
                      <span className="text-sky-400">ℹ</span>
                      <span>Money-Weighted (XIRR) vs Fund CAGR</span>
                    </div>
                    <p className="text-xs text-zinc-400 leading-relaxed">
                      An investor&apos;s personal rate of return (XIRR) reflects the exact timing and magnitude of your cash inflows and outflows.
                      It intentionally diverges from the fund&apos;s buy-and-hold CAGR whenever capital is deployed during market dips or peaks.
                    </p>
                  </div>

                  {/* TWR Limitation Disclosure */}
                  <div className="p-4 bg-zinc-950/50 border border-zinc-800 rounded-xl space-y-2">
                    <div className="flex items-center justify-between text-xs font-semibold text-zinc-200">
                      <div className="flex items-center gap-2">
                        <span className="text-amber-400">⚠</span>
                        <span>Time-Weighted Return (TWR)</span>
                      </div>
                      <span className="px-2 py-0.5 rounded text-[10px] font-mono bg-zinc-800 text-zinc-400">
                        {performance.twrStatus}
                      </span>
                    </div>
                    <p className="text-xs text-zinc-400 leading-relaxed">
                      {performance.twrLimitationDisclosure}
                    </p>
                  </div>
                </div>

                {/* Scheme-Level Performance Breakdown Table */}
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <h3 className="text-sm font-semibold text-zinc-200">
                      Scheme-Level Performance Breakdown
                    </h3>
                    <span className="text-xs text-zinc-500 font-mono">
                      FIFO Lot-Matching Engine
                    </span>
                  </div>

                  <div className="border border-zinc-800 rounded-xl overflow-hidden bg-zinc-950/60">
                    <div className="overflow-x-auto">
                      <table className="w-full text-xs text-left">
                        <thead className="bg-zinc-900/80 text-zinc-400 uppercase text-[10px] font-mono border-b border-zinc-800">
                          <tr>
                            <th className="py-2.5 px-4">Scheme</th>
                            <th className="py-2.5 px-3 text-right">Invested</th>
                            <th className="py-2.5 px-3 text-right">Redeemed</th>
                            <th className="py-2.5 px-3 text-right">Realized Gain</th>
                            <th className="py-2.5 px-3 text-right">Current Value</th>
                            <th className="py-2.5 px-3 text-right">Unrealized Gain</th>
                            <th className="py-2.5 px-3 text-right">Scheme XIRR</th>
                            <th className="py-2.5 px-3 text-right">Fund CAGR</th>
                            <th className="py-2.5 px-3 text-center">Flows</th>
                            <th className="py-2.5 px-3 text-center">Schedule</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-zinc-800/80">
                          {performance.schemePerformances.map((s) => (
                            <tr key={s.schemeOptionId} className="hover:bg-zinc-800/30 transition">
                              <td className="py-3 px-4">
                                <div className="font-medium text-zinc-100">{s.schemeName}</div>
                                <div className="text-[11px] text-zinc-500 font-mono">
                                  Units: {formatNumber(s.currentUnits, 4)} @ ₹{formatNumber(s.currentNav, 2)}
                                </div>
                              </td>
                              <td className="py-3 px-3 text-right font-mono text-zinc-300">
                                {formatCurrency(s.totalInvestedCapital)}
                              </td>
                              <td className="py-3 px-3 text-right font-mono text-zinc-300">
                                {formatCurrency(s.totalRedeemedCapital)}
                              </td>
                              <td className={`py-3 px-3 text-right font-mono ${
                                s.realizedGains >= 0 ? 'text-emerald-400' : 'text-rose-400'
                              }`}>
                                {s.realizedGains >= 0 ? '+' : ''}{formatCurrency(s.realizedGains)}
                              </td>
                              <td className="py-3 px-3 text-right font-mono text-zinc-100 font-semibold">
                                {formatCurrency(s.currentMarketValue)}
                              </td>
                              <td className={`py-3 px-3 text-right font-mono ${
                                s.unrealizedGains >= 0 ? 'text-emerald-400' : 'text-rose-400'
                              }`}>
                                {s.unrealizedGains >= 0 ? '+' : ''}{formatCurrency(s.unrealizedGains)}
                              </td>
                              <td className="py-3 px-3 text-right font-mono font-semibold">
                                {s.xirrPercent != null ? (
                                  <span className={s.xirrPercent >= 0 ? 'text-emerald-400' : 'text-rose-400'}>
                                    {s.xirrPercent > 0 ? '+' : ''}{s.xirrPercent.toFixed(2)}%
                                  </span>
                                ) : (
                                  <span className="text-zinc-500">
                                    {renderXirrBadge(s.xirrStatus)}
                                  </span>
                                )}
                              </td>
                              <td className="py-3 px-3 text-right font-mono text-zinc-400">
                                {s.fundBuyAndHoldCagrPercent != null ? (
                                  `${s.fundBuyAndHoldCagrPercent > 0 ? '+' : ''}${s.fundBuyAndHoldCagrPercent.toFixed(2)}%`
                                ) : (
                                  '—'
                                )}
                              </td>
                              <td className="py-3 px-3 text-center font-mono text-zinc-400">
                                {s.cashFlowCount}
                              </td>
                              <td className="py-3 px-3 text-center">
                                <button
                                  onClick={() => {
                                    setSelectedSchemeSchedule(
                                      selectedSchemeSchedule === s.schemeOptionId ? null : s.schemeOptionId
                                    );
                                  }}
                                  className="text-[11px] text-sky-400 hover:text-sky-300 underline font-mono"
                                >
                                  {selectedSchemeSchedule === s.schemeOptionId ? 'Hide' : 'View'}
                                </button>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                </div>

                {/* Scheme Specific Cash Flow Schedule Drawer */}
                {selectedSchemeSchedule && (
                  <div className="p-4 bg-zinc-950 border border-sky-900/50 rounded-xl space-y-3">
                    <div className="flex items-center justify-between">
                      <div className="text-xs font-semibold text-sky-300">
                        Cash Flow Schedule for {
                          performance.schemePerformances.find(s => s.schemeOptionId === selectedSchemeSchedule)?.schemeName
                        }
                      </div>
                      <button
                        onClick={() => setSelectedSchemeSchedule(null)}
                        className="text-xs text-zinc-400 hover:text-zinc-200"
                      >
                        ✕ Close
                      </button>
                    </div>
                    <div className="overflow-x-auto max-h-56">
                      <table className="w-full text-xs text-left">
                        <thead className="bg-zinc-900 text-zinc-400 text-[10px] font-mono uppercase">
                          <tr>
                            <th className="py-1.5 px-3">Date</th>
                            <th className="py-1.5 px-3">Flow Type</th>
                            <th className="py-1.5 px-3 text-right">Amount (₹)</th>
                            <th className="py-1.5 px-3">Description</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-zinc-800">
                          {performance.schemePerformances
                            .find(s => s.schemeOptionId === selectedSchemeSchedule)
                            ?.cashFlowSchedule.map((cf, idx) => (
                              <tr key={idx} className="font-mono text-[11px]">
                                <td className="py-1.5 px-3 text-zinc-300">{cf.date}</td>
                                <td className="py-1.5 px-3">
                                  <span className={`px-1.5 py-0.5 rounded ${
                                    cf.amount < 0 ? 'bg-rose-950/60 text-rose-300' : 'bg-emerald-950/60 text-emerald-300'
                                  }`}>
                                    {cf.cashFlowType}
                                  </span>
                                </td>
                                <td className={`py-1.5 px-3 text-right font-semibold ${
                                  cf.amount < 0 ? 'text-rose-400' : 'text-emerald-400'
                                }`}>
                                  {cf.amount < 0 ? '-' : '+'}{formatCurrency(Math.abs(cf.amount))}
                                </td>
                                <td className="py-1.5 px-3 text-zinc-400 font-sans">{cf.description}</td>
                              </tr>
                            ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                )}

                {/* Portfolio Cash Flow Schedule Toggle */}
                <div className="border-t border-zinc-800 pt-4">
                  <button
                    onClick={() => setShowCashFlowSchedule(!showCashFlowSchedule)}
                    className="text-xs font-semibold text-zinc-300 hover:text-zinc-100 flex items-center gap-2"
                  >
                    <span>{showCashFlowSchedule ? '▼ Hide' : '▶ Show'} Portfolio-Level External Cash Flow Schedule ({performance.portfolioCashFlowSchedule.length} events)</span>
                  </button>

                  {showCashFlowSchedule && (
                    <div className="mt-3 p-4 bg-zinc-950/80 border border-zinc-800 rounded-xl space-y-2">
                      <p className="text-xs text-zinc-400">
                        External cash flows isolate money coming in from or leaving the investor&apos;s bank account.
                        Internal fund switches and dividend reinvestments are treated as neutral transfers.
                      </p>
                      <div className="overflow-x-auto max-h-64">
                        <table className="w-full text-xs text-left">
                          <thead className="bg-zinc-900 text-zinc-400 text-[10px] font-mono uppercase">
                            <tr>
                              <th className="py-1.5 px-3">Date</th>
                              <th className="py-1.5 px-3">Flow Type</th>
                              <th className="py-1.5 px-3 text-right">Cash Amount (₹)</th>
                              <th className="py-1.5 px-3">Description</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-zinc-800">
                            {performance.portfolioCashFlowSchedule.map((cf, idx) => (
                              <tr key={idx} className="font-mono text-[11px]">
                                <td className="py-1.5 px-3 text-zinc-300">{cf.date}</td>
                                <td className="py-1.5 px-3">
                                  <span className={`px-1.5 py-0.5 rounded ${
                                    cf.amount < 0 ? 'bg-rose-950/60 text-rose-300' : 'bg-emerald-950/60 text-emerald-300'
                                  }`}>
                                    {cf.cashFlowType}
                                  </span>
                                </td>
                                <td className={`py-1.5 px-3 text-right font-semibold ${
                                  cf.amount < 0 ? 'text-rose-400' : 'text-emerald-400'
                                }`}>
                                  {cf.amount < 0 ? '-' : '+'}{formatCurrency(Math.abs(cf.amount))}
                                </td>
                                <td className="py-1.5 px-3 text-zinc-400 font-sans">{cf.description}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    </div>
                  )}
                </div>

                {/* Evidence Findings Box */}
                {performance.evidenceFindings && performance.evidenceFindings.length > 0 && (
                  <div className="p-4 bg-zinc-950/50 border border-zinc-800 rounded-xl space-y-2">
                    <div className="text-xs font-semibold text-zinc-300 flex items-center gap-2">
                      <span>Deterministic Audit Findings</span>
                    </div>
                    <ul className="space-y-1">
                      {performance.evidenceFindings.map((finding, idx) => (
                        <li key={idx} className="text-xs text-zinc-400 flex items-start gap-2">
                          <span className="text-emerald-400">•</span>
                          <span>{finding}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
              </>
            ) : (
              <div className="p-8 text-center text-zinc-500 text-xs">
                No portfolio performance data available. Add holdings or record transactions to compute returns.
              </div>
            )}
          </div>
        )}

        {/* ============================================================== */}
        {/* SUB-TAB 2: TRANSACTION HISTORY */}
        {/* ============================================================== */}
        {activeTab === 'history' && (
          <div className="space-y-4">
            {/* Filter Bar */}
            <div className="p-3 bg-zinc-950/70 border border-zinc-800 rounded-lg flex flex-wrap items-center gap-3 text-xs">
              <div>
                <label className="block text-[10px] text-zinc-400 uppercase font-mono mb-0.5">Scheme</label>
                <select
                  value={filterSchemeOptionId}
                  onChange={(e) => setFilterSchemeOptionId(e.target.value)}
                  className="bg-zinc-900 border border-zinc-700 rounded px-2.5 py-1 text-zinc-200 text-xs"
                >
                  <option value="">All Schemes</option>
                  {holdings.map((h) => (
                    <option key={h.schemeOptionId} value={h.schemeOptionId}>
                      {h.fundName}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-[10px] text-zinc-400 uppercase font-mono mb-0.5">Transaction Type</label>
                <select
                  value={filterType}
                  onChange={(e) => setFilterType(e.target.value)}
                  className="bg-zinc-900 border border-zinc-700 rounded px-2.5 py-1 text-zinc-200 text-xs"
                >
                  <option value="">All Types</option>
                  <option value="PURCHASE">Purchase</option>
                  <option value="SIP_PURCHASE">SIP Purchase</option>
                  <option value="REDEMPTION">Redemption</option>
                  <option value="SWITCH_IN">Switch In</option>
                  <option value="SWITCH_OUT">Switch Out</option>
                  <option value="DIVIDEND_PAYOUT">Dividend Payout</option>
                  <option value="DIVIDEND_REINVESTMENT">Dividend Reinvestment</option>
                  <option value="REVERSAL">Reversal</option>
                </select>
              </div>

              <div>
                <label className="block text-[10px] text-zinc-400 uppercase font-mono mb-0.5">From Date</label>
                <input
                  type="date"
                  value={filterStartDate}
                  onChange={(e) => setFilterStartDate(e.target.value)}
                  className="bg-zinc-900 border border-zinc-700 rounded px-2 py-0.5 text-zinc-200 text-xs font-mono"
                />
              </div>

              <div>
                <label className="block text-[10px] text-zinc-400 uppercase font-mono mb-0.5">To Date</label>
                <input
                  type="date"
                  value={filterEndDate}
                  onChange={(e) => setFilterEndDate(e.target.value)}
                  className="bg-zinc-900 border border-zinc-700 rounded px-2 py-0.5 text-zinc-200 text-xs font-mono"
                />
              </div>

              {(filterSchemeOptionId || filterType || filterStartDate || filterEndDate) && (
                <button
                  onClick={() => {
                    setFilterSchemeOptionId('');
                    setFilterType('');
                    setFilterStartDate('');
                    setFilterEndDate('');
                  }}
                  className="text-amber-400 hover:text-amber-300 text-xs underline mt-3.5"
                >
                  Reset Filters
                </button>
              )}
            </div>

            {/* Transactions Table */}
            {loadingTx ? (
              <div className="p-12 text-center text-zinc-500 text-xs">Loading ledger entries...</div>
            ) : txError ? (
              <div className="p-4 bg-rose-950/40 border border-rose-800 rounded-lg text-rose-300 text-xs">
                {txError}
              </div>
            ) : transactions.length === 0 ? (
              <div className="p-12 text-center text-zinc-500 text-xs border border-zinc-800 rounded-xl bg-zinc-950/40">
                <p className="font-semibold text-zinc-400 mb-1">No Transactions Found</p>
                <p className="text-zinc-500 max-w-md mx-auto">
                  No ledger entries match the selected filters. Record a manual transaction or import your CAS statement to populate your ledger.
                </p>
                <button
                  onClick={() => setIsManualModalOpen(true)}
                  className="mt-4 px-3 py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg text-xs font-medium transition"
                >
                  Record First Transaction
                </button>
              </div>
            ) : (
              <div className="border border-zinc-800 rounded-xl overflow-hidden bg-zinc-950/60">
                <div className="overflow-x-auto">
                  <table className="w-full text-xs text-left">
                    <thead className="bg-zinc-900/80 text-zinc-400 uppercase text-[10px] font-mono border-b border-zinc-800">
                      <tr>
                        <th className="py-2.5 px-4">Date</th>
                        <th className="py-2.5 px-3">Scheme & Folio</th>
                        <th className="py-2.5 px-3">Type</th>
                        <th className="py-2.5 px-3 text-right">Units</th>
                        <th className="py-2.5 px-3 text-right">NAV</th>
                        <th className="py-2.5 px-3 text-right">Net Amount</th>
                        <th className="py-2.5 px-3 text-center">Source</th>
                        <th className="py-2.5 px-3 text-center">Status</th>
                        <th className="py-2.5 px-3 text-center">Action</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-zinc-800/80">
                      {transactions.map((tx) => (
                        <tr
                          key={tx.id}
                          className={`hover:bg-zinc-800/30 transition ${
                            tx.isReversed ? 'opacity-50 line-through bg-zinc-950/90' : ''
                          }`}
                        >
                          <td className="py-3 px-4 font-mono text-zinc-300 whitespace-nowrap">
                            {tx.transactionDate}
                          </td>
                          <td className="py-3 px-3">
                            <div className="font-medium text-zinc-100 truncate max-w-[200px]" title={tx.schemeName}>
                              {tx.schemeName}
                            </div>
                            <div className="text-[11px] text-zinc-500 font-mono">
                              {tx.folioNumber ? `Folio: ${tx.folioNumber}` : tx.planType || 'DIRECT'}
                            </div>
                          </td>
                          <td className="py-3 px-3 whitespace-nowrap">
                            {renderTxTypeBadge(tx.transactionType)}
                          </td>
                          <td className="py-3 px-3 text-right font-mono text-zinc-200 whitespace-nowrap">
                            {tx.units != null ? formatNumber(tx.units, 4) : '—'}
                          </td>
                          <td className="py-3 px-3 text-right font-mono text-zinc-300 whitespace-nowrap">
                            {tx.nav != null ? `₹${formatNumber(tx.nav, 4)}` : '—'}
                          </td>
                          <td className="py-3 px-3 text-right font-mono font-semibold text-zinc-100 whitespace-nowrap">
                            {tx.netAmount != null ? formatCurrency(tx.netAmount) : '—'}
                          </td>
                          <td className="py-3 px-3 text-center whitespace-nowrap">
                            {renderSourceBadge(tx.sourceAttribution)}
                          </td>
                          <td className="py-3 px-3 text-center whitespace-nowrap">
                            {tx.isReversed ? (
                              <span
                                className="px-1.5 py-0.5 rounded text-[10px] font-mono bg-rose-950/80 text-rose-300 border border-rose-800"
                                title={`Reversed: ${tx.reversalReason || 'Compensated'}`}
                              >
                                REVERSED
                              </span>
                            ) : (
                              <span className="px-1.5 py-0.5 rounded text-[10px] font-mono bg-emerald-950/60 text-emerald-300 border border-emerald-800/60">
                                ACTIVE
                              </span>
                            )}
                          </td>
                          <td className="py-3 px-3 text-center whitespace-nowrap">
                            {!tx.isReversed && tx.transactionType !== 'REVERSAL' && (
                              <button
                                onClick={() => {
                                  setSelectedTxToReverse(tx);
                                  setIsReverseModalOpen(true);
                                }}
                                className="text-[11px] text-rose-400 hover:text-rose-300 underline font-medium"
                              >
                                Reverse
                              </button>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        )}

        {/* ============================================================== */}
        {/* SUB-TAB 3: HOLDINGS RECONCILIATION */}
        {/* ============================================================== */}
        {activeTab === 'reconciliation' && (
          <div className="space-y-6">
            {loadingRecon ? (
              <div className="p-12 text-center text-zinc-500 text-xs">
                Auditing reported units against cumulative ledger transaction streams...
              </div>
            ) : reconError ? (
              <div className="p-4 bg-rose-950/40 border border-rose-800 rounded-lg text-rose-300 text-xs">
                {reconError}
              </div>
            ) : reconciliation ? (
              <>
                {/* Overall Status Banner */}
                <div className="p-4 bg-zinc-950/80 border border-zinc-800 rounded-xl flex flex-col md:flex-row md:items-center justify-between gap-4">
                  <div>
                    <div className="flex items-center gap-2 mb-1">
                      <span className="text-xs text-zinc-400 font-medium">Reconciliation Audit State:</span>
                      {renderReconBadge(reconciliation.overallStatus)}
                    </div>
                    <p className="text-xs text-zinc-400 leading-relaxed max-w-3xl">
                      {reconciliation.reconciliationSummary}
                    </p>
                  </div>
                  <div className="text-right shrink-0">
                    <div className="text-xs text-zinc-500 font-mono">
                      Audit Cutoff: {reconciliation.reconciliationDate}
                    </div>
                  </div>
                </div>

                {/* Status Count Pills */}
                <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
                  <div className="p-3 bg-zinc-950/60 border border-zinc-800 rounded-lg">
                    <div className="text-[10px] font-mono text-zinc-400 uppercase">Total Holdings</div>
                    <div className="text-xl font-bold font-mono text-zinc-200 mt-0.5">
                      {reconciliation.totalHoldingsEvaluated}
                    </div>
                  </div>
                  <div className="p-3 bg-zinc-950/60 border border-zinc-800 rounded-lg">
                    <div className="text-[10px] font-mono text-emerald-400 uppercase">Reconciled</div>
                    <div className="text-xl font-bold font-mono text-emerald-400 mt-0.5">
                      {reconciliation.reconciledCount}
                    </div>
                  </div>
                  <div className="p-3 bg-zinc-950/60 border border-zinc-800 rounded-lg">
                    <div className="text-[10px] font-mono text-sky-400 uppercase">Partially Reconciled</div>
                    <div className="text-xl font-bold font-mono text-sky-400 mt-0.5">
                      {reconciliation.partiallyReconciledCount}
                    </div>
                  </div>
                  <div className="p-3 bg-zinc-950/60 border border-zinc-800 rounded-lg">
                    <div className="text-[10px] font-mono text-amber-400 uppercase">Insufficient History</div>
                    <div className="text-xl font-bold font-mono text-amber-400 mt-0.5">
                      {reconciliation.insufficientHistoryCount}
                    </div>
                  </div>
                  <div className="p-3 bg-zinc-950/60 border border-zinc-800 rounded-lg">
                    <div className="text-[10px] font-mono text-rose-400 uppercase">Unreconciled / Conflict</div>
                    <div className="text-xl font-bold font-mono text-rose-400 mt-0.5">
                      {reconciliation.unreconciledCount + reconciliation.sourceConflictCount}
                    </div>
                  </div>
                </div>

                {/* Reconciliation Comparison Table */}
                <div className="border border-zinc-800 rounded-xl overflow-hidden bg-zinc-950/60">
                  <div className="overflow-x-auto">
                    <table className="w-full text-xs text-left">
                      <thead className="bg-zinc-900/80 text-zinc-400 uppercase text-[10px] font-mono border-b border-zinc-800">
                        <tr>
                          <th className="py-2.5 px-4">Scheme</th>
                          <th className="py-2.5 px-3 text-right">Reported Units</th>
                          <th className="py-2.5 px-3 text-right">Ledger Inflows (+)</th>
                          <th className="py-2.5 px-3 text-right">Ledger Outflows (-)</th>
                          <th className="py-2.5 px-3 text-right">Net Ledger Units</th>
                          <th className="py-2.5 px-3 text-right">Unit Difference</th>
                          <th className="py-2.5 px-3 text-center">Status</th>
                          <th className="py-2.5 px-4">Reconciliation Notes & Diagnostics</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-zinc-800/80">
                        {reconciliation.items.map((item) => (
                          <tr key={item.schemeOptionId} className="hover:bg-zinc-800/30 transition">
                            <td className="py-3 px-4">
                              <div className="font-medium text-zinc-100">{item.schemeName}</div>
                              <div className="text-[11px] text-zinc-500 font-mono">
                                {item.folioNumber ? `Folio: ${item.folioNumber}` : 'DIRECT'} • {item.transactionCount} transactions
                              </div>
                            </td>
                            <td className="py-3 px-3 text-right font-mono text-zinc-200">
                              {formatNumber(item.reportedUnits, 4)}
                            </td>
                            <td className="py-3 px-3 text-right font-mono text-emerald-400">
                              +{formatNumber(item.ledgerInflowUnits, 4)}
                            </td>
                            <td className="py-3 px-3 text-right font-mono text-rose-400">
                              -{formatNumber(item.ledgerOutflowUnits, 4)}
                            </td>
                            <td className="py-3 px-3 text-right font-mono font-semibold text-zinc-100">
                              {formatNumber(item.netLedgerUnits, 4)}
                            </td>
                            <td className={`py-3 px-3 text-right font-mono font-semibold ${
                              Math.abs(item.unitDifference) < 0.0001 ? 'text-emerald-400' : 'text-rose-400'
                            }`}>
                              {item.unitDifference > 0 ? '+' : ''}{formatNumber(item.unitDifference, 4)}
                            </td>
                            <td className="py-3 px-3 text-center">
                              {renderReconBadge(item.reconciliationStatus)}
                            </td>
                            <td className="py-3 px-4 text-zinc-400 text-xs">
                              {item.reconciliationNotes}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              </>
            ) : null}
          </div>
        )}

        {/* ============================================================== */}
        {/* SUB-TAB 4: CAS IMPORT REVIEW */}
        {/* ============================================================== */}
        {activeTab === 'casReview' && (
          <div className="space-y-4">
            <div className="p-4 bg-zinc-950/70 border border-zinc-800 rounded-xl flex items-center justify-between gap-4">
              <div>
                <h3 className="text-sm font-semibold text-zinc-200">CAS Transaction Staging & Confirmation Review</h3>
                <p className="text-xs text-zinc-400 mt-0.5">
                  Inspect raw parsed statement transactions and their canonical scheme resolution mapping.
                </p>
              </div>
              <div className="flex items-center gap-2">
                <input
                  type="number"
                  placeholder="Batch ID"
                  value={importBatchIdInput}
                  onChange={(e) => setImportBatchIdInput(e.target.value)}
                  className="w-24 px-2.5 py-1.5 bg-zinc-900 border border-zinc-700 rounded-lg text-xs font-mono text-zinc-100"
                />
                <button
                  onClick={() => loadImportTransactions(parseInt(importBatchIdInput) || 1)}
                  className="px-3 py-1.5 bg-sky-600 hover:bg-sky-500 text-white rounded-lg text-xs font-medium transition"
                >
                  Review Batch
                </button>
              </div>
            </div>

            {loadingImportTx ? (
              <div className="p-12 text-center text-zinc-500 text-xs">Loading import transaction records...</div>
            ) : importTxError ? (
              <div className="p-4 bg-rose-950/40 border border-rose-800 rounded-lg text-rose-300 text-xs">
                {importTxError}
              </div>
            ) : importTransactions.length === 0 ? (
              <div className="p-12 text-center text-zinc-500 text-xs border border-zinc-800 rounded-xl bg-zinc-950/40">
                No staged import transactions found for Batch #{importBatchIdInput}.
              </div>
            ) : (
              <div className="border border-zinc-800 rounded-xl overflow-hidden bg-zinc-950/60">
                <div className="overflow-x-auto">
                  <table className="w-full text-xs text-left">
                    <thead className="bg-zinc-900/80 text-zinc-400 uppercase text-[10px] font-mono border-b border-zinc-800">
                      <tr>
                        <th className="py-2.5 px-4">Date</th>
                        <th className="py-2.5 px-3">Reported Scheme</th>
                        <th className="py-2.5 px-3">Raw Type</th>
                        <th className="py-2.5 px-3">Canonical Type</th>
                        <th className="py-2.5 px-3 text-right">Units</th>
                        <th className="py-2.5 px-3 text-right">NAV</th>
                        <th className="py-2.5 px-3 text-right">Amount</th>
                        <th className="py-2.5 px-3 text-center">Resolution</th>
                        <th className="py-2.5 px-3 text-center">Confirmed</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-zinc-800/80">
                      {importTransactions.map((tx) => (
                        <tr key={tx.id} className="hover:bg-zinc-800/30 transition">
                          <td className="py-3 px-4 font-mono text-zinc-300 whitespace-nowrap">
                            {tx.transactionDate}
                          </td>
                          <td className="py-3 px-3">
                            <div className="font-medium text-zinc-100 truncate max-w-[200px]" title={tx.schemeName}>
                              {tx.schemeName}
                            </div>
                            <div className="text-[11px] text-zinc-500 font-mono">
                              {tx.folioNumber ? `Folio: ${tx.folioNumber}` : '—'}
                            </div>
                          </td>
                          <td className="py-3 px-3 text-zinc-400 font-mono text-[11px]">
                            {tx.rawTransactionType || '—'}
                          </td>
                          <td className="py-3 px-3 whitespace-nowrap">
                            {renderTxTypeBadge(tx.canonicalTransactionType)}
                          </td>
                          <td className="py-3 px-3 text-right font-mono text-zinc-200">
                            {tx.units != null ? formatNumber(tx.units, 4) : '—'}
                          </td>
                          <td className="py-3 px-3 text-right font-mono text-zinc-300">
                            {tx.nav != null ? `₹${formatNumber(tx.nav, 4)}` : '—'}
                          </td>
                          <td className="py-3 px-3 text-right font-mono font-semibold text-zinc-100">
                            {tx.amount != null ? formatCurrency(tx.amount) : '—'}
                          </td>
                          <td className="py-3 px-3 text-center">
                            <span className={`px-1.5 py-0.5 rounded text-[10px] font-mono ${
                              tx.resolutionStatus === 'RESOLVED_DIRECT'
                                ? 'bg-emerald-950/60 text-emerald-300 border border-emerald-800/60'
                                : 'bg-amber-950/60 text-amber-300 border border-amber-800/60'
                            }`}>
                              {tx.resolutionStatus}
                            </span>
                          </td>
                          <td className="py-3 px-3 text-center">
                            {tx.isConfirmed ? (
                              <span className="text-emerald-400 font-semibold">✓ Yes</span>
                            ) : (
                              <span className="text-zinc-500">Pending</span>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Manual Transaction Modal */}
      {isManualModalOpen && (
        <ManualTransactionModal
          isOpen={isManualModalOpen}
          onClose={() => setIsManualModalOpen(false)}
          onSuccess={handleTransactionCreated}
          availableOptions={availableOptions}
        />
      )}

      {/* Reverse Transaction Modal */}
      {isReverseModalOpen && selectedTxToReverse && (
        <ReverseTransactionModal
          isOpen={isReverseModalOpen}
          onClose={() => {
            setIsReverseModalOpen(false);
            setSelectedTxToReverse(null);
          }}
          onSuccess={handleTransactionReversed}
          transaction={selectedTxToReverse}
        />
      )}
    </div>
  );
}

