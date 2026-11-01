package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.Scheme;
import com.yukira.backend.domain.entity.SchemeDataCoverage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SchemeDataCoverageRepository extends JpaRepository<SchemeDataCoverage, Long> {

    Optional<SchemeDataCoverage> findByScheme(Scheme scheme);

    Optional<SchemeDataCoverage> findBySchemeId(Long schemeId);
}

