package com.yukira.backend.ingestion.mcap;

import com.yukira.backend.domain.entity.DataSource;
import com.yukira.backend.domain.entity.Security;
import com.yukira.backend.domain.entity.SecurityIdentifier;
import com.yukira.backend.domain.entity.SecurityMarketCapClassification;
import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.repository.DataSourceRepository;
import com.yukira.backend.repository.SecurityIdentifierRepository;
import com.yukira.backend.repository.SecurityMarketCapClassificationRepository;
import com.yukira.backend.repository.SourceArtifactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Ingestion service for official AMFI Market Capitalization categorization publications.
 *
 * Epistemic & Bitemporal Principles:
 * 1. Cryptographic lineage: computes SHA-256 and persists raw artifact in source_artifact.
 * 2. Idempotency: re-running ingestion for the same artifact does not duplicate records.
 * 3. Bitemporal discipline: preserves effective_from/effective_to and availability_time for zero-lookahead PIT queries.
 * 4. Deterministic identity resolution: matches canonical securities via ISIN. Unresolved securities are flagged UNMAPPED.
 * 5. Dry-run support: allows dry-run validation without modifying the persistent ledger.
 */
@Service
@SuppressWarnings("null")
public class AmfiMarketCapIngestionService {

    private static final Logger log = LoggerFactory.getLogger(AmfiMarketCapIngestionService.class);

    private final AmfiMarketCapParser parser;
    private final SecurityMarketCapClassificationRepository classificationRepository;
    private final SecurityIdentifierRepository securityIdentifierRepository;
    private final SourceArtifactRepository sourceArtifactRepository;
    private final DataSourceRepository dataSourceRepository;

    public record IngestionResult(
        long totalParsed,
        long savedCount,
        long skippedDuplicates,
        long unmappedSecurities,
        List<String> validationErrors,
        List<String> warnings,
        Long sourceArtifactId,
        String sha256Hash
    ) {}

    public AmfiMarketCapIngestionService(
        AmfiMarketCapParser parser,
        SecurityMarketCapClassificationRepository classificationRepository,
        SecurityIdentifierRepository securityIdentifierRepository,
        SourceArtifactRepository sourceArtifactRepository,
        DataSourceRepository dataSourceRepository
    ) {
        this.parser = parser;
        this.classificationRepository = classificationRepository;
        this.securityIdentifierRepository = securityIdentifierRepository;
        this.sourceArtifactRepository = sourceArtifactRepository;
        this.dataSourceRepository = dataSourceRepository;
    }

    @Transactional
    public IngestionResult ingestArtifact(
        byte[] payloadBytes,
        String sourceUri,
        String rankingPeriod,
        LocalDate rankingPeriodEndDate,
        LocalDate sourcePublicationDate,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        OffsetDateTime availabilityTime,
        boolean dryRun
    ) {
        if (payloadBytes == null || payloadBytes.length == 0) {
            throw new IllegalArgumentException("AMFI Market-Cap payload cannot be null or empty");
        }
        if (rankingPeriodEndDate == null || effectiveFrom == null) {
            throw new IllegalArgumentException("Ranking period end date and effectiveFrom date must be provided");
        }

        String sha256 = computeSha256(payloadBytes);
        OffsetDateTime retrievalTime = OffsetDateTime.now();
        OffsetDateTime resolvedAvailability = availabilityTime != null ? availabilityTime : retrievalTime;

        // 1. Parse Excel payload
        AmfiMarketCapParser.ParseResult parseResult = parser.parse(payloadBytes);
        List<String> validationErrors = new ArrayList<>(parseResult.validationErrors());
        List<String> warnings = new ArrayList<>(parseResult.warnings());

        if (parseResult.records().isEmpty()) {
            validationErrors.add("AMFI spreadsheet contains 0 parsed records");
            return new IngestionResult(0, 0, 0, 0, validationErrors, warnings, null, sha256);
        }

        if (dryRun) {
            log.info("Dry-run validation successful for AMFI Market-Cap: {} records parsed, {} errors, {} warnings",
                parseResult.records().size(), validationErrors.size(), warnings.size());
            return new IngestionResult(
                parseResult.records().size(), 0, 0, 0,
                validationErrors, warnings, null, sha256
            );
        }

        // 2. Persist or retrieve SourceArtifact
        DataSource dataSource = dataSourceRepository.findByCode("AMFI_MCAP_CATEGORIZATION")
            .orElseGet(() -> dataSourceRepository.save(new DataSource(
                "AMFI_MCAP_CATEGORIZATION",
                "AMFI Market Capitalization Categorization of Listed Companies",
                "https://www.amfiindia.com"
            )));

        SourceArtifact artifact = sourceArtifactRepository.findBySha256Hash(sha256)
            .orElseGet(() -> {
                SourceArtifact sa = new SourceArtifact(dataSource, retrievalTime, "AMFI_MCAP_CATEGORIZATION_XLSX", sha256, (long) payloadBytes.length);
                sa.setStorageUri(sourceUri != null ? sourceUri : "AMFI_MCAP_" + rankingPeriodEndDate);
                sa.setPayloadBlob(payloadBytes);
                return sourceArtifactRepository.save(sa);
            });

        if (artifact.getPayloadBlob() == null) {
            artifact.setPayloadBlob(payloadBytes);
            artifact = sourceArtifactRepository.save(artifact);
        }

        // 3. Check for existing ingestion for this ranking period
        boolean alreadyIngested = classificationRepository.existsByRankingPeriodEndDateAndRevisionSeq(rankingPeriodEndDate, 1);
        if (alreadyIngested) {
            long existingCount = classificationRepository.countByRankingPeriodEndDate(rankingPeriodEndDate);
            log.info("AMFI Market-Cap classifications for period end {} already present ({} records). Re-ingestion skipped for idempotency.",
                rankingPeriodEndDate, existingCount);
            return new IngestionResult(
                parseResult.records().size(), 0, existingCount, 0,
                validationErrors, warnings, artifact.getId(), sha256
            );
        }

        // 4. Resolve securities in bulk via ISIN
        List<SecurityIdentifier> isinIdentifiers = securityIdentifierRepository.findByIdType("ISIN");
        Map<String, Security> isinToSecurityMap = new HashMap<>();
        for (SecurityIdentifier ident : isinIdentifiers) {
            if (ident.getSecurity() != null && ident.getIdValue() != null) {
                isinToSecurityMap.putIfAbsent(ident.getIdValue().trim().toUpperCase(Locale.ROOT), ident.getSecurity());
            }
        }

        // 5. Build entity records
        List<SecurityMarketCapClassification> entitiesToSave = new ArrayList<>(parseResult.records().size());
        long unmappedCount = 0;
        Set<String> seenIsinsInPeriod = new HashSet<>();

        for (AmfiMarketCapParser.ParsedMarketCapRecord rec : parseResult.records()) {
            if (rec.isin() == null || rec.isin().isBlank()) {
                continue;
            }
            String cleanIsin = rec.isin().trim().toUpperCase(Locale.ROOT);
            if (!seenIsinsInPeriod.add(cleanIsin)) {
                log.warn("Duplicate ISIN {} encountered in AMFI spreadsheet for period {}; skipping duplicate entry.",
                    cleanIsin, rankingPeriodEndDate);
                continue;
            }

            SecurityMarketCapClassification entity = new SecurityMarketCapClassification();
            entity.setIsin(cleanIsin);
            entity.setCompanyName(rec.companyName());
            entity.setBseSymbol(rec.bseSymbol());
            entity.setNseSymbol(rec.nseSymbol());
            entity.setMarketCapRank(rec.rank());
            entity.setMarketCapCategory(rec.officialCategory());
            entity.setAvgMarketCapCrores(rec.avgMarketCapCrores());
            entity.setRankingPeriod(rankingPeriod != null ? rankingPeriod : "Period ended " + rankingPeriodEndDate);
            entity.setRankingPeriodEndDate(rankingPeriodEndDate);
            entity.setSourcePublicationDate(sourcePublicationDate != null ? sourcePublicationDate : rankingPeriodEndDate.plusDays(5));
            entity.setEffectiveFrom(effectiveFrom);
            entity.setEffectiveTo(effectiveTo);
            entity.setAvailabilityTime(resolvedAvailability);
            entity.setIngestionTime(OffsetDateTime.now());
            entity.setSourceArtifact(artifact);
            entity.setRevisionSeq(1);
            entity.setLatestRevision(true);
            entity.setValidationStatus("VALIDATED");

            Security mappedSecurity = isinToSecurityMap.get(rec.isin());
            if (mappedSecurity != null) {
                entity.setSecurity(mappedSecurity);
                entity.setMappingStatus("MAPPED");
            } else {
                entity.setMappingStatus("UNMAPPED");
                unmappedCount++;
            }

            entitiesToSave.add(entity);
        }

        // 6. Batch save
        classificationRepository.saveAll(entitiesToSave);
        log.info("Successfully ingested {} AMFI Market-Cap classifications for period end {} (mapped: {}, unmapped: {})",
            entitiesToSave.size(), rankingPeriodEndDate, entitiesToSave.size() - unmappedCount, unmappedCount);

        return new IngestionResult(
            parseResult.records().size(),
            entitiesToSave.size(),
            0,
            unmappedCount,
            validationErrors,
            warnings,
            artifact.getId(),
            sha256
        );
    }

    private String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
