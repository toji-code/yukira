'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { ThemeToggle } from '@/components/theme/ThemeToggle';
import { useUser } from '@auth0/nextjs-auth0/client';

/**
 * Global application shell header.
 *
 * Deliberately carries navigation and account access only.
 * API health, governance counters, engine versions and other system
 * diagnostics are NOT ambient chrome — they are contextual state, reported
 * inside the panel of the screen whose data they qualify, or on demand via the
 * footer's "System status" panel.
 */
export function AppHeader() {
  const pathname = usePathname();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const { user, isLoading } = useUser();

  const [prevPathname, setPrevPathname] = useState(pathname);
  if (prevPathname !== pathname) {
    setPrevPathname(pathname);
    setMobileMenuOpen(false);
  }

  const navLinks = [
    { href: '/', label: 'Overview' },
    { href: '/funds', label: 'Explore funds' },
    { href: '/watchlist', label: 'Watchlist' },
    { href: '/portfolio', label: 'My portfolio' },
    { href: '/methodology', label: 'Methodology & governance' },
  ];

  const isActivePath = (href: string) =>
    href === '/' ? pathname === '/' : pathname === href || pathname.startsWith(`${href}/`);

  const accountName = user?.name || user?.email || '';
  const accountInitial = accountName.trim().charAt(0).toUpperCase() || '?';

  return (
    <header className="sticky top-0 z-30 border-b border-border bg-background shadow-sticky">
      <div className="container-app">
        <div className="flex h-13 items-center justify-between gap-6 py-2 md:h-14">
          <div className="flex min-w-0 items-center gap-6 lg:gap-8">
            <Link
              href="/"
              className="group flex shrink-0 items-center gap-3 rounded-sm focus-visible:outline-1"
              aria-label="YUKIRA — home"
            >
              <span
                aria-hidden
                className="flex h-7 w-7 items-center justify-center rounded-sm border border-border-strong bg-surface-inset font-mono text-[13px] text-accent transition-colors group-hover:border-accent/50"
              >
                Y
              </span>
              <span className="hidden leading-none sm:block">
                <span className="block text-[15px] font-semibold tracking-[-0.02em] text-text-primary">
                  YUKIRA
                </span>
                <span className="mt-1 block font-mono text-[11px] uppercase leading-none tracking-[0.09em] text-text-tertiary">
                  Investment intelligence
                </span>
              </span>
            </Link>

            <nav className="hidden lg:flex items-center gap-1" aria-label="Main">
              {navLinks.map((link) => {
                const isActive = isActivePath(link.href);
                return (
                  <Link
                    key={link.href}
                    href={link.href}
                    aria-current={isActive ? 'page' : undefined}
                    className={`relative rounded-sm px-3 py-2 text-[13px] font-medium transition-colors focus-visible:outline-1 ${
                      isActive
                        ? 'text-text-primary after:absolute after:inset-x-3 after:bottom-0 after:h-0.5 after:bg-accent'
                        : 'text-text-secondary hover:text-text-primary'
                    }`}
                  >
                    {link.label}
                  </Link>
                );
              })}
            </nav>
          </div>

          <div className="flex shrink-0 items-center gap-2">
            <ThemeToggle />

            {!isLoading && (
              <div className="hidden md:flex items-center">
                {user ? (
                  <div className="flex items-center gap-2">
                    <span
                      aria-hidden
                      className="flex h-7 w-7 items-center justify-center rounded-sm border border-border bg-surface-inset font-mono text-[11px] text-text-secondary"
                    >
                      {accountInitial}
                    </span>
                    <span className="max-w-[120px] truncate text-[13px] text-text-secondary">
                      {accountName}
                    </span>
                    {/* Auth0 SDK route, not an app page — a plain anchor is correct here. */}
                    <a href="/api/auth/logout" className="btn btn-secondary btn-sm">
                      Sign out
                    </a>
                  </div>
                ) : (
                  <a href="/api/auth/login" className="btn btn-primary btn-sm">
                    Sign in
                  </a>
                )}
              </div>
            )}

            <button
              type="button"
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              className="flex h-8 w-8 items-center justify-center rounded-sm border border-border bg-surface text-text-secondary transition-colors hover:bg-surface-raised hover:text-text-primary focus-visible:outline-1 lg:hidden"
              aria-controls="mobile-menu"
              aria-expanded={mobileMenuOpen}
              aria-label={mobileMenuOpen ? 'Close navigation menu' : 'Open navigation menu'}
            >
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden>
                {mobileMenuOpen ? (
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                ) : (
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
                )}
              </svg>
            </button>
          </div>
        </div>
      </div>

      {mobileMenuOpen && (
        <nav
          id="mobile-menu"
          className="border-t border-border bg-background shadow-overlay lg:hidden"
          aria-label="Mobile"
        >
          <div className="container-app py-2">
            {navLinks.map((link) => {
              const isActive = isActivePath(link.href);
              return (
                <Link
                  key={link.href}
                  href={link.href}
                  aria-current={isActive ? 'page' : undefined}
                  className={`flex min-h-11 items-center gap-3 rounded-sm px-2 text-[15px] transition-colors ${
                    isActive ? 'bg-accent-muted text-text-primary' : 'text-text-secondary'
                  }`}
                >
                  {isActive && <span className="h-3.5 w-0.5 rounded-xs bg-accent" aria-hidden />}
                  {link.label}
                </Link>
              );
            })}

            {!isLoading && (
              <div className="mt-2 border-t border-border pt-2 pb-2">
                {user ? (
                  <div className="flex items-center justify-between gap-3 px-2">
                    <span className="truncate text-[13px] text-text-secondary">{accountName}</span>
                    {/* Auth0 SDK route, not an app page — a plain anchor is correct here. */}
                    <a href="/api/auth/logout" className="btn btn-secondary btn-sm">
                      Sign out
                    </a>
                  </div>
                ) : (
                  <a href="/api/auth/login" className="btn btn-primary btn-sm w-full">
                    Sign in
                  </a>
                )}
              </div>
            )}
          </div>
        </nav>
      )}
    </header>
  );
}