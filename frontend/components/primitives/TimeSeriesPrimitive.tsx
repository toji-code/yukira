import React from 'react';
import { formatNumber, formatDate } from '@/lib/utils/formatters';

export interface DataPoint {
  date: string;
  value: number;
}

interface TimeSeriesPrimitiveProps {
  data: DataPoint[];
  title?: string;
  height?: number;
  valueLabel?: string;
}

export function TimeSeriesPrimitive({
  data,
  title,
  height = 180,
  valueLabel = 'NAV',
}: TimeSeriesPrimitiveProps) {
  if (!data || data.length === 0) {
    return (
      <div
        style={{ height }}
        className="state-well flex items-center justify-center font-mono text-[11px]"
      >
        No historical observation series loaded
      </div>
    );
  }

  const values = data.map((d) => d.value);
  const minVal = Math.min(...values);
  const maxVal = Math.max(...values);
  const range = maxVal - minVal === 0 ? 1 : maxVal - minVal;

  const width = 600;
  const padding = { top: 20, right: 20, bottom: 30, left: 54 };
  const innerWidth = width - padding.left - padding.right;
  const innerHeight = height - padding.top - padding.bottom;

  const points = data.map((d, i) => {
    const x = padding.left + (i / Math.max(1, data.length - 1)) * innerWidth;
    const y = padding.top + innerHeight - ((d.value - minVal) / range) * innerHeight;
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  });

  const pathD = `M ${points.join(' L ')}`;
  const areaD = `M ${padding.left},${padding.top + innerHeight} L ${points.join(' L ')} L ${padding.left + innerWidth},${padding.top + innerHeight} Z`;

  return (
    <div className="panel">
      <div className="panel-header">
        <h4 className="eyebrow truncate">{title || 'Time Series'}</h4>
        <span className="mono-meta shrink-0">
          {data.length} observations
        </span>
      </div>

      <div className="panel-inset px-3 py-2">
        <svg
          viewBox={`0 0 ${width} ${height}`}
          className="h-auto w-full overflow-visible"
          role="img"
          aria-label={`${title || 'Time Series'} visualization containing ${data.length} observations`}
        >
          {/* Grid lines — inset well substrate only, no decorative axes */}
          <line
            x1={padding.left}
            y1={padding.top}
            x2={width - padding.right}
            y2={padding.top}
            className="stroke-border"
            strokeDasharray="3 3"
          />
          <line
            x1={padding.left}
            y1={padding.top + innerHeight / 2}
            x2={width - padding.right}
            y2={padding.top + innerHeight / 2}
            className="stroke-border"
            strokeDasharray="3 3"
          />
          <line
            x1={padding.left}
            y1={padding.top + innerHeight}
            x2={width - padding.right}
            y2={padding.top + innerHeight}
            className="stroke-border-strong"
          />

          {/* Y Axis Labels — mono data type, 11px floor */}
          <text
            x={padding.left - 8}
            y={padding.top + 4}
            textAnchor="end"
            className="fill-text-muted font-mono text-[11px] tabular-nums"
          >
            {formatNumber(maxVal)}
          </text>
          <text
            x={padding.left - 8}
            y={padding.top + innerHeight}
            textAnchor="end"
            className="fill-text-muted font-mono text-[11px] tabular-nums"
          >
            {formatNumber(minVal)}
          </text>

          {/* Area fill — series 1, low alpha so gridline remains legible */}
          <path d={areaD} className="fill-series-1/10" />

          {/* Line series */}
          <path
            d={pathD}
            fill="none"
            className="stroke-series-1"
            strokeWidth="1.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          />

          {/* Direct series label — replaces the footer legend */}
          <text
            x={width - padding.right}
            y={padding.top + 4}
            textAnchor="end"
            className="fill-series-1 font-mono text-[11px]"
          >
            {valueLabel}
          </text>

          {/* X Axis Start / End */}
          <text
            x={padding.left}
            y={height - 8}
            textAnchor="start"
            className="fill-text-muted font-mono text-[11px] tabular-nums"
          >
            {formatDate(data[0]?.date)}
          </text>
          <text
            x={width - padding.right}
            y={height - 8}
            textAnchor="end"
            className="fill-text-muted font-mono text-[11px] tabular-nums"
          >
            {formatDate(data[data.length - 1]?.date)}
          </text>
        </svg>
      </div>

      <div className="flex items-center justify-between border-t border-border px-3 py-2 font-mono text-[11px] text-muted tabular-nums">
        <span>Min {formatNumber(minVal)}</span>
        <span>
          {data.length} obs · {formatDate(data[0]?.date)} → {formatDate(data[data.length - 1]?.date)}
        </span>
        <span>Max {formatNumber(maxVal)}</span>
      </div>
    </div>
  );
}
