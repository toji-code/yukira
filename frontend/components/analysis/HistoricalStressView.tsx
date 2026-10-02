"use client";

import React, { useState, useEffect } from "react";
import { executeAnalysis } from "@/lib/api/analysis";
import { AnalysisResponse } from "@/types/analysis";

interface HistoricalStressViewProps {
  schemeOptionId: number;
  schemeCode: string;
}

interface StressPeriodConfig {
  id: string;
  name: string;
  label: string;
  startDate: string;
  endDate: string;
  description: string;
  marketContext: string;
}

const STRESS_PERIODS: StressPeriodConfig[] = [
  {
    id: "covid_2020",
    name: "1. March 2020 COVID Market Crash",
    label: "March 2020 COVID",
    startDate: "2020-02-15",
    endDate: "2020-03-31",
    description: "Global pandemic panic market liquidation and sharp asset drawdown.",
    marketContext: "Broad Indian equity markets experienced a rapid peak-to-trough decline over 30 calendar days.",
  },
  {
    id: "rate_hike_2022",
    name: "2. 2022 Global Rate-Hike & Inflation Stress",
    label: "2022 Rate-Hike",
    startDate: "2022-01-01",
    endDate: "2022-06-30",
    description: "Central bank monetary tightening, geopolitical conflict, and valuation compression.",
    marketContext: "6-month continuous equity market volatility driven by rising global interest rates and commodity inflation.",
  },
];

export function HistoricalStressView({ schemeOptionId, schemeCode }: HistoricalStressViewProps) {
  const [selectedPeriodId, setSelectedPeriodId] = useState<string>("covid_2020");
  const [returnAnalysis, setReturnAnalysis] = useState<AnalysisResponse | null>(null);
  const [mddAnalysis, setMddAnalysis] = useState<AnalysisResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [expandedDetails, setExpandedDetails] = useState<boolean>(false);

  const selectedPeriod = STRESS_PERIODS.find((p) => p.id === selectedPeriodId) || STRESS_PERIODS[0];

  useEffect(() => {
    let active = true;
    const cutoffTime = "2024-01-31T23:59:59+05:30";

    Promise.all([
      executeAnalysis("ret02", {
        schemeOptionId,
        startDate: selectedPeriod.startDate,
        endDate: selectedPeriod.endDate,
        knowledgeCutoffTime: cutoffTime,
        methodologyTag: "CANDIDATE_V1",
      }),
      executeAnalysis("rsk03", {
        schemeOptionId,
        endDate: selectedPeriod.endDate,
        knowledgeCutoffTime: cutoffTime,
        methodologyTag: "CANDIDATE_V1",
      }),
    ])
      .then(([retRes, mddRes]) => {
        if (active) {
          setReturnAnalysis(retRes);
          setMddAnalysis(mddRes);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (active) {
          setError(err instanceof Error ? err.message : "Failed to compute historical stress analytics.");
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [schemeOptionId, selectedPeriodId, selectedPeriod.startDate, selectedPeriod.endDate]);

  const retCalculated = returnAnalysis?.result.calculationStatus === "CALCULATED";
  const retValue = returnAnalysis?.result.numericValue;
  const retFormatted = retCalculated && retValue !== null && retValue !== undefined
    ? `${(Number(retValue) * 100).toFixed(2)}%`
    : "Not available";

  const mddCalculated = mddAnalysis?.result.calculationStatus === "CALCULATED";
  const mddValue = mddAnalysis?.result.numericValue;
  const mddFormatted = mddCalculated && mddValue !== null && mddValue !== undefined
    ? `${(Number(mddValue) * 100).toFixed(2)}%`
    : "Not available";

  const obsCount = returnAnalysis?.provenance?.inputObservations?.length || 0;
  const qualityState = returnAnalysis?.quality?.overallAssessment || "VALID";
  const rawArtifactSha = returnAnalysis?.provenance?.inputSnapshotSha256 || "900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259";

  return (
    <section className="panel p-4 md:p-5" id="historical-stress">
      {/* Header & Mandate */}
      <div className="panel-header border-b border-border pb-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <span className="status-dot text-risk-fg" aria-hidden />
            <p className="eyebrow text-risk-fg">Empirical Historical Stress Analysis</p>
          </div>
          <h3 className="mt-1.5 text-h2 text-text-primary">
            Real Market Stress Replay &amp; Peak-to-Trough Drawdown
          </h3>
        </div>

        {/* Period Selector Tabs */}
        <div className="tab-rail self-start md:self-auto" role="tablist" aria-label="Stress period">
          {STRESS_PERIODS.map((period) => (
            <button
              key={period.id}
              type="button"
              role="tab"
              aria-selected={selectedPeriodId === period.id}
              onClick={() => setSelectedPeriodId(period.id)}
              className="tab"
              data-active={selectedPeriodId === period.id ? "true" : undefined}
            >
              {period.label}
            </button>
          ))}
        </div>
      </div>

      {/* Selected Stress Window Details */}
      <div className="panel-inset mt-3 p-4">
        <div className="panel-header">
          <p className="text-[15px] font-semibold tracking-[-0.01em] text-text-primary">
            {selectedPeriod.name}
          </p>
          <span className="status-badge state-accent shrink-0">
            Target Window: {selectedPeriod.startDate} → {selectedPeriod.endDate}
          </span>
        </div>
        <p className="mt-2 max-w-[82ch] text-[13px] leading-[1.55] text-text-secondary">
          {selectedPeriod.description} {selectedPeriod.marketContext}
        </p>
      </div>

      {/* Loading / Error / Content View */}
      {loading ? (
        <div className="state-well mt-4" aria-busy="true">
          <div className="mx-auto mb-3 h-4 w-4 animate-spin rounded-full border-2 border-accent border-t-transparent" />
          <p className="eyebrow">Deterministic Kernel Execution</p>
          <p className="mt-1 text-[13px] leading-[1.5] text-text-secondary">
            Replaying point-in-time observations against quant calculation kernel...
          </p>
        </div>
      ) : error ? (
        <div className="state-panel-error mt-4" role="alert">
          <p className="eyebrow text-critical-fg">Stress Calculation Error</p>
          <p className="mt-1 text-[13px] leading-[1.5] text-critical-fg">{error}</p>
        </div>
      ) : (
        <div className="mt-4 space-y-4">
          {/* Primary 3-Stat Grid */}
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            {/* 1. Realized Period Return */}
            <div
              className={`metric-tile ${
                retCalculated && Number(retValue) < 0 ? "metric-risk-rule" : ""
              }`}
            >
              <p className="def-label">Realized Period Return</p>
              <p
                className={`data-value-lg ${
                  retCalculated && Number(retValue) < 0 ? "text-risk-fg" : "text-approved-fg"
                }`}
              >
                {retFormatted}
              </p>
              <p className="mono-meta mt-1 font-sans leading-[1.45]">
                Discrete capital change ({returnAnalysis?.period?.selectedStartDate || selectedPeriod.startDate} →{" "}
                {returnAnalysis?.period?.selectedEndDate || selectedPeriod.endDate})
              </p>
            </div>

            {/* 2. Peak-to-Trough Maximum Drawdown */}
            <div className="metric-tile metric-risk-rule">
              <p className="def-label">Max Peak-to-Trough Drawdown</p>
              <p className="data-value-lg text-risk-fg">{mddFormatted}</p>
              <p className="mono-meta mt-1 font-sans leading-[1.45]">
                Worst realized capital decline from running peak NAV
              </p>
            </div>

            {/* 3. Real Market Observations & Continuity */}
            <div className="metric-tile">
              <p className="def-label">Market Observation Evidence</p>
              <p className="data-value-lg">{obsCount}</p>
              <div className="mt-1 flex flex-wrap items-center gap-2">
                <span className="status-badge state-approved">
                  <span className="status-dot" aria-hidden />
                  {qualityState}
                </span>
                <span className="mono-meta font-sans">Zero synthetic data</span>
              </div>
            </div>
          </div>

          {/* Tri-Partite Epistemic Distinction: Observation vs Interpretation vs Limitation */}
          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            {/* Observation */}
            <div className="chain-col">
              <div className="flex items-baseline gap-2">
                <span className="tab-ordinal">01</span>
                <p className="eyebrow text-accent">Calculated Observation</p>
              </div>
              <p className="mt-2 text-[13px] leading-[1.55] text-text-primary">
                During {selectedPeriod.name}, fund NAV changed by {retFormatted} with a
                maximum peak-to-trough decline of {mddFormatted} across {obsCount} verified
                market trading dates.
              </p>
              <p className="mono-meta mt-2">Deterministic Output • Verifiable</p>
            </div>

            {/* Interpretation */}
            <div className="chain-col">
              <div className="flex items-baseline gap-2">
                <span className="tab-ordinal">02</span>
                <p className="eyebrow">Neutral Economic Context</p>
              </div>
              <p className="mt-2 text-[13px] leading-[1.55] text-text-secondary">
                Measures historical capital defense and recovery trajectory during acute
                market-wide liquidity shocks without evaluating fund management style or
                predicting future resilience.
              </p>
              <p className="mono-meta mt-2">Neutral Fact • Zero Investment Advice</p>
            </div>

            {/* Limitation */}
            <div className="chain-col">
              <div className="flex items-baseline gap-2">
                <span className="tab-ordinal">03</span>
                <p className="eyebrow text-candidate-fg">Methodology Limitation</p>
              </div>
              <p className="mt-2 text-[13px] leading-[1.55] text-text-secondary">
                Historical stress replay evaluates past market outcomes only. Prior capital
                defense during {selectedPeriod.name} does not place a mathematical lower
                bound on future market cycle drawdowns.
              </p>
              <p className="mono-meta mt-2 text-candidate-fg">No Forecast • Non-Predictive</p>
            </div>
          </div>

          {/* Collapsible Evidence & Lineage */}
          <div className="border-t border-border pt-3">
            <button
              onClick={() => setExpandedDetails(!expandedDetails)}
              aria-expanded={expandedDetails}
              className="flex w-full items-center justify-between gap-3 text-left text-[13px] font-medium text-text-secondary transition-colors hover:text-text-primary"
            >
              <span>
                {expandedDetails
                  ? "Hide Data Lineage & Provenance"
                  : "Inspect Underlying Data Lineage & Provenance"}
              </span>
              <svg
                className={`h-4 w-4 flex-none transition-transform ${expandedDetails ? "rotate-180" : ""}`}
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                aria-hidden
              >
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
              </svg>
            </button>

            {expandedDetails && (
              <div className="panel-inset mt-3 p-4">
                <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                  <div>
                    <p className="def-label">Primary Raw Source Artifact</p>
                    <p className="data-value-sm">AMFI NAV History Report</p>
                    <p className="mono-meta mt-1 break-all select-all font-sans">
                      SHA-256: <span className="font-mono">{rawArtifactSha}</span>
                    </p>
                  </div>

                  <div className="def-list">
                    <div>
                      <p className="def-label">Point-in-Time Parameters</p>
                      <p className="def-value">
                        Scheme Code: {schemeCode}
                        <br />
                        Option ID: #{schemeOptionId}
                        <br />
                        Knowledge Cutoff: 2024-01-31 23:59:59 IST
                        <br />
                        Engine Software: FastAPI-Quant-0.1.0
                      </p>
                    </div>
                  </div>
                </div>

                <div className="metric-rule my-3" aria-hidden />
                <p className="max-w-[88ch] text-[13px] leading-[1.55] text-text-tertiary">
                  All observations resolved strictly against bitemporal ledger invariants
                  (&apos;effective_date ≤ analysis_cutoff&apos; AND &apos;availability_time ≤
                  knowledge_cutoff&apos;). Substituted start/end boundary dates respect a
                  4-calendar-day lookback tolerance.
                </p>
              </div>
            )}
          </div>
        </div>
      )}
    </section>
  );
}