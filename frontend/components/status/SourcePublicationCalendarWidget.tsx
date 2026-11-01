'use client';

import React, { useEffect, useState } from 'react';
import { fetchSourceCalendars, SourcePublicationCalendarItem } from '@/lib/api/research';

export function SourcePublicationCalendarWidget() {
  const [calendars, setCalendars] = useState<SourcePublicationCalendarItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;
    async function load() {
      try {
        setLoading(true);
        setError(null);
        const data = await fetchSourceCalendars();
        if (!ignore) {
          setCalendars(data);
          setLoading(false);
        }
      } catch (err: unknown) {
        if (!ignore) {
          setError(err instanceof Error ? err.message : 'Failed to load source publication calendar');
          setLoading(false);
        }
      }
    }
    load();
    return () => {
      ignore = true;
    };
  }, []);

  return (
    <div className="rounded-lg border border-border-gold-subtle bg-surface-navy-raised/40 p-6 space-y-4">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between pb-3 border-b border-border-gold-subtle/60 gap-2">
        <div>
          <div className="flex items-center gap-2">
            <span className="font-mono text-xs px-2 py-0.5 rounded bg-blue-950 text-blue-400 border border-blue-800 font-semibold">
              TASK 215
            </span>
            <h3 className="text-base font-bold text-text-cream-bright">
              Source Availability &amp; Publication Calendar (Expected vs Observed Arrival)
            </h3>
          </div>
          <p className="mt-1 text-xs text-text-cream-muted">
            Monitors upstream reporting latency, business-day lag thresholds, and official release windows for AMFI, AMC monthly holdings, FBIL, and NSE TRIs.
          </p>
        </div>
        <span className="inline-flex items-center gap-1.5 rounded-full border border-emerald-500/30 bg-emerald-950/40 px-2.5 py-0.5 font-mono text-[11px] font-medium text-emerald-400 shrink-0">
          <span className="h-1.5 w-1.5 rounded-full bg-emerald-400 animate-pulse" />
          CALENDAR ACTIVE
        </span>
      </div>

      {loading ? (
        <div className="p-8 text-center text-xs font-mono text-text-cream-muted animate-pulse">
          Loading source publication schedules and arrival latencies...
        </div>
      ) : error ? (
        <div className="p-4 bg-red-950/30 border border-red-800 rounded text-xs text-red-300 font-mono">
          {error}
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs font-mono border-collapse">
            <thead>
              <tr className="border-b border-border-gold-subtle/40 text-[11px] text-text-cream-muted uppercase bg-surface-midnight/40">
                <th className="py-2.5 px-3">Data Feed Source</th>
                <th className="py-2.5 px-3">Reporting Frequency</th>
                <th className="py-2.5 px-3">Expected Lag</th>
                <th className="py-2.5 px-3">Publication Cutoff</th>
                <th className="py-2.5 px-3">Last Observed</th>
                <th className="py-2.5 px-3">Next Expected</th>
                <th className="py-2.5 px-3 text-right">Arrival Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border-gold-subtle/20">
              {calendars.map((cal) => {
                const isFresh = cal.status === 'CURRENT' || cal.status === 'FRESH' || !cal.status;
                return (
                  <tr key={cal.id || cal.sourceName} className="hover:bg-surface-navy-raised/30 transition-colors">
                    <td className="py-3 px-3 font-semibold text-text-cream-bright">
                      {cal.sourceName}
                    </td>
                    <td className="py-3 px-3 text-text-cream-muted">
                      {cal.frequency}
                    </td>
                    <td className="py-3 px-3 text-text-cream-muted">
                      {cal.expectedLagBusinessDays === 0
                        ? 'Same Day (T+0)'
                        : `T+${cal.expectedLagBusinessDays} Business Days`}
                    </td>
                    <td className="py-3 px-3 text-text-cream-muted">
                      {cal.expectedCutoffTime} {cal.timezone}
                    </td>
                    <td className="py-3 px-3 text-text-cream-body">
                      {cal.lastObservedDate ? (
                        <span>
                          {cal.lastObservedDate}{' '}
                          <span className="text-[10px] text-text-cream-muted">
                            ({cal.lastObservedAt?.slice(11, 16) || '23:00'} IST)
                          </span>
                        </span>
                      ) : (
                        <span className="text-text-cream-muted italic">Awaiting sync</span>
                      )}
                    </td>
                    <td className="py-3 px-3 text-text-cream-body">
                      {cal.nextExpectedDate ? (
                        <span>
                          {cal.nextExpectedDate}{' '}
                          <span className="text-[10px] text-text-cream-muted">
                            ({cal.nextExpectedAt?.slice(11, 16) || cal.expectedCutoffTime})
                          </span>
                        </span>
                      ) : (
                        <span className="text-text-cream-muted italic">Scheduled</span>
                      )}
                    </td>
                    <td className="py-3 px-3 text-right">
                      <span
                        className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-[10px] font-semibold ${
                          isFresh
                            ? 'border-emerald-500/30 bg-emerald-950/40 text-emerald-400'
                            : 'border-amber-500/30 bg-amber-950/40 text-amber-300'
                        }`}
                      >
                        <span
                          className={`h-1.5 w-1.5 rounded-full ${
                            isFresh ? 'bg-emerald-400 animate-pulse' : 'bg-amber-400'
                          }`}
                        />
                        {cal.status || 'CURRENT'}
                      </span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      <div className="pt-2 border-t border-border-gold-subtle/30 text-[11px] text-text-cream-muted flex flex-col sm:flex-row sm:items-center justify-between gap-2">
        <span>
          <strong className="text-text-cream-bright">Point-in-Time Lag Governance:</strong> Ingestion timestamps determine whether observations enter past historical calculation windows.
        </span>
        <span className="text-[10px] text-primary font-mono">
          Calendar Authority: SEBI (Mutual Funds) Regulations, 1996 §Schedule VII
        </span>
      </div>
    </div>
  );
}

