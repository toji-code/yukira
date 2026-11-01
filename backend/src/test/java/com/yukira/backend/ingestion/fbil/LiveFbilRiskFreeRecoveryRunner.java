package com.yukira.backend.ingestion.fbil;

import com.yukira.backend.domain.entity.RiskFreeObservation;
import com.yukira.backend.repository.RiskFreeObservationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Operational Runner for verifying recovered authentic FBIL 91-Day T-Bill observations
 * for early January 2021 (2021-01-01 to 2021-01-15).
 *
 * Verifies:
 * 1. Authentic observations present in database.
 * 2. Idempotency: re-running ingestion skips all 11 observations with 0 insertions and 0 revisions.
 * 3. Continuity: calendar gap between trading dates is <= 3 days (normal weekend gap).
 */
@SpringBootTest(classes = com.yukira.backend.BackendApplication.class)
@Transactional
@Commit
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Live FBIL Risk-Free Recovery Runner (2021-01-01 to 2021-01-15)")
public class LiveFbilRiskFreeRecoveryRunner {

    private static final Logger log = LoggerFactory.getLogger(LiveFbilRiskFreeRecoveryRunner.class);

    @Autowired
    private FbilTBillIngestionService ingestionService;

    @Autowired
    private RiskFreeObservationRepository riskFreeObservationRepository;

    private static final String BENCHMARK_CODE = "FBIL_91D_TBILL";
    private static final LocalDate START_DATE = LocalDate.of(2021, 1, 1);
    private static final LocalDate END_DATE = LocalDate.of(2021, 1, 15);

    @Test
    @Order(1)
    @DisplayName("Idempotency: Verify ingestion of range 2021-01-01 to 2021-01-15 skips all rows (0 inserted)")
    void step1_verifyIdempotency() {
        log.info("Executing ingestion run for range {} to {}...", START_DATE, END_DATE);
        FbilTBillIngestionService.FbilIngestionSummary summary = ingestionService.ingestRange(START_DATE, END_DATE);

        log.info("FBIL Ingestion Summary: parsed={}, inserted={}, skipped={}, revised={}",
            summary.totalParsed(), summary.insertedCount(), summary.skippedCount(), summary.revisedCount());

        assertEquals(11, summary.totalParsed(), "Must parse exactly 11 3-Month T-Bill observations from FBIL payload");
        assertEquals(0, summary.insertedCount(), "Idempotent run must insert 0 rows as all observations exist");
        assertEquals(11, summary.skippedCount(), "Must skip all 11 already-present observations");
        assertEquals(0, summary.revisedCount(), "Zero revisions expected");
    }

    @Test
    @Order(2)
    @DisplayName("Continuity: Verify 11 latest-revision observations and max calendar gap <= 3 days")
    void step2_verifyContinuityAndQuality() {
        List<RiskFreeObservation> recovered = riskFreeObservationRepository.findAuthoritativeObservationsAsOfCutoff(
            BENCHMARK_CODE, END_DATE, java.time.OffsetDateTime.now()
        );

        assertNotNull(recovered);

        // Filter to early January
        List<RiskFreeObservation> earlyJan = recovered.stream()
            .filter(o -> !o.getEffectiveDate().isBefore(START_DATE) && !o.getEffectiveDate().isAfter(END_DATE))
            .sorted((a, b) -> a.getEffectiveDate().compareTo(b.getEffectiveDate()))
            .toList();

        assertEquals(11, earlyJan.size(), "Must contain exactly 11 authoritative trading days between 2021-01-01 and 2021-01-15");

        log.info("Authoritative Early January FBIL Observations:");
        for (RiskFreeObservation obs : earlyJan) {
            log.info("  Date: {}, Yield: {} ({}%), Availability: {}, Quality: {}, Verification: {}",
                obs.getEffectiveDate(), obs.getQuotedYield(),
                obs.getQuotedYield().multiply(BigDecimal.valueOf(100)),
                obs.getAvailabilityTime(), obs.getQualityAssessment(), obs.getVerificationStatus());

            assertTrue(obs.getQuotedYield().compareTo(BigDecimal.ZERO) > 0, "Yield must be positive");
            assertTrue(obs.getQuotedYield().compareTo(new BigDecimal("0.10")) < 0, "Yield must be realistic (< 10%)");
            assertEquals("VALID", obs.getQualityAssessment());
            assertEquals("VERIFIED", obs.getVerificationStatus());
            assertEquals("AVAILABLE", obs.getPresenceStatus());
        }

        // Check date continuity and lookback gap <= 3 calendar days
        for (int i = 1; i < earlyJan.size(); i++) {
            LocalDate prev = earlyJan.get(i - 1).getEffectiveDate();
            LocalDate curr = earlyJan.get(i).getEffectiveDate();
            long gap = ChronoUnit.DAYS.between(prev, curr);
            assertTrue(gap <= 3, String.format("Calendar gap between %s and %s is %d days (must be <= 3 days over weekends)", prev, curr, gap));
        }

        log.info("FBIL risk-free rate recovery and gap verification complete successfully.");
    }
}
