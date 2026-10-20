package com.yukira.backend.service;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.dto.ingestion.BenchmarkConstituentIngestionResultDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class BenchmarkSeedDatabaseCommitIntegrationTest {

    @Autowired
    private PilotBootstrapService pilotBootstrapService;

    @Autowired
    private PilotBenchmarkConstituentDataService pilotBenchmarkConstituentDataService;

    @Test
    @Commit
    @DisplayName("Persist all 4 authentic historical benchmark constituent snapshots to database")
    void seedDatabaseWithAuthenticSnapshots() {
        pilotBootstrapService.bootstrapPilot();
        List<BenchmarkConstituentIngestionResultDto> results = pilotBenchmarkConstituentDataService.seedCanonicalBenchmarkConstituents();
        assertThat(results).isNotEmpty();
        assertThat(results).hasSize(4);
    }
}
