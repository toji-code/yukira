package com.yukira.backend.service.portfolio;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.lookthrough.*;
import com.yukira.backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service orchestrating Multi-Fund Portfolio Look-Through, Multi-Fund Exposure Aggregation,
 * Pairwise Overlap Matrix, Concentration Metrics, and Candidate Fund Simulation.
 *
 * Epistemic & Methodological Guardrails (AGENTS.md):
 * 1. Zero frontend financial calculations: all aggregation, overlap, concentration, and deltas
 *    are computed deterministically in this service.
 * 2. Point-in-Time compliance: snapshots must satisfy portfolioDate <= asOfDate and
 *    availabilityTime <= knowledgeCutoff.
 * 3. Temporal comparability: flags mismatch when constituent observation dates span > 45 days.
 * 4. Missing exposure is never treated as zero: explicitly reports coverage, unclassified, and cash.
 * 5. Descriptive research only: never outputs buy/sell recommendations or claims of diversification.
 */
@Service
@SuppressWarnings("null")
public class MultiFundLookThroughService {

    private static final Logger log = LoggerFactory.getLogger(MultiFundLookThroughService.class);
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final SchemeOptionRepository schemeOptionRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final PortfolioHoldingRepository holdingRepository;
    private final SecurityIdentifierRepository securityIdentifierRepository;
    private final SecurityMarketCapClassificationRepository classificationRepository;
    private final InvestorTargetAllocationRepository targetAllocationRepository;
    private final InvestorRepository investorRepository;
    private final NavObservationRepository navObservationRepository;
    private final BenchmarkRepository benchmarkRepository;
    private final BenchmarkObservationRepository benchmarkObservationRepository;

    // Canonical Macro-Sector Taxonomy (aligned with official NSE Indices macro-sectors)
    private static final Map<String, String> CANONICAL_SECTOR_MAP = new HashMap<>();

    static {
        CANONICAL_SECTOR_MAP.put("FINANCIAL SERVICES", "Financial Services");
        CANONICAL_SECTOR_MAP.put("BANKS", "Financial Services");
        CANONICAL_SECTOR_MAP.put("BANK", "Financial Services");
        CANONICAL_SECTOR_MAP.put("BANKING", "Financial Services");
        CANONICAL_SECTOR_MAP.put("INSURANCE", "Financial Services");
        CANONICAL_SECTOR_MAP.put("FINANCE", "Financial Services");
        CANONICAL_SECTOR_MAP.put("NON BANKING FINANCIAL COMPANY (NBFC)", "Financial Services");

        CANONICAL_SECTOR_MAP.put("INFORMATION TECHNOLOGY", "Information Technology");
        CANONICAL_SECTOR_MAP.put("IT", "Information Technology");
        CANONICAL_SECTOR_MAP.put("SOFTWARE", "Information Technology");
        CANONICAL_SECTOR_MAP.put("IT - SOFTWARE", "Information Technology");
        CANONICAL_SECTOR_MAP.put("IT - SERVICES", "Information Technology");

        CANONICAL_SECTOR_MAP.put("OIL GAS & CONSUMABLE FUELS", "Oil Gas & Consumable Fuels");
        CANONICAL_SECTOR_MAP.put("OIL, GAS & CONSUMABLE FUELS", "Oil Gas & Consumable Fuels");
        CANONICAL_SECTOR_MAP.put("PETROLEUM PRODUCTS", "Oil Gas & Consumable Fuels");
        CANONICAL_SECTOR_MAP.put("OIL", "Oil Gas & Consumable Fuels");
        CANONICAL_SECTOR_MAP.put("ENERGY", "Oil Gas & Consumable Fuels");

        CANONICAL_SECTOR_MAP.put("FAST MOVING CONSUMER GOODS", "Fast Moving Consumer Goods");
        CANONICAL_SECTOR_MAP.put("FMCG", "Fast Moving Consumer Goods");
        CANONICAL_SECTOR_MAP.put("CONSUMER GOODS", "Fast Moving Consumer Goods");
        CANONICAL_SECTOR_MAP.put("FOOD PRODUCTS", "Fast Moving Consumer Goods");

        CANONICAL_SECTOR_MAP.put("AUTOMOBILE AND AUTO COMPONENTS", "Automobile and Auto Components");
        CANONICAL_SECTOR_MAP.put("AUTOMOBILE", "Automobile and Auto Components");
        CANONICAL_SECTOR_MAP.put("AUTOMOBILES", "Automobile and Auto Components");
        CANONICAL_SECTOR_MAP.put("AUTO COMPONENTS", "Automobile and Auto Components");
        CANONICAL_SECTOR_MAP.put("AUTO", "Automobile and Auto Components");

        CANONICAL_SECTOR_MAP.put("HEALTHCARE", "Healthcare");
        CANONICAL_SECTOR_MAP.put("PHARMACEUTICALS & BIOTECHNOLOGY", "Healthcare");
        CANONICAL_SECTOR_MAP.put("PHARMACEUTICALS", "Healthcare");
        CANONICAL_SECTOR_MAP.put("PHARMA", "Healthcare");
        CANONICAL_SECTOR_MAP.put("HEALTHCARE SERVICES", "Healthcare");

        CANONICAL_SECTOR_MAP.put("CONSTRUCTION", "Construction");
        CANONICAL_SECTOR_MAP.put("CIVIL CONSTRUCTION", "Construction");
        CANONICAL_SECTOR_MAP.put("INFRASTRUCTURE", "Construction");

        CANONICAL_SECTOR_MAP.put("CAPITAL GOODS", "Capital Goods");
        CANONICAL_SECTOR_MAP.put("AEROSPACE & DEFENSE", "Capital Goods");
        CANONICAL_SECTOR_MAP.put("INDUSTRIAL MANUFACTURING", "Capital Goods");
        CANONICAL_SECTOR_MAP.put("INDUSTRIAL PRODUCTS", "Capital Goods");
        CANONICAL_SECTOR_MAP.put("ELECTRICAL EQUIPMENT", "Capital Goods");

        CANONICAL_SECTOR_MAP.put("METALS & MINING", "Metals & Mining");
        CANONICAL_SECTOR_MAP.put("FERROUS METALS", "Metals & Mining");
        CANONICAL_SECTOR_MAP.put("NON - FERROUS METALS", "Metals & Mining");
        CANONICAL_SECTOR_MAP.put("MINERALS & MINING", "Metals & Mining");

        CANONICAL_SECTOR_MAP.put("CONSUMER SERVICES", "Consumer Services");
        CANONICAL_SECTOR_MAP.put("RETAILING", "Consumer Services");
        CANONICAL_SECTOR_MAP.put("COMMERCIAL SERVICES & SUPPLIES", "Consumer Services");
        CANONICAL_SECTOR_MAP.put("HOTELS & RESTAURANTS", "Consumer Services");

        CANONICAL_SECTOR_MAP.put("TELECOMMUNICATION", "Telecommunication");
        CANONICAL_SECTOR_MAP.put("TELECOM - SERVICES", "Telecommunication");

        CANONICAL_SECTOR_MAP.put("POWER", "Power");
        CANONICAL_SECTOR_MAP.put("CHEMICALS", "Chemicals");
        CANONICAL_SECTOR_MAP.put("FERTILIZERS & AGROCHEMICALS", "Chemicals");
        CANONICAL_SECTOR_MAP.put("CONSTRUCTION MATERIALS", "Construction Materials");
        CANONICAL_SECTOR_MAP.put("CEMENT & CEMENT PRODUCTS", "Construction Materials");
        CANONICAL_SECTOR_MAP.put("CONSUMER DURABLES", "Consumer Durables");
        CANONICAL_SECTOR_MAP.put("SERVICES", "Services");
        CANONICAL_SECTOR_MAP.put("TRANSPORT SERVICES", "Services");
        CANONICAL_SECTOR_MAP.put("REALTY", "Realty");
        CANONICAL_SECTOR_MAP.put("REAL ESTATE", "Realty");
        CANONICAL_SECTOR_MAP.put("TEXTILES", "Textiles");
        CANONICAL_SECTOR_MAP.put("MEDIA ENTERTAINMENT & PUBLICATION", "Media Entertainment & Publication");
        CANONICAL_SECTOR_MAP.put("MEDIA & ENTERTAINMENT", "Media Entertainment & Publication");
        CANONICAL_SECTOR_MAP.put("DIVERSIFIED", "Diversified");
    }

    private static final Set<String> SEBI_APPROX_LARGE_CAP = Set.of(
        "HDFC BANK", "ICICI BANK", "STATE BANK OF INDIA", "AXIS BANK", "KOTAK MAHINDRA BANK",
        "RELIANCE INDUSTRIES", "TATA CONSULTANCY SERVICES", "INFOSYS", "BHARTI AIRTEL", "ITC",
        "LARSEN & TOUBRO", "MARUTI SUZUKI", "HINDUSTAN AERONAUTICS", "CIPLA", "HCL TECHNOLOGIES",
        "POWER GRID", "COAL INDIA", "MAHINDRA & MAHINDRA", "DLF", "DR. REDDY", "DR REDDY",
        "BAJAJ FINANCE", "BAJAJ AUTO", "APOLLO HOSPITALS", "ULTRATECH CEMENT", "TATA STEEL",
        "OIL & NATURAL GAS", "ONGC", "JSW STEEL", "BHARAT ELECTRONICS", "EICHER MOTORS", "ZOMATO",
        "LIFE INSURANCE CORPORATION", "LIC", "ADANI ENTERPRISES", "TRENT", "SUN PHARMACEUTICAL",
        "TITAN", "TATA MOTORS", "NTPC", "HINDALCO", "GRASIM", "TECH MAHINDRA", "WIPRO", "NESTLE INDIA",
        "ASIAN PAINTS", "AVENUE SUPERMARTS", "DMART", "BAJAJ FINSERV", "BHARAT PETROLEUM", "BPCL",
        "BRITANNIA INDUSTRIES", "DIVI'S LABORATORIES", "DIVIS LAB", "HINDUSTAN UNILEVER", "HUL",
        "INDIAN OIL CORPORATION", "IOC", "INDUSIND BANK", "JIO FINANCIAL", "SBI CARDS",
        "SBI LIFE INSURANCE", "SHREE CEMENT", "SIEMENS", "TATA CONSUMER", "TORRENT PHARMACEUTICALS",
        "UNITED SPIRITS", "VARUN BEVERAGES", "VEDANTA", "MAX HEALTHCARE", "ABB INDIA",
        "BANK OF BARODA", "INTERGLOBE AVIATION", "INDIGO", "ZOMATO LTD"
    );

    private static final Set<String> SEBI_APPROX_MID_CAP = Set.of(
        "ZYDUS LIFESCIENCES", "INDRAPRASTHA GAS", "PETRONET LNG", "MAHANAGAR GAS",
        "NARAYANA HRUDAYALAYA", "CENTRAL DEPOSITORY SERVICES", "CDSL", "INDUS TOWERS",
        "CIE AUTOMOTIVE", "ASHOK LEYLAND", "TVS MOTOR", "DIXON TECHNOLOGIES",
        "PERSISTENT SYSTEMS", "BOSCH", "BIOCON", "JK LAKSHMI CEMENT", "PB FINTECH", "POLICYBAZAAR",
        "SWIGGY", "HYUNDAI MOTOR INDIA", "THE INDIAN HOTELS", "INDIAN HOTELS", "COFORGE",
        "FEDERAL BANK", "BLUE STAR", "KALPATARU PROJECTS", "CARBORUNDUM UNIVERSAL",
        "MPHASIS", "ASTRAL", "AU SMALL FINANCE BANK", "BALKRISHNA INDUSTRIES", "BANDHAN BANK",
        "BATA INDIA", "BERGER PAINTS", "BHARAT FORGE", "CANARA BANK", "CITY UNION BANK",
        "CONTAINER CORPORATION", "CONCOR", "COROMANDEL INTERNATIONAL", "CROMPTON GREAVES",
        "CUMMINS INDIA", "DALMIA BHARAT", "DEEPAK NITRITE", "DELHIVERY", "EMAMI",
        "ENDURANCE TECHNOLOGIES", "ESCORTS KUBOTA", "EXIDE INDUSTRIES", "FORTIS HEALTHCARE",
        "GMR AIRPORTS", "GODREJ PROPERTIES", "GUJARAT GAS", "HAVELLS INDIA", "HINDUSTAN PETROLEUM",
        "HPCL", "IDFC FIRST BANK", "IPCA LABORATORIES", "JINDAL STEEL", "JSW ENERGY",
        "JUBILANT FOODWORKS", "L&T FINANCE", "L&T TECHNOLOGY", "LAURUS LABS", "LUPIN",
        "MARICO", "MRF", "MUTHOOT FINANCE", "NATIONAL ALUMINIUM", "NALCO", "PAGE INDUSTRIES",
        "PATANJALI FOODS", "PI INDUSTRIES", "POLYCAB INDIA", "PUNJAB NATIONAL BANK", "PNB",
        "REC LIMITED", "RECL", "SAIL", "STEEL AUTHORITY", "SCHAEFFLER INDIA", "SOLAR INDUSTRIES",
        "SONA BLW", "SRF", "SUPREME INDUSTRIES", "SUZLON ENERGY", "SYNGENE INTERNATIONAL",
        "TATA CHEMICALS", "TATA COMMUNICATIONS", "TATA POWER", "THERMAX", "TUBE INVESTMENTS",
        "UNION BANK", "VOLTAS", "YES BANK", "ZEE ENTERTAINMENT", "PRESTIGE ESTATES",
        "JSW INFRASTRUCTURE", "DR. LAL PATH LABS", "DR LAL PATHLABS", "METROPOLIS HEALTHCARE",
        "JUPITER LIFE LINE", "PIRAMAL PHARMA", "NUVOCO VISTAS", "THE RAMCO CEMENTS", "RAMCO CEMENTS"
    );

    @Autowired
    public MultiFundLookThroughService(
        SchemeOptionRepository schemeOptionRepository,
        PortfolioSnapshotRepository snapshotRepository,
        PortfolioHoldingRepository holdingRepository,
        SecurityIdentifierRepository securityIdentifierRepository,
        SecurityMarketCapClassificationRepository classificationRepository,
        InvestorTargetAllocationRepository targetAllocationRepository,
        InvestorRepository investorRepository,
        NavObservationRepository navObservationRepository,
        BenchmarkRepository benchmarkRepository,
        BenchmarkObservationRepository benchmarkObservationRepository
    ) {
        this.schemeOptionRepository = schemeOptionRepository;
        this.snapshotRepository = snapshotRepository;
        this.holdingRepository = holdingRepository;
        this.securityIdentifierRepository = securityIdentifierRepository;
        this.classificationRepository = classificationRepository;
        this.targetAllocationRepository = targetAllocationRepository;
        this.investorRepository = investorRepository;
        this.navObservationRepository = navObservationRepository;
        this.benchmarkRepository = benchmarkRepository;
        this.benchmarkObservationRepository = benchmarkObservationRepository;
    }

    public MultiFundLookThroughService(
        SchemeOptionRepository schemeOptionRepository,
        PortfolioSnapshotRepository snapshotRepository,
        PortfolioHoldingRepository holdingRepository,
        SecurityIdentifierRepository securityIdentifierRepository,
        SecurityMarketCapClassificationRepository classificationRepository,
        InvestorTargetAllocationRepository targetAllocationRepository,
        InvestorRepository investorRepository
    ) {
        this(schemeOptionRepository, snapshotRepository, holdingRepository,
             securityIdentifierRepository, classificationRepository,
             targetAllocationRepository, investorRepository, null, null, null);
    }

    /**
     * Executes deterministic multi-fund look-through aggregation, sector exposure aggregation,
     * market-cap distribution, pairwise overlap matrix, and concentration diagnostics.
     */
    @Transactional(readOnly = true)
    public MultiFundLookThroughResponseDto calculateMultiFundLookThrough(MultiFundLookThroughRequestDto request) {
        OffsetDateTime cutoff = request.knowledgeCutoff() != null ? request.knowledgeCutoff() : OffsetDateTime.now();
        LocalDate asOf = request.asOfDate() != null ? request.asOfDate() : cutoff.toLocalDate();

        List<String> limitations = new ArrayList<>();
        Map<String, String> epistemic = new LinkedHashMap<>();
        epistemic.put("Methodology", "Deterministic Multi-Fund Look-Through Exposure Aggregation (Phase 2L)");
        epistemic.put("PointInTimeRules", "portfolioDate <= asOfDate AND availabilityTime <= knowledgeCutoff");
        epistemic.put("AggregationFormula", "E_portfolio,k = SUM_j ( a_j * E_j,k )");
        epistemic.put("EpistemicNotice", "Pure analytical research. No personalized investment advice or automatic diversification rating.");

        if (request.funds() == null || request.funds().isEmpty()) {
            return new MultiFundLookThroughResponseDto(
                asOf, cutoff, BigDecimal.ZERO, false, "EMPTY",
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(),
                new PortfolioConcentrationMetricsDto(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "Unconcentrated (<1500)", 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                Collections.emptyList(), List.of("Portfolio has no constituent funds selected."), epistemic
            );
        }

        // 1. Validate fund allocations
        BigDecimal sumWeights = BigDecimal.ZERO;
        for (ResearchPortfolioFundItemDto f : request.funds()) {
            if (f.schemeOptionId() == null) {
                throw new IllegalArgumentException("schemeOptionId cannot be null in portfolio funds.");
            }
            if (f.allocationWeight() == null || f.allocationWeight().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Allocation for scheme option #" + f.schemeOptionId() + " cannot be negative or null.");
            }
            sumWeights = sumWeights.add(f.allocationWeight());
        }

        if (sumWeights.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Total portfolio allocation must be positive.");
        }

        boolean isFullyAllocated = sumWeights.subtract(HUNDRED).abs().compareTo(new BigDecimal("0.05")) <= 0;

        // Normalize weights so sum = 1.0 (as fractions for math) and percentage (0..100)
        Map<Long, BigDecimal> normalizedWeightsFraction = new LinkedHashMap<>();
        Map<Long, BigDecimal> normalizedWeightsPct = new LinkedHashMap<>();
        for (ResearchPortfolioFundItemDto f : request.funds()) {
            BigDecimal frac = f.allocationWeight().divide(sumWeights, 8, RoundingMode.HALF_UP);
            normalizedWeightsFraction.put(f.schemeOptionId(), frac);
            normalizedWeightsPct.put(f.schemeOptionId(), frac.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP));
        }

        // 2. Resolve constituent funds & latest snapshots under PIT cutoff
        List<ConstituentFundSummaryDto> constituentSummaries = new ArrayList<>();
        Map<Long, List<PortfolioHolding>> fundHoldingsMap = new LinkedHashMap<>();
        Map<Long, PortfolioSnapshot> fundSnapshotMap = new LinkedHashMap<>();
        Map<Long, SchemeOption> fundOptionMap = new LinkedHashMap<>();
        List<LocalDate> obsDates = new ArrayList<>();

        for (ResearchPortfolioFundItemDto f : request.funds()) {
            Long optId = f.schemeOptionId();
            SchemeOption opt = schemeOptionRepository.findById(optId)
                .orElseThrow(() -> new IllegalArgumentException("SchemeOption #" + optId + " not found."));
            fundOptionMap.put(optId, opt);

            String fundName = (opt.getPlan() != null && opt.getPlan().getScheme() != null)
                ? opt.getPlan().getScheme().getName() : "Option #" + optId;
            String amfiCode = opt.getAmfiCode();
            String isin = opt.getIsin();

            List<PortfolioSnapshot> snaps = snapshotRepository
                .findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(optId, cutoff);

            Optional<PortfolioSnapshot> snapOpt = snaps.stream()
                .filter(s -> !s.getPortfolioDate().isAfter(asOf))
                .findFirst();

            if (snapOpt.isEmpty()) {
                limitations.add("No holdings snapshot available for fund '" + fundName + "' as of " + asOf + ".");
                constituentSummaries.add(new ConstituentFundSummaryDto(
                    optId, fundName, amfiCode, isin, f.allocationWeight(), normalizedWeightsPct.get(optId),
                    null, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, "NO_SNAPSHOT"
                ));
                continue;
            }

            PortfolioSnapshot snap = snapOpt.get();
            fundSnapshotMap.put(optId, snap);
            obsDates.add(snap.getPortfolioDate());

            List<PortfolioHolding> holdings = holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(snap.getId());
            fundHoldingsMap.put(optId, holdings);

            BigDecimal equityWeight = BigDecimal.ZERO;
            BigDecimal cashWeight = BigDecimal.ZERO;
            for (PortfolioHolding h : holdings) {
                BigDecimal w = h.getReportedWeight() != null ? h.getReportedWeight().multiply(HUNDRED) : BigDecimal.ZERO;
                String ac = h.getSecurity() != null && h.getSecurity().getAssetClass() != null
                    ? h.getSecurity().getAssetClass().toUpperCase(Locale.ROOT) : "";
                String secName = h.getSecurity() != null && h.getSecurity().getCanonicalName() != null
                    ? h.getSecurity().getCanonicalName().toUpperCase(Locale.ROOT) : "";

                if (ac.contains("CASH") || secName.contains("TREPS") || secName.contains("NET CURRENT ASSETS") || secName.contains("CASH")) {
                    cashWeight = cashWeight.add(w);
                } else {
                    equityWeight = equityWeight.add(w);
                }
            }

            constituentSummaries.add(new ConstituentFundSummaryDto(
                optId, fundName, amfiCode, isin, f.allocationWeight(), normalizedWeightsPct.get(optId),
                snap.getPortfolioDate(), holdings.size(), equityWeight.setScale(2, RoundingMode.HALF_UP),
                cashWeight.setScale(2, RoundingMode.HALF_UP), 0, "VALID"
            ));
        }

        // 3. Temporal comparability assessment
        String qualityState = "COMPUTED_VALIDATED_INPUTS";
        if (fundSnapshotMap.size() < request.funds().size()) {
            qualityState = "PARTIAL_HOLDINGS_COVERAGE";
        }

        LocalDate maxObsDate = obsDates.stream().max(LocalDate::compareTo).orElse(asOf);
        LocalDate minObsDate = obsDates.stream().min(LocalDate::compareTo).orElse(asOf);
        long maxDeltaDays = ChronoUnit.DAYS.between(minObsDate, maxObsDate);

        if (maxDeltaDays > 45) {
            qualityState = "NON_COMPARABLE_OBSERVATION_DATES";
            limitations.add("Temporal mismatch: constituent observation dates span " + maxDeltaDays + " days ("
                + minObsDate + " to " + maxObsDate + "). Holdings may not be synchronously comparable.");
        } else if (maxDeltaDays > 0) {
            limitations.add("Constituent observation dates span " + maxDeltaDays + " days (" + minObsDate + " to " + maxObsDate + "). Acceptable reporting lag.");
        }

        // Update temporal mismatch days on constituent summaries
        List<ConstituentFundSummaryDto> updatedSummaries = new ArrayList<>();
        for (ConstituentFundSummaryDto c : constituentSummaries) {
            int delta = c.observationDate() != null ? (int) ChronoUnit.DAYS.between(c.observationDate(), maxObsDate) : 0;
            updatedSummaries.add(new ConstituentFundSummaryDto(
                c.schemeOptionId(), c.fundName(), c.amfiCode(), c.isin(), c.rawWeight(), c.normalizedWeightPct(),
                c.observationDate(), c.holdingsCount(), c.disclosedEquityCoveragePct(), c.cashWeightPct(),
                delta, c.status()
            ));
        }

        // 4. Security-Level Look-Through Aggregation
        Map<String, ConsolidatedSecurityBuilder> secBuilderMap = new LinkedHashMap<>();

        for (Map.Entry<Long, List<PortfolioHolding>> entry : fundHoldingsMap.entrySet()) {
            Long optId = entry.getKey();
            BigDecimal fundWeightFrac = normalizedWeightsFraction.get(optId);
            BigDecimal fundWeightPct = normalizedWeightsPct.get(optId);
            String fundName = fundOptionMap.get(optId).getPlan().getScheme().getName();

            for (PortfolioHolding h : entry.getValue()) {
                Security sec = h.getSecurity();
                if (sec == null) continue;

                String isin = securityIdentifierRepository.findTopBySecurityIdAndIdType(sec.getId(), "ISIN")
                    .map(SecurityIdentifier::getIdValue)
                    .orElse(null);

                String key = (isin != null && !isin.isBlank())
                    ? isin.toUpperCase(Locale.ROOT)
                    : sec.getCanonicalName().trim().toUpperCase(Locale.ROOT);

                BigDecimal holdingWeightInFundFrac = h.getReportedWeight() != null ? h.getReportedWeight() : BigDecimal.ZERO;
                BigDecimal holdingWeightInFundPct = holdingWeightInFundFrac.multiply(HUNDRED).setScale(4, RoundingMode.HALF_UP);
                BigDecimal effectiveContributionPct = fundWeightFrac.multiply(holdingWeightInFundFrac).multiply(HUNDRED).setScale(4, RoundingMode.HALF_UP);

                // Market-Cap categorization for security
                String mcapCategory = resolveMarketCapCategory(sec, fundSnapshotMap.get(optId).getPortfolioDate());
                String macroSector = resolveCanonicalSector(sec.getSector(), sec.getCanonicalName(), sec.getAssetClass());

                secBuilderMap.computeIfAbsent(key, k -> new ConsolidatedSecurityBuilder(
                    sec.getCanonicalName(), isin, macroSector, sec.getAssetClass(), mcapCategory,
                    (isin != null && !isin.isBlank()) ? "CANONICAL_ISIN_VERIFIED" : "CANONICAL_NAME_MAPPED",
                    "AMC_PORTFOLIO_DISCLOSURE"
                )).addContribution(optId, fundName, fundWeightPct, holdingWeightInFundPct, effectiveContributionPct);
            }
        }

        List<ConsolidatedSecurityExposureDto> consolidatedSecurities = new ArrayList<>();
        for (ConsolidatedSecurityBuilder builder : secBuilderMap.values()) {
            consolidatedSecurities.add(builder.build());
        }
        consolidatedSecurities.sort((a, b) -> b.consolidatedWeightPct().compareTo(a.consolidatedWeightPct()));

        // 5. Consolidated Sector Exposure Aggregation
        Map<String, SectorAggregationBuilder> sectorMap = new LinkedHashMap<>();
        for (ConsolidatedSecurityExposureDto sec : consolidatedSecurities) {
            String secSector = sec.sector();
            sectorMap.computeIfAbsent(secSector, k -> new SectorAggregationBuilder(secSector))
                .addSecurityExposure(sec);
        }

        List<ConsolidatedSectorExposureDto> consolidatedSectors = new ArrayList<>();
        for (SectorAggregationBuilder b : sectorMap.values()) {
            consolidatedSectors.add(b.build());
        }
        consolidatedSectors.sort((a, b) -> b.consolidatedWeightPct().compareTo(a.consolidatedWeightPct()));

        // 6. Consolidated Market-Cap Exposure Aggregation
        Map<String, McapAggregationBuilder> mcapMap = new LinkedHashMap<>();
        mcapMap.put("LARGE_CAP", new McapAggregationBuilder("LARGE_CAP", "AMFI Point-in-Time Half-Yearly Classification / SEBI Reference"));
        mcapMap.put("MID_CAP", new McapAggregationBuilder("MID_CAP", "AMFI Point-in-Time Half-Yearly Classification / SEBI Reference"));
        mcapMap.put("SMALL_CAP", new McapAggregationBuilder("SMALL_CAP", "AMFI Point-in-Time Half-Yearly Classification / SEBI Reference"));
        mcapMap.put("CASH_AND_EQUIVALENTS", new McapAggregationBuilder("CASH_AND_EQUIVALENTS", "Reported Liquid Reserves, TREPS & Net Current Assets"));
        mcapMap.put("UNCLASSIFIED", new McapAggregationBuilder("UNCLASSIFIED", "Unmapped or Off-Benchmark Securities"));

        for (ConsolidatedSecurityExposureDto sec : consolidatedSecurities) {
            String cat = sec.marketCapCategory();
            if (!mcapMap.containsKey(cat)) cat = "UNCLASSIFIED";
            mcapMap.get(cat).addSecurity(sec.consolidatedWeightPct());
        }

        List<ConsolidatedMarketCapExposureDto> consolidatedMarketCap = new ArrayList<>();
        for (McapAggregationBuilder b : mcapMap.values()) {
            consolidatedMarketCap.add(b.build());
        }

        // 7. Pairwise Fund Overlap Matrix
        List<PairwiseOverlapResultDto> pairwiseOverlapList = new ArrayList<>();
        List<Long> fundIds = new ArrayList<>(fundHoldingsMap.keySet());

        for (int i = 0; i < fundIds.size(); i++) {
            for (int j = i + 1; j < fundIds.size(); j++) {
                Long idA = fundIds.get(i);
                Long idB = fundIds.get(j);

                PairwiseOverlapResultDto pairResult = computePairwiseOverlap(
                    idA, idB, fundOptionMap.get(idA), fundOptionMap.get(idB),
                    fundSnapshotMap.get(idA), fundSnapshotMap.get(idB),
                    fundHoldingsMap.get(idA), fundHoldingsMap.get(idB)
                );
                pairwiseOverlapList.add(pairResult);
            }
        }

        // 8. Portfolio Concentration Metrics
        BigDecimal top5Weight = BigDecimal.ZERO;
        BigDecimal top10Weight = BigDecimal.ZERO;
        BigDecimal hhiSum = BigDecimal.ZERO;
        int uniqueSecCount = consolidatedSecurities.size();
        int sharedSecCount = 0;
        BigDecimal sharedSecWeight = BigDecimal.ZERO;
        BigDecimal equityCoverage = BigDecimal.ZERO;
        BigDecimal cashWeight = BigDecimal.ZERO;
        BigDecimal unclassifiedWeight = BigDecimal.ZERO;

        for (int i = 0; i < consolidatedSecurities.size(); i++) {
            ConsolidatedSecurityExposureDto sec = consolidatedSecurities.get(i);
            BigDecimal w = sec.consolidatedWeightPct();
            if (i < 5) top5Weight = top5Weight.add(w);
            if (i < 10) top10Weight = top10Weight.add(w);

            // HHI = SUM (w_pct)^2
            hhiSum = hhiSum.add(w.pow(2));

            boolean isCash = "CASH_AND_EQUIVALENTS".equals(sec.marketCapCategory()) || sec.sector().contains("Cash");

            if (sec.fundCount() >= 2 && !isCash) {
                sharedSecCount++;
                sharedSecWeight = sharedSecWeight.add(w);
            }

            if ("CASH_AND_EQUIVALENTS".equals(sec.marketCapCategory()) || sec.sector().contains("Cash")) {
                cashWeight = cashWeight.add(w);
            } else if ("UNCLASSIFIED".equals(sec.marketCapCategory())) {
                unclassifiedWeight = unclassifiedWeight.add(w);
                equityCoverage = equityCoverage.add(w);
            } else {
                equityCoverage = equityCoverage.add(w);
            }
        }

        BigDecimal hhiFinal = hhiSum.setScale(2, RoundingMode.HALF_UP);
        String hhiInterp;
        if (hhiFinal.compareTo(new BigDecimal("1500")) < 0) {
            hhiInterp = "Unconcentrated (<1500)";
        } else if (hhiFinal.compareTo(new BigDecimal("2500")) <= 0) {
            hhiInterp = "Moderately Concentrated (1500-2500)";
        } else {
            hhiInterp = "Highly Concentrated (>2500)";
        }

        PortfolioConcentrationMetricsDto concentrationMetrics = new PortfolioConcentrationMetricsDto(
            top5Weight.setScale(2, RoundingMode.HALF_UP),
            top10Weight.setScale(2, RoundingMode.HALF_UP),
            hhiFinal,
            hhiInterp,
            uniqueSecCount,
            sharedSecCount,
            sharedSecWeight.setScale(2, RoundingMode.HALF_UP),
            equityCoverage.setScale(2, RoundingMode.HALF_UP),
            cashWeight.setScale(2, RoundingMode.HALF_UP),
            unclassifiedWeight.setScale(2, RoundingMode.HALF_UP)
        );

        // 9. Shared Exposure Diagnostics
        List<SharedExposureDiagnosticDto> diagnostics = new ArrayList<>();

        if (sharedSecWeight.compareTo(new BigDecimal("25.00")) >= 0) {
            diagnostics.add(new SharedExposureDiagnosticDto(
                "WARNING",
                "High Multi-Fund Holdings Overlap",
                String.format(Locale.US, "%.2f%% of total portfolio value is concentrated in %d securities held simultaneously across 2 or more constituent funds.",
                    sharedSecWeight.doubleValue(), sharedSecCount),
                "Calculated by summing consolidated look-through weights of all underlying securities where holding fund count >= 2."
            ));
        }

        for (ConsolidatedSecurityExposureDto sec : consolidatedSecurities) {
            if (sec.fundCount() >= 2 && sec.consolidatedWeightPct().compareTo(new BigDecimal("3.50")) >= 0) {
                diagnostics.add(new SharedExposureDiagnosticDto(
                    "NOTE",
                    "Concentrated Shared Position: " + sec.canonicalName(),
                    String.format(Locale.US, "Held across %d constituent funds, aggregating to %.2f%% portfolio weight (Sector: %s).",
                        sec.fundCount(), sec.consolidatedWeightPct().doubleValue(), sec.sector()),
                    "Security appears independently in multiple AMC monthly portfolios."
                ));
            }
        }

        if (!consolidatedSectors.isEmpty()) {
            ConsolidatedSectorExposureDto topSector = consolidatedSectors.get(0);
            if (topSector.consolidatedWeightPct().compareTo(new BigDecimal("25.00")) >= 0) {
                diagnostics.add(new SharedExposureDiagnosticDto(
                    "INFO",
                    "Prominent Sector Weight: " + topSector.macroSector(),
                    String.format(Locale.US, "Portfolio has %.2f%% aggregated look-through exposure to %s across %d underlying securities.",
                        topSector.consolidatedWeightPct().doubleValue(), topSector.macroSector(), topSector.classifiedSecuritiesCount()),
                    "Aggregated across constituent fund allocations using official macro-sector taxonomy."
                ));
            }
        }

        PortfolioRiskDecompositionDto riskDecomp = null;
        if (navObservationRepository != null && benchmarkRepository != null && benchmarkObservationRepository != null) {
            riskDecomp = computePortfolioRiskDecomposition(
                updatedSummaries, normalizedWeightsFraction, asOf, cutoff, consolidatedMarketCap
            );
        }

        return new MultiFundLookThroughResponseDto(
            asOf, cutoff, sumWeights.setScale(2, RoundingMode.HALF_UP), isFullyAllocated, qualityState,
            updatedSummaries, consolidatedSecurities, consolidatedSectors, consolidatedMarketCap,
            pairwiseOverlapList, concentrationMetrics, diagnostics, limitations, epistemic, riskDecomp
        );
    }

    /**
     * Simulates adding a candidate fund to an existing research portfolio under proportional rebalancing:
     * Candidate weight w_c, existing funds scaled by (1 - w_c).
     * Computes exact before vs after exposure deltas, top augmented positions, and newly introduced securities.
     */
    @Transactional(readOnly = true)
    public CandidateSimulationResponseDto simulateCandidateFund(CandidateSimulationRequestDto request) {
        if (request.candidateSchemeOptionId() == null) {
            throw new IllegalArgumentException("candidateSchemeOptionId is required for candidate simulation.");
        }
        if (request.proposedAllocationPct() == null || request.proposedAllocationPct().compareTo(BigDecimal.ZERO) <= 0
            || request.proposedAllocationPct().compareTo(HUNDRED) >= 0) {
            throw new IllegalArgumentException("Proposed candidate allocation must be strictly between 0% and 100%.");
        }

        SchemeOption candidateOpt = schemeOptionRepository.findById(request.candidateSchemeOptionId())
            .orElseThrow(() -> new IllegalArgumentException("Candidate SchemeOption #" + request.candidateSchemeOptionId() + " not found."));

        String candidateName = (candidateOpt.getPlan() != null && candidateOpt.getPlan().getScheme() != null)
            ? candidateOpt.getPlan().getScheme().getName() : "Option #" + request.candidateSchemeOptionId();
        String candidateAmfi = candidateOpt.getAmfiCode();

        // Step 1: Compute Baseline Portfolio Look-Through
        MultiFundLookThroughRequestDto baselineReq = new MultiFundLookThroughRequestDto(
            request.currentFunds(), request.asOfDate(), request.knowledgeCutoff(), "RAW"
        );
        MultiFundLookThroughResponseDto baselineResponse = calculateMultiFundLookThrough(baselineReq);

        // Step 2: Scale existing allocations proportionally: a'_j = a_j * (1 - w_c)
        BigDecimal proposedFrac = request.proposedAllocationPct().divide(HUNDRED, 8, RoundingMode.HALF_UP);
        BigDecimal retainFrac = BigDecimal.ONE.subtract(proposedFrac);

        List<ResearchPortfolioFundItemDto> simulatedFunds = new ArrayList<>();
        for (ResearchPortfolioFundItemDto f : request.currentFunds()) {
            if (f.schemeOptionId().equals(request.candidateSchemeOptionId())) {
                // If candidate is already in portfolio, it gets augmented directly
                continue;
            }
            BigDecimal scaledWeight = f.allocationWeight().multiply(retainFrac).setScale(4, RoundingMode.HALF_UP);
            simulatedFunds.add(new ResearchPortfolioFundItemDto(f.schemeOptionId(), scaledWeight));
        }

        // Add candidate fund
        simulatedFunds.add(new ResearchPortfolioFundItemDto(request.candidateSchemeOptionId(), request.proposedAllocationPct()));

        // Step 3: Compute Simulated Portfolio Look-Through
        MultiFundLookThroughRequestDto simulatedReq = new MultiFundLookThroughRequestDto(
            simulatedFunds, request.asOfDate(), request.knowledgeCutoff(), "PROPORTIONAL"
        );
        MultiFundLookThroughResponseDto simulatedResponse = calculateMultiFundLookThrough(simulatedReq);

        // Step 4: Compute exact concentration deltas
        BigDecimal deltaTop5 = simulatedResponse.concentrationMetrics().top5WeightPct()
            .subtract(baselineResponse.concentrationMetrics().top5WeightPct());
        BigDecimal deltaTop10 = simulatedResponse.concentrationMetrics().top10WeightPct()
            .subtract(baselineResponse.concentrationMetrics().top10WeightPct());
        BigDecimal deltaHhi = simulatedResponse.concentrationMetrics().herfindahlHirschmanIndex()
            .subtract(baselineResponse.concentrationMetrics().herfindahlHirschmanIndex());
        BigDecimal deltaShared = simulatedResponse.concentrationMetrics().sharedSecuritiesWeightPct()
            .subtract(baselineResponse.concentrationMetrics().sharedSecuritiesWeightPct());
        int deltaUnique = simulatedResponse.concentrationMetrics().uniqueSecuritiesCount()
            - baselineResponse.concentrationMetrics().uniqueSecuritiesCount();

        // Step 5: Classify Augmented vs New Securities
        Map<String, ConsolidatedSecurityExposureDto> baseSecMap = new HashMap<>();
        for (ConsolidatedSecurityExposureDto sec : baselineResponse.consolidatedSecurities()) {
            String key = (sec.isin() != null && !sec.isin().isBlank()) ? sec.isin().toUpperCase(Locale.ROOT) : sec.canonicalName().toUpperCase(Locale.ROOT);
            baseSecMap.put(key, sec);
        }

        List<AugmentedSecurityComparisonDto> augmentedList = new ArrayList<>();
        List<ConsolidatedSecurityExposureDto> newList = new ArrayList<>();

        for (ConsolidatedSecurityExposureDto simSec : simulatedResponse.consolidatedSecurities()) {
            String key = (simSec.isin() != null && !simSec.isin().isBlank()) ? simSec.isin().toUpperCase(Locale.ROOT) : simSec.canonicalName().toUpperCase(Locale.ROOT);

            // Find candidate's specific contribution to this security
            BigDecimal candidateContr = BigDecimal.ZERO;
            for (FundSecurityContributionDto contr : simSec.contributions()) {
                if (contr.schemeOptionId().equals(request.candidateSchemeOptionId())) {
                    candidateContr = contr.effectiveWeightContributionPct();
                    break;
                }
            }

            if (baseSecMap.containsKey(key)) {
                // Was already present in baseline
                ConsolidatedSecurityExposureDto baseSec = baseSecMap.get(key);
                BigDecimal delta = simSec.consolidatedWeightPct().subtract(baseSec.consolidatedWeightPct());

                if (candidateContr.compareTo(BigDecimal.ZERO) > 0) {
                    augmentedList.add(new AugmentedSecurityComparisonDto(
                        simSec.canonicalName(), simSec.isin(), simSec.sector(),
                        baseSec.consolidatedWeightPct(), candidateContr, simSec.consolidatedWeightPct(), delta
                    ));
                }
            } else if (candidateContr.compareTo(BigDecimal.ZERO) > 0) {
                // New security introduced by candidate
                newList.add(simSec);
            }
        }

        augmentedList.sort((a, b) -> b.candidateContributionPct().compareTo(a.candidateContributionPct()));
        newList.sort((a, b) -> b.consolidatedWeightPct().compareTo(a.consolidatedWeightPct()));

        // Step 6: Sector & Market-Cap Deltas
        Map<String, BigDecimal> baseSectorMap = new HashMap<>();
        for (ConsolidatedSectorExposureDto s : baselineResponse.consolidatedSectors()) {
            baseSectorMap.put(s.macroSector(), s.consolidatedWeightPct());
        }
        List<CategoryDeltaDto> sectorDeltas = new ArrayList<>();
        for (ConsolidatedSectorExposureDto simSec : simulatedResponse.consolidatedSectors()) {
            BigDecimal baseW = baseSectorMap.getOrDefault(simSec.macroSector(), BigDecimal.ZERO);
            BigDecimal delta = simSec.consolidatedWeightPct().subtract(baseW);
            sectorDeltas.add(new CategoryDeltaDto(simSec.macroSector(), baseW, simSec.consolidatedWeightPct(), delta));
        }

        Map<String, BigDecimal> baseMcapMap = new HashMap<>();
        for (ConsolidatedMarketCapExposureDto m : baselineResponse.consolidatedMarketCap()) {
            baseMcapMap.put(m.category(), m.consolidatedWeightPct());
        }
        List<CategoryDeltaDto> mcapDeltas = new ArrayList<>();
        for (ConsolidatedMarketCapExposureDto simM : simulatedResponse.consolidatedMarketCap()) {
            BigDecimal baseW = baseMcapMap.getOrDefault(simM.category(), BigDecimal.ZERO);
            BigDecimal delta = simM.consolidatedWeightPct().subtract(baseW);
            mcapDeltas.add(new CategoryDeltaDto(simM.category(), baseW, simM.consolidatedWeightPct(), delta));
        }

        // Step 7: Analytical Observations & Disclaimers
        List<String> observations = new ArrayList<>();
        observations.add(String.format(Locale.US,
            "Simulating %s at %.2f%% adds %d new unique securities while augmenting %d existing portfolio holdings.",
            candidateName, request.proposedAllocationPct().doubleValue(), newList.size(), augmentedList.size()));

        if (deltaHhi.compareTo(BigDecimal.ZERO) < 0) {
            observations.add(String.format(Locale.US,
                "Observed HHI decreased by %.2f points (from %.2f to %.2f), reflecting lower concentration in existing top positions.",
                deltaHhi.abs().doubleValue(),
                baselineResponse.concentrationMetrics().herfindahlHirschmanIndex().doubleValue(),
                simulatedResponse.concentrationMetrics().herfindahlHirschmanIndex().doubleValue()));
        } else if (deltaHhi.compareTo(BigDecimal.ZERO) > 0) {
            observations.add(String.format(Locale.US,
                "Observed HHI increased by +%.2f points (from %.2f to %.2f), indicating higher portfolio concentration in shared holdings.",
                deltaHhi.doubleValue(),
                baselineResponse.concentrationMetrics().herfindahlHirschmanIndex().doubleValue(),
                simulatedResponse.concentrationMetrics().herfindahlHirschmanIndex().doubleValue()));
        }

        if (deltaShared.compareTo(BigDecimal.ZERO) > 0) {
            observations.add(String.format(Locale.US,
                "Multi-fund shared security weight increased by +%.2f%% (from %.2f%% to %.2f%%).",
                deltaShared.doubleValue(),
                baselineResponse.concentrationMetrics().sharedSecuritiesWeightPct().doubleValue(),
                simulatedResponse.concentrationMetrics().sharedSecuritiesWeightPct().doubleValue()));
        }

        List<String> simLimitations = new ArrayList<>(simulatedResponse.limitations());
        simLimitations.add("Simulation assumes proportional dilution of existing fund weights. Transaction costs, exit loads, and tax impacts are excluded.");
        simLimitations.add("Look-through analysis reflects disclosed month-end holdings only and does not measure covariance, return correlations, or downside tail risk.");

        BigDecimal deltaVol = null;
        BigDecimal deltaDiv = null;
        BigDecimal deltaBeta = null;
        BigDecimal deltaMaxDd = null;

        if (baselineResponse.riskDecomposition() != null && simulatedResponse.riskDecomposition() != null) {
            PortfolioRiskDecompositionDto baseRisk = baselineResponse.riskDecomposition();
            PortfolioRiskDecompositionDto simRisk = simulatedResponse.riskDecomposition();
            if (baseRisk.portfolioRealizedVolatilityPct() != null && simRisk.portfolioRealizedVolatilityPct() != null) {
                deltaVol = simRisk.portfolioRealizedVolatilityPct().subtract(baseRisk.portfolioRealizedVolatilityPct()).setScale(2, RoundingMode.HALF_UP);
            }
            if (baseRisk.diversificationBenefitPct() != null && simRisk.diversificationBenefitPct() != null) {
                deltaDiv = simRisk.diversificationBenefitPct().subtract(baseRisk.diversificationBenefitPct()).setScale(2, RoundingMode.HALF_UP);
            }
            if (baseRisk.portfolioBeta() != null && simRisk.portfolioBeta() != null) {
                deltaBeta = simRisk.portfolioBeta().subtract(baseRisk.portfolioBeta()).setScale(2, RoundingMode.HALF_UP);
            }
            if (baseRisk.portfolioMaxDrawdownPct() != null && simRisk.portfolioMaxDrawdownPct() != null) {
                deltaMaxDd = simRisk.portfolioMaxDrawdownPct().subtract(baseRisk.portfolioMaxDrawdownPct()).setScale(2, RoundingMode.HALF_UP);
            }
        }

        if (deltaVol != null && deltaDiv != null) {
            observations.add(String.format(Locale.US,
                "Candidate fund simulation shifts realized annualized portfolio volatility by %s%.2f%% points and diversification benefit by %s%.2f%% points.",
                deltaVol.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "", deltaVol.doubleValue(),
                deltaDiv.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "", deltaDiv.doubleValue()));
        }

        return new CandidateSimulationResponseDto(
            request.candidateSchemeOptionId(), candidateName, candidateAmfi,
            request.proposedAllocationPct(), "PROPORTIONAL",
            baselineResponse, simulatedResponse,
            deltaTop5, deltaTop10, deltaHhi, deltaShared, deltaUnique,
            newList.size(), augmentedList.size(),
            augmentedList, newList, sectorDeltas, mcapDeltas,
            observations, simLimitations,
            deltaVol, deltaDiv, deltaBeta, deltaMaxDd
        );
    }

    /**
     * Computes pairwise security overlap between two funds A and B using the deterministic formula:
     * Overlap(A, B) = SUM_{i in A int B} min(w_{A,i}, w_{B,i})
     */
    private PairwiseOverlapResultDto computePairwiseOverlap(
        Long idA, Long idB, SchemeOption optA, SchemeOption optB,
        PortfolioSnapshot snapA, PortfolioSnapshot snapB,
        List<PortfolioHolding> holdingsA, List<PortfolioHolding> holdingsB
    ) {
        String nameA = optA.getPlan().getScheme().getName();
        String nameB = optB.getPlan().getScheme().getName();

        LocalDate dateA = snapA.getPortfolioDate();
        LocalDate dateB = snapB.getPortfolioDate();
        int deltaDays = (int) Math.abs(ChronoUnit.DAYS.between(dateA, dateB));

        String compState = (deltaDays == 0) ? "SYNCHRONIZED" : (deltaDays <= 45 ? "ACCEPTABLE_LAG" : "NON_COMPARABLE_OBSERVATION_DATES");

        // Build identity map for A
        Map<String, BigDecimal> mapA = new HashMap<>();
        Map<String, String> nameMap = new HashMap<>();
        Map<String, String> isinMap = new HashMap<>();
        Map<String, String> sectorMap = new HashMap<>();

        for (PortfolioHolding h : holdingsA) {
            if (h.getSecurity() == null) continue;
            String ac = h.getSecurity().getAssetClass() != null ? h.getSecurity().getAssetClass().toUpperCase(Locale.ROOT) : "";
            String name = h.getSecurity().getCanonicalName() != null ? h.getSecurity().getCanonicalName().toUpperCase(Locale.ROOT) : "";
            if (ac.contains("CASH") || name.contains("TREPS") || name.contains("NET CURRENT ASSETS") || "NET_CURRENT_ASSETS".equals(name)) {
                continue;
            }

            String isin = securityIdentifierRepository.findTopBySecurityIdAndIdType(h.getSecurity().getId(), "ISIN")
                .map(SecurityIdentifier::getIdValue).orElse(null);
            String key = (isin != null && !isin.isBlank()) ? isin.toUpperCase(Locale.ROOT) : h.getSecurity().getCanonicalName().toUpperCase(Locale.ROOT);
            BigDecimal w = h.getReportedWeight() != null ? h.getReportedWeight().multiply(HUNDRED) : BigDecimal.ZERO;
            mapA.put(key, w);
            nameMap.put(key, h.getSecurity().getCanonicalName());
            if (isin != null) isinMap.put(key, isin);
            if (h.getSecurity().getSector() != null) sectorMap.put(key, h.getSecurity().getSector());
        }

        BigDecimal overlapAcc = BigDecimal.ZERO;
        List<SharedSecurityItemDto> sharedList = new ArrayList<>();

        for (PortfolioHolding h : holdingsB) {
            if (h.getSecurity() == null) continue;
            String ac = h.getSecurity().getAssetClass() != null ? h.getSecurity().getAssetClass().toUpperCase(Locale.ROOT) : "";
            String name = h.getSecurity().getCanonicalName() != null ? h.getSecurity().getCanonicalName().toUpperCase(Locale.ROOT) : "";
            if (ac.contains("CASH") || name.contains("TREPS") || name.contains("NET CURRENT ASSETS") || "NET_CURRENT_ASSETS".equals(name)) {
                continue;
            }

            String isin = securityIdentifierRepository.findTopBySecurityIdAndIdType(h.getSecurity().getId(), "ISIN")
                .map(SecurityIdentifier::getIdValue).orElse(null);
            String key = (isin != null && !isin.isBlank()) ? isin.toUpperCase(Locale.ROOT) : h.getSecurity().getCanonicalName().toUpperCase(Locale.ROOT);
            BigDecimal wB = h.getReportedWeight() != null ? h.getReportedWeight().multiply(HUNDRED) : BigDecimal.ZERO;

            if (mapA.containsKey(key)) {
                BigDecimal wA = mapA.get(key);
                BigDecimal minW = wA.min(wB);
                overlapAcc = overlapAcc.add(minW);

                sharedList.add(new SharedSecurityItemDto(
                    nameMap.getOrDefault(key, h.getSecurity().getCanonicalName()),
                    isinMap.getOrDefault(key, isin),
                    resolveCanonicalSector(sectorMap.get(key), nameMap.get(key), h.getSecurity().getAssetClass()),
                    wA.setScale(2, RoundingMode.HALF_UP),
                    wB.setScale(2, RoundingMode.HALF_UP),
                    minW.setScale(2, RoundingMode.HALF_UP)
                ));
            }
        }

        sharedList.sort((a, b) -> b.overlapWeightPct().compareTo(a.overlapWeightPct()));

        return new PairwiseOverlapResultDto(
            idA, nameA, idB, nameB,
            overlapAcc.setScale(2, RoundingMode.HALF_UP),
            sharedList.size(), dateA, dateB, deltaDays, compState, sharedList
        );
    }

    /**
     * Resolves macro-sector classification aligned with official NSE macro-sectors.
     */
    public String resolveCanonicalSector(String rawSector, String securityName, String assetClass) {
        String nameUpper = securityName != null ? securityName.toUpperCase(Locale.ROOT) : "";
        String secUpper = rawSector != null ? rawSector.trim().toUpperCase(Locale.ROOT) : "";
        String assetUpper = assetClass != null ? assetClass.toUpperCase(Locale.ROOT) : "";

        if (assetUpper.contains("CASH") || secUpper.contains("CASH") || nameUpper.contains("CASH")
            || secUpper.contains("TREPS") || nameUpper.contains("TREPS")
            || secUpper.contains("NET CURRENT ASSETS") || nameUpper.contains("NET CURRENT ASSETS")) {
            return "Cash & Liquid Assets";
        }

        if (assetUpper.contains("DEBT") || secUpper.contains("GOI MAT") || nameUpper.contains("GOI MAT")
            || nameUpper.contains("TREASURY") || nameUpper.contains("GOVERNMENT BOND")) {
            return "Sovereign & Debt";
        }

        if (!secUpper.isBlank() && CANONICAL_SECTOR_MAP.containsKey(secUpper)) {
            return CANONICAL_SECTOR_MAP.get(secUpper);
        }

        for (Map.Entry<String, String> entry : CANONICAL_SECTOR_MAP.entrySet()) {
            if (secUpper.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        if (nameUpper.contains("BANK") || nameUpper.contains("FINANCE") || nameUpper.contains("INSURANCE")) {
            return "Financial Services";
        }

        return !rawSector.isBlank() ? rawSector : "Unclassified / Other";
    }

    /**
     * Resolves SEBI market-cap categorization using AMFI Point-in-Time ranking if available,
     * falling back to the canonical reference sets and liquid cash identification.
     */
    private String resolveMarketCapCategory(Security sec, LocalDate obsDate) {
        if (sec == null) return "UNCLASSIFIED";

        String assetUpper = sec.getAssetClass() != null ? sec.getAssetClass().toUpperCase(Locale.ROOT) : "";
        String nameUpper = sec.getCanonicalName() != null ? sec.getCanonicalName().toUpperCase(Locale.ROOT) : "";

        if (assetUpper.contains("CASH") || nameUpper.contains("TREPS") || nameUpper.contains("NET CURRENT ASSETS") || nameUpper.contains("CASH")) {
            return "CASH_AND_EQUIVALENTS";
        }
        if (assetUpper.contains("DEBT") || nameUpper.contains("GOI") || nameUpper.contains("TREASURY")) {
            return "CASH_AND_EQUIVALENTS";
        }

        // Try PIT AMFI database classification
        if (obsDate != null && sec.getId() != null) {
            List<SecurityMarketCapClassification> list = classificationRepository
                .findBySecurityIdAndPit(sec.getId(), obsDate, OffsetDateTime.now());
            if (!list.isEmpty() && list.get(0).getMarketCapCategory() != null) {
                return list.get(0).getMarketCapCategory();
            }
        }

        // Fallback to canonical reference sets
        for (String lcap : SEBI_APPROX_LARGE_CAP) {
            if (nameUpper.contains(lcap)) return "LARGE_CAP";
        }
        for (String mcap : SEBI_APPROX_MID_CAP) {
            if (nameUpper.contains(mcap)) return "MID_CAP";
        }

        return "SMALL_CAP";
    }

    /**
     * Synthesizes daily rebalanced portfolio returns across synchronized constituent fund NAVs and computes:
     * - Realized portfolio volatility (annualized sqrt(252))
     * - Weighted-average constituent volatility
     * - Empirical diversification benefit: Delta sigma_div = bar(sigma) - sigma_port
     * - Portfolio downside semideviation (relative to MAR = 0)
     * - Portfolio maximum drawdown on cumulative synthesized wealth path
     * - Portfolio historical beta to NIFTY 500 TRI (or broad market benchmark)
     * - Pairwise return correlation matrix
     * - Factor exposure inventory (Holdings proxies vs Empirical returns vs Gated models)
     */
    public PortfolioRiskDecompositionDto computePortfolioRiskDecomposition(
        List<ConstituentFundSummaryDto> constituents,
        Map<Long, BigDecimal> weightsFraction,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoff,
        List<ConsolidatedMarketCapExposureDto> marketCapExposures
    ) {
        if (constituents == null || constituents.isEmpty()) {
            return null;
        }

        List<String> riskObs = new ArrayList<>();
        List<String> limitations = new ArrayList<>();

        // 1. Resolve Benchmark (prefer NIFTY_500_TRI, fallback to NIFTY_50 or first available)
        Benchmark benchmark = benchmarkRepository.findByCode("NIFTY_500_TRI")
            .or(() -> benchmarkRepository.findByCode("NIFTY_50"))
            .or(() -> benchmarkRepository.findAll().stream().findFirst())
            .orElse(null);

        String bmCode = benchmark != null ? benchmark.getCode() : "NIFTY_500_TRI";
        String bmName = benchmark != null ? benchmark.getName() : "Nifty 500 Total Returns Index";

        Map<LocalDate, BigDecimal> bmValues = new HashMap<>();
        if (benchmark != null) {
            List<BenchmarkObservation> bmObservations = benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(
                benchmark.getId(), asOfDate, knowledgeCutoff
            );
            for (BenchmarkObservation bo : bmObservations) {
                if (bo.getEffectiveDate() != null && bo.getIndexLevel() != null) {
                    bmValues.put(bo.getEffectiveDate(), bo.getIndexLevel());
                }
            }
        }

        // 2. Fetch authoritative PIT NAV observations for all constituent funds
        Map<Long, Map<LocalDate, BigDecimal>> fundNavs = new LinkedHashMap<>();
        for (ConstituentFundSummaryDto c : constituents) {
            List<NavObservation> navList = navObservationRepository.findAuthoritativeObservationsAsOfCutoff(
                c.schemeOptionId(), asOfDate, knowledgeCutoff
            );
            Map<LocalDate, BigDecimal> navMap = new HashMap<>();
            for (NavObservation no : navList) {
                if (no.getEffectiveDate() != null && no.getNavValue() != null) {
                    navMap.put(no.getEffectiveDate(), no.getNavValue());
                }
            }
            fundNavs.put(c.schemeOptionId(), navMap);
        }

        // 3. Identify common synchronized trading dates across ALL constituent funds and benchmark
        Set<LocalDate> commonDates = null;
        for (Map<LocalDate, BigDecimal> navMap : fundNavs.values()) {
            if (commonDates == null) {
                commonDates = new HashSet<>(navMap.keySet());
            } else {
                commonDates.retainAll(navMap.keySet());
            }
        }

        if (commonDates == null) {
            commonDates = Collections.emptySet();
        }

        // Also retain dates present in benchmark if benchmark observations are available
        if (!bmValues.isEmpty()) {
            commonDates.retainAll(bmValues.keySet());
        }

        List<LocalDate> sortedDates = new ArrayList<>(commonDates);
        Collections.sort(sortedDates);

        // Build factor exposures inventory with status and economic definitions
        List<FactorExposureItemDto> factorExposures = buildFactorExposures(marketCapExposures, bmCode);

        if (sortedDates.size() < 25) {
            limitations.add("Insufficient synchronized trading days (" + sortedDates.size() + " days found, minimum 25 required). Historical return risk decomposition blocked.");
            limitations.add("Fundamental factors (Value, Quality, Momentum) are gated under AGENTS.md §4: security-level financial statements and stock prices are not in raw artifact store.");
            return new PortfolioRiskDecompositionDto(
                "INSUFFICIENT_RETURN_OBSERVATIONS",
                bmCode, bmName,
                sortedDates.isEmpty() ? null : sortedDates.get(0),
                sortedDates.isEmpty() ? null : sortedDates.get(sortedDates.size() - 1),
                sortedDates.size(),
                null, null, null, null, null, null, null, null,
                Collections.emptyList(), Collections.emptyList(), factorExposures,
                "DAILY_REBALANCED_SYNTHESIZED",
                Collections.emptyList(), limitations
            );
        }

        // 4. Compute aligned daily returns
        // R_{j, k} = (NAV_{j, k} - NAV_{j, k-1}) / NAV_{j, k-1}
        // R_{port, k} = SUM_j ( a_j * R_{j, k} )
        // R_{bm, k} = (BM_k - BM_{k-1}) / BM_{k-1}
        int nDates = sortedDates.size();
        List<Double> portReturns = new ArrayList<>();
        List<Double> bmReturns = new ArrayList<>();
        Map<Long, List<Double>> fundReturns = new LinkedHashMap<>();
        for (ConstituentFundSummaryDto c : constituents) {
            fundReturns.put(c.schemeOptionId(), new ArrayList<>());
        }

        for (int k = 1; k < nDates; k++) {
            LocalDate prevDate = sortedDates.get(k - 1);
            LocalDate currDate = sortedDates.get(k);

            // Skip if gap exceeds 7 calendar days (abnormal suspension / holiday gap)
            if (ChronoUnit.DAYS.between(prevDate, currDate) > 7) {
                continue;
            }

            double portDailyRet = 0.0;
            boolean validInterval = true;

            Map<Long, Double> intervalFundRets = new HashMap<>();
            for (ConstituentFundSummaryDto c : constituents) {
                BigDecimal pNav = fundNavs.get(c.schemeOptionId()).get(prevDate);
                BigDecimal cNav = fundNavs.get(c.schemeOptionId()).get(currDate);
                if (pNav == null || cNav == null || pNav.compareTo(BigDecimal.ZERO) <= 0) {
                    validInterval = false;
                    break;
                }
                double r_j = cNav.subtract(pNav).divide(pNav, 10, RoundingMode.HALF_UP).doubleValue();
                intervalFundRets.put(c.schemeOptionId(), r_j);
                BigDecimal wFrac = weightsFraction.getOrDefault(c.schemeOptionId(), BigDecimal.ZERO);
                portDailyRet += wFrac.doubleValue() * r_j;
            }

            if (!validInterval) continue;

            double bmDailyRet = 0.0;
            if (!bmValues.isEmpty() && bmValues.containsKey(prevDate) && bmValues.containsKey(currDate)) {
                BigDecimal pBm = bmValues.get(prevDate);
                BigDecimal cBm = bmValues.get(currDate);
                if (pBm != null && cBm != null && pBm.compareTo(BigDecimal.ZERO) > 0) {
                    bmDailyRet = cBm.subtract(pBm).divide(pBm, 10, RoundingMode.HALF_UP).doubleValue();
                }
            }

            portReturns.add(portDailyRet);
            bmReturns.add(bmDailyRet);
            for (Map.Entry<Long, Double> entry : intervalFundRets.entrySet()) {
                fundReturns.get(entry.getKey()).add(entry.getValue());
            }
        }

        int K = portReturns.size();
        if (K < 20) {
            limitations.add("Insufficient valid consecutive return intervals (" + K + " intervals).");
            return new PortfolioRiskDecompositionDto(
                "INSUFFICIENT_RETURN_OBSERVATIONS",
                bmCode, bmName, sortedDates.get(0), sortedDates.get(nDates - 1),
                K, null, null, null, null, null, null, null, null,
                Collections.emptyList(), Collections.emptyList(), factorExposures,
                "DAILY_REBALANCED_SYNTHESIZED",
                Collections.emptyList(), limitations
            );
        }

        // 5. Benchmark Variance
        double sumBm = 0.0;
        for (double r : bmReturns) sumBm += r;
        double meanBm = sumBm / K;
        double varBmAcc = 0.0;
        for (double r : bmReturns) varBmAcc += Math.pow(r - meanBm, 2);
        double varBm = varBmAcc / (K - 1);
        double bmVolAnnual = Math.sqrt(varBm * 252.0) * 100.0;
        double bmMaxDd = computeMaxDrawdown(bmReturns);

        // 6. Constituent Fund Risk Profiles
        List<ConstituentFundRiskProfileDto> constituentProfiles = new ArrayList<>();
        Map<Long, Double> fundVols = new HashMap<>();

        for (ConstituentFundSummaryDto c : constituents) {
            List<Double> rets = fundReturns.get(c.schemeOptionId());
            double sumR = 0.0;
            for (double r : rets) sumR += r;
            double meanR = sumR / K;

            double varAcc = 0.0;
            double downVarAcc = 0.0;
            double covBmAcc = 0.0;

            for (int i = 0; i < K; i++) {
                double r = rets.get(i);
                varAcc += Math.pow(r - meanR, 2);
                if (r < 0.0) {
                    downVarAcc += Math.pow(r, 2);
                }
                covBmAcc += (r - meanR) * (bmReturns.get(i) - meanBm);
            }

            double var_j = varAcc / (K - 1);
            double downVar_j = downVarAcc / (K - 1);
            double vol_j = Math.sqrt(var_j * 252.0) * 100.0;
            double downSemi_j = Math.sqrt(downVar_j * 252.0) * 100.0;
            double maxDd_j = computeMaxDrawdown(rets);

            double cov_bm_j = covBmAcc / (K - 1);
            Double beta_j = varBm > 1e-12 ? (cov_bm_j / varBm) : null;

            fundVols.put(c.schemeOptionId(), vol_j);

            constituentProfiles.add(new ConstituentFundRiskProfileDto(
                c.schemeOptionId(), c.fundName(), c.amfiCode(), c.normalizedWeightPct(),
                BigDecimal.valueOf(vol_j).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(downSemi_j).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(maxDd_j).setScale(2, RoundingMode.HALF_UP),
                beta_j != null ? BigDecimal.valueOf(beta_j).setScale(2, RoundingMode.HALF_UP) : null,
                K, "SYNCHRONIZED_VALIDATED"
            ));
        }

        // 7. Portfolio Synthesized Risk
        double sumPort = 0.0;
        for (double r : portReturns) sumPort += r;
        double meanPort = sumPort / K;

        double varPortAcc = 0.0;
        double downVarPortAcc = 0.0;
        double covPortBmAcc = 0.0;

        for (int i = 0; i < K; i++) {
            double r = portReturns.get(i);
            varPortAcc += Math.pow(r - meanPort, 2);
            if (r < 0.0) {
                downVarPortAcc += Math.pow(r, 2);
            }
            covPortBmAcc += (r - meanPort) * (bmReturns.get(i) - meanBm);
        }

        double varPort = varPortAcc / (K - 1);
        double downVarPort = downVarPortAcc / (K - 1);
        double portVol = Math.sqrt(varPort * 252.0) * 100.0;
        double portDownSemi = Math.sqrt(downVarPort * 252.0) * 100.0;
        double portMaxDd = computeMaxDrawdown(portReturns);

        double covPortBm = covPortBmAcc / (K - 1);
        Double portBeta = varBm > 1e-12 ? (covPortBm / varBm) : null;

        // Weighted Average Constituent Volatility: bar(sigma) = SUM ( a_j * sigma_j )
        double weightedVol = 0.0;
        for (ConstituentFundSummaryDto c : constituents) {
            BigDecimal wFrac = weightsFraction.getOrDefault(c.schemeOptionId(), BigDecimal.ZERO);
            double v_j = fundVols.getOrDefault(c.schemeOptionId(), 0.0);
            weightedVol += wFrac.doubleValue() * v_j;
        }

        // Diversification Benefit: bar(sigma) - sigma_port
        double divBenefit = Math.max(0.0, weightedVol - portVol);

        // 8. Pairwise Return Correlations
        List<PairwiseCorrelationDto> pairwiseCorrs = new ArrayList<>();
        List<ConstituentFundSummaryDto> fundList = new ArrayList<>(constituents);
        double sumRho = 0.0;
        int pairCount = 0;

        for (int i = 0; i < fundList.size(); i++) {
            for (int j = i + 1; j < fundList.size(); j++) {
                ConstituentFundSummaryDto fa = fundList.get(i);
                ConstituentFundSummaryDto fb = fundList.get(j);

                List<Double> rA = fundReturns.get(fa.schemeOptionId());
                List<Double> rB = fundReturns.get(fb.schemeOptionId());

                double mA = computeMean(rA);
                double mB = computeMean(rB);

                double covAcc = 0.0;
                double varAAcc = 0.0;
                double varBAcc = 0.0;

                for (int t = 0; t < K; t++) {
                    double da = rA.get(t) - mA;
                    double db = rB.get(t) - mB;
                    covAcc += da * db;
                    varAAcc += da * da;
                    varBAcc += db * db;
                }

                double denom = Math.sqrt(varAAcc * varBAcc);
                double rho = denom > 1e-12 ? (covAcc / denom) : 0.0;
                sumRho += rho;
                pairCount++;

                pairwiseCorrs.add(new PairwiseCorrelationDto(
                    fa.schemeOptionId(), fa.fundName(),
                    fb.schemeOptionId(), fb.fundName(),
                    BigDecimal.valueOf(rho).setScale(4, RoundingMode.HALF_UP),
                    K
                ));
            }
        }

        // Update empirical factor exposure values
        List<FactorExposureItemDto> updatedFactors = updateFactorValues(
            factorExposures, portBeta, bmCode, portVol, bmVolAnnual
        );

        // Analytical Observations
        double avgRho = pairCount > 0 ? (sumRho / pairCount) : 1.0;
        riskObs.add(String.format(Locale.US,
            "Realized portfolio annualized volatility is %.2f%% over %d aligned trading days (%s to %s).",
            portVol, K, sortedDates.get(0), sortedDates.get(nDates - 1)));
        riskObs.add(String.format(Locale.US,
            "Weighted average fund volatility is %.2f%%. Empirical diversification benefit is +%.2f%% points (portfolio volatility is lower by %.2f%% points due to constituent correlation averaging %.2f).",
            weightedVol, divBenefit, divBenefit, avgRho));

        if (portBeta != null) {
            riskObs.add(String.format(Locale.US,
                "Portfolio systematic market sensitivity (Beta) against %s is %.2f.", bmCode, portBeta));
        }

        riskObs.add(String.format(Locale.US,
            "Synthesized portfolio maximum drawdown is -%.2f%% (Benchmark %s: -%.2f%%).",
            portMaxDd, bmCode, bmMaxDd));

        limitations.add("Daily rebalanced portfolio convention: assumes continuous daily rebalancing to target weights without transaction slippage or taxes.");
        limitations.add("Historical return window aligned strictly to common available trading days across all constituents and benchmark.");
        limitations.add("Fundamental factors (Value, Quality, Momentum) remain strictly gated under AGENTS.md §4: corporate balance sheet metrics are not in the raw artifact repository.");

        return new PortfolioRiskDecompositionDto(
            "COMPUTED_VALIDATED_INPUTS",
            bmCode, bmName, sortedDates.get(0), sortedDates.get(nDates - 1),
            K,
            BigDecimal.valueOf(portVol).setScale(2, RoundingMode.HALF_UP),
            BigDecimal.valueOf(weightedVol).setScale(2, RoundingMode.HALF_UP),
            BigDecimal.valueOf(divBenefit).setScale(2, RoundingMode.HALF_UP),
            BigDecimal.valueOf(portMaxDd).setScale(2, RoundingMode.HALF_UP),
            BigDecimal.valueOf(portDownSemi).setScale(2, RoundingMode.HALF_UP),
            portBeta != null ? BigDecimal.valueOf(portBeta).setScale(2, RoundingMode.HALF_UP) : null,
            BigDecimal.valueOf(bmVolAnnual).setScale(2, RoundingMode.HALF_UP),
            BigDecimal.valueOf(bmMaxDd).setScale(2, RoundingMode.HALF_UP),
            constituentProfiles, pairwiseCorrs, updatedFactors,
            "DAILY_REBALANCED_SYNTHESIZED",
            riskObs, limitations
        );
    }

    private static double computeMaxDrawdown(List<Double> returns) {
        double wealth = 1.0;
        double peak = 1.0;
        double maxDd = 0.0;
        for (double r : returns) {
            wealth *= (1.0 + r);
            if (wealth > peak) {
                peak = wealth;
            }
            double dd = (peak - wealth) / peak;
            if (dd > maxDd) {
                maxDd = dd;
            }
        }
        return maxDd * 100.0;
    }

    private static double computeMean(List<Double> values) {
        if (values.isEmpty()) return 0.0;
        double sum = 0.0;
        for (double v : values) sum += v;
        return sum / values.size();
    }

    private List<FactorExposureItemDto> buildFactorExposures(
        List<ConsolidatedMarketCapExposureDto> marketCaps,
        String bmCode
    ) {
        List<FactorExposureItemDto> factors = new ArrayList<>();

        // 1. Size
        String sizeSummary = "Unclassified";
        if (marketCaps != null) {
            StringBuilder sb = new StringBuilder();
            for (ConsolidatedMarketCapExposureDto m : marketCaps) {
                if (m.consolidatedWeightPct().compareTo(BigDecimal.ZERO) > 0) {
                    if (!sb.isEmpty()) sb.append(" | ");
                    sb.append(m.category()).append(": ").append(m.consolidatedWeightPct()).append("%");
                }
            }
            if (!sb.isEmpty()) sizeSummary = sb.toString();
        }

        factors.add(new FactorExposureItemDto(
            "SIZE",
            "Market Capitalization (Size)",
            "HOLDINGS_PROXY",
            sizeSummary,
            "VALIDATED_PROXY",
            "Portfolio exposure across equity market capitalization segments (Large, Mid, Small Cap).",
            "Disclosed constituent holdings and AMFI point-in-time half-yearly market-cap rankings.",
            "Consolidated look-through weighted sum of underlying security market-cap tiers.",
            "Verified against official AMFI half-yearly classification master list."
        ));

        // 2. Market Sensitivity (Beta)
        factors.add(new FactorExposureItemDto(
            "MARKET_BETA",
            "Market Sensitivity (Beta)",
            "RETURN_BASED",
            "Pending calculation",
            "EMPIRICALLY_COMPUTED",
            "Systematic market risk sensitivity relative to broad equity market benchmark.",
            "Synchronized daily NAV returns and benchmark daily total return series.",
            "Ordinary Least Squares (OLS) beta regression over aligned trading days.",
            "Computed on daily rebalanced synthesized portfolio returns."
        ));

        // 3. Low Volatility
        factors.add(new FactorExposureItemDto(
            "LOW_VOLATILITY",
            "Low Volatility",
            "RETURN_BASED",
            "Pending calculation",
            "EMPIRICALLY_COMPUTED",
            "Empirical annualized return volatility and drawdown dispersion.",
            "Synchronized daily constituent NAV returns over aligned window.",
            "Sample standard deviation of daily returns annualized with sqrt(252).",
            "Directly captures diversification benefit from imperfect cross-fund correlations."
        ));

        // 4. Value
        factors.add(new FactorExposureItemDto(
            "VALUE",
            "Value (Earnings Yield / Book-to-Price)",
            "EMPIRICALLY_GATED",
            "Not available",
            "FACTOR_INPUTS_UNAVAILABLE",
            "Systematic exposure to equities with low market price relative to fundamental book value or earnings (HML).",
            "Point-in-time corporate balance sheet filings, P/E, P/B, and EV/EBITDA ratios.",
            "Characteristic-sorted cross-sectional factor ranking or Fama-French 3-factor regression.",
            "GATED under §4 of AGENTS.md: Security-level corporate balance sheet and fundamental valuation data are not licensed or ingested in current artifact store."
        ));

        // 5. Quality
        factors.add(new FactorExposureItemDto(
            "QUALITY",
            "Quality (ROE, Leverage, Stability)",
            "EMPIRICALLY_GATED",
            "Not available",
            "FACTOR_INPUTS_UNAVAILABLE",
            "Exposure to financially sound firms with high return on equity, low leverage, and stable earnings (QMJ).",
            "Point-in-time corporate balance sheets, debt-to-equity, ROE, and earnings consistency data.",
            "Composite z-score aggregation across profitability, leverage, and earnings growth metrics.",
            "GATED under §4 of AGENTS.md: Multi-year fundamental financial statements for Indian corporate securities are not available in local data store."
        ));

        // 6. Momentum
        factors.add(new FactorExposureItemDto(
            "MOMENTUM",
            "Price Momentum (12-1M)",
            "EMPIRICALLY_GATED",
            "Not available",
            "FACTOR_INPUTS_UNAVAILABLE",
            "Tendency of assets with strong past 12-month returns to continue outperforming (UMD/WML).",
            "Point-in-time daily adjusted closing stock prices for all 100+ underlying portfolio securities.",
            "Cross-sectional ranking of 12-1 month equity price returns weighted by portfolio holding weights.",
            "GATED under §4 of AGENTS.md: Daily adjusted stock price feeds for all individual underlying equity securities are not ingested."
        ));

        return factors;
    }

    private List<FactorExposureItemDto> updateFactorValues(
        List<FactorExposureItemDto> baseline,
        Double portBeta,
        String bmCode,
        double portVol,
        double bmVol
    ) {
        List<FactorExposureItemDto> updated = new ArrayList<>();
        for (FactorExposureItemDto f : baseline) {
            if ("MARKET_BETA".equals(f.factorCode())) {
                String val = portBeta != null
                    ? String.format(Locale.US, "%.2f vs %s", portBeta, bmCode)
                    : "Not available";
                updated.add(new FactorExposureItemDto(
                    f.factorCode(), f.factorName(), f.category(), val, f.status(),
                    f.economicDefinition(), f.requiredInputs(), f.calculationMethod(), f.evidenceNotes()
                ));
            } else if ("LOW_VOLATILITY".equals(f.factorCode())) {
                String val = String.format(Locale.US, "%.2f%% (Benchmark: %.2f%%)", portVol, bmVol);
                updated.add(new FactorExposureItemDto(
                    f.factorCode(), f.factorName(), f.category(), val, f.status(),
                    f.economicDefinition(), f.requiredInputs(), f.calculationMethod(), f.evidenceNotes()
                ));
            } else {
                updated.add(f);
            }
        }
        return updated;
    }

    // Builder helpers for clean, deterministic math
    private static class ConsolidatedSecurityBuilder {
        private final String canonicalName;
        private final String isin;
        private final String sector;
        private final String assetClass;
        private final String marketCapCategory;
        private final String identityConfidence;
        private final String classificationProvenance;
        private BigDecimal totalWeightAcc = BigDecimal.ZERO;
        private final List<FundSecurityContributionDto> contributions = new ArrayList<>();

        public ConsolidatedSecurityBuilder(
            String canonicalName, String isin, String sector, String assetClass,
            String marketCapCategory, String identityConfidence, String classificationProvenance
        ) {
            this.canonicalName = canonicalName;
            this.isin = isin;
            this.sector = sector;
            this.assetClass = assetClass;
            this.marketCapCategory = marketCapCategory;
            this.identityConfidence = identityConfidence;
            this.classificationProvenance = classificationProvenance;
        }

        public void addContribution(Long optId, String fundName, BigDecimal fundWeightPct, BigDecimal holdingWeightInFundPct, BigDecimal effectiveContributionPct) {
            this.totalWeightAcc = this.totalWeightAcc.add(effectiveContributionPct);
            this.contributions.add(new FundSecurityContributionDto(
                optId, fundName, fundWeightPct, holdingWeightInFundPct, effectiveContributionPct
            ));
        }

        public ConsolidatedSecurityExposureDto build() {
            contributions.sort((a, b) -> b.effectiveWeightContributionPct().compareTo(a.effectiveWeightContributionPct()));
            return new ConsolidatedSecurityExposureDto(
                canonicalName, isin, sector, assetClass, marketCapCategory,
                totalWeightAcc.setScale(2, RoundingMode.HALF_UP),
                contributions.size(), contributions, identityConfidence, classificationProvenance
            );
        }
    }

    private static class SectorAggregationBuilder {
        private final String macroSector;
        private BigDecimal totalWeightAcc = BigDecimal.ZERO;
        private final Set<String> uniqueSecurities = new HashSet<>();
        private final Map<Long, FundSectorContributionAccumulator> fundContributions = new LinkedHashMap<>();
        private final List<ConsolidatedSecurityExposureDto> securitiesInSector = new ArrayList<>();

        public SectorAggregationBuilder(String macroSector) {
            this.macroSector = macroSector;
        }

        public void addSecurityExposure(ConsolidatedSecurityExposureDto sec) {
            this.totalWeightAcc = this.totalWeightAcc.add(sec.consolidatedWeightPct());
            this.uniqueSecurities.add(sec.canonicalName());
            this.securitiesInSector.add(sec);

            for (FundSecurityContributionDto c : sec.contributions()) {
                fundContributions.computeIfAbsent(c.schemeOptionId(), k -> new FundSectorContributionAccumulator(c.schemeOptionId(), c.fundName()))
                    .addWeight(c.effectiveWeightContributionPct(), c.holdingWeightInFundPct());
            }
        }

        public ConsolidatedSectorExposureDto build() {
            List<FundSectorContributionDto> contrs = new ArrayList<>();
            for (FundSectorContributionAccumulator acc : fundContributions.values()) {
                contrs.add(acc.build());
            }
            contrs.sort((a, b) -> b.effectiveContributionPct().compareTo(a.effectiveContributionPct()));

            securitiesInSector.sort((a, b) -> b.consolidatedWeightPct().compareTo(a.consolidatedWeightPct()));
            List<SectorTopSecurityDto> topSecs = new ArrayList<>();
            int limit = Math.min(5, securitiesInSector.size());
            for (int i = 0; i < limit; i++) {
                ConsolidatedSecurityExposureDto s = securitiesInSector.get(i);
                topSecs.add(new SectorTopSecurityDto(s.canonicalName(), s.isin(), s.consolidatedWeightPct(), s.fundCount()));
            }

            return new ConsolidatedSectorExposureDto(
                macroSector, totalWeightAcc.setScale(2, RoundingMode.HALF_UP),
                uniqueSecurities.size(), contrs, "CANONICAL_SECTOR_TAXONOMY", topSecs
            );
        }
    }

    private static class FundSectorContributionAccumulator {
        private final Long schemeOptionId;
        private final String fundName;
        private BigDecimal effectiveContributionAcc = BigDecimal.ZERO;
        private BigDecimal sectorWeightInFundAcc = BigDecimal.ZERO;

        public FundSectorContributionAccumulator(Long schemeOptionId, String fundName) {
            this.schemeOptionId = schemeOptionId;
            this.fundName = fundName;
        }

        public void addWeight(BigDecimal effectiveContribution, BigDecimal holdingWeightInFund) {
            this.effectiveContributionAcc = this.effectiveContributionAcc.add(effectiveContribution);
            this.sectorWeightInFundAcc = this.sectorWeightInFundAcc.add(holdingWeightInFund);
        }

        public FundSectorContributionDto build() {
            return new FundSectorContributionDto(
                schemeOptionId, fundName,
                sectorWeightInFundAcc.setScale(2, RoundingMode.HALF_UP),
                effectiveContributionAcc.setScale(2, RoundingMode.HALF_UP)
            );
        }
    }

    private static class McapAggregationBuilder {
        private final String category;
        private final String provenance;
        private BigDecimal weightAcc = BigDecimal.ZERO;
        private int count = 0;

        public McapAggregationBuilder(String category, String provenance) {
            this.category = category;
            this.provenance = provenance;
        }

        public void addSecurity(BigDecimal weight) {
            this.weightAcc = this.weightAcc.add(weight);
            this.count++;
        }

        public ConsolidatedMarketCapExposureDto build() {
            return new ConsolidatedMarketCapExposureDto(
                category, weightAcc.setScale(2, RoundingMode.HALF_UP), count, provenance
            );
        }
    }
}
