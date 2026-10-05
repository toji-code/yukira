'use client';

import React, { useState, useEffect } from 'react';
import {
  fetchTargetAllocation,
  saveTargetAllocation,
  deleteTargetAllocation,
  fetchPortfolioDriftAnalysis,
  TargetAllocationDto,
  TargetAllocationItemDto,
  PortfolioDriftAnalysisDto
} from '@/lib/api/portfolio';
import { formatCurrency } from '@/lib/utils/formatters';

const DEFAULT_CATEGORIES = [
  'Equity Scheme',
  'Debt Scheme',
  'Hybrid Scheme',
  'Solution Oriented Scheme',
  'Other Scheme'
];

interface PortfolioDriftViewProps {
  hasHoldings?: boolean;
}

export function PortfolioDriftView({ hasHoldings = true }: PortfolioDriftViewProps) {
  const [target, setTarget] = useState<TargetAllocationDto | null>(null);
  const [drift, setDrift] = useState<PortfolioDriftAnalysisDto | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [isEditing, setIsEditing] = useState<boolean>(false);

  // Edit form state
  const [editItems, setEditItems] = useState<TargetAllocationItemDto[]>([]);

  const loadData = async () => {
    try {
      setLoading(true);
      setError(null);
      const [tData, dData] = await Promise.all([
        fetchTargetAllocation(),
        fetchPortfolioDriftAnalysis()
      ]);
      setTarget(tData);
      setDrift(dData);
      if (tData.items.length > 0) {
        setEditItems(tData.items);
      } else {
        // Initialize default category target template (e.g. 100% Equity)
        setEditItems([
          { targetType: 'CATEGORY', categoryName: 'Equity Scheme', displayName: 'Equity Scheme', targetWeightPercentage: 60 },
          { targetType: 'CATEGORY', categoryName: 'Debt Scheme', displayName: 'Debt Scheme', targetWeightPercentage: 40 }
        ]);
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to load target allocation and drift analysis';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    let isMounted = true;

    async function init() {
      try {
        const [tData, dData] = await Promise.all([
          fetchTargetAllocation(),
          fetchPortfolioDriftAnalysis()
        ]);
        if (!isMounted) return;
        setTarget(tData);
        setDrift(dData);
        if (tData.items.length > 0) {
          setEditItems(tData.items);
        } else {
          setEditItems([
            { targetType: 'CATEGORY', categoryName: 'Equity Scheme', displayName: 'Equity Scheme', targetWeightPercentage: 60 },
            { targetType: 'CATEGORY', categoryName: 'Debt Scheme', displayName: 'Debt Scheme', targetWeightPercentage: 40 }
          ]);
        }
      } catch (err: unknown) {
        if (!isMounted) return;
        const msg = err instanceof Error ? err.message : 'Failed to load target allocation and drift analysis';
        setError(msg);
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    }

    init();

    return () => {
      isMounted = false;
    };
  }, []);

  if (!hasHoldings) {
    return (
      <div className="panel p-4 space-y-3 border-l-4 border-l-emerald-500">
        <h3 className="text-[14px] font-semibold text-text-primary">Portfolio Analytical Rebalancing & Drift Inspection V1</h3>
        <p className="font-mono text-[11px] text-text-tertiary">
          No external mutual fund holdings found in your portfolio. Add holdings above to calculate analytical allocation drift against target category weights.
        </p>
      </div>
    );
  }

  const totalEditWeight = editItems.reduce((acc, curr) => acc + (Number(curr.targetWeightPercentage) || 0), 0);
  const isValidEditTotal = Math.abs(totalEditWeight - 100) < 0.001;

  const handleItemWeightChange = (index: number, val: string) => {
    const num = parseFloat(val) || 0;
    const updated = [...editItems];
    updated[index] = { ...updated[index], targetWeightPercentage: num };
    setEditItems(updated);
  };

  const handleAddCategoryRow = (categoryName: string) => {
    if (editItems.some(i => i.categoryName === categoryName)) return;
    setEditItems([...editItems, {
      targetType: 'CATEGORY',
      categoryName,
      displayName: categoryName,
      targetWeightPercentage: 0
    }]);
  };

  const handleRemoveRow = (index: number) => {
    setEditItems(editItems.filter((_, i) => i !== index));
  };

  const handleSaveTarget = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!isValidEditTotal) {
      setError(`Target allocation weights total ${totalEditWeight.toFixed(2)}%, which does not equal 100.00%.`);
      return;
    }

    try {
      setSubmitting(true);
      setError(null);
      await saveTargetAllocation({
        items: editItems,
        totalTargetWeightPercentage: totalEditWeight,
        isValidTotal: true
      });
      setIsEditing(false);
      await loadData();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to save target allocation';
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeleteTarget = async () => {
    if (!confirm('Are you sure you want to clear your target allocation?')) return;
    try {
      setSubmitting(true);
      setError(null);
      await deleteTargetAllocation();
      setIsEditing(false);
      await loadData();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to clear target allocation';
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="py-12 text-center text-text-tertiary">
        <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-accent mb-3"></div>
        <p className="text-xs font-mono">Loading target allocation & drift inspection...</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header & Mode Switcher */}
      <div className="panel flex flex-col md:flex-row md:items-center justify-between gap-4 p-5">
        <div>
          <div className="flex items-center space-x-2">
            <h2 className="text-xs font-semibold text-text-primary uppercase tracking-wider">
              Portfolio Allocation & Drift Inspection V1
            </h2>
            <span className="status-badge state-candidate font-mono">
              ANALYTICAL INSPECTION
            </span>
          </div>
          <p className="text-xs text-text-secondary mt-1">
            Compare investor-defined target weights against deterministic current portfolio valuation
          </p>
        </div>

        <div className="flex items-center space-x-3">
          {target && target.items.length > 0 && !isEditing && (
            <button
              onClick={handleDeleteTarget}
              disabled={submitting}
              className="btn btn-secondary btn-sm text-risk-fg"
            >
              Clear Target
            </button>
          )}
          <button
            onClick={() => setIsEditing(!isEditing)}
            className="btn btn-primary btn-sm"
          >
            <span>{isEditing ? 'Cancel Editing' : (target && target.items.length > 0 ? 'Edit Target Allocation' : '+ Set Target Allocation')}</span>
          </button>
        </div>
      </div>

      {error && (
        <div className="p-4 bg-risk-bg border border-risk-border rounded-xl text-risk-fg text-xs flex items-center justify-between">
          <span>{error}</span>
          <button onClick={() => setError(null)} className="text-risk-fg hover:underline text-xs">Dismiss</button>
        </div>
      )}

      {/* TARGET ALLOCATION EDITOR */}
      {isEditing && (
        <form onSubmit={handleSaveTarget} className="panel p-5 space-y-4">
          <div className="flex items-center justify-between border-b border-border pb-3">
            <div>
              <h3 className="text-xs font-semibold text-text-primary">Define Target Allocation Weights</h3>
              <p className="text-[11px] text-text-secondary">Total target weight must equal exactly 100.00%</p>
            </div>
            <div className={`font-mono text-xs px-3 py-1 rounded font-bold ${
              isValidEditTotal ? 'status-badge state-approved' : 'status-badge state-risk'
            }`}>
              Total: {totalEditWeight.toFixed(2)}% {isValidEditTotal ? '✓ Valid' : '✕ Must equal 100%'}
            </div>
          </div>

          <div className="space-y-3">
            {editItems.map((item, idx) => (
              <div key={idx} className="flex items-center gap-3 bg-surface-inset p-3 rounded-lg border border-border text-xs">
                <span className="font-mono text-text-tertiary w-24 uppercase text-[10px]">{item.targetType}</span>
                <span className="font-medium text-text-primary flex-1">{item.displayName}</span>
                <div className="flex items-center gap-1.5 font-mono">
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    max="100"
                    value={item.targetWeightPercentage}
                    onChange={(e) => handleItemWeightChange(idx, e.target.value)}
                    className="field field-mono w-24 text-right"
                  />
                  <span className="text-text-tertiary">%</span>
                </div>
                <button
                  type="button"
                  onClick={() => handleRemoveRow(idx)}
                  className="text-text-tertiary hover:text-risk-fg p-1 rounded"
                >
                  ✕
                </button>
              </div>
            ))}
          </div>

          <div className="flex flex-wrap items-center justify-between gap-3 pt-3 border-t border-border">
            <div className="flex items-center gap-2 text-xs">
              <span className="text-text-tertiary font-mono text-[11px]">Add Category:</span>
              {DEFAULT_CATEGORIES.map((cat) => (
                <button
                  key={cat}
                  type="button"
                  onClick={() => handleAddCategoryRow(cat)}
                  disabled={editItems.some(i => i.categoryName === cat)}
                  className="btn btn-secondary btn-sm text-[11px] font-mono"
                >
                  + {cat.replace(' Scheme', '')}
                </button>
              ))}
            </div>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setIsEditing(false)}
                className="btn btn-secondary btn-sm"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={!isValidEditTotal || submitting}
                className="btn btn-primary btn-sm"
              >
                Save Target Allocation
              </button>
            </div>
          </div>
        </form>
      )}

      {/* DRIFT ANALYSIS RESULTS */}
      {drift && (
        <div className="space-y-6">
          {drift.status === 'NO_TARGET' && !isEditing && (
            <div className="panel p-8 text-center space-y-3">
              <div className="w-12 h-12 rounded-full bg-accent/10 border border-accent/20 text-accent flex items-center justify-center mx-auto text-xl">
                🎯
              </div>
              <h3 className="text-sm font-semibold text-text-primary">No Target Allocation Defined</h3>
              <p className="text-xs text-text-secondary max-w-md mx-auto leading-relaxed">
                Set an investor-defined target allocation by category or scheme option to measure factual allocation differences against your portfolio valuation.
              </p>
              <button
                onClick={() => setIsEditing(true)}
                className="btn btn-primary btn-sm"
              >
                Set Target Allocation
              </button>
            </div>
          )}

          {drift.status !== 'NO_TARGET' && (
            <>
              {/* Drift Summary Cards */}
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4 font-mono text-xs">
                <div className="panel p-4">
                  <span className="text-text-secondary block text-[11px]">Covered Portfolio Value</span>
                  <span className="text-base font-bold text-text-primary mt-1 block">
                    {drift.coveredPortfolioValue !== null ? formatCurrency(drift.coveredPortfolioValue) : 'Unavailable'}
                  </span>
                  <span className="text-[10px] text-text-tertiary">Deterministically valued</span>
                </div>

                <div className="panel p-4">
                  <span className="text-text-secondary block text-[11px]">Target Weight Total</span>
                  <span className="text-base font-bold text-accent mt-1 block">
                    {drift.totalTargetWeightPercentage.toFixed(2)}%
                  </span>
                  <span className="text-[10px] text-text-tertiary">Investor-defined</span>
                </div>

                <div className="panel p-4">
                  <span className="text-text-secondary block text-[11px]">Current Weight Total</span>
                  <span className="text-base font-bold text-text-primary mt-1 block">
                    {drift.totalCurrentWeightPercentage.toFixed(2)}%
                  </span>
                  <span className="text-[10px] text-text-tertiary">Portfolio exposure</span>
                </div>

                <div className="panel p-4">
                  <span className="text-text-secondary block text-[11px]">Total Absolute Drift</span>
                  <span className="text-base font-bold text-candidate-fg mt-1 block">
                    {drift.totalAbsoluteDriftPercentagePoints.toFixed(2)} pp
                  </span>
                  <span className="text-[10px] text-text-tertiary">Sum of absolute differences</span>
                </div>
              </div>

              {/* Allocation Comparison Table */}
              <div className="panel p-5 space-y-4">
                <div className="flex items-center justify-between border-b border-border pb-3">
                  <h3 className="text-xs font-semibold text-text-primary uppercase tracking-wider">
                    Target vs Current Allocation Comparison
                  </h3>
                  <span className="text-[11px] text-text-secondary font-mono">
                    Drift = Current % − Target %
                  </span>
                </div>

                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs border-collapse font-sans">
                    <thead>
                      <tr className="border-b border-border text-text-secondary bg-surface-inset font-mono">
                        <th className="p-2.5 font-medium">Allocation Item</th>
                        <th className="p-2.5 font-medium text-right">Target %</th>
                        <th className="p-2.5 font-medium text-right">Current %</th>
                        <th className="p-2.5 font-medium text-right">Drift (pp)</th>
                        <th className="p-2.5 font-medium text-center">Direction</th>
                        <th className="p-2.5 font-medium text-right">Valuation</th>
                        <th className="p-2.5 font-medium text-center">Fund Score V1</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border font-mono">
                      {drift.itemDrifts.map((item, idx) => (
                        <tr key={idx} className="hover:bg-surface-raised transition-colors">
                          <td className="p-2.5 font-sans">
                            <div className="font-semibold text-text-primary">{item.displayName}</div>
                            <div className="text-[10px] text-text-tertiary font-mono">
                              Type: {item.targetType} {item.schemeOptionId ? `(OptID: ${item.schemeOptionId})` : ''}
                            </div>
                          </td>
                          <td className="p-2.5 text-right text-accent font-bold">
                            {item.targetWeightPercentage.toFixed(2)}%
                          </td>
                          <td className="p-2.5 text-right text-text-primary font-semibold">
                            {item.currentWeightPercentage.toFixed(2)}%
                          </td>
                          <td className={`p-2.5 text-right font-bold ${
                            item.driftPercentagePoints > 0 ? 'text-candidate-fg' : (item.driftPercentagePoints < 0 ? 'text-operational-fg' : 'text-text-secondary')
                          }`}>
                            {item.driftPercentagePoints >= 0 ? '+' : ''}{item.driftPercentagePoints.toFixed(2)} pp
                          </td>
                          <td className="p-2.5 text-center">
                            <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                              item.driftDirection === 'OVER_ALLOCATED' ? 'status-badge state-candidate' :
                              item.driftDirection === 'UNDER_ALLOCATED' ? 'status-badge state-operational' :
                              'status-badge state-approved'
                            }`}>
                              {item.driftDirection}
                            </span>
                          </td>
                          <td className="p-2.5 text-right text-text-secondary">
                            {item.currentValue !== null ? formatCurrency(item.currentValue) : '₹0.00'}
                          </td>
                          <td className="p-2.5 text-center">
                            {item.yukiraScore !== null ? (
                              <span className="status-badge state-approved font-mono font-bold text-[11px]">
                                {item.yukiraScore.toFixed(2)}
                              </span>
                            ) : (
                              <span className="text-text-tertiary text-[10px] font-sans">Score Unavailable</span>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* Unmapped Holdings / Targets Section */}
              {(drift.unmappedHoldings.length > 0 || drift.unmappedTargets.length > 0) && (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {drift.unmappedHoldings.length > 0 && (
                    <div className="panel p-5 space-y-3">
                      <h4 className="text-xs font-semibold text-text-primary uppercase tracking-wider">
                        Current Holdings Without Defined Target ({drift.unmappedHoldings.length})
                      </h4>
                      <div className="space-y-2">
                        {drift.unmappedHoldings.map((h) => (
                          <div key={h.schemeOptionId} className="p-3 bg-surface-inset border border-border rounded-lg text-xs space-y-1">
                            <div className="font-semibold text-text-primary">{h.fundName}</div>
                            <div className="text-[11px] text-text-secondary font-mono flex items-center justify-between">
                              <span>Category: {h.category}</span>
                              <span className="text-text-primary font-bold">{h.currentWeightPercentage.toFixed(2)}% exposure</span>
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {drift.unmappedTargets.length > 0 && (
                    <div className="panel p-5 space-y-3">
                      <h4 className="text-xs font-semibold text-text-primary uppercase tracking-wider">
                        Target Items Without Current Holdings ({drift.unmappedTargets.length})
                      </h4>
                      <div className="space-y-2">
                        {drift.unmappedTargets.map((ut, idx) => (
                          <div key={idx} className="p-3 bg-surface-inset border border-border rounded-lg text-xs flex items-center justify-between">
                            <span className="font-semibold text-text-primary">{ut.displayName}</span>
                            <span className="font-mono text-accent font-bold">Target: {ut.targetWeightPercentage.toFixed(2)}%</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              )}

              {/* Data Quality & Investigation Questions */}
              <div className="panel p-5 space-y-3">
                <h3 className="text-xs font-semibold text-text-primary uppercase tracking-wider">
                  Questions Worth Investigating
                </h3>
                <ul className="space-y-1.5 text-xs text-text-secondary list-disc list-inside">
                  {drift.investigationQuestions.map((q, idx) => (
                    <li key={idx} className="leading-relaxed">{q}</li>
                  ))}
                </ul>
              </div>

              {/* Regulatory Disclaimers */}
              <div className="panel bg-surface-inset p-5 space-y-2 text-[11px] text-text-tertiary">
                {drift.disclaimers.map((disc, idx) => (
                  <p key={idx} className="border-l-2 border-candidate-fg pl-2.5">{disc}</p>
                ))}
              </div>
            </>
          )}
        </div>
      )}
    </div>
  );
}
