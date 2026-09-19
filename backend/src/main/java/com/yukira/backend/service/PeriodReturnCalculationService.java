package com.yukira.backend.service;

import com.yukira.backend.client.dto.CalculationDtos.CalculationRequestDto;
import com.yukira.backend.client.dto.CalculationDtos.CalculationResponseDto;
import com.yukira.backend.client.dto.CalculationDtos.MetricOutputItemDto;
import com.yukira.backend.client.dto.CalculationDtos.ObservationItemDto;
import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class PeriodReturnCalculationService {

    private final PitObservationResolutionService pitResolutionService;
    private final SchemeOptionRepository schemeOptionRepository;
    private final MethodologyVersionRepository methodologyVersionRepository;
    private final CalculationRunRepository calculationRunRepository;
    private final MetricResultRepository metricResultRepository;
    private final CalculationRunInputObservationRepository calculationRunInputObservationRepository;
    private final com.yukira.backend.client.QuantEngineClient quantEngineClient;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public PeriodReturnCalculationService(
        PitObservationResolutionService pitResolutionService,
        SchemeOptionRepository schemeOptionRepository,
        MethodologyVersionRepository methodologyVersionRepository,
        CalculationRunRepository calculationRunRepository,
        MetricResultRepository metricResultRepository,
        CalculationRunInputObservationRepository calculationRunInputObservationRepository,
        com.yukira.backend.client.QuantEngineClient quantEngineClient
    ) {
        this.pitResolutionService = pitResolutionService;
        this.schemeOptionRepository = schemeOptionRepository;
        this.methodologyVersionRepository = methodologyVersionRepository;
        this.calculationRunRepository = calculationRunRepository;
        this.metricResultRepository = metricResultRepository;
        this.calculationRunInputObservationRepository = calculationRunInputObservationRepository;
        this.quantEngineClient = quantEngineClient;
    }

    public record PeriodSelectionResult(
        Optional<NavObservation> selectedObservation,
        LocalDate requestedDate,
        LocalDate selectedDate,
        int lookbackDaysUsed,
        boolean requiresSubstitution,
        String failureReason
    ) {}

    /**
     * Selects authoritative boundary observation within candidate 4-calendar-day lookback window.
     * The 4-day lookback window is explicitly classified as CANDIDATE methodology.
     */
    public PeriodSelectionResult selectBoundaryObservation(
        Long schemeOptionId,
        LocalDate targetDate,
        OffsetDateTime knowledgeCutoffTime,
        int maxLookbackDays
    ) {
        for (int offset = 0; offset <= maxLookbackDays; offset++) {
            LocalDate candidateDate = targetDate.minusDays(offset);
            PitObservationResolutionService.PitResolutionResult res = pitResolutionService
                .resolveAuthoritativeObservation(schemeOptionId, candidateDate, knowledgeCutoffTime);

            if (res.authoritativeObservation().isPresent()) {
                NavObservation obs = res.authoritativeObservation().get();
                return new PeriodSelectionResult(
                    Optional.of(obs),
                    targetDate,
                    candidateDate,
                    offset,
                    offset > 0,
                    null
                );
            } else if (res.isAmbiguous()) {
                return new PeriodSelectionResult(
                    Optional.empty(),
                    targetDate,
                    null,
                    offset,
                    false,
                    "Ambiguous PIT authority detected: " + res.diagnosticReason()
                );
            }
        }

        return new PeriodSelectionResult(
            Optional.empty(),
            targetDate,
            null,
            maxLookbackDays,
            false,
            String.format("No valid observation available within candidate %d-day lookback window preceding %s at cutoff %s",
                maxLookbackDays, targetDate, knowledgeCutoffTime)
        );
    }

    /**
     * Executes deterministic RET-02 Simple Period Return calculation run.
     * Captures full provenance, input references, cryptographic snapshot hash, and candidate methodology status.
     */
    @Transactional
    public CalculationRun executeRet02Calculation(
        Long schemeOptionId,
        LocalDate requestedStartDate,
        LocalDate requestedEndDate,
        OffsetDateTime knowledgeCutoffTime,
        String methodologyTag // e.g. "CANDIDATE_V1"
    ) {
        SchemeOption option = schemeOptionRepository.findById(schemeOptionId)
            .orElseThrow(() -> new IllegalArgumentException("SchemeOption not found: " + schemeOptionId));

        MethodologyVersion methodologyVersion = methodologyVersionRepository
            .findByMethodologyCodeAndVersionTag("RET_02_SIMPLE_RETURN", methodologyTag)
            .orElseGet(() -> methodologyVersionRepository.save(new MethodologyVersion(
                "RET_02_SIMPLE_RETURN", methodologyTag, "CANDIDATE", "92c5f257fa55cfdbce724d2712d7f87a8b66da91"
            )));

        // 1. Boundary observation resolution using candidate 4-calendar-day window
        PeriodSelectionResult startSel = selectBoundaryObservation(schemeOptionId, requestedStartDate, knowledgeCutoffTime, 4);
        PeriodSelectionResult endSel = selectBoundaryObservation(schemeOptionId, requestedEndDate, knowledgeCutoffTime, 4);

        // RET-02 is a standalone single-asset metric and does NOT mathematically require a benchmark.
        // benchmark is null, cleanly distinguishing "benchmark not required" from "benchmark exists and was used".
        CalculationRun run = new CalculationRun(
            option, requestedEndDate, knowledgeCutoffTime, methodologyVersion, "FASTAPI-QUANT-0.1.0"
        );
        run.setRunStatus("RUNNING");
        run = calculationRunRepository.save(run);

        // Check if either boundary failed
        if (startSel.selectedObservation().isEmpty() || endSel.selectedObservation().isEmpty()) {
            String errorMsg = String.format("Insufficient evidence for RET-02: start=[%s], end=[%s]",
                startSel.failureReason() != null ? startSel.failureReason() : "OK",
                endSel.failureReason() != null ? endSel.failureReason() : "OK"
            );
            run.setRunStatus("FAILED");
            run.setExecutionCompletedAt(OffsetDateTime.now());
            run.setErrorMessage(errorMsg);

            MetricResult metricResult = new MetricResult(
                run, "RET-02", "PERIOD", null, "PERCENTAGE", "INSUFFICIENT_DATA"
            );
            metricResult.setErrorMessage(errorMsg);

            Map<String, Object> failureDiag = new HashMap<>();
            failureDiag.put("methodology_status", "CANDIDATE");
            failureDiag.put("methodology_tag", methodologyTag);
            failureDiag.put("benchmark_required", false);
            failureDiag.put("factual_source_availability", "UNKNOWN_AMFI_HISTORICAL_BATCH_SOURCE_LIMITATION");
            failureDiag.put("analytical_availability_convention", "CONVENTION_EOD_HISTORICAL_CUTOFF");
            failureDiag.put("temporal_limitation_disclosure", "Factual AMFI source availability time is unrecorded upstream. Analytical cutoff convention applied.");
            failureDiag.put("requested_start_date", requestedStartDate.toString());
            failureDiag.put("requested_end_date", requestedEndDate.toString());
            failureDiag.put("knowledge_cutoff_time", knowledgeCutoffTime.toString());
            failureDiag.put("failure_reason", errorMsg);
            failureDiag.put("insufficient_evidence", true);
            try {
                metricResult.setDiagnostics(objectMapper.writeValueAsString(failureDiag));
            } catch (Exception ignored) {}

            metricResultRepository.save(metricResult);

            return calculationRunRepository.save(run);
        }

        NavObservation startObs = startSel.selectedObservation().get();
        NavObservation endObs = endSel.selectedObservation().get();

        // Sequence invariant: end observation date must be strictly after start observation date
        if (!endObs.getEffectiveDate().isAfter(startObs.getEffectiveDate())) {
            String errorMsg = String.format("Sequence invariant violated: selected end date %s must be strictly after start date %s",
                endObs.getEffectiveDate(), startObs.getEffectiveDate());
            run.setRunStatus("FAILED");
            run.setExecutionCompletedAt(OffsetDateTime.now());
            run.setErrorMessage(errorMsg);

            MetricResult metricResult = new MetricResult(
                run, "RET-02", "PERIOD", null, "PERCENTAGE", "INSUFFICIENT_DATA"
            );
            metricResult.setErrorMessage(errorMsg);

            Map<String, Object> failureDiag = new HashMap<>();
            failureDiag.put("methodology_status", "CANDIDATE");
            failureDiag.put("methodology_tag", methodologyTag);
            failureDiag.put("benchmark_required", false);
            failureDiag.put("factual_source_availability", "UNKNOWN_AMFI_HISTORICAL_BATCH_SOURCE_LIMITATION");
            failureDiag.put("analytical_availability_convention", "CONVENTION_EOD_HISTORICAL_CUTOFF");
            failureDiag.put("temporal_limitation_disclosure", "Factual AMFI source availability time is unrecorded upstream. Analytical cutoff convention applied.");
            failureDiag.put("requested_start_date", requestedStartDate.toString());
            failureDiag.put("requested_end_date", requestedEndDate.toString());
            failureDiag.put("selected_start_date", startObs.getEffectiveDate().toString());
            failureDiag.put("selected_end_date", endObs.getEffectiveDate().toString());
            failureDiag.put("knowledge_cutoff_time", knowledgeCutoffTime.toString());
            failureDiag.put("failure_reason", errorMsg);
            failureDiag.put("insufficient_evidence", true);
            try {
                metricResult.setDiagnostics(objectMapper.writeValueAsString(failureDiag));
            } catch (Exception ignored) {}

            metricResultRepository.save(metricResult);

            return calculationRunRepository.save(run);
        }

        // 2. Persist immutable input observation linkages
        calculationRunInputObservationRepository.save(new CalculationRunInputObservation(
            run, startObs, startObs.getEffectiveDate(), startObs.getRevisionSeq()
        ));
        calculationRunInputObservationRepository.save(new CalculationRunInputObservation(
            run, endObs, endObs.getEffectiveDate(), endObs.getRevisionSeq()
        ));

        // 3. Build DTO input series and snapshot hash
        List<ObservationItemDto> navSeries = List.of(
            new ObservationItemDto(startObs.getEffectiveDate().toString(), startObs.getNavValue().doubleValue(),
                startObs.getAvailabilityTime().toString(), startObs.getRevisionSeq()),
            new ObservationItemDto(endObs.getEffectiveDate().toString(), endObs.getNavValue().doubleValue(),
                endObs.getAvailabilityTime().toString(), endObs.getRevisionSeq())
        );
        String inputSnapshotHash = computeSnapshotHash(navSeries);
        run.setInputSnapshotSha256(inputSnapshotHash);

        // 4. Dispatch deterministic calculation to Quant Engine
        try {
            CalculationRequestDto requestDto = new CalculationRequestDto(
                "RET02-" + UUID.randomUUID().toString().substring(0, 8),
                String.valueOf(schemeOptionId),
                null, // benchmark not required for RET-02
                requestedEndDate.toString(),
                knowledgeCutoffTime.toString(),
                methodologyTag,
                List.of("RET-02"),
                navSeries,
                Collections.emptyList(),
                Map.of("methodology_status", "CANDIDATE")
            );

            CalculationResponseDto response = quantEngineClient.executeCalculation(requestDto);

            if (response.results() != null && !response.results().isEmpty()) {
                MetricOutputItemDto item = response.results().get(0);
                MetricResult metricResult = new MetricResult(
                    run,
                    "RET-02",
                    "PERIOD",
                    item.numericValue(),
                    "PERCENTAGE",
                    item.status() != null ? item.status() : "CALCULATED"
                );

                Map<String, Object> diagnostics = new HashMap<>();
                diagnostics.put("methodology_status", "CANDIDATE");
                diagnostics.put("methodology_tag", methodologyTag);
                diagnostics.put("benchmark_required", false);
                diagnostics.put("factual_source_availability", "UNKNOWN_AMFI_HISTORICAL_BATCH_SOURCE_LIMITATION");
                diagnostics.put("analytical_availability_convention", "CONVENTION_EOD_HISTORICAL_CUTOFF");
                diagnostics.put("temporal_limitation_disclosure", "Factual AMFI source availability time is unrecorded upstream. Analytical cutoff convention applied.");
                diagnostics.put("requested_start_date", requestedStartDate.toString());
                diagnostics.put("requested_end_date", requestedEndDate.toString());
                diagnostics.put("selected_start_date", startObs.getEffectiveDate().toString());
                diagnostics.put("selected_end_date", endObs.getEffectiveDate().toString());
                diagnostics.put("start_nav", startObs.getNavValue());
                diagnostics.put("end_nav", endObs.getNavValue());
                diagnostics.put("start_lookback_days_used", startSel.lookbackDaysUsed());
                diagnostics.put("end_lookback_days_used", endSel.lookbackDaysUsed());
                diagnostics.put("knowledge_cutoff_time", knowledgeCutoffTime.toString());

                metricResult.setDiagnostics(objectMapper.writeValueAsString(diagnostics));
                metricResultRepository.save(metricResult);
            }

            run.setRunStatus("COMPLETED");
            run.setExecutionCompletedAt(OffsetDateTime.now());

        } catch (Exception e) {
            // Local fallback deterministic calculation if Quant Engine HTTP is offline
            BigDecimal startVal = startObs.getNavValue();
            BigDecimal endVal = endObs.getNavValue();
            BigDecimal periodReturn = endVal.subtract(startVal)
                .divide(startVal, 10, java.math.RoundingMode.HALF_UP);

            MetricResult metricResult = new MetricResult(
                run, "RET-02", "PERIOD", periodReturn, "PERCENTAGE", "CALCULATED"
            );

            Map<String, Object> diagnostics = new HashMap<>();
            diagnostics.put("methodology_status", "CANDIDATE");
            diagnostics.put("methodology_tag", methodologyTag);
            diagnostics.put("benchmark_required", false);
            diagnostics.put("factual_source_availability", "UNKNOWN_AMFI_HISTORICAL_BATCH_SOURCE_LIMITATION");
            diagnostics.put("analytical_availability_convention", "CONVENTION_EOD_HISTORICAL_CUTOFF");
            diagnostics.put("temporal_limitation_disclosure", "Factual AMFI source availability time is unrecorded upstream. Analytical cutoff convention applied.");
            diagnostics.put("requested_start_date", requestedStartDate.toString());
            diagnostics.put("requested_end_date", requestedEndDate.toString());
            diagnostics.put("selected_start_date", startObs.getEffectiveDate().toString());
            diagnostics.put("selected_end_date", endObs.getEffectiveDate().toString());
            diagnostics.put("start_nav", startObs.getNavValue());
            diagnostics.put("end_nav", endObs.getNavValue());
            diagnostics.put("start_lookback_days_used", startSel.lookbackDaysUsed());
            diagnostics.put("end_lookback_days_used", endSel.lookbackDaysUsed());
            diagnostics.put("knowledge_cutoff_time", knowledgeCutoffTime.toString());
            diagnostics.put("local_fallback", true);

            try {
                metricResult.setDiagnostics(objectMapper.writeValueAsString(diagnostics));
            } catch (Exception ignored) {}

            metricResultRepository.save(metricResult);
            run.setRunStatus("COMPLETED");
            run.setExecutionCompletedAt(OffsetDateTime.now());
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
