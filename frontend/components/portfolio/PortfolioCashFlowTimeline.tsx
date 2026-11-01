'use client';

import React, { useState, useEffect } from 'react';
import {
  fetchCashFlowTimeline,
  createResearchSnapshot,
  PortfolioCashFlowTimelineDto,
} from '@/lib/api/investor';

export function PortfolioCashFlowTimeline() {
  const [timeline, setTimeline] = useState<PortfolioCashFlowTimelineDto | null>(null);
  const [loading, setLoading] = useState(false);
  const [showSnapshotModal, setShowSnapshotModal] = useState(false);
  const [snapshotTitle, setSnapshotTitle] = useState('');
  const [thesisNotes, setThesisNotes] = useState('');
  const [snapshotSuccess, setSnapshotSuccess] = useState(false);

  useEffect(() => {
    loadTimeline();
  }, []);

  async function loadTimeline() {
    setLoading(true);
    try {
      const data = await fetchCashFlowTimeline();
      setTimeline(data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }

  async function handleCreateSnapshot(e: React.FormEvent) {
    e.preventDefault();
    if (!snapshotTitle.trim()) return;
    try {
      await createResearchSnapshot({
        snapshotTitle: snapshotTitle.trim(),
        totalValue: timeline?.currentPortfolioValue || 0,
        researchThesis: thesisNotes.trim(),
      });
      setSnapshotSuccess(true);
      setTimeout(() => {
        setSnapshotSuccess(false);
        setShowSnapshotModal(false);
        setSnapshotTitle('');
        setThesisNotes('');
      }, 1500);
    } catch (e) {
      console.error(e);
    }
  }

  return (
    <div className="space-y-4">
      {/* Header Bar */}
      <div className="flex items-center justify-between p-4 bg-zinc-900/40 border border-zinc-800 rounded-xl">
        <div>
          <h3 className="text-sm font-semibold text-zinc-100 flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-emerald-500" />
            Cash-Flow Timeline & Holding-Period Accounting
          </h3>
          <p className="text-xs text-zinc-400 mt-0.5">
            Audit cumulative net capital, multi-class transactions, and point-in-time snapshots (Tasks 183–190).
          </p>
        </div>
        <button
          onClick={() => setShowSnapshotModal(true)}
          className="px-3 py-1.5 text-xs font-medium bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg transition-colors flex items-center gap-1.5"
        >
          📷 Capture Research Snapshot
        </button>
      </div>

      {/* Snapshot Modal */}
      {showSnapshotModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-sm p-4">
          <div className="relative w-full max-w-md bg-zinc-950 border border-zinc-800 rounded-xl shadow-2xl p-6 space-y-4">
            <div className="flex items-center justify-between pb-2 border-b border-zinc-800">
              <h4 className="text-sm font-semibold text-zinc-100">Point-in-Time Research Snapshot</h4>
              <button onClick={() => setShowSnapshotModal(false)} className="text-zinc-400 hover:text-zinc-200">
                ✕
              </button>
            </div>
            <form onSubmit={handleCreateSnapshot} className="space-y-3">
              <div>
                <label className="block text-xs font-medium text-zinc-300 mb-1">Snapshot Title</label>
                <input
                  type="text"
                  value={snapshotTitle}
                  onChange={(e) => setSnapshotTitle(e.target.value)}
                  placeholder="e.g. Q4 2024 Strategic Reallocation Baseline"
                  required
                  className="w-full px-3 py-1.5 text-xs bg-zinc-900 border border-zinc-800 rounded text-zinc-200 focus:outline-none focus:border-emerald-500"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-zinc-300 mb-1">Research Thesis / Notes</label>
                <textarea
                  value={thesisNotes}
                  onChange={(e) => setThesisNotes(e.target.value)}
                  placeholder="Document current rationale, macro hypotheses, and allocation assumptions..."
                  rows={3}
                  className="w-full px-3 py-1.5 text-xs bg-zinc-900 border border-zinc-800 rounded text-zinc-200 focus:outline-none focus:border-emerald-500"
                />
              </div>
              <div className="flex justify-end gap-2 pt-2">
                {snapshotSuccess ? (
                  <span className="text-xs text-emerald-400 self-center">Snapshot Persisted!</span>
                ) : (
                  <>
                    <button
                      type="button"
                      onClick={() => setShowSnapshotModal(false)}
                      className="px-3 py-1.5 text-xs text-zinc-400 hover:text-zinc-200"
                    >
                      Cancel
                    </button>
                    <button
                      type="submit"
                      className="px-4 py-1.5 text-xs bg-emerald-600 hover:bg-emerald-500 text-white rounded font-medium"
                    >
                      Save Immutable Snapshot
                    </button>
                  </>
                )}
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Cash Flow Summary Cards */}
      {timeline && (
        <div className="grid grid-cols-3 gap-3">
          <div className="p-3 bg-zinc-900/30 border border-zinc-800 rounded-lg">
            <span className="text-[10px] text-zinc-400 uppercase tracking-wider block">Cumulative Inflows</span>
            <span className="text-sm font-semibold text-emerald-400">
              ₹{Number(timeline.totalCashInflows).toLocaleString('en-IN', { maximumFractionDigits: 2 })}
            </span>
          </div>
          <div className="p-3 bg-zinc-900/30 border border-zinc-800 rounded-lg">
            <span className="text-[10px] text-zinc-400 uppercase tracking-wider block">Cumulative Outflows</span>
            <span className="text-sm font-semibold text-rose-400">
              ₹{Number(timeline.totalCashOutflows).toLocaleString('en-IN', { maximumFractionDigits: 2 })}
            </span>
          </div>
          <div className="p-3 bg-zinc-900/30 border border-zinc-800 rounded-lg">
            <span className="text-[10px] text-zinc-400 uppercase tracking-wider block">Net Capital Invested</span>
            <span className="text-sm font-semibold text-zinc-100">
              ₹{Number(timeline.netCumulativeInvested).toLocaleString('en-IN', { maximumFractionDigits: 2 })}
            </span>
          </div>
        </div>
      )}

      {/* Events Table */}
      <div className="border border-zinc-800 rounded-xl overflow-hidden bg-zinc-950">
        <div className="px-4 py-2.5 bg-zinc-900/40 border-b border-zinc-800 text-xs font-semibold text-zinc-200">
          Historical Cash Flow Events Ledger
        </div>
        {loading ? (
          <div className="py-8 text-center text-xs text-zinc-500">Loading cash flows...</div>
        ) : !timeline || timeline.cashFlows.length === 0 ? (
          <div className="py-8 text-center text-xs text-zinc-500">
            No transactions found. Add transactions or import a CAS statement to build your ledger.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-zinc-900/60 border-b border-zinc-800 text-[10px] text-zinc-400 uppercase">
                <tr>
                  <th className="py-2.5 px-3">Date</th>
                  <th className="py-2.5 px-3">Scheme</th>
                  <th className="py-2.5 px-3">Type</th>
                  <th className="py-2.5 px-3">Classification</th>
                  <th className="py-2.5 px-3 text-right">Units</th>
                  <th className="py-2.5 px-3 text-right">NAV</th>
                  <th className="py-2.5 px-3 text-right">Amount</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-800/60 font-mono text-[11px]">
                {timeline.cashFlows.map((tx) => (
                  <tr key={tx.transactionId} className="hover:bg-zinc-900/40 transition-colors">
                    <td className="py-2.5 px-3 text-zinc-400">{tx.transactionDate}</td>
                    <td className="py-2.5 px-3 font-sans font-medium text-zinc-200">{tx.schemeName}</td>
                    <td className="py-2.5 px-3">
                      <span
                        className={`px-1.5 py-0.5 rounded text-[10px] font-semibold ${
                          tx.flowDirection === 'INFLOW'
                            ? 'text-emerald-400 bg-emerald-500/10'
                            : tx.flowDirection === 'OUTFLOW'
                            ? 'text-rose-400 bg-rose-500/10'
                            : 'text-zinc-400 bg-zinc-800'
                        }`}
                      >
                        {tx.transactionType}
                      </span>
                    </td>
                    <td className="py-2.5 px-3 text-[10px] text-zinc-400">{tx.classification}</td>
                    <td className="py-2.5 px-3 text-right text-zinc-300">
                      {tx.units ? Number(tx.units).toFixed(3) : '—'}
                    </td>
                    <td className="py-2.5 px-3 text-right text-zinc-300">
                      {tx.nav ? `₹${Number(tx.nav).toFixed(2)}` : '—'}
                    </td>
                    <td
                      className={`py-2.5 px-3 text-right font-semibold ${
                        tx.flowDirection === 'INFLOW' ? 'text-emerald-400' : 'text-rose-400'
                      }`}
                    >
                      {tx.amount ? `₹${Math.abs(Number(tx.amount)).toLocaleString('en-IN')}` : '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

