'use client';

import React, { useEffect, useState, useCallback } from 'react';
import Link from 'next/link';
import {
  fetchMarketDataFreshness,
  MarketDataFreshnessDto,
  SourceDomainFreshnessDto,
} from '@/lib/api/freshness';
import {
  fetchOperationalJobs,
  fetchPlatformReadiness,
  OperationalJobExecutionDto,
  V1PlatformReadinessDto,
} from '@/lib/api/investor';
import { SourcePublicationCalendarWidget } from './SourcePublicationCalendarWidget';

export function DataStatusDashboard() {
  const [data, setData] = useState<MarketDataFreshnessDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastRefreshedAt, setLastRefreshedAt] = useState<string | null>(null);
  const [jobs, setJobs] = useState<OperationalJobExecutionDto[]>([]);
  const [readiness, setReadiness] = useState<V1PlatformReadinessDto | null>(null);

  const loadFreshness = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [res, jobList, readData] = await Promise.all([
        fetchMarketDataFreshness(),
        fetchOperationalJobs().catch(() => []),
        fetchPlatformReadiness().catch(() => null),
      ]);
      setData(res);
      setJobs(jobList);
      setReadiness(readData);
      setLastRefreshedAt(new Date().toLocaleTimeString('en-IN', { hour12: false }));
    } catch (err: unknown) {
      const msg = err && typeof err === 'object' && 'message' in err
        ? String((err as { message: unknown }).message)
        : 'Failed to retrieve market data freshness. Ensure the backend orchestrator is running.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadFreshness();
  }, [loadFreshness]);

  const getStatusBadge = (status: string) => {
    const s = (status || '').toUpperCase();
    if (s === 'CURRENT' || s === 'FRESH' || s === 'OPERATIONAL') {
      return (
        <span className="inline-flex items-center gap-1.5 rounded-full border border-emerald-500/30 bg-emerald-950/40 px-2.5 py-0.5 font-mono text-[11px] font-medium text-emerald-400">
          <span className="h-1.5 w-1.5 rounded-full bg-emerald-400 animate-pulse" />
          {status}
        </span>
      );
    }
    if (s === 'PARTIAL' || s === 'DATA_QUALITY_LIMITED') {
      return (
        <span className="inline-flex items-center gap-1.5 rounded-full border border-amber-500/30 bg-amber-950/40 px-2.5 py-0.5 font-mono text-[11px] font-medium text-amber-300">
          <span className="h-1.5 w-1.5 rounded-full bg-amber-400" />
          {status}
        </span>
      );
    }
    if (s === 'STALE') {
      return (
        <span className="inline-flex items-center gap-1.5 rounded-full border border-orange-500/30 bg-orange-950/40 px-2.5 py-0.5 font-mono text-[11px] font-medium text-orange-400">
          <span className="h-1.5 w-1.5 rounded-full bg-orange-400" />
          STALE
        </span>
      );
    }
    return (
      <span className="inline-flex items-center gap-1.5 rounded-full border border-rose-500/30 bg-rose-950/40 px-2.5 py-0.5 font-mono text-[11px] font-medium text-rose-400">
        <span className="h-1.5 w-1.5 rounded-full bg-rose-400" />
        {status || 'UNAVAILABLE'}
      </span>
    );
  };

  return (
    <div className="mx-auto w-full max-w-[1440px] px-4 py-8 sm:px-6 lg:px-10">
      {/* Page Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between border-b border-border-gold-subtle pb-6">
        <div>
          <div className="flex items-center gap-2">
            <span className="font-mono text-xs uppercase tracking-widest text-primary font-semibold">
              OPERATIONAL VISIBILITY & AUDIT TRAIL
            </span>
            <span className="rounded bg-surface-navy-raised px-2 py-0.5 font-mono text-[10px] text-text-cream-muted border border-border-gold-subtle">
              V1 LIVE LEDGER
            </span>
          </div>
          <h1 className="mt-1 font-sans text-2xl font-bold tracking-tight text-text-cream-bright sm:text-3xl">
            Market Data Freshness & Source Availability
          </h1>
          <p className="mt-1 text-sm text-text-cream-muted max-w-3xl">
            Authoritative operational status across mutual fund NAVs, category benchmarks, scheme universe, AMC portfolio disclosures, and calculation runs.
          </p>
        </div>

        <div className="flex items-center gap-3 shrink-0">
          {lastRefreshedAt && (
            <span className="font-mono text-xs text-text-cream-muted hidden sm:inline">
              Checked: {lastRefreshedAt} IST
            </span>
          )}
          <button
            type="button"
            onClick={() => void loadFreshness()}
            disabled={loading}
            className="inline-flex items-center gap-2 rounded-full border border-border-gold-subtle bg-surface-navy-raised px-4 py-2 font-sans text-xs font-semibold text-text-cream-bright hover:border-border-gold-bright hover:bg-surface-navy-hover transition-all disabled:opacity-50"
          >
            <svg
              className={`h-3.5 w-3.5 text-primary ${loading ? 'animate-spin' : ''}`}
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            {loading ? 'Re-probing…' : 'Refresh Status'}
          </button>
        </div>
      </div>

      {/* Epistemic Non-Advisory Governance Disclaimer */}
      <div className="mt-6 rounded-lg border border-border-gold-subtle/80 bg-surface-navy-raised/50 p-4 backdrop-blur-sm">
        <div className="flex items-start gap-3">
          <span className="text-primary text-lg shrink-0 mt-0.5">ℹ️</span>
          <div className="space-y-1">
            <h2 className="text-xs font-semibold uppercase tracking-wider text-primary font-mono">
              EPISTEMIC PRINCIPLE: OPERATIONAL STATUS IS NOT AN INVESTMENT SCORE
            </h2>
            <p className="text-xs leading-relaxed text-text-cream-muted">
              {data?.governanceDisclaimer ||
                "Market data freshness evaluates temporal availability of mutual fund NAVs, category benchmark TRIs, and FBIL risk-free yields against the trading calendar. Operational issues or ingestion latencies describe data availability only and must not be interpreted as investment performance or portfolio weakness."}
            </p>
          </div>
        </div>
      </div>

      {/* Error State */}
      {error && (
        <div className="mt-6 rounded-lg border border-rose-500/40 bg-rose-950/20 p-5 text-rose-300">
          <div className="flex items-center gap-2 font-semibold">
            <span>⚠️</span>
            <span>Failed to Connect to Ingestion Diagnostics</span>
          </div>
          <p className="mt-1 text-xs text-rose-200/80">{error}</p>
          <button
            type="button"
            onClick={() => void loadFreshness()}
            className="mt-3 inline-flex items-center gap-1.5 rounded bg-rose-900/60 px-3 py-1 text-xs font-medium text-rose-100 hover:bg-rose-900 border border-rose-500/30"
          >
            Retry Diagnostics Connection
          </button>
        </div>
      )}

      {/* Loading Skeleton */}
      {loading && !data && (
        <div className="mt-8 space-y-6">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {[1, 2, 3, 4, 5, 6].map((i) => (
              <div key={i} className="h-32 animate-pulse rounded-lg border border-border-gold-subtle/40 bg-surface-navy-raised/30" />
            ))}
          </div>
          <div className="h-64 animate-pulse rounded-lg border border-border-gold-subtle/40 bg-surface-navy-raised/30" />
        </div>
      )}

      {/* Data Loaded Display */}
      {data && (
        <div className="mt-8 space-y-8">
          {/* Key Metric Overview Cards */}
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {/* Card 1: AMFI Daily NAVs */}
            <div className="rounded-lg border border-border-gold-subtle bg-surface-navy-raised/60 p-5 flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between">
                  <span className="font-mono text-[11px] font-semibold uppercase text-text-cream-muted">
                    AMFI Daily NAVs
                  </span>
                  {getStatusBadge(data.navFreshness.freshnessState)}
                </div>
                <div className="mt-3 flex items-baseline gap-2">
                  <span className="font-mono text-2xl font-bold text-text-cream-bright">
                    {data.navFreshness.latestNavDate || '—'}
                  </span>
                  {data.navFreshness.daysLag !== null && (
                    <span className="font-mono text-xs text-text-cream-muted">
                      ({data.navFreshness.daysLag}d lag)
                    </span>
                  )}
                </div>
                <p className="mt-2 text-xs text-text-cream-muted leading-snug">
                  {data.navFreshness.referenceUniverseSufficient3Y} of {data.navFreshness.referenceUniverseTotalOptions} schemes meet strict 3Y sufficiency (≥700 trading days).
                </p>
              </div>
              <div className="mt-4 pt-3 border-t border-border-gold-subtle/40 flex items-center justify-between font-mono text-[11px] text-text-cream-muted">
                <span>Total Observations:</span>
                <span className="font-semibold text-primary">{data.navFreshness.totalObservationsInLedger.toLocaleString('en-IN')}</span>
              </div>
            </div>

            {/* Card 2: NSE Benchmark TRIs */}
            <div className="rounded-lg border border-border-gold-subtle bg-surface-navy-raised/60 p-5 flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between">
                  <span className="font-mono text-[11px] font-semibold uppercase text-text-cream-muted">
                    Category Benchmarks (TRI)
                  </span>
                  {getStatusBadge(
                    Object.values(data.benchmarkFreshnessByCategory || {}).some((b) => b.freshnessState === 'STALE')
                      ? 'STALE'
                      : 'CURRENT'
                  )}
                </div>
                <div className="mt-3 flex items-baseline gap-2">
                  <span className="font-mono text-2xl font-bold text-text-cream-bright">
                    5 Categories
                  </span>
                  <span className="font-mono text-xs text-text-cream-muted">
                    (TRI series)
                  </span>
                </div>
                <p className="mt-2 text-xs text-text-cream-muted leading-snug">
                  Nifty 100, Nifty LargeMidcap 250, Nifty Midcap 150, Nifty Smallcap 250, and Nifty 500 Total Return Indices.
                </p>
              </div>
              <div className="mt-4 pt-3 border-t border-border-gold-subtle/40 flex items-center justify-between font-mono text-[11px] text-text-cream-muted">
                <span>Risk-Free 91D T-Bill:</span>
                <span className="font-semibold text-primary">
                  {data.riskFreeFreshness?.latestRate ? `${data.riskFreeFreshness.latestRate.toFixed(2)}%` : 'Active'}
                </span>
              </div>
            </div>

            {/* Card 3: Reference Universe */}
            <div className="rounded-lg border border-border-gold-subtle bg-surface-navy-raised/60 p-5 flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between">
                  <span className="font-mono text-[11px] font-semibold uppercase text-text-cream-muted">
                    Reference Equity Universe
                  </span>
                  {getStatusBadge(data.universeFreshness.syncStatus)}
                </div>
                <div className="mt-3 flex items-baseline gap-2">
                  <span className="font-mono text-2xl font-bold text-text-cream-bright">
                    {data.universeFreshness.totalEligibleReferenceSchemes} Schemes
                  </span>
                  <span className="font-mono text-xs text-text-cream-muted">
                    (Direct-Growth)
                  </span>
                </div>
                <p className="mt-2 text-xs text-text-cream-muted leading-snug">
                  Active Direct-Growth equity schemes partitioned across Large (39), Large&Mid (36), Mid (34), Small (37), Flexi (47).
                </p>
              </div>
              <div className="mt-4 pt-3 border-t border-border-gold-subtle/40 flex items-center justify-between font-mono text-[11px] text-text-cream-muted">
                <span>AMFI Verified Master:</span>
                <span className="font-semibold text-emerald-400">100% Canonical</span>
              </div>
            </div>

            {/* Card 4: Monthly AMC Holdings */}
            <div className="rounded-lg border border-border-gold-subtle bg-surface-navy-raised/60 p-5 flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between">
                  <span className="font-mono text-[11px] font-semibold uppercase text-text-cream-muted">
                    Monthly Holdings Disclosures
                  </span>
                  {getStatusBadge(data.holdingsFreshness.overallHoldingsState)}
                </div>
                <div className="mt-3 flex items-baseline gap-2">
                  <span className="font-mono text-2xl font-bold text-text-cream-bright">
                    {data.holdingsFreshness.schemesWithUsableHoldings} / {data.holdingsFreshness.totalReferenceUniverse}
                  </span>
                  <span className="font-mono text-xs text-text-cream-muted">
                    schemes
                  </span>
                </div>
                <p className="mt-2 text-xs text-text-cream-muted leading-snug">
                  Verified disclosures for supported AMCs (HDFC, PPFAS). Zero-substitution prohibited: missing disclosures cannot be assumed 0% overlap.
                </p>
              </div>
              <div className="mt-4 pt-3 border-t border-border-gold-subtle/40 flex items-center justify-between font-mono text-[11px] text-text-cream-muted">
                <span>Latest Disclosure:</span>
                <span className="font-semibold text-primary">{data.holdingsFreshness.latestReportedPortfolioDate || 'Historical'}</span>
              </div>
            </div>

            {/* Card 5: Quantitative Metric Calculations */}
            <div className="rounded-lg border border-border-gold-subtle bg-surface-navy-raised/60 p-5 flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between">
                  <span className="font-mono text-[11px] font-semibold uppercase text-text-cream-muted">
                    Quant Engine (Port 8001)
                  </span>
                  {getStatusBadge(data.metricsFreshness.status)}
                </div>
                <div className="mt-3 flex items-baseline gap-2">
                  <span className="font-mono text-2xl font-bold text-text-cream-bright">
                    {data.metricsFreshness.totalCalculationRuns} Runs
                  </span>
                  <span className="font-mono text-xs text-text-cream-muted">
                    recorded
                  </span>
                </div>
                <p className="mt-2 text-xs text-text-cream-muted leading-snug">
                  Deterministic vectorized kernel executing RET-02, RET-03, RSK-01, RSK-02, RSK-03, RAT-01, RAT-02, RET-07.
                </p>
              </div>
              <div className="mt-4 pt-3 border-t border-border-gold-subtle/40 flex items-center justify-between font-mono text-[11px] text-text-cream-muted">
                <span>Kernel Version:</span>
                <span className="font-semibold text-primary">{data.metricsFreshness.calculationEngineVersion}</span>
              </div>
            </div>

            {/* Card 6: Investor CAS Import Engine */}
            <div className="rounded-lg border border-border-gold-subtle bg-surface-navy-raised/60 p-5 flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between">
                  <span className="font-mono text-[11px] font-semibold uppercase text-text-cream-muted">
                    Investor CAS Parser
                  </span>
                  {getStatusBadge(data.casProcessingStatus.engineStatus)}
                </div>
                <div className="mt-3 flex items-baseline gap-2">
                  <span className="font-mono text-2xl font-bold text-text-cream-bright">
                    {data.casProcessingStatus.totalImportedStatements} Statements
                  </span>
                  <span className="font-mono text-xs text-text-cream-muted">
                    processed
                  </span>
                </div>
                <p className="mt-2 text-xs text-text-cream-muted leading-snug">
                  CAMS & KFintech PDF parser operational with in-flight password decryption. External positions kept separate from fund disclosures.
                </p>
              </div>
              <div className="mt-4 pt-3 border-t border-border-gold-subtle/40 flex items-center justify-between font-mono text-[11px] text-text-cream-muted">
                <span>Supported Registrars:</span>
                <span className="font-semibold text-primary">CAMS & KFintech</span>
              </div>
            </div>
          </div>

          {/* Operational Domains & Audit Lineage Table */}
          <div className="rounded-lg border border-border-gold-subtle bg-surface-navy-raised/40 p-6">
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between pb-4 border-b border-border-gold-subtle/60 gap-2">
              <div>
                <h3 className="text-base font-bold text-text-cream-bright">
                  Source Domains & Audit Provenance Ledger
                </h3>
                <p className="text-xs text-text-cream-muted">
                  Deterministic record of observation dates, ingestion timestamps, records covered, and source limitations.
                </p>
              </div>
              <span className="font-mono text-[11px] text-text-cream-muted">
                {data.domainSummaries?.length || 6} Registered Ingestion Domains
              </span>
            </div>

            <div className="mt-4 overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="border-b border-border-gold-subtle/40 font-mono text-[11px] uppercase text-text-cream-muted">
                    <th className="py-2.5 px-3">Domain</th>
                    <th className="py-2.5 px-3">Latest Observation</th>
                    <th className="py-2.5 px-3">Ingested / Evaluated</th>
                    <th className="py-2.5 px-3">Coverage (Units)</th>
                    <th className="py-2.5 px-3">Status</th>
                    <th className="py-2.5 px-3">Limitation & Governance Details</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border-gold-subtle/20 font-sans text-xs">
                  {data.domainSummaries?.map((domain: SourceDomainFreshnessDto) => (
                    <tr key={domain.domainIdentifier} className="hover:bg-surface-navy-raised/50 transition-colors">
                      <td className="py-3 px-3">
                        <div className="font-semibold text-text-cream-bright">{domain.displayName}</div>
                        <div className="font-mono text-[10px] text-primary/80">{domain.domainIdentifier}</div>
                      </td>
                      <td className="py-3 px-3 font-mono text-text-cream-body whitespace-nowrap">
                        {domain.latestObservationDate || '—'}
                      </td>
                      <td className="py-3 px-3 font-mono text-[11px] text-text-cream-muted whitespace-nowrap">
                        {domain.latestSuccessfulIngestionTime
                          ? new Date(domain.latestSuccessfulIngestionTime).toLocaleString('en-IN', {
                              dateStyle: 'short',
                              timeStyle: 'short',
                              hour12: false,
                            })
                          : 'Operational'}
                      </td>
                      <td className="py-3 px-3 font-mono text-text-cream-body whitespace-nowrap">
                        <span className="font-semibold text-text-cream-bright">
                          {domain.recordsOrSchemesCovered.toLocaleString('en-IN')}
                        </span>
                        {domain.totalTargetDenominator > 0 && (
                          <span className="text-text-cream-muted">
                            {' '}
                            / {domain.totalTargetDenominator.toLocaleString('en-IN')}
                          </span>
                        )}
                      </td>
                      <td className="py-3 px-3 whitespace-nowrap">
                        {getStatusBadge(domain.status)}
                      </td>
                      <td className="py-3 px-3 max-w-md text-text-cream-muted text-xs leading-relaxed">
                        {domain.limitationExplanation}
                        {domain.sourceProvenanceOrRunId && (
                          <div className="mt-1 font-mono text-[10px] text-primary/70 truncate max-w-sm" title={domain.sourceProvenanceOrRunId}>
                            Provenance: {domain.sourceProvenanceOrRunId}
                          </div>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* AMC Holdings Discovery Status Matrix */}
          <div className="rounded-lg border border-border-gold-subtle bg-surface-navy-raised/40 p-6">
            <h3 className="text-base font-bold text-text-cream-bright">
              Official AMC Monthly Portfolio Disclosure Matrix
            </h3>
            <p className="mt-1 text-xs text-text-cream-muted max-w-3xl">
              Holdings disclosures are ingested from official AMC statutory publications per SEBI guidelines (within 10 days of month close).
              Where AMC web portals restrict automated web access via Cloudflare or bot protection, direct upload of official disclosures ensures data authenticity without guessing.
            </p>

            <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
              {Object.entries(data.holdingsFreshness.amcCoverageStatus || {}).map(([amc, st]) => (
                <div key={amc} className="rounded border border-border-gold-subtle/50 bg-surface-midnight/60 p-3 text-center">
                  <div className="font-mono text-xs font-bold text-text-cream-bright">{amc.replace('_MF', '')}</div>
                  <div className="mt-2">{getStatusBadge(st)}</div>
                </div>
              ))}
            </div>
          </div>

          {/* Source Publication & Expected vs Observed Arrival Calendar (Task 215) */}
          <SourcePublicationCalendarWidget />

          {/* Analytical Impact & Horizon Limitation Matrix */}
          <div className="rounded-lg border border-border-gold-subtle bg-surface-navy-raised/40 p-6">
            <h3 className="text-base font-bold text-text-cream-bright">
              How Operational Data Status Connects to Analytical Limitations
            </h3>
            <p className="mt-1 text-xs text-text-cream-muted max-w-3xl">
              YUKIRA refuses to fabricate missing data or smooth market observations. Understanding data boundaries is critical to interpreting quantitative outputs.
            </p>

            <div className="mt-5 grid grid-cols-1 gap-4 md:grid-cols-2">
              <div className="rounded border border-border-gold-subtle/50 bg-surface-midnight/60 p-4">
                <div className="flex items-center gap-2 font-mono text-xs font-bold text-primary">
                  <span>📅</span> NAV Sufficiency & Horizon Limitations
                </div>
                <p className="mt-2 text-xs text-text-cream-muted leading-relaxed">
                  Historical return and risk metrics strictly enforce the 700 trading day threshold for 3-year evaluations. Funds launched within the last 3 years or exhibiting historical data discontinuities display explicit <code className="text-amber-400">INSUFFICIENT_DATA</code> states rather than misleading truncated CAGR.
                </p>
              </div>

              <div className="rounded border border-border-gold-subtle/50 bg-surface-midnight/60 p-4">
                <div className="flex items-center gap-2 font-mono text-xs font-bold text-primary">
                  <span>📊</span> Benchmark TRI Alignment
                </div>
                <p className="mt-2 text-xs text-text-cream-muted leading-relaxed">
                  Tracking error (RET-07), Alpha (REL-01), and Beta (RSK-04) require point-in-time alignment between fund NAVs and primary category TRI benchmarks. When benchmark feeds have latency, benchmark-relative metrics are temporarily withheld to prevent cross-date estimation error.
                </p>
              </div>

              <div className="rounded border border-border-gold-subtle/50 bg-surface-midnight/60 p-4">
                <div className="flex items-center gap-2 font-mono text-xs font-bold text-primary">
                  <span>🔒</span> Prohibition of Zero-Substitution in Overlap
                </div>
                <p className="mt-2 text-xs text-text-cream-muted leading-relaxed">
                  In portfolio overlap analysis, a scheme lacking monthly holdings disclosure cannot be assumed to have 0% overlap with another fund. YUKIRA renders an explicit unavailable disclosure warning rather than giving investors a false impression of total portfolio diversification.
                </p>
              </div>

              <div className="rounded border border-border-gold-subtle/50 bg-surface-midnight/60 p-4">
                <div className="flex items-center gap-2 font-mono text-xs font-bold text-primary">
                  <span>⏱️</span> Disclosure Asynchrony Notice
                </div>
                <p className="mt-2 text-xs text-text-cream-muted leading-relaxed">
                  When comparing funds with holdings reported as of different month ends (e.g., September 30 vs August 31), YUKIRA alerts allocators to potential subsequent rebalancing differences.
                </p>
              </div>
            </div>
          </div>

          {/* V1 Platform Governance & Release Readiness (Task 209) */}
          {readiness && (
            <div className="rounded-xl border border-emerald-500/30 bg-emerald-950/20 p-5 space-y-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="h-2.5 w-2.5 rounded-full bg-emerald-400 animate-pulse" />
                  <h3 className="font-mono text-sm font-bold text-emerald-400">
                    V1 Platform Release Readiness & Methodology Governance Lock (Task 209)
                  </h3>
                </div>
                <span className="px-2 py-0.5 rounded text-[10px] font-mono font-bold bg-emerald-500/20 text-emerald-300 border border-emerald-500/40">
                  {readiness.releaseStatus} ({readiness.platformVersion})
                </span>
              </div>
              <p className="text-xs text-zinc-300">
                Governance State: <strong>{readiness.methodologyGovernanceStatus}</strong> · Approved Operational Baseline: <strong>{readiness.baselineOperationalMetric}</strong> · Candidate Metrics in Validation: <strong>{readiness.totalCandidateMetricsCount}</strong>
              </p>
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 pt-2 text-[11px] font-mono">
                {Object.entries(readiness.subsystemHealth).map(([sub, status]) => (
                  <div key={sub} className="p-2 bg-zinc-950/60 border border-zinc-800 rounded">
                    <span className="text-[10px] text-zinc-500 uppercase block">{sub.replace(/_/g, ' ')}</span>
                    <span className="text-zinc-200">{status}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Operational Background Jobs Telemetry (Task 207) */}
          <div className="rounded-xl border border-border-gold-subtle/50 bg-surface-obsidian/60 p-5 space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="font-sans text-base font-bold text-text-cream-bright flex items-center gap-2">
                  <span className="w-2 h-2 rounded-full bg-cyan-400" />
                  Background Job Execution Telemetry & Health (Task 207)
                </h3>
                <p className="text-xs text-text-cream-muted mt-0.5">
                  Scheduled monitoring sweeps, outbox deliveries, and market ingestion execution runs.
                </p>
              </div>
            </div>

            {jobs.length === 0 ? (
              <div className="text-xs text-text-cream-muted py-4 text-center">
                Continuous background scheduler active. Sweep runs execute on configured intervals.
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs font-mono">
                  <thead className="bg-zinc-900/40 border-b border-zinc-800 text-[10px] text-zinc-400 uppercase">
                    <tr>
                      <th className="py-2 px-3">Job Name</th>
                      <th className="py-2 px-3">Trigger</th>
                      <th className="py-2 px-3">Status</th>
                      <th className="py-2 px-3">Started</th>
                      <th className="py-2 px-3 text-right">Processed</th>
                      <th className="py-2 px-3 text-right">Failed</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-zinc-800/60 text-[11px]">
                    {jobs.slice(0, 8).map((j) => (
                      <tr key={j.id} className="hover:bg-zinc-900/30">
                        <td className="py-2 px-3 font-semibold text-zinc-200">{j.jobName}</td>
                        <td className="py-2 px-3 text-zinc-400">{j.executionTrigger}</td>
                        <td className="py-2 px-3">
                          <span
                            className={`px-1.5 py-0.5 rounded text-[9px] font-semibold ${
                              j.executionStatus === 'SUCCESS'
                                ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                                : j.executionStatus === 'RUNNING'
                                ? 'bg-cyan-500/20 text-cyan-400 border border-cyan-500/30'
                                : 'bg-rose-500/20 text-rose-400 border border-rose-500/30'
                            }`}
                          >
                            {j.executionStatus}
                          </span>
                        </td>
                        <td className="py-2 px-3 text-zinc-400">{new Date(j.startedAt).toLocaleTimeString()}</td>
                        <td className="py-2 px-3 text-right text-emerald-400 font-semibold">{j.recordsProcessed}</td>
                        <td className="py-2 px-3 text-right text-rose-400">{j.recordsFailed}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* Quick Navigation Footer */}
          <div className="flex flex-wrap items-center justify-between gap-4 pt-4 border-t border-border-gold-subtle">
            <Link
              href="/funds"
              className="inline-flex items-center gap-1.5 text-xs font-medium text-primary hover:underline"
            >
              ← Return to Fund Discovery & Catalog
            </Link>
            <div className="flex items-center gap-4 text-xs text-text-cream-muted font-mono">
              <span>PostgreSQL 17 Bitemporal Store</span>
              <span>·</span>
              <span>Quant Engine v0.1.0-alpha</span>
              <span>·</span>
              <span>Spring Boot 4.1.1</span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

