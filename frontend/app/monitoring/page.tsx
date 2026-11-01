'use client';

import React, { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import {
  MonitoringOverviewDto,
  MonitoringEventDto,
  MonitoringConditionDto,
  MonitoringExecutionDto,
  getMonitoringOverview,
  getMonitoringEvents,
  getMonitoringConditions,
  getLatestMonitoringExecution,
  runMonitoringScan,
  toggleMonitoringCondition,
  deleteMonitoringCondition,
  syncThesesToMonitoringConditions,
  CLASSIFICATION_META,
  SEVERITY_META,
  LIFECYCLE_META,
  MonitoringSeverity,
  MonitoringLifecycleStatus,
} from '@/lib/api/monitoring';
import { MonitoringEventDetailModal } from '@/components/monitoring/MonitoringEventDetailModal';
import { CreateConditionModal } from '@/components/monitoring/CreateConditionModal';
import { ConditionHistoryModal } from '@/components/monitoring/ConditionHistoryModal';
import { NotificationCentreModal } from '@/components/monitoring/NotificationCentreModal';
import { MonitoringOutboxWidget } from '@/components/monitoring/MonitoringOutboxWidget';

export default function MonitoringDashboardPage() {
  const [overview, setOverview] = useState<MonitoringOverviewDto | null>(null);
  const [latestExecution, setLatestExecution] = useState<MonitoringExecutionDto | null>(null);
  const [events, setEvents] = useState<MonitoringEventDto[]>([]);
  const [conditions, setConditions] = useState<MonitoringConditionDto[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [scanning, setScanning] = useState(false);
  const [scanMessage, setScanMessage] = useState<string | null>(null);

  // Filters & Tabs
  const [activeTab, setActiveTab] = useState<'alerts' | 'conditions' | 'investigating' | 'execution'>('alerts');
  const [severityFilter, setSeverityFilter] = useState<string>('ALL');
  const [classificationFilter, setClassificationFilter] = useState<string>('ALL');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [scopeFilter, setScopeFilter] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState<string>('');

  // Modals
  const [selectedEvent, setSelectedEvent] = useState<MonitoringEventDto | null>(null);
  const [isConditionModalOpen, setIsConditionModalOpen] = useState(false);
  const [isNotificationModalOpen, setIsNotificationModalOpen] = useState(false);
  const [selectedConditionForHistory, setSelectedConditionForHistory] = useState<{ id: number; name: string } | null>(null);

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      const [ov, exec, conds] = await Promise.all([
        getMonitoringOverview().catch(() => null),
        getLatestMonitoringExecution().catch(() => null),
        getMonitoringConditions().catch(() => []),
      ]);
      setOverview(ov);
      setLatestExecution(exec);
      setConditions(conds);

      // Fetch events with filters
      const pageRes = await getMonitoringEvents({
        severity: severityFilter !== 'ALL' ? severityFilter : undefined,
        classification: classificationFilter !== 'ALL' ? classificationFilter : undefined,
        status: activeTab === 'investigating'
          ? undefined
          : (statusFilter !== 'ALL' ? statusFilter : undefined),
        scope: scopeFilter !== 'ALL' ? scopeFilter : undefined,
        size: 50,
      }).catch(() => ({ content: [], totalElements: 0, totalPages: 0, size: 50, number: 0 }));

      let filtered = pageRes.content;
      if (activeTab === 'investigating') {
        filtered = filtered.filter(
          (e) => e.lifecycleStatus === 'NEW' || e.lifecycleStatus === 'ACKNOWLEDGED' || e.lifecycleStatus === 'INVESTIGATING'
        );
      }
      setEvents(filtered);
      setTotalElements(pageRes.totalElements);
    } catch (err) {
      console.error('Failed to load monitoring data', err);
    } finally {
      setLoading(false);
    }
  }, [severityFilter, classificationFilter, statusFilter, scopeFilter, activeTab]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleRunScan = async () => {
    setScanning(true);
    setScanMessage(null);
    try {
      const res = await runMonitoringScan();
      setScanMessage(
        `Evaluation complete: Evaluated ${res.evaluatedFundsCount} funds & ${res.evaluatedConditionsCount} conditions. ${res.newEventsDetectedCount} new/updated alerts recorded.`
      );
      await loadData();
    } catch (err) {
      console.error('Scan execution error', err);
      setScanMessage('Failed to execute monitoring scan. Please check backend connectivity.');
    } finally {
      setScanning(false);
    }
  };

  const handleSyncTheses = async () => {
    try {
      const count = await syncThesesToMonitoringConditions();
      setScanMessage(`Imported ${count} new reconsideration condition(s) from your Research Theses.`);
      await loadData();
    } catch (err) {
      console.error('Thesis sync error', err);
    }
  };

  const handleToggleCondition = async (id: number) => {
    try {
      const updated = await toggleMonitoringCondition(id);
      setConditions((prev) => prev.map((c) => (c.id === id ? updated : c)));
    } catch (err) {
      console.error('Failed to toggle condition', err);
    }
  };

  const handleDeleteCondition = async (id: number) => {
    try {
      await deleteMonitoringCondition(id);
      setConditions((prev) => prev.filter((c) => c.id !== id));
    } catch (err) {
      console.error('Failed to delete condition', err);
    }
  };

  // Client-side text search filter
  const displayedEvents = events.filter((evt) => {
    if (!searchQuery.trim()) return true;
    const q = searchQuery.toLowerCase();
    return (
      evt.metricName.toLowerCase().includes(q) ||
      evt.explanation.toLowerCase().includes(q) ||
      (evt.schemeName && evt.schemeName.toLowerCase().includes(q)) ||
      (evt.schemeCode && evt.schemeCode.toLowerCase().includes(q))
    );
  });

  return (
    <div className="min-h-screen bg-surface-midnight text-text-cream-bright pb-16">
      {/* Top Banner & Header */}
      <div className="border-b border-border-gold-subtle bg-surface-navy-raised/20">
        <div className="mx-auto max-w-[1440px] px-4 py-8 sm:px-6 lg:px-10 space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div className="space-y-1">
              <div className="flex items-center gap-2.5">
                <span className="h-3 w-3 rounded-full bg-primary animate-pulse" />
                <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-text-cream-bright">
                  Continuous Fund & Portfolio Monitoring
                </h1>
                <span className="text-xs font-mono px-2.5 py-0.5 rounded-full border border-primary/40 bg-primary/10 text-primary font-semibold">
                  SCHEDULED EVALUATION & EVIDENCE INTEGRITY
                </span>
              </div>
              <p className="text-sm text-text-cream-muted max-w-3xl leading-relaxed">
                Deterministic backend engine continuously tracking empirical performance changes (RSK-03 Drawdown, RSK-01 Volatility, RSK-02 Semideviation), holdings disclosure shifts, data freshness lags, and investor research thesis reconsideration conditions.
              </p>
            </div>

            <div className="flex flex-wrap items-center gap-3">
              <button
                type="button"
                onClick={handleSyncTheses}
                className="px-3.5 py-2 rounded-xl border border-border-gold-subtle bg-surface-navy-raised/60 hover:bg-surface-navy-raised text-xs font-medium text-text-cream-bright transition-all flex items-center gap-2"
                title="Imports reconsideration rules from your stored Research Theses"
              >
                <svg className="w-4 h-4 text-primary" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
                </svg>
                Sync Thesis Rules
              </button>

              <button
                type="button"
                onClick={() => setIsConditionModalOpen(true)}
                className="px-3.5 py-2 rounded-xl border border-primary/40 bg-primary/10 hover:bg-primary/20 text-xs font-bold text-primary transition-all flex items-center gap-1.5"
              >
                <span>+</span> New Monitoring Rule
              </button>

              <button
                type="button"
                onClick={() => setIsNotificationModalOpen(true)}
                className="px-3.5 py-1.5 rounded-lg border border-border-gold-subtle bg-surface-navy-raised text-text-cream-bright font-sans text-xs font-semibold hover:bg-surface-navy-hover transition-colors flex items-center gap-1.5"
              >
                🔔 Notification Channels
              </button>
              <button
                type="button"
                onClick={handleRunScan}
                disabled={scanning}
                className="px-4 py-2 rounded-xl bg-primary text-surface-midnight font-bold text-xs uppercase tracking-wider hover:bg-primary/90 transition-all disabled:opacity-50 flex items-center gap-2 shadow-lg shadow-primary/10"
              >
                {scanning ? (
                  <>
                    <span className="inline-block animate-spin rounded-full h-3.5 w-3.5 border-2 border-surface-midnight border-t-transparent" />
                    <span>Evaluating Universe...</span>
                  </>
                ) : (
                  <>
                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                    </svg>
                    <span>Run On-Demand Scan</span>
                  </>
                )}
              </button>
            </div>
          </div>

          {scanMessage && (
            <div className="p-3 rounded-xl border border-primary/30 bg-primary/10 text-xs text-primary font-mono flex items-center justify-between">
              <span>{scanMessage}</span>
              <button onClick={() => setScanMessage(null)} className="text-primary hover:text-text-cream-bright font-bold ml-4">
                &times;
              </button>
            </div>
          )}

          {/* Operational Engine Telemetry & Scheduling Banner */}
          <div className="p-4 rounded-2xl border border-border-gold-subtle bg-surface-midnight/80 grid grid-cols-1 md:grid-cols-4 gap-4 items-center">
            <div className="space-y-1">
              <div className="flex items-center gap-2">
                <span className={`h-2.5 w-2.5 rounded-full ${
                  latestExecution?.executionStatus === 'COMPLETED'
                    ? 'bg-emerald-400'
                    : latestExecution?.executionStatus === 'PARTIALLY_COMPLETED'
                    ? 'bg-amber-400'
                    : latestExecution?.executionStatus === 'FAILED'
                    ? 'bg-rose-500'
                    : 'bg-primary animate-pulse'
                }`} />
                <span className="text-xs font-mono font-bold uppercase tracking-wider text-text-cream-bright">
                  Engine Status: {latestExecution?.executionStatus || overview?.latestExecutionStatus || 'ACTIVE'}
                </span>
              </div>
              <p className="text-[11px] text-text-cream-muted">
                Cadence: Daily 04:00 AM IST (Scheduled) + On-Demand
              </p>
            </div>

            <div className="space-y-0.5 font-mono text-[11px]">
              <span className="text-text-cream-muted block">Last Evaluated:</span>
              <span className="text-text-cream-bright font-semibold">
                {latestExecution?.completedAt || overview?.lastMonitoringEvaluationTime
                  ? new Date(latestExecution?.completedAt || overview?.lastMonitoringEvaluationTime || '').toLocaleString('en-IN', { timeZone: 'Asia/Kolkata' })
                  : 'Awaiting execution'}
              </span>
              <span className="text-[10px] text-primary block">
                Trigger: {latestExecution?.executionTrigger || 'SCHEDULED'}
              </span>
            </div>

            <div className="space-y-0.5 font-mono text-[11px]">
              <span className="text-text-cream-muted block">Evaluation Universe:</span>
              <span className="text-text-cream-bright font-semibold">
                {latestExecution ? `${latestExecution.fundsEvaluatedCount} funds evaluated` : `${overview?.monitoredFundsCount || 0} watched funds`}
              </span>
              <span className="text-[10px] text-text-cream-muted block">
                {latestExecution ? `${latestExecution.conditionsEvaluatedCount} rules evaluated (${latestExecution.eventsDeduplicatedCount} dedup)` : 'Point-in-time aligned'}
              </span>
            </div>

            <div className="space-y-0.5 font-mono text-[11px]">
              <span className="text-text-cream-muted block">Data Availability Flags:</span>
              <div className="flex items-center gap-2 mt-0.5">
                {(overview?.unavailableEvidenceCount || 0) > 0 ? (
                  <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-amber-500/20 text-amber-300 border border-amber-500/40">
                    {overview?.unavailableEvidenceCount} Evidence Missing
                  </span>
                ) : (
                  <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-500/10 text-emerald-300 border border-emerald-500/30">
                    Evidence Complete
                  </span>
                )}
                {(overview?.staleSourceCount || 0) > 0 && (
                  <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-orange-500/20 text-orange-300 border border-orange-500/40">
                    {overview?.staleSourceCount} Stale Feeds
                  </span>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Main Container */}
      <div className="mx-auto max-w-[1440px] px-4 py-8 sm:px-6 lg:px-10 space-y-8">
        {/* Metric & Summary Cards */}
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3 sm:gap-4">
          <div className="p-4 rounded-2xl border border-border-gold-subtle bg-surface-navy-raised/40 space-y-1">
            <span className="text-[10.5px] font-mono uppercase tracking-wider text-text-cream-muted">Active Alerts</span>
            <div className="text-2xl font-bold font-mono text-text-cream-bright">
              {overview ? overview.totalActiveEvents : '—'}
            </div>
            <span className="text-[10px] text-text-cream-muted">Awaiting action</span>
          </div>

          <div className="p-4 rounded-2xl border border-red-500/30 bg-red-500/10 space-y-1">
            <span className="text-[10.5px] font-mono uppercase tracking-wider text-red-300">High Severity</span>
            <div className="text-2xl font-bold font-mono text-red-300">
              {overview ? overview.highSeverityCount : '—'}
            </div>
            <span className="text-[10px] text-red-200/70">Critical breaches</span>
          </div>

          <div className="p-4 rounded-2xl border border-amber-500/30 bg-amber-500/10 space-y-1">
            <span className="text-[10.5px] font-mono uppercase tracking-wider text-amber-300">Medium Severity</span>
            <div className="text-2xl font-bold font-mono text-amber-300">
              {overview ? overview.mediumSeverityCount : '—'}
            </div>
            <span className="text-[10px] text-amber-200/70">Material drift</span>
          </div>

          <div className="p-4 rounded-2xl border border-purple-500/30 bg-purple-500/10 space-y-1">
            <span className="text-[10.5px] font-mono uppercase tracking-wider text-purple-300">Thesis Triggered</span>
            <div className="text-2xl font-bold font-mono text-purple-300">
              {overview ? overview.triggeredThesisConditionsCount : '—'}
            </div>
            <span className="text-[10px] text-purple-200/70">Reassessment due</span>
          </div>

          <div className="p-4 rounded-2xl border border-orange-500/30 bg-orange-500/10 space-y-1">
            <span className="text-[10.5px] font-mono uppercase tracking-wider text-orange-300">Freshness Warnings</span>
            <div className="text-2xl font-bold font-mono text-orange-300">
              {overview ? overview.dataFreshnessWarningsCount : '—'}
            </div>
            <span className="text-[10px] text-orange-200/70">Stale NAV / Holdings</span>
          </div>

          <div className="p-4 rounded-2xl border border-border-gold-subtle bg-surface-navy-raised/40 space-y-1">
            <span className="text-[10.5px] font-mono uppercase tracking-wider text-text-cream-muted">Monitored Universe</span>
            <div className="text-2xl font-bold font-mono text-primary">
              {overview ? `${overview.monitoredFundsCount + overview.monitoredPortfolioPositionsCount}` : '—'}
            </div>
            <span className="text-[10px] text-text-cream-muted">
              {overview ? `${overview.monitoredFundsCount} funds · ${overview.monitoredPortfolioPositionsCount} positions` : 'Watched & Portfolio'}
            </span>
          </div>
        </div>

        {/* Tab Selection */}
        <div className="flex border-b border-border-gold-subtle gap-2">
          <button
            type="button"
            onClick={() => setActiveTab('alerts')}
            className={`py-3 px-5 text-xs font-semibold uppercase tracking-wider border-b-2 transition-all flex items-center gap-2 ${
              activeTab === 'alerts'
                ? 'border-primary text-primary font-bold'
                : 'border-transparent text-text-cream-muted hover:text-text-cream-bright'
            }`}
          >
            <span>Evidence Drift & Alerts</span>
            <span className="text-[10px] font-mono px-2 py-0.2 rounded-full bg-surface-navy-raised text-text-cream-bright border border-border-gold-subtle">
              {displayedEvents.length}
            </span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('investigating')}
            className={`py-3 px-5 text-xs font-semibold uppercase tracking-wider border-b-2 transition-all flex items-center gap-2 ${
              activeTab === 'investigating'
                ? 'border-purple-400 text-purple-300 font-bold'
                : 'border-transparent text-text-cream-muted hover:text-text-cream-bright'
            }`}
          >
            <span>Investigation Queue</span>
            {overview && (
              <span className="text-[10px] font-mono px-2 py-0.2 rounded-full bg-purple-500/20 text-purple-300 border border-purple-500/40">
                {overview.awaitingAcknowledgementCount + overview.investigatingCount}
              </span>
            )}
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('conditions')}
            className={`py-3 px-5 text-xs font-semibold uppercase tracking-wider border-b-2 transition-all flex items-center gap-2 ${
              activeTab === 'conditions'
                ? 'border-primary text-primary font-bold'
                : 'border-transparent text-text-cream-muted hover:text-text-cream-bright'
            }`}
          >
            <span>Thesis Invalidation Rules</span>
            <span className="text-[10px] font-mono px-2 py-0.2 rounded-full bg-surface-navy-raised text-text-cream-bright border border-border-gold-subtle">
              {conditions.length}
            </span>
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('execution')}
            className={`py-3 px-5 text-xs font-semibold uppercase tracking-wider border-b-2 transition-all flex items-center gap-2 ${
              activeTab === 'execution'
                ? 'border-emerald-400 text-emerald-300 font-bold'
                : 'border-transparent text-text-cream-muted hover:text-text-cream-bright'
            }`}
          >
            <span>Execution & Outbox</span>
          </button>

        </div>

                {/* Tab 4: Execution & Outbox */}
        {activeTab === 'execution' && (
          <MonitoringOutboxWidget />
        )}

        {/* Tab 1 & Tab 2: Alerts & Investigation Queue */}
        {(activeTab === 'alerts' || activeTab === 'investigating') && (
          <div className="space-y-6">
            {/* Filter Bar */}
            <div className="p-4 rounded-2xl border border-border-gold-subtle bg-surface-navy-raised/30 flex flex-wrap items-center justify-between gap-4">
              <div className="flex flex-wrap items-center gap-3">
                {/* Search */}
                <div className="relative w-64">
                  <input
                    type="text"
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    placeholder="Search metric, fund, code..."
                    className="w-full pl-8 pr-3 py-1.5 rounded-lg border border-border-gold-subtle bg-surface-midnight text-xs text-text-cream-bright placeholder-text-cream-muted/50 focus:outline-none focus:ring-1 focus:ring-primary"
                  />
                  <svg className="w-3.5 h-3.5 text-text-cream-muted absolute left-2.5 top-2.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                  </svg>
                </div>

                {/* Severity Filter */}
                <select
                  value={severityFilter}
                  onChange={(e) => setSeverityFilter(e.target.value)}
                  className="p-1.5 rounded-lg border border-border-gold-subtle bg-surface-midnight text-xs text-text-cream-bright focus:outline-none focus:ring-1 focus:ring-primary"
                >
                  <option value="ALL">Severity: All</option>
                  <option value="HIGH">High Severity</option>
                  <option value="MEDIUM">Medium Severity</option>
                  <option value="LOW">Low Severity</option>
                  <option value="INFO">Info</option>
                </select>

                {/* Classification Filter */}
                <select
                  value={classificationFilter}
                  onChange={(e) => setClassificationFilter(e.target.value)}
                  className="p-1.5 rounded-lg border border-border-gold-subtle bg-surface-midnight text-xs text-text-cream-bright focus:outline-none focus:ring-1 focus:ring-primary"
                >
                  <option value="ALL">Classification: All</option>
                  <option value="PERFORMANCE_CHANGE">Performance Drift</option>
                  <option value="HOLDINGS_DISCLOSURE_CHANGE">Holdings Disclosure</option>
                  <option value="PORTFOLIO_EXPOSURE_CHANGE">Portfolio Exposure</option>
                  <option value="DATA_FRESHNESS_CHANGE">Data Freshness Warning</option>
                  <option value="DATA_QUALITY_CHANGE">Data Quality</option>
                  <option value="THESIS_CONDITION_TRIGGERED">Thesis Triggered</option>
                </select>

                {/* Scope Filter */}
                <select
                  value={scopeFilter}
                  onChange={(e) => setScopeFilter(e.target.value)}
                  className="p-1.5 rounded-lg border border-border-gold-subtle bg-surface-midnight text-xs text-text-cream-bright focus:outline-none focus:ring-1 focus:ring-primary"
                >
                  <option value="ALL">Scope: All</option>
                  <option value="FUND">Fund Level</option>
                  <option value="PORTFOLIO">Portfolio Level</option>
                </select>

                {activeTab !== 'investigating' && (
                  <select
                    value={statusFilter}
                    onChange={(e) => setStatusFilter(e.target.value)}
                    className="p-1.5 rounded-lg border border-border-gold-subtle bg-surface-midnight text-xs text-text-cream-bright focus:outline-none focus:ring-1 focus:ring-primary"
                  >
                    <option value="ALL">Status: All</option>
                    <option value="NEW">New</option>
                    <option value="ACKNOWLEDGED">Acknowledged</option>
                    <option value="INVESTIGATING">Investigating</option>
                    <option value="RESOLVED">Resolved</option>
                    <option value="DISMISSED">Dismissed</option>
                  </select>
                )}
              </div>

              <div className="text-xs text-text-cream-muted font-mono">
                Showing {displayedEvents.length} of {totalElements} events
              </div>
            </div>

            {/* Events List */}
            {displayedEvents.length === 0 ? (
              <div className="py-20 text-center rounded-2xl border border-border-gold-subtle bg-surface-navy-raised/20 space-y-3">
                <div className="w-12 h-12 rounded-full border border-primary/30 bg-primary/10 flex items-center justify-center mx-auto text-primary">
                  <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                  </svg>
                </div>
                <h3 className="text-base font-bold text-text-cream-bright">No monitoring alerts detected</h3>
                <p className="text-xs text-text-cream-muted max-w-md mx-auto">
                  All watched funds and portfolio holdings remain within candidate materiality thresholds. Data feeds are operating within expected reporting cadences.
                </p>
              </div>
            ) : (
              <div className="space-y-3">
                {displayedEvents.map((evt) => {
                  const classMeta = CLASSIFICATION_META[evt.eventClassification] || {
                    label: evt.eventClassification,
                    badgeClass: 'bg-slate-500/20 text-slate-300 border-slate-500/30',
                    description: '',
                  };
                  const sevMeta = SEVERITY_META[evt.severity] || {
                    label: evt.severity,
                    badgeClass: 'bg-slate-500/20 text-slate-300 border-slate-500/30',
                    dotClass: 'bg-slate-400',
                  };
                  const lifeMeta = LIFECYCLE_META[evt.lifecycleStatus] || {
                    label: evt.lifecycleStatus,
                    badgeClass: 'bg-slate-500/20 text-slate-300 border-slate-500/30',
                  };

                  return (
                    <div
                      key={evt.id}
                      onClick={() => setSelectedEvent(evt)}
                      className="p-5 rounded-2xl border border-border-gold-subtle bg-surface-navy-raised/40 hover:bg-surface-navy-raised/70 transition-all cursor-pointer space-y-3 group"
                    >
                      <div className="flex flex-wrap items-center justify-between gap-3">
                        <div className="flex flex-wrap items-center gap-2">
                          {/* Severity Pill */}
                          <span className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[10.5px] font-mono font-bold border ${sevMeta.badgeClass}`}>
                            <span className={`h-1.5 w-1.5 rounded-full ${sevMeta.dotClass}`} />
                            {sevMeta.label}
                          </span>

                          {/* Classification Pill */}
                          <span className={`px-2.5 py-0.5 rounded-full text-[10.5px] font-medium border ${classMeta.badgeClass}`}>
                            {classMeta.label}
                          </span>

                          {/* Data Artifact Warning Badge */}
                          {evt.isDataArtifact && (
                            <span className="px-2.5 py-0.5 rounded-full text-[10.5px] font-mono font-semibold bg-orange-500/20 text-orange-300 border border-orange-500/40">
                              Data / Disclosure Lag
                            </span>
                          )}

                          {/* Thesis Condition Link Badge */}
                          {evt.associatedThesisKey && (
                            <span className="px-2.5 py-0.5 rounded-full text-[10.5px] font-mono font-semibold bg-red-500/20 text-red-300 border border-red-500/40">
                              Thesis Linked
                            </span>
                          )}
                        </div>

                        <div className="flex items-center gap-3">
                          {/* Lifecycle Status Pill */}
                          <span className={`px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold uppercase tracking-wider border ${lifeMeta.badgeClass}`}>
                            {lifeMeta.label}
                          </span>
                          <span className="text-[11px] font-mono text-text-cream-muted">
                            {new Date(evt.createdAt).toLocaleDateString('en-IN', { timeZone: 'Asia/Kolkata' })}
                          </span>
                        </div>
                      </div>

                      {/* Fund & Scope Title */}
                      <div className="space-y-1">
                        <div className="flex items-center gap-2">
                          <span className="text-sm font-bold text-text-cream-bright group-hover:text-primary transition-colors">
                            {evt.schemeName || (evt.portfolioScope ? 'Consolidated Investor Portfolio' : 'Mutual Fund')}
                          </span>
                          {evt.schemeCode && (
                            <span className="text-xs font-mono text-text-cream-muted">
                              ({evt.schemeCode})
                            </span>
                          )}
                          {evt.planType && (
                            <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-surface-midnight text-text-cream-muted border border-border-gold-subtle">
                              {evt.planType}
                            </span>
                          )}
                        </div>
                        <p className="text-xs text-text-cream-muted line-clamp-2 leading-relaxed">
                          {evt.explanation}
                        </p>
                      </div>

                      {/* Values Shift Row */}
                      <div className="pt-2 border-t border-border-gold-subtle/60 flex flex-wrap items-center justify-between gap-4 font-mono text-xs">
                        <div className="flex items-center gap-6">
                          <div>
                            <span className="text-[10px] uppercase text-text-cream-muted block">Observed Metric</span>
                            <span className="text-text-cream-bright font-bold">{evt.metricName}</span>
                          </div>
                          <div>
                            <span className="text-[10px] uppercase text-text-cream-muted block">Previous</span>
                            <span className="text-text-cream-muted">{evt.previousValue || 'Baseline'}</span>
                          </div>
                          <div>
                            <span className="text-[10px] uppercase text-text-cream-muted block">Latest</span>
                            <span className="text-primary font-bold">{evt.currentValue || 'Unavailable'}</span>
                          </div>
                        </div>

                        <div className="flex items-center gap-2 text-text-cream-muted text-[11px] group-hover:text-primary transition-colors">
                          <span>Inspect Evidence & Due Diligence</span>
                          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                          </svg>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {/* Tab 3: Thesis Invalidation Rules */}
        {activeTab === 'conditions' && (
          <div className="space-y-6">
            <div className="flex flex-wrap items-center justify-between gap-4 p-4 rounded-2xl border border-border-gold-subtle bg-surface-navy-raised/30">
              <div>
                <h2 className="text-sm font-bold text-text-cream-bright tracking-tight">
                  Deterministic Thesis Invalidation & Monitoring Rules
                </h2>
                <p className="text-xs text-text-cream-muted mt-0.5">
                  Rules execute continuously against empirical observations (RSK-03 Drawdown, RSK-01 Volatility, Holdings Shifts) to identify thesis reconsiderations.
                </p>
              </div>

              <div className="flex items-center gap-3">
                <button
                  type="button"
                  onClick={handleSyncTheses}
                  className="px-3 py-1.5 rounded-xl border border-border-gold-subtle bg-surface-navy-raised hover:bg-surface-navy-raised/80 text-xs text-text-cream-bright flex items-center gap-1.5"
                >
                  Sync From Theses
                </button>
                <button
                  type="button"
                  onClick={() => setIsConditionModalOpen(true)}
                  className="px-3.5 py-1.5 rounded-xl bg-primary text-surface-midnight text-xs font-bold hover:bg-primary/90"
                >
                  + Add Condition
                </button>
              </div>
            </div>

            {conditions.length === 0 ? (
              <div className="py-16 text-center rounded-2xl border border-border-gold-subtle bg-surface-navy-raised/20 space-y-3">
                <p className="text-sm font-medium text-text-cream-bright">No monitoring rules configured yet</p>
                <p className="text-xs text-text-cream-muted max-w-md mx-auto">
                  Author reconsideration criteria from your research hypotheses (e.g. alert if RSK-03 drawdown deepens past -15% or RSK-01 volatility exceeds 20%).
                </p>
                <button
                  type="button"
                  onClick={() => setIsConditionModalOpen(true)}
                  className="px-4 py-2 rounded-xl bg-primary text-surface-midnight text-xs font-bold hover:bg-primary/90"
                >
                  Create First Rule
                </button>
              </div>
            ) : (
              <div className="overflow-x-auto rounded-2xl border border-border-gold-subtle bg-surface-navy-raised/30">
                <table className="w-full text-left text-xs border-collapse">
                  <thead>
                    <tr className="border-b border-border-gold-subtle bg-surface-midnight/60 font-mono text-[10.5px] uppercase tracking-wider text-text-cream-muted">
                      <th className="py-3 px-4">Rule Name</th>
                      <th className="py-3 px-4">Target Scope</th>
                      <th className="py-3 px-4">Evidence Field</th>
                      <th className="py-3 px-4">Operator & Threshold</th>
                      <th className="py-3 px-4">Evaluation State</th>
                      <th className="py-3 px-4">Evidence State</th>
                      <th className="py-3 px-4">Triggers</th>
                      <th className="py-3 px-4">Status</th>
                      <th className="py-3 px-4 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border-gold-subtle font-mono text-[11px]">
                    {conditions.map((c) => (
                      <tr key={c.id} className="hover:bg-surface-navy-raised/50 transition-colors">
                        <td className="py-3.5 px-4 font-sans font-medium text-text-cream-bright">
                          <div>{c.conditionName}</div>
                          {c.thesisReconsiderationGuidance && (
                            <div className="text-[10px] text-text-cream-muted italic mt-0.5 line-clamp-1">
                              Guidance: {c.thesisReconsiderationGuidance}
                            </div>
                          )}
                        </td>
                        <td className="py-3.5 px-4 text-text-cream-muted">
                          {c.schemeName ? (
                            <span className="text-text-cream-bright truncate block max-w-xs">{c.schemeName}</span>
                          ) : (
                            <span className="text-purple-300">Consolidated Portfolio</span>
                          )}
                        </td>
                        <td className="py-3.5 px-4 text-primary font-bold">{c.evidenceField}</td>
                        <td className="py-3.5 px-4 text-text-cream-bright">
                          {c.operator} {c.thresholdValue}
                        </td>
                        <td className="py-3.5 px-4">
                          <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] border ${
                            c.evaluationState === 'TRIGGERED'
                              ? 'bg-red-500/20 text-red-300 border-red-500/40'
                              : c.evaluationState === 'CLEAR'
                              ? 'bg-emerald-500/10 text-emerald-300 border-emerald-500/30'
                              : c.evaluationState === 'REQUIRES_REVIEW'
                              ? 'bg-amber-500/20 text-amber-300 border-amber-500/40'
                              : 'bg-slate-500/20 text-slate-300 border-slate-500/30'
                          }`}>
                            {c.evaluationState}
                          </span>
                        </td>
                        <td className="py-3.5 px-4">
                          <span className={`text-[10px] ${
                            c.evidenceState === 'STALE' ? 'text-amber-300' :
                            c.evidenceState === 'MISSING' ? 'text-red-300' :
                            c.evidenceState === 'METHODOLOGY_INCOMPATIBLE' ? 'text-orange-400 font-bold' :
                            'text-emerald-300'
                          }`}>
                            {c.evidenceState || 'AVAILABLE'}
                          </span>
                        </td>
                        <td className="py-3.5 px-4 text-text-cream-muted">
                          {c.triggerCount || 0}
                        </td>
                        <td className="py-3.5 px-4">
                          <button
                            type="button"
                            onClick={() => handleToggleCondition(c.id)}
                            className={`px-2 py-0.5 rounded text-[10px] font-bold border transition-all ${
                              c.isEnabled
                                ? 'bg-primary/20 text-primary border-primary/40'
                                : 'bg-slate-700/30 text-slate-400 border-slate-600/30'
                            }`}
                          >
                            {c.isEnabled ? 'ENABLED' : 'DISABLED'}
                          </button>
                        </td>
                        <td className="py-3.5 px-4 text-right">
                          <button
                            type="button"
                            onClick={() => setSelectedConditionForHistory({ id: c.id, name: c.conditionName })}
                            className="text-cyan-400 hover:text-cyan-300 hover:underline text-[10px] mr-2"
                          >
                            Ledger
                          </button>
                          <button
                            type="button"
                            onClick={() => handleDeleteCondition(c.id)}
                            className="text-rose-400 hover:text-rose-300 hover:underline text-[10px] ml-2"
                          >
                            Delete
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Modals */}
      <MonitoringEventDetailModal
        isOpen={Boolean(selectedEvent)}
        onClose={() => setSelectedEvent(null)}
        event={selectedEvent}
        onEventUpdated={(updated) => {
          setSelectedEvent(updated);
          setEvents((prev) => prev.map((e) => (e.id === updated.id ? updated : e)));
        }}
      />

      <CreateConditionModal
        isOpen={isConditionModalOpen}
        onClose={() => setIsConditionModalOpen(false)}
        onCreated={(created) => {
          setConditions((prev) => [created, ...prev]);
        }}
      />
      <NotificationCentreModal
        isOpen={isNotificationModalOpen}
        onClose={() => setIsNotificationModalOpen(false)}
      />
      {selectedConditionForHistory && (
        <ConditionHistoryModal
          conditionId={selectedConditionForHistory.id}
          conditionName={selectedConditionForHistory.name}
          isOpen={Boolean(selectedConditionForHistory)}
          onClose={() => setSelectedConditionForHistory(null)}
        />
      )}
    </div>
  );
}
