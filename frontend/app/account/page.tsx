'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { useUser } from '@auth0/nextjs-auth0/client';
import { PageContainer } from '@/components/layout/PageContainer';
import { StateView } from '@/components/epistemic/StateView';
import { fetchInvestorAccount, InvestorAccountResponse } from '@/lib/api/investor';
import {
  fetchCurrentSubscription,
  InvestorSubscription,
  fetchBillingHistory,
  BillingRecord,
  cancelSubscription,
  reactivateSubscription,
  fetchInvoiceUrl,
} from '@/lib/api/subscription';
import { useWatchlist } from '@/lib/hooks/useWatchlist';
import { InvestorOnboardingGuide } from '@/components/onboarding/InvestorOnboardingGuide';

export default function AccountPage() {
  const { user, isLoading: authLoading, error: authError } = useUser();
  const { watchlistItems, syncStatus, isLoaded: watchlistLoaded } = useWatchlist();

  const [backendAccount, setBackendAccount] = useState<InvestorAccountResponse | null>(null);
  const [currentSub, setCurrentSub] = useState<InvestorSubscription | null>(null);
  const [billingRecords, setBillingRecords] = useState<BillingRecord[]>([]);
  const [accountLoading, setAccountLoading] = useState<boolean>(true);
  const [billingLoading, setBillingLoading] = useState<boolean>(true);
  const [accountError, setAccountError] = useState<string | null>(null);
  const [showOnboarding, setShowOnboarding] = useState<boolean>(false);

  // Subscription self-service state
  const [actionLoading, setActionLoading] = useState<boolean>(false);
  const [actionMessage, setActionMessage] = useState<{ text: string; type: 'success' | 'info' | 'error' } | null>(null);
  const [showCancelModal, setShowCancelModal] = useState<boolean>(false);
  const [cancelReason, setCancelReason] = useState<string>('');
  const [invoiceStatusModal, setInvoiceStatusModal] = useState<{ record: BillingRecord; message: string } | null>(null);

  useEffect(() => {
    let active = true;

    if (!user) {
      setBackendAccount(null);
      setCurrentSub(null);
      setBillingRecords([]);
      setAccountLoading(false);
      setBillingLoading(false);
      return;
    }

    setAccountLoading(true);
    setBillingLoading(true);
    Promise.all([
      fetchInvestorAccount().catch((err) => {
        logWarn('Account metadata load error', err);
        return null;
      }),
      fetchCurrentSubscription().catch((err) => {
        logWarn('Subscription metadata load error', err);
        return null;
      }),
      fetchBillingHistory().catch((err) => {
        logWarn('Billing history load error', err);
        return null;
      }),
    ])
      .then(([accountData, subData, billingData]) => {
        if (active) {
          if (accountData) setBackendAccount(accountData);
          if (subData) setCurrentSub(subData);
          if (billingData) setBillingRecords(billingData);
          setAccountError(null);
          setAccountLoading(false);
          setBillingLoading(false);
        }
      })
      .catch((err) => {
        if (active) {
          setAccountError(err instanceof Error ? err.message : 'Unable to load investor account metadata.');
          setAccountLoading(false);
          setBillingLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [user]);

  function logWarn(msg: string, err: unknown) {
    if (process.env.NODE_ENV !== 'production') {
      console.warn(msg, err);
    }
  }

  const handleRestartOnboarding = () => {
    try {
      localStorage.removeItem('yukira_onboarding_dismissed');
      localStorage.setItem('yukira_onboarding_active_step', '1');
    } catch {}
    setShowOnboarding(true);
  };

  const handleConfirmCancel = async () => {
    setActionLoading(true);
    setActionMessage(null);
    try {
      const res = await cancelSubscription({ reason: cancelReason.trim() ? cancelReason.trim() : null });
      setCurrentSub(res.subscription);
      setActionMessage({ text: res.message, type: 'info' });
      setShowCancelModal(false);
      setCancelReason('');
    } catch (err) {
      setActionMessage({
        text: err instanceof Error ? err.message : 'Failed to schedule subscription cancellation.',
        type: 'error',
      });
    } finally {
      setActionLoading(false);
    }
  };

  const handleReactivate = async () => {
    setActionLoading(true);
    setActionMessage(null);
    try {
      const res = await reactivateSubscription();
      setCurrentSub(res.subscription);
      setActionMessage({ text: res.message, type: 'success' });
    } catch (err) {
      setActionMessage({
        text: err instanceof Error ? err.message : 'Failed to reactivate subscription auto-renewal.',
        type: 'error',
      });
    } finally {
      setActionLoading(false);
    }
  };

  const handleInvoiceClick = async (record: BillingRecord) => {
    if (record.invoiceAvailable && record.providerInvoiceUrl) {
      window.open(record.providerInvoiceUrl, '_blank', 'noopener,noreferrer');
      return;
    }

    try {
      const data = await fetchInvoiceUrl(record.id);
      if (data?.invoiceUrl) {
        window.open(data.invoiceUrl, '_blank', 'noopener,noreferrer');
      } else {
        setInvoiceStatusModal({
          record,
          message: record.invoiceStatusMessage || 'Statutory GST invoice generation requires active Razorpay Invoices / GST compliance configuration.',
        });
      }
    } catch (err) {
      setInvoiceStatusModal({
        record,
        message: err instanceof Error ? err.message : 'Official GST invoice is not yet configured for this transaction.',
      });
    }
  };

  if (authLoading) {
    return (
      <PageContainer
        title="Investor Account"
        subtitle="Verification status, session identity, and research workspace state."
        breadcrumbs={[
          { label: 'Home', href: '/' },
          { label: 'Account', href: '/account' },
        ]}
      >
        <div className="panel p-8 text-center font-mono text-xs text-text-cream-muted">
          Loading authentication and session state…
        </div>
      </PageContainer>
    );
  }

  return (
    <PageContainer
      title="Investor Account"
      subtitle="Verification status, session identity, research workspace synchronization, and account preferences."
      breadcrumbs={[
        { label: 'Home', href: '/' },
        { label: 'Account', href: '/account' },
      ]}
      action={
        user ? (
          <a
            href="/auth/logout"
            className="btn btn-secondary text-xs"
          >
            Sign Out
          </a>
        ) : (
          <a
            href="/auth/login?returnTo=/account"
            className="rounded-full bg-gradient-to-r from-[#d4af37] via-[#f2ca50] to-[#e6be44] px-4 py-1.5 font-sans text-xs font-semibold text-[#1e1704] transition-all hover:scale-[1.02]"
          >
            Sign In to Account
          </a>
        )
      }
    >
      {/* Onboarding Guide if forced or active */}
      {showOnboarding && (
        <div className="mb-6">
          <InvestorOnboardingGuide forceOpen={true} onDismissChange={(dismissed) => {
            if (dismissed) setShowOnboarding(false);
          }} />
        </div>
      )}

      {/* Guest Exploration State */}
      {!user && (
        <div className="panel mb-6 border border-border-gold-subtle bg-surface-midnight/80 p-6 backdrop-blur-md">
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-6">
            <div className="space-y-2 max-w-2xl">
              <div className="flex items-center gap-2">
                <span className="status-badge state-candidate text-[10px] font-mono">GUEST SESSION</span>
                <h2 className="text-base font-semibold text-text-cream-bright">
                  Unauthenticated Browser Exploration
                </h2>
              </div>
              <p className="text-xs leading-relaxed text-text-cream-muted">
                You are currently exploring YUKIRA as a guest. Public fund catalog discovery, institutional analytical profiles, Evidence Explorer, and comparative matrices are completely accessible without registration.
              </p>
              <p className="text-xs leading-relaxed text-text-cream-muted">
                Your saved funds and research notes are stored <strong className="text-text-cream-bright">locally in this browser</strong> ({watchlistItems.length} saved item{watchlistItems.length === 1 ? '' : 's'}). Sign in to permanently synchronize your research to an authenticated investor account across devices.
              </p>
            </div>

            <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3 shrink-0">
              <a
                href="/auth/login?returnTo=/account"
                className="flex items-center justify-center rounded-full bg-gradient-to-r from-[#d4af37] via-[#f2ca50] to-[#e6be44] px-5 py-2 font-sans text-xs font-semibold text-[#1e1704] shadow-sm transition-all hover:scale-[1.02]"
              >
                Sign In / Register
              </a>
              <Link
                href="/watchlist"
                className="btn btn-secondary text-xs flex items-center justify-center"
              >
                View Local Workspace
              </Link>
            </div>
          </div>
        </div>
      )}

      {/* Authenticated Identity Grid */}
      {user && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
          {/* Column 1 & 2: Identity & Session Details */}
          <div className="panel md:col-span-2 p-6 border border-border-gold-subtle bg-surface-midnight/80 backdrop-blur-md space-y-6">
            <div className="flex items-center justify-between border-b border-border-gold-subtle pb-4">
              <div className="flex items-center gap-3.5">
                <div className="flex h-12 w-12 items-center justify-center rounded-full bg-surface-navy-raised border border-border-gold-subtle font-mono text-base font-bold text-primary">
                  {user.name?.charAt(0)?.toUpperCase() || user.email?.charAt(0)?.toUpperCase() || 'I'}
                </div>
                <div>
                  <h2 className="text-base font-semibold text-text-cream-bright">
                    {user.name || 'Verified Investor'}
                  </h2>
                  <p className="font-mono text-xs text-text-cream-muted">
                    {user.email || 'No email disclosed by provider'}
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <span className="status-badge state-approved text-[10px] font-mono">
                  AUTHENTICATED
                </span>
                {user.email_verified && (
                  <span className="status-badge state-approved text-[10px] font-mono">
                    EMAIL VERIFIED
                  </span>
                )}
              </div>
            </div>

            {/* Read-Only Profile Attributes */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs font-mono">
              <div className="panel p-3.5 bg-surface-navy-raised/40 border border-border-gold-subtle">
                <span className="text-[10px] uppercase tracking-wider text-text-cream-muted block">
                  Identity Provider Identifier
                </span>
                <span className="text-text-cream-bright break-all mt-1 block">
                  {user.sub || 'N/A'}
                </span>
              </div>

              <div className="panel p-3.5 bg-surface-navy-raised/40 border border-border-gold-subtle">
                <span className="text-[10px] uppercase tracking-wider text-text-cream-muted block">
                  Backend Investor Account ID
                </span>
                <span className="text-text-cream-bright mt-1 block">
                  {accountLoading ? 'Resolving…' : backendAccount?.id ? `#${backendAccount.id}` : 'Local Dev / Fallback'}
                </span>
              </div>

              <div className="panel p-3.5 bg-surface-navy-raised/40 border border-border-gold-subtle">
                <span className="text-[10px] uppercase tracking-wider text-text-cream-muted block">
                  Account Registration Date
                </span>
                <span className="text-text-cream-bright mt-1 block">
                  {backendAccount?.createdAt ? new Date(backendAccount.createdAt).toLocaleDateString('en-IN', {
                    day: 'numeric',
                    month: 'short',
                    year: 'numeric',
                  }) : 'Point-in-time active'}
                </span>
              </div>

              <div className="panel p-3.5 bg-surface-navy-raised/40 border border-border-gold-subtle">
                <span className="text-[10px] uppercase tracking-wider text-text-cream-muted block">
                  Authoritative Plan & Entitlement
                </span>
                <div className="flex items-center gap-2 mt-1">
                  <span className="text-text-cream-bright font-semibold">
                    {currentSub?.planName || backendAccount?.planName || 'Community Explorer'}
                  </span>
                  <span className={`status-badge text-[9px] ${
                    (currentSub?.entitlementStatus === 'ACTIVE_PRO' || backendAccount?.entitlementStatus === 'ACTIVE_PRO')
                      ? 'state-approved'
                      : 'state-candidate'
                  }`}>
                    {currentSub?.entitlementStatus || backendAccount?.entitlementStatus || 'STANDARD'}
                  </span>
                </div>
              </div>
            </div>

            {accountError && (
              <div className="p-3 bg-critical-bg/20 border border-critical-border text-critical-text text-xs rounded font-mono">
                {accountError}
              </div>
            )}
          </div>

          {/* Column 3: Research Workspace State */}
          <div className="panel p-6 border border-border-gold-subtle bg-surface-midnight/80 backdrop-blur-md space-y-5">
            <h3 className="text-sm font-semibold uppercase tracking-wider text-text-cream-bright">
              Research State & Storage
            </h3>

            <div className="space-y-3 font-mono text-xs">
              <div className="flex items-center justify-between py-2 border-b border-border-gold-subtle">
                <span className="text-text-cream-muted">Workspace Mode</span>
                <span className="status-badge state-approved text-[10px]">
                  {syncStatus === 'SERVER_SAVED' ? 'SERVER SAVED' : syncStatus}
                </span>
              </div>

              <div className="flex items-center justify-between py-2 border-b border-border-gold-subtle">
                <span className="text-text-cream-muted">Saved Investigations</span>
                <span className="text-text-cream-bright font-bold">
                  {watchlistItems.length} scheme options
                </span>
              </div>

              <div className="flex items-center justify-between py-2 border-b border-border-gold-subtle">
                <span className="text-text-cream-muted">Portfolio Holdings</span>
                <span className="text-text-cream-bright font-bold">
                  {backendAccount?.portfolioHoldingsCount ?? 0} positions
                </span>
              </div>

              <div className="flex items-center justify-between py-2 border-b border-border-gold-subtle">
                <span className="text-text-cream-muted">Privacy Boundary</span>
                <span className="text-positive font-semibold">Strict Tenant Isolation</span>
              </div>
            </div>

            <div className="pt-2 flex flex-col gap-2">
              <Link
                href="/watchlist"
                className="btn btn-secondary text-xs flex items-center justify-center w-full"
              >
                Open Research Workspace →
              </Link>
              <Link
                href="/portfolio"
                className="btn btn-secondary text-xs flex items-center justify-center w-full"
              >
                Open My Portfolio →
              </Link>
            </div>
          </div>
        </div>
      )}

      {/* Commercial Subscription & Entitlement Lifecycle Panel */}
      {user && (
        <div className="panel mb-8 p-6 border border-border-gold-subtle bg-surface-midnight/80 backdrop-blur-md">
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-border-gold-subtle pb-4 mb-5">
            <div>
              <div className="flex items-center gap-2">
                <span className="status-badge state-candidate text-[10px] font-mono">
                  COMMERCIAL STATUS
                </span>
                <h3 className="text-base font-semibold text-text-cream-bright">
                  Commercial Plan & Payment Lifecycle
                </h3>
              </div>
              <p className="text-xs text-text-cream-muted mt-1">
                Authoritative subscription state, payment verification status, and capacity limits.
              </p>
            </div>
            <Link
              href="/pricing"
              className="rounded-full bg-gradient-to-r from-[#d4af37] via-[#f2ca50] to-[#e6be44] px-4 py-1.5 font-sans text-xs font-semibold text-[#1e1704] transition-all hover:scale-[1.02] shrink-0 self-start md:self-auto shadow-sm"
            >
              Browse Plans & Pricing →
            </Link>
          </div>

          {/* Attention Required Banner */}
          {currentSub?.attentionRequired && (
            <div className="panel mb-5 p-4 bg-critical-bg/20 border border-critical-border rounded-lg flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div className="flex items-start gap-3">
                <span className="text-base shrink-0 mt-0.5">⚠️</span>
                <div>
                  <div className="flex items-center gap-2">
                    <span className="status-badge state-rejected text-[10px] font-mono">
                      PAYMENT ATTENTION REQUIRED
                    </span>
                    <h4 className="text-xs font-semibold text-text-cream-bright">
                      Subscription Renewal Issue
                    </h4>
                  </div>
                  <p className="text-xs text-text-cream-muted mt-1 leading-relaxed">
                    A recent renewal charge was unsuccessful. However, in accordance with YUKIRA governance, your Pro access remains <strong>fully active</strong> through {currentSub?.currentPeriodEnd ? new Date(currentSub.currentPeriodEnd).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : 'the current period end'}. No premature access revocation has occurred.
                  </p>
                </div>
              </div>
              <Link href="/pricing" className="btn btn-secondary btn-sm shrink-0 self-start sm:self-auto">
                Update / Retry Payment →
              </Link>
            </div>
          )}

          {/* Expired Subscription Notice */}
          {currentSub?.status === 'EXPIRED' && (
            <div className="panel mb-5 p-4 bg-surface-navy-raised/80 border border-primary/40 rounded-lg flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div className="flex items-start gap-3">
                <span className="text-base shrink-0 mt-0.5">ℹ️</span>
                <div>
                  <div className="flex items-center gap-2">
                    <span className="status-badge state-rejected text-[10px] font-mono">
                      PAID PERIOD CONCLUDED
                    </span>
                    <h4 className="text-xs font-semibold text-text-cream-bright">
                      Account Transitioned to Community Explorer
                    </h4>
                  </div>
                  <p className="text-xs text-text-cream-muted mt-1 leading-relaxed">
                    Your previous {currentSub.planName} subscription completed its paid duration on {currentSub.currentPeriodEnd ? new Date(currentSub.currentPeriodEnd).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : 'period end'}. <strong>Research Preservation Guarantee:</strong> All your saved mutual funds, analytical questions, and portfolio holdings remain intact without any data deletion.
                  </p>
                </div>
              </div>
              <Link href="/pricing" className="btn btn-primary btn-sm shrink-0 self-start sm:self-auto">
                Renew Pro Access →
              </Link>
            </div>
          )}

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 text-xs font-mono mb-5">
            <div className="panel p-3.5 bg-surface-navy-raised/40 border border-border-gold-subtle">
              <span className="text-[10px] uppercase tracking-wider text-text-cream-muted block">
                Active Plan
              </span>
              <span className="text-text-cream-bright font-semibold mt-1 block">
                {currentSub?.planName || backendAccount?.planName || 'Community Explorer'}
              </span>
              <span className="text-[10px] text-text-cream-muted mt-0.5 block">
                {currentSub?.billingInterval || 'FREE'} billing • {currentSub?.entitlementStatus || 'STANDARD'}
              </span>
            </div>

            <div className="panel p-3.5 bg-surface-navy-raised/40 border border-border-gold-subtle">
              <span className="text-[10px] uppercase tracking-wider text-text-cream-muted block">
                Lifecycle Status
              </span>
              <div className="flex items-center gap-1.5 mt-1">
                <span className={`status-badge text-[10px] ${
                  currentSub?.status === 'ACTIVE'
                    ? 'state-approved'
                    : currentSub?.status === 'PAST_DUE'
                    ? 'state-candidate'
                    : currentSub?.status === 'EXPIRED'
                    ? 'state-rejected'
                    : currentSub?.status === 'CHECKOUT_INITIATED'
                    ? 'state-candidate'
                    : 'state-candidate'
                }`}>
                  {currentSub?.status === 'PAST_DUE' ? 'PAST DUE (ACTIVE)' : currentSub?.status || backendAccount?.subscriptionStatus || 'ACTIVE'}
                </span>
              </div>
              <span className="text-[10px] text-text-cream-muted mt-0.5 block">
                Payment: {currentSub?.paymentStatus || 'PAID'}
              </span>
            </div>

            <div className="panel p-3.5 bg-surface-navy-raised/40 border border-border-gold-subtle">
              <span className="text-[10px] uppercase tracking-wider text-text-cream-muted block">
                Research Workspace Quota
              </span>
              <span className="text-text-cream-bright font-semibold mt-1 block">
                {currentSub?.entitlementStatus === 'ACTIVE_PRO' ? 'Unlimited' : '25 Saved Funds'}
              </span>
              <span className="text-[10px] text-text-cream-muted mt-0.5 block">
                {watchlistItems.length} currently saved
              </span>
            </div>

            <div className="panel p-3.5 bg-surface-navy-raised/40 border border-border-gold-subtle">
              <span className="text-[10px] uppercase tracking-wider text-text-cream-muted block">
                Billing Period End
              </span>
              <span className="text-text-cream-bright mt-1 block">
                {currentSub?.currentPeriodEnd
                  ? new Date(currentSub.currentPeriodEnd).toLocaleDateString('en-IN', {
                      day: 'numeric',
                      month: 'short',
                      year: 'numeric',
                    })
                  : 'Continuous Free'}
              </span>
              <span className="text-[10px] text-text-cream-muted mt-0.5 block">
                {currentSub?.status === 'EXPIRED'
                  ? 'Expired'
                  : currentSub?.cancelAtPeriodEnd
                  ? 'Cancels at period end'
                  : currentSub?.status === 'PAST_DUE'
                  ? 'Renewal past due'
                  : 'Auto-renews when enabled'}
              </span>
            </div>
          </div>

          {/* Period-End Behavior & Next Action Clarity */}
          {(currentSub?.periodEndBehavior || currentSub?.nextAction) && (
            <div className="panel p-3.5 mb-5 bg-surface-navy-raised/30 border border-border-gold-subtle text-xs font-mono space-y-2">
              {currentSub.periodEndBehavior && (
                <div className="flex items-start gap-2">
                  <span className="text-primary font-bold shrink-0">Access Horizon:</span>
                  <span className="text-text-cream-muted">{currentSub.periodEndBehavior}</span>
                </div>
              )}
              {currentSub.nextAction && (
                <div className="flex items-start gap-2">
                  <span className="text-accent font-bold shrink-0">Recommended Action:</span>
                  <span className="text-text-cream-bright">{currentSub.nextAction}</span>
                </div>
              )}
            </div>
          )}

          {actionMessage && (
            <div className={`mb-4 p-3 rounded text-xs font-mono border ${
              actionMessage.type === 'success'
                ? 'bg-positive/10 border-positive/30 text-positive'
                : actionMessage.type === 'info'
                ? 'bg-primary/10 border-primary/30 text-primary'
                : 'bg-critical-bg/20 border-critical-border text-critical-text'
            }`}>
              {actionMessage.text}
            </div>
          )}

          {/* Subscription Self-Service Action Bar */}
          {user && currentSub && (
            <div className="pt-3 border-t border-border-gold-subtle flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs">
              <div className="text-text-cream-muted">
                {currentSub.planId === 'FREE' || currentSub.status === 'EXPIRED' ? (
                  <span>Your account is on the Community Explorer tier. You can upgrade or renew anytime to unlock unlimited saved fund research.</span>
                ) : currentSub.cancelAtPeriodEnd ? (
                  <span className="text-primary font-mono">
                    Cancellation scheduled for {currentSub.currentPeriodEnd ? new Date(currentSub.currentPeriodEnd).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : 'cycle end'}. No further renewal charges will occur.
                  </span>
                ) : (
                  <span className="text-text-cream-muted">
                    Recurring subscription active. Renews automatically on {currentSub.currentPeriodEnd ? new Date(currentSub.currentPeriodEnd).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : 'scheduled period end'}.
                  </span>
                )}
              </div>

              <div className="flex items-center gap-2 shrink-0">
                {currentSub.canReactivate && (
                  <button
                    type="button"
                    disabled={actionLoading}
                    onClick={handleReactivate}
                    className="btn btn-primary text-xs"
                  >
                    {actionLoading ? 'Processing…' : 'Resume Auto-Renewal'}
                  </button>
                )}

                {currentSub.planId !== 'FREE' && !currentSub.cancelAtPeriodEnd && (currentSub.status === 'ACTIVE' || currentSub.status === 'PAST_DUE') && (
                  <button
                    type="button"
                    disabled={actionLoading}
                    onClick={() => setShowCancelModal(true)}
                    className="btn btn-secondary text-xs text-text-cream-muted hover:text-critical-text hover:border-critical-border"
                  >
                    Cancel Subscription
                  </button>
                )}

                <Link
                  href="/pricing"
                  className="btn btn-secondary text-xs"
                >
                  {currentSub.planId === 'FREE' ? 'Upgrade Plan' : currentSub.status === 'EXPIRED' ? 'Renew Plan' : 'Change Plan'}
                </Link>
              </div>
            </div>
          )}

          {currentSub && !currentSub.providerConfigured && currentSub.planId !== 'FREE' && (
            <div className="mt-4 panel p-3.5 bg-surface-raised/60 border border-primary/30 rounded text-xs text-text-cream-muted font-mono leading-relaxed">
              <div className="flex items-start gap-2.5">
                <span className="text-primary text-sm">ℹ</span>
                <div>
                  <strong className="text-text-cream-bright">Commercial Gateway Notice:</strong> Razorpay payment gateway credentials are not yet configured in this deployment. In accordance with YUKIRA governance, checkout cannot proceed and simulated payments are strictly prohibited.
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Billing History & Invoice Records Panel */}
      {user && (
        <div className="panel mb-8 p-6 border border-border-gold-subtle bg-surface-midnight/80 backdrop-blur-md">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-border-gold-subtle pb-4 mb-4">
            <div>
              <div className="flex items-center gap-2">
                <span className="status-badge state-approved text-[10px] font-mono">
                  LEDGER RECORDS
                </span>
                <h3 className="text-base font-semibold text-text-cream-bright">
                  Billing History & Invoice Records
                </h3>
              </div>
              <p className="text-xs text-text-cream-muted mt-1">
                Immutable record of payment transactions, provider order references, and verified tax invoices.
              </p>
            </div>
          </div>

          {billingLoading ? (
            <div className="py-8 text-center font-mono text-xs text-text-cream-muted">
              Loading verified billing history…
            </div>
          ) : billingRecords.length === 0 ? (
            <div className="panel p-6 bg-surface-navy-raised/30 border border-border-gold-subtle text-center space-y-2">
              <p className="text-xs font-semibold text-text-cream-bright">
                No billing transactions recorded yet.
              </p>
              <p className="text-xs text-text-cream-muted max-w-lg mx-auto leading-relaxed">
                Your account is on the complimentary Community Explorer tier. When you upgrade to Institutional Research Pro or renew subscriptions, authoritative transaction records and verified invoice documents will appear here.
              </p>
              <div className="pt-2">
                <Link href="/pricing" className="btn btn-secondary text-xs inline-block">
                  View Subscription Plans →
                </Link>
              </div>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs font-mono">
                <thead>
                  <tr className="border-b border-border-gold-subtle text-[11px] uppercase tracking-wider text-text-cream-muted">
                    <th className="py-2.5 px-3">Date</th>
                    <th className="py-2.5 px-3">Plan / Description</th>
                    <th className="py-2.5 px-3">Provider Reference</th>
                    <th className="py-2.5 px-3 text-right">Amount</th>
                    <th className="py-2.5 px-3 text-center">Payment Status</th>
                    <th className="py-2.5 px-3 text-right">Invoice Document</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border-gold-subtle/40">
                  {billingRecords.map((record) => (
                    <tr key={record.id} className="hover:bg-surface-navy-raised/20 transition-colors">
                      <td className="py-3 px-3 text-text-cream-bright whitespace-nowrap">
                        {new Date(record.createdAt).toLocaleDateString('en-IN', {
                          day: 'numeric',
                          month: 'short',
                          year: 'numeric',
                          hour: '2-digit',
                          minute: '2-digit',
                        })}
                      </td>
                      <td className="py-3 px-3">
                        <span className="font-semibold text-text-cream-bright block">
                          {record.planName}
                        </span>
                        <span className="text-[10px] text-text-cream-muted block">
                          {record.billingReason}
                        </span>
                      </td>
                      <td className="py-3 px-3 text-text-cream-muted font-mono text-[11px]">
                        {record.providerPaymentId ? (
                          <span className="text-text-cream-bright">{record.providerPaymentId}</span>
                        ) : record.providerOrderId ? (
                          <span>{record.providerOrderId}</span>
                        ) : (
                          <span>—</span>
                        )}
                      </td>
                      <td className="py-3 px-3 text-right font-semibold text-text-cream-bright whitespace-nowrap">
                        ₹{record.amountInr.toLocaleString('en-IN')}
                      </td>
                      <td className="py-3 px-3 text-center whitespace-nowrap">
                        <span className={`status-badge text-[10px] ${
                          record.paymentStatus === 'PAID'
                            ? 'state-approved'
                            : record.paymentStatus === 'FAILED'
                            ? 'state-rejected'
                            : 'state-candidate'
                        }`}>
                          {record.paymentStatus}
                        </span>
                        {record.failureReason && (
                          <span className="block text-[9px] text-critical-text mt-0.5 max-w-xs truncate mx-auto" title={record.failureReason}>
                            {record.failureReason}
                          </span>
                        )}
                      </td>
                      <td className="py-3 px-3 text-right whitespace-nowrap">
                        {record.invoiceAvailable ? (
                          <button
                            type="button"
                            onClick={() => handleInvoiceClick(record)}
                            className="btn btn-secondary text-[11px] py-1 px-2.5"
                          >
                            📄 View Invoice
                          </button>
                        ) : (
                          <button
                            type="button"
                            onClick={() => handleInvoiceClick(record)}
                            className="text-[11px] text-text-cream-muted underline hover:text-text-cream-bright transition-colors"
                            title="Statutory invoice requirements"
                          >
                            Unavailable (Details)
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="mt-4 pt-3 border-t border-border-gold-subtle/50 text-[11px] text-text-cream-muted font-mono">
            <span>💡 <strong>Research Preservation Guarantee:</strong> Subscription changes, cancellations, or payment failures never delete your saved mutual fund investigations, research notes, or custom portfolios.</span>
          </div>
        </div>
      )}

      {/* Account Workflow & Onboarding Management */}
      <div className="panel mb-8 p-6 border border-border-gold-subtle bg-surface-midnight/80 backdrop-blur-md">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="space-y-1">
            <h3 className="text-sm font-semibold text-text-cream-bright">
              Investor Onboarding & Workflow Guidance
            </h3>
            <p className="text-xs text-text-cream-muted">
              YUKIRA provides a 6-step walkthrough for mutual fund discovery, return & risk evidence inspection, comparative screening, and due-diligence retention.
            </p>
          </div>

          <button
            type="button"
            onClick={handleRestartOnboarding}
            className="btn btn-secondary text-xs shrink-0 self-start sm:self-auto"
          >
            {showOnboarding ? 'Onboarding Guide Active' : 'Restart Onboarding Guide'}
          </button>
        </div>
      </div>

      {/* Epistemic & Account Integrity Disclosures */}
      <div className="panel p-5 border border-border bg-surface-raised/40 text-xs text-text-cream-muted space-y-3 font-mono leading-relaxed">
        <div className="flex items-start gap-2.5">
          <span className="text-accent text-sm">🛡️</span>
          <div>
            <h4 className="font-semibold text-text-cream-bright mb-1">
              Account, Privacy, and Calculation Governance Disclosures
            </h4>
            <ul className="list-disc list-inside space-y-1">
              <li>
                <strong>Zero Financial Math in Presentation Tier:</strong> All metric values (CAGRs, rolling returns, volatilities, capture ratios) originate from deterministic algorithms in the backend and quant kernel.
              </li>
              <li>
                <strong>Independence from Account Status:</strong> Authenticated account creation confers identity persistence and multi-device research synchronization. It does not alter calculations, enhance data quality, or validate candidate methodologies.
              </li>
              <li>
                <strong>Strict Tenant Isolation:</strong> Saved investigations and private hypotheses are cryptographically isolated by your authenticated subject ID. Research notes are never aggregated or disclosed to third parties.
              </li>
              <li>
                <strong>No Unsolicited Recommendations:</strong> YUKIRA does not offer buy, sell, or hold advice. Research workspaces exist solely to assist allocators in structured due diligence before committing capital.
              </li>
            </ul>
          </div>
        </div>
      </div>

      {/* Cancellation Confirmation Modal */}
      {showCancelModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
          <div className="panel max-w-md w-full p-6 border border-border-gold-subtle bg-surface-midnight text-left space-y-4 shadow-xl">
            <div className="flex items-center justify-between border-b border-border-gold-subtle pb-3">
              <h3 className="text-base font-semibold text-text-cream-bright">
                Cancel Subscription Auto-Renewal
              </h3>
              <button
                type="button"
                onClick={() => setShowCancelModal(false)}
                className="text-text-cream-muted hover:text-text-cream-bright text-lg leading-none"
              >
                ✕
              </button>
            </div>

            <div className="space-y-3 text-xs leading-relaxed text-text-cream-muted font-sans">
              <p>
                Are you sure you want to cancel automatic renewal for your <strong className="text-text-cream-bright">{currentSub?.planName}</strong> plan?
              </p>

              <div className="panel p-3 bg-surface-navy-raised/50 border border-border-gold-subtle space-y-1.5 font-mono text-[11px]">
                <div className="text-primary font-semibold">
                  What to expect upon cancellation:
                </div>
                <ul className="list-disc list-inside space-y-1 text-text-cream-muted">
                  <li>Your Pro access remains <strong className="text-text-cream-bright">fully active</strong> through {currentSub?.currentPeriodEnd ? new Date(currentSub.currentPeriodEnd).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : 'the current period'}.</li>
                  <li>No further renewal charges will be initiated.</li>
                  <li><strong className="text-text-cream-bright">Zero Data Loss:</strong> Your saved research notes, fund watchlists, and portfolio models will remain intact.</li>
                  <li>After period end, your account will smoothly revert to Community Explorer (with a 25 saved funds quota).</li>
                </ul>
              </div>

              <div>
                <label htmlFor="cancelReasonInput" className="block text-[11px] font-mono uppercase tracking-wider text-text-cream-muted mb-1">
                  Reason for cancelling (optional):
                </label>
                <textarea
                  id="cancelReasonInput"
                  value={cancelReason}
                  onChange={(e) => setCancelReason(e.target.value)}
                  placeholder="Let us know how we can improve our quant research tools..."
                  className="w-full bg-surface-navy-raised border border-border-gold-subtle rounded p-2 text-xs text-text-cream-bright focus:outline-none focus:border-primary resize-none h-20"
                />
              </div>
            </div>

            <div className="flex items-center justify-end gap-3 pt-2 border-t border-border-gold-subtle">
              <button
                type="button"
                disabled={actionLoading}
                onClick={() => setShowCancelModal(false)}
                className="btn btn-secondary text-xs"
              >
                Keep Subscription
              </button>
              <button
                type="button"
                disabled={actionLoading}
                onClick={handleConfirmCancel}
                className="btn btn-primary text-xs bg-critical-border hover:bg-critical-border/80 border-critical-border text-text-cream-bright"
              >
                {actionLoading ? 'Scheduling…' : 'Confirm Cancellation'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Statutory Invoice Status Modal */}
      {invoiceStatusModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
          <div className="panel max-w-md w-full p-6 border border-border-gold-subtle bg-surface-midnight text-left space-y-4 shadow-xl">
            <div className="flex items-center justify-between border-b border-border-gold-subtle pb-3">
              <div className="flex items-center gap-2">
                <span className="text-primary">📄</span>
                <h3 className="text-sm font-semibold text-text-cream-bright">
                  Statutory Invoice Disclosure
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setInvoiceStatusModal(null)}
                className="text-text-cream-muted hover:text-text-cream-bright text-lg leading-none"
              >
                ✕
              </button>
            </div>

            <div className="space-y-3 text-xs leading-relaxed text-text-cream-muted">
              <div className="panel p-3 bg-surface-navy-raised/50 border border-border-gold-subtle font-mono text-[11px] space-y-1">
                <div><span className="text-text-cream-muted">Transaction ID:</span> <span className="text-text-cream-bright">#{invoiceStatusModal.record.id}</span></div>
                <div><span className="text-text-cream-muted">Plan:</span> <span className="text-text-cream-bright">{invoiceStatusModal.record.planName}</span></div>
                <div><span className="text-text-cream-muted">Provider Reference:</span> <span className="text-text-cream-bright">{invoiceStatusModal.record.providerPaymentId || invoiceStatusModal.record.providerOrderId || 'N/A'}</span></div>
                <div><span className="text-text-cream-muted">Amount:</span> <span className="text-text-cream-bright">₹{invoiceStatusModal.record.amountInr.toLocaleString('en-IN')}</span></div>
              </div>

              <p>
                {invoiceStatusModal.message}
              </p>

              <div className="panel p-3 bg-surface-raised/40 border border-border text-[11px] font-mono space-y-1">
                <span className="text-primary font-semibold block">Epistemic Accounting Standard:</span>
                <p className="text-text-cream-muted">
                  In compliance with YUKIRA governance, the system refuses to generate simulated or placeholder tax invoices. Official GST invoices are only issued once verified merchant GSTIN and Razorpay Invoices integration are active.
                </p>
              </div>
            </div>

            <div className="flex justify-end pt-2 border-t border-border-gold-subtle">
              <button
                type="button"
                onClick={() => setInvoiceStatusModal(null)}
                className="btn btn-secondary text-xs"
              >
                Understood
              </button>
            </div>
          </div>
        </div>
      )}
    </PageContainer>
  );
}
