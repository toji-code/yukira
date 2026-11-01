package com.yukira.backend.service.portfolio.cas;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.*;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.portfolio.InvestorPortfolioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.*;

@Service
@Transactional
public class CasImportService {

    private static final Logger log = LoggerFactory.getLogger(CasImportService.class);

    private final InvestorPortfolioService investorPortfolioService;
    private final PortfolioImportRepository importRepository;
    private final PortfolioImportHoldingRepository importHoldingRepository;
    private final InvestorPortfolioHoldingRepository investorHoldingRepository;
    private final PortfolioImportTransactionRepository importTransactionRepository;
    private final InvestorTransactionRepository investorTransactionRepository;
    private final SourceArtifactRepository sourceArtifactRepository;
    private final DataSourceRepository dataSourceRepository;
    private final CasParser casParser;
    private final CasSchemeResolutionService resolutionService;

    public CasImportService(
        InvestorPortfolioService investorPortfolioService,
        PortfolioImportRepository importRepository,
        PortfolioImportHoldingRepository importHoldingRepository,
        InvestorPortfolioHoldingRepository investorHoldingRepository,
        PortfolioImportTransactionRepository importTransactionRepository,
        InvestorTransactionRepository investorTransactionRepository,
        SourceArtifactRepository sourceArtifactRepository,
        DataSourceRepository dataSourceRepository,
        CasParser casParser,
        CasSchemeResolutionService resolutionService
    ) {
        this.investorPortfolioService = investorPortfolioService;
        this.importRepository = importRepository;
        this.importHoldingRepository = importHoldingRepository;
        this.investorHoldingRepository = investorHoldingRepository;
        this.importTransactionRepository = importTransactionRepository;
        this.investorTransactionRepository = investorTransactionRepository;
        this.sourceArtifactRepository = sourceArtifactRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.casParser = casParser;
        this.resolutionService = resolutionService;
    }

    public CasImportResultDto stageImport(String auth0Subject, String filename, InputStream inputStream) {
        return stageImport(auth0Subject, filename, inputStream, null);
    }

    public CasImportResultDto stageImport(String auth0Subject, String filename, InputStream inputStream, String password) {
        Investor investor = investorPortfolioService.resolveInvestor(auth0Subject);

        byte[] payloadBytes;
        try {
            payloadBytes = inputStream != null ? inputStream.readAllBytes() : new byte[0];
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read CAS input payload: " + e.getMessage());
        }

        if (payloadBytes.length == 0) {
            throw new IllegalArgumentException("CAS import payload is empty.");
        }

        // Calculate SHA-256 hash for raw file provenance
        String sha256Hash = computeSha256(payloadBytes);

        // Fetch or create DataSource for CAS uploads
        DataSource casSource = dataSourceRepository.findByCode("CAS_INVESTOR_UPLOAD")
            .orElseGet(() -> dataSourceRepository.save(new DataSource("CAS_INVESTOR_UPLOAD", "Investor CAS Statement Import Feed", "https://www.camsonline.com")));

        // Persist raw source artifact
        SourceArtifact artifact = new SourceArtifact(
            casSource,
            OffsetDateTime.now(),
            "CAS_STATEMENT",
            sha256Hash,
            (long) payloadBytes.length
        );
        SourceArtifact savedArtifact = sourceArtifactRepository.save(artifact);

        // Parse file (passing optional password, password is NEVER logged or persisted)
        CasParser.ParseResult parseResult = casParser.parse(new ByteArrayInputStream(payloadBytes), filename, password);

        boolean isParseFailed = !"SUCCESS".equals(parseResult.parseStatus()) && (parseResult.records().isEmpty() && parseResult.transactions().isEmpty());

        PortfolioImport portfolioImport = new PortfolioImport();
        portfolioImport.setInvestor(investor);
        portfolioImport.setSourceFilename(filename != null ? filename : "cas_import.pdf");
        portfolioImport.setFileType(filename != null && filename.toLowerCase().endsWith(".pdf") ? "CAS_PDF_STATEMENT" : "CAS_STATEMENT");
        portfolioImport.setStatus(isParseFailed ? "FAILED" : "PENDING_REVIEW");
        portfolioImport.setStatementDate(parseResult.statementDate());
        portfolioImport.setParserVersion(parseResult.parserVersion());
        portfolioImport.setSourceArtifact(savedArtifact);

        int resolvedCount = 0;
        int unresolvedCount = 0;
        int ambiguousCount = 0;

        List<PortfolioImportHolding> stagedHoldings = new ArrayList<>();

        for (CasImportParsedRecordDto rec : parseResult.records()) {
            CasSchemeResolutionService.ResolutionResult res = resolutionService.resolve(rec);

            PortfolioImportHolding importHolding = new PortfolioImportHolding();
            importHolding.setPortfolioImport(portfolioImport);
            importHolding.setRawSchemeName(rec.rawSchemeName());
            importHolding.setFolioNumber(rec.folioNumber());
            importHolding.setIsin(rec.isin());
            importHolding.setAmfiCode(rec.amfiCode());
            importHolding.setUnits(rec.units());
            importHolding.setCostBasisAmount(rec.costBasisAmount());
            importHolding.setMarketValue(rec.marketValue());
            importHolding.setNav(rec.nav());
            importHolding.setResolvedSchemeOption(res.schemeOption());
            importHolding.setResolutionStatus(res.status());
            importHolding.setResolutionMethod(res.method());
            importHolding.setResolutionNotes(res.notes());

            if ("RESOLVED".equals(res.status())) resolvedCount++;
            else if ("AMBIGUOUS".equals(res.status())) ambiguousCount++;
            else unresolvedCount++;

            stagedHoldings.add(importHolding);
        }

        portfolioImport.setTotalRecordsDetected(parseResult.records().size());
        portfolioImport.setResolvedRecordsCount(resolvedCount);
        portfolioImport.setUnresolvedRecordsCount(unresolvedCount);
        portfolioImport.setAmbiguousRecordsCount(ambiguousCount);
        portfolioImport.setHoldings(stagedHoldings);

        PortfolioImport savedImport = importRepository.save(portfolioImport);

        // Stage transactions if statement contains dated transaction history
        if (parseResult.transactions() != null && !parseResult.transactions().isEmpty()) {
            List<PortfolioImportTransaction> stagedTxs = new ArrayList<>();
            for (CasImportParsedTransactionDto ptx : parseResult.transactions()) {
                CasImportParsedRecordDto schemeLookup = new CasImportParsedRecordDto(
                    ptx.rawSchemeName(), ptx.folioNumber(), ptx.isin(), ptx.amfiCode(), ptx.units(), null, null, ptx.nav()
                );
                CasSchemeResolutionService.ResolutionResult res = resolutionService.resolve(schemeLookup);
                String normalizedType = normalizeType(ptx.rawTypeDescription());
                String dedupHash = computeTxDedupHash(investor.getId(), ptx.transactionDate(), normalizedType, ptx.units(), ptx.amount());

                PortfolioImportTransaction pit = new PortfolioImportTransaction();
                pit.setPortfolioImport(savedImport);
                pit.setRawSchemeName(ptx.rawSchemeName());
                pit.setFolioNumber(ptx.folioNumber());
                pit.setIsin(ptx.isin());
                pit.setAmfiCode(ptx.amfiCode());
                pit.setTransactionDate(ptx.transactionDate());
                pit.setRawTypeDescription(ptx.rawTypeDescription());
                pit.setNormalizedType(normalizedType);
                pit.setUnits(ptx.units());
                pit.setNav(ptx.nav());
                pit.setAmount(ptx.amount());
                pit.setResolvedSchemeOption(res.schemeOption());
                pit.setResolutionStatus(res.status());
                pit.setResolutionMethod(res.method());
                pit.setResolutionNotes(res.notes());
                pit.setDedupHash(dedupHash);

                stagedTxs.add(pit);
            }
            importTransactionRepository.saveAll(stagedTxs);
            log.info("Staged {} transactions for CAS Import #{}", stagedTxs.size(), savedImport.getId());
        }

        log.info("Staged CAS Import #{} (Artifact #{}, SHA-256 {}) for investor #{}: {} detected, {} resolved, {} unresolved, {} ambiguous",
            savedImport.getId(), savedArtifact.getId(), sha256Hash.substring(0, 8), investor.getId(), parseResult.records().size(), resolvedCount, unresolvedCount, ambiguousCount);

        return toImportResultDto(savedImport, parseResult.limitations());
    }

    @Transactional(readOnly = true)
    public CasImportResultDto getImportDetails(String auth0Subject, Long importId) {
        Investor investor = investorPortfolioService.resolveInvestor(auth0Subject);
        PortfolioImport portfolioImport = importRepository.findByIdAndInvestorId(importId, investor.getId())
            .orElseThrow(() -> new IllegalArgumentException("Import #" + importId + " not found or access denied."));

        return toImportResultDto(portfolioImport, List.of());
    }

    public CasImportConfirmResponseDto confirmImport(String auth0Subject, Long importId, String mode) {
        Investor investor = investorPortfolioService.resolveInvestor(auth0Subject);
        PortfolioImport portfolioImport = importRepository.findByIdAndInvestorId(importId, investor.getId())
            .orElseThrow(() -> new IllegalArgumentException("Import #" + importId + " not found or access denied."));

        if ("CONFIRMED".equals(portfolioImport.getStatus())) {
            return new CasImportConfirmResponseDto(
                importId, "CONFIRMED", 0, 0, 0, 0,
                portfolioImport.getConfirmedAt(),
                List.of("Import #" + importId + " was already confirmed previously.")
            );
        }

        String effectiveMode = (mode != null && mode.equalsIgnoreCase("REPLACE")) ? "REPLACE" : "MERGE";

        if ("REPLACE".equals(effectiveMode)) {
            investorHoldingRepository.deleteByInvestorId(investor.getId());
        }

        int addedCount = 0;
        int updatedCount = 0;
        int unresolvedSkipped = 0;

        // Group & aggregate multi-folio holdings deterministically at scheme level
        Map<Long, BigDecimal> aggregatedUnits = new LinkedHashMap<>();
        Map<Long, BigDecimal> aggregatedCostBasis = new LinkedHashMap<>();
        Map<Long, SchemeOption> schemeOptions = new LinkedHashMap<>();

        List<PortfolioImportHolding> importHoldings = importHoldingRepository.findByPortfolioImportId(importId);
        for (PortfolioImportHolding ih : importHoldings) {
            if ("RESOLVED".equals(ih.getResolutionStatus()) && ih.getResolvedSchemeOption() != null) {
                Long optId = ih.getResolvedSchemeOption().getId();
                schemeOptions.put(optId, ih.getResolvedSchemeOption());

                BigDecimal u = ih.getUnits() != null ? ih.getUnits() : BigDecimal.ZERO;
                aggregatedUnits.put(optId, aggregatedUnits.getOrDefault(optId, BigDecimal.ZERO).add(u));

                if (ih.getCostBasisAmount() != null) {
                    BigDecimal cb = ih.getCostBasisAmount();
                    aggregatedCostBasis.put(optId, aggregatedCostBasis.getOrDefault(optId, BigDecimal.ZERO).add(cb));
                }
            } else {
                unresolvedSkipped++;
            }
        }

        for (Map.Entry<Long, BigDecimal> entry : aggregatedUnits.entrySet()) {
            Long schemeOptionId = entry.getKey();
            BigDecimal totalUnits = entry.getValue();
            BigDecimal totalCostBasis = aggregatedCostBasis.get(schemeOptionId);
            SchemeOption option = schemeOptions.get(schemeOptionId);

            Optional<InvestorPortfolioHolding> existingOpt = investorHoldingRepository.findByInvestorIdAndSchemeOptionId(investor.getId(), schemeOptionId);

            if (existingOpt.isPresent()) {
                InvestorPortfolioHolding existing = existingOpt.get();
                existing.setUnits(totalUnits);
                if (totalCostBasis != null) {
                    existing.setCostBasisAmount(totalCostBasis);
                }
                investorHoldingRepository.save(existing);
                updatedCount++;
            } else {
                InvestorPortfolioHolding newHolding = new InvestorPortfolioHolding();
                newHolding.setInvestor(investor);
                newHolding.setSchemeOption(option);
                newHolding.setUnits(totalUnits);
                newHolding.setCostBasisAmount(totalCostBasis);
                investorHoldingRepository.save(newHolding);
                addedCount++;
            }
        }

        // Process staged transactions into investor_transaction ledger
        List<PortfolioImportTransaction> stagedTxs = importTransactionRepository.findByPortfolioImportIdOrderByTransactionDateDesc(importId);
        int txAdded = 0;
        int txDuplicates = 0;

        for (PortfolioImportTransaction pit : stagedTxs) {
            if ("RESOLVED".equals(pit.getResolutionStatus()) && pit.getResolvedSchemeOption() != null) {
                boolean exists = pit.getDedupHash() != null && investorTransactionRepository.existsByInvestorIdAndDedupHash(investor.getId(), pit.getDedupHash());
                if (!exists) {
                    InvestorTransaction itx = new InvestorTransaction();
                    itx.setInvestor(investor);
                    itx.setSchemeOption(pit.getResolvedSchemeOption());
                    itx.setTransactionType(pit.getNormalizedType());
                    itx.setTransactionDate(pit.getTransactionDate());
                    itx.setSettlementDate(pit.getTransactionDate());
                    itx.setUnits(pit.getUnits() != null ? pit.getUnits() : BigDecimal.ZERO);
                    itx.setNav(pit.getNav());
                    BigDecimal amt = pit.getAmount() != null ? pit.getAmount() : BigDecimal.ZERO;
                    itx.setGrossAmount(amt);
                    itx.setNetAmount(amt);
                    itx.setChargesOrTaxes(BigDecimal.ZERO);
                    itx.setDividendAmount(BigDecimal.ZERO);
                    itx.setSourceAttribution("CAS_IMPORT");
                    itx.setOriginalDescription(pit.getRawTypeDescription());
                    itx.setImportBatchId(String.valueOf(importId));
                    itx.setReconciliationStatus("UNRECONCILED");
                    itx.setIsReversed(false);
                    itx.setDedupHash(pit.getDedupHash());

                    investorTransactionRepository.save(itx);
                    txAdded++;
                } else {
                    pit.setResolutionStatus("DUPLICATE");
                    importTransactionRepository.save(pit);
                    txDuplicates++;
                }
            }
        }

        portfolioImport.setStatus("CONFIRMED");
        portfolioImport.setConfirmedAt(ZonedDateTime.now());
        importRepository.save(portfolioImport);

        log.info("Confirmed CAS Import #{}: {} scheme holdings added, {} updated, {} unresolved skipped, {} transactions persisted, {} duplicates skipped",
            importId, addedCount, updatedCount, unresolvedSkipped, txAdded, txDuplicates);

        return new CasImportConfirmResponseDto(
            importId,
            "CONFIRMED",
            addedCount,
            updatedCount,
            0,
            unresolvedSkipped,
            portfolioImport.getConfirmedAt(),
            List.of("Import confirmed successfully in " + effectiveMode + " mode with " + txAdded + " transactions recorded into ledger.")
        );
    }

    @Transactional(readOnly = true)
    public List<CasImportResultDto> listInvestorImports(String auth0Subject) {
        Investor investor = investorPortfolioService.resolveInvestor(auth0Subject);
        List<PortfolioImport> imports = importRepository.findByInvestorIdOrderByCreatedAtDesc(investor.getId());
        return imports.stream().map(imp -> toImportResultDto(imp, List.of())).toList();
    }

    private CasImportResultDto toImportResultDto(PortfolioImport imp, List<String> limitations) {
        String qualityState = "VALID";
        if ("FAILED".equals(imp.getStatus())) {
            qualityState = "FAILED";
        } else if (imp.getUnresolvedRecordsCount() > 0 || imp.getAmbiguousRecordsCount() > 0) {
            qualityState = "DATA_QUALITY_LIMITED";
        }

        String casProvider = "UNKNOWN";
        if (imp.getParserVersion() != null && imp.getParserVersion().contains("CAMS")) {
            casProvider = "CAMS";
        } else if (imp.getParserVersion() != null && imp.getParserVersion().contains("KFIN")) {
            casProvider = "KFINTECH";
        }

        List<CasImportHoldingDetailDto> holdingDtos = imp.getHoldings() != null ? imp.getHoldings().stream().map(h -> {
            String fundName = h.getResolvedSchemeOption() != null && h.getResolvedSchemeOption().getPlan() != null && h.getResolvedSchemeOption().getPlan().getScheme() != null
                ? h.getResolvedSchemeOption().getPlan().getScheme().getName()
                : null;
            String amcName = h.getResolvedSchemeOption() != null && h.getResolvedSchemeOption().getPlan() != null && h.getResolvedSchemeOption().getPlan().getScheme() != null && h.getResolvedSchemeOption().getPlan().getScheme().getAmc() != null
                ? h.getResolvedSchemeOption().getPlan().getScheme().getAmc().getLegalName()
                : null;

            return new CasImportHoldingDetailDto(
                h.getId(),
                h.getRawSchemeName(),
                h.getFolioNumber(),
                h.getIsin(),
                h.getAmfiCode(),
                h.getUnits(),
                h.getCostBasisAmount(),
                h.getMarketValue(),
                h.getNav(),
                h.getResolvedSchemeOption() != null ? h.getResolvedSchemeOption().getId() : null,
                fundName,
                amcName,
                h.getResolutionStatus(),
                h.getResolutionMethod(),
                h.getResolutionNotes()
            );
        }).toList() : List.of();

        return new CasImportResultDto(
            imp.getId(),
            imp.getSourceFilename(),
            imp.getFileType(),
            imp.getStatus(),
            qualityState,
            casProvider,
            imp.getTotalRecordsDetected(),
            imp.getResolvedRecordsCount(),
            imp.getUnresolvedRecordsCount(),
            imp.getAmbiguousRecordsCount(),
            imp.getSourceArtifact() != null ? imp.getSourceArtifact().getId() : null,
            imp.getSourceArtifact() != null ? imp.getSourceArtifact().getSha256Hash() : null,
            imp.getStatementDate(),
            imp.getParserVersion(),
            imp.getCreatedAt(),
            imp.getConfirmedAt(),
            holdingDtos,
            limitations
        );
    }

    private String normalizeType(String raw) {
        if (raw == null) return "PURCHASE";
        String s = raw.trim().toUpperCase(Locale.ROOT);
        if (s.contains("SIP") || s.contains("SYSTEMATIC")) return "SIP_PURCHASE";
        if (s.contains("STP IN") || s.contains("STP PURCHASE")) return "STP_PURCHASE";
        if (s.contains("STP OUT") || s.contains("STP REDEMPTION")) return "STP_REDEMPTION";
        if (s.contains("SWITCH IN") || s.contains("SWITCH-IN")) return "SWITCH_IN";
        if (s.contains("SWITCH OUT") || s.contains("SWITCH-OUT")) return "SWITCH_OUT";
        if (s.contains("PURCHASE") || s.contains("SUBSCRIPTION") || s.contains("NEW FUND")) return "PURCHASE";
        if (s.contains("REDEMPTION") || s.contains("REPURCHASE") || s.contains("WITHDRAWAL")) return "REDEMPTION";
        if (s.contains("DIVIDEND REINVEST") || s.contains("IDCW REINVEST")) return "DIVIDEND_REINVESTMENT";
        if (s.contains("DIVIDEND") || s.contains("IDCW")) return "DIVIDEND_PAYOUT";
        if (s.contains("BONUS")) return "BONUS";
        if (s.contains("REVERSAL")) return "REVERSAL";
        return "OTHER";
    }

    private String computeTxDedupHash(Long investorId, LocalDate date, String type, BigDecimal units, BigDecimal amt) {
        String raw = investorId + ":" + date + ":" + type + ":" + (units != null ? units.stripTrailingZeros() : "0") + ":" + (amt != null ? amt.stripTrailingZeros() : "0");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
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
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute SHA-256 hash: " + e.getMessage(), e);
        }
    }
}
