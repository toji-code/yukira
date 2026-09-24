"use client";

import { use, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { PageContainer } from "@/components/layout/PageContainer";
import { StateView } from "@/components/epistemic/StateView";
import { fetchSchemeById, fetchSchemeOptionsBySchemeId } from "@/lib/api/schemes";
import { triggerCalculation } from "@/lib/api/calculations";
import { Scheme, SchemeOption } from "@/types/domain";

interface PageProps {
  params: Promise<{ id: string }>;
}

export default function FundDetailPage({ params }: PageProps) {
  const resolvedParams = use(params);
  const schemeId = parseInt(resolvedParams.id, 10);
  const router = useRouter();

  const [scheme, setScheme] = useState<Scheme | null>(null);
  const [options, setOptions] = useState<SchemeOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Trigger calculation state
  const [selectedOptionId, setSelectedOptionId] = useState<number | null>(null);
  const [triggering, setTriggering] = useState(false);
  const [triggerError, setTriggerError] = useState<string | null>(null);
  const [triggerSuccessRunId, setTriggerSuccessRunId] = useState<number | null>(null);

  useEffect(() => {
    if (isNaN(schemeId)) {
      return;
    }

    let active = true;
    Promise.all([
      fetchSchemeById(schemeId),
      fetchSchemeOptionsBySchemeId(schemeId),
    ])
      .then(([schemeData, schemeOptions]) => {
        if (active) {
          setScheme(schemeData);
          setOptions(schemeOptions);
          if (schemeOptions.length > 0) {
            setSelectedOptionId(schemeOptions[0].id);
          }
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (active) {
          setError(err instanceof Error ? err.message : "Failed to load scheme details");
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [schemeId]);

  const handleTriggerCalculation = async (metricCode: string) => {
    if (!selectedOptionId) {
      setTriggerError("Please select a scheme option before initiating calculation.");
      return;
    }

    setTriggering(true);
    setTriggerError(null);
    setTriggerSuccessRunId(null);

    try {
      const run = await triggerCalculation({
        schemeOptionId: selectedOptionId,
        benchmarkId: 1,
        asOfDate: "2024-01-15",
        knowledgeCutoffTime: "2024-01-31T23:59:59+05:30",
        metricCodes: [metricCode],
        methodologyTag: "CANDIDATE_V1",
      });
      setTriggerSuccessRunId(run.id);
      router.push(`/analysis/${run.id}`);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Failed to trigger calculation run";
      setTriggerError(msg);
    } finally {
      setTriggering(false);
    }
  };

  if (loading) {
    return (
      <PageContainer title="Scheme Intelligence">
        <StateView
          kind="loading"
          title="Loading Scheme Details"
          message={`Querying backend database for scheme ID #${schemeId}...`}
        />
      </PageContainer>
    );
  }

  if (error || !scheme) {
    return (
      <PageContainer title="Scheme Unavailable">
        <StateView
          kind="unavailable"
          title="Fund data is not currently available."
          message={error || `Scheme #${schemeId} does not exist in master records.`}
          action={
            <Link
              href="/funds"
              className="inline-flex rounded-md bg-zinc-800 px-3.5 py-2 text-xs font-mono font-medium text-zinc-200 hover:bg-zinc-700"
            >
              Back to Catalog
            </Link>
          }
        />
      </PageContainer>
    );
  }

  const selectedOption = options.find((o) => o.id === selectedOptionId) || options[0];

  return (
    <PageContainer
      title={scheme.name}
      subtitle={`Scheme Code: ${scheme.code} • Master ID: #${scheme.id}`}
      breadcrumbs={[
        { label: "Funds", href: "/funds" },
        { label: scheme.code, href: `/funds/${scheme.id}` },
      ]}
    >
      {/* Epistemic Notice */}
      <div className="mb-8 rounded-xl border border-amber-500/30 bg-amber-500/10 p-4 font-mono text-xs">
        <div className="flex items-center gap-2 font-bold text-amber-400 uppercase tracking-wider text-[11px]">
          <span className="h-2 w-2 rounded-full bg-amber-400" />
          Candidate Methodology Disclosure
        </div>
        <p className="mt-1 text-zinc-300 font-sans leading-relaxed text-xs">
          All analytical metrics displayed on this page operate strictly under candidate governance status. No quantitative methodology is currently validated for production. YUKIRA does not generate investment recommendations, star ratings, or performance forecasts.
        </p>
      </div>

      {/* 1. FUND IDENTITY */}
      <section className="mb-10">
        <div className="font-mono text-[11px] uppercase tracking-wider text-cyan-400">Section 1</div>
        <h2 className="text-xl font-bold text-zinc-100">Fund Identity</h2>
        <div className="mt-4 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 font-mono text-xs">
          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-4">
            <span className="text-zinc-500 text-[10px] uppercase block">Scheme Code</span>
            <span className="text-cyan-400 font-semibold text-sm mt-1 block">{scheme.code}</span>
            <span className="text-zinc-500 text-[10px] mt-1 block">Canonical Identifier</span>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-4">
            <span className="text-zinc-500 text-[10px] uppercase block">Inception Date</span>
            <span className="text-zinc-100 font-semibold text-sm mt-1 block">{scheme.inceptionDate || "—"}</span>
            <span className="text-zinc-500 text-[10px] mt-1 block">Formal Registration</span>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-4">
            <span className="text-zinc-500 text-[10px] uppercase block">Master Record Status</span>
            <span className="text-emerald-400 font-semibold text-sm mt-1 block flex items-center gap-1.5">
              <span className="h-2 w-2 rounded-full bg-emerald-400" />
              {scheme.status || "ACTIVE"}
            </span>
            <span className="text-zinc-500 text-[10px] mt-1 block">PostgreSQL Verified</span>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-4">
            <span className="text-zinc-500 text-[10px] uppercase block">Registered Share Classes</span>
            <span className="text-zinc-100 font-semibold text-sm mt-1 block">{options.length} Options</span>
            <span className="text-zinc-500 text-[10px] mt-1 block">Direct / Regular Plans</span>
          </div>
        </div>

        {/* Registered Plans & Options Table */}
        <div className="mt-5 rounded-xl border border-zinc-800 bg-zinc-900/60 p-5">
          <h3 className="text-xs font-mono font-semibold uppercase tracking-wider text-zinc-300">
            Registered Share Classes & Options ({options.length})
          </h3>
          <p className="mt-1 text-xs text-zinc-400">
            Select a specific share class to inspect point-in-time data status and run candidate calculations.
          </p>

          {options.length === 0 ? (
            <div className="mt-4">
              <StateView
                kind="empty"
                title="No Share Classes Registered"
                message="No options or plans have been recorded for this fund in the database."
              />
            </div>
          ) : (
            <div className="mt-4 overflow-x-auto">
              <table className="w-full text-left text-xs font-mono">
                <thead className="border-b border-zinc-800 bg-zinc-950/60 text-zinc-400 uppercase text-[10px] tracking-wider">
                  <tr>
                    <th className="px-4 py-2.5">Select</th>
                    <th className="px-4 py-2.5">Option ID</th>
                    <th className="px-4 py-2.5">Plan Type</th>
                    <th className="px-4 py-2.5">Option Type</th>
                    <th className="px-4 py-2.5">AMFI Code</th>
                    <th className="px-4 py-2.5">ISIN</th>
                    <th className="px-4 py-2.5">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-zinc-800/60">
                  {options.map((opt) => (
                    <tr
                      key={opt.id}
                      onClick={() => setSelectedOptionId(opt.id)}
                      className={`cursor-pointer transition hover:bg-zinc-800/40 ${
                        selectedOptionId === opt.id ? "bg-cyan-500/10 border-l-2 border-cyan-400" : ""
                      }`}
                    >
                      <td className="px-4 py-2.5">
                        <input
                          type="radio"
                          name="selectedOption"
                          checked={selectedOptionId === opt.id}
                          onChange={() => setSelectedOptionId(opt.id)}
                          className="text-cyan-500 focus:ring-cyan-500"
                        />
                      </td>
                      <td className="px-4 py-2.5 font-semibold text-cyan-400">
                        Option #{opt.id}
                      </td>
                      <td className="px-4 py-2.5 text-zinc-300">
                        {opt.plan?.planType || "DIRECT"}
                      </td>
                      <td className="px-4 py-2.5 text-zinc-300">
                        {opt.optionType || "GROWTH"}
                      </td>
                      <td className="px-4 py-2.5 text-zinc-400 font-bold">
                        {opt.amfiCode || "—"}
                      </td>
                      <td className="px-4 py-2.5 text-zinc-400">
                        {opt.isin || "—"}
                      </td>
                      <td className="px-4 py-2.5">
                        <span className="inline-flex rounded px-2 py-0.5 text-[10px] font-medium bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
                          {opt.status || "ACTIVE"}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </section>

      {/* 2. DATA STATUS */}
      <section className="mb-10">
        <div className="font-mono text-[11px] uppercase tracking-wider text-cyan-400">Section 2</div>
        <h2 className="text-xl font-bold text-zinc-100">Data Status</h2>
        <div className="mt-4 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 font-mono text-xs">
          <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-4">
            <span className="text-zinc-500 text-[10px] uppercase block">Point-in-Time Ledger</span>
            <span className="text-emerald-400 font-semibold mt-1 block">ACTIVE & ENFORCED</span>
            <p className="mt-1 text-[11px] text-zinc-400 font-sans">
              Bitemporal separation of effective observation dates from knowledge availability cutoffs.
            </p>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-4">
            <span className="text-zinc-500 text-[10px] uppercase block">Data Ingestion Source</span>
            <span className="text-zinc-100 font-semibold mt-1 block">AMFI Official Feeds</span>
            <p className="mt-1 text-[11px] text-zinc-400 font-sans">
              Raw text feeds verified with SHA-256 checksums and exact byte counts.
            </p>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-4">
            <span className="text-zinc-500 text-[10px] uppercase block">Data Quality Taxonomy</span>
            <span className="text-cyan-400 font-semibold mt-1 block">6 Orthogonal Dimensions</span>
            <p className="mt-1 text-[11px] text-zinc-400 font-sans">
              Quality, Verification, Revision, Freshness, Presence, and Integrity tracked per observation.
            </p>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-4">
            <span className="text-zinc-500 text-[10px] uppercase block">Benchmark Data Status</span>
            <span className="text-amber-400 font-semibold mt-1 block">PENDING INGESTION</span>
            <p className="mt-1 text-[11px] text-zinc-400 font-sans">
              Official TRI indices scheduled for Phase 3 ingestion. Zero synthetic benchmarks used.
            </p>
          </div>
        </div>
      </section>

      {/* 3. LATEST AVAILABLE INFORMATION */}
      <section className="mb-10">
        <div className="font-mono text-[11px] uppercase tracking-wider text-cyan-400">Section 3</div>
        <h2 className="text-xl font-bold text-zinc-100">Latest Available Information</h2>
        <div className="mt-4 rounded-xl border border-zinc-800 bg-zinc-900/60 p-5 font-mono text-xs">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 pb-4 border-b border-zinc-800">
            <div>
              <span className="text-zinc-500 text-[10px] uppercase block">Active Share Class</span>
              <span className="text-cyan-400 font-semibold mt-0.5 block">
                Option #{selectedOption?.id || "—"} ({selectedOption?.optionType || "GROWTH"})
              </span>
            </div>
            <div>
              <span className="text-zinc-500 text-[10px] uppercase block">AMFI Identifier</span>
              <span className="text-zinc-100 font-semibold mt-0.5 block">{selectedOption?.amfiCode || "—"}</span>
            </div>
            <div>
              <span className="text-zinc-500 text-[10px] uppercase block">ISIN</span>
              <span className="text-zinc-100 font-semibold mt-0.5 block">{selectedOption?.isin || "—"}</span>
            </div>
          </div>

          <div className="mt-4 grid grid-cols-1 sm:grid-cols-2 gap-4 text-zinc-400 font-sans text-xs">
            <div>
              <strong className="text-zinc-200 block font-mono text-[11px] uppercase">Evaluation Window Convention</strong>
              <p className="mt-1">
                Conservative trading calendar lookback: If a target date falls on a weekend or market holiday, the engine deterministically searches backwards up to a maximum of 4 calendar days.
              </p>
            </div>
            <div>
              <strong className="text-zinc-200 block font-mono text-[11px] uppercase">Knowledge Cutoff Convention</strong>
              <p className="mt-1">
                Point-in-time resolution guarantees that observations published after the designated cutoff timestamp are strictly excluded to avoid hindsight bias.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* 4. RETURN ANALYSIS */}
      <section className="mb-10">
        <div className="font-mono text-[11px] uppercase tracking-wider text-cyan-400">Section 4</div>
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
          <div>
            <h2 className="text-xl font-bold text-zinc-100">Return Analysis</h2>
            <p className="text-xs text-zinc-400">
              Only implemented candidate methodologies can be executed. Unimplemented metrics are explicitly marked.
            </p>
          </div>
        </div>

        <div className="mt-4 space-y-4">
          {/* RET-02 Card: Implemented Candidate */}
          <div className="rounded-xl border border-cyan-500/30 bg-cyan-950/15 p-5 backdrop-blur-sm">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-cyan-500/20 pb-4">
              <div>
                <div className="flex items-center gap-2">
                  <span className="font-mono text-xs font-bold text-cyan-400">RET-02</span>
                  <h3 className="text-sm font-semibold text-zinc-100">Simple Period Return</h3>
                  <span className="rounded bg-amber-500/20 px-2 py-0.5 font-mono text-[10px] font-semibold text-amber-300 border border-amber-500/30">
                    Candidate — Not Validated
                  </span>
                </div>
                <p className="mt-1 text-xs text-zinc-300 font-sans">
                  Formula: <code className="font-mono text-cyan-300">Return = Ending NAV / Starting NAV - 1</code>. Supporting discrete return primitive.
                </p>
              </div>

              <div className="flex items-center gap-3">
                <button
                  onClick={() => handleTriggerCalculation("RET-02")}
                  disabled={triggering || !selectedOptionId}
                  className="inline-flex items-center gap-2 rounded-lg bg-cyan-600 px-4 py-2 font-mono text-xs font-semibold text-white shadow-lg shadow-cyan-600/20 transition hover:bg-cyan-500 disabled:opacity-50"
                >
                  {triggering ? (
                    <>
                      <svg className="h-3.5 w-3.5 animate-spin" fill="none" viewBox="0 0 24 24">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                      </svg>
                      Calculating...
                    </>
                  ) : (
                    <>
                      Execute RET-02 Run
                      <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
                      </svg>
                    </>
                  )}
                </button>
              </div>
            </div>

            {triggerError && (
              <div className="mt-3 rounded border border-rose-500/30 bg-rose-500/10 p-2 text-xs text-rose-300 font-mono">
                {triggerError}
              </div>
            )}

            {triggerSuccessRunId && (
              <div className="mt-3 rounded border border-emerald-500/30 bg-emerald-500/10 p-2 text-xs text-emerald-300 font-mono">
                Calculation run #{triggerSuccessRunId} executed. Redirecting to analysis report...
              </div>
            )}

            <div className="mt-4 grid grid-cols-1 sm:grid-cols-3 gap-3 font-mono text-xs">
              <div className="rounded bg-zinc-950/60 p-2.5 border border-zinc-800">
                <span className="text-zinc-500 text-[10px] uppercase block">Implementation</span>
                <span className="text-cyan-300 font-semibold">CANDIDATE_V1</span>
              </div>
              <div className="rounded bg-zinc-950/60 p-2.5 border border-zinc-800">
                <span className="text-zinc-500 text-[10px] uppercase block">Production Validation</span>
                <span className="text-amber-400 font-semibold">STRICTLY NONE</span>
              </div>
              <div className="rounded bg-zinc-950/60 p-2.5 border border-zinc-800">
                <span className="text-zinc-500 text-[10px] uppercase block">Numerical Kernel</span>
                <span className="text-zinc-200">Python Vectorized</span>
              </div>
            </div>
          </div>

          {/* RET-03 Card: Implemented Candidate */}
          <div className="rounded-xl border border-cyan-500/30 bg-cyan-950/15 p-5 backdrop-blur-sm">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-cyan-500/20 pb-4">
              <div>
                <div className="flex items-center gap-2">
                  <span className="font-mono text-xs font-bold text-cyan-400">RET-03</span>
                  <h3 className="text-sm font-semibold text-zinc-100">3-Year CAGR</h3>
                  <span className="rounded bg-amber-500/20 px-2 py-0.5 font-mono text-[10px] font-semibold text-amber-300 border border-amber-500/30">
                    Candidate — Not Validated
                  </span>
                </div>
                <p className="mt-1 text-xs text-zinc-300 font-sans">
                  Formula: <code className="font-mono text-cyan-300">Return = (NAV_end / NAV_start)^(365.25 / elapsed) - 1</code>. Supporting 3-year annualized compound return primitive.
                </p>
              </div>

              <div className="flex items-center gap-3">
                <button
                  onClick={() => handleTriggerCalculation("RET-03")}
                  disabled={triggering || !selectedOptionId}
                  className="inline-flex items-center gap-2 rounded-lg bg-cyan-600 px-4 py-2 font-mono text-xs font-semibold text-white shadow-lg shadow-cyan-600/20 transition hover:bg-cyan-500 disabled:opacity-50"
                >
                  {triggering ? (
                    <>
                      <svg className="h-3.5 w-3.5 animate-spin" fill="none" viewBox="0 0 24 24">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                      </svg>
                      Calculating...
                    </>
                  ) : (
                    <>
                      Execute RET-03 Run
                      <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
                      </svg>
                    </>
                  )}
                </button>
              </div>
            </div>

            <div className="mt-4 grid grid-cols-1 sm:grid-cols-3 gap-3 font-mono text-xs">
              <div className="rounded bg-zinc-950/60 p-2.5 border border-zinc-800">
                <span className="text-zinc-500 text-[10px] uppercase block">Implementation</span>
                <span className="text-cyan-300 font-semibold">CANDIDATE_V1</span>
              </div>
              <div className="rounded bg-zinc-950/60 p-2.5 border border-zinc-800">
                <span className="text-zinc-500 text-[10px] uppercase block">Production Validation</span>
                <span className="text-amber-400 font-semibold">STRICTLY NONE</span>
              </div>
              <div className="rounded bg-zinc-950/60 p-2.5 border border-zinc-800">
                <span className="text-zinc-500 text-[10px] uppercase block">Numerical Kernel</span>
                <span className="text-zinc-200">Python Vectorized</span>
              </div>
            </div>
          </div>

          {/* Unimplemented Candidate Return Metrics */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-4">
              <div className="flex items-center justify-between">
                <span className="font-mono text-xs font-semibold text-zinc-300">RET-01: 1Y CAGR</span>
                <span className="text-[10px] font-mono text-amber-400 bg-amber-500/10 px-2 py-0.5 rounded border border-amber-500/20">
                  Candidate
                </span>
              </div>
              <p className="mt-2 text-xs text-zinc-500 italic">
                Candidate methodology — not validated for production.
              </p>
            </div>



            <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-4">
              <div className="flex items-center justify-between">
                <span className="font-mono text-xs font-semibold text-zinc-300">RET-04: 5Y CAGR</span>
                <span className="text-[10px] font-mono text-amber-400 bg-amber-500/10 px-2 py-0.5 rounded border border-amber-500/20">
                  Candidate
                </span>
              </div>
              <p className="mt-2 text-xs text-zinc-500 italic">
                Candidate methodology — not validated for production.
              </p>
            </div>

            <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-4">
              <div className="flex items-center justify-between">
                <span className="font-mono text-xs font-semibold text-zinc-300">RET-05: 3Y Rolling Return Mean</span>
                <span className="text-[10px] font-mono text-amber-400 bg-amber-500/10 px-2 py-0.5 rounded border border-amber-500/20">
                  Candidate
                </span>
              </div>
              <p className="mt-2 text-xs text-zinc-500 italic">
                Candidate methodology — not validated for production.
              </p>
            </div>

            <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-4">
              <div className="flex items-center justify-between">
                <span className="font-mono text-xs font-semibold text-zinc-300">RET-06: Rolling Outperformance %</span>
                <span className="text-[10px] font-mono text-amber-400 bg-amber-500/10 px-2 py-0.5 rounded border border-amber-500/20">
                  Candidate
                </span>
              </div>
              <p className="mt-2 text-xs text-zinc-500 italic">
                Candidate methodology — not validated for production.
              </p>
            </div>

            <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-4">
              <div className="flex items-center justify-between">
                <span className="font-mono text-xs font-semibold text-zinc-300">RET-07: 3Y Active Return</span>
                <span className="text-[10px] font-mono text-amber-400 bg-amber-500/10 px-2 py-0.5 rounded border border-amber-500/20">
                  Candidate
                </span>
              </div>
              <p className="mt-2 text-xs text-zinc-500 italic">
                Candidate methodology — not validated for production.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* 5. RISK ANALYSIS */}
      <section className="mb-10">
        <div className="font-mono text-[11px] uppercase tracking-wider text-cyan-400">Section 5</div>
        <h2 className="text-xl font-bold text-zinc-100">Risk Analysis</h2>
        <p className="text-xs text-zinc-400">
          Risk metrics require multi-year continuous daily observation histories and formal validation before rendering numerical values.
        </p>

        <div className="mt-4 space-y-4">
          {/* RSK-01 Card: Implemented Candidate */}
          <div className="rounded-xl border border-cyan-500/30 bg-cyan-950/15 p-5 backdrop-blur-sm">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-cyan-500/20 pb-4">
              <div>
                <div className="flex items-center gap-2">
                  <span className="font-mono text-xs font-bold text-cyan-400">RSK-01</span>
                  <h3 className="text-sm font-semibold text-zinc-100">3-Year Annualized Volatility</h3>
                  <span className="rounded bg-amber-500/20 px-2 py-0.5 font-mono text-[10px] font-semibold text-amber-300 border border-amber-500/30">
                    Candidate — Not Validated
                  </span>
                </div>
                <p className="mt-1 text-xs text-zinc-300 font-sans">
                  Formula: <code className="font-mono text-cyan-300">sigma_ann = sqrt(252) * sqrt( sum((r_t - r_bar)^2) / (N - 1) )</code>. Requires continuous 36M history ($\ge 700$ trading days).
                </p>
              </div>

              <div className="flex items-center gap-3">
                <button
                  onClick={() => handleTriggerCalculation("RSK-01")}
                  disabled={triggering || !selectedOptionId}
                  className="inline-flex items-center gap-2 rounded-lg bg-cyan-600 px-4 py-2 font-mono text-xs font-semibold text-white shadow-lg shadow-cyan-600/20 transition hover:bg-cyan-500 disabled:opacity-50"
                >
                  {triggering ? (
                    <>
                      <svg className="h-3.5 w-3.5 animate-spin" fill="none" viewBox="0 0 24 24">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                      </svg>
                      Calculating...
                    </>
                  ) : (
                    <>
                      Execute RSK-01 Run
                      <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
                      </svg>
                    </>
                  )}
                </button>
              </div>
            </div>

            <div className="mt-4 grid grid-cols-1 sm:grid-cols-4 gap-3 font-mono text-xs">
              <div className="rounded bg-zinc-950/60 p-2.5 border border-zinc-800">
                <span className="text-zinc-500 text-[10px] uppercase block">Implementation</span>
                <span className="text-cyan-300 font-semibold">CANDIDATE_V1</span>
              </div>
              <div className="rounded bg-zinc-950/60 p-2.5 border border-zinc-800">
                <span className="text-zinc-500 text-[10px] uppercase block">Annualizer Convention</span>
                <span className="text-amber-400 font-semibold">sqrt(252) Candidate</span>
              </div>
              <div className="rounded bg-zinc-950/60 p-2.5 border border-zinc-800">
                <span className="text-zinc-500 text-[10px] uppercase block">Denominator</span>
                <span className="text-amber-400 font-semibold">N - 1 Candidate</span>
              </div>
              <div className="rounded bg-zinc-950/60 p-2.5 border border-zinc-800">
                <span className="text-zinc-500 text-[10px] uppercase block">Numerical Kernel</span>
                <span className="text-zinc-200">Python Vectorized</span>
              </div>
            </div>
          </div>

          {/* Unimplemented Risk Metrics */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-4">
              <div className="flex items-center justify-between">
                <span className="font-mono text-xs font-semibold text-zinc-300">RSK-02: Downside Semideviation</span>
                <span className="text-[10px] font-mono text-amber-400 bg-amber-500/10 px-2 py-0.5 rounded border border-amber-500/20">
                  Candidate
                </span>
              </div>
              <p className="mt-2 text-xs text-zinc-500 italic">
                Candidate methodology — not validated for production.
              </p>
            </div>

            <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-4">
              <div className="flex items-center justify-between">
                <span className="font-mono text-xs font-semibold text-zinc-300">RSK-03: Maximum Drawdown 3Y</span>
                <span className="text-[10px] font-mono text-amber-400 bg-amber-500/10 px-2 py-0.5 rounded border border-amber-500/20">
                  Candidate
                </span>
              </div>
              <p className="mt-2 text-xs text-zinc-500 italic">
                Candidate methodology — not validated for production.
              </p>
            </div>

            <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-4">
              <div className="flex items-center justify-between">
                <span className="font-mono text-xs font-semibold text-zinc-300">RSK-04: Maximum Drawdown Duration</span>
                <span className="text-[10px] font-mono text-amber-400 bg-amber-500/10 px-2 py-0.5 rounded border border-amber-500/20">
                  Candidate
                </span>
              </div>
              <p className="mt-2 text-xs text-zinc-500 italic">
                Candidate methodology — not validated for production.
              </p>
            </div>

            <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-4">
              <div className="flex items-center justify-between">
                <span className="font-mono text-xs font-semibold text-zinc-300">RSK-05: Ulcer Index</span>
                <span className="text-[10px] font-mono text-amber-400 bg-amber-500/10 px-2 py-0.5 rounded border border-amber-500/20">
                  Candidate
                </span>
              </div>
              <p className="mt-2 text-xs text-zinc-500 italic">
                Candidate methodology — not validated for production.
              </p>
            </div>

            <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-4">
              <div className="flex items-center justify-between">
                <span className="font-mono text-xs font-semibold text-zinc-300">RSK-06: Historical VaR 95%</span>
                <span className="text-[10px] font-mono text-amber-400 bg-amber-500/10 px-2 py-0.5 rounded border border-amber-500/20">
                  Candidate
                </span>
              </div>
              <p className="mt-2 text-xs text-zinc-500 italic">
                Candidate methodology — not validated for production.
              </p>
            </div>

            <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-4">
              <div className="flex items-center justify-between">
                <span className="font-mono text-xs font-semibold text-zinc-300">RSK-07: Expected Shortfall 95%</span>
                <span className="text-[10px] font-mono text-amber-400 bg-amber-500/10 px-2 py-0.5 rounded border border-amber-500/20">
                  Candidate
                </span>
              </div>
              <p className="mt-2 text-xs text-zinc-500 italic">
                Candidate methodology — not validated for production.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* 6. PORTFOLIO ANALYSIS */}
      <section className="mb-10">
        <div className="font-mono text-[11px] uppercase tracking-wider text-cyan-400">Section 6</div>
        <h2 className="text-xl font-bold text-zinc-100">Portfolio Analysis</h2>
        <p className="text-xs text-zinc-400">
          Portfolio structure intelligence depends on SEBI monthly holding disclosures, scheduled for Phase 3 ingestion.
        </p>

        <div className="mt-4 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 font-mono text-xs">
          <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-4">
            <span className="text-zinc-400 font-semibold block">PRT-01: Top-10 Concentration</span>
            <span className="mt-2 inline-block text-[11px] text-zinc-500 font-sans italic">
              Not yet available (Portfolio holdings ingestion planned for Phase 3).
            </span>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-4">
            <span className="text-zinc-400 font-semibold block">PRT-02: Effective Number of Holdings</span>
            <span className="mt-2 inline-block text-[11px] text-zinc-500 font-sans italic">
              Not yet available (Benchmark portfolio reconciliation pending).
            </span>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-4">
            <span className="text-zinc-400 font-semibold block">PRT-03: Active Share</span>
            <span className="mt-2 inline-block text-[11px] text-zinc-500 font-sans italic">
              Not yet available.
            </span>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-4">
            <span className="text-zinc-400 font-semibold block">PRT-04: Monthly Weight Turnover</span>
            <span className="mt-2 inline-block text-[11px] text-zinc-500 font-sans italic">
              Not yet available.
            </span>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-4">
            <span className="text-zinc-400 font-semibold block">PRT-05: Cash & Equivalent Allocation %</span>
            <span className="mt-2 inline-block text-[11px] text-zinc-500 font-sans italic">
              Not yet available.
            </span>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-4">
            <span className="text-zinc-400 font-semibold block">GOV-01: Direct Plan TER</span>
            <span className="mt-2 inline-block text-[11px] text-zinc-500 font-sans italic">
              Not yet available.
            </span>
          </div>
        </div>
      </section>

      {/* 7. METHODOLOGY GOVERNANCE */}
      <section className="mb-10">
        <div className="font-mono text-[11px] uppercase tracking-wider text-cyan-400">Section 7</div>
        <h2 className="text-xl font-bold text-zinc-100">Methodology & Governance</h2>
        <div className="mt-4 rounded-xl border border-zinc-800 bg-zinc-900/40 p-5 font-mono text-xs space-y-3">
          <div className="flex items-center justify-between pb-3 border-b border-zinc-800">
            <span className="font-semibold text-zinc-200">GOVERNANCE TIER SUMMARY</span>
            <span className="text-amber-400 text-[11px]">CANDIDATE STAGE</span>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
              <span className="text-zinc-500 text-[10px] uppercase block">Implemented Candidate</span>
              <span className="text-cyan-300 font-semibold">RET-02, RET-03, RSK-01 (CANDIDATE_V1)</span>
            </div>
            <div>
              <span className="text-zinc-500 text-[10px] uppercase block">Validated Methodologies</span>
              <span className="text-zinc-400 font-semibold">STRICTLY NONE</span>
            </div>
            <div>
              <span className="text-zinc-500 text-[10px] uppercase block">Approved Production</span>
              <span className="text-zinc-400 font-semibold">STRICTLY NONE</span>
            </div>
          </div>
          <p className="text-zinc-400 font-sans text-xs pt-2">
            The Phase 2H methodology specification defines all 30 candidate metrics with mathematical formulas, lookback parameters, and missing-data conventions. Verification against historical market data has not yet commenced.
          </p>
        </div>
      </section>

      {/* 8. EVIDENCE / PROVENANCE */}
      <section className="mb-10">
        <div className="font-mono text-[11px] uppercase tracking-wider text-cyan-400">Section 8</div>
        <h2 className="text-xl font-bold text-zinc-100">Evidence & Data Provenance</h2>
        <div className="mt-4 rounded-xl border border-zinc-800 bg-zinc-900/60 p-5 font-mono text-xs space-y-3">
          <p className="text-zinc-300 font-sans text-xs">
            Every metric computed by YUKIRA records cryptographic proof linking it directly back to official primary source documents:
          </p>
          <div className="space-y-2 pt-2 text-zinc-400">
            <div className="flex items-start gap-2">
              <span className="text-cyan-400 font-bold">•</span>
              <span><strong>Raw Ingestion Archive:</strong> Full AMFI data feeds stored with SHA-256 digests and HTTP headers.</span>
            </div>
            <div className="flex items-start gap-2">
              <span className="text-cyan-400 font-bold">•</span>
              <span><strong>Bitemporal Ledger:</strong> Exact observation IDs, effective dates, and revision sequence tracked.</span>
            </div>
            <div className="flex items-start gap-2">
              <span className="text-cyan-400 font-bold">•</span>
              <span><strong>Calculation Fingerprint:</strong> Execution timestamp, Python Quant Engine git commit, and snapshot hash saved.</span>
            </div>
          </div>
        </div>
      </section>

      {/* 9. LIMITATIONS */}
      <section className="mb-12">
        <div className="font-mono text-[11px] uppercase tracking-wider text-cyan-400">Section 9</div>
        <h2 className="text-xl font-bold text-zinc-100">Analytical Limitations</h2>
        <div className="mt-4 rounded-xl border border-zinc-800 bg-zinc-900/40 p-5 text-xs text-zinc-400 space-y-2 font-sans leading-relaxed">
          <p>
            <strong className="text-zinc-200">No Benchmark Attribution:</strong> Broad-market benchmark comparison (Beta, Alpha, Information Ratio) cannot be calculated until official TRI benchmark series are integrated in Phase 3.
          </p>
          <p>
            <strong className="text-zinc-200">No Risk-Adjusted Ratings:</strong> Sharpe, Sortino, and Treynor ratios require daily risk-free rate series (FBIL 91D T-bill) which are not yet ingested.
          </p>
          <p>
            <strong className="text-zinc-200">No Commercial Advice:</strong> Information presented here is for quantitative institutional investigation only and does not constitute investment advice, ratings, or recommendations.
          </p>
        </div>
      </section>
    </PageContainer>
  );
}
