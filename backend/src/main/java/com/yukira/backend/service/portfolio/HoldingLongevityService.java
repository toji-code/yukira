package com.yukira.backend.service.portfolio;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.longevity.*;
import com.yukira.backend.repository.*;
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
 * Service orchestrating Point-in-Time Holding Longevity, Portfolio Turnover & Position Persistence Analysis.
 *
 * Epistemic & Methodological Guardrails (AGENTS.md & Phase 2H/2K):
 * 1. Descriptive Portfolio Analysis:
 *    Describes empirical holding tenure and presence across authentic Point-in-Time portfolio disclosures.
 *    Never generates manager-skill scores, conviction scores, or predictive performance forecasts.
 * 2. Point-in-Time (PIT) Look-Ahead Bias Prevention:
 *    portfolioDate <= asOfDate AND availabilityTime <= knowledgeCutoff.
 * 3. Scope Shift Awareness:
 *    When a security disappears from a partial top-holdings disclosure, its status is strictly classified
 *    as NOT_OBSERVED_IN_PARTIAL_DISCLOSURE, never as an actual portfolio liquidation or exit.
 * 4. Temporal Gap Identification:
 *    When snapshots are separated by a material gap (> 45 days), the transition is gated with
 *    NON_COMPARABLE_TEMPORAL_GAP rather than assuming continuous holding throughout the unobserved period.
 * 5. Strict Turnover Gating:
 *    Regulatory / economic portfolio turnover is strictly gated with TURNOVER_UNAVAILABLE_TRANSACTION_DATA_MISSING
 *    when transaction logs, purchases, sales, and average net assets are absent.
 *    Descriptive composition changes (1/2 * sum(|w_t - w_{t-1}|)) are explicitly distinguished from turnover.
 * 6. Multi-Lot Merging:
 *    Multiple holding lots for identical securities or cash positions are deterministically merged by summing weights.
 */
@Service
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class HoldingLongevityService {

    private static final Logger log = LoggerFactory.getLogger(HoldingLongevityService.class);
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal FULL_DISCLOSURE_THRESHOLD = new BigDecimal("90.00");
    private static final long MAX_COMPARABLE_INTERVAL_DAYS = 45;
    private static final String METHODOLOGY_VERSION = "YUKIRA-LONGEVITY-1.0 (Phase 2K Deterministic)";

    private final SchemeOptionRepository schemeOptionRepository;
    private final PortfolioSnapshotRepository portfolioSnapshotRepository;
    private final PortfolioHoldingRepository portfolioHoldingRepository;
    private final SecurityIdentifierRepository securityIdentifierRepository;

    private record SnapshotHoldingRecord(
        Long securityId,
        String isin,
        String canonicalName,
        BigDecimal reportedWeightPct,
        Integer holdingRank
    ) {}

    public HoldingLongevityService(
        SchemeOptionRepository schemeOptionRepository,
        PortfolioSnapshotRepository portfolioSnapshotRepository,
        PortfolioHoldingRepository portfolioHoldingRepository,
        SecurityIdentifierRepository securityIdentifierRepository
    ) {
        this.schemeOptionRepository = schemeOptionRepository;
        this.portfolioSnapshotRepository = portfolioSnapshotRepository;
        this.portfolioHoldingRepository = portfolioHoldingRepository;
        this.securityIdentifierRepository = securityIdentifierRepository;
    }

    public HoldingLongevityResponseDto getHoldingLongevityTrajectory(
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

        // 1. Retrieve all snapshots under PIT cutoff
        List<PortfolioSnapshot> allSnapshots = portfolioSnapshotRepository
            .findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(schemeOptionId, cutoff);

        // Deduplicate snapshots by date (taking highest revision under cutoff)
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

        List<PortfolioSnapshot> filteredSnapshots = new ArrayList<>(dedupMap.values());
        filteredSnapshots.sort(Comparator.comparing(PortfolioSnapshot::getPortfolioDate));

        String runId = UUID.randomUUID().toString();

        if (filteredSnapshots.isEmpty()) {
            return new HoldingLongevityResponseDto(
                schemeOptionId, fundName, planType, optionType, amfiCode, schemeCode,
                "INSUFFICIENT_DATA",
                "No disclosed portfolio snapshots found under Point-in-Time knowledge cutoff " + cutoff + ".",
                METHODOLOGY_VERSION, runId, cutoff.toString(),
                allSnapshots.size(), 0, skippedPeriods,
                null, null,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                new TurnoverEvaluationDto(
                    "TURNOVER_UNAVAILABLE_TRANSACTION_DATA_MISSING",
                    "No snapshots available to evaluate portfolio turnover or composition changes.",
                    false, null, "1/2 * SUM(|w_t - w_{t-1}|)", BigDecimal.ZERO, BigDecimal.ZERO, 0, 0,
                    List.of("Zero fake data: Analysis requires authentic point-in-time portfolio holdings.")
                ),
                List.of("Disclosed portfolio holdings required under requested Point-in-Time cutoffs"),
                List.of("Are monthly portfolio holding disclosures published by the AMC for this period?"),
                List.of("Descriptive portfolio analysis requires authentic historical snapshots.")
            );
        }

        int totalEligibleSnapshots = filteredSnapshots.size();
        LocalDate windowStart = filteredSnapshots.get(0).getPortfolioDate();
        LocalDate windowEnd = filteredSnapshots.get(totalEligibleSnapshots - 1).getPortfolioDate();

        // 2. Process each snapshot and load merged holdings
        List<HoldingSnapshotSummaryDto> snapshotSummaries = new ArrayList<>();
        List<Map<String, SnapshotHoldingRecord>> snapshotHoldingMaps = new ArrayList<>();
        List<String> snapshotDisclosureScopes = new ArrayList<>();

        for (PortfolioSnapshot ps : filteredSnapshots) {
            List<PortfolioHolding> holdings = portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(ps.getId());

            // Resolve ISINs for security IDs present in this snapshot
            List<Long> secIds = holdings.stream()
                .map(PortfolioHolding::getSecurity)
                .filter(Objects::nonNull)
                .map(Security::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

            Map<Long, String> secIdToIsinMap = new HashMap<>();
            if (!secIds.isEmpty()) {
                List<SecurityIdentifier> isinIdentifiers = securityIdentifierRepository.findBySecurityIdInAndIdType(secIds, "ISIN");
                for (SecurityIdentifier si : isinIdentifiers) {
                    secIdToIsinMap.putIfAbsent(si.getSecurity().getId(), si.getIdValue());
                }
            }

            BigDecimal sumReportedW = BigDecimal.ZERO;
            Map<String, SnapshotHoldingRecord> mergedHoldingMap = new LinkedHashMap<>();

            for (PortfolioHolding h : holdings) {
                BigDecimal wPct = (h.getReportedWeight() != null)
                    ? h.getReportedWeight().multiply(BigDecimal.valueOf(100), MC).setScale(4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
                sumReportedW = sumReportedW.add(wPct, MC);

                Security sec = h.getSecurity();
                Long secId = sec != null ? sec.getId() : null;
                String isin = secId != null ? secIdToIsinMap.get(secId) : null;
                String secName = (sec != null && sec.getCanonicalName() != null) ? sec.getCanonicalName() : "Unknown Holding";
                int rank = h.getHoldingRank() != null ? h.getHoldingRank() : 999;

                String uniqueKey = resolveUniqueSecurityKey(secId, isin, secName);

                mergedHoldingMap.merge(uniqueKey,
                    new SnapshotHoldingRecord(secId, isin, secName, wPct, rank),
                    (existing, incoming) -> new SnapshotHoldingRecord(
                        existing.securityId() != null ? existing.securityId() : incoming.securityId(),
                        existing.isin() != null ? existing.isin() : incoming.isin(),
                        existing.canonicalName(),
                        existing.reportedWeightPct().add(incoming.reportedWeightPct(), MC).setScale(4, RoundingMode.HALF_UP),
                        Math.min(existing.holdingRank(), incoming.holdingRank())
                    )
                );
            }

            BigDecimal scaledSumWeight = sumReportedW.setScale(2, RoundingMode.HALF_UP);
            BigDecimal effectiveSumWeight = (ps.getSumReportedWeights() != null && ps.getSumReportedWeights().multiply(BigDecimal.valueOf(100), MC).compareTo(scaledSumWeight) > 0)
                ? ps.getSumReportedWeights().multiply(BigDecimal.valueOf(100), MC).setScale(2, RoundingMode.HALF_UP)
                : scaledSumWeight;

            String scope = effectiveSumWeight.compareTo(FULL_DISCLOSURE_THRESHOLD) >= 0
                ? "FULL_PORTFOLIO_DISCLOSURE"
                : "PARTIAL_TOP_HOLDINGS";

            snapshotDisclosureScopes.add(scope);
            snapshotHoldingMaps.add(mergedHoldingMap);

            SourceArtifact sa = ps.getSourceArtifact();
            String artifactSha = sa != null ? sa.getSha256Hash() : null;
            String artifactUri = sa != null ? sa.getStorageUri() : "SEBI_MONTHLY_DISCLOSURE";

            snapshotSummaries.add(new HoldingSnapshotSummaryDto(
                ps.getId(),
                ps.getPortfolioDate(),
                ps.getAvailabilityTime(),
                mergedHoldingMap.size(),
                effectiveSumWeight,
                scope,
                artifactSha,
                artifactUri
            ));
        }

        // 3. Track each security across chronological snapshots
        Set<String> allSecurityKeys = new LinkedHashSet<>();
        for (Map<String, SnapshotHoldingRecord> map : snapshotHoldingMaps) {
            allSecurityKeys.addAll(map.keySet());
        }

        List<PositionLongevityItemDto> allHoldingLongevityItems = new ArrayList<>();
        Map<String, SnapshotHoldingRecord> latestHoldingMap = snapshotHoldingMaps.get(totalEligibleSnapshots - 1);
        String latestDisclosureScope = snapshotDisclosureScopes.get(totalEligibleSnapshots - 1);

        for (String secKey : allSecurityKeys) {
            List<PositionWeightPointDto> trajectory = new ArrayList<>();
            LocalDate firstObsDate = null;
            LocalDate lastObsDate = null;
            int firstIdx = -1;
            int lastIdx = -1;
            int appearCount = 0;
            BigDecimal sumWeights = BigDecimal.ZERO;
            BigDecimal minWeight = null;
            BigDecimal maxWeight = null;
            List<BigDecimal> weightsList = new ArrayList<>();

            Long resolvedSecId = null;
            String resolvedIsin = null;
            String resolvedName = null;

            for (int i = 0; i < totalEligibleSnapshots; i++) {
                PortfolioSnapshot ps = filteredSnapshots.get(i);
                LocalDate pDate = ps.getPortfolioDate();
                String scope = snapshotDisclosureScopes.get(i);
                Map<String, SnapshotHoldingRecord> map = snapshotHoldingMaps.get(i);
                SnapshotHoldingRecord rec = map.get(secKey);

                if (rec != null && rec.reportedWeightPct().compareTo(BigDecimal.ZERO) > 0) {
                    if (resolvedSecId == null) resolvedSecId = rec.securityId();
                    if (resolvedIsin == null) resolvedIsin = rec.isin();
                    if (resolvedName == null) resolvedName = rec.canonicalName();

                    if (firstObsDate == null) {
                        firstObsDate = pDate;
                        firstIdx = i;
                    }
                    lastObsDate = pDate;
                    lastIdx = i;
                    appearCount++;

                    BigDecimal w = rec.reportedWeightPct();
                    sumWeights = sumWeights.add(w, MC);
                    weightsList.add(w);

                    if (minWeight == null || w.compareTo(minWeight) < 0) minWeight = w;
                    if (maxWeight == null || w.compareTo(maxWeight) > 0) maxWeight = w;

                    trajectory.add(new PositionWeightPointDto(pDate, w.setScale(2, RoundingMode.HALF_UP), rec.holdingRank(), true, scope));
                } else {
                    trajectory.add(new PositionWeightPointDto(pDate, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), null, false, scope));
                }
            }

            int spanObsCount = (lastIdx >= firstIdx && firstIdx >= 0) ? (lastIdx - firstIdx + 1) : 0;
            BigDecimal appearRatioPct = BigDecimal.valueOf((appearCount * 100.0) / totalEligibleSnapshots).setScale(2, RoundingMode.HALF_UP);
            long tenureDays = (firstObsDate != null && lastObsDate != null) ? ChronoUnit.DAYS.between(firstObsDate, lastObsDate) : 0L;
            long tenureMonths = (firstObsDate != null && lastObsDate != null) ? ChronoUnit.MONTHS.between(firstObsDate, lastObsDate) : 0L;

            String tenureBucket = resolveTenureBucket(tenureDays);

            boolean isPresentInLatest = (lastIdx == totalEligibleSnapshots - 1);
            SnapshotHoldingRecord latestRec = latestHoldingMap.get(secKey);
            BigDecimal latestW = (latestRec != null) ? latestRec.reportedWeightPct().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            Integer latestRank = (latestRec != null) ? latestRec.holdingRank() : null;
            boolean isTop10 = (latestRank != null && latestRank <= 10 && latestW.compareTo(BigDecimal.ZERO) > 0);

            BigDecimal avgWeight = appearCount > 0
                ? sumWeights.divide(BigDecimal.valueOf(appearCount), MC).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            BigDecimal scaledMinW = minWeight != null ? minWeight.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BigDecimal scaledMaxW = maxWeight != null ? maxWeight.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BigDecimal weightRange = scaledMaxW.subtract(scaledMinW, MC).setScale(2, RoundingMode.HALF_UP);

            // Calculate standard deviation of weights when present
            BigDecimal stdDev = calculateStdDev(weightsList, avgWeight);

            // Calculate consecutive observations backwards from latest snapshot
            int consecutiveCount = calculateConsecutiveAppearances(snapshotHoldingMaps, secKey, totalEligibleSnapshots - 1);

            // Determine presence status & persistence category
            String presenceStatus;
            String persistenceCategory;

            if (isPresentInLatest) {
                if (firstIdx == totalEligibleSnapshots - 1 && appearCount == 1) {
                    presenceStatus = "NEWLY_OBSERVED";
                    persistenceCategory = "NEWLY_OBSERVED";
                } else if (appearCount == spanObsCount) {
                    presenceStatus = "CONTINUOUS";
                    persistenceCategory = (appearRatioPct.compareTo(new BigDecimal("75.00")) >= 0) ? "PERSISTENT" : "INTERMITTENT";
                } else {
                    presenceStatus = "INTERMITTENT";
                    persistenceCategory = "INTERMITTENT";
                }
            } else {
                // Not present in latest snapshot
                int nextIdx = lastIdx + 1;
                if (nextIdx < totalEligibleSnapshots) {
                    String nextScope = snapshotDisclosureScopes.get(nextIdx);
                    long gapDays = ChronoUnit.DAYS.between(lastObsDate, filteredSnapshots.get(nextIdx).getPortfolioDate());

                    if ("PARTIAL_TOP_HOLDINGS".equals(nextScope)) {
                        presenceStatus = "NOT_OBSERVED_IN_PARTIAL_DISCLOSURE";
                        persistenceCategory = "NOT_OBSERVED_IN_PARTIAL_DISCLOSURE";
                    } else if (gapDays > MAX_COMPARABLE_INTERVAL_DAYS) {
                        presenceStatus = "UNRESOLVED_GAP";
                        persistenceCategory = "UNRESOLVED";
                    } else {
                        presenceStatus = "POTENTIAL_EXIT";
                        persistenceCategory = "POTENTIAL_EXIT";
                    }
                } else {
                    presenceStatus = "UNRESOLVED";
                    persistenceCategory = "UNRESOLVED";
                }
            }

            if (resolvedName == null) {
                resolvedName = "Unresolved Holding (" + secKey + ")";
                presenceStatus = "UNRESOLVED";
                persistenceCategory = "UNRESOLVED";
            }

            allHoldingLongevityItems.add(new PositionLongevityItemDto(
                resolvedSecId,
                resolvedIsin,
                resolvedName,
                firstObsDate,
                lastObsDate,
                appearCount,
                spanObsCount,
                appearRatioPct,
                tenureDays,
                tenureMonths,
                tenureBucket,
                presenceStatus,
                persistenceCategory,
                isTop10,
                latestRank,
                latestW,
                avgWeight,
                scaledMinW,
                scaledMaxW,
                weightRange,
                stdDev,
                consecutiveCount,
                trajectory
            ));
        }

        // Sort holdings: current positions first by latest weight desc, then historical exits by lastObservedDate desc
        allHoldingLongevityItems.sort((a, b) -> {
            boolean aActive = a.latestReportedWeightPct().compareTo(BigDecimal.ZERO) > 0;
            boolean bActive = b.latestReportedWeightPct().compareTo(BigDecimal.ZERO) > 0;
            if (aActive && !bActive) return -1;
            if (!aActive && bActive) return 1;
            if (aActive) {
                return b.latestReportedWeightPct().compareTo(a.latestReportedWeightPct());
            }
            if (a.lastObservedDate() != null && b.lastObservedDate() != null) {
                int cmp = b.lastObservedDate().compareTo(a.lastObservedDate());
                if (cmp != 0) return cmp;
            }
            return b.appearanceCount() - a.appearanceCount();
        });

        // Filter persistent holdings
        List<PositionLongevityItemDto> persistentHoldings = allHoldingLongevityItems.stream()
            .filter(item -> "PERSISTENT".equals(item.persistenceCategory()))
            .toList();

        // 4. Build Portfolio Longevity Distribution (Tenure Buckets)
        // Evaluates holdings present in the latest portfolio snapshot
        List<PositionTenureBucketSummaryDto> tenureBuckets = buildTenureBuckets(allHoldingLongevityItems, snapshotSummaries.get(totalEligibleSnapshots - 1));

        // 5. Calculate Consecutive Composition Change Transitions
        List<CompositionChangeTransitionDto> transitions = new ArrayList<>();
        int comparableTransitionsCount = 0;
        BigDecimal sumComparableHalfChanges = BigDecimal.ZERO;

        for (int i = 1; i < totalEligibleSnapshots; i++) {
            PortfolioSnapshot prevSnap = filteredSnapshots.get(i - 1);
            PortfolioSnapshot currSnap = filteredSnapshots.get(i);
            LocalDate d1 = prevSnap.getPortfolioDate();
            LocalDate d2 = currSnap.getPortfolioDate();
            long daysBetween = ChronoUnit.DAYS.between(d1, d2);

            String scope1 = snapshotDisclosureScopes.get(i - 1);
            String scope2 = snapshotDisclosureScopes.get(i);

            String compStatus;
            String compReason;
            boolean isComp;

            if ("PARTIAL_TOP_HOLDINGS".equals(scope1) || "PARTIAL_TOP_HOLDINGS".equals(scope2)) {
                compStatus = "NON_COMPARABLE_DISCLOSURE_SCOPE_SHIFT";
                compReason = "Transition involves partial top-holdings disclosure (< 90% weight coverage). Disclosed weight differences reflect reporting threshold changes rather than verified portfolio trading.";
                isComp = false;
            } else if (daysBetween > MAX_COMPARABLE_INTERVAL_DAYS) {
                compStatus = "NON_COMPARABLE_TEMPORAL_GAP";
                compReason = "Temporal interval of " + daysBetween + " days between disclosures exceeds 45-day threshold. Intermediate entries, exits, and market price changes cannot be observed.";
                isComp = false;
            } else {
                compStatus = "COMPARABLE_FULL_DISCLOSURE";
                compReason = "Consecutive monthly full-portfolio disclosures with verified holdings coverage.";
                isComp = true;
                comparableTransitionsCount++;
            }

            Map<String, SnapshotHoldingRecord> map1 = snapshotHoldingMaps.get(i - 1);
            Map<String, SnapshotHoldingRecord> map2 = snapshotHoldingMaps.get(i);

            Set<String> transitionKeys = new HashSet<>(map1.keySet());
            transitionKeys.addAll(map2.keySet());

            int newEntriesCount = 0;
            BigDecimal newEntriesWeightPct = BigDecimal.ZERO;
            int completeExitsCount = 0;
            BigDecimal completeExitsWeightPct = BigDecimal.ZERO;
            int continuingPositionsCount = 0;
            BigDecimal continuingIncSum = BigDecimal.ZERO;
            BigDecimal continuingDecSum = BigDecimal.ZERO;
            BigDecimal sumAbsWeightChanges = BigDecimal.ZERO;

            List<EntryExitDetail> entriesList = new ArrayList<>();
            List<EntryExitDetail> exitsList = new ArrayList<>();
            List<WeightDeltaDetail> continuingDeltas = new ArrayList<>();

            for (String k : transitionKeys) {
                SnapshotHoldingRecord r1 = map1.get(k);
                SnapshotHoldingRecord r2 = map2.get(k);

                BigDecimal w1 = (r1 != null) ? r1.reportedWeightPct() : BigDecimal.ZERO;
                BigDecimal w2 = (r2 != null) ? r2.reportedWeightPct() : BigDecimal.ZERO;
                BigDecimal delta = w2.subtract(w1, MC);
                BigDecimal absDelta = delta.abs();
                sumAbsWeightChanges = sumAbsWeightChanges.add(absDelta, MC);

                String name = (r2 != null) ? r2.canonicalName() : (r1 != null ? r1.canonicalName() : k);

                if (w1.compareTo(BigDecimal.ZERO) == 0 && w2.compareTo(BigDecimal.ZERO) > 0) {
                    newEntriesCount++;
                    newEntriesWeightPct = newEntriesWeightPct.add(w2, MC);
                    entriesList.add(new EntryExitDetail(name, w2));
                } else if (w1.compareTo(BigDecimal.ZERO) > 0 && w2.compareTo(BigDecimal.ZERO) == 0) {
                    completeExitsCount++;
                    completeExitsWeightPct = completeExitsWeightPct.add(w1, MC);
                    exitsList.add(new EntryExitDetail(name, w1));
                } else if (w1.compareTo(BigDecimal.ZERO) > 0 && w2.compareTo(BigDecimal.ZERO) > 0) {
                    continuingPositionsCount++;
                    if (delta.compareTo(BigDecimal.ZERO) > 0) {
                        continuingIncSum = continuingIncSum.add(delta, MC);
                    } else if (delta.compareTo(BigDecimal.ZERO) < 0) {
                        continuingDecSum = continuingDecSum.add(absDelta, MC);
                    }
                    if (absDelta.compareTo(new BigDecimal("0.05")) >= 0) {
                        continuingDeltas.add(new WeightDeltaDetail(name, delta, absDelta));
                    }
                }
            }

            BigDecimal halfSumCompositionChange = sumAbsWeightChanges.divide(BigDecimal.valueOf(2), MC).setScale(2, RoundingMode.HALF_UP);
            if (isComp) {
                sumComparableHalfChanges = sumComparableHalfChanges.add(halfSumCompositionChange, MC);
            }

            // Unresolved discrepancy check:
            BigDecimal reportedDelta = snapshotSummaries.get(i).sumReportedWeightsPct()
                .subtract(snapshotSummaries.get(i - 1).sumReportedWeightsPct(), MC);
            BigDecimal calculatedDelta = newEntriesWeightPct.subtract(completeExitsWeightPct, MC).add(continuingIncSum.subtract(continuingDecSum, MC), MC);
            BigDecimal unresolvedDiscrepancy = reportedDelta.subtract(calculatedDelta, MC).abs().setScale(2, RoundingMode.HALF_UP);

            entriesList.sort((a, b) -> b.weight().compareTo(a.weight()));
            exitsList.sort((a, b) -> b.weight().compareTo(a.weight()));
            continuingDeltas.sort((a, b) -> b.absDelta().compareTo(a.absDelta()));

            List<String> topEntries = entriesList.stream().limit(3)
                .map(e -> e.name() + " (" + e.weight().setScale(2, RoundingMode.HALF_UP) + "%)")
                .toList();

            List<String> topExits = exitsList.stream().limit(3)
                .map(e -> e.name() + " (" + e.weight().setScale(2, RoundingMode.HALF_UP) + "%)")
                .toList();

            List<String> largestChanges = continuingDeltas.stream().limit(5)
                .map(d -> d.name() + " (" + (d.delta().compareTo(BigDecimal.ZERO) > 0 ? "+" : "") + d.delta().setScale(2, RoundingMode.HALF_UP) + " pp)")
                .toList();

            transitions.add(new CompositionChangeTransitionDto(
                d1, d2, daysBetween, scope1, scope2,
                compStatus, compReason, isComp,
                newEntriesCount, newEntriesWeightPct.setScale(2, RoundingMode.HALF_UP),
                completeExitsCount, completeExitsWeightPct.setScale(2, RoundingMode.HALF_UP),
                continuingPositionsCount,
                continuingIncSum.setScale(2, RoundingMode.HALF_UP),
                continuingDecSum.setScale(2, RoundingMode.HALF_UP),
                sumAbsWeightChanges.setScale(2, RoundingMode.HALF_UP),
                halfSumCompositionChange,
                unresolvedDiscrepancy,
                topEntries, topExits, largestChanges
            ));
        }

        // 6. Turnover Audit & Descriptive Composition Change Summary
        BigDecimal avgMonthlyCompChange = (comparableTransitionsCount > 0)
            ? sumComparableHalfChanges.divide(BigDecimal.valueOf(comparableTransitionsCount), MC).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal annualizedDescriptiveChange = avgMonthlyCompChange.multiply(BigDecimal.valueOf(12), MC).setScale(2, RoundingMode.HALF_UP);

        TurnoverEvaluationDto turnoverDto = new TurnoverEvaluationDto(
            "TURNOVER_UNAVAILABLE_TRANSACTION_DATA_MISSING",
            "Actual regulatory and economic portfolio turnover requires transaction ledgers detailing gross purchase and sale values alongside daily average net assets over statutory trailing periods. Disclosed monthly portfolio snapshots provide point-in-time holdings at calendar month-end, not transaction logs.",
            false,
            null,
            "1/2 * SUM(|w_{i,t} - w_{i,t-1}|) across all reported holdings in consecutive comparable full-portfolio disclosures.",
            avgMonthlyCompChange,
            annualizedDescriptiveChange,
            comparableTransitionsCount,
            transitions.size(),
            List.of(
                "Descriptive composition change measures net holding weight differences across calendar month-ends; it is NOT regulatory portfolio turnover.",
                "Weight changes in continuing positions reflect relative market price fluctuations, corporate actions, and net fund cash flows in addition to active manager rebalancing.",
                "Holdings not observed in partial top-holdings disclosures cannot be inferred as portfolio exits.",
                "Actual portfolio turnover disclosed in fund factsheets is calculated under SEBI regulations using the lesser of purchases or sales divided by average AUM."
            )
        );

        // 7. Data Quality Warnings & Investigation Questions
        List<String> warnings = new ArrayList<>();
        if (allSnapshots.size() > totalEligibleSnapshots) {
            warnings.add("Filtered " + (allSnapshots.size() - totalEligibleSnapshots) + " historical snapshot(s) outside requested Point-in-Time cutoffs or date boundaries.");
        }
        boolean hasPartial = snapshotDisclosureScopes.stream().anyMatch("PARTIAL_TOP_HOLDINGS"::equals);
        if (hasPartial) {
            warnings.add("Includes partial top-holdings disclosures (< 90% weight coverage). Position exits cannot be definitively established from partial reporting periods.");
        }
        boolean hasGap = transitions.stream().anyMatch(t -> "NON_COMPARABLE_TEMPORAL_GAP".equals(t.comparabilityStatus()));
        if (hasGap) {
            warnings.add("Includes temporal gaps exceeding 45 days between portfolio disclosures. Intermediate holding changes cannot be observed or verified.");
        }

        List<String> questions = List.of(
            "Does the fund exhibit high observed tenure across core banking and industrial holdings, consistent with its long-term compounding mandate?",
            "Are observed holding exits genuine portfolio liquidations, or do they represent positions falling below the top-holdings disclosure threshold in partial reporting periods?",
            "How much of the observed month-over-month weight fluctuation in continuing positions is driven by market price appreciation vs active shares rebalancing?",
            "Is regulatory portfolio turnover disclosed in the fund's monthly factsheets or annual reports, and how does it compare to the descriptive composition change observed here?"
        );

        List<String> notes = List.of(
            "Holding tenure reflects empirical presence across authentic Point-in-Time disclosures. First observed date is not necessarily purchase date; last observed date is not necessarily sale date.",
            "Persistence across portfolio disclosures indicates holding duration, not manager investment skill or future return potential.",
            "Descriptive composition change formula: 1/2 * SUM(|w_{i,t} - w_{i,t-1}|). Gated when disclosure scope or temporal cadence is non-comparable."
        );

        return new HoldingLongevityResponseDto(
            schemeOptionId, fundName, planType, optionType, amfiCode, schemeCode,
            "SUCCESS",
            "Point-in-Time holding longevity and portfolio composition change analysis successfully computed across " + totalEligibleSnapshots + " eligible snapshot(s).",
            METHODOLOGY_VERSION, runId, cutoff.toString(),
            allSnapshots.size(), totalEligibleSnapshots, skippedPeriods,
            windowStart, windowEnd,
            snapshotSummaries,
            tenureBuckets,
            persistentHoldings,
            allHoldingLongevityItems,
            transitions,
            turnoverDto,
            warnings,
            questions,
            notes
        );
    }

    private static String resolveUniqueSecurityKey(Long secId, String isin, String canonicalName) {
        if (secId != null) return "ID:" + secId;
        if (isin != null && !isin.isBlank()) return "ISIN:" + isin.trim();
        return "NAME:" + (canonicalName != null ? canonicalName.trim().toUpperCase() : "UNKNOWN");
    }

    private static String resolveTenureBucket(long days) {
        if (days < 180) return "UNDER_6_MONTHS";
        if (days < 365) return "MONTHS_6_TO_12";
        if (days < 1095) return "YEARS_1_TO_3";
        return "OVER_3_YEARS";
    }

    private static BigDecimal calculateStdDev(List<BigDecimal> weights, BigDecimal mean) {
        if (weights.size() <= 1) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal sumSq = BigDecimal.ZERO;
        for (BigDecimal w : weights) {
            BigDecimal diff = w.subtract(mean, MC);
            sumSq = sumSq.add(diff.multiply(diff, MC), MC);
        }
        double variance = sumSq.doubleValue() / weights.size();
        return BigDecimal.valueOf(Math.sqrt(variance)).setScale(2, RoundingMode.HALF_UP);
    }

    private static int calculateConsecutiveAppearances(List<Map<String, SnapshotHoldingRecord>> snapshots, String key, int endIndex) {
        int count = 0;
        for (int i = endIndex; i >= 0; i--) {
            SnapshotHoldingRecord rec = snapshots.get(i).get(key);
            if (rec != null && rec.reportedWeightPct().compareTo(BigDecimal.ZERO) > 0) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private static List<PositionTenureBucketSummaryDto> buildTenureBuckets(
        List<PositionLongevityItemDto> allItems,
        HoldingSnapshotSummaryDto latestSnapshot
    ) {
        // Group active holdings in latest snapshot by tenure bucket
        Map<String, List<PositionLongevityItemDto>> bucketMap = new LinkedHashMap<>();
        bucketMap.put("OVER_3_YEARS", new ArrayList<>());
        bucketMap.put("YEARS_1_TO_3", new ArrayList<>());
        bucketMap.put("MONTHS_6_TO_12", new ArrayList<>());
        bucketMap.put("UNDER_6_MONTHS", new ArrayList<>());

        for (PositionLongevityItemDto item : allItems) {
            if (item.latestReportedWeightPct().compareTo(BigDecimal.ZERO) > 0) {
                String b = item.observedTenureBucket();
                if (bucketMap.containsKey(b)) {
                    bucketMap.get(b).add(item);
                } else {
                    bucketMap.get("UNDER_6_MONTHS").add(item);
                }
            }
        }

        BigDecimal totalLatestWeight = latestSnapshot.sumReportedWeightsPct();
        List<PositionTenureBucketSummaryDto> result = new ArrayList<>();

        for (Map.Entry<String, List<PositionLongevityItemDto>> entry : bucketMap.entrySet()) {
            String bKey = entry.getKey();
            List<PositionLongevityItemDto> items = entry.getValue();
            items.sort((a, b) -> b.latestReportedWeightPct().compareTo(a.latestReportedWeightPct()));

            BigDecimal bucketWeight = items.stream()
                .map(PositionLongevityItemDto::latestReportedWeightPct)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

            BigDecimal weightShare = totalLatestWeight.compareTo(BigDecimal.ZERO) > 0
                ? bucketWeight.divide(totalLatestWeight, MC).multiply(BigDecimal.valueOf(100), MC).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            String label = switch (bKey) {
                case "OVER_3_YEARS" -> "More than 3 Years";
                case "YEARS_1_TO_3" -> "1 to 3 Years";
                case "MONTHS_6_TO_12" -> "6 to 12 Months";
                default -> "Less than 6 Months";
            };

            List<String> top3 = items.stream().limit(3)
                .map(item -> item.canonicalName() + " (" + item.latestReportedWeightPct() + "%)")
                .toList();

            result.add(new PositionTenureBucketSummaryDto(
                bKey, label, items.size(), bucketWeight, weightShare, top3
            ));
        }

        return result;
    }

    private record EntryExitDetail(String name, BigDecimal weight) {}
    private record WeightDeltaDetail(String name, BigDecimal delta, BigDecimal absDelta) {}
}
