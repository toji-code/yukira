package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.Amc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AmcRepository extends JpaRepository<Amc, Long> {
    Optional<Amc> findByCode(String code);
}
