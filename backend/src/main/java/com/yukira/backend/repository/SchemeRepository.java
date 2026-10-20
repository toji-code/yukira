package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.Scheme;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SchemeRepository extends JpaRepository<Scheme, Long>, JpaSpecificationExecutor<Scheme> {
    @EntityGraph(attributePaths = {"amc"})
    Optional<Scheme> findByCode(String code);

    @Override
    @EntityGraph(attributePaths = {"amc"})
    Optional<Scheme> findById(Long id);

    long countByCategoryIsNull();
    long countBySubcategoryIsNull();
    long countByStatus(String status);
}
