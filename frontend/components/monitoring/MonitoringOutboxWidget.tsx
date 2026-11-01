'use client';

import React, { useEffect, useState } from 'react';
import {
  getMonitoringRuns,
  getNotificationOutbox,
  dispatchNotifications,
  MonitoringEvaluationRun,
  NotificationDelivery,
} from '@/lib/api/reconciliation';

export function MonitoringOutboxWidget() {
  const [runs, setRuns] = useState<MonitoringEvaluationRun[]>([]);
  const [outbox, setOutbox] = useState<NotificationDelivery[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isDispatching, setIsDispatching] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'outbox' | 'runs'>('outbox');

  const fetchData = async () => {
    try {
      setIsLoading(true);
      setError(null);
      const [runsData, outboxData] = await Promise.all([
        getMonitoringRuns(10).catch(() => []),
        getNotificationOutbox(20).catch(() => []),
      ]);
      setRuns(runsData);
      setOutbox(outboxData);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load monitoring outbox data');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const handleDispatch = async () => {
    try {
      setIsDispatching(true);
      await dispatchNotifications(10);
      await fetchData();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Dispatch failed');
    } finally {
      setIsDispatching(false);
    }
  };

  return (
    <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-6 space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between pb-4 border-b border-slate-800 gap-3">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <span className="font-mono text-xs px-2 py-0.5 rounded bg-emerald-950 text-emerald-400 border border-emerald-800 font-semibold">
              TASKS 304–306
            </span>
            <span className="font-mono text-xs px-2 py-0.5 rounded bg-blue-950 text-blue-400 border border-blue-800">
              RELIABLE OUTBOX
            </span>
          </div>
          <h3 className="text-lg font-bold text-slate-100">
            Monitoring Evaluation &amp; Notification Outbox
          </h3>
          <p className="mt-1 text-xs text-slate-400">
            Transactional outbox pattern for continuous monitoring events, channel deliveries (In-App, Email, Webhook), and idempotency tracking.
          </p>
        </div>

        {/* Action Controls */}
        <div className="flex items-center gap-2 self-start sm:self-auto">
          <div className="inline-flex rounded-lg bg-slate-950 p-1 border border-slate-800">
            <button
              type="button"
              onClick={() => setActiveTab('outbox')}
              className={`px-3 py-1.5 rounded-md text-xs font-medium font-mono transition-colors ${
                activeTab === 'outbox'
                  ? 'bg-blue-600 text-white'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              Outbox Queue ({outbox.length})
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('runs')}
              className={`px-3 py-1.5 rounded-md text-xs font-medium font-mono transition-colors ${
                activeTab === 'runs'
                  ? 'bg-blue-600 text-white'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              Evaluation Runs ({runs.length})
            </button>
          </div>

          <button
            type="button"
            onClick={handleDispatch}
            disabled={isDispatching}
            className="px-3 py-1.5 rounded-md text-xs font-semibold font-mono bg-emerald-700 hover:bg-emerald-600 text-white disabled:opacity-50 transition-colors"
          >
            {isDispatching ? 'Dispatching...' : 'Dispatch Batch'}
          </button>
        </div>
      </div>

      {/* Epistemic Architecture Notice */}
      <div className="rounded-lg bg-blue-950/30 border border-blue-800/60 p-3.5 text-xs text-blue-200 space-y-1">
        <div className="font-semibold text-blue-300 flex items-center gap-1.5">
          <span>ℹ️</span> DECOUPLED NOTIFICATION DELIVERY ARCHITECTURE
        </div>
        <p className="text-slate-300 text-[11px] leading-relaxed">
          Monitoring evaluation jobs execute within strict point-in-time boundaries and record alerts directly to the transactional outbox. Deliveries across third-party channels (SMTP, Webhooks) execute asynchronously with exponential backoff and never block quantitative pipelines. Unconfigured channels remain safely in <span className="font-mono text-amber-300">CONFIGURATION_REQUIRED</span> state without silent loss.
        </p>
      </div>

      {isLoading ? (
        <div className="p-8 text-center text-xs font-mono text-slate-400 animate-pulse">
          Loading monitoring outbox and evaluation run states...
        </div>
      ) : error ? (
        <div className="p-4 bg-red-950/30 border border-red-800 rounded text-xs text-red-300 font-mono">
          {error}
        </div>
      ) : activeTab === 'outbox' ? (
        outbox.length === 0 ? (
          <div className="p-8 text-center text-slate-400 text-xs font-mono border border-dashed border-slate-800 rounded-lg">
            No notifications in outbox queue. Any triggered alerts will appear here pending channel dispatch.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs font-mono border-collapse">
              <thead>
                <tr className="border-b border-slate-800 text-[11px] text-slate-400 uppercase bg-slate-950/50">
                  <th className="py-2.5 px-3">Delivery ID</th>
                  <th className="py-2.5 px-3">Event Ref</th>
                  <th className="py-2.5 px-3">Channel</th>
                  <th className="py-2.5 px-3">Recipient Target</th>
                  <th className="py-2.5 px-3">Status</th>
                  <th className="py-2.5 px-3">Attempts</th>
                  <th className="py-2.5 px-3">Queued At</th>
                  <th className="py-2.5 px-3 text-right">Details</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/50">
                {outbox.map((d) => (
                  <tr key={d.id} className="hover:bg-slate-800/30 transition-colors">
                    <td className="py-3 px-3 text-slate-200">#{d.id}</td>
                    <td className="py-3 px-3 text-slate-400">Event #{d.eventId}</td>
                    <td className="py-3 px-3">
                      <span className="px-1.5 py-0.5 rounded text-[10px] font-semibold bg-slate-800 text-slate-300">
                        {d.channel}
                      </span>
                    </td>
                    <td className="py-3 px-3 text-slate-300 truncate max-w-[150px]">{d.recipientTarget}</td>
                    <td className="py-3 px-3">
                      <span
                        className={`inline-block px-2 py-0.5 rounded text-[10px] font-semibold border ${
                          d.status === 'ACCEPTED'
                            ? 'bg-emerald-950 text-emerald-400 border-emerald-800'
                            : d.status === 'QUEUED'
                            ? 'bg-blue-950 text-blue-400 border-blue-800'
                            : d.status === 'CONFIGURATION_REQUIRED'
                            ? 'bg-amber-950 text-amber-300 border-amber-800'
                            : 'bg-rose-950 text-rose-400 border-rose-800'
                        }`}
                      >
                        {d.status}
                      </span>
                    </td>
                    <td className="py-3 px-3 text-slate-300">{d.attemptCount}</td>
                    <td className="py-3 px-3 text-slate-400 text-[11px]">{d.queuedAt}</td>
                    <td className="py-3 px-3 text-right text-[11px] text-slate-400">
                      {d.failureReason || '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
      ) : (
        /* Evaluation Runs */
        runs.length === 0 ? (
          <div className="p-8 text-center text-slate-400 text-xs font-mono border border-dashed border-slate-800 rounded-lg">
            No monitoring evaluation runs recorded.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs font-mono border-collapse">
              <thead>
                <tr className="border-b border-slate-800 text-[11px] text-slate-400 uppercase bg-slate-950/50">
                  <th className="py-2.5 px-3">Run Identifier</th>
                  <th className="py-2.5 px-3">Cutoff</th>
                  <th className="py-2.5 px-3">Status</th>
                  <th className="py-2.5 px-3">Rules Evaluated</th>
                  <th className="py-2.5 px-3">Alerts Fired</th>
                  <th className="py-2.5 px-3">Duration</th>
                  <th className="py-2.5 px-3 text-right">Executed At</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/50">
                {runs.map((r) => (
                  <tr key={r.id} className="hover:bg-slate-800/30 transition-colors">
                    <td className="py-3 px-3 text-slate-200">{r.runIdentifier}</td>
                    <td className="py-3 px-3 text-slate-400">{r.evaluationCutoff}</td>
                    <td className="py-3 px-3">
                      <span
                        className={`inline-block px-2 py-0.5 rounded text-[10px] font-semibold border ${
                          r.status === 'COMPLETED'
                            ? 'bg-emerald-950 text-emerald-400 border-emerald-800'
                            : r.status === 'IN_PROGRESS'
                            ? 'bg-blue-950 text-blue-400 border-blue-800'
                            : 'bg-rose-950 text-rose-400 border-rose-800'
                        }`}
                      >
                        {r.status}
                      </span>
                    </td>
                    <td className="py-3 px-3 text-slate-300">{r.evaluatedRulesCount}</td>
                    <td className="py-3 px-3 font-semibold text-slate-100">{r.eventsTriggeredCount}</td>
                    <td className="py-3 px-3 text-slate-400">{r.executionDurationMs} ms</td>
                    <td className="py-3 px-3 text-right text-slate-400 text-[11px]">{r.executedAt}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
      )}
    </div>
  );
}

