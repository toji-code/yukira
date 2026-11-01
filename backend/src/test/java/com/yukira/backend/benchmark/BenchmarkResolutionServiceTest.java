package com.yukira.backend.benchmark;

import com.yukira.backend.domain.entity.Amc;
import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.domain.entity.Scheme;
import com.yukira.backend.domain.entity.SchemeBenchmarkHist;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.domain.entity.SchemePlan;
import com.yukira.backend.repository.BenchmarkRepository;
import com.yukira.backend.repository.SchemeBenchmarkHistRepository;
import com.yukira.backend.repository.SchemeRepository;
import com.yukira.backend.service.BenchmarkResolutionService;
import com.yukira.backend.service.BenchmarkResolutionService.BenchmarkResolutionResult;
import com.yukira.backend.universe.EquityUniverseCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TSK-M1-04: Canonical Benchmark Assignment and Precedence Verification")
class BenchmarkResolutionServiceTest {

    @Mock
    private SchemeBenchmarkHistRepository schemeBenchmarkHistRepository;

    @Mock
    private BenchmarkRepository benchmarkRepository;

    @Mock
    private SchemeRepository schemeRepository;

    private BenchmarkResolutionService resolutionService;

    private Amc dummyAmc;

    @BeforeEach
    void setUp() {
        resolutionService = new BenchmarkResolutionService(
            schemeBenchmarkHistRepository,
            benchmarkRepository,
            schemeRepository
        );
        dummyAmc = new Amc("Test Asset Management", "TEST_AMC");
    }

    @Test
    @DisplayName("Verify all five reference universe categories map to canonical TRI benchmarks")
    void testCanonicalCategoryBenchmarkMappingDescriptors() {
        // 1. Flexi Cap -> NIFTY 500 TRI
        var flexiDesc = CategoryBenchmarkMapping.getDescriptorForCategory(EquityUniverseCategory.FLEXI_CAP).orElseThrow();
        assertEquals("NIFTY_500_TRI", flexiDesc.benchmarkCode());
        assertEquals("TRI", flexiDesc.returnVariant());
        assertTrue(flexiDesc.isTri());
        assertEquals("NSE Indices Limited", flexiDesc.provider());
        assertEquals("CANDIDATE_METHODOLOGY_MAPPING", flexiDesc.methodologyStatus());

        // 2. Large Cap -> NIFTY 100 TRI
        var largeDesc = CategoryBenchmarkMapping.getDescriptorForCategory(EquityUniverseCategory.LARGE_CAP).orElseThrow();
        assertEquals("NIFTY_100_TRI", largeDesc.benchmarkCode());
        assertTrue(largeDesc.isTri());

        // 3. Large & Mid Cap -> NIFTY LargeMidcap 250 TRI
        var largeMidDesc = CategoryBenchmarkMapping.getDescriptorForCategory(EquityUniverseCategory.LARGE_AND_MID_CAP).orElseThrow();
        assertEquals("NIFTY_LARGEMIDCAP_250_TRI", largeMidDesc.benchmarkCode());
        assertTrue(largeMidDesc.isTri());

        // 4. Mid Cap -> NIFTY Midcap 150 TRI
        var midDesc = CategoryBenchmarkMapping.getDescriptorForCategory(EquityUniverseCategory.MID_CAP).orElseThrow();
        assertEquals("NIFTY_MIDCAP_150_TRI", midDesc.benchmarkCode());
        assertTrue(midDesc.isTri());

        // 5. Small Cap -> NIFTY Smallcap 250 TRI
        var smallDesc = CategoryBenchmarkMapping.getDescriptorForCategory(EquityUniverseCategory.SMALL_CAP).orElseThrow();
        assertEquals("NIFTY_SMALLCAP_250_TRI", smallDesc.benchmarkCode());
        assertTrue(smallDesc.isTri());

        // Verify total descriptors count is exactly 5
        assertEquals(5, CategoryBenchmarkMapping.getAllDescriptors().size());
    }

    @Test
    @DisplayName("Explicit fund-level mapping takes absolute precedence over category default")
    void testExplicitMappingPrecedence() {
        Long schemeId = 1001L;
        LocalDate asOfDate = LocalDate.of(2024, 1, 15);

        Benchmark customBenchmark = new Benchmark("CUSTOM_BENCHMARK_TRI", "Custom Index TRI", "Custom Provider", "TRI");
        Scheme dummyScheme = new Scheme(dummyAmc, "HDFC Flexi Cap Fund", "HDFC_FLEXI", LocalDate.of(1995, 1, 1), "Equity", "Flexi Cap Fund");
        dummyScheme.setId(schemeId);

        SchemeBenchmarkHist explicitHist = new SchemeBenchmarkHist(dummyScheme, customBenchmark, LocalDate.of(2020, 1, 1), null, true);

        when(schemeBenchmarkHistRepository.findPrimaryBenchmarkAsOfDate(eq(schemeId), eq(asOfDate)))
            .thenReturn(Optional.of(explicitHist));

        BenchmarkResolutionResult result = resolutionService.resolvePrimaryBenchmark(schemeId, asOfDate);

        assertNotNull(result);
        assertEquals("CUSTOM_BENCHMARK_TRI", result.benchmark().getCode());
        assertTrue(result.isExplicitMapping());
        assertEquals("EXPLICIT_HISTORICAL_MAPPING", result.resolutionMode());
        assertEquals("AUTHORIZED_EXPLICIT_MAPPING", result.methodologyStatus());
        verify(schemeRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("Candidate category default mapping resolves when no explicit mapping exists")
    void testCategoryDefaultMappingResolution() {
        Long schemeId = 2001L;
        LocalDate asOfDate = LocalDate.of(2024, 1, 15);

        Scheme midCapScheme = new Scheme(dummyAmc, "Axis Midcap Fund", "AXIS_MIDCAP", LocalDate.of(2011, 2, 1), "Equity Scheme", "Mid Cap Fund");
        midCapScheme.setId(schemeId);

        Benchmark midCapBm = new Benchmark("NIFTY_MIDCAP_150_TRI", "NIFTY Midcap 150 Total Returns Index", "NSE Indices Limited", "TRI");

        when(schemeBenchmarkHistRepository.findPrimaryBenchmarkAsOfDate(eq(schemeId), eq(asOfDate)))
            .thenReturn(Optional.empty());
        when(schemeRepository.findById(schemeId)).thenReturn(Optional.of(midCapScheme));
        when(benchmarkRepository.findByCode("NIFTY_MIDCAP_150_TRI")).thenReturn(Optional.of(midCapBm));

        BenchmarkResolutionResult result = resolutionService.resolvePrimaryBenchmark(schemeId, asOfDate);

        assertNotNull(result);
        assertEquals("NIFTY_MIDCAP_150_TRI", result.benchmark().getCode());
        assertFalse(result.isExplicitMapping());
        assertEquals("CATEGORY_DEFAULT_MAPPING", result.resolutionMode());
        assertEquals("CANDIDATE_METHODOLOGY_MAPPING", result.methodologyStatus());
        assertEquals(EquityUniverseCategory.MID_CAP, result.category());
        assertTrue(result.isTri());
        assertTrue(result.isMapped());
    }

    @Test
    @DisplayName("Schemes outside the 5 reference universe categories return explicit UNMAPPED status")
    void testUnmappedSchemeOutsideReferenceUniverse() {
        Long schemeId = 3001L;
        LocalDate asOfDate = LocalDate.of(2024, 1, 15);

        Scheme debtScheme = new Scheme(dummyAmc, "HDFC Liquid Fund", "HDFC_LIQUID", LocalDate.of(2000, 10, 1), "Debt Scheme", "Liquid Fund");
        debtScheme.setId(schemeId);

        when(schemeBenchmarkHistRepository.findPrimaryBenchmarkAsOfDate(eq(schemeId), eq(asOfDate)))
            .thenReturn(Optional.empty());
        when(schemeRepository.findById(schemeId)).thenReturn(Optional.of(debtScheme));

        BenchmarkResolutionResult result = resolutionService.resolvePrimaryBenchmark(schemeId, asOfDate);

        assertNotNull(result);
        assertNull(result.benchmark());
        assertFalse(result.isExplicitMapping());
        assertEquals("UNMAPPED", result.resolutionMode());
        assertEquals("UNMAPPED", result.methodologyStatus());
        assertNull(result.category());
        assertFalse(result.isMapped());
        assertFalse(result.isTri());
        assertTrue(result.message().contains("outside the five reference equity categories"));
    }

    @Test
    @DisplayName("Option-level resolution navigates through plan and scheme entity")
    void testResolveBenchmarkForOption() {
        Long optionId = 4001L;
        LocalDate asOfDate = LocalDate.of(2024, 1, 15);

        Scheme flexiScheme = new Scheme(dummyAmc, "Parag Parikh Flexi Cap Fund", "PPFAS_FLEXI", LocalDate.of(2013, 5, 24), "Equity Scheme", "Flexi Cap Fund");
        flexiScheme.setId(5001L);

        SchemePlan plan = new SchemePlan(flexiScheme, "Direct Plan", "DIRECT");
        SchemeOption option = new SchemeOption(plan, "122639", "INF879O01019", "GROWTH");
        option.setId(optionId);

        Benchmark nifty500 = new Benchmark("NIFTY_500_TRI", "NIFTY 500 Total Returns Index", "NSE Indices Limited", "TRI");

        when(schemeBenchmarkHistRepository.findPrimaryBenchmarkAsOfDate(eq(5001L), eq(asOfDate)))
            .thenReturn(Optional.empty());
        when(schemeRepository.findById(5001L)).thenReturn(Optional.of(flexiScheme));
        when(benchmarkRepository.findByCode("NIFTY_500_TRI")).thenReturn(Optional.of(nifty500));

        BenchmarkResolutionResult result = resolutionService.resolveBenchmarkForOption(option, asOfDate);

        assertNotNull(result);
        assertEquals("NIFTY_500_TRI", result.benchmark().getCode());
        assertEquals(EquityUniverseCategory.FLEXI_CAP, result.category());
        assertEquals("CATEGORY_DEFAULT_MAPPING", result.resolutionMode());
        assertTrue(result.isTri());
    }
}
