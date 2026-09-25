import Link from "next/link";
import { PageContainer } from "@/components/layout/PageContainer";

export default function MethodologyPage() {
  return (
    <PageContainer
      title="Methodology Governance & Transparency"
      subtitle="Strict epistemic separation of software implementation, empirical validation, and production approval."
      breadcrumbs={[
        { label: "Home", href: "/" },
        { label: "Methodology", href: "/methodology" },
      ]}
    >
      {/* Epistemic Mandate Alert */}
      <div className="mb-8 rounded-xl border border-amber-500/30 bg-amber-500/10 p-5">
        <div className="flex items-start gap-3">
          <div className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-amber-500/20 text-xs font-bold text-amber-400">
            !
          </div>
          <div className="space-y-1">
            <h3 className="text-xs font-bold uppercase tracking-wider text-amber-400 font-mono">
              Core Epistemic Governance Mandate
            </h3>
            <p className="text-xs leading-relaxed text-zinc-200">
              <strong className="text-white">IMPLEMENTED ≠ VALIDATED ≠ APPROVED PRODUCTION METHODOLOGY</strong>
            </p>
            <p className="text-xs leading-relaxed text-zinc-300">
              In YUKIRA, defining a mathematical algorithm or implementing it in code does <strong className="text-zinc-100">not</strong> mean the methodology has been validated. No metric constitutes an investment recommendation, rating, or commercial advice.
            </p>
          </div>
        </div>
      </div>

      {/* Authoritative Epistemic State */}
      <div className="mb-10 rounded-xl border border-zinc-800 bg-zinc-900/60 p-6 font-mono text-xs">
        <h3 className="text-xs font-semibold uppercase tracking-wider text-zinc-400 mb-4">
          Current Authoritative Governance State
        </h3>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <div className="rounded-lg border border-cyan-500/30 bg-cyan-950/20 p-3.5">
            <span className="text-zinc-500 text-[10px] uppercase block">Implemented Candidates</span>
            <span className="text-cyan-300 font-bold text-sm mt-1 block">13 Analytical Slices</span>
            <span className="text-[10px] text-zinc-400 mt-1 block">RET-02/03, RSK-01..07, RAT-01/02, REL-01/04</span>
          </div>

          <div className="rounded-lg border border-blue-500/30 bg-blue-950/20 p-3.5">
            <span className="text-zinc-500 text-[10px] uppercase block">Phase 2N Approved</span>
            <span className="text-blue-300 font-bold text-sm mt-1 block">5 Methodologies</span>
            <span className="text-[10px] text-zinc-400 mt-1 block">M2N-01, M2N-02, M2N-05, M2N-06, M2N-07</span>
          </div>

          <div className="rounded-lg border border-amber-500/30 bg-amber-950/20 p-3.5">
            <span className="text-zinc-500 text-[10px] uppercase block">Phase 2N Deferred</span>
            <span className="text-amber-400 font-bold text-sm mt-1 block">4 Methodologies</span>
            <span className="text-[10px] text-zinc-400 mt-1 block">M2N-03, M2N-04, M2N-08, M2N-09</span>
          </div>

          <div className="rounded-lg border border-zinc-800 bg-zinc-950/60 p-3.5">
            <span className="text-zinc-500 text-[10px] uppercase block">Commercial Advice</span>
            <span className="text-zinc-400 font-bold text-sm mt-1 block">STRICTLY NONE</span>
            <span className="text-[10px] text-zinc-400 mt-1 block">Zero star ratings &bull; Zero tips</span>
          </div>
        </div>
      </div>

      {/* The 3 Lifecycle Tiers */}
      <section className="mb-12">
        <h2 className="text-xl font-bold text-zinc-100 mb-4">
          The Three Lifecycle Tiers
        </h2>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <div className="rounded-xl border border-blue-500/30 bg-blue-950/20 p-5">
            <div className="flex items-center gap-2 font-mono text-xs font-bold text-blue-400 uppercase">
              <span className="h-2.5 w-2.5 rounded-full bg-blue-400" />
              Tier 1: Implemented
            </div>
            <h3 className="mt-2 text-base font-semibold text-zinc-100">Executable Code</h3>
            <p className="mt-2 text-xs leading-relaxed text-zinc-300">
              The algorithm is implemented in the Python Quantitative Engine and verified against deterministic unit tests. Given valid point-in-time input observations, it produces verifiable numbers.
            </p>
            <div className="mt-4 border-t border-blue-500/20 pt-3 text-[11px] font-mono text-blue-300">
              Current: 13 Candidate Slices Executable
            </div>
          </div>

          <div className="rounded-xl border border-amber-500/30 bg-amber-950/20 p-5">
            <div className="flex items-center gap-2 font-mono text-xs font-bold text-amber-400 uppercase">
              <span className="h-2.5 w-2.5 rounded-full bg-amber-400" />
              Tier 2: Validated
            </div>
            <h3 className="mt-2 text-base font-semibold text-zinc-100">Empirical Verification</h3>
            <p className="mt-2 text-xs leading-relaxed text-zinc-300">
              The methodology has been tested against multi-year historical data across bull, bear, and sideways regimes; reconciled against independent institutional vendor datasets; and verified for statistical soundness.
            </p>
            <div className="mt-4 border-t border-amber-500/20 pt-3 text-[11px] font-mono text-amber-300">
              Current: Empirical Regime Stress Testing
            </div>
          </div>

          <div className="rounded-xl border border-emerald-500/30 bg-emerald-950/20 p-5">
            <div className="flex items-center gap-2 font-mono text-xs font-bold text-emerald-400 uppercase">
              <span className="h-2.5 w-2.5 rounded-full bg-emerald-400" />
              Tier 3: Approved
            </div>
            <h3 className="mt-2 text-base font-semibold text-zinc-100">Governance Authorization</h3>
            <p className="mt-2 text-xs leading-relaxed text-zinc-300">
              Formally approved by governance review for production decision support. Five foundational methodology standards approved in Phase 2N.
            </p>
            <div className="mt-4 border-t border-emerald-500/20 pt-3 text-[11px] font-mono text-emerald-300">
              Current: 5 Approved Methodologies (Phase 2N)
            </div>
          </div>
        </div>
      </section>

      {/* The 30 MVP Candidate Metrics Inventory */}
      <section className="mb-12">
        <h2 className="text-xl font-bold text-zinc-100 mb-2">
          Candidate Methodology Inventory (30 Metrics across 7 Dimensions)
        </h2>
        <p className="text-xs text-zinc-400 max-w-3xl mb-6">
          Formally specified in Phase 2H (`phase2h_quantitative_methodology.md`). All formulas, lookback windows, and calendar conventions are documented. Implementation and validation occur progressively.
        </p>

        <div className="space-y-4 font-mono text-xs">
          {/* Dimension 1 */}
          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-5">
            <div className="flex items-center justify-between pb-3 border-b border-zinc-800">
              <span className="font-bold text-cyan-400 uppercase">1. Return Quality (6 Candidate Metrics)</span>
              <span className="text-[10px] text-zinc-400 bg-zinc-950 px-2 py-0.5 rounded border border-zinc-800">
                6 Candidate Specifications
              </span>
            </div>
            <div className="mt-3 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
              <div className="bg-zinc-950 p-2.5 rounded border border-zinc-800">
                <span className="font-semibold text-zinc-200 block">RET-01</span>
                <span className="text-[11px] text-zinc-400 font-sans block">1Y CAGR</span>
                <span className="text-[10px] text-zinc-500 block mt-1">Candidate Spec</span>
              </div>
              <div className="bg-zinc-950 p-2.5 rounded border border-zinc-800">
                <span className="font-semibold text-zinc-200 block">RET-03</span>
                <span className="text-[11px] text-zinc-400 font-sans block">3Y CAGR</span>
                <span className="text-[10px] text-zinc-500 block mt-1">Candidate Spec</span>
              </div>
              <div className="bg-zinc-950 p-2.5 rounded border border-zinc-800">
                <span className="font-semibold text-zinc-200 block">RET-04</span>
                <span className="text-[11px] text-zinc-400 font-sans block">5Y CAGR</span>
                <span className="text-[10px] text-zinc-500 block mt-1">Candidate Spec</span>
              </div>
              <div className="bg-zinc-950 p-2.5 rounded border border-zinc-800">
                <span className="font-semibold text-zinc-200 block">RET-05</span>
                <span className="text-[11px] text-zinc-400 font-sans block">3Y Rolling Return Mean</span>
                <span className="text-[10px] text-zinc-500 block mt-1">Candidate Spec</span>
              </div>
              <div className="bg-zinc-950 p-2.5 rounded border border-zinc-800">
                <span className="font-semibold text-zinc-200 block">RET-06</span>
                <span className="text-[11px] text-zinc-400 font-sans block">Rolling Outperformance %</span>
                <span className="text-[10px] text-zinc-500 block mt-1">Candidate Spec</span>
              </div>
              <div className="bg-zinc-950 p-2.5 rounded border border-zinc-800">
                <span className="font-semibold text-zinc-200 block">RET-07</span>
                <span className="text-[11px] text-zinc-400 font-sans block">3Y Active Return</span>
                <span className="text-[10px] text-zinc-500 block mt-1">Candidate Spec</span>
              </div>
            </div>
            <div className="mt-3 p-2.5 rounded border border-cyan-500/30 bg-cyan-950/20 flex flex-col sm:flex-row sm:items-center justify-between gap-2 text-[11px]">
              <div>
                <span className="font-bold text-cyan-300">Supporting Implemented Primitive: RET-02 (Simple Period Return)</span>
                <span className="text-zinc-400 font-sans block">R = (Ending NAV / Starting NAV) - 1. Implemented candidate primitive inherited from Phase 2F; not counted among the 30 analytical metrics.</span>
              </div>
              <span className="text-[10px] text-cyan-300 font-mono bg-cyan-950 px-2 py-0.5 rounded border border-cyan-800 shrink-0">
                Implemented Candidate
              </span>
            </div>
          </div>

          {/* Dimension 2 */}
          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-5">
            <div className="flex items-center justify-between pb-3 border-b border-zinc-800">
              <span className="font-bold text-indigo-400 uppercase">2. Risk & Tail (7 Metrics)</span>
              <span className="text-[10px] text-zinc-500 bg-zinc-950 px-2 py-0.5 rounded border border-zinc-800">
                Candidate Specifications
              </span>
            </div>
            <div className="mt-3 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
              <div className="bg-zinc-950 p-2.5 rounded border border-zinc-800">
                <span className="font-semibold text-zinc-200 block">RSK-01</span>
                <span className="text-[11px] text-zinc-400 font-sans block">Annualized Volatility</span>
              </div>
              <div className="bg-zinc-950 p-2.5 rounded border border-zinc-800">
                <span className="font-semibold text-zinc-200 block">RSK-02</span>
                <span className="text-[11px] text-zinc-400 font-sans block">Downside Semideviation</span>
              </div>
              <div className="bg-zinc-950 p-2.5 rounded border border-zinc-800">
                <span className="font-semibold text-zinc-200 block">RSK-03</span>
                <span className="text-[11px] text-zinc-400 font-sans block">Maximum Drawdown 3Y</span>
              </div>
              <div className="bg-zinc-950 p-2.5 rounded border border-zinc-800">
                <span className="font-semibold text-zinc-200 block">RSK-04</span>
                <span className="text-[11px] text-zinc-400 font-sans block">Maximum Drawdown Duration</span>
              </div>
              <div className="bg-zinc-950 p-2.5 rounded border border-zinc-800">
                <span className="font-semibold text-zinc-200 block">RSK-05</span>
                <span className="text-[11px] text-zinc-400 font-sans block">Ulcer Index</span>
              </div>
              <div className="bg-zinc-950 p-2.5 rounded border border-zinc-800">
                <span className="font-semibold text-zinc-200 block">RSK-06</span>
                <span className="text-[11px] text-zinc-400 font-sans block">Historical VaR 95%</span>
              </div>
              <div className="bg-zinc-950 p-2.5 rounded border border-zinc-800">
                <span className="font-semibold text-zinc-200 block">RSK-07</span>
                <span className="text-[11px] text-zinc-400 font-sans block">Expected Shortfall 95%</span>
              </div>
            </div>
          </div>

          {/* Dimension 3 */}
          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-5">
            <div className="flex items-center justify-between pb-3 border-b border-zinc-800">
              <span className="font-bold text-emerald-400 uppercase">3. Risk-Adjusted (4 Metrics)</span>
              <span className="text-[10px] text-zinc-500 bg-zinc-950 px-2 py-0.5 rounded border border-zinc-800">
                Awaiting Risk-Free Rate Feed
              </span>
            </div>
            <div className="mt-3 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
              <div className="bg-zinc-950 p-2.5 rounded border border-zinc-800">
                <span className="font-semibold text-zinc-200 block">RAT-01</span>
                <span className="text-[11px] text-zinc-400 font-sans block">Sharpe Ratio 3Y</span>
              </div>
              <div className="bg-zinc-950 p-2.5 rounded border border-zinc-800">
                <span className="font-semibold text-zinc-200 block">RAT-02</span>
                <span className="text-[11px] text-zinc-400 font-sans block">Sortino Ratio 3Y</span>
              </div>
              <div className="bg-zinc-950 p-2.5 rounded border border-zinc-800">
                <span className="font-semibold text-zinc-200 block">RAT-03</span>
                <span className="text-[11px] text-zinc-400 font-sans block">Treynor Ratio 3Y</span>
              </div>
              <div className="bg-zinc-950 p-2.5 rounded border border-zinc-800">
                <span className="font-semibold text-zinc-200 block">RAT-04</span>
                <span className="text-[11px] text-zinc-400 font-sans block">Information Ratio 3Y</span>
              </div>
            </div>
          </div>

          {/* Dimension 4 */}
          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-5">
            <div className="flex items-center justify-between pb-3 border-b border-zinc-800">
              <span className="font-bold text-amber-400 uppercase">4. Market Sensitivity (5 Metrics)</span>
              <span className="text-[10px] text-zinc-500 bg-zinc-950 px-2 py-0.5 rounded border border-zinc-800">
                Awaiting Benchmark TRI Feeds
              </span>
            </div>
            <div className="mt-3 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
              <div className="bg-zinc-950 p-2.5 rounded border border-zinc-800">
                <span className="font-semibold text-zinc-200 block">MKT-01</span>
                <span className="text-[11px] text-zinc-400 font-sans block">Beta 3Y</span>
              </div>
              <div className="bg-zinc-950 p-2.5 rounded border border-zinc-800">
                <span className="font-semibold text-zinc-200 block">MKT-02</span>
                <span className="text-[11px] text-zinc-400 font-sans block">Downside Beta</span>
              </div>
              <div className="bg-zinc-950 p-2.5 rounded border border-zinc-800">
                <span className="font-semibold text-zinc-200 block">MKT-03</span>
                <span className="text-[11px] text-zinc-400 font-sans block">Upside Capture</span>
              </div>
              <div className="bg-zinc-950 p-2.5 rounded border border-zinc-800">
                <span className="font-semibold text-zinc-200 block">MKT-04</span>
                <span className="text-[11px] text-zinc-400 font-sans block">Downside Capture</span>
              </div>
              <div className="bg-zinc-950 p-2.5 rounded border border-zinc-800">
                <span className="font-semibold text-zinc-200 block">MKT-05</span>
                <span className="text-[11px] text-zinc-400 font-sans block">Capture Spread</span>
              </div>
            </div>
          </div>

          {/* Dimension 5 */}
          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-5">
            <div className="flex items-center justify-between pb-3 border-b border-zinc-800">
              <span className="font-bold text-sky-400 uppercase">5. Benchmark / Alpha (2 Metrics)</span>
              <span className="text-[10px] text-zinc-500 bg-zinc-950 px-2 py-0.5 rounded border border-zinc-800">
                Awaiting Benchmark TRI Feeds
              </span>
            </div>
            <div className="mt-3 grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="bg-zinc-950 p-2.5 rounded border border-zinc-800">
                <span className="font-semibold text-zinc-200 block">REL-01</span>
                <span className="text-[11px] text-zinc-400 font-sans block">Tracking Error 3Y</span>
              </div>
              <div className="bg-zinc-950 p-2.5 rounded border border-zinc-800">
                <span className="font-semibold text-zinc-200 block">REL-02</span>
                <span className="text-[11px] text-zinc-400 font-sans block">Jensen&apos;s Alpha 3Y</span>
              </div>
            </div>
          </div>

          {/* Dimension 6 */}
          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-5">
            <div className="flex items-center justify-between pb-3 border-b border-zinc-800">
              <span className="font-bold text-purple-400 uppercase">6. Portfolio Structure (5 Metrics)</span>
              <span className="text-[10px] text-zinc-500 bg-zinc-950 px-2 py-0.5 rounded border border-zinc-800">
                Awaiting Portfolio Holdings Ingestion
              </span>
            </div>
            <div className="mt-3 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
              <div className="bg-zinc-950 p-2.5 rounded border border-zinc-800">
                <span className="font-semibold text-zinc-200 block">PRT-01</span>
                <span className="text-[11px] text-zinc-400 font-sans block">Top-10 Concentration</span>
              </div>
              <div className="bg-zinc-950 p-2.5 rounded border border-zinc-800">
                <span className="font-semibold text-zinc-200 block">PRT-02</span>
                <span className="text-[11px] text-zinc-400 font-sans block">Effective Number of Holdings</span>
              </div>
              <div className="bg-zinc-950 p-2.5 rounded border border-zinc-800">
                <span className="font-semibold text-zinc-200 block">PRT-03</span>
                <span className="text-[11px] text-zinc-400 font-sans block">Active Share</span>
              </div>
              <div className="bg-zinc-950 p-2.5 rounded border border-zinc-800">
                <span className="font-semibold text-zinc-200 block">PRT-04</span>
                <span className="text-[11px] text-zinc-400 font-sans block">Monthly Weight Turnover</span>
              </div>
              <div className="bg-zinc-950 p-2.5 rounded border border-zinc-800">
                <span className="font-semibold text-zinc-200 block">PRT-05</span>
                <span className="text-[11px] text-zinc-400 font-sans block">Cash & Equivalent Allocation %</span>
              </div>
            </div>
          </div>

          {/* Dimension 7 */}
          <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-5">
            <div className="flex items-center justify-between pb-3 border-b border-zinc-800">
              <span className="font-bold text-rose-400 uppercase">7. Governance / Expense (1 Metric)</span>
              <span className="text-[10px] text-zinc-500 bg-zinc-950 px-2 py-0.5 rounded border border-zinc-800">
                Candidate Specification
              </span>
            </div>
            <div className="mt-3 grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="bg-zinc-950 p-2.5 rounded border border-zinc-800">
                <span className="font-semibold text-zinc-200 block">GOV-01</span>
                <span className="text-[11px] text-zinc-400 font-sans block">Direct Plan TER</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Navigation Links */}
      <div className="flex items-center justify-between border-t border-zinc-800 pt-6 font-mono text-xs">
        <Link
          href="/"
          className="text-zinc-400 hover:text-zinc-200 transition"
        >
          ← Return to Overview
        </Link>
        <Link
          href="/funds"
          className="inline-flex items-center gap-1.5 rounded bg-cyan-600 px-4 py-2 text-white font-semibold hover:bg-cyan-500 transition"
        >
          Explore Fund Catalog →
        </Link>
      </div>
    </PageContainer>
  );
}
