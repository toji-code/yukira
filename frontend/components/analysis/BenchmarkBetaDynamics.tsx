"use client";

import React, { useState, useEffect } from "react";
import { fetchBetaDynamics } from "@/lib/api/analysis";
import { BetaDynamicsResponse } from "@/types/analysis";

interface BenchmarkBetaDynamicsProps {
  schemeOptionId: number;
  schemeCode: string;
}

export function BenchmarkBetaDynamics({ schemeOptionId }: BenchmarkBetaDynamicsProps) {
  const [data, setData] = useState<BetaDynamicsResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [showAuditLineage, setShowAuditLineage] = useState<boolean>(false);

  useEffect(() => {
    let active = true;

    const asOfDate = "2024-01-15";
    const cutoffTime = "2024-01-31T23:59:59+05:30";

    fetchBetaDynamics(schemeOptionId, asOfDate, cutoffTime)
      .then((res) => {
        if (active) {
          setData(res);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (active) {
          setError(err instanceof Error ? err.message : "Failed to load beta dynamics analysis.");
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
          Benchmark Beta Dynamics Unavailable
        </p>
        <p className="mt-1 text-[13px] leading-[1.5] text-critical-fg">
          {error ||
            "Unable to compute beta metrics from verified ledger observations."}
        </p>
      </div>
    );
  }

  const { context, metrics, epistemic } = data;

  const formatBeta = (val: number | null | undefined) => {
    if (val === null || val === undefined) return "Not available";
    return val.toFixed(4);
  };

  const betaBarWidth = (val: number | null | undefined) => {
    if (val === null || val === undefined) return 0;
    return Math.min(100, Math.max(4, (Math.abs(val) / 2.0) * 100));
  };

  const parityWidth = (1.0 / 2.0) * 100;

  return (
    <section className="panel p-4 md:p-5" id="beta-dynamics">
      <div className="panel-header border-b border-border pb-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <p className="eyebrow text-accent">Systematic Covariance</p>
            <span className="status-badge state-approved">
              <span className="status-dot" aria-hidden />
              {epistemic.dataQualityStatus}
            </span>
            <span className="status-badge state-unavailable">§REL-01 / §REL-04 / §REL-05</span>
          </div>
          <h3 className="mt-1.5 text-h2 text-text-primary">
            Benchmark Beta Dynamics
          </h3>
          <p className="mono-meta mt-1 font-sans">
            Linear co-movement vs {context.benchmarkName} — standard, downside, and upside
            regimes
          </p>
        </div>

        <div className="panel-inset shrink-0 px-3 py-2">
          <p className="def-label mb-0">Beta Asymmetry</p>
          {metrics.betaAsymmetrySpread != null ? (
            <p
              className={`data-value-md ${
                metrics.betaAsymmetrySpread >= 0
                  ? "text-approved-fg"
                  : "text-candidate-fg"
              }`}
            >
              {metrics.betaAsymmetrySpread >= 0 ? "+" : ""}
              {metrics.betaAsymmetrySpread.toFixed(4)}
            </p>
          ) : (
            <p className="data-unavailable">Insufficient Data</p>
          )}
        </div>
      </div>

      <div className="def-list def-list-4 mt-3">
        <div>
          <p className="def-label">Observation Window</p>
          <p className="def-value">
            {context.startDate ?? "Not available"} → {context.endDate ?? "Not available"}
          </p>
        </div>
        <div>
          <p className="def-label">Paired Days (≥{metrics.minPairedRequired})</p>
          <p className="def-value">{metrics.totalPairedDays}</p>
        </div>
        <div>
          <p className="def-label">Up Days (≥{metrics.minUpRequired})</p>
          <p className="def-value text-approved-fg">{metrics.upDaysCount}</p>
        </div>
        <div>
          <p className="def-label">Down Days (≥{metrics.minDownRequired})</p>
          <p className="def-value text-risk-fg">{metrics.downDaysCount}</p>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 pt-4 md:grid-cols-3">
        <div className="metric-tile">
          <div className="panel-header">
            <p className="def-label mb-0">Beta (3Y)</p>
            <span className="status-badge state-candidate shrink-0">§REL-01</span>
          </div>
          {metrics.standardBetaStatus === "CALCULATED" ? (
            <>
              <p className="data-value-lg mt-2">{formatBeta(metrics.standardBeta)}</p>
              <p className="mono-meta mt-2 font-sans leading-[1.45]">
                Excess-return OLS slope vs {context.benchmarkName}. Measures full-sample
                linear sensitivity. β = 1 tracks the market; β &gt; 1 amplifies moves; β &lt; 1
                dampens them.
              </p>
              <p className="mono-meta mt-2 border-t border-border pt-2 font-sans leading-[1.45]">
                {metrics.totalPairedDays} paired days ·{" "}
                {metrics.riskFreeAligned ? "FBIL 91D T-Bill excess" : "raw returns"} · no
                annualization
              </p>
            </>
          ) : (
            <div className="pt-2">
              <p className="data-value-md text-candidate-fg">Insufficient Paired Days</p>
              <p className="mono-meta mt-1 font-sans leading-[1.45]">
                {metrics.totalPairedDays} paired days found; minimum{" "}
                {metrics.minPairedRequired} required.
              </p>
            </div>
          )}
        </div>

        <div className="metric-tile metric-risk-rule">
          <div className="panel-header">
            <p className="def-label mb-0">Downside Beta (3Y)</p>
            <span className="status-badge state-candidate shrink-0">§REL-04</span>
          </div>
          {metrics.downsideBetaStatus === "CALCULATED" ? (
            <>
              <p className="data-value-lg mt-2 text-risk-fg">
                {formatBeta(metrics.downsideBeta)}
              </p>
              <p className="mono-meta mt-2 font-sans leading-[1.45]">
                Co-movement conditioned strictly on days when the benchmark declined (Rb &lt;
                0). Isolates sell-off sensitivity; raw returns, no risk-free adjustment.
              </p>
              <p className="mono-meta mt-2 border-t border-border pt-2 font-sans leading-[1.45]">
                Computed across {metrics.downDaysCount} negative benchmark days (threshold:
                ≥{metrics.minDownRequired})
              </p>
            </>
          ) : (
            <div className="pt-2">
              <p className="data-value-md text-candidate-fg">Insufficient Negative Days</p>
              <p className="mono-meta mt-1 font-sans leading-[1.45]">
                {metrics.downDaysCount} negative days found; minimum{" "}
                {metrics.minDownRequired} required by methodology.
              </p>
            </div>
          )}
        </div>

        <div className="metric-tile">
          <div className="panel-header">
            <p className="def-label mb-0">Upside Beta (3Y)</p>
            <span className="status-badge state-candidate shrink-0">§REL-05</span>
          </div>
          {metrics.upsideBetaStatus === "CALCULATED" ? (
            <>
              <p className="data-value-lg mt-2 text-approved-fg">
                {formatBeta(metrics.upsideBeta)}
              </p>
              <p className="mono-meta mt-2 font-sans leading-[1.45]">
                Co-movement conditioned strictly on days when the benchmark rose (Rb &gt; 0).
                Isolates advance participation; raw returns, no risk-free adjustment.
              </p>
              <p className="mono-meta mt-2 border-t border-border pt-2 font-sans leading-[1.45]">
                Computed across {metrics.upDaysCount} positive benchmark days (threshold:
                ≥{metrics.minUpRequired})
              </p>
            </>
          ) : (
            <div className="pt-2">
              <p className="data-value-md text-candidate-fg">Insufficient Positive Days</p>
              <p className="mono-meta mt-1 font-sans leading-[1.45]">
                {metrics.upDaysCount} positive days found; minimum{" "}
                {metrics.minUpRequired} required by methodology.
              </p>
            </div>
          )}
        </div>
      </div>

      {(metrics.standardBetaStatus === "CALCULATED" ||
        metrics.downsideBetaStatus === "CALCULATED" ||
        metrics.upsideBetaStatus === "CALCULATED") && (
        <div className="panel-inset mt-4 p-4">
          <div className="panel-header">
            <p className="text-[13px] font-semibold text-text-primary">
              Regime Sensitivity vs Market Parity (β = 1.00)
            </p>
            <span className="mono-meta shrink-0">Scale 0.00 → 2.00</span>
          </div>

          <div className="space-y-4 pt-3">
            {metrics.standardBetaStatus === "CALCULATED" && (
              <div className="space-y-1">
                <div className="flex items-baseline justify-between gap-3">
                  <span className="text-[13px] font-medium text-text-primary">
                    Beta (full sample)
                  </span>
                  <span className="data-value-sm">{formatBeta(metrics.standardBeta)}</span>
                </div>
                <div className="relative h-3 w-full overflow-hidden rounded-sm border border-border bg-surface-inset">
                  <div
                    className="absolute top-0 bottom-0 z-10 w-px bg-text-tertiary"
                    style={{ left: `${parityWidth}%` }}
                    title="Market Parity (β = 1)"
                  />
                  <div
                    className="h-full bg-accent transition-all"
                    style={{ width: `${betaBarWidth(metrics.standardBeta)}%` }}
                  />
                </div>
              </div>
            )}

            {metrics.downsideBetaStatus === "CALCULATED" && (
              <div className="space-y-1">
                <div className="flex items-baseline justify-between gap-3">
                  <span className="text-[13px] font-medium text-risk-fg">
                    Downside Beta (Rb &lt; 0)
                  </span>
                  <span className="data-value-sm">{formatBeta(metrics.downsideBeta)}</span>
                </div>
                <div className="relative h-3 w-full overflow-hidden rounded-sm border border-border bg-surface-inset">
                  <div
                    className="absolute top-0 bottom-0 z-10 w-px bg-text-tertiary"
                    style={{ left: `${parityWidth}%` }}
                    title="Market Parity (β = 1)"
                  />
                  <div
                    className="h-full bg-risk-fg transition-all"
                    style={{ width: `${betaBarWidth(metrics.downsideBeta)}%` }}
                  />
                </div>
              </div>
            )}

            {metrics.upsideBetaStatus === "CALCULATED" && (
              <div className="space-y-1">
                <div className="flex items-baseline justify-between gap-3">
                  <span className="text-[13px] font-medium text-approved-fg">
                    Upside Beta (Rb &gt; 0)
                  </span>
                  <span className="data-value-sm">{formatBeta(metrics.upsideBeta)}</span>
                </div>
                <div className="relative h-3 w-full overflow-hidden rounded-sm border border-border bg-surface-inset">
                  <div
                    className="absolute top-0 bottom-0 z-10 w-px bg-text-tertiary"
                    style={{ left: `${parityWidth}%` }}
                    title="Market Parity (β = 1)"
                  />
                  <div
                    className="h-full bg-approved-fg transition-all"
                    style={{ width: `${betaBarWidth(metrics.upsideBeta)}%` }}
                  />
                </div>
                <div className="flex justify-between gap-2">
                  <span className="mono-meta">0.00 (uncorrelated)</span>
                  <span className="mono-meta text-text-secondary">Market Parity (1.00)</span>
                  <span className="mono-meta">2.00 (amplified)</span>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

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
          <div className="grid grid-cols-1 gap-4 border-t border-border p-4 md:grid-cols-2">
            <div className="def-list">
              <div>
                <p className="def-label">Benchmark Alignment</p>
                <p className="def-value font-sans leading-[1.45]">{epistemic.benchmarkLineage}</p>
              </div>
              <div>
                <p className="def-label">Paired / Up / Down / Flat</p>
                <p className="def-value">
                  {metrics.totalPairedDays} / {metrics.upDaysCount} /{" "}
                  {metrics.downDaysCount} / {metrics.flatDaysCount}
                </p>
              </div>
              <div>
                <p className="def-label">Flat days (Rb = 0)</p>
                <p className="def-value font-sans leading-[1.45]">
                  {metrics.flatDaysCount} excluded from both conditioned betas
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
                  {epistemic.sourceArtifactSha256 ?? "Not available"}
                </p>
              </div>
              <div>
                <p className="def-label">Governance Status</p>
                <p className="def-value text-candidate-fg">CANDIDATE / REQUIRES VALIDATION</p>
              </div>
            </div>
          </div>
        )}
      </div>
    </section>
  );
}