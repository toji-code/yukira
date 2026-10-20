package com.yukira.backend.service;

import com.yukira.backend.domain.entity.AnalyticalScore;
import com.yukira.backend.domain.entity.Investor;
import com.yukira.backend.domain.entity.InvestorPortfolioHolding;
import com.yukira.backend.domain.entity.InvestorWatchlist;
import com.yukira.backend.domain.entity.NavObservation;
import com.yukira.backend.domain.entity.Scheme;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.watchlist.WatchlistNoteRequest;
import com.yukira.backend.dto.watchlist.WatchlistRequest;
import com.yukira.backend.dto.watchlist.WatchlistResponse;
import com.yukira.backend.repository.AnalyticalScoreRepository;
import com.yukira.backend.repository.InvestorPortfolioHoldingRepository;
import com.yukira.backend.repository.InvestorRepository;
import com.yukira.backend.repository.InvestorWatchlistRepository;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.repository.SchemeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class InvestorService {
    private static final int MAX_NOTE_LENGTH = 1000;

    private final InvestorRepository investorRepository;
    private final InvestorWatchlistRepository watchlistRepository;
    private final InvestorPortfolioHoldingRepository portfolioRepository;
    private final SchemeOptionRepository schemeOptionRepository;
    private final SchemeRepository schemeRepository;
    private final NavObservationRepository navObservationRepository;
    private final AnalyticalScoreRepository analyticalScoreRepository;

    @org.springframework.beans.factory.annotation.Value("${yukira.auth.dev-fallback-enabled:true}")
    private boolean devFallbackEnabled;

    public InvestorService(InvestorRepository investorRepository,
                           InvestorWatchlistRepository watchlistRepository,
                           InvestorPortfolioHoldingRepository portfolioRepository,
                           SchemeOptionRepository schemeOptionRepository,
                           SchemeRepository schemeRepository,
                           NavObservationRepository navObservationRepository,
                           AnalyticalScoreRepository analyticalScoreRepository) {
        this.investorRepository = investorRepository;
        this.watchlistRepository = watchlistRepository;
        this.portfolioRepository = portfolioRepository;
        this.schemeOptionRepository = schemeOptionRepository;
        this.schemeRepository = schemeRepository;
        this.navObservationRepository = navObservationRepository;
        this.analyticalScoreRepository = analyticalScoreRepository;
    }

    public Investor getOrCreateInvestor(String auth0Subject) {
        String effectiveSubject;
        if (auth0Subject != null && !auth0Subject.isBlank()) {
            effectiveSubject = auth0Subject;
        } else {
            if (!devFallbackEnabled) {
                throw new org.springframework.security.access.AccessDeniedException("Authenticated investor principal required.");
            }
            effectiveSubject = "dev-investor-local";
        }

        final String targetSubject = effectiveSubject;
        return investorRepository.findByAuth0Subject(targetSubject)
                .orElseGet(() -> {
                    Investor investor = new Investor();
                    investor.setAuth0Subject(targetSubject);
                    return investorRepository.save(investor);
                });
    }

    public List<InvestorWatchlist> getWatchlist(String auth0Subject) {
        Investor investor = getOrCreateInvestor(auth0Subject);
        return watchlistRepository.findByInvestorId(investor.getId());
    }

    public List<WatchlistResponse> getWatchlistDtoList(String auth0Subject) {
        Investor investor = getOrCreateInvestor(auth0Subject);
        List<InvestorWatchlist> list = watchlistRepository.findByInvestorId(investor.getId());
        return list.stream().map(this::mapToWatchlistResponse).collect(Collectors.toList());
    }

    public WatchlistResponse addWatchlistDto(String auth0Subject, WatchlistRequest req) {
        validateNoteLength(req.reasonSaved(), "reasonSaved");
        validateNoteLength(req.investigationQuestion(), "investigationQuestion");
        validateNoteLength(req.nextStep(), "nextStep");

        Investor investor = getOrCreateInvestor(auth0Subject);
        Scheme scheme = null;
        SchemeOption option = null;

        if (req.schemeOptionId() != null) {
            option = schemeOptionRepository.findById(req.schemeOptionId())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid schemeOptionId: " + req.schemeOptionId()));
            scheme = option.getPlan() != null ? option.getPlan().getScheme() : null;
        }

        if (scheme == null && req.schemeId() != null) {
            scheme = schemeRepository.findById(req.schemeId())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid schemeId: " + req.schemeId()));
        }

        if (scheme == null) {
            throw new IllegalArgumentException("Either schemeId or schemeOptionId must be provided");
        }

        // If schemeOption was not explicitly passed, resolve the first option for the scheme if available
        if (option == null) {
            List<SchemeOption> options = schemeOptionRepository.findByPlanSchemeId(scheme.getId());
            if (!options.isEmpty()) {
                option = options.get(0);
            }
        }

        final Scheme finalScheme = scheme;
        final SchemeOption finalOption = option;

        InvestorWatchlist wl = null;
        if (finalOption != null) {
            wl = watchlistRepository.findByInvestorIdAndSchemeOptionId(investor.getId(), finalOption.getId()).orElse(null);
        }
        if (wl == null && finalOption == null) {
            wl = watchlistRepository.findByInvestorIdAndSchemeIdAndSchemeOptionIsNull(investor.getId(), finalScheme.getId()).orElse(null);
        }
        if (wl == null) {
            wl = new InvestorWatchlist();
            wl.setInvestor(investor);
            wl.setScheme(finalScheme);
            wl.setSchemeOption(finalOption);
        }

        if (req.reasonSaved() != null) {
            wl.setReasonSaved(clean(req.reasonSaved()));
        }
        if (req.investigationQuestion() != null) {
            wl.setInvestigationQuestion(clean(req.investigationQuestion()));
        }
        if (req.nextStep() != null) {
            wl.setNextStep(clean(req.nextStep()));
        }

        InvestorWatchlist saved = watchlistRepository.save(wl);
        return mapToWatchlistResponse(saved);
    }

    public WatchlistResponse updateWatchlistNoteByOption(String auth0Subject, Long schemeOptionId, WatchlistNoteRequest req) {
        validateNoteLength(req.reasonSaved(), "reasonSaved");
        validateNoteLength(req.investigationQuestion(), "investigationQuestion");
        validateNoteLength(req.nextStep(), "nextStep");

        Investor investor = getOrCreateInvestor(auth0Subject);
        InvestorWatchlist wl = watchlistRepository.findByInvestorIdAndSchemeOptionId(investor.getId(), schemeOptionId)
                .orElseThrow(() -> new IllegalArgumentException("Watchlist entry not found for schemeOptionId: " + schemeOptionId));

        wl.setReasonSaved(clean(req.reasonSaved()));
        wl.setInvestigationQuestion(clean(req.investigationQuestion()));
        wl.setNextStep(clean(req.nextStep()));

        InvestorWatchlist saved = watchlistRepository.save(wl);
        return mapToWatchlistResponse(saved);
    }

    public WatchlistResponse updateWatchlistNote(String auth0Subject, Long schemeId, WatchlistNoteRequest req) {
        validateNoteLength(req.reasonSaved(), "reasonSaved");
        validateNoteLength(req.investigationQuestion(), "investigationQuestion");
        validateNoteLength(req.nextStep(), "nextStep");

        Investor investor = getOrCreateInvestor(auth0Subject);
        List<InvestorWatchlist> entries = watchlistRepository.findByInvestorIdAndSchemeId(investor.getId(), schemeId);
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("Watchlist entry not found for schemeId: " + schemeId);
        }
        InvestorWatchlist wl = entries.get(0);

        wl.setReasonSaved(clean(req.reasonSaved()));
        wl.setInvestigationQuestion(clean(req.investigationQuestion()));
        wl.setNextStep(clean(req.nextStep()));

        InvestorWatchlist saved = watchlistRepository.save(wl);
        return mapToWatchlistResponse(saved);
    }

    public InvestorWatchlist addWatchlist(String auth0Subject, Long schemeId) {
        return addWatchlistEntity(auth0Subject, schemeId, null);
    }

    public InvestorWatchlist addWatchlistEntity(String auth0Subject, Long schemeId, Long schemeOptionId) {
        WatchlistRequest req = new WatchlistRequest(schemeId, schemeOptionId, null, null, null);
        WatchlistResponse resp = addWatchlistDto(auth0Subject, req);
        return watchlistRepository.findById(resp.id()).orElseThrow();
    }

    public void removeWatchlist(String auth0Subject, Long schemeId) {
        Investor investor = getOrCreateInvestor(auth0Subject);
        List<InvestorWatchlist> entries = watchlistRepository.findByInvestorIdAndSchemeId(investor.getId(), schemeId);
        entries.forEach(watchlistRepository::delete);
    }

    public void removeWatchlistByOption(String auth0Subject, Long schemeOptionId) {
        Investor investor = getOrCreateInvestor(auth0Subject);
        watchlistRepository.findByInvestorIdAndSchemeOptionId(investor.getId(), schemeOptionId)
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

    private String clean(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validateNoteLength(String val, String fieldName) {
        if (val != null && val.length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException(fieldName + " exceeds maximum allowed length of " + MAX_NOTE_LENGTH + " characters.");
        }
    }

    private WatchlistResponse mapToWatchlistResponse(InvestorWatchlist w) {
        Scheme scheme = w.getScheme();
        SchemeOption option = w.getSchemeOption();

        if (option == null && scheme != null) {
            List<SchemeOption> options = schemeOptionRepository.findByPlanSchemeId(scheme.getId());
            if (!options.isEmpty()) {
                option = options.get(0);
            }
        }

        BigDecimal latestNav = null;
        String latestNavDate = null;
        Double yukiraScore = null;
        String yukiraScoreStatus = null;

        Long optionId = option != null ? option.getId() : null;
        if (optionId != null) {
            Optional<NavObservation> navOpt = navObservationRepository.findLatestActiveObservation(optionId);
            if (navOpt.isPresent()) {
                latestNav = navOpt.get().getNavValue();
                latestNavDate = navOpt.get().getEffectiveDate() != null ? navOpt.get().getEffectiveDate().toString() : null;
            }

            List<AnalyticalScore> scores = analyticalScoreRepository.findLatestBySchemeOptionId(optionId);
            if (!scores.isEmpty()) {
                AnalyticalScore score = scores.get(0);
                yukiraScore = score.getScore() != null ? score.getScore().doubleValue() : null;
                yukiraScoreStatus = score.getStatus() != null ? score.getStatus() : "CANDIDATE";
            }
        }

        String schemeCode = scheme != null ? scheme.getCode() : (option != null && option.getPlan() != null && option.getPlan().getScheme() != null ? option.getPlan().getScheme().getCode() : "UNKNOWN");
        String schemeName = scheme != null ? scheme.getName() : (option != null && option.getPlan() != null && option.getPlan().getScheme() != null ? option.getPlan().getScheme().getName() : "Unknown Scheme");
        String amcName = (scheme != null && scheme.getAmc() != null) ? scheme.getAmc().getLegalName() : null;

        return new WatchlistResponse(
                w.getId(),
                scheme != null ? scheme.getId() : null,
                schemeCode,
                schemeName,
                amcName,
                optionId,
                option != null ? option.getOptionType() : null,
                option != null && option.getPlan() != null ? option.getPlan().getPlanType() : null,
                option != null ? option.getAmfiCode() : null,
                option != null ? option.getIsin() : null,
                latestNav,
                latestNavDate,
                yukiraScore,
                yukiraScoreStatus,
                w.getReasonSaved(),
                w.getInvestigationQuestion(),
                w.getNextStep(),
                w.getCreatedAt() != null ? w.getCreatedAt().toString() : null,
                w.getUpdatedAt() != null ? w.getUpdatedAt().toString() : null
        );
    }
}
