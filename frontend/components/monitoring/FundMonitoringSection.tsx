'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import {
  MonitoringEventDto,
  MonitoringConditionDto,
  getFundMonitoringEvents,
  getMonitoringConditions,
  CLASSIFICATION_META,
  SEVERITY_META,
  LIFECYCLE_META,
} from '@/lib/api/monitoring';
import { MonitoringEventDetailModal } from './MonitoringEventDetailModal';
import { CreateConditionModal } from './CreateConditionModal';

interface FundMonitoringSectionProps {
  schemeId: number;
  schemeOptionId?: number | null;
  schemeName?: string;
  schemeCode?: string;
}

export function FundMonitoringSection({
  schemeId,
  schemeOptionId,
  schemeName,
  schemeCode,
}: FundMonitoringSectionProps) {
  const [events, setEvents] = useState<MonitoringEventDto[]>([]);
  const [conditions, setConditions] = useState<MonitoringConditionDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedEvent, setSelectedEvent] = useState<MonitoringEventDto | null>(null);
  const [isConditionModalOpen, setIsConditionModalOpen] = useState(false);

  useEffect(() => {
    let isMounted = true;
    async function loadData() {
      try {
        setLoading(true);
        const [evts, conds] = await Promise.all([
          getFundMonitoringEvents(schemeId).catch(() => []),
          getMonitoringConditions().catch(() => []),
        ]);
        if (isMounted) {
          setEvents(evts);
          const fundConds = conds.filter(
            (c) => c.schemeId === schemeId || (schemeOptionId && c.schemeOptionId === schemeOptionId)
          );
          setConditions(fundConds);
        }
      } catch (err) {
        console.warn('Failed to load fund monitoring events', err);
      } finally {
        if (isMounted) setLoading(false);
      }
    }
    if (schemeId) {
      loadData();
    }
    return () => {
      isMounted = false;
    };
  }, [schemeId, schemeOptionId]);

  const activeEvents = events.filter((e) => e.lifecycleStatus !== 'RESOLVED' && e.lifecycleStatus !== 'DISMISSED');

  return (
    <div className="space-y-4 rounded-2xl border border-border-gold-subtle bg-surface-midnight/80 p-5 sm:p-6 backdrop-blur-md">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border-gold-subtle pb-4">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <span className="h-2 w-2 rounded-full bg-primary animate-pulse" />
            <h3 className="text-base font-bold text-text-cream-bright tracking-tight">
              Continuous Evidence Drift & Reassessment
            </h3>
            <span className="text-[10px] font-mono px-2 py-0.5 rounded-full border border-border-gold-subtle bg-surface-navy-raised text-text-cream-muted">
              Monitoring Slice
            </span>
          </div>
          <p className="text-xs text-text-cream-muted">
            Continuous empirical verification of holdings, rolling risk/return, and data freshness.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setIsConditionModalOpen(true)}
            className="px-3 py-1.5 rounded-lg border border-primary/40 bg-primary/10 text-primary text-xs font-semibold hover:bg-primary/20 transition-all"
          >
            + Set Reconsideration Rule
          </button>
          <Link
            href="/monitoring"
            className="px-3 py-1.5 rounded-lg border border-border-gold-subtle bg-surface-navy-raised/50 text-text-cream-muted hover:text-text-cream-bright text-xs transition-all"
          >
            All Alerts &rarr;
          </Link>
        </div>
      </div>

      {loading ? (
        <div className="py-8 text-center text-xs text-text-cream-muted font-mono">
          Loading continuous monitoring evidence...
        </div>
      ) : activeEvents.length === 0 && conditions.length === 0 ? (
        <div className="py-6 text-center space-y-2">
          <p className="text-xs text-text-cream-muted font-medium">
            No active evidence drift alerts or data quality warnings detected for this fund.
          </p>
          <p className="text-[11px] text-text-cream-muted/70">
            NAV observations and disclosed holdings are currently within statutory cadences.
          </p>
        </div>
      ) : (
        <div className="space-y-4">
          {/* Active Events List */}
          {activeEvents.length > 0 && (
            <div className="space-y-2">
              <span className="text-[11px] font-mono uppercase tracking-wider text-text-cream-muted">
                Active Evidence & Drift Alerts ({activeEvents.length})
              </span>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                {activeEvents.slice(0, 4).map((evt) => {
                  const classMeta = CLASSIFICATION_META[evt.eventClassification] || {
                    label: evt.eventClassification,
                    badgeClass: 'bg-slate-500/20 text-slate-300 border-slate-500/30',
                  };
                  const sevMeta = SEVERITY_META[evt.severity] || {
                    label: evt.severity,
                    badgeClass: 'bg-slate-500/20 text-slate-300 border-slate-500/30',
                  };

                  return (
                    <div
                      key={evt.id}
                      onClick={() => setSelectedEvent(evt)}
                      className="cursor-pointer p-3.5 rounded-xl border border-border-gold-subtle bg-surface-navy-raised/40 hover:bg-surface-navy-raised/80 hover:border-primary/50 transition-all space-y-2"
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className={`text-[10px] font-mono px-2 py-0.5 rounded-full border ${classMeta.badgeClass}`}>
                          {classMeta.label}
                        </span>
                        <span className={`text-[10px] font-mono px-2 py-0.5 rounded-full border ${sevMeta.badgeClass}`}>
                          {sevMeta.label}
                        </span>
                      </div>
                      <h4 className="text-xs font-semibold text-text-cream-bright truncate">
                        {evt.metricName}
                      </h4>
                      <p className="text-[11px] text-text-cream-muted line-clamp-2 leading-relaxed">
                        {evt.explanation}
                      </p>
                      <div className="flex items-center justify-between pt-1 border-t border-border-gold-subtle text-[10px] font-mono text-text-cream-muted">
                        <span>Observed: {evt.currentObservationDate || 'Recent'}</span>
                        <span className="text-primary hover:underline">Inspect Evidence &rarr;</span>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* Associated Monitoring Conditions */}
          {conditions.length > 0 && (
            <div className="space-y-2 pt-2 border-t border-border-gold-subtle">
              <span className="text-[11px] font-mono uppercase tracking-wider text-text-cream-muted">
                Active Reconsideration Conditions ({conditions.length})
              </span>
              <div className="space-y-2">
                {conditions.map((cond) => (
                  <div
                    key={cond.id}
                    className="flex items-center justify-between p-2.5 rounded-xl border border-border-gold-subtle bg-surface-navy-raised/30 text-xs"
                  >
                    <div className="space-y-0.5">
                      <div className="flex items-center gap-2">
                        <span className={`h-1.5 w-1.5 rounded-full ${cond.evaluationState === 'TRIGGERED' ? 'bg-red-400' : 'bg-emerald-400'}`} />
                        <span className="font-medium text-text-cream-bright">{cond.conditionName}</span>
                      </div>
                      <span className="text-[10px] font-mono text-text-cream-muted">
                        Rule: {cond.evidenceField} {cond.operator} {cond.thresholdValue} · State: {cond.evaluationState}
                      </span>
                    </div>
                    <span className={`text-[10px] font-mono px-2 py-0.5 rounded-full border ${
                      cond.evaluationState === 'TRIGGERED'
                        ? 'bg-red-500/20 text-red-300 border-red-500/40'
                        : 'bg-emerald-500/10 text-emerald-300 border-emerald-500/30'
                    }`}>
                      {cond.evaluationState}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}

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
        initialSchemeId={schemeId}
        initialSchemeOptionId={schemeOptionId || undefined}
        initialSchemeName={schemeName}
        onCreated={(newCond) => {
          setConditions((prev) => [newCond, ...prev]);
        }}
      />
    </div>
  );
}

