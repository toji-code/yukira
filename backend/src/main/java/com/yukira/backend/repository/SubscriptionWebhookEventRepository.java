package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.SubscriptionWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SubscriptionWebhookEventRepository extends JpaRepository<SubscriptionWebhookEvent, Long> {
    boolean existsByEventId(String eventId);
    Optional<SubscriptionWebhookEvent> findByEventId(String eventId);
}
