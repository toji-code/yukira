"use client";

import React, { useState, useEffect } from "react";
import { ComparisonFund } from "@/types/comparison";
import {
  fetchPortfolioDrift,
  PortfolioDriftResponseDto,
} from "@/lib/api/portfolio";
import { SectionHeading } from "@/components/layout/PageContainer";

interface WithinFundDriftComparisonProps {
  funds: ComparisonFund[];
  knowledgeCutoff?: string;
  ordinal?: string;
}

export function WithinFundDriftComparison({
  funds,
  knowledgeCutoff,
  ordinal = "07. Portfolio Drift",
}: WithinFundDriftComparisonProps) {
  const [driftMap, setDriftMap] = useState<Map<number, { loading: boolean; data: PortfolioDriftResponseDto | null; error: string | null }>>(new Map());
  const [selectedFundIdState, setSelectedFundId] = useState<number | null>(null);

  const selectedFundId =
    selectedFundIdState !== null && funds.some((f) => f.schemeOptionId === selectedFundIdState)
      ? selectedFundIdState
      : funds[0]?.schemeOptionId ?? null;

  useEffect(() => {
    if (!funds || funds.length === 0) return;

    funds.forEach((fund) => {
      fetchPortfolioDrift(fund.schemeOptionId, undefined, undefined, knowledgeCutoff)
        .then((data) => {
          setDriftMap((prev) =>
            new Map(prev).set(fund.schemeOptionId, { loading: false, data, error: null })
          );
        })
        .catch((err: unknown) => {
          setDriftMap((prev) =>
            new Map(prev).set(fund.schemeOptionId, {
              loading: false,
              data: null,
              error: err instanceof Error ? err.message : "Drift fetch failed",
            })
          );
        });
    });
  }, [funds, knowledgeCutoff]);

  if (!funds || funds.length === 0) return null;

  return (
    <section className="space-y-4">
      <SectionHeading
        ordinal={ordinal}
        title="Within-Fund Disclosed Portfolio Drift"
        description="Tracks longitudinal portfolio changes for each fund across reporting periods. Clearly distinguished from cross-sectional between-fund overlap."
      />

      {/* Epistemic Demarcation Box */}
      <div className="bg-surface-raised p-4 rounded border border-border text-[12.5px] space-y-2">
        <div className="flex items-center gap-2">
          <span className="mono-meta text-accent font-semibold uppercase">EPISTEMIC BOUNDARY: OVERLAP VS. DRIFT</span>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3 pt-1 text-text-secondary leading-relaxed">
          <div>
            <strong className="text-text-primary">Between-Fund Overlap (Section 04):</strong> Evaluates cross-sectional commonality between two distinct funds at a single point in time. Note: high overlap does not automatically imply inadequate portfolio diversification across all investor goals.
          </div>
          <div>
            <strong className="text-text-primary">Within-Fund Drift (Section 05):</strong> Evaluates how an individual fund&apos;s own disclosed positions and sector weights evolved between consecutive monthly reports. Disclosed shifts do not establish whether changes resulted from trades, price changes, or corporate actions.
          </div>
        </div>
      </div>

      {/* Fund Selector Tabs for Multi-Fund Comparison */}
      <div className="flex flex-wrap items-center gap-2 border-b border-border pb-3">
        {funds.map((fund) => {
          const state = driftMap.get(fund.schemeOptionId);
          const isSelected = selectedFundId === fund.schemeOptionId;
          const status = state?.data?.status;

          return (
            <button
              key={fund.schemeOptionId}
              onClick={() => setSelectedFundId(fund.schemeOptionId)}
              className={`px-3 py-1.5 rounded text-[12.5px] font-medium transition-colors flex items-center gap-2 ${
                isSelected
                  ? "bg-accent text-accent-foreground font-bold shadow-xs"
                  : "bg-surface hover:bg-surface-raised text-text-secondary border border-border"
              }`}
            >
              <span>{fund.schemeName}</span>
              {status && (
                <span className={`text-[10px] px-1.5 py-0.2 rounded font-mono ${
                  status === "COMPARABLE" ? "bg-approved-bg text-approved-fg" : "bg-surface-inset text-text-tertiary"
                }`}>
                  {status === "COMPARABLE" ? "DRIFT AVAILABLE" : "1 SNAPSHOT"}
                </span>
              )}
            </button>
          );
        })}
      </div>

      {/* Selected Fund Drift Card */}
      {selectedFundId && (() => {
        const state = driftMap.get(selectedFundId);
        const fund = funds.find((f) => f.schemeOptionId === selectedFundId);
        if (!fund) return null;

        if (!state || state.loading) {
          return (
            <div className="panel p-5 space-y-3" aria-busy="true">
              <div className="skeleton h-4 w-1/4" />
              <div className="skeleton h-24 w-full" />
            </div>
          );
        }

        if (state.error || !state.data) {
          return (
            <div className="panel p-4 border-l-2 border-l-critical-border bg-critical-bg/20 text-[13px] text-critical-fg">
              Failed to evaluate portfolio drift for {fund.schemeName}: {state.error || "Unavailable"}
            </div>
          );
        }

        const data = state.data;

        if (data.status === "INSUFFICIENT_HISTORY" || !data.isComparable) {
          return (
            <div className="panel p-5 border border-border bg-surface-raised space-y-3 text-[13px]">
              <div className="flex items-center justify-between border-b border-border/60 pb-2">
                <span className="font-semibold text-text-primary">{fund.schemeName}</span>
                <span className="status-badge state-neutral">INSUFFICIENT HISTORY</span>
              </div>
              <p className="text-text-secondary leading-relaxed">
                {data.statusReason || "Only 1 historical holdings disclosure is registered in YUKIRA for this scheme option. Multi-period drift analysis requires at least 2 distinct monthly snapshots."}
              </p>
              <p className="text-[11.5px] text-text-tertiary font-mono">
                YUKIRA does not fabricate prior portfolio observations or render misleading zero changes when history is unavailable.
              </p>
            </div>
          );
        }

        const earlierSnap = data.earlierSnapshot;
        const laterSnap = data.laterSnapshot;
        const topIncreases = data.topIncreases || [];
        const topDecreases = data.topDecreases || [];
        const sectorDrift = data.sectorDrift || [];

        return (
          <div className="panel p-5 space-y-5 bg-surface border border-border">
            {/* Header with Comparison Dates */}
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border pb-3">
              <div>
                <h4 className="font-semibold text-[15px] text-text-primary">
                  {fund.schemeName}
                </h4>
                <p className="text-[12px] font-mono text-text-secondary mt-0.5">
                  Comparison Period: <strong className="text-text-primary">{earlierSnap?.portfolioDate}</strong> → <strong className="text-text-primary">{laterSnap?.portfolioDate}</strong>
                </p>
              </div>
              <div className="flex items-center gap-2">
                <span className="status-badge state-approved">
                  ✓ Reconciled ({data.classificationSummary?.totalComparedPositions || 0} positions)
                </span>
                <span className="mono-meta text-[11px]">
                  Top-10 Delta: {data.top10PctDelta > 0 ? `+${data.top10PctDelta.toFixed(2)}` : data.top10PctDelta.toFixed(2)} pp
                </span>
              </div>
            </div>

            {/* Drift Summary Grid */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {/* Largest Position Increases */}
              <div className="bg-surface-raised p-3.5 rounded border border-border space-y-2.5">
                <span className="text-[11px] font-semibold uppercase tracking-wider text-text-secondary block border-b border-border/60 pb-1.5">
                  Largest Disclosed Position Increases
                </span>
                {topIncreases.length === 0 ? (
                  <p className="text-[12px] text-text-tertiary">No position increases observed.</p>
                ) : (
                  <ul className="space-y-1.5 text-[12px]">
                    {topIncreases.slice(0, 4).map((item, idx) => (
                      <li key={idx} className="flex items-center justify-between">
                        <div className="min-w-0 pr-2">
                          <span className="font-medium text-text-primary block truncate">{item.securityName}</span>
                          <span className="text-[10.5px] text-text-tertiary font-mono">{item.changeType.replace(/_/g, " ")} • {item.earlierWeightPctFormatted || "0%"} → {item.laterWeightPctFormatted}</span>
                        </div>
                        <span className="font-mono text-approved-fg font-bold shrink-0">
                          +{item.weightDeltaPct.toFixed(2)} pp
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>

              {/* Largest Position Reductions */}
              <div className="bg-surface-raised p-3.5 rounded border border-border space-y-2.5">
                <span className="text-[11px] font-semibold uppercase tracking-wider text-text-secondary block border-b border-border/60 pb-1.5">
                  Largest Disclosed Position Reductions &amp; Exits
                </span>
                {topDecreases.length === 0 ? (
                  <p className="text-[12px] text-text-tertiary">No position reductions observed.</p>
                ) : (
                  <ul className="space-y-1.5 text-[12px]">
                    {topDecreases.slice(0, 4).map((item, idx) => (
                      <li key={idx} className="flex items-center justify-between">
                        <div className="min-w-0 pr-2">
                          <span className="font-medium text-text-primary block truncate">{item.securityName}</span>
                          <span className="text-[10.5px] text-text-tertiary font-mono">{item.changeType.replace(/_/g, " ")} • {item.earlierWeightPctFormatted} → {item.laterWeightPctFormatted || "0%"}</span>
                        </div>
                        <span className="font-mono text-critical-fg font-bold shrink-0">
                          {item.weightDeltaPct.toFixed(2)} pp
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </div>

            {/* Sector Shifts Summary */}
            {sectorDrift.length > 0 && (
              <div className="bg-surface-raised p-3.5 rounded border border-border space-y-2.5">
                <div className="flex items-center justify-between border-b border-border/60 pb-1.5">
                  <span className="text-[11px] font-semibold uppercase tracking-wider text-text-secondary">
                    Key Sector Reallocations (in Percentage Points)
                  </span>
                  <span className="text-[10.5px] text-text-tertiary font-mono">
                    {data.sectorCoverage?.coverageStatus || "ADEQUATE"}
                  </span>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 text-[12px]">
                  {sectorDrift.slice(0, 6).map((sec, idx) => (
                    <div key={idx} className="bg-surface-inset p-2 rounded flex items-center justify-between">
                      <span className="font-medium text-text-primary truncate pr-1">{sec.sector}</span>
                      <span className={`font-mono font-bold shrink-0 ${
                        sec.weightDeltaPct > 0 ? "text-approved-fg" : sec.weightDeltaPct < 0 ? "text-critical-fg" : "text-text-tertiary"
                      }`}>
                        {sec.weightDeltaPctFormatted} pp
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        );
      })()}
    </section>
  );
}
