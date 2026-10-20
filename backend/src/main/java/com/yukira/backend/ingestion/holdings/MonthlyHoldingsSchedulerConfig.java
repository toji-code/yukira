package com.yukira.backend.ingestion.holdings;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring configuration enabling scheduled background execution for YUKIRA ingestion workflows.
 */
@Configuration
@EnableScheduling
public class MonthlyHoldingsSchedulerConfig {
}
