'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { ThemeToggle } from '@/components/theme/ThemeToggle';
import { YukiraBrandLogo } from '@/components/brand/YukiraBrandLogo';
import { useUser } from '@auth0/nextjs-auth0/client';

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
    { href: '/funds', label: 'Discover' },
    { href: '/compare', label: 'Compare' },
    { href: '/watchlist', label: 'Saved Research' },
    { href: '/portfolio', label: 'Portfolio' },
    { href: '/monitoring', label: 'Monitoring' },
    { href: '/data-status', label: 'Data Status' },
    { href: '/pricing', label: 'Plans' },
    { href: '/methodology', label: 'Methodology' },
  ];

  const isActivePath = (href: string) =>
    href === '/' ? pathname === '/' : pathname === href || pathname.startsWith(`${href}/`);

  const accountName = user?.name || user?.email || '';
  const accountInitial = accountName.trim().charAt(0).toUpperCase() || '?';

  const safeReturnTo = pathname && pathname.startsWith('/') && !pathname.startsWith('//')
    ? encodeURIComponent(pathname)
    : '';
  const loginHref = safeReturnTo ? `/auth/login?returnTo=${safeReturnTo}` : '/auth/login';

  return (
    <header className="sticky top-0 z-50 h-[68px] w-full border-b border-border-gold-subtle bg-surface-midnight/90 backdrop-blur-[16px] transition-all">
      <div className="mx-auto flex h-full max-w-[1440px] items-center justify-between px-4 sm:px-6 lg:px-10">
        {/* Brand & Main Nav */}
        <div className="flex items-center gap-6 lg:gap-8">
          <Link
            href="/"
            className="group flex items-center gap-3 transition-all"
            aria-label="YUKIRA — Investment Intelligence"
          >
            {/* Theme-Aware Tree of Life Emblem */}
            <YukiraBrandLogo variant="emblem" className="h-[38px] w-[38px] sm:h-[42px] sm:w-[42px]" priority />

            <div className="flex flex-col justify-center leading-tight">
              <span className="font-sans text-[17px] font-bold tracking-[0.04em] text-text-cream-bright transition-colors group-hover:text-primary">
                YUKIRA
              </span>
              <span className="font-mono text-[9px] uppercase tracking-[0.16em] text-primary font-semibold">
                INVESTMENT INTELLIGENCE
              </span>
            </div>
          </Link>

          {/* Desktop Navigation */}
          <nav className="hidden items-center gap-1.5 lg:flex" aria-label="Main Navigation">
            {navLinks.map((link) => {
              const isActive = isActivePath(link.href);
              return (
                <Link
                  key={link.href}
                  href={link.href}
                  aria-current={isActive ? 'page' : undefined}
                  className={`relative px-3.5 py-1.5 text-[13.5px] font-medium tracking-normal transition-all rounded-full ${
                    isActive
                      ? 'bg-surface-navy-raised/80 text-primary border border-border-gold-subtle font-semibold shadow-sm'
                      : 'text-text-cream-muted hover:text-text-cream-bright hover:bg-surface-navy-raised/40'
                  }`}
                >
                  {link.label}
                  {isActive && (
                    <span className="absolute bottom-0 left-3 right-3 h-[2px] bg-primary rounded-full" />
                  )}
                </Link>
              );
            })}
          </nav>
        </div>

        {/* Right Side Controls */}
        <div className="flex items-center gap-3 shrink-0">
          <ThemeToggle />

          {!isLoading && (
            <div className="hidden sm:flex items-center gap-2">
              {user ? (
                <div className="flex items-center gap-2">
                  <Link
                    href="/account"
                    className="flex items-center gap-2 rounded-full bg-surface-navy-raised/80 hover:bg-surface-navy-raised py-1 pl-1 pr-3 border border-border-gold-subtle hover:border-border-gold-bright transition-all group"
                    title="View Investor Account and Research Sync"
                  >
                    <span className="flex h-7 w-7 items-center justify-center rounded-full bg-surface-midnight border border-border-gold-subtle text-xs font-semibold text-primary group-hover:scale-105 transition-transform">
                      {accountInitial}
                    </span>
                    <span className="max-w-[110px] truncate text-xs text-text-cream-body font-medium group-hover:text-primary transition-colors">
                      {accountName}
                    </span>
                  </Link>
                  <a
                    href="/auth/logout"
                    className="rounded-full bg-surface-navy-raised px-3 py-1.5 text-xs font-medium text-text-cream-muted border border-border-gold-subtle hover:text-text-cream-bright hover:border-border-gold-bright transition-all"
                    title="Sign Out"
                  >
                    Sign Out
                  </a>
                </div>
              ) : (
                <a
                  href={loginHref}
                  className="flex h-[36px] items-center justify-center rounded-full bg-gradient-to-r from-[#d4af37] via-[#f2ca50] to-[#e6be44] px-5 font-sans text-[13px] font-semibold text-[#1e1704] glow-gold-sm transition-all duration-200 hover:scale-[1.02] hover:from-[#e1bc42] hover:to-[#ffd868] active:scale-[0.98]"
                >
                  Sign In
                </a>
              )}
            </div>
          )}

          {/* Mobile Hamburger Toggle */}
          <button
            type="button"
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            className="flex h-9 w-9 items-center justify-center rounded-full border border-border-gold-subtle bg-surface-navy-raised text-text-cream-muted hover:text-primary hover:border-border-gold-bright lg:hidden transition-all"
            aria-expanded={mobileMenuOpen}
            aria-label="Toggle mobile menu"
          >
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              {mobileMenuOpen ? (
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              ) : (
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
              )}
            </svg>
          </button>
        </div>
      </div>

      {/* Mobile Menu */}
      {mobileMenuOpen && (
        <nav className="absolute top-[68px] left-0 w-full border-b border-border-gold-subtle bg-surface-midnight/98 px-4 py-3 lg:hidden shadow-xl backdrop-blur-md z-50">
          <div className="flex flex-col gap-1.5">
            {navLinks.map((link) => {
              const isActive = isActivePath(link.href);
              return (
                <Link
                  key={link.href}
                  href={link.href}
                  className={`px-4 py-2 text-sm font-medium tracking-tight rounded-full transition-all ${
                    isActive
                      ? 'bg-surface-navy-raised text-primary border border-border-gold-subtle font-semibold'
                      : 'text-text-cream-muted hover:text-text-cream-bright hover:bg-surface-navy-raised/50'
                  }`}
                >
                  {link.label}
                </Link>
              );
            })}

            {/* Mobile Account & Auth Section */}
            <div className="mt-2 pt-2 border-t border-border-gold-subtle/50 flex flex-col gap-2">
              {user ? (
                <div className="flex items-center justify-between gap-2 px-2 py-1">
                  <Link
                    href="/account"
                    className="flex items-center gap-2.5 text-sm font-medium text-text-cream-bright hover:text-primary transition-colors"
                  >
                    <span className="flex h-7 w-7 items-center justify-center rounded-full bg-surface-navy-raised border border-border-gold-subtle text-xs font-semibold text-primary">
                      {accountInitial}
                    </span>
                    <div className="flex flex-col text-left">
                      <span className="truncate max-w-[180px] text-xs font-semibold">{accountName}</span>
                      <span className="text-[10px] text-text-cream-muted font-mono">Account & Sync State</span>
                    </div>
                  </Link>
                  <a
                    href="/auth/logout"
                    className="rounded-full bg-surface-navy-raised px-3 py-1 text-xs font-medium text-text-cream-muted border border-border-gold-subtle hover:text-text-cream-bright"
                  >
                    Sign Out
                  </a>
                </div>
              ) : (
                <div className="flex flex-col gap-2 p-2">
                  <a
                    href={loginHref}
                    className="flex h-[36px] items-center justify-center rounded-full bg-gradient-to-r from-[#d4af37] via-[#f2ca50] to-[#e6be44] px-5 font-sans text-xs font-semibold text-[#1e1704] text-center"
                  >
                    Sign In / Register
                  </a>
                  <span className="text-[10px] font-mono text-center text-text-cream-muted">
                    Guest mode active · Local research storage
                  </span>
                </div>
              )}
            </div>
          </div>
        </nav>
      )}
    </header>
  );
}