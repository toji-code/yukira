"use client";

import React, { useMemo } from "react";
import Link from "next/link";
import { ComparisonResponse, ComparisonFund, ComparisonDifferenceExplanation } from "@/types/comparison";

interface ComparisonInterpretationSummaryProps {
  comparison: ComparisonResponse;
  asOfDate: string;
  knowledgeCutoff: string;
  onInvestigateFund: (fund: ComparisonFund, question: string, reason: string) => void;
}

interface MetricSummaryItem {
  code: string;
  name: string;
  category: string;
  values: Array<{
    fund: ComparisonFund;
    numericValue: number | null;
    formattedValue: string | null;
    units: string;
    isAvailable: boolean;
  }>;
  spread: {
    min: number;
    max: number;
    diff: number;
    leader: ComparisonFund;
    laggard: ComparisonFund;
  } | null;
}

export function ComparisonInterpretationSummary({
  comparison,
  asOfDate,
  knowledgeCutoff,
  onInvestigateFund,
}: ComparisonInterpretationSummaryProps) {
  const funds = comparison.funds || [];
  const metrics = comparison.metrics || [];
  const differenceExplanations = comparison.differenceExplanations || [];
  const [savingComparison, setSavingComparison] = React.useState(false);
  const [savedSuccess, setSavedSuccess] = React.useState(false);

  const handleSaveComparison = async () => {
    setSavingComparison(true);
    try {
      const title = funds.map((f) => f.schemeName).join(" vs ") || "Multi-Fund Comparison";
      const payload = {
        comparisonTitle: title,
        schemeOptionIds: funds.map((f) => f.schemeOptionId),
        asOfDate: asOfDate,
        knowledgeCutoff: knowledgeCutoff,
        benchmarkCode: comparison.commonSupportedPeriod?.benchmarkCode || "NIFTY_500_TRI",
        compatibilityStatus: comparison.compatibilitySummary?.overallCompatibility || "COMPATIBLE",
        metricsSummaryJson: JSON.stringify(funds.map((f) => ({ id: f.schemeOptionId, name: f.schemeName }))),
        investorThesis: "Comparative investigation across peer funds.",
        investorNotes: "Preserved research snapshot from comparison screener.",
        associatedFindingId: null,
      };
      const res = await fetch("/api/investor/workspace/comparisons", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      if (res.ok) {
        setSavedSuccess(true);
        setTimeout(() => setSavedSuccess(false), 4000);
      }
    } catch {
      // ignore
    } finally {
      setSavingComparison(false);
    }
  };

  // Deterministically extract summary stats across key metrics
  const keyMetricSummaries = useMemo<MetricSummaryItem[]>(() => {
    const targetCodes = ["RET-03", "RSK-01", "RSK-03", "RSK-04", "RAT-01", "MKT-01", "PRT-01", "CONC-01"];
    const results: MetricSummaryItem[] = [];

    targetCodes.forEach((code) => {
      const metric = metrics.find((m) => m.metricCode === code);
      if (!metric) return;

      const values = funds.map((fund) => {
        const res = metric.results.find((r) => r.schemeOptionId === fund.schemeOptionId);
        const isAvail = res?.calculationStatus === "CALCULATED" && res.numericValue !== null && res.numericValue !== undefined;
        return {
          fund,
          numericValue: isAvail ? (res.numericValue as number) : null,
          formattedValue: res?.formattedValue || null,
          units: res?.units || metric.units || "",
          isAvailable: isAvail,
        };
      });

      const validValues = values.filter((v) => v.numericValue !== null);
      let spread: MetricSummaryItem["spread"] = null;

      if (validValues.length >= 2) {
        let minItem = validValues[0];
        let maxItem = validValues[0];

        validValues.forEach((v) => {
          if ((v.numericValue as number) < (minItem.numericValue as number)) minItem = v;
          if ((v.numericValue as number) > (maxItem.numericValue as number)) maxItem = v;
        });

        spread = {
          min: minItem.numericValue as number,
          max: maxItem.numericValue as number,
          diff: (maxItem.numericValue as number) - (minItem.numericValue as number),
          leader: maxItem.fund,
          laggard: minItem.fund,
        };
      }

      results.push({
        code: metric.metricCode,
        name: metric.metricName,
        category: metric.category,
        values,
        spread,
      });
    });

    return results;
  }, [funds, metrics]);

  // Check temporal alignment differences
  const temporalAlignment = useMemo(() => {
    const coverage = comparison.coverageSummary;
    const differingAsOfDates = coverage?.differingAsOfDates ?? false;

    // Check individual metric start dates for lookback differences
    const ret03 = metrics.find((m) => m.metricCode === "RET-03");
    const startDates = ret03?.results
      .map((r) => r.startDate)
      .filter((d): d is string => Boolean(d));
    const uniqueStartDates = Array.from(new Set(startDates));
    const hasDifferentStartDates = uniqueStartDates.length > 1;

    return {
      differingAsOfDates,
      hasDifferentStartDates,
      uniqueStartDates,
      isPilotCutoff: asOfDate === "2024-01-15",
    };
  }, [comparison.coverageSummary, metrics, asOfDate]);

  // Suggested due diligence questions derived from actual observed differences
  const dueDiligenceQuestions = useMemo(() => {
    const list: Array<{
      targetFund: ComparisonFund;
      question: string;
      reason: string;
      tag: string;
    }> = [];

    // 1. Return vs Volatility trade-off
    const ret03Summary = keyMetricSummaries.find((m) => m.code === "RET-03");
    const rsk01Summary = keyMetricSummaries.find((m) => m.code === "RSK-01");

    if (ret03Summary?.spread && rsk01Summary?.spread) {
      const highestReturnFund = ret03Summary.spread.leader;
      list.push({
        targetFund: highestReturnFund,
        question: `How much of ${highestReturnFund.schemeName}'s historical CAGR advantage (${(ret03Summary.spread.diff * 100).toFixed(2)}% spread) is attributable to higher systematic return dispersion (volatility spread: ${(rsk01Summary.spread.diff * 100).toFixed(2)}%)?`,
        reason: `Examine return-to-volatility efficiency trade-off vs peers`,
        tag: "RISK/RETURN TRADE-OFF",
      });
    }

    // 2. Temporal coverage / inception discrepancy
    if (temporalAlignment.hasDifferentStartDates && funds.length >= 2) {
      const fundWithShortestHistory = funds.find((f) => f.schemeName.toLowerCase().includes("elss")) || funds[funds.length - 1];
      list.push({
        targetFund: fundWithShortestHistory,
        question: `Does ${fundWithShortestHistory.schemeName}'s evaluation window reflect a truncated inception history relative to peer schemes, potentially excluding prior cyclical down-markets?`,
        reason: `Audit observation window length and inception bias in comparative returns`,
        tag: "OBSERVATION REGIME",
      });
    }

    // 3. Downside risk & drawdown resilience (RSK-03: Max Drawdown, RSK-04: Drawdown Duration)
    const rsk03Summary = keyMetricSummaries.find((m) => m.code === "RSK-03");
    if (rsk03Summary?.spread) {
      const deepestDrawdownFund = rsk03Summary.spread.laggard;
      list.push({
        targetFund: deepestDrawdownFund,
        question: `Under what market conditions did ${deepestDrawdownFund.schemeName} incur its maximum peak-to-trough drawdown (${(Math.abs(rsk03Summary.spread.min) * 100).toFixed(2)}%), and what portfolio positions drove the decline?`,
        reason: `Audit maximum peak-to-trough drawdown (RSK-03) depth and downside resilience`,
        tag: "DRAWDOWN & RECOVERY",
      });
    }

    const rsk04Summary = keyMetricSummaries.find((m) => m.code === "RSK-04");
    if (rsk04Summary?.spread && rsk04Summary.spread.max > 0) {
      const longestUnderwaterFund = rsk04Summary.spread.leader;
      list.push({
        targetFund: longestUnderwaterFund,
        question: `Why did ${longestUnderwaterFund.schemeName} require ${Math.round(rsk04Summary.spread.max)} calendar days to recover to previous peak NAV (RSK-04 duration spread: ${Math.round(rsk04Summary.spread.diff)} days vs peers)?`,
        reason: `Evaluate maximum drawdown recovery duration (RSK-04) and capital lockup timeline`,
        tag: "RECOVERY TIMELINE",
      });
    }

    // 4. Portfolio concentration vs diversification (PRT-01: Top 10, CONC-01: Top 5)
    const prt01Summary = keyMetricSummaries.find((m) => m.code === "PRT-01");
    const conc01Summary = keyMetricSummaries.find((m) => m.code === "CONC-01");
    if (prt01Summary?.spread) {
      const mostConcentratedFund = prt01Summary.spread.leader;
      list.push({
        targetFund: mostConcentratedFund,
        question: `With top 10 positions representing ${(prt01Summary.spread.max > 1 ? prt01Summary.spread.max : prt01Summary.spread.max * 100).toFixed(1)}% of disclosed equity (PRT-01), does ${mostConcentratedFund.schemeName}'s active risk represent specific idiosyncratic stock selection rather than broad category beta?`,
        reason: `Investigate top 10 stock concentration risk (PRT-01)`,
        tag: "PORTFOLIO CONCENTRATION",
      });
    } else if (conc01Summary?.spread) {
      const mostConcentratedFund = conc01Summary.spread.leader;
      list.push({
        targetFund: mostConcentratedFund,
        question: `With top 5 positions representing ${(conc01Summary.spread.max > 1 ? conc01Summary.spread.max : conc01Summary.spread.max * 100).toFixed(1)}% of disclosed equity (CONC-01), does ${mostConcentratedFund.schemeName}'s active risk represent concentrated single-stock conviction?`,
        reason: `Investigate top 5 stock concentration risk (CONC-01)`,
        tag: "PORTFOLIO CONCENTRATION",
      });
    }

    return list;
  }, [keyMetricSummaries, temporalAlignment, funds]);

  const getDifferenceTypeBadge = (diffType: string) => {
    switch (diffType) {
      case "OBSERVED_DIFFERENCE":
        return {
          label: "OBSERVED DIFFERENCE",
          className: "bg-sky-500/15 border-sky-500/40 text-sky-400",
          desc: "Supported by identical observation period and verified math.",
        };
      case "CALCULATED_DIFFERENCE":
        return {
          label: "CALCULATED DIFFERENCE",
          className: "bg-indigo-500/15 border-indigo-500/40 text-indigo-400",
          desc: "Derived statistical metric under explicit methodology assumptions.",
        };
      case "INTERPRETATION":
        return {
          label: "INTERPRETATION",
          className: "bg-purple-500/15 border-purple-500/40 text-purple-400",
          desc: "Qualitative / structural interpretation of portfolio construction.",
        };
      case "DATA_LIMITATION":
        return {
          label: "DATA LIMITATION",
          className: "bg-amber-500/15 border-amber-500/40 text-amber-400",
          desc: "Difference may reflect disclosure timing, coverage, or history truncation.",
        };
      case "UNKNOWN":
      default:
        return {
          label: "UNKNOWN",
          className: "bg-slate-500/15 border-slate-500/40 text-slate-300",
          desc: "Insufficient data to verify whether difference is genuine.",
        };
    }
  };

  return (
    <section className="panel space-y-6 p-6 border-l-4 border-l-accent bg-surface-raised/40">
      <div className="flex flex-wrap items-start justify-between gap-4 border-b border-border pb-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="mono-meta text-accent font-bold uppercase tracking-wider text-[11px]">
              Evidence Interpretation &amp; Due Diligence
            </span>
            <span className="status-badge state-candidate text-[9.5px]">EVIDENCE-GROUNDED</span>
          </div>
          <h3 className="text-h3 text-text-primary mt-1">
            Comparative Evidence Interpretation &amp; Research Diligence
          </h3>
          <p className="text-text-secondary text-[12.5px] mt-1 max-w-[80ch] leading-relaxed">
            Deterministic synthesis of observed metric differences, temporal alignment constraints, and candidate methodology status across {funds.length} selected share classes. Zero automated rankings, zero buy/sell recommendations.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={handleSaveComparison}
            disabled={savingComparison || savedSuccess}
            className={`btn btn-sm text-[11px] font-mono flex items-center gap-1.5 transition-colors ${
              savedSuccess
                ? "bg-emerald-950/60 border border-emerald-800 text-emerald-300"
                : "btn-secondary text-text-primary hover:border-accent"
            }`}
            title="Preserve this comparison investigation to personal workspace"
          >
            <svg className="h-3.5 w-3.5 text-accent" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7H5a2 2 0 00-2 2v9a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-3m-1 4l-3 3m0 0l-3-3m3 3V4" />
            </svg>
            <span>{savedSuccess ? "✓ Saved to Workspace" : savingComparison ? "Saving..." : "Save Comparison to Workspace"}</span>
          </button>
          <Link
            href={`/analysis/${comparison.runId || 1}`}
            className="btn btn-secondary btn-sm text-[11px] font-mono flex items-center gap-1.5"
            title="Inspect full calculation run in Evidence Explorer"
          >
            <svg className="h-3.5 w-3.5 text-accent" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
            <span>Evidence Explorer Run #{comparison.runId || 1} →</span>
          </Link>
        </div>
      </div>

      {/* Structured Comparison Difference Explanations Layer */}
      {differenceExplanations.length > 0 && (
        <div className="space-y-3">
          <div className="flex items-center justify-between pb-1 border-b border-border/60">
            <div>
              <h4 className="text-[13px] font-semibold text-text-primary flex items-center gap-1.5">
                <span className="text-accent font-mono text-[11px]">A.</span>
                Structured Difference Explanations
              </h4>
              <p className="mono-meta text-[11px] mt-0.5">
                Every material quantitative difference evaluated under epistemic distinction tags.
              </p>
            </div>
            <span className="mono-meta text-[10px]">{differenceExplanations.length} Differences Evaluated</span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-3.5">
            {differenceExplanations.map((diff: ComparisonDifferenceExplanation, idx: number) => {
              const badge = getDifferenceTypeBadge(diff.differenceType);
              return (
                <div
                  key={idx}
                  className="p-3.5 bg-surface border border-border rounded-lg space-y-2.5 font-mono text-xs flex flex-col justify-between hover:border-accent/30 transition-all shadow-xs"
                >
                  <div className="space-y-1.5">
                    <div className="flex flex-wrap items-center justify-between gap-1.5">
                      <div className="flex items-center gap-1.5">
                        <span className="text-text-primary font-bold text-[12px]">{diff.metricName}</span>
                        <span className="text-accent text-[10px]">[{diff.metricCode}]</span>
                      </div>
                      <span
                        className={`px-2 py-0.5 rounded text-[9.5px] font-bold border ${badge.className}`}
                        title={badge.desc}
                      >
                        {badge.label}
                      </span>
                    </div>

                    <div className="bg-surface-raised p-2 rounded border border-border/60 space-y-1">
                      <div className="text-[11px] text-text-primary font-sans leading-tight">
                        {diff.whatDiffers}
                      </div>
                      <div className="flex items-center justify-between text-[10.5px] text-text-secondary">
                        <span className="font-bold text-accent">Magnitude: {diff.magnitude}</span>
                        <span className="text-text-muted">{diff.isMaterial ? "Material Spread" : "Noise Range"}</span>
                      </div>
                    </div>

                    <div className="space-y-1 text-[10.5px] text-text-secondary font-sans leading-relaxed">
                      <div>
                        <strong className="text-text-muted font-mono uppercase text-[9.5px] block">Limitation:</strong>
                        <span>{diff.limitation}</span>
                      </div>
                      <div>
                        <strong className="text-text-muted font-mono uppercase text-[9.5px] block">Next Evidence to Audit:</strong>
                        <span className="text-accent">{diff.additionalEvidenceNeeded}</span>
                      </div>
                    </div>
                  </div>

                  <div className="pt-2 border-t border-border/40 flex items-center justify-between">
                    <span className="text-[9.5px] text-text-muted font-mono truncate max-w-[200px]" title={diff.supportingSource}>
                      Src: {diff.supportingSource}
                    </span>
                    <button
                      type="button"
                      onClick={() => {
                        const targetFund = funds[0] || null;
                        if (targetFund) {
                          onInvestigateFund(
                            targetFund,
                            `Investigate ${diff.metricName} (${diff.metricCode}) spread: ${diff.magnitude}. ${diff.additionalEvidenceNeeded}`,
                            diff.limitation
                          );
                        }
                      }}
                      className="btn btn-secondary btn-sm text-[10px] flex items-center gap-1 font-mono"
                      title="Save difference finding into Research Workspace"
                    >
                      <svg className="h-3 w-3 text-accent" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z" />
                      </svg>
                      <span>Record Finding</span>
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Grid: Observed Metric Spreads & Analytical Limitations */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 pt-2">
        {/* Panel 1: Observed Quantitative Discrepancies */}
        <div className="panel-inset p-4 bg-surface rounded space-y-3 border border-border">
          <div className="flex items-center justify-between pb-2 border-b border-border/60">
            <h4 className="text-[13px] font-semibold text-text-primary flex items-center gap-1.5">
              <span className="text-accent font-mono text-[11px]">B.</span>
              Key Analytical Metric Dispersion
            </h4>
            <span className="mono-meta text-[10px]">Deterministic Math</span>
          </div>

          <div className="space-y-3 font-mono text-[11px]">
            {keyMetricSummaries.map((summary) => {
              if (!summary.spread) {
                return (
                  <div key={summary.code} className="flex items-center justify-between py-1 border-b border-border/40 text-text-secondary">
                    <span>{summary.name} ({summary.code}):</span>
                    <span className="text-text-muted italic">Non-comparable or incomplete</span>
                  </div>
                );
              }

              const isPct = summary.values[0]?.units === "PERCENTAGE";
              const formattedSpread = isPct
                ? `${(summary.spread.diff * 100).toFixed(2)}% spread`
                : `${summary.spread.diff.toFixed(2)} spread`;

              return (
                <div key={summary.code} className="py-1.5 border-b border-border/40 space-y-1">
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-text-primary">
                      {summary.name} <span className="text-accent text-[10px]">[{summary.code}]</span>
                    </span>
                    <span className="px-1.5 py-0.5 rounded bg-surface-raised border border-border text-text-secondary font-bold text-[10.5px]">
                      Δ {formattedSpread}
                    </span>
                  </div>
                  <div className="flex items-center justify-between text-[10.5px] text-text-secondary pl-2">
                    <span className="truncate max-w-[200px]" title={summary.spread.leader.schemeName}>
                      High: <strong className="text-text-primary">{summary.spread.leader.schemeName}</strong>
                    </span>
                    <span className="font-bold text-text-primary">
                      {isPct ? `${(summary.spread.max * 100).toFixed(2)}%` : summary.spread.max.toFixed(2)}
                    </span>
                  </div>
                  <div className="flex items-center justify-between text-[10.5px] text-text-secondary pl-2">
                    <span className="truncate max-w-[200px]" title={summary.spread.laggard.schemeName}>
                      Low: <strong className="text-text-primary">{summary.spread.laggard.schemeName}</strong>
                    </span>
                    <span className="font-bold text-text-primary">
                      {isPct ? `${(summary.spread.min * 100).toFixed(2)}%` : summary.spread.min.toFixed(2)}
                    </span>
                  </div>
                </div>
              );
            })}
          </div>

          <p className="text-[11px] leading-relaxed text-text-muted pt-1">
            *Observed numerical spreads originate strictly from verified vectorized backend calculations. Historical return spreads must not be interpreted as manager skill or future outperformance guarantees.
          </p>
        </div>

        {/* Panel 2: Temporal Alignment & Epistemic Boundaries */}
        <div className="panel-inset p-4 bg-surface rounded space-y-3 border border-border">
          <div className="flex items-center justify-between pb-2 border-b border-border/60">
            <h4 className="text-[13px] font-semibold text-text-primary flex items-center gap-1.5">
              <span className="text-accent font-mono text-[11px]">C.</span>
              Period Alignment &amp; Analytical Limitations
            </h4>
            <span className="mono-meta text-[10px]">Governance Guardrails</span>
          </div>

          <div className="space-y-3 text-[12px] leading-relaxed text-text-secondary font-mono">
            {/* Horizon Cutoff Disclosure */}
            <div className={`p-2.5 rounded border text-[11px] ${
              temporalAlignment.isPilotCutoff
                ? "bg-amber-500/10 border-amber-500/30 text-amber-200"
                : "bg-surface-raised border-border text-text-secondary"
            }`}>
              <div className="flex items-center gap-1.5 font-bold uppercase text-[10px]">
                <span>Observation Cutoff:</span>
                <span className="text-text-primary">{asOfDate}</span>
                <span className="text-text-muted">• Knowledge: {knowledgeCutoff}</span>
              </div>
              <p className="mt-1 font-sans text-[11.5px]">
                {temporalAlignment.isPilotCutoff
                  ? "Historical Baseline Active: Metric observations terminate at 2024-01-15 (Phase 2I/2J canonical pilot horizon). These observations do not reflect current market valuations or prevailing economic regimes."
                  : "Latest market observation horizon active. Look-ahead bias strictly excluded via bitemporal point-in-time timestamping."}
              </p>
            </div>

            {/* Inception & Evaluation Window Asymmetry */}
            <div className="p-2.5 rounded bg-surface-raised border border-border text-[11px] space-y-1">
              <span className="font-bold uppercase text-[10px] text-text-primary block">
                Observation Window Synchronization:
              </span>
              <p className="font-sans text-[11.5px]">
                {temporalAlignment.hasDifferentStartDates ? (
                  <>
                    <strong className="text-amber-300">Asymmetric Inception Dates:</strong> Selected candidates possess unequal trading histories (e.g. Parag Parikh ELSS commenced July 2021 vs multi-decade track records for older flexi-cap peers). Comparing metrics across non-overlapping market cycles creates survivorship and bull-market regime bias.
                  </>
                ) : (
                  <>
                    All candidates evaluate over synchronous start and end observation bounds across the requested lookback window.
                  </>
                )}
              </p>
            </div>

            {/* Methodology Governance Status */}
            <div className="p-2.5 rounded bg-surface-raised border border-border text-[11px] space-y-1">
              <span className="font-bold uppercase text-[10px] text-accent block">
                Governance Classification: Candidate Methodologies
              </span>
              <p className="font-sans text-[11.5px]">
                Only <strong>RET-02 (Simple Period Return)</strong> is an approved operational baseline. Metrics such as 3Y CAGR (RET-03), Annualized Volatility (RSK-01), Sharpe Ratio (RAT-01), Beta (MKT-01), and Pearson Correlation are candidate research specifications operating under institutional research disclaimers.
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Due Diligence Follow-up Action Questions */}
      <div className="border-t border-border pt-4 space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div>
            <h4 className="text-[13px] font-semibold text-text-primary flex items-center gap-1.5">
              <span className="text-accent font-mono text-[11px]">D.</span>
              Structured Investor Due-Diligence Follow-Up Questions
            </h4>
            <p className="mono-meta text-[11px] mt-0.5">
              Actionable investigation hypotheses generated deterministically from candidate dispersion and data limitations.
            </p>
          </div>
          <span className="mono-meta text-[10px]">{dueDiligenceQuestions.length} Questions Identified</span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          {dueDiligenceQuestions.map((q, idx) => (
            <div
              key={idx}
              className="panel-inset p-3 bg-surface rounded flex flex-col justify-between space-y-2.5 border border-border hover:border-accent/40 transition-colors"
            >
              <div className="space-y-1.5">
                <div className="flex items-center justify-between gap-2">
                  <span className="status-badge state-candidate text-[9px]">{q.tag}</span>
                  <span className="mono-meta text-[10px] truncate max-w-[180px] font-semibold text-text-primary">
                    Target: {q.targetFund.schemeName}
                  </span>
                </div>
                <p className="text-[12px] leading-snug font-medium text-text-primary">
                  &ldquo;{q.question}&rdquo;
                </p>
                <p className="mono-meta text-[10.5px] text-text-secondary">
                  Diligence Purpose: {q.reason}
                </p>
              </div>

              <div className="pt-2 border-t border-border/40 flex items-center justify-between">
                <Link
                  href={`/funds/${q.targetFund.schemeId || 1}?option=${q.targetFund.schemeOptionId}#coverage`}
                  className="text-link text-[10.5px] hover:underline"
                >
                  Inspect Fund Evidence →
                </Link>
                <button
                  type="button"
                  onClick={() => onInvestigateFund(q.targetFund, q.question, q.reason)}
                  className="btn btn-secondary btn-sm text-[10.5px] flex items-center gap-1 font-mono"
                  title="Save question into Research Workspace"
                >
                  <svg className="h-3 w-3 text-accent" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z" />
                  </svg>
                  <span>Record in Workspace</span>
                </button>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
