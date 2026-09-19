/**
 * YUKIRA Financial & Metric Formatting Utilities.
 *
 * CRITICAL RULE:
 * Missing/null/undefined values MUST NEVER be formatted as 0 or 0.00%.
 * They must always evaluate to an em dash ("—") with unverified/missing semantics.
 */

export function formatPercent(
  value: number | null | undefined,
  decimals = 2,
  showSign = false
): string {
  if (value === null || value === undefined || isNaN(value)) {
    return '—';
  }
  const pct = value * 100;
  const sign = showSign && pct > 0 ? '+' : '';
  return `${sign}${pct.toFixed(decimals)}%`;
}

export const formatPercentage = formatPercent;

export function formatBasisPoints(value: number | null | undefined): string {
  if (value === null || value === undefined || isNaN(value)) {
    return '—';
  }
  const bps = Math.round(value * 10000);
  const sign = bps > 0 ? '+' : '';
  return `${sign}${bps} bps`;
}

export function formatRatio(value: number | null | undefined, decimals = 2): string {
  if (value === null || value === undefined || isNaN(value)) {
    return '—';
  }
  return `${value.toFixed(decimals)}x`;
}

export function formatNumber(value: number | null | undefined, decimals = 2): string {
  if (value === null || value === undefined || isNaN(value)) {
    return '—';
  }
  return value.toLocaleString('en-IN', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  });
}

export function formatCurrency(value: number | null | undefined, currency = 'INR'): string {
  if (value === null || value === undefined || isNaN(value)) {
    return '—';
  }
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency,
    maximumFractionDigits: 2,
  }).format(value);
}

export function formatDate(dateStr: string | null | undefined): string {
  if (!dateStr) return '—';
  try {
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return dateStr;
    return d.toLocaleDateString('en-IN', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
  } catch {
    return dateStr;
  }
}

export function formatDateTime(isoStr: string | null | undefined): string {
  if (!isoStr) return '—';
  try {
    const d = new Date(isoStr);
    if (isNaN(d.getTime())) return isoStr;
    return d.toLocaleString('en-IN', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      timeZoneName: 'short',
    });
  } catch {
    return isoStr;
  }
}

export function parseDiagnostics(
  input: { diagnostics?: string | Record<string, unknown> | null } | string | Record<string, unknown> | null | undefined
): Record<string, unknown> | null {
  if (!input) return null;

  let target: unknown = input;
  if (typeof input === 'object' && 'diagnostics' in input) {
    target = input.diagnostics;
  }

  if (!target) return null;
  if (typeof target === 'object') return target as Record<string, unknown>;
  if (typeof target !== 'string') return null;

  const trimmed = target.trim();
  if (!trimmed) return null;

  try {
    const parsed = JSON.parse(trimmed);
    if (typeof parsed === 'object' && parsed !== null) {
      return parsed as Record<string, unknown>;
    }
    return { value: parsed };
  } catch {
    return { malformed: true, raw: target };
  }
}
