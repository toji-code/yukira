'use client';

import React from 'react';
import { useTheme } from './ThemeProvider';

export function ThemeToggle() {
  const { resolvedTheme, toggleTheme, mounted } = useTheme();

  const shell =
    'flex h-[38px] w-[38px] items-center justify-center rounded-full border border-border-gold-subtle/50 bg-surface-navy-raised/40 text-text-cream-muted transition-all duration-200 hover:scale-105 hover:bg-surface-navy-raised hover:text-primary hover:border-border-gold-subtle disabled:opacity-60 shadow-xs';

  // Prevent hydration mismatch by rendering a stable placeholder until mounted
  if (!mounted) {
    return (
      <button type="button" disabled className={shell} aria-label="Loading theme toggle">
        <span className="h-4 w-4 rounded-full bg-border-gold-subtle/40 animate-pulse" aria-hidden />
      </button>
    );
  }

  const isDark = resolvedTheme === 'dark';
  const label = isDark ? 'Switch to light theme' : 'Switch to dark theme';

  return (
    <button
      type="button"
      onClick={toggleTheme}
      className={shell}
      aria-label={label}
      title={label}
    >
      {isDark ? (
        // Sun icon for switching to light theme
        <svg
          className="h-[18px] w-[18px] text-primary"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <circle cx="12" cy="12" r="4" strokeWidth="1.75" />
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth="1.75"
            d="M12 2v2m0 16v2M4.93 4.93l1.41 1.41m11.32 11.32l1.41 1.41M2 12h2m16 0h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41"
          />
        </svg>
      ) : (
        // Moon icon for switching to dark theme
        <svg
          className="h-[18px] w-[18px] text-primary"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth="1.75"
            d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z"
          />
        </svg>
      )}
    </button>
  );
}