package com.yukira.backend.dto.watchlist;

public record WatchlistRequest(
    Long schemeId,
    Long schemeOptionId,
    String reasonSaved,
    String investigationQuestion,
    String nextStep,
    String thesisStatus,
    String associatedFindingId
) {
    public WatchlistRequest(
        Long schemeId,
        Long schemeOptionId,
        String reasonSaved,
        String investigationQuestion,
        String nextStep
    ) {
        this(schemeId, schemeOptionId, reasonSaved, investigationQuestion, nextStep, "ACTIVE_HYPOTHESIS", null);
    }
}
