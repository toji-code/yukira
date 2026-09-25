import React from 'react';
import Link from 'next/link';

export function AppFooter() {
  return (
    <footer className="border-t border-zinc-800 bg-zinc-950/80 mt-auto py-10 font-sans text-xs text-zinc-400">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-8 pb-8 border-b border-zinc-800">
          <div className="md:col-span-1 space-y-3">
            <div className="flex items-center gap-2">
              <div className="flex h-7 w-7 items-center justify-center rounded bg-zinc-900 border border-zinc-800 text-cyan-400 font-mono font-bold text-xs">
                Y
              </div>
              <span className="font-bold tracking-tight text-zinc-100 font-mono text-sm">
                PROJECT YUKIRA
              </span>
            </div>
            <p className="text-zinc-400 text-xs leading-relaxed">
              Institutional-grade Quantitative Investment Intelligence Platform. A deterministic verification checkpoint for investors and allocators before committing capital.
            </p>
            <div className="font-mono text-[10px] text-zinc-400">
              Phase 2R &bull; Investor Experience Tier
            </div>
          </div>

          <div className="space-y-2">
            <h4 className="font-semibold text-zinc-200 uppercase tracking-wider font-mono text-[11px]">
              Epistemic Core
            </h4>
            <ul className="space-y-1.5 text-zinc-400 font-mono text-[11px]">
              <li>&bull; Evidence over opinion</li>
              <li>&bull; Risk before return</li>
              <li>&bull; Probability over prediction</li>
              <li>&bull; Explainability before complexity</li>
              <li>&bull; Zero synthetic NAV data</li>
            </ul>
          </div>

          <div className="space-y-2">
            <h4 className="font-semibold text-zinc-200 uppercase tracking-wider font-mono text-[11px]">
              Architecture Invariants
            </h4>
            <p className="text-zinc-400 text-xs leading-relaxed">
              Deterministic Python 3.12 mathematical engine. Strict point-in-time queries with explicit knowledge cutoffs to prevent look-ahead bias. Spring Boot 4 orchestrator with PostgreSQL 17 bitemporal ledger.
            </p>
          </div>

          <div className="space-y-2">
            <h4 className="font-semibold text-zinc-200 uppercase tracking-wider font-mono text-[11px]">
              Platform Navigation
            </h4>
            <div className="flex flex-col space-y-1.5 font-mono text-xs">
              <Link href="/" className="hover:text-cyan-400 transition-colors">
                System Overview
              </Link>
              <Link href="/funds" className="hover:text-cyan-400 transition-colors">
                Fund Catalog & Discovery
              </Link>
              <Link href="/methodology" className="hover:text-cyan-400 transition-colors">
                Methodology Governance
              </Link>
              <a
                href="https://www.amfiindia.com"
                target="_blank"
                rel="noreferrer"
                className="hover:text-cyan-400 transition-colors inline-flex items-center gap-1"
              >
                Official AMFI Feeds
                <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
                </svg>
              </a>
            </div>
          </div>
        </div>

        <div className="pt-6 flex flex-col sm:flex-row items-center justify-between text-[11px] text-zinc-400 gap-3 font-mono">
          <div>
            &ldquo;Before you commit capital, ask one more question.&rdquo; &bull; Decision Support Only
          </div>
          <div className="text-zinc-400 text-right">
            Zero Commercial Advice &bull; Zero Star Ratings &bull; Cryptographic Provenance
          </div>
        </div>
      </div>
    </footer>
  );
}
