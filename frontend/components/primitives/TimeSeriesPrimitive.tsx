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
        className="flex items-center justify-center border border-dashed border-zinc-200 dark:border-zinc-800 rounded-lg text-xs text-zinc-600 dark:text-zinc-400 font-mono"
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
  const padding = { top: 20, right: 20, bottom: 30, left: 50 };
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
    <div className="rounded-lg border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-4">
      {title && (
        <div className="flex items-center justify-between mb-2">
          <h4 className="text-xs font-semibold text-zinc-900 dark:text-zinc-100 uppercase tracking-wider font-mono">
            {title}
          </h4>
          <span className="text-[11px] font-mono text-zinc-600 dark:text-zinc-400">
            {data.length} observations
          </span>
        </div>
      )}

      <div className="w-full overflow-hidden">
        <svg
          viewBox={`0 0 ${width} ${height}`}
          className="w-full h-auto overflow-visible"
          role="img"
          aria-label={`${title || 'Time Series'} visualization containing ${data.length} observations`}
        >
          {/* Grid lines */}
          <line
            x1={padding.left}
            y1={padding.top}
            x2={width - padding.right}
            y2={padding.top}
            stroke="currentColor"
            className="text-zinc-100 dark:text-zinc-800"
            strokeDasharray="3 3"
          />
          <line
            x1={padding.left}
            y1={padding.top + innerHeight / 2}
            x2={width - padding.right}
            y2={padding.top + innerHeight / 2}
            stroke="currentColor"
            className="text-zinc-100 dark:text-zinc-800"
            strokeDasharray="3 3"
          />
          <line
            x1={padding.left}
            y1={padding.top + innerHeight}
            x2={width - padding.right}
            y2={padding.top + innerHeight}
            stroke="currentColor"
            className="text-zinc-200 dark:text-zinc-800"
          />

          {/* Y Axis Labels */}
          <text
            x={padding.left - 8}
            y={padding.top + 4}
            textAnchor="end"
            className="text-[9px] fill-zinc-600 dark:fill-zinc-400 font-mono"
          >
            {formatNumber(maxVal)}
          </text>
          <text
            x={padding.left - 8}
            y={padding.top + innerHeight}
            textAnchor="end"
            className="text-[9px] fill-zinc-600 dark:fill-zinc-400 font-mono"
          >
            {formatNumber(minVal)}
          </text>

          {/* Area fill */}
          <path d={areaD} fill="currentColor" className="text-zinc-100/60 dark:text-zinc-800/30" />

          {/* Line series */}
          <path
            d={pathD}
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            className="text-zinc-900 dark:text-zinc-100"
          />

          {/* X Axis Start / End */}
          <text
            x={padding.left}
            y={height - 8}
            textAnchor="start"
            className="text-[9px] fill-zinc-600 dark:fill-zinc-400 font-mono"
          >
            {formatDate(data[0]?.date)}
          </text>
          <text
            x={width - padding.right}
            y={height - 8}
            textAnchor="end"
            className="text-[9px] fill-zinc-600 dark:fill-zinc-400 font-mono"
          >
            {formatDate(data[data.length - 1]?.date)}
          </text>
        </svg>
      </div>

      <div className="flex justify-between items-center text-[10px] text-zinc-600 dark:text-zinc-400 font-mono mt-1 pt-1.5 border-t border-zinc-100 dark:border-zinc-800">
        <span>Min: {formatNumber(minVal)}</span>
        <span>Unit: {valueLabel}</span>
        <span>Max: {formatNumber(maxVal)}</span>
      </div>
    </div>
  );
}
