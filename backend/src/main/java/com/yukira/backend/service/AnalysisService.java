package com.yukira.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.analysis.Ret02AnalysisResponse;
import com.yukira.backend.dto.analysis.Ret02CalculationRequest;
import com.yukira.backend.dto.analysis.Ret03AnalysisResponse;
import com.yukira.backend.dto.analysis.Ret03CalculationRequest;
import com.yukira.backend.dto.analysis.Rsk01AnalysisResponse;
import com.yukira.backend.dto.analysis.Rsk01CalculationRequest;
import com.yukira.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class AnalysisService {

    private final PeriodReturnCalculationService periodReturnCalculationService;
    private final RiskCalculationService riskCalculationService;
    private final CalculationRunRepository calculationRunRepository;
    private final MetricResultRepository metricResultRepository;
    private final CalculationRunInputObservationRepository calculationRunInputObservationRepository;
    private final SchemeOptionRepository schemeOptionRepository;
    private final SchemePlanRepository schemePlanRepository;
    private final SchemeRepository schemeRepository;
    private final ValidationIssueRepository validationIssueRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnalysisService(
        PeriodReturnCalculationService periodReturnCalculationService,
        RiskCalculationService riskCalculationService,
        CalculationRunRepository calculationRunRepository,
        MetricResultRepository metricResultRepository,
        CalculationRunInputObservationRepository calculationRunInputObservationRepository,
        SchemeOptionRepository schemeOptionRepository,
        SchemePlanRepository schemePlanRepository,
        SchemeRepository schemeRepository,
        ValidationIssueRepository validationIssueRepository
    ) {
        this.periodReturnCalculationService = periodReturnCalculationService;
        this.riskCalculationService = riskCalculationService;
        this.calculationRunRepository = calculationRunRepository;
        this.metricResultRepository = metricResultRepository;
        this.calculationRunInputObservationRepository = calculationRunInputObservationRepository;
        this.schemeOptionRepository = schemeOptionRepository;
        this.schemePlanRepository = schemePlanRepository;
        this.schemeRepository = schemeRepository;
        this.validationIssueRepository = validationIssueRepository;
    }

    @Transactional
    public Ret02AnalysisResponse executeRet02Analysis(Ret02CalculationRequest request) {
        String tag = request.methodologyTag() != null && !request.methodologyTag().isBlank()
            ? request.methodologyTag() : "CANDIDATE_V1";

        CalculationRun run = periodReturnCalculationService.executeRet02Calculation(
            request.schemeOptionId(),
            request.startDate(),
            request.endDate(),
            request.knowledgeCutoffTime(),
            tag
        );

        return buildRet02Response(run);
    }

    @Transactional
    public Rsk01AnalysisResponse executeRsk01Analysis(Rsk01CalculationRequest request) {
        Objects.requireNonNull(request.schemeOptionId(), "schemeOptionId must not be null");
        Objects.requireNonNull(request.endDate(), "endDate must not be null");
        Objects.requireNonNull(request.knowledgeCutoffTime(), "knowledgeCutoffTime must not be null for PIT compliance");

        String tag = request.methodologyTag() != null && !request.methodologyTag().isBlank()
            ? request.methodologyTag() : "CANDIDATE_V1";

        CalculationRun run = riskCalculationService.executeRsk01Calculation(
            request.schemeOptionId(),
            request.endDate(),
            request.knowledgeCutoffTime(),
            tag
        );

        return buildRsk01Response(run);
    }

    @Transactional(readOnly = true)
    public Optional<Object> getAnalysisByRunId(Long runId) {
        return calculationRunRepository.findById(runId).map(run -> {
            String mCode = run.getMethodologyVersion() != null ? run.getMethodologyVersion().getMethodologyCode() : "";
            if (mCode.contains("RSK_01") || !metricResultRepository.findByCalculationRunIdAndMetricCode(run.getId(), "RSK-01").isEmpty()) {
                return buildRsk01Response(run);
            }
            if (mCode.contains("RET_03") || !metricResultRepository.findByCalculationRunIdAndMetricCode(run.getId(), "RET-03").isEmpty()) {
                return buildRet03Response(run);
            }
            return buildRet02Response(run);
        });
    }

    @Transactional(readOnly = true)
    public Optional<Ret02AnalysisResponse> getRet02AnalysisByRunId(Long runId) {
        return calculationRunRepository.findById(runId).map(this::buildRet02Response);
    }

    @Transactional(readOnly = true)
    public Optional<Rsk01AnalysisResponse> getRsk01AnalysisByRunId(Long runId) {
        return calculationRunRepository.findById(runId).map(this::buildRsk01Response);
    }

    @Transactional(readOnly = true)
    public Ret02AnalysisResponse buildRet02Response(CalculationRun run) {
        // 1. Identity - resolve robustly without lazy initialization failure on detached entities
        SchemeOption option = run.getSchemeOption();
        if (option != null && option.getId() != null) {
            option = schemeOptionRepository.findById(option.getId()).orElse(option);
        }
        SchemePlan plan = null;
        if (option != null) {
            try {
                plan = option.getPlan();
                if (plan != null && plan.getId() != null) {
                    plan = schemePlanRepository.findById(plan.getId()).orElse(plan);
                }
            } catch (Exception ignored) {
                plan = null;
            }
        }
        Scheme scheme = null;
        if (plan != null) {
            try {
                scheme = plan.getScheme();
                if (scheme != null && scheme.getId() != null) {
                    scheme = schemeRepository.findById(scheme.getId()).orElse(scheme);
                }
            } catch (Exception ignored) {
                scheme = null;
            }
        }

        Ret02AnalysisResponse.IdentityInfo identity = new Ret02AnalysisResponse.IdentityInfo(
            scheme != null ? scheme.getId() : null,
            scheme != null ? scheme.getName() : "Unknown Scheme",
            option != null ? option.getAmfiCode() : (scheme != null ? scheme.getCode() : null),
            option != null ? option.getId() : null,
            option != null ? option.getOptionType() : "Unknown Option",
            option != null ? option.getIsin() : null
        );

        // 2. Metric Result & Diagnostics
        List<MetricResult> results = metricResultRepository.findByCalculationRunIdAndMetricCode(run.getId(), "RET-02");
        MetricResult metricResult = results.isEmpty() ? null : results.get(0);

        Map<String, Object> diagnostics = Collections.emptyMap();
        if (metricResult != null && metricResult.getDiagnostics() != null) {
            try {
                diagnostics = objectMapper.readValue(metricResult.getDiagnostics(), new TypeReference<>() {});
            } catch (Exception ignored) {}
        }

        BigDecimal numericValue = metricResult != null ? metricResult.getNumericValue() : null;
        String calculationStatus = metricResult != null ? metricResult.getCalculationStatus()
            : ("FAILED".equals(run.getRunStatus()) ? "INSUFFICIENT_DATA" : run.getRunStatus());
        String formattedValue = numericValue != null
            ? String.format("%+.4f%%", numericValue.multiply(new BigDecimal("100")))
            : null;
        String errorMessage = metricResult != null && metricResult.getErrorMessage() != null
            ? metricResult.getErrorMessage() : run.getErrorMessage();

        Ret02AnalysisResponse.ResultInfo result = new Ret02AnalysisResponse.ResultInfo(
            "RET-02",
            "Simple Period Return",
            numericValue,
            formattedValue,
            "PERCENTAGE",
            calculationStatus,
            errorMessage
        );

        // 3. Period
        LocalDate reqStart = diagnostics.containsKey("requested_start_date")
            ? LocalDate.parse((String) diagnostics.get("requested_start_date"))
            : null;
        LocalDate reqEnd = diagnostics.containsKey("requested_end_date")
            ? LocalDate.parse((String) diagnostics.get("requested_end_date"))
            : run.getAsOfDate();

        LocalDate selStart = diagnostics.containsKey("selected_start_date")
            ? LocalDate.parse((String) diagnostics.get("selected_start_date"))
            : null;
        LocalDate selEnd = diagnostics.containsKey("selected_end_date")
            ? LocalDate.parse((String) diagnostics.get("selected_end_date"))
            : null;

        Integer startLookback = diagnostics.containsKey("start_lookback_days_used")
            ? ((Number) diagnostics.get("start_lookback_days_used")).intValue()
            : 0;
        Integer endLookback = diagnostics.containsKey("end_lookback_days_used")
            ? ((Number) diagnostics.get("end_lookback_days_used")).intValue()
            : 0;

        Ret02AnalysisResponse.PeriodInfo period = new Ret02AnalysisResponse.PeriodInfo(
            reqStart,
            reqEnd,
            selStart,
            selEnd,
            startLookback,
            endLookback,
            startLookback != null && startLookback > 0,
            endLookback != null && endLookback > 0
        );

        // 4. PIT
        Ret02AnalysisResponse.PitInfo pit = new Ret02AnalysisResponse.PitInfo(
            run.getKnowledgeCutoffTime(),
            true,
            "Factual AMFI source availability timestamp is unrecorded upstream. Analytical EOD cutoff convention applied.",
            "CONVENTION_EOD_HISTORICAL_CUTOFF",
            "HISTORICAL_BACKFILL"
        );

        // 5. Methodology
        String mCode = run.getMethodologyVersion() != null ? run.getMethodologyVersion().getMethodologyCode() : "RET_02_SIMPLE_RETURN";
        String mVer = run.getMethodologyVersion() != null ? run.getMethodologyVersion().getVersionTag() : "CANDIDATE_V1";
        String mStatus = run.getMethodologyVersion() != null ? run.getMethodologyVersion().getApprovalStatus() : "CANDIDATE";

        Ret02AnalysisResponse.MethodologyInfo methodology = new Ret02AnalysisResponse.MethodologyInfo(
            mCode,
            mVer,
            mStatus,
            true, // Always candidate per epistemic governance
            "Candidate 4-calendar-day lookback window preceding requested boundary date",
            "Discrete return: (NAV_end - NAV_start) / NAV_start"
        );

        // 6. Input Observations & Provenance
        List<CalculationRunInputObservation> inputObsLinks = calculationRunInputObservationRepository.findByCalculationRunId(run.getId());
        inputObsLinks.sort(Comparator.comparing(CalculationRunInputObservation::getEffectiveDate));

        List<Ret02AnalysisResponse.InputObservationRef> inputObservationRefs = new ArrayList<>();
        Map<Long, Ret02AnalysisResponse.SourceArtifactSummary> artifactMap = new HashMap<>();

        String primaryQuality = "VALID";
        String primaryVerification = "VERIFIED";
        String primaryRevision = "ORIGINAL";
        String primaryFreshness = "CURRENT";
        String primaryPresence = "AVAILABLE";
        String primaryIntegrity = "NONE";

        for (int i = 0; i < inputObsLinks.size(); i++) {
            CalculationRunInputObservation link = inputObsLinks.get(i);
            NavObservation obs = link.getNavObservation();
            if (obs == null) continue;

            String role = (i == 0 && inputObsLinks.size() > 1) ? "START" : (i == inputObsLinks.size() - 1 ? "END" : "INTERMEDIATE");
            SourceArtifact artifact = obs.getSourceArtifact();

            if ("SUSPICIOUS".equals(obs.getQualityAssessment())) primaryQuality = "SUSPICIOUS";
            if ("INVALID".equals(obs.getQualityAssessment())) primaryQuality = "INVALID";
            if ("UNVERIFIED".equals(obs.getVerificationStatus())) primaryVerification = "UNVERIFIED";
            if ("REVISED".equals(obs.getRevisionStatus())) primaryRevision = "REVISED";
            if ("STALE".equals(obs.getTemporalStatus())) primaryFreshness = "STALE";

            inputObservationRefs.add(new Ret02AnalysisResponse.InputObservationRef(
                obs.getId(),
                role,
                obs.getEffectiveDate(),
                obs.getRevisionSeq(),
                obs.getNavValue(),
                obs.getAvailabilityTime(),
                obs.getQualityAssessment(),
                obs.getVerificationStatus(),
                obs.getRevisionStatus(),
                obs.getTemporalStatus() != null ? obs.getTemporalStatus() : "CURRENT",
                obs.getPresenceStatus(),
                "NONE",
                "HISTORICAL_BACKFILL",
                artifact != null ? artifact.getId() : null,
                artifact != null ? artifact.getSha256Hash() : null
            ));

            if (artifact != null && !artifactMap.containsKey(artifact.getId())) {
                String sourceUrl = artifact.getStorageUri() != null ? artifact.getStorageUri() : "https://www.amfiindia.com/net-asset-value/nav-history";
                artifactMap.put(artifact.getId(), new Ret02AnalysisResponse.SourceArtifactSummary(
                    artifact.getId(),
                    sourceUrl,
                    artifact.getSha256Hash(),
                    artifact.getRetrievalTimestamp(),
                    artifact.getByteSize()
                ));
            }
        }

        // If no input observations (e.g. failed before resolution)
        if (inputObsLinks.isEmpty()) {
            primaryPresence = "MISSING";
            primaryQuality = "INVALID";
        }

        List<String> validationFlags = new ArrayList<>();
        if (option != null) {
            List<ValidationIssue> issues = validationIssueRepository.findByTargetEntityTypeAndTargetEntityId("SCHEME_OPTION", option.getId());
            for (ValidationIssue issue : issues) {
                validationFlags.add(issue.getCheckCode() + ": " + issue.getMessage());
                if ("CONFLICTING".equalsIgnoreCase(issue.getIntegrityCondition())) {
                    primaryIntegrity = "CONFLICTING";
                } else if ("DUPLICATE".equalsIgnoreCase(issue.getIntegrityCondition()) && !"CONFLICTING".equals(primaryIntegrity)) {
                    primaryIntegrity = "DUPLICATE";
                }
            }
        }

        List<Ret02AnalysisResponse.QualityDimension> dimensions = List.of(
            new Ret02AnalysisResponse.QualityDimension("Quality", primaryQuality, "Conforms to schema and historical sanity thresholds."),
            new Ret02AnalysisResponse.QualityDimension("Verification", primaryVerification, "Observation reconciled against source artifact."),
            new Ret02AnalysisResponse.QualityDimension("Revision", primaryRevision, "Authoritative revision status within bitemporal ledger."),
            new Ret02AnalysisResponse.QualityDimension("Freshness", primaryFreshness, "Observation delivery timeliness against reporting schedule."),
            new Ret02AnalysisResponse.QualityDimension("Presence", primaryPresence, "Observation presence evaluated at PIT cutoff."),
            new Ret02AnalysisResponse.QualityDimension("Integrity", primaryIntegrity, "Bitemporal relationship condition (duplicate or conflicting detection).")
        );

        Ret02AnalysisResponse.QualityInfo quality = new Ret02AnalysisResponse.QualityInfo(
            primaryQuality,
            dimensions,
            validationFlags
        );

        String commitHash = run.getMethodologyVersion() != null ? run.getMethodologyVersion().getGitCommitHash() : null;
        Ret02AnalysisResponse.ProvenanceInfo provenance = new Ret02AnalysisResponse.ProvenanceInfo(
            run.getId(),
            run.getRunStatus(),
            run.getExecutionStartedAt(),
            run.getExecutionCompletedAt(),
            run.getEngineSoftwareVersion(),
            commitHash,
            run.getInputSnapshotSha256(),
            inputObservationRefs,
            new ArrayList<>(artifactMap.values())
        );

        // 7. Limitations
        boolean insufficient = "INSUFFICIENT_DATA".equals(calculationStatus) || "FAILED".equals(run.getRunStatus());
        Ret02AnalysisResponse.LimitationsInfo limitations = new Ret02AnalysisResponse.LimitationsInfo(
            true,
            "CONVENTION_EOD_HISTORICAL_CUTOFF",
            "HISTORICAL_BACKFILL",
            period.startSubstituted() || period.endSubstituted(),
            4,
            insufficient,
            "Factual AMFI availability timestamp is unavailable upstream. Analytical EOD cutoff convention applied. Candidate 4-calendar-day lookback window active. Zero investment recommendation."
        );

        // 8. Benchmark explicitly null & not required
        Ret02AnalysisResponse.BenchmarkInfo benchmark = new Ret02AnalysisResponse.BenchmarkInfo(
            false,
            null,
            "RET-02 is a standalone single-asset return metric. Benchmark is explicitly not required and no synthetic benchmark was used."
        );

        return new Ret02AnalysisResponse(
            identity,
            result,
            period,
            pit,
            methodology,
            quality,
            provenance,
            limitations,
            benchmark
        );
    }

    @Transactional
    public Ret03AnalysisResponse executeRet03Analysis(Ret03CalculationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.schemeOptionId(), "schemeOptionId must not be null");
        Objects.requireNonNull(request.endDate(), "endDate must not be null");
        Objects.requireNonNull(request.knowledgeCutoffTime(), "knowledgeCutoffTime must not be null for PIT compliance");

        String tag = request.methodologyTag() != null && !request.methodologyTag().isBlank()
            ? request.methodologyTag() : "CANDIDATE_V1";

        CalculationRun run = periodReturnCalculationService.executeRet03Calculation(
            request.schemeOptionId(),
            request.endDate(),
            request.knowledgeCutoffTime(),
            tag
        );

        return buildRet03Response(run);
    }

    @Transactional(readOnly = true)
    public Optional<Ret03AnalysisResponse> getRet03AnalysisByRunId(Long runId) {
        return calculationRunRepository.findById(runId).map(this::buildRet03Response);
    }

    @Transactional(readOnly = true)
    public Ret03AnalysisResponse buildRet03Response(CalculationRun run) {
        SchemeOption option = run.getSchemeOption();
        if (option != null && option.getId() != null) {
            option = schemeOptionRepository.findById(option.getId()).orElse(option);
        }
        SchemePlan plan = null;
        if (option != null) {
            try {
                plan = option.getPlan();
                if (plan != null && plan.getId() != null) {
                    plan = schemePlanRepository.findById(plan.getId()).orElse(plan);
                }
            } catch (Exception ignored) {
                plan = null;
            }
        }
        Scheme scheme = null;
        if (plan != null) {
            try {
                scheme = plan.getScheme();
                if (scheme != null && scheme.getId() != null) {
                    scheme = schemeRepository.findById(scheme.getId()).orElse(scheme);
                }
            } catch (Exception ignored) {
                scheme = null;
            }
        }

        Ret03AnalysisResponse.IdentityInfo identity = new Ret03AnalysisResponse.IdentityInfo(
            scheme != null ? scheme.getId() : null,
            scheme != null ? scheme.getName() : "Unknown Scheme",
            option != null ? option.getAmfiCode() : (scheme != null ? scheme.getCode() : null),
            option != null ? option.getId() : null,
            option != null ? option.getOptionType() : "Unknown Option",
            option != null ? option.getIsin() : null
        );

        List<MetricResult> results = metricResultRepository.findByCalculationRunIdAndMetricCode(run.getId(), "RET-03");
        MetricResult metricResult = results.isEmpty() ? null : results.get(0);

        Map<String, Object> diagnostics = Collections.emptyMap();
        if (metricResult != null && metricResult.getDiagnostics() != null) {
            try {
                diagnostics = objectMapper.readValue(metricResult.getDiagnostics(), new TypeReference<>() {});
            } catch (Exception ignored) {}
        }

        BigDecimal numericValue = metricResult != null ? metricResult.getNumericValue() : null;
        String calculationStatus = metricResult != null ? metricResult.getCalculationStatus()
            : ("FAILED".equals(run.getRunStatus()) ? "INSUFFICIENT_DATA" : run.getRunStatus());
        String formattedValue = numericValue != null
            ? String.format("%+.4f%%", numericValue.multiply(new BigDecimal("100")))
            : null;
        String errorMessage = metricResult != null && metricResult.getErrorMessage() != null
            ? metricResult.getErrorMessage() : run.getErrorMessage();

        Ret03AnalysisResponse.ResultInfo result = new Ret03AnalysisResponse.ResultInfo(
            "RET-03",
            "3-Year Compound Annual Growth Rate",
            numericValue,
            formattedValue,
            "PERCENTAGE",
            calculationStatus,
            errorMessage
        );

        LocalDate reqStart = diagnostics.containsKey("requested_start_date")
            ? LocalDate.parse((String) diagnostics.get("requested_start_date"))
            : null;
        LocalDate reqEnd = diagnostics.containsKey("requested_end_date")
            ? LocalDate.parse((String) diagnostics.get("requested_end_date"))
            : run.getAsOfDate();

        LocalDate selStart = diagnostics.containsKey("selected_start_date")
            ? LocalDate.parse((String) diagnostics.get("selected_start_date"))
            : null;
        LocalDate selEnd = diagnostics.containsKey("selected_end_date")
            ? LocalDate.parse((String) diagnostics.get("selected_end_date"))
            : null;

        Integer startLookback = diagnostics.containsKey("start_lookback_days_used")
            ? ((Number) diagnostics.get("start_lookback_days_used")).intValue()
            : 0;
        Integer endLookback = diagnostics.containsKey("end_lookback_days_used")
            ? ((Number) diagnostics.get("end_lookback_days_used")).intValue()
            : 0;

        Ret03AnalysisResponse.PeriodInfo period = new Ret03AnalysisResponse.PeriodInfo(
            reqStart,
            reqEnd,
            selStart,
            selEnd,
            startLookback,
            endLookback,
            startLookback != null && startLookback > 0,
            endLookback != null && endLookback > 0
        );

        Ret03AnalysisResponse.PitInfo pit = new Ret03AnalysisResponse.PitInfo(
            run.getKnowledgeCutoffTime(),
            true,
            "Factual AMFI source availability timestamp is unrecorded upstream. Analytical EOD cutoff convention applied.",
            "CONVENTION_EOD_HISTORICAL_CUTOFF",
            "HISTORICAL_BACKFILL"
        );

        String mCode = run.getMethodologyVersion() != null ? run.getMethodologyVersion().getMethodologyCode() : "RET_03_3Y_CAGR";
        String mVer = run.getMethodologyVersion() != null ? run.getMethodologyVersion().getVersionTag() : "CANDIDATE_V1";
        String mStatus = run.getMethodologyVersion() != null ? run.getMethodologyVersion().getApprovalStatus() : "CANDIDATE";

        Ret03AnalysisResponse.MethodologyInfo methodology = new Ret03AnalysisResponse.MethodologyInfo(
            mCode,
            mVer,
            mStatus,
            true,
            "36 calendar months candidate analytical window with 4-day boundary lookback",
            "Standard CAGR with fractional year compounding: (NAV_end / NAV_start)^(365.25 / elapsed_calendar_days) - 1"
        );

        List<CalculationRunInputObservation> inputObsLinks = calculationRunInputObservationRepository.findByCalculationRunId(run.getId());
        inputObsLinks.sort(Comparator.comparing(CalculationRunInputObservation::getEffectiveDate));

        List<Ret03AnalysisResponse.InputObservationRef> inputObservationRefs = new ArrayList<>();
        Map<Long, Ret03AnalysisResponse.SourceArtifactSummary> artifactMap = new HashMap<>();

        String primaryQuality = "VALID";
        String primaryVerification = "VERIFIED";
        String primaryRevision = "ORIGINAL";
        String primaryFreshness = "CURRENT";
        String primaryPresence = "AVAILABLE";
        String primaryIntegrity = "NONE";

        for (int i = 0; i < inputObsLinks.size(); i++) {
            CalculationRunInputObservation link = inputObsLinks.get(i);
            NavObservation obs = link.getNavObservation();
            if (obs == null) continue;

            String role = (i == 0 && inputObsLinks.size() > 1) ? "START" : (i == inputObsLinks.size() - 1 ? "END" : "INTERMEDIATE");
            SourceArtifact artifact = obs.getSourceArtifact();

            if ("SUSPICIOUS".equals(obs.getQualityAssessment())) primaryQuality = "SUSPICIOUS";
            if ("INVALID".equals(obs.getQualityAssessment())) primaryQuality = "INVALID";
            if ("UNVERIFIED".equals(obs.getVerificationStatus())) primaryVerification = "UNVERIFIED";
            if ("REVISED".equals(obs.getRevisionStatus())) primaryRevision = "REVISED";
            if ("STALE".equals(obs.getTemporalStatus())) primaryFreshness = "STALE";

            inputObservationRefs.add(new Ret03AnalysisResponse.InputObservationRef(
                obs.getId(),
                role,
                obs.getEffectiveDate(),
                obs.getRevisionSeq(),
                obs.getNavValue(),
                obs.getAvailabilityTime(),
                obs.getQualityAssessment(),
                obs.getVerificationStatus(),
                obs.getRevisionStatus(),
                obs.getTemporalStatus() != null ? obs.getTemporalStatus() : "CURRENT",
                obs.getPresenceStatus(),
                "NONE",
                "HISTORICAL_BACKFILL",
                artifact != null ? artifact.getId() : null,
                artifact != null ? artifact.getSha256Hash() : null
            ));

            if (artifact != null && !artifactMap.containsKey(artifact.getId())) {
                String sourceUrl = artifact.getStorageUri() != null ? artifact.getStorageUri() : "https://www.amfiindia.com/net-asset-value/nav-history";
                artifactMap.put(artifact.getId(), new Ret03AnalysisResponse.SourceArtifactSummary(
                    artifact.getId(),
                    sourceUrl,
                    artifact.getSha256Hash(),
                    artifact.getRetrievalTimestamp(),
                    artifact.getByteSize()
                ));
            }
        }

        if (inputObsLinks.isEmpty()) {
            primaryPresence = "MISSING";
            primaryQuality = "INVALID";
        }

        List<String> validationFlags = new ArrayList<>();
        if (option != null) {
            List<ValidationIssue> issues = validationIssueRepository.findByTargetEntityTypeAndTargetEntityId("SCHEME_OPTION", option.getId());
            for (ValidationIssue issue : issues) {
                validationFlags.add(issue.getCheckCode() + ": " + issue.getMessage());
                if ("CONFLICTING".equalsIgnoreCase(issue.getIntegrityCondition())) {
                    primaryIntegrity = "CONFLICTING";
                } else if ("DUPLICATE".equalsIgnoreCase(issue.getIntegrityCondition()) && !"CONFLICTING".equals(primaryIntegrity)) {
                    primaryIntegrity = "DUPLICATE";
                }
            }
        }

        List<Ret03AnalysisResponse.QualityDimension> dimensions = List.of(
            new Ret03AnalysisResponse.QualityDimension("Quality", primaryQuality, "Conforms to schema and historical sanity thresholds."),
            new Ret03AnalysisResponse.QualityDimension("Verification", primaryVerification, "Observation reconciled against source artifact."),
            new Ret03AnalysisResponse.QualityDimension("Revision", primaryRevision, "Authoritative revision status within bitemporal ledger."),
            new Ret03AnalysisResponse.QualityDimension("Freshness", primaryFreshness, "Observation delivery timeliness against reporting schedule."),
            new Ret03AnalysisResponse.QualityDimension("Presence", primaryPresence, "Observation presence evaluated at PIT cutoff."),
            new Ret03AnalysisResponse.QualityDimension("Integrity", primaryIntegrity, "Bitemporal relationship condition (duplicate or conflicting detection).")
        );

        Ret03AnalysisResponse.QualityInfo quality = new Ret03AnalysisResponse.QualityInfo(
            primaryQuality,
            dimensions,
            validationFlags
        );

        String commitHash = run.getMethodologyVersion() != null ? run.getMethodologyVersion().getGitCommitHash() : null;
        Ret03AnalysisResponse.ProvenanceInfo provenance = new Ret03AnalysisResponse.ProvenanceInfo(
            run.getId(),
            run.getRunStatus(),
            run.getExecutionStartedAt(),
            run.getExecutionCompletedAt(),
            run.getEngineSoftwareVersion(),
            commitHash,
            run.getInputSnapshotSha256(),
            inputObservationRefs,
            new ArrayList<>(artifactMap.values())
        );

        boolean insufficient = "INSUFFICIENT_DATA".equals(calculationStatus) || "FAILED".equals(run.getRunStatus());
        Ret03AnalysisResponse.LimitationsInfo limitations = new Ret03AnalysisResponse.LimitationsInfo(
            true,
            "CONVENTION_EOD_HISTORICAL_CUTOFF",
            "HISTORICAL_BACKFILL",
            period.startSubstituted() || period.endSubstituted(),
            4,
            insufficient,
            "Factual AMFI availability timestamp is unavailable upstream. Analytical EOD cutoff convention applied. Candidate 4-calendar-day lookback window active. Zero investment recommendation."
        );

        Ret03AnalysisResponse.BenchmarkInfo benchmark = new Ret03AnalysisResponse.BenchmarkInfo(
            false,
            null,
            "RET-03 is a standalone single-asset return metric. Benchmark is explicitly not required and no synthetic benchmark was used."
        );

        return new Ret03AnalysisResponse(
            identity,
            result,
            period,
            pit,
            methodology,
            quality,
            provenance,
            limitations,
            benchmark
        );
    }

    @Transactional(readOnly = true)
    public Rsk01AnalysisResponse buildRsk01Response(CalculationRun run) {
        SchemeOption option = run.getSchemeOption();
        if (option != null && option.getId() != null) {
            option = schemeOptionRepository.findById(option.getId()).orElse(option);
        }
        SchemePlan plan = null;
        if (option != null) {
            try {
                plan = option.getPlan();
                if (plan != null && plan.getId() != null) {
                    plan = schemePlanRepository.findById(plan.getId()).orElse(plan);
                }
            } catch (Exception ignored) {
                plan = null;
            }
        }
        Scheme scheme = null;
        if (plan != null) {
            try {
                scheme = plan.getScheme();
                if (scheme != null && scheme.getId() != null) {
                    scheme = schemeRepository.findById(scheme.getId()).orElse(scheme);
                }
            } catch (Exception ignored) {
                scheme = null;
            }
        }

        Rsk01AnalysisResponse.IdentityInfo identity = new Rsk01AnalysisResponse.IdentityInfo(
            scheme != null ? scheme.getId() : null,
            scheme != null ? scheme.getName() : "Unknown Scheme",
            option != null ? option.getAmfiCode() : (scheme != null ? scheme.getCode() : null),
            option != null ? option.getId() : null,
            option != null ? option.getOptionType() : "Unknown Option",
            option != null ? option.getIsin() : null
        );

        List<MetricResult> results = metricResultRepository.findByCalculationRunIdAndMetricCode(run.getId(), "RSK-01");
        MetricResult metricResult = results.isEmpty() ? null : results.get(0);

        Map<String, Object> diagnostics = Collections.emptyMap();
        if (metricResult != null && metricResult.getDiagnostics() != null) {
            try {
                diagnostics = objectMapper.readValue(metricResult.getDiagnostics(), new TypeReference<>() {});
            } catch (Exception ignored) {}
        }

        BigDecimal numericValue = metricResult != null ? metricResult.getNumericValue() : null;
        String calculationStatus = metricResult != null ? metricResult.getCalculationStatus()
            : ("FAILED".equals(run.getRunStatus()) ? "INSUFFICIENT_DATA" : run.getRunStatus());
        String formattedValue = numericValue != null
            ? String.format("%.4f%%", numericValue.multiply(new BigDecimal("100")))
            : null;
        String errorMessage = metricResult != null && metricResult.getErrorMessage() != null
            ? metricResult.getErrorMessage() : run.getErrorMessage();

        Rsk01AnalysisResponse.ResultInfo result = new Rsk01AnalysisResponse.ResultInfo(
            "RSK-01",
            "3-Year Annualized Volatility",
            numericValue,
            formattedValue,
            "PERCENTAGE",
            calculationStatus,
            errorMessage
        );

        LocalDate reqStart = diagnostics.containsKey("requested_start_date")
            ? LocalDate.parse((String) diagnostics.get("requested_start_date"))
            : run.getAsOfDate().minusYears(3);
        LocalDate reqEnd = diagnostics.containsKey("requested_end_date")
            ? LocalDate.parse((String) diagnostics.get("requested_end_date"))
            : run.getAsOfDate();

        LocalDate actualStart = diagnostics.containsKey("actual_start_date")
            ? LocalDate.parse((String) diagnostics.get("actual_start_date"))
            : null;
        LocalDate actualEnd = diagnostics.containsKey("actual_end_date")
            ? LocalDate.parse((String) diagnostics.get("actual_end_date"))
            : null;

        Integer obsCount = diagnostics.containsKey("observation_count")
            ? ((Number) diagnostics.get("observation_count")).intValue()
            : 0;
        Integer minObsReq = diagnostics.containsKey("min_observations_required")
            ? ((Number) diagnostics.get("min_observations_required")).intValue()
            : 700;

        Rsk01AnalysisResponse.WindowInfo window = new Rsk01AnalysisResponse.WindowInfo(
            reqStart,
            reqEnd,
            actualStart,
            actualEnd,
            obsCount,
            minObsReq,
            36
        );

        Rsk01AnalysisResponse.PitInfo pit = new Rsk01AnalysisResponse.PitInfo(
            run.getKnowledgeCutoffTime(),
            true,
            "Factual AMFI source availability timestamp is unrecorded upstream. Analytical EOD cutoff convention applied.",
            "CONVENTION_EOD_HISTORICAL_CUTOFF",
            "HISTORICAL_BACKFILL"
        );

        String mCode = run.getMethodologyVersion() != null ? run.getMethodologyVersion().getMethodologyCode() : "RSK_01_3Y_VOLATILITY";
        String mVer = run.getMethodologyVersion() != null ? run.getMethodologyVersion().getVersionTag() : "CANDIDATE_V1";
        String mStatus = run.getMethodologyVersion() != null ? run.getMethodologyVersion().getApprovalStatus() : "CANDIDATE";

        Rsk01AnalysisResponse.MethodologyInfo methodology = new Rsk01AnalysisResponse.MethodologyInfo(
            mCode,
            mVer,
            mStatus,
            true,
            "SQRT_252_CANDIDATE",
            "N_MINUS_ONE_CANDIDATE",
            "s = sqrt( (1 / (N - 1)) * sum((R_t - R_mean)^2) ); sigma_annual = s * sqrt(252)"
        );

        List<CalculationRunInputObservation> inputObsLinks = calculationRunInputObservationRepository.findByCalculationRunId(run.getId());
        inputObsLinks.sort(Comparator.comparing(CalculationRunInputObservation::getEffectiveDate));

        List<Rsk01AnalysisResponse.InputObservationRef> inputObservationRefs = new ArrayList<>();
        Map<Long, Rsk01AnalysisResponse.SourceArtifactSummary> artifactMap = new HashMap<>();

        String primaryQuality = "VALID";
        String primaryVerification = "VERIFIED";
        String primaryRevision = "ORIGINAL";
        String primaryFreshness = "CURRENT";
        String primaryPresence = "AVAILABLE";
        String primaryIntegrity = "NONE";

        for (CalculationRunInputObservation link : inputObsLinks) {
            NavObservation obs = link.getNavObservation();
            if (obs == null) continue;

            SourceArtifact artifact = obs.getSourceArtifact();

            if ("SUSPICIOUS".equals(obs.getQualityAssessment())) primaryQuality = "SUSPICIOUS";
            if ("INVALID".equals(obs.getQualityAssessment())) primaryQuality = "INVALID";
            if ("UNVERIFIED".equals(obs.getVerificationStatus())) primaryVerification = "UNVERIFIED";
            if ("REVISED".equals(obs.getRevisionStatus())) primaryRevision = "REVISED";
            if ("STALE".equals(obs.getTemporalStatus())) primaryFreshness = "STALE";

            inputObservationRefs.add(new Rsk01AnalysisResponse.InputObservationRef(
                obs.getId(),
                obs.getEffectiveDate(),
                obs.getRevisionSeq(),
                obs.getNavValue(),
                obs.getAvailabilityTime(),
                obs.getQualityAssessment(),
                obs.getVerificationStatus(),
                obs.getRevisionStatus(),
                obs.getTemporalStatus() != null ? obs.getTemporalStatus() : "CURRENT",
                obs.getPresenceStatus(),
                "NONE",
                "HISTORICAL_BACKFILL",
                artifact != null ? artifact.getId() : null,
                artifact != null ? artifact.getSha256Hash() : null
            ));

            if (artifact != null && !artifactMap.containsKey(artifact.getId())) {
                String sourceUrl = artifact.getStorageUri() != null ? artifact.getStorageUri() : "https://www.amfiindia.com/net-asset-value/nav-history";
                artifactMap.put(artifact.getId(), new Rsk01AnalysisResponse.SourceArtifactSummary(
                    artifact.getId(),
                    sourceUrl,
                    artifact.getSha256Hash(),
                    artifact.getRetrievalTimestamp(),
                    artifact.getByteSize()
                ));
            }
        }

        if (inputObsLinks.isEmpty()) {
            primaryPresence = "MISSING";
            primaryQuality = "INVALID";
        }

        List<String> validationFlags = new ArrayList<>();
        if (option != null) {
            List<ValidationIssue> issues = validationIssueRepository.findByTargetEntityTypeAndTargetEntityId("SCHEME_OPTION", option.getId());
            for (ValidationIssue issue : issues) {
                validationFlags.add(issue.getCheckCode() + ": " + issue.getMessage());
                if ("CONFLICTING".equalsIgnoreCase(issue.getIntegrityCondition())) {
                    primaryIntegrity = "CONFLICTING";
                } else if ("DUPLICATE".equalsIgnoreCase(issue.getIntegrityCondition()) && !"CONFLICTING".equals(primaryIntegrity)) {
                    primaryIntegrity = "DUPLICATE";
                }
            }
        }

        List<Rsk01AnalysisResponse.QualityDimension> dimensions = List.of(
            new Rsk01AnalysisResponse.QualityDimension("Quality", primaryQuality, "Conforms to schema and historical sanity thresholds."),
            new Rsk01AnalysisResponse.QualityDimension("Verification", primaryVerification, "Observation reconciled against source artifact."),
            new Rsk01AnalysisResponse.QualityDimension("Revision", primaryRevision, "Authoritative revision status within bitemporal ledger."),
            new Rsk01AnalysisResponse.QualityDimension("Freshness", primaryFreshness, "Observation delivery timeliness against reporting schedule."),
            new Rsk01AnalysisResponse.QualityDimension("Presence", primaryPresence, "Observation presence evaluated at PIT cutoff."),
            new Rsk01AnalysisResponse.QualityDimension("Integrity", primaryIntegrity, "Bitemporal relationship condition (duplicate or conflicting detection).")
        );

        Rsk01AnalysisResponse.QualityInfo quality = new Rsk01AnalysisResponse.QualityInfo(
            primaryQuality,
            dimensions,
            validationFlags
        );

        String commitHash = run.getMethodologyVersion() != null ? run.getMethodologyVersion().getGitCommitHash() : null;
        Rsk01AnalysisResponse.ProvenanceInfo provenance = new Rsk01AnalysisResponse.ProvenanceInfo(
            run.getId(),
            run.getRunStatus(),
            run.getExecutionStartedAt(),
            run.getExecutionCompletedAt(),
            run.getEngineSoftwareVersion(),
            commitHash,
            run.getInputSnapshotSha256(),
            inputObservationRefs,
            new ArrayList<>(artifactMap.values())
        );

        boolean insufficient = "INSUFFICIENT_DATA".equals(calculationStatus) || "FAILED".equals(run.getRunStatus());
        Rsk01AnalysisResponse.LimitationsInfo limitations = new Rsk01AnalysisResponse.LimitationsInfo(
            true,
            "CONVENTION_EOD_HISTORICAL_CUTOFF",
            "HISTORICAL_BACKFILL",
            true,
            true,
            insufficient,
            obsCount,
            minObsReq,
            "Annualized volatility uses candidate sqrt(252) annualizer and N-1 divisor. Historical volatility does not predict future volatility. Zero investment recommendation."
        );

        Rsk01AnalysisResponse.BenchmarkInfo benchmark = new Rsk01AnalysisResponse.BenchmarkInfo(
            false,
            null,
            "RSK-01 is a standalone single-asset risk metric. Benchmark is explicitly not required and no synthetic benchmark was used."
        );

        return new Rsk01AnalysisResponse(
            identity,
            result,
            window,
            pit,
            methodology,
            quality,
            provenance,
            limitations,
            benchmark
        );
    }
}
