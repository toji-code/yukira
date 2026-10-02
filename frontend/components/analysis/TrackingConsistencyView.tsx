"use client";

import React, { useState, useEffect } from "react";
import { fetchTrackingConsistency } from "@/lib/api/analysis";
import { TrackingConsistencyResponse } from "@/types/analysis";

interface TrackingConsistencyViewProps {
  schemeOptionId: number;
  schemeCode: string;
}

export function TrackingConsistencyView({ schemeOptionId }: TrackingConsistencyViewProps) {
  const [data, setData] = useState<TrackingConsistencyResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [showAuditLineage, setShowAuditLineage] = useState<boolean>(false);

  useEffect(() => {
    let active = true;

    const asOfDate = "2024-01-15";
    const cutoffTime = "2024-01-31T23:59:59+05:30";

    fetchTrackingConsistency(schemeOptionId, asOfDate, cutoffTime)
      .then((res) => {
        if (active) {
          setData(res);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (active) {
          setError(err instanceof Error ? err.message : "Failed to load tracking consistency analysis.");
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
        <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
          <div className="skeleton h-24" />
          <div className="skeleton h-24" />
          <div className="skeleton h-24" />
        </div>
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="state-panel-error" role="alert">
        <p className="text-[15px] font-semibold tracking-[-0.01em] text-text-primary">
          Tracking Consistency Analysis Unavailable
        </p>
        <p className="mt-1 text-[13px] leading-[1.5] text-critical-fg">
          {error ||
            "Unable to compute tracking consistency metrics from verified ledger observations."}
        </p>
      </div>
    );
  }

  const { context, metrics, epistemic } = data;

  const formatPct = (val: number | null | undefined, digits: number = 2) => {
    if (val === null || val === undefined) return "Not available";
    return `${val.toFixed(digits)}%`;
  };

  const formatSignedPct = (val: number | null | undefined, digits: number = 2) => {
    if (val === null || val === undefined) return "Not available";
    const sign = val > 0 ? "+" : "";
    return `${sign}${val.toFixed(digits)}%`;
  };

  const formatRatio = (val: number | null | undefined, digits: number = 2) => {
    if (val === null || val === undefined) return "Not available";
    const sign = val > 0 ? "+" : "";
    return `${sign}${val.toFixed(digits)}`;
  };

  return (
    <section className="panel p-4 md:p-5" id="tracking-consistency">
      {/* Header */}
      <div className="panel-header border-b border-border pb-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <p className="eyebrow text-accent">Market-Relative Consistency &amp; Skill</p>
            <span className="status-badge state-approved">
              <span className="status-dot" aria-hidden />
              {epistemic.dataQualityStatus}
            </span>
            <span className="status-badge state-unavailable">§REL-02 / §RAT-04</span>
          </div>
          <h3 className="mt-1.5 text-h2 text-text-primary">
            Tracking Consistency &amp; Information Ratio
          </h3>
          <p className="mono-meta mt-1 font-sans">
            Dispersion of daily excess returns and active return generated per unit of tracking
            variability vs {context.benchmarkName}
          </p>
        </div>

        {/* Quick Information Ratio Indicator Badge */}
        <div className="panel-inset shrink-0 px-3 py-2">
          <p className="def-label mb-0">Information Ratio</p>
          {metrics.informationRatio != null ? (
            <p
              className={`data-value-md ${
                metrics.informationRatio >= 0 ? "text-approved-fg" : "text-candidate-fg"
              }`}
            >
              {formatRatio(metrics.informationRatio)}
            </p>
          ) : (
            <p className="data-unavailable">{metrics.informationRatioStatus}</p>
          )}
        </div>
      </div>

      {/* Observation Period & Qualification Banner */}
      <div className="def-list def-list-4 mt-3">
        <div>
          <p className="def-label">Observation Window</p>
          <p className="def-value">
            {context.startDate ?? "Not available"} → {context.endDate ?? "Not available"}
          </p>
        </div>
        <div>
          <p className="def-label">Paired Trading Days</p>
          <p className="def-value">{metrics.pairedObservationsCount} Days</p>
        </div>
        <div>
          <p className="def-label">Benchmark</p>
          <p className="def-value">{context.benchmarkName}</p>
        </div>
        <div>
          <p className="def-label">Annualizer</p>
          <p className="def-value">
            √{metrics.periodsPerYear} ({metrics.annualizationConvention})
          </p>
        </div>
      </div>

      <div className="def-list def-list-2 mt-2">
        <div>
          <p className="def-label">Paired Observations</p>
          <p
            className={`def-value ${
              metrics.isSufficientObservations ? "text-approved-fg" : "text-candidate-fg"
            }`}
          >
            {metrics.pairedObservationsCount}
          </p>
        </div>
        <div>
          <p className="def-label">Minimum Observation Gate</p>
          <p className="def-value">≥{metrics.minPairedObservationsRequired}</p>
        </div>
      </div>

      {/* 3-Card Core Metric Grid */}
      <div className="grid grid-cols-1 gap-4 pt-4 md:grid-cols-3">
        {/* Card 1: Annualized Tracking Error (REL-02) */}
        <div className="metric-tile">
          <div className="panel-header">
            <p className="def-label mb-0">Tracking Error (3Y Annualized)</p>
            <span className="status-badge state-candidate shrink-0">§REL-02</span>
          </div>

          {metrics.trackingErrorStatus === "CALCULATED" ? (
            <>
              <p className="data-value-lg mt-2">{formatPct(metrics.trackingErrorAnnualized)}</p>
              <div className="metric-rule my-3" aria-hidden />
              <div className="space-y-1">
                <div className="flex items-baseline justify-between gap-3">
                  <span className="mono-meta font-sans">Formula Divisor</span>
                  <span className="data-value-sm">{metrics.denominatorConvention}</span>
                </div>
                <div className="flex items-baseline justify-between gap-3">
                  <span className="mono-meta font-sans">Annualization Scale</span>
                  <span className="data-value-sm">
                    √{metrics.periodsPerYear} ({metrics.annualizationConvention})
                  </span>
                </div>
              </div>
              <p className="mono-meta mt-2 border-t border-border pt-2 font-sans leading-[1.45]">
                Standard deviation of daily active return differences (N=
                {metrics.pairedObservationsCount})
              </p>
            </>
          ) : (
            <div className="pt-2">
              <p className="data-value-md text-candidate-fg">{metrics.trackingErrorStatus}</p>
              <p className="mono-meta mt-1 font-sans leading-[1.45]">
                {metrics.pairedObservationsCount} paired days found; minimum{" "}
                {metrics.minPairedObservationsRequired} required by methodology.
              </p>
            </div>
          )}
        </div>

        {/* Card 2: Annualized Mean Active Return */}
        <div className="metric-tile">
          <div className="panel-header">
            <p className="def-label mb-0">Mean Active Return (Annualized)</p>
            <span className="status-badge state-unavailable shrink-0">Active Spread</span>
          </div>

          {metrics.isSufficientObservations && metrics.annualizedMeanActiveReturn != null ? (
            <>
              <p
                className={`data-value-lg mt-2 ${
                  metrics.annualizedMeanActiveReturn >= 0
                    ? "text-approved-fg"
                    : "text-risk-fg"
                }`}
              >
                {formatSignedPct(metrics.annualizedMeanActiveReturn)}
              </p>
              <div className="metric-rule my-3" aria-hidden />
              <div className="space-y-1">
                <div className="flex items-baseline justify-between gap-3">
                  <span className="mono-meta font-sans">Mean Daily Excess</span>
                  <span className="data-value-sm">
                    {formatSignedPct(metrics.meanDailyExcessReturn, 4)}
                  </span>
                </div>
                <div className="flex items-baseline justify-between gap-3">
                  <span className="mono-meta font-sans">Annualized Conversion</span>
                  <span className="data-value-sm">Mean × {metrics.periodsPerYear}</span>
                </div>
              </div>
              <p className="mono-meta mt-2 border-t border-border pt-2 font-sans leading-[1.45]">
                Arithmetic average of R_fund,t − R_benchmark,t scaled to 252 trading days
              </p>
            </>
          ) : (
            <div className="pt-2">
              <p className="data-value-md text-candidate-fg">Insufficient Observations</p>
              <p className="mono-meta mt-1 font-sans leading-[1.45]">
                Requires N ≥ {metrics.minPairedObservationsRequired} paired days for reliable active
                mean estimation.
              </p>
            </div>
          )}
        </div>

        {/* Card 3: Information Ratio (RAT-04) */}
        <div className="metric-tile">
          <div className="panel-header">
            <p className="def-label mb-0">Information Ratio (3Y Annualized)</p>
            <span className="status-badge state-candidate shrink-0">§RAT-04</span>
          </div>

          {metrics.informationRatioStatus === "CALCULATED" ? (
            <>
              <p
                className={`data-value-lg mt-2 ${
                  (metrics.informationRatio ?? 0) >= 0
                    ? "text-approved-fg"
                    : "text-candidate-fg"
                }`}
              >
                {formatRatio(metrics.informationRatio)}
              </p>
              <div className="metric-rule my-3" aria-hidden />
              <div className="space-y-1">
                <div className="flex items-baseline justify-between gap-3">
                  <span className="mono-meta font-sans">Ratio Definition</span>
                  <span className="data-value-sm">Active Return / Tracking Error</span>
                </div>
                <div className="flex items-baseline justify-between gap-3">
                  <span className="mono-meta font-sans">Daily Formula</span>
                  <span className="data-value-sm">(ē / s_e) × √252</span>
                </div>
              </div>
              <p className="mono-meta mt-2 border-t border-border pt-2 font-sans leading-[1.45]">
                Active return generated per unit of historical benchmark tracking volatility
              </p>
            </>
          ) : (
            <div className="pt-2">
              <p className="data-value-md text-candidate-fg">{metrics.informationRatioStatus}</p>
              <p className="mono-meta mt-1 font-sans leading-[1.45]">
                {metrics.zeroTrackingError
                  ? "Zero tracking error: Fund exactly mirrored benchmark variance (division by zero undefined)."
                  : `${metrics.pairedObservationsCount} paired days found; minimum ${metrics.minPairedObservationsRequired} required.`}
              </p>
            </div>
          )}
        </div>
      </div>

      {/* Epistemic Disclosures: Observation, Interpretation, Limitation */}
      <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-3">
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

      {/* Progressive Disclosure: Audit Lineage & Methodology Details */}
      <div className="panel-inset mt-4">
        <button
          type="button"
          onClick={() => setShowAuditLineage((prev) => !prev)}
          aria-expanded={showAuditLineage}
          className="flex w-full items-center justify-between gap-3 p-3.5 text-left transition-colors hover:bg-surface-raised"
        >
          <span className="flex min-w-0 flex-wrap items-center gap-2">
            <span className="text-[13px] font-medium text-accent">
              Methodology &amp; Audit Lineage
            </span>
            <span className="status-badge state-unavailable">Deterministic Engine Audit</span>
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
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div className="def-list">
                <div>
                  <p className="def-label">Benchmark Lineage &amp; Source</p>
                  <p className="def-value font-sans leading-[1.45] break-all">
                    {epistemic.benchmarkLineage}
                  </p>
                </div>
                <div>
                  <p className="def-label">Calculation Run ID</p>
                  <p className="def-value">
                    {epistemic.calculationRunId ? `#${epistemic.calculationRunId}` : "Unpersisted Run"}
                  </p>
                </div>
              </div>

              <div className="def-list">
                <div>
                  <p className="def-label">Source Artifact Cryptographic Hash</p>
                  <p className="def-value text-accent select-all break-all">
                    {epistemic.sourceArtifactSha256}
                  </p>
                </div>
                <div>
                  <p className="def-label">Point-in-Time Knowledge Cutoff</p>
                  <p className="def-value">{context.knowledgeCutoffTime}</p>
                </div>
              </div>
            </div>

            <div className="panel-inset mt-4 p-3.5">
              <p className="eyebrow text-accent">Institutional Calculation Guardrails</p>
              <ol className="mt-2 space-y-1.5 text-[12px] leading-[1.5] text-text-secondary">
                <li>
                  <strong className="font-semibold text-text-primary">Synchronous Date Pairing:</strong>{" "}
                  Only paired trading days with authoritative observations in both fund and
                  benchmark ledgers are included. Zero interpolation or forward-fill.
                </li>
                <li>
                  <strong className="font-semibold text-text-primary">
                    Minimum Observation Gate:
                  </strong>{" "}
                  Tracking consistency metrics require at least 700 paired trading days (approx. 3
                  years). Sub-threshold series halt deterministically with
                  INSUFFICIENT_OBSERVATIONS.
                </li>
                <li>
                  <strong className="font-semibold text-text-primary">
                    Mathematical Invariance:
                  </strong>{" "}
                  Information ratio uses the daily excess return formulation: (ē / s_e) × √252 = (ē ×
                  252) / (s_e × √252). Zero tracking error results in an explicit error status
                  rather than undefined floating-point representation.
                </li>
              </ol>
            </div>
          </div>
        )}
      </div>
    </section>
  );
}