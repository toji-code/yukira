'use client';

import React, { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import {
  fetchPortfolioScoreHistory,
  HoldingScoreHistoryDto,
  PortfolioScoreHistoryDto,
  ScoreSnapshotDto
} from '@/lib/api/portfolio';
import { formatPercentage } from '@/lib/utils/formatters';

const DEFAULT_AS_OF_DATE = '2024-01-15';

function scoreText(snapshot?: ScoreSnapshotDto | null): string {
  if (!snapshot || snapshot.availabilityState !== 'AVAILABLE' || snapshot.score === null) {
    return 'Not available';
  }
  return snapshot.score.toFixed(2);
}

function confidenceText(snapshot?: ScoreSnapshotDto | null): string {
  if (!snapshot || snapshot.confidence === null || snapshot.confidence === undefined) {
    return 'Not available';
  }
  return snapshot.confidence.toFixed(2);
}

function statusClass(state?: string | null): string {
  if (state === 'AVAILABLE' || state === 'COMPLETE' || state === 'VALID' || state === 'CALCULATED') {
    return 'state-verified';
  }
  if (state === 'PARTIAL' || state === 'CANDIDATE') {
    return 'state-candidate';
  }
  return 'state-unavailable';
}

function firstHoldingWithEvidence(holdings: HoldingScoreHistoryDto[]): HoldingScoreHistoryDto | null {
  return holdings.find(h => h.selectedHistoricalFundScore?.availabilityState === 'AVAILABLE') ?? holdings[0] ?? null;
}

export function PortfolioScoreHistoryView() {
  const [asOfDate, setAsOfDate] = useState(DEFAULT_AS_OF_DATE);
  const [history, setHistory] = useState<PortfolioScoreHistoryDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function load(date: string) {
    try {
      setLoading(true);
      setError(null);
      setHistory(await fetchPortfolioScoreHistory(date));
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to load portfolio score history');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    let ignore = false;
    async function loadInitial() {
      try {
        const initialHistory = await fetchPortfolioScoreHistory(DEFAULT_AS_OF_DATE);
        if (!ignore) {
          setHistory(initialHistory);
        }
      } catch (err: unknown) {
        if (!ignore) {
          setError((err as Error).message || 'Failed to load portfolio score history');
        }
      } finally {
        if (!ignore) {
          setLoading(false);
        }
      }
    }
    void loadInitial();
    return () => {
      ignore = true;
    };
  }, []);

  const selectedHolding = useMemo(
    () => firstHoldingWithEvidence(history?.holdings ?? []),
    [history]
  );
  const selectedSnapshot = selectedHolding?.selectedHistoricalFundScore ?? null;

  return (
    <section className="panel mb-6 p-4 space-y-4" aria-labelledby="portfolio-score-history-title">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-border pb-3">
        <div>
          <span className="eyebrow text-accent">POINT-IN-TIME SCORE EVIDENCE</span>
          <h3 id="portfolio-score-history-title" className="mt-1 text-[15px] font-semibold text-text-primary">
            Portfolio Score History
          </h3>
          <p className="mt-1 max-w-3xl text-[12px] leading-[1.6] text-text-tertiary">
            Current portfolio score, historical fund score snapshots, and investor holding performance are separate evidence objects.
          </p>
        </div>
        <form
          className="flex flex-wrap items-end gap-2"
          onSubmit={(event) => {
            event.preventDefault();
            void load(asOfDate);
          }}
        >
          <label className="block">
            <span className="field-label">Historical as-of date</span>
            <input
              type="date"
              value={asOfDate}
              onChange={(event) => setAsOfDate(event.target.value)}
              className="field field-mono h-9"
            />
          </label>
          <button type="submit" className="btn btn-secondary h-9" disabled={loading}>
            {loading ? 'Resolving...' : 'Refresh'}
          </button>
        </form>
      </div>

      {error && (
        <div className="state-panel-error">
          <span className="font-mono text-[12px]">Error: {error}</span>
        </div>
      )}

      {loading && !history ? (
        <div className="panel-inset p-4 font-mono text-[12px] text-text-tertiary">
          Resolving point-in-time observations...
        </div>
      ) : history ? (
        <>
          <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
            <div className="panel-inset p-3">
              <div className="flex items-center justify-between gap-2">
                <span className="eyebrow">Current Portfolio Analytical Score</span>
                <span className={`status-badge ${statusClass(history.currentPortfolioScore?.scoreState)}`}>
                  {history.currentPortfolioScore?.scoreState ?? 'UNAVAILABLE'}
                </span>
              </div>
              <div className="mt-3 flex items-baseline gap-2">
                <span className="data-value-lg text-accent">
                  {history.currentPortfolioScore?.portfolioScore !== null && history.currentPortfolioScore?.portfolioScore !== undefined
                    ? history.currentPortfolioScore.portfolioScore.toFixed(2)
                    : 'Not available'}
                </span>
                <span className="font-mono text-[11px] text-text-tertiary">/ 100 current</span>
              </div>
              <dl className="mt-3 grid grid-cols-2 gap-2 font-mono text-[11px]">
                <div><dt className="def-label">Covered holdings</dt><dd>{history.currentPortfolioScore?.coveredHoldingCount ?? 0}/{history.currentPortfolioScore?.totalHoldingCount ?? 0}</dd></div>
                <div><dt className="def-label">Covered weight</dt><dd>{history.currentPortfolioScore?.coveredPortfolioWeight !== null && history.currentPortfolioScore?.coveredPortfolioWeight !== undefined ? `${history.currentPortfolioScore.coveredPortfolioWeight.toFixed(2)}%` : 'Not available'}</dd></div>
                <div><dt className="def-label">As of</dt><dd>{history.currentPortfolioScore?.asOfDate ?? 'Not available'}</dd></div>
                <div><dt className="def-label">Methodology</dt><dd>{history.currentPortfolioScore?.methodologyStatus ?? 'Not available'}</dd></div>
              </dl>
            </div>

            <div className="panel-inset p-3 border-l-4 border-l-border-strong">
              <div className="flex items-center justify-between gap-2">
                <span className="eyebrow">Historical Portfolio Score Availability</span>
                <span className={`status-badge ${statusClass(history.historicalPortfolioScore.state)}`}>
                  {history.historicalPortfolioScore.state}
                </span>
              </div>
              <p className="mt-3 font-mono text-[13px] text-text-primary">
                {history.historicalPortfolioScore.reason}
              </p>
              <p className="mt-2 text-[12px] leading-[1.6] text-text-tertiary">
                {history.historicalPortfolioScore.evidenceBoundary}
              </p>
              <p className="mt-3 font-mono text-[11px] text-text-tertiary">
                Requested date: {history.requestedAsOfDate ?? 'Not available'}
              </p>
            </div>
          </div>

          <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
            <div className="panel-inset p-3 border-l border-border">
              <span className="eyebrow">01 Current Portfolio Score</span>
              <p className="mt-2 text-[12px] text-text-secondary">Exposure-weighted aggregation using current holdings and persisted current fund scores.</p>
            </div>
            <div className="panel-inset p-3 border-l border-border">
              <span className="eyebrow">02 Historical Fund Score</span>
              <p className="mt-2 text-[12px] text-text-secondary">Persisted standalone fund snapshot selected on or before the requested date.</p>
            </div>
            <div className="panel-inset p-3 border-l border-border">
              <span className="eyebrow">03 Investor Holding Performance</span>
              <p className="mt-2 text-[12px] text-text-secondary">Investor-specific valuation and gain/loss. This does not alter the fund score.</p>
            </div>
          </div>

          <div className="overflow-x-auto rounded border border-border">
            <table className="w-full min-w-[1040px] text-left text-[11px]">
              <thead className="bg-surface-raised font-mono text-[10px] uppercase text-text-tertiary">
                <tr>
                  <th className="px-3 py-2">Fund</th>
                  <th className="px-3 py-2 text-right">Historical fund score</th>
                  <th className="px-3 py-2">State</th>
                  <th className="px-3 py-2">As-of</th>
                  <th className="px-3 py-2">Knowledge cutoff</th>
                  <th className="px-3 py-2">Run ID</th>
                  <th className="px-3 py-2">Holding performance</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border bg-surface font-mono">
                {history.holdings.map((holding) => {
                  const snapshot = holding.selectedHistoricalFundScore;
                  return (
                    <tr key={holding.schemeOptionId}>
                      <td className="px-3 py-3 font-sans text-text-primary">
                        <Link href={`/funds/${holding.schemeOptionId}`} className="font-semibold hover:text-accent">
                          {holding.fundName}
                        </Link>
                        <span className="block font-mono text-[10px] text-text-tertiary">
                          scheme_option_id={holding.schemeOptionId} | AMFI {holding.amfiCode} | {holding.isin}
                        </span>
                      </td>
                      <td className="px-3 py-3 text-right text-accent">{scoreText(snapshot)}</td>
                      <td className="px-3 py-3"><span className={`status-badge ${statusClass(snapshot.availabilityState)}`}>{snapshot.availabilityState}</span></td>
                      <td className="px-3 py-3">{snapshot.asOfDate ?? 'Not available'}</td>
                      <td className="px-3 py-3">{snapshot.knowledgeCutoffTime ?? 'Not available'}</td>
                      <td className="px-3 py-3">{snapshot.calculationRunId ?? 'Not available'}</td>
                      <td className="px-3 py-3 text-text-secondary">
                        {holding.holdingPerformance.absoluteGainLossPercentage !== null
                          ? formatPercentage(holding.holdingPerformance.absoluteGainLossPercentage)
                          : 'Not available'}
                        <span className="block text-[10px] text-text-tertiary">
                          P&L only; not a score input
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {selectedHolding && (
            <div className="panel-inset p-3">
              <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border pb-2">
                <div>
                  <span className="eyebrow text-accent">Persisted evidence detail</span>
                  <h4 className="mt-1 text-[13px] font-semibold text-text-primary">{selectedHolding.fundName}</h4>
                </div>
                <span className="font-mono text-[11px] text-text-tertiary">
                  Confidence: {confidenceText(selectedSnapshot)} | Reference: {selectedSnapshot?.referencePopulation ?? 'Not available'}
                </span>
              </div>
              {selectedSnapshot?.availabilityState === 'AVAILABLE' ? (
                <div className="mt-3 grid grid-cols-1 gap-3 md:grid-cols-2">
                  {selectedSnapshot.dimensions.map((dimension) => (
                    <div key={dimension.id} className="rounded border border-border bg-surface p-3">
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-sans text-[12px] font-semibold text-text-primary">{dimension.dimensionName}</span>
                        <span className="font-mono text-[10px] text-text-tertiary">W {dimension.weight}</span>
                      </div>
                      <p className="mt-1 font-mono text-[13px] text-accent">{dimension.score !== null ? dimension.score.toFixed(2) : 'Not available'}</p>
                      <div className="mt-2 space-y-1">
                        {dimension.metricContributions.slice(0, 4).map((metric) => (
                          <div key={metric.id} className="flex items-center justify-between gap-2 border-t border-border pt-1 font-mono text-[10px]">
                            <span className="text-text-secondary">{metric.metricCode} - {metric.metricName}</span>
                            <span className="text-text-primary">{metric.contribution !== null ? metric.contribution.toFixed(4) : metric.eligibility}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="mt-3 font-mono text-[12px] text-text-tertiary">
                  {selectedSnapshot?.unavailableReason ?? 'Not available'}
                </div>
              )}
            </div>
          )}
        </>
      ) : null}
    </section>
  );
}