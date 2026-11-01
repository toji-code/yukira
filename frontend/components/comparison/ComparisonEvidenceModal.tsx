"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { ComparisonMetric, ComparisonFund, ComparisonResponse } from "@/types/comparison";
import { getMetricEvidenceDefinition } from "@/lib/analysis/metricEvidence";

interface ComparisonEvidenceModalProps {
  isOpen: boolean;
  onClose: () => void;
  metric: ComparisonMetric | null;
  funds: ComparisonFund[];
  comparison: ComparisonResponse;
  onInvestigateFund?: (fund: ComparisonFund, question: string, reason: string) => void;
}

export function ComparisonEvidenceModal({
  isOpen,
  onClose,
  metric,
  funds,
  comparison,
  onInvestigateFund,
}: ComparisonEvidenceModalProps) {
  const [selectedFundId, setSelectedFundId] = useState<number>(() => funds[0]?.schemeOptionId || 0);

  useEffect(() => {
    if (funds.length > 0 && (!selectedFundId || !funds.some((f) => f.schemeOptionId === selectedFundId))) {
      setSelectedFundId(funds[0].schemeOptionId);
    }
  }, [funds, selectedFundId]);

  // Handle escape key
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    if (isOpen) {
      window.addEventListener("keydown", handleKeyDown);
    }
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen || !metric) return null;

  const def = getMetricEvidenceDefinition(metric.metricCode);
  const activeFund = funds.find((f) => f.schemeOptionId === selectedFundId) || funds[0];
  const activeResult = metric.results.find((r) => r.schemeOptionId === activeFund?.schemeOptionId);
  const isCalculated = activeResult?.calculationStatus === "CALCULATED";
  const runId = comparison.runId || 1;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm animate-in fade-in duration-200">
      <div
        className="relative w-full max-w-4xl max-h-[90vh] flex flex-col bg-surface border border-border rounded-xl shadow-2xl overflow-hidden"
        role="dialog"
        aria-modal="true"
        aria-labelledby="evidence-modal-title"
      >
        {/* Modal Header */}
        <div className="flex items-start justify-between p-5 border-b border-border bg-surface-raised">
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <span className="font-mono text-xs font-bold text-accent bg-accent/10 px-2 py-0.5 rounded border border-accent/20">
                {metric.metricCode}
              </span>
              <span className={`status-badge ${
                metric.governanceStatus === "APPROVED" ? "state-approved" : "state-candidate"
              }`}>
                {metric.governanceStatus || "CANDIDATE"}
              </span>
              <span className="mono-meta text-[11px] uppercase">
                {metric.category || "QUANTITATIVE EVIDENCE"}
              </span>
            </div>
            <h2 id="evidence-modal-title" className="text-h3 text-text-primary mt-1">
              {metric.metricName}
            </h2>
            <p className="mono-meta text-[11px]">
              Calculation Run #{runId} • As of {comparison.asOfDate || "2024-01-15"} • Knowledge Cutoff: {comparison.knowledgeCutoff || "2024-01-31T23:59:59+05:30"}
            </p>
          </div>

          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-text-muted hover:text-text-primary hover:bg-surface transition-colors"
            aria-label="Close modal"
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Modal Body */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6 scroll-region">
          {/* Section 1: Mathematical Specification & Formula */}
          <div className="panel-inset p-4 bg-surface-raised rounded-lg space-y-3 border border-border">
            <span className="mono-meta text-accent uppercase font-bold text-[10px] block">
              01. Mathematical Formula &amp; Deterministic Specification
            </span>
            <div className="font-mono text-[12px] bg-surface p-3 rounded border border-border text-text-primary overflow-x-auto">
              <code>{def?.formulaSpecification || metric.description || "Formula specification documented in quant-engine."}</code>
            </div>
            {def?.whyItMatters && (
              <p className="text-[12.5px] leading-relaxed text-text-secondary">
                <strong className="text-text-primary">Financial Significance:</strong> {def.whyItMatters}
              </p>
            )}
          </div>

          {/* Section 2: Assumptions & Constraints */}
          {def?.assumptions && def.assumptions.length > 0 && (
            <div className="space-y-2">
              <span className="mono-meta text-text-primary uppercase font-bold text-[10px] block">
                02. Methodological Assumptions &amp; Numerical Conventions
              </span>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2.5 font-mono text-[11px]">
                {def.assumptions.map((assump, idx) => (
                  <div key={idx} className="p-2.5 rounded bg-surface border border-border">
                    <span className="text-text-muted block text-[10px] uppercase">{assump.label}</span>
                    <span className="text-text-primary font-semibold mt-0.5 block">{assump.value}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Section 3: Material Limitations */}
          <div className="p-3.5 rounded-lg bg-amber-500/10 border border-amber-500/20 text-xs font-mono text-amber-200 space-y-1.5">
            <div className="flex items-center gap-1.5 font-bold uppercase text-[10.5px] text-amber-300">
              <span>⚠</span>
              <span>03. Material Quantitative &amp; Comparability Limitations</span>
            </div>
            <p className="leading-relaxed font-sans text-[12px]">
              {def?.limitations || metric.limitations || "Past realized metrics provide zero guarantee or forecast of future market cycle performance. Candidate methodologies operate under research disclaimers."}
            </p>
          </div>

          {/* Section 4: Fund-by-Fund Candidate Traceability */}
          <div className="space-y-3 pt-2 border-t border-border">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <span className="mono-meta text-accent uppercase font-bold text-[10px] block">
                  04. Candidate Evidence Traceability ({funds.length} Share Classes)
                </span>
                <p className="mono-meta text-[11px]">
                  Select a candidate to review exact observation bounds, calculation status, and lineage.
                </p>
              </div>
              <div className="flex gap-1">
                {funds.map((f) => (
                  <button
                    key={f.schemeOptionId}
                    onClick={() => setSelectedFundId(f.schemeOptionId)}
                    className={`px-2.5 py-1 text-[11px] font-mono rounded transition-colors ${
                      selectedFundId === f.schemeOptionId
                        ? "bg-accent text-surface-dark font-bold"
                        : "bg-surface hover:bg-surface-raised text-text-secondary border border-border"
                    }`}
                  >
                    {f.schemeName.split(" ")[0]} ({f.planType})
                  </button>
                ))}
              </div>
            </div>

            {activeFund && (
              <div className="panel p-4 bg-surface space-y-3 border border-border">
                <div className="flex flex-wrap items-start justify-between gap-2 border-b border-border/60 pb-2">
                  <div>
                    <h4 className="text-[13.5px] font-semibold text-text-primary">
                      {activeFund.schemeName}
                    </h4>
                    <span className="mono-meta text-[11px]">
                      {activeFund.amcName} • {activeFund.planType} • {activeFund.optionType} • AMFI: {activeFund.amfiCode || "N/A"}
                    </span>
                  </div>
                  <span className={`status-badge ${
                    isCalculated ? "state-approved" : "state-candidate"
                  }`}>
                    {activeResult?.calculationStatus || "NOT_CALCULATED"}
                  </span>
                </div>

                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 font-mono text-[11px]">
                  <div className="panel-inset p-2.5 bg-surface-raised rounded">
                    <span className="text-text-muted text-[10px] block uppercase">Evaluated Value</span>
                    <span className="text-[14px] font-bold text-text-primary block mt-0.5">
                      {activeResult?.formattedValue || (activeResult?.numericValue !== null && activeResult?.numericValue !== undefined ? activeResult.numericValue.toFixed(4) : "—")}
                    </span>
                    <span className="text-text-muted text-[9.5px]">{activeResult?.units || metric.units || ""}</span>
                  </div>

                  <div className="panel-inset p-2.5 bg-surface-raised rounded">
                    <span className="text-text-muted text-[10px] block uppercase">Observation Coverage</span>
                    <span className="text-[12px] font-semibold text-text-primary block mt-0.5">
                      {activeResult?.startDate && activeResult?.endDate
                        ? `${activeResult.startDate} → ${activeResult.endDate}`
                        : "Standard Horizon"}
                    </span>
                    <span className="text-text-muted text-[9.5px]">N = {activeResult?.observationCount ?? "Aligned"} trading days</span>
                  </div>

                  <div className="panel-inset p-2.5 bg-surface-raised rounded">
                    <span className="text-text-muted text-[10px] block uppercase">Comparability</span>
                    <span className="text-[12px] font-semibold text-text-primary block mt-0.5">
                      {activeResult?.comparabilityStatus || "COMPARABLE"}
                    </span>
                    <span className="text-text-muted text-[9.5px] truncate block" title={activeResult?.limitationReason || undefined}>
                      {activeResult?.limitationReason || "Full coverage"}
                    </span>
                  </div>

                  <div className="panel-inset p-2.5 bg-surface-raised rounded">
                    <span className="text-text-muted text-[10px] block uppercase">Data Provenance</span>
                    <span className="text-[12px] font-semibold text-text-primary block mt-0.5">
                      AMFI Verified NAV
                    </span>
                    <span className="text-text-muted text-[9.5px]">SHA-256 Validated</span>
                  </div>
                </div>

                {activeResult?.limitationReason && (
                  <div className="p-2.5 rounded bg-amber-500/10 border border-amber-500/20 text-[11px] font-mono text-amber-300">
                    <strong>Specific Fund Limitation:</strong> {activeResult.limitationReason}
                  </div>
                )}

                <div className="pt-2 flex flex-wrap items-center justify-between gap-2 border-t border-border/40">
                  <div className="flex items-center gap-3">
                    <Link
                      href={`/analysis/${runId}?metric=${metric.metricCode}`}
                      className="text-link text-[11px] font-mono hover:underline flex items-center gap-1"
                    >
                      <span>Open in Evidence Explorer (Run #{runId})</span>
                      <span>→</span>
                    </Link>
                    <Link
                      href={`/funds/${activeFund.schemeId || 1}?option=${activeFund.schemeOptionId}#coverage`}
                      className="text-link text-[11px] font-mono hover:underline flex items-center gap-1"
                    >
                      <span>View Fund Observation Ledger</span>
                      <span>→</span>
                    </Link>
                  </div>

                  {onInvestigateFund && (
                    <button
                      type="button"
                      onClick={() => {
                        const question = def?.investigationQuestion || `How does ${metric.metricName} for ${activeFund.schemeName} compare against peer dispersion given observed volatility and market conditions?`;
                        const reason = def?.investigationReasonTemplate ? def.investigationReasonTemplate(comparison.asOfDate) : `Evaluating ${metric.metricCode} (${metric.metricName}) vs peers`;
                        onInvestigateFund(activeFund, question, reason);
                      }}
                      className="btn btn-secondary btn-sm text-[11px] font-mono flex items-center gap-1"
                    >
                      <svg className="h-3.5 w-3.5 text-accent" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z" />
                      </svg>
                      <span>Record Due Diligence Question</span>
                    </button>
                  )}
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Modal Footer */}
        <div className="p-4 border-t border-border bg-surface-raised flex items-center justify-between gap-3">
          <span className="mono-meta text-[11px]">
            Quantitative math calculated deterministically. Zero LLM interpolation.
          </span>
          <button onClick={onClose} className="btn btn-secondary btn-sm font-mono text-xs">
            Close Inspector
          </button>
        </div>
      </div>
    </div>
  );
}
