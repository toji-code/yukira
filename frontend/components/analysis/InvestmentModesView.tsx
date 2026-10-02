"use client";

import { SchemeOption } from "@/types/domain";

/**
 * YUKIRA — Investment Mode Information (SIP / Lumpsum)
 * ---------------------------------------------------------------------------
 * Epistemic contract for this component:
 *
 * This is an INFORMATION surface, not a transaction or recommendation surface.
 * YUKIRA's verified dataset (scheme / scheme_plan / scheme_option master records
 * and the bitemporal NAV ledger) does NOT contain AMC scheme terms: minimum SIP
 * amount, SIP frequency options, minimum lump-sum amount, exit load, lock-in
 * period, or purchase cut-off times.
 *
 * Therefore this component:
 *   1. Reports every absent AMC term explicitly as "Not available" — never as a
 *      zero, a placeholder, an estimate, or an industry-default guess.
 *   2. Renders ONLY values that exist in YUKIRA's verified master records.
 *   3. Performs ZERO financial calculation. Share-class counts and taxonomy
 *      labels are deterministic string/enum mappings, not financial facts.
 *
 * See INVESTMENT_MODE_DATA_GAP for the auditable disclosure shown to investors.
 */

/** Canonical YUKIRA token for a field YUKIRA cannot source from verified data. */
export const NOT_AVAILABLE_TOKEN = "Not available" as const;

/**
 * A field YUKIRA explicitly does NOT hold. These are standard mutual fund scheme
 * terms. They are enumerated here so that every gap is disclosed by name rather
 * than silently omitted — an unnamed gap reads like an oversight, a named gap
 * reads like a known limitation.
 */
export interface UnavailableTerm {
  /** Canonical investor-facing label. */
  readonly label: string;
  /** Why YUKIRA cannot currently answer this. */
  readonly reason: string;
}

/** Investment route: how an investor makes the investment. */
export type InvestmentRoute = "SIP" | "LUMPSUM";

/** Determined solely by the persisted `option_type` enum. Never inferred from returns. */
export type ShareClassTreatment =
  | "ACCUMULATION"
  | "DISTRIBUTION_PAYOUT"
  | "DISTRIBUTION_REINVESTMENT"
  | "UNCLASSIFIED";

/**
 * Scheme terms that determine HOW an investment may be made.
 * All are currently absent from YUKIRA's verified dataset.
 */
export const INVESTMENT_ROUTE_TERMS: Record<InvestmentRoute, readonly UnavailableTerm[]> = {
  SIP: [
    {
      label: "Minimum SIP Amount",
      reason: "Set by the AMC in the scheme terms document; not held in YUKIRA's master records.",
    },
    {
      label: "Permitted SIP Frequencies",
      reason: "Weekly / monthly / quarterly instalment options are AMC-published; not ingested.",
    },
    {
      label: "Instalment Tenure & Minimum Instalments",
      reason: "Perpetual vs. fixed-tenure SIP mandates are AMC-published; not ingested.",
    },
    {
      label: "Step-up / Step-down Facility",
      reason: "Annual escalation provisions are AMC-published; not ingested.",
    },
    {
      label: "Instalment Debit Date & Cut-off Time",
      reason: "Requires the AMC mandate plus the exchange trading calendar; not ingested.",
    },
    {
      label: "SIP Exit Load / Deduction",
      reason: "Charge schedules on early redemption are AMC-published; not ingested.",
    },
  ],
  LUMPSUM: [
    {
      label: "Minimum Lump-sum Amount",
      reason: "Set by the AMC in the scheme terms document; not held in YUKIRA's master records.",
    },
    {
      label: "Purchase Cut-off Time & Applicable NAV",
      reason: "Requires the AMC mandate plus exchange cut-off rules; not ingested.",
    },
    {
      label: "Exit Load / Redemption Charge",
      reason: "Charge schedules on early redemption are AMC-published; not ingested.",
    },
    {
      label: "Lock-in Period",
      reason: "Any statutory or scheme-imposed lock-in is AMC-published; not ingested.",
    },
  ],
};

/**
 * Auditable data-gap disclosure rendered above the route panels.
 * Wording is deliberately non-promissory: it states what is missing, never
 * what an investor should or should not do.
 */
export const INVESTMENT_MODE_DATA_GAP = {
  headline: "Scheme terms are not in YUKIRA's dataset yet",
  body:
    "YUKIRA has ingested the official AMFI registry (scheme, plan, option, ISIN, AMFI code) and the " +
    "point-in-time NAV ledger. It has not yet ingested the AMC's scheme terms document, which is the " +
    "authoritative source for minimum amounts, permitted frequencies, and charges. Every such field " +
    "below is therefore reported as \"Not available\" rather than estimated. Confirm the actual terms " +
    "with the fund house (AMC) or your distributor before committing capital.",
} as const;

/**
 * Deterministic taxonomy label for `scheme_plan.plan_type`.
 * Pure mapping of an existing database enum to investor language.
 */
export function describePlanType(raw: string | null | undefined): string {
  switch ((raw ?? "").toUpperCase()) {
    case "DIRECT":
      return "Direct Plan";
    case "REGULAR":
      return "Regular Plan";
    default:
      return raw && raw.trim().length > 0 ? raw : NOT_AVAILABLE_TOKEN;
  }
}

/**
 * Deterministic taxonomy label for `scheme_option.option_type`.
 * Pure mapping of an existing database enum to investor language.
 */
export function describeOptionType(raw: string | null | undefined): string {
  switch ((raw ?? "").toUpperCase()) {
    case "GROWTH":
      return "Growth Option (Accumulation)";
    case "IDCW_PAYOUT":
      return "IDCW Payout Option (Dividend)";
    case "IDCW_REINVESTMENT":
      return "IDCW Reinvestment Option (Dividend)";
    default:
      return raw && raw.trim().length > 0 ? raw : NOT_AVAILABLE_TOKEN;
  }
}

/**
 * Classifies a share class strictly from the persisted `option_type` enum.
 * This describes how the share class ACCOUNTS FOR RETURNS, nothing more.
 * It never implies that any investment route is or is not permitted.
 */
export function classifyShareClassTreatment(
  raw: string | null | undefined
): ShareClassTreatment {
  switch ((raw ?? "").toUpperCase()) {
    case "GROWTH":
      return "ACCUMULATION";
    case "IDCW_PAYOUT":
      return "DISTRIBUTION_PAYOUT";
    case "IDCW_REINVESTMENT":
      return "DISTRIBUTION_REINVESTMENT";
    default:
      return "UNCLASSIFIED";
  }
}

/** Human-readable explanation of each share-class treatment. */
export const SHARE_CLASS_TREATMENT_GUIDANCE: Record<ShareClassTreatment, string> = {
  ACCUMULATION:
    "Returns stay inside the scheme and raise the NAV. The investor receives no periodic payout from this share class.",
  DISTRIBUTION_PAYOUT:
    "Returns are distributed to the investor as a periodic payout, which reduces the NAV by the distributed amount on the record date.",
  DISTRIBUTION_REINVESTMENT:
    "Returns are distributed and immediately reinvested into additional units of the same share class.",
  UNCLASSIFIED:
    "YUKIRA holds no recognisable option type for this share class, so it cannot describe how returns are accounted for.",
};

/** Deterministic counts over persisted enums. Not a financial calculation. */
export interface ShareClassCoverage {
  readonly total: number;
  readonly accumulation: number;
  readonly distributionPayout: number;
  readonly distributionReinvestment: number;
  readonly unclassified: number;
  readonly registeredIdentifiers: number;
}

export function summarizeShareClassCoverage(
  options: readonly SchemeOption[]
): ShareClassCoverage {
  let accumulation = 0;
  let distributionPayout = 0;
  let distributionReinvestment = 0;
  let unclassified = 0;
  let registeredIdentifiers = 0;

  for (const option of options) {
    switch (classifyShareClassTreatment(option.optionType)) {
      case "ACCUMULATION":
        accumulation += 1;
        break;
      case "DISTRIBUTION_PAYOUT":
        distributionPayout += 1;
        break;
      case "DISTRIBUTION_REINVESTMENT":
        distributionReinvestment += 1;
        break;
      default:
        unclassified += 1;
    }

    if ((option.isin ?? "").trim().length > 0 || (option.amfiCode ?? "").trim().length > 0) {
      registeredIdentifiers += 1;
    }
  }

  return {
    total: options.length,
    accumulation,
    distributionPayout,
    distributionReinvestment,
    unclassified,
    registeredIdentifiers,
  };
}

/** Master-record status is shown verbatim; it is never translated into "open for purchase". */
export function describeRegistryStatus(raw: string | null | undefined): string {
  return raw && raw.trim().length > 0 ? raw : NOT_AVAILABLE_TOKEN;
}

interface InvestmentModesViewProps {
  options: readonly SchemeOption[];
  schemeCode: string;
}

/** Renders a single disclosed data gap as an explicit "Not available" row. */
function UnavailableTermRow({ term }: { term: UnavailableTerm }) {
  return (
    <div className="flex flex-col gap-1 border-b border-border-subtle py-3 last:border-b-0 sm:flex-row sm:items-start sm:gap-3">
      <dt className="def-label sm:w-44 sm:shrink-0 sm:self-start">{term.label}</dt>
      <dd className="flex flex-col gap-1.5 sm:flex-row sm:items-start sm:gap-3">
        <span className="status-badge state-unavailable shrink-0">{NOT_AVAILABLE_TOKEN}</span>
        <span className="mono-meta font-sans leading-[1.5]">{term.reason}</span>
      </dd>
    </div>
  );
}

function RoutePanel({
  route,
  title,
  subtitle,
}: {
  route: InvestmentRoute;
  title: string;
  subtitle: string;
}) {
  const terms = INVESTMENT_ROUTE_TERMS[route];

  return (
    <div className="panel">
      <div className="panel-header flex-wrap border-b border-border pb-3">
        <p className="eyebrow text-accent">{title}</p>
        <span className="status-badge state-unavailable shrink-0">
          {terms.length} terms &bull; none verified
        </span>
      </div>

      <p className="mono-meta border-b border-border-subtle py-2.5 font-sans leading-[1.5]">
        {subtitle}
      </p>

      <dl className="py-1">
        {terms.map((term) => (
          <UnavailableTermRow key={term.label} term={term} />
        ))}
      </dl>
    </div>
  );
}

export function InvestmentModesView({ options, schemeCode }: InvestmentModesViewProps) {
  const coverage = summarizeShareClassCoverage(options);

  return (
    <div className="space-y-5">
      {/* DATA GAP DISCLOSURE */}
      <section className="panel p-4 md:p-5" aria-label="Data coverage notice">
        <div className="flex items-start gap-3">
          <span
            className="status-badge state-critical mt-0.5 shrink-0"
            aria-hidden="true"
          >
            !
          </span>
          <div className="min-w-0">
            <h3 className="text-[14px] font-semibold tracking-[-0.01em] text-text-primary">
              {INVESTMENT_MODE_DATA_GAP.headline}
            </h3>
            <p className="mono-meta mt-1.5 font-sans leading-[1.55]">
              {INVESTMENT_MODE_DATA_GAP.body}
            </p>
          </div>
        </div>
      </section>

      {/* ROUTE PANELS: SIP vs LUMPSUM */}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <RoutePanel
          route="SIP"
          title="SIP — Systematic Investment Plan"
          subtitle="A fixed amount invested on a recurring schedule, so market timing is spread across multiple dates rather than decided on one."
        />
        <RoutePanel
          route="LUMPSUM"
          title="Lumpsum — One-Time Investment"
          subtitle="The entire amount is invested on a single date, so the outcome depends entirely on the market level on that one date."
        />
      </div>

      {/* REGISTERED SHARE CLASSES — REAL VERIFIED MASTER-RECORD DATA */}
      <section className="panel p-4 md:p-5" aria-label="Registered share classes">
        <div className="panel-header border-b border-border pb-3">
          <div className="min-w-0">
            <p className="eyebrow text-accent">Share Classes Available for Investment Routes</p>
            <p className="mono-meta mt-1.5 font-sans leading-[1.5]">
              Verified AMFI master records for{" "}
              <strong className="font-mono text-text-primary">{schemeCode}</strong>. An investment
              route must be executed against one of these share classes.
            </p>
          </div>
          <span className="status-badge state-approved shrink-0">
            {coverage.total} registered &bull; {coverage.registeredIdentifiers} with ISIN
          </span>
        </div>

        {options.length === 0 ? (
          <div className="state-well mt-4">
            <p className="mono-meta">
              No share classes are registered for this scheme in YUKIRA&apos;s master records.
            </p>
          </div>
        ) : (
          <>
            <div className="scroll-region mt-4">
              <table className="data-table">
                <thead>
                  <tr>
                    <th scope="col">Share Class</th>
                    <th scope="col">Plan Type</th>
                    <th scope="col">Option Type</th>
                    <th scope="col">AMFI Code</th>
                    <th scope="col">ISIN</th>
                    <th scope="col">Registry Status</th>
                    <th scope="col">Route Eligibility</th>
                  </tr>
                </thead>
                <tbody>
                  {options.map((option) => (
                    <tr key={option.id}>
                      <td className="num key text-left whitespace-nowrap text-accent">
                        Option #{option.id}
                      </td>
                      <td className="num key text-left whitespace-nowrap">
                        {describePlanType(option.plan?.planType)}
                      </td>
                      <td className="num text-left">
                        {describeOptionType(option.optionType)}
                      </td>
                      <td className="num text-left">
                        {option.amfiCode ?? NOT_AVAILABLE_TOKEN}
                      </td>
                      <td className="num text-left">{option.isin ?? NOT_AVAILABLE_TOKEN}</td>
                      <td>
                        <span className="status-badge state-unavailable">
                          {describeRegistryStatus(option.status)}
                        </span>
                      </td>
                      <td>
                        <span className="status-badge state-unavailable whitespace-nowrap">
                          SIP &amp; Lumpsum {NOT_AVAILABLE_TOKEN}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <p className="mono-meta mt-3 border-t border-border-subtle pt-3 font-sans leading-[1.55]">
              Registry Status is the AMFI master-record state only. It is{" "}
              <strong className="text-text-primary">not</strong> a confirmation that the AMC
              currently accepts new money in this share class. AMFI Code and ISIN are the identifiers
              a distributor requires in order to accept an order against this share class.
            </p>
          </>
        )}
      </section>

      {/* SHARE CLASS TAXONOMY — DEFINITIONAL, NOT A MEASUREMENT */}
      <section className="panel p-4 md:p-5" aria-label="Share class treatment reference">
        <p className="eyebrow text-accent">What Each Share Class Does With Returns</p>
        <p className="mono-meta mt-1.5 font-sans leading-[1.55]">
          Definitions of the share-class types YUKIRA holds for this scheme. These describe accounting
          treatment only. They are not statements about which investment route a share class accepts.
        </p>

        <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {(
            [
              ["ACCUMULATION", coverage.accumulation],
              ["DISTRIBUTION_PAYOUT", coverage.distributionPayout],
              ["DISTRIBUTION_REINVESTMENT", coverage.distributionReinvestment],
              ["UNCLASSIFIED", coverage.unclassified],
            ] as const
          ).map(([treatment, count]) => (
            <div
              key={treatment}
              className={`metric-tile ${
                count === 0 ? "border-dashed bg-surface-inset" : ""
              }`}
            >
              <div className="flex items-baseline justify-between gap-2">
                <span className="def-label mb-0">{treatment.replace(/_/g, " ")}</span>
                <span className={count > 0 ? "data-value-sm text-accent" : "data-unavailable"}>
                  {count}
                </span>
              </div>
              <div className="metric-rule my-3" aria-hidden />
              <p className="mono-meta font-sans leading-[1.5]">
                {SHARE_CLASS_TREATMENT_GUIDANCE[treatment]}
              </p>
            </div>
          ))}
        </div>
      </section>

      {/* NON-SUITABILITY GUARDRAIL */}
      <section className="panel-inset p-4 md:p-5" aria-label="Scope and suitability notice">
        <p className="eyebrow text-accent">Availability Is Not Suitability</p>
        <div className="prose-measure mt-2 space-y-2 text-[13px] leading-[1.55] text-text-secondary">
          <p>
            Whether an investment can be made as a SIP or as a lump-sum describes the{" "}
            <em>mechanics</em> of investing, not whether it is appropriate. YUKIRA publishes no
            suitability assessment, no target return, no expected outcome, and no buy / hold / avoid
            call of any kind.
          </p>
          <p>
            Minimum amounts, permitted instalment frequencies, and exit charges are set by the fund
            house (AMC) and by your distributor. They are currently{" "}
            <strong className="text-text-primary">{NOT_AVAILABLE_TOKEN}</strong> in YUKIRA and must be
            read from the AMC&apos;s official Key Information Memorandum and scheme terms, or from the
            distributor that will execute the order.
          </p>
          <p className="text-text-tertiary">
            YUKIRA does not execute investments, hold money, or connect to any broker, registrar, or
            transaction platform.
          </p>
        </div>
      </section>
    </div>
  );
}