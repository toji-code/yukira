'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { PageContainer, SectionHeading } from '@/components/layout/PageContainer';
import { SchemeOption } from '@/types/domain';
import { fetchAllSchemeOptions } from '@/lib/api/schemes';
import { executeProfileAnalysis } from '@/lib/api/analysis';
import { formatPercentage } from '@/lib/utils/formatters';
import { useUser } from '@auth0/nextjs-auth0/client';
import { fetchServerPortfolio, addServerPortfolioHolding, removeServerPortfolioHolding } from '@/lib/api/investor';

interface PortfolioHolding {
  id: string;
  schemeOptionId: number;
  schemeName: string;
  planType: string;
  optionType: string;
  units: number;
  addedAt: string;
}

export default function PortfolioPage() {
  const [holdings, setHoldings] = useState<PortfolioHolding[]>([]);
  const [options, setOptions] = useState<SchemeOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { user, isLoading: isUserLoading } = useUser();

  // Form state
  const [selectedOptionId, setSelectedOptionId] = useState<string>('');
  const [unitsInput, setUnitsInput] = useState<string>('');
  
  // Analytics state
  const [analytics, setAnalytics] = useState<Record<number, number | null>>({});

  useEffect(() => {
    let localHoldings: PortfolioHolding[] = [];
    const saved = localStorage.getItem('yukira_portfolio');
    if (saved) {
      try {
        localHoldings = JSON.parse(saved);
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setHoldings(localHoldings);
      } catch {
        console.error('Failed to parse portfolio data');
      }
    }

    // Fetch options
    const fetchOptions = async () => {
      try {
        const data = await fetchAllSchemeOptions();
        setOptions(data);

        if (!isUserLoading && user) {
          try {
            const serverData = await fetchServerPortfolio();
            const serverOptionIds = serverData.map(d => d.schemeOptionId);
            
            // Sync local to server
            const newHoldings = localHoldings.filter(h => !serverOptionIds.includes(h.schemeOptionId));
            for (const h of newHoldings) {
              await addServerPortfolioHolding(h.schemeOptionId, h.units);
            }
            if (newHoldings.length > 0) {
              localStorage.removeItem('yukira_portfolio');
            }

            // Refetch after sync
            const finalServerData = await fetchServerPortfolio();
            const mappedHoldings = finalServerData.map(d => {
              const opt = data.find(o => o.id === d.schemeOptionId);
              return {
                id: crypto.randomUUID(),
                schemeOptionId: d.schemeOptionId,
                schemeName: opt?.plan?.scheme?.name || 'Unknown',
                planType: opt?.plan?.planType || 'Unknown',
                optionType: opt?.optionType || 'Unknown',
                units: d.units,
                addedAt: new Date().toISOString()
              };
            });
            setHoldings(mappedHoldings);
          } catch (e) {
            console.error('Failed to sync server portfolio', e);
          }
        }
        setLoading(false);
      } catch (err: unknown) {
        setError((err as Error).message || 'Failed to load schemes');
        setLoading(false);
      }
    };
    if (!isUserLoading) {
      fetchOptions();
    }
  }, [user, isUserLoading]);

  // Fetch analytics for holdings
  useEffect(() => {
    holdings.forEach(holding => {
      if (analytics[holding.schemeOptionId] === undefined) {
        executeProfileAnalysis({ 
          schemeOptionId: holding.schemeOptionId,
          asOfDate: new Date().toISOString(),
          knowledgeCutoffTime: new Date().toISOString()
        })
          .then(res => {
            setAnalytics(prev => ({
              ...prev,
              [holding.schemeOptionId]: res.returnMetrics.find(m => m.metricCode === 'RET-03')?.numericValue ?? null
            }));
          })
          .catch(() => {
            setAnalytics(prev => ({
              ...prev,
              [holding.schemeOptionId]: null
            }));
          });
      }
    });
  }, [holdings, analytics]);

  const saveHoldings = async (newHoldings: PortfolioHolding[], removedId?: number) => {
    if (user) {
      try {
        if (removedId !== undefined) {
          await removeServerPortfolioHolding(removedId);
        } else {
          // It's an add or update (taking the last one added as the new one)
          const latest = newHoldings[newHoldings.length - 1];
          await addServerPortfolioHolding(latest.schemeOptionId, latest.units);
        }
        setHoldings(newHoldings);
      } catch (e) {
        console.error('Failed to sync to server', e);
      }
    } else {
      setHoldings(newHoldings);
      localStorage.setItem('yukira_portfolio', JSON.stringify(newHoldings));
    }
  };

  const handleAddHolding = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedOptionId || !unitsInput) return;

    const option = options.find(o => o.id.toString() === selectedOptionId);
    if (!option || !option.plan?.scheme) return;

    const newHolding: PortfolioHolding = {
      id: crypto.randomUUID(),
      schemeOptionId: option.id,
      schemeName: option.plan.scheme.name,
      planType: option.plan.planType,
      optionType: option.optionType,
      units: parseFloat(unitsInput),
      addedAt: new Date().toISOString()
    };

    saveHoldings([...holdings, newHolding]);
    setSelectedOptionId('');
    setUnitsInput('');
  };

  const handleRemoveHolding = (holding: PortfolioHolding) => {
    saveHoldings(holdings.filter(h => h.id !== holding.id), holding.schemeOptionId);
  };

  return (
    <PageContainer
      title="My Portfolio"
      subtitle="Track and analyze your externally held mutual funds with YUKIRA's institutional analytics."
    >
      {/* Portfolio Level Summary */}
      <div className="mb-6 grid grid-cols-1 gap-3 sm:grid-cols-3">
        <div className="metric-tile">
          <span className="eyebrow">Total Value</span>
          <div className="metric-rule" />
          <p className="data-value-lg data-unavailable">Data Unavailable</p>
          <p className="mt-auto pt-2 font-mono text-[11px] text-text-tertiary">
            Real-time NAV fetching not currently supported
          </p>
        </div>
        <div className="metric-tile">
          <span className="eyebrow">Total Holdings</span>
          <div className="metric-rule" />
          <p className="data-value-lg">{holdings.length}</p>
          <p className="mt-auto pt-2 font-mono text-[11px] text-text-tertiary">
            Externally tracked funds
          </p>
        </div>
        <div className="metric-tile">
          <span className="eyebrow">Portfolio Risk</span>
          <div className="metric-rule" />
          <p className="data-value-lg data-unavailable">Unavailable</p>
          <p className="mt-auto pt-2 font-mono text-[11px] text-text-tertiary">
            Cross-asset correlation matrix pending
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Holdings List */}
        <div className="lg:col-span-2">
          <div className="mb-4">
            <SectionHeading
              ordinal="01"
              title="Current Holdings"
              description="Externally tracked positions. YUKIRA holds no custody of these units."
            />
          </div>

          {holdings.length === 0 ? (
            <div className="state-well p-8 text-center">
              <p className="font-mono text-[12px]">No external holdings added yet.</p>
            </div>
          ) : (
            <div className="space-y-3">
              {holdings.map(holding => (
                <div key={holding.id} className="panel">
                  <div className="panel-header">
                    <div className="min-w-0">
                      <Link
                        href={`/funds/${holding.schemeOptionId}`}
                        className="text-[14px] font-semibold text-text-primary hover:text-accent"
                      >
                        {holding.schemeName}
                      </Link>
                      <div className="mt-1.5 flex flex-wrap gap-1.5">
                        <span className="status-badge state-unavailable">{holding.planType}</span>
                        <span className="status-badge state-unavailable">{holding.optionType}</span>
                        <span className="status-badge state-candidate">External Data</span>
                      </div>
                    </div>
                    <button
                      onClick={() => handleRemoveHolding(holding)}
                      className="btn btn-danger btn-sm shrink-0"
                      aria-label="Remove holding"
                    >
                      <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path>
                      </svg>
                      Remove
                    </button>
                  </div>
                  
                  <dl className="grid grid-cols-1 gap-3 border-t border-border px-4 py-3 sm:grid-cols-3">
                    <div>
                      <dt className="def-label">Units Held</dt>
                      <dd className="data-value-sm">{holding.units}</dd>
                    </div>
                    <div>
                      <dt className="def-label">Current Value</dt>
                      <dd className="data-value-sm data-unavailable">Unavailable</dd>
                    </div>
                    <div>
                      <dt className="def-label">3Y CAGR (RET-03)</dt>
                      <dd className="data-value-sm">
                        {analytics[holding.schemeOptionId] !== undefined
                          ? formatPercentage(analytics[holding.schemeOptionId])
                          : <span className="text-text-disabled">Loading…</span>}
                      </dd>
                    </div>
                  </dl>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Add Holding Form */}
        <div>
          <div className="mb-4">
            <SectionHeading ordinal="02" title="Add External Holding" />
          </div>

          <form onSubmit={handleAddHolding} className="panel">
            <div className="space-y-4 px-4 py-4">
            {loading ? (
              <div className="skeleton h-9 w-full" aria-label="Loading fund catalog" />
            ) : error ? (
              <div className="state-panel-error">
                <span className="font-mono text-[12px]">Error: {error}</span>
              </div>
            ) : (
              <>
                <div>
                  <label htmlFor="fund-select" className="field-label">
                    Select Fund Option
                  </label>
                  <select
                    id="fund-select"
                    value={selectedOptionId}
                    onChange={(e) => setSelectedOptionId(e.target.value)}
                    className="field"
                    required
                  >
                    <option value="">— Select a fund —</option>
                    {options.map(opt => (
                      <option key={opt.id} value={opt.id}>
                        {opt.plan?.scheme?.name} ({opt.plan?.planType} - {opt.optionType})
                      </option>
                    ))}
                  </select>
                </div>
                
                <div>
                  <label htmlFor="units-input" className="field-label">
                    Units Held
                  </label>
                  <input
                    id="units-input"
                    type="number"
                    step="0.001"
                    min="0"
                    value={unitsInput}
                    onChange={(e) => setUnitsInput(e.target.value)}
                    className="field field-mono"
                    placeholder="e.g. 150.5"
                    required
                  />
                </div>

                <button
                  type="submit"
                  disabled={!selectedOptionId || !unitsInput}
                  className="btn btn-primary w-full"
                >
                  Add Holding
                </button>
              </>
            )}

            <div className="panel-inset p-3 text-[11px] leading-[1.6] text-text-tertiary">
              <strong className="text-text-secondary">Note:</strong> YUKIRA operates on a
              read-only deterministic analysis engine. Externally added holdings are stored
              locally in your browser and are entirely distinct from YUKIRA-derived empirical
              data. No transaction execution is supported.
            </div>
            </div>
          </form>
        </div>
      </div>
    </PageContainer>
  );
}
