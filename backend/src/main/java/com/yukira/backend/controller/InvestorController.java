package com.yukira.backend.controller;

import com.yukira.backend.domain.entity.Investor;
import com.yukira.backend.domain.entity.InvestorWatchlist;
import com.yukira.backend.dto.portfolio.PortfolioHoldingDto;
import com.yukira.backend.dto.portfolio.PortfolioHoldingRequest;
import com.yukira.backend.dto.portfolio.PortfolioSummaryDto;
import com.yukira.backend.service.InvestorService;
import com.yukira.backend.service.portfolio.InvestorPortfolioService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/investor")
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
        Investor investor = investorService.getOrCreateInvestor(subject);
        return new AccountResponse(investor.getId(), investor.getAuth0Subject());
    }

    @GetMapping("/watchlist")
    public List<WatchlistResponse> getWatchlist(@AuthenticationPrincipal Jwt jwt) {
        String subject = jwt != null ? jwt.getSubject() : null;
        return investorService.getWatchlist(subject).stream()
                .map(w -> new WatchlistResponse(w.getScheme().getId(), w.getScheme().getCode()))
                .collect(Collectors.toList());
    }

    @PostMapping("/watchlist")
    public WatchlistResponse addWatchlist(@AuthenticationPrincipal Jwt jwt, @RequestBody WatchlistRequest req) {
        String subject = jwt != null ? jwt.getSubject() : null;
        InvestorWatchlist w = investorService.addWatchlist(subject, req.schemeId());
        return new WatchlistResponse(w.getScheme().getId(), w.getScheme().getCode());
    }

    @DeleteMapping("/watchlist/{schemeId}")
    public void removeWatchlist(@AuthenticationPrincipal Jwt jwt, @PathVariable Long schemeId) {
        String subject = jwt != null ? jwt.getSubject() : null;
        investorService.removeWatchlist(subject, schemeId);
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

    public record AccountResponse(Long id, String auth0Subject) {}
    public record WatchlistRequest(Long schemeId) {}
    public record WatchlistResponse(Long schemeId, String code) {}
    public record PortfolioRequest(Long schemeOptionId, BigDecimal units, BigDecimal costBasisAmount) {}
    public record PortfolioResponse(Long schemeOptionId, BigDecimal units) {}
}
