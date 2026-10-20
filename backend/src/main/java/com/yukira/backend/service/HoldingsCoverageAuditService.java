package com.yukira.backend.service;

import com.yukira.backend.domain.entity.PortfolioSnapshot;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.dto.analysis.HoldingsCoverageReportDto;
import com.yukira.backend.dto.analysis.HoldingsCoverageReportDto.*;
import com.yukira.backend.ingestion.holdings.MonthlyHoldingsSourceRegistry;
import com.yukira.backend.repository.PortfolioHoldingRepository;
import com.yukira.backend.repository.PortfolioSnapshotRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.repository.SecurityIdentifierRepository;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
public class HoldingsCoverageAuditService {

    private static final Logger log = LoggerFactory.getLogger(HoldingsCoverageAuditService.class);

    private final SchemeOptionRepository schemeOptionRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final PortfolioHoldingRepository holdingRepository;
    private final SecurityIdentifierRepository securityIdentifierRepository;
    private final MonthlyHoldingsSourceRegistry sourceRegistry;
    private final EntityManager entityManager;

    public HoldingsCoverageAuditService(
        SchemeOptionRepository schemeOptionRepository,
        PortfolioSnapshotRepository snapshotRepository,
        PortfolioHoldingRepository holdingRepository,
        SecurityIdentifierRepository securityIdentifierRepository,
        MonthlyHoldingsSourceRegistry sourceRegistry,
        EntityManager entityManager
    ) {
        this.schemeOptionRepository = schemeOptionRepository;
        this.snapshotRepository = snapshotRepository;
        this.holdingRepository = holdingRepository;
        this.securityIdentifierRepository = securityIdentifierRepository;
        this.sourceRegistry = sourceRegistry;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public HoldingsCoverageReportDto generateCoverageReport(LocalDate asOfDate) {
        long startTime = System.currentTimeMillis();
        long queryCount = 0;

        if (asOfDate == null) {
            asOfDate = LocalDate.of(2024, 1, 15);
        }

        // 1. Active Universe
        List<SchemeOption> allOptions = schemeOptionRepository.findAll();
        queryCount++;
        List<SchemeOption> activeOptions = allOptions.stream()
            .filter(o -> "ACTIVE".equalsIgnoreCase(o.getStatus()) || o.getStatus() == null)
            .toList();
        long totalActiveUniverse = activeOptions.size();

        // 2. Options with NAV
        @SuppressWarnings("unchecked")
        List<Long> navOptionIds = (List<Long>) entityManager.createNativeQuery(
            "SELECT DISTINCT scheme_option_id FROM nav_observation"
        ).getResultList();
        queryCount++;
        Set<Long> navOptionSet = new HashSet<>(navOptionIds);
        long activeWithNav = activeOptions.stream().filter(o -> navOptionSet.contains(o.getId())).count();

        // 3. 3Y Ready
        LocalDate start3Y = asOfDate.minusYears(3);
        @SuppressWarnings("unchecked")
        List<Object[]> nav3YRows = entityManager.createNativeQuery(
            "SELECT scheme_option_id, COUNT(DISTINCT effective_date) FROM nav_observation " +
            "WHERE effective_date BETWEEN :sDate AND :eDate GROUP BY scheme_option_id"
        ).setParameter("sDate", start3Y).setParameter("eDate", asOfDate).getResultList();
        queryCount++;
        Map<Long, Long> nav3YMap = new HashMap<>();
        for (Object[] r : nav3YRows) {
            nav3YMap.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
        }
        long active3yReady = activeOptions.stream().filter(o -> nav3YMap.getOrDefault(o.getId(), 0L) >= 700).count();

        // 4. Holdings Coverage Counts
        @SuppressWarnings("unchecked")
        List<Object[]> snapshotOptionRows = entityManager.createNativeQuery(
            "SELECT scheme_option_id, id, portfolio_date, is_latest_revision, reported_holdings_count, sum_reported_weights " +
            "FROM portfolio_snapshot"
        ).getResultList();
        queryCount++;

        Set<Long> optionsWithHoldings = new HashSet<>();
        Set<Long> optionsWithLatestHoldings = new HashSet<>();
        Set<Long> optionsWithRecentHoldings = new HashSet<>();
        Set<Long> optionsWithValidHoldings = new HashSet<>();
        Set<Long> productionVerifiedOptionSet = new HashSet<>();
        Set<Long> controlledFixtureOptionSet = new HashSet<>();

        LocalDate recentThreshold = asOfDate.minusDays(90);

        for (Object[] row : snapshotOptionRows) {
            Long optId = ((Number) row[0]).longValue();
            Long snapId = ((Number) row[1]).longValue();
            LocalDate pDate = row[2] instanceof LocalDate ? (LocalDate) row[2] : (row[2] instanceof java.sql.Date ? ((java.sql.Date) row[2]).toLocalDate() : LocalDate.parse(row[2].toString()));
            Boolean isLatest = Boolean.valueOf(row[3].toString());
            Integer count = row[4] != null ? ((Number) row[4]).intValue() : 0;
            java.math.BigDecimal sumW = row[5] != null ? new java.math.BigDecimal(row[5].toString()) : java.math.BigDecimal.ZERO;

            optionsWithHoldings.add(optId);
            if (isLatest) {
                optionsWithLatestHoldings.add(optId);
            }
            if (pDate != null && !pDate.isBefore(recentThreshold)) {
                optionsWithRecentHoldings.add(optId);
            }
            if (count > 0 && sumW.compareTo(java.math.BigDecimal.ZERO) > 0) {
                optionsWithValidHoldings.add(optId);
            }

            // Inspect SourceArtifact to determine provenance (Seed fixture vs Real Source Acquired)
            Optional<PortfolioSnapshot> sOpt = snapshotRepository.findById(snapId);
            if (sOpt.isPresent() && sOpt.get().getSourceArtifact() != null) {
                SourceArtifact sa = sOpt.get().getSourceArtifact();
                if (isProductionVerifiedArtifact(sa)) {
                    productionVerifiedOptionSet.add(optId);
                } else {
                    controlledFixtureOptionSet.add(optId);
                }
            } else {
                controlledFixtureOptionSet.add(optId);
            }
        }

        long activeWithHoldings = activeOptions.stream().filter(o -> optionsWithHoldings.contains(o.getId())).count();
        long productionVerifiedHoldingsCount = activeOptions.stream().filter(o -> productionVerifiedOptionSet.contains(o.getId())).count();
        long controlledFixtureHoldingsCount = activeOptions.stream().filter(o -> controlledFixtureOptionSet.contains(o.getId())).count();
        long activeWithLatestHoldings = activeOptions.stream().filter(o -> optionsWithLatestHoldings.contains(o.getId())).count();
        long activeWithRecentHoldings = activeOptions.stream().filter(o -> optionsWithRecentHoldings.contains(o.getId())).count();
        long activeWithValidHoldings = activeOptions.stream().filter(o -> optionsWithValidHoldings.contains(o.getId())).count();

        // 5. Options with resolved security identities
        @SuppressWarnings("unchecked")
        List<Long> identityOptionIds = (List<Long>) entityManager.createNativeQuery(
            "SELECT DISTINCT s.scheme_option_id FROM portfolio_snapshot s " +
            "JOIN portfolio_holding h ON h.portfolio_snapshot_id = s.id " +
            "JOIN security_identifier si ON si.security_id = h.security_id " +
            "WHERE si.id_type = 'ISIN'"
        ).getResultList();
        queryCount++;
        Set<Long> identityOptionSet = new HashSet<>(identityOptionIds);
        long activeWithSecurityIdentities = activeOptions.stream().filter(o -> identityOptionSet.contains(o.getId())).count();

        // 6. Audit HDFC Flexi Cap Pilot (118955)
        HdfcPilotAuditDto hdfcPilotAudit = auditHdfcPilot(asOfDate);
        queryCount += 3;

        // 7. Audit Source Classifications
        List<SourceClassificationDto> sourceClassifications = auditSources();
        queryCount += 2;

        long duration = System.currentTimeMillis() - startTime;
        long peakMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        AuditPerformanceMetrics perfMetrics = new AuditPerformanceMetrics(
            totalActiveUniverse,
            queryCount,
            duration,
            peakMem
        );

        return new HoldingsCoverageReportDto(
            asOfDate,
            totalActiveUniverse,
            activeWithNav,
            active3yReady,
            activeWithHoldings,
            productionVerifiedHoldingsCount,
            controlledFixtureHoldingsCount,
            activeWithLatestHoldings,
            activeWithRecentHoldings,
            activeWithValidHoldings,
            activeWithSecurityIdentities,
            hdfcPilotAudit,
            sourceClassifications,
            perfMetrics
        );
    }

    private HdfcPilotAuditDto auditHdfcPilot(LocalDate asOfDate) {
        Optional<SchemeOption> hdfcOpt = schemeOptionRepository.findByAmfiCode("118955");
        if (hdfcOpt.isEmpty()) {
            return new HdfcPilotAuditDto(
                null, "118955", "INF179K01UT0", "HDFC Flexi Cap Fund",
                null, null, 0, 0.0, 0, 0, 0, null, null, null, null, 0, "UNAVAILABLE", "MISSING", "UNAVAILABLE"
            );
        }

        SchemeOption option = hdfcOpt.get();
        Optional<PortfolioSnapshot> snapOpt = snapshotRepository
            .findTopBySchemeOptionIdOrderByPortfolioDateDesc(option.getId());

        if (snapOpt.isEmpty()) {
            return new HdfcPilotAuditDto(
                option.getId(), "118955", "INF179K01UT0", "HDFC Flexi Cap Fund",
                null, null, 0, 0.0, 0, 0, 0, null, null, null, null, 0, "UNAVAILABLE", "MISSING", "UNAVAILABLE"
            );
        }

        PortfolioSnapshot snap = snapOpt.get();
        var holdings = holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(snap.getId());

        int posCount = holdings.size();
        double sumWeight = snap.getSumReportedWeights() != null
            ? snap.getSumReportedWeights().doubleValue() * 100.0
            : 0.0;

        int resolvedCount = 0;
        int unresolvedCount = 0;
        Set<String> isinSet = new HashSet<>();
        int duplicateCount = 0;

        for (var h : holdings) {
            var isinOpt = securityIdentifierRepository.findTopBySecurityIdAndIdType(h.getSecurity().getId(), "ISIN");
            if (isinOpt.isPresent()) {
                resolvedCount++;
                if (!isinSet.add(isinOpt.get().getIdValue())) {
                    duplicateCount++;
                }
            } else {
                unresolvedCount++;
            }
        }

        SourceArtifact artifact = snap.getSourceArtifact();
        Long artifactId = artifact != null ? artifact.getId() : null;
        String storageUri = artifact != null ? artifact.getStorageUri() : null;
        String sha256 = artifact != null ? artifact.getSha256Hash() : null;
        String availTime = snap.getAvailabilityTime() != null ? snap.getAvailabilityTime().toString() : null;

        boolean isStale = snap.getPortfolioDate().isBefore(asOfDate.minusDays(60));
        String freshness = isStale ? "STALE" : "CURRENT";
        String quality = (unresolvedCount == 0 && duplicateCount == 0 && posCount > 0) ? "VALID" : "DATA_QUALITY_LIMITED";

        String provenance = isProductionVerifiedArtifact(artifact) ? "REAL_SOURCE_ACQUIRED" : "CONTROLLED_FIXTURE";

        return new HdfcPilotAuditDto(
            option.getId(),
            "118955",
            "INF179K01UT0",
            option.getPlan() != null && option.getPlan().getScheme() != null ? option.getPlan().getScheme().getName() : "HDFC Flexi Cap Fund",
            snap.getPortfolioDate(),
            snap.getId(),
            posCount,
            sumWeight,
            resolvedCount,
            unresolvedCount,
            duplicateCount,
            artifactId,
            storageUri,
            sha256,
            availTime,
            snap.getRevisionSeq(),
            freshness,
            quality,
            provenance
        );
    }

    private List<SourceClassificationDto> auditSources() {
        List<SourceClassificationDto> list = new ArrayList<>();
        var registeredFunds = sourceRegistry.getAllRegisteredFunds();

        for (var f : registeredFunds) {
            String amfiCode = f.amfiCode();
            String schemeName = f.schemeName();
            String amcName = f.amcName();
            String sourceState = f.state().name();
            String sourceUrl = f.descriptor() != null ? f.descriptor().sourceUrl() : null;

            Optional<SchemeOption> opt = schemeOptionRepository.findByAmfiCode(amfiCode);
            LocalDate lastDate = null;
            int count = 0;
            String quality = "NO_DATA";
            String provenance = "UNVERIFIED";

            if (opt.isPresent()) {
                Optional<PortfolioSnapshot> snapOpt = snapshotRepository
                    .findTopBySchemeOptionIdOrderByPortfolioDateDesc(opt.get().getId());
                if (snapOpt.isPresent()) {
                    PortfolioSnapshot snap = snapOpt.get();
                    lastDate = snap.getPortfolioDate();
                    count = snap.getReportedHoldingsCount() != null ? snap.getReportedHoldingsCount() : 0;
                    quality = "VALID";

                    SourceArtifact sa = snap.getSourceArtifact();
                    if (isProductionVerifiedArtifact(sa)) {
                        provenance = "REAL_SOURCE_ACQUIRED";
                    } else if (sa != null) {
                        provenance = "CONTROLLED_FIXTURE";
                    }
                }
            }

            String httpStatus = "HTTP_404_UNVERIFIED";
            if ("CONTROLLED_FIXTURE".equals(provenance)) {
                httpStatus = "LOCAL_FIXTURE";
            } else if ("REAL_SOURCE_ACQUIRED".equals(provenance)) {
                httpStatus = "HTTP_200_ACQUIRED";
            }

            list.add(new SourceClassificationDto(
                amfiCode,
                schemeName,
                amcName,
                sourceState,
                provenance,
                sourceUrl,
                lastDate,
                count,
                quality,
                httpStatus
            ));
        }

        return list;
    }

    public static boolean isProductionVerifiedArtifact(SourceArtifact sa) {
        if (sa == null) return false;
        String sha = sa.getSha256Hash();
        if (sha == null || sha.isBlank() || sha.startsWith("SEED_HOLDINGS_")) {
            return false;
        }
        if ("7f8a9b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a".equalsIgnoreCase(sha)) {
            return false; // Known synthetic bootstrap SHA
        }
        byte[] blob = sa.getPayloadBlob();
        if (blob == null || blob.length == 0) {
            return false; // Real source acquired MUST have actual payload bytes stored
        }
        return true;
    }
}

