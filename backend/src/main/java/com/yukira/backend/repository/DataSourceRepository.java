package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.DataSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DataSourceRepository extends JpaRepository<DataSource, Long> {
    Optional<DataSource> findByCode(String code);
}
