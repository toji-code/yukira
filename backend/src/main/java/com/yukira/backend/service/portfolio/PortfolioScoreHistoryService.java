package com.yukira.backend.service.portfolio;

import com.yukira.backend.domain.entity.AnalyticalScore;
import com.yukira.backend.domain.entity.ScoreDimension;
import com.yukira.backend.domain.entity.ScoreMetricContribution;
import com.yukira.backend.dto.portfolio.PortfolioHoldingDto;
import com.yukira.backend.dto.portfolio.PortfolioScoreHistoryDto;
import com.yukira.backend.dto.portfolio.PortfolioSummaryDto;
import com.yukira.backend.repository.AnalyticalScoreRepository;
import com.yukira.backend.repository.ScoreDimensionRepository;
import com.yukira.backend.repository.ScoreMetricContributionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PortfolioScoreHistoryService {

    public static final String HISTORICAL_PORTFOLIO_COMPOSITION_UNAVAILABLE =
        "Historical portfolio composition is not available for this date.";

    private static final String SELECTION_POLICY =
        "Select the latest persisted analytical score snapshot whose asOfDate is less than or equal to the requested date. Future scores are never selected.";

    private final InvestorPortfolioService investorPortfolioService;
    private final AnalyticalScoreRepository analyticalScoreRepository;
    private final ScoreDimensionRepository scoreDimensionRepository;
    private final ScoreMetricContributionRepository scoreMetricContributionRepository;

    public PortfolioScoreHistoryService(
        InvestorPortfolioService investorPortfolioService,
        AnalyticalScoreRepository analyticalScoreRepository,
        ScoreDimensionRepository scoreDimensionRepository,
        ScoreMetricContributionRepository scoreMetricContributionRepository
    ) {
        this.investorPortfolioService = investorPortfolioService;
        this.analyticalScoreRepository = analyticalScoreRepository;
        this.scoreDimensionRepository = scoreDimensionRepository;
        this.scoreMetricContributionRepository = scoreMetricContributionRepository;
    }

    public PortfolioScoreHistoryDto getPortfolioScoreHistory(String auth0Subject, LocalDate requestedAsOfDate) {
        PortfolioSummaryDto summary = investorPortfolioService.getPortfolioSummary(auth0Subject);
        LocalDate effectiveDate = requestedAsOfDate != null ? requestedAsOfDate : latestCurrentScoreDate(summary);

        List<Long> schemeOptionIds = summary.holdings().stream()
            .map(PortfolioHoldingDto::schemeOptionId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

        Map<Long, List<AnalyticalScore>> historyByOption = schemeOptionIds.isEmpty()
            ? Collections.emptyMap()
            : analyticalScoreRepository.findHistoryBySchemeOptionIds(schemeOptionIds).stream()
                .collect(Collectors.groupingBy(
                    s -> s.getSchemeOption().getId(),
                    LinkedHashMap::new,
                    Collectors.toList()
                ));

        Map<Long, AnalyticalScore> selectedByOption = (schemeOptionIds.isEmpty() || effectiveDate == null)
            ? Collections.emptyMap()
            : analyticalScoreRepository.findLatestOnOrBeforeBySchemeOptionIds(schemeOptionIds, effectiveDate).stream()
                .collect(Collectors.toMap(s -> s.getSchemeOption().getId(), s -> s, this::preferLatest, LinkedHashMap::new));

        Map<Long, AnalyticalScore> currentByOption = new LinkedHashMap<>();
        historyByOption.forEach((optionId, scores) -> {
            if (!scores.isEmpty()) {
                currentByOption.put(optionId, scores.get(0));
            }
        });

        List<Long> scoreIds = new ArrayList<>();
        historyByOption.values().forEach(scores -> scores.stream()
            .map(AnalyticalScore::getId)
            .filter(Objects::nonNull)
            .forEach(scoreIds::add));

        Map<Long, List<ScoreDimension>> dimensionsByScoreId = scoreIds.isEmpty()
            ? Collections.emptyMap()
            : scoreDimensionRepository.findByAnalyticalScoreIdIn(scoreIds).stream()
                .collect(Collectors.groupingBy(d -> d.getAnalyticalScore().getId(), LinkedHashMap::new, Collectors.toList()));

        List<Long> dimensionIds = dimensionsByScoreId.values().stream()
            .flatMap(List::stream)
            .map(ScoreDimension::getId)
            .filter(Objects::nonNull)
            .toList();

        Map<Long, List<ScoreMetricContribution>> contributionsByDimensionId = dimensionIds.isEmpty()
            ? Collections.emptyMap()
            : scoreMetricContributionRepository.findByScoreDimensionIdIn(dimensionIds).stream()
                .collect(Collectors.groupingBy(c -> c.getScoreDimension().getId(), LinkedHashMap::new, Collectors.toList()));

        List<PortfolioScoreHistoryDto.HoldingScoreHistoryDto> holdings = summary.holdings().stream()
            .map(h -> toHoldingHistory(
                h,
                currentByOption.get(h.schemeOptionId()),
                selectedByOption.get(h.schemeOptionId()),
                historyByOption.getOrDefault(h.schemeOptionId(), Collections.emptyList()),
                dimensionsByScoreId,
                contributionsByDimensionId,
                effectiveDate
            ))
            .toList();

        List<String> limitations = new ArrayList<>(summary.dataQualityLimitations());
        limitations.add(HISTORICAL_PORTFOLIO_COMPOSITION_UNAVAILABLE);
        limitations.add("Historical fund score snapshots are persisted analytical evidence and are independent of investor holding performance.");

        List<String> questions = new ArrayList<>(summary.investigationQuestions());
        questions.add("Review historical fund score evidence separately from investor-specific gain/loss until historical portfolio holdings composition is available.");

        return new PortfolioScoreHistoryDto(
            effectiveDate,
            SELECTION_POLICY,
            summary.portfolioAnalyticalScore(),
            new PortfolioScoreHistoryDto.HistoricalPortfolioScoreAvailabilityDto(
                "UNAVAILABLE",
                effectiveDate,
                null,
                HISTORICAL_PORTFOLIO_COMPOSITION_UNAVAILABLE,
                "Current holdings do not constitute a historical holdings ledger; no historical portfolio score is reconstructed."
            ),
            holdings,
            limitations.stream().distinct().toList(),
            questions.stream().distinct().toList()
        );
    }

    private PortfolioScoreHistoryDto.HoldingScoreHistoryDto toHoldingHistory(
        PortfolioHoldingDto holding,
        AnalyticalScore current,
        AnalyticalScore selected,
        List<AnalyticalScore> history,
        Map<Long, List<ScoreDimension>> dimensionsByScoreId,
        Map<Long, List<ScoreMetricContribution>> contributionsByDimensionId,
        LocalDate requestedAsOfDate
    ) {
        return new PortfolioScoreHistoryDto.HoldingScoreHistoryDto(
            holding.schemeOptionId(),
            holding.fundName(),
            holding.amcName(),
            holding.amfiCode(),
            holding.isin(),
            new PortfolioScoreHistoryDto.HoldingPerformanceDto(
                holding.valuationState(),
                holding.units(),
                holding.availableValue(),
                holding.investedAmount(),
                holding.absoluteGainLoss(),
                holding.absoluteGainLossPercentage(),
                holding.portfolioWeight()
            ),
            toSnapshot(current, dimensionsByScoreId, contributionsByDimensionId, null),
            selected != null
                ? toSnapshot(selected, dimensionsByScoreId, contributionsByDimensionId, null)
                : unavailableSnapshot(holding.schemeOptionId(), requestedAsOfDate),
            history.stream()
                .map(score -> toSnapshot(score, dimensionsByScoreId, contributionsByDimensionId, null))
                .toList()
        );
    }

    private PortfolioScoreHistoryDto.ScoreSnapshotDto unavailableSnapshot(Long schemeOptionId, LocalDate requestedAsOfDate) {
        String reason = requestedAsOfDate != null
            ? "No persisted analytical score snapshot exists on or before " + requestedAsOfDate + "."
            : "No persisted analytical score snapshot exists for this holding.";
        return new PortfolioScoreHistoryDto.ScoreSnapshotDto(
            "UNAVAILABLE",
            null,
            schemeOptionId,
            null,
            null,
            "NOT_AVAILABLE",
            null,
            null,
            null,
            null,
            null,
            null,
            reason,
            Collections.emptyList()
        );
    }

    private PortfolioScoreHistoryDto.ScoreSnapshotDto toSnapshot(
        AnalyticalScore score,
        Map<Long, List<ScoreDimension>> dimensionsByScoreId,
        Map<Long, List<ScoreMetricContribution>> contributionsByDimensionId,
        String unavailableReason
    ) {
        if (score == null) {
            return null;
        }
        List<PortfolioScoreHistoryDto.DimensionSnapshotDto> dimensions = dimensionsByScoreId
            .getOrDefault(score.getId(), Collections.emptyList())
            .stream()
            .map(d -> toDimension(d, contributionsByDimensionId.getOrDefault(d.getId(), Collections.emptyList())))
            .toList();

        return new PortfolioScoreHistoryDto.ScoreSnapshotDto(
            "AVAILABLE",
            score.getId(),
            score.getSchemeOption().getId(),
            score.getScore(),
            score.getConfidence(),
            score.getStatus(),
            score.getScoreVersion(),
            score.getMethodologyStatus(),
            score.getAsOfDate(),
            score.getKnowledgeCutoffTime(),
            score.getCalculationRun() != null ? score.getCalculationRun().getId() : null,
            score.getReferencePopulation(),
            unavailableReason,
            dimensions
        );
    }

    private PortfolioScoreHistoryDto.DimensionSnapshotDto toDimension(
        ScoreDimension dimension,
        List<ScoreMetricContribution> contributions
    ) {
        return new PortfolioScoreHistoryDto.DimensionSnapshotDto(
            dimension.getId(),
            dimension.getDimension(),
            dimension.getDimensionName(),
            dimension.getScore(),
            dimension.getWeight(),
            dimension.getStatus(),
            dimension.getConfidence(),
            dimension.getEligibleMetricCount(),
            dimension.getTotalMetricCount(),
            contributions.stream().map(this::toContribution).toList()
        );
    }

    private PortfolioScoreHistoryDto.MetricContributionSnapshotDto toContribution(ScoreMetricContribution contribution) {
        return new PortfolioScoreHistoryDto.MetricContributionSnapshotDto(
            contribution.getId(),
            contribution.getMetricCode(),
            contribution.getMetricName(),
            contribution.getRawValue(),
            contribution.getNormalizedValue(),
            contribution.getDirection(),
            contribution.getWeight(),
            contribution.getContribution(),
            contribution.getEligibility(),
            contribution.getExclusionReason(),
            contribution.getUnit()
        );
    }

    private LocalDate latestCurrentScoreDate(PortfolioSummaryDto summary) {
        if (summary.portfolioAnalyticalScore() != null && summary.portfolioAnalyticalScore().asOfDate() != null) {
            return LocalDate.parse(summary.portfolioAnalyticalScore().asOfDate());
        }
        return null;
    }

    private AnalyticalScore preferLatest(AnalyticalScore left, AnalyticalScore right) {
        if (left.getAsOfDate() == null) {
            return right;
        }
        if (right.getAsOfDate() == null) {
            return left;
        }
        int dateCompare = left.getAsOfDate().compareTo(right.getAsOfDate());
        if (dateCompare != 0) {
            return dateCompare >= 0 ? left : right;
        }
        if (left.getCreatedAt() == null) {
            return right;
        }
        if (right.getCreatedAt() == null) {
            return left;
        }
        int createdCompare = left.getCreatedAt().compareTo(right.getCreatedAt());
        if (createdCompare != 0) {
            return createdCompare >= 0 ? left : right;
        }
        if (left.getId() == null) {
            return right;
        }
        if (right.getId() == null) {
            return left;
        }
        return left.getId().compareTo(right.getId()) >= 0 ? left : right;
    }
}