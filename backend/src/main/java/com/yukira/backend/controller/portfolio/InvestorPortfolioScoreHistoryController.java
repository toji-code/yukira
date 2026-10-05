package com.yukira.backend.controller.portfolio;

import com.yukira.backend.dto.portfolio.PortfolioScoreHistoryDto;
import com.yukira.backend.service.portfolio.PortfolioScoreHistoryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/investor/portfolio")
public class InvestorPortfolioScoreHistoryController {

    private final PortfolioScoreHistoryService portfolioScoreHistoryService;

    public InvestorPortfolioScoreHistoryController(PortfolioScoreHistoryService portfolioScoreHistoryService) {
        this.portfolioScoreHistoryService = portfolioScoreHistoryService;
    }

    @GetMapping("/score-history")
    public ResponseEntity<PortfolioScoreHistoryDto> getScoreHistory(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        return ResponseEntity.ok(portfolioScoreHistoryService.getPortfolioScoreHistory(subject, asOfDate));
    }
}