"use client";

import React, { useState, useEffect, useMemo } from "react";
import Link from "next/link";
import { AnalyticalProfileResponse, ProfileMetricItem } from "@/types/analysis";
import { formatDateTime } from "@/lib/utils/formatters";
import { MetricEvidencePanel } from "@/components/analysis/MetricEvidencePanel";
import { ResearchNoteModal } from "@/components/watchlist/ResearchNoteModal";
import { useWatchlist } from "@/lib/hooks/useWatchlist";
import { WatchlistResponse } from "@/lib/api/investor";
import { GroundedAiInterpretationPanel } from "@/components/analysis/GroundedAiInterpretationPanel";

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
  "RET-07": "3-Year Active Return",
  "MKT-01": "Portfolio Beta (3Y)",
  "MKT-02": "Downside Beta (3Y)",
  "MKT-03": "Upside Capture Ratio (3Y)",
  "MKT-04": "Downside Capture Ratio (3Y)",
  "MKT-05": "Capture Spread (3Y)",
  "REL-01": "Tracking Error (3Y)",
  "REL-02": "Jensen's Alpha (3Y)",
  "REL-03": "Information Ratio (Alternative)",
  "PRT-01": "Top-10 Concentration",
};

export function InstitutionalProfileView({ data, onRerunRequested }: InstitutionalProfileViewProps) {
  const { context, returnMetrics, riskMetrics, riskAdjustedMetrics, marketSensitivityMetrics, provenance, quality, limitations } = data;
  const [activeTab, setActiveTab] = useState<
    "all" | "return" | "risk" | "riskAdjusted" | "marketSensitivity" | "explorer"
  >("all");
  const [expandedMetric, setExpandedMetric] = useState<string | null>(null);
  const [selectedExplorerMetric, setSelectedExplorerMetric] = useState<string>("RET-02");
  const [explorerSearch, setExplorerSearch] = useState<string>("");
  const [claimContext, setClaimContext] = useState<{
    claimText?: string;
    claimType?: string;
    methodologyStatus?: string;
  } | null>(null);
  const [unavailableClaimNotice, setUnavailableClaimNotice] = useState<string | null>(null);
  const [showProvenance, setShowProvenance] = useState(false);
  const [showQuality, setShowQuality] = useState(false);

  const allMetrics: ProfileMetricItem[] = useMemo(() => [
    ...returnMetrics,
    ...riskMetrics,
    ...riskAdjustedMetrics,
    ...marketSensitivityMetrics,
  ], [returnMetrics, riskMetrics, riskAdjustedMetrics, marketSensitivityMetrics]);

  // Context reset effect: prevents stale evidence, old claim traces, or unavailable warnings from leaking across context changes
  useEffect(() => {
    setExpandedMetric(null);
    setClaimContext(null);
    setUnavailableClaimNotice(null);
    if (allMetrics.length > 0 && !allMetrics.some((m) => m.metricCode === selectedExplorerMetric)) {
      setSelectedExplorerMetric(allMetrics[0].metricCode);
    }
  }, [context.runId, context.horizon, context.schemeOptionId, context.asOfDate]);

  const { watchlistItems, saveResearchNote } = useWatchlist();
  const [investigationTarget, setInvestigationTarget] = useState<{
    fund: WatchlistResponse;
    prefill: {
      investigationQuestion: string;
      reasonSaved: string;
    };
  } | null>(null);
  const [isNoteModalOpen, setIsNoteModalOpen] = useState(false);

  const toggleMetricExpand = (code: string) => {
    setExpandedMetric(expandedMetric === code ? null : code);
  };

  const handleClaimMetricClick = (
    code: string,
    claimCtx?: { claimText?: string; claimType?: string; methodologyStatus?: string }
  ) => {
    const metricItem = allMetrics.find((m) => m.metricCode === code);
    if (metricItem) {
      setUnavailableClaimNotice(null);
      setClaimContext(claimCtx || null);
      setSelectedExplorerMetric(code);
      setExpandedMetric(code);
      setActiveTab("explorer");
      if (typeof document !== "undefined") {
        const el = document.getElementById("evidence-explorer-section");
        if (el) {
          el.scrollIntoView({ behavior: "smooth", block: "start" });
        }
      }
    } else {
      setUnavailableClaimNotice(
        `Claim references metric "${code}", which is not available or not calculated in the current ${context.horizon || "3Y"} analytical profile for ${context.schemeName} (Option #${context.schemeOptionId || 1}, Cutoff: ${context.asOfDate}). Analytical evaluations are strictly bounded to verified observations within the active evaluation window.`
      );
      setClaimContext(claimCtx || null);
      setActiveTab("explorer");
      if (typeof document !== "undefined") {
        const el = document.getElementById("evidence-explorer-section");
        if (el) {
          el.scrollIntoView({ behavior: "smooth", block: "start" });
        }
      }
    }
  };

  const filteredExplorerMetrics = useMemo(() => {
    if (!explorerSearch.trim()) return allMetrics;
    const q = explorerSearch.toLowerCase().trim();
    return allMetrics.filter(
      (m) =>
        m.metricCode.toLowerCase().includes(q) ||
        m.metricName.toLowerCase().includes(q) ||
        (getMetricDisplayName(m.metricCode, m.metricName)).toLowerCase().includes(q)
    );
  }, [allMetrics, explorerSearch]);

  const selectedMetricItem = useMemo(() => {
    return allMetrics.find((m) => m.metricCode === selectedExplorerMetric) || allMetrics[0];
  }, [allMetrics, selectedExplorerMetric]);

  const getMetricDisplayName = (code: string, fallbackName: string) => {
    const h = context.horizon;
    if (h && (h === "5Y" || h === "10Y")) {
      if (code === "RET-03") return `${h === "10Y" ? "10-Year" : "5-Year"} CAGR`;
      if (code === "RET-07") return `${h} Active Return`;
      if (code === "RSK-01") return `Annualized Volatility (${h})`;
      if (code === "RSK-02") return `Downside Semideviation (${h})`;
      if (code === "RSK-03") return `Maximum Drawdown (${h})`;
      if (code === "RSK-04") return `Maximum Drawdown Duration (${h})`;
      if (code === "RSK-05") return `Ulcer Index (${h})`;
      if (code === "RSK-06") return `Historical VaR 95% (${h})`;
      if (code === "RSK-07") return `Expected Shortfall 95% (${h})`;
      if (code === "MKT-01") return `Portfolio Beta (${h})`;
      if (code === "MKT-02") return `Downside Beta (${h})`;
      if (code === "MKT-03") return `Upside Capture Ratio (${h})`;
      if (code === "MKT-04") return `Downside Capture Ratio (${h})`;
      if (code === "MKT-05") return `Capture Spread (${h})`;
      if (code === "REL-01") return `Tracking Error (${h})`;
      if (code === "RAT-01") return `Sharpe Ratio (${h} · Restricted)`;
      if (code === "RAT-02") return `Sortino Ratio (${h} · Restricted)`;
      if (code === "RAT-03") return `Treynor Ratio (${h} · Restricted)`;
      if (code === "RAT-04") return `Information Ratio (${h} · Restricted)`;
      if (code === "REL-02") return `Jensen's Alpha (${h} · Restricted)`;
    }
    return AUTHORITATIVE_METRIC_NAMES[code] || fallbackName;
  };

  const handleOpenInvestigation = (
    inv: { metricCode: string; metricName: string; suggestedQuestion: string; suggestedReason: string }
  ) => {
    const existing = watchlistItems.find(
      (w) =>
        (context.schemeOptionId && w.schemeOptionId === context.schemeOptionId) ||
        (context.schemeId ? w.schemeId === context.schemeId : false)
    );

    const targetFund: WatchlistResponse = {
      id: existing?.id ?? -(context.schemeOptionId || context.schemeId || 1),
      schemeId: context.schemeId || context.schemeOptionId || 1,
      schemeCode: context.amfiCode || `SCHEME_${context.schemeOptionId || 1}`,
      schemeName: context.schemeName,
      amcName: "AMC Verified",
      schemeOptionId: context.schemeOptionId || null,
      optionType: context.optionType || "GROWTH",
      planType: context.planType || "DIRECT",
      amfiCode: context.amfiCode || null,
      isin: context.isin || null,
      latestNav: null,
      latestNavDate: context.asOfDate ? String(context.asOfDate) : null,
      yukiraScore: null,
      yukiraScoreStatus: "CANDIDATE",
      reasonSaved: existing?.reasonSaved || null,
      investigationQuestion: existing?.investigationQuestion || null,
      nextStep: existing?.nextStep || null,
      createdAt: existing?.createdAt || null,
      updatedAt: existing?.updatedAt || null,
    };

    setInvestigationTarget({
      fund: targetFund,
      prefill: {
        investigationQuestion: inv.suggestedQuestion,
        reasonSaved: inv.suggestedReason,
      },
    });
    setIsNoteModalOpen(true);
  };

  const renderMetricCard = (item: ProfileMetricItem) => {
    const isExpanded = expandedMetric === item.metricCode;
    const displayName = getMetricDisplayName(item.metricCode, item.metricName);

    return (
      <div
        key={item.metricCode}
        id={`metric-${item.metricCode}`}
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
          {item.calculationStatus === "INSUFFICIENT_DATA" ? (
            <div>
              <span className="data-unavailable text-amber-400 font-semibold text-[13.5px]">
                Insufficient data
              </span>
              {(item.unavailableReason || item.errorMessage) && (
                <p className="mt-1 text-[11px] font-mono text-text-tertiary leading-[1.4] line-clamp-2" title={item.unavailableReason || item.errorMessage || undefined}>
                  {item.unavailableReason || item.errorMessage}
                </p>
              )}
            </div>
          ) : item.calculationStatus === "FAILED" ? (
            <div>
              <span className="data-unavailable text-rose-400 font-semibold text-[13.5px]">
                Calculation failed
              </span>
              {(item.unavailableReason || item.errorMessage) && (
                <p className="mt-1 text-[11px] font-mono text-text-tertiary leading-[1.4] line-clamp-2" title={item.unavailableReason || item.errorMessage || undefined}>
                  {item.unavailableReason || item.errorMessage}
                </p>
              )}
            </div>
          ) : (
            <div>
              <div className="flex items-baseline justify-between gap-2">
                <div className="flex items-baseline gap-2">
                  <span className="data-value-lg">
                    {item.formattedValue || "Not available"}
                  </span>
                  {item.formattedValue && item.units && <span className="mono-meta">{item.units}</span>}
                </div>
                {item.formattedPercentile && (
                  <span
                    className="inline-flex items-center px-1.5 py-0.5 rounded text-[11px] font-mono font-medium bg-accent/10 text-accent border border-accent/20"
                    title={`Category Peer Percentile: ${item.formattedPercentile}${item.peerCohortInfo ? ` (${item.peerCohortInfo})` : ""}`}
                  >
                    {item.formattedPercentile}
                  </span>
                )}
              </div>
              {item.peerCohortInfo && (
                <p className="mono-meta text-[11px] text-text-tertiary mt-0.5">
                  {item.peerCohortInfo}
                </p>
              )}
            </div>
          )}
        </div>

        <div className="metric-rule my-3" aria-hidden />

        {/* Statistical Interpretation */}
        <p className="mono-meta font-sans leading-[1.55]">{item.interpretation}</p>

        {/* Progressive Disclosure: 5-Layer Evidence & Investigation Action */}
        <div className="mt-auto pt-4">
          <div className="border-t border-border pt-3">
            <div className="flex items-center justify-between gap-2">
              <button
                type="button"
                onClick={() => toggleMetricExpand(item.metricCode)}
                aria-expanded={isExpanded}
                className="flex items-center gap-1.5 text-left text-[12px] font-medium text-accent transition-colors hover:text-accent-hover"
              >
                <span>{isExpanded ? "Hide Inline Evidence" : "Understand this Metric & Evidence"}</span>
                <span
                  className={`tab-ordinal transition-transform ${isExpanded ? "rotate-180" : ""}`}
                  aria-hidden
                >
                  ▼
                </span>
              </button>
              <button
                type="button"
                onClick={() => {
                  setSelectedExplorerMetric(item.metricCode);
                  setActiveTab("explorer");
                  if (typeof document !== "undefined") {
                    const el = document.getElementById("evidence-explorer-section");
                    if (el) {
                      el.scrollIntoView({ behavior: "smooth", block: "start" });
                    }
                  }
                }}
                className="text-[11px] font-mono text-text-tertiary hover:text-accent transition-colors flex items-center gap-1"
                title="Open detailed calculation provenance and observation ledger in Evidence Explorer"
              >
                <span>Explorer ↗</span>
              </button>
            </div>

            {isExpanded && (
              <div className="mt-3">
                <MetricEvidencePanel
                  metric={item}
                  context={{
                    schemeId: context.schemeId || context.schemeOptionId || 1,
                    schemeCode: context.amfiCode || undefined,
                    schemeName: context.schemeName,
                    schemeOptionId: context.schemeOptionId,
                    optionType: context.optionType,
                    planType: context.planType,
                    amfiCode: context.amfiCode,
                    isin: context.isin,
                    benchmarkCode: context.benchmarkCode,
                    benchmarkName: context.benchmarkName,
                    startDate: context.startDate ? String(context.startDate) : undefined,
                    endDate: context.asOfDate ? String(context.asOfDate) : undefined,
                    knowledgeCutoffTime: context.knowledgeCutoffTime ? String(context.knowledgeCutoffTime) : undefined,
                    observationCount: provenance.navObservationCount,
                    calculationRunId: provenance.calculationRunId,
                    methodologyTag: provenance.methodologyTag,
                    engineSoftwareVersion: provenance.engineSoftwareVersion,
                    inputSnapshotSha256: provenance.inputSnapshotSha256,
                    actualStartDate: context.startDate ? String(context.startDate) : undefined,
                    actualEndDate: context.asOfDate ? String(context.asOfDate) : undefined,
                    requestedHorizon: context.horizon,
                    observationThreshold: context.horizon === "10Y" ? 2400 : context.horizon === "5Y" ? 1200 : 700,
                    benchmarkMappingStatus: "CANDIDATE",
                    dataQualityAssessment: quality?.overallAssessment,
                    sampleObservations: provenance?.sampleObservations,
                    claimContext: claimContext || undefined,
                  }}
                  onInvestigate={(inv) => handleOpenInvestigation(inv)}
                  isCollapsible={false}
                  defaultExpanded={true}
                />
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
            <div className="flex items-center gap-2 mt-1">
              <span className="status-badge state-approved font-mono text-[10px]">
                {context.horizon || "3Y"} HORIZON
              </span>
              <p className="mono-meta font-sans">
                {context.horizon ? `${context.horizon} Institutional Risk-Return Profile` : "3-Year Institutional Risk-Return Profile"} &bull;{" "}
                {returnMetrics.length + riskMetrics.length + riskAdjustedMetrics.length + marketSensitivityMetrics.length} Deterministic Financial Metrics
              </p>
            </div>
          </div>

          <div className="flex shrink-0 flex-wrap items-center gap-2">
            {onRerunRequested && (
              <button type="button" onClick={onRerunRequested} className="btn btn-ghost">
                Re-run Profile Analysis
              </button>
            )}
            <Link
              href={`/funds/${context.schemeId || context.schemeOptionId || 1}${context.schemeOptionId ? `?option=${context.schemeOptionId}` : ""}`}
              className="btn btn-secondary flex items-center gap-1.5"
              title="Inspect evidence-led research brief"
            >
              <svg className="h-3.5 w-3.5 text-accent" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
              <span>Research Brief →</span>
            </Link>
            <Link href={`/funds/${context.schemeId || context.schemeOptionId || 1}${context.schemeOptionId ? `?option=${context.schemeOptionId}` : ""}`} className="btn btn-primary">
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
            <p className="def-value text-accent flex items-center gap-1.5 flex-wrap">
              {context.benchmarkCode}
              <span className="status-badge state-candidate text-[9.5px]">Candidate Mapping</span>
            </p>
            <p className="mono-meta text-[10px] mt-0.5">Category reference benchmark mapping</p>
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
              ["all", "All", allMetrics.length],
              ["return", "Return & Benchmark", returnMetrics.length],
              ["risk", "Total & Tail Risk", riskMetrics.length],
              ["riskAdjusted", "Risk-Adjusted", riskAdjustedMetrics.length],
              ["marketSensitivity", "Sensitivity & Alpha", marketSensitivityMetrics.length],
              ["explorer", "Evidence Explorer", allMetrics.length],
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

      {/* 5A. EVIDENCE EXPLORER VIEW */}
      {activeTab === "explorer" && (
        <section id="evidence-explorer-section" className="space-y-4">
          <div className="panel p-4 md:p-5">
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border pb-3">
              <div>
                <div className="flex items-center gap-2">
                  <span className="status-badge state-accent font-mono text-[10px]">
                    DETERMINISTIC PROVENANCE
                  </span>
                  <span className="status-badge state-approved font-mono text-[10px]">
                    {context.horizon} LOOKBACK
                  </span>
                </div>
                <h2 className="mt-1 text-[18px] font-semibold text-text-primary">
                  Evidence Explorer &amp; Calculation Provenance
                </h2>
                <p className="mono-meta mt-0.5 font-sans">
                  Inspect the mathematical derivation, underlying observation ledger, sample records, and 6-D data quality boundaries for every metric.
                </p>
              </div>
              <div className="flex items-center gap-2">
                <span className="mono-meta text-[11px]">
                  Showing {allMetrics.length} metrics &bull; {context.startDate} &rarr; {context.asOfDate}
                </span>
              </div>
            </div>

            {/* Unavailable Metric Warning Notice */}
            {unavailableClaimNotice && (
              <div
                data-testid="unavailable-claim-warning"
                className="mt-4 rounded-sm border border-amber-500/40 bg-amber-500/10 p-3.5 text-text-primary"
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="flex items-start gap-2.5">
                    <span className="text-amber-400 font-bold text-[14px]">⚠️</span>
                    <div>
                      <p className="text-[12.5px] font-semibold text-amber-300">
                        Metric Unavailable in Current Horizon Context
                      </p>
                      <p className="mt-0.5 text-[12px] text-text-secondary leading-[1.5]">
                        {unavailableClaimNotice}
                      </p>
                      <p className="mt-1 text-[11px] font-mono text-text-tertiary">
                        Please select an available metric from the index below or switch horizons to evaluate other historical regimes.
                      </p>
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => setUnavailableClaimNotice(null)}
                    className="text-text-muted hover:text-text-primary text-[12px] font-mono shrink-0 px-2 py-0.5 border border-border rounded"
                  >
                    Dismiss
                  </button>
                </div>
              </div>
            )}

            {/* Master-Detail Layout: Left Catalog (1/3), Right Detailed Evidence Panel (2/3) */}
            <div className="mt-5 grid grid-cols-1 lg:grid-cols-12 gap-5">
              {/* LEFT COLUMN: Metric Index & Search */}
              <div className="lg:col-span-4 space-y-3">
                <div className="flex items-center justify-between gap-2">
                  <span className="mono-meta font-mono uppercase text-[11px] font-semibold text-text-secondary">
                    Metric Index ({filteredExplorerMetrics.length})
                  </span>
                  {explorerSearch && (
                    <button
                      type="button"
                      onClick={() => setExplorerSearch("")}
                      className="text-[11px] font-mono text-accent hover:underline"
                    >
                      Clear Filter
                    </button>
                  )}
                </div>

                <div className="relative">
                  <input
                    type="text"
                    value={explorerSearch}
                    onChange={(e) => setExplorerSearch(e.target.value)}
                    placeholder="Filter by code or name..."
                    className="w-full rounded border border-border bg-surface-inset px-3 py-1.5 text-[12px] font-mono text-text-primary placeholder:text-text-muted focus:border-accent focus:outline-none"
                  />
                  {explorerSearch && (
                    <button
                      type="button"
                      onClick={() => setExplorerSearch("")}
                      className="absolute right-2.5 top-1/2 -translate-y-1/2 text-text-muted hover:text-text-primary text-[12px]"
                    >
                      &times;
                    </button>
                  )}
                </div>

                <div className="max-h-[620px] overflow-y-auto space-y-1.5 pr-1 divide-y divide-border/30">
                  {filteredExplorerMetrics.map((item) => {
                    const isSelected = selectedExplorerMetric === item.metricCode;
                    const displayName = getMetricDisplayName(item.metricCode, item.metricName);
                    const isCalculated = item.calculationStatus === "CALCULATED";
                    const isInsufficient = item.calculationStatus === "INSUFFICIENT_DATA";
                    const isFailed = item.calculationStatus === "FAILED";
                    const isRestricted =
                      item.calculationStatus === "RESTRICTED" ||
                      (item.errorMessage && item.errorMessage.toLowerCase().includes("restricted"));

                    return (
                      <button
                        key={item.metricCode}
                        type="button"
                        onClick={() => {
                          setSelectedExplorerMetric(item.metricCode);
                          setUnavailableClaimNotice(null);
                        }}
                        className={`w-full text-left p-2.5 rounded transition-all flex flex-col gap-1 ${
                          isSelected
                            ? "bg-accent/15 border border-accent shadow-sm"
                            : "hover:bg-surface-raised border border-transparent"
                        }`}
                      >
                        <div className="flex items-center justify-between gap-2">
                          <span className={`font-mono text-[11.5px] font-semibold ${isSelected ? "text-accent" : "text-text-primary"}`}>
                            {item.metricCode}
                          </span>
                          <span className={`text-[10px] font-mono px-1.5 py-0.2 rounded border ${
                            isCalculated
                              ? "bg-emerald-500/10 border-emerald-500/30 text-emerald-400"
                              : isInsufficient
                              ? "bg-amber-500/10 border-amber-500/30 text-amber-400"
                              : isRestricted
                              ? "bg-purple-500/10 border-purple-500/30 text-purple-400"
                              : "bg-rose-500/10 border-rose-500/30 text-rose-400"
                          }`}>
                            {isCalculated
                              ? "CALCULATED"
                              : isInsufficient
                              ? "INSUFFICIENT"
                              : isRestricted
                              ? "RESTRICTED"
                              : isFailed
                              ? "FAILED"
                              : "UNAVAILABLE"}
                          </span>
                        </div>
                        <p className="text-[12px] font-medium text-text-secondary truncate" title={displayName}>
                          {displayName}
                        </p>
                        <div className="flex items-baseline justify-between text-[11px] font-mono text-text-muted mt-0.5">
                          <span className="truncate">
                            {item.formattedValue
                              ? `${item.formattedValue} ${item.units || ""}`
                              : isInsufficient
                              ? "Insufficient data"
                              : "Not available"}
                          </span>
                          <span className="text-[9.5px] uppercase tracking-wider text-text-tertiary shrink-0">
                            {item.governanceStatus || "CANDIDATE"}
                          </span>
                        </div>
                      </button>
                    );
                  })}
                  {filteredExplorerMetrics.length === 0 && (
                    <div className="p-4 text-center font-mono text-[12px] text-text-muted">
                      No metrics matched &quot;{explorerSearch}&quot;
                    </div>
                  )}
                </div>
              </div>

              {/* RIGHT COLUMN: Active Metric Evidence Detail */}
              <div className="lg:col-span-8">
                {selectedMetricItem ? (
                  <MetricEvidencePanel
                    metric={selectedMetricItem}
                    context={{
                      schemeId: context.schemeId || context.schemeOptionId || 1,
                      schemeCode: context.amfiCode || undefined,
                      schemeName: context.schemeName,
                      schemeOptionId: context.schemeOptionId,
                      optionType: context.optionType,
                      planType: context.planType,
                      amfiCode: context.amfiCode,
                      isin: context.isin,
                      benchmarkCode: context.benchmarkCode,
                      benchmarkName: context.benchmarkName,
                      startDate: context.startDate ? String(context.startDate) : undefined,
                      endDate: context.asOfDate ? String(context.asOfDate) : undefined,
                      knowledgeCutoffTime: context.knowledgeCutoffTime ? String(context.knowledgeCutoffTime) : undefined,
                      observationCount: provenance.navObservationCount,
                      calculationRunId: provenance.calculationRunId,
                      methodologyTag: provenance.methodologyTag,
                      engineSoftwareVersion: provenance.engineSoftwareVersion,
                      inputSnapshotSha256: provenance.inputSnapshotSha256,
                      actualStartDate: context.startDate ? String(context.startDate) : undefined,
                      actualEndDate: context.asOfDate ? String(context.asOfDate) : undefined,
                      requestedHorizon: context.horizon,
                      observationThreshold: context.horizon === "10Y" ? 2400 : context.horizon === "5Y" ? 1200 : 700,
                      benchmarkMappingStatus: "CANDIDATE",
                      dataQualityAssessment: quality?.overallAssessment,
                      sampleObservations: provenance?.sampleObservations,
                      claimContext: claimContext || undefined,
                    }}
                    onInvestigate={(inv) => handleOpenInvestigation(inv)}
                    isCollapsible={false}
                    defaultExpanded={true}
                  />
                ) : (
                  <div className="panel p-6 text-center text-text-muted font-mono text-[13px]">
                    Select a metric from the catalog to inspect underlying evidence.
                  </div>
                )}
              </div>
            </div>
          </div>
        </section>
      )}

      {/* 5B. GROUNDED EVIDENCE INTERPRETATION & INVESTIGATION PANEL */}
      <section className="space-y-4">
        <div className="flex flex-wrap items-center gap-2 border-b border-border pb-2">
          <span className="tab-ordinal">05</span>
          <h2 className="text-[14px] font-semibold tracking-[-0.01em] text-text-primary">
            Grounded Evidence Interpretation &amp; Investigation Questions
          </h2>
          <span className="mono-meta">({context.horizon} Horizon Context)</span>
        </div>
        <GroundedAiInterpretationPanel
          profile={data}
          schemeOptionId={context.schemeOptionId}
          runId={context.runId}
          onMetricClick={(code, claimCtx) => handleClaimMetricClick(code, claimCtx)}
          onQuestionInvestigate={(questionText) => {
            setInvestigationTarget({
              fund: {
                id: 0,
                schemeId: context.schemeId || 0,
                schemeOptionId: context.schemeOptionId || 0,
                schemeCode: context.amfiCode || "",
                schemeName: context.schemeName || "",
                amcName: "",
                optionType: context.optionType || "",
                planType: context.planType || "",
                isin: context.isin || "",
                createdAt: new Date().toISOString(),
                updatedAt: new Date().toISOString(),
              },
              prefill: {
                investigationQuestion: questionText,
                reasonSaved: `Grounded AI Investigation Question for ${context.schemeName} (${context.horizon})`,
              },
            });
            setIsNoteModalOpen(true);
          }}
        />
      </section>

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

      {investigationTarget && (
        <ResearchNoteModal
          isOpen={isNoteModalOpen}
          onClose={() => {
            setIsNoteModalOpen(false);
            setInvestigationTarget(null);
          }}
          fund={investigationTarget.fund}
          prefill={investigationTarget.prefill}
          onSave={async (schemeId, note, schemeOptionId) => {
            await saveResearchNote(schemeId, note, schemeOptionId, {
              schemeCode: investigationTarget.fund.schemeCode,
              schemeName: investigationTarget.fund.schemeName,
              amcName: investigationTarget.fund.amcName,
              optionType: investigationTarget.fund.optionType,
              planType: investigationTarget.fund.planType,
            });
            setIsNoteModalOpen(false);
            setInvestigationTarget(null);
          }}
        />
      )}
    </div>
  );
}