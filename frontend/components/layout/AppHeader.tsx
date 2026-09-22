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

  const navLinks = [
    { href: '/', label: 'Overview' },
    { href: '/funds', label: 'Explore Funds' },
    { href: '/#how-it-works', label: 'How YUKIRA Works' },
    { href: '/methodology', label: 'Methodology' },
    { href: '/#project-status', label: 'Project Status' },
  ];

  return (
    <header className="border-b border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-950 sticky top-0 z-30">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="flex h-16 items-center justify-between">
          <div className="flex items-center gap-6">
            <Link href="/" className="flex items-center gap-3 group focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 rounded">
              <div className="flex h-9 w-9 items-center justify-center rounded bg-zinc-900 text-white font-mono font-bold text-sm tracking-widest dark:bg-zinc-100 dark:text-zinc-900">
                Y
              </div>
              <div>
                <span className="text-base font-semibold tracking-tight text-zinc-900 dark:text-zinc-100 block">
                  YUKIRA
                </span>
                <span className="text-[10px] uppercase font-mono tracking-wider text-zinc-600 dark:text-zinc-400 block -mt-0.5">
                  Institutional Analytics
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
                    className={`px-3 py-1.5 rounded-md text-sm font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 ${
                      isActive
                        ? 'bg-zinc-100 text-zinc-900 dark:bg-zinc-800 dark:text-zinc-100'
                        : 'text-zinc-600 hover:text-zinc-900 hover:bg-zinc-50 dark:text-zinc-400 dark:hover:text-zinc-200 dark:hover:bg-zinc-900'
                    }`}
                  >
                    {link.label}
                  </Link>
                );
              })}
            </nav>
          </div>

          <div className="flex items-center gap-3">
            <div
              className="inline-flex items-center gap-2 rounded-full px-2.5 py-1 text-xs border font-mono transition-colors"
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
                    : 'bg-zinc-400'
                }`}
              />
              <span className="text-zinc-600 dark:text-zinc-400">
                {isLive === true ? 'API ONLINE' : isLive === false ? 'API OFFLINE' : 'PROBING'}
              </span>
            </div>

            <span className="hidden sm:inline-block px-2.5 py-1 text-[11px] font-mono uppercase rounded bg-zinc-100 text-zinc-600 border border-zinc-200 dark:bg-zinc-900 dark:text-zinc-400 dark:border-zinc-800">
              METHODOLOGY: EMPTY
            </span>
          </div>
        </div>
      </div>
    </header>
  );
}
