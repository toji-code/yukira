"use client";

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Scheme, SchemeOption } from '@/types/domain';
import { fetchSchemeOptionsBySchemeId } from '@/lib/api/schemes';
import { WatchlistResponse } from '@/lib/api/investor';

interface FundCardProps {
  scheme: Scheme;
  initialOptionId?: number;
  selectedOptionId?: number;
  initialOptions?: SchemeOption[];
  isWatchlisted: boolean;
  onToggleWatchlist: (id: number, optionId?: number, metadata?: Partial<WatchlistResponse>) => void;
  onOptionSelect?: (schemeId: number, optionId: number) => void;
  returnUrl?: string;
}

export function FundCard({
  scheme,
  initialOptionId,
  selectedOptionId: controlledSelectedOptionId,
  initialOptions,
  isWatchlisted,
  onToggleWatchlist,
  onOptionSelect,
  returnUrl,
}: FundCardProps) {
  const [options, setOptions] = useState<SchemeOption[]>(initialOptions || []);
  const [loadingOptions, setLoadingOptions] = useState<boolean>(!initialOptions || initialOptions.length === 0);
  const [internalSelectedOptionId, setInternalSelectedOptionId] = useState<number | null>(() => {
    if (controlledSelectedOptionId !== undefined) return controlledSelectedOptionId;
    if (initialOptionId) return initialOptionId;
    if (initialOptions && initialOptions.length > 0) {
      const dg =
        initialOptions.find((o) => o.plan?.planType === 'DIRECT' && o.optionType === 'GROWTH') ||
        initialOptions.find((o) => o.plan?.planType === 'DIRECT') ||
        initialOptions[0];
      return dg ? dg.id : null;
    }
    return null;
  });

  const selectedOptionId =
    controlledSelectedOptionId !== undefined ? controlledSelectedOptionId : internalSelectedOptionId;

  // Identify canonical pilot instruments
  const isHdfcFlexi = scheme.code === 'HDFC_FLEXI' || scheme.name.toLowerCase().includes('hdfc flexi cap');
  const isPpfasFlexi = scheme.code === 'PARAG_PARIKH_FLEXI_CAP_FUND' || scheme.name.toLowerCase().includes('parag parikh flexi');
  const isPpfasElss = scheme.code === 'PARAG_PARIKH_ELSS_TAX_SAVER_FUND' || scheme.name.toLowerCase().includes('parag parikh elss');
  const isPilot = isHdfcFlexi || isPpfasFlexi || isPpfasElss;

  const pilotBadge = isHdfcFlexi
    ? 'Canonical Pilot · Flexi Cap'
    : isPpfasFlexi
    ? 'Pilot Instrument · Flexi Cap'
    : isPpfasElss
    ? 'Pilot Instrument · ELSS Tax Saver'
    : null;

  // Fetch share-class options lazily if not provided
  useEffect(() => {
    if (initialOptions && initialOptions.length > 0) {
      return;
    }

    let active = true;
    fetchSchemeOptionsBySchemeId(scheme.id)
      .then((data) => {
        if (active) {
          setOptions(data);
          setLoadingOptions(false);
          if (data.length > 0) {
            setInternalSelectedOptionId((current) => {
              if (current && data.some((o) => o.id === current)) return current;
              const directGrowth =
                data.find((o) => o.plan?.planType === 'DIRECT' && o.optionType === 'GROWTH') ||
                data.find((o) => o.plan?.planType === 'DIRECT') ||
                data[0];
              return directGrowth ? directGrowth.id : null;
            });
          }
        }
      })
      .catch(() => {
        if (active) {
          setLoadingOptions(false);
        }
      });

    return () => {
      active = false;
    };
  }, [scheme.id, initialOptions]);

  const activeOption = options.find((o) => o.id === selectedOptionId) || options[0] || null;

  const handleOptionChange = (optionId: number) => {
    setInternalSelectedOptionId(optionId);
    onOptionSelect?.(scheme.id, optionId);
  };

  const handleWatchlistClick = () => {
    const metadata: Partial<WatchlistResponse> = {
      schemeCode: scheme.code,
      schemeName: scheme.name,
      amcName: scheme.amc?.name || scheme.amc?.code || 'AMC Verified',
      optionType: activeOption?.optionType || 'GROWTH',
      planType: activeOption?.plan?.planType || 'DIRECT',
      amfiCode: activeOption?.amfiCode || null,
      isin: activeOption?.isin || null,
      latestNavDate: activeOption?.latestNavDate || null,
      yukiraScore: scheme.yukiraScore?.score || null,
      yukiraScoreStatus: scheme.yukiraScore?.status || 'CANDIDATE',
    };
    onToggleWatchlist(scheme.id, activeOption?.id || undefined, metadata);
  };

  const profileHref = activeOption
    ? `/funds/${scheme.id}?option=${activeOption.id}${returnUrl ? `&from=${encodeURIComponent(returnUrl)}` : ''}`
    : `/funds/${scheme.id}${returnUrl ? `?from=${encodeURIComponent(returnUrl)}` : ''}`;
  const compareHref = activeOption ? `/compare?ids=${activeOption.id}` : `/compare?ids=${scheme.id}`;

  const hasVerifiedNav = Boolean(activeOption?.latestNavDate && (activeOption?.navObservationCount ?? 0) > 0);
  const hasDisclosedHoldings = Boolean(activeOption?.latestHoldingsDate && (activeOption?.holdingsCount ?? 0) > 0);

  return (
    <article
      data-testid={`fund-card-${scheme.id}`}
      className={`p-5 rounded-2xl bg-surface-midnight/90 border border-border-gold-subtle transition-all hover:border-border-gold-bright hover:bg-surface-navy-raised/50 shadow-lg backdrop-blur-sm glow-gold ${
        isPilot ? 'ring-1 ring-primary/40' : ''
      }`}
    >
      <div className="flex flex-col gap-4">
        {/* Header Badges & Identities */}
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex flex-wrap items-center gap-x-2.5 gap-y-1">
            <span className="font-mono text-xs text-text-cream-muted">Scheme #{scheme.id}</span>
            <span className="font-mono text-xs font-semibold text-primary">{scheme.code}</span>
            {pilotBadge && (
              <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-mono uppercase tracking-wider bg-primary/10 text-primary border border-primary/20">
                <span className="h-1.5 w-1.5 rounded-full bg-primary" aria-hidden />
                {pilotBadge}
              </span>
            )}
            <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10.5px] font-mono uppercase tracking-wider bg-surface-navy-raised text-text-cream-muted border border-border-gold-subtle">
              {scheme.status || 'ACTIVE'}
            </span>
          </div>

          {/* Watchlist / Save for Investigation */}
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={handleWatchlistClick}
              className={`flex h-8 w-8 items-center justify-center rounded-full border transition-all ${
                isWatchlisted
                  ? 'border-primary bg-primary/10 text-primary shadow-xs'
                  : 'border-border-gold-subtle bg-surface-navy-raised text-text-cream-muted hover:bg-surface-navy-hover hover:text-primary'
              }`}
              title={isWatchlisted ? 'Remove from Research Workspace' : 'Save for Investigation'}
              aria-label={isWatchlisted ? 'Remove from Research Workspace' : 'Save for Investigation'}
              aria-pressed={isWatchlisted}
            >
              <svg
                className="h-4 w-4"
                fill={isWatchlisted ? 'currentColor' : 'none'}
                viewBox="0 0 24 24"
                stroke="currentColor"
                aria-hidden
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={isWatchlisted ? 1 : 2}
                  d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z"
                />
              </svg>
            </button>
          </div>
        </div>

        {/* Scheme Name & Fund House */}
        <div>
          <h3 className="text-base sm:text-lg font-bold tracking-tight text-text-primary">
            <Link
              href={profileHref}
              className="transition-colors hover:text-primary"
            >
              {scheme.name}
            </Link>
          </h3>
          <div className="mt-1.5 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-text-secondary">
            {scheme.amc && (
              <p className="font-mono">
                AMC <span className="font-semibold text-text-primary">{scheme.amc.name}</span>
              </p>
            )}
            {scheme.category && (
              <p className="font-mono">
                Category <span className="font-semibold text-text-primary">{scheme.category}</span>
              </p>
            )}
            <p className="font-mono">
              Inception <span className="font-semibold text-text-primary">{scheme.inceptionDate || 'Not available'}</span>
            </p>
          </div>
        </div>

        {/* Share Class Option Selector */}
        <div className="rounded-xl border border-border-gold-subtle bg-surface-navy-raised/40 p-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="font-mono text-[11px] uppercase tracking-wider text-text-tertiary">
              Share Class / Plan
            </span>
            {activeOption && (
              <span className="font-mono text-[11px] text-text-secondary">
                Option ID <strong className="text-text-primary font-semibold">#{activeOption.id}</strong>
              </span>
            )}
          </div>

          {loadingOptions ? (
            <div className="mt-2 flex items-center gap-2 py-1">
              <span className="h-3 w-3 animate-spin rounded-full border-2 border-primary border-t-transparent" />
              <span className="font-mono text-xs text-text-tertiary">Resolving registered share classes…</span>
            </div>
          ) : options.length > 0 ? (
            <div className="mt-2 space-y-2">
              <div className="flex flex-wrap gap-1.5" role="group" aria-label="Select share class">
                {options.map((opt) => {
                  const isSelected = opt.id === activeOption?.id;
                  const planLabel = opt.plan?.planType === 'DIRECT' ? 'Direct' : 'Regular';
                  const optionLabel = opt.optionType === 'GROWTH' ? 'Growth' : 'IDCW';

                  return (
                    <button
                      key={opt.id}
                      type="button"
                      onClick={() => handleOptionChange(opt.id)}
                      className={`px-2.5 py-1 rounded-lg font-mono text-xs transition-all border ${
                        isSelected
                          ? 'bg-primary/15 border-primary text-primary font-semibold'
                          : 'bg-surface-obsidian border-border-gold-subtle text-text-cream-body hover:text-text-cream-bright hover:border-primary/40'
                      }`}
                      aria-pressed={isSelected}
                    >
                      <span>{planLabel} · {optionLabel}</span>
                    </button>
                  );
                })}
              </div>

              {/* Exact Identifiers of Selected Option */}
              {activeOption && (
                <div className="flex flex-wrap items-center gap-x-4 gap-y-1 pt-1 font-mono text-[11px] text-text-cream-muted border-t border-border-gold-subtle/50">
                  <span>
                    AMFI <strong className="text-text-primary">{activeOption.amfiCode || 'Not available'}</strong>
                  </span>
                  <span>
                    ISIN <strong className="text-text-primary">{activeOption.isin || 'Not available'}</strong>
                  </span>
                  <span>
                    Plan <strong className="text-text-primary">{activeOption.plan?.planType || 'DIRECT'}</strong>
                  </span>
                  <span>
                    Option <strong className="text-text-primary">{activeOption.optionType || 'GROWTH'}</strong>
                  </span>
                </div>
              )}
            </div>
          ) : (
            <p className="mt-1 font-mono text-xs text-text-disabled">No registered share classes found.</p>
          )}
        </div>

        {/* Factual Evidence & Freshness Status (Strict Dual Cadence) */}
        <div className="rounded-xl border border-border-gold-subtle bg-surface-navy-raised/50 p-3 text-xs">
          <div className="flex flex-wrap items-center justify-between gap-2 mb-2">
            <span className="font-mono text-[10.5px] uppercase tracking-wider text-text-tertiary">
              Data Quality &amp; Cadence Freshness
            </span>
            <div className="flex items-center gap-1.5 font-mono text-[10px]">
              {/* NAV Freshness Badge */}
              {activeOption?.navFreshnessState === 'CURRENT' ? (
                <span className="px-2 py-0.5 rounded font-semibold bg-emerald-500/15 text-emerald-700 border border-emerald-500/30 dark:bg-emerald-950/40 dark:text-emerald-300 dark:border-emerald-800">
                  NAV · CURRENT
                </span>
              ) : activeOption?.navFreshnessState === 'STALE' ? (
                <span className="px-2 py-0.5 rounded font-semibold bg-amber-500/15 text-amber-800 border border-amber-500/30 dark:bg-amber-950/40 dark:text-amber-300 dark:border-amber-800">
                  NAV · STALE
                </span>
              ) : (
                <span className="px-2 py-0.5 rounded font-mono bg-surface-raised text-text-disabled border border-border">
                  NAV · {activeOption?.navFreshnessState || 'UNAVAILABLE'}
                </span>
              )}

              {/* Holdings Freshness Badge */}
              {activeOption?.holdingsFreshnessState === 'CURRENT' ? (
                <span className="px-2 py-0.5 rounded font-semibold bg-emerald-500/15 text-emerald-700 border border-emerald-500/30 dark:bg-emerald-950/40 dark:text-emerald-300 dark:border-emerald-800">
                  Holdings · CURRENT
                </span>
              ) : activeOption?.holdingsFreshnessState === 'PARTIAL' ? (
                <span className="px-2 py-0.5 rounded font-semibold bg-amber-500/15 text-amber-800 border border-amber-500/30 dark:bg-amber-950/40 dark:text-amber-300 dark:border-amber-800">
                  Holdings · PARTIAL
                </span>
              ) : activeOption?.holdingsFreshnessState === 'STALE' ? (
                <span className="px-2 py-0.5 rounded font-semibold bg-amber-500/15 text-amber-800 border border-amber-500/30 dark:bg-amber-950/40 dark:text-amber-300 dark:border-amber-800">
                  Holdings · STALE
                </span>
              ) : (
                <span className="px-2 py-0.5 rounded font-mono bg-surface-raised text-text-disabled border border-border">
                  Holdings · {activeOption?.holdingsFreshnessState || 'UNAVAILABLE'}
                </span>
              )}
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 font-mono text-[11px]">
            <div>
              <span className="text-text-tertiary block">Market NAV History:</span>
              {hasVerifiedNav ? (
                <span className="text-text-primary font-medium">
                  As of {activeOption?.latestNavDate} ({activeOption?.navObservationCount?.toLocaleString()} dates)
                </span>
              ) : (
                <span className="text-amber-700 dark:text-amber-400">
                  Not available
                </span>
              )}
              {activeOption?.navFreshnessExplanation && (
                <span className="text-[10px] text-text-tertiary block mt-0.5">
                  {activeOption.navFreshnessExplanation}
                </span>
              )}
            </div>

            <div>
              <span className="text-text-tertiary block">Monthly Holdings:</span>
              {hasDisclosedHoldings ? (
                <span className="text-text-primary font-medium">
                  As of {activeOption?.latestHoldingsDate} ({activeOption?.holdingsCount} positions
                  {activeOption?.holdingsRawSumPercentage ? ` · ${activeOption.holdingsRawSumPercentage.toFixed(1)}% raw` : ''})
                </span>
              ) : (
                <span className="text-text-disabled">
                  Not available
                </span>
              )}
              {activeOption?.snapshotCount !== undefined && activeOption.snapshotCount !== null && (
                <span className="text-[10px] text-text-tertiary block mt-0.5">
                  {activeOption.snapshotCount} snapshot{activeOption.snapshotCount === 1 ? ' (drift requires ≥2)' : 's available'}
                </span>
              )}
            </div>
          </div>

          <div className="mt-2.5 pt-2 border-t border-border-gold-subtle/40 flex items-center justify-between text-[11px] font-mono">
            <span className="text-text-tertiary">Analytical Readiness</span>
            {hasVerifiedNav ? (
              <span className="inline-flex items-center gap-1 text-primary">
                <span className="h-1.5 w-1.5 rounded-full bg-primary" aria-hidden />
                Verified Institutional Profile Available
              </span>
            ) : (
              <span className="text-text-disabled">
                Catalog Master Only · Awaiting Observation Feed
              </span>
            )}
          </div>
        </div>

        {/* Compact YUKIRA Analytical Score Summary */}
        <div className="rounded-xl border border-border-gold-subtle bg-surface-navy-raised/50 p-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <span className="font-mono text-xs uppercase tracking-wider text-text-tertiary">
                YUKIRA Analytical Score
              </span>
              {scheme.yukiraScore?.methodologyStatus && (
                <span className="px-2.5 py-0.5 rounded-full font-mono text-[10px] uppercase tracking-wider bg-surface-obsidian border border-border-gold-subtle text-text-secondary">
                  {scheme.yukiraScore.methodologyStatus}
                </span>
              )}
            </div>
            {scheme.yukiraScore?.asOfDate && (
              <span className="font-mono text-xs text-text-tertiary">
                As of {scheme.yukiraScore.asOfDate}
              </span>
            )}
          </div>

          <div className="mt-2 flex flex-wrap items-baseline gap-3">
            {scheme.yukiraScore && scheme.yukiraScore.score !== null && scheme.yukiraScore.score !== undefined && scheme.yukiraScore.status !== 'INSUFFICIENT_DATA' ? (
              <>
                <div className="flex items-baseline gap-1">
                  <span className="font-mono text-xl font-bold tracking-tight text-text-primary">
                    {scheme.yukiraScore.score.toFixed(2)}
                  </span>
                  <span className="font-mono text-xs text-text-tertiary">/ 100</span>
                </div>

                <span className="px-2.5 py-0.5 rounded-full font-mono text-xs uppercase tracking-wider bg-status-approved-bg text-status-approved border border-status-approved/30">
                  {scheme.yukiraScore.status}
                </span>

                {scheme.yukiraScore.confidence !== null && scheme.yukiraScore.confidence !== undefined && (
                  <span className="font-mono text-xs text-text-secondary">
                    Evidence Confidence: <strong className="text-text-primary">{scheme.yukiraScore.confidence.toFixed(1)}</strong>
                  </span>
                )}
              </>
            ) : scheme.yukiraScore?.status === 'INSUFFICIENT_DATA' ? (
              <div className="flex items-center gap-2">
                <span className="font-mono text-xs text-text-disabled">Analytical score unavailable</span>
                <span className="px-2.5 py-0.5 rounded-full font-mono text-[10px] uppercase bg-surface-navy-raised text-text-tertiary border border-border-gold-subtle">Insufficient Data</span>
              </div>
            ) : scheme.yukiraScore?.status === 'NOT_APPLICABLE' ? (
              <div className="flex items-center gap-2">
                <span className="font-mono text-xs text-text-disabled">Not currently evaluated</span>
                <span className="px-2.5 py-0.5 rounded-full font-mono text-[10px] uppercase bg-surface-navy-raised text-text-tertiary border border-border-gold-subtle">Not Applicable</span>
              </div>
            ) : (
              <div className="flex items-center gap-2">
                <span className="font-mono text-xs text-text-disabled">Analytical score unavailable</span>
                <span className="px-2.5 py-0.5 rounded-full font-mono text-[10px] uppercase bg-surface-navy-raised text-text-tertiary border border-border-gold-subtle">No Snapshot</span>
              </div>
            )}
          </div>
        </div>

        {/* Card Footer Actions */}
        <div className="flex flex-wrap items-center justify-between gap-2 pt-2 border-t border-border-gold-subtle/60">
          <Link
            href={compareHref}
            className="font-mono text-xs text-text-cream-muted hover:text-primary transition-colors flex items-center gap-1"
          >
            + Compare Share Class
          </Link>
          <Link
            href={profileHref}
            className="rounded-full bg-gradient-to-r from-[#d4af37] via-[#f2ca50] to-[#e6be44] text-[#1e1704] px-4 py-1.5 font-sans text-xs font-semibold glow-gold-sm transition-all duration-200 hover:scale-[1.02] hover:from-[#e1bc42] hover:to-[#ffd868]"
          >
            Open Profile →
          </Link>
        </div>
      </div>
    </article>
  );
}