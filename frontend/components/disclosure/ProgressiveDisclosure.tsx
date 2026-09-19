'use client';

import React, { useState } from 'react';

interface ProgressiveDisclosureProps {
  level1: React.ReactNode;
  level2: React.ReactNode;
  level3: React.ReactNode;
  defaultLevel?: 1 | 2 | 3;
}

export function ProgressiveDisclosure({
  level1,
  level2,
  level3,
  defaultLevel = 1,
}: ProgressiveDisclosureProps) {
  const [activeLevel, setActiveLevel] = useState<1 | 2 | 3>(defaultLevel);

  const levels: Array<{ id: 1 | 2 | 3; label: string; badge: string; description: string }> = [
    {
      id: 1,
      label: 'Level 1: Summary Context',
      badge: 'Investor Context',
      description: 'Key calculated outputs with data-quality and candidate methodology tags.',
    },
    {
      id: 2,
      label: 'Level 2: Evidence & Reasoning',
      badge: 'Evidence Base',
      description: 'Benchmark comparisons, relative performance, and historical window observations.',
    },
    {
      id: 3,
      label: 'Level 3: Institutional Audit',
      badge: 'Deep Provenance',
      description: 'Input snapshot SHA-256, software commit SHA, bitemporal revision linkages & diagnostics.',
    },
  ];

  return (
    <div className="w-full space-y-6">
      <div className="border-b border-zinc-200 dark:border-zinc-800">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pb-2">
          <div>
            <span className="text-xs uppercase tracking-wider font-mono text-zinc-600 dark:text-zinc-400">
              Progressive Disclosure Architecture
            </span>
            <p className="text-xs text-zinc-600 dark:text-zinc-400">
              {levels.find((l) => l.id === activeLevel)?.description}
            </p>
          </div>

          <div
            role="tablist"
            aria-label="Analytical disclosure levels"
            className="flex rounded-lg bg-zinc-100 dark:bg-zinc-900 p-1 border border-zinc-200 dark:border-zinc-800 self-start sm:self-auto"
          >
            {levels.map((lvl) => {
              const isSelected = activeLevel === lvl.id;
              return (
                <button
                  key={lvl.id}
                  role="tab"
                  aria-selected={isSelected}
                  aria-controls={`panel-level-${lvl.id}`}
                  id={`tab-level-${lvl.id}`}
                  onClick={() => setActiveLevel(lvl.id)}
                  className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium font-mono transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 ${
                    isSelected
                      ? 'bg-white dark:bg-zinc-800 text-zinc-900 dark:text-zinc-50 shadow-xs'
                      : 'text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-zinc-200'
                  }`}
                >
                  <span className="w-1.5 h-1.5 rounded-full bg-current opacity-60" />
                  <span>L{lvl.id}</span>
                  <span className="hidden md:inline text-[11px] opacity-70">
                    ({lvl.badge})
                  </span>
                </button>
              );
            })}
          </div>
        </div>
      </div>

      <div
        id={`panel-level-${activeLevel}`}
        role="tabpanel"
        aria-labelledby={`tab-level-${activeLevel}`}
        className="focus:outline-none"
      >
        {activeLevel === 1 && <div className="space-y-6">{level1}</div>}
        {activeLevel === 2 && <div className="space-y-6">{level2}</div>}
        {activeLevel === 3 && <div className="space-y-6">{level3}</div>}
      </div>
    </div>
  );
}
