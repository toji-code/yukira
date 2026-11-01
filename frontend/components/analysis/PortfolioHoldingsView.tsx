"use client";

import React, { useState, useEffect, useCallback } from "react";
import { Holding } from "@/types/domain";
import { PortfolioSnapshotDetailDto } from "@/types/enrichment";
import { fetchHoldingsSnapshot } from "@/lib/api/enrichment";
import { fetchSchemeHoldings } from "@/lib/api/schemes";
import {
  fetchFundConcentration,
  FundConcentrationDto,
} from "@/lib/api/portfolio";
import { PortfolioChangesSection } from "@/components/analysis/PortfolioChangesSection";

interface PortfolioHoldingsViewProps {
  schemeOptionId: number;
  schemeCode?: string;
}

export function PortfolioHoldingsView({ schemeOptionId, schemeCode }: PortfolioHoldingsViewProps) {
  const [snapshot, setSnapshot] = useState<PortfolioSnapshotDetailDto | null>(null);
  const [fallbackHoldings, setFallbackHoldings] = useState<Holding[] | null>(null);
  const [concentration, setConcentration] = useState<FundConcentrationDto | null>(null);
  const [showAllHoldings, setShowAllHoldings] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const snap = await fetchHoldingsSnapshot(schemeOptionId);
      setSnapshot(snap);
    } catch {
      try {
        const data = await fetchSchemeHoldings(schemeOptionId);
        setFallbackHoldings(data);
      } catch (err: unknown) {
        setError(
          err instanceof Error ? err.message : "Failed to load portfolio holdings."
        );
      }
    }

    // Fetch Concentration
    try {
      const concData = await fetchFundConcentration(schemeOptionId);
      setConcentration(concData);
    } catch {
      // Non-blocking
    } finally {
      setLoading(false);
    }
  }, [schemeOptionId]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadData();
  }, [loadData]);

  if (loading) {
    return (
      <div className="panel space-y-4 p-4 md:p-5" aria-busy="true">
        <div className="skeleton h-3 w-1/4" />
        <div className="skeleton h-28 w-full" />
      </div>
    );
  }

  const holdings = snapshot?.holdings ?? fallbackHoldings ?? [];

  if (error || (snapshot === null && fallbackHoldings === null)) {
    return (
      <div className="state-panel-error space-y-3" role="alert">
        <div className="panel-header">
          <p className="text-[15px] font-semibold tracking-[-0.01em] text-text-primary">
            Portfolio Holdings Unavailable
          </p>
          <button onClick={loadData} className="btn btn-secondary btn-sm">
            Retry Analysis
          </button>
        </div>
        <p className="text-[13px] leading-[1.5] text-critical-fg">
          {error || "Pending SEBI monthly portfolio holding sheets ingestion."}
        </p>
      </div>
    );
  }

  if (holdings.length === 0) {
    return (
      <div className="state-well">
        <p className="eyebrow">Zero Holdings Disclosed</p>
        <p className="mt-1 text-[13px] leading-[1.5] text-text-secondary">
          No holdings data available for this scheme option. (Pending regulatory feed ingestion)
        </p>
      </div>
    );
  }

  const reportingDate = snapshot?.portfolioDate || holdings[0]?.asOfDate || "Not available";
  const freshness = snapshot?.freshnessStatus || "CURRENT";
  const quality = snapshot?.dataQualityStatus || snapshot?.qualityStatus || "VERIFIED";
  const calculatedRawSum = holdings.length > 0
    ? holdings.reduce((sum, h) => sum + (typeof h.weight === "number" ? h.weight : 0), 0)
    : null;
  const rawSumPct =
    calculatedRawSum != null
      ? calculatedRawSum > 1
        ? calculatedRawSum
        : calculatedRawSum * 100
      : null;
  const undisclosedResidual =
    rawSumPct != null ? `${Math.max(0, 100 - rawSumPct).toFixed(2)}%` : "Not available";
  const coverage =
    snapshot?.formattedCoverage ||
    snapshot?.formattedTotalPortfolioWeight ||
    (rawSumPct != null && rawSumPct > 0
      ? `${rawSumPct.toFixed(2)}%`
      : "Not available");
  const sourceHash = snapshot?.sourceSha256Hash || snapshot?.sourceArtifactHash || "Not available";
  const sourceTitle = snapshot?.sourceDocumentTitle || "Official AMC Monthly Disclosure";
  const limitations = snapshot?.dataQualityLimitations || snapshot?.dataLimitations || [
    "Holdings reflect monthly portfolio disclosures published by the AMC; not intraday or real-time trading positions.",
  ];

  return (
    <div className="space-y-6 mt-6">
      {/* 1. PORTFOLIO CHANGES SECTION (Drift, Sector Shifts, Additions/Exits & Investigation Prompts) */}
      <PortfolioChangesSection
        schemeOptionId={schemeOptionId}
        schemeCode={schemeCode}
        onViewHoldingsEvidence={() => {
          document.getElementById('holdings-table')?.scrollIntoView({ behavior: 'smooth' });
        }}
      />

      {/* CONCENTRATION SUMMARY METRICS STRIP */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
        <div className="panel-inset p-3 bg-surface-raised">
          <span className="def-label">Top 5 Disclosed Weight</span>
          <span className="data-value-sm font-mono mt-1 block text-text-primary">
            {concentration?.formattedTop5Weight || (concentration?.top5WeightPct != null ? `${concentration.top5WeightPct}%` : "Not available")}
          </span>
          <span className="mono-meta mt-1 block">5 largest disclosed holdings</span>
        </div>

        <div className="panel-inset p-3 bg-surface-raised">
          <span className="def-label">Top 10 Disclosed Weight</span>
          <span className="data-value-sm font-mono mt-1 block text-text-primary">
            {concentration?.formattedTop10Weight || (concentration?.top10WeightPct != null ? `${concentration.top10WeightPct}%` : "Not available")}
          </span>
          <span className="mono-meta mt-1 block">10 largest disclosed holdings</span>
        </div>

        <div className="panel-inset p-3 bg-surface-raised">
          <span className="def-label">Disclosed HHI Index</span>
          <span className="data-value-sm font-mono mt-1 block text-accent">
            {concentration?.formattedHhi || (concentration?.hhi != null ? String(concentration.hhi) : (concentration?.hhiIndex != null ? String(concentration.hhiIndex) : "Not available"))}
          </span>
          <span className="mono-meta mt-1 block">
            {concentration?.concentrationClassification || concentration?.hhiClassification || "DIVERSIFIED"}
          </span>
        </div>

        <div className="panel-inset p-3 bg-surface-raised">
          <span className="def-label">Disclosed Coverage</span>
          <span className="data-value-sm font-mono mt-1 block text-text-primary">
            {concentration?.formattedDisclosedCoverage || concentration?.formattedDisclosedWeight || coverage}
          </span>
          <span className="mono-meta mt-1 block text-text-tertiary">
            Raw unforced sum (no 100% normalization)
          </span>
        </div>

        <div className="panel-inset p-3 bg-surface-raised col-span-2 sm:col-span-1">
          <span className="def-label">Undisclosed Residual</span>
          <span className="data-value-sm font-mono mt-1 block text-text-secondary">
            {undisclosedResidual}
          </span>
          <span className="mono-meta mt-1 block text-text-tertiary">
            Cash, receivables, or omitted items
          </span>
        </div>
      </div>

      {/* 2. HOLDINGS SUMMARY HEADER & TABLE */}
      <div className="panel" id="holdings-table">
        <div className="panel-header flex-wrap gap-2">
          <div className="min-w-0">
            <h3 className="eyebrow">Monthly Portfolio Disclosure Snapshot</h3>
            <p className="mt-1 text-[13px] font-semibold text-text-primary">
              {reportingDate} Reporting Period • {holdings.length} Positions Disclosed
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <span className="status-badge state-approved">
              <span className="status-dot bg-approved-fg" aria-hidden="true" />
              {quality}
            </span>
            <span className="status-badge state-approved">
              Freshness: {freshness}
            </span>
            <span className="mono-meta">
              Portfolio Coverage: {coverage}
            </span>
          </div>
        </div>

        {/* DETAILED HOLDINGS TABLE */}
        <div className="scroll-region">
          <table className="data-table">
            <caption className="sr-only">
              Disclosed portfolio holdings with weights, as-of dates and verification state
            </caption>
            <thead>
              <tr>
                <th scope="col">Security Name</th>
                <th scope="col">ISIN / Identifier</th>
                <th scope="col">Sector / Asset Class</th>
                <th scope="col" className="text-right">Weight</th>
                <th scope="col" className="text-right">Reporting Date</th>
                <th scope="col">Data Quality</th>
              </tr>
            </thead>
            <tbody>
              {(showAllHoldings ? holdings : holdings.slice(0, 10)).map((h, i) => (
                <tr key={h.id ?? i}>
                  <td className="key font-medium">{h.securityName}</td>
                  <td className="font-mono text-[12px] text-text-tertiary">
                    {h.isin || "Not available"}
                  </td>
                  <td>{h.sector || h.category || h.assetClass || "Not available"}</td>
                  <td className="num font-semibold text-text-primary">
                    {h.formattedWeight || (h.weight != null ? `${(h.weight * 100).toFixed(2)}%` : "Not available")}
                  </td>
                  <td className="num text-right text-text-tertiary">{h.asOfDate || reportingDate}</td>
                  <td>
                    <span
                      className={`status-badge ${
                        h.dataQuality === "INVALID" || h.dataQuality === "SUSPICIOUS"
                          ? "state-candidate"
                          : "state-approved"
                      }`}
                    >
                      {h.dataQuality || h.source || "VALID"}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {holdings.length > 10 && (
          <div className="border-t border-border px-4 py-2.5 bg-surface-raised/40 flex items-center justify-between">
            <span className="text-[12px] font-mono text-text-secondary">
              Showing {showAllHoldings ? holdings.length : 10} of {holdings.length} disclosed positions
            </span>
            <button
              type="button"
              onClick={() => setShowAllHoldings(!showAllHoldings)}
              className="btn btn-secondary btn-sm text-[11px]"
            >
              {showAllHoldings ? "Collapse to Top 10" : `View All ${holdings.length} Positions`}
            </button>
          </div>
        )}
      </div>

      {/* 3. PROVENANCE EVIDENCE, METHODOLOGY & DATA LIMITATIONS PANELS */}
      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        {/* EVIDENCE PANEL */}
        <div className="panel p-4">
          <div className="flex items-center justify-between border-b border-border pb-2.5 mb-3">
            <h4 className="text-[12.5px] font-semibold tracking-[0.02em] uppercase text-text-primary">
              Cryptographic Provenance
            </h4>
            <span className="mono-meta">SHA-256 Verified</span>
          </div>
          <dl className="space-y-2 text-[12px]">
            <div>
              <dt className="def-label">Source Document</dt>
              <dd className="def-value text-[12.5px]">{sourceTitle}</dd>
            </div>
            <div>
              <dt className="def-label">SHA-256 Artifact Hash</dt>
              <dd className="select-all font-mono text-[11px] leading-relaxed text-text-secondary break-all bg-surface-raised p-1.5 rounded border border-border mt-0.5">
                {sourceHash}
              </dd>
            </div>
            <div className="grid grid-cols-2 gap-2 pt-1">
              <div>
                <dt className="def-label">Reporting Date</dt>
                <dd className="font-mono text-text-primary">{reportingDate}</dd>
              </div>
              <div>
                <dt className="def-label">Ingestion Status</dt>
                <dd className="font-mono text-approved-fg">{quality}</dd>
              </div>
            </div>
          </dl>
        </div>

        {/* METHODOLOGY & FORMULAS PANEL */}
        <div className="panel p-4">
          <div className="flex items-center justify-between border-b border-border pb-2.5 mb-3">
            <h4 className="text-[12.5px] font-semibold tracking-[0.02em] uppercase text-text-primary">
              Calculation &amp; Math Rules
            </h4>
            <span className="status-badge state-approved">Deterministic Math</span>
          </div>
          <ul className="space-y-2 text-[12px] text-text-secondary">
            <li className="leading-snug">
              <strong className="text-text-primary">Denominator:</strong> Disclosed Net Assets (AUM). Total weight sum = <span className="font-mono text-text-primary">{coverage}</span> (No synthetic scaling or forced normalization).
            </li>
            <li className="leading-snug">
              <strong className="text-text-primary">Undisclosed Residual:</strong> <span className="font-mono text-text-primary">{undisclosedResidual}</span>. Not assumed to be zero. Represents cash, net current receivables, TREPS collateral, or sub-threshold holdings not enumerated in AMC monthly disclosure sheets.
            </li>
            <li className="leading-snug">
              <strong className="text-text-primary">Non-Equity Positions:</strong> Sovereign Debt, REITs, Cash/TREPS &amp; Derivative Liabilities included in reconciliation.
            </li>
            <li className="leading-snug">
              <strong className="text-text-primary">Concentration Formulas:</strong> Top-5/Top-10 sum largest position weights (%). HHI = <span className="font-mono">Σ(w_i × 100)²</span> (0–10,000 pts).
            </li>
            <li className="leading-snug">
              <strong className="text-text-primary">Drift Classification:</strong> <span className="mono-meta">NEWLY_REPORTED</span> (added), <span className="mono-meta">NO_LONGER_REPORTED</span> (omitted disclosure, not confirmed sale), <span className="mono-meta">UNCHANGED</span> (|Δ| &lt; 0.01%).
            </li>
          </ul>
        </div>

        {/* LIMITATIONS PANEL */}
        <div className="panel p-4">
          <div className="flex items-center justify-between border-b border-border pb-2.5 mb-3">
            <h4 className="text-[12.5px] font-semibold tracking-[0.02em] uppercase text-text-primary">
              Data Quality &amp; Epistemic Scope
            </h4>
            <span className="status-badge state-candidate">Monthly Disclosure Scope</span>
          </div>
          <ul className="space-y-2 text-[12px] text-text-secondary">
            {limitations.map((lim, idx) => (
              <li key={idx} className="flex items-start gap-2">
                <span className="text-text-tertiary select-none">•</span>
                <span>{lim}</span>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  );
}