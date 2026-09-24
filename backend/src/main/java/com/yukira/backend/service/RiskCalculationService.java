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
 * Implements deterministic execution of Risk & Tail metrics (Phase 2L: RSK-01 3-Year Annualized Volatility).
 * Adheres strictly to the YUKIRA governance mandate:
 *   - Pure Python quant-engine is the sole calculation authority
 *   - Zero Java mathematical fallback
 *   - Strict PIT resolution (effective_date <= analysis_cutoff AND availability_time <= knowledge_cutoff)
 *   - Immutable input observation lineage and cryptographic snapshot hashing
 *   - Explicit Candidate / Unvalidated convention disclosure
 */
@Service
public class RiskCalculationService {

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

    /**
     * Executes deterministic RSK-01 (3-Year Annualized Volatility) calculation run.
     *
     * @param schemeOptionId Database ID of the SchemeOption
     * @param requestedEndDate Analysis cutoff date
     * @param knowledgeCutoffTime Strict information availability cutoff timestamp
     * @param methodologyTag Version tag (e.g. "CANDIDATE_V1")
     * @return Persisted CalculationRun entity with linked MetricResult and input observations
     */
    @Transactional
    public CalculationRun executeRsk01Calculation(
        Long schemeOptionId,
        LocalDate requestedEndDate,
        OffsetDateTime knowledgeCutoffTime,
        String methodologyTag
    ) {
        Objects.requireNonNull(schemeOptionId, "schemeOptionId must not be null");
        Objects.requireNonNull(requestedEndDate, "requestedEndDate must not be null");
        Objects.requireNonNull(knowledgeCutoffTime, "knowledgeCutoffTime must not be null for PIT compliance");

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
            .findByMethodologyCodeAndVersionTag("RSK_01_3Y_VOLATILITY", tag)
            .orElseGet(() -> methodologyGovernanceService.registerMethodologyVersion(new MethodologyVersion(
                "RSK_01_3Y_VOLATILITY", tag, "CANDIDATE", "pending_commit"
            ), "RISK_CALCULATION_SERVICE"));

        if (!methodologyVersion.isLocked()) {
            methodologyVersion = methodologyGovernanceService.lockVersion(
                methodologyVersion.getId(), "RISK_CALCULATION_SERVICE"
            );
        }

        // Initialize CalculationRun (RSK-01 is a single-asset metric, benchmark is null)
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

        int minObservationsRequired = 700;

        // 2. Minimum observation history check (Phase 2H rule: < 700 trading days returns insufficient data)
        if (resolvedObservations.size() < minObservationsRequired) {
            String errorMsg = String.format(
                "Insufficient observation history for RSK-01: %d valid trading days available between %s and %s, minimum %d required.",
                resolvedObservations.size(), requestedStartDate, requestedEndDate, minObservationsRequired
            );
            run.setRunStatus("FAILED");
            run.setExecutionCompletedAt(OffsetDateTime.now());
            run.setErrorMessage(errorMsg);

            MetricResult metricResult = new MetricResult(
                run, "RSK-01", "3Y", null, "PERCENTAGE", "INSUFFICIENT_DATA"
            );
            metricResult.setErrorMessage(errorMsg);

            Map<String, Object> failureDiag = new HashMap<>();
            failureDiag.put("methodology_status", "CANDIDATE");
            failureDiag.put("methodology_tag", tag);
            failureDiag.put("benchmark_required", false);
            failureDiag.put("annualization_convention", "SQRT_252_CANDIDATE");
            failureDiag.put("denominator_convention", "N_MINUS_ONE_CANDIDATE");
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
            CalculationRequestDto requestDto = new CalculationRequestDto(
                "RSK01-" + UUID.randomUUID().toString().substring(0, 8),
                String.valueOf(schemeOptionId),
                null, // benchmark not required for RSK-01
                requestedEndDate.toString(),
                knowledgeCutoffTime.toString(),
                tag,
                List.of("RSK-01"),
                navSeries,
                Collections.emptyList(),
                Map.of(
                    "periods_per_year", 252.0,
                    "min_observations", minObservationsRequired,
                    "methodology_status", "CANDIDATE"
                )
            );

            CalculationResponseDto response = quantEngineClient.executeCalculation(requestDto);

            if (response.results() != null && !response.results().isEmpty()) {
                MetricOutputItemDto item = response.results().get(0);
                MetricResult metricResult = new MetricResult(
                    run,
                    "RSK-01",
                    "3Y",
                    item.numericValue(),
                    "PERCENTAGE",
                    item.status() != null ? item.status() : "CALCULATED"
                );

                Map<String, Object> diagnostics = new HashMap<>();
                diagnostics.put("methodology_status", "CANDIDATE");
                diagnostics.put("methodology_tag", tag);
                diagnostics.put("benchmark_required", false);
                diagnostics.put("annualization_convention", "SQRT_252_CANDIDATE");
                diagnostics.put("denominator_convention", "N_MINUS_ONE_CANDIDATE");
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
                run, "RSK-01", "3Y", null, "PERCENTAGE", "FAILED"
            );
            metricResult.setErrorMessage(errorMsg);

            Map<String, Object> failureDiag = new HashMap<>();
            failureDiag.put("methodology_status", "CANDIDATE");
            failureDiag.put("methodology_tag", tag);
            failureDiag.put("benchmark_required", false);
            failureDiag.put("annualization_convention", "SQRT_252_CANDIDATE");
            failureDiag.put("denominator_convention", "N_MINUS_ONE_CANDIDATE");
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
