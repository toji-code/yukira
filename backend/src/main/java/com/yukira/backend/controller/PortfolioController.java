package com.yukira.backend.controller;

import com.yukira.backend.dto.analysis.HoldingsCoverageReportDto;
import com.yukira.backend.dto.enrichment.PortfolioSnapshotDetailDto;
import com.yukira.backend.dto.portfolio.FundConcentrationDto;
import com.yukira.backend.dto.portfolio.HoldingDto;
import com.yukira.backend.dto.portfolio.PairwiseCorrelationDto;
import com.yukira.backend.dto.portfolio.PortfolioOverlapResponseDto;
import com.yukira.backend.service.HoldingsCoverageAuditService;
import com.yukira.backend.service.PortfolioIntelligenceService;
import com.yukira.backend.service.PortfolioService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.yukira.backend.dto.portfolio.LookThroughRequestDto;
import com.yukira.backend.dto.portfolio.PortfolioSecurityAggregationDto;

import com.yukira.backend.dto.portfolio.PortfolioFactorExposureResponseDto;
import com.yukira.backend.service.PortfolioFactorExposureService;

import com.yukira.backend.dto.portfolio.PortfolioConstructionRiskResponseDto;
import com.yukira.backend.service.PortfolioConstructionRiskService;

@RestController
@RequestMapping("/api/v1/schemes/options")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final PortfolioIntelligenceService intelligenceService;
    private final HoldingsCoverageAuditService auditService;
    private final PortfolioFactorExposureService factorExposureService;
    private final PortfolioConstructionRiskService constructionRiskService;
    private final com.yukira.backend.service.portfolio.ActiveReturnAttributionService attributionService;
    private final com.yukira.backend.service.BenchmarkCompositionComparisonService benchmarkComparisonService;
    private final com.yukira.backend.service.BenchmarkDivergenceService benchmarkDivergenceService;
    private final com.yukira.backend.service.BenchmarkReconstitutionService benchmarkReconstitutionService;
    private final com.yukira.backend.service.portfolio.ActiveShareService activeShareService;
    private final com.yukira.backend.service.portfolio.SectorDriftService sectorDriftService;
    private final com.yukira.backend.service.portfolio.MarketCapDriftService marketCapDriftService;
    private final com.yukira.backend.service.portfolio.HoldingLongevityService holdingLongevityService;

    @org.springframework.beans.factory.annotation.Autowired
    public PortfolioController(
        PortfolioService portfolioService,
        PortfolioIntelligenceService intelligenceService,
        HoldingsCoverageAuditService auditService,
        PortfolioFactorExposureService factorExposureService,
        PortfolioConstructionRiskService constructionRiskService,
        com.yukira.backend.service.portfolio.ActiveReturnAttributionService attributionService,
        com.yukira.backend.service.BenchmarkCompositionComparisonService benchmarkComparisonService,
        com.yukira.backend.service.BenchmarkDivergenceService benchmarkDivergenceService,
        com.yukira.backend.service.BenchmarkReconstitutionService benchmarkReconstitutionService,
        com.yukira.backend.service.portfolio.ActiveShareService activeShareService,
        com.yukira.backend.service.portfolio.SectorDriftService sectorDriftService,
        com.yukira.backend.service.portfolio.MarketCapDriftService marketCapDriftService,
        com.yukira.backend.service.portfolio.HoldingLongevityService holdingLongevityService
    ) {
        this.portfolioService = portfolioService;
        this.intelligenceService = intelligenceService;
        this.auditService = auditService;
        this.factorExposureService = factorExposureService;
        this.constructionRiskService = constructionRiskService;
        this.attributionService = attributionService;
        this.benchmarkComparisonService = benchmarkComparisonService;
        this.benchmarkDivergenceService = benchmarkDivergenceService;
        this.benchmarkReconstitutionService = benchmarkReconstitutionService;
        this.activeShareService = activeShareService;
        this.sectorDriftService = sectorDriftService;
        this.marketCapDriftService = marketCapDriftService;
        this.holdingLongevityService = holdingLongevityService;
    }

    @GetMapping("/{id}/holdings")
    public ResponseEntity<List<HoldingDto>> getSchemeHoldings(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime knowledgeCutoff) {
        OffsetDateTime cutoff = knowledgeCutoff != null ? knowledgeCutoff : OffsetDateTime.now();
        List<HoldingDto> holdings = portfolioService.getHoldings(id, cutoff);
        return ResponseEntity.ok(holdings);
    }

    @GetMapping("/{id}/holdings/snapshot")
    public ResponseEntity<PortfolioSnapshotDetailDto> getHoldingsSnapshot(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime knowledgeCutoff) {
        OffsetDateTime cutoff = knowledgeCutoff != null ? knowledgeCutoff : OffsetDateTime.now();
        PortfolioSnapshotDetailDto snapshot = portfolioService.getHoldingsSnapshot(id, cutoff);
        return ResponseEntity.ok(snapshot);
    }

    @GetMapping("/{id}/holdings/dates")
    public ResponseEntity<List<LocalDate>> getAvailableHoldingsDates(@PathVariable Long id) {
        List<LocalDate> dates = portfolioService.getAvailableHoldingsDates(id);
        return ResponseEntity.ok(dates);
    }

    @GetMapping("/{id}/holdings/concentration")
    public ResponseEntity<FundConcentrationDto> getFundConcentration(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime knowledgeCutoff) {
        return ResponseEntity.ok(intelligenceService.calculateFundConcentration(id, knowledgeCutoff));
    }

    @GetMapping("/{id}/holdings/snapshots")
    public ResponseEntity<List<com.yukira.backend.dto.portfolio.PortfolioSnapshotSummaryDto>> getAvailableSnapshots(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime knowledgeCutoff) {
        return ResponseEntity.ok(intelligenceService.getAvailableSnapshots(id, knowledgeCutoff));
    }

    @GetMapping("/{id}/holdings/drift")
    public ResponseEntity<com.yukira.backend.dto.portfolio.PortfolioDriftResponseDto> getPortfolioDrift(
            @PathVariable Long id,
            @RequestParam(required = false) Long earlierSnapshotId,
            @RequestParam(required = false) Long laterSnapshotId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime knowledgeCutoff) {
        return ResponseEntity.ok(intelligenceService.calculatePortfolioDrift(id, earlierSnapshotId, laterSnapshotId, knowledgeCutoff));
    }

    @GetMapping("/{id}/holdings/factor-exposure")
    public ResponseEntity<PortfolioFactorExposureResponseDto> getFactorExposure(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime knowledgeCutoff) {
        return ResponseEntity.ok(factorExposureService.calculateFactorExposure(id, asOfDate, knowledgeCutoff));
    }

    @GetMapping("/{id}/attribution")
    public ResponseEntity<com.yukira.backend.dto.portfolio.ActiveReturnAttributionResponseDto> getActiveReturnAttribution(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String horizon,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime knowledgeCutoff) {
        if (attributionService == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(attributionService.calculateAttribution(id, asOfDate, startDate, endDate, knowledgeCutoff, horizon));
    }

    @GetMapping("/{id}/benchmark-comparison")
    public ResponseEntity<com.yukira.backend.dto.portfolio.BenchmarkCompositionComparisonResponseDto> getBenchmarkComparison(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime knowledgeCutoff) {
        return ResponseEntity.ok(benchmarkComparisonService.compareComposition(id, asOfDate, knowledgeCutoff));
    }

    @GetMapping("/{id}/benchmark-divergence")
    public ResponseEntity<com.yukira.backend.dto.portfolio.BenchmarkDivergenceResponseDto> getBenchmarkDivergence(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime knowledgeCutoff) {
        return ResponseEntity.ok(benchmarkDivergenceService.calculateMultiPeriodDivergence(id, startDate, endDate, knowledgeCutoff));
    }

    @GetMapping("/{id}/active-share")
    public ResponseEntity<com.yukira.backend.dto.portfolio.ActiveShareTrajectoryResponseDto> getActiveShareTrajectory(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime knowledgeCutoff) {
        return ResponseEntity.ok(activeShareService.getActiveShareTrajectory(id, asOfDate, startDate, endDate, knowledgeCutoff));
    }

    @GetMapping("/{id}/sector-drift")
    public ResponseEntity<com.yukira.backend.dto.portfolio.SectorDriftResponseDto> getSectorDriftTrajectory(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime knowledgeCutoff) {
        return ResponseEntity.ok(sectorDriftService.getSectorDriftTrajectory(id, asOfDate, startDate, endDate, knowledgeCutoff));
    }

    @GetMapping("/{id}/market-cap-drift")
    public ResponseEntity<com.yukira.backend.dto.portfolio.mcap.MarketCapDriftResponseDto> getMarketCapDriftTrajectory(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime knowledgeCutoff) {
        return ResponseEntity.ok(marketCapDriftService.getMarketCapDriftTrajectory(id, asOfDate, startDate, endDate, knowledgeCutoff));
    }

    @GetMapping("/{id}/holding-longevity")
    public ResponseEntity<com.yukira.backend.dto.portfolio.longevity.HoldingLongevityResponseDto> getHoldingLongevityTrajectory(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime knowledgeCutoff) {
        return ResponseEntity.ok(holdingLongevityService.getHoldingLongevityTrajectory(id, asOfDate, startDate, endDate, knowledgeCutoff));
    }

    @GetMapping("/{id}/benchmark-reconstitution")
    public ResponseEntity<com.yukira.backend.dto.portfolio.BenchmarkReconstitutionResponseDto> getBenchmarkReconstitution(
            @PathVariable Long id,
            @RequestParam(required = false) String benchmarkCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime knowledgeCutoff) {
        return ResponseEntity.ok(benchmarkReconstitutionService.calculateReconstitution(
            benchmarkCode != null ? benchmarkCode : "NIFTY_500_TRI", fromDate, toDate, knowledgeCutoff
        ));
    }

    @GetMapping("/{id}/holdings/construction-risk")
    public ResponseEntity<PortfolioConstructionRiskResponseDto> getPortfolioConstructionRisk(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime knowledgeCutoff) {
        return ResponseEntity.ok(constructionRiskService.calculateConstructionRisk(id, asOfDate, knowledgeCutoff));
    }

    @GetMapping("/overlap")
    public ResponseEntity<PortfolioOverlapResponseDto> getPairwiseOverlap(
            @RequestParam Long schemeOptionIdA,
            @RequestParam Long schemeOptionIdB,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime knowledgeCutoff) {
        return ResponseEntity.ok(intelligenceService.calculatePairwiseOverlap(schemeOptionIdA, schemeOptionIdB, knowledgeCutoff));
    }

    @GetMapping("/correlation")
    public ResponseEntity<PairwiseCorrelationDto> getPairwiseCorrelation(
            @RequestParam Long schemeOptionIdA,
            @RequestParam Long schemeOptionIdB,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime knowledgeCutoff) {
        return ResponseEntity.ok(intelligenceService.calculatePairwiseCorrelation(schemeOptionIdA, schemeOptionIdB, knowledgeCutoff));
    }

    @PostMapping("/look-through")
    public ResponseEntity<PortfolioSecurityAggregationDto> getLookThroughExposure(
            @RequestBody LookThroughRequestDto request,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime knowledgeCutoff) {
        return ResponseEntity.ok(intelligenceService.aggregateLookThroughExposures(request.fundWeights(), knowledgeCutoff));
    }

    @GetMapping("/coverage-audit")
    public ResponseEntity<HoldingsCoverageReportDto> getHoldingsCoverageAudit(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return ResponseEntity.ok(auditService.generateCoverageReport(asOfDate));
    }
}
