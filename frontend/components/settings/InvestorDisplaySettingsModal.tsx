'use client';

import React, { useState, useEffect } from 'react';
import {
  fetchDisplayPreferences,
  updateDisplayPreferences,
  exportEvidenceBundle,
  InvestorDisplayPreferenceDto,
} from '@/lib/api/investor';

interface InvestorDisplaySettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export function InvestorDisplaySettingsModal({ isOpen, onClose }: InvestorDisplaySettingsModalProps) {
  const [prefs, setPrefs] = useState<InvestorDisplayPreferenceDto | null>(null);
  const [loading, setLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [saveSuccess, setSaveSuccess] = useState(false);

  useEffect(() => {
    if (isOpen) {
      loadPreferences();
    }
  }, [isOpen]);

  async function loadPreferences() {
    setLoading(true);
    try {
      const data = await fetchDisplayPreferences();
      setPrefs(data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }

  async function handleSave(e: React.FormEvent) {
    e.preventDefault();
    if (!prefs) return;
    try {
      const updated = await updateDisplayPreferences(prefs);
      setPrefs(updated);
      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 2000);
    } catch (e) {
      console.error(e);
    }
  }

  async function handleExport() {
    setExporting(true);
    try {
      const bundle = await exportEvidenceBundle();
      const blob = new Blob([JSON.stringify(bundle, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${bundle.exportId}.json`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      console.error(e);
    } finally {
      setExporting(false);
    }
  }

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-sm p-4">
      <div className="relative w-full max-w-xl bg-zinc-950 border border-zinc-800 rounded-xl shadow-2xl p-6 overflow-hidden">
        <div className="flex items-center justify-between pb-4 border-b border-zinc-800">
          <div>
            <h3 className="text-sm font-semibold text-zinc-100 flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-indigo-500" />
              Investor Display & Epistemic Settings
            </h3>
            <p className="text-xs text-zinc-400 mt-0.5">Configure default horizons, unit displays, and audit exports.</p>
          </div>
          <button onClick={onClose} className="text-zinc-400 hover:text-zinc-200">
            ✕
          </button>
        </div>

        {loading ? (
          <div className="py-12 text-center text-xs text-zinc-500">Loading display configuration...</div>
        ) : prefs ? (
          <form onSubmit={handleSave} className="space-y-4 py-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-medium text-zinc-300 mb-1">Default Analysis Horizon</label>
                <select
                  value={prefs.defaultAnalysisPeriod}
                  onChange={(e) => setPrefs({ ...prefs, defaultAnalysisPeriod: e.target.value })}
                  className="w-full px-3 py-1.5 text-xs bg-zinc-900 border border-zinc-800 rounded text-zinc-200 focus:outline-none focus:border-indigo-500"
                >
                  <option value="1Y">1 Year (1Y)</option>
                  <option value="3Y">3 Years (3Y - Recommended)</option>
                  <option value="5Y">5 Years (5Y)</option>
                  <option value="ALL">Full Inception (ALL)</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-medium text-zinc-300 mb-1">Default Workspace Tab</label>
                <select
                  value={prefs.defaultWorkspaceTab}
                  onChange={(e) => setPrefs({ ...prefs, defaultWorkspaceTab: e.target.value })}
                  className="w-full px-3 py-1.5 text-xs bg-zinc-900 border border-zinc-800 rounded text-zinc-200 focus:outline-none focus:border-indigo-500"
                >
                  <option value="OVERVIEW">Overview</option>
                  <option value="WATCHLIST">Watchlist</option>
                  <option value="PORTFOLIO">Portfolio</option>
                  <option value="COMPARISONS">Comparisons</option>
                  <option value="MONITORING">Monitoring</option>
                  <option value="ACTIONS">Investigation Queue</option>
                  <option value="TIMELINE">Research Timeline</option>
                </select>
              </div>
            </div>

            <div className="pt-2">
              <label className="flex items-center gap-3 text-xs text-zinc-300 cursor-pointer">
                <input
                  type="checkbox"
                  checked={prefs.showCandidateMetricWarnings}
                  onChange={(e) => setPrefs({ ...prefs, showCandidateMetricWarnings: e.target.checked })}
                  className="rounded border-zinc-700 bg-zinc-800 text-indigo-500"
                />
                Show Candidate Metric Badges & Limitations (Epistemic Transparency)
              </label>
            </div>

            <div className="pt-4 border-t border-zinc-800/80 flex items-center justify-between">
              <button
                type="button"
                onClick={handleExport}
                disabled={exporting}
                className="text-xs px-3 py-1.5 bg-zinc-900 hover:bg-zinc-800 text-zinc-300 border border-zinc-800 rounded transition-colors"
              >
                {exporting ? 'Generating Bundle...' : '📥 Export Self-Contained Evidence Bundle (JSON)'}
              </button>

              <div className="flex items-center gap-2">
                {saveSuccess && <span className="text-[11px] text-emerald-400">Saved!</span>}
                <button
                  type="submit"
                  className="text-xs px-4 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white font-medium rounded transition-colors"
                >
                  Save Settings
                </button>
              </div>
            </div>
          </form>
        ) : null}
      </div>
    </div>
  );
}

