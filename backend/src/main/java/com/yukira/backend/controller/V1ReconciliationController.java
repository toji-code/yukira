package com.yukira.backend.controller;

import com.yukira.backend.domain.entity.HistoricalReportContradictionRecord;
import com.yukira.backend.domain.entity.IndianMutualFundTaxRule;
import com.yukira.backend.domain.entity.V1TaskRequirementReconciliation;
import com.yukira.backend.domain.entity.V1ValidationReadinessItem;
import com.yukira.backend.repository.IndianMutualFundTaxRuleRepository;
import com.yukira.backend.service.*;
import com.yukira.backend.service.ai.CanonicalEvidencePacketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for V1 Implementation Reconciliation, Statutory Tax Rules,
 * Methodology Validation Registry, Capability Matrices, Thesis Versions,
 * Operational Job Dependencies, and XIRR Evidence Contracts (Tasks 260-264, 273, 274, 299, 304-309, 310-359).
 */
@RestController
@RequestMapping("/api/v1/reconciliation")
@CrossOrigin(origins = "*")
public class V1ReconciliationController {

    private final V1ProductReconciliationService reconciliationService;
    private final IndianMutualFundTaxRuleRepository taxRuleRepository;
    private final SchemeDataCoverageService schemeDataCoverageService;
    private final InvestorResearchThesisService thesisService;
    private final MonitoringEvaluationLifecycleService evaluationService;
    private final NotificationDeliveryService deliveryService;
    private final SourceAvailabilityCalendarService calendarService;
    private final MetricValidationRegistryService metricValidationService;
    private final OperationalDependencyRegistryService operationalDependencyService;
    private final PortfolioValuationAndDriftService portfolioValuationService;
    private final BenchmarkCoverageLedgerService benchmarkCoverageService;
    private final CanonicalEvidencePacketService canonicalEvidencePacketService;

    public V1ReconciliationController(
        V1ProductReconciliationService reconciliationService,
        IndianMutualFundTaxRuleRepository taxRuleRepository,
        SchemeDataCoverageService schemeDataCoverageService,
        InvestorResearchThesisService thesisService,
        MonitoringEvaluationLifecycleService evaluationService,
        NotificationDeliveryService deliveryService,
        SourceAvailabilityCalendarService calendarService,
        MetricValidationRegistryService metricValidationService,
        OperationalDependencyRegistryService operationalDependencyService,
        PortfolioValuationAndDriftService portfolioValuationService,
        BenchmarkCoverageLedgerService benchmarkCoverageService,
        CanonicalEvidencePacketService canonicalEvidencePacketService
    ) {
        this.reconciliationService = reconciliationService;
        this.taxRuleRepository = taxRuleRepository;
        this.schemeDataCoverageService = schemeDataCoverageService;
        this.thesisService = thesisService;
        this.evaluationService = evaluationService;
        this.deliveryService = deliveryService;
        this.calendarService = calendarService;
        this.metricValidationService = metricValidationService;
        this.operationalDependencyService = operationalDependencyService;
        this.portfolioValuationService = portfolioValuationService;
        this.benchmarkCoverageService = benchmarkCoverageService;
        this.canonicalEvidencePacketService = canonicalEvidencePacketService;
    }

    @GetMapping("/ledger")
    public ResponseEntity<List<V1ProductReconciliationService.RequirementReconciliationItemDto>> getReconciliationLedger() {
        return ResponseEntity.ok(reconciliationService.getTasks210To259ReconciliationLedger());
    }

    @GetMapping("/reconstructed-tasks")
    public ResponseEntity<List<V1ProductReconciliationService.ReconstructedTaskAuditDto>> getReconstructedTasksLedger() {
        return ResponseEntity.ok(reconciliationService.getReconstructedTasks260To309Ledger());
    }

    @GetMapping("/capabilities")
    public ResponseEntity<List<V1ProductReconciliationService.CanonicalProductCapabilityDto>> getCapabilities() {
        return ResponseEntity.ok(reconciliationService.getCanonicalCapabilities());
    }

    @GetMapping("/gaps")
    public ResponseEntity<List<V1ProductReconciliationService.ImplementationGapItemDto>> getImplementationGaps() {
        return ResponseEntity.ok(reconciliationService.getImplementationGaps());
    }

    @GetMapping("/test-baselines")
    public ResponseEntity<List<V1ProductReconciliationService.HistoricalTestBaselineDto>> getHistoricalTestBaselines() {
        return ResponseEntity.ok(reconciliationService.getHistoricalTestBaselines());
    }

    @GetMapping("/dependencies")
    public ResponseEntity<List<V1ProductReconciliationService.DependencyRegisterItemDto>> getDependencyRegister() {
        return ResponseEntity.ok(reconciliationService.getV1AnalyticalDependencyRegister());
    }

    @GetMapping("/metrics/registry")
    public ResponseEntity<List<MetricValidationRegistryService.Phase2HMetricDefinitionDto>> getPhase2HMetricRegistry() {
        return ResponseEntity.ok(metricValidationService.getAll30Phase2HMetrics());
    }

    public record MetricTransitionRequest(
        String metricCode,
        String targetStatus,
        String reviewerIdentity,
        String rationale,
        String referenceDatasetRef,
        String independentMethodology,
        Double numericalTolerance
    ) {}

    @PostMapping("/metrics/transition")
    public ResponseEntity<MetricValidationRegistryService.MetricTransitionResultDto> transitionMetric(
            @RequestBody MetricTransitionRequest request
    ) {
        var result = metricValidationService.transitionMetricGovernanceState(
            request.metricCode(),
            request.targetStatus(),
            request.reviewerIdentity(),
            request.rationale(),
            request.referenceDatasetRef(),
            request.independentMethodology(),
            request.numericalTolerance()
        );
        return ResponseEntity.ok(result);
    }

    @GetMapping("/operational/jobs")
    public ResponseEntity<List<OperationalDependencyRegistryService.OperationalJobSummaryDto>> getOperationalJobs() {
        return ResponseEntity.ok(operationalDependencyService.listAllOperationalJobs());
    }

    @GetMapping("/operational/readiness")
    public ResponseEntity<List<OperationalDependencyRegistryService.ExternalProviderReadinessDto>> getProviderReadiness() {
        return ResponseEntity.ok(operationalDependencyService.evaluateProviderReadiness());
    }

    @GetMapping("/portfolio/{investorId}/xirr-contract")
    public ResponseEntity<PortfolioValuationAndDriftService.XirrSolverContractDto> getPortfolioXirrContract(
            @PathVariable Long investorId,
            @RequestParam(required = false) String asOfDate
    ) {
        LocalDate date = (asOfDate != null && !asOfDate.isBlank()) ? LocalDate.parse(asOfDate) : LocalDate.now();
        return portfolioValuationService.computeXirrCashFlowContract(investorId, date)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/tax-rules")
    public ResponseEntity<List<IndianMutualFundTaxRule>> getTaxRules() {
        return ResponseEntity.ok(taxRuleRepository.findAllByOrderByAcquisitionStartDateAsc());
    }

    @GetMapping("/coverage-matrix/{schemeOptionId}")
    public ResponseEntity<SchemeDataCoverageService.SchemeCapabilityMatrixDto> getCapabilityMatrix(
            @PathVariable Long schemeOptionId
    ) {
        return schemeDataCoverageService.getSchemeCapabilityMatrix(schemeOptionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/theses/{investorId}")
    public ResponseEntity<List<InvestorResearchThesisService.ResearchThesisVersionDto>> getInvestorTheses(
            @PathVariable Long investorId
    ) {
        return ResponseEntity.ok(thesisService.getAllInvestorTheses(investorId));
    }

    @GetMapping("/theses/{investorId}/{thesisId}")
    public ResponseEntity<List<InvestorResearchThesisService.ResearchThesisVersionDto>> getThesisVersions(
            @PathVariable Long investorId,
            @PathVariable String thesisId
    ) {
        return ResponseEntity.ok(thesisService.getThesisHistory(investorId, thesisId));
    }

    public record ThesisCreationRequest(
        Long investorId,
        Long schemeId,
        Long schemeOptionId,
        String thesisId,
        String author,
        String title,
        String body,
        String linkedCondition,
        String linkedEvidence,
        String revisionReason
    ) {}

    @PostMapping("/theses")
    public ResponseEntity<InvestorResearchThesisService.ResearchThesisVersionDto> saveThesisVersion(
            @RequestBody ThesisCreationRequest request
    ) {
        var saved = thesisService.recordThesisVersion(
            request.investorId(),
            request.schemeId(),
            request.schemeOptionId(),
            request.thesisId(),
            request.author(),
            request.title(),
            request.body(),
            request.linkedCondition(),
            request.linkedEvidence(),
            request.revisionReason()
        );
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/monitoring/runs")
    public ResponseEntity<List<MonitoringEvaluationLifecycleService.MonitoringEvaluationRunDto>> getMonitoringRuns(
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(evaluationService.getRecentEvaluationRuns(limit));
    }

    @GetMapping("/monitoring/outbox")
    public ResponseEntity<List<NotificationDeliveryService.NotificationDeliveryDto>> getNotificationOutbox(
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(deliveryService.getRecentDeliveries(limit));
    }

    @PostMapping("/monitoring/dispatch")
    public ResponseEntity<List<NotificationDeliveryService.NotificationDeliveryDto>> dispatchNotifications(
            @RequestParam(defaultValue = "10") int batchSize
    ) {
        return ResponseEntity.ok(deliveryService.dispatchPendingNotifications(batchSize));
    }

    @GetMapping("/calendar/logs")
    public ResponseEntity<List<SourceAvailabilityCalendarService.SourceCalendarExecutionLogDto>> getCalendarLogs(
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(calendarService.getRecentExecutionLogs(limit));
    }

    @GetMapping("/tasks-360-409")
    public ResponseEntity<List<V1ProductReconciliationService.Task360To409AuditDto>> getTasks360To409Ledger() {
        return ResponseEntity.ok(reconciliationService.getTasks360To409Ledger());
    }

    @GetMapping("/operational/status-report")
    public ResponseEntity<OperationalDependencyRegistryService.OperationalStatusReportDto> getOperationalStatusReport() {
        return ResponseEntity.ok(operationalDependencyService.getOperationalStatusReport());
    }

    @GetMapping("/operational/investor-disclosure")
    public ResponseEntity<OperationalDependencyRegistryService.InvestorSafeDisclosureDto> getInvestorSafeDisclosure() {
        return ResponseEntity.ok(operationalDependencyService.getInvestorSafeDisclosure());
    }

    @GetMapping("/benchmark/coverage")
    public ResponseEntity<List<BenchmarkCoverageLedgerService.BenchmarkCoverageDto>> getBenchmarkCoverage() {
        return ResponseEntity.ok(benchmarkCoverageService.listAllBenchmarkCoverage());
    }

    @GetMapping("/benchmark/mapping-history/{schemeId}")
    public ResponseEntity<List<BenchmarkCoverageLedgerService.SchemeBenchmarkMappingIntervalDto>> getSchemeBenchmarkMappingHistory(
            @PathVariable Long schemeId
    ) {
        return ResponseEntity.ok(benchmarkCoverageService.getMappingHistoryForScheme(schemeId));
    }

    @GetMapping("/coverage-10d/{schemeOptionId}")
    public ResponseEntity<SchemeDataCoverageService.SchemeCoverageMatrix10dDto> getScheme10DimensionCoverage(
            @PathVariable Long schemeOptionId
    ) {
        return schemeDataCoverageService.get10DimensionCoverageMatrix(schemeOptionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/evidence-chain/{schemeOptionId}")
    public ResponseEntity<CanonicalEvidencePacketService.EndToEndEvidenceChainDto> getEndToEndEvidenceChain(
            @PathVariable Long schemeOptionId
    ) {
        return canonicalEvidencePacketService.getEndToEndEvidenceChain(schemeOptionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    @GetMapping("/task-requirements")
    public ResponseEntity<List<V1TaskRequirementReconciliation>> getTaskRequirements() {
        return ResponseEntity.ok(reconciliationService.getAllTaskRequirementReconciliations());
    }

    @GetMapping("/contradictions")
    public ResponseEntity<List<HistoricalReportContradictionRecord>> getHistoricalContradictions() {
        return ResponseEntity.ok(reconciliationService.getAllHistoricalReportContradictions());
    }

    @GetMapping("/validation-readiness")
    public ResponseEntity<List<V1ValidationReadinessItem>> getValidationReadiness() {
        return ResponseEntity.ok(reconciliationService.getAllValidationReadinessItems());
    }

    @GetMapping("/validation-readiness/{areaCode}")
    public ResponseEntity<V1ValidationReadinessItem> getValidationReadinessByArea(@PathVariable String areaCode) {
        return reconciliationService.getValidationReadinessItemByCode(areaCode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
