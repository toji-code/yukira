'use client';

import React, { useState, useEffect } from 'react';
import {
  fetchPortfolioRiskDiversification,
  exportHistoricalStressEvidenceRecord,
  PortfolioRiskDiversificationResponseDto,
  PortfolioFundRiskContributionDto,
  CorrelatedPairDto,
  RiskInvestigationFindingDto,
  PortfolioStressEpisodeDto,
  FundStressContributionDto,
  PortfolioStressEvidenceRecordDto
} from '@/lib/api/portfolio-risk';
import { PortfolioHoldingDto, addPortfolioHolding } from '@/lib/api/portfolio';
import { formatCurrency, formatPercentage } from '@/lib/utils/formatters';
import { PortfolioSharedContextBanner } from './PortfolioSharedContextBanner';

interface PortfolioRiskDiversificationSectionProps {
  holdings: PortfolioHoldingDto[];
  onHoldingUpdated?: () => void;
}

function renderWithholdingBadge(code?: string, explanation?: string | null) {
  if (!code || code === 'NONE') return null;
  return (
    <span
      className="px-2 py-0.5 rounded text-[10px] font-mono font-bold bg-rose-500/15 text-rose-600 dark:text-rose-400 border border-rose-500/30"
      title={explanation || code.replace(/_/g, ' ')}
    >
      Withheld: {code.replace(/_/g, ' ')}
    </span>
  );
}

function renderStressRoleBadge(role?: string) {
  if (!role) return null;
  let color = 'bg-slate-500/15 text-slate-600 dark:text-slate-400 border-slate-500/30';
  if (role === 'POSITIVE_CONTRIBUTOR' || role === 'RESILIENT_CONTRIBUTOR') {
    color = 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-400 border-emerald-500/30';
  } else if (role === 'RESILIENT_LOWER_LOSS_CONTRIBUTOR') {
    color = 'bg-teal-500/15 text-teal-600 dark:text-teal-400 border-teal-500/30';
  } else if (role === 'PRIMARY_DETRACTOR') {
    color = 'bg-rose-500/15 text-rose-600 dark:text-rose-400 border-rose-500/30';
  } else if (role === 'MODERATE_DETRACTOR') {
    color = 'bg-amber-500/15 text-amber-600 dark:text-amber-400 border-amber-500/30';
  } else if (role === 'NEUTRAL_CONTRIBUTOR') {
    color = 'bg-slate-500/15 text-slate-600 dark:text-slate-400 border-slate-500/30';
  } else if (role === 'LIMITED_EVIDENCE') {
    color = 'bg-orange-500/15 text-orange-600 dark:text-orange-400 border-orange-500/30';
  } else if (role === 'INSUFFICIENT_EVIDENCE' || role === 'NUMERICALLY_INVALID' || role === 'UNAVAILABLE') {
    color = 'bg-slate-500/15 text-slate-500 dark:text-slate-400 border-slate-500/30';
  }
  return (
    <span className={`px-2 py-0.5 rounded text-[10px] font-mono font-semibold border ${color}`} title={role.replace(/_/g, ' ')}>
      {role.replace(/_/g, ' ')}
    </span>
  );
}

function renderStandaloneRoleBadge(role?: string) {
  if (!role) return null;
  let color = 'bg-slate-500/15 text-slate-600 dark:text-slate-400 border-slate-500/30';
  if (role === 'POSITIVE_RETURN_ABOVE_PROXY') {
    color = 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-400 border-emerald-500/30';
  } else if (role === 'POSITIVE_RETURN_BELOW_PROXY') {
    color = 'bg-teal-500/15 text-teal-600 dark:text-teal-400 border-teal-500/30';
  } else if (role === 'LOWER_LOSS_BELOW_PROXY') {
    color = 'bg-blue-500/15 text-blue-600 dark:text-blue-400 border-blue-500/30';
  } else if (role === 'HIGHER_LOSS_ABOVE_PROXY') {
    color = 'bg-amber-500/15 text-amber-600 dark:text-amber-400 border-amber-500/30';
  } else if (role === 'WORST_STANDALONE_PERFORMER') {
    color = 'bg-rose-500/15 text-rose-600 dark:text-rose-400 border-rose-500/30';
  } else if (role === 'NEUTRAL_STANDALONE_PERFORMER') {
    color = 'bg-slate-500/15 text-slate-600 dark:text-slate-400 border-slate-500/30';
  } else if (role === 'LIMITED_EVIDENCE') {
    color = 'bg-orange-500/15 text-orange-600 dark:text-orange-400 border-orange-500/30';
  } else if (role === 'INSUFFICIENT_EVIDENCE' || role === 'NUMERICALLY_INVALID' || role === 'UNAVAILABLE') {
    color = 'bg-slate-500/15 text-slate-500 dark:text-slate-400 border-slate-500/30';
  }
  return (
    <span className={`px-2 py-0.5 rounded text-[10px] font-mono font-semibold border ${color}`} title={role.replace(/_/g, ' ')}>
      {role.replace(/_/g, ' ')}
    </span>
  );
}

function renderLifecycleBadge(state?: string) {
  if (!state) return null;
  let color = 'bg-slate-500/15 text-slate-600 dark:text-slate-400 border-slate-500/30';
  if (state === 'RECOVERED') {
    color = 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-400 border-emerald-500/30';
  } else if (state === 'ONGOING_RECOVERY') {
    color = 'bg-blue-500/15 text-blue-600 dark:text-blue-400 border-blue-500/30';
  } else if (state === 'ACTIVE_DECLINE') {
    color = 'bg-rose-500/15 text-rose-600 dark:text-rose-400 border-rose-500/30';
  } else if (state === 'UNRECOVERED_CUTOFF_REACHED') {
    color = 'bg-amber-500/15 text-amber-600 dark:text-amber-400 border-amber-500/30';
  } else if (state === 'RECOVERY_UNVERIFIABLE_DATA_GAP') {
    color = 'bg-orange-500/15 text-orange-600 dark:text-orange-400 border-orange-500/30';
  }
  return (
    <span className={`px-2 py-0.5 rounded text-[10px] font-mono font-semibold border ${color}`} title={state.replace(/_/g, ' ')}>
      {state.replace(/_/g, ' ')}
    </span>
  );
}

function renderEligibilityBadge(outcome?: string) {
  if (!outcome) return null;
  let color = 'bg-slate-500/15 text-slate-600 dark:text-slate-400 border-slate-500/30';
  if (outcome === 'ELIGIBLE_COMPLETE_WINDOW') {
    color = 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-400 border-emerald-500/30';
  } else if (outcome === 'ELIGIBLE_COMMON_DATE_SUBSET') {
    color = 'bg-blue-500/15 text-blue-600 dark:text-blue-400 border-blue-500/30';
  } else if (outcome === 'WITHHELD_INCOMPLETE_CONSTITUENT_HISTORY') {
    color = 'bg-orange-500/15 text-orange-600 dark:text-orange-400 border-orange-500/30';
  } else if (outcome === 'WITHHELD_INSUFFICIENT_OBSERVATIONS') {
    color = 'bg-amber-500/15 text-amber-600 dark:text-amber-400 border-amber-500/30';
  } else if (outcome === 'WITHHELD_NUMERICALLY_INVALID') {
    color = 'bg-rose-500/15 text-rose-600 dark:text-rose-400 border-rose-500/30';
  } else if (outcome === 'UNAVAILABLE_SOURCE_DATA') {
    color = 'bg-slate-500/15 text-slate-500 dark:text-slate-400 border-slate-500/30';
  }
  return (
    <span className={`px-2 py-0.5 rounded text-[10px] font-mono font-semibold border ${color}`} title={outcome.replace(/_/g, ' ')}>
      {outcome.replace(/_/g, ' ')}
    </span>
  );
}

function renderRecoveryComparisonBadge(comp?: string) {
  if (!comp) return null;
  let color = 'bg-slate-500/15 text-slate-600 dark:text-slate-400 border-slate-500/30';
  if (comp === 'BOTH_RECOVERED') {
    color = 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-400 border-emerald-500/30';
  } else if (comp === 'BENCHMARK_RECOVERED_PORTFOLIO_LAGGING') {
    color = 'bg-amber-500/15 text-amber-600 dark:text-amber-400 border-amber-500/30';
  } else if (comp === 'PORTFOLIO_RECOVERED_BENCHMARK_LAGGING') {
    color = 'bg-teal-500/15 text-teal-600 dark:text-teal-400 border-teal-500/30';
  } else if (comp === 'NEITHER_RECOVERED') {
    color = 'bg-blue-500/15 text-blue-600 dark:text-blue-400 border-blue-500/30';
  } else if (comp === 'BENCHMARK_UNRECOVERED_PORTFOLIO_INCOMPLETE' || comp === 'INSUFFICIENT_DATA') {
    color = 'bg-orange-500/15 text-orange-600 dark:text-orange-400 border-orange-500/30';
  }
  return (
    <span className={`px-2 py-0.5 rounded text-[10px] font-mono font-semibold border ${color}`}>
      {comp.replace(/_/g, ' ')}
    </span>
  );
}

function renderEvidenceBadge(state?: string) {
  if (!state) return null;
  let color = 'bg-slate-500/15 text-slate-600 dark:text-slate-400 border-slate-500/30';
  if (state === 'SUFFICIENT_EVIDENCE' || state === 'ALIGNED' || state === 'SUITABLE' || state === 'DIRECTLY_COMPARABLE' || state === 'AVAILABLE' || state === 'NO_MATERIAL_DIFFERENCE_UNDER_CANDIDATE_RULE' || state === 'NO_MATERIAL_SHIFT_UNDER_CANDIDATE_RULE') {
    color = 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-400 border-emerald-500/30';
  } else if (state === 'LIMITED_EVIDENCE' || state === 'PARTIALLY_ALIGNED' || state === 'CONDITIONALLY_SUITABLE' || state === 'CONDITIONALLY_COMPARABLE' || state === 'LIMITED_COMPARABILITY' || state === 'MATERIAL_DIFFERENCE_OBSERVED' || state === 'MATERIAL_SHIFT_OBSERVED') {
    color = 'bg-amber-500/15 text-amber-600 dark:text-amber-400 border-amber-500/30';
  } else if (state === 'INSUFFICIENT_EVIDENCE' || state === 'INSUFFICIENT_HISTORY') {
    color = 'bg-orange-500/15 text-orange-600 dark:text-orange-400 border-orange-500/30';
  } else if (state === 'NUMERICALLY_INVALID' || state === 'UNSUITABLE' || state === 'NOT_DIRECTLY_COMPARABLE') {
    color = 'bg-rose-500/15 text-rose-600 dark:text-rose-400 border-rose-500/30';
  } else if (state === 'NEGATIVE_CONTRIBUTOR') {
    color = 'bg-purple-500/15 text-purple-600 dark:text-purple-400 border-purple-500/30';
  } else if (state === 'NEGLIGIBLE_NEGATIVE_CONTRIBUTION') {
    color = 'bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 border-indigo-500/30';
  } else if (state === 'RISK_DRIVER') {
    color = 'bg-rose-500/15 text-rose-600 dark:text-rose-400 border-rose-500/30';
  } else if (state === 'BELOW_WEIGHT_CONTRIBUTION' || state === 'RISK_DAMPENER') {
    color = 'bg-blue-500/15 text-blue-600 dark:text-blue-400 border-blue-500/30';
  } else if (state === 'PROPORTIONAL_CONTRIBUTION') {
    color = 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-400 border-emerald-500/30';
  } else if (state === 'ABOVE_WEIGHT_CONTRIBUTION') {
    color = 'bg-amber-500/15 text-amber-600 dark:text-amber-400 border-amber-500/30';
  } else if (state === 'ZERO_WEIGHT_POSITION') {
    color = 'bg-slate-500/15 text-slate-600 dark:text-slate-400 border-slate-500/30';
  } else if (state === 'NEGLIGIBLE_WEIGHT_POSITION') {
    color = 'bg-cyan-500/15 text-cyan-600 dark:text-cyan-400 border-cyan-500/30';
  } else if (state === 'ALLOCATION_CONTRIBUTION_MISMATCH') {
    color = 'bg-orange-500/15 text-orange-600 dark:text-orange-400 border-orange-500/30';
  }
  return (
    <span className={`px-2 py-0.5 rounded text-[10px] font-mono font-semibold border ${color}`}>
      {state.replace(/_/g, ' ')}
    </span>
  );
}

function getRiskContributionLabel(classification?: string): string {
  switch (classification) {
    case 'ZERO_WEIGHT_POSITION':
      return 'Zero-weight position';
    case 'NEGLIGIBLE_WEIGHT_POSITION':
      return 'Near-zero allocation position';
    case 'ALLOCATION_CONTRIBUTION_MISMATCH':
      return 'Allocation-contribution mismatch';
    case 'NEGATIVE_CONTRIBUTOR':
      return 'Negative historical contribution';
    case 'NEGLIGIBLE_NEGATIVE_CONTRIBUTION':
      return 'Negligible negative contribution';
    case 'BELOW_WEIGHT_CONTRIBUTION':
      return 'Below allocation-weight contribution';
    case 'PROPORTIONAL_CONTRIBUTION':
      return 'Proportional to allocation';
    case 'ABOVE_WEIGHT_CONTRIBUTION':
      return 'Above allocation-weight contribution';
    case 'RISK_DRIVER':
      return 'Disproportionate risk contributor';
    case 'UNRELIABLE_ATTRIBUTION':
      return 'Unreliable attribution';
    case 'UNCLASSIFIED':
      return 'Unclassified';
    case 'RISK_DAMPENER':
      return 'Below allocation-weight contribution';
    default:
      return classification ? classification.replace(/_/g, ' ') : 'Unclassified';
  }
}

function getRiskContributionBadgeColor(classification?: string): string {
  switch (classification) {
    case 'ZERO_WEIGHT_POSITION':
      return 'bg-slate-500/15 text-slate-600 dark:text-slate-400 border-slate-500/30';
    case 'NEGLIGIBLE_WEIGHT_POSITION':
      return 'bg-cyan-500/15 text-cyan-600 dark:text-cyan-400 border-cyan-500/30';
    case 'ALLOCATION_CONTRIBUTION_MISMATCH':
      return 'bg-orange-500/15 text-orange-600 dark:text-orange-400 border-orange-500/30';
    case 'NEGATIVE_CONTRIBUTOR':
      return 'bg-purple-500/15 text-purple-600 dark:text-purple-400 border-purple-500/30';
    case 'NEGLIGIBLE_NEGATIVE_CONTRIBUTION':
      return 'bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 border-indigo-500/30';
    case 'BELOW_WEIGHT_CONTRIBUTION':
    case 'RISK_DAMPENER':
      return 'bg-blue-500/15 text-blue-600 dark:text-blue-400 border-blue-500/30';
    case 'PROPORTIONAL_CONTRIBUTION':
      return 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-400 border-emerald-500/30';
    case 'ABOVE_WEIGHT_CONTRIBUTION':
      return 'bg-amber-500/15 text-amber-600 dark:text-amber-400 border-amber-500/30';
    case 'RISK_DRIVER':
      return 'bg-rose-500/15 text-rose-600 dark:text-rose-400 border-rose-500/30';
    case 'UNRELIABLE_ATTRIBUTION':
      return 'bg-rose-500/15 text-rose-600 dark:text-rose-400 border-rose-500/30';
    case 'UNCLASSIFIED':
    default:
      return 'bg-slate-500/15 text-slate-600 dark:text-slate-400 border-slate-500/30';
  }
}

export function PortfolioRiskDiversificationSection({
  holdings,
  onHoldingUpdated
}: PortfolioRiskDiversificationSectionProps) {
  const [data, setData] = useState<PortfolioRiskDiversificationResponseDto | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'overview' | 'matrix' | 'contributions' | 'diversification' | 'downside' | 'sensitivity' | 'stress' | 'investigation'>('overview');
  const [matrixView, setMatrixView] = useState<'correlation' | 'covariance'>('correlation');
  const [showPerFundCounts, setShowPerFundCounts] = useState<boolean>(false);
  const [selectedStressEpisodeId, setSelectedStressEpisodeId] = useState<string | null>(null);

  // Historical stress filtering, sorting & export state (Tasks 56 & 58)
  const [stressSortBy, setStressSortBy] = useState<'CHRONO_DESC' | 'CHRONO_ASC' | 'SEVERITY_DESC' | 'BENCHMARK_DESC'>('CHRONO_DESC');
  const [stressFilterBy, setStressFilterBy] = useState<'ALL' | 'COMPUTABLE_ONLY' | 'WITHHELD_ONLY' | 'RECOVERED_ONLY' | 'UNRECOVERED_ONLY'>('ALL');
  const [exportingStressEvidence, setExportingStressEvidence] = useState<boolean>(false);

  // Workspace log modal state (Task 44 Research Workspace Continuity)
  const [workspaceFinding, setWorkspaceFinding] = useState<RiskInvestigationFindingDto | null>(null);
  const [selectedHoldingId, setSelectedHoldingId] = useState<number | null>(null);
  const [workspaceQuestion, setWorkspaceQuestion] = useState<string>('');
  const [workspaceEvidence, setWorkspaceEvidence] = useState<string>('');
  const [workspaceNotes, setWorkspaceNotes] = useState<string>('');
  const [workspaceUncertainty, setWorkspaceUncertainty] = useState<string>('');
  const [workspaceNextStep, setWorkspaceNextStep] = useState<string>('');
  const [savingNote, setSavingNote] = useState<boolean>(false);
  const [saveSuccessMsg, setSaveSuccessMsg] = useState<string | null>(null);

  const handleExportStressEvidence = async () => {
    try {
      setExportingStressEvidence(true);
      const evidence = await exportHistoricalStressEvidenceRecord();
      const dataStr = 'data:text/json;charset=utf-8,' + encodeURIComponent(JSON.stringify(evidence, null, 2));
      const downloadAnchor = document.createElement('a');
      downloadAnchor.setAttribute('href', dataStr);
      downloadAnchor.setAttribute('download', `stress-evidence-${evidence.portfolioAllocationSnapshotId || 'snapshot'}.json`);
      document.body.appendChild(downloadAnchor);
      downloadAnchor.click();
      downloadAnchor.remove();
    } catch (err: unknown) {
      alert((err as Error).message || 'Failed to export historical stress evidence');
    } finally {
      setExportingStressEvidence(false);
    }
  };

  const loadRiskData = async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await fetchPortfolioRiskDiversification();
      setData(res);
      if (res?.historicalStressAnalysis?.episodes?.length) {
        setSelectedStressEpisodeId((prev) => prev || res.historicalStressAnalysis!.episodes[0].episodeId);
      }
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to load portfolio risk & diversification analysis');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadRiskData();
  }, [holdings.length]);

  const handleOpenWorkspace = (finding: RiskInvestigationFindingDto, isMonitoringRule: boolean = false) => {
    setWorkspaceFinding(finding);
    setWorkspaceQuestion(finding.investigationQuestion || finding.suggestedInvestigation || finding.headline);
    setWorkspaceEvidence(finding.evidence || '');
    setWorkspaceNotes('');
    setWorkspaceUncertainty('');
    setWorkspaceNextStep(
      isMonitoringRule
        ? `[Monitoring Condition]: Review if risk contribution or drawdown exceeds historical stress episode bounds (${finding.episodeId || 'episode'}).`
        : (finding.actionableWorkspacePrompt || 'Review risk contribution and correlation exposure.')
    );
    if (holdings.length > 0) {
      setSelectedHoldingId(holdings[0].id);
    }
    setSaveSuccessMsg(null);
  };

  const handleSaveWorkspaceNote = async () => {
    if (!selectedHoldingId) return;
    const targetHolding = holdings.find((h) => h.id === selectedHoldingId);
    if (!targetHolding) return;
    try {
      setSavingNote(true);
      let compiledReason = targetHolding.reasonSaved || '';
      const newContextParts: string[] = [];

      // Task 72: Cleanly separate System Evidence Snapshot from Investor-Authored Research
      const systemSnapshotHeader = `[System Evidence Snapshot: Run=${workspaceFinding?.calculationRunId || 'N/A'} Ep=${workspaceFinding?.episodeId || 'N/A'} Fingerprint=${workspaceFinding?.calculationInputFingerprint || 'N/A'}]`;
      newContextParts.push(systemSnapshotHeader);

      if (workspaceEvidence.trim()) {
        newContextParts.push(`[System Evidence Detail: ${workspaceEvidence.trim()}]`);
      }
      if (workspaceQuestion.trim()) {
        newContextParts.push(`[Investor Investigation Question: ${workspaceQuestion.trim()}]`);
      }
      if (workspaceNotes.trim()) {
        newContextParts.push(`[Investor Interpretation: ${workspaceNotes.trim()}]`);
      }
      if (workspaceUncertainty.trim()) {
        newContextParts.push(`[Unresolved Uncertainty: ${workspaceUncertainty.trim()}]`);
      }
      if (workspaceNextStep.trim()) {
        newContextParts.push(`[Actionable Follow-Up / Monitoring Rule: ${workspaceNextStep.trim()}]`);
      }

      if (newContextParts.length > 0) {
        compiledReason = compiledReason
          ? `${compiledReason}\n\n${newContextParts.join('\n')}`
          : newContextParts.join('\n');
      }

      await addPortfolioHolding(
        targetHolding.schemeOptionId,
        targetHolding.units,
        targetHolding.costBasisAmount,
        compiledReason || null,
        workspaceQuestion.trim() || null,
        workspaceNextStep.trim() || null
      );
      setSaveSuccessMsg('Risk investigation note logged to workspace.');
      if (onHoldingUpdated) onHoldingUpdated();
      setTimeout(() => {
        setWorkspaceFinding(null);
        setSaveSuccessMsg(null);
      }, 1500);
    } catch (err: unknown) {
      alert((err as Error).message || 'Failed to save workspace note');
    } finally {
      setSavingNote(false);
    }
  };

  const summary = data?.summary;
  const covMatrix = data?.covarianceMatrix;
  const contributions = data?.riskContributions || [];
  const diversification = data?.diversification;
  const downside = data?.downsideAnalysis;
  const findings = data?.investigationFindings || [];
  const sensitivity = data?.lookbackSensitivity;

  const win1Y = sensitivity?.windows?.find((w) => w.windowCode === '1Y');
  const win3Y = sensitivity?.windows?.find((w) => w.windowCode === '3Y');
  const win5Y = sensitivity?.windows?.find((w) => w.windowCode === '5Y');

  return (
    <div className="bg-surface border border-border rounded-xl p-6 shadow-sm text-text-primary dark:bg-slate-900 dark:border-slate-800 dark:text-slate-100 space-y-6">
      {/* ------------------------------------------------------------------- */}
      {/* 1. SECTION HEADER & METHODOLOGY IDENTIFIERS */}
      {/* ------------------------------------------------------------------- */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between border-b border-border dark:border-slate-800 pb-4 gap-4">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="text-lg font-bold tracking-tight">
              Portfolio Risk & Diversification Intelligence
            </h3>
            <span className="px-2 py-0.5 rounded text-[10px] font-mono font-semibold bg-amber-500/15 text-amber-600 dark:text-amber-400 border border-amber-500/30">
              STATIC CURRENT-ALLOCATION HISTORICAL PROXY
            </span>
            <span className="px-2 py-0.5 rounded text-[10px] font-mono font-semibold bg-blue-500/15 text-blue-600 dark:text-blue-400 border border-blue-500/30">
              EULER COVARIANCE DECOMPOSITION
            </span>
            {data?.evidenceThresholdPolicyVersion && (
              <span className="px-2 py-0.5 rounded text-[10px] font-mono font-semibold bg-purple-500/15 text-purple-600 dark:text-purple-400 border border-purple-500/30">
                {data.evidenceThresholdPolicyVersion}
              </span>
            )}
            <span className="px-2 py-0.5 rounded text-[10px] font-mono font-semibold bg-emerald-500/15 text-emerald-600 dark:text-emerald-400 border border-emerald-500/30">
              TASK 18 LOOKBACK SENSITIVITY
            </span>
          </div>
          <p className="text-xs text-text-secondary dark:text-slate-400 mt-1">
            Deterministic portfolio volatility proxy, fund risk contributions, covariance matrix suitability diagnostics, and historical lookback sensitivity (1Y / 3Y / 5Y).
          </p>
          {data?.calculationRunId && (
            <div className="text-[10px] font-mono text-text-tertiary mt-1">
              Run ID: <span className="text-text-secondary">{data.calculationRunId}</span> | Tolerance: <span className="text-text-secondary">{data.numericalTolerance}</span>
            </div>
          )}
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={loadRiskData}
            disabled={loading}
            className="px-3 py-1.5 text-xs font-medium rounded-lg border border-border dark:border-slate-700 bg-surface hover:bg-slate-50 dark:hover:bg-slate-800 dark:hover:bg-slate-700 transition"
          >
            {loading ? 'Evaluating...' : 'Recalculate'}
          </button>
        </div>
      </div>

      {/* Epistemic Guardrail Notice */}
      <div className="p-3.5 rounded-lg bg-amber-500/10 border border-amber-500/25 text-amber-800 dark:text-amber-300 text-xs flex items-start gap-2.5">
        <span className="font-bold text-amber-600 dark:text-amber-400 mt-0.5">ℹ</span>
        <div className="space-y-0.5 leading-relaxed">
          <p className="font-semibold text-[11px] tracking-wide uppercase font-mono">
            Hypothetical Static Current-Allocation Historical Risk Proxy
          </p>
          <p className="text-[11px] opacity-90">
            This capability models historical return co-movement and portfolio volatility by applying the investor&apos;s <strong>current mutual fund allocation weights</strong> to synchronized historical daily returns. It does <em>not</em> represent realized historical investor returns, which depend on transaction timings and fluctuating portfolio cash flows.
          </p>
        </div>
      </div>

      {/* ------------------------------------------------------------------- */}
      {/* 2. SUB-SECTION NAVIGATION */}
      {/* ------------------------------------------------------------------- */}
      <div className="flex flex-wrap gap-1 border-b border-border dark:border-slate-800 pb-2">
        {[
          { id: 'overview', label: 'A. Risk Overview' },
          { id: 'matrix', label: 'B. Correlation Matrix & Diagnostics' },
          { id: 'contributions', label: 'C. Euler Risk Contributions' },
          { id: 'diversification', label: 'D. Diversification & Overlap' },
          { id: 'downside', label: 'E. Downside & Tail Risk' },
          { id: 'sensitivity', label: 'F. Lookback Sensitivity (1Y/3Y/5Y)' },
          { id: 'stress', label: 'G. Historical Stress Analysis' },
          { id: 'investigation', label: 'H. Investigation & Workspace' }
        ].map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id as any)}
            className={`px-3 py-1.5 text-xs font-medium rounded-md transition ${
              activeTab === tab.id
                ? 'bg-primary text-white shadow-sm'
                : 'text-text-secondary hover:text-text-primary hover:bg-slate-100 dark:hover:bg-slate-800'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Shared Portfolio Context & Provenance Banner (Task 77) */}
      {!loading && data && data.evaluationStatus === 'SUCCESS' && (
        <PortfolioSharedContextBanner
          snapshot={data.allocationSnapshot || data.historicalStressAnalysis?.allocationSnapshot}
          calculationContext={data.historicalStressAnalysis?.calculationContext}
          calculationRunId={data.calculationRunId}
          calculationInputFingerprint={data.historicalStressAnalysis?.calculationInputFingerprint || data.historicalStressAnalysis?.summary?.calculationInputFingerprint}
          asOfDate={data.summary?.asOfDate}
          knowledgeCutoff={data.summary?.knowledgeCutoff}
          benchmarkCode={data.historicalStressAnalysis?.benchmarkReference?.benchmarkCode}
          sourceLineageState={data.historicalStressAnalysis?.sourceLineageState}
        />
      )}

      {/* Error state */}
      {error && (
        <div className="p-4 rounded-lg bg-red-500/10 border border-red-500/30 text-red-700 dark:text-red-400 text-xs">
          <strong>Error:</strong> {error}
        </div>
      )}

      {/* Loading state */}
      {loading && !data && (
        <div className="py-12 text-center text-xs text-text-tertiary font-mono">
          Evaluating aligned daily return series, covariance suitability diagnostics, and lookback sensitivity...
        </div>
      )}

      {/* Empty / Insufficient data state */}
      {!loading && data && data.evaluationStatus !== 'SUCCESS' && (
        <div className="py-10 text-center space-y-3">
          <div className="text-sm font-semibold text-text-secondary">
            {data.summary?.methodologyDisclaimer || 'Insufficient historical return observations.'}
          </div>
          <p className="text-xs text-text-tertiary max-w-lg mx-auto">
            At least 20 synchronized trading observations across valued portfolio funds are required to establish an empirical covariance matrix.
          </p>
          {summary?.unvaluedPositions && summary.unvaluedPositions.length > 0 && (
            <div className="mt-4 p-4 border border-border dark:border-slate-800 rounded-lg max-w-xl mx-auto text-left">
              <div className="text-xs font-semibold text-text-secondary mb-2">Unvalued Positions Isolated:</div>
              <ul className="text-xs space-y-1 list-disc pl-4 text-text-tertiary">
                {summary.unvaluedPositions.map((p, idx) => (
                  <li key={idx}>
                    <strong>{p.fundName}</strong>: {p.reason}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}

      {/* ------------------------------------------------------------------- */}
      {/* 3. SUBSECTION A: RISK OVERVIEW */}
      {/* ------------------------------------------------------------------- */}
      {!loading && data && data.evaluationStatus === 'SUCCESS' && activeTab === 'overview' && (
        <div className="space-y-6">
          {/* Key Metrics Cards with Metric-Specific Evidence Badges */}
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
            <div className="p-3.5 bg-slate-50 dark:bg-slate-800/60 border border-border dark:border-slate-800 rounded-lg flex flex-col justify-between">
              <div>
                <span className="text-[10px] font-mono text-text-tertiary uppercase block">
                  Portfolio Volatility Proxy
                </span>
                <span className="text-xl font-bold font-mono text-primary dark:text-blue-400 mt-1 block">
                  {formatPercentage(summary?.annualizedPortfolioVolatilityProxy ?? null)}
                </span>
                <span className="text-[10px] text-text-secondary mt-0.5 block">
                  Annualized (252 days)
                </span>
              </div>
              <div className="mt-2 pt-1 border-t border-border/50 dark:border-slate-700/50">
                {renderEvidenceBadge(summary?.portfolioVolatilityEvidenceState)}
              </div>
            </div>

            <div className="p-3.5 bg-slate-50 dark:bg-slate-800/60 border border-border dark:border-slate-800 rounded-lg flex flex-col justify-between">
              <div>
                <span className="text-[10px] font-mono text-text-tertiary uppercase block">
                  Weighted Standalone Vol
                </span>
                <span className="text-xl font-bold font-mono text-text-primary mt-1 block">
                  {formatPercentage(diversification?.weightedStandaloneVolatilityPct ?? null)}
                </span>
                <span className="text-[10px] text-text-secondary mt-0.5 block">
                  Sum of w_i * sigma_i
                </span>
              </div>
              <div className="mt-2 pt-1 border-t border-border/50 dark:border-slate-700/50">
                {renderEvidenceBadge(summary?.windowEvidenceState)}
              </div>
            </div>

            <div className="p-3.5 bg-slate-50 dark:bg-slate-800/60 border border-border dark:border-slate-800 rounded-lg flex flex-col justify-between">
              <div>
                <span className="text-[10px] font-mono text-text-tertiary uppercase block">
                  Diversification Benefit
                </span>
                <span className="text-xl font-bold font-mono text-emerald-600 dark:text-emerald-400 mt-1 block">
                  +{formatPercentage(diversification?.diversificationEffectPct ?? null)}
                </span>
                <span className="text-[10px] text-text-secondary mt-0.5 block">
                  Ratio: {diversification?.diversificationRatio ? `${diversification.diversificationRatio.toFixed(2)}x` : '—'}
                </span>
              </div>
              <div className="mt-2 pt-1 border-t border-border/50 dark:border-slate-700/50">
                {renderEvidenceBadge(diversification?.diversificationEvidenceState)}
              </div>
            </div>

            <div className="p-3.5 bg-slate-50 dark:bg-slate-800/60 border border-border dark:border-slate-800 rounded-lg flex flex-col justify-between">
              <div>
                <span className="text-[10px] font-mono text-text-tertiary uppercase block">
                  Max Drawdown (RSK-03)
                </span>
                <span className="text-xl font-bold font-mono text-rose-600 dark:text-rose-400 mt-1 block">
                  {downside?.maximumDrawdownPct ? `${downside.maximumDrawdownPct.toFixed(2)}%` : '—'}
                </span>
                <span className="text-[10px] text-text-secondary mt-0.5 block">
                  Duration: {downside?.drawdownDurationTradingDays ?? 0}d {downside?.hasRecovered ? '(Recovered)' : '(Unrecovered)'}
                </span>
              </div>
              <div className="mt-2 pt-1 border-t border-border/50 dark:border-slate-700/50">
                {renderEvidenceBadge(downside?.drawdownEvidenceState)}
              </div>
            </div>

            <div className="p-3.5 bg-slate-50 dark:bg-slate-800/60 border border-border dark:border-slate-800 rounded-lg flex flex-col justify-between">
              <div>
                <span className="text-[10px] font-mono text-text-tertiary uppercase block">
                  Historical VaR 95% (RSK-06)
                </span>
                <span className="text-xl font-bold font-mono text-amber-600 dark:text-amber-400 mt-1 block">
                  {downside?.historicalVaR95Pct ? `${downside.historicalVaR95Pct.toFixed(2)}%` : '—'}
                </span>
                <span className="text-[10px] text-text-secondary mt-0.5 block">
                  Tail obs: {downside?.effectiveTailObservationCount ?? 0} (req &ge; {downside?.requiredMinimumTailObservations ?? 12})
                </span>
              </div>
              <div className="mt-2 pt-1 border-t border-border/50 dark:border-slate-700/50">
                {renderEvidenceBadge(downside?.varEvidenceState)}
              </div>
            </div>

            <div className="p-3.5 bg-slate-50 dark:bg-slate-800/60 border border-border dark:border-slate-800 rounded-lg flex flex-col justify-between">
              <div>
                <span className="text-[10px] font-mono text-text-tertiary uppercase block">
                  Known Value Coverage
                </span>
                <span className="text-xl font-bold font-mono text-text-primary mt-1 block">
                  {formatPercentage(summary?.knownValueAllocationCoveragePct ?? null)}
                </span>
                <span className="text-[10px] text-text-secondary mt-0.5 block">
                  {summary?.valuedFundCount ?? 0} valued / {summary?.unvaluedPositionCount ?? 0} unvalued
                </span>
              </div>
              <div className="mt-2 pt-1 border-t border-border/50 dark:border-slate-700/50">
                <span className="px-2 py-0.5 rounded text-[10px] font-mono font-semibold border bg-blue-500/10 text-blue-600 border-blue-500/20">
                  {summary?.valuedFundCount ?? 0} FUNDS
                </span>
              </div>
            </div>
          </div>

          {/* Observation Period and Data Alignment Metadata */}
          <div className="p-4 rounded-lg bg-surface border border-border dark:border-slate-800 space-y-3 text-xs">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
              <div className="space-y-1">
                <div className="font-semibold text-text-primary flex flex-wrap items-center gap-2">
                  <span>Synchronized Historical Observation Window:</span>
                  <span className="font-mono text-text-secondary">
                    {summary?.commonStartDate || '—'} to {summary?.commonEndDate || '—'}
                  </span>
                  <span className="px-1.5 py-0.5 rounded text-[10px] font-mono font-semibold bg-blue-500/10 text-blue-600 dark:text-blue-400 border border-blue-500/20">
                    {summary?.alignedObservationCount ?? 0} ALIGNED TRADING DAYS
                  </span>
                </div>
                <p className="text-text-tertiary text-[11px]">
                  Annualization factor: {summary?.annualizationFactor} trading days | Bessel&apos;s sample variance denominator: {summary?.sampleDenominatorConvention}
                </p>
              </div>

              <div className="flex flex-wrap items-center gap-2">
                <span className="text-text-tertiary">Window Adequacy:</span>
                {renderEvidenceBadge(summary?.windowEvidenceState)}
              </div>
            </div>

            {/* Data Alignment Breakdown */}
            <div className="pt-2 border-t border-border dark:border-slate-800 grid grid-cols-1 sm:grid-cols-3 gap-2 text-[11px] font-mono text-text-secondary">
              <div className="p-2 rounded bg-slate-50 dark:bg-slate-800/40 border border-border dark:border-slate-800">
                <span className="text-text-tertiary block">Calendar Gap Exclusions (&gt; 7 days):</span>
                <span className="font-bold text-text-primary">{summary?.calendarGapExclusionsCount ?? 0} intervals</span>
              </div>
              <div className="p-2 rounded bg-slate-50 dark:bg-slate-800/40 border border-border dark:border-slate-800">
                <span className="text-text-tertiary block">Excluded Dates (missing/invalid NAVs):</span>
                <span className="font-bold text-text-primary">{summary?.excludedObservationCount ?? 0} dates</span>
              </div>
              <div className="p-2 rounded bg-slate-50 dark:bg-slate-800/40 border border-border dark:border-slate-800">
                <span className="text-text-tertiary block">Euler Identity Discrepancy:</span>
                <span className={`font-bold ${summary?.isEulerReconciled ? 'text-emerald-600' : 'text-rose-600'}`}>
                  &delta; = {summary?.eulerReconciliationDiscrepancy !== undefined ? summary.eulerReconciliationDiscrepancy.toFixed(6) : '0.000000'} ({summary?.isEulerReconciled ? 'Reconciled' : 'Discrepancy'})
                </span>
              </div>
            </div>

            <div className="text-[11px] text-text-tertiary leading-relaxed">
              <strong>Alignment Context:</strong> {summary?.alignmentLimitations}
            </div>

            {summary?.perFundObservationCounts && Object.keys(summary.perFundObservationCounts).length > 0 && (
              <div>
                <button
                  onClick={() => setShowPerFundCounts(!showPerFundCounts)}
                  className="text-[11px] text-primary hover:underline font-medium"
                >
                  {showPerFundCounts ? 'Hide per-fund raw observation counts' : 'Show per-fund raw observation counts'}
                </button>
                {showPerFundCounts && (
                  <div className="mt-2 p-3 rounded bg-slate-50 dark:bg-slate-800/60 border border-border dark:border-slate-800 grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-2 text-[11px] font-mono">
                    {Object.entries(summary.perFundObservationCounts).map(([fundName, count]) => (
                      <div key={fundName} className="flex justify-between border-b border-border/40 pb-1">
                        <span className="truncate max-w-[200px]" title={fundName}>{fundName}:</span>
                        <span className="font-bold">{count} raw observations</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Unvalued Positions Alert if any */}
          {summary?.unvaluedPositions && summary.unvaluedPositions.length > 0 && (
            <div className="p-4 rounded-lg bg-slate-50 dark:bg-slate-800/40 border border-border dark:border-slate-800 space-y-2">
              <div className="text-xs font-semibold text-amber-600 dark:text-amber-400 flex items-center gap-1.5">
                <span>⚠</span>
                <span>Unvalued Positions Excluded from Allocation Denominator ({summary.unvaluedPositions.length})</span>
              </div>
              <div className="text-xs text-text-secondary space-y-1">
                {summary.unvaluedPositions.map((pos, idx) => (
                  <div key={idx} className="flex items-center justify-between font-mono text-[11px]">
                    <span>{pos.fundName} (Units: {pos.units.toString()})</span>
                    <span className="text-text-tertiary">{pos.reason}</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {/* ------------------------------------------------------------------- */}
      {/* 4. SUBSECTION B: CORRELATION & COVARIANCE MATRIX WITH DIAGNOSTICS */}
      {/* ------------------------------------------------------------------- */}
      {!loading && data && data.evaluationStatus === 'SUCCESS' && activeTab === 'matrix' && (
        <div className="space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div>
              <div className="flex items-center gap-2">
                <h4 className="text-sm font-semibold text-text-primary">
                  Pairwise Historical Co-Movement &amp; Covariance Matrix
                </h4>
                {renderEvidenceBadge(covMatrix?.matrixSuitabilityState)}
                {renderEvidenceBadge(covMatrix?.matrixEvidenceState)}
              </div>
              <p className="text-xs text-text-tertiary mt-0.5">
                Pearson correlation coefficients and annualized daily return covariance across constituent funds with rigorous numerical diagnostics.
              </p>
            </div>
            <div className="flex items-center gap-1 bg-slate-100 dark:bg-slate-800 p-0.5 rounded-lg border border-border dark:border-slate-700 self-start sm:self-auto">
              <button
                onClick={() => setMatrixView('correlation')}
                className={`px-2.5 py-1 text-xs font-medium rounded ${
                  matrixView === 'correlation'
                    ? 'bg-surface dark:bg-slate-700 text-text-primary shadow-xs'
                    : 'text-text-secondary hover:text-text-primary'
                }`}
              >
                Correlation (r)
              </button>
              <button
                onClick={() => setMatrixView('covariance')}
                className={`px-2.5 py-1 text-xs font-medium rounded ${
                  matrixView === 'covariance'
                    ? 'bg-surface dark:bg-slate-700 text-text-primary shadow-xs'
                    : 'text-text-secondary hover:text-text-primary'
                }`}
              >
                Covariance (&Sigma;)
              </button>
            </div>
          </div>

          {/* Covariance Matrix Numerical Diagnostics Bar */}
          {covMatrix && (
            <div className="p-3.5 rounded-lg bg-slate-50 dark:bg-slate-800/60 border border-border dark:border-slate-800 space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold text-text-secondary font-mono">
                  Numerical Integrity Diagnostics (Deterministic Jacobi Eigensolver):
                </span>
                <span className="text-[10px] text-text-tertiary font-mono">
                  Zero matrix repair or shrinkage applied
                </span>
              </div>

              <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-6 gap-2 text-[11px] font-mono">
                <div className="p-2 rounded bg-surface dark:bg-slate-900 border border-border dark:border-slate-800">
                  <span className="text-text-tertiary block">&lambda;_min:</span>
                  <span className={`font-bold ${covMatrix.isPositiveSemiDefinite ? 'text-emerald-600' : 'text-rose-600'}`}>
                    {covMatrix.minimumEigenvalue !== undefined ? covMatrix.minimumEigenvalue.toFixed(6) : '—'}
                  </span>
                </div>

                <div className="p-2 rounded bg-surface dark:bg-slate-900 border border-border dark:border-slate-800">
                  <span className="text-text-tertiary block">&lambda;_max:</span>
                  <span className="font-bold text-text-primary">
                    {covMatrix.maximumEigenvalue !== undefined ? covMatrix.maximumEigenvalue.toFixed(6) : '—'}
                  </span>
                </div>

                <div className="p-2 rounded bg-surface dark:bg-slate-900 border border-border dark:border-slate-800">
                  <span className="text-text-tertiary block">Condition Number (&kappa;):</span>
                  <span className={`font-bold ${covMatrix.isNearSingular ? 'text-amber-600' : 'text-text-primary'}`}>
                    {covMatrix.conditionNumber != null ? covMatrix.conditionNumber.toFixed(2) : (covMatrix.isSingular ? '— (Singular)' : '—')}
                  </span>
                </div>

                <div className="p-2 rounded bg-surface dark:bg-slate-900 border border-border dark:border-slate-800">
                  <span className="text-text-tertiary block">Determinant (&det;):</span>
                  <span className="font-bold text-text-primary truncate block" title={String(covMatrix.determinant)}>
                    {covMatrix.determinant != null ? covMatrix.determinant.toExponential(4) : '—'}
                  </span>
                </div>

                <div className="p-2 rounded bg-surface dark:bg-slate-900 border border-border dark:border-slate-800">
                  <span className="text-text-tertiary block">Positive Semidefinite:</span>
                  <span className={`font-bold ${covMatrix.isPositiveSemiDefinite ? 'text-emerald-600' : 'text-rose-600'}`}>
                    {covMatrix.isPositiveSemiDefinite ? 'YES (&ge; -1e-8)' : 'NO (Violated)'}
                  </span>
                </div>

                <div className="p-2 rounded bg-surface dark:bg-slate-900 border border-border dark:border-slate-800">
                  <span className="text-text-tertiary block">Symmetry &amp; Finite:</span>
                  <span className={`font-bold ${covMatrix.isSymmetric && covMatrix.isFinite ? 'text-emerald-600' : 'text-rose-600'}`}>
                    {covMatrix.isSymmetric && covMatrix.isFinite ? 'VALID' : 'INVALID'}
                  </span>
                </div>
              </div>

              <div className="text-[11px] text-text-secondary leading-relaxed pt-1">
                <strong>Suitability Evaluation:</strong> {covMatrix.matrixSuitabilityExplanation}
              </div>
            </div>
          )}

          {covMatrix && covMatrix.fundNames.length > 0 ? (
            <div className="overflow-x-auto border border-border dark:border-slate-800 rounded-lg">
              <table className="w-full text-xs text-left border-collapse">
                <thead>
                  <tr className="bg-slate-50 dark:bg-slate-800/70 border-b border-border dark:border-slate-800">
                    <th className="p-3 font-mono text-text-secondary w-48">Fund</th>
                    {covMatrix.fundNames.map((name, idx) => (
                      <th key={idx} className="p-3 font-mono text-text-secondary text-center min-w-[110px]" title={name}>
                        #{idx + 1}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-border dark:divide-slate-800 font-mono">
                  {covMatrix.fundNames.map((nameI, i) => (
                    <tr key={i} className="hover:bg-slate-50/50 dark:hover:bg-slate-800/30">
                      <td className="p-3 font-medium text-text-primary whitespace-nowrap truncate max-w-xs" title={nameI}>
                        <span className="text-text-tertiary mr-1.5">#{i + 1}</span>
                        {nameI}
                      </td>
                      {covMatrix.fundNames.map((nameJ, j) => {
                        const corrVal = covMatrix.correlationMatrix[i][j];
                        const covVal = covMatrix.covarianceMatrix[i][j];
                        const isDiag = i === j;

                        let cellBg = 'bg-transparent';
                        let cellText = 'text-text-primary';

                        if (!isDiag) {
                          if (corrVal >= 0.8) {
                            cellBg = 'bg-rose-500/15';
                            cellText = 'text-rose-600 dark:text-rose-400 font-bold';
                          } else if (corrVal >= 0.5) {
                            cellBg = 'bg-amber-500/10';
                            cellText = 'text-amber-600 dark:text-amber-400';
                          } else if (corrVal <= 0.2) {
                            cellBg = 'bg-emerald-500/15';
                            cellText = 'text-emerald-600 dark:text-emerald-400 font-bold';
                          }
                        } else {
                          cellBg = 'bg-slate-100 dark:bg-slate-800';
                          cellText = 'text-text-tertiary';
                        }

                        return (
                          <td
                            key={j}
                            className={`p-3 text-center border-l border-border dark:border-slate-800 transition ${cellBg} ${cellText}`}
                          >
                            {matrixView === 'correlation' ? (
                              <span>{corrVal.toFixed(2)}</span>
                            ) : (
                              <span>{covVal.toFixed(5)}</span>
                            )}
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="py-8 text-center text-xs text-text-tertiary">
              No covariance matrix available.
            </div>
          )}

          <div className="flex flex-wrap items-center gap-4 text-[11px] text-text-tertiary pt-2">
            <span className="flex items-center gap-1.5">
              <span className="w-3 h-3 rounded bg-rose-500/20 border border-rose-500/40 inline-block"></span>
              High co-movement (r &ge; 0.80)
            </span>
            <span className="flex items-center gap-1.5">
              <span className="w-3 h-3 rounded bg-amber-500/15 border border-amber-500/30 inline-block"></span>
              Moderate co-movement (0.50 &le; r &lt; 0.80)
            </span>
            <span className="flex items-center gap-1.5">
              <span className="w-3 h-3 rounded bg-emerald-500/20 border border-emerald-500/40 inline-block"></span>
              Diversification cushion (r &le; 0.20)
            </span>
          </div>
        </div>
      )}

      {/* ------------------------------------------------------------------- */}
      {/* 5. SUBSECTION C: EULER RISK CONTRIBUTIONS & SAFEGUARDS */}
      {/* ------------------------------------------------------------------- */}
      {!loading && data && data.evaluationStatus === 'SUCCESS' && activeTab === 'contributions' && (
        <div className="space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div>
              <div className="flex items-center gap-2">
                <h4 className="text-sm font-semibold text-text-primary">
                  Euler Volatility Risk Decomposition
                </h4>
                {renderEvidenceBadge(summary?.eulerEvidenceState)}
              </div>
              <p className="text-xs text-text-tertiary mt-0.5">
                Mathematical attribution of portfolio volatility to individual funds: RC_i = w_i * (&Sigma; w)_i / &sigma;_p.
              </p>
            </div>
          </div>

          {!covMatrix?.isMathematicallySuitable && (
            <div className="p-3.5 rounded-lg bg-rose-500/10 border border-rose-500/30 text-rose-700 dark:text-rose-400 text-xs">
              <strong>Caution:</strong> Covariance matrix suitability checks failed. Euler risk attribution cannot be interpreted as mathematically reliable.
            </div>
          )}

          <div className="overflow-x-auto border border-border dark:border-slate-800 rounded-lg">
            <table className="w-full text-xs text-left border-collapse">
              <thead>
                <tr className="bg-slate-50 dark:bg-slate-800/70 border-b border-border dark:border-slate-800 text-text-secondary font-mono">
                  <th className="p-3">Fund / Scheme</th>
                  <th className="p-3 text-right">Market Value</th>
                  <th className="p-3 text-right">Capital Weight</th>
                  <th className="p-3 text-right">Standalone Vol</th>
                  <th className="p-3 text-right">Marginal RC</th>
                  <th className="p-3 text-right">Volatility Contrib</th>
                  <th className="p-3 text-right">% Risk Contrib</th>
                  <th className="p-3 text-right">Ratio (%RC / Weight)</th>
                  <th className="p-3 text-center">Evidence</th>
                  <th className="p-3 text-center">Classification</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border dark:divide-slate-800 font-mono">
                {contributions.map((c) => (
                  <tr key={c.schemeOptionId} className="hover:bg-slate-50/50 dark:hover:bg-slate-800/30">
                    <td className="p-3 font-sans">
                      <div className="font-semibold text-text-primary">{c.fundName}</div>
                      <div className="text-[10px] text-text-tertiary font-mono">{c.category} | {c.planType}</div>
                    </td>
                    <td className="p-3 text-right">{formatCurrency(c.marketValue)}</td>
                    <td className="p-3 text-right font-bold">{c.allocationWeightPct.toFixed(2)}%</td>
                    <td className="p-3 text-right text-text-secondary">
                      <div>{c.standaloneVolatilityPct.toFixed(2)}%</div>
                      <div className="text-[9px] text-text-tertiary">{c.standaloneVolatilityEvidenceState}</div>
                    </td>
                    <td className="p-3 text-right text-text-secondary">
                      {c.isMathematicallyReliable ? `${c.marginalRiskContributionPct.toFixed(2)}%` : '—'}
                    </td>
                    <td className="p-3 text-right text-text-secondary">
                      {c.isMathematicallyReliable ? `${c.componentRiskContributionPct.toFixed(2)}%` : '—'}
                    </td>
                    <td className="p-3 text-right font-bold text-primary dark:text-blue-400">
                      {c.isMathematicallyReliable ? `${c.percentageRiskContribution.toFixed(2)}%` : '—'}
                    </td>
                    <td className="p-3 text-right font-mono font-semibold text-text-secondary">
                      {c.isMathematicallyReliable && c.contributionToWeightRatio != null
                        ? `${c.contributionToWeightRatio.toFixed(2)}x`
                        : '—'}
                    </td>
                    <td className="p-3 text-center">
                      {renderEvidenceBadge(c.componentRiskContributionEvidenceState)}
                    </td>
                    <td className="p-3 text-center">
                      <span className={`px-2 py-0.5 rounded text-[10px] font-semibold border ${getRiskContributionBadgeColor(c.classification)}`}>
                        {!c.isMathematicallyReliable
                          ? 'Unreliable attribution'
                          : getRiskContributionLabel(c.classification)}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Euler Reconciliation Identity Box */}
          <div className="p-3.5 rounded-lg bg-slate-50 dark:bg-slate-800/50 border border-border dark:border-slate-800 text-xs font-mono flex flex-col md:flex-row md:items-center justify-between gap-2 text-text-secondary">
            <div>
              <span className="font-bold text-text-primary">Euler Identity Verification: </span>
              <span>SUM(RC_i) = {contributions.reduce((acc, c) => acc + c.componentRiskContributionPct, 0).toFixed(2)}% (Portfolio Volatility: {summary?.annualizedPortfolioVolatilityProxy?.toFixed(2)}%)</span>
              <span className="ml-2 font-semibold text-text-primary">
                [&delta; = {summary?.eulerReconciliationDiscrepancy !== undefined ? summary.eulerReconciliationDiscrepancy.toFixed(6) : '0.000000'}, Tolerance: {data?.numericalTolerance}]
              </span>
            </div>
            <div className="flex items-center gap-2">
              <span>SUM(%RC_i) = {contributions.reduce((acc, c) => acc + c.percentageRiskContribution, 0).toFixed(1)}%</span>
              <span className={`px-2 py-0.5 rounded text-[10px] font-bold border ${
                summary?.isEulerReconciled
                  ? 'bg-emerald-500/15 text-emerald-600 border-emerald-500/30'
                  : 'bg-rose-500/15 text-rose-600 border-rose-500/30'
              }`}>
                {summary?.isEulerReconciled ? 'RECONCILED' : 'DISCREPANCY DETECTED'}
              </span>
            </div>
          </div>

          {/* Risk Contribution Taxonomy Guidance */}
          <div className="p-3.5 rounded-lg bg-slate-50 dark:bg-slate-800/40 border border-border dark:border-slate-800 text-xs space-y-2">
            <div className="font-semibold text-text-primary text-[11px] font-mono uppercase tracking-wider">
              Risk Contribution Classification Semantics (Policy: {data?.classificationPolicyVersion || data?.methodologyNotes?.classificationPolicyVersion || 'PORTFOLIO_RISK_CLASSIFICATION_POLICY_V4'})
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2 text-[11px] font-mono">
              <div className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 rounded-full bg-rose-500"></span>
                <span><strong>Disproportionate contributor:</strong> Ratio &gt; 1.15 (requires weight &gt; 0.0001% &amp; verified ratio)</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 rounded-full bg-amber-500"></span>
                <span><strong>Above-weight contribution:</strong> 1.02 &lt; Ratio &le; 1.15</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 rounded-full bg-emerald-500"></span>
                <span><strong>Proportional to allocation:</strong> 0.98 &le; Ratio &le; 1.02 (&plusmn;2% parity)</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 rounded-full bg-blue-500"></span>
                <span><strong>Below-weight contribution:</strong> 0.00 &le; Ratio &lt; 0.98</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 rounded-full bg-indigo-500"></span>
                <span><strong>Negligible negative:</strong> -0.0001% &le; %RC &lt; 0.00% (near-zero marginal covariance; ratio omitted)</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 rounded-full bg-purple-500"></span>
                <span><strong>Negative contribution:</strong> %RC &lt; -0.0001% (historical sample dampener; ratio omitted)</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 rounded-full bg-slate-500"></span>
                <span><strong>Zero-weight position:</strong> Weight = 0.00% &amp; |%RC| &le; 0.0001% (ratio undefined)</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 rounded-full bg-cyan-500"></span>
                <span><strong>Near-zero allocation:</strong> 0.00% &lt; Weight &le; 0.0001% &amp; |%RC| &le; 0.0001% (ratio omitted)</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 rounded-full bg-orange-500"></span>
                <span><strong>Allocation mismatch:</strong> Weight &le; 0.0001% with |%RC| &gt; 0.0001% (anomaly; ratio omitted)</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 rounded-full bg-rose-400"></span>
                <span><strong>Unreliable attribution:</strong> Matrix unsuitable or Euler reconciliation discrepancy</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 rounded-full bg-slate-400"></span>
                <span><strong>Unclassified:</strong> Null/NaN/Inf or negative capital weight</span>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ------------------------------------------------------------------- */}
      {/* 6. SUBSECTION D: DIVERSIFICATION & OVERLAP */}
      {/* ------------------------------------------------------------------- */}
      {!loading && data && data.evaluationStatus === 'SUCCESS' && activeTab === 'diversification' && (
        <div className="space-y-6">
          {/* Diversification Spectrum */}
          <div className="p-5 rounded-lg bg-surface border border-border dark:border-slate-800 space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
              <div>
                <div className="flex items-center gap-2">
                  <h4 className="text-sm font-semibold text-text-primary">
                    Portfolio Diversification Benefit
                  </h4>
                  {renderEvidenceBadge(diversification?.diversificationEvidenceState)}
                </div>
                <p className="text-xs text-text-tertiary mt-0.5">
                  Comparing weighted standalone volatility against actual combined portfolio volatility proxy.
                </p>
              </div>
              <span className={`px-2.5 py-1 rounded text-xs font-mono font-bold border ${
                diversification?.diversificationBenefitClassification === 'SIGNIFICANT_DIVERSIFICATION'
                  ? 'bg-emerald-500/15 text-emerald-600 border-emerald-500/30'
                  : diversification?.diversificationBenefitClassification === 'MODERATE_DIVERSIFICATION'
                  ? 'bg-blue-500/15 text-blue-600 border-blue-500/30'
                  : 'bg-amber-500/15 text-amber-600 border-amber-500/30'
              }`}>
                {diversification?.diversificationBenefitClassification || 'DIVERSIFICATION UNRATED'}
              </span>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-4 pt-2">
              <div className="p-3 bg-slate-50 dark:bg-slate-800/60 rounded-lg border border-border dark:border-slate-800">
                <span className="text-[10px] font-mono text-text-tertiary uppercase block">
                  Weighted Standalone Volatility
                </span>
                <span className="text-xl font-bold font-mono text-text-primary mt-1 block">
                  {formatPercentage(diversification?.weightedStandaloneVolatilityPct ?? null)}
                </span>
                <span className="text-[10px] text-text-secondary mt-0.5 block">
                  If correlations were 1.0 (zero diversification)
                </span>
              </div>

              <div className="p-3 bg-slate-50 dark:bg-slate-800/60 rounded-lg border border-border dark:border-slate-800">
                <span className="text-[10px] font-mono text-text-tertiary uppercase block">
                  Combined Portfolio Volatility Proxy
                </span>
                <span className="text-xl font-bold font-mono text-primary dark:text-blue-400 mt-1 block">
                  {formatPercentage(diversification?.portfolioVolatilityProxyPct ?? null)}
                </span>
                <span className="text-[10px] text-text-secondary mt-0.5 block">
                  Accounting for historical co-movement
                </span>
              </div>

              <div className="p-3 bg-emerald-500/10 rounded-lg border border-emerald-500/30">
                <span className="text-[10px] font-mono text-emerald-700 dark:text-emerald-400 uppercase block">
                  Volatility Reduction (Benefit)
                </span>
                <span className="text-xl font-bold font-mono text-emerald-600 dark:text-emerald-400 mt-1 block">
                  -{formatPercentage(diversification?.diversificationEffectPct ?? null)}
                </span>
                <span className="text-[10px] text-emerald-700 dark:text-emerald-400 mt-0.5 block">
                  Diversification Ratio: {diversification?.diversificationRatio ? `${diversification.diversificationRatio.toFixed(2)}x` : '—'}
                </span>
              </div>
            </div>

            <p className="text-[11px] text-text-tertiary leading-relaxed">
              <strong>Caveat:</strong> {diversification?.diversificationCaveat}
            </p>
          </div>

          {/* Highly Correlated Pairs */}
          <div className="space-y-3">
            <h5 className="text-xs font-semibold text-text-secondary uppercase tracking-wider font-mono">
              Highly Correlated Pairs (r &ge; 0.80) — Limited Diversification Cushion
            </h5>
            {diversification?.highlyCorrelatedPairs && diversification.highlyCorrelatedPairs.length > 0 ? (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                {diversification.highlyCorrelatedPairs.map((pair, idx) => (
                  <div key={idx} className="p-3.5 bg-surface border border-rose-500/30 rounded-lg space-y-2">
                    <div className="flex items-center justify-between">
                      <div className="text-xs font-semibold text-text-primary truncate max-w-[70%]">
                        {pair.fundNameA} &harr; {pair.fundNameB}
                      </div>
                      <span className="px-2 py-0.5 rounded text-[10px] font-mono font-bold bg-rose-500/15 text-rose-600 border border-rose-500/30">
                        r = {pair.correlation.toFixed(2)}
                      </span>
                    </div>
                    <p className="text-[11px] text-text-secondary leading-normal">
                      {pair.interpretation}
                    </p>
                  </div>
                ))}
              </div>
            ) : (
              <div className="p-3 bg-slate-50 dark:bg-slate-800/40 rounded-lg border border-border dark:border-slate-800 text-xs text-text-secondary">
                No fund pairs exhibit high historical return co-movement above r = 0.80.
              </div>
            )}
          </div>

          {/* Low or Negative Correlation Pairs */}
          <div className="space-y-3">
            <h5 className="text-xs font-semibold text-text-secondary uppercase tracking-wider font-mono">
              Diversifying Pairs (r &le; 0.30) — Volatility Cushion
            </h5>
            {diversification?.lowOrNegativeCorrelationPairs && diversification.lowOrNegativeCorrelationPairs.length > 0 ? (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                {diversification.lowOrNegativeCorrelationPairs.map((pair, idx) => (
                  <div key={idx} className="p-3.5 bg-surface border border-emerald-500/30 rounded-lg space-y-2">
                    <div className="flex items-center justify-between">
                      <div className="text-xs font-semibold text-text-primary truncate max-w-[70%]">
                        {pair.fundNameA} &harr; {pair.fundNameB}
                      </div>
                      <span className="px-2 py-0.5 rounded text-[10px] font-mono font-bold bg-emerald-500/15 text-emerald-600 border border-emerald-500/30">
                        r = {pair.correlation.toFixed(2)}
                      </span>
                    </div>
                    <p className="text-[11px] text-text-secondary leading-normal">
                      {pair.interpretation}
                    </p>
                  </div>
                ))}
              </div>
            ) : (
              <div className="p-3 bg-slate-50 dark:bg-slate-800/40 rounded-lg border border-border dark:border-slate-800 text-xs text-text-secondary">
                All constituent fund pairs demonstrate moderate co-movement.
              </div>
            )}
          </div>
        </div>
      )}

      {/* ------------------------------------------------------------------- */}
      {/* 7. SUBSECTION E: DOWNSIDE & TAIL RISK */}
      {/* ------------------------------------------------------------------- */}
      {!loading && data && data.evaluationStatus === 'SUCCESS' && activeTab === 'downside' && (
        <div className="space-y-6">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div>
              <h4 className="text-sm font-semibold text-text-primary">
                Hypothetical Downside Portfolio Behaviour
              </h4>
              <p className="text-xs text-text-tertiary">
                Drawdown dynamics and empirical tail loss metrics over aligned historical observations.
              </p>
            </div>
            <span className="px-2.5 py-0.5 rounded text-[10px] font-mono font-bold bg-amber-500/15 text-amber-600 dark:text-amber-400 border border-amber-500/30 self-start sm:self-auto">
              GOVERNANCE: {downside?.metricGovernanceStatus || 'CANDIDATE'}
            </span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="p-4 bg-surface border border-border dark:border-slate-800 rounded-lg space-y-1 flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between">
                  <span className="text-[10px] font-mono text-text-tertiary uppercase">Max Drawdown (RSK-03)</span>
                  {renderEvidenceBadge(downside?.drawdownEvidenceState)}
                </div>
                <div className="text-2xl font-bold font-mono text-rose-600 dark:text-rose-400 mt-1">
                  {downside?.maximumDrawdownPct ? `${downside.maximumDrawdownPct.toFixed(2)}%` : '—'}
                </div>
                <p className="text-[11px] text-text-secondary mt-1">
                  Peak: {downside?.maxDrawdownPeakDate || '—'} &rarr; Trough: {downside?.maxDrawdownTroughDate || '—'}
                </p>
              </div>
              <div className="text-[10px] font-mono text-text-tertiary pt-2 border-t border-border/40 mt-2">
                Recovery status: <span className="font-semibold text-text-primary">{downside?.hasRecovered ? 'Fully Recovered' : 'Unrecovered'}</span>
              </div>
            </div>

            <div className="p-4 bg-surface border border-border dark:border-slate-800 rounded-lg space-y-1 flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between">
                  <span className="text-[10px] font-mono text-text-tertiary uppercase">Drawdown Duration (RSK-04)</span>
                  {renderEvidenceBadge(downside?.drawdownEvidenceState)}
                </div>
                <div className="text-2xl font-bold font-mono text-text-primary mt-1">
                  {downside?.drawdownDurationTradingDays ?? 0}d
                </div>
                <p className="text-[11px] text-text-secondary mt-1">
                  Trading days from previous peak to trough
                </p>
              </div>
              <div className="text-[10px] font-mono text-text-tertiary pt-2 border-t border-border/40 mt-2">
                Recovery trading days: <span className="font-semibold text-text-primary">{downside?.recoveryTradingDays ?? 0}d</span>
              </div>
            </div>

            <div className="p-4 bg-surface border border-border dark:border-slate-800 rounded-lg space-y-1 flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between">
                  <span className="text-[10px] font-mono text-text-tertiary uppercase">Historical VaR 95% (RSK-06)</span>
                  {renderEvidenceBadge(downside?.varEvidenceState)}
                </div>
                <div className="text-2xl font-bold font-mono text-amber-600 dark:text-amber-400 mt-1">
                  {downside?.historicalVaR95Pct ? `${downside.historicalVaR95Pct.toFixed(2)}%` : '—'}
                </div>
                <p className="text-[11px] text-text-secondary mt-1">
                  {downside?.varQuantileConvention || 'Empirical 5th percentile worst daily return'}
                </p>
              </div>
              <div className="text-[10px] font-mono text-text-tertiary pt-2 border-t border-border/40 mt-2">
                Tail sample: <span className="font-semibold text-text-primary">{downside?.effectiveTailObservationCount ?? 0} obs</span> (req &ge; {downside?.requiredMinimumTailObservations ?? 12})
              </div>
            </div>

            <div className="p-4 bg-surface border border-border dark:border-slate-800 rounded-lg space-y-1 flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between">
                  <span className="text-[10px] font-mono text-text-tertiary uppercase">Expected Shortfall 95% (RSK-07)</span>
                  {renderEvidenceBadge(downside?.expectedShortfallEvidenceState)}
                </div>
                <div className="text-2xl font-bold font-mono text-rose-600 dark:text-rose-400 mt-1">
                  {downside?.historicalExpectedShortfall95Pct ? `${downside.historicalExpectedShortfall95Pct.toFixed(2)}%` : '—'}
                </div>
                <p className="text-[11px] text-text-secondary mt-1">
                  {downside?.esAveragingConvention || 'Mean daily loss in worst 5% tail (CVaR)'}
                </p>
              </div>
              <div className="text-[10px] font-mono text-text-tertiary pt-2 border-t border-border/40 mt-2">
                Confidence level: <span className="font-semibold text-text-primary">{downside?.varConfidenceLevel ? `${(downside.varConfidenceLevel * 100).toFixed(0)}%` : '95%'}</span>
              </div>
            </div>
          </div>

          <div className="p-4 rounded-lg bg-slate-50 dark:bg-slate-800/40 border border-border dark:border-slate-800 space-y-2 text-xs">
            <div className="font-semibold text-text-primary">
              Drawdown &amp; Tail Risk Limitations:
            </div>
            <p className="text-text-secondary leading-relaxed">
              {downside?.drawdownLimitationExplanation}
            </p>
            <p className="text-text-secondary leading-relaxed pt-1 border-t border-border/40">
              {downside?.tailSampleLimitations}
            </p>
          </div>
        </div>
      )}

      {/* ------------------------------------------------------------------- */}
      {/* 8. SUBSECTION F: LOOKBACK SENSITIVITY & STABILITY (TASK 18) */}
      {/* ------------------------------------------------------------------- */}
      {!loading && data && data.evaluationStatus === 'SUCCESS' && activeTab === 'sensitivity' && (
        <div className="space-y-6">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-border dark:border-slate-800 pb-3">
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <h4 className="text-sm font-bold text-text-primary">
                  Historical Lookback Sensitivity &amp; Stability Analysis
                </h4>
                {renderEvidenceBadge(sensitivity?.overallComparabilityState)}
              </div>
              <p className="text-xs text-text-tertiary mt-0.5">
                Evaluates whether portfolio volatility, fund correlations, and Euler risk attribution are consistent across 1Y (250 obs), 3Y (750 obs), and 5Y (1,250 obs) observation windows.
              </p>
            </div>
          </div>

          {/* Comparability Guardrail Notice */}
          <div className="p-3.5 rounded-lg bg-blue-500/10 border border-blue-500/25 text-blue-800 dark:text-blue-300 text-xs flex items-start gap-2.5">
            <span className="font-bold text-blue-600 dark:text-blue-400 mt-0.5">ℹ</span>
            <div className="space-y-0.5 leading-relaxed">
              <p className="font-semibold text-[11px] tracking-wide uppercase font-mono">
                Lookback Comparability &amp; Historical Regime Guardrail
              </p>
              <p className="text-[11px] opacity-90">
                All windows use the investor&apos;s <strong>identical static capital allocation weights</strong> applied to the <strong>identical fund universe</strong> over the latest N synchronized historical daily observations. Differences between windows reflect historical market conditions; they do <em>not</em> prove macroeconomic structural changes or predict future performance.
              </p>
            </div>
          </div>

          {/* A. Standard Horizon Window Comparison Table */}
          <div className="space-y-3">
            <h5 className="text-xs font-semibold text-text-secondary uppercase tracking-wider font-mono">
              A. Multi-Window Historical Risk Comparison Table
            </h5>

            <div className="overflow-x-auto border border-border dark:border-slate-800 rounded-lg">
              <table className="w-full text-xs text-left border-collapse font-mono">
                <thead>
                  <tr className="bg-slate-50 dark:bg-slate-800/70 border-b border-border dark:border-slate-800 text-text-secondary">
                    <th className="p-3 text-left w-52 font-sans font-semibold">Risk Dimension / Metric</th>
                    <th className="p-3 text-center min-w-[150px]">1Y Window (250 Obs)</th>
                    <th className="p-3 text-center min-w-[150px]">3Y Window (750 Obs)</th>
                    <th className="p-3 text-center min-w-[150px]">5Y Window (1,250 Obs)</th>
                    <th className="p-3 text-center min-w-[150px] bg-slate-100/50 dark:bg-slate-800/40">Lookback Sensitivity (1Y vs 3Y)</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border dark:divide-slate-800">
                  {/* Observation Window & Status */}
                  <tr className="hover:bg-slate-50/50 dark:hover:bg-slate-800/30">
                    <td className="p-3 font-sans font-medium text-text-primary">Observation Dates &amp; Count</td>
                    <td className="p-3 text-center">
                      <div>{win1Y?.actualObservationCount ?? 0} / 250 obs</div>
                      <div className="text-[10px] text-text-tertiary">{win1Y?.observationStartDate || '—'} &rarr; {win1Y?.observationEndDate || '—'}</div>
                    </td>
                    <td className="p-3 text-center">
                      <div>{win3Y?.actualObservationCount ?? 0} / 750 obs</div>
                      <div className="text-[10px] text-text-tertiary">{win3Y?.observationStartDate || '—'} &rarr; {win3Y?.observationEndDate || '—'}</div>
                    </td>
                    <td className="p-3 text-center">
                      <div>{win5Y?.actualObservationCount ?? 0} / 1250 obs</div>
                      <div className="text-[10px] text-text-tertiary">{win5Y?.observationStartDate || '—'} &rarr; {win5Y?.observationEndDate || '—'}</div>
                    </td>
                    <td className="p-3 text-center bg-slate-100/50 dark:bg-slate-800/40 font-bold">
                      {win1Y?.status === 'AVAILABLE' && win3Y?.status === 'AVAILABLE' ? 'Directly Aligned' : 'Limited Coverage'}
                    </td>
                  </tr>

                  {/* Window Evidence State */}
                  <tr className="hover:bg-slate-50/50 dark:hover:bg-slate-800/30">
                    <td className="p-3 font-sans font-medium text-text-primary">Evidence State</td>
                    <td className="p-3 text-center">{renderEvidenceBadge(win1Y?.windowEvidenceState)}</td>
                    <td className="p-3 text-center">{renderEvidenceBadge(win3Y?.windowEvidenceState)}</td>
                    <td className="p-3 text-center">{renderEvidenceBadge(win5Y?.windowEvidenceState)}</td>
                    <td className="p-3 text-center bg-slate-100/50 dark:bg-slate-800/40">
                      {renderEvidenceBadge(sensitivity?.overallComparabilityState)}
                    </td>
                  </tr>

                  {/* Portfolio Volatility Proxy */}
                  <tr className="hover:bg-slate-50/50 dark:hover:bg-slate-800/30">
                    <td className="p-3 font-sans font-medium text-text-primary">Portfolio Volatility Proxy</td>
                    <td className="p-3 text-center font-bold text-primary dark:text-blue-400">
                      {win1Y?.annualizedPortfolioVolatilityProxy ? `${win1Y.annualizedPortfolioVolatilityProxy.toFixed(2)}%` : '—'}
                    </td>
                    <td className="p-3 text-center font-bold text-primary dark:text-blue-400">
                      {win3Y?.annualizedPortfolioVolatilityProxy ? `${win3Y.annualizedPortfolioVolatilityProxy.toFixed(2)}%` : '—'}
                    </td>
                    <td className="p-3 text-center font-bold text-primary dark:text-blue-400">
                      {win5Y?.annualizedPortfolioVolatilityProxy ? `${win5Y.annualizedPortfolioVolatilityProxy.toFixed(2)}%` : '—'}
                    </td>
                    <td className="p-3 text-center bg-slate-100/50 dark:bg-slate-800/40 font-bold">
                      {win1Y && win3Y && win1Y.status === 'AVAILABLE' && win3Y.status === 'AVAILABLE' ? (
                        <span className={win1Y.annualizedPortfolioVolatilityProxy > win3Y.annualizedPortfolioVolatilityProxy ? 'text-rose-600' : 'text-emerald-600'}>
                          {(win1Y.annualizedPortfolioVolatilityProxy - win3Y.annualizedPortfolioVolatilityProxy) > 0 ? '+' : ''}
                          {(win1Y.annualizedPortfolioVolatilityProxy - win3Y.annualizedPortfolioVolatilityProxy).toFixed(2)}%
                        </span>
                      ) : '—'}
                    </td>
                  </tr>

                  {/* Weighted Standalone Vol */}
                  <tr className="hover:bg-slate-50/50 dark:hover:bg-slate-800/30">
                    <td className="p-3 font-sans font-medium text-text-primary">Weighted Standalone Volatility</td>
                    <td className="p-3 text-center">{win1Y?.weightedStandaloneVolatilityPct ? `${win1Y.weightedStandaloneVolatilityPct.toFixed(2)}%` : '—'}</td>
                    <td className="p-3 text-center">{win3Y?.weightedStandaloneVolatilityPct ? `${win3Y.weightedStandaloneVolatilityPct.toFixed(2)}%` : '—'}</td>
                    <td className="p-3 text-center">{win5Y?.weightedStandaloneVolatilityPct ? `${win5Y.weightedStandaloneVolatilityPct.toFixed(2)}%` : '—'}</td>
                    <td className="p-3 text-center bg-slate-100/50 dark:bg-slate-800/40 text-text-secondary">
                      {win1Y && win3Y && win1Y.status === 'AVAILABLE' && win3Y.status === 'AVAILABLE' ? (
                        <span>{(win1Y.weightedStandaloneVolatilityPct - win3Y.weightedStandaloneVolatilityPct).toFixed(2)}%</span>
                      ) : '—'}
                    </td>
                  </tr>

                  {/* Diversification Benefit */}
                  <tr className="hover:bg-slate-50/50 dark:hover:bg-slate-800/30">
                    <td className="p-3 font-sans font-medium text-text-primary">Diversification Benefit (Reduction)</td>
                    <td className="p-3 text-center text-emerald-600 font-bold">
                      {win1Y?.diversificationEffectPct ? `+${win1Y.diversificationEffectPct.toFixed(2)}%` : '—'}
                    </td>
                    <td className="p-3 text-center text-emerald-600 font-bold">
                      {win3Y?.diversificationEffectPct ? `+${win3Y.diversificationEffectPct.toFixed(2)}%` : '—'}
                    </td>
                    <td className="p-3 text-center text-emerald-600 font-bold">
                      {win5Y?.diversificationEffectPct ? `+${win5Y.diversificationEffectPct.toFixed(2)}%` : '—'}
                    </td>
                    <td className="p-3 text-center bg-slate-100/50 dark:bg-slate-800/40 text-text-secondary">
                      {win1Y && win3Y && win1Y.status === 'AVAILABLE' && win3Y.status === 'AVAILABLE' ? (
                        <span>{(win1Y.diversificationEffectPct - win3Y.diversificationEffectPct).toFixed(2)}%</span>
                      ) : '—'}
                    </td>
                  </tr>

                  {/* Diversification Ratio */}
                  <tr className="hover:bg-slate-50/50 dark:hover:bg-slate-800/30">
                    <td className="p-3 font-sans font-medium text-text-primary">Diversification Ratio</td>
                    <td className="p-3 text-center">{win1Y?.diversificationRatio ? `${win1Y.diversificationRatio.toFixed(2)}x` : '—'}</td>
                    <td className="p-3 text-center">{win3Y?.diversificationRatio ? `${win3Y.diversificationRatio.toFixed(2)}x` : '—'}</td>
                    <td className="p-3 text-center">{win5Y?.diversificationRatio ? `${win5Y.diversificationRatio.toFixed(2)}x` : '—'}</td>
                    <td className="p-3 text-center bg-slate-100/50 dark:bg-slate-800/40 text-text-secondary">
                      {win1Y && win3Y && win1Y.status === 'AVAILABLE' && win3Y.status === 'AVAILABLE' ? (
                        <span>{(win1Y.diversificationRatio - win3Y.diversificationRatio).toFixed(2)}x</span>
                      ) : '—'}
                    </td>
                  </tr>

                  {/* Max Drawdown */}
                  <tr className="hover:bg-slate-50/50 dark:hover:bg-slate-800/30">
                    <td className="p-3 font-sans font-medium text-text-primary">Max Drawdown (RSK-03)</td>
                    <td className="p-3 text-center text-rose-600 font-bold">
                      {win1Y?.maximumDrawdownPct ? `${win1Y.maximumDrawdownPct.toFixed(2)}%` : '—'}
                      <div className="text-[10px] text-text-tertiary">{win1Y?.hasRecovered ? 'Recovered' : 'Unrecovered'}</div>
                    </td>
                    <td className="p-3 text-center text-rose-600 font-bold">
                      {win3Y?.maximumDrawdownPct ? `${win3Y.maximumDrawdownPct.toFixed(2)}%` : '—'}
                      <div className="text-[10px] text-text-tertiary">{win3Y?.hasRecovered ? 'Recovered' : 'Unrecovered'}</div>
                    </td>
                    <td className="p-3 text-center text-rose-600 font-bold">
                      {win5Y?.maximumDrawdownPct ? `${win5Y.maximumDrawdownPct.toFixed(2)}%` : '—'}
                      <div className="text-[10px] text-text-tertiary">{win5Y?.hasRecovered ? 'Recovered' : 'Unrecovered'}</div>
                    </td>
                    <td className="p-3 text-center bg-slate-100/50 dark:bg-slate-800/40 text-text-secondary">
                      {win1Y && win3Y && win1Y.status === 'AVAILABLE' && win3Y.status === 'AVAILABLE' ? (
                        <span>{(win1Y.maximumDrawdownPct - win3Y.maximumDrawdownPct).toFixed(2)}%</span>
                      ) : '—'}
                    </td>
                  </tr>

                  {/* Historical VaR 95% */}
                  <tr className="hover:bg-slate-50/50 dark:hover:bg-slate-800/30">
                    <td className="p-3 font-sans font-medium text-text-primary">Historical VaR 95% (RSK-06)</td>
                    <td className="p-3 text-center text-amber-600">
                      {win1Y?.historicalVaR95Pct ? `${win1Y.historicalVaR95Pct.toFixed(2)}%` : '—'}
                      <div className="text-[10px] text-text-tertiary">Tail: {win1Y?.effectiveTailObservationCount ?? 0} obs</div>
                    </td>
                    <td className="p-3 text-center text-amber-600">
                      {win3Y?.historicalVaR95Pct ? `${win3Y.historicalVaR95Pct.toFixed(2)}%` : '—'}
                      <div className="text-[10px] text-text-tertiary">Tail: {win3Y?.effectiveTailObservationCount ?? 0} obs</div>
                    </td>
                    <td className="p-3 text-center text-amber-600">
                      {win5Y?.historicalVaR95Pct ? `${win5Y.historicalVaR95Pct.toFixed(2)}%` : '—'}
                      <div className="text-[10px] text-text-tertiary">Tail: {win5Y?.effectiveTailObservationCount ?? 0} obs</div>
                    </td>
                    <td className="p-3 text-center bg-slate-100/50 dark:bg-slate-800/40 text-text-secondary">
                      {win1Y && win3Y && win1Y.status === 'AVAILABLE' && win3Y.status === 'AVAILABLE' ? (
                        <span>{(win1Y.historicalVaR95Pct - win3Y.historicalVaR95Pct).toFixed(2)}%</span>
                      ) : '—'}
                    </td>
                  </tr>

                  {/* Expected Shortfall 95% */}
                  <tr className="hover:bg-slate-50/50 dark:hover:bg-slate-800/30">
                    <td className="p-3 font-sans font-medium text-text-primary">Expected Shortfall 95% (RSK-07)</td>
                    <td className="p-3 text-center text-rose-600">
                      {win1Y?.historicalExpectedShortfall95Pct ? `${win1Y.historicalExpectedShortfall95Pct.toFixed(2)}%` : '—'}
                    </td>
                    <td className="p-3 text-center text-rose-600">
                      {win3Y?.historicalExpectedShortfall95Pct ? `${win3Y.historicalExpectedShortfall95Pct.toFixed(2)}%` : '—'}
                    </td>
                    <td className="p-3 text-center text-rose-600">
                      {win5Y?.historicalExpectedShortfall95Pct ? `${win5Y.historicalExpectedShortfall95Pct.toFixed(2)}%` : '—'}
                    </td>
                    <td className="p-3 text-center bg-slate-100/50 dark:bg-slate-800/40 text-text-secondary">
                      {win1Y && win3Y && win1Y.status === 'AVAILABLE' && win3Y.status === 'AVAILABLE' ? (
                        <span>{(win1Y.historicalExpectedShortfall95Pct - win3Y.historicalExpectedShortfall95Pct).toFixed(2)}%</span>
                      ) : '—'}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          {/* B. Volatility Sensitivity Comparisons */}
          <div className="space-y-3">
            <h5 className="text-xs font-semibold text-text-secondary uppercase tracking-wider font-mono">
              B. Volatility Lookback Sensitivity &amp; Shift Classifications
            </h5>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              {sensitivity?.volatilityComparisons?.map((vc) => (
                <div key={vc.comparisonPair} className="p-4 bg-surface border border-border dark:border-slate-800 rounded-lg space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-text-primary font-mono">{vc.windowA} vs {vc.windowB}</span>
                    {renderEvidenceBadge(vc.shiftClassification)}
                  </div>
                  <div className="flex items-baseline gap-2 mt-1">
                    <span className="text-xl font-bold font-mono text-text-primary">
                      {vc.absoluteDifferencePct !== null && vc.absoluteDifferencePct !== undefined ? (
                        <span className={vc.absoluteDifferencePct > 0 ? 'text-rose-600' : 'text-emerald-600'}>
                          {vc.absoluteDifferencePct > 0 ? '+' : ''}{vc.absoluteDifferencePct.toFixed(2)}%
                        </span>
                      ) : '—'}
                    </span>
                    {vc.relativeChangePct !== null && vc.relativeChangePct !== undefined && (
                      <span className="text-xs font-mono text-text-tertiary">
                        ({vc.relativeChangePct > 0 ? '+' : ''}{vc.relativeChangePct.toFixed(1)}% rel)
                      </span>
                    )}
                  </div>
                  <p className="text-[11px] text-text-secondary leading-normal">
                    {vc.explanation}
                  </p>
                </div>
              ))}
            </div>
          </div>

          {/* C. Fund Risk Contribution Sensitivity Across Horizons */}
          <div className="space-y-3">
            <h5 className="text-xs font-semibold text-text-secondary uppercase tracking-wider font-mono">
              C. Fund Risk Contribution Sensitivity &amp; Dynamic Behavior
            </h5>

            <div className="overflow-x-auto border border-border dark:border-slate-800 rounded-lg">
              <table className="w-full text-xs text-left border-collapse font-mono">
                <thead>
                  <tr className="bg-slate-50 dark:bg-slate-800/70 border-b border-border dark:border-slate-800 text-text-secondary">
                    <th className="p-3 text-left font-sans font-semibold">Fund / Scheme</th>
                    <th className="p-3 text-right">Weight</th>
                    <th className="p-3 text-right">1Y %RC</th>
                    <th className="p-3 text-right">3Y %RC</th>
                    <th className="p-3 text-right">5Y %RC</th>
                    <th className="p-3 text-right">&Delta; (1Y vs 3Y)</th>
                    <th className="p-3 text-center">Horizon Behavior</th>
                    <th className="p-3 text-left font-sans font-normal">Sensitivity Note</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border dark:divide-slate-800">
                  {sensitivity?.contributionComparisons?.map((c) => (
                    <tr key={c.schemeOptionId} className="hover:bg-slate-50/50 dark:hover:bg-slate-800/30">
                      <td className="p-3 font-sans font-medium text-text-primary whitespace-nowrap">
                        {c.fundName}
                      </td>
                      <td className="p-3 text-right font-bold">{c.allocationWeightPct.toFixed(2)}%</td>
                      <td className="p-3 text-right font-bold text-primary dark:text-blue-400">
                        {c.percentageRiskContribution1Y !== null && c.percentageRiskContribution1Y !== undefined ? `${c.percentageRiskContribution1Y.toFixed(2)}%` : '—'}
                      </td>
                      <td className="p-3 text-right font-bold text-primary dark:text-blue-400">
                        {c.percentageRiskContribution3Y !== null && c.percentageRiskContribution3Y !== undefined ? `${c.percentageRiskContribution3Y.toFixed(2)}%` : '—'}
                      </td>
                      <td className="p-3 text-right font-bold text-primary dark:text-blue-400">
                        {c.percentageRiskContribution5Y !== null && c.percentageRiskContribution5Y !== undefined ? `${c.percentageRiskContribution5Y.toFixed(2)}%` : '—'}
                      </td>
                      <td className="p-3 text-right font-bold">
                        {c.change1Yto3Y !== null && c.change1Yto3Y !== undefined ? (
                          <span className={c.change1Yto3Y > 0 ? 'text-rose-600' : 'text-emerald-600'}>
                            {c.change1Yto3Y > 0 ? '+' : ''}{c.change1Yto3Y.toFixed(2)}%
                          </span>
                        ) : '—'}
                      </td>
                      <td className="p-3 text-center">
                        <span className={`px-2 py-0.5 rounded text-[10px] font-semibold border ${
                          c.classification1Y === 'UNAVAILABLE' || c.classification3Y === 'UNAVAILABLE'
                            ? 'bg-slate-500/15 text-slate-500 border-slate-500/30'
                            : c.hasSignFlipped
                            ? 'bg-purple-500/15 text-purple-600 border-purple-500/30'
                            : c.hasClassificationShifted
                            ? 'bg-amber-500/15 text-amber-600 border-amber-500/30'
                            : 'bg-emerald-500/15 text-emerald-600 border-emerald-500/30'
                        }`}>
                          {c.classification1Y === 'UNAVAILABLE' || c.classification3Y === 'UNAVAILABLE'
                            ? 'UNAVAILABLE'
                            : c.hasSignFlipped
                            ? 'SIGN FLIP'
                            : (c.hasClassificationShifted ? 'CLASSIFICATION SHIFT' : 'CONSISTENT ROLE')}
                        </span>
                      </td>
                      <td className="p-3 font-sans text-[11px] text-text-tertiary leading-normal">
                        {c.sensitivityNote}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* D. Correlation Sensitivity Across Horizons */}
          <div className="space-y-3">
            <h5 className="text-xs font-semibold text-text-secondary uppercase tracking-wider font-mono">
              D. Pairwise Correlation Sensitivity Across Horizons
            </h5>

            <div className="overflow-x-auto border border-border dark:border-slate-800 rounded-lg">
              <table className="w-full text-xs text-left border-collapse font-mono">
                <thead>
                  <tr className="bg-slate-50 dark:bg-slate-800/70 border-b border-border dark:border-slate-800 text-text-secondary">
                    <th className="p-3 text-left font-sans font-semibold">Fund Pair</th>
                    <th className="p-3 text-center">1Y Correlation</th>
                    <th className="p-3 text-center">3Y Correlation</th>
                    <th className="p-3 text-center">5Y Correlation</th>
                    <th className="p-3 text-center">&Delta; (1Y vs 3Y)</th>
                    <th className="p-3 text-center">Sensitivity Flag</th>
                    <th className="p-3 text-left font-sans font-normal">Interpretation</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border dark:divide-slate-800">
                  {sensitivity?.correlationComparisons?.map((cp, idx) => (
                    <tr key={idx} className="hover:bg-slate-50/50 dark:hover:bg-slate-800/30">
                      <td className="p-3 font-sans font-medium text-text-primary whitespace-nowrap">
                        {cp.fundNameA} &harr; {cp.fundNameB}
                      </td>
                      <td className="p-3 text-center">{cp.correlation1Y !== null && cp.correlation1Y !== undefined ? cp.correlation1Y.toFixed(2) : '—'}</td>
                      <td className="p-3 text-center">{cp.correlation3Y !== null && cp.correlation3Y !== undefined ? cp.correlation3Y.toFixed(2) : '—'}</td>
                      <td className="p-3 text-center">{cp.correlation5Y !== null && cp.correlation5Y !== undefined ? cp.correlation5Y.toFixed(2) : '—'}</td>
                      <td className="p-3 text-center font-bold">
                        {cp.change1Yto3Y !== null && cp.change1Yto3Y !== undefined ? (
                          <span className={Math.abs(cp.change1Yto3Y) >= 0.15 ? 'text-rose-600' : 'text-text-secondary'}>
                            {cp.change1Yto3Y > 0 ? '+' : ''}{cp.change1Yto3Y.toFixed(2)}
                          </span>
                        ) : '—'}
                      </td>
                      <td className="p-3 text-center">
                        <span className={`px-2 py-0.5 rounded text-[10px] font-semibold border ${
                          cp.correlation1Y === null || cp.correlation3Y === null
                            ? 'bg-slate-500/15 text-slate-500 border-slate-500/30'
                            : cp.isMateriallySensitive
                            ? 'bg-rose-500/15 text-rose-600 border-rose-500/30'
                            : cp.hasClassificationChanged
                            ? 'bg-amber-500/15 text-amber-600 border-amber-500/30'
                            : 'bg-emerald-500/15 text-emerald-600 border-emerald-500/30'
                        }`}>
                          {cp.correlation1Y === null || cp.correlation3Y === null
                            ? 'UNAVAILABLE'
                            : cp.isMateriallySensitive
                            ? 'MATERIAL SHIFT'
                            : (cp.hasClassificationChanged ? 'CLASSIFICATION SHIFT' : 'STABLE')}
                        </span>
                      </td>
                      <td className="p-3 font-sans text-[11px] text-text-tertiary leading-normal">
                        {cp.interpretation}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* E. Comparability Limitations & Methodological Guardrails */}
          <div className="p-4 rounded-lg bg-slate-50 dark:bg-slate-800/40 border border-border dark:border-slate-800 space-y-2 text-xs">
            <div className="font-semibold text-text-primary">
              Comparability Limitations &amp; Methodological Rules:
            </div>
            <ul className="list-disc pl-5 space-y-1 text-text-secondary">
              {sensitivity?.comparabilityLimitations?.map((lim, idx) => (
                <li key={idx}>{lim}</li>
              ))}
            </ul>
          </div>
        </div>
      )}

      {/* ------------------------------------------------------------------- */}
      {/* 9. SUBSECTION G: HISTORICAL STRESS ANALYSIS */}
      {/* ------------------------------------------------------------------- */}
      {!loading && data && data.evaluationStatus === 'SUCCESS' && activeTab === 'stress' && (() => {
        const stress = data.historicalStressAnalysis;
        const bmRef = stress?.benchmarkReference;
        const summary = stress?.summary;
        const episodes = stress?.episodes || [];
        const allocationSnapshot = stress?.allocationSnapshot;
        const calculationContext = stress?.calculationContext;

        const filteredEpisodes = episodes.filter((ep) => {
          if (stressFilterBy === 'COMPUTABLE_ONLY') {
            return ep.calculationEligibilityOutcome === 'ELIGIBLE_COMPLETE_WINDOW' ||
                   ep.calculationEligibilityOutcome === 'ELIGIBLE_COMMON_DATE_SUBSET' ||
                   ep.isPortfolioReturnComputable;
          }
          if (stressFilterBy === 'WITHHELD_ONLY') {
            return (ep.calculationEligibilityOutcome && ep.calculationEligibilityOutcome.startsWith('WITHHELD')) || !ep.isPortfolioReturnComputable;
          }
          if (stressFilterBy === 'RECOVERED_ONLY') {
            return ep.episodeLifecycleState === 'RECOVERED' || ep.portfolioHasRecovered;
          }
          if (stressFilterBy === 'UNRECOVERED_ONLY') {
            return ep.episodeLifecycleState === 'ONGOING_RECOVERY' ||
                   ep.episodeLifecycleState === 'ACTIVE_DECLINE' ||
                   ep.episodeLifecycleState === 'UNRECOVERED_CUTOFF_REACHED' ||
                   !ep.portfolioHasRecovered;
          }
          return true;
        });

        const sortedEpisodes = [...filteredEpisodes].sort((a, b) => {
          if (stressSortBy === 'CHRONO_DESC') {
            return (b.startDate || '').localeCompare(a.startDate || '');
          }
          if (stressSortBy === 'CHRONO_ASC') {
            return (a.startDate || '').localeCompare(b.startDate || '');
          }
          if (stressSortBy === 'SEVERITY_DESC') {
            return (b.benchmarkMaxDrawdownPct || 0) - (a.benchmarkMaxDrawdownPct || 0);
          }
          if (stressSortBy === 'BENCHMARK_DESC') {
            return (b.benchmarkCumulativeReturnPct || 0) - (a.benchmarkCumulativeReturnPct || 0);
          }
          return 0;
        });

        const activeEp = episodes.find(e => e.episodeId === selectedStressEpisodeId) || sortedEpisodes[0] || episodes[0];

        const totalWeight = activeEp?.fundContributions?.reduce((acc, fc) => acc + (fc.currentAllocationWeightPct || 0), 0) || 0;
        const totalExactContrib = activeEp?.fundContributions?.reduce((acc, fc) => acc + (fc.exactAdditiveReturnContributionPct || 0), 0) || 0;
        const totalApproxContrib = activeEp?.fundContributions?.reduce((acc, fc) => acc + (fc.approximateWeightedReturnIndicatorPct || 0), 0) || 0;
        const totalInteraction = activeEp?.fundContributions?.reduce((acc, fc) => acc + (fc.rebalancingInteractionPct || 0), 0) || 0;
        const totalDiff = activeEp?.fundContributions?.reduce((acc, fc) => acc + (fc.weightedReturnToLinkedAttributionDifferencePct ?? fc.rebalancingInteractionPct ?? 0), 0) || 0;

        return (
          <div className="space-y-6">
            {/* Header & Epistemic Disclaimer Banner */}
            <div className="space-y-2">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <h4 className="text-sm font-semibold text-text-primary flex items-center gap-2">
                    <span>Historical Portfolio Stress-Period Analysis</span>
                    <span className="px-2 py-0.5 rounded text-[10px] font-mono bg-purple-500/15 text-purple-600 dark:text-purple-400 border border-purple-500/30">
                      Constant-Weight Daily Rebalanced Proxy
                    </span>
                    <span className="px-2 py-0.5 rounded text-[10px] font-mono bg-blue-500/15 text-blue-600 dark:text-blue-400 border border-blue-500/30">
                      Carino Exact Additive Attribution
                    </span>
                    <span className="px-2 py-0.5 rounded text-[10px] font-mono bg-amber-500/15 text-amber-600 dark:text-amber-400 border border-amber-500/30">
                      Candidate Methodology ({stress?.methodologyVersion || 'PORTFOLIO_STRESS_METHODOLOGY_V2'})
                    </span>
                  </h4>
                  <p className="text-xs text-text-tertiary">
                    Evaluates how current portfolio allocation weights would have behaved across actual historical market stress episodes (&ge; 10.0% benchmark peak-to-trough drawdown).
                  </p>
                </div>
                <div className="text-[11px] font-mono text-text-secondary flex flex-wrap items-center gap-3">
                  <span>Run ID: <strong className="text-text-primary">{stress?.calculationRunId ? `${stress.calculationRunId.substring(0, 8)}...` : 'N/A'}</strong></span>
                  {stress?.calculationInputFingerprint && (
                    <span>Input Hash: <strong className="text-text-primary" title={stress.calculationInputFingerprint}>{`${stress.calculationInputFingerprint.substring(0, 8)}...`}</strong></span>
                  )}
                  <span>As-Of: <strong className="text-text-primary">{stress?.asOfDate || 'N/A'}</strong></span>
                  <span>Knowledge Cutoff: <strong className="text-text-primary">{stress?.knowledgeCutoff ? stress.knowledgeCutoff.substring(0, 10) : 'N/A'}</strong></span>
                  <span>Comparability: {renderEvidenceBadge(stress?.overallComparabilityState || 'DIRECTLY_COMPARABLE')}</span>
                </div>
              </div>

              {/* Static Current-Allocation Historical Proxy Notice & Snapshot Provenance (Task 48) */}
              <div className="p-3.5 rounded-lg bg-amber-500/10 border border-amber-500/30 text-amber-900 dark:text-amber-200 text-xs space-y-2">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <span className="font-bold uppercase tracking-wider text-amber-800 dark:text-amber-300">
                      Static Current-Allocation Historical Proxy Notice
                    </span>
                    <span className="px-2 py-0.5 rounded text-[10px] font-mono bg-amber-500/20 text-amber-700 dark:text-amber-300 border border-amber-500/40">
                      Snapshot ID: {allocationSnapshot?.snapshotId ? `${allocationSnapshot.snapshotId.substring(0, 10)}...` : (stress?.portfolioAllocationSnapshotId ? `${stress.portfolioAllocationSnapshotId.substring(0, 10)}...` : 'CURRENT')}
                    </span>
                  </div>
                  <div className="text-[11px] font-mono text-amber-800 dark:text-amber-300 flex items-center gap-3">
                    <span>Effective As-Of: <strong>{allocationSnapshot?.effectiveAsOfDate || stress?.asOfDate || 'N/A'}</strong></span>
                    <span>Valued Allocation: <strong>{allocationSnapshot?.totalValuedAllocationPercentage != null ? `${allocationSnapshot.totalValuedAllocationPercentage.toFixed(2)}%` : '100.00%'}</strong></span>
                    <button
                      onClick={handleExportStressEvidence}
                      disabled={exportingStressEvidence}
                      className="px-2.5 py-1 rounded bg-surface dark:bg-slate-800 border border-amber-500/40 hover:border-amber-600 font-sans font-semibold text-[11px] text-text-primary flex items-center gap-1.5 transition disabled:opacity-50"
                      title="Export complete structured audit record for historical stress analysis (Task 58)"
                    >
                      <span>{exportingStressEvidence ? 'Exporting...' : 'Export Stress Evidence Record (JSON)'}</span>
                    </button>
                  </div>
                </div>
                <div className="text-[11px] leading-relaxed text-amber-800 dark:text-amber-300">
                  {allocationSnapshot?.staticProxyDisclaimer || 'This stress analysis evaluates current portfolio weights back-cast through historical market stress events. It assumes constant-weight daily rebalancing and does NOT represent actual past performance, past trade execution, or historical holdings changes.'}
                </div>
                <div className="flex flex-wrap items-center gap-4 text-[10px] font-mono text-amber-700 dark:text-amber-400">
                  <span>Valued Positions: {allocationSnapshot?.valuedPositionsCount ?? 0}</span>
                  <span>Unvalued Positions: {allocationSnapshot?.unvaluedPositionsCount ?? 0}</span>
                  <span>Denominator: {allocationSnapshot?.weightDerivationDenominator || 'TOTAL_VALUED_CAPITAL'}</span>
                </div>
              </div>

              <div className="p-3 rounded-lg bg-blue-500/10 border border-blue-500/30 text-blue-800 dark:text-blue-300 text-xs space-y-1">
                <div>
                  <strong>Constant-Weight Daily Rebalanced Historical Stress Proxy Mandate:</strong> This analysis applies today&apos;s known capital allocation weights consistently across historical market drawdowns, assuming daily constant-weight rebalancing back to initial weights (<em>R<sub>p,t</sub> = &sum; w<sub>i</sub> R<sub>i,t</sub></em>). This is a mathematical historical proxy, <em>not</em> a reconstruction of actual realized investor transactions, cash flows, taxes, or historical account performance.
                </div>
                <div className="text-[11px] text-blue-700 dark:text-blue-400">
                  Constituent multi-period return attribution is linked via <strong>Carino Logarithmic Linking</strong>, guaranteeing exact additive decomposition (&sum; A<sub>i</sub> = R<sub>p,cum</sub>) within &plusmn;0.0001% tolerance without compounding gaps.
                </div>
              </div>
            </div>

            {/* LEVEL 1: Benchmark Reference & Consolidated Summary Cards */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {/* Benchmark Reference Card */}
              <div className="p-4 rounded-lg bg-surface border border-border dark:border-slate-800 space-y-3">
                <div className="flex items-center justify-between">
                  <div className="text-xs font-semibold text-text-primary flex items-center gap-2">
                    <span>Market-Stress Reference Benchmark</span>
                    {bmRef?.isFallbackBenchmark && (
                      <span className="px-1.5 py-0.5 rounded text-[10px] font-mono bg-amber-500/15 text-amber-600 dark:text-amber-400 border border-amber-500/30">
                        Fallback Benchmark ({bmRef?.benchmarkCode || 'NIFTY_50_TRI'})
                      </span>
                    )}
                  </div>
                  {renderEvidenceBadge(bmRef?.status || 'AVAILABLE')}
                </div>
                <div className="space-y-1.5 text-xs">
                  <div className="flex justify-between">
                    <span className="text-text-tertiary">Benchmark Code:</span>
                    <span className="font-mono font-medium text-text-primary">{bmRef?.benchmarkCode || 'NIFTY_500_TRI'}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-text-tertiary">Benchmark Name:</span>
                    <span className="font-medium text-text-primary">{bmRef?.benchmarkName || 'NIFTY 500 Total Returns Index'}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-text-tertiary">Series Type:</span>
                    <span className="font-mono text-text-primary">{bmRef?.benchmarkSeriesType || 'TOTAL_RETURNS_INDEX (TRI)'}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-text-tertiary">Index Provider:</span>
                    <span className="text-text-secondary">{bmRef?.provider || 'NSE Indices Limited'}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-text-tertiary">Authoritative Window:</span>
                    <span className="font-mono text-text-secondary">
                      {bmRef?.minObservationDate ? `${bmRef.minObservationDate} to ${bmRef.maxObservationDate} (${bmRef.totalObservationCount} days)` : 'No observations loaded'}
                    </span>
                  </div>
                  {bmRef?.observationCoverageRatioPct != null && (
                    <div className="flex justify-between">
                      <span className="text-text-tertiary">Observation Coverage:</span>
                      <span className="font-mono text-emerald-600 dark:text-emerald-400">
                        {(bmRef.observationCoverageRatioPct * 100).toFixed(1)}%
                      </span>
                    </div>
                  )}
                </div>

                {bmRef?.isFallbackBenchmark && (
                  <div className="p-2.5 rounded bg-amber-500/10 border border-amber-500/30 text-amber-800 dark:text-amber-300 text-[11px] space-y-1">
                    <div>
                      <strong>Benchmark Fallback Active:</strong> {bmRef.fallbackReason || 'Primary broad benchmark NIFTY_500_TRI had insufficient historical observation coverage; falling back to NIFTY_50_TRI.'}
                    </div>
                    {bmRef.comparabilityNotice && (
                      <div className="text-[10px] text-amber-700 dark:text-amber-400">
                        {bmRef.comparabilityNotice}
                      </div>
                    )}
                  </div>
                )}

                <p className="text-[11px] text-text-tertiary border-t border-border dark:border-slate-800 pt-2">
                  * Broad equity TRI benchmark is used strictly to identify broad market stress episodes; it is not automatically the performance benchmark for each individual constituent fund.
                </p>
              </div>

              {/* Stress Analysis Summary Card */}
              <div className="p-4 rounded-lg bg-surface border border-border dark:border-slate-800 space-y-3">
                <div className="flex items-center justify-between">
                  <div className="text-xs font-semibold text-text-primary flex items-center gap-2">
                    <span>Consolidated Stress Summary</span>
                    <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-slate-500/10 text-slate-600 dark:text-slate-400 border border-slate-500/20">
                      {summary?.aggregationConvention ? summary.aggregationConvention.replace(/_/g, ' ') : 'Arithmetic Episode Mean'}
                    </span>
                  </div>
                  <span className="text-xs font-mono text-text-secondary">
                    {summary?.episodesEligible ?? summary?.totalEpisodesEvaluated ?? 0} Episodes Evaluated
                  </span>
                </div>

                {/* Benchmark Fallback Multi-Episode Disclosure */}
                {(summary?.episodesWithFallbackBenchmark ?? 0) > 0 && (
                  <div className="p-2.5 rounded bg-amber-500/10 border border-amber-500/30 text-amber-800 dark:text-amber-300 text-[11px] font-mono">
                    <strong>Benchmark Fallback Alert:</strong> {summary?.episodesWithFallbackBenchmark} episode(s) evaluated against fallback benchmark ({bmRef?.benchmarkCode || 'NIFTY_50_TRI'}). Fallback reason: {bmRef?.fallbackReason || 'Primary NIFTY_500_TRI insufficient coverage'}. Episodes using different benchmarks are NOT directly comparable (§37 AGENTS.md).
                  </div>
                )}

                <div className="grid grid-cols-2 gap-2 text-xs">
                  <div className="p-2.5 rounded bg-slate-50 dark:bg-slate-800/50 border border-border dark:border-slate-800">
                    <div className="text-[11px] text-text-tertiary">Worst Proxy Drawdown</div>
                    <div className="text-sm font-semibold font-mono text-rose-600 dark:text-rose-400">
                      {summary?.worstPortfolioDrawdownPct != null ? `${summary.worstPortfolioDrawdownPct.toFixed(2)}%` : 'Withheld (Incomplete Data)'}
                    </div>
                  </div>
                  <div className="p-2.5 rounded bg-slate-50 dark:bg-slate-800/50 border border-border dark:border-slate-800">
                    <div className="text-[11px] text-text-tertiary">Avg Stress Return</div>
                    <div className={`text-sm font-semibold font-mono ${(summary?.averageStressCumulativeReturnPct ?? 0) >= 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'}`}>
                      {summary?.averageStressCumulativeReturnPct != null ? `${summary.averageStressCumulativeReturnPct > 0 ? '+' : ''}${summary.averageStressCumulativeReturnPct.toFixed(2)}%` : 'Withheld (Incomplete Data)'}
                    </div>
                  </div>
                  <div className="p-2.5 rounded bg-slate-50 dark:bg-slate-800/50 border border-border dark:border-slate-800">
                    <div className="text-[11px] text-text-tertiary">Most Resilient Fund</div>
                    <div className="text-xs font-medium text-text-primary truncate" title={summary?.mostResilientFundName}>
                      {summary?.mostResilientFundName || 'N/A'}
                    </div>
                  </div>
                  <div className="p-2.5 rounded bg-slate-50 dark:bg-slate-800/50 border border-border dark:border-slate-800">
                    <div className="text-[11px] text-text-tertiary">Greatest Detractor</div>
                    <div className="text-xs font-medium text-text-primary truncate" title={summary?.greatestDetractorFundName}>
                      {summary?.greatestDetractorFundName || 'N/A'}
                    </div>
                  </div>
                </div>

                {/* Evidence Adequacy & Explicit Denominator Accounting */}
                <div className="space-y-1 pt-1 border-t border-border dark:border-slate-800 text-[11px] font-mono">
                  <div className="flex flex-wrap items-center justify-between gap-1 text-text-secondary">
                    <span>Eligible: <strong className="text-text-primary">{summary?.episodesEligible ?? summary?.totalEpisodesEvaluated ?? 0}</strong></span>
                    <span>Computable: <strong className="text-emerald-600 dark:text-emerald-400">{summary?.episodesComputable ?? (summary?.episodesSufficientEvidence ?? 0) + (summary?.episodesLimitedEvidence ?? 0)}</strong></span>
                    <span>Withheld: <strong className="text-orange-500">{summary?.episodesWithheld ?? 0}</strong></span>
                    <span>Unavailable: <strong className="text-rose-500">{summary?.episodesUnavailable ?? summary?.episodesInsufficientEvidence ?? 0}</strong></span>
                  </div>
                  <div className="flex flex-wrap items-center justify-between gap-1 text-[10px]">
                    <span className="text-emerald-600 dark:text-emerald-400">
                      Complete Window: {summary?.episodesCompleteWindow ?? 0}
                    </span>
                    <span className="text-blue-600 dark:text-blue-400">
                      Common Subset: {summary?.episodesCommonDateSubset ?? 0}
                    </span>
                    <span className="text-amber-600 dark:text-amber-400">
                      Limited Evidence: {summary?.episodesLimitedEvidence ?? 0}
                    </span>
                  </div>
                  {summary?.aggregationDenominator && (
                    <div className="text-[10px] text-text-tertiary">
                      Multi-Episode Denominator: <strong className="text-text-secondary">{summary.aggregationDenominator}</strong>
                    </div>
                  )}
                </div>

                {/* Sub-Group Comparability Accounting (Task 69) */}
                <div className="pt-2 border-t border-border dark:border-slate-800 space-y-1.5 text-[11px] font-mono">
                  <div className="font-semibold text-text-primary text-xs font-sans">
                    Comparability Sub-Group Breakdown:
                  </div>
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
                    <div className="p-2 rounded bg-slate-50 dark:bg-slate-800/40 border border-border dark:border-slate-800">
                      <div className="text-[10px] text-text-tertiary font-sans">Direct Primary Benchmark</div>
                      <div className="font-semibold text-text-primary">
                        {summary?.directlyComparableEpisodesCount ?? 0} eps
                      </div>
                      <div className="text-[10px] text-emerald-600 dark:text-emerald-400">
                        Avg: {summary?.directlyComparableAverageReturnPct != null ? `${summary.directlyComparableAverageReturnPct > 0 ? '+' : ''}${summary.directlyComparableAverageReturnPct.toFixed(2)}%` : 'N/A'}
                      </div>
                    </div>
                    <div className="p-2 rounded bg-slate-50 dark:bg-slate-800/40 border border-border dark:border-slate-800">
                      <div className="text-[10px] text-text-tertiary font-sans">Fallback Benchmark</div>
                      <div className="font-semibold text-text-primary">
                        {summary?.fallbackBenchmarkEpisodesCount ?? 0} eps
                      </div>
                      <div className="text-[10px] text-blue-600 dark:text-blue-400">
                        Avg: {summary?.fallbackBenchmarkAverageReturnPct != null ? `${summary.fallbackBenchmarkAverageReturnPct > 0 ? '+' : ''}${summary.fallbackBenchmarkAverageReturnPct.toFixed(2)}%` : 'N/A'}
                      </div>
                    </div>
                    <div className="p-2 rounded bg-slate-50 dark:bg-slate-800/40 border border-border dark:border-slate-800">
                      <div className="text-[10px] text-text-tertiary font-sans">Common-Date Subset</div>
                      <div className="font-semibold text-text-primary">
                        {summary?.commonDateSubsetEpisodesCount ?? 0} eps
                      </div>
                      <div className="text-[10px] text-amber-600 dark:text-amber-400">
                        Avg: {summary?.commonDateSubsetAverageReturnPct != null ? `${summary.commonDateSubsetAverageReturnPct > 0 ? '+' : ''}${summary.commonDateSubsetAverageReturnPct.toFixed(2)}%` : 'N/A'}
                      </div>
                    </div>
                  </div>
                  {summary?.groupingExplanation && (
                    <div className="text-[10px] text-text-tertiary leading-tight pt-0.5 font-sans">
                      {summary.groupingExplanation}
                    </div>
                  )}
                </div>

                {summary?.comparabilityLimitations && (
                  <div className="p-2 rounded bg-amber-500/10 border border-amber-500/30 text-amber-800 dark:text-amber-300 text-[11px] font-mono">
                    <strong>Comparability Limitations:</strong> {summary.comparabilityLimitations}
                  </div>
                )}

                {summary?.comparabilityNotice && (
                  <div className="p-2 rounded bg-amber-500/10 border border-amber-500/30 text-amber-800 dark:text-amber-300 text-[11px]">
                    {summary.comparabilityNotice}
                  </div>
                )}

                {summary?.overallStressAssessment && (
                  <p className="text-[11px] text-text-secondary border-t border-border dark:border-slate-800 pt-2">
                    {summary.overallStressAssessment}
                  </p>
                )}
              </div>
            </div>

            {/* Episode Selector & Deep Dive */}
            {episodes.length > 0 ? (
              <div className="space-y-4">
                <div>
                  <div className="flex flex-wrap items-center justify-between gap-2 mb-2">
                    <div className="text-xs font-semibold text-text-primary">
                      Select Historical Stress Episode ({sortedEpisodes.length} of {episodes.length}):
                    </div>
                    <div className="flex flex-wrap items-center gap-2">
                      {/* Sort Control (Task 56) */}
                      <div className="flex items-center gap-1 text-[11px]">
                        <span className="text-text-tertiary">Sort:</span>
                        <select
                          value={stressSortBy}
                          onChange={(e) => setStressSortBy(e.target.value as any)}
                          className="bg-surface dark:bg-slate-800 border border-border dark:border-slate-700 rounded px-2 py-0.5 text-xs text-text-primary font-sans"
                        >
                          <option value="CHRONO_DESC">Chronological (Newest First)</option>
                          <option value="CHRONO_ASC">Chronological (Oldest First)</option>
                          <option value="SEVERITY_DESC">Severity (Worst Drawdown)</option>
                          <option value="BENCHMARK_DESC">Benchmark Return (Worst Loss)</option>
                        </select>
                      </div>
                      {/* Filter Control (Task 56) */}
                      <div className="flex items-center gap-1 text-[11px]">
                        <span className="text-text-tertiary">Filter:</span>
                        <select
                          value={stressFilterBy}
                          onChange={(e) => setStressFilterBy(e.target.value as any)}
                          className="bg-surface dark:bg-slate-800 border border-border dark:border-slate-700 rounded px-2 py-0.5 text-xs text-text-primary font-sans"
                        >
                          <option value="ALL">All Episodes ({episodes.length})</option>
                          <option value="COMPUTABLE_ONLY">Computable Only</option>
                          <option value="WITHHELD_ONLY">Withheld / Incomplete Only</option>
                          <option value="RECOVERED_ONLY">Recovered Only</option>
                          <option value="UNRECOVERED_ONLY">Unrecovered / Ongoing Only</option>
                        </select>
                      </div>
                    </div>
                  </div>

                  <div className="flex flex-wrap gap-2">
                    {sortedEpisodes.map((ep) => (
                      <button
                        key={ep.episodeId}
                        onClick={() => setSelectedStressEpisodeId(ep.episodeId)}
                        className={`p-3 rounded-lg border text-left transition flex flex-col gap-1 min-w-[220px] flex-1 ${
                          (activeEp?.episodeId === ep.episodeId)
                            ? 'bg-primary/10 border-primary text-text-primary shadow-sm'
                            : 'bg-surface border-border dark:border-slate-800 text-text-secondary hover:border-slate-400 dark:hover:border-slate-700'
                        }`}
                      >
                        <div className="flex items-center justify-between gap-1">
                          <span className="font-semibold text-xs text-text-primary truncate">
                            {ep.episodeName}
                          </span>
                          <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-rose-500/15 text-rose-600 dark:text-rose-400 font-bold">
                            {ep.benchmarkMaxDrawdownPct?.toFixed(1)}%
                          </span>
                        </div>
                        <div className="text-[10px] font-mono text-text-tertiary truncate" title={ep.episodeId}>
                          ID: {ep.episodeId}
                        </div>
                        {ep.legacyEpisodeId && ep.legacyEpisodeId !== ep.episodeId && (
                          <div className="text-[9px] font-mono text-text-tertiary/70 truncate" title={`Legacy ID: ${ep.legacyEpisodeId}`}>
                            Legacy: {ep.legacyEpisodeId}
                          </div>
                        )}
                        <div className="text-[11px] text-text-tertiary font-mono">
                          {ep.startDate} &rarr; {ep.endDate}
                        </div>
                        <div className="flex flex-wrap items-center gap-1 mt-1">
                          {renderLifecycleBadge(ep.episodeLifecycleState)}
                          {renderEligibilityBadge(ep.calculationEligibilityOutcome)}
                          {renderEvidenceBadge(ep.evidenceState)}
                          {renderRecoveryComparisonBadge(ep.recoveryComparison || ep.recoveryState)}
                          {ep.comparabilityState && renderEvidenceBadge(ep.comparabilityState)}
                        </div>
                      </button>
                    ))}
                  </div>
                </div>

                {/* LEVEL 2: Active Episode Detail Container */}
                {activeEp && (
                  <div className="p-4 rounded-lg bg-surface border border-border dark:border-slate-800 space-y-5">
                    {/* Active Episode Header */}
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-border dark:border-slate-800 pb-3">
                      <div>
                        <div className="flex flex-wrap items-center gap-2">
                          <h5 className="text-sm font-semibold text-text-primary">
                            {activeEp.episodeName}
                          </h5>
                          {renderLifecycleBadge(activeEp.episodeLifecycleState)}
                          {renderEligibilityBadge(activeEp.calculationEligibilityOutcome)}
                          {renderEvidenceBadge(activeEp.evidenceState)}
                          <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-slate-500/10 text-slate-600 dark:text-slate-400 border border-slate-500/20">
                            {activeEp.regimeCategory}
                          </span>
                          <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-purple-500/10 text-purple-600 dark:text-purple-400 border border-purple-500/20">
                            {activeEp.stressIdentificationPolicyVersion || 'POLICY_V2'}
                          </span>
                          {activeEp.numericalValidityState && (
                            <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-slate-500/10 text-slate-600 dark:text-slate-400 border border-slate-500/20">
                              {activeEp.numericalValidityState}
                            </span>
                          )}
                          {activeEp.hasReducedCommonDateSubset && (
                            <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-blue-500/15 text-blue-600 dark:text-blue-400 border border-blue-500/30">
                              Common-Date Subset ({activeEp.usableCommonTradingDaysCount ?? activeEp.usableObservationCount}/{activeEp.expectedBenchmarkTradingDays ?? activeEp.expectedObservationCount}d)
                            </span>
                          )}
                          {activeEp.comparabilityState && renderEvidenceBadge(activeEp.comparabilityState)}
                          {renderRecoveryComparisonBadge(activeEp.recoveryComparison || (activeEp.portfolioHasRecovered ? 'RECOVERED' : 'ONGOING_RECOVERY'))}
                          <span className={`text-[10px] font-mono px-2 py-0.5 rounded border ${
                            activeEp.attributionReconciliationStatus === 'EXACTLY_RECONCILED'
                              ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/30'
                              : 'bg-amber-500/10 text-amber-600 dark:text-amber-400 border-amber-500/30'
                          }`}>
                            {activeEp.attributionReconciliationStatus === 'EXACTLY_RECONCILED' ? '✓ Reconciled (±0.0001%)' : (activeEp.attributionReconciliationStatus || 'Reconciliation Discrepancy')}
                          </span>
                          {renderWithholdingBadge(activeEp.withholdingReasonCode, activeEp.withholdingReasonExplanation)}
                          {activeEp.benchmarkSourceQualityState && (
                            <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-slate-500/10 text-slate-600 dark:text-slate-400 border border-slate-500/20" title="Benchmark Source Quality State">
                              Bm Source: {activeEp.benchmarkSourceQualityState}
                            </span>
                          )}
                        </div>
                        <div className="text-xs text-text-tertiary font-mono mt-0.5">
                          Episode ID: <strong className="text-text-secondary">{activeEp.episodeId}</strong> {activeEp.legacyEpisodeId && activeEp.legacyEpisodeId !== activeEp.episodeId ? `(Legacy: ${activeEp.legacyEpisodeId})` : ''} {activeEp.calculationInputFingerprint ? `| Hash: ${activeEp.calculationInputFingerprint.substring(0, 8)}...` : ''} | Window: {activeEp.startDate} to {activeEp.endDate} | {activeEp.alignedTradingDaysCount} Aligned Trading Days {activeEp.commonSubsetStartDate && activeEp.hasReducedCommonDateSubset ? `| Usable Subset: ${activeEp.commonSubsetStartDate} to ${activeEp.commonSubsetEndDate} (${activeEp.usableCommonTradingDaysCount ?? activeEp.usableObservationCount}d)` : ''} | Baseline Peak Preserved
                        </div>
                        {activeEp.lifecycleTransitionContext && (
                          <div className="text-[11px] text-text-tertiary font-mono mt-0.5">
                            Transition Context: {activeEp.lifecycleTransitionContext}
                          </div>
                        )}
                      </div>
                      <div className="text-xs font-mono text-text-secondary">
                        Benchmark Peak: {activeEp.benchmarkPeakDate} {activeEp.firstDeclineDate ? `| First Decline: ${activeEp.firstDeclineDate}` : ''} | Trough: {activeEp.benchmarkTroughDate}
                        {activeEp.benchmarkRecoveryDate ? ` | Bm Recovered: ${activeEp.benchmarkRecoveryDate}` : ' | Bm Unrecovered'}
                      </div>
                    </div>

                    {/* Detailed Date-Level Observation Accounting Card */}
                    <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-800/40 border border-border dark:border-slate-800 space-y-2 text-xs">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <span className="font-semibold text-text-primary">
                          Observation Accounting &amp; Separated Evidence Dimensions:
                        </span>
                        <span className="font-mono text-[11px] text-text-secondary">
                          Cutoff: {activeEp.analysisCutoffDate || stress?.asOfDate || 'N/A'} | Knowledge: {activeEp.knowledgeCutoff ? activeEp.knowledgeCutoff.substring(0, 19).replace('T', ' ') : (stress?.knowledgeCutoff ? stress.knowledgeCutoff.substring(0, 19).replace('T', ' ') : 'N/A')}
                        </span>
                      </div>
                      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2 font-mono text-[11px]">
                        <div className="p-2 rounded bg-surface border border-border dark:border-slate-700/60">
                          <div className="text-text-tertiary text-[10px] font-sans">Availability</div>
                          <div className="font-semibold text-text-primary text-[10px] truncate" title={activeEp.calculationAvailability || 'CALCULATED'}>
                            {activeEp.calculationAvailability || 'CALCULATED'}
                          </div>
                        </div>
                        <div className="p-2 rounded bg-surface border border-border dark:border-slate-700/60">
                          <div className="text-text-tertiary text-[10px] font-sans">Completeness</div>
                          <div className="font-semibold text-text-primary text-[10px] truncate" title={activeEp.dataCompleteness || 'COMPLETE'}>
                            {activeEp.dataCompleteness || 'COMPLETE'}
                          </div>
                        </div>
                        <div className="p-2 rounded bg-surface border border-border dark:border-slate-700/60">
                          <div className="text-text-tertiary text-[10px] font-sans">Usable / Expected</div>
                          <div className="font-semibold text-text-primary text-[10px]">
                            {activeEp.usableCommonTradingDaysCount ?? activeEp.usableObservationCount ?? activeEp.alignedTradingDaysCount} / {activeEp.expectedBenchmarkTradingDays ?? activeEp.expectedObservationCount ?? activeEp.alignedTradingDaysCount}d ({activeEp.observationCoveragePct != null ? `${activeEp.observationCoveragePct.toFixed(1)}%` : '100%'})
                          </div>
                        </div>
                        <div className="p-2 rounded bg-surface border border-border dark:border-slate-700/60">
                          <div className="text-text-tertiary text-[10px] font-sans">Statistical Evidence</div>
                          <div className="font-semibold text-text-primary text-[10px] truncate" title={activeEp.statisticalEvidenceAdequacy || 'DESCRIPTIVE_EVENT_SAMPLE'}>
                            {activeEp.statisticalEvidenceAdequacy || 'DESCRIPTIVE_SAMPLE'}
                          </div>
                        </div>
                        <div className="p-2 rounded bg-surface border border-border dark:border-slate-700/60">
                          <div className="text-text-tertiary text-[10px] font-sans">Volatility Evidence</div>
                          <div className="font-semibold text-text-primary text-[10px] truncate" title={activeEp.volatilityEvidenceState || 'DESCRIPTIVE_ONLY_HIGH_VARIANCE_SAMPLE'}>
                            {activeEp.volatilityEvidenceState || 'DESCRIPTIVE_ONLY'}
                          </div>
                        </div>
                        <div className="p-2 rounded bg-surface border border-border dark:border-slate-700/60">
                          <div className="text-text-tertiary text-[10px] font-sans">Tail Risk State</div>
                          <div className="font-semibold text-text-primary text-[10px] truncate" title={activeEp.tailEvidenceState || 'DESCRIPTIVE_EXTREME_OBSERVATION'}>
                            {activeEp.tailEvidenceState || 'DESCRIPTIVE_EXTREME'}
                          </div>
                        </div>
                      </div>

                      {/* Excluded Observations Breakdown if any dates were excluded */}
                      {((activeEp.excludedBenchmarkTradingDaysCount ?? activeEp.excludedObservationCount ?? 0) > 0 || (activeEp.preInceptionDateCount ?? 0) > 0 || (activeEp.missingNavDateCount ?? 0) > 0) && (
                        <div className="p-2 rounded bg-surface border border-border dark:border-slate-700/60 font-mono text-[11px] flex flex-wrap items-center justify-between gap-2">
                          <div className="flex flex-wrap items-center gap-2">
                            <span className="font-semibold text-rose-500">Excluded Observations Breakdown ({activeEp.excludedBenchmarkTradingDaysCount ?? activeEp.excludedObservationCount ?? 0} dates):</span>
                            <span>Pre-Inception: <strong>{activeEp.preInceptionDateCount ?? 0}</strong></span> |
                            <span>Missing NAV: <strong>{activeEp.missingNavDateCount ?? 0}</strong></span> |
                            <span>Invalid NAV: <strong>{activeEp.invalidNavDateCount ?? 0}</strong></span>
                          </div>
                          {(activeEp.excludedDateReasons || activeEp.excludedObservationReasons) && (activeEp.excludedDateReasons || activeEp.excludedObservationReasons)!.length > 0 && (
                            <div className="text-[10px] text-text-tertiary">
                              Reasons: {(activeEp.excludedDateReasons || activeEp.excludedObservationReasons)!.join('; ')}
                            </div>
                          )}
                        </div>
                      )}

                      {/* Common-Date Subset Explicit Notice */}
                      {activeEp.hasReducedCommonDateSubset && (
                        <div className="p-2.5 rounded bg-blue-500/10 border border-blue-500/30 text-blue-800 dark:text-blue-300 text-[11px]">
                          <strong>Common-Date Subset Disclosure:</strong> Calculation evaluated over common-date subset ({activeEp.usableCommonTradingDaysCount ?? activeEp.usableObservationCount} of {activeEp.expectedBenchmarkTradingDays ?? activeEp.expectedObservationCount} trading days, {activeEp.observationCoveragePct?.toFixed(1)}% coverage) from {activeEp.commonSubsetStartDate} to {activeEp.commonSubsetEndDate}. This is an explicitly disclosed common-date subset and does not represent a complete benchmark-window result (§35 &amp; §45 AGENTS.md).
                        </div>
                      )}
                    </div>

                    {/* Data Quality Signals Banner (Task 51) */}
                    {activeEp.dataQualitySignals && activeEp.dataQualitySignals.length > 0 && (
                      <div className="p-3 rounded-lg bg-amber-500/10 border border-amber-500/30 space-y-1.5 text-xs">
                        <div className="font-semibold text-amber-800 dark:text-amber-300 flex items-center justify-between">
                          <div className="flex items-center gap-1.5">
                            <span>Data-Quality &amp; Reporting Anomaly Signals ({activeEp.dataQualitySignals.length}):</span>
                            <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-amber-500/20 border border-amber-500/40">
                              Descriptive Flags (Non-Defect Until Investigated)
                            </span>
                          </div>
                          <span className="text-[10px] font-mono text-amber-700 dark:text-amber-400">
                            Source Integrity Review
                          </span>
                        </div>
                        <div className="space-y-1">
                          {activeEp.dataQualitySignals.map((sig, idx) => (
                            <div key={sig.signalId || idx} className="flex flex-wrap items-center justify-between gap-1 text-[11px] font-mono text-amber-900 dark:text-amber-200 bg-amber-500/5 p-1.5 rounded border border-amber-500/20">
                              <div className="flex items-center gap-2">
                                <span className={`px-1.5 py-0.2 rounded text-[10px] font-bold border ${
                                  sig.severity === 'WARNING' || sig.severity === 'POTENTIAL_ANOMALY'
                                    ? 'bg-amber-500/20 text-amber-700 dark:text-amber-300 border-amber-500/40'
                                    : sig.severity === 'DATA_DEFECT'
                                    ? 'bg-rose-500/20 text-rose-700 dark:text-rose-300 border-rose-500/40'
                                    : 'bg-blue-500/20 text-blue-700 dark:text-blue-300 border-blue-500/40'
                                }`}>
                                  {sig.signalType?.replace(/_/g, ' ')}
                                </span>
                                <span className="font-sans text-text-primary">{sig.description}</span>
                                {sig.signalLifecycleState && (
                                  <span className="text-[9px] px-1 py-0.2 rounded bg-slate-500/20 text-slate-700 dark:text-slate-300 border border-slate-500/30">
                                    {sig.signalLifecycleState}
                                  </span>
                                )}
                              </div>
                              <div className="text-[10px] text-amber-800 dark:text-amber-300">
                                {sig.affectedDaysCount ?? sig.affectedObservationCount} obs ({sig.startDate ?? sig.affectedStartDate} &rarr; {sig.endDate ?? sig.affectedEndDate}) | {(sig.isCalculationWithholding ?? sig.calculationWithheld) ? 'CALCULATION WITHHELD' : 'REVIEW REQUIRED'}
                              </div>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* Partial Portfolio Alert Banner if missing constituents */}
                    {(activeEp.missingConstituentCount && activeEp.missingConstituentCount > 0 || activeEp.partialPortfolioNotice) && (
                      <div className="p-3 rounded-lg bg-orange-500/10 border border-orange-500/30 text-orange-900 dark:text-orange-300 text-xs space-y-1">
                        <div className="font-bold flex items-center gap-1.5">
                          <span>Partial Portfolio Evidence &amp; Truncated History:</span>
                          <span className="font-mono text-[10px] px-1.5 py-0.2 rounded bg-orange-500/20 border border-orange-500/40">
                            Missing Allocation: {activeEp.missingAllocationWeightPct?.toFixed(2)}%
                          </span>
                        </div>
                        <div>
                          {activeEp.partialPortfolioNotice || 'One or more constituent funds lack NAV observations during this historical episode. The portfolio-level cumulative return and risk metrics are withheld to prevent silent weight redistribution or synthetic extrapolation.'}
                        </div>
                        <div className="text-[11px] text-orange-800 dark:text-orange-400 font-mono">
                          Constituent standalone returns for available funds are preserved without fabricating pre-inception returns (§4 &amp; §45 AGENTS.md).
                        </div>
                      </div>
                    )}

                    {/* Comparability limitations notice */}
                    {activeEp.comparabilityLimitations && (
                      <div className="p-3 rounded bg-amber-500/10 border border-amber-500/30 text-amber-800 dark:text-amber-300 text-xs">
                        <strong>Comparability Limitation:</strong> {activeEp.comparabilityLimitations}
                      </div>
                    )}

                    {/* Fallback benchmark notice if applicable */}
                    {activeEp.isFallbackBenchmark && activeEp.benchmarkComparabilityNotice && (
                      <div className="p-3 rounded bg-amber-500/10 border border-amber-500/30 text-amber-800 dark:text-amber-300 text-xs">
                        <strong>Benchmark Fallback Alert:</strong> {activeEp.benchmarkComparabilityNotice}
                      </div>
                    )}

                    {/* Limitation notice if limited evidence */}
                    {activeEp.evidenceLimitationReason && (
                      <div className="p-3 rounded bg-amber-500/10 border border-amber-500/30 text-amber-800 dark:text-amber-300 text-xs">
                        <strong>Evidence Limitation Notice:</strong> {activeEp.evidenceLimitationReason}
                      </div>
                    )}

                    {/* Comparative Performance KPI Cards with Path & Duration Metrics */}
                    <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
                      {/* Card 1: Cumulative Return */}
                      <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-800/40 border border-border dark:border-slate-800 space-y-1">
                        <div className="text-[11px] text-text-tertiary">Portfolio Cumulative Return</div>
                        <div className={`text-base font-semibold font-mono ${(activeEp.portfolioCumulativeReturnPct ?? 0) >= 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'}`}>
                          {activeEp.portfolioCumulativeReturnPct != null ? `${activeEp.portfolioCumulativeReturnPct > 0 ? '+' : ''}${activeEp.portfolioCumulativeReturnPct.toFixed(2)}%` : 'Withheld (Partial)'}
                        </div>
                        <div className="text-[10px] text-text-tertiary">
                          Benchmark: <span className="font-mono">{activeEp.benchmarkCumulativeReturnPct != null ? `${activeEp.benchmarkCumulativeReturnPct > 0 ? '+' : ''}${activeEp.benchmarkCumulativeReturnPct.toFixed(2)}%` : 'N/A'}</span>
                        </div>
                        <div className="text-[10px] font-mono text-text-secondary">
                          Active: <span className={(activeEp.activeReturnPct ?? 0) >= 0 ? 'text-emerald-500' : 'text-rose-500'}>{activeEp.activeReturnPct != null ? `${activeEp.activeReturnPct > 0 ? '+' : ''}${activeEp.activeReturnPct.toFixed(2)}%` : 'N/A'}</span>
                        </div>
                      </div>

                      {/* Card 2: Maximum Drawdown & Peak-to-Trough Loss */}
                      <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-800/40 border border-border dark:border-slate-800 space-y-1">
                        <div className="text-[11px] text-text-tertiary">Max DD / Peak-to-Trough</div>
                        <div className="text-base font-semibold font-mono text-rose-600 dark:text-rose-400">
                          {activeEp.portfolioMaxDrawdownPct != null ? `${activeEp.portfolioMaxDrawdownPct.toFixed(2)}%` : 'N/A'}
                        </div>
                        <div className="text-[10px] text-text-tertiary">
                          P-to-T Loss: <span className="font-mono font-semibold text-rose-500">{activeEp.peakToTroughLossPct != null ? `${activeEp.peakToTroughLossPct.toFixed(2)}%` : 'N/A'}</span>
                        </div>
                        <div className="text-[10px] font-mono text-text-secondary truncate">
                          Trough: {activeEp.portfolioTroughDate || 'N/A'}
                        </div>
                      </div>

                      {/* Card 3: Path & Duration Metrics (Task 39 & Task 54) */}
                      <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-800/40 border border-border dark:border-slate-800 space-y-1">
                        <div className="text-[11px] text-text-tertiary">Peak-to-Trough Duration</div>
                        <div className="text-sm font-semibold font-mono text-text-primary">
                          {activeEp.tradingDaysToTrough != null ? `${activeEp.tradingDaysToTrough} sessions` : 'N/A'}
                        </div>
                        <div className="text-[10px] text-text-tertiary">
                          Calendar: <span className="font-mono">{activeEp.calendarDaysToTrough != null ? `${activeEp.calendarDaysToTrough} days` : 'N/A'}</span>
                        </div>
                        <div className="text-[10px] font-mono text-text-secondary pt-0.5 border-t border-border dark:border-slate-800/80">
                          Recovery: {activeEp.tradingDaysToRecovery != null ? `${activeEp.tradingDaysToRecovery} sessions (${activeEp.calendarDaysToRecovery ?? 'N/A'} cal days)` : (activeEp.portfolioHasRecovered ? 'Recovered' : 'Unrecovered')}
                        </div>
                        {activeEp.unrecoveredDurationDays != null && activeEp.unrecoveredDurationDays > 0 && (
                          <div className="text-[9px] font-mono text-amber-600 dark:text-amber-400">
                            Cutoff: {activeEp.unrecoveredDurationDays} calendar days to cutoff
                          </div>
                        )}
                      </div>

                      {/* Card 4: Annualized Volatility */}
                      <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-800/40 border border-border dark:border-slate-800 space-y-1">
                        <div className="text-[11px] text-text-tertiary">Annualized Volatility</div>
                        <div className="text-base font-semibold font-mono text-text-primary">
                          {activeEp.portfolioAnnualizedVolatilityPct != null ? `${activeEp.portfolioAnnualizedVolatilityPct.toFixed(2)}%` : 'N/A'}
                        </div>
                        <div className="text-[10px] text-text-tertiary">
                          252 Trading Days (SQRT)
                        </div>
                        <div className="text-[10px] font-mono text-text-secondary">
                          {activeEp.usableCommonTradingDaysCount ?? activeEp.usableObservationCount ?? activeEp.alignedTradingDaysCount} usable days
                        </div>
                      </div>

                      {/* Card 5: Recovery State */}
                      <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-800/40 border border-border dark:border-slate-800 space-y-1">
                        <div className="text-[11px] text-text-tertiary">Recovery Comparison</div>
                        <div className="text-sm font-semibold font-mono">
                          {activeEp.portfolioHasRecovered ? (
                            <span className="text-emerald-600 dark:text-emerald-400 flex items-center gap-1">
                              <span>&check;</span> Port. Recovered
                            </span>
                          ) : (
                            <span className="text-amber-600 dark:text-amber-400">
                              Port. Ongoing
                            </span>
                          )}
                        </div>
                        <div className="text-[10px] text-text-tertiary truncate">
                          {activeEp.portfolioRecoveryDate ? `Date: ${activeEp.portfolioRecoveryDate}` : 'Unrecovered by cutoff'}
                        </div>
                        <div className="text-[10px] font-mono text-text-secondary truncate">
                          Benchmark: {activeEp.benchmarkRecoveryState ? activeEp.benchmarkRecoveryState.replace(/_/g, ' ') : (activeEp.benchmarkHasRecovered ? 'Recovered' : 'Unrecovered')}
                        </div>
                      </div>

                      {/* Card 6: Extreme Daily Returns */}
                      <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-800/40 border border-border dark:border-slate-800 space-y-1">
                        <div className="text-[11px] text-text-tertiary">Worst Single Day</div>
                        <div className="text-sm font-semibold font-mono text-rose-600 dark:text-rose-400">
                          {activeEp.worstDailyReturnPct != null ? `${activeEp.worstDailyReturnPct.toFixed(2)}%` : 'N/A'}
                        </div>
                        <div className="text-[10px] text-text-tertiary font-mono truncate">
                          Date: {activeEp.worstDailyReturnDate || 'N/A'}
                        </div>
                        <div className="text-[10px] text-text-secondary">
                          Best Day: <span className="font-mono text-emerald-500">{activeEp.bestDailyReturnPct != null ? `+${activeEp.bestDailyReturnPct.toFixed(2)}%` : 'N/A'}</span>
                        </div>
                      </div>
                    </div>

                    {/* Constituent Fund Return Contribution Breakdown Table (Task 46 & Task 53) */}
                    <div className="space-y-3">
                      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-1">
                        <div>
                          <h6 className="text-xs font-semibold text-text-primary">
                            Constituent Return Attribution &amp; Compounding Reconciliation
                          </h6>
                          <div className="text-[11px] text-text-tertiary">
                            Exact Additive Decomposition (&sum; A<sub>i</sub> = R<sub>p,cum</sub>) via Carino Multi-Period Logarithmic Linking
                          </div>
                        </div>
                        <span className="text-[11px] font-mono text-text-secondary">
                          Reconciliation Status: <strong className="text-emerald-600 dark:text-emerald-400">{activeEp.attributionReconciliationStatus}</strong>
                        </span>
                      </div>

                      <div className="overflow-x-auto border border-border dark:border-slate-800 rounded-lg">
                        <table className="w-full text-xs text-left">
                          <thead className="bg-slate-50 dark:bg-slate-800/60 text-text-tertiary font-medium border-b border-border dark:border-slate-800">
                            <tr>
                              <th className="py-2.5 px-3">Mutual Fund Scheme</th>
                              <th className="py-2.5 px-3">Category</th>
                              <th className="py-2.5 px-3 text-right">Capital Weight (w<sub>i</sub>)</th>
                              <th className="py-2.5 px-3 text-right">Fund Return (R<sub>i,cum</sub>)</th>
                              <th className="py-2.5 px-3 text-center">Standalone Role</th>
                              <th className="py-2.5 px-3 text-right" title="Single-period weighted return indicator; non-additive across multi-period compounding">
                                Approx Weighted Indicator*
                              </th>
                              <th className="py-2.5 px-3 text-right" title="Exact additive contribution derived via Carino logarithmic linking; sums exactly to portfolio cumulative return">
                                Exact Additive Contrib (A<sub>i</sub>)
                              </th>
                              <th className="py-2.5 px-3 text-center">Linked Attribution Role</th>
                              <th className="py-2.5 px-3 text-right" title="Attribution difference between single-period weighted indicator and exact linked contribution (w_i * R_i,cum - A_i)">
                                Attribution Difference (&Delta;<sub>i</sub>)
                              </th>
                              <th className="py-2.5 px-3 text-right">Fund Max DD</th>
                              <th className="py-2.5 px-3 text-center">Sessions / Cal</th>
                              <th className="py-2.5 px-3 text-center">Data Coverage</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-border dark:divide-slate-800 font-mono">
                            {activeEp.fundContributions?.map((fc) => (
                              <tr key={fc.schemeOptionId} className="hover:bg-slate-50/50 dark:hover:bg-slate-800/30">
                                <td className="py-2.5 px-3 font-sans font-medium text-text-primary">
                                  <div className="flex items-center gap-1.5">
                                    <span>{fc.fundName}</span>
                                    {fc.hasPreInception && (
                                      <span className="px-1 py-0.2 rounded text-[9px] font-mono bg-amber-500/15 text-amber-600 dark:text-amber-400 border border-amber-500/30">
                                        Post-Dates Episode
                                      </span>
                                    )}
                                  </div>
                                  <div className="text-[10px] text-text-tertiary font-mono flex items-center gap-2">
                                    <span>AMFI: {fc.amfiCode || 'N/A'}</span>
                                    {fc.sourceQualityState && (
                                      <span className="px-1 py-0.2 rounded text-[9px] font-mono bg-slate-500/10 text-slate-600 dark:text-slate-400 border border-slate-500/20" title="Constituent Source Quality State">
                                        {fc.sourceQualityState}
                                      </span>
                                    )}
                                    {fc.earliestKnownNavDate && (
                                      <span>Earliest NAV: {fc.earliestKnownNavDate}</span>
                                    )}
                                  </div>
                                </td>
                                <td className="py-2.5 px-3 font-sans text-text-secondary text-[11px]">
                                  {fc.category}
                                </td>
                                <td className="py-2.5 px-3 text-right text-text-primary font-semibold">
                                  {fc.currentAllocationWeightPct != null ? `${fc.currentAllocationWeightPct.toFixed(2)}%` : '0.00%'}
                                </td>
                                <td className={`py-2.5 px-3 text-right font-semibold ${(fc.fundCumulativeReturnPct ?? 0) >= 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'}`}>
                                  {fc.fundCumulativeReturnPct != null ? `${fc.fundCumulativeReturnPct > 0 ? '+' : ''}${fc.fundCumulativeReturnPct.toFixed(2)}%` : 'N/A'}
                                </td>
                                <td className="py-2.5 px-3 text-center">
                                  <div className="flex flex-col items-center">
                                    {renderStandaloneRoleBadge(fc.standalonePerformanceRole)}
                                    {fc.outperformedPortfolioProxy != null && (
                                      <span className={`text-[9px] font-mono mt-0.5 ${fc.outperformedPortfolioProxy ? 'text-emerald-500' : 'text-slate-500'}`}>
                                        {fc.outperformedPortfolioProxy ? '▲ Outperformed Proxy' : '▼ Lagged Proxy'}
                                      </span>
                                    )}
                                  </div>
                                </td>
                                <td className={`py-2.5 px-3 text-right ${(fc.approximateWeightedReturnIndicatorPct ?? 0) >= 0 ? 'text-emerald-600/80 dark:text-emerald-400/80' : 'text-rose-600/80 dark:text-rose-400/80'}`}>
                                  {fc.approximateWeightedReturnIndicatorPct != null ? `${fc.approximateWeightedReturnIndicatorPct > 0 ? '+' : ''}${fc.approximateWeightedReturnIndicatorPct.toFixed(2)}% pts` : 'N/A'}
                                </td>
                                <td className={`py-2.5 px-3 text-right font-bold ${(fc.exactAdditiveReturnContributionPct ?? 0) >= 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'}`}>
                                  {fc.exactAdditiveReturnContributionPct != null ? `${fc.exactAdditiveReturnContributionPct > 0 ? '+' : ''}${fc.exactAdditiveReturnContributionPct.toFixed(2)}% pts` : 'N/A'}
                                </td>
                                <td className="py-2.5 px-3 text-center">
                                  <div className="flex flex-col items-center gap-0.5">
                                    {renderStressRoleBadge(fc.linkedAttributionRole || fc.attributionRole)}
                                    {fc.attributionRoleInterpretation && (
                                      <span className="text-[9px] text-text-tertiary font-sans max-w-[140px] leading-tight mt-0.5">
                                        {fc.attributionRoleInterpretation}
                                      </span>
                                    )}
                                    {fc.attributionReconciliationStatus && fc.attributionReconciliationStatus !== 'EXACTLY_RECONCILED' && (
                                      <span className="text-[9px] font-mono text-amber-600 dark:text-amber-400 mt-0.5" title="Attribution discrepancy">
                                        {fc.attributionReconciliationStatus}
                                      </span>
                                    )}
                                    {renderWithholdingBadge(fc.withholdingReasonCode, fc.withholdingReasonExplanation)}
                                  </div>
                                </td>
                                <td className="py-2.5 px-3 text-right text-text-secondary text-[11px]">
                                  {fc.weightedReturnToLinkedAttributionDifferencePct != null
                                    ? `${fc.weightedReturnToLinkedAttributionDifferencePct > 0 ? '+' : ''}${fc.weightedReturnToLinkedAttributionDifferencePct.toFixed(2)}% pts`
                                    : (fc.rebalancingInteractionPct != null ? `${fc.rebalancingInteractionPct > 0 ? '+' : ''}${fc.rebalancingInteractionPct.toFixed(2)}% pts` : 'N/A')}
                                </td>
                                <td className="py-2.5 px-3 text-right text-rose-500">
                                  {fc.fundMaxDrawdownPct != null ? `${fc.fundMaxDrawdownPct.toFixed(2)}%` : 'N/A'}
                                </td>
                                <td className="py-2.5 px-3 text-center font-mono text-[10px] text-text-secondary">
                                  {fc.tradingDaysInWindow != null ? `${fc.tradingDaysInWindow} sess` : '—'} / {fc.calendarDaysInWindow != null ? `${fc.calendarDaysInWindow} cal` : '—'}
                                </td>
                                <td className="py-2.5 px-3 text-center font-sans">
                                  <div className="flex flex-col items-center gap-0.5">
                                    <span className="font-mono text-[11px] font-semibold text-text-primary">
                                      {fc.constituentCoveragePct != null ? `${fc.constituentCoveragePct.toFixed(0)}%` : 'N/A'}
                                    </span>
                                    <span className="text-[10px] text-text-tertiary font-mono">
                                      {fc.usableObservationCount ?? fc.availableObservations ?? 0}/{fc.expectedObservations ?? activeEp.alignedTradingDaysCount}d
                                    </span>
                                    {(fc.excludedObservationCount ?? 0) > 0 && (
                                      <span className="text-[9px] font-mono text-rose-500">
                                        Excl: {fc.excludedObservationCount} (Pre:{fc.preInceptionDateCount ?? 0}, Miss:{fc.missingNavDateCount ?? 0})
                                      </span>
                                    )}
                                    {renderEvidenceBadge(fc.evidenceState)}
                                    {fc.numericalValidityState && (
                                      <span className="text-[9px] font-mono text-text-tertiary truncate max-w-[120px]" title={fc.numericalValidityState}>
                                        {fc.numericalValidityState.replace(/_/g, ' ')}
                                      </span>
                                    )}
                                  </div>
                                </td>
                              </tr>
                            ))}
                          </tbody>
                          <tfoot className="bg-slate-100 dark:bg-slate-800/90 font-mono font-semibold border-t-2 border-border dark:border-slate-700">
                            <tr>
                              <td colSpan={2} className="py-2.5 px-3 font-sans text-text-primary text-xs">
                                Portfolio Proxy Total (Constant-Weight Daily Rebalanced)
                              </td>
                              <td className="py-2.5 px-3 text-right text-text-primary">
                                {totalWeight.toFixed(2)}%
                              </td>
                              <td className="py-2.5 px-3 text-right text-text-tertiary">
                                &mdash;
                              </td>
                              <td className="py-2.5 px-3 text-center text-text-tertiary">
                                &mdash;
                              </td>
                              <td className={`py-2.5 px-3 text-right ${totalApproxContrib >= 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'}`}>
                                {totalApproxContrib > 0 ? '+' : ''}{totalApproxContrib.toFixed(2)}% pts*
                              </td>
                              <td className={`py-2.5 px-3 text-right font-bold ${totalExactContrib >= 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'}`}>
                                {totalExactContrib > 0 ? '+' : ''}{totalExactContrib.toFixed(2)}% pts
                                <span className="ml-1 text-[10px] text-emerald-500 font-normal">
                                  {activeEp.attributionReconciliationStatus === 'EXACTLY_RECONCILED' ? '✓ Reconciled' : ''}
                                </span>
                              </td>
                              <td className="py-2.5 px-3 text-center text-text-tertiary">
                                &mdash;
                              </td>
                              <td className="py-2.5 px-3 text-right text-text-secondary text-[11px]">
                                {totalDiff > 0 ? '+' : ''}{totalDiff.toFixed(2)}% pts
                              </td>
                              <td className="py-2.5 px-3 text-right text-rose-600 dark:text-rose-400">
                                {activeEp.portfolioMaxDrawdownPct != null ? `${activeEp.portfolioMaxDrawdownPct.toFixed(2)}%` : 'N/A'}
                              </td>
                              <td colSpan={2} className="py-2.5 px-3 text-center text-[10px] text-text-tertiary font-sans">
                                Tol: &plusmn;0.0001%
                              </td>
                            </tr>
                          </tfoot>
                        </table>
                      </div>

                      {/* Reconciliation Callout & Compounding Residual Explanation */}
                      <div className="p-3 rounded bg-slate-50 dark:bg-slate-800/40 border border-border dark:border-slate-800 space-y-1.5 text-xs">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <div className="font-semibold text-text-primary flex items-center gap-1.5">
                            <span>Carino Exact Additive Attribution Reconciliation:</span>
                            <span className={`px-2 py-0.5 rounded text-[10px] font-mono border ${
                              activeEp.attributionReconciliationStatus === 'EXACTLY_RECONCILED'
                                ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/30'
                                : 'bg-amber-500/10 text-amber-600 dark:text-amber-400 border-amber-500/30'
                            }`}>
                              {activeEp.attributionReconciliationStatus === 'EXACTLY_RECONCILED' ? 'EXACTLY RECONCILED (±0.0001%)' : (activeEp.attributionReconciliationStatus || 'RECONCILIATION DISCREPANCY')}
                            </span>
                          </div>
                          <div className="font-mono text-[11px] text-text-secondary">
                            Sum of Exact Additive Contrib: <span className="font-bold text-text-primary">{totalExactContrib.toFixed(2)}% pts</span> = Portfolio Return: <span className="font-bold text-text-primary">{activeEp.portfolioCumulativeReturnPct != null ? `${activeEp.portfolioCumulativeReturnPct.toFixed(2)}%` : 'N/A'}</span>
                          </div>
                        </div>

                        <div className="text-[11px] text-text-secondary">
                          * <strong>Compounding Residual &amp; Rebalancing Interaction (&Delta;<sub>compounding</sub>):</strong> The sum of approximate weighted returns (&sum; w<sub>i</sub> &times; R<sub>i,cum</sub> = {totalApproxContrib.toFixed(2)}% pts) differs from cumulative portfolio proxy return ({activeEp.portfolioCumulativeReturnPct?.toFixed(2)}%) by <span className="font-mono font-semibold text-text-primary">{activeEp.compoundingResidualPct != null ? `${activeEp.compoundingResidualPct > 0 ? '+' : ''}${activeEp.compoundingResidualPct.toFixed(2)}% pts` : '0.00% pts'}</span>. This residual is mathematically expected due to multi-period compounding and daily constant-weight rebalancing. Carino logarithmic linking eliminates this gap to provide exact additive constituent attribution.
                        </div>
                      </div>

                      <p className="text-[11px] text-text-tertiary">
                        <strong>Epistemic Warning &amp; Distinction:</strong> (1) <em>Fund Return (R<sub>i,cum</sub>)</em> is the constituent&apos;s standalone cumulative growth over the episode. (2) <em>Approx. Weighted Indicator (w<sub>i</sub> &times; R<sub>i,cum</sub>)</em> is a single-period indicator and non-additive over multi-period compounding. (3) <em>Exact Additive Contribution (A<sub>i</sub>)</em> is derived via Carino logarithmic linking and strictly sums to portfolio return. (4) <em>Return Attribution</em> is entirely separate from Euler covariance-based volatility risk contribution (%RC<sub>i</sub>) and marginal covariance dampening.
                      </p>
                    </div>
                  </div>
                )}
              </div>
            ) : (
              <div className="p-6 rounded-lg bg-slate-50 dark:bg-slate-800/40 border border-border dark:border-slate-800 text-center space-y-3">
                <div className="text-sm font-semibold text-text-primary">
                  No Historical Market Drawdown Reached the 10.0% Threshold in Available Observations
                </div>
                <p className="text-xs text-text-tertiary max-w-2xl mx-auto">
                  {summary?.overallStressAssessment || `Benchmark '${bmRef?.benchmarkCode || 'NIFTY_500_TRI'}' observation window (${bmRef?.minObservationDate} to ${bmRef?.maxObservationDate}, ${bmRef?.totalObservationCount} days) contains no market stress episode reaching the 10.0% peak-to-trough drawdown threshold under ${stress?.identificationPolicyVersion || 'POLICY_V1'}.`}
                </p>
                <div className="text-[11px] font-mono text-text-secondary">
                  Stress episode identification strictly evaluates empirical benchmark observations and refuses to fabricate synthetic market crisis dates (§4 AGENTS.md).
                </div>
              </div>
            )}

            {/* Unvalued Positions Disclosure */}
            {stress?.unvaluedPositions && stress.unvaluedPositions.length > 0 && (
              <div className="p-4 rounded-lg bg-amber-500/10 border border-amber-500/30 text-amber-800 dark:text-amber-300 space-y-2 text-xs">
                <div className="font-semibold flex items-center justify-between">
                  <span>Unvalued Portfolio Positions ({stress.unvaluedPositions.length})</span>
                  <span className="font-mono">Known Value Coverage: {stress.knownValueCoveragePct?.toFixed(1)}%</span>
                </div>
                <p className="text-[11px]">
                  The following holdings lack authoritative point-in-time valuation data and are excluded from weight derivation without silent redistribution:
                </p>
                <ul className="list-disc pl-5 space-y-0.5 font-mono text-[11px]">
                  {stress.unvaluedPositions.map((pos, idx) => (
                    <li key={idx}>
                      {pos.fundName} (AMFI: {pos.amfiCode || 'N/A'}) &mdash; {pos.reason}
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {/* LEVEL 3: Governance Disclaimers & Methodology Registry Notes */}
            <div className="p-4 rounded-lg bg-slate-50 dark:bg-slate-800/40 border border-border dark:border-slate-800 space-y-3 text-xs">
              <div className="font-semibold text-text-primary flex items-center justify-between">
                <span>Stress Methodology &amp; Institutional Governance Disclaimers:</span>
                <span className="font-mono text-[11px] px-2 py-0.5 rounded bg-amber-500/15 text-amber-700 dark:text-amber-300 border border-amber-500/30">
                  Status: CANDIDATE METHODOLOGY (§11 AGENTS.md)
                </span>
              </div>
              <ul className="list-disc pl-5 space-y-1 text-text-secondary">
                {stress?.governanceDisclaimers?.map((disc, idx) => (
                  <li key={idx}>{disc}</li>
                ))}
              </ul>
              <div className="border-t border-border dark:border-slate-700/60 pt-2 space-y-1 text-[11px] text-text-tertiary">
                <div>
                  <strong>Registered Stress Methodology Metrics:</strong>{' '}
                  <span className="font-mono text-text-secondary">STR-01</span> (Benchmark Stress Episode Peak-to-Trough Drawdown &ge;10.0% under Policy V2),{' '}
                  <span className="font-mono text-text-secondary">STR-02</span> (Constant-Weight Daily Rebalanced Historical Stress Proxy),{' '}
                  <span className="font-mono text-text-secondary">STR-03</span> (Carino Multi-Period Logarithmic Linking Return Attribution with observation-specific factors <em>k<sub>t</sub></em>),{' '}
                  <span className="font-mono text-text-secondary">STR-04</span> (Constituent Weighted Return to Linked Attribution Difference &Delta;<sub>i</sub> = w<sub>i</sub>R<sub>i,cum</sub> - A<sub>i</sub>),{' '}
                  <span className="font-mono text-text-secondary">STR-05</span> (Portfolio Compounding Residual &Delta;<sub>compounding</sub> = &sum;w<sub>i</sub>R<sub>i,cum</sub> - R<sub>p,cum</sub>).
                </div>
                <div>
                  <strong>Governance Policy Contracts:</strong> Governed under <span className="font-mono text-text-secondary">PORTFOLIO_STRESS_IDENTIFICATION_POLICY_V2</span> and <span className="font-mono text-text-secondary">PORTFOLIO_STRESS_EVIDENCE_POLICY_V2</span>. All stress outputs are candidate analytical models and are strictly disclaimed against use as financial advice, capital commitment recommendations, or verified performance forecasts (§11 AGENTS.md).
                </div>
              </div>
            </div>
          </div>
        );
      })()}

      {/* ------------------------------------------------------------------- */}
      {/* 10. SUBSECTION H: INVESTIGATION & RESEARCH WORKSPACE */}
      {/* ------------------------------------------------------------------- */}
      {!loading && data && data.evaluationStatus === 'SUCCESS' && activeTab === 'investigation' && (
        <div className="space-y-6">
          <div>
            <h4 className="text-sm font-semibold text-text-primary">
              Institutional Due Diligence Findings &amp; Grounded Questions
            </h4>
            <p className="text-xs text-text-tertiary">
              Structured observations categorized by epistemic evidence types with 1-click workspace research logging.
            </p>
          </div>

          <div className="space-y-3">
            {findings.map((f) => (
              <div
                key={f.id}
                className="p-4 rounded-lg bg-surface border border-border dark:border-slate-800 hover:border-slate-400 dark:hover:border-slate-700 transition space-y-3"
              >
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className={`px-2 py-0.5 rounded text-[10px] font-mono font-bold border ${
                      f.findingType === 'OBSERVED_FACT'
                        ? 'bg-blue-500/15 text-blue-600 border-blue-500/30'
                        : f.findingType === 'CALCULATED_RISK'
                        ? 'bg-amber-500/15 text-amber-600 border-amber-500/30'
                        : f.findingType === 'DATA_LIMITATION'
                        ? 'bg-slate-500/15 text-slate-600 border-slate-500/30'
                        : 'bg-emerald-500/15 text-emerald-600 border-emerald-500/30'
                    }`}>
                      {f.findingType}
                    </span>
                    <h5 className="text-xs font-bold text-text-primary">{f.title}</h5>
                    {f.findingVersion && (
                      <span className="px-1.5 py-0.2 rounded text-[9px] font-mono bg-purple-500/10 text-purple-600 dark:text-purple-400 border border-purple-500/20">
                        {f.findingVersion}
                      </span>
                    )}
                    {f.findingId && (
                      <span className="px-1.5 py-0.2 rounded text-[9px] font-mono bg-slate-500/10 text-slate-600 dark:text-slate-400 border border-slate-500/20" title={`Finding ID: ${f.findingId}`}>
                        ID: {f.findingId.substring(0, 8)}...
                      </span>
                    )}
                    {f.lifecycleStatus && (
                      <span className={`px-1.5 py-0.2 rounded text-[9px] font-mono border ${
                        f.lifecycleStatus === 'ACTIVE'
                          ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20'
                          : f.lifecycleStatus === 'SUPERSEDED'
                          ? 'bg-rose-500/10 text-rose-600 dark:text-rose-400 border-rose-500/20'
                          : 'bg-blue-500/10 text-blue-600 dark:text-blue-400 border-blue-500/20'
                      }`}>
                        {f.lifecycleStatus}
                      </span>
                    )}
                    {f.episodeId && (
                      <span className="px-1.5 py-0.2 rounded text-[9px] font-mono bg-blue-500/10 text-blue-600 dark:text-blue-400 border border-blue-500/20 truncate max-w-[140px]" title={f.episodeId}>
                        Ep: {f.episodeId}
                      </span>
                    )}
                    {f.isSuperseded && (
                      <span className="px-1.5 py-0.2 rounded text-[9px] font-mono bg-rose-500/10 text-rose-600 dark:text-rose-400 border border-rose-500/20">
                        SUPERSEDED
                      </span>
                    )}
                  </div>

                  <div className="flex items-center gap-1.5 self-start sm:self-auto">
                    <button
                      onClick={() => handleOpenWorkspace(f, false)}
                      className="px-2.5 py-1 text-xs font-medium rounded border border-border dark:border-slate-700 bg-surface hover:bg-slate-50 dark:hover:bg-slate-800 text-text-secondary hover:text-text-primary transition cursor-pointer"
                    >
                      + Log to Workspace
                    </button>
                    <button
                      onClick={() => handleOpenWorkspace(f, true)}
                      className="px-2.5 py-1 text-xs font-medium rounded border border-primary/40 bg-primary/10 text-primary dark:text-blue-400 hover:bg-primary/20 transition cursor-pointer font-sans"
                      title="Create an actionable monitoring rule from this stress finding (Task 78)"
                    >
                      + Track Condition
                    </button>
                  </div>
                </div>

                <div className="text-xs font-semibold text-text-secondary">
                  {f.headline}
                </div>

                <div className="text-xs text-text-tertiary leading-relaxed">
                  {f.evidence}
                </div>

                {/* Grounded Empirical Investigation Question (Task 71) */}
                <div className="p-2.5 rounded bg-blue-50/70 dark:bg-blue-900/20 text-xs text-blue-900 dark:text-blue-200 border border-blue-200/80 dark:border-blue-800/80 flex items-start gap-2">
                  <span className="font-bold text-primary dark:text-blue-400 shrink-0">Inquiry Question:</span>
                  <span className="font-medium">{f.investigationQuestion || f.suggestedInvestigation}</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* ------------------------------------------------------------------- */}
      {/* 10. WORKSPACE LOG MODAL (Task 44 Structured Research Workspace) */}
      {/* ------------------------------------------------------------------- */}
      {workspaceFinding && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4">
          <div className="bg-surface border border-border dark:border-slate-800 rounded-xl p-6 shadow-xl max-w-xl w-full max-h-[90vh] overflow-y-auto space-y-4">
            <div className="flex items-center justify-between border-b border-border dark:border-slate-800 pb-3">
              <div>
                <h4 className="text-sm font-bold text-text-primary">
                  Log Risk Finding to Research Workspace
                </h4>
                <p className="text-xs text-text-tertiary">
                  {workspaceFinding.title} ({workspaceFinding.findingType})
                </p>
              </div>
              <button
                onClick={() => setWorkspaceFinding(null)}
                className="text-text-tertiary hover:text-text-primary font-bold text-lg"
              >
                &times;
              </button>
            </div>

            {saveSuccessMsg ? (
              <div className="p-4 rounded-lg bg-emerald-500/10 border border-emerald-500/30 text-emerald-600 text-xs font-semibold text-center">
                {saveSuccessMsg}
              </div>
            ) : (
              <div className="space-y-3 text-xs">
                <div>
                  <label className="block text-[11px] font-semibold text-text-secondary mb-1">
                    1. Associate with Portfolio Holding:
                  </label>
                  <select
                    value={selectedHoldingId || ''}
                    onChange={(e) => setSelectedHoldingId(parseInt(e.target.value, 10))}
                    className="w-full p-2 rounded border border-border dark:border-slate-700 bg-surface dark:bg-slate-800 font-medium"
                  >
                    {holdings.map((h) => (
                      <option key={h.id} value={h.id}>
                        {h.fundName} ({h.amfiCode})
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-[11px] font-semibold text-text-secondary mb-1">
                    2. Grounded Investigation Question:
                  </label>
                  <textarea
                    rows={2}
                    value={workspaceQuestion}
                    onChange={(e) => setWorkspaceQuestion(e.target.value)}
                    className="w-full p-2 rounded border border-border dark:border-slate-700 bg-surface dark:bg-slate-800 font-sans"
                    placeholder="Enter the core thesis question or investigation mandate..."
                  />
                </div>

                <div>
                  <label className="block text-[11px] font-semibold text-text-secondary mb-1">
                    3. Epistemic Evidence Reviewed:
                  </label>
                  <textarea
                    rows={2}
                    value={workspaceEvidence}
                    onChange={(e) => setWorkspaceEvidence(e.target.value)}
                    className="w-full p-2 rounded border border-border dark:border-slate-700 bg-surface dark:bg-slate-800 font-mono text-[11px]"
                    placeholder="Empirical data points, dates, and quantitative findings..."
                  />
                </div>

                <div>
                  <label className="block text-[11px] font-semibold text-text-secondary mb-1">
                    4. Research Interpretation &amp; Due Diligence Notes:
                  </label>
                  <textarea
                    rows={2}
                    value={workspaceNotes}
                    onChange={(e) => setWorkspaceNotes(e.target.value)}
                    className="w-full p-2 rounded border border-border dark:border-slate-700 bg-surface dark:bg-slate-800 font-sans"
                    placeholder="Manager commentary, factor exposure analysis, structural thesis..."
                  />
                </div>

                <div>
                  <label className="block text-[11px] font-semibold text-text-secondary mb-1">
                    5. Unresolved Uncertainty &amp; Reconsideration Conditions:
                  </label>
                  <input
                    type="text"
                    value={workspaceUncertainty}
                    onChange={(e) => setWorkspaceUncertainty(e.target.value)}
                    className="w-full p-2 rounded border border-border dark:border-slate-700 bg-surface dark:bg-slate-800 font-sans"
                    placeholder="Conditions under which capital commitment should be reconsidered..."
                  />
                </div>

                <div>
                  <label className="block text-[11px] font-semibold text-text-secondary mb-1">
                    6. Actionable Follow-Up Step:
                  </label>
                  <input
                    type="text"
                    value={workspaceNextStep}
                    onChange={(e) => setWorkspaceNextStep(e.target.value)}
                    className="w-full p-2 rounded border border-border dark:border-slate-700 bg-surface dark:bg-slate-800 font-sans"
                    placeholder="Specific due diligence step before committing capital..."
                  />
                </div>

                <div className="flex items-center justify-end gap-2 pt-2 border-t border-border dark:border-slate-800">
                  <button
                    onClick={() => setWorkspaceFinding(null)}
                    className="px-3 py-1.5 rounded border border-border dark:border-slate-700 hover:bg-slate-100 dark:hover:bg-slate-800 text-text-secondary font-medium"
                  >
                    Cancel
                  </button>
                  <button
                    onClick={handleSaveWorkspaceNote}
                    disabled={savingNote}
                    className="px-4 py-1.5 rounded bg-primary text-white font-medium hover:bg-primary-hover disabled:opacity-50"
                  >
                    {savingNote ? 'Saving...' : 'Save to Workspace'}
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
