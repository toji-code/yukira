"use client";

import React, { useState, useEffect } from "react";
import { fetchRollingConsistency } from "@/lib/api/analysis";
import { RollingConsistencyResponse, RollingHorizonResult } from "@/types/analysis";

interface RollingConsistencyViewProps {
  schemeOptionId: number;
  schemeCode: string;
}

export function RollingConsistencyView({ schemeOptionId }: RollingConsistencyViewProps) {
  const [data, setData] = useState<RollingConsistencyResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [activeHorizon, setActiveHorizon] = useState<"3Y" | "1Y">("3Y");
  const [showAuditLineage, setShowAuditLineage] = useState<boolean>(false);

  useEffect(() => {
    let active = true;

    const asOfDate = "2024-01-15";
    const cutoffTime = "2024-01-31T23:59:59+05:30";

    fetchRollingConsistency(schemeOptionId, asOfDate, cutoffTime)
      .then((res) => {
        if (active) {
          setData(res);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (active) {
          setError(err instanceof Error ? err.message : "Failed to load rolling consistency analysis.");
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [schemeOptionId]);

  if (loading) {
    return (
      <div className="panel space-y-4 p-4 md:p-5" aria-busy="true">
        <div className="skeleton h-3 w-1/3" />
        <div className="skeleton h-10 w-full" />
        <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
          <div className="skeleton h-20" />
          <div className="skeleton h-20" />
          <div className="skeleton h-20" />
          <div className="skeleton h-20" />
        </div>
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="state-panel-error" role="alert">
        <p className="text-[15px] font-semibold tracking-[-0.01em] text-text-primary">
          Rolling Return Consistency Data Unavailable
        </p>
        <p className="mt-1 text-[13px] leading-[1.5] text-critical-fg">
          {error ||
            "Unable to compute rolling consistency metrics from verified ledger observations."}
        </p>
      </div>
    );
  }

  const { context, primary3Y, supporting1Y, sampleWindows, epistemic } = data;
  const currentHorizon: RollingHorizonResult = activeHorizon === "3Y" ? primary3Y : supporting1Y;

  // Format helper functions (formatting only; no financial calculations)
  const formatPct = (val: number | null | undefined, digits: number = 2) => {
    if (val === null || val === undefined) return "Not available";
    return `${(val * 100).toFixed(digits)}%`;
  };

  const formatRawPct = (val: number | null | undefined, digits: number = 2) => {
    if (val === null || val === undefined) return "Not available";
    return `${val.toFixed(digits)}%`;
  };

  // Distribution range visualization geometry
  // Map min to max into a percentage [0..100] for visual slider
  const minVal = currentHorizon.minReturn ?? 0;
  const maxVal = currentHorizon.maxReturn ?? 1;
  const p25Val = currentHorizon.p25Return ?? minVal;
  const medVal = currentHorizon.medianReturn ?? (minVal + maxVal) / 2;
  const p75Val = currentHorizon.p75Return ?? maxVal;

  const span = maxVal > minVal ? maxVal - minVal : 1;
  const p25Pos = Math.max(0, Math.min(100, ((p25Val - minVal) / span) * 100));
  const medPos = Math.max(0, Math.min(100, ((medVal - minVal) / span) * 100));
  const p75Pos = Math.max(0, Math.min(100, ((p75Val - minVal) / span) * 100));
  const iqrWidth = Math.max(2, p75Pos - p25Pos);

  return (
    <section className="panel p-4 md:p-5" id="rolling-consistency">
      {/* Header & Horizon Controls */}
      <div className="panel-header border-b border-border pb-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <p className="eyebrow text-accent">Multi-Window Consistency Analysis</p>
            <span className="status-badge state-approved">
              <span className="status-dot" aria-hidden />
              {epistemic.dataQualityStatus}
            </span>
            <span className="status-badge state-unavailable">§RET-05 / §RET-06</span>
          </div>
          <h3 className="mt-1.5 text-h2 text-text-primary">
            Rolling Return Distribution &amp; Outperformance Consistency
          </h3>
          <p className="mono-meta mt-1 font-sans">
            Evaluates multi-period consistency across rolling horizons (§RET-05 &amp; §RET-06) vs{" "}
            {context.benchmarkName}
          </p>
        </div>
      </div>

      {/* Horizon Tabs (3Y Primary §RET-05 / 1Y Supporting) */}
      <div className="tab-rail mt-3" role="tablist" aria-label="Rolling horizon">
        <button
          type="button"
          role="tab"
          aria-selected={activeHorizon === "3Y"}
          data-active={activeHorizon === "3Y"}
          onClick={() => setActiveHorizon("3Y")}
          className="tab"
        >
          <span className="tab-ordinal">§RET-05</span>
          <span>3-Year Horizon (Canonical)</span>
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeHorizon === "1Y"}
          data-active={activeHorizon === "1Y"}
          onClick={() => setActiveHorizon("1Y")}
          className="tab"
        >
          <span className="tab-ordinal">Supporting</span>
          <span>1-Year Horizon</span>
        </button>
      </div>

      {/* Horizon Meta & Qualification Banner */}
      <div className="def-list def-list-3 mt-3">
        <div>
          <p className="def-label">Rolling Window</p>
          <p className="def-value">
            {currentHorizon.windowYears * 365} Calendar Days ({currentHorizon.periodType})
          </p>
        </div>
        <div>
          <p className="def-label">Annualization Convention</p>
          <p className="def-value">Julian 365.25 Days</p>
        </div>
        <div>
          <p className="def-label">Valid Windows</p>
          <p className="def-value text-approved-fg">
            {currentHorizon.totalWindows ?? 0}{" "}
            <span className="text-text-tertiary">
              (Threshold: ≥ {currentHorizon.minWindowsRequired})
            </span>
          </p>
        </div>
      </div>

      {/* 4-Stat Core Metric Distribution Grid */}
      <div className="grid grid-cols-2 gap-4 pt-4 lg:grid-cols-4">
        {/* Mean Rolling Return */}
        <div className="metric-tile">
          <p className="def-label mb-0">Mean Rolling Return</p>
          <p className="data-value-md mt-2 text-approved-fg">
            {formatPct(currentHorizon.meanReturn)}
          </p>
          <div className="metric-rule my-3" aria-hidden />
          <p className="mono-meta font-sans leading-[1.45]">
            Average annualized CAGR across {currentHorizon.totalWindows} windows
          </p>
        </div>

        {/* Median Rolling Return */}
        <div className="metric-tile">
          <p className="def-label mb-0">Median Rolling Return</p>
          <p className="data-value-md mt-2">{formatPct(currentHorizon.medianReturn)}</p>
          <div className="metric-rule my-3" aria-hidden />
          <p className="mono-meta font-sans leading-[1.45]">
            50th percentile (reduces outlier skew)
          </p>
        </div>

        {/* Return Dispersion (Min - Max) */}
        <div className="metric-tile">
          <p className="def-label mb-0">Realized Range (Min → Max)</p>
          <p className="data-value-sm mt-2 leading-[1.35]">
            {formatPct(currentHorizon.minReturn)} → {formatPct(currentHorizon.maxReturn)}
          </p>
          <div className="metric-rule my-3" aria-hidden />
          <p className="mono-meta font-sans leading-[1.45]">
            Std Dev: {formatPct(currentHorizon.stdDev)} across windows
          </p>
        </div>

        {/* Outperformance vs benchmark */}
        <div className="metric-tile">
          <p className="def-label mb-0">Rolling Outperformance %</p>
          {currentHorizon.outperformanceStatus === "CALCULATED" ? (
            <>
              <p className="data-value-md mt-2 text-accent">
                {formatRawPct(currentHorizon.outperformancePercentage)}
              </p>
              <div className="metric-rule my-3" aria-hidden />
              <p className="mono-meta font-sans leading-[1.45]">
                {currentHorizon.outperformingWindows} of {currentHorizon.pairedWindows} paired
                windows &gt; {context.benchmarkName}
              </p>
            </>
          ) : (
            <>
              <p className="data-value-sm mt-2 text-candidate-fg">
                Insufficient Paired History
              </p>
              <div className="metric-rule my-3" aria-hidden />
              <p className="mono-meta font-sans leading-[1.45]">
                {currentHorizon.pairedWindows} paired windows (requires ≥{" "}
                {currentHorizon.minWindowsRequired})
              </p>
            </>
          )}
        </div>
      </div>

      {/* Visual Return Distribution Bar */}
      <div className="panel-inset mt-4 p-4">
        <div className="panel-header">
          <p className="text-[13px] font-semibold text-text-primary">
            Rolling CAGR Distribution Range ({currentHorizon.periodType} Horizon)
          </p>
          <span className="mono-meta shrink-0">
            Interquartile (p25 → p75): {formatPct(currentHorizon.p25Return)} →{" "}
            {formatPct(currentHorizon.p75Return)}
          </span>
        </div>

        {/* Visual Bar Track */}
        <div className="relative pt-6 pb-2">
          {/* Full Range Bar */}
          <div className="relative h-3 w-full overflow-hidden rounded-sm border border-border bg-surface-inset">
            {/* IQR Box (p25 to p75) */}
            <div
              className="absolute inset-y-0 border-x-2 border-accent bg-accent-muted"
              style={{
                left: `${p25Pos}%`,
                width: `${iqrWidth}%`,
              }}
              title={`25th-75th Percentile: ${formatPct(currentHorizon.p25Return)} to ${formatPct(currentHorizon.p75Return)}`}
            />
          </div>

          {/* Median Marker */}
          <div
            className="absolute top-3 -ml-px flex flex-col items-center"
            style={{ left: `${medPos}%` }}
          >
            <div className="h-6 w-px bg-accent" />
            <span className="mono-meta mt-1 whitespace-nowrap text-accent">
              Med {formatPct(currentHorizon.medianReturn)}
            </span>
          </div>

          {/* Min Label */}
          <div className="mono-meta absolute top-7 left-0">Min {formatPct(currentHorizon.minReturn)}</div>

          {/* Max Label */}
          <div className="mono-meta absolute top-7 right-0">Max {formatPct(currentHorizon.maxReturn)}</div>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-2 border-t border-border pt-3">
          <div className="flex flex-wrap items-center gap-4">
            <span className="flex items-center gap-1.5">
              <span className="h-1.5 w-1.5 rounded-full bg-accent" aria-hidden />
              <span className="mono-meta font-sans">Median Return</span>
              <strong className="data-value-sm">{formatPct(currentHorizon.medianReturn)}</strong>
            </span>
            <span className="flex items-center gap-1.5">
              <span
                className="h-2.5 w-3 rounded-xs border border-accent bg-accent-muted"
                aria-hidden
              />
              <span className="mono-meta font-sans">Middle 50% Range (IQR)</span>
              <strong className="data-value-sm">
                {formatPct(currentHorizon.p25Return)} — {formatPct(currentHorizon.p75Return)}
              </strong>
            </span>
          </div>
          <span className="mono-meta font-sans">
            Total Observations Evaluated:{" "}
            <strong className="text-text-primary">{currentHorizon.totalWindows}</strong>
          </span>
        </div>
      </div>

      {/* Outperformance Breakdown & Benchmark Integrity Status */}
      <div className="panel-inset mt-4 p-4">
        <div className="panel-header">
          <div className="min-w-0">
            <p className="text-[13px] font-semibold text-text-primary">
              Benchmark Outperformance Consistency vs {context.benchmarkName}
            </p>
            <p className="mono-meta mt-0.5 font-sans leading-[1.45]">
              {currentHorizon.statusReason}
            </p>
          </div>
          {currentHorizon.outperformanceStatus === "CALCULATED" && (
            <p className="mono-meta shrink-0 font-sans">
              Mean Excess Return:{" "}
              <strong className="data-value-sm text-approved-fg">
                {formatPct(currentHorizon.meanExcessReturn)}
              </strong>
            </p>
          )}
        </div>

        {currentHorizon.outperformanceStatus === "CALCULATED" ? (
          <div className="space-y-2 pt-3">
            {/* Visual Outperformance Bar */}
            <div className="flex h-3 w-full overflow-hidden rounded-sm border border-border bg-surface-inset">
              <div
                className="h-full bg-approved-fg transition-all duration-500"
                style={{ width: `${currentHorizon.outperformancePercentage}%` }}
                title={`Outperforming: ${currentHorizon.outperformingWindows} windows (${formatRawPct(currentHorizon.outperformancePercentage)})`}
              />
              <div
                className="h-full bg-risk-fg transition-all duration-500"
                style={{ width: `${100 - (currentHorizon.outperformancePercentage ?? 100)}%` }}
                title={`Underperforming: ${currentHorizon.underperformingWindows} windows`}
              />
            </div>

            <div className="flex flex-wrap items-center justify-between gap-2">
              <span className="mono-meta font-sans text-approved-fg">
                Outperformed ({currentHorizon.outperformingWindows} windows /{" "}
                {formatRawPct(currentHorizon.outperformancePercentage)})
              </span>
              <span className="mono-meta font-sans text-risk-fg">
                Underperformed ({currentHorizon.underperformingWindows} windows)
              </span>
            </div>
          </div>
        ) : (
          <div className="state-well mt-3 text-left">
            <p className="eyebrow text-candidate-fg">Benchmark Ledger Availability Note</p>
            <p className="mono-meta mt-1.5 font-sans leading-[1.5]">
              {epistemic.benchmarkIntegrityDisclosure} Missing historical benchmark dates are
              strictly distinguished from underperformance. To inspect outperformance consistency
              with real ledger data, switch to the{" "}
              <strong className="font-semibold text-text-primary">1-Year Horizon</strong> tab above.
            </p>
          </div>
        )}
      </div>

      {/* Epistemic Framework: Observation, Interpretation, Limitation */}
      <div className="mt-4">
        <div className="flex flex-wrap items-center gap-2 border-b border-border pb-2">
          <p className="eyebrow text-accent">YUKIRA Epistemic Disclosure Framework</p>
          <p className="mono-meta">
            • No Predictions • No Commercial Winner Labels
          </p>
        </div>

        <div className="grid grid-cols-1 gap-4 pt-3 md:grid-cols-3">
          <div className="chain-col">
            <div className="flex items-baseline gap-2">
              <span className="tab-ordinal">01</span>
              <p className="eyebrow text-accent">Observation — What YUKIRA Sees</p>
            </div>
            <p className="mt-2 text-[13px] leading-[1.55] text-text-secondary">
              {epistemic.observation}
            </p>
          </div>

          <div className="chain-col">
            <div className="flex items-baseline gap-2">
              <span className="tab-ordinal">02</span>
              <p className="eyebrow">Interpretation — What It Means</p>
            </div>
            <p className="mt-2 text-[13px] leading-[1.55] text-text-secondary">
              {epistemic.interpretation}
            </p>
          </div>

          <div className="chain-col">
            <div className="flex items-baseline gap-2">
              <span className="tab-ordinal">03</span>
              <p className="eyebrow text-candidate-fg">Methodological Limitation</p>
            </div>
            <p className="mt-2 text-[13px] leading-[1.55] text-text-secondary">
              {epistemic.limitation}
            </p>
          </div>
        </div>
      </div>

      {/* Progressive Disclosure: Benchmark Pairing & Audit Lineage */}
      <div className="panel-inset mt-4">
        <button
          type="button"
          onClick={() => setShowAuditLineage((prev) => !prev)}
          aria-expanded={showAuditLineage}
          className="flex w-full items-center justify-between gap-3 p-3.5 text-left transition-colors hover:bg-surface-raised"
        >
          <span className="flex min-w-0 flex-wrap items-center gap-2">
            <span className="text-[13px] font-medium text-accent">
              Inspect Benchmark Pairing Lineage &amp; Rolling Sample Records
            </span>
            <span className="mono-meta">
              {showAuditLineage ? "Click to collapse" : "Click to inspect calculation lineage"}
            </span>
          </span>
          <span
            className={`tab-ordinal transition-transform ${showAuditLineage ? "rotate-180" : ""}`}
            aria-hidden
          >
            ▼
          </span>
        </button>

        {showAuditLineage && (
          <div className="border-t border-border p-4">
            <div className="def-list def-list-3">
              <div>
                <p className="def-label">Point-in-Time Cutoff</p>
                <p className="def-value">{context.knowledgeCutoffTime}</p>
              </div>
              <div>
                <p className="def-label">Source Artifact Verification</p>
                <p
                  className="def-value text-approved-fg truncate"
                  title={epistemic.sourceArtifactSha256 ?? "Not available"}
                >
                  {epistemic.sourceArtifactSha256
                    ? `SHA-256: ${epistemic.sourceArtifactSha256.substring(0, 16)}...`
                    : "Not available"}
                </p>
              </div>
              <div>
                <p className="def-label">Calculation Run ID</p>
                <p className="def-value">Run #{epistemic.calculationRunId ?? "N/A"}</p>
              </div>
            </div>

            {/* Recent Paired Sample Windows */}
            {sampleWindows && sampleWindows.length > 0 && (
              <div className="mt-4">
                <div className="panel-header">
                  <p className="text-[13px] font-semibold text-text-primary">
                    Recent Sample Rolling Windows (End-Date Aligned)
                  </p>
                  <span className="mono-meta shrink-0">
                    Outperforming = (Fund CAGR &gt; Benchmark CAGR)
                  </span>
                </div>

                <div className="scroll-region mt-2">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th scope="col">Start Date</th>
                        <th scope="col">End Date</th>
                        <th scope="col" className="text-right">
                          Fund CAGR
                        </th>
                        <th scope="col" className="text-right">
                          Benchmark CAGR
                        </th>
                        <th scope="col" className="text-right">
                          Excess Return
                        </th>
                        <th scope="col">Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {sampleWindows.map((w, idx) => {
                        const excess =
                          w.benchmarkReturn !== null ? w.fundReturn - w.benchmarkReturn : null;
                        return (
                          <tr key={idx}>
                            <td className="num text-left">{w.startDate}</td>
                            <td className="num key text-left">{w.endDate}</td>
                            <td className="num text-approved-fg">{formatPct(w.fundReturn)}</td>
                            <td className="num">{formatPct(w.benchmarkReturn)}</td>
                            <td
                              className={`num ${
                                excess !== null
                                  ? excess > 0
                                    ? "text-approved-fg"
                                    : "text-risk-fg"
                                  : ""
                              }`}
                            >
                              {excess !== null
                                ? `${excess > 0 ? "+" : ""}${(excess * 100).toFixed(2)}%`
                                : "Not available"}
                            </td>
                            <td>
                              {w.outperforming !== null ? (
                                <span
                                  className={`status-badge ${
                                    w.outperforming ? "state-approved" : "state-risk"
                                  }`}
                                >
                                  {w.outperforming ? "OUTPERFORM" : "UNDERPERFORM"}
                                </span>
                              ) : (
                                <span className="status-badge state-unavailable">
                                  UNPAIRED
                                </span>
                              )}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </section>
  );
}