import { apiFetch } from './client';

export interface SubscriptionPlan {
  id: string;
  name: string;
  description?: string | null;
  priceInr: number;
  billingInterval: 'FREE' | 'MONTHLY' | 'ANNUAL' | string;
  maxPortfolios: number;
  maxHoldingsPerPortfolio: number;
  maxSavedFunds: number;
  canImportCas: boolean;
  canAccessApi: boolean;
  apiRateLimitPerMin: number;
  isActive: boolean;
}

export interface InvestorSubscription {
  id: number;
  investorId: number;
  planId: string;
  planName: string;
  billingInterval: string;
  priceInr: number;
  provider: string;
  providerOrderId?: string | null;
  providerSubscriptionId?: string | null;
  status: 'NONE' | 'CHECKOUT_INITIATED' | 'ACTIVE' | 'PAST_DUE' | 'CANCELLED' | 'EXPIRED' | 'PAYMENT_FAILED' | string;
  paymentStatus: 'UNPAID' | 'PENDING' | 'PAID' | 'FAILED' | 'REFUNDED' | string;
  entitlementStatus: 'STANDARD' | 'ELEVATED_PREVIEW' | 'ACTIVE_PRO' | 'EXPIRED' | string;
  cancelAtPeriodEnd: boolean;
  currentPeriodStart?: string | null;
  currentPeriodEnd?: string | null;
  providerConfigured: boolean;
  message?: string | null;
  canReactivate?: boolean;
  attentionRequired?: boolean;
  nextAction?: string | null;
  periodEndBehavior?: string | null;
}

export interface CheckoutInitiateResponse {
  status: 'SUCCESS' | 'PROVIDER_UNCONFIGURED' | 'ERROR' | string;
  orderId?: string | null;
  amountPaise?: number | null;
  currency?: string | null;
  keyId?: string | null;
  planId: string;
  planName: string;
  providerConfigured: boolean;
  message?: string | null;
}

export interface PaymentVerificationRequest {
  orderId: string;
  paymentId: string;
  signature: string;
}

export async function fetchSubscriptionPlans(): Promise<SubscriptionPlan[]> {
  return apiFetch<SubscriptionPlan[]>('/api/subscription/plans');
}

export async function fetchCurrentSubscription(): Promise<InvestorSubscription> {
  return apiFetch<InvestorSubscription>('/api/investor/subscription');
}

export async function initiateSubscriptionCheckout(planId: string): Promise<CheckoutInitiateResponse> {
  return apiFetch<CheckoutInitiateResponse>('/api/investor/subscription/checkout', {
    method: 'POST',
    body: JSON.stringify({ planId }),
  });
}

export async function verifySubscriptionPayment(req: PaymentVerificationRequest): Promise<InvestorSubscription> {
  return apiFetch<InvestorSubscription>('/api/investor/subscription/verify', {
    method: 'POST',
    body: JSON.stringify(req),
  });
}

export interface BillingRecord {
  id: number;
  planId: string;
  planName: string;
  provider: string;
  providerPaymentId?: string | null;
  providerOrderId?: string | null;
  providerInvoiceId?: string | null;
  providerInvoiceUrl?: string | null;
  amountInr: number;
  currency: string;
  paymentStatus: 'PAID' | 'FAILED' | 'PENDING' | 'REFUNDED' | string;
  billingReason: string;
  failureReason?: string | null;
  periodStart?: string | null;
  periodEnd?: string | null;
  createdAt: string;
  invoiceAvailable: boolean;
  invoiceStatusMessage?: string | null;
}

export interface SubscriptionCancelRequest {
  reason?: string | null;
}

export interface SubscriptionActionResponse {
  actionStatus: 'SUCCESS' | 'SCHEDULED' | 'INFO' | string;
  message: string;
  subscription: InvestorSubscription;
}

export async function fetchBillingHistory(): Promise<BillingRecord[]> {
  return apiFetch<BillingRecord[]>('/api/investor/billing/history');
}

export async function fetchInvoiceUrl(recordId: number): Promise<{ invoiceUrl: string }> {
  return apiFetch<{ invoiceUrl: string }>(`/api/investor/billing/records/${recordId}/invoice`);
}

export async function cancelSubscription(req?: SubscriptionCancelRequest): Promise<SubscriptionActionResponse> {
  return apiFetch<SubscriptionActionResponse>('/api/investor/subscription/cancel', {
    method: 'POST',
    body: JSON.stringify(req || {}),
  });
}

export async function reactivateSubscription(): Promise<SubscriptionActionResponse> {
  return apiFetch<SubscriptionActionResponse>('/api/investor/subscription/reactivate', {
    method: 'POST',
  });
}
