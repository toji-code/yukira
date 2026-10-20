'use client';

import React, { useState, useEffect } from 'react';
import {
  FundConcentrationDto,
  PortfolioOverlapResponseDto,
  PortfolioSecurityAggregationDto,
  HoldingsCoverageReportDto,
  PortfolioHoldingDto,
  fetchFundConcentration,
  fetchPairwiseOverlap,
  fetchLookThroughExposure,
  fetchHoldingsCoverageAudit
} from '@/lib/api/portfolio';
import { formatPercentage } from '@/lib/utils/formatters';

interface PortfolioIntelligenceSectionProps {
  holdings?: PortfolioHoldingDto[];
}

export function PortfolioIntelligenceSection({ holdings = [] }: PortfolioIntelligenceSectionProps) {
  const [activeTab, setActiveTab] = useState<'concentration' | 'overlap' | 'lookThrough' | 'evidence'>('concentration');

  // Concentration State
  const [selectedConcentrationFundId, setSelectedConcentrationFundId] = useState<number | null>(
    holdings.length > 0 ? holdings[0].schemeOptionId : 1 // default to 1 (HDFC Flexi Cap)
  );
  const [concentrationData, setConcentrationData] = useState<FundConcentrationDto | null>(null);
  const [loadingConcentration, setLoadingConcentration] = useState<boolean>(false);
  const [concentrationError, setConcentrationError] = useState<string | null>(null);

  // Overlap State
  const [fundAId, setFundAId] = useState<number>(
    holdings.length > 0 ? holdings[0].schemeOptionId : 1
  );
  const [fundBId, setFundBId] = useState<number>(
    holdings.length > 1 ? holdings[1].schemeOptionId : 2
  );
  const [overlapData, setOverlapData] = useState<PortfolioOverlapResponseDto | null>(null);
  const [loadingOverlap, setLoadingOverlap] = useState<boolean>(false);
  const [overlapError, setOverlapError] = useState<string | null>(null);

  // Look-Through State
  const [lookThroughData, setLookThroughData] = useState<PortfolioSecurityAggregationDto | null>(null);
  const [loadingLookThrough, setLoadingLookThrough] = useState<boolean>(false);
  const [lookThroughError, setLookThroughError] = useState<string | null>(null);

  // Audit State
  const [auditData, setAuditData] = useState<HoldingsCoverageReportDto | null>(null);
  const [loadingAudit, setLoadingAudit] = useState<boolean>(false);
  const [auditError, setAuditError] = useState<string | null>(null);

  // Fetch Concentration
  useEffect(() => {
    if (!selectedConcentrationFundId) return;
    let ignore = false;
    async function loadConcentration() {
      setLoadingConcentration(true);
      setConcentrationError(null);
      try {
        const data = await fetchFundConcentration(selectedConcentrationFundId!);
        if (!ignore) {
          setConcentrationData(data);
          setLoadingConcentration(false);
        }
      } catch (err: unknown) {
        if (!ignore) {
          setConcentrationError((err as Error).message || 'Failed to load concentration data');
          setLoadingConcentration(false);
        }
      }
    }
    loadConcentration();
    return () => { ignore = true; };
  }, [selectedConcentrationFundId]);

  // Fetch Overlap
  useEffect(() => {
    if (!fundAId || !fundBId || fundAId === fundBId) return;
    let ignore = false;
    async function loadOverlap() {
      setLoadingOverlap(true);
      setOverlapError(null);
      try {
        const data = await fetchPairwiseOverlap(fundAId, fundBId);
        if (!ignore) {
          setOverlapData(data);
          setLoadingOverlap(false);
        }
      } catch (err: unknown) {
        if (!ignore) {
          setOverlapError((err as Error).message || 'Failed to load overlap analysis');
          setLoadingOverlap(false);
        }
      }
    }
    loadOverlap();
    return () => { ignore = true; };
  }, [fundAId, fundBId]);

  // Fetch Look-Through
  useEffect(() => {
    let ignore = false;
    async function loadLookThrough() {
      setLoadingLookThrough(true);
      setLookThroughError(null);
      try {
        const weightsMap: Record<number, number> = {};
        if (holdings.length > 0) {
          holdings.forEach(h => {
            weightsMap[h.schemeOptionId] = h.portfolioWeight || 1.0;
          });
        } else {
          // Fallback pilot portfolio weights for direct demonstration
          weightsMap[1] = 40.0; // HDFC Flexi Cap
          weightsMap[2] = 35.0; // PPFAS Flexi Cap
          weightsMap[3] = 25.0; // SBI Flexicap
        }
        const data = await fetchLookThroughExposure(weightsMap);
        if (!ignore) {
          setLookThroughData(data);
          setLoadingLookThrough(false);
        }
      } catch (err: unknown) {
        if (!ignore) {
          setLookThroughError((err as Error).message || 'Failed to load look-through exposure');
          setLoadingLookThrough(false);
        }
      }
    }
    loadLookThrough();
    return () => { ignore = true; };
  }, [holdings]);

  // Fetch Audit
  useEffect(() => {
    if (activeTab !== 'evidence') return;
    let ignore = false;
    async function loadAudit() {
      setLoadingAudit(true);
      setAuditError(null);
      try {
        const data = await fetchHoldingsCoverageAudit();
        if (!ignore) {
          setAuditData(data);
          setLoadingAudit(false);
        }
      } catch (err: unknown) {
        if (!ignore) {
          setAuditError((err as Error).message || 'Failed to load coverage audit');
          setLoadingAudit(false);
        }
      }
    }
    loadAudit();
    return () => { ignore = true; };
  }, [activeTab]);

  return (
    <div className="bg-surface border border-border rounded-xl p-6 shadow-sm text-text-primary dark:bg-slate-900 dark:border-slate-800 dark:text-slate-100">
      <div className="flex flex-col md:flex-row md:items-center justify-between border-b border-border dark:border-slate-800 pb-4 mb-6 gap-4">
        <div>
          <h2 className="text-xl font-semibold tracking-tight text-text-primary dark:text-white flex items-center gap-2">
            <span>Portfolio Intelligence & Security Evidence</span>
            <span className="text-xs px-2.5 py-0.5 rounded-full bg-accent/15 text-accent border border-accent/30 font-mono dark:bg-cyan-950 dark:text-cyan-400 dark:border-cyan-800">
              DETERMINISTIC
            </span>
          </h2>
          <p className="text-sm text-text-secondary dark:text-slate-400 mt-1">
            Evidence-based concentration, pairwise overlap, look-through security exposure, and reporting coverage.
          </p>
        </div>

        {/* Sub-Navigation Tabs */}
        <div className="flex flex-wrap gap-1 bg-surface-raised dark:bg-slate-950 p-1 rounded-lg border border-border dark:border-slate-800">
          <button
            onClick={() => setActiveTab('concentration')}
            className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${
              activeTab === 'concentration'
                ? 'bg-primary text-surface-dark font-semibold shadow dark:bg-cyan-600 dark:text-white'
                : 'text-text-secondary hover:text-text-primary hover:bg-surface-hover dark:text-slate-400 dark:hover:text-slate-200 dark:hover:bg-slate-900'
            }`}
          >
            Fund Concentration
          </button>
          <button
            onClick={() => setActiveTab('overlap')}
            className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${
              activeTab === 'overlap'
                ? 'bg-primary text-surface-dark font-semibold shadow dark:bg-cyan-600 dark:text-white'
                : 'text-text-secondary hover:text-text-primary hover:bg-surface-hover dark:text-slate-400 dark:hover:text-slate-200 dark:hover:bg-slate-900'
            }`}
          >
            Pairwise Overlap
          </button>
          <button
            onClick={() => setActiveTab('lookThrough')}
            className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${
              activeTab === 'lookThrough'
                ? 'bg-primary text-surface-dark font-semibold shadow dark:bg-cyan-600 dark:text-white'
                : 'text-text-secondary hover:text-text-primary hover:bg-surface-hover dark:text-slate-400 dark:hover:text-slate-200 dark:hover:bg-slate-900'
            }`}
          >
            Look-Through Exposure
          </button>
          <button
            onClick={() => setActiveTab('evidence')}
            className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${
              activeTab === 'evidence'
                ? 'bg-primary text-surface-dark font-semibold shadow dark:bg-cyan-600 dark:text-white'
                : 'text-text-secondary hover:text-text-primary hover:bg-surface-hover dark:text-slate-400 dark:hover:text-slate-200 dark:hover:bg-slate-900'
            }`}
          >
            Coverage & Evidence Audit
          </button>
        </div>
      </div>

      {/* ------------------------------------------------------------------- */}
      {/* TAB 1: FUND CONCENTRATION */}
      {/* ------------------------------------------------------------------- */}
      {activeTab === 'concentration' && (
        <div className="space-y-6">
          <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 bg-slate-950/60 p-4 rounded-lg border border-slate-800">
            <div>
              <label className="block text-xs font-mono text-slate-400 uppercase tracking-wider mb-1">
                Select Fund for Concentration Analysis
              </label>
              <select
                value={selectedConcentrationFundId || ''}
                onChange={(e) => setSelectedConcentrationFundId(Number(e.target.value))}
                className="bg-slate-900 border border-slate-700 text-white text-sm rounded-lg px-3 py-2 focus:ring-1 focus:ring-cyan-500 focus:outline-none"
              >
                {holdings.length > 0 ? (
                  holdings.map((h) => (
                    <option key={h.schemeOptionId} value={h.schemeOptionId}>
                      {h.fundName} ({h.amcName})
                    </option>
                  ))
                ) : (
                  <>
                    <option value={1}>HDFC Flexi Cap Fund (Direct Growth)</option>
                    <option value={2}>Parag Parikh Flexi Cap Fund (Direct Growth)</option>
                    <option value={3}>SBI Flexicap Fund (Direct Growth)</option>
                  </>
                )}
              </select>
            </div>
            {concentrationData?.portfolioDate && (
              <div className="text-right text-xs text-slate-400">
                <span className="font-mono text-slate-300">As of Date:</span> {concentrationData.portfolioDate}
              </div>
            )}
          </div>

          {loadingConcentration ? (
            <div className="p-8 text-center text-slate-400 animate-pulse font-mono text-xs">
              Calculating fund concentration and Herfindahl-Hirschman Index...
            </div>
          ) : concentrationError ? (
            <div className="p-4 bg-red-950/50 border border-red-800 text-red-300 rounded-lg text-sm">
              {concentrationError}
            </div>
          ) : concentrationData ? (
            <>
              {/* Quality & Limitation Alerts */}
              {concentrationData.limitations.length > 0 && (
                <div className="p-4 bg-amber-950/40 border border-amber-800/80 rounded-lg space-y-1">
                  <div className="text-xs font-semibold text-amber-400 uppercase tracking-wider flex items-center gap-1.5">
                    <span>⚠ Data Quality & Disclosure Limitations</span>
                  </div>
                  {concentrationData.limitations.map((lim, i) => (
                    <p key={i} className="text-xs text-amber-200/90 pl-4 border-l border-amber-700">
                      {lim}
                    </p>
                  ))}
                </div>
              )}

              {/* Concentration Overview Metric Cards */}
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                <div className="bg-slate-950 p-4 rounded-lg border border-slate-800">
                  <div className="text-xs text-slate-400 font-mono">Top 5 Disclosed Weight</div>
                  <div className="text-2xl font-bold text-white mt-1">
                    {concentrationData.formattedTop5Weight}
                  </div>
                  <div className="text-xs text-slate-500 mt-1">5 largest holdings sum</div>
                </div>

                <div className="bg-slate-950 p-4 rounded-lg border border-slate-800">
                  <div className="text-xs text-slate-400 font-mono">Top 10 Disclosed Weight</div>
                  <div className="text-2xl font-bold text-white mt-1">
                    {concentrationData.formattedTop10Weight}
                  </div>
                  <div className="text-xs text-slate-500 mt-1">Top 10 holdings sum</div>
                </div>

                <div className="bg-slate-950 p-4 rounded-lg border border-slate-800">
                  <div className="text-xs text-slate-400 font-mono">Disclosed HHI Index</div>
                  <div className="text-2xl font-bold text-cyan-400 mt-1">
                    {concentrationData.formattedHhi}
                  </div>
                  <div className="text-xs text-slate-500 mt-1">
                    Classification: <span className="font-semibold text-slate-300">{concentrationData.concentrationClassification}</span>
                  </div>
                </div>

                <div className="bg-slate-950 p-4 rounded-lg border border-slate-800">
                  <div className="text-xs text-slate-400 font-mono">Disclosed Coverage</div>
                  <div className="text-2xl font-bold text-slate-200 mt-1">
                    {concentrationData.formattedDisclosedWeight}
                  </div>
                  <div className="text-xs text-slate-500 mt-1">
                    {concentrationData.positionCount} reported positions
                  </div>
                </div>
              </div>

              {/* Methodology Explainer */}
              <div className="bg-slate-950/40 p-3 rounded-lg border border-slate-800/60 text-xs text-slate-400">
                <span className="font-semibold text-slate-300">Methodology Note:</span> Herfindahl-Hirschman Index (HHI) is computed deterministically as <code className="text-cyan-300 font-mono">HHI = Σ(100 × w_i)²</code> for all disclosed weights. An HHI below 1500 indicates a diversified portfolio structure.
              </div>

              {/* Top Disclosed Holdings Table */}
              <div>
                <h3 className="text-sm font-semibold text-slate-200 mb-3">Top Disclosed Security Positions</h3>
                <div className="overflow-x-auto rounded-lg border border-slate-800">
                  <table className="w-full text-left text-xs text-slate-300">
                    <thead className="bg-slate-950 text-slate-400 uppercase font-mono border-b border-slate-800">
                      <tr>
                        <th className="px-4 py-3">#</th>
                        <th className="px-4 py-3">Security Name</th>
                        <th className="px-4 py-3">ISIN / ID</th>
                        <th className="px-4 py-3">Sector</th>
                        <th className="px-4 py-3 text-right">Disclosed Weight</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800/60 bg-slate-900/50">
                      {concentrationData.topDisclosedHoldings.map((h) => (
                        <tr key={h.positionNumber} className="hover:bg-slate-800/40 transition-colors">
                          <td className="px-4 py-2.5 font-mono text-slate-500">{h.positionNumber}</td>
                          <td className="px-4 py-2.5 font-medium text-white">{h.securityName}</td>
                          <td className="px-4 py-2.5 font-mono text-slate-400">{h.isin || 'UNRESOLVED'}</td>
                          <td className="px-4 py-2.5 text-slate-300">{h.sector || 'N/A'}</td>
                          <td className="px-4 py-2.5 text-right font-mono font-semibold text-cyan-400">
                            {h.formattedWeight}
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

      {/* ------------------------------------------------------------------- */}
      {/* TAB 2: PAIRWISE OVERLAP */}
      {/* ------------------------------------------------------------------- */}
      {activeTab === 'overlap' && (
        <div className="space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 bg-slate-950/60 p-4 rounded-lg border border-slate-800">
            <div>
              <label className="block text-xs font-mono text-slate-400 uppercase tracking-wider mb-1">
                Fund A (First Selected Fund)
              </label>
              <select
                value={fundAId}
                onChange={(e) => setFundAId(Number(e.target.value))}
                className="w-full bg-slate-900 border border-slate-700 text-white text-sm rounded-lg px-3 py-2 focus:ring-1 focus:ring-cyan-500 focus:outline-none"
              >
                <option value={1}>HDFC Flexi Cap Fund (AMFI: 118955)</option>
                <option value={2}>Parag Parikh Flexi Cap Fund (AMFI: 122639)</option>
                <option value={3}>SBI Flexicap Fund (AMFI: 120503)</option>
              </select>
            </div>

            <div>
              <label className="block text-xs font-mono text-slate-400 uppercase tracking-wider mb-1">
                Fund B (Second Selected Fund)
              </label>
              <select
                value={fundBId}
                onChange={(e) => setFundBId(Number(e.target.value))}
                className="w-full bg-slate-900 border border-slate-700 text-white text-sm rounded-lg px-3 py-2 focus:ring-1 focus:ring-cyan-500 focus:outline-none"
              >
                <option value={2}>Parag Parikh Flexi Cap Fund (AMFI: 122639)</option>
                <option value={1}>HDFC Flexi Cap Fund (AMFI: 118955)</option>
                <option value={3}>SBI Flexicap Fund (AMFI: 120503)</option>
              </select>
            </div>
          </div>

          {loadingOverlap ? (
            <div className="p-8 text-center text-slate-400 animate-pulse font-mono text-xs">
              Computing deterministic pairwise overlap formula Overlap(A,B) = Σ min(w_A, w_B)...
            </div>
          ) : overlapError ? (
            <div className="p-4 bg-red-950/50 border border-red-800 text-red-300 rounded-lg text-sm">
              {overlapError}
            </div>
          ) : overlapData ? (
            <>
              {/* Quality & Limitation Alerts */}
              {overlapData.limitations.length > 0 && (
                <div className="p-4 bg-amber-950/40 border border-amber-800/80 rounded-lg space-y-1">
                  <div className="text-xs font-semibold text-amber-400 uppercase tracking-wider">
                    ⚠ Overlap Audit & Date Alignment Warnings
                  </div>
                  {overlapData.limitations.map((lim, i) => (
                    <p key={i} className="text-xs text-amber-200/90 pl-4 border-l border-amber-700">
                      {lim}
                    </p>
                  ))}
                </div>
              )}

              {/* Overlap Summary Card */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div className="bg-slate-950 p-4 rounded-lg border border-slate-800 text-center">
                  <div className="text-xs text-slate-400 font-mono">Portfolio Overlap</div>
                  <div className="text-3xl font-extrabold text-cyan-400 mt-1">
                    {overlapData.formattedOverlapPercentage}
                  </div>
                  <div className="text-xs text-slate-500 mt-1">
                    Shared weight between Fund A & Fund B
                  </div>
                </div>

                <div className="bg-slate-950 p-4 rounded-lg border border-slate-800 text-center">
                  <div className="text-xs text-slate-400 font-mono">Common Disclosed Securities</div>
                  <div className="text-3xl font-extrabold text-white mt-1">
                    {overlapData.commonSecuritiesCount}
                  </div>
                  <div className="text-xs text-slate-500 mt-1">
                    Securities held by both funds
                  </div>
                </div>

                <div className="bg-slate-950 p-4 rounded-lg border border-slate-800 text-center">
                  <div className="text-xs text-slate-400 font-mono">Unique Positions</div>
                  <div className="text-xl font-bold text-slate-300 mt-2">
                    {overlapData.uniqueSecuritiesFundA} / {overlapData.uniqueSecuritiesFundB}
                  </div>
                  <div className="text-xs text-slate-500 mt-1">
                    Unique to A / Unique to B
                  </div>
                </div>
              </div>

              {/* Methodology Explanation */}
              <div className="bg-slate-950/40 p-3 rounded-lg border border-slate-800/60 text-xs text-slate-400">
                <span className="font-semibold text-slate-300">Methodology Note:</span> Overlap measures the portion of disclosed holdings shared by both funds. For each security held by both funds, YUKIRA uses the smaller disclosed weight <code className="text-cyan-300 font-mono">min(weight_A_i, weight_B_i)</code> and sums those values.
              </div>

              {/* Overlapping Securities Breakdown Table */}
              <div>
                <h3 className="text-sm font-semibold text-slate-200 mb-3">Top Overlapping Disclosed Securities</h3>
                <div className="overflow-x-auto rounded-lg border border-slate-800">
                  <table className="w-full text-left text-xs text-slate-300">
                    <thead className="bg-slate-950 text-slate-400 uppercase font-mono border-b border-slate-800">
                      <tr>
                        <th className="px-4 py-3">Security Name</th>
                        <th className="px-4 py-3">ISIN</th>
                        <th className="px-4 py-3 text-right">{overlapData.fundNameA} (A)</th>
                        <th className="px-4 py-3 text-right">{overlapData.fundNameB} (B)</th>
                        <th className="px-4 py-3 text-right">Overlap Contribution</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800/60 bg-slate-900/50">
                      {overlapData.overlappingSecurities.map((sec, idx) => (
                        <tr key={idx} className="hover:bg-slate-800/40 transition-colors">
                          <td className="px-4 py-2.5 font-medium text-white">{sec.securityName}</td>
                          <td className="px-4 py-2.5 font-mono text-slate-400">{sec.isin || 'N/A'}</td>
                          <td className="px-4 py-2.5 text-right font-mono text-slate-300">
                            {formatPercentage(sec.fundAWeightPct)}
                          </td>
                          <td className="px-4 py-2.5 text-right font-mono text-slate-300">
                            {formatPercentage(sec.fundBWeightPct)}
                          </td>
                          <td className="px-4 py-2.5 text-right font-mono font-semibold text-cyan-400">
                            {sec.formattedOverlapContribution}
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

      {/* ------------------------------------------------------------------- */}
      {/* TAB 3: LOOK-THROUGH EXPOSURE */}
      {/* ------------------------------------------------------------------- */}
      {activeTab === 'lookThrough' && (
        <div className="space-y-6">
          {loadingLookThrough ? (
            <div className="p-8 text-center text-slate-400 animate-pulse font-mono text-xs">
              Aggregating look-through security exposure across constituent funds...
            </div>
          ) : lookThroughError ? (
            <div className="p-4 bg-red-950/50 border border-red-800 text-red-300 rounded-lg text-sm">
              {lookThroughError}
            </div>
          ) : lookThroughData ? (
            <>
              {/* Quality & Limitation Alerts */}
              {lookThroughData.limitations.length > 0 && (
                <div className="p-4 bg-amber-950/40 border border-amber-800/80 rounded-lg space-y-1">
                  <div className="text-xs font-semibold text-amber-400 uppercase tracking-wider">
                    ⚠ Look-Through Disclosures & Coverage Limitations
                  </div>
                  {lookThroughData.limitations.map((lim, i) => (
                    <p key={i} className="text-xs text-amber-200/90 pl-4 border-l border-amber-700">
                      {lim}
                    </p>
                  ))}
                </div>
              )}

              {/* Metric Cards */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div className="bg-slate-950 p-4 rounded-lg border border-slate-800">
                  <div className="text-xs text-slate-400 font-mono">Constituent Funds</div>
                  <div className="text-2xl font-bold text-white mt-1">
                    {lookThroughData.totalConstituentFunds} Funds
                  </div>
                  <div className="text-xs text-slate-500 mt-1">Valued in multi-fund aggregation</div>
                </div>

                <div className="bg-slate-950 p-4 rounded-lg border border-slate-800">
                  <div className="text-xs text-slate-400 font-mono">Unique Disclosed Securities</div>
                  <div className="text-2xl font-bold text-cyan-400 mt-1">
                    {lookThroughData.uniqueSecuritiesCount} Securities
                  </div>
                  <div className="text-xs text-slate-500 mt-1">Underlying look-through positions</div>
                </div>

                <div className="bg-slate-950 p-4 rounded-lg border border-slate-800">
                  <div className="text-xs text-slate-400 font-mono">Disclosed Coverage Share</div>
                  <div className="text-2xl font-bold text-slate-200 mt-1">
                    {formatPercentage(lookThroughData.disclosedCoveragePercentage)}
                  </div>
                  <div className="text-xs text-slate-500 mt-1">Disclosed weight sum share</div>
                </div>
              </div>

              {/* Explainer */}
              <div className="bg-slate-950/40 p-3 rounded-lg border border-slate-800/60 text-xs text-slate-400">
                <span className="font-semibold text-slate-300">Look-Through Exposure Note:</span> Estimates underlying security exposure across selected funds using their disclosed portfolio weights. It is based on disclosed holdings and is not a substitute for a full transaction-level portfolio ledger.
              </div>

              {/* Aggregated Look-Through Table */}
              <div>
                <h3 className="text-sm font-semibold text-slate-200 mb-3">
                  Aggregated Look-Through Security Exposures (Top Disclosed)
                </h3>
                <div className="overflow-x-auto rounded-lg border border-slate-800">
                  <table className="w-full text-left text-xs text-slate-300">
                    <thead className="bg-slate-950 text-slate-400 uppercase font-mono border-b border-slate-800">
                      <tr>
                        <th className="px-4 py-3">Security Name</th>
                        <th className="px-4 py-3">ISIN</th>
                        <th className="px-4 py-3">Sector</th>
                        <th className="px-4 py-3 text-center">Holding Funds</th>
                        <th className="px-4 py-3 text-right">Look-Through Weight</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800/60 bg-slate-900/50">
                      {lookThroughData.topSecurityExposures.map((sec, idx) => (
                        <tr key={idx} className="hover:bg-slate-800/40 transition-colors">
                          <td className="px-4 py-2.5 font-medium text-white">{sec.securityName}</td>
                          <td className="px-4 py-2.5 font-mono text-slate-400">{sec.isin || 'UNRESOLVED'}</td>
                          <td className="px-4 py-2.5 text-slate-300">{sec.sector || 'N/A'}</td>
                          <td className="px-4 py-2.5 text-center font-mono text-slate-300 font-semibold">
                            <span className="px-2 py-0.5 rounded bg-slate-800 border border-slate-700">
                              {sec.holdingFundCount} funds
                            </span>
                          </td>
                          <td className="px-4 py-2.5 text-right font-mono font-semibold text-cyan-400">
                            {sec.formattedLookThroughWeight}
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

      {/* ------------------------------------------------------------------- */}
      {/* TAB 4: COVERAGE & EVIDENCE AUDIT */}
      {/* ------------------------------------------------------------------- */}
      {activeTab === 'evidence' && (
        <div className="space-y-6">
          {loadingAudit ? (
            <div className="p-8 text-center text-slate-400 animate-pulse font-mono text-xs">
              Fetching canonical holdings coverage audit from database...
            </div>
          ) : auditError ? (
            <div className="p-4 bg-red-950/50 border border-red-800 text-red-300 rounded-lg text-sm">
              {auditError}
            </div>
          ) : auditData ? (
            <>
              <div className="bg-slate-950 p-4 rounded-lg border border-slate-800 flex justify-between items-center text-xs">
                <span className="font-mono text-slate-400 uppercase">Universe Audit Timestamp</span>
                <span className="font-mono text-white">{auditData.auditAsOfDate}</span>
              </div>

              {/* Audit Grid */}
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
                <div className="bg-slate-950 p-4 rounded-lg border border-slate-800">
                  <div className="text-xs text-slate-400 font-mono">Active Universe</div>
                  <div className="text-2xl font-bold text-white mt-1">{auditData.totalActiveUniverse.toLocaleString()}</div>
                  <div className="text-xs text-slate-500 mt-1">Total scheme options</div>
                </div>

                <div className="bg-slate-950 p-4 rounded-lg border border-slate-800">
                  <div className="text-xs text-slate-400 font-mono">With Holdings</div>
                  <div className="text-2xl font-bold text-cyan-400 mt-1">{auditData.activeWithHoldings}</div>
                  <div className="text-xs text-slate-500 mt-1">Persisted snapshots</div>
                </div>

                <div className="bg-slate-950 p-4 rounded-lg border border-slate-800">
                  <div className="text-xs text-slate-400 font-mono">Valid Holdings</div>
                  <div className="text-2xl font-bold text-emerald-400 mt-1">{auditData.activeWithValidHoldings}</div>
                  <div className="text-xs text-slate-500 mt-1">Passing validation</div>
                </div>

                <div className="bg-slate-950 p-4 rounded-lg border border-slate-800">
                  <div className="text-xs text-slate-400 font-mono">Identity Resolved</div>
                  <div className="text-2xl font-bold text-slate-200 mt-1">{auditData.activeWithSecurityIdentities}</div>
                  <div className="text-xs text-slate-500 mt-1">ISIN / ID resolved</div>
                </div>
              </div>

              {/* Source Classification Table */}
              <div>
                <h3 className="text-sm font-semibold text-slate-200 mb-3">Production Sources Audit</h3>
                <div className="overflow-x-auto rounded-lg border border-slate-800">
                  <table className="w-full text-left text-xs text-slate-300">
                    <thead className="bg-slate-950 text-slate-400 uppercase font-mono border-b border-slate-800">
                      <tr>
                        <th className="px-4 py-3">AMFI Code</th>
                        <th className="px-4 py-3">Fund / Option Name</th>
                        <th className="px-4 py-3">Source State</th>
                        <th className="px-4 py-3 text-center">Positions</th>
                        <th className="px-4 py-3 text-right">Quality State</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800/60 bg-slate-900/50">
                      {auditData.sourceClassifications.map((src, idx) => (
                        <tr key={idx} className="hover:bg-slate-800/40 transition-colors">
                          <td className="px-4 py-2.5 font-mono text-slate-400">{src.amfiCode}</td>
                          <td className="px-4 py-2.5 font-medium text-white">{src.fundName}</td>
                          <td className="px-4 py-2.5 font-mono text-xs">
                            <span className="px-2 py-0.5 rounded bg-cyan-950 text-cyan-400 border border-cyan-800">
                              {src.sourceClassification}
                            </span>
                          </td>
                          <td className="px-4 py-2.5 text-center font-mono text-slate-300">{src.positionCount}</td>
                          <td className="px-4 py-2.5 text-right font-mono text-emerald-400 font-semibold">
                            {src.qualityState}
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
    </div>
  );
}
