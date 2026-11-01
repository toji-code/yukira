'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';

export interface OnboardingStep {
  id: number;
  title: string;
  shortDesc: string;
  actionText: string;
  actionHref: string;
  evidenceBadge: string;
}

export const ONBOARDING_STEPS: OnboardingStep[] = [
  {
    id: 1,
    title: 'Discover a Mutual Fund',
    shortDesc: 'Search the fund universe, filter by direct growth options, and select a candidate fund for evaluation.',
    actionText: 'Browse Funds Universe',
    actionHref: '/funds',
    evidenceBadge: 'AMFI Universe',
  },
  {
    id: 2,
    title: 'Open Analytical Profile',
    shortDesc: 'View point-in-time institutional returns, annualized volatilities, and downmarket capture ratios.',
    actionText: 'Open Canonical Pilot Profile',
    actionHref: '/funds/1?option=1',
    evidenceBadge: 'HDFC Flexi Cap Direct',
  },
  {
    id: 3,
    title: 'Inspect Return and Risk Evidence',
    shortDesc: 'Trace analytical conclusions back to raw daily NAV observations, benchmark TRI series, and risk-free curves.',
    actionText: 'Inspect Evidence Explorer',
    actionHref: '/funds/1?option=1#evidence',
    evidenceBadge: 'Evidence Explorer',
  },
  {
    id: 4,
    title: 'Screen & Compare Alternatives',
    shortDesc: 'Run multi-fund comparative matrices side-by-side with identical bitemporal market cutoffs.',
    actionText: 'Open Comparative Screener',
    actionHref: '/compare',
    evidenceBadge: 'Deterministic Screener',
  },
  {
    id: 5,
    title: 'Save to Research Workspace',
    shortDesc: 'Retain investigated funds in your workspace with exact scheme-option identity and persistence confirmation.',
    actionText: 'View Research Workspace',
    actionHref: '/watchlist',
    evidenceBadge: 'Research Workspace',
  },
  {
    id: 6,
    title: 'Record Due-Diligence Questions',
    shortDesc: 'Formulate hypotheses and questions for allocators, preserved point-in-time without automated buy/sell labels.',
    actionText: 'Add Due-Diligence Note',
    actionHref: '/watchlist',
    evidenceBadge: 'Private Hypotheses',
  },
];

const STORAGE_KEY_DISMISSED = 'yukira_onboarding_dismissed';
const STORAGE_KEY_STEP = 'yukira_onboarding_active_step';

interface InvestorOnboardingGuideProps {
  forceOpen?: boolean;
  onDismissChange?: (dismissed: boolean) => void;
  className?: string;
}

export function InvestorOnboardingGuide({
  forceOpen = false,
  onDismissChange,
  className = '',
}: InvestorOnboardingGuideProps) {
  const [dismissed, setDismissed] = useState<boolean>(true);
  const [activeStepId, setActiveStepId] = useState<number>(1);
  const [completedSteps, setCompletedSteps] = useState<number[]>([]);
  const [mounted, setMounted] = useState<boolean>(false);

  useEffect(() => {
    setMounted(true);
    try {
      const isDismissed = localStorage.getItem(STORAGE_KEY_DISMISSED) === 'true';
      setDismissed(isDismissed);

      const savedStep = localStorage.getItem(STORAGE_KEY_STEP);
      if (savedStep) {
        const parsed = parseInt(savedStep, 10);
        if (parsed >= 1 && parsed <= 6) {
          setActiveStepId(parsed);
        }
      }

      const savedCompleted = localStorage.getItem('yukira_onboarding_completed_steps');
      if (savedCompleted) {
        setCompletedSteps(JSON.parse(savedCompleted));
      }
    } catch {
      // LocalStorage fallback for private browsing
    }
  }, []);

  const handleDismiss = () => {
    setDismissed(true);
    try {
      localStorage.setItem(STORAGE_KEY_DISMISSED, 'true');
    } catch {}
    if (onDismissChange) onDismissChange(true);
  };

  const handleStepSelect = (stepId: number) => {
    setActiveStepId(stepId);
    try {
      localStorage.setItem(STORAGE_KEY_STEP, stepId.toString());
    } catch {}
  };

  const handleToggleComplete = (stepId: number) => {
    const next = completedSteps.includes(stepId)
      ? completedSteps.filter((id) => id !== stepId)
      : [...completedSteps, stepId];
    setCompletedSteps(next);
    try {
      localStorage.setItem('yukira_onboarding_completed_steps', JSON.stringify(next));
    } catch {}

    // Auto-advance to next step if marking complete
    if (!completedSteps.includes(stepId) && stepId < 6) {
      handleStepSelect(stepId + 1);
    }
  };

  if (!mounted) return null;
  if (dismissed && !forceOpen) return null;

  const currentStep = ONBOARDING_STEPS.find((s) => s.id === activeStepId) || ONBOARDING_STEPS[0];
  const progressPercent = Math.round((completedSteps.length / ONBOARDING_STEPS.length) * 100);

  return (
    <div
      className={`panel overflow-hidden border border-border-gold-subtle bg-surface-midnight/90 shadow-lg backdrop-blur-md transition-all ${className}`}
      data-testid="investor-onboarding-guide"
    >
      {/* Header Bar */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border-gold-subtle bg-surface-navy-raised/40 px-5 py-3.5">
        <div className="flex items-center gap-3">
          <span className="flex h-7 w-7 items-center justify-center rounded-full bg-primary/20 text-xs font-bold text-primary">
            🧭
          </span>
          <div>
            <h2 className="text-sm font-semibold tracking-wide text-text-cream-bright">
              Investor Onboarding Journey
            </h2>
            <p className="text-xs text-text-cream-muted">
              Core research verification workflow: from fund discovery to due-diligence question retention
            </p>
          </div>
        </div>

        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2">
            <span className="text-[11px] font-mono text-text-cream-muted">
              {completedSteps.length}/{ONBOARDING_STEPS.length} Completed
            </span>
            <div className="h-2 w-20 overflow-hidden rounded-full bg-surface-navy-raised border border-border-gold-subtle">
              <div
                className="h-full bg-primary transition-all duration-300"
                style={{ width: `${progressPercent}%` }}
              />
            </div>
          </div>

          <button
            onClick={handleDismiss}
            className="text-xs text-text-cream-muted hover:text-text-cream-bright rounded px-2 py-1 transition-colors hover:bg-surface-navy-raised/60"
            title="Dismiss onboarding guide"
            aria-label="Dismiss onboarding guide"
          >
            Dismiss ✕
          </button>
        </div>
      </div>

      {/* Step Stepper Tabs */}
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-6 border-b border-border-gold-subtle bg-surface-navy-raised/20">
        {ONBOARDING_STEPS.map((step) => {
          const isActive = step.id === activeStepId;
          const isDone = completedSteps.includes(step.id);
          return (
            <button
              key={step.id}
              onClick={() => handleStepSelect(step.id)}
              className={`flex flex-col items-start px-3.5 py-2.5 text-left transition-all border-b-2 sm:border-b-0 sm:border-r border-border-gold-subtle last:border-r-0 ${
                isActive
                  ? 'bg-surface-navy-raised/80 border-b-primary font-semibold text-primary'
                  : 'text-text-cream-muted hover:bg-surface-navy-raised/40 hover:text-text-cream-bright'
              }`}
            >
              <div className="flex items-center gap-1.5 w-full justify-between">
                <span className="font-mono text-[10px] uppercase tracking-wider text-text-cream-muted">
                  Step 0{step.id}
                </span>
                {isDone && (
                  <span className="text-[11px] text-positive font-bold" title="Completed">
                    ✓
                  </span>
                )}
              </div>
              <span className="mt-0.5 text-xs font-medium leading-snug truncate w-full">
                {step.title}
              </span>
            </button>
          );
        })}
      </div>

      {/* Active Step Content */}
      <div className="p-5">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="max-w-2xl space-y-2">
            <div className="flex items-center gap-2">
              <span className="status-badge state-candidate text-[10px] font-mono">
                {currentStep.evidenceBadge}
              </span>
              <h3 className="text-base font-semibold text-text-cream-bright">
                Step {currentStep.id}: {currentStep.title}
              </h3>
            </div>
            <p className="text-xs leading-relaxed text-text-cream-muted">
              {currentStep.shortDesc}
            </p>
          </div>

          {/* Action & Toggle Controls */}
          <div className="flex items-center gap-3 shrink-0">
            <button
              type="button"
              onClick={() => handleToggleComplete(currentStep.id)}
              className={`rounded-full px-3.5 py-1.5 text-xs font-medium border transition-all ${
                completedSteps.includes(currentStep.id)
                  ? 'bg-positive/10 border-positive/30 text-positive hover:bg-positive/20'
                  : 'bg-surface-navy-raised border-border-gold-subtle text-text-cream-muted hover:text-text-cream-bright'
              }`}
            >
              {completedSteps.includes(currentStep.id) ? '✓ Completed' : 'Mark as Done'}
            </button>

            <Link
              href={currentStep.actionHref}
              className="rounded-full bg-gradient-to-r from-[#d4af37] via-[#f2ca50] to-[#e6be44] px-4 py-1.5 text-xs font-semibold text-[#1e1704] transition-all hover:scale-[1.02] hover:from-[#e1bc42] hover:to-[#ffd868] shadow-sm flex items-center gap-1.5"
            >
              <span>{currentStep.actionText}</span>
              <span aria-hidden="true">→</span>
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
