package com.yukira.backend.service.portfolio.cas;

import com.yukira.backend.dto.portfolio.CasImportParsedRecordDto;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

public interface CasParser {

    record ParseResult(
        String parserVersion,
        LocalDate statementDate,
        List<CasImportParsedRecordDto> records,
        List<String> limitations,
        String parseStatus,
        String casProvider
    ) {
        public ParseResult(String parserVersion, LocalDate statementDate, List<CasImportParsedRecordDto> records, List<String> limitations) {
            this(parserVersion, statementDate, records, limitations, (records != null && !records.isEmpty()) ? "SUCCESS" : "NO_HOLDINGS_FOUND", "UNKNOWN");
        }

        public ParseResult(String parserVersion, LocalDate statementDate, List<CasImportParsedRecordDto> records, List<String> limitations, String parseStatus) {
            this(parserVersion, statementDate, records, limitations, parseStatus, "UNKNOWN");
        }
    }

    ParseResult parse(InputStream inputStream, String filename, String password);

    default ParseResult parse(InputStream inputStream, String filename) {
        return parse(inputStream, filename, null);
    }
}

