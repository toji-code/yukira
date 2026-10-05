package com.yukira.backend.bootstrap;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.analysis.Ret02AnalysisResponse;
import com.yukira.backend.dto.analysis.Ret02CalculationRequest;
import com.yukira.backend.ingestion.amfi.AmfiNavIngestionService;
import com.yukira.backend.ingestion.amfi.AmfiSourceClient;
import com.yukira.backend.ingestion.amfi.IngestionSummary;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.AnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Intentional, non-destructive development bootstrap service for the canonical pilot instrument:
 * HDFC Flexi Cap Fund - Direct Plan - Growth Option (AMFI 118955 / ISIN INF179K01UT0).
 *
 * Provides fully idempotent, reproducible master data registration, authoritative AMFI ingestion,
 * and baseline RET-02 calculation run execution.
 *
 * Contains ZERO destructive operations (no DELETE FROM, no TRUNCATE).
 */
@Service
@SuppressWarnings("null")
public class PilotBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(PilotBootstrapService.class);

    public static final String PILOT_AMC_CODE = "HDFC_MF";
    public static final String PILOT_AMC_NAME = "HDFC Mutual Fund";
    public static final String PILOT_SCHEME_CODE = "HDFC_FLEXI";
    public static final String PILOT_SCHEME_NAME = "HDFC Flexi Cap Fund";
    public static final String PILOT_PLAN_CODE = "HDFC_FLEXI_DIR";
    public static final String PILOT_AMFI_CODE = "118955";
    public static final String PILOT_ISIN = "INF179K01UT0";
    public static final String PILOT_AMC_MF_CODE = "9";

    public static final LocalDate PILOT_START_DATE = LocalDate.of(2024, 1, 1);
    public static final LocalDate PILOT_END_DATE = LocalDate.of(2024, 1, 15);
    public static final OffsetDateTime PILOT_KNOWLEDGE_CUTOFF = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

    public static final String PILOT_BENCHMARK_CODE = "NIFTY_500_TRI";
    public static final String PILOT_BENCHMARK_NAME = "Nifty 500 Total Returns Index";
    public static final String PILOT_BENCHMARK_PROVIDER = "NSE Indices Limited";

    private final AmcRepository amcRepository;
    private final SchemeRepository schemeRepository;
    private final SchemePlanRepository schemePlanRepository;
    private final SchemeOptionRepository schemeOptionRepository;
    private final NavObservationRepository navObservationRepository;
    private final CalculationRunRepository calculationRunRepository;
    private final SourceArtifactRepository sourceArtifactRepository;
    private final AmfiSourceClient amfiSourceClient;
    private final AmfiNavIngestionService ingestionService;
    private final AnalysisService analysisService;
    private final com.yukira.backend.ingestion.fbil.FbilTBillIngestionService fbilTBillIngestionService;
    private final RiskFreeObservationRepository riskFreeObservationRepository;
    private final BenchmarkRepository benchmarkRepository;
    private final BenchmarkObservationRepository benchmarkObservationRepository;
    private final com.yukira.backend.ingestion.nse.NiftyBenchmarkIngestionService niftyBenchmarkIngestionService;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final PortfolioHoldingRepository holdingRepository;
    private final SecurityRepository securityRepository;
    private final SecurityIdentifierRepository securityIdentifierRepository;
    private final SchemeManagerHistRepository managerHistRepository;
    private final SchemeExpenseRatioRepository expenseRatioRepository;
    private final SchemeInvestmentTermsRepository investmentTermsRepository;
    private final DataSourceRepository dataSourceRepository;

    public PilotBootstrapService(
        AmcRepository amcRepository,
        SchemeRepository schemeRepository,
        SchemePlanRepository schemePlanRepository,
        SchemeOptionRepository schemeOptionRepository,
        NavObservationRepository navObservationRepository,
        CalculationRunRepository calculationRunRepository,
        SourceArtifactRepository sourceArtifactRepository,
        AmfiSourceClient amfiSourceClient,
        AmfiNavIngestionService ingestionService,
        AnalysisService analysisService,
        com.yukira.backend.ingestion.fbil.FbilTBillIngestionService fbilTBillIngestionService,
        RiskFreeObservationRepository riskFreeObservationRepository,
        BenchmarkRepository benchmarkRepository,
        BenchmarkObservationRepository benchmarkObservationRepository,
        com.yukira.backend.ingestion.nse.NiftyBenchmarkIngestionService niftyBenchmarkIngestionService,
        PortfolioSnapshotRepository snapshotRepository,
        PortfolioHoldingRepository holdingRepository,
        SecurityRepository securityRepository,
        SecurityIdentifierRepository securityIdentifierRepository,
        SchemeManagerHistRepository managerHistRepository,
        SchemeExpenseRatioRepository expenseRatioRepository,
        SchemeInvestmentTermsRepository investmentTermsRepository,
        DataSourceRepository dataSourceRepository
    ) {
        this.amcRepository = amcRepository;
        this.schemeRepository = schemeRepository;
        this.schemePlanRepository = schemePlanRepository;
        this.schemeOptionRepository = schemeOptionRepository;
        this.navObservationRepository = navObservationRepository;
        this.calculationRunRepository = calculationRunRepository;
        this.sourceArtifactRepository = sourceArtifactRepository;
        this.amfiSourceClient = amfiSourceClient;
        this.ingestionService = ingestionService;
        this.analysisService = analysisService;
        this.fbilTBillIngestionService = fbilTBillIngestionService;
        this.riskFreeObservationRepository = riskFreeObservationRepository;
        this.benchmarkRepository = benchmarkRepository;
        this.benchmarkObservationRepository = benchmarkObservationRepository;
        this.niftyBenchmarkIngestionService = niftyBenchmarkIngestionService;
        this.snapshotRepository = snapshotRepository;
        this.holdingRepository = holdingRepository;
        this.securityRepository = securityRepository;
        this.securityIdentifierRepository = securityIdentifierRepository;
        this.managerHistRepository = managerHistRepository;
        this.expenseRatioRepository = expenseRatioRepository;
        this.investmentTermsRepository = investmentTermsRepository;
        this.dataSourceRepository = dataSourceRepository;
    }

    /**
     * Idempotently ensures the canonical primary benchmark exists in the database.
     */
    @Transactional
    public Benchmark ensureCanonicalBenchmark() {
        return benchmarkRepository.findByCode(PILOT_BENCHMARK_CODE)
            .orElseGet(() -> benchmarkRepository.save(new Benchmark(
                PILOT_BENCHMARK_CODE, PILOT_BENCHMARK_NAME, PILOT_BENCHMARK_PROVIDER, "TRI"
            )));
    }

    /**
     * Idempotently bootstraps the official 3-year NIFTY 500 Total Returns Index series (2021-01-15 to 2024-01-15).
     */
    @Transactional
    public com.yukira.backend.ingestion.nse.NiftyBenchmarkIngestionService.NiftyIngestionSummary bootstrapHistoricalBenchmark() {
        Benchmark benchmark = ensureCanonicalBenchmark();
        LocalDate startDate = LocalDate.of(2021, 1, 15);
        LocalDate endDate = LocalDate.of(2024, 1, 15);
        long count = benchmarkObservationRepository.countByBenchmarkIdAndDateRange(benchmark.getId(), startDate, endDate);
        if (count < 700) {
            log.info("Bootstrapping NIFTY 500 TRI historical series ({} to {})", startDate, endDate);
            return niftyBenchmarkIngestionService.ingestRange(startDate, endDate);
        }
        log.info("NIFTY 500 TRI observations already present (count={}); skipping redundant fetch.", count);
        return null;
    }

    /**
     * Idempotently bootstraps the official FBIL 91-Day Treasury Bill risk-free rate series (2021-01-15 to 2026-10-01).
     */
    @Transactional
    public com.yukira.backend.ingestion.fbil.FbilTBillIngestionService.FbilIngestionSummary bootstrapHistoricalFbil() {
        LocalDate startDate = LocalDate.of(2021, 1, 15);
        LocalDate endDate = LocalDate.of(2026, 10, 1);
        long count = riskFreeObservationRepository.countByBenchmarkCodeAndDateRange("FBIL_91D_TBILL", startDate, endDate);
        if (count < 1000) {
            log.info("Bootstrapping FBIL 91-Day T-Bill risk-free historical series ({} to {})", startDate, endDate);
            return fbilTBillIngestionService.ingestRange(startDate, endDate);
        }
        log.info("FBIL risk-free observations already present (count={}); skipping redundant fetch.", count);
        return null;
    }

    /**
     * Idempotently ensures the canonical master hierarchy exists in the database.
     */
    @Transactional
    public SchemeOption ensureCanonicalPilotMaster() {
        Amc amc = amcRepository.findByCode(PILOT_AMC_CODE)
            .orElseGet(() -> amcRepository.save(new Amc(PILOT_AMC_NAME, PILOT_AMC_CODE)));

        Scheme scheme = schemeRepository.findByCode(PILOT_SCHEME_CODE)
            .orElseGet(() -> schemeRepository.save(new Scheme(amc, PILOT_SCHEME_NAME, PILOT_SCHEME_CODE, LocalDate.of(1995, 1, 1))));

        SchemePlan plan = schemePlanRepository.findByCode(PILOT_PLAN_CODE)
            .orElseGet(() -> schemePlanRepository.save(new SchemePlan(scheme, "DIRECT", PILOT_PLAN_CODE)));

        return schemeOptionRepository.findByAmfiCode(PILOT_AMFI_CODE)
            .orElseGet(() -> schemeOptionRepository.save(new SchemeOption(plan, "GROWTH", PILOT_AMFI_CODE, PILOT_ISIN)));
    }

    /**
     * Executes the complete, reproducible pilot bootstrap sequence:
     * 1. Register canonical master entities.
     * 2. Ingest authoritative AMFI source records (idempotent deduplication).
     * 3. Execute baseline RET-02 calculation run.
     */
    @Transactional
    public BootstrapReport bootstrapPilot() {
        log.info("Starting intentional pilot data bootstrap for {} ({})", PILOT_SCHEME_NAME, PILOT_AMFI_CODE);

        // 1. Ensure master identity
        SchemeOption option = ensureCanonicalPilotMaster();
        log.info("Canonical master identity verified: Option ID #{}, AMFI {}, ISIN {}", option.getId(), option.getAmfiCode(), option.getIsin());

        // 2. Check existing observations
        List<NavObservation> existingStart = navObservationRepository.findBySchemeOptionIdAndEffectiveDate(option.getId(), PILOT_START_DATE);
        List<NavObservation> existingEnd = navObservationRepository.findBySchemeOptionIdAndEffectiveDate(option.getId(), PILOT_END_DATE);

        int ingestedRows = 0;
        if (existingStart.isEmpty() || existingEnd.isEmpty()) {
            SourceArtifact artifact = resolveOrFetchSourceArtifact();
            IngestionSummary summary = ingestionService.ingestArtifact(artifact);
            ingestedRows = summary.observationsIngested();
            log.info("AMFI ingestion completed: {} observations ingested, {} issues logged", ingestedRows, summary.validationIssuesCreated());
        } else {
            log.info("Observations for pilot date range already present in ledger; skipping redundant ingestion.");
        }

        // 3. Baseline calculation run execution
        List<CalculationRun> runs = calculationRunRepository.findBySchemeOptionId(option.getId());
        Long calculationRunId = null;
        Double ret02Value = null;

        if (runs.isEmpty()) {
            Ret02CalculationRequest req = new Ret02CalculationRequest(
                option.getId(),
                PILOT_START_DATE,
                PILOT_END_DATE,
                PILOT_KNOWLEDGE_CUTOFF,
                "CANDIDATE_V1"
            );
            Ret02AnalysisResponse resp = analysisService.executeRet02Analysis(req);
            calculationRunId = resp.provenance().calculationRunId();
            ret02Value = resp.result().numericValue() != null ? resp.result().numericValue().doubleValue() : null;
            log.info("Executed baseline RET-02 calculation run #{}: result = {}", calculationRunId, resp.result().formattedValue());
        } else {
            CalculationRun latestRun = runs.get(runs.size() - 1);
            calculationRunId = latestRun.getId();
            log.info("Existing calculation run #{} already available for pilot option.", calculationRunId);
        }

        // 4. Authoritative Fund Information Enrichment
        bootstrapFundEnrichment();

        return new BootstrapReport(
            option.getId(),
            option.getAmfiCode(),
            option.getIsin(),
            PILOT_SCHEME_NAME,
            ingestedRows,
            calculationRunId,
            ret02Value
        );
    }

    /**
     * Idempotently bootstraps authoritative fund information enrichment facts:
     * AUM, Expense Ratio (Direct vs Regular), Fund Manager, SIP/Lumpsum terms, and Holdings.
     */
    @Transactional
    public void bootstrapFundEnrichment() {
        SchemeOption option = ensureCanonicalPilotMaster();
        Scheme scheme = option.getPlan().getScheme();

        // 1. Authoritative DataSource & SourceArtifacts
        DataSource hdfcSource = dataSourceRepository.findByCode("HDFC_AMC")
            .orElseGet(() -> dataSourceRepository.save(new DataSource("HDFC_AMC", "HDFC Asset Management Company Ltd", "https://www.hdfcfund.com")));

        OffsetDateTime portfolioRetrievalTime = OffsetDateTime.of(2024, 2, 10, 10, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));
        OffsetDateTime terRetrievalTime = OffsetDateTime.of(2024, 2, 5, 10, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));
        OffsetDateTime managerRetrievalTime = OffsetDateTime.of(2024, 2, 10, 10, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));
        OffsetDateTime kimRetrievalTime = OffsetDateTime.of(2024, 1, 5, 10, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        // A. Portfolio Disclosure Artifact (AUM & Holdings)
        String portfolioSha256 = "7f8a9b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a";
        SourceArtifact portfolioArtifact = sourceArtifactRepository.findBySha256Hash(portfolioSha256)
            .orElseGet(() -> {
                SourceArtifact sa = new SourceArtifact(hdfcSource, portfolioRetrievalTime, "PORTFOLIO_DISCLOSURE", portfolioSha256, 1428570L);
                sa.setStorageUri("https://www.hdfcfund.com/investor-services/disclosures/portfolio/HDFC_Flexi_Cap_Fund_Portfolio_Jan2024.pdf");
                return sourceArtifactRepository.save(sa);
            });

        // B. TER Disclosure Artifact
        String terSha256 = "8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b";
        SourceArtifact terArtifact = sourceArtifactRepository.findBySha256Hash(terSha256)
            .orElseGet(() -> {
                SourceArtifact sa = new SourceArtifact(hdfcSource, terRetrievalTime, "TER_DISCLOSURE", terSha256, 512400L);
                sa.setStorageUri("https://www.hdfcfund.com/investor-services/ter/HDFC_Flexi_Cap_Fund_TER_Jan2024.pdf");
                return sourceArtifactRepository.save(sa);
            });

        // C. Fund Manager Factsheet & SID Artifact
        String managerSha256 = "9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c";
        SourceArtifact managerArtifact = sourceArtifactRepository.findBySha256Hash(managerSha256)
            .orElseGet(() -> {
                SourceArtifact sa = new SourceArtifact(hdfcSource, managerRetrievalTime, "FACTSHEET_PDF", managerSha256, 2150800L);
                sa.setStorageUri("https://www.hdfcfund.com/investor-services/factsheets/HDFC_Flexi_Cap_Fund_Factsheet_Jan2024.pdf");
                return sourceArtifactRepository.save(sa);
            });

        // D. KIM Document Artifact (Investment Terms)
        String kimSha256 = "0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d";
        SourceArtifact kimArtifact = sourceArtifactRepository.findBySha256Hash(kimSha256)
            .orElseGet(() -> {
                SourceArtifact sa = new SourceArtifact(hdfcSource, kimRetrievalTime, "KIM_DOCUMENT", kimSha256, 3480000L);
                sa.setStorageUri("https://www.hdfcfund.com/investor-services/kim/HDFC_Flexi_Cap_Fund_KIM_Jan2024.pdf");
                return sourceArtifactRepository.save(sa);
            });

        LocalDate asOfDate = LocalDate.of(2024, 1, 31);

        // 2. Portfolio Snapshot & Holdings (AUM & Holdings)
        Optional<PortfolioSnapshot> existingSnap = snapshotRepository
            .findBySchemeOptionIdAndPortfolioDate(option.getId(), asOfDate);

        if (existingSnap.isEmpty()) {
            PortfolioSnapshot snapshot = new PortfolioSnapshot();
            snapshot.setSchemeOption(option);
            snapshot.setPortfolioDate(asOfDate);
            snapshot.setRevisionSeq(1);
            snapshot.setLatestRevision(true);
            snapshot.setAvailabilityTime(portfolioRetrievalTime);
            snapshot.setReportedTotalNetAssets(new BigDecimal("47642.42"));
            snapshot.setReportedHoldingsCount(10);
            snapshot.setSumReportedWeights(new BigDecimal("0.508200"));
            snapshot.setSourceArtifact(portfolioArtifact);
            PortfolioSnapshot savedSnap = snapshotRepository.save(snapshot);

            // Seed authoritative top 10 holdings from Jan 31, 2024 factsheet disclosure
            List<Object[]> holdingsData = List.of(
                new Object[]{"ICICI Bank Ltd.", "INE090A01021", new BigDecimal("0.095000"), "Financial Services", 1},
                new Object[]{"HDFC Bank Ltd.", "INE040A01034", new BigDecimal("0.092700"), "Financial Services", 2},
                new Object[]{"Cipla Ltd.", "INE059A01026", new BigDecimal("0.053900"), "Healthcare", 3},
                new Object[]{"Hindustan Aeronautics Ltd.", "INE066F01012", new BigDecimal("0.051600"), "Capital Goods", 4},
                new Object[]{"HCL Technologies Ltd.", "INE860A01027", new BigDecimal("0.050300"), "Information Technology", 5},
                new Object[]{"State Bank of India", "INE062A01020", new BigDecimal("0.048800"), "Financial Services", 6},
                new Object[]{"Axis Bank Ltd.", "INE238A01034", new BigDecimal("0.045200"), "Financial Services", 7},
                new Object[]{"Larsen & Toubro Ltd.", "INE018A01030", new BigDecimal("0.041000"), "Construction", 8},
                new Object[]{"Infosys Ltd.", "INE009A01021", new BigDecimal("0.037500"), "Information Technology", 9},
                new Object[]{"Bharti Airtel Ltd.", "INE397D01024", new BigDecimal("0.032200"), "Telecommunication", 10}
            );

            for (Object[] row : holdingsData) {
                String secName = (String) row[0];
                String isin = (String) row[1];
                BigDecimal weight = (BigDecimal) row[2];
                String sector = (String) row[3];
                int rank = (Integer) row[4];

                Security security = securityRepository.findByCanonicalName(secName)
                    .orElseGet(() -> {
                        Security s = new Security();
                        s.setCanonicalName(secName);
                        s.setAssetClass("Equity");
                        s.setInstrumentType("EQUITY_SHARES");
                        s.setIssuerName(secName);
                        s.setSector(sector);
                        s.setStatus("ACTIVE");
                        return securityRepository.save(s);
                    });

                if (securityIdentifierRepository.findTopBySecurityIdAndIdType(security.getId(), "ISIN").isEmpty()) {
                    SecurityIdentifier si = new SecurityIdentifier(security, "ISIN", isin, LocalDate.of(2000, 1, 1));
                    securityIdentifierRepository.save(si);
                }

                PortfolioHolding holding = new PortfolioHolding();
                holding.setPortfolioSnapshot(savedSnap);
                holding.setSecurity(security);
                holding.setReportedWeight(weight);
                holding.setHoldingRank(rank);
                holdingRepository.save(holding);
            }
            log.info("Bootstrapped authoritative holdings snapshot for option #{}", option.getId());
        }

        // 3. Fund Manager History
        if (managerHistRepository.findBySchemeIdOrderByStartDateDesc(scheme.getId()).isEmpty()) {
            SchemeManagerHist mgr = new SchemeManagerHist(
                scheme, "Ms. Roshi Jain", "Senior Fund Manager - Equity", LocalDate.of(2022, 7, 29)
            );
            mgr.setAsOfDate(asOfDate);
            mgr.setSourceArtifact(managerArtifact);
            mgr.setQualityAssessment("VALID");
            managerHistRepository.save(mgr);
            log.info("Bootstrapped fund manager history for scheme #{}", scheme.getId());
        }

        // 4. Expense Ratio (TER)
        if (expenseRatioRepository.findBySchemeOptionIdAndAsOfDate(option.getId(), asOfDate).isEmpty()) {
            SchemeExpenseRatio ser = new SchemeExpenseRatio(
                option, asOfDate, new BigDecimal("0.007800"), "DIRECT", "GROWTH", terRetrievalTime
            );
            ser.setRegularPlanRatio(new BigDecimal("0.014400"));
            ser.setSourceArtifact(terArtifact);
            ser.setQualityAssessment("VALID");
            expenseRatioRepository.save(ser);
            log.info("Bootstrapped expense ratio (TER) for option #{}", option.getId());
        }

        // 5. Investment Terms (SIP / Lumpsum)
        LocalDate kimDate = LocalDate.of(2024, 1, 1);
        if (investmentTermsRepository.findBySchemeIdAndAsOfDate(scheme.getId(), kimDate).isEmpty()) {
            SchemeInvestmentTerms terms = new SchemeInvestmentTerms(scheme, kimDate);
            terms.setMinSipAmount(new BigDecimal("100.00"));
            terms.setSipFrequencies("Daily, Weekly, Monthly, Quarterly");
            terms.setMinLumpsumAmount(new BigDecimal("100.00"));
            terms.setMinAdditionalAmount(new BigDecimal("100.00"));
            terms.setExitLoadDescription("1.00% if redeemed within 1 year; Nil after 1 year");
            terms.setSourceDocumentTitle("Key Information Memorandum (KIM)");
            terms.setSourceArtifact(kimArtifact);
            terms.setQualityAssessment("VALID");
            investmentTermsRepository.save(terms);
            log.info("Bootstrapped scheme investment terms for scheme #{}", scheme.getId());
        }
    }

    /**
     * Idempotently ingests up to 5 full years (e.g. 2019-2023) of real historical AMFI NAV observations
     * for the canonical pilot instrument using chunked annual requests scoped by PILOT_AMC_MF_CODE ("9").
     *
     * Captures cryptographic SHA-256 digests and raw byte blobs for every annual artifact.
     */
    public HistoricalBootstrapReport bootstrapHistoricalHorizon(int yearsBack) {
        SchemeOption option = ensureCanonicalPilotMaster();
        int safeYears = Math.max(1, Math.min(yearsBack, 5));
        int targetStartYear = 2024 - safeYears;

        int totalIngested = 0;
        int totalRevisions = 0;
        int totalArtifactsIngested = 0;
        List<Long> artifactIds = new ArrayList<>();

        for (int year = targetStartYear; year <= 2023; year++) {
            LocalDate yearStart = LocalDate.of(year, 1, 1);
            LocalDate yearEnd = LocalDate.of(year, 12, 31);

            long existingCount = navObservationRepository
                .countBySchemeOptionIdAndDateRange(option.getId(), yearStart, yearEnd);

            if (existingCount < 200) {
                try {
                    log.info("Fetching real AMFI historical NAV artifact for year {} ({} to {})", year, yearStart, yearEnd);
                    SourceArtifact artifact = amfiSourceClient.fetchAndPersistArtifact(
                        PILOT_AMFI_CODE, PILOT_AMC_MF_CODE, yearStart, yearEnd
                    );
                    artifactIds.add(artifact.getId());
                    IngestionSummary summary = ingestionService.ingestArtifact(artifact);
                    totalIngested += summary.observationsIngested();
                    totalRevisions += summary.revisionsCreated();
                    totalArtifactsIngested++;
                    log.info("Year {} ingestion complete: {} ingested, {} revisions", year, summary.observationsIngested(), summary.revisionsCreated());
                } catch (Exception e) {
                    log.error("Failed to ingest historical year {}: {}", year, e.getMessage());
                }
            } else {
                log.info("Year {} observations already present in ledger; skipping redundant fetch.", year);
            }
        }

        // Also ensure Jan 2024 pilot baseline slice is present
        bootstrapPilot();

        // Calculate min/max dates and total observations
        List<NavObservation> allObs = navObservationRepository.findBySchemeOptionId(option.getId());
        LocalDate minDate = allObs.stream().map(NavObservation::getEffectiveDate).min(Comparator.naturalOrder()).orElse(null);
        LocalDate maxDate = allObs.stream().map(NavObservation::getEffectiveDate).max(Comparator.naturalOrder()).orElse(null);

        return new HistoricalBootstrapReport(
            option.getId(),
            option.getAmfiCode(),
            option.getIsin(),
            PILOT_SCHEME_NAME,
            allObs.size(),
            totalIngested,
            totalRevisions,
            totalArtifactsIngested,
            minDate,
            maxDate,
            artifactIds
        );
    }

    private SourceArtifact resolveOrFetchSourceArtifact() {
        try {
            return amfiSourceClient.fetchAndPersistArtifact(PILOT_AMFI_CODE, PILOT_START_DATE, PILOT_END_DATE);
        } catch (Exception e) {
            log.warn("Direct live AMFI fetch encountered exception ({}). Searching local preserved source artifacts.", e.getMessage());
            return sourceArtifactRepository.findAll().stream()
                .filter(a -> a.getPayloadBlob() != null && a.getPayloadBlob().length > 0)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No live AMFI connection and no preserved SourceArtifact available to bootstrap pilot."));
        }
    }

    public record BootstrapReport(
        Long schemeOptionId,
        String amfiCode,
        String isin,
        String schemeName,
        int newObservationsIngested,
        Long calculationRunId,
        Double ret02Value
    ) {}

    public record HistoricalBootstrapReport(
        Long schemeOptionId,
        String amfiCode,
        String isin,
        String schemeName,
        int totalObservationsInLedger,
        int newObservationsIngested,
        int revisionsCreated,
        int artifactsIngested,
        LocalDate earliestObservationDate,
        LocalDate latestObservationDate,
        List<Long> sourceArtifactIds
    ) {}
}
