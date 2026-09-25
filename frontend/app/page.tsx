import Link from "next/link";
import { PageContainer } from "@/components/layout/PageContainer";

export default function HomePage() {
  return (
    <PageContainer>
      {/* 1. HERO SECTION: Concise Institutional Value Proposition */}
      <section className="relative overflow-hidden pt-6 pb-12">
        <div className="inline-flex items-center gap-2 rounded-full border border-cyan-500/30 bg-cyan-500/10 px-3 py-1 font-mono text-[11px] font-medium text-cyan-400">
          <span className="h-1.5 w-1.5 rounded-full bg-cyan-400 animate-pulse" />
          QUANTITATIVE INVESTMENT INTELLIGENCE &bull; INSTITUTIONAL-ORIENTED CHECKPOINT
        </div>

        <h1 className="mt-5 text-4xl font-extrabold tracking-tight text-zinc-100 sm:text-5xl lg:text-6xl font-sans">
          Before you commit capital, <br className="hidden sm:inline" />
          <span className="text-transparent bg-clip-text bg-gradient-to-r from-cyan-400 via-teal-300 to-emerald-400">
            ask one more question.
          </span>
        </h1>

        <p className="mt-5 max-w-3xl text-sm leading-relaxed text-zinc-300 sm:text-base">
          YUKIRA is an evidence-based quantitative decision-support platform initially focused on Indian mutual funds. We reject simplistic trailing returns, opaque star ratings, and marketing narratives. Instead, we provide deterministic mathematical calculation, cryptographic source provenance, and point-in-time empirical verification.
        </p>

        {/* Action Controls */}
        <div className="mt-8 flex flex-wrap items-center gap-3 font-mono text-xs">
          <Link
            href="/funds/1"
            className="inline-flex items-center gap-2 rounded-lg bg-cyan-600 px-5 py-2.5 font-semibold text-white shadow-md shadow-cyan-600/20 transition hover:bg-cyan-500 focus:outline-none focus-visible:ring-2 focus-visible:ring-cyan-500"
          >
            Explore Canonical Pilot (HDFC Flexi Cap)
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
            </svg>
          </Link>

          <Link
            href="/funds"
            className="inline-flex items-center gap-1.5 rounded-lg border border-zinc-800 bg-zinc-900/90 px-4 py-2.5 font-medium text-zinc-200 transition hover:bg-zinc-800 hover:text-white focus:outline-none focus-visible:ring-2 focus-visible:ring-cyan-500"
          >
            Fund Catalog & Discovery
          </Link>

          <Link
            href="/methodology"
            className="inline-flex items-center gap-1.5 rounded-lg border border-zinc-800 bg-zinc-900/90 px-4 py-2.5 font-medium text-zinc-200 transition hover:bg-zinc-800 hover:text-white focus:outline-none focus-visible:ring-2 focus-visible:ring-cyan-500"
          >
            Methodology & Governance
          </Link>
        </div>
      </section>

      {/* 2. EPISTEMIC GOVERNANCE MANDATE */}
      <section className="mb-12 rounded-xl border border-amber-500/30 bg-amber-500/10 p-5 backdrop-blur-sm" aria-label="Governance Mandate">
        <div className="flex items-start gap-3.5">
          <div className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-amber-500/20 text-xs font-bold text-amber-400 font-mono">
            !
          </div>
          <div className="space-y-1.5">
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="text-xs font-bold uppercase tracking-wider text-amber-300 font-mono">
                Epistemic Governance Mandate
              </h2>
              <span className="rounded bg-amber-500/20 px-2 py-0.5 font-mono text-[10px] font-semibold text-amber-200 border border-amber-500/30">
                IMPLEMENTED &ne; VALIDATED &ne; APPROVED
              </span>
            </div>
            <p className="text-xs leading-relaxed text-zinc-200">
              In YUKIRA, implementing an algorithm in code does not constitute approval for live investment advice. Phase 2N established 5 formally approved methodologies (<code className="font-mono text-cyan-300 text-[11px]">M2N-01, M2N-02, M2N-05, M2N-06, M2N-07</code>), while 13 analytical vertical slices are implemented across distinct approved, candidate, and deferred governance states.
            </p>
            <p className="text-[11px] leading-relaxed text-zinc-400 font-mono">
              Zero automated buy/sell tips &bull; Zero 5-star ratings &bull; Zero synthetic NAV imputation &bull; Zero LLM hallucinations in financial calculations.
            </p>
          </div>
        </div>
      </section>

      {/* 3. CANONICAL PILOT SHOWCASE: Evidence in Action */}
      <section className="mb-14 rounded-2xl border border-zinc-800 bg-zinc-900/60 p-6 md:p-8 backdrop-blur-sm">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-6 border-b border-zinc-800/80">
          <div>
            <div className="font-mono text-[11px] uppercase tracking-wider text-cyan-400">
              Canonical Pilot Instrument
            </div>
            <h2 className="text-xl sm:text-2xl font-bold text-zinc-100 mt-1">
              HDFC Flexi Cap Fund (Direct Plan &bull; Growth)
            </h2>
            <p className="text-xs text-zinc-400 mt-1">
              Authoritative pilot dataset used across all Phase 2 analytical verifications.
            </p>
          </div>

          <Link
            href="/funds/1"
            className="inline-flex items-center gap-2 rounded-lg bg-zinc-800 border border-zinc-700/80 px-4 py-2 font-mono text-xs font-medium text-zinc-100 hover:bg-zinc-700 transition"
          >
            Open Fund Workspace
            <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          </Link>
        </div>

        <div className="mt-6 grid grid-cols-2 sm:grid-cols-4 gap-4 font-mono text-xs">
          <div className="rounded-xl border border-zinc-800/80 bg-zinc-950/60 p-4">
            <span className="text-zinc-500 text-[10px] uppercase block">AMFI Scheme Code</span>
            <span className="text-cyan-400 font-bold text-sm mt-1 block">118955</span>
            <span className="text-zinc-500 text-[10px] mt-1 block">ISIN: INF179K01UT0</span>
          </div>

          <div className="rounded-xl border border-zinc-800/80 bg-zinc-950/60 p-4">
            <span className="text-zinc-500 text-[10px] uppercase block">Observation Horizon</span>
            <span className="text-zinc-100 font-bold text-sm mt-1 block">5 Full Years</span>
            <span className="text-zinc-500 text-[10px] mt-1 block">2019-01-01 &rarr; 2024-01-15</span>
          </div>

          <div className="rounded-xl border border-zinc-800/80 bg-zinc-950/60 p-4">
            <span className="text-zinc-500 text-[10px] uppercase block">Trading Ledger</span>
            <span className="text-emerald-400 font-bold text-sm mt-1 block">1,243 Market Dates</span>
            <span className="text-zinc-500 text-[10px] mt-1 block">Zero synthetic gaps</span>
          </div>

          <div className="rounded-xl border border-zinc-800/80 bg-zinc-950/60 p-4">
            <span className="text-zinc-500 text-[10px] uppercase block">Cryptographic Hash</span>
            <span className="text-zinc-300 font-mono text-[11px] mt-1 block truncate" title="SHA-256: 900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259">
              900508f8bf13...
            </span>
            <span className="text-zinc-500 text-[10px] mt-1 block">AMFI Raw Source #1</span>
          </div>
        </div>

        {/* Real Analytical Metric Highlights */}
        <div className="mt-6 rounded-xl border border-zinc-800/80 bg-zinc-950/40 p-4 font-mono text-xs">
          <div className="text-[11px] uppercase tracking-wider text-zinc-400 mb-3 font-semibold">
            Verified Canonical Analytical Slices (As of 2024-01-15, Cutoff 2024-01-31)
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
            <div>
              <span className="text-zinc-500 text-[10px] block">RET-02 (Period Return)</span>
              <span className="text-zinc-100 font-bold text-base mt-0.5 block">+2.45%</span>
              <span className="text-[10px] text-zinc-500">2024-01-01 to 2024-01-15</span>
            </div>
            <div>
              <span className="text-zinc-500 text-[10px] block">RET-03 (3Y CAGR)</span>
              <span className="text-zinc-100 font-bold text-base mt-0.5 block">24.58%</span>
              <span className="text-[10px] text-zinc-500">36M Compound Annualized</span>
            </div>
            <div>
              <span className="text-zinc-500 text-[10px] block">RSK-01 (3Y Volatility)</span>
              <span className="text-zinc-100 font-bold text-base mt-0.5 block">14.69%</span>
              <span className="text-[10px] text-zinc-500">&radic;252 Annualized Dispersion</span>
            </div>
            <div>
              <span className="text-zinc-500 text-[10px] block">RSK-03 (3Y Max Drawdown)</span>
              <span className="text-zinc-100 font-bold text-base mt-0.5 block">-12.45%</span>
              <span className="text-[10px] text-zinc-500">Peak-to-Trough Decline</span>
            </div>
          </div>
        </div>
      </section>

      {/* 4. THE 7-STEP PROGRESSIVE DISCLOSURE FRAMEWORK */}
      <section className="mb-14 scroll-mt-20" id="framework">
        <div className="mb-6">
          <div className="font-mono text-[11px] uppercase tracking-wider text-cyan-400">
            Progressive Disclosure Architecture
          </div>
          <h2 className="mt-1 text-2xl font-bold tracking-tight text-zinc-100">
            How YUKIRA Analyzes Investment Intelligence
          </h2>
          <p className="mt-1 text-xs text-zinc-400 max-w-2xl">
            Every analytical page adheres to a 7-step progressive disclosure framework, presenting immediate clarity at the surface and complete cryptographic provenance one level deeper.
          </p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 font-mono text-xs">
          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-4">
            <span className="text-cyan-400 font-bold text-xs">Step 01</span>
            <h3 className="mt-1 font-semibold text-zinc-100 font-sans text-sm">What is this?</h3>
            <p className="mt-1.5 text-xs text-zinc-400 font-sans leading-relaxed">
              Canonical scheme identity: AMC, Plan (Direct vs. Regular), Option (Growth), AMFI Code, and ISIN.
            </p>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-4">
            <span className="text-indigo-400 font-bold text-xs">Step 02</span>
            <h3 className="mt-1 font-semibold text-zinc-100 font-sans text-sm">What does YUKIRA know?</h3>
            <p className="mt-1.5 text-xs text-zinc-400 font-sans leading-relaxed">
              Point-in-time observation ledger: verified trading dates, knowledge cutoffs, and revision history.
            </p>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-4">
            <span className="text-emerald-400 font-bold text-xs">Step 03</span>
            <h3 className="mt-1 font-semibold text-zinc-100 font-sans text-sm">What does the data show?</h3>
            <p className="mt-1.5 text-xs text-zinc-400 font-sans leading-relaxed">
              Objective mathematical outputs: realized CAGR, volatility, drawdowns, ratios, and downside beta.
            </p>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-4">
            <span className="text-teal-400 font-bold text-xs">Step 04</span>
            <h3 className="mt-1 font-semibold text-zinc-100 font-sans text-sm">What does it mean?</h3>
            <p className="mt-1.5 text-xs text-zinc-400 font-sans leading-relaxed">
              Deterministic statistical interpretation of return dispersion and market sensitivity without speculation.
            </p>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-4">
            <span className="text-rose-400 font-bold text-xs">Step 05</span>
            <h3 className="mt-1 font-semibold text-zinc-100 font-sans text-sm">What are the risks?</h3>
            <p className="mt-1.5 text-xs text-zinc-400 font-sans leading-relaxed">
              Downside semideviation, Ulcer Index path stress, Value at Risk (95%), and maximum recovery duration.
            </p>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-4">
            <span className="text-amber-400 font-bold text-xs">Step 06</span>
            <h3 className="mt-1 font-semibold text-zinc-100 font-sans text-sm">What is missing?</h3>
            <p className="mt-1.5 text-xs text-zinc-400 font-sans leading-relaxed">
              Explicit disclosure of uningested benchmark series, missing exchange calendars, or unvalidated conventions.
            </p>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-4 sm:col-span-2">
            <span className="text-purple-400 font-bold text-xs">Step 07</span>
            <h3 className="mt-1 font-semibold text-zinc-100 font-sans text-sm">What should the investor investigate next?</h3>
            <p className="mt-1.5 text-xs text-zinc-400 font-sans leading-relaxed">
              Targeted verification checkpoints: portfolio concentration, manager tenure, market regime behavior, and expense drag before capital is allocated.
            </p>
          </div>
        </div>
      </section>

      {/* 5. CORE EPISTEMIC PRINCIPLES */}
      <section className="mb-14 scroll-mt-20" id="philosophy">
        <div className="mb-6">
          <div className="font-mono text-[11px] uppercase tracking-wider text-cyan-400">
            Epistemic Architecture
          </div>
          <h2 className="mt-1 text-2xl font-bold tracking-tight text-zinc-100">
            How YUKIRA Thinks
          </h2>
          <p className="mt-1 text-xs text-zinc-400 max-w-2xl">
            Mathematical discipline, deterministic execution, and complete lineage replace fintech marketing promises.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 font-mono text-xs">
          <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-5">
            <div className="text-cyan-400 font-bold">01 / DETERMINISTIC MATH</div>
            <h3 className="mt-2 text-sm font-semibold text-zinc-100 font-sans">Zero AI in Financial Math</h3>
            <p className="mt-2 text-xs leading-relaxed text-zinc-400 font-sans">
              Calculations originate exclusively from pure, vectorized Python algorithms (NumPy 2, Polars, SciPy). LLMs are strictly forbidden from computing, smoothing, or estimating financial observations.
            </p>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-5">
            <div className="text-indigo-400 font-bold">02 / BITEMPORAL LEDGER</div>
            <h3 className="mt-2 text-sm font-semibold text-zinc-100 font-sans">Zero Look-Ahead Bias</h3>
            <p className="mt-2 text-xs leading-relaxed text-zinc-400 font-sans">
              Every historical query specifies both an <code className="text-cyan-300">analysis_cutoff</code> and a <code className="text-cyan-300">knowledge_cutoff</code>. Observations published after the knowledge cutoff can never contaminate historical evaluations.
            </p>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-5">
            <div className="text-emerald-400 font-bold">03 / SOURCE PROVENANCE</div>
            <h3 className="mt-2 text-sm font-semibold text-zinc-100 font-sans">Cryptographic Lineage</h3>
            <p className="mt-2 text-xs leading-relaxed text-zinc-400 font-sans">
              Every input observation traces directly to an immutable <code className="text-cyan-300">source_artifact</code> record with SHA-256 digest, HTTP request URI, and retrieval timestamp.
            </p>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-5">
            <div className="text-amber-400 font-bold">04 / RISK BEFORE RETURN</div>
            <h3 className="mt-2 text-sm font-semibold text-zinc-100 font-sans">Asymmetric Downside Analysis</h3>
            <p className="mt-2 text-xs leading-relaxed text-zinc-400 font-sans">
              Downside semideviation, Ulcer Index, maximum drawdown duration, and downside beta are prioritized over trailing point-to-point gains that mask intermediate capital impairment.
            </p>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-5">
            <div className="text-purple-400 font-bold">05 / REPUTATION OVER HYPE</div>
            <h3 className="mt-2 text-sm font-semibold text-zinc-100 font-sans">Comfortable Saying &ldquo;Missing&rdquo;</h3>
            <p className="mt-2 text-xs leading-relaxed text-zinc-400 font-sans">
              If data is missing or an indicator is unvalidated, the platform explicitly reports &ldquo;Not available&rdquo; or &ldquo;Candidate methodology&rdquo;. We never fabricate default values.
            </p>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-5">
            <div className="text-rose-400 font-bold">06 / ZERO STAR RATINGS</div>
            <h3 className="mt-2 text-sm font-semibold text-zinc-100 font-sans">Transparent Decision Support</h3>
            <p className="mt-2 text-xs leading-relaxed text-zinc-400 font-sans">
              YUKIRA produces no opaque composite scores or algorithmic star badges. We equip the allocator with auditable empirical evidence to evaluate their own hypothesis.
            </p>
          </div>
        </div>
      </section>

      {/* 6. CALL TO ACTION */}
      <section className="mb-12 rounded-2xl border border-cyan-500/20 bg-gradient-to-b from-cyan-950/20 to-zinc-900/60 p-8 text-center backdrop-blur-sm">
        <h2 className="text-xl sm:text-2xl font-bold text-zinc-100">
          Ready to verify before committing capital?
        </h2>
        <p className="mt-2 text-xs sm:text-sm text-zinc-400 max-w-xl mx-auto font-sans leading-relaxed">
          Inspect authentic mutual fund records, examine point-in-time calculation runs, and explore methodology specifications.
        </p>

        <div className="mt-6 flex flex-wrap items-center justify-center gap-3 font-mono text-xs">
          <Link
            href="/funds"
            className="rounded-lg bg-cyan-600 px-5 py-2.5 font-semibold text-white shadow-md shadow-cyan-600/20 hover:bg-cyan-500 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-cyan-500"
          >
            Open Fund Discovery Catalog
          </Link>
          <Link
            href="/methodology"
            className="rounded-lg border border-zinc-700 bg-zinc-800 px-4 py-2.5 font-medium text-zinc-200 hover:bg-zinc-700 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-cyan-500"
          >
            Review Governance Specifications
          </Link>
        </div>
      </section>
    </PageContainer>
  );
}
