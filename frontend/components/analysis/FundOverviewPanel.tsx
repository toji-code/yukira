"use client";

import React, { useState } from "react";
import { EnrichedFundProfileDto } from "@/types/enrichment";

interface FundOverviewPanelProps {
  enrichment: EnrichedFundProfileDto | null;
  loading?: boolean;
}

function formatDisplayDate(dateStr?: string | null): string {
  if (!dateStr) return "Not available";
  try {
    const d = new Date(dateStr + "T00:00:00Z");
    return d.toLocaleDateString("en-GB", {
      day: "2-digit",
      month: "short",
      year: "numeric",
      timeZone: "UTC",
    });
  } catch {
    return dateStr;
  }
}

export function FundOverviewPanel({ enrichment, loading }: FundOverviewPanelProps) {
  const [showProvenanceDetails, setShowProvenanceDetails] = useState(false);

  if (loading) {
    return (
      <div className="panel space-y-4 p-4 md:p-5" aria-busy="true">
        <div className="skeleton h-4 w-1/3" />
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <div className="skeleton h-24 w-full" />
          <div className="skeleton h-24 w-full" />
          <div className="skeleton h-24 w-full" />
        </div>
      </div>
    );
  }

  if (!enrichment) {
    return (
      <div className="state-panel-error p-4">
        <p className="text-[13px] text-critical-fg">
          Fund enrichment profile is currently unavailable.
        </p>
      </div>
    );
  }

  const { aum, expenseRatio, fundManagers } = enrichment;

  return (
    <section className="panel p-4 md:p-5" aria-label="Fund Key Characteristics">
      <div className="panel-header border-b border-border pb-3">
        <div>
          <p className="eyebrow text-accent">Fund Key Characteristics</p>
          <p className="mono-meta mt-1 font-sans leading-[1.5]">
            Factual attributes from historical statutory filings and official AMC disclosures.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <span
            className={`status-badge ${
              enrichment.epistemicStatus === "VERIFIED_PRIMARY_SOURCE"
                ? "state-approved"
                : enrichment.epistemicStatus === "PARTIAL_SOURCE_COVERAGE"
                ? "state-candidate"
                : "state-unavailable"
            }`}
          >
            {enrichment.epistemicStatus.replace(/_/g, " ")}
          </span>
          <button
            onClick={() => setShowProvenanceDetails(!showProvenanceDetails)}
            className="btn btn-secondary btn-sm"
            aria-expanded={showProvenanceDetails}
          >
            {showProvenanceDetails ? "Hide Provenance" : "View Provenance"}
          </button>
        </div>
      </div>

      {/* METRIC TILES: AUM, EXPENSE RATIO, FUND MANAGER */}
      <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-3">
        {/* 1. AUM */}
        <div className="metric-tile">
          <div className="flex items-baseline justify-between gap-2">
            <span className="def-label mb-0">Assets Under Management (AUM)</span>
            <span
              className={`status-badge shrink-0 ${
                aum.status === "AVAILABLE" ? "state-approved" : "state-unavailable"
              }`}
            >
              {aum.status === "AVAILABLE" ? aum.qualityAssessment : "MISSING"}
            </span>
          </div>

          <div className="my-2">
            {aum.status === "AVAILABLE" && aum.formattedAum ? (
              <p className="data-value text-text-primary">{aum.formattedAum}</p>
            ) : (
              <p className="data-unavailable">MISSING / NOT AVAILABLE</p>
            )}
          </div>

          <div className="metric-rule my-2" aria-hidden />

          <div className="space-y-1 text-[12px] text-text-secondary">
            <div className="flex justify-between">
              <span>As-Of Date:</span>
              <span className="font-mono text-text-primary">
                {aum.asOfDate ? `As of ${formatDisplayDate(aum.asOfDate)}` : "Not available"}
              </span>
            </div>
            <div className="flex justify-between">
              <span>Scope:</span>
              <span className="text-text-primary">Scheme Total Net Assets</span>
            </div>
          </div>
        </div>

        {/* 2. EXPENSE RATIO */}
        <div className="metric-tile">
          <div className="flex items-baseline justify-between gap-2">
            <span className="def-label mb-0">
              Total Expense Ratio ({expenseRatio.planType ?? enrichment.planType})
            </span>
            <span
              className={`status-badge shrink-0 ${
                expenseRatio.status === "AVAILABLE" ? "state-approved" : "state-unavailable"
              }`}
            >
              {expenseRatio.status === "AVAILABLE" ? expenseRatio.qualityAssessment : "MISSING"}
            </span>
          </div>

          <div className="my-2">
            {expenseRatio.status === "AVAILABLE" && expenseRatio.formattedExpenseRatio ? (
              <div className="flex items-baseline gap-2">
                <p className="data-value text-text-primary">
                  {expenseRatio.formattedExpenseRatio}
                </p>
                {expenseRatio.formattedRegularPlanRatio && (
                  <span className="text-[12px] text-text-tertiary">
                    (Regular: {expenseRatio.formattedRegularPlanRatio})
                  </span>
                )}
              </div>
            ) : (
              <p className="data-unavailable">MISSING / NOT AVAILABLE</p>
            )}
          </div>

          <div className="metric-rule my-2" aria-hidden />

          <div className="space-y-1 text-[12px] text-text-secondary">
            <div className="flex justify-between">
              <span>As-Of Date:</span>
              <span className="font-mono text-text-primary">
                {expenseRatio.asOfDate ? `As of ${formatDisplayDate(expenseRatio.asOfDate)}` : "Not available"}
              </span>
            </div>
            <div className="flex justify-between">
              <span>Share Class:</span>
              <span className="text-text-primary">
                {enrichment.planType} - {enrichment.optionType}
              </span>
            </div>
          </div>
        </div>

        {/* 3. FUND MANAGER */}
        <div className="metric-tile">
          <div className="flex items-baseline justify-between gap-2">
            <span className="def-label mb-0">Fund Manager</span>
            <span
              className={`status-badge shrink-0 ${
                fundManagers && fundManagers.length > 0 ? "state-approved" : "state-unavailable"
              }`}
            >
              {fundManagers && fundManagers.length > 0
                ? fundManagers[0].qualityAssessment
                : "MISSING"}
            </span>
          </div>

          <div className="my-2">
            {fundManagers && fundManagers.length > 0 ? (
              <div>
                <p className="text-[15px] font-semibold text-text-primary">
                  {fundManagers[0].managerName}
                </p>
                {fundManagers[0].role && (
                  <p className="text-[12px] text-text-secondary">{fundManagers[0].role}</p>
                )}
              </div>
            ) : (
              <p className="data-unavailable">MISSING / NOT AVAILABLE</p>
            )}
          </div>

          <div className="metric-rule my-2" aria-hidden />

          <div className="space-y-1 text-[12px] text-text-secondary">
            <div className="flex justify-between">
              <span>Managing Since:</span>
              <span className="font-mono text-text-primary">
                {fundManagers && fundManagers.length > 0 && fundManagers[0].startDate
                  ? formatDisplayDate(fundManagers[0].startDate)
                  : "Not available"}
              </span>
            </div>
            <div className="flex justify-between">
              <span>As-Of Date:</span>
              <span className="font-mono text-text-primary">
                {fundManagers && fundManagers.length > 0 && fundManagers[0].asOfDate
                  ? `As of ${formatDisplayDate(fundManagers[0].asOfDate)}`
                  : "Not available"}
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* PROVENANCE DISCLOSURE ACCORDION */}
      {showProvenanceDetails && (
        <div className="mt-4 rounded border border-border-subtle bg-surface-inset p-3">
          <p className="text-[12px] font-semibold tracking-[-0.01em] text-text-primary">
            Cryptographic Source Provenance &amp; Verification Lineage
          </p>
          <div className="mt-2 grid grid-cols-1 gap-2 text-[11px] sm:grid-cols-3">
            <div>
              <span className="font-medium text-text-secondary">AUM Source:</span>
              <p className="font-mono text-text-tertiary">
                {aum.sourceDocumentTitle ?? "No artifact linked"} (Artifact #{aum.sourceArtifactId ?? "N/A"})
              </p>
            </div>
            <div>
              <span className="font-medium text-text-secondary">TER Source:</span>
              <p className="font-mono text-text-tertiary">
                {expenseRatio.sourceDocumentTitle ?? "No artifact linked"} (Artifact #{expenseRatio.sourceArtifactId ?? "N/A"})
              </p>
            </div>
            <div>
              <span className="font-medium text-text-secondary">Manager Source:</span>
              <p className="font-mono text-text-tertiary">
                {fundManagers && fundManagers.length > 0 && fundManagers[0].sourceDocumentTitle
                  ? fundManagers[0].sourceDocumentTitle
                  : "No artifact linked"}{" "}
                (Artifact #{fundManagers && fundManagers.length > 0 && fundManagers[0].sourceArtifactId ? fundManagers[0].sourceArtifactId : "N/A"})
              </p>
            </div>
          </div>
          <p className="mono-meta mt-2 text-[11px] font-sans leading-[1.4]">
            All facts are strictly isolated to scheme option #{enrichment.schemeOptionId} ({enrichment.planType} {enrichment.optionType}) under Point-in-Time discipline.
          </p>
        </div>
      )}
    </section>
  );
}
