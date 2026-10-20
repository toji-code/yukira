package com.yukira.backend.ingestion.mcap;

import com.yukira.backend.domain.entity.DataSource;
import com.yukira.backend.domain.entity.Security;
import com.yukira.backend.domain.entity.SecurityIdentifier;
import com.yukira.backend.domain.entity.SecurityMarketCapClassification;
import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.repository.DataSourceRepository;
import com.yukira.backend.repository.SecurityIdentifierRepository;
import com.yukira.backend.repository.SecurityMarketCapClassificationRepository;
import com.yukira.backend.repository.SourceArtifactRepository;
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
import org.springframework.core.io.ClassPathResource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AmfiMarketCapIngestionServiceTest {

    @Mock
    private AmfiMarketCapParser parser;

    @Mock
    private SecurityMarketCapClassificationRepository classificationRepository;

    @Mock
    private SecurityIdentifierRepository securityIdentifierRepository;

    @Mock
    private SourceArtifactRepository sourceArtifactRepository;

    @Mock
    private DataSourceRepository dataSourceRepository;

    @InjectMocks
    private AmfiMarketCapIngestionService ingestionService;

    private byte[] samplePayload;
    private DataSource sampleDataSource;
    private SourceArtifact sampleArtifact;

    @BeforeEach
    void setUp() {
        samplePayload = "DUMMY_XLSX_BYTES_FOR_MOCK".getBytes();
        sampleDataSource = new DataSource("AMFI_MCAP_CATEGORIZATION", "AMFI Market Cap", "https://www.amfiindia.com");
        sampleDataSource.setId(10L);

        sampleArtifact = new SourceArtifact(sampleDataSource, OffsetDateTime.now(), "AMFI_MCAP_CATEGORIZATION_XLSX", "hash123", 100L);
        sampleArtifact.setId(101L);

        when(dataSourceRepository.findByCode("AMFI_MCAP_CATEGORIZATION")).thenReturn(Optional.of(sampleDataSource));
        when(sourceArtifactRepository.findBySha256Hash(anyString())).thenReturn(Optional.of(sampleArtifact));
        when(sourceArtifactRepository.save(any(SourceArtifact.class))).thenReturn(sampleArtifact);
    }

    @Test
    @DisplayName("Dry run validates records without persisting classifications to database")
    void shouldExecuteDryRunSuccessfully() {
        List<AmfiMarketCapParser.ParsedMarketCapRecord> mockRecords = List.of(
            new AmfiMarketCapParser.ParsedMarketCapRecord(1, "Reliance Industries Ltd", "INE002A01018", "500325", "RELIANCE", new BigDecimal("2000000.00"), "LARGE_CAP"),
            new AmfiMarketCapParser.ParsedMarketCapRecord(101, "Bosch Ltd", "INE323A01026", "500530", "BOSCHLTD", new BigDecimal("95000.00"), "MID_CAP")
        );
        when(parser.parse(any())).thenReturn(new AmfiMarketCapParser.ParseResult("Title", mockRecords, Collections.emptyList(), Collections.emptyList()));

        AmfiMarketCapIngestionService.IngestionResult result = ingestionService.ingestArtifact(
            samplePayload, "URI", "Jul-Dec 2025", LocalDate.of(2025, 12, 31),
            LocalDate.of(2026, 1, 5), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 7, 31),
            OffsetDateTime.now(), true
        );

        assertThat(result.totalParsed()).isEqualTo(2);
        assertThat(result.savedCount()).isEqualTo(0);
        assertThat(result.validationErrors()).isEmpty();
        verify(classificationRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Ingestion idempotency: skips duplicate ingestion if ranking period already exists")
    void shouldSkipIfAlreadyIngested() {
        List<AmfiMarketCapParser.ParsedMarketCapRecord> mockRecords = List.of(
            new AmfiMarketCapParser.ParsedMarketCapRecord(1, "Reliance", "INE002A01018", "500325", "RELIANCE", new BigDecimal("2000000.00"), "LARGE_CAP")
        );
        when(parser.parse(any())).thenReturn(new AmfiMarketCapParser.ParseResult("Title", mockRecords, Collections.emptyList(), Collections.emptyList()));
        when(classificationRepository.existsByRankingPeriodEndDateAndRevisionSeq(LocalDate.of(2025, 12, 31), 1)).thenReturn(true);
        when(classificationRepository.countByRankingPeriodEndDate(LocalDate.of(2025, 12, 31))).thenReturn(5000L);

        AmfiMarketCapIngestionService.IngestionResult result = ingestionService.ingestArtifact(
            samplePayload, "URI", "Jul-Dec 2025", LocalDate.of(2025, 12, 31),
            LocalDate.of(2026, 1, 5), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 7, 31),
            OffsetDateTime.now(), false
        );

        assertThat(result.savedCount()).isEqualTo(0);
        assertThat(result.skippedDuplicates()).isEqualTo(5000L);
        verify(classificationRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Maps securities via ISIN and persists temporal Point-in-Time metadata")
    void shouldPersistVerifiedClassificationsWithCorrectTemporalBounds() {
        Security relianceSec = new Security();
        relianceSec.setId(501L);
        relianceSec.setCanonicalName("Reliance Industries Ltd");

        SecurityIdentifier si = new SecurityIdentifier();
        si.setSecurity(relianceSec);
        si.setIdType("ISIN");
        si.setIdValue("INE002A01018");

        when(securityIdentifierRepository.findByIdType("ISIN")).thenReturn(List.of(si));

        List<AmfiMarketCapParser.ParsedMarketCapRecord> mockRecords = List.of(
            new AmfiMarketCapParser.ParsedMarketCapRecord(1, "Reliance Industries Ltd", "INE002A01018", "500325", "RELIANCE", new BigDecimal("2000000.00"), "LARGE_CAP"),
            new AmfiMarketCapParser.ParsedMarketCapRecord(150, "Unmapped Co", "INE999X01099", "599999", "UNMAPPED", new BigDecimal("50000.00"), "MID_CAP")
        );
        when(parser.parse(any())).thenReturn(new AmfiMarketCapParser.ParseResult("Title", mockRecords, Collections.emptyList(), Collections.emptyList()));
        when(classificationRepository.existsByRankingPeriodEndDateAndRevisionSeq(any(), anyInt())).thenReturn(false);

        LocalDate endDate = LocalDate.of(2025, 12, 31);
        LocalDate pubDate = LocalDate.of(2026, 1, 5);
        LocalDate effFrom = LocalDate.of(2026, 2, 1);
        LocalDate effTo = LocalDate.of(2026, 7, 31);
        OffsetDateTime availTime = OffsetDateTime.of(2026, 1, 5, 18, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        AmfiMarketCapIngestionService.IngestionResult result = ingestionService.ingestArtifact(
            samplePayload, "https://amfi.com/mcap.xlsx", "Jul-Dec 2025", endDate, pubDate, effFrom, effTo, availTime, false
        );

        assertThat(result.totalParsed()).isEqualTo(2);
        assertThat(result.savedCount()).isEqualTo(2);
        assertThat(result.unmappedSecurities()).isEqualTo(1); // 1 unmapped

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SecurityMarketCapClassification>> captor = ArgumentCaptor.forClass(List.class);
        verify(classificationRepository).saveAll(captor.capture());

        List<SecurityMarketCapClassification> saved = captor.getValue();
        assertThat(saved).hasSize(2);

        SecurityMarketCapClassification relRec = saved.get(0);
        assertThat(relRec.getIsin()).isEqualTo("INE002A01018");
        assertThat(relRec.getMarketCapRank()).isEqualTo(1);
        assertThat(relRec.getMarketCapCategory()).isEqualTo("LARGE_CAP");
        assertThat(relRec.getSecurity()).isNotNull();
        assertThat(relRec.getSecurity().getId()).isEqualTo(501L);
        assertThat(relRec.getMappingStatus()).isEqualTo("MAPPED");
        assertThat(relRec.getEffectiveFrom()).isEqualTo(effFrom);
        assertThat(relRec.getEffectiveTo()).isEqualTo(effTo);
        assertThat(relRec.getSourcePublicationDate()).isEqualTo(pubDate);
        assertThat(relRec.getAvailabilityTime()).isEqualTo(availTime);

        SecurityMarketCapClassification unmappedRec = saved.get(1);
        assertThat(unmappedRec.getMappingStatus()).isEqualTo("UNMAPPED");
        assertThat(unmappedRec.getSecurity()).isNull();
        assertThat(unmappedRec.getMarketCapCategory()).isEqualTo("MID_CAP");
    }
}
