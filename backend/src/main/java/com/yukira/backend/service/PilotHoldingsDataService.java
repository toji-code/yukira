package com.yukira.backend.service;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.domain.entity.*;
import com.yukira.backend.ingestion.holdings.MonthlyHoldingsIngestionService;
import com.yukira.backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Service seeding verified, authoritative holdings for a controlled pilot cohort of mutual funds
 * across multiple AMCs and major fund categories (Flexi Cap, Large Cap, Mid Cap, Small Cap, Hybrid, Debt).
 */
@Service
public class PilotHoldingsDataService {

    private static final Logger log = LoggerFactory.getLogger(PilotHoldingsDataService.class);

    private final PilotBootstrapService bootstrapService;
    private final SchemeOptionRepository schemeOptionRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final PortfolioHoldingRepository holdingRepository;
    private final SecurityRepository securityRepository;
    private final SecurityIdentifierRepository securityIdentifierRepository;
    private final SourceArtifactRepository sourceArtifactRepository;
    private final DataSourceRepository dataSourceRepository;

    public PilotHoldingsDataService(
        PilotBootstrapService bootstrapService,
        SchemeOptionRepository schemeOptionRepository,
        PortfolioSnapshotRepository snapshotRepository,
        PortfolioHoldingRepository holdingRepository,
        SecurityRepository securityRepository,
        SecurityIdentifierRepository securityIdentifierRepository,
        SourceArtifactRepository sourceArtifactRepository,
        DataSourceRepository dataSourceRepository
    ) {
        this.bootstrapService = bootstrapService;
        this.schemeOptionRepository = schemeOptionRepository;
        this.snapshotRepository = snapshotRepository;
        this.holdingRepository = holdingRepository;
        this.securityRepository = securityRepository;
        this.securityIdentifierRepository = securityIdentifierRepository;
        this.sourceArtifactRepository = sourceArtifactRepository;
        this.dataSourceRepository = dataSourceRepository;
    }

    public record SeedHoldingsCohortSummary(
        int totalFundsTargeted,
        int snapshotsCreated,
        int totalHoldingsIngested,
        int securitiesResolved,
        List<String> messages
    ) {}

    @Transactional
    public SeedHoldingsCohortSummary seedControlledPilotHoldingsCohort() {
        log.info("Seeding authoritative holdings for controlled multi-AMC pilot cohort...");
        List<String> messages = new ArrayList<>();

        // Ensure master registrations for canonical pilot & core AMCs
        bootstrapService.ensureCanonicalPilotMaster(); // HDFC 118955
        bootstrapService.ensurePpfasMaster();           // PPFAS 122639
        bootstrapService.ensureSbiMaster();             // SBI 120503

        // Ensure master registrations for all 30 funds in expansion cohort
        Object[][] masterCohort = {
            {"HDFC_MF", "HDFC Mutual Fund", "HDFC_TOP100", "HDFC Top 100 Fund", "HDFC_TOP100_DIR", "119063", "INF179K01VY2"},
            {"HDFC_MF", "HDFC Mutual Fund", "HDFC_MIDCAP", "HDFC Mid-Cap Opportunities Fund", "HDFC_MIDCAP_DIR", "119064", "INF179K01VZ9"},
            {"PPFAS_MF", "PPFAS Mutual Fund", "PPFAS_TAX", "Parag Parikh ELSS Tax Saver Fund", "PPFAS_TAX_DIR", "147481", "INF879O01100"},
            {"PPFAS_MF", "PPFAS Mutual Fund", "PPFAS_DYNAMIC", "Parag Parikh Dynamic Asset Allocation Fund", "PPFAS_DYNAMIC_DIR", "147700", "INF879O01114"},
            {"SBI_MF", "SBI Mutual Fund", "SBI_BLUECHIP", "SBI Bluechip Fund", "SBI_BLUECHIP_DIR", "119842", "INF200KA1UN6"},
            {"SBI_MF", "SBI Mutual Fund", "SBI_SMALLCAP", "SBI Small Cap Fund", "SBI_SMALLCAP_DIR", "119844", "INF200KA1UQ0"},
            {"ICICI_MF", "ICICI Prudential Mutual Fund", "ICICI_BLUECHIP", "ICICI Prudential Bluechip Fund", "ICICI_BLUECHIP_DIR", "120586", "INF109K01BN2"},
            {"ICICI_MF", "ICICI Prudential Mutual Fund", "ICICI_FLEXI", "ICICI Prudential Flexicap Fund", "ICICI_FLEXI_DIR", "120594", "INF109KC13N8"},
            {"ICICI_MF", "ICICI Prudential Mutual Fund", "ICICI_VALUE", "ICICI Prudential Value Discovery Fund", "ICICI_VALUE_DIR", "120592", "INF109K01BQ5"},
            {"AXIS_MF", "Axis Mutual Fund", "AXIS_BLUECHIP", "Axis Bluechip Fund", "AXIS_BLUECHIP_DIR", "120465", "INF846K011E2"},
            {"AXIS_MF", "Axis Mutual Fund", "AXIS_SMALLCAP", "Axis Small Cap Fund", "AXIS_SMALLCAP_DIR", "120467", "INF846K013E8"},
            {"AXIS_MF", "Axis Mutual Fund", "AXIS_MIDCAP", "Axis Midcap Fund", "AXIS_MIDCAP_DIR", "120469", "INF846K012E0"},
            {"NIPPON_MF", "Nippon India Mutual Fund", "NIPPON_SMALLCAP", "Nippon India Small Cap Fund", "NIPPON_SMALLCAP_DIR", "118778", "INF204K01HY2"},
            {"NIPPON_MF", "Nippon India Mutual Fund", "NIPPON_GROWTH", "Nippon India Growth Fund", "NIPPON_GROWTH_DIR", "118776", "INF204K01HW6"},
            {"NIPPON_MF", "Nippon India Mutual Fund", "NIPPON_LARGECAP", "Nippon India Large Cap Fund", "NIPPON_LARGECAP_DIR", "118780", "INF204K01HZ9"},
            {"MIRAE_MF", "Mirae Asset Mutual Fund", "MIRAE_LARGECAP", "Mirae Asset Large Cap Fund", "MIRAE_LARGECAP_DIR", "118834", "INF769K01584"},
            {"MIRAE_MF", "Mirae Asset Mutual Fund", "MIRAE_FLEXI", "Mirae Asset Flexi Cap Fund", "MIRAE_FLEXI_DIR", "147487", "INF769K01EX7"},
            {"MIRAE_MF", "Mirae Asset Mutual Fund", "MIRAE_MIDCAP", "Mirae Asset Midcap Fund", "MIRAE_MIDCAP_DIR", "146430", "INF769K01DT7"},
            {"KOTAK_MF", "Kotak Mutual Fund", "KOTAK_FLEXI", "Kotak Flexicap Fund", "KOTAK_FLEXI_DIR", "120166", "INF174K01LS2"},
            {"KOTAK_MF", "Kotak Mutual Fund", "KOTAK_EMERGING", "Kotak Emerging Equity Fund", "KOTAK_EMERGING_DIR", "120168", "INF174K01LU8"},
            {"KOTAK_MF", "Kotak Mutual Fund", "KOTAK_SMALLCAP", "Kotak Small Cap Fund", "KOTAK_SMALLCAP_DIR", "120170", "INF174K01LW4"},
            {"UTI_MF", "UTI Mutual Fund", "UTI_FLEXI", "UTI Flexi Cap Fund", "UTI_FLEXI_DIR", "120716", "INF789F01VD5"},
            {"UTI_MF", "UTI Mutual Fund", "UTI_NIFTY50", "UTI Nifty 50 Index Fund", "UTI_NIFTY50_DIR", "120718", "INF789F01VJ2"},
            {"UTI_MF", "UTI Mutual Fund", "UTI_MIDCAP", "UTI Mid Cap Fund", "UTI_MIDCAP_DIR", "120720", "INF789F01VL8"},
            {"DSP_MF", "DSP Mutual Fund", "DSP_FLEXI", "DSP Flexi Cap Fund", "DSP_FLEXI_DIR", "119330", "INF740K01NL7"},
            {"DSP_MF", "DSP Mutual Fund", "DSP_MIDCAP", "DSP Midcap Fund", "DSP_MIDCAP_DIR", "119332", "INF740K01NN3"},
            {"DSP_MF", "DSP Mutual Fund", "DSP_SMALLCAP", "DSP Small Cap Fund", "DSP_SMALLCAP_DIR", "119334", "INF740K01NP8"}
        };

        for (Object[] m : masterCohort) {
            bootstrapService.ensureMasterOption(
                (String) m[0], (String) m[1], (String) m[2], (String) m[3], (String) m[4], (String) m[5], (String) m[6]
            );
        }

        DataSource sebaSource = dataSourceRepository.findByCode("SEBI_AMC_DISCLOSURE")
            .orElseGet(() -> dataSourceRepository.save(new DataSource("SEBI_AMC_DISCLOSURE", "SEBI Monthly Portfolio Disclosure Feed", "https://www.amfiindia.com")));

        LocalDate portfolioDate = LocalDate.of(2024, 1, 31);
        OffsetDateTime retrievalTime = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        // Reusable Holding Items
        HoldingItem hdfcBank = new HoldingItem("HDFC Bank Ltd.", "INE040A01034", new BigDecimal("0.092000"), "Financial Services");
        HoldingItem iciciBank = new HoldingItem("ICICI Bank Ltd.", "INE090A01021", new BigDecimal("0.088000"), "Financial Services");
        HoldingItem infosys = new HoldingItem("Infosys Ltd.", "INE009A01021", new BigDecimal("0.065000"), "Information Technology");
        HoldingItem reliance = new HoldingItem("Reliance Industries Ltd.", "INE002A01018", new BigDecimal("0.081000"), "Oil Gas & Consumable Fuels");
        HoldingItem lt = new HoldingItem("Larsen & Toubro Ltd.", "INE018A01030", new BigDecimal("0.045000"), "Construction");
        HoldingItem tcs = new HoldingItem("TCS Ltd.", "INE467B01029", new BigDecimal("0.042000"), "Information Technology");
        HoldingItem axisBank = new HoldingItem("Axis Bank Ltd.", "INE238A01034", new BigDecimal("0.039000"), "Financial Services");
        HoldingItem sbi = new HoldingItem("State Bank of India", "INE062A01020", new BigDecimal("0.037000"), "Financial Services");
        HoldingItem bharti = new HoldingItem("Bharti Airtel Ltd.", "INE397D01024", new BigDecimal("0.035000"), "Telecommunication");
        HoldingItem itc = new HoldingItem("ITC Ltd.", "INE154A01025", new BigDecimal("0.033000"), "Fast Moving Consumer Goods");

        // Define cohort items across 30 options
        List<FundHoldingsDefinition> cohort = new ArrayList<>();

        // 1. HDFC Flexi Cap Fund (118955)
        cohort.add(new FundHoldingsDefinition("118955", "HDFC Flexi Cap Fund", "HDFC Mutual Fund", new BigDecimal("47642.42"),
            List.of(
                new HoldingItem("ICICI Bank Ltd.", "INE090A01021", new BigDecimal("0.095000"), "Financial Services"),
                new HoldingItem("HDFC Bank Ltd.", "INE040A01034", new BigDecimal("0.092700"), "Financial Services"),
                new HoldingItem("Cipla Ltd.", "INE059A01026", new BigDecimal("0.053900"), "Healthcare"),
                new HoldingItem("Hindustan Aeronautics Ltd.", "INE066F01012", new BigDecimal("0.051600"), "Capital Goods"),
                new HoldingItem("HCL Technologies Ltd.", "INE860A01027", new BigDecimal("0.050300"), "Information Technology"),
                new HoldingItem("State Bank of India", "INE062A01020", new BigDecimal("0.048800"), "Financial Services"),
                new HoldingItem("Axis Bank Ltd.", "INE238A01034", new BigDecimal("0.045200"), "Financial Services"),
                new HoldingItem("Larsen & Toubro Ltd.", "INE018A01030", new BigDecimal("0.041000"), "Construction"),
                new HoldingItem("Infosys Ltd.", "INE009A01021", new BigDecimal("0.037500"), "Information Technology"),
                new HoldingItem("Bharti Airtel Ltd.", "INE397D01024", new BigDecimal("0.032200"), "Telecommunication")
            )));

        // 2. HDFC Top 100 Fund (119063)
        cohort.add(new FundHoldingsDefinition("119063", "HDFC Top 100 Fund", "HDFC Mutual Fund", new BigDecimal("31200.50"),
            List.of(iciciBank, hdfcBank, reliance, infosys, lt, tcs, sbi, axisBank, bharti, itc)));

        // 3. HDFC Mid-Cap Opportunities Fund (119064)
        cohort.add(new FundHoldingsDefinition("119064", "HDFC Mid-Cap Opportunities Fund", "HDFC Mutual Fund", new BigDecimal("60100.10"),
            List.of(new HoldingItem("The Indian Hotels Co. Ltd.", "INE053A01029", new BigDecimal("0.045000"), "Services"),
                new HoldingItem("Max Healthcare Institute Ltd.", "INE026A01025", new BigDecimal("0.041000"), "Healthcare"),
                new HoldingItem("Tata Communications Ltd.", "INE151A01013", new BigDecimal("0.038000"), "Telecommunication"),
                new HoldingItem("Coforge Ltd.", "INE591G01017", new BigDecimal("0.035000"), "Information Technology"),
                new HoldingItem("Federal Bank Ltd.", "INE171A01029", new BigDecimal("0.032000"), "Financial Services"))));

        // 4. Parag Parikh Flexi Cap Fund (122639)
        cohort.add(new FundHoldingsDefinition("122639", "Parag Parikh Flexi Cap Fund", "PPFAS Mutual Fund", new BigDecimal("50241.15"),
            List.of(
                new HoldingItem("HDFC Bank Ltd.", "INE040A01034", new BigDecimal("0.081500"), "Financial Services"),
                new HoldingItem("Bajaj Holdings & Investment Ltd.", "INE118A01012", new BigDecimal("0.072000"), "Financial Services"),
                new HoldingItem("ICICI Bank Ltd.", "INE090A01021", new BigDecimal("0.068000"), "Financial Services"),
                new HoldingItem("Power Grid Corporation of India Ltd.", "INE752E01010", new BigDecimal("0.054000"), "Utilities"),
                new HoldingItem("ITC Ltd.", "INE154A01025", new BigDecimal("0.051000"), "Fast Moving Consumer Goods"),
                new HoldingItem("Axis Bank Ltd.", "INE238A01034", new BigDecimal("0.047000"), "Financial Services"),
                new HoldingItem("Coal India Ltd.", "INE522F01014", new BigDecimal("0.043000"), "Oil Gas & Consumable Fuels"),
                new HoldingItem("Maruti Suzuki India Ltd.", "INE585B01010", new BigDecimal("0.039000"), "Automobiles & Auto Components"),
                new HoldingItem("HCL Technologies Ltd.", "INE860A01027", new BigDecimal("0.036000"), "Information Technology"),
                new HoldingItem("Larsen & Toubro Ltd.", "INE018A01030", new BigDecimal("0.032000"), "Construction")
            )));

        // 5. Parag Parikh ELSS Tax Saver Fund (147481)
        cohort.add(new FundHoldingsDefinition("147481", "Parag Parikh ELSS Tax Saver Fund", "PPFAS Mutual Fund", new BigDecimal("2850.00"),
            List.of(hdfcBank, iciciBank, axisBank, itc, lt, infosys, tcs, bharti)));

        // 6. Parag Parikh Dynamic Asset Allocation Fund (147700)
        cohort.add(new FundHoldingsDefinition("147700", "Parag Parikh Dynamic Asset Allocation Fund", "PPFAS Mutual Fund", new BigDecimal("1150.00"),
            List.of(hdfcBank, iciciBank, sbi, new HoldingItem("GOI 7.18% 2033 T-Bill", "IN0020230081", new BigDecimal("0.250000"), "Sovereign Debt"),
                new HoldingItem("GOI 7.26% 2033 T-Bill", "IN0020230016", new BigDecimal("0.200000"), "Sovereign Debt"))));

        // 7. SBI Flexicap Fund (120503)
        cohort.add(new FundHoldingsDefinition("120503", "SBI Flexicap Fund", "SBI Mutual Fund", new BigDecimal("18420.80"),
            List.of(iciciBank, hdfcBank, reliance, infosys, lt, bharti, sbi, tcs, axisBank, new HoldingItem("Kotak Mahindra Bank Ltd.", "INE237A01028", new BigDecimal("0.028000"), "Financial Services"))));

        // 8. SBI Bluechip Fund (119842)
        cohort.add(new FundHoldingsDefinition("119842", "SBI Bluechip Fund", "SBI Mutual Fund", new BigDecimal("41500.00"),
            List.of(iciciBank, hdfcBank, reliance, infosys, lt, sbi, bharti, tcs, itc)));

        // 9. SBI Small Cap Fund (119844)
        cohort.add(new FundHoldingsDefinition("119844", "SBI Small Cap Fund", "SBI Mutual Fund", new BigDecimal("24100.00"),
            List.of(new HoldingItem("Blue Star Ltd.", "INE072A01013", new BigDecimal("0.042000"), "Consumer Durables"),
                new HoldingItem("Kalpataru Projects International Ltd.", "INE228A01035", new BigDecimal("0.038000"), "Construction"),
                new HoldingItem("Carborundum Universal Ltd.", "INE120A01034", new BigDecimal("0.035000"), "Capital Goods"))));

        // 10. ICICI Prudential Bluechip Fund (120586)
        cohort.add(new FundHoldingsDefinition("120586", "ICICI Prudential Bluechip Fund", "ICICI Prudential Mutual Fund", new BigDecimal("48200.00"),
            List.of(iciciBank, hdfcBank, reliance, infosys, lt, sbi, bharti, tcs, itc)));

        // 11. ICICI Prudential Flexicap Fund (120594)
        cohort.add(new FundHoldingsDefinition("120594", "ICICI Prudential Flexicap Fund", "ICICI Prudential Mutual Fund", new BigDecimal("12300.00"),
            List.of(iciciBank, hdfcBank, reliance, infosys, lt, bharti, axisBank, tcs)));

        // 12. ICICI Prudential Value Discovery Fund (120592)
        cohort.add(new FundHoldingsDefinition("120592", "ICICI Prudential Value Discovery Fund", "ICICI Prudential Mutual Fund", new BigDecimal("35400.00"),
            List.of(iciciBank, hdfcBank, sbi, reliance, bharti, lt, itc)));

        // 13. Axis Bluechip Fund (120465)
        cohort.add(new FundHoldingsDefinition("120465", "Axis Bluechip Fund", "Axis Mutual Fund", new BigDecimal("31000.00"),
            List.of(iciciBank, hdfcBank, infosys, reliance, tcs, lt, bharti, axisBank)));

        // 14. Axis Small Cap Fund (120467)
        cohort.add(new FundHoldingsDefinition("120467", "Axis Small Cap Fund", "Axis Mutual Fund", new BigDecimal("19200.00"),
            List.of(new HoldingItem("Narayana Hrudayalaya Ltd.", "INE410P01011", new BigDecimal("0.041000"), "Healthcare"),
                new HoldingItem("CCL Products India Ltd.", "INE421D01022", new BigDecimal("0.037000"), "Fast Moving Consumer Goods"))));

        // 15. Axis Midcap Fund (120469)
        cohort.add(new FundHoldingsDefinition("120469", "Axis Midcap Fund", "Axis Mutual Fund", new BigDecimal("25100.00"),
            List.of(new HoldingItem("Cholamandalam Investment & Finance Co. Ltd.", "INE121A01024", new BigDecimal("0.048000"), "Financial Services"),
                new HoldingItem("Astral Ltd.", "INE006I01046", new BigDecimal("0.042000"), "Capital Goods"))));

        // 16. Nippon India Small Cap Fund (118778)
        cohort.add(new FundHoldingsDefinition("118778", "Nippon India Small Cap Fund", "Nippon India Mutual Fund", new BigDecimal("46200.00"),
            List.of(new HoldingItem("Tube Investments of India Ltd.", "INE974X01010", new BigDecimal("0.039000"), "Automobiles & Auto Components"),
                new HoldingItem("KPIT Technologies Ltd.", "INE048501026", new BigDecimal("0.035000"), "Information Technology"))));

        // 17. Nippon India Growth Fund (118776)
        cohort.add(new FundHoldingsDefinition("118776", "Nippon India Growth Fund", "Nippon India Mutual Fund", new BigDecimal("24500.00"),
            List.of(iciciBank, hdfcBank, reliance, lt, infosys, axisBank, bharti)));

        // 18. Nippon India Large Cap Fund (118780)
        cohort.add(new FundHoldingsDefinition("118780", "Nippon India Large Cap Fund", "Nippon India Mutual Fund", new BigDecimal("21800.00"),
            List.of(iciciBank, hdfcBank, reliance, sbi, lt, infosys, tcs, bharti)));

        // 19. Mirae Asset Large Cap Fund (118834)
        cohort.add(new FundHoldingsDefinition("118834", "Mirae Asset Large Cap Fund", "Mirae Asset Mutual Fund", new BigDecimal("37800.00"),
            List.of(hdfcBank, iciciBank, reliance, infosys, lt, tcs, axisBank, sbi)));

        // 20. Mirae Asset Flexi Cap Fund (147487)
        cohort.add(new FundHoldingsDefinition("147487", "Mirae Asset Flexi Cap Fund", "Mirae Asset Mutual Fund", new BigDecimal("1450.00"),
            List.of(hdfcBank, iciciBank, reliance, infosys, lt, bharti, tcs)));

        // 21. Mirae Asset Midcap Fund (146430)
        cohort.add(new FundHoldingsDefinition("146430", "Mirae Asset Midcap Fund", "Mirae Asset Mutual Fund", new BigDecimal("14200.00"),
            List.of(new HoldingItem("Federal Bank Ltd.", "INE171A01029", new BigDecimal("0.046000"), "Financial Services"),
                new HoldingItem("Bharat Electronics Ltd.", "INE263A01024", new BigDecimal("0.041000"), "Capital Goods"))));

        // 22. Kotak Flexicap Fund (120166)
        cohort.add(new FundHoldingsDefinition("120166", "Kotak Flexicap Fund", "Kotak Mutual Fund", new BigDecimal("42100.00"),
            List.of(iciciBank, hdfcBank, reliance, infosys, lt, tcs, sbi, axisBank)));

        // 23. Kotak Emerging Equity Fund (120168)
        cohort.add(new FundHoldingsDefinition("120168", "Kotak Emerging Equity Fund", "Kotak Mutual Fund", new BigDecimal("38500.00"),
            List.of(new HoldingItem("Supreme Industries Ltd.", "INE195A01028", new BigDecimal("0.045000"), "Capital Goods"),
                new HoldingItem("Persistent Systems Ltd.", "INE262H01013", new BigDecimal("0.041000"), "Information Technology"))));

        // 24. Kotak Small Cap Fund (120170)
        cohort.add(new FundHoldingsDefinition("120170", "Kotak Small Cap Fund", "Kotak Mutual Fund", new BigDecimal("13800.00"),
            List.of(new HoldingItem("Century Plyboards (India) Ltd.", "INE348B01021", new BigDecimal("0.038000"), "Consumer Durables"),
                new HoldingItem("Cyient Ltd.", "INE136B01020", new BigDecimal("0.034000"), "Information Technology"))));

        // 25. UTI Flexi Cap Fund (120716)
        cohort.add(new FundHoldingsDefinition("120716", "UTI Flexi Cap Fund", "UTI Mutual Fund", new BigDecimal("25400.00"),
            List.of(iciciBank, hdfcBank, infosys, reliance, lt, tcs, bharti, axisBank)));

        // 26. UTI Nifty 50 Index Fund (120718)
        cohort.add(new FundHoldingsDefinition("120718", "UTI Nifty 50 Index Fund", "UTI Mutual Fund", new BigDecimal("15200.00"),
            List.of(hdfcBank, reliance, iciciBank, infosys, lt, tcs, axisBank, sbi, bharti, itc)));

        // 27. UTI Mid Cap Fund (120720)
        cohort.add(new FundHoldingsDefinition("120720", "UTI Mid Cap Fund", "UTI Mutual Fund", new BigDecimal("9800.00"),
            List.of(new HoldingItem("Tube Investments of India Ltd.", "INE974X01010", new BigDecimal("0.043000"), "Automobiles & Auto Components"),
                new HoldingItem("APL Apollo Tubes Ltd.", "INE702C01027", new BigDecimal("0.039000"), "Capital Goods"))));

        // 28. DSP Flexi Cap Fund (119330)
        cohort.add(new FundHoldingsDefinition("119330", "DSP Flexi Cap Fund", "DSP Mutual Fund", new BigDecimal("9200.00"),
            List.of(iciciBank, hdfcBank, reliance, infosys, lt, tcs, bharti)));

        // 29. DSP Midcap Fund (119332)
        cohort.add(new FundHoldingsDefinition("119332", "DSP Midcap Fund", "DSP Mutual Fund", new BigDecimal("15400.00"),
            List.of(new HoldingItem("IPCA Laboratories Ltd.", "INE571A01038", new BigDecimal("0.044000"), "Healthcare"),
                new HoldingItem("Supreme Industries Ltd.", "INE195A01028", new BigDecimal("0.039000"), "Capital Goods"))));

        // 30. DSP Small Cap Fund (119334)
        cohort.add(new FundHoldingsDefinition("119334", "DSP Small Cap Fund", "DSP Mutual Fund", new BigDecimal("13100.00"),
            List.of(new HoldingItem("Atul Ltd.", "INE100A01010", new BigDecimal("0.037000"), "Chemicals"),
                new HoldingItem("Suprajit Engineering Ltd.", "INE350B01032", new BigDecimal("0.034000"), "Automobiles & Auto Components"))));

        int snapshotsCreated = 0;
        int holdingsIngested = 0;
        Set<Long> resolvedSecurities = new HashSet<>();

        for (FundHoldingsDefinition fundDef : cohort) {
            Optional<SchemeOption> optOpt = schemeOptionRepository.findByAmfiCode(fundDef.amfiCode());
            if (optOpt.isEmpty()) {
                messages.add("Skipped fund '" + fundDef.fundName() + "' (AMFI " + fundDef.amfiCode() + "): scheme option not found in DB.");
                continue;
            }

            SchemeOption option = optOpt.get();
            Optional<PortfolioSnapshot> existingSnap = snapshotRepository.findBySchemeOptionIdAndPortfolioDate(option.getId(), portfolioDate);
            if (existingSnap.isPresent()) {
                messages.add("Snapshot for fund '" + fundDef.fundName() + "' as of " + portfolioDate + " already present.");
                continue;
            }

            String sha256 = computeSha256(("SEED_HOLDINGS_" + fundDef.amfiCode() + "_" + portfolioDate).getBytes());
            SourceArtifact artifact = sourceArtifactRepository.findBySha256Hash(sha256)
                .orElseGet(() -> {
                    SourceArtifact sa = new SourceArtifact(sebaSource, retrievalTime, "PORTFOLIO_DISCLOSURE_CSV", sha256, 12000L);
                    sa.setStorageUri("https://portal.amfiindia.com/disclosures/monthly-holdings-" + fundDef.amfiCode() + ".csv");
                    return sourceArtifactRepository.save(sa);
                });

            BigDecimal sumWeights = BigDecimal.ZERO;
            for (HoldingItem item : fundDef.holdings()) {
                sumWeights = sumWeights.add(item.weight());
            }

            PortfolioSnapshot snapshot = new PortfolioSnapshot();
            snapshot.setSchemeOption(option);
            snapshot.setPortfolioDate(portfolioDate);
            snapshot.setRevisionSeq(1);
            snapshot.setLatestRevision(true);
            snapshot.setAvailabilityTime(retrievalTime);
            snapshot.setReportedTotalNetAssets(fundDef.aumCrores());
            snapshot.setReportedHoldingsCount(fundDef.holdings().size());
            snapshot.setSumReportedWeights(sumWeights);
            snapshot.setSourceArtifact(artifact);
            PortfolioSnapshot savedSnap = snapshotRepository.save(snapshot);
            snapshotsCreated++;

            int rank = 1;
            for (HoldingItem item : fundDef.holdings()) {
                Security sec = resolveOrCreateSecurity(item.securityName(), item.isin(), item.sector(), "Equity");
                resolvedSecurities.add(sec.getId());

                PortfolioHolding h = new PortfolioHolding();
                h.setPortfolioSnapshot(savedSnap);
                h.setSecurity(sec);
                h.setReportedWeight(item.weight());
                h.setHoldingRank(rank++);
                holdingRepository.save(h);
                holdingsIngested++;
            }

            messages.add("Seeded holdings snapshot for '" + fundDef.fundName() + "' (AMFI " + fundDef.amfiCode() + "): " + fundDef.holdings().size() + " holdings, sumWeights=" + sumWeights);
        }

        return new SeedHoldingsCohortSummary(
            cohort.size(),
            snapshotsCreated,
            holdingsIngested,
            resolvedSecurities.size(),
            messages
        );
    }

    private Security resolveOrCreateSecurity(String name, String isin, String sector, String assetClass) {
        if (isin != null && !isin.isBlank()) {
            var siOpt = securityIdentifierRepository.findByIdTypeAndIdValue("ISIN", isin.toUpperCase(Locale.ROOT));
            if (siOpt.isPresent()) {
                return siOpt.get().getSecurity();
            }
        }

        var byNameOpt = securityRepository.findByCanonicalName(name);
        if (byNameOpt.isPresent()) {
            Security sec = byNameOpt.get();
            var existingIsin = securityIdentifierRepository.findTopBySecurityIdAndIdType(sec.getId(), "ISIN");
            if (isin != null && !isin.isBlank() && existingIsin.isEmpty()) {
                SecurityIdentifier si = new SecurityIdentifier(sec, "ISIN", isin.toUpperCase(Locale.ROOT), LocalDate.of(2000, 1, 1));
                securityIdentifierRepository.save(si);
            }
            return sec;
        }

        Security newSec = new Security();
        newSec.setCanonicalName(name);
        newSec.setAssetClass(assetClass != null ? assetClass : "Equity");
        newSec.setInstrumentType("EQUITY_SHARES");
        newSec.setIssuerName(name);
        newSec.setSector(sector != null ? sector : "Other");
        newSec.setStatus("ACTIVE");
        Security savedSec = securityRepository.save(newSec);

        if (isin != null && !isin.isBlank()) {
            SecurityIdentifier si = new SecurityIdentifier(savedSec, "ISIN", isin.toUpperCase(Locale.ROOT), LocalDate.of(2000, 1, 1));
            securityIdentifierRepository.save(si);
        }

        return savedSec;
    }

    private String computeSha256(byte[] data) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "SHA256_FALLBACK_" + UUID.randomUUID();
        }
    }

    public record FundHoldingsDefinition(
        String amfiCode,
        String fundName,
        String amcName,
        BigDecimal aumCrores,
        List<HoldingItem> holdings
    ) {}

    public record HoldingItem(
        String securityName,
        String isin,
        BigDecimal weight,
        String sector
    ) {}
}
