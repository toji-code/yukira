'use client';

import React, { useState, useEffect } from 'react';
import {
  fetchCanonicalEvidencePacket,
  fetchMethodologyExplainers,
  CanonicalEvidencePacketDto,
  MethodologyGovernanceExplainerDto,
} from '@/lib/api/investor';

interface CanonicalEvidencePacketModalProps {
  schemeOptionId: number | null;
  schemeName: string;
  isOpen: boolean;
  onClose: () => void;
}

export function CanonicalEvidencePacketModal({
  schemeOptionId,
  schemeName,
  isOpen,
  onClose,
}: CanonicalEvidencePacketModalProps) {
  const [activeTab, setActiveTab] = useState<'PACKET' | 'EXPLAINERS'>('PACKET');
  const [packet, setPacket] = useState<CanonicalEvidencePacketDto | null>(null);
  const [explainers, setExplainers] = useState<MethodologyGovernanceExplainerDto[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (isOpen && schemeOptionId) {
      loadPacket(schemeOptionId);
    }
  }, [isOpen, schemeOptionId]);

  async function loadPacket(id: number) {
    setLoading(true);
    try {
      const [pkt, exp] = await Promise.all([
        fetchCanonicalEvidencePacket(id),
        fetchMethodologyExplainers().catch(() => []),
      ]);
      setPacket(pkt);
      setExplainers(exp);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }

  if (!isOpen || !schemeOptionId) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-md p-4">
      <div className="relative w-full max-w-3xl bg-zinc-950 border border-zinc-800 rounded-xl shadow-2xl flex flex-col max-h-[85vh] overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-zinc-800 bg-zinc-900/40">
          <div>
            <h3 className="text-sm font-semibold text-zinc-100 flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-emerald-500" />
              Canonical Evidence Packet Standard & Methodology Explainer
            </h3>
            <p className="text-xs text-zinc-400 mt-0.5 truncate max-w-lg">
              {schemeName} (Option #{schemeOptionId})
            </p>
          </div>
          <button onClick={onClose} className="text-zinc-400 hover:text-zinc-200">
            ✕
          </button>
        </div>

        {/* Tabs */}
        <div className="flex border-b border-zinc-800 bg-zinc-900/20 px-6">
          <button
            onClick={() => setActiveTab('PACKET')}
            className={`py-2.5 px-4 text-xs font-medium border-b-2 transition-colors ${
              activeTab === 'PACKET'
                ? 'border-emerald-500 text-emerald-400'
                : 'border-transparent text-zinc-400 hover:text-zinc-200'
            }`}
          >
            Evidence Packet Standard
          </button>
          <button
            onClick={() => setActiveTab('EXPLAINERS')}
            className={`py-2.5 px-4 text-xs font-medium border-b-2 transition-colors ${
              activeTab === 'EXPLAINERS'
                ? 'border-emerald-500 text-emerald-400'
                : 'border-transparent text-zinc-400 hover:text-zinc-200'
            }`}
          >
            Methodology & Formulas
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-6 space-y-4">
          {loading ? (
            <div className="py-12 text-center text-xs text-zinc-500">Assembling canonical evidence packet...</div>
          ) : activeTab === 'PACKET' ? (
            packet ? (
              <div className="space-y-4">
                {/* Fingerprint Banner */}
                <div className="p-3 bg-zinc-900/60 border border-zinc-800 rounded-lg">
                  <div className="text-[10px] text-zinc-400 uppercase tracking-wider font-semibold">
                    Cryptographic Evidence Fingerprint
                  </div>
                  <div className="font-mono text-xs text-emerald-400 mt-0.5 break-all select-all">
                    {packet.packetFingerprint}
                  </div>
                  <div className="text-[10px] text-zinc-500 mt-1 flex gap-4">
                    <span>As-Of: {packet.asOfDate}</span>
                    <span>Benchmark: {packet.primaryBenchmarkCode}</span>
                  </div>
                </div>

                {/* Metrics Table */}
                <div className="border border-zinc-800 rounded-lg overflow-hidden bg-zinc-950">
                  <div className="px-3 py-2 bg-zinc-900/40 border-b border-zinc-800 text-xs font-semibold text-zinc-200">
                    Authoritative Deterministic Metrics
                  </div>
                  <table className="w-full text-left text-xs">
                    <thead className="bg-zinc-900/60 border-b border-zinc-800 text-[10px] text-zinc-400 uppercase">
                      <tr>
                        <th className="py-2 px-3">Metric</th>
                        <th className="py-2 px-3">Value</th>
                        <th className="py-2 px-3">Status</th>
                        <th className="py-2 px-3">Run Fingerprint</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-zinc-800/60 font-mono text-[11px]">
                      {packet.metrics.map((m) => (
                        <tr key={m.metricCode} className="hover:bg-zinc-900/40">
                          <td className="py-2 px-3 text-zinc-200">
                            {m.metricCode} — {m.metricName}
                          </td>
                          <td className="py-2 px-3 font-semibold text-emerald-400">{m.formattedValue}</td>
                          <td className="py-2 px-3">
                            <span
                              className={`px-1.5 py-0.5 rounded text-[9px] font-semibold ${
                                m.methodologyStatus === 'APPROVED'
                                  ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                                  : 'bg-amber-500/20 text-amber-400 border border-amber-500/30'
                              }`}
                            >
                              {m.methodologyStatus}
                            </span>
                          </td>
                          <td className="py-2 px-3 text-zinc-500 text-[10px] truncate max-w-[140px]">
                            {m.runFingerprint}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                {/* Six-Dimensional Quality Taxonomy */}
                <div className="p-3 bg-zinc-900/30 border border-zinc-800 rounded-lg">
                  <div className="text-xs font-semibold text-zinc-200 mb-2">Six-Dimensional Quality Flags</div>
                  <div className="grid grid-cols-3 gap-2 text-xs font-mono">
                    {Object.entries(packet.dataQualityFlags).map(([k, v]) => (
                      <div key={k} className="p-2 bg-zinc-900/60 border border-zinc-800/60 rounded">
                        <span className="text-[10px] text-zinc-500 uppercase block">{k}</span>
                        <span className="text-zinc-200 font-semibold">{v}</span>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Limitations */}
                {packet.dataLimitations.length > 0 && (
                  <div className="p-3 bg-zinc-900/30 border border-zinc-800 rounded-lg">
                    <div className="text-xs font-semibold text-zinc-200 mb-1">Disclosed Data Limitations</div>
                    <ul className="list-disc pl-4 space-y-1 text-xs text-zinc-400">
                      {packet.dataLimitations.map((lim, i) => (
                        <li key={i}>{lim}</li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            ) : (
              <div className="text-xs text-zinc-500">Packet unavailable.</div>
            )
          ) : (
            /* Methodology Explainers Tab */
            <div className="space-y-3">
              {explainers.map((exp) => (
                <div key={exp.metricCode} className="p-4 bg-zinc-900/30 border border-zinc-800 rounded-lg space-y-2">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-bold text-zinc-200">
                        {exp.metricCode}: {exp.metricName}
                      </span>
                      <span
                        className={`px-1.5 py-0.5 rounded text-[9px] font-semibold ${
                          exp.methodologyStatus === 'APPROVED'
                            ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                            : 'bg-amber-500/20 text-amber-400 border border-amber-500/30'
                        }`}
                      >
                        {exp.methodologyStatus}
                      </span>
                    </div>
                    <span className="text-[10px] font-mono text-zinc-400">Min Horizon: {exp.minimumHorizonRequired}</span>
                  </div>

                  <p className="text-xs text-zinc-300">{exp.description}</p>

                  <div className="p-2 bg-zinc-950 font-mono text-[11px] text-emerald-400 rounded border border-zinc-800">
                    Formula: {exp.mathematicalFormula}
                  </div>

                  <div className="text-[11px] text-zinc-400">
                    <strong className="text-zinc-300">Inputs: </strong>
                    {exp.requiredInputsSummary}
                  </div>

                  <div className="text-[11px] text-zinc-500 italic">
                    Note: {exp.governanceNote}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

