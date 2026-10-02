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
        className="state-well flex items-center justify-center font-mono text-[11px]"
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
  const padding = { top: 20, right: 20, bottom: 30, left: 58 };
  const innerWidth = width - padding.left - padding.right;
  const innerHeight = height - padding.top - padding.bottom;

  const points = data.map((d, i) => {
    const x = padding.left + (i / Math.max(1, data.length - 1)) * innerWidth;
    const y = padding.top + ((0 - d.drawdown) / range) * innerHeight;
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  });

  const areaD = `M ${padding.left},${padding.top} L ${points.join(' L ')} L ${padding.left + innerWidth},${padding.top} Z`;

  return (
    <div className="panel">
      <div className="panel-header">
        <h4 className="eyebrow truncate">{title}</h4>
        <span className="mono-meta shrink-0">Max drawdown {formatPercent(minVal)}</span>
      </div>

      <div className="panel-inset px-3 py-2">
        <svg
          viewBox={`0 0 ${width} ${height}`}
          className="h-auto w-full overflow-visible"
          role="img"
          aria-label="Drawdown depth chart showing percentage distance from previous peak"
        >
          {/* Zero baseline */}
          <line
            x1={padding.left}
            y1={padding.top}
            x2={width - padding.right}
            y2={padding.top}
            className="stroke-border-strong"
          />

          {/* Depth Area */}
          <path d={areaD} className="fill-series-neg/10" />

          {/* Under-water curve */}
          <path
            d={`M ${points.join(' L ')}`}
            fill="none"
            className="stroke-series-neg"
            strokeWidth="1.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          />

          {/* Y Axis Labels */}
          <text
            x={padding.left - 8}
            y={padding.top + 3}
            textAnchor="end"
            className="fill-text-muted font-mono text-[11px] tabular-nums"
          >
            0.0%
          </text>
          <text
            x={padding.left - 8}
            y={padding.top + innerHeight}
            textAnchor="end"
            className="fill-text-muted font-mono text-[11px] tabular-nums"
          >
            {formatPercent(minVal)}
          </text>

          {/* Direct series label */}
          <text
            x={width - padding.right}
            y={padding.top + 3}
            textAnchor="end"
            className="fill-series-neg font-mono text-[11px]"
          >
            Underwater
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
        <span>Ceiling 0.00%</span>
        <span>{data.length} obs · peak-to-trough</span>
        <span>{formatPercent(minVal)}</span>
      </div>
    </div>
  );
}
