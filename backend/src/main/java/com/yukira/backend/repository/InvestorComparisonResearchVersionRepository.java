package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.InvestorComparisonResearch;
import com.yukira.backend.domain.entity.InvestorComparisonResearchVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvestorComparisonResearchVersionRepository extends JpaRepository<InvestorComparisonResearchVersion, Long> {

    List<InvestorComparisonResearchVersion> findByComparisonResearchOrderByVersionNumberDesc(InvestorComparisonResearch comparisonResearch);
}

