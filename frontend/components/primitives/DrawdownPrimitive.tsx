import React from 'react';
import { formatPercent, formatDate } from '@/lib/utils/formatters';

export interface DrawdownPoint {
  date: string;
  drawdown: number; // e.g. -0.15 for -15%
}

interface DrawdownPrimitiveProps {
  data: DrawdownPoint[];
  title?: string;
  height?: number;
}

export function DrawdownPrimitive({
  data,
  title = 'Historical Drawdown Depth',
  height = 160,
}: DrawdownPrimitiveProps) {
  if (!data || data.length === 0) {
    return (
      <div
        style={{ height }}
        className="flex items-center justify-center border border-dashed border-zinc-200 dark:border-zinc-800 rounded-lg text-xs text-zinc-600 dark:text-zinc-400 font-mono"
      >
        No drawdown observation series loaded
      </div>
    );
  }

  const values = data.map((d) => d.drawdown);
  const minVal = Math.min(0, ...values);
  const maxVal = 0; // Underwater chart ceiling is always 0%
  const range = maxVal - minVal === 0 ? 0.01 : maxVal - minVal;

  const width = 600;
  const padding = { top: 20, right: 20, bottom: 30, left: 55 };
  const innerWidth = width - padding.left - padding.right;
  const innerHeight = height - padding.top - padding.bottom;

  const points = data.map((d, i) => {
    const x = padding.left + (i / Math.max(1, data.length - 1)) * innerWidth;
    const y = padding.top + ((0 - d.drawdown) / range) * innerHeight;
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  });

  const areaD = `M ${padding.left},${padding.top} L ${points.join(' L ')} L ${padding.left + innerWidth},${padding.top} Z`;

  return (
    <div className="rounded-lg border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-4">
      <div className="flex items-center justify-between mb-2">
        <h4 className="text-xs font-semibold text-zinc-900 dark:text-zinc-100 uppercase tracking-wider font-mono">
          {title}
        </h4>
        <span className="text-[11px] font-mono text-zinc-600 dark:text-zinc-400">
          Max Drawdown: {formatPercent(minVal)}
        </span>
      </div>

      <div className="w-full overflow-hidden">
        <svg
          viewBox={`0 0 ${width} ${height}`}
          className="w-full h-auto overflow-visible"
          role="img"
          aria-label="Drawdown depth chart showing percentage distance from previous peak"
        >
          {/* Zero baseline */}
          <line
            x1={padding.left}
            y1={padding.top}
            x2={width - padding.right}
            y2={padding.top}
            stroke="currentColor"
            className="text-zinc-300 dark:text-zinc-700"
            strokeWidth="1.5"
          />

          {/* Depth Area */}
          <path
            d={areaD}
            fill="currentColor"
            className="text-rose-100/60 dark:text-rose-950/40"
          />

          {/* Under-water curve */}
          <path
            d={`M ${points.join(' L ')}`}
            fill="none"
            stroke="currentColor"
            strokeWidth="1.5"
            strokeLinecap="round"
            strokeLinejoin="round"
            className="text-rose-600 dark:text-rose-400"
          />

          {/* Y Axis Labels */}
          <text
            x={padding.left - 8}
            y={padding.top + 3}
            textAnchor="end"
            className="text-[9px] fill-zinc-600 dark:fill-zinc-400 font-mono"
          >
            0.0%
          </text>
          <text
            x={padding.left - 8}
            y={padding.top + innerHeight}
            textAnchor="end"
            className="text-[9px] fill-zinc-600 dark:fill-zinc-400 font-mono"
          >
            {formatPercent(minVal)}
          </text>

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
        <span>Drawdown ceiling: 0.00%</span>
        <span>Presentation primitive</span>
        <span>Peak-to-trough distance</span>
      </div>
    </div>
  );
}
