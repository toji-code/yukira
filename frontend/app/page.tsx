import Link from "next/link";
import { PageContainer } from "@/components/layout/PageContainer";

export default function HomePage() {
  return (
    <PageContainer
      title="Institutional Quantitative Engine"
      subtitle="Bitemporal point-in-time mutual fund analytics architecture with formal mathematical provenance."
    >
      {/* Epistemic Status Banner */}
      <div className="mb-8 rounded-lg border border-amber-500/30 bg-amber-500/10 p-4">
        <div className="flex items-start gap-3">
          <div className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-amber-500/20 text-xs font-bold text-amber-400">
            !
          </div>
          <div>
            <h3 className="text-sm font-semibold uppercase tracking-wider text-amber-400">
              Epistemic Baseline Notice
            </h3>
            <p className="mt-1 text-xs leading-relaxed text-zinc-300">
              Empirical Findings: <span className="font-semibold text-zinc-100">EXACTLY ZERO</span>. Approved Production Investment Methodology:{" "}
              <span className="font-semibold text-zinc-100">STRICTLY EMPTY</span>. All analytical indicators rendered in this system are unapproved candidate models subject to empirical verification. No metric constitutes an investment recommendation, rating, or commercial advice.
            </p>
          </div>
        </div>
      </div>

      {/* Grid of Core Architectural Pillars */}
      <div className="grid grid-cols-1 gap-6 md:grid-cols-3">
        <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-6 backdrop-blur-sm">
          <div className="mb-4 inline-flex h-10 w-10 items-center justify-center rounded-lg bg-cyan-500/10 text-cyan-400">
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </div>
          <h3 className="text-base font-semibold text-zinc-100">Bitemporal PIT Semantics</h3>
          <p className="mt-2 text-xs leading-relaxed text-zinc-400">
            Rigorous separation of effective financial observation date (<span className="font-mono text-cyan-300">effective_date</span>) and knowledge acquisition cutoff (<span className="font-mono text-cyan-300">availability_time</span>). Historical revisions are permanently preserved without destructive overwrites.
          </p>
        </div>

        <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-6 backdrop-blur-sm">
          <div className="mb-4 inline-flex h-10 w-10 items-center justify-center rounded-lg bg-indigo-500/10 text-indigo-400">
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
          </div>
          <h3 className="text-base font-semibold text-zinc-100">Deterministic Provenance</h3>
          <p className="mt-2 text-xs leading-relaxed text-zinc-400">
            Every calculation run records the input observation dataset SHA-256 hash, execution timestamp, Python Quant Engine git commit hash, and candidate parameter configurations. Zero unverifiable figures.
          </p>
        </div>

        <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-6 backdrop-blur-sm">
          <div className="mb-4 inline-flex h-10 w-10 items-center justify-center rounded-lg bg-emerald-500/10 text-emerald-400">
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
            </svg>
          </div>
          <h3 className="text-base font-semibold text-zinc-100">Three-Level UX Disclosure</h3>
          <p className="mt-2 text-xs leading-relaxed text-zinc-400">
            Hierarchical transparency from Level 1 summary context down to Level 2 evidence/reasoning and Level 3 institutional provenance, complete observation audit trails, and raw Quant Engine diagnostics.
          </p>
        </div>
      </div>

      {/* Navigation Actions */}
      <div className="mt-10 flex flex-wrap items-center gap-4">
        <Link
          href="/funds"
          className="inline-flex items-center gap-2 rounded-lg bg-cyan-600 px-5 py-2.5 text-sm font-semibold text-white shadow-lg shadow-cyan-600/20 transition hover:bg-cyan-500 focus:outline-none focus:ring-2 focus:ring-cyan-500 focus:ring-offset-2 focus:ring-offset-zinc-950"
        >
          Explore Fund Catalog
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
          </svg>
        </Link>
      </div>

      {/* Architecture Topology Specifications */}
      <div className="mt-12 rounded-xl border border-zinc-800 bg-zinc-900/40 p-6">
        <h3 className="text-sm font-semibold uppercase tracking-wider text-zinc-300">
          Integrated System Architecture
        </h3>
        <div className="mt-4 grid grid-cols-1 gap-4 text-xs font-mono text-zinc-400 sm:grid-cols-3">
          <div className="rounded-lg border border-zinc-800/80 bg-zinc-950/60 p-4">
            <div className="text-zinc-500">{"// REST API Service"}</div>
            <div className="mt-1 font-semibold text-zinc-200">Spring Boot 4.0.3</div>
            <div className="mt-1 text-zinc-400">Java 21 • Hibernate 7 • Flyway 10</div>
            <div className="mt-2 text-[11px] text-cyan-400">Bitemporal Observation Repositories</div>
          </div>
          <div className="rounded-lg border border-zinc-800/80 bg-zinc-950/60 p-4">
            <div className="text-zinc-500">{"// Quantitative Engine"}</div>
            <div className="mt-1 font-semibold text-zinc-200">Python 3.12+</div>
            <div className="mt-1 text-zinc-400">NumPy 2.x • Polars • SciPy</div>
            <div className="mt-2 text-[11px] text-indigo-400">Vectorized Financial Kernel</div>
          </div>
          <div className="rounded-lg border border-zinc-800/80 bg-zinc-950/60 p-4">
            <div className="text-zinc-500">{"// Analytical Presentation"}</div>
            <div className="mt-1 font-semibold text-zinc-200">Next.js 16 (App Router)</div>
            <div className="mt-1 text-zinc-400">React 19 • Tailwind CSS 4 • TypeScript 5</div>
            <div className="mt-2 text-[11px] text-emerald-400">Zero-Calculation Presentation Tier</div>
          </div>
        </div>
      </div>
    </PageContainer>
  );
}
