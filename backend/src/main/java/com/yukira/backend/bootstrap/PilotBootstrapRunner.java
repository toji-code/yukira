package com.yukira.backend.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Command-line runner for intentional pilot bootstrap execution.
 * Only triggers if explicitly requested via property or argument: --yukira.bootstrap.pilot=true.
 */
@Component
@ConditionalOnProperty(name = "yukira.bootstrap.pilot", havingValue = "true")
public class PilotBootstrapRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PilotBootstrapRunner.class);

    private final PilotBootstrapService pilotBootstrapService;

    public PilotBootstrapRunner(PilotBootstrapService pilotBootstrapService) {
        this.pilotBootstrapService = pilotBootstrapService;
    }

    @Override
    public void run(String... args) {
        log.info("Executing intentional pilot bootstrap runner (--yukira.bootstrap.pilot=true)...");
        PilotBootstrapService.BootstrapReport report = pilotBootstrapService.bootstrapPilot();
        log.info("Pilot bootstrap complete: Option ID #{}, AMFI {}, ISIN {}, Run ID #{}",
            report.schemeOptionId(), report.amfiCode(), report.isin(), report.calculationRunId());
    }
}
