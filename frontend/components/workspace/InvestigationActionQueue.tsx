'use client';

import React, { useState, useEffect } from 'react';
import {
  fetchInvestigationActions,
  createInvestigationAction,
  updateActionStatus,
  InvestorInvestigationActionDto,
} from '@/lib/api/investor';

export function InvestigationActionQueue() {
  const [actions, setActions] = useState<InvestorInvestigationActionDto[]>([]);
  const [statusFilter, setStatusFilter] = useState<string>('OPEN');
  const [loading, setLoading] = useState(false);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [newDesc, setNewDesc] = useState('');
  const [newPriority, setNewPriority] = useState('MEDIUM');

  useEffect(() => {
    loadActions();
  }, [statusFilter]);

  async function loadActions() {
    setLoading(true);
    try {
      const data = await fetchInvestigationActions(statusFilter === 'ALL' ? undefined : statusFilter);
      setActions(data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    if (!newTitle.trim()) return;
    try {
      const created = await createInvestigationAction({
        actionTitle: newTitle.trim(),
        actionDescription: newDesc.trim(),
        priority: newPriority,
      });
      setActions((prev) => [created, ...prev]);
      setShowCreateModal(false);
      setNewTitle('');
      setNewDesc('');
    } catch (e) {
      console.error(e);
    }
  }

  async function handleStatusChange(id: number, nextStatus: string) {
    try {
      const updated = await updateActionStatus(id, nextStatus);
      setActions((prev) => prev.map((a) => (a.id === id ? updated : a)));
    } catch (e) {
      console.error(e);
    }
  }

  return (
    <div className="space-y-4">
      {/* Header Bar */}
      <div className="flex flex-wrap items-center justify-between gap-3 p-4 bg-zinc-900/40 border border-zinc-800 rounded-xl">
        <div>
          <h3 className="text-sm font-semibold text-zinc-100 flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-amber-500" />
            Integrated Investigation Action Queue
          </h3>
          <p className="text-xs text-zinc-400 mt-0.5">
            Operational follow-up items from fund reviews, stress findings, and monitoring triggers (Task 195).
          </p>
        </div>
        <div className="flex items-center gap-2">
          <div className="flex gap-1 bg-zinc-950 p-1 border border-zinc-800 rounded-lg">
            {['OPEN', 'IN_PROGRESS', 'COMPLETED', 'ALL'].map((s) => (
              <button
                key={s}
                onClick={() => setStatusFilter(s)}
                className={`px-2.5 py-1 text-[11px] font-medium rounded transition-colors ${
                  statusFilter === s
                    ? 'bg-zinc-800 text-zinc-100'
                    : 'text-zinc-400 hover:text-zinc-200'
                }`}
              >
                {s}
              </button>
            ))}
          </div>
          <button
            onClick={() => setShowCreateModal(true)}
            className="px-3 py-1.5 text-xs font-medium bg-amber-600 hover:bg-amber-500 text-white rounded-lg transition-colors whitespace-nowrap"
          >
            + New Action
          </button>
        </div>
      </div>

      {/* Create Modal */}
      {showCreateModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-sm p-4">
          <div className="relative w-full max-w-md bg-zinc-950 border border-zinc-800 rounded-xl shadow-2xl p-6 space-y-4">
            <div className="flex items-center justify-between pb-2 border-b border-zinc-800">
              <h4 className="text-sm font-semibold text-zinc-100">Enqueue Investigation Action</h4>
              <button onClick={() => setShowCreateModal(false)} className="text-zinc-400 hover:text-zinc-200">
                ✕
              </button>
            </div>
            <form onSubmit={handleCreate} className="space-y-3">
              <div>
                <label className="block text-xs font-medium text-zinc-300 mb-1">Action Title</label>
                <input
                  type="text"
                  value={newTitle}
                  onChange={(e) => setNewTitle(e.target.value)}
                  placeholder="e.g. Audit Large Cap exposure drift vs Scheme Mandate"
                  required
                  className="w-full px-3 py-1.5 text-xs bg-zinc-900 border border-zinc-800 rounded text-zinc-200 focus:outline-none focus:border-amber-500"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-zinc-300 mb-1">Description / Hypothesis</label>
                <textarea
                  value={newDesc}
                  onChange={(e) => setNewDesc(e.target.value)}
                  placeholder="Add context on what evidence needs further review..."
                  rows={3}
                  className="w-full px-3 py-1.5 text-xs bg-zinc-900 border border-zinc-800 rounded text-zinc-200 focus:outline-none focus:border-amber-500"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-zinc-300 mb-1">Priority</label>
                <select
                  value={newPriority}
                  onChange={(e) => setNewPriority(e.target.value)}
                  className="w-full px-3 py-1.5 text-xs bg-zinc-900 border border-zinc-800 rounded text-zinc-200 focus:outline-none focus:border-amber-500"
                >
                  <option value="LOW">LOW</option>
                  <option value="MEDIUM">MEDIUM</option>
                  <option value="HIGH">HIGH</option>
                  <option value="URGENT">URGENT</option>
                </select>
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  className="px-3 py-1.5 text-xs text-zinc-400 hover:text-zinc-200"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-1.5 text-xs bg-amber-600 hover:bg-amber-500 text-white rounded font-medium"
                >
                  Save Action
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Action Cards */}
      {loading ? (
        <div className="py-12 text-center text-xs text-zinc-500">Loading action queue...</div>
      ) : actions.length === 0 ? (
        <div className="py-12 text-center text-xs text-zinc-500 p-6 border border-zinc-800 rounded-xl bg-zinc-950">
          No investigation actions found for status &quot;{statusFilter}&quot;.
        </div>
      ) : (
        <div className="space-y-3">
          {actions.map((a) => {
            const priorityColor =
              a.priority === 'URGENT'
                ? 'text-rose-400 bg-rose-500/10 border-rose-500/30'
                : a.priority === 'HIGH'
                ? 'text-amber-400 bg-amber-500/10 border-amber-500/30'
                : 'text-zinc-300 bg-zinc-800 border-zinc-700';

            return (
              <div
                key={a.id}
                className="p-4 bg-zinc-950 border border-zinc-800 rounded-xl hover:border-zinc-700/80 transition-colors flex flex-col md:flex-row md:items-center justify-between gap-4"
              >
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <span className={`px-2 py-0.5 rounded text-[10px] font-semibold border ${priorityColor}`}>
                      {a.priority}
                    </span>
                    <h4 className="text-xs font-semibold text-zinc-100">{a.actionTitle}</h4>
                  </div>
                  {a.actionDescription && <p className="text-xs text-zinc-400">{a.actionDescription}</p>}
                  <div className="flex items-center gap-3 text-[10px] text-zinc-500 pt-1">
                    <span>Created: {new Date(a.createdAt).toLocaleDateString()}</span>
                    <span>Status: {a.status}</span>
                  </div>
                </div>

                <div className="flex items-center gap-2 shrink-0">
                  {a.status === 'OPEN' && (
                    <button
                      onClick={() => handleStatusChange(a.id, 'IN_PROGRESS')}
                      className="text-[11px] px-3 py-1 bg-sky-950/40 text-sky-300 border border-sky-800/60 rounded hover:bg-sky-900/40 transition-colors"
                    >
                      Start Investigation
                    </button>
                  )}
                  {a.status !== 'COMPLETED' && (
                    <button
                      onClick={() => handleStatusChange(a.id, 'COMPLETED')}
                      className="text-[11px] px-3 py-1 bg-emerald-950/40 text-emerald-300 border border-emerald-800/60 rounded hover:bg-emerald-900/40 transition-colors"
                    >
                      Mark Complete ✓
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

