import React from 'react';
import Link from 'next/link';

export function AppFooter() {
  return (
    <footer className="border-t border-border bg-surface/80 mt-auto py-10 font-sans text-xs text-text-secondary transition-colors">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-8 pb-8 border-b border-border">
          <div className="md:col-span-1 space-y-3">
            <div className="flex items-center gap-2">
              <div className="flex h-7 w-7 items-center justify-center rounded bg-surface-elevated border border-border text-accent font-mono font-bold text-xs">
                Y
              </div>
              <span className="font-bold tracking-tight text-text-primary font-mono text-sm">
                PROJECT YUKIRA
              </span>
            </div>
            <p className="text-text-secondary text-xs leading-relaxed">
              Institutional-oriented Quantitative Investment Intelligence Platform. A deterministic verification checkpoint for investors and allocators before committing capital.
            </p>
            <div className="font-mono text-[10px] text-text-muted">
              Quantitative Intelligence &bull; Deterministic Financial Engineering
            </div>
          </div>

          <div className="space-y-2">
            <h4 className="font-semibold text-text-primary uppercase tracking-wider font-mono text-[11px]">
              Epistemic Core
            </h4>
            <ul className="space-y-1.5 text-text-secondary font-mono text-[11px]">
              <li>&bull; Evidence over opinion</li>
              <li>&bull; Risk before return</li>
              <li>&bull; Probability over prediction</li>
              <li>&bull; Explainability before complexity</li>
              <li>&bull; Zero synthetic NAV data</li>
            </ul>
          </div>

          <div className="space-y-2">
            <h4 className="font-semibold text-text-primary uppercase tracking-wider font-mono text-[11px]">
              Architecture Invariants
            </h4>
            <p className="text-text-secondary text-xs leading-relaxed">
              Deterministic Python 3.12 mathematical engine. Strict point-in-time queries with explicit knowledge cutoffs to prevent look-ahead bias. Spring Boot 4 orchestrator with PostgreSQL 17 bitemporal ledger.
            </p>
          </div>

          <div className="space-y-2">
            <h4 className="font-semibold text-text-primary uppercase tracking-wider font-mono text-[11px]">
              Platform Navigation
            </h4>
            <div className="flex flex-col space-y-1.5 font-mono text-xs">
              <Link href="/" className="hover:text-accent transition-colors">
                System Overview
              </Link>
              <Link href="/funds" className="hover:text-accent transition-colors">
                Fund Catalog & Discovery
              </Link>
              <Link href="/methodology" className="hover:text-accent transition-colors">
                Methodology Governance
              </Link>
              <a
                href="https://www.amfiindia.com"
                target="_blank"
                rel="noreferrer"
                className="hover:text-accent transition-colors inline-flex items-center gap-1"
              >
                AMFI Portal (NAV Source)
                <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
                </svg>
              </a>
            </div>
          </div>
        </div>

        <div className="pt-6 flex flex-col sm:flex-row items-center justify-between text-[11px] text-text-muted gap-3 font-mono">
          <div>
            &ldquo;Before you commit capital, ask one more question.&rdquo; &bull; Decision Support Only
          </div>
          <div className="text-right">
            Zero Commercial Advice &bull; Zero Star Ratings &bull; Investment-Risk & Methodology Disclosures
          </div>
        </div>
      </div>
    </footer>
  );
}
