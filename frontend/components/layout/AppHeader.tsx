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
    { href: '/', label: 'Overview' },
    { href: '/funds', label: 'Explore Funds' },
    { href: '/compare', label: 'Compare' },
    { href: '/discover', label: 'Goal Discovery' },
    { href: '/watchlist', label: 'Watchlist' },
    { href: '/portfolio', label: 'My Portfolio' },
    { href: '/methodology', label: 'Methodology & Governance' },
  ];

  const isActivePath = (href: string) =>
    href === '/' ? pathname === '/' : pathname === href || pathname.startsWith(`${href}/`);

  const accountName = user?.name || user?.email || '';
  const accountInitial = accountName.trim().charAt(0).toUpperCase() || '?';

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
                <div className="flex items-center gap-2.5">
                  <span className="flex h-8 w-8 items-center justify-center rounded-full bg-surface-navy-raised border border-border-gold-subtle text-xs font-semibold text-primary">
                    {accountInitial}
                  </span>
                  <span className="max-w-[120px] truncate text-xs text-text-cream-muted">
                    {accountName}
                  </span>
                  <a
                    href="/auth/logout"
                    className="rounded-full bg-surface-navy-raised px-3.5 py-1.5 text-xs font-medium text-text-cream-body border border-border-gold-subtle hover:text-primary hover:border-border-gold-bright transition-all"
                  >
                    Sign Out
                  </a>
                </div>
              ) : (
                <a
                  href="/auth/login"
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
          </div>
        </nav>
      )}
    </header>
  );
}