"use client";

import React, { useState, useEffect } from "react";
import { GroundedAiInterpretation } from "@/types/interpretation";
import {
  fetchScoreInterpretation,
  fetchAnalysisInterpretation,
  fetchPortfolioInterpretation,
} from "@/lib/api/interpretation";

interface GroundedAiInterpretationPanelProps {
  schemeOptionId?: number;
  runId?: number;
  isPortfolio?: boolean;
}

export function GroundedAiInterpretationPanel({
  schemeOptionId,
  runId,
  isPortfolio,
}: GroundedAiInterpretationPanelProps) {
  const [data, setData] = useState<GroundedAiInterpretation | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<
    "happened" | "meaning" | "risks" | "caveats" | "questions"
  >("happened");

  useEffect(() => {
    let active = true;

    const loadData = async () => {
      try {
        let res: GroundedAiInterpretation | null = null;
        if (isPortfolio) {
          res = await fetchPortfolioInterpretation();
        } else if (runId) {
          res = await fetchAnalysisInterpretation(runId);
        } else if (schemeOptionId) {
          res = await fetchScoreInterpretation(schemeOptionId);
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
  }, [schemeOptionId, runId, isPortfolio]);

  if (loading) {
    return (
      <div className="panel space-y-4 p-4 md:p-6" aria-busy="true">
        <div className="flex items-center justify-between">
          <div className="skeleton h-4 w-48" />
          <div className="skeleton h-5 w-24 rounded-full" />
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
      <div className="state-panel-error" role="alert">
        <p className="text-[15px] font-semibold tracking-[-0.01em] text-text-primary">
          AI Interpretation Unavailable
        </p>
        <p className="mt-1 text-[13px] leading-[1.5] text-critical-fg">
          {error || "No quantitative evidence interpretation recorded for this evaluation run."}
        </p>
      </div>
    );
  }

  const getStatusBadge = () => {
    if (data.isFallback) {
      return (
        <span className="inline-flex items-center gap-1.5 rounded-full bg-amber-500/10 px-2.5 py-0.5 text-[11px] font-medium text-amber-500 border border-amber-500/20">
          <span className="h-1.5 w-1.5 rounded-full bg-amber-500" />
          Deterministic Fallback Engine
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

  return (
    <div className="panel overflow-hidden border border-border bg-surface p-5 md:p-6 rounded-lg space-y-5">
      {/* Header */}
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between border-b border-border pb-4">
        <div>
          <div className="flex items-center gap-2">
            <h3 className="text-[17px] font-bold tracking-[-0.01em] text-text-primary">
              YUKIRA Grounded AI Interpretation
            </h3>
            {getStatusBadge()}
          </div>
          <p className="mt-1 text-[13px] text-text-secondary">
            Plain-language analysis grounded strictly in authoritative backend calculation runs & metrics. Zero fabricated numbers.
          </p>
        </div>
        <div className="text-right text-[11px] text-text-muted font-mono">
          <div>As of: {data.asOfDate || "Latest"}</div>
          {data.calculationRunId && <div>Run ID: #{data.calculationRunId}</div>}
        </div>
      </div>

      {/* Summary Box */}
      <div className="rounded-md border border-border/80 bg-background/50 p-4">
        <p className="eyebrow text-accent">Synthesis Overview</p>
        <p className="mt-1 text-[14px] leading-[1.6] font-medium text-text-primary">
          {data.summary}
        </p>
      </div>

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
          01 What Happened ({data.whatHappened?.length || 0})
        </button>
        <button
          onClick={() => setActiveTab("meaning")}
          className={`pb-2.5 px-3 border-b-2 whitespace-nowrap transition-colors ${
            activeTab === "meaning"
              ? "border-accent text-accent font-semibold"
              : "border-transparent text-text-secondary hover:text-text-primary"
          }`}
        >
          02 What It Means ({data.interpretation?.length || 0})
        </button>
        <button
          onClick={() => setActiveTab("risks")}
          className={`pb-2.5 px-3 border-b-2 whitespace-nowrap transition-colors ${
            activeTab === "risks"
              ? "border-accent text-accent font-semibold"
              : "border-transparent text-text-secondary hover:text-text-primary"
          }`}
        >
          03 Key Risk Factors ({data.riskFactors?.length || 0})
        </button>
        <button
          onClick={() => setActiveTab("caveats")}
          className={`pb-2.5 px-3 border-b-2 whitespace-nowrap transition-colors ${
            activeTab === "caveats"
              ? "border-accent text-accent font-semibold"
              : "border-transparent text-text-secondary hover:text-text-primary"
          }`}
        >
          04 Data Quality & Invalidation ({ (data.dataQualityCaveats?.length || 0) + (data.invalidationFactors?.length || 0) })
        </button>
        <button
          onClick={() => setActiveTab("questions")}
          className={`pb-2.5 px-3 border-b-2 whitespace-nowrap transition-colors ${
            activeTab === "questions"
              ? "border-accent text-accent font-semibold"
              : "border-transparent text-text-secondary hover:text-text-primary"
          }`}
        >
          05 Allocator Questions ({data.investigationQuestions?.length || 0})
        </button>
      </div>

      {/* Tab Content Panels */}
      <div className="min-h-[160px] pt-1">
        {activeTab === "happened" && (
          <div className="space-y-2.5">
            <p className="eyebrow text-text-secondary">Factual Quantitative Observations</p>
            <ul className="space-y-2">
              {data.whatHappened?.map((item, idx) => (
                <li key={idx} className="flex items-start gap-2 text-[13.5px] leading-[1.55] text-text-primary">
                  <span className="mt-1 h-1.5 w-1.5 rounded-full bg-accent flex-shrink-0" />
                  <span>{item}</span>
                </li>
              ))}
            </ul>
          </div>
        )}

        {activeTab === "meaning" && (
          <div className="space-y-2.5">
            <p className="eyebrow text-text-secondary">Analytical Significance for Allocators</p>
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

        {activeTab === "risks" && (
          <div className="space-y-2.5">
            <p className="eyebrow text-amber-500">Exposed Risk Factors & Drawdown Vulnerabilities</p>
            <ul className="space-y-2">
              {data.riskFactors?.map((item, idx) => (
                <li key={idx} className="flex items-start gap-2 text-[13.5px] leading-[1.55] text-text-primary">
                  <span className="mt-1 h-1.5 w-1.5 rounded-full bg-amber-500 flex-shrink-0" />
                  <span>{item}</span>
                </li>
              ))}
            </ul>
          </div>
        )}

        {activeTab === "caveats" && (
          <div className="space-y-4">
            <div className="space-y-2">
              <p className="eyebrow text-text-secondary">Data Quality Taxonomies & Limitations</p>
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
                <p className="eyebrow text-critical-fg">What Could Invalidate This Interpretation</p>
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

        {activeTab === "questions" && (
          <div className="space-y-2.5">
            <p className="eyebrow text-emerald-500">Targeted Due-Diligence Questions for Allocator Audit</p>
            <ul className="space-y-2.5">
              {data.investigationQuestions?.map((item, idx) => (
                <li key={idx} className="rounded border border-border bg-background/40 p-3 text-[13.5px] leading-[1.5] text-text-primary">
                  <span className="font-semibold text-accent mr-1.5">Q{idx + 1}.</span>
                  {item}
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>

      {/* Non-Advisory Institutional Disclaimer Footer */}
      <div className="border-t border-border/70 pt-3 text-[11px] text-text-muted flex flex-col sm:flex-row sm:items-center justify-between gap-2">
        <div>
          <span className="font-semibold uppercase tracking-wider text-amber-500/90 mr-1">Institutional Governance Disclaimer:</span>
          Grounding methodology version: <code className="font-mono">{data.methodologyVersion || "YUKIRA_SCORE_V1"}</code>. Non-advisory output.
        </div>
        <div className="font-mono text-[10px]">
          Epistemic State: <span className="text-text-primary">{data.epistemicStatus}</span>
        </div>
      </div>
    </div>
  );
}
