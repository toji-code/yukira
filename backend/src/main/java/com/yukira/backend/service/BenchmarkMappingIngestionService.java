package com.yukira.backend.service;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@SuppressWarnings("null")
public class BenchmarkMappingIngestionService {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkMappingIngestionService.class);

    private final SchemeRepository schemeRepository;
    private final SchemeOptionRepository schemeOptionRepository;
    private final BenchmarkRepository benchmarkRepository;
    private final SchemeBenchmarkHistRepository schemeBenchmarkHistRepository;
    private final SourceArtifactRepository sourceArtifactRepository;
    private final DataSourceRepository dataSourceRepository;
    private final BenchmarkMasterService benchmarkMasterService;

    public BenchmarkMappingIngestionService(
        SchemeRepository schemeRepository,
        SchemeOptionRepository schemeOptionRepository,
        BenchmarkRepository benchmarkRepository,
        SchemeBenchmarkHistRepository schemeBenchmarkHistRepository,
        SourceArtifactRepository sourceArtifactRepository,
        DataSourceRepository dataSourceRepository,
        BenchmarkMasterService benchmarkMasterService
    ) {
        this.schemeRepository = schemeRepository;
        this.schemeOptionRepository = schemeOptionRepository;
        this.benchmarkRepository = benchmarkRepository;
        this.schemeBenchmarkHistRepository = schemeBenchmarkHistRepository;
        this.sourceArtifactRepository = sourceArtifactRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.benchmarkMasterService = benchmarkMasterService;
    }

    public record BenchmarkMappingItemDto(
        String schemeCode,
        String amfiCode,
        String benchmarkCode,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Boolean isPrimary,
        String sourceReference
    ) {}

    public record BenchmarkMappingIngestionSummary(
        int totalProcessed,
        int insertedCount,
        int skippedCount,
        int conflictCount,
        Long sourceArtifactId,
        String payloadSha256
    ) {}

    /**
     * Ingests a batch of benchmark mapping items idempotently and provenance-preservingly.
     */
    @Transactional
    public BenchmarkMappingIngestionSummary ingestMappingBatch(
        String sourceUri,
        List<BenchmarkMappingItemDto> items
    ) {
        benchmarkMasterService.ensureCanonicalBenchmarkMaster();

        if (items == null || items.isEmpty()) {
            return new BenchmarkMappingIngestionSummary(0, 0, 0, 0, null, null);
        }

        final String effectiveUri = (sourceUri != null && !sourceUri.isBlank())
            ? sourceUri
            : "https://www.amfiindia.com/official-benchmark-master";

        DataSource ds = dataSourceRepository.findByCode("AMFI_MASTER")
            .orElseGet(() -> dataSourceRepository.save(new DataSource("AMFI_MASTER", "AMFI Official Master", "AMFI")));

        // 1. Create or retrieve SourceArtifact
        String rawContent = items.toString();
        String sha256 = computeSha256(rawContent);

        SourceArtifact artifact = sourceArtifactRepository.findBySha256Hash(sha256)
            .orElseGet(() -> {
                SourceArtifact sa = new SourceArtifact(
                    ds,
                    OffsetDateTime.now(),
                    "BENCHMARK_MAPPING",
                    sha256,
                    (long) rawContent.getBytes(StandardCharsets.UTF_8).length
                );
                sa.setStorageUri(effectiveUri);
                sa.setPayloadBlob(rawContent.getBytes(StandardCharsets.UTF_8));
                return sourceArtifactRepository.save(sa);
            });

        int inserted = 0;
        int skipped = 0;
        int conflicts = 0;

        // Cache scheme lookups and existing history records
        Map<String, Scheme> schemeMap = new HashMap<>();
        List<SchemeBenchmarkHist> allExistingHist = schemeBenchmarkHistRepository.findAll();
        Map<Long, List<SchemeBenchmarkHist>> existingHistMap = new HashMap<>();
        for (SchemeBenchmarkHist h : allExistingHist) {
            existingHistMap.computeIfAbsent(h.getScheme().getId(), k -> new ArrayList<>()).add(h);
        }

        for (BenchmarkMappingItemDto item : items) {
            Scheme scheme = null;
            if (item.amfiCode() != null && !item.amfiCode().isBlank()) {
                Optional<SchemeOption> opt = schemeOptionRepository.findByAmfiCode(item.amfiCode());
                if (opt.isPresent() && opt.get().getPlan() != null && opt.get().getPlan().getScheme() != null) {
                    scheme = opt.get().getPlan().getScheme();
                }
            }
            if (scheme == null && item.schemeCode() != null && !item.schemeCode().isBlank()) {
                scheme = schemeMap.computeIfAbsent(item.schemeCode(),
                    code -> schemeRepository.findByCode(code).orElse(null));
            }

            if (scheme == null) {
                log.warn("Unknown scheme for benchmark mapping: amfiCode={}, schemeCode={}", item.amfiCode(), item.schemeCode());
                conflicts++;
                continue;
            }

            Optional<Benchmark> benchmarkOpt = benchmarkRepository.findByCode(item.benchmarkCode());
            if (benchmarkOpt.isEmpty()) {
                log.warn("Unknown benchmark code for scheme {}: {}", scheme.getCode(), item.benchmarkCode());
                conflicts++;
                continue;
            }
            Benchmark benchmark = benchmarkOpt.get();

            LocalDate effFrom = item.effectiveFrom() != null ? item.effectiveFrom() : LocalDate.of(2021, 1, 1);
            Boolean isPrimary = item.isPrimary() != null ? item.isPrimary() : true;

            // Idempotency check against pre-fetched scheme_benchmark_hist map
            List<SchemeBenchmarkHist> existingList = existingHistMap.getOrDefault(scheme.getId(), Collections.emptyList());
            boolean exactExists = existingList.stream().anyMatch(e ->
                e.getBenchmark().getId().equals(benchmark.getId()) &&
                e.getEffectiveFrom().equals(effFrom) &&
                Objects.equals(e.getIsPrimary(), isPrimary)
            );

            if (exactExists) {
                skipped++;
                continue;
            }

            // Close out previous active primary mapping if new effectiveFrom is later
            if (isPrimary) {
                for (SchemeBenchmarkHist oldHist : existingList) {
                    if (Boolean.TRUE.equals(oldHist.getIsPrimary()) &&
                        (oldHist.getEffectiveTo() == null || oldHist.getEffectiveTo().isAfter(effFrom))) {
                        if (!oldHist.getEffectiveFrom().isAfter(effFrom)) {
                            oldHist.setEffectiveTo(effFrom.minusDays(1));
                            schemeBenchmarkHistRepository.save(oldHist);
                        }
                    }
                }
            }

            SchemeBenchmarkHist newHist = new SchemeBenchmarkHist(
                scheme,
                benchmark,
                effFrom,
                item.effectiveTo(),
                isPrimary
            );
            schemeBenchmarkHistRepository.save(newHist);
            inserted++;
            log.info("Ingested primary benchmark mapping: scheme={}, benchmark={}, effectiveFrom={}",
                scheme.getName(), benchmark.getCode(), effFrom);
        }

        return new BenchmarkMappingIngestionSummary(
            items.size(),
            inserted,
            skipped,
            conflicts,
            artifact.getId(),
            sha256
        );
    }

    private String computeSha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "0000000000000000000000000000000000000000000000000000000000000000";
        }
    }
}
