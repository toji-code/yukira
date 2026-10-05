import Link from "next/link";
import { PageContainer } from "@/components/layout/PageContainer";

const SEVEN_STEPS = [
  {
    ordinal: "01",
    title: "What is this?",
    body: "Canonical scheme identity: AMC, Plan (Direct vs. Regular), Option (Growth), AMFI Code, and ISIN.",
  },
  {
    ordinal: "02",
    title: "What does YUKIRA know?",
    body: "Point-in-time observation ledger: verified trading dates, knowledge cutoffs, and revision history.",
  },
  {
    ordinal: "03",
    title: "What does the data show?",
    body: "Objective mathematical outputs: realized CAGR, volatility, drawdowns, ratios, and downside beta.",
  },
  {
    ordinal: "04",
    title: "What does it mean?",
    body: "Deterministic statistical interpretation of return dispersion and market sensitivity without speculation.",
  },
  {
    ordinal: "05",
    title: "What are the risks?",
    body: "Downside semideviation, Ulcer Index path stress, Value at Risk (95%), and maximum recovery duration.",
  },
  {
    ordinal: "06",
    title: "What is missing?",
    body: "Explicit disclosure of uningested benchmark series, missing exchange calendars, or unvalidated conventions.",
  },
  {
    ordinal: "07",
    title: "What should the investor investigate next?",
    body: "Targeted verification checkpoints: portfolio concentration, manager tenure, market regime behavior, and expense drag before capital is allocated.",
  },
];

const PRINCIPLES = [
  {
    ordinal: "01",
    title: "Deterministic Math",
    body: "Calculations originate exclusively from pure, vectorized Python algorithms (NumPy 2, Polars, SciPy). LLMs are strictly forbidden from computing, smoothing, or estimating financial observations.",
  },
  {
    ordinal: "02",
    title: "Zero Look-Ahead Bias",
    body: "Every historical query specifies both an analysis_cutoff and a knowledge_cutoff. Observations published after the knowledge cutoff can never contaminate historical evaluations.",
    code: ["analysis_cutoff", "knowledge_cutoff"],
  },
  {
    ordinal: "03",
    title: "Cryptographic Lineage",
    body: "Every input observation traces directly to an immutable source_artifact record with SHA-256 digest, HTTP request URI, and retrieval timestamp.",
    code: ["source_artifact"],
  },
  {
    ordinal: "04",
    title: "Risk Before Return",
    body: "Downside semideviation, Ulcer Index, maximum drawdown duration, and downside beta are prioritized over trailing point-to-point gains that mask intermediate capital impairment.",
  },
  {
    ordinal: "05",
    title: "Reputation Over Hype",
    body: "If data is missing or an indicator is unvalidated, the platform explicitly reports “Not available” or “Candidate methodology”. We never fabricate default values.",
  },
  {
    ordinal: "06",
    title: "Zero Star Ratings",
    body: "YUKIRA produces no opaque composite scores or algorithmic star badges. We equip the allocator with auditable empirical evidence to evaluate their own hypothesis.",
  },
];

export default function HomePage() {
  return (
    <PageContainer width="app">
      {/* Template Hero Masthead */}
      <section className="relative overflow-hidden rounded-3xl bg-surface-raised p-6 sm:p-10 lg:p-14 mb-10 border border-border dark:bg-[#131314] dark:border-[#2a2a2b] dark:grid-bg">
        <div className="flex flex-col gap-6 max-w-4xl">
          <div className="inline-flex items-center gap-2 rounded-full border border-border bg-background px-3.5 py-1 text-xs font-medium text-text-secondary dark:bg-[#1c1b1d] dark:border-[#2a2a2b] dark:text-[#50d8e9]">
            <span className="h-2 w-2 rounded-full bg-accent dark:bg-[#50d8e9]" />
            Quantitative Investment Intelligence Platform
          </div>

          <h1 className="text-3xl font-extrabold tracking-tight text-text-primary sm:text-5xl lg:text-6xl">
            Bringing investment intelligence to life.
          </h1>

          <p className="text-base sm:text-lg leading-relaxed text-text-secondary max-w-3xl">
            Before you commit capital, ask one more question. YUKIRA is an evidence-based quantitative decision-support platform for Indian mutual funds, replacing trailing returns and star ratings with deterministic calculation, point-in-time verification, and cryptographic provenance.
          </p>

          <div className="flex flex-wrap items-center gap-3 pt-2">
            <Link
              href="/funds"
              className="rounded-full bg-text-primary px-6 py-3 text-sm font-semibold text-background transition-all hover:opacity-90 dark:bg-[#5E6BFF] dark:text-white"
            >
              Explore Funds
            </Link>
            <Link
              href="/funds/10189"
              className="rounded-full bg-background px-6 py-3 text-sm font-semibold text-text-primary border border-border transition-all hover:bg-surface-raised dark:bg-[#1c1b1d] dark:border-[#2a2a2b] dark:text-[#e5e2e3]"
            >
              Canonical Pilot #10189
            </Link>
            <Link
              href="/methodology"
              className="rounded-full bg-transparent px-6 py-3 text-sm font-semibold text-text-secondary hover:text-text-primary transition-colors"
            >
              Methodology & Governance
            </Link>
          </div>
        </div>
      </section>

      {/* Governance Mandate Banner */}
      <section className="mb-10">
        <div className="panel p-6 dark:bg-[#1c1b1d] dark:border-[#2a2a2b]">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-border pb-4 mb-4 dark:border-[#2a2a2b]">
            <div>
              <p className="font-mono text-xs uppercase tracking-widest text-text-tertiary">
                Governance Mandate
              </p>
              <h2 className="text-lg font-bold text-text-primary mt-1">
                Methodology Verification Lifecycle
              </h2>
            </div>
            <span className="inline-flex items-center gap-2 rounded px-3 py-1 font-mono text-xs uppercase tracking-widest bg-[#471e00]/30 text-[#ffb689] border border-[#e0731d]">
              IMPLEMENTED ≠ VALIDATED ≠ APPROVED
            </span>
          </div>
          <p className="text-sm leading-relaxed text-text-secondary">
            In YUKIRA, implementing an algorithm in code does not constitute approval for live investment advice. Every calculation is governed under strict empirical validation standards across candidate, validated, and approved methodology tiers.
          </p>
          <div className="mt-4 pt-3 border-t border-border font-mono text-xs text-text-tertiary flex flex-wrap gap-4 dark:border-[#2a2a2b]">
            <span>Zero automated buy/sell tips</span>
            <span>·</span>
            <span>Zero star ratings</span>
            <span>·</span>
            <span>Zero synthetic NAV imputation</span>
            <span>·</span>
            <span>Zero LLM financial calculations</span>
          </div>
        </div>
      </section>

      {/* Canonical Pilot Spotlight */}
      <section className="mb-12" id="pilot">
        <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4 mb-6">
          <div>
            <p className="font-mono text-xs uppercase tracking-widest text-text-tertiary">
              01 · Baseline Verification
            </p>
            <h2 className="text-2xl font-bold tracking-tight text-text-primary sm:text-3xl">
              Canonical Pilot Instrument
            </h2>
          </div>
          <Link
            href="/funds/10189"
            className="rounded-full bg-surface-raised px-4 py-2 text-xs font-semibold text-text-primary border border-border hover:bg-surface dark:bg-[#1c1b1d] dark:border-[#2a2a2b]"
          >
            Open Fund Workspace #10189
          </Link>
        </div>

        <div className="panel p-6 mb-6 dark:bg-[#1c1b1d] dark:border-[#2a2a2b]">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-border pb-4 mb-4 dark:border-[#2a2a2b]">
            <h3 className="text-base font-bold text-text-primary">
              HDFC Flexi Cap Fund (Direct Plan · Growth Option)
            </h3>
            <span className="font-mono text-xs text-text-tertiary">
              AMFI 118955 · ISIN INF179K01UT0
            </span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="p-3 rounded-xl bg-surface-inset dark:bg-[#0e0e0f] dark:border dark:border-[#2a2a2b]">
              <p className="font-mono text-[11px] uppercase tracking-widest text-text-tertiary">
                AMFI Scheme Code
              </p>
              <p className="font-mono text-sm font-semibold text-text-primary mt-1">118955</p>
              <p className="font-mono text-[10px] text-text-tertiary mt-0.5">ISIN: INF179K01UT0</p>
            </div>
            <div className="p-3 rounded-xl bg-surface-inset dark:bg-[#0e0e0f] dark:border dark:border-[#2a2a2b]">
              <p className="font-mono text-[11px] uppercase tracking-widest text-text-tertiary">
                Observation Horizon
              </p>
              <p className="font-mono text-sm font-semibold text-text-primary mt-1">5 Full Years</p>
              <p className="font-mono text-[10px] text-text-tertiary mt-0.5">2019-01-01 → 2024-01-15</p>
            </div>
            <div className="p-3 rounded-xl bg-surface-inset dark:bg-[#0e0e0f] dark:border dark:border-[#2a2a2b]">
              <p className="font-mono text-[11px] uppercase tracking-widest text-text-tertiary">
                Trading Ledger
              </p>
              <p className="font-mono text-sm font-semibold text-text-primary mt-1">1,243 Market Dates</p>
              <p className="font-mono text-[10px] text-text-tertiary mt-0.5">Zero synthetic gaps</p>
            </div>
            <div className="p-3 rounded-xl bg-surface-inset dark:bg-[#0e0e0f] dark:border dark:border-[#2a2a2b]">
              <p className="font-mono text-[11px] uppercase tracking-widest text-text-tertiary">
                Cryptographic Hash
              </p>
              <p className="font-mono text-xs font-semibold text-text-primary mt-1 truncate" title="SHA-256: 900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259">
                900508f8bf13…
              </p>
              <p className="font-mono text-[10px] text-text-tertiary mt-0.5">AMFI Raw Source #1</p>
            </div>
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {[
            {
              code: "RET-02",
              name: "Simple Period Return",
              value: "+2.45%",
              note: "2024-01-01 to 2024-01-15",
            },
            {
              code: "RET-05",
              name: "3-Year CAGR",
              value: "24.58%",
              note: "36M Compound Annualized",
            },
            {
              code: "RSK-02",
              name: "Annualized Volatility (3Y)",
              value: "14.69%",
              note: "√252 Annualized Dispersion",
            },
            {
              code: "RSK-03",
              name: "Maximum Drawdown (3Y)",
              value: "-12.45%",
              note: "Peak-to-Trough Decline",
              risk: true,
            },
          ].map((m) => (
            <div
              key={m.code}
              className="p-5 rounded-2xl bg-surface border border-border flex flex-col justify-between dark:bg-[#1c1b1d] dark:border-[#2a2a2b]"
            >
              <div className="flex items-center justify-between gap-2">
                <span className="font-mono text-xs uppercase tracking-widest text-text-tertiary">{m.name}</span>
                <span className="font-mono text-xs text-accent font-semibold dark:text-[#50d8e9]">{m.code}</span>
              </div>
              <div className="my-3 text-3xl font-bold font-mono tracking-tight text-text-primary">
                {m.value}
              </div>
              <div className="font-mono text-[11px] text-text-tertiary">{m.note}</div>
            </div>
          ))}
        </div>
      </section>

      {/* Progressive Disclosure Architecture */}
      <section className="mb-12" id="framework">
        <div className="mb-6">
          <p className="font-mono text-xs uppercase tracking-widest text-text-tertiary">
            02 · Information Architecture
          </p>
          <h2 className="text-2xl font-bold tracking-tight text-text-primary sm:text-3xl">
            7-Step Progressive Disclosure Architecture
          </h2>
          <p className="mt-2 text-sm text-text-secondary">
            Every analytical page presents immediate clarity at the surface and complete cryptographic provenance one level deeper.
          </p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {SEVEN_STEPS.map((step) => (
            <div
              key={step.ordinal}
              className="p-5 rounded-2xl bg-surface border border-border dark:bg-[#1c1b1d] dark:border-[#2a2a2b]"
            >
              <span className="font-mono text-xs font-bold text-accent dark:text-[#50d8e9]">
                {step.ordinal}
              </span>
              <h3 className="mt-2 text-sm font-bold text-text-primary">
                {step.title}
              </h3>
              <p className="mt-2 text-xs leading-relaxed text-text-secondary">
                {step.body}
              </p>
            </div>
          ))}
        </div>
      </section>

      {/* Epistemic Architecture */}
      <section className="mb-12" id="philosophy">
        <div className="mb-6">
          <p className="font-mono text-xs uppercase tracking-widest text-text-tertiary">
            03 · Foundational Rules
          </p>
          <h2 className="text-2xl font-bold tracking-tight text-text-primary sm:text-3xl">
            Epistemic Architecture & Invariants
          </h2>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {PRINCIPLES.map((p) => (
            <div
              key={p.ordinal}
              className="p-6 rounded-2xl bg-surface border border-border dark:bg-[#1c1b1d] dark:border-[#2a2a2b]"
            >
              <div className="font-mono text-xs font-bold text-accent dark:text-[#50d8e9] mb-2">
                {p.ordinal}
              </div>
              <h3 className="text-base font-bold text-text-primary">
                {p.title}
              </h3>
              <p className="mt-2 text-xs leading-relaxed text-text-secondary">
                {p.body}
              </p>
            </div>
          ))}
        </div>
      </section>

      {/* Next Verification Steps */}
      <section className="mb-10">
        <div className="mb-6">
          <p className="font-mono text-xs uppercase tracking-widest text-text-tertiary">
            04 · Continue Verification
          </p>
          <h2 className="text-2xl font-bold tracking-tight text-text-primary sm:text-3xl">
            Verification Workflows
          </h2>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          {[
            {
              href: "/funds",
              title: "Open Fund Discovery Catalog",
              note: "Search canonical scheme universe by AMC, plan, option, and AMFI code.",
            },
            {
              href: "/funds/10189",
              title: "Review Analytical Profile",
              note: "Sixteen metrics across return, risk, ratio, and market dimensions.",
            },
            {
              href: "/methodology",
              title: "Governance Specifications",
              note: "Methodology lifecycle, approval state, and frozen Phase 2H contract.",
            },
          ].map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className="p-6 rounded-2xl bg-surface border border-border hover:border-text-primary transition-all flex flex-col justify-between dark:bg-[#1c1b1d] dark:border-[#2a2a2b] dark:hover:border-[#50d8e9]"
            >
              <div>
                <h3 className="text-base font-bold text-text-primary">
                  {item.title}
                </h3>
                <p className="mt-2 text-xs text-text-secondary leading-relaxed">
                  {item.note}
                </p>
              </div>
              <div className="mt-4 font-mono text-xs font-semibold text-accent dark:text-[#50d8e9] flex items-center gap-1">
                Open workflow &rarr;
              </div>
            </Link>
          ))}
        </div>
      </section>
    </PageContainer>
  );
}
