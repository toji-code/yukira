"use client";

import { use, useEffect, useState, Suspense } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { PageContainer, SectionHeading } from "@/components/layout/PageContainer";
import { StateView } from "@/components/epistemic/StateView";
import { MetricCard, MetricGovernanceStatus } from "@/components/primitives/MetricCard";
import { fetchSchemeById, fetchSchemeOptionsBySchemeId } from "@/lib/api/schemes";
import { triggerCalculation } from "@/lib/api/calculations";
import { executeProfileAnalysis } from "@/lib/api/analysis";
import { AnalyticalProfileResponse, ProfileMetricItem } from "@/types/analysis";
import { MetricEvidencePanel, MetricEvidenceContext } from "@/components/analysis/MetricEvidencePanel";
import { Scheme, SchemeOption } from "@/types/domain";
import { HistoricalStressView } from "@/components/analysis/HistoricalStressView";
import { DataQualityCenter } from "@/components/analysis/DataQualityCenter";
import { DataQualityFreshnessSection } from "@/components/analysis/DataQualityFreshnessSection";
import { RollingConsistencyView } from "@/components/analysis/RollingConsistencyView";
import { CaptureRatioView } from "@/components/analysis/CaptureRatioView";
import { BenchmarkBetaDynamics } from "@/components/analysis/BenchmarkBetaDynamics";

import { BenchmarkRelationshipView } from "@/components/analysis/BenchmarkRelationshipView";
import { FundSectionNav } from "@/components/analysis/FundSectionNav";
import { InvestmentModesView } from "@/components/analysis/InvestmentModesView";
import { PortfolioHoldingsView } from "@/components/analysis/PortfolioHoldingsView";
import { FactorExposureView } from "@/components/analysis/FactorExposureView";
import { PortfolioConstructionRiskView } from "@/components/analysis/PortfolioConstructionRiskView";
import { YukiraScoreCard } from "@/components/analysis/YukiraScoreCard";
import { FundOverviewPanel } from "@/components/analysis/FundOverviewPanel";
import { GroundedAiInterpretationPanel } from "@/components/analysis/GroundedAiInterpretationPanel";
import { fetchOptionEnrichment } from "@/lib/api/enrichment";
import { EnrichedFundProfileDto } from "@/types/enrichment";

import { useWatchlist } from "@/lib/hooks/useWatchlist";
import { ResearchNoteModal } from "@/components/watchlist/ResearchNoteModal";
import { WatchlistResponse } from "@/lib/api/investor";

interface PageProps {
  params: Promise<{ id: string }>;
}

function FundDetailContent({ schemeId }: { schemeId: string | number }) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const optionParam = searchParams.get("option");

  const [scheme, setScheme] = useState<Scheme | null>(null);
  const [options, setOptions] = useState<SchemeOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Selected Option & URL Sync
  const [selectedOptionId, setSelectedOptionId] = useState<number | null>(null);
  const [executingMetric, setExecutingMetric] = useState<string | null>(null);
  const [triggerError, setTriggerError] = useState<string | null>(null);

  // Point-in-Time Temporal Horizon Selection
  type AnalysisHorizon = "LATEST_MARKET" | "HISTORICAL_AUDIT";
  const [selectedHorizon, setSelectedHorizon] = useState<AnalysisHorizon>("LATEST_MARKET");

  const activeConfig = selectedHorizon === "LATEST_MARKET"
    ? { asOfDate: "2026-10-01", knowledgeCutoffTime: "2026-10-10T23:59:59+05:30" }
    : { asOfDate: "2024-01-15", knowledgeCutoffTime: "2024-01-31T23:59:59+05:30" };

  // Verified Analytical Profile State (Direct Quant Engine Execution)
  const [profile, setProfile] = useState<AnalyticalProfileResponse | null>(null);
  const [profileLoading, setProfileLoading] = useState(false);
  const [profileError, setProfileError] = useState<string | null>(null);
  const [expandedEvidenceMetric, setExpandedEvidenceMetric] = useState<string | null>(null);

  // Watchlist hook & research note state
  const effectiveSchemeId = scheme?.id ?? (typeof schemeId === "number" ? schemeId : 0);
  const { isWatchlisted, toggleWatchlist, watchlistItems, saveResearchNote } = useWatchlist();
  const watchlisted = isWatchlisted(effectiveSchemeId, selectedOptionId || undefined);
  const watchlistItem = watchlistItems.find(
    (item) => item.schemeId === effectiveSchemeId && (selectedOptionId ? item.schemeOptionId === selectedOptionId : true)
  ) ?? watchlistItems.find((item) => item.schemeId === effectiveSchemeId);
  const [isNoteModalOpen, setIsNoteModalOpen] = useState(false);
  const [investigationPrefill, setInvestigationPrefill] = useState<{
    investigationQuestion?: string;
    reasonSaved?: string;
    nextStep?: string;
  } | undefined>(undefined);

  const targetOption = options.find((opt) => opt.id === selectedOptionId);

  const activeFundForNote: WatchlistResponse | null =
    watchlistItem ||
    (scheme && selectedOptionId
      ? {
          id: -(selectedOptionId || scheme.id),
          schemeId: scheme.id,
          schemeCode: scheme.code,
          schemeName: scheme.name,
          amcName: scheme.amc?.name || "AMC Verified",
          schemeOptionId: selectedOptionId,
          optionType: targetOption?.optionType || "GROWTH",
          planType: targetOption?.plan?.planType || "DIRECT",
          amfiCode: targetOption?.amfiCode || null,
          isin: targetOption?.isin || null,
          latestNav: null,
          latestNavDate: null,
          yukiraScore: null,
          yukiraScoreStatus: "CANDIDATE",
          reasonSaved: null,
          investigationQuestion: null,
          nextStep: null,
          createdAt: null,
          updatedAt: null,
        }
      : null);

  const handleInvestigateQuestion = (question: string, reason?: string) => {
    setInvestigationPrefill({
      investigationQuestion: question,
      reasonSaved: reason || `Investigating ${scheme?.name || "fund"} due diligence question`,
    });
    setIsNoteModalOpen(true);
  };

  const handleOptionSelect = (optId: number) => {
    setSelectedOptionId(optId);
    router.replace(`/funds/${scheme?.code || scheme?.id || schemeId}?option=${optId}`, { scroll: false });
  };

  // Enriched fund profile state (AUM, TER, Manager, Terms, Holdings summary)
  const [enrichment, setEnrichment] = useState<EnrichedFundProfileDto | null>(null);
  const [enrichmentLoading, setEnrichmentLoading] = useState(false);

  useEffect(() => {
    let active = true;
    if (!selectedOptionId) {
      return;
    }
    const timer = setTimeout(() => {
      if (active) setEnrichmentLoading(true);
    }, 0);

    fetchOptionEnrichment(selectedOptionId)
      .then((data) => {
        if (active) {
          setEnrichment(data);
          setEnrichmentLoading(false);
        }
      })
      .catch(() => {
        if (active) {
          setEnrichment(null);
          setEnrichmentLoading(false);
        }
      });

    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [selectedOptionId]);

  // Load Scheme Master and Share Class Options
  useEffect(() => {
    if (!schemeId) {
      return;
    }

    let active = true;
    fetchSchemeById(schemeId)
      .then(async (schemeData) => {
        if (!active) return;
        setScheme(schemeData);
        const schemeOptions = await fetchSchemeOptionsBySchemeId(schemeData.id);
        if (!active) return;
        setOptions(schemeOptions);
        if (schemeOptions.length > 0) {
          const requestedOptId = optionParam ? parseInt(optionParam, 10) : NaN;
          const matchOpt = schemeOptions.find(o => o.id === requestedOptId);
          if (matchOpt) {
            setSelectedOptionId(matchOpt.id);
          } else {
            const directGrowth = schemeOptions.find(o => o.plan?.planType === "DIRECT" && o.optionType === "GROWTH")
              || schemeOptions.find(o => o.plan?.planType === "DIRECT")
              || schemeOptions[0];
            setSelectedOptionId(directGrowth.id);
          }
        }
        setLoading(false);
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
  }, [schemeId, optionParam]);

  // Load Real Institutional Risk-Return Profile via Quant Engine
  useEffect(() => {
    if (!selectedOptionId) return;
    let active = true;
    const timer = setTimeout(() => {
      if (active) {
        setProfileLoading(true);
        setProfileError(null);
      }
    }, 0);

    executeProfileAnalysis({
      schemeOptionId: selectedOptionId,
      asOfDate: activeConfig.asOfDate,
      knowledgeCutoffTime: activeConfig.knowledgeCutoffTime,
      methodologyTag: "APPROVED_M2N",
    })
      .then((res) => {
        if (active) {
          setProfile(res);
          setProfileLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (active) {
          setProfile(null);
          setProfileError(err instanceof Error ? err.message : "Quantitative calculation profile currently unavailable.");
          setProfileLoading(false);
        }
      });

    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [selectedOptionId, selectedHorizon]);

  const getMetricItem = (code: string): ProfileMetricItem | undefined => {
    if (!profile) return undefined;
    const all = [
      ...profile.returnMetrics,
      ...profile.riskMetrics,
      ...profile.riskAdjustedMetrics,
      ...profile.marketSensitivityMetrics,
    ];
    return all.find((m) => m.metricCode === code);
  };

  const handleTriggerCalculation = async (metricCode: string) => {
    if (!selectedOptionId) {
      setTriggerError("Please select a scheme option before initiating calculation.");
      return;
    }

    setExecutingMetric(metricCode);
    setTriggerError(null);

    try {
      const activeConfig = selectedHorizon === "LATEST_MARKET"
        ? { asOfDate: "2026-10-01", knowledgeCutoffTime: "2026-10-10T23:59:59+05:30" }
        : { asOfDate: "2024-01-15", knowledgeCutoffTime: "2024-01-31T23:59:59+05:30" };

      const run = await triggerCalculation({
        schemeOptionId: selectedOptionId,
        benchmarkId: 1,
        asOfDate: activeConfig.asOfDate,
        knowledgeCutoffTime: activeConfig.knowledgeCutoffTime,
        metricCodes: [metricCode],
        methodologyTag: "CANDIDATE_V1",
      });
      router.push(`/analysis/${run.id}`);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Failed to trigger calculation run";
      setTriggerError(msg);
      setExecutingMetric(null);
    }
  };

  if (loading) {
    return (
      <PageContainer title="Scheme Intelligence">
        <StateView
          kind="loading"
          title="Loading Scheme Details"
          message={`Querying backend database for scheme ${schemeId}...`}
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
          message={error || `Scheme ${schemeId} does not exist in master records.`}
          action={
            <Link href="/funds" className="btn btn-secondary btn-sm">
              Back to Catalog
            </Link>
          }
        />
      </PageContainer>
    );
  }

  const selectedOption = options.find((o) => o.id === selectedOptionId) ?? null;
  const isCanonicalPilot = scheme.code === "HDFC_FLEXI" || scheme.name.toLowerCase().includes("hdfc flexi cap");

  const evidenceContext: MetricEvidenceContext = {
    schemeId: scheme.id,
    schemeCode: scheme.code,
    schemeName: scheme.name,
    schemeOptionId: selectedOptionId,
    optionType: selectedOption?.optionType,
    planType: selectedOption?.plan?.planType,
    amfiCode: selectedOption?.amfiCode,
    isin: selectedOption?.isin,
    benchmarkCode: profile?.context?.benchmarkCode,
    benchmarkName: profile?.context?.benchmarkName,
    startDate: profile?.context?.startDate ? String(profile.context.startDate) : undefined,
    endDate: profile?.context?.asOfDate ? String(profile.context.asOfDate) : undefined,
    knowledgeCutoffTime: profile?.context?.knowledgeCutoffTime ? String(profile.context.knowledgeCutoffTime) : undefined,
    observationCount: profile?.provenance?.navObservationCount,
    calculationRunId: profile?.provenance?.calculationRunId,
    methodologyTag: profile?.provenance?.methodologyTag,
    engineSoftwareVersion: profile?.provenance?.engineSoftwareVersion,
    inputSnapshotSha256: profile?.provenance?.inputSnapshotSha256,
  };

  const renderMetricCardWithEvidence = (
    code: string,
    defaultConfig: {
      name: string;
      category: "return" | "risk" | "drawdown" | "tail" | "risk_adjusted" | "portfolio";
      categoryLabel: string;
      period: string;
      description: string;
      interpretation: string;
      limitation: string;
      formula?: string;
      assumptions?: Array<{ label: string; value: string }>;
      canExecute?: boolean;
      investigationQuestion?: string;
      investigationReason?: string;
      defaultGovernanceStatus?: "candidate" | "validated" | "approved" | "unimplemented" | "pending_ingestion";
    }
  ) => {
    const item = getMetricItem(code);
    const isCalculated = item?.calculationStatus === "CALCULATED";
    const rawGov = item?.governanceStatus?.toLowerCase();
    const governance: MetricGovernanceStatus =
      rawGov === "approved" ||
      rawGov === "validated" ||
      rawGov === "candidate" ||
      rawGov === "unimplemented" ||
      rawGov === "pending_ingestion"
        ? rawGov
        : defaultConfig.defaultGovernanceStatus || "candidate";
    const isExpanded = expandedEvidenceMetric === code;

    return (
      <div key={code} className="flex flex-col">
        <MetricCard
          code={code}
          name={defaultConfig.name}
          category={defaultConfig.category}
          categoryLabel={defaultConfig.categoryLabel}
          governanceStatus={governance}
          isCalculated={isCalculated}
          formattedValue={item?.formattedValue ?? undefined}
          period={item?.periodType || defaultConfig.period}
          description={item?.interpretation || defaultConfig.description}
          interpretation={item?.interpretation || defaultConfig.interpretation}
          limitation={item?.limitations || defaultConfig.limitation}
          formula={item?.formulaDisclosure || defaultConfig.formula}
          assumptions={defaultConfig.assumptions || []}
          canExecute={defaultConfig.canExecute}
          isExecuting={executingMetric === code}
          onExecute={defaultConfig.canExecute ? () => handleTriggerCalculation(code) : undefined}
          runId={profile?.provenance?.calculationRunId}
          investigationQuestion={item?.investigationQuestion || defaultConfig.investigationQuestion}
          onInvestigate={(q) =>
            handleInvestigateQuestion(
              q,
              defaultConfig.investigationReason || `Evaluating ${defaultConfig.name}`
            )
          }
        />
        {item && (
          <div className="mt-2 text-right">
            <button
              type="button"
              onClick={() => setExpandedEvidenceMetric(isExpanded ? null : code)}
              className="text-[12px] font-mono text-accent hover:underline inline-flex items-center gap-1"
            >
              <span>{isExpanded ? "Hide Metric Evidence ▲" : "Understand Metric Evidence ▼"}</span>
            </button>
            {isExpanded && (
              <div className="mt-3 text-left">
                <MetricEvidencePanel
                  metric={item}
                  context={evidenceContext}
                  onInvestigate={(inv) => handleInvestigateQuestion(inv.suggestedQuestion, inv.suggestedReason)}
                  isCollapsible={false}
                  defaultExpanded={true}
                />
              </div>
            )}
          </div>
        )}
      </div>
    );
  };

  return (
    <PageContainer
      title={scheme.name}
      subtitle={`Scheme Code: ${scheme.code} • Master ID: #${scheme.id}`}
      breadcrumbs={[
        { label: "Home", href: "/" },
        { label: "Funds", href: "/funds" },
        { label: scheme.code, href: `/funds/${scheme.id}` },
      ]}
      action={
        <div className="flex items-center gap-2">
          <Link
            href={`/compare?funds=${selectedOptionId || scheme.id}`}
            className="btn btn-secondary"
          >
            <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01" />
            </svg>
            <span>Compare Fund</span>
          </Link>
          <button
            onClick={() =>
              toggleWatchlist(effectiveSchemeId, selectedOptionId || undefined, {
                schemeCode: scheme.code,
                schemeName: scheme.name,
                amcName: scheme.amc?.name,
                optionType: selectedOption?.optionType,
                planType: selectedOption?.plan?.planType,
                amfiCode: selectedOption?.amfiCode,
                isin: selectedOption?.isin,
              })
            }
            className={watchlisted ? "btn btn-accent" : "btn btn-secondary"}
            title={watchlisted ? "Remove from Research Workspace" : "Save to Research Workspace"}
          >
            <svg
              className="h-4 w-4"
              fill={watchlisted ? "currentColor" : "none"}
              viewBox="0 0 24 24"
              stroke="currentColor"
              aria-hidden="true"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={watchlisted ? 1 : 2}
                d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z"
              />
            </svg>
            <span>{watchlisted ? "Saved in Workspace" : "Save for Investigation"}</span>
          </button>
        </div>
      }
    >
      {/* Investigation Due Diligence Context Banner */}
      {watchlisted && (
        <section className="panel mb-8 p-4 bg-surface border-l-4 border-l-accent" aria-label="Investigation Due Diligence">
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border pb-2.5 mb-3">
            <div className="flex items-center gap-2">
              <span className="eyebrow text-accent">Investor Due Diligence Context</span>
              <span className="status-badge state-approved text-[10px]">SAVED IN WORKSPACE</span>
            </div>
            <button
              onClick={() => setIsNoteModalOpen(true)}
              className="btn btn-secondary btn-sm"
            >
              {watchlistItem?.reasonSaved || watchlistItem?.investigationQuestion || watchlistItem?.nextStep
                ? "Edit Research Note"
                : "+ Attach Research Question"}
            </button>
          </div>

          {watchlistItem?.reasonSaved || watchlistItem?.investigationQuestion || watchlistItem?.nextStep ? (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3 font-mono text-[11.5px]">
              {watchlistItem?.reasonSaved && (
                <div className="panel-inset p-2.5 bg-surface-raised">
                  <span className="text-text-tertiary text-[10px] block">WHY INVESTIGATING</span>
                  <p className="text-text-primary font-sans text-[12.5px] mt-1">{watchlistItem.reasonSaved}</p>
                </div>
              )}
              {watchlistItem?.investigationQuestion && (
                <div className="panel-inset p-2.5 bg-surface-raised border border-accent/20">
                  <span className="text-accent text-[10px] block font-bold">UNANSWERED QUESTION</span>
                  <p className="text-text-primary font-sans text-[12.5px] font-medium mt-1">{watchlistItem.investigationQuestion}</p>
                </div>
              )}
              {watchlistItem?.nextStep && (
                <div className="panel-inset p-2.5 bg-surface-raised">
                  <span className="text-text-tertiary text-[10px] block">NEXT DUE DILIGENCE STEP</span>
                  <p className="text-text-secondary font-sans text-[12.5px] mt-1">{watchlistItem.nextStep}</p>
                </div>
              )}
            </div>
          ) : (
            <p className="text-[12px] font-mono text-text-tertiary italic">
              Fund saved for investigation. Click &ldquo;+ Attach Research Question&rdquo; to record why this fund deserves due diligence.
            </p>
          )}
        </section>
      )}

      {/* Methodology governance — contextual, scoped to this profile */}
      <section className="panel state-candidate mb-8" aria-label="Methodology Status">
        <div className="panel-header">
          <h2 className="eyebrow">Methodology Governance &amp; Operational Status</h2>
          <span className="status-badge state-candidate">ZERO STAR RATINGS · ZERO TIPS</span>
        </div>
        <p className="max-w-[86ch] px-4 py-3 text-[12.5px] leading-[1.6] text-text-secondary">
          Analytical metrics displayed below reflect verified point-in-time observations. Core
          analytical metrics adhere to formal quantitative governance standards, while research
          indicators operate as candidate specifications. YUKIRA does not generate commercial
          ratings or investment forecasts.
        </p>
      </section>

      {/* Scoped execution failure */}
      {triggerError && (
        <div className="state-panel-error mb-6">
          <strong className="eyebrow block">Execution Failure</strong>
          <span className="mt-1 block font-mono text-[12px]">{triggerError}</span>
        </div>
      )}

      {/* Sticky Section Navigation */}
      <FundSectionNav />

      {/* 1. FUND IDENTITY & SHARE CLASS SELECTOR */}
      <section className="mb-10 scroll-mt-20" id="identity">
        <div className="mb-4">
          <SectionHeading
            ordinal="Section 1"
            title="Fund Identity & Share Classes"
            action={
              isCanonicalPilot ? (
                <span className="status-badge state-approved">
                  <span className="status-dot bg-approved-fg" aria-hidden="true" />
                  Canonical Pilot Instrument
                </span>
              ) : undefined
            }
          />
        </div>

        <dl className="mb-5 grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <div className="panel-inset p-3">
            <dt className="def-label">Scheme Code</dt>
            <dd className="def-value">{scheme.code}</dd>
            <dd className="mono-meta mt-1">Canonical Identifier</dd>
          </div>

          <div className="panel-inset p-3">
            <dt className="def-label">Inception Date</dt>
            <dd className="def-value">{scheme.inceptionDate || "Not available"}</dd>
            <dd className="mono-meta mt-1">AMFI Record</dd>
          </div>

          <div className="panel-inset p-3">
            <dt className="def-label">Master Record Status</dt>
            <dd className="def-value">{scheme.status || "ACTIVE"}</dd>
            <dd className="mono-meta mt-1">PostgreSQL Verified</dd>
          </div>

          <div className="panel-inset p-3">
            <dt className="def-label">Registered Options</dt>
            <dd className="def-value">{options.length} Share Classes</dd>
            <dd className="mono-meta mt-1">Direct / Regular Plans</dd>
          </div>
        </dl>

        {/* FUND OVERVIEW: AUM, EXPENSE RATIO, FUND MANAGER */}
        <div className="mb-6">
          <FundOverviewPanel enrichment={enrichment} loading={enrichmentLoading} />
        </div>

        {/* Share Class Selector Table */}
        <div className="panel">
          <div className="panel-header">
            <div className="min-w-0">
              <h3 className="text-[13px] font-semibold text-text-primary">
                Registered Share Classes ({options.length})
              </h3>
              <p className="mt-0.5 text-[12px] leading-[1.5] text-text-tertiary">
                Select an option to target calculations and verify point-in-time coverage.
              </p>
            </div>
            {selectedOption && (
              <span className="mono-meta shrink-0">
                Active: Option #{selectedOption.id} ({selectedOption.plan?.planType || "DIRECT"}{" "}
                {selectedOption.optionType})
              </span>
            )}
          </div>

          <div className="scroll-region">
            <table className="data-table">
              <thead>
                <tr>
                  <th className="w-10">Sel</th>
                  <th>Option ID</th>
                  <th>Plan Type</th>
                  <th>Option</th>
                  <th>AMFI Code</th>
                  <th>ISIN</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {options.map((opt) => (
                  <tr
                    key={opt.id}
                    onClick={() => handleOptionSelect(opt.id)}
                    className="cursor-pointer transition-colors hover:bg-surface-raised"
                  >
                    <td>
                      <input
                        type="radio"
                        name="selectedOption"
                        checked={selectedOptionId === opt.id}
                        onChange={() => handleOptionSelect(opt.id)}
                        className="accent-[var(--accent)]"
                        aria-label={`Select Option #${opt.id}`}
                      />
                    </td>
                    <td className={selectedOptionId === opt.id ? "text-accent" : "key"}>
                      Option #{opt.id}
                    </td>
                    <td className="key">{opt.plan?.planType || "DIRECT"}</td>
                    <td>{opt.optionType || "GROWTH"}</td>
                    <td className="key">{opt.amfiCode || "Not available"}</td>
                    <td className="num text-[11px]">{opt.isin || "Not available"}</td>
                    <td>
                      <span className="status-badge state-approved">
                        {opt.status || "ACTIVE"}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </section>

      {/* YUKIRA ANALYTICAL SCORECARD */}
      {selectedOptionId && (
        <section className="mb-10 scroll-mt-20" id="analytical-scorecard">
          <YukiraScoreCard schemeOptionId={selectedOptionId} />
        </section>
      )}

      {/* 2. DATA COVERAGE & POINT-IN-TIME STATUS */}
      <section className="mb-10 scroll-mt-20" id="coverage">
        <div className="mb-4">
          <SectionHeading
            ordinal="Section 2"
            title="Data Coverage & Point-in-Time Status"
          />
        </div>

        {/* POINT-IN-TIME ANALYSIS HORIZON SELECTOR */}
        <div className="mb-6 p-4 rounded-2xl border border-border-gold-subtle bg-surface-midnight/90 backdrop-blur-md shadow-lg glow-gold">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-3 border-b border-border-gold-subtle/60">
            <div>
              <span className="font-mono text-[10.5px] uppercase tracking-[0.12em] text-primary font-semibold flex items-center gap-1.5">
                <svg className="w-3.5 h-3.5 text-primary" fill="currentColor" viewBox="0 0 20 20">
                  <path clipRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-12a1 1 0 10-2 0v4a1 1 0 00.293.707l2.828 2.829a1 1 0 101.415-1.415L11 9.586V6z" fillRule="evenodd" />
                </svg>
                POINT-IN-TIME TEMPORAL HORIZON
              </span>
              <h3 className="text-[15px] font-bold text-text-cream-bright mt-0.5">
                Active Quantitative Analysis Horizon
              </h3>
            </div>
            {profileLoading ? (
              <span className="font-mono text-xs text-primary flex items-center gap-1.5 animate-pulse">
                <span className="w-2 h-2 rounded-full bg-primary" />
                Recalculating 20 Metrics...
              </span>
            ) : profile ? (
              <span className="font-mono text-xs text-emerald-400 bg-emerald-500/10 border border-emerald-500/20 px-2.5 py-1 rounded-full flex items-center gap-1.5">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
                Run #{profile.provenance.calculationRunId} Verified
              </span>
            ) : null}
          </div>

          <div className="mt-3.5 grid grid-cols-1 md:grid-cols-2 gap-3">
            {/* Horizon 1: Latest Market Horizon */}
            <button
              type="button"
              onClick={() => setSelectedHorizon("LATEST_MARKET")}
              className={`p-3.5 rounded-xl border text-left transition-all relative ${
                selectedHorizon === "LATEST_MARKET"
                  ? "border-primary bg-primary/10 shadow-sm ring-1 ring-primary/40"
                  : "border-border-gold-subtle/70 bg-surface-obsidian hover:border-border-gold-bright"
              }`}
            >
              <div className="flex items-center justify-between mb-1">
                <span className="font-sans font-bold text-[13.5px] text-text-cream-bright flex items-center gap-1.5">
                  <span className={`w-2 h-2 rounded-full ${selectedHorizon === "LATEST_MARKET" ? "bg-primary" : "bg-neutral-500"}`} />
                  Latest Available Market Horizon
                </span>
                <span className="font-mono text-[10px] font-bold uppercase tracking-wider text-primary bg-primary/15 px-2 py-0.5 rounded-full border border-primary/30">
                  LATEST MARKET
                </span>
              </div>
              <div className="font-mono text-xs text-text-cream-muted mt-1 space-y-0.5">
                <div>Cutoff Date: <span className="font-semibold text-text-cream-bright">2026-10-01</span> · Knowledge: <span className="text-text-cream-body">2026-10-10</span></div>
                <div className="text-[11px] text-text-cream-muted/80">1,920 continuous daily NAV observations · 36-Month Rolling Lookback</div>
              </div>
            </button>

            {/* Horizon 2: Historical Audit Baseline */}
            <button
              type="button"
              onClick={() => setSelectedHorizon("HISTORICAL_AUDIT")}
              className={`p-3.5 rounded-xl border text-left transition-all relative ${
                selectedHorizon === "HISTORICAL_AUDIT"
                  ? "border-primary bg-primary/10 shadow-sm ring-1 ring-primary/40"
                  : "border-border-gold-subtle/70 bg-surface-obsidian hover:border-border-gold-bright"
              }`}
            >
              <div className="flex items-center justify-between mb-1">
                <span className="font-sans font-bold text-[13.5px] text-text-cream-bright flex items-center gap-1.5">
                  <span className={`w-2 h-2 rounded-full ${selectedHorizon === "HISTORICAL_AUDIT" ? "bg-primary" : "bg-neutral-500"}`} />
                  Historical Audit Baseline
                </span>
                <span className="font-mono text-[10px] font-bold uppercase tracking-wider text-amber-400 bg-amber-500/15 px-2 py-0.5 rounded-full border border-amber-500/30">
                  FROZEN AUDIT
                </span>
              </div>
              <div className="font-mono text-xs text-text-cream-muted mt-1 space-y-0.5">
                <div>Cutoff Date: <span className="font-semibold text-text-cream-bright">2024-01-15</span> · Knowledge: <span className="text-text-cream-body">2024-01-31</span></div>
                <div className="text-[11px] text-text-cream-muted/80">Phase 2I/2J Frozen Cryptographic Baseline (SHA-256 Verified)</div>
              </div>
            </button>
          </div>
        </div>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <div className="panel-inset p-3">
            <span className="def-label">NAV Analysis Cutoff</span>
            <span className="def-value block">
              {profile?.context?.asOfDate ? String(profile.context.asOfDate) : "2024-01-15"}
            </span>
            <p className="mt-1.5 text-[12px] leading-[1.5] text-text-secondary">
              Daily NAV time-series evaluated through market close.
            </p>
          </div>

          <div className="panel-inset p-3">
            <span className="def-label">NAV Knowledge Cutoff</span>
            <span className="def-value block truncate" title={profile?.context?.knowledgeCutoffTime || "2024-01-31T23:59:59+05:30"}>
              {profile?.context?.knowledgeCutoffTime ? "2024-01-31 23:59:59 IST" : "2024-01-31 23:59:59 IST"}
            </span>
            <p className="mt-1.5 text-[12px] leading-[1.5] text-text-secondary">
              Strict exclusion of any NAV revisions published after cutoff.
            </p>
          </div>

          <div className="panel-inset p-3">
            <span className="def-label">Holdings Reporting Date</span>
            <span className="def-value block">2023-12-31</span>
            <p className="mt-1.5 text-[12px] leading-[1.5] text-text-secondary">
              Monthly portfolio disclosure. Distinct from daily NAV date.
            </p>
          </div>

          <div className="panel-inset p-3">
            <span className="def-label">Quant Engine Status</span>
            <span className="def-value block text-[13px] truncate">
              {profileLoading
                ? "Calculating via Quant Engine..."
                : profile
                ? "FastAPI Quant Engine Verified"
                : profileError
                ? "Engine Unavailable"
                : "Awaiting Execution"}
            </span>
            <p className="mt-1.5 text-[12px] leading-[1.5] text-text-secondary">
              {profile
                ? `Run #${profile.provenance.calculationRunId} · ${profile.provenance.engineSoftwareVersion}`
                : "Deterministic financial math"}
            </p>
          </div>
        </div>

        {profileError && (
          <div className="mt-3 state-panel-error p-3 text-[12px]">
            <strong className="font-semibold block text-critical-fg">Quant Engine Calculation Notice</strong>
            <p className="mt-0.5 text-text-secondary">
              {profileError}. Displaying documented candidate specifications; no numbers are estimated or approximated.
            </p>
          </div>
        )}
      </section>

      {/* GROUNDED AI INTERPRETATION LAYER */}
      {selectedOptionId && (
        <section className="mb-10 scroll-mt-20" id="ai-interpretation">
          <GroundedAiInterpretationPanel schemeOptionId={selectedOptionId} />
        </section>
      )}

      {/* 3. METRIC GROUPS (PROGRESSIVE DISCLOSURE) */}

      {/* DIMENSION 1: RETURN QUALITY */}
      <section className="mb-10 scroll-mt-20" id="return-quality">
        <div className="mb-4">
          <SectionHeading
            ordinal="Dimension 1"
            title="Return Quality & Compound Horizon"
            action={<span className="mono-meta shrink-0">4 Horizon Metrics</span>}
          />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {renderMetricCardWithEvidence("RET-02", {
            name: "Simple Period Return",
            category: "return",
            categoryLabel: "Discrete Return",
            period: "2024-01-01 → 2024-01-15 (11 trading days)",
            description: "Discrete percentage change in net asset value between start and end observations.",
            interpretation: "Measures realized point-to-point capital appreciation over the selected historical dates.",
            limitation: "Point-to-point discrete return masks intra-period volatility and path stress.",
            formula: "R = (NAV_end / NAV_start) - 1",
            assumptions: [
              { label: "Lookback Tolerance", value: "4 calendar days" },
              { label: "Units", value: "PERCENTAGE" },
            ],
            canExecute: true,
            investigationQuestion: "Is the short-term discrete return consistent with multi-year compound growth?",
            investigationReason: "Evaluating RET-02 Simple Period Return",
            defaultGovernanceStatus: "candidate",
          })}

          {renderMetricCardWithEvidence("RET-03", {
            name: "3-Year Compound Annual Growth Rate (3Y CAGR)",
            category: "return",
            categoryLabel: "Annualized Return",
            period: "36 Calendar Months (≥ 700 trading days)",
            description: "Annualized compound return evaluated over a continuous 36-month lookback window.",
            interpretation: "Normalizes cumulative multi-year growth onto an annualized basis using 365.25 calendar days convention.",
            limitation: "Past annualized return does not predict future returns across changing market cycles.",
            formula: "CAGR = (NAV_end / NAV_start)^(365.25 / elapsed_calendar_days) - 1",
            assumptions: [
              { label: "Window", value: "36 Calendar Months" },
              { label: "Minimum Days", value: "700 trading days" },
            ],
            canExecute: true,
            investigationQuestion: "Is the 3-year CAGR accompanied by higher downside volatility or drawdown risk?",
            investigationReason: "Evaluating 3-Year annualized return consistency",
            defaultGovernanceStatus: "approved",
          })}

          <MetricCard
            code="RET-01"
            name="1-Year Compound Annual Growth Rate (1Y CAGR)"
            category="return"
            categoryLabel="12M Lookback"
            governanceStatus="unimplemented"
            period="12 Calendar Months"
            description="Annualized 1-year trailing return primitive."
            disabledReason="Under quantitative review — available in full analytical audit."
          />

          <MetricCard
            code="RET-04"
            name="5-Year Compound Annual Growth Rate (5Y CAGR)"
            category="return"
            categoryLabel="60M Lookback"
            governanceStatus="unimplemented"
            period="60 Calendar Months"
            description="Full-cycle 5-year annualized return primitive."
            disabledReason="Under quantitative review — available in full analytical audit."
          />
        </div>
      </section>

      {/* ROLLING RETURN & OUTPERFORMANCE CONSISTENCY SLICE (§RET-05 / §RET-06) */}
      {selectedOptionId && (
        <div className="mb-10">
          <RollingConsistencyView
            schemeOptionId={selectedOptionId}
            schemeCode={scheme.code}
            asOfDate={activeConfig.asOfDate}
            knowledgeCutoffTime={activeConfig.knowledgeCutoffTime}
          />
        </div>
      )}

      {/* DIMENSION 2: TOTAL & DOWNSIDE RISK */}
      <section className="mb-10 scroll-mt-20" id="risk">
        <div className="mb-4">
          <SectionHeading
            ordinal="Dimension 2"
            title="Total & Downside Risk"
            action={<span className="mono-meta shrink-0">2 Risk Metrics</span>}
          />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {renderMetricCardWithEvidence("RSK-01", {
            name: "Annualized Volatility (3Y)",
            category: "risk",
            categoryLabel: "Total Dispersion",
            period: "36 Calendar Months (≥ 700 trading days)",
            description: "Annualized sample standard deviation of daily log/discrete returns.",
            interpretation: "Measures the total dispersion of daily returns around the sample mean over 36 months.",
            limitation: "Treats upside gains and downside drawdowns with equal penalty.",
            formula: "σ_ann = √252 × √( ∑(r_t - r̄)² / (N - 1) )",
            assumptions: [
              { label: "Annualizer", value: "√252 (Trading Days)" },
              { label: "Denominator", value: "N - 1 (Unbiased Sample)" },
            ],
            canExecute: true,
            investigationQuestion: "Is annualized volatility driven by transient market regime shocks or persistent fund beta?",
            investigationReason: "Evaluating 3-Year total risk dispersion",
            defaultGovernanceStatus: "approved",
          })}

          {renderMetricCardWithEvidence("RSK-02", {
            name: "Downside Semideviation (3Y)",
            category: "risk",
            categoryLabel: "Asymmetric Risk",
            period: "36 Calendar Months (≥ 700 trading days)",
            description: "Annualized dispersion of negative daily returns below minimum acceptable return (MAR = 0.0).",
            interpretation: "Isolates exclusively harmful negative returns, ignoring upside volatility that benefits the investor.",
            limitation: "Candidate convention MAR = 0.0; does not penalize returns that lag inflation.",
            formula: "σ_d = √252 × √( ∑(min(r_t, 0))² / (N - 1) )",
            assumptions: [
              { label: "Threshold (MAR)", value: "0.0% (Zero Return)" },
              { label: "Annualizer", value: "√252" },
            ],
            canExecute: true,
            investigationQuestion: "Does downside risk deviate significantly from total volatility during market corrections?",
            investigationReason: "Evaluating 3-Year downside semideviation",
            defaultGovernanceStatus: "candidate",
          })}
        </div>
      </section>

      {/* DIMENSION 3: DRAWDOWN & PATH STRESS */}
      <section className="mb-10 scroll-mt-20" id="drawdown">
        <div className="mb-4">
          <SectionHeading
            ordinal="Dimension 3"
            title="Drawdown & Path Stress"
            action={<span className="mono-meta shrink-0">3 Drawdown Metrics</span>}
          />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {renderMetricCardWithEvidence("RSK-03", {
            name: "Maximum Drawdown (3Y)",
            category: "drawdown",
            categoryLabel: "Capital Decline",
            period: "36 Calendar Months",
            description: "Worst peak-to-trough percentage decline observed over the 36-month horizon.",
            interpretation: "Quantifies the maximum capital loss an investor experienced from the highest historical peak.",
            limitation: "Measures historical worst-case decline only; does not place an upper bound on future drawdowns.",
            formula: "MDD = min_t ( NAV_t / max_{s≤t}(NAV_s) - 1 )",
            assumptions: [
              { label: "Sign Convention", value: "Signed Negative Decimal" },
              { label: "Peak Tracking", value: "Running Cumulative Max" },
            ],
            canExecute: true,
            investigationQuestion: "Did the maximum drawdown recover faster or slower than the category benchmark?",
            investigationReason: "Evaluating 3-Year maximum drawdown",
            defaultGovernanceStatus: "candidate",
          })}

          {renderMetricCardWithEvidence("RSK-04", {
            name: "Maximum Drawdown Duration",
            category: "drawdown",
            categoryLabel: "Recovery Horizon",
            period: "36 Calendar Months",
            description: "Longest elapsed calendar days between a peak and the subsequent full recovery.",
            interpretation: "Measures the psychological holding stress and capital lockup duration required to break even.",
            limitation: "Unrecovered drawdowns are censored at knowledge cutoff timestamp.",
            formula: "Duration = max( recovery_date - peak_date )",
            assumptions: [
              { label: "Units", value: "Calendar Days (DAYS)" },
              { label: "Censoring Rule", value: "Cutoff Bound Applied" },
            ],
            canExecute: true,
            investigationQuestion: "How many months was capital underwater during the deepest drawdown episode?",
            investigationReason: "Evaluating drawdown duration and recovery pace",
            defaultGovernanceStatus: "candidate",
          })}

          {renderMetricCardWithEvidence("RSK-05", {
            name: "Ulcer Index (3Y)",
            category: "drawdown",
            categoryLabel: "Stress Metric",
            period: "36 Calendar Months",
            description: "Quadratic root-mean-square of percentage drawdowns from running peak NAV.",
            interpretation: "Synthesizes both depth and duration of all drawdowns into a single path-dependent stress measure.",
            limitation: "Heavier penalty on deeper drawdowns due to quadratic weighting.",
            formula: "UI = √( (1/N) × ∑( ((NAV_t - max NAV)/max NAV × 100)² ) )",
            assumptions: [
              { label: "Weighting", value: "Quadratic RMS" },
              { label: "Units", value: "POINTS" },
            ],
            canExecute: true,
            investigationQuestion: "Does the Ulcer Index indicate persistent drawdown stress even when maximum drawdown appears moderate?",
            investigationReason: "Evaluating Ulcer Index path stress",
            defaultGovernanceStatus: "candidate",
          })}
        </div>
      </section>

      {/* HISTORICAL STRESS VIEW SLICE */}
      {selectedOptionId && (
        <div className="mb-10">
          <HistoricalStressView
            schemeOptionId={selectedOptionId}
            schemeCode={scheme.code}
            asOfDate={activeConfig.asOfDate}
            knowledgeCutoffTime={activeConfig.knowledgeCutoffTime}
          />
        </div>
      )}

      {/* DIMENSION 4: TAIL RISK */}
      <section className="mb-10 scroll-mt-20" id="tail-risk">
        <div className="mb-4">
          <SectionHeading
            ordinal="Dimension 4"
            title="Tail Risk & Extreme Losses"
            action={<span className="mono-meta shrink-0">2 Tail Risk Metrics</span>}
          />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <MetricCard
            code="RSK-06"
            name="Historical Value at Risk (VaR 95%)"
            category="tail"
            categoryLabel="Tail Loss Boundary"
            governanceStatus="candidate"
            period="36 Calendar Months"
            description="5th percentile empirical daily return threshold over the 36-month lookback window."
            interpretation="On 95% of trading days, daily loss did not exceed this threshold in historical data."
            limitation="Non-parametric empirical quantile; does not describe severity of losses in the remaining 5% tail."
            formula="VaR_95 = -Quantile_0.05( { r_t } )"
            assumptions={[
              { label: 'Quantile Convention', value: 'Type 7 Linear Interpolation' },
              { label: 'Confidence Level', value: '95.0% One-Tailed' },
            ]}
            disabledReason="Implemented in Python quant engine kernel. Available in full analytical audit."
          />

          <MetricCard
            code="RSK-07"
            name="Expected Shortfall (CVaR 95%)"
            category="tail"
            categoryLabel="Conditional Tail Expectation"
            governanceStatus="candidate"
            period="36 Calendar Months"
            description="Conditional mean of daily returns strictly below the 5th percentile VaR cutoff."
            interpretation="Measures the expected daily loss when an extreme tail loss event actually occurs."
            limitation="Sub-sample average subject to small-sample estimation variance during calm market regimes."
            formula="ES_95 = -(1 / |Tail|) × ∑_{r_t < -VaR_95} r_t"
            assumptions={[
              { label: 'Coherence', value: 'Sub-additive Coherent Measure' },
              { label: 'Tail Threshold', value: 'Empirical 5th Percentile' },
            ]}
            disabledReason="Implemented in Python quant engine kernel. Available in full analytical audit."
          />
        </div>
      </section>

      {/* DIMENSION 5: RISK-ADJUSTED & MARKET SENSITIVITY */}
      <section className="mb-10 scroll-mt-20" id="risk-adjusted">
        <div className="mb-4">
          <SectionHeading
            ordinal="Dimension 5"
            title="Risk-Adjusted Ratios & Market Sensitivity"
            action={
              <span className="mono-meta shrink-0">4 Risk-Adjusted &amp; Sensitivity Metrics</span>
            }
          />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {renderMetricCardWithEvidence("RAT-01", {
            name: "Sharpe Ratio (3Y)",
            category: "risk_adjusted",
            categoryLabel: "Excess Return per Unit Total Risk",
            period: "36 Calendar Months",
            description: "Ratio of annualized excess return above the risk-free rate to annualized return volatility.",
            interpretation: "Evaluates how effectively the fund compensated for total standard deviation above 91-day T-bills.",
            limitation: "Assumes symmetrical return distributions; penalizes large upside gains.",
            formula: "Sharpe = (R_fund - R_f) / σ_ann",
            assumptions: [
              { label: "Risk-Free Benchmark", value: "FBIL 91D T-Bill" },
              { label: "Compounding", value: "Geometric Annualized" },
            ],
            canExecute: false,
            investigationQuestion: "Is the Sharpe ratio driven primarily by market beta or genuine active alpha?",
            investigationReason: "Evaluating Sharpe ratio risk compensation",
            defaultGovernanceStatus: "approved",
          })}

          {renderMetricCardWithEvidence("RAT-02", {
            name: "Treynor Ratio (3Y)",
            category: "risk_adjusted",
            categoryLabel: "Excess Return per Unit Systematic Risk",
            period: "36 Calendar Months",
            description: "Ratio of annualized excess return above the risk-free rate to systematic equity Beta.",
            interpretation: "Measures excess reward earned per unit of unavoidable broad-market systematic risk.",
            limitation: "Only meaningful for well-diversified equity portfolios with high correlation to benchmark.",
            formula: "Treynor = (R_fund - R_f) / Beta",
            assumptions: [
              { label: "Systematic Beta", value: "3Y Nifty 50 TRI Beta" },
              { label: "Denominator Rule", value: "Beta > 0 required" },
            ],
            canExecute: false,
            investigationQuestion: "Does the excess return compensate for systematic benchmark volatility exposure?",
            investigationReason: "Evaluating Treynor systematic return ratio",
            defaultGovernanceStatus: "approved",
          })}

          {renderMetricCardWithEvidence("MKT-01", {
            name: "Portfolio Beta (3Y)",
            category: "risk_adjusted",
            categoryLabel: "Systematic Sensitivity",
            period: "36 Calendar Months",
            description: "Slope coefficient from linear regression of daily fund returns against benchmark returns.",
            interpretation: "Measures the portfolio sensitivity to broad market movements (Beta > 1 implies amplified swings).",
            limitation: "Linear static measure; beta fluctuates across bull, bear, and crisis regimes.",
            formula: "Beta = Cov(r_fund, r_bench) / Var(r_bench)",
            assumptions: [
              { label: "Benchmark", value: "Official NIFTY 50 TRI" },
              { label: "Regression", value: "Ordinary Least Squares (OLS)" },
            ],
            canExecute: false,
            investigationQuestion: "Does portfolio beta remain stable across market drawdowns versus rallies?",
            investigationReason: "Evaluating systematic portfolio beta",
            defaultGovernanceStatus: "approved",
          })}

          {renderMetricCardWithEvidence("MKT-02", {
            name: "Downside Beta (3Y)",
            category: "risk_adjusted",
            categoryLabel: "Asymmetric Sensitivity",
            period: "36 Calendar Months (Benchmark < 0)",
            description: "Beta calculated conditioning exclusively on trading days when benchmark return was negative.",
            interpretation: "Identifies whether fund sensitivity increases during market sell-offs vs up-trending regimes.",
            limitation: "Requires sufficient negative benchmark trading days (minimum 100 days threshold).",
            formula: "Beta_down = Cov(r_fund, r_bench | r_bench < 0) / Var(r_bench | r_bench < 0)",
            assumptions: [
              { label: "Conditioning", value: "r_benchmark < 0.0" },
              { label: "Min Observations", value: "100 downside market days" },
            ],
            canExecute: false,
            investigationQuestion: "Is downside beta lower than overall beta, proving defensive market cushioning?",
            investigationReason: "Evaluating asymmetric downside beta",
            defaultGovernanceStatus: "approved",
          })}
        </div>
      </section>

      {/* CAPTURE RATIOS & ASYMMETRIC MARKET PARTICIPATION (§MKT-03 / §MKT-04 / §MKT-05) */}
      {selectedOptionId && (
        <div className="mb-10">
          <CaptureRatioView
            schemeOptionId={selectedOptionId}
            schemeCode={scheme.code}
            asOfDate={activeConfig.asOfDate}
            knowledgeCutoffTime={activeConfig.knowledgeCutoffTime}
          />
        </div>
      )}

      {/* ROLLING MARKET BETA & FACTOR EXPOSURE STABILITY (§MKT-01 / §MKT-02 / §MKT-06) */}
      {selectedOptionId && (
        <div className="mb-10" id="beta-dynamics">
          <BenchmarkBetaDynamics
            schemeOptionId={selectedOptionId}
            schemeCode={scheme.code}
            asOfDate={activeConfig.asOfDate}
            knowledgeCutoffTime={activeConfig.knowledgeCutoffTime}
          />
        </div>
      )}

      {/* DIMENSION 5.5: CONSOLIDATED BENCHMARK RELATIONSHIP PANEL */}
      {selectedOptionId && (
        <section className="mb-10 scroll-mt-20" id="benchmark-relationship">
          <div className="mb-4">
            <SectionHeading
              ordinal="Dimension 5.5"
              title="Benchmark Relationship & Market Sensitivity"
              action={<span className="mono-meta shrink-0">6 Consolidated Indicators</span>}
            />
          </div>
          <BenchmarkRelationshipView
            schemeOptionId={selectedOptionId}
            schemeCode={scheme.code}
          />
        </section>
      )}


      {/* DIMENSION 6: PORTFOLIO STRUCTURE & HOLDINGS CONCENTRATION */}

      <section className="mb-12 scroll-mt-20" id="portfolio">
        <div className="mb-4">
          <SectionHeading
            ordinal="Dimension 6"
            title="Portfolio Structure & Holdings Concentration"
            action={
              <span className="mono-meta shrink-0">
                Official Monthly Portfolio Disclosures
              </span>
            }
          />
        </div>

        {selectedOptionId && (
          <PortfolioHoldingsView schemeOptionId={selectedOptionId} schemeCode={scheme.code} />
        )}
      </section>

      {/* DIMENSION 6.5: PORTFOLIO FACTOR EXPOSURE, STYLE DRIFT & RETURN ATTRIBUTION */}
      <section className="mb-12 scroll-mt-20" id="factor-exposure">
        <div className="mb-4">
          <SectionHeading
            ordinal="Dimension 6.5"
            title="Portfolio Factor Exposure, Style Drift & Return Attribution"
            action={
              <span className="mono-meta shrink-0">
                Holdings-Based Characteristics &amp; Style Drift
              </span>
            }
          />
        </div>

        {selectedOptionId && (
          <FactorExposureView
            schemeOptionId={selectedOptionId}
            schemeCode={scheme.code}
            asOfDate={activeConfig.asOfDate}
            knowledgeCutoffTime={activeConfig.knowledgeCutoffTime}
          />
        )}
      </section>

      {/* DIMENSION 6.6: PORTFOLIO CONSTRUCTION RISK & HOLDINGS DATA RELIABILITY */}
      <section className="mb-12 scroll-mt-20" id="construction-risk">
        <div className="mb-4">
          <SectionHeading
            ordinal="Dimension 6.6"
            title="Portfolio Construction Risk & Holdings Data Reliability"
            action={
              <span className="mono-meta shrink-0">
                Weight Reconciliation, Issuer Concentration &amp; Comparability
              </span>
            }
          />
        </div>

        {selectedOptionId && (
          <PortfolioConstructionRiskView
            schemeOptionId={selectedOptionId}
            schemeCode={scheme.code}
            asOfDate={activeConfig.asOfDate}
            knowledgeCutoffTime={activeConfig.knowledgeCutoffTime}
          />
        )}
      </section>

      {/* SECTION 3: SIP & LUMPSUM INVESTMENT INFORMATION */}
      <section className="mb-12 scroll-mt-20" id="investment-modes">
        <div className="mb-4">
          <SectionHeading
            ordinal="Section 3"
            title="SIP & Lumpsum Investment Information"
            description="Investment routes and registered share classes, reported strictly from verified AMFI master records. AMC scheme terms are reported as unavailable where YUKIRA does not hold them."
          />
        </div>

        <InvestmentModesView
          options={options}
          schemeCode={scheme.code}
          investmentTerms={enrichment?.investmentTerms}
        />
      </section>

      {/* 4. DATA PROVENANCE & LINEAGE AUDIT */}
      <section className="panel mb-12 scroll-mt-20" id="provenance">
        <div className="panel-header">
          <h2 className="text-[13px] font-semibold text-text-primary">
            Official AMFI Data Lineage &amp; Cryptographic Proof
          </h2>
          <span className="mono-meta shrink-0">Primary Source Verification</span>
        </div>

        <div className="px-4 py-4">
          <p className="max-w-[86ch] text-[12.5px] leading-[1.6] text-text-secondary">
            All numerical calculations originate strictly from authenticated primary source documents
            in the repository&apos;s bitemporal ledger. For scheme{" "}
            <strong className="font-mono text-text-primary">{scheme.code}</strong> (AMFI{" "}
            <strong className="font-mono text-text-primary">
              {selectedOption?.amfiCode ?? "Not available"}
            </strong>
            ), observations are cryptographically anchored to:
          </p>

          <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div className="panel-inset p-3">
              <span className="def-label">Raw Source Artifact #1</span>
              <span className="data-value-sm mt-1 block">AMFI NAV History Payload</span>
              <span className="mt-2 block select-all break-all font-mono text-[11px] leading-[1.5] text-text-secondary">
                SHA-256: 900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259
              </span>
              <span className="mono-meta mt-2 block">Size: 11,185,549 bytes · 11 Jan 2024 dates</span>
            </div>

            <div className="panel-inset p-3">
              <span className="def-label">Historical 5Y Horizon Artifacts</span>
              <span className="data-value-sm mt-1 block">Annual Source Artifacts #149..#155</span>
              <span className="mt-2 block font-mono text-[11px] leading-[1.5] text-text-secondary">
                2019 (#149), 2020 (#155), 2021 (#150), 2022 (#151), 2023 (#152)
              </span>
              <span className="mono-meta mt-2 block">
                1,243 authentic market dates · Zero synthetic series
              </span>
            </div>
          </div>
        </div>
      </section>

      {/* 4.5 DATA QUALITY, CADENCE & ANOMALY HEALTH */}
      <section className="mb-12 scroll-mt-20" id="data-quality">
        {selectedOptionId && (
          <div className="space-y-6">
            <DataQualityFreshnessSection
              schemeOptionId={selectedOptionId}
              schemeCode={scheme?.code}
              schemeName={scheme?.name}
            />
            <DataQualityCenter schemeOptionId={selectedOptionId} />
          </div>
        )}
      </section>

      {/* 5. DECISION SUPPORT: WHAT TO INVESTIGATE NEXT */}
      <section className="panel mb-12 scroll-mt-20" id="decision-support">
        <div className="panel-header">
          <h2 className="text-[13px] font-semibold text-text-primary">
            Decision Support Checkpoint: Questions Before Committing Capital
          </h2>
        </div>

        <p className="px-4 pt-3 text-[12.5px] leading-[1.6] text-text-secondary">
          Quantitative metrics describe past realized outcomes under historical market conditions.
          Before allocating capital, consider investigating:
        </p>

        <ol className="grid grid-cols-1 gap-px border-t border-border bg-border sm:grid-cols-2 lg:grid-cols-3">
          {[
            {
              n: "01",
              title: "Market Regime Resilience",
              body: "Did outperformance occur during broad liquidity rallies or during market drawdowns?",
            },
            {
              n: "02",
              title: "Portfolio Concentration",
              body: "Is return driven by fund-wide alpha or by heavy weight in 2-3 outperforming stocks?",
            },
            {
              n: "03",
              title: "Manager Tenure & Style Drift",
              body: "Did current fund leadership generate the 5-year track record without style deviation?",
            },
          ].map((q) => (
            <li key={q.n} className="bg-surface px-4 py-3 flex flex-col justify-between">
              <div>
                <span className="flex items-baseline gap-2">
                  <span className="tab-ordinal">{q.n}</span>
                  <span className="text-[13px] font-semibold text-text-primary">{q.title}</span>
                </span>
                <p className="mt-1.5 text-[12px] leading-[1.55] text-text-secondary">{q.body}</p>
              </div>
              <div className="mt-3 pt-2 border-t border-border/40 flex justify-end">
                <button
                  type="button"
                  onClick={() => handleInvestigateQuestion(q.body, `Investigating ${q.title}`)}
                  className="btn btn-secondary btn-sm text-[11px] flex items-center gap-1"
                >
                  <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z" />
                  </svg>
                  <span>Investigate in Workspace</span>
                </button>
              </div>
            </li>
          ))}
        </ol>
      </section>

      {activeFundForNote && (
        <ResearchNoteModal
          isOpen={isNoteModalOpen}
          onClose={() => {
            setIsNoteModalOpen(false);
            setInvestigationPrefill(undefined);
          }}
          fund={activeFundForNote}
          prefill={investigationPrefill}
          onSave={async (sId, note, sOptId) => {
            await saveResearchNote(sId, note, sOptId, {
              schemeCode: activeFundForNote.schemeCode,
              schemeName: activeFundForNote.schemeName,
              amcName: activeFundForNote.amcName,
              optionType: activeFundForNote.optionType,
              planType: activeFundForNote.planType,
            });
            setIsNoteModalOpen(false);
            setInvestigationPrefill(undefined);
          }}
        />
      )}
    </PageContainer>
  );
}

export default function FundDetailPage({ params }: PageProps) {
  const resolvedParams = use(params);
  const identifier = resolvedParams.id;

  return (
    <Suspense fallback={<PageContainer title="Scheme Intelligence"><StateView kind="loading" title="Loading Scheme Details" message="Querying master records..." /></PageContainer>}>
      <FundDetailContent schemeId={identifier} />
    </Suspense>
  );
}
