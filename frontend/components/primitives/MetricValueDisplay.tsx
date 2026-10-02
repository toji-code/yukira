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

  const isCalculated = finalValue !== null && finalValue !== undefined && status === 'CALCULATED';

  function renderFormattedValue(): string | null {
    if (!isCalculated || finalValue === null || finalValue === undefined) {
      return null;
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

  const formatted = renderFormattedValue();
  const isInsufficient = status === 'INSUFFICIENT_DATA';
  const isError = status === 'ERROR';

  return (
    <div className="metric-tile">
      <div className="flex items-start justify-between gap-3">
        <p className="text-[13px] font-medium text-text-secondary">{label}</p>
        {isInsufficient ? (
          <span className="status-badge state-candidate">
            <span className="status-dot" aria-hidden />
            Insufficient data
          </span>
        ) : isError ? (
          <span className="status-badge state-critical">
            <span className="status-dot" aria-hidden />
            Error
          </span>
        ) : null}
      </div>

      <div className="metric-rule my-3" role="presentation" />

      {isCalculated ? (
        <>
          <p className="data-value-lg">{formatted}</p>
          <p className="mono-meta mt-1">
            {[finalUnits, finalPeriod].filter(Boolean).join(' · ').toUpperCase()}
          </p>
        </>
      ) : (
        <>
          <p className="data-unavailable">Not available</p>
          <p className="mt-1 text-[13px] leading-[1.5] text-text-tertiary">
            {isError
              ? result?.errorMessage || 'Calculation engine error.'
              : 'Insufficient observations for authoritative calculation.'}
          </p>
        </>
      )}

      {description && (
        <p className="mt-3 border-t border-border pt-3 text-[13px] leading-[1.55] text-text-secondary">
          {description}
        </p>
      )}
    </div>
  );
}