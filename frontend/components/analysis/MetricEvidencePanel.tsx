"use client";

import React, { useState } from "react";
import Link from "next/link";
import { getMetricEvidenceDefinition } from "@/lib/analysis/metricEvidence";

export interface MetricEvidenceContext {
  schemeId: number;
  schemeCode?: string;
  schemeName?: string;
  schemeOptionId?: number | null;
  optionType?: string | null;
  planType?: string | null;
  amfiCode?: string | null;
  isin?: string | null;
  benchmarkCode?: string | null;
  benchmarkName?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  knowledgeCutoffTime?: string | null;
  observationCount?: number | null;
  calculationRunId?: number | null;
  methodologyTag?: string | null;
  engineSoftwareVersion?: string | null;
  inputSnapshotSha256?: string | null;
}

export interface MetricEvidenceItem {
  metricCode: string;
  metricName: string;
  category?: string;
  numericValue?: number | null;
  formattedValue?: string | null;
  units?: string | null;
  periodType?: string | null;
  calculationStatus?: string | null;
  governanceStatus?: string | null;
  formulaDisclosure?: string | null;
  interpretation?: string | null;
  limitations?: string | null;
  errorMessage?: string | null;
  investigationQuestion?: string | null;
  diagnostics?: Record<string, unknown> | null;
}

export interface MetricEvidencePanelProps {
  metric: MetricEvidenceItem;
  context: MetricEvidenceContext;
  onInvestigate?: (investigation: {
    metricCode: string;
    metricName: string;
    suggestedQuestion: string;
    suggestedReason: string;
  }) => void;
  isCollapsible?: boolean;
  defaultExpanded?: boolean;
}

export function MetricEvidencePanel({
  metric,
  context,
  onInvestigate,
  isCollapsible = false,
  defaultExpanded = true,
}: MetricEvidencePanelProps) {
  const [expanded, setExpanded] = useState(defaultExpanded);
  const def = getMetricEvidenceDefinition(metric.metricCode);

  const isCalculated = metric.calculationStatus === "CALCULATED";
  const isInsufficient =
    metric.calculationStatus === "INSUFFICIENT_DATA" ||
    metric.calculationStatus === "FAILED";
  const governanceStatus =
    metric.governanceStatus || def.governanceStatus;
  const isApproved = governanceStatus === "APPROVED";
  const isOperational = governanceStatus === "OPERATIONAL BASELINE";

  const question =
    metric.investigationQuestion ||
    (metric.diagnostics?.investigationQuestion as string | undefined) ||
    def.investigationQuestion;

  const reason = def.investigationReasonTemplate(context.endDate || undefined);

  const handleInvestigateClick = () => {
    if (onInvestigate) {
      onInvestigate({
        metricCode: metric.metricCode,
        metricName: metric.metricName || def.name,
        suggestedQuestion: question,
        suggestedReason: reason,
      });
    }
  };

  return (
    <div
      className="panel border border-border/80 bg-surface rounded-lg overflow-hidden transition-all"
      data-testid={`evidence-panel-${metric.metricCode}`}
    >
      {/* HEADER / EXPAND TOGGLE */}
      {isCollapsible && (
        <button
          type="button"
          onClick={() => setExpanded(!expanded)}
          className="w-full p-4 flex items-center justify-between text-left hover:bg-surface-raised transition-colors border-b border-border/60"
          aria-expanded={expanded}
        >
          <div className="flex items-center gap-2">
            <span className="mono-meta text-accent font-bold">
              {metric.metricCode}
            </span>
            <span className="text-text-primary text-[13.5px] font-semibold">
              Understand this Metric &amp; Evidence
            </span>
          </div>
          <span className="text-text-tertiary text-[12px] font-mono">
            {expanded ? "Hide Details ▲" : "Examine Evidence ▼"}
          </span>
        </button>
      )}

      {expanded && (
        <div className="p-4 sm:p-5 space-y-5">
          {/* LAYER A: WHAT YUKIRA SEES */}
          <section aria-labelledby={`sees-title-${metric.metricCode}`}>
            <div className="flex items-center justify-between border-b border-border/60 pb-2">
              <span
                id={`sees-title-${metric.metricCode}`}
                className="eyebrow text-text-secondary"
              >
                Layer 1 &bull; What YUKIRA Sees
              </span>
              <div className="flex items-center gap-1.5">
                <span
                  className={`status-badge text-[10px] ${
                    isCalculated
                      ? "state-operational"
                      : isInsufficient
                      ? "state-candidate"
                      : "state-unavailable"
                  }`}
                >
                  {metric.calculationStatus || "NOT CALCULATED"}
                </span>
                <span
                  className={`status-badge text-[10px] ${
                    isApproved
                      ? "state-approved"
                      : isOperational
                      ? "state-operational"
                      : "state-candidate"
                  }`}
                >
                  {governanceStatus}
                </span>
              </div>
            </div>

            <div className="mt-3 flex flex-wrap items-baseline justify-between gap-3 bg-surface-raised p-3.5 rounded-md border border-border/40">
              <div>
                <span className="mono-meta text-[11px] block">
                  Authoritative Deterministic Result
                </span>
                <div className="flex items-baseline gap-2 mt-0.5">
                  <span className="data-value-lg text-text-primary">
                    {metric.formattedValue || (isCalculated ? "Calculated" : "Not available")}
                  </span>
                  {metric.units && metric.units !== "PERCENTAGE" && (
                    <span className="mono-meta text-[12px]">{metric.units}</span>
                  )}
                </div>
              </div>

              <div className="text-right font-mono text-[11px] text-text-secondary space-y-0.5">
                <div>
                  <span className="text-text-tertiary">Horizon: </span>
                  <span className="text-text-primary">
                    {metric.periodType || "3Y"}
                  </span>
                </div>
                {context.endDate && (
                  <div>
                    <span className="text-text-tertiary">Analysis Cutoff: </span>
                    <span className="text-text-primary">{context.endDate}</span>
                  </div>
                )}
                {context.knowledgeCutoffTime && (
                  <div className="truncate max-w-[240px]">
                    <span className="text-text-tertiary">Knowledge Cutoff: </span>
                    <span
                      className="text-text-secondary truncate"
                      title={context.knowledgeCutoffTime}
                    >
                      {context.knowledgeCutoffTime.split("T")[0]}
                    </span>
                  </div>
                )}
              </div>
            </div>

            {metric.errorMessage && (
              <div className="state-panel-error mt-2.5 p-2.5 text-[12px] font-mono text-critical-fg bg-critical-bg/20 border border-critical-border rounded">
                <strong>Calculation Notice:</strong> {metric.errorMessage}
              </div>
            )}
          </section>

          {/* LAYER B: WHY IT MATTERS */}
          <section aria-labelledby={`matters-title-${metric.metricCode}`}>
            <span
              id={`matters-title-${metric.metricCode}`}
              className="eyebrow text-text-secondary block border-b border-border/60 pb-1.5"
            >
              Layer 2 &bull; Why It Matters
            </span>
            <p className="mt-2 text-[13px] leading-[1.6] text-text-primary font-sans">
              {metric.interpretation || def.whyItMatters}
            </p>
            <div className="mt-2 p-2 bg-surface-inset rounded border border-border/30 text-[11px] font-mono text-text-tertiary flex items-center gap-1.5">
              <span className="text-accent font-bold">&bull;</span>
              <span>
                Descriptive empirical calculation. YUKIRA does not generate commercial ratings or investment forecasts.
              </span>
            </div>
          </section>

          {/* LAYER C: EMPIRICAL EVIDENCE & PROVENANCE */}
          <section aria-labelledby={`evidence-title-${metric.metricCode}`}>
            <span
              id={`evidence-title-${metric.metricCode}`}
              className="eyebrow text-text-secondary block border-b border-border/60 pb-1.5"
            >
              Layer 3 &bull; Supporting Evidence &amp; Provenance
            </span>

            <div className="mt-2.5 grid grid-cols-1 sm:grid-cols-2 gap-3 text-[11.5px] font-mono">
              <div className="panel-inset p-2.5 bg-surface-raised space-y-1">
                <span className="def-label text-[10px] block">Observation Coverage</span>
                <p className="text-text-primary font-semibold">
                  {context.observationCount != null
                    ? `${context.observationCount} Market Observations`
                    : "Observation count not available"}
                </p>
                {context.startDate && context.endDate ? (
                  <p className="text-text-tertiary text-[10.5px]">
                    Window: {context.startDate} &rarr; {context.endDate}
                  </p>
                ) : (
                  <p className="text-text-tertiary text-[10.5px]">
                    Window: Not available
                  </p>
                )}
              </div>

              <div className="panel-inset p-2.5 bg-surface-raised space-y-1">
                <span className="def-label text-[10px] block">Benchmark &amp; Proxy</span>
                {['RET-07', 'RAT-01', 'RAT-02', 'RAT-03', 'RAT-04', 'MKT-01', 'MKT-02', 'MKT-03', 'MKT-04', 'MKT-05', 'REL-01', 'REL-02', 'REL-03'].includes(metric.metricCode) ? (
                  <>
                    <p className="text-text-primary font-semibold">
                      {context.benchmarkCode || context.benchmarkName || "NIFTY 500 TRI"}
                    </p>
                    <p className="text-text-tertiary text-[10.5px]">
                      {['RAT-01', 'RAT-02', 'RAT-03', 'MKT-01', 'REL-02'].includes(metric.metricCode)
                        ? "Risk-Free: FBIL 91-Day T-Bill (M2N-02)"
                        : "Single benchmark relative"}
                    </p>
                  </>
                ) : (
                  <>
                    <p className="text-text-primary font-semibold text-text-secondary">
                      Not required
                    </p>
                    <p className="text-text-tertiary text-[10.5px]">
                      Single-asset standalone series
                    </p>
                  </>
                )}
              </div>

              {context.calculationRunId ? (
                <div className="panel-inset p-2.5 bg-surface-raised space-y-1">
                  <span className="def-label text-[10px] block">Audit Run Lineage</span>
                  <p className="text-text-primary font-semibold">
                    Calculation Run #{context.calculationRunId}
                  </p>
                  <p className="text-text-tertiary text-[10.5px]">
                    Tag: {context.methodologyTag || "Unspecified"} &bull; {context.engineSoftwareVersion || "Quant Kernel v1"}
                  </p>
                </div>
              ) : (
                <div className="panel-inset p-2.5 bg-surface-raised space-y-1">
                  <span className="def-label text-[10px] block">Audit Run Lineage</span>
                  <p className="text-text-secondary text-[11px]">
                    Lineage reference not available
                  </p>
                </div>
              )}

              {context.inputSnapshotSha256 ? (
                <div className="panel-inset p-2.5 bg-surface-raised space-y-1">
                  <span className="def-label text-[10px] block">Cryptographic Hash</span>
                  <code
                    className="text-[10px] text-accent block truncate select-all"
                    title={context.inputSnapshotSha256}
                  >
                    {context.inputSnapshotSha256.substring(0, 16)}...
                  </code>
                  <p className="text-text-tertiary text-[10px]">
                    Immutable point-in-time SHA-256 digest
                  </p>
                </div>
              ) : (
                <div className="panel-inset p-2.5 bg-surface-raised space-y-1">
                  <span className="def-label text-[10px] block">Cryptographic Hash</span>
                  <p className="text-text-secondary text-[11px]">
                    Digest not available
                  </p>
                </div>
              )}
            </div>

            {/* Formula disclosure */}
            <div className="mt-2.5 panel-inset p-2.5 bg-surface-inset">
              <div className="flex items-center justify-between mb-1">
                <span className="def-label text-[10.5px]">Mathematical Specification</span>
                <Link
                  href="/methodology"
                  className="text-link text-[10.5px] hover:underline"
                >
                  Methodology Docs &rarr;
                </Link>
              </div>
              <code className="block font-mono text-[11px] text-accent overflow-x-auto p-1 bg-surface-raised rounded">
                {metric.formulaDisclosure || def.formulaSpecification}
              </code>
            </div>
          </section>

          {/* LAYER D: LIMITATIONS AND UNCERTAINTY */}
          <section aria-labelledby={`limits-title-${metric.metricCode}`}>
            <span
              id={`limits-title-${metric.metricCode}`}
              className="eyebrow text-candidate-fg block border-b border-candidate-border/60 pb-1.5"
            >
              Layer 4 &bull; Limitations &amp; Uncertainty
            </span>
            <div className="mt-2 p-3 bg-candidate-bg/10 border-l-2 border-candidate-fg rounded-r text-[12.5px] leading-[1.55] text-text-secondary">
              <p>{metric.limitations || def.limitations}</p>
            </div>
          </section>

          {/* LAYER E: WHAT TO INVESTIGATE NEXT */}
          <section
            className="pt-2 border-t border-border/80"
            aria-labelledby={`next-title-${metric.metricCode}`}
          >
            <div className="p-3.5 bg-surface-raised border border-accent/30 rounded-lg space-y-3">
              <div className="flex items-center justify-between">
                <span
                  id={`next-title-${metric.metricCode}`}
                  className="eyebrow text-accent font-semibold"
                >
                  Layer 5 &bull; Due Diligence Investigation
                </span>
                <span className="mono-meta text-[10px]">Research Workspace Action</span>
              </div>

              <div>
                <span className="mono-meta text-[10.5px] text-text-tertiary block">
                  Suggested Due Diligence Question:
                </span>
                <p className="text-[13px] font-medium text-text-primary mt-1 leading-snug font-sans">
                  &ldquo;{question}&rdquo;
                </p>
              </div>

              {onInvestigate && (
                <div className="flex items-center justify-end pt-1">
                  <button
                    type="button"
                    onClick={handleInvestigateClick}
                    className="btn btn-primary btn-sm flex items-center gap-1.5 shadow-sm"
                  >
                    <svg
                      className="h-3.5 w-3.5"
                      fill="none"
                      viewBox="0 0 24 24"
                      stroke="currentColor"
                      aria-hidden="true"
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={2}
                        d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z"
                      />
                    </svg>
                    <span>Investigate this in Research Workspace</span>
                  </button>
                </div>
              )}
            </div>
          </section>
        </div>
      )}
    </div>
  );
}
