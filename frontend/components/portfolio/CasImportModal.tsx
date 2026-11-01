'use client';

import React, { useState } from 'react';
import {
  CasImportResultDto,
  CasImportConfirmResponseDto,
  stageCasImportJson,
  confirmCasImport
} from '@/lib/api/portfolio';

interface CasImportModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

const SAMPLE_CAS_JSON = JSON.stringify({
  statementDate: "2024-01-31",
  holdings: [
    {
      rawSchemeName: "HDFC Flexi Cap Fund - Direct Growth",
      isin: "INF179K01UT0",
      amfiCode: "118955",
      folioNumber: "12345678/90",
      units: 150.5000,
      costBasisAmount: 220000.00,
      marketValue: 251435.12
    },
    {
      rawSchemeName: "Parag Parikh Flexi Cap Fund - Direct Growth",
      isin: "INF879O01015",
      amfiCode: "122639",
      folioNumber: "98765432/10",
      units: 500.0000,
      costBasisAmount: 300000.00,
      marketValue: 345000.00
    },
    {
      rawSchemeName: "Unknown Obscure Fund Option XYZ",
      isin: "INF999999999",
      amfiCode: "999999",
      folioNumber: "11111111/00",
      units: 10.0000,
      costBasisAmount: 5000.00,
      marketValue: 5500.00
    }
  ]
}, null, 2);

export function CasImportModal({ isOpen, onClose, onSuccess }: CasImportModalProps) {
  const [step, setStep] = useState<'upload' | 'review' | 'success'>('upload');
  const [inputTab, setInputTab] = useState<'file' | 'raw'>('file');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [passwordInput, setPasswordInput] = useState<string>('');
  const [rawJsonInput, setRawJsonInput] = useState<string>(SAMPLE_CAS_JSON);
  const [filenameInput, setFilenameInput] = useState<string>('cams_cas_statement_sample.json');
  const [importResult, setImportResult] = useState<CasImportResultDto | null>(null);
  const [confirmResult, setConfirmResult] = useState<CasImportConfirmResponseDto | null>(null);
  const [mode, setMode] = useState<'MERGE' | 'REPLACE'>('MERGE');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleStageImport = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      let result: CasImportResultDto;
      if (inputTab === 'file') {
        if (!selectedFile) {
          setError('Please select a valid CAS statement file (PDF, JSON, or CSV).');
          setLoading(false);
          return;
        }
        const { uploadCasFileMultipart } = await import('@/lib/api/portfolio');
        result = await uploadCasFileMultipart(selectedFile, passwordInput);
      } else {
        result = await stageCasImportJson(rawJsonInput, filenameInput);
      }

      if (result.status === 'FAILED') {
        const limitationStr = result.limitations.join(' ');
        if (limitationStr.includes('PASSWORD_REQUIRED') || limitationStr.includes('INVALID_PASSWORD')) {
          setError(limitationStr);
        } else {
          setError(limitationStr || 'Parsing failed for selected CAS file.');
        }
        setLoading(false);
        return;
      }

      setImportResult(result);
      setStep('review');
      setLoading(false);
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to parse and stage CAS file');
      setLoading(false);
    }
  };

  const handleConfirmImport = async () => {
    if (!importResult) return;
    setLoading(true);
    setError(null);
    try {
      const res = await confirmCasImport(importResult.importId, mode);
      setConfirmResult(res);
      setStep('success');
      setLoading(false);
      onSuccess();
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to confirm CAS import');
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-sm p-4 overflow-y-auto">
      <div className="bg-slate-900 border border-slate-800 rounded-xl max-w-3xl w-full p-6 text-slate-100 shadow-2xl space-y-6">
        
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-800 pb-4">
          <div>
            <h2 className="text-lg font-semibold text-white flex items-center gap-2">
              <span>Import External Mutual Fund Portfolio (CAS)</span>
              <span className="text-xs px-2 py-0.5 rounded bg-cyan-950 text-cyan-400 border border-cyan-800 font-mono">
                CAMS / KFINTECH PDF & STRUCTURED
              </span>
            </h2>
            <p className="text-xs text-slate-400 mt-1">
              Upload CAMS/KFintech CAS PDF statements or structured JSON/CSV files to stage and resolve holdings.
            </p>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-white text-xl font-bold px-2"
          >
            ✕
          </button>
        </div>

        {/* Epistemic Boundary Notice */}
        <div className="p-3 bg-slate-950/80 border-l-2 border-l-cyan-400 text-slate-300 rounded-r-lg text-[11px] font-mono leading-relaxed">
          <strong className="text-cyan-300">EPISTEMIC BOUNDARY:</strong> CAS statements represent external investor-owned portfolio positions extracted from registrar records. They do not constitute statutory fund portfolio holdings disclosures or mutual fund analytical metrics. Imported positions are isolated strictly to your authenticated workspace.
        </div>

        {error && (
          <div className="p-3 bg-red-950/60 border border-red-800 text-red-300 rounded-lg text-xs font-mono">
            ⚠ {error}
          </div>
        )}

        {/* STEP 1: UPLOAD / RAW INPUT */}
        {step === 'upload' && (
          <form onSubmit={handleStageImport} className="space-y-4">
            <div className="flex gap-2 border-b border-slate-800 pb-2">
              <button
                type="button"
                onClick={() => setInputTab('file')}
                className={`px-3 py-1.5 rounded text-xs font-medium ${inputTab === 'file' ? 'bg-cyan-950 text-cyan-300 border border-cyan-800' : 'text-slate-400 hover:text-white'}`}
              >
                📄 Upload File (PDF / JSON / CSV)
              </button>
              <button
                type="button"
                onClick={() => setInputTab('raw')}
                className={`px-3 py-1.5 rounded text-xs font-medium ${inputTab === 'raw' ? 'bg-cyan-950 text-cyan-300 border border-cyan-800' : 'text-slate-400 hover:text-white'}`}
              >
                {'{ }'} Raw Payload
              </button>
            </div>

            {inputTab === 'file' ? (
              <div className="space-y-4">
                <div>
                  <label className="block text-xs font-mono text-slate-400 uppercase tracking-wider mb-1">
                    Select CAS Statement File
                  </label>
                  <input
                    type="file"
                    accept=".pdf,.json,.csv,.txt"
                    onChange={(e) => setSelectedFile(e.target.files?.[0] || null)}
                    className="w-full bg-slate-950 border border-slate-700 text-white text-xs rounded-lg p-2.5 font-mono focus:ring-1 focus:ring-cyan-500 focus:outline-none"
                  />
                  <p className="text-[11px] text-slate-500 mt-1">
                    Supported: CAMS CAS PDF, KFintech CAS PDF, Structured JSON/CSV file upload (max 10MB).
                  </p>
                </div>

                <div>
                  <label className="block text-xs font-mono text-slate-400 uppercase tracking-wider mb-1">
                    PDF Statement Password (If Encrypted)
                  </label>
                  <input
                    type="password"
                    placeholder="Enter password (PAN / DOB as required by provider)"
                    value={passwordInput}
                    onChange={(e) => setPasswordInput(e.target.value)}
                    className="w-full bg-slate-950 border border-slate-700 text-white text-xs rounded-lg p-2.5 font-mono focus:ring-1 focus:ring-cyan-500 focus:outline-none"
                  />
                  <p className="text-[11px] text-slate-500 mt-1">
                    Passwords are used in-memory for extraction only and are NEVER logged or stored.
                  </p>
                </div>
              </div>
            ) : (
              <div className="space-y-4">
                <div>
                  <label className="block text-xs font-mono text-slate-400 uppercase tracking-wider mb-1">
                    CAS Statement Source Filename
                  </label>
                  <input
                    type="text"
                    value={filenameInput}
                    onChange={(e) => setFilenameInput(e.target.value)}
                    className="w-full bg-slate-950 border border-slate-700 text-white text-xs rounded-lg p-2.5 font-mono focus:ring-1 focus:ring-cyan-500 focus:outline-none"
                  />
                </div>

                <div>
                  <label className="block text-xs font-mono text-slate-400 uppercase tracking-wider mb-1">
                    Structured CAS Statement Payload (JSON / CSV Format)
                  </label>
                  <textarea
                    rows={8}
                    value={rawJsonInput}
                    onChange={(e) => setRawJsonInput(e.target.value)}
                    className="w-full bg-slate-950 border border-slate-700 text-cyan-300 text-xs rounded-lg p-3 font-mono focus:ring-1 focus:ring-cyan-500 focus:outline-none"
                  />
                </div>
              </div>
            )}

            <div className="flex justify-end gap-3 pt-2">
              <button
                type="button"
                onClick={onClose}
                className="px-4 py-2 text-xs font-medium text-slate-400 hover:text-white bg-slate-800 rounded-lg"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={loading}
                className="px-5 py-2 text-xs font-semibold text-white bg-cyan-600 hover:bg-cyan-500 rounded-lg shadow disabled:opacity-50"
              >
                {loading ? 'Processing & Parsing CAS...' : 'Stage & Parse CAS File'}
              </button>
            </div>
          </form>
        )}

        {/* STEP 2: REVIEW STAGED IMPORT */}
        {step === 'review' && importResult && (
          <div className="space-y-5">
            {/* Metadata Summary Banner */}
            <div className="bg-slate-950 p-3.5 rounded-lg border border-slate-800 grid grid-cols-3 gap-3 text-xs font-mono">
              <div>
                <span className="text-slate-400 block text-[10px]">CAS PROVIDER</span>
                <span className="text-white font-bold">{importResult.casProvider || 'CAMS'}</span>
              </div>
              <div>
                <span className="text-slate-400 block text-[10px]">STATEMENT DATE</span>
                <span className="text-cyan-300 font-bold">{importResult.statementDate || 'N/A'}</span>
              </div>
              <div>
                <span className="text-slate-400 block text-[10px]">IMPORT QUALITY</span>
                <span className={`font-bold px-2 py-0.5 rounded text-[10px] ${
                  importResult.importQualityState === 'VALID' ? 'bg-emerald-950 text-emerald-400 border border-emerald-800' : 'bg-amber-950 text-amber-400 border border-amber-800'
                }`}>
                  {importResult.importQualityState || 'VALID'}
                </span>
              </div>
            </div>

            {/* Staging Counter Cards */}
            <div className="grid grid-cols-4 gap-3 text-center">
              <div className="bg-slate-950 p-3 rounded-lg border border-slate-800">
                <div className="text-[10px] text-slate-400 font-mono">Detected</div>
                <div className="text-xl font-bold text-white mt-0.5">{importResult.totalRecordsDetected}</div>
              </div>
              <div className="bg-slate-950 p-3 rounded-lg border border-slate-800">
                <div className="text-[10px] text-slate-400 font-mono">Resolved</div>
                <div className="text-xl font-bold text-emerald-400 mt-0.5">{importResult.resolvedRecordsCount}</div>
              </div>
              <div className="bg-slate-950 p-3 rounded-lg border border-slate-800">
                <div className="text-[10px] text-slate-400 font-mono">Unresolved</div>
                <div className="text-xl font-bold text-amber-400 mt-0.5">{importResult.unresolvedRecordsCount}</div>
              </div>
              <div className="bg-slate-950 p-3 rounded-lg border border-slate-800">
                <div className="text-[10px] text-slate-400 font-mono">Ambiguous</div>
                <div className="text-xl font-bold text-rose-400 mt-0.5">{importResult.ambiguousRecordsCount}</div>
              </div>
            </div>

            {/* Reconciliation Mode Selector */}
            <div className="bg-slate-950/60 p-3 rounded-lg border border-slate-800 flex items-center justify-between text-xs">
              <span className="font-mono text-slate-300">Import Reconciliation Mode:</span>
              <div className="flex gap-2">
                <label className="flex items-center gap-1.5 cursor-pointer text-slate-300">
                  <input
                    type="radio"
                    name="mode"
                    value="MERGE"
                    checked={mode === 'MERGE'}
                    onChange={() => setMode('MERGE')}
                    className="text-cyan-600 focus:ring-cyan-500"
                  />
                  <span>MERGE (Add/Update)</span>
                </label>
                <label className="flex items-center gap-1.5 cursor-pointer text-slate-300">
                  <input
                    type="radio"
                    name="mode"
                    value="REPLACE"
                    checked={mode === 'REPLACE'}
                    onChange={() => setMode('REPLACE')}
                    className="text-cyan-600 focus:ring-cyan-500"
                  />
                  <span>REPLACE (Overwrite Portfolio)</span>
                </label>
              </div>
            </div>

            <p className="text-[11px] text-slate-400 italic">
              ℹ Multi-folio holdings are retained individually in raw staging audit history. Upon confirmation, holdings are aggregated deterministically at the scheme level.
            </p>

            {/* Staged Records Table */}
            <div>
              <h3 className="text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">
                Detected Holding Resolution Breakdown
              </h3>
              <div className="max-h-60 overflow-y-auto border border-slate-800 rounded-lg">
                <table className="w-full text-left text-xs text-slate-300">
                  <thead className="bg-slate-950 text-slate-400 font-mono uppercase sticky top-0 border-b border-slate-800">
                    <tr>
                      <th className="px-3 py-2">Raw Scheme Name</th>
                      <th className="px-3 py-2">ISIN / AMFI</th>
                      <th className="px-3 py-2 text-right">Units</th>
                      <th className="px-3 py-2">Resolution Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/60 bg-slate-900/50 font-mono text-[11px]">
                    {importResult.holdings.map((h) => (
                      <tr key={h.id} className="hover:bg-slate-800/40">
                        <td className="px-3 py-2 font-medium text-white">
                          <div>{h.rawSchemeName}</div>
                          {h.resolvedFundName && (
                            <div className="text-[10px] text-cyan-400">→ {h.resolvedFundName} ({h.resolvedAmcName})</div>
                          )}
                        </td>
                        <td className="px-3 py-2 text-slate-400">{h.isin || h.amfiCode || 'N/A'}</td>
                        <td className="px-3 py-2 text-right text-slate-200">{h.units}</td>
                        <td className="px-3 py-2">
                          <span className={`px-2 py-0.5 rounded text-[10px] ${
                            h.resolutionStatus === 'RESOLVED' ? 'bg-emerald-950 text-emerald-400 border border-emerald-800' :
                            'bg-amber-950 text-amber-400 border border-amber-800'
                          }`}>
                            {h.resolutionStatus} ({h.resolutionMethod})
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Buttons */}
            <div className="flex justify-between items-center pt-2">
              <button
                type="button"
                onClick={() => setStep('upload')}
                className="px-4 py-2 text-xs font-medium text-slate-400 hover:text-white bg-slate-800 rounded-lg"
              >
                ← Back to Edit
              </button>
              <button
                type="button"
                onClick={handleConfirmImport}
                disabled={loading || importResult.resolvedRecordsCount === 0}
                className="px-5 py-2 text-xs font-semibold text-white bg-emerald-600 hover:bg-emerald-500 rounded-lg shadow disabled:opacity-50"
              >
                {loading ? 'Confirming Portfolio Import...' : `Confirm & Apply ${importResult.resolvedRecordsCount} Holdings`}
              </button>
            </div>
          </div>
        )}

        {/* STEP 3: SUCCESS */}
        {step === 'success' && confirmResult && (
          <div className="space-y-6 text-center py-4">
            <div className="text-4xl text-emerald-400">✓</div>
            <div>
              <h3 className="text-lg font-bold text-white">External Portfolio Import Confirmed</h3>
              <p className="text-xs text-slate-400 mt-1">
                Your portfolio has been updated deterministically with full provenance audit trails.
              </p>
            </div>

            <div className="bg-slate-950 p-4 rounded-lg border border-slate-800 text-xs font-mono text-left space-y-1">
              <div><span className="text-slate-400">Holdings Added:</span> {confirmResult.holdingsAddedCount}</div>
              <div><span className="text-slate-400">Holdings Updated:</span> {confirmResult.holdingsUpdatedCount}</div>
              <div><span className="text-slate-400">Unresolved Skipped:</span> {confirmResult.unresolvedSkippedCount}</div>
              <div><span className="text-slate-400">Confirmed Timestamp:</span> {confirmResult.confirmedAt}</div>
            </div>

            <button
              onClick={onClose}
              className="w-full py-2.5 text-xs font-semibold text-white bg-cyan-600 hover:bg-cyan-500 rounded-lg"
            >
              Close & View Updated Portfolio Intelligence
            </button>
          </div>
        )}

      </div>
    </div>
  );
}
