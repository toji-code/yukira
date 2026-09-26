import React from 'react';
import { MetricResult } from '@/types/calculation';
import {
  formatPercent,
  formatBasisPoints,
  formatRatio,
  formatNumber,
  formatCurrency,
} from '@/lib/utils/formatters';

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
  label,
  value,
  units,
  period,
  description,
}: MetricValueDisplayProps) {
  const finalValue = result ? result.numericValue : value;
  const finalUnits = result ? result.units : units || '';
  const finalPeriod = result ? result.periodType : period;
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
    <div className="rounded-lg border border-border bg-surface p-4 flex flex-col justify-between shadow-xs">
      <div>
        <div className="flex items-start justify-between gap-2 mb-1.5">
          <span className="text-xs font-medium text-text-secondary">
            {label}
          </span>
          {finalPeriod && (
            <span className="text-[10px] font-mono font-medium text-text-muted bg-surface-elevated px-1.5 py-0.5 rounded border border-border">
              {finalPeriod}
            </span>
          )}
        </div>

        <div className="flex items-baseline gap-2 my-2">
          <span
            className={`text-2xl font-bold tracking-tight font-mono ${
              finalValue === null || finalValue === undefined || status !== 'CALCULATED'
                ? 'text-text-muted'
                : 'text-text-primary'
            }`}
          >
            {renderFormattedValue()}
          </span>
          {finalUnits && finalValue !== null && finalValue !== undefined && status === 'CALCULATED' && (
            <span className="text-xs text-text-muted font-mono">
              {finalUnits}
            </span>
          )}
        </div>

        {isInsufficient && (
          <p className="text-[11px] text-warning mt-1 font-mono">
            Insufficient observations for authoritative calculation.
          </p>
        )}
        {isError && (
          <p className="text-[11px] text-danger mt-1 font-mono">
            {result?.errorMessage || 'Calculation engine error.'}
          </p>
        )}
      </div>

      {description && (
        <div className="mt-3 pt-2.5 border-t border-border flex items-center justify-between text-[11px]">
          <span className="text-text-muted text-[10px] truncate" title={description}>
            {description}
          </span>
        </div>
      )}
    </div>
  );
}
