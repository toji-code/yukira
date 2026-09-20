package com.yukira.backend.service;

import com.yukira.backend.domain.entity.MetricDefinition;
import com.yukira.backend.domain.entity.MethodologyVersion;
import com.yukira.backend.domain.entity.MethodologyChangeLog;
import com.yukira.backend.repository.MetricDefinitionRepository;
import com.yukira.backend.repository.MethodologyVersionRepository;
import com.yukira.backend.repository.MethodologyChangeLogRepository;
import com.yukira.backend.repository.CalculationRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class MethodologyGovernanceService {

    private final MetricDefinitionRepository metricDefinitionRepository;
    private final MethodologyVersionRepository methodologyVersionRepository;
    private final MethodologyChangeLogRepository methodologyChangeLogRepository;
    private final CalculationRunRepository calculationRunRepository;

    public MethodologyGovernanceService(
        MetricDefinitionRepository metricDefinitionRepository,
        MethodologyVersionRepository methodologyVersionRepository,
        MethodologyChangeLogRepository methodologyChangeLogRepository,
        CalculationRunRepository calculationRunRepository
    ) {
        this.metricDefinitionRepository = metricDefinitionRepository;
        this.methodologyVersionRepository = methodologyVersionRepository;
        this.methodologyChangeLogRepository = methodologyChangeLogRepository;
        this.calculationRunRepository = calculationRunRepository;
    }

    /**
     * Registers or updates a governed metric definition.
     */
    @Transactional
    public MetricDefinition registerMetricDefinition(MetricDefinition metricDefinition) {
        return metricDefinitionRepository.findByMetricCode(metricDefinition.getMetricCode())
            .orElseGet(() -> metricDefinitionRepository.save(metricDefinition));
    }

    /**
     * Registers a new methodology version.
     * Enforces default lifecycle status = CANDIDATE and validation status = UNVALIDATED.
     */
    @Transactional
    public MethodologyVersion registerMethodologyVersion(MethodologyVersion version, String actor) {
        if (version.getLifecycleStatus() == null || version.getLifecycleStatus().isBlank()) {
            version.setLifecycleStatus("CANDIDATE");
        }
        if (version.getApprovalStatus() == null || version.getApprovalStatus().isBlank()) {
            version.setApprovalStatus("CANDIDATE");
        }
        if (version.getValidationStatus() == null || version.getValidationStatus().isBlank()) {
            version.setValidationStatus("UNVALIDATED");
        }

        MethodologyVersion saved = methodologyVersionRepository.save(version);
        methodologyChangeLogRepository.save(new MethodologyChangeLog(
            saved,
            "INITIAL_CREATION",
            null,
            saved.getLifecycleStatus(),
            "Initial registration of methodology version " + saved.getVersionTag(),
            actor != null ? actor : "SYSTEM"
        ));
        return saved;
    }

    /**
     * Asserts that a methodology version can be modified.
     * A methodology version becomes permanently immutable once locked or referenced by calculation runs.
     */
    @Transactional(readOnly = true)
    public void assertVersionMutable(MethodologyVersion version) {
        if (version.isLocked() || (version.getId() != null && calculationRunRepository.existsByMethodologyVersionId(version.getId()))) {
            throw new IllegalStateException(String.format(
                "Methodology version '%s' (tag: '%s') is immutable because it is locked or referenced by historical calculation runs. A new methodology version must be created instead.",
                version.getMethodologyCode(), version.getVersionTag()
            ));
        }
    }

    /**
     * Permanently locks a methodology version to ensure historical calculation reproducibility.
     */
    @Transactional
    public MethodologyVersion lockVersion(Long versionId, String actor) {
        MethodologyVersion version = methodologyVersionRepository.findById(versionId)
            .orElseThrow(() -> new IllegalArgumentException("MethodologyVersion not found: " + versionId));

        if (!version.isLocked()) {
            version.setLocked(true);
            version = methodologyVersionRepository.save(version);
            methodologyChangeLogRepository.save(new MethodologyChangeLog(
                version,
                "LOCK_ENFORCEMENT",
                version.getLifecycleStatus(),
                version.getLifecycleStatus(),
                "Methodology version locked for historical reproducibility.",
                actor != null ? actor : "SYSTEM"
            ));
        }
        return version;
    }

    /**
     * Creates a new methodology version forking from an immutable predecessor.
     * Historical results remain bound to the predecessor version.
     */
    @Transactional
    public MethodologyVersion createNewVersion(
        Long predecessorVersionId,
        String newVersionTag,
        String gitCommitHash,
        String actor,
        String changeReason
    ) {
        MethodologyVersion predecessor = methodologyVersionRepository.findById(predecessorVersionId)
            .orElseThrow(() -> new IllegalArgumentException("Predecessor MethodologyVersion not found: " + predecessorVersionId));

        if (predecessor.getVersionTag().equalsIgnoreCase(newVersionTag)) {
            throw new IllegalArgumentException("New version tag must differ from predecessor version tag: " + newVersionTag);
        }

        // Predecessor must be locked when forking a successor
        if (!predecessor.isLocked()) {
            lockVersion(predecessor.getId(), actor);
        }

        MethodologyVersion successor = new MethodologyVersion();
        successor.setMetricDefinition(predecessor.getMetricDefinition());
        successor.setMethodologyCode(predecessor.getMethodologyCode());
        successor.setVersionTag(newVersionTag);
        successor.setGitCommitHash(gitCommitHash);
        successor.setParameterConfiguration(predecessor.getParameterConfiguration());
        successor.setMathematicalDefinition(predecessor.getMathematicalDefinition());
        successor.setFormulaReference(predecessor.getFormulaReference());
        successor.setRequiredInputs(predecessor.getRequiredInputs());
        successor.setFrequencyAssumptions(predecessor.getFrequencyAssumptions());
        successor.setLookbackRule(predecessor.getLookbackRule());
        successor.setObservationDateSemantics(predecessor.getObservationDateSemantics());
        successor.setInformationSetRequirement(predecessor.getInformationSetRequirement());
        successor.setAnnualizationConvention(predecessor.getAnnualizationConvention());
        successor.setDenominatorConvention(predecessor.getDenominatorConvention());
        successor.setMissingDataRule(predecessor.getMissingDataRule());
        successor.setInsufficientHistoryRule(predecessor.getInsufficientHistoryRule());
        successor.setInvalidDataRule(predecessor.getInvalidDataRule());
        successor.setQualityPrerequisites(predecessor.getQualityPrerequisites());
        successor.setKnownLimitations(predecessor.getKnownLimitations());
        successor.setSupersedesVersion(predecessor);
        successor.setLifecycleStatus("CANDIDATE");
        successor.setApprovalStatus("CANDIDATE");
        successor.setValidationStatus("UNVALIDATED");
        successor.setValidationEvidenceReference(null);
        successor.setApprovalRecord(null);
        successor.setApprovedBy(null);
        successor.setApprovedAt(null);
        successor.setLocked(false);
        successor.setEffectiveFrom(OffsetDateTime.now());
        successor.setCreatedAt(OffsetDateTime.now());

        MethodologyVersion saved = methodologyVersionRepository.save(successor);
        methodologyChangeLogRepository.save(new MethodologyChangeLog(
            saved,
            "VERSION_FORK",
            predecessor.getVersionTag(),
            saved.getVersionTag(),
            changeReason != null ? changeReason : "Forked new candidate methodology version from " + predecessor.getVersionTag(),
            actor != null ? actor : "SYSTEM"
        ));
        return saved;
    }

    /**
     * Updates mutable methodology definition attributes.
     * Fails immediately if the version is locked or referenced by historical calculation runs.
     */
    @Transactional
    public MethodologyVersion updateMethodologyDefinition(MethodologyVersion updated, String actor) {
        MethodologyVersion existing = methodologyVersionRepository.findById(updated.getId())
            .orElseThrow(() -> new IllegalArgumentException("MethodologyVersion not found: " + updated.getId()));

        assertVersionMutable(existing);

        existing.setMathematicalDefinition(updated.getMathematicalDefinition());
        existing.setFormulaReference(updated.getFormulaReference());
        existing.setRequiredInputs(updated.getRequiredInputs());
        existing.setFrequencyAssumptions(updated.getFrequencyAssumptions());
        existing.setLookbackRule(updated.getLookbackRule());
        existing.setObservationDateSemantics(updated.getObservationDateSemantics());
        existing.setInformationSetRequirement(updated.getInformationSetRequirement());
        existing.setAnnualizationConvention(updated.getAnnualizationConvention());
        existing.setDenominatorConvention(updated.getDenominatorConvention());
        existing.setMissingDataRule(updated.getMissingDataRule());
        existing.setInsufficientHistoryRule(updated.getInsufficientHistoryRule());
        existing.setInvalidDataRule(updated.getInvalidDataRule());
        existing.setQualityPrerequisites(updated.getQualityPrerequisites());
        existing.setKnownLimitations(updated.getKnownLimitations());
        existing.setParameterConfiguration(updated.getParameterConfiguration());

        MethodologyVersion saved = methodologyVersionRepository.save(existing);
        methodologyChangeLogRepository.save(new MethodologyChangeLog(
            saved,
            "DEFINITION_UPDATE",
            saved.getLifecycleStatus(),
            saved.getLifecycleStatus(),
            "Methodology definition attributes updated while mutable.",
            actor != null ? actor : "SYSTEM"
        ));
        return saved;
    }

    /**
     * Executes controlled lifecycle transition with rigorous validation constraints.
     * Enforces the core epistemic invariant: IMPLEMENTED != VALIDATED != APPROVED PRODUCTION METHODOLOGY.
     */
    @Transactional
    public MethodologyVersion transitionLifecycle(
        Long versionId,
        String targetStatus,
        String evidenceOrRecord,
        String actor
    ) {
        MethodologyVersion version = methodologyVersionRepository.findById(versionId)
            .orElseThrow(() -> new IllegalArgumentException("MethodologyVersion not found: " + versionId));

        String currentStatus = version.getLifecycleStatus();
        if (currentStatus.equalsIgnoreCase(targetStatus)) {
            return version;
        }

        if ("RETIRED".equalsIgnoreCase(currentStatus)) {
            throw new IllegalStateException("Cannot transition a 'RETIRED' methodology version. Retired versions are immutable historical records.");
        }

        switch (targetStatus.toUpperCase()) {
            case "VALIDATED" -> {
                // Rule 1: Only CANDIDATE can transition to VALIDATED. Cannot demote from APPROVED.
                if (!"CANDIDATE".equalsIgnoreCase(currentStatus)) {
                    throw new IllegalStateException(String.format(
                        "Cannot transition methodology from '%s' to 'VALIDATED'. Only 'CANDIDATE' methodologies can be validated.",
                        currentStatus
                    ));
                }
                // Rule 2: Cannot transition to VALIDATED without empirical validation evidence reference
                if (evidenceOrRecord == null || evidenceOrRecord.isBlank()) {
                    throw new IllegalArgumentException(
                        "Cannot transition methodology to VALIDATED without empirical validation evidence reference. In YUKIRA, empirical validation requires verifiable backtests or empirical evidence."
                    );
                }
                version.setLifecycleStatus("VALIDATED");
                version.setValidationStatus("VALIDATED");
                version.setValidationEvidenceReference(evidenceOrRecord);
            }
            case "APPROVED" -> {
                // Rule 3: Cannot jump directly from CANDIDATE to APPROVED without having achieved VALIDATED status first
                if (!"VALIDATED".equalsIgnoreCase(currentStatus)) {
                    throw new IllegalStateException(String.format(
                        "Cannot approve methodology directly from '%s'. In YUKIRA, a methodology must first achieve VALIDATED status with empirical evidence before production approval.",
                        currentStatus
                    ));
                }
                // Rule 4: Cannot approve without a formal approval record and actor
                if (evidenceOrRecord == null || evidenceOrRecord.isBlank()) {
                    throw new IllegalArgumentException("Cannot approve methodology without a formal approval record.");
                }
                if (actor == null || actor.isBlank()) {
                    throw new IllegalArgumentException("Cannot approve methodology without an identified approving actor.");
                }
                version.setLifecycleStatus("APPROVED");
                version.setApprovalStatus("APPROVED");
                version.setApprovalRecord(evidenceOrRecord);
                version.setApprovedBy(actor);
                version.setApprovedAt(OffsetDateTime.now());
            }
            case "RETIRED" -> {
                version.setLifecycleStatus("RETIRED");
                version.setApprovalStatus("RETIRED");
            }
            case "CANDIDATE" -> {
                // Rule 5: Backward transition from APPROVED or VALIDATED to CANDIDATE is prohibited.
                throw new IllegalStateException(String.format(
                    "Cannot demote methodology from '%s' back to 'CANDIDATE'. Create a new candidate version instead.",
                    currentStatus
                ));
            }
            default -> throw new IllegalArgumentException("Unknown methodology lifecycle status: " + targetStatus);
        }

        MethodologyVersion saved = methodologyVersionRepository.save(version);
        methodologyChangeLogRepository.save(new MethodologyChangeLog(
            saved,
            "LIFECYCLE_TRANSITION",
            currentStatus,
            targetStatus.toUpperCase(),
            evidenceOrRecord != null ? evidenceOrRecord : "Lifecycle transition to " + targetStatus,
            actor != null ? actor : "SYSTEM"
        ));
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<MethodologyVersion> getMethodologyVersion(String methodologyCode, String versionTag) {
        return methodologyVersionRepository.findByMethodologyCodeAndVersionTag(methodologyCode, versionTag);
    }

    @Transactional(readOnly = true)
    public Optional<MetricDefinition> getMetricDefinition(String metricCode) {
        return metricDefinitionRepository.findByMetricCode(metricCode);
    }

    @Transactional(readOnly = true)
    public List<MethodologyChangeLog> getChangeHistory(Long methodologyVersionId) {
        return methodologyChangeLogRepository.findByMethodologyVersionIdOrderByCreatedAtAsc(methodologyVersionId);
    }
}
