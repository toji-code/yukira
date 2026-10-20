package com.yukira.backend.service;

import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.domain.entity.BenchmarkConstituent;
import com.yukira.backend.domain.entity.Security;
import com.yukira.backend.domain.entity.SecurityIdentifier;
import com.yukira.backend.dto.portfolio.BenchmarkReconstitutionResponseDto;
import com.yukira.backend.dto.portfolio.ReconstitutionMemberDto;
import com.yukira.backend.repository.BenchmarkConstituentRepository;
import com.yukira.backend.repository.BenchmarkRepository;
import com.yukira.backend.repository.SecurityIdentifierRepository;
import com.yukira.backend.repository.SecurityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Deterministic calculation engine for Point-in-Time Multi-Period Benchmark Reconstitution Tracking.
 *
 * Epistemic & Bitemporal Principles (AGENTS.md):
 * 1. Zero Fake Data: Distinguishes observed two-point transitions from official reconstitution events.
 * 2. Canonical Security Resolution: Maps constituents across corporate actions (stock splits, ISIN migrations).
 * 3. Exact Point-in-Time Query: Respects availabilityTime <= knowledgeCutoff for historical invariance.
 * 4. Auditable Disclosures: Reports exact entries, exits, retentions, and unresolved identifiers.
 */
@Service
@SuppressWarnings("null")
public class BenchmarkReconstitutionService {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkReconstitutionService.class);
    private static final MathContext MC = MathContext.DECIMAL128;

    private final BenchmarkRepository benchmarkRepository;
    private final BenchmarkConstituentRepository constituentRepository;
    private final SecurityRepository securityRepository;
    private final SecurityIdentifierRepository securityIdentifierRepository;

    public BenchmarkReconstitutionService(
        BenchmarkRepository benchmarkRepository,
        BenchmarkConstituentRepository constituentRepository,
        SecurityRepository securityRepository,
        SecurityIdentifierRepository securityIdentifierRepository
    ) {
        this.benchmarkRepository = benchmarkRepository;
        this.constituentRepository = constituentRepository;
        this.securityRepository = securityRepository;
        this.securityIdentifierRepository = securityIdentifierRepository;
    }

    @Transactional(readOnly = true)
    public BenchmarkReconstitutionResponseDto calculateReconstitution(
        String benchmarkCode,
        LocalDate fromDate,
        LocalDate toDate,
        OffsetDateTime knowledgeCutoff
    ) {
        OffsetDateTime cutoff = knowledgeCutoff != null ? knowledgeCutoff : OffsetDateTime.now();

        Benchmark benchmark = benchmarkRepository.findByCode(benchmarkCode != null ? benchmarkCode : "NIFTY_500_TRI")
            .orElseThrow(() -> new IllegalArgumentException("Benchmark not found: " + benchmarkCode));

        List<LocalDate> availableDates = constituentRepository.findAvailableCompositionDates(benchmark.getId(), cutoff);
        if (availableDates.isEmpty()) {
            return buildEmptyResponse(benchmark, fromDate, toDate, cutoff, "No benchmark constituent snapshots found under knowledge cutoff.");
        }

        // Resolve earlier (from) and later (to) observation dates
        LocalDate resolvedFrom = fromDate;
        LocalDate resolvedTo = toDate;

        if (resolvedTo == null) {
            resolvedTo = availableDates.get(0); // latest available
        }
        if (resolvedFrom == null) {
            // Find nearest prior observation or earliest
            resolvedFrom = availableDates.size() > 1 ? availableDates.get(availableDates.size() - 1) : availableDates.get(0);
        }

        if (resolvedFrom.isAfter(resolvedTo)) {
            LocalDate tmp = resolvedFrom;
            resolvedFrom = resolvedTo;
            resolvedTo = tmp;
        }

        List<BenchmarkConstituent> fromList = constituentRepository.findByBenchmarkAndDateAndCutoff(benchmark.getId(), resolvedFrom, cutoff);
        List<BenchmarkConstituent> toList = constituentRepository.findByBenchmarkAndDateAndCutoff(benchmark.getId(), resolvedTo, cutoff);

        if (fromList.isEmpty() || toList.isEmpty()) {
            return buildEmptyResponse(benchmark, resolvedFrom, resolvedTo, cutoff,
                "Insufficient observations: FromDate count=" + fromList.size() + ", ToDate count=" + toList.size());
        }

        long daysBetween = ChronoUnit.DAYS.between(resolvedFrom, resolvedTo);
        String observationType = (daysBetween <= 180) ? "OFFICIAL_SEMI_ANNUAL_RECONSTITUTION" : "TWO_POINT_OBSERVED_TRANSITION";

        // Index earlier snapshot by Security ID (preferred) or ISIN
        Map<String, BenchmarkConstituent> fromByIsin = new HashMap<>();
        Map<Long, BenchmarkConstituent> fromBySecId = new HashMap<>();
        int unresolvedFrom = 0;

        for (BenchmarkConstituent bc : fromList) {
            fromByIsin.put(bc.getIsin(), bc);
            if (bc.getSecurity() != null) {
                fromBySecId.put(bc.getSecurity().getId(), bc);
            } else {
                unresolvedFrom++;
            }
        }

        // Index later snapshot
        Map<String, BenchmarkConstituent> toByIsin = new HashMap<>();
        Map<Long, BenchmarkConstituent> toBySecId = new HashMap<>();
        int unresolvedTo = 0;

        for (BenchmarkConstituent bc : toList) {
            toByIsin.put(bc.getIsin(), bc);
            if (bc.getSecurity() != null) {
                toBySecId.put(bc.getSecurity().getId(), bc);
            } else {
                unresolvedTo++;
            }
        }

        List<ReconstitutionMemberDto> retained = new ArrayList<>();
        List<ReconstitutionMemberDto> entered = new ArrayList<>();
        List<ReconstitutionMemberDto> exited = new ArrayList<>();

        Set<Long> processedSecIds = new HashSet<>();
        Set<String> processedIsins = new HashSet<>();

        // 1. Process To-list (find Retained and Entered)
        for (BenchmarkConstituent toBc : toList) {
            Long secId = toBc.getSecurity() != null ? toBc.getSecurity().getId() : null;
            String isin = toBc.getIsin();

            BenchmarkConstituent fromMatch = null;
            if (secId != null && fromBySecId.containsKey(secId)) {
                fromMatch = fromBySecId.get(secId);
                processedSecIds.add(secId);
            } else if (fromByIsin.containsKey(isin)) {
                fromMatch = fromByIsin.get(isin);
                processedIsins.add(isin);
            }

            if (fromMatch != null) {
                // RETAINED
                String note = null;
                if (!toBc.getIsin().equalsIgnoreCase(fromMatch.getIsin())) {
                    note = "Corporate action: ISIN migrated from " + fromMatch.getIsin() + " to " + toBc.getIsin();
                }
                retained.add(new ReconstitutionMemberDto(
                    toBc.getIsin(),
                    toBc.getSymbol(),
                    toBc.getCompanyName(),
                    toBc.getIndustry(),
                    secId,
                    toBc.getSecurity() != null ? toBc.getSecurity().getCanonicalName() : toBc.getCompanyName(),
                    fromMatch.getWeight(),
                    toBc.getWeight(),
                    "RETAINED",
                    note
                ));
            } else {
                // ENTERED
                entered.add(new ReconstitutionMemberDto(
                    toBc.getIsin(),
                    toBc.getSymbol(),
                    toBc.getCompanyName(),
                    toBc.getIndustry(),
                    secId,
                    toBc.getSecurity() != null ? toBc.getSecurity().getCanonicalName() : toBc.getCompanyName(),
                    null,
                    toBc.getWeight(),
                    "ENTERED",
                    null
                ));
            }
        }

        // 2. Process From-list (find Exited)
        for (BenchmarkConstituent fromBc : fromList) {
            Long secId = fromBc.getSecurity() != null ? fromBc.getSecurity().getId() : null;
            String isin = fromBc.getIsin();

            boolean wasMatched = (secId != null && processedSecIds.contains(secId)) || processedIsins.contains(isin);
            if (!wasMatched) {
                // EXITED
                exited.add(new ReconstitutionMemberDto(
                    fromBc.getIsin(),
                    fromBc.getSymbol(),
                    fromBc.getCompanyName(),
                    fromBc.getIndustry(),
                    secId,
                    fromBc.getSecurity() != null ? fromBc.getSecurity().getCanonicalName() : fromBc.getCompanyName(),
                    fromBc.getWeight(),
                    null,
                    "EXITED",
                    null
                ));
            }
        }

        // Sort lists
        retained.sort(Comparator.comparing(ReconstitutionMemberDto::companyName));
        entered.sort(Comparator.comparing(ReconstitutionMemberDto::companyName));
        exited.sort(Comparator.comparing(ReconstitutionMemberDto::companyName));

        int totalFrom = fromList.size();
        int totalTo = toList.size();
        int retainedCount = retained.size();
        int enteredCount = entered.size();
        int exitedCount = exited.size();

        BigDecimal turnoverPct = BigDecimal.ZERO;
        if (totalTo > 0) {
            turnoverPct = BigDecimal.valueOf(enteredCount)
                .divide(BigDecimal.valueOf(totalTo), MC)
                .multiply(BigDecimal.valueOf(100), MC)
                .setScale(2, RoundingMode.HALF_UP);
        }

        String coverageStatus = "VERIFIED_PARTIAL";
        if (daysBetween > 365 * 2) {
            // Provenance gap caveat
            coverageStatus = "VERIFIED_PARTIAL"; // Two authenticated boundary points, long interval
        }

        String disclaimer = "Reconstitution tracking identifies constituent entries, exits, and migrations between authentic Point-in-Time benchmark snapshots. " +
            (daysBetween > 180 ? "Notice: Snapshot interval spans " + daysBetween + " days; results report net two-point observed transitions, not continuous daily reconstitution events."
                               : "Official semi-annual index reconstitution interval.");

        String shaFrom = fromList.get(0).getSourceArtifact() != null ? fromList.get(0).getSourceArtifact().getSha256Hash() : "N/A";
        String shaTo = toList.get(0).getSourceArtifact() != null ? toList.get(0).getSourceArtifact().getSha256Hash() : "N/A";

        return new BenchmarkReconstitutionResponseDto(
            benchmark.getId(),
            benchmark.getCode(),
            benchmark.getName(),
            resolvedFrom,
            resolvedTo,
            daysBetween,
            observationType,
            coverageStatus,
            totalFrom,
            totalTo,
            retainedCount,
            enteredCount,
            exitedCount,
            turnoverPct,
            unresolvedFrom + unresolvedTo,
            entered,
            exited,
            retained,
            disclaimer,
            shaFrom,
            shaTo,
            cutoff.toString()
        );
    }

    private BenchmarkReconstitutionResponseDto buildEmptyResponse(
        Benchmark benchmark, LocalDate fromDate, LocalDate toDate, OffsetDateTime cutoff, String message
    ) {
        return new BenchmarkReconstitutionResponseDto(
            benchmark.getId(),
            benchmark.getCode(),
            benchmark.getName(),
            fromDate,
            toDate,
            0,
            "UNAVAILABLE",
            "INSUFFICIENT_DATA",
            0, 0, 0, 0, 0,
            BigDecimal.ZERO,
            0,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            message,
            "N/A", "N/A",
            cutoff.toString()
        );
    }
}
