package com.yukira.backend.service.subscription;

import com.yukira.backend.domain.entity.Investor;
import com.yukira.backend.domain.entity.InvestorSubscription;
import com.yukira.backend.domain.entity.SubscriptionPlan;
import com.yukira.backend.repository.InvestorSubscriptionRepository;
import com.yukira.backend.repository.SubscriptionPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EntitlementService {

    private final InvestorSubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;

    public EntitlementService(InvestorSubscriptionRepository subscriptionRepository,
                              SubscriptionPlanRepository planRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
    }

    public SubscriptionPlan getEffectivePlan(Investor investor) {
        if (investor == null) {
            return getFreePlan();
        }
        return subscriptionRepository.findByInvestorId(investor.getId())
                .filter(this::isSubscriptionActive)
                .map(InvestorSubscription::getPlan)
                .orElseGet(this::getFreePlan);
    }

    public boolean isSubscriptionActive(InvestorSubscription sub) {
        if (sub == null || sub.getPlan() == null || "FREE".equalsIgnoreCase(sub.getPlan().getId())) {
            return false;
        }

        java.time.ZonedDateTime now = java.time.ZonedDateTime.now();
        java.time.ZonedDateTime periodEnd = sub.getCurrentPeriodEnd();

        // 1. If paid period has elapsed, subscription is expired regardless of stored string
        if (periodEnd != null && now.isAfter(periodEnd)) {
            return false;
        }

        // 2. Explicit active entitlement with unexpired period
        if ("ACTIVE_PRO".equalsIgnoreCase(sub.getEntitlementStatus())) {
            return periodEnd == null || !now.isAfter(periodEnd);
        }

        // 3. Active status with confirmed payment
        if ("ACTIVE".equalsIgnoreCase(sub.getStatus()) && "PAID".equalsIgnoreCase(sub.getPaymentStatus())) {
            return periodEnd == null || !now.isAfter(periodEnd);
        }

        // 4. Past due (e.g. renewal failed), but current paid period has not yet expired
        if ("PAST_DUE".equalsIgnoreCase(sub.getStatus()) && periodEnd != null && !now.isAfter(periodEnd)) {
            return true;
        }

        return false;
    }

    public String getEffectiveEntitlementStatus(Investor investor) {
        SubscriptionPlan plan = getEffectivePlan(investor);
        if (plan != null && !"FREE".equalsIgnoreCase(plan.getId())) {
            return "ACTIVE_PRO";
        }
        return "STANDARD";
    }

    public boolean isProEntitled(Investor investor) {
        SubscriptionPlan plan = getEffectivePlan(investor);
        return plan != null && !"FREE".equalsIgnoreCase(plan.getId());
    }

    public boolean canAddWatchlistItem(Investor investor, int currentCount) {
        int maxAllowed = getMaxWatchlistItems(investor);
        return currentCount < maxAllowed;
    }

    public boolean canAddPortfolioHolding(Investor investor, int currentCount) {
        int maxAllowed = getMaxPortfolioHoldings(investor);
        return currentCount < maxAllowed;
    }

    public int getMaxWatchlistItems(Investor investor) {
        SubscriptionPlan plan = getEffectivePlan(investor);
        return plan != null ? plan.getMaxSavedFunds() : 25;
    }

    public int getMaxPortfolioHoldings(Investor investor) {
        SubscriptionPlan plan = getEffectivePlan(investor);
        return plan != null ? plan.getMaxHoldingsPerPortfolio() : 20;
    }

    public boolean canAccessApi(Investor investor) {
        SubscriptionPlan plan = getEffectivePlan(investor);
        return plan != null && plan.isCanAccessApi();
    }

    public boolean canImportCas(Investor investor) {
        SubscriptionPlan plan = getEffectivePlan(investor);
        return plan != null && plan.isCanImportCas();
    }

    private SubscriptionPlan getFreePlan() {
        return planRepository.findById("FREE").orElse(null);
    }
}
