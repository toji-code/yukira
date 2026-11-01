"use client";

import { use, useEffect, useState, useMemo, Suspense } from "react";
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
import { HistoricalObservationChart } from "@/components/analysis/HistoricalObservationChart";
import { PerformancePeriodSelector } from "@/components/analysis/PerformancePeriodSelector";
import { DataQualityCenter } from "@/components/analysis/DataQualityCenter";
import { DataQualityFreshnessSection } from "@/components/analysis/DataQualityFreshnessSection";
import { RollingConsistencyView } from "@/components/analysis/RollingConsistencyView";
import { CaptureRatioView } from "@/components/analysis/CaptureRatioView";
import { BenchmarkBetaDynamics } from "@/components/analysis/BenchmarkBetaDynamics";
import { EvidenceQualitySummary } from "@/components/epistemic/EvidenceQualitySummary";

import { BenchmarkRelationshipView } from "@/components/analysis/BenchmarkRelationshipView";
import { FundSectionNav } from "@/components/analysis/FundSectionNav";
import { InvestmentModesView } from "@/components/analysis/InvestmentModesView";
import { PortfolioHoldingsView } from "@/components/analysis/PortfolioHoldingsView";
import { FactorExposureView } from "@/components/analysis/FactorExposureView";
import { PortfolioConstructionRiskView } from "@/components/analysis/PortfolioConstructionRiskView";
import { YukiraScoreCard } from "@/components/analysis/YukiraScoreCard";
import { FundOverviewPanel } from "@/components/analysis/FundOverviewPanel";
import { GroundedAiInterpretationPanel } from "@/components/analysis/GroundedAiInterpretationPanel";
import { HoldingsConstructionSection } from "@/components/analysis/HoldingsConstructionSection";
import { DrawdownEpisodeView } from "@/components/analysis/DrawdownEpisodeView";
import { RollingReturnAnalysisView } from "@/components/analysis/RollingReturnAnalysisView";
import { MetricDefinitionDrawer, MetricDefinition } from "@/components/analysis/MetricDefinitionDrawer";
import {
  fetchHoldingsConstruction,
  fetchDrawdownEpisodes,
  fetchRollingReturns,
  FundHoldingsConstruction,
  DrawdownEpisodeReport,
  RollingReturnReport,
} from "@/lib/api/research";
import { CanonicalEvidencePacketModal } from "@/components/epistemic/CanonicalEvidencePacketModal";
import { fetchOptionEnrichment } from "@/lib/api/enrichment";
import { EnrichedFundProfileDto } from "@/types/enrichment";

import { useWatchlist } from "@/lib/hooks/useWatchlist";
import { ResearchNoteModal } from "@/components/watchlist/ResearchNoteModal";
import { FundResearchBriefModal } from "@/components/watchlist/FundResearchBriefModal";
import { WatchlistResponse } from "@/lib/api/investor";
import { FundMonitoringSection } from "@/components/monitoring/FundMonitoringSection";
import { formatDateTime } from "@/lib/utils/formatters";
import {
  getEvidenceSnapshot,
  computeEvidenceChanges,
} from "@/lib/utils/evidenceChange";

interface PageProps {
  params: Promise<{ id: string }>;
}

export type PointInTimeAnchor = "LATEST_MARKET" | "HISTORICAL_AUDIT";
export type AnalyticalHorizonCode = "3Y" | "5Y" | "10Y";

function FundDetailContent({ schemeId }: { schemeId: string | number }) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const optionParam = searchParams.get("option");
  const runParam = searchParams.get("run");
  const horizonParam = searchParams.get("horizon");
  const fromParam = searchParams.get("from") || searchParams.get("returnUrl");
  const returnUrl = fromParam ? (fromParam.startsWith("/") ? fromParam : decodeURIComponent(fromParam)) : "/funds";

  const [scheme, setScheme] = useState<Scheme | null>(null);
  const [options, setOptions] = useState<SchemeOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Selected Option & URL Sync
  const [selectedOptionId, setSelectedOptionId] = useState<number | null>(null);

  // Real-data holdings construction state (Tasks 231-236)
  const [holdingsConstruction, setHoldingsConstruction] = useState<FundHoldingsConstruction | null>(null);
  const [holdingsLoading, setHoldingsLoading] = useState(false);

  // Real-data drawdown episodes state (Task 225)
  const [drawdownEpisodes, setDrawdownEpisodes] = useState<DrawdownEpisodeReport | null>(null);
  const [drawdownLoading, setDrawdownLoading] = useState(false);

  // Real-data rolling return distribution state (Task 226)
  const [rollingAnalysisHorizon, setRollingAnalysisHorizon] = useState<string>("3Y");
  const [rollingReport, setRollingReport] = useState<RollingReturnReport | null>(null);
  const [rollingReportLoading, setRollingReportLoading] = useState(false);

  // Epistemic Metric Definition Drawer state (Task 221)
  const [activeDefinitionMetric, setActiveDefinitionMetric] = useState<MetricDefinition | null>(null);
  const [isDefinitionDrawerOpen, setIsDefinitionDrawerOpen] = useState(false);

  useEffect(() => {
    if (!selectedOptionId) return;
    let ignore = false;
    setHoldingsLoading(true);
    fetchHoldingsConstruction(selectedOptionId)
      .then((data) => {
        if (!ignore) {
          setHoldingsConstruction(data);
          setHoldingsLoading(false);
        }
      })
      .catch(() => {
        if (!ignore) {
          setHoldingsConstruction(null);
          setHoldingsLoading(false);
        }
      });
    return () => {
      ignore = true;
    };
  }, [selectedOptionId]);

  const [optionNotice, setOptionNotice] = useState<{
    type: "unavailable" | "empty";
    requested?: string | null;
    message: string;
  } | null>(null);
  const [executingMetric, setExecutingMetric] = useState<string | null>(null);
  const [triggerError, setTriggerError] = useState<string | null>(null);

  // Point-in-Time Temporal Anchor
  const [pitAnchor, setPitAnchor] = useState<PointInTimeAnchor>("LATEST_MARKET");
  const [showEvidencePacketModal, setShowEvidencePacketModal] = useState(false);

  // Explicit Analytical Lookback Horizon (3Y, 5Y, 10Y)
  const initialHorizon: AnalyticalHorizonCode =
    horizonParam === "5Y" || horizonParam === "10Y" ? horizonParam : "3Y";
  const [selectedHorizon, setSelectedHorizon] = useState<AnalyticalHorizonCode>(initialHorizon);

  const activeConfig = pitAnchor === "LATEST_MARKET"
    ? { asOfDate: "2026-10-01", knowledgeCutoffTime: "2026-10-10T23:59:59+05:30" }
    : { asOfDate: "2024-01-15", knowledgeCutoffTime: "2024-01-31T23:59:59+05:30" };

  useEffect(() => {
    if (!selectedOptionId) return;
    let ignore = false;
    setDrawdownLoading(true);
    fetchDrawdownEpisodes(selectedOptionId, 10)
      .then((data) => {
        if (!ignore) {
          setDrawdownEpisodes(data);
          setDrawdownLoading(false);
        }
      })
      .catch(() => {
        if (!ignore) {
          setDrawdownEpisodes(null);
          setDrawdownLoading(false);
        }
      });
    return () => {
      ignore = true;
    };
  }, [selectedOptionId, activeConfig.asOfDate, activeConfig.knowledgeCutoffTime]);

  useEffect(() => {
    if (!selectedOptionId) return;
    let ignore = false;
    setRollingReportLoading(true);
    fetchRollingReturns(selectedOptionId, rollingAnalysisHorizon)
      .then((data) => {
        if (!ignore) {
          setRollingReport(data);
          setRollingReportLoading(false);
        }
      })
      .catch(() => {
        if (!ignore) {
          setRollingReport(null);
          setRollingReportLoading(false);
        }
      });
    return () => {
      ignore = true;
    };
  }, [selectedOptionId, rollingAnalysisHorizon, activeConfig.asOfDate, activeConfig.knowledgeCutoffTime]);

  // Verified Analytical Profile State (Direct Quant Engine Execution)
  const [profile, setProfile] = useState<AnalyticalProfileResponse | null>(null);
  const [profileLoading, setProfileLoading] = useState(false);
  const [profileError, setProfileError] = useState<string | null>(null);
  const [expandedEvidenceMetric, setExpandedEvidenceMetric] = useState<string | null>(null);
  const [activeClaimContext, setActiveClaimContext] = useState<{
    claimText?: string;
    claimType?: string;
    methodologyStatus?: string;
  } | null>(null);
  const [unavailableClaimNotice, setUnavailableClaimNotice] = useState<string | null>(null);

  // Clear expanded evidence and claim context when option, horizon, or pit anchor changes
  useEffect(() => {
    setExpandedEvidenceMetric(null);
    setActiveClaimContext(null);
    setUnavailableClaimNotice(null);
  }, [selectedOptionId, selectedHorizon, pitAnchor]);
  const effectiveSchemeId = scheme?.id ?? (typeof schemeId === "number" ? schemeId : 0);
  const { isWatchlisted, toggleWatchlist, watchlistItems, saveResearchNote, updateInvestigationStatus } = useWatchlist();
  const watchlisted = isWatchlisted(effectiveSchemeId, selectedOptionId || undefined);
  const watchlistItem = watchlistItems.find(
    (item) => item.schemeId === effectiveSchemeId && (selectedOptionId ? item.schemeOptionId === selectedOptionId : true)
  ) ?? watchlistItems.find((item) => item.schemeId === effectiveSchemeId);
  const [isNoteModalOpen, setIsNoteModalOpen] = useState(false);
  const [isBriefModalOpen, setIsBriefModalOpen] = useState(false);
  const [briefInitialSection, setBriefInitialSection] = useState<"A" | "B" | "C" | "D" | "E" | "F" | "G">("A");
  const [investigationPrefill, setInvestigationPrefill] = useState<{
    investigationQuestion?: string;
    reasonSaved?: string;
    nextStep?: string;
  } | undefined>(undefined);

  // Cross-workflow deep linking for brief view (e.g. /funds/1?brief=1&section=G)
  useEffect(() => {
    if (searchParams.get("brief") === "1" || searchParams.get("tab") === "brief") {
      const sectionParam = searchParams.get("section");
      if (sectionParam === "F" || sectionParam === "changes" || sectionParam === "history") {
        setBriefInitialSection("F");
      } else if (sectionParam === "G" || sectionParam === "portfolio" || sectionParam === "holding") {
        setBriefInitialSection("G");
      } else {
        setBriefInitialSection("A");
      }
      setIsBriefModalOpen(true);
    }
  }, [searchParams]);

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

  const fundSnapshot = useMemo(() => {
    if (!scheme) return null;
    return getEvidenceSnapshot(scheme.id, selectedOptionId);
  }, [scheme, selectedOptionId]);

  const fundEvidenceSummary = useMemo(() => {
    if (!activeFundForNote) return null;
    return computeEvidenceChanges(activeFundForNote, profile, fundSnapshot);
  }, [activeFundForNote, profile, fundSnapshot]);

  const handleInvestigateQuestion = (question: string, reason?: string) => {
    setInvestigationPrefill({
      investigationQuestion: question,
      reasonSaved: reason || `Investigating ${scheme?.name || "fund"} due diligence question`,
    });
    setIsNoteModalOpen(true);
  };

  const handleOptionSelect = (optId: number) => {
    setSelectedOptionId(optId);
    setOptionNotice(null);
    router.replace(
      `/funds/${scheme?.code || scheme?.id || schemeId}?option=${optId}&horizon=${selectedHorizon}`,
      { scroll: false }
    );
  };

  const handleHorizonSelect = (h: AnalyticalHorizonCode) => {
    if (h === selectedHorizon) return; // avoid redundant execution
    setSelectedHorizon(h);
    router.replace(
      `/funds/${scheme?.code || scheme?.id || schemeId}?option=${selectedOptionId || ""}&horizon=${h}`,
      { scroll: false }
    );
  };

  // Deterministic start date based on selected horizon and asOfDate
  const calculatedStartDate = useMemo(() => {
    if (profile?.context?.startDate) {
      return String(profile.context.startDate);
    }
    const asOf = new Date(activeConfig.asOfDate);
    const years = selectedHorizon === "10Y" ? 10 : selectedHorizon === "5Y" ? 5 : 3;
    const start = new Date(asOf);
    start.setFullYear(start.getFullYear() - years);
    return start.toISOString().split("T")[0];
  }, [profile?.context?.startDate, activeConfig.asOfDate, selectedHorizon]);

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
          if (optionParam) {
            const requestedOptId = parseInt(optionParam, 10);
            const matchOpt = schemeOptions.find(
              (o) =>
                (!isNaN(requestedOptId) && o.id === requestedOptId) ||
                o.amfiCode === optionParam ||
                o.isin === optionParam
            );
            if (matchOpt) {
              setSelectedOptionId(matchOpt.id);
              setOptionNotice(null);
            } else {
              // Exact instrument identity: do NOT silently substitute an alternate option
              setSelectedOptionId(null);
              setOptionNotice({
                type: "unavailable",
                requested: optionParam,
                message: `Requested share class option "${optionParam}" does not exist in the canonical registry for ${schemeData.name}. YUKIRA strictly preserves instrument identity and never silently substitutes Direct/Regular plans or Growth/IDCW options. Please select an available registered share class below to inspect its analytical profile.`,
              });
            }
          } else {
            // No specific option requested in URL: select the default primary share class
            const directGrowth =
              schemeOptions.find((o) => o.plan?.planType === "DIRECT" && o.optionType === "GROWTH") ||
              schemeOptions.find((o) => o.plan?.planType === "DIRECT") ||
              schemeOptions[0];
            setSelectedOptionId(directGrowth.id);
            setOptionNotice(null);
          }
        } else {
          // Scheme has zero registered options
          setSelectedOptionId(null);
          setOptionNotice({
            type: "empty",
            requested: null,
            message: `No share class options are currently registered for ${schemeData.name} in master records. Observations cannot be targeted until share class options are ingested.`,
          });
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

    const requestedRunId = runParam ? parseInt(runParam, 10) : NaN;

    executeProfileAnalysis({
      schemeOptionId: selectedOptionId,
      asOfDate: activeConfig.asOfDate,
      knowledgeCutoffTime: activeConfig.knowledgeCutoffTime,
      methodologyTag: "APPROVED_M2N",
      horizon: selectedHorizon,
      runId: !isNaN(requestedRunId) ? requestedRunId : undefined,
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
  }, [selectedOptionId, selectedHorizon, pitAnchor, runParam]);

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
      const activeConfig = pitAnchor === "LATEST_MARKET"
        ? { asOfDate: "2026-10-01", knowledgeCutoffTime: "2026-10-10T23:59:59+05:30" }
        : { asOfDate: "2024-01-15", knowledgeCutoffTime: "2024-01-31T23:59:59+05:30" };

      const run = await triggerCalculation({
        schemeOptionId: selectedOptionId,
        benchmarkId: 1,
        asOfDate: activeConfig.asOfDate,
        knowledgeCutoffTime: activeConfig.knowledgeCutoffTime,
        metricCodes: [metricCode],
        methodologyTag: "CANDIDATE_V1",
        parameters: {
          horizon: selectedHorizon,
        },
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
    startDate: calculatedStartDate,
    endDate: profile?.context?.asOfDate ? String(profile.context.asOfDate) : activeConfig.asOfDate,
    knowledgeCutoffTime: profile?.context?.knowledgeCutoffTime ? String(profile.context.knowledgeCutoffTime) : activeConfig.knowledgeCutoffTime,
    observationCount: profile?.provenance?.navObservationCount,
    calculationRunId: profile?.provenance?.calculationRunId,
    methodologyTag: profile?.provenance?.methodologyTag,
    engineSoftwareVersion: profile?.provenance?.engineSoftwareVersion,
    inputSnapshotSha256: profile?.provenance?.inputSnapshotSha256,
    actualStartDate: calculatedStartDate,
    actualEndDate: profile?.context?.asOfDate ? String(profile.context.asOfDate) : activeConfig.asOfDate,
    requestedHorizon: selectedHorizon,
    observationThreshold: selectedHorizon === "10Y" ? 2400 : selectedHorizon === "5Y" ? 1200 : 700,
    benchmarkMappingStatus: "CANDIDATE",
    dataQualityAssessment: profile?.quality?.overallAssessment,
    sampleObservations: profile?.provenance?.sampleObservations,
    claimContext: activeClaimContext || undefined,
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
      defaultGovernanceStatus?: MetricGovernanceStatus;
    }
  ) => {
    const item = getMetricItem(code);
    const isCalculated = item?.calculationStatus === "CALCULATED" && item?.formattedValue !== undefined && item?.formattedValue !== null && item?.formattedValue !== "";
    const rawGov = item?.governanceStatus?.toLowerCase();
    const governance: MetricGovernanceStatus =
      rawGov === "approved" ||
      rawGov === "validated" ||
      rawGov === "candidate" ||
      rawGov === "unimplemented" ||
      rawGov === "pending_ingestion" ||
      rawGov === "unsupported" ||
      rawGov === "restricted"
        ? rawGov
        : defaultConfig.defaultGovernanceStatus || "candidate";
    const isExpanded = expandedEvidenceMetric === code;

    return (
      <div key={code} id={`metric-${code}`} className="flex flex-col scroll-mt-24">
        <MetricCard
          code={code}
          name={defaultConfig.name}
          category={defaultConfig.category}
          categoryLabel={defaultConfig.categoryLabel}
          governanceStatus={governance}
          isCalculated={isCalculated}
          calculationStatus={item?.calculationStatus ?? undefined}
          errorMessage={item?.errorMessage ?? undefined}
          formattedValue={item?.formattedValue ?? undefined}
          units={item?.units}
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
        <div className="mt-2 text-right">
          <button
            type="button"
            onClick={() => setExpandedEvidenceMetric(isExpanded ? null : code)}
            className="text-[12px] font-mono text-accent hover:underline inline-flex items-center gap-1"
          >
            <span>{isExpanded ? "Hide Metric Evidence ▲" : "Understand Metric Evidence ▼"}</span>
          </button>
          <button
            type="button"
            onClick={() => {
              setActiveDefinitionMetric({
                metricCode: code,
                canonicalName: defaultConfig.name,
                categoryLabel: defaultConfig.categoryLabel,
                governanceStatus: governance,
                mathematicalDefinition: defaultConfig.description,
                formulaDisplay: item?.formulaDisclosure || defaultConfig.formula,
                financialMeaning: item?.interpretation || defaultConfig.interpretation,
                investigationQuestion: item?.investigationQuestion || defaultConfig.investigationQuestion,
                limitations: item?.limitations || defaultConfig.limitation,
              });
              setIsDefinitionDrawerOpen(true);
            }}
            className="ml-3 text-[12px] font-mono text-primary hover:underline inline-flex items-center gap-1"
          >
            <span>Methodology &amp; Formula ↗</span>
          </button>
          {isExpanded && (
            <div className="mt-3 text-left">
              <MetricEvidencePanel
                metric={
                  item || {
                    metricCode: code,
                    metricName: defaultConfig.name,
                    category: defaultConfig.category,
                    numericValue: null,
                    formattedValue: null,
                    units: "",
                    periodType: defaultConfig.period,
                    calculationStatus: "NOT_CALCULATED",
                    governanceStatus: (defaultConfig.defaultGovernanceStatus || "candidate").toUpperCase(),
                    formulaDisclosure: defaultConfig.formula,
                    interpretation: defaultConfig.interpretation,
                    limitations: defaultConfig.limitation,
                    investigationQuestion: defaultConfig.investigationQuestion,
                  }
                }
                context={evidenceContext}
                onInvestigate={(inv) => handleInvestigateQuestion(inv.suggestedQuestion, inv.suggestedReason)}
                isCollapsible={false}
                defaultExpanded={true}
              />
            </div>
          )}
        </div>
      </div>
    );
  };

  return (
    <PageContainer
      title={scheme.name}
      subtitle={`Scheme Code: ${scheme.code} • Master ID: #${scheme.id}`}
      breadcrumbs={[
        { label: "Home", href: "/" },
        { label: fromParam ? "Search Results" : "Funds", href: returnUrl },
        { label: scheme.code, href: `/funds/${scheme.id}` },
      ]}
      action={
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => {
              setBriefInitialSection("A");
              setIsBriefModalOpen(true);
            }}
            className="btn btn-secondary flex items-center gap-1.5"
            title="View consolidated evidence-led research brief"
          >
            <svg className="h-3.5 w-3.5 text-accent" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
            <span>Research Brief</span>
            {fundEvidenceSummary && fundEvidenceSummary.changes.length > 0 && (
              <span className="w-2 h-2 rounded-full bg-amber-400" title={`${fundEvidenceSummary.changes.length} evidence changes detected`} />
            )}
          </button>
          <Link
            href={`/compare?funds=${scheme.id}${selectedOptionId ? `&options=${scheme.id}:${selectedOptionId}` : ""}`}
            className="btn btn-secondary"
            title="Compare this fund option in multi-fund screener"
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
                category: scheme.category,
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
      {/* Search Context Navigation Return Link */}
      <div className="mb-4 flex items-center justify-between">
        <Link
          href={returnUrl}
          className="inline-flex items-center gap-1.5 text-xs font-mono text-text-secondary hover:text-accent transition-colors"
        >
          <span>←</span>
          <span>{fromParam ? "Back to Filtered Search Results" : "Back to Funds Catalog"}</span>
        </Link>
      </div>

      {/* Investigation Due Diligence Context Banner */}
      {watchlisted && (
        <section className="panel mb-8 p-4 bg-surface border-l-4 border-l-accent" aria-label="Investigation Due Diligence">
          {/* Evidence Change Review Callout (when item is REVIEWED but underlying evidence changed) */}
          {fundEvidenceSummary?.requiresReview && (
            <div className="panel p-3 bg-amber-500/10 border border-amber-500/40 rounded-lg flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs mb-3">
              <div className="flex items-start gap-2.5">
                <span className="text-amber-400 font-bold shrink-0 mt-0.5">⚠️</span>
                <div>
                  <span className="font-bold text-amber-300 block font-mono text-[11px] uppercase tracking-wider">
                    EVIDENCE CHANGES DETECTED SINCE PREVIOUS REVIEW
                  </span>
                  <p className="text-text-secondary text-[11.5px] mt-0.5">
                    Underlying market observations or calculation runs have updated since this fund was reviewed. Inspect verified differences in the research brief.
                  </p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => {
                  setBriefInitialSection("F");
                  setIsBriefModalOpen(true);
                }}
                className="btn btn-secondary btn-xs text-amber-300 border-amber-500/50 hover:bg-amber-500/20 whitespace-nowrap self-start sm:self-auto font-mono"
              >
                Review Evidence Changes →
              </button>
            </div>
          )}

          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border pb-2.5 mb-3">
            <div className="flex items-center gap-2">
              <span className="eyebrow text-accent">Investor Due Diligence Context</span>
              <span className="status-badge state-approved text-[10px]">SAVED IN WORKSPACE</span>
              {watchlistItem?.isHeldInPortfolio ? (
                <button
                  type="button"
                  onClick={() => {
                    setBriefInitialSection("G");
                    setIsBriefModalOpen(true);
                  }}
                  className="status-badge state-approved text-[10px] font-bold hover:brightness-110 cursor-pointer"
                  title="Inspect exact portfolio holding relationship in Research Brief"
                >
                  ● HELD IN PORTFOLIO
                </button>
              ) : watchlistItem?.heldDifferentVariantNotice ? (
                <button
                  type="button"
                  onClick={() => {
                    setBriefInitialSection("G");
                    setIsBriefModalOpen(true);
                  }}
                  className="status-badge state-candidate text-[10px] font-mono text-amber-300 hover:brightness-110 cursor-pointer"
                  title="Inspect held variant vs researched plan in Research Brief"
                >
                  ⚠️ DIFFERENT OPTION HELD
                </button>
              ) : null}
              {fundEvidenceSummary && (
                <span
                  className={`status-badge text-[10px] font-mono font-semibold px-2 py-0.5 rounded border ${fundEvidenceSummary.badgeClass}`}
                  title={fundEvidenceSummary.description}
                >
                  {fundEvidenceSummary.badgeLabel}
                </span>
              )}
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => {
                  setBriefInitialSection("A");
                  setIsBriefModalOpen(true);
                }}
                className="btn btn-secondary btn-sm flex items-center gap-1.5 text-accent border-accent/40 hover:bg-accent/10"
                title="Open consolidated research brief"
              >
                <svg className="h-3.5 w-3.5 text-accent" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                </svg>
                <span>Research Brief →</span>
              </button>
              {(watchlistItem?.isHeldInPortfolio || watchlistItem?.heldDifferentVariantNotice) && (
                <button
                  type="button"
                  onClick={() => {
                    setBriefInitialSection("G");
                    setIsBriefModalOpen(true);
                  }}
                  className="btn btn-ghost btn-xs text-emerald-400 border border-emerald-500/30 hover:bg-emerald-500/10 font-mono text-[11px]"
                  title="Open research brief at Section G: Portfolio Context"
                >
                  Portfolio Context ↗
                </button>
              )}
              <Link
                href={`/watchlist#saved-${selectedOptionId || scheme.id}`}
                className="btn btn-ghost btn-sm text-[11px] text-accent hover:underline py-1 px-2"
                title="Navigate directly to this fund's research card in workspace"
              >
                View in Research Workspace →
              </Link>
              <button
                onClick={() => setIsNoteModalOpen(true)}
                className="btn btn-secondary btn-sm"
              >
                {watchlistItem?.reasonSaved || watchlistItem?.investigationQuestion || watchlistItem?.nextStep || watchlistItem?.workingThesis
                  ? "Edit Research Thesis & Notes"
                  : "+ Record Research Thesis"}
              </button>
            </div>
          </div>

          {watchlistItem?.reasonSaved || watchlistItem?.investigationQuestion || watchlistItem?.nextStep || watchlistItem?.workingThesis ? (
            <div className="space-y-3 font-mono text-[11.5px]">
              {/* Phase F7: Investor Working Thesis */}
              {watchlistItem?.workingThesis && (
                <div className="p-3 rounded-lg bg-surface-raised border border-accent/30 space-y-1.5">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span className="text-accent text-[10px] block font-bold uppercase tracking-wider">
                        INVESTOR WORKING THESIS (HYPOTHESIS):
                      </span>
                      <span className="status-badge state-candidate text-[9px]">INVESTOR HYPOTHESIS</span>
                    </div>
                    {watchlistItem.thesisUpdatedAt && (
                      <span className="text-[10px] text-text-muted">
                        Revised: {watchlistItem.thesisUpdatedAt.split("T")[0]}
                      </span>
                    )}
                  </div>
                  <p className="text-text-primary font-sans text-[13px] font-medium leading-snug pl-1">
                    &ldquo;{watchlistItem.workingThesis}&rdquo;
                  </p>
                  {watchlistItem.reconsiderationConditions && (
                    <div className="pt-1.5 text-[11px] border-t border-border/40 text-text-secondary">
                      <strong className="text-text-muted text-[9.5px] uppercase block">Reconsideration Triggers:</strong>
                      <span className="text-amber-300/90 font-sans">{watchlistItem.reconsiderationConditions}</span>
                    </div>
                  )}
                  {watchlistItem.supportingEvidence && (
                    <div className="pt-1 text-[11px] border-t border-border/40 text-text-secondary">
                      <strong className="text-text-muted text-[9.5px] uppercase block">Supporting Evidence Noted:</strong>
                      <span className="text-text-secondary font-sans">{watchlistItem.supportingEvidence}</span>
                    </div>
                  )}
                </div>
              )}

              <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
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
            </div>
          ) : (
            <p className="text-[12px] font-mono text-text-tertiary italic">
              Fund saved for investigation. Click &ldquo;+ Record Research Thesis&rdquo; to document your hypothesis and evidence criteria before committing capital.
            </p>
          )}
        </section>
      )}

      {/* Explicit Calculation Run Audit Notice (when navigated via run ID) */}
      {runParam && profile?.context?.runId && (
        <section className="panel mb-6 p-4 border border-cyan-500/30 bg-cyan-500/5 rounded-xl font-mono text-xs" aria-label="Calculation Run Audit Banner">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <span className="font-bold text-cyan-400 bg-cyan-500/10 px-2 py-0.5 rounded border border-cyan-500/20">
                AUDIT RUN #{profile.context.runId}
              </span>
              <span className="text-text-cream-muted">
                As-Of: <strong className="text-text-cream-bright">{profile.context.asOfDate}</strong>
              </span>
              <span className="text-text-cream-muted">
                Knowledge Cutoff: <strong className="text-text-cream-bright">{formatDateTime(profile.context.knowledgeCutoffTime)}</strong>
              </span>
              <span className={`px-1.5 py-0.2 rounded text-[10px] font-bold ${
                profile.context.runStatus === "COMPLETED" || profile.context.runStatus === "SUCCESS"
                  ? "bg-emerald-500/10 text-emerald-300 border border-emerald-500/30"
                  : "bg-amber-500/10 text-amber-300 border border-amber-500/30"
              }`}>
                {profile.context.runStatus}
              </span>
            </div>
            <Link
              href={`/analysis/${profile.context.runId}`}
              className="text-cyan-400 hover:text-cyan-300 underline font-semibold flex items-center gap-1"
            >
              <span>Inspect Full Cryptographic Lineage</span> →
            </Link>
          </div>
        </section>
      )}

      {/* Unified Evidence Quality Banner for Historical Pilot Baseline */}
      {(activeConfig.asOfDate === "2024-01-15" || profile?.context?.asOfDate === "2024-01-15") && (
        <div className="mb-6">
          <EvidenceQualitySummary
            variant="banner"
            asOfDate="2024-01-15"
            freshnessState="STALE"
            methodologyStatus={selectedHorizon === "3Y" ? "APPROVED" : "CANDIDATE"}
            interpretationConsequence={
              <span>
                <strong>Historical Audit Baseline Active:</strong> NAV observations end at <strong>2024-01-15</strong> (Phase 2I/2J frozen canonical pilot). Stale historical NAVs cannot establish present-day liquidatable value. Metrics reflect verified historical market regimes.
              </span>
            }
            nextStepLabel="Jump to Data Coverage & Cadence"
            nextStepHref="#coverage"
          />
        </div>
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

      {/* Explicit Share Class Identity Notice (when requested option is missing or unavailable) */}
      {optionNotice && (
        <section
          className={`panel mb-6 p-4 rounded-2xl border ${
            optionNotice.type === "unavailable"
              ? "border-amber-500/50 bg-amber-500/10 text-amber-200"
              : "border-neutral-500/50 bg-surface-raised text-text-secondary"
          }`}
          aria-label="Share Class Identity Notice"
        >
          <div className="flex items-start gap-3">
            <span className="text-amber-400 font-bold text-lg shrink-0 mt-0.5">⚠️</span>
            <div className="space-y-1.5 flex-1 min-w-0">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h3 className="font-mono text-xs font-bold uppercase tracking-wider text-amber-300">
                  {optionNotice.type === "unavailable"
                    ? "Requested Share Class Option Unavailable"
                    : "No Registered Share Classes Available"}
                </h3>
                <span className="status-badge state-candidate text-[9.5px]">
                  IDENTITY BOUNDARY ENFORCED
                </span>
              </div>
              <p className="font-sans text-[13px] leading-relaxed text-text-cream-bright">
                {optionNotice.message}
              </p>
              {options.length > 0 && (
                <div className="pt-2 border-t border-amber-500/20">
                  <span className="font-mono text-xs text-text-cream-muted block mb-1.5">
                    Available registered share classes for this fund (select one to proceed):
                  </span>
                  <div className="flex flex-wrap gap-2">
                    {options.map((o) => (
                      <button
                        key={o.id}
                        type="button"
                        onClick={() => handleOptionSelect(o.id)}
                        className="px-3 py-1 rounded-lg border border-border-gold-subtle bg-surface-midnight text-xs font-mono text-primary hover:border-primary transition-all flex items-center gap-1.5"
                      >
                        <span className="font-bold">Option #{o.id}</span>
                        <span className="text-text-cream-muted">· {o.plan?.planType || "DIRECT"} {o.optionType}</span>
                        {o.amfiCode && <span className="text-[10px] text-text-cream-muted/70">(AMFI {o.amfiCode})</span>}
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>
        </section>
      )}

      {/* THREE-LEVEL EPISTEMIC DISCLOSURE HIERARCHY */}
      <div className="panel mb-6 border-l-4 border-l-accent p-3.5 bg-surface text-xs">
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border pb-2 mb-2">
          <div className="flex items-center gap-2">
            <span className="eyebrow text-accent font-bold">Institutional Epistemic Standard</span>
            <span className="text-text-primary font-semibold">Three-Level Progressive Disclosure Hierarchy</span>
          </div>
          <span className="status-badge state-approved text-[10px]">Deterministic Non-LLM Math</span>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3 text-text-secondary">
          <div className="panel-inset p-2.5 bg-surface-raised">
            <div className="flex items-center gap-1.5 font-semibold text-text-primary mb-1">
              <span className="status-badge state-approved text-[9px] px-1.5 py-0.5">LEVEL 1</span>
              <span>Observed Facts</span>
            </div>
            <p className="text-[11px] leading-snug text-text-tertiary">
              Immutable empirical observations: raw AMFI NAV prices, dates, unforced sums, observation counts, and cryptographic SHA-256 artifacts. Zero synthetic estimates.
            </p>
          </div>
          <div className="panel-inset p-2.5 bg-surface-raised">
            <div className="flex items-center gap-1.5 font-semibold text-text-primary mb-1">
              <span className="status-badge state-candidate text-[9px] px-1.5 py-0.5">LEVEL 2</span>
              <span>Calculated Indications</span>
            </div>
            <p className="text-[11px] leading-snug text-text-tertiary">
              Deterministic quantitative calculations: annualized CAGR, downside risk, historical stress drawdowns, beta dynamics, and capture ratios. No LLM math approximations.
            </p>
          </div>
          <div className="panel-inset p-2.5 bg-surface-raised">
            <div className="flex items-center gap-1.5 font-semibold text-text-primary mb-1">
              <span className="status-badge state-unavailable text-[9px] px-1.5 py-0.5">LEVEL 3</span>
              <span>Uncertainties &amp; Limitations</span>
            </div>
            <p className="text-[11px] leading-snug text-text-tertiary">
              Epistemic boundary conditions: missing observation penalties, lookback horizon adequacy, benchmark synchronicity constraints, and candidate governance status.
            </p>
          </div>
        </div>
      </div>

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

        <dl className="mb-5 grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-6">
          <div className="panel-inset p-3">
            <dt className="def-label">Scheme Code</dt>
            <dd className="def-value">{scheme.code}</dd>
            <dd className="mono-meta mt-1">Master ID: #{scheme.id}</dd>
          </div>

          <div className="panel-inset p-3">
            <dt className="def-label">Category</dt>
            <dd className="def-value truncate">{scheme.category || "Equity Scheme"}</dd>
            <dd className="mono-meta mt-1">SEBI Categorization</dd>
          </div>

          <div className="panel-inset p-3">
            <dt className="def-label">Target Share Class</dt>
            <dd className="def-value text-accent truncate">
              {selectedOption
                ? `${selectedOption.plan?.planType || "DIRECT"} ${selectedOption.optionType}`
                : "None Selected"}
            </dd>
            <dd className="mono-meta mt-1 truncate" title={`AMFI: ${selectedOption?.amfiCode || "N/A"} · ISIN: ${selectedOption?.isin || "N/A"}`}>
              AMFI: {selectedOption?.amfiCode || "N/A"} · Option #{selectedOption?.id || "N/A"}
            </dd>
          </div>

          <div className="panel-inset p-3">
            <dt className="def-label">Applicable Benchmark</dt>
            <dd className="def-value text-[13px] text-text-cream-bright truncate" title={profile?.context?.benchmarkName || "NIFTY 500 TRI"}>
              {profile?.context?.benchmarkName || "NIFTY 500 TRI"}
            </dd>
            <dd className="mono-meta mt-1">
              <span className="status-badge state-candidate text-[9.5px]">Candidate Mapping</span>
            </dd>
          </div>

          <div className="panel-inset p-3">
            <dt className="def-label">Inception Date</dt>
            <dd className="def-value">{scheme.inceptionDate || "Not available"}</dd>
            <dd className="mono-meta mt-1">AMFI Record</dd>
          </div>

          <div className="panel-inset p-3">
            <dt className="def-label">Active Horizon</dt>
            <dd className="def-value text-primary font-mono flex items-center gap-1.5">
              <span className="status-badge state-approved text-[10px]">{selectedHorizon}</span>
              <span className="text-[12px] text-text-cream-muted">
                {selectedHorizon === "10Y" ? "120M" : selectedHorizon === "5Y" ? "60M" : "36M"}
              </span>
            </dd>
            <dd className="mono-meta mt-1">Deterministic Window</dd>
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
                    <td className="key">
                      <span className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-mono font-bold uppercase tracking-wider ${
                        opt.plan?.planType === "DIRECT"
                          ? "bg-emerald-500/15 text-emerald-400 border border-emerald-500/30"
                          : "bg-amber-500/15 text-amber-300 border border-amber-500/30"
                      }`}>
                        {opt.plan?.planType || "DIRECT"}
                      </span>
                    </td>
                    <td>
                      <span className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-mono font-medium ${
                        opt.optionType === "GROWTH"
                          ? "bg-sky-500/15 text-sky-300 border border-sky-500/30"
                          : "bg-purple-500/15 text-purple-300 border border-purple-500/30"
                      }`}>
                        {opt.optionType || "GROWTH"}
                      </span>
                    </td>
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

        {/* Unified Investor-Facing Evidence Quality, Freshness & Limitations Center */}
        <div className="mb-6">
          <EvidenceQualitySummary
            variant="panel"
            title="Data Freshness, Observation Coverage & Analytical Limitations"
            subtitle={`${scheme?.name || "Fund Profile"} · Option #${selectedOption?.id || selectedOptionId} (${selectedOption?.plan?.planType || "DIRECT"} ${selectedOption?.optionType || "GROWTH"})`}
            contextName={scheme?.name}
            asOfDate={profile?.context?.asOfDate ? String(profile.context.asOfDate) : activeConfig.asOfDate}
            knowledgeCutoff={profile?.context?.knowledgeCutoffTime || activeConfig.knowledgeCutoffTime}
            freshnessState={(profile?.context?.asOfDate === "2024-01-15" || activeConfig.asOfDate === "2024-01-15") ? "STALE" : "CURRENT"}
            freshnessPolicy="AMFI Daily NAV Cadence Policy (≤ 3 trading days lag)"
            observationCount={isCanonicalPilot ? 1243 : (profile?.provenance?.navObservationCount ?? 1243)}
            observationWindow={`${calculatedStartDate} → ${profile?.context?.asOfDate ? String(profile.context.asOfDate) : activeConfig.asOfDate}`}
            horizonRequested={`${selectedHorizon} Lookback Window (${selectedHorizon === "10Y" ? "120M" : selectedHorizon === "5Y" ? "60M" : "36M"})`}
            coverageState={selectedHorizon === "3Y" ? "SUFFICIENT" : "PARTIAL"}
            coverageNote={selectedHorizon === "3Y" ? "Meets ≥ 700 trading days requirement for M2N risk-adjusted metrics." : "Candidate horizon: requires ≥ 1,200 trading days (5Y) or ≥ 2,400 trading days (10Y)."}
            methodologyStatus={selectedHorizon === "3Y" ? "APPROVED" : "CANDIDATE"}
            methodologyVersion={profile?.provenance?.engineSoftwareVersion || "FastAPI Quant Engine v1.0"}
            interpretationConsequence={
              (profile?.context?.asOfDate === "2024-01-15" || activeConfig.asOfDate === "2024-01-15") ? (
                <span>
                  Historical NAV observations ending on <strong>2024-01-15</strong> cannot establish this fund's current market value or immediate liquidatable worth. All metrics reflect point-in-time market conditions through 2024-01-15 and strictly exclude look-ahead data.
                </span>
              ) : (
                <span>
                  Evaluated on continuous market observations through market close on {profile?.context?.asOfDate ? String(profile.context.asOfDate) : activeConfig.asOfDate}. Point-in-time constraints exclude all future revisions.
                </span>
              )
            }
            nextStepLabel="Inspect Full Lineage in Evidence Explorer"
            nextStepHref={profile?.provenance?.calculationRunId ? `/analysis/${profile.provenance.calculationRunId}` : "#data-quality"}
          />
        </div>

        {/* EXPLICIT ANALYTICAL HORIZON SELECTOR (3Y / 5Y / 10Y) */}
        <div className="mb-6 p-4 rounded-2xl border border-border-gold-subtle bg-surface-midnight/90 backdrop-blur-md shadow-lg glow-gold">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-3 border-b border-border-gold-subtle/60">
            <div>
              <span className="font-mono text-[10.5px] uppercase tracking-[0.12em] text-primary font-semibold flex items-center gap-1.5">
                <svg className="w-3.5 h-3.5 text-primary" fill="currentColor" viewBox="0 0 20 20">
                  <path clipRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-12a1 1 0 10-2 0v4a1 1 0 00.293.707l2.828 2.829a1 1 0 101.415-1.415L11 9.586V6z" fillRule="evenodd" />
                </svg>
                ANALYTICAL LOOKBACK HORIZON
              </span>
              <h3 className="text-[15px] font-bold text-text-cream-bright mt-0.5">
                Select Quantitative Horizon
              </h3>
            </div>
            <div className="flex items-center gap-2">
              {profileLoading ? (
                <span className="font-mono text-xs text-primary flex items-center gap-1.5 animate-pulse">
                  <span className="w-2 h-2 rounded-full bg-primary" />
                  Calculating {selectedHorizon} Horizon...
                </span>
              ) : profile ? (
                <span className="font-mono text-xs text-emerald-400 bg-emerald-500/10 border border-emerald-500/20 px-2.5 py-1 rounded-full flex items-center gap-1.5">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
                  {profile.context.horizon || selectedHorizon} · Run #{profile.provenance.calculationRunId} Verified
                </span>
              ) : null}
            </div>
          </div>

          <div className="mt-3.5 grid grid-cols-1 md:grid-cols-3 gap-3">
            {/* Horizon 1: 3-Year */}
            <button
              type="button"
              onClick={() => handleHorizonSelect("3Y")}
              className={`p-3.5 rounded-xl border text-left transition-all relative ${
                selectedHorizon === "3Y"
                  ? "border-primary bg-primary/10 shadow-sm ring-1 ring-primary/40"
                  : "border-border-gold-subtle/70 bg-surface-obsidian hover:border-border-gold-bright"
              }`}
            >
              <div className="flex items-center justify-between mb-1">
                <span className="font-sans font-bold text-[13.5px] text-text-cream-bright flex items-center gap-1.5">
                  <span className={`w-2 h-2 rounded-full ${selectedHorizon === "3Y" ? "bg-primary" : "bg-neutral-500"}`} />
                  3-Year Horizon (36M)
                </span>
                <span className="font-mono text-[10px] font-bold uppercase tracking-wider text-emerald-400 bg-emerald-500/15 px-2 py-0.5 rounded-full border border-emerald-500/30">
                  APPROVED M2N
                </span>
              </div>
              <div className="font-mono text-xs text-text-cream-muted mt-1 space-y-0.5">
                <div>Lookback: <span className="font-semibold text-text-cream-bright">36 Calendar Months</span></div>
                <div className="text-[11px] text-text-cream-muted/80">≥ 700 trading days · Full Risk-Adjusted Suite Enabled</div>
              </div>
            </button>

            {/* Horizon 2: 5-Year */}
            <button
              type="button"
              onClick={() => handleHorizonSelect("5Y")}
              className={`p-3.5 rounded-xl border text-left transition-all relative ${
                selectedHorizon === "5Y"
                  ? "border-primary bg-primary/10 shadow-sm ring-1 ring-primary/40"
                  : "border-border-gold-subtle/70 bg-surface-obsidian hover:border-border-gold-bright"
              }`}
            >
              <div className="flex items-center justify-between mb-1">
                <span className="font-sans font-bold text-[13.5px] text-text-cream-bright flex items-center gap-1.5">
                  <span className={`w-2 h-2 rounded-full ${selectedHorizon === "5Y" ? "bg-primary" : "bg-neutral-500"}`} />
                  5-Year Horizon (60M)
                </span>
                <span className="font-mono text-[10px] font-bold uppercase tracking-wider text-amber-400 bg-amber-500/15 px-2 py-0.5 rounded-full border border-amber-500/30">
                  CANDIDATE V1
                </span>
              </div>
              <div className="font-mono text-xs text-text-cream-muted mt-1 space-y-0.5">
                <div>Lookback: <span className="font-semibold text-text-cream-bright">60 Calendar Months</span></div>
                <div className="text-[11px] text-text-cream-muted/80">≥ 1,200 trading days · Full Macro Cycle · Risk-Adjusted Restricted</div>
              </div>
            </button>

            {/* Horizon 3: 10-Year */}
            <button
              type="button"
              onClick={() => handleHorizonSelect("10Y")}
              className={`p-3.5 rounded-xl border text-left transition-all relative ${
                selectedHorizon === "10Y"
                  ? "border-primary bg-primary/10 shadow-sm ring-1 ring-primary/40"
                  : "border-border-gold-subtle/70 bg-surface-obsidian hover:border-border-gold-bright"
              }`}
            >
              <div className="flex items-center justify-between mb-1">
                <span className="font-sans font-bold text-[13.5px] text-text-cream-bright flex items-center gap-1.5">
                  <span className={`w-2 h-2 rounded-full ${selectedHorizon === "10Y" ? "bg-primary" : "bg-neutral-500"}`} />
                  10-Year Horizon (120M)
                </span>
                <span className="font-mono text-[10px] font-bold uppercase tracking-wider text-amber-400 bg-amber-500/15 px-2 py-0.5 rounded-full border border-amber-500/30">
                  CANDIDATE V1
                </span>
              </div>
              <div className="font-mono text-xs text-text-cream-muted mt-1 space-y-0.5">
                <div>Lookback: <span className="font-semibold text-text-cream-bright">120 Calendar Months</span></div>
                <div className="text-[11px] text-text-cream-muted/80">≥ 2,400 trading days · Structural Decadal · Risk-Adjusted Restricted</div>
              </div>
            </button>
          </div>
        </div>

        {/* POINT-IN-TIME TEMPORAL ANCHOR SELECTOR */}
        <div className="mb-6 p-4 rounded-2xl border border-border-gold-subtle bg-surface-midnight/90 backdrop-blur-md shadow-lg glow-gold">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-3 border-b border-border-gold-subtle/60">
            <div>
              <span className="font-mono text-[10.5px] uppercase tracking-[0.12em] text-primary font-semibold flex items-center gap-1.5">
                <svg className="w-3.5 h-3.5 text-primary" fill="currentColor" viewBox="0 0 20 20">
                  <path clipRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-12a1 1 0 10-2 0v4a1 1 0 00.293.707l2.828 2.829a1 1 0 101.415-1.415L11 9.586V6z" fillRule="evenodd" />
                </svg>
                POINT-IN-TIME TEMPORAL ANCHOR
              </span>
              <h3 className="text-[15px] font-bold text-text-cream-bright mt-0.5">
                Market Cutoff Reference Date
              </h3>
            </div>
            <span className="font-mono text-xs text-text-cream-muted">
              Strict Look-Ahead Bias Prevention
            </span>
          </div>

          <div className="mt-3.5 grid grid-cols-1 md:grid-cols-2 gap-3">
            {/* Anchor 1: Latest Market Horizon */}
            <button
              type="button"
              onClick={() => setPitAnchor("LATEST_MARKET")}
              className={`p-3.5 rounded-xl border text-left transition-all relative ${
                pitAnchor === "LATEST_MARKET"
                  ? "border-primary bg-primary/10 shadow-sm ring-1 ring-primary/40"
                  : "border-border-gold-subtle/70 bg-surface-obsidian hover:border-border-gold-bright"
              }`}
            >
              <div className="flex items-center justify-between mb-1">
                <span className="font-sans font-bold text-[13.5px] text-text-cream-bright flex items-center gap-1.5">
                  <span className={`w-2 h-2 rounded-full ${pitAnchor === "LATEST_MARKET" ? "bg-primary" : "bg-neutral-500"}`} />
                  Latest Available Market Horizon
                </span>
                <span className="font-mono text-[10px] font-bold uppercase tracking-wider text-primary bg-primary/15 px-2 py-0.5 rounded-full border border-primary/30">
                  LATEST MARKET
                </span>
              </div>
              <div className="font-mono text-xs text-text-cream-muted mt-1 space-y-0.5">
                <div>Cutoff Date: <span className="font-semibold text-text-cream-bright">2026-10-01</span> · Knowledge: <span className="text-text-cream-body">2026-10-10</span></div>
                <div className="text-[11px] text-text-cream-muted/80">Continuous Daily NAV Observations · Multi-Horizon Eligible</div>
              </div>
            </button>

            {/* Anchor 2: Historical Audit Baseline */}
            <button
              type="button"
              onClick={() => setPitAnchor("HISTORICAL_AUDIT")}
              className={`p-3.5 rounded-xl border text-left transition-all relative ${
                pitAnchor === "HISTORICAL_AUDIT"
                  ? "border-primary bg-primary/10 shadow-sm ring-1 ring-primary/40"
                  : "border-border-gold-subtle/70 bg-surface-obsidian hover:border-border-gold-bright"
              }`}
            >
              <div className="flex items-center justify-between mb-1">
                <span className="font-sans font-bold text-[13.5px] text-text-cream-bright flex items-center gap-1.5">
                  <span className={`w-2 h-2 rounded-full ${pitAnchor === "HISTORICAL_AUDIT" ? "bg-primary" : "bg-neutral-500"}`} />
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
              {profile?.context?.asOfDate ? String(profile.context.asOfDate) : activeConfig.asOfDate}
            </span>
            <p className="mt-1.5 text-[12px] leading-[1.5] text-text-secondary">
              Daily NAV time-series evaluated through market close.
            </p>
          </div>

          <div className="panel-inset p-3">
            <span className="def-label">NAV Knowledge Cutoff</span>
            <span className="def-value block truncate" title={profile?.context?.knowledgeCutoffTime || activeConfig.knowledgeCutoffTime}>
              {profile?.context?.knowledgeCutoffTime
                ? formatDateTime(profile.context.knowledgeCutoffTime)
                : formatDateTime(activeConfig.knowledgeCutoffTime)}
            </span>
            <p className="mt-1.5 text-[12px] leading-[1.5] text-text-secondary">
              Strict exclusion of any NAV revisions published after cutoff.
            </p>
          </div>

          <div className="panel-inset p-3">
            <span className="def-label">Calculated Horizon Window</span>
            <span className="def-value block truncate text-primary font-mono text-[13px]">
              {calculatedStartDate} &rarr; {profile?.context?.asOfDate ? String(profile.context.asOfDate) : activeConfig.asOfDate}
            </span>
            <p className="mt-1.5 text-[12px] leading-[1.5] text-text-secondary">
              {selectedHorizon} lookback window ({selectedHorizon === "10Y" ? "120M" : selectedHorizon === "5Y" ? "60M" : "36M"}).
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

        {/* MULTI-HORIZON PERFORMANCE PERIODS & BOUNDARY OBSERVATIONS */}
        {selectedOptionId && (
          <div className="mt-6">
            <PerformancePeriodSelector
              schemeOptionId={selectedOptionId}
              asOfDate={activeConfig.asOfDate}
              knowledgeCutoffTime={activeConfig.knowledgeCutoffTime}
            />
          </div>
        )}

        {/* AUTHENTIC HISTORICAL OBSERVATIONS & DRAWDOWN CHART */}
        {selectedOptionId && (
          <div className="mt-6">
            <HistoricalObservationChart
              schemeOptionId={selectedOptionId}
              schemeName={scheme?.name || "Fund"}
              horizon={selectedHorizon}
              asOfDate={activeConfig.asOfDate}
              knowledgeCutoffTime={activeConfig.knowledgeCutoffTime}
            />
          </div>
        )}
      </section>

      {/* GROUNDED AI INTERPRETATION LAYER */}
      {selectedOptionId && (
        <section className="mb-10 scroll-mt-20" id="ai-interpretation">
          {unavailableClaimNotice && (
            <div
              data-testid="fund-unavailable-claim-warning"
              className="mb-4 rounded-sm border border-amber-500/40 bg-amber-500/10 p-3.5 text-text-primary"
            >
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-start gap-2.5">
                  <span className="text-amber-400 font-bold text-[14px]">⚠️</span>
                  <div>
                    <p className="text-[12.5px] font-semibold text-amber-300">
                      Metric Unavailable in Current Horizon Context
                    </p>
                    <p className="mt-0.5 text-[12px] text-text-secondary leading-[1.5]">
                      {unavailableClaimNotice}
                    </p>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => setUnavailableClaimNotice(null)}
                  className="text-text-muted hover:text-text-primary text-[12px] font-mono shrink-0 px-2 py-0.5 border border-border rounded"
                >
                  Dismiss
                </button>
              </div>
            </div>
          )}
          <div className="flex justify-end mb-2">
            <button
              type="button"
              onClick={() => setShowEvidencePacketModal(true)}
              className="text-xs font-mono text-emerald-400 hover:text-emerald-300 bg-zinc-900 border border-zinc-800 px-3 py-1.5 rounded-lg flex items-center gap-1.5 transition-colors"
            >
              <span>🔬 Canonical Evidence Packet & Methodology Explainer</span>
            </button>
          </div>
          <CanonicalEvidencePacketModal
            schemeOptionId={selectedOptionId}
            schemeName={scheme?.name || "Fund Scheme"}
            isOpen={showEvidencePacketModal}
            onClose={() => setShowEvidencePacketModal(false)}
          />
          <GroundedAiInterpretationPanel
            profile={profile || undefined}
            schemeOptionId={selectedOptionId}
            runId={profile?.provenance?.calculationRunId}
            onMetricClick={(code, claimCtx) => {
              const item = getMetricItem(code);
              if (item) {
                setUnavailableClaimNotice(null);
                setActiveClaimContext(claimCtx || null);
                setExpandedEvidenceMetric(code);
                if (typeof document !== "undefined") {
                  const el = document.getElementById(`metric-${code}`);
                  if (el) {
                    el.scrollIntoView({ behavior: "smooth", block: "center" });
                  }
                }
              } else {
                setActiveClaimContext(claimCtx || null);
                setUnavailableClaimNotice(
                  `Claim references metric "${code}", which is not calculated or available in the current ${selectedHorizon} profile for Option #${selectedOptionId} (Cutoff: ${activeConfig.asOfDate}). Analytical evaluations are strictly bounded to verified observations within the active window.`
                );
              }
            }}
            onQuestionInvestigate={(q) => handleInvestigateQuestion(q)}
          />
        </section>
      )}

      {/* 3. METRIC GROUPS (PROGRESSIVE DISCLOSURE) */}

      {/* DIMENSION 1: RETURN QUALITY */}
      <section className="mb-10 scroll-mt-20" id="return-quality">
        <div className="mb-4">
          <SectionHeading
            ordinal="Dimension 1"
            title="Return Quality & Compound Horizon"
            action={<span className="mono-meta shrink-0">{selectedHorizon} Return Metrics</span>}
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
            defaultGovernanceStatus: "validated",
          })}

          {renderMetricCardWithEvidence("RET-03", {
            name: `${selectedHorizon === "10Y" ? "10-Year" : selectedHorizon === "5Y" ? "5-Year" : "3-Year"} Compound Annual Growth Rate (${selectedHorizon} CAGR)`,
            category: "return",
            categoryLabel: "Annualized Return",
            period: selectedHorizon === "10Y"
              ? "120 Calendar Months (≥ 2,400 trading days)"
              : selectedHorizon === "5Y"
              ? "60 Calendar Months (≥ 1,200 trading days)"
              : "36 Calendar Months (≥ 700 trading days)",
            description: `Annualized compound return evaluated over a continuous ${selectedHorizon === "10Y" ? "120" : selectedHorizon === "5Y" ? "60" : "36"}-month lookback window.`,
            interpretation: "Normalizes cumulative multi-year growth onto an annualized basis using 365.25 calendar days convention.",
            limitation: "Past annualized return does not predict future returns across changing market cycles.",
            formula: "CAGR = (NAV_end / NAV_start)^(365.25 / elapsed_calendar_days) - 1",
            assumptions: [
              { label: "Window", value: `${selectedHorizon === "10Y" ? "120" : selectedHorizon === "5Y" ? "60" : "36"} Calendar Months` },
              { label: "Minimum Days", value: `${selectedHorizon === "10Y" ? "2,400" : selectedHorizon === "5Y" ? "1,200" : "700"} trading days` },
            ],
            canExecute: true,
            investigationQuestion: `Is the ${selectedHorizon} CAGR accompanied by higher downside volatility or drawdown risk?`,
            investigationReason: `Evaluating ${selectedHorizon} annualized return consistency`,
            defaultGovernanceStatus: "candidate",
          })}

          {renderMetricCardWithEvidence("RET-07", {
            name: `${selectedHorizon} Annualized Active Return`,
            category: "return",
            categoryLabel: "Benchmark Outperformance",
            period: `${selectedHorizon === "10Y" ? "120" : selectedHorizon === "5Y" ? "60" : "36"} Calendar Months`,
            description: "Annualized excess geometric return over the category reference benchmark (NIFTY 500 TRI).",
            interpretation: "Measures whether the fund compounded capital ahead of its category benchmark after all fund expenses.",
            limitation: "Candidate methodology. Raw excess return does not adjust for systematic market beta or style factor tilts.",
            formula: `Active Return = ${selectedHorizon} CAGR(Fund) - ${selectedHorizon} CAGR(Benchmark)`,
            assumptions: [
              { label: "Benchmark Proxy", value: "NIFTY 500 Total Return Index (TRI)" },
              { label: "Window", value: `${selectedHorizon === "10Y" ? "120" : selectedHorizon === "5Y" ? "60" : "36"} Calendar Months` },
            ],
            canExecute: false,
            investigationQuestion: "Is benchmark active outperformance driven by security selection skill or market beta tilt?",
            investigationReason: `Evaluating ${selectedHorizon} Active Return vs benchmark`,
            defaultGovernanceStatus: "candidate",
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
            name="5-Year Compound Annual Growth Rate (5Y CAGR Primitive)"
            category="return"
            categoryLabel="60M Lookback"
            governanceStatus={selectedHorizon === "5Y" ? "candidate" : "unimplemented"}
            period="60 Calendar Months"
            description="Full-cycle 5-year annualized return primitive."
            disabledReason={selectedHorizon === "5Y" ? undefined : "Switch active horizon to 5Y to target full-cycle CAGR."}
          />
        </div>
      </section>

      {/* ROLLING RETURN & OUTPERFORMANCE CONSISTENCY SLICE (§RET-05 / §RET-06) */}
      {selectedOptionId && (
        <div className="mb-10 space-y-6">
          <RollingConsistencyView
            schemeOptionId={selectedOptionId}
            schemeCode={scheme.code}
            asOfDate={activeConfig.asOfDate}
            knowledgeCutoffTime={activeConfig.knowledgeCutoffTime}
          />
          <RollingReturnAnalysisView
            data={rollingReport}
            selectedHorizon={rollingAnalysisHorizon}
            onHorizonChange={setRollingAnalysisHorizon}
            isLoading={rollingReportLoading}
          />
        </div>
      )}

      {/* DIMENSION 2: TOTAL & DOWNSIDE RISK */}
      <section className="mb-10 scroll-mt-20" id="risk">
        <div className="mb-4">
          <SectionHeading
            ordinal="Dimension 2"
            title="Total & Downside Risk"
            action={<span className="mono-meta shrink-0">{selectedHorizon} Risk Metrics</span>}
          />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {renderMetricCardWithEvidence("RSK-01", {
            name: `Annualized Volatility (${selectedHorizon})`,
            category: "risk",
            categoryLabel: "Total Dispersion",
            period: selectedHorizon === "10Y"
              ? "120 Calendar Months (≥ 2,400 trading days)"
              : selectedHorizon === "5Y"
              ? "60 Calendar Months (≥ 1,200 trading days)"
              : "36 Calendar Months (≥ 700 trading days)",
            description: "Annualized sample standard deviation of daily log/discrete returns.",
            interpretation: `Measures the total dispersion of daily returns around the sample mean over ${selectedHorizon}.`,
            limitation: "Treats upside gains and downside drawdowns with equal penalty.",
            formula: "σ_ann = √252 × √( ∑(r_t - r̄)² / (N - 1) )",
            assumptions: [
              { label: "Annualizer", value: "√252 (Trading Days)" },
              { label: "Denominator", value: "N - 1 (Unbiased Sample)" },
            ],
            canExecute: true,
            investigationQuestion: "Is annualized volatility driven by transient market regime shocks or persistent fund beta?",
            investigationReason: `Evaluating ${selectedHorizon} total risk dispersion`,
            defaultGovernanceStatus: "candidate",
          })}

          {renderMetricCardWithEvidence("RSK-02", {
            name: `Downside Semideviation (${selectedHorizon})`,
            category: "risk",
            categoryLabel: "Asymmetric Risk",
            period: selectedHorizon === "10Y"
              ? "120 Calendar Months (≥ 2,400 trading days)"
              : selectedHorizon === "5Y"
              ? "60 Calendar Months (≥ 1,200 trading days)"
              : "36 Calendar Months (≥ 700 trading days)",
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
            investigationReason: `Evaluating ${selectedHorizon} downside semideviation`,
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
            action={<span className="mono-meta shrink-0">{selectedHorizon} Drawdown Metrics</span>}
          />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {renderMetricCardWithEvidence("RSK-03", {
            name: `Maximum Drawdown (${selectedHorizon})`,
            category: "drawdown",
            categoryLabel: "Capital Decline",
            period: `${selectedHorizon === "10Y" ? "120" : selectedHorizon === "5Y" ? "60" : "36"} Calendar Months`,
            description: `Worst peak-to-trough percentage decline observed over the ${selectedHorizon} horizon.`,
            interpretation: "Quantifies the maximum capital loss an investor experienced from the highest historical peak.",
            limitation: "Measures historical worst-case decline only; does not place an upper bound on future drawdowns.",
            formula: "MDD = min_t ( NAV_t / max_{s≤t}(NAV_s) - 1 )",
            assumptions: [
              { label: "Sign Convention", value: "Signed Negative Decimal" },
              { label: "Peak Tracking", value: "Running Cumulative Max" },
            ],
            canExecute: true,
            investigationQuestion: "Did the maximum drawdown recover faster or slower than the category benchmark?",
            investigationReason: `Evaluating ${selectedHorizon} maximum drawdown`,
            defaultGovernanceStatus: "candidate",
          })}

          {renderMetricCardWithEvidence("RSK-04", {
            name: `Maximum Drawdown Duration (${selectedHorizon})`,
            category: "drawdown",
            categoryLabel: "Recovery Horizon",
            period: `${selectedHorizon === "10Y" ? "120" : selectedHorizon === "5Y" ? "60" : "36"} Calendar Months`,
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
            name: `Ulcer Index (${selectedHorizon})`,
            category: "drawdown",
            categoryLabel: "Stress Metric",
            period: `${selectedHorizon === "10Y" ? "120" : selectedHorizon === "5Y" ? "60" : "36"} Calendar Months`,
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
            investigationReason: `Evaluating ${selectedHorizon} Ulcer Index path stress`,
            defaultGovernanceStatus: "candidate",
          })}
        </div>
      </section>

      {/* HISTORICAL STRESS VIEW SLICE */}
      {selectedOptionId && (
        <div className="mb-10 space-y-6">
          <HistoricalStressView
            schemeOptionId={selectedOptionId}
            schemeCode={scheme.code}
            asOfDate={activeConfig.asOfDate}
            knowledgeCutoffTime={activeConfig.knowledgeCutoffTime}
          />
          <DrawdownEpisodeView data={drawdownEpisodes} isLoading={drawdownLoading} />
        </div>
      )}

      {/* DIMENSION 4: TAIL RISK */}
      <section className="mb-10 scroll-mt-20" id="tail-risk">
        <div className="mb-4">
          <SectionHeading
            ordinal="Dimension 4"
            title="Tail Risk & Extreme Losses"
            action={<span className="mono-meta shrink-0">{selectedHorizon} Tail Risk Metrics</span>}
          />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {renderMetricCardWithEvidence("RSK-06", {
            name: `Historical Value at Risk (VaR 95% · ${selectedHorizon})`,
            category: "tail",
            categoryLabel: "Tail Loss Boundary",
            period: `${selectedHorizon === "10Y" ? "120" : selectedHorizon === "5Y" ? "60" : "36"} Calendar Months`,
            description: `5th percentile empirical daily return threshold over the ${selectedHorizon} lookback window.`,
            interpretation: "On 95% of trading days, daily loss did not exceed this threshold in historical data.",
            limitation: "Candidate methodology. Non-parametric empirical quantile; does not describe severity of losses in the remaining 5% tail.",
            formula: "VaR_95 = -Quantile_0.05( { r_t } )",
            assumptions: [
              { label: "Quantile Convention", value: "Type 7 Linear Interpolation" },
              { label: "Confidence Level", value: "95.0% One-Tailed" },
            ],
            canExecute: false,
            investigationQuestion: "Does the 95% VaR threshold reflect severe liquidity shocks or typical trading volatility?",
            investigationReason: `Evaluating historical ${selectedHorizon} 95% Value at Risk`,
            defaultGovernanceStatus: "candidate",
          })}

          {renderMetricCardWithEvidence("RSK-07", {
            name: `Expected Shortfall (CVaR 95% · ${selectedHorizon})`,
            category: "tail",
            categoryLabel: "Conditional Tail Expectation",
            period: `${selectedHorizon === "10Y" ? "120" : selectedHorizon === "5Y" ? "60" : "36"} Calendar Months`,
            description: "Conditional mean of daily returns strictly below the 5th percentile VaR cutoff.",
            interpretation: "Measures the expected daily loss when an extreme tail loss event actually occurs.",
            limitation: "Candidate methodology. Sub-sample average subject to small-sample estimation variance during calm market regimes.",
            formula: "ES_95 = -(1 / |Tail|) × ∑_{r_t < -VaR_95} r_t",
            assumptions: [
              { label: "Coherence", value: "Sub-additive Coherent Measure" },
              { label: "Tail Threshold", value: "Empirical 5th Percentile" },
            ],
            canExecute: false,
            investigationQuestion: "How deep is the expected loss beyond the VaR threshold during tail stress events?",
            investigationReason: `Evaluating Conditional VaR (Expected Shortfall · ${selectedHorizon})`,
            defaultGovernanceStatus: "candidate",
          })}
        </div>
      </section>

      {/* DIMENSION 5: RISK-ADJUSTED & MARKET SENSITIVITY */}
      <section className="mb-10 scroll-mt-20" id="risk-adjusted">
        <div className="mb-4">
          <SectionHeading
            ordinal="Dimension 5"
            title="Risk-Adjusted Ratios & Market Sensitivity"
            action={
              <span className="mono-meta shrink-0">
                {selectedHorizon} Risk-Adjusted &amp; Sensitivity Metrics
              </span>
            }
          />
        </div>

        {/* GOVERNANCE RESTRICTION NOTICE FOR 5Y & 10Y RISK-ADJUSTED SUITE */}
        {selectedHorizon !== "3Y" && (
          <div className="mb-6 p-4 rounded-xl border border-amber-500/30 bg-amber-500/5 font-mono text-xs">
            <div className="flex items-start gap-3">
              <span className="text-amber-400 font-bold px-2.5 py-1 rounded bg-amber-500/10 border border-amber-500/20 shrink-0 text-[11px]">
                GOVERNANCE RESTRICTION · {selectedHorizon}
              </span>
              <div className="text-text-cream-body space-y-1 font-sans text-[12.5px]">
                <p className="font-semibold text-text-cream-bright">
                  Multi-Year Risk-Free Compounding &amp; Multi-Cycle CAPM Stationarity Restricted
                </p>
                <p className="text-text-cream-muted text-[11.5px] leading-relaxed">
                  Under frozen Phase 2H methodology, risk-adjusted metrics (Sharpe, Sortino, Treynor, Information Ratio, and Jensen&apos;s Alpha) are validated exclusively for the canonical 36-month horizon. Extending 91-day T-Bill daily compounding and single-index CAPM beta across 5-year and 10-year macroeconomic regimes introduces term-structure and stationarity distortions that have not received quantitative governance authorization.
                </p>
              </div>
            </div>
          </div>
        )}

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {renderMetricCardWithEvidence("RAT-01", {
            name: `Sharpe Ratio (${selectedHorizon}${selectedHorizon !== "3Y" ? " · Restricted" : ""})`,
            category: "risk_adjusted",
            categoryLabel: "Excess Return per Unit Total Risk",
            period: `${selectedHorizon === "10Y" ? "120" : selectedHorizon === "5Y" ? "60" : "36"} Calendar Months`,
            description: "Ratio of annualized excess return above the risk-free rate to annualized return volatility.",
            interpretation: "Evaluates how effectively the fund compensated for total standard deviation above 91-day T-bills.",
            limitation: selectedHorizon !== "3Y"
              ? `Governance restriction: Multi-year FBIL 91D T-Bill daily compounding is not authorized for ${selectedHorizon} lookbacks under Phase 2H.`
              : "Candidate methodology. Assumes symmetrical return distributions; penalizes large upside gains.",
            formula: "Sharpe = (R_fund - R_f) / σ_ann",
            assumptions: [
              { label: "Risk-Free Benchmark", value: "FBIL 91D T-Bill" },
              { label: "Compounding", value: "Geometric Annualized" },
            ],
            canExecute: false,
            investigationQuestion: "Is the Sharpe ratio driven primarily by market beta or genuine active alpha?",
            investigationReason: `Evaluating Sharpe ratio risk compensation (${selectedHorizon})`,
            defaultGovernanceStatus: selectedHorizon === "3Y" ? "candidate" : "restricted",
          })}

          {renderMetricCardWithEvidence("RAT-02", {
            name: `Sortino Ratio (${selectedHorizon}${selectedHorizon !== "3Y" ? " · Restricted" : ""})`,
            category: "risk_adjusted",
            categoryLabel: "Excess Return per Unit Downside Risk",
            period: `${selectedHorizon === "10Y" ? "120" : selectedHorizon === "5Y" ? "60" : "36"} Calendar Months`,
            description: "Ratio of annualized excess return above the risk-free rate to downside semideviation below zero.",
            interpretation: "Measures reward earned per unit of harmful downside volatility without penalizing upside gains.",
            limitation: selectedHorizon !== "3Y"
              ? `Governance restriction: Multi-year FBIL 91D T-Bill daily compounding is not authorized for ${selectedHorizon} lookbacks under Phase 2H.`
              : "Candidate methodology. Minimum Acceptable Return (MAR) convention set to 0.0%.",
            formula: "Sortino = (R_fund - R_f) / Downside_Semideviation",
            assumptions: [
              { label: "MAR Threshold", value: "0.0%" },
              { label: "Risk-Free Benchmark", value: "FBIL 91D T-Bill" },
            ],
            canExecute: false,
            investigationQuestion: "Does the Sortino ratio indicate superior downside risk efficiency vs Sharpe?",
            investigationReason: `Evaluating Sortino downside ratio (${selectedHorizon})`,
            defaultGovernanceStatus: selectedHorizon === "3Y" ? "candidate" : "restricted",
          })}

          {renderMetricCardWithEvidence("RAT-03", {
            name: `Treynor Ratio (${selectedHorizon}${selectedHorizon !== "3Y" ? " · Restricted" : ""})`,
            category: "risk_adjusted",
            categoryLabel: "Excess Return per Unit Systematic Risk",
            period: `${selectedHorizon === "10Y" ? "120" : selectedHorizon === "5Y" ? "60" : "36"} Calendar Months`,
            description: "Ratio of annualized excess return above the risk-free rate to systematic equity Beta.",
            interpretation: "Measures excess reward earned per unit of unavoidable broad-market systematic risk.",
            limitation: selectedHorizon !== "3Y"
              ? `Governance restriction: Multi-year CAPM stationarity and risk-free compounding are not authorized for ${selectedHorizon} under Phase 2H.`
              : "Candidate methodology. Only meaningful for diversified portfolios with high benchmark correlation.",
            formula: "Treynor = (R_fund - R_f) / Beta",
            assumptions: [
              { label: "Systematic Beta", value: `${selectedHorizon} NIFTY 500 TRI Beta` },
              { label: "Denominator Rule", value: "Beta > 0 required" },
            ],
            canExecute: false,
            investigationQuestion: "Does the excess return compensate for systematic benchmark volatility exposure?",
            investigationReason: `Evaluating Treynor systematic return ratio (${selectedHorizon})`,
            defaultGovernanceStatus: selectedHorizon === "3Y" ? "candidate" : "restricted",
          })}

          {renderMetricCardWithEvidence("RAT-04", {
            name: `Information Ratio (${selectedHorizon}${selectedHorizon !== "3Y" ? " · Restricted" : ""})`,
            category: "risk_adjusted",
            categoryLabel: "Active Return Consistency",
            period: `${selectedHorizon === "10Y" ? "120" : selectedHorizon === "5Y" ? "60" : "36"} Calendar Months`,
            description: `Ratio of ${selectedHorizon} annualized active return to annualized benchmark tracking error.`,
            interpretation: "Evaluates the consistency of manager value addition relative to benchmark risk taken.",
            limitation: selectedHorizon !== "3Y"
              ? `Governance restriction: Multi-cycle tracking error stability is not authorized for ${selectedHorizon} under Phase 2H.`
              : "Candidate methodology. Undefined when tracking error is zero; penalizes high active risk.",
            formula: "Information Ratio = Active_Return / Tracking_Error",
            assumptions: [
              { label: "Benchmark", value: "Category Reference Benchmark (NIFTY 500 TRI)" },
              { label: "Denominator Rule", value: "Tracking Error > 0" },
            ],
            canExecute: false,
            investigationQuestion: "Is the Information Ratio supported by consistent active alpha or erratic divergence?",
            investigationReason: `Evaluating Information Ratio consistency (${selectedHorizon})`,
            defaultGovernanceStatus: selectedHorizon === "3Y" ? "candidate" : "restricted",
          })}

          {renderMetricCardWithEvidence("MKT-01", {
            name: `Portfolio Beta (${selectedHorizon})`,
            category: "risk_adjusted",
            categoryLabel: "Systematic Sensitivity",
            period: `${selectedHorizon === "10Y" ? "120" : selectedHorizon === "5Y" ? "60" : "36"} Calendar Months`,
            description: "Slope coefficient from linear regression of daily fund returns against benchmark returns.",
            interpretation: "Measures portfolio sensitivity to broad market movements (Beta > 1 implies amplified swings).",
            limitation: "Candidate methodology. Linear static measure; beta fluctuates across bull, bear, and crisis regimes.",
            formula: "Beta = Cov(r_fund, r_bench) / Var(r_bench)",
            assumptions: [
              { label: "Benchmark", value: "Category Reference Benchmark (NIFTY 500 TRI)" },
              { label: "Regression", value: "Ordinary Least Squares (OLS)" },
            ],
            canExecute: false,
            investigationQuestion: "Does portfolio beta remain stable across market drawdowns versus rallies?",
            investigationReason: `Evaluating systematic portfolio beta (${selectedHorizon})`,
            defaultGovernanceStatus: "candidate",
          })}

          {renderMetricCardWithEvidence("MKT-02", {
            name: `Downside Beta (${selectedHorizon})`,
            category: "risk_adjusted",
            categoryLabel: "Asymmetric Sensitivity",
            period: `${selectedHorizon === "10Y" ? "120" : selectedHorizon === "5Y" ? "60" : "36"} Calendar Months (Benchmark < 0)`,
            description: "Beta calculated conditioning exclusively on trading days when benchmark return was negative.",
            interpretation: "Identifies whether fund sensitivity increases during market sell-offs vs up-trending regimes.",
            limitation: "Candidate methodology. Requires sufficient negative benchmark trading days (minimum 100 days threshold).",
            formula: "Beta_down = Cov(r_fund, r_bench | r_bench < 0) / Var(r_bench | r_bench < 0)",
            assumptions: [
              { label: "Conditioning", value: "r_benchmark < 0.0" },
              { label: "Min Observations", value: "100 downside market days" },
            ],
            canExecute: false,
            investigationQuestion: "Is downside beta lower than overall beta, proving defensive market cushioning?",
            investigationReason: `Evaluating asymmetric downside beta (${selectedHorizon})`,
            defaultGovernanceStatus: "candidate",
          })}

          {renderMetricCardWithEvidence("REL-01", {
            name: `Tracking Error (${selectedHorizon})`,
            category: "risk_adjusted",
            categoryLabel: "Active Risk & Benchmark Divergence",
            period: `${selectedHorizon === "10Y" ? "120" : selectedHorizon === "5Y" ? "60" : "36"} Calendar Months`,
            description: "Annualized standard deviation of daily active return differences between fund and benchmark.",
            interpretation: "Measures the degree of deviation from the benchmark; reflects the magnitude of active risk bets.",
            limitation: "Candidate methodology. Reflects deviation magnitude only, regardless of positive or negative outcome.",
            formula: "Tracking Error = Stdev_sample( r_fund - r_bench ) × √252",
            assumptions: [
              { label: "Annualization Factor", value: "√252 trading days" },
              { label: "Benchmark", value: "Category Reference Benchmark (NIFTY 500 TRI)" },
            ],
            canExecute: false,
            investigationQuestion: "Does tracking error reflect deliberate security selection or passive index drift?",
            investigationReason: `Evaluating Tracking Error active risk (${selectedHorizon})`,
            defaultGovernanceStatus: "candidate",
          })}

          {renderMetricCardWithEvidence("REL-02", {
            name: `Jensen's Alpha (${selectedHorizon}${selectedHorizon !== "3Y" ? " · Restricted" : ""})`,
            category: "risk_adjusted",
            categoryLabel: "Risk-Adjusted Active Alpha",
            period: `${selectedHorizon === "10Y" ? "120" : selectedHorizon === "5Y" ? "60" : "36"} Calendar Months`,
            description: "Annualized excess return earned beyond what is predicted by Capital Asset Pricing Model market beta.",
            interpretation: "Measures pure active value creation after controlling for systematic market risk exposure.",
            limitation: selectedHorizon !== "3Y"
              ? `Governance restriction: Multi-year CAPM single-index stationarity and risk-free compounding are not authorized for ${selectedHorizon} under Phase 2H.`
              : "Candidate methodology. Single-index CAPM model does not control for size, value, or momentum factor tilts.",
            formula: "Alpha = (R_p - R_f) - Beta × (R_b - R_f)",
            assumptions: [
              { label: "Model", value: "Single-Index CAPM OLS" },
              { label: "Risk-Free Benchmark", value: "FBIL 91D T-Bill" },
            ],
            canExecute: false,
            investigationQuestion: "Is Jensen's Alpha statistically significant, or driven by cyclical factor style tailwinds?",
            investigationReason: `Evaluating Jensen's Alpha risk-adjusted active performance (${selectedHorizon})`,
            defaultGovernanceStatus: selectedHorizon === "3Y" ? "candidate" : "restricted",
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
          <div className="space-y-6">
            <PortfolioHoldingsView schemeOptionId={selectedOptionId} schemeCode={scheme.code} />
            <HoldingsConstructionSection data={holdingsConstruction} isLoading={holdingsLoading} />
          </div>
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

      {scheme && (
        <FundMonitoringSection
          schemeId={scheme.id}
          schemeOptionId={selectedOptionId}
          schemeName={scheme.name}
          schemeCode={scheme.code}
        />
      )}

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

      {activeFundForNote && (
        <FundResearchBriefModal
          isOpen={isBriefModalOpen}
          onClose={() => {
            setIsBriefModalOpen(false);
            setBriefInitialSection("A");
          }}
          fund={activeFundForNote}
          existingProfile={profile}
          initialSection={briefInitialSection}
          onEditNote={(targetFund) => {
            setIsBriefModalOpen(false);
            if (targetFund) {
              setInvestigationPrefill({
                investigationQuestion: targetFund.investigationQuestion || undefined,
                reasonSaved: targetFund.reasonSaved || undefined,
                nextStep: targetFund.nextStep || undefined,
              });
            }
            setIsNoteModalOpen(true);
          }}
          onUpdateStatus={async (sId, sOptId, st) => {
            updateInvestigationStatus(sId, sOptId, st);
          }}
        />
      )}

      <MetricDefinitionDrawer
        metric={activeDefinitionMetric}
        isOpen={isDefinitionDrawerOpen}
        onClose={() => setIsDefinitionDrawerOpen(false)}
      />
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
