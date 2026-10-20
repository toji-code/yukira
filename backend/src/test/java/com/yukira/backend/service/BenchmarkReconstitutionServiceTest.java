package com.yukira.backend.service;

import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.domain.entity.BenchmarkConstituent;
import com.yukira.backend.domain.entity.Security;
import com.yukira.backend.dto.portfolio.BenchmarkReconstitutionResponseDto;
import com.yukira.backend.repository.BenchmarkConstituentRepository;
import com.yukira.backend.repository.BenchmarkRepository;
import com.yukira.backend.repository.SecurityIdentifierRepository;
import com.yukira.backend.repository.SecurityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BenchmarkReconstitutionServiceTest {

    @Mock
    private BenchmarkRepository benchmarkRepository;

    @Mock
    private BenchmarkConstituentRepository constituentRepository;

    @Mock
    private SecurityRepository securityRepository;

    @Mock
    private SecurityIdentifierRepository securityIdentifierRepository;

    private BenchmarkReconstitutionService reconstitutionService;
    private Benchmark benchmark;

    @BeforeEach
    void setUp() {
        reconstitutionService = new BenchmarkReconstitutionService(
            benchmarkRepository,
            constituentRepository,
            securityRepository,
            securityIdentifierRepository
        );

        benchmark = new Benchmark("NIFTY_500_TRI", "NIFTY 500 Total Returns Index", "NSE_INDICES", "TRI");
        benchmark.setId(100L);

        when(benchmarkRepository.findByCode("NIFTY_500_TRI")).thenReturn(Optional.of(benchmark));
    }

    @Test
    @DisplayName("Calculates entries, exits, and retentions between consecutive benchmark observations")
    void shouldCalculateReconstitutionEntriesAndExits() {
        LocalDate fromDate = LocalDate.of(2019, 3, 29);
        LocalDate toDate = LocalDate.of(2026, 9, 30);
        OffsetDateTime cutoff = OffsetDateTime.of(2026, 10, 1, 0, 0, 0, 0, ZoneOffset.UTC);

        // Security 1: ICICI Bank (Retained, same ISIN)
        Security secIcici = new Security();
        secIcici.setId(1L);
        secIcici.setCanonicalName("ICICI Bank Ltd.");

        BenchmarkConstituent fromIcici = new BenchmarkConstituent();
        fromIcici.setBenchmark(benchmark);
        fromIcici.setIsin("INE090A01021");
        fromIcici.setSymbol("ICICIBANK");
        fromIcici.setCompanyName("ICICI Bank Ltd.");
        fromIcici.setSecurity(secIcici);
        fromIcici.setWeight(new BigDecimal("5.900000"));
        fromIcici.setEffectiveDate(fromDate);

        BenchmarkConstituent toIcici = new BenchmarkConstituent();
        toIcici.setBenchmark(benchmark);
        toIcici.setIsin("INE090A01021");
        toIcici.setSymbol("ICICIBANK");
        toIcici.setCompanyName("ICICI Bank Ltd.");
        toIcici.setSecurity(secIcici);
        toIcici.setWeight(new BigDecimal("4.930000"));
        toIcici.setEffectiveDate(toDate);

        // Security 2: Tata Steel (Retained across stock split: Old ISIN INE081A01012 -> New ISIN INE081A01020)
        Security secTataSteel = new Security();
        secTataSteel.setId(2L);
        secTataSteel.setCanonicalName("Tata Steel Ltd.");

        BenchmarkConstituent fromTataSteel = new BenchmarkConstituent();
        fromTataSteel.setBenchmark(benchmark);
        fromTataSteel.setIsin("INE081A01012");
        fromTataSteel.setSymbol("TATASTEEL");
        fromTataSteel.setCompanyName("Tata Steel Ltd.");
        fromTataSteel.setSecurity(secTataSteel);
        fromTataSteel.setEffectiveDate(fromDate);

        BenchmarkConstituent toTataSteel = new BenchmarkConstituent();
        toTataSteel.setBenchmark(benchmark);
        toTataSteel.setIsin("INE081A01020");
        toTataSteel.setSymbol("TATASTEEL");
        toTataSteel.setCompanyName("Tata Steel Ltd.");
        toTataSteel.setSecurity(secTataSteel);
        toTataSteel.setEffectiveDate(toDate);

        // Security 3: Jet Airways (Exited)
        Security secJet = new Security();
        secJet.setId(3L);
        secJet.setCanonicalName("Jet Airways (India) Ltd.");

        BenchmarkConstituent fromJet = new BenchmarkConstituent();
        fromJet.setBenchmark(benchmark);
        fromJet.setIsin("INE802G01018");
        fromJet.setSymbol("JETAIRWAYS");
        fromJet.setCompanyName("Jet Airways (India) Ltd.");
        fromJet.setSecurity(secJet);
        fromJet.setEffectiveDate(fromDate);

        // Security 4: Zomato (Entered)
        Security secZomato = new Security();
        secZomato.setId(4L);
        secZomato.setCanonicalName("Zomato Ltd.");

        BenchmarkConstituent toZomato = new BenchmarkConstituent();
        toZomato.setBenchmark(benchmark);
        toZomato.setIsin("INE758T01015");
        toZomato.setSymbol("ZOMATO");
        toZomato.setCompanyName("Zomato Ltd.");
        toZomato.setSecurity(secZomato);
        toZomato.setEffectiveDate(toDate);

        when(constituentRepository.findAvailableCompositionDates(eq(100L), eq(cutoff)))
            .thenReturn(List.of(toDate, fromDate));
        when(constituentRepository.findByBenchmarkAndDateAndCutoff(eq(100L), eq(fromDate), eq(cutoff)))
            .thenReturn(List.of(fromIcici, fromTataSteel, fromJet));
        when(constituentRepository.findByBenchmarkAndDateAndCutoff(eq(100L), eq(toDate), eq(cutoff)))
            .thenReturn(List.of(toIcici, toTataSteel, toZomato));

        BenchmarkReconstitutionResponseDto response = reconstitutionService.calculateReconstitution(
            "NIFTY_500_TRI", fromDate, toDate, cutoff
        );

        assertThat(response).isNotNull();
        assertThat(response.benchmarkCode()).isEqualTo("NIFTY_500_TRI");
        assertThat(response.fromDate()).isEqualTo(fromDate);
        assertThat(response.toDate()).isEqualTo(toDate);
        assertThat(response.daysBetween()).isGreaterThan(2000L);
        assertThat(response.observationType()).isEqualTo("TWO_POINT_OBSERVED_TRANSITION");

        // Retained: ICICI and Tata Steel (stock split ISIN migration recognized as retained!)
        assertThat(response.retainedCount()).isEqualTo(2);
        assertThat(response.retainedConstituents()).extracting("companyName")
            .containsExactlyInAnyOrder("ICICI Bank Ltd.", "Tata Steel Ltd.");

        var tataSteelMember = response.retainedConstituents().stream()
            .filter(m -> m.symbol().equals("TATASTEEL")).findFirst().orElseThrow();
        assertThat(tataSteelMember.notes()).contains("ISIN migrated from INE081A01012 to INE081A01020");

        // Entered: Zomato
        assertThat(response.enteredCount()).isEqualTo(1);
        assertThat(response.enteredConstituents()).extracting("symbol").containsExactly("ZOMATO");

        // Exited: Jet Airways
        assertThat(response.exitedCount()).isEqualTo(1);
        assertThat(response.exitedConstituents()).extracting("symbol").containsExactly("JETAIRWAYS");

        // Turnover: 1 entered / 3 totalTo = 33.33%
        assertThat(response.turnoverPct()).isEqualByComparingTo("33.33");
        assertThat(response.comparisonCoverageStatus()).isEqualTo("VERIFIED_PARTIAL");
    }

    @Test
    @DisplayName("Returns empty response gracefully when no benchmark snapshots exist under cutoff")
    void shouldReturnEmptyWhenNoSnapshotsFound() {
        OffsetDateTime cutoff = OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        when(constituentRepository.findAvailableCompositionDates(eq(100L), eq(cutoff)))
            .thenReturn(Collections.emptyList());

        BenchmarkReconstitutionResponseDto response = reconstitutionService.calculateReconstitution(
            "NIFTY_500_TRI", null, null, cutoff
        );

        assertThat(response).isNotNull();
        assertThat(response.observationType()).isEqualTo("UNAVAILABLE");
        assertThat(response.comparisonCoverageStatus()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(response.retainedCount()).isEqualTo(0);
        assertThat(response.enteredCount()).isEqualTo(0);
        assertThat(response.exitedCount()).isEqualTo(0);
    }
}
