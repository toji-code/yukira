package com.yukira.backend.service;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class HoldingsConstructionService {

    private final PortfolioSnapshotRepository portfolioSnapshotRepository;
    private final PortfolioHoldingRepository portfolioHoldingRepository;
    private final HoldingsMarketCapClassificationRepository marketCapRepository;
    private final HoldingsSectorClassificationRepository sectorRepository;
    private final SecurityIdentifierRepository securityIdentifierRepository;

    public HoldingsConstructionService(
        PortfolioSnapshotRepository portfolioSnapshotRepository,
        PortfolioHoldingRepository portfolioHoldingRepository,
        HoldingsMarketCapClassificationRepository marketCapRepository,
        HoldingsSectorClassificationRepository sectorRepository,
        SecurityIdentifierRepository securityIdentifierRepository
    ) {
        this.portfolioSnapshotRepository = portfolioSnapshotRepository;
        this.portfolioHoldingRepository = portfolioHoldingRepository;
        this.marketCapRepository = marketCapRepository;
        this.sectorRepository = sectorRepository;
        this.securityIdentifierRepository = securityIdentifierRepository;
    }

    public record HoldingItemDto(
        int rank,
        String isin,
        String securityName,
        String assetClass,
        String sector,
        String marketCapCategory, // LARGE_CAP, MID_CAP, SMALL_CAP, UNCLASSIFIED
        BigDecimal reportedWeight,
        BigDecimal marketValue,
        String resolutionStatus // EXACT_ISIN_MATCH, VERIFIED_ALIAS, NAME_CANDIDATE, UNRESOLVED_SECURITY
    ) {}

    public record SectorExposureDto(
        String sectorName,
        double weightPercentage,
        int holdingsCount
    ) {}

    public record MarketCapExposureDto(
        double largeCapPercentage,
        double midCapPercentage,
        double smallCapPercentage,
        double unclassifiedPercentage
    ) {}

    public record AssetAllocationDto(
        double netEquityPercentage,
        double debtPercentage,
        double cashPercentage,
        double derivativePercentage
    ) {}

    public record FundHoldingsConstructionDto(
        Long schemeOptionId,
        LocalDate portfolioDate,
        int totalHoldingsCount,
        BigDecimal totalReportedNetAssets,
        double top5ConcentrationPercentage,
        double top10ConcentrationPercentage,
        double top20ConcentrationPercentage,
        String concentrationDenominator, // "TOTAL_NET_ASSETS (AUM)"
        AssetAllocationDto assetAllocation,
        MarketCapExposureDto marketCapExposure,
        List<SectorExposureDto> sectorExposures,
        List<HoldingItemDto> topHoldings,
        String sourceArtifactHash,
        String epistemicNotice,
        int unresolvedSecuritiesCount,
        double unresolvedWeightPercentage,
        long disclosureLagDays
    ) {}

    public Optional<FundHoldingsConstructionDto> getHoldingsConstruction(Long schemeOptionId, LocalDate asOfDate) {
        List<PortfolioSnapshot> snapshots;
        if (asOfDate != null) {
            snapshots = portfolioSnapshotRepository.findBySchemeOptionIdAndPortfolioDateLessThanEqualOrderByPortfolioDateDesc(schemeOptionId, asOfDate);
        } else {
            snapshots = portfolioSnapshotRepository.findBySchemeOptionIdOrderByPortfolioDateDesc(schemeOptionId);
        }

        if (snapshots.isEmpty()) {
            return Optional.empty();
        }

        PortfolioSnapshot snap = snapshots.get(0);
        List<PortfolioHolding> holdings = portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByHoldingRankAsc(snap.getId());

        long lagDays = ChronoUnit.DAYS.between(snap.getPortfolioDate(), LocalDate.now());

        if (holdings.isEmpty()) {
            return Optional.of(new FundHoldingsConstructionDto(
                schemeOptionId, snap.getPortfolioDate(), 0, snap.getReportedTotalNetAssets(),
                0.0, 0.0, 0.0, "TOTAL_NET_ASSETS (AUM)",
                new AssetAllocationDto(0.0, 0.0, 100.0, 0.0),
                new MarketCapExposureDto(0.0, 0.0, 0.0, 0.0),
                List.of(), List.of(),
                snap.getSourceArtifact() != null ? snap.getSourceArtifact().getSha256Hash() : null,
                "Candidate fund construction analytics grounded in official monthly portfolio disclosures.",
                0, 0.0, lagDays
            ));
        }

        // Map holdings to DTOs
        List<HoldingItemDto> holdingDtos = new ArrayList<>();
        Map<String, Double> sectorWeights = new HashMap<>();
        Map<String, Integer> sectorCounts = new HashMap<>();

        double equitySum = 0.0;
        double debtSum = 0.0;
        double cashSum = 0.0;
        double derivSum = 0.0;

        double largeCapSum = 0.0;
        double midCapSum = 0.0;
        double smallCapSum = 0.0;
        double unclassMcapSum = 0.0;

        int r = 1;
        for (PortfolioHolding ph : holdings) {
            Security s = ph.getSecurity();
            String name = s != null ? s.getCanonicalName() : "Unknown Holding";
            String assetClass = s != null && s.getAssetClass() != null ? s.getAssetClass().toUpperCase() : "EQUITY";
            String sector = s != null && s.getSector() != null ? s.getSector() : "Unclassified";

            // Find ISIN
            String isin = null;
            if (s != null) {
                var ids = securityIdentifierRepository.findBySecurityId(s.getId());
                for (SecurityIdentifier si : ids) {
                    if ("ISIN".equalsIgnoreCase(si.getIdentifierType())) {
                        isin = si.getIdentifierValue();
                        break;
                    }
                }
            }

            // Resolution Status (Task 287)
            String resolutionStatus;
            if (isin != null && !isin.isBlank()) {
                resolutionStatus = "EXACT_ISIN_MATCH";
            } else if (s != null && s.getCanonicalName() != null && !s.getCanonicalName().contains("Unknown")) {
                resolutionStatus = "NAME_CANDIDATE";
            } else {
                resolutionStatus = "UNRESOLVED_SECURITY";
            }

            // Market cap classification
            String mcapCat = "UNCLASSIFIED";
            if (isin != null) {
                var mcap = marketCapRepository.findLatestByIsin(isin);
                if (!mcap.isEmpty()) {
                    mcapCat = mcap.get(0).getMarketCapCategory();
                }
            }

            double w = ph.getReportedWeight() != null ? ph.getReportedWeight().doubleValue() : 0.0;

            if (assetClass.contains("EQUITY")) {
                equitySum += w;
                switch (mcapCat.toUpperCase()) {
                    case "LARGE_CAP" -> largeCapSum += w;
                    case "MID_CAP" -> midCapSum += w;
                    case "SMALL_CAP", "MICRO_CAP" -> smallCapSum += w;
                    default -> unclassMcapSum += w;
                }
            } else if (assetClass.contains("DEBT") || assetClass.contains("BOND") || assetClass.contains("TREPS")) {
                debtSum += w;
            } else if (assetClass.contains("CASH")) {
                cashSum += w;
            } else if (assetClass.contains("DERIVATIVE")) {
                derivSum += w;
            } else {
                equitySum += w;
            }

            sectorWeights.put(sector, sectorWeights.getOrDefault(sector, 0.0) + w);
            sectorCounts.put(sector, sectorCounts.getOrDefault(sector, 0) + 1);

            holdingDtos.add(new HoldingItemDto(
                ph.getHoldingRank() != null ? ph.getHoldingRank() : r++,
                isin,
                name,
                assetClass,
                sector,
                mcapCat,
                ph.getReportedWeight(),
                ph.getMarketValue(),
                resolutionStatus
            ));
        }

        // Sort holdings by weight desc
        holdingDtos.sort((a, b) -> Double.compare(
            b.reportedWeight() != null ? b.reportedWeight().doubleValue() : 0.0,
            a.reportedWeight() != null ? a.reportedWeight().doubleValue() : 0.0
        ));

        double top5 = holdingDtos.stream().limit(5).mapToDouble(h -> h.reportedWeight() != null ? h.reportedWeight().doubleValue() : 0.0).sum();
        double top10 = holdingDtos.stream().limit(10).mapToDouble(h -> h.reportedWeight() != null ? h.reportedWeight().doubleValue() : 0.0).sum();
        double top20 = holdingDtos.stream().limit(20).mapToDouble(h -> h.reportedWeight() != null ? h.reportedWeight().doubleValue() : 0.0).sum();

        int unresolvedCount = (int) holdingDtos.stream().filter(h -> "UNRESOLVED_SECURITY".equals(h.resolutionStatus())).count();
        double unresolvedWeight = holdingDtos.stream()
            .filter(h -> "UNRESOLVED_SECURITY".equals(h.resolutionStatus()))
            .mapToDouble(h -> h.reportedWeight() != null ? h.reportedWeight().doubleValue() : 0.0)
            .sum();

        List<SectorExposureDto> sectorList = sectorWeights.entrySet().stream()
            .map(e -> new SectorExposureDto(e.getKey(), Math.round(e.getValue() * 100.0) / 100.0, sectorCounts.get(e.getKey())))
            .sorted(Comparator.comparingDouble(SectorExposureDto::weightPercentage).reversed())
            .collect(Collectors.toList());

        return Optional.of(new FundHoldingsConstructionDto(
            schemeOptionId,
            snap.getPortfolioDate(),
            holdings.size(),
            snap.getReportedTotalNetAssets(),
            Math.round(top5 * 100.0) / 100.0,
            Math.round(top10 * 100.0) / 100.0,
            Math.round(top20 * 100.0) / 100.0,
            "TOTAL_NET_ASSETS (AUM)",
            new AssetAllocationDto(
                Math.round(equitySum * 100.0) / 100.0,
                Math.round(debtSum * 100.0) / 100.0,
                Math.round(cashSum * 100.0) / 100.0,
                Math.round(derivSum * 100.0) / 100.0
            ),
            new MarketCapExposureDto(
                Math.round(largeCapSum * 100.0) / 100.0,
                Math.round(midCapSum * 100.0) / 100.0,
                Math.round(smallCapSum * 100.0) / 100.0,
                Math.round(unclassMcapSum * 100.0) / 100.0
            ),
            sectorList,
            holdingDtos.stream().limit(25).collect(Collectors.toList()),
            snap.getSourceArtifact() != null ? snap.getSourceArtifact().getSha256Hash() : null,
            "Candidate fund construction analytics grounded in official monthly portfolio disclosures.",
            unresolvedCount,
            Math.round(unresolvedWeight * 100.0) / 100.0,
            lagDays
        ));
    }
}
