package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.InvestorSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvestorSubscriptionRepository extends JpaRepository<InvestorSubscription, Long> {
    Optional<InvestorSubscription> findByInvestorId(Long investorId);
    Optional<InvestorSubscription> findByProviderOrderId(String providerOrderId);
    Optional<InvestorSubscription> findByProviderSubscriptionId(String providerSubscriptionId);
}
