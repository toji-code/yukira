import Link from 'next/link';
import { Scheme } from '@/types/domain';

interface FundCardProps {
  scheme: Scheme;
  isWatchlisted: boolean;
  onToggleWatchlist: (id: number) => void;
}

export function FundCard({ scheme, isWatchlisted, onToggleWatchlist }: FundCardProps) {
  const isCanonicalPilot =
    scheme.code === 'HDFC_FLEXI' || scheme.name.toLowerCase().includes('hdfc flexi cap');

  return (
    <article
      className={`p-5 rounded-2xl bg-surface border border-border transition-all hover:border-text-primary/40 dark:bg-[#1c1b1d] dark:border-[#2a2a2b] dark:hover:border-[#50d8e9]/50 ${
        isCanonicalPilot ? 'ring-1 ring-accent/30 dark:ring-[#50d8e9]/40' : ''
      }`}
    >
      <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <div className="min-w-0 flex-1 space-y-2.5">
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5">
            <span className="font-mono text-xs text-text-tertiary">ID #{scheme.id}</span>
            <span className="font-mono text-xs font-semibold text-accent dark:text-[#50d8e9]">{scheme.code}</span>
            {isCanonicalPilot && (
              <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-mono uppercase tracking-wider bg-accent/10 text-accent border border-accent/20 dark:bg-[#00363c] dark:text-[#50d8e9] dark:border-[#00b1c2]">
                <span className="h-1.5 w-1.5 rounded-full bg-accent dark:bg-[#50d8e9]" aria-hidden />
                Canonical pilot
              </span>
            )}
            <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-[11px] font-mono uppercase tracking-wider bg-surface-raised text-text-secondary border border-border dark:bg-[#201f21] dark:border-[#2a2a2b]">
              {scheme.status || 'ACTIVE'}
            </span>
          </div>

          <h3 className="text-base sm:text-lg font-bold tracking-tight text-text-primary">
            <Link
              href={`/funds/${scheme.id}`}
              className="transition-colors hover:text-accent dark:hover:text-[#50d8e9]"
            >
              {scheme.name}
            </Link>
          </h3>

          <div className="flex flex-wrap items-center gap-x-5 gap-y-1 text-xs text-text-secondary">
            {scheme.amc && (
              <p className="font-mono">
                AMC <span className="font-semibold text-text-primary">{scheme.amc.name}</span>
              </p>
            )}
            <p className="font-mono">
              Inception{' '}
              <span className="font-semibold text-text-primary">{scheme.inceptionDate || 'Not available'}</span>
            </p>
            {isCanonicalPilot && (
              <p className="font-mono">
                Coverage <span className="font-semibold text-text-primary">1,243 trading dates verified</span>
              </p>
            )}
          </div>

          {/* Compact YUKIRA Analytical Score Summary */}
          <div className="mt-3 rounded-xl border border-border bg-surface-raised/60 p-3 dark:bg-[#0e0e0f] dark:border-[#2a2a2b]">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <span className="font-mono text-xs uppercase tracking-wider text-text-tertiary">
                  YUKIRA Analytical Score
                </span>
                {scheme.yukiraScore?.methodologyStatus && (
                  <span className="px-2.5 py-0.5 rounded-full font-mono text-[10px] uppercase tracking-wider bg-surface border border-border text-text-secondary dark:bg-[#1c1b1d] dark:border-[#2a2a2b]">
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

                  <span className="px-2.5 py-0.5 rounded-full font-mono text-xs uppercase tracking-wider bg-[#e6f4ec] text-[#1f7a4d] border border-[#a8d5bd] dark:bg-[#0f2a1d] dark:text-[#5bc98c] dark:border-[#1e4433]">
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
                  <span className="px-2.5 py-0.5 rounded-full font-mono text-[10px] uppercase bg-surface-raised text-text-tertiary border border-border dark:bg-[#201f21] dark:border-[#2a2a2b]">Insufficient Data</span>
                </div>
              ) : scheme.yukiraScore?.status === 'NOT_APPLICABLE' ? (
                <div className="flex items-center gap-2">
                  <span className="font-mono text-xs text-text-disabled">Not currently evaluated</span>
                  <span className="px-2.5 py-0.5 rounded-full font-mono text-[10px] uppercase bg-surface-raised text-text-tertiary border border-border dark:bg-[#201f21] dark:border-[#2a2a2b]">Not Applicable</span>
                </div>
              ) : (
                <div className="flex items-center gap-2">
                  <span className="font-mono text-xs text-text-disabled">Analytical score unavailable</span>
                  <span className="px-2.5 py-0.5 rounded-full font-mono text-[10px] uppercase bg-surface-raised text-text-tertiary border border-border dark:bg-[#201f21] dark:border-[#2a2a2b]">No Snapshot</span>
                </div>
              )}
            </div>
          </div>
        </div>

        <div className="flex shrink-0 items-center gap-2 md:pt-1">
          <button
            type="button"
            onClick={() => onToggleWatchlist(scheme.id)}
            className={`flex h-9 w-9 items-center justify-center rounded-full border transition-all ${
              isWatchlisted
                ? 'border-accent bg-accent/10 text-accent dark:border-[#50d8e9] dark:bg-[#00363c] dark:text-[#50d8e9]'
                : 'border-border bg-surface text-text-tertiary hover:bg-surface-raised hover:text-text-primary dark:bg-[#201f21] dark:border-[#2a2a2b]'
            }`}
            title={isWatchlisted ? 'Remove from Watchlist' : 'Add to Watchlist'}
            aria-label={isWatchlisted ? 'Remove from Watchlist' : 'Add to Watchlist'}
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
          <Link
            href={`/funds/${scheme.id}`}
            className="rounded-full bg-surface-raised px-4 py-2 text-xs font-semibold text-text-primary border border-border hover:bg-surface dark:bg-[#201f21] dark:border-[#2a2a2b]"
          >
            Open Profile
          </Link>
        </div>
      </div>
    </article>
  );
}