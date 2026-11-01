package com.yukira.backend.service;

import com.yukira.backend.domain.entity.Scheme;
import com.yukira.backend.domain.entity.SchemeLifecycleRecord;
import com.yukira.backend.dto.scheme.SchemeLifecycleRecordDto;
import com.yukira.backend.repository.SchemeLifecycleRecordRepository;
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
 * Service for scheme lifecycle tracking across renames, mergers, and suspensions (Task 182).
 */
@Service
public class SchemeLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(SchemeLifecycleService.class);

    private final SchemeLifecycleRecordRepository lifecycleRepository;
    private final SchemeRepository schemeRepository;

    public SchemeLifecycleService(
        SchemeLifecycleRecordRepository lifecycleRepository,
        SchemeRepository schemeRepository
    ) {
        this.lifecycleRepository = lifecycleRepository;
        this.schemeRepository = schemeRepository;
    }

    @Transactional(readOnly = true)
    public List<SchemeLifecycleRecordDto> getLifecycleRecords(Long schemeId) {
        Scheme scheme = schemeRepository.findById(schemeId)
            .orElseThrow(() -> new IllegalArgumentException("Scheme not found with id: " + schemeId));
        return lifecycleRepository.findBySchemeOrderByEffectiveDateDesc(scheme).stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<SchemeLifecycleRecordDto> getLatestLifecycleStatus(Long schemeId) {
        Scheme scheme = schemeRepository.findById(schemeId).orElse(null);
        if (scheme == null) return Optional.empty();
        return lifecycleRepository.findFirstBySchemeOrderByEffectiveDateDesc(scheme)
            .map(this::toDto);
    }

    @Transactional
    public SchemeLifecycleRecordDto recordLifecycleChange(
        Long schemeId,
        String status,
        LocalDate effectiveDate,
        Long successorSchemeId,
        Long predecessorSchemeId,
        String regulatoryCircularRef,
        String remarks
    ) {
        Scheme scheme = schemeRepository.findById(schemeId)
            .orElseThrow(() -> new IllegalArgumentException("Scheme not found with id: " + schemeId));
        Scheme successor = successorSchemeId != null ? schemeRepository.findById(successorSchemeId).orElse(null) : null;
        Scheme predecessor = predecessorSchemeId != null ? schemeRepository.findById(predecessorSchemeId).orElse(null) : null;

        SchemeLifecycleRecord record = new SchemeLifecycleRecord();
        record.setScheme(scheme);
        record.setLifecycleStatus(status != null ? status.toUpperCase() : "ACTIVE");
        record.setEffectiveDate(effectiveDate != null ? effectiveDate : LocalDate.now());
        record.setSuccessorScheme(successor);
        record.setPredecessorScheme(predecessor);
        record.setRegulatoryCircularRef(regulatoryCircularRef);
        record.setRemarks(remarks);

        SchemeLifecycleRecord saved = lifecycleRepository.save(record);
        log.info("Recorded lifecycle change for scheme {}: status={}, effectiveDate={}",
            schemeId, saved.getLifecycleStatus(), saved.getEffectiveDate());
        return toDto(saved);
    }

    private SchemeLifecycleRecordDto toDto(SchemeLifecycleRecord r) {
        return new SchemeLifecycleRecordDto(
            r.getId(),
            r.getScheme().getId(),
            r.getScheme().getName(),
            r.getLifecycleStatus(),
            r.getEffectiveDate(),
            r.getSuccessorScheme() != null ? r.getSuccessorScheme().getId() : null,
            r.getSuccessorScheme() != null ? r.getSuccessorScheme().getName() : null,
            r.getPredecessorScheme() != null ? r.getPredecessorScheme().getId() : null,
            r.getPredecessorScheme() != null ? r.getPredecessorScheme().getName() : null,
            r.getRegulatoryCircularRef(),
            r.getRemarks(),
            r.getCreatedAt()
        );
    }
}

