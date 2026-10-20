package com.yukira.backend.dto.portfolio.stress;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Single aligned trading date along the stress period trajectory.
 */
public record TrajectoryPointDto(
    LocalDate date,
    BigDecimal portfolioCumulativeReturnPct,
    BigDecimal benchmarkCumulativeReturnPct,
    BigDecimal activeReturnPct,
    BigDecimal portfolioDrawdownPct,
    BigDecimal benchmarkDrawdownPct
) {}
