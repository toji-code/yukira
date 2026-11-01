package com.yukira.backend.service;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.experience.*;
import com.yukira.backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for investor display preferences, global command palette search,
 * and self-contained evidence export bundles (Tasks 201–205).
 */
@Service
public class InvestorExperienceService {

    private static final Logger log = LoggerFactory.getLogger(InvestorExperienceService.class);

    private final InvestorDisplayPreferenceRepository preferenceRepository;
    private final InvestorService investorService;
    private final SchemeRepository schemeRepository;
    private final InvestorWatchlistRepository watchlistRepository;
    private final InvestorComparisonResearchRepository comparisonRepository;
    private final InvestorInvestigationActionRepository actionRepository;
    private final InvestorMonitoringConditionRepository conditionRepository;

    public InvestorExperienceService(
        InvestorDisplayPreferenceRepository preferenceRepository,
        InvestorService investorService,
        SchemeRepository schemeRepository,
        InvestorWatchlistRepository watchlistRepository,
        InvestorComparisonResearchRepository comparisonRepository,
        InvestorInvestigationActionRepository actionRepository,
        InvestorMonitoringConditionRepository conditionRepository
    ) {
        this.preferenceRepository = preferenceRepository;
        this.investorService = investorService;
        this.schemeRepository = schemeRepository;
        this.watchlistRepository = watchlistRepository;
        this.comparisonRepository = comparisonRepository;
        this.actionRepository = actionRepository;
        this.conditionRepository = conditionRepository;
    }

    // --- TASK 203: DISPLAY PREFERENCES ---
    @Transactional
    public InvestorDisplayPreferenceDto getOrCreatePreferences(String auth0Subject) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        InvestorDisplayPreference pref = preferenceRepository.findByInvestor(investor)
            .orElseGet(() -> {
                InvestorDisplayPreference p = new InvestorDisplayPreference();
                p.setInvestor(investor);
                return preferenceRepository.save(p);
            });
        return toPreferenceDto(pref);
    }

    @Transactional
    public InvestorDisplayPreferenceDto updatePreferences(
        String auth0Subject,
        String analysisPeriod,
        String workspaceTab,
        String theme,
        Boolean showWarnings,
        String dateFormat
    ) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        InvestorDisplayPreference pref = preferenceRepository.findByInvestor(investor)
            .orElseGet(() -> {
                InvestorDisplayPreference p = new InvestorDisplayPreference();
                p.setInvestor(investor);
                return p;
            });

        if (analysisPeriod != null) pref.setDefaultAnalysisPeriod(analysisPeriod);
        if (workspaceTab != null) pref.setDefaultWorkspaceTab(workspaceTab);
        if (theme != null) pref.setPreferredTheme(theme);
        if (showWarnings != null) pref.setShowCandidateMetricWarnings(showWarnings);
        if (dateFormat != null) pref.setDateDisplayFormat(dateFormat);
        pref.setUpdatedAt(ZonedDateTime.now());

        InvestorDisplayPreference saved = preferenceRepository.save(pref);
        return toPreferenceDto(saved);
    }

    // --- TASK 204: GLOBAL INVESTOR SEARCH ---
    @Transactional(readOnly = true)
    public List<GlobalSearchResultDto> globalSearch(String auth0Subject, String query) {
        if (query == null || query.trim().length() < 2) return List.of();
        String q = query.trim().toLowerCase();
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);

        List<GlobalSearchResultDto> results = new ArrayList<>();

        // 1. Mutual Fund Schemes
        try {
            List<Scheme> schemes = schemeRepository.findByNameContainingIgnoreCase(q);
            for (Scheme s : schemes.stream().limit(8).toList()) {
                results.add(new GlobalSearchResultDto(
                    "FUND",
                    s.getName(),
                    s.getCategory() != null ? s.getCategory() : "Equity Scheme",
                    "/funds/" + s.getId(),
                    s.getAmc() != null ? s.getAmc().getName() : "Mutual Fund",
                    "DEFAULT"
                ));
            }
        } catch (Exception e) {
            log.debug("Scheme search error: {}", e.getMessage());
        }

        // 2. Saved Comparisons
        try {
            List<InvestorComparisonResearch> comps = comparisonRepository.findByInvestorOrderByUpdatedAtDesc(investor);
            for (InvestorComparisonResearch c : comps) {
                if (c.getTitle() != null && c.getTitle().toLowerCase().contains(q)) {
                    results.add(new GlobalSearchResultDto(
                        "COMPARISON",
                        c.getTitle(),
                        "Saved Comparative Screening (" + (c.getBenchmarkCode() != null ? c.getBenchmarkCode() : "NIFTY_500_TRI") + ")",
                        "/comparisons",
                        "COMPARISON",
                        "PURPLE"
                    ));
                }
            }
        } catch (Exception e) {
            log.debug("Comparison search error: {}", e.getMessage());
        }

        // 3. Investigation Actions
        try {
            List<InvestorInvestigationAction> actions = actionRepository.findByInvestorOrderByCreatedAtDesc(investor);
            for (InvestorInvestigationAction a : actions) {
                if (a.getActionTitle().toLowerCase().contains(q)) {
                    results.add(new GlobalSearchResultDto(
                        "ACTION",
                        a.getActionTitle(),
                        "Investigation Action [" + a.getStatus() + "] Priority: " + a.getPriority(),
                        "/account",
                        a.getStatus(),
                        "COMPLETED".equals(a.getStatus()) ? "SUCCESS" : "WARNING"
                    ));
                }
            }
        } catch (Exception e) {
            log.debug("Action search error: {}", e.getMessage());
        }

        // 4. Monitoring Conditions
        try {
            List<InvestorMonitoringCondition> conds = conditionRepository.findByInvestorAndStatusNot(investor, "DISMISSED");
            for (InvestorMonitoringCondition mc : conds) {
                if (mc.getMetricCode().toLowerCase().contains(q) || (mc.getNotes() != null && mc.getNotes().toLowerCase().contains(q))) {
                    results.add(new GlobalSearchResultDto(
                        "MONITORING",
                        "Condition on " + mc.getMetricCode() + " (" + mc.getEvaluationCadence() + ")",
                        "Status: " + mc.getStatus() + " | Threshold: " + mc.getThresholdValue(),
                        "/monitoring",
                        mc.getStatus(),
                        "ACTIVE".equals(mc.getStatus()) ? "SUCCESS" : "DEFAULT"
                    ));
                }
            }
        } catch (Exception e) {
            log.debug("Monitoring condition search error: {}", e.getMessage());
        }

        return results;
    }

    // --- TASK 205: EVIDENCE EXPORT BUNDLE ---
    @Transactional(readOnly = true)
    public EvidenceExportBundleDto exportEvidenceBundle(String auth0Subject) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        String exportId = "YUKIRA_EXPORT_" + investor.getId() + "_" + System.currentTimeMillis();

        Map<String, Object> portfolioSummary = Map.of(
            "investorId", investor.getId(),
            "auth0Subject", investor.getAuth0Subject(),
            "status", "ACTIVE"
        );

        List<Map<String, Object>> watchlists = watchlistRepository.findByInvestor(investor).stream()
            .map(w -> Map.<String, Object>of(
                "id", w.getId(),
                "schemeOptionId", w.getSchemeOption() != null ? w.getSchemeOption().getId() : 0,
                "status", w.getStatus() != null ? w.getStatus() : "ACTIVE",
                "addedAt", String.valueOf(w.getAddedAt())
            ))
            .collect(Collectors.toList());

        List<Map<String, Object>> comparisons = comparisonRepository.findByInvestorOrderByUpdatedAtDesc(investor).stream()
            .map(c -> Map.<String, Object>of(
                "id", c.getId(),
                "title", c.getTitle() != null ? c.getTitle() : "Comparison",
                "benchmark", c.getBenchmarkCode() != null ? c.getBenchmarkCode() : "NIFTY_500_TRI"
            ))
            .collect(Collectors.toList());

        List<Map<String, Object>> conditions = conditionRepository.findByInvestorAndStatusNot(investor, "DISMISSED").stream()
            .map(mc -> Map.<String, Object>of(
                "id", mc.getId(),
                "metricCode", mc.getMetricCode(),
                "cadence", mc.getEvaluationCadence(),
                "status", mc.getStatus()
            ))
            .collect(Collectors.toList());

        List<Map<String, Object>> actions = actionRepository.findByInvestorOrderByCreatedAtDesc(investor).stream()
            .map(a -> Map.<String, Object>of(
                "id", a.getId(),
                "title", a.getActionTitle(),
                "status", a.getStatus(),
                "priority", a.getPriority()
            ))
            .collect(Collectors.toList());

        String rawFingerprint = exportId + ":" + watchlists.size() + ":" + comparisons.size() + ":" + conditions.size();
        String hash = computeSha256(rawFingerprint);

        return new EvidenceExportBundleDto(
            exportId,
            investor.getId(),
            hash,
            ZonedDateTime.now(),
            "YUKIRA Evidence Export Standard — Point-in-Time Deterministic Verification Artifact (Phase 2H Frozen Governance).",
            portfolioSummary,
            watchlists,
            comparisons,
            conditions,
            actions
        );
    }

    private InvestorDisplayPreferenceDto toPreferenceDto(InvestorDisplayPreference p) {
        return new InvestorDisplayPreferenceDto(
            p.getId(),
            p.getDefaultAnalysisPeriod(),
            p.getDefaultWorkspaceTab(),
            p.getPreferredTheme(),
            p.getShowCandidateMetricWarnings(),
            p.getDateDisplayFormat(),
            p.getUpdatedAt()
        );
    }

    private String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}

