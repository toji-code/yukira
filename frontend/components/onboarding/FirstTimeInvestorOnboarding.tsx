'use client';

import React, { useState } from 'react';

interface FirstTimeInvestorOnboardingProps {
  isOpen: boolean;
  onClose: () => void;
}

export function FirstTimeInvestorOnboarding({ isOpen, onClose }: FirstTimeInvestorOnboardingProps) {
  const [step, setStep] = useState(0);

  if (!isOpen) return null;

  const steps = [
    {
      title: 'Welcome to YUKIRA',
      subtitle: 'Institutional-grade Quantitative Investment Intelligence',
      tagline: '"Before you commit capital, ask one more question."',
      body: (
        <div className="space-y-3 text-xs text-zinc-300 leading-relaxed">
          <p>
            YUKIRA serves as the rigorous verification checkpoint investors and allocators use before committing capital.
            It rejects simplistic trailing returns, opaque star ratings, marketing narratives, and black-box recommendations.
          </p>
          <div className="p-3 bg-zinc-900/60 border border-zinc-800 rounded-lg">
            <h5 className="font-semibold text-zinc-200 mb-1">Our Core Commitment:</h5>
            <p className="text-zinc-400">
              A transparent, auditable mathematical framework grounded in deterministic financial calculation and point-in-time empirical evidence.
            </p>
          </div>
        </div>
      ),
    },
    {
      title: 'Epistemic Governance & Integrity',
      subtitle: 'The Four Invariants of YUKIRA',
      tagline: 'Reliability built on verifiable mathematical proof',
      body: (
        <div className="grid grid-cols-2 gap-3 text-xs">
          <div className="p-3 bg-zinc-900/40 border border-zinc-800 rounded">
            <div className="font-semibold text-emerald-400 mb-1">1. Evidence Over Decoration</div>
            <p className="text-zinc-400 text-[11px]">Every chart, metric, and table exists to clarify data quality, provenance, or methodology.</p>
          </div>
          <div className="p-3 bg-zinc-900/40 border border-zinc-800 rounded">
            <div className="font-semibold text-cyan-400 mb-1">2. Zero Fake Data</div>
            <p className="text-zinc-400 text-[11px]">If data is missing or incomplete, the system displays &quot;Not available&quot;—never silently interpolated.</p>
          </div>
          <div className="p-3 bg-zinc-900/40 border border-zinc-800 rounded">
            <div className="font-semibold text-amber-400 mb-1">3. Deterministic Math</div>
            <p className="text-zinc-400 text-[11px]">Financial metrics are computed deterministically in vectorized code—never approximated by LLMs.</p>
          </div>
          <div className="p-3 bg-zinc-900/40 border border-zinc-800 rounded">
            <div className="font-semibold text-purple-400 mb-1">4. Point-in-Time (PIT)</div>
            <p className="text-zinc-400 text-[11px]">Strict separation of effective date vs knowledge cutoff prevents look-ahead survivorship bias.</p>
          </div>
        </div>
      ),
    },
    {
      title: 'Methodology Lifecycle',
      subtitle: 'IMPLEMENTED ≠ VALIDATED ≠ APPROVED',
      tagline: 'Transparent governance status on every analytical metric',
      body: (
        <div className="space-y-3 text-xs text-zinc-300">
          <p>
            YUKIRA enforces explicit methodology status badges across the interface so you always know the empirical validity of what you are viewing:
          </p>
          <div className="space-y-2">
            <div className="flex items-center gap-2 p-2 bg-emerald-950/20 border border-emerald-800/40 rounded">
              <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
                APPROVED
              </span>
              <span className="text-[11px] text-zinc-300">
                Formally audited baseline metric (e.g. <strong>RET-02 Simple Period Return</strong>).
              </span>
            </div>
            <div className="flex items-center gap-2 p-2 bg-amber-950/20 border border-amber-800/40 rounded">
              <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-amber-500/20 text-amber-400 border border-amber-500/30">
                CANDIDATE
              </span>
              <span className="text-[11px] text-zinc-300">
                Implemented algorithm under empirical review (e.g. RET-03 CAGR, RSK-01 Volatility).
              </span>
            </div>
          </div>
        </div>
      ),
    },
  ];

  const current = steps[step];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-md p-4">
      <div className="relative w-full max-w-xl bg-zinc-950 border border-zinc-800 rounded-xl shadow-2xl overflow-hidden p-6 space-y-6">
        <div>
          <span className="text-[10px] font-mono uppercase tracking-wider text-emerald-400 font-semibold">
            Onboarding • Step {step + 1} of {steps.length}
          </span>
          <h2 className="text-lg font-bold text-zinc-100 mt-1">{current.title}</h2>
          <p className="text-xs text-zinc-400">{current.subtitle}</p>
          <p className="text-xs italic text-zinc-500 mt-0.5">{current.tagline}</p>
        </div>

        <div className="min-h-[140px]">{current.body}</div>

        <div className="flex items-center justify-between pt-4 border-t border-zinc-800">
          <button
            onClick={() => {
              if (step > 0) setStep(step - 1);
            }}
            disabled={step === 0}
            className={`text-xs px-3 py-1.5 rounded transition-colors ${
              step === 0 ? 'text-zinc-600 cursor-not-allowed' : 'text-zinc-300 hover:bg-zinc-800'
            }`}
          >
            Previous
          </button>

          <div className="flex gap-1.5">
            {steps.map((_, i) => (
              <span
                key={i}
                className={`w-2 h-2 rounded-full transition-colors ${
                  i === step ? 'bg-emerald-400' : 'bg-zinc-800'
                }`}
              />
            ))}
          </div>

          {step < steps.length - 1 ? (
            <button
              onClick={() => setStep(step + 1)}
              className="text-xs px-4 py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white font-medium rounded transition-colors"
            >
              Continue
            </button>
          ) : (
            <button
              onClick={onClose}
              className="text-xs px-4 py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white font-medium rounded transition-colors"
            >
              Enter YUKIRA Workspace
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

