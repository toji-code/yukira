package com.yukira.backend.bootstrap;

import com.yukira.backend.ingestion.amfi.AmfiUniverseIngestionService;
import com.yukira.backend.ingestion.amfi.UniverseIngestionSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Command-line runner for operator-triggered AMFI universe scheme master ingestion.
 * Only triggers if explicitly requested via property or CLI argument: --yukira.ingestion.universe.enabled=true.
 * Adheres strictly to the invariant: zero live external ingestion automatically during application startup.
 */
@Component
@ConditionalOnProperty(name = "yukira.ingestion.universe.enabled", havingValue = "true")
public class AmfiUniverseIngestionRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AmfiUniverseIngestionRunner.class);

    private final AmfiUniverseIngestionService universeIngestionService;

    public AmfiUniverseIngestionRunner(AmfiUniverseIngestionService universeIngestionService) {
        this.universeIngestionService = universeIngestionService;
    }

    @Override
    public void run(String... args) {
        log.info("Executing intentional AMFI universe ingestion runner (--yukira.ingestion.universe.enabled=true)...");
        try {
            UniverseIngestionSummary summary = universeIngestionService.fetchAndIngestLiveUniverse();
            log.info("AMFI universe ingestion complete: {} rows processed, {} AMCs, {} Schemes, {} Plans, {} Options registered, {} skipped duplicates, {} validation issues.",
                summary.totalRowsParsed(), summary.amcsRegistered(), summary.schemesRegistered(),
                summary.plansRegistered(), summary.optionsRegistered(), summary.duplicatesSkipped(),
                summary.validationIssuesCreated());
        } catch (Exception e) {
            log.error("Fatal error during command-line AMFI universe ingestion: {}", e.getMessage(), e);
            throw e;
        }
    }
}
