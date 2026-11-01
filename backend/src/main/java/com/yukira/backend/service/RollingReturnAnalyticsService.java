package com.yukira.backend.service;

import com.yukira.backend.domain.entity.NavObservation;
import com.yukira.backend.repository.NavObservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class RollingReturnAnalyticsService {

    private final NavObservationRepository navObservationRepository;

    public RollingReturnAnalyticsService(NavObservationRepository navObservationRepository) {
        this.navObservationRepository = navObservationRepository;
    }

    public record RollingPointDto(
        LocalDate startDate,
        LocalDate endDate,
        double annualizedReturnPercentage
    ) {}

    public record RollingReturnSummaryDto(
        String horizon, // "1Y", "3Y", "5Y"
        int windowTradingDays,
        int totalRollingPeriods,
        double minReturnPercentage,
        double p25ReturnPercentage,
        double medianReturnPercentage,
        double p75ReturnPercentage,
        double maxReturnPercentage,
        double meanReturnPercentage,
        double standardDeviationPercentage,
        double positivePeriodsPercentage,
        List<RollingPointDto> sampledPoints,
        String methodologyStatus // "CANDIDATE — RET-03 / Rolling Window Return"
    ) {}

    public RollingReturnSummaryDto calculateRollingReturns(
        Long schemeOptionId,
        String horizon,
        LocalDate startDate,
        LocalDate endDate,
        OffsetDateTime knowledgeCutoff
    ) {
        int windowDays = switch (horizon.toUpperCase()) {
            case "1Y" -> 252;
            case "3Y" -> 756;
            case "5Y" -> 1260;
            default -> 252;
        };

        LocalDate start = startDate != null ? startDate : LocalDate.now().minusYears(7);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        List<NavObservation> observations = navObservationRepository
            .findBySchemeOptionIdAndEffectiveDateBetweenOrderByEffectiveDateAsc(schemeOptionId, start, end);

        if (observations.size() <= windowDays) {
            return new RollingReturnSummaryDto(
                horizon, windowDays, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, List.of(),
                "CANDIDATE — RET-03 / Rolling Window Return (Insufficient History: found " + observations.size() + ", required > " + windowDays + ")"
            );
        }

        List<Double> returns = new ArrayList<>();
        List<RollingPointDto> sampledPoints = new ArrayList<>();

        int step = Math.max(1, (observations.size() - windowDays) / 50); // Sample up to 50 points for chart representation

        double years = (double) windowDays / 252.0;

        for (int i = 0; i + windowDays < observations.size(); i++) {
            NavObservation startObs = observations.get(i);
            NavObservation endObs = observations.get(i + windowDays);

            double startNav = startObs.getNavValue().doubleValue();
            double endNav = endObs.getNavValue().doubleValue();

            if (startNav > 0) {
                double totalReturn = endNav / startNav;
                double annReturn = (Math.pow(totalReturn, 1.0 / years) - 1.0) * 100.0;
                returns.add(annReturn);

                if (i % step == 0) {
                    sampledPoints.add(new RollingPointDto(startObs.getEffectiveDate(), endObs.getEffectiveDate(), annReturn));
                }
            }
        }

        if (returns.isEmpty()) {
            return new RollingReturnSummaryDto(
                horizon, windowDays, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, List.of(),
                "CANDIDATE — RET-03 / Rolling Window Return"
            );
        }

        List<Double> sorted = new ArrayList<>(returns);
        Collections.sort(sorted);

        double min = sorted.get(0);
        double max = sorted.get(sorted.size() - 1);
        double p25 = percentile(sorted, 0.25);
        double median = percentile(sorted, 0.50);
        double p75 = percentile(sorted, 0.75);

        double sum = 0.0;
        int positiveCount = 0;
        for (double r : sorted) {
            sum += r;
            if (r > 0.0) positiveCount++;
        }
        double mean = sum / sorted.size();

        double varSum = 0.0;
        for (double r : sorted) {
            varSum += (r - mean) * (r - mean);
        }
        double stdDev = Math.sqrt(varSum / sorted.size());
        double positivePct = ((double) positiveCount / sorted.size()) * 100.0;

        return new RollingReturnSummaryDto(
            horizon,
            windowDays,
            returns.size(),
            min,
            p25,
            median,
            p75,
            max,
            mean,
            stdDev,
            positivePct,
            sampledPoints,
            "CANDIDATE — RET-03 / Rolling Window Return"
        );
    }

    private double percentile(List<Double> sorted, double p) {
        if (sorted.isEmpty()) return 0.0;
        int idx = (int) Math.round(p * (sorted.size() - 1));
        idx = Math.max(0, Math.min(idx, sorted.size() - 1));
        return sorted.get(idx);
    }
}

