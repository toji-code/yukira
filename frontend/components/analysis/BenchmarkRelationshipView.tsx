"use client";

import React, { useState, useEffect, useCallback } from "react";
import {
  fetchTrackingConsistency,
  fetchBetaDynamics,
  fetchBenchmarkRelationship,
} from "@/lib/api/analysis";
import {
  TrackingConsistencyResponse,
  BetaDynamicsResponse,
  BenchmarkRelationshipResponse,
} from "@/types/analysis";

interface BenchmarkRelationshipViewProps {
  schemeOptionId: number;
  schemeCode?: string;
}

type ActiveTab = "overview" | "tracking";

function formatPct(val: number | null | undefined, digits: number = 2): string {
  if (val === null || val === undefined || isNaN(Number(val))) return "Not available";
  return `${Number(val).toFixed(digits)}%`;
}

function formatSignedPct(val: number | null | undefined, digits: number = 2): string {
  if (val === null || val === undefined || isNaN(Number(val))) return "Not available";
  const num = Number(val);
  const sign = num > 0 ? "+" : "";
  return `${sign}${num.toFixed(digits)}%`;
}

function formatRatio(val: number | string | null | undefined, digits: number = 4): string {
  if (val === null || val === undefined || isNaN(Number(val))) return "Not available";
  const num = Number(val);
  const sign = num > 0 ? "+" : "";
  return `${sign}${num.toFixed(digits)}`;
}

/** Shows an inline metric status badge using SEMANTIC state tokens. */
function StatusBadge({ status }: { status: string }) {
  const isOk = status === "CALCULATED" || status === "SOURCE_ARTIFACT_VERIFIED";
  return (
    <span className={`status-badge ${isOk ? "state-approved" : "state-candidate"}`}>
      {status.replaceAll("_", " ")}
    </span>
  );
}

/** Inline "Not available" placeholder for INSUFFICIENT_DATA metric cards. */
function InsufficientMetric({ status }: { status: string }) {
  const isInsufficient = status === "INSUFFICIENT_DATA" || status === "INSUFFICIENT_OBSERVATIONS";
  return (
    <p className={isInsufficient ? "data-value-sm text-candidate-fg" : "data-unavailable"}>
      {isInsufficient ? "Insufficient data" : "Not available"}
    </p>
  );
}

export function BenchmarkRelationshipView({
  schemeOptionId,
}: BenchmarkRelationshipViewProps) {
  const [trackingData, setTrackingData] = useState<TrackingConsistencyResponse | null>(null);
  const [relationshipData, setRelationshipData] = useState<BenchmarkRelationshipResponse | null>(null);
  const [betaData, setBetaData] = useState<BetaDynamicsResponse | null>(null);

  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const [activeTab, setActiveTab] = useState<ActiveTab>("overview");
  const [showAuditLineage, setShowAuditLineage] = useState<boolean>(false);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    setTrackingData(null);
    setRelationshipData(null);
    setBetaData(null);

    const asOfDate = "2024-01-15";
    const cutoffTime = "2024-01-31T23:59:59+05:30";

    try {
      const [trackingRes, relRes, betaRes] = await Promise.all([
        fetchTrackingConsistency(schemeOptionId, asOfDate, cutoffTime),
        fetchBenchmarkRelationship(schemeOptionId, asOfDate, cutoffTime),
        fetchBetaDynamics(schemeOptionId, asOfDate, cutoffTime),
      ]);

      setTrackingData(trackingRes);
      setRelationshipData(relRes);
      setBetaData(betaRes);
    } catch (err: unknown) {
      setError(
        err instanceof Error
          ? err.message
          : "Failed to load consolidated benchmark relationship analysis."
      );
    } finally {
      setLoading(false);
    }
  }, [schemeOptionId]);

  useEffect(() => {
    let active = true;
    const asOfDate = "2024-01-15";
    const cutoffTime = "2024-01-31T23:59:59+05:30";

    Promise.all([
      fetchTrackingConsistency(schemeOptionId, asOfDate, cutoffTime),
      fetchBenchmarkRelationship(schemeOptionId, asOfDate, cutoffTime),
      fetchBetaDynamics(schemeOptionId, asOfDate, cutoffTime),
    ])
      .then(([trackingRes, relRes, betaRes]) => {
        if (active) {
          setTrackingData(trackingRes);
          setRelationshipData(relRes);
          setBetaData(betaRes);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (active) {
          setError(
            err instanceof Error
              ? err.message
              : "Failed to load consolidated benchmark relationship analysis."
          );
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [schemeOptionId]);

  // ── Loading skeleton ─────────────────────────────────────────────────────────
  if (loading) {
    return (
      <div className="panel space-y-4 p-4 md:p-5" id="benchmark-relationship-panel" aria-busy="true">
        <div className="skeleton h-3 w-1/4" />
        <div className="skeleton h-7 w-2/4" />
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="skeleton h-28" />
          ))}
        </div>
        <div className="skeleton h-32 w-full" />
      </div>
    );
  }

  // ── Error state ──────────────────────────────────────────────────────────────
  if (error || (!trackingData && !relationshipData && !betaData)) {
    return (
      <div className="state-panel-error" id="benchmark-relationship-panel" role="alert">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="text-[15px] font-semibold tracking-[-0.01em] text-text-primary">
            Benchmark Relationship Analysis Unavailable
          </p>
          <button type="button" onClick={loadData} className="btn btn-sm btn-secondary">
            Retry Analysis
          </button>
        </div>
        <p className="mt-1 text-[13px] leading-[1.5] text-critical-fg">
          {error || "Unable to compute benchmark relationship metrics from verified ledger observations."}
        </p>
      </div>
    );
  }

  // ── INSUFFICIENT_DATA state (data loaded but below threshold) ────────────────
  const isRelationshipInsufficient =
    relationshipData?.metrics.resultState === "INSUFFICIENT_DATA" ||
    (!relationshipData?.metrics.isSufficient && !relationshipData?.metrics.correlation);
  const isTrackingInsufficient =
    trackingData && !trackingData.metrics.isSufficientObservations;

  if (isRelationshipInsufficient && isTrackingInsufficient && !betaData?.metrics.isStandardSufficient) {
    const pairedCount =
      betaData?.metrics.totalPairedDays ??
      trackingData?.metrics.pairedObservationsCount ??
      relationshipData?.metrics.pairedObservationCount ??
      0;
    const cutoff = trackingData?.context.knowledgeCutoffTime ??
      betaData?.context.knowledgeCutoffTime ??
      relationshipData?.context.knowledgeCutoffTime ??
      "2024-01-31T23:59:59+05:30";

    return (
      <div className="panel p-4 md:p-5" id="benchmark-relationship-panel" role="status">
        <div className="flex items-start gap-3">
          <span className="status-badge state-candidate mt-0.5 shrink-0" aria-hidden="true">
            !
          </span>
          <div className="min-w-0">
            <p className="text-[15px] font-semibold tracking-[-0.01em] text-text-primary">
              Insufficient Paired Observations — 6-Metric Panel Unavailable
            </p>
            <p className="mono-meta mt-1 font-sans leading-[1.55]">
              <strong>{pairedCount}</strong> paired trading days found across the observation
              window.{" "}
              A minimum of <strong>700 paired days</strong> (approx. 3 years) is required to compute
              all six benchmark relationship indicators with methodological validity.
            </p>
            <p className="mono-meta mt-1.5">Knowledge cutoff: {cutoff}</p>
          </div>
        </div>
        <button type="button" onClick={loadData} className="btn btn-sm btn-secondary mt-3">
          Retry Analysis
        </button>
      </div>
    );
  }

  // ── Derived context values ───────────────────────────────────────────────────
  const benchmarkName =
    trackingData?.context.benchmarkName ||
    relationshipData?.context.benchmarkName ||
    betaData?.context.benchmarkName ||
    "Official Benchmark";

  const startDate =
    trackingData?.context.startDate ||
    relationshipData?.context.startDate ||
    betaData?.context.startDate ||
    "—";

  const endDate =
    trackingData?.context.endDate ||
    relationshipData?.context.endDate ||
    betaData?.context.endDate ||
    "—";

  const knowledgeCutoffTime =
    trackingData?.context.knowledgeCutoffTime ||
    betaData?.context.knowledgeCutoffTime ||
    relationshipData?.context.knowledgeCutoffTime ||
    "2024-01-31T23:59:59+05:30";

  const pairedDays =
    betaData?.metrics.totalPairedDays ??
    trackingData?.metrics.pairedObservationsCount ??
    relationshipData?.metrics.pairedObservationCount ??
    0;

  const dataQuality =
    trackingData?.epistemic.dataQualityStatus ||
    relationshipData?.epistemic.dataQualityStatus ||
    betaData?.epistemic.dataQualityStatus ||
    "UNVERIFIED";

  const isDataQualityOk =
    dataQuality === "SOURCE_ARTIFACT_VERIFIED" || dataQuality === "VERIFIED";

  // ── Consolidated Metric Extraction ───────────────────────────────────────────
  const activeReturnVal = trackingData?.metrics.annualizedMeanActiveReturn;
  const activeReturnStatus = trackingData?.metrics.isSufficientObservations
    ? "CALCULATED"
    : "INSUFFICIENT_DATA";

  return (
    <section
      className="panel p-4 md:p-5"
      id="benchmark-relationship-panel"
      aria-label="Benchmark Relationship & Market Sensitivity Panel"
    >
      {/* ── 1. PANEL HEADER ───────────────────────────────────────────────── */}
      <div className="panel-header flex-wrap border-b border-border pb-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <p className="eyebrow text-accent">
              Consolidated Fund Profile &bull; Benchmark Relationship
            </p>
            {/* Data quality badge — semantic state reflects actual quality status */}
            <span
              className={`status-badge ${isDataQualityOk ? "state-approved" : "state-candidate"}`}
            >
              <span className="status-dot" aria-hidden />
              {dataQuality}
            </span>
            {/* CANDIDATE governance status — always shown since these are candidate metrics */}
            <span className="status-badge state-candidate">CANDIDATE</span>
            <span className="status-badge state-unavailable">6 Core Indicators</span>
          </div>
          <h3 className="mt-1.5 text-h2 text-text-primary">
            Benchmark Relationship &amp; Market Sensitivity
          </h3>
          <p className="mono-meta mt-1 font-sans">
            Synchronized risk, active return, correlation, systematic beta, and explanatory power
            vs <strong className="text-text-primary">{benchmarkName}</strong>
          </p>
        </div>

        {/* PIT Window & Observation Summary */}
        <div className="panel-inset shrink-0 px-3 py-2">
          <p className="def-label mb-0">Paired Observations</p>
          <p className="data-value-md">
            {pairedDays} Days <span className="text-text-tertiary">(≥700 Required)</span>
          </p>
        </div>
      </div>

      <div className="def-list def-list-2 mt-3">
        <div>
          <p className="def-label">Observation Window</p>
          <p className="def-value">
            {startDate} → {endDate}
          </p>
        </div>
        <div>
          <p className="def-label">Benchmark</p>
          <p className="def-value">{benchmarkName}</p>
        </div>
      </div>

      {/* ── 2. THE CONSOLIDATED EXECUTIVE METRIC ───────────────────────────── */}
      <div className="grid grid-cols-1 gap-4 pt-4 sm:grid-cols-2 lg:grid-cols-3">
        {/* Metric: Active Return */}
        <div className="metric-tile">
          <div className="panel-header">
            <p className="def-label mb-0">Active Return</p>
            <span className="status-badge state-candidate shrink-0">§REL-06</span>
          </div>

          <div className="mt-2">
            {activeReturnStatus === "CALCULATED" && activeReturnVal != null ? (
              <p
                className={`data-value-lg ${
                  activeReturnVal >= 0 ? "text-approved-fg" : "text-risk-fg"
                }`}
              >
                {formatSignedPct(activeReturnVal)}
              </p>
            ) : (
              <InsufficientMetric status={activeReturnStatus} />
            )}
          </div>

          <div className="metric-rule my-3" aria-hidden />

          <p className="mono-meta font-sans leading-[1.45]">
            {trackingData?.metrics.meanDailyExcessReturn != null
              ? `Daily mean: ${formatSignedPct(trackingData.metrics.meanDailyExcessReturn, 4)}`
              : "Annualized excess over benchmark"}
          </p>
        </div>
      </div>

      {/* ── 3. INTERACTIVE SUB-VIEW TABS ─────────────────────────────────── */}
      <div className="tab-rail mt-4" role="tablist" aria-label="Benchmark analysis views">
        {(
          [
            { key: "overview", label: "Overview & Epistemic Triad" },
            { key: "tracking", label: "Tracking & Active Spread" },
          ] as { key: ActiveTab; label: string }[]
        ).map(({ key, label }) => (
          <button
            key={key}
            type="button"
            role="tab"
            onClick={() => setActiveTab(key)}
            data-active={activeTab === key}
            aria-selected={activeTab === key}
            className="tab"
          >
            <span className="tab-ordinal">{key === "overview" ? "01" : "02"}</span>
            <span>{label}</span>
          </button>
        ))}
      </div>

      {/* ── TAB CONTENT 1: OVERVIEW & EPISTEMIC DISCLOSURE TRIAD ─────────── */}
      {activeTab === "overview" && (
        <div className="grid grid-cols-1 gap-4 pt-4 lg:grid-cols-3">
          <div className="chain-col">
            <div className="flex items-baseline gap-2">
              <span className="tab-ordinal">01</span>
              <p className="eyebrow text-accent">Observation — What YUKIRA Sees</p>
            </div>
            <p className="mt-2 text-[13px] leading-[1.55] text-text-secondary">
              {relationshipData?.epistemic.observation ||
                betaData?.epistemic.observation ||
                trackingData?.epistemic.observation ||
                "Synchronized daily observations evaluated against official benchmark returns."}
            </p>
          </div>

          <div className="chain-col">
            <div className="flex items-baseline gap-2">
              <span className="tab-ordinal">02</span>
              <p className="eyebrow">Interpretation — What It Means</p>
            </div>
            <p className="mt-2 text-[13px] leading-[1.55] text-text-secondary">
              {betaData?.epistemic.interpretation ||
                trackingData?.epistemic.interpretation ||
                relationshipData?.epistemic.interpretation ||
                "Measures active management risk, market correlation, and systematic beta."}
            </p>
          </div>

          <div className="chain-col">
            <div className="flex items-baseline gap-2">
              <span className="tab-ordinal">03</span>
              <p className="eyebrow text-candidate-fg">Limitation — Methodological Boundary</p>
            </div>
            <p className="mt-2 text-[13px] leading-[1.55] text-text-secondary">
              {betaData?.epistemic.limitation ||
                trackingData?.epistemic.limitation ||
                relationshipData?.epistemic.limitation ||
                "Beta and correlation are static linear historical measures evaluated strictly on paired trading days."}
            </p>
          </div>
        </div>
      )}

      {/* ── TAB CONTENT 2: TRACKING CONSISTENCY ──────────────────────────── */}
      {activeTab === "tracking" && (
        <div className="pt-4">
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <div className="metric-tile">
              <div className="panel-header">
                <p className="def-label mb-0">Annualized Tracking Error (3Y)</p>
                <StatusBadge
                  status={trackingData?.metrics.trackingErrorStatus || "INSUFFICIENT_DATA"}
                />
              </div>
              <div className="mt-2">
                {trackingData?.metrics.trackingErrorStatus === "CALCULATED" ? (
                  <p className="data-value-lg">
                    {formatPct(trackingData.metrics.trackingErrorAnnualized)}
                  </p>
                ) : (
                  <InsufficientMetric
                    status={trackingData?.metrics.trackingErrorStatus || "INSUFFICIENT_DATA"}
                  />
                )}
              </div>
              <div className="metric-rule my-3" aria-hidden />
              <div className="space-y-1">
                <div className="flex items-baseline justify-between gap-3">
                  <span className="mono-meta font-sans">Divisor</span>
                  <span className="data-value-sm">
                    {trackingData?.metrics.denominatorConvention || "N - 1 (Unbiased)"}
                  </span>
                </div>
                <div className="flex items-baseline justify-between gap-3">
                  <span className="mono-meta font-sans">Annualization Scale</span>
                  <span className="data-value-sm">
                    √{trackingData?.metrics.periodsPerYear || 252}{" "}
                    ({trackingData?.metrics.annualizationConvention || "Trading Days"})
                  </span>
                </div>
              </div>
            </div>

            <div className="metric-tile">
              <div className="panel-header">
                <p className="def-label mb-0">Information Ratio (3Y)</p>
                <StatusBadge
                  status={trackingData?.metrics.informationRatioStatus || "INSUFFICIENT_DATA"}
                />
              </div>
              <div className="mt-2">
                {trackingData?.metrics.informationRatioStatus === "CALCULATED" ? (
                  <p className="data-value-lg text-approved-fg">
                    {formatRatio(trackingData.metrics.informationRatio)}
                  </p>
                ) : (
                  <InsufficientMetric
                    status={trackingData?.metrics.informationRatioStatus || "INSUFFICIENT_DATA"}
                  />
                )}
              </div>
              <div className="metric-rule my-3" aria-hidden />
              <div className="space-y-1">
                <div className="flex items-baseline justify-between gap-3">
                  <span className="mono-meta font-sans">Formula</span>
                  <span className="data-value-sm">(Active Return / Tracking Error)</span>
                </div>
                <div className="flex items-baseline justify-between gap-3">
                  <span className="mono-meta font-sans">Daily Scaling</span>
                  <span className="data-value-sm">(ē / s_e) × √252</span>
                </div>
              </div>
            </div>
          </div>

          {/* Insufficient data notice for tracking */}
          {isTrackingInsufficient && (
            <div className="state-well mt-4 text-left">
              <p className="eyebrow text-candidate-fg">Observation Threshold Not Met</p>
              <p className="mono-meta mt-1.5 font-sans leading-[1.55]">
                {trackingData?.metrics.pairedObservationsCount ?? 0} paired days found; minimum{" "}
                {trackingData?.metrics.minPairedObservationsRequired ?? 700} required for reliable
                tracking consistency estimation.
              </p>
            </div>
          )}
        </div>
      )}

      {/* ── 4. CRYPTOGRAPHIC PROVENANCE & LINEAGE AUDIT DRAWER ───────────── */}
      <div className="panel-inset mt-4">
        <button
          type="button"
          onClick={() => setShowAuditLineage((prev) => !prev)}
          className="flex w-full items-center justify-between gap-3 p-3.5 text-left transition-colors hover:bg-surface-raised"
          aria-expanded={showAuditLineage}
          aria-controls="audit-lineage-content"
        >
          <span className="flex min-w-0 flex-wrap items-center gap-2">
            <span className="text-[13px] font-medium text-accent">
              Audit Lineage &amp; Cryptographic Provenance
            </span>
            <span className="mono-meta">
              {showAuditLineage
                ? "Click to collapse"
                : "Click to inspect calculation lineage & SHA-256 hashes"}
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
          <div id="audit-lineage-content" className="border-t border-border p-4">
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div className="def-list">
                <div>
                  <p className="def-label">Benchmark Series</p>
                  <p className="def-value font-sans leading-[1.45]">
                    {trackingData?.epistemic.benchmarkLineage ||
                      betaData?.epistemic.benchmarkLineage ||
                      relationshipData?.epistemic.benchmarkLineage ||
                      "Resolved synchronously"}
                  </p>
                </div>
                <div>
                  <p className="def-label">Date Pairing Rule</p>
                  <p className="def-value font-sans leading-[1.45]">
                    Synchronous calendar matching; missing dates excluded with zero interpolation
                  </p>
                </div>
                <div>
                  <p className="def-label">Paired Days Evaluated</p>
                  <p className="def-value">
                    {pairedDays} <span className="text-text-tertiary">/ 700 threshold</span>
                  </p>
                </div>
              </div>

              <div className="def-list">
                <div>
                  <p className="def-label">Knowledge Cutoff</p>
                  <p className="def-value">{knowledgeCutoffTime}</p>
                </div>
                <div>
                  <p className="def-label">Source Artifact SHA-256</p>
                  <p className="def-value select-all break-all text-accent">
                    {relationshipData?.epistemic.sourceArtifactSha256 ||
                      trackingData?.epistemic.sourceArtifactSha256 ||
                      betaData?.epistemic.sourceArtifactSha256 ||
                      "Not available"}
                  </p>
                </div>
                <div>
                  <p className="def-label">Calculation Run ID</p>
                  <p className="def-value">
                    {relationshipData?.epistemic.calculationRunId
                      ? `#${relationshipData.epistemic.calculationRunId}`
                      : betaData?.epistemic.calculationRunId
                        ? `#${betaData.epistemic.calculationRunId}`
                        : trackingData?.epistemic.calculationRunId
                          ? `#${trackingData.epistemic.calculationRunId}`
                          : "Verified Synchronous IPC Run"}
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
