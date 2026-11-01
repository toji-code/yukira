"use client";

import React, { useState, useEffect } from "react";
import { GroundedAiInterpretation, EvidenceClaim } from "@/types/interpretation";
import { AnalyticalProfileResponse } from "@/types/analysis";
import {
  fetchScoreInterpretation,
  fetchAnalysisInterpretation,
  fetchPortfolioInterpretation,
  generateProfileInterpretation,
  fetchProfileInterpretation,
} from "@/lib/api/interpretation";

interface GroundedAiInterpretationPanelProps {
  profile?: AnalyticalProfileResponse;
  schemeOptionId?: number;
  runId?: number;
  isPortfolio?: boolean;
  onMetricClick?: (
    metricCode: string,
    claimContext?: {
      claimText?: string;
      claimType?: string;
      methodologyStatus?: string;
    }
  ) => void;
  onQuestionInvestigate?: (questionText: string) => void;
}

export function GroundedAiInterpretationPanel({
  profile,
  schemeOptionId,
  runId,
  isPortfolio,
  onMetricClick,
  onQuestionInvestigate,
}: GroundedAiInterpretationPanelProps) {
  const [data, setData] = useState<GroundedAiInterpretation | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<
    "happened" | "returns" | "risks" | "meaning" | "caveats" | "questions"
  >("happened");

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);

    const loadData = async () => {
      try {
        let res: GroundedAiInterpretation | null = null;
        if (profile) {
          res = await generateProfileInterpretation(profile);
        } else if (isPortfolio) {
          res = await fetchPortfolioInterpretation();
        } else if (runId) {
          res = await fetchAnalysisInterpretation(runId);
        } else if (schemeOptionId) {
          res = await fetchProfileInterpretation(schemeOptionId);
        }

        if (active) {
          setData(res);
          setLoading(false);
        }
      } catch (err: unknown) {
        if (active) {
          setError(
            err instanceof Error
              ? err.message
              : "Failed to load grounded AI interpretation."
          );
          setLoading(false);
        }
      }
    };

    loadData();

    return () => {
      active = false;
    };
  }, [
    profile?.context?.runId,
    profile?.context?.horizon,
    profile?.context?.asOfDate,
    schemeOptionId,
    runId,
    isPortfolio,
  ]);

  if (loading) {
    return (
      <div className="panel space-y-4 p-5 md:p-6" aria-busy="true">
        <div className="flex items-center justify-between">
          <div className="skeleton h-4 w-48" />
          <div className="skeleton h-5 w-28 rounded-full" />
        </div>
        <div className="skeleton h-16 w-full" />
        <div className="grid grid-cols-2 gap-4">
          <div className="skeleton h-24 w-full" />
          <div className="skeleton h-24 w-full" />
        </div>
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="panel p-5 bg-surface-midnight border border-border-gold-subtle rounded-2xl" role="region" aria-label="Evidence Interpretation Notice">
        <div className="flex items-start gap-3">
          <div className="w-8 h-8 rounded-lg bg-surface-navy-raised flex items-center justify-center text-primary font-bold text-sm shrink-0">
            AI
          </div>
          <div className="space-y-1">
            <h4 className="font-mono text-xs font-bold uppercase tracking-wider text-text-cream-bright">
              AI Interpretation Notice
            </h4>
            <p className="font-sans text-xs text-text-cream-muted leading-relaxed">
              {error
                ? `The qualitative interpretation service could not be reached (${error}). Quantitative metrics, volatility models, drawdowns, and mathematical evidence above remain fully authoritative and unaffected.`
                : "No automated qualitative narrative is recorded for this evaluation run. All mathematical metrics, drawdowns, and provenance records above are deterministically verified in code."}
            </p>
            <p className="font-mono text-[10.5px] text-text-cream-muted/70 pt-1">
              Deterministic Math Principle: Financial calculations in YUKIRA are never computed, smoothed, or approximated by Large Language Models.
            </p>
          </div>
        </div>
      </div>
    );
  }

  const getStatusBadge = () => {
    if (data.isFallback) {
      return (
        <span className="inline-flex items-center gap-1.5 rounded-full bg-amber-500/10 px-2.5 py-0.5 text-[11px] font-medium text-amber-500 border border-amber-500/20">
          <span className="h-1.5 w-1.5 rounded-full bg-amber-500" />
          Deterministic Rule Synthesis (Fallback)
        </span>
      );
    }
    return (
      <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-500/10 px-2.5 py-0.5 text-[11px] font-medium text-emerald-500 border border-emerald-500/20">
        <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
        Grounded AI ({data.modelProvider || "Gemini Flash"})
      </span>
    );
  };

  const getHorizonBadge = () => {
    const h = data.horizon || "3Y";
    if (h === "3Y") {
      return (
        <span className="inline-flex items-center px-2 py-0.5 rounded text-[10.5px] font-mono font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
          3Y Baseline (Approved)
        </span>
      );
    }
    return (
      <span className="inline-flex items-center px-2 py-0.5 rounded text-[10.5px] font-mono font-semibold bg-amber-500/10 text-amber-400 border border-amber-500/20">
        {h} Multi-Cycle (Candidate)
      </span>
    );
  };

  const getClaimTypeBadge = (type: string) => {
    switch (type) {
      case "OBSERVATION":
        return <span className="px-1.5 py-0.5 rounded text-[9.5px] font-mono font-bold bg-blue-500/15 text-blue-400 border border-blue-500/30">OBSERVATION</span>;
      case "INTERPRETATION":
        return <span className="px-1.5 py-0.5 rounded text-[9.5px] font-mono font-bold bg-indigo-500/15 text-indigo-400 border border-indigo-500/30">INTERPRETATION</span>;
      case "RISK":
        return <span className="px-1.5 py-0.5 rounded text-[9.5px] font-mono font-bold bg-rose-500/15 text-rose-400 border border-rose-500/30">RISK</span>;
      case "LIMITATION":
        return <span className="px-1.5 py-0.5 rounded text-[9.5px] font-mono font-bold bg-amber-500/15 text-amber-400 border border-amber-500/30">LIMITATION</span>;
      default:
        return <span className="px-1.5 py-0.5 rounded text-[9.5px] font-mono font-bold bg-slate-500/15 text-slate-400 border border-slate-500/30">CLAIM</span>;
    }
  };

  return (
    <div className="panel overflow-hidden border border-border bg-surface p-5 md:p-6 rounded-lg space-y-5">
      {/* Header */}
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between border-b border-border pb-4">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="text-[17px] font-bold tracking-[-0.01em] text-text-primary">
              Evidence-Grounded AI Fund Interpretation
            </h3>
            {getStatusBadge()}
            {getHorizonBadge()}
          </div>
          <p className="mt-1 text-[13px] text-text-secondary">
            Deterministic narrative grounded strictly in authoritative backend calculation runs &amp; metrics. Zero financial math in the AI path.
          </p>
        </div>
        <div className="text-right text-[11px] text-text-muted font-mono">
          <div>As of: <span className="text-text-primary">{data.asOfDate || "Latest"}</span></div>
          {data.calculationRunId && <div>Run ID: <span className="text-text-primary">#{data.calculationRunId}</span></div>}
        </div>
      </div>

      {/* Summary Box */}
      <div className="rounded-md border border-border/80 bg-background/50 p-4">
        <div className="flex items-center justify-between">
          <p className="eyebrow text-accent">Executive Interpretation</p>
          <span className="mono-meta text-[11px]">
            Methodology: {data.methodologyVersion || "YUKIRA_PROFILE_V1"}
          </span>
        </div>
        <p className="mt-1 text-[14px] leading-[1.6] font-medium text-text-primary">
          {data.summary}
        </p>
      </div>

      {/* Conflicting Empirical Evidence Callout */}
      {data.conflictingEvidence && data.conflictingEvidence.length > 0 && (
        <div className="rounded border border-amber-500/40 bg-amber-500/10 p-3.5 space-y-2">
          <p className="eyebrow text-amber-400 font-semibold flex items-center gap-1.5">
            <span>⚠</span> Conflicting Empirical Evidence Identified
          </p>
          <ul className="space-y-1.5">
            {data.conflictingEvidence.map((item, idx) => (
              <li key={idx} className="text-[13px] text-text-primary leading-[1.5]">
                • {item}
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Tab Navigation */}
      <div className="flex border-b border-border space-x-1 overflow-x-auto text-[13px] font-medium">
        <button
          onClick={() => setActiveTab("happened")}
          className={`pb-2.5 px-3 border-b-2 whitespace-nowrap transition-colors ${
            activeTab === "happened"
              ? "border-accent text-accent font-semibold"
              : "border-transparent text-text-secondary hover:text-text-primary"
          }`}
        >
          01 Observations &amp; Claims ({data.claims?.length || data.whatHappened?.length || 0})
        </button>
        <button
          onClick={() => setActiveTab("returns")}
          className={`pb-2.5 px-3 border-b-2 whitespace-nowrap transition-colors ${
            activeTab === "returns"
              ? "border-accent text-accent font-semibold"
              : "border-transparent text-text-secondary hover:text-text-primary"
          }`}
        >
          02 Return Dynamics ({data.returnCharacteristics?.length || 0})
        </button>
        <button
          onClick={() => setActiveTab("risks")}
          className={`pb-2.5 px-3 border-b-2 whitespace-nowrap transition-colors ${
            activeTab === "risks"
              ? "border-accent text-accent font-semibold"
              : "border-transparent text-text-secondary hover:text-text-primary"
          }`}
        >
          03 Risk &amp; Downside ({data.riskCharacteristics?.length || data.riskFactors?.length || 0})
        </button>
        <button
          onClick={() => setActiveTab("meaning")}
          className={`pb-2.5 px-3 border-b-2 whitespace-nowrap transition-colors ${
            activeTab === "meaning"
              ? "border-accent text-accent font-semibold"
              : "border-transparent text-text-secondary hover:text-text-primary"
          }`}
        >
          04 Analytical Meaning ({data.interpretation?.length || 0})
        </button>
        <button
          onClick={() => setActiveTab("caveats")}
          className={`pb-2.5 px-3 border-b-2 whitespace-nowrap transition-colors ${
            activeTab === "caveats"
              ? "border-accent text-accent font-semibold"
              : "border-transparent text-text-secondary hover:text-text-primary"
          }`}
        >
          05 Governance &amp; Limitations ({(data.dataQualityCaveats?.length || 0) + (data.invalidationFactors?.length || 0)})
        </button>
        <button
          onClick={() => setActiveTab("questions")}
          className={`pb-2.5 px-3 border-b-2 whitespace-nowrap transition-colors ${
            activeTab === "questions"
              ? "border-accent text-accent font-semibold"
              : "border-transparent text-text-secondary hover:text-text-primary"
          }`}
        >
          06 Investigation Questions ({data.investigationQuestions?.length || 0})
        </button>
      </div>

      {/* Tab Content Panels */}
      <div className="min-h-[160px] pt-1">
        {/* TAB 1: What Happened & Evidence Claims */}
        {activeTab === "happened" && (
          <div className="space-y-4">
            <div className="space-y-2.5">
              <p className="eyebrow text-text-secondary">Directly Observed Factual Results</p>
              <ul className="space-y-2">
                {data.whatHappened?.map((item, idx) => (
                  <li key={idx} className="flex items-start gap-2 text-[13.5px] leading-[1.55] text-text-primary">
                    <span className="mt-1 h-1.5 w-1.5 rounded-full bg-accent flex-shrink-0" />
                    <span>{item}</span>
                  </li>
                ))}
              </ul>
            </div>

            {/* Structured Claim-to-Evidence Association */}
            {data.claims && data.claims.length > 0 && (
              <div className="space-y-2.5 pt-3 border-t border-border/60">
                <p className="eyebrow text-accent">Structured Claim-to-Evidence Traceability</p>
                <div className="space-y-2">
                  {data.claims.map((c: EvidenceClaim, idx: number) => (
                    <div
                      key={idx}
                      className="rounded border border-border/80 bg-background/40 p-3 flex flex-col sm:flex-row sm:items-center justify-between gap-2 text-[13px]"
                    >
                      <div className="flex items-start gap-2.5">
                        <div className="mt-0.5">{getClaimTypeBadge(c.claimType)}</div>
                        <span className="text-text-primary leading-[1.5]">{c.claimText}</span>
                      </div>
                      <div className="flex items-center gap-1.5 self-end sm:self-center flex-shrink-0">
                        {c.supportingMetricCodes?.map((code) => (
                          <button
                            key={code}
                            type="button"
                            onClick={() =>
                              onMetricClick &&
                              onMetricClick(code, {
                                claimText: c.claimText,
                                claimType: c.claimType,
                                methodologyStatus: c.methodologyStatus,
                              })
                            }
                            className="font-mono text-[10.5px] px-2 py-0.5 rounded bg-surface border border-border-gold-subtle text-primary hover:bg-primary/10 transition-colors font-semibold"
                            title={`Inspect evidence for metric ${code}`}
                          >
                            {code} ↗
                          </button>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}

        {/* TAB 2: Return Characteristics & Horizons */}
        {activeTab === "returns" && (
          <div className="space-y-4">
            <div className="space-y-2.5">
              <p className="eyebrow text-text-secondary">Compound Growth &amp; Benchmark-Relative Return Evidence</p>
              <ul className="space-y-2">
                {data.returnCharacteristics && data.returnCharacteristics.length > 0 ? (
                  data.returnCharacteristics.map((item, idx) => (
                    <li key={idx} className="flex items-start gap-2 text-[13.5px] leading-[1.55] text-text-primary">
                      <span className="mt-1 h-1.5 w-1.5 rounded-full bg-emerald-500 flex-shrink-0" />
                      <span>{item}</span>
                    </li>
                  ))
                ) : (
                  <li className="text-[13px] text-text-muted">Return characteristics parsed into standard observations.</li>
                )}
              </ul>
            </div>

            {data.horizonInterpretation && data.horizonInterpretation.length > 0 && (
              <div className="space-y-2 pt-3 border-t border-border/60">
                <p className="eyebrow text-amber-500">Horizon Consistency &amp; Historical Window Evaluation</p>
                <ul className="space-y-2">
                  {data.horizonInterpretation.map((item, idx) => (
                    <li key={idx} className="flex items-start gap-2 text-[13px] text-text-secondary">
                      <span className="mt-1 h-1.5 w-1.5 rounded-full bg-amber-500 flex-shrink-0" />
                      <span>{item}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}

        {/* TAB 3: Risk Characteristics & Downside */}
        {activeTab === "risks" && (
          <div className="space-y-4">
            {data.conflictingEvidence && data.conflictingEvidence.length > 0 && (
              <div className="rounded border border-amber-500/40 bg-amber-500/10 p-3.5 space-y-2">
                <p className="eyebrow text-amber-400 font-semibold flex items-center gap-1.5">
                  <span>⚠</span> Conflicting Empirical Evidence
                </p>
                <ul className="space-y-1.5">
                  {data.conflictingEvidence.map((item, idx) => (
                    <li key={idx} className="text-[13px] text-text-primary leading-[1.5]">
                      • {item}
                    </li>
                  ))}
                </ul>
              </div>
            )}
            <div className="space-y-2.5">
              <p className="eyebrow text-rose-400">Total Volatility, Downside Drawdown &amp; Systematic Sensitivity</p>
              <ul className="space-y-2">
                {data.riskCharacteristics && data.riskCharacteristics.length > 0 ? (
                  data.riskCharacteristics.map((item, idx) => (
                    <li key={idx} className="flex items-start gap-2 text-[13.5px] leading-[1.55] text-text-primary">
                      <span className="mt-1 h-1.5 w-1.5 rounded-full bg-rose-500 flex-shrink-0" />
                      <span>{item}</span>
                    </li>
                  ))
                ) : (
                  data.riskFactors?.map((item, idx) => (
                    <li key={idx} className="flex items-start gap-2 text-[13.5px] leading-[1.55] text-text-primary">
                      <span className="mt-1 h-1.5 w-1.5 rounded-full bg-rose-500 flex-shrink-0" />
                      <span>{item}</span>
                    </li>
                  ))
                )}
              </ul>
            </div>

            {data.riskFactors && data.riskFactors.length > 0 && (
              <div className="space-y-2 pt-3 border-t border-border/60">
                <p className="eyebrow text-text-muted">Structural Tail Risks &amp; Macro Vulnerabilities</p>
                <ul className="space-y-1.5">
                  {data.riskFactors.map((item, idx) => (
                    <li key={idx} className="text-[12.5px] text-text-secondary italic">
                      • {item}
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}

        {/* TAB 4: Analytical Meaning */}
        {activeTab === "meaning" && (
          <div className="space-y-2.5">
            <p className="eyebrow text-indigo-400">Quantitative Interpretations Supported by Evidence</p>
            <ul className="space-y-2">
              {data.interpretation?.map((item, idx) => (
                <li key={idx} className="flex items-start gap-2 text-[13.5px] leading-[1.55] text-text-primary">
                  <span className="mt-1 h-1.5 w-1.5 rounded-full bg-indigo-500 flex-shrink-0" />
                  <span>{item}</span>
                </li>
              ))}
            </ul>
          </div>
        )}

        {/* TAB 5: Data Quality & Limitations */}
        {activeTab === "caveats" && (
          <div className="space-y-4">
            <div className="space-y-2">
              <p className="eyebrow text-text-secondary">Point-in-Time Data Quality &amp; Governance Limitations</p>
              <ul className="space-y-2">
                {data.dataQualityCaveats?.map((item, idx) => (
                  <li key={idx} className="flex items-start gap-2 text-[13px] text-text-secondary">
                    <span className="mt-1 h-1.5 w-1.5 rounded-full bg-slate-400 flex-shrink-0" />
                    <span>{item}</span>
                  </li>
                ))}
              </ul>
            </div>

            {data.invalidationFactors && data.invalidationFactors.length > 0 && (
              <div className="space-y-2 border-t border-border/60 pt-3">
                <p className="eyebrow text-critical-fg">Factors That Would Invalidate This Interpretation</p>
                <ul className="space-y-1.5">
                  {data.invalidationFactors.map((item, idx) => (
                    <li key={idx} className="text-[13px] text-text-secondary italic">
                      • {item}
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}

        {/* TAB 6: Investigation Questions */}
        {activeTab === "questions" && (
          <div className="space-y-3">
            <p className="eyebrow text-emerald-400">Prioritized Allocator Due-Diligence Questions</p>
            <p className="text-[12.5px] text-text-secondary">
              Derived directly from observed metric divergences, systematic beta sensitivities, and historical drawdowns.
            </p>
            <div className="space-y-2.5">
              {data.investigationQuestions?.map((item, idx) => (
                <div
                  key={idx}
                  className="rounded border border-border bg-background/50 p-3.5 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-[13.5px] leading-[1.5] text-text-primary hover:border-border-gold-subtle transition-all"
                >
                  <div className="flex items-start gap-2.5">
                    <span className="font-mono font-bold text-accent">Q{idx + 1}.</span>
                    <span>{item}</span>
                  </div>
                  {onQuestionInvestigate && (
                    <button
                      type="button"
                      onClick={() => onQuestionInvestigate(item)}
                      className="px-3 py-1 rounded bg-surface border border-border text-xs text-text-secondary hover:text-text-primary hover:border-primary/50 self-end sm:self-center flex-shrink-0 font-medium transition-colors"
                    >
                      Save to Notes ↗
                    </button>
                  )}
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Non-Advisory Institutional Disclaimer Footer */}
      <div className="border-t border-border/70 pt-3 text-[11px] text-text-muted flex flex-col sm:flex-row sm:items-center justify-between gap-2">
        <div>
          <span className="font-semibold uppercase tracking-wider text-amber-500/90 mr-1">
            Institutional Epistemic Boundary:
          </span>
          Point-in-time calculation snapshot. Non-advisory output. AI does not predict returns or issue Buy/Hold/Sell ratings.
        </div>
        <div className="font-mono text-[10.5px]">
          Epistemic Status: <span className="text-text-primary">{data.epistemicStatus}</span>
        </div>
      </div>
    </div>
  );
}
