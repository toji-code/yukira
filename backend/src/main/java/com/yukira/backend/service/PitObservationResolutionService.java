package com.yukira.backend.service;

import com.yukira.backend.domain.entity.NavObservation;
import com.yukira.backend.domain.entity.ValidationIssue;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.ValidationIssueRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class PitObservationResolutionService {

    private final NavObservationRepository navObservationRepository;
    private final ValidationIssueRepository validationIssueRepository;

    public PitObservationResolutionService(
        NavObservationRepository navObservationRepository,
        ValidationIssueRepository validationIssueRepository
    ) {
        this.navObservationRepository = navObservationRepository;
        this.validationIssueRepository = validationIssueRepository;
    }

    public record PitResolutionResult(
        Optional<NavObservation> authoritativeObservation,
        List<NavObservation> eligibleRevisions,
        boolean isAmbiguous,
        String diagnosticReason
    ) {
        public static PitResolutionResult resolved(NavObservation obs, List<NavObservation> eligible) {
            return new PitResolutionResult(Optional.of(obs), eligible, false, null);
        }

        public static PitResolutionResult missing(String reason) {
            return new PitResolutionResult(Optional.empty(), Collections.emptyList(), false, reason);
        }

        public static PitResolutionResult ambiguous(List<NavObservation> eligible, String reason) {
            return new PitResolutionResult(Optional.empty(), eligible, true, reason);
        }
    }

    /**
     * Resolves the authoritative observation for a logical observation (schemeOptionId, effectiveDate)
     * strictly at knowledgeCutoffTime according to the approved 6-step PIT selection algorithm:
     *
     * 1. Identify all immutable revisions for (schemeOptionId, effectiveDate).
     * 2. Filter to revisions eligible at knowledgeCutoffTime (availability_time <= knowledgeCutoffTime).
     * 3. Exclude revisions published after knowledgeCutoffTime (prevent lookahead bias).
     * 4. Determine authoritative version among eligible revisions (latest availability / supersession).
     * 5. Resolve ties deterministically (source artifact / revision sequence priority).
     * 6. Return explicit ambiguity/data-quality state if authority cannot be established.
     */
    public PitResolutionResult resolveAuthoritativeObservation(
        Long schemeOptionId,
        LocalDate effectiveDate,
        OffsetDateTime knowledgeCutoffTime
    ) {
        if (schemeOptionId == null || effectiveDate == null || knowledgeCutoffTime == null) {
            throw new IllegalArgumentException("schemeOptionId, effectiveDate, and knowledgeCutoffTime must not be null");
        }

        // Step 1: Gather all immutable revisions for the logical observation
        List<NavObservation> allRevisions = navObservationRepository
            .findBySchemeOptionIdAndEffectiveDate(schemeOptionId, effectiveDate);

        if (allRevisions.isEmpty()) {
            return PitResolutionResult.missing(
                String.format("No observations found for scheme option %d on date %s", schemeOptionId, effectiveDate)
            );
        }

        // Step 2 & 3: Restrict to revisions whose availability is known to be <= knowledgeCutoffTime
        List<NavObservation> eligibleRevisions = allRevisions.stream()
            .filter(r -> r.getAvailabilityTime() != null && !r.getAvailabilityTime().isAfter(knowledgeCutoffTime))
            .toList();

        if (eligibleRevisions.isEmpty()) {
            return PitResolutionResult.missing(
                String.format("All %d revision(s) for scheme option %d on %s have availability_time after knowledge cutoff %s",
                    allRevisions.size(), schemeOptionId, effectiveDate, knowledgeCutoffTime)
            );
        }

        // Step 3: Identify the latest eligible availability_time
        OffsetDateTime maxAvailabilityTime = eligibleRevisions.stream()
            .map(NavObservation::getAvailabilityTime)
            .max(Comparator.naturalOrder())
            .orElseThrow();

        // Step 4: Inspect ALL observations at that latest eligible availability_time
        List<NavObservation> latestCandidates = eligibleRevisions.stream()
            .filter(r -> r.getAvailabilityTime().equals(maxAvailabilityTime))
            .toList();

        // Step 5 & 6: Check for conflicting values at the latest availability instant
        boolean hasConflictingValues = latestCandidates.stream()
            .anyMatch(c -> c.getNavValue().compareTo(latestCandidates.get(0).getNavValue()) != 0);

        if (hasConflictingValues) {
            // Step 7: For conflicting values, return explicit PIT authority ambiguity — do not silently resolve
            ValidationIssue issue = new ValidationIssue(
                "SCHEME_OPTION",
                schemeOptionId,
                "PIT_AUTHORITY_AMBIGUITY",
                String.format("Ambiguous authority for date %s at cutoff %s: multiple eligible revisions at latest availability instant %s have conflicting values",
                    effectiveDate, knowledgeCutoffTime, maxAvailabilityTime)
            );
            issue.setQualityAssessment("SUSPICIOUS");
            issue.setIntegrityCondition("CONFLICTING");
            validationIssueRepository.save(issue);

            return PitResolutionResult.ambiguous(
                eligibleRevisions,
                String.format("Conflicting eligible revisions with unresolvable authority at latest availability instant %s as of cutoff %s",
                    maxAvailabilityTime, knowledgeCutoffTime)
            );
        }

        // If all observations at maxAvailabilityTime have identical values (or exactly 1 exists):
        // Deterministically break any tie among identical-value observations using revision_seq DESC, then source_artifact_id DESC
        Comparator<NavObservation> tieBreaker = Comparator
            .comparing(NavObservation::getRevisionSeq)
            .thenComparing(obs -> obs.getSourceArtifact() != null ? obs.getSourceArtifact().getId() : 0L);

        NavObservation authoritative = Collections.max(latestCandidates, tieBreaker);
        return PitResolutionResult.resolved(authoritative, eligibleRevisions);
    }
}
