'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { fetchBackendHealth } from '@/lib/api/health';
import { BackendHealthResponse } from '@/types/api';

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
    <header className="border-b border-zinc-800 bg-zinc-950/90 backdrop-blur-md sticky top-0 z-30">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="flex h-16 items-center justify-between">
          <div className="flex items-center gap-6">
            <Link
              href="/"
              className="flex items-center gap-3 group focus:outline-none focus-visible:ring-2 focus-visible:ring-cyan-500 rounded p-1"
            >
              <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-zinc-900 border border-zinc-800 text-cyan-400 font-mono font-bold text-base tracking-widest shadow-inner group-hover:border-cyan-500/50 transition-colors">
                Y
              </div>
              <div>
                <span className="text-base font-bold tracking-tight text-zinc-100 group-hover:text-cyan-400 transition-colors block">
                  YUKIRA
                </span>
                <span className="text-[10px] uppercase font-mono tracking-wider text-zinc-400 block -mt-0.5">
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
                    className={`px-3 py-1.5 rounded-md text-xs font-mono font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-cyan-500 ${
                      isActive
                        ? 'bg-zinc-800/80 text-cyan-400 border border-zinc-700/60'
                        : 'text-zinc-400 hover:text-zinc-100 hover:bg-zinc-900'
                    }`}
                  >
                    {link.label}
                  </Link>
                );
              })}
            </nav>
          </div>

          <div className="flex items-center gap-3">
            {/* Live API Health Status */}
            <div
              className="inline-flex items-center gap-2 rounded-full px-2.5 py-1 text-[11px] border border-zinc-800 bg-zinc-900/60 font-mono transition-colors"
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
                    ? 'bg-emerald-400 animate-pulse'
                    : isLive === false
                    ? 'bg-rose-500'
                    : 'bg-zinc-500'
                }`}
              />
              <span className="text-zinc-300">
                {isLive === true ? 'API ONLINE' : isLive === false ? 'API OFFLINE' : 'PROBING'}
              </span>
            </div>

            {/* Methodology Governance Link */}
            <Link
              href="/methodology"
              className="hidden sm:inline-flex items-center gap-1.5 px-2.5 py-1 text-[11px] font-mono uppercase rounded-md bg-zinc-900 text-zinc-300 border border-zinc-800 hover:border-zinc-700 hover:text-zinc-100 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-cyan-500"
              title="View methodology governance and analytical standards"
            >
              <span className="text-zinc-400">GOVERNANCE:</span>
              <span className="text-cyan-400 font-semibold">5 APPROVED</span>
              <span className="text-zinc-500">/</span>
              <span className="text-amber-400">CANDIDATE</span>
            </Link>

            {/* Mobile Menu Toggle Button */}
            <button
              type="button"
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              className="md:hidden inline-flex items-center justify-center p-2 rounded-lg border border-zinc-800 bg-zinc-900 text-zinc-400 hover:text-zinc-100 hover:bg-zinc-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-cyan-500"
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
            className="md:hidden border-t border-zinc-800 py-3 space-y-1 font-mono text-xs animate-in slide-in-from-top-2 duration-150"
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
                      ? 'bg-zinc-800 text-cyan-400 font-semibold'
                      : 'text-zinc-300 hover:bg-zinc-900 hover:text-zinc-100'
                  }`}
                >
                  {link.label}
                </Link>
              );
            })}
            <div className="pt-2 border-t border-zinc-800/80 px-3">
              <Link
                href="/methodology"
                className="flex items-center justify-between py-1.5 text-zinc-400 hover:text-zinc-200"
              >
                <span>Methodology Status</span>
                <span className="text-cyan-400">5 Approved / Candidate</span>
              </Link>
            </div>
          </nav>
        )}
      </div>
    </header>
  );
}
