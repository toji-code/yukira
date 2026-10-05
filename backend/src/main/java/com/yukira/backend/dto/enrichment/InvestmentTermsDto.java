package com.yukira.backend.dto.enrichment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InvestmentTermsDto(
    BigDecimal minSipAmount,
    String formattedMinSip,
    List<String> sipFrequencies,
    BigDecimal minLumpsumAmount,
    String formattedMinLumpsum,
    BigDecimal minAdditionalAmount,
    String formattedMinAdditional,
    Integer lockInPeriodDays,
    String exitLoadDescription,
    LocalDate asOfDate,
    Long sourceArtifactId,
    String sourceDocumentTitle,
    String dataQuality,
    String status
) {
    public static InvestmentTermsDto missing() {
        return new InvestmentTermsDto(
            null, null, List.of(), null, null, null, null, null, null, null, null, null, "MISSING", "MISSING"
        );
    }
}
