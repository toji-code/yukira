package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.NavObservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface NavObservationRepository extends JpaRepository<NavObservation, Long> {

    /**
     * Point-in-Time (PIT) Authoritative Revision Resolution Query.
     *
     * For each distinct effective date, selects the authoritative revision
     * known as of knowledgeCutoffTime.
     *
     * Primary selection criterion: revision_seq DESC (authoritative version sequence).
     * Tie-breaker: availability_time DESC.
     * Eligibility condition: availability_time <= knowledgeCutoffTime.
     */
    @Query(value = """
        SELECT DISTINCT ON (n.effective_date) n.*
        FROM nav_observation n
        WHERE n.scheme_option_id = :schemeOptionId
          AND n.effective_date <= :asOfDate
          AND n.availability_time <= :knowledgeCutoffTime
        ORDER BY n.effective_date ASC, n.revision_seq DESC, n.availability_time DESC
        """, nativeQuery = true)
    List<NavObservation> findAuthoritativeObservationsAsOfCutoff(
        @Param("schemeOptionId") Long schemeOptionId,
        @Param("asOfDate") LocalDate asOfDate,
        @Param("knowledgeCutoffTime") OffsetDateTime knowledgeCutoffTime
    );
}
