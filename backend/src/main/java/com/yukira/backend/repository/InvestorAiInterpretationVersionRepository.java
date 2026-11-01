package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.InvestorAiInterpretationVersion;
import com.yukira.backend.domain.entity.SchemeOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvestorAiInterpretationVersionRepository extends JpaRepository<InvestorAiInterpretationVersion, Long> {

    List<InvestorAiInterpretationVersion> findBySchemeOptionOrderByCreatedAtDesc(SchemeOption schemeOption);

    Optional<InvestorAiInterpretationVersion> findFirstBySchemeOptionOrderByCreatedAtDesc(SchemeOption schemeOption);
}

