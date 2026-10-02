"use client";

import React, { useState, useEffect, useCallback } from "react";
import { Holding } from "@/types/domain";
import { fetchSchemeHoldings } from "@/lib/api/schemes";

interface PortfolioHoldingsViewProps {
  schemeOptionId: number;
  schemeCode?: string;
}

export function PortfolioHoldingsView({ schemeOptionId }: PortfolioHoldingsViewProps) {
  const [holdings, setHoldings] = useState<Holding[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchSchemeHoldings(schemeOptionId);
      setHoldings(data);
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Failed to load portfolio holdings."
      );
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

  if (error || !holdings) {
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
          No holdings data available for this scheme option. (Pending regulatory feed
          ingestion)
        </p>
      </div>
    );
  }

  return (
    <div className="panel mt-6">
      <p className="eyebrow border-b border-border p-4">
        Detailed Portfolio Holdings
        <span className="ml-2 text-text-disabled">{holdings.length} positions</span>
      </p>
      <div className="scroll-region">
        <table className="data-table">
          <caption className="sr-only">
            Disclosed portfolio holdings with weights, as-of dates and verification state
          </caption>
          <thead>
            <tr>
              <th scope="col">Security Name</th>
              <th scope="col">Category/Type</th>
              <th scope="col" className="text-right">Weight</th>
              <th scope="col" className="text-right">As-Of Date</th>
              <th scope="col">Data Quality</th>
            </tr>
          </thead>
          <tbody>
            {holdings.map((h, i) => (
              <tr key={h.id ?? i}>
                <td className="key font-medium">{h.securityName}</td>
                <td>{h.category || "Not available"}</td>
                <td className="num">{(h.weight * 100).toFixed(2)}%</td>
                <td className="num text-right text-text-tertiary">{h.asOfDate}</td>
                <td>
                  <span
                    className={`status-badge ${
                      h.source === "VERIFIED" || h.source === "SOURCE_ARTIFACT_VERIFIED"
                        ? "state-approved"
                        : "state-candidate"
                    }`}
                  >
                    {h.source || "UNVERIFIED"}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}