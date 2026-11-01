package com.yukira.backend.service;

import com.yukira.backend.domain.entity.Amc;
import com.yukira.backend.domain.entity.HoldingsArtifactReconciliation;
import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.dto.holdings.HoldingsReconciliationSummaryDto;
import com.yukira.backend.repository.AmcRepository;
import com.yukira.backend.repository.HoldingsArtifactReconciliationRepository;
import com.yukira.backend.repository.SourceArtifactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for multi-asset portfolio holdings classification, reconciliation, and audit telemetry (Tasks 176–180).
 */
@Service
public class HoldingsReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(HoldingsReconciliationService.class);

    private final HoldingsArtifactReconciliationRepository reconciliationRepository;
    private final AmcRepository amcRepository;
    private final SourceArtifactRepository sourceArtifactRepository;

    public HoldingsReconciliationService(
        HoldingsArtifactReconciliationRepository reconciliationRepository,
        AmcRepository amcRepository,
        SourceArtifactRepository sourceArtifactRepository
    ) {
        this.reconciliationRepository = reconciliationRepository;
        this.amcRepository = amcRepository;
        this.sourceArtifactRepository = sourceArtifactRepository;
    }

    @Transactional(readOnly = true)
    public List<HoldingsReconciliationSummaryDto> getReconciliationsForAmc(Long amcId) {
        Amc amc = amcRepository.findById(amcId)
            .orElseThrow(() -> new IllegalArgumentException("AMC not found with id: " + amcId));
        return reconciliationRepository.findByAmcOrderByReportingPeriodDesc(amc).stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<HoldingsReconciliationSummaryDto> getReconciliation(Long id) {
        return reconciliationRepository.findById(id).map(this::toDto);
    }

    @Transactional
    public HoldingsReconciliationSummaryDto recordReconciliation(
        Long sourceArtifactId,
        Long amcId,
        LocalDate reportingPeriod,
        String parserVersion,
        String outcome,
        int totalRows,
        int equityRows,
        int debtRows,
        int cashRows,
        int derivativeRows,
        int unclassifiedRows,
        BigDecimal totalMarketValue,
        BigDecimal derivativeNotional,
        BigDecimal cashMarketValue,
        String errorDiagnostics
    ) {
        SourceArtifact artifact = sourceArtifactId != null ? sourceArtifactRepository.findById(sourceArtifactId).orElse(null) : null;
        Amc amc = amcId != null ? amcRepository.findById(amcId).orElse(null) : null;

        HoldingsArtifactReconciliation rec = new HoldingsArtifactReconciliation();
        rec.setSourceArtifact(artifact);
        rec.setAmc(amc);
        rec.setReportingPeriod(reportingPeriod);
        rec.setParserVersion(parserVersion != null ? parserVersion : "1.0.0");
        rec.setParsingOutcome(outcome != null ? outcome : "SUCCESSFULLY_PARSED");
        rec.setTotalRowsRead(totalRows);
        rec.setEquityRowsParsed(equityRows);
        rec.setDebtRowsParsed(debtRows);
        rec.setCashRowsParsed(cashRows);
        rec.setDerivativeRowsParsed(derivativeRows);
        rec.setUnclassifiedRows(unclassifiedRows);
        rec.setTotalMarketValue(totalMarketValue);
        rec.setDerivativeNotionalValue(derivativeNotional);
        rec.setCashMarketValue(cashMarketValue);
        rec.setErrorDiagnostics(errorDiagnostics);

        HoldingsArtifactReconciliation saved = reconciliationRepository.save(rec);
        log.info("Recorded holdings reconciliation: artifact={}, amc={}, outcome={}, totalRows={}",
            sourceArtifactId, amcId, outcome, totalRows);
        return toDto(saved);
    }

    private HoldingsReconciliationSummaryDto toDto(HoldingsArtifactReconciliation r) {
        return new HoldingsReconciliationSummaryDto(
            r.getId(),
            r.getSourceArtifact() != null ? r.getSourceArtifact().getId() : null,
            r.getAmc() != null ? r.getAmc().getId() : null,
            r.getAmc() != null ? r.getAmc().getName() : null,
            r.getReportingPeriod(),
            r.getParserVersion(),
            r.getParsingOutcome(),
            r.getTotalRowsRead(),
            r.getEquityRowsParsed(),
            r.getDebtRowsParsed(),
            r.getCashRowsParsed(),
            r.getDerivativeRowsParsed(),
            r.getUnclassifiedRows(),
            r.getTotalMarketValue(),
            r.getDerivativeNotionalValue(),
            r.getCashMarketValue(),
            r.getErrorDiagnostics(),
            r.getCreatedAt()
        );
    }
}

