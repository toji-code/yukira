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
      className={`panel p-4 transition-colors ${isCanonicalPilot ? 'border-border-strong' : ''}`}
    >
      <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <div className="min-w-0 flex-1 space-y-2">
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
            <span className="mono-meta">ID #{scheme.id}</span>
            <span className="font-mono text-[11px] font-medium text-accent">{scheme.code}</span>
            {isCanonicalPilot && (
              <span className="status-badge state-accent">
                <span className="status-dot" aria-hidden />
                Canonical pilot
              </span>
            )}
            <span className="status-badge state-info">{scheme.status || 'ACTIVE'}</span>
          </div>

          <h3 className="text-[15px] font-semibold tracking-[-0.01em] text-text-primary">
            <Link
              href={`/funds/${scheme.id}`}
              className="transition-colors hover:text-accent focus-visible:outline-1"
            >
              {scheme.name}
            </Link>
          </h3>

          <div className="flex flex-wrap items-center gap-x-5 gap-y-1">
            {scheme.amc && (
              <p className="mono-meta">
                AMC <span className="text-text-secondary">{scheme.amc.name}</span>
              </p>
            )}
            <p className="mono-meta">
              Inception{' '}
              <span className="text-text-secondary">{scheme.inceptionDate || 'Not available'}</span>
            </p>
            {isCanonicalPilot && (
              <p className="mono-meta">
                Coverage <span className="text-text-secondary">1,243 trading dates verified</span>
              </p>
            )}
          </div>
        </div>

        <div className="flex shrink-0 items-center gap-2">
          <button
            type="button"
            onClick={() => onToggleWatchlist(scheme.id)}
            className={`flex h-8 w-8 items-center justify-center rounded-sm border transition-colors ${
              isWatchlisted
                ? 'border-accent/50 bg-accent-muted text-accent'
                : 'border-border bg-surface text-text-tertiary hover:bg-surface-raised hover:text-text-primary'
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
          <Link href={`/funds/${scheme.id}`} className="btn btn-secondary btn-sm">
            Open profile
          </Link>
        </div>
      </div>
    </article>
  );
}