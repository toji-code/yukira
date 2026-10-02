"use client";

import React from "react";
import { useTheme } from "./ThemeProvider";

export function ThemeToggle() {
  const { resolvedTheme, toggleTheme, mounted } = useTheme();

  const shell =
    "flex h-8 w-8 items-center justify-center rounded-sm border border-border bg-surface text-text-secondary transition-colors hover:bg-surface-raised hover:text-text-primary disabled:opacity-60";

  // Prevent hydration mismatch by rendering a stable placeholder until mounted
  if (!mounted) {
    return (
      <button type="button" disabled className={shell} aria-label="Loading theme toggle">
        <span className="h-3 w-3 rounded-xs bg-border" aria-hidden />
      </button>
    );
  }

  const isDark = resolvedTheme === "dark";
  const label = isDark ? "Switch to light theme" : "Switch to dark theme";

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
          className="h-4 w-4"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth={2}
          aria-hidden="true"
        >
          <circle cx="12" cy="12" r="4" />
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M12 2v2m0 16v2M4.93 4.93l1.41 1.41m11.32 11.32l1.41 1.41M2 12h2m16 0h2M6.34 17.66l-1.41 1.41m14.14-14.14l-1.41 1.41"
          />
        </svg>
      ) : (
        // Moon icon for switching to dark theme
        <svg
          className="h-4 w-4"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth={2}
          aria-hidden="true"
        >
          <path d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z" />
        </svg>
      )}
    </button>
  );
}