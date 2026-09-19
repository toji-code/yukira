"use client";

import { use, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { PageContainer } from "@/components/layout/PageContainer";
import { StateView } from "@/components/epistemic/StateView";
import { fetchSchemeById, fetchAllSchemeOptions } from "@/lib/api/schemes";
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

  // Trigger calculation form state
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
      fetchAllSchemeOptions(),
    ])
      .then(([schemeData, allOptions]) => {
        if (active) {
          setScheme(schemeData);
          const filteredOptions = allOptions.filter(
            (opt) => opt.plan?.scheme?.id === schemeId
          );
          setOptions(filteredOptions.length > 0 ? filteredOptions : allOptions);
          if (allOptions.length > 0) {
            setSelectedOptionId(allOptions[0].id);
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

  const handleTriggerCalculation = async () => {
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
        asOfDate: "2026-03-31",
        knowledgeCutoffTime: new Date().toISOString(),
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
      <PageContainer title="Scheme Record">
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
      <PageContainer title="Scheme Not Found">
        <StateView
          kind="unavailable"
          title="Scheme Not Found"
          message={error || `Scheme #${schemeId} does not exist in master records.`}
          action={
            <button
              onClick={() => router.push("/funds")}
              className="inline-flex rounded-md bg-zinc-800 px-3 py-1.5 text-xs font-medium text-zinc-200 hover:bg-zinc-700"
            >
              Back to Catalog
            </button>
          }
        />
      </PageContainer>
    );
  }

  return (
    <PageContainer
      title={scheme.name}
      subtitle={`Code: ${scheme.code} • Master ID: ${scheme.id}`}
      breadcrumbs={[
        { label: "Funds", href: "/funds" },
        { label: scheme.code, href: `/funds/${scheme.id}` },
      ]}
    >
      {/* Scheme Metadata Grid */}
      <div className="grid grid-cols-1 gap-6 md:grid-cols-4">
        <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-5 backdrop-blur-sm">
          <div className="text-xs font-mono uppercase tracking-wider text-zinc-500">Asset Management Co.</div>
          <div className="mt-1 text-sm font-semibold text-zinc-100">{scheme.amc?.name || "—"}</div>
          <div className="mt-1 text-xs text-zinc-400">Code: {scheme.amc?.code || "—"}</div>
        </div>

        <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-5 backdrop-blur-sm">
          <div className="text-xs font-mono uppercase tracking-wider text-zinc-500">Inception Date</div>
          <div className="mt-1 text-sm font-semibold text-zinc-100">{scheme.inceptionDate || "—"}</div>
          <div className="mt-1 text-xs text-zinc-400">Formal Registration Date</div>
        </div>

        <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-5 backdrop-blur-sm">
          <div className="text-xs font-mono uppercase tracking-wider text-zinc-500">Master Record Status</div>
          <div className="mt-1 flex items-center gap-2">
            <span className={`inline-block h-2.5 w-2.5 rounded-full ${scheme.status === "ACTIVE" ? "bg-emerald-500" : "bg-zinc-600"}`} />
            <span className="text-sm font-semibold text-zinc-100">{scheme.status}</span>
          </div>
          <div className="mt-1 text-xs text-zinc-400">Validated In Master</div>
        </div>

        <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-5 backdrop-blur-sm">
          <div className="text-xs font-mono uppercase tracking-wider text-zinc-500">Associated Benchmark</div>
          <div className="mt-1 text-sm font-semibold text-zinc-100">Benchmark #1</div>
          <div className="mt-1 text-xs text-zinc-400">Mandated Broad-Market Reference</div>
        </div>
      </div>

      {/* Available Plans & Options */}
      <div className="mt-8 rounded-xl border border-zinc-800 bg-zinc-900/60 p-6 backdrop-blur-sm">
        <h3 className="text-sm font-semibold uppercase tracking-wider text-zinc-300">
          Registered Scheme Plans & Options ({options.length})
        </h3>
        <p className="mt-1 text-xs text-zinc-400">
          Point-in-time quantitative metrics are computed on specific share classes (Direct / Regular; Growth / IDCW).
        </p>

        {options.length === 0 ? (
          <div className="mt-4">
            <StateView
              kind="empty"
              title="No Share Classes Registered"
              message="There are no registered plans or options for this scheme in the database."
            />
          </div>
        ) : (
          <div className="mt-4 overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-zinc-800 bg-zinc-950/60 font-mono uppercase tracking-wider text-zinc-400">
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
              <tbody className="divide-y divide-zinc-800/60 font-mono">
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
                    <td className="px-4 py-2.5 font-medium text-cyan-400">
                      Option #{opt.id}
                    </td>
                    <td className="px-4 py-2.5 text-zinc-300">
                      {opt.plan?.planType || "—"}
                    </td>
                    <td className="px-4 py-2.5 text-zinc-300">
                      {opt.optionType || "—"}
                    </td>
                    <td className="px-4 py-2.5 text-zinc-400">
                      {opt.amfiCode || "—"}
                    </td>
                    <td className="px-4 py-2.5 text-zinc-400">
                      {opt.isin || "—"}
                    </td>
                    <td className="px-4 py-2.5">
                      <span className={`inline-flex rounded px-1.5 py-0.5 text-[10px] font-medium ${
                        opt.status === "ACTIVE" ? "bg-emerald-500/20 text-emerald-400" : "bg-zinc-800 text-zinc-500"
                      }`}>
                        {opt.status}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Point-in-Time Quantitative Run Trigger Section */}
      <div className="mt-8 rounded-xl border border-cyan-500/20 bg-cyan-950/10 p-6 backdrop-blur-sm">
        <h3 className="text-sm font-semibold uppercase tracking-wider text-cyan-400">
          Trigger Point-in-Time Quantitative Calculation
        </h3>
        <p className="mt-1 text-xs text-zinc-300">
          Initiate an authoritative point-in-time calculation orchestrator run. The Spring Boot backend resolves observations at knowledge cutoff and executes the validated Python Quant Engine kernel.
        </p>

        {triggerError && (
          <div className="mt-4 rounded-lg border border-red-500/30 bg-red-500/10 p-3 text-xs text-red-400">
            {triggerError}
          </div>
        )}

        {triggerSuccessRunId && (
          <div className="mt-4 rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-3 text-xs text-emerald-300">
            Calculation Run #{triggerSuccessRunId} initiated successfully. Navigating to analysis report...
          </div>
        )}

        <div className="mt-5 flex flex-wrap items-center gap-4">
          <button
            onClick={handleTriggerCalculation}
            disabled={triggering || !selectedOptionId}
            className="inline-flex items-center gap-2 rounded-lg bg-cyan-600 px-5 py-2.5 text-xs font-semibold text-white shadow-lg shadow-cyan-600/20 transition hover:bg-cyan-500 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {triggering ? (
              <>
                <svg className="h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                </svg>
                Executing Python Quant Engine...
              </>
            ) : (
              <>
                <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z" />
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                Execute Quantitative Run (Selected Option #{selectedOptionId || "—"})
              </>
            )}
          </button>

          <Link
            href="/funds"
            className="rounded-lg border border-zinc-800 bg-zinc-900 px-4 py-2.5 text-xs font-medium text-zinc-400 transition hover:bg-zinc-800 hover:text-zinc-200"
          >
            Back to Catalog
          </Link>
        </div>
      </div>
    </PageContainer>
  );
}
