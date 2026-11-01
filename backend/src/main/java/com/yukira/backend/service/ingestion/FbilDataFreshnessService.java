package com.yukira.backend.service.ingestion;

import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.dto.ingestion.FbilDataFreshnessDto;
import com.yukira.backend.repository.RiskFreeObservationRepository;
import com.yukira.backend.repository.SourceArtifactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Service providing authoritative data freshness assessment for FBIL risk-free rates (Phase A2).
 *
 * Epistemic Rules:
 * 1. Rates are persisted with authentic cryptographic SHA-256 source artifact provenance.
 * 2. FBIL quoted yields are published under ACT/365 daycount convention.
 * 3. Freshness indicates temporal availability of published rates; it does NOT imply synthetic zero-filling.
 */
@Service
@Transactional(readOnly = true)
public class FbilDataFreshnessService {

    public static final String FBIL_91D_TBILL_CODE = "FBIL_91D_TBILL";
    public static final String FBIL_91D_TBILL_NAME = "FBIL 91-Day Treasury Bill Curve";
    public static final String FBIL_PROVIDER = "Financial Benchmarks India Pvt Ltd";
    public static final String DAYCOUNT_ACT_365 = "ACT_365";
    public static final int FRESHNESS_THRESHOLD_DAYS = 3;

    public static final String GOVERNANCE_DISCLAIMER =
        "Risk-free rate observations are ingested as authentic quoted yields from FBIL under the ACT/365 daycount convention. " +
        "Zero synthetic rates or forward fills are generated. Rate ingestion does NOT calculate fund alpha or Sharpe ratios.";

    private final RiskFreeObservationRepository riskFreeObservationRepository;
    private final SourceArtifactRepository sourceArtifactRepository;

    public FbilDataFreshnessService(
        RiskFreeObservationRepository riskFreeObservationRepository,
        SourceArtifactRepository sourceArtifactRepository
    ) {
        this.riskFreeObservationRepository = riskFreeObservationRepository;
        this.sourceArtifactRepository = sourceArtifactRepository;
    }

    public FbilDataFreshnessDto getFreshnessStatus() {
        return getFreshnessStatus(LocalDate.now());
    }

    public FbilDataFreshnessDto getFreshnessStatus(LocalDate referenceDate) {
        long totalObservations = riskFreeObservationRepository.countByBenchmarkCode(FBIL_91D_TBILL_CODE);
        Optional<LocalDate> minDateOpt = riskFreeObservationRepository.findMinEffectiveDateByBenchmarkCode(FBIL_91D_TBILL_CODE);
        Optional<LocalDate> maxDateOpt = riskFreeObservationRepository.findMaxEffectiveDateByBenchmarkCode(FBIL_91D_TBILL_CODE);
        Optional<OffsetDateTime> maxAvailabilityOpt = riskFreeObservationRepository.findMaxAvailabilityTimeByBenchmarkCode(FBIL_91D_TBILL_CODE);
        Optional<SourceArtifact> latestArtifactOpt = sourceArtifactRepository.findTopByOrderByRetrievalTimestampDesc();

        if (totalObservations == 0 || maxDateOpt.isEmpty()) {
            return new FbilDataFreshnessDto(
                FBIL_91D_TBILL_CODE,
                FBIL_91D_TBILL_NAME,
                FBIL_PROVIDER,
                DAYCOUNT_ACT_365,
                "UNAVAILABLE",
                null,
                null,
                latestArtifactOpt.map(SourceArtifact::getRetrievalTimestamp).orElse(null),
                null,
                latestArtifactOpt.map(SourceArtifact::getSha256Hash).orElse(null),
                0,
                null,
                FRESHNESS_THRESHOLD_DAYS,
                GOVERNANCE_DISCLAIMER,
                "NO_OBSERVATIONS",
                "No authoritative FBIL 91D T-Bill risk-free observations currently available in database."
            );
        }

        LocalDate latestDate = maxDateOpt.get();
        LocalDate evalDate = referenceDate != null ? referenceDate : LocalDate.now();
        long daysDiff = ChronoUnit.DAYS.between(latestDate, evalDate);
        if (daysDiff < 0) {
            daysDiff = 0;
        }

        String state;
        String explanation;
        if (daysDiff <= FRESHNESS_THRESHOLD_DAYS) {
            state = "FRESH";
            explanation = String.format("Latest FBIL 91D T-Bill observation is from %s (%d days lag). Quoted under ACT/365 convention.", latestDate, daysDiff);
        } else if (daysDiff <= 7) {
            state = "PARTIAL";
            explanation = String.format("FBIL risk-free feed is slightly delayed: latest observation %s (%d days lag).", latestDate, daysDiff);
        } else {
            state = "STALE";
            explanation = String.format("FBIL risk-free feed is stale: latest observation %s exceeds freshness threshold of %d days (%d days lag).",
                latestDate, FRESHNESS_THRESHOLD_DAYS, daysDiff);
        }

        SourceArtifact artifact = latestArtifactOpt.orElse(null);

        return new FbilDataFreshnessDto(
            FBIL_91D_TBILL_CODE,
            FBIL_91D_TBILL_NAME,
            FBIL_PROVIDER,
            DAYCOUNT_ACT_365,
            state,
            minDateOpt.orElse(null),
            latestDate,
            artifact != null ? artifact.getRetrievalTimestamp() : null,
            maxAvailabilityOpt.orElse(null),
            artifact != null ? artifact.getSha256Hash() : null,
            totalObservations,
            daysDiff,
            FRESHNESS_THRESHOLD_DAYS,
            GOVERNANCE_DISCLAIMER,
            "OBSERVATIONS_AVAILABLE",
            explanation
        );
    }
}
