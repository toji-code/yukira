'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { ThemeToggle } from '@/components/theme/ThemeToggle';
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
    { href: '/portfolio', label: 'Portfolio' },
    { href: '/methodology', label: 'Methodology' },
  ];

  const isActivePath = (href: string) =>
    href === '/' ? pathname === '/' : pathname === href || pathname.startsWith(`${href}/`);

  const accountName = user?.name || user?.email || '';
  const accountInitial = accountName.trim().charAt(0).toUpperCase() || '?';

  return (
    <header className="sticky top-0 z-50 w-full border-b border-border bg-background/90 backdrop-blur-md transition-all">
      <div className="mx-auto max-w-[1240px] px-4 sm:px-6 lg:px-10">
        <div className="flex h-[68px] items-center justify-between gap-4">
          {/* Brand & Main Nav */}
          <div className="flex items-center gap-6 lg:gap-8">
            <Link
              href="/"
              className="group flex items-center gap-2.5 rounded-full transition-opacity hover:opacity-90"
              aria-label="YUKIRA — home"
            >
              <div className="flex h-8 w-8 items-center justify-center rounded-full bg-text-primary text-background font-bold text-sm shadow-sm dark:bg-[#5E6BFF] dark:text-white">
                Y
              </div>
              <div className="flex flex-col">
                <span className="text-[18px] font-bold tracking-tighter text-text-primary">
                  YUKIRA
                </span>
                <span className="font-mono text-[10px] uppercase tracking-widest text-text-tertiary">
                  QII v2.4.0
                </span>
              </div>
            </Link>

            {/* Desktop Navigation */}
            <nav className="hidden lg:flex items-center gap-2" aria-label="Main Navigation">
              {navLinks.map((link) => {
                const isActive = isActivePath(link.href);
                return (
                  <Link
                    key={link.href}
                    href={link.href}
                    aria-current={isActive ? 'page' : undefined}
                    className={`px-3.5 py-1.5 text-[14px] font-medium tracking-tight transition-all rounded-full ${
                      isActive
                        ? 'bg-text-primary text-background font-semibold dark:bg-[#1c1b1d] dark:text-[#50d8e9] dark:border dark:border-[#2a2a2b]'
                        : 'text-text-secondary hover:text-text-primary hover:bg-surface-raised dark:hover:bg-[#201f21]'
                    }`}
                  >
                    {link.label}
                  </Link>
                );
              })}
            </nav>
          </div>

          {/* Right Side Actions */}
          <div className="flex items-center gap-3 shrink-0">
            <ThemeToggle />

            {!isLoading && (
              <div className="hidden sm:flex items-center gap-2">
                {user ? (
                  <div className="flex items-center gap-2">
                    <span className="flex h-8 w-8 items-center justify-center rounded-full bg-surface-raised border border-border text-xs font-semibold text-text-primary dark:bg-[#201f21] dark:border-[#2a2a2b]">
                      {accountInitial}
                    </span>
                    <span className="max-w-[110px] truncate text-xs text-text-secondary">
                      {accountName}
                    </span>
                    <a
                      href="/api/auth/logout"
                      className="rounded-full bg-surface-raised px-4 py-2 text-xs font-medium text-text-primary border border-border hover:bg-surface dark:bg-[#201f21] dark:border-[#2a2a2b]"
                    >
                      Sign Out
                    </a>
                  </div>
                ) : (
                  <a
                    href="/api/auth/login"
                    className="rounded-full bg-text-primary px-5 py-2 text-xs font-medium text-background transition-opacity hover:opacity-90 dark:bg-[#5E6BFF] dark:text-white"
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
              className="flex h-9 w-9 items-center justify-center rounded-full border border-border bg-surface text-text-secondary hover:text-text-primary lg:hidden dark:bg-[#201f21] dark:border-[#2a2a2b]"
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
      </div>

      {/* Mobile Menu */}
      {mobileMenuOpen && (
        <nav className="border-t border-border bg-background px-4 py-3 lg:hidden shadow-lg">
          <div className="flex flex-col gap-1.5">
            {navLinks.map((link) => {
              const isActive = isActivePath(link.href);
              return (
                <Link
                  key={link.href}
                  href={link.href}
                  className={`px-4 py-2 text-sm font-medium tracking-tight rounded-full ${
                    isActive
                      ? 'bg-text-primary text-background font-semibold dark:bg-[#1c1b1d] dark:text-[#50d8e9]'
                      : 'text-text-secondary hover:text-text-primary hover:bg-surface-raised'
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