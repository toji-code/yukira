'use client';

import React from 'react';

interface EpistemicBannerProps {
  observation: string | React.ReactNode;
  interpretation: string | React.ReactNode;
  limitation: string | React.ReactNode;
  governanceTag?: string;
  title?: string;
}

/**
 * The signature YUKIRA pattern.
 *
 * Observation / Interpretation / Limitation as three visibly separated columns,
 * each with its own 1px left rule whose colour encodes the epistemic register of
 * that column. A pulsing accent dot carried no epistemic meaning and has been
 * removed: motion here must confirm a state change, never perform.
 */
export function EpistemicBanner({
  observation,
  interpretation,
  limitation,
  governanceTag = 'Candidate methodology — not validated for production',
  title = 'Epistemic distinction',
}: EpistemicBannerProps) {
  return (
    <section className="panel p-5" aria-label="Epistemic tri-partite distinction">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border pb-3">
        <h3 className="text-[15px] font-semibold text-text-primary">{title}</h3>
        <span className="status-badge state-candidate">
          <span className="status-dot" aria-hidden />
          {governanceTag}
        </span>
      </div>

      <div className="mt-4 grid grid-cols-1 gap-5 md:grid-cols-3 md:gap-6">
        <div className="chain-col" style={{ borderLeftColor: 'var(--operational-fg)' }}>
          <p className="eyebrow" style={{ color: 'var(--operational-fg)' }}>
            01 Observation
          </p>
          <div className="mt-2 font-mono text-[13px] leading-[1.5] tabular-nums text-text-primary">
            {observation}
          </div>
          <p className="mono-meta mt-2">Deterministic output · verifiable</p>
        </div>

        <div className="chain-col" style={{ borderLeftColor: 'var(--info-fg)' }}>
          <p className="eyebrow">02 Interpretation</p>
          <div className="mt-2 text-[13px] leading-[1.55] text-text-secondary">
            {interpretation}
          </div>
          <p className="mono-meta mt-2">Statistical definition · no forecast</p>
        </div>

        <div className="chain-col" style={{ borderLeftColor: 'var(--candidate-fg)' }}>
          <p className="eyebrow" style={{ color: 'var(--candidate-fg)' }}>
            03 Limitation
          </p>
          <div className="mt-2 text-[13px] leading-[1.55] text-text-secondary">
            {limitation}
          </div>
          <p className="mono-meta mt-2" style={{ color: 'var(--candidate-fg)' }}>
            Methodology and governance constraint · no advice
          </p>
        </div>
      </div>
    </section>
  );
}