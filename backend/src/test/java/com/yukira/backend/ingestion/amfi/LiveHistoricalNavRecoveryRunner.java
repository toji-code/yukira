package com.yukira.backend.ingestion.amfi;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.*;
import com.yukira.backend.universe.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.yukira.backend.BackendApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("YUKIRA V1 Task 1: Historical NAV Recovery Across Reference Universe")
public class LiveHistoricalNavRecoveryRunner {

    private static final Logger log = LoggerFactory.getLogger(LiveHistoricalNavRecoveryRunner.class);

    private static final LocalDate START_DATE = LocalDate.of(2023, 10, 1);
    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 10, 1);
    private static final OffsetDateTime KNOWLEDGE_CUTOFF = OffsetDateTime.of(2026, 10, 1, 23, 59, 59, 999000000, ZoneOffset.ofHoursMinutes(5, 30));

    @Autowired
    private ReferenceUniverseNavRecoveryService referenceUniverseNavRecoveryService;

    @Autowired
    private ReferenceUniverseService referenceUniverseService;

    @Autowired
    private HistoricalNavIngestionService historicalNavIngestionService;

    @Autowired
    private HistorySufficiencyService historySufficiencyService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private NavObservationRepository navObservationRepository;

    @Autowired
    private SourceArtifactRepository sourceArtifactRepository;

    @Test
    @Order(1)
    @DisplayName("Step 1: Audit baseline observation counts and classification breakdown before recovery")
    void step1_auditBaseline() {
        log.info("=== STEP 1: AUDITING BASELINE COVERAGE BEFORE RECOVERY ===");
        ReferenceUniverseRecoveryInventoryDto inventory = referenceUniverseNavRecoveryService.getInventory(START_DATE, AS_OF_DATE);

        log.info("Baseline Reference Universe Total: {}", inventory.totalReferenceOptions());
        log.info("  SUFFICIENT_3Y (>=700): {}", inventory.sufficientCount());
        log.info("  PARTIAL_PROGRESS (<700): {}", inventory.partialCount());
        log.info("    STORED_ARTIFACT_READY (556): {}", inventory.storedArtifactReadyCount());
        log.info("    INCEPTION_BOUNDED: {}", inventory.inceptionBoundedCount());
        log.info("    HISTORICAL_GAP_3Y: {}", inventory.historicalGap3YCount());
        log.info("    OLDER_DISCONTINUITY: {}", inventory.olderDiscontinuityCount());
        log.info("Category Breakdown: {}", inventory.categoryBreakdown());

        assertEquals(193, inventory.totalReferenceOptions(), "Total reference universe must be exactly 193 Direct-Growth active options");
        assertEquals(23, inventory.sufficientCount(), "Baseline sufficient options must be 23");
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: Recover 2026 observations from stored authentic universe artifacts #5378-#5381")
    void step2_recoverStoredArtifacts() {
        log.info("=== STEP 2: RECOVERING FROM STORED ARTIFACTS #5378-#5381 ===");

        // Target all candidates currently below sufficiency threshold
        ReferenceUniverseRecoveryInventoryDto inventory = referenceUniverseNavRecoveryService.getInventory(START_DATE, AS_OF_DATE);
        List<Long> targetCandidateIds = inventory.candidates().stream()
            .filter(c -> c.count3Y() < 700)
            .map(NavRecoveryCandidateDto::schemeOptionId)
            .toList();

        log.info("Identified {} candidates below threshold for stored-artifact 2026 ingestion", targetCandidateIds.size());

        StoredArtifactRecoveryRequest req = new StoredArtifactRecoveryRequest(
            targetCandidateIds.size(),
            null,
            targetCandidateIds,
            false,
            AS_OF_DATE,
            "task1-stored-recovery"
        );

        NavRecoveryBatchExecutionResultDto result = referenceUniverseNavRecoveryService.executeStoredArtifactRecovery(req);

        log.info("Stored-artifact recovery completed: runId={}, evaluated={}, newlySufficient={}, inserted={}, skipped={}, durationMs={}",
            result.runId(), result.candidatesSelected(), result.newlySufficientCount(),
            result.totalObservationsInserted(), result.totalDuplicatesSkipped(), result.durationMs());

        assertTrue(result.totalObservationsInserted() > 0, "Must have inserted observations from stored artifacts #5378-#5381");
        assertTrue(result.newlySufficientCount() > 0, "At least the 66 stored-artifact-ready options must become sufficient");
    }

    @Test
    @Order(3)
    @DisplayName("Step 3: Recover missing historical intervals for gap and discontinuity schemes")
    void step3_recoverTargetedIntervals() {
        log.info("=== STEP 3: RECOVERING TARGETED HISTORICAL INTERVALS ===");

        TargetedIntervalRecoveryRequest req = new TargetedIntervalRecoveryRequest(
            100,
            null,
            null,
            false,
            AS_OF_DATE,
            10,
            "task1-interval-recovery"
        );

        NavRecoveryBatchExecutionResultDto result = referenceUniverseNavRecoveryService.executeTargetedIntervalRecovery(req);

        log.info("Targeted interval recovery completed: runId={}, evaluated={}, newlySufficient={}, inserted={}, skipped={}, durationMs={}",
            result.runId(), result.candidatesSelected(), result.newlySufficientCount(),
            result.totalObservationsInserted(), result.totalDuplicatesSkipped(), result.durationMs());

        for (var outcome : result.fundOutcomes()) {
            log.info("Fund Outcome [ID={}, AMFI={}]: name='{}', cat={}, status='{}', count: {} -> {}, inserted={}, msg='{}'",
                outcome.schemeOptionId(), outcome.amfiCode(), outcome.schemeName(), outcome.category(),
                outcome.outcomeStatus(), outcome.previous3YObservations(), outcome.current3YObservations(),
                outcome.observationsInserted(), outcome.message());
        }
    }

    @Test
    @Order(4)
    @DisplayName("Step 4: Audit and verify final post-recovery coverage across reference universe")
    void step4_auditPostRecovery() {
        log.info("=== STEP 4: FINAL POST-RECOVERY AUDIT ===");
        ReferenceUniverseRecoveryInventoryDto inventory = referenceUniverseNavRecoveryService.getInventory(START_DATE, AS_OF_DATE);

        log.info("Post-Recovery Reference Universe Total: {}", inventory.totalReferenceOptions());
        log.info("  SUFFICIENT_3Y (>=700): {}", inventory.sufficientCount());
        log.info("  PARTIAL_PROGRESS (<700): {}", inventory.partialCount());
        log.info("    STORED_ARTIFACT_READY (556): {}", inventory.storedArtifactReadyCount());
        log.info("    INCEPTION_BOUNDED: {}", inventory.inceptionBoundedCount());
        log.info("    HISTORICAL_GAP_3Y: {}", inventory.historicalGap3YCount());
        log.info("    OLDER_DISCONTINUITY: {}", inventory.olderDiscontinuityCount());
        log.info("Category Breakdown: {}", inventory.categoryBreakdown());

        for (var candidate : inventory.candidates()) {
            if (candidate.classification() == HistoricalNavRecoveryCategory.INCEPTION_BOUNDED) {
                log.info("INCEPTION_BOUNDED Scheme [ID={}, AMFI={}]: name='{}', inception={}, earliest={}, 3Y={}, all={}",
                    candidate.schemeOptionId(), candidate.amfiCode(), candidate.schemeName(),
                    candidate.inceptionDate(), candidate.earliestDate(), candidate.count3Y(), candidate.countAll());
            } else if (candidate.count3Y() < 700) {
                log.info("REMAINING_PARTIAL Scheme [ID={}, AMFI={}]: name='{}', class={}, 3Y={}, all={}, intervals={}",
                    candidate.schemeOptionId(), candidate.amfiCode(), candidate.schemeName(),
                    candidate.classification(), candidate.count3Y(), candidate.countAll(), candidate.missingIntervals().size());
            }
        }

        assertTrue(inventory.sufficientCount() > 23, "Sufficient schemes must have increased significantly from baseline 23");
    }
}
