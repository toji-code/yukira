package com.yukira.backend.service;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.*;
import com.yukira.backend.dto.portfolio.FundConcentrationDto.*;
import com.yukira.backend.dto.portfolio.PortfolioOverlapResponseDto.*;
import com.yukira.backend.dto.portfolio.PortfolioSecurityAggregationDto.*;
import com.yukira.backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service orchestrating evidence-based Portfolio Intelligence calculations:
 * 1. Single-Fund Concentration & HHI (Herfindahl-Hirschman Index)
 * 2. Pairwise Portfolio Overlap
 * 3. Multi-Fund Look-Through Security Exposure Aggregation
 *
 * Epistemic & Calculation Rules:
 * 1. Overlap(A, B) = sum_{i in A int B} min(w_{A,i}, w_{B,i}) where weights w are expressed as decimal (0..1) or percentage (0..100).
 * 2. HHI = sum_{i=1}^N (100 * w_i)^2 where w_i is reported weight in decimal format (0..1).
 * 3. Look-through Security Exposure = sum_{k=1}^M (FundWeight_k * HoldingWeight_{k,i}).
 * 4. Data Quality Propagation: Partial disclosures (<90% coverage), stale disclosures (>60 days), or missing snapshots propagate machine-readable quality states.
 * 5. Deterministic Math: Floating point rounding discipline with BigDecimal and 2-decimal percentage output.
 */
@Service
@Transactional(readOnly = true)
public class PortfolioIntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioIntelligenceService.class);

    private final SchemeOptionRepository schemeOptionRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final PortfolioHoldingRepository holdingRepository;
    private final SecurityIdentifierRepository securityIdentifierRepository;
    private final NavObservationRepository navObservationRepository;

    public PortfolioIntelligenceService(
        SchemeOptionRepository schemeOptionRepository,
        PortfolioSnapshotRepository snapshotRepository,
        PortfolioHoldingRepository holdingRepository,
        SecurityIdentifierRepository securityIdentifierRepository,
        NavObservationRepository navObservationRepository
    ) {
        this.schemeOptionRepository = schemeOptionRepository;
        this.snapshotRepository = snapshotRepository;
        this.holdingRepository = holdingRepository;
        this.securityIdentifierRepository = securityIdentifierRepository;
        this.navObservationRepository = navObservationRepository;
    }

    /**
     * Calculates deterministic concentration metrics (Top 5, Top 10, HHI) for a target scheme option.
     */
    public FundConcentrationDto calculateFundConcentration(Long schemeOptionId, OffsetDateTime knowledgeCutoff) {
        OffsetDateTime cutoff = knowledgeCutoff != null ? knowledgeCutoff : OffsetDateTime.now();

        SchemeOption option = schemeOptionRepository.findById(schemeOptionId)
            .orElseThrow(() -> new IllegalArgumentException("Scheme option #" + schemeOptionId + " not found."));

        String fundName = option.getPlan() != null && option.getPlan().getScheme() != null
            ? option.getPlan().getScheme().getName()
            : "Scheme Option #" + schemeOptionId;

        Optional<PortfolioSnapshot> snapOpt = snapshotRepository
            .findTopBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDesc(schemeOptionId, cutoff);

        if (snapOpt.isEmpty()) {
            return new FundConcentrationDto(
                schemeOptionId, fundName, null, 0,
                BigDecimal.ZERO, "0.00%", BigDecimal.ZERO, "0.00%", BigDecimal.ZERO, "0.00%",
                BigDecimal.ZERO, "UNAVAILABLE", Collections.emptyList(),
                "UNAVAILABLE", List.of("No holdings snapshot available in YUKIRA database.")
            );
        }

        PortfolioSnapshot snap = snapOpt.get();
        List<PortfolioHolding> holdings = holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(snap.getId());

        if (holdings.isEmpty()) {
            return new FundConcentrationDto(
                schemeOptionId, fundName, snap.getPortfolioDate(), 0,
                BigDecimal.ZERO, "0.00%", BigDecimal.ZERO, "0.00%", BigDecimal.ZERO, "0.00%",
                BigDecimal.ZERO, "UNAVAILABLE", Collections.emptyList(),
                "UNAVAILABLE", List.of("Holdings snapshot exists but contains 0 holdings rows.")
            );
        }

        BigDecimal sumWeightsDecimal = BigDecimal.ZERO;
        BigDecimal top5WeightDecimal = BigDecimal.ZERO;
        BigDecimal top10WeightDecimal = BigDecimal.ZERO;
        BigDecimal hhiAcc = BigDecimal.ZERO;

        List<TopHoldingDetailDto> topHoldingList = new ArrayList<>();
        int rank = 1;

        for (PortfolioHolding h : holdings) {
            BigDecimal wDec = h.getReportedWeight() != null ? h.getReportedWeight() : BigDecimal.ZERO;
            sumWeightsDecimal = sumWeightsDecimal.add(wDec);

            if (rank <= 5) top5WeightDecimal = top5WeightDecimal.add(wDec);
            if (rank <= 10) top10WeightDecimal = top10WeightDecimal.add(wDec);

            // HHI = sum((wDec * 100)^2)
            BigDecimal pctVal = wDec.multiply(BigDecimal.valueOf(100));
            hhiAcc = hhiAcc.add(pctVal.multiply(pctVal));

            Security sec = h.getSecurity();
            String isin = securityIdentifierRepository.findTopBySecurityIdAndIdType(sec.getId(), "ISIN")
                .map(SecurityIdentifier::getIdValue)
                .orElse(null);

            BigDecimal wPct = pctVal.setScale(2, RoundingMode.HALF_UP);
            if (rank <= 10) {
                topHoldingList.add(new TopHoldingDetailDto(
                    rank,
                    sec.getCanonicalName(),
                    isin,
                    sec.getSector(),
                    wPct,
                    wPct.toPlainString() + "%"
                ));
            }
            rank++;
        }

        BigDecimal sumWeightsPct = sumWeightsDecimal.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal top5Pct = top5WeightDecimal.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal top10Pct = top10WeightDecimal.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal hhiFinal = hhiAcc.setScale(2, RoundingMode.HALF_UP);

        String hhiClassification;
        if (hhiFinal.compareTo(BigDecimal.valueOf(2500)) > 0) {
            hhiClassification = "HIGHLY_CONCENTRATED";
        } else if (hhiFinal.compareTo(BigDecimal.valueOf(1500)) > 0) {
            hhiClassification = "MODERATELY_CONCENTRATED";
        } else {
            hhiClassification = "DIVERSIFIED";
        }

        List<String> limitations = new ArrayList<>();
        LocalDate cutoffDate = cutoff.toLocalDate();
        boolean isStale = snap.getPortfolioDate().isBefore(cutoffDate.minusDays(60));
        boolean isPartial = sumWeightsDecimal.compareTo(new BigDecimal("0.90")) < 0;

        if (isPartial) limitations.add("Disclosed weight sum is " + sumWeightsPct.toPlainString() + "% (<90% coverage).");
        if (isStale) limitations.add("Historical holdings snapshot as of " + snap.getPortfolioDate() + " (>60 days old).");

        String qualityState = (isPartial || isStale) ? "DATA_QUALITY_LIMITED" : "VALID";

        return new FundConcentrationDto(
            schemeOptionId,
            fundName,
            snap.getPortfolioDate(),
            holdings.size(),
            sumWeightsPct,
            sumWeightsPct.toPlainString() + "%",
            top5Pct,
            top5Pct.toPlainString() + "%",
            top10Pct,
            top10Pct.toPlainString() + "%",
            hhiFinal,
            hhiClassification,
            topHoldingList,
            qualityState,
            limitations
        );
    }

    /**
     * Calculates deterministic pairwise portfolio overlap between two scheme options A and B.
     * Overlap(A, B) = sum_{i in A int B} min(w_{A,i}, w_{B,i})
     */
    public PortfolioOverlapResponseDto calculatePairwiseOverlap(Long schemeOptionIdA, Long schemeOptionIdB, OffsetDateTime knowledgeCutoff) {
        OffsetDateTime cutoff = knowledgeCutoff != null ? knowledgeCutoff : OffsetDateTime.now();

        SchemeOption optA = schemeOptionRepository.findById(schemeOptionIdA)
            .orElseThrow(() -> new IllegalArgumentException("Scheme option A #" + schemeOptionIdA + " not found."));
        SchemeOption optB = schemeOptionRepository.findById(schemeOptionIdB)
            .orElseThrow(() -> new IllegalArgumentException("Scheme option B #" + schemeOptionIdB + " not found."));

        String nameA = optA.getPlan() != null && optA.getPlan().getScheme() != null ? optA.getPlan().getScheme().getName() : "Option #" + schemeOptionIdA;
        String nameB = optB.getPlan() != null && optB.getPlan().getScheme() != null ? optB.getPlan().getScheme().getName() : "Option #" + schemeOptionIdB;

        Optional<PortfolioSnapshot> snapOptA = snapshotRepository
            .findTopBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDesc(schemeOptionIdA, cutoff);
        Optional<PortfolioSnapshot> snapOptB = snapshotRepository
            .findTopBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDesc(schemeOptionIdB, cutoff);

        List<String> limitations = new ArrayList<>();
        if (snapOptA.isEmpty() || snapOptB.isEmpty()) {
            if (snapOptA.isEmpty()) limitations.add("No holdings snapshot available for fund A (" + nameA + ").");
            if (snapOptB.isEmpty()) limitations.add("No holdings snapshot available for fund B (" + nameB + ").");
            return new PortfolioOverlapResponseDto(
                schemeOptionIdA, nameA, snapOptA.map(PortfolioSnapshot::getPortfolioDate).orElse(null),
                schemeOptionIdB, nameB, snapOptB.map(PortfolioSnapshot::getPortfolioDate).orElse(null),
                BigDecimal.ZERO, "0.00%", 0, 0, 0, 0, 0, Collections.emptyList(),
                "UNAVAILABLE", limitations
            );
        }

        PortfolioSnapshot snapA = snapOptA.get();
        PortfolioSnapshot snapB = snapOptB.get();

        List<PortfolioHolding> holdingsA = holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(snapA.getId());
        List<PortfolioHolding> holdingsB = holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(snapB.getId());

        // Build identity map for A: Key = ISIN or Canonical Name
        Map<String, PortfolioHolding> mapA = new HashMap<>();
        Map<String, String> secNameMap = new HashMap<>();
        Map<String, String> secIsinMap = new HashMap<>();
        Map<String, String> secSectorMap = new HashMap<>();

        for (PortfolioHolding h : holdingsA) {
            String isin = securityIdentifierRepository.findTopBySecurityIdAndIdType(h.getSecurity().getId(), "ISIN")
                .map(SecurityIdentifier::getIdValue)
                .orElse(null);
            String key = (isin != null && !isin.isBlank()) ? isin.toUpperCase(Locale.ROOT) : h.getSecurity().getCanonicalName().toUpperCase(Locale.ROOT);
            mapA.put(key, h);
            secNameMap.put(key, h.getSecurity().getCanonicalName());
            if (isin != null) secIsinMap.put(key, isin);
            if (h.getSecurity().getSector() != null) secSectorMap.put(key, h.getSecurity().getSector());
        }

        // Build identity map for B
        Map<String, PortfolioHolding> mapB = new HashMap<>();
        for (PortfolioHolding h : holdingsB) {
            String isin = securityIdentifierRepository.findTopBySecurityIdAndIdType(h.getSecurity().getId(), "ISIN")
                .map(SecurityIdentifier::getIdValue)
                .orElse(null);
            String key = (isin != null && !isin.isBlank()) ? isin.toUpperCase(Locale.ROOT) : h.getSecurity().getCanonicalName().toUpperCase(Locale.ROOT);
            mapB.put(key, h);
            if (!secNameMap.containsKey(key)) secNameMap.put(key, h.getSecurity().getCanonicalName());
            if (isin != null && !secIsinMap.containsKey(key)) secIsinMap.put(key, isin);
            if (h.getSecurity().getSector() != null && !secSectorMap.containsKey(key)) secSectorMap.put(key, h.getSecurity().getSector());
        }

        BigDecimal totalOverlapDecimal = BigDecimal.ZERO;
        List<OverlappingSecurityDto> overlappingSecurities = new ArrayList<>();
        Set<String> commonKeys = new HashSet<>();

        for (Map.Entry<String, PortfolioHolding> entryA : mapA.entrySet()) {
            String key = entryA.getKey();
            PortfolioHolding hA = entryA.getValue();
            if (mapB.containsKey(key)) {
                commonKeys.add(key);
                PortfolioHolding hB = mapB.get(key);

                BigDecimal wA = hA.getReportedWeight() != null ? hA.getReportedWeight() : BigDecimal.ZERO;
                BigDecimal wB = hB.getReportedWeight() != null ? hB.getReportedWeight() : BigDecimal.ZERO;
                BigDecimal minW = wA.min(wB);

                totalOverlapDecimal = totalOverlapDecimal.add(minW);

                BigDecimal wAPct = wA.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
                BigDecimal wBPct = wB.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
                BigDecimal minWPct = minW.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);

                overlappingSecurities.add(new OverlappingSecurityDto(
                    secNameMap.get(key),
                    secIsinMap.get(key),
                    secSectorMap.get(key),
                    wAPct,
                    wAPct.toPlainString() + "%",
                    wBPct,
                    wBPct.toPlainString() + "%",
                    minWPct,
                    minWPct.toPlainString() + "%"
                ));
            }
        }

        // Sort overlapping securities by overlap contribution descending
        overlappingSecurities.sort((a, b) -> b.overlapContribution().compareTo(a.overlapContribution()));

        BigDecimal overlapPct = totalOverlapDecimal.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        int commonCount = commonKeys.size();
        int uniqueCountA = mapA.size() - commonCount;
        int uniqueCountB = mapB.size() - commonCount;

        LocalDate cutoffDate = cutoff.toLocalDate();
        if (snapA.getPortfolioDate().isBefore(cutoffDate.minusDays(60))) {
            limitations.add("Fund A holdings snapshot as of " + snapA.getPortfolioDate() + " is >60 days old.");
        }
        if (snapB.getPortfolioDate().isBefore(cutoffDate.minusDays(60))) {
            limitations.add("Fund B holdings snapshot as of " + snapB.getPortfolioDate() + " is >60 days old.");
        }
        if (!snapA.getPortfolioDate().equals(snapB.getPortfolioDate())) {
            limitations.add("Reporting date mismatch: Fund A as of " + snapA.getPortfolioDate() + ", Fund B as of " + snapB.getPortfolioDate() + ".");
        }

        BigDecimal sumWeightsA = holdingsA.stream()
            .map(h -> h.getReportedWeight() != null ? h.getReportedWeight() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumWeightsB = holdingsB.stream()
            .map(h -> h.getReportedWeight() != null ? h.getReportedWeight() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (sumWeightsA.compareTo(new BigDecimal("0.90")) < 0) {
            limitations.add("Fund A has partial holdings disclosure (" + holdingsA.size() + " positions, " + sumWeightsA.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP) + "% coverage).");
        }
        if (sumWeightsB.compareTo(new BigDecimal("0.90")) < 0) {
            limitations.add("Fund B has partial holdings disclosure (" + holdingsB.size() + " positions, " + sumWeightsB.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP) + "% coverage).");
        }

        String qualityState = limitations.isEmpty() ? "VALID" : "DATA_QUALITY_LIMITED";

        return new PortfolioOverlapResponseDto(
            schemeOptionIdA,
            nameA,
            snapA.getPortfolioDate(),
            schemeOptionIdB,
            nameB,
            snapB.getPortfolioDate(),
            overlapPct,
            overlapPct.toPlainString() + "%",
            commonCount,
            mapA.size(),
            mapB.size(),
            uniqueCountA,
            uniqueCountB,
            overlappingSecurities,
            qualityState,
            limitations
        );
    }

    /**
     * Calculates deterministic pairwise Pearson correlation over synchronous daily NAV returns.
     */
    public PairwiseCorrelationDto calculatePairwiseCorrelation(Long schemeOptionIdA, Long schemeOptionIdB, OffsetDateTime knowledgeCutoff) {
        OffsetDateTime cutoff = knowledgeCutoff != null ? knowledgeCutoff : OffsetDateTime.now();
        LocalDate asOfDate = cutoff.toLocalDate();

        SchemeOption optA = schemeOptionRepository.findById(schemeOptionIdA)
            .orElseThrow(() -> new IllegalArgumentException("Scheme option A #" + schemeOptionIdA + " not found."));
        SchemeOption optB = schemeOptionRepository.findById(schemeOptionIdB)
            .orElseThrow(() -> new IllegalArgumentException("Scheme option B #" + schemeOptionIdB + " not found."));

        String nameA = optA.getPlan() != null && optA.getPlan().getScheme() != null ? optA.getPlan().getScheme().getName() : "Option #" + schemeOptionIdA;
        String nameB = optB.getPlan() != null && optB.getPlan().getScheme() != null ? optB.getPlan().getScheme().getName() : "Option #" + schemeOptionIdB;

        List<NavObservation> obsA = navObservationRepository.findAuthoritativeObservationsAsOfCutoff(schemeOptionIdA, asOfDate, cutoff);
        List<NavObservation> obsB = navObservationRepository.findAuthoritativeObservationsAsOfCutoff(schemeOptionIdB, asOfDate, cutoff);

        List<String> limitations = new ArrayList<>();
        if (obsA.isEmpty() || obsB.isEmpty()) {
            if (obsA.isEmpty()) limitations.add("Insufficient historical NAV observations available for fund A (" + nameA + ").");
            if (obsB.isEmpty()) limitations.add("Insufficient historical NAV observations available for fund B (" + nameB + ").");
            return new PairwiseCorrelationDto(
                schemeOptionIdA, nameA, schemeOptionIdB, nameB,
                null, null, 0, null, null,
                "INSUFFICIENT_DATA", "INSUFFICIENT_DATA", limitations,
                "Unavailable due to missing NAV history."
            );
        }

        // Deduplicate observations by effectiveDate keeping latest revision
        Map<LocalDate, BigDecimal> navMapA = new TreeMap<>();
        for (NavObservation o : obsA) {
            navMapA.put(o.getEffectiveDate(), o.getNavValue());
        }
        Map<LocalDate, BigDecimal> navMapB = new TreeMap<>();
        for (NavObservation o : obsB) {
            navMapB.put(o.getEffectiveDate(), o.getNavValue());
        }

        // Compute 1-day simple returns: R_t = (NAV_t / NAV_{t-1}) - 1
        Map<LocalDate, Double> returnsA = computeDailyReturns(navMapA);
        Map<LocalDate, Double> returnsB = computeDailyReturns(navMapB);

        // Intersect common dates
        Set<LocalDate> commonDates = new TreeSet<>(returnsA.keySet());
        commonDates.retainAll(returnsB.keySet());

        if (commonDates.size() < 30) {
            limitations.add("Fewer than 30 synchronous trading day observations available for correlation (found: " + commonDates.size() + ").");
            return new PairwiseCorrelationDto(
                schemeOptionIdA, nameA, schemeOptionIdB, nameB,
                null, null, commonDates.size(),
                commonDates.isEmpty() ? null : commonDates.iterator().next(),
                commonDates.isEmpty() ? null : ((TreeSet<LocalDate>) commonDates).last(),
                "INSUFFICIENT_DATA", "INSUFFICIENT_DATA", limitations,
                "Sample size too small for meaningful correlation analysis (minimum 30 paired days required)."
            );
        }

        List<Double> listA = new ArrayList<>(commonDates.size());
        List<Double> listB = new ArrayList<>(commonDates.size());
        for (LocalDate d : commonDates) {
            listA.add(returnsA.get(d));
            listB.add(returnsB.get(d));
        }

        double meanA = listA.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double meanB = listB.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        double cov = 0.0;
        double varA = 0.0;
        double varB = 0.0;
        int n = listA.size();
        for (int i = 0; i < n; i++) {
            double diffA = listA.get(i) - meanA;
            double diffB = listB.get(i) - meanB;
            cov += diffA * diffB;
            varA += diffA * diffA;
            varB += diffB * diffB;
        }

        BigDecimal corrBd;
        String formattedCorr;
        if (varA == 0.0 || varB == 0.0) {
            corrBd = BigDecimal.ZERO;
            formattedCorr = "0.0000";
            limitations.add("Zero return variance observed over common evaluation period.");
        } else {
            double r = cov / Math.sqrt(varA * varB);
            r = Math.max(-1.0, Math.min(1.0, r));
            corrBd = BigDecimal.valueOf(r).setScale(4, RoundingMode.HALF_UP);
            formattedCorr = (r >= 0 ? "+" : "") + corrBd.toPlainString();
        }

        LocalDate startD = ((TreeSet<LocalDate>) commonDates).first();
        LocalDate endD = ((TreeSet<LocalDate>) commonDates).last();

        String compStatus = "COMPARABLE";
        if (n < 250) {
            compStatus = "COMPARABLE_WITH_LIMITATIONS";
            limitations.add("Sample size (" + n + " trading days) is less than standard 1-year baseline (250 days).");
        }
        limitations.add("Candidate methodology — correlation measures empirical linear co-movement over the common observation window, not diversification or downside protection.");

        String interpretation;
        double rVal = corrBd.doubleValue();
        if (rVal >= 0.8) {
            interpretation = "High co-movement: daily returns move in the same direction across most market regimes.";
        } else if (rVal >= 0.5) {
            interpretation = "Moderate co-movement: substantial shared equity exposure with distinct active stock-selection variation.";
        } else if (rVal >= 0.2) {
            interpretation = "Low-to-moderate co-movement: meaningful daily return differentiation.";
        } else {
            interpretation = "Low or negative co-movement: independent return trajectories over the observation window.";
        }

        return new PairwiseCorrelationDto(
            schemeOptionIdA, nameA, schemeOptionIdB, nameB,
            corrBd, formattedCorr, n, startD, endD,
            "CALCULATED", compStatus, limitations, interpretation
        );
    }

    private Map<LocalDate, Double> computeDailyReturns(Map<LocalDate, BigDecimal> navMap) {
        Map<LocalDate, Double> returns = new TreeMap<>();
        BigDecimal prevNav = null;
        for (Map.Entry<LocalDate, BigDecimal> entry : navMap.entrySet()) {
            BigDecimal nav = entry.getValue();
            if (prevNav != null && prevNav.compareTo(BigDecimal.ZERO) > 0 && nav != null) {
                double r = (nav.doubleValue() / prevNav.doubleValue()) - 1.0;
                returns.put(entry.getKey(), r);
            }
            prevNav = nav;
        }
        return returns;
    }

    /**
     * Aggregates look-through security exposures across a multi-fund investor portfolio.
     * Fund weights map: schemeOptionId -> portfolio weight fraction (0..1) or percentage (0..100).
     */
    public PortfolioSecurityAggregationDto aggregateLookThroughExposures(Map<Long, BigDecimal> fundWeightsMap, OffsetDateTime knowledgeCutoff) {
        OffsetDateTime cutoff = knowledgeCutoff != null ? knowledgeCutoff : OffsetDateTime.now();

        if (fundWeightsMap == null || fundWeightsMap.isEmpty()) {
            return new PortfolioSecurityAggregationDto(
                0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0,
                Collections.emptyList(), "EMPTY", List.of("Investor portfolio is empty.")
            );
        }

        // Normalize fund weights so sum = 1.0
        BigDecimal sumWeightsInput = BigDecimal.ZERO;
        for (BigDecimal w : fundWeightsMap.values()) {
            if (w != null && w.compareTo(BigDecimal.ZERO) > 0) {
                sumWeightsInput = sumWeightsInput.add(w);
            }
        }

        if (sumWeightsInput.compareTo(BigDecimal.ZERO) <= 0) {
            return new PortfolioSecurityAggregationDto(
                fundWeightsMap.size(), 0, BigDecimal.ZERO, BigDecimal.ZERO, 0,
                Collections.emptyList(), "UNAVAILABLE", List.of("Fund weights sum to <= 0.")
            );
        }

        Map<Long, BigDecimal> normalizedFundWeights = new HashMap<>();
        for (Map.Entry<Long, BigDecimal> entry : fundWeightsMap.entrySet()) {
            if (entry.getValue() != null && entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal normW = entry.getValue().divide(sumWeightsInput, 6, RoundingMode.HALF_UP);
                normalizedFundWeights.put(entry.getKey(), normW);
            }
        }

        int totalConstituentFunds = fundWeightsMap.size();
        int valuedConstituentFunds = 0;

        Map<String, AggregatedSecurityBuilder> secBuilderMap = new HashMap<>();
        List<String> limitations = new ArrayList<>();

        for (Map.Entry<Long, BigDecimal> entry : normalizedFundWeights.entrySet()) {
            Long optId = entry.getKey();
            BigDecimal fundWeightInPort = entry.getValue(); // decimal 0..1

            Optional<SchemeOption> optOpt = schemeOptionRepository.findById(optId);
            String fundName = optOpt.isPresent() && optOpt.get().getPlan() != null && optOpt.get().getPlan().getScheme() != null
                ? optOpt.get().getPlan().getScheme().getName()
                : "Fund #" + optId;

            Optional<PortfolioSnapshot> snapOpt = snapshotRepository
                .findTopBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDesc(optId, cutoff);

            if (snapOpt.isEmpty()) {
                limitations.add("No holdings snapshot for constituent fund '" + fundName + "'. Look-through exposure excludes this fund.");
                continue;
            }

            PortfolioSnapshot snap = snapOpt.get();
            List<PortfolioHolding> holdings = holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(snap.getId());

            if (holdings.isEmpty()) {
                limitations.add("Constituent fund '" + fundName + "' snapshot contains 0 holdings.");
                continue;
            }

            valuedConstituentFunds++;

            for (PortfolioHolding h : holdings) {
                Security sec = h.getSecurity();
                String isin = securityIdentifierRepository.findTopBySecurityIdAndIdType(sec.getId(), "ISIN")
                    .map(SecurityIdentifier::getIdValue)
                    .orElse(null);

                String key = (isin != null && !isin.isBlank()) ? isin.toUpperCase(Locale.ROOT) : sec.getCanonicalName().toUpperCase(Locale.ROOT);

                BigDecimal holdingWeightInFund = h.getReportedWeight() != null ? h.getReportedWeight() : BigDecimal.ZERO; // decimal 0..1
                BigDecimal effectiveLookThroughW = fundWeightInPort.multiply(holdingWeightInFund);

                secBuilderMap.computeIfAbsent(key, k -> new AggregatedSecurityBuilder(sec.getCanonicalName(), isin, sec.getSector(), sec.getAssetClass()))
                    .addExposure(optId, fundName, fundWeightInPort, holdingWeightInFund, effectiveLookThroughW);
            }
        }

        List<AggregatedSecurityExposureDto> topExposures = new ArrayList<>();
        BigDecimal totalDisclosedCoverageAcc = BigDecimal.ZERO;

        for (AggregatedSecurityBuilder builder : secBuilderMap.values()) {
            AggregatedSecurityExposureDto dto = builder.build();
            topExposures.add(dto);
            totalDisclosedCoverageAcc = totalDisclosedCoverageAcc.add(dto.lookThroughWeightPct());
        }

        // Sort by look-through weight descending
        topExposures.sort((a, b) -> b.lookThroughWeightPct().compareTo(a.lookThroughWeightPct()));

        BigDecimal totalDisclosedCoveragePct = totalDisclosedCoverageAcc.setScale(2, RoundingMode.HALF_UP);
        String qualityState = (valuedConstituentFunds == totalConstituentFunds && limitations.isEmpty()) ? "VALID" : "PARTIAL_DISCLOSURE";

        return new PortfolioSecurityAggregationDto(
            totalConstituentFunds,
            valuedConstituentFunds,
            BigDecimal.ZERO, // Value depends on investor capital
            totalDisclosedCoveragePct,
            secBuilderMap.size(),
            topExposures,
            qualityState,
            limitations
        );
    }

    private static class AggregatedSecurityBuilder {
        private final String securityName;
        private final String isin;
        private final String sector;
        private final String assetClass;
        private BigDecimal totalLookThroughDecimal = BigDecimal.ZERO;
        private final List<ConstituentFundExposureDto> constituentExposures = new ArrayList<>();

        public AggregatedSecurityBuilder(String securityName, String isin, String sector, String assetClass) {
            this.securityName = securityName;
            this.isin = isin;
            this.sector = sector;
            this.assetClass = assetClass != null ? assetClass : "Equity";
        }

        public void addExposure(Long schemeOptionId, String fundName, BigDecimal fundWeightInPort, BigDecimal holdingWeightInFund, BigDecimal effectiveLookThroughW) {
            this.totalLookThroughDecimal = this.totalLookThroughDecimal.add(effectiveLookThroughW);

            BigDecimal fundWPortPct = fundWeightInPort.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal holdingWFundPct = holdingWeightInFund.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal effWPortPct = effectiveLookThroughW.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);

            constituentExposures.add(new ConstituentFundExposureDto(
                schemeOptionId, fundName, fundWPortPct, holdingWFundPct, effWPortPct
            ));
        }

        public AggregatedSecurityExposureDto build() {
            BigDecimal ltPct = totalLookThroughDecimal.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
            return new AggregatedSecurityExposureDto(
                securityName,
                isin,
                sector,
                assetClass,
                ltPct,
                ltPct.toPlainString() + "%",
                null,
                constituentExposures.size(),
                constituentExposures
            );
        }
    }

    /**
     * Lists available, analytically eligible portfolio holdings snapshots for a given scheme option up to knowledgeCutoff.
     * De-duplicates by portfolioDate selecting the latest revision available as of knowledgeCutoff.
     */
    public List<PortfolioSnapshotSummaryDto> getAvailableSnapshots(Long schemeOptionId, OffsetDateTime knowledgeCutoff) {
        OffsetDateTime cutoff = knowledgeCutoff != null ? knowledgeCutoff : OffsetDateTime.now();

        SchemeOption option = schemeOptionRepository.findById(schemeOptionId)
            .orElseThrow(() -> new IllegalArgumentException("Scheme option #" + schemeOptionId + " not found."));

        String fundName = option.getPlan() != null && option.getPlan().getScheme() != null
            ? option.getPlan().getScheme().getName()
            : "Option #" + schemeOptionId;

        List<PortfolioSnapshot> allSnaps = snapshotRepository
            .findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(schemeOptionId, cutoff);

        // De-duplicate by portfolioDate (keep highest revisionSeq per date)
        Map<LocalDate, PortfolioSnapshot> dedupMap = new LinkedHashMap<>();
        for (PortfolioSnapshot s : allSnaps) {
            dedupMap.putIfAbsent(s.getPortfolioDate(), s);
        }

        List<PortfolioSnapshotSummaryDto> summaries = new ArrayList<>();
        for (PortfolioSnapshot s : dedupMap.values()) {
            BigDecimal sumWDec = s.getSumReportedWeights() != null ? s.getSumReportedWeights() : BigDecimal.ZERO;
            BigDecimal sumWPct = sumWDec.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);

            SourceArtifact art = s.getSourceArtifact();
            Long artId = art != null ? art.getId() : null;
            String sha256 = art != null ? art.getSha256Hash() : null;
            String provider = art != null && art.getDataSource() != null ? art.getDataSource().getName() : "AMFI_DISCLOSURE";

            String qualityState = sumWDec.compareTo(new BigDecimal("0.90")) >= 0 ? "VALID" : "DATA_QUALITY_LIMITED";

            summaries.add(new PortfolioSnapshotSummaryDto(
                s.getId(),
                schemeOptionId,
                fundName,
                option.getAmfiCode(),
                s.getPortfolioDate(),
                s.getAvailabilityTime(),
                s.getRevisionSeq(),
                s.getLatestRevision(),
                s.getReportedHoldingsCount(),
                sumWPct,
                sumWPct.toPlainString() + "%",
                artId,
                sha256,
                provider,
                "ELIGIBLE_WITH_FULL_PROVENANCE",
                qualityState
            ));
        }

        return summaries;
    }

    /**
     * Calculates deterministic month-over-month portfolio drift between two snapshots of the same scheme option.
     * Enforces PIT cutoff, cross-scheme rejection, position-level weight deltas, concentration deltas, asset class drift,
     * and explicit status reporting when history is insufficient (<2 snapshots).
     */
    public PortfolioDriftResponseDto calculatePortfolioDrift(Long schemeOptionId, Long earlierSnapshotId, Long laterSnapshotId, OffsetDateTime knowledgeCutoff) {
        OffsetDateTime cutoff = knowledgeCutoff != null ? knowledgeCutoff : OffsetDateTime.now();

        SchemeOption option = schemeOptionRepository.findById(schemeOptionId)
            .orElseThrow(() -> new IllegalArgumentException("Scheme option #" + schemeOptionId + " not found."));

        String fundName = option.getPlan() != null && option.getPlan().getScheme() != null
            ? option.getPlan().getScheme().getName()
            : "Option #" + schemeOptionId;

        // Auto-resolution if snapshot IDs not specified
        PortfolioSnapshot earlierSnap = null;
        PortfolioSnapshot laterSnap = null;

        if (earlierSnapshotId == null && laterSnapshotId == null) {
            List<PortfolioSnapshotSummaryDto> available = getAvailableSnapshots(schemeOptionId, cutoff);
            if (available.size() < 2) {
                // Insufficient history state
                PortfolioSnapshotSummaryDto singleHeader = available.isEmpty() ? null : available.get(0);
                PortfolioDriftResponseDto.SnapshotDriftHeaderDto headerDto = singleHeader != null
                    ? new PortfolioDriftResponseDto.SnapshotDriftHeaderDto(
                        singleHeader.snapshotId(), singleHeader.portfolioDate(), singleHeader.availabilityTime(),
                        singleHeader.sourceArtifactId(), singleHeader.sourceArtifactSha256(), singleHeader.reportedHoldingsCount(),
                        singleHeader.sumReportedWeightsPct(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
                    : null;

                String reason = available.isEmpty()
                    ? "No holdings snapshots available for fund '" + fundName + "'."
                    : "Only 1 historical snapshot (" + singleHeader.portfolioDate() + ") is available in YUKIRA database for fund '" + fundName + "'. Multi-period portfolio drift analysis requires at least 2 distinct monthly disclosure snapshots.";

                return new PortfolioDriftResponseDto(
                    schemeOptionId, fundName, option.getAmfiCode(),
                    "INSUFFICIENT_HISTORY", reason,
                    headerDto, null,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null,
                    Collections.emptyList(), Collections.emptyList(),
                    false, List.of(reason),
                    "INSUFFICIENT_DATA", "DRIFT_COMPOSITION_V1"
                );
            }

            // Take top 2 available (0 is latest, 1 is earlier)
            laterSnap = snapshotRepository.findById(available.get(0).snapshotId()).orElse(null);
            earlierSnap = snapshotRepository.findById(available.get(1).snapshotId()).orElse(null);
        } else {
            if (earlierSnapshotId != null) earlierSnap = snapshotRepository.findById(earlierSnapshotId).orElse(null);
            if (laterSnapshotId != null) laterSnap = snapshotRepository.findById(laterSnapshotId).orElse(null);
        }

        if (earlierSnap == null || laterSnap == null) {
            return new PortfolioDriftResponseDto(
                schemeOptionId, fundName, option.getAmfiCode(),
                "UNAVAILABLE", "Specified snapshot IDs could not be resolved in database.",
                null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null,
                Collections.emptyList(), Collections.emptyList(),
                false, List.of("One or both requested snapshot IDs do not exist."),
                "UNAVAILABLE", "DRIFT_COMPOSITION_V1"
            );
        }

        // Cross-scheme validation
        if (!earlierSnap.getSchemeOption().getId().equals(schemeOptionId) || !laterSnap.getSchemeOption().getId().equals(schemeOptionId)) {
            return new PortfolioDriftResponseDto(
                schemeOptionId, fundName, option.getAmfiCode(),
                "INVALID_SELECTION", "Comparison snapshots belong to different scheme options. Cross-scheme portfolio drift is rejected.",
                null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null,
                Collections.emptyList(), Collections.emptyList(),
                false, List.of("Snapshots must belong to the exact same scheme option."),
                "INVALID_SELECTION", "DRIFT_COMPOSITION_V1"
            );
        }

        // Enforce PIT availability cutoff
        if (earlierSnap.getAvailabilityTime().isAfter(cutoff) || laterSnap.getAvailabilityTime().isAfter(cutoff)) {
            return new PortfolioDriftResponseDto(
                schemeOptionId, fundName, option.getAmfiCode(),
                "UNAVAILABLE", "Requested historical snapshots exceed the specified knowledge cutoff timestamp (" + cutoff + ").",
                null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null,
                Collections.emptyList(), Collections.emptyList(),
                false, List.of("Snapshot availability timestamp exceeds knowledge cutoff."),
                "UNAVAILABLE", "DRIFT_COMPOSITION_V1"
            );
        }

        // Ensure chronological order
        if (earlierSnap.getPortfolioDate().isAfter(laterSnap.getPortfolioDate())) {
            PortfolioSnapshot tmp = earlierSnap;
            earlierSnap = laterSnap;
            laterSnap = tmp;
        }

        if (earlierSnap.getId().equals(laterSnap.getId()) || earlierSnap.getPortfolioDate().equals(laterSnap.getPortfolioDate())) {
            return new PortfolioDriftResponseDto(
                schemeOptionId, fundName, option.getAmfiCode(),
                "INVALID_SELECTION", "Cannot calculate portfolio drift between identical reporting dates (" + earlierSnap.getPortfolioDate() + ").",
                null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null,
                Collections.emptyList(), Collections.emptyList(),
                false, List.of("Earlier and later comparison snapshots must have distinct portfolio dates."),
                "INVALID_SELECTION", "DRIFT_COMPOSITION_V1"
            );
        }

        // Compute concentration metrics for earlier and later snapshots
        FundConcentrationDto concEarlier = calculateFundConcentrationForSnapshot(earlierSnap);
        FundConcentrationDto concLater = calculateFundConcentrationForSnapshot(laterSnap);

        PortfolioDriftResponseDto.SnapshotDriftHeaderDto earlierHeader = new PortfolioDriftResponseDto.SnapshotDriftHeaderDto(
            earlierSnap.getId(), earlierSnap.getPortfolioDate(), earlierSnap.getAvailabilityTime(),
            earlierSnap.getSourceArtifact() != null ? earlierSnap.getSourceArtifact().getId() : null,
            earlierSnap.getSourceArtifact() != null ? earlierSnap.getSourceArtifact().getSha256Hash() : null,
            concEarlier.positionCount(), concEarlier.sumDisclosedWeightsPct(),
            concEarlier.top5WeightPct(), concEarlier.top10WeightPct(), concEarlier.hhi()
        );

        PortfolioDriftResponseDto.SnapshotDriftHeaderDto laterHeader = new PortfolioDriftResponseDto.SnapshotDriftHeaderDto(
            laterSnap.getId(), laterSnap.getPortfolioDate(), laterSnap.getAvailabilityTime(),
            laterSnap.getSourceArtifact() != null ? laterSnap.getSourceArtifact().getId() : null,
            laterSnap.getSourceArtifact() != null ? laterSnap.getSourceArtifact().getSha256Hash() : null,
            concLater.positionCount(), concLater.sumDisclosedWeightsPct(),
            concLater.top5WeightPct(), concLater.top10WeightPct(), concLater.hhi()
        );

        BigDecimal top5Delta = concLater.top5WeightPct().subtract(concEarlier.top5WeightPct()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal top10Delta = concLater.top10WeightPct().subtract(concEarlier.top10WeightPct()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal hhiDelta = concLater.hhi().subtract(concEarlier.hhi()).setScale(2, RoundingMode.HALF_UP);


        // Position-level holdings drift
        List<PortfolioHolding> holdingsEarlier = holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(earlierSnap.getId());
        List<PortfolioHolding> holdingsLater = holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(laterSnap.getId());

        Map<String, HoldingItemRef> mapEarlier = buildHoldingMap(holdingsEarlier);
        Map<String, HoldingItemRef> mapLater = buildHoldingMap(holdingsLater);

        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(mapLater.keySet());
        allKeys.addAll(mapEarlier.keySet());

        List<PortfolioDriftResponseDto.HoldingDriftItemDto> driftItems = new ArrayList<>();

        for (String key : allKeys) {
            HoldingItemRef itemE = mapEarlier.get(key);
            HoldingItemRef itemL = mapLater.get(key);

            String secName = itemL != null ? itemL.name : itemE.name;
            String isin = itemL != null ? itemL.isin : itemE.isin;
            String sector = itemL != null ? itemL.sector : itemE.sector;
            String assetClass = itemL != null ? itemL.assetClass : itemE.assetClass;

            BigDecimal wE = itemE != null ? itemE.weightPct : null;
            BigDecimal wL = itemL != null ? itemL.weightPct : null;

            String changeType;
            BigDecimal deltaPct;
            String description;

            if (itemE != null && itemL != null) {
                deltaPct = wL.subtract(wE).setScale(2, RoundingMode.HALF_UP);
                if (deltaPct.compareTo(BigDecimal.ZERO) > 0) {
                    changeType = "WEIGHT_INCREASED";
                    description = "Disclosed weight increased by +" + deltaPct.toPlainString() + "%";
                } else if (deltaPct.compareTo(BigDecimal.ZERO) < 0) {
                    changeType = "WEIGHT_DECREASED";
                    description = "Disclosed weight decreased by " + deltaPct.toPlainString() + "%";
                } else {
                    changeType = "UNCHANGED";
                    description = "Disclosed weight unchanged (" + wL.toPlainString() + "%)";
                }
            } else if (itemL != null) {
                changeType = "NEWLY_REPORTED";
                deltaPct = wL;
                description = "Newly observed in later snapshot (" + wL.toPlainString() + "%)";
            } else {
                changeType = "NO_LONGER_REPORTED";
                deltaPct = wE.negate();
                description = "Not reported in later snapshot (was " + wE.toPlainString() + "% in earlier snapshot)";
            }

            driftItems.add(new PortfolioDriftResponseDto.HoldingDriftItemDto(
                secName, isin, sector, assetClass, changeType,
                wE, wE != null ? wE.toPlainString() + "%" : null,
                wL, wL != null ? wL.toPlainString() + "%" : null,
                deltaPct, (deltaPct.compareTo(BigDecimal.ZERO) > 0 ? "+" : "") + deltaPct.toPlainString() + "%",
                description
            ));
        }

        // Sort drift items by absolute weightDeltaPct descending
        driftItems.sort((a, b) -> b.weightDeltaPct().abs().compareTo(a.weightDeltaPct().abs()));

        // Compute mutually exclusive drift category counts
        int newlyCount = (int) driftItems.stream().filter(i -> "NEWLY_REPORTED".equals(i.changeType())).count();
        int noLongerCount = (int) driftItems.stream().filter(i -> "NO_LONGER_REPORTED".equals(i.changeType())).count();
        int increasedCount = (int) driftItems.stream().filter(i -> "WEIGHT_INCREASED".equals(i.changeType())).count();
        int decreasedCount = (int) driftItems.stream().filter(i -> "WEIGHT_DECREASED".equals(i.changeType())).count();
        int unchangedCount = (int) driftItems.stream().filter(i -> "UNCHANGED".equals(i.changeType())).count();
        int unclassifiedCount = (int) driftItems.stream().filter(i -> "UNCLASSIFIED".equals(i.changeType())).count();
        int totalCompared = driftItems.size();

        // Enforce reconciliation invariant
        int categorySum = newlyCount + noLongerCount + increasedCount + decreasedCount + unchangedCount + unclassifiedCount;
        if (totalCompared != categorySum) {
            log.warn("Drift classification invariant mismatch: totalCompared={} != categorySum={}", totalCompared, categorySum);
        }

        PortfolioDriftResponseDto.DriftClassificationSummaryDto classificationSummary = new PortfolioDriftResponseDto.DriftClassificationSummaryDto(
            totalCompared, newlyCount, noLongerCount, increasedCount, decreasedCount, unchangedCount, unclassifiedCount
        );

        // Asset Class breakdown drift
        List<PortfolioDriftResponseDto.AssetClassDriftDto> assetClassDriftList = calculateAssetClassDrift(holdingsEarlier, holdingsLater);

        // Sector breakdown drift & coverage
        SectorDriftResult sectorDriftResult = calculateSectorDrift(holdingsEarlier, holdingsLater);

        // Ranked movement slices for prominent investor display
        List<PortfolioDriftResponseDto.HoldingDriftItemDto> topIncreases = driftItems.stream()
            .filter(i -> i.weightDeltaPct().compareTo(BigDecimal.ZERO) > 0)
            .sorted((a, b) -> b.weightDeltaPct().compareTo(a.weightDeltaPct()))
            .limit(10)
            .toList();

        List<PortfolioDriftResponseDto.HoldingDriftItemDto> topDecreases = driftItems.stream()
            .filter(i -> i.weightDeltaPct().compareTo(BigDecimal.ZERO) < 0)
            .sorted((a, b) -> a.weightDeltaPct().compareTo(b.weightDeltaPct())) // most negative delta first
            .limit(10)
            .toList();

        List<PortfolioDriftResponseDto.HoldingDriftItemDto> additions = driftItems.stream()
            .filter(i -> "NEWLY_REPORTED".equals(i.changeType()))
            .sorted((a, b) -> b.laterWeightPct().compareTo(a.laterWeightPct()))
            .toList();

        List<PortfolioDriftResponseDto.HoldingDriftItemDto> exits = driftItems.stream()
            .filter(i -> "NO_LONGER_REPORTED".equals(i.changeType()))
            .sorted((a, b) -> b.earlierWeightPct().compareTo(a.earlierWeightPct()))
            .toList();

        List<String> notes = new ArrayList<>();
        notes.add("Drift comparison calculated between " + earlierSnap.getPortfolioDate() + " and " + laterSnap.getPortfolioDate() + ".");
        notes.add("Position-level changes reflect reported disclosure weight deltas, not verified order execution or trade flows.");
        if ("PARTIAL_COVERAGE".equals(sectorDriftResult.coverage().coverageStatus())) {
            notes.addAll(sectorDriftResult.coverage().coverageLimitationNotes());
        }

        // Layer 6: Grounded Investigation Prompts
        List<String> prompts = new ArrayList<>();
        if (!topIncreases.isEmpty()) {
            var inc = topIncreases.get(0);
            prompts.add("Evaluate whether the largest position increase (" + inc.securityName() + ", " + inc.weightDeltaPctFormatted() + ") was driven by active allocation or market price appreciation.");
        }
        if (!additions.isEmpty()) {
            prompts.add("Review newly reported holdings (" + additions.size() + " positions, led by " + additions.get(0).securityName() + " at " + additions.get(0).laterWeightPctFormatted() + ") to evaluate strategic intent.");
        }
        if (!exits.isEmpty()) {
            prompts.add("Verify whether positions no longer reported (" + exits.size() + " positions, led by " + exits.get(0).securityName() + " previously at " + exits.get(0).earlierWeightPctFormatted() + ") were fully divested or fell below disclosure cutoffs.");
        }
        if (!sectorDriftResult.sectorDriftList().isEmpty()) {
            var sec = sectorDriftResult.sectorDriftList().get(0);
            prompts.add("Inspect sector allocation shift: largest movement observed in " + sec.sector() + " (" + sec.weightDeltaPctFormatted() + ").");
        }
        prompts.add("Examine portfolio unclassified/cash residual exposure (" + earlierHeader.sumWeightsPct() + "% -> " + laterHeader.sumWeightsPct() + "% total weight) to assess cash drag and liquidity buffer changes.");

        return new PortfolioDriftResponseDto(
            schemeOptionId, fundName, option.getAmfiCode(),
            "COMPARABLE", "Month-over-month portfolio drift successfully calculated between " + earlierSnap.getPortfolioDate() + " and " + laterSnap.getPortfolioDate() + ".",
            earlierHeader, laterHeader,
            top5Delta, top10Delta, hhiDelta,
            classificationSummary,
            assetClassDriftList,
            sectorDriftResult.sectorDriftList(),
            sectorDriftResult.coverage(),
            driftItems,
            topIncreases,
            topDecreases,
            additions,
            exits,
            true, notes,
            "VALID", "DRIFT_COMPOSITION_V1",
            prompts
        );
    }

    private FundConcentrationDto calculateFundConcentrationForSnapshot(PortfolioSnapshot snap) {
        List<PortfolioHolding> holdings = holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(snap.getId());
        BigDecimal sumWeightsDecimal = BigDecimal.ZERO;
        BigDecimal top5WeightDecimal = BigDecimal.ZERO;
        BigDecimal top10WeightDecimal = BigDecimal.ZERO;
        BigDecimal hhiAcc = BigDecimal.ZERO;

        int rank = 1;
        for (PortfolioHolding h : holdings) {
            BigDecimal wDec = h.getReportedWeight() != null ? h.getReportedWeight() : BigDecimal.ZERO;
            sumWeightsDecimal = sumWeightsDecimal.add(wDec);
            if (rank <= 5) top5WeightDecimal = top5WeightDecimal.add(wDec);
            if (rank <= 10) top10WeightDecimal = top10WeightDecimal.add(wDec);
            BigDecimal pctVal = wDec.multiply(BigDecimal.valueOf(100));
            hhiAcc = hhiAcc.add(pctVal.multiply(pctVal));
            rank++;
        }

        BigDecimal sumWeightsPct = sumWeightsDecimal.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal top5Pct = top5WeightDecimal.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal top10Pct = top10WeightDecimal.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal hhiFinal = hhiAcc.setScale(2, RoundingMode.HALF_UP);

        return new FundConcentrationDto(
            snap.getSchemeOption().getId(), snap.getSchemeOption().getPlan().getScheme().getName(),
            snap.getPortfolioDate(), holdings.size(),
            sumWeightsPct, sumWeightsPct.toPlainString() + "%",
            top5Pct, top5Pct.toPlainString() + "%",
            top10Pct, top10Pct.toPlainString() + "%",
            hhiFinal, "MODERATELY_CONCENTRATED", Collections.emptyList(), "VALID", Collections.emptyList()
        );
    }

    private record HoldingItemRef(String name, String isin, String sector, String assetClass, BigDecimal weightPct) {}

    private Map<String, HoldingItemRef> buildHoldingMap(List<PortfolioHolding> holdings) {
        Map<String, HoldingItemRef> map = new LinkedHashMap<>();
        for (PortfolioHolding h : holdings) {
            Security sec = h.getSecurity();
            String isin = securityIdentifierRepository.findTopBySecurityIdAndIdType(sec.getId(), "ISIN")
                .map(SecurityIdentifier::getIdValue)
                .orElse(null);
            String key = (isin != null && !isin.isBlank()) ? isin.toUpperCase(Locale.ROOT) : sec.getCanonicalName().toUpperCase(Locale.ROOT);
            BigDecimal wDec = h.getReportedWeight() != null ? h.getReportedWeight() : BigDecimal.ZERO;
            BigDecimal wPct = wDec.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);

            map.compute(key, (k, existing) -> {
                if (existing == null) {
                    return new HoldingItemRef(sec.getCanonicalName(), isin, sec.getSector(), sec.getAssetClass(), wPct);
                } else {
                    BigDecimal combinedW = existing.weightPct().add(wPct);
                    return new HoldingItemRef(existing.name(), existing.isin(), existing.sector(), existing.assetClass(), combinedW);
                }
            });
        }
        return map;
    }

    private record SectorDriftResult(
        List<PortfolioDriftResponseDto.SectorDriftDto> sectorDriftList,
        PortfolioDriftResponseDto.SectorDriftCoverageDto coverage
    ) {}

    private SectorDriftResult calculateSectorDrift(List<PortfolioHolding> earlier, List<PortfolioHolding> later) {
        Map<String, BigDecimal> earlierMap = groupWeightsBySector(earlier);
        Map<String, BigDecimal> laterMap = groupWeightsBySector(later);

        Set<String> sectors = new LinkedHashSet<>();
        sectors.addAll(laterMap.keySet());
        sectors.addAll(earlierMap.keySet());

        List<PortfolioDriftResponseDto.SectorDriftDto> result = new ArrayList<>();
        BigDecimal totalClassifiedEarlier = BigDecimal.ZERO;
        BigDecimal totalClassifiedLater = BigDecimal.ZERO;
        BigDecimal unclassifiedEarlier = BigDecimal.ZERO;
        BigDecimal unclassifiedLater = BigDecimal.ZERO;

        for (String sec : sectors) {
            BigDecimal wE = earlierMap.getOrDefault(sec, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal wL = laterMap.getOrDefault(sec, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal delta = wL.subtract(wE).setScale(2, RoundingMode.HALF_UP);

            if (isUnclassifiedSector(sec)) {
                unclassifiedEarlier = unclassifiedEarlier.add(wE);
                unclassifiedLater = unclassifiedLater.add(wL);
            } else {
                totalClassifiedEarlier = totalClassifiedEarlier.add(wE);
                totalClassifiedLater = totalClassifiedLater.add(wL);
            }

            result.add(new PortfolioDriftResponseDto.SectorDriftDto(
                sec, wE, wE.toPlainString() + "%",
                wL, wL.toPlainString() + "%",
                delta, (delta.compareTo(BigDecimal.ZERO) > 0 ? "+" : "") + delta.toPlainString() + "%"
            ));
        }

        // Sort sectors by absolute weightDeltaPct descending
        result.sort((a, b) -> b.weightDeltaPct().abs().compareTo(a.weightDeltaPct().abs()));

        List<String> limitationNotes = new ArrayList<>();
        String coverageStatus = "ADEQUATE_COVERAGE";

        if (unclassifiedEarlier.compareTo(new BigDecimal("10.00")) > 0 || unclassifiedLater.compareTo(new BigDecimal("10.00")) > 0) {
            coverageStatus = "PARTIAL_COVERAGE";
            limitationNotes.add("Unclassified or cash exposure exceeds 10% in at least one comparison snapshot (" +
                "Earlier: " + unclassifiedEarlier.toPlainString() + "%, Later: " + unclassifiedLater.toPlainString() + "%).");
        }
        if (totalClassifiedEarlier.compareTo(new BigDecimal("80.00")) < 0 || totalClassifiedLater.compareTo(new BigDecimal("80.00")) < 0) {
            coverageStatus = "PARTIAL_COVERAGE";
            limitationNotes.add("Classified sector coverage is below 80% of disclosed portfolio assets.");
        }
        limitationNotes.add("Sector classification is based strictly on reported AMC disclosure tags without synthetic normalization.");

        PortfolioDriftResponseDto.SectorDriftCoverageDto coverageDto = new PortfolioDriftResponseDto.SectorDriftCoverageDto(
            totalClassifiedEarlier, totalClassifiedEarlier.toPlainString() + "%",
            totalClassifiedLater, totalClassifiedLater.toPlainString() + "%",
            unclassifiedEarlier, unclassifiedEarlier.toPlainString() + "%",
            unclassifiedLater, unclassifiedLater.toPlainString() + "%",
            coverageStatus,
            limitationNotes
        );

        return new SectorDriftResult(result, coverageDto);
    }

    private boolean isUnclassifiedSector(String sector) {
        if (sector == null || sector.isBlank()) return true;
        String s = sector.trim().toLowerCase(Locale.ROOT);
        return s.contains("unclassified") || s.contains("unmapped") || s.equals("other") || s.contains("cash") || s.contains("treps");
    }

    private Map<String, BigDecimal> groupWeightsBySector(List<PortfolioHolding> holdings) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (PortfolioHolding h : holdings) {
            Security sec = h.getSecurity();
            String sectorName = (sec != null && sec.getSector() != null && !sec.getSector().isBlank())
                ? sec.getSector().trim()
                : "Unclassified / Cash";
            BigDecimal wDec = h.getReportedWeight() != null ? h.getReportedWeight() : BigDecimal.ZERO;
            BigDecimal wPct = wDec.multiply(BigDecimal.valueOf(100));
            map.put(sectorName, map.getOrDefault(sectorName, BigDecimal.ZERO).add(wPct));
        }
        return map;
    }

    private List<PortfolioDriftResponseDto.AssetClassDriftDto> calculateAssetClassDrift(List<PortfolioHolding> earlier, List<PortfolioHolding> later) {
        Map<String, BigDecimal> earlierMap = groupWeightsByAssetClass(earlier);
        Map<String, BigDecimal> laterMap = groupWeightsByAssetClass(later);

        Set<String> assetClasses = new LinkedHashSet<>();
        assetClasses.addAll(laterMap.keySet());
        assetClasses.addAll(earlierMap.keySet());

        List<PortfolioDriftResponseDto.AssetClassDriftDto> result = new ArrayList<>();
        for (String ac : assetClasses) {
            BigDecimal wE = earlierMap.getOrDefault(ac, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal wL = laterMap.getOrDefault(ac, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal delta = wL.subtract(wE).setScale(2, RoundingMode.HALF_UP);

            result.add(new PortfolioDriftResponseDto.AssetClassDriftDto(
                ac, wE, wE.toPlainString() + "%",
                wL, wL.toPlainString() + "%",
                delta, (delta.compareTo(BigDecimal.ZERO) > 0 ? "+" : "") + delta.toPlainString() + "%"
            ));
        }
        return result;
    }

    private Map<String, BigDecimal> groupWeightsByAssetClass(List<PortfolioHolding> holdings) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (PortfolioHolding h : holdings) {
            Security sec = h.getSecurity();
            String ac = sec != null && sec.getAssetClass() != null ? sec.getAssetClass() : "Equity";
            BigDecimal wDec = h.getReportedWeight() != null ? h.getReportedWeight() : BigDecimal.ZERO;
            BigDecimal wPct = wDec.multiply(BigDecimal.valueOf(100));
            map.put(ac, map.getOrDefault(ac, BigDecimal.ZERO).add(wPct));
        }
        return map;
    }
}

