package com.yukira.backend.ingestion.amfi;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for expanding and managing the Indian Mutual Fund universe catalog
 * directly from authoritative AMFI feeds (daily NAVAll.txt and historical downloads).
 *
 * Preserves the strict four-tier hierarchy:
 *   AMC -> Scheme -> SchemePlan -> SchemeOption
 *
 * Implements deterministic identity resolution, idempotent deduplication,
 * zero fabricated fields (inception_date remains null if absent),
 * and six-dimensional data quality diagnostics.
 */
@Service
@SuppressWarnings("null")
public class AmfiUniverseIngestionService {

    private static final Logger log = LoggerFactory.getLogger(AmfiUniverseIngestionService.class);
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    // Pattern for category banners: Open Ended Schemes ( Equity Scheme - Flexi Cap Fund )
    private static final Pattern CATEGORY_PATTERN = Pattern.compile(
        "^([^(]+?)\\s*\\(\\s*([^-\\)]+?)(?:\\s*-\\s*([^-\\)]+?))?\\s*\\)$",
        Pattern.CASE_INSENSITIVE
    );

    private final AmcRepository amcRepository;
    private final SchemeRepository schemeRepository;
    private final SchemePlanRepository schemePlanRepository;
    private final SchemeOptionRepository schemeOptionRepository;
    private final ValidationIssueRepository validationIssueRepository;
    private final SourceArtifactRepository sourceArtifactRepository;
    private final DataSourceRepository dataSourceRepository;
    private final AmfiSourceClient amfiSourceClient;

    @Autowired
    public AmfiUniverseIngestionService(
        AmcRepository amcRepository,
        SchemeRepository schemeRepository,
        SchemePlanRepository schemePlanRepository,
        SchemeOptionRepository schemeOptionRepository,
        ValidationIssueRepository validationIssueRepository,
        SourceArtifactRepository sourceArtifactRepository,
        DataSourceRepository dataSourceRepository,
        @Autowired(required = false) AmfiSourceClient amfiSourceClient
    ) {
        this.amcRepository = amcRepository;
        this.schemeRepository = schemeRepository;
        this.schemePlanRepository = schemePlanRepository;
        this.schemeOptionRepository = schemeOptionRepository;
        this.validationIssueRepository = validationIssueRepository;
        this.sourceArtifactRepository = sourceArtifactRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.amfiSourceClient = amfiSourceClient;
    }

    public AmfiUniverseIngestionService(
        AmcRepository amcRepository,
        SchemeRepository schemeRepository,
        SchemePlanRepository schemePlanRepository,
        SchemeOptionRepository schemeOptionRepository,
        ValidationIssueRepository validationIssueRepository,
        SourceArtifactRepository sourceArtifactRepository,
        DataSourceRepository dataSourceRepository
    ) {
        this(amcRepository, schemeRepository, schemePlanRepository, schemeOptionRepository,
             validationIssueRepository, sourceArtifactRepository, dataSourceRepository, null);
    }

    /**
     * Fetches the live AMFI daily universe from portal.amfiindia.com/spages/NAVAll.txt
     * and deterministically ingests or updates the catalog.
     */
    @Transactional
    public UniverseIngestionSummary fetchAndIngestLiveUniverse() {
        if (amfiSourceClient == null) {
            throw new IllegalStateException("AmfiSourceClient not available for live universe ingestion");
        }
        SourceArtifact artifact = amfiSourceClient.fetchAndPersistUniverseCatalogArtifact();
        return processArtifact(artifact);
    }

    /**
     * Ingests raw AMFI payload bytes to expand or update the mutual fund universe catalog.
     */
    @Transactional
    public UniverseIngestionSummary ingestUniversePayload(byte[] payloadBytes, String sourceUri) {
        if (payloadBytes == null || payloadBytes.length == 0) {
            throw new IllegalArgumentException("Payload bytes must not be null or empty");
        }

        String sha256 = computeSha256(payloadBytes);
        SourceArtifact artifact = sourceArtifactRepository.findBySha256Hash(sha256)
            .orElseGet(() -> {
                DataSource ds = dataSourceRepository.findByCode("AMFI_PORTAL")
                    .orElseGet(() -> dataSourceRepository.save(new DataSource("AMFI_PORTAL", "AMFI NAV Historical Portal", "AMFI")));
                SourceArtifact sa = new SourceArtifact(
                    ds,
                    OffsetDateTime.now(),
                    "UNIVERSE_CATALOG_TEXT",
                    sha256,
                    (long) payloadBytes.length
                );
                sa.setStorageUri(sourceUri != null ? sourceUri : "amfi://catalog/" + sha256);
                sa.setPayloadBlob(payloadBytes);
                return sourceArtifactRepository.save(sa);
            });

        return processArtifact(artifact);
    }

    /**
     * Ingests universe catalog from an existing SourceArtifact.
     */
    @Transactional
    public UniverseIngestionSummary ingestUniverseArtifact(SourceArtifact artifact) {
        if (artifact == null || artifact.getPayloadBlob() == null) {
            throw new IllegalArgumentException("SourceArtifact and payload blob must not be null");
        }
        return processArtifact(artifact);
    }

    private UniverseIngestionSummary processArtifact(SourceArtifact artifact) {
        byte[] payloadBytes = artifact.getPayloadBlob();
        List<String> messages = new ArrayList<>();

        int totalLines = 0;
        int amcsRegistered = 0;
        int schemesRegistered = 0;
        int plansRegistered = 0;
        int optionsRegistered = 0;
        int duplicatesSkipped = 0;
        int issuesCreated = 0;

        // Diagnostic counts for Part G Machine-Readable Diagnostics
        int activeOptionsCount = 0;
        int inactiveOptionsCount = 0;
        int directOptionsCount = 0;
        int regularOptionsCount = 0;
        int growthOptionsCount = 0;
        int idcwOptionsCount = 0;
        int bonusOptionsCount = 0;
        int otherOptionsCount = 0;
        int missingIsinCount = 0;
        int missingAmfiCodeCount = 0;
        int missingCategoryCount = 0;
        int missingSubcategoryCount = 0;

        // Caches to avoid redundant queries during large batch processing
        Map<String, Amc> amcByCode = new HashMap<>();
        Map<String, Scheme> schemeByCode = new HashMap<>();
        Map<String, SchemePlan> planByCode = new HashMap<>();
        Map<String, SchemeOption> optionByAmfiCode = new HashMap<>();
        Map<String, SchemeOption> optionByIsin = new HashMap<>();

        for (Amc amc : amcRepository.findAll()) {
            amcByCode.put(amc.getCode(), amc);
        }
        for (Scheme s : schemeRepository.findAll()) {
            schemeByCode.put(s.getCode(), s);
        }
        for (SchemePlan p : schemePlanRepository.findAll()) {
            planByCode.put(p.getCode(), p);
        }
        for (SchemeOption o : schemeOptionRepository.findAll()) {
            if (o.getAmfiCode() != null && !o.getAmfiCode().isBlank()) {
                optionByAmfiCode.put(o.getAmfiCode().trim(), o);
            }
            if (o.getIsin() != null && !o.getIsin().isBlank()) {
                optionByIsin.put(o.getIsin().trim(), o);
            }
        }

        String currentCategory = null;
        String currentSubcategory = null;
        String currentAmcName = null;

        Map<String, String> feedAmfiCodeToName = new HashMap<>();
        Map<String, String> feedIsinToAmfiCode = new HashMap<>();

        // Dynamic header column indices
        int schemeCodeCol = 0;
        int isinGrowthCol = 1;
        int isinReinvCol = 2;
        int schemeNameCol = 3;
        int planCol = -1;
        int optionCol = -1;
        int navCol = 6;
        int dateCol = 7;
        boolean hasDynamicHeader = false;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(payloadBytes), WINDOWS_1252))) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                totalLines++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                // Check for Category Banner: e.g. Open Ended Schemes ( Equity Scheme - Flexi Cap Fund )
                Matcher catMatcher = CATEGORY_PATTERN.matcher(trimmed);
                if (catMatcher.matches()) {
                    currentCategory = catMatcher.group(2) != null ? catMatcher.group(2).trim() : null;
                    currentSubcategory = catMatcher.group(3) != null ? catMatcher.group(3).trim() : null;
                    continue;
                }

                // Check for Header Line
                if (trimmed.toLowerCase().startsWith("scheme code;") || trimmed.toLowerCase().startsWith("scheme code ;")) {
                    String[] headerTokens = trimmed.split(";", -1);
                    for (int i = 0; i < headerTokens.length; i++) {
                        String h = headerTokens[i].toLowerCase().trim();
                        if (h.contains("scheme code")) {
                            schemeCodeCol = i;
                        } else if (h.contains("scheme name") || h.contains("nav name")) {
                            schemeNameCol = i;
                        } else if (h.contains("isin div payout") || h.contains("isin growth") || (h.contains("isin") && !h.contains("reinvest"))) {
                            isinGrowthCol = i;
                        } else if (h.contains("isin div reinvest") || (h.contains("isin") && h.contains("reinvest"))) {
                            isinReinvCol = i;
                        } else if (h.equals("plan")) {
                            planCol = i;
                        } else if (h.equals("option")) {
                            optionCol = i;
                        } else if (h.contains("net asset value") || h.equals("nav")) {
                            navCol = i;
                        } else if (h.contains("date")) {
                            dateCol = i;
                        }
                    }
                    hasDynamicHeader = true;
                    continue;
                }

                // Check for AMC Banner (single string with no semicolons)
                if (!trimmed.contains(";")) {
                    if (isPotentialAmcBanner(trimmed)) {
                        currentAmcName = trimmed;
                    }
                    continue;
                }

                // Scheme Line (semicolon separated)
                String[] tokens = trimmed.split(";", -1);
                if (tokens.length < 3) {
                    if (issuesCreated < 100) {
                        recordIssue(artifact.getId(), "MALFORMED_ROW", "Line " + lineNumber + ": Insufficient columns: " + trimmed, "INVALID");
                        issuesCreated++;
                    }
                    continue;
                }

                String schemeCode = tokens[0].trim();
                if (!isNumeric(schemeCode)) {
                    missingAmfiCodeCount++;
                    if (issuesCreated < 100) {
                        recordIssue(artifact.getId(), "MISSING_AMFI_CODE", "Line " + lineNumber + ": Non-numeric Scheme Code: " + schemeCode, "INVALID");
                        issuesCreated++;
                    }
                    continue;
                }

                // Dynamic or heuristic column extraction
                String isinGrowth;
                String isinReinv;
                String schemeName;
                String explicitPlan = "";
                String explicitOption = "";
                String dateStr = "";

                if (hasDynamicHeader) {
                    isinGrowth = (isinGrowthCol >= 0 && isinGrowthCol < tokens.length) ? tokens[isinGrowthCol].trim() : "";
                    isinReinv = (isinReinvCol >= 0 && isinReinvCol < tokens.length) ? tokens[isinReinvCol].trim() : "";
                    schemeName = (schemeNameCol >= 0 && schemeNameCol < tokens.length) ? tokens[schemeNameCol].trim() : "";
                    explicitPlan = (planCol >= 0 && planCol < tokens.length) ? tokens[planCol].trim() : null;
                    explicitOption = (optionCol >= 0 && optionCol < tokens.length) ? tokens[optionCol].trim() : null;
                    dateStr = (dateCol >= 0 && dateCol < tokens.length) ? tokens[dateCol].trim() : "";
                } else {
                    if (tokens.length >= 4 && (tokens[1].trim().startsWith("INF") || tokens[2].trim().startsWith("INF"))) {
                        isinGrowth = tokens[1].trim();
                        isinReinv = tokens[2].trim();
                        schemeName = tokens[3].trim();
                    } else {
                        schemeName = tokens[1].trim();
                        isinGrowth = tokens.length > 2 ? tokens[2].trim() : "";
                        isinReinv = tokens.length > 3 ? tokens[3].trim() : "";
                    }
                    dateStr = tokens.length > 5 ? tokens[tokens.length - 1].trim() : "";
                }

                if (schemeName.isBlank()) {
                    if (issuesCreated < 100) {
                        recordIssue(artifact.getId(), "MALFORMED_SCHEME_NAME", "Line " + lineNumber + ": Empty scheme name for AMFI " + schemeCode, "INVALID");
                        issuesCreated++;
                    }
                    continue;
                }

                // Recency / Active Status determination:
                // Options reporting NAV in current period (2025/2026) are ACTIVE; older are INACTIVE
                boolean isActive = false;
                if (!dateStr.isBlank()) {
                    String lowerDate = dateStr.toLowerCase();
                    if (lowerDate.contains("2026") || lowerDate.contains("2025")) {
                        isActive = true;
                    }
                }
                String optionStatus = isActive ? "ACTIVE" : "INACTIVE";

                // Resolve primary ISIN
                String primaryIsin = sanitizeIsin(isinGrowth);
                if (primaryIsin == null) {
                    primaryIsin = sanitizeIsin(isinReinv);
                }

                if (primaryIsin == null) {
                    missingIsinCount++;
                    if (issuesCreated < 100) {
                        recordIssue(artifact.getId(), "MISSING_ISIN", "Line " + lineNumber + ": No valid ISIN for AMFI code " + schemeCode + " (" + schemeName + ")", "SUSPICIOUS");
                        issuesCreated++;
                    }
                }

                // Category diagnostics
                if (currentCategory == null || currentCategory.isBlank()) {
                    missingCategoryCount++;
                }
                if (currentSubcategory == null || currentSubcategory.isBlank()) {
                    missingSubcategoryCount++;
                }

                // Duplicate checking within this feed
                if (feedAmfiCodeToName.containsKey(schemeCode)) {
                    String existingName = feedAmfiCodeToName.get(schemeCode);
                    if (!existingName.equalsIgnoreCase(schemeName) && issuesCreated < 100) {
                        recordIssue(artifact.getId(), "DUPLICATE_AMFI_CODE", "Line " + lineNumber + ": Duplicate AMFI code " + schemeCode + " with different names: '" + existingName + "' vs '" + schemeName + "'", "SUSPICIOUS");
                        issuesCreated++;
                    }
                    duplicatesSkipped++;
                    continue;
                }
                feedAmfiCodeToName.put(schemeCode, schemeName);

                // Duplicate ISIN resolution across different AMFI schemes
                boolean isDuplicateIsin = false;
                if (primaryIsin != null) {
                    if (feedIsinToAmfiCode.containsKey(primaryIsin)) {
                        String existingAmfi = feedIsinToAmfiCode.get(primaryIsin);
                        if (!existingAmfi.equals(schemeCode)) {
                            isDuplicateIsin = true;
                            if (issuesCreated < 100) {
                                recordIssue(artifact.getId(), "DUPLICATE_ISIN", "Line " + lineNumber + ": Duplicate ISIN " + primaryIsin + " for AMFI " + existingAmfi + " and " + schemeCode, "SUSPICIOUS");
                                issuesCreated++;
                            }
                        }
                    } else if (optionByIsin.containsKey(primaryIsin)) {
                        SchemeOption existingOpt = optionByIsin.get(primaryIsin);
                        if (existingOpt.getAmfiCode() != null && !existingOpt.getAmfiCode().equals(schemeCode)) {
                            isDuplicateIsin = true;
                            if (issuesCreated < 100) {
                                recordIssue(artifact.getId(), "DUPLICATE_ISIN", "Line " + lineNumber + ": Duplicate ISIN " + primaryIsin + " already registered for AMFI " + existingOpt.getAmfiCode() + ", conflicting with " + schemeCode, "SUSPICIOUS");
                                issuesCreated++;
                            }
                        }
                    } else {
                        feedIsinToAmfiCode.put(primaryIsin, schemeCode);
                    }
                }

                if (isDuplicateIsin) {
                    // Nullify ISIN on this entity to avoid breaching PostgreSQL UNIQUE constraint scheme_option_isin_key
                    primaryIsin = null;
                    optionStatus = "DATA_QUALITY_LIMITED";
                }

                // Decompose scheme name
                DecomposedScheme decomp = decomposeScheme(schemeName, explicitPlan, explicitOption);
                String planType = decomp.planType();
                String optionType = decomp.optionType();

                // Track metrics
                if ("DIRECT".equalsIgnoreCase(planType)) {
                    directOptionsCount++;
                } else {
                    regularOptionsCount++;
                }

                if ("GROWTH".equalsIgnoreCase(optionType)) {
                    growthOptionsCount++;
                } else if (optionType.startsWith("IDCW")) {
                    idcwOptionsCount++;
                } else if ("BONUS".equalsIgnoreCase(optionType)) {
                    bonusOptionsCount++;
                } else {
                    otherOptionsCount++;
                }

                if (isActive) {
                    activeOptionsCount++;
                } else {
                    inactiveOptionsCount++;
                }

                // 1. AMC Resolution
                String amcName = currentAmcName != null ? currentAmcName : extractAmcNameFromScheme(schemeName);
                if (amcName == null || amcName.isBlank()) {
                    amcName = "Independent / Unclassified AMC";
                    if (issuesCreated < 100) {
                        recordIssue(artifact.getId(), "AMBIGUOUS_HIERARCHY", "Line " + lineNumber + ": Could not identify AMC for scheme: " + schemeName, "SUSPICIOUS");
                        issuesCreated++;
                    }
                }
                String amcCode = generateAmcCode(amcName);
                Amc amc = amcByCode.get(amcCode);
                if (amc == null) {
                    Optional<Amc> existingAmc = amcRepository.findByCode(amcCode);
                    if (existingAmc.isPresent()) {
                        amc = existingAmc.get();
                    } else {
                        amc = amcRepository.save(new Amc(amcName, amcCode));
                        amcsRegistered++;
                    }
                    amcByCode.put(amcCode, amc);
                }

                // 2. Scheme Resolution (with cross-AMC collision prevention)
                String baseSchemeName = decomp.baseSchemeName();
                String schemeCodeIdentifier = resolveSchemeCode(amc.getCode(), baseSchemeName);
                Scheme scheme = schemeByCode.get(schemeCodeIdentifier);
                if (scheme == null) {
                    Optional<Scheme> existingScheme = schemeRepository.findByCode(schemeCodeIdentifier);
                    if (existingScheme.isPresent()) {
                        scheme = existingScheme.get();
                    } else {
                        scheme = schemeRepository.save(new Scheme(
                            amc,
                            baseSchemeName,
                            schemeCodeIdentifier,
                            null, // Inception date is strictly nullable when unavailable; ZERO fabricated dates
                            currentCategory,
                            currentSubcategory
                        ));
                        schemesRegistered++;
                    }
                    schemeByCode.put(schemeCodeIdentifier, scheme);
                } else {
                    boolean updated = false;
                    if (scheme.getCategory() == null && currentCategory != null) {
                        scheme.setCategory(currentCategory);
                        updated = true;
                    }
                    if (scheme.getSubcategory() == null && currentSubcategory != null) {
                        scheme.setSubcategory(currentSubcategory);
                        updated = true;
                    }
                    if (updated) {
                        scheme = schemeRepository.save(scheme);
                        schemeByCode.put(schemeCodeIdentifier, scheme);
                    }
                }

                // 3. SchemePlan Resolution
                String planCode = resolvePlanCode(scheme.getCode(), planType);
                SchemePlan plan = planByCode.get(planCode);
                if (plan == null) {
                    Optional<SchemePlan> existingPlan = schemePlanRepository.findByCode(planCode);
                    if (existingPlan.isPresent()) {
                        plan = existingPlan.get();
                    } else {
                        plan = schemePlanRepository.save(new SchemePlan(scheme, planType, planCode));
                        plansRegistered++;
                    }
                    planByCode.put(planCode, plan);
                }

                // 4. SchemeOption Resolution
                SchemeOption option = optionByAmfiCode.get(schemeCode);
                if (option == null && primaryIsin != null) {
                    option = optionByIsin.get(primaryIsin);
                }

                if (option == null) {
                    Optional<SchemeOption> optByAmfi = schemeOptionRepository.findByAmfiCode(schemeCode);
                    if (optByAmfi.isPresent()) {
                        option = optByAmfi.get();
                    } else if (primaryIsin != null) {
                        Optional<SchemeOption> optByIsinMatch = schemeOptionRepository.findByIsin(primaryIsin);
                        if (optByIsinMatch.isPresent()) {
                            option = optByIsinMatch.get();
                        }
                    }
                }

                if (option == null) {
                    SchemeOption newOpt = new SchemeOption(plan, optionType, schemeCode, primaryIsin);
                    newOpt.setStatus(optionStatus);
                    option = schemeOptionRepository.save(newOpt);
                    optionsRegistered++;
                } else {
                    boolean modified = false;
                    if (option.getAmfiCode() == null && schemeCode != null) {
                        option.setAmfiCode(schemeCode);
                        modified = true;
                    }
                    if (option.getIsin() == null && primaryIsin != null) {
                        option.setIsin(primaryIsin);
                        modified = true;
                    }
                    if (!option.getStatus().equals(optionStatus) && !"DATA_QUALITY_LIMITED".equals(option.getStatus())) {
                        option.setStatus(optionStatus);
                        modified = true;
                    }
                    if (modified) {
                        option = schemeOptionRepository.save(option);
                    }
                }

                if (option.getAmfiCode() != null) {
                    optionByAmfiCode.put(option.getAmfiCode(), option);
                }
                if (option.getIsin() != null) {
                    optionByIsin.put(option.getIsin(), option);
                }
            }
        } catch (Exception e) {
            log.error("Fatal exception during AMFI universe ingestion: {}", e.getMessage(), e);
            messages.add("Fatal parsing error: " + e.getMessage());
        }

        messages.add(String.format("Ingestion complete: %d rows processed, %d AMCs, %d Schemes, %d Plans, %d Options registered, %d duplicates skipped, %d issues logged.",
            totalLines, amcsRegistered, schemesRegistered, plansRegistered, optionsRegistered, duplicatesSkipped, issuesCreated));

        return new UniverseIngestionSummary(
            artifact.getId(),
            artifact.getSha256Hash(),
            totalLines,
            amcsRegistered,
            schemesRegistered,
            plansRegistered,
            optionsRegistered,
            activeOptionsCount,
            inactiveOptionsCount,
            directOptionsCount,
            regularOptionsCount,
            growthOptionsCount,
            idcwOptionsCount,
            bonusOptionsCount,
            otherOptionsCount,
            missingIsinCount,
            missingAmfiCodeCount,
            missingCategoryCount,
            missingSubcategoryCount,
            duplicatesSkipped,
            issuesCreated,
            messages
        );
    }

    /**
     * Deterministically decomposes raw AMFI scheme name and optional explicit tokens into Base Scheme Name, Plan Type, and Option Type.
     */
    public DecomposedScheme decomposeScheme(String rawSchemeName, String explicitPlan, String explicitOption) {
        if (rawSchemeName == null || rawSchemeName.isBlank()) {
            return new DecomposedScheme("Unclassified Scheme", "REGULAR", "GROWTH");
        }

        String cleaned = rawSchemeName.trim();
        String lower = cleaned.toLowerCase();

        // 1. Plan Type
        String planType = "REGULAR";
        if (explicitPlan != null && (explicitPlan.toLowerCase().contains("direct") || explicitPlan.toLowerCase().contains("dir"))) {
            planType = "DIRECT";
        } else if (lower.contains("direct") || lower.contains("dir.") || lower.matches(".*\\bdir\\b.*")) {
            planType = "DIRECT";
        }

        // 2. Option Type
        String optionType = "GROWTH";
        if (explicitOption != null && !explicitOption.isBlank()) {
            String expLower = explicitOption.toLowerCase();
            if (expLower.contains("bonus")) {
                optionType = "BONUS";
            } else if (expLower.contains("reinvest") || expLower.contains("re-invest")) {
                optionType = "IDCW_REINVESTMENT";
            } else if (expLower.contains("payout")) {
                optionType = "IDCW_PAYOUT";
            } else if (expLower.contains("idcw") || expLower.contains("dividend") || expLower.contains("div")) {
                optionType = "IDCW";
            } else if (expLower.contains("growth") || expLower.contains("gwth") || expLower.matches(".*\\bgr\\b.*")) {
                optionType = "GROWTH";
            }
        }
        if ("GROWTH".equals(optionType)) {
            if (lower.contains("bonus")) {
                optionType = "BONUS";
            } else if (lower.contains("reinvest") || lower.contains("re-invest")) {
                optionType = "IDCW_REINVESTMENT";
            } else if (lower.contains("payout")) {
                optionType = "IDCW_PAYOUT";
            } else if (lower.contains("idcw") || lower.contains("dividend") || lower.contains("div.")) {
                optionType = "IDCW";
            } else if (lower.contains("growth") || lower.contains("gwth") || lower.matches(".*\\bgr\\b.*")) {
                optionType = "GROWTH";
            }
        }

        // 3. Base Scheme Name extraction
        String baseName = cleaned;

        // Split by standard delimiters: " - ", " – ", " — "
        String[] parts = baseName.split("\\s+[-–—]\\s+");
        if (parts.length > 1) {
            List<String> retainedParts = new ArrayList<>();
            for (String part : parts) {
                String pLower = part.toLowerCase().trim();
                boolean isPlanPart = pLower.equals("direct plan") || pLower.equals("direct") || pLower.equals("regular plan") || pLower.equals("regular");
                boolean isOptionPart = pLower.contains("growth") || pLower.contains("idcw") || pLower.contains("dividend")
                    || pLower.contains("bonus") || pLower.contains("payout") || pLower.contains("reinvestment")
                    || pLower.equals("option") || pLower.equals("plan");

                if (!isPlanPart && !isOptionPart) {
                    retainedParts.add(part.trim());
                }
            }
            if (!retainedParts.isEmpty()) {
                baseName = String.join(" - ", retainedParts);
            }
        }

        // Strip residual trailing plan/option phrases
        baseName = baseName.replaceAll("(?i)\\s*[-–—]?\\s*\\b(direct|regular)\\b\\s*(plan)?.*$", "")
            .replaceAll("(?i)\\s*[-–—]?\\s*\\b(growth|idcw|dividend|bonus)\\b\\s*(option|plan)?.*$", "")
            .trim();

        if (baseName.isBlank()) {
            baseName = cleaned;
        }

        return new DecomposedScheme(baseName, planType, optionType);
    }

    public DecomposedScheme decomposeScheme(String rawSchemeName) {
        return decomposeScheme(rawSchemeName, null, null);
    }

    private String resolveSchemeCode(String amcCode, String baseSchemeName) {
        // Canonical pilot preservation
        if ("HDFC Flexi Cap Fund".equalsIgnoreCase(baseSchemeName.trim()) || "HDFC_FLEXI".equalsIgnoreCase(baseSchemeName.trim())) {
            return "HDFC_FLEXI";
        }

        String slug = baseSchemeName.toUpperCase()
            .replaceAll("[^A-Z0-9]+", "_")
            .replaceAll("^_+|_+$", "");

        if (slug.length() > 80) {
            slug = slug.substring(0, 80);
        }

        return slug;
    }

    private String resolvePlanCode(String schemeCode, String planType) {
        // Canonical pilot preservation
        if ("HDFC_FLEXI".equals(schemeCode) && "DIRECT".equalsIgnoreCase(planType)) {
            return "HDFC_FLEXI_DIR";
        }
        String code = schemeCode + "_" + planType.toUpperCase();
        if (code.length() > 80) {
            code = code.substring(0, 80);
        }
        return code;
    }

    private String generateAmcCode(String amcName) {
        if ("HDFC Mutual Fund".equalsIgnoreCase(amcName.trim())) {
            return "HDFC_MF";
        }
        String code = amcName.toUpperCase()
            .replace("MUTUAL FUND", "MF")
            .replaceAll("[^A-Z0-9]+", "_")
            .replaceAll("^_+|_+$", "");

        if (!code.endsWith("_MF")) {
            code = code + "_MF";
        }
        if (code.length() > 50) {
            code = code.substring(0, 50);
        }
        return code;
    }

    private String extractAmcNameFromScheme(String schemeName) {
        String trimmed = schemeName.trim();
        String[] commonAmcs = {
            "HDFC", "SBI", "ICICI Prudential", "Nippon India", "Kotak", "Axis", "Aditya Birla Sun Life",
            "UTI", "Mirae Asset", "DSP", "Tata", "Franklin Templeton", "Bandhan", "Edelweiss", "Invesco",
            "Motilal Oswal", "Parag Parikh", "Canara Robeco", "Sundaram", "HSBC", "PGIM India", "Quant"
        };
        for (String amc : commonAmcs) {
            if (trimmed.toLowerCase().startsWith(amc.toLowerCase())) {
                return amc + " Mutual Fund";
            }
        }
        return null;
    }

    private boolean isPotentialAmcBanner(String line) {
        String trimmed = line.trim();
        if (trimmed.length() < 3 || trimmed.length() > 100) return false;
        String lower = trimmed.toLowerCase();
        return lower.endsWith("mutual fund") || lower.contains("mutual fund") || lower.endsWith("amc") || lower.endsWith("fund");
    }

    private String sanitizeIsin(String isin) {
        if (isin == null) return null;
        String trimmed = isin.trim();
        if (trimmed.length() == 12 && trimmed.matches("^[A-Z]{2}[A-Z0-9]{10}$")) {
            return trimmed;
        }
        return null;
    }

    private boolean isNumeric(String str) {
        if (str == null || str.isBlank()) return false;
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isDigit(str.charAt(i))) return false;
        }
        return true;
    }

    private void recordIssue(Long artifactId, String checkCode, String message, String quality) {
        ValidationIssue issue = new ValidationIssue(
            "SOURCE_ARTIFACT",
            artifactId,
            checkCode,
            message
        );
        issue.setQualityAssessment(quality);
        issue.setVerificationStatus("UNVERIFIED");
        validationIssueRepository.save(issue);
    }

    /**
     * Generates a comprehensive Part G machine-readable coverage and data-quality report from the database.
     */
    public UniverseCoverageReport generateUniverseCoverageReport(Map<String, Object> controlledSampleCoverage) {
        long totalAmcs = amcRepository.count();
        long totalSchemes = schemeRepository.count();
        long totalPlans = schemePlanRepository.count();
        long totalOptions = schemeOptionRepository.count();

        long activeOptions = schemeOptionRepository.countByStatus("ACTIVE");
        long inactiveOptions = schemeOptionRepository.countByStatus("INACTIVE");
        long directOptions = schemeOptionRepository.countByPlanPlanType("DIRECT");
        long regularOptions = schemeOptionRepository.countByPlanPlanType("REGULAR");
        long growthOptions = schemeOptionRepository.countByOptionType("GROWTH");
        long idcwOptions = schemeOptionRepository.countByOptionTypeStartingWith("IDCW");
        long missingIsin = schemeOptionRepository.countByIsinIsNull();
        long missingAmfiCode = schemeOptionRepository.countByAmfiCodeIsNull();
        long missingCategory = schemeRepository.countByCategoryIsNull();
        long missingSubcategory = schemeRepository.countBySubcategoryIsNull();
        long missingBenchmark = totalSchemes; // AMFI daily NAV feed does not provide authoritative benchmark assignments

        long duplicateFindings = validationIssueRepository.count();

        List<String> qualityNotes = List.of(
            "AMFI daily NAV feed (NAVAll.txt) provides Scheme Code, ISIN, Scheme Name, NAV, Date, but does not provide Inception Date or Benchmark.",
            "Inception dates remain strictly NULL when absent from source; ZERO fabricated dates.",
            "ISIN duplicate conflicts in defunct series are mitigated by clearing duplicate ISIN and marking DATA_QUALITY_LIMITED.",
            "Canonical pilot HDFC Flexi Cap Direct Growth (AMFI 118955, ISIN INF179K01UT0) preserved as verified baseline.",
            "Reference population INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1 remains explicitly PROVISIONAL."
        );

        return new UniverseCoverageReport(
            totalAmcs,
            totalSchemes,
            totalPlans,
            totalOptions,
            activeOptions,
            inactiveOptions,
            directOptions,
            regularOptions,
            growthOptions,
            idcwOptions,
            missingIsin,
            missingAmfiCode,
            missingCategory,
            missingSubcategory,
            missingBenchmark,
            duplicateFindings,
            0L, // malformed rows are tracked as validation issues
            0L, // ingestion failures
            controlledSampleCoverage != null ? controlledSampleCoverage : Map.of(),
            qualityNotes
        );
    }

    private String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }

    public record DecomposedScheme(
        String baseSchemeName,
        String planType,
        String optionType
    ) {}
}
