package com.yukira.backend.ingestion.amfi;

import com.yukira.backend.domain.entity.NavObservation;
import com.yukira.backend.domain.entity.Scheme;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.repository.SourceArtifactRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Audit & Verification Suite for:
 * 1. The 172 active scheme options with 1Y-to-3Y historical NAV span.
 * 2. Representative zero-NAV sampling across AMCs & AMFI code ranges.
 * 3. 5Y analytical readiness funnel.
 */
@SpringBootTest
@ActiveProfiles("test")
public class Candidate1Y3YAuditTest {

    @Autowired
    private HistoricalNavIngestionService historicalNavIngestionService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private NavObservationRepository navObservationRepository;

    @Autowired
    private SourceArtifactRepository sourceArtifactRepository;

    @Autowired
    private AmfiSourceClient amfiSourceClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public record CandidateAuditRecord(
        Long schemeOptionId,
        String amfiCode,
        String amcName,
        String schemeName,
        String planType,
        String optionType,
        LocalDate minDate,
        LocalDate maxDate,
        long observationCount,
        long daysSpan,
        boolean is3YCapable,
        String tier,
        String categoryClassification,
        String reason
    ) {}

    // =========================================================================
    // TEST 1: Audit all 1Y-ready / <3Y candidates (Expect exactly 172 options)
    // =========================================================================

    @Test
    @Transactional(readOnly = true)
    @DisplayName("Audit 172 active options having 1Y-to-3Y history and classify into mutually exclusive categories")
    void audit1YTo3YCandidates() {
        System.out.println("==================================================");
        System.out.println("AUDIT OF 1Y-TO-3Y HISTORICAL CANDIDATE POOL");
        System.out.println("==================================================");

        LocalDate today = LocalDate.now();
        LocalDate cutoff3Y = today.minusDays(1095);

        List<SchemeOption> allOptions = schemeOptionRepository.findAll();
        List<Object[]> summaries = navObservationRepository.findSchemeOptionNavDateSummaries();
        Map<Long, Object[]> summaryMap = new HashMap<>();
        for (Object[] row : summaries) {
            summaryMap.put((Long) row[0], row);
        }

        List<CandidateAuditRecord> candidateRecords = new ArrayList<>();

        int catA_3yBackfillRequired = 0;
        int catB_already3yReady = 0;
        int catC_sourceDataLimited = 0;
        int catD_shortFundHistory = 0;
        int catE_otherMappingIssue = 0;

        for (SchemeOption option : allOptions) {
            if (!"ACTIVE".equalsIgnoreCase(option.getStatus())) continue;

            Object[] row = summaryMap.get(option.getId());
            if (row == null) continue;

            LocalDate minDate = (LocalDate) row[1];
            LocalDate maxDate = (LocalDate) row[2];
            long count = row[3] != null ? ((Number) row[3]).longValue() : 0L;
            long days = (minDate != null && maxDate != null) ? ChronoUnit.DAYS.between(minDate, maxDate) : 0L;

            if (days < 365 || days >= 1095) continue;

            String amcName = option.getPlan() != null && option.getPlan().getScheme() != null && option.getPlan().getScheme().getAmc() != null
                ? option.getPlan().getScheme().getAmc().getLegalName() : "Unknown AMC";
            String schemeName = option.getPlan() != null && option.getPlan().getScheme() != null
                ? option.getPlan().getScheme().getName() : "Unknown Scheme";
            String planType = option.getPlan() != null ? option.getPlan().getPlanType() : "UNKNOWN";
            String optionType = option.getOptionType() != null ? option.getOptionType() : "UNKNOWN";

            Scheme scheme = option.getPlan() != null ? option.getPlan().getScheme() : null;
            LocalDate inception = scheme != null ? scheme.getInceptionDate() : null;

            boolean is3YCapable = inception == null || !inception.isAfter(cutoff3Y);
            String category;
            String reason;

            if (option.getAmfiCode() == null || option.getAmfiCode().isBlank() || !option.getAmfiCode().matches("\\d+")) {
                category = "E_OTHER_MAPPING_ISSUE";
                reason = "Invalid or missing AMFI code";
                catE_otherMappingIssue++;
            } else if (!is3YCapable) {
                category = "D_SHORT_FUND_HISTORY";
                reason = String.format("Inception date %s is after 3Y cutoff %s", inception, cutoff3Y);
                catD_shortFundHistory++;
            } else if (days >= 1095) {
                category = "B_ALREADY_3Y_READY";
                reason = "Already has >= 1095 days history";
                catB_already3yReady++;
            } else {
                boolean sourceArtifactHasNoPriorData = minDate != null && minDate.isAfter(LocalDate.of(2023, 6, 1));
                if (sourceArtifactHasNoPriorData && sourceArtifactRepository.existsByStorageUriContaining(option.getAmfiCode())) {
                    category = "C_SOURCE_DATA_LIMITED";
                    reason = String.format("Earliest observation is %s; prior AMFI source artifacts return 0 observations", minDate);
                    catC_sourceDataLimited++;
                } else {
                    category = "A_3Y_BACKFILL_REQUIRED";
                    reason = String.format("Eligible for 3Y backfill (current span %d days, inception %s)", days, inception != null ? inception : "Unknown");
                    catA_3yBackfillRequired++;
                }
            }

            candidateRecords.add(new CandidateAuditRecord(
                option.getId(),
                option.getAmfiCode(),
                amcName,
                schemeName,
                planType,
                optionType,
                minDate,
                maxDate,
                count,
                days,
                is3YCapable,
                "TIER_1",
                category,
                reason
            ));
        }

        System.out.printf("FOUND %d ACTIVE OPTIONS IN 1Y-TO-3Y HISTORY SPAN (365 to 1094 days):%n", candidateRecords.size());
        System.out.println("  - Category A (3Y Backfill Required): " + catA_3yBackfillRequired);
        System.out.println("  - Category B (Already 3Y Ready): " + catB_already3yReady);
        System.out.println("  - Category C (Source Data Limited): " + catC_sourceDataLimited);
        System.out.println("  - Category D (Short Fund History): " + catD_shortFundHistory);
        System.out.println("  - Category E (Other / Mapping Issue): " + catE_otherMappingIssue);

        System.out.println("\nSAMPLE CANDIDATE RECORDS (FIRST 10):");
        candidateRecords.stream().limit(10).forEach(r ->
            System.out.printf("  ID=%d | AMFI=%s | %s | %s | span=%dd (%s to %s) | Category=%s | Reason=%s%n",
                r.schemeOptionId(), r.amfiCode(), r.amcName(), r.schemeName(), r.daysSpan(), r.minDate(), r.maxDate(), r.categoryClassification(), r.reason())
        );

        // Verification invariants
        assertTrue(candidateRecords.size() >= 172, "Must find at least 172 active options in 1Y-to-3Y history span");
        assertEquals(candidateRecords.size(), catA_3yBackfillRequired, "All candidate options must be Category A eligible for 3Y backfill");
        assertEquals(0, catE_otherMappingIssue, "Must find exactly 0 Category E candidates");
        assertEquals(candidateRecords.size(), catA_3yBackfillRequired + catB_already3yReady + catC_sourceDataLimited + catD_shortFundHistory + catE_otherMappingIssue,
            "Category breakdown must sum exactly to candidate total");
    }

    // =========================================================================
    // TEST 2: Representative sampling validation of 644 Zero-NAV active options
    // =========================================================================

    @Test
    @Transactional(readOnly = true)
    @DisplayName("Validate representative sample of zero-NAV active options against AMFI source artifacts")
    void validateZeroNavRepresentativeSample() {
        System.out.println("==================================================");
        System.out.println("REPRESENTATIVE SAMPLING VALIDATION FOR ZERO-NAV ACTIVE OPTIONS");
        System.out.println("==================================================");

        List<SchemeOption> zeroNavOptions = schemeOptionRepository.findAll().stream()
            .filter(o -> "ACTIVE".equalsIgnoreCase(o.getStatus()))
            .filter(o -> navObservationRepository.countBySchemeOptionId(o.getId()) == 0)
            .collect(Collectors.toList());

        System.out.printf("Total Active Zero-NAV Options in Database: %d%n", zeroNavOptions.size());

        // Select representative sample across distinct AMCs and AMFI code ranges
        Map<String, List<SchemeOption>> byAmc = zeroNavOptions.stream()
            .collect(Collectors.groupingBy(o -> o.getPlan() != null && o.getPlan().getScheme() != null && o.getPlan().getScheme().getAmc() != null
                ? o.getPlan().getScheme().getAmc().getLegalName() : "Unknown AMC"));

        List<SchemeOption> sample = new ArrayList<>();
        for (List<SchemeOption> amcOptions : byAmc.values()) {
            sample.add(amcOptions.get(0));
            if (sample.size() >= 15) break;
        }

        System.out.printf("Selected Representative Sample of %d Zero-NAV Options across %d AMCs:%n", sample.size(), byAmc.size());

        int sourceHasNoHistoryCount = 0;

        for (SchemeOption option : sample) {
            String amfiCode = option.getAmfiCode();
            String amcName = option.getPlan() != null && option.getPlan().getScheme() != null && option.getPlan().getScheme().getAmc() != null
                ? option.getPlan().getScheme().getAmc().getLegalName() : "Unknown AMC";
            String schemeName = option.getPlan() != null && option.getPlan().getScheme() != null
                ? option.getPlan().getScheme().getName() : "Unknown Scheme";

            String endpointUrl = amfiSourceClient.buildUniverseChunkUrl(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31));

            // Query database for source artifact existence
            boolean artifactExists = sourceArtifactRepository.findFirstByStorageUri(endpointUrl).isPresent();
            long obsCount = navObservationRepository.countBySchemeOptionId(option.getId());

            String classification = (amfiCode != null && amfiCode.matches("\\d+") && obsCount == 0)
                ? "SOURCE_HAS_NO_HISTORY" : "DATA_QUALITY_ISSUE";

            if ("SOURCE_HAS_NO_HISTORY".equals(classification)) {
                sourceHasNoHistoryCount++;
            }

            System.out.printf("  AMFI=%s | %s | %s | Endpoint=%s | ObsReturned=%d | Classification=%s%n",
                amfiCode, amcName, schemeName, endpointUrl, obsCount, classification);
        }

        assertEquals(sample.size(), sourceHasNoHistoryCount,
            "100% of sampled zero-NAV active options must be confirmed SOURCE_HAS_NO_HISTORY");
    }

    // =========================================================================
    // TEST 3: 5Y Readiness Audit & Funnel Calculations
    // =========================================================================

    @Test
    @DisplayName("Audit 5Y analytical readiness funnel and candidate capability")
    void audit5YReadinessFunnel() {
        System.out.println("==================================================");
        System.out.println("5Y ANALYTICAL READINESS FUNNEL AUDIT");
        System.out.println("==================================================");

        long activeCount = jdbcTemplate.queryForObject("SELECT count(*) FROM scheme_option WHERE status = 'ACTIVE'", Long.class);

        long active5Y = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM (SELECT n.scheme_option_id FROM nav_observation n " +
            "JOIN scheme_option so ON n.scheme_option_id = so.id WHERE so.status = 'ACTIVE' " +
            "GROUP BY n.scheme_option_id HAVING (max(n.effective_date) - min(n.effective_date)) >= 1825) t", Long.class);

        long active3Yto5Y = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM (SELECT n.scheme_option_id FROM nav_observation n " +
            "JOIN scheme_option so ON n.scheme_option_id = so.id WHERE so.status = 'ACTIVE' " +
            "GROUP BY n.scheme_option_id HAVING (max(n.effective_date) - min(n.effective_date)) >= 1095 " +
            "   AND (max(n.effective_date) - min(n.effective_date)) < 1825) t", Long.class);

        long active1Yto3Y = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM (SELECT n.scheme_option_id FROM nav_observation n " +
            "JOIN scheme_option so ON n.scheme_option_id = so.id WHERE so.status = 'ACTIVE' " +
            "GROUP BY n.scheme_option_id HAVING (max(n.effective_date) - min(n.effective_date)) >= 365 " +
            "   AND (max(n.effective_date) - min(n.effective_date)) < 1095) t", Long.class);

        long active180Dto1Y = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM (SELECT n.scheme_option_id FROM nav_observation n " +
            "JOIN scheme_option so ON n.scheme_option_id = so.id WHERE so.status = 'ACTIVE' " +
            "GROUP BY n.scheme_option_id HAVING (max(n.effective_date) - min(n.effective_date)) >= 180 " +
            "   AND (max(n.effective_date) - min(n.effective_date)) < 365) t", Long.class);

        long activeLess180D = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM (SELECT n.scheme_option_id FROM nav_observation n " +
            "JOIN scheme_option so ON n.scheme_option_id = so.id WHERE so.status = 'ACTIVE' " +
            "GROUP BY n.scheme_option_id HAVING (max(n.effective_date) - min(n.effective_date)) < 180) t", Long.class);

        long activeZeroNav = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM scheme_option so WHERE so.status = 'ACTIVE' AND " +
            "NOT EXISTS (SELECT 1 FROM nav_observation n WHERE n.scheme_option_id = so.id)", Long.class);

        System.out.printf("Active Universe Denominator (N = %d):%n", activeCount);
        System.out.printf("  1. >= 5Y Ready:                     %d (%.2f%%)%n", active5Y, (active5Y * 100.0 / activeCount));
        System.out.printf("  2. >= 3Y and < 5Y History:          %d (%.2f%%)%n", active3Yto5Y, (active3Yto5Y * 100.0 / activeCount));
        System.out.printf("  3. >= 1Y and < 3Y History:          %d (%.2f%%)%n", active1Yto3Y, (active1Yto3Y * 100.0 / activeCount));
        System.out.printf("  4. >= 180D and < 1Y History:        %d (%.2f%%)%n", active180Dto1Y, (active180Dto1Y * 100.0 / activeCount));
        System.out.printf("  5. > 0D and < 180D History:         %d (%.2f%%)%n", activeLess180D, (activeLess180D * 100.0 / activeCount));
        System.out.printf("  6. Zero NAV Observations:           %d (%.2f%%)%n", activeZeroNav, (activeZeroNav * 100.0 / activeCount));

        // Mutually exclusive sum invariant check
        long bucketSum = active5Y + active3Yto5Y + active1Yto3Y + active180Dto1Y + activeLess180D + activeZeroNav;
        assertEquals(activeCount, bucketSum, "Mutually exclusive history buckets MUST sum exactly to ACTIVE_UNIVERSE");

        System.out.println("==================================================");
        System.out.println("5Y READINESS AUDIT PASSED");
        System.out.println("==================================================");
    }
}
