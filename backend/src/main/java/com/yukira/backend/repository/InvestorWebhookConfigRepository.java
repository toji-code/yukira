package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.Investor;
import com.yukira.backend.domain.entity.InvestorWebhookConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvestorWebhookConfigRepository extends JpaRepository<InvestorWebhookConfig, Long> {

    List<InvestorWebhookConfig> findByInvestor(Investor investor);

    List<InvestorWebhookConfig> findByInvestorAndIsActiveTrue(Investor investor);

    Optional<InvestorWebhookConfig> findByIdAndInvestor(Long id, Investor investor);
}

