package com.yukira.backend.service;

import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.domain.entity.Scheme;
import com.yukira.backend.domain.entity.SchemeBenchmarkMapping;
import com.yukira.backend.dto.coverage.SchemeBenchmarkMappingDto;
import com.yukira.backend.repository.BenchmarkRepository;
import com.yukira.backend.repository.SchemeBenchmarkMappingRepository;
import com.yukira.backend.repository.SchemeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing primary, secondary, and blended benchmark mappings with TRI priority
 * and point-in-time historical validity (Tasks 171 & 172).
 */
@Service
public class SchemeBenchmarkMappingService {

    private static final Logger log = LoggerFactory.getLogger(SchemeBenchmarkMappingService.class);

    private final SchemeBenchmarkMappingRepository mappingRepository;
    private final SchemeRepository schemeRepository;
    private final BenchmarkRepository benchmarkRepository;

    public SchemeBenchmarkMappingService(
        SchemeBenchmarkMappingRepository mappingRepository,
        SchemeRepository schemeRepository,
        BenchmarkRepository benchmarkRepository
    ) {
        this.mappingRepository = mappingRepository;
        this.schemeRepository = schemeRepository;
        this.benchmarkRepository = benchmarkRepository;
    }

    @Transactional(readOnly = true)
    public List<SchemeBenchmarkMappingDto> getMappingsForScheme(Long schemeId) {
        Scheme scheme = schemeRepository.findById(schemeId)
            .orElseThrow(() -> new IllegalArgumentException("Scheme not found with id: " + schemeId));
        return mappingRepository.findBySchemeOrderByEffectiveFromDesc(scheme).stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<SchemeBenchmarkMappingDto> getPrimaryMapping(Long schemeId) {
        Scheme scheme = schemeRepository.findById(schemeId).orElse(null);
        if (scheme == null) return Optional.empty();
        return mappingRepository.findFirstBySchemeAndMappingRoleOrderByEffectiveFromDesc(scheme, "PRIMARY")
            .map(this::toDto);
    }

    @Transactional
    public SchemeBenchmarkMappingDto createOrUpdateMapping(
        Long schemeId,
        Long benchmarkId,
        String mappingRole,
        String mappingSource,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Boolean isTri,
        String provenanceReference
    ) {
        Scheme scheme = schemeRepository.findById(schemeId)
            .orElseThrow(() -> new IllegalArgumentException("Scheme not found with id: " + schemeId));
        Benchmark benchmark = benchmarkRepository.findById(benchmarkId)
            .orElseThrow(() -> new IllegalArgumentException("Benchmark not found with id: " + benchmarkId));

        SchemeBenchmarkMapping mapping = new SchemeBenchmarkMapping();
        mapping.setScheme(scheme);
        mapping.setBenchmark(benchmark);
        mapping.setMappingRole(mappingRole != null ? mappingRole.toUpperCase() : "PRIMARY");
        mapping.setMappingSource(mappingSource != null ? mappingSource : "AMC_DISCLOSURE");
        mapping.setEffectiveFrom(effectiveFrom != null ? effectiveFrom : LocalDate.now());
        mapping.setEffectiveTo(effectiveTo);
        mapping.setIsTri(isTri != null ? isTri : true);
        mapping.setProvenanceReference(provenanceReference);

        SchemeBenchmarkMapping saved = mappingRepository.save(mapping);
        log.info("Saved benchmark mapping for scheme {} with benchmark {} [role={}, isTri={}]",
            schemeId, benchmark.getCode(), mapping.getMappingRole(), mapping.getIsTri());
        return toDto(saved);
    }

    private SchemeBenchmarkMappingDto toDto(SchemeBenchmarkMapping mapping) {
        return new SchemeBenchmarkMappingDto(
            mapping.getId(),
            mapping.getScheme().getId(),
            mapping.getScheme().getName(),
            mapping.getBenchmark().getId(),
            mapping.getBenchmark().getCode(),
            mapping.getBenchmark().getName(),
            mapping.getMappingRole(),
            mapping.getMappingSource(),
            mapping.getEffectiveFrom(),
            mapping.getEffectiveTo(),
            mapping.getIsTri(),
            mapping.getProvenanceReference(),
            mapping.getCreatedAt()
        );
    }
}

