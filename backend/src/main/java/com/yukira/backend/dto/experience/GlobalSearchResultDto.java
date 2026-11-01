package com.yukira.backend.dto.experience;

public record GlobalSearchResultDto(
    String resultType, // FUND, COMPARISON, ACTION, MONITORING, PORTFOLIO
    String title,
    String subtitle,
    String targetUrl,
    String badgeText,
    String badgeVariant // DEFAULT, SUCCESS, WARNING, PURPLE
) {}

