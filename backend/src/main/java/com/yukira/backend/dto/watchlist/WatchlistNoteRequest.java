package com.yukira.backend.dto.watchlist;

public record WatchlistNoteRequest(
    String reasonSaved,
    String investigationQuestion,
    String nextStep,
    String thesisStatus,
    String associatedFindingId
) {
    public WatchlistNoteRequest(
        String reasonSaved,
        String investigationQuestion,
        String nextStep
    ) {
        this(reasonSaved, investigationQuestion, nextStep, "ACTIVE_HYPOTHESIS", null);
    }
}
