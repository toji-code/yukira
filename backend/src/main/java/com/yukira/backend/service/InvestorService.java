package com.yukira.backend.service;

import com.yukira.backend.domain.entity.Investor;
import com.yukira.backend.domain.entity.InvestorPortfolioHolding;
import com.yukira.backend.domain.entity.InvestorWatchlist;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.repository.InvestorPortfolioHoldingRepository;
import com.yukira.backend.repository.InvestorRepository;
import com.yukira.backend.repository.InvestorWatchlistRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.repository.SchemeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class InvestorService {
    private final InvestorRepository investorRepository;
    private final InvestorWatchlistRepository watchlistRepository;
    private final InvestorPortfolioHoldingRepository portfolioRepository;
    private final SchemeOptionRepository schemeOptionRepository;
    private final SchemeRepository schemeRepository;

    public InvestorService(InvestorRepository investorRepository,
                           InvestorWatchlistRepository watchlistRepository,
                           InvestorPortfolioHoldingRepository portfolioRepository,
                           SchemeOptionRepository schemeOptionRepository,
                           SchemeRepository schemeRepository) {
        this.investorRepository = investorRepository;
        this.watchlistRepository = watchlistRepository;
        this.portfolioRepository = portfolioRepository;
        this.schemeOptionRepository = schemeOptionRepository;
        this.schemeRepository = schemeRepository;
    }

    public Investor getOrCreateInvestor(String auth0Subject) {
        String effectiveSubject = (auth0Subject != null && !auth0Subject.isBlank()) ? auth0Subject : "dev-investor-local";
        return investorRepository.findByAuth0Subject(effectiveSubject)
                .orElseGet(() -> {
                    Investor investor = new Investor();
                    investor.setAuth0Subject(effectiveSubject);
                    return investorRepository.save(investor);
                });
    }

    public List<InvestorWatchlist> getWatchlist(String auth0Subject) {
        Investor investor = getOrCreateInvestor(auth0Subject);
        return watchlistRepository.findByInvestorId(investor.getId());
    }

    public InvestorWatchlist addWatchlist(String auth0Subject, Long schemeId) {
        Investor investor = getOrCreateInvestor(auth0Subject);
        return watchlistRepository.findByInvestorIdAndSchemeId(investor.getId(), schemeId)
                .orElseGet(() -> {
                    com.yukira.backend.domain.entity.Scheme scheme = schemeRepository.findById(schemeId)
                            .orElseThrow(() -> new IllegalArgumentException("Invalid schemeId"));
                    InvestorWatchlist wl = new InvestorWatchlist();
                    wl.setInvestor(investor);
                    wl.setScheme(scheme);
                    return watchlistRepository.save(wl);
                });
    }

    public void removeWatchlist(String auth0Subject, Long schemeId) {
        Investor investor = getOrCreateInvestor(auth0Subject);
        watchlistRepository.findByInvestorIdAndSchemeId(investor.getId(), schemeId)
                .ifPresent(watchlistRepository::delete);
    }

    public List<InvestorPortfolioHolding> getPortfolio(String auth0Subject) {
        Investor investor = getOrCreateInvestor(auth0Subject);
        return portfolioRepository.findByInvestorId(investor.getId());
    }

    public InvestorPortfolioHolding addOrUpdatePortfolio(String auth0Subject, Long schemeOptionId, BigDecimal units) {
        Investor investor = getOrCreateInvestor(auth0Subject);
        SchemeOption schemeOption = schemeOptionRepository.findById(schemeOptionId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid schemeOptionId"));

        InvestorPortfolioHolding holding = portfolioRepository.findByInvestorIdAndSchemeOptionId(investor.getId(), schemeOptionId)
                .orElse(new InvestorPortfolioHolding());
        
        holding.setInvestor(investor);
        holding.setSchemeOption(schemeOption);
        holding.setUnits(units);
        return portfolioRepository.save(holding);
    }

    public void removePortfolioHolding(String auth0Subject, Long schemeOptionId) {
        Investor investor = getOrCreateInvestor(auth0Subject);
        portfolioRepository.findByInvestorIdAndSchemeOptionId(investor.getId(), schemeOptionId)
                .ifPresent(portfolioRepository::delete);
    }
}
