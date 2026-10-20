import Link from "next/link";
import { PageContainer } from "@/components/layout/PageContainer";
import { YukiraBrandLogo } from "@/components/brand/YukiraBrandLogo";

const SEVEN_STEPS = [
  {
    ordinal: "01",
    title: "Where am I?",
    body: "Canonical scheme identity: AMC, Plan (Direct vs. Regular), Option (Growth vs. IDCW), AMFI Code, and ISIN.",
  },
  {
    ordinal: "02",
    title: "What does YUKIRA know?",
    body: "Point-in-time observation ledger: verified trading dates, knowledge cutoffs, and immutable revision history.",
  },
  {
    ordinal: "03",
    title: "What does the data show?",
    body: "Objective mathematical outputs: realized CAGR, volatility, drawdowns, capture ratios, and downside beta.",
  },
  {
    ordinal: "04",
    title: "What does it mean?",
    body: "Deterministic statistical interpretation of return dispersion and market sensitivity without promotional speculation.",
  },
  {
    ordinal: "05",
    title: "What are the risks?",
    body: "Downside semideviation, Ulcer Index path stress, Value at Risk (95%), and maximum historical recovery duration.",
  },
  {
    ordinal: "06",
    title: "What is missing?",
    body: "Explicit disclosure of uningested benchmark series, missing portfolio disclosures, or unvalidated candidate metrics.",
  },
  {
    ordinal: "07",
    title: "What should I investigate next?",
    body: "Targeted verification checkpoints: portfolio concentration, manager tenure, market regime behavior, and fee drag.",
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
    body: "Every historical query enforces both an analysis_cutoff and a knowledge_cutoff. Observations published after the knowledge cutoff can never contaminate historical evaluations.",
  },
  {
    ordinal: "03",
    title: "Cryptographic Lineage",
    body: "Every input observation traces directly to an immutable source_artifact record with SHA-256 digest, HTTP request URI, and retrieval timestamp.",
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
      {/* Ambient background glow accents inspired by Tree of Life emblem */}
      <div className="pointer-events-none fixed left-1/2 top-0 -z-10 h-[360px] w-[900px] -translate-x-1/2 bg-gradient-to-b from-[#d4af37]/[0.08] via-[#f2ca50]/[0.02] to-transparent blur-[120px]" />
      <div className="pointer-events-none fixed -bottom-32 right-0 -z-10 h-[500px] w-[500px] bg-[#d4af37]/[0.03] blur-[140px]" />

      {/* Hero Masthead with Tree of Life Sigil */}
      <section className="relative mb-12 overflow-hidden rounded-3xl border border-border-gold-subtle bg-gradient-to-b from-surface-midnight via-surface-midnight to-surface-obsidian p-6 sm:p-10 lg:p-14 shadow-2xl glow-gold">
        <div className="flex flex-col lg:flex-row items-center justify-between gap-10">
          <div className="flex flex-col gap-6 max-w-3xl">
            <div className="inline-flex items-center gap-2.5 rounded-full border border-border-gold-subtle bg-surface-navy-raised/80 px-4 py-1.5 text-xs font-mono font-medium text-primary shadow-sm self-start">
              <span className="h-2 w-2 rounded-full bg-primary animate-pulse" />
              <span className="tracking-[0.14em] uppercase text-[11px]">Quantitative Investment Intelligence</span>
            </div>

            <h1 className="text-3xl font-bold tracking-tight text-text-cream-bright sm:text-5xl lg:text-6xl leading-[1.15]">
              Bringing investment intelligence to life.
            </h1>

            <p className="text-base sm:text-lg leading-relaxed text-text-cream-body max-w-2xl font-sans">
              <strong className="text-primary font-semibold">Before you commit capital, ask one more question.</strong> YUKIRA is an institutional-grade, evidence-based verification platform for Indian mutual funds, replacing trailing returns and marketing narratives with deterministic calculation, point-in-time verification, and cryptographic provenance.
            </p>

            <div className="flex flex-wrap items-center gap-4 pt-2">
              <Link
                href="/funds"
                className="flex items-center justify-center rounded-full bg-gradient-to-r from-[#d4af37] via-[#f2ca50] to-[#e6be44] px-7 py-3.5 font-sans text-sm font-semibold text-[#1e1704] glow-gold-sm transition-all duration-200 hover:scale-[1.02] hover:from-[#e1bc42] hover:to-[#ffd868] shadow-md active:scale-[0.98]"
              >
                Explore Funds
              </Link>
              <Link
                href="/funds/HDFC_FLEXI"
                className="flex items-center justify-center rounded-full border border-border-gold-subtle bg-surface-navy-raised px-6 py-3.5 font-sans text-sm font-semibold text-text-cream-bright transition-all hover:bg-surface-navy-hover hover:border-border-gold-bright"
              >
                Canonical Pilot · HDFC Flexi Cap
              </Link>
              <Link
                href="/methodology"
                className="rounded-full px-5 py-3 font-sans text-sm font-semibold text-text-cream-muted transition-colors hover:text-primary"
              >
                Methodology & Governance &rarr;
              </Link>
            </div>
          </div>

          {/* Majestic Tree of Life Emblem Display */}
          <div className="relative shrink-0 flex items-center justify-center">
            {/* Ambient backlight glow for transparent PNG */}
            <div className="absolute h-60 w-60 sm:h-72 sm:w-72 rounded-full bg-primary/15 blur-3xl -z-10 pointer-events-none" />

            <div className="relative h-60 w-60 sm:h-72 sm:w-72 rounded-3xl border border-border-gold-subtle/80 bg-surface-midnight/70 backdrop-blur-md p-6 shadow-2xl glow-gold-lg flex items-center justify-center transition-transform duration-300 hover:scale-[1.02]">
              <YukiraBrandLogo variant="emblem" className="!h-48 !w-48 sm:!h-60 sm:!w-60" priority />
            </div>
          </div>
        </div>
      </section>

      {/* Governance Mandate Banner */}
      <section className="mb-12">
        <div className="rounded-2xl border border-border-gold-subtle bg-surface-midnight/90 p-6 shadow-xl backdrop-blur-sm glow-gold">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-border-gold-subtle/60 pb-4 mb-4">
            <div>
              <p className="font-mono text-[11px] uppercase tracking-[0.12em] text-primary font-semibold">
                Governance Mandate
              </p>
              <h2 className="text-lg font-bold text-text-cream-bright mt-1">
                Methodology Verification Lifecycle
              </h2>
            </div>
            <span className="inline-flex items-center gap-2 rounded-full px-3.5 py-1.5 font-mono text-[11px] uppercase tracking-[0.08em] bg-status-candidate-bg text-status-candidate border border-status-candidate/40 font-semibold shadow-sm">
              <span className="w-1.5 h-1.5 rounded-full bg-status-candidate" />
              IMPLEMENTED ≠ VALIDATED ≠ APPROVED
            </span>
          </div>
          <p className="text-sm leading-relaxed text-text-cream-body">
            In YUKIRA, implementing an algorithm in code does not constitute approval for live investment decision support. Every calculation is governed under strict empirical validation standards across candidate, validated, and approved methodology tiers.
          </p>
          <div className="mt-4 pt-3.5 border-t border-border-gold-subtle/60 font-mono text-xs text-text-cream-muted flex flex-wrap gap-4">
            <span className="flex items-center gap-1.5">
              <span className="w-1.5 h-1.5 rounded-full bg-primary" />
              Zero automated buy/sell recommendations
            </span>
            <span>·</span>
            <span>Zero commercial star ratings</span>
            <span>·</span>
            <span>Zero synthetic NAV imputation</span>
            <span>·</span>
            <span>Zero LLM mathematical calculation</span>
          </div>
        </div>
      </section>

      {/* Canonical Pilot Spotlight */}
      <section className="mb-14" id="pilot">
        <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4 mb-6">
          <div>
            <p className="font-mono text-[11px] uppercase tracking-[0.14em] text-primary font-semibold">
              01 · Baseline Verification
            </p>
            <h2 className="text-2xl font-bold tracking-tight text-text-cream-bright sm:text-3xl">
              Canonical Pilot Instrument
            </h2>
          </div>
          <Link
            href="/funds/HDFC_FLEXI"
            className="rounded-full bg-surface-navy-raised px-4 py-2 font-sans text-xs font-semibold text-text-cream-bright border border-border-gold-subtle hover:text-primary hover:border-border-gold-bright transition-all"
          >
            Open Fund Workspace · HDFC Flexi Cap &rarr;
          </Link>
        </div>

        <div className="rounded-2xl border border-border-gold-subtle bg-surface-midnight/90 p-6 mb-6 shadow-xl backdrop-blur-sm glow-gold">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-border-gold-subtle/60 pb-4 mb-4">
            <div className="flex items-center gap-3">
              <div className="w-8 h-8 rounded-xl bg-surface-navy-raised border border-border-gold-subtle flex items-center justify-center font-mono text-sm text-primary font-bold">
                H
              </div>
              <h3 className="text-base font-bold text-text-cream-bright">
                HDFC Flexi Cap Fund (Direct Plan · Growth Option)
              </h3>
            </div>
            <span className="font-mono text-xs text-primary/80">
              AMFI 118955 · ISIN INF179K01UT0
            </span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="p-4 rounded-xl bg-surface-obsidian border border-border-gold-subtle/60">
              <p className="font-mono text-[11px] uppercase tracking-[0.1em] text-primary/80 font-medium">
                AMFI Scheme Code
              </p>
              <p className="font-mono text-base font-bold tabular text-text-cream-bright mt-1">118955</p>
              <p className="font-mono text-[11px] text-text-cream-muted mt-0.5">ISIN: INF179K01UT0</p>
            </div>
            <div className="p-4 rounded-xl bg-surface-obsidian border border-border-gold-subtle/60">
              <p className="font-mono text-[11px] uppercase tracking-[0.1em] text-primary/80 font-medium">
                Observation Horizon
              </p>
              <p className="font-mono text-base font-bold tabular text-text-cream-bright mt-1">5 Full Years</p>
              <p className="font-mono text-[11px] text-text-cream-muted mt-0.5">2019-01-01 → 2024-01-15</p>
            </div>
            <div className="p-4 rounded-xl bg-surface-obsidian border border-border-gold-subtle/60">
              <p className="font-mono text-[11px] uppercase tracking-[0.1em] text-primary/80 font-medium">
                Trading Ledger
              </p>
              <p className="font-mono text-base font-bold tabular text-primary mt-1">1,243 Observations</p>
              <p className="font-mono text-[11px] text-text-cream-muted mt-0.5">Zero synthetic gaps</p>
            </div>
            <div className="p-4 rounded-xl bg-surface-obsidian border border-border-gold-subtle/60">
              <p className="font-mono text-[11px] uppercase tracking-[0.1em] text-primary/80 font-medium">
                Cryptographic Hash
              </p>
              <p className="font-mono text-sm font-bold text-text-cream-bright mt-1 truncate" title="SHA-256: 900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259">
                900508f8bf13…
              </p>
              <p className="font-mono text-[11px] text-primary/70 mt-0.5">AMFI Raw Source #1</p>
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
              status: "APPROVED",
            },
            {
              code: "RET-05",
              name: "3-Year CAGR",
              value: "24.58%",
              note: "36M Compound Annualized",
              status: "CANDIDATE",
            },
            {
              code: "RSK-02",
              name: "Annualized Volatility (3Y)",
              value: "14.69%",
              note: "√252 Annualized Dispersion",
              status: "CANDIDATE",
            },
            {
              code: "RSK-03",
              name: "Maximum Drawdown (3Y)",
              value: "-12.45%",
              note: "Peak-to-Trough Decline",
              status: "CANDIDATE",
              risk: true,
            },
          ].map((m) => (
            <div
              key={m.code}
              className="p-5 rounded-2xl border border-border-gold-subtle bg-surface-midnight/90 flex flex-col justify-between shadow-lg glow-gold"
            >
              <div className="flex items-center justify-between gap-2">
                <span className="font-sans text-xs text-text-cream-muted font-medium">{m.name}</span>
                <span className="font-mono text-xs text-primary font-bold">{m.code}</span>
              </div>
              <div className={`my-3 text-3xl font-bold font-mono tracking-tight tabular ${m.risk ? 'text-status-risk' : 'text-text-cream-bright'}`}>
                {m.value}
              </div>
              <div className="flex items-center justify-between pt-2 border-t border-border-gold-subtle/50 font-mono text-[11px] text-text-cream-muted">
                <span>{m.note}</span>
                <span className={`px-2 py-0.5 rounded-full text-[9.5px] uppercase font-bold tracking-wider ${
                  m.status === 'APPROVED' ? 'bg-status-approved-bg text-status-approved border border-status-approved/30' : 'bg-status-candidate-bg text-status-candidate border border-status-candidate/30'
                }`}>
                  {m.status}
                </span>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Progressive Disclosure Architecture */}
      <section className="mb-14" id="framework">
        <div className="mb-6">
          <p className="font-mono text-[11px] uppercase tracking-[0.14em] text-primary font-semibold">
            02 · Information Architecture
          </p>
          <h2 className="text-2xl font-bold tracking-tight text-text-cream-bright sm:text-3xl">
            7-Step Progressive Disclosure Architecture
          </h2>
          <p className="mt-2 text-sm text-text-cream-body max-w-2xl leading-relaxed">
            Every screen answers three simple questions: Where am I? What am I looking at? What can I do next? Showing immediate clarity at the surface, with complete mathematical evidence one level deeper.
          </p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {SEVEN_STEPS.map((step) => (
            <div
              key={step.ordinal}
              className="p-5 rounded-2xl border border-border-gold-subtle bg-surface-midnight/90 hover:border-border-gold-muted/80 transition-all shadow-md glow-gold"
            >
              <span className="font-mono text-xs font-bold text-primary">
                {step.ordinal}
              </span>
              <h3 className="mt-2 text-sm font-bold text-text-cream-bright">
                {step.title}
              </h3>
              <p className="mt-2 text-xs leading-relaxed text-text-cream-body font-sans">
                {step.body}
              </p>
            </div>
          ))}
        </div>
      </section>

      {/* Epistemic Architecture */}
      <section className="mb-14" id="philosophy">
        <div className="mb-6">
          <p className="font-mono text-[11px] uppercase tracking-[0.14em] text-primary font-semibold">
            03 · Foundational Rules
          </p>
          <h2 className="text-2xl font-bold tracking-tight text-text-cream-bright sm:text-3xl">
            Epistemic Architecture & Invariants
          </h2>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
          {PRINCIPLES.map((p) => (
            <div
              key={p.ordinal}
              className="p-6 rounded-2xl border border-border-gold-subtle bg-surface-midnight/90 hover:border-border-gold-muted/80 transition-all shadow-md glow-gold"
            >
              <div className="font-mono text-xs font-bold text-primary mb-2">
                {p.ordinal}
              </div>
              <h3 className="text-base font-bold text-text-cream-bright">
                {p.title}
              </h3>
              <p className="mt-2.5 text-xs leading-relaxed text-text-cream-body font-sans">
                {p.body}
              </p>
            </div>
          ))}
        </div>
      </section>

      {/* Next Verification Steps */}
      <section className="mb-12">
        <div className="mb-6">
          <p className="font-mono text-[11px] uppercase tracking-[0.14em] text-primary font-semibold">
            04 · Continue Verification
          </p>
          <h2 className="text-2xl font-bold tracking-tight text-text-cream-bright sm:text-3xl">
            Verification Workflows
          </h2>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-5">
          {[
            {
              href: "/funds",
              title: "Explore Funds Catalog",
              note: "Search canonical scheme universe by AMC, category, plan, and option with live AMFI data availability.",
            },
            {
              href: "/funds/HDFC_FLEXI",
              title: "Review Analytical Profile",
              note: "Sixteen point-in-time metrics across return, risk, ratio, and market dimensions for canonical pilot HDFC Flexi Cap.",
            },
            {
              href: "/methodology",
              title: "Governance Specifications",
              note: "Methodology lifecycle, approval states, and frozen Phase 2H mathematical definitions.",
            },
          ].map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className="p-6 rounded-2xl border border-border-gold-subtle bg-surface-midnight/90 hover:border-primary/60 hover:bg-surface-navy-raised/60 transition-all flex flex-col justify-between shadow-lg glow-gold group"
            >
              <div>
                <h3 className="text-base font-bold text-text-cream-bright group-hover:text-primary transition-colors">
                  {item.title}
                </h3>
                <p className="mt-2 text-xs text-text-cream-muted leading-relaxed font-sans">
                  {item.note}
                </p>
              </div>
              <div className="mt-4 font-mono text-xs font-semibold text-primary flex items-center gap-1.5">
                Open workflow &rarr;
              </div>
            </Link>
          ))}
        </div>
      </section>
    </PageContainer>
  );
}
