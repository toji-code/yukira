"use client";

import { useMemo } from "react";
import { ComparisonFund, ComparisonMetric, ComparisonResponse } from "@/types/comparison";

interface BenchmarkRelativeComparisonProps {
  funds?: ComparisonFund[];
  metrics?: ComparisonMetric[];
  comparison?: ComparisonResponse;
  asOfDate?: string;
  knowledgeCutoff?: string;
}

interface BenchmarkRelativeRow {
  code: string;
  name: string;
  description: string;
  units: string;
  category: string;
  interpretation: string;
  limitations: string;
}

const BENCHMARK_METRIC_SPECS: BenchmarkRelativeRow[] = [
  {
    code: "RET-03",
    name: "3-Year Annualized Return (CAGR)",
    description: "Normalized 3-year compound annual growth rate over Julian trading days.",
    units: "PERCENTAGE",
    category: "Absolute Return",
    interpretation: "Annualized multi-year compound performance over standard 3-year horizon.",
    limitations: "Conceals sub-period return volatility and peak drawdowns.",
  },
  {
    code: "RET-07",
    name: "3-Year Active / Excess Return",
    description: "Annualized fund return minus NIFTY 500 TRI benchmark return (Fund CAGR - Benchmark CAGR).",
    units: "PERCENTAGE",
    category: "Benchmark-Relative Return",
    interpretation: "Geometric or arithmetic excess return relative to primary broad-market index.",
    limitations: "Factual descriptive historical spread; does not imply manager skill or future persistence.",
  },
  {
    code: "MKT-01",
    name: "Systematic Market Beta (3Y)",
    description: "Covariance of fund daily returns with NIFTY 500 TRI divided by benchmark variance.",
    units: "BETA",
    category: "Market Sensitivity",
    interpretation: "Degree of exposure to overall market directional moves (>1.0 indicates higher volatility than market).",
    limitations: "Assumes stationary linear covariance; can shift materially across market regimes.",
  },
  {
    code: "MKT-02",
    name: "Downside Market Beta (3Y)",
    description: "Covariance with benchmark conditional strictly on negative benchmark return trading days.",
    units: "BETA",
    category: "Downside Sensitivity",
    interpretation: "Sensitivity to broad market sell-offs. Values below 1.0 indicate asymmetric capital preservation.",
    limitations: "Evaluated on conditional negative market subsets; smaller sample size under bull regimes.",
  },
  {
    code: "MKT-05",
    name: "Capture Spread (Up - Down)",
    description: "Upside capture ratio minus downside capture ratio against NIFTY 500 TRI.",
    units: "BASIS_POINTS",
    category: "Asymmetric Capture",
    interpretation: "Positive spread indicates the fund captured more upside than downside during benchmark swings.",
    limitations: "Sample dependency on the frequency and magnitude of benchmark market cycles.",
  },
  {
    code: "REL-01",
    name: "Tracking Error (3Y Annualized)",
    description: "Annualized standard deviation of daily active returns (Fund Return - NIFTY 500 TRI Return).",
    units: "PERCENTAGE",
    category: "Benchmark Proximity",
    interpretation: "Volatility of excess return. Lower values indicate index proximity; higher indicates active divergence.",
    limitations: "Tracking error does not distinguish between positive and negative active divergence.",
  },
  {
    code: "REL-02",
    name: "Jensen's Alpha (3Y Annualized)",
    description: "Annualized active return in excess of CAPM expected return: R_p - [R_f + Beta * (R_m - R_f)].",
    units: "PERCENTAGE",
    category: "Risk-Adjusted Alpha",
    interpretation: "Historical risk-adjusted excess return after controlling for market beta and 91-day T-Bill risk-free rate.",
    limitations: "Candidate specification. Sensitive to the chosen risk-free proxy and beta estimation window.",
  },
  {
    code: "RAT-04",
    name: "Information Ratio (3Y)",
    description: "Annualized active return divided by annualized tracking error (Active Return / Tracking Error).",
    units: "RATIO",
    category: "Active Efficiency",
    interpretation: "Consistency of excess return per unit of benchmark divergence risk.",
    limitations: "Can be misleading when excess return and tracking error are both close to zero.",
  },
  {
    code: "RSK-03",
    name: "Maximum Drawdown (3Y)",
    description: "Peak-to-trough maximum percentage drop observed across the standard 3-year observation window.",
    units: "PERCENTAGE",
    category: "Tail Capital Risk",
    interpretation: "Worst historical capital impairment experienced from peak before recovering.",
    limitations: "Path-dependent metric based on historical peaks; future drawdowns may exceed historical peaks.",
  },
];

export function BenchmarkRelativeComparison(props: BenchmarkRelativeComparisonProps) {
  const funds = props.funds || props.comparison?.funds || [];
  const metrics = props.metrics || props.comparison?.metrics || [];
  const asOfDate = props.asOfDate || props.comparison?.period?.endDate || "2024-01-15";
  const knowledgeCutoff = props.knowledgeCutoff || props.comparison?.period?.knowledgeCutoffTime || "2024-01-31T23:59:59+05:30";

  const metricMap = useMemo(() => {
    const map = new Map<string, ComparisonMetric>();
    metrics.forEach((m) => map.set(m.metricCode, m));
    return map;
  }, [metrics]);

  return (
    <section className="space-y-4">
      {/* Section Header */}
      <div className="flex flex-wrap items-end justify-between gap-3 border-b border-border pb-3">
        <div>
          <div className="flex items-center gap-2">
            <span className="tab-ordinal font-mono text-[11px] font-bold text-accent">03. BENCHMARK ATTRIBUTION</span>
            <span className="status-badge state-verified text-[10px]">VERIFIED INDEX ALIGNMENT</span>
          </div>
          <h3 className="text-h2 text-text-primary mt-1">
            Benchmark-Relative Analysis &amp; Market Sensitivity
          </h3>
          <p className="mono-meta mt-0.5">
            Primary Benchmark: <span className="text-text-primary font-semibold">NIFTY 500 TRI (Total Returns Index)</span> • Provider: <span className="text-text-secondary">NSE / AMFI Authoritative Feed</span>
          </p>
        </div>
        <div className="font-mono text-[11px] text-text-muted text-right">
          <div>Analysis Cutoff: <span className="text-text-primary">{asOfDate}</span></div>
          <div>PIT Horizon: <span className="text-text-secondary">{knowledgeCutoff.split('T')[0]}</span></div>
        </div>
      </div>

      {/* Benchmark Identity Card */}
      <div className="panel p-4 bg-surface border-l-2 border-l-accent flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <span className="font-mono text-[10.5px] font-bold uppercase text-accent">Canonical Benchmark Mandate</span>
            <span className="status-badge state-approved text-[9.5px]">NIFTY_500_TRI</span>
          </div>
          <p className="text-[13px] text-text-primary font-semibold">
            NIFTY 500 Total Returns Index (TRI)
          </p>
          <p className="text-[11.5px] text-text-secondary leading-relaxed max-w-[85ch]">
            YUKIRA standardizes all broad multi-cap and flexi-cap mutual fund evaluations against NIFTY 500 TRI. Dividends and capital gains are explicitly reinvested into index levels on ex-dates to prevent synthetic manager outperformance bias.
          </p>
        </div>
        <div className="panel-inset p-3 bg-surface-raised rounded font-mono text-[11px] space-y-1 shrink-0 w-full md:w-auto min-w-[220px]">
          <div className="flex justify-between text-text-muted">
            <span>INDEX CODE:</span>
            <span className="text-text-primary font-semibold">NIFTY 500 TRI</span>
          </div>
          <div className="flex justify-between text-text-muted">
            <span>RISK-FREE PROXY:</span>
            <span className="text-text-primary font-semibold">FBIL 91D T-BILL</span>
          </div>
          <div className="flex justify-between text-text-muted">
            <span>SAMPLING FREQ:</span>
            <span className="text-text-primary font-semibold">Daily Trading Days</span>
          </div>
        </div>
      </div>

      {/* Benchmark-Relative Comparison Table */}
      <div className="panel scroll-region">
        <div className="scroll-region overflow-x-auto">
          <table className="data-table w-full">
            <thead>
              <tr>
                <th className="w-1/3 text-left">Benchmark-Relative Metric</th>
                <th className="w-24 text-left">Units</th>
                {funds.map((fund) => (
                  <th key={fund.schemeOptionId} className="text-left">
                    <span className="block text-[12px] font-semibold text-text-primary">
                      {fund.schemeName}
                    </span>
                    <span className="mono-meta block">{fund.planType} • {fund.optionType}</span>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {BENCHMARK_METRIC_SPECS.map((spec) => {
                const metric = metricMap.get(spec.code);

                return (
                  <tr key={spec.code} className="transition-colors hover:bg-surface-raised">
                    <td className="py-2.5">
                      <div className="space-y-0.5">
                        <div className="flex items-center gap-1.5">
                          <span className="text-[12.5px] font-semibold text-text-primary">
                            {spec.name}
                          </span>
                          <span className="font-mono text-[10px] text-accent font-bold">
                            {spec.code}
                          </span>
                        </div>
                        <p className="text-[11px] text-text-muted leading-tight">
                          {spec.description}
                        </p>
                      </div>
                    </td>
                    <td className="py-2.5 font-mono text-[11px] text-text-secondary">
                      {spec.units}
                    </td>
                    {funds.map((fund) => {
                      const res = metric?.results.find((r) => r.schemeOptionId === fund.schemeOptionId);

                      if (!res || res.calculationStatus !== "CALCULATED") {
                        return (
                          <td key={fund.schemeOptionId} className="py-2.5">
                            <span className="status-badge state-candidate text-[9.5px]">
                              {res?.calculationStatus === "INSUFFICIENT_DATA" ? "Insufficient Data" : "Not Available"}
                            </span>
                            {res?.errorMessage && (
                              <div className="mono-meta mt-1 text-[10px] text-text-tertiary">
                                {res.errorMessage}
                              </div>
                            )}
                          </td>
                        );
                      }

                      const numVal = res.numericValue;
                      let displayVal = res.formattedValue;

                      if (!displayVal && numVal !== null && numVal !== undefined) {
                        if (spec.units === "PERCENTAGE") {
                          displayVal = `${numVal >= 0 ? "+" : ""}${(numVal * 100).toFixed(2)}%`;
                        } else if (spec.units === "BASIS_POINTS") {
                          displayVal = `${numVal >= 0 ? "+" : ""}${numVal.toFixed(0)} bps`;
                        } else {
                          displayVal = numVal.toFixed(2);
                        }
                      }

                      // Color coding for excess return and capture spread
                      let valColorClass = "text-text-primary";
                      if (spec.code === "RET-07" || spec.code === "REL-02" || spec.code === "MKT-05") {
                        if (numVal !== null && numVal !== undefined) {
                          valColorClass = numVal > 0 ? "text-emerald-400 font-bold" : numVal < 0 ? "text-rose-400 font-bold" : "text-text-primary";
                        }
                      }

                      return (
                        <td key={fund.schemeOptionId} className="py-2.5">
                          <div className={`data-value-md font-mono ${valColorClass}`}>
                            {displayVal || "—"}
                          </div>
                          <div className="mono-meta mt-0.5 text-[9.5px]">
                            {res.comparabilityStatus === "COMPARABLE_WITH_LIMITATIONS" ? (
                              <span className="text-amber-400" title={res.limitationReason || undefined}>
                                Limitations Apply *
                              </span>
                            ) : (
                              <span className="text-emerald-400">Comparable</span>
                            )}
                          </div>
                        </td>
                      );
                    })}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      {/* Epistemic Institutional Disclaimer */}
      <div className="panel p-3.5 bg-surface text-[11.5px] font-mono border border-border/80 rounded-md space-y-1.5">
        <div className="flex items-center gap-1.5 text-accent font-semibold text-[10.5px] uppercase">
          <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <span>Epistemic Guardrail: Benchmark-Relative Metrics Interpretation</span>
        </div>
        <p className="text-text-secondary leading-relaxed">
          Historical excess return (RET-07), Jensen&apos;s Alpha (REL-02), and systematic beta (MKT-01) describe backward-looking empirical divergence from NIFTY 500 TRI under the specified observation cutoff. They do not constitute evidence of fund manager skill, market timing expertise, or predictive alpha. Benchmark outperformance may reflect cyclical factor tilts (e.g. value, small-cap, cash allocation) rather than structural stock selection efficiency.
        </p>
      </div>
    </section>
  );
}
