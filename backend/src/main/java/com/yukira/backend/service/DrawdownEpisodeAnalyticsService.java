package com.yukira.backend.service;

import com.yukira.backend.domain.entity.NavObservation;
import com.yukira.backend.repository.NavObservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DrawdownEpisodeAnalyticsService {

    private final NavObservationRepository navObservationRepository;

    public DrawdownEpisodeAnalyticsService(NavObservationRepository navObservationRepository) {
        this.navObservationRepository = navObservationRepository;
    }

    public record DrawdownEpisodeDto(
        int rank,
        LocalDate peakDate,
        BigDecimal peakNav,
        LocalDate troughDate,
        BigDecimal troughNav,
        double drawdownDepthPercentage,
        LocalDate recoveryDate,
        long daysToTrough,
        Long daysToRecovery,
        boolean isRecovered
    ) {}

    public record DrawdownAnalysisReportDto(
        Long schemeOptionId,
        LocalDate observationStartDate,
        LocalDate observationEndDate,
        int totalObservations,
        double maxDrawdownPercentage,
        LocalDate maxDrawdownPeakDate,
        LocalDate maxDrawdownTroughDate,
        Double currentDrawdownPercentage,
        LocalDate currentPeakDate,
        List<DrawdownEpisodeDto> topEpisodes,
        String methodologyStatus // "CANDIDATE — RSK-03/RSK-04 methodology"
    ) {}

    public DrawdownAnalysisReportDto analyzeDrawdownEpisodes(
        Long schemeOptionId,
        LocalDate startDate,
        LocalDate analysisCutoff,
        OffsetDateTime knowledgeCutoff,
        int topLimit
    ) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusYears(5);
        LocalDate end = analysisCutoff != null ? analysisCutoff : LocalDate.now();

        List<NavObservation> observations = navObservationRepository
            .findBySchemeOptionIdAndEffectiveDateBetweenOrderByEffectiveDateAsc(schemeOptionId, start, end);

        if (observations.isEmpty()) {
            return new DrawdownAnalysisReportDto(
                schemeOptionId, start, end, 0, 0.0, null, null, 0.0, null, List.of(),
                "CANDIDATE — RSK-03/RSK-04 methodology"
            );
        }

        List<DrawdownEpisodeDto> episodes = new ArrayList<>();

        BigDecimal runningPeakNav = observations.get(0).getNavValue();
        LocalDate runningPeakDate = observations.get(0).getEffectiveDate();

        BigDecimal currentTroughNav = runningPeakNav;
        LocalDate currentTroughDate = runningPeakDate;
        boolean inDrawdown = false;

        for (int i = 1; i < observations.size(); i++) {
            NavObservation obs = observations.get(i);
            BigDecimal nav = obs.getNavValue();
            LocalDate dt = obs.getEffectiveDate();

            if (nav.compareTo(runningPeakNav) >= 0) {
                // Recovered or reached new peak
                if (inDrawdown) {
                    double depth = calculateDrawdownPct(currentTroughNav, runningPeakNav);
                    long daysToTrough = ChronoUnit.DAYS.between(runningPeakDate, currentTroughDate);
                    long daysToRecovery = ChronoUnit.DAYS.between(runningPeakDate, dt);

                    episodes.add(new DrawdownEpisodeDto(
                        0, runningPeakDate, runningPeakNav, currentTroughDate, currentTroughNav,
                        depth, dt, daysToTrough, daysToRecovery, true
                    ));
                    inDrawdown = false;
                }
                runningPeakNav = nav;
                runningPeakDate = dt;
                currentTroughNav = nav;
                currentTroughDate = dt;
            } else {
                inDrawdown = true;
                if (nav.compareTo(currentTroughNav) < 0) {
                    currentTroughNav = nav;
                    currentTroughDate = dt;
                }
            }
        }

        // Check ongoing drawdown at end of series
        Double currentDd = null;
        if (inDrawdown) {
            double depth = calculateDrawdownPct(currentTroughNav, runningPeakNav);
            long daysToTrough = ChronoUnit.DAYS.between(runningPeakDate, currentTroughDate);

            episodes.add(new DrawdownEpisodeDto(
                0, runningPeakDate, runningPeakNav, currentTroughDate, currentTroughNav,
                depth, null, daysToTrough, null, false
            ));

            BigDecimal latestNav = observations.get(observations.size() - 1).getNavValue();
            currentDd = calculateDrawdownPct(latestNav, runningPeakNav);
        } else {
            currentDd = 0.0;
        }

        // Sort episodes by deepest drawdown (most negative percentage)
        episodes.sort(Comparator.comparingDouble(DrawdownEpisodeDto::drawdownDepthPercentage));

        List<DrawdownEpisodeDto> rankedEpisodes = new ArrayList<>();
        int r = 1;
        for (DrawdownEpisodeDto ep : episodes) {
            rankedEpisodes.add(new DrawdownEpisodeDto(
                r++, ep.peakDate(), ep.peakNav(), ep.troughDate(), ep.troughNav(),
                ep.drawdownDepthPercentage(), ep.recoveryDate(), ep.daysToTrough(),
                ep.daysToRecovery(), ep.isRecovered()
            ));
            if (rankedEpisodes.size() >= topLimit) break;
        }

        double maxDd = 0.0;
        LocalDate maxPeak = null;
        LocalDate maxTrough = null;

        if (!rankedEpisodes.isEmpty()) {
            var deepest = rankedEpisodes.get(0);
            maxDd = deepest.drawdownDepthPercentage();
            maxPeak = deepest.peakDate();
            maxTrough = deepest.troughDate();
        }

        return new DrawdownAnalysisReportDto(
            schemeOptionId,
            observations.get(0).getEffectiveDate(),
            observations.get(observations.size() - 1).getEffectiveDate(),
            observations.size(),
            maxDd,
            maxPeak,
            maxTrough,
            currentDd,
            runningPeakDate,
            rankedEpisodes,
            "CANDIDATE — RSK-03/RSK-04 methodology"
        );
    }

    private double calculateDrawdownPct(BigDecimal current, BigDecimal peak) {
        if (peak.compareTo(BigDecimal.ZERO) == 0) return 0.0;
        return current.subtract(peak)
            .divide(peak, 6, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .doubleValue();
    }
}

