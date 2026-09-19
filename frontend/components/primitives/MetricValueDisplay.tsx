import React from 'react';
import { MetricResult } from '@/types/calculation';
import {
  formatPercent,
  formatBasisPoints,
  formatRatio,
  formatNumber,
  formatCurrency,
} from '@/lib/utils/formatters';
import { MethodologyBadge } from '@/components/epistemic/MethodologyBadge';

interface MetricValueDisplayProps {
  result?: MetricResult;
  metricCode?: string;
  label: string;
  value?: number | null;
  units?: string;
  period?: string;
  isCandidate?: boolean;
  candidateConvention?: string;
  description?: string;
}

export function MetricValueDisplay({
  result,
  metricCode,
  label,
  value,
  units,
  period,
  isCandidate = false,
  candidateConvention,
  description,
}: MetricValueDisplayProps) {
  const finalValue = result ? result.numericValue : value;
  const finalUnits = result ? result.units : units || '';
  const finalPeriod = result ? result.periodType : period;
  const finalCode = result ? result.metricCode : metricCode;
  const status = result?.calculationStatus || 'CALCULATED';

  function renderFormattedValue(): string {
    if (finalValue === null || finalValue === undefined || status !== 'CALCULATED') {
      return '—';
    }

    switch (finalUnits.toUpperCase()) {
      case 'PERCENTAGE':
      case 'PERCENT':
      case '%':
        return formatPercent(finalValue);
      case 'BPS':
      case 'BASIS_POINTS':
        return formatBasisPoints(finalValue);
      case 'RATIO':
        return formatRatio(finalValue);
      case 'INR':
      case 'CURRENCY':
        return formatCurrency(finalValue);
      default:
        return formatNumber(finalValue);
    }
  }

  const isInsufficient = status === 'INSUFFICIENT_DATA';
  const isError = status === 'ERROR';

  return (
    <div className="rounded-lg border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-4 flex flex-col justify-between shadow-xs">
      <div>
        <div className="flex items-start justify-between gap-2 mb-1.5">
          <div className="flex items-center gap-1.5 flex-wrap">
            <span className="text-xs font-medium text-zinc-600 dark:text-zinc-400">
              {label}
            </span>
            {finalCode && (
              <span className="text-[10px] font-mono text-zinc-600 dark:text-zinc-400 bg-zinc-100 dark:bg-zinc-800 px-1 py-0.2 rounded">
                {finalCode}
              </span>
            )}
          </div>
          {finalPeriod && (
            <span className="text-[10px] font-mono font-medium text-zinc-600 dark:text-zinc-400 bg-zinc-100 dark:bg-zinc-800 px-1.5 py-0.5 rounded">
              {finalPeriod}
            </span>
          )}
        </div>

        <div className="flex items-baseline gap-2 my-2">
          <span
            className={`text-2xl font-bold tracking-tight font-mono ${
              finalValue === null || finalValue === undefined || status !== 'CALCULATED'
                ? 'text-zinc-400 dark:text-zinc-600'
                : 'text-zinc-900 dark:text-zinc-50'
            }`}
          >
            {renderFormattedValue()}
          </span>
          {finalUnits && finalValue !== null && finalValue !== undefined && status === 'CALCULATED' && (
            <span className="text-xs text-zinc-600 dark:text-zinc-400 font-mono">
              {finalUnits}
            </span>
          )}
        </div>

        {isInsufficient && (
          <p className="text-[11px] text-amber-600 dark:text-amber-400 mt-1 font-mono">
            Insufficient observations for authoritative calculation.
          </p>
        )}
        {isError && (
          <p className="text-[11px] text-rose-600 dark:text-rose-400 mt-1 font-mono">
            {result?.errorMessage || 'Calculation engine error.'}
          </p>
        )}
      </div>

      <div className="mt-3 pt-2.5 border-t border-zinc-100 dark:border-zinc-800 flex items-center justify-between text-[11px]">
        {isCandidate ? (
          <MethodologyBadge status="CANDIDATE" convention={candidateConvention} size="sm" />
        ) : (
          <span className="text-zinc-600 dark:text-zinc-400 text-[10px]">Deterministic Output</span>
        )}
        {description && (
          <span className="text-zinc-600 dark:text-zinc-400 text-[10px] truncate max-w-[140px]" title={description}>
            {description}
          </span>
        )}
      </div>
    </div>
  );
}
