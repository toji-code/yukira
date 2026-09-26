'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { fetchBackendHealth } from '@/lib/api/health';
import { BackendHealthResponse } from '@/types/api';
import { ThemeToggle } from '@/components/theme/ThemeToggle';

export function AppHeader() {
  const pathname = usePathname();
  const [health, setHealth] = useState<BackendHealthResponse | null>(null);
  const [isLive, setIsLive] = useState<boolean | null>(null);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  useEffect(() => {
    let mounted = true;
    fetchBackendHealth()
      .then((res) => {
        if (mounted) {
          setHealth(res);
          setIsLive(res.status === 'UP');
        }
      })
      .catch(() => {
        if (mounted) {
          setIsLive(false);
        }
      });
    return () => {
      mounted = false;
    };
  }, []);

  const [prevPathname, setPrevPathname] = useState(pathname);
  if (prevPathname !== pathname) {
    setPrevPathname(pathname);
    setMobileMenuOpen(false);
  }

  const navLinks = [
    { href: '/', label: 'Overview' },
    { href: '/funds', label: 'Explore Funds' },
    { href: '/methodology', label: 'Methodology & Governance' },
  ];

  return (
    <header className="border-b border-border bg-background/90 backdrop-blur-md sticky top-0 z-30 transition-colors">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="flex h-16 items-center justify-between">
          <div className="flex items-center gap-6">
            <Link
              href="/"
              className="flex items-center gap-3 group focus:outline-none focus-visible:ring-2 focus-visible:ring-accent rounded p-1"
            >
              <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-surface border border-border text-accent font-mono font-bold text-base tracking-widest shadow-inner group-hover:border-accent/50 transition-colors">
                Y
              </div>
              <div>
                <span className="text-base font-bold tracking-tight text-text-primary group-hover:text-accent transition-colors block">
                  YUKIRA
                </span>
                <span className="text-[10px] uppercase font-mono tracking-wider text-text-muted block -mt-0.5">
                  Investment Intelligence
                </span>
              </div>
            </Link>

            <nav className="hidden md:flex items-center gap-1" aria-label="Main Navigation">
              {navLinks.map((link) => {
                const isActive = pathname === link.href || (link.href !== '/' && pathname.startsWith(link.href));
                return (
                  <Link
                    key={link.href}
                    href={link.href}
                    className={`px-3 py-1.5 rounded-md text-xs font-mono font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent ${
                      isActive
                        ? 'bg-surface-elevated text-accent border border-border'
                        : 'text-text-secondary hover:text-text-primary hover:bg-surface'
                    }`}
                  >
                    {link.label}
                  </Link>
                );
              })}
            </nav>
          </div>

          <div className="flex items-center gap-2 sm:gap-3">
            {/* Live API Health Status */}
            <div
              className="inline-flex items-center gap-2 rounded-full px-2.5 py-1 text-[11px] border border-border bg-surface font-mono transition-colors"
              title={
                isLive === true
                  ? `Backend connected: ${health?.service || 'yukira-backend'} (${health?.version || 'active'})`
                  : isLive === false
                  ? 'Backend unavailable: Connection to API failed'
                  : 'Probing backend connection...'
              }
            >
              <span
                className={`h-2 w-2 rounded-full ${
                  isLive === true
                    ? 'bg-emerald-500 animate-pulse'
                    : isLive === false
                    ? 'bg-rose-500'
                    : 'bg-text-muted'
                }`}
              />
              <span className="text-text-secondary text-[10px] sm:text-[11px]">
                {isLive === true ? 'API ONLINE' : isLive === false ? 'API OFFLINE' : 'PROBING'}
              </span>
            </div>

            {/* Methodology Governance Link */}
            <Link
              href="/methodology"
              className="hidden sm:inline-flex items-center gap-1.5 px-2.5 py-1 text-[11px] font-mono uppercase rounded-md bg-surface text-text-secondary border border-border hover:border-border-subtle hover:bg-surface-elevated hover:text-text-primary transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent"
              title="View methodology governance and analytical standards"
            >
              <span className="text-text-muted">GOVERNANCE:</span>
              <span className="text-accent font-semibold">5 APPROVED</span>
              <span className="text-text-muted">/</span>
              <span className="text-amber-500">CANDIDATE</span>
            </Link>

            {/* Theme Toggle Button */}
            <ThemeToggle />

            {/* Mobile Menu Toggle Button */}
            <button
              type="button"
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              className="md:hidden inline-flex items-center justify-center p-2 rounded-lg border border-border bg-surface text-text-secondary hover:text-text-primary hover:bg-surface-elevated focus:outline-none focus-visible:ring-2 focus-visible:ring-accent"
              aria-controls="mobile-menu"
              aria-expanded={mobileMenuOpen}
              aria-label="Toggle navigation menu"
            >
              <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                {mobileMenuOpen ? (
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                ) : (
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
                )}
              </svg>
            </button>
          </div>
        </div>

        {/* Mobile Navigation Drawer */}
        {mobileMenuOpen && (
          <nav
            id="mobile-menu"
            className="md:hidden border-t border-border py-3 space-y-1 font-mono text-xs animate-in slide-in-from-top-2 duration-150"
            aria-label="Mobile Navigation"
          >
            {navLinks.map((link) => {
              const isActive = pathname === link.href || (link.href !== '/' && pathname.startsWith(link.href));
              return (
                <Link
                  key={link.href}
                  href={link.href}
                  className={`block px-3 py-2 rounded-md transition-colors ${
                    isActive
                      ? 'bg-surface-elevated text-accent font-semibold'
                      : 'text-text-secondary hover:bg-surface hover:text-text-primary'
                  }`}
                >
                  {link.label}
                </Link>
              );
            })}
            <div className="pt-2 border-t border-border px-3">
              <Link
                href="/methodology"
                className="flex items-center justify-between py-1.5 text-text-secondary hover:text-text-primary"
              >
                <span>Methodology Status</span>
                <span className="text-accent">5 Approved / Candidate</span>
              </Link>
            </div>
          </nav>
        )}
      </div>
    </header>
  );
}
