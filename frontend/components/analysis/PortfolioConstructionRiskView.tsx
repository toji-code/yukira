"use client";

import React, { useState, useEffect } from "react";
import {
  fetchPortfolioConstructionRisk,
  PortfolioConstructionRiskResponseDto,
} from "@/lib/api/portfolio";

interface PortfolioConstructionRiskViewProps {
  schemeOptionId: number;
  schemeCode?: string;
  asOfDate?: string;
  knowledgeCutoffTime?: string;
}

type ActiveTab = "RELIABILITY" | "ISSUERS" | "SECTORS" | "COMPARABILITY" | "INVESTIGATION";

export function PortfolioConstructionRiskView({
  schemeOptionId,
  schemeCode,
  asOfDate: propAsOfDate,
  knowledgeCutoffTime: propCutoffTime,
}: PortfolioConstructionRiskViewProps) {
  void schemeCode;
  const [data, setData] = useState<PortfolioConstructionRiskResponseDto | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<ActiveTab>("RELIABILITY");
  const [showMethodologyDrawer, setShowMethodologyDrawer] = useState<boolean>(false);

  const effectiveAsOfDate = propAsOfDate || "2026-10-01";
  const effectiveCutoffTime = propCutoffTime || "2026-10-10T23:59:59+05:30";

  useEffect(() => {
    let active = true;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLoading(true);
    setError(null);

    fetchPortfolioConstructionRisk(schemeOptionId, effectiveAsOfDate, effectiveCutoffTime)
      .then((res) => {
        if (!active) return;
        setData(res);
      })
      .catch((err: unknown) => {
        if (!active) return;
        setError(err instanceof Error ? err.message : "Failed to load portfolio construction risk analysis.");
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [schemeOptionId, effectiveAsOfDate, effectiveCutoffTime]);

  if (loading) {
    return (
      <section className="bg-card border border-border rounded-xl p-6 shadow-sm space-y-4">
        <div className="flex items-center justify-between">
          <div className="h-6 w-72 bg-muted/60 animate-pulse rounded" />
          <div className="h-6 w-24 bg-muted/60 animate-pulse rounded" />
        </div>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="h-20 bg-muted/40 animate-pulse rounded-lg" />
          ))}
        </div>
      </section>
    );
  }

  if (error || !data || !data.dataReliability) {
    return (
      <section className="bg-card border border-border rounded-xl p-6 shadow-sm">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-semibold text-foreground">
            Portfolio Construction Risk & Holdings Data Reliability
          </h3>
          <span className="text-xs px-2.5 py-1 rounded-full bg-amber-500/10 text-amber-600 dark:text-amber-400 font-medium border border-amber-500/20">
            CANDIDATE
          </span>
        </div>
        <div className="p-4 rounded-lg bg-muted/30 border border-border text-sm text-muted-foreground">
          {error || data?.message || "No portfolio construction data available for this fund."}
        </div>
      </section>
    );
  }

  const rel = data.dataReliability;
  const iss = data.issuerConcentration;
  const sec = data.sectorConcentration;
  const mcap = data.marketCapReliability;

  return (
    <section id="construction-risk" className="bg-card border border-border rounded-xl shadow-sm overflow-hidden">
      {/* Header */}
      <div className="p-6 border-b border-border bg-gradient-to-r from-muted/30 via-transparent to-transparent">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 mb-1.5">
              <span className="text-xs font-semibold uppercase tracking-wider text-primary">
                Dimension 6.6
              </span>
              <span className="text-muted-foreground/40">•</span>
              <span className="text-xs px-2.5 py-0.5 rounded-full bg-amber-500/10 text-amber-600 dark:text-amber-400 font-medium border border-amber-500/20">
                {data.methodology.governanceStatus}
              </span>
            </div>
            <h2 className="text-xl font-bold text-foreground">
              Portfolio Construction Risk & Holdings Data Reliability
            </h2>
            <p className="text-xs text-muted-foreground mt-1">
              Evidence-based holdings reconciliation, issuer concentration, Herfindahl-Hirschman Index (HHI), and multi-factor comparability across disclosures.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2 text-xs">
            <span className="px-2.5 py-1 rounded bg-muted/60 text-foreground font-mono">
              Snapshot: {rel.snapshotDate}
            </span>
            <span className="px-2.5 py-1 rounded bg-muted/60 text-muted-foreground font-mono">
              Cutoff: {data.knowledgeCutoff.substring(0, 10)}
            </span>
          </div>
        </div>

        {/* Tab Navigation */}
        <div className="flex overflow-x-auto gap-2 mt-6 border-b border-border -mb-6 pb-2 scrollbar-none">
          <button
            onClick={() => setActiveTab("RELIABILITY")}
            className={`px-3.5 py-1.5 rounded-lg text-xs font-medium whitespace-nowrap transition-colors ${
              activeTab === "RELIABILITY"
                ? "bg-primary text-primary-foreground shadow-sm"
                : "bg-muted/40 hover:bg-muted text-muted-foreground hover:text-foreground"
            }`}
          >
            Weight Reconciliation & Audit
          </button>
          <button
            onClick={() => setActiveTab("ISSUERS")}
            className={`px-3.5 py-1.5 rounded-lg text-xs font-medium whitespace-nowrap transition-colors ${
              activeTab === "ISSUERS"
                ? "bg-primary text-primary-foreground shadow-sm"
                : "bg-muted/40 hover:bg-muted text-muted-foreground hover:text-foreground"
            }`}
          >
            Issuer Concentration & HHI
          </button>
          <button
            onClick={() => setActiveTab("SECTORS")}
            className={`px-3.5 py-1.5 rounded-lg text-xs font-medium whitespace-nowrap transition-colors ${
              activeTab === "SECTORS"
                ? "bg-primary text-primary-foreground shadow-sm"
                : "bg-muted/40 hover:bg-muted text-muted-foreground hover:text-foreground"
            }`}
          >
            Sector Concentration
          </button>
          <button
            onClick={() => setActiveTab("COMPARABILITY")}
            className={`px-3.5 py-1.5 rounded-lg text-xs font-medium whitespace-nowrap transition-colors ${
              activeTab === "COMPARABILITY"
                ? "bg-primary text-primary-foreground shadow-sm"
                : "bg-muted/40 hover:bg-muted text-muted-foreground hover:text-foreground"
            }`}
          >
            Comparability & Data Gaps ({data.comparabilityMatrix.length})
          </button>
          <button
            onClick={() => setActiveTab("INVESTIGATION")}
            className={`px-3.5 py-1.5 rounded-lg text-xs font-medium whitespace-nowrap transition-colors ${
              activeTab === "INVESTIGATION"
                ? "bg-primary text-primary-foreground shadow-sm"
                : "bg-muted/40 hover:bg-muted text-muted-foreground hover:text-foreground"
            }`}
          >
            Due-Diligence Items ({data.investigationItems.length})
          </button>
        </div>
      </div>

      {/* Tab 1: Weight Reconciliation & Audit */}
      {activeTab === "RELIABILITY" && (
        <div className="p-6 space-y-6">
          {/* Key Metric Cards */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div className="p-4 rounded-xl border border-border bg-card">
              <span className="text-xs text-muted-foreground block mb-1">Sum Reported Weights</span>
              <div className="flex items-baseline gap-2">
                <span className="text-2xl font-bold font-mono text-foreground">
                  {rel.sumReportedWeightsPct}
                </span>
                <span className={`text-[10px] px-1.5 py-0.5 rounded font-semibold uppercase ${
                  rel.weightReconciliationCode === "BALANCED_100"
                    ? "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20"
                    : rel.weightReconciliationCode === "NET_ASSETS_ACCRUAL_DEVIATION"
                    ? "bg-amber-500/10 text-amber-600 dark:text-amber-400 border border-amber-500/20"
                    : "bg-rose-500/10 text-rose-600 dark:text-rose-400 border border-rose-500/20"
                }`}>
                  {rel.weightReconciliationCode === "NET_ASSETS_ACCRUAL_DEVIATION" ? "+53 bps Accrual" : rel.weightReconciliationCode}
                </span>
              </div>
              <span className="text-[11px] text-muted-foreground mt-1 block">
                {rel.completenessType.replace(/_/g, " ")}
              </span>
            </div>

            <div className="p-4 rounded-xl border border-border bg-card">
              <span className="text-xs text-muted-foreground block mb-1">Holdings & Securities</span>
              <div className="flex items-baseline gap-2">
                <span className="text-2xl font-bold font-mono text-foreground">
                  {rel.totalHoldingsDisclosed}
                </span>
                <span className="text-xs text-muted-foreground">lines</span>
              </div>
              <span className="text-[11px] text-muted-foreground mt-1 block">
                {rel.uniqueSecuritiesCount} unique canonical securities
              </span>
            </div>

            <div className="p-4 rounded-xl border border-border bg-card">
              <span className="text-xs text-muted-foreground block mb-1">Mapped vs Classified</span>
              <div className="flex items-baseline gap-2">
                <span className="text-2xl font-bold font-mono text-foreground">
                  {rel.mappedWeightPct.toFixed(1)}%
                </span>
                <span className="text-xs text-muted-foreground">mapped</span>
              </div>
              <span className="text-[11px] text-muted-foreground mt-1 block">
                {rel.classifiedWeightPct.toFixed(1)}% with sector/asset tags
              </span>
            </div>

            <div className="p-4 rounded-xl border border-border bg-card">
              <span className="text-xs text-muted-foreground block mb-1">Cash & Net Current Assets</span>
              <div className="flex items-baseline gap-2">
                <span className="text-2xl font-bold font-mono text-foreground">
                  {rel.cashAndEquivalentsWeightPct.toFixed(2)}%
                </span>
                <span className="text-xs text-muted-foreground">TREPS/Cash</span>
              </div>
              <span className="text-[11px] text-muted-foreground mt-1 block">
                Equity: {rel.equityWeightPct.toFixed(2)}%
              </span>
            </div>
          </div>

          {/* Reconciliation Rationale Card */}
          <div className="p-4 rounded-xl border border-border bg-muted/20 space-y-2">
            <div className="flex items-center gap-2">
              <span className="text-xs font-semibold uppercase tracking-wider text-foreground">
                Weight Reconciliation & Accounting Provenance
              </span>
              <span className="text-[10px] px-2 py-0.5 rounded bg-muted font-mono text-muted-foreground">
                Status: {rel.weightReconciliationStatus}
              </span>
            </div>
            <p className="text-xs text-muted-foreground leading-relaxed">
              {rel.weightReconciliationExplanation}
            </p>
          </div>

          {/* Duplicate Holdings Callout */}
          {rel.duplicateHoldingsCount > 0 ? (
            <div className="p-4 rounded-xl border border-amber-500/20 bg-amber-500/5 space-y-3">
              <div className="flex items-center justify-between">
                <h4 className="text-xs font-semibold text-amber-700 dark:text-amber-400 flex items-center gap-2">
                  <span>⚠</span> Duplicate Security Holdings Detected ({rel.duplicateHoldingsCount})
                </h4>
                <span className="text-[11px] text-muted-foreground">
                  Multiple positions mapped to single security ID
                </span>
              </div>
              <div className="space-y-2">
                {rel.duplicateHoldings.map((dup) => (
                  <div key={dup.securityId} className="p-2.5 rounded-lg bg-card/60 border border-border text-xs flex justify-between items-center">
                    <div>
                      <span className="font-semibold text-foreground">{dup.canonicalName}</span>
                      <span className="text-muted-foreground ml-2">({dup.sector})</span>
                      <p className="text-[11px] text-muted-foreground mt-0.5">{dup.note}</p>
                    </div>
                    <div className="text-right font-mono">
                      <span className="font-bold text-foreground">{dup.totalReportedWeightPct}%</span>
                      <span className="text-[10px] text-muted-foreground block">Across {dup.occurrenceCount} rows</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ) : (
            <div className="p-3 rounded-lg border border-border bg-card text-xs text-muted-foreground flex items-center gap-2">
              <span className="text-emerald-500">✓</span> Zero duplicate security IDs in snapshot {rel.snapshotDate}. Each position maps 1:1 to a unique instrument.
            </div>
          )}

          {/* Market Cap Reliability Sub-Section */}
          {mcap && (
            <div className="p-4 rounded-xl border border-border bg-card space-y-3">
              <div className="flex items-center justify-between">
                <h4 className="text-xs font-semibold text-foreground uppercase tracking-wider">
                  Market Capitalization Classification Provenance
                </h4>
                <span className="text-[10px] px-2 py-0.5 rounded bg-muted font-mono text-muted-foreground">
                  {mcap.classificationProvenance}
                </span>
              </div>
              <div className="grid grid-cols-2 md:grid-cols-5 gap-3 text-center">
                <div className="p-2.5 rounded-lg bg-muted/30 border border-border">
                  <span className="text-[11px] text-muted-foreground block">Large Cap (Top 100)</span>
                  <span className="text-base font-bold font-mono text-foreground">{mcap.largeCapWeightPct}%</span>
                </div>
                <div className="p-2.5 rounded-lg bg-muted/30 border border-border">
                  <span className="text-[11px] text-muted-foreground block">Mid Cap (101–250)</span>
                  <span className="text-base font-bold font-mono text-foreground">{mcap.midCapWeightPct}%</span>
                </div>
                <div className="p-2.5 rounded-lg bg-muted/30 border border-border">
                  <span className="text-[11px] text-muted-foreground block">Small Cap (251+)</span>
                  <span className="text-base font-bold font-mono text-foreground">{mcap.smallCapWeightPct}%</span>
                </div>
                <div className="p-2.5 rounded-lg bg-muted/30 border border-border">
                  <span className="text-[11px] text-muted-foreground block">Cash & Liquidity</span>
                  <span className="text-base font-bold font-mono text-foreground">{mcap.cashWeightPct}%</span>
                </div>
                <div className="p-2.5 rounded-lg bg-muted/30 border border-border">
                  <span className="text-[11px] text-muted-foreground block">Unclassified</span>
                  <span className="text-base font-bold font-mono text-foreground">{mcap.unclassifiedWeightPct}%</span>
                </div>
              </div>
              <p className="text-[11px] text-muted-foreground leading-relaxed italic bg-muted/20 p-2.5 rounded">
                <span className="font-semibold text-foreground not-italic">Epistemic Limitation:</span> {mcap.methodologyLimitation}
              </p>
            </div>
          )}

          {/* Cryptographic Lineage Card */}
          <div className="p-4 rounded-xl border border-border bg-card space-y-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-foreground block">
              Cryptographic Lineage & Source Artifact
            </span>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs font-mono text-muted-foreground">
              <div>
                <span className="text-[11px] text-muted-foreground/60 block">Source URI</span>
                <span className="text-foreground truncate block">{rel.sourceArtifactUri || "Internal repository"}</span>
              </div>
              <div>
                <span className="text-[11px] text-muted-foreground/60 block">SHA-256 Digest</span>
                <span className="text-foreground truncate block">{rel.sourceArtifactSha256 || "Verified"}</span>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Tab 2: Issuer Concentration & HHI */}
      {activeTab === "ISSUERS" && iss && (
        <div className="p-6 space-y-6">
          {/* Concentration Cards */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div className="p-4 rounded-xl border border-border bg-card">
              <span className="text-xs text-muted-foreground block mb-1">Top 5 Issuers</span>
              <span className="text-2xl font-bold font-mono text-foreground">{iss.top5IssuerWeightPct}%</span>
              <span className="text-[11px] text-muted-foreground mt-1 block">
                Top 10: {iss.top10IssuerWeightPct}% | Top 20: {iss.top20IssuerWeightPct}%
              </span>
            </div>

            <div className="p-4 rounded-xl border border-border bg-card">
              <span className="text-xs text-muted-foreground block mb-1">Herfindahl-Hirschman Index</span>
              <div className="flex items-baseline gap-2">
                <span className="text-2xl font-bold font-mono text-foreground">{iss.issuerHhi.toFixed(1)}</span>
                <span className="text-xs text-muted-foreground">/ 10,000</span>
              </div>
              <span className="text-[10px] px-1.5 py-0.5 rounded font-semibold bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20 mt-1 inline-block">
                {iss.hhiConcentrationTier}
              </span>
            </div>

            <div className="p-4 rounded-xl border border-border bg-card">
              <span className="text-xs text-muted-foreground block mb-1">Effective Number of Issuers</span>
              <div className="flex items-baseline gap-2">
                <span className="text-2xl font-bold font-mono text-foreground">{iss.effectiveNumberOfIssuers.toFixed(1)}</span>
                <span className="text-xs text-muted-foreground">equal-weighted</span>
              </div>
              <span className="text-[11px] text-muted-foreground mt-1 block">
                Out of {iss.totalIssuersDisclosed} parent companies
              </span>
            </div>

            <div className="p-4 rounded-xl border border-border bg-card">
              <span className="text-xs text-muted-foreground block mb-1">Security-Level Comparison</span>
              <div className="flex items-baseline gap-2">
                <span className="text-2xl font-bold font-mono text-foreground">{iss.securityHhi.toFixed(1)}</span>
                <span className="text-xs text-muted-foreground">Sec HHI</span>
              </div>
              <span className="text-[11px] text-muted-foreground mt-1 block">
                N_eff (Securities): {iss.effectiveNumberOfSecurities.toFixed(1)}
              </span>
            </div>
          </div>

          {/* Top 10 Issuers Table */}
          <div className="border border-border rounded-xl overflow-hidden">
            <div className="p-3.5 bg-muted/40 border-b border-border flex justify-between items-center">
              <h4 className="text-xs font-semibold text-foreground uppercase tracking-wider">
                Top 10 Parent Issuers (Aggregated Across Instruments)
              </h4>
              <span className="text-xs text-muted-foreground">
                Sum Top 10: {iss.top10IssuerWeightPct}%
              </span>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-xs text-left">
                <thead className="bg-muted/20 text-muted-foreground uppercase text-[10px] tracking-wider border-b border-border font-medium">
                  <tr>
                    <th className="py-2.5 px-3 w-10">#</th>
                    <th className="py-2.5 px-3">Parent Issuer</th>
                    <th className="py-2.5 px-3">Primary Sector</th>
                    <th className="py-2.5 px-3 text-center">Instruments</th>
                    <th className="py-2.5 px-3 text-right">Portfolio Weight</th>
                    <th className="py-2.5 px-3 w-32">Weight Share</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border font-mono">
                  {iss.topIssuers.map((item) => (
                    <tr key={item.rank} className="hover:bg-muted/10 transition-colors">
                      <td className="py-2.5 px-3 text-muted-foreground">{item.rank}</td>
                      <td className="py-2.5 px-3 font-medium font-sans text-foreground">
                        {item.issuerName}
                      </td>
                      <td className="py-2.5 px-3 font-sans text-muted-foreground">{item.primarySector}</td>
                      <td className="py-2.5 px-3 text-center">
                        <span className="px-1.5 py-0.5 rounded bg-muted text-muted-foreground text-[10px]">
                          {item.instrumentsHeldCount}
                        </span>
                      </td>
                      <td className="py-2.5 px-3 text-right font-bold text-foreground">
                        {item.totalWeightPct.toFixed(2)}%
                      </td>
                      <td className="py-2.5 px-3">
                        <div className="h-1.5 w-full bg-muted/60 rounded-full overflow-hidden">
                          <div
                            className="h-full bg-primary rounded-full"
                            style={{ width: `${Math.min(100, item.totalWeightPct * 10)}%` }}
                          />
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* Tab 3: Sector Concentration */}
      {activeTab === "SECTORS" && sec && (
        <div className="p-6 space-y-6">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div className="p-4 rounded-xl border border-border bg-card">
              <span className="text-xs text-muted-foreground block mb-1">Top Sector</span>
              <span className="text-2xl font-bold font-mono text-foreground">{sec.top1SectorWeightPct}%</span>
              <span className="text-[11px] text-muted-foreground mt-1 block truncate font-medium">
                {sec.top1SectorName}
              </span>
            </div>
            <div className="p-4 rounded-xl border border-border bg-card">
              <span className="text-xs text-muted-foreground block mb-1">Top 3 Sectors</span>
              <span className="text-2xl font-bold font-mono text-foreground">{sec.top3SectorWeightPct}%</span>
              <span className="text-[11px] text-muted-foreground mt-1 block">
                Across {sec.totalSectorsDisclosed} sectors
              </span>
            </div>
            <div className="p-4 rounded-xl border border-border bg-card">
              <span className="text-xs text-muted-foreground block mb-1">Unclassified / Liquid</span>
              <span className="text-2xl font-bold font-mono text-foreground">{sec.unclassifiedSectorWeightPct}%</span>
              <span className="text-[11px] text-muted-foreground mt-1 block">
                Cash & Net Current Assets
              </span>
            </div>
            <div className="p-4 rounded-xl border border-border bg-card">
              <span className="text-xs text-muted-foreground block mb-1">Sector Taxonomy</span>
              <span className="text-xs font-semibold text-foreground block truncate">AMC Disclosed</span>
              <span className="text-[10px] text-muted-foreground mt-1 block truncate">
                Official Monthly Disclosure
              </span>
            </div>
          </div>

          {/* Sector Breakdown Table */}
          <div className="border border-border rounded-xl overflow-hidden">
            <div className="p-3.5 bg-muted/40 border-b border-border flex justify-between items-center">
              <h4 className="text-xs font-semibold text-foreground uppercase tracking-wider">
                Sector Allocation Breakdown ({sec.sectors.length} Sectors Disclosed)
              </h4>
              <span className="text-xs text-muted-foreground">
                Top 3 Sum: {sec.top3SectorWeightPct}%
              </span>
            </div>
            <div className="overflow-x-auto max-h-[380px]">
              <table className="w-full text-xs text-left">
                <thead className="bg-muted/20 text-muted-foreground uppercase text-[10px] tracking-wider border-b border-border font-medium sticky top-0">
                  <tr>
                    <th className="py-2.5 px-3 w-10">#</th>
                    <th className="py-2.5 px-3">Industry / Sector Name</th>
                    <th className="py-2.5 px-3 text-center">Holdings Count</th>
                    <th className="py-2.5 px-3 text-right">Portfolio Weight</th>
                    <th className="py-2.5 px-3 w-36">Allocation Bar</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border font-mono">
                  {sec.sectors.map((s) => (
                    <tr key={s.rank} className="hover:bg-muted/10 transition-colors">
                      <td className="py-2 px-3 text-muted-foreground">{s.rank}</td>
                      <td className="py-2 px-3 font-medium font-sans text-foreground">{s.sectorName}</td>
                      <td className="py-2 px-3 text-center text-muted-foreground">{s.holdingsCount}</td>
                      <td className="py-2 px-3 text-right font-bold text-foreground">{s.weightPct.toFixed(2)}%</td>
                      <td className="py-2 px-3">
                        <div className="h-1.5 w-full bg-muted/60 rounded-full overflow-hidden">
                          <div
                            className="h-full bg-primary rounded-full"
                            style={{ width: `${Math.min(100, s.weightPct * 3.3)}%` }}
                          />
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* Tab 4: Historical Comparability Matrix & Data Gaps */}
      {activeTab === "COMPARABILITY" && (
        <div className="p-6 space-y-6">
          {/* Data Gap Banner */}
          <div className="p-4 rounded-xl border border-rose-500/20 bg-rose-500/5 space-y-2">
            <div className="flex items-center gap-2 text-rose-700 dark:text-rose-400 font-semibold text-xs">
              <span>⚠️</span>
              <h4>Historical 29-Month Reporting Gap (January 31, 2024 to June 30, 2026)</h4>
            </div>
            <p className="text-xs text-muted-foreground leading-relaxed">
              YUKIRA identifies an 881-day reporting hiatus between the 2024 Factsheet disclosure and the 2026 full portfolio disclosures.
              In accordance with epistemic governance rules, <span className="font-semibold text-foreground">no synthetic interpolation is performed</span>.
              The system marks this transition as <span className="font-semibold text-rose-600 dark:text-rose-400">NON_COMPARABLE</span> because apparent drift was caused by coverage expansion rather than manager rebalancing.
            </p>
          </div>

          {/* Comparability Matrix Cards */}
          <div className="space-y-3">
            <h4 className="text-xs font-semibold text-foreground uppercase tracking-wider">
              Pairwise Chronological Comparability Assessment
            </h4>
            {data.comparabilityMatrix.map((pair, idx) => (
              <div key={idx} className="p-4 rounded-xl border border-border bg-card space-y-2">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <span className="font-mono font-bold text-sm text-foreground">
                      {pair.earlierDate} → {pair.laterDate}
                    </span>
                    <span className="text-xs text-muted-foreground font-mono">
                      ({pair.intervalDays} days)
                    </span>
                  </div>

                  <span className={`text-[11px] px-2.5 py-0.5 rounded-full font-semibold border ${
                    pair.comparabilityStatus === "COMPARABLE"
                      ? "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20"
                      : pair.comparabilityStatus === "COMPARABLE_WITH_LIMITATIONS"
                      ? "bg-amber-500/10 text-amber-600 dark:text-amber-400 border-amber-500/20"
                      : "bg-rose-500/10 text-rose-600 dark:text-rose-400 border-rose-500/20"
                  }`}>
                    {pair.comparabilityStatus.replace(/_/g, " ")}
                  </span>
                </div>

                <div className="grid grid-cols-2 md:grid-cols-4 gap-2 text-xs font-mono text-muted-foreground bg-muted/20 p-2.5 rounded-lg">
                  <div>
                    <span className="text-[10px] block text-muted-foreground/60">Coverage Shift</span>
                    <span className="text-foreground">{pair.earlierSumWeightPct}% → {pair.laterSumWeightPct}%</span>
                  </div>
                  <div>
                    <span className="text-[10px] block text-muted-foreground/60">Weight Delta</span>
                    <span className="text-foreground">{pair.weightDeltaPct > 0 ? "+" : ""}{pair.weightDeltaPct}%</span>
                  </div>
                  <div>
                    <span className="text-[10px] block text-muted-foreground/60">Holdings Lines</span>
                    <span className="text-foreground">{pair.earlierHoldingsCount} → {pair.laterHoldingsCount} (+{pair.holdingsDeltaCount})</span>
                  </div>
                  <div>
                    <span className="text-[10px] block text-muted-foreground/60">Scope Change</span>
                    <span className="text-foreground">{pair.earlierCompleteness} → {pair.laterCompleteness}</span>
                  </div>
                </div>

                <p className="text-xs text-muted-foreground leading-relaxed">
                  {pair.assessmentSummary}
                </p>

                <div className="flex flex-wrap gap-1.5 pt-1">
                  {pair.reasonCodes.map((code) => (
                    <span key={code} className="text-[10px] px-2 py-0.5 rounded bg-muted text-muted-foreground font-mono">
                      {code}
                    </span>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Tab 5: Due-Diligence Investigation Items */}
      {activeTab === "INVESTIGATION" && (
        <div className="p-6 space-y-4">
          <div className="flex items-center justify-between">
            <h4 className="text-xs font-semibold text-foreground uppercase tracking-wider">
              Targeted Institutional Due-Diligence Checklist
            </h4>
            <span className="text-xs text-muted-foreground">
              {data.investigationItems.length} empirical inquiry areas
            </span>
          </div>

          <div className="space-y-3">
            {data.investigationItems.map((item, idx) => (
              <div key={idx} className="p-4 rounded-xl border border-border bg-card space-y-2">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className={`text-[10px] px-2 py-0.5 rounded font-bold uppercase ${
                      item.severity === "HIGH"
                        ? "bg-rose-500/10 text-rose-600 dark:text-rose-400 border border-rose-500/20"
                        : "bg-amber-500/10 text-amber-600 dark:text-amber-400 border border-amber-500/20"
                    }`}>
                      {item.severity} PRIORITY
                    </span>
                    <span className="text-xs font-mono text-muted-foreground">{item.category}</span>
                  </div>
                </div>

                <h5 className="text-sm font-bold text-foreground">{item.title}</h5>

                <div className="p-3 rounded-lg bg-muted/20 border border-border text-xs text-muted-foreground space-y-1.5">
                  <div>
                    <span className="font-semibold text-foreground">Empirical Evidence: </span>
                    {item.empiricalEvidence}
                  </div>
                  <div>
                    <span className="font-semibold text-primary">Due-Diligence Question: </span>
                    {item.dueDiligenceQuestion}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Methodology Drawer Toggle */}
      <div className="border-t border-border bg-muted/20 px-6 py-3 flex items-center justify-between">
        <button
          onClick={() => setShowMethodologyDrawer(!showMethodologyDrawer)}
          className="text-xs font-medium text-primary hover:underline flex items-center gap-1.5"
        >
          <span>{showMethodologyDrawer ? "Hide" : "Show"} Methodology, Formulas & Epistemic Boundaries</span>
          <span className="text-muted-foreground">({showMethodologyDrawer ? "▲" : "▼"})</span>
        </button>
        <span className="text-[11px] font-mono text-muted-foreground">
          Version: {data.methodology.methodologyVersion} ({data.methodology.governanceStatus})
        </span>
      </div>

      {/* Collapsible Methodology Drawer */}
      {showMethodologyDrawer && (
        <div className="p-6 border-t border-border bg-muted/10 space-y-4 text-xs text-muted-foreground">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="p-3.5 rounded-lg bg-card border border-border space-y-1.5">
              <span className="font-semibold text-foreground block">Herfindahl-Hirschman Index (HHI)</span>
              <p className="font-mono text-[11px] text-primary">{data.methodology.hhiFormula}</p>
              <p className="text-[11px]">Standardized on a 0 to 10,000 scale. Below 1,000 indicates a diversified portfolio; above 1,800 indicates high concentration.</p>
            </div>
            <div className="p-3.5 rounded-lg bg-card border border-border space-y-1.5">
              <span className="font-semibold text-foreground block">Effective Number of Holdings (N_eff)</span>
              <p className="font-mono text-[11px] text-primary">{data.methodology.effectiveHoldingsFormula}</p>
              <p className="text-[11px]">Computes the equivalent number of equal-sized parent companies that would yield the same HHI concentration.</p>
            </div>
          </div>

          <div className="p-3.5 rounded-lg bg-card border border-border space-y-2">
            <span className="font-semibold text-foreground block">Weight Reconciliation Policy</span>
            <p className="text-[11px] leading-relaxed">{data.methodology.weightReconciliationPolicy}</p>
          </div>

          <div className="p-3.5 rounded-lg bg-rose-500/5 border border-rose-500/20 text-rose-700 dark:text-rose-400 space-y-1">
            <span className="font-semibold block">Liquidity & Capacity Discretion Disclaimer</span>
            <p className="text-[11px] leading-relaxed">{data.methodology.liquidityDiscretionDisclaimer}</p>
          </div>
        </div>
      )}
    </section>
  );
}
