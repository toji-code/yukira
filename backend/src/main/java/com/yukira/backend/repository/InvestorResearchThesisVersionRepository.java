package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.InvestorResearchThesisVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvestorResearchThesisVersionRepository extends JpaRepository<InvestorResearchThesisVersion, Long> {
    List<InvestorResearchThesisVersion> findByInvestorIdAndThesisIdOrderByVersionNumberDesc(Long investorId, String thesisId);
    Optional<InvestorResearchThesisVersion> findByInvestorIdAndThesisIdAndVersionNumber(Long investorId, String thesisId, Integer versionNumber);
    List<InvestorResearchThesisVersion> findByInvestorIdOrderByCreatedAtDesc(Long investorId);
}

