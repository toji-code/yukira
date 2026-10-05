package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.SchemeExpenseRatio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public interface SchemeExpenseRatioRepository extends JpaRepository<SchemeExpenseRatio, Long> {

    Optional<SchemeExpenseRatio> findTopBySchemeOptionIdOrderByAsOfDateDesc(Long schemeOptionId);

    Optional<SchemeExpenseRatio> findTopBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByAsOfDateDesc(
            Long schemeOptionId, OffsetDateTime availabilityTime);

    Optional<SchemeExpenseRatio> findBySchemeOptionIdAndAsOfDate(Long schemeOptionId, LocalDate asOfDate);
}
