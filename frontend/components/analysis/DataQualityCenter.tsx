"use client";

import React, { useState, useEffect } from "react";
import { fetchDataQualityAudit } from "@/lib/api/analysis";
import { DataQualityAuditResponse } from "@/types/analysis";

interface DataQualityCenterProps {
  schemeOptionId: number;
}

export function DataQualityCenter({ schemeOptionId }: DataQualityCenterProps) {
  const [data, setData] = useState<DataQualityAuditResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [expandedSection, setExpandedSection] = useState<"dimensions" | "anomalies" | "artifact" | null>("anomalies");

  useEffect(() => {
    let active = true;
    const cutoffTime = "2024-01-31T23:59:59+05:30";

    fetchDataQualityAudit(schemeOptionId, cutoffTime)
      .then((res) => {
        if (active) {
          setData(res);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (active) {
          setError(err instanceof Error ? err.message : "Failed to load data quality audit.");
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [schemeOptionId]);

  if (loading) {
    return (
      <div className="panel space-y-4 p-4 md:p-5" aria-busy="true">
        <div className="skeleton h-3 w-1/3" />
        <div className="skeleton h-10 w-full" />
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="state-panel-error" role="alert">
        <p className="text-[15px] font-semibold tracking-[-0.01em] text-text-primary">
          Data Quality Audit Unavailable
        </p>
        <p className="mt-1 text-[13px] leading-[1.5] text-critical-fg">
          {error || "Unable to inspect historical data quality ledger."}
        </p>
      </div>
    );
  }

  const { context, summary, ledgerContinuity, dimensions, sourceArtifactStatus, detectedAnomalies, limitations } = data;

  const TABS: Array<{
    id: "dimensions" | "anomalies" | "artifact";
    label: string;
    count?: number;
  }> = [
    {
      id: "anomalies",
      label: "Detected Observations & Limitations",
      count: detectedAnomalies.length,
    },
    { id: "dimensions", label: "6-Dimensional Taxonomy", count: dimensions.length },
    { id: "artifact", label: "Source Artifact & PIT Cutoff" },
  ];

  return (
    <div className="panel p-4 md:p-5">
      {/* Header & Overall Quality Status */}
      <div className="panel-header border-b border-border pb-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <p className="eyebrow text-accent">Institutional Epistemic Evidence</p>
            <span className="status-badge state-approved">
              <span className="status-dot" aria-hidden />
              {summary.overallStatus}
            </span>
          </div>
          <h3 className="mt-1.5 text-h2 text-text-primary">
            Data Quality &amp; Anomaly Health Center
          </h3>
          <p className="mono-meta mt-1">
            {context.schemeName} ({context.planType} • {context.optionType}) • AMFI:{" "}
            {context.amfiCode || "Not available"} • ISIN: {context.isin || "Not available"}
          </p>
        </div>

        <div className="mono-meta shrink-0 space-y-1 text-right">
          <p>
            Ledger Coverage:{" "}
            <span className="data-value-sm text-text-primary">
              {ledgerContinuity.coveragePercentage.toFixed(1)}%
            </span>
          </p>
          <p>
            Observations:{" "}
            <span className="data-value-sm text-text-primary">{summary.totalObservations}</span>
          </p>
        </div>
      </div>

      {/* 4-Stat High Level Summary */}
      <div className="grid grid-cols-2 gap-4 pt-4 md:grid-cols-4">
        <div className="metric-tile">
          <p className="def-label">Valid Observations</p>
          <p className="data-value-md text-approved-fg">
            {summary.validObservations} / {summary.totalObservations}
          </p>
          <p className="mono-meta mt-1">Pass statistical validation</p>
        </div>

        <div className="metric-tile">
          <p className="def-label">Missing/Gaps</p>
          <p className="data-value-md">{summary.missingObservations}</p>
          <p className="mono-meta mt-1">Unverified feed gaps</p>
        </div>

        <div className="metric-tile">
          <p className="def-label">Revised NAVs</p>
          <p className="data-value-md">{summary.revisedObservations}</p>
          <p className="mono-meta mt-1">Retroactive AMC revisions</p>
        </div>

        <div className="metric-tile">
          <p className="def-label">Substitutions</p>
          <p className="data-value-md">{summary.lookbackSubstitutionsCount}</p>
          <p className="mono-meta mt-1">Lookback date shifts</p>
        </div>
      </div>

      {/* Tri-Partite Epistemic Disclosure Rail */}
      <div className="mt-4">
        <div className="tab-rail" role="tablist" aria-label="Data quality disclosure">
          {TABS.map((tab) => (
            <button
              key={tab.id}
              type="button"
              role="tab"
              aria-selected={expandedSection === tab.id}
              onClick={() =>
                setExpandedSection(expandedSection === tab.id ? null : tab.id)
              }
              className="tab"
              data-active={expandedSection === tab.id ? "true" : undefined}
            >
              {tab.label}
              {tab.count !== undefined && (
                <span className="tab-ordinal">{tab.count}</span>
              )}
            </button>
          ))}
        </div>

        <div className="pt-4">
          {/* Tab 1: Detected Observations & Limitations */}
          {expandedSection === "anomalies" &&
            (detectedAnomalies.length === 0 ? (
              <div className="state-well">
                <p className="eyebrow">Zero Anomalies Recorded</p>
                <p className="mt-1 text-[13px] leading-[1.5] text-text-secondary">
                  No ledger anomalies were recorded for this instrument at the stated
                  point-in-time cutoff.
                </p>
              </div>
            ) : (
              <div className="space-y-3">
                {detectedAnomalies.map((item) => (
                  <div key={item.issueId} className="panel-inset p-4">
                    <div className="panel-header">
                      <div className="flex min-w-0 flex-wrap items-center gap-2">
                        <span className="data-value-sm">
                          {item.issueId}: {item.checkCode}
                        </span>
                        <span
                          className={`status-badge ${
                            item.severity === "ANOMALY" ? "state-critical" : "state-candidate"
                          }`}
                        >
                          <span className="status-dot" aria-hidden />
                          {item.severity}
                        </span>
                      </div>
                      <span className="mono-meta shrink-0">Period: {item.affectedPeriod}</span>
                    </div>

                    <div className="mt-3 grid grid-cols-1 gap-3 md:grid-cols-2">
                      <div className="chain-col">
                        <p className="def-label">What YUKIRA Sees</p>
                        <p className="text-[13px] leading-[1.5] text-text-secondary">
                          {item.whatYukiraSees}
                        </p>
                      </div>
                      <div className="chain-col">
                        <p className="def-label">Why It Matters</p>
                        <p className="text-[13px] leading-[1.5] text-text-secondary">
                          {item.whyItMatters}
                        </p>
                      </div>
                    </div>

                    <div className="metric-rule mt-3" aria-hidden />
                    <div className="mt-3 grid grid-cols-1 gap-3 md:grid-cols-2">
                      <div>
                        <p className="def-label">Evidence</p>
                        <p className="mono-meta break-all">{item.evidence}</p>
                      </div>
                      <div>
                        <p className="def-label">Limitation / Standard</p>
                        <p className="text-[13px] leading-[1.5] text-text-secondary">
                          {item.limitation}
                        </p>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            ))}

          {/* Tab 2: 6-Dimensional Taxonomy */}
          {expandedSection === "dimensions" && (
            <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
              {dimensions.map((dim) => (
                <div key={dim.dimensionName} className="metric-tile">
                  <div className="panel-header">
                    <p className="def-label mb-0">{dim.dimensionName}</p>
                    <span className="status-badge state-approved">
                      <span className="status-dot" aria-hidden />
                      {dim.status}
                    </span>
                  </div>
                  <p className="mt-2 text-[13px] leading-[1.5] text-text-secondary">
                    {dim.description}
                  </p>
                  <div className="metric-rule mt-3" aria-hidden />
                  <p className="mono-meta mt-2 break-all">
                    <span className="font-semibold text-text-primary">Evidence</span>{" "}
                    {dim.evidence}
                  </p>
                </div>
              ))}
            </div>
          )}

          {/* Tab 3: Source Artifact & PIT Cutoff */}
          {expandedSection === "artifact" && (
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div className="def-list">
                <div>
                  <p className="def-label">Source Artifact ID</p>
                  <p className="def-value">#{sourceArtifactStatus.artifactId}</p>
                </div>
                <div>
                  <p className="def-label">Cryptographic SHA-256 Hash</p>
                  <p className="def-value select-all break-all">
                    {sourceArtifactStatus.sha256Hash}
                  </p>
                </div>
                <div>
                  <p className="def-label">Byte Size</p>
                  <p className="def-value">
                    {sourceArtifactStatus.byteSize.toLocaleString()} bytes
                  </p>
                </div>
              </div>

              <div className="def-list">
                <div>
                  <p className="def-label">Point-in-Time Knowledge Cutoff</p>
                  <p className="def-value">{limitations.pitKnowledgeCutoff}</p>
                </div>
                <div>
                  <p className="def-label">Market Trading Convention</p>
                  <p className="def-value font-sans">{limitations.marketHolidayConvention}</p>
                </div>
                <div>
                  <p className="def-label">Auditable Disclaimer</p>
                  <p className="def-value font-sans leading-[1.45]">{limitations.disclaimer}</p>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}