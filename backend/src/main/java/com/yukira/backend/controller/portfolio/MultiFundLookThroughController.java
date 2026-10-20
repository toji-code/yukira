package com.yukira.backend.controller.portfolio;

import com.yukira.backend.domain.entity.Investor;
import com.yukira.backend.domain.entity.InvestorTargetAllocation;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.portfolio.attribution.*;
import com.yukira.backend.dto.portfolio.drivers.*;
import com.yukira.backend.dto.portfolio.lookthrough.*;
import com.yukira.backend.dto.portfolio.scenario.*;
import com.yukira.backend.dto.portfolio.stress.*;
import com.yukira.backend.repository.InvestorRepository;
import com.yukira.backend.repository.InvestorTargetAllocationRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.service.portfolio.MultiFundLookThroughService;
import com.yukira.backend.service.portfolio.PortfolioRiskAttributionService;
import com.yukira.backend.service.portfolio.PortfolioRiskDriversService;
import com.yukira.backend.service.portfolio.PortfolioScenarioLabService;
import com.yukira.backend.service.portfolio.PortfolioStressReplayService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller exposing REST endpoints for Multi-Fund Look-Through, Exposure Aggregation,
 * Pairwise Overlap Matrix, Candidate Allocation Simulation, Historical Stress Replay,
 * Portfolio Scenario Lab, Portfolio Risk Attribution, and Portfolio Risk Drivers.
 */
@RestController
@RequestMapping("/api/v1/portfolio/multi-fund")
@SuppressWarnings("null")
public class MultiFundLookThroughController {

    private final MultiFundLookThroughService lookThroughService;
    private final PortfolioStressReplayService stressReplayService;
    private final PortfolioScenarioLabService scenarioLabService;
    private final PortfolioRiskAttributionService riskAttributionService;
    private final PortfolioRiskDriversService riskDriversService;
    private final InvestorTargetAllocationRepository targetAllocationRepository;
    private final InvestorRepository investorRepository;
    private final SchemeOptionRepository schemeOptionRepository;

    public MultiFundLookThroughController(
        MultiFundLookThroughService lookThroughService,
        PortfolioStressReplayService stressReplayService,
        PortfolioScenarioLabService scenarioLabService,
        PortfolioRiskAttributionService riskAttributionService,
        PortfolioRiskDriversService riskDriversService,
        InvestorTargetAllocationRepository targetAllocationRepository,
        InvestorRepository investorRepository,
        SchemeOptionRepository schemeOptionRepository
    ) {
        this.lookThroughService = lookThroughService;
        this.stressReplayService = stressReplayService;
        this.scenarioLabService = scenarioLabService;
        this.riskAttributionService = riskAttributionService;
        this.riskDriversService = riskDriversService;
        this.targetAllocationRepository = targetAllocationRepository;
        this.investorRepository = investorRepository;
        this.schemeOptionRepository = schemeOptionRepository;
    }

    /**
     * Calculates consolidated look-through exposures, sectors, market-caps, pairwise overlap,
     * and concentration metrics for an ad-hoc or saved research portfolio.
     */
    @PostMapping("/look-through")
    public ResponseEntity<?> calculateLookThrough(@RequestBody MultiFundLookThroughRequestDto request) {
        try {
            MultiFundLookThroughResponseDto response = lookThroughService.calculateMultiFundLookThrough(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Evaluates a candidate fund addition scenario with proportional allocation dilution.
     */
    @PostMapping("/candidate-simulation")
    public ResponseEntity<?> simulateCandidate(@RequestBody CandidateSimulationRequestDto request) {
        try {
            CandidateSimulationResponseDto response = lookThroughService.simulateCandidateFund(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Provides canonical pilot portfolio preset (HDFC Flexi Cap #1 @ 60% + Parag Parikh ELSS #10151 @ 40%).
     */
    @GetMapping("/default-pilot")
    public ResponseEntity<MultiFundLookThroughRequestDto> getDefaultPilotPortfolio() {
        return ResponseEntity.ok(new MultiFundLookThroughRequestDto(
            List.of(
                new ResearchPortfolioFundItemDto(1L, new BigDecimal("60.00")),
                new ResearchPortfolioFundItemDto(10151L, new BigDecimal("40.00"))
            ),
            LocalDate.of(2026, 9, 30),
            OffsetDateTime.now(),
            "RAW"
        ));
    }

    /**
     * Retrieves saved research portfolio target allocations for the authenticated investor.
     */
    @GetMapping("/saved")
    @Transactional(readOnly = true)
    public ResponseEntity<SavedResearchPortfolioDto> getSavedPortfolio(@AuthenticationPrincipal Jwt jwt) {
        String subject = jwt != null ? jwt.getSubject() : "dev-anonymous-investor";
        Optional<Investor> investorOpt = investorRepository.findByAuth0Subject(subject);
        if (investorOpt.isEmpty()) {
            return ResponseEntity.ok(new SavedResearchPortfolioDto("My Research Portfolio", List.of(), BigDecimal.ZERO, false));
        }

        List<InvestorTargetAllocation> targets = targetAllocationRepository.findByInvestor(investorOpt.get());
        List<ResearchPortfolioFundItemDto> funds = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (InvestorTargetAllocation t : targets) {
            if ("SCHEME_OPTION".equalsIgnoreCase(t.getTargetType()) && t.getSchemeOption() != null) {
                BigDecimal w = t.getTargetWeightPercentage() != null ? t.getTargetWeightPercentage() : BigDecimal.ZERO;
                funds.add(new ResearchPortfolioFundItemDto(t.getSchemeOption().getId(), w));
                total = total.add(w);
            }
        }

        boolean isValid = total.compareTo(new BigDecimal("100.00")) == 0 || total.compareTo(new BigDecimal("100")) == 0;
        return ResponseEntity.ok(new SavedResearchPortfolioDto("My Research Portfolio", funds, total, isValid));
    }

    /**
     * Persists research portfolio target allocations for the authenticated investor.
     */
    @PostMapping("/saved")
    @Transactional
    public ResponseEntity<?> savePortfolio(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody SavedResearchPortfolioDto payload
    ) {
        String subject = jwt != null ? jwt.getSubject() : "dev-anonymous-investor";
        Investor investor = investorRepository.findByAuth0Subject(subject)
            .orElseGet(() -> {
                Investor newInv = new Investor();
                newInv.setAuth0Subject(subject);
                return investorRepository.save(newInv);
            });

        targetAllocationRepository.deleteByInvestor(investor);

        BigDecimal total = BigDecimal.ZERO;
        List<ResearchPortfolioFundItemDto> savedFunds = new ArrayList<>();

        if (payload != null && payload.funds() != null) {
            for (ResearchPortfolioFundItemDto item : payload.funds()) {
                if (item.schemeOptionId() == null || item.allocationWeight() == null || item.allocationWeight().compareTo(BigDecimal.ZERO) < 0) {
                    continue;
                }
                Optional<SchemeOption> opt = schemeOptionRepository.findById(item.schemeOptionId());
                if (opt.isPresent()) {
                    InvestorTargetAllocation target = new InvestorTargetAllocation();
                    target.setInvestor(investor);
                    target.setTargetType("SCHEME_OPTION");
                    target.setSchemeOption(opt.get());
                    target.setTargetWeightPercentage(item.allocationWeight());
                    targetAllocationRepository.save(target);

                    savedFunds.add(item);
                    total = total.add(item.allocationWeight());
                }
            }
        }

        boolean isValid = total.compareTo(new BigDecimal("100.00")) == 0 || total.compareTo(new BigDecimal("100")) == 0;
        return ResponseEntity.ok(new SavedResearchPortfolioDto(
            payload != null && payload.portfolioName() != null ? payload.portfolioName() : "My Research Portfolio",
            savedFunds, total, isValid
        ));
    }

    /**
     * Inspects historical coverage for the research portfolio funds and returns the episode catalog
     * with dynamic availability flags and reasons.
     */
    @PostMapping("/stress-replay/episodes")
    public ResponseEntity<List<HistoricalMarketEpisodeDto>> getStressEpisodes(@RequestBody PortfolioStressReplayRequestDto request) {
        List<HistoricalMarketEpisodeDto> episodes = stressReplayService.getAvailableEpisodes(
            request != null ? request.funds() : List.of(),
            request != null ? request.benchmarkCode() : null,
            request != null ? request.knowledgeCutoff() : null
        );
        return ResponseEntity.ok(episodes);
    }

    /**
     * Executes deterministic historical market-regime replay, drawdown path synthesis,
     * constituent loss attribution, and empirical beta sensitivity analysis.
     */
    @PostMapping("/stress-replay")
    public ResponseEntity<?> executeStressReplay(@RequestBody PortfolioStressReplayRequestDto request) {
        try {
            PortfolioStressReplayResponseDto response = stressReplayService.executeStressReplay(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Retrieves portfolio dimension metadata, current market-cap & sector exposures,
     * beta availability, and supported scenario templates for the Scenario Lab.
     */
    @PostMapping("/scenario-lab/metadata")
    public ResponseEntity<?> getScenarioMetadata(@RequestBody ScenarioLabRequestDto request) {
        try {
            ScenarioDimensionMetadataDto metadata = scenarioLabService.getDimensionMetadata(
                request != null ? request.funds() : List.of(),
                request != null ? request.benchmarkCode() : null,
                request != null ? request.asOfDate() : null,
                request != null ? request.knowledgeCutoff() : null
            );
            return ResponseEntity.ok(metadata);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Executes deterministic calculation of a user-configured or templated scenario shock.
     */
    @PostMapping("/scenario-lab/run")
    public ResponseEntity<?> runScenario(@RequestBody ScenarioLabRequestDto request) {
        try {
            ScenarioLabResponseDto response = scenarioLabService.runScenario(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Compares two scenarios under identical portfolio weights, snapshot dates, and calculation rules.
     */
    @PostMapping("/scenario-lab/compare")
    public ResponseEntity<?> compareScenarios(@RequestBody ScenarioComparisonRequestDto request) {
        try {
            ScenarioComparisonResponseDto response = scenarioLabService.compareScenarios(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Executes deterministic covariance-based portfolio risk attribution (Euler volatility decomposition).
     */
    @PostMapping("/risk-attribution")
    public ResponseEntity<?> calculateRiskAttribution(@RequestBody PortfolioRiskAttributionRequestDto request) {
        try {
            PortfolioRiskAttributionResponseDto response = riskAttributionService.calculateRiskAttribution(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Evaluates portfolio risk drivers, underlying security concentrations, cross-fund overlap,
     * sector and market-cap distributions, and deterministic investigation findings.
     */
    @PostMapping("/risk-drivers")
    public ResponseEntity<?> calculateRiskDrivers(@RequestBody MultiFundLookThroughRequestDto request) {
        try {
            PortfolioRiskDriversResponseDto response = riskDriversService.calculateRiskDrivers(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}


