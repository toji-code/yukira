package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.SchemeOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SchemeOptionRepository extends JpaRepository<SchemeOption, Long> {
    Optional<SchemeOption> findByAmfiCode(String amfiCode);
    Optional<SchemeOption> findByIsin(String isin);
    java.util.List<SchemeOption> findByPlanSchemeId(Long schemeId);
}
