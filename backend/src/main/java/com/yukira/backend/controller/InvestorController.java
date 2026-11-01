package com.yukira.backend.controller;

import com.yukira.backend.domain.entity.Investor;
import com.yukira.backend.dto.analysis.InvestorComparisonResearchDto;
import com.yukira.backend.dto.analysis.SaveComparisonResearchRequest;
import com.yukira.backend.dto.portfolio.PortfolioHoldingDto;
import com.yukira.backend.dto.portfolio.PortfolioHoldingRequest;
import com.yukira.backend.dto.portfolio.PortfolioSummaryDto;
import com.yukira.backend.dto.watchlist.InvestorResearchThesisHistoryDto;
import com.yukira.backend.dto.watchlist.WatchlistNoteRequest;
import com.yukira.backend.dto.watchlist.WatchlistRequest;
import com.yukira.backend.dto.watchlist.WatchlistResponse;
import com.yukira.backend.service.InvestorService;
import com.yukira.backend.service.portfolio.InvestorPortfolioService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/api/investor", "/api/v1/investors"})
public class InvestorController {
    private final InvestorService investorService;
    private final InvestorPortfolioService portfolioService;

    public InvestorController(InvestorService investorService, InvestorPortfolioService portfolioService) {
        this.investorService = investorService;
        this.portfolioService = portfolioService;
    }

    @GetMapping("/account")
    public AccountResponse getAccount(@AuthenticationPrincipal Jwt jwt) {
        String subject = jwt != null ? jwt.getSubject() : null;
        return investorService.getAccountSummary(subject);
    }

    @GetMapping("/watchlist")
    public List<WatchlistResponse> getWatchlist(@AuthenticationPrincipal Jwt jwt) {
        String subject = jwt != null ? jwt.getSubject() : null;
        return investorService.getWatchlistDtoList(subject);
    }

    @GetMapping("/watchlist/{watchlistId}/history")
    public List<InvestorResearchThesisHistoryDto> getWatchlistThesisHistory(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long watchlistId
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        return investorService.getThesisHistory(subject, watchlistId);
    }

    @PostMapping("/watchlist")
    public WatchlistResponse addWatchlist(@AuthenticationPrincipal Jwt jwt, @RequestBody WatchlistRequest req) {
        String subject = jwt != null ? jwt.getSubject() : null;
        return investorService.addWatchlistDto(subject, req);
    }

    @PutMapping("/watchlist/{schemeId}")
    public WatchlistResponse updateWatchlistNote(@AuthenticationPrincipal Jwt jwt,
                                                 @PathVariable Long schemeId,
                                                 @RequestBody WatchlistNoteRequest req) {
        String subject = jwt != null ? jwt.getSubject() : null;
        return investorService.updateWatchlistNote(subject, schemeId, req);
    }

    @PutMapping("/watchlist/option/{schemeOptionId}")
    public WatchlistResponse updateWatchlistNoteByOption(@AuthenticationPrincipal Jwt jwt,
                                                         @PathVariable Long schemeOptionId,
                                                         @RequestBody WatchlistNoteRequest req) {
        String subject = jwt != null ? jwt.getSubject() : null;
        return investorService.updateWatchlistNoteByOption(subject, schemeOptionId, req);
    }

    @DeleteMapping("/watchlist/{schemeId}")
    public void removeWatchlist(@AuthenticationPrincipal Jwt jwt, @PathVariable Long schemeId) {
        String subject = jwt != null ? jwt.getSubject() : null;
        investorService.removeWatchlist(subject, schemeId);
    }

    @DeleteMapping("/watchlist/option/{schemeOptionId}")
    public void removeWatchlistByOption(@AuthenticationPrincipal Jwt jwt, @PathVariable Long schemeOptionId) {
        String subject = jwt != null ? jwt.getSubject() : null;
        investorService.removeWatchlistByOption(subject, schemeOptionId);
    }

    @GetMapping("/portfolio")
    public List<PortfolioResponse> getPortfolio(@AuthenticationPrincipal Jwt jwt) {
        String subject = jwt != null ? jwt.getSubject() : null;
        PortfolioSummaryDto summary = portfolioService.getPortfolioSummary(subject);
        return summary.holdings().stream()
                .map(h -> new PortfolioResponse(h.schemeOptionId(), h.units()))
                .collect(Collectors.toList());
    }

    @PostMapping("/portfolio")
    public PortfolioResponse addOrUpdatePortfolio(@AuthenticationPrincipal Jwt jwt, @RequestBody PortfolioRequest req) {
        String subject = jwt != null ? jwt.getSubject() : null;
        PortfolioHoldingDto h = portfolioService.addOrUpdateHolding(
            subject, new PortfolioHoldingRequest(req.schemeOptionId(), req.units(), req.costBasisAmount())
        );
        return new PortfolioResponse(h.schemeOptionId(), h.units());
    }

    @DeleteMapping("/portfolio/{schemeOptionId}")
    public void removePortfolioHolding(@AuthenticationPrincipal Jwt jwt, @PathVariable Long schemeOptionId) {
        String subject = jwt != null ? jwt.getSubject() : null;
        portfolioService.removeHolding(subject, schemeOptionId);
    }

    @PostMapping("/workspace/comparisons")
    public InvestorComparisonResearchDto saveComparison(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody SaveComparisonResearchRequest req
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        return investorService.saveComparisonResearch(subject, req);
    }

    @GetMapping("/workspace/comparisons")
    public List<InvestorComparisonResearchDto> getSavedComparisons(
        @AuthenticationPrincipal Jwt jwt
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        return investorService.getSavedComparisons(subject);
    }

    public record AccountResponse(
            Long id,
            String auth0Subject,
            String createdAt,
            int savedFundsCount,
            int portfolioHoldingsCount,
            String planId,
            String planName,
            String entitlementStatus,
            String subscriptionStatus
    ) {
        public AccountResponse(Long id, String auth0Subject, String createdAt, int savedFundsCount, int portfolioHoldingsCount) {
            this(id, auth0Subject, createdAt, savedFundsCount, portfolioHoldingsCount, "FREE", "Community Explorer", "STANDARD", "ACTIVE");
        }

        public AccountResponse(Long id, String auth0Subject) {
            this(id, auth0Subject, null, 0, 0, "FREE", "Community Explorer", "STANDARD", "ACTIVE");
        }
    }
    public record PortfolioRequest(Long schemeOptionId, BigDecimal units, BigDecimal costBasisAmount) {}
    public record PortfolioResponse(Long schemeOptionId, BigDecimal units) {}
}
