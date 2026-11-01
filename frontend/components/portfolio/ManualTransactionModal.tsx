'use client';

import React, { useState, useEffect } from 'react';
import { SchemeOption } from '@/types/domain';
import { searchSchemeOptions } from '@/lib/api/schemes';
import {
  createManualTransaction,
  CreateManualTransactionRequest,
  TransactionType,
  InvestorTransactionDto
} from '@/lib/api/transactions';

interface ManualTransactionModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (newTx: InvestorTransactionDto) => void;
  defaultSchemeOptionId?: number;
  availableOptions?: SchemeOption[];
}

const TRANSACTION_TYPES: { type: TransactionType; label: string; group: 'Inflow' | 'Outflow' | 'Internal / Other' }[] = [
  { type: 'PURCHASE', label: 'Purchase (Lump Sum)', group: 'Inflow' },
  { type: 'SIP_PURCHASE', label: 'SIP Purchase', group: 'Inflow' },
  { type: 'SWITCH_IN', label: 'Switch In (Transfer From Scheme)', group: 'Inflow' },
  { type: 'STP_PURCHASE', label: 'STP Purchase', group: 'Inflow' },
  { type: 'REDEMPTION', label: 'Redemption (Withdrawal)', group: 'Outflow' },
  { type: 'STP_REDEMPTION', label: 'STP Redemption', group: 'Outflow' },
  { type: 'SWITCH_OUT', label: 'Switch Out (Transfer To Scheme)', group: 'Outflow' },
  { type: 'DIVIDEND_PAYOUT', label: 'Dividend Payout (Cash Distributed)', group: 'Outflow' },
  { type: 'DIVIDEND_REINVESTMENT', label: 'Dividend Reinvestment', group: 'Internal / Other' },
  { type: 'BONUS', label: 'Bonus Units', group: 'Internal / Other' },
  { type: 'OTHER', label: 'Other Transaction', group: 'Internal / Other' },
];

export function ManualTransactionModal({
  isOpen,
  onClose,
  onSuccess,
  defaultSchemeOptionId,
  availableOptions = []
}: ManualTransactionModalProps) {
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [searchResults, setSearchResults] = useState<SchemeOption[]>(availableOptions);
  const [searching, setSearching] = useState<boolean>(false);
  const [selectedOption, setSelectedOption] = useState<SchemeOption | null>(null);

  const [transactionDate, setTransactionDate] = useState<string>(
    new Date().toISOString().split('T')[0]
  );
  const [settlementDate, setSettlementDate] = useState<string>('');
  const [transactionType, setTransactionType] = useState<TransactionType>('PURCHASE');
  const [units, setUnits] = useState<string>('');
  const [nav, setNav] = useState<string>('');
  const [grossAmount, setGrossAmount] = useState<string>('');
  const [stampDuty, setStampDuty] = useState<string>('');
  const [stt, setStt] = useState<string>('');
  const [netAmount, setNetAmount] = useState<string>('');
  const [folioNumber, setFolioNumber] = useState<string>('');
  const [description, setDescription] = useState<string>('');
  const [userNotes, setUserNotes] = useState<string>('');

  const [submitting, setSubmitting] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  // Initialize selected option if provided
  useEffect(() => {
    if (defaultSchemeOptionId && availableOptions.length > 0) {
      const match = availableOptions.find(o => o.id === defaultSchemeOptionId);
      if (match) setSelectedOption(match);
    }
  }, [defaultSchemeOptionId, availableOptions]);

  // Search scheme options
  useEffect(() => {
    if (!searchQuery || searchQuery.trim().length < 2) {
      setSearchResults(availableOptions);
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
  }, [searchQuery, availableOptions]);

  // Auto-calculate gross and net amount if units and nav are present
  const handleUnitsChange = (val: string) => {
    setUnits(val);
    const u = parseFloat(val);
    const n = parseFloat(nav);
    if (!isNaN(u) && !isNaN(n) && u > 0 && n > 0 && !grossAmount) {
      const g = (u * n).toFixed(2);
      setGrossAmount(g);
      calculateNet(g, stampDuty, stt);
    }
  };

  const handleNavChange = (val: string) => {
    setNav(val);
    const u = parseFloat(units);
    const n = parseFloat(val);
    if (!isNaN(u) && !isNaN(n) && u > 0 && n > 0 && !grossAmount) {
      const g = (u * n).toFixed(2);
      setGrossAmount(g);
      calculateNet(g, stampDuty, stt);
    }
  };

  const handleGrossChange = (val: string) => {
    setGrossAmount(val);
    calculateNet(val, stampDuty, stt);
  };

  const handleStampDutyChange = (val: string) => {
    setStampDuty(val);
    calculateNet(grossAmount, val, stt);
  };

  const handleSttChange = (val: string) => {
    setStt(val);
    calculateNet(grossAmount, stampDuty, val);
  };

  const calculateNet = (gStr: string, sdStr: string, sttStr: string) => {
    const g = parseFloat(gStr);
    if (isNaN(g)) return;
    const sd = parseFloat(sdStr) || 0;
    const s = parseFloat(sttStr) || 0;
    // For purchases, fees are added or subtracted depending on AMC convention; net cash paid is typically gross + charges
    const net = (g + sd + s).toFixed(2);
    setNetAmount(net);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedOption) {
      setError('Please select a target mutual fund scheme option.');
      return;
    }
    if (!transactionDate) {
      setError('Transaction date is required.');
      return;
    }

    const parsedUnits = units ? parseFloat(units) : undefined;
    const parsedNav = nav ? parseFloat(nav) : undefined;
    const parsedGross = grossAmount ? parseFloat(grossAmount) : undefined;
    const parsedNet = netAmount ? parseFloat(netAmount) : parsedGross;
    const parsedSd = stampDuty ? parseFloat(stampDuty) : undefined;
    const parsedStt = stt ? parseFloat(stt) : undefined;

    if (!parsedUnits && !parsedNet) {
      setError('Either transaction units or net investment amount must be provided.');
      return;
    }

    setSubmitting(true);
    setError(null);

    const payload: CreateManualTransactionRequest = {
      schemeOptionId: selectedOption.id,
      transactionDate,
      settlementDate: settlementDate || undefined,
      transactionType,
      units: parsedUnits,
      nav: parsedNav,
      grossAmount: parsedGross,
      stampDuty: parsedSd,
      stt: parsedStt,
      netAmount: parsedNet,
      folioNumber: folioNumber.trim() || undefined,
      description: description.trim() || undefined,
      userNotes: userNotes.trim() || undefined,
    };

    try {
      const created = await createManualTransaction(payload);
      onSuccess(created);
      onClose();
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to record manual transaction.');
    } finally {
      setSubmitting(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm overflow-y-auto">
      <div className="relative w-full max-w-2xl bg-zinc-900 border border-zinc-800 rounded-xl shadow-2xl p-6 text-zinc-100 max-h-[90vh] flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-zinc-800">
          <div>
            <h2 className="text-lg font-semibold text-zinc-100 tracking-tight">Record Manual Transaction</h2>
            <p className="text-xs text-zinc-400 mt-0.5">
              Append an auditable transaction entry to your investment ledger (<span className="font-mono text-zinc-300">source_attribution = MANUAL_ENTRY</span>).
            </p>
          </div>
          <button
            onClick={onClose}
            className="text-zinc-400 hover:text-zinc-200 p-1.5 rounded-lg hover:bg-zinc-800 transition"
          >
            ✕
          </button>
        </div>

        {/* Scrollable Form Body */}
        <form onSubmit={handleSubmit} className="overflow-y-auto flex-1 py-4 space-y-4 pr-1">
          {error && (
            <div className="p-3 bg-rose-950/60 border border-rose-800/80 rounded-lg text-rose-300 text-xs flex items-center gap-2">
              <span className="font-bold">Error:</span> {error}
            </div>
          )}

          {/* Scheme Selection */}
          <div>
            <label className="block text-xs font-medium text-zinc-300 mb-1">
              Select Mutual Fund Scheme <span className="text-rose-400">*</span>
            </label>
            {selectedOption ? (
              <div className="flex items-center justify-between p-2.5 bg-zinc-800/80 border border-zinc-700 rounded-lg">
                <div className="min-w-0">
                  <div className="text-sm font-medium text-zinc-100 truncate">
                    {selectedOption.plan?.scheme?.name || 'Selected Scheme'}
                  </div>
                  <div className="text-xs text-zinc-400 font-mono">
                    Plan: {selectedOption.plan?.planType || 'DIRECT'} • Option: {selectedOption.optionType} • AMFI: {selectedOption.amfiCode || 'N/A'}
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => setSelectedOption(null)}
                  className="text-xs text-amber-400 hover:text-amber-300 ml-3 underline"
                >
                  Change
                </button>
              </div>
            ) : (
              <div className="space-y-2">
                <input
                  type="text"
                  placeholder="Search scheme name, ISIN, or AMFI code..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="w-full px-3 py-2 bg-zinc-950 border border-zinc-700 rounded-lg text-sm text-zinc-100 focus:outline-none focus:border-sky-500"
                />
                {searching && <div className="text-xs text-zinc-400">Searching schemes...</div>}
                {searchResults.length > 0 && (
                  <div className="max-h-40 overflow-y-auto border border-zinc-800 bg-zinc-950/80 rounded-lg divide-y divide-zinc-800">
                    {searchResults.map((opt) => (
                      <button
                        key={opt.id}
                        type="button"
                        onClick={() => setSelectedOption(opt)}
                        className="w-full text-left p-2 hover:bg-zinc-800 transition text-xs flex justify-between items-center"
                      >
                        <div className="truncate mr-2">
                          <span className="font-medium text-zinc-200">{opt.plan?.scheme?.name}</span>
                          <span className="text-zinc-500 ml-1">({opt.plan?.planType} - {opt.optionType})</span>
                        </div>
                        <span className="text-zinc-500 font-mono text-[11px] shrink-0">{opt.amfiCode || opt.isin}</span>
                      </button>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Date and Type Row */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            <div>
              <label className="block text-xs font-medium text-zinc-300 mb-1">
                Transaction Date <span className="text-rose-400">*</span>
              </label>
              <input
                type="date"
                value={transactionDate}
                onChange={(e) => setTransactionDate(e.target.value)}
                required
                className="w-full px-3 py-1.5 bg-zinc-950 border border-zinc-700 rounded-lg text-xs text-zinc-100 focus:outline-none focus:border-sky-500 font-mono"
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-zinc-300 mb-1">Settlement Date</label>
              <input
                type="date"
                value={settlementDate}
                onChange={(e) => setSettlementDate(e.target.value)}
                className="w-full px-3 py-1.5 bg-zinc-950 border border-zinc-700 rounded-lg text-xs text-zinc-100 focus:outline-none focus:border-sky-500 font-mono"
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-zinc-300 mb-1">
                Transaction Type <span className="text-rose-400">*</span>
              </label>
              <select
                value={transactionType}
                onChange={(e) => setTransactionType(e.target.value as TransactionType)}
                className="w-full px-2 py-1.5 bg-zinc-950 border border-zinc-700 rounded-lg text-xs text-zinc-100 focus:outline-none focus:border-sky-500"
              >
                {TRANSACTION_TYPES.map((t) => (
                  <option key={t.type} value={t.type}>
                    [{t.group}] {t.label}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* Units, NAV, Gross Amount Row */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            <div>
              <label className="block text-xs font-medium text-zinc-300 mb-1">
                Units <span className="text-zinc-500">(e.g. 150.2541)</span>
              </label>
              <input
                type="number"
                step="0.0001"
                placeholder="0.0000"
                value={units}
                onChange={(e) => handleUnitsChange(e.target.value)}
                className="w-full px-3 py-1.5 bg-zinc-950 border border-zinc-700 rounded-lg text-xs text-zinc-100 focus:outline-none focus:border-sky-500 font-mono"
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-zinc-300 mb-1">
                NAV (₹) <span className="text-zinc-500">(Unit Price)</span>
              </label>
              <input
                type="number"
                step="0.0001"
                placeholder="0.0000"
                value={nav}
                onChange={(e) => handleNavChange(e.target.value)}
                className="w-full px-3 py-1.5 bg-zinc-950 border border-zinc-700 rounded-lg text-xs text-zinc-100 focus:outline-none focus:border-sky-500 font-mono"
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-zinc-300 mb-1">Gross Amount (₹)</label>
              <input
                type="number"
                step="0.01"
                placeholder="0.00"
                value={grossAmount}
                onChange={(e) => handleGrossChange(e.target.value)}
                className="w-full px-3 py-1.5 bg-zinc-950 border border-zinc-700 rounded-lg text-xs text-zinc-100 focus:outline-none focus:border-sky-500 font-mono"
              />
            </div>
          </div>

          {/* Taxes & Net Amount Row */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            <div>
              <label className="block text-xs font-medium text-zinc-300 mb-1">Stamp Duty (₹)</label>
              <input
                type="number"
                step="0.01"
                placeholder="0.00"
                value={stampDuty}
                onChange={(e) => handleStampDutyChange(e.target.value)}
                className="w-full px-3 py-1.5 bg-zinc-950 border border-zinc-700 rounded-lg text-xs text-zinc-100 focus:outline-none focus:border-sky-500 font-mono"
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-zinc-300 mb-1">STT / Charges (₹)</label>
              <input
                type="number"
                step="0.01"
                placeholder="0.00"
                value={stt}
                onChange={(e) => handleSttChange(e.target.value)}
                className="w-full px-3 py-1.5 bg-zinc-950 border border-zinc-700 rounded-lg text-xs text-zinc-100 focus:outline-none focus:border-sky-500 font-mono"
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-zinc-300 mb-1">
                Net Amount (₹) <span className="text-rose-400">*</span>
              </label>
              <input
                type="number"
                step="0.01"
                placeholder="0.00"
                value={netAmount}
                onChange={(e) => setNetAmount(e.target.value)}
                className="w-full px-3 py-1.5 bg-zinc-950 border border-zinc-700 rounded-lg text-xs text-emerald-400 font-semibold focus:outline-none focus:border-sky-500 font-mono"
              />
            </div>
          </div>

          {/* Folio and Description */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-zinc-300 mb-1">Folio Number</label>
              <input
                type="text"
                placeholder="e.g. 12345678/90"
                value={folioNumber}
                onChange={(e) => setFolioNumber(e.target.value)}
                className="w-full px-3 py-1.5 bg-zinc-950 border border-zinc-700 rounded-lg text-xs text-zinc-100 focus:outline-none focus:border-sky-500 font-mono"
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-zinc-300 mb-1">Description / Memo</label>
              <input
                type="text"
                placeholder="e.g. Monthly SIP via NetBanking"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                className="w-full px-3 py-1.5 bg-zinc-950 border border-zinc-700 rounded-lg text-xs text-zinc-100 focus:outline-none focus:border-sky-500"
              />
            </div>
          </div>

          {/* User Notes */}
          <div>
            <label className="block text-xs font-medium text-zinc-300 mb-1">Audit Notes / Reason</label>
            <textarea
              rows={2}
              placeholder="Context or rationale for recording this transaction manually..."
              value={userNotes}
              onChange={(e) => setUserNotes(e.target.value)}
              className="w-full px-3 py-1.5 bg-zinc-950 border border-zinc-700 rounded-lg text-xs text-zinc-100 focus:outline-none focus:border-sky-500"
            />
          </div>
        </form>

        {/* Footer */}
        <div className="flex items-center justify-end gap-3 pt-4 border-t border-zinc-800">
          <button
            type="button"
            onClick={onClose}
            disabled={submitting}
            className="px-4 py-2 text-xs font-medium text-zinc-400 hover:text-zinc-200 transition"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleSubmit}
            disabled={submitting}
            className="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg text-xs font-medium transition disabled:opacity-50 flex items-center gap-1.5"
          >
            {submitting ? 'Recording...' : 'Record Transaction'}
          </button>
        </div>
      </div>
    </div>
  );
}

