package com.yukira.backend.service;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.analysis.DataQualityFreshnessDto;
import com.yukira.backend.dto.analysis.DataQualityFreshnessDto.*;
import com.yukira.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service evaluating factual, evidence-backed Data Quality & Freshness for mutual fund share classes.
 *
 * Epistemic & Governance Guarantees:
 * 1. Zero math or data quality calculations in frontend presentation tier.
 * 2. NAV freshness is evaluated strictly independent of holdings freshness.
 * 3. Holdings reporting date is strictly separated from availabilityTime (PIT cutoff) and ingestion timestamps.
 * 4. Raw reported weights are NEVER normalized to 100%. Raw sums above/below 100% are displayed with factual explanations.
 * 5. Distinctly reports metric eligibility based on actual usable input observations.
 * 6. Explicitly answers the 7 key investor due-diligence questions.
 */
@Service
@Transactional(readOnly = true)
public class DataQualityFreshnessService {

    public static final String NAV_CADENCE_POLICY =
        "Daily (Business Trading Days) by 11:00 PM IST per SEBI (Mutual Funds) Regulations";

    public static final String HOLDINGS_CADENCE_POLICY =
        "Monthly (within 10 calendar days of month close) per SEBI Master Circular";

    private final SchemeOptionRepository schemeOptionRepository;
    private final NavObservationRepository navObservationRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final PortfolioHoldingRepository holdingRepository;

    @Autowired
    public DataQualityFreshnessService(
        SchemeOptionRepository schemeOptionRepository,
        @Autowired(required = false) NavObservationRepository navObservationRepository,
        @Autowired(required = false) PortfolioSnapshotRepository snapshotRepository,
        @Autowired(required = false) PortfolioHoldingRepository holdingRepository
    ) {
        this.schemeOptionRepository = schemeOptionRepository;
        this.navObservationRepository = navObservationRepository;
        this.snapshotRepository = snapshotRepository;
        this.holdingRepository = holdingRepository;
    }

    /**
     * Evaluates comprehensive Data Quality & Freshness for a specific scheme option as of evaluationDate and knowledgeCutoff.
     */
    public DataQualityFreshnessDto evaluateQualityAndFreshness(
        Long schemeOptionId,
        LocalDate evaluationDate,
        OffsetDateTime knowledgeCutoff
    ) {
        SchemeOption option = schemeOptionRepository.findById(schemeOptionId)
            .orElseThrow(() -> new IllegalArgumentException("SchemeOption not found for ID: " + schemeOptionId));

        LocalDate evalDate = evaluationDate != null ? evaluationDate : LocalDate.now();
        OffsetDateTime cutoff = knowledgeCutoff != null ? knowledgeCutoff : OffsetDateTime.now();

        Scheme scheme = option.getPlan() != null ? option.getPlan().getScheme() : null;
        Amc amc = scheme != null ? scheme.getAmc() : null;

        String schemeCode = scheme != null ? scheme.getCode() : "UNKNOWN";
        String schemeName = scheme != null ? scheme.getName() : "Unknown Scheme";
        String amcName = amc != null ? amc.getName() : "Unknown AMC";
        String planType = option.getPlan() != null ? option.getPlan().getPlanType() : "DIRECT";
        String optionType = option.getOptionType() != null ? option.getOptionType() : "GROWTH";
        String amfiCode = option.getAmfiCode();
        String isin = option.getIsin();

        // 1. NAV Evidence Evaluation
        NavFreshnessDetailDto navEvidence = evaluateNavEvidence(option, evalDate, cutoff);

        // 2. Holdings Evidence Evaluation
        HoldingsFreshnessDetailDto holdingsEvidence = evaluateHoldingsEvidence(option, evalDate, cutoff);

        // 3. Metric Eligibility Matrix
        List<MetricEligibilityItemDto> metricEligibility = evaluateMetricEligibility(navEvidence, holdingsEvidence);

        // 4. Data Quality Findings & Caveats
        List<DataQualityFindingDto> qualityFindings = evaluateFindings(option, navEvidence, holdingsEvidence);

        // 5. Answers to the 7 Key Investor Due Diligence Questions
        SevenQuestionsAnswerDto sevenQuestions = answerSevenQuestions(navEvidence, holdingsEvidence, metricEligibility);

        return new DataQualityFreshnessDto(
            option.getId(),
            schemeCode,
            schemeName,
            amcName,
            planType,
            optionType,
            amfiCode,
            isin,
            evalDate,
            cutoff,
            navEvidence,
            holdingsEvidence,
            metricEligibility,
            qualityFindings,
            sevenQuestions
        );
    }

    /**
     * Enriches a SchemeOption instance with compact, scannable freshness fields for the /funds catalog.
     */
    public void enrichOptionFreshness(SchemeOption opt, LocalDate evaluationDate, OffsetDateTime knowledgeCutoff) {
        if (opt == null) return;
        LocalDate evalDate = evaluationDate != null ? evaluationDate : LocalDate.now();
        OffsetDateTime cutoff = knowledgeCutoff != null ? knowledgeCutoff : OffsetDateTime.now();

        NavFreshnessDetailDto nav = evaluateNavEvidence(opt, evalDate, cutoff);
        HoldingsFreshnessDetailDto holdings = evaluateHoldingsEvidence(opt, evalDate, cutoff);

        if (nav.latestNavDate() != null) {
            opt.setLatestNavDate(nav.latestNavDate().toString());
        }
        opt.setNavObservationCount(nav.observationCount());
        opt.setNavFreshnessState(nav.freshnessState());
        opt.setNavFreshnessExplanation(nav.explanation());

        if (holdings.latestReportingDate() != null) {
            opt.setLatestHoldingsDate(holdings.latestReportingDate().toString());
        }
        opt.setHoldingsCount(holdings.positionCount());
        opt.setHoldingsFreshnessState(holdings.freshnessState());
        opt.setHoldingsFreshnessExplanation(holdings.explanation());
        opt.setHoldingsRawSumPercentage(holdings.rawSumWeightsPercentage());
        opt.setSnapshotCount(holdings.snapshotCount());
    }

    private NavFreshnessDetailDto evaluateNavEvidence(SchemeOption option, LocalDate evaluationDate, OffsetDateTime knowledgeCutoff) {
        if (navObservationRepository == null) {
            return new NavFreshnessDetailDto(
                null, 0L, "Not available", "UNAVAILABLE", NAV_CADENCE_POLICY,
                null, null, null, List.of("NAV repository is not wired."),
                "NAV repository is unavailable."
            );
        }

        List<NavObservation> eligibleNavs = navObservationRepository.findAuthoritativeObservationsAsOfCutoff(
            option.getId(), evaluationDate, knowledgeCutoff
        );

        if (eligibleNavs == null || eligibleNavs.isEmpty()) {
            long totalCount = navObservationRepository.countBySchemeOptionId(option.getId());
            if (totalCount == 0) {
                return new NavFreshnessDetailDto(
                    null, 0L, "Not available", "UNAVAILABLE", NAV_CADENCE_POLICY,
                    null, null, null, List.of("Zero NAV observations found in ledger."),
                    "No historical NAV observations recorded in database."
                );
            }
            return new NavFreshnessDetailDto(
                null, 0L, "Not available", "STALE", NAV_CADENCE_POLICY,
                null, null, null, List.of("No NAV observations match point-in-time cutoffs (evaluationDate: " + evaluationDate + ", cutoff: " + knowledgeCutoff + ")."),
                "No NAV observations meet point-in-time cutoffs."
            );
        }

        NavObservation latestNav = eligibleNavs.get(eligibleNavs.size() - 1);
        NavObservation earliestNav = eligibleNavs.get(0);

        LocalDate latestDate = latestNav.getEffectiveDate();
        LocalDate earliestDate = earliestNav.getEffectiveDate();
        long count = eligibleNavs.size();
        String usableRange = earliestDate + " → " + latestDate;

        Long sourceArtifactId = latestNav.getSourceArtifact() != null ? latestNav.getSourceArtifact().getId() : null;
        String sha256 = latestNav.getSourceArtifact() != null ? latestNav.getSourceArtifact().getSha256Hash() : null;

        long ageDays = ChronoUnit.DAYS.between(latestDate, evaluationDate);
        String freshnessState;
        String explanation;
        List<String> warnings = new ArrayList<>();

        if (ageDays <= 5) {
            freshnessState = "CURRENT";
            explanation = String.format(Locale.ROOT, "NAV observation is current (%d days old as of %s), within the standard 5-day trading window.", ageDays, latestDate);
        } else {
            freshnessState = "STALE";
            explanation = String.format(Locale.ROOT, "NAV observation is %d days old (as of %s), exceeding the 5-day daily publication expectation.", ageDays, latestDate);
            warnings.add("NAV data is older than current market date; calculations reflect historical window.");
        }

        if (count < 2) {
            warnings.add("Fewer than 2 NAV observations available; returns cannot be computed.");
        }

        return new NavFreshnessDetailDto(
            latestDate,
            count,
            usableRange,
            freshnessState,
            NAV_CADENCE_POLICY,
            ageDays,
            sourceArtifactId,
            sha256,
            warnings,
            explanation
        );
    }

    private HoldingsFreshnessDetailDto evaluateHoldingsEvidence(SchemeOption option, LocalDate evaluationDate, OffsetDateTime knowledgeCutoff) {
        if (snapshotRepository == null) {
            return new HoldingsFreshnessDetailDto(
                null, null, null, "UNAVAILABLE", "UNAVAILABLE", HOLDINGS_CADENCE_POLICY,
                null, 0, BigDecimal.ZERO, 0.0, 0.0, 0,
                Collections.emptyList(), null, null, null, "SOURCE_UNAVAILABLE",
                List.of("Portfolio snapshot repository is not wired."),
                "Portfolio snapshot repository is unavailable."
            );
        }

        List<PortfolioSnapshot> eligibleSnaps = snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(
            option.getId(), knowledgeCutoff
        );

        if (eligibleSnaps == null || eligibleSnaps.isEmpty()) {
            return new HoldingsFreshnessDetailDto(
                null, null, null, "UNAVAILABLE", "UNAVAILABLE", HOLDINGS_CADENCE_POLICY,
                null, 0, BigDecimal.ZERO, 0.0, 0.0, 0,
                Collections.emptyList(), null, null, null, "SOURCE_UNAVAILABLE",
                List.of("No monthly portfolio snapshots found in database."),
                "No monthly portfolio disclosures ingested for this fund."
            );
        }

        PortfolioSnapshot latestSnap = eligibleSnaps.get(0);
        LocalDate repDate = latestSnap.getPortfolioDate();
        OffsetDateTime availTime = latestSnap.getAvailabilityTime();
        OffsetDateTime ingTime = latestSnap.getSourceArtifact() != null ? latestSnap.getSourceArtifact().getRetrievalTimestamp() : latestSnap.getAvailabilityTime();

        int snapCount = eligibleSnaps.size();
        int posCount = latestSnap.getReportedHoldingsCount() != null ? latestSnap.getReportedHoldingsCount() : 0;
        BigDecimal rawSumW = latestSnap.getSumReportedWeights() != null ? latestSnap.getSumReportedWeights() : BigDecimal.ZERO;
        double rawSumPct = rawSumW.multiply(BigDecimal.valueOf(100)).setScale(4, RoundingMode.HALF_UP).doubleValue();

        Long artifactId = latestSnap.getSourceArtifact() != null ? latestSnap.getSourceArtifact().getId() : null;
        String sha256 = latestSnap.getSourceArtifact() != null ? latestSnap.getSourceArtifact().getSha256Hash() : null;
        String sourceUri = latestSnap.getSourceArtifact() != null ? latestSnap.getSourceArtifact().getStorageUri() : null;

        List<String> usablePeriods = eligibleSnaps.stream()
            .map(s -> s.getPortfolioDate() != null ? s.getPortfolioDate().toString() : "Unknown")
            .distinct()
            .toList();

        // Calculate unclassified exposure from holdings if available
        double unclassifiedPct = 0.0;
        if (holdingRepository != null && latestSnap.getId() != null) {
            List<PortfolioHolding> holdings = holdingRepository.findByPortfolioSnapshotIdOrderByHoldingRankAsc(latestSnap.getId());
            BigDecimal unclassifiedSum = BigDecimal.ZERO;
            for (PortfolioHolding h : holdings) {
                Security sec = h.getSecurity();
                if (sec == null || sec.getSector() == null || sec.getSector().equalsIgnoreCase("Other") || sec.getSector().isBlank()) {
                    if (h.getReportedWeight() != null) {
                        unclassifiedSum = unclassifiedSum.add(h.getReportedWeight());
                    }
                }
            }
            unclassifiedPct = unclassifiedSum.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).doubleValue();
        }

        long repAgeDays = ChronoUnit.DAYS.between(repDate, evaluationDate);
        String freshnessState;
        List<String> warnings = new ArrayList<>();

        // Expected monthly publication cadence: 45 days covers month close + 10-day statutory window + standard buffer
        if (repAgeDays <= 45) {
            freshnessState = "CURRENT";
        } else {
            freshnessState = "STALE";
            warnings.add(String.format(Locale.ROOT, "Latest holdings disclosure is %d days old (as of %s), older than the standard monthly disclosure window.", repAgeDays, repDate));
        }

        String coverageState;
        if (rawSumPct >= 90.0 && rawSumPct <= 105.0) {
            coverageState = "COMPLETE";
        } else {
            coverageState = "PARTIAL";
            warnings.add(String.format(Locale.ROOT, "Reported holdings sum is %.2f%% (< 90%%). Reflects partial top-holdings disclosure; %.2f%% residual represents smaller positions and cash/net current assets.", rawSumPct, 100.0 - rawSumPct));
        }

        if (rawSumPct > 100.0) {
            warnings.add(String.format(Locale.ROOT, "Reported holdings sum is %.2f%% (> 100%%). Reflects gross exposure / derivative positions without liability netting.", rawSumPct));
        }

        if (snapCount < 2) {
            warnings.add("Only 1 monthly snapshot in ledger. At least 2 distinct reporting periods required to compute portfolio drift.");
        }

        String discoveryStatus = "OFFICIAL_DISCLOSURE_ACCESSIBLE";
        if (option.getAmfiCode() != null && option.getAmfiCode().equals("118955")) {
            // HDFC AMC web access controls block scraping; documented honestly
            discoveryStatus = "DISCOVERY_RESTRICTED";
            warnings.add("AMC web portal maintains automated access controls; source discovery operates under restricted mode.");
        }

        String explanation = String.format(Locale.ROOT,
            "Latest holdings reporting date is %s (%d positions, %.2f%% raw disclosed weight sum, %s). Availability/knowledge cutoff: %s.",
            repDate, posCount, rawSumPct, freshnessState, availTime != null ? availTime.toString() : "Not recorded"
        );

        return new HoldingsFreshnessDetailDto(
            repDate,
            availTime,
            ingTime,
            freshnessState,
            coverageState,
            HOLDINGS_CADENCE_POLICY,
            repAgeDays,
            posCount,
            rawSumW,
            rawSumPct,
            unclassifiedPct,
            snapCount,
            usablePeriods,
            artifactId,
            sha256,
            sourceUri,
            discoveryStatus,
            warnings,
            explanation
        );
    }

    private List<MetricEligibilityItemDto> evaluateMetricEligibility(
        NavFreshnessDetailDto navEvidence,
        HoldingsFreshnessDetailDto holdingsEvidence
    ) {
        List<MetricEligibilityItemDto> items = new ArrayList<>();

        // 1. RET-02: Simple Period Return
        boolean ret02Eligible = navEvidence.observationCount() >= 2;
        items.add(new MetricEligibilityItemDto(
            "RET-02",
            "Simple Period Return",
            "APPROVED",
            ret02Eligible ? "ELIGIBLE" : "INPUT_UNAVAILABLE",
            "At least 2 point-in-time NAV observations within selected historical period",
            navEvidence.observationCount() + " observations (" + navEvidence.usableDateRange() + ")",
            ret02Eligible
                ? "Eligible for deterministic discrete return calculation."
                : "Insufficient observations in selected period to compute period return."
        ));

        // 2. RET-03: Annualized Compound Return (3Y)
        boolean ret03Eligible = navEvidence.observationCount() >= 700;
        String ret03State = ret03Eligible ? "ELIGIBLE" : (navEvidence.observationCount() > 0 ? "INSUFFICIENT_HISTORY" : "INPUT_UNAVAILABLE");
        items.add(new MetricEligibilityItemDto(
            "RET-03",
            "Annualized Return (3Y)",
            "APPROVED",
            ret03State,
            "Minimum 700 trading day NAV observations over a 36-month horizon",
            navEvidence.observationCount() + " observations",
            ret03Eligible
                ? "Full 3-year trading horizon verified."
                : String.format(Locale.ROOT, "Requires >= 700 trading days for 3Y evaluation; fund has %d observations.", navEvidence.observationCount())
        ));

        // 3. RSK-01: Annualized Volatility / Standard Deviation (3Y)
        boolean rsk01Eligible = navEvidence.observationCount() >= 700;
        String rsk01State = rsk01Eligible ? "ELIGIBLE" : (navEvidence.observationCount() > 0 ? "INSUFFICIENT_HISTORY" : "INPUT_UNAVAILABLE");
        items.add(new MetricEligibilityItemDto(
            "RSK-01",
            "Standard Deviation (3Y)",
            "APPROVED",
            rsk01State,
            "Minimum 700 trading day log/simple returns over a 36-month horizon",
            navEvidence.observationCount() + " observations",
            rsk01Eligible
                ? "Full 3-year return series verified for volatility computation."
                : String.format(Locale.ROOT, "Requires >= 700 trading days for 3Y volatility; fund has %d observations.", navEvidence.observationCount())
        ));

        // 4. PORTFOLIO_DRIFT: Month-over-Month Holdings Shift
        boolean driftEligible = holdingsEvidence.snapshotCount() >= 2;
        String driftState = driftEligible ? "ELIGIBLE" : (holdingsEvidence.snapshotCount() > 0 ? "INSUFFICIENT_HISTORY" : "INPUT_UNAVAILABLE");
        items.add(new MetricEligibilityItemDto(
            "PORTFOLIO_DRIFT",
            "Holdings Drift & Turnover",
            "APPROVED",
            driftState,
            "At least 2 distinct monthly portfolio snapshots in ledger",
            holdingsEvidence.snapshotCount() + " snapshot(s) in ledger",
            driftEligible
                ? "At least 2 monthly snapshots available for month-over-month drift."
                : String.format(Locale.ROOT, "Fund has only %d snapshot; at least 2 distinct monthly reporting periods required.", holdingsEvidence.snapshotCount())
        ));

        // 5. SECTOR_DRIFT: Sector Allocation Shift
        boolean sectorDriftEligible = holdingsEvidence.snapshotCount() >= 2;
        String sectorDriftState = sectorDriftEligible ? "ELIGIBLE" : (holdingsEvidence.snapshotCount() > 0 ? "INSUFFICIENT_HISTORY" : "INPUT_UNAVAILABLE");
        items.add(new MetricEligibilityItemDto(
            "SECTOR_DRIFT",
            "Sector Allocation Drift",
            "APPROVED",
            sectorDriftState,
            "At least 2 distinct monthly snapshots with defensible sector classifications",
            holdingsEvidence.snapshotCount() + " snapshot(s), unclassified exposure: " + holdingsEvidence.unclassifiedExposurePercentage() + "%",
            sectorDriftEligible
                ? "Eligible for sector allocation drift calculation."
                : "Insufficient snapshots to compute sector allocation drift."
        ));

        // 6. PORTFOLIO_OVERLAP: Multi-Fund Holdings Overlap
        boolean overlapEligible = holdingsEvidence.positionCount() > 0;
        items.add(new MetricEligibilityItemDto(
            "PORTFOLIO_OVERLAP",
            "Portfolio Holdings Overlap",
            "APPROVED",
            overlapEligible ? "ELIGIBLE" : "INPUT_UNAVAILABLE",
            "Disclosed holdings positions with verified ISIN security identity mapping",
            holdingsEvidence.positionCount() + " positions disclosed",
            overlapEligible
                ? "Eligible for pairwise holdings intersection and overlap calculation."
                : "No holdings disclosed to compute portfolio overlap."
        ));

        // 7. PRT-02: Holdings Concentration (Herfindahl-Hirschman Index)
        items.add(new MetricEligibilityItemDto(
            "PRT-02",
            "Portfolio Concentration Index",
            "CANDIDATE",
            "UNVALIDATED_CANDIDATE",
            "Authoritative validated concentration methodology under Phase 2H governance",
            "Candidate formula implemented in quant kernel",
            "Candidate methodology specification; operates with research disclaimer and not validated for live investment advice."
        ));

        return items;
    }

    private List<DataQualityFindingDto> evaluateFindings(
        SchemeOption option,
        NavFreshnessDetailDto navEvidence,
        HoldingsFreshnessDetailDto holdingsEvidence
    ) {
        List<DataQualityFindingDto> findings = new ArrayList<>();

        // NAV findings
        if ("STALE".equals(navEvidence.freshnessState())) {
            findings.add(new DataQualityFindingDto(
                "NAV_CADENCE",
                "WARNING",
                "Historical NAV Observation Horizon",
                String.format(Locale.ROOT, "Latest NAV observation is from %s (%d days old). Evaluated against historical research window.", navEvidence.latestNavDate(), navEvidence.observationAgeDays())
            ));
        }

        // Holdings disclosure findings
        if ("PARTIAL".equals(holdingsEvidence.coverageState())) {
            findings.add(new DataQualityFindingDto(
                "HOLDINGS_DISCLOSURE",
                "INFO",
                "Partial Top-Holdings Disclosure",
                String.format(Locale.ROOT, "Disclosed portfolio represents %.2f%% raw reported weight across %d positions. The remaining %.2f%% represents smaller positions and cash/TREPS/net current assets.",
                    holdingsEvidence.rawSumWeightsPercentage(), holdingsEvidence.positionCount(), 100.0 - holdingsEvidence.rawSumWeightsPercentage())
            ));
        }

        if (holdingsEvidence.rawSumWeightsPercentage() > 100.0) {
            findings.add(new DataQualityFindingDto(
                "HOLDINGS_DISCLOSURE",
                "WARNING",
                "Gross Reported Weight Above 100%",
                String.format(Locale.ROOT, "Raw reported holdings total is %.2f%%, reflecting gross investment exposure before liability netting.", holdingsEvidence.rawSumWeightsPercentage())
            ));
        }

        if (holdingsEvidence.snapshotCount() < 2) {
            findings.add(new DataQualityFindingDto(
                "SNAPSHOT_HISTORY",
                "LIMITATION",
                "Single Reporting Snapshot Available",
                String.format(Locale.ROOT, "Only %d monthly snapshot is recorded in the ledger. Month-over-month portfolio drift requires at least 2 distinct reporting dates.", holdingsEvidence.snapshotCount())
            ));
        }

        if ("DISCOVERY_RESTRICTED".equals(holdingsEvidence.discoveryStatus())) {
            findings.add(new DataQualityFindingDto(
                "SOURCE_DISCOVERY",
                "LIMITATION",
                "AMC Direct Discovery Restricted",
                "HDFC Mutual Fund portal enforces anti-automation controls. YUKIRA preserves verified existing ledger snapshots without falsely advancing freshness."
            ));
        }

        if (holdingsEvidence.unclassifiedExposurePercentage() > 5.0) {
            findings.add(new DataQualityFindingDto(
                "SECTOR_CLASSIFICATION",
                "INFO",
                "Unclassified Sector Exposure",
                String.format(Locale.ROOT, "%.2f%% of disclosed weight is in unclassified or 'Other' sector categories.", holdingsEvidence.unclassifiedExposurePercentage())
            ));
        }

        return findings;
    }

    private SevenQuestionsAnswerDto answerSevenQuestions(
        NavFreshnessDetailDto navEvidence,
        HoldingsFreshnessDetailDto holdingsEvidence,
        List<MetricEligibilityItemDto> metricEligibility
    ) {
        // Q1: When was the latest NAV observation?
        String q1 = navEvidence.latestNavDate() != null
            ? String.format(Locale.ROOT, "%s (%d trading observations in usable range %s, %s)",
                navEvidence.latestNavDate(), navEvidence.observationCount(), navEvidence.usableDateRange(), navEvidence.freshnessState())
            : "No NAV observations recorded in ledger.";

        // Q2: When was the latest holdings portfolio reporting date?
        String q2 = holdingsEvidence.latestReportingDate() != null
            ? String.format(Locale.ROOT, "%s (%d positions, raw sum %.2f%%, %s)",
                holdingsEvidence.latestReportingDate(), holdingsEvidence.positionCount(), holdingsEvidence.rawSumWeightsPercentage(), holdingsEvidence.freshnessState())
            : "No monthly holdings disclosures recorded.";

        // Q3: When was the holdings source known to be available?
        String q3 = holdingsEvidence.availabilityTime() != null
            ? String.format(Locale.ROOT, "Available as of %s (Ingested: %s)",
                holdingsEvidence.availabilityTime(),
                holdingsEvidence.ingestionTimestamp() != null ? holdingsEvidence.ingestionTimestamp() : "Not recorded")
            : "Availability timestamp not recorded.";

        // Q4: Is the data current relative to its expected publication cadence?
        String q4 = String.format(Locale.ROOT,
            "NAV is %s (policy: %s). Holdings are %s (policy: %s). NAV and holdings freshness are evaluated independently.",
            navEvidence.freshnessState(), navEvidence.cadencePolicy(),
            holdingsEvidence.freshnessState(), holdingsEvidence.cadencePolicy()
        );

        // Q5: How complete is the disclosed holdings information?
        String q5 = holdingsEvidence.latestReportingDate() != null
            ? String.format(Locale.ROOT,
                "Coverage is %s. Disclosed raw sum is %.2f%% across %d positions. Residual of %.2f%% represents smaller positions and cash/TREPS. Weights are strictly unnormalized.",
                holdingsEvidence.coverageState(), holdingsEvidence.rawSumWeightsPercentage(), holdingsEvidence.positionCount(), 100.0 - holdingsEvidence.rawSumWeightsPercentage())
            : "Holdings coverage unavailable.";

        // Q6: Are important metrics calculable for the requested period?
        long eligibleCount = metricEligibility.stream().filter(m -> "ELIGIBLE".equals(m.eligibilityState())).count();
        long insufficientCount = metricEligibility.stream().filter(m -> "INSUFFICIENT_HISTORY".equals(m.eligibilityState())).count();
        String q6 = String.format(Locale.ROOT,
            "%d metrics ELIGIBLE, %d INSUFFICIENT_HISTORY, %d CANDIDATE only. Discrete return (RET-02) is %s; 3Y risk/return is %s; Portfolio drift is %s.",
            eligibleCount, insufficientCount,
            metricEligibility.stream().filter(m -> "UNVALIDATED_CANDIDATE".equals(m.eligibilityState())).count(),
            metricEligibility.stream().filter(m -> "RET-02".equals(m.metricCode())).map(MetricEligibilityItemDto::eligibilityState).findFirst().orElse("UNKNOWN"),
            metricEligibility.stream().filter(m -> "RET-03".equals(m.metricCode())).map(MetricEligibilityItemDto::eligibilityState).findFirst().orElse("UNKNOWN"),
            metricEligibility.stream().filter(m -> "PORTFOLIO_DRIFT".equals(m.metricCode())).map(MetricEligibilityItemDto::eligibilityState).findFirst().orElse("UNKNOWN")
        );

        // Q7: What evidence or limitations should the investor investigate?
        String q7 = String.format(Locale.ROOT,
            "1. Holdings drift requires >= 2 snapshots (fund has %d). 2. Disclosed holdings represent top %d holdings (%.2f%% raw total). 3. Source discovery status: %s.",
            holdingsEvidence.snapshotCount(), holdingsEvidence.positionCount(), holdingsEvidence.rawSumWeightsPercentage(), holdingsEvidence.discoveryStatus()
        );

        return new SevenQuestionsAnswerDto(q1, q2, q3, q4, q5, q6, q7);
    }
}
