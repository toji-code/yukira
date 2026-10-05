'use client';

import React, { useEffect, useState } from 'react';
import { AmfiDataFreshnessDto, fetchAmfiFreshness } from '@/lib/api/ingestion';

function freshnessBadgeClass(state?: string): string {
  if (state === 'FRESH') return 'state-verified';
  if (state === 'PARTIAL') return 'state-candidate';
  if (state === 'STALE') return 'state-candidate';
  return 'state-unavailable';
}

export function DataFreshnessIndicator() {
  const [freshness, setFreshness] = useState<AmfiDataFreshnessDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;
    async function loadFreshness() {
      try {
        const data = await fetchAmfiFreshness();
        if (!ignore) {
          setFreshness(data);
        }
      } catch (err: unknown) {
        if (!ignore) {
          setError((err as Error).message || 'Failed to load data freshness status');
        }
      } finally {
        if (!ignore) {
          setLoading(false);
        }
      }
    }
    void loadFreshness();
    return () => {
      ignore = true;
    };
  }, []);

  if (loading) {
    return (
      <div className="panel-inset p-2 font-mono text-[11px] text-text-tertiary">
        Checking AMFI data freshness...
      </div>
    );
  }

  if (error || !freshness) {
    return null; // Silent graceful fallback if freshness endpoint is unavailable
  }

  const hashPreview = freshness.latestSourceHash
    ? `${freshness.latestSourceHash.substring(0, 12)}...`
    : 'Not available';

  return (
    <div className="panel-inset p-3 mb-4 space-y-2" aria-label="Data Freshness Status">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border pb-2">
        <div className="flex items-center gap-2">
          <span className="eyebrow text-accent">DATA PIPELINE FRESHNESS</span>
          <span className={`status-badge ${freshnessBadgeClass(freshness.freshnessState)}`}>
            {freshness.freshnessState}
          </span>
        </div>
        <span className="font-mono text-[11px] text-text-tertiary">
          Threshold: {freshness.freshnessThresholdDays} days
        </span>
      </div>

      <div className="grid grid-cols-2 gap-2 md:grid-cols-4 font-mono text-[11px]">
        <div>
          <span className="def-label">Latest AMFI NAV Date</span>
          <p className="mt-0.5 font-semibold text-text-primary">
            {freshness.latestNavDate ?? 'Not available'}
          </p>
        </div>
        <div>
          <span className="def-label">Total Observations</span>
          <p className="mt-0.5 text-text-primary">
            {freshness.totalNavObservationCount.toLocaleString()}
          </p>
        </div>
        <div>
          <span className="def-label">Latest Artifact Hash</span>
          <p className="mt-0.5 text-text-tertiary truncate" title={freshness.latestSourceHash ?? ''}>
            {hashPreview}
          </p>
        </div>
        <div>
          <span className="def-label">Validation Issues</span>
          <p className="mt-0.5 text-text-primary">
            {freshness.validationIssueCount}
          </p>
        </div>
      </div>

      <p className="text-[11px] leading-relaxed text-text-tertiary font-sans border-t border-border pt-2 mt-2">
        {freshness.governanceDisclaimer}
      </p>
    </div>
  );
}
