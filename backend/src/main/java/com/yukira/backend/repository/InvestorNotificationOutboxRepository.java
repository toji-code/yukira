package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.Investor;
import com.yukira.backend.domain.entity.InvestorNotificationOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvestorNotificationOutboxRepository extends JpaRepository<InvestorNotificationOutbox, Long> {

    List<InvestorNotificationOutbox> findByInvestorOrderByCreatedAtDesc(Investor investor);

    @Query("SELECT n FROM InvestorNotificationOutbox n WHERE n.deliveryStatus IN ('QUEUED', 'FAILED') AND n.retryCount < n.maxRetries AND (n.nextAttemptAt IS NULL OR n.nextAttemptAt <= :now)")
    List<InvestorNotificationOutbox> findPendingDeliveries(@Param("now") ZonedDateTime now);

    Optional<InvestorNotificationOutbox> findByInvestorAndIdempotencyHash(Investor investor, String idempotencyHash);
}

