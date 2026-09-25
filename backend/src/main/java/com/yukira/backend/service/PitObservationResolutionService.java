package com.yukira.backend.service;

import com.yukira.backend.domain.entity.BenchmarkObservation;
import com.yukira.backend.domain.entity.NavObservation;
import com.yukira.backend.domain.entity.RiskFreeObservation;
import com.yukira.backend.domain.entity.ValidationIssue;
import com.yukira.backend.repository.BenchmarkObservationRepository;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.RiskFreeObservationRepository;
import com.yukira.backend.repository.ValidationIssueRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class PitObservationResolutionService {

    private final NavObservationRepository navObservationRepository;
    private final RiskFreeObservationRepository riskFreeObservationRepository;
    private final BenchmarkObservationRepository benchmarkObservationRepository;
    private final ValidationIssueRepository validationIssueRepository;

    public PitObservationResolutionService(
        NavObservationRepository navObservationRepository,
        RiskFreeObservationRepository riskFreeObservationRepository,
        BenchmarkObservationRepository benchmarkObservationRepository,
        ValidationIssueRepository validationIssueRepository
    ) {
        this.navObservationRepository = navObservationRepository;
        this.riskFreeObservationRepository = riskFreeObservationRepository;
        this.benchmarkObservationRepository = benchmarkObservationRepository;
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

    public record RiskFreePitResolutionResult(
        Optional<RiskFreeObservation> authoritativeObservation,
        List<RiskFreeObservation> eligibleRevisions,
        boolean isAmbiguous,
        String diagnosticReason
    ) {
        public static RiskFreePitResolutionResult resolved(RiskFreeObservation obs, List<RiskFreeObservation> eligible) {
            return new RiskFreePitResolutionResult(Optional.of(obs), eligible, false, null);
        }

        public static RiskFreePitResolutionResult missing(String reason) {
            return new RiskFreePitResolutionResult(Optional.empty(), Collections.emptyList(), false, reason);
        }

        public static RiskFreePitResolutionResult ambiguous(List<RiskFreeObservation> eligible, String reason) {
            return new RiskFreePitResolutionResult(Optional.empty(), eligible, true, reason);
        }
    }

    public record BenchmarkPitResolutionResult(
        Optional<BenchmarkObservation> authoritativeObservation,
        List<BenchmarkObservation> eligibleRevisions,
        boolean isAmbiguous,
        String diagnosticReason
    ) {
        public static BenchmarkPitResolutionResult resolved(BenchmarkObservation obs, List<BenchmarkObservation> eligible) {
            return new BenchmarkPitResolutionResult(Optional.of(obs), eligible, false, null);
        }

        public static BenchmarkPitResolutionResult missing(String reason) {
            return new BenchmarkPitResolutionResult(Optional.empty(), Collections.emptyList(), false, reason);
        }

        public static BenchmarkPitResolutionResult ambiguous(List<BenchmarkObservation> eligible, String reason) {
            return new BenchmarkPitResolutionResult(Optional.empty(), eligible, true, reason);
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

    /**
     * Resolves the authoritative risk-free observation for (benchmarkCode, effectiveDate)
     * strictly at knowledgeCutoffTime according to the approved 6-step PIT selection algorithm.
     */
    public RiskFreePitResolutionResult resolveAuthoritativeRiskFreeObservation(
        String benchmarkCode,
        LocalDate effectiveDate,
        OffsetDateTime knowledgeCutoffTime
    ) {
        if (benchmarkCode == null || benchmarkCode.isBlank() || effectiveDate == null || knowledgeCutoffTime == null) {
            throw new IllegalArgumentException("benchmarkCode, effectiveDate, and knowledgeCutoffTime must not be null");
        }

        List<RiskFreeObservation> allRevisions = riskFreeObservationRepository
            .findByBenchmarkCodeAndEffectiveDate(benchmarkCode, effectiveDate);

        if (allRevisions.isEmpty()) {
            return RiskFreePitResolutionResult.missing(
                String.format("No risk-free observations found for benchmark %s on date %s", benchmarkCode, effectiveDate)
            );
        }

        List<RiskFreeObservation> eligibleRevisions = allRevisions.stream()
            .filter(r -> r.getAvailabilityTime() != null && !r.getAvailabilityTime().isAfter(knowledgeCutoffTime))
            .toList();

        if (eligibleRevisions.isEmpty()) {
            return RiskFreePitResolutionResult.missing(
                String.format("All %d revision(s) for benchmark %s on %s have availability_time after knowledge cutoff %s",
                    allRevisions.size(), benchmarkCode, effectiveDate, knowledgeCutoffTime)
            );
        }

        OffsetDateTime maxAvailabilityTime = eligibleRevisions.stream()
            .map(RiskFreeObservation::getAvailabilityTime)
            .max(Comparator.naturalOrder())
            .orElseThrow();

        List<RiskFreeObservation> latestCandidates = eligibleRevisions.stream()
            .filter(r -> r.getAvailabilityTime().equals(maxAvailabilityTime))
            .toList();

        boolean hasConflictingValues = latestCandidates.stream()
            .anyMatch(c -> c.getQuotedYield().compareTo(latestCandidates.get(0).getQuotedYield()) != 0);

        if (hasConflictingValues) {
            ValidationIssue issue = new ValidationIssue(
                "RISK_FREE_BENCHMARK",
                0L,
                "PIT_AUTHORITY_AMBIGUITY",
                String.format("Ambiguous authority for risk-free benchmark %s on date %s at cutoff %s: multiple eligible revisions at latest availability instant %s have conflicting values",
                    benchmarkCode, effectiveDate, knowledgeCutoffTime, maxAvailabilityTime)
            );
            issue.setQualityAssessment("SUSPICIOUS");
            issue.setIntegrityCondition("CONFLICTING");
            validationIssueRepository.save(issue);

            return RiskFreePitResolutionResult.ambiguous(
                eligibleRevisions,
                String.format("Conflicting eligible risk-free revisions with unresolvable authority at latest availability instant %s as of cutoff %s",
                    maxAvailabilityTime, knowledgeCutoffTime)
            );
        }

        Comparator<RiskFreeObservation> tieBreaker = Comparator
            .comparing(RiskFreeObservation::getRevisionSeq)
            .thenComparing(obs -> obs.getSourceArtifact() != null ? obs.getSourceArtifact().getId() : 0L);

        RiskFreeObservation authoritative = Collections.max(latestCandidates, tieBreaker);
        return RiskFreePitResolutionResult.resolved(authoritative, eligibleRevisions);
    }

    /**
     * Resolves the authoritative benchmark observation for (benchmarkId, effectiveDate)
     * strictly at knowledgeCutoffTime according to the approved 6-step PIT selection algorithm.
     */
    public BenchmarkPitResolutionResult resolveAuthoritativeBenchmarkObservation(
        Long benchmarkId,
        LocalDate effectiveDate,
        OffsetDateTime knowledgeCutoffTime
    ) {
        if (benchmarkId == null || effectiveDate == null || knowledgeCutoffTime == null) {
            throw new IllegalArgumentException("benchmarkId, effectiveDate, and knowledgeCutoffTime must not be null");
        }

        List<BenchmarkObservation> allRevisions = benchmarkObservationRepository
            .findByBenchmarkIdAndEffectiveDate(benchmarkId, effectiveDate);

        if (allRevisions.isEmpty()) {
            return BenchmarkPitResolutionResult.missing(
                String.format("No benchmark observations found for benchmark %d on date %s", benchmarkId, effectiveDate)
            );
        }

        List<BenchmarkObservation> eligibleRevisions = allRevisions.stream()
            .filter(r -> r.getAvailabilityTime() != null && !r.getAvailabilityTime().isAfter(knowledgeCutoffTime))
            .toList();

        if (eligibleRevisions.isEmpty()) {
            return BenchmarkPitResolutionResult.missing(
                String.format("All %d revision(s) for benchmark %d on %s have availability_time after knowledge cutoff %s",
                    allRevisions.size(), benchmarkId, effectiveDate, knowledgeCutoffTime)
            );
        }

        OffsetDateTime maxAvailabilityTime = eligibleRevisions.stream()
            .map(BenchmarkObservation::getAvailabilityTime)
            .max(Comparator.naturalOrder())
            .orElseThrow();

        List<BenchmarkObservation> latestCandidates = eligibleRevisions.stream()
            .filter(r -> r.getAvailabilityTime().equals(maxAvailabilityTime))
            .toList();

        boolean hasConflictingValues = latestCandidates.stream()
            .anyMatch(c -> c.getIndexLevel().compareTo(latestCandidates.get(0).getIndexLevel()) != 0);

        if (hasConflictingValues) {
            ValidationIssue issue = new ValidationIssue(
                "BENCHMARK",
                benchmarkId,
                "PIT_AUTHORITY_AMBIGUITY",
                String.format("Ambiguous authority for benchmark %d on date %s at cutoff %s: multiple eligible revisions at latest availability instant %s have conflicting values",
                    benchmarkId, effectiveDate, knowledgeCutoffTime, maxAvailabilityTime)
            );
            issue.setQualityAssessment("SUSPICIOUS");
            issue.setIntegrityCondition("CONFLICTING");
            validationIssueRepository.save(issue);

            return BenchmarkPitResolutionResult.ambiguous(
                eligibleRevisions,
                String.format("Conflicting eligible benchmark revisions with unresolvable authority at latest availability instant %s as of cutoff %s",
                    maxAvailabilityTime, knowledgeCutoffTime)
            );
        }

        Comparator<BenchmarkObservation> tieBreaker = Comparator
            .comparing(BenchmarkObservation::getRevisionSeq)
            .thenComparing(obs -> obs.getSourceArtifact() != null ? obs.getSourceArtifact().getId() : 0L);

        BenchmarkObservation authoritative = Collections.max(latestCandidates, tieBreaker);
        return BenchmarkPitResolutionResult.resolved(authoritative, eligibleRevisions);
    }
}
