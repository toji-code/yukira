"use client";

import React, { useState } from "react";
import { ComparisonCompatibilitySummary, CommonSupportedPeriod, PairwiseCompatibilityDto } from "@/types/comparison";

interface ComparisonCompatibilityBannerProps {
  compatibilitySummary?: ComparisonCompatibilitySummary | null;
  commonSupportedPeriod?: CommonSupportedPeriod | null;
}

export function ComparisonCompatibilityBanner({
  compatibilitySummary,
  commonSupportedPeriod,
}: ComparisonCompatibilityBannerProps) {
  const [showPairwiseDetails, setShowPairwiseDetails] = useState(false);

  if (!compatibilitySummary) {
    return null;
  }

  const { overallStatus, overallReason, pairwiseCompatibilities = [], overallLimitations = [] } = compatibilitySummary;

  const getStatusBadge = (status: string) => {
    switch (status) {
      case "COMPARABLE":
        return {
          label: "COMPARABLE",
          className: "bg-emerald-500/15 border-emerald-500/40 text-emerald-400",
          icon: "✓",
          description: "Consistent categories, aligned periods, appropriate benchmarks, complete data.",
        };
      case "PARTIALLY_COMPARABLE":
        return {
          label: "PARTIALLY COMPARABLE",
          className: "bg-amber-500/15 border-amber-500/40 text-amber-400",
          icon: "⚠️",
          description: "Cross-category mandates, differing inception dates, or disclosure timing differences require context.",
        };
      case "NOT_COMPARABLE":
        return {
          label: "NOT COMPARABLE",
          className: "bg-rose-500/15 border-rose-500/40 text-rose-400",
          icon: "✕",
          description: "Incompatible mandates, non-overlapping periods, or fundamentally divergent benchmarks.",
        };
      case "INSUFFICIENT_EVIDENCE":
      default:
        return {
          label: "INSUFFICIENT EVIDENCE",
          className: "bg-slate-500/15 border-slate-500/40 text-slate-300",
          icon: "ℹ",
          description: "Missing historical observations or holdings snapshots prevent institutional comparison.",
        };
    }
  };

  const statusConfig = getStatusBadge(overallStatus);

  return (
    <section className="panel p-5 bg-surface space-y-4 mb-8 border-l-4 border-l-accent shadow-sm">
      {/* Top Banner Row */}
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-1.5 flex-1 min-w-[280px]">
          <div className="flex items-center gap-2.5">
            <span className="mono-meta text-accent font-bold uppercase tracking-wider text-[11px]">
              Institutional Comparability Assessment
            </span>
            <span
              className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded text-[11px] font-mono font-bold border ${statusConfig.className}`}
              title={statusConfig.description}
            >
              <span>{statusConfig.icon}</span>
              <span>{statusConfig.label}</span>
            </span>
          </div>
          <h3 className="text-h3 text-text-primary">
            Fund Mandate, Horizon & Evidence Compatibility
          </h3>
          <p className="text-sm text-text-secondary leading-relaxed max-w-[85ch]">
            {overallReason}
          </p>
        </div>

        {/* Common Supported Period Badge */}
        {commonSupportedPeriod && (
          <div className="bg-surface-raised border border-border rounded-lg p-3 shrink-0 text-right space-y-1 min-w-[220px]">
            <div className="text-[10px] font-mono uppercase tracking-wider text-text-muted">
              Common Supported Horizon
            </div>
            <div className="font-mono text-xs text-text-primary font-bold">
              {commonSupportedPeriod.commonStartDate} → {commonSupportedPeriod.commonEndDate}
            </div>
            <div className="flex items-center justify-end gap-1.5 text-[11px] font-mono text-text-secondary">
              <span>{commonSupportedPeriod.commonObservationCount ?? 750} observations</span>
              <span
                className={`px-1.5 py-0.2 rounded text-[10px] border ${
                  commonSupportedPeriod.isAligned
                    ? "bg-emerald-500/10 border-emerald-500/30 text-emerald-400"
                    : "bg-amber-500/10 border-amber-500/30 text-amber-400"
                }`}
              >
                {commonSupportedPeriod.isAligned ? "Aligned Window" : "Truncated Bound"}
              </span>
            </div>
            {commonSupportedPeriod.limitationNotice && (
              <p className="text-[10.5px] font-mono text-amber-400/90 text-left pt-1 border-t border-border mt-1 leading-tight">
                {commonSupportedPeriod.limitationNotice}
              </p>
            )}
          </div>
        )}
      </div>

      {/* Epistemic Limitations & Disclaimers */}
      {overallLimitations.length > 0 && (
        <div className="bg-surface-raised/60 border border-border/80 rounded-md p-3 text-xs space-y-1.5">
          <div className="font-mono text-[10px] uppercase tracking-wider text-text-muted flex items-center gap-1.5">
            <span>🛡️</span>
            <span>Allocators Due Diligence Guardrails</span>
          </div>
          <ul className="grid grid-cols-1 md:grid-cols-2 gap-x-6 gap-y-1 text-text-secondary list-disc pl-4 text-[11.5px] leading-relaxed">
            {overallLimitations.map((lim, idx) => (
              <li key={idx}>{lim}</li>
            ))}
          </ul>
        </div>
      )}

      {/* Pairwise Details Toggle */}
      {pairwiseCompatibilities.length > 0 && (
        <div className="pt-1">
          <button
            type="button"
            onClick={() => setShowPairwiseDetails(!showPairwiseDetails)}
            className="flex items-center gap-1.5 text-xs font-mono text-accent hover:underline font-semibold"
          >
            <span>{showPairwiseDetails ? "Hide" : "Inspect"} Pairwise Compatibility Dimensions ({pairwiseCompatibilities.length} pairs)</span>
            <span>{showPairwiseDetails ? "▲" : "▼"}</span>
          </button>

          {showPairwiseDetails && (
            <div className="mt-3 space-y-3">
              {pairwiseCompatibilities.map((pair, idx) => (
                <div
                  key={idx}
                  className="p-3.5 bg-surface-raised border border-border rounded-lg space-y-2 text-xs font-mono"
                >
                  <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border pb-2">
                    <div className="font-semibold text-text-primary text-[13px]">
                      {pair.fundAName} <span className="text-text-muted">vs</span> {pair.fundBName}
                    </div>
                    <span
                      className={`px-2 py-0.5 rounded text-[10px] font-bold border ${getStatusBadge(pair.pairwiseStatus).className}`}
                    >
                      {pair.pairwiseStatus}
                    </span>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2.5 text-[11px] pt-1">
                    <div className="bg-surface p-2 rounded border border-border/60">
                      <span className="text-text-muted block text-[10px] uppercase">Category</span>
                      <span className="font-semibold text-text-primary">{pair.categoryCompatibility.replace(/_/g, " ")}</span>
                      <span className="text-[10px] text-text-muted block mt-0.5">
                        {pair.categoryA} vs {pair.categoryB}
                      </span>
                    </div>

                    <div className="bg-surface p-2 rounded border border-border/60">
                      <span className="text-text-muted block text-[10px] uppercase">Benchmark</span>
                      <span className="font-semibold text-text-primary">{pair.benchmarkCompatibility.replace(/_/g, " ")}</span>
                      <span className="text-[10px] text-text-muted block mt-0.5">
                        {pair.benchmarkA} vs {pair.benchmarkB}
                      </span>
                    </div>

                    <div className="bg-surface p-2 rounded border border-border/60">
                      <span className="text-text-muted block text-[10px] uppercase">Observation Window</span>
                      <span className="font-semibold text-text-primary">{pair.observationPeriodCompatibility.replace(/_/g, " ")}</span>
                    </div>

                    <div className="bg-surface p-2 rounded border border-border/60">
                      <span className="text-text-muted block text-[10px] uppercase">Data Freshness</span>
                      <span className="font-semibold text-text-primary">{pair.freshnessCompatibility.replace(/_/g, " ")}</span>
                    </div>

                    <div className="bg-surface p-2 rounded border border-border/60">
                      <span className="text-text-muted block text-[10px] uppercase">Holdings Disclosures</span>
                      <span className="font-semibold text-text-primary">{pair.holdingsCompatibility.replace(/_/g, " ")}</span>
                      {(pair.holdingsDateA || pair.holdingsDateB) && (
                        <span className="text-[10px] text-text-muted block mt-0.5">
                          {pair.holdingsDateA || "N/A"} vs {pair.holdingsDateB || "N/A"}
                        </span>
                      )}
                    </div>

                    <div className="bg-surface p-2 rounded border border-border/60">
                      <span className="text-text-muted block text-[10px] uppercase">Methodology & Metrics</span>
                      <span className="font-semibold text-text-primary">{pair.methodologyCompatibility.replace(/_/g, " ")}</span>
                      <span className="text-[10px] text-text-muted block mt-0.5">
                        {pair.metricAvailabilityCompatibility.replace(/_/g, " ")}
                      </span>
                    </div>
                  </div>

                  {pair.pairwiseLimitations.length > 0 && (
                    <div className="pt-1.5 border-t border-border/60">
                      <span className="text-[10px] font-bold text-amber-400 block mb-0.5">Pair Limitations:</span>
                      <ul className="list-disc pl-4 space-y-0.5 text-[10.5px] text-text-secondary">
                        {pair.pairwiseLimitations.map((l, lIdx) => (
                          <li key={lIdx}>{l}</li>
                        ))}
                      </ul>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </section>
  );
}

