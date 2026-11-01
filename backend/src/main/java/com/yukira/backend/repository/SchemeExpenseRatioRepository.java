package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.SchemeExpenseRatio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface SchemeExpenseRatioRepository extends JpaRepository<SchemeExpenseRatio, Long> {

    @Query("SELECT er FROM SchemeExpenseRatio er WHERE er.schemeOption.plan.scheme.id = :schemeId ORDER BY er.asOfDate DESC")
    List<SchemeExpenseRatio> findBySchemeId(@Param("schemeId") Long schemeId);

    Optional<SchemeExpenseRatio> findTopBySchemeOptionIdOrderByAsOfDateDesc(Long schemeOptionId);

    Optional<SchemeExpenseRatio> findTopBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByAsOfDateDesc(
            Long schemeOptionId, OffsetDateTime availabilityTime);

    Optional<SchemeExpenseRatio> findBySchemeOptionIdAndAsOfDate(Long schemeOptionId, LocalDate asOfDate);
}
