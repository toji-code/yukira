'use client';

import React, { useState } from 'react';

interface ProgressiveDisclosureProps {
  level1: React.ReactNode;
  level2: React.ReactNode;
  level3: React.ReactNode;
  defaultLevel?: 1 | 2 | 3;
}

/**
 * L1/L2/L3 analytical disclosure.
 *
 * Underline tabs, not pills. The active level's description stays visible above
 * the panel so the depth being shown is always self-explanatory.
 */
export function ProgressiveDisclosure({
  level1,
  level2,
  level3,
  defaultLevel = 1,
}: ProgressiveDisclosureProps) {
  const [activeLevel, setActiveLevel] = useState<1 | 2 | 3>(defaultLevel);

  const levels: Array<{ id: 1 | 2 | 3; label: string; description: string }> = [
    {
      id: 1,
      label: 'Summary',
      description: 'Key calculated outputs with data-quality and governance state attached.',
    },
    {
      id: 2,
      label: 'Evidence',
      description: 'Benchmark comparisons, relative performance, and historical window observations.',
    },
    {
      id: 3,
      label: 'Audit',
      description: 'Input snapshot SHA-256, software commit, bitemporal revision linkage and diagnostics.',
    },
  ];

  const active = levels.find((l) => l.id === activeLevel);

  return (
    <div className="w-full">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0">
          <p className="eyebrow">Progressive disclosure</p>
          <p className="mt-1 text-[13px] leading-[1.4] text-text-tertiary">{active?.description}</p>
        </div>

        <div role="tablist" aria-label="Analytical disclosure levels" className="tab-rail self-start sm:self-auto">
          {levels.map((lvl) => {
            const isSelected = activeLevel === lvl.id;
            return (
              <button
                key={lvl.id}
                role="tab"
                type="button"
                aria-selected={isSelected}
                aria-controls={`panel-level-${lvl.id}`}
                id={`tab-level-${lvl.id}`}
                onClick={() => setActiveLevel(lvl.id)}
                data-active={isSelected}
                className="tab"
              >
                <span className="tab-ordinal">L{lvl.id}</span>
                {lvl.label}
              </button>
            );
          })}
        </div>
      </div>

      <div
        id={`panel-level-${activeLevel}`}
        role="tabpanel"
        aria-labelledby={`tab-level-${activeLevel}`}
        tabIndex={0}
        className="mt-5 space-y-6 focus:outline-none"
      >
        {activeLevel === 1 && level1}
        {activeLevel === 2 && level2}
        {activeLevel === 3 && level3}
      </div>
    </div>
  );
}