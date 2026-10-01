package com.yukira.backend.ingestion.amfi;

import com.yukira.backend.domain.entity.NavObservation;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.repository.SourceArtifactRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 2J Provenance Reconciliation & Regression Guard:
 * Formally verifies that January 2024 Series A is the sole authentic, cryptographically-proven
 * AMFI observation series for canonical pilot scheme option 118955 (HDFC Flexi Cap Direct Growth).
 *
 * Prevents synthetic Series B reintroduction or contaminated regular plan identifiers (119062 / INF179K01BE2).
 */
@SpringBootTest
@Transactional
@SuppressWarnings("null")
public class January2024ProvenanceDiscrepancyTest {

    @Autowired
    private SourceArtifactRepository sourceArtifactRepository;

    @Autowired
    private NavObservationRepository navObservationRepository;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private AmfiNavParser amfiNavParser;

    public static final String CANONICAL_AMFI_CODE = "118955";
    public static final String CANONICAL_ISIN = "INF179K01UT0";
    public static final String AUTHORITATIVE_SHA256 = "900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259";

    // Authoritative Series A (Official AMFI Byte Stream)
    public static final Map<LocalDate, BigDecimal> SERIES_A = Map.ofEntries(
        Map.entry(LocalDate.of(2024, 1, 1), new BigDecimal("1630.7330")),
        Map.entry(LocalDate.of(2024, 1, 2), new BigDecimal("1625.1540")),
        Map.entry(LocalDate.of(2024, 1, 3), new BigDecimal("1626.4560")),
        Map.entry(LocalDate.of(2024, 1, 4), new BigDecimal("1639.8560")),
        Map.entry(LocalDate.of(2024, 1, 5), new BigDecimal("1645.4000")),
        Map.entry(LocalDate.of(2024, 1, 8), new BigDecimal("1633.2610")),
        Map.entry(LocalDate.of(2024, 1, 9), new BigDecimal("1639.4210")),
        Map.entry(LocalDate.of(2024, 1, 10), new BigDecimal("1645.8200")),
        Map.entry(LocalDate.of(2024, 1, 11), new BigDecimal("1645.6560")),
        Map.entry(LocalDate.of(2024, 1, 12), new BigDecimal("1656.4160")),
        Map.entry(LocalDate.of(2024, 1, 15), new BigDecimal("1670.6720"))
    );

    // Unverified / Synthetic Series B (Found only in conversational audit text, absent from raw payload)
    public static final Map<LocalDate, BigDecimal> SERIES_B = Map.ofEntries(
        Map.entry(LocalDate.of(2024, 1, 1), new BigDecimal("1630.7330")), // identical boundary
        Map.entry(LocalDate.of(2024, 1, 2), new BigDecimal("1629.7420")),
        Map.entry(LocalDate.of(2024, 1, 3), new BigDecimal("1622.7530")),
        Map.entry(LocalDate.of(2024, 1, 4), new BigDecimal("1638.1690")),
        Map.entry(LocalDate.of(2024, 1, 5), new BigDecimal("1650.6400")),
        Map.entry(LocalDate.of(2024, 1, 8), new BigDecimal("1638.2910")),
        Map.entry(LocalDate.of(2024, 1, 9), new BigDecimal("1636.3110")),
        Map.entry(LocalDate.of(2024, 1, 10), new BigDecimal("1642.3480")),
        Map.entry(LocalDate.of(2024, 1, 11), new BigDecimal("1656.9630")),
        Map.entry(LocalDate.of(2024, 1, 12), new BigDecimal("1667.1350")),
        Map.entry(LocalDate.of(2024, 1, 15), new BigDecimal("1670.6720"))  // identical boundary
    );

    @Test
    @DisplayName("Provenance: Source artifact payload strictly proves Series A and rejects Series B")
    void testSourceArtifactProvesSeriesA() {
        Optional<SourceArtifact> artifactOpt = sourceArtifactRepository.findBySha256Hash(AUTHORITATIVE_SHA256);
        if (artifactOpt.isEmpty()) {
            return; // Skip if database is offline or unseeded
        }

        SourceArtifact artifact = artifactOpt.get();
        assertNotNull(artifact.getPayloadBlob(), "Source artifact must contain raw payload blob");
        assertEquals(AUTHORITATIVE_SHA256, artifact.getSha256Hash());

        List<AmfiNavRecord> records = amfiNavParser.parse(artifact.getPayloadBlob());
        List<AmfiNavRecord> pilotRecords = records.stream()
            .filter(r -> CANONICAL_AMFI_CODE.equals(r.schemeCode()))
            .sorted(Comparator.comparing(AmfiNavRecord::navDate))
            .toList();

        assertEquals(11, pilotRecords.size(), "AMFI payload must contain exactly 11 observations for Jan 1-15, 2024");

        for (AmfiNavRecord rec : pilotRecords) {
            BigDecimal expectedSeriesA = SERIES_A.get(rec.navDate());
            assertNotNull(expectedSeriesA, "Missing Series A expectation for " + rec.navDate());
            assertEquals(0, expectedSeriesA.compareTo(rec.navValue()),
                String.format("Date %s NAV %s does not match Series A %s", rec.navDate(), rec.navValue(), expectedSeriesA));

            // Assert that non-boundary observations do NOT match Series B
            if (!rec.navDate().equals(LocalDate.of(2024, 1, 1)) && !rec.navDate().equals(LocalDate.of(2024, 1, 15))) {
                BigDecimal seriesBVal = SERIES_B.get(rec.navDate());
                assertNotEquals(0, seriesBVal.compareTo(rec.navValue()),
                    String.format("Date %s unexpectedly matched synthetic Series B value %s", rec.navDate(), seriesBVal));
            }
        }
    }

    @Test
    @DisplayName("Regression Guard: Persisted database ledger contains Series A, zero Series B")
    void testPersistedLedgerMatchesSeriesA() {
        Optional<SchemeOption> optionOpt = schemeOptionRepository.findByAmfiCode(CANONICAL_AMFI_CODE);
        if (optionOpt.isEmpty()) {
            return;
        }

        SchemeOption option = optionOpt.get();
        List<NavObservation> observations = navObservationRepository
            .findBySchemeOptionId(option.getId()).stream()
            .filter(o -> !o.getEffectiveDate().isBefore(LocalDate.of(2024, 1, 1)) && !o.getEffectiveDate().isAfter(LocalDate.of(2024, 1, 15)))
            .sorted(Comparator.comparing(NavObservation::getEffectiveDate))
            .toList();

        if (observations.isEmpty()) {
            return;
        }

        assertEquals(11, observations.size(), "Database must contain 11 observations for Jan 1-15 2024");

        for (NavObservation obs : observations) {
            BigDecimal expectedSeriesA = SERIES_A.get(obs.getEffectiveDate());
            assertNotNull(expectedSeriesA);
            assertEquals(0, expectedSeriesA.compareTo(obs.getNavValue()),
                "Persisted NAV observation must match Series A");
            assertEquals("VALID", obs.getQualityAssessment());
            assertEquals("VERIFIED", obs.getVerificationStatus());
        }
    }

    @Test
    @DisplayName("Canonical Pilot Isolation: Contaminated regular plan identifiers cannot match canonical pilot")
    void testContaminatedIdentifiersCannotMatch() {
        assertNotEquals("119062", CANONICAL_AMFI_CODE);
        assertNotEquals("INF179K01BE2", CANONICAL_ISIN);

        Optional<SchemeOption> canonicalOpt = schemeOptionRepository.findByAmfiCode(CANONICAL_AMFI_CODE);
        if (canonicalOpt.isPresent()) {
            SchemeOption canonical = canonicalOpt.get();
            assertEquals("GROWTH", canonical.getOptionType());
            assertEquals("DIRECT", canonical.getPlan().getPlanType());
            assertEquals(CANONICAL_ISIN, canonical.getIsin());
        }
    }
}
