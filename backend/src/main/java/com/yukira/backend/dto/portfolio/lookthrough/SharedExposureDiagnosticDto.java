package com.yukira.backend.dto.portfolio.lookthrough;

public record SharedExposureDiagnosticDto(
    String severity,
    String title,
    String diagnostic,
    String evidence
) {}
