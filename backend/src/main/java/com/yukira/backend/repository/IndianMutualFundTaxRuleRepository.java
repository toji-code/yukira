package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.IndianMutualFundTaxRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IndianMutualFundTaxRuleRepository extends JpaRepository<IndianMutualFundTaxRule, Long> {
    Optional<IndianMutualFundTaxRule> findByRuleCode(String ruleCode);
    List<IndianMutualFundTaxRule> findByCategoryPatternIgnoreCase(String categoryPattern);
    List<IndianMutualFundTaxRule> findAllByOrderByAcquisitionStartDateAsc();
}

