'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { useUser } from '@auth0/nextjs-auth0/client';
import { useWatchlist } from '@/lib/hooks/useWatchlist';
import { fetchPortfolioSummary, PortfolioSummaryDto } from '@/lib/api/portfolio';
import { fetchSavedComparisons, InvestorComparisonResearchItem } from '@/lib/api/investor';
import {
  getMonitoringOverview,
  getMonitoringConditions,
  MonitoringOverviewDto,
  MonitoringConditionDto,
} from '@/lib/api/monitoring';
import { NotificationCentreModal } from '@/components/monitoring/NotificationCentreModal';
import { InvestorDisplaySettingsModal } from '@/components/settings/InvestorDisplaySettingsModal';
import { GlobalInvestorSearchModal } from '@/components/search/GlobalInvestorSearchModal';
import { FirstTimeInvestorOnboarding } from '@/components/onboarding/FirstTimeInvestorOnboarding';
import { InvestorResearchTimelineFeed } from '@/components/workspace/InvestorResearchTimelineFeed';
import { InvestigationActionQueue } from '@/components/workspace/InvestigationActionQueue';

export function InvestorQuickWorkspace() {
  const { user, isLoading: authLoading } = useUser();
  const { watchlistItems, isLoaded: watchlistLoaded } = useWatchlist();
  const [portfolioSummary, setPortfolioSummary] = useState<PortfolioSummaryDto | null>(null);
  const [portfolioLoading, setPortfolioLoading] = useState(false);

  const [savedComparisons, setSavedComparisons] = useState<InvestorComparisonResearchItem[]>([]);
  const [monitoringOverview, setMonitoringOverview] = useState<MonitoringOverviewDto | null>(null);
  const [monitoringConditions, setMonitoringConditions] = useState<MonitoringConditionDto[]>([]);

  const [showNotifications, setShowNotifications] = useState(false);
  const [showSettings, setShowSettings] = useState(false);
  const [showSearch, setShowSearch] = useState(false);
  const [showOnboarding, setShowOnboarding] = useState(false);
  const [activeSubView, setActiveSubView] = useState<'PILLARS' | 'TIMELINE' | 'ACTIONS'>('PILLARS');

  useEffect(() => {
    let ignore = false;
    if (user) {
      setPortfolioLoading(true);
      fetchPortfolioSummary()
        .then((data) => {
          if (!ignore) {
            setPortfolioSummary(data);
            setPortfolioLoading(false);
          }
        })
        .catch(() => {
          if (!ignore) {
            setPortfolioLoading(false);
          }
        });

      fetchSavedComparisons()
        .then((comps) => {
          if (!ignore && Array.isArray(comps)) {
            setSavedComparisons(comps);
          }
        })
        .catch(() => {});

      getMonitoringOverview()
        .then((ov) => {
          if (!ignore && ov) {
            setMonitoringOverview(ov);
          }
        })
        .catch(() => {});

      getMonitoringConditions()
        .then((conds) => {
          if (!ignore && Array.isArray(conds)) {
            setMonitoringConditions(conds);
          }
        })
        .catch(() => {});
    } else {
      setPortfolioSummary(null);
      setSavedComparisons([]);
      setMonitoringOverview(null);
      setMonitoringConditions([]);
    }
    return () => {
      ignore = true;
    };
  }, [user]);

  // Loading state
  if (authLoading || (!watchlistLoaded && user)) {
    return (
      <div className="mb-10 rounded-2xl border border-border-gold-subtle/60 bg-surface-midnight/60 p-5 shadow-lg backdrop-blur-sm animate-pulse">
        <div className="flex items-center justify-between">
          <div className="h-4 w-48 bg-surface-navy-raised rounded" />
          <div className="h-8 w-32 bg-surface-navy-raised rounded-full" />
        </div>
      </div>
    );
  }

  // Authenticated Investor State (Consolidated 4-Pillar Hub)
  if (user) {
    const savedCount = watchlistItems.length;
    const activeTheses = watchlistItems.filter(
      (i) => i.thesisStatus === 'ACTIVE_HYPOTHESIS' || (i.workingThesis && i.workingThesis.trim().length > 0)
    );
    const needsFollowUp = watchlistItems.filter((i) => i.investigationStatus === 'NEEDS_FOLLOW_UP').length;
    const holdingsCount = portfolioSummary?.totalHoldings ?? 0;
    const staleHoldingsCount = portfolioSummary?.staleValuedHoldingsCount ?? 0;
    const unavailableCount = portfolioSummary?.unavailableHoldingsCount ?? 0;
    const hasStaleOrIncomplete = staleHoldingsCount > 0 || unavailableCount > 0;
    const accountDisplayName = user.name || user.email || 'Investor';

    const activeConditionsCount = monitoringConditions.filter(
      (c) => c.conditionStatus === 'ACTIVE' || c.isEnabled
    ).length;
    const triggeredCount = monitoringOverview?.triggeredThesisConditionsCount ?? 0;
    const totalEventsCount = monitoringOverview?.totalActiveEvents ?? 0;
    const comparisonsCount = savedComparisons.length;

    return (
      <section
        aria-label="Investor Research Workspace"
        className="mb-10 overflow-hidden rounded-2xl border border-border-gold-subtle bg-gradient-to-r from-surface-midnight via-surface-midnight to-surface-obsidian p-6 shadow-xl backdrop-blur-sm glow-gold"
      >
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6">
          <div className="flex flex-col gap-2">
            <div className="flex items-center gap-2">
              <span className="h-2 w-2 rounded-full bg-status-approved shadow-[0_0_8px_#4ade80]" />
              <span className="font-mono text-[11px] uppercase tracking-[0.14em] text-primary font-semibold">
                Consolidated Investor Research Workspace
              </span>
              <span className="font-mono text-[11px] text-text-cream-muted">·</span>
              <span className="font-sans text-xs text-text-cream-muted truncate max-w-[200px] sm:max-w-[320px]">
                {accountDisplayName}
              </span>
            </div>
            <h2 className="text-xl font-bold tracking-tight text-text-cream-bright sm:text-2xl">
              Resume Your Quantitative Research & Monitoring Checkpoints
            </h2>
            <p className="text-xs sm:text-sm text-text-cream-body max-w-2xl font-sans leading-relaxed">
              Consolidated view of active research theses, open historical stress findings, automated monitoring conditions, and preserved comparative investigations.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <Link
              href={needsFollowUp > 0 ? "/watchlist?tab=NEEDS_FOLLOW_UP" : "/watchlist"}
              className="flex items-center justify-center gap-2 rounded-full bg-gradient-to-r from-[#d4af37] via-[#f2ca50] to-[#e6be44] px-5 py-2.5 font-sans text-xs font-semibold text-[#1e1704] glow-gold-sm transition-all duration-200 hover:scale-[1.02] hover:from-[#e1bc42] hover:to-[#ffd868] shadow-md active:scale-[0.98]"
            >
              <span>Saved Research</span>
              <span className="rounded-full bg-[#1e1704]/20 px-1.5 py-0.5 font-mono text-[10px] font-bold">
                {savedCount}
              </span>
              {activeTheses.length > 0 && (
                <span className="rounded-full bg-emerald-600/30 text-emerald-950 border border-emerald-900/30 px-1.5 py-0.5 font-mono text-[10px] font-bold">
                  {activeTheses.length} active
                </span>
              )}
            </Link>

            <Link
              href="/monitoring"
              className="flex items-center justify-center gap-2 rounded-full border border-border-gold-subtle bg-surface-navy-raised px-5 py-2.5 font-sans text-xs font-semibold text-text-cream-bright transition-all hover:bg-surface-navy-hover hover:border-border-gold-bright"
            >
              <span>Monitoring</span>
              <span className="rounded-full bg-surface-midnight px-1.5 py-0.5 font-mono text-[10px] font-bold text-primary border border-border-gold-subtle">
                {activeConditionsCount}
              </span>
              {triggeredCount > 0 && (
                <span className="rounded-full bg-rose-500/20 text-rose-300 border border-rose-500/40 px-1.5 py-0.5 font-mono text-[10px] font-bold">
                  {triggeredCount} alert{triggeredCount > 1 ? 's' : ''}
                </span>
              )}
            </Link>

            <Link
              href="/portfolio"
              className="flex items-center justify-center gap-2 rounded-full border border-border-gold-subtle bg-surface-navy-raised px-5 py-2.5 font-sans text-xs font-semibold text-text-cream-bright transition-all hover:bg-surface-navy-hover hover:border-border-gold-bright"
            >
              <span>Portfolio Holdings</span>
              <span className="rounded-full bg-surface-midnight px-1.5 py-0.5 font-mono text-[10px] font-bold text-primary border border-border-gold-subtle">
                {holdingsCount}
              </span>
            </Link>

            {/* V1 Workspace Controls: Search, Notifications, Settings, Tour */}
            <div className="flex items-center gap-1.5 pl-2 border-l border-border-gold-subtle/40">
              <button
                onClick={() => setShowSearch(true)}
                title="Global Search (Ctrl+K)"
                className="p-2 rounded-full bg-surface-navy-raised border border-border-gold-subtle text-text-cream-muted hover:text-text-cream-bright hover:border-border-gold-bright transition-colors"
              >
                🔍
              </button>
              <button
                onClick={() => setShowNotifications(true)}
                title="Notifications"
                className="relative p-2 rounded-full bg-surface-navy-raised border border-border-gold-subtle text-text-cream-muted hover:text-text-cream-bright hover:border-border-gold-bright transition-colors"
              >
                🔔
              </button>
              <button
                onClick={() => setShowSettings(true)}
                title="Display Preferences"
                className="p-2 rounded-full bg-surface-navy-raised border border-border-gold-subtle text-text-cream-muted hover:text-text-cream-bright hover:border-border-gold-bright transition-colors"
              >
                ⚙️
              </button>
              <button
                onClick={() => setShowOnboarding(true)}
                title="Methodology Walkthrough"
                className="px-2.5 py-1 text-[11px] font-mono rounded-full bg-surface-navy-raised border border-border-gold-subtle text-primary hover:border-border-gold-bright transition-colors"
              >
                ? Guide
              </button>
            </div>
          </div>
        </div>

        {/* Subview Navigation: Overview Pillars vs Unified Timeline vs Action Queue */}
        <div className="mt-6 pt-4 border-t border-border-gold-subtle/40 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <button
              onClick={() => setActiveSubView('PILLARS')}
              className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-colors ${
                activeSubView === 'PILLARS'
                  ? 'bg-primary text-[#1e1704]'
                  : 'text-text-cream-muted hover:text-text-cream-bright bg-surface-navy-raised'
              }`}
            >
              Overview Pillars
            </button>
            <button
              onClick={() => setActiveSubView('TIMELINE')}
              className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-colors ${
                activeSubView === 'TIMELINE'
                  ? 'bg-primary text-[#1e1704]'
                  : 'text-text-cream-muted hover:text-text-cream-bright bg-surface-navy-raised'
              }`}
            >
              Unified Research Timeline
            </button>
            <button
              onClick={() => setActiveSubView('ACTIONS')}
              className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-colors ${
                activeSubView === 'ACTIONS'
                  ? 'bg-primary text-[#1e1704]'
                  : 'text-text-cream-muted hover:text-text-cream-bright bg-surface-navy-raised'
              }`}
            >
              Investigation Queue
            </button>
          </div>
        </div>

        {/* Modals */}
        <NotificationCentreModal isOpen={showNotifications} onClose={() => setShowNotifications(false)} />
        <InvestorDisplaySettingsModal isOpen={showSettings} onClose={() => setShowSettings(false)} />
        <GlobalInvestorSearchModal isOpen={showSearch} onClose={() => setShowSearch(false)} />
        <FirstTimeInvestorOnboarding isOpen={showOnboarding} onClose={() => setShowOnboarding(false)} />

        {activeSubView === 'TIMELINE' ? (
          <div className="mt-6">
            <InvestorResearchTimelineFeed />
          </div>
        ) : activeSubView === 'ACTIONS' ? (
          <div className="mt-6">
            <InvestigationActionQueue />
          </div>
        ) : (
        /* 4-Pillar Consolidated Research Grid */
        <div className="mt-6 pt-2 grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
          {/* Pillar 1: Watchlist Research & Active Theses */}
          <div className="p-4 rounded-xl bg-surface-obsidian/80 border border-border-gold-subtle/40 flex flex-col justify-between space-y-3">
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <span className="font-mono text-[10.5px] uppercase tracking-wider text-text-cream-muted font-medium">
                  Watchlist Due Diligence
                </span>
                <span className="font-mono text-[11px] font-bold text-primary bg-primary/10 px-2 py-0.5 rounded border border-primary/30">
                  {savedCount} Schemes
                </span>
              </div>
              <p className="font-sans text-xs font-semibold text-text-cream-bright">
                {activeTheses.length > 0
                  ? `${activeTheses.length} Active Research Hypothesis${activeTheses.length > 1 ? 'es' : ''}`
                  : 'No active hypothesis recorded'}
              </p>
              <p className="mt-1 text-[11px] text-text-cream-muted font-sans line-clamp-2">
                {needsFollowUp > 0
                  ? `${needsFollowUp} candidate fund(s) flagged as requiring follow-up inspection.`
                  : 'Track due diligence questions, verify peer evidence, and record analytical theses.'}
              </p>
            </div>
            <div className="pt-2 border-t border-border-gold-subtle/30 flex items-center justify-between text-[11px]">
              <Link
                href="/watchlist"
                className="font-mono text-primary hover:underline flex items-center gap-1"
              >
                Inspect Watchlist &rarr;
              </Link>
              {activeTheses.length > 0 && (
                <span className="font-mono text-[10px] text-emerald-400">
                  ● Thesis Linked
                </span>
              )}
            </div>
          </div>

          {/* Pillar 2: Open Stress Findings & Capital Impairment Checkpoints */}
          <div className="p-4 rounded-xl bg-surface-obsidian/80 border border-border-gold-subtle/40 flex flex-col justify-between space-y-3">
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <span className="font-mono text-[10.5px] uppercase tracking-wider text-text-cream-muted font-medium">
                  Stress &amp; Drawdowns
                </span>
                <span className="font-mono text-[10.5px] font-semibold text-amber-400 bg-amber-500/10 px-2 py-0.5 rounded border border-amber-500/30">
                  Historical Audit
                </span>
              </div>
              <p className="font-sans text-xs font-semibold text-text-cream-bright">
                Capital Preservation &amp; Recovery
              </p>
              <p className="mt-1 text-[11px] text-text-cream-muted font-sans line-clamp-2">
                Review historical stress findings across market shocks (COVID Crash 2020, Rate Hike 2022) with zero synthetic data.
              </p>
            </div>
            <div className="pt-2 border-t border-border-gold-subtle/30 flex items-center justify-between text-[11px]">
              <Link
                href="/funds/HDFC_FLEXI#performance-risk"
                className="font-mono text-primary hover:underline flex items-center gap-1"
              >
                Inspect Stress Audit &rarr;
              </Link>
              <span className="font-mono text-[10px] text-text-cream-muted">
                Max DD: RSK-03
              </span>
            </div>
          </div>

          {/* Pillar 3: Continuous Monitoring & Condition Triggers */}
          <div className="p-4 rounded-xl bg-surface-obsidian/80 border border-border-gold-subtle/40 flex flex-col justify-between space-y-3">
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <span className="font-mono text-[10.5px] uppercase tracking-wider text-text-cream-muted font-medium">
                  Continuous Monitoring
                </span>
                <span className={`font-mono text-[10.5px] font-semibold px-2 py-0.5 rounded border ${
                  triggeredCount > 0
                    ? 'text-rose-400 bg-rose-500/10 border-rose-500/30'
                    : 'text-emerald-400 bg-emerald-500/10 border-emerald-500/30'
                }`}>
                  {triggeredCount > 0 ? `${triggeredCount} Triggered` : 'All Clear'}
                </span>
              </div>
              <p className="font-sans text-xs font-semibold text-text-cream-bright">
                {activeConditionsCount > 0
                  ? `${activeConditionsCount} Active Monitored Condition${activeConditionsCount > 1 ? 's' : ''}`
                  : 'Monitoring Ledger Active'}
              </p>
              <p className="mt-1 text-[11px] text-text-cream-muted font-sans line-clamp-2">
                {totalEventsCount > 0
                  ? `${totalEventsCount} active monitoring event(s) logged across evidence changes & drift.`
                  : 'Tracks investment drift, threshold breaches, and thesis invalidation conditions.'}
              </p>
            </div>
            <div className="pt-2 border-t border-border-gold-subtle/30 flex items-center justify-between text-[11px]">
              <Link
                href="/monitoring"
                className="font-mono text-primary hover:underline flex items-center gap-1"
              >
                Open Monitor &rarr;
              </Link>
              <span className="font-mono text-[10px] text-text-cream-muted">
                Scheduled Scan
              </span>
            </div>
          </div>

          {/* Pillar 4: Preserved Comparative Research Investigations */}
          <div className="p-4 rounded-xl bg-surface-obsidian/80 border border-border-gold-subtle/40 flex flex-col justify-between space-y-3">
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <span className="font-mono text-[10.5px] uppercase tracking-wider text-text-cream-muted font-medium">
                  Comparative Research
                </span>
                <span className="font-mono text-[10.5px] font-bold text-sky-400 bg-sky-500/10 px-2 py-0.5 rounded border border-sky-500/30">
                  {comparisonsCount} Saved
                </span>
              </div>
              <p className="font-sans text-xs font-semibold text-text-cream-bright truncate" title={savedComparisons[0]?.comparisonTitle || 'Multi-Fund Screener'}>
                {savedComparisons[0]?.comparisonTitle || 'Multi-Fund Investigations'}
              </p>
              <p className="mt-1 text-[11px] text-text-cream-muted font-sans line-clamp-2">
                {savedComparisons.length > 0
                  ? `Last saved: ${savedComparisons[0].asOfDate} · ${savedComparisons[0].compatibilityStatus}`
                  : 'Preserve side-by-side matrices, correlation analyses, and pairwise overlap studies.'}
              </p>
            </div>
            <div className="pt-2 border-t border-border-gold-subtle/30 flex items-center justify-between text-[11px]">
              <Link
                href="/compare"
                className="font-mono text-primary hover:underline flex items-center gap-1"
              >
                Launch Screener &rarr;
              </Link>
              <span className="font-mono text-[10px] text-text-cream-muted">
                Zero AI Math
              </span>
            </div>
          </div>
        </div>
        )}
      </section>
    );
  }

  // Unauthenticated / Guest State
  const guestSavedCount = watchlistItems.length;

  return (
    <section
      aria-label="Unified Research Entry"
      className="mb-10 overflow-hidden rounded-2xl border border-border-gold-subtle bg-gradient-to-r from-surface-midnight via-surface-midnight to-surface-obsidian p-6 shadow-xl backdrop-blur-sm glow-gold"
    >
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6">
        <div className="flex flex-col gap-2 max-w-2xl">
          <div className="flex items-center gap-2">
            <span className="h-2 w-2 rounded-full bg-primary" />
            <span className="font-mono text-[11px] uppercase tracking-[0.14em] text-primary font-semibold">
              Guest Research Mode
            </span>
            <span className="font-mono text-[11px] text-text-cream-muted">·</span>
            <span className="font-sans text-xs text-text-cream-muted">
              Full empirical access · Local browser persistence
            </span>
          </div>
          <h2 className="text-xl font-bold tracking-tight text-text-cream-bright sm:text-2xl">
            {guestSavedCount > 0
              ? `You Have ${guestSavedCount} Saved Investigation${guestSavedCount === 1 ? '' : 's'} in Local Storage`
              : 'Begin Your Verification Checkpoint'}
          </h2>
          <p className="text-xs sm:text-sm text-text-cream-body font-sans leading-relaxed">
            {guestSavedCount > 0
              ? 'Your research notes, due diligence questions, and candidate funds are stored in this browser. Sign in at any time to sync them securely to a permanent investor account.'
              : 'Search Indian mutual funds, compare candidate schemes on deterministic risk metrics, or audit data lineage. No account required to investigate public evidence.'}
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          {guestSavedCount > 0 ? (
            <>
              <Link
                href="/watchlist"
                className="flex items-center justify-center gap-2 rounded-full bg-gradient-to-r from-[#d4af37] via-[#f2ca50] to-[#e6be44] px-5 py-2.5 font-sans text-xs font-semibold text-[#1e1704] glow-gold-sm transition-all duration-200 hover:scale-[1.02] hover:from-[#e1bc42] hover:to-[#ffd868] shadow-md active:scale-[0.98]"
              >
                <span>Open Saved Research</span>
                <span className="rounded-full bg-[#1e1704]/20 px-1.5 py-0.5 font-mono text-[10px] font-bold">
                  {guestSavedCount}
                </span>
              </Link>
              <a
                href="/auth/login"
                className="flex items-center justify-center rounded-full border border-border-gold-subtle bg-surface-navy-raised px-5 py-2.5 font-sans text-xs font-semibold text-text-cream-bright transition-all hover:bg-surface-navy-hover hover:border-border-gold-bright"
              >
                Sign In to Sync &rarr;
              </a>
            </>
          ) : (
            <>
              <Link
                href="/funds"
                className="flex items-center justify-center rounded-full bg-gradient-to-r from-[#d4af37] via-[#f2ca50] to-[#e6be44] px-6 py-2.5 font-sans text-xs font-semibold text-[#1e1704] glow-gold-sm transition-all duration-200 hover:scale-[1.02] hover:from-[#e1bc42] hover:to-[#ffd868] shadow-md active:scale-[0.98]"
              >
                Explore Mutual Funds
              </Link>
              <Link
                href="/compare"
                className="flex items-center justify-center rounded-full border border-border-gold-subtle bg-surface-navy-raised px-5 py-2.5 font-sans text-xs font-semibold text-text-cream-bright transition-all hover:bg-surface-navy-hover hover:border-border-gold-bright"
              >
                Compare Candidates
              </Link>
            </>
          )}
        </div>
      </div>
    </section>
  );
}
