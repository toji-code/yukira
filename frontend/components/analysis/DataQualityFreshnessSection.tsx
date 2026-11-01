"use client";

import React, { useState, useEffect } from "react";
import Link from "next/link";
import { fetchDataQualityFreshness } from "@/lib/api/enrichment";
import { DataQualityFreshnessDto } from "@/types/enrichment";

interface DataQualityFreshnessSectionProps {
  schemeOptionId: number;
  schemeCode?: string;
  schemeName?: string;
}

export function DataQualityFreshnessSection({
  schemeOptionId,
  schemeCode,
  schemeName,
}: DataQualityFreshnessSectionProps) {
  const [data, setData] = useState<DataQualityFreshnessDto | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    const timer = setTimeout(() => {
      if (active) {
        setLoading(true);
        setError(null);
      }
    }, 0);

    fetchDataQualityFreshness(schemeOptionId)
      .then((res) => {
        if (active) {
          setData(res);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (active) {
          setError(
            err instanceof Error
              ? err.message
              : "Failed to load authoritative data quality & freshness evidence."
          );
          setLoading(false);
        }
      });

    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [schemeOptionId]);

  if (loading) {
    return (
      <div className="panel space-y-4 p-5" aria-busy="true" data-testid="quality-freshness-loading">
        <div className="skeleton h-4 w-1/4" />
        <div className="skeleton h-12 w-full" />
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="skeleton h-40 w-full" />
          <div className="skeleton h-40 w-full" />
        </div>
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="state-panel-error p-5" role="alert" data-testid="quality-freshness-error">
        <div className="flex items-center gap-2">
          <span className="font-mono text-xs uppercase tracking-wider text-critical-fg">
            Data Quality Assessment Unavailable
          </span>
        </div>
        <p className="mt-1.5 text-sm text-text-primary">
          {error || "Could not retrieve authoritative freshness and data quality evidence from backend."}
        </p>
        <p className="mono-meta mt-1">
          Option ID: #{schemeOptionId} · Verify backend connectivity and schema option registration.
        </p>
      </div>
    );
  }

  const { navEvidence, holdingsEvidence, metricEligibility, qualityFindings, sevenQuestions } = data;

  // Helper badge styles
  const getFreshnessBadge = (state: string) => {
    switch (state) {
      case "CURRENT":
        return "bg-emerald-500/15 text-emerald-700 border-emerald-500/30 dark:bg-emerald-950/40 dark:text-emerald-300 dark:border-emerald-800";
      case "STALE":
        return "bg-amber-500/15 text-amber-800 border-amber-500/30 dark:bg-amber-950/40 dark:text-amber-300 dark:border-amber-800";
      case "PARTIAL":
        return "bg-amber-500/15 text-amber-800 border-amber-500/30 dark:bg-amber-950/40 dark:text-amber-300 dark:border-amber-800";
      case "INSUFFICIENT_HISTORY":
        return "bg-purple-500/15 text-purple-700 border-purple-500/30 dark:bg-purple-950/40 dark:text-purple-300 dark:border-purple-800";
      default:
        return "bg-surface-raised text-text-secondary border-border";
    }
  };

  const getGovernanceBadge = (tier: string) => {
    switch (tier) {
      case "APPROVED":
        return "bg-emerald-500/15 text-emerald-700 border-emerald-500/30 dark:bg-emerald-950/40 dark:text-emerald-300 dark:border-emerald-800";
      case "VALIDATED":
        return "bg-blue-500/15 text-blue-700 border-blue-500/30 dark:bg-blue-950/40 dark:text-blue-300 dark:border-blue-800";
      default:
        return "bg-purple-500/15 text-purple-700 border-purple-500/30 dark:bg-purple-950/40 dark:text-purple-300 dark:border-purple-800";
    }
  };

  const getEligibilityBadge = (state: string) => {
    switch (state) {
      case "ELIGIBLE":
        return "bg-emerald-500/15 text-emerald-700 border-emerald-500/30 dark:bg-emerald-950/40 dark:text-emerald-300 dark:border-emerald-800";
      case "INSUFFICIENT_HISTORY":
        return "bg-amber-500/15 text-amber-800 border-amber-500/30 dark:bg-amber-950/40 dark:text-amber-300 dark:border-amber-800";
      case "INPUT_UNAVAILABLE":
        return "bg-rose-500/15 text-rose-700 border-rose-500/30 dark:bg-rose-950/40 dark:text-rose-300 dark:border-rose-800";
      default:
        return "bg-surface-raised text-text-secondary border-border";
    }
  };

  return (
    <div className="panel p-5 md:p-6 space-y-6" data-testid="data-quality-freshness-section">
      {/* Header & Epistemic Boundaries */}
      <div className="panel-header border-b border-border pb-4">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <p className="eyebrow text-accent dark:text-[#50d8e9]">Institutional Due Diligence Checkpoint</p>
            <span className="font-mono text-[10.5px] uppercase tracking-wider px-2 py-0.5 rounded border border-border bg-surface-raised text-text-secondary">
              Zero Estimated Math · Point-in-Time Audited
            </span>
          </div>
          <h2 className="mt-1.5 text-h2 text-text-primary">
            Data Quality, Cadence &amp; Freshness Verification
          </h2>
          <p className="mono-meta mt-1">
            {data.schemeName || schemeName} {data.schemeCode || schemeCode ? `(${data.schemeCode || schemeCode})` : ""} · Option #{data.schemeOptionId} ({data.planType} · {data.optionType}) · AMFI: {data.amfiCode || "Not available"} · ISIN: {data.isin || "Not available"}
          </p>
        </div>

        <div className="mono-meta shrink-0 space-y-1 text-left sm:text-right">
          <p>
            Evaluation Cutoff: <strong className="text-text-primary">{data.evaluationDate || "Latest"}</strong>
          </p>
          <p className="text-[11px] text-text-tertiary">
            Knowledge Cutoff: {data.knowledgeCutoff ? new Date(data.knowledgeCutoff).toLocaleString("en-IN", { timeZone: "Asia/Kolkata" }) : "Unbounded"}
          </p>
        </div>
      </div>

      {/* 7 QUESTIONS EXECUTIVE CHECKPOINT */}
      <div className="rounded-xl border border-border bg-surface-raised/40 p-4 dark:bg-[#121213] dark:border-[#2a2a2b]">
        <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
          <h3 className="text-sm font-bold tracking-tight text-text-primary uppercase font-mono">
            Investor Due Diligence: The 7 Definitive Questions
          </h3>
          <span className="font-mono text-[11px] text-text-tertiary">
            Directly Grounded in Empirical Records
          </span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
          {/* Q1 */}
          <div className="rounded-lg border border-border/80 bg-surface p-3 dark:bg-[#1a1a1c] dark:border-[#2a2a2b]/80">
            <span className="font-mono text-[10.5px] text-accent dark:text-[#50d8e9] uppercase tracking-wider block font-semibold">
              Q1 · Latest Market NAV Observation
            </span>
            <p className="mt-1 text-sm font-semibold text-text-primary">
              {sevenQuestions.q1LatestNavObservation}
            </p>
            <p className="mt-1 text-xs text-text-secondary leading-relaxed">
              Based on {navEvidence.observationCount?.toLocaleString()} verified trading observations.
            </p>
          </div>

          {/* Q2 */}
          <div className="rounded-lg border border-border/80 bg-surface p-3 dark:bg-[#1a1a1c] dark:border-[#2a2a2b]/80">
            <span className="font-mono text-[10.5px] text-accent dark:text-[#50d8e9] uppercase tracking-wider block font-semibold">
              Q2 · Latest Holdings Reporting Date
            </span>
            <p className="mt-1 text-sm font-semibold text-text-primary">
              {sevenQuestions.q2LatestHoldingsReportingDate}
            </p>
            <p className="mt-1 text-xs text-text-secondary leading-relaxed">
              Official month-end portfolio snapshot date published by the asset management company.
            </p>
          </div>

          {/* Q3 */}
          <div className="rounded-lg border border-border/80 bg-surface p-3 dark:bg-[#1a1a1c] dark:border-[#2a2a2b]/80">
            <span className="font-mono text-[10.5px] text-accent dark:text-[#50d8e9] uppercase tracking-wider block font-semibold">
              Q3 · Holdings Source Availability Date
            </span>
            <p className="mt-1 text-sm font-semibold text-text-primary">
              {sevenQuestions.q3HoldingsSourceAvailabilityDate}
            </p>
            <p className="mt-1 text-xs text-text-secondary leading-relaxed">
              Point-in-time publication timestamp; strictly separate from ingestion timestamp.
            </p>
          </div>

          {/* Q4 */}
          <div className="rounded-lg border border-border/80 bg-surface p-3 dark:bg-[#1a1a1c] dark:border-[#2a2a2b]/80">
            <span className="font-mono text-[10.5px] text-accent dark:text-[#50d8e9] uppercase tracking-wider block font-semibold">
              Q4 · Cadence Freshness Status
            </span>
            <p className="mt-1 text-sm font-semibold text-text-primary">
              {sevenQuestions.q4IsDataCurrentRelativeToCadence}
            </p>
            <p className="mt-1 text-xs text-text-secondary leading-relaxed">
              NAV is {navEvidence.freshnessState}; Holdings report is {holdingsEvidence.freshnessState}.
            </p>
          </div>

          {/* Q5 */}
          <div className="rounded-lg border border-border/80 bg-surface p-3 dark:bg-[#1a1a1c] dark:border-[#2a2a2b]/80">
            <span className="font-mono text-[10.5px] text-accent dark:text-[#50d8e9] uppercase tracking-wider block font-semibold">
              Q5 · Disclosed Portfolio Completeness
            </span>
            <p className="mt-1 text-sm font-semibold text-text-primary">
              {sevenQuestions.q5CompletenessOfDisclosedHoldings}
            </p>
            <p className="mt-1 text-xs text-text-secondary leading-relaxed">
              Raw unforced sum: {holdingsEvidence.rawSumWeightsPercentage?.toFixed(2)}% ({holdingsEvidence.positionCount} positions). Never normalized to 100%.
            </p>
          </div>

          {/* Q6 */}
          <div className="rounded-lg border border-border/80 bg-surface p-3 dark:bg-[#1a1a1c] dark:border-[#2a2a2b]/80">
            <span className="font-mono text-[10.5px] text-accent dark:text-[#50d8e9] uppercase tracking-wider block font-semibold">
              Q6 · Metric Calculability Status
            </span>
            <p className="mt-1 text-sm font-semibold text-text-primary">
              {sevenQuestions.q6MetricCalculabilityStatus}
            </p>
            <p className="mt-1 text-xs text-text-secondary leading-relaxed">
              Eligibility depends on input availability and strict historical window requirements.
            </p>
          </div>

          {/* Q7 */}
          <div className="rounded-lg border border-border/80 bg-surface p-3 dark:bg-[#1a1a1c] dark:border-[#2a2a2b]/80 md:col-span-2 lg:col-span-3">
            <span className="font-mono text-[10.5px] text-amber-700 dark:text-amber-400 uppercase tracking-wider block font-semibold">
              Q7 · What Evidence or Limitations Should the Investor Investigate?
            </span>
            <p className="mt-1 text-sm font-semibold text-text-primary">
              {sevenQuestions.q7EvidenceOrLimitationsToInvestigate}
            </p>
            <p className="mt-1 text-xs text-text-secondary leading-relaxed">
              Verify partial factsheet disclosure coverage vs full statutory monthly portfolio disclosures before committing capital.
            </p>
          </div>
        </div>
      </div>

      {/* DUAL EVIDENCE COMPARISON: NAV VS HOLDINGS */}
      <div>
        <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
          <h3 className="text-sm font-bold tracking-tight text-text-primary uppercase font-mono">
            Dual Cadence &amp; Provenance Evidence
          </h3>
          <span className="font-mono text-[11px] text-text-tertiary">
            NAV Freshness is Strictly Independent from Holdings Freshness
          </span>
        </div>

        <div className="panel-inset mb-4 border-l-2 border-l-accent p-3 bg-surface-raised text-xs text-text-secondary">
          <div className="flex flex-wrap items-center justify-between gap-2 font-mono text-[11px] mb-1">
            <span className="font-semibold text-text-primary">Cadence Distinction: Reporting Delay vs. Stale NAV</span>
            <span className="status-badge state-approved text-[9.5px]">Cadence Rule</span>
          </div>
          <p className="leading-relaxed">
            <strong className="text-text-primary">AMFI Reporting Lag vs. Stale NAV:</strong> Normal AMFI end-of-day publication exhibits an expected 1–2 business day delay, as well as exchange holiday intervals. An observation series is flagged as <span className="font-mono text-amber-500 font-semibold">STALE</span> only when missing consecutive trading days exceed the 3-business-day threshold or when an investor evaluates a historical backtest cutoff.
          </p>
          <p className="mt-1 leading-relaxed">
            <strong className="text-text-primary">Decoupled Operational Ingestion:</strong> Financial data freshness measures the point-in-time state of official market filings, completely independent of background crawler daemon schedules, API server uptime, or retry queues.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* NAV CADENCE PANEL */}
          <div className="rounded-xl border border-border bg-surface p-4 flex flex-col justify-between dark:bg-[#161617] dark:border-[#2a2a2b]">
            <div>
              <div className="flex items-center justify-between gap-2 pb-2 border-b border-border/60">
                <div className="flex items-center gap-2">
                  <span className="h-2 w-2 rounded-full bg-blue-500" aria-hidden />
                  <h4 className="font-mono text-xs font-bold uppercase tracking-wider text-text-primary">
                    Market NAV History (Daily)
                  </h4>
                </div>
                <span className={`px-2 py-0.5 rounded font-mono text-[10.5px] font-semibold border ${getFreshnessBadge(navEvidence.freshnessState)}`}>
                  {navEvidence.freshnessState}
                </span>
              </div>

              <div className="mt-3 space-y-2 font-mono text-xs">
                <div className="flex justify-between py-1 border-b border-border/30">
                  <span className="text-text-tertiary">Latest Observation Date:</span>
                  <strong className="text-text-primary">{navEvidence.latestNavDate || "Not available"}</strong>
                </div>
                <div className="flex justify-between py-1 border-b border-border/30">
                  <span className="text-text-tertiary">Observation Count:</span>
                  <span className="text-text-primary">{navEvidence.observationCount?.toLocaleString()} dates</span>
                </div>
                <div className="flex justify-between py-1 border-b border-border/30">
                  <span className="text-text-tertiary">Usable Date Range:</span>
                  <span className="text-text-primary">{navEvidence.usableDateRange || "Not available"}</span>
                </div>
                <div className="flex justify-between py-1 border-b border-border/30">
                  <span className="text-text-tertiary">Observation Age:</span>
                  <span className="text-text-primary">
                    {navEvidence.observationAgeDays !== null ? `${navEvidence.observationAgeDays} days` : "Unknown"}
                  </span>
                </div>
                <div className="flex justify-between py-1 border-b border-border/30">
                  <span className="text-text-tertiary">Cadence Policy:</span>
                  <span className="text-text-secondary text-right max-w-[240px] truncate" title={navEvidence.cadencePolicy}>
                    Daily by 23:00 IST (STALE if &gt; 5d)
                  </span>
                </div>
                <div className="flex justify-between py-1 border-b border-border/30">
                  <span className="text-text-tertiary">Source Artifact ID:</span>
                  <span className="text-text-primary">#{navEvidence.sourceArtifactId || "1"}</span>
                </div>
                <div className="flex justify-between py-1 border-b border-border/30">
                  <span className="text-text-tertiary">Cryptographic Hash:</span>
                  <span className="text-text-primary font-mono text-[10.5px] truncate max-w-[180px]" title={navEvidence.sha256Hash || ""}>
                    {navEvidence.sha256Hash || "Not tracked"}
                  </span>
                </div>
              </div>
            </div>

            <div className="mt-4 pt-3 border-t border-border/60">
              <p className="text-xs text-text-secondary leading-relaxed">
                {navEvidence.explanation}
              </p>
              {navEvidence.warnings && navEvidence.warnings.length > 0 && (
                <ul className="mt-2 space-y-1">
                  {navEvidence.warnings.map((w, idx) => (
                    <li key={idx} className="text-[11px] text-amber-700 dark:text-amber-400 font-mono">
                      ⚠ {w}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>

          {/* HOLDINGS CADENCE PANEL */}
          <div className="rounded-xl border border-border bg-surface p-4 flex flex-col justify-between dark:bg-[#161617] dark:border-[#2a2a2b]">
            <div>
              <div className="flex items-center justify-between gap-2 pb-2 border-b border-border/60">
                <div className="flex items-center gap-2">
                  <span className="h-2 w-2 rounded-full bg-emerald-500" aria-hidden />
                  <h4 className="font-mono text-xs font-bold uppercase tracking-wider text-text-primary">
                    Monthly Portfolio Holdings
                  </h4>
                </div>
                <div className="flex items-center gap-1.5">
                  <span className={`px-2 py-0.5 rounded font-mono text-[10.5px] font-semibold border ${getFreshnessBadge(holdingsEvidence.freshnessState)}`}>
                    {holdingsEvidence.freshnessState}
                  </span>
                  <span className={`px-2 py-0.5 rounded font-mono text-[10.5px] font-semibold border ${getFreshnessBadge(holdingsEvidence.coverageState)}`}>
                    {holdingsEvidence.coverageState}
                  </span>
                </div>
              </div>

              <div className="mt-3 space-y-2 font-mono text-xs">
                <div className="flex justify-between py-1 border-b border-border/30">
                  <span className="text-text-tertiary">Latest Reporting Date:</span>
                  <strong className="text-text-primary">{holdingsEvidence.latestReportingDate || "Not available"}</strong>
                </div>
                <div className="flex justify-between py-1 border-b border-border/30">
                  <span className="text-text-tertiary">Availability Date (PIT Cutoff):</span>
                  <span className="text-text-primary" title={holdingsEvidence.availabilityTime || ""}>
                    {holdingsEvidence.availabilityTime ? holdingsEvidence.availabilityTime.split("T")[0] : "Not recorded"}
                  </span>
                </div>
                <div className="flex justify-between py-1 border-b border-border/30">
                  <span className="text-text-tertiary">Ingestion Timestamp:</span>
                  <span className="text-text-primary" title={holdingsEvidence.ingestionTimestamp || ""}>
                    {holdingsEvidence.ingestionTimestamp ? holdingsEvidence.ingestionTimestamp.split("T")[0] : "Not recorded"}
                  </span>
                </div>
                <div className="flex justify-between py-1 border-b border-border/30">
                  <span className="text-text-tertiary">Disclosed Positions:</span>
                  <span className="text-text-primary">{holdingsEvidence.positionCount} positions</span>
                </div>
                <div className="flex justify-between py-1 border-b border-border/30">
                  <span className="text-text-tertiary">Raw Disclosed Sum:</span>
                  <strong className="text-text-primary">
                    {holdingsEvidence.rawSumWeightsPercentage?.toFixed(2)}% (Unforced)
                  </strong>
                </div>
                <div className="flex justify-between py-1 border-b border-border/30">
                  <span className="text-text-tertiary">Unclassified / Residual:</span>
                  <span className="text-text-secondary">{holdingsEvidence.unclassifiedExposurePercentage?.toFixed(2)}%</span>
                </div>
                <div className="flex justify-between py-1 border-b border-border/30">
                  <span className="text-text-tertiary">Historical Snapshots:</span>
                  <span className="text-text-primary font-semibold">
                    {holdingsEvidence.snapshotCount} snapshot{holdingsEvidence.snapshotCount === 1 ? " (drift requires ≥2)" : "s"}
                  </span>
                </div>
                <div className="flex justify-between py-1 border-b border-border/30">
                  <span className="text-text-tertiary">Discovery Status:</span>
                  <span className="text-text-primary font-mono text-[10.5px]">
                    {holdingsEvidence.discoveryStatus}
                  </span>
                </div>
              </div>
            </div>

            <div className="mt-4 pt-3 border-t border-border/60">
              <p className="text-xs text-text-secondary leading-relaxed">
                {holdingsEvidence.explanation}
              </p>
              {holdingsEvidence.warnings && holdingsEvidence.warnings.length > 0 && (
                <ul className="mt-2 space-y-1">
                  {holdingsEvidence.warnings.map((w, idx) => (
                    <li key={idx} className="text-[11px] text-amber-700 dark:text-amber-400 font-mono">
                      ⚠ {w}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* METRIC CALCULABILITY & GOVERNANCE MATRIX */}
      <div>
        <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
          <h3 className="text-sm font-bold tracking-tight text-text-primary uppercase font-mono">
            Analytical Metric Eligibility Matrix
          </h3>
          <span className="font-mono text-[11px] text-text-tertiary">
            Preserves Strict Distinction Between Implemented, Validated &amp; Approved
          </span>
        </div>

        <div className="overflow-x-auto rounded-xl border border-border">
          <table className="w-full text-left text-xs font-mono">
            <thead className="bg-surface-raised border-b border-border text-text-tertiary uppercase text-[10px] tracking-wider">
              <tr>
                <th className="p-3">Metric Code &amp; Name</th>
                <th className="p-3">Governance Tier</th>
                <th className="p-3">Calculability Status</th>
                <th className="p-3">Required Evidence</th>
                <th className="p-3">Available Evidence</th>
                <th className="p-3">Epistemic Determination</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/60 bg-surface dark:bg-[#161617]">
              {metricEligibility.map((item) => (
                <tr key={item.metricCode} className="hover:bg-surface-raised/40 transition-colors">
                  <td className="p-3">
                    <span className="font-bold text-text-primary block">{item.metricCode}</span>
                    <span className="text-text-secondary text-[11px]">{item.metricName}</span>
                  </td>
                  <td className="p-3">
                    <span className={`px-2 py-0.5 rounded text-[10px] uppercase font-semibold border ${getGovernanceBadge(item.governanceTier)}`}>
                      {item.governanceTier}
                    </span>
                  </td>
                  <td className="p-3">
                    <span className={`px-2 py-0.5 rounded text-[10px] uppercase font-semibold border ${getEligibilityBadge(item.eligibilityState)}`}>
                      {item.eligibilityState}
                    </span>
                  </td>
                  <td className="p-3 text-text-secondary max-w-[200px]">
                    {item.requiredEvidence}
                  </td>
                  <td className="p-3 text-text-primary max-w-[180px]">
                    {item.availableEvidence}
                  </td>
                  <td className="p-3 text-text-secondary max-w-[260px]">
                    {item.explanation}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* DATA QUALITY FINDINGS & OPERATIONAL LIMITATIONS */}
      {qualityFindings && qualityFindings.length > 0 && (
        <div>
          <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
            <h3 className="text-sm font-bold tracking-tight text-text-primary uppercase font-mono">
              Empirical Findings &amp; Known Limitations
            </h3>
            <span className="font-mono text-[11px] text-text-tertiary">
              Explains Without Concealing or Artificially Normalizing
            </span>
          </div>

          <div className="space-y-2">
            {qualityFindings.map((finding, idx) => {
              const isLimitation = finding.severity === "LIMITATION";
              const isWarning = finding.severity === "WARNING";

              return (
                <div
                  key={idx}
                  className={`p-3.5 rounded-xl border flex flex-col sm:flex-row sm:items-start justify-between gap-3 ${
                    isLimitation
                      ? "bg-amber-500/5 border-amber-500/30 dark:bg-amber-950/20 dark:border-amber-800/60"
                      : isWarning
                      ? "bg-rose-500/5 border-rose-500/30 dark:bg-rose-950/20 dark:border-rose-800/60"
                      : "bg-surface-raised border-border"
                  }`}
                >
                  <div className="space-y-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-mono text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded bg-surface border border-border text-text-secondary">
                        {finding.category}
                      </span>
                      <span className="font-semibold text-xs text-text-primary">
                        {finding.title}
                      </span>
                    </div>
                    <p className="text-xs text-text-secondary leading-relaxed">
                      {finding.explanation}
                    </p>
                  </div>
                  <span
                    className={`shrink-0 font-mono text-[10px] uppercase font-semibold px-2 py-0.5 rounded border self-start ${
                      isLimitation
                        ? "bg-amber-500/15 text-amber-800 border-amber-500/30 dark:bg-amber-950/40 dark:text-amber-300 dark:border-amber-800"
                        : isWarning
                        ? "bg-rose-500/15 text-rose-800 border-rose-500/30 dark:bg-rose-950/40 dark:text-rose-300 dark:border-rose-800"
                        : "bg-surface text-text-secondary border-border"
                    }`}
                  >
                    {finding.severity}
                  </span>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* QUICK JUMP LINKS FOR AUDIT TRAILS */}
      <div className="pt-3 border-t border-border flex flex-wrap items-center justify-between gap-3">
        <span className="font-mono text-xs text-text-tertiary">
          Corroborate with primary source artifacts:
        </span>
        <div className="flex flex-wrap items-center gap-2 font-mono text-xs">
          <Link
            href="#portfolio"
            className="px-3 py-1.5 rounded-lg border border-border bg-surface hover:border-accent hover:text-accent transition-all dark:hover:border-[#50d8e9] dark:hover:text-[#50d8e9]"
          >
            → Inspect Disclosed Holdings &amp; Drift
          </Link>
          <Link
            href="#provenance"
            className="px-3 py-1.5 rounded-lg border border-border bg-surface hover:border-accent hover:text-accent transition-all dark:hover:border-[#50d8e9] dark:hover:text-[#50d8e9]"
          >
            → Inspect Cryptographic Hashes &amp; Lineage
          </Link>
        </div>
      </div>
    </div>
  );
}
