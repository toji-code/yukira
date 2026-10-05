package com.yukira.backend;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.analysis.AnalyticalProfileResponse;
import com.yukira.backend.dto.analysis.ProfileCalculationRequest;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.AnalysisService;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@SuppressWarnings("null")
public class AnalyticalProfileEndToEndIntegrationTest {

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private BenchmarkRepository benchmarkRepository;

    @Autowired
    private CalculationRunRepository calculationRunRepository;

    @Autowired
    private MetricResultRepository metricResultRepository;

    @Test
    @DisplayName("Phase 2R: Execute unified 3-Year Institutional Risk-Return Profile against canonical pilot")
    void testCanonicalPilot3YProfileEndToEnd() {
        // 1. Identify canonical pilot: HDFC Flexi Cap Fund Direct Growth (AMFI 118955, ISIN INF179K01UT0)
        SchemeOption pilot = schemeOptionRepository.findByAmfiCode("118955")
            .orElseThrow(() -> new IllegalStateException("Canonical pilot option 118955 not found"));

        assertEquals("INF179K01UT0", pilot.getIsin());
        assertEquals("GROWTH", pilot.getOptionType());

        Benchmark benchmark = benchmarkRepository.findByCode("NIFTY_500_TRI")
            .orElseGet(() -> benchmarkRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Benchmark not found")));

        // 2. Canonical Parameters:
        // Analysis date: 2024-01-15
        // Knowledge cutoff: 2024-01-31T23:59:59+05:30
        LocalDate asOfDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        ProfileCalculationRequest request = new ProfileCalculationRequest(
            pilot.getId(),
            benchmark.getId(),
            asOfDate,
            knowledgeCutoff,
            "APPROVED_M2N",
            null, // defaults to CANONICAL_3Y_PROFILE_METRIC_CODES
            Map.of("periods_per_year", 252.0, "min_downside_observations", 100)
        );

        long runCountBefore = calculationRunRepository.count();

        // 3. Execute Profile Calculation
        AnalyticalProfileResponse response = analysisService.executeProfileAnalysis(request);

        assertNotNull(response);
        assertEquals(runCountBefore + 1, calculationRunRepository.count());

        // 4. Verify Structure and Section Counts
        assertNotNull(response.context());
        assertEquals("118955", response.context().amfiCode());
        assertEquals("INF179K01UT0", response.context().isin());
        assertEquals(asOfDate, response.context().asOfDate());

        assertNotNull(response.provenance());
        assertEquals("SUCCESS", response.provenance().runStatus());
        assertNotNull(response.provenance().inputSnapshotSha256());
        assertNotNull(response.provenance().calculationRunId());


        // Check 4 sections: 3 + 7 + 3 + 7 = 20 metrics
        List<AnalyticalProfileResponse.ProfileMetricItem> retMetrics = response.returnMetrics();
        List<AnalyticalProfileResponse.ProfileMetricItem> rskMetrics = response.riskMetrics();
        List<AnalyticalProfileResponse.ProfileMetricItem> ratMetrics = response.riskAdjustedMetrics();
        List<AnalyticalProfileResponse.ProfileMetricItem> relMetrics = response.marketSensitivityMetrics();

        assertEquals(3, retMetrics.size(), "Return section must have RET-02, RET-03, RET-07");
        assertEquals(7, rskMetrics.size(), "Risk section must have RSK-01 through RSK-07");
        assertEquals(3, ratMetrics.size(), "Risk-adjusted section must have RAT-01, RAT-03, RAT-04");
        assertEquals(7, relMetrics.size(), "Market sensitivity section must have MKT-01..05, REL-01, REL-02");

        int totalMetrics = retMetrics.size() + rskMetrics.size() + ratMetrics.size() + relMetrics.size();
        assertEquals(20, totalMetrics, "Total unified profile must contain exactly 20 metrics");

        // Verify all 16 metrics are CALCULATED
        for (AnalyticalProfileResponse.ProfileMetricItem item : retMetrics) {
            assertEquals("CALCULATED", item.calculationStatus(), "Metric " + item.metricCode() + " must be CALCULATED");
            assertNotNull(item.numericValue(), "Metric " + item.metricCode() + " must have numeric value");
        }
        for (AnalyticalProfileResponse.ProfileMetricItem item : rskMetrics) {
            assertEquals("CALCULATED", item.calculationStatus(), "Metric " + item.metricCode() + " must be CALCULATED");
            assertNotNull(item.numericValue(), "Metric " + item.metricCode() + " must have numeric value");
        }
        for (AnalyticalProfileResponse.ProfileMetricItem item : ratMetrics) {
            assertEquals("CALCULATED", item.calculationStatus(), "Metric " + item.metricCode() + " must be CALCULATED");
            assertNotNull(item.numericValue(), "Metric " + item.metricCode() + " must have numeric value");
        }
        for (AnalyticalProfileResponse.ProfileMetricItem item : relMetrics) {
            assertEquals("CALCULATED", item.calculationStatus(), "Metric " + item.metricCode() + " must be CALCULATED");
            assertNotNull(item.numericValue(), "Metric " + item.metricCode() + " must have numeric value");
        }

        // 5. Canonical Value Verifications (Section 7 of prompt)
        // RSK-01 Volatility: fixed PIT resolver includes valid same-availability revised observations.
        AnalyticalProfileResponse.ProfileMetricItem rsk01 = findMetric(rskMetrics, "RSK-01");
        assertEquals(0.150571584544, rsk01.numericValue().doubleValue(), 0.0001, "RSK-01 canonical volatility mismatch");

        // RSK-02 Downside Semideviation: ~0.1013013296
        AnalyticalProfileResponse.ProfileMetricItem rsk02 = findMetric(rskMetrics, "RSK-02");
        assertEquals(0.1013013296, rsk02.numericValue().doubleValue(), 0.005, "RSK-02 canonical semideviation mismatch");

        // RAT-01 Sharpe: ~1.3669125480
        AnalyticalProfileResponse.ProfileMetricItem rat01 = findMetric(ratMetrics, "RAT-01");
        assertEquals(1.3669125480, rat01.numericValue().doubleValue(), 0.05, "RAT-01 canonical Sharpe mismatch");

        // RAT-03 Treynor: ~0.2145410507
        AnalyticalProfileResponse.ProfileMetricItem rat03 = findMetric(ratMetrics, "RAT-03");
        assertEquals(0.2145410507, rat03.numericValue().doubleValue(), 0.05, "RAT-03 canonical Treynor mismatch");

        // RAT-04 Information Ratio
        AnalyticalProfileResponse.ProfileMetricItem rat04 = findMetric(ratMetrics, "RAT-04");
        assertEquals("CANDIDATE", rat04.governanceStatus());

        // MKT-01 Beta: ~0.9590602545
        AnalyticalProfileResponse.ProfileMetricItem mkt01 = findMetric(relMetrics, "MKT-01");
        assertEquals(0.9590602545, mkt01.numericValue().doubleValue(), 0.05, "MKT-01 canonical Beta mismatch");

        // MKT-02 Downside Beta: ~0.9678148690
        AnalyticalProfileResponse.ProfileMetricItem mkt02 = findMetric(relMetrics, "MKT-02");
        assertEquals(0.9678148690, mkt02.numericValue().doubleValue(), 0.05, "MKT-02 canonical Downside Beta mismatch");

        // REL-01 Tracking Error and REL-02 Jensen's Alpha remain distinct canonical registry identities
        AnalyticalProfileResponse.ProfileMetricItem rel01 = findMetric(relMetrics, "REL-01");
        AnalyticalProfileResponse.ProfileMetricItem rel02 = findMetric(relMetrics, "REL-02");
        assertEquals("CANDIDATE", rel01.governanceStatus());
        assertEquals("CANDIDATE", rel02.governanceStatus());

        // 6. Governance Status Verifications
        //
        // Governance invariant (AGENTS.md 11 / 12): IMPLEMENTED != VALIDATED != APPROVED.
        // Every methodology_version row in the database is lifecycle CANDIDATE /
        // approval CANDIDATE / validation UNVALIDATED, with no approved_by and no
        // approval_record. No metric may therefore be presented as APPROVED.
        //
        // The profile previously hardcoded "APPROVED" for RET-03, RAT-01 and RAT-02 as a
        // Java display string that had no governance backing, overstating the status of
        // three metrics whose persisted methodology versions are all CANDIDATE.
        // These assertions now pin the honest status.

        // Operational baseline: RET-02 (candidate primitive, per phase2h section 4.2)
        AnalyticalProfileResponse.ProfileMetricItem ret02 = findMetric(retMetrics, "RET-02");
        assertTrue(ret02.governanceStatus().contains("OPERATIONAL")
                || "CANDIDATE".equals(ret02.governanceStatus()),
            "RET-02 must not be presented as APPROVED");

        // Previously mislabelled APPROVED; persisted methodology versions are CANDIDATE.
        AnalyticalProfileResponse.ProfileMetricItem ret03 = findMetric(retMetrics, "RET-03");
        assertEquals("CANDIDATE", ret03.governanceStatus());

        assertEquals("CANDIDATE", rat01.governanceStatus());
        assertEquals("CANDIDATE", rat03.governanceStatus());

        // Candidates: RSK-01..07, RET-07, RAT-04, MKT-01..05, REL-01, REL-02
        assertEquals("CANDIDATE", rsk01.governanceStatus());
        assertEquals("CANDIDATE", rsk02.governanceStatus());
        assertEquals("CANDIDATE", mkt01.governanceStatus());
        assertEquals("CANDIDATE", mkt02.governanceStatus());

        AnalyticalProfileResponse.ProfileMetricItem ret07 = findMetric(retMetrics, "RET-07");
        assertEquals("CANDIDATE", ret07.governanceStatus());

        assertEquals("CANDIDATE", rel01.governanceStatus());
        assertEquals("CANDIDATE", rel02.governanceStatus());

        // 7. Database Persistence & Run-Level Verification
        Long runId = response.provenance().calculationRunId();
        CalculationRun persistedRun = calculationRunRepository.findById(runId).orElseThrow();
        assertEquals("SUCCESS", persistedRun.getRunStatus());
        assertEquals(response.provenance().inputSnapshotSha256(), persistedRun.getInputSnapshotSha256());


        List<MetricResult> persistedMetrics = metricResultRepository.findByCalculationRunId(runId);
        assertEquals(20, persistedMetrics.size(), "Database must contain exactly 20 MetricResult records for this run");

        // 8. Retrieval via getAnalysisByRunId returning unified AnalyticalProfileResponse
        Optional<Object> retrieved = analysisService.getAnalysisByRunId(runId);
        assertTrue(retrieved.isPresent());
        assertInstanceOf(AnalyticalProfileResponse.class, retrieved.get());
        AnalyticalProfileResponse profile = (AnalyticalProfileResponse) retrieved.get();
        assertEquals(20, profile.returnMetrics().size() + profile.riskMetrics().size() + profile.riskAdjustedMetrics().size() + profile.marketSensitivityMetrics().size());
        assertEquals(response.provenance().inputSnapshotSha256(), profile.provenance().inputSnapshotSha256());
    }

    private AnalyticalProfileResponse.ProfileMetricItem findMetric(List<AnalyticalProfileResponse.ProfileMetricItem> items, String code) {
        return items.stream()
            .filter(i -> code.equals(i.metricCode()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Metric " + code + " not found in items"));
    }
}
