package com.yukira.backend.service;

import com.yukira.backend.domain.entity.Scheme;
import com.yukira.backend.domain.entity.SchemeMetadataRevision;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.repository.SchemeMetadataRevisionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Service to track, audit and retrieve immutable historical metadata revisions (Task 268).
 * Ensures that changes in fund category, classification, scheme names, or identifier mappings
 * are tracked with bitemporal availability without mutating historical research context.
 */
@Service
@Transactional
public class SchemeMetadataRevisionService {

    private static final Logger log = LoggerFactory.getLogger(SchemeMetadataRevisionService.class);

    private final SchemeMetadataRevisionRepository revisionRepository;

    public SchemeMetadataRevisionService(SchemeMetadataRevisionRepository revisionRepository) {
        this.revisionRepository = revisionRepository;
    }

    public record SchemeMetadataRevisionDto(
        Long id,
        Long schemeId,
        Long schemeOptionId,
        String revisionType,
        String fieldName,
        String previousValue,
        String newValue,
        LocalDate effectiveDate,
        OffsetDateTime availabilityTime,
        String revisionReason
    ) {}

    public void trackSchemeChange(
            Scheme scheme,
            String fieldName,
            String previousValue,
            String newValue,
            LocalDate effectiveDate,
            SourceArtifact sourceArtifact,
            String reason
    ) {
        if (Objects.equals(previousValue, newValue)) {
            return;
        }
        SchemeMetadataRevision rev = new SchemeMetadataRevision();
        rev.setScheme(scheme);
        rev.setRevisionType("SCHEME_MUTATION");
        rev.setFieldName(fieldName);
        rev.setPreviousValue(previousValue);
        rev.setNewValue(newValue);
        rev.setEffectiveDate(effectiveDate != null ? effectiveDate : LocalDate.now());
        rev.setAvailabilityTime(java.time.ZonedDateTime.now());
        rev.setSourceArtifact(sourceArtifact);
        rev.setRevisionReason(reason);
        revisionRepository.save(rev);
        log.info("Recorded metadata revision for scheme {} on field {}: '{}' -> '{}'",
                scheme.getId(), fieldName, previousValue, newValue);
    }

    public void trackOptionChange(
            SchemeOption option,
            String fieldName,
            String previousValue,
            String newValue,
            LocalDate effectiveDate,
            SourceArtifact sourceArtifact,
            String reason
    ) {
        if (Objects.equals(previousValue, newValue)) {
            return;
        }
        SchemeMetadataRevision rev = new SchemeMetadataRevision();
        rev.setScheme(option.getScheme());
        rev.setSchemeOption(option);
        rev.setRevisionType("OPTION_MUTATION");
        rev.setFieldName(fieldName);
        rev.setPreviousValue(previousValue);
        rev.setNewValue(newValue);
        rev.setEffectiveDate(effectiveDate != null ? effectiveDate : LocalDate.now());
        rev.setAvailabilityTime(java.time.ZonedDateTime.now());
        rev.setSourceArtifact(sourceArtifact);
        rev.setRevisionReason(reason);
        revisionRepository.save(rev);
        log.info("Recorded metadata revision for option {} on field {}: '{}' -> '{}'",
                option.getId(), fieldName, previousValue, newValue);
    }

    @Transactional(readOnly = true)
    public List<SchemeMetadataRevisionDto> getRevisionsForScheme(Long schemeId) {
        return revisionRepository.findBySchemeIdOrderByEffectiveDateDesc(schemeId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SchemeMetadataRevisionDto> getRevisionsForOption(Long schemeOptionId) {
        return revisionRepository.findBySchemeOptionIdOrderByEffectiveDateDesc(schemeOptionId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private SchemeMetadataRevisionDto toDto(SchemeMetadataRevision rev) {
        return new SchemeMetadataRevisionDto(
                rev.getId(),
                rev.getScheme() != null ? rev.getScheme().getId() : null,
                rev.getSchemeOption() != null ? rev.getSchemeOption().getId() : null,
                rev.getRevisionType(),
                rev.getFieldName(),
                rev.getPreviousValue(),
                rev.getNewValue(),
                rev.getEffectiveDate(),
                rev.getAvailabilityTime() != null ? rev.getAvailabilityTime().toOffsetDateTime() : null,
                rev.getRevisionReason()
        );
    }
}

