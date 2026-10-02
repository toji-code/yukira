import Link from "next/link";
import {
  PageContainer,
  SectionHeading,
  MetricGrid,
} from "@/components/layout/PageContainer";

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
      {/* Masthead — proposition and primary investor actions */}
      <section className="panel mb-6">
        <div className="panel-header">
          <p className="eyebrow">Quantitative Investment Intelligence · Institutional-Oriented Checkpoint</p>
        </div>
        <div className="px-5 py-6 sm:px-6 sm:py-8">
          <h1 className="max-w-[26ch] text-[clamp(1.75rem,4vw,2.75rem)] font-semibold leading-[1.1] tracking-[-0.025em] text-text-primary">
            Before you commit capital, ask one more question.
          </h1>
          <p className="mt-4 max-w-[74ch] text-[14px] leading-[1.6] text-text-secondary">
            YUKIRA is an evidence-based quantitative decision-support platform initially focused on
            Indian mutual funds. We reject simplistic trailing returns, opaque star ratings, and
            marketing narratives. Instead, we provide deterministic mathematical calculation,
            cryptographic source provenance, and point-in-time empirical verification.
          </p>
          <div className="mt-6 flex flex-wrap items-center gap-2">
            <Link href="/funds" className="btn btn-primary">
              Explore Funds
            </Link>
            <Link href="/funds/1" className="btn btn-secondary">
              Canonical Pilot Workspace
            </Link>
            <Link href="/methodology" className="btn btn-secondary">
              Methodology &amp; Governance
            </Link>
          </div>
        </div>
      </section>

      {/* Governance mandate — contextual, not a decorative ambient banner */}
      <section className="mb-8" aria-label="Governance Mandate">
        <div className="panel state-candidate">
          <div className="panel-header">
            <p className="eyebrow">Governance Mandate</p>
            <span className="status-badge state-candidate">
              IMPLEMENTED ≠ VALIDATED ≠ APPROVED
            </span>
          </div>
          <div className="px-4 py-4">
            <p className="max-w-[80ch] text-[13px] leading-[1.6] text-text-secondary">
              In YUKIRA, implementing an algorithm in code does not constitute approval for live
              investment advice. Every calculation is governed under strict empirical validation
              standards, ensuring transparent verification across approved, candidate, and research
              methodologies.
            </p>
            <p className="mt-3 border-t border-border pt-3 font-mono text-[11px] leading-[1.6] text-text-tertiary">
              Zero automated buy/sell tips · Zero 5-star ratings · Zero synthetic NAV imputation ·
              Zero LLM hallucinations in financial calculations.
            </p>
          </div>
        </div>
      </section>

      {/* Canonical pilot instrument — evidence at a glance */}
      <section className="mb-10" id="pilot">
        <div className="mb-4">
          <SectionHeading
            ordinal="01"
            title="Canonical Pilot Instrument"
            description="Authoritative canonical pilot dataset for empirical baseline verifications."
            action={
              <Link href="/funds/1" className="btn btn-secondary btn-sm">
                Open Fund Workspace
              </Link>
            }
          />
        </div>

        <div className="panel mb-4">
          <div className="panel-header">
            <h3 className="text-[15px] font-semibold text-text-primary">
              HDFC Flexi Cap Fund (Direct Plan · Growth)
            </h3>
            <span className="mono-meta">AMFI 118955 · ISIN INF179K01UT0</span>
          </div>
          <dl className="grid grid-cols-1 gap-3 px-4 py-4 sm:grid-cols-2 xl:grid-cols-4">
            <div>
              <dt className="def-label">AMFI Scheme Code</dt>
              <dd className="def-value">118955</dd>
              <dd className="mono-meta mt-1">ISIN: INF179K01UT0</dd>
            </div>
            <div>
              <dt className="def-label">Observation Horizon</dt>
              <dd className="def-value">5 Full Years</dd>
              <dd className="mono-meta mt-1">2019-01-01 → 2024-01-15</dd>
            </div>
            <div>
              <dt className="def-label">Trading Ledger</dt>
              <dd className="def-value">1,243 Market Dates</dd>
              <dd className="mono-meta mt-1">Zero synthetic gaps</dd>
            </div>
            <div>
              <dt className="def-label">Cryptographic Hash</dt>
              <dd
                className="def-value text-[12px]"
                title="SHA-256: 900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259"
              >
                900508f8bf13…
              </dd>
              <dd className="mono-meta mt-1">AMFI Raw Source #1</dd>
            </div>
          </dl>
        </div>

        <MetricGrid min={260}>
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
            <div key={m.code} className={m.risk ? "metric-tile metric-risk-rule" : "metric-tile"}>
              <div className="flex items-baseline justify-between gap-2">
                <span className="eyebrow">{m.name}</span>
                <span className="mono-meta shrink-0">{m.code}</span>
              </div>
              <div className="metric-rule" />
              <p className="data-value-lg">{m.value}</p>
              <p className="mt-auto pt-2 font-mono text-[11px] text-text-tertiary">{m.note}</p>
            </div>
          ))}
        </MetricGrid>

        <p className="mt-3 font-mono text-[11px] leading-[1.6] text-text-tertiary">
          Verified canonical analytical metrics, evaluated 2024-01-15 with knowledge cutoff
          2024-01-31.
        </p>
      </section>

      {/* Progressive disclosure framework */}
      <section className="mb-10 scroll-mt-20" id="framework">
        <div className="mb-4">
          <SectionHeading
            ordinal="02"
            title="Progressive Disclosure Architecture"
            description="Every analytical page adheres to a 7-step progressive disclosure framework, presenting immediate clarity at the surface and complete cryptographic provenance one level deeper."
          />
        </div>

        <ol className="grid grid-cols-1 gap-px overflow-hidden border border-border bg-border sm:grid-cols-2 lg:grid-cols-4">
          {SEVEN_STEPS.map((step) => (
            <li key={step.ordinal} className="bg-surface p-4">
              <span className="tab-ordinal">{step.ordinal}</span>
              <h3 className="mt-1.5 text-[13px] font-semibold leading-[1.4] text-text-primary">
                {step.title}
              </h3>
              <p className="mt-1.5 text-[12px] leading-[1.55] text-text-secondary">{step.body}</p>
            </li>
          ))}
        </ol>
      </section>

      {/* Epistemic principles */}
      <section className="mb-10 scroll-mt-20" id="philosophy">
        <div className="mb-4">
          <SectionHeading
            ordinal="03"
            title="Epistemic Architecture"
            description="Mathematical discipline, deterministic execution, and complete lineage replace fintech marketing promises."
          />
        </div>

        <MetricGrid min={320}>
          {PRINCIPLES.map((p) => (
            <div key={p.ordinal} className="panel">
              <div className="panel-header">
                <span className="tab-ordinal">{p.ordinal}</span>
              </div>
              <div className="px-4 py-4">
                <h3 className="text-[14px] font-semibold leading-[1.35] text-text-primary">
                  {p.title}
                </h3>
                <p className="mt-2 text-[12.5px] leading-[1.6] text-text-secondary">
                  {p.body}
                  {p.code?.map((c) => (
                    <code key={c} className="field-mono mx-0.5 inline-block align-baseline">
                      {c}
                    </code>
                  ))}
                </p>
              </div>
            </div>
          ))}
        </MetricGrid>
      </section>

      {/* Continuing investigation — a task list, not a marketing CTA */}
      <section className="mb-4">
        <div className="mb-4">
          <SectionHeading
            ordinal="04"
            title="Continue The Verification"
            description="Inspect authentic mutual fund records, examine point-in-time calculation runs, and explore methodology specifications."
          />
        </div>

        <div className="panel">
          <ul className="divide-y divide-border">
            {[
              {
                href: "/funds",
                title: "Open Fund Discovery Catalog",
                note: "Search the canonical scheme universe by AMC, plan, option, and AMFI code.",
              },
              {
                href: "/funds/1",
                title: "Review the Institutional Analytical Profile",
                note: "Sixteen metrics across return, risk, ratio, market, and relative dimensions.",
              },
              {
                href: "/methodology",
                title: "Review Governance Specifications",
                note: "Methodology lifecycle, approval state, and frozen Phase 2H contract.",
              },
            ].map((item) => (
              <li key={item.href}>
                <Link
                  href={item.href}
                  className="flex flex-col gap-1 px-4 py-3 transition-colors hover:bg-surface-raised sm:flex-row sm:items-center sm:justify-between sm:gap-6"
                >
                  <span className="text-[13.5px] font-medium text-text-primary">{item.title}</span>
                  <span className="min-w-0 flex-1 text-[12px] leading-[1.5] text-text-tertiary sm:text-right">
                    {item.note}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </div>
      </section>
    </PageContainer>
  );
}
