package com.yukira.backend.dto.watchlist;

public record WatchlistNoteRequest(
    String reasonSaved,
    String investigationQuestion,
    String nextStep
) {}
