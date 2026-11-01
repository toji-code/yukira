package com.yukira.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yukira.backend.client.QuantEngineClient;
import com.yukira.backend.client.dto.CalculationDtos.CalculationRequestDto;
import com.yukira.backend.client.dto.CalculationDtos.CalculationResponseDto;
import com.yukira.backend.client.dto.CalculationDtos.MetricOutputItemDto;
import com.yukira.backend.client.dto.CalculationDtos.ObservationItemDto;
import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import com.yukira.backend.universe.AnalyticalHorizon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class CalculationOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(CalculationOrchestratorService.class);
    private static final Map<String, String> CANONICAL_TO_ENGINE_METRIC_CODE = Map.of();

    private final NavObservationRepository navObservationRepository;
    private final BenchmarkObservationRepository benchmarkObservationRepository;
    private final RiskFreeObservationRepository riskFreeObservationRepository;
    private final SchemeOptionRepository schemeOptionRepository;
    private final BenchmarkRepository benchmarkRepository;
    private final MethodologyVersionRepository methodologyVersionRepository;
    private final CalculationRunRepository calculationRunRepository;
    private final MetricResultRepository metricResultRepository;
    private final CalculationRunInputObservationRepository calculationRunInputObservationRepository;
    private final QuantEngineClient quantEngineClient;
    private final PitObservationResolutionService pitObservationResolutionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CalculationOrchestratorService(
        NavObservationRepository navObservationRepository,
        BenchmarkObservationRepository benchmarkObservationRepository,
        RiskFreeObservationRepository riskFreeObservationRepository,
        SchemeOptionRepository schemeOptionRepository,
        BenchmarkRepository benchmarkRepository,
        MethodologyVersionRepository methodologyVersionRepository,
        CalculationRunRepository calculationRunRepository,
        MetricResultRepository metricResultRepository,
        CalculationRunInputObservationRepository calculationRunInputObservationRepository,
        QuantEngineClient quantEngineClient,
        PitObservationResolutionService pitObservationResolutionService
    ) {
        this.navObservationRepository = navObservationRepository;
        this.benchmarkObservationRepository = benchmarkObservationRepository;
        this.riskFreeObservationRepository = riskFreeObservationRepository;
        this.schemeOptionRepository = schemeOptionRepository;
        this.benchmarkRepository = benchmarkRepository;
        this.methodologyVersionRepository = methodologyVersionRepository;
        this.calculationRunRepository = calculationRunRepository;
        this.metricResultRepository = metricResultRepository;
        this.calculationRunInputObservationRepository = calculationRunInputObservationRepository;
        this.quantEngineClient = quantEngineClient;
        this.pitObservationResolutionService = pitObservationResolutionService;
    }

    @Transactional
    public CalculationRun executeCalculationRun(
        Long schemeOptionId,
        Long benchmarkId,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoffTime,
        List<String> metricCodes,
        String methodologyTag,
        Map<String, Object> parameters
    ) {
        SchemeOption option = schemeOptionRepository.findById(schemeOptionId)
            .orElseThrow(() -> new IllegalArgumentException("SchemeOption not found: " + schemeOptionId));

        Benchmark benchmark = benchmarkId != null
            ? benchmarkRepository.findById(benchmarkId).orElseThrow(() -> new IllegalArgumentException("Benchmark not found: " + benchmarkId))
            : null;

        MethodologyVersion methodologyVersion = methodologyVersionRepository
            .findByMethodologyCodeAndVersionTag("CORE_QUANT", methodologyTag)
            .orElseGet(() -> methodologyVersionRepository.save(new MethodologyVersion(
                "CORE_QUANT", methodologyTag, "CANDIDATE", "0000000000000000000000000000000000000000"
            )));

        // Resolve calculation horizon start date and canonical AnalyticalHorizon
        AnalyticalHorizon analyticalHorizon = AnalyticalHorizon.THREE_YEAR;
        LocalDate startDate = null;
        if (parameters != null) {
            if (parameters.containsKey("horizon")) {
                analyticalHorizon = AnalyticalHorizon.fromCode(parameters.get("horizon").toString());
            } else if (parameters.containsKey("lookback_years")) {
                int years = Integer.parseInt(parameters.get("lookback_years").toString());
                analyticalHorizon = AnalyticalHorizon.fromCode(String.valueOf(years));
            }
            if (parameters.containsKey("start_date")) {
                startDate = LocalDate.parse(parameters.get("start_date").toString());
            } else {
                startDate = analyticalHorizon.calculateStartDate(asOfDate);
            }
        }
        if (startDate == null) {
            startDate = analyticalHorizon.calculateStartDate(asOfDate);
        }
        final LocalDate effStartDate = startDate;
        final AnalyticalHorizon effHorizon = analyticalHorizon;

        // 1. Authoritative PIT Revision-Resolution Query
        List<NavObservation> candidates = navObservationRepository
            .findAuthoritativeObservationsAsOfCutoff(schemeOptionId, asOfDate, knowledgeCutoffTime);
        Map<LocalDate, NavObservation> dedupNav = new TreeMap<>();
        for (NavObservation obs : candidates) {
            if (!obs.getEffectiveDate().isBefore(effStartDate) && !obs.getEffectiveDate().isAfter(asOfDate)) {
                dedupNav.putIfAbsent(obs.getEffectiveDate(), obs);
            }
        }
        List<NavObservation> navObservations = new ArrayList<>(dedupNav.values());

        List<BenchmarkObservation> benchmarkObservations = Collections.emptyList();
        if (benchmark != null) {
            List<BenchmarkObservation> rawBm = benchmarkObservationRepository
                .findAuthoritativeObservationsAsOfCutoff(benchmarkId, asOfDate, knowledgeCutoffTime);
            Map<LocalDate, BenchmarkObservation> dedupBm = new TreeMap<>();
            for (BenchmarkObservation b : rawBm) {
                if (!b.getEffectiveDate().isBefore(effStartDate) && !b.getEffectiveDate().isAfter(asOfDate)) {
                    dedupBm.putIfAbsent(b.getEffectiveDate(), b);
                }
            }
            benchmarkObservations = new ArrayList<>(dedupBm.values());
        }

        List<RiskFreeObservation> rawRf = riskFreeObservationRepository != null
            ? riskFreeObservationRepository.findAuthoritativeObservationsAsOfCutoff("FBIL_91D_TBILL", asOfDate, knowledgeCutoffTime)
            : Collections.emptyList();
        Map<LocalDate, RiskFreeObservation> dedupRf = new TreeMap<>();
        for (RiskFreeObservation r : rawRf) {
            if (!r.getEffectiveDate().isBefore(effStartDate) && !r.getEffectiveDate().isAfter(asOfDate)) {
                dedupRf.putIfAbsent(r.getEffectiveDate(), r);
            }
        }
        List<RiskFreeObservation> riskFreeObservations = new ArrayList<>(dedupRf.values());

        // 2. Build input series DTOs
        List<ObservationItemDto> navSeries = navObservations.stream()
            .map(n -> new ObservationItemDto(
                n.getEffectiveDate().toString(),
                n.getNavValue().doubleValue(),
                n.getAvailabilityTime().toString(),
                n.getRevisionSeq()
            ))
            .toList();

        List<ObservationItemDto> benchmarkSeries = benchmarkObservations.stream()
            .map(b -> new ObservationItemDto(
                b.getEffectiveDate().toString(),
                b.getIndexLevel().doubleValue(),
                b.getAvailabilityTime().toString(),
                b.getRevisionSeq()
            ))
            .toList();

        List<ObservationItemDto> riskFreeSeries = riskFreeObservations.stream()
            .map(r -> new ObservationItemDto(
                r.getEffectiveDate().toString(),
                r.getQuotedYield().doubleValue(),
                r.getAvailabilityTime().toString(),
                r.getRevisionSeq()
            ))
            .toList();

        // 3. Compute Input Snapshot Cryptographic SHA-256 Hash
        String inputSnapshotHash = computeSnapshotHash(navSeries, benchmarkSeries, riskFreeSeries);

        // 4. Create and persist calculation_run manifest
        String requestId = "RUN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        CalculationRun run = new CalculationRun(
            option, benchmark, asOfDate, knowledgeCutoffTime, methodologyVersion, "FASTAPI-QUANT-0.1.0"
        );
        run.setInputSnapshotSha256(inputSnapshotHash);
        run.setRunStatus("RUNNING");
        run = calculationRunRepository.save(run);

        // 4b. Persist immutable calculation input observation references
        List<CalculationRunInputObservation> inputObsList = new ArrayList<>(
            navObservations.size() + benchmarkObservations.size() + riskFreeObservations.size()
        );
        for (NavObservation nav : navObservations) {
            inputObsList.add(new CalculationRunInputObservation(
                run, nav, nav.getEffectiveDate(), nav.getRevisionSeq()
            ));
        }
        for (BenchmarkObservation bm : benchmarkObservations) {
            inputObsList.add(new CalculationRunInputObservation(
                run, bm, bm.getEffectiveDate(), bm.getRevisionSeq()
            ));
        }
        for (RiskFreeObservation rf : riskFreeObservations) {
            inputObsList.add(new CalculationRunInputObservation(
                run, rf, rf.getEffectiveDate(), rf.getRevisionSeq()
            ));
        }
        calculationRunInputObservationRepository.saveAll(inputObsList);

        try {
            // 5. Build request and call Quant Engine
            List<String> engineMetricCodes = metricCodes.stream()
                .map(code -> CANONICAL_TO_ENGINE_METRIC_CODE.getOrDefault(code, code))
                .distinct()
                .toList();
            Map<String, List<String>> engineToRequestedMetricCodes = new HashMap<>();
            for (String requestedCode : metricCodes) {
                String engineCode = CANONICAL_TO_ENGINE_METRIC_CODE.getOrDefault(requestedCode, requestedCode);
                engineToRequestedMetricCodes
                    .computeIfAbsent(engineCode, ignored -> new ArrayList<>())
                    .add(requestedCode);
            }

            Map<String, Object> engineParams = new HashMap<>();
            if (parameters != null) {
                engineParams.putAll(parameters);
            }
            engineParams.putIfAbsent("horizon", effHorizon.getCode());
            engineParams.putIfAbsent("lookback_years", effHorizon.getYears());
            engineParams.putIfAbsent("min_observations", effHorizon.getDefaultThreshold());
            engineParams.putIfAbsent("start_date", effStartDate.toString());
            engineParams.putIfAbsent("end_date", asOfDate.toString());

            CalculationRequestDto requestDto = new CalculationRequestDto(
                requestId,
                String.valueOf(schemeOptionId),
                benchmarkId != null ? String.valueOf(benchmarkId) : null,
                asOfDate.toString(),
                knowledgeCutoffTime.toString(),
                methodologyTag,
                engineMetricCodes,
                navSeries,
                benchmarkSeries,
                riskFreeSeries,
                engineParams
            );

            CalculationResponseDto response = quantEngineClient.executeCalculation(requestDto);

            // 6. Persist metric_result records
            if (response.results() != null) {
                Set<String> persistedMetricKeys = new HashSet<>();
                for (MetricOutputItemDto item : response.results()) {
                    List<String> requestedCodes = engineToRequestedMetricCodes.getOrDefault(
                        item.metricCode(),
                        List.of(item.metricCode())
                    );
                    for (String metricCode : requestedCodes) {
                        String periodType;
                        if (item.periodType() != null && !item.periodType().isBlank()) {
                            if ("LATEST_SNAPSHOT".equalsIgnoreCase(item.periodType())) {
                                periodType = "LATEST_SNAPSHOT";
                            } else if (("3Y".equalsIgnoreCase(item.periodType()) || "1Y".equalsIgnoreCase(item.periodType()) || "PERIOD".equalsIgnoreCase(item.periodType()))
                                && effHorizon != AnalyticalHorizon.THREE_YEAR) {
                                periodType = effHorizon.getCode();
                            } else {
                                periodType = item.periodType();
                            }
                        } else {
                            periodType = effHorizon.getCode();
                        }

                        String metricKey = metricCode + "|" + periodType;
                        if (!persistedMetricKeys.add(metricKey)) {
                            continue;
                        }
                        MetricResult result = new MetricResult(
                            run,
                            metricCode,
                            periodType,
                            item.numericValue() != null ? item.numericValue().setScale(10, java.math.RoundingMode.HALF_UP) : null,
                            item.units() != null ? item.units() : "UNKNOWN",
                            item.status() != null ? item.status() : "CALCULATED"
                        );
                        result.setStringValue(item.stringValue());
                        result.setErrorMessage(item.errorMessage());

                        Map<String, Object> diags = new HashMap<>();
                        if (item.diagnostics() != null) {
                            diags.putAll(item.diagnostics());
                        }
                        diags.putIfAbsent("requested_horizon", effHorizon.getCode());
                        diags.putIfAbsent("effective_start_date", effStartDate.toString());
                        diags.putIfAbsent("as_of_date", asOfDate.toString());
                        diags.putIfAbsent("min_observations_threshold", effHorizon.getDefaultThreshold());
                        result.setDiagnostics(objectMapper.writeValueAsString(diags));

                        metricResultRepository.save(result);
                    }
                }
            }

            run.setRunStatus(response.status() != null ? response.status() : "COMPLETED");
            run.setEngineSoftwareVersion(response.gitCommitHash() != null ? response.gitCommitHash() : "0.1.0-alpha");
            run.setExecutionCompletedAt(OffsetDateTime.now());
            run.setErrorMessage(response.errorMessage());

        } catch (Exception e) {
            log.warn("Quant Engine HTTP service unavailable ({}); refusing non-authoritative Java fallback scoring", e.getMessage());
            run.setRunStatus("FAILED");
            run.setEngineSoftwareVersion("PYTHON-QUANT-UNAVAILABLE");
            run.setExecutionCompletedAt(OffsetDateTime.now());
            run.setErrorMessage("Authoritative Python quant engine unavailable; Java offline fallback is not parity-certified for score-producing runs.");
        }

        return calculationRunRepository.save(run);
    }

    private String computeSnapshotHash(
        List<ObservationItemDto> navSeries,
        List<ObservationItemDto> benchSeries,
        List<ObservationItemDto> riskFreeSeries
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (ObservationItemDto obs : navSeries) {
                sb.append(obs.effectiveDate()).append(":").append(obs.value()).append(";");
            }
            sb.append("|");
            for (ObservationItemDto obs : benchSeries) {
                sb.append(obs.effectiveDate()).append(":").append(obs.value()).append(";");
            }
            sb.append("|");
            if (riskFreeSeries != null) {
                for (ObservationItemDto obs : riskFreeSeries) {
                    sb.append(obs.effectiveDate()).append(":").append(obs.value()).append(";");
                }
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
