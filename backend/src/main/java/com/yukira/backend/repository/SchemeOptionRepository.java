package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.SchemeOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SchemeOptionRepository extends JpaRepository<SchemeOption, Long>, JpaSpecificationExecutor<SchemeOption> {
    Optional<SchemeOption> findByAmfiCode(String amfiCode);
    Optional<SchemeOption> findByIsin(String isin);
    List<SchemeOption> findByPlanSchemeId(Long schemeId);

    long countByStatus(String status);
    long countByPlanPlanType(String planType);
    long countByOptionType(String optionType);
    long countByOptionTypeStartingWith(String prefix);
    long countByIsinIsNull();
    long countByAmfiCodeIsNull();

    @Query("""
        SELECT DISTINCT o FROM SchemeOption o
        JOIN FETCH o.plan p
        JOIN FETCH p.scheme s
        JOIN FETCH s.amc
        WHERE o.id IN :ids
        """)
    List<SchemeOption> findComparisonIdentitiesByIdIn(@Param("ids") List<Long> ids);
}
