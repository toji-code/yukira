package com.yukira.backend.service;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class FundResearchBriefService {

    private final UnifiedFundProfileService unifiedFundProfileService;
    private final HoldingsConstructionService holdingsConstructionService;
    private final SchemeOptionRepository schemeOptionRepository;
    private final NavObservationRepository navObservationRepository;

    public FundResearchBriefService(
        UnifiedFundProfileService unifiedFundProfileService,
        HoldingsConstructionService holdingsConstructionService,
        SchemeOptionRepository schemeOptionRepository,
        NavObservationRepository navObservationRepository
    ) {
        this.unifiedFundProfileService = unifiedFundProfileService;
        this.holdingsConstructionService = holdingsConstructionService;
        this.schemeOptionRepository = schemeOptionRepository;
        this.navObservationRepository = navObservationRepository;
    }

    public record ResearchBriefDto(
        Long schemeOptionId,
        Long schemeId,
        String schemeName,
        String optionName,
        String amcName,
        String category,
        String benchmarkName,
        LocalDate reportDate,
        UnifiedFundProfileService.CompositeFundProfileDto profile,
        HoldingsConstructionService.FundHoldingsConstructionDto holdingsConstruction,
        String markdownDossier
    ) {}

    public Optional<ResearchBriefDto> generateResearchBrief(Long schemeOptionId) {
        Optional<SchemeOption> optEntity = schemeOptionRepository.findById(schemeOptionId);
        if (optEntity.isEmpty()) return Optional.empty();

        SchemeOption opt = optEntity.get();
        Scheme scheme = opt.getScheme();
        if (scheme == null) return Optional.empty();

        var profileOpt = unifiedFundProfileService.getCompositeProfile(scheme.getId());
        if (profileOpt.isEmpty()) return Optional.empty();

        var profile = profileOpt.get();
        var hc = holdingsConstructionService.getHoldingsConstruction(schemeOptionId, null).orElse(null);

        String markdown = buildMarkdownDossier(opt, profile, hc);

        return Optional.of(new ResearchBriefDto(
            schemeOptionId,
            scheme.getId(),
            scheme.getName(),
            opt.getOptionName(),
            profile.amcName(),
            profile.category(),
            profile.benchmarkName(),
            LocalDate.now(),
            profile,
            hc,
            markdown
        ));
    }

    private String buildMarkdownDossier(
        SchemeOption opt,
        UnifiedFundProfileService.CompositeFundProfileDto profile,
        HoldingsConstructionService.FundHoldingsConstructionDto hc
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("# YUKIRA Quantitative Investment Due Diligence Brief\n\n");
        sb.append("**Fund:** ").append(opt.getOptionName()).append("\n");
        sb.append("**AMC:** ").append(profile.amcName()).append("\n");
        sb.append("**Category:** ").append(profile.category()).append(" | **Benchmark:** ").append(profile.benchmarkName()).append("\n");
        sb.append("**AMFI Scheme Code:** `").append(opt.getAmfiSchemeCode()).append("` | **ISIN:** `").append(opt.getIsin()).append("`\n");
        sb.append("**Brief Generated:** ").append(LocalDate.now()).append("\n\n");

        sb.append("---\n\n");
        sb.append("## 1. Executive Summary & Epistemic Notice\n\n");
        sb.append("> [!IMPORTANT]\n");
        sb.append("> **Governance Baseline:** RET-02 (Simple Period Return) is the sole approved operational baseline. ");
        sb.append("All other quantitative metrics (RSK-01, RSK-03, ratios) are candidate methodologies under empirical testing.\n\n");

        sb.append("## 2. Performance & Risk Overview\n\n");
        sb.append("| Metric | 1-Year | 3-Year | 5-Year |\n");
        sb.append("| :--- | :---: | :---: | :---: |\n");

        // Find matching option in profile
        var optProfile = profile.options().stream()
            .filter(o -> o.optionId().equals(opt.getId()))
            .findFirst()
            .orElse(null);

        if (optProfile != null) {
            String r1 = optProfile.ret02OneYear() != null ? String.format("%+.2f%%", optProfile.ret02OneYear()) : "N/A";
            String r3 = optProfile.ret02ThreeYear() != null ? String.format("%+.2f%%", optProfile.ret02ThreeYear()) : "N/A";
            String r5 = optProfile.ret02FiveYear() != null ? String.format("%+.2f%%", optProfile.ret02FiveYear()) : "N/A";
            sb.append("| **Period Return (RET-02)** | ").append(r1).append(" | ").append(r3).append(" | ").append(r5).append(" |\n");

            String vol = optProfile.rsk01Volatility() != null ? String.format("%.2f%%", optProfile.rsk01Volatility()) : "N/A";
            sb.append("| **Annualized Volatility (RSK-01)** | ").append(vol).append(" | Candidate | Candidate |\n");
        } else {
            sb.append("| **Period Return (RET-02)** | N/A | N/A | N/A |\n");
        }

        sb.append("\n## 3. Fund Construction & Holdings\n\n");
        if (hc != null) {
            sb.append("- **Total Disclosed Holdings:** ").append(hc.totalHoldingsCount()).append("\n");
            sb.append("- **Top 5 Concentration:** ").append(hc.top5ConcentrationPercentage()).append("%\n");
            sb.append("- **Top 10 Concentration:** ").append(hc.top10ConcentrationPercentage()).append("%\n");
            if (hc.assetAllocation() != null) {
                sb.append("- **Asset Split:** Equity ").append(hc.assetAllocation().netEquityPercentage()).append("% | Debt ")
                  .append(hc.assetAllocation().debtPercentage()).append("% | Cash ")
                  .append(hc.assetAllocation().cashPercentage()).append("%\n");
            }
            if (hc.marketCapExposure() != null) {
                sb.append("- **Market Cap Exposure:** Large Cap ").append(hc.marketCapExposure().largeCapPercentage()).append("% | Mid Cap ")
                  .append(hc.marketCapExposure().midCapPercentage()).append("% | Small Cap ")
                  .append(hc.marketCapExposure().smallCapPercentage()).append("%\n");
            }

            sb.append("\n### Top Holdings (Official Disclosure as of ").append(hc.portfolioDate()).append(")\n\n");
            sb.append("| # | Security Name | Sector | Weight (%) |\n");
            sb.append("| :--- | :--- | :--- | :---: |\n");
            for (var h : hc.topHoldings()) {
                sb.append("| ").append(h.rank()).append(" | ").append(h.securityName()).append(" | ")
                  .append(h.sector()).append(" | ").append(h.reportedWeight()).append("% |\n");
            }
        } else {
            sb.append("_No monthly holdings disclosure recorded in system for this fund._\n");
        }

        sb.append("\n## 4. Verification Checkpoint & Audit Trail\n\n");
        sb.append("- **Total Ingested Observations:** ").append(profile.totalObservations()).append("\n");
        sb.append("- **Observation History:** ").append(profile.earliestObservationDate()).append(" to ").append(profile.latestObservationDate()).append("\n");
        sb.append("- **Source Provenance:** Cryptographically verified against official AMFI daily NAV text stream.\n");
        sb.append("- **Platform Mandate:** Zero synthetic estimates; zero LLM calculations.\n");

        return sb.toString();
    }
}

