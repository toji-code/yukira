"use client";

import { useEffect, useState, useMemo, Suspense } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import { PageContainer, SectionHeading } from "@/components/layout/PageContainer";
import { StateView } from "@/components/epistemic/StateView";
import { fetchAllSchemes, fetchSchemeOptionsBySchemeId } from "@/lib/api/schemes";
import { executeComparison } from "@/lib/api/comparison";
import { Scheme, SchemeOption } from "@/types/domain";
import { ComparisonResponse, ComparisonMetric } from "@/types/comparison";

function ComparisonView({
  comparison,
  allSchemes,
  onReset,
}: {
  comparison: ComparisonResponse;
  allSchemes: Scheme[];
  onReset: () => void;
}) {
  return (
    <div className="space-y-8">
      <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h3 className="text-h2 text-text-primary">
            Comparison Results
          </h3>
          <p className="mono-meta mt-1">
            Analysis Period: {comparison.period.startDate} → {comparison.period.endDate}
          </p>
        </div>
        <button onClick={onReset} className="btn btn-secondary btn-sm">
          <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
          </svg>
          <span>New Comparison</span>
        </button>
      </div>

      <div className="panel scroll-region mb-6">
        <div className="scroll-region">
          <table className="data-table">
            <thead>
              <tr>
                <th>Metric</th>
                {comparison.funds.map((fund) => {
                  const scheme = allSchemes.find((s) => s.code === fund.schemeCode);
                  const href = scheme ? `/funds/${scheme.id}` : "#";
                  return (
                    <th key={fund.schemeOptionId} className="text-left">
                      <Link href={href} className="block">
                        <span className="block text-[12px] font-semibold leading-[1.35] text-text-primary hover:text-accent">
                          {fund.schemeName}
                        </span>
                      </Link>
                      <span className="mono-meta mt-1 block">
                        {fund.planType} • {fund.optionType}
                      </span>
                      <span className="mono-meta block">AMFI: {fund.amfiCode}</span>
                    </th>
                  );
                })}
              </tr>
            </thead>
            <tbody>
              {comparison.metrics.map((metric) => (
                <MetricRow key={metric.metricCode} metric={metric} funds={comparison.funds} />
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <section className="panel state-candidate">
        <div className="panel-header">
          <h4 className="eyebrow">Comparative Analysis Limitations</h4>
        </div>
        <ul className="space-y-1.5 px-4 py-3 text-[12.5px] leading-[1.6] text-text-secondary">
          <li>All metrics calculated using identical observation periods and knowledge cutoffs</li>
          <li>Historical performance does not predict future returns across changing market cycles</li>
          <li>Missing or insufficient data explicitly indicated per fund and metric</li>
          <li>No fund rankings, star ratings, or investment recommendations generated</li>
        </ul>
      </section>
    </div>
  );
}

function MetricRow({
  metric,
  funds,
}: {
  metric: ComparisonMetric;
  funds: ComparisonResponse["funds"];
}) {
  const [expanded, setExpanded] = useState(false);

  return (
    <>
      <tr className="transition-colors hover:bg-surface-raised">
        <td>
          <div className="space-y-0.5">
            <div className="text-[12.5px] font-semibold leading-[1.35] text-text-primary">
              {metric.metricName}
            </div>
            <div className="mono-meta">{metric.metricCode}</div>
            <button
              onClick={() => setExpanded(!expanded)}
              className="text-link mt-1 inline-flex items-center gap-1"
              aria-expanded={expanded}
            >
              {expanded ? "Hide" : "Show"} Details
              <svg
                className={`h-2.5 w-2.5 transition-transform ${expanded ? "rotate-180" : ""}`}
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                aria-hidden="true"
              >
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
              </svg>
            </button>
          </div>
        </td>
        {funds.map((fund) => {
          const result = metric.results.find((r) => r.schemeOptionId === fund.schemeOptionId);
          if (!result) {
            return (
              <td key={fund.schemeOptionId} className="data-unavailable">
                Not available
              </td>
            );
          }

          if (result.calculationStatus !== "CALCULATED") {
            return (
              <td key={fund.schemeOptionId}>
                <span className="status-badge state-candidate">
                  {result.calculationStatus === "INSUFFICIENT_DATA" ? "Insufficient data" : "Failed"}
                </span>
                {result.errorMessage && (
                  <div className="mono-meta mt-1.5 normal-case">{result.errorMessage}</div>
                )}
              </td>
            );
          }

          return (
            <td key={fund.schemeOptionId}>
              <div className="data-value-md">{result.formattedValue}</div>
              <div className="mono-meta mt-1">{result.units}</div>
            </td>
          );
        })}
      </tr>
      {expanded && (
        <tr className="bg-surface-inset">
          <td colSpan={funds.length + 1}>
            <div className="chain-col grid gap-4 sm:grid-cols-3">
              <div>
                <span className="tab-ordinal block">01 Observation</span>
                <p className="mt-1.5 text-[12.5px] leading-[1.55] text-text-secondary">
                  {metric.description}
                </p>
              </div>
              <div>
                <span className="tab-ordinal block">02 Interpretation</span>
                <p className="mt-1.5 text-[12.5px] leading-[1.55] text-text-secondary">
                  {metric.interpretation}
                </p>
              </div>
              <div className="state-candidate">
                <span className="tab-ordinal block text-candidate-fg">03 Limitation</span>
                <p className="mt-1.5 text-[12.5px] leading-[1.55] text-candidate-fg">
                  {metric.limitations}
                </p>
              </div>

              <div className="grid gap-3 sm:col-span-3 sm:grid-cols-2">
                <div className="panel-inset p-2.5">
                  <span className="def-label">Governance Status</span>
                  <span className="data-value-sm block">{metric.governanceStatus}</span>
                </div>
                <div className="panel-inset p-2.5">
                  <span className="def-label">Period</span>
                  <span className="data-value-sm block">{metric.period}</span>
                </div>
              </div>
            </div>
          </td>
        </tr>
      )}
    </>
  );
}

function CompareContent() {
  const searchParams = useSearchParams();
  
  const [allSchemes, setAllSchemes] = useState<Scheme[]>([]);
  const [selectedSchemeIds, setSelectedSchemeIds] = useState<number[]>([]);
  const [schemeOptions, setSchemeOptions] = useState<Map<number, SchemeOption[]>>(new Map());
  const [selectedOptionIds, setSelectedOptionIds] = useState<Map<number, number>>(new Map());
  
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  
  const [comparison, setComparison] = useState<ComparisonResponse | null>(null);
  const [comparing, setComparing] = useState(false);
  const [compareError, setCompareError] = useState<string | null>(null);

  useEffect(() => {
    const ids = searchParams.get("funds");
    if (ids) {
      const parsed = ids.split(",").map(Number).filter(n => !isNaN(n));
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setSelectedSchemeIds(parsed.slice(0, 4));
    }
  }, [searchParams]);

  useEffect(() => {
    fetchAllSchemes()
      .then((data) => {
        setAllSchemes(data);
        setLoading(false);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : "Failed to load schemes");
        setLoading(false);
      });
  }, []);

  useEffect(() => {
    const unvisited = selectedSchemeIds.filter(id => !schemeOptions.has(id));
    if (unvisited.length === 0) return;

    Promise.all(unvisited.map(schemeId => fetchSchemeOptionsBySchemeId(schemeId)))
      .then((results) => {
        setSchemeOptions(prev => {
          const next = new Map(prev);
          unvisited.forEach((id, idx) => next.set(id, results[idx]));
          return next;
        });
        
        setSelectedOptionIds(prev => {
          const next = new Map(prev);
          unvisited.forEach((id, idx) => {
            if (results[idx].length > 0 && !next.has(id)) {
              next.set(id, results[idx][0].id);
            }
          });
          return next;
        });
      })
      .catch(() => {});
  }, [selectedSchemeIds, schemeOptions]);

  const filteredSchemes = useMemo(() => {
    const q = searchQuery.toLowerCase().trim();
    if (!q) return allSchemes;
    return allSchemes.filter(
      (s) =>
        s.name.toLowerCase().includes(q) ||
        s.code.toLowerCase().includes(q) ||
        (s.amc && s.amc.name && s.amc.name.toLowerCase().includes(q))
    );
  }, [allSchemes, searchQuery]);

  const toggleScheme = (schemeId: number) => {
    setSelectedSchemeIds((prev) => {
      if (prev.includes(schemeId)) {
        const updated = prev.filter((id) => id !== schemeId);
        setSelectedOptionIds((optMap) => {
          const newMap = new Map(optMap);
          newMap.delete(schemeId);
          return newMap;
        });
        return updated;
      }
      if (prev.length >= 4) return prev;
      return [...prev, schemeId];
    });
  };

  const handleCompare = async () => {
    const optionIds = Array.from(selectedOptionIds.values());
    if (optionIds.length < 2) {
      setCompareError("Select at least 2 funds to compare.");
      return;
    }

    setComparing(true);
    setCompareError(null);

    try {
      const result = await executeComparison({
        schemeOptionIds: optionIds,
        asOfDate: "2024-01-15",
        knowledgeCutoffTime: "2024-01-31T23:59:59+05:30",
        metricCodes: [
          "RET-02", "RET-03", 
          "RSK-01", "RSK-02", "RSK-03", "RSK-04", "RSK-05",
          "RAT-01", "RAT-02", 
          "MKT-01", "MKT-02", "MKT-03", "MKT-04", "MKT-05", "MKT-06"
        ],
      });
      setComparison(result);
    } catch (err: unknown) {
      setCompareError(err instanceof Error ? err.message : "Comparison failed");
    } finally {
      setComparing(false);
    }
  };

  const selectedSchemes = selectedSchemeIds
    .map((id) => allSchemes.find((s) => s.id === id))
    .filter((s): s is Scheme => s !== undefined);

  if (loading) {
    return <StateView kind="loading" title="Loading Funds" message="Retrieving verified mutual fund catalog..." />;
  }

  if (error) {
    return <StateView kind="unavailable" title="Catalog Unavailable" message={error} />;
  }

  return (
    <>
      <section className="panel state-candidate mb-8">
        <div className="panel-header">
          <h2 className="eyebrow">Comparative Analysis Framework</h2>
        </div>
        <p className="max-w-[86ch] px-4 py-3 text-[12.5px] leading-[1.6] text-text-secondary">
          All compared funds use identical observation periods, methodology versions, and knowledge
          cutoffs. Metrics reflect historical realized outcomes under past market conditions. YUKIRA
          does not generate investment recommendations or fund rankings.
        </p>
      </section>

      {compareError && (
        <div className="state-panel-error mb-6">
          <strong className="eyebrow block">Comparison Error</strong>
          <span className="mt-1 block font-mono text-[12px]">{compareError}</span>
        </div>
      )}

      {!comparison ? (
        <>
          <section className="mb-8">
            <div className="mb-4">
              <SectionHeading
                ordinal="Step 1"
                title="Select Funds (2–4)"
                description={`${selectedSchemeIds.length} of 4 slots occupied`}
              />
            </div>

            <div className="mb-4 max-w-md">
              <label htmlFor="compare-search" className="field-label">
                Search
              </label>
              <input
                id="compare-search"
                type="text"
                placeholder="Scheme name, code, or AMC…"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="field field-mono"
              />
            </div>

            <div className="scroll-region grid max-h-96 grid-cols-1 gap-px overflow-y-auto border border-border bg-border">
              {filteredSchemes.map((scheme) => {
                const isSelected = selectedSchemeIds.includes(scheme.id);
                const isDisabled = !isSelected && selectedSchemeIds.length >= 4;

                return (
                  <label
                    key={scheme.id}
                    className={`flex cursor-pointer items-start gap-3 bg-surface p-3 transition-colors ${
                      isSelected ? "bg-accent-muted" : isDisabled ? "opacity-50" : "hover:bg-surface-raised"
                    }`}
                  >
                    <input
                      type="checkbox"
                      checked={isSelected}
                      disabled={isDisabled}
                      onChange={() => !isDisabled && toggleScheme(scheme.id)}
                      className="mt-0.5 accent-[var(--accent)]"
                    />
                    <span className="min-w-0 flex-1">
                      <span className="mono-meta block">{scheme.code}</span>
                      <span className="mt-0.5 block text-[13px] font-semibold text-text-primary">
                        {scheme.name}
                      </span>
                      <span className="mono-meta mt-1 block">
                        {scheme.amc?.name || "Not available"} • Inception:{" "}
                        {scheme.inceptionDate || "Not available"}
                      </span>
                    </span>
                  </label>
                );
              })}
            </div>
          </section>

          {selectedSchemes.length > 0 && (
            <section className="mb-8">
              <div className="mb-4">
                <SectionHeading
                  ordinal="Step 2"
                  title={`Select Share Classes (${selectedSchemes.length} funds)`}
                  description="Share class selection determines which registered option each metric is evaluated against."
                />
              </div>

              <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
                {selectedSchemes.map((scheme) => {
                  const options = schemeOptions.get(scheme.id) || [];
                  const selectedOption = selectedOptionIds.get(scheme.id);

                  return (
                    <div key={scheme.id} className="panel">
                      <div className="panel-header">
                        <h4 className="text-[13px] font-semibold text-text-primary">{scheme.name}</h4>
                        <span className="mono-meta shrink-0">{options.length} options</span>
                      </div>
                      {options.length === 0 ? (
                        <p className="mono-meta px-4 py-3 normal-case">Loading options…</p>
                      ) : (
                        <ul className="divide-y divide-border">
                          {options.map((opt) => (
                            <li key={opt.id}>
                              <label className="flex cursor-pointer items-center gap-2.5 px-4 py-2 transition-colors hover:bg-surface-raised">
                                <input
                                  type="radio"
                                  name={`option-${scheme.id}`}
                                  checked={selectedOption === opt.id}
                                  onChange={() =>
                                    setSelectedOptionIds(
                                      (prev) => new Map(prev).set(scheme.id, opt.id)
                                    )
                                  }
                                  className="accent-[var(--accent)]"
                                />
                                <span className="font-mono text-[12px] text-text-primary">
                                  {opt.plan?.planType || "DIRECT"} • {opt.optionType || "GROWTH"} •
                                  AMFI: {opt.amfiCode || "Not available"}
                                </span>
                              </label>
                            </li>
                          ))}
                        </ul>
                      )}
                    </div>
                  );
                })}
              </div>
            </section>
          )}

          <div className="flex items-center gap-3">
            <button
              onClick={handleCompare}
              disabled={comparing || selectedSchemeIds.length < 2 || selectedOptionIds.size < selectedSchemeIds.length}
              className="btn btn-primary"
            >
              {comparing ? (
                <>
                  <svg className="h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24" aria-hidden="true">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                  </svg>
                  <span>Calculating Metrics…</span>
                </>
              ) : (
                <>
                  <span>Compare Selected Funds</span>
                  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                  </svg>
                </>
              )}
            </button>
            <span className="mono-meta">
              {selectedSchemeIds.length < 2
                ? "Select at least 2 funds"
                : "Analysis cutoff 2024-01-15 · Knowledge cutoff 2024-01-31T23:59:59+05:30"}
            </span>
          </div>
        </>
      ) : (
        <ComparisonView comparison={comparison} allSchemes={allSchemes} onReset={() => setComparison(null)} />
      )}
    </>
  );
}

export default function ComparePage() {
  return (
    <PageContainer
      title="Multi-Fund Comparison"
      subtitle="Compare quantitative metrics across multiple mutual funds using verified point-in-time observations."
      breadcrumbs={[
        { label: "Home", href: "/" },
        { label: "Funds", href: "/funds" },
        { label: "Compare", href: "/compare" },
      ]}
    >
      <Suspense fallback={<StateView kind="loading" title="Loading..." message="Initializing comparison view..." />}>
        <CompareContent />
      </Suspense>
    </PageContainer>
  );
}
