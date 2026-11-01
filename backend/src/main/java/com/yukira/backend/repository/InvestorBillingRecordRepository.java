package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.InvestorBillingRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvestorBillingRecordRepository extends JpaRepository<InvestorBillingRecord, Long> {
    List<InvestorBillingRecord> findByInvestorIdOrderByCreatedAtDesc(Long investorId);
    Optional<InvestorBillingRecord> findByInvestorIdAndId(Long investorId, Long id);
    Optional<InvestorBillingRecord> findByProviderPaymentId(String providerPaymentId);
    Optional<InvestorBillingRecord> findByProviderOrderId(String providerOrderId);
}
