package com.yukira.backend.ingestion.holdings;

import com.yukira.backend.controller.MonthlyHoldingsAdminController;
import com.yukira.backend.repository.DataSourceRepository;
import com.yukira.backend.repository.SourceArtifactRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonthlyHoldingsSchedulerTest {

    @Mock
    private MonthlyHoldingsRefreshService refreshService;

    @Mock
    private SourceArtifactRepository sourceArtifactRepository;

    @Mock
    private DataSourceRepository dataSourceRepository;

    @Test
    @DisplayName("1. Scheduler does not trigger refresh when disabled")
    void testSchedulerDoesNotTriggerWhenDisabled() {
        MonthlyHoldingsRefreshScheduler scheduler = new MonthlyHoldingsRefreshScheduler(refreshService, false);
        assertFalse(scheduler.isRefreshEnabled());

        scheduler.runScheduledRefresh();

        verify(refreshService, never()).executeMonthlyHoldingsRefresh(any());
    }

    @Test
    @DisplayName("2. Scheduler triggers with SCHEDULED trigger type when enabled")
    void testSchedulerTriggersWithScheduledTriggerTypeWhenEnabled() {
        MonthlyHoldingsRefreshScheduler scheduler = new MonthlyHoldingsRefreshScheduler(refreshService, true);
        assertTrue(scheduler.isRefreshEnabled());

        MonthlyHoldingsRefreshRun mockRun = new MonthlyHoldingsRefreshRun(
            "RUN_123", TriggerType.SCHEDULED, OffsetDateTime.now(), OffsetDateTime.now(),
            3, 1, 1, 0, 0, 2, "SUCCESS", Collections.emptyList(), Collections.emptyList()
        );
        when(refreshService.executeMonthlyHoldingsRefresh(TriggerType.SCHEDULED)).thenReturn(mockRun);

        scheduler.runScheduledRefresh();

        verify(refreshService, times(1)).executeMonthlyHoldingsRefresh(TriggerType.SCHEDULED);
    }

    @Test
    @DisplayName("3. Scheduler exposes configured conservative cron expression")
    void testSchedulerExposesConfiguredCronExpression() {
        MonthlyHoldingsRefreshScheduler defaultScheduler = new MonthlyHoldingsRefreshScheduler(refreshService, true);
        assertEquals("0 0 2 12 * ?", defaultScheduler.getCronExpression(), "Default cron must run on 12th of month after SEBI 10th window");

        MonthlyHoldingsRefreshScheduler customScheduler = new MonthlyHoldingsRefreshScheduler(refreshService, true, "0 0 3 15 * ?");
        assertEquals("0 0 3 15 * ?", customScheduler.getCronExpression());
    }

    @Test
    @DisplayName("4. Admin controller triggers manual refresh with MANUAL trigger type")
    void testAdminControllerTriggersManualRefresh() {
        MonthlyHoldingsAdminController controller = new MonthlyHoldingsAdminController(refreshService);
        MonthlyHoldingsRefreshRun mockRun = new MonthlyHoldingsRefreshRun(
            "RUN_456", TriggerType.MANUAL, OffsetDateTime.now(), OffsetDateTime.now(),
            3, 1, 1, 0, 0, 2, "SUCCESS", Collections.emptyList(), Collections.emptyList()
        );
        when(refreshService.executeMonthlyHoldingsRefresh(TriggerType.MANUAL)).thenReturn(mockRun);

        ResponseEntity<MonthlyHoldingsRefreshRun> response = controller.triggerRefresh();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("RUN_456", response.getBody().runId());
        assertEquals(TriggerType.MANUAL, response.getBody().triggerType());
    }

    @Test
    @DisplayName("5. Admin controller returns HTTP 409 Conflict when concurrent run is in progress")
    void testAdminControllerReturnsConflictWhenConcurrentRunSkipped() {
        MonthlyHoldingsAdminController controller = new MonthlyHoldingsAdminController(refreshService);
        MonthlyHoldingsRefreshRun concurrentRun = new MonthlyHoldingsRefreshRun(
            "HOLDINGS_REFRESH_CONCURRENT", TriggerType.MANUAL, OffsetDateTime.now(), OffsetDateTime.now(),
            0, 0, 0, 0, 0, 0, "CONCURRENT_RUN_SKIPPED", Collections.emptyList(),
            List.of("Skipped execution: a monthly holdings refresh run is already in progress.")
        );
        when(refreshService.executeMonthlyHoldingsRefresh(TriggerType.MANUAL)).thenReturn(concurrentRun);

        ResponseEntity<MonthlyHoldingsRefreshRun> response = controller.triggerRefresh();

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("CONCURRENT_RUN_SKIPPED", response.getBody().overallStatus());
    }

    @Test
    @DisplayName("6. Admin controller returns operational status and pilot verification details")
    void testAdminControllerReturnsOperationalStatus() {
        MonthlyHoldingsAdminController controller = new MonthlyHoldingsAdminController(refreshService);

        PilotFundHoldingsStatusDto pilotDto = new PilotFundHoldingsStatusDto(
            1L, "HDFC_MF", "HDFC Flexi Cap Fund", "118955", "INF179K01UT0",
            LocalDate.of(2026, 9, 30), 85, new BigDecimal("1.005300"), 8311L,
            "495a3d78dd97283b...", "CURRENT", "VALID", "REAL_SOURCE_ACQUIRED", 4
        );

        MonthlyHoldingsRefreshStatusDto statusDto = new MonthlyHoldingsRefreshStatusDto(
            false, "0 0 2 12 * ?", OffsetDateTime.now(), false, null, null, List.of(pilotDto)
        );

        when(refreshService.getOperationalStatus()).thenReturn(statusDto);

        ResponseEntity<MonthlyHoldingsRefreshStatusDto> response = controller.getOperationalStatus();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().schedulerEnabled());
        assertEquals("0 0 2 12 * ?", response.getBody().configuredCron());
        assertEquals(1, response.getBody().pilotFundsStatus().size());
        assertEquals("118955", response.getBody().pilotFundsStatus().get(0).amfiCode());
    }

    @Test
    @DisplayName("7. Source fetcher rejects HTML error pages masquerading as workbooks")
    void testSourceFetcherRejectsHtmlErrorPage() {
        AuthoritativeHoldingsSourceFetcher fetcher = new AuthoritativeHoldingsSourceFetcher(sourceArtifactRepository, dataSourceRepository);
        byte[] htmlErrorBytes = "<html><head><title>500 Internal Server Error</title></head><body>Server Error</body></html>".getBytes(StandardCharsets.UTF_8);

        MonthlyHoldingsSourceDescriptor descriptor = MonthlyHoldingsSourceDescriptor.of(
            "HDFC_MF", "HDFC Mutual Fund", "HDFC_FLEXI", "HDFC Flexi Cap Fund", "118955", "INF179K01UT0",
            "https://files.hdfcfund.com/fake.xlsx", LocalDate.of(2026, 9, 30)
        );

        AuthoritativeHoldingsSourceFetcher.FetchResult result = fetcher.processRawBytes(htmlErrorBytes, descriptor.sourceUrl(), descriptor);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(MonthlyHoldingsAcquisitionResult.Status.INVALID_RESPONSE, result.status());
        assertTrue(result.statusMessage().contains("HTML page"));
    }

    @Test
    @DisplayName("8. Source fetcher rejects unsupported PDF binary documents")
    void testSourceFetcherRejectsPdfBinaryFormat() {
        AuthoritativeHoldingsSourceFetcher fetcher = new AuthoritativeHoldingsSourceFetcher(sourceArtifactRepository, dataSourceRepository);
        byte[] pdfBytes = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x35}; // %PDF-1.5

        MonthlyHoldingsSourceDescriptor descriptor = MonthlyHoldingsSourceDescriptor.of(
            "PPFAS_MF", "PPFAS Mutual Fund", "PPFAS_FLEXI", "Parag Parikh Flexi Cap Fund", "122639", "INF879O01027",
            "https://amc.ppfas.com/fake.pdf", LocalDate.of(2026, 9, 30)
        );

        AuthoritativeHoldingsSourceFetcher.FetchResult result = fetcher.processRawBytes(pdfBytes, descriptor.sourceUrl(), descriptor);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(MonthlyHoldingsAcquisitionResult.Status.UNSUPPORTED_FORMAT, result.status());
        assertTrue(result.statusMessage().contains("PDF binary"));
    }

    @Test
    @DisplayName("9. Source fetcher rejects non-HTTPS production URLs")
    void testSourceFetcherRejectsNonHttpsUrl() {
        AuthoritativeHoldingsSourceFetcher fetcher = new AuthoritativeHoldingsSourceFetcher(sourceArtifactRepository, dataSourceRepository);
        MonthlyHoldingsSourceDescriptor insecureDescriptor = MonthlyHoldingsSourceDescriptor.of(
            "PPFAS_MF", "PPFAS Mutual Fund", "PPFAS_FLEXI", "Parag Parikh Flexi Cap Fund", "122639", "INF879O01027",
            "http://insecure.amc.ppfas.com/disclosure.xlsx", LocalDate.of(2026, 9, 30)
        );

        AuthoritativeHoldingsSourceFetcher.FetchResult result = fetcher.acquireArtifact(insecureDescriptor);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(MonthlyHoldingsAcquisitionResult.Status.INVALID_RESPONSE, result.status());
        assertTrue(result.statusMessage().contains("Production acquisition URL must use HTTPS"));
    }

    @Test
    @DisplayName("10. FetchHtml rejects non-HTTPS discovery URLs and blank input")
    void testFetchHtmlValidation() {
        AuthoritativeHoldingsSourceFetcher fetcher = new AuthoritativeHoldingsSourceFetcher(sourceArtifactRepository, dataSourceRepository);

        AuthoritativeHoldingsSourceFetcher.HtmlFetchResult blankResult = fetcher.fetchHtml("   ");
        assertFalse(blankResult.success());
        assertEquals(400, blankResult.statusCode());

        AuthoritativeHoldingsSourceFetcher.HtmlFetchResult insecureResult = fetcher.fetchHtml("http://amc.ppfas.com/index.html");
        assertFalse(insecureResult.success());
        assertEquals(400, insecureResult.statusCode());
        assertTrue(insecureResult.errorMessage().contains("HTTPS"));
    }
}
