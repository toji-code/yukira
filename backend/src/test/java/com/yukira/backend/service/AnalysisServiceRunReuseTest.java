package com.yukira.backend.service;

import com.yukira.backend.domain.entity.CalculationRun;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.analysis.AnalyticalProfileResponse;
import com.yukira.backend.dto.analysis.ProfileCalculationRequest;
import com.yukira.backend.repository.CalculationRunRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@SuppressWarnings("null")
public class AnalysisServiceRunReuseTest {

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private CalculationRunRepository calculationRunRepository;

    @Test
    @DisplayName("Run Reuse Safety: Run-ID belonging to another scheme option is strictly rejected")
    void testDirectRunIdMismatchThrowsIllegalArgumentException() {
        // Option 1 (HDFC Flexi Cap) and Option 10262 (Parag Parikh Flexi Cap)
        SchemeOption opt1 = schemeOptionRepository.findById(1L).orElseThrow();
        SchemeOption opt10262 = schemeOptionRepository.findById(10262L).orElseThrow();

        // Find a run belonging to Option 10262
        List<CalculationRun> opt10262Runs = calculationRunRepository.findBySchemeOptionIdAndAsOfDateOrderByExecutionStartedAtDesc(
            opt10262.getId(), LocalDate.of(2026, 10, 1)
        );
        assertFalse(opt10262Runs.isEmpty(), "Option 10262 must have existing calculation runs");
        Long opt10262RunId = opt10262Runs.get(0).getId();

        // Attempt to execute profile for Option 1 while passing Option 10262's runId
        ProfileCalculationRequest request = new ProfileCalculationRequest(
            opt1.getId(),
            null,
            LocalDate.of(2026, 10, 1),
            OffsetDateTime.of(2026, 10, 10, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30)),
            "APPROVED_M2N",
            null,
            Map.of(),
            opt10262RunId,
            false
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            analysisService.executeProfileAnalysis(request)
        );
        assertTrue(ex.getMessage().contains("does not match requested scheme option #1"),
            "Error message must indicate mismatch: " + ex.getMessage());
    }

    @Test
    @DisplayName("Run Reuse Safety: Non-existent Run-ID is strictly rejected")
    void testDirectRunIdNonExistentThrowsIllegalArgumentException() {
        SchemeOption opt1 = schemeOptionRepository.findById(1L).orElseThrow();

        ProfileCalculationRequest request = new ProfileCalculationRequest(
            opt1.getId(),
            null,
            LocalDate.of(2026, 10, 1),
            OffsetDateTime.of(2026, 10, 10, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30)),
            "APPROVED_M2N",
            null,
            Map.of(),
            999999999L, // Non-existent
            false
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            analysisService.executeProfileAnalysis(request)
        );
        assertTrue(ex.getMessage().contains("not found"),
            "Error message must indicate not found: " + ex.getMessage());
    }

    @Test
    @DisplayName("Run Reuse Safety: Explicit valid runId is idempotently reused without new run creation")
    void testDirectRunIdCompatibleReusesExactRun() {
        SchemeOption opt1 = schemeOptionRepository.findById(1L).orElseThrow();

        List<CalculationRun> opt1Runs = calculationRunRepository.findBySchemeOptionIdAndAsOfDateOrderByExecutionStartedAtDesc(
            opt1.getId(), LocalDate.of(2026, 10, 1)
        );
        assertFalse(opt1Runs.isEmpty());
        CalculationRun existingRun = opt1Runs.get(0);

        long runCountBefore = calculationRunRepository.count();

        ProfileCalculationRequest request = new ProfileCalculationRequest(
            opt1.getId(),
            null,
            LocalDate.of(2026, 10, 1),
            OffsetDateTime.of(2026, 10, 10, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30)),
            "APPROVED_M2N",
            null,
            Map.of(),
            existingRun.getId(),
            false
        );

        AnalyticalProfileResponse response = analysisService.executeProfileAnalysis(request);

        assertNotNull(response);
        assertEquals(existingRun.getId(), response.provenance().calculationRunId());
        assertEquals(runCountBefore, calculationRunRepository.count(), "Zero new runs must be created on runId reuse");
    }
}
