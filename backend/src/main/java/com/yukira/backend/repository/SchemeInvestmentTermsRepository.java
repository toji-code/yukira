package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.SchemeInvestmentTerms;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface SchemeInvestmentTermsRepository extends JpaRepository<SchemeInvestmentTerms, Long> {

    Optional<SchemeInvestmentTerms> findTopBySchemeIdOrderByAsOfDateDesc(Long schemeId);

    Optional<SchemeInvestmentTerms> findBySchemeIdAndAsOfDate(Long schemeId, LocalDate asOfDate);
}
