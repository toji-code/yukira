/**
 * YUKIRA Quantitative Intelligence Platform
 * Phase F6: Investor Research Workspace Continuity and Portfolio Context
 *
 * Implements deterministic research-to-portfolio context resolution without
 * fabricating holding data, inferring suitability, or making investment recommendations.
 */

import { WatchlistResponse } from "@/lib/api/investor";
import { PortfolioSummaryDto, PortfolioHoldingDto, fetchPortfolioSummary } from "@/lib/api/portfolio";

export type PortfolioRelationshipType =
  | "EXACT_OPTION_MATCH"
  | "VARIANT_SCHEME_MATCH"
  | "NO_HOLDING_MATCH"
  | "PORTFOLIO_EMPTY"
  | "PORTFOLIO_UNAVAILABLE";

export interface ResearchedIdentity {
  schemeId: number;
  schemeOptionId?: number | null;
  schemeCode: string;
  schemeName: string;
  planType?: string | null;
  optionType?: string | null;
  amfiCode?: string | null;
  isin?: string | null;
}

export interface HeldHoldingSummary {
  id: number;
  schemeOptionId: number;
  fundName: string;
  amcName: string;
  planType: string;
  optionType: string;
  amfiCode: string;
  isin: string;
  units: number;
  availableValue: number | null;
  investedAmount: number | null;
  costBasisAmount: number | null;
  portfolioWeight: number | null;
  valuationFreshnessStatus?: string;
  navAsOfDate?: string | null;
  dataQualityState: string;
}

export interface PortfolioContextResolution {
  relationshipType: PortfolioRelationshipType;
  badgeLabel: string;
  badgeClass: string;
  description: string;
  isHeld: boolean;
  isExactMatch: boolean;
  isVariantMatch: boolean;
  researchedIdentity: ResearchedIdentity;
  heldHolding?: HeldHoldingSummary | null;
  variantDifferenceNotice?: string | null;
  portfolioSummaryMeta?: {
    totalHoldings: number;
    valuationCoverageState: string;
    valuationFreshnessState?: string;
    asOfDate?: string | null;
    totalAvailableValue?: number | null;
  } | null;
  navigationHref: string;
  navigationLabel: string;
  contextualGuidance: string;
}

// In-memory single-flight promise cache to avoid redundant portfolio requests
let cachedPortfolioPromise: Promise<PortfolioSummaryDto> | null = null;
let cachedPortfolioData: PortfolioSummaryDto | null = null;
let lastFetchTime = 0;
const CACHE_TTL_MS = 15000; // 15 seconds fresh window

export async function getCachedPortfolioSummary(forceRefresh = false): Promise<PortfolioSummaryDto | null> {
  const now = Date.now();
  if (!forceRefresh && cachedPortfolioData && now - lastFetchTime < CACHE_TTL_MS) {
    return cachedPortfolioData;
  }

  if (cachedPortfolioPromise && !forceRefresh) {
    return cachedPortfolioPromise;
  }

  try {
    cachedPortfolioPromise = fetchPortfolioSummary();
    const data = await cachedPortfolioPromise;
    cachedPortfolioData = data;
    lastFetchTime = Date.now();
    cachedPortfolioPromise = null;
    return data;
  } catch {
    cachedPortfolioPromise = null;
    return null;
  }
}

/**
 * Resolves the precise relationship between a researched fund option
 * and an investor's active portfolio holdings.
 */
export function resolvePortfolioContext(
  fund: WatchlistResponse | null,
  portfolio: PortfolioSummaryDto | null,
  isLoading = false
): PortfolioContextResolution {
  if (!fund) {
    return {
      relationshipType: "PORTFOLIO_UNAVAILABLE",
      badgeLabel: "PORTFOLIO NOT CONNECTED",
      badgeClass: "bg-surface-raised border-border text-text-muted",
      description: "Portfolio context is currently unavailable.",
      isHeld: false,
      isExactMatch: false,
      isVariantMatch: false,
      researchedIdentity: {
        schemeId: 0,
        schemeOptionId: null,
        schemeCode: "",
        schemeName: "",
        planType: "DIRECT",
        optionType: "GROWTH",
        amfiCode: null,
        isin: null,
      },
      heldHolding: null,
      portfolioSummaryMeta: null,
      navigationHref: "/portfolio",
      navigationLabel: "Connect Portfolio Workspace →",
      contextualGuidance: "",
    };
  }

  const researchedIdentity: ResearchedIdentity = {
    schemeId: fund.schemeId,
    schemeOptionId: fund.schemeOptionId ?? null,
    schemeCode: fund.schemeCode,
    schemeName: fund.schemeName,
    planType: fund.planType || "DIRECT",
    optionType: fund.optionType || "GROWTH",
    amfiCode: fund.amfiCode || null,
    isin: fund.isin || null,
  };

  // State 1: Portfolio is still loading or completely unavailable / not connected
  if (isLoading || (!portfolio && !fund.isHeldInPortfolio)) {
    return {
      relationshipType: "PORTFOLIO_UNAVAILABLE",
      badgeLabel: isLoading ? "CHECKING PORTFOLIO…" : "PORTFOLIO NOT CONNECTED",
      badgeClass: "bg-surface-raised border-border text-text-muted",
      description: isLoading
        ? "Correlating researched scheme option with existing portfolio holdings…"
        : "Portfolio context is currently unavailable or not connected. Fund research is evaluated independently.",
      isHeld: false,
      isExactMatch: false,
      isVariantMatch: false,
      researchedIdentity,
      heldHolding: null,
      portfolioSummaryMeta: null,
      navigationHref: "/portfolio",
      navigationLabel: "Connect Portfolio Workspace →",
      contextualGuidance:
        "Sign in or initialize portfolio tracking to evaluate live holding weights and allocation overlap against your research candidates.",
    };
  }

  const holdings = portfolio?.holdings || [];

  // State 2: Portfolio has 0 holdings
  if (portfolio && portfolio.totalHoldings === 0 && !fund.isHeldInPortfolio) {
    return {
      relationshipType: "PORTFOLIO_EMPTY",
      badgeLabel: "NO PORTFOLIO HOLDINGS",
      badgeClass: "bg-surface-raised border-border text-text-muted",
      description: "No mutual fund holdings are currently registered in your portfolio workspace.",
      isHeld: false,
      isExactMatch: false,
      isVariantMatch: false,
      researchedIdentity,
      heldHolding: null,
      portfolioSummaryMeta: {
        totalHoldings: 0,
        valuationCoverageState: portfolio.valuationCoverageState || "EMPTY",
        valuationFreshnessState: portfolio.valuationFreshnessState,
        asOfDate: portfolio.valuationAsOfDate,
        totalAvailableValue: portfolio.totalAvailableValue,
      },
      navigationHref: "/portfolio",
      navigationLabel: "Open Portfolio Tracking →",
      contextualGuidance:
        "Add your externally held scheme options to evaluate portfolio concentration, risk coverage, and holding-level overlap.",
    };
  }

  // State 3: Check for EXACT scheme-option match
  // Strict matching requires matching schemeOptionId or confirmed backend flag
  let exactMatchHolding: PortfolioHoldingDto | null = null;

  if (fund.schemeOptionId) {
    exactMatchHolding = holdings.find((h) => h.schemeOptionId === fund.schemeOptionId) || null;
  } else if (fund.isHeldInPortfolio && fund.portfolioHoldingId) {
    exactMatchHolding = holdings.find((h) => h.id === fund.portfolioHoldingId) || null;
  }

  if (exactMatchHolding || fund.isHeldInPortfolio) {
    const units = exactMatchHolding ? exactMatchHolding.units : (fund.holdingUnits ?? 0);
    const holdingSummary: HeldHoldingSummary = exactMatchHolding
      ? {
          id: exactMatchHolding.id,
          schemeOptionId: exactMatchHolding.schemeOptionId,
          fundName: exactMatchHolding.fundName,
          amcName: exactMatchHolding.amcName,
          planType: exactMatchHolding.planType,
          optionType: exactMatchHolding.optionType,
          amfiCode: exactMatchHolding.amfiCode,
          isin: exactMatchHolding.isin,
          units: exactMatchHolding.units,
          availableValue: exactMatchHolding.availableValue,
          investedAmount: exactMatchHolding.investedAmount,
          costBasisAmount: exactMatchHolding.costBasisAmount,
          portfolioWeight: exactMatchHolding.portfolioWeight ?? null,
          valuationFreshnessStatus: exactMatchHolding.valuationFreshnessStatus,
          navAsOfDate: exactMatchHolding.navAsOfDate,
          dataQualityState: exactMatchHolding.dataQualityState,
        }
      : {
          id: fund.portfolioHoldingId || -1,
          schemeOptionId: fund.schemeOptionId || fund.schemeId,
          fundName: fund.schemeName,
          amcName: fund.amcName || "AMC Verified",
          planType: fund.planType || "DIRECT",
          optionType: fund.optionType || "GROWTH",
          amfiCode: fund.amfiCode || "—",
          isin: fund.isin || "—",
          units,
          availableValue: null,
          investedAmount: null,
          costBasisAmount: null,
          portfolioWeight: null,
          valuationFreshnessStatus: "VALUATION_CURRENT",
          navAsOfDate: fund.latestNavDate,
          dataQualityState: "VALID",
        };

    const holdingOptionId = holdingSummary.schemeOptionId;

    return {
      relationshipType: "EXACT_OPTION_MATCH",
      badgeLabel: "● EXACT OPTION HELD IN PORTFOLIO",
      badgeClass: "bg-emerald-500/15 text-emerald-300 border-emerald-500/40",
      description: `Exact mutual fund option (#${holdingOptionId}) is held in your tracked portfolio (${units} units).`,
      isHeld: true,
      isExactMatch: true,
      isVariantMatch: false,
      researchedIdentity,
      heldHolding: holdingSummary,
      portfolioSummaryMeta: portfolio
        ? {
            totalHoldings: portfolio.totalHoldings,
            valuationCoverageState: portfolio.valuationCoverageState,
            valuationFreshnessState: portfolio.valuationFreshnessState,
            asOfDate: portfolio.valuationAsOfDate,
            totalAvailableValue: portfolio.totalAvailableValue,
          }
        : null,
      navigationHref: `/portfolio#holding-${holdingOptionId}`,
      navigationLabel: "Inspect Holding in Portfolio →",
      contextualGuidance:
        "You already hold this exact fund share class. Re-evaluating this brief serves as portfolio due diligence rather than new-position consideration.",
    };
  }

  // State 4: Check for VARIANT scheme match (same scheme, different option/plan)
  let variantHolding: PortfolioHoldingDto | null = null;

  if (fund.heldDifferentVariantNotice) {
    // Backend detected a different variant
    variantHolding = holdings.find((h) => {
      const hName = h.fundName.toLowerCase().replace(/[^a-z0-9]/g, "");
      const rName = fund.schemeName.toLowerCase().replace(/[^a-z0-9]/g, "");
      return hName.includes(rName) || rName.includes(hName) || h.schemeOptionId !== fund.schemeOptionId;
    }) || null;
  } else {
    // Client-side detection: same fund name or AMC with different plan or option type
    variantHolding =
      holdings.find((h) => {
        if (h.schemeOptionId === fund.schemeOptionId) return false;
        const hName = h.fundName.toLowerCase().replace(/ direct| regular| growth| idcw| dividend/g, "").trim();
        const rName = fund.schemeName.toLowerCase().replace(/ direct| regular| growth| idcw| dividend/g, "").trim();
        return hName === rName || (h.amcName === fund.amcName && hName.includes(rName));
      }) || null;
  }

  if (variantHolding || fund.heldDifferentVariantNotice) {
    const heldOptionId = variantHolding?.schemeOptionId || "different";
    const heldPlan = variantHolding?.planType || "REGULAR";
    const heldOptType = variantHolding?.optionType || "GROWTH";
    const notice =
      fund.heldDifferentVariantNotice ||
      `Held in portfolio under different variant: ${heldPlan} ${heldOptType} (Option #${heldOptionId})`;

    return {
      relationshipType: "VARIANT_SCHEME_MATCH",
      badgeLabel: "⚠️ DIFFERENT SCHEME OPTION HELD",
      badgeClass: "bg-amber-500/15 text-amber-300 border-amber-500/40",
      description: `You hold a different option of this scheme in your portfolio: ${heldPlan} • ${heldOptType} (Option #${heldOptionId}).`,
      isHeld: false,
      isExactMatch: false,
      isVariantMatch: true,
      researchedIdentity,
      variantDifferenceNotice: notice,
      heldHolding: variantHolding
        ? {
            id: variantHolding.id,
            schemeOptionId: variantHolding.schemeOptionId,
            fundName: variantHolding.fundName,
            amcName: variantHolding.amcName,
            planType: variantHolding.planType,
            optionType: variantHolding.optionType,
            amfiCode: variantHolding.amfiCode,
            isin: variantHolding.isin,
            units: variantHolding.units,
            availableValue: variantHolding.availableValue,
            investedAmount: variantHolding.investedAmount,
            costBasisAmount: variantHolding.costBasisAmount,
            portfolioWeight: variantHolding.portfolioWeight ?? null,
            valuationFreshnessStatus: variantHolding.valuationFreshnessStatus,
            navAsOfDate: variantHolding.navAsOfDate,
            dataQualityState: variantHolding.dataQualityState,
          }
        : null,
      portfolioSummaryMeta: portfolio
        ? {
            totalHoldings: portfolio.totalHoldings,
            valuationCoverageState: portfolio.valuationCoverageState,
            valuationFreshnessState: portfolio.valuationFreshnessState,
            asOfDate: portfolio.valuationAsOfDate,
            totalAvailableValue: portfolio.totalAvailableValue,
          }
        : null,
      navigationHref: `/portfolio#holding-${heldOptionId}`,
      navigationLabel: "Inspect Held Variant in Portfolio →",
      contextualGuidance:
        "Variant Distinction: The researched fund is Direct Plan, while your holding is Regular Plan (or different dividend/growth option). Direct plans do not pay intermediary distribution commissions, yielding lower expense drag and higher compounded net returns over time.",
    };
  }

  // State 5: Portfolio connected, but NO matching holding found
  const totalCount = portfolio ? portfolio.totalHoldings : 0;
  return {
    relationshipType: "NO_HOLDING_MATCH",
    badgeLabel: "NOT HELD IN PORTFOLIO",
    badgeClass: "bg-surface-raised border-border/80 text-text-tertiary",
    description: `No matching holding was found across your ${totalCount} registered portfolio positions.`,
    isHeld: false,
    isExactMatch: false,
    isVariantMatch: false,
    researchedIdentity,
    heldHolding: null,
    portfolioSummaryMeta: portfolio
      ? {
          totalHoldings: portfolio.totalHoldings,
          valuationCoverageState: portfolio.valuationCoverageState,
          valuationFreshnessState: portfolio.valuationFreshnessState,
          asOfDate: portfolio.valuationAsOfDate,
          totalAvailableValue: portfolio.totalAvailableValue,
        }
      : null,
    navigationHref: "/portfolio",
    navigationLabel: "View Portfolio Workspace →",
    contextualGuidance:
      "This fund is evaluated strictly as an uncommitted research candidate. Absence from your existing portfolio does not constitute a buy or sell recommendation.",
  };
}
