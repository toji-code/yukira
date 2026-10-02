import React from 'react';
import { formatPercent, formatBasisPoints, formatNumber } from '@/lib/utils/formatters';

interface MetricComparisonProps {
  title: string;
  metricCode: string;
  period?: string;
  schemeValue: number | null | undefined;
  benchmarkValue: number | null | undefined;
  units?: string;
  schemeLabel?: string;
  benchmarkLabel?: string;
}

export function MetricComparison({
  title,
  metricCode,
  period = '1Y',
  schemeValue,
  benchmarkValue,
  units = 'PERCENTAGE',
  schemeLabel = 'Scheme',
  benchmarkLabel = 'Benchmark',
}: MetricComparisonProps) {
  function formatVal(v: number | null | undefined): string {
    if (v === null || v === undefined) return 'Not available';
    if (units === 'PERCENTAGE') return formatPercent(v);
    if (units === 'BPS') return formatBasisPoints(v);
    return formatNumber(v);
  }

  const delta =
    schemeValue !== null && schemeValue !== undefined && benchmarkValue !== null && benchmarkValue !== undefined
      ? schemeValue - benchmarkValue
      : null;

  return (
    <div className="panel">
      <div className="panel-header">
        <div className="flex min-w-0 items-baseline gap-2">
          <h4 className="truncate text-[15px] font-semibold text-text-primary">{title}</h4>
          <span className="mono-meta shrink-0">{metricCode}</span>
        </div>
        <span className="mono-meta shrink-0">{period}</span>
      </div>

      <dl className="grid grid-cols-1 divide-y divide-border sm:grid-cols-3 sm:divide-x sm:divide-y-0">
        <div className="px-3 py-3">
          <dt className="eyebrow">{schemeLabel}</dt>
          <dd className="data-value-md mt-1.5">
            {formatVal(schemeValue)}
          </dd>
        </div>

        <div className="px-3 py-3">
          <dt className="eyebrow">{benchmarkLabel}</dt>
          <dd className="data-value-md mt-1.5 text-secondary">
            {formatVal(benchmarkValue)}
          </dd>
        </div>

        <div className="px-3 py-3">
          <dt className="eyebrow">Difference</dt>
          <dd
            className={`data-value-md mt-1.5 ${
              delta === null ? 'data-unavailable' : delta < 0 ? 'text-risk' : 'text-text-primary'
            }`}
          >
            {delta !== null
              ? units === 'PERCENTAGE'
                ? formatBasisPoints(delta)
                : formatVal(delta)
              : 'Not available'}
          </dd>
        </div>
      </dl>

      <div className="mt-auto flex items-center justify-between border-t border-border px-3 py-2">
        <span className="mono-meta">Comparative evidence only</span>
      </div>
    </div>
  );
}
