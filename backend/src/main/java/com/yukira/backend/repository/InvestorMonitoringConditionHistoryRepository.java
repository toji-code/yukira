package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.Investor;
import com.yukira.backend.domain.entity.InvestorMonitoringCondition;
import com.yukira.backend.domain.entity.InvestorMonitoringConditionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvestorMonitoringConditionHistoryRepository extends JpaRepository<InvestorMonitoringConditionHistory, Long> {

    List<InvestorMonitoringConditionHistory> findByConditionOrderByEvaluatedAtDesc(InvestorMonitoringCondition condition);

    List<InvestorMonitoringConditionHistory> findByInvestorOrderByEvaluatedAtDesc(Investor investor);

    List<InvestorMonitoringConditionHistory> findTop50ByInvestorOrderByEvaluatedAtDesc(Investor investor);
}

