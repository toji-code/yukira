'use client';

import React, { useState, useEffect } from 'react';
import { SchemeOption } from '@/types/domain';
import { searchSchemeOptions } from '@/lib/api/schemes';
import { addPortfolioHoldingsBulk, PortfolioHoldingDto } from '@/lib/api/portfolio';
import { formatCurrency } from '@/lib/utils/formatters';

interface PendingHolding {
  option: SchemeOption;
  units: number;
  costBasisAmount: number | null;
}

interface AddHoldingModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  existingHoldings?: PortfolioHoldingDto[];
}

export function AddHoldingModal({ isOpen, onClose, onSuccess, existingHoldings = [] }: AddHoldingModalProps) {
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [searchResults, setSearchResults] = useState<SchemeOption[]>([]);
  const [searching, setSearching] = useState<boolean>(false);

  const [selectedOption, setSelectedOption] = useState<SchemeOption | null>(null);
  const [unitsInput, setUnitsInput] = useState<string>('');
  const [costBasisInput, setCostBasisInput] = useState<string>('');

  const [pendingHoldings, setPendingHoldings] = useState<PendingHolding[]>([]);
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  // Existing holdings lookup map
  const existingMap = new Map<number, PortfolioHoldingDto>(
    existingHoldings.map((h) => [h.schemeOptionId, h])
  );

  useEffect(() => {
    if (!searchQuery || searchQuery.trim().length < 2) {
      return;
    }

    const timer = setTimeout(async () => {
      try {
        setSearching(true);
        const results = await searchSchemeOptions(searchQuery);
        setSearchResults(results);
      } catch {
        setSearchResults([]);
      } finally {
        setSearching(false);
      }
    }, 250);

    return () => clearTimeout(timer);
  }, [searchQuery]);

  if (!isOpen) return null;

  const handleSelectOption = (opt: SchemeOption) => {
    setSelectedOption(opt);
    setError(null);

    // If already in portfolio, prefill existing holding details
    const existing = existingMap.get(opt.id);
    if (existing) {
      setUnitsInput(existing.units ? String(existing.units) : '');
      setCostBasisInput(existing.costBasisAmount !== null && existing.costBasisAmount !== undefined ? String(existing.costBasisAmount) : '');
    } else {
      setUnitsInput('');
      setCostBasisInput('');
    }
  };

  const handleAddPending = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedOption) return;

    const units = parseFloat(unitsInput);
    if (isNaN(units) || units <= 0) {
      setError('Units must be a positive number greater than 0');
      return;
    }

    let costBasis: number | null = null;
    if (costBasisInput.trim().length > 0) {
      costBasis = parseFloat(costBasisInput);
      if (isNaN(costBasis) || costBasis < 0) {
        setError('Cost basis cannot be negative');
        return;
      }
    }

    // Add to pending or update existing pending entry
    setPendingHoldings((prev) => {
      const filtered = prev.filter((item) => item.option.id !== selectedOption.id);
      return [...filtered, { option: selectedOption, units, costBasisAmount: costBasis }];
    });

    // Reset selection state for next addition
    setSelectedOption(null);
    setUnitsInput('');
    setCostBasisInput('');
    setSearchQuery('');
    setSearchResults([]);
    setError(null);
  };

  const handleRemovePending = (optionId: number) => {
    setPendingHoldings((prev) => prev.filter((item) => item.option.id !== optionId));
  };

  const handleSubmitBulk = async () => {
    if (pendingHoldings.length === 0) return;

    try {
      setSubmitting(true);
      setError(null);

      const requests = pendingHoldings.map((p) => ({
        schemeOptionId: p.option.id,
        units: p.units,
        costBasisAmount: p.costBasisAmount,
      }));

      await addPortfolioHoldingsBulk(requests);
      setPendingHoldings([]);
      onSuccess();
      onClose();
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to add portfolio holdings');
    } finally {
      setSubmitting(false);
    }
  };

  const isExistingInPortfolio = selectedOption ? existingMap.has(selectedOption.id) : false;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
      <div className="panel w-full max-w-2xl max-h-[90vh] flex flex-col bg-surface border-border shadow-2xl overflow-hidden">
        {/* Modal Header */}
        <div className="flex items-center justify-between border-b border-border p-4 bg-surface-raised">
          <div>
            <h3 className="text-[15px] font-semibold text-text-primary flex items-center gap-2">
              <span>Import External Mutual Fund Holdings V1</span>
              <span className="status-badge state-verified text-[10px]">EXACT SCHEME_OPTION_ID</span>
            </h3>
            <p className="font-mono text-[11px] text-text-tertiary mt-0.5">
              Search and add your externally held mutual fund scheme options to your YUKIRA portfolio.
            </p>
          </div>
          <button
            onClick={onClose}
            className="text-text-tertiary hover:text-text-primary font-mono text-lg px-2"
          >
            ✕
          </button>
        </div>

        {/* Modal Body */}
        <div className="p-4 overflow-y-auto space-y-4 flex-1 font-mono text-[11px]">
          {error && (
            <div className="state-panel-error">
              <span>Error: {error}</span>
            </div>
          )}

          {/* Step 1: Fund Search Input */}
          <div className="space-y-2">
            <label className="font-sans font-medium text-text-secondary text-[12px] block">
              Step 1: Search Mutual Fund Option
            </label>
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => {
                const val = e.target.value;
                setSearchQuery(val);
                if (!val || val.trim().length < 2) {
                  setSearchResults([]);
                }
              }}
              placeholder="Type scheme name, AMC, AMFI code (e.g. 118955), or ISIN (e.g. INF179K01UT0)..."
              className="w-full bg-surface-raised border border-border rounded p-2.5 text-text-primary text-[12px] focus:outline-none focus:border-accent"
            />

            {searching && (
              <p className="text-text-tertiary text-[10px]">Searching database universe...</p>
            )}

            {/* Search Results Dropdown */}
            {searchResults.length > 0 && !selectedOption && (
              <div className="max-h-48 overflow-y-auto border border-border rounded bg-surface-raised divide-y divide-border">
                {searchResults.map((opt) => {
                  const isHeld = existingMap.has(opt.id);
                  const schemeName = opt.plan?.scheme?.name || 'Unknown Scheme';
                  const amcName = opt.plan?.scheme?.amc?.name || 'Unknown AMC';
                  return (
                    <div
                      key={opt.id}
                      onClick={() => handleSelectOption(opt)}
                      className="p-2.5 hover:bg-surface/80 cursor-pointer flex items-center justify-between gap-2"
                    >
                      <div>
                        <p className="font-sans font-medium text-text-primary text-[12px]">
                          {schemeName}
                        </p>
                        <div className="flex flex-wrap items-center gap-2 text-[10px] text-text-tertiary mt-0.5">
                          <span>{amcName}</span>
                          <span>• {opt.plan?.planType} Plan</span>
                          <span>• {opt.optionType} Option</span>
                          {opt.amfiCode && <span>• AMFI: {opt.amfiCode}</span>}
                          {opt.isin && <span>• ISIN: {opt.isin}</span>}
                        </div>
                      </div>
                      <div className="flex items-center gap-2">
                        {isHeld && (
                          <span className="status-badge state-candidate text-[9px]">IN PORTFOLIO</span>
                        )}
                        <span className="text-accent text-[11px]">Select →</span>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* Step 2: Selected Scheme Identity Confirmation & Details Input */}
          {selectedOption && (
            <form onSubmit={handleAddPending} className="panel-inset p-3.5 bg-surface space-y-3 border-l-4 border-l-accent">
              <div className="flex items-center justify-between border-b border-border/60 pb-2">
                <div>
                  <span className="font-sans font-semibold text-[13px] text-text-primary block">
                    {selectedOption.plan?.scheme?.name || 'Selected Scheme Option'}
                  </span>
                  <div className="text-[10px] text-text-tertiary mt-0.5 flex flex-wrap gap-2">
                    <span>Option ID: #{selectedOption.id}</span>
                    <span>AMC: {selectedOption.plan?.scheme?.amc?.name}</span>
                    <span>Plan: {selectedOption.plan?.planType}</span>
                    <span>Option: {selectedOption.optionType}</span>
                    {selectedOption.amfiCode && <span>AMFI: {selectedOption.amfiCode}</span>}
                    {selectedOption.isin && <span>ISIN: {selectedOption.isin}</span>}
                  </div>
                </div>
                {isExistingInPortfolio && (
                  <span className="status-badge state-candidate text-[10px]">
                    Updating Existing Holding
                  </span>
                )}
              </div>

              {/* Holding details input */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
                <div>
                  <label className="text-text-muted text-[10px] block mb-1">
                    Units Held <span className="text-rose-400">*</span>
                  </label>
                  <input
                    type="number"
                    step="any"
                    value={unitsInput}
                    onChange={(e) => setUnitsInput(e.target.value)}
                    placeholder="e.g. 100.0"
                    required
                    className="w-full bg-surface-raised border border-border rounded p-2 text-text-primary text-[12px] focus:outline-none focus:border-accent"
                  />
                </div>

                <div>
                  <label className="text-text-muted text-[10px] block mb-1">
                    Cost Basis Amount (Optional per unit/total)
                  </label>
                  <input
                    type="number"
                    step="any"
                    value={costBasisInput}
                    onChange={(e) => setCostBasisInput(e.target.value)}
                    placeholder="e.g. 1500.0 (Leave empty if unknown)"
                    className="w-full bg-surface-raised border border-border rounded p-2 text-text-primary text-[12px] focus:outline-none focus:border-accent"
                  />
                </div>
              </div>

              <div className="flex items-center justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setSelectedOption(null)}
                  className="px-3 py-1.5 rounded border border-border text-text-tertiary hover:text-text-primary"
                >
                  Cancel Selection
                </button>
                <button
                  type="submit"
                  className="px-4 py-1.5 rounded bg-accent text-surface-dark font-medium hover:bg-accent/90"
                >
                  {isExistingInPortfolio ? 'Update Holding in Pending List' : 'Add to Pending Import List'}
                </button>
              </div>
            </form>
          )}

          {/* Pending Import Holdings List */}
          {pendingHoldings.length > 0 && (
            <div className="space-y-2 pt-2 border-t border-border">
              <h4 className="font-sans font-semibold text-[12px] text-text-primary flex items-center justify-between">
                <span>Pending Import List ({pendingHoldings.length})</span>
                <span className="text-text-tertiary text-[10px]">Ready for portfolio ingestion</span>
              </h4>

              <div className="space-y-2">
                {pendingHoldings.map((p) => (
                  <div
                    key={p.option.id}
                    className="panel-inset p-2.5 bg-surface flex items-center justify-between gap-3 border border-border"
                  >
                    <div>
                      <p className="font-sans font-medium text-text-primary text-[12px]">
                        {p.option.plan?.scheme?.name}
                      </p>
                      <p className="text-[10px] text-text-tertiary">
                        Option ID: #{p.option.id} • Units: {p.units} • Cost Basis:{' '}
                        {p.costBasisAmount !== null ? formatCurrency(p.costBasisAmount) : 'Not Provided'}
                      </p>
                    </div>

                    <button
                      type="button"
                      onClick={() => handleRemovePending(p.option.id)}
                      className="text-rose-400 hover:text-rose-300 text-[10px]"
                    >
                      Remove
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Modal Footer */}
        <div className="p-4 border-t border-border bg-surface-raised flex items-center justify-between">
          <button
            onClick={onClose}
            className="px-4 py-2 rounded border border-border text-text-secondary hover:text-text-primary text-[12px] font-mono"
          >
            Close
          </button>

          {pendingHoldings.length > 0 && (
            <button
              onClick={handleSubmitBulk}
              disabled={submitting}
              className="px-5 py-2 rounded bg-emerald-500 text-surface-dark font-mono font-medium hover:bg-emerald-400 disabled:opacity-50 text-[12px]"
            >
              {submitting ? 'Ingesting Portfolio Holdings...' : `Save ${pendingHoldings.length} Holding(s) to Portfolio`}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
