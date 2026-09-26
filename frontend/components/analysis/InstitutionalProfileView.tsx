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
  "RET-07": "3Y Active Return",
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
  "REL-01": "Tracking Error (3Y)",
  "REL-02": "Jensen's Alpha (3Y)",
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

  const getBadgeStyle = (status: string) => {
    if (status === "APPROVED") {
      return "bg-emerald-500/15 text-emerald-600 dark:text-emerald-400 border-emerald-500/30";
    }
    if (status.includes("OPERATIONAL")) {
      return "bg-sky-500/15 text-sky-600 dark:text-sky-400 border-sky-500/30";
    }
    return "bg-amber-500/15 text-amber-700 dark:text-amber-400 border-amber-500/30";
  };

  const renderMetricCard = (item: ProfileMetricItem) => {
    const isExpanded = expandedMetric === item.metricCode;
    const isCandidate = item.governanceStatus === "CANDIDATE";
    const badgeStyle = getBadgeStyle(item.governanceStatus);
    const displayName = getMetricDisplayName(item.metricCode, item.metricName);

    return (
      <div
        key={item.metricCode}
        className="rounded-xl border border-border bg-surface hover:border-border-subtle hover:bg-surface-elevated transition-all duration-200 p-5 space-y-3 flex flex-col justify-between"
      >
        <div className="space-y-3">
          {/* Header: Human-readable Name as Primary Title (No internal code or governance badge in normal view) */}
          <div className="flex flex-wrap items-start justify-between gap-2">
            <div className="space-y-1">
              <h4 className="text-sm font-semibold text-text-primary tracking-tight">
                {displayName}
              </h4>
              {item.periodType && (
                <div className="font-mono text-[10px] text-text-muted">
                  {item.periodType}
                </div>
              )}
            </div>
          </div>

          {/* Prominent Value Display */}
          <div className="py-1">
            <div className="flex items-baseline gap-2">
              <span className="font-mono text-2xl font-bold text-text-primary tracking-tight">
                {item.formattedValue || "—"}
              </span>
              {item.units && (
                <span className="font-mono text-[10px] text-text-muted uppercase">
                  {item.units}
                </span>
              )}
            </div>
          </div>

          {/* Statistical Interpretation */}
          <p className="text-xs text-text-secondary leading-relaxed font-sans">
            {item.interpretation}
          </p>
        </div>

        {/* Progressive Disclosure: Formula, Diagnostics, Limitations */}
        <div className="pt-2 border-t border-border mt-3">
          <button
            onClick={() => toggleMetricExpand(item.metricCode)}
            className="flex items-center justify-between w-full text-[11px] font-mono text-text-secondary hover:text-text-primary transition"
          >
            <span>{isExpanded ? "Hide Formula & Details" : "View Formula & Details"}</span>
            <svg
              className={`h-3.5 w-3.5 transform transition-transform ${isExpanded ? "rotate-180" : ""}`}
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
            </svg>
          </button>

          {isExpanded && (
            <div className="mt-3 space-y-2 text-xs font-mono bg-surface-elevated rounded-lg p-3 border border-border animate-in fade-in duration-150">
              {/* Technical Audit Reference */}
              <div className="flex items-center justify-between gap-2 p-2 rounded bg-surface border border-border text-[11px]">
                <span className="text-text-muted">
                  Internal Identifier: <strong className="text-text-primary">{item.metricCode}</strong>
                </span>
                <span className={`inline-flex items-center rounded px-2 py-0.5 text-[10px] font-semibold border ${badgeStyle}`}>
                  {item.governanceStatus}
                </span>
              </div>

              <div>
                <span className="text-text-muted text-[10px] uppercase tracking-wider block mb-0.5">Formula:</span>
                <code className="text-accent text-[11px] block overflow-x-auto p-1.5 bg-surface rounded border border-border">
                  {item.formulaDisclosure}
                </code>
              </div>

              <div>
                <span className="text-text-muted text-[10px] uppercase tracking-wider block mb-0.5">Methodology Limitations:</span>
                <p className="text-text-secondary text-[11px] leading-relaxed font-sans">{item.limitations}</p>
              </div>

              {isCandidate && (
                <div className="rounded border border-amber-500/20 bg-amber-500/5 px-2.5 py-1.5 text-[11px] text-amber-700 dark:text-amber-300/90 font-mono">
                  <strong className="text-amber-800 dark:text-amber-400">Methodology Notice: </strong>
                  Candidate specification &bull; Subject to empirical regime validation.
                </div>
              )}

              {item.errorMessage && (
                <div className="text-danger text-[11px]">
                  <strong>Error: </strong>{item.errorMessage}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    );
  };

  return (
    <div className="space-y-8 font-sans">
      {/* 1. EXECUTIVE CONTEXT HEADER */}
      <div className="rounded-2xl border border-border bg-surface p-6 backdrop-blur-md shadow-sm space-y-5 transition-colors">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6 pb-6 border-b border-border">
          <div className="space-y-2">
            <div className="flex flex-wrap items-center gap-2 font-mono text-xs">
              <span className="rounded bg-surface-elevated border border-border px-2.5 py-1 text-text-secondary font-semibold">
                Run #{context.runId}
              </span>
              <span className="rounded bg-emerald-500/15 border border-emerald-500/30 px-2.5 py-1 text-emerald-600 dark:text-emerald-400 font-bold uppercase">
                {context.runStatus}
              </span>
              <span className="text-text-muted">&bull;</span>
              <span className="text-text-secondary">AMFI: {context.amfiCode}</span>
              <span className="text-text-muted">&bull;</span>
              <span className="text-text-secondary">ISIN: {context.isin}</span>
            </div>

            <h1 className="text-xl lg:text-2xl font-bold tracking-tight text-text-primary">
              {context.schemeName}
            </h1>
            <p className="text-xs text-text-secondary font-mono">
              3-Year Institutional Risk-Return Profile &bull; 16 Deterministic Financial Metrics
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            {onRerunRequested && (
              <button
                onClick={onRerunRequested}
                className="rounded-lg border border-border bg-surface-elevated px-3.5 py-2 text-xs font-mono font-medium text-text-secondary hover:text-text-primary hover:bg-surface transition"
              >
                Re-run Profile Analysis
              </button>
            )}
            <Link
              href={`/funds/${context.schemeOptionId || 1}`}
              className="rounded-lg bg-accent px-3.5 py-2 text-xs font-mono font-semibold text-accent-foreground hover:opacity-90 transition shadow-sm"
            >
              Fund Detail
            </Link>
          </div>
        </div>

        {/* Context metadata strip */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-xs font-mono">
          <div className="space-y-1">
            <span className="text-text-muted text-[10px] uppercase tracking-wider block">Evaluation Window</span>
            <span className="text-text-primary font-semibold">{context.startDate} &rarr; {context.asOfDate}</span>
          </div>

          <div className="space-y-1">
            <span className="text-text-muted text-[10px] uppercase tracking-wider block">Benchmark</span>
            <span className="text-accent font-semibold">{context.benchmarkCode}</span>
          </div>

          <div className="space-y-1">
            <span className="text-text-muted text-[10px] uppercase tracking-wider block">Risk-Free Proxy</span>
            <span className="text-text-primary font-semibold">{context.riskFreeProxy}</span>
          </div>

          <div className="space-y-1">
            <span className="text-text-muted text-[10px] uppercase tracking-wider block">Knowledge Cutoff</span>
            <span className="text-amber-600 dark:text-amber-400 font-semibold truncate block" title={context.knowledgeCutoffTime}>
              {context.knowledgeCutoffTime.replace("T", " ")}
            </span>
          </div>
        </div>
      </div>

      {/* 2. TAB NAVIGATION */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-border pb-3">
        <div className="flex flex-wrap gap-1 font-mono text-xs">
          <button
            onClick={() => setActiveTab("all")}
            className={`rounded-lg px-3 py-1.5 transition ${
              activeTab === "all" ? "bg-surface-elevated text-text-primary font-semibold border border-border" : "text-text-secondary hover:text-text-primary hover:bg-surface"
            }`}
          >
            All 16 Metrics
          </button>
          <button
            onClick={() => setActiveTab("return")}
            className={`rounded-lg px-3 py-1.5 transition ${
              activeTab === "return" ? "bg-surface-elevated text-text-primary font-semibold border border-border" : "text-text-secondary hover:text-text-primary hover:bg-surface"
            }`}
          >
            Return & Benchmark ({returnMetrics.length})
          </button>
          <button
            onClick={() => setActiveTab("risk")}
            className={`rounded-lg px-3 py-1.5 transition ${
              activeTab === "risk" ? "bg-surface-elevated text-text-primary font-semibold border border-border" : "text-text-secondary hover:text-text-primary hover:bg-surface"
            }`}
          >
            Total & Tail Risk ({riskMetrics.length})
          </button>
          <button
            onClick={() => setActiveTab("riskAdjusted")}
            className={`rounded-lg px-3 py-1.5 transition ${
              activeTab === "riskAdjusted" ? "bg-surface-elevated text-text-primary font-semibold border border-border" : "text-text-secondary hover:text-text-primary hover:bg-surface"
            }`}
          >
            Risk-Adjusted ({riskAdjustedMetrics.length})
          </button>
          <button
            onClick={() => setActiveTab("marketSensitivity")}
            className={`rounded-lg px-3 py-1.5 transition ${
              activeTab === "marketSensitivity" ? "bg-surface-elevated text-text-primary font-semibold border border-border" : "text-text-secondary hover:text-text-primary hover:bg-surface"
            }`}
          >
            Sensitivity & Alpha ({marketSensitivityMetrics.length})
          </button>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => setShowProvenance(!showProvenance)}
            className="text-[11px] font-mono text-text-secondary hover:text-accent transition"
          >
            {showProvenance ? "Hide Audit Details" : "Audit Lineage"}
          </button>
          <span className="text-text-muted">&bull;</span>
          <button
            onClick={() => setShowQuality(!showQuality)}
            className="text-[11px] font-mono text-text-secondary hover:text-accent transition"
          >
            {showQuality ? "Hide Quality" : "Data Quality Taxonomy"}
          </button>
        </div>
      </div>

      {/* 3. PROVENANCE CARD (Collapsible) */}
      {showProvenance && (
        <div className="rounded-xl border border-border bg-surface-elevated p-5 font-mono text-xs space-y-4 animate-in fade-in duration-150">
          <div className="flex items-center justify-between border-b border-border pb-3">
            <h3 className="font-bold text-text-primary uppercase tracking-wider text-xs">
              Cryptographic Audit Provenance
            </h3>
            <span className="text-text-muted text-[10px]">Deterministic Audit Lineage</span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <span className="text-text-muted text-[10px] uppercase block mb-1">Input Snapshot SHA-256</span>
              <code className="text-[11px] text-accent bg-surface p-2 rounded block break-all select-all border border-border">
                {provenance.inputSnapshotSha256}
              </code>
            </div>

            <div className="space-y-1">
              <span className="text-text-muted text-[10px] uppercase block mb-1">Execution Metrics</span>
              <div className="text-text-secondary text-[11px]">Engine Version: {provenance.engineSoftwareVersion}</div>
              <div className="text-text-secondary text-[11px]">Methodology Tag: {provenance.methodologyTag}</div>
              <div className="text-text-secondary text-[11px]">Execution Started: {formatDateTime(provenance.executionStartedAt)}</div>
              <div className="text-text-secondary text-[11px]">Execution Completed: {provenance.executionCompletedAt ? formatDateTime(provenance.executionCompletedAt) : "—"}</div>
            </div>
          </div>

          <div className="grid grid-cols-3 gap-4 pt-2 border-t border-border">
            <div>
              <span className="text-text-muted text-[10px] uppercase block">NAV Observations</span>
              <span className="text-emerald-600 dark:text-emerald-400 font-bold text-sm">{provenance.navObservationCount}</span>
            </div>
            <div>
              <span className="text-text-muted text-[10px] uppercase block">Benchmark Observations</span>
              <span className="text-accent font-bold text-sm">{provenance.benchmarkObservationCount}</span>
            </div>
            <div>
              <span className="text-text-muted text-[10px] uppercase block">Risk-Free Observations</span>
              <span className="text-amber-600 dark:text-amber-400 font-bold text-sm">{provenance.riskFreeObservationCount}</span>
            </div>
          </div>
        </div>
      )}

      {/* 4. DATA QUALITY TAXONOMY (Collapsible) */}
      {showQuality && (
        <div className="rounded-xl border border-border bg-surface-elevated p-5 font-mono text-xs space-y-4 animate-in fade-in duration-150">
          <div className="flex items-center justify-between border-b border-border pb-3">
            <h3 className="font-bold text-text-primary uppercase tracking-wider text-xs">
              Six-Dimensional Data Quality Taxonomy
            </h3>
            <span className="text-emerald-600 dark:text-emerald-400 font-bold">{quality.overallAssessment}</span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {quality.dimensions.map((dim, idx) => (
              <div key={idx} className="rounded-lg border border-border bg-surface p-3 space-y-1">
                <div className="flex items-center justify-between text-[11px]">
                  <span className="text-text-secondary">{dim.dimension}</span>
                  <span className="rounded bg-surface-elevated border border-border px-1.5 py-0.5 text-[10px] font-bold text-text-primary">
                    {dim.state}
                  </span>
                </div>
                <p className="text-[10px] text-text-muted leading-relaxed font-sans">{dim.description}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* 5. METRIC SECTIONS */}
      {(activeTab === "all" || activeTab === "return") && (
        <section className="space-y-4">
          <div className="flex items-center gap-2 border-b border-border pb-2">
            <span className="h-2 w-2 rounded-full bg-sky-500" />
            <h2 className="text-sm font-bold uppercase tracking-wider text-text-primary font-mono">
              1. Return & Benchmark Dynamics
            </h2>
            <span className="text-xs text-text-muted font-mono">({returnMetrics.length} metrics)</span>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {returnMetrics.map(renderMetricCard)}
          </div>
        </section>
      )}

      {(activeTab === "all" || activeTab === "risk") && (
        <section className="space-y-4">
          <div className="flex items-center gap-2 border-b border-border pb-2">
            <span className="h-2 w-2 rounded-full bg-amber-500" />
            <h2 className="text-sm font-bold uppercase tracking-wider text-text-primary font-mono">
              2. Total & Tail Risk Dynamics
            </h2>
            <span className="text-xs text-text-muted font-mono">({riskMetrics.length} metrics)</span>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {riskMetrics.map(renderMetricCard)}
          </div>
        </section>
      )}

      {(activeTab === "all" || activeTab === "riskAdjusted") && (
        <section className="space-y-4">
          <div className="flex items-center gap-2 border-b border-border pb-2">
            <span className="h-2 w-2 rounded-full bg-emerald-500" />
            <h2 className="text-sm font-bold uppercase tracking-wider text-text-primary font-mono">
              3. Risk-Adjusted Performance
            </h2>
            <span className="text-xs text-text-muted font-mono">({riskAdjustedMetrics.length} metrics)</span>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {riskAdjustedMetrics.map(renderMetricCard)}
          </div>
        </section>
      )}

      {(activeTab === "all" || activeTab === "marketSensitivity") && (
        <section className="space-y-4">
          <div className="flex items-center gap-2 border-b border-border pb-2">
            <span className="h-2 w-2 rounded-full bg-indigo-500" />
            <h2 className="text-sm font-bold uppercase tracking-wider text-text-primary font-mono">
              4. Market Sensitivity & Alpha Dynamics
            </h2>
            <span className="text-xs text-text-muted font-mono">({marketSensitivityMetrics.length} metrics)</span>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {marketSensitivityMetrics.map(renderMetricCard)}
          </div>
        </section>
      )}

      {/* 6. EPISTEMIC DISCLAIMER FOOTER */}
      <div className="rounded-xl border border-border bg-surface p-4 font-mono text-[11px] text-text-secondary space-y-2">
        <div className="flex items-center gap-2 text-amber-700 dark:text-amber-400 font-bold uppercase">
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
          Institutional Epistemic Guardrail
        </div>
        <p className="text-text-secondary leading-relaxed font-sans">
          {limitations.candidateMethodologyDisclaimer}
        </p>
        <p className="text-text-muted text-[10px]">
          Point-in-Time Cutoff: {limitations.knowledgeCutoff} &bull; Analysis Cutoff: {limitations.analysisCutoff} &bull; Zero generative financial forecasts &bull; Evidence Over Opinion.
        </p>
      </div>
    </div>
  );
}
