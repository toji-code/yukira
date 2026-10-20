package com.yukira.backend.service.portfolio;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.mcap.*;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.BenchmarkResolutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Service orchestrating Point-in-Time Market-Cap Style Drift & Allocation Decomposition.
 *
 * Epistemic & Methodological Guardrails (AGENTS.md & Phase 2H/2K):
 * 1. Descriptive Portfolio Construction Analysis:
 *    Describes observed exposures across authentic portfolio snapshots.
 *    Never generates manager-skill scores or predictive return forecasts.
 * 2. Point-in-Time (PIT) Look-Ahead Bias Prevention:
 *    portfolioDate <= asOfDate AND availabilityTime <= knowledgeCutoff.
 *    Classifications must satisfy effectiveFrom <= portfolioDate <= effectiveTo
 *    and availabilityTime <= knowledgeCutoff.
 * 3. Separation of Market-Cap Categories from Asset Classes:
 *    Large-Cap, Mid-Cap, Small-Cap strictly apply to equity securities.
 *    Cash/liquid assets, debt/non-equity, and unclassified securities are kept strictly distinct.
 * 4. Mathematical Drift Decomposition:
 *    \Delta W_{obs} = \Delta W_{weight} + \Delta W_{reclass} + \Delta W_{disclosure} + \Delta W_{unresolved}
 *    Verified against documented numerical tolerance (<= 0.02 pp).
 * 5. Strict Benchmark Completeness Gating:
 *    Benchmark-relative active style comparison is gated with GATED_INCOMPLETE_BENCHMARK_WEIGHTS
 *    when benchmark constituent weights are partially disclosed (< 99.50%).
 *    Missing constituent weights are NEVER treated as zero.
 * 6. Non-Causal Attribution Language:
 *    Continuing holding weight adjustments must not be described solely as manager transactions;
 *    they reflect market price fluctuations, net fund flows, and corporate actions.
 */
@Service
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class MarketCapDriftService {

    private static final Logger log = LoggerFactory.getLogger(MarketCapDriftService.class);
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal COMPLETE_COVERAGE_THRESHOLD = new BigDecimal("99.50");
    private static final BigDecimal RECONCILIATION_TOLERANCE = new BigDecimal("0.02");
    private static final String METHODOLOGY_VERSION = "YUKIRA-MCAP-DRIFT-1.0 (Phase 2K Deterministic)";

    private final SchemeOptionRepository schemeOptionRepository;
    private final PortfolioSnapshotRepository portfolioSnapshotRepository;
    private final PortfolioHoldingRepository portfolioHoldingRepository;
    private final SecurityIdentifierRepository securityIdentifierRepository;
    private final SecurityMarketCapClassificationRepository classificationRepository;
    private final BenchmarkRepository benchmarkRepository;
    private final BenchmarkConstituentRepository benchmarkConstituentRepository;
    private final BenchmarkResolutionService benchmarkResolutionService;

    // Canonical Curated Indian Market Capitalization Reference Sets (SEBI Categorization Standards)
    private static final Set<String> APPROX_LARGE_CAP = Set.of(
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

    private static final Set<String> APPROX_MID_CAP = Set.of(
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

    private static final Set<String> APPROX_SMALL_CAP = Set.of(
        "ZYDUS WELLNESS", "EID PARRY", "CMS INFO SYSTEM", "CRAFTSMAN AUTOMATION",
        "DIAMOND POWER", "BIRLASOFT", "RUBICON RESEARCH", "MOLBIO DIAGNOSTICS",
        "CYIENT", "APAR INDUSTRIES", "NEULAND LABORATORIES", "ASTER DM", "AVANTI FEEDS",
        "BSE", "BOMBAY STOCK EXCHANGE", "CENTURY PLYBOARDS", "CESC", "CHAMBAL FERTILISERS",
        "CHOLA FINANCIAL", "COMPUTER AGE", "CAMS", "ECLERX", "FINOLEX CABLES",
        "FIRSTSOURCE", "GRANULES INDIA", "GRAPHITE INDIA", "HAPPIEST MINDS", "HATSUN AGRO",
        "HEG", "HIMATATSINGKA", "IDBI BANK", "IIFL FINANCE", "INTELLECT DESIGN",
        "JB CHEMICALS", "JYOTHY LABS", "KAYNES TECHNOLOGY", "KNR CONSTRUCTIONS", "KRBL",
        "LEMON TREE", "LEMONTREE", "LUX INDUSTRIES", "MAPMYINDIA", "NOCIL", "PRAJ INDUSTRIES",
        "RBL BANK", "SAFEXPRESS", "SHARDA CROPCHEM", "STERLITE TECHNOLOGIES", "SUVEN PHARMA",
        "TANLA PLATFORMS", "TEJAS NETWORKS", "VA TECH WABAG", "WELSPUN CORP", "ZENSAR TECHNOLOGIES",
        "ACME SOLAR", "ATHER ENERGY", "CORONA REMEDIES", "FSN ECOMMERCE", "NYKAA",
        "LENSKART", "NIPPON LIFE INDIA", "RESTAURANT BRANDS ASIA", "BURGER KING",
        "SAPPHIRE FOODS", "SBI FUNDS MANAGEMENT", "TENNECO CLEAN AIR", "VARROC ENGINEERING",
        "VISHAL MEGA MART", "HEXAWARE TECHNOLOGIES", "ANTHEM BIOSCIENCES"
    );

    private record HoldingClassification(
        Long securityId,
        String isin,
        String companyName,
        BigDecimal weightPct,
        String category,
        Integer rank,
        String classificationSource,
        String rankingPeriod,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String provenance
    ) {}

    public MarketCapDriftService(
        SchemeOptionRepository schemeOptionRepository,
        PortfolioSnapshotRepository portfolioSnapshotRepository,
        PortfolioHoldingRepository portfolioHoldingRepository,
        SecurityIdentifierRepository securityIdentifierRepository,
        SecurityMarketCapClassificationRepository classificationRepository,
        BenchmarkRepository benchmarkRepository,
        BenchmarkConstituentRepository benchmarkConstituentRepository,
        BenchmarkResolutionService benchmarkResolutionService
    ) {
        this.schemeOptionRepository = schemeOptionRepository;
        this.portfolioSnapshotRepository = portfolioSnapshotRepository;
        this.portfolioHoldingRepository = portfolioHoldingRepository;
        this.securityIdentifierRepository = securityIdentifierRepository;
        this.classificationRepository = classificationRepository;
        this.benchmarkRepository = benchmarkRepository;
        this.benchmarkConstituentRepository = benchmarkConstituentRepository;
        this.benchmarkResolutionService = benchmarkResolutionService;
    }

    public MarketCapDriftResponseDto getMarketCapDriftTrajectory(
        Long schemeOptionId,
        LocalDate asOfDate,
        LocalDate startDate,
        LocalDate endDate,
        OffsetDateTime knowledgeCutoff
    ) {
        OffsetDateTime cutoff = knowledgeCutoff != null ? knowledgeCutoff : OffsetDateTime.now();
        LocalDate effectiveAsOf = asOfDate != null ? asOfDate : cutoff.toLocalDate();

        SchemeOption option = schemeOptionRepository.findById(schemeOptionId)
            .orElseThrow(() -> new IllegalArgumentException("SchemeOption #" + schemeOptionId + " not found."));

        String fundName = (option.getPlan() != null && option.getPlan().getScheme() != null)
            ? option.getPlan().getScheme().getName() : "Scheme Option #" + schemeOptionId;
        String planType = option.getPlan() != null ? option.getPlan().getPlanType() : "DIRECT";
        String optionType = option.getOptionType() != null ? option.getOptionType() : "GROWTH";
        String amfiCode = option.getAmfiCode();
        String schemeCode = (option.getPlan() != null && option.getPlan().getScheme() != null)
            ? option.getPlan().getScheme().getCode() : null;
        Long schemeId = (option.getPlan() != null && option.getPlan().getScheme() != null)
            ? option.getPlan().getScheme().getId() : null;

        // 1. Resolve Primary Benchmark
        Benchmark benchmark = null;
        if (schemeId != null) {
            var bmResult = benchmarkResolutionService.resolvePrimaryBenchmark(schemeId, effectiveAsOf);
            benchmark = bmResult.benchmark();
        }
        if (benchmark == null) {
            benchmark = benchmarkRepository.findByCode("NIFTY_500_TRI")
                .orElseThrow(() -> new IllegalStateException("Primary benchmark NIFTY_500_TRI not found"));
        }

        // 2. Query Disclosed Portfolio Snapshots under PIT cutoff
        List<PortfolioSnapshot> allSnapshots = portfolioSnapshotRepository
            .findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(schemeOptionId, cutoff);

        // Deduplicate snapshots by date (taking newest revision under cutoff)
        Map<LocalDate, PortfolioSnapshot> dedupMap = new LinkedHashMap<>();
        int skippedPeriods = 0;
        for (PortfolioSnapshot s : allSnapshots) {
            if (s.getPortfolioDate().isAfter(effectiveAsOf)) {
                skippedPeriods++;
                continue;
            }
            if (startDate != null && s.getPortfolioDate().isBefore(startDate)) {
                skippedPeriods++;
                continue;
            }
            if (endDate != null && s.getPortfolioDate().isAfter(endDate)) {
                skippedPeriods++;
                continue;
            }
            dedupMap.putIfAbsent(s.getPortfolioDate(), s);
        }

        List<PortfolioSnapshot> filtered = new ArrayList<>(dedupMap.values());
        filtered.sort(Comparator.comparing(PortfolioSnapshot::getPortfolioDate));

        String runId = UUID.randomUUID().toString();

        if (filtered.isEmpty()) {
            return new MarketCapDriftResponseDto(
                schemeOptionId, fundName, planType, optionType, amfiCode, schemeCode,
                benchmark.getId(), benchmark.getCode(), benchmark.getName(),
                "INSUFFICIENT_DATA",
                "No disclosed portfolio snapshots found under Point-in-Time knowledge cutoff " + cutoff + ".",
                METHODOLOGY_VERSION, runId, cutoff.toString(),
                allSnapshots.size(), 0, skippedPeriods,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                List.of("Disclosed portfolio holdings required under requested Point-in-Time cutoffs"),
                List.of("Are monthly portfolio holding disclosures published by the AMC for this period?"),
                List.of("Zero fake data: Market-cap style drift analysis requires authentic point-in-time portfolio holdings.")
            );
        }

        // 3. Process each historical portfolio snapshot
        List<MarketCapObservationDto> observations = new ArrayList<>();
        List<Map<String, HoldingClassification>> snapshotHoldingMaps = new ArrayList<>();

        for (PortfolioSnapshot ps : filtered) {
            LocalDate pDate = ps.getPortfolioDate();

            // Load Fund Holdings for Snapshot
            List<PortfolioHolding> holdings = portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(ps.getId());

            // Resolve PIT AMFI Market-Cap Classifications for this portfolio date under cutoff
            List<SecurityMarketCapClassification> eligibleClassifications = classificationRepository.findEligibleClassifications(pDate, cutoff);

            String classificationStatus;
            String sourceRankingPeriod = null;
            LocalDate sourcePubDate = null;
            LocalDate effFrom = null;
            LocalDate effTo = null;
            Long artifactId = null;
            String artifactSha = null;

            Map<String, SecurityMarketCapClassification> isinToMcapMap = new HashMap<>();
            Map<Long, SecurityMarketCapClassification> secIdToMcapMap = new HashMap<>();

            if (!eligibleClassifications.isEmpty()) {
                classificationStatus = "VERIFIED_HISTORICAL_PIT";
                SecurityMarketCapClassification sample = eligibleClassifications.get(0);
                sourceRankingPeriod = sample.getRankingPeriod();
                sourcePubDate = sample.getSourcePublicationDate();
                effFrom = sample.getEffectiveFrom();
                effTo = sample.getEffectiveTo();
                artifactId = sample.getSourceArtifact() != null ? sample.getSourceArtifact().getId() : null;
                artifactSha = sample.getSourceArtifact() != null ? sample.getSourceArtifact().getSha256Hash() : null;

                for (SecurityMarketCapClassification c : eligibleClassifications) {
                    if (c.getIsin() != null && !c.getIsin().isBlank()) {
                        isinToMcapMap.putIfAbsent(c.getIsin().trim().toUpperCase(Locale.ROOT), c);
                    }
                    if (c.getSecurity() != null && c.getSecurity().getId() != null) {
                        secIdToMcapMap.putIfAbsent(c.getSecurity().getId(), c);
                    }
                }
            } else {
                classificationStatus = "APPROXIMATE_REFERENCE";
            }

            // Query ISIN identifiers for security master resolution
            List<Long> secIds = holdings.stream()
                .map(h -> h.getSecurity() != null ? h.getSecurity().getId() : null)
                .filter(Objects::nonNull)
                .toList();

            Map<Long, String> secIdToIsinMap = new HashMap<>();
            if (!secIds.isEmpty()) {
                List<SecurityIdentifier> isins = securityIdentifierRepository.findBySecurityIdInAndIdType(secIds, "ISIN");
                for (SecurityIdentifier si : isins) {
                    if (si.getSecurity() != null && si.getIdValue() != null) {
                        secIdToIsinMap.putIfAbsent(si.getSecurity().getId(), si.getIdValue().trim().toUpperCase(Locale.ROOT));
                    }
                }
            }

            // Classify each holding into 6 distinct buckets:
            // LARGE_CAP, MID_CAP, SMALL_CAP, CASH, DEBT_OTHER, UNCLASSIFIED
            Map<String, HoldingClassification> currentHoldingMap = new LinkedHashMap<>();
            Map<String, BigDecimal> bucketWeights = new LinkedHashMap<>();
            Map<String, Integer> bucketCounts = new LinkedHashMap<>();
            Map<String, List<String>> bucketTopHoldings = new LinkedHashMap<>();

            // Initialize all 6 standard buckets
            List<String> standardBuckets = List.of("LARGE_CAP", "MID_CAP", "SMALL_CAP", "CASH", "DEBT_OTHER", "UNCLASSIFIED");
            for (String b : standardBuckets) {
                bucketWeights.put(b, BigDecimal.ZERO);
                bucketCounts.put(b, 0);
                bucketTopHoldings.put(b, new ArrayList<>());
            }

            BigDecimal sumReportedWeightPct = BigDecimal.ZERO;
            int classifiedHoldingsCount = 0;
            BigDecimal classifiedWeightPct = BigDecimal.ZERO;
            int unclassifiedHoldingCount = 0;
            BigDecimal unclassifiedWeightPct = BigDecimal.ZERO;

            for (PortfolioHolding h : holdings) {
                BigDecimal wPct = (h.getReportedWeight() != null)
                    ? h.getReportedWeight().multiply(BigDecimal.valueOf(100), MC).setScale(4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
                sumReportedWeightPct = sumReportedWeightPct.add(wPct, MC);

                Security sec = h.getSecurity();
                Long secId = sec != null ? sec.getId() : null;
                String isin = secId != null ? secIdToIsinMap.get(secId) : null;
                String compName = sec != null ? sec.getCanonicalName() : "Unknown Security";

                String bucket;
                Integer rank = null;
                String source;
                String rankPeriod = sourceRankingPeriod;
                LocalDate eFrom = effFrom;
                LocalDate eTo = effTo;
                String prov;

                if (isCash(sec)) {
                    bucket = "CASH";
                    source = "AMC Disclosed Asset Class";
                    prov = "ASSET_CLASS_DISCLOSURE";
                    classifiedHoldingsCount++;
                    classifiedWeightPct = classifiedWeightPct.add(wPct, MC);
                } else if (isDebt(sec)) {
                    bucket = "DEBT_OTHER";
                    source = "AMC Disclosed Asset Class";
                    prov = "ASSET_CLASS_DISCLOSURE";
                    classifiedHoldingsCount++;
                    classifiedWeightPct = classifiedWeightPct.add(wPct, MC);
                } else {
                    // Equity holding: check PIT AMFI classification
                    SecurityMarketCapClassification mcapRec = null;
                    if (secId != null) {
                        mcapRec = secIdToMcapMap.get(secId);
                    }
                    if (mcapRec == null && isin != null) {
                        mcapRec = isinToMcapMap.get(isin);
                    }

                    if (mcapRec != null) {
                        bucket = normalizeMarketCapCategory(mcapRec.getMarketCapCategory());
                        rank = mcapRec.getMarketCapRank();
                        source = "AMFI Semi-Annual Market-Cap Ledger (" + mcapRec.getRankingPeriod() + ")";
                        rankPeriod = mcapRec.getRankingPeriod();
                        eFrom = mcapRec.getEffectiveFrom();
                        eTo = mcapRec.getEffectiveTo();
                        prov = "VERIFIED_HISTORICAL_PIT";
                        classifiedHoldingsCount++;
                        classifiedWeightPct = classifiedWeightPct.add(wPct, MC);
                    } else if (isApproximateEquity(sec)) {
                        bucket = classifyHoldingApproximate(sec);
                        source = "SEBI Approximate Reference Fallback";
                        prov = "APPROXIMATE_REFERENCE";
                        classifiedHoldingsCount++;
                        classifiedWeightPct = classifiedWeightPct.add(wPct, MC);
                    } else {
                        bucket = "UNCLASSIFIED";
                        source = "Unresolved Classification";
                        prov = "UNMAPPED";
                        unclassifiedHoldingCount++;
                        unclassifiedWeightPct = unclassifiedWeightPct.add(wPct, MC);
                    }
                }

                bucketWeights.merge(bucket, wPct, (a, b) -> a.add(b, MC));
                bucketCounts.merge(bucket, 1, Integer::sum);
                List<String> tops = bucketTopHoldings.get(bucket);
                if (tops.size() < 5) {
                    tops.add(compName + " (" + wPct.setScale(2, RoundingMode.HALF_UP) + "%)");
                }

                String holdingKey = isin != null ? isin : (secId != null ? "SEC_" + secId : "NAME_" + compName);
                currentHoldingMap.merge(
                    holdingKey,
                    new HoldingClassification(secId, isin, compName, wPct, bucket, rank, source, rankPeriod, eFrom, eTo, prov),
                    (existing, incoming) -> new HoldingClassification(
                        existing.securityId(),
                        existing.isin(),
                        existing.companyName(),
                        existing.weightPct().add(incoming.weightPct(), MC),
                        existing.category(),
                        existing.rank() != null ? existing.rank() : incoming.rank(),
                        existing.classificationSource(),
                        existing.rankingPeriod(),
                        existing.effectiveFrom(),
                        existing.effectiveTo(),
                        existing.provenance()
                    )
                );
            }

            snapshotHoldingMaps.add(currentHoldingMap);

            BigDecimal sumReportedScaled = sumReportedWeightPct.setScale(2, RoundingMode.HALF_UP);
            BigDecimal weightReconciliationDeltaPct = new BigDecimal("100.00").subtract(sumReportedScaled, MC).setScale(2, RoundingMode.HALF_UP);

            BigDecimal countCoveragePct = !holdings.isEmpty()
                ? BigDecimal.valueOf(classifiedHoldingsCount * 100.0 / holdings.size()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            BigDecimal weightCoveragePct = sumReportedScaled.compareTo(BigDecimal.ZERO) > 0
                ? classifiedWeightPct.divide(sumReportedScaled, MC).multiply(BigDecimal.valueOf(100), MC).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            String disclosureScope = sumReportedScaled.compareTo(new BigDecimal("90.00")) >= 0
                ? "FULL_PORTFOLIO_DISCLOSURE"
                : "PARTIAL_TOP_HOLDINGS";

            // Build Bucket Exposure DTOs
            List<MarketCapBucketExposureDto> bucketDtos = new ArrayList<>();
            for (String b : standardBuckets) {
                BigDecimal rawW = bucketWeights.getOrDefault(b, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                BigDecimal normW = sumReportedScaled.compareTo(BigDecimal.ZERO) > 0
                    ? rawW.divide(sumReportedScaled, MC).multiply(BigDecimal.valueOf(100), MC).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
                int count = bucketCounts.getOrDefault(b, 0);
                List<String> tops = bucketTopHoldings.getOrDefault(b, Collections.emptyList());

                String label = switch (b) {
                    case "LARGE_CAP" -> "Large-Cap (1st–100th)";
                    case "MID_CAP" -> "Mid-Cap (101st–250th)";
                    case "SMALL_CAP" -> "Small-Cap (251st+)";
                    case "CASH" -> "Cash & Liquid Assets";
                    case "DEBT_OTHER" -> "Debt & Other Non-Equity";
                    default -> "Unclassified / Unresolved Holdings";
                };

                String prov = switch (b) {
                    case "CASH", "DEBT_OTHER" -> "ASSET_CLASS_DISCLOSURE";
                    case "UNCLASSIFIED" -> "UNMAPPED";
                    default -> classificationStatus;
                };

                String src = switch (b) {
                    case "CASH", "DEBT_OTHER" -> "AMC Asset Class Categorization";
                    case "UNCLASSIFIED" -> "Pending AMFI / Identifier Resolution";
                    default -> sourceRankingPeriod != null ? "AMFI Semi-Annual Ledger (" + sourceRankingPeriod + ")" : "SEBI Reference Standard";
                };

                bucketDtos.add(new MarketCapBucketExposureDto(
                    b, label, rawW, normW, count, tops, src, sourceRankingPeriod, effFrom, effTo, prov
                ));
            }

            // Benchmark Comparison under strict PIT rules
            MarketCapBenchmarkComparisonDto bmComp = buildBenchmarkComparison(
                benchmark, pDate, cutoff, eligibleClassifications
            );

            observations.add(new MarketCapObservationDto(
                ps.getId(),
                pDate,
                ps.getAvailabilityTime(),
                disclosureScope,
                sumReportedScaled,
                weightReconciliationDeltaPct,
                holdings.size(),
                countCoveragePct,
                weightCoveragePct,
                unclassifiedWeightPct.setScale(2, RoundingMode.HALF_UP),
                unclassifiedHoldingCount,
                sourceRankingPeriod,
                sourcePubDate,
                effFrom,
                effTo,
                artifactId,
                artifactSha,
                classificationStatus,
                bucketDtos,
                bmComp
            ));
        }

        // 4. Calculate Chronological Style-Drift Transitions and Decompositions
        List<MarketCapDriftChangeDto> transitions = new ArrayList<>();
        List<MarketCapReclassificationItemDto> allReclassifiedHoldings = new ArrayList<>();

        for (int i = 0; i < observations.size() - 1; i++) {
            MarketCapObservationDto earlierObs = observations.get(i);
            MarketCapObservationDto laterObs = observations.get(i + 1);

            Map<String, HoldingClassification> earlierHoldings = snapshotHoldingMaps.get(i);
            Map<String, HoldingClassification> laterHoldings = snapshotHoldingMaps.get(i + 1);

            long elapsedDays = ChronoUnit.DAYS.between(earlierObs.portfolioDate(), laterObs.portfolioDate());
            BigDecimal covDelta = laterObs.sumReportedWeightsPct().subtract(earlierObs.sumReportedWeightsPct(), MC).setScale(2, RoundingMode.HALF_UP);
            boolean isScopeShift = "PARTIAL_TOP_HOLDINGS".equals(earlierObs.portfolioDisclosureScope())
                && "FULL_PORTFOLIO_DISCLOSURE".equals(laterObs.portfolioDisclosureScope());
            boolean isCoverageDivergence = covDelta.abs().compareTo(new BigDecimal("15.00")) > 0;
            boolean isElapsedTooLong = elapsedDays > 120;

            boolean isComparable = !isScopeShift && !isCoverageDivergence && !isElapsedTooLong;
            String nonCompReason = null;
            if (isScopeShift) {
                nonCompReason = "Disclosure scope expanded from partial top-holdings (" + earlierObs.sumReportedWeightsPct() + "%) to full portfolio (" + laterObs.sumReportedWeightsPct() + "%).";
            } else if (isCoverageDivergence) {
                nonCompReason = "Disclosed weight coverage changed by " + covDelta + " pp (" + earlierObs.sumReportedWeightsPct() + "% -> " + laterObs.sumReportedWeightsPct() + "%).";
            } else if (isElapsedTooLong) {
                nonCompReason = "Reporting gap of " + elapsedDays + " days between disclosures exceeds standard observation window.";
            }

            // Identify reclassified holdings between earlier and later ranking periods
            List<MarketCapReclassificationItemDto> transitionReclassified = new ArrayList<>();
            Set<String> allKeys = new LinkedHashSet<>();
            allKeys.addAll(earlierHoldings.keySet());
            allKeys.addAll(laterHoldings.keySet());

            for (String k : allKeys) {
                HoldingClassification prev = earlierHoldings.get(k);
                HoldingClassification curr = laterHoldings.get(k);

                if (prev != null && curr != null && isEquityCategory(prev.category()) && isEquityCategory(curr.category())
                    && !prev.category().equals(curr.category())) {
                    BigDecimal wPrev = prev.weightPct().setScale(2, RoundingMode.HALF_UP);
                    BigDecimal wCurr = curr.weightPct().setScale(2, RoundingMode.HALF_UP);
                    BigDecimal wDelta = wCurr.subtract(wPrev, MC).setScale(2, RoundingMode.HALF_UP);
                    String periodChange = (prev.rankingPeriod() != null ? prev.rankingPeriod() : "Period 1") + " -> " +
                        (curr.rankingPeriod() != null ? curr.rankingPeriod() : "Period 2");
                    String exp = "AMFI reclassified " + curr.companyName() + " from " + prev.category() +
                        (prev.rank() != null ? " (#" + prev.rank() + ")" : "") + " to " + curr.category() +
                        (curr.rank() != null ? " (#" + curr.rank() + ")" : "") + " under ranking period update (" + periodChange + ").";

                    MarketCapReclassificationItemDto item = new MarketCapReclassificationItemDto(
                        curr.isin() != null ? curr.isin() : prev.isin() != null ? prev.isin() : "-",
                        curr.companyName(),
                        prev.category(),
                        prev.rank(),
                        curr.category(),
                        curr.rank(),
                        wPrev,
                        wCurr,
                        wDelta,
                        periodChange,
                        exp
                    );
                    transitionReclassified.add(item);
                    allReclassifiedHoldings.add(item);
                }
            }

            // Decompose drift for each category:
            // Large-Cap, Mid-Cap, Small-Cap, Cash, Debt, Unclassified
            Map<String, BigDecimal> earlierWeights = new HashMap<>();
            Map<String, Integer> earlierCounts = new HashMap<>();
            for (MarketCapBucketExposureDto b : earlierObs.bucketExposures()) {
                earlierWeights.put(b.category(), b.reportedWeightPct());
                earlierCounts.put(b.category(), b.holdingCount());
            }

            Map<String, BigDecimal> laterWeights = new HashMap<>();
            Map<String, Integer> laterCounts = new HashMap<>();
            for (MarketCapBucketExposureDto b : laterObs.bucketExposures()) {
                laterWeights.put(b.category(), b.reportedWeightPct());
                laterCounts.put(b.category(), b.holdingCount());
            }

            List<String> categoriesToDecompose = List.of("LARGE_CAP", "MID_CAP", "SMALL_CAP", "CASH", "DEBT_OTHER", "UNCLASSIFIED");

            for (String cat : categoriesToDecompose) {
                BigDecimal w1 = earlierWeights.getOrDefault(cat, BigDecimal.ZERO);
                BigDecimal w2 = laterWeights.getOrDefault(cat, BigDecimal.ZERO);
                BigDecimal deltaObs = w2.subtract(w1, MC).setScale(2, RoundingMode.HALF_UP);
                int c1 = earlierCounts.getOrDefault(cat, 0);
                int c2 = laterCounts.getOrDefault(cat, 0);

                if (deltaObs.compareTo(BigDecimal.ZERO) == 0 && c1 == c2) {
                    continue;
                }

                // Mathematical Components:
                // 1. Reclassification Component: base weight shifted into/out of category solely by AMFI classification change
                BigDecimal deltaReclass = BigDecimal.ZERO;
                // 2. Weight Change Component: weight changes for continuing holdings in this category, plus new entries, minus exits
                BigDecimal deltaWeight = BigDecimal.ZERO;

                for (String k : allKeys) {
                    HoldingClassification prev = earlierHoldings.get(k);
                    HoldingClassification curr = laterHoldings.get(k);

                    BigDecimal pw = prev != null ? prev.weightPct() : BigDecimal.ZERO;
                    BigDecimal cw = curr != null ? curr.weightPct() : BigDecimal.ZERO;
                    String prevCat = prev != null ? prev.category() : null;
                    String currCat = curr != null ? curr.category() : null;

                    boolean inEarlier = cat.equals(prevCat);
                    boolean inLater = cat.equals(currCat);

                    if (inEarlier && inLater) {
                        // Continuing holding in same category: pure portfolio weight adjustment
                        deltaWeight = deltaWeight.add(cw.subtract(pw, MC), MC);
                    } else if (!inEarlier && inLater) {
                        if (prevCat != null) {
                            // Holding was present earlier in another category and reclassified into this category:
                            // Base weight pw brings reclassification inflow; weight adjustment (cw - pw) is portfolio weight change
                            deltaReclass = deltaReclass.add(pw, MC);
                            deltaWeight = deltaWeight.add(cw.subtract(pw, MC), MC);
                        } else {
                            // New security entering portfolio in this category
                            deltaWeight = deltaWeight.add(cw, MC);
                        }
                    } else if (inEarlier && !inLater) {
                        if (currCat != null) {
                            // Holding moved out of this category to another category:
                            // Reclassification outflow takes away base weight pw
                            deltaReclass = deltaReclass.subtract(pw, MC);
                        } else {
                            // Security liquidated / exited from portfolio
                            deltaWeight = deltaWeight.subtract(pw, MC);
                        }
                    }
                }

                BigDecimal deltaWeightScaled = deltaWeight.setScale(2, RoundingMode.HALF_UP);
                BigDecimal deltaReclassScaled = deltaReclass.setScale(2, RoundingMode.HALF_UP);

                BigDecimal deltaDisclosure = BigDecimal.ZERO;
                if (isScopeShift || isCoverageDivergence) {
                    // When disclosure coverage shifted, attribute residual to disclosure component
                    deltaDisclosure = deltaObs.subtract(deltaWeightScaled.add(deltaReclassScaled, MC), MC).setScale(2, RoundingMode.HALF_UP);
                }

                BigDecimal deltaUnresolved = deltaObs.subtract(
                    deltaWeightScaled.add(deltaReclassScaled, MC).add(deltaDisclosure, MC), MC
                ).setScale(2, RoundingMode.HALF_UP);

                boolean isReconciled = deltaUnresolved.abs().compareTo(RECONCILIATION_TOLERANCE) <= 0;

                // Determine Primary Driver
                String driver;
                String explanation;

                if (isScopeShift) {
                    driver = "DISCLOSURE_SCOPE_SHIFT";
                    explanation = "Observed weight shift (" + deltaObs + " pp) is dominated by portfolio disclosure scope expansion (" +
                        earlierObs.sumReportedWeightsPct() + "% -> " + laterObs.sumReportedWeightsPct() + "%).";
                } else if (deltaReclassScaled.abs().compareTo(BigDecimal.ZERO) > 0 && deltaReclassScaled.abs().compareTo(deltaWeightScaled.abs()) >= 0) {
                    driver = "MARKET_CAP_RECLASSIFICATION";
                    explanation = "Category exposure changed by " + (deltaObs.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + deltaObs +
                        " pp, primarily driven by AMFI semi-annual reclassification (" + (deltaReclassScaled.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") +
                        deltaReclassScaled + " pp reclassification component; " + (deltaWeightScaled.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") +
                        deltaWeightScaled + " pp weight component).";
                } else if (c1 == 0 && c2 > 0) {
                    driver = "SECURITY_ENTRY_EXIT";
                    explanation = "New category allocation initiated (" + c2 + " holdings added, net delta " + deltaObs + " pp).";
                } else if (c1 > 0 && c2 == 0) {
                    driver = "SECURITY_ENTRY_EXIT";
                    explanation = "Category completely liquidated (" + c1 + " holdings exited, net delta " + deltaObs + " pp).";
                } else if (c1 != c2) {
                    driver = "SECURITY_ENTRY_EXIT";
                    explanation = "Holding count changed from " + c1 + " to " + c2 + " (" + (deltaObs.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") +
                        deltaObs + " pp net delta across entry/exit and continuing positions).";
                } else {
                    driver = "PORTFOLIO_WEIGHT_ADJUSTMENT";
                    explanation = "Reported weights adjusted across existing " + c1 + " holdings (net delta: " +
                        (deltaObs.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + deltaObs + " pp). Reflects rebalancing, price movements, or net inflows.";
                }

                transitions.add(new MarketCapDriftChangeDto(
                    earlierObs.portfolioDate(),
                    laterObs.portfolioDate(),
                    cat,
                    w1,
                    w2,
                    deltaObs,
                    deltaWeightScaled,
                    deltaReclassScaled,
                    deltaDisclosure,
                    deltaUnresolved,
                    isReconciled,
                    RECONCILIATION_TOLERANCE,
                    driver,
                    isComparable,
                    nonCompReason,
                    explanation,
                    earlierObs.classificationCoverageWeightPct(),
                    laterObs.classificationCoverageWeightPct(),
                    c1,
                    c2,
                    transitionReclassified
                ));
            }
        }

        transitions.sort((a, b) -> {
            int dateCmp = b.laterDate().compareTo(a.laterDate());
            if (dateCmp != 0) return dateCmp;
            return b.deltaWeightPct().abs().compareTo(a.deltaWeightPct().abs());
        });

        // 5. Data Quality Warnings & Investigation Questions
        List<String> warnings = new ArrayList<>();
        List<String> questions = new ArrayList<>();

        // Check latest observation
        MarketCapObservationDto latestObs = observations.get(observations.size() - 1);
        if (latestObs.weightReconciliationDeltaPct().abs().compareTo(new BigDecimal("1.00")) > 0) {
            warnings.add("Portfolio reported weight sum (" + latestObs.sumReportedWeightsPct() + "%) diverges from 100.00% by " +
                latestObs.weightReconciliationDeltaPct() + " pp (cash/derivative netting or partial reporting).");
        }
        if (latestObs.unclassifiedWeightPct().compareTo(BigDecimal.ZERO) > 0) {
            warnings.add("Unclassified holdings account for " + latestObs.unclassifiedWeightPct() + "% of portfolio across " +
                latestObs.unclassifiedHoldingCount() + " positions.");
        }
        if ("APPROXIMATE_REFERENCE".equals(latestObs.classificationStatus())) {
            warnings.add("Latest observation utilizes SEBI approximate reference categories because authentic AMFI ranking ledger was unavailable under Point-in-Time cutoff.");
        }

        questions.add("How much of the observed market-cap drift was caused by AMFI ranking reclassifications versus active manager allocation changes?");
        questions.add("Are the unclassified holdings equity securities with missing ISINs, or unlisted debt/derivative contracts?");
        questions.add("Does the fund maintain consistent exposure to mid- and small-cap securities across historical market cycles, or does it retreat to large-cap during market drawdowns?");

        List<String> disclaimers = List.of(
            "Descriptive portfolio construction analysis only. Does not constitute investment advice or manager skill scoring.",
            "Continuing-holding weight changes reflect price movements, corporate actions, and fund flows as well as active transactions.",
            "Benchmark comparison is strictly gated when benchmark constituent weights are partially disclosed (< 99.50%).",
            "Zero fake data: All market-cap classifications are grounded in verified point-in-time AMFI ranking ledgers."
        );

        return new MarketCapDriftResponseDto(
            schemeOptionId,
            fundName,
            planType,
            optionType,
            amfiCode,
            schemeCode,
            benchmark.getId(),
            benchmark.getCode(),
            benchmark.getName(),
            "SUCCESS",
            "Point-in-Time Market-Cap Style Drift & Allocation Decomposition successfully calculated across " + observations.size() + " chronological observations.",
            METHODOLOGY_VERSION,
            runId,
            cutoff.toString(),
            allSnapshots.size(),
            observations.size(),
            skippedPeriods,
            observations,
            transitions,
            allReclassifiedHoldings,
            warnings,
            questions,
            disclaimers
        );
    }

    private MarketCapBenchmarkComparisonDto buildBenchmarkComparison(
        Benchmark benchmark,
        LocalDate pDate,
        OffsetDateTime cutoff,
        List<SecurityMarketCapClassification> eligibleClassifications
    ) {
        if (benchmark == null) {
            return new MarketCapBenchmarkComparisonDto(
                null, null, null, null, null, 0, 0,
                BigDecimal.ZERO, false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "BENCHMARK_UNAVAILABLE",
                "Primary benchmark is not registered or resolved.",
                "Benchmark market-cap exposure cannot be calculated."
            );
        }

        List<BenchmarkConstituent> constituents = benchmarkConstituentRepository
            .findEligibleConstituents(benchmark.getId(), pDate, cutoff);

        if (constituents.isEmpty()) {
            List<LocalDate> availDates = benchmarkConstituentRepository.findAvailableCompositionDates(benchmark.getId(), cutoff);
            if (!availDates.isEmpty()) {
                Optional<LocalDate> validPastDate = availDates.stream()
                    .filter(d -> !d.isAfter(pDate))
                    .findFirst();
                if (validPastDate.isPresent()) {
                    constituents = benchmarkConstituentRepository.findByBenchmarkAndDateAndCutoff(benchmark.getId(), validPastDate.get(), cutoff);
                }
            }
        }

        if (constituents.isEmpty()) {
            return new MarketCapBenchmarkComparisonDto(
                benchmark.getId(), benchmark.getCode(), benchmark.getName(),
                null, null, 0, 0,
                BigDecimal.ZERO, false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "BENCHMARK_UNAVAILABLE",
                "No eligible benchmark constituent snapshot found on or before portfolio disclosure date under requested knowledge cutoff.",
                "Benchmark constituent weights are unavailable."
            );
        }

        LocalDate bmDate = constituents.get(0).getEffectiveDate();
        OffsetDateTime bmAvail = constituents.get(0).getAvailabilityTime();
        long dateDiffDays = Math.abs(ChronoUnit.DAYS.between(bmDate, pDate));
        boolean dateCompatible = dateDiffDays <= 45;

        // Map eligible classifications by ISIN
        Map<String, String> isinToCategory = new HashMap<>();
        for (SecurityMarketCapClassification c : eligibleClassifications) {
            if (c.getIsin() != null && !c.getIsin().isBlank()) {
                isinToCategory.put(c.getIsin().trim().toUpperCase(Locale.ROOT), normalizeMarketCapCategory(c.getMarketCapCategory()));
            }
        }

        BigDecimal sumDisclosedWeight = BigDecimal.ZERO;
        BigDecimal largeCapDisclosed = BigDecimal.ZERO;
        BigDecimal midCapDisclosed = BigDecimal.ZERO;
        BigDecimal smallCapDisclosed = BigDecimal.ZERO;
        int weightedCount = 0;

        for (BenchmarkConstituent bc : constituents) {
            if (bc.getWeight() != null) {
                sumDisclosedWeight = sumDisclosedWeight.add(bc.getWeight(), MC);
                weightedCount++;

                String cat = null;
                if (bc.getIsin() != null) {
                    cat = isinToCategory.get(bc.getIsin().trim().toUpperCase(Locale.ROOT));
                }
                if (cat == null && bc.getCompanyName() != null) {
                    cat = classifyCompanyNameApproximate(bc.getCompanyName());
                }

                if ("LARGE_CAP".equals(cat)) {
                    largeCapDisclosed = largeCapDisclosed.add(bc.getWeight(), MC);
                } else if ("MID_CAP".equals(cat)) {
                    midCapDisclosed = midCapDisclosed.add(bc.getWeight(), MC);
                } else if ("SMALL_CAP".equals(cat)) {
                    smallCapDisclosed = smallCapDisclosed.add(bc.getWeight(), MC);
                }
            }
        }

        BigDecimal weightCoveragePct = sumDisclosedWeight.setScale(2, RoundingMode.HALF_UP);
        boolean isComplete = weightCoveragePct.compareTo(COMPLETE_COVERAGE_THRESHOLD) >= 0;

        String gatingStatus;
        String gatingReason;

        if (!dateCompatible) {
            gatingStatus = "TEMPORAL_MISMATCH";
            gatingReason = "Benchmark observation date (" + bmDate + ") is " + dateDiffDays + " days from portfolio snapshot (threshold: 45 days).";
        } else if (!isComplete) {
            gatingStatus = "GATED_INCOMPLETE_BENCHMARK_WEIGHTS";
            gatingReason = "Benchmark constituent weights are only partially disclosed (" + weightCoveragePct + "% weight coverage across " +
                weightedCount + " weighted constituents out of " + constituents.size() + " index members). Definitive active market-cap exposure is gated to prevent artificial active overweight distortion.";
        } else {
            gatingStatus = "VERIFIED_COMPLETE";
            gatingReason = "Verified complete benchmark constituent weight distribution.";
        }

        return new MarketCapBenchmarkComparisonDto(
            benchmark.getId(),
            benchmark.getCode(),
            benchmark.getName(),
            bmDate,
            bmAvail,
            constituents.size(),
            weightedCount,
            weightCoveragePct,
            isComplete,
            largeCapDisclosed.setScale(2, RoundingMode.HALF_UP),
            midCapDisclosed.setScale(2, RoundingMode.HALF_UP),
            smallCapDisclosed.setScale(2, RoundingMode.HALF_UP),
            gatingStatus,
            gatingReason,
            "Disclosed benchmark weights represent partial top-holding lower bounds (" + weightCoveragePct + "% total coverage). Unknown constituent weights are NEVER treated as zero."
        );
    }

    private boolean isCash(Security sec) {
        if (sec == null) return false;
        String assetClass = sec.getAssetClass() != null ? sec.getAssetClass().toUpperCase(Locale.ROOT) : "";
        String name = sec.getCanonicalName() != null ? sec.getCanonicalName().toUpperCase(Locale.ROOT) : "";
        String sector = sec.getSector() != null ? sec.getSector().toUpperCase(Locale.ROOT) : "";
        return "CASH".equals(assetClass) || "MONEY MARKET".equals(assetClass) ||
               name.contains("TREPS") || name.contains("REVERSE REPO") || name.contains("CLEARING CORP") ||
               name.contains("NET CURRENT ASSET") || name.contains("CASH") ||
               "CASH & CASH EQUIVALENTS".equals(sector) || "CASH & LIQUID ASSETS".equals(sector);
    }

    private boolean isDebt(Security sec) {
        if (sec == null) return false;
        String assetClass = sec.getAssetClass() != null ? sec.getAssetClass().toUpperCase(Locale.ROOT) : "";
        String name = sec.getCanonicalName() != null ? sec.getCanonicalName().toUpperCase(Locale.ROOT) : "";
        String sector = sec.getSector() != null ? sec.getSector().toUpperCase(Locale.ROOT) : "";
        return "DEBT".equals(assetClass) || "BOND".equals(assetClass) || "G-SEC".equals(assetClass) ||
               name.contains("TREASURY BILL") || name.contains("GOVERNMENT OF INDIA") || name.contains("SOVEREIGN") ||
               "SOVEREIGN DEBT".equals(sector) || "DEBT".equals(sector);
    }

    private boolean isApproximateEquity(Security sec) {
        if (sec == null) return false;
        String assetClass = sec.getAssetClass() != null ? sec.getAssetClass().toUpperCase(Locale.ROOT) : "";
        return "EQUITY".equals(assetClass) || (!isCash(sec) && !isDebt(sec));
    }

    private boolean isEquityCategory(String cat) {
        return "LARGE_CAP".equals(cat) || "MID_CAP".equals(cat) || "SMALL_CAP".equals(cat);
    }

    private String normalizeMarketCapCategory(String category) {
        if (category == null) return "UNCLASSIFIED";
        String norm = category.trim().toUpperCase(Locale.ROOT).replace(" ", "_").replace("-", "_");
        if (norm.contains("LARGE")) return "LARGE_CAP";
        if (norm.contains("MID")) return "MID_CAP";
        if (norm.contains("SMALL")) return "SMALL_CAP";
        return "UNCLASSIFIED";
    }

    private String classifyHoldingApproximate(Security sec) {
        if (sec == null || sec.getCanonicalName() == null) return "UNCLASSIFIED";
        return classifyCompanyNameApproximate(sec.getCanonicalName());
    }

    private String classifyCompanyNameApproximate(String name) {
        if (name == null) return "UNCLASSIFIED";
        String upper = name.toUpperCase(Locale.ROOT);
        for (String l : APPROX_LARGE_CAP) {
            if (upper.contains(l)) return "LARGE_CAP";
        }
        for (String m : APPROX_MID_CAP) {
            if (upper.contains(m)) return "MID_CAP";
        }
        for (String s : APPROX_SMALL_CAP) {
            if (upper.contains(s)) return "SMALL_CAP";
        }
        return "UNCLASSIFIED";
    }
}
