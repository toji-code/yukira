'use client';

import React from 'react';

interface EpistemicBannerProps {
  observation: string | React.ReactNode;
  interpretation: string | React.ReactNode;
  limitation: string | React.ReactNode;
  governanceTag?: string;
}

export function EpistemicBanner({
  observation,
  interpretation,
  limitation,
  governanceTag = 'CANDIDATE METHODOLOGY — NOT VALIDATED FOR PRODUCTION',
}: EpistemicBannerProps) {
  return (
    <section
      className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-5 backdrop-blur-sm space-y-4"
      aria-label="Epistemic Tri-Partite Distinction"
    >
      <div className="flex flex-wrap items-center justify-between gap-2 pb-3 border-b border-zinc-800/80">
        <div className="flex items-center gap-2">
          <span className="flex h-2 w-2 rounded-full bg-cyan-400 animate-pulse" />
          <h3 className="font-mono text-xs font-bold uppercase tracking-wider text-zinc-200">
            Epistemic Distinction: Observation vs. Interpretation vs. Limitation
          </h3>
        </div>
        <span className="font-mono text-[10px] font-semibold text-amber-300 bg-amber-500/15 border border-amber-500/30 px-2 py-0.5 rounded">
          {governanceTag}
        </span>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs font-sans">
        {/* 1. OBSERVATION */}
        <div className="rounded-lg border border-cyan-500/30 bg-cyan-950/20 p-4 space-y-1.5">
          <div className="flex items-center gap-1.5 font-mono text-[11px] font-bold text-cyan-400 uppercase tracking-wider">
            <span>01</span>
            <span>Observation</span>
          </div>
          <div className="text-zinc-200 text-xs leading-relaxed font-mono">
            {observation}
          </div>
          <div className="text-[10px] text-zinc-400 font-mono pt-1">
            Deterministic Output &bull; Verifiable
          </div>
        </div>

        {/* 2. INTERPRETATION */}
        <div className="rounded-lg border border-zinc-700/60 bg-zinc-950/60 p-4 space-y-1.5">
          <div className="flex items-center gap-1.5 font-mono text-[11px] font-bold text-indigo-400 uppercase tracking-wider">
            <span>02</span>
            <span>Interpretation</span>
          </div>
          <div className="text-zinc-300 text-xs leading-relaxed">
            {interpretation}
          </div>
          <div className="text-[10px] text-zinc-400 font-mono pt-1">
            Statistical Definition &bull; No Forecast
          </div>
        </div>

        {/* 3. LIMITATION */}
        <div className="rounded-lg border border-amber-500/30 bg-amber-950/20 p-4 space-y-1.5">
          <div className="flex items-center gap-1.5 font-mono text-[11px] font-bold text-amber-400 uppercase tracking-wider">
            <span>03</span>
            <span>Limitation</span>
          </div>
          <div className="text-zinc-300 text-xs leading-relaxed">
            {limitation}
          </div>
          <div className="text-[10px] text-amber-400/80 font-mono pt-1">
            Methodology & Governance Constraint &bull; Zero Tips
          </div>
        </div>
      </div>
    </section>
  );
}
