package com.yukira.backend.service;

import com.yukira.backend.domain.entity.PortfolioHolding;
import com.yukira.backend.domain.entity.PortfolioSnapshot;
import com.yukira.backend.dto.portfolio.HoldingDto;
import com.yukira.backend.repository.PortfolioHoldingRepository;
import com.yukira.backend.repository.PortfolioSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class PortfolioService {

    private final PortfolioSnapshotRepository snapshotRepository;
    private final PortfolioHoldingRepository holdingRepository;
    private final FundEnrichmentService fundEnrichmentService;

    public PortfolioService(
        PortfolioSnapshotRepository snapshotRepository,
        PortfolioHoldingRepository holdingRepository,
        FundEnrichmentService fundEnrichmentService
    ) {
        this.snapshotRepository = snapshotRepository;
        this.holdingRepository = holdingRepository;
        this.fundEnrichmentService = fundEnrichmentService;
    }

    public List<HoldingDto> getHoldings(Long schemeOptionId, OffsetDateTime knowledgeCutoff) {
        return fundEnrichmentService.getHoldings(schemeOptionId, knowledgeCutoff);
    }

    @Transactional
    public void ingestHoldings(PortfolioSnapshot snapshot, List<PortfolioHolding> holdings) {
        snapshotRepository.save(snapshot);
        holdingRepository.saveAll(holdings);
    }
}
