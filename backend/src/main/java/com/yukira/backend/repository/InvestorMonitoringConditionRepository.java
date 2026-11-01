package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.Investor;
import com.yukira.backend.domain.entity.InvestorMonitoringCondition;
import com.yukira.backend.domain.entity.SchemeOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvestorMonitoringConditionRepository extends JpaRepository<InvestorMonitoringCondition, Long> {

    List<InvestorMonitoringCondition> findByInvestorOrderByCreatedAtDesc(Investor investor);

    List<InvestorMonitoringCondition> findByInvestorAndIsEnabledTrue(Investor investor);

    @Query("SELECT c FROM InvestorMonitoringCondition c WHERE c.investor = :investor AND c.conditionStatus <> :status")
    List<InvestorMonitoringCondition> findByInvestorAndStatusNot(@Param("investor") Investor investor, @Param("status") String status);

    List<InvestorMonitoringCondition> findByInvestorAndSchemeOption(Investor investor, SchemeOption schemeOption);

    List<InvestorMonitoringCondition> findByInvestorAndAssociatedThesisKey(Investor investor, String associatedThesisKey);

    Optional<InvestorMonitoringCondition> findByIdAndInvestor(Long id, Investor investor);

    @Query("SELECT c FROM InvestorMonitoringCondition c WHERE c.investor = :investor AND c.isEnabled = true AND c.conditionStatus = 'ACTIVE' AND (c.nextEvaluationDueAt IS NULL OR c.nextEvaluationDueAt <= :now)")
    List<InvestorMonitoringCondition> findDueConditionsForInvestor(@Param("investor") Investor investor, @Param("now") ZonedDateTime now);

    @Query("SELECT c FROM InvestorMonitoringCondition c WHERE c.isEnabled = true AND c.conditionStatus = 'ACTIVE' AND (c.nextEvaluationDueAt IS NULL OR c.nextEvaluationDueAt <= :now)")
    List<InvestorMonitoringCondition> findAllDueConditions(@Param("now") ZonedDateTime now);
}
