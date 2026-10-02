package com.yukira.backend.controller;

import com.yukira.backend.domain.entity.Investor;
import com.yukira.backend.domain.entity.InvestorPortfolioHolding;
import com.yukira.backend.domain.entity.InvestorWatchlist;
import com.yukira.backend.service.InvestorService;
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

    public InvestorController(InvestorService investorService) {
        this.investorService = investorService;
    }

    @GetMapping("/account")
    public AccountResponse getAccount(@AuthenticationPrincipal Jwt jwt) {
        Investor investor = investorService.getOrCreateInvestor(jwt.getSubject());
        return new AccountResponse(investor.getId(), investor.getAuth0Subject());
    }

    @GetMapping("/watchlist")
    public List<WatchlistResponse> getWatchlist(@AuthenticationPrincipal Jwt jwt) {
        return investorService.getWatchlist(jwt.getSubject()).stream()
                .map(w -> new WatchlistResponse(w.getScheme().getId(), w.getScheme().getCode()))
                .collect(Collectors.toList());
    }

    @PostMapping("/watchlist")
    public WatchlistResponse addWatchlist(@AuthenticationPrincipal Jwt jwt, @RequestBody WatchlistRequest req) {
        InvestorWatchlist w = investorService.addWatchlist(jwt.getSubject(), req.schemeId());
        return new WatchlistResponse(w.getScheme().getId(), w.getScheme().getCode());
    }

    @DeleteMapping("/watchlist/{schemeId}")
    public void removeWatchlist(@AuthenticationPrincipal Jwt jwt, @PathVariable Long schemeId) {
        investorService.removeWatchlist(jwt.getSubject(), schemeId);
    }

    @GetMapping("/portfolio")
    public List<PortfolioResponse> getPortfolio(@AuthenticationPrincipal Jwt jwt) {
        return investorService.getPortfolio(jwt.getSubject()).stream()
                .map(h -> new PortfolioResponse(h.getSchemeOption().getId(), h.getUnits()))
                .collect(Collectors.toList());
    }

    @PostMapping("/portfolio")
    public PortfolioResponse addOrUpdatePortfolio(@AuthenticationPrincipal Jwt jwt, @RequestBody PortfolioRequest req) {
        InvestorPortfolioHolding h = investorService.addOrUpdatePortfolio(jwt.getSubject(), req.schemeOptionId(), req.units());
        return new PortfolioResponse(h.getSchemeOption().getId(), h.getUnits());
    }

    @DeleteMapping("/portfolio/{schemeOptionId}")
    public void removePortfolioHolding(@AuthenticationPrincipal Jwt jwt, @PathVariable Long schemeOptionId) {
        investorService.removePortfolioHolding(jwt.getSubject(), schemeOptionId);
    }

    public record AccountResponse(Long id, String auth0Subject) {}
    public record WatchlistRequest(Long schemeId) {}
    public record WatchlistResponse(Long schemeId, String code) {}
    public record PortfolioRequest(Long schemeOptionId, BigDecimal units) {}
    public record PortfolioResponse(Long schemeOptionId, BigDecimal units) {}
}
