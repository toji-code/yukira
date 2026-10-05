package com.yukira.backend.service.scoring;

import com.yukira.backend.domain.entity.AnalyticalScore;
import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.domain.entity.BenchmarkObservation;
import com.yukira.backend.domain.entity.NavObservation;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.ingestion.AmfiDataFreshnessDto;
import com.yukira.backend.dto.scoring.AnalyticalRefreshResultDto;
import com.yukira.backend.dto.scoring.CurrentScoreResponseDto;
import com.yukira.backend.repository.AnalyticalScoreRepository;
import com.yukira.backend.repository.BenchmarkObservationRepository;
import com.yukira.backend.repository.BenchmarkRepository;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.scoring.config.ScoreMethodologyConfig;
import com.yukira.backend.scoring.dto.AnalyticalScoreResponse;
import com.yukira.backend.scoring.dto.ScoreCalculationRequest;
import com.yukira.backend.scoring.service.AnalyticalScoringService;
import com.yukira.backend.service.ingestion.AmfiDataFreshnessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Service orchestrating Production Analytical Refresh & Current Score V1.
 *
 * Workflow:
 * CHECK DATA FRESHNESS → IDENTIFY ELIGIBLE FUNDS → VERIFY BENCHMARK DATA → BUILD PIT ANALYTICAL WINDOW →
 * RUN EXISTING QUANT PIPELINE → VALIDATE METRIC OUTPUT → PERSIST METRIC RESULTS → RUN SCORE ENGINE →
 * PERSIST NEW ANALYTICAL SCORE SNAPSHOT → EXPOSE CURRENT SCORE READ MODEL
 *
 * Epistemic & Operational Invariants:
 * 1. Historical AnalyticalScore snapshots remain immutable and are never overwritten.
 * 2. Every refresh creates new CalculationRun and AnalyticalScore snapshots under explicit asOfDate & knowledgeCutoffTime.
 * 3. Idempotent: re-running for an identical (schemeOptionId, asOfDate, scoreVersion) returns existing persisted snapshots.
 * 4. Benchmark Dependency Gate: Evaluates benchmark observations for the requested analytical window up to asOfDate. Missing benchmark data is explicitly reported without fabrication.
 * 5. Quant engine execution is restricted to controlled batch refreshes; investor read requests do NOT trigger calculations.
 */
@Service
public class AnalyticalRefreshService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticalRefreshService.class);

    public static final String DEFAULT_BENCHMARK_CODE = "NIFTY_500_TRI";
    public static final String DISCLAIMER = "YUKIRA Production Analytical Refresh executes deterministic quantitative scoring across verified point-in-time NAV and benchmark observation windows. It is strictly non-advisory and does NOT manufacture scores when benchmark or NAV observations are insufficient.";

    private final AnalyticalScoringService analyticalScoringService;
    private final AmfiDataFreshnessService amfiDataFreshnessService;
    private final NavObservationRepository navObservationRepository;
    private final BenchmarkObservationRepository benchmarkObservationRepository;
    private final BenchmarkRepository benchmarkRepository;
    private final SchemeOptionRepository schemeOptionRepository;
    private final AnalyticalScoreRepository analyticalScoreRepository;
    private final ScoreMethodologyConfig methodologyConfig;

    public AnalyticalRefreshService(
        AnalyticalScoringService analyticalScoringService,
        AmfiDataFreshnessService amfiDataFreshnessService,
        NavObservationRepository navObservationRepository,
        BenchmarkObservationRepository benchmarkObservationRepository,
        BenchmarkRepository benchmarkRepository,
        SchemeOptionRepository schemeOptionRepository,
        AnalyticalScoreRepository analyticalScoreRepository,
        ScoreMethodologyConfig methodologyConfig
    ) {
        this.analyticalScoringService = analyticalScoringService;
        this.amfiDataFreshnessService = amfiDataFreshnessService;
        this.navObservationRepository = navObservationRepository;
        this.benchmarkObservationRepository = benchmarkObservationRepository;
        this.benchmarkRepository = benchmarkRepository;
        this.schemeOptionRepository = schemeOptionRepository;
        this.analyticalScoreRepository = analyticalScoreRepository;
        this.methodologyConfig = methodologyConfig;
    }

    /**
     * Executes controlled production analytical refresh for the active fund universe.
     */
    @Transactional
    public AnalyticalRefreshResultDto refreshAnalyticalScores(LocalDate reqAsOfDate, OffsetDateTime reqKnowledgeCutoff) {
        log.info("Starting production analytical score refresh batch...");
        List<String> messages = new ArrayList<>();

        // 1. Determine asOfDate & knowledgeCutoffTime
        LocalDate maxNavDate = navObservationRepository.findMaxEffectiveDate().orElse(LocalDate.of(2024, 1, 15));
        LocalDate asOfDate = reqAsOfDate != null ? reqAsOfDate : maxNavDate;
        OffsetDateTime knowledgeCutoff = reqKnowledgeCutoff != null ? reqKnowledgeCutoff
            : OffsetDateTime.of(asOfDate.getYear(), asOfDate.getMonthValue(), asOfDate.getDayOfMonth(), 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        // 2. Evaluate NAV Data Freshness Status
        AmfiDataFreshnessDto freshness = amfiDataFreshnessService.getFreshnessStatus(asOfDate);
        messages.add("NAV Data Freshness state: " + freshness.freshnessState() + " (latest NAV date: " + maxNavDate + ")");

        // 3. Benchmark Observation Window Verification
        Benchmark benchmark = benchmarkRepository.findByCode(DEFAULT_BENCHMARK_CODE)
            .orElseGet(() -> benchmarkRepository.findAll().stream().findFirst().orElse(null));

        boolean benchmarkDataAvailable = false;
        LocalDate maxBenchmarkDate = null;
        long benchmarkObsCount = 0;

        if (benchmark != null) {
            LocalDate startDate = asOfDate.minusYears(3);
            benchmarkObsCount = benchmarkObservationRepository.countByBenchmarkIdAndDateRange(benchmark.getId(), startDate, asOfDate);
            List<BenchmarkObservation> bmObs = benchmarkObservationRepository.findByBenchmarkId(benchmark.getId());
            maxBenchmarkDate = bmObs.stream()
                .map(BenchmarkObservation::getEffectiveDate)
                .max(Comparator.naturalOrder())
                .orElse(null);

            if (maxBenchmarkDate != null && !maxBenchmarkDate.isBefore(asOfDate) && benchmarkObsCount >= 700) {
                benchmarkDataAvailable = true;
                messages.add("Benchmark " + benchmark.getCode() + " observations verified for window " + startDate + " to " + asOfDate + " (N=" + benchmarkObsCount + ")");
            } else {
                messages.add(String.format("BENCHMARK DATA DEPENDENCY: Benchmark %s observations (latest date: %s, N=%d) do not fully cover requested analytical cutoff %s.",
                    benchmark.getCode(), maxBenchmarkDate != null ? maxBenchmarkDate : "NONE", benchmarkObsCount, asOfDate));
            }
        } else {
            messages.add("BENCHMARK UNMAPPED: Primary benchmark " + DEFAULT_BENCHMARK_CODE + " not found in system repository.");
        }

        // 4. Universe Active Scheme Options Processing
        List<SchemeOption> allOptions = schemeOptionRepository.findAll();
        List<SchemeOption> activeOptions = allOptions.stream()
            .filter(o -> "ACTIVE".equalsIgnoreCase(o.getStatus()) || o.getStatus() == null)
            .toList();

        long totalProcessed = activeOptions.size();
        long scoresGenerated = 0;
        long scoresCached = 0;
        long scoresAvailable = 0;
        long scoresPartial = 0;
        long scoresDataQualityLimited = 0;
        long scoresUnavailable = 0;
        long scoresNotApplicable = 0;

        for (SchemeOption option : activeOptions) {
            // Idempotency check: see if score entity already exists for (option, asOfDate, scoreVersion)
            List<AnalyticalScore> existing = analyticalScoreRepository.findBySchemeOptionAndDateAndVersion(
                option.getId(), asOfDate, methodologyConfig.getScoreVersion()
            );

            AnalyticalScoreResponse resp;
            if (!existing.isEmpty()) {
                scoresCached++;
                resp = analyticalScoringService.getLatestScore(option.getId()).orElse(null);
            } else {
                ScoreCalculationRequest req = new ScoreCalculationRequest(
                    option.getId(),
                    null,
                    asOfDate,
                    knowledgeCutoff,
                    null
                );
                resp = analyticalScoringService.calculateScore(req);
                scoresGenerated++;
            }

            if (resp != null) {
                String status = resp.status() != null ? resp.status().toUpperCase(Locale.ENGLISH) : "UNKNOWN";
                switch (status) {
                    case "AVAILABLE" -> scoresAvailable++;
                    case "PARTIAL" -> scoresPartial++;
                    case "DATA_QUALITY_LIMITED" -> scoresDataQualityLimited++;
                    case "INSUFFICIENT_DATA" -> scoresUnavailable++;
                    case "NOT_APPLICABLE" -> scoresNotApplicable++;
                    default -> scoresUnavailable++;
                }
            } else {
                scoresUnavailable++;
            }
        }

        String refreshStatus;
        if (!benchmarkDataAvailable && asOfDate.isAfter(LocalDate.of(2024, 1, 15))) {
            refreshStatus = "BENCHMARK_DEPENDENCY_REQUIRED";
        } else if (scoresAvailable > 0 || scoresPartial > 0) {
            refreshStatus = "SUCCESS";
        } else if (scoresGenerated > 0 || scoresCached > 0) {
            refreshStatus = "PARTIAL";
        } else {
            refreshStatus = "FAILED";
        }

        String summary = String.format(Locale.ENGLISH,
            "Analytical Refresh %s as of %s: Processed %d active scheme options (%d scores generated, %d cached from idempotent ledger). %d AVAILABLE, %d PARTIAL, %d DATA_QUALITY_LIMITED, %d INSUFFICIENT_DATA, %d NOT_APPLICABLE.",
            refreshStatus, asOfDate, totalProcessed, scoresGenerated, scoresCached,
            scoresAvailable, scoresPartial, scoresDataQualityLimited, scoresUnavailable, scoresNotApplicable);

        messages.add(summary);

        return new AnalyticalRefreshResultDto(
            refreshStatus,
            asOfDate,
            knowledgeCutoff,
            freshness.freshnessState(),
            maxNavDate,
            benchmark != null ? benchmark.getCode() : DEFAULT_BENCHMARK_CODE,
            benchmarkDataAvailable,
            maxBenchmarkDate,
            totalProcessed,
            scoresGenerated,
            scoresCached,
            scoresAvailable,
            scoresPartial,
            scoresDataQualityLimited,
            scoresUnavailable,
            scoresNotApplicable,
            summary,
            messages,
            DISCLAIMER
        );
    }

    /**
     * Retrieves the current analytical score for a scheme option with data freshness & PIT context.
     * Operates purely as a read operation — zero calculation on investor HTTP requests.
     */
    @Transactional(readOnly = true)
    public Optional<CurrentScoreResponseDto> getCurrentScore(Long schemeOptionId) {
        Optional<AnalyticalScoreResponse> scoreOpt = analyticalScoringService.getLatestScore(schemeOptionId);
        if (scoreOpt.isEmpty()) {
            return Optional.empty();
        }

        AnalyticalScoreResponse score = scoreOpt.get();
        AmfiDataFreshnessDto freshness = amfiDataFreshnessService.getFreshnessStatus(score.asOfDate());

        // Resolve max effective date for this scheme option to evaluate whether this score is from the latest available date
        Optional<LocalDate> maxSchemeDateOpt = navObservationRepository.findTopBySchemeOptionIdOrderByEffectiveDateDesc(schemeOptionId)
            .map(NavObservation::getEffectiveDate);
        LocalDate maxNavDate = maxSchemeDateOpt.orElseGet(() -> navObservationRepository.findMaxEffectiveDate().orElse(LocalDate.of(2024, 1, 15)));
        boolean isCurrent = !score.asOfDate().isBefore(maxNavDate);

        CurrentScoreResponseDto currentDto = new CurrentScoreResponseDto(
            score.scoreId(),
            score.schemeOptionId(),
            score.schemeName(),
            score.amfiCode(),
            score.isin(),
            score.score(),
            score.confidence(),
            score.status(),
            score.scoreVersion(),
            score.methodologyStatus(),
            score.asOfDate(),
            score.knowledgeCutoffTime(),
            score.calculationRunId(),
            score.referencePopulation(),
            score.summary(),
            score.disclaimer(),
            isCurrent,
            freshness.freshnessState(),
            score.dimensions(),
            score.evidenceConfidence()
        );

        return Optional.of(currentDto);
    }
}
