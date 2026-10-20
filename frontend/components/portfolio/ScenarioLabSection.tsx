'use client';

import React, { useState, useEffect } from 'react';
import {
  ResearchPortfolioFundItemDto,
  ScenarioType,
  ScenarioTemplateDto,
  ScenarioLabRequestDto,
  ScenarioLabResponseDto,
  ScenarioComparisonResponseDto,
  ScenarioDimensionMetadataDto,
  fetchScenarioLabMetadata,
  executeScenarioLab,
  compareScenarioLab,
} from '@/lib/api/portfolio';
import { SchemeOption } from '@/types/domain';

interface ScenarioLabSectionProps {
  selectedFunds: ResearchPortfolioFundItemDto[];
  availableSchemes?: SchemeOption[];
  asOfDate: string;
}

export function ScenarioLabSection({
  selectedFunds,
  asOfDate,
}: ScenarioLabSectionProps) {
  // Pre-flight metadata
  const [metadata, setMetadata] = useState<ScenarioDimensionMetadataDto | null>(null);
  const [loadingMeta, setLoadingMeta] = useState<boolean>(false);
  const [metaError, setMetaError] = useState<string | null>(null);

  // Scenario Mode: 'single' | 'compare'
  const [labMode, setLabMode] = useState<'single' | 'compare'>('single');

  // Active Scenario Type
  const [scenarioType, setScenarioType] = useState<ScenarioType>('BROAD_MARKET');
  const [selectedTemplateId, setSelectedTemplateId] = useState<string>('TEMPLATE_BROAD_10');

  // Input Shock States
  const [broadMarketShock, setBroadMarketShock] = useState<number>(-10.0);
  const [largeCapShock, setLargeCapShock] = useState<number>(-5.0);
  const [midCapShock, setMidCapShock] = useState<number>(-10.0);
  const [smallCapShock, setSmallCapShock] = useState<number>(-18.0);
  const [cashShock, setCashShock] = useState<number>(0.0);
  const [sectorShocks, setSectorShocks] = useState<Record<string, number>>({
    'Financial Services': -12.0,
    'Information Technology': -6.0,
    'Consumer Discretionary': -8.0,
    'Healthcare': -2.0,
  });
  const [combinationMethod, setCombinationMethod] = useState<string>('ADDITIVE_EXCESS_TILTS');

  // Scenario Comparison States
  const [compScenarioTypeB, setCompScenarioTypeB] = useState<ScenarioType>('MARKET_CAP');
  const [compBroadShockB, setCompBroadShockB] = useState<number>(-15.0);
  const [compLargeCapShockB, setCompLargeCapShockB] = useState<number>(-8.0);
  const [compMidCapShockB, setCompMidCapShockB] = useState<number>(-15.0);
  const [compSmallCapShockB, setCompSmallCapShockB] = useState<number>(-25.0);

  // Execution States
  const [loadingRun, setLoadingRun] = useState<boolean>(false);
  const [runError, setRunError] = useState<string | null>(null);
  const [scenarioResult, setScenarioResult] = useState<ScenarioLabResponseDto | null>(null);
  const [comparisonResult, setComparisonResult] = useState<ScenarioComparisonResponseDto | null>(null);

  // Active Results Tab
  const [resultSubTab, setResultSubTab] = useState<'overview' | 'constituents' | 'exposures' | 'evidence'>('overview');

  // Benchmark Selection
  const [benchmarkCode, setBenchmarkCode] = useState<string>('NIFTY_500_TRI');

  // Load Metadata on Fund/Date Changes
  useEffect(() => {
    if (selectedFunds.length === 0) return;
    let ignore = false;
    async function loadMeta() {
      setLoadingMeta(true);
      setMetaError(null);
      try {
        const meta = await fetchScenarioLabMetadata(selectedFunds, benchmarkCode, asOfDate);
        if (!ignore) {
          setMetadata(meta);
          // Pre-populate template if available
          if (meta.availableTemplates && meta.availableTemplates.length > 0) {
            const first = meta.availableTemplates[0];
            setSelectedTemplateId(first.id);
          }
        }
      } catch (err: unknown) {
        if (!ignore) {
          setMetaError((err as Error).message || 'Failed to load portfolio scenario metadata');
        }
      } finally {
        if (!ignore) setLoadingMeta(false);
      }
    }
    loadMeta();
    return () => { ignore = true; };
  }, [selectedFunds, benchmarkCode, asOfDate]);

  // Handle Template Selection
  const handleSelectTemplate = (t: ScenarioTemplateDto) => {
    setSelectedTemplateId(t.id);
    setScenarioType(t.category);
    if (t.broadMarketShockPct !== null && t.broadMarketShockPct !== undefined) {
      setBroadMarketShock(t.broadMarketShockPct);
    }
    if (t.marketCapShocks) {
      if (t.marketCapShocks['LARGE_CAP'] !== undefined) setLargeCapShock(t.marketCapShocks['LARGE_CAP']);
      if (t.marketCapShocks['MID_CAP'] !== undefined) setMidCapShock(t.marketCapShocks['MID_CAP']);
      if (t.marketCapShocks['SMALL_CAP'] !== undefined) setSmallCapShock(t.marketCapShocks['SMALL_CAP']);
      if (t.marketCapShocks['CASH'] !== undefined) setCashShock(t.marketCapShocks['CASH']);
    }
    if (t.sectorShocks) {
      setSectorShocks(t.sectorShocks);
    }
    if (t.combinationMethod) {
      setCombinationMethod(t.combinationMethod);
    }
  };

  // Build Request DTO
  const buildRequestDto = (
    sType: ScenarioType,
    bShock: number,
    lShock: number,
    mShock: number,
    sShock: number,
    cShock: number,
    secShocks: Record<string, number>,
    cMethod: string,
    namePrefix = 'Scenario'
  ): ScenarioLabRequestDto => {
    const marketCapMap: Record<string, number> = {
      'LARGE_CAP': lShock,
      'MID_CAP': mShock,
      'SMALL_CAP': sShock,
      'CASH': cShock,
      'UNCLASSIFIED': 0.0,
    };

    return {
      funds: selectedFunds,
      asOfDate,
      benchmarkCode,
      scenarioType: sType,
      scenarioName: `${namePrefix} (${sType})`,
      scenarioDescription: `Deterministic scenario evaluation for research portfolio`,
      broadMarketShockPct: bShock,
      marketCapShocks: marketCapMap,
      sectorShocks: secShocks,
      combinationMethod: cMethod,
      unmodeledShockPct: 0.0,
    };
  };

  // Run Scenario
  const handleRunScenario = async () => {
    if (selectedFunds.length === 0) return;
    setLoadingRun(true);
    setRunError(null);
    setScenarioResult(null);
    setComparisonResult(null);

    try {
      if (labMode === 'single') {
        const req = buildRequestDto(
          scenarioType,
          broadMarketShock,
          largeCapShock,
          midCapShock,
          smallCapShock,
          cashShock,
          sectorShocks,
          combinationMethod,
          'Active Scenario'
        );
        const res = await executeScenarioLab(req);
        setScenarioResult(res);
      } else {
        const reqA = buildRequestDto(
          scenarioType,
          broadMarketShock,
          largeCapShock,
          midCapShock,
          smallCapShock,
          cashShock,
          sectorShocks,
          combinationMethod,
          'Scenario A'
        );
        const reqB = buildRequestDto(
          compScenarioTypeB,
          compBroadShockB,
          compLargeCapShockB,
          compMidCapShockB,
          compSmallCapShockB,
          0.0,
          sectorShocks,
          combinationMethod,
          'Scenario B'
        );
        const comp = await compareScenarioLab({ scenarioA: reqA, scenarioB: reqB });
        setComparisonResult(comp);
        setScenarioResult(comp.scenarioA);
      }
    } catch (err: unknown) {
      setRunError((err as Error).message || 'Calculation execution failed.');
    } finally {
      setLoadingRun(false);
    }
  };

  // Helper formatting
  const formatPct = (val: number | null | undefined, showSign = true) => {
    if (val === null || val === undefined) return '—';
    const s = val.toFixed(2);
    if (showSign && val > 0) return `+${s}%`;
    return `${s}%`;
  };

  return (
    <div className="space-y-6">
      {/* HEADER BANNER */}
      <div className="panel p-5 bg-gradient-to-r from-surface to-surface-raised border border-border rounded-xl">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="space-y-1.5 max-w-3xl">
            <div className="flex items-center gap-2">
              <span className="status-badge state-candidate text-[10px] font-mono">PHASE 2N EXPERIMENTAL</span>
              <span className="font-mono text-[11px] text-text-tertiary">DETERMINISTIC SENSITIVITY KERNEL</span>
            </div>
            <h2 className="text-xl font-bold tracking-tight text-text-primary">
              Portfolio Scenario Lab & Evidence-Based Risk Investigation
            </h2>
            <p className="font-sans text-[12.5px] text-text-secondary leading-relaxed">
              Investigate hypothetical market shocks across broad-market beta sensitivity, market-cap buckets, and macro-sector dislocations.
              All calculations are strictly deterministic, preserve actual holdings data boundaries, and never scale unclassified weights to 100%.
            </p>
          </div>

          {/* Mode Switcher */}
          <div className="flex items-center bg-surface-sunken p-1 rounded-lg border border-border text-[11px] font-mono">
            <button
              onClick={() => setLabMode('single')}
              className={`px-3 py-1.5 rounded-md font-semibold transition-all ${
                labMode === 'single'
                  ? 'bg-accent text-accent-contrast shadow-sm'
                  : 'text-text-tertiary hover:text-text-primary'
              }`}
            >
              Single Scenario
            </button>
            <button
              onClick={() => setLabMode('compare')}
              className={`px-3 py-1.5 rounded-md font-semibold transition-all ${
                labMode === 'compare'
                  ? 'bg-accent text-accent-contrast shadow-sm'
                  : 'text-text-tertiary hover:text-text-primary'
              }`}
            >
              Compare Scenarios (A vs B)
            </button>
          </div>
        </div>

        {/* PRE-FLIGHT METADATA AUDIT BAR */}
        <div className="mt-4 pt-3 border-t border-border/70 grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-6 gap-3 text-[11px] font-mono">
          <div>
            <span className="text-text-tertiary block text-[10px]">Benchmark:</span>
            <select
              value={benchmarkCode}
              onChange={e => setBenchmarkCode(e.target.value)}
              className="bg-transparent font-semibold text-text-primary text-[11px] focus:outline-none cursor-pointer"
            >
              <option value="NIFTY_500_TRI" className="bg-surface text-text-primary">NIFTY 500 TRI</option>
              <option value="BSE_SENSEX_TRI" className="bg-surface text-text-primary">BSE SENSEX TRI</option>
            </select>
            {loadingMeta && <span className="block text-[9px] text-accent animate-pulse">Checking...</span>}
            {metaError && <span className="block text-[9px] text-rose-400">{metaError}</span>}
          </div>
          <div>
            <span className="text-text-tertiary block text-[10px]">Holdings Coverage:</span>
            <span className={`font-semibold ${metadata && metadata.coveredWeightPct >= 95 ? 'text-emerald-400' : 'text-amber-400'}`}>
              {metadata ? `${metadata.coveredWeightPct.toFixed(1)}% Covered` : 'Inspecting...'}
            </span>
          </div>
          <div>
            <span className="text-text-tertiary block text-[10px]">Unmodeled Weight:</span>
            <span className={`font-semibold ${metadata && metadata.unmodeledWeightPct > 5 ? 'text-amber-400' : 'text-text-secondary'}`}>
              {metadata ? `${metadata.unmodeledWeightPct.toFixed(1)}% Unmodeled` : '—'}
            </span>
          </div>
          <div>
            <span className="text-text-tertiary block text-[10px]">Portfolio OLS Beta:</span>
            <span className={`font-semibold ${metadata?.isBetaAvailable ? 'text-text-primary' : 'text-rose-400'}`}>
              {metadata?.portfolioBeta !== null && metadata?.portfolioBeta !== undefined
                ? metadata.portfolioBeta.toFixed(2)
                : metadata?.isBetaAvailable === false ? 'Gated (<15 obs)' : 'Calculating...'}
            </span>
          </div>
          <div>
            <span className="text-text-tertiary block text-[10px]">Beta Sample Size:</span>
            <span className="text-text-secondary">
              {metadata ? `N = ${metadata.betaObservationCount} days` : '—'}
            </span>
          </div>
          <div>
            <span className="text-text-tertiary block text-[10px]">Data Alignment:</span>
            <span className="text-text-secondary">{asOfDate}</span>
          </div>
        </div>
      </div>

      {/* SCENARIO CONFIGURATION PANEL */}
      <div className="panel p-5 space-y-5 rounded-xl border border-border">
        {/* Template Selector Carousel */}
        <div>
          <div className="flex items-center justify-between mb-2">
            <span className="eyebrow text-text-tertiary">1. Select Standard Scenario Template or Custom Dimension</span>
            <span className="font-mono text-[10.5px] text-text-tertiary">Pre-configured empirical templates</span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
            {metadata?.availableTemplates.map(t => {
              const isSelected = selectedTemplateId === t.id;
              return (
                <div
                  key={t.id}
                  onClick={() => handleSelectTemplate(t)}
                  className={`p-3.5 rounded-lg border cursor-pointer transition-all ${
                    isSelected
                      ? 'bg-accent/10 border-accent text-text-primary shadow-sm ring-1 ring-accent/30'
                      : 'bg-surface hover:bg-surface-raised border-border text-text-secondary'
                  }`}
                >
                  <div className="flex items-center justify-between text-[10px] font-mono mb-1">
                    <span className="px-1.5 py-0.5 rounded bg-surface-sunken text-accent font-semibold">
                      {t.category}
                    </span>
                    {isSelected && <span className="text-accent font-bold">● Active</span>}
                  </div>
                  <h4 className="font-semibold text-[13px] text-text-primary">{t.name}</h4>
                  <p className="font-sans text-[11px] text-text-secondary line-clamp-2 mt-1">{t.description}</p>
                </div>
              );
            })}
          </div>
        </div>

        {/* Custom Dimension Selector Tabs */}
        <div className="pt-2 border-t border-border/60">
          <div className="flex items-center gap-2 mb-3">
            <span className="eyebrow text-text-tertiary">2. Configure Scenario Dimensions & Shock Magnitudes:</span>
          </div>

          <div className="flex border-b border-border gap-2 font-mono text-[11.5px] mb-4">
            <button
              onClick={() => { setScenarioType('BROAD_MARKET'); setSelectedTemplateId('CUSTOM'); }}
              className={`pb-2 px-3 border-b-2 font-semibold transition-all ${
                scenarioType === 'BROAD_MARKET'
                  ? 'border-accent text-accent'
                  : 'border-transparent text-text-tertiary hover:text-text-primary'
              }`}
            >
              Broad-Market Sensitivity (Beta)
            </button>
            <button
              onClick={() => { setScenarioType('MARKET_CAP'); setSelectedTemplateId('CUSTOM'); }}
              className={`pb-2 px-3 border-b-2 font-semibold transition-all ${
                scenarioType === 'MARKET_CAP'
                  ? 'border-accent text-accent'
                  : 'border-transparent text-text-tertiary hover:text-text-primary'
              }`}
            >
              Market-Cap Buckets (Large/Mid/Small)
            </button>
            <button
              onClick={() => { setScenarioType('SECTOR'); setSelectedTemplateId('CUSTOM'); }}
              className={`pb-2 px-3 border-b-2 font-semibold transition-all ${
                scenarioType === 'SECTOR'
                  ? 'border-accent text-accent'
                  : 'border-transparent text-text-tertiary hover:text-text-primary'
              }`}
            >
              Macro Sector Tilts
            </button>
            <button
              onClick={() => { setScenarioType('COMBINED'); setSelectedTemplateId('CUSTOM'); }}
              className={`pb-2 px-3 border-b-2 font-semibold transition-all ${
                scenarioType === 'COMBINED'
                  ? 'border-accent text-accent'
                  : 'border-transparent text-text-tertiary hover:text-text-primary'
              }`}
            >
              Multi-Layer Combined Shock
            </button>
          </div>

          {/* DIMENSION CONTROLS */}
          {scenarioType === 'BROAD_MARKET' && (
            <div className="p-4 bg-surface-sunken rounded-lg space-y-4 border border-border">
              <div className="max-w-xl space-y-2">
                <div className="flex items-center justify-between text-[12px] font-mono">
                  <label className="font-semibold text-text-primary">
                    Broad Equity Market Shock ({benchmarkCode}):
                  </label>
                  <span className={`font-bold ${broadMarketShock < 0 ? 'text-rose-400' : 'text-emerald-400'}`}>
                    {formatPct(broadMarketShock)}
                  </span>
                </div>
                <input
                  type="range"
                  min="-40"
                  max="20"
                  step="1"
                  value={broadMarketShock}
                  onChange={e => setBroadMarketShock(parseFloat(e.target.value))}
                  className="w-full accent-accent cursor-pointer"
                />
                <div className="flex justify-between font-mono text-[10px] text-text-tertiary">
                  <span>-40% (Severe Crash)</span>
                  <span>-20% (Bear Market)</span>
                  <span>-10% (Correction)</span>
                  <span>0%</span>
                  <span>+20% (Rally)</span>
                </div>
              </div>

              <div className="text-[11px] font-mono text-text-secondary bg-surface p-3 rounded border border-border/60">
                <strong>Calculation Method:</strong> Linear OLS Beta Sensitivity. Estimated Return = Fund Beta × Market Shock.
                Beta is empirically estimated from {metadata?.betaObservationCount || 0} common aligned daily trading returns against {benchmarkCode}.
              </div>
            </div>
          )}

          {scenarioType === 'MARKET_CAP' && (
            <div className="p-4 bg-surface-sunken rounded-lg space-y-4 border border-border">
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div className="space-y-1 font-mono text-[12px]">
                  <div className="flex justify-between">
                    <label className="text-text-secondary">Large-Cap Shock:</label>
                    <span className={largeCapShock < 0 ? 'text-rose-400 font-bold' : 'text-emerald-400 font-bold'}>
                      {formatPct(largeCapShock)}
                    </span>
                  </div>
                  <input
                    type="range"
                    min="-40"
                    max="20"
                    step="1"
                    value={largeCapShock}
                    onChange={e => setLargeCapShock(parseFloat(e.target.value))}
                    className="w-full accent-accent"
                  />
                  <span className="text-[10px] text-text-tertiary">Portfolio Weight: {metadata?.marketCapWeights?.['LARGE_CAP']?.toFixed(1) || '0.0'}%</span>
                </div>

                <div className="space-y-1 font-mono text-[12px]">
                  <div className="flex justify-between">
                    <label className="text-text-secondary">Mid-Cap Shock:</label>
                    <span className={midCapShock < 0 ? 'text-rose-400 font-bold' : 'text-emerald-400 font-bold'}>
                      {formatPct(midCapShock)}
                    </span>
                  </div>
                  <input
                    type="range"
                    min="-40"
                    max="20"
                    step="1"
                    value={midCapShock}
                    onChange={e => setMidCapShock(parseFloat(e.target.value))}
                    className="w-full accent-accent"
                  />
                  <span className="text-[10px] text-text-tertiary">Portfolio Weight: {metadata?.marketCapWeights?.['MID_CAP']?.toFixed(1) || '0.0'}%</span>
                </div>

                <div className="space-y-1 font-mono text-[12px]">
                  <div className="flex justify-between">
                    <label className="text-text-secondary">Small-Cap Shock:</label>
                    <span className={smallCapShock < 0 ? 'text-rose-400 font-bold' : 'text-emerald-400 font-bold'}>
                      {formatPct(smallCapShock)}
                    </span>
                  </div>
                  <input
                    type="range"
                    min="-40"
                    max="20"
                    step="1"
                    value={smallCapShock}
                    onChange={e => setSmallCapShock(parseFloat(e.target.value))}
                    className="w-full accent-accent"
                  />
                  <span className="text-[10px] text-text-tertiary">Portfolio Weight: {metadata?.marketCapWeights?.['SMALL_CAP']?.toFixed(1) || '0.0'}%</span>
                </div>
              </div>

              <div className="text-[11px] font-mono text-text-secondary bg-surface p-3 rounded border border-border/60">
                <strong>Mutually Exclusive Partition:</strong> Every underlying holding is uniquely mapped to exactly one market-cap bucket. Cash ({metadata?.marketCapWeights?.['CASH']?.toFixed(1) || '0.0'}%) receives 0.00% return. Unclassified holdings ({metadata?.unmodeledWeightPct?.toFixed(1) || '0.0'}%) are isolated with 0.00% assumed shock and not scaled.
              </div>
            </div>
          )}

          {scenarioType === 'SECTOR' && (
            <div className="p-4 bg-surface-sunken rounded-lg space-y-4 border border-border">
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 font-mono text-[11.5px]">
                {metadata && Object.entries(metadata.sectorWeights).slice(0, 8).map(([secName, secWeight]) => {
                  const currentShock = sectorShocks[secName] !== undefined ? sectorShocks[secName] : 0.0;
                  return (
                    <div key={secName} className="p-2.5 bg-surface rounded border border-border/60 space-y-1.5">
                      <div className="flex justify-between text-[11px]">
                        <span className="font-semibold text-text-primary truncate" title={secName}>{secName}</span>
                        <span className="text-text-tertiary">{secWeight.toFixed(1)}%</span>
                      </div>
                      <div className="flex items-center justify-between text-[11px]">
                        <input
                          type="range"
                          min="-35"
                          max="25"
                          step="1"
                          value={currentShock}
                          onChange={e => {
                            const val = parseFloat(e.target.value);
                            setSectorShocks(prev => ({ ...prev, [secName]: val }));
                          }}
                          className="w-24 accent-accent"
                        />
                        <span className={`font-bold ${currentShock < 0 ? 'text-rose-400' : currentShock > 0 ? 'text-emerald-400' : 'text-text-tertiary'}`}>
                          {formatPct(currentShock)}
                        </span>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {scenarioType === 'COMBINED' && (
            <div className="p-4 bg-surface-sunken rounded-lg space-y-4 border border-border">
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 font-mono text-[11.5px]">
                <div className="p-3 bg-surface rounded border border-border/60 space-y-2">
                  <span className="font-bold text-accent block">Layer 1: Broad Beta Shock</span>
                  <div className="flex justify-between">
                    <span className="text-text-secondary">Market Shock:</span>
                    <span className="text-rose-400 font-bold">{formatPct(broadMarketShock)}</span>
                  </div>
                  <input
                    type="range"
                    min="-30"
                    max="10"
                    value={broadMarketShock}
                    onChange={e => setBroadMarketShock(parseFloat(e.target.value))}
                    className="w-full accent-accent"
                  />
                </div>

                <div className="p-3 bg-surface rounded border border-border/60 space-y-2">
                  <span className="font-bold text-accent block">Layer 2: Small-Cap Excess Tilt</span>
                  <div className="flex justify-between">
                    <span className="text-text-secondary">Excess Shock:</span>
                    <span className="text-rose-400 font-bold">{formatPct(smallCapShock)}</span>
                  </div>
                  <input
                    type="range"
                    min="-30"
                    max="10"
                    value={smallCapShock}
                    onChange={e => setSmallCapShock(parseFloat(e.target.value))}
                    className="w-full accent-accent"
                  />
                </div>

                <div className="p-3 bg-surface rounded border border-border/60 space-y-2">
                  <span className="font-bold text-accent block">Layer 3: Financials Sector Tilt</span>
                  <div className="flex justify-between">
                    <span className="text-text-secondary">Sector Tilt:</span>
                    <span className="text-rose-400 font-bold">{formatPct(sectorShocks['Financial Services'] || -8.0)}</span>
                  </div>
                  <input
                    type="range"
                    min="-30"
                    max="10"
                    value={sectorShocks['Financial Services'] || -8.0}
                    onChange={e => setSectorShocks(prev => ({ ...prev, 'Financial Services': parseFloat(e.target.value) }))}
                    className="w-full accent-accent"
                  />
                </div>
              </div>

              <div className="text-[11px] font-mono text-text-secondary bg-surface p-3 rounded border border-border/60">
                <strong>Double-Counting Prevention (Additive Excess Tilts):</strong>
                Layer 1 computes the portfolio baseline via beta sensitivity. Layers 2 & 3 apply only the <em>excess orthogonal tilt</em> multiplied by specific exposure weights, avoiding overlapping returns.
              </div>
            </div>
          )}

          {/* SECOND SCENARIO CONTROLS (COMPARE MODE) */}
          {labMode === 'compare' && (
            <div className="mt-4 pt-4 border-t border-border/80 p-4 bg-accent/5 rounded-lg border border-accent/20 space-y-3">
              <div className="flex items-center justify-between">
                <span className="font-bold text-[12px] font-mono text-accent">
                  Configure Comparison Scenario B (Identical Portfolio Snapshot & Cutoff):
                </span>
                <span className="text-[10px] font-mono text-text-tertiary">Baseline conditions strictly held constant</span>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-4 gap-3 font-mono text-[11px]">
                <div>
                  <label className="text-text-secondary block">Scenario B Type:</label>
                  <select
                    value={compScenarioTypeB}
                    onChange={e => setCompScenarioTypeB(e.target.value as ScenarioType)}
                    className="field text-[11px] py-1 px-2 h-8 w-full mt-1"
                  >
                    <option value="BROAD_MARKET">Broad Market (Beta)</option>
                    <option value="MARKET_CAP">Market-Cap Buckets</option>
                    <option value="SECTOR">Macro Sector</option>
                  </select>
                </div>

                {compScenarioTypeB === 'BROAD_MARKET' && (
                  <div>
                    <label className="text-text-secondary block">Market Shock B (%):</label>
                    <input
                      type="number"
                      step="0.5"
                      value={compBroadShockB}
                      onChange={e => setCompBroadShockB(parseFloat(e.target.value))}
                      className="field text-[11px] py-1 px-2 h-8 w-full mt-1"
                    />
                  </div>
                )}

                {compScenarioTypeB === 'MARKET_CAP' && (
                  <>
                    <div>
                      <label className="text-text-secondary block">Large-Cap Shock B:</label>
                      <input
                        type="number"
                        step="0.5"
                        value={compLargeCapShockB}
                        onChange={e => setCompLargeCapShockB(parseFloat(e.target.value))}
                        className="field text-[11px] py-1 px-2 h-8 w-full mt-1"
                      />
                    </div>
                    <div>
                      <label className="text-text-secondary block">Mid-Cap Shock B:</label>
                      <input
                        type="number"
                        step="0.5"
                        value={compMidCapShockB}
                        onChange={e => setCompMidCapShockB(parseFloat(e.target.value))}
                        className="field text-[11px] py-1 px-2 h-8 w-full mt-1"
                      />
                    </div>
                    <div>
                      <label className="text-text-secondary block">Small-Cap Shock B:</label>
                      <input
                        type="number"
                        step="0.5"
                        value={compSmallCapShockB}
                        onChange={e => setCompSmallCapShockB(parseFloat(e.target.value))}
                        className="field text-[11px] py-1 px-2 h-8 w-full mt-1"
                      />
                    </div>
                  </>
                )}
              </div>
            </div>
          )}
        </div>

        {/* EXECUTION BUTTON */}
        <div className="flex items-center justify-between pt-2">
          <div className="flex items-center gap-2">
            <button
              onClick={handleRunScenario}
              disabled={loadingRun || selectedFunds.length === 0}
              className="btn btn-primary text-[12px] py-2 px-5 flex items-center gap-2 font-mono shadow"
            >
              {loadingRun ? (
                <>
                  <div className="w-3.5 h-3.5 border-2 border-white/20 border-t-white rounded-full animate-spin" />
                  <span>Computing Deterministic Impact...</span>
                </>
              ) : (
                <>
                  <span>🧪 Execute Scenario Lab</span>
                </>
              )}
            </button>

            {selectedFunds.length === 0 && (
              <span className="font-mono text-[11px] text-amber-400">Please select at least one constituent fund.</span>
            )}
          </div>

          {runError && (
            <div className="font-mono text-[11px] text-rose-400 bg-rose-500/10 px-3 py-1.5 rounded border border-rose-500/30">
              {runError}
            </div>
          )}
        </div>
      </div>

      {/* RESULTS DISPLAY PANEL */}
      {scenarioResult && (
        <div className="space-y-5 animate-in fade-in duration-200">
          {/* Epistemic Status Banner */}
          <div className="p-4 rounded-xl border flex flex-wrap items-center justify-between gap-3 bg-surface-raised border-border">
            <div className="flex items-center gap-3">
              <span className="px-2.5 py-1 rounded font-mono text-[11px] font-bold bg-amber-500/10 text-amber-300 border border-amber-500/30">
                {scenarioResult.epistemicLabel || 'SENSITIVITY_ESTIMATE_ONLY'}
              </span>
              <span className="font-mono text-[11px] text-text-secondary">
                Calculation Timestamp: {new Date(scenarioResult.calculationTimestamp).toLocaleTimeString()}
              </span>
            </div>

            <div className="font-mono text-[11px] text-text-tertiary">
              Reconciled Benchmark: <strong className="text-text-primary">{scenarioResult.benchmarkCode}</strong>
            </div>
          </div>

          {/* KPI CARDS (SINGLE OR COMPARISON) */}
          {labMode === 'single' ? (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              <div className="panel p-4 rounded-xl border border-border">
                <span className="eyebrow text-text-tertiary">Estimated Portfolio Impact</span>
                <div className="metric-rule" />
                <p className={`text-2xl font-bold font-mono ${scenarioResult.portfolioShockPct < 0 ? 'text-rose-400' : 'text-emerald-400'}`}>
                  {formatPct(scenarioResult.portfolioShockPct)}
                </p>
                <p className="font-mono text-[10px] text-text-tertiary mt-1">Weighted linear estimation</p>
              </div>

              <div className="panel p-4 rounded-xl border border-border">
                <span className="eyebrow text-text-tertiary">Covered Portfolio Weight</span>
                <div className="metric-rule" />
                <p className="text-2xl font-bold font-mono text-emerald-400">
                  {scenarioResult.coveredPortfolioWeightPct.toFixed(1)}%
                </p>
                <p className="font-mono text-[10px] text-text-tertiary mt-1">Classified holdings exposure</p>
              </div>

              <div className="panel p-4 rounded-xl border border-border">
                <span className="eyebrow text-text-tertiary">Unmodeled Exposure</span>
                <div className="metric-rule" />
                <p className={`text-2xl font-bold font-mono ${scenarioResult.unmodeledPortfolioWeightPct > 5 ? 'text-amber-400' : 'text-text-secondary'}`}>
                  {scenarioResult.unmodeledPortfolioWeightPct.toFixed(1)}%
                </p>
                <p className="font-mono text-[10px] text-text-tertiary mt-1">Isolated at 0% return (non-renormalized)</p>
              </div>

              <div className="panel p-4 rounded-xl border border-border">
                <span className="eyebrow text-text-tertiary">Portfolio Beta (OLS)</span>
                <div className="metric-rule" />
                <p className="text-2xl font-bold font-mono text-text-primary">
                  {scenarioResult.portfolioBeta !== null && scenarioResult.portfolioBeta !== undefined
                    ? scenarioResult.portfolioBeta.toFixed(2)
                    : 'N/A'}
                </p>
                <p className="font-mono text-[10px] text-text-tertiary mt-1">
                  {scenarioResult.betaObservationCount} aligned daily observations
                </p>
              </div>
            </div>
          ) : comparisonResult ? (
            /* SIDE-BY-SIDE COMPARISON CARDS */
            <div className="panel p-5 rounded-xl border border-border space-y-4">
              <div className="flex items-center justify-between pb-2 border-b border-border/70">
                <span className="eyebrow text-accent">Scenario Comparison Summary (A vs B)</span>
                <span className="status-badge state-verified text-[10px] font-mono">
                  {comparisonResult.isComparable ? 'COMPARABLE (IDENTICAL BASELINE)' : 'NON-COMPARABLE'}
                </span>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div className="p-4 bg-surface rounded-lg border border-border">
                  <span className="text-[11px] font-mono text-text-tertiary block mb-1">Scenario A ({comparisonResult.scenarioA.scenarioType}):</span>
                  <p className="text-2xl font-bold font-mono text-rose-400">
                    {formatPct(comparisonResult.scenarioA.portfolioShockPct)}
                  </p>
                  <p className="text-[10px] font-mono text-text-tertiary mt-1">{comparisonResult.scenarioA.scenarioName}</p>
                </div>

                <div className="p-4 bg-surface rounded-lg border border-border">
                  <span className="text-[11px] font-mono text-text-tertiary block mb-1">Scenario B ({comparisonResult.scenarioB.scenarioType}):</span>
                  <p className="text-2xl font-bold font-mono text-rose-400">
                    {formatPct(comparisonResult.scenarioB.portfolioShockPct)}
                  </p>
                  <p className="text-[10px] font-mono text-text-tertiary mt-1">{comparisonResult.scenarioB.scenarioName}</p>
                </div>

                <div className="p-4 bg-accent/10 rounded-lg border border-accent/30">
                  <span className="text-[11px] font-mono text-accent block mb-1">Delta Shock Impact (B − A):</span>
                  <p className={`text-2xl font-bold font-mono ${comparisonResult.deltaPortfolioShockPct < 0 ? 'text-rose-400' : 'text-emerald-400'}`}>
                    {formatPct(comparisonResult.deltaPortfolioShockPct)}
                  </p>
                  <p className="text-[10px] font-mono text-text-tertiary mt-1">Variance due solely to shock parameters</p>
                </div>
              </div>

              <p className="text-[11px] font-mono text-text-secondary bg-surface-sunken p-3 rounded border border-border/60">
                <strong>Divergence Explanation:</strong> {comparisonResult.divergenceExplanation}
              </p>
            </div>
          ) : null}

          {/* SUB-TABS: CONSTITUENTS, EXPOSURES, EVIDENCE */}
          <div className="panel p-5 rounded-xl border border-border space-y-4">
            <div className="flex border-b border-border gap-2 font-mono text-[12px]">
              <button
                onClick={() => setResultSubTab('overview')}
                className={`pb-2.5 px-3 border-b-2 font-semibold transition-all ${
                  resultSubTab === 'overview'
                    ? 'border-accent text-accent'
                    : 'border-transparent text-text-tertiary hover:text-text-primary'
                }`}
              >
                01 Constituent Loss Attribution
              </button>
              <button
                onClick={() => setResultSubTab('exposures')}
                className={`pb-2.5 px-3 border-b-2 font-semibold transition-all ${
                  resultSubTab === 'exposures'
                    ? 'border-accent text-accent'
                    : 'border-transparent text-text-tertiary hover:text-text-primary'
                }`}
              >
                02 Exposure Dimension Shocks ({scenarioResult.exposures.length})
              </button>
              <button
                onClick={() => setResultSubTab('evidence')}
                className={`pb-2.5 px-3 border-b-2 font-semibold transition-all ${
                  resultSubTab === 'evidence'
                    ? 'border-accent text-accent'
                    : 'border-transparent text-text-tertiary hover:text-text-primary'
                }`}
              >
                03 Epistemic Evidence & Due-Diligence (6 Core Questions)
              </button>
            </div>

            {/* TAB 1: CONSTITUENT LOSS ATTRIBUTION TABLE */}
            {resultSubTab === 'overview' && (
              <div className="space-y-3">
                <div className="overflow-x-auto">
                  <table className="w-full text-left font-mono text-[11.5px] border-collapse">
                    <thead>
                      <tr className="border-b border-border/80 text-text-tertiary text-[10.5px]">
                        <th className="py-2 px-2">Constituent Fund</th>
                        <th className="py-2 px-2 text-right">Portfolio Weight</th>
                        <th className="py-2 px-2 text-right">Fund Beta</th>
                        <th className="py-2 px-2 text-right">Fund Shock</th>
                        <th className="py-2 px-2 text-right">Weighted Loss Contrib</th>
                        <th className="py-2 px-2 text-right">Covered Weight</th>
                        <th className="py-2 px-2 text-right">Holdings Snapshot</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border/40">
                      {scenarioResult.constituents.map(c => (
                        <tr key={c.schemeOptionId} className="hover:bg-surface-raised/60">
                          <td className="py-2.5 px-2 font-medium text-text-primary">
                            <div>{c.fundName}</div>
                            <span className="text-[10px] text-text-tertiary">{c.amcName}</span>
                          </td>
                          <td className="py-2.5 px-2 text-right text-text-secondary">
                            {c.normalizedWeightPct.toFixed(1)}%
                          </td>
                          <td className="py-2.5 px-2 text-right text-text-primary">
                            {c.fundBeta !== null && c.fundBeta !== undefined ? c.fundBeta.toFixed(2) : '—'}
                          </td>
                          <td className={`py-2.5 px-2 text-right font-bold ${c.constituentShockPct < 0 ? 'text-rose-400' : 'text-emerald-400'}`}>
                            {formatPct(c.constituentShockPct)}
                          </td>
                          <td className={`py-2.5 px-2 text-right font-bold ${c.weightedContributionPct < 0 ? 'text-rose-400' : 'text-emerald-400'}`}>
                            {formatPct(c.weightedContributionPct)}
                          </td>
                          <td className="py-2.5 px-2 text-right">
                            <span className={c.unmodeledHoldingsWeightPct > 5 ? 'text-amber-400 font-semibold' : 'text-text-secondary'}>
                              {c.coveredHoldingsWeightPct.toFixed(1)}%
                            </span>
                          </td>
                          <td className="py-2.5 px-2 text-right text-text-tertiary text-[10px]">
                            {c.holdingsSnapshotDate || '—'}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                    <tfoot>
                      <tr className="border-t-2 border-border font-bold bg-surface-sunken">
                        <td className="py-2 px-2 text-text-primary">Portfolio Total</td>
                        <td className="py-2 px-2 text-right">100.0%</td>
                        <td className="py-2 px-2 text-right">{scenarioResult.portfolioBeta?.toFixed(2) || '—'}</td>
                        <td className="py-2 px-2 text-right text-rose-400">{formatPct(scenarioResult.portfolioShockPct)}</td>
                        <td className="py-2 px-2 text-right text-rose-400">{formatPct(scenarioResult.portfolioShockPct)}</td>
                        <td className="py-2 px-2 text-right">{scenarioResult.coveredPortfolioWeightPct.toFixed(1)}%</td>
                        <td className="py-2 px-2 text-right text-text-tertiary">—</td>
                      </tr>
                    </tfoot>
                  </table>
                </div>

                <div className="text-[10.5px] font-mono text-text-tertiary pt-2">
                  * Note: Reconciled constituent loss contributions sum exactly to the estimated portfolio impact.
                </div>
              </div>
            )}

            {/* TAB 2: EXPOSURE DIMENSION SHOCKS */}
            {resultSubTab === 'exposures' && (
              <div className="space-y-3">
                <div className="overflow-x-auto">
                  <table className="w-full text-left font-mono text-[11.5px] border-collapse">
                    <thead>
                      <tr className="border-b border-border/80 text-text-tertiary text-[10.5px]">
                        <th className="py-2 px-2">Exposure Dimension</th>
                        <th className="py-2 px-2">Category</th>
                        <th className="py-2 px-2 text-right">Portfolio Weight</th>
                        <th className="py-2 px-2 text-right">Assumed Shock</th>
                        <th className="py-2 px-2 text-right">Weighted Contribution</th>
                        <th className="py-2 px-2 text-center">Status</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border/40">
                      {scenarioResult.exposures.map((e, idx) => (
                        <tr key={idx} className="hover:bg-surface-raised/60">
                          <td className="py-2.5 px-2 font-medium text-text-primary">
                            {e.dimensionName}
                          </td>
                          <td className="py-2.5 px-2 text-text-tertiary text-[10.5px]">
                            {e.dimensionCategory}
                          </td>
                          <td className="py-2.5 px-2 text-right text-text-secondary">
                            {e.portfolioWeightPct.toFixed(1)}%
                          </td>
                          <td className={`py-2.5 px-2 text-right font-bold ${e.shockPct < 0 ? 'text-rose-400' : e.shockPct > 0 ? 'text-emerald-400' : 'text-text-tertiary'}`}>
                            {formatPct(e.shockPct)}
                          </td>
                          <td className={`py-2.5 px-2 text-right font-bold ${e.weightedContributionPct < 0 ? 'text-rose-400' : e.weightedContributionPct > 0 ? 'text-emerald-400' : 'text-text-tertiary'}`}>
                            {formatPct(e.weightedContributionPct)}
                          </td>
                          <td className="py-2.5 px-2 text-center">
                            <span className={`status-badge text-[9px] ${
                              e.coverageState === 'COVERED' ? 'state-verified' :
                              e.coverageState === 'UNMODELED' ? 'state-candidate' : 'bg-surface text-text-tertiary'
                            }`}>
                              {e.coverageState}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}

            {/* TAB 3: EPISTEMIC EVIDENCE & DUE-DILIGENCE PANEL */}
            {resultSubTab === 'evidence' && (
              <div className="space-y-5 text-sans">
                {/* 6 Investor Due-Diligence Questions Answered */}
                <div className="space-y-3">
                  <h4 className="font-semibold text-[13px] font-mono text-accent">
                    Evidence-Based Due-Diligence Checklist:
                  </h4>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                    {scenarioResult.investigationQuestions.map((q, idx) => (
                      <div key={idx} className="p-3 bg-surface-sunken rounded-lg border border-border/70 space-y-1">
                        <span className="text-[10px] font-mono text-text-tertiary block">Investigation Point #{idx + 1}</span>
                        <p className="text-[12px] font-medium text-text-primary">{q}</p>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Methodological Assumptions */}
                <div className="space-y-2 pt-3 border-t border-border/60">
                  <h4 className="font-semibold text-[12px] font-mono text-text-secondary">
                    Mathematical Assumptions Applied:
                  </h4>
                  <ul className="list-disc pl-5 space-y-1 text-[11.5px] font-mono text-text-tertiary">
                    {scenarioResult.assumptions.map((a, idx) => (
                      <li key={idx}>{a}</li>
                    ))}
                  </ul>
                </div>

                {/* Data Limitations & Why Estimates May Be Wrong */}
                <div className="space-y-2 pt-3 border-t border-border/60">
                  <h4 className="font-semibold text-[12px] font-mono text-amber-400">
                    Why This Sensitivity Estimate May Be Inaccurate (Known Omissions):
                  </h4>
                  <ul className="list-disc pl-5 space-y-1 text-[11.5px] font-mono text-text-secondary">
                    {scenarioResult.dataLimitations.map((l, idx) => (
                      <li key={idx} className="text-amber-300/90">{l}</li>
                    ))}
                  </ul>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
