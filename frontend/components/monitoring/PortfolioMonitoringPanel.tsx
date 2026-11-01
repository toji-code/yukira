'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import {
  MonitoringEventDto,
  getPortfolioMonitoringEvents,
  CLASSIFICATION_META,
  SEVERITY_META,
} from '@/lib/api/monitoring';
import { MonitoringEventDetailModal } from './MonitoringEventDetailModal';

export function PortfolioMonitoringPanel() {
  const [events, setEvents] = useState<MonitoringEventDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedEvent, setSelectedEvent] = useState<MonitoringEventDto | null>(null);

  useEffect(() => {
    let isMounted = true;
    async function loadPortfolioEvents() {
      try {
        setLoading(true);
        const data = await getPortfolioMonitoringEvents();
        if (isMounted) setEvents(data);
      } catch (err) {
        console.warn('Failed to load portfolio monitoring events', err);
      } finally {
        if (isMounted) setLoading(false);
      }
    }
    loadPortfolioEvents();
    return () => {
      isMounted = false;
    };
  }, []);

  const activeEvents = events.filter((e) => e.lifecycleStatus !== 'RESOLVED' && e.lifecycleStatus !== 'DISMISSED');

  if (loading) {
    return (
      <div className="p-4 rounded-xl border border-border-gold-subtle bg-surface-navy-raised/20 text-xs text-text-cream-muted font-mono">
        Scanning portfolio continuous evidence drift...
      </div>
    );
  }

  if (activeEvents.length === 0) {
    return null; // Don't clutter portfolio if zero active portfolio warnings
  }

  return (
    <div className="space-y-4 rounded-2xl border border-purple-500/30 bg-purple-950/15 p-5 sm:p-6 backdrop-blur-md">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border-gold-subtle pb-3">
        <div className="flex items-center gap-2.5">
          <span className="h-2.5 w-2.5 rounded-full bg-purple-400 animate-pulse" />
          <h3 className="text-sm font-bold text-text-cream-bright tracking-tight">
            Portfolio Evidence Drift & Concentration Alerts ({activeEvents.length})
          </h3>
        </div>
        <Link
          href="/monitoring?scope=PORTFOLIO"
          className="text-xs text-primary hover:underline font-mono"
        >
          View Full Monitoring Queue &rarr;
        </Link>
      </div>

      {/* Attribution Explanation Banner (Requirement 9) */}
      <div className="p-3 rounded-xl border border-border-gold-subtle bg-surface-midnight/60 text-[11px] text-text-cream-muted space-y-1">
        <span className="font-mono text-primary font-semibold uppercase tracking-wider">
          Portfolio Change Attribution Discipline:
        </span>
        <p className="leading-relaxed">
          Consolidated exposure shifts can be driven by <strong>investor transactions</strong>, <strong>fund-reported monthly disclosures</strong>, <strong>NAV valuation movements</strong>, or <strong>source coverage changes</strong>. YUKIRA never attributes an exposure shift to fund manager action unless disclosed holdings filings corroborate that conclusion.
        </p>
      </div>

      {/* Events Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
        {activeEvents.map((evt) => {
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
              className="cursor-pointer p-3.5 rounded-xl border border-border-gold-subtle bg-surface-navy-raised/40 hover:bg-surface-navy-raised/80 hover:border-purple-400/50 transition-all space-y-2"
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
                <span>Value: <strong className="text-primary">{evt.currentValue}</strong></span>
                <span className="text-purple-300 hover:underline">Investigate &rarr;</span>
              </div>
            </div>
          );
        })}
      </div>

      <MonitoringEventDetailModal
        isOpen={Boolean(selectedEvent)}
        onClose={() => setSelectedEvent(null)}
        event={selectedEvent}
        onEventUpdated={(updated) => {
          setSelectedEvent(updated);
          setEvents((prev) => prev.map((e) => (e.id === updated.id ? updated : e)));
        }}
      />
    </div>
  );
}

