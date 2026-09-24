package com.yukira.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.analysis.*;
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

    @Transactional
    public RiskAnalysisResponse executeRsk02Analysis(Rsk02CalculationRequest request) {
        Objects.requireNonNull(request.schemeOptionId(), "schemeOptionId must not be null");
        Objects.requireNonNull(request.endDate(), "endDate must not be null");
        Objects.requireNonNull(request.knowledgeCutoffTime(), "knowledgeCutoffTime must not be null for PIT compliance");

        String tag = request.methodologyTag() != null && !request.methodologyTag().isBlank()
            ? request.methodologyTag() : "CANDIDATE_V1";

        CalculationRun run = riskCalculationService.executeRsk02Calculation(
            request.schemeOptionId(),
            request.endDate(),
            request.knowledgeCutoffTime(),
            tag
        );

        return buildRiskResponse(run, "RSK-02");
    }

    @Transactional
    public RiskAnalysisResponse executeRsk03Analysis(Rsk03CalculationRequest request) {
        Objects.requireNonNull(request.schemeOptionId(), "schemeOptionId must not be null");
        Objects.requireNonNull(request.endDate(), "endDate must not be null");
        Objects.requireNonNull(request.knowledgeCutoffTime(), "knowledgeCutoffTime must not be null for PIT compliance");

        String tag = request.methodologyTag() != null && !request.methodologyTag().isBlank()
            ? request.methodologyTag() : "CANDIDATE_V1";

        CalculationRun run = riskCalculationService.executeRsk03Calculation(
            request.schemeOptionId(),
            request.endDate(),
            request.knowledgeCutoffTime(),
            tag
        );

        return buildRiskResponse(run, "RSK-03");
    }

    @Transactional
    public RiskAnalysisResponse executeRsk04Analysis(Rsk04CalculationRequest request) {
        Objects.requireNonNull(request.schemeOptionId(), "schemeOptionId must not be null");
        Objects.requireNonNull(request.endDate(), "endDate must not be null");
        Objects.requireNonNull(request.knowledgeCutoffTime(), "knowledgeCutoffTime must not be null for PIT compliance");

        String tag = request.methodologyTag() != null && !request.methodologyTag().isBlank()
            ? request.methodologyTag() : "CANDIDATE_V1";

        CalculationRun run = riskCalculationService.executeRsk04Calculation(
            request.schemeOptionId(),
            request.endDate(),
            request.knowledgeCutoffTime(),
            tag
        );

        return buildRiskResponse(run, "RSK-04");
    }

    @Transactional
    public RiskAnalysisResponse executeRsk05Analysis(Rsk05CalculationRequest request) {
        Objects.requireNonNull(request.schemeOptionId(), "schemeOptionId must not be null");
        Objects.requireNonNull(request.endDate(), "endDate must not be null");
        Objects.requireNonNull(request.knowledgeCutoffTime(), "knowledgeCutoffTime must not be null for PIT compliance");

        String tag = request.methodologyTag() != null && !request.methodologyTag().isBlank()
            ? request.methodologyTag() : "CANDIDATE_V1";

        CalculationRun run = riskCalculationService.executeRsk05Calculation(
            request.schemeOptionId(),
            request.endDate(),
            request.knowledgeCutoffTime(),
            tag
        );

        return buildRiskResponse(run, "RSK-05");
    }

    @Transactional(readOnly = true)
    public Optional<Object> getAnalysisByRunId(Long runId) {
        return calculationRunRepository.findById(runId).map(run -> {
            String mCode = run.getMethodologyVersion() != null ? run.getMethodologyVersion().getMethodologyCode() : "";
            List<MetricResult> results = metricResultRepository.findByCalculationRunId(run.getId());
            String resCode = results.isEmpty() ? "" : results.get(0).getMetricCode();

            if (mCode.contains("RSK_01") || "RSK-01".equals(resCode)) {
                return buildRsk01Response(run);
            }
            if (mCode.contains("RSK_02") || "RSK-02".equals(resCode)) {
                return buildRiskResponse(run, "RSK-02");
            }
            if (mCode.contains("RSK_03") || "RSK-03".equals(resCode)) {
                return buildRiskResponse(run, "RSK-03");
            }
            if (mCode.contains("RSK_04") || "RSK-04".equals(resCode)) {
                return buildRiskResponse(run, "RSK-04");
            }
            if (mCode.contains("RSK_05") || "RSK-05".equals(resCode)) {
                return buildRiskResponse(run, "RSK-05");
            }
            if (mCode.contains("RET_03") || "RET-03".equals(resCode)) {
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
    public Optional<Ret03AnalysisResponse> getRet03AnalysisByRunId(Long runId) {
        return calculationRunRepository.findById(runId).map(this::buildRet03Response);
    }

    @Transactional(readOnly = true)
    public Optional<Rsk01AnalysisResponse> getRsk01AnalysisByRunId(Long runId) {
        return calculationRunRepository.findById(runId).map(this::buildRsk01Response);
    }

    @Transactional(readOnly = true)
    public Optional<RiskAnalysisResponse> getRiskAnalysisByRunIdAndCode(Long runId, String metricCode) {
        return calculationRunRepository.findById(runId).map(r -> this.buildRiskResponse(r, metricCode));
    }

    @Transactional(readOnly = true)
    public Ret02AnalysisResponse buildRet02Response(CalculationRun run) {
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

        Ret02AnalysisResponse.PitInfo pit = new Ret02AnalysisResponse.PitInfo(
            run.getKnowledgeCutoffTime(),
            true,
            "Factual AMFI source availability timestamp is unrecorded upstream. Analytical EOD cutoff convention applied.",
            "CONVENTION_EOD_HISTORICAL_CUTOFF",
            "HISTORICAL_BACKFILL"
        );

        String mCode = run.getMethodologyVersion() != null ? run.getMethodologyVersion().getMethodologyCode() : "RET_02_SIMPLE_RETURN";
        String mVer = run.getMethodologyVersion() != null ? run.getMethodologyVersion().getVersionTag() : "CANDIDATE_V1";
        String mStatus = run.getMethodologyVersion() != null ? run.getMethodologyVersion().getApprovalStatus() : "CANDIDATE";

        Ret02AnalysisResponse.MethodologyInfo methodology = new Ret02AnalysisResponse.MethodologyInfo(
            mCode,
            mVer,
            mStatus,
            true,
            "Candidate 4-calendar-day lookback window preceding requested boundary date",
            "Discrete return: (NAV_end - NAV_start) / NAV_start"
        );

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
        RiskAnalysisResponse r = buildRiskResponse(run, "RSK-01");
        return new Rsk01AnalysisResponse(
            new Rsk01AnalysisResponse.IdentityInfo(
                r.identity().schemeId(), r.identity().schemeName(), r.identity().amfiCode(),
                r.identity().schemeOptionId(), r.identity().optionType(), r.identity().isin()
            ),
            new Rsk01AnalysisResponse.ResultInfo(
                r.result().metricCode(), r.result().metricName(), r.result().numericValue(),
                r.result().formattedValue(), r.result().units(), r.result().calculationStatus(),
                r.result().errorMessage()
            ),
            new Rsk01AnalysisResponse.WindowInfo(
                r.window().requestedStartDate(), r.window().requestedEndDate(),
                r.window().actualStartDate(), r.window().actualEndDate(),
                r.window().observationCount(), r.window().minObservationsRequired(),
                r.window().windowMonths()
            ),
            new Rsk01AnalysisResponse.PitInfo(
                r.pit().knowledgeCutoffTime(), r.pit().pitFilteringApplied(),
                r.pit().temporalLimitationDisclosure(), r.pit().cutoffConventionApplied(),
                r.pit().sourceAvailabilitySemantic()
            ),
            new Rsk01AnalysisResponse.MethodologyInfo(
                r.methodology().methodologyCode(), r.methodology().methodologyVersion(),
                r.methodology().approvalStatus(), r.methodology().isCandidate(),
                r.methodology().annualizationConvention(), r.methodology().denominatorConvention(),
                r.methodology().formulaDisclosure()
            ),
            new Rsk01AnalysisResponse.QualityInfo(
                r.quality().overallAssessment(),
                r.quality().dimensions().stream().map(d -> new Rsk01AnalysisResponse.QualityDimension(d.dimension(), d.state(), d.description())).toList(),
                r.quality().validationFlags()
            ),
            new Rsk01AnalysisResponse.ProvenanceInfo(
                r.provenance().calculationRunId(), r.provenance().runStatus(),
                r.provenance().executionStartedAt(), r.provenance().executionCompletedAt(),
                r.provenance().quantEngineVersion(), r.provenance().methodologyGitCommit(),
                r.provenance().inputSnapshotSha256(),
                r.provenance().inputObservations().stream().map(obs -> new Rsk01AnalysisResponse.InputObservationRef(
                    obs.observationId(), obs.effectiveDate(), obs.revisionSeq(), obs.navValue(),
                    obs.availabilityTime(), obs.qualityAssessment(), obs.verificationStatus(),
                    obs.revisionStatus(), obs.temporalStatus(), obs.presenceStatus(),
                    obs.integrityCondition(), obs.sourceAvailabilitySemantic(),
                    obs.sourceArtifactId(), obs.sourceArtifactSha256()
                )).toList(),
                r.provenance().sourceArtifacts().stream().map(a -> new Rsk01AnalysisResponse.SourceArtifactSummary(
                    a.sourceArtifactId(), a.sourceUrl(), a.sha256Hash(), a.retrievalTimestamp(), a.byteSize()
                )).toList()
            ),
            new Rsk01AnalysisResponse.LimitationsInfo(
                r.limitations().factualAvailabilityTimestampUnavailable(),
                r.limitations().analyticalCutoffConvention(),
                r.limitations().sourceAvailabilitySemantic(),
                r.limitations().candidateAnnualizationApplied(),
                r.limitations().candidateDenominatorApplied(),
                r.limitations().insufficientEvidence(),
                r.limitations().observationCount(),
                r.limitations().minObservationsRequired(),
                r.limitations().disclosureSummary()
            ),
            new Rsk01AnalysisResponse.BenchmarkInfo(
                r.benchmark().benchmarkRequired(), r.benchmark().benchmarkId(), r.benchmark().benchmarkNotice()
            )
        );
    }

    @Transactional(readOnly = true)
    public RiskAnalysisResponse buildRiskResponse(CalculationRun run, String defaultMetricCode) {
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

        RiskAnalysisResponse.IdentityInfo identity = new RiskAnalysisResponse.IdentityInfo(
            scheme != null ? scheme.getId() : null,
            scheme != null ? scheme.getName() : "Unknown Scheme",
            option != null ? option.getAmfiCode() : (scheme != null ? scheme.getCode() : null),
            option != null ? option.getId() : null,
            option != null ? option.getOptionType() : "Unknown Option",
            option != null ? option.getIsin() : null
        );

        String metricCode = defaultMetricCode;
        List<MetricResult> allResults = metricResultRepository.findByCalculationRunId(run.getId());
        MetricResult metricResult = null;
        if (defaultMetricCode != null) {
            for (MetricResult mr : allResults) {
                if (defaultMetricCode.equals(mr.getMetricCode())) {
                    metricResult = mr;
                    break;
                }
            }
        }
        if (metricResult == null && !allResults.isEmpty()) {
            metricResult = allResults.get(0);
            metricCode = metricResult.getMetricCode();
        }
        if (metricCode == null && run.getMethodologyVersion() != null) {
            String mCode = run.getMethodologyVersion().getMethodologyCode();
            if (mCode.contains("RSK_01")) metricCode = "RSK-01";
            else if (mCode.contains("RSK_02")) metricCode = "RSK-02";
            else if (mCode.contains("RSK_03")) metricCode = "RSK-03";
            else if (mCode.contains("RSK_04")) metricCode = "RSK-04";
            else if (mCode.contains("RSK_05")) metricCode = "RSK-05";
            else metricCode = "RSK-01";
        }

        Map<String, Object> diagnostics = Collections.emptyMap();
        if (metricResult != null && metricResult.getDiagnostics() != null) {
            try {
                diagnostics = objectMapper.readValue(metricResult.getDiagnostics(), new TypeReference<>() {});
            } catch (Exception ignored) {}
        }

        BigDecimal numericValue = metricResult != null ? metricResult.getNumericValue() : null;
        String calculationStatus = metricResult != null ? metricResult.getCalculationStatus()
            : ("FAILED".equals(run.getRunStatus()) ? "INSUFFICIENT_DATA" : run.getRunStatus());

        String metricName;
        String units = metricResult != null ? metricResult.getUnits() : "PERCENTAGE";
        String formattedValue = null;
        String formulaDisclosure;
        String disclosureSummary;
        String annualizationConv = "NONE";
        String denominatorConv = "NONE";

        switch (metricCode) {
            case "RSK-02" -> {
                metricName = "Downside Semideviation (3Y)";
                units = "PERCENTAGE";
                annualizationConv = "SQRT_252_CANDIDATE";
                denominatorConv = "N_MINUS_ONE_CANDIDATE";
                formulaDisclosure = "sigma_down = sqrt( (1 / (N - 1)) * sum(min(R_t - MAR, 0)^2) ) * sqrt(252)";
                disclosureSummary = "Downside semideviation uses candidate MAR=0 hurdle, sqrt(252) annualizer, and N-1 divisor. Zero investment recommendation.";
                if (numericValue != null) formattedValue = String.format("%.4f%%", numericValue.multiply(new BigDecimal("100")));
            }
            case "RSK-03" -> {
                metricName = "3-Year Maximum Drawdown";
                units = "PERCENTAGE";
                annualizationConv = "NONE_DISCRETE_PATH";
                denominatorConv = "RUNNING_PEAK_NAV_CANDIDATE";
                formulaDisclosure = "Running_Peak_t = max(NAV_1 ... NAV_t); Drawdown_t = (NAV_t / Running_Peak_t) - 1; Max_Drawdown = min(Drawdown_t)";
                disclosureSummary = "Maximum drawdown measures the worst peak-to-trough decline over the 3-year valuation path. Does not capture recovery duration or secondary drawdown frequency. Zero investment recommendation.";
                if (numericValue != null) formattedValue = String.format("%.4f%%", numericValue.multiply(new BigDecimal("100")));
            }
            case "RSK-04" -> {
                metricName = "Maximum Drawdown Duration";
                units = "DAYS";
                annualizationConv = "NONE";
                denominatorConv = "NONE";
                formulaDisclosure = "Duration = max(ElapsedCalendarDays(Peak -> Recovery)); Ongoing measured to cutoff";
                disclosureSummary = "Maximum drawdown duration measures the longest calendar period spent underwater. Ongoing drawdowns at the cutoff date are right-censored. Zero investment recommendation.";
                if (numericValue != null) formattedValue = String.format("%d days", numericValue.intValue());
            }
            case "RSK-05" -> {
                metricName = "Ulcer Index (3Y)";
                units = "POINTS";
                annualizationConv = "NONE";
                denominatorConv = "N_OBSERVATIONS_CANDIDATE";
                formulaDisclosure = "Pct_DD_t = 100 * ((NAV_t / Running_Peak_t) - 1); Ulcer_Index = sqrt((1 / N) * sum(Pct_DD_t^2))";
                disclosureSummary = "Ulcer Index measures the depth and duration of drawdowns quadratically as an index score of underwater stress. Zero investment recommendation.";
                if (numericValue != null) formattedValue = String.format("%.4f UI points", numericValue);
            }
            default -> {
                metricName = "3-Year Annualized Volatility";
                units = "PERCENTAGE";
                annualizationConv = "SQRT_252_CANDIDATE";
                denominatorConv = "N_MINUS_ONE_CANDIDATE";
                formulaDisclosure = "s = sqrt( (1 / (N - 1)) * sum((R_t - R_mean)^2) ); sigma_annual = s * sqrt(252)";
                disclosureSummary = "Annualized volatility uses candidate sqrt(252) annualizer and N-1 divisor. Historical volatility does not predict future volatility. Zero investment recommendation.";
                if (numericValue != null) formattedValue = String.format("%.4f%%", numericValue.multiply(new BigDecimal("100")));
            }
        }

        String errorMessage = metricResult != null && metricResult.getErrorMessage() != null
            ? metricResult.getErrorMessage() : run.getErrorMessage();

        RiskAnalysisResponse.ResultInfo result = new RiskAnalysisResponse.ResultInfo(
            metricCode,
            metricName,
            numericValue,
            formattedValue,
            units,
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

        RiskAnalysisResponse.WindowInfo window = new RiskAnalysisResponse.WindowInfo(
            reqStart,
            reqEnd,
            actualStart,
            actualEnd,
            obsCount,
            minObsReq,
            36
        );

        RiskAnalysisResponse.PitInfo pit = new RiskAnalysisResponse.PitInfo(
            run.getKnowledgeCutoffTime(),
            true,
            "Factual AMFI source availability timestamp is unrecorded upstream. Analytical EOD cutoff convention applied.",
            "CONVENTION_EOD_HISTORICAL_CUTOFF",
            "HISTORICAL_BACKFILL"
        );

        String mCode = run.getMethodologyVersion() != null ? run.getMethodologyVersion().getMethodologyCode() : ("RSK_01_3Y_VOLATILITY");
        String mVer = run.getMethodologyVersion() != null ? run.getMethodologyVersion().getVersionTag() : "CANDIDATE_V1";
        String mStatus = run.getMethodologyVersion() != null ? run.getMethodologyVersion().getApprovalStatus() : "CANDIDATE";

        RiskAnalysisResponse.MethodologyInfo methodology = new RiskAnalysisResponse.MethodologyInfo(
            mCode,
            mVer,
            mStatus,
            true,
            annualizationConv,
            denominatorConv,
            formulaDisclosure
        );

        List<CalculationRunInputObservation> inputObsLinks = calculationRunInputObservationRepository.findByCalculationRunId(run.getId());
        inputObsLinks.sort(Comparator.comparing(CalculationRunInputObservation::getEffectiveDate));

        List<RiskAnalysisResponse.InputObservationRef> inputObservationRefs = new ArrayList<>();
        Map<Long, RiskAnalysisResponse.SourceArtifactSummary> artifactMap = new HashMap<>();

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

            inputObservationRefs.add(new RiskAnalysisResponse.InputObservationRef(
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
                artifactMap.put(artifact.getId(), new RiskAnalysisResponse.SourceArtifactSummary(
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

        List<RiskAnalysisResponse.QualityDimension> dimensions = List.of(
            new RiskAnalysisResponse.QualityDimension("Quality", primaryQuality, "Conforms to schema and historical sanity thresholds."),
            new RiskAnalysisResponse.QualityDimension("Verification", primaryVerification, "Observation reconciled against source artifact."),
            new RiskAnalysisResponse.QualityDimension("Revision", primaryRevision, "Authoritative revision status within bitemporal ledger."),
            new RiskAnalysisResponse.QualityDimension("Freshness", primaryFreshness, "Observation delivery timeliness against reporting schedule."),
            new RiskAnalysisResponse.QualityDimension("Presence", primaryPresence, "Observation presence evaluated at PIT cutoff."),
            new RiskAnalysisResponse.QualityDimension("Integrity", primaryIntegrity, "Bitemporal relationship condition (duplicate or conflicting detection).")
        );

        RiskAnalysisResponse.QualityInfo quality = new RiskAnalysisResponse.QualityInfo(
            primaryQuality,
            dimensions,
            validationFlags
        );

        String commitHash = run.getMethodologyVersion() != null ? run.getMethodologyVersion().getGitCommitHash() : null;
        RiskAnalysisResponse.ProvenanceInfo provenance = new RiskAnalysisResponse.ProvenanceInfo(
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
        RiskAnalysisResponse.LimitationsInfo limitations = new RiskAnalysisResponse.LimitationsInfo(
            true,
            "CONVENTION_EOD_HISTORICAL_CUTOFF",
            "HISTORICAL_BACKFILL",
            !"NONE".equals(annualizationConv),
            !"NONE".equals(denominatorConv),
            insufficient,
            obsCount,
            minObsReq,
            disclosureSummary
        );

        RiskAnalysisResponse.BenchmarkInfo benchmark = new RiskAnalysisResponse.BenchmarkInfo(
            false,
            null,
            metricCode + " is a standalone single-asset risk metric. Benchmark is explicitly not required and no synthetic benchmark was used."
        );

        return new RiskAnalysisResponse(
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
