package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, String> {
    List<SubscriptionPlan> findByIsActiveTrue();
    Optional<SubscriptionPlan> findByIdAndIsActiveTrue(String id);
}
