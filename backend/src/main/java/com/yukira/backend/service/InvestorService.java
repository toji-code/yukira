package com.yukira.backend.service;

import com.yukira.backend.controller.InvestorController;
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
import com.yukira.backend.domain.entity.InvestorSubscription;
import com.yukira.backend.domain.entity.SubscriptionPlan;
import com.yukira.backend.repository.InvestorSubscriptionRepository;
import com.yukira.backend.repository.SubscriptionPlanRepository;
import com.yukira.backend.domain.entity.InvestorResearchThesisHistory;
import com.yukira.backend.domain.entity.InvestorComparisonResearch;
import com.yukira.backend.dto.analysis.InvestorComparisonResearchDto;
import com.yukira.backend.dto.analysis.SaveComparisonResearchRequest;
import com.yukira.backend.dto.watchlist.InvestorResearchThesisHistoryDto;
import com.yukira.backend.repository.InvestorResearchThesisHistoryRepository;
import com.yukira.backend.repository.InvestorComparisonResearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class InvestorService {
    private static final Logger log = LoggerFactory.getLogger(InvestorService.class);
    private static final int MAX_NOTE_LENGTH = 1000;

    private final InvestorRepository investorRepository;
    private final InvestorWatchlistRepository watchlistRepository;
    private final InvestorPortfolioHoldingRepository portfolioRepository;
    private final SchemeOptionRepository schemeOptionRepository;
    private final SchemeRepository schemeRepository;
    private final NavObservationRepository navObservationRepository;

    @Autowired(required = false)
    private InvestorResearchThesisHistoryRepository thesisHistoryRepository;

    @Autowired(required = false)
    private InvestorComparisonResearchRepository comparisonResearchRepository;
    private final AnalyticalScoreRepository analyticalScoreRepository;
    private final InvestorSubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final com.yukira.backend.service.subscription.EntitlementService entitlementService;

    @org.springframework.beans.factory.annotation.Value("${yukira.auth.dev-fallback-enabled:true}")
    private boolean devFallbackEnabled;

    @Autowired
    public InvestorService(InvestorRepository investorRepository,
                           InvestorWatchlistRepository watchlistRepository,
                           InvestorPortfolioHoldingRepository portfolioRepository,
                           SchemeOptionRepository schemeOptionRepository,
                           SchemeRepository schemeRepository,
                           NavObservationRepository navObservationRepository,
                           AnalyticalScoreRepository analyticalScoreRepository,
                           InvestorSubscriptionRepository subscriptionRepository,
                           SubscriptionPlanRepository planRepository,
                           com.yukira.backend.service.subscription.EntitlementService entitlementService) {
        this.investorRepository = investorRepository;
        this.watchlistRepository = watchlistRepository;
        this.portfolioRepository = portfolioRepository;
        this.schemeOptionRepository = schemeOptionRepository;
        this.schemeRepository = schemeRepository;
        this.navObservationRepository = navObservationRepository;
        this.analyticalScoreRepository = analyticalScoreRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.entitlementService = entitlementService;
    }

    public InvestorService(InvestorRepository investorRepository,
                           InvestorWatchlistRepository watchlistRepository,
                           InvestorPortfolioHoldingRepository portfolioRepository,
                           SchemeOptionRepository schemeOptionRepository,
                           SchemeRepository schemeRepository,
                           NavObservationRepository navObservationRepository,
                           AnalyticalScoreRepository analyticalScoreRepository,
                           InvestorSubscriptionRepository subscriptionRepository,
                           SubscriptionPlanRepository planRepository) {
        this(investorRepository, watchlistRepository, portfolioRepository, schemeOptionRepository,
             schemeRepository, navObservationRepository, analyticalScoreRepository, subscriptionRepository, planRepository, null);
    }

    public InvestorService(InvestorRepository investorRepository,
                           InvestorWatchlistRepository watchlistRepository,
                           InvestorPortfolioHoldingRepository portfolioRepository,
                           SchemeOptionRepository schemeOptionRepository,
                           SchemeRepository schemeRepository,
                           NavObservationRepository navObservationRepository,
                           AnalyticalScoreRepository analyticalScoreRepository) {
        this(investorRepository, watchlistRepository, portfolioRepository, schemeOptionRepository,
             schemeRepository, navObservationRepository, analyticalScoreRepository, null, null, null);
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

    public InvestorController.AccountResponse getAccountSummary(String auth0Subject) {
        Investor investor = getOrCreateInvestor(auth0Subject);
        int savedFundsCount = watchlistRepository.findByInvestorId(investor.getId()).size();
        int holdingsCount = portfolioRepository.findByInvestorId(investor.getId()).size();
        String createdAtStr = investor.getCreatedAt() != null ? investor.getCreatedAt().toString() : null;

        SubscriptionPlan effectivePlan = entitlementService != null
                ? entitlementService.getEffectivePlan(investor)
                : null;
        String planId = effectivePlan != null ? effectivePlan.getId() : "FREE";
        String planName = effectivePlan != null ? effectivePlan.getName() : "Community Explorer";
        String entitlementStatus = entitlementService != null
                ? entitlementService.getEffectiveEntitlementStatus(investor)
                : "STANDARD";

        InvestorSubscription sub = subscriptionRepository != null
                ? subscriptionRepository.findByInvestorId(investor.getId()).orElse(null)
                : null;
        String subscriptionStatus = sub != null ? sub.getStatus() : "ACTIVE";

        return new InvestorController.AccountResponse(
                investor.getId(),
                investor.getAuth0Subject(),
                createdAtStr,
                savedFundsCount,
                holdingsCount,
                planId,
                planName,
                entitlementStatus,
                subscriptionStatus
        );
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

        // Authoritative Entitlement Check: enforce plan capacity limit
        if (entitlementService != null) {
            int currentCount = watchlistRepository.findByInvestorId(investor.getId()).size();
            if (!entitlementService.canAddWatchlistItem(investor, currentCount)) {
                int maxAllowed = entitlementService.getMaxWatchlistItems(investor);
                SubscriptionPlan plan = entitlementService.getEffectivePlan(investor);
                String planName = plan != null ? plan.getName() : "Community Explorer";
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        "Saved research capacity reached for " + planName + " (" + maxAllowed + " funds). Please upgrade your subscription on the Pricing or Account page to track additional funds."
                );
            }
        } else if (subscriptionRepository != null) {
            int currentCount = watchlistRepository.findByInvestorId(investor.getId()).size();
            InvestorSubscription sub = subscriptionRepository.findByInvestorId(investor.getId()).orElse(null);
            int maxAllowed = (sub != null && sub.getPlan() != null) ? sub.getPlan().getMaxSavedFunds() : 25;
            if (currentCount >= maxAllowed) {
                String planName = sub != null && sub.getPlan() != null ? sub.getPlan().getName() : "Community Explorer";
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        "Saved research capacity reached for " + planName + " (" + maxAllowed + " funds). Please upgrade your subscription on the Pricing or Account page to track additional funds."
                );
            }
        }

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
        if (req.thesisStatus() != null && !req.thesisStatus().isBlank()) {
            wl.setThesisStatus(clean(req.thesisStatus()));
        }
        if (req.associatedFindingId() != null) {
            wl.setAssociatedFindingId(clean(req.associatedFindingId()));
        }

        InvestorWatchlist saved = watchlistRepository.save(wl);
        recordThesisHistory(saved);
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
        if (req.thesisStatus() != null && !req.thesisStatus().isBlank()) {
            wl.setThesisStatus(clean(req.thesisStatus()));
        }
        if (req.associatedFindingId() != null) {
            wl.setAssociatedFindingId(clean(req.associatedFindingId()));
        }

        InvestorWatchlist saved = watchlistRepository.save(wl);
        recordThesisHistory(saved);
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
        if (req.thesisStatus() != null && !req.thesisStatus().isBlank()) {
            wl.setThesisStatus(clean(req.thesisStatus()));
        }
        if (req.associatedFindingId() != null) {
            wl.setAssociatedFindingId(clean(req.associatedFindingId()));
        }

        InvestorWatchlist saved = watchlistRepository.save(wl);
        recordThesisHistory(saved);
        return mapToWatchlistResponse(saved);
    }

    /**
     * Records a durable historical snapshot of an investor's research thesis (Task 120, 121).
     */
    private void recordThesisHistory(InvestorWatchlist saved) {
        if (thesisHistoryRepository == null || saved == null) return;
        try {
            int nextVersion = 1;
            List<InvestorResearchThesisHistory> history = thesisHistoryRepository.findByWatchlistIdOrderByThesisVersionDesc(saved.getId());
            if (history != null && !history.isEmpty()) {
                nextVersion = (history.get(0).getThesisVersion() != null ? history.get(0).getThesisVersion() : 1) + 1;
            }
            InvestorResearchThesisHistory entry = new InvestorResearchThesisHistory(
                saved,
                saved.getInvestor(),
                saved.getScheme(),
                saved.getSchemeOption(),
                nextVersion,
                saved.getThesisStatus() != null ? saved.getThesisStatus() : "ACTIVE_HYPOTHESIS",
                saved.getReasonSaved(),
                saved.getInvestigationQuestion(),
                saved.getNextStep(),
                saved.getAssociatedFindingId(),
                null,
                null
            );
            thesisHistoryRepository.save(entry);
        } catch (Exception ex) {
            log.warn("Failed to record thesis version history for watchlist {}: {}", saved.getId(), ex.getMessage());
        }
    }

    /**
     * Retrieves the complete version history of an investor's research thesis (Task 120).
     */
    public List<InvestorResearchThesisHistoryDto> getThesisHistory(String auth0Subject, Long watchlistId) {
        if (thesisHistoryRepository == null || watchlistId == null) return Collections.emptyList();
        Investor investor = getOrCreateInvestor(auth0Subject);
        Optional<InvestorWatchlist> wlOpt = watchlistRepository.findById(watchlistId);
        if (wlOpt.isEmpty() || !investor.getId().equals(wlOpt.get().getInvestor().getId())) {
            throw new IllegalArgumentException("Watchlist entry not found or unauthorized.");
        }
        return thesisHistoryRepository.findByWatchlistIdOrderByThesisVersionDesc(watchlistId)
            .stream()
            .map(h -> new InvestorResearchThesisHistoryDto(
                h.getId(),
                h.getWatchlist().getId(),
                h.getInvestor().getId(),
                h.getScheme() != null ? h.getScheme().getId() : null,
                h.getSchemeOption() != null ? h.getSchemeOption().getId() : null,
                h.getThesisVersion(),
                h.getThesisStatus(),
                h.getReasonSaved(),
                h.getInvestigationQuestion(),
                h.getNextStep(),
                h.getAssociatedFindingId(),
                h.getEvidenceContext(),
                h.getReconsiderationConditions(),
                h.getCreatedAt()
            ))
            .toList();
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

        boolean isNew = holding.getId() == null;
        if (isNew && entitlementService != null) {
            int currentCount = portfolioRepository.findByInvestorId(investor.getId()).size();
            if (!entitlementService.canAddPortfolioHolding(investor, currentCount)) {
                int maxAllowed = entitlementService.getMaxPortfolioHoldings(investor);
                SubscriptionPlan plan = entitlementService.getEffectivePlan(investor);
                String planName = plan != null ? plan.getName() : "Community Explorer";
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        "Portfolio capacity reached for " + planName + " (" + maxAllowed + " holdings). Please upgrade your subscription on the Pricing or Account page to track additional positions."
                );
            }
        }

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
        String category = scheme != null ? scheme.getCategory() : null;

        // Holding Linkage Resolution: exact schemeOptionId matching & variant distinction
        Boolean isHeldInPortfolio = false;
        Long portfolioHoldingId = null;
        BigDecimal holdingUnits = null;
        String heldDifferentVariantNotice = null;

        Long investorId = w.getInvestor() != null ? w.getInvestor().getId() : null;
        if (investorId != null && optionId != null) {
            Optional<InvestorPortfolioHolding> holdingOpt = portfolioRepository.findByInvestorIdAndSchemeOptionId(investorId, optionId);
            if (holdingOpt.isPresent()) {
                isHeldInPortfolio = true;
                portfolioHoldingId = holdingOpt.get().getId();
                holdingUnits = holdingOpt.get().getUnits();
            } else {
                Long targetSchemeId = scheme != null ? scheme.getId() : (option != null && option.getPlan() != null && option.getPlan().getScheme() != null ? option.getPlan().getScheme().getId() : null);
                if (targetSchemeId != null) {
                    List<InvestorPortfolioHolding> investorHoldings = portfolioRepository.findByInvestorId(investorId);
                    for (InvestorPortfolioHolding h : investorHoldings) {
                        if (h.getSchemeOption() != null && h.getSchemeOption().getPlan() != null && h.getSchemeOption().getPlan().getScheme() != null) {
                            if (java.util.Objects.equals(h.getSchemeOption().getPlan().getScheme().getId(), targetSchemeId)) {
                                SchemeOption otherOpt = h.getSchemeOption();
                                String otherPlan = otherOpt.getPlan() != null && otherOpt.getPlan().getPlanType() != null ? otherOpt.getPlan().getPlanType() : "Unknown Plan";
                                String otherOptType = otherOpt.getOptionType() != null ? otherOpt.getOptionType() : "Unknown Option";
                                heldDifferentVariantNotice = "Held in portfolio under different variant: " + otherPlan + " " + otherOptType + " (Option #" + otherOpt.getId() + ")";
                                break;
                            }
                        }
                    }
                }
            }
        }

        return new WatchlistResponse(
                w.getId(),
                scheme != null ? scheme.getId() : null,
                schemeCode,
                schemeName,
                amcName,
                category,
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
                w.getUpdatedAt() != null ? w.getUpdatedAt().toString() : null,
                isHeldInPortfolio,
                portfolioHoldingId,
                holdingUnits,
                heldDifferentVariantNotice,
                w.getThesisStatus() != null ? w.getThesisStatus() : "ACTIVE_HYPOTHESIS",
                w.getAssociatedFindingId()
        );
    }

    public InvestorComparisonResearchDto saveComparisonResearch(String auth0Subject, SaveComparisonResearchRequest req) {
        Investor investor = getOrCreateInvestor(auth0Subject);
        if (comparisonResearchRepository == null) {
            throw new IllegalStateException("Comparison research repository not configured");
        }
        InvestorComparisonResearch entity = new InvestorComparisonResearch();
        entity.setInvestor(investor);
        entity.setComparisonTitle(req.comparisonTitle() != null && !req.comparisonTitle().isBlank() ? req.comparisonTitle() : "Saved Comparison");
        String idsCsv = req.schemeOptionIds() != null
                ? req.schemeOptionIds().stream().map(String::valueOf).collect(Collectors.joining(","))
                : "";
        entity.setSchemeOptionIdsCsv(idsCsv);
        entity.setAsOfDate(req.asOfDate() != null ? req.asOfDate() : LocalDate.now());
        entity.setKnowledgeCutoff(req.knowledgeCutoff() != null ? req.knowledgeCutoff() : OffsetDateTime.now());
        entity.setBenchmarkCode(req.benchmarkCode());
        entity.setCompatibilityStatus(req.compatibilityStatus() != null ? req.compatibilityStatus() : "COMPATIBLE");
        entity.setMetricsSummaryJson(req.metricsSummaryJson());
        entity.setInvestorThesis(req.investorThesis());
        entity.setInvestorNotes(req.investorNotes());
        entity.setAssociatedFindingId(req.associatedFindingId());

        InvestorComparisonResearch saved = comparisonResearchRepository.save(entity);
        return mapToComparisonResearchDto(saved);
    }

    public List<InvestorComparisonResearchDto> getSavedComparisons(String auth0Subject) {
        Investor investor = getOrCreateInvestor(auth0Subject);
        if (comparisonResearchRepository == null) {
            return Collections.emptyList();
        }
        return comparisonResearchRepository.findByInvestorIdOrderByCreatedAtDesc(investor.getId())
                .stream()
                .map(this::mapToComparisonResearchDto)
                .collect(Collectors.toList());
    }

    private InvestorComparisonResearchDto mapToComparisonResearchDto(InvestorComparisonResearch r) {
        List<Long> ids = new ArrayList<>();
        if (r.getSchemeOptionIdsCsv() != null && !r.getSchemeOptionIdsCsv().isBlank()) {
            for (String s : r.getSchemeOptionIdsCsv().split(",")) {
                try {
                    ids.add(Long.parseLong(s.trim()));
                } catch (NumberFormatException ignored) {}
            }
        }
        return new InvestorComparisonResearchDto(
                r.getId(),
                r.getInvestor() != null ? r.getInvestor().getId() : null,
                r.getComparisonTitle(),
                ids,
                r.getAsOfDate(),
                r.getKnowledgeCutoff(),
                r.getBenchmarkCode(),
                r.getCompatibilityStatus(),
                r.getMetricsSummaryJson(),
                r.getInvestorThesis(),
                r.getInvestorNotes(),
                r.getAssociatedFindingId(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}
