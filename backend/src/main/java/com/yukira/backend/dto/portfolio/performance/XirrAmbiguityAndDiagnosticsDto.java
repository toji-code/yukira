package com.yukira.backend.dto.portfolio.performance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Detailed diagnostics on XIRR convergence, polynomial sign alternations, and root uniqueness.
 *
 * Epistemic Rules:
 * 1. Root ambiguity (Descartes' rule of signs / multi-root polynomial) is disclosed explicitly.
 * 2. When multiple roots exist, solver does not silently choose one; it flags AMBIGUOUS_ROOT.
 * 3. Fund CAGR comparison is documented strictly as a return difference over specified periods, not alpha.
 */
public record XirrAmbiguityAndDiagnosticsDto(
    BigDecimal portfolioXirr,
    String portfolioXirrStatus,
    String solverMethod,
    Integer iterations,
    BigDecimal residualNpv,
    String dayCountConvention,
    LocalDate firstCashFlowDate,
    LocalDate lastCashFlowDate,
    LocalDate endingValuationDate,
    Integer cashFlowCount,
    Integer signAlternationsCount,
    Boolean isMultipleRootsPossible,
    List<BigDecimal> rootCandidates,
    String diagnosticExplanation,
    String fundCagrComparison
) {}

