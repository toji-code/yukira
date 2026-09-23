package com.yukira.backend.service;

import com.yukira.backend.domain.entity.NavObservation;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.analysis.AnalyticalObservationSeriesDto;
import com.yukira.backend.dto.analysis.AnalyticalObservationSeriesDto.*;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Historical Analytical Data Service:
 *
 * Provides point-in-time (PIT) historical observation series for quantitative analysis,
 * strictly governed by the bitemporal PIT contract:
 *   effective_date <= analysis_cutoff
 *   AND availability_time <= knowledge_cutoff
 *
 * Rejects any query lacking an explicit knowledge cutoff to eliminate look-ahead bias.
 */
@Service
public class HistoricalAnalyticalDataService {

    private final SchemeOptionRepository schemeOptionRepository;
    private final NavObservationRepository navObservationRepository;
    private final PitObservationResolutionService pitResolutionService;
    private final TradingDateContinuityService continuityService;

    public HistoricalAnalyticalDataService(
        SchemeOptionRepository schemeOptionRepository,
        NavObservationRepository navObservationRepository,
        PitObservationResolutionService pitResolutionService,
        TradingDateContinuityService continuityService
    ) {
        this.schemeOptionRepository = schemeOptionRepository;
        this.navObservationRepository = navObservationRepository;
        this.pitResolutionService = pitResolutionService;
        this.continuityService = continuityService;
    }

    /**
     * Retrieves an immutable, point-in-time analytical observation series for a scheme option.
     *
     * @param schemeOptionId Database ID of the SchemeOption
     * @param startDate Earliest effective date for the series
     * @param analysisCutoffDate Latest effective date allowed (analysis horizon)
     * @param knowledgeCutoffTime Information availability cutoff (strictly enforces zero look-ahead bias)
     * @return Fully populated AnalyticalObservationSeriesDto
     */
    @Transactional
    public AnalyticalObservationSeriesDto getHistoricalObservationSeries(
        Long schemeOptionId,
        LocalDate startDate,
        LocalDate analysisCutoffDate,
        OffsetDateTime knowledgeCutoffTime
    ) {
        if (schemeOptionId == null) {
            throw new IllegalArgumentException("schemeOptionId must not be null");
        }
        if (startDate == null || analysisCutoffDate == null) {
            throw new IllegalArgumentException("startDate and analysisCutoffDate must not be null");
        }
        if (analysisCutoffDate.isBefore(startDate)) {
            throw new IllegalArgumentException("analysisCutoffDate cannot be before startDate");
        }
        if (knowledgeCutoffTime == null) {
            throw new IllegalArgumentException("Strict PIT Invariant: knowledgeCutoffTime must be explicitly specified");
        }
        if (analysisCutoffDate.isAfter(knowledgeCutoffTime.toLocalDate())) {
            throw new IllegalArgumentException(String.format(
                "Strict PIT Invariant: analysisCutoffDate (%s) cannot exceed knowledgeCutoff date (%s)",
                analysisCutoffDate, knowledgeCutoffTime.toLocalDate()
            ));
        }

        SchemeOption option = schemeOptionRepository.findById(schemeOptionId)
            .orElseThrow(() -> new NoSuchElementException("SchemeOption not found for ID: " + schemeOptionId));

        // Query eligible observations from database where effective_date <= analysisCutoffDate AND availability_time <= knowledgeCutoffTime
        List<NavObservation> candidates = navObservationRepository.findAuthoritativeObservationsAsOfCutoff(
            schemeOptionId, analysisCutoffDate, knowledgeCutoffTime
        );

        // Filter to [startDate, analysisCutoffDate] and resolve authoritative observation per effective date
        Map<LocalDate, List<NavObservation>> groupedByDate = new TreeMap<>();
        for (NavObservation obs : candidates) {
            if (!obs.getEffectiveDate().isBefore(startDate) && !obs.getEffectiveDate().isAfter(analysisCutoffDate)) {
                groupedByDate.computeIfAbsent(obs.getEffectiveDate(), k -> new ArrayList<>()).add(obs);
            }
        }

        List<AnalyticalObservationItem> resolvedItems = new ArrayList<>();
        Set<Long> sourceArtifactIds = new HashSet<>();

        for (Map.Entry<LocalDate, List<NavObservation>> entry : groupedByDate.entrySet()) {
            LocalDate date = entry.getKey();
            var pitResult = pitResolutionService.resolveAuthoritativeObservation(schemeOptionId, date, knowledgeCutoffTime);

            if (pitResult.authoritativeObservation().isPresent()) {
                NavObservation obs = pitResult.authoritativeObservation().get();
                Long artifactId = obs.getSourceArtifact() != null ? obs.getSourceArtifact().getId() : null;
                String artifactHash = obs.getSourceArtifact() != null ? obs.getSourceArtifact().getSha256Hash() : null;

                if (artifactId != null) {
                    sourceArtifactIds.add(artifactId);
                }

                resolvedItems.add(new AnalyticalObservationItem(
                    obs.getId(),
                    obs.getEffectiveDate(),
                    obs.getNavValue(),
                    obs.getRevisionSeq(),
                    Boolean.TRUE.equals(obs.getLatestRevision()),
                    obs.getAvailabilityTime(),
                    artifactId,
                    artifactHash,
                    obs.getQualityAssessment(),
                    obs.getVerificationStatus(),
                    obs.getRevisionStatus(),
                    obs.getTemporalStatus(),
                    obs.getPresenceStatus(),
                    "NONE"
                ));
            }
        }

        // Build summary
        int totalObs = resolvedItems.size();
        LocalDate firstDate = totalObs > 0 ? resolvedItems.get(0).effectiveDate() : null;
        LocalDate latestDate = totalObs > 0 ? resolvedItems.get(totalObs - 1).effectiveDate() : null;
        BigDecimal firstNav = totalObs > 0 ? resolvedItems.get(0).navValue() : null;
        BigDecimal latestNav = totalObs > 0 ? resolvedItems.get(totalObs - 1).navValue() : null;

        // Check continuity gaps
        var continuityReport = continuityService.analyzeContinuity(
            startDate, analysisCutoffDate, candidates
        );

        SchemeOptionIdentity identity = new SchemeOptionIdentity(
            option.getId(),
            option.getAmfiCode(),
            option.getIsin(),
            option.getPlan().getScheme().getName(),
            option.getPlan().getPlanType(),
            option.getOptionType()
        );

        PointInTimeBounds bounds = new PointInTimeBounds(
            startDate,
            analysisCutoffDate,
            knowledgeCutoffTime
        );

        SeriesSummary summary = new SeriesSummary(
            totalObs,
            firstDate,
            latestDate,
            firstNav,
            latestNav,
            continuityReport.hasContinuityBreach(),
            sourceArtifactIds.size()
        );

        return new AnalyticalObservationSeriesDto(identity, bounds, summary, resolvedItems);
    }
}
