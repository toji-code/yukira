package com.yukira.backend.service;

import com.yukira.backend.ingestion.mcap.AmfiMarketCapIngestionService;
import com.yukira.backend.repository.SecurityMarketCapClassificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Service to seed authoritative historical AMFI Market-Cap Classification publications from packaged seeds.
 *
 * Epistemic Rules:
 * 1. Only authentic AMFI OpenXML publications are packaged and ingested.
 * 2. Idempotent: previously ingested ranking periods are skipped without duplication or schema mutation.
 * 3. Exact temporal parameters (rankingPeriodEndDate, sourcePublicationDate, effectiveFrom, effectiveTo, availabilityTime)
 *    are grounded in SEBI circulars and official AMFI publication schedules.
 */
@Service
public class PilotMarketCapDataService {

    private static final Logger log = LoggerFactory.getLogger(PilotMarketCapDataService.class);

    private final AmfiMarketCapIngestionService ingestionService;
    private final SecurityMarketCapClassificationRepository classificationRepository;

    public record SeedMarketCapSummary(
        int totalPeriodsTargeted,
        long totalRecordsIngested,
        long totalRecordsSkipped,
        long totalSecuritiesUnmapped,
        List<String> periodSummaries
    ) {}

    private record SeedConfig(
        String filename,
        String rankingPeriod,
        LocalDate rankingPeriodEndDate,
        LocalDate sourcePublicationDate,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        OffsetDateTime availabilityTime,
        String sourceUri
    ) {}

    public PilotMarketCapDataService(
        AmfiMarketCapIngestionService ingestionService,
        SecurityMarketCapClassificationRepository classificationRepository
    ) {
        this.ingestionService = ingestionService;
        this.classificationRepository = classificationRepository;
    }

    public SeedMarketCapSummary seedAuthoritativeMarketCapClassifications() {
        log.info("Starting ingestion of authoritative AMFI Market-Cap Classification seeds...");

        List<SeedConfig> seeds = List.of(
            new SeedConfig(
                "AverageMarketCapitalization31Dec2023.xlsx",
                "Jul-Dec 2023",
                LocalDate.of(2023, 12, 31),
                LocalDate.of(2024, 1, 5),
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 7, 31),
                OffsetDateTime.of(2024, 1, 5, 18, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)),
                "https://www.amfiindia.com/Themes/Theme1/downloads/AverageMarketCapitalizationoflistedcompaniesduringthesixmonthsended31Dec2023.xlsx"
            ),
            new SeedConfig(
                "AverageMarketCapitalization30Jun2024.xlsx",
                "Jan-Jun 2024",
                LocalDate.of(2024, 6, 30),
                LocalDate.of(2024, 7, 5),
                LocalDate.of(2024, 8, 1),
                LocalDate.of(2025, 1, 31),
                OffsetDateTime.of(2024, 7, 5, 18, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)),
                "https://www.amfiindia.com/Themes/Theme1/downloads/AverageMarketCapitalization30Jun2024.xlsx"
            ),
            new SeedConfig(
                "AverageMarketCapitalization30Jun2025.xlsx",
                "Jan-Jun 2025",
                LocalDate.of(2025, 6, 30),
                LocalDate.of(2025, 7, 4),
                LocalDate.of(2025, 8, 1),
                LocalDate.of(2026, 1, 31),
                OffsetDateTime.of(2025, 7, 4, 18, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)),
                "https://www.amfiindia.com/Themes/Theme1/downloads/AverageMarketCapitalization30Jun2025.xlsx"
            ),
            new SeedConfig(
                "AverageMarketCapitalization31Dec2025.xlsx",
                "Jul-Dec 2025",
                LocalDate.of(2025, 12, 31),
                LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 7, 31),
                OffsetDateTime.of(2026, 1, 5, 18, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)),
                "https://www.amfiindia.com/Themes/Theme1/downloads/AverageMarketCapitalization31Dec2025.xlsx"
            ),
            new SeedConfig(
                "AverageMarketCapitalization30Jun2026.xlsx",
                "Jan-Jun 2026",
                LocalDate.of(2026, 6, 30),
                LocalDate.of(2026, 7, 6),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2027, 1, 31),
                OffsetDateTime.of(2026, 7, 6, 18, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)),
                "https://portal.amfiindia.com/spages/AverageMarketCapitalization30Jun2026.xlsx"
            )
        );

        int targeted = seeds.size();
        long totalSaved = 0;
        long totalSkipped = 0;
        long totalUnmapped = 0;
        List<String> periodSummaries = new ArrayList<>();

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        for (SeedConfig seed : seeds) {
            try {
                Resource res = resolver.getResource("classpath:seeds/mcap/" + seed.filename());
                if (!res.exists()) {
                    log.warn("Seed resource {} not found on classpath, skipping.", seed.filename());
                    periodSummaries.add("SKIPPED: Resource " + seed.filename() + " not found");
                    continue;
                }

                byte[] bytes;
                try (InputStream is = res.getInputStream()) {
                    bytes = is.readAllBytes();
                }

                AmfiMarketCapIngestionService.IngestionResult result = ingestionService.ingestArtifact(
                    bytes,
                    seed.sourceUri(),
                    seed.rankingPeriod(),
                    seed.rankingPeriodEndDate(),
                    seed.sourcePublicationDate(),
                    seed.effectiveFrom(),
                    seed.effectiveTo(),
                    seed.availabilityTime(),
                    false
                );

                totalSaved += result.savedCount();
                totalSkipped += result.skippedDuplicates();
                totalUnmapped += result.unmappedSecurities();

                String summary = String.format("Period %s (End: %s, Eff: %s to %s): Parsed %d, Saved %d, Existing %d, Unmapped %d",
                    seed.rankingPeriod(), seed.rankingPeriodEndDate(), seed.effectiveFrom(), seed.effectiveTo(),
                    result.totalParsed(), result.savedCount(), result.skippedDuplicates(), result.unmappedSecurities());
                periodSummaries.add(summary);
                log.info(summary);

            } catch (Exception e) {
                String errorMsg = "ERROR ingesting " + seed.filename() + ": " + e.getMessage();
                log.error(errorMsg, e);
                periodSummaries.add(errorMsg);
            }
        }

        return new SeedMarketCapSummary(targeted, totalSaved, totalSkipped, totalUnmapped, periodSummaries);
    }
}
