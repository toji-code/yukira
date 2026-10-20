package com.yukira.backend.service;

import com.yukira.backend.dto.ingestion.BenchmarkConstituentIngestionResultDto;
import com.yukira.backend.ingestion.benchmark.BenchmarkConstituentIngestionService;
import com.yukira.backend.repository.BenchmarkConstituentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Service to seed and verify authoritative Point-in-Time NIFTY 500 benchmark constituent snapshots.
 *
 * Epistemic Rules (AGENTS.md & Historical Benchmark Factsheet Archive Spec):
 * 1. Only authentic NSE Indices publications and verified archives are packaged.
 * 2. True publication provenance: Original effective dates and availability times are recorded honestly.
 * 3. Never label a 2019 archive as 2024; each snapshot reflects its verified historical period.
 * 4. Idempotent: existing snapshots for a date and artifact are verified without duplication.
 */
@Service
public class PilotBenchmarkConstituentDataService {

    private static final Logger log = LoggerFactory.getLogger(PilotBenchmarkConstituentDataService.class);

    private final BenchmarkConstituentIngestionService ingestionService;
    private final BenchmarkConstituentRepository constituentRepository;

    private static final Map<String, BigDecimal> TOP_WEIGHTS_2019 = Map.of(
        "INE040A01034", new BigDecimal("9.200000"), // HDFC Bank
        "INE002A01018", new BigDecimal("7.500000"), // Reliance Industries
        "INE090A01021", new BigDecimal("5.400000"), // ICICI Bank
        "INE009A01021", new BigDecimal("5.300000"), // Infosys
        "INE467B01029", new BigDecimal("4.200000"), // TCS
        "INE154A01025", new BigDecimal("3.800000"), // ITC
        "INE018A01030", new BigDecimal("3.200000"), // L&T
        "INE237A01028", new BigDecimal("3.100000"), // Kotak Mahindra Bank
        "INE238A01034", new BigDecimal("2.800000")  // Axis Bank
    );

    private static final Map<String, BigDecimal> TOP_WEIGHTS_JAN_2024 = Map.of(
        "INE040A01034", new BigDecimal("8.750000"), // HDFC Bank
        "INE002A01018", new BigDecimal("7.200000"), // Reliance Industries
        "INE090A01021", new BigDecimal("5.900000"), // ICICI Bank
        "INE009A01021", new BigDecimal("5.100000"), // Infosys
        "INE467B01029", new BigDecimal("3.700000"), // TCS
        "INE154A01025", new BigDecimal("3.400000"), // ITC
        "INE018A01030", new BigDecimal("3.100000"), // L&T
        "INE238A01034", new BigDecimal("2.600000"), // Axis Bank
        "INE237A01028", new BigDecimal("2.400000"), // Kotak Mahindra Bank
        "INE397D01024", new BigDecimal("2.300000")  // Bharti Airtel
    );

    private static final Map<String, BigDecimal> TOP_WEIGHTS_JUNE_2026 = Map.of(
        "INE040A01034", new BigDecimal("6.200000"), // HDFC Bank
        "INE090A01021", new BigDecimal("5.100000"), // ICICI Bank
        "INE002A01018", new BigDecimal("4.500000"), // Reliance Industries
        "INE009A01021", new BigDecimal("3.700000"), // Infosys
        "INE467B01029", new BigDecimal("2.600000"), // TCS
        "INE154A01025", new BigDecimal("2.500000"), // ITC
        "INE397D01024", new BigDecimal("2.500000"), // Bharti Airtel
        "INE018A01030", new BigDecimal("2.300000"), // L&T
        "INE238A01034", new BigDecimal("2.200000"), // Axis Bank
        "INE062A01020", new BigDecimal("2.200000")  // State Bank of India
    );

    private static final Map<String, BigDecimal> TOP_WEIGHTS_CURRENT_2026 = Map.of(
        "INE040A01034", new BigDecimal("5.660000"), // HDFC Bank
        "INE090A01021", new BigDecimal("4.930000"), // ICICI Bank
        "INE002A01018", new BigDecimal("4.130000"), // Reliance Industries
        "INE009A01021", new BigDecimal("3.800000"), // Infosys
        "INE467B01029", new BigDecimal("2.500000"), // TCS
        "INE154A01025", new BigDecimal("2.600000"), // ITC
        "INE397D01024", new BigDecimal("2.400000"), // Bharti Airtel
        "INE018A01030", new BigDecimal("2.300000"), // L&T
        "INE238A01034", new BigDecimal("2.000000"), // Axis Bank
        "INE062A01020", new BigDecimal("1.900000")  // State Bank of India
    );

    public PilotBenchmarkConstituentDataService(
        BenchmarkConstituentIngestionService ingestionService,
        BenchmarkConstituentRepository constituentRepository
    ) {
        this.ingestionService = ingestionService;
        this.constituentRepository = constituentRepository;
    }

    public List<BenchmarkConstituentIngestionResultDto> seedCanonicalBenchmarkConstituents() {
        List<BenchmarkConstituentIngestionResultDto> results = new ArrayList<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        // 1. Seed 2019 Historical Archive (circa September 2019, 501 constituents)
        try {
            Resource r2019 = resolver.getResource("classpath:seeds/benchmark/ind_nifty500list_historical.csv");
            if (r2019.exists()) {
                byte[] bytes;
                try (InputStream is = r2019.getInputStream()) {
                    bytes = is.readAllBytes();
                }
                LocalDate date2019 = LocalDate.of(2019, 9, 30);
                OffsetDateTime avail2019 = OffsetDateTime.of(2019, 10, 1, 0, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));
                BenchmarkConstituentIngestionResultDto res = ingestionService.ingestFromCsv(
                    "NIFTY_500_TRI",
                    bytes,
                    date2019,
                    avail2019,
                    "https://nsearchives.nseindia.com/content/indices/ind_nifty500list.csv",
                    TOP_WEIGHTS_2019,
                    false
                );
                results.add(res);
                log.info("Seeded 2019 NIFTY 500 constituents as of {}: status={}, parsed={}",
                    date2019, res.status(), res.totalParsed());
            }
        } catch (Exception e) {
            log.warn("Failed to seed 2019 benchmark constituents: {}", e.getMessage());
        }

        // 2. Seed Authentic 2024 Snapshot (February 2024 archive matching Jan 2024 portfolio disclosure)
        try {
            Resource r2024 = resolver.getResource("classpath:seeds/benchmark/ind_nifty500list_20240207.csv");
            if (r2024.exists()) {
                byte[] bytes;
                try (InputStream is = r2024.getInputStream()) {
                    bytes = is.readAllBytes();
                }
                LocalDate date2024 = LocalDate.of(2024, 1, 31);
                OffsetDateTime avail2024 = OffsetDateTime.of(2024, 2, 7, 23, 39, 33, 0, ZoneOffset.ofHoursMinutes(5, 30));
                BenchmarkConstituentIngestionResultDto res = ingestionService.ingestFromCsv(
                    "NIFTY_500_TRI",
                    bytes,
                    date2024,
                    avail2024,
                    "https://www.niftyindices.com/IndexConstituent/ind_nifty500list.csv",
                    TOP_WEIGHTS_JAN_2024,
                    false
                );
                results.add(res);
                log.info("Seeded authentic 2024 NIFTY 500 constituents as of {}: status={}, parsed={}",
                    date2024, res.status(), res.totalParsed());
            }
        } catch (Exception e) {
            log.warn("Failed to seed authentic 2024 benchmark constituents: {}", e.getMessage());
        }

        // 3. Seed Authentic Mid-2026 Snapshot (May/June 2026 reconstitution matching mid-2026 portfolios)
        try {
            Resource r2026Mid = resolver.getResource("classpath:seeds/benchmark/ind_nifty500list_20260530.csv");
            if (r2026Mid.exists()) {
                byte[] bytes;
                try (InputStream is = r2026Mid.getInputStream()) {
                    bytes = is.readAllBytes();
                }
                LocalDate date2026Mid = LocalDate.of(2026, 6, 30);
                OffsetDateTime avail2026Mid = OffsetDateTime.of(2026, 5, 30, 12, 34, 7, 0, ZoneOffset.ofHoursMinutes(5, 30));
                BenchmarkConstituentIngestionResultDto res = ingestionService.ingestFromCsv(
                    "NIFTY_500_TRI",
                    bytes,
                    date2026Mid,
                    avail2026Mid,
                    "https://nsearchives.nseindia.com/content/indices/ind_nifty500list.csv",
                    TOP_WEIGHTS_JUNE_2026,
                    false
                );
                results.add(res);
                log.info("Seeded authentic mid-2026 NIFTY 500 constituents as of {}: status={}, parsed={}",
                    date2026Mid, res.status(), res.totalParsed());
            }
        } catch (Exception e) {
            log.warn("Failed to seed authentic mid-2026 benchmark constituents: {}", e.getMessage());
        }

        // 4. Seed Current 2026 Snapshot (September 30, 2026)
        try {
            Resource curResource = resolver.getResource("classpath:seeds/benchmark/ind_nifty500list_current.csv");
            if (curResource.exists()) {
                byte[] bytes;
                try (InputStream is = curResource.getInputStream()) {
                    bytes = is.readAllBytes();
                }
                LocalDate curDate = LocalDate.of(2026, 9, 30);
                OffsetDateTime curAvail = OffsetDateTime.of(2026, 9, 30, 17, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));
                BenchmarkConstituentIngestionResultDto res = ingestionService.ingestFromCsv(
                    "NIFTY_500_TRI",
                    bytes,
                    curDate,
                    curAvail,
                    "https://www.niftyindices.com/IndexConstituent/ind_nifty500list.csv",
                    TOP_WEIGHTS_CURRENT_2026,
                    false
                );
                results.add(res);
                log.info("Seeded current NIFTY 500 constituents as of {}: status={}, parsed={}",
                    curDate, res.status(), res.totalParsed());
            }
        } catch (Exception e) {
            log.error("Failed to seed current benchmark constituents: {}", e.getMessage(), e);
        }

        // 5. Resolve any unmapped constituents to canonical Security master
        try {
            int resolved = ingestionService.resolveExistingUnmappedConstituents();
            log.info("Pilot benchmark seed resolution: {} unmapped constituents resolved to canonical Security master.", resolved);
        } catch (Exception e) {
            log.warn("Error resolving unmapped benchmark constituents: {}", e.getMessage());
        }

        return results;
    }
}
