package com.yukira.backend.service;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class HoldingsOverlapService {

    private final PortfolioSnapshotRepository portfolioSnapshotRepository;
    private final PortfolioHoldingRepository portfolioHoldingRepository;
    private final SecurityIdentifierRepository securityIdentifierRepository;
    private final SchemeOptionRepository schemeOptionRepository;

    public HoldingsOverlapService(
        PortfolioSnapshotRepository portfolioSnapshotRepository,
        PortfolioHoldingRepository portfolioHoldingRepository,
        SecurityIdentifierRepository securityIdentifierRepository,
        SchemeOptionRepository schemeOptionRepository
    ) {
        this.portfolioSnapshotRepository = portfolioSnapshotRepository;
        this.portfolioHoldingRepository = portfolioHoldingRepository;
        this.securityIdentifierRepository = securityIdentifierRepository;
        this.schemeOptionRepository = schemeOptionRepository;
    }

    public record OverlappingPositionDto(
        String isin,
        String securityName,
        String sector,
        double weightInFundA,
        double weightInFundB,
        double overlapContribution
    ) {}

    public record PairwiseHoldingsOverlapResultDto(
        Long schemeOptionIdA,
        String schemeNameA,
        LocalDate portfolioDateA,
        Long schemeOptionIdB,
        String schemeNameB,
        LocalDate portfolioDateB,
        boolean hasReportingDateMismatch,
        int sharedSecuritiesCount,
        int uniqueSecuritiesInFundA,
        int uniqueSecuritiesInFundB,
        double totalOverlapPercentage,
        double activeSharePercentage,
        List<OverlappingPositionDto> topOverlappingPositions,
        String methodologyStatus // "CANDIDATE — Minimum Weight Pairwise Overlap"
    ) {}

    public PairwiseHoldingsOverlapResultDto calculatePairwiseOverlap(Long schemeOptionIdA, Long schemeOptionIdB) {
        SchemeOption optA = schemeOptionRepository.findById(schemeOptionIdA)
            .orElseThrow(() -> new IllegalArgumentException("SchemeOption A not found: " + schemeOptionIdA));
        SchemeOption optB = schemeOptionRepository.findById(schemeOptionIdB)
            .orElseThrow(() -> new IllegalArgumentException("SchemeOption B not found: " + schemeOptionIdB));

        var snapsA = portfolioSnapshotRepository.findBySchemeOptionIdOrderByPortfolioDateDesc(schemeOptionIdA);
        var snapsB = portfolioSnapshotRepository.findBySchemeOptionIdOrderByPortfolioDateDesc(schemeOptionIdB);

        if (snapsA.isEmpty() || snapsB.isEmpty()) {
            return new PairwiseHoldingsOverlapResultDto(
                schemeOptionIdA, optA.getOptionName(), snapsA.isEmpty() ? null : snapsA.get(0).getPortfolioDate(),
                schemeOptionIdB, optB.getOptionName(), snapsB.isEmpty() ? null : snapsB.get(0).getPortfolioDate(),
                false, 0, 0, 0, 0.0, 100.0, List.of(),
                "CANDIDATE — Insufficient holdings disclosure for one or both funds"
            );
        }

        PortfolioSnapshot snapA = snapsA.get(0);
        PortfolioSnapshot snapB = snapsB.get(0);

        boolean dateMismatch = !snapA.getPortfolioDate().equals(snapB.getPortfolioDate());

        List<PortfolioHolding> holdingsA = portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByHoldingRankAsc(snapA.getId());
        List<PortfolioHolding> holdingsB = portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByHoldingRankAsc(snapB.getId());

        Map<String, HoldingRef> mapA = buildHoldingMap(holdingsA);
        Map<String, HoldingRef> mapB = buildHoldingMap(holdingsB);

        double totalOverlap = 0.0;
        List<OverlappingPositionDto> overlaps = new ArrayList<>();

        for (Map.Entry<String, HoldingRef> entry : mapA.entrySet()) {
            String key = entry.getKey();
            HoldingRef refA = entry.getValue();

            if (mapB.containsKey(key)) {
                HoldingRef refB = mapB.get(key);
                double minWeight = Math.min(refA.weight, refB.weight);
                totalOverlap += minWeight;

                overlaps.add(new OverlappingPositionDto(
                    refA.isin,
                    refA.name,
                    refA.sector,
                    Math.round(refA.weight * 100.0) / 100.0,
                    Math.round(refB.weight * 100.0) / 100.0,
                    Math.round(minWeight * 100.0) / 100.0
                ));
            }
        }

        overlaps.sort((a, b) -> Double.compare(b.overlapContribution(), a.overlapContribution()));

        int sharedCount = overlaps.size();
        int uniqueA = mapA.size() - sharedCount;
        int uniqueB = mapB.size() - sharedCount;
        double roundedOverlap = Math.round(totalOverlap * 100.0) / 100.0;
        double activeShare = Math.round((100.0 - roundedOverlap) * 100.0) / 100.0;

        return new PairwiseHoldingsOverlapResultDto(
            schemeOptionIdA,
            optA.getOptionName(),
            snapA.getPortfolioDate(),
            schemeOptionIdB,
            optB.getOptionName(),
            snapB.getPortfolioDate(),
            dateMismatch,
            sharedCount,
            uniqueA,
            uniqueB,
            roundedOverlap,
            activeShare,
            overlaps.stream().limit(20).toList(),
            "CANDIDATE — Pairwise Minimum Weight Overlap Methodology (FROZEN Phase 2H)"
        );
    }

    private record HoldingRef(String isin, String name, String sector, double weight) {}

    private Map<String, HoldingRef> buildHoldingMap(List<PortfolioHolding> holdings) {
        Map<String, HoldingRef> map = new HashMap<>();
        for (PortfolioHolding ph : holdings) {
            Security s = ph.getSecurity();
            if (s == null) continue;

            String isin = null;
            var ids = securityIdentifierRepository.findBySecurityId(s.getId());
            for (SecurityIdentifier si : ids) {
                if ("ISIN".equalsIgnoreCase(si.getIdentifierType())) {
                    isin = si.getIdentifierValue();
                    break;
                }
            }

            String key = isin != null ? isin : s.getCanonicalName().trim().toUpperCase();
            double w = ph.getReportedWeight() != null ? ph.getReportedWeight().doubleValue() : 0.0;
            String sector = s.getSector() != null ? s.getSector() : "Unclassified";

            map.put(key, new HoldingRef(isin, s.getCanonicalName(), sector, w));
        }
        return map;
    }
}

