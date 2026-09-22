"use client";

import { use, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { PageContainer } from "@/components/layout/PageContainer";
import { StateView } from "@/components/epistemic/StateView";
import { MethodologyBadge } from "@/components/epistemic/MethodologyBadge";
import { DataQualityBadge } from "@/components/epistemic/DataQualityBadge";
import { ProgressiveDisclosure } from "@/components/disclosure/ProgressiveDisclosure";
import { MetricValueDisplay } from "@/components/primitives/MetricValueDisplay";
import { fetchRet02Analysis, executeRet02Analysis } from "@/lib/api/analysis";
import { Ret02AnalysisResponse } from "@/types/analysis";
import { formatDateTime } from "@/lib/utils/formatters";

interface PageProps {
  params: Promise<{ id: string }>;
}

export default function CalculationAnalysisPage({ params }: PageProps) {
  const resolvedParams = use(params);
  const runId = parseInt(resolvedParams.id, 10);
  const router = useRouter();

  const [data, setData] = useState<Ret02AnalysisResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Parameterized run state
  const [showParamPanel, setShowParamPanel] = useState(false);
  const [customStartDate, setCustomStartDate] = useState("2024-01-01");
  const [customEndDate, setCustomEndDate] = useState("2024-01-15");
  const [customCutoff, setCustomCutoff] = useState("2024-01-31T23:59:59+05:30");
  const [executingCustom, setExecutingCustom] = useState(false);
  const [customError, setCustomError] = useState<string | null>(null);

  const reloadAnalysis = () => {
    if (isNaN(runId)) {
      setError("Invalid calculation run ID");
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    fetchRet02Analysis(runId)
      .then((res) => {
        setData(res);
        if (res.period.requestedStartDate) setCustomStartDate(res.period.requestedStartDate);
        if (res.period.requestedEndDate) setCustomEndDate(res.period.requestedEndDate);
        if (res.pit.knowledgeCutoffTime) setCustomCutoff(res.pit.knowledgeCutoffTime);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : "Failed to load calculation run analysis");
      })
      .finally(() => {
        setLoading(false);
      });
  };

  useEffect(() => {
    if (isNaN(runId)) {
      return;
    }
    let active = true;
    fetchRet02Analysis(runId)
      .then((res) => {
        if (active) {
          setData(res);
          if (res.period.requestedStartDate) setCustomStartDate(res.period.requestedStartDate);
          if (res.period.requestedEndDate) setCustomEndDate(res.period.requestedEndDate);
          if (res.pit.knowledgeCutoffTime) setCustomCutoff(res.pit.knowledgeCutoffTime);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (active) {
          setError(err instanceof Error ? err.message : "Failed to load calculation run analysis");
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [runId]);

  const handleExecuteParameterizedRun = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!data?.identity.schemeOptionId) return;

    setExecutingCustom(true);
    setCustomError(null);

    try {
      const response = await executeRet02Analysis({
        schemeOptionId: data.identity.schemeOptionId,
        startDate: customStartDate,
        endDate: customEndDate,
        knowledgeCutoffTime: customCutoff,
        methodologyTag: "CANDIDATE_V1",
      });
      setShowParamPanel(false);
      router.push(`/analysis/${response.provenance.calculationRunId}`);
    } catch (err: unknown) {
      setCustomError(err instanceof Error ? err.message : "Calculation failed");
    } finally {
      setExecutingCustom(false);
    }
  };

  if (loading) {
    return (
      <PageContainer title="Quantitative Analysis">
        <StateView
          kind="loading"
          title="Retrieving Authoritative Quantitative Audit"
          message={`Fetching calculation run #${runId} and authoritative RET-02 audit manifest from backend...`}
        />
      </PageContainer>
    );
  }

  if (error || !data) {
    return (
      <PageContainer title="Analysis Run Unavailable">
        <StateView
          kind="unavailable"
          title="Calculation Run Unavailable"
          message={error || `Calculation Run #${runId} could not be retrieved from the backend.`}
          action={
            <button
              onClick={reloadAnalysis}
              className="inline-flex rounded-md bg-zinc-800 px-3 py-1.5 text-xs font-medium text-zinc-200 hover:bg-zinc-700"
            >
              Try Again
            </button>
          }
        />
      </PageContainer>
    );
  }

  const isCalculated = data.result.calculationStatus === "CALCULATED";
  const isInsufficient = data.result.calculationStatus === "INSUFFICIENT_DATA" || data.limitations.insufficientEvidence;

  return (
    <PageContainer
      title={`Analysis Run #${data.provenance.calculationRunId}`}
      subtitle={`${data.identity.schemeName} — Option #${data.identity.schemeOptionId || "—"} (${data.identity.optionType})`}
      breadcrumbs={[
        { label: "Funds", href: "/funds" },
        { label: `Run #${data.provenance.calculationRunId}`, href: `/analysis/${data.provenance.calculationRunId}` },
      ]}
    >
      {/* Top Banner: Epistemic Invariants & Governance */}
      <div className="mb-6 space-y-3">
        <div className="rounded-lg border border-amber-500/40 bg-amber-500/10 px-4 py-3.5 text-xs text-amber-200">
          <div className="flex items-center gap-2 font-bold uppercase tracking-wider text-[11px] text-amber-400 font-mono">
            <span className="inline-block h-2 w-2 rounded-full bg-amber-400 animate-pulse" />
            CANDIDATE METHODOLOGY — NOT VALIDATED FOR PRODUCTION
          </div>
          <p className="mt-1 text-zinc-300 font-sans leading-relaxed text-xs">
            <strong className="text-white">Implemented ≠ Validated ≠ Approved:</strong> This calculation ({data.methodology.methodologyCode}) was computed deterministically by the Python quantitative engine using candidate methodology specifications. YUKIRA currently has <strong className="text-white">strictly zero validated production methodologies</strong> and <strong className="text-white">exactly zero empirical findings</strong>. This metric does not constitute an investment recommendation, rating, or commercial advice.
          </p>
        </div>

        {/* Temporal Limitation Disclosure Banner */}
        <div className="rounded-lg border border-zinc-700 bg-zinc-900/80 px-4 py-3 text-xs text-zinc-300">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="font-mono font-semibold text-zinc-400 uppercase tracking-wider text-[11px]">
                PIT & Temporal Limitation:
              </span>
              <span>{data.pit.temporalLimitationDisclosure}</span>
            </div>
            <span className="font-mono text-[10px] text-zinc-500 bg-zinc-800 px-2 py-0.5 rounded">
              {data.pit.cutoffConventionApplied}
            </span>
          </div>
        </div>
      </div>

      {/* Top Controls: Re-run & Parameter Launcher */}
      <div className="mb-6 flex flex-wrap items-center justify-between gap-4 border-b border-zinc-800 pb-4">
        <div className="flex items-center gap-3">
          <span className="font-mono text-xs text-zinc-400">Status:</span>
          <span
            className={`inline-flex items-center rounded px-2.5 py-1 text-xs font-mono font-semibold ${
              isCalculated
                ? "bg-emerald-500/20 text-emerald-400 border border-emerald-500/40"
                : isInsufficient
                ? "bg-amber-500/20 text-amber-400 border border-amber-500/40"
                : "bg-rose-500/20 text-rose-400 border border-rose-500/40"
            }`}
          >
            {data.result.calculationStatus}
          </span>

          <MethodologyBadge
            status={data.methodology.approvalStatus}
            convention={data.methodology.methodologyVersion}
          />
        </div>

        <button
          onClick={() => setShowParamPanel(!showParamPanel)}
          className="rounded border border-zinc-700 bg-zinc-800/80 px-3 py-1 text-xs font-mono text-zinc-200 hover:bg-zinc-700"
        >
          {showParamPanel ? "Close Parameter Panel" : "Run Parameterized RET-02"}
        </button>
      </div>

      {/* Parameterized Calculation Form */}
      {showParamPanel && (
        <form onSubmit={handleExecuteParameterizedRun} className="mb-8 rounded-xl border border-zinc-800 bg-zinc-900/90 p-5 font-mono text-xs">
          <h4 className="font-semibold text-zinc-200 uppercase tracking-wider mb-4">
            Trigger Parameterized RET-02 Calculation
          </h4>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <label className="block text-zinc-400 mb-1">Requested Start Date</label>
              <input
                type="date"
                value={customStartDate}
                onChange={(e) => setCustomStartDate(e.target.value)}
                required
                className="w-full rounded border border-zinc-700 bg-zinc-950 px-3 py-1.5 text-zinc-200"
              />
            </div>
            <div>
              <label className="block text-zinc-400 mb-1">Requested End Date</label>
              <input
                type="date"
                value={customEndDate}
                onChange={(e) => setCustomEndDate(e.target.value)}
                required
                className="w-full rounded border border-zinc-700 bg-zinc-950 px-3 py-1.5 text-zinc-200"
              />
            </div>
            <div>
              <label className="block text-zinc-400 mb-1">Knowledge Cutoff Time (ISO)</label>
              <input
                type="text"
                value={customCutoff}
                onChange={(e) => setCustomCutoff(e.target.value)}
                required
                className="w-full rounded border border-zinc-700 bg-zinc-950 px-3 py-1.5 text-zinc-200"
              />
            </div>
          </div>
          {customError && (
            <div className="mt-3 text-rose-400 text-[11px]">{customError}</div>
          )}
          <div className="mt-4 flex justify-end gap-3">
            <button
              type="button"
              onClick={() => setShowParamPanel(false)}
              className="rounded px-3 py-1 text-zinc-400 hover:text-zinc-200"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={executingCustom}
              className="rounded bg-cyan-600 px-4 py-1.5 font-semibold text-white hover:bg-cyan-500 disabled:opacity-50"
            >
              {executingCustom ? "Executing Quant Engine..." : "Execute Calculation"}
            </button>
          </div>
        </form>
      )}

      {/* 3-Level Progressive Disclosure */}
      <ProgressiveDisclosure
        level1={
          <div className="space-y-6">
            {/* Primary KPI Display */}
            <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-6">
              <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div className="space-y-4">
                  <MetricValueDisplay
                    label={`${data.result.metricCode} ${data.result.metricName}`}
                    value={data.result.numericValue}
                    units="PERCENTAGE"
                    isCandidate={true}
                    description="Authoritative discrete period return from backend quant engine: (NAV_end - NAV_start) / NAV_start. Frontend displays authoritative result without client calculation."
                  />
                  <div className="font-mono text-xs text-zinc-400">
                    Status: <span className="font-semibold text-zinc-200">{data.result.calculationStatus}</span>
                  </div>
                </div>

                <div className="rounded-lg border border-zinc-800 bg-zinc-950/60 p-4 font-mono text-xs space-y-2">
                  <div className="text-zinc-500 uppercase tracking-wider text-[10px]">
                    Evaluation Window
                  </div>
                  <div>
                    <span className="text-zinc-400">Requested: </span>
                    <span className="text-zinc-200 font-semibold">
                      {data.period.requestedStartDate || "—"} → {data.period.requestedEndDate || "—"}
                    </span>
                  </div>
                  <div>
                    <span className="text-zinc-400">Selected PIT: </span>
                    <span className="text-cyan-400 font-semibold">
                      {data.period.selectedStartDate || "—"} → {data.period.selectedEndDate || "—"}
                    </span>
                  </div>
                </div>
              </div>

              {isInsufficient && data.result.errorMessage && (
                <div className="mt-6 rounded-lg border border-amber-500/30 bg-amber-500/10 p-4 text-xs text-amber-300 font-mono">
                  <span className="font-bold uppercase">Evidence Limitation: </span>
                  {data.result.errorMessage}
                </div>
              )}
            </div>

            {/* Scheme Metadata Summary */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 font-mono text-xs">
              <div className="rounded-lg border border-zinc-800 bg-zinc-900/40 p-3">
                <span className="text-zinc-500 text-[10px] uppercase block">AMFI Code</span>
                <span className="text-zinc-200 font-semibold">{data.identity.amfiCode || "—"}</span>
              </div>
              <div className="rounded-lg border border-zinc-800 bg-zinc-900/40 p-3">
                <span className="text-zinc-500 text-[10px] uppercase block">ISIN</span>
                <span className="text-zinc-200 font-semibold">{data.identity.isin || "—"}</span>
              </div>
              <div className="rounded-lg border border-zinc-800 bg-zinc-900/40 p-3">
                <span className="text-zinc-500 text-[10px] uppercase block">Option Type</span>
                <span className="text-zinc-200 font-semibold">{data.identity.optionType}</span>
              </div>
              <div className="rounded-lg border border-zinc-800 bg-zinc-900/40 p-3">
                <span className="text-zinc-500 text-[10px] uppercase block">Candidate Version</span>
                <span className="text-amber-400 font-semibold">{data.methodology.methodologyVersion}</span>
              </div>
            </div>
          </div>
        }
        level2={
          <div className="space-y-6">
            <h4 className="font-mono text-xs uppercase tracking-wider text-zinc-400">
              Evidence Base & Lookback Resolution
            </h4>

            {/* Boundary NAV Evidence Cards */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6 font-mono text-xs">
              <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-5">
                <div className="flex items-center justify-between pb-3 border-b border-zinc-800">
                  <span className="font-semibold text-zinc-300 uppercase tracking-wider">
                    Start Observation (NAV_start)
                  </span>
                  <span className="text-[10px] text-zinc-500 bg-zinc-800 px-2 py-0.5 rounded">
                    {data.period.startSubstituted
                      ? `${data.period.startLookbackDaysUsed}d lookback substitution`
                      : "Exact date match"}
                  </span>
                </div>
                <div className="mt-4 space-y-2">
                  <div className="flex justify-between">
                    <span className="text-zinc-500">Requested Target:</span>
                    <span className="text-zinc-300">{data.period.requestedStartDate || "—"}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-zinc-500">Selected Effective:</span>
                    <span className="text-cyan-400 font-semibold">{data.period.selectedStartDate || "—"}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-zinc-500">Lookback Days Used:</span>
                    <span className="text-zinc-300">{data.period.startLookbackDaysUsed} / 4 calendar days max</span>
                  </div>
                </div>
              </div>

              <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-5">
                <div className="flex items-center justify-between pb-3 border-b border-zinc-800">
                  <span className="font-semibold text-zinc-300 uppercase tracking-wider">
                    End Observation (NAV_end)
                  </span>
                  <span className="text-[10px] text-zinc-500 bg-zinc-800 px-2 py-0.5 rounded">
                    {data.period.endSubstituted
                      ? `${data.period.endLookbackDaysUsed}d lookback substitution`
                      : "Exact date match"}
                  </span>
                </div>
                <div className="mt-4 space-y-2">
                  <div className="flex justify-between">
                    <span className="text-zinc-500">Requested Target:</span>
                    <span className="text-zinc-300">{data.period.requestedEndDate || "—"}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-zinc-500">Selected Effective:</span>
                    <span className="text-cyan-400 font-semibold">{data.period.selectedEndDate || "—"}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-zinc-500">Lookback Days Used:</span>
                    <span className="text-zinc-300">{data.period.endLookbackDaysUsed} / 4 calendar days max</span>
                  </div>
                </div>
              </div>
            </div>

            {/* Benchmark Disclosure Card */}
            <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-5 font-mono text-xs">
              <div className="flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-zinc-500" />
                <span className="font-semibold text-zinc-300 uppercase tracking-wider">
                  Benchmark Status: NOT REQUIRED / NOT USED
                </span>
              </div>
              <p className="mt-2 text-zinc-400 leading-relaxed">
                {data.benchmark.benchmarkNotice}
              </p>
              <div className="mt-3 text-[11px] text-zinc-500">
                Database constraint verification: <code className="text-zinc-400">calculation_run.benchmark_id = null</code> (Flyway V6 validated). No synthetic entities created.
              </div>
            </div>

            {/* Methodology Specification */}
            <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-5 font-mono text-xs space-y-2">
              <span className="font-semibold text-zinc-300 uppercase tracking-wider block mb-2">
                Methodology Specification ({data.methodology.methodologyCode})
              </span>
              <div className="text-zinc-400">
                <span className="text-zinc-500">Formula: </span>
                <span className="text-zinc-200">{data.methodology.formulaDisclosure}</span>
              </div>
              <div className="text-zinc-400">
                <span className="text-zinc-500">Window Rule: </span>
                <span className="text-zinc-200">{data.methodology.lookbackSpecification}</span>
              </div>
              <div className="text-zinc-400">
                <span className="text-zinc-500">Knowledge Cutoff: </span>
                <span className="text-zinc-200">{formatDateTime(data.pit.knowledgeCutoffTime)}</span>
              </div>
            </div>
          </div>
        }
        level3={
          <div className="space-y-6">
            <h4 className="font-mono text-xs uppercase tracking-wider text-zinc-400">
              Institutional Audit & Deep Provenance
            </h4>

            {/* 6-Dimension Quality Grid */}
            <div>
              <span className="font-mono text-xs text-zinc-400 block mb-3 uppercase tracking-wider">
                Approved 6-Dimensional Data Quality States
              </span>
              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
                {data.quality.dimensions.map((dim) => (
                  <div key={dim.dimension} className="rounded-lg border border-zinc-800 bg-zinc-900/40 p-3 font-mono text-xs">
                    <span className="text-zinc-500 text-[10px] uppercase block mb-1">{dim.dimension}</span>
                    <DataQualityBadge
                      type={
                        dim.dimension.toLowerCase() === "quality" ? "assessment"
                        : dim.dimension.toLowerCase() === "verification" ? "verification"
                        : dim.dimension.toLowerCase() === "revision" ? "revision"
                        : dim.dimension.toLowerCase() === "freshness" ? "temporal"
                        : dim.dimension.toLowerCase() === "presence" ? "presence"
                        : "integrity"
                      }
                      status={dim.state}
                    />
                    <p className="mt-2 text-[10px] text-zinc-500 line-clamp-2" title={dim.description}>
                      {dim.description}
                    </p>
                  </div>
                ))}
              </div>
            </div>

            {/* Run Provenance Details */}
            <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-5 font-mono text-xs space-y-4">
              <div className="flex items-center justify-between border-b border-zinc-800 pb-3">
                <span className="font-semibold text-zinc-300 uppercase tracking-wider">
                  Calculation Run #{data.provenance.calculationRunId}
                </span>
                <span className="text-zinc-500 text-[11px]">
                  Started: {formatDateTime(data.provenance.executionStartedAt)}
                </span>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-zinc-400">
                <div>
                  <span className="text-zinc-500 text-[10px] uppercase block mb-1">
                    Input Snapshot SHA-256 Digest
                  </span>
                  <span className="text-zinc-200 break-all select-all block bg-zinc-950 p-2 rounded border border-zinc-800">
                    {data.provenance.inputSnapshotSha256 || "—"}
                  </span>
                </div>
                <div>
                  <span className="text-zinc-500 text-[10px] uppercase block mb-1">
                    Quant Engine Version
                  </span>
                  <span className="text-zinc-200 block bg-zinc-950 p-2 rounded border border-zinc-800">
                    {data.provenance.quantEngineVersion}
                  </span>
                </div>
              </div>
            </div>

            {/* Input Observations Lineage Table */}
            <div>
              <span className="font-mono text-xs text-zinc-400 block mb-3 uppercase tracking-wider">
                Authoritative Input Observations ({data.provenance.inputObservations.length} Lineage Records)
              </span>

              {data.provenance.inputObservations.length === 0 ? (
                <div className="rounded-lg border border-zinc-800 bg-zinc-900/30 p-4 font-mono text-xs text-zinc-500 text-center">
                  Zero input observations recorded for this run.
                </div>
              ) : (
                <div className="overflow-x-auto rounded-xl border border-zinc-800 bg-zinc-900/60 font-mono text-xs">
                  <table className="w-full text-left">
                    <thead className="border-b border-zinc-800 bg-zinc-950/60 uppercase tracking-wider text-zinc-400">
                      <tr>
                        <th className="px-4 py-3">Role</th>
                        <th className="px-4 py-3">Obs ID</th>
                        <th className="px-4 py-3">Effective Date</th>
                        <th className="px-4 py-3">Rev Seq</th>
                        <th className="px-4 py-3">NAV Value (INR)</th>
                        <th className="px-4 py-3">Quality</th>
                        <th className="px-4 py-3">Temporal Status</th>
                        <th className="px-4 py-3">Source Artifact Hash</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-zinc-800/60">
                      {data.provenance.inputObservations.map((obs) => (
                        <tr key={obs.observationId} className="hover:bg-zinc-800/30">
                          <td className="px-4 py-3 font-semibold text-cyan-400">
                            {obs.role}
                          </td>
                          <td className="px-4 py-3 text-zinc-400">
                            #{obs.observationId}
                          </td>
                          <td className="px-4 py-3 text-zinc-200">
                            {obs.effectiveDate}
                          </td>
                          <td className="px-4 py-3 text-zinc-400">
                            v{obs.revisionSeq}
                          </td>
                          <td className="px-4 py-3 text-zinc-100 font-bold">
                            {obs.navValue !== null && obs.navValue !== undefined
                              ? Number(obs.navValue).toFixed(4)
                              : "—"}
                          </td>
                          <td className="px-4 py-3">
                            <DataQualityBadge type="assessment" status={obs.qualityAssessment} size="sm" />
                          </td>
                          <td className="px-4 py-3 text-zinc-400">
                            {obs.temporalStatus}
                          </td>
                          <td className="px-4 py-3 font-mono text-[10px] text-zinc-500">
                            {obs.sourceArtifactSha256
                              ? obs.sourceArtifactSha256.substring(0, 12) + "..."
                              : "unlinked"}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            {/* Source Artifacts Provenance */}
            {data.provenance.sourceArtifacts.length > 0 && (
              <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-5 font-mono text-xs space-y-3">
                <span className="font-semibold text-zinc-300 uppercase tracking-wider block">
                  Raw Ingestion Source Artifacts
                </span>
                {data.provenance.sourceArtifacts.map((art) => (
                  <div key={art.sourceArtifactId} className="rounded-lg border border-zinc-800 bg-zinc-950 p-3 space-y-1">
                    <div className="flex justify-between">
                      <span className="text-zinc-400">Artifact #{art.sourceArtifactId}</span>
                      <span className="text-zinc-500 text-[10px]">{art.byteSize} bytes</span>
                    </div>
                    <div className="text-zinc-500 text-[11px] break-all">
                      SHA-256: <span className="text-zinc-300 select-all">{art.sha256Hash}</span>
                    </div>
                    <div className="text-zinc-500 text-[10px]">
                      Source: {art.sourceUrl} | Retrieved: {formatDateTime(art.retrievalTimestamp)}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        }
      />
    </PageContainer>
  );
}
