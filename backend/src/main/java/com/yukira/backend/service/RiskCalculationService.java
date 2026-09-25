package com.yukira.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yukira.backend.client.QuantEngineClient;
import com.yukira.backend.client.dto.CalculationDtos.CalculationRequestDto;
import com.yukira.backend.client.dto.CalculationDtos.CalculationResponseDto;
import com.yukira.backend.client.dto.CalculationDtos.MetricOutputItemDto;
import com.yukira.backend.client.dto.CalculationDtos.ObservationItemDto;
import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Risk Calculation Service:
 *
 * Implements deterministic execution of Risk & Tail metrics:
 *   - RSK-01: 3-Year Annualized Volatility
 *   - RSK-02: Downside Semideviation (3Y)
 *   - RSK-03: 3-Year Maximum Drawdown
 *   - RSK-04: Maximum Drawdown Duration
 *   - RSK-05: Ulcer Index (3Y)
 *
 * Adheres strictly to the YUKIRA governance mandate:
 *   - Pure Python quant-engine is the sole calculation authority
 *   - Zero Java mathematical fallback
 *   - Strict PIT resolution (effective_date <= analysis_cutoff AND availability_time <= knowledge_cutoff)
 *   - Immutable input observation lineage and cryptographic snapshot hashing
 *   - Explicit Candidate / Unvalidated convention disclosure
 */
@Service
public class RiskCalculationService {

    private record RiskMetricMeta(
        String metricCode,
        String metricName,
        String methodologyCode,
        String units,
        String annualizationConvention,
        String denominatorConvention,
        String formulaDisclosure,
        int minObservations
    ) {}

    private static final Map<String, RiskMetricMeta> METRIC_CONFIG = Map.of(
        "RSK-01", new RiskMetricMeta(
            "RSK-01", "3-Year Annualized Volatility", "RSK_01_3Y_VOLATILITY",
            "PERCENTAGE", "SQRT_252_CANDIDATE", "N_MINUS_ONE_CANDIDATE",
            "s = sqrt( (1 / (N - 1)) * sum((R_t - R_mean)^2) ); sigma_annual = s * sqrt(252)",
            700
        ),
        "RSK-02", new RiskMetricMeta(
            "RSK-02", "Downside Semideviation (3Y)", "RSK_02_3Y_DOWNSIDE_DEVIATION",
            "PERCENTAGE", "SQRT_252_CANDIDATE", "N_MINUS_ONE_CANDIDATE",
            "sigma_down = sqrt( (1 / (N - 1)) * sum(min(R_t - MAR, 0)^2) ) * sqrt(252)",
            700
        ),
        "RSK-03", new RiskMetricMeta(
            "RSK-03", "3-Year Maximum Drawdown", "RSK_03_3Y_MAX_DRAWDOWN",
            "PERCENTAGE", "NONE_DISCRETE_PATH", "RUNNING_PEAK_NAV_CANDIDATE",
            "Running_Peak_t = max(NAV_1 ... NAV_t); Drawdown_t = (NAV_t / Running_Peak_t) - 1; Max_Drawdown = min(Drawdown_t)",
            700
        ),
        "RSK-04", new RiskMetricMeta(
            "RSK-04", "Maximum Drawdown Duration", "RSK_04_MAX_DRAWDOWN_DURATION",
            "DAYS", "NONE", "NONE",
            "Duration = max(ElapsedCalendarDays(Peak -> Recovery)); Ongoing measured to cutoff",
            700
        ),
        "RSK-05", new RiskMetricMeta(
            "RSK-05", "Ulcer Index (3Y)", "RSK_05_3Y_ULCER_INDEX",
            "POINTS", "NONE", "N_OBSERVATIONS_CANDIDATE",
            "Pct_DD_t = 100 * ((NAV_t / Running_Peak_t) - 1); Ulcer_Index = sqrt((1 / N) * sum(Pct_DD_t^2))",
            700
        ),
        "RSK-06", new RiskMetricMeta(
            "RSK-06", "Historical Value at Risk (95% 3Y)", "RSK_06_3Y_HISTORICAL_VAR_95",
            "PERCENTAGE", "NONE_1DAY_HORIZON", "QUANTILE_RANK_POSITION",
            "VaR_0.95 = -Q_0.05(R_1, ..., R_N)",
            700
        ),
        "RSK-07", new RiskMetricMeta(
            "RSK-07", "Historical Expected Shortfall (95% 3Y)", "RSK_07_3Y_EXPECTED_SHORTFALL_95",
            "PERCENTAGE", "NONE_1DAY_HORIZON", "TAIL_OBSERVATION_COUNT",
            "ES_0.95 = - (1 / |T_tail|) * sum(R_t for R_t <= Q_0.05)",
            700
        )
    );

    private final PitObservationResolutionService pitResolutionService;
    private final NavObservationRepository navObservationRepository;
    private final SchemeOptionRepository schemeOptionRepository;
    private final MethodologyVersionRepository methodologyVersionRepository;
    private final CalculationRunRepository calculationRunRepository;
    private final MetricResultRepository metricResultRepository;
    private final CalculationRunInputObservationRepository calculationRunInputObservationRepository;
    private final QuantEngineClient quantEngineClient;
    private final MethodologyGovernanceService methodologyGovernanceService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RiskCalculationService(
        PitObservationResolutionService pitResolutionService,
        NavObservationRepository navObservationRepository,
        SchemeOptionRepository schemeOptionRepository,
        MethodologyVersionRepository methodologyVersionRepository,
        CalculationRunRepository calculationRunRepository,
        MetricResultRepository metricResultRepository,
        CalculationRunInputObservationRepository calculationRunInputObservationRepository,
        QuantEngineClient quantEngineClient,
        MethodologyGovernanceService methodologyGovernanceService
    ) {
        this.pitResolutionService = pitResolutionService;
        this.navObservationRepository = navObservationRepository;
        this.schemeOptionRepository = schemeOptionRepository;
        this.methodologyVersionRepository = methodologyVersionRepository;
        this.calculationRunRepository = calculationRunRepository;
        this.metricResultRepository = metricResultRepository;
        this.calculationRunInputObservationRepository = calculationRunInputObservationRepository;
        this.quantEngineClient = quantEngineClient;
        this.methodologyGovernanceService = methodologyGovernanceService;
    }

    @Transactional
    public CalculationRun executeRsk01Calculation(
        Long schemeOptionId,
        LocalDate requestedEndDate,
        OffsetDateTime knowledgeCutoffTime,
        String methodologyTag
    ) {
        return executeRiskMetricCalculation("RSK-01", schemeOptionId, requestedEndDate, knowledgeCutoffTime, methodologyTag);
    }

    @Transactional
    public CalculationRun executeRsk02Calculation(
        Long schemeOptionId,
        LocalDate requestedEndDate,
        OffsetDateTime knowledgeCutoffTime,
        String methodologyTag
    ) {
        return executeRiskMetricCalculation("RSK-02", schemeOptionId, requestedEndDate, knowledgeCutoffTime, methodologyTag);
    }

    @Transactional
    public CalculationRun executeRsk03Calculation(
        Long schemeOptionId,
        LocalDate requestedEndDate,
        OffsetDateTime knowledgeCutoffTime,
        String methodologyTag
    ) {
        return executeRiskMetricCalculation("RSK-03", schemeOptionId, requestedEndDate, knowledgeCutoffTime, methodologyTag);
    }

    @Transactional
    public CalculationRun executeRsk04Calculation(
        Long schemeOptionId,
        LocalDate requestedEndDate,
        OffsetDateTime knowledgeCutoffTime,
        String methodologyTag
    ) {
        return executeRiskMetricCalculation("RSK-04", schemeOptionId, requestedEndDate, knowledgeCutoffTime, methodologyTag);
    }

    @Transactional
    public CalculationRun executeRsk05Calculation(
        Long schemeOptionId,
        LocalDate requestedEndDate,
        OffsetDateTime knowledgeCutoffTime,
        String methodologyTag
    ) {
        return executeRiskMetricCalculation("RSK-05", schemeOptionId, requestedEndDate, knowledgeCutoffTime, methodologyTag);
    }

    @Transactional
    public CalculationRun executeRsk06Calculation(
        Long schemeOptionId,
        LocalDate requestedEndDate,
        OffsetDateTime knowledgeCutoffTime,
        String methodologyTag
    ) {
        return executeRiskMetricCalculation("RSK-06", schemeOptionId, requestedEndDate, knowledgeCutoffTime, methodologyTag);
    }

    @Transactional
    public CalculationRun executeRsk07Calculation(
        Long schemeOptionId,
        LocalDate requestedEndDate,
        OffsetDateTime knowledgeCutoffTime,
        String methodologyTag
    ) {
        return executeRiskMetricCalculation("RSK-07", schemeOptionId, requestedEndDate, knowledgeCutoffTime, methodologyTag);
    }

    @Transactional
    public CalculationRun executeRiskMetricCalculation(
        String metricCode,
        Long schemeOptionId,
        LocalDate requestedEndDate,
        OffsetDateTime knowledgeCutoffTime,
        String methodologyTag
    ) {
        Objects.requireNonNull(metricCode, "metricCode must not be null");
        Objects.requireNonNull(schemeOptionId, "schemeOptionId must not be null");
        Objects.requireNonNull(requestedEndDate, "requestedEndDate must not be null");
        Objects.requireNonNull(knowledgeCutoffTime, "knowledgeCutoffTime must not be null for PIT compliance");

        RiskMetricMeta meta = METRIC_CONFIG.get(metricCode);
        if (meta == null) {
            throw new IllegalArgumentException("Unsupported risk metric code: " + metricCode);
        }

        if (requestedEndDate.isAfter(knowledgeCutoffTime.toLocalDate())) {
            throw new IllegalArgumentException(String.format(
                "Strict PIT Invariant: requestedEndDate (%s) cannot exceed knowledgeCutoff date (%s)",
                requestedEndDate, knowledgeCutoffTime.toLocalDate()
            ));
        }

        LocalDate requestedStartDate = requestedEndDate.minusYears(3);
        String tag = (methodologyTag != null && !methodologyTag.isBlank()) ? methodologyTag : "CANDIDATE_V1";

        SchemeOption option = schemeOptionRepository.findById(schemeOptionId)
            .orElseThrow(() -> new IllegalArgumentException("SchemeOption not found: " + schemeOptionId));

        MethodologyVersion methodologyVersion = methodologyVersionRepository
            .findByMethodologyCodeAndVersionTag(meta.methodologyCode(), tag)
            .orElseGet(() -> methodologyGovernanceService.registerMethodologyVersion(new MethodologyVersion(
                meta.methodologyCode(), tag, "CANDIDATE", "pending_commit"
            ), "RISK_CALCULATION_SERVICE"));

        if (!methodologyVersion.isLocked()) {
            methodologyVersion = methodologyGovernanceService.lockVersion(
                methodologyVersion.getId(), "RISK_CALCULATION_SERVICE"
            );
        }

        // Initialize CalculationRun (Risk metrics are single-asset metrics, benchmark is null)
        CalculationRun run = new CalculationRun(
            option, requestedEndDate, knowledgeCutoffTime, methodologyVersion, "FASTAPI-QUANT-0.1.0"
        );
        run.setRunStatus("RUNNING");
        run = calculationRunRepository.save(run);

        // 1. Retrieve PIT-eligible observations
        List<NavObservation> candidates = navObservationRepository.findAuthoritativeObservationsAsOfCutoff(
            schemeOptionId, requestedEndDate, knowledgeCutoffTime
        );

        // Filter to [requestedStartDate, requestedEndDate] and resolve authoritative observation per effective date
        Map<LocalDate, List<NavObservation>> groupedByDate = new TreeMap<>();
        for (NavObservation obs : candidates) {
            if (!obs.getEffectiveDate().isBefore(requestedStartDate) && !obs.getEffectiveDate().isAfter(requestedEndDate)) {
                groupedByDate.computeIfAbsent(obs.getEffectiveDate(), k -> new ArrayList<>()).add(obs);
            }
        }

        List<NavObservation> resolvedObservations = new ArrayList<>();
        for (Map.Entry<LocalDate, List<NavObservation>> entry : groupedByDate.entrySet()) {
            LocalDate date = entry.getKey();
            var pitResult = pitResolutionService.resolveAuthoritativeObservation(schemeOptionId, date, knowledgeCutoffTime);
            if (pitResult.authoritativeObservation().isPresent()) {
                resolvedObservations.add(pitResult.authoritativeObservation().get());
            }
        }

        int minObservationsRequired = meta.minObservations();

        // 2. Minimum observation history check
        if (resolvedObservations.size() < minObservationsRequired) {
            String errorMsg = String.format(
                "Insufficient observation history for %s: %d valid trading days available between %s and %s, minimum %d required.",
                metricCode, resolvedObservations.size(), requestedStartDate, requestedEndDate, minObservationsRequired
            );
            run.setRunStatus("FAILED");
            run.setExecutionCompletedAt(OffsetDateTime.now());
            run.setErrorMessage(errorMsg);

            MetricResult metricResult = new MetricResult(
                run, metricCode, "3Y", null, meta.units(), "INSUFFICIENT_DATA"
            );
            metricResult.setErrorMessage(errorMsg);

            Map<String, Object> failureDiag = new HashMap<>();
            failureDiag.put("methodology_status", "CANDIDATE");
            failureDiag.put("methodology_tag", tag);
            failureDiag.put("benchmark_required", false);
            failureDiag.put("annualization_convention", meta.annualizationConvention());
            failureDiag.put("denominator_convention", meta.denominatorConvention());
            failureDiag.put("requested_start_date", requestedStartDate.toString());
            failureDiag.put("requested_end_date", requestedEndDate.toString());
            failureDiag.put("observation_count", resolvedObservations.size());
            failureDiag.put("min_observations_required", minObservationsRequired);
            failureDiag.put("knowledge_cutoff_time", knowledgeCutoffTime.toString());
            failureDiag.put("failure_reason", errorMsg);
            failureDiag.put("insufficient_evidence", true);

            try {
                metricResult.setDiagnostics(objectMapper.writeValueAsString(failureDiag));
            } catch (Exception ignored) {}

            metricResultRepository.save(metricResult);
            return calculationRunRepository.save(run);
        }

        // 3. Persist immutable input observation linkages
        for (NavObservation obs : resolvedObservations) {
            calculationRunInputObservationRepository.save(new CalculationRunInputObservation(
                run, obs, obs.getEffectiveDate(), obs.getRevisionSeq()
            ));
        }

        // 4. Build DTO series and compute cryptographic snapshot hash
        List<ObservationItemDto> navSeries = new ArrayList<>();
        for (NavObservation obs : resolvedObservations) {
            navSeries.add(new ObservationItemDto(
                obs.getEffectiveDate().toString(),
                obs.getNavValue().doubleValue(),
                obs.getAvailabilityTime().toString(),
                obs.getRevisionSeq()
            ));
        }

        String inputSnapshotHash = computeSnapshotHash(navSeries);
        run.setInputSnapshotSha256(inputSnapshotHash);

        // 5. Dispatch deterministic calculation to Quant Engine
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("periods_per_year", 252.0);
            params.put("min_observations", minObservationsRequired);
            params.put("methodology_status", "CANDIDATE");
            if ("RSK-02".equals(metricCode)) {
                params.put("target_return", 0.0);
            }

            CalculationRequestDto requestDto = new CalculationRequestDto(
                metricCode.replace("-", "") + "-" + UUID.randomUUID().toString().substring(0, 8),
                String.valueOf(schemeOptionId),
                null,
                requestedEndDate.toString(),
                knowledgeCutoffTime.toString(),
                tag,
                List.of(metricCode),
                navSeries,
                Collections.emptyList(),
                params
            );

            CalculationResponseDto response = quantEngineClient.executeCalculation(requestDto);

            if (response.results() != null && !response.results().isEmpty()) {
                MetricOutputItemDto item = response.results().get(0);
                MetricResult metricResult = new MetricResult(
                    run,
                    metricCode,
                    "3Y",
                    item.numericValue(),
                    meta.units(),
                    item.status() != null ? item.status() : "CALCULATED"
                );

                Map<String, Object> diagnostics = new HashMap<>();
                diagnostics.put("methodology_status", "CANDIDATE");
                diagnostics.put("methodology_tag", tag);
                diagnostics.put("benchmark_required", false);
                diagnostics.put("annualization_convention", meta.annualizationConvention());
                diagnostics.put("denominator_convention", meta.denominatorConvention());
                diagnostics.put("requested_start_date", requestedStartDate.toString());
                diagnostics.put("requested_end_date", requestedEndDate.toString());
                diagnostics.put("actual_start_date", resolvedObservations.get(0).getEffectiveDate().toString());
                diagnostics.put("actual_end_date", resolvedObservations.get(resolvedObservations.size() - 1).getEffectiveDate().toString());
                diagnostics.put("start_nav", resolvedObservations.get(0).getNavValue());
                diagnostics.put("end_nav", resolvedObservations.get(resolvedObservations.size() - 1).getNavValue());
                diagnostics.put("observation_count", resolvedObservations.size());
                diagnostics.put("min_observations_required", minObservationsRequired);
                diagnostics.put("knowledge_cutoff_time", knowledgeCutoffTime.toString());

                if (item.diagnostics() != null) {
                    diagnostics.putAll(item.diagnostics());
                }

                metricResult.setDiagnostics(objectMapper.writeValueAsString(diagnostics));
                metricResultRepository.save(metricResult);

                if ("CALCULATED".equals(item.status()) && item.numericValue() != null) {
                    run.setRunStatus("COMPLETED");
                } else {
                    run.setRunStatus("FAILED");
                    run.setErrorMessage(item.errorMessage() != null ? item.errorMessage() : "Quant Engine returned non-calculated status");
                }
            } else {
                run.setRunStatus("FAILED");
                run.setErrorMessage("Quant Engine returned empty results");
            }
            run.setExecutionCompletedAt(OffsetDateTime.now());

        } catch (Exception e) {
            String errorMsg = "Quant Engine calculation failure: " + e.getMessage();
            run.setRunStatus("FAILED");
            run.setExecutionCompletedAt(OffsetDateTime.now());
            run.setErrorMessage(errorMsg);

            MetricResult metricResult = new MetricResult(
                run, metricCode, "3Y", null, meta.units(), "FAILED"
            );
            metricResult.setErrorMessage(errorMsg);

            Map<String, Object> failureDiag = new HashMap<>();
            failureDiag.put("methodology_status", "CANDIDATE");
            failureDiag.put("methodology_tag", tag);
            failureDiag.put("benchmark_required", false);
            failureDiag.put("annualization_convention", meta.annualizationConvention());
            failureDiag.put("denominator_convention", meta.denominatorConvention());
            failureDiag.put("requested_start_date", requestedStartDate.toString());
            failureDiag.put("requested_end_date", requestedEndDate.toString());
            failureDiag.put("actual_start_date", resolvedObservations.get(0).getEffectiveDate().toString());
            failureDiag.put("actual_end_date", resolvedObservations.get(resolvedObservations.size() - 1).getEffectiveDate().toString());
            failureDiag.put("observation_count", resolvedObservations.size());
            failureDiag.put("min_observations_required", minObservationsRequired);
            failureDiag.put("knowledge_cutoff_time", knowledgeCutoffTime.toString());
            failureDiag.put("failure_reason", errorMsg);

            try {
                metricResult.setDiagnostics(objectMapper.writeValueAsString(failureDiag));
            } catch (Exception ignored) {}

            metricResultRepository.save(metricResult);
        }

        return calculationRunRepository.save(run);
    }

    private String computeSnapshotHash(List<ObservationItemDto> navSeries) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (ObservationItemDto obs : navSeries) {
                sb.append(obs.effectiveDate()).append(":").append(obs.value()).append(";");
            }
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return "0000000000000000000000000000000000000000000000000000000000000000";
        }
    }
}
