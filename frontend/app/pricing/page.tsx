'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { useUser } from '@auth0/nextjs-auth0/client';
import { PageContainer } from '@/components/layout/PageContainer';
import {
  SubscriptionPlan,
  InvestorSubscription,
  fetchSubscriptionPlans,
  fetchCurrentSubscription,
  initiateSubscriptionCheckout,
  verifySubscriptionPayment,
  CheckoutInitiateResponse,
} from '@/lib/api/subscription';
import { formatCurrency } from '@/lib/utils/formatters';

declare global {
  interface Window {
    Razorpay?: new (options: Record<string, unknown>) => {
      open: () => void;
    };
  }
}

export default function PricingPage() {
  const { user, isLoading: authLoading } = useUser();

  const [plans, setPlans] = useState<SubscriptionPlan[]>([]);
  const [currentSub, setCurrentSub] = useState<InvestorSubscription | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Checkout interaction states
  const [selectedPlanId, setSelectedPlanId] = useState<string | null>(null);
  const [checkoutStatus, setCheckoutStatus] = useState<
    'IDLE' | 'INITIATING' | 'UNCONFIGURED' | 'PENDING' | 'SUCCESS' | 'FAILED'
  >('IDLE');
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function loadData() {
      try {
        setLoading(true);
        const plansData = await fetchSubscriptionPlans();
        if (!active) return;
        setPlans(plansData);

        if (user) {
          try {
            const subData = await fetchCurrentSubscription();
            if (active) setCurrentSub(subData);
          } catch {
            // Unauthenticated or profile initialization fallback
          }
        }
      } catch (err) {
        if (active) {
          setError(err instanceof Error ? err.message : 'Unable to load subscription plans.');
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    loadData();

    return () => {
      active = false;
    };
  }, [user]);

  const handleSelectPlan = async (plan: SubscriptionPlan) => {
    setSelectedPlanId(plan.id);
    setStatusMessage(null);

    // Guest investors must authenticate before initiating subscription or account persistence
    if (!user) {
      return;
    }

    try {
      setCheckoutStatus('INITIATING');
      const response: CheckoutInitiateResponse = await initiateSubscriptionCheckout(plan.id);

      if (response.status === 'PROVIDER_UNCONFIGURED') {
        setCheckoutStatus('UNCONFIGURED');
        setStatusMessage(
          response.message ||
            'Commercial checkout gateway pending merchant KYC & API configuration. Live checkout cannot be completed until corporate credentials are provided.'
        );
        return;
      }

      if (response.status === 'SUCCESS') {
        if (plan.priceInr === 0) {
          setCheckoutStatus('SUCCESS');
          setStatusMessage('Switched to Community Explorer (Free) plan.');
          const updated = await fetchCurrentSubscription();
          setCurrentSub(updated);
          return;
        }

        // Live Razorpay Hosted Modal Flow
        if (typeof window !== 'undefined' && window.Razorpay && response.orderId && response.keyId) {
          const options = {
            key: response.keyId,
            amount: response.amountPaise,
            currency: response.currency || 'INR',
            name: 'YUKIRA Investment Intelligence',
            description: plan.name,
            order_id: response.orderId,
            handler: async function (paymentRes: {
              razorpay_order_id: string;
              razorpay_payment_id: string;
              razorpay_signature: string;
            }) {
              setCheckoutStatus('PENDING');
              try {
                const verifiedSub = await verifySubscriptionPayment({
                  orderId: paymentRes.razorpay_order_id,
                  paymentId: paymentRes.razorpay_payment_id,
                  signature: paymentRes.razorpay_signature,
                });
                setCurrentSub(verifiedSub);
                setCheckoutStatus('SUCCESS');
                setStatusMessage('Payment verified successfully! Institutional Research Pro access active.');
              } catch (verifyErr) {
                setCheckoutStatus('FAILED');
                setStatusMessage(verifyErr instanceof Error ? verifyErr.message : 'Cryptographic payment verification failed.');
              }
            },
            modal: {
              ondismiss: function () {
                setCheckoutStatus('IDLE');
              },
            },
          };

          const rzp = new window.Razorpay(options);
          rzp.open();
        } else {
          setCheckoutStatus('UNCONFIGURED');
          setStatusMessage(
            'Razorpay SDK script or credentials not loaded. Live payment cannot be completed in this environment without active merchant onboarding.'
          );
        }
      } else {
        setCheckoutStatus('FAILED');
        setStatusMessage(response.message || 'Failed to initiate checkout session.');
      }
    } catch (checkoutErr) {
      setCheckoutStatus('FAILED');
      setStatusMessage(checkoutErr instanceof Error ? checkoutErr.message : 'Failed to initiate checkout.');
    }
  };

  return (
    <PageContainer
      title="Transparent Plans & Institutional Subscriptions"
      subtitle="Neutral quantitative intelligence, point-in-time calculation provenance, and expanded research workspace capacity."
      breadcrumbs={[
        { label: 'Home', href: '/' },
        { label: 'Pricing & Plans', href: '/pricing' },
      ]}
      action={
        user ? (
          <Link href="/account" className="btn btn-secondary text-xs">
            View My Account →
          </Link>
        ) : (
          <a
            href="/auth/login?returnTo=/pricing"
            className="rounded-full bg-gradient-to-r from-[#d4af37] via-[#f2ca50] to-[#e6be44] px-4 py-1.5 font-sans text-xs font-semibold text-[#1e1704] transition-all hover:scale-[1.02]"
          >
            Sign In to Subscribe
          </a>
        )
      }
    >
      {/* Checkout Status & Epistemic Gateway Alert */}
      {currentSub?.attentionRequired && (
        <div className="panel mb-6 p-4 bg-critical-bg/20 border border-critical-border rounded-lg flex flex-col md:flex-row md:items-center justify-between gap-3">
          <div className="flex items-start gap-3">
            <span className="text-sm shrink-0 mt-0.5">⚠️</span>
            <div>
              <div className="flex items-center gap-2">
                <span className="status-badge state-rejected text-[10px] font-mono">
                  PAYMENT ATTENTION REQUIRED
                </span>
                <h3 className="text-xs font-semibold text-text-cream-bright">
                  Recent Renewal Payment Unsuccessful
                </h3>
              </div>
              <p className="text-xs text-text-cream-muted mt-1 leading-relaxed">
                {currentSub.periodEndBehavior ||
                  'A recent renewal charge failed. Your Pro entitlement remains active through the end of your prepaid period. Please update your payment method or review billing history.'}
              </p>
            </div>
          </div>
          <Link href="/account" className="btn btn-secondary btn-sm self-start md:self-auto shrink-0">
            View Billing & Account →
          </Link>
        </div>
      )}

      {checkoutStatus === 'UNCONFIGURED' && (
        <div className="panel mb-6 p-4 bg-surface-navy-raised/90 border border-primary/40 rounded-lg flex flex-col md:flex-row md:items-center justify-between gap-3">
          <div className="flex items-start gap-3">
            <span className="flex h-7 w-7 items-center justify-center rounded-full bg-primary/20 text-xs font-bold text-primary shrink-0 mt-0.5">
              ⚖️
            </span>
            <div>
              <div className="flex items-center gap-2">
                <span className="status-badge state-candidate text-[10px] font-mono">
                  GATEWAY UNCONFIGURED
                </span>
                <h3 className="text-xs font-semibold text-text-cream-bright">
                  Commercial Provider Status Notice
                </h3>
              </div>
              <p className="text-xs text-text-cream-muted mt-1 leading-relaxed">
                {statusMessage}
              </p>
            </div>
          </div>
          <button
            onClick={() => setCheckoutStatus('IDLE')}
            className="btn btn-secondary btn-sm self-start md:self-auto shrink-0"
          >
            Acknowledge ✕
          </button>
        </div>
      )}

      {checkoutStatus === 'SUCCESS' && (
        <div className="panel mb-6 p-4 bg-positive/15 border border-positive/30 rounded-lg flex items-center justify-between gap-3">
          <div className="flex items-center gap-2.5">
            <span className="status-badge state-approved text-[10px] font-mono">CONFIRMED</span>
            <span className="text-xs text-text-cream-bright font-medium">{statusMessage}</span>
          </div>
          <Link href="/account" className="btn btn-secondary btn-sm">
            Go to Account →
          </Link>
        </div>
      )}

      {checkoutStatus === 'FAILED' && (
        <div className="panel mb-6 p-4 bg-critical-bg/20 border border-critical-border rounded-lg flex items-center justify-between gap-3">
          <div className="flex items-center gap-2.5">
            <span className="status-badge state-rejected text-[10px] font-mono">PAYMENT NOTICE</span>
            <span className="text-xs text-critical-text">{statusMessage}</span>
          </div>
          <button onClick={() => setCheckoutStatus('IDLE')} className="btn btn-secondary btn-sm">
            Dismiss
          </button>
        </div>
      )}

      {/* Guest Exploration Banner */}
      {!user && (
        <div className="panel mb-6 p-4 bg-surface-raised/40 border border-border-gold-subtle rounded-lg flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <span className="status-badge state-candidate text-[10px] font-mono">PUBLIC PREVIEW</span>
              <span className="text-xs font-semibold text-text-cream-bright">
                Guest Mode: Free Access Available Immediately
              </span>
            </div>
            <p className="text-xs text-text-cream-muted">
              You can explore India's mutual fund universe, inspect institutional return profiles, and evaluate evidence without an account. Authenticate to persist your research across devices or upgrade capacity.
            </p>
          </div>
          <a
            href="/auth/login?returnTo=/pricing"
            className="rounded-full bg-surface-navy-raised border border-border-gold-subtle hover:border-border-gold-bright px-4 py-1.5 text-xs font-semibold text-text-cream-bright shrink-0 text-center"
          >
            Sign In / Register
          </a>
        </div>
      )}

      {/* Pricing Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
        {plans.map((plan) => {
          const isFree = plan.priceInr === 0;
          const isAnnual = plan.billingInterval === 'ANNUAL';
          const isProEntitled = currentSub?.entitlementStatus === 'ACTIVE_PRO';
          const isCurrent = isFree ? !isProEntitled : (isProEntitled && currentSub?.planId === plan.id);
          const isExpiredOnThisPlan = !isProEntitled && currentSub?.planId === plan.id && currentSub?.status === 'EXPIRED';
          const isCancelling = isCurrent && !!currentSub?.cancelAtPeriodEnd;

          return (
            <div
              key={plan.id}
              className={`panel flex flex-col justify-between p-6 border rounded-xl transition-all relative ${
                isCurrent
                  ? 'border-primary bg-surface-midnight shadow-lg glow-gold-sm'
                  : 'border-border-gold-subtle bg-surface-midnight/80 hover:border-border-gold-bright'
              }`}
            >
              {isCurrent && !isCancelling && (
                <span className="absolute -top-3 left-6 rounded-full bg-primary px-3 py-0.5 font-mono text-[10px] font-bold uppercase tracking-wider text-[#1e1704]">
                  Active Plan
                </span>
              )}

              {isCancelling && (
                <span className="absolute -top-3 left-6 rounded-full bg-primary/20 border border-primary/50 px-3 py-0.5 font-mono text-[10px] font-bold uppercase tracking-wider text-primary">
                  Cancelling at Period End
                </span>
              )}

              {isExpiredOnThisPlan && (
                <span className="absolute -top-3 left-6 rounded-full bg-surface-navy-raised border border-critical-border px-2.5 py-0.5 font-mono text-[10px] font-bold text-critical-text">
                  Previously Subscribed (Expired)
                </span>
              )}

              {isAnnual && !isCurrent && (
                <span className="absolute -top-3 right-6 rounded-full bg-surface-navy-raised border border-border-gold-bright px-2.5 py-0.5 font-mono text-[10px] font-bold text-primary">
                  Save ~17% (2 Months Free)
                </span>
              )}

              <div>
                <div className="border-b border-border-gold-subtle/60 pb-4 mb-4">
                  <span className="font-mono text-[10px] uppercase tracking-wider text-primary font-semibold block">
                    {plan.billingInterval} ACCESS
                  </span>
                  <h3 className="text-lg font-bold text-text-cream-bright mt-1">
                    {plan.name}
                  </h3>
                  <p className="text-xs text-text-cream-muted mt-2 leading-relaxed">
                    {plan.description}
                  </p>
                </div>

                {/* Price Display */}
                <div className="mb-6">
                  <div className="flex items-baseline gap-1">
                    <span className="text-2xl font-bold font-mono text-text-cream-bright">
                      {isFree ? '₹0' : formatCurrency(plan.priceInr)}
                    </span>
                    <span className="text-xs font-mono text-text-cream-muted">
                      {isFree ? '/ forever' : isAnnual ? '/ year' : '/ month'}
                    </span>
                  </div>
                  <span className="text-[10px] text-text-cream-muted font-mono block mt-1">
                    {isFree ? 'Complimentary public research access' : 'Excl. applicable GST'}
                  </span>
                </div>

                {/* Capabilities Matrix */}
                <div className="space-y-2.5 mb-6 text-xs">
                  <span className="font-mono text-[10px] uppercase tracking-wider text-text-cream-muted block font-semibold">
                    Included Capabilities
                  </span>

                  <div className="flex items-center gap-2 text-text-cream-body">
                    <span className="text-positive text-sm">✓</span>
                    <span>All AMFI Direct-Growth schemes catalog</span>
                  </div>

                  <div className="flex items-center gap-2 text-text-cream-body">
                    <span className="text-positive text-sm">✓</span>
                    <span>Point-in-time Evidence Explorer & metrics</span>
                  </div>

                  <div className="flex items-center gap-2 text-text-cream-body">
                    <span className="text-positive text-sm">✓</span>
                    <span>Deterministic Comparative Screener</span>
                  </div>

                  <div className="flex items-center gap-2 text-text-cream-body">
                    <span className="text-positive text-sm">✓</span>
                    <span>
                      Research Workspace:{' '}
                      <strong className="text-text-cream-bright font-mono">
                        {plan.maxSavedFunds > 500 ? 'Unlimited' : `${plan.maxSavedFunds} funds`}
                      </strong>
                    </span>
                  </div>

                  <div className="flex items-center gap-2 text-text-cream-body">
                    <span className="text-positive text-sm">✓</span>
                    <span>
                      Tracked Holdings:{' '}
                      <strong className="text-text-cream-bright font-mono">
                        Up to {plan.maxHoldingsPerPortfolio} positions
                      </strong>
                    </span>
                  </div>

                  <div className="flex items-center gap-2 text-text-cream-body">
                    <span className={plan.canImportCas ? 'text-positive text-sm' : 'text-text-cream-muted text-sm'}>
                      {plan.canImportCas ? '✓' : '—'}
                    </span>
                    <span className={plan.canImportCas ? 'text-text-cream-body' : 'text-text-cream-muted'}>
                      CAS CAMS / KFintech statement import
                    </span>
                  </div>

                  <div className="flex items-center gap-2 text-text-cream-body">
                    <span className={plan.canAccessApi ? 'text-positive text-sm' : 'text-text-cream-muted text-sm'}>
                      {plan.canAccessApi ? '✓' : '—'}
                    </span>
                    <span className={plan.canAccessApi ? 'text-text-cream-body' : 'text-text-cream-muted'}>
                      Public Developer REST API ({plan.apiRateLimitPerMin} req/min)
                    </span>
                  </div>
                </div>
              </div>

              {/* Plan Action Button */}
              <div className="pt-4 border-t border-border-gold-subtle/60">
                {!user ? (
                  <a
                    href={`/auth/login?returnTo=/pricing`}
                    className="btn btn-secondary text-xs w-full text-center block"
                  >
                    Sign In to Choose
                  </a>
                ) : isCurrent ? (
                  isCancelling ? (
                    <div className="space-y-2">
                      <div className="text-center font-mono text-[11px] text-primary font-semibold">
                        Active until {currentSub?.currentPeriodEnd ? new Date(currentSub.currentPeriodEnd).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' }) : 'period end'}
                      </div>
                      <Link
                        href="/account"
                        className="btn btn-primary text-xs w-full text-center block"
                      >
                        Manage / Resume Renewal →
                      </Link>
                    </div>
                  ) : (
                    <div className="text-center font-mono text-xs text-positive font-semibold py-2">
                      Current Plan
                    </div>
                  )
                ) : isExpiredOnThisPlan ? (
                  <button
                    type="button"
                    onClick={() => handleSelectPlan(plan)}
                    disabled={checkoutStatus === 'INITIATING'}
                    className="w-full rounded-full py-2 px-4 font-sans text-xs font-semibold bg-gradient-to-r from-[#d4af37] via-[#f2ca50] to-[#e6be44] text-[#1e1704] hover:scale-[1.02] shadow-sm transition-all"
                  >
                    Renew Plan
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={() => handleSelectPlan(plan)}
                    disabled={checkoutStatus === 'INITIATING'}
                    className={`w-full rounded-full py-2 px-4 font-sans text-xs font-semibold transition-all ${
                      isFree
                        ? 'btn btn-secondary'
                        : 'bg-gradient-to-r from-[#d4af37] via-[#f2ca50] to-[#e6be44] text-[#1e1704] hover:scale-[1.02] shadow-sm'
                    }`}
                  >
                    {isFree
                      ? 'Switch to Community'
                      : isProEntitled
                      ? plan.billingInterval === 'ANNUAL'
                        ? 'Switch to Annual (Save ~17%)'
                        : 'Switch to Monthly'
                      : 'Upgrade to Pro'}
                  </button>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {/* Epistemic & Commercial Governance Disclosures */}
      <div className="panel p-5 border border-border bg-surface-raised/40 text-xs text-text-cream-muted space-y-3 font-mono leading-relaxed">
        <div className="flex items-start gap-2.5">
          <span className="text-accent text-sm">🛡️</span>
          <div>
            <h4 className="font-semibold text-text-cream-bright mb-1">
              Commercial Policy, Quantitative Neutrality, and Governance Rules
            </h4>
            <ul className="list-disc list-inside space-y-1">
              <li>
                <strong>Absolute Separation of Commercials and Math:</strong> Payment or subscription status has ZERO impact on financial metrics, returns calculations, risk ratios, or methodology governance. All algorithms execute identically across every tier.
              </li>
              <li>
                <strong>No Pay-to-Win Ratings:</strong> Funds cannot sponsor rankings, buy visibility, or influence YUKIRA scores. We do not provide financial advice, broker commissions, or distribution marketing.
              </li>
              <li>
                <strong>Research Safety Guarantee:</strong> A subscription downgrade or expiration NEVER deletes your private due-diligence questions, notes, or saved investigations. Records remain preserved point-in-time.
              </li>
              <li>
                <strong>Merchant Integration Status:</strong> Live payment settlement via Razorpay requires corporate merchant KYC authorization under Reserve Bank of India (RBI) recurring mandate regulations. In testing environments without live keys, checkout reports gateway unconfigured rather than simulating fake transactions.
              </li>
            </ul>
          </div>
        </div>
      </div>
    </PageContainer>
  );
}
