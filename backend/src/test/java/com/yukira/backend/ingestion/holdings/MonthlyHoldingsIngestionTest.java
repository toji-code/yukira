package com.yukira.backend.ingestion.holdings;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonthlyHoldingsIngestionTest {

    @Mock
    private SchemeOptionRepository schemeOptionRepository;

    @Mock
    private PortfolioSnapshotRepository snapshotRepository;

    @Mock
    private PortfolioHoldingRepository holdingRepository;

    @Mock
    private SecurityRepository securityRepository;

    @Mock
    private SecurityIdentifierRepository securityIdentifierRepository;

    @Mock
    private SourceArtifactRepository sourceArtifactRepository;

    @Mock
    private DataSourceRepository dataSourceRepository;

    @Mock
    private ValidationIssueRepository validationIssueRepository;

    @Mock
    private com.yukira.backend.bootstrap.PilotBootstrapService bootstrapService;

    private MonthlyHoldingsCsvParser parser;
    private MonthlyHoldingsIngestionService ingestionService;
    private AuthoritativeHoldingsSourceFetcher sourceFetcher;
    private MonthlyHoldingsIngestionJob ingestionJob;
    private MonthlyHoldingsSourceRegistry sourceRegistry;
    private MonthlyHoldingsRefreshService refreshService;

    @BeforeEach
    void setUp() {
        parser = new MonthlyHoldingsCsvParser();
        MonthlyHoldingsParserRegistry parserRegistry = new MonthlyHoldingsParserRegistry(List.of(
            new PpfasXlsxHoldingsParser(),
            parser
        ));
        ingestionService = new MonthlyHoldingsIngestionService(
            parserRegistry,
            schemeOptionRepository,
            snapshotRepository,
            holdingRepository,
            securityRepository,
            securityIdentifierRepository,
            sourceArtifactRepository,
            dataSourceRepository,
            validationIssueRepository
        );
        sourceFetcher = new AuthoritativeHoldingsSourceFetcher(sourceArtifactRepository, dataSourceRepository);
        ingestionJob = new MonthlyHoldingsIngestionJob(schemeOptionRepository, sourceFetcher, ingestionService);
        sourceRegistry = new MonthlyHoldingsSourceRegistry();
        refreshService = new MonthlyHoldingsRefreshService(bootstrapService, sourceRegistry, ingestionJob);
    }

    @Test
    void testParserParsesValidCsvPayload() {
        String csv = """
            SchemeCode,SecurityName,ISIN,Sector,AssetClass,Quantity,MarketValue,Weight,Date
            118955,ICICI Bank Ltd.,INE090A01021,Financial Services,Equity,50000,475.50,9.50%,2024-02-29
            118955,HDFC Bank Ltd.,INE040A01034,Financial Services,Equity,45000,435.20,8.70%,2024-02-29
            """;

        List<MonthlyHoldingsRecord> records = parser.parse(csv.getBytes(StandardCharsets.UTF_8));
        assertEquals(2, records.size());

        MonthlyHoldingsRecord rec1 = records.get(0);
        assertTrue(rec1.isValid());
        assertEquals("118955", rec1.schemeCode());
        assertEquals("ICICI Bank Ltd.", rec1.securityName());
        assertEquals("INE090A01021", rec1.isin());
        assertEquals("Financial Services", rec1.sector());
        assertEquals(new BigDecimal("0.095000"), rec1.weight());
        assertEquals(LocalDate.of(2024, 2, 29), rec1.portfolioDate());
    }

    @Test
    void testIngestionCreatesSnapshotAndHoldingsWithProvenance() {
        SchemeOption option = createMockOption(10189L, "118955");
        when(schemeOptionRepository.findById(10189L)).thenReturn(Optional.of(option));
        when(dataSourceRepository.findByCode("SEBI_AMC_DISCLOSURE")).thenReturn(Optional.of(new DataSource("SEBI_AMC_DISCLOSURE", "SEBI Feed", "https://amfi.com")));
        when(sourceArtifactRepository.findBySha256Hash(any())).thenReturn(Optional.empty());
        when(sourceArtifactRepository.save(any())).thenAnswer(invocation -> {
            SourceArtifact sa = invocation.getArgument(0);
            sa.setId(500L);
            return sa;
        });

        when(snapshotRepository.findBySchemeOptionIdAndPortfolioDate(eq(10189L), any())).thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenAnswer(invocation -> {
            PortfolioSnapshot ps = invocation.getArgument(0);
            ps.setId(1000L);
            return ps;
        });

        when(securityRepository.findTopByCanonicalNameOrderByIdAsc(any())).thenReturn(Optional.empty());
        when(securityRepository.save(any())).thenAnswer(invocation -> {
            Security s = invocation.getArgument(0);
            s.setId(2000L);
            return s;
        });

        String csv = """
            SchemeCode,SecurityName,ISIN,Sector,AssetClass,Quantity,MarketValue,Weight,Date
            118955,ICICI Bank Ltd.,INE090A01021,Financial Services,Equity,50000,475.50,9.50%,2024-02-29
            118955,Cipla Ltd.,INE059A01026,Healthcare,Equity,20000,210.00,4.20%,2024-02-29
            """;

        HoldingsIngestionSummary summary = ingestionService.ingestMonthlyHoldings(
            10189L, LocalDate.of(2024, 2, 29), csv.getBytes(StandardCharsets.UTF_8), "http://hdfcfund.com/disclosure.csv"
        );

        assertNotNull(summary);
        assertEquals(10189L, summary.schemeOptionId());
        assertEquals(LocalDate.of(2024, 2, 29), summary.portfolioDate());
        assertEquals(2, summary.holdingsIngested());
        assertEquals(new BigDecimal("0.137000"), summary.sumReportedWeights());

        verify(snapshotRepository, times(1)).save(any(PortfolioSnapshot.class));
        verify(holdingRepository, times(1)).saveAll(any());
    }

    @Test
    void testIdempotentIngestionSkipsDuplicateArtifact() {
        SchemeOption option = createMockOption(10189L, "118955");
        when(schemeOptionRepository.findById(10189L)).thenReturn(Optional.of(option));
        when(dataSourceRepository.findByCode(any())).thenReturn(Optional.of(new DataSource("SEBI", "SEBI", "http://amfi.com")));

        String csv = """
            SchemeCode,SecurityName,ISIN,Sector,AssetClass,Quantity,MarketValue,Weight,Date
            118955,ICICI Bank Ltd.,INE090A01021,Financial Services,Equity,50000,475.50,9.50%,2024-02-29
            """;

        byte[] payload = csv.getBytes(StandardCharsets.UTF_8);

        SourceArtifact artifact = new SourceArtifact();
        artifact.setId(500L);
        artifact.setSha256Hash("mockhash123");
        artifact.setPayloadBlob(payload);
        when(sourceArtifactRepository.findBySha256Hash(any())).thenReturn(Optional.of(artifact));

        PortfolioSnapshot existingSnapshot = new PortfolioSnapshot();
        existingSnapshot.setId(1000L);
        existingSnapshot.setSchemeOption(option);
        existingSnapshot.setPortfolioDate(LocalDate.of(2024, 2, 29));
        existingSnapshot.setSourceArtifact(artifact);
        existingSnapshot.setReportedHoldingsCount(1);
        existingSnapshot.setSumReportedWeights(new BigDecimal("0.095000"));

        when(snapshotRepository.findBySchemeOptionIdAndPortfolioDate(10189L, LocalDate.of(2024, 2, 29)))
            .thenReturn(Optional.of(existingSnapshot));

        HoldingsIngestionSummary summary = ingestionService.ingestMonthlyHoldings(10189L, LocalDate.of(2024, 2, 29), payload, "uri");

        assertNotNull(summary);
        assertEquals(0, summary.holdingsIngested());
        assertTrue(summary.messages().stream().anyMatch(m -> m.contains("skipping redundant parse")));
        verify(holdingRepository, never()).saveAll(any());
    }

    @Test
    void testParserHandlesQuotedCommasAndDecimalWeights() {
        String csv = """
            SchemeCode,SecurityName,ISIN,Sector,AssetClass,Quantity,MarketValue,Weight,Date
            118955,"Larsen & Toubro Ltd., Construction Division",INE018A01030,"Capital Goods, Heavy",Equity,15000,180.50,0.0410,2024-01-31
            """;

        List<MonthlyHoldingsRecord> records = parser.parse(csv.getBytes(StandardCharsets.UTF_8));
        assertEquals(1, records.size());

        MonthlyHoldingsRecord rec = records.get(0);
        assertTrue(rec.isValid());
        assertEquals("Larsen & Toubro Ltd., Construction Division", rec.securityName());
        assertEquals("INE018A01030", rec.isin());
        assertEquals("Capital Goods, Heavy", rec.sector());
        assertEquals(new BigDecimal("0.0410"), rec.weight());
        assertEquals(LocalDate.of(2024, 1, 31), rec.portfolioDate());
    }

    @Test
    void testIngestionCreatesRevisionSequenceOnRestatement() {
        SchemeOption option = createMockOption(10189L, "118955");
        when(schemeOptionRepository.findById(10189L)).thenReturn(Optional.of(option));
        when(dataSourceRepository.findByCode(any())).thenReturn(Optional.of(new DataSource("SEBI", "SEBI", "http://amfi.com")));

        String originalCsv = """
            SchemeCode,SecurityName,ISIN,Sector,AssetClass,Quantity,MarketValue,Weight,Date
            118955,ICICI Bank Ltd.,INE090A01021,Financial Services,Equity,50000,475.50,9.50%,2024-01-31
            """;
        byte[] originalPayload = originalCsv.getBytes(StandardCharsets.UTF_8);

        SourceArtifact newArtifact = new SourceArtifact();
        newArtifact.setId(501L);
        newArtifact.setSha256Hash("newhash456");
        newArtifact.setPayloadBlob(originalPayload);
        when(sourceArtifactRepository.findBySha256Hash(any())).thenReturn(Optional.of(newArtifact));


        PortfolioSnapshot existingSnapshot = new PortfolioSnapshot();
        existingSnapshot.setId(1000L);
        existingSnapshot.setSchemeOption(option);
        existingSnapshot.setPortfolioDate(LocalDate.of(2024, 1, 31));
        SourceArtifact oldArtifact = new SourceArtifact();
        oldArtifact.setId(500L);
        oldArtifact.setSha256Hash("oldhash123");
        existingSnapshot.setSourceArtifact(oldArtifact);
        existingSnapshot.setRevisionSeq(1);
        existingSnapshot.setLatestRevision(true);

        when(snapshotRepository.findBySchemeOptionIdAndPortfolioDate(10189L, LocalDate.of(2024, 1, 31)))
            .thenReturn(Optional.of(existingSnapshot));
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(securityRepository.findTopByCanonicalNameOrderByIdAsc(any())).thenReturn(Optional.empty());
        when(securityRepository.save(any())).thenAnswer(inv -> {
            Security s = inv.getArgument(0);
            s.setId(3000L);
            return s;
        });

        HoldingsIngestionSummary summary = ingestionService.ingestMonthlyHoldings(10189L, LocalDate.of(2024, 1, 31), originalPayload, "uri");

        assertNotNull(summary);
        assertEquals(1, summary.revisionsCreated());
        assertFalse(existingSnapshot.getLatestRevision());
        verify(snapshotRepository, times(2)).save(any(PortfolioSnapshot.class));
    }

    @Test
    void testValidationLogsIssuesForNegativeWeightAndDuplicateIsin() {
        SchemeOption option = createMockOption(10189L, "118955");
        when(schemeOptionRepository.findById(10189L)).thenReturn(Optional.of(option));
        when(dataSourceRepository.findByCode(any())).thenReturn(Optional.of(new DataSource("SEBI", "SEBI", "http://amfi.com")));
        when(sourceArtifactRepository.findBySha256Hash(any())).thenReturn(Optional.empty());
        when(sourceArtifactRepository.save(any())).thenAnswer(inv -> {
            SourceArtifact sa = inv.getArgument(0);
            sa.setId(500L);
            return sa;
        });
        when(snapshotRepository.findBySchemeOptionIdAndPortfolioDate(eq(10189L), any())).thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenAnswer(inv -> {
            PortfolioSnapshot ps = inv.getArgument(0);
            ps.setId(1000L);
            return ps;
        });

        String csvWithIssues = """
            SchemeCode,SecurityName,ISIN,Sector,AssetClass,Quantity,MarketValue,Weight,Date
            118955,Invalid Asset,INE000A01010,Other,Equity,100,10.0,-2.50%,2024-01-31
            118955,Duplicate Asset 1,INE090A01021,Financial Services,Equity,50000,475.50,9.50%,2024-01-31
            118955,Duplicate Asset 2,INE090A01021,Financial Services,Equity,50000,475.50,9.50%,2024-01-31
            """;

        HoldingsIngestionSummary summary = ingestionService.ingestMonthlyHoldings(
            10189L, LocalDate.of(2024, 1, 31), csvWithIssues.getBytes(StandardCharsets.UTF_8), "uri"
        );

        assertNotNull(summary);
        assertTrue(summary.validationIssuesCreated() >= 2);
        verify(validationIssueRepository, atLeast(2)).save(any(ValidationIssue.class));
    }

    @Test
    void testParserHandlesSecondAmcFormatWithDifferentHeaderOrderAndForeignAssets() {
        String ppfasCsv = """
            Portfolio Date,ISIN,Company Name,Industry,Quantity,Market Value (INR Cr),% to NAV
            2024-01-31,INE040A01034,HDFC Bank Ltd.,Financial Services,1200000,175.40,8.02%
            2024-01-31,,Alphabet Inc. Class A,Overseas Equity,250000,123.50,5.64%
            2024-01-31,INE090A01021,ICICI Bank Ltd.,Financial Services,850000,138.90,6.35%
            """;

        List<MonthlyHoldingsRecord> records = parser.parse(ppfasCsv.getBytes(StandardCharsets.UTF_8));
        assertEquals(3, records.size());

        MonthlyHoldingsRecord r1 = records.get(0);
        assertTrue(r1.isValid());
        assertEquals("HDFC Bank Ltd.", r1.securityName());
        assertEquals("INE040A01034", r1.isin());
        assertEquals(new BigDecimal("0.080200"), r1.weight());

        MonthlyHoldingsRecord r2 = records.get(1);
        assertTrue(r2.isValid());
        assertEquals("Alphabet Inc. Class A", r2.securityName());
        assertNull(r2.isin()); // Foreign holding without Indian INE ISIN
        assertEquals(new BigDecimal("0.056400"), r2.weight());
    }

    @Test
    void testSecurityIdentityResolutionCollisionSafety() {
        // Mock existing security with ISIN_A
        Security existingSec = new Security();
        existingSec.setId(10L);
        existingSec.setCanonicalName("ABC Power Ltd.");

        SecurityIdentifier existingSi = new SecurityIdentifier(existingSec, "ISIN", "INE111A01010", LocalDate.of(2000, 1, 1));
        when(securityIdentifierRepository.findTopByIdTypeAndIdValueOrderByIdAsc("ISIN", "INE222B01020")).thenReturn(Optional.empty());
        when(securityRepository.findTopByCanonicalNameOrderByIdAsc("ABC Power Ltd.")).thenReturn(Optional.of(existingSec));
        when(securityIdentifierRepository.findTopBySecurityIdAndIdType(10L, "ISIN")).thenReturn(Optional.of(existingSi));

        // Ingest row with same name "ABC Power Ltd." BUT different ISIN "INE222B01020"
        when(schemeOptionRepository.findById(10189L)).thenReturn(Optional.of(createMockOption(10189L, "118955")));
        when(dataSourceRepository.findByCode(any())).thenReturn(Optional.of(new DataSource("SEBI", "SEBI", "http://amfi.com")));
        when(sourceArtifactRepository.findBySha256Hash(any())).thenReturn(Optional.empty());
        when(sourceArtifactRepository.save(any())).thenAnswer(inv -> {
            SourceArtifact sa = inv.getArgument(0);
            sa.setId(500L);
            return sa;
        });
        when(snapshotRepository.findBySchemeOptionIdAndPortfolioDate(eq(10189L), any())).thenReturn(Optional.empty());

        when(securityRepository.save(any())).thenAnswer(inv -> {
            Security s = inv.getArgument(0);
            s.setId(20L);
            return s;
        });

        String csv = """
            SchemeCode,SecurityName,ISIN,Sector,AssetClass,Quantity,MarketValue,Weight,Date
            118955,ABC Power Ltd.,INE222B01020,Utilities,Equity,1000,10.0,1.00%,2024-01-31
            """;

        HoldingsIngestionSummary summary = ingestionService.ingestMonthlyHoldings(10189L, LocalDate.of(2024, 1, 31), csv.getBytes(StandardCharsets.UTF_8), "uri");
        assertNotNull(summary);

        // Verify distinct Security (id 20L) was created rather than merging into conflicting Security (id 10L)
        verify(securityRepository, times(1)).save(any(Security.class));
    }

    @Test
    void testCrossFundIsolationAndMultiFundIngestion() {
        SchemeOption optionA = createMockOption(10001L, "118955"); // HDFC
        SchemeOption optionB = createMockOption(10002L, "122639"); // PPFAS

        when(schemeOptionRepository.findById(10001L)).thenReturn(Optional.of(optionA));
        when(schemeOptionRepository.findById(10002L)).thenReturn(Optional.of(optionB));
        when(dataSourceRepository.findByCode(any())).thenReturn(Optional.of(new DataSource("SEBI", "SEBI", "http://amfi.com")));
        when(sourceArtifactRepository.findBySha256Hash(any())).thenReturn(Optional.empty());
        when(sourceArtifactRepository.save(any())).thenAnswer(inv -> {
            SourceArtifact sa = inv.getArgument(0);
            sa.setId(500L);
            return sa;
        });
        when(snapshotRepository.findBySchemeOptionIdAndPortfolioDate(any(), any())).thenReturn(Optional.empty());

        when(snapshotRepository.save(any())).thenAnswer(inv -> {
            PortfolioSnapshot ps = inv.getArgument(0);
            ps.setId(ps.getSchemeOption().getId() * 10);
            return ps;
        });

        String csvA = """
            SchemeCode,SecurityName,ISIN,Sector,AssetClass,Quantity,MarketValue,Weight,Date
            118955,ICICI Bank Ltd.,INE090A01021,Financial Services,Equity,50000,475.50,9.50%,2024-01-31
            """;

        String csvB = """
            Portfolio Date,ISIN,Company Name,Industry,Quantity,Market Value (INR Cr),% to NAV
            2024-01-31,INE090A01021,ICICI Bank Ltd.,Financial Services,850000,138.90,6.35%
            """;

        HoldingsIngestionSummary summaryA = ingestionService.ingestMonthlyHoldings(10001L, LocalDate.of(2024, 1, 31), csvA.getBytes(StandardCharsets.UTF_8), "uriA");
        HoldingsIngestionSummary summaryB = ingestionService.ingestMonthlyHoldings(10002L, LocalDate.of(2024, 1, 31), csvB.getBytes(StandardCharsets.UTF_8), "uriB");

        assertEquals(10001L, summaryA.schemeOptionId());
        assertEquals(new BigDecimal("0.095000"), summaryA.sumReportedWeights());

        assertEquals(10002L, summaryB.schemeOptionId());
        assertEquals(new BigDecimal("0.063500"), summaryB.sumReportedWeights());

        verify(snapshotRepository, times(2)).save(any(PortfolioSnapshot.class));
    }

    @Test
    void testIngestionJobWithRawPayloadExecutesEndToEndWorkflow() {
        SchemeOption option = createMockOption(10189L, "118955");
        when(schemeOptionRepository.findByAmfiCode("118955")).thenReturn(Optional.of(option));
        when(schemeOptionRepository.findById(10189L)).thenReturn(Optional.of(option));
        when(dataSourceRepository.findByCode(any())).thenReturn(Optional.of(new DataSource("SEBI", "SEBI", "http://amfi.com")));
        when(sourceArtifactRepository.findBySha256Hash(any())).thenReturn(Optional.empty());
        when(sourceArtifactRepository.save(any())).thenAnswer(inv -> {
            SourceArtifact sa = inv.getArgument(0);
            sa.setId(888L);
            return sa;
        });
        when(snapshotRepository.findBySchemeOptionIdAndPortfolioDate(eq(10189L), any())).thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenAnswer(inv -> {
            PortfolioSnapshot ps = inv.getArgument(0);
            ps.setId(999L);
            return ps;
        });

        String csv = """
            SchemeCode,SecurityName,ISIN,Sector,AssetClass,Quantity,MarketValue,Weight,Date
            118955,ICICI Bank Ltd.,INE090A01021,Financial Services,Equity,50000,475.50,9.50%,2024-01-31
            """;

        MonthlyHoldingsSourceDescriptor descriptor = MonthlyHoldingsSourceDescriptor.of(
            "HDFC_MF", "HDFC Mutual Fund", "HDFC_FLEXI", "HDFC Flexi Cap Fund", "118955", "INF179K01UT0",
            "https://www.hdfcfund.com/disclosures/HDFC_Flexi_Jan2024.csv", LocalDate.of(2024, 1, 31)
        );

        MonthlyHoldingsAcquisitionResult result = ingestionJob.executeWithRawPayload(descriptor, csv.getBytes(StandardCharsets.UTF_8));

        assertNotNull(result);
        assertTrue(result.isSuccessful());
        assertEquals(MonthlyHoldingsAcquisitionResult.Status.INGESTED, result.status());
        assertEquals(888L, result.sourceArtifactId());
        assertEquals(10189L, result.schemeOptionId());
        assertEquals(1, result.holdingsCount());
        assertEquals(new BigDecimal("0.095000"), result.sumWeights());
    }

    @Test
    void testIngestionJobRejectsUnresolvedSchemeIdentity() {
        when(schemeOptionRepository.findByAmfiCode("999999")).thenReturn(Optional.empty());

        MonthlyHoldingsSourceDescriptor descriptor = MonthlyHoldingsSourceDescriptor.of(
            "UNKNOWN_MF", "Unknown AMC", "UNKNOWN", "Unknown Scheme", "999999", null,
            "https://unknown.com/disclosure.csv", LocalDate.of(2024, 1, 31)
        );

        MonthlyHoldingsAcquisitionResult result = ingestionJob.executeWithRawPayload(descriptor, "SchemeCode,SecurityName\n999999,ABC".getBytes(StandardCharsets.UTF_8));

        assertNotNull(result);
        assertFalse(result.isSuccessful());
        assertEquals(MonthlyHoldingsAcquisitionResult.Status.SCHEME_UNRESOLVED, result.status());
        verify(snapshotRepository, never()).save(any());
    }

    @Test
    void testIngestionJobRejectsHtmlLoginPagePayload() {
        SchemeOption option = createMockOption(10189L, "118955");
        when(schemeOptionRepository.findByAmfiCode("118955")).thenReturn(Optional.of(option));

        String htmlLoginWall = "<!DOCTYPE html><html><head><title>AMC Portal Login</title></head><body><form action='/login'></form></body></html>";

        MonthlyHoldingsSourceDescriptor descriptor = MonthlyHoldingsSourceDescriptor.of(
            "HDFC_MF", "HDFC Mutual Fund", "HDFC_FLEXI", "HDFC Flexi Cap Fund", "118955", "INF179K01UT0",
            "https://www.hdfcfund.com/disclosures/HDFC_Flexi_Jan2024.csv", LocalDate.of(2024, 1, 31)
        );

        MonthlyHoldingsAcquisitionResult result = ingestionJob.executeWithRawPayload(descriptor, htmlLoginWall.getBytes(StandardCharsets.UTF_8));

        assertNotNull(result);
        assertFalse(result.isSuccessful());
        assertEquals(MonthlyHoldingsAcquisitionResult.Status.INVALID_RESPONSE, result.status());
        assertTrue(result.statusMessage().contains("HTML page or login wall"));
        verify(snapshotRepository, never()).save(any());
    }

    @Test
    void testIngestionJobRejectsBinaryPdfFormatPayload() {
        SchemeOption option = createMockOption(10189L, "118955");
        when(schemeOptionRepository.findByAmfiCode("118955")).thenReturn(Optional.of(option));

        byte[] pdfHeader = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34}; // %PDF-1.4

        MonthlyHoldingsSourceDescriptor descriptor = MonthlyHoldingsSourceDescriptor.of(
            "HDFC_MF", "HDFC Mutual Fund", "HDFC_FLEXI", "HDFC Flexi Cap Fund", "118955", "INF179K01UT0",
            "https://www.hdfcfund.com/disclosures/HDFC_Flexi_Jan2024.pdf", LocalDate.of(2024, 1, 31)
        );

        MonthlyHoldingsAcquisitionResult result = ingestionJob.executeWithRawPayload(descriptor, pdfHeader);

        assertNotNull(result);
        assertFalse(result.isSuccessful());
        assertEquals(MonthlyHoldingsAcquisitionResult.Status.UNSUPPORTED_FORMAT, result.status());
        assertTrue(result.statusMessage().contains("PDF binary disclosure format"));
        verify(snapshotRepository, never()).save(any());
    }

    @Test
    void testRefreshServiceExecutesBatchWithConfiguredAndUnconfiguredFunds() {
        SchemeOption optionHdfc = createMockOption(10189L, "118955");
        when(schemeOptionRepository.findByAmfiCode("118955")).thenReturn(Optional.of(optionHdfc));

        MonthlyHoldingsRefreshRun run = refreshService.executeMonthlyHoldingsRefresh();

        assertNotNull(run);
        assertNotNull(run.runId());
        assertTrue(run.runId().startsWith("HOLDINGS_REFRESH_"));
        assertEquals(30, run.totalRegisteredFunds());
        assertEquals(12, run.attemptedCount());
        assertEquals(27, run.skippedCount()); // 27 unverified funds skipped as SOURCE_NOT_CONFIGURED

        // Verify SBI result is explicitly marked SOURCE_NOT_CONFIGURED
        Optional<FundRefreshResult> sbiRes = run.fundResults().stream().filter(f -> "120503".equals(f.amfiCode())).findFirst();
        assertTrue(sbiRes.isPresent());
        assertEquals(MonthlyHoldingsAcquisitionResult.Status.SOURCE_NOT_CONFIGURED, sbiRes.get().status());
        assertTrue(sbiRes.get().statusMessage().contains("no authoritative disclosure source endpoint is configured yet"));

        verify(bootstrapService, times(1)).ensureCanonicalPilotMaster();
        verify(bootstrapService, times(1)).ensurePpfasMaster();
        verify(bootstrapService, times(1)).ensureSbiMaster();
    }

    @Test
    void testRefreshServiceSingleFundWithRawPayload() {
        SchemeOption option = createMockOption(10189L, "118955");
        when(schemeOptionRepository.findByAmfiCode("118955")).thenReturn(Optional.of(option));
        when(schemeOptionRepository.findById(10189L)).thenReturn(Optional.of(option));
        when(dataSourceRepository.findByCode(any())).thenReturn(Optional.of(new DataSource("SEBI", "SEBI", "http://amfi.com")));
        when(sourceArtifactRepository.findBySha256Hash(any())).thenReturn(Optional.empty());
        when(sourceArtifactRepository.save(any())).thenAnswer(inv -> {
            SourceArtifact sa = inv.getArgument(0);
            sa.setId(777L);
            return sa;
        });
        when(snapshotRepository.findBySchemeOptionIdAndPortfolioDate(eq(10189L), any())).thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenAnswer(inv -> {
            PortfolioSnapshot ps = inv.getArgument(0);
            ps.setId(888L);
            return ps;
        });

        String csv = """
            SchemeCode,SecurityName,ISIN,Sector,AssetClass,Quantity,MarketValue,Weight,Date
            118955,ICICI Bank Ltd.,INE090A01021,Financial Services,Equity,50000,475.50,9.50%,2024-01-31
            """;

        FundRefreshResult result = refreshService.executeRefreshWithRawPayload("118955", csv.getBytes(StandardCharsets.UTF_8));

        assertNotNull(result);
        assertTrue(result.isSuccessful());
        assertEquals(MonthlyHoldingsAcquisitionResult.Status.INGESTED, result.status());
        assertEquals("118955", result.amfiCode());
        assertEquals(10189L, result.schemeOptionId());
        assertEquals(1, result.holdingsCount());
        assertEquals(new BigDecimal("0.095000"), result.sumWeights());
    }

    @Test
    void testRefreshServiceRecordsTriggerTypeMetadata() {
        SchemeOption optionHdfc = createMockOption(10189L, "118955");
        when(schemeOptionRepository.findByAmfiCode("118955")).thenReturn(Optional.of(optionHdfc));

        MonthlyHoldingsRefreshRun scheduledRun = refreshService.executeMonthlyHoldingsRefresh(TriggerType.SCHEDULED);
        assertNotNull(scheduledRun);
        assertEquals(TriggerType.SCHEDULED, scheduledRun.triggerType());

        MonthlyHoldingsRefreshRun manualRun = refreshService.executeMonthlyHoldingsRefresh(TriggerType.MANUAL);
        assertNotNull(manualRun);
        assertEquals(TriggerType.MANUAL, manualRun.triggerType());
    }

    @Test
    void testOlderHistoricalSnapshotIngestionDoesNotSetLatestRevisionWhenNewerSnapshotExists() {
        SchemeOption option = createMockOption(10189L, "118955");
        when(schemeOptionRepository.findById(10189L)).thenReturn(Optional.of(option));
        when(dataSourceRepository.findByCode(any())).thenReturn(Optional.of(new DataSource("SEBI", "SEBI", "http://amfi.com")));
        when(sourceArtifactRepository.findBySha256Hash(any())).thenReturn(Optional.empty());
        when(sourceArtifactRepository.save(any())).thenAnswer(inv -> {
            SourceArtifact sa = inv.getArgument(0);
            sa.setId(500L);
            return sa;
        });

        // Mock that a snapshot for 2024-02-29 already exists
        when(snapshotRepository.findBySchemeOptionIdAndPortfolioDate(eq(10189L), eq(LocalDate.of(2024, 1, 31)))).thenReturn(Optional.empty());
        when(snapshotRepository.existsBySchemeOptionIdAndPortfolioDateAfter(eq(10189L), eq(LocalDate.of(2024, 1, 31)))).thenReturn(true);

        PortfolioSnapshot[] savedHolder = new PortfolioSnapshot[1];
        when(snapshotRepository.save(any())).thenAnswer(inv -> {
            PortfolioSnapshot ps = inv.getArgument(0);
            ps.setId(1000L);
            savedHolder[0] = ps;
            return ps;
        });

        String oldCsv = """
            SchemeCode,SecurityName,ISIN,Sector,AssetClass,Quantity,MarketValue,Weight,Date
            118955,ICICI Bank Ltd.,INE090A01021,Financial Services,Equity,50000,475.50,9.50%,2024-01-31
            """;

        HoldingsIngestionSummary summary = ingestionService.ingestMonthlyHoldings(
            10189L, LocalDate.of(2024, 1, 31), oldCsv.getBytes(StandardCharsets.UTF_8), "http://hdfcfund.com/jan2024.csv"
        );

        assertNotNull(summary);
        assertNotNull(savedHolder[0]);
        // Verify isLatestRevision was set to false because newer snapshot (2024-02-29) exists
        assertFalse(savedHolder[0].getLatestRevision());
        assertTrue(summary.messages().stream().anyMatch(m -> m.contains("ingested as non-latest revision")));
    }

    @Test
    void testPpfasAndSbiRegistryStateAndDescriptorRegistration() {
        Optional<MonthlyHoldingsSourceRegistry.RegisteredFundConfig> ppfasConfigOpt = sourceRegistry.findByAmfiCode("122639");
        assertTrue(ppfasConfigOpt.isPresent());
        assertEquals(MonthlyHoldingsSourceRegistry.FundSourceState.VERIFIED_OFFICIAL, ppfasConfigOpt.get().state());
        assertNotNull(ppfasConfigOpt.get().descriptor());

        Optional<MonthlyHoldingsSourceRegistry.RegisteredFundConfig> sbiConfigOpt = sourceRegistry.findByAmfiCode("120503");
        assertTrue(sbiConfigOpt.isPresent());
        assertEquals(MonthlyHoldingsSourceRegistry.FundSourceState.UNVERIFIED, sbiConfigOpt.get().state());
        assertNull(sbiConfigOpt.get().descriptor());

        // Test registerSource updates state to VERIFIED_OFFICIAL for registered descriptor
        MonthlyHoldingsSourceDescriptor ppfasDesc = MonthlyHoldingsSourceDescriptor.of(
            "PPFAS_MF", "PPFAS Mutual Fund", "PPFAS_FLEXI", "Parag Parikh Flexi Cap Fund",
            "122639", "INF879O01015", "https://amc.ppfas.com/downloads/portfolio-disclosure/2026/PPFCF_PPFAS_Monthly_Portfolio_Report_September_30_2026.xlsx", LocalDate.of(2026, 9, 30)
        );
        sourceRegistry.registerSource("122639", ppfasDesc);

        Optional<MonthlyHoldingsSourceRegistry.RegisteredFundConfig> updatedPpfas = sourceRegistry.findByAmfiCode("122639");
        assertTrue(updatedPpfas.isPresent());
        assertEquals(MonthlyHoldingsSourceRegistry.FundSourceState.VERIFIED_OFFICIAL, updatedPpfas.get().state());
        assertNotNull(updatedPpfas.get().descriptor());
        assertTrue(updatedPpfas.get().descriptor().sourceUrl().contains("amc.ppfas.com"));
    }

    private SchemeOption createMockOption(Long id, String amfi) {
        Scheme scheme = new Scheme();
        scheme.setId(100L + id);
        scheme.setName("Scheme #" + id);
        scheme.setCode("SCHEME_" + id);

        SchemePlan plan = new SchemePlan();
        plan.setId(200L + id);
        plan.setScheme(scheme);
        plan.setPlanType("DIRECT");

        SchemeOption option = new SchemeOption();
        option.setId(id);
        option.setPlan(plan);
        option.setAmfiCode(amfi);
        option.setIsin("INF" + id);
        option.setOptionType("GROWTH");

        return option;
    }
}

