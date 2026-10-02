"use client";

import React, { useState } from "react";
import Link from "next/link";
import { AnalyticalProfileResponse, ProfileMetricItem } from "@/types/analysis";
import { formatDateTime } from "@/lib/utils/formatters";

interface InstitutionalProfileViewProps {
  data: AnalyticalProfileResponse;
  onRerunRequested?: () => void;
}

const AUTHORITATIVE_METRIC_NAMES: Record<string, string> = {
  "RET-01": "1-Year CAGR",
  "RET-02": "Simple Period Return",
  "RET-03": "3-Year CAGR",
  "RET-04": "5-Year CAGR",
  "RET-05": "3Y Rolling Return Mean",
  "RET-06": "Rolling Outperformance %",
  "RSK-01": "Annualized Volatility (3Y)",
  "RSK-02": "Downside Semideviation (3Y)",
  "RSK-03": "Maximum Drawdown (3Y)",
  "RSK-04": "Maximum Drawdown Duration",
  "RSK-05": "Ulcer Index (3Y)",
  "RSK-06": "Historical VaR 95%",
  "RSK-07": "Expected Shortfall 95%",
  "RAT-01": "Sharpe Ratio (3Y)",
  "RAT-02": "Sortino Ratio (3Y)",
  "RAT-03": "Treynor Ratio (3Y)",
  "RAT-04": "Information Ratio (3Y)",
  "MKT-01": "Portfolio Beta (3Y)",
  "MKT-02": "Downside Beta (3Y)",
  "MKT-03": "Upside Capture Ratio (3Y)",
  "MKT-04": "Downside Capture Ratio (3Y)",
  "MKT-05": "Capture Spread (3Y)",
  "REL-01": "Beta (3Y)",
  "REL-02": "Tracking Error (3Y)",
  "REL-03": "Jensen's Alpha (3Y)",
  "REL-04": "Downside Beta (3Y)",
  "REL-05": "Upside Beta (3Y)",
  "REL-06": "Annualized Mean Active Return (3Y)",
  "PRT-01": "Top-10 Concentration",
};

export function InstitutionalProfileView({ data, onRerunRequested }: InstitutionalProfileViewProps) {
  const { context, returnMetrics, riskMetrics, riskAdjustedMetrics, marketSensitivityMetrics, provenance, quality, limitations } = data;
  const [activeTab, setActiveTab] = useState<"all" | "return" | "risk" | "riskAdjusted" | "marketSensitivity">("all");
  const [expandedMetric, setExpandedMetric] = useState<string | null>(null);
  const [showProvenance, setShowProvenance] = useState(false);
  const [showQuality, setShowQuality] = useState(false);

  const toggleMetricExpand = (code: string) => {
    setExpandedMetric(expandedMetric === code ? null : code);
  };

  const getMetricDisplayName = (code: string, fallbackName: string) => {
    return AUTHORITATIVE_METRIC_NAMES[code] || fallbackName;
  };

  /**
   * Governance state is a SEMANTIC state, so it maps onto a semantic token —
   * never onto a decorative category colour.
   */
  const getGovernanceStateClass = (status: string) => {
    if (status === "APPROVED") {
      return "state-approved";
    }
    if (status.includes("OPERATIONAL")) {
      return "state-operational";
    }
    return "state-candidate";
  };

  const renderMetricCard = (item: ProfileMetricItem) => {
    const isExpanded = expandedMetric === item.metricCode;
    const isCandidate = item.governanceStatus === "CANDIDATE";
    const governanceState = getGovernanceStateClass(item.governanceStatus);
    const displayName = getMetricDisplayName(item.metricCode, item.metricName);

    return (
      <div
        key={item.metricCode}
        className="metric-tile transition-colors hover:bg-surface-raised"
      >
        {/* Header: Human-readable Name as Primary Title */}
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div className="min-w-0">
            <h4 className="text-[14px] font-semibold tracking-[-0.01em] text-text-primary">
              {displayName}
            </h4>
            {item.periodType && (
              <p className="mono-meta mt-0.5">{item.periodType}</p>
            )}
          </div>
        </div>

        {/* Prominent Value Display */}
        <div className="mt-3">
          <div className="flex items-baseline gap-2">
            <span className="data-value-lg">
              {item.formattedValue || "Not available"}
            </span>
            {item.units && <span className="mono-meta">{item.units}</span>}
          </div>
        </div>

        <div className="metric-rule my-3" aria-hidden />

        {/* Statistical Interpretation */}
        <p className="mono-meta font-sans leading-[1.55]">{item.interpretation}</p>

        {/* Progressive Disclosure: Formula, Diagnostics, Limitations */}
        <div className="mt-auto pt-4">
          <div className="border-t border-border pt-3">
            <button
              type="button"
              onClick={() => toggleMetricExpand(item.metricCode)}
              aria-expanded={isExpanded}
              className="flex w-full items-center justify-between gap-2 text-left text-[12px] font-medium text-accent transition-colors hover:text-accent-hover"
            >
              <span>{isExpanded ? "Hide Formula & Details" : "View Formula & Details"}</span>
              <span
                className={`tab-ordinal transition-transform ${isExpanded ? "rotate-180" : ""}`}
                aria-hidden
              >
                ▼
              </span>
            </button>

            {isExpanded && (
              <div className="mt-3 space-y-2.5">
                {/* Technical Audit Reference */}
                <div className="panel-inset flex flex-wrap items-center justify-between gap-2 px-3 py-2">
                  <p className="mono-meta font-sans">
                    Internal Identifier:{" "}
                    <strong className="text-text-primary">{item.metricCode}</strong>
                  </p>
                  <span className={`status-badge ${governanceState}`}>
                    {item.governanceStatus}
                  </span>
                </div>

                <div>
                  <p className="def-label">Formula</p>
                  <code className="mt-1 block overflow-x-auto rounded-sm border border-border bg-surface-inset p-2 text-accent">
                    {item.formulaDisclosure}
                  </code>
                </div>

                <div>
                  <p className="def-label">Methodology Limitations</p>
                  <p className="mono-meta font-sans leading-[1.5]">{item.limitations}</p>
                </div>

                {isCandidate && (
                  <div className="panel-inset px-3 py-2">
                    <p className="eyebrow text-candidate-fg">Methodology Notice</p>
                    <p className="mono-meta mt-1 font-sans">
                      Candidate specification &bull; Subject to empirical regime validation.
                    </p>
                  </div>
                )}

                {item.errorMessage && (
                  <div className="state-panel-error">
                    <p className="eyebrow text-critical-fg">Error</p>
                    <p className="mt-1 text-[12px] leading-[1.5] text-critical-fg">
                      {item.errorMessage}
                    </p>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    );
  };

  return (
    <div className="space-y-6">
      {/* 1. EXECUTIVE CONTEXT HEADER */}
      <section className="panel p-4 md:p-5">
        <div className="panel-header flex-wrap border-b border-border pb-4">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <span className="status-badge state-unavailable">Run #{context.runId}</span>
              <span className="status-badge state-approved">
                <span className="status-dot" aria-hidden />
                {context.runStatus}
              </span>
              <span className="status-badge state-accent">AMFI: {context.amfiCode}</span>
              <span className="status-badge state-accent">ISIN: {context.isin}</span>
            </div>

            <h1 className="mt-2 text-[22px] font-semibold tracking-[-0.015em] text-text-primary">
              {context.schemeName}
            </h1>
            <p className="mono-meta mt-1 font-sans">
              3-Year Institutional Risk-Return Profile &bull; 16 Deterministic Financial Metrics
            </p>
          </div>

          <div className="flex shrink-0 flex-wrap items-center gap-2">
            {onRerunRequested && (
              <button type="button" onClick={onRerunRequested} className="btn btn-ghost">
                Re-run Profile Analysis
              </button>
            )}
            <Link href={`/funds/${context.schemeOptionId || 1}`} className="btn btn-primary">
              Fund Detail
            </Link>
          </div>
        </div>

        {/* Context metadata strip */}
        <div className="def-list def-list-4 mt-4">
          <div>
            <p className="def-label">Evaluation Window</p>
            <p className="def-value">
              {context.startDate} &rarr; {context.asOfDate}
            </p>
          </div>
          <div>
            <p className="def-label">Benchmark</p>
            <p className="def-value text-accent">{context.benchmarkCode}</p>
          </div>
          <div>
            <p className="def-label">Risk-Free Proxy</p>
            <p className="def-value">{context.riskFreeProxy}</p>
          </div>
          <div>
            <p className="def-label">Knowledge Cutoff</p>
            <p
              className="def-value text-candidate-fg truncate"
              title={context.knowledgeCutoffTime}
            >
              {context.knowledgeCutoffTime.replace("T", " ")}
            </p>
          </div>
        </div>
      </section>

      {/* 2. TAB NAVIGATION + AUDIT CONTROLS */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="tab-rail min-w-0 flex-1" role="tablist" aria-label="Metric category">
          {(
            [
              ["all", "All", returnMetrics.length + riskMetrics.length + riskAdjustedMetrics.length + marketSensitivityMetrics.length],
              ["return", "Return & Benchmark", returnMetrics.length],
              ["risk", "Total & Tail Risk", riskMetrics.length],
              ["riskAdjusted", "Risk-Adjusted", riskAdjustedMetrics.length],
              ["marketSensitivity", "Sensitivity & Alpha", marketSensitivityMetrics.length],
            ] as const
          ).map(([key, label, count]) => (
            <button
              key={key}
              type="button"
              role="tab"
              aria-selected={activeTab === key}
              data-active={activeTab === key}
              onClick={() => setActiveTab(key)}
              className="tab"
            >
              <span className="tab-ordinal">{String(count).padStart(2, "0")}</span>
              <span>{label}</span>
            </button>
          ))}
        </div>

        <div className="flex shrink-0 items-center gap-2">
          <button
            type="button"
            onClick={() => setShowProvenance((prev) => !prev)}
            aria-expanded={showProvenance}
            className="status-badge state-unavailable transition-colors hover:border-border-strong"
          >
            {showProvenance ? "Hide Audit Details" : "Audit Lineage"}
          </button>
          <button
            type="button"
            onClick={() => setShowQuality((prev) => !prev)}
            aria-expanded={showQuality}
            className="status-badge state-unavailable transition-colors hover:border-border-strong"
          >
            {showQuality ? "Hide Quality" : "Data Quality Taxonomy"}
          </button>
        </div>
      </div>

      {/* 3. PROVENANCE CARD (Collapsible) */}
      {showProvenance && (
        <section className="panel p-4 md:p-5">
          <div className="panel-header border-b border-border pb-3">
            <p className="eyebrow text-accent">Cryptographic Audit Provenance</p>
            <span className="mono-meta shrink-0">Deterministic Audit Lineage</span>
          </div>

          <div className="grid grid-cols-1 gap-4 pt-4 md:grid-cols-2">
            <div>
              <p className="def-label">Input Snapshot SHA-256</p>
              <code className="mt-1 block select-all break-all rounded-sm border border-border bg-surface-inset p-2 text-accent">
                {provenance.inputSnapshotSha256}
              </code>
            </div>

            <div className="def-list">
              <div>
                <p className="def-label">Engine Version</p>
                <p className="def-value">{provenance.engineSoftwareVersion}</p>
              </div>
              <div>
                <p className="def-label">Methodology Tag</p>
                <p className="def-value">{provenance.methodologyTag}</p>
              </div>
              <div>
                <p className="def-label">Execution Started</p>
                <p className="def-value">{formatDateTime(provenance.executionStartedAt)}</p>
              </div>
              <div>
                <p className="def-label">Execution Completed</p>
                <p className="def-value">
                  {provenance.executionCompletedAt
                    ? formatDateTime(provenance.executionCompletedAt)
                    : "Not available"}
                </p>
              </div>
            </div>
          </div>

          <div className="def-list def-list-3 mt-4 border-t border-border pt-4">
            <div>
              <p className="def-label">NAV Observations</p>
              <p className="data-value-sm text-approved-fg">{provenance.navObservationCount}</p>
            </div>
            <div>
              <p className="def-label">Benchmark Observations</p>
              <p className="data-value-sm text-accent">
                {provenance.benchmarkObservationCount}
              </p>
            </div>
            <div>
              <p className="def-label">Risk-Free Observations</p>
              <p className="data-value-sm text-candidate-fg">
                {provenance.riskFreeObservationCount}
              </p>
            </div>
          </div>
        </section>
      )}

      {/* 4. DATA QUALITY TAXONOMY (Collapsible) */}
      {showQuality && (
        <section className="panel p-4 md:p-5">
          <div className="panel-header border-b border-border pb-3">
            <p className="eyebrow text-accent">Six-Dimensional Data Quality Taxonomy</p>
            <span className="status-badge state-approved shrink-0">
              {quality.overallAssessment}
            </span>
          </div>

          <div className="grid grid-cols-1 gap-3 pt-4 sm:grid-cols-2 lg:grid-cols-3">
            {quality.dimensions.map((dim, idx) => (
              <div key={idx} className="panel-inset p-3">
                <div className="flex items-center justify-between gap-2">
                  <p className="def-label mb-0">{dim.dimension}</p>
                  <span className="status-badge state-unavailable shrink-0">{dim.state}</span>
                </div>
                <p className="mono-meta mt-1.5 font-sans leading-[1.5]">{dim.description}</p>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* 5. METRIC SECTIONS */}
      {(activeTab === "all" || activeTab === "return") && (
        <section>
          <div className="flex flex-wrap items-center gap-2 border-b border-border pb-2">
            <span className="tab-ordinal">01</span>
            <h2 className="text-[14px] font-semibold tracking-[-0.01em] text-text-primary">
              Return &amp; Benchmark Dynamics
            </h2>
            <span className="mono-meta">({returnMetrics.length} metrics)</span>
          </div>
          <div className="grid grid-cols-1 gap-4 pt-4 md:grid-cols-3">
            {returnMetrics.map(renderMetricCard)}
          </div>
        </section>
      )}

      {(activeTab === "all" || activeTab === "risk") && (
        <section>
          <div className="flex flex-wrap items-center gap-2 border-b border-border pb-2">
            <span className="tab-ordinal">02</span>
            <h2 className="text-[14px] font-semibold tracking-[-0.01em] text-text-primary">
              Total &amp; Tail Risk Dynamics
            </h2>
            <span className="mono-meta">({riskMetrics.length} metrics)</span>
          </div>
          <div className="grid grid-cols-1 gap-4 pt-4 md:grid-cols-2 lg:grid-cols-3">
            {riskMetrics.map(renderMetricCard)}
          </div>
        </section>
      )}

      {(activeTab === "all" || activeTab === "riskAdjusted") && (
        <section>
          <div className="flex flex-wrap items-center gap-2 border-b border-border pb-2">
            <span className="tab-ordinal">03</span>
            <h2 className="text-[14px] font-semibold tracking-[-0.01em] text-text-primary">
              Risk-Adjusted Performance
            </h2>
            <span className="mono-meta">({riskAdjustedMetrics.length} metrics)</span>
          </div>
          <div className="grid grid-cols-1 gap-4 pt-4 md:grid-cols-2">
            {riskAdjustedMetrics.map(renderMetricCard)}
          </div>
        </section>
      )}

      {(activeTab === "all" || activeTab === "marketSensitivity") && (
        <section>
          <div className="flex flex-wrap items-center gap-2 border-b border-border pb-2">
            <span className="tab-ordinal">04</span>
            <h2 className="text-[14px] font-semibold tracking-[-0.01em] text-text-primary">
              Market Sensitivity &amp; Alpha Dynamics
            </h2>
            <span className="mono-meta">({marketSensitivityMetrics.length} metrics)</span>
          </div>
          <div className="grid grid-cols-1 gap-4 pt-4 md:grid-cols-2">
            {marketSensitivityMetrics.map(renderMetricCard)}
          </div>
        </section>
      )}

      {/* 6. EPISTEMIC DISCLAIMER FOOTER */}
      <section className="panel-inset p-4">
        <p className="eyebrow text-candidate-fg">Institutional Epistemic Guardrail</p>
        <p className="prose-measure mt-2 text-[13px] leading-[1.55] text-text-secondary">
          {limitations.candidateMethodologyDisclaimer}
        </p>
        <p className="mono-meta mt-2 border-t border-border-subtle pt-2 font-sans">
          Point-in-Time Cutoff: {limitations.knowledgeCutoff} &bull; Analysis Cutoff:{" "}
          {limitations.analysisCutoff} &bull; Zero generative financial forecasts &bull; Evidence Over
          Opinion.
        </p>
      </section>
    </div>
  );
}