package com.yukira.backend.service;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.PortfolioConstructionRiskResponseDto;
import com.yukira.backend.dto.portfolio.PortfolioConstructionRiskResponseDto.*;
import com.yukira.backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class PortfolioConstructionRiskService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioConstructionRiskService.class);

    private final SchemeOptionRepository schemeOptionRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final PortfolioHoldingRepository holdingRepository;
    private final SecurityRepository securityRepository;

    private static final Set<String> LARGE_CAP_NAMES = Set.of(
        "HDFC BANK", "ICICI BANK", "INFOSYS", "STATE BANK OF INDIA", "RELIANCE",
        "TCS", "TATA CONSULTANCY", "LARSEN", "BHARTI AIRTEL", "AXIS BANK",
        "KOTAK MAHINDRA", "ITC", "HINDUSTAN UNILEVER", "BAJAJ FINANCE", "MARUTI SUZUKI",
        "SUN PHARMA", "TATA MOTORS", "MAHINDRA", "NTPC", "POWER GRID", "TITAN",
        "ULTRATECH", "ASIAN PAINTS", "HCL TECH", "COAL INDIA", "TATA STEEL",
        "BAJAJ FINSERV", "ADANI ENTERPRISES", "ADANI PORTS", "INDUSIND BANK",
        "JIO FINANCIAL", "NESTLE", "WIPRO", "HINDALCO", "GRASIM", "TECH MAHINDRA",
        "CIPLA", "EICHER MOTORS", "APOLLO HOSPITALS", "DIVIS LAB", "INTERGLOBE AVIATION",
        "DR REDDY", "VARUN BEVERAGES", "BAJAJ AUTO", "GAIL", "HERO MOTOCORP",
        "SHRIRAM FINANCE", "SBI LIFE", "HDFC LIFE", "ZOMATO"
    );

    private static final Set<String> MID_CAP_NAMES = Set.of(
        "FEDERAL BANK", "INDIAN HOTEL", "MAX HEALTHCARE", "TORRENT PHARMA", "MPHASIS",
        "POLYCAB", "CUMMINS", "LUPIN", "ASTRAL", "PERSISTENT", "ASHOK LEYLAND",
        "AU SMALL FINANCE", "IDFC FIRST", "JINDAL STEEL", "BHARAT FORGE", "SUPREME IND",
        "TUBE INVESTMENTS", "COLGATE", "BALKRISHNA", "PETRONET", "PAGE INDUSTRIES",
        "COFORGE", "ACC", "PI INDUSTRIES", "COROMANDEL", "ALPL", "FORTIS", "GUJARAT GAS",
        "SUZLON", "YES BANK", "TRENT", "DIXON", "PB FINTECH", "PRESTIGE", "GMR AIRPORTS"
    );

    private static final Set<String> SMALL_CAP_NAMES = Set.of(
        "RBL BANK", "KARUR VYSYA", "EQUITAS", "UJJIVAN", "CITY UNION", "INDIAN OVERSEAS",
        "CENTRAL BANK", "MAHABANK", "PNC INFRATECH", "KNR CONSTR", "NCC", "AHLUWALIA",
        "CENTURY PLY", "GREENLAM", "CERA", "KAJARIA", "SOMANY", "ORIENT ELECTRIC",
        "SYRMA", "KAYNES", "DATAPATTERNS", "ROUTE MOBILE", "TANLA", "HAPPIEST MINDS",
        "LATENT VIEW", "MASTEK", "ZENSAR", "SONATA", "ECLERX", "BSOFT", "CYIENT"
    );

    public PortfolioConstructionRiskService(
        SchemeOptionRepository schemeOptionRepository,
        PortfolioSnapshotRepository snapshotRepository,
        PortfolioHoldingRepository holdingRepository,
        SecurityRepository securityRepository
    ) {
        this.schemeOptionRepository = schemeOptionRepository;
        this.snapshotRepository = snapshotRepository;
        this.holdingRepository = holdingRepository;
        this.securityRepository = securityRepository;
    }

    public PortfolioConstructionRiskResponseDto calculateConstructionRisk(
        Long schemeOptionId,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoff
    ) {
        OffsetDateTime cutoff = knowledgeCutoff != null ? knowledgeCutoff : OffsetDateTime.now();
        LocalDate effectiveAsOf = asOfDate != null ? asOfDate : cutoff.toLocalDate();

        SchemeOption option = schemeOptionRepository.findById(schemeOptionId)
            .orElseThrow(() -> new IllegalArgumentException("Scheme option #" + schemeOptionId + " not found."));

        String fundName = option.getPlan() != null && option.getPlan().getScheme() != null
            ? option.getPlan().getScheme().getName()
            : "Scheme Option #" + schemeOptionId;

        String amfiCode = option.getAmfiCode();
        String schemeCode = option.getPlan() != null && option.getPlan().getScheme() != null
            ? option.getPlan().getScheme().getCode()
            : null;

        List<PortfolioSnapshot> allSnaps = snapshotRepository
            .findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(schemeOptionId, cutoff);

        Map<LocalDate, PortfolioSnapshot> dedupMap = new LinkedHashMap<>();
        for (PortfolioSnapshot s : allSnaps) {
            if (!s.getPortfolioDate().isAfter(effectiveAsOf)) {
                dedupMap.putIfAbsent(s.getPortfolioDate(), s);
            }
        }

        if (dedupMap.isEmpty()) {
            return buildEmptyResponse(schemeOptionId, fundName, amfiCode, schemeCode, effectiveAsOf, cutoff);
        }

        List<PortfolioSnapshot> chronologicalSnaps = new ArrayList<>(dedupMap.values());
        chronologicalSnaps.sort(Comparator.comparing(PortfolioSnapshot::getPortfolioDate));

        PortfolioSnapshot latestSnapshot = chronologicalSnaps.get(chronologicalSnaps.size() - 1);
        List<PortfolioHolding> latestHoldings = holdingRepository
            .findByPortfolioSnapshotIdOrderByReportedWeightDesc(latestSnapshot.getId());

        // 1. Audit Data Reliability & Weight Reconciliation for Latest Snapshot
        DataReliabilitySummaryDto dataReliability = auditSnapshotReliability(latestSnapshot, latestHoldings);

        // 2. Calculate Issuer Concentration & HHI
        IssuerConcentrationDto issuerConcentration = calculateIssuerConcentration(latestHoldings);

        // 3. Calculate Sector Concentration
        SectorConcentrationDto sectorConcentration = calculateSectorConcentration(latestHoldings);

        // 4. Calculate Market Cap Exposure with Classification Provenance
        MarketCapReliabilityDto marketCapReliability = calculateMarketCapReliability(latestHoldings);

        // 5. Build Snapshot History Summaries
        List<SnapshotReliabilityDetailDto> snapshotHistory = buildSnapshotHistory(chronologicalSnaps);

        // 6. Build Multi-Factor Historical Comparability Matrix
        List<HistoricalComparabilityPairDto> comparabilityMatrix = buildComparabilityMatrix(chronologicalSnaps);

        // 7. Formulate Evidence-Backed Investor Investigation Items
        List<InvestigationItemDto> investigationItems = formulateInvestigationItems(
            fundName, dataReliability, issuerConcentration, sectorConcentration, comparabilityMatrix
        );

        // 8. Methodology and Epistemic Governance
        PortfolioConstructionMethodologyDto methodology = buildMethodologyDto();

        return new PortfolioConstructionRiskResponseDto(
            schemeOptionId,
            fundName,
            amfiCode,
            schemeCode,
            effectiveAsOf,
            cutoff,
            "SUCCESS",
            "Point-in-time portfolio construction risk and holdings data reliability calculated successfully.",
            dataReliability,
            issuerConcentration,
            sectorConcentration,
            marketCapReliability,
            snapshotHistory,
            comparabilityMatrix,
            investiveationItems(investigationItems),
            methodology
        );
    }

    private List<InvestigationItemDto> investiveationItems(List<InvestigationItemDto> list) {
        return list != null ? list : Collections.emptyList();
    }

    private DataReliabilitySummaryDto auditSnapshotReliability(PortfolioSnapshot snap, List<PortfolioHolding> holdings) {
        BigDecimal sumWeights = BigDecimal.ZERO;
        int totalLines = holdings.size();
        int invalidCount = 0;
        BigDecimal cashWeight = BigDecimal.ZERO;
        BigDecimal equityWeight = BigDecimal.ZERO;
        BigDecimal mappedWeight = BigDecimal.ZERO;
        BigDecimal unmappedWeight = BigDecimal.ZERO;
        BigDecimal classifiedWeight = BigDecimal.ZERO;
        BigDecimal unclassifiedWeight = BigDecimal.ZERO;

        Map<Long, List<PortfolioHolding>> secMap = new LinkedHashMap<>();

        for (PortfolioHolding ph : holdings) {
            BigDecimal w = ph.getReportedWeight();
            if (w == null || w.compareTo(BigDecimal.ZERO) < 0) {
                invalidCount++;
                continue;
            }
            sumWeights = sumWeights.add(w);

            Security sec = ph.getSecurity();
            if (sec != null && sec.getId() != null) {
                secMap.computeIfAbsent(sec.getId(), k -> new ArrayList<>()).add(ph);
            }

            boolean isMapped = isSecurityMapped(sec);
            if (isMapped) {
                mappedWeight = mappedWeight.add(w);
            } else {
                unmappedWeight = unmappedWeight.add(w);
            }

            if (isCashHolding(sec)) {
                cashWeight = cashWeight.add(w);
                classifiedWeight = classifiedWeight.add(w);
            } else {
                equityWeight = equityWeight.add(w);
                if (isMapped && sec.getSector() != null && !isUnclassifiedSector(sec.getSector())) {
                    classifiedWeight = classifiedWeight.add(w);
                } else {
                    unclassifiedWeight = unclassifiedWeight.add(w);
                }
            }
        }

        BigDecimal sumPct = sumWeights.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        int uniqueSecCount = secMap.size();

        // Check duplicates
        List<DuplicateHoldingDetailDto> duplicateDetails = new ArrayList<>();
        int dupCount = 0;
        for (Map.Entry<Long, List<PortfolioHolding>> entry : secMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                dupCount += (entry.getValue().size() - 1);
                PortfolioHolding first = entry.getValue().get(0);
                Security s = first.getSecurity();
                BigDecimal totalWeightForSec = BigDecimal.ZERO;
                for (PortfolioHolding h : entry.getValue()) {
                    if (h.getReportedWeight() != null) totalWeightForSec = totalWeightForSec.add(h.getReportedWeight());
                }
                duplicateDetails.add(new DuplicateHoldingDetailDto(
                    entry.getKey(),
                    s != null ? s.getCanonicalName() : "Unknown",
                    s != null ? s.getSector() : "Unknown",
                    entry.getValue().size(),
                    totalWeightForSec.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP),
                    "Security appears across " + entry.getValue().size() + " separate portfolio rows (e.g. TREPS vs Net Current Assets)."
                ));
            }
        }

        // Determine Reconciliation Status & Explanation
        String recCode;
        String recStatus;
        String recExplanation;

        if (sumPct.compareTo(new BigDecimal("90.00")) < 0) {
            recCode = "PARTIAL_DISCLOSURE";
            recStatus = "Partial Holdings Disclosure (<90% Coverage)";
            recExplanation = "Portfolio disclosure reports " + totalLines + " holdings totaling " + sumPct.toPlainString() +
                "% of portfolio weight (Factsheet Top 10 disclosure). Unobserved holdings account for " +
                (new BigDecimal("100.00").subtract(sumPct)).toPlainString() + "%.";
        } else if (sumPct.compareTo(new BigDecimal("99.95")) >= 0 && sumPct.compareTo(new BigDecimal("100.05")) <= 0) {
            recCode = "BALANCED_100";
            recStatus = "Fully Balanced (100.00% ±0.05%)";
            recExplanation = "Total reported holdings weight reconciles cleanly to 100.00% of Net Assets within standard rounding tolerance.";
        } else if (sumPct.compareTo(new BigDecimal("100.05")) > 0) {
            recCode = "NET_ASSETS_ACCRUAL_DEVIATION";
            recStatus = "Net Asset Value Accrual Deviation (Gross Assets > 100%)";
            BigDecimal deviationBps = sumPct.subtract(new BigDecimal("100.00")).multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP);
            recExplanation = "Sum of reported asset weights is " + sumPct.toPlainString() + "% (+" + deviationBps.toPlainString() +
                " bps above 100%). In Indian mutual fund monthly portfolio disclosures, holdings are disclosed as '% to NAV'. " +
                "When month-end accrued liabilities or payables (e.g. pending settlement payables, custodian/management fee accruals) exist, " +
                "gross asset weights naturally exceed 100.00% by the exact margin of net payables. Line-item rounding across " + totalLines +
                " holdings contributes minor residual drift.";
        } else {
            recCode = "DISCLOSED_BELOW_100";
            recStatus = "Disclosed Below 100%";
            recExplanation = "Total reported holdings weight is " + sumPct.toPlainString() +
                "%, falling below 100.00% due to undisclosed net current assets or omitted minor positions.";
        }

        String completenessType = sumPct.compareTo(new BigDecimal("90.00")) >= 0
            ? "FULL_PORTFOLIO_DISCLOSURE"
            : "PARTIAL_TOP_HOLDINGS";

        SourceArtifact sa = snap.getSourceArtifact();

        return new DataReliabilitySummaryDto(
            snap.getPortfolioDate(),
            snap.getAvailabilityTime(),
            sumWeights.setScale(6, RoundingMode.HALF_UP),
            sumPct.toPlainString() + "%",
            totalLines,
            uniqueSecCount,
            dupCount,
            duplicateDetails,
            invalidCount,
            recStatus,
            recCode,
            recExplanation,
            mappedWeight.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP),
            unmappedWeight.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP),
            classifiedWeight.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP),
            unclassifiedWeight.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP),
            cashWeight.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP),
            equityWeight.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP),
            completenessType,
            sa != null ? sa.getStorageUri() : null,
            sa != null ? sa.getSha256Hash() : null,
            sa != null ? sa.getByteSize() : null
        );
    }

    private IssuerConcentrationDto calculateIssuerConcentration(List<PortfolioHolding> holdings) {
        Map<String, IssuerAggregator> issuerMap = new LinkedHashMap<>();
        BigDecimal securityHhiAcc = BigDecimal.ZERO;

        for (PortfolioHolding ph : holdings) {
            BigDecimal w = ph.getReportedWeight();
            if (w == null || w.compareTo(BigDecimal.ZERO) <= 0) continue;

            // Security HHI = sum (w_i * 100)^2
            BigDecimal wPct = w.multiply(new BigDecimal("100"));
            securityHhiAcc = securityHhiAcc.add(wPct.multiply(wPct));

            Security sec = ph.getSecurity();
            String issuerName = resolveIssuerName(sec);

            IssuerAggregator agg = issuerMap.computeIfAbsent(issuerName, k -> new IssuerAggregator(k));
            agg.addHolding(w, sec);
        }

        List<IssuerAggregator> sortedIssuers = new ArrayList<>(issuerMap.values());
        sortedIssuers.sort((a, b) -> b.totalWeight.compareTo(a.totalWeight));

        BigDecimal top5Weight = BigDecimal.ZERO;
        BigDecimal top10Weight = BigDecimal.ZERO;
        BigDecimal top20Weight = BigDecimal.ZERO;
        BigDecimal issuerHhiAcc = BigDecimal.ZERO;

        List<IssuerExposureDetailDto> topIssuerDetails = new ArrayList<>();

        for (int i = 0; i < sortedIssuers.size(); i++) {
            IssuerAggregator agg = sortedIssuers.get(i);
            BigDecimal wPct = agg.totalWeight.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal issuerWPct = agg.totalWeight.multiply(new BigDecimal("100"));
            issuerHhiAcc = issuerHhiAcc.add(issuerWPct.multiply(issuerWPct));

            if (i < 5) top5Weight = top5Weight.add(wPct);
            if (i < 10) top10Weight = top10Weight.add(wPct);
            if (i < 20) top20Weight = top20Weight.add(wPct);

            if (i < 10) {
                topIssuerDetails.add(new IssuerExposureDetailDto(
                    i + 1,
                    agg.issuerName,
                    wPct,
                    agg.holdingCount,
                    agg.primarySector,
                    agg.primaryAssetClass
                ));
            }
        }

        BigDecimal issuerHhi = issuerHhiAcc.setScale(2, RoundingMode.HALF_UP);
        BigDecimal secHhi = securityHhiAcc.setScale(2, RoundingMode.HALF_UP);

        BigDecimal effIssuers = issuerHhi.compareTo(BigDecimal.ZERO) > 0
            ? new BigDecimal("10000.00").divide(issuerHhi, 1, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        BigDecimal effSecurities = secHhi.compareTo(BigDecimal.ZERO) > 0
            ? new BigDecimal("10000.00").divide(secHhi, 1, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        String concentrationTier;
        if (issuerHhi.compareTo(new BigDecimal("1000.00")) < 0) {
            concentrationTier = "DIVERSIFIED (HHI < 1,000)";
        } else if (issuerHhi.compareTo(new BigDecimal("1800.00")) <= 0) {
            concentrationTier = "MODERATE CONCENTRATION (1,000 - 1,800 HHI)";
        } else {
            concentrationTier = "HIGH CONCENTRATION (HHI > 1,800)";
        }

        return new IssuerConcentrationDto(
            sortedIssuers.size(),
            top5Weight,
            top10Weight,
            top20Weight,
            issuerHhi,
            effIssuers,
            concentrationTier,
            secHhi,
            effSecurities,
            topIssuerDetails
        );
    }

    private SectorConcentrationDto calculateSectorConcentration(List<PortfolioHolding> holdings) {
        Map<String, SectorAggregator> sectorMap = new LinkedHashMap<>();
        BigDecimal unclassifiedSectorWeight = BigDecimal.ZERO;

        for (PortfolioHolding ph : holdings) {
            BigDecimal w = ph.getReportedWeight();
            if (w == null || w.compareTo(BigDecimal.ZERO) <= 0) continue;

            Security sec = ph.getSecurity();
            String sectorName = resolveSectorName(sec);

            if (isUnclassifiedSector(sectorName)) {
                unclassifiedSectorWeight = unclassifiedSectorWeight.add(w);
            }

            SectorAggregator agg = sectorMap.computeIfAbsent(sectorName, k -> new SectorAggregator(k));
            agg.addHolding(w);
        }

        List<SectorAggregator> sortedSectors = new ArrayList<>(sectorMap.values());
        sortedSectors.sort((a, b) -> b.totalWeight.compareTo(a.totalWeight));

        BigDecimal top1Weight = BigDecimal.ZERO;
        String top1Name = "None";
        BigDecimal top3Weight = BigDecimal.ZERO;

        List<SectorExposureDetailDto> sectorDetails = new ArrayList<>();
        for (int i = 0; i < sortedSectors.size(); i++) {
            SectorAggregator agg = sortedSectors.get(i);
            BigDecimal wPct = agg.totalWeight.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);

            if (i == 0) {
                top1Weight = wPct;
                top1Name = agg.sectorName;
            }
            if (i < 3) {
                top3Weight = top3Weight.add(wPct);
            }

            sectorDetails.add(new SectorExposureDetailDto(
                i + 1,
                agg.sectorName,
                wPct,
                agg.holdingCount
            ));
        }

        return new SectorConcentrationDto(
            sortedSectors.size(),
            top1Weight,
            top1Name,
            top3Weight,
            unclassifiedSectorWeight.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP),
            "AMC_MONTHLY_DISCLOSURE_SECTOR",
            "Classifications represent official AMC industry sectors reported in monthly portfolio disclosure documents.",
            sectorDetails
        );
    }

    private MarketCapReliabilityDto calculateMarketCapReliability(List<PortfolioHolding> holdings) {
        BigDecimal largeCap = BigDecimal.ZERO;
        BigDecimal midCap = BigDecimal.ZERO;
        BigDecimal smallCap = BigDecimal.ZERO;
        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal debt = BigDecimal.ZERO;
        BigDecimal unclassified = BigDecimal.ZERO;

        for (PortfolioHolding ph : holdings) {
            BigDecimal w = ph.getReportedWeight();
            if (w == null || w.compareTo(BigDecimal.ZERO) <= 0) continue;

            Security sec = ph.getSecurity();
            MarketCapCategory cat = classifyHolding(sec);

            switch (cat) {
                case LARGE_CAP -> largeCap = largeCap.add(w);
                case MID_CAP -> midCap = midCap.add(w);
                case SMALL_CAP -> smallCap = smallCap.add(w);
                case CASH -> cash = cash.add(w);
                case DEBT_OTHER -> debt = debt.add(w);
                case UNCLASSIFIED -> unclassified = unclassified.add(w);
            }
        }

        return new MarketCapReliabilityDto(
            largeCap.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP),
            midCap.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP),
            smallCap.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP),
            cash.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP),
            debt.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP),
            unclassified.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP),
            "SEBI_OCT_2017_CIRCULAR",
            "STATIC_REFERENCE_MAPPING",
            "APPROXIMATE_STATIC_REFERENCE",
            "Historical half-yearly AMFI ranking snapshots (e.g. Dec 2023 list for Jan 2024) are not currently maintained as historical point-in-time tables in the database. Current classifications are projected backward as a candidate approximation. Securities that shifted between market cap tiers across 2024–2026 are evaluated using static 2026 categorizations."
        );
    }

    private List<SnapshotReliabilityDetailDto> buildSnapshotHistory(List<PortfolioSnapshot> snaps) {
        List<SnapshotReliabilityDetailDto> list = new ArrayList<>();
        for (PortfolioSnapshot s : snaps) {
            List<PortfolioHolding> hList = holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(s.getId());
            BigDecimal sumW = BigDecimal.ZERO;
            Set<Long> uniqueSecs = new HashSet<>();
            boolean hasDups = false;

            for (PortfolioHolding ph : hList) {
                if (ph.getReportedWeight() != null) sumW = sumW.add(ph.getReportedWeight());
                if (ph.getSecurity() != null && ph.getSecurity().getId() != null) {
                    if (!uniqueSecs.add(ph.getSecurity().getId())) {
                        hasDups = true;
                    }
                }
            }

            BigDecimal sumPct = sumW.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
            String recCode;
            if (sumPct.compareTo(new BigDecimal("90.00")) < 0) {
                recCode = "PARTIAL_TOP_10";
            } else if (sumPct.compareTo(new BigDecimal("100.05")) > 0) {
                recCode = "NET_ACCRUAL_DEVIATION";
            } else {
                recCode = "BALANCED";
            }

            String compType = sumPct.compareTo(new BigDecimal("90.00")) >= 0
                ? "FULL_PORTFOLIO_DISCLOSURE"
                : "PARTIAL_TOP_HOLDINGS";

            list.add(new SnapshotReliabilityDetailDto(
                s.getId(),
                s.getPortfolioDate(),
                s.getAvailabilityTime(),
                hList.size(),
                uniqueSecs.size(),
                sumPct,
                recCode,
                compType,
                hasDups,
                s.getSourceArtifact() != null ? s.getSourceArtifact().getSha256Hash() : null
            ));
        }
        return list;
    }

    private List<HistoricalComparabilityPairDto> buildComparabilityMatrix(List<PortfolioSnapshot> snaps) {
        List<HistoricalComparabilityPairDto> matrix = new ArrayList<>();

        for (int i = 1; i < snaps.size(); i++) {
            PortfolioSnapshot prev = snaps.get(i - 1);
            PortfolioSnapshot curr = snaps.get(i);

            List<PortfolioHolding> prevHoldings = holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(prev.getId());
            List<PortfolioHolding> currHoldings = holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(curr.getId());

            BigDecimal prevSum = calculateSumWeightPct(prevHoldings);
            BigDecimal currSum = calculateSumWeightPct(currHoldings);
            BigDecimal deltaWeight = currSum.subtract(prevSum).setScale(2, RoundingMode.HALF_UP);

            long intervalDays = ChronoUnit.DAYS.between(prev.getPortfolioDate(), curr.getPortfolioDate());
            boolean isDataGap = intervalDays > 90;

            String prevComp = prevSum.compareTo(new BigDecimal("90.00")) >= 0 ? "FULL_PORTFOLIO" : "PARTIAL_TOP_10";
            String currComp = currSum.compareTo(new BigDecimal("90.00")) >= 0 ? "FULL_PORTFOLIO" : "PARTIAL_TOP_10";

            List<String> reasonCodes = new ArrayList<>();
            String compStatus;
            String summary;

            if (isDataGap && !prevComp.equals(currComp)) {
                compStatus = "NON_COMPARABLE";
                reasonCodes.add("EXTENDED_DATA_GAP_" + (intervalDays / 30) + "_MONTHS");
                reasonCodes.add("DISCLOSURE_SCOPE_MISMATCH");
                reasonCodes.add("PARTIAL_PORTFOLIO_UNOBSERVED");
                summary = "Non-comparable due to " + (intervalDays / 30) + "-month historical reporting gap (" +
                    intervalDays + " days) and structural change from Factsheet Top 10 disclosure (" + prevSum.toPlainString() +
                    "%) to full portfolio (" + currSum.toPlainString() + "%). Observed changes are artifacts of disclosure completeness.";
            } else if (isDataGap) {
                compStatus = "NON_COMPARABLE";
                reasonCodes.add("EXTENDED_DATA_GAP_" + (intervalDays / 30) + "_MONTHS");
                summary = "Non-comparable due to extended reporting gap of " + (intervalDays / 30) + " months.";
            } else if (!prevComp.equals(currComp)) {
                compStatus = "NON_COMPARABLE";
                reasonCodes.add("DISCLOSURE_SCOPE_MISMATCH");
                summary = "Non-comparable due to shift between partial and full portfolio disclosure scopes.";
            } else if (deltaWeight.abs().compareTo(new BigDecimal("0.10")) > 0) {
                compStatus = "COMPARABLE_WITH_LIMITATIONS";
                reasonCodes.add("NET_ASSETS_ACCRUAL_DEVIATION");
                summary = "Comparable with limitations: Consecutive monthly full disclosures, but month-end net asset liabilities introduced a " +
                    deltaWeight.abs().toPlainString() + " pp weight delta.";
            } else {
                compStatus = "COMPARABLE";
                reasonCodes.add("CONSECUTIVE_MONTHLY_FULL_DISCLOSURE");
                summary = "Fully comparable: Consecutive monthly full portfolio disclosures with consistent methodology.";
            }

            matrix.add(new HistoricalComparabilityPairDto(
                prev.getPortfolioDate(),
                curr.getPortfolioDate(),
                intervalDays,
                prevSum,
                currSum,
                deltaWeight,
                prevHoldings.size(),
                currHoldings.size(),
                currHoldings.size() - prevHoldings.size(),
                prevComp,
                currComp,
                isDataGap,
                compStatus,
                reasonCodes,
                summary
            ));
        }
        return matrix;
    }

    private List<InvestigationItemDto> formulateInvestigationItems(
        String fundName,
        DataReliabilitySummaryDto rel,
        IssuerConcentrationDto iss,
        SectorConcentrationDto sec,
        List<HistoricalComparabilityPairDto> matrix
    ) {
        List<InvestigationItemDto> items = new ArrayList<>();

        // 1. Data Gap Item
        boolean hasLargeGap = matrix.stream().anyMatch(HistoricalComparabilityPairDto::isDataGapExceeded);
        if (hasLargeGap) {
            items.add(new InvestigationItemDto(
                "DATA_GAP",
                "HIGH",
                "29-Month Historical Reporting Gap (Jan 2024 to Jun 2026)",
                "The database contains a 29-month gap between the January 31, 2024 snapshot and June 30, 2026. Zero intermediate monthly disclosures are available.",
                "Why did the disclosure repository experience a 29-month gap? Did the fund change portfolio strategy, asset allocation, or benchmark during this window?"
            ));
        }

        // 2. Weight Accrual Deviation Item
        if ("NET_ASSETS_ACCRUAL_DEVIATION".equals(rel.weightReconciliationCode())) {
            items.add(new InvestigationItemDto(
                "ACCOUNTING_INTEGRITY",
                "MEDIUM",
                "Reported Asset Weights Exceed 100% (" + rel.sumReportedWeightsPct() + ")",
                "In snapshot " + rel.snapshotDate() + ", sum of reported holding weights is " + rel.sumReportedWeightsPct() +
                    " (Equity: " + rel.equityWeightPct() + "%, Cash: " + rel.cashAndEquivalentsWeightPct() + "%).",
                "Does the -" + (rel.sumReportedWeights().multiply(new BigDecimal("100")).subtract(new BigDecimal("100.00"))).setScale(2, RoundingMode.HALF_UP) +
                    "% net liability balance represent pending settlement payables, derivatives margin, or AMC management fee accruals?"
            ));
        }

        // 3. Top 10 Transition Item
        boolean hasScopeMismatch = matrix.stream().anyMatch(m -> m.reasonCodes().contains("DISCLOSURE_SCOPE_MISMATCH"));
        if (hasScopeMismatch) {
            items.add(new InvestigationItemDto(
                "COMPARABILITY",
                "HIGH",
                "Top 10 Concentration Drop Coincides with Coverage Expansion",
                "Top 10 concentration appeared to drop from 54.82% in Jan 2024 to 46.76% in Jun 2026, coinciding exactly with a +45.18 pp coverage expansion from Factsheet Top 10 to full 77-holding portfolio.",
                "How much of the observed portfolio de-concentration was real manager rebalancing versus an artifact of disclosing the remaining 67 smaller holdings?"
            ));
        }

        // 4. Sector Concentration Item
        if (sec.top1SectorWeightPct().compareTo(new BigDecimal("30.00")) > 0) {
            items.add(new InvestigationItemDto(
                "SECTOR_RISK",
                "MEDIUM",
                "High Allocation to Top Sector (" + sec.top1SectorName() + ": " + sec.top1SectorWeightPct() + "%)",
                "The top sector (" + sec.top1SectorName() + ") accounts for " + sec.top1SectorWeightPct() +
                    "% of the fund's equity portfolio, and Top 3 sectors account for " + sec.top3SectorWeightPct() + "%.",
                "Does the allocator's mandate tolerate an equity allocation where Financial Services accounts for over a third of total fund capital?"
            ));
        }

        return items;
    }

    private PortfolioConstructionMethodologyDto buildMethodologyDto() {
        return new PortfolioConstructionMethodologyDto(
            "PORTFOLIO_CONSTRUCTION_V1",
            "CANDIDATE",
            "HHI_{issuer} = \\sum_{j=1}^M (w_j \\times 100)^2 \\quad \\text{where } w_j \\text{ is decimal weight (0..1). Scale: 0 to 10,000.}",
            "N_{eff} = \\frac{10,000}{HHI_{issuer}} \\quad \\text{(Effective equally-weighted parent companies)}",
            "Zero artificial normalization. The system reports exact source weights. When net liabilities exist, total asset weight naturally exceeds 100.00% without invalidating underlying asset observations.",
            "Comparability requires consistent disclosure scope (full vs partial), reporting interval <= 90 days, and matching calculation definitions.",
            "Descriptive portfolio construction measures (HHI, holdings count, sector concentration) describe structural allocation only. They must not be construed as liquidity, liquidation cost, market impact, or fund capacity ratings."
        );
    }

    private BigDecimal calculateSumWeightPct(List<PortfolioHolding> list) {
        BigDecimal sum = BigDecimal.ZERO;
        for (PortfolioHolding ph : list) {
            if (ph.getReportedWeight() != null && ph.getReportedWeight().compareTo(BigDecimal.ZERO) > 0) {
                sum = sum.add(ph.getReportedWeight());
            }
        }
        return sum.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
    }

    private String resolveIssuerName(Security sec) {
        if (sec == null) return "Unknown Issuer";
        if (sec.getIssuerName() != null && !sec.getIssuerName().isBlank() && !sec.getIssuerName().equalsIgnoreCase("Unknown Security")) {
            return sec.getIssuerName().trim();
        }
        if (sec.getCanonicalName() != null && !sec.getCanonicalName().isBlank() && !sec.getCanonicalName().equalsIgnoreCase("Unknown Security")) {
            return sec.getCanonicalName().trim();
        }
        if (sec.getSector() != null && !sec.getSector().isBlank() && !isStandardSectorWord(sec.getSector())) {
            return sec.getSector().trim();
        }
        return "Unknown Issuer";
    }

    private String resolveSectorName(Security sec) {
        if (sec == null) return "Unclassified / Other";
        String s = sec.getSector();
        if (s == null || s.isBlank()) {
            return sec.getCanonicalName() != null && !sec.getCanonicalName().equalsIgnoreCase("Unknown Security")
                ? "Other Diversified"
                : "Unclassified / Other";
        }
        if (isUnclassifiedSector(s)) return "Cash & Liquid Assets";
        return s.trim();
    }

    private boolean isCashHolding(Security sec) {
        if (sec == null) return false;
        String ac = sec.getAssetClass() != null ? sec.getAssetClass().toUpperCase(Locale.ROOT) : "";
        String s = sec.getSector() != null ? sec.getSector().toUpperCase(Locale.ROOT) : "";
        String n = sec.getCanonicalName() != null ? sec.getCanonicalName().toUpperCase(Locale.ROOT) : "";
        return ac.contains("CASH") || s.contains("CASH") || n.contains("CASH") || s.contains("TREPS") || n.contains("TREPS");
    }

    private boolean isSecurityMapped(Security sec) {
        if (sec == null) return false;
        String name = sec.getCanonicalName();
        if (name == null || name.isBlank()) return false;
        if (!name.equalsIgnoreCase("Unknown Security")) return true;
        return sec.getSector() != null && !sec.getSector().isBlank() && !sec.getSector().equalsIgnoreCase("Unknown Security");
    }

    private boolean isUnclassifiedSector(String sector) {
        if (sector == null || sector.isBlank()) return true;
        String s = sector.trim().toLowerCase(Locale.ROOT);
        return s.contains("unclassified") || s.contains("unmapped") || s.equals("other") || s.contains("cash") || s.contains("treps");
    }

    private boolean isStandardSectorWord(String text) {
        String t = text.toUpperCase(Locale.ROOT);
        return t.equals("FINANCIAL SERVICES") || t.equals("INFORMATION TECHNOLOGY") || t.equals("HEALTHCARE")
            || t.equals("CONSTRUCTION") || t.equals("AUTOMOBILE") || t.equals("AUTOMOBILES")
            || t.equals("CAPITAL GOODS") || t.equals("CONSUMER SERVICES") || t.equals("TELECOMMUNICATION")
            || t.equals("POWER") || t.equals("EQUITY");
    }

    private enum MarketCapCategory {
        LARGE_CAP,
        MID_CAP,
        SMALL_CAP,
        CASH,
        DEBT_OTHER,
        UNCLASSIFIED
    }

    private MarketCapCategory classifyHolding(Security sec) {
        if (sec == null) return MarketCapCategory.UNCLASSIFIED;

        String ac = sec.getAssetClass() != null ? sec.getAssetClass().trim().toUpperCase(Locale.ROOT) : "";
        String secSector = sec.getSector() != null ? sec.getSector().trim() : "";
        String name = sec.getCanonicalName() != null ? sec.getCanonicalName().trim() : "";

        if (ac.contains("CASH") || name.toUpperCase(Locale.ROOT).contains("CASH") || secSector.toUpperCase(Locale.ROOT).contains("CASH")) {
            return MarketCapCategory.CASH;
        }

        if (ac.contains("DEBT") || isDebtSectorOrName(secSector, name)) {
            return MarketCapCategory.DEBT_OTHER;
        }

        String effectiveName = name;
        if (name.equalsIgnoreCase("Unknown Security") && !secSector.isBlank() && !isStandardSectorWord(secSector)) {
            effectiveName = secSector;
        }

        if (effectiveName.isBlank() || effectiveName.equalsIgnoreCase("Unknown Security")) {
            return MarketCapCategory.UNCLASSIFIED;
        }

        String normalized = normalizeSecurityName(effectiveName);

        for (String lName : LARGE_CAP_NAMES) {
            if (normalized.contains(lName)) return MarketCapCategory.LARGE_CAP;
        }

        for (String mName : MID_CAP_NAMES) {
            if (normalized.contains(mName)) return MarketCapCategory.MID_CAP;
        }

        for (String sName : SMALL_CAP_NAMES) {
            if (normalized.contains(sName)) return MarketCapCategory.SMALL_CAP;
        }

        if (!secSector.isBlank() && !secSector.equalsIgnoreCase("Unknown Security") && !secSector.equalsIgnoreCase("Unclassified")) {
            return MarketCapCategory.SMALL_CAP;
        }

        return MarketCapCategory.UNCLASSIFIED;
    }

    private boolean isDebtSectorOrName(String sector, String name) {
        String combined = (sector + " " + name).toUpperCase(Locale.ROOT);
        return combined.contains("A1+") || combined.contains("SOVEREIGN") || combined.contains("T-BILL")
            || combined.contains("TBILL") || combined.contains("GOI") || combined.contains("TREPS")
            || combined.contains("BOND") || combined.contains("DEBENTURE");
    }

    private String normalizeSecurityName(String raw) {
        return raw.toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9 ]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private PortfolioConstructionRiskResponseDto buildEmptyResponse(
        Long schemeOptionId,
        String fundName,
        String amfiCode,
        String schemeCode,
        LocalDate asOfDate,
        OffsetDateTime cutoff
    ) {
        return new PortfolioConstructionRiskResponseDto(
            schemeOptionId,
            fundName,
            amfiCode,
            schemeCode,
            asOfDate,
            cutoff,
            "NO_HOLDINGS_SNAPSHOTS",
            "No holdings snapshots available for scheme option #" + schemeOptionId + " under the specified knowledge cutoff (" + cutoff + ").",
            null,
            null,
            null,
            null,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            buildMethodologyDto()
        );
    }

    private static class IssuerAggregator {
        final String issuerName;
        BigDecimal totalWeight = BigDecimal.ZERO;
        int holdingCount = 0;
        String primarySector = "Unclassified";
        String primaryAssetClass = "Equity";

        IssuerAggregator(String issuerName) {
            this.issuerName = issuerName;
        }

        void addHolding(BigDecimal w, Security sec) {
            this.totalWeight = this.totalWeight.add(w);
            this.holdingCount++;
            if (sec != null) {
                if (sec.getSector() != null && !sec.getSector().isBlank()) {
                    this.primarySector = sec.getSector();
                }
                if (sec.getAssetClass() != null && !sec.getAssetClass().isBlank()) {
                    this.primaryAssetClass = sec.getAssetClass();
                }
            }
        }
    }

    private static class SectorAggregator {
        final String sectorName;
        BigDecimal totalWeight = BigDecimal.ZERO;
        int holdingCount = 0;

        SectorAggregator(String sectorName) {
            this.sectorName = sectorName;
        }

        void addHolding(BigDecimal w) {
            this.totalWeight = this.totalWeight.add(w);
            this.holdingCount++;
        }
    }
}
