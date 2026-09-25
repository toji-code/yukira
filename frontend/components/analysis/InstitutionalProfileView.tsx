"use client";

import React, { useState } from "react";
import Link from "next/link";
import { AnalyticalProfileResponse, ProfileMetricItem } from "@/types/analysis";
import { formatDateTime } from "@/lib/utils/formatters";

interface InstitutionalProfileViewProps {
  data: AnalyticalProfileResponse;
  onRerunRequested?: () => void;
}

export function InstitutionalProfileView({ data, onRerunRequested }: InstitutionalProfileViewProps) {
  const { context, returnMetrics, riskMetrics, riskAdjustedMetrics, marketSensitivityMetrics, provenance, quality, limitations } = data;
  const [activeTab, setActiveTab] = useState<"all" | "return" | "risk" | "riskAdjusted" | "marketSensitivity">("all");
  const [expandedMetric, setExpandedMetric] = useState<string | null>(null);
  const [showProvenance, setShowProvenance] = useState(false);
  const [showQuality, setShowQuality] = useState(false);

  const toggleMetricExpand = (code: string) => {
    setExpandedMetric(expandedMetric === code ? null : code);
  };

  const getBadgeStyle = (status: string) => {
    if (status === "APPROVED") {
      return "bg-emerald-500/15 text-emerald-400 border-emerald-500/30";
    }
    if (status.includes("OPERATIONAL")) {
      return "bg-cyan-500/15 text-cyan-400 border-cyan-500/30";
    }
    return "bg-amber-500/15 text-amber-400 border-amber-500/30";
  };

  const renderMetricCard = (item: ProfileMetricItem) => {
    const isExpanded = expandedMetric === item.metricCode;
    const isCandidate = item.governanceStatus === "CANDIDATE";
    const badgeStyle = getBadgeStyle(item.governanceStatus);

    return (
      <div
        key={item.metricCode}
        className={`rounded-xl border transition-all duration-200 ${
          isCandidate
            ? "border-zinc-800 bg-zinc-900/60 hover:border-zinc-700"
            : "border-zinc-800 bg-zinc-900/90 hover:border-emerald-500/40"
        } p-5 space-y-3`}
      >
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <span className="font-mono text-sm font-bold text-zinc-100">{item.metricCode}</span>
              <span className={`inline-flex items-center rounded px-2 py-0.5 text-[10px] font-mono font-semibold border ${badgeStyle}`}>
                {item.governanceStatus}
              </span>
              <span className="font-mono text-[10px] text-zinc-500">{item.periodType}</span>
            </div>
            <h4 className="text-xs font-medium text-zinc-300">{item.metricName}</h4>
          </div>

          <div className="text-right">
            <div className="font-mono text-lg font-bold text-white tracking-tight">
              {item.formattedValue || "—"}
            </div>
            <div className="font-mono text-[10px] text-zinc-500 uppercase">{item.units}</div>
          </div>
        </div>

        {/* Statistical Interpretation */}
        <p className="text-xs text-zinc-400 leading-relaxed font-sans">
          {item.interpretation}
        </p>

        {/* Candidate Warning Banner if candidate */}
        {isCandidate && (
          <div className="rounded border border-amber-500/20 bg-amber-500/5 px-2.5 py-1.5 text-[11px] font-mono text-amber-300/90">
            <strong className="text-amber-400">Governance Notice: </strong>
            Candidate specification &bull; Not validated for live production allocation.
          </div>
        )}

        {/* Progressive Disclosure: Formula, Diagnostics, Limitations */}
        <div className="pt-2 border-t border-zinc-800/80">
          <button
            onClick={() => toggleMetricExpand(item.metricCode)}
            className="flex items-center justify-between w-full text-[11px] font-mono text-zinc-400 hover:text-zinc-200 transition"
          >
            <span>{isExpanded ? "Hide Mathematical Details" : "View Formula & Limitations"}</span>
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
            <div className="mt-3 space-y-2 text-xs font-mono bg-zinc-950/70 rounded-lg p-3 border border-zinc-800 animate-in fade-in duration-150">
              <div>
                <span className="text-zinc-500 text-[10px] uppercase tracking-wider block mb-0.5">Formula:</span>
                <code className="text-cyan-300 text-[11px] block overflow-x-auto p-1 bg-zinc-900 rounded">
                  {item.formulaDisclosure}
                </code>
              </div>

              <div>
                <span className="text-zinc-500 text-[10px] uppercase tracking-wider block mb-0.5">Regime Limitations:</span>
                <p className="text-zinc-400 text-[11px] leading-relaxed font-sans">{item.limitations}</p>
              </div>

              {item.errorMessage && (
                <div className="text-rose-400 text-[11px]">
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
      <div className="rounded-2xl border border-zinc-800 bg-zinc-900/80 p-6 backdrop-blur-md shadow-xl space-y-5">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6 pb-6 border-b border-zinc-800">
          <div className="space-y-2">
            <div className="flex flex-wrap items-center gap-2 font-mono text-xs">
              <span className="rounded bg-zinc-800 px-2.5 py-1 text-zinc-300 font-semibold">
                Run #{context.runId}
              </span>
              <span className="rounded bg-emerald-500/15 border border-emerald-500/30 px-2.5 py-1 text-emerald-400 font-bold uppercase">
                {context.runStatus}
              </span>
              <span className="text-zinc-500">&bull;</span>
              <span className="text-zinc-400">AMFI: {context.amfiCode}</span>
              <span className="text-zinc-500">&bull;</span>
              <span className="text-zinc-400">ISIN: {context.isin}</span>
            </div>

            <h1 className="text-xl lg:text-2xl font-bold tracking-tight text-white">
              {context.schemeName}
            </h1>
            <p className="text-xs text-zinc-400 font-mono">
              3-Year Institutional Risk-Return Profile &bull; 16 Deterministic Financial Metrics
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            {onRerunRequested && (
              <button
                onClick={onRerunRequested}
                className="rounded-lg border border-zinc-700 bg-zinc-800 px-3.5 py-2 text-xs font-mono font-medium text-zinc-200 hover:bg-zinc-700 transition"
              >
                Re-run Profile Analysis
              </button>
            )}
            <Link
              href={`/funds/${context.schemeOptionId || 1}`}
              className="rounded-lg bg-cyan-600 px-3.5 py-2 text-xs font-mono font-semibold text-white hover:bg-cyan-500 transition shadow-lg shadow-cyan-900/30"
            >
              Fund Detail
            </Link>
          </div>
        </div>

        {/* Context metadata strip */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-xs font-mono">
          <div className="space-y-1">
            <span className="text-zinc-500 text-[10px] uppercase tracking-wider block">Evaluation Window</span>
            <span className="text-zinc-200 font-semibold">{context.startDate} &rarr; {context.asOfDate}</span>
          </div>

          <div className="space-y-1">
            <span className="text-zinc-500 text-[10px] uppercase tracking-wider block">Benchmark</span>
            <span className="text-cyan-400 font-semibold">{context.benchmarkCode}</span>
          </div>

          <div className="space-y-1">
            <span className="text-zinc-500 text-[10px] uppercase tracking-wider block">Risk-Free Proxy</span>
            <span className="text-zinc-200 font-semibold">{context.riskFreeProxy}</span>
          </div>

          <div className="space-y-1">
            <span className="text-zinc-500 text-[10px] uppercase tracking-wider block">Knowledge Cutoff</span>
            <span className="text-amber-400 font-semibold truncate block" title={context.knowledgeCutoffTime}>
              {context.knowledgeCutoffTime.replace("T", " ")}
            </span>
          </div>
        </div>
      </div>

      {/* 2. TAB NAVIGATION */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-zinc-800 pb-3">
        <div className="flex flex-wrap gap-1 font-mono text-xs">
          <button
            onClick={() => setActiveTab("all")}
            className={`rounded-lg px-3 py-1.5 transition ${
              activeTab === "all" ? "bg-zinc-800 text-white font-semibold" : "text-zinc-400 hover:text-zinc-200"
            }`}
          >
            All 16 Metrics
          </button>
          <button
            onClick={() => setActiveTab("return")}
            className={`rounded-lg px-3 py-1.5 transition ${
              activeTab === "return" ? "bg-zinc-800 text-white font-semibold" : "text-zinc-400 hover:text-zinc-200"
            }`}
          >
            Return & Benchmark ({returnMetrics.length})
          </button>
          <button
            onClick={() => setActiveTab("risk")}
            className={`rounded-lg px-3 py-1.5 transition ${
              activeTab === "risk" ? "bg-zinc-800 text-white font-semibold" : "text-zinc-400 hover:text-zinc-200"
            }`}
          >
            Total & Tail Risk ({riskMetrics.length})
          </button>
          <button
            onClick={() => setActiveTab("riskAdjusted")}
            className={`rounded-lg px-3 py-1.5 transition ${
              activeTab === "riskAdjusted" ? "bg-zinc-800 text-white font-semibold" : "text-zinc-400 hover:text-zinc-200"
            }`}
          >
            Risk-Adjusted ({riskAdjustedMetrics.length})
          </button>
          <button
            onClick={() => setActiveTab("marketSensitivity")}
            className={`rounded-lg px-3 py-1.5 transition ${
              activeTab === "marketSensitivity" ? "bg-zinc-800 text-white font-semibold" : "text-zinc-400 hover:text-zinc-200"
            }`}
          >
            Sensitivity & Alpha ({marketSensitivityMetrics.length})
          </button>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => setShowProvenance(!showProvenance)}
            className="text-[11px] font-mono text-zinc-400 hover:text-cyan-400 transition"
          >
            {showProvenance ? "Hide Provenance" : "Audit Provenance"}
          </button>
          <span className="text-zinc-600">&bull;</span>
          <button
            onClick={() => setShowQuality(!showQuality)}
            className="text-[11px] font-mono text-zinc-400 hover:text-cyan-400 transition"
          >
            {showQuality ? "Hide Quality" : "Data Quality Taxonomy"}
          </button>
        </div>
      </div>

      {/* 3. PROVENANCE CARD (Collapsible) */}
      {showProvenance && (
        <div className="rounded-xl border border-zinc-800 bg-zinc-950 p-5 font-mono text-xs space-y-4 animate-in fade-in duration-150">
          <div className="flex items-center justify-between border-b border-zinc-800 pb-3">
            <h3 className="font-bold text-white uppercase tracking-wider text-xs">
              Cryptographic Audit Provenance
            </h3>
            <span className="text-zinc-500 text-[10px]">Deterministic Audit Lineage</span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <span className="text-zinc-500 text-[10px] uppercase block mb-1">Input Snapshot SHA-256</span>
              <code className="text-[11px] text-cyan-300 bg-zinc-900 p-2 rounded block break-all select-all border border-zinc-800">
                {provenance.inputSnapshotSha256}
              </code>
            </div>

            <div className="space-y-1">
              <span className="text-zinc-500 text-[10px] uppercase block mb-1">Execution Metrics</span>
              <div className="text-zinc-300 text-[11px]">Engine Version: {provenance.engineSoftwareVersion}</div>
              <div className="text-zinc-300 text-[11px]">Methodology Tag: {provenance.methodologyTag}</div>
              <div className="text-zinc-300 text-[11px]">Execution Started: {formatDateTime(provenance.executionStartedAt)}</div>
              <div className="text-zinc-300 text-[11px]">Execution Completed: {provenance.executionCompletedAt ? formatDateTime(provenance.executionCompletedAt) : "—"}</div>
            </div>
          </div>

          <div className="grid grid-cols-3 gap-4 pt-2 border-t border-zinc-800/80">
            <div>
              <span className="text-zinc-500 text-[10px] uppercase block">NAV Observations</span>
              <span className="text-emerald-400 font-bold text-sm">{provenance.navObservationCount}</span>
            </div>
            <div>
              <span className="text-zinc-500 text-[10px] uppercase block">Benchmark Observations</span>
              <span className="text-cyan-400 font-bold text-sm">{provenance.benchmarkObservationCount}</span>
            </div>
            <div>
              <span className="text-zinc-500 text-[10px] uppercase block">Risk-Free Observations</span>
              <span className="text-amber-400 font-bold text-sm">{provenance.riskFreeObservationCount}</span>
            </div>
          </div>
        </div>
      )}

      {/* 4. DATA QUALITY TAXONOMY (Collapsible) */}
      {showQuality && (
        <div className="rounded-xl border border-zinc-800 bg-zinc-950 p-5 font-mono text-xs space-y-4 animate-in fade-in duration-150">
          <div className="flex items-center justify-between border-b border-zinc-800 pb-3">
            <h3 className="font-bold text-white uppercase tracking-wider text-xs">
              Six-Dimensional Data Quality Taxonomy
            </h3>
            <span className="text-emerald-400 font-bold">{quality.overallAssessment}</span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {quality.dimensions.map((dim, idx) => (
              <div key={idx} className="rounded-lg border border-zinc-800 bg-zinc-900/60 p-3 space-y-1">
                <div className="flex items-center justify-between text-[11px]">
                  <span className="text-zinc-400">{dim.dimension}</span>
                  <span className="rounded bg-zinc-800 px-1.5 py-0.5 text-[10px] font-bold text-zinc-200">
                    {dim.state}
                  </span>
                </div>
                <p className="text-[10px] text-zinc-500 leading-relaxed font-sans">{dim.description}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* 5. METRIC SECTIONS */}
      {(activeTab === "all" || activeTab === "return") && (
        <section className="space-y-4">
          <div className="flex items-center gap-2 border-b border-zinc-800/80 pb-2">
            <span className="h-2 w-2 rounded-full bg-cyan-400" />
            <h2 className="text-sm font-bold uppercase tracking-wider text-zinc-200 font-mono">
              1. Return & Benchmark Dynamics
            </h2>
            <span className="text-xs text-zinc-500 font-mono">({returnMetrics.length} metrics)</span>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {returnMetrics.map(renderMetricCard)}
          </div>
        </section>
      )}

      {(activeTab === "all" || activeTab === "risk") && (
        <section className="space-y-4">
          <div className="flex items-center gap-2 border-b border-zinc-800/80 pb-2">
            <span className="h-2 w-2 rounded-full bg-amber-400" />
            <h2 className="text-sm font-bold uppercase tracking-wider text-zinc-200 font-mono">
              2. Total & Tail Risk Dynamics
            </h2>
            <span className="text-xs text-zinc-500 font-mono">({riskMetrics.length} metrics)</span>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {riskMetrics.map(renderMetricCard)}
          </div>
        </section>
      )}

      {(activeTab === "all" || activeTab === "riskAdjusted") && (
        <section className="space-y-4">
          <div className="flex items-center gap-2 border-b border-zinc-800/80 pb-2">
            <span className="h-2 w-2 rounded-full bg-emerald-400" />
            <h2 className="text-sm font-bold uppercase tracking-wider text-zinc-200 font-mono">
              3. Risk-Adjusted Performance
            </h2>
            <span className="text-xs text-zinc-500 font-mono">({riskAdjustedMetrics.length} metrics)</span>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {riskAdjustedMetrics.map(renderMetricCard)}
          </div>
        </section>
      )}

      {(activeTab === "all" || activeTab === "marketSensitivity") && (
        <section className="space-y-4">
          <div className="flex items-center gap-2 border-b border-zinc-800/80 pb-2">
            <span className="h-2 w-2 rounded-full bg-indigo-400" />
            <h2 className="text-sm font-bold uppercase tracking-wider text-zinc-200 font-mono">
              4. Market Sensitivity & Alpha Dynamics
            </h2>
            <span className="text-xs text-zinc-500 font-mono">({marketSensitivityMetrics.length} metrics)</span>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {marketSensitivityMetrics.map(renderMetricCard)}
          </div>
        </section>
      )}

      {/* 6. EPISTEMIC DISCLAIMER FOOTER */}
      <div className="rounded-xl border border-zinc-800 bg-zinc-950 p-4 font-mono text-[11px] text-zinc-400 space-y-2">
        <div className="flex items-center gap-2 text-amber-400 font-bold uppercase">
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
          Institutional Epistemic Guardrail
        </div>
        <p className="text-zinc-400 leading-relaxed font-sans">
          {limitations.candidateMethodologyDisclaimer}
        </p>
        <p className="text-zinc-500 text-[10px]">
          Point-in-Time Cutoff: {limitations.knowledgeCutoff} &bull; Analysis Cutoff: {limitations.analysisCutoff} &bull; Zero generative financial forecasts &bull; Evidence Over Opinion.
        </p>
      </div>
    </div>
  );
}
