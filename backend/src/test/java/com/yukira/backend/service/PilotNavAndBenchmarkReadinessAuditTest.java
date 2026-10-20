package com.yukira.backend.service;

import com.yukira.backend.ingestion.amfi.HistoricalNavIngestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
class PilotNavAndBenchmarkReadinessAuditTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private HistoricalNavIngestionService historicalNavIngestionService;

    @Test
    @DisplayName("Audit NAV and Benchmark Data Readiness for Pilot Funds")
    void auditPilotFundsReadiness() {
        System.out.println("==================================================");
        System.out.println("PHASE 1 — PILOT FUNDS NAV & BENCHMARK READINESS AUDIT");
        System.out.println("==================================================");

        String[] amfiCodes = {"118955", "122639", "147481"};

        for (String amfiCode : amfiCodes) {
            System.out.println("\n--- AUDITING AMFI CODE: " + amfiCode + " ---");
            List<Map<String, Object>> options = jdbcTemplate.queryForList(
                "SELECT so.id as option_id, s.name as scheme_name, sp.plan_type, so.option_type, so.isin, so.amfi_code, so.status " +
                "FROM scheme_option so " +
                "JOIN scheme_plan sp ON so.plan_id = sp.id " +
                "JOIN scheme s ON sp.scheme_id = s.id " +
                "WHERE so.amfi_code = ?", amfiCode);

            if (options.isEmpty()) {
                System.out.println("NO SCHEME OPTION FOUND FOR AMFI CODE " + amfiCode);
                continue;
            }

            for (Map<String, Object> opt : options) {
                Long optionId = (Long) opt.get("option_id");
                System.out.println("Option ID: " + optionId);
                System.out.println("Scheme Name: " + opt.get("scheme_name"));
                System.out.println("Plan Type: " + opt.get("plan_type"));
                System.out.println("Option Type: " + opt.get("option_type"));
                System.out.println("ISIN: " + opt.get("isin"));
                System.out.println("Status: " + opt.get("status"));

                Long navCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM nav_observation WHERE scheme_option_id = ?", Long.class, optionId);
                String minDate = jdbcTemplate.queryForObject(
                    "SELECT min(effective_date) FROM nav_observation WHERE scheme_option_id = ?", String.class, optionId);
                String maxDate = jdbcTemplate.queryForObject(
                    "SELECT max(effective_date) FROM nav_observation WHERE scheme_option_id = ?", String.class, optionId);

                System.out.println("NAV Count: " + navCount);
                System.out.println("Min Effective Date: " + minDate);
                System.out.println("Max Effective Date: " + maxDate);
            }
        }

        System.out.println("\n--- BENCHMARK TRI OBSERVATIONS AUDIT ---");
        List<Map<String, Object>> benchmarks = jdbcTemplate.queryForList(
            "SELECT b.id, b.name as benchmark_name, b.code as benchmark_code, b.return_variant, " +
            "count(bo.id) as obs_count, min(bo.effective_date) as min_date, max(bo.effective_date) as max_date " +
            "FROM benchmark b " +
            "LEFT JOIN benchmark_observation bo ON bo.benchmark_id = b.id " +
            "GROUP BY b.id, b.name, b.code, b.return_variant");

        for (Map<String, Object> bm : benchmarks) {
            System.out.println("Benchmark: " + bm.get("benchmark_name") + " (" + bm.get("benchmark_code") + ")");
            System.out.println("  Variant: " + bm.get("return_variant"));
            System.out.println("  Observations: " + bm.get("obs_count"));
            System.out.println("  Span: " + bm.get("min_date") + " to " + bm.get("max_date"));
        }

        System.out.println("==================================================");
        assertNotNull(jdbcTemplate);
    }
}
