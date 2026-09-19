import React from 'react';

export function AppFooter() {
  return (
    <footer className="border-t border-zinc-200 bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-950/60 mt-auto py-8">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 pb-6 border-b border-zinc-200 dark:border-zinc-800 text-xs text-zinc-600 dark:text-zinc-400">
          <div>
            <h4 className="font-semibold text-zinc-800 dark:text-zinc-200 uppercase tracking-wider font-mono text-[11px] mb-2">
              Epistemic Baseline
            </h4>
            <p className="font-mono text-[11px] leading-relaxed">
              YUKIRA EMPIRICAL FINDINGS: <span className="font-bold text-zinc-900 dark:text-zinc-100">EXACTLY ZERO</span>
              <br />
              APPROVED PRODUCTION METHODOLOGY: <span className="font-bold text-zinc-900 dark:text-zinc-100">STRICTLY EMPTY</span>
            </p>
          </div>

          <div>
            <h4 className="font-semibold text-zinc-800 dark:text-zinc-200 uppercase tracking-wider font-mono text-[11px] mb-2">
              Computational Architecture
            </h4>
            <p className="leading-relaxed">
              100% deterministic Python Quantitative Engine. Zero AI / LLM involvement in numerical calculation path.
              Point-in-Time Bitemporal Ledger on PostgreSQL 17.
            </p>
          </div>

          <div>
            <h4 className="font-semibold text-zinc-800 dark:text-zinc-200 uppercase tracking-wider font-mono text-[11px] mb-2">
              Governance & Attribution
            </h4>
            <p className="leading-relaxed">
              Phase 2E Frontend Foundation. Consumer of verified API data contracts only. Does not provide investment advice or recommendations.
            </p>
          </div>
        </div>

        <div className="pt-4 flex flex-col sm:flex-row items-center justify-between text-[11px] text-zinc-600 dark:text-zinc-400 gap-2 font-mono">
          <span>PROJECT YUKIRA — FOUNDATIONAL SYSTEM ARCHITECTURE</span>
          <span>BITEMPORAL AUDITABILITY — AS-OF CUTOFF INTEGRITY</span>
        </div>
      </div>
    </footer>
  );
}
