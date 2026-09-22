import Link from "next/link";
import { PageContainer } from "@/components/layout/PageContainer";

export default function HomePage() {
  return (
    <PageContainer>
      {/* Hero Section */}
      <section className="relative overflow-hidden pt-4 pb-12">
        <div className="inline-flex items-center gap-2 rounded-full border border-cyan-500/30 bg-cyan-500/10 px-3 py-1 font-mono text-[11px] font-medium text-cyan-400">
          <span className="h-1.5 w-1.5 rounded-full bg-cyan-400 animate-pulse" />
          INVESTMENT INTELLIGENCE PLATFORM
        </div>

        <h1 className="mt-5 text-4xl font-extrabold tracking-tight text-zinc-100 sm:text-6xl">
          YUKIRA
        </h1>

        <p className="mt-3 text-xl font-semibold tracking-tight text-cyan-400 sm:text-2xl">
          Investment Intelligence. Before you commit capital, ask one more question.
        </p>

        <p className="mt-4 max-w-3xl text-sm leading-relaxed text-zinc-300 sm:text-base">
          YUKIRA combines deterministic quantitative analysis, risk analysis, portfolio intelligence,
          bitemporal data validation, and explainable AI to help investors thoroughly investigate and verify
          investment decisions before committing capital.
        </p>

        {/* Action Navigation */}
        <div className="mt-8 flex flex-wrap items-center gap-3 font-mono text-xs">
          <Link
            href="/funds"
            className="inline-flex items-center gap-2 rounded-lg bg-cyan-600 px-5 py-2.5 font-semibold text-white shadow-lg shadow-cyan-600/20 transition hover:bg-cyan-500 focus:outline-none focus:ring-2 focus:ring-cyan-500 focus:ring-offset-2 focus:ring-offset-zinc-950"
          >
            Explore Funds
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
            </svg>
          </Link>

          <a
            href="#how-it-works"
            className="inline-flex items-center gap-1.5 rounded-lg border border-zinc-800 bg-zinc-900/80 px-4 py-2.5 font-medium text-zinc-300 transition hover:bg-zinc-800 hover:text-white"
          >
            How YUKIRA Works
          </a>

          <a
            href="#methodology"
            className="inline-flex items-center gap-1.5 rounded-lg border border-zinc-800 bg-zinc-900/80 px-4 py-2.5 font-medium text-zinc-300 transition hover:bg-zinc-800 hover:text-white"
          >
            Methodology
          </a>

          <a
            href="#project-status"
            className="inline-flex items-center gap-1.5 rounded-lg border border-zinc-800 bg-zinc-900/80 px-4 py-2.5 font-medium text-zinc-300 transition hover:bg-zinc-800 hover:text-white"
          >
            Project Status
          </a>
        </div>
      </section>

      {/* Epistemic Baseline Notice */}
      <section className="mb-12 rounded-xl border border-amber-500/30 bg-amber-500/10 p-5">
        <div className="flex items-start gap-3.5">
          <div className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-amber-500/20 text-xs font-bold text-amber-400">
            !
          </div>
          <div className="space-y-1.5">
            <div className="flex flex-wrap items-center gap-2">
              <h3 className="text-xs font-bold uppercase tracking-wider text-amber-400 font-mono">
                Epistemic Baseline Notice
              </h3>
              <span className="rounded bg-amber-500/20 px-1.5 py-0.5 font-mono text-[10px] font-semibold text-amber-300">
                GOVERNANCE MANDATE
              </span>
            </div>
            <p className="text-xs leading-relaxed text-zinc-200">
              Empirical Findings: <span className="font-bold text-white">EXACTLY ZERO</span>. Approved Production Investment Methodology:{" "}
              <span className="font-bold text-white">STRICTLY EMPTY</span>. Implemented Candidate Methodology:{" "}
              <span className="font-bold text-cyan-300">RET-02 Simple Period Return</span>.
            </p>
            <p className="text-[11px] leading-relaxed text-zinc-300">
              YUKIRA enforces an absolute separation between mathematical computation and artificial intelligence. AI must never invent, alter, or override authoritative financial calculations. All indicators rendered across this platform are unvalidated candidate models. Zero investment recommendations, star ratings, or commercial advice.
            </p>
          </div>
        </div>
      </section>

      {/* Section: How YUKIRA Works */}
      <section id="how-it-works" className="mb-14 scroll-mt-20">
        <div className="mb-6">
          <div className="font-mono text-[11px] uppercase tracking-wider text-cyan-400">Institutional Workflow</div>
          <h2 className="mt-1 text-2xl font-bold tracking-tight text-zinc-100">
            How YUKIRA Works
          </h2>
          <p className="mt-1 text-xs text-zinc-400 max-w-2xl">
            A deterministic analytical pipeline designed from first principles to eliminate look-ahead bias, data revisions, and synthetic outputs.
          </p>
        </div>

        <div className="grid grid-cols-1 gap-6 md:grid-cols-4">
          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-5 backdrop-blur-sm">
            <div className="font-mono text-xs font-bold text-cyan-400">01</div>
            <h3 className="mt-2 text-sm font-semibold text-zinc-100">Raw Source Ingestion</h3>
            <p className="mt-2 text-xs leading-relaxed text-zinc-400">
              Direct ingestion of official AMFI NAV feeds and disclosures. Every raw file is cryptographically hashed with SHA-256, timestamped, and stored immutably.
            </p>
            <div className="mt-3 font-mono text-[10px] text-zinc-500">
              Input Provenance: Verifiable
            </div>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-5 backdrop-blur-sm">
            <div className="font-mono text-xs font-bold text-indigo-400">02</div>
            <h3 className="mt-2 text-sm font-semibold text-zinc-100">Point-in-Time Resolution</h3>
            <p className="mt-2 text-xs leading-relaxed text-zinc-400">
              Bitemporal ledger separates effective date from availability time. Calculations strictly evaluate observations known as-of the knowledge cutoff timestamp.
            </p>
            <div className="mt-3 font-mono text-[10px] text-zinc-500">
              Look-Ahead Bias: Prevented
            </div>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-5 backdrop-blur-sm">
            <div className="font-mono text-xs font-bold text-emerald-400">03</div>
            <h3 className="mt-2 text-sm font-semibold text-zinc-100">Deterministic Quant Engine</h3>
            <p className="mt-2 text-xs leading-relaxed text-zinc-400">
              Isolated Python kernel executes vectorized financial formulas using NumPy 2, Polars, and SciPy. Zero synthetic numbers and zero LLM hallucination in calculations.
            </p>
            <div className="mt-3 font-mono text-[10px] text-zinc-500">
              Reproducibility: Exact
            </div>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-5 backdrop-blur-sm">
            <div className="font-mono text-xs font-bold text-amber-400">04</div>
            <h3 className="mt-2 text-sm font-semibold text-zinc-100">Progressive Disclosure</h3>
            <p className="mt-2 text-xs leading-relaxed text-zinc-400">
              Hierarchical UI provides Level 1 executive summary, Level 2 lookback evidence, and Level 3 raw observation lineage with artifact hashes.
            </p>
            <div className="mt-3 font-mono text-[10px] text-zinc-500">
              Auditability: Complete
            </div>
          </div>
        </div>
      </section>

      {/* Section: Methodology Transparency */}
      <section id="methodology" className="mb-14 scroll-mt-20">
        <div className="mb-6">
          <div className="font-mono text-[11px] uppercase tracking-wider text-cyan-400">Methodology Governance</div>
          <h2 className="mt-1 text-2xl font-bold tracking-tight text-zinc-100">
            Methodology Transparency & Candidate Lifecycle
          </h2>
          <p className="mt-1 text-xs text-zinc-400 max-w-3xl">
            YUKIRA establishes a strict epistemic boundary between software implementation and empirical validation. Defining an algorithm or writing code never implies that a methodology is validated.
          </p>
        </div>

        {/* 3 Lifecycle States Banner */}
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3 font-mono text-xs mb-8">
          <div className="rounded-xl border border-blue-500/30 bg-blue-950/20 p-4">
            <div className="flex items-center gap-2 font-bold text-blue-400 uppercase">
              <span className="h-2 w-2 rounded-full bg-blue-400" />
              1. Implemented
            </div>
            <p className="mt-2 text-[11px] leading-relaxed text-zinc-300 font-sans">
              Code exists and executes deterministically. Produces verifiable numerical outputs from verified input data.
            </p>
            <div className="mt-3 text-[10px] text-blue-300 font-mono">
              Status: RET-02 Implemented
            </div>
          </div>

          <div className="rounded-xl border border-amber-500/30 bg-amber-950/20 p-4">
            <div className="flex items-center gap-2 font-bold text-amber-400 uppercase">
              <span className="h-2 w-2 rounded-full bg-amber-400" />
              2. Validated
            </div>
            <p className="mt-2 text-[11px] leading-relaxed text-zinc-300 font-sans">
              Methodology has undergone empirical testing across multi-cycle regimes, vendor cross-validation, and statistical stress testing.
            </p>
            <div className="mt-3 text-[10px] text-amber-300 font-mono">
              Status: STRICTLY NONE
            </div>
          </div>

          <div className="rounded-xl border border-emerald-500/30 bg-emerald-950/20 p-4">
            <div className="flex items-center gap-2 font-bold text-emerald-400 uppercase">
              <span className="h-2 w-2 rounded-full bg-emerald-400" />
              3. Approved
            </div>
            <p className="mt-2 text-[11px] leading-relaxed text-zinc-300 font-sans">
              Formally authorized by governance review for live investor decision support and production benchmarking.
            </p>
            <div className="mt-3 text-[10px] text-emerald-300 font-mono">
              Status: STRICTLY NONE
            </div>
          </div>
        </div>

        {/* 7 Analytical Dimensions Inventory Summary */}
        <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-6">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-zinc-800 pb-4 mb-4">
            <div>
              <h3 className="text-sm font-semibold text-zinc-100">
                Phase 2H Candidate Methodology Inventory (30 Metrics across 7 Dimensions)
              </h3>
              <p className="text-xs text-zinc-400">
                Frozen in Phase 2B and mathematically specified in Phase 2H. All 30 metrics are candidate specifications.
              </p>
            </div>
            <span className="font-mono text-xs text-amber-400 bg-amber-500/10 border border-amber-500/30 px-2.5 py-1 rounded">
              Zero Production Approvals
            </span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 font-mono text-xs">
            <div className="rounded-lg border border-zinc-800/80 bg-zinc-950/60 p-3.5">
              <div className="font-semibold text-zinc-200">1. Return Quality (6)</div>
              <div className="mt-1 text-[11px] text-zinc-400 font-sans">
                RET-01 (1Y CAGR), RET-03 (3Y CAGR), RET-04 (5Y CAGR), RET-05 (3Y Rolling Return Mean), RET-06 (Rolling Outperformance %), RET-07 (3Y Active Return).
              </div>
              <div className="mt-2 text-[10px] text-cyan-400">Supporting Primitive: RET-02 (Simple Return) Implemented</div>
            </div>

            <div className="rounded-lg border border-zinc-800/80 bg-zinc-950/60 p-3.5">
              <div className="font-semibold text-zinc-200">2. Risk & Tail (7)</div>
              <div className="mt-1 text-[11px] text-zinc-400 font-sans">
                RSK-01 (Volatility), RSK-02 (Downside Semideviation), RSK-03 (Max Drawdown 3Y), RSK-04 (Max Drawdown Duration), RSK-05 (Ulcer Index), RSK-06 (Historical VaR 95%), RSK-07 (Expected Shortfall 95%).
              </div>
              <div className="mt-2 text-[10px] text-zinc-500">Candidate Specifications</div>
            </div>

            <div className="rounded-lg border border-zinc-800/80 bg-zinc-950/60 p-3.5">
              <div className="font-semibold text-zinc-200">3. Risk-Adjusted (4)</div>
              <div className="mt-1 text-[11px] text-zinc-400 font-sans">
                RAT-01 (Sharpe 3Y), RAT-02 (Sortino 3Y), RAT-03 (Treynor 3Y), RAT-04 (Information Ratio 3Y).
              </div>
              <div className="mt-2 text-[10px] text-zinc-500">Requires Risk-Free & Benchmarks</div>
            </div>

            <div className="rounded-lg border border-zinc-800/80 bg-zinc-950/60 p-3.5">
              <div className="font-semibold text-zinc-200">4. Market Sensitivity (5)</div>
              <div className="mt-1 text-[11px] text-zinc-400 font-sans">
                MKT-01 (Beta 3Y), MKT-02 (Downside Beta), MKT-03 (Upside Capture), MKT-04 (Downside Capture), MKT-05 (Capture Spread).
              </div>
              <div className="mt-2 text-[10px] text-zinc-500">Candidate Specifications</div>
            </div>

            <div className="rounded-lg border border-zinc-800/80 bg-zinc-950/60 p-3.5">
              <div className="font-semibold text-zinc-200">5. Benchmark / Alpha (2)</div>
              <div className="mt-1 text-[11px] text-zinc-400 font-sans">
                REL-01 (Tracking Error 3Y), REL-02 (Jensen&apos;s Alpha 3Y).
              </div>
              <div className="mt-2 text-[10px] text-zinc-500">Candidate Specifications</div>
            </div>

            <div className="rounded-lg border border-zinc-800/80 bg-zinc-950/60 p-3.5">
              <div className="font-semibold text-zinc-200">6. Portfolio Structure (5)</div>
              <div className="mt-1 text-[11px] text-zinc-400 font-sans">
                PRT-01 (Top-10 Concentration), PRT-02 (Effective Holdings), PRT-03 (Active Share), PRT-04 (Turnover), PRT-05 (Cash & Equivalent %).
              </div>
              <div className="mt-2 text-[10px] text-zinc-500">Awaiting Portfolio Holdings Feed</div>
            </div>

            <div className="rounded-lg border border-zinc-800/80 bg-zinc-950/60 p-3.5">
              <div className="font-semibold text-zinc-200">7. Governance / Expense (1)</div>
              <div className="mt-1 text-[11px] text-zinc-400 font-sans">
                GOV-01 (Direct Plan TER).
              </div>
              <div className="mt-2 text-[10px] text-zinc-500">Candidate Specification</div>
            </div>
          </div>
        </div>
      </section>

      {/* Section: Six-Dimensional Data Quality Taxonomy */}
      <section className="mb-14">
        <div className="mb-6">
          <div className="font-mono text-[11px] uppercase tracking-wider text-cyan-400">Data Integrity Framework</div>
          <h2 className="mt-1 text-2xl font-bold tracking-tight text-zinc-100">
            Approved Six-Dimensional Data Quality Taxonomy
          </h2>
          <p className="mt-1 text-xs text-zinc-400 max-w-2xl">
            Every input observation and metric calculation is tagged across six orthogonal dimensions so investors always know the veracity of the underlying evidence.
          </p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 font-mono text-xs">
          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-4">
            <div className="flex items-center justify-between pb-2 border-b border-zinc-800">
              <span className="font-semibold text-zinc-200">DIMENSION 1: QUALITY</span>
              <span className="text-[10px] text-emerald-400">VALID / SUSPICIOUS / INVALID</span>
            </div>
            <p className="mt-2 text-xs text-zinc-400 font-sans leading-relaxed">
              Verifies mathematical and domain sanity (e.g., non-negative NAV values, reasonable day-over-day movement thresholds). Invalid data is excluded.
            </p>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-4">
            <div className="flex items-center justify-between pb-2 border-b border-zinc-800">
              <span className="font-semibold text-zinc-200">DIMENSION 2: VERIFICATION</span>
              <span className="text-[10px] text-cyan-400">VERIFIED / UNVERIFIED</span>
            </div>
            <p className="mt-2 text-xs text-zinc-400 font-sans leading-relaxed">
              Tracks whether the data point has been cross-checked and verified against official external sources (AMFI, RTA, custodian disclosures).
            </p>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-4">
            <div className="flex items-center justify-between pb-2 border-b border-zinc-800">
              <span className="font-semibold text-zinc-200">DIMENSION 3: REVISION</span>
              <span className="text-[10px] text-sky-400">ORIGINAL / REVISED / SUPERSEDED</span>
            </div>
            <p className="mt-2 text-xs text-zinc-400 font-sans leading-relaxed">
              Records whether the observation reflects the initial report or a restated/revised figure. Prior versions are permanently preserved in the bitemporal ledger.
            </p>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-4">
            <div className="flex items-center justify-between pb-2 border-b border-zinc-800">
              <span className="font-semibold text-zinc-200">DIMENSION 4: FRESHNESS</span>
              <span className="text-[10px] text-indigo-400">CURRENT / STALE / BACKFILL</span>
            </div>
            <p className="mt-2 text-xs text-zinc-400 font-sans leading-relaxed">
              Reflects latency against the expected market release schedule. Explicitly tags historical batch backfills that use analytical end-of-day conventions.
            </p>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-4">
            <div className="flex items-center justify-between pb-2 border-b border-zinc-800">
              <span className="font-semibold text-zinc-200">DIMENSION 5: PRESENCE</span>
              <span className="text-[10px] text-amber-400">PRESENT / MISSING / INTERPOLATED</span>
            </div>
            <p className="mt-2 text-xs text-zinc-400 font-sans leading-relaxed">
              Discloses whether an exact observation exists or whether a deterministic lookback rule was applied (up to 4 calendar days on non-trading days).
            </p>
          </div>

          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-4">
            <div className="flex items-center justify-between pb-2 border-b border-zinc-800">
              <span className="font-semibold text-zinc-200">DIMENSION 6: INTEGRITY</span>
              <span className="text-[10px] text-emerald-400">NORMAL / DUPLICATE / CONFLICTING</span>
            </div>
            <p className="mt-2 text-xs text-zinc-400 font-sans leading-relaxed">
              Monitors multi-channel ingestion consistency. Identifies deduplication events or conflicting reports from divergent upstream feeds.
            </p>
          </div>
        </div>
      </section>

      {/* Section: Project Status & Delivery Estimates */}
      <section id="project-status" className="mb-12 scroll-mt-20">
        <div className="mb-6">
          <div className="font-mono text-[11px] uppercase tracking-wider text-cyan-400">Engineering Transparency</div>
          <h2 className="mt-1 text-2xl font-bold tracking-tight text-zinc-100">
            Current Project Status & Delivery Estimates
          </h2>
          <p className="mt-1 text-xs text-zinc-400 max-w-2xl">
            YUKIRA has progressed beyond the concept stage into an operational architecture, but is not yet an investor-ready production platform.
          </p>
        </div>

        {/* Delivery Progress Table */}
        <div className="overflow-hidden rounded-xl border border-zinc-800 bg-zinc-900/60 backdrop-blur-sm mb-8">
          <div className="bg-zinc-950/80 px-4 py-3 border-b border-zinc-800 flex items-center justify-between">
            <span className="text-xs font-mono font-semibold uppercase tracking-wider text-zinc-300">
              Project Delivery Estimates (Approximate Progress Indicators)
            </span>
            <span className="text-[10px] font-mono text-zinc-500">
              Updated: Phase 2I
            </span>
          </div>

          <div className="overflow-x-auto font-mono text-xs">
            <table className="w-full text-left">
              <thead className="border-b border-zinc-800 bg-zinc-950/40 text-zinc-400 uppercase text-[10px] tracking-wider">
                <tr>
                  <th className="px-4 py-2.5">Workstream / Area</th>
                  <th className="px-4 py-2.5">Estimated Status</th>
                  <th className="px-4 py-2.5">Current Deliverable Scope</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-800/60">
                <tr>
                  <td className="px-4 py-2.5 font-medium text-zinc-200">Product Concept & Scope</td>
                  <td className="px-4 py-2.5 text-cyan-400 font-bold">~90%</td>
                  <td className="px-4 py-2.5 text-zinc-400 font-sans">Institutional vision, epistemic principles, 30-metric candidate inventory frozen.</td>
                </tr>
                <tr>
                  <td className="px-4 py-2.5 font-medium text-zinc-200">System Architecture</td>
                  <td className="px-4 py-2.5 text-cyan-400 font-bold">~75%</td>
                  <td className="px-4 py-2.5 text-zinc-400 font-sans">Decoupled multi-service boundaries, PIT data flow, Docker orchestration.</td>
                </tr>
                <tr>
                  <td className="px-4 py-2.5 font-medium text-zinc-200">Database Foundation</td>
                  <td className="px-4 py-2.5 text-cyan-400 font-bold">~55%</td>
                  <td className="px-4 py-2.5 text-zinc-400 font-sans">PostgreSQL 17 bitemporal schema, Flyway migrations V1–V7, immutable audit trails.</td>
                </tr>
                <tr>
                  <td className="px-4 py-2.5 font-medium text-zinc-200">Backend Service</td>
                  <td className="px-4 py-2.5 text-cyan-400 font-bold">~35%</td>
                  <td className="px-4 py-2.5 text-zinc-400 font-sans">Spring Boot 4, JPA repositories, calculation orchestrator, RET-02 analysis service.</td>
                </tr>
                <tr>
                  <td className="px-4 py-2.5 font-medium text-zinc-200">Quantitative Engine</td>
                  <td className="px-4 py-2.5 text-cyan-400 font-bold">~25%</td>
                  <td className="px-4 py-2.5 text-zinc-400 font-sans">Python numerical kernel, 301 unit tests, mathematical specifications for 30 metrics.</td>
                </tr>
                <tr>
                  <td className="px-4 py-2.5 font-medium text-zinc-200">Data Ingestion</td>
                  <td className="px-4 py-2.5 text-cyan-400 font-bold">~30%</td>
                  <td className="px-4 py-2.5 text-zinc-400 font-sans">Real AMFI NAV parser, SHA-256 raw artifact archiving, daily & historical flows.</td>
                </tr>
                <tr>
                  <td className="px-4 py-2.5 font-medium text-zinc-200">Investment Methodology</td>
                  <td className="px-4 py-2.5 text-cyan-400 font-bold">~25%</td>
                  <td className="px-4 py-2.5 text-zinc-400 font-sans">Phase 2H specification frozen; RET-02 implemented; formal validation pending.</td>
                </tr>
                <tr>
                  <td className="px-4 py-2.5 font-medium text-zinc-200">Frontend / Website</td>
                  <td className="px-4 py-2.5 text-cyan-400 font-bold">~30%</td>
                  <td className="px-4 py-2.5 text-zinc-400 font-sans">Next.js 16 app, zero-calc presentation tier, fund discovery, analysis audit view.</td>
                </tr>
                <tr>
                  <td className="px-4 py-2.5 font-medium text-zinc-200">AI Interpretation Layer</td>
                  <td className="px-4 py-2.5 text-zinc-500 font-bold">~10%</td>
                  <td className="px-4 py-2.5 text-zinc-400 font-sans">Strict governance boundaries established; qualitative reasoning in development.</td>
                </tr>
                <tr>
                  <td className="px-4 py-2.5 font-medium text-zinc-200">Production Deployment</td>
                  <td className="px-4 py-2.5 text-rose-400 font-bold">0%</td>
                  <td className="px-4 py-2.5 text-zinc-400 font-sans">Operates in containerized local dev environment; cloud provisioning pending.</td>
                </tr>
                <tr className="bg-cyan-950/20 font-bold border-t-2 border-cyan-500/30">
                  <td className="px-4 py-3 text-cyan-300">INVESTOR-READY MVP</td>
                  <td className="px-4 py-3 text-cyan-300">~20–25%</td>
                  <td className="px-4 py-3 text-cyan-100 font-sans">Approximate delivery progress toward first fully validated investor release.</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div className="bg-zinc-950/40 px-4 py-2.5 text-[11px] text-zinc-500 font-sans border-t border-zinc-800">
            * Note: These percentages are approximate delivery indicators based on completed technical deliverables and should not be construed as objective financial measurements.
          </div>
        </div>

        {/* Current Limitations Disclosure */}
        <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-6">
          <h3 className="text-sm font-semibold uppercase tracking-wider text-zinc-300 font-mono mb-3">
            Explicit System Limitations (What YUKIRA Does Not Yet Do)
          </h3>
          <ul className="space-y-2 text-xs text-zinc-400 list-disc list-inside leading-relaxed font-sans">
            <li><strong className="text-zinc-200">Unimplemented Metrics:</strong> 29 of the 30 candidate metrics remain candidate specifications and are not yet wired into the execution pipeline.</li>
            <li><strong className="text-zinc-200">Zero Validated Methodologies:</strong> No quantitative algorithm has completed empirical validation across multiple historical market regimes.</li>
            <li><strong className="text-zinc-200">No Scoring or Recommendations:</strong> YUKIRA does not generate overall fund ratings, buy/sell recommendations, or investor suitability rankings.</li>
            <li><strong className="text-zinc-200">Pending External Feeds:</strong> Broad-market benchmark indices (NIFTY / S&P BSE TRI), risk-free rate series (FBIL T-Bills), and monthly portfolio disclosures are not yet integrated.</li>
            <li><strong className="text-zinc-200">Pre-Production Infrastructure:</strong> High availability cloud deployment, automated scaling, and continuous operational monitoring are scheduled for later phases.</li>
          </ul>
        </div>
      </section>

      {/* Explore Catalog CTA */}
      <section className="rounded-xl border border-cyan-500/20 bg-gradient-to-r from-cyan-950/30 to-indigo-950/30 p-8 text-center">
        <h3 className="text-xl font-bold text-zinc-100">
          Begin Fund Investigation
        </h3>
        <p className="mt-2 text-xs text-zinc-300 max-w-xl mx-auto">
          Explore real mutual fund schemes ingested from official AMFI data feeds, inspect share classes, and trigger auditable point-in-time quantitative calculations.
        </p>
        <div className="mt-6 flex justify-center">
          <Link
            href="/funds"
            className="inline-flex items-center gap-2 rounded-lg bg-cyan-600 px-6 py-2.5 font-mono text-xs font-semibold text-white shadow-lg shadow-cyan-600/20 transition hover:bg-cyan-500"
          >
            Open Fund Catalog
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
            </svg>
          </Link>
        </div>
      </section>
    </PageContainer>
  );
}
