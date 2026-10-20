package com.yukira.backend.service;

import com.yukira.backend.domain.entity.InvestorWatchlist;
import com.yukira.backend.dto.watchlist.WatchlistNoteRequest;
import com.yukira.backend.dto.watchlist.WatchlistRequest;
import com.yukira.backend.dto.watchlist.WatchlistResponse;
import com.yukira.backend.repository.InvestorWatchlistRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class InvestorResearchWorkspaceTest {

    @Autowired
    private InvestorService investorService;

    @Autowired
    private InvestorWatchlistRepository watchlistRepository;

    @Test
    @DisplayName("Save fund to watchlist and verify exact scheme identity")
    void testSaveAndRemoveWatchlistFund() {
        String investorId = "auth0|investor-alpha";

        WatchlistRequest req = new WatchlistRequest(1L, 1L, "Analyzing 3Y CAGR outperformance", "Is risk-adjusted semideviation stable?", "Compare vs PPFAS");
        WatchlistResponse saved = investorService.addWatchlistDto(investorId, req);

        assertThat(saved).isNotNull();
        assertThat(saved.schemeId()).isEqualTo(1L);
        assertThat(saved.schemeCode()).isEqualTo("HDFC_FLEXI");
        assertThat(saved.reasonSaved()).isEqualTo("Analyzing 3Y CAGR outperformance");
        assertThat(saved.investigationQuestion()).isEqualTo("Is risk-adjusted semideviation stable?");
        assertThat(saved.nextStep()).isEqualTo("Compare vs PPFAS");

        List<WatchlistResponse> list = investorService.getWatchlistDtoList(investorId);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).schemeId()).isEqualTo(1L);

        investorService.removeWatchlist(investorId, 1L);
        List<WatchlistResponse> afterRemove = investorService.getWatchlistDtoList(investorId);
        assertThat(afterRemove).isEmpty();
    }

    @Test
    @DisplayName("Prevent duplicate watchlist entries for the same investor and scheme")
    void testDuplicatePrevention() {
        String investorId = "auth0|investor-beta";

        WatchlistRequest req1 = new WatchlistRequest(1L, 1L, "Initial Note", "Initial Question", "Step 1");
        investorService.addWatchlistDto(investorId, req1);

        WatchlistRequest req2 = new WatchlistRequest(1L, 1L, "Updated Note", "Updated Question", "Step 2");
        investorService.addWatchlistDto(investorId, req2);

        List<WatchlistResponse> list = investorService.getWatchlistDtoList(investorId);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).reasonSaved()).isEqualTo("Updated Note");
    }

    @Test
    @DisplayName("Edit research note and validate note length limit")
    void testEditResearchNoteAndValidation() {
        String investorId = "auth0|investor-gamma";

        WatchlistRequest req = new WatchlistRequest(1L, null, "Note 1", "Question 1", "Step 1");
        investorService.addWatchlistDto(investorId, req);

        WatchlistNoteRequest updateReq = new WatchlistNoteRequest("Edited Note", "Edited Question", "Edited Step");
        WatchlistResponse updated = investorService.updateWatchlistNote(investorId, 1L, updateReq);

        assertThat(updated.reasonSaved()).isEqualTo("Edited Note");
        assertThat(updated.investigationQuestion()).isEqualTo("Edited Question");
        assertThat(updated.nextStep()).isEqualTo("Edited Step");

        // Oversized note validation (> 1000 chars)
        String oversized = "A".repeat(1001);
        WatchlistNoteRequest invalidReq = new WatchlistNoteRequest(oversized, "Q", "S");

        assertThatThrownBy(() -> investorService.updateWatchlistNote(investorId, 1L, invalidReq))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum allowed length");
    }

    @Test
    @DisplayName("Enforce strict ownership isolation between different investors")
    void testOwnershipIsolation() {
        String investorA = "auth0|investor-a";
        String investorB = "auth0|investor-b";

        WatchlistRequest reqA = new WatchlistRequest(1L, 1L, "Investor A Private Note", "Investor A Question", "Step A");
        investorService.addWatchlistDto(investorA, reqA);

        List<WatchlistResponse> listB = investorService.getWatchlistDtoList(investorB);
        assertThat(listB).isEmpty();

        // Investor B cannot modify Investor A's note
        WatchlistNoteRequest reqMod = new WatchlistNoteRequest("Malicious edit", "Q", "S");
        assertThatThrownBy(() -> investorService.updateWatchlistNote(investorB, 1L, reqMod))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Watchlist entry not found");
    }

    @Test
    @DisplayName("Support saving multiple distinct options of the same scheme independently")
    void testMultipleOptionsForSameScheme() {
        String investorId = "auth0|investor-multi-opt";

        // Save Option 1 (Direct Growth)
        WatchlistRequest reqOpt1 = new WatchlistRequest(1L, 1L, "Direct option for low expense ratio", "Alpha check", "Compare");
        WatchlistResponse savedOpt1 = investorService.addWatchlistDto(investorId, reqOpt1);
        assertThat(savedOpt1.schemeOptionId()).isEqualTo(1L);

        // Save Option 10223 (Regular Growth) for the exact same scheme (1L)
        WatchlistRequest reqOpt2 = new WatchlistRequest(1L, 10223L, "Regular option tracking distributor impact", "Fee spread", "Evaluate TER impact");
        WatchlistResponse savedOpt2 = investorService.addWatchlistDto(investorId, reqOpt2);
        assertThat(savedOpt2.schemeOptionId()).isEqualTo(10223L);

        // Verify both entries exist in investor's research workspace
        List<WatchlistResponse> list = investorService.getWatchlistDtoList(investorId);
        assertThat(list).hasSize(2);

        // Verify option-specific note update
        WatchlistNoteRequest updateOpt2 = new WatchlistNoteRequest("Updated Regular Note", null, "Next step regular");
        WatchlistResponse updatedOpt2 = investorService.updateWatchlistNoteByOption(investorId, 10223L, updateOpt2);
        assertThat(updatedOpt2.reasonSaved()).isEqualTo("Updated Regular Note");
        assertThat(updatedOpt2.investigationQuestion()).isNull(); // cleared
        assertThat(updatedOpt2.nextStep()).isEqualTo("Next step regular");

        // Verify Option 1 notes were unaffected
        List<WatchlistResponse> listAfterUpdate = investorService.getWatchlistDtoList(investorId);
        WatchlistResponse opt1After = listAfterUpdate.stream().filter(i -> i.schemeOptionId().equals(1L)).findFirst().orElseThrow();
        assertThat(opt1After.reasonSaved()).isEqualTo("Direct option for low expense ratio");
        assertThat(opt1After.investigationQuestion()).isEqualTo("Alpha check");

        // Remove only Option 10223
        investorService.removeWatchlistByOption(investorId, 10223L);
        List<WatchlistResponse> listAfterRemoval = investorService.getWatchlistDtoList(investorId);
        assertThat(listAfterRemoval).hasSize(1);
        assertThat(listAfterRemoval.get(0).schemeOptionId()).isEqualTo(1L);

        // Verify latest active NAV is resolved
        assertThat(opt1After.latestNav()).isNotNull();
        assertThat(opt1After.latestNavDate()).isNotNull();
    }
}
