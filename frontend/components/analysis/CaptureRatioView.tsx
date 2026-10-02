"use client";

import React, { useState, useEffect } from "react";
import { fetchCaptureRatios } from "@/lib/api/analysis";
import { CaptureRatioResponse } from "@/types/analysis";

interface CaptureRatioViewProps {
  schemeOptionId: number;
  schemeCode: string;
}

export function CaptureRatioView({ schemeOptionId }: CaptureRatioViewProps) {
  const [data, setData] = useState<CaptureRatioResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [showAuditLineage, setShowAuditLineage] = useState<boolean>(false);

  useEffect(() => {
    let active = true;

    const asOfDate = "2024-01-15";
    const cutoffTime = "2024-01-31T23:59:59+05:30";

    fetchCaptureRatios(schemeOptionId, asOfDate, cutoffTime)
      .then((res) => {
        if (active) {
          setData(res);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (active) {
          setError(err instanceof Error ? err.message : "Failed to load capture ratio analysis.");
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
          Capture Ratio Analysis Unavailable
        </p>
        <p className="mt-1 text-[13px] leading-[1.5] text-critical-fg">
          {error ||
            "Unable to compute capture ratio metrics from verified ledger observations."}
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

  const formatRatio = (val: number | null | undefined) => {
    if (val === null || val === undefined) return "Not available";
    return `${val.toFixed(2)}%`;
  };

  // Safe visual percentage widths (clamped 0..150% visual scale)
  const ucWidth = metrics.upsideCaptureRatio != null
    ? Math.min(100, Math.max(5, (metrics.upsideCaptureRatio / 150) * 100))
    : 0;
  const dcWidth = metrics.downsideCaptureRatio != null
    ? Math.min(100, Math.max(5, (metrics.downsideCaptureRatio / 150) * 100))
    : 0;
  const baselineWidth = (100 / 150) * 100; // 66.67% represents 100% benchmark baseline

  return (
    <section className="panel p-4 md:p-5" id="capture-ratios">
      {/* Header */}
      <div className="panel-header border-b border-border pb-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <p className="eyebrow text-accent">Market Sensitivity &amp; Asymmetry</p>
            <span className="status-badge state-approved">
              <span className="status-dot" aria-hidden />
              {epistemic.dataQualityStatus}
            </span>
            <span className="status-badge state-unavailable">§MKT-03 / §MKT-04 / §MKT-05</span>
          </div>
          <h3 className="mt-1.5 text-h2 text-text-primary">
            Capture Ratios &amp; Asymmetric Market Participation
          </h3>
          <p className="mono-meta mt-1 font-sans">
            Evaluates participation during benchmark-positive vs benchmark-negative regimes vs{" "}
            {context.benchmarkName}
          </p>
        </div>

        {/* Quick Asymmetry Indicator Badge */}
        <div className="panel-inset shrink-0 px-3 py-2">
          <p className="def-label mb-0">Capture Asymmetry</p>
          {metrics.captureSpread != null ? (
            <p
              className={`data-value-md ${
                metrics.captureSpread >= 0 ? "text-approved-fg" : "text-candidate-fg"
              }`}
            >
              {metrics.captureSpread >= 0 ? "+" : ""}
              {metrics.captureSpread.toFixed(2)} pp
            </p>
          ) : (
            <p className="data-unavailable">Insufficient Data</p>
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
          <p className="def-value">{metrics.totalPairedDays} Days</p>
        </div>
        <div>
          <p className="def-label">Up Days (≥{metrics.minUpDaysRequired})</p>
          <p className="def-value text-approved-fg">{metrics.upDaysCount}</p>
        </div>
        <div>
          <p className="def-label">Down Days (≥{metrics.minDownDaysRequired})</p>
          <p className="def-value text-risk-fg">{metrics.downDaysCount}</p>
        </div>
      </div>

      {/* 3-Card Core Capture Metric Grid */}
      <div className="grid grid-cols-1 gap-4 pt-4 md:grid-cols-3">
        {/* Card 1: Upside Capture (MKT-03) */}
        <div className="metric-tile">
          <div className="panel-header">
            <p className="def-label mb-0">Upside Capture Ratio (3Y)</p>
            <span className="status-badge state-candidate shrink-0">§MKT-03</span>
          </div>

          {metrics.upsideStatus === "CALCULATED" ? (
            <>
              <p className="data-value-lg mt-2 text-approved-fg">
                {formatRatio(metrics.upsideCaptureRatio)}
              </p>
              <div className="metric-rule my-3" aria-hidden />
              <div className="space-y-1">
                <div className="flex items-baseline justify-between gap-3">
                  <span className="mono-meta font-sans">Fund Up Cumulative</span>
                  <span className="data-value-sm text-approved-fg">
                    {formatSignedPct(
                      metrics.fundUpCumulativeReturn
                        ? metrics.fundUpCumulativeReturn * 100
                        : null
                    )}
                  </span>
                </div>
                <div className="flex items-baseline justify-between gap-3">
                  <span className="mono-meta font-sans">Index Up Cumulative</span>
                  <span className="data-value-sm">
                    {formatSignedPct(
                      metrics.benchUpCumulativeReturn
                        ? metrics.benchUpCumulativeReturn * 100
                        : null
                    )}
                  </span>
                </div>
              </div>
              <p className="mono-meta mt-2 border-t border-border pt-2 font-sans leading-[1.45]">
                Computed across {metrics.upDaysCount} positive benchmark days (threshold:
                ≥{metrics.minUpDaysRequired})
              </p>
            </>
          ) : (
            <div className="pt-2">
              <p className="data-value-md text-candidate-fg">Insufficient Positive Days</p>
              <p className="mono-meta mt-1 font-sans leading-[1.45]">
                {metrics.upDaysCount} positive days found; minimum{" "}
                {metrics.minUpDaysRequired} required by methodology.
              </p>
            </div>
          )}
        </div>

        {/* Card 2: Downside Capture (MKT-04) */}
        <div className="metric-tile metric-risk-rule">
          <div className="panel-header">
            <p className="def-label mb-0">Downside Capture Ratio (3Y)</p>
            <span className="status-badge state-candidate shrink-0">§MKT-04</span>
          </div>

          {metrics.downsideStatus === "CALCULATED" ? (
            <>
              <p className="data-value-lg mt-2 text-risk-fg">
                {formatRatio(metrics.downsideCaptureRatio)}
              </p>
              <div className="metric-rule my-3" aria-hidden />
              <div className="space-y-1">
                <div className="flex items-baseline justify-between gap-3">
                  <span className="mono-meta font-sans">Fund Down Cumulative</span>
                  <span className="data-value-sm text-risk-fg">
                    {formatSignedPct(
                      metrics.fundDownCumulativeReturn
                        ? metrics.fundDownCumulativeReturn * 100
                        : null
                    )}
                  </span>
                </div>
                <div className="flex items-baseline justify-between gap-3">
                  <span className="mono-meta font-sans">Index Down Cumulative</span>
                  <span className="data-value-sm">
                    {formatSignedPct(
                      metrics.benchDownCumulativeReturn
                        ? metrics.benchDownCumulativeReturn * 100
                        : null
                    )}
                  </span>
                </div>
              </div>
              <p className="mono-meta mt-2 border-t border-border pt-2 font-sans leading-[1.45]">
                Computed across {metrics.downDaysCount} negative benchmark days (threshold:
                ≥{metrics.minDownDaysRequired})
              </p>
              {metrics.isInverseCaptureGain && (
                <div className="state-panel-error mt-3">
                  <p className="eyebrow text-critical-fg">INVERSE_CAPTURE_GAIN</p>
                  <p className="mt-1 text-[13px] leading-[1.5] text-critical-fg">
                    Fund produced cumulative gains during down-market days.
                  </p>
                </div>
              )}
            </>
          ) : (
            <div className="pt-2">
              <p className="data-value-md text-candidate-fg">Insufficient Negative Days</p>
              <p className="mono-meta mt-1 font-sans leading-[1.45]">
                {metrics.downDaysCount} negative days found; minimum{" "}
                {metrics.minDownDaysRequired} required by methodology.
              </p>
            </div>
          )}
        </div>

        {/* Card 3: Capture Spread (MKT-05) */}
        <div className="metric-tile">
          <div className="panel-header">
            <p className="def-label mb-0">Capture Spread (3Y)</p>
            <span className="status-badge state-candidate shrink-0">§MKT-05</span>
          </div>

          {metrics.spreadStatus === "CALCULATED" && metrics.captureSpread != null ? (
            <>
              <p
                className={`data-value-lg mt-2 ${
                  metrics.captureSpread >= 0 ? "text-approved-fg" : "text-candidate-fg"
                }`}
              >
                {metrics.captureSpread >= 0 ? "+" : ""}
                {metrics.captureSpread.toFixed(2)} pp
              </p>
              <div className="metric-rule my-3" aria-hidden />
              <div className="space-y-1">
                <div className="flex items-baseline justify-between gap-3">
                  <span className="mono-meta font-sans">Formula</span>
                  <span className="data-value-sm">Upside Capture - Downside Capture</span>
                </div>
                <div className="flex items-baseline justify-between gap-3">
                  <span className="mono-meta font-sans">Constituents</span>
                  <span className="data-value-sm">
                    {formatPct(metrics.upsideCaptureRatio)} -{" "}
                    {formatPct(metrics.downsideCaptureRatio)}
                  </span>
                </div>
              </div>
              <p className="mono-meta mt-2 border-t border-border pt-2 font-sans leading-[1.45]">
                Measures market participation differential without evaluative ranking or
                score.
              </p>
            </>
          ) : (
            <div className="pt-2">
              <p className="data-value-md text-candidate-fg">Spread Undefined</p>
              <p className="mono-meta mt-1 font-sans leading-[1.45]">
                Both upside and downside capture ratios must meet minimum sample size
                requirements.
              </p>
            </div>
          )}
        </div>
      </div>

      {/* Visual Asymmetric Participation Comparison Display */}
      {metrics.upsideStatus === "CALCULATED" && metrics.downsideStatus === "CALCULATED" && (
        <div className="panel-inset mt-4 p-4">
          <div className="panel-header">
            <p className="text-[13px] font-semibold text-text-primary">
              Regime Participation Comparison vs Benchmark Parity (100%)
            </p>
            <span className="mono-meta shrink-0">Benchmark Parity Baseline = 100.00%</span>
          </div>

          <div className="space-y-4 pt-3">
            {/* Upside Participation Bar */}
            <div className="space-y-1">
              <div className="flex items-baseline justify-between gap-3">
                <span className="text-[13px] font-medium text-approved-fg">
                  Bull Market Participation (Upside Capture)
                </span>
                <span className="data-value-sm">{formatRatio(metrics.upsideCaptureRatio)}</span>
              </div>
              <div className="relative h-3 w-full overflow-hidden rounded-sm border border-border bg-surface-inset">
                {/* 100% baseline marker */}
                <div
                  className="absolute top-0 bottom-0 z-10 w-px bg-text-tertiary"
                  style={{ left: `${baselineWidth}%` }}
                  title="Benchmark Parity (100%)"
                />
                {/* Fund Bar */}
                <div
                  className="h-full bg-approved-fg transition-all"
                  style={{ width: `${ucWidth}%` }}
                />
              </div>
              <div className="flex justify-between gap-2">
                <span className="mono-meta">0%</span>
                <span className="mono-meta text-text-secondary">Benchmark Parity (100%)</span>
                <span className="mono-meta">150%</span>
              </div>
            </div>

            {/* Downside Absorption Bar */}
            <div className="space-y-1">
              <div className="flex items-baseline justify-between gap-3">
                <span className="text-[13px] font-medium text-risk-fg">
                  Bear Market Absorption (Downside Capture)
                </span>
                <span className="data-value-sm">{formatRatio(metrics.downsideCaptureRatio)}</span>
              </div>
              <div className="relative h-3 w-full overflow-hidden rounded-sm border border-border bg-surface-inset">
                {/* 100% baseline marker */}
                <div
                  className="absolute top-0 bottom-0 z-10 w-px bg-text-tertiary"
                  style={{ left: `${baselineWidth}%` }}
                  title="Benchmark Parity (100%)"
                />
                {/* Fund Bar */}
                <div
                  className="h-full bg-risk-fg transition-all"
                  style={{ width: `${dcWidth}%` }}
                />
              </div>
              <div className="flex justify-between gap-2">
                <span className="mono-meta">0% (Full Protection)</span>
                <span className="mono-meta text-text-secondary">Benchmark Parity (100%)</span>
                <span className="mono-meta">150% (Amplified Loss)</span>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Epistemic Tri-Partite Callout */}
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

      {/* Expandable Technical Lineage & Audit Drawer */}
      <div className="panel-inset mt-4">
        <button
          type="button"
          onClick={() => setShowAuditLineage((prev) => !prev)}
          aria-expanded={showAuditLineage}
          className="flex w-full items-center justify-between gap-3 p-3.5 text-left transition-colors hover:bg-surface-raised"
        >
          <span className="flex min-w-0 flex-wrap items-center gap-2">
            <span className="text-[13px] font-medium text-accent">
              Audit Lineage &amp; Provenance
            </span>
            <span className="mono-meta">
              {showAuditLineage
                ? "Click to collapse"
                : "Click to inspect calculation lineage"}
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
                <p className="def-label">Benchmark Alignment</p>
                <p className="def-value font-sans leading-[1.45]">{epistemic.benchmarkLineage}</p>
              </div>
              <div>
                <p className="def-label">Total Paired Days</p>
                <p className="def-value">
                  {metrics.totalPairedDays} calendar days (Up: {metrics.upDaysCount}, Down:{" "}
                  {metrics.downDaysCount}, Flat: {metrics.flatDaysCount})
                </p>
              </div>
              <div>
                <p className="def-label">Flat Return Days (Rb = 0)</p>
                <p className="def-value">
                  {metrics.flatDaysCount} days explicitly excluded per methodology
                </p>
              </div>
            </div>

            <div className="def-list">
              <div>
                <p className="def-label">Knowledge Cutoff</p>
                <p className="def-value">{context.knowledgeCutoffTime}</p>
              </div>
              <div>
                <p className="def-label">Calculation Run ID</p>
                <p className="def-value">
                  {epistemic.calculationRunId ?? "Ephemeral In-Memory Audit"}
                </p>
              </div>
              <div>
                <p className="def-label">Source Artifact SHA-256</p>
                <p className="def-value text-accent select-all break-all">
                  {epistemic.sourceArtifactSha256}
                </p>
              </div>
              <div>
                <p className="def-label">Governance Status</p>
                <p className="def-value text-candidate-fg">CANDIDATE / REQUIRES VALIDATION</p>
              </div>
            </div>
          </div>
        </div>
        )}
      </div>
    </section>
  );
}