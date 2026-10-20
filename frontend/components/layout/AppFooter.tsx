'use client';

import React, { useCallback, useState } from 'react';
import Link from 'next/link';
import { fetchBackendHealth } from '@/lib/api/health';
import { BackendHealthResponse } from '@/types/api';

type SystemState =
  | { phase: 'idle' }
  | { phase: 'checking' }
  | { phase: 'ready'; health: BackendHealthResponse }
  | { phase: 'unreachable'; reason: string };

const LINK_COLUMNS = [
  {
    head: 'Explore',
    links: [
      { href: '/', label: 'Overview' },
      { href: '/funds', label: 'Explore funds' },
      { href: '/compare', label: 'Compare' },
      { href: '/watchlist', label: 'Watchlist' },
    ],
  },
  {
    head: 'Account',
    links: [
      { href: '/portfolio', label: 'My portfolio' },
      { href: '/auth/login', label: 'Sign in' },
    ],
  },
  {
    head: 'Methodology',
    links: [
      { href: '/methodology', label: 'Methodology & governance' },
      { href: '/funds/HDFC_FLEXI', label: 'Data lineage' },
      {
        href: 'https://www.amfiindia.com',
        label: 'AMFI portal',
        external: true,
      },
    ],
  },
] as const;

function describeError(error: unknown): string {
  if (error && typeof error === 'object' && 'code' in error) {
    return String((error as { code?: unknown }).code ?? 'UNKNOWN');
  }
  return 'UNREACHABLE';
}

/**
 * Compressed disclosure band.
 *
 * Health and engine diagnostics are opt-in here rather than ambient in the
 * header: an investor who wants to know whether the evidence layer is reachable
 * can open it, and it never competes with navigation.
 */
export function AppFooter() {
  const [open, setOpen] = useState(false);
  const [state, setState] = useState<SystemState>({ phase: 'idle' });

  const probe = useCallback(async () => {
    setState({ phase: 'checking' });
    try {
      const health = await fetchBackendHealth();
      setState({ phase: 'ready', health });
    } catch (error) {
      setState({ phase: 'unreachable', reason: describeError(error) });
    }
  }, []);

  const toggle = () => {
    const next = !open;
    setOpen(next);
    if (next && state.phase === 'idle') {
      void probe();
    }
  };

  return (
    <footer className="mt-auto w-full border-t border-border-gold-subtle bg-surface-obsidian/95 py-8 backdrop-blur-md">
      <div className="mx-auto max-w-[1440px] px-6 md:px-10">
        <div className="flex flex-col gap-8 lg:flex-row lg:items-start lg:justify-between">
          <div className="max-w-[48ch]">
            <div className="flex items-center gap-2.5">
              <span className="font-sans font-bold text-[15px] tracking-[0.04em] text-primary">
                YUKIRA
              </span>
              <span className="font-mono text-[9.5px] uppercase tracking-[0.16em] text-primary/70">
                INVESTMENT INTELLIGENCE
              </span>
            </div>
            <p className="mt-3 font-sans text-[13px] leading-[1.65] text-text-cream-muted">
              Institutional quantitative research and verification checkpoint for Indian mutual funds. Not investment advice, not a solicitation, and not a recommendation.
            </p>
            <p className="mt-2 font-mono text-[11px] text-text-cream-muted/70">
              No composite score, star rating, or commercial grade is computed or displayed.
            </p>
          </div>

          <nav
            aria-label="Footer"
            className="grid grid-cols-2 gap-8 sm:grid-cols-3 lg:gap-12"
          >
            {LINK_COLUMNS.map((column) => (
              <div key={column.head}>
                <h2 className="font-mono text-[10.5px] uppercase tracking-[0.12em] text-primary/90 font-semibold">
                  {column.head}
                </h2>
                <ul className="mt-3.5 space-y-2">
                  {column.links.map((link) => (
                    <li key={link.label}>
                      {'external' in link && link.external ? (
                        <a
                          href={link.href}
                          target="_blank"
                          rel="noreferrer"
                          className="font-sans text-[13px] text-text-cream-body transition-colors hover:text-primary"
                        >
                          {link.label}
                          <span className="sr-only"> (external)</span>
                        </a>
                      ) : (
                        <Link
                          href={link.href}
                          className="font-sans text-[13px] text-text-cream-body transition-colors hover:text-primary"
                        >
                          {link.label}
                        </Link>
                      )}
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </nav>
        </div>

        <div className="mt-8 flex flex-col gap-3 border-t border-border-gold-subtle pt-5 sm:flex-row sm:items-center sm:justify-between">
          <p className="font-mono text-[11px] text-text-cream-muted">
            Analysis cutoff and knowledge cutoff are enforced on every analytical surface.
          </p>
          <button
            type="button"
            onClick={toggle}
            className="inline-flex items-center gap-2 rounded-full border border-border-gold-subtle bg-surface-navy-raised px-3.5 py-1.5 font-sans text-[12.5px] font-medium text-text-cream-bright transition-all hover:bg-surface-navy-hover hover:border-border-gold-bright self-start"
            aria-expanded={open}
            aria-controls="system-status"
          >
            <span className="h-2 w-2 rounded-full bg-status-approved shadow-[0_0_8px_#4ade80]" />
            System status
            <svg className="h-3 w-3 text-text-cream-muted" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden>
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d={open ? 'M6 9l6 6 6-6' : 'M6 15l6-6 6 6'}
              />
            </svg>
          </button>
        </div>

        {open && (
          <section
            id="system-status"
            aria-label="System status"
            className="panel mt-4 p-4"
          >
            <div className="panel-header">
              <div>
                <p className="eyebrow">System status</p>
                <h3 className="mt-1 text-[15px] font-semibold text-text-primary">
                  Evidence layer reachability
                </h3>
              </div>
              <button type="button" onClick={probe} className="btn btn-secondary btn-sm">
                Re-check
              </button>
            </div>

            <div className="mt-4">
              {state.phase === 'checking' && (
                <div className="space-y-2" role="status" aria-live="polite">
                  <p className="mono-meta">Probing backend connection…</p>
                  <div className="skeleton h-3 w-48" />
                  <div className="skeleton h-3 w-64" />
                </div>
              )}

              {state.phase === 'ready' && (
                <div className="def-list def-list-2">
                  <div>
                    <p className="def-label">Backend</p>
                    <p className="def-value flex items-center gap-2">
                      <span
                        className={`status-badge ${
                          state.health.status === 'UP' ? 'state-approved' : 'state-critical'
                        }`}
                      >
                        <span className="status-dot" aria-hidden />
                        {state.health.status === 'UP' ? 'Reachable' : 'Degraded'}
                      </span>
                    </p>
                  </div>
                  <div>
                    <p className="def-label">Service</p>
                    <p className="def-value">{state.health.service}</p>
                  </div>
                  <div>
                    <p className="def-label">Engine version</p>
                    <p className="def-value">{state.health.version}</p>
                  </div>
                  <div>
                    <p className="def-label">Methodology status</p>
                    <p className="def-value">{state.health.methodology_status}</p>
                  </div>
                  <div className="sm:col-span-2">
                    <p className="def-label">Empirical findings</p>
                    <p className="def-value">{state.health.empirical_findings}</p>
                  </div>
                </div>
              )}

              {state.phase === 'unreachable' && (
                <div className="state-panel-error">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="status-badge state-critical">
                      <span className="status-dot" aria-hidden />
                      Unreachable
                    </span>
                    <span className="mono-meta">reason_code {state.reason}</span>
                  </div>
                  <p className="mt-2 text-[13px] leading-[1.55] text-text-secondary">
                    The evidence layer did not respond. Analytical surfaces that depend on it
                    report their own scoped failure. No value on this page is derived from an
                    unreachable service.
                  </p>
                </div>
              )}

              {state.phase === 'idle' && (
                <p className="mono-meta">Select Re-check to query the evidence layer.</p>
              )}
            </div>
          </section>
        )}
      </div>
    </footer>
  );
}