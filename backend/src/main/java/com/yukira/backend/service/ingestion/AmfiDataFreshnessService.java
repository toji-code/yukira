package com.yukira.backend.service.ingestion;

import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.dto.ingestion.AmfiDataFreshnessDto;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.SourceArtifactRepository;
import com.yukira.backend.repository.ValidationIssueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class AmfiDataFreshnessService {

    /**
     * Standard production freshness threshold in calendar days.
     * AMFI publishes NAV data daily on trading days; a gap <= 3 calendar days accounts for standard weekend reporting intervals.
     */
    public static final int FRESHNESS_THRESHOLD_DAYS = 3;

    public static final String GOVERNANCE_DISCLAIMER =
        "Data freshness is based strictly on verified AMFI source artifacts. Ingestion of raw NAV observations does NOT automatically recalculate analytical scores or execute quant engines.";

    private final NavObservationRepository navObservationRepository;
    private final SourceArtifactRepository sourceArtifactRepository;
    private final ValidationIssueRepository validationIssueRepository;

    public AmfiDataFreshnessService(
        NavObservationRepository navObservationRepository,
        SourceArtifactRepository sourceArtifactRepository,
        ValidationIssueRepository validationIssueRepository
    ) {
        this.navObservationRepository = navObservationRepository;
        this.sourceArtifactRepository = sourceArtifactRepository;
        this.validationIssueRepository = validationIssueRepository;
    }

    public AmfiDataFreshnessDto getFreshnessStatus() {
        return getFreshnessStatus(LocalDate.now());
    }

    public AmfiDataFreshnessDto getFreshnessStatus(LocalDate referenceDate) {
        long totalObservations = navObservationRepository.count();
        Optional<LocalDate> maxEffectiveDateOpt = navObservationRepository.findMaxEffectiveDate();
        Optional<OffsetDateTime> maxIngestionTimeOpt = navObservationRepository.findMaxIngestionTime();
        Optional<SourceArtifact> latestArtifactOpt = sourceArtifactRepository.findTopByOrderByRetrievalTimestampDesc();
        long validationIssueCount = validationIssueRepository.count();

        if (totalObservations == 0 || maxEffectiveDateOpt.isEmpty()) {
            return new AmfiDataFreshnessDto(
                "UNAVAILABLE",
                null,
                latestArtifactOpt.map(SourceArtifact::getRetrievalTimestamp).orElse(null),
                maxIngestionTimeOpt.orElse(null),
                latestArtifactOpt.map(SourceArtifact::getSha256Hash).orElse(null),
                0,
                0,
                0,
                0,
                validationIssueCount,
                null,
                FRESHNESS_THRESHOLD_DAYS,
                GOVERNANCE_DISCLAIMER
            );
        }

        LocalDate latestNavDate = maxEffectiveDateOpt.get();
        LocalDate evalDate = referenceDate != null ? referenceDate : LocalDate.now();
        long daysDiff = ChronoUnit.DAYS.between(latestNavDate, evalDate);
        if (daysDiff < 0) {
            daysDiff = 0; // Future date safety bound
        }

        String state;
        if (daysDiff <= FRESHNESS_THRESHOLD_DAYS) {
            state = "FRESH";
        } else if (daysDiff <= 7) {
            state = "PARTIAL";
        } else {
            state = "STALE";
        }

        SourceArtifact artifact = latestArtifactOpt.orElse(null);

        return new AmfiDataFreshnessDto(
            state,
            latestNavDate,
            artifact != null ? artifact.getRetrievalTimestamp() : null,
            maxIngestionTimeOpt.orElse(null),
            artifact != null ? artifact.getSha256Hash() : null,
            0,
            0,
            0,
            totalObservations,
            validationIssueCount,
            daysDiff,
            FRESHNESS_THRESHOLD_DAYS,
            GOVERNANCE_DISCLAIMER
        );
    }
}
