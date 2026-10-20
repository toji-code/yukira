package com.yukira.backend.ingestion.benchmark;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.ingestion.BenchmarkConstituentIngestionResultDto;
import com.yukira.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BenchmarkConstituentIngestionServiceTest {

    @Mock
    private BenchmarkRepository benchmarkRepository;

    @Mock
    private BenchmarkConstituentRepository constituentRepository;

    @Mock
    private SecurityIdentifierRepository securityIdentifierRepository;

    @Mock
    private SecurityRepository securityRepository;

    @Mock
    private SourceArtifactRepository sourceArtifactRepository;

    @Mock
    private DataSourceRepository dataSourceRepository;

    @InjectMocks
    private BenchmarkConstituentIngestionService ingestionService;

    private Benchmark testBenchmark;
    private DataSource testDataSource;
    private SourceArtifact testArtifact;
    private Security testSecurity;
    private SecurityIdentifier testIdentifier;

    private static final String SAMPLE_CSV =
        "Company Name,Industry,Symbol,Series,ISIN Code\n" +
        "HDFC Bank Ltd.,Financial Services,HDFCBANK,EQ,INE040A01034\n" +
        "Reliance Industries Ltd.,Oil Gas & Consumable Fuels,RELIANCE,EQ,INE002A01018\n" +
        "Unmapped Tech Ltd.,Information Technology,UNMAPPED,EQ,INE999A01099\n";

    @BeforeEach
    void setUp() {
        testBenchmark = new Benchmark("NIFTY_500_TRI", "NIFTY 500 Total Returns Index", "NSE_INDICES", "TRI");
        testBenchmark.setId(100L);

        testDataSource = new DataSource("NSE_INDICES", "NSE Indices Limited", "https://www.niftyindices.com");
        testDataSource.setId(10L);

        testArtifact = new SourceArtifact(testDataSource, OffsetDateTime.now(ZoneOffset.UTC), "BENCHMARK_CONSTITUENT_CSV", "testhash", 500L);
        testArtifact.setId(200L);

        testSecurity = new Security();
        testSecurity.setId(50L);
        testSecurity.setCanonicalName("HDFC Bank Ltd.");

        testIdentifier = new SecurityIdentifier(testSecurity, "ISIN", "INE040A01034", LocalDate.of(2020, 1, 1));

        when(benchmarkRepository.findByCode("NIFTY_500_TRI")).thenReturn(Optional.of(testBenchmark));
        when(dataSourceRepository.findByCode("NSE_INDICES")).thenReturn(Optional.of(testDataSource));
        when(sourceArtifactRepository.findBySha256Hash(anyString())).thenReturn(Optional.empty());
        when(sourceArtifactRepository.save(any(SourceArtifact.class))).thenReturn(testArtifact);
        when(constituentRepository.countByBenchmarkIdAndEffectiveDate(eq(100L), any(LocalDate.class))).thenReturn(0L);

        // Security identifier mapping: HDFC Bank mapped, Reliance and Unmapped not mapped
        when(securityIdentifierRepository.findTopByIdTypeAndIdValueOrderByIdAsc("ISIN", "INE040A01034"))
            .thenReturn(Optional.of(testIdentifier));
        when(securityIdentifierRepository.findTopByIdTypeAndIdValueOrderByIdAsc("ISIN", "INE002A01018"))
            .thenReturn(Optional.empty());
        when(securityIdentifierRepository.findTopByIdTypeAndIdValueOrderByIdAsc("ISIN", "INE999A01099"))
            .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Dry run validates records and reports mapped/unmapped counts without persisting")
    void shouldExecuteDryRunSuccessfully() {
        byte[] csvBytes = SAMPLE_CSV.getBytes(StandardCharsets.UTF_8);
        LocalDate effectiveDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime avail = OffsetDateTime.of(2024, 1, 15, 17, 30, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        BenchmarkConstituentIngestionResultDto result = ingestionService.ingestFromCsv(
            "NIFTY_500_TRI",
            csvBytes,
            effectiveDate,
            avail,
            "https://niftyindices.com/ind_nifty500list.csv",
            null,
            true
        );

        assertThat(result.status()).isEqualTo("DRY_RUN_SUCCESS");
        assertThat(result.totalParsed()).isEqualTo(3);
        assertThat(result.insertedCount()).isEqualTo(0);
        assertThat(result.mappedCount()).isEqualTo(1);
        assertThat(result.unmappedCount()).isEqualTo(2);

        verify(constituentRepository, never()).saveAll(any());
        verify(sourceArtifactRepository, never()).save(any());
    }

    @Test
    @DisplayName("Idempotency: returns ALREADY_INGESTED if constituents exist for benchmark and date")
    void shouldBeIdempotentWhenAlreadyIngested() {
        byte[] csvBytes = SAMPLE_CSV.getBytes(StandardCharsets.UTF_8);
        LocalDate effectiveDate = LocalDate.of(2024, 1, 15);
        when(constituentRepository.countByBenchmarkIdAndEffectiveDate(100L, effectiveDate)).thenReturn(501L);

        BenchmarkConstituentIngestionResultDto result = ingestionService.ingestFromCsv(
            "NIFTY_500_TRI",
            csvBytes,
            effectiveDate,
            OffsetDateTime.now(),
            "https://niftyindices.com/ind_nifty500list.csv",
            null,
            false
        );

        assertThat(result.status()).isEqualTo("ALREADY_INGESTED");
        assertThat(result.totalParsed()).isEqualTo(501);
        assertThat(result.insertedCount()).isEqualTo(0);
        verify(constituentRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Persists constituents with exact SHA-256 and preserves UNMAPPED records without dropping")
    @SuppressWarnings("unchecked")
    void shouldPersistConstituentsAndPreserveUnmapped() {
        byte[] csvBytes = SAMPLE_CSV.getBytes(StandardCharsets.UTF_8);
        LocalDate effectiveDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime avail = OffsetDateTime.of(2024, 1, 15, 17, 30, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        Map<String, BigDecimal> topWeights = Map.of(
            "INE040A01034", new BigDecimal("8.750000")
        );

        when(constituentRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BenchmarkConstituentIngestionResultDto result = ingestionService.ingestFromCsv(
            "NIFTY_500_TRI",
            csvBytes,
            effectiveDate,
            avail,
            "https://niftyindices.com/ind_nifty500list.csv",
            topWeights,
            false
        );

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.totalParsed()).isEqualTo(3);
        assertThat(result.insertedCount()).isEqualTo(3);
        assertThat(result.mappedCount()).isEqualTo(1);
        assertThat(result.unmappedCount()).isEqualTo(2);

        ArgumentCaptor<List<BenchmarkConstituent>> captor = ArgumentCaptor.forClass(List.class);
        verify(constituentRepository).saveAll(captor.capture());
        List<BenchmarkConstituent> saved = captor.getValue();
        assertThat(saved).hasSize(3);

        // HDFC Bank: Mapped, has weight, VALIDATED
        BenchmarkConstituent hdfc = saved.stream().filter(c -> c.getIsin().equals("INE040A01034")).findFirst().orElseThrow();
        assertThat(hdfc.getMappingStatus()).isEqualTo("MAPPED");
        assertThat(hdfc.getSecurity()).isNotNull();
        assertThat(hdfc.getWeight()).isEqualByComparingTo("8.750000");
        assertThat(hdfc.getValidationStatus()).isEqualTo("VALIDATED");

        // Reliance: Unmapped, no published weight, PARTIAL_DISCLOSURE
        BenchmarkConstituent reliance = saved.stream().filter(c -> c.getIsin().equals("INE002A01018")).findFirst().orElseThrow();
        assertThat(reliance.getMappingStatus()).isEqualTo("UNMAPPED");
        assertThat(reliance.getSecurity()).isNull();
        assertThat(reliance.getWeight()).isNull();
        assertThat(reliance.getValidationStatus()).isEqualTo("PARTIAL_DISCLOSURE");
        assertThat(reliance.getCompanyName()).isEqualTo("Reliance Industries Ltd.");
        assertThat(reliance.getIndustry()).isEqualTo("Oil Gas & Consumable Fuels");
    }

    @Test
    @DisplayName("Rejects null payload or invalid benchmark code")
    void shouldRejectInvalidInput() {
        assertThatThrownBy(() -> ingestionService.ingestFromCsv(
            "NIFTY_500_TRI", null, LocalDate.now(), OffsetDateTime.now(), "url", null, false))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> ingestionService.ingestFromCsv(
            "NON_EXISTENT_BM", "test".getBytes(), LocalDate.now(), OffsetDateTime.now(), "url", null, false))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
