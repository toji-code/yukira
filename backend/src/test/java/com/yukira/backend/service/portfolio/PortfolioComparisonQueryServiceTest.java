package com.yukira.backend.service.portfolio;

import com.yukira.backend.domain.entity.AnalyticalScore;
import com.yukira.backend.domain.entity.CalculationRun;
import com.yukira.backend.domain.entity.MetricResult;
import com.yukira.backend.domain.entity.Scheme;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.domain.entity.SchemePlan;
import com.yukira.backend.dto.analysis.ComparisonRequest;
import com.yukira.backend.dto.analysis.ComparisonResponse;
import com.yukira.backend.repository.AnalyticalScoreRepository;
import com.yukira.backend.repository.CalculationRunRepository;
import com.yukira.backend.repository.MetricResultRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.repository.ScoreDimensionRepository;
import com.yukira.backend.service.PortfolioIntelligenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortfolioComparisonQueryServiceTest {

    @Mock
    private SchemeOptionRepository schemeOptionRepository;

    @Mock
    private AnalyticalScoreRepository analyticalScoreRepository;

    @Mock
    private ScoreDimensionRepository scoreDimensionRepository;

    @Mock
    private CalculationRunRepository calculationRunRepository;

    @Mock
    private MetricResultRepository metricResultRepository;

    @Mock
    private PortfolioIntelligenceService portfolioIntelligenceService;

    @InjectMocks
    private PortfolioComparisonQueryService comparisonQueryService;

    private SchemeOption hdfcOption;
    private SchemeOption ppfasOption;
    private SchemeOption ppfasElssOption;

    @BeforeEach
    void setUp() {
        Scheme hdfcScheme = new Scheme();
        hdfcScheme.setName("HDFC Flexi Cap Fund");
        hdfcScheme.setCode("HDFC_FLEXI");
        SchemePlan hdfcPlan = new SchemePlan();
        hdfcPlan.setScheme(hdfcScheme);
        hdfcPlan.setPlanType("DIRECT");
        hdfcOption = new SchemeOption();
        hdfcOption.setId(1L);
        hdfcOption.setPlan(hdfcPlan);
        hdfcOption.setAmfiCode("118955");
        hdfcOption.setIsin("INF179K01UT0");
        hdfcOption.setOptionType("GROWTH");

        Scheme ppfasScheme = new Scheme();
        ppfasScheme.setName("Parag Parikh Flexi Cap Fund");
        ppfasScheme.setCode("PPFAS_FLEXI");
        SchemePlan ppfasPlan = new SchemePlan();
        ppfasPlan.setScheme(ppfasScheme);
        ppfasPlan.setPlanType("DIRECT");
        ppfasOption = new SchemeOption();
        ppfasOption.setId(10262L);
        ppfasOption.setPlan(ppfasPlan);
        ppfasOption.setAmfiCode("122639");
        ppfasOption.setIsin("INF879O01027");
        ppfasOption.setOptionType("GROWTH");

        Scheme ppfasElssScheme = new Scheme();
        ppfasElssScheme.setName("Parag Parikh ELSS Tax Saver Fund");
        ppfasElssScheme.setCode("PPFAS_ELSS");
        SchemePlan ppfasElssPlan = new SchemePlan();
        ppfasElssPlan.setScheme(ppfasElssScheme);
        ppfasElssPlan.setPlanType("DIRECT");
        ppfasElssOption = new SchemeOption();
        ppfasElssOption.setId(10151L);
        ppfasElssOption.setPlan(ppfasElssPlan);
        ppfasElssOption.setAmfiCode("147481");
        ppfasElssOption.setIsin("INF879O01100");
        ppfasElssOption.setOptionType("GROWTH");
    }

    @Test
    @DisplayName("Should batch load 3 pilot schemes and evaluate metric comparability status")
    void testExecuteComparison_PilotSchemes() {
        List<Long> ids = List.of(1L, 10262L, 10151L);
        when(schemeOptionRepository.findComparisonIdentitiesByIdIn(ids))
            .thenReturn(List.of(hdfcOption, ppfasOption, ppfasElssOption));

        CalculationRun runHdfc = new CalculationRun();
        runHdfc.setId(101L);
        runHdfc.setSchemeOption(hdfcOption);

        CalculationRun runPpfas = new CalculationRun();
        runPpfas.setId(102L);
        runPpfas.setSchemeOption(ppfasOption);

        CalculationRun runElss = new CalculationRun();
        runElss.setId(103L);
        runElss.setSchemeOption(ppfasElssOption);

        when(calculationRunRepository.findComparisonRunsBySchemeOptionIdsAndAsOfDate(eq(ids), any(LocalDate.class)))
            .thenReturn(List.of(runHdfc, runPpfas, runElss));

        MetricResult r1 = new MetricResult();
        r1.setCalculationRun(runHdfc);
        r1.setMetricCode("RET-03");
        r1.setNumericValue(new BigDecimal("0.1850"));
        r1.setStringValue("+18.5000%");
        r1.setUnits("PERCENTAGE");
        r1.setCalculationStatus("CALCULATED");

        MetricResult r2 = new MetricResult();
        r2.setCalculationRun(runPpfas);
        r2.setMetricCode("RET-03");
        r2.setNumericValue(new BigDecimal("0.2110"));
        r2.setStringValue("+21.1000%");
        r2.setUnits("PERCENTAGE");
        r2.setCalculationStatus("CALCULATED");

        MetricResult r3 = new MetricResult();
        r3.setCalculationRun(runElss);
        r3.setMetricCode("RET-03");
        r3.setNumericValue(new BigDecimal("0.2240"));
        r3.setStringValue("+22.4000%");
        r3.setUnits("PERCENTAGE");
        r3.setCalculationStatus("CALCULATED");

        when(metricResultRepository.findCanonicalByCalculationRunIdInAndMetricCodeIn(anyList(), anyList()))
            .thenReturn(List.of(r1, r2, r3));

        ComparisonRequest request = new ComparisonRequest(
            ids,
            LocalDate.of(2024, 1, 15),
            OffsetDateTime.parse("2024-01-31T23:59:59+05:30"),
            List.of("RET-03")
        );

        ComparisonResponse response = comparisonQueryService.executeComparison(request);

        assertThat(response.funds()).hasSize(3);
        assertThat(response.metrics()).hasSize(1);

        ComparisonResponse.ComparisonMetric ret03 = response.metrics().get(0);
        assertThat(ret03.metricCode()).isEqualTo("RET-03");
        assertThat(ret03.overallComparabilityStatus()).isEqualTo("COMPARABLE_WITH_LIMITATIONS");
        assertThat(ret03.overallComparabilityReason()).contains("differing fund historical lengths");

        ComparisonResponse.ComparisonMetricResult elssResult = ret03.results().stream()
            .filter(res -> res.schemeOptionId() == 10151L)
            .findFirst()
            .orElseThrow();

        assertThat(elssResult.comparabilityStatus()).isEqualTo("COMPARABLE_WITH_LIMITATIONS");
        assertThat(elssResult.limitationReason()).contains("740 daily NAV observations");
    }

    @Test
    @DisplayName("Should handle missing metrics with INSUFFICIENT_DATA status")
    void testExecuteComparison_MissingMetrics() {
        List<Long> ids = List.of(1L, 10262L);
        when(schemeOptionRepository.findComparisonIdentitiesByIdIn(ids))
            .thenReturn(List.of(hdfcOption, ppfasOption));

        when(calculationRunRepository.findComparisonRunsBySchemeOptionIdsAndAsOfDate(eq(ids), any(LocalDate.class)))
            .thenReturn(Collections.emptyList());

        ComparisonRequest request = new ComparisonRequest(
            ids,
            LocalDate.of(2024, 1, 15),
            OffsetDateTime.parse("2024-01-31T23:59:59+05:30"),
            List.of("RSK-01")
        );

        ComparisonResponse response = comparisonQueryService.executeComparison(request);

        assertThat(response.metrics()).hasSize(1);
        ComparisonResponse.ComparisonMetric rsk01 = response.metrics().get(0);
        assertThat(rsk01.overallComparabilityStatus()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(rsk01.results()).allMatch(res -> "INSUFFICIENT_DATA".equals(res.calculationStatus()));
    }

    @Test
    @DisplayName("Should reject invalid scheme option ID")
    void testExecuteComparison_InvalidSchemeOption() {
        when(schemeOptionRepository.findComparisonIdentitiesByIdIn(List.of(999L, 1000L)))
            .thenReturn(Collections.emptyList());

        ComparisonRequest request = new ComparisonRequest(
            List.of(999L, 1000L),
            LocalDate.of(2024, 1, 15),
            OffsetDateTime.parse("2024-01-31T23:59:59+05:30"),
            List.of("RET-02")
        );

        assertThatThrownBy(() -> comparisonQueryService.executeComparison(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("SchemeOption #999 not found");
    }
}
