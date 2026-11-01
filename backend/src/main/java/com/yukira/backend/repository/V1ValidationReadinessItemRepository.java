package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.V1ValidationReadinessItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface V1ValidationReadinessItemRepository extends JpaRepository<V1ValidationReadinessItem, Long> {

    Optional<V1ValidationReadinessItem> findByReadinessAreaCode(String readinessAreaCode);

    List<V1ValidationReadinessItem> findAllByOrderByReadinessAreaCodeAsc();
}
