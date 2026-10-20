package com.yukira.backend.service.portfolio.cas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
public class CasParserImpl implements CasParser {

    private static final Logger log = LoggerFactory.getLogger(CasParserImpl.class);
    private static final String PARSER_VERSION = "CAS_PARSER_V2_MULTI_FORMAT";

    private final StructuredCasParser structuredCasParser;
    private final PdfCasParser pdfCasParser;

    public CasParserImpl(StructuredCasParser structuredCasParser, PdfCasParser pdfCasParser) {
        this.structuredCasParser = structuredCasParser;
        this.pdfCasParser = pdfCasParser;
    }

    @Override
    public ParseResult parse(InputStream inputStream, String filename, String password) {
        if (inputStream == null) {
            return new ParseResult(PARSER_VERSION, null, List.of(), List.of("INVALID_FILE: Input stream is null."), "INVALID_FILE");
        }

        byte[] payloadBytes;
        try {
            payloadBytes = inputStream.readAllBytes();
        } catch (Exception e) {
            log.error("Failed to read CAS input stream: {}", e.getMessage());
            return new ParseResult(PARSER_VERSION, null, List.of(), List.of("PARSE_FAILED: Failed to read input file bytes: " + e.getMessage()), "PARSE_FAILED");
        }

        if (payloadBytes.length == 0) {
            return new ParseResult(PARSER_VERSION, null, List.of(), List.of("INVALID_FILE: Uploaded CAS file payload is empty."), "INVALID_FILE");
        }

        // Format detection by magic bytes or extension
        boolean isPdfMagic = payloadBytes.length >= 4 && payloadBytes[0] == 0x25 && payloadBytes[1] == 0x50 && payloadBytes[2] == 0x44 && payloadBytes[3] == 0x46;
        boolean isPdfExtension = filename != null && filename.toLowerCase().endsWith(".pdf");

        if (isPdfMagic || isPdfExtension) {
            return pdfCasParser.parsePdf(payloadBytes, password);
        } else {
            return structuredCasParser.parse(payloadBytes, filename);
        }
    }
}
