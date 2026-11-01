'use client';

import React, { useState, useEffect, useMemo } from 'react';
import Link from 'next/link';
import { SchemeOption } from '@/types/domain';
import { fetchAllSchemeOptions } from '@/lib/api/schemes';
import {
  fetchMultiFundLookThrough,
  simulateCandidateFund,
  fetchDefaultPilotPortfolio,
  fetchSavedResearchPortfolio,
  saveResearchPortfolio,
  fetchStressReplayEpisodes,
  executePortfolioStressReplay,
  fetchPortfolioSummary,
  HistoricalMarketEpisodeDto,
  PortfolioStressReplayResponseDto,
  MultiFundLookThroughResponseDto,
  CandidateSimulationResponseDto,
  ResearchPortfolioFundItemDto,
} from '@/lib/api/portfolio';
import { ScenarioLabSection } from './ScenarioLabSection';
import { RiskAttributionSection } from './RiskAttributionSection';
import { RiskDriversSection } from './RiskDriversSection';

export function ResearchPortfolioView() {
  const [availableSchemes, setAvailableSchemes] = useState<SchemeOption[]>([]);
  const [selectedFunds, setSelectedFunds] = useState<ResearchPortfolioFundItemDto[]>([
    { schemeOptionId: 1, allocationWeight: 60.0 },
    { schemeOptionId: 10151, allocationWeight: 40.0 },
  ]);
  const [asOfDate, setAsOfDate] = useState<string>('2026-09-30');
  const [lookThroughData, setLookThroughData] = useState<MultiFundLookThroughResponseDto | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  // Active Sub-Tab
  const [activeTab, setActiveTab] = useState<'holdings' | 'overlap' | 'sectors' | 'risk' | 'attribution' | 'drivers' | 'replay' | 'scenario' | 'simulation' | 'evidence'>('holdings');

  // Security Search Filter
  const [securityFilter, setSecurityFilter] = useState<string>('');
  const [expandedSecurity, setExpandedSecurity] = useState<string | null>(null);

  // Candidate Simulation State
  const [candidateOptionId, setCandidateOptionId] = useState<number>(10262); // Default to Parag Parikh Flexi Cap
  const [proposedAllocation, setProposedAllocation] = useState<number>(20.0);
  const [simulationData, setSimulationData] = useState<CandidateSimulationResponseDto | null>(null);
  const [loadingSimulation, setLoadingSimulation] = useState<boolean>(false);
  const [simulationError, setSimulationError] = useState<string | null>(null);

  // Historical Stress Replay State
  const [episodes, setEpisodes] = useState<HistoricalMarketEpisodeDto[]>([]);
  const [loadingEpisodes, setLoadingEpisodes] = useState<boolean>(false);
  const [selectedEpisodeId, setSelectedEpisodeId] = useState<string>('EPISODE_2024_SEBI_MIDSMALL_SHAKEOUT');
  const [customStartDate, setCustomStartDate] = useState<string>('2024-02-08');
  const [customEndDate, setCustomEndDate] = useState<string>('2024-03-19');
  const [isCustomRange, setIsCustomRange] = useState<boolean>(false);
  const [replayData, setReplayData] = useState<PortfolioStressReplayResponseDto | null>(null);
  const [loadingReplay, setLoadingReplay] = useState<boolean>(false);
  const [replayError, setReplayError] = useState<string | null>(null);
  const [trajectoryFilter, setTrajectoryFilter] = useState<'milestones' | 'all'>('milestones');

  // Fund Adding to Builder
  const [addingFundId, setAddingFundId] = useState<string>('');

  // 1. Initial Load of Schemes
  useEffect(() => {
    let ignore = false;
    async function init() {
      try {
        const schemes = await fetchAllSchemeOptions();
        if (!ignore) {
          setAvailableSchemes(schemes);
        }
      } catch (err: unknown) {
        console.error('Failed to load schemes', err);
      }
    }
    init();
    return () => { ignore = true; };
  }, []);

  // 2. Fetch Look-Through Function
  const runLookThrough = async (fundsToRun = selectedFunds, dateToRun = asOfDate) => {
    if (fundsToRun.length === 0) {
      setError('Please add at least one fund to the research portfolio.');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const data = await fetchMultiFundLookThrough({
        funds: fundsToRun,
        asOfDate: dateToRun,
      });
      setLookThroughData(data);
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to calculate look-through exposures.');
    } finally {
      setLoading(false);
    }
  };

  // Initial calculation on mount
  useEffect(() => {
    let ignore = false;
    async function loadInitial() {
      setLoading(true);
      setError(null);
      try {
        const data = await fetchMultiFundLookThrough({
          funds: [
            { schemeOptionId: 1, allocationWeight: 60.0 },
            { schemeOptionId: 10151, allocationWeight: 40.0 },
          ],
          asOfDate: '2026-09-30',
        });
        if (!ignore) {
          setLookThroughData(data);
        }
      } catch (err: unknown) {
        if (!ignore) {
          setError((err as Error).message || 'Failed to calculate look-through exposures.');
        }
      } finally {
        if (!ignore) {
          setLoading(false);
        }
      }
    }
    loadInitial();
    return () => {
      ignore = true;
    };
  }, []);

  // 3. Allocation Calculations & Validation
  const totalAllocation = useMemo(() => {
    return selectedFunds.reduce((sum, f) => sum + (Number(f.allocationWeight) || 0), 0);
  }, [selectedFunds]);

  const isAllocation100 = Math.abs(totalAllocation - 100.0) <= 0.05;

  const handleWeightChange = (index: number, newWeight: number) => {
    const updated = [...selectedFunds];
    updated[index] = { ...updated[index], allocationWeight: Math.max(0, newWeight) };
    setSelectedFunds(updated);
  };

  const handleRemoveFund = (index: number) => {
    const updated = selectedFunds.filter((_, idx) => idx !== index);
    setSelectedFunds(updated);
  };

  const handleAddFund = () => {
    if (!addingFundId) return;
    const optId = parseInt(addingFundId, 10);
    if (selectedFunds.some(f => f.schemeOptionId === optId)) {
      setError('This scheme is already present in your research portfolio.');
      return;
    }
    const updated = [...selectedFunds, { schemeOptionId: optId, allocationWeight: 0.0 }];
    setSelectedFunds(updated);
    setAddingFundId('');
    setError(null);
  };

  const handleNormalizeWeights = () => {
    if (totalAllocation <= 0) return;
    const normalized = selectedFunds.map(f => ({
      ...f,
      allocationWeight: parseFloat(((f.allocationWeight / totalAllocation) * 100).toFixed(2)),
    }));
    setSelectedFunds(normalized);
  };

  const handleLoadPilot = async () => {
    setLoading(true);
    try {
      const pilot = await fetchDefaultPilotPortfolio();
      setSelectedFunds(pilot.funds);
      if (pilot.asOfDate) setAsOfDate(pilot.asOfDate);
      runLookThrough(pilot.funds, pilot.asOfDate || asOfDate);
      setStatusMessage('Loaded canonical pilot portfolio (HDFC Flexi Cap 60% + Parag Parikh ELSS 40%).');
      setTimeout(() => setStatusMessage(null), 4000);
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to load pilot portfolio');
    } finally {
      setLoading(false);
    }
  };

  const handleSavePortfolio = async () => {
    try {
      await saveResearchPortfolio({
        portfolioName: 'My Research Portfolio',
        funds: selectedFunds,
        totalAllocationPct: totalAllocation,
        isValid: isAllocation100,
      });
      setStatusMessage('Research portfolio saved to your workspace account.');
      setTimeout(() => setStatusMessage(null), 4000);
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to save research portfolio');
    }
  };

  const handleLoadSaved = async () => {
    try {
      const saved = await fetchSavedResearchPortfolio();
      if (saved.funds && saved.funds.length > 0) {
        setSelectedFunds(saved.funds);
        runLookThrough(saved.funds, asOfDate);
        setStatusMessage('Loaded saved research portfolio.');
        setTimeout(() => setStatusMessage(null), 4000);
      } else {
        setError('No saved research portfolio found on your account.');
      }
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to load saved portfolio');
    }
  };

  const handleLoadTrackedPortfolio = async () => {
    setLoading(true);
    try {
      const summary = await fetchPortfolioSummary();
      if (summary.holdings && summary.holdings.length > 0) {
        const totalVal = summary.totalAvailableValue || 0;
        const funds: ResearchPortfolioFundItemDto[] = summary.holdings.map(h => {
          let weight = 0;
          if (h.portfolioWeight && h.portfolioWeight > 0) {
            weight = h.portfolioWeight;
          } else if (totalVal > 0 && h.currentMarketValue) {
            weight = parseFloat(((h.currentMarketValue / totalVal) * 100).toFixed(2));
          } else {
            weight = parseFloat((100.0 / summary.holdings.length).toFixed(2));
          }
          return {
            schemeOptionId: h.schemeOptionId,
            allocationWeight: weight,
          };
        });
        setSelectedFunds(funds);
        runLookThrough(funds, asOfDate);
        setStatusMessage(`Loaded ${funds.length} tracked portfolio holdings with investor capital weights.`);
        setTimeout(() => setStatusMessage(null), 4000);
      } else {
        setError('No tracked holdings found in your portfolio workspace. Add holdings or import CAS statement first.');
      }
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to load tracked portfolio holdings');
    } finally {
      setLoading(false);
    }
  };

  // 4. Candidate Simulation Handler
  const handleRunSimulation = async () => {
    if (!candidateOptionId || proposedAllocation <= 0 || proposedAllocation >= 100) {
      setSimulationError('Proposed allocation must be between 1% and 99%.');
      return;
    }
    setLoadingSimulation(true);
    setSimulationError(null);
    try {
      const data = await simulateCandidateFund({
        currentFunds: selectedFunds,
        candidateSchemeOptionId: candidateOptionId,
        proposedAllocationPct: proposedAllocation,
        asOfDate,
      });
      setSimulationData(data);
    } catch (err: unknown) {
      setSimulationError((err as Error).message || 'Failed to evaluate candidate simulation.');
    } finally {
      setLoadingSimulation(false);
    }
  };

  // 5. Historical Stress Replay Handlers
  useEffect(() => {
    let ignore = false;
    async function loadEpisodes() {
      if (selectedFunds.length === 0) {
        setEpisodes([]);
        return;
      }
      setLoadingEpisodes(true);
      try {
        const epList = await fetchStressReplayEpisodes(selectedFunds);
        if (!ignore) {
          setEpisodes(epList);
          // Auto-select first available episode if current selected isn't available
          const currentValid = epList.find(e => e.episodeId === selectedEpisodeId && e.isAvailable);
          if (!currentValid) {
            const firstAvail = epList.find(e => e.isAvailable);
            if (firstAvail) {
              setSelectedEpisodeId(firstAvail.episodeId);
              setCustomStartDate(firstAvail.startDate);
              setCustomEndDate(firstAvail.endDate);
            }
          }
        }
      } catch (err: unknown) {
        console.error('Failed to load stress replay episodes', err);
      } finally {
        if (!ignore) {
          setLoadingEpisodes(false);
        }
      }
    }
    loadEpisodes();
    return () => {
      ignore = true;
    };
  }, [selectedFunds]);

  const handleRunStressReplay = async () => {
    if (selectedFunds.length === 0) {
      setReplayError('Please select at least one fund for the portfolio.');
      return;
    }
    setLoadingReplay(true);
    setReplayError(null);
    try {
      let reqStart = customStartDate;
      let reqEnd = customEndDate;
      let epId: string | undefined = undefined;

      if (!isCustomRange) {
        const activeEp = episodes.find(e => e.episodeId === selectedEpisodeId);
        if (activeEp) {
          reqStart = activeEp.startDate;
          reqEnd = activeEp.endDate;
          epId = activeEp.episodeId;
        }
      }

      const res = await executePortfolioStressReplay({
        funds: selectedFunds,
        startDate: reqStart,
        endDate: reqEnd,
        episodeId: epId,
      });
      setReplayData(res);
    } catch (err: unknown) {
      setReplayError((err as Error).message || 'Failed to execute portfolio stress replay.');
    } finally {
      setLoadingReplay(false);
    }
  };

  // Auto-run stress replay on first switch to replay tab if not yet loaded
  useEffect(() => {
    if (activeTab === 'replay' && !replayData && !loadingReplay && episodes.length > 0) {
      handleRunStressReplay();
    }
  }, [activeTab, episodes]);

  // Filtered Securities
  const filteredSecurities = useMemo(() => {
    if (!lookThroughData?.consolidatedSecurities) return [];
    if (!securityFilter.trim()) return lookThroughData.consolidatedSecurities;
    const term = securityFilter.toLowerCase();
    return lookThroughData.consolidatedSecurities.filter(
      s => s.canonicalName.toLowerCase().includes(term) ||
           (s.isin && s.isin.toLowerCase().includes(term)) ||
           s.sector.toLowerCase().includes(term)
    );
  }, [lookThroughData, securityFilter]);

  const conc = lookThroughData?.concentrationMetrics;

  return (
    <div className="space-y-6">
      {/* Top Banner / Actions Bar */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border pb-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="font-mono text-[11px] text-text-tertiary">RESEARCH CAPABILITY V1</span>
            <span className="status-badge state-verified text-[10px]">POINT-IN-TIME EVIDENCE</span>
          </div>
          <h2 className="text-[17px] font-bold text-text-primary tracking-tight">
            Multi-Fund Look-Through & Exposure Aggregation
          </h2>
          <p className="font-sans text-[13px] text-text-secondary mt-0.5">
            Evaluate consolidated underlying security exposures, sector drift, holdings overlap, and test candidate fund allocations.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <button
            onClick={handleLoadTrackedPortfolio}
            className="px-3.5 py-1.5 rounded bg-cyan-950/60 border border-cyan-500/40 text-cyan-400 font-mono text-[11.5px] font-semibold hover:bg-cyan-900/40 transition-all flex items-center gap-1.5"
            title="Load your actual tracked portfolio holdings and capital weights into look-through analysis"
          >
            <span>📋 Load My Tracked Holdings</span>
          </button>
          <button
            onClick={handleLoadPilot}
            className="px-3.5 py-1.5 rounded bg-surface border border-border text-text-primary font-mono text-[11.5px] hover:bg-surface-raised transition-all flex items-center gap-1.5"
            title="Load canonical pilot funds: HDFC Flexi Cap 60% + PPFAS ELSS 40%"
          >
            <span>⚡ Load Canonical Pilot</span>
          </button>
          <button
            onClick={handleLoadSaved}
            className="px-3.5 py-1.5 rounded bg-surface border border-border text-text-primary font-mono text-[11.5px] hover:bg-surface-raised transition-all flex items-center gap-1.5"
          >
            <span>📂 Load Saved</span>
          </button>
          <button
            onClick={handleSavePortfolio}
            className="px-3.5 py-1.5 rounded bg-accent/10 border border-accent/40 text-accent font-mono text-[11.5px] font-semibold hover:bg-accent/20 transition-all flex items-center gap-1.5"
          >
            <span>💾 Save Portfolio</span>
          </button>
        </div>
      </div>

      {/* Status & Error Toasts */}
      {statusMessage && (
        <div className="p-3 rounded bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 font-mono text-[12px] flex items-center gap-2">
          <span>✓</span>
          <span>{statusMessage}</span>
        </div>
      )}
      {error && (
        <div className="p-3 rounded bg-rose-500/10 border border-rose-500/30 text-rose-400 font-mono text-[12px] flex items-center justify-between">
          <span>⚠️ {error}</span>
          <button onClick={() => setError(null)} className="text-text-tertiary hover:text-text-primary">✕</button>
        </div>
      )}

      {/* SECTION 1: PORTFOLIO BUILDER & ALLOCATION VALIDATOR */}
      <div className="panel p-5 space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border pb-3">
          <div>
            <span className="font-mono text-[10px] uppercase tracking-wider text-text-tertiary">01 BUILDER</span>
            <h3 className="text-[14px] font-semibold text-text-primary">Constituent Scheme Allocations</h3>
          </div>
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-1.5">
              <label htmlFor="asOfDateInput" className="font-mono text-[11px] text-text-tertiary">Analysis Date:</label>
              <input
                id="asOfDateInput"
                type="date"
                value={asOfDate}
                onChange={e => setAsOfDate(e.target.value)}
                className="field field-mono text-[11px] py-1 px-2 h-7"
              />
            </div>
            <button
              onClick={() => runLookThrough()}
              disabled={loading || selectedFunds.length === 0}
              className="px-4 py-1.5 rounded bg-accent text-surface-dark font-mono text-[11.5px] font-bold hover:bg-accent/90 disabled:opacity-50 transition-all"
            >
              {loading ? 'Evaluating...' : '↻ Evaluate Look-Through'}
            </button>
          </div>
        </div>

        {/* Funds Allocation Table */}
        <div className="overflow-x-auto rounded border border-border">
          <table className="w-full text-left font-mono text-[11px]">
            <thead className="bg-surface-raised text-text-muted text-[10px] border-b border-border">
              <tr>
                <th className="py-2.5 px-3">Scheme Option</th>
                <th className="py-2.5 px-3">Plan / Option</th>
                <th className="py-2.5 px-3">AMFI Code</th>
                <th className="py-2.5 px-3 text-right">Allocation Weight (%)</th>
                <th className="py-2.5 px-3 text-right">Observation Date</th>
                <th className="py-2.5 px-3 text-center">Status</th>
                <th className="py-2.5 px-3 text-center">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border bg-surface">
              {selectedFunds.map((fund, idx) => {
                const opt = availableSchemes.find(o => o.id === fund.schemeOptionId);
                const constituent = lookThroughData?.constituentFunds.find(c => c.schemeOptionId === fund.schemeOptionId);
                const schemeName = opt?.plan?.scheme?.name || constituent?.fundName || `Scheme Option #${fund.schemeOptionId}`;
                const planType = opt?.plan?.planType || 'DIRECT';
                const optionType = opt?.optionType || 'GROWTH';
                const amfiCode = opt?.amfiCode || constituent?.amfiCode || '—';

                return (
                  <tr key={fund.schemeOptionId} className="hover:bg-surface-raised/40">
                    <td className="py-2.5 px-3 font-sans font-medium text-text-primary">
                      <Link href={`/funds/${fund.schemeOptionId}`} className="hover:text-accent">
                        {schemeName}
                      </Link>
                    </td>
                    <td className="py-2.5 px-3 text-text-secondary">
                      {planType} - {optionType}
                    </td>
                    <td className="py-2.5 px-3 text-text-tertiary">
                      {amfiCode}
                    </td>
                    <td className="py-2.5 px-3 text-right">
                      <div className="flex items-center justify-end gap-1">
                        <input
                          type="number"
                          step="0.01"
                          min="0"
                          max="100"
                          value={fund.allocationWeight}
                          onChange={e => handleWeightChange(idx, parseFloat(e.target.value) || 0)}
                          className="field field-mono w-24 text-right py-1 px-2 text-[11px] h-7 font-bold text-accent"
                        />
                        <span className="text-text-tertiary">%</span>
                      </div>
                    </td>
                    <td className="py-2.5 px-3 text-right text-text-secondary">
                      {constituent?.observationDate || '—'}
                      {constituent?.temporalMismatchDays ? (
                        <span className="ml-1 text-[9px] text-amber-400">(-{constituent.temporalMismatchDays}d)</span>
                      ) : null}
                    </td>
                    <td className="py-2.5 px-3 text-center">
                      <span className={`status-badge text-[9px] ${
                        constituent?.status === 'VALID' ? 'state-verified' : 'state-candidate'
                      }`}>
                        {constituent?.status || 'PENDING'}
                      </span>
                    </td>
                    <td className="py-2.5 px-3 text-center">
                      <button
                        onClick={() => handleRemoveFund(idx)}
                        className="text-text-tertiary hover:text-rose-400 font-bold px-2 py-0.5"
                        title="Remove fund from portfolio"
                      >
                        ✕
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        {/* Allocation Summary & Add Fund Row */}
        <div className="flex flex-wrap items-center justify-between gap-4 pt-2">
          {/* Add Scheme Dropdown */}
          <div className="flex flex-wrap items-center gap-2">
            <select
              value={addingFundId}
              onChange={e => setAddingFundId(e.target.value)}
              className="field text-[11px] py-1 px-2 h-8 max-w-sm"
            >
              <option value="">— Select scheme to add —</option>
              {availableSchemes
                .filter(s => !selectedFunds.some(f => f.schemeOptionId === s.id))
                .map(s => (
                  <option key={s.id} value={s.id}>
                    {s.plan?.scheme?.name} ({s.plan?.planType}) [AMFI: {s.amfiCode || 'N/A'}]
                  </option>
                ))}
            </select>
            <button
              onClick={handleAddFund}
              disabled={!addingFundId}
              className="px-3 py-1.5 rounded bg-surface border border-border text-text-primary font-mono text-[11px] font-semibold hover:bg-surface-raised disabled:opacity-50"
            >
              + Add to Portfolio
            </button>
          </div>

          {/* Allocation Total & Normalizer */}
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2 font-mono text-[12px]">
              <span className="text-text-secondary">Total Allocation:</span>
              <span className={`font-bold ${isAllocation100 ? 'text-emerald-400' : 'text-amber-400'}`}>
                {totalAllocation.toFixed(2)}%
              </span>
              <span className={`status-badge text-[9px] ${isAllocation100 ? 'state-verified' : 'state-candidate'}`}>
                {isAllocation100 ? '100% ALLOCATED' : 'PARTIAL / UNEQUAL'}
              </span>
            </div>

            {!isAllocation100 && totalAllocation > 0 && (
              <button
                onClick={handleNormalizeWeights}
                className="px-3 py-1 rounded bg-amber-500/10 border border-amber-500/30 text-amber-300 font-mono text-[10.5px] hover:bg-amber-500/20"
                title="Scale all weights proportionately to sum to exactly 100.00%"
              >
                Normalize to 100%
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Equal-Weight Assumption vs Actual Weights Banner */}
      {lookThroughData && (
        <div className={`p-3.5 rounded-lg border flex flex-wrap items-center justify-between gap-3 text-xs font-mono ${
          lookThroughData.isEqualWeightAssumption
            ? 'bg-amber-500/10 border-amber-500/30 text-amber-300'
            : 'bg-emerald-500/10 border-emerald-500/30 text-emerald-300'
        }`}>
          <div className="flex items-center gap-2">
            <span className="font-bold">
              {lookThroughData.isEqualWeightAssumption ? '⚠️ EQUAL-WEIGHT CONVENTION APPLIED' : '✓ INVESTOR CAPITAL ALLOCATION APPLIED'}
            </span>
            <span className="text-text-secondary">
              {lookThroughData.isEqualWeightAssumption
                ? 'Portfolio look-through exposures are calculated assuming equal allocation across constituent funds. Not reflective of your actual capital weighting.'
                : 'Exposures are calculated using exact specified capital allocation weights summing to 100%.'}
            </span>
          </div>
          <span className="text-[10px] uppercase tracking-wider px-2 py-0.5 rounded bg-surface border border-border">
            {lookThroughData.isEqualWeightAssumption ? 'UNWEIGHTED MODEL' : 'EXACT CAPITAL WEIGHTED'}
          </span>
        </div>
      )}

      {/* Institutional Non-Advisory Epistemic Disclaimer */}
      <div className="panel p-3 bg-surface border-l-2 border-l-accent flex items-start gap-2 text-[11px] font-mono text-text-muted">
        <span className="text-accent font-bold shrink-0">ℹ</span>
        <p>
          <strong className="text-text-secondary">NON-ADVISORY INSTITUTIONAL DISCLOSURE:</strong> All underlying security look-through calculations, sector aggregations, concentration scores, and risk stress simulations are deterministic empirical computations grounded in statutory fund disclosures. YUKIRA does not offer portfolio recommendations, asset allocation advice, or buy/sell/hold ratings.
        </p>
      </div>

      {/* Allocator Due Diligence Investigation Prompts */}
      {lookThroughData?.investigationPrompts && lookThroughData.investigationPrompts.length > 0 && (
        <div className="panel p-4 bg-surface space-y-2 border-l-2 border-l-primary">
          <div className="flex items-center justify-between">
            <span className="mono-meta text-primary font-semibold">ALLOCATOR DUE DILIGENCE</span>
            <span className="text-[10px] font-mono uppercase text-text-tertiary">Portfolio Investigation Questions</span>
          </div>
          <ul className="space-y-1.5 text-xs text-text-secondary list-disc list-inside">
            {lookThroughData.investigationPrompts.map((prompt, idx) => (
              <li key={idx} className="leading-relaxed">
                <span className="text-text-primary font-medium">{prompt}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* SECTION 2: TOP KPI METRIC TILES */}
      {conc && (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-7">
          <div className="metric-tile">
            <span className="eyebrow">Constituent Funds</span>
            <div className="metric-rule" />
            <p className="data-value-lg">{selectedFunds.length}</p>
            <p className="mt-auto pt-1 font-mono text-[10px] text-text-tertiary">Disclosed portfolios</p>
          </div>

          <div className="metric-tile">
            <span className="eyebrow">Equity Coverage</span>
            <div className="metric-rule" />
            <p className="data-value-lg">{conc.disclosedEquityCoveragePct.toFixed(1)}%</p>
            <p className="mt-auto pt-1 font-mono text-[10px] text-text-tertiary">Disclosed holdings</p>
          </div>

          <div className="metric-tile">
            <span className="eyebrow">Top 5 Weight</span>
            <div className="metric-rule" />
            <p className="data-value-lg text-accent">{conc.top5WeightPct.toFixed(1)}%</p>
            <p className="mt-auto pt-1 font-mono text-[10px] text-text-tertiary">Top 5 positions</p>
          </div>

          <div className="metric-tile">
            <span className="eyebrow">Top 10 Weight</span>
            <div className="metric-rule" />
            <p className="data-value-lg text-accent">{conc.top10WeightPct.toFixed(1)}%</p>
            <p className="mt-auto pt-1 font-mono text-[10px] text-text-tertiary">Top 10 positions</p>
          </div>

          <div className="metric-tile">
            <span className="eyebrow">HHI Index</span>
            <div className="metric-rule" />
            <p className="data-value-lg">{conc.herfindahlHirschmanIndex.toFixed(0)}</p>
            <p className="mt-auto pt-1 font-mono text-[10px] text-text-tertiary truncate">{conc.hhiInterpretation}</p>
          </div>

          <div className="metric-tile">
            <span className="eyebrow">Multi-Fund Shared</span>
            <div className="metric-rule" />
            <p className="data-value-lg text-amber-400">{conc.sharedSecuritiesWeightPct.toFixed(1)}%</p>
            <p className="mt-auto pt-1 font-mono text-[10px] text-text-tertiary">{conc.sharedSecuritiesCount} securities in ≥2 funds</p>
          </div>

          <div className="metric-tile">
            <span className="eyebrow">Unique Securities</span>
            <div className="metric-rule" />
            <p className="data-value-lg">{conc.uniqueSecuritiesCount}</p>
            <p className="mt-auto pt-1 font-mono text-[10px] text-text-tertiary">Distinct holdings</p>
          </div>
        </div>
      )}

      {/* SECTION 3: SHARED EXPOSURE DIAGNOSTIC ALERTS */}
      {lookThroughData?.sharedDiagnostics && lookThroughData.sharedDiagnostics.length > 0 && (
        <div className="space-y-2">
          {lookThroughData.sharedDiagnostics.map((diag, idx) => (
            <div
              key={idx}
              className={`p-3.5 rounded border text-[12px] flex items-start gap-3 ${
                diag.severity === 'WARNING'
                  ? 'bg-amber-500/10 border-amber-500/30 text-amber-300'
                  : diag.severity === 'NOTE'
                  ? 'bg-blue-500/10 border-blue-500/30 text-blue-300'
                  : 'bg-surface-raised border-border text-text-secondary'
              }`}
            >
              <span className="text-[14px]">
                {diag.severity === 'WARNING' ? '⚠️' : diag.severity === 'NOTE' ? 'ℹ️' : '💡'}
              </span>
              <div className="space-y-0.5">
                <span className="font-semibold block">{diag.title}</span>
                <p className="font-mono text-[11px] opacity-90">{diag.diagnostic}</p>
                <p className="font-mono text-[10px] text-text-tertiary mt-1">Lineage: {diag.evidence}</p>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* SECTION 4: INTERACTIVE TABS */}
      <div className="space-y-4">
        {/* Tab Controls */}
        <div className="flex border-b border-border gap-2 font-mono text-[12px] overflow-x-auto">
          <button
            onClick={() => setActiveTab('holdings')}
            className={`pb-2.5 px-3 border-b-2 font-semibold transition-all whitespace-nowrap ${
              activeTab === 'holdings'
                ? 'border-accent text-accent'
                : 'border-transparent text-text-tertiary hover:text-text-primary'
            }`}
          >
            01 Consolidated Holdings ({lookThroughData?.consolidatedSecurities.length || 0})
          </button>
          <button
            onClick={() => setActiveTab('overlap')}
            className={`pb-2.5 px-3 border-b-2 font-semibold transition-all whitespace-nowrap ${
              activeTab === 'overlap'
                ? 'border-accent text-accent'
                : 'border-transparent text-text-tertiary hover:text-text-primary'
            }`}
          >
            02 Pairwise Overlap Matrix ({lookThroughData?.pairwiseOverlapMatrix.length || 0})
          </button>
          <button
            onClick={() => setActiveTab('sectors')}
            className={`pb-2.5 px-3 border-b-2 font-semibold transition-all whitespace-nowrap ${
              activeTab === 'sectors'
                ? 'border-accent text-accent'
                : 'border-transparent text-text-tertiary hover:text-text-primary'
            }`}
          >
            03 Sector & Market-Cap
          </button>
          <button
            onClick={() => setActiveTab('risk')}
            className={`pb-2.5 px-3 border-b-2 font-semibold transition-all whitespace-nowrap ${
              activeTab === 'risk'
                ? 'border-accent text-accent'
                : 'border-transparent text-text-tertiary hover:text-text-primary'
            }`}
          >
            04 Risk & Factors 📊
          </button>
          <button
            onClick={() => setActiveTab('attribution')}
            className={`pb-2.5 px-3 border-b-2 font-semibold transition-all whitespace-nowrap ${
              activeTab === 'attribution'
                ? 'border-accent text-accent'
                : 'border-transparent text-text-tertiary hover:text-text-primary'
            }`}
          >
            05 Risk Attribution ⚖️
          </button>
          <button
            onClick={() => setActiveTab('drivers')}
            className={`pb-2.5 px-3 border-b-2 font-semibold transition-all whitespace-nowrap ${
              activeTab === 'drivers'
                ? 'border-accent text-accent'
                : 'border-transparent text-text-tertiary hover:text-text-primary'
            }`}
          >
            06 Risk Drivers 🎯
          </button>
          <button
            onClick={() => setActiveTab('replay')}
            className={`pb-2.5 px-3 border-b-2 font-semibold transition-all whitespace-nowrap ${
              activeTab === 'replay'
                ? 'border-accent text-accent'
                : 'border-transparent text-text-tertiary hover:text-text-primary'
            }`}
          >
            07 Stress & Replay ⚡
          </button>
          <button
            onClick={() => setActiveTab('scenario')}
            className={`pb-2.5 px-3 border-b-2 font-semibold transition-all whitespace-nowrap ${
              activeTab === 'scenario'
                ? 'border-accent text-accent'
                : 'border-transparent text-text-tertiary hover:text-text-primary'
            }`}
          >
            08 Scenario Lab 🧪
          </button>
          <button
            onClick={() => setActiveTab('simulation')}
            className={`pb-2.5 px-3 border-b-2 font-semibold transition-all whitespace-nowrap ${
              activeTab === 'simulation'
                ? 'border-accent text-accent'
                : 'border-transparent text-text-tertiary hover:text-text-primary'
            }`}
          >
            09 Candidate Simulation 🔬
          </button>
          <button
            onClick={() => setActiveTab('evidence')}
            className={`pb-2.5 px-3 border-b-2 font-semibold transition-all whitespace-nowrap ${
              activeTab === 'evidence'
                ? 'border-accent text-accent'
                : 'border-transparent text-text-tertiary hover:text-text-primary'
            }`}
          >
            10 Evidence & Disclaimers
          </button>
        </div>

        {/* TAB 1: HOLDINGS LOOK-THROUGH TABLE */}
        {activeTab === 'holdings' && (
          <div className="panel p-5 space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h3 className="text-[14px] font-semibold text-text-primary">Consolidated Security Look-Through</h3>
                <p className="font-mono text-[11px] text-text-tertiary">
                  Aggregated portfolio weight for each underlying security across all constituent funds.
                </p>
              </div>

              <div className="w-full sm:w-64">
                <input
                  type="text"
                  placeholder="Filter by security, ISIN, sector..."
                  value={securityFilter}
                  onChange={e => setSecurityFilter(e.target.value)}
                  className="field field-mono text-[11px] py-1 px-3 w-full"
                />
              </div>
            </div>

            <div className="overflow-x-auto rounded border border-border">
              <table className="w-full text-left font-mono text-[11px]">
                <thead className="bg-surface-raised text-text-muted text-[10px] border-b border-border">
                  <tr>
                    <th className="py-2.5 px-3">#</th>
                    <th className="py-2.5 px-3">Security Name</th>
                    <th className="py-2.5 px-3">ISIN</th>
                    <th className="py-2.5 px-3">Sector</th>
                    <th className="py-2.5 px-3">Cap Category</th>
                    <th className="py-2.5 px-3 text-center">Funds</th>
                    <th className="py-2.5 px-3 text-right">Consolidated Weight</th>
                    <th className="py-2.5 px-3 text-center">Drilldown</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border bg-surface">
                  {filteredSecurities.map((sec, idx) => {
                    const isExpanded = expandedSecurity === sec.canonicalName;
                    return (
                      <React.Fragment key={sec.canonicalName + idx}>
                        <tr
                          onClick={() => setExpandedSecurity(isExpanded ? null : sec.canonicalName)}
                          className="hover:bg-surface-raised/40 cursor-pointer"
                        >
                          <td className="py-2.5 px-3 text-text-tertiary">{idx + 1}</td>
                          <td className="py-2.5 px-3 font-sans font-medium text-text-primary flex items-center gap-1.5">
                            <span>{sec.canonicalName}</span>
                            {sec.fundCount >= 2 && (
                              <span className="status-badge state-candidate text-[8.5px] px-1 py-0">SHARED</span>
                            )}
                          </td>
                          <td className="py-2.5 px-3 text-text-tertiary">{sec.isin || '—'}</td>
                          <td className="py-2.5 px-3 text-text-secondary">{sec.sector}</td>
                          <td className="py-2.5 px-3">
                            <span className={`status-badge text-[9px] ${
                              sec.marketCapCategory === 'LARGE_CAP' ? 'state-verified' :
                              sec.marketCapCategory === 'MID_CAP' ? 'state-candidate' : 'state-unavailable'
                            }`}>
                              {sec.marketCapCategory}
                            </span>
                          </td>
                          <td className="py-2.5 px-3 text-center font-bold text-text-primary">
                            {sec.fundCount}
                          </td>
                          <td className="py-2.5 px-3 text-right font-bold text-accent">
                            {sec.consolidatedWeightPct.toFixed(2)}%
                          </td>
                          <td className="py-2.5 px-3 text-center text-text-tertiary text-[10px]">
                            {isExpanded ? '▲' : '▼'}
                          </td>
                        </tr>

                        {/* Expandable Contributing Funds Breakdown */}
                        {isExpanded && (
                          <tr className="bg-surface-raised/30">
                            <td colSpan={8} className="p-3">
                              <div className="panel-inset p-3 space-y-2">
                                <span className="text-[10px] font-semibold text-text-secondary uppercase tracking-wider block">
                                  Fund Contributions to {sec.canonicalName}
                                </span>
                                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
                                  {sec.contributions.map(c => (
                                    <div key={c.schemeOptionId} className="p-2 rounded bg-surface border border-border text-[11px] space-y-1">
                                      <span className="font-sans font-medium text-text-primary block truncate">
                                        {c.fundName}
                                      </span>
                                      <div className="flex justify-between text-text-tertiary text-[10px]">
                                        <span>Fund Port Share:</span>
                                        <span className="text-text-secondary font-bold">{c.fundWeightInPortfolioPct.toFixed(1)}%</span>
                                      </div>
                                      <div className="flex justify-between text-text-tertiary text-[10px]">
                                        <span>Weight in Fund:</span>
                                        <span className="text-text-secondary font-bold">{c.holdingWeightInFundPct.toFixed(2)}%</span>
                                      </div>
                                      <div className="flex justify-between border-t border-border/60 pt-1 text-accent font-semibold text-[10.5px]">
                                        <span>Effective Contribution:</span>
                                        <span>+{c.effectiveWeightContributionPct.toFixed(2)}%</span>
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
                  })}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* TAB 2: PAIRWISE OVERLAP MATRIX */}
        {activeTab === 'overlap' && (
          <div className="panel p-5 space-y-4">
            <div>
              <h3 className="text-[14px] font-semibold text-text-primary">Pairwise Fund Overlap Matrix</h3>
              <p className="font-mono text-[11px] text-text-tertiary">
                Deterministic security overlap between constituent mutual fund pairs: Overlap(A, B) = ∑ min(w_A, w_B).
              </p>
            </div>

            {lookThroughData?.pairwiseOverlapMatrix.length === 0 ? (
              <div className="p-6 text-center text-text-tertiary font-mono text-[12px]">
                Add at least two constituent funds to calculate pairwise overlap.
              </div>
            ) : (
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                {lookThroughData?.pairwiseOverlapMatrix.map((pair, idx) => (
                  <div key={idx} className="panel-inset p-4 space-y-3 bg-surface">
                    <div className="flex items-start justify-between gap-2 border-b border-border pb-2.5">
                      <div>
                        <div className="font-sans font-semibold text-text-primary text-[13px]">
                          {pair.fundAName}
                        </div>
                        <div className="font-sans text-[11px] text-text-tertiary">vs</div>
                        <div className="font-sans font-semibold text-text-primary text-[13px]">
                          {pair.fundBName}
                        </div>
                      </div>

                      <div className="text-right">
                        <span className="data-value-lg text-accent block font-mono font-bold">
                          {pair.overlapWeightPct.toFixed(2)}%
                        </span>
                        <span className={`status-badge text-[9px] ${
                          pair.comparabilityState === 'SYNCHRONIZED' ? 'state-verified' :
                          pair.comparabilityState === 'ACCEPTABLE_LAG' ? 'state-candidate' : 'state-unavailable'
                        }`}>
                          {pair.comparabilityState}
                        </span>
                      </div>
                    </div>

                    <div className="flex justify-between font-mono text-[10.5px] text-text-tertiary">
                      <span>Shared Securities: <strong className="text-text-primary">{pair.sharedSecurityCount}</strong></span>
                      <span>Observation Dates: <strong className="text-text-primary">{pair.observationDateA} / {pair.observationDateB}</strong></span>
                      {pair.temporalDeltaDays > 0 && (
                        <span>Lag: <strong className="text-amber-400">{pair.temporalDeltaDays} days</strong></span>
                      )}
                    </div>

                    {/* Top Shared Securities Table */}
                    {pair.topSharedSecurities.length > 0 && (
                      <div className="space-y-1.5 pt-1">
                        <span className="font-mono text-[10px] text-text-muted uppercase">Top Shared Holdings</span>
                        <div className="overflow-x-auto rounded border border-border/70">
                          <table className="w-full text-left font-mono text-[10px]">
                            <thead className="bg-surface-raised text-text-tertiary">
                              <tr>
                                <th className="py-1 px-2">Security</th>
                                <th className="py-1 px-2 text-right">In Fund A</th>
                                <th className="py-1 px-2 text-right">In Fund B</th>
                                <th className="py-1 px-2 text-right font-bold text-accent">Overlap</th>
                              </tr>
                            </thead>
                            <tbody className="divide-y divide-border/60">
                              {pair.topSharedSecurities.slice(0, 6).map((item, sIdx) => (
                                <tr key={sIdx}>
                                  <td className="py-1 px-2 font-sans text-text-secondary truncate max-w-[140px]">{item.securityName}</td>
                                  <td className="py-1 px-2 text-right text-text-tertiary">{item.weightInA.toFixed(2)}%</td>
                                  <td className="py-1 px-2 text-right text-text-tertiary">{item.weightInB.toFixed(2)}%</td>
                                  <td className="py-1 px-2 text-right font-semibold text-accent">{item.overlapWeightPct.toFixed(2)}%</td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* TAB 3: SECTOR & MARKET-CAP DISTRIBUTION */}
        {activeTab === 'sectors' && (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            {/* Sector Exposure Breakdown */}
            <div className="panel p-5 space-y-4">
              <div>
                <h3 className="text-[14px] font-semibold text-text-primary">Consolidated Macro-Sector Breakdown</h3>
                <p className="font-mono text-[11px] text-text-tertiary">
                  Exposure-weighted sum using official NSE macro-sector taxonomy.
                </p>
              </div>

              <div className="space-y-3">
                {lookThroughData?.consolidatedSectors.map(sec => (
                  <div key={sec.macroSector} className="space-y-1.5 p-2 rounded hover:bg-surface-raised/40 transition-colors">
                    <div className="flex justify-between font-mono text-[11px]">
                      <span className="text-text-primary font-medium">{sec.macroSector} ({sec.classifiedSecuritiesCount} stocks)</span>
                      <span className="font-bold text-accent">{sec.consolidatedWeightPct.toFixed(2)}%</span>
                    </div>
                    <div className="h-2 w-full bg-border rounded-full overflow-hidden">
                      <div
                        className="h-full bg-accent transition-all duration-300"
                        style={{ width: `${Math.min(sec.consolidatedWeightPct, 100)}%` }}
                      />
                    </div>
                    {/* Top Contributing Securities Drilldown */}
                    {sec.topSecurities && sec.topSecurities.length > 0 && (
                      <div className="flex flex-wrap items-center gap-1 pt-0.5">
                        <span className="font-mono text-[9px] text-text-tertiary mr-1">Top holdings:</span>
                        {sec.topSecurities.map((ts, idx) => (
                          <span
                            key={idx}
                            className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] bg-surface-raised border border-border/80 text-text-secondary"
                            title={`ISIN: ${ts.isin || 'N/A'} | Held in ${ts.fundCount} funds`}
                          >
                            <span className="font-sans font-medium text-text-primary truncate max-w-[120px]">{ts.canonicalName}</span>
                            <span className="text-accent font-semibold">{ts.consolidatedWeightPct.toFixed(2)}%</span>
                            {ts.fundCount > 1 && (
                              <span className="text-[9px] text-amber-400 font-mono">({ts.fundCount} funds)</span>
                            )}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>

            {/* Market-Cap Breakdown */}
            <div className="panel p-5 space-y-4">
              <div>
                <h3 className="text-[14px] font-semibold text-text-primary">Consolidated Market-Cap Breakdown</h3>
                <p className="font-mono text-[11px] text-text-tertiary">
                  Point-in-Time AMFI semi-annual ranking and SEBI market-cap categorization.
                </p>
              </div>

              <div className="space-y-3">
                {lookThroughData?.consolidatedMarketCap.map(m => (
                  <div key={m.category} className="space-y-1">
                    <div className="flex justify-between font-mono text-[11px]">
                      <span className="text-text-primary font-medium">
                        {m.category === 'LARGE_CAP' ? 'Large Cap (Ranks 1–100)' :
                         m.category === 'MID_CAP' ? 'Mid Cap (Ranks 101–250)' :
                         m.category === 'SMALL_CAP' ? 'Small Cap (Ranks 251+)' :
                         m.category === 'CASH_AND_EQUIVALENTS' ? 'Cash & Liquid Reserves' : 'Unclassified'}
                        {' '}({m.securitiesCount} items)
                      </span>
                      <span className="font-bold text-accent">{m.consolidatedWeightPct.toFixed(2)}%</span>
                    </div>
                    <div className="h-2 w-full bg-border rounded-full overflow-hidden">
                      <div
                        className={`h-full transition-all duration-300 ${
                          m.category === 'LARGE_CAP' ? 'bg-blue-500' :
                          m.category === 'MID_CAP' ? 'bg-emerald-500' :
                          m.category === 'SMALL_CAP' ? 'bg-amber-500' :
                          m.category === 'CASH_AND_EQUIVALENTS' ? 'bg-teal-400' : 'bg-gray-500'
                        }`}
                        style={{ width: `${Math.min(m.consolidatedWeightPct, 100)}%` }}
                      />
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* TAB 4: RISK & FACTOR DECOMPOSITION */}
        {activeTab === 'risk' && (
          <div className="space-y-4">
            {/* Risk Decomposition Header / Status */}
            <div className="panel p-5 space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border pb-3">
                <div>
                  <span className="font-mono text-[10px] uppercase tracking-wider text-accent">04 EMPIRICAL RISK DECOMPOSITION</span>
                  <h3 className="text-[14px] font-semibold text-text-primary">
                    Realized Portfolio Risk & Factor Exposure Profile
                  </h3>
                  <p className="font-mono text-[11px] text-text-tertiary mt-0.5">
                    Evaluated over {lookThroughData?.riskDecomposition?.alignedTradingDaysCount || 0} synchronized trading days
                    ({lookThroughData?.riskDecomposition?.returnStartDate || 'N/A'} to {lookThroughData?.riskDecomposition?.returnEndDate || 'N/A'})
                    against {lookThroughData?.riskDecomposition?.benchmarkName || 'NIFTY 500 TRI'}.
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <span className={`px-2 py-0.5 rounded text-[10px] font-mono font-bold ${
                    lookThroughData?.riskDecomposition?.evaluationStatus === 'COMPUTED_VALIDATED_INPUTS'
                      ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30'
                      : 'bg-amber-500/10 text-amber-400 border border-amber-500/30'
                  }`}>
                    {lookThroughData?.riskDecomposition?.evaluationStatus || 'PENDING'}
                  </span>
                  <span className="px-2 py-0.5 rounded text-[10px] font-mono bg-surface-raised border border-border text-text-secondary">
                    {lookThroughData?.riskDecomposition?.rebalancingConvention || 'DAILY_REBALANCED'}
                  </span>
                </div>
              </div>

              {/* Realized Risk vs Weighted Average KPI Banner */}
              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
                <div className="metric-tile">
                  <span className="eyebrow">Realized Portfolio Volatility</span>
                  <div className="metric-rule" />
                  <p className="data-value-lg text-accent">
                    {lookThroughData?.riskDecomposition?.portfolioRealizedVolatilityPct != null
                      ? `${lookThroughData.riskDecomposition.portfolioRealizedVolatilityPct.toFixed(2)}%`
                      : 'N/A'}
                  </p>
                  <p className="mt-auto pt-1 font-mono text-[10px] text-text-tertiary">Annualized sqrt(252)</p>
                </div>

                <div className="metric-tile">
                  <span className="eyebrow">Weighted Constituent Vol</span>
                  <div className="metric-rule" />
                  <p className="data-value-lg text-text-secondary">
                    {lookThroughData?.riskDecomposition?.weightedAverageFundVolatilityPct != null
                      ? `${lookThroughData.riskDecomposition.weightedAverageFundVolatilityPct.toFixed(2)}%`
                      : 'N/A'}
                  </p>
                  <p className="mt-auto pt-1 font-mono text-[10px] text-text-tertiary">∑ ( a_j × σ_j )</p>
                </div>

                <div className="metric-tile border-emerald-500/30 bg-emerald-500/5">
                  <span className="eyebrow text-emerald-400">Diversification Benefit</span>
                  <div className="metric-rule" />
                  <p className="data-value-lg text-emerald-400 font-bold">
                    {lookThroughData?.riskDecomposition?.diversificationBenefitPct != null
                      ? `+${lookThroughData.riskDecomposition.diversificationBenefitPct.toFixed(2)}%`
                      : 'N/A'}
                  </p>
                  <p className="mt-auto pt-1 font-mono text-[10px] text-emerald-300/70">σ_weighted - σ_portfolio</p>
                </div>

                <div className="metric-tile">
                  <span className="eyebrow">Portfolio Beta (OLS)</span>
                  <div className="metric-rule" />
                  <p className="data-value-lg">
                    {lookThroughData?.riskDecomposition?.portfolioBeta != null
                      ? lookThroughData.riskDecomposition.portfolioBeta.toFixed(2)
                      : 'N/A'}
                  </p>
                  <p className="mt-auto pt-1 font-mono text-[10px] text-text-tertiary">
                    vs {lookThroughData?.riskDecomposition?.benchmarkCode || 'NIFTY 500 TRI'}
                  </p>
                </div>

                <div className="metric-tile">
                  <span className="eyebrow">Portfolio Max Drawdown</span>
                  <div className="metric-rule" />
                  <p className="data-value-lg text-rose-400">
                    {lookThroughData?.riskDecomposition?.portfolioMaxDrawdownPct != null
                      ? `-${lookThroughData.riskDecomposition.portfolioMaxDrawdownPct.toFixed(2)}%`
                      : 'N/A'}
                  </p>
                  <p className="mt-auto pt-1 font-mono text-[10px] text-text-tertiary">
                    Benchmark: {lookThroughData?.riskDecomposition?.benchmarkMaxDrawdownPct != null ? `-${lookThroughData.riskDecomposition.benchmarkMaxDrawdownPct.toFixed(2)}%` : 'N/A'}
                  </p>
                </div>

                <div className="metric-tile">
                  <span className="eyebrow">Downside Semideviation</span>
                  <div className="metric-rule" />
                  <p className="data-value-lg text-amber-400">
                    {lookThroughData?.riskDecomposition?.portfolioDownsideDeviationPct != null
                      ? `${lookThroughData.riskDecomposition.portfolioDownsideDeviationPct.toFixed(2)}%`
                      : 'N/A'}
                  </p>
                  <p className="mt-auto pt-1 font-mono text-[10px] text-text-tertiary">Threshold MAR = 0%</p>
                </div>
              </div>

              {/* Factual Risk Observations */}
              {lookThroughData?.riskDecomposition?.riskObservations && lookThroughData.riskDecomposition.riskObservations.length > 0 && (
                <div className="p-3.5 rounded bg-surface-raised border border-border space-y-1 font-mono text-[11px]">
                  <span className="font-bold text-accent uppercase text-[10px] block">Verified Mathematical Risk Observations:</span>
                  <ul className="list-disc list-inside space-y-0.5 text-text-secondary">
                    {lookThroughData.riskDecomposition.riskObservations.map((obs, idx) => (
                      <li key={idx}>{obs}</li>
                    ))}
                  </ul>
                </div>
              )}
            </div>

            {/* Constituent Fund Risk Profiles Table & Pairwise Return Correlations */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
              {/* Constituent Fund Risk Profiles Table (2 cols) */}
              <div className="panel p-5 space-y-3 lg:col-span-2">
                <div>
                  <h4 className="text-[13px] font-semibold text-text-primary">Constituent Fund Risk Profiles</h4>
                  <p className="font-mono text-[11px] text-text-tertiary">
                    Individual risk characteristics over the synchronized historical return window.
                  </p>
                </div>

                <div className="overflow-x-auto rounded border border-border">
                  <table className="w-full text-left font-mono text-[11px]">
                    <thead className="bg-surface-raised text-text-tertiary text-[10px] border-b border-border">
                      <tr>
                        <th className="py-2 px-3">Fund Scheme</th>
                        <th className="py-2 px-3 text-right">Weight</th>
                        <th className="py-2 px-3 text-right">Ann. Vol</th>
                        <th className="py-2 px-3 text-right">Downside Dev</th>
                        <th className="py-2 px-3 text-right">Max DD</th>
                        <th className="py-2 px-3 text-right">Beta</th>
                        <th className="py-2 px-3 text-right">Days</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border">
                      {lookThroughData?.riskDecomposition?.constituentRiskProfiles?.map((c, idx) => (
                        <tr key={idx} className="hover:bg-surface-raised/40">
                          <td className="py-2 px-3 font-sans font-medium text-text-primary">
                            <div>{c.fundName}</div>
                            <span className="font-mono text-[9.5px] text-text-tertiary">AMFI: {c.amfiCode || 'N/A'}</span>
                          </td>
                          <td className="py-2 px-3 text-right font-bold text-text-primary">{c.allocationWeightPct.toFixed(1)}%</td>
                          <td className="py-2 px-3 text-right text-accent font-semibold">{c.annualizedVolatilityPct.toFixed(2)}%</td>
                          <td className="py-2 px-3 text-right text-text-secondary">{c.downsideDeviationPct.toFixed(2)}%</td>
                          <td className="py-2 px-3 text-right text-rose-400">-{c.maxDrawdownPct.toFixed(2)}%</td>
                          <td className="py-2 px-3 text-right text-text-primary">{c.betaToBenchmark != null ? c.betaToBenchmark.toFixed(2) : 'N/A'}</td>
                          <td className="py-2 px-3 text-right text-text-tertiary">{c.returnObservationsCount}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* Pairwise Return Correlations Matrix (1 col) */}
              <div className="panel p-5 space-y-3">
                <div>
                  <h4 className="text-[13px] font-semibold text-text-primary">Pairwise Return Correlation (ρ)</h4>
                  <p className="font-mono text-[11px] text-text-tertiary">
                    Synchronized daily return correlation matrix.
                  </p>
                </div>

                {lookThroughData?.riskDecomposition?.pairwiseCorrelations && lookThroughData.riskDecomposition.pairwiseCorrelations.length > 0 ? (
                  <div className="space-y-2.5">
                    {lookThroughData.riskDecomposition.pairwiseCorrelations.map((pair, pIdx) => {
                      const rho = pair.correlationCoefficient;
                      return (
                        <div key={pIdx} className="p-3 rounded border border-border bg-surface-raised/60 space-y-1.5 font-mono text-[11px]">
                          <div className="flex justify-between items-center text-[10px] text-text-tertiary">
                            <span className="truncate max-w-[110px]" title={pair.fundAName}>{pair.fundAName}</span>
                            <span>↔</span>
                            <span className="truncate max-w-[110px]" title={pair.fundBName}>{pair.fundBName}</span>
                          </div>
                          <div className="flex justify-between items-center pt-0.5">
                            <span className="text-[10px] text-text-muted">Correlation (ρ):</span>
                            <span className={`text-[13px] font-bold ${
                              rho < 0.6 ? 'text-emerald-400' :
                              rho < 0.85 ? 'text-accent' : 'text-amber-400'
                            }`}>
                              {rho.toFixed(4)}
                            </span>
                          </div>
                          <div className="h-1.5 w-full bg-border rounded-full overflow-hidden">
                            <div
                              className={`h-full ${
                                rho < 0.6 ? 'bg-emerald-400' :
                                rho < 0.85 ? 'bg-accent' : 'bg-amber-400'
                              }`}
                              style={{ width: `${Math.max(0, Math.min(rho * 100, 100))}%` }}
                            />
                          </div>
                          <p className="text-[9.5px] text-text-tertiary text-right">{pair.commonTradingDays} common trading days</p>
                        </div>
                      );
                    })}
                  </div>
                ) : (
                  <p className="font-mono text-[11px] text-text-tertiary italic">Need at least 2 constituent funds to compute pairwise correlations.</p>
                )}
              </div>
            </div>

            {/* Factor Exposure Inventory & Evidence Gates */}
            <div className="panel p-5 space-y-3">
              <div>
                <h4 className="text-[13px] font-semibold text-text-primary">Factor Exposure Inventory & Epistemic Gates</h4>
                <p className="font-mono text-[11px] text-text-tertiary">
                  Rigorous distinction between holdings-based proxies, return-based empirical exposures, and unvalidated/gated factor models.
                </p>
              </div>

              <div className="overflow-x-auto rounded border border-border">
                <table className="w-full text-left font-mono text-[11px]">
                  <thead className="bg-surface-raised text-text-tertiary text-[10px] border-b border-border">
                    <tr>
                      <th className="py-2.5 px-3">Factor</th>
                      <th className="py-2.5 px-3">Category</th>
                      <th className="py-2.5 px-3">Exposure / Observation</th>
                      <th className="py-2.5 px-3">Status</th>
                      <th className="py-2.5 px-3">Required Inputs & Epistemic Evidence</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {lookThroughData?.riskDecomposition?.factorExposures?.map((f, idx) => (
                      <tr key={idx} className="hover:bg-surface-raised/40">
                        <td className="py-2.5 px-3 font-sans">
                          <span className="font-semibold text-text-primary block">{f.factorName}</span>
                          <span className="font-mono text-[10px] text-text-tertiary">{f.economicDefinition}</span>
                        </td>
                        <td className="py-2.5 px-3">
                          <span className="px-1.5 py-0.5 rounded text-[10px] bg-surface-raised border border-border text-text-secondary">
                            {f.category}
                          </span>
                        </td>
                        <td className="py-2.5 px-3 font-bold text-accent">{f.exposureValue}</td>
                        <td className="py-2.5 px-3">
                          <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                            f.status === 'VALIDATED_PROXY'
                              ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30'
                              : f.status === 'EMPIRICALLY_COMPUTED'
                              ? 'bg-blue-500/10 text-blue-400 border border-blue-500/30'
                              : 'bg-amber-500/10 text-amber-400 border border-amber-500/30'
                          }`}>
                            {f.status}
                          </span>
                        </td>
                        <td className="py-2.5 px-3 text-[10px] text-text-secondary max-w-xs">
                          <div><strong className="text-text-primary">Inputs:</strong> {f.requiredInputs}</div>
                          <div className="mt-0.5 text-text-tertiary"><strong className="text-text-muted">Evidence:</strong> {f.evidenceNotes}</div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Methodological Limitations & Assumptions */}
            {lookThroughData?.riskDecomposition?.dataLimitations && lookThroughData.riskDecomposition.dataLimitations.length > 0 && (
              <div className="panel p-4 space-y-2 bg-surface-raised border-border">
                <span className="font-mono text-[11px] font-bold text-amber-400 uppercase tracking-wider block">
                  Methodological Limitations & Governance Guardrails (AGENTS.md):
                </span>
                <ul className="list-disc list-inside space-y-1 font-mono text-[11px] text-text-secondary">
                  {lookThroughData.riskDecomposition.dataLimitations.map((lim, idx) => (
                    <li key={idx}>{lim}</li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}

        {/* TAB 5: PORTFOLIO RISK ATTRIBUTION (PHASE 2O) */}
        {activeTab === 'attribution' && (
          <RiskAttributionSection
            selectedFunds={selectedFunds}
            availableSchemes={availableSchemes}
            asOfDate={asOfDate}
          />
        )}

        {/* TAB 6: RISK DRIVERS & CONCENTRATION INVESTIGATION (PHASE 2P) */}
        {activeTab === 'drivers' && (
          <RiskDriversSection
            selectedFunds={selectedFunds}
            availableSchemes={availableSchemes}
            asOfDate={asOfDate}
          />
        )}

        {/* TAB 7: HISTORICAL MARKET-REGIME REPLAY & PORTFOLIO STRESS TESTING */}
        {activeTab === 'replay' && (
          <div className="space-y-6">
            {/* 1. Mandatory Epistemic Disclaimer Banner */}
            <div className="panel p-4 bg-amber-500/10 border-amber-500/30 rounded-lg space-y-1.5">
              <div className="flex items-center gap-2">
                <span className="text-amber-400 font-bold text-[13px]">⚠️ HYPOTHETICAL HISTORICAL RECONSTRUCTION — CONSTANT DAILY REBALANCING</span>
                <span className="status-badge state-unverified text-[9px] bg-amber-500/20 text-amber-300 border border-amber-500/40">
                  METHODOLOGY: HYPOTHETICAL_RECONSTRUCTION
                </span>
              </div>
              <p className="font-sans text-[12px] text-text-secondary leading-relaxed">
                This stress replay synthesizes how a portfolio with constant fixed target allocations (<code className="font-mono text-accent">R_port,t = ∑ a_j · R_j,t</code>) would have performed during historical adverse market episodes. It does <strong className="text-text-primary">not</strong> reflect an actual investor’s realized historical portfolio or execution history. Daily rebalancing assumes frictionless zero-cost reweighting. Holdings exposure context is evaluated against the selected portfolio snapshot as of <strong className="text-text-primary font-mono">{asOfDate}</strong>.
              </p>
            </div>

            {/* 2. Episode Selection & Date Range Configuration */}
            <div className="panel p-5 space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <span className="font-mono text-[10px] uppercase tracking-wider text-accent">REGIME REPLAY CONTROLS</span>
                  <h3 className="text-[14px] font-semibold text-text-primary">
                    Historical Stress Episode Selection
                  </h3>
                  <p className="font-mono text-[11px] text-text-tertiary">
                    Replay verified adverse market regimes from direct AMFI NAVs and official NIFTY 500 TRI series.
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => setIsCustomRange(false)}
                    className={`btn text-[11px] py-1 px-3 ${!isCustomRange ? 'btn-primary' : 'btn-secondary'}`}
                  >
                    Preset Market Episodes
                  </button>
                  <button
                    onClick={() => setIsCustomRange(true)}
                    className={`btn text-[11px] py-1 px-3 ${isCustomRange ? 'btn-primary' : 'btn-secondary'}`}
                  >
                    Custom Date Window
                  </button>
                </div>
              </div>

              {!isCustomRange ? (
                /* Preset Episodes Grid */
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                  {episodes.map(ep => {
                    const isSelected = selectedEpisodeId === ep.episodeId;
                    return (
                      <div
                        key={ep.episodeId}
                        onClick={() => {
                          if (ep.isAvailable) {
                            setSelectedEpisodeId(ep.episodeId);
                          }
                        }}
                        className={`p-3.5 rounded-lg border transition-all text-left flex flex-col justify-between ${
                          isSelected
                            ? 'border-accent bg-accent/5 ring-1 ring-accent'
                            : ep.isAvailable
                            ? 'border-border bg-surface-raised/40 hover:border-text-tertiary cursor-pointer'
                            : 'border-border/60 bg-surface-raised/10 opacity-60 cursor-not-allowed'
                        }`}
                      >
                        <div className="space-y-1.5">
                          <div className="flex items-center justify-between gap-1">
                            <span className="font-mono text-[10px] uppercase tracking-wider text-text-tertiary">
                              {ep.regimeType}
                            </span>
                            {ep.isAvailable ? (
                              <span className="px-1.5 py-0.5 rounded text-[9px] font-mono font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/30">
                                VERIFIED DATA
                              </span>
                            ) : (
                              <span className="px-1.5 py-0.5 rounded text-[9px] font-mono font-bold bg-rose-500/10 text-rose-400 border border-rose-500/30">
                                DATA UNAVAILABLE
                              </span>
                            )}
                          </div>
                          <h4 className="font-semibold text-[13px] text-text-primary leading-snug">
                            {ep.title}
                          </h4>
                          <div className="font-mono text-[11px] text-text-tertiary">
                            <span>{ep.startDate}</span> <span className="text-text-muted">→</span> <span>{ep.endDate}</span>
                          </div>
                          <p className="font-sans text-[11px] text-text-secondary leading-normal line-clamp-3">
                            {ep.description}
                          </p>
                        </div>

                        <div className="mt-3 pt-2 border-t border-border/60 flex items-center justify-between font-mono text-[11px]">
                          {ep.benchmarkTotalReturnPct !== null ? (
                            <span className="text-text-tertiary">
                              <span className="text-text-muted text-[10px]">{ep.benchmarkCode || 'NIFTY_500_TRI'}: </span>
                              <strong className={ep.benchmarkTotalReturnPct < 0 ? 'text-rose-400' : 'text-emerald-400'}>
                                {ep.benchmarkTotalReturnPct > 0 ? '+' : ''}{ep.benchmarkTotalReturnPct.toFixed(2)}%
                              </strong>
                            </span>
                          ) : (
                            <span className="text-text-tertiary text-[10px]">{ep.benchmarkCode || 'Benchmark'}: N/A</span>
                          )}

                          {ep.isAvailable ? (
                            <span className="text-[10px] font-semibold text-accent">
                              {isSelected ? '● Selected' : 'Select'}
                            </span>
                          ) : (
                            <span className="text-[10px] text-rose-400 font-medium" title={ep.unavailabilityReason || ''}>
                              Incomplete History
                            </span>
                          )}
                        </div>

                        {!ep.isAvailable && ep.unavailabilityReason && (
                          <div className="mt-2 text-[10px] font-mono text-rose-300 bg-rose-500/10 p-1.5 rounded border border-rose-500/20">
                            {ep.unavailabilityReason}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              ) : (
                /* Custom Date Range Controls */
                <div className="panel-inset p-4 rounded-lg space-y-3">
                  <div className="flex flex-wrap items-center gap-4">
                    <div>
                      <label className="field-label text-[11px]">Start Date (Trading Day)</label>
                      <input
                        type="date"
                        value={customStartDate}
                        onChange={e => setCustomStartDate(e.target.value)}
                        className="field text-[11px] py-1 px-3 h-8"
                      />
                    </div>
                    <div>
                      <label className="field-label text-[11px]">End Date (Trading Day)</label>
                      <input
                        type="date"
                        value={customEndDate}
                        onChange={e => setCustomEndDate(e.target.value)}
                        className="field text-[11px] py-1 px-3 h-8"
                      />
                    </div>
                    <div className="text-[11px] font-mono text-text-tertiary pt-4">
                      <span>Historical Data Boundary: </span>
                      <strong className="text-text-secondary">2021-01-15 to 2026-10-01</strong>
                    </div>
                  </div>
                </div>
              )}

              {/* Execution Button & Error Status */}
              <div className="flex items-center justify-between pt-2">
                <button
                  onClick={handleRunStressReplay}
                  disabled={loadingReplay || totalAllocation === 0}
                  className="btn btn-primary text-[12px] py-2 px-4 flex items-center gap-2"
                >
                  {loadingReplay ? (
                    <>
                      <div className="w-3.5 h-3.5 border-2 border-white/20 border-t-white rounded-full animate-spin" />
                      <span>Synthesizing Historical Stress Path...</span>
                    </>
                  ) : (
                    <>
                      <span>⚡ Run Historical Replay</span>
                    </>
                  )}
                </button>

                {replayError && (
                  <div className="text-[11px] font-mono text-rose-400 bg-rose-500/10 px-3 py-1.5 rounded border border-rose-500/20">
                    {replayError}
                  </div>
                )}
              </div>
            </div>

            {/* 3. Replay Results (Rendered when replayData is available) */}
            {replayData && (
              <div className="space-y-6">
                {/* Executive Summary Metrics Grid */}
                <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-7 gap-3">
                  {/* Metric 1: Portfolio Cumulative Return */}
                  <div className="panel p-3.5 space-y-1">
                    <span className="font-mono text-[10px] text-text-tertiary uppercase block">
                      Portfolio Return
                    </span>
                    <div className={`text-[17px] font-bold font-mono ${
                      replayData.portfolioCumulativeReturnPct < 0 ? 'text-rose-400' : 'text-emerald-400'
                    }`}>
                      {replayData.portfolioCumulativeReturnPct > 0 ? '+' : ''}
                      {replayData.portfolioCumulativeReturnPct.toFixed(2)}%
                    </div>
                    <span className="font-mono text-[9px] text-text-muted block">
                      Constant Daily Rebal
                    </span>
                  </div>

                  {/* Metric 2: Benchmark Return & Active Return */}
                  <div className="panel p-3.5 space-y-1">
                    <div className="flex items-center justify-between">
                      <span className="font-mono text-[10px] text-text-tertiary uppercase block">
                        Benchmark Return
                      </span>
                      <span className="font-mono text-[9px] px-1.5 py-0.5 rounded bg-surface-raised border border-border text-accent font-semibold" title={replayData.benchmarkName}>
                        {replayData.benchmarkCode || 'NIFTY_500_TRI'}
                      </span>
                    </div>
                    <div className={`text-[17px] font-bold font-mono ${
                      replayData.benchmarkCumulativeReturnPct < 0 ? 'text-rose-400' : 'text-emerald-400'
                    }`}>
                      {replayData.benchmarkCumulativeReturnPct > 0 ? '+' : ''}
                      {replayData.benchmarkCumulativeReturnPct.toFixed(2)}%
                    </div>
                    <span className={`font-mono text-[10px] font-semibold block ${
                      replayData.activeReturnPct >= 0 ? 'text-emerald-400' : 'text-rose-400'
                    }`}>
                      Active Return: {replayData.activeReturnPct >= 0 ? '+' : ''}{replayData.activeReturnPct.toFixed(2)}%
                    </span>
                  </div>

                  {/* Metric 3: Max Drawdown */}
                  <div className="panel p-3.5 space-y-1">
                    <span className="font-mono text-[10px] text-text-tertiary uppercase block">
                      Max Drawdown
                    </span>
                    <div className="text-[17px] font-bold font-mono text-rose-400">
                      {replayData.portfolioMaxDrawdownPct.toFixed(2)}%
                    </div>
                    <span className="font-mono text-[9px] text-text-muted block truncate" title={`Peak: ${replayData.peakDate || 'N/A'} → Trough: ${replayData.troughDate || 'N/A'}`}>
                      Trough: {replayData.troughDate || 'N/A'}
                    </span>
                  </div>

                  {/* Metric 4: Worst Daily Return */}
                  <div className="panel p-3.5 space-y-1">
                    <span className="font-mono text-[10px] text-text-tertiary uppercase block">
                      Worst Single Day
                    </span>
                    <div className="text-[17px] font-bold font-mono text-rose-400">
                      {replayData.worstDailyReturnPct.toFixed(2)}%
                    </div>
                    <span className="font-mono text-[9px] text-text-muted block truncate">
                      {replayData.worstDailyReturnDate || 'N/A'}
                    </span>
                  </div>

                  {/* Metric 5: Annualized Realized Volatility */}
                  <div className="panel p-3.5 space-y-1">
                    <span className="font-mono text-[10px] text-text-tertiary uppercase block">
                      Realized Volatility
                    </span>
                    <div className="text-[17px] font-bold font-mono text-text-primary">
                      {replayData.portfolioAnnualizedVolatilityPct !== null
                        ? `${replayData.portfolioAnnualizedVolatilityPct.toFixed(2)}%`
                        : 'N/A'}
                    </div>
                    <span className="font-mono text-[9px] text-text-muted block">
                      Ann. (√252)
                    </span>
                  </div>

                  {/* Metric 6: Downside Semideviation */}
                  <div className="panel p-3.5 space-y-1">
                    <span className="font-mono text-[10px] text-text-tertiary uppercase block">
                      Downside Semidev
                    </span>
                    <div className="text-[17px] font-bold font-mono text-text-primary">
                      {replayData.portfolioDownsideDeviationPct !== null
                        ? `${replayData.portfolioDownsideDeviationPct.toFixed(2)}%`
                        : 'N/A'}
                    </div>
                    <span className="font-mono text-[9px] text-text-muted block">
                      MAR = 0.0%
                    </span>
                  </div>

                  {/* Metric 7: Aligned Trading Days */}
                  <div className="panel p-3.5 space-y-1">
                    <span className="font-mono text-[10px] text-text-tertiary uppercase block">
                      Aligned Trading Days
                    </span>
                    <div className="text-[17px] font-bold font-mono text-accent">
                      {replayData.alignedTradingDaysCount}
                    </div>
                    <span className="font-mono text-[9px] text-text-muted block truncate">
                      {replayData.startDate} → {replayData.endDate}
                    </span>
                  </div>
                </div>

                {/* Trajectory Realized Loss Path */}
                <div className="panel p-5 space-y-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div>
                      <h4 className="text-[13px] font-semibold text-text-primary">
                        Realized Stress Trajectory Path
                      </h4>
                      <p className="font-mono text-[11px] text-text-tertiary">
                        Daily portfolio and benchmark cumulative return progression and drawdown depth.
                      </p>
                    </div>
                    <div className="flex items-center gap-2">
                      <span className="font-mono text-[10px] text-text-tertiary">View:</span>
                      <button
                        onClick={() => setTrajectoryFilter('milestones')}
                        className={`px-2.5 py-1 text-[10px] font-mono rounded ${
                          trajectoryFilter === 'milestones'
                            ? 'bg-accent text-background font-bold'
                            : 'bg-surface-raised text-text-secondary hover:text-text-primary'
                        }`}
                      >
                        Key Milestones
                      </button>
                      <button
                        onClick={() => setTrajectoryFilter('all')}
                        className={`px-2.5 py-1 text-[10px] font-mono rounded ${
                          trajectoryFilter === 'all'
                            ? 'bg-accent text-background font-bold'
                            : 'bg-surface-raised text-text-secondary hover:text-text-primary'
                        }`}
                      >
                        All Trading Days ({replayData.trajectory.length})
                      </button>
                    </div>
                  </div>

                  <div className="overflow-x-auto max-h-72 border border-border/80 rounded">
                    <table className="w-full text-[11px] font-mono border-collapse">
                      <thead className="bg-surface-raised sticky top-0 text-text-tertiary border-b border-border">
                        <tr>
                          <th className="py-2 px-3 text-left">Date</th>
                          <th className="py-2 px-3 text-right">Portfolio Cum. Return</th>
                          <th className="py-2 px-3 text-right">{replayData.benchmarkCode || 'Benchmark'} Cum. Return</th>
                          <th className="py-2 px-3 text-right">Active Return</th>
                          <th className="py-2 px-3 text-right">Portfolio Drawdown</th>
                          <th className="py-2 px-3 text-right">{replayData.benchmarkCode || 'Benchmark'} Drawdown</th>
                          <th className="py-2 px-3 text-center">Milestone Status</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-border/60">
                        {replayData.trajectory
                          .filter(pt => {
                            if (trajectoryFilter === 'all') return true;
                            // Milestones: start, end, peak, trough, worst day, recovery
                            return (
                              pt.date === replayData.startDate ||
                              pt.date === replayData.endDate ||
                              pt.date === replayData.peakDate ||
                              pt.date === replayData.troughDate ||
                              pt.date === replayData.worstDailyReturnDate ||
                              (replayData.recoveryDate && pt.date === replayData.recoveryDate)
                            );
                          })
                          .map((pt, idx) => {
                            const isPeak = pt.date === replayData.peakDate;
                            const isTrough = pt.date === replayData.troughDate;
                            const isWorst = pt.date === replayData.worstDailyReturnDate;
                            const isStart = pt.date === replayData.startDate;
                            const isEnd = pt.date === replayData.endDate;

                            return (
                              <tr key={idx} className="hover:bg-surface-raised/40 transition-colors">
                                <td className="py-1.5 px-3 font-semibold text-text-primary">{pt.date}</td>
                                <td className={`py-1.5 px-3 text-right font-bold ${
                                  pt.portfolioCumulativeReturnPct < 0 ? 'text-rose-400' : 'text-emerald-400'
                                }`}>
                                  {pt.portfolioCumulativeReturnPct > 0 ? '+' : ''}
                                  {pt.portfolioCumulativeReturnPct.toFixed(2)}%
                                </td>
                                <td className={`py-1.5 px-3 text-right ${
                                  pt.benchmarkCumulativeReturnPct < 0 ? 'text-rose-400' : 'text-emerald-400'
                                }`}>
                                  {pt.benchmarkCumulativeReturnPct > 0 ? '+' : ''}
                                  {pt.benchmarkCumulativeReturnPct.toFixed(2)}%
                                </td>
                                <td className={`py-1.5 px-3 text-right font-medium ${
                                  pt.activeReturnPct >= 0 ? 'text-emerald-400' : 'text-rose-400'
                                }`}>
                                  {pt.activeReturnPct >= 0 ? '+' : ''}
                                  {pt.activeReturnPct.toFixed(2)}%
                                </td>
                                <td className="py-1.5 px-3 text-right text-rose-400 font-medium">
                                  {pt.portfolioDrawdownPct.toFixed(2)}%
                                </td>
                                <td className="py-1.5 px-3 text-right text-text-tertiary">
                                  {pt.benchmarkDrawdownPct.toFixed(2)}%
                                </td>
                                <td className="py-1.5 px-3 text-center">
                                  {isTrough ? (
                                    <span className="px-1.5 py-0.5 rounded text-[9px] font-bold bg-rose-500/20 text-rose-300 border border-rose-500/40">
                                      MAX TROUGH
                                    </span>
                                  ) : isPeak ? (
                                    <span className="px-1.5 py-0.5 rounded text-[9px] font-bold bg-emerald-500/20 text-emerald-300 border border-emerald-500/40">
                                      PEAK
                                    </span>
                                  ) : isWorst ? (
                                    <span className="px-1.5 py-0.5 rounded text-[9px] font-bold bg-amber-500/20 text-amber-300 border border-amber-500/40">
                                      WORST DAY
                                    </span>
                                  ) : isStart ? (
                                    <span className="px-1.5 py-0.5 rounded text-[9px] text-text-tertiary bg-surface-raised border border-border">
                                      START
                                    </span>
                                  ) : isEnd ? (
                                    <span className="px-1.5 py-0.5 rounded text-[9px] text-text-secondary bg-surface-raised border border-border">
                                      END
                                    </span>
                                  ) : (
                                    <span className="text-text-muted text-[10px]">—</span>
                                  )}
                                </td>
                              </tr>
                            );
                          })}
                      </tbody>
                    </table>
                  </div>
                </div>

                {/* Constituent Fund Return & Loss Contribution Decomposition */}
                <div className="panel p-5 space-y-4">
                  <div>
                    <h4 className="text-[13px] font-semibold text-text-primary">
                      Constituent Return & Loss Attribution Decomposition
                    </h4>
                    <p className="font-mono text-[11px] text-text-tertiary">
                      Multi-period <strong className="text-accent">Carino logarithmic linking</strong> (<code className="text-accent">C_j = Σ (w_j · R_j,t) · (k_t / K)</code>) exactly reconciles daily rebalanced constituent contributions with portfolio return (Attribution Residual: <span className="text-emerald-400 font-bold">{replayData.attributionResidualPct.toFixed(2)}%</span>). Standalone buy-and-hold linear contributions (<code className="text-text-secondary">w_j · R_j,cum</code>) diverge by the rebalancing interaction residual (<span className="text-accent font-semibold">{replayData.compoundingResidualPct > 0 ? '+' : ''}{replayData.compoundingResidualPct.toFixed(2)}%</span>).
                    </p>
                  </div>

                  <div className="overflow-x-auto border border-border/80 rounded">
                    <table className="w-full text-[11px] font-mono border-collapse">
                      <thead className="bg-surface-raised text-text-tertiary border-b border-border">
                        <tr>
                          <th className="py-2.5 px-3 text-left font-sans">Constituent Mutual Fund</th>
                          <th className="py-2.5 px-3 text-right">Target Weight</th>
                          <th className="py-2.5 px-3 text-right">Standalone Return</th>
                          <th className="py-2.5 px-3 text-right text-text-tertiary">Linear (w_j · R_j)</th>
                          <th className="py-2.5 px-3 text-right font-bold text-accent">Carino Contribution (C_j)</th>
                          <th className="py-2.5 px-3 text-right">Max Drawdown</th>
                          <th className="py-2.5 px-3 text-right">Worst Single Day</th>
                          <th className="py-2.5 px-3 text-right">Realized Volatility</th>
                          <th className="py-2.5 px-3 text-center">Observations</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-border/60">
                        {replayData.constituentResults.map(c => (
                          <tr key={c.schemeOptionId} className="hover:bg-surface-raised/40 transition-colors">
                            <td className="py-2 px-3 font-sans">
                              <span className="font-semibold text-text-primary block">{c.fundName}</span>
                              <span className="font-mono text-[10px] text-text-tertiary">AMFI: {c.amfiCode || 'N/A'} | ID: {c.schemeOptionId}</span>
                            </td>
                            <td className="py-2 px-3 text-right font-medium text-text-secondary">
                              {c.allocationWeightPct.toFixed(2)}%
                            </td>
                            <td className={`py-2 px-3 text-right font-semibold ${
                              c.cumulativeReturnPct < 0 ? 'text-rose-400' : 'text-emerald-400'
                            }`}>
                              {c.cumulativeReturnPct > 0 ? '+' : ''}
                              {c.cumulativeReturnPct.toFixed(2)}%
                            </td>
                            <td className={`py-2 px-3 text-right text-text-secondary ${
                              c.standaloneContributionPct < 0 ? 'text-rose-400/80' : 'text-emerald-400/80'
                            }`}>
                              {c.standaloneContributionPct > 0 ? '+' : ''}
                              {c.standaloneContributionPct.toFixed(2)}%
                            </td>
                            <td className={`py-2 px-3 text-right font-bold text-[12px] ${
                              c.contributionPct < 0 ? 'text-rose-400' : 'text-emerald-400'
                            }`}>
                              {c.contributionPct > 0 ? '+' : ''}
                              {c.contributionPct.toFixed(2)}%
                            </td>
                            <td className="py-2 px-3 text-right text-rose-400">
                              {c.maxDrawdownPct.toFixed(2)}%
                            </td>
                            <td className="py-2 px-3 text-right text-rose-400">
                              {c.worstDailyReturnPct.toFixed(2)}%
                            </td>
                            <td className="py-2 px-3 text-right text-text-secondary">
                              {c.annualizedVolatilityPct !== null ? `${c.annualizedVolatilityPct.toFixed(2)}%` : 'N/A'}
                            </td>
                            <td className="py-2 px-3 text-center text-text-tertiary">
                              {c.observationCount}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                      <tfoot className="bg-surface-raised font-bold border-t-2 border-border text-text-primary">
                        <tr>
                          <td className="py-2.5 px-3 font-sans">Portfolio Reconciled Total</td>
                          <td className="py-2.5 px-3 text-right text-accent">
                            {replayData.constituentResults.reduce((s, c) => s + c.allocationWeightPct, 0).toFixed(2)}%
                          </td>
                          <td className="py-2.5 px-3 text-right">—</td>
                          <td className={`py-2.5 px-3 text-right text-text-secondary ${
                            replayData.standaloneSumReturnPct < 0 ? 'text-rose-400/80' : 'text-emerald-400/80'
                          }`}>
                            {replayData.standaloneSumReturnPct > 0 ? '+' : ''}
                            {replayData.standaloneSumReturnPct.toFixed(2)}%
                          </td>
                          <td className={`py-2.5 px-3 text-right text-[12px] ${
                            replayData.portfolioCumulativeReturnPct < 0 ? 'text-rose-400' : 'text-emerald-400'
                          }`}>
                            {replayData.portfolioCumulativeReturnPct > 0 ? '+' : ''}
                            {replayData.portfolioCumulativeReturnPct.toFixed(2)}%
                          </td>
                          <td className="py-2.5 px-3 text-right text-rose-400">
                            {replayData.portfolioMaxDrawdownPct.toFixed(2)}%
                          </td>
                          <td className="py-2.5 px-3 text-right text-rose-400">
                            {replayData.worstDailyReturnPct.toFixed(2)}%
                          </td>
                          <td className="py-2.5 px-3 text-right">
                            {replayData.portfolioAnnualizedVolatilityPct !== null ? `${replayData.portfolioAnnualizedVolatilityPct.toFixed(2)}%` : 'N/A'}
                          </td>
                          <td className="py-2.5 px-3 text-center text-text-secondary">
                            {replayData.alignedTradingDaysCount}
                          </td>
                        </tr>
                      </tfoot>
                    </table>
                  </div>

                  {/* Mathematical Attribution & Compounding Rebalancing Reconciliation Card */}
                  <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 pt-1 text-[11px] font-mono">
                    <div className="panel-inset p-3 rounded space-y-0.5">
                      <span className="text-text-tertiary block text-[10px] uppercase font-semibold">Compounded Portfolio Return</span>
                      <span className={`text-[15px] font-bold block ${
                        replayData.portfolioCumulativeReturnPct < 0 ? 'text-rose-400' : 'text-emerald-400'
                      }`}>
                        {replayData.portfolioCumulativeReturnPct > 0 ? '+' : ''}{replayData.portfolioCumulativeReturnPct.toFixed(2)}%
                      </span>
                      <span className="text-text-muted block text-[9px]">Geometric daily rebalancing (R_port)</span>
                    </div>

                    <div className="panel-inset p-3 rounded space-y-0.5">
                      <span className="text-text-tertiary block text-[10px] uppercase font-semibold">Linear Buy-and-Hold Sum</span>
                      <span className={`text-[15px] font-bold block ${
                        replayData.standaloneSumReturnPct < 0 ? 'text-rose-400' : 'text-emerald-400'
                      }`}>
                        {replayData.standaloneSumReturnPct > 0 ? '+' : ''}{replayData.standaloneSumReturnPct.toFixed(2)}%
                      </span>
                      <span className="text-text-muted block text-[9px]">Σ (weight × constituent return)</span>
                    </div>

                    <div className="panel-inset p-3 rounded space-y-0.5">
                      <span className="text-text-tertiary block text-[10px] uppercase font-semibold">Rebalancing Interaction Effect</span>
                      <span className={`text-[15px] font-bold block ${
                        replayData.compoundingResidualPct < 0 ? 'text-rose-400' : 'text-emerald-400'
                      }`}>
                        {replayData.compoundingResidualPct > 0 ? '+' : ''}{replayData.compoundingResidualPct.toFixed(2)}%
                      </span>
                      <span className="text-text-muted block text-[9px]">Δ_rebal = R_port - Σ (w_j · R_j,cum)</span>
                    </div>

                    <div className="panel-inset p-3 rounded space-y-0.5">
                      <span className="text-text-tertiary block text-[10px] uppercase font-semibold">Carino Reconciliation Residual</span>
                      <span className="text-[15px] font-bold text-emerald-400 block">
                        {replayData.attributionResidualPct.toFixed(2)}%
                      </span>
                      <span className="text-text-muted block text-[9px]">Σ C_j - R_port (Exact 0.00% Match)</span>
                    </div>
                  </div>
                </div>

                {/* Stress Exposure Context & Temporal Mismatch Warning */}
                <div className="panel p-5 space-y-4">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div>
                      <span className="font-mono text-[10px] uppercase tracking-wider text-accent">
                        HOLDINGS EXPOSURE SNAPSHOT
                      </span>
                      <h4 className="text-[13px] font-semibold text-text-primary">
                        Portfolio Exposure Context as of {replayData.exposureContext.observationDate || asOfDate}
                      </h4>
                    </div>

                    <div className="flex items-center gap-2">
                      {replayData.exposureContext.comparabilityState === 'SIGNIFICANT_TEMPORAL_MISMATCH' ? (
                        <span className="px-2 py-0.5 rounded text-[10px] font-mono font-bold bg-amber-500/20 text-amber-300 border border-amber-500/40">
                          ⚠️ SIGNIFICANT_TEMPORAL_MISMATCH ({replayData.exposureContext.daysMismatchFromEpisodeEnd} DAYS)
                        </span>
                      ) : (
                        <span className="px-2 py-0.5 rounded text-[10px] font-mono font-bold bg-emerald-500/20 text-emerald-300 border border-emerald-500/40">
                          ✓ HISTORICALLY_ALIGNED_SNAPSHOT ({replayData.exposureContext.daysMismatchFromEpisodeEnd} DAYS)
                        </span>
                      )}
                    </div>
                  </div>

                  {replayData.exposureContext.epistemicWarning && (
                    <div className="panel-inset p-3 rounded bg-amber-500/10 border border-amber-500/30 text-amber-200 font-mono text-[11px] leading-relaxed">
                      {replayData.exposureContext.epistemicWarning}
                    </div>
                  )}

                  {/* Market-Cap & Top Sector Distribution */}
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-1 font-mono text-[11px]">
                    {/* Market-Cap Breakdown */}
                    <div className="panel-inset p-3.5 space-y-2 rounded">
                      <span className="text-text-tertiary uppercase text-[10px] font-semibold block">
                        Market-Cap Distribution at Snapshot Date
                      </span>
                      <div className="space-y-1.5">
                        <div className="flex justify-between">
                          <span className="text-text-secondary">Large Cap:</span>
                          <span className="font-bold text-text-primary">{replayData.exposureContext.largeCapWeightPct.toFixed(2)}%</span>
                        </div>
                        <div className="flex justify-between">
                          <span className="text-text-secondary">Mid Cap:</span>
                          <span className="font-bold text-text-primary">{replayData.exposureContext.midCapWeightPct.toFixed(2)}%</span>
                        </div>
                        <div className="flex justify-between">
                          <span className="text-text-secondary">Small Cap:</span>
                          <span className="font-bold text-text-primary">{replayData.exposureContext.smallCapWeightPct.toFixed(2)}%</span>
                        </div>
                        <div className="flex justify-between">
                          <span className="text-text-secondary">Cash & Equivalents:</span>
                          <span className="font-bold text-text-primary">{replayData.exposureContext.cashWeightPct.toFixed(2)}%</span>
                        </div>
                      </div>
                    </div>

                    {/* Top Sectors Breakdown */}
                    <div className="panel-inset p-3.5 space-y-2 rounded">
                      <span className="text-text-tertiary uppercase text-[10px] font-semibold block">
                        Top Macro-Sectors at Snapshot Date
                      </span>
                      <div className="space-y-1.5">
                        {replayData.exposureContext.topSectors.map((sec, idx) => (
                          <div key={idx} className="flex justify-between items-center">
                            <span className="text-text-secondary truncate max-w-[180px]">{sec.macroSector}</span>
                            <span className="font-bold text-accent">{sec.weightPct.toFixed(2)}%</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  </div>
                </div>

                {/* Modeled Sensitivity Scenarios (Beta-Transmitted Stress Shocks) */}
                <div className="panel p-5 space-y-4">
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="font-mono text-[10px] uppercase tracking-wider text-accent">
                        FORWARD-LOOKING STRESS ESTIMATES
                      </span>
                      <span className="px-1.5 py-0.5 rounded text-[9px] font-mono font-bold bg-amber-500/10 text-amber-400 border border-amber-500/30">
                        SENSITIVITY_ESTIMATE_ONLY
                      </span>
                    </div>
                    <h4 className="text-[13px] font-semibold text-text-primary mt-0.5">
                      Modeled Systematic Sensitivity Shocks (-5%, -10%, -15%)
                    </h4>
                    <p className="font-mono text-[11px] text-text-tertiary">
                      Linear beta transmission from benchmark shock. <strong className="text-amber-400">Caution:</strong> Linear sensitivity does not model severe liquidity cascades, correlation breakdowns, or non-linear downside tail risk.
                    </p>
                  </div>

                  <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    {replayData.modeledScenarios.map(sc => (
                      <div key={sc.scenarioId} className="panel-inset p-4 rounded-lg space-y-3 border border-border">
                        <div className="flex items-center justify-between">
                          <span className="font-bold text-[13px] text-text-primary font-mono">{sc.shockLabel}</span>
                          <span className="font-mono text-[10px] px-1.5 py-0.5 rounded bg-rose-500/10 text-rose-400 border border-rose-500/30">
                            Shock: {sc.benchmarkShockPct.toFixed(1)}%
                          </span>
                        </div>

                        <div className="p-3 bg-surface-raised rounded text-center space-y-0.5">
                          <span className="font-mono text-[10px] text-text-tertiary uppercase block">
                            Estimated Portfolio Loss
                          </span>
                          <div className="text-[20px] font-bold font-mono text-rose-400">
                            {sc.estimatedPortfolioLossPct.toFixed(2)}%
                          </div>
                          <span className="font-mono text-[10px] text-text-secondary block">
                            Beta: {sc.betaUsed.toFixed(2)}
                          </span>
                        </div>

                        {/* Constituent breakdown */}
                        <div className="space-y-1 pt-1 font-mono text-[10px]">
                          <span className="text-text-tertiary uppercase block font-semibold">Constituent Transmission</span>
                          {sc.constituentEstimates.map(ce => (
                            <div key={ce.schemeOptionId} className="flex justify-between py-0.5 border-b border-border/40">
                              <span className="text-text-secondary truncate max-w-[130px]" title={ce.fundName}>
                                {ce.fundName}
                              </span>
                              <span className="text-rose-400 font-semibold">
                                {ce.estimatedLossPct.toFixed(2)}%
                              </span>
                            </div>
                          ))}
                        </div>

                        {/* Omitted Non-Linear Effects */}
                        <div className="pt-2 border-t border-border/60">
                          <details className="cursor-pointer">
                            <summary className="font-mono text-[10px] text-amber-400 font-semibold hover:underline">
                              Omitted Non-Linear Effects ({sc.omittedEffects.length})
                            </summary>
                            <ul className="mt-1.5 list-disc list-inside space-y-0.5 font-mono text-[9px] text-text-tertiary">
                              {sc.omittedEffects.map((eff, idx) => (
                                <li key={idx}>{eff}</li>
                              ))}
                            </ul>
                          </details>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Methodological Transparency & Limitations */}
                <div className="panel p-5 space-y-3 bg-surface-raised border border-border">
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-[10px] uppercase tracking-wider text-text-tertiary">
                      EPISTEMIC BOUNDARIES & MATHEMATICAL CONVENTIONS
                    </span>
                  </div>
                  <h4 className="text-[13px] font-semibold text-text-primary">
                    Replay Methodology & Data Lineage Disclosure
                  </h4>

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4 font-mono text-[11px] pt-1">
                    <div className="p-3 bg-surface rounded space-y-1">
                      <span className="text-text-tertiary uppercase text-[10px] block">Rebalancing Convention</span>
                      <p className="text-text-primary font-semibold">{replayData.rebalancingConvention}</p>
                      <p className="text-text-secondary text-[10px]">
                        Target weights are re-established at the start of each trading day. Does not simulate transaction costs or tracking drift.
                      </p>
                    </div>

                    <div className="p-3 bg-surface rounded space-y-1">
                      <span className="text-text-tertiary uppercase text-[10px] block">Bitemporal Look-Ahead Discipline</span>
                      <p className="text-text-primary font-semibold">Point-in-Time Availability Guaranteed</p>
                      <p className="text-text-secondary text-[10px]">
                        Historical NAV observations strictly respect effective dates and ingestion availability timestamps. No future observations leak.
                      </p>
                    </div>
                  </div>

                  {replayData.limitations && replayData.limitations.length > 0 && (
                    <div className="pt-2 border-t border-border">
                      <span className="font-mono text-[10px] uppercase font-bold text-text-secondary block mb-1">
                        Known Analytical Limitations:
                      </span>
                      <ul className="list-disc list-inside space-y-1 font-mono text-[10px] text-text-tertiary">
                        {replayData.limitations.map((lim, idx) => (
                          <li key={idx}>{lim}</li>
                        ))}
                      </ul>
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
        )}

        {/* TAB 6: SCENARIO LAB & RISK INVESTIGATION (PHASE 2N) */}
        {activeTab === 'scenario' && (
          <ScenarioLabSection
            selectedFunds={selectedFunds}
            availableSchemes={availableSchemes}
            asOfDate={asOfDate}
          />
        )}

        {/* TAB 6: CANDIDATE FUND SIMULATION SCENARIO */}
        {activeTab === 'simulation' && (
          <div className="space-y-4">
            {/* Simulation Input Panel */}
            <div className="panel p-5 space-y-4 border-l-4 border-l-accent">
              <div>
                <span className="font-mono text-[10px] uppercase tracking-wider text-accent">06 SIMULATION WORKFLOW</span>
                <h3 className="text-[14px] font-semibold text-text-primary">
                  Candidate Fund Addition & Proportional Rebalance Scenario
                </h3>
                <p className="font-mono text-[11px] text-text-secondary mt-0.5">
                  Simulate adding a candidate mutual fund to your research portfolio. Existing allocations will be proportionally diluted by (1 - w_c) to fund the candidate position.
                </p>
              </div>

              <div className="flex flex-wrap items-center gap-4 pt-2">
                <div className="flex-1 min-w-[280px]">
                  <label htmlFor="candidateSelect" className="field-label">Candidate Mutual Fund</label>
                  <select
                    id="candidateSelect"
                    value={candidateOptionId}
                    onChange={e => setCandidateOptionId(parseInt(e.target.value, 10))}
                    className="field text-[11px] py-1.5 px-3 h-9 w-full"
                  >
                    {availableSchemes.map(s => (
                      <option key={s.id} value={s.id}>
                        {s.plan?.scheme?.name} - {s.plan?.planType} ({s.optionType}) [AMFI: {s.amfiCode || 'N/A'}]
                      </option>
                    ))}
                  </select>
                </div>

                <div className="w-36">
                  <label htmlFor="allocationInput" className="field-label">Proposed Allocation</label>
                  <div className="flex items-center gap-1">
                    <input
                      id="allocationInput"
                      type="number"
                      step="1"
                      min="1"
                      max="99"
                      value={proposedAllocation}
                      onChange={e => setProposedAllocation(parseFloat(e.target.value) || 0)}
                      className="field field-mono text-[12px] py-1 px-3 h-9 w-full font-bold text-accent"
                    />
                    <span className="font-mono text-[12px] text-text-tertiary">%</span>
                  </div>
                </div>

                <div className="pt-5">
                  <button
                    onClick={handleRunSimulation}
                    disabled={loadingSimulation}
                    className="px-5 py-2 rounded bg-accent text-surface-dark font-mono text-[12px] font-bold hover:bg-accent/90 disabled:opacity-50 shadow"
                  >
                    {loadingSimulation ? 'Simulating Scenario...' : '🔬 Run Candidate Simulation'}
                  </button>
                </div>
              </div>

              {simulationError && (
                <div className="p-3 rounded bg-rose-500/10 border border-rose-500/30 text-rose-400 font-mono text-[11px]">
                  ⚠️ {simulationError}
                </div>
              )}
            </div>

            {/* Simulation Results View */}
            {simulationData && (
              <div className="space-y-4">
                {/* Before vs After KPI Delta Cards */}
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-5">
                  <div className="metric-tile">
                    <span className="eyebrow">Top 5 Weight Delta</span>
                    <div className="metric-rule" />
                    <div className="flex items-baseline gap-2">
                      <span className="data-value-lg">{simulationData.simulatedPortfolio.concentrationMetrics.top5WeightPct.toFixed(1)}%</span>
                      <span className={`font-mono text-[11px] font-bold ${
                        simulationData.deltaTop5WeightPct < 0 ? 'text-emerald-400' : 'text-rose-400'
                      }`}>
                        {simulationData.deltaTop5WeightPct > 0 ? '+' : ''}{simulationData.deltaTop5WeightPct.toFixed(2)}%
                      </span>
                    </div>
                    <p className="mt-auto pt-1 font-mono text-[10px] text-text-tertiary">
                      Baseline: {simulationData.baselinePortfolio.concentrationMetrics.top5WeightPct.toFixed(1)}%
                    </p>
                  </div>

                  <div className="metric-tile">
                    <span className="eyebrow">Top 10 Weight Delta</span>
                    <div className="metric-rule" />
                    <div className="flex items-baseline gap-2">
                      <span className="data-value-lg">{simulationData.simulatedPortfolio.concentrationMetrics.top10WeightPct.toFixed(1)}%</span>
                      <span className={`font-mono text-[11px] font-bold ${
                        simulationData.deltaTop10WeightPct < 0 ? 'text-emerald-400' : 'text-rose-400'
                      }`}>
                        {simulationData.deltaTop10WeightPct > 0 ? '+' : ''}{simulationData.deltaTop10WeightPct.toFixed(2)}%
                      </span>
                    </div>
                    <p className="mt-auto pt-1 font-mono text-[10px] text-text-tertiary">
                      Baseline: {simulationData.baselinePortfolio.concentrationMetrics.top10WeightPct.toFixed(1)}%
                    </p>
                  </div>

                  <div className="metric-tile">
                    <span className="eyebrow">HHI Index Shift</span>
                    <div className="metric-rule" />
                    <div className="flex items-baseline gap-2">
                      <span className="data-value-lg">{simulationData.simulatedPortfolio.concentrationMetrics.herfindahlHirschmanIndex.toFixed(0)}</span>
                      <span className={`font-mono text-[11px] font-bold ${
                        simulationData.deltaHhi < 0 ? 'text-emerald-400' : 'text-rose-400'
                      }`}>
                        {simulationData.deltaHhi > 0 ? '+' : ''}{simulationData.deltaHhi.toFixed(1)}
                      </span>
                    </div>
                    <p className="mt-auto pt-1 font-mono text-[10px] text-text-tertiary">
                      Baseline: {simulationData.baselinePortfolio.concentrationMetrics.herfindahlHirschmanIndex.toFixed(0)}
                    </p>
                  </div>

                  <div className="metric-tile">
                    <span className="eyebrow">Shared Holdings Weight</span>
                    <div className="metric-rule" />
                    <div className="flex items-baseline gap-2">
                      <span className="data-value-lg text-amber-400">{simulationData.simulatedPortfolio.concentrationMetrics.sharedSecuritiesWeightPct.toFixed(1)}%</span>
                      <span className={`font-mono text-[11px] font-bold ${
                        simulationData.deltaSharedSecuritiesWeightPct > 0 ? 'text-amber-400' : 'text-emerald-400'
                      }`}>
                        {simulationData.deltaSharedSecuritiesWeightPct > 0 ? '+' : ''}{simulationData.deltaSharedSecuritiesWeightPct.toFixed(2)}%
                      </span>
                    </div>
                    <p className="mt-auto pt-1 font-mono text-[10px] text-text-tertiary">
                      Baseline: {simulationData.baselinePortfolio.concentrationMetrics.sharedSecuritiesWeightPct.toFixed(1)}%
                    </p>
                  </div>

                  <div className="metric-tile">
                    <span className="eyebrow">Unique Holdings Count</span>
                    <div className="metric-rule" />
                    <div className="flex items-baseline gap-2">
                      <span className="data-value-lg">{simulationData.simulatedPortfolio.concentrationMetrics.uniqueSecuritiesCount}</span>
                      <span className="font-mono text-[11px] font-bold text-emerald-400">
                        +{simulationData.deltaUniqueSecuritiesCount}
                      </span>
                    </div>
                    <p className="mt-auto pt-1 font-mono text-[10px] text-text-tertiary">
                      +{simulationData.newSecuritiesIntroducedCount} new / {simulationData.augmentedSecuritiesCount} augmented
                    </p>
                  </div>
                </div>

                {/* Risk Metric Deltas Row */}
                {(simulationData.deltaVolatilityPct != null || simulationData.deltaDiversificationBenefitPct != null) && (
                  <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                    <div className="metric-tile">
                      <span className="eyebrow">Realized Volatility Shift</span>
                      <div className="metric-rule" />
                      <div className="flex items-baseline gap-2">
                        <span className="data-value-lg text-accent">
                          {simulationData.simulatedPortfolio.riskDecomposition?.portfolioRealizedVolatilityPct != null
                            ? `${simulationData.simulatedPortfolio.riskDecomposition.portfolioRealizedVolatilityPct.toFixed(2)}%`
                            : 'N/A'}
                        </span>
                        {simulationData.deltaVolatilityPct != null && (
                          <span className={`font-mono text-[11px] font-bold ${
                            simulationData.deltaVolatilityPct < 0 ? 'text-emerald-400' : 'text-amber-400'
                          }`}>
                            {simulationData.deltaVolatilityPct > 0 ? '+' : ''}{simulationData.deltaVolatilityPct.toFixed(2)}%
                          </span>
                        )}
                      </div>
                      <p className="mt-auto pt-1 font-mono text-[10px] text-text-tertiary">
                        Baseline: {simulationData.baselinePortfolio.riskDecomposition?.portfolioRealizedVolatilityPct != null ? `${simulationData.baselinePortfolio.riskDecomposition.portfolioRealizedVolatilityPct.toFixed(2)}%` : 'N/A'}
                      </p>
                    </div>

                    <div className="metric-tile">
                      <span className="eyebrow">Diversification Delta</span>
                      <div className="metric-rule" />
                      <div className="flex items-baseline gap-2">
                        <span className="data-value-lg text-emerald-400">
                          {simulationData.simulatedPortfolio.riskDecomposition?.diversificationBenefitPct != null
                            ? `+${simulationData.simulatedPortfolio.riskDecomposition.diversificationBenefitPct.toFixed(2)}%`
                            : 'N/A'}
                        </span>
                        {simulationData.deltaDiversificationBenefitPct != null && (
                          <span className={`font-mono text-[11px] font-bold ${
                            simulationData.deltaDiversificationBenefitPct > 0 ? 'text-emerald-400' : 'text-text-tertiary'
                          }`}>
                            {simulationData.deltaDiversificationBenefitPct > 0 ? '+' : ''}{simulationData.deltaDiversificationBenefitPct.toFixed(2)}%
                          </span>
                        )}
                      </div>
                      <p className="mt-auto pt-1 font-mono text-[10px] text-text-tertiary">
                        Baseline: {simulationData.baselinePortfolio.riskDecomposition?.diversificationBenefitPct != null ? `+${simulationData.baselinePortfolio.riskDecomposition.diversificationBenefitPct.toFixed(2)}%` : 'N/A'}
                      </p>
                    </div>

                    <div className="metric-tile">
                      <span className="eyebrow">Market Beta Delta</span>
                      <div className="metric-rule" />
                      <div className="flex items-baseline gap-2">
                        <span className="data-value-lg">
                          {simulationData.simulatedPortfolio.riskDecomposition?.portfolioBeta != null
                            ? simulationData.simulatedPortfolio.riskDecomposition.portfolioBeta.toFixed(2)
                            : 'N/A'}
                        </span>
                        {simulationData.deltaBeta != null && (
                          <span className="font-mono text-[11px] font-bold text-accent">
                            {simulationData.deltaBeta > 0 ? '+' : ''}{simulationData.deltaBeta.toFixed(2)}
                          </span>
                        )}
                      </div>
                      <p className="mt-auto pt-1 font-mono text-[10px] text-text-tertiary">
                        Baseline: {simulationData.baselinePortfolio.riskDecomposition?.portfolioBeta != null ? simulationData.baselinePortfolio.riskDecomposition.portfolioBeta.toFixed(2) : 'N/A'}
                      </p>
                    </div>

                    <div className="metric-tile">
                      <span className="eyebrow">Max Drawdown Shift</span>
                      <div className="metric-rule" />
                      <div className="flex items-baseline gap-2">
                        <span className="data-value-lg text-rose-400">
                          {simulationData.simulatedPortfolio.riskDecomposition?.portfolioMaxDrawdownPct != null
                            ? `-${simulationData.simulatedPortfolio.riskDecomposition.portfolioMaxDrawdownPct.toFixed(2)}%`
                            : 'N/A'}
                        </span>
                        {simulationData.deltaMaxDrawdownPct != null && (
                          <span className={`font-mono text-[11px] font-bold ${
                            simulationData.deltaMaxDrawdownPct < 0 ? 'text-emerald-400' : 'text-rose-400'
                          }`}>
                            {simulationData.deltaMaxDrawdownPct > 0 ? '+' : ''}{simulationData.deltaMaxDrawdownPct.toFixed(2)}%
                          </span>
                        )}
                      </div>
                      <p className="mt-auto pt-1 font-mono text-[10px] text-text-tertiary">
                        Baseline: {simulationData.baselinePortfolio.riskDecomposition?.portfolioMaxDrawdownPct != null ? `-${simulationData.baselinePortfolio.riskDecomposition.portfolioMaxDrawdownPct.toFixed(2)}%` : 'N/A'}
                      </p>
                    </div>
                  </div>
                )}

                {/* Analytical Observations Banner */}
                <div className="panel p-4 space-y-2 bg-surface-raised">
                  <span className="font-mono text-[11px] font-bold text-accent uppercase tracking-wider block">
                    Factual Analytical Observations (No Advice / Disclaimers)
                  </span>
                  <ul className="list-disc list-inside space-y-1 font-mono text-[11px] text-text-secondary">
                    {simulationData.analyticalObservations.map((obs, idx) => (
                      <li key={idx}>{obs}</li>
                    ))}
                  </ul>
                </div>

                {/* Augmented Securities & New Securities Tables */}
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                  {/* Augmented Securities (Positions that were already owned and increased) */}
                  <div className="panel p-4 space-y-3">
                    <h4 className="text-[13px] font-semibold text-text-primary flex items-center justify-between">
                      <span>Augmented Existing Positions ({simulationData.augmentedSecuritiesCount})</span>
                      <span className="font-mono text-[10px] text-text-tertiary">Already held in portfolio</span>
                    </h4>

                    <div className="overflow-x-auto rounded border border-border">
                      <table className="w-full text-left font-mono text-[10.5px]">
                        <thead className="bg-surface-raised text-text-tertiary">
                          <tr>
                            <th className="py-1.5 px-2.5">Security</th>
                            <th className="py-1.5 px-2.5 text-right">Baseline</th>
                            <th className="py-1.5 px-2.5 text-right text-accent">Candidate Adds</th>
                            <th className="py-1.5 px-2.5 text-right font-bold">New Total</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-border">
                          {simulationData.topAugmentedSecurities.slice(0, 8).map((sec, idx) => (
                            <tr key={idx} className="hover:bg-surface-raised/40">
                              <td className="py-1.5 px-2.5 font-sans font-medium text-text-primary truncate max-w-[150px]">
                                {sec.securityName}
                              </td>
                              <td className="py-1.5 px-2.5 text-right text-text-secondary">
                                {sec.baselineWeightPct.toFixed(2)}%
                              </td>
                              <td className="py-1.5 px-2.5 text-right text-accent font-semibold">
                                +{sec.candidateContributionPct.toFixed(2)}%
                              </td>
                              <td className="py-1.5 px-2.5 text-right font-bold text-text-primary">
                                {sec.simulatedWeightPct.toFixed(2)}%
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>

                  {/* Newly Introduced Securities */}
                  <div className="panel p-4 space-y-3">
                    <h4 className="text-[13px] font-semibold text-text-primary flex items-center justify-between">
                      <span>Newly Introduced Securities ({simulationData.newSecuritiesIntroducedCount})</span>
                      <span className="font-mono text-[10px] text-emerald-400">Unique to candidate</span>
                    </h4>

                    <div className="overflow-x-auto rounded border border-border">
                      <table className="w-full text-left font-mono text-[10.5px]">
                        <thead className="bg-surface-raised text-text-tertiary">
                          <tr>
                            <th className="py-1.5 px-2.5">Security</th>
                            <th className="py-1.5 px-2.5">Sector</th>
                            <th className="py-1.5 px-2.5 text-right font-bold text-emerald-400">Added Weight</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-border">
                          {simulationData.topNewSecurities.slice(0, 8).map((sec, idx) => (
                            <tr key={idx} className="hover:bg-surface-raised/40">
                              <td className="py-1.5 px-2.5 font-sans font-medium text-text-primary truncate max-w-[150px]">
                                {sec.canonicalName}
                              </td>
                              <td className="py-1.5 px-2.5 text-text-secondary">
                                {sec.sector}
                              </td>
                              <td className="py-1.5 px-2.5 text-right font-bold text-emerald-400">
                                {sec.consolidatedWeightPct.toFixed(2)}%
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                </div>
              </div>
            )}
          </div>
        )}

        {/* TAB 7: EVIDENCE & METHODOLOGY */}
        {activeTab === 'evidence' && (
          <div className="space-y-4">
            <div className="panel p-5 space-y-3">
              <h3 className="text-[14px] font-semibold text-text-primary">Methodology & Epistemic Boundaries</h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 font-mono text-[11px]">
                <div className="p-3 rounded bg-surface-raised space-y-1">
                  <span className="text-text-tertiary uppercase text-[10px] block">Aggregation Formula</span>
                  <p className="text-text-primary font-semibold">E_portfolio,k = ∑ ( a_j × E_j,k )</p>
                  <p className="text-text-secondary text-[10px]">
                    Consolidated look-through weight equals the investor allocation a_j multiplied by each fund’s reported holding weight.
                  </p>
                </div>

                <div className="p-3 rounded bg-surface-raised space-y-1">
                  <span className="text-text-tertiary uppercase text-[10px] block">Point-in-Time Discipline</span>
                  <p className="text-text-primary font-semibold">Strict Historical Knowledge Cutoffs</p>
                  <p className="text-text-secondary text-[10px]">
                    Only snapshots published on or prior to knowledge_cutoff and effective on or before as_of_date are used. Look-ahead bias is eliminated.
                  </p>
                </div>
              </div>

              {/* Progressive Disclosure Checklist */}
              <div className="space-y-2 pt-3 border-t border-border">
                <h4 className="text-[12px] font-semibold text-text-secondary">Epistemic Progressive Disclosure</h4>

                <div className="space-y-2 font-mono text-[11px]">
                  <details className="panel-inset p-3 cursor-pointer">
                    <summary className="font-semibold text-text-primary">1. What YUKIRA Sees</summary>
                    <p className="mt-1 text-text-secondary">
                      Point-in-time disclosed holdings from AMC monthly portfolio filings, canonical security ISIN mappings, and AMFI semi-annual market capitalization ranks.
                    </p>
                  </details>

                  <details className="panel-inset p-3 cursor-pointer">
                    <summary className="font-semibold text-text-primary">2. Why It Matters</summary>
                    <p className="mt-1 text-text-secondary">
                      Investors frequently hold multiple funds believing they are diversified, only to find high overlapping exposure in the same bank or tech stocks.
                    </p>
                  </details>

                  <details className="panel-inset p-3 cursor-pointer">
                    <summary className="font-semibold text-text-primary">3. Key Risks</summary>
                    <p className="mt-1 text-text-secondary">
                      Unintended factor or sector concentration, hidden shared-security drawdowns, and false diversification narratives.
                    </p>
                  </details>

                  <details className="panel-inset p-3 cursor-pointer">
                    <summary className="font-semibold text-text-primary">4. What Could Make This Analysis Wrong</summary>
                    <p className="mt-1 text-text-secondary">
                      Mid-month trading turnover after the monthly portfolio filing date, undisclosed derivative overlays, or non-comparable observation dates spanning different market periods.
                    </p>
                  </details>

                  <details className="panel-inset p-3 cursor-pointer">
                    <summary className="font-semibold text-text-primary">5. What Investors Should Investigate</summary>
                    <p className="mt-1 text-text-secondary">
                      Whether the overlap in top holdings aligns with your risk tolerance, whether the candidate fund actually adds new security exposure, and the temporal spread between disclosures.
                    </p>
                  </details>
                </div>
              </div>

              {/* Data Quality Limitations */}
              {lookThroughData?.limitations && lookThroughData.limitations.length > 0 && (
                <div className="pt-3 border-t border-border">
                  <h4 className="text-[12px] font-semibold text-text-secondary mb-1.5">Observation Limitations & Traceability</h4>
                  <ul className="list-disc list-inside space-y-1 text-[11px] text-text-tertiary font-mono">
                    {lookThroughData.limitations.map((lim, idx) => (
                      <li key={idx}>{lim}</li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
