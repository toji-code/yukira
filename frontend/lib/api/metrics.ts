import { CANONICAL_METRIC_EVIDENCE, MetricEvidenceDefinition } from '@/lib/analysis/metricEvidence';

const BACKEND_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

export interface AuthoritativeMetricDto {
  metricCode: string;
  canonicalName: string;
  analyticalDimension: string;
  categoryLabel: string;
  financialMeaning: string;
  mathematicalDefinition: string;
  formulaDisplay: string;
  requiredInputs: string[];
  returnConvention: string;
  annualizationConvention: string;
  measurementHorizon: string;
  benchmarkDependency: string;
  primaryBenchmarkCode: string;
  riskFreeDependency: string;
  riskFreeRateProxy: string;
  rankingDirection: 'HIGHER_IS_BETTER' | 'LOWER_IS_BETTER' | 'NEUTRAL';
  availabilityRequirements: string;
  missingDataBehavior: string;
  methodologyVersion: string;
  governanceStatus: string;
  assumptions: Record<string, string>;
  limitations: string;
  investigationQuestion: string;
  unit?: string;
  isDiagnostic?: boolean;
  parentMetricCode?: string | null;
  aliases?: string[];
}

/**
 * Fetches the complete authoritative metric registry from the backend.
 * Falls back to the canonical client-side registry if network is unavailable or during static generation.
 */
export async function getAuthoritativeMetricRegistry(): Promise<AuthoritativeMetricDto[]> {
  try {
    const res = await fetch(`${BACKEND_BASE}/api/v1/metrics/registry`, {
      method: 'GET',
      headers: { 'Accept': 'application/json' },
      cache: 'force-cache',
    });
    if (res.ok) {
      return await res.json();
    }
  } catch {
    // Fall back to client canonical metadata
  }

  return Object.values(CANONICAL_METRIC_EVIDENCE).map(m => ({
    metricCode: m.code,
    canonicalName: m.name,
    analyticalDimension: m.analyticalDimension || 'ANALYTICS',
    categoryLabel: m.categoryLabel,
    financialMeaning: m.whyItMatters,
    mathematicalDefinition: m.mathematicalDefinition || m.formulaSpecification,
    formulaDisplay: m.formulaSpecification,
    requiredInputs: m.requiredInputs || ['NAV_SERIES'],
    returnConvention: m.returnConvention || 'DISCRETE_ARITHMETIC',
    annualizationConvention: m.annualizationConvention || 'SQRT_252',
    measurementHorizon: m.measurementHorizon || '3Y_STANDARD',
    benchmarkDependency: m.benchmarkDependency || 'NONE',
    primaryBenchmarkCode: m.primaryBenchmarkCode || 'NONE',
    riskFreeDependency: m.riskFreeDependency || 'NONE',
    riskFreeRateProxy: m.riskFreeRateProxy || 'NONE',
    rankingDirection: m.rankingDirection || 'HIGHER_IS_BETTER',
    availabilityRequirements: m.availabilityRequirements || '>= 700 trading days',
    missingDataBehavior: m.missingDataBehavior || 'FAIL_CLOSED_RETURN_UNAVAILABLE',
    methodologyVersion: m.methodologyVersion || 'v1.0.0-candidate',
    governanceStatus: m.governanceStatus,
    assumptions: m.assumptions ? m.assumptions.reduce((acc, curr) => ({ ...acc, [curr.label]: curr.value }), {}) : {},
    limitations: m.limitations,
    investigationQuestion: m.investigationQuestion,
    unit: m.unit || deriveDefaultUnit(m.code, m.returnConvention),
    isDiagnostic: m.isDiagnostic || false,
    parentMetricCode: m.parentMetricCode || null,
    aliases: m.aliases || [],
  }));
}

function deriveDefaultUnit(code: string, returnConvention?: string): string {
  if (code === 'RSK-04' || returnConvention === 'CALENDAR_DAYS') return 'DAYS';
  if (returnConvention === 'PERCENTAGE_POINTS' || code === 'RSK-05' || code === 'CONC-03') return 'POINTS';
  if (code.startsWith('RAT-') || code.startsWith('MKT-01') || code.startsWith('MKT-02')) return 'RATIO';
  if (code === 'PRT-02' || code === 'PORTFOLIO_UNVALUED_POSITIONS') return 'COUNT';
  return 'PERCENTAGE';
}

/**
 * Fetches definition for a single metric code or alias.
 */
export async function getAuthoritativeMetric(codeOrAlias: string): Promise<AuthoritativeMetricDto | null> {
  if (!codeOrAlias) return null;
  const target = codeOrAlias.trim().toUpperCase();

  try {
    const res = await fetch(`${BACKEND_BASE}/api/v1/metrics/registry/${encodeURIComponent(target)}`, {
      method: 'GET',
      headers: { 'Accept': 'application/json' },
    });
    if (res.ok) {
      return await res.json();
    }
  } catch {
    // Fall back to client search
  }

  const all = await getAuthoritativeMetricRegistry();
  const found = all.find(m =>
    m.metricCode.toUpperCase() === target ||
    (m.aliases && m.aliases.some(a => a.toUpperCase() === target))
  );
  return found || null;
}

/**
 * Fetches only non-diagnostic quantitative methodology metrics.
 */
export async function getAnalyticalMetrics(): Promise<AuthoritativeMetricDto[]> {
  const all = await getAuthoritativeMetricRegistry();
  return all.filter(m => !m.isDiagnostic && m.analyticalDimension !== 'OPERATIONAL_DATA_QUALITY');
}

/**
 * Fetches operational and analytical diagnostics.
 */
export async function getOperationalDiagnostics(): Promise<AuthoritativeMetricDto[]> {
  const all = await getAuthoritativeMetricRegistry();
  return all.filter(m => m.isDiagnostic || m.analyticalDimension === 'OPERATIONAL_DATA_QUALITY');
}
