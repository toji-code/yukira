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
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PortfolioService {

    private final PortfolioSnapshotRepository snapshotRepository;
    private final PortfolioHoldingRepository holdingRepository;

    public PortfolioService(PortfolioSnapshotRepository snapshotRepository, PortfolioHoldingRepository holdingRepository) {
        this.snapshotRepository = snapshotRepository;
        this.holdingRepository = holdingRepository;
    }

    public List<HoldingDto> getHoldings(Long schemeOptionId, OffsetDateTime knowledgeCutoff) {
        return snapshotRepository.findTopBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDesc(schemeOptionId, knowledgeCutoff)
                .map(snapshot -> holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(snapshot.getId())
                        .stream()
                        .map(h -> new HoldingDto(
                                h.getSecurity().getCanonicalName(),
                                h.getReportedWeight(),
                                h.getSecurity().getAssetClass(),
                                snapshot.getPortfolioDate(),
                                "SEBI Monthly"
                        ))
                        .collect(Collectors.toList()))
                .orElse(List.of());
    }

    @Transactional
    public void ingestHoldings(PortfolioSnapshot snapshot, List<PortfolioHolding> holdings) {
        snapshotRepository.save(snapshot);
        holdingRepository.saveAll(holdings);
    }
}
