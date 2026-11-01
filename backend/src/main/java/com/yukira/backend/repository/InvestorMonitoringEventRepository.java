package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.Investor;
import com.yukira.backend.domain.entity.InvestorMonitoringEvent;
import com.yukira.backend.domain.entity.SchemeOption;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvestorMonitoringEventRepository extends JpaRepository<InvestorMonitoringEvent, Long> {

    Optional<InvestorMonitoringEvent> findByIdAndInvestor(Long id, Investor investor);

    Optional<InvestorMonitoringEvent> findByInvestorAndDedupHash(Investor investor, String dedupHash);

    List<InvestorMonitoringEvent> findByInvestorOrderByCreatedAtDesc(Investor investor);

    default List<InvestorMonitoringEvent> findByInvestorOrderByTriggeredAtDesc(Investor investor) {
        return findByInvestorOrderByCreatedAtDesc(investor);
    }

    List<InvestorMonitoringEvent> findByInvestorAndSchemeOptionOrderByCreatedAtDesc(Investor investor, SchemeOption schemeOption);

    @Query("SELECT e FROM InvestorMonitoringEvent e WHERE e.investor = :investor AND e.scheme.id = :schemeId ORDER BY e.createdAt DESC")
    List<InvestorMonitoringEvent> findByInvestorAndSchemeIdOrderByCreatedAtDesc(@Param("investor") Investor investor, @Param("schemeId") Long schemeId);

    List<InvestorMonitoringEvent> findByInvestorAndPortfolioScopeIsNotNullOrderByCreatedAtDesc(Investor investor);

    long countByInvestor(Investor investor);

    long countByInvestorAndLifecycleStatus(Investor investor, String lifecycleStatus);

    long countByInvestorAndLifecycleStatusIn(Investor investor, Collection<String> statuses);

    long countByInvestorAndSeverityInAndLifecycleStatusIn(Investor investor, Collection<String> severities, Collection<String> statuses);

    long countByInvestorAndEventClassificationAndLifecycleStatusIn(Investor investor, String classification, Collection<String> statuses);

    @Query("SELECT e FROM InvestorMonitoringEvent e WHERE e.investor = :investor " +
           "AND (:schemeId IS NULL OR e.scheme.id = :schemeId) " +
           "AND (:schemeOptionId IS NULL OR (e.schemeOption IS NOT NULL AND e.schemeOption.id = :schemeOptionId)) " +
           "AND (:classification IS NULL OR e.eventClassification = :classification) " +
           "AND (:severity IS NULL OR e.severity = :severity) " +
           "AND (:status IS NULL OR e.lifecycleStatus = :status) " +
           "AND (:scope IS NULL OR (:scope = 'PORTFOLIO' AND e.portfolioScope IS NOT NULL) OR (:scope = 'FUND' AND e.portfolioScope IS NULL)) " +
           "ORDER BY e.createdAt DESC")
    Page<InvestorMonitoringEvent> findFilteredEvents(
        @Param("investor") Investor investor,
        @Param("schemeId") Long schemeId,
        @Param("schemeOptionId") Long schemeOptionId,
        @Param("classification") String classification,
        @Param("severity") String severity,
        @Param("status") String status,
        @Param("scope") String scope,
        Pageable pageable
    );
}

