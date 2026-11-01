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
    default Optional<SchemeOption> findByAmfiSchemeCode(String amfiSchemeCode) {
        return findByAmfiCode(amfiSchemeCode);
    }
    Optional<SchemeOption> findByIsin(String isin);
    @Query("""
        SELECT DISTINCT o FROM SchemeOption o
        JOIN FETCH o.plan p
        JOIN FETCH p.scheme s
        JOIN FETCH s.amc
        WHERE s.id = :schemeId
        ORDER BY o.id ASC
        """)
    List<SchemeOption> findByPlanSchemeId(@Param("schemeId") Long schemeId);

    default List<SchemeOption> findBySchemeId(Long schemeId) {
        return findByPlanSchemeId(schemeId);
    }

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

    @Query("""
        SELECT DISTINCT o FROM SchemeOption o
        JOIN FETCH o.plan p
        JOIN FETCH p.scheme s
        JOIN FETCH s.amc a
        WHERE UPPER(p.planType) = 'DIRECT'
          AND UPPER(o.optionType) = 'GROWTH'
          AND o.status = 'ACTIVE'
        ORDER BY o.id ASC
        """)
    List<SchemeOption> findDirectGrowthActiveCandidates();
}
