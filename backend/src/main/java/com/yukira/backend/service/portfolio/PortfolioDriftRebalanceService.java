package com.yukira.backend.service.portfolio;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class PortfolioDriftRebalanceService {

    private final InvestorRepository investorRepository;
    private final InvestorPortfolioHoldingRepository holdingRepository;
    private final InvestorTargetAllocationRepository targetAllocationRepository;
    private final NavObservationRepository navObservationRepository;

    public PortfolioDriftRebalanceService(
        InvestorRepository investorRepository,
        InvestorPortfolioHoldingRepository holdingRepository,
        InvestorTargetAllocationRepository targetAllocationRepository,
        NavObservationRepository navObservationRepository
    ) {
        this.investorRepository = investorRepository;
        this.holdingRepository = holdingRepository;
        this.targetAllocationRepository = targetAllocationRepository;
        this.navObservationRepository = navObservationRepository;
    }

    public record PositionDriftItemDto(
        Long schemeOptionId,
        String schemeName,
        BigDecimal actualMarketValue,
        double actualWeightPercentage,
        double targetWeightPercentage,
        double driftPercentage,
        String rebalanceAction, // OVERWEIGHT_TRIM, UNDERWEIGHT_ADD, ON_TARGET
        BigDecimal rebalanceCashAmount,
        boolean triggersDriftAlert
    ) {}

    public record PortfolioRebalanceDriftReportDto(
        Long investorId,
        BigDecimal totalPortfolioMarketValue,
        int totalPositionsCount,
        int driftedPositionsCount,
        double maxDriftPercentage,
        double alertThresholdPercentage,
        List<PositionDriftItemDto> driftItems,
        String epistemicNotice // "Deterministic calculation comparing actual verified market values against investor target allocation."
    ) {}

    public PortfolioRebalanceDriftReportDto evaluateDrift(String auth0Subject, double alertThreshold) {
        Investor investor = investorRepository.findByAuth0Subject(auth0Subject)
            .orElseThrow(() -> new IllegalArgumentException("Investor not found: " + auth0Subject));

        List<InvestorPortfolioHolding> holdings = holdingRepository.findByInvestor(investor);
        List<InvestorTargetAllocation> targets = targetAllocationRepository.findByInvestor(investor);

        // 1. Calculate actual market values
        BigDecimal totalMktVal = BigDecimal.ZERO;
        List<ValuedHolding> valuedList = new ArrayList<>();

        for (InvestorPortfolioHolding h : holdings) {
            Long optId = h.getSchemeOption() != null ? h.getSchemeOption().getId() : null;
            BigDecimal units = h.getUnits() != null ? h.getUnits() : BigDecimal.ZERO;

            BigDecimal nav = BigDecimal.ZERO;
            if (optId != null) {
                var obs = navObservationRepository.findTopBySchemeOptionIdOrderByEffectiveDateDesc(optId);
                nav = obs.map(NavObservation::getNavValue).orElse(BigDecimal.ZERO);
            }

            BigDecimal mktVal = units.multiply(nav).setScale(2, RoundingMode.HALF_UP);
            totalMktVal = totalMktVal.add(mktVal);
            valuedList.add(new ValuedHolding(h, mktVal));
        }

        // 2. Compare against targets
        List<PositionDriftItemDto> items = new ArrayList<>();
        int driftedCount = 0;
        double maxDrift = 0.0;

        for (ValuedHolding vh : valuedList) {
            InvestorPortfolioHolding h = vh.holding;
            Long optId = h.getSchemeOption() != null ? h.getSchemeOption().getId() : null;
            String name = h.getSchemeOption() != null ? h.getSchemeOption().getOptionName() : "Unknown";

            double actualWeight = 0.0;
            if (totalMktVal.compareTo(BigDecimal.ZERO) > 0) {
                actualWeight = vh.marketValue.divide(totalMktVal, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
            }

            // Find matching target
            double targetWeight = 0.0;
            for (InvestorTargetAllocation t : targets) {
                if (optId != null && t.getSchemeOption() != null && optId.equals(t.getSchemeOption().getId())) {
                    targetWeight = t.getTargetWeightPercentage().doubleValue();
                    break;
                }
            }

            double drift = Math.round((actualWeight - targetWeight) * 100.0) / 100.0;
            double absDrift = Math.abs(drift);
            if (absDrift > maxDrift) maxDrift = absDrift;

            boolean alert = absDrift >= alertThreshold;
            if (alert) driftedCount++;

            String action;
            if (drift > 1.0) action = "OVERWEIGHT_TRIM";
            else if (drift < -1.0) action = "UNDERWEIGHT_ADD";
            else action = "ON_TARGET";

            // Target market value
            BigDecimal targetMktVal = totalMktVal.multiply(BigDecimal.valueOf(targetWeight / 100.0)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal cashDelta = targetMktVal.subtract(vh.marketValue);

            items.add(new PositionDriftItemDto(
                optId,
                name,
                vh.marketValue,
                actualWeight,
                targetWeight,
                drift,
                action,
                cashDelta,
                alert
            ));
        }

        return new PortfolioRebalanceDriftReportDto(
            investor.getId(),
            totalMktVal,
            holdings.size(),
            driftedCount,
            Math.round(maxDrift * 100.0) / 100.0,
            alertThreshold,
            items,
            "Deterministic calculation comparing actual verified market values against investor target allocation."
        );
    }

    private record ValuedHolding(InvestorPortfolioHolding holding, BigDecimal marketValue) {}
}

