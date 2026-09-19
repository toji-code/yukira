package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.Benchmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BenchmarkRepository extends JpaRepository<Benchmark, Long> {
    Optional<Benchmark> findByCode(String code);
}
