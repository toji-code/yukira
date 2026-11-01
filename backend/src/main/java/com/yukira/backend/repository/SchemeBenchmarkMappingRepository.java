package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.Scheme;
import com.yukira.backend.domain.entity.SchemeBenchmarkMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SchemeBenchmarkMappingRepository extends JpaRepository<SchemeBenchmarkMapping, Long> {

    List<SchemeBenchmarkMapping> findBySchemeOrderByEffectiveFromDesc(Scheme scheme);

    List<SchemeBenchmarkMapping> findBySchemeId(Long schemeId);

    Optional<SchemeBenchmarkMapping> findFirstBySchemeAndMappingRoleOrderByEffectiveFromDesc(Scheme scheme, String mappingRole);
}

