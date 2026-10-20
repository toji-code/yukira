package com.yukira.backend.ingestion.holdings;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.repository.PortfolioSnapshotRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service orchestrating batch monthly holdings refresh runs across registered mutual funds.
 *
 * Epistemic, Provenance & Concurrency Rules:
 * 1. Controlled Execution: Processes registered funds using explicit, verified source descriptors.
 * 2. Strict State Separation: Distinguishes MASTER_REGISTERED from SOURCE_CONFIGURED; unconfigured funds are safely skipped with SOURCE_NOT_CONFIGURED status.
 * 3. Fund-Level Isolation: Failure of one fund's source fetch (e.g. SOURCE_UNAVAILABLE) does NOT affect other funds or erase existing historical snapshots.
 * 4. Idempotency & Bitemporal Lineage: Duplicate payloads return DUPLICATE status; modified payloads create a new revision while preserving previous revisions.
 * 5. Single-Instance Concurrency Guard: Uses AtomicBoolean compareAndSet to ensure only one batch refresh run executes concurrently per JVM instance.
 */
@Service
@SuppressWarnings("null")
public class MonthlyHoldingsRefreshService {

    private static final Logger log = LoggerFactory.getLogger(MonthlyHoldingsRefreshService.class);

    private final PilotBootstrapService bootstrapService;
    private final MonthlyHoldingsSourceRegistry sourceRegistry;
    private final MonthlyHoldingsIngestionJob ingestionJob;
    private final SchemeOptionRepository schemeOptionRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final OfficialHoldingsDiscoveryRegistry discoveryRegistry;
    private final boolean schedulerEnabled;
    private final boolean discoveryEnabled;
    private final String configuredCron;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicReference<MonthlyHoldingsRefreshRun> lastAttemptedRun = new java.util.concurrent.atomic.AtomicReference<>();
    private final java.util.concurrent.atomic.AtomicReference<MonthlyHoldingsRefreshRun> lastSuccessfulRun = new java.util.concurrent.atomic.AtomicReference<>();

    public MonthlyHoldingsRefreshService(
        PilotBootstrapService bootstrapService,
        MonthlyHoldingsSourceRegistry sourceRegistry,
        MonthlyHoldingsIngestionJob ingestionJob
    ) {
        this(bootstrapService, sourceRegistry, ingestionJob, null, null, null, false, false, "0 0 2 12 * ?");
    }

    @org.springframework.beans.factory.annotation.Autowired
    public MonthlyHoldingsRefreshService(
        PilotBootstrapService bootstrapService,
        MonthlyHoldingsSourceRegistry sourceRegistry,
        MonthlyHoldingsIngestionJob ingestionJob,
        @org.springframework.beans.factory.annotation.Autowired(required = false) SchemeOptionRepository schemeOptionRepository,
        @org.springframework.beans.factory.annotation.Autowired(required = false) PortfolioSnapshotRepository snapshotRepository,
        @org.springframework.beans.factory.annotation.Autowired(required = false) OfficialHoldingsDiscoveryRegistry discoveryRegistry,
        @org.springframework.beans.factory.annotation.Value("${yukira.holdings.refresh.enabled:false}") boolean schedulerEnabled,
        @org.springframework.beans.factory.annotation.Value("${yukira.holdings.discovery.enabled:true}") boolean discoveryEnabled,
        @org.springframework.beans.factory.annotation.Value("${yukira.holdings.refresh.cron:0 0 2 12 * ?}") String configuredCron
    ) {
        this.bootstrapService = bootstrapService;
        this.sourceRegistry = sourceRegistry;
        this.ingestionJob = ingestionJob;
        this.schemeOptionRepository = schemeOptionRepository;
        this.snapshotRepository = snapshotRepository;
        this.discoveryRegistry = discoveryRegistry;
        this.schedulerEnabled = schedulerEnabled;
        this.discoveryEnabled = discoveryEnabled;
        this.configuredCron = configuredCron;
    }

    /**
     * Executes a batch monthly holdings refresh run with default MANUAL trigger.
     */
    @Transactional
    public MonthlyHoldingsRefreshRun executeMonthlyHoldingsRefresh() {
        return executeMonthlyHoldingsRefresh(TriggerType.MANUAL);
    }

    /**
     * Executes a batch monthly holdings refresh run across all registered mutual funds with specified trigger type.
     */
    @Transactional
    public MonthlyHoldingsRefreshRun executeMonthlyHoldingsRefresh(TriggerType triggerType) {
        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Monthly holdings refresh run skipped: another refresh run is currently in progress (Trigger: {}).", triggerType);
            OffsetDateTime now = OffsetDateTime.now();
            return new MonthlyHoldingsRefreshRun(
                "HOLDINGS_REFRESH_CONCURRENT",
                triggerType,
                now,
                now,
                0, 0, 0, 0, 0, 0,
                "CONCURRENT_RUN_SKIPPED",
                Collections.emptyList(),
                List.of("Skipped execution: a monthly holdings refresh run is already in progress.")
            );
        }

        try {
            OffsetDateTime startTime = OffsetDateTime.now();
            String runId = "HOLDINGS_REFRESH_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);

            log.info("Starting monthly holdings refresh run #{} (Trigger: {})...", runId, triggerType);
            List<String> auditLogs = new ArrayList<>();
            auditLogs.add(String.format("Initiating monthly holdings refresh run #%s (Trigger: %s)", runId, triggerType));

            // 1. Ensure production master entity registration for canonical pilot and registered funds
            ensureMasterRegistrations();

            List<MonthlyHoldingsSourceRegistry.RegisteredFundConfig> registeredFunds = sourceRegistry.getAllRegisteredFunds();
            int totalRegistered = registeredFunds.size();
            auditLogs.add(String.format("Found %d registered funds in YUKIRA source registry", totalRegistered));

            List<FundRefreshResult> fundResults = new ArrayList<>();
            int attemptedCount = 0;
            int successCount = 0;
            int duplicateCount = 0;
            int failedCount = 0;
            int skippedCount = 0;

            for (MonthlyHoldingsSourceRegistry.RegisteredFundConfig fundConfig : registeredFunds) {
                long fundStartTime = System.currentTimeMillis();

                List<MonthlyHoldingsSourceDescriptor> descriptorsToProcess = new ArrayList<>();
                if (fundConfig.allDescriptors() != null && !fundConfig.allDescriptors().isEmpty()) {
                    descriptorsToProcess.addAll(fundConfig.allDescriptors());
                } else if (fundConfig.descriptor() != null) {
                    descriptorsToProcess.add(fundConfig.descriptor());
                }

                // Dynamic official source discovery
                if (discoveryEnabled && discoveryRegistry != null) {
                    Optional<OfficialHoldingsDiscoveryClient> discovererOpt = discoveryRegistry.findDiscoverer(fundConfig.amcCode(), fundConfig.amfiCode());
                    if (discovererOpt.isPresent()) {
                        LocalDate now = LocalDate.now();
                        LocalDate windowEnd = now;
                        LocalDate windowStart = now.minusMonths(4).withDayOfMonth(1);

                        try {
                            List<DiscoveredHoldingsCandidate> discovered = discovererOpt.get().discoverDisclosures(fundConfig, windowStart, windowEnd);
                            for (DiscoveredHoldingsCandidate cand : discovered) {
                                if (cand.status() == DiscoveryStatus.DISCOVERED && cand.descriptor() != null) {
                                    boolean alreadyPresent = descriptorsToProcess.stream().anyMatch(d ->
                                        d.sourceUrl().equalsIgnoreCase(cand.descriptor().sourceUrl()) ||
                                        (d.explicitReportingDate() != null && d.explicitReportingDate().equals(cand.descriptor().explicitReportingDate()))
                                    );
                                    if (!alreadyPresent) {
                                        descriptorsToProcess.add(cand.descriptor());
                                        log.info("Refresh run #{}: Discovered official disclosure for fund '{}' on {}: {}",
                                            runId, fundConfig.schemeName(), cand.reportingDate(), cand.sourceUrl());
                                        auditLogs.add(String.format("[DISCOVERED] Fund '%s': %s (%s)",
                                            fundConfig.schemeName(), cand.reportingDate(), cand.sourceUrl()));
                                    }
                                } else if (cand.status() == DiscoveryStatus.SOURCE_UNAVAILABLE) {
                                    String msg = String.format("Source discovery unavailable for fund '%s': %s", fundConfig.schemeName(), cand.statusMessage());
                                    log.warn("Refresh run #{}: {}", runId, msg);
                                    auditLogs.add("[SOURCE_UNAVAILABLE] " + msg);
                                } else if (cand.status() == DiscoveryStatus.AMBIGUOUS_SOURCE) {
                                    String msg = String.format("Ambiguous discovery candidates for fund '%s': %s", fundConfig.schemeName(), cand.statusMessage());
                                    log.warn("Refresh run #{}: {}", runId, msg);
                                    auditLogs.add("[AMBIGUOUS_SOURCE] " + msg);
                                } else if (cand.status() == DiscoveryStatus.NOT_YET_PUBLISHED) {
                                    auditLogs.add(String.format("[NOT_YET_PUBLISHED] Fund '%s': %s", fundConfig.schemeName(), cand.statusMessage()));
                                }
                            }
                        } catch (Exception de) {
                            log.error("Refresh run #{}: Exception during discovery for fund '{}': {}", runId, fundConfig.schemeName(), de.getMessage());
                            auditLogs.add(String.format("[DISCOVERY_ERROR] Fund '%s': %s", fundConfig.schemeName(), de.getMessage()));
                        }
                    }
                }

                if (fundConfig.state() == MonthlyHoldingsSourceRegistry.FundSourceState.SOURCE_NOT_CONFIGURED || descriptorsToProcess.isEmpty()) {
                    skippedCount++;
                    String msg = String.format("Fund '%s' (AMFI: %s) is master registered, but no authoritative disclosure source endpoint is configured yet.",
                        fundConfig.schemeName(), fundConfig.amfiCode());
                    log.info("Refresh run #{}: [SKIPPED] {}", runId, msg);
                    auditLogs.add("[SKIPPED] " + msg);

                    fundResults.add(new FundRefreshResult(
                        fundConfig.amcCode(),
                        fundConfig.amfiCode(),
                        fundConfig.schemeName(),
                        null,
                        MonthlyHoldingsAcquisitionResult.Status.SOURCE_NOT_CONFIGURED,
                        msg,
                        null,
                        null,
                        null,
                        0,
                        0,
                        null,
                        System.currentTimeMillis() - fundStartTime
                    ));
                    continue;
                }

                // Execute acquisition & ingestion job for each configured historical descriptor
                for (MonthlyHoldingsSourceDescriptor descriptor : descriptorsToProcess) {
                    attemptedCount++;
                    log.info("Refresh run #{}: Processing fund '{}' (AMFI: {}) via {}", runId, fundConfig.schemeName(), fundConfig.amfiCode(), descriptor.sourceUrl());

                    try {
                        MonthlyHoldingsAcquisitionResult jobResult = ingestionJob.execute(descriptor);
                        long duration = System.currentTimeMillis() - fundStartTime;

                        FundRefreshResult fundRes = new FundRefreshResult(
                            descriptor.amcCode(),
                            descriptor.amfiCode(),
                            descriptor.schemeName(),
                            jobResult.schemeOptionId(),
                            jobResult.status(),
                            jobResult.statusMessage(),
                            jobResult.sourceArtifactId(),
                            jobResult.sha256Hash(),
                            jobResult.portfolioDate(),
                            jobResult.revisionSeq(),
                            jobResult.holdingsCount(),
                            jobResult.sumWeights(),
                            duration
                        );
                        fundResults.add(fundRes);

                        if (jobResult.status() == MonthlyHoldingsAcquisitionResult.Status.INGESTED || jobResult.status() == MonthlyHoldingsAcquisitionResult.Status.RESTATED_REVISION) {
                            successCount++;
                            auditLogs.add(String.format("[SUCCESS] Fund '%s': %d holdings ingested for %s (Revision #%d)",
                                fundConfig.schemeName(), jobResult.holdingsCount(), jobResult.portfolioDate(), jobResult.revisionSeq()));
                        } else if (jobResult.status() == MonthlyHoldingsAcquisitionResult.Status.DUPLICATE) {
                            duplicateCount++;
                            auditLogs.add(String.format("[DUPLICATE] Fund '%s': Identical holdings payload already present for date %s",
                                fundConfig.schemeName(), jobResult.portfolioDate()));
                        } else {
                            failedCount++;
                            auditLogs.add(String.format("[FAILED] Fund '%s': [%s] %s",
                                fundConfig.schemeName(), jobResult.status(), jobResult.statusMessage()));
                        }

                    } catch (Exception e) {
                        failedCount++;
                        long duration = System.currentTimeMillis() - fundStartTime;
                        String errorMsg = "Exception executing ingestion job: " + e.getMessage();
                        log.error("Refresh run #{}: Failed processing fund '{}': {}", runId, fundConfig.schemeName(), e.getMessage());
                        auditLogs.add(String.format("[FAILED] Fund '%s': %s", fundConfig.schemeName(), errorMsg));

                        fundResults.add(new FundRefreshResult(
                            descriptor.amcCode(),
                            descriptor.amfiCode(),
                            descriptor.schemeName(),
                            null,
                            MonthlyHoldingsAcquisitionResult.Status.PARSE_FAILED,
                            errorMsg,
                            null,
                            null,
                            descriptor.explicitReportingDate(),
                            0,
                            0,
                            null,
                            duration
                        ));
                    }
                }
            }

            OffsetDateTime completedAt = OffsetDateTime.now();
            String overallStatus;
            if (attemptedCount > 0 && (successCount > 0 || duplicateCount > 0) && failedCount == 0) {
                overallStatus = "SUCCESS";
            } else if ((successCount > 0 || duplicateCount > 0) && (failedCount > 0 || skippedCount > 0)) {
                overallStatus = "PARTIAL_SUCCESS";
            } else if (attemptedCount > 0 && failedCount == attemptedCount) {
                overallStatus = "FAILED";
            } else {
                overallStatus = "NO_SOURCES_CONFIGURED";
            }

            log.info("Monthly holdings refresh run #{} completed (Trigger: {}): status={}, total={}, attempted={}, success={}, duplicate={}, failed={}, skipped={}",
                runId, triggerType, overallStatus, totalRegistered, attemptedCount, successCount, duplicateCount, failedCount, skippedCount);

            MonthlyHoldingsRefreshRun run = new MonthlyHoldingsRefreshRun(
                runId,
                triggerType,
                startTime,
                completedAt,
                totalRegistered,
                attemptedCount,
                successCount,
                duplicateCount,
                failedCount,
                skippedCount,
                overallStatus,
                fundResults,
                auditLogs
            );

            lastAttemptedRun.set(run);
            if ("SUCCESS".equalsIgnoreCase(overallStatus) || "PARTIAL_SUCCESS".equalsIgnoreCase(overallStatus)) {
                lastSuccessfulRun.set(run);
            }

            return run;
        } finally {
            isRunning.set(false);
        }
    }

    /**
     * Executes refresh for a single target mutual fund by AMFI scheme code using direct raw bytes (e.g. for offline testing).
     */
    @Transactional
    public FundRefreshResult executeRefreshWithRawPayload(String amfiCode, byte[] rawBytes) {
        long startTime = System.currentTimeMillis();
        ensureMasterRegistrations();

        Optional<MonthlyHoldingsSourceRegistry.RegisteredFundConfig> configOpt = sourceRegistry.findByAmfiCode(amfiCode);
        if (configOpt.isEmpty()) {
            return new FundRefreshResult(
                null, amfiCode, "Unknown Fund #" + amfiCode, null,
                MonthlyHoldingsAcquisitionResult.Status.SCHEME_UNRESOLVED,
                "AMFI code '" + amfiCode + "' is not registered in YUKIRA source registry",
                null, null, null, 0, 0, null, System.currentTimeMillis() - startTime
            );
        }

        MonthlyHoldingsSourceRegistry.RegisteredFundConfig fundConfig = configOpt.get();
        MonthlyHoldingsSourceDescriptor descriptor = fundConfig.descriptor();
        if (descriptor == null) {
            descriptor = MonthlyHoldingsSourceDescriptor.of(
                fundConfig.amcCode(), fundConfig.amcName(), fundConfig.schemeCode(), fundConfig.schemeName(),
                amfiCode, fundConfig.isin(), "https://portal.amfiindia.com/disclosures/monthly-holdings-" + amfiCode + ".csv", null
            );
        }

        MonthlyHoldingsAcquisitionResult jobResult = ingestionJob.executeWithRawPayload(descriptor, rawBytes);
        return new FundRefreshResult(
            descriptor.amcCode(),
            amfiCode,
            fundConfig.schemeName(),
            jobResult.schemeOptionId(),
            jobResult.status(),
            jobResult.statusMessage(),
            jobResult.sourceArtifactId(),
            jobResult.sha256Hash(),
            jobResult.portfolioDate(),
            jobResult.revisionSeq(),
            jobResult.holdingsCount(),
            jobResult.sumWeights(),
            System.currentTimeMillis() - startTime
        );
    }

    private void ensureMasterRegistrations() {
        bootstrapService.ensureCanonicalPilotMaster(); // HDFC 118955
        bootstrapService.ensurePpfasMaster(); // PPFAS 122639
        bootstrapService.ensurePpfasTaxSaverMaster(); // PPFAS ELSS 147481
        bootstrapService.ensureSbiMaster();   // SBI 120503
    }

    public Optional<MonthlyHoldingsRefreshRun> getLastAttemptedRun() {
        return Optional.ofNullable(lastAttemptedRun.get());
    }

    public Optional<MonthlyHoldingsRefreshRun> getLastSuccessfulRun() {
        return Optional.ofNullable(lastSuccessfulRun.get());
    }

    public MonthlyHoldingsRefreshStatusDto getOperationalStatus() {
        OffsetDateTime now = OffsetDateTime.now();
        List<PilotFundHoldingsStatusDto> pilotStatusList = new ArrayList<>();

        List<String> pilotAmfiCodes = List.of(
            PilotBootstrapService.PILOT_AMFI_CODE,    // 118955 HDFC Flexi Cap
            PilotBootstrapService.PPFAS_AMFI_CODE,    // 122639 PPFAS Flexi Cap
            PilotBootstrapService.PPFAS_TAX_AMFI_CODE // 147481 PPFAS Tax Saver
        );

        if (schemeOptionRepository != null && snapshotRepository != null) {
            for (String amfi : pilotAmfiCodes) {
                Optional<com.yukira.backend.domain.entity.SchemeOption> opt = schemeOptionRepository.findByAmfiCode(amfi);
                if (opt.isPresent()) {
                    com.yukira.backend.domain.entity.SchemeOption option = opt.get();
                    List<com.yukira.backend.domain.entity.PortfolioSnapshot> allSnaps = snapshotRepository.findBySchemeOptionIdOrderByPortfolioDateDescRevisionSeqDesc(option.getId());
                    Optional<com.yukira.backend.domain.entity.PortfolioSnapshot> latestSnapOpt = allSnaps.isEmpty() ? Optional.empty() : Optional.of(allSnaps.get(0));

                    java.time.LocalDate latestDate = latestSnapOpt.map(com.yukira.backend.domain.entity.PortfolioSnapshot::getPortfolioDate).orElse(null);
                    Integer posCount = latestSnapOpt.map(com.yukira.backend.domain.entity.PortfolioSnapshot::getReportedHoldingsCount).orElse(0);
                    java.math.BigDecimal sumW = latestSnapOpt.map(com.yukira.backend.domain.entity.PortfolioSnapshot::getSumReportedWeights).orElse(java.math.BigDecimal.ZERO);
                    Long artifactId = latestSnapOpt.flatMap(s -> Optional.ofNullable(s.getSourceArtifact())).map(com.yukira.backend.domain.entity.SourceArtifact::getId).orElse(null);
                    String sha256 = latestSnapOpt.flatMap(s -> Optional.ofNullable(s.getSourceArtifact())).map(com.yukira.backend.domain.entity.SourceArtifact::getSha256Hash).orElse(null);

                    String freshness = "UNAVAILABLE";
                    if (latestDate != null) {
                        freshness = latestDate.isBefore(java.time.LocalDate.now().minusDays(90)) ? "STALE" : "CURRENT";
                    }

                    String provenance = "UNAVAILABLE";
                    if (latestSnapOpt.isPresent()) {
                        com.yukira.backend.domain.entity.SourceArtifact sa = latestSnapOpt.get().getSourceArtifact();
                        provenance = (sa != null && sa.getPayloadBlob() != null) ? "REAL_SOURCE_ACQUIRED" : "CONTROLLED_FIXTURE";
                    }

                    String quality = (posCount > 0 && sumW != null && sumW.compareTo(java.math.BigDecimal.ZERO) > 0) ? "VALID" : "DATA_QUALITY_LIMITED";

                    pilotStatusList.add(new PilotFundHoldingsStatusDto(
                        option.getId(),
                        option.getPlan() != null && option.getPlan().getScheme() != null && option.getPlan().getScheme().getAmc() != null
                            ? option.getPlan().getScheme().getAmc().getCode() : null,
                        option.getPlan() != null && option.getPlan().getScheme() != null
                            ? option.getPlan().getScheme().getName() : null,
                        option.getAmfiCode(),
                        option.getIsin(),
                        latestDate,
                        posCount,
                        sumW,
                        artifactId,
                        sha256,
                        freshness,
                        quality,
                        provenance,
                        allSnaps.size()
                    ));
                }
            }
        }

        return new MonthlyHoldingsRefreshStatusDto(
            schedulerEnabled,
            configuredCron,
            now,
            isRunning.get(),
            lastAttemptedRun.get(),
            lastSuccessfulRun.get(),
            pilotStatusList
        );
    }
}
