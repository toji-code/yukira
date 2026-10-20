package com.yukira.backend.ingestion.holdings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Registry resolving format and AMC-specific MonthlyHoldingsParser strategy implementations.
 *
 * Epistemic Rules:
 * 1. Decoupled Routing: Ingestion engine queries registry to resolve parser without hardcoding formats or AMC names.
 * 2. Order Preservation: Iterates registered parser beans in priority order.
 * 3. Strict Failure: Throws explicit IllegalArgumentException if no registered parser supports the payload/descriptor.
 */
@Component
public class MonthlyHoldingsParserRegistry {

    private static final Logger log = LoggerFactory.getLogger(MonthlyHoldingsParserRegistry.class);

    private final List<MonthlyHoldingsParser> parsers;

    public MonthlyHoldingsParserRegistry(List<MonthlyHoldingsParser> parsers) {
        this.parsers = parsers;
        log.info("MonthlyHoldingsParserRegistry initialized with {} registered parsers.", parsers.size());
    }

    /**
     * Resolves the supporting MonthlyHoldingsParser strategy for a given descriptor and payload.
     */
    public MonthlyHoldingsParser resolveParser(MonthlyHoldingsSourceDescriptor descriptor, byte[] payloadBytes) {
        for (MonthlyHoldingsParser parser : parsers) {
            if (parser.supports(descriptor, payloadBytes)) {
                return parser;
            }
        }
        throw new IllegalArgumentException(String.format(
            "Unsupported holdings disclosure payload format for descriptor %s (Byte size: %d)",
            descriptor != null ? descriptor.amcCode() + "/" + descriptor.amfiCode() : "UNKNOWN",
            payloadBytes != null ? payloadBytes.length : 0
        ));
    }
}
