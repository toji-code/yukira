package com.yukira.backend.service;

import com.yukira.backend.benchmark.CategoryBenchmarkMapping;
import com.yukira.backend.benchmark.CategoryBenchmarkMapping.BenchmarkDescriptor;
import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.domain.entity.Scheme;
import com.yukira.backend.domain.entity.SchemeBenchmarkHist;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.domain.entity.SchemePlan;
import com.yukira.backend.repository.BenchmarkRepository;
import com.yukira.backend.repository.SchemeBenchmarkHistRepository;
import com.yukira.backend.repository.SchemeRepository;
import com.yukira.backend.universe.EquityUniverseCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class BenchmarkResolutionService {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkResolutionService.class);
    public static final String DEFAULT_BENCHMARK_CODE = "NIFTY_500_TRI";

    private final SchemeBenchmarkHistRepository schemeBenchmarkHistRepository;
    private final BenchmarkRepository benchmarkRepository;
    private final SchemeRepository schemeRepository;

    public BenchmarkResolutionService(
        SchemeBenchmarkHistRepository schemeBenchmarkHistRepository,
        BenchmarkRepository benchmarkRepository
    ) {
        this(schemeBenchmarkHistRepository, benchmarkRepository, null);
    }

    @Autowired
    public BenchmarkResolutionService(
        SchemeBenchmarkHistRepository schemeBenchmarkHistRepository,
        BenchmarkRepository benchmarkRepository,
        @Autowired(required = false) SchemeRepository schemeRepository
    ) {
        this.schemeBenchmarkHistRepository = schemeBenchmarkHistRepository;
        this.benchmarkRepository = benchmarkRepository;
        this.schemeRepository = schemeRepository;
    }

    public record BenchmarkResolutionResult(
        Benchmark benchmark,
        boolean isExplicitMapping,
        String resolutionMode,
        LocalDate effectiveFrom,
        String message,
        String methodologyStatus,
        EquityUniverseCategory category,
        BenchmarkDescriptor descriptor
    ) {
        public BenchmarkResolutionResult(
            Benchmark benchmark,
            boolean isExplicitMapping,
            String resolutionMode,
            LocalDate effectiveFrom,
            String message
        ) {
            this(
                benchmark,
                isExplicitMapping,
                resolutionMode,
                effectiveFrom,
                message,
                isExplicitMapping ? "AUTHORIZED_EXPLICIT_MAPPING" : ("UNMAPPED".equals(resolutionMode) ? "UNMAPPED" : "CANDIDATE_METHODOLOGY_MAPPING"),
                null,
                null
            );
        }

        public boolean isTri() {
            return benchmark != null && "TRI".equalsIgnoreCase(benchmark.getReturnVariant());
        }

        public boolean isMapped() {
            return benchmark != null && !"UNMAPPED".equalsIgnoreCase(resolutionMode);
        }

        public String getCode() {
            return benchmark != null ? benchmark.getCode() : null;
        }

        public String getBenchmarkCode() {
            return benchmark != null ? benchmark.getCode() : null;
        }
    }

    /**
     * Resolves the authoritative primary benchmark for a scheme option as of a given analytical date.
     */
    @Transactional
    public BenchmarkResolutionResult resolveBenchmarkForOption(SchemeOption option, LocalDate asOfDate) {
        if (option == null) {
            throw new IllegalArgumentException("option must not be null");
        }
        SchemePlan plan = option.getPlan();
        Scheme scheme = plan != null ? plan.getScheme() : null;
        if (scheme == null) {
            return new BenchmarkResolutionResult(
                null,
                false,
                "UNMAPPED",
                null,
                String.format("SchemeOption #%d has no associated scheme entity", option.getId()),
                "UNMAPPED",
                null,
                null
            );
        }
        return resolvePrimaryBenchmark(scheme.getId(), asOfDate);
    }

    @Transactional
    public BenchmarkResolutionResult resolveBenchmarkForSchemeOption(SchemeOption option) {
        return resolveBenchmarkForOption(option, LocalDate.now());
    }

    /**
     * Resolves the authoritative primary benchmark for a scheme as of a given analytical date.
     *
     * Precedence Order:
     *   1. Explicit historical mapping in scheme_benchmark_hist (AUTHORIZED_EXPLICIT_MAPPING)
     *   2. Candidate Category-level mapping for the 5 reference universe categories (CANDIDATE_METHODOLOGY_MAPPING)
     *   3. Explicit UNMAPPED status for schemes outside the 5 reference categories without explicit mapping
     *   4. Default fallback diagnostic if category is undetermined
     *
     * Strict PIT Invariant:
     *   effective_from <= asOfDate AND (effective_to IS NULL OR effective_to >= asOfDate)
     */
    @Transactional
    public BenchmarkResolutionResult resolvePrimaryBenchmark(Long schemeId, LocalDate asOfDate) {
        if (schemeId == null) {
            throw new IllegalArgumentException("schemeId must not be null");
        }
        if (asOfDate == null) {
            asOfDate = LocalDate.now();
        }

        // 1. Explicit historical mapping takes absolute precedence
        Optional<SchemeBenchmarkHist> explicitOpt = schemeBenchmarkHistRepository
            .findPrimaryBenchmarkAsOfDate(schemeId, asOfDate);

        if (explicitOpt.isPresent()) {
            SchemeBenchmarkHist hist = explicitOpt.get();
            return new BenchmarkResolutionResult(
                hist.getBenchmark(),
                true,
                "EXPLICIT_HISTORICAL_MAPPING",
                hist.getEffectiveFrom(),
                String.format("Resolved explicit benchmark %s (%s) effective from %s",
                    hist.getBenchmark().getCode(), hist.getBenchmark().getName(), hist.getEffectiveFrom()),
                "AUTHORIZED_EXPLICIT_MAPPING",
                null,
                null
            );
        }

        // 2. Candidate Category-level default mapping for reference universe categories
        if (schemeRepository != null) {
            Optional<Scheme> schemeOpt = schemeRepository.findById(schemeId);
            if (schemeOpt.isPresent()) {
                Scheme scheme = schemeOpt.get();
                EquityUniverseCategory category = EquityUniverseCategory.categorize(
                    scheme.getCategory(), scheme.getSubcategory(), scheme.getName()
                );

                if (category != null) {
                    Optional<BenchmarkDescriptor> descOpt = CategoryBenchmarkMapping.getDescriptorForCategory(category);
                    if (descOpt.isPresent()) {
                        BenchmarkDescriptor desc = descOpt.get();
                        Benchmark bm = benchmarkRepository.findByCode(desc.benchmarkCode())
                            .orElseGet(() -> benchmarkRepository.save(new Benchmark(
                                desc.benchmarkCode(),
                                desc.benchmarkName(),
                                desc.provider(),
                                desc.returnVariant()
                            )));

                        return new BenchmarkResolutionResult(
                            bm,
                            false,
                            "CATEGORY_DEFAULT_MAPPING",
                            null,
                            String.format("Resolved candidate category benchmark %s (%s) for category %s under candidate methodology conventions",
                                desc.benchmarkCode(), desc.benchmarkName(), category.getDisplayName()),
                            desc.methodologyStatus(),
                            category,
                            desc
                        );
                    }
                } else {
                    // Scheme is outside the 5 reference categories and has no explicit mapping
                    return new BenchmarkResolutionResult(
                        null,
                        false,
                        "UNMAPPED",
                        null,
                        String.format("Scheme #%d ('%s') is outside the five reference equity categories and has no explicit benchmark mapping",
                            schemeId, scheme.getName()),
                        "UNMAPPED",
                        null,
                        null
                    );
                }
            }
        }

        // 3. Fallback resolution diagnostic for legacy unmapped schemes where scheme entity could not be retrieved
        Benchmark defaultBm = benchmarkRepository.findByCode(DEFAULT_BENCHMARK_CODE)
            .orElseGet(() -> benchmarkRepository.findAll().stream().findFirst().orElse(null));

        if (defaultBm != null) {
            return new BenchmarkResolutionResult(
                defaultBm,
                false,
                "DEFAULT_FALLBACK_UNMAPPED",
                null,
                String.format("No explicit benchmark mapping found for scheme #%d as of %s; defaulting to system benchmark %s",
                    schemeId, asOfDate, defaultBm.getCode()),
                "CANDIDATE_METHODOLOGY_MAPPING",
                null,
                null
            );
        }

        return new BenchmarkResolutionResult(
            null,
            false,
            "BENCHMARK_UNAVAILABLE",
            null,
            String.format("No explicit mapping or system default benchmark available for scheme #%d as of %s", schemeId, asOfDate),
            "UNMAPPED",
            null,
            null
        );
    }
}
