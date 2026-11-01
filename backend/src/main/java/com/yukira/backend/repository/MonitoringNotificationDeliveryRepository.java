package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.MonitoringNotificationDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MonitoringNotificationDeliveryRepository extends JpaRepository<MonitoringNotificationDelivery, Long> {
    List<MonitoringNotificationDelivery> findByAlertIdOrderByCreatedAtDesc(Long alertId);
    List<MonitoringNotificationDelivery> findByDeliveryStatusOrderByCreatedAtAsc(String deliveryStatus);
    List<MonitoringNotificationDelivery> findTop50ByOrderByCreatedAtDesc();
}

