package com.yukira.backend.ingestion.holdings;

/**
 * Strategy contract for format and AMC-specific monthly portfolio disclosure parsers.
 *
 * Epistemic Rules:
 * 1. Decoupled Architecture: Generic ingestion service delegates parsing to registered parser implementations.
 * 2. Strict Support Validation: Each parser inspects payload signatures and descriptor metadata to declare support.
 * 3. Canonical Record Contract: Parsers transform raw disclosure payloads into standardized MonthlyHoldingsRecord streams.
 */
public interface MonthlyHoldingsParser {

    /**
     * Determines whether this parser supports the given source descriptor and raw payload bytes.
     */
    boolean supports(MonthlyHoldingsSourceDescriptor descriptor, byte[] payloadBytes);

    /**
     * Parses the raw disclosure bytes into a canonical ParseResult.
     */
    PpfasXlsxHoldingsParser.ParseResult parse(byte[] payloadBlob, MonthlyHoldingsSourceDescriptor descriptor);
}
