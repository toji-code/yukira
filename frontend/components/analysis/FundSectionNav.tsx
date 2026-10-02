"use client";

import React, { useEffect, useState, useRef } from "react";

export interface SectionNavItem {
  id: string;
  label: string;
  shortLabel: string;
  badge?: string;
}

export const FUND_SECTIONS: SectionNavItem[] = [
  { id: "identity", label: "Fund Identity", shortLabel: "Identity", badge: "Sec 1" },
  { id: "coverage", label: "Data Coverage", shortLabel: "Coverage", badge: "Sec 2" },
  { id: "return-quality", label: "Return Quality", shortLabel: "Return", badge: "Dim 1" },
  { id: "risk", label: "Total Risk", shortLabel: "Risk", badge: "Dim 2" },
  { id: "drawdown", label: "Drawdown & Stress", shortLabel: "Drawdown", badge: "Dim 3" },
  { id: "tail-risk", label: "Tail Risk", shortLabel: "Tail Risk", badge: "Dim 4" },
  { id: "risk-adjusted", label: "Risk-Adjusted", shortLabel: "Risk-Adj", badge: "Dim 5" },
  { id: "benchmark-relationship", label: "Benchmark Rel", shortLabel: "Benchmark", badge: "Dim 5.5" },
  { id: "portfolio", label: "Portfolio Structure", shortLabel: "Portfolio", badge: "Dim 6" },
  { id: "investment-modes", label: "SIP & Lumpsum", shortLabel: "SIP/Lump", badge: "Sec 3" },
  { id: "provenance", label: "AMFI Lineage", shortLabel: "Lineage" },
  { id: "data-quality", label: "Data Quality", shortLabel: "Quality" },
  { id: "decision-support", label: "Decision Support", shortLabel: "Decision" },
];

export function FundSectionNav() {
  const [activeSectionId, setActiveSectionId] = useState<string>("identity");
  const navRef = useRef<HTMLDivElement>(null);
  const isClickScrolling = useRef<boolean>(false);

  useEffect(() => {
    const sectionElements = FUND_SECTIONS.map((item) =>
      document.getElementById(item.id)
    ).filter((el): el is HTMLElement => el !== null);

    if (sectionElements.length === 0) return;

    const observerCallback: IntersectionObserverCallback = (entries) => {
      if (isClickScrolling.current) return;

      // Find the first section intersecting near top of viewport
      const visibleSection = entries.find((entry) => entry.isIntersecting);
      if (visibleSection && visibleSection.target.id) {
        setActiveSectionId(visibleSection.target.id);
      }
    };

    // Root margin creates a trigger band near the top of the screen (under sticky header)
    const observer = new IntersectionObserver(observerCallback, {
      root: null,
      rootMargin: "-80px 0px -70% 0px",
      threshold: 0,
    });

    sectionElements.forEach((el) => observer.observe(el));

    return () => {
      observer.disconnect();
    };
  }, []);

  const navigateTo = (sectionId: string) => {
    setActiveSectionId(sectionId);
    isClickScrolling.current = true;

    const targetEl = document.getElementById(sectionId);
    if (targetEl) {
      targetEl.scrollIntoView({ behavior: "smooth", block: "start" });
      if (typeof window !== "undefined" && window.history?.pushState) {
        window.history.pushState(null, "", `#${sectionId}`);
      }
    }

    setTimeout(() => {
      isClickScrolling.current = false;
    }, 800);
  };

  const handleNavClick = (
    e: React.MouseEvent<HTMLAnchorElement>,
    sectionId: string
  ) => {
    e.preventDefault();
    navigateTo(sectionId);
  };

  const handleSelectChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const sectionId = e.target.value;
    if (!sectionId) return;
    navigateTo(sectionId);
  };

  return (
    <nav
      aria-label="Fund Profile Sections"
      className="scroll-region sticky top-0 z-30 mb-8 border-b border-border bg-background shadow-sticky"
    >
      <div className="tab-rail mx-auto max-w-7xl items-center justify-between gap-3">
        {/* Mobile Select Fallback (< sm) */}
        <div className="flex w-full items-center gap-2 sm:hidden">
          <span className="eyebrow shrink-0">Section</span>
          <select
            value={activeSectionId}
            onChange={handleSelectChange}
            aria-label="Select section to navigate"
            className="field font-mono text-[12px]"
          >
            {FUND_SECTIONS.map((item) => (
              <option key={item.id} value={item.id}>
                {item.badge ? `[${item.badge}] ` : ""}{item.label}
              </option>
            ))}
          </select>
        </div>

        {/* Desktop / Tablet Rail (>= sm) */}
        <div
          ref={navRef}
          className="scroll-region hidden w-full items-center overflow-x-auto sm:flex"
        >
          {FUND_SECTIONS.map((item) => {
            const isActive = activeSectionId === item.id;
            return (
              <a
                key={item.id}
                href={`#${item.id}`}
                onClick={(e) => handleNavClick(e, item.id)}
                aria-current={isActive ? "page" : undefined}
                className="tab whitespace-nowrap"
              >
                {item.badge && <span className="tab-ordinal">{item.badge}</span>}
                <span>{item.shortLabel}</span>
              </a>
            );
          })}
        </div>
      </div>
    </nav>
  );
}
