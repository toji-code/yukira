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
    if (v === null || v === undefined) return '—';
    if (units === 'PERCENTAGE') return formatPercent(v);
    if (units === 'BPS') return formatBasisPoints(v);
    return formatNumber(v);
  }

  const delta =
    schemeValue !== null && schemeValue !== undefined && benchmarkValue !== null && benchmarkValue !== undefined
      ? schemeValue - benchmarkValue
      : null;

  return (
    <div className="rounded-lg border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-4">
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          <h4 className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">{title}</h4>
          <span className="text-[10px] font-mono bg-zinc-100 dark:bg-zinc-800 text-zinc-500 px-1 rounded">
            {metricCode}
          </span>
        </div>
        <span className="text-[10px] font-mono bg-zinc-100 dark:bg-zinc-800 text-zinc-500 px-1.5 py-0.5 rounded">
          {period}
        </span>
      </div>

      <div className="grid grid-cols-3 gap-2 text-center py-2 bg-zinc-50 dark:bg-zinc-950/50 rounded-md border border-zinc-100 dark:border-zinc-800/80">
        <div>
          <span className="text-[10px] uppercase text-zinc-500 font-mono block mb-1">
            {schemeLabel}
          </span>
          <span className="text-base font-bold font-mono text-zinc-900 dark:text-zinc-100">
            {formatVal(schemeValue)}
          </span>
        </div>

        <div>
          <span className="text-[10px] uppercase text-zinc-500 font-mono block mb-1">
            {benchmarkLabel}
          </span>
          <span className="text-base font-bold font-mono text-zinc-900 dark:text-zinc-100">
            {formatVal(benchmarkValue)}
          </span>
        </div>

        <div>
          <span className="text-[10px] uppercase text-zinc-500 font-mono block mb-1">
            Difference
          </span>
          <span
            className={`text-base font-bold font-mono ${
              delta === null
                ? 'text-zinc-400 dark:text-zinc-600'
                : delta >= 0
                ? 'text-zinc-900 dark:text-zinc-100'
                : 'text-zinc-700 dark:text-zinc-300'
            }`}
          >
            {delta !== null
              ? units === 'PERCENTAGE'
                ? formatBasisPoints(delta)
                : formatVal(delta)
              : '—'}
          </span>
        </div>
      </div>

      <div className="mt-2 text-right">
        <span className="text-[10px] text-zinc-400 font-mono">Comparative evidence only</span>
      </div>
    </div>
  );
}
