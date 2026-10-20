package com.yukira.backend.dto.watchlist;

public record WatchlistRequest(
    Long schemeId,
    Long schemeOptionId,
    String reasonSaved,
    String investigationQuestion,
    String nextStep
) {}
