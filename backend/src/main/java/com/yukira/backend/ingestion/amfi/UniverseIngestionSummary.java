package com.yukira.backend.ingestion.amfi;

import java.util.List;

public record UniverseIngestionSummary(
    Long sourceArtifactId,
    String sha256Hash,
    int totalRowsParsed,
    int amcsRegistered,
    int schemesRegistered,
    int plansRegistered,
    int optionsRegistered,
    int activeOptionsCount,
    int inactiveOptionsCount,
    int directOptionsCount,
    int regularOptionsCount,
    int growthOptionsCount,
    int idcwOptionsCount,
    int bonusOptionsCount,
    int otherOptionsCount,
    int missingIsinCount,
    int missingAmfiCodeCount,
    int missingCategoryCount,
    int missingSubcategoryCount,
    int duplicatesSkipped,
    int validationIssuesCreated,
    List<String> messages
) {
    public UniverseIngestionSummary(
        Long sourceArtifactId,
        String sha256Hash,
        int totalRowsParsed,
        int amcsRegistered,
        int schemesRegistered,
        int plansRegistered,
        int optionsRegistered,
        int duplicatesSkipped,
        int validationIssuesCreated,
        List<String> messages
    ) {
        this(
            sourceArtifactId, sha256Hash, totalRowsParsed, amcsRegistered, schemesRegistered,
            plansRegistered, optionsRegistered, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            duplicatesSkipped, validationIssuesCreated, messages
        );
    }
}
