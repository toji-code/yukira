package com.yukira.backend.dto.portfolio.risk;

import com.yukira.backend.dto.portfolio.risk.stress.PortfolioAllocationSnapshotDto;
import com.yukira.backend.dto.portfolio.risk.stress.PortfolioStressAnalysisResponseDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Authoritative response payload for investor portfolio risk, diversification, lookback sensitivity,
 * and historical stress analysis (Tasks 17–24, 48, 57, 62, 67).
 *
 * Epistemic Guardrails:
 * - Provides unified portfolio allocation snapshot across Risk, Sensitivity, and Historical Stress.
 */
public record PortfolioRiskDiversificationResponseDto(
    String evaluationStatus,
    PortfolioRiskSummaryDto summary,
    PortfolioCovarianceMatrixDto covarianceMatrix,
    List<PortfolioFundRiskContributionDto> riskContributions,
    PortfolioDiversificationAnalysisDto diversification,
    PortfolioDownsideAnalysisDto downsideAnalysis,
    List<RiskInvestigationFindingDto> investigationFindings,
    List<String> governanceDisclaimers,
    Map<String, String> methodologyNotes,
    String calculationRunId,
    String evidenceThresholdPolicyVersion,
    BigDecimal numericalTolerance,
    Map<String, String> metricRegistryMappings,
    PortfolioLookbackSensitivityDto lookbackSensitivity,
    PortfolioStressAnalysisResponseDto historicalStressAnalysis,
    PortfolioAllocationSnapshotDto allocationSnapshot
) {
    public List<RiskInvestigationFindingDto> findings() {
        return investigationFindings;
    }

    /**
     * Backward-compatible 15-parameter constructor (Task 48 compatibility).
     */
    public PortfolioRiskDiversificationResponseDto(
        String evaluationStatus,
        PortfolioRiskSummaryDto summary,
        PortfolioCovarianceMatrixDto covarianceMatrix,
        List<PortfolioFundRiskContributionDto> riskContributions,
        PortfolioDiversificationAnalysisDto diversification,
        PortfolioDownsideAnalysisDto downsideAnalysis,
        List<RiskInvestigationFindingDto> investigationFindings,
        List<String> governanceDisclaimers,
        Map<String, String> methodologyNotes,
        String calculationRunId,
        String evidenceThresholdPolicyVersion,
        BigDecimal numericalTolerance,
        Map<String, String> metricRegistryMappings,
        PortfolioLookbackSensitivityDto lookbackSensitivity,
        PortfolioStressAnalysisResponseDto historicalStressAnalysis
    ) {
        this(
            evaluationStatus,
            summary,
            covarianceMatrix,
            riskContributions,
            diversification,
            downsideAnalysis,
            investigationFindings,
            governanceDisclaimers,
            methodologyNotes,
            calculationRunId,
            evidenceThresholdPolicyVersion,
            numericalTolerance,
            metricRegistryMappings,
            lookbackSensitivity,
            historicalStressAnalysis,
            historicalStressAnalysis != null ? historicalStressAnalysis.allocationSnapshot() : null
        );
    }

    /**
     * Backward-compatible 14-parameter constructor (pre-stress compatibility).
     */
    public PortfolioRiskDiversificationResponseDto(
        String evaluationStatus,
        PortfolioRiskSummaryDto summary,
        PortfolioCovarianceMatrixDto covarianceMatrix,
        List<PortfolioFundRiskContributionDto> riskContributions,
        PortfolioDiversificationAnalysisDto diversification,
        PortfolioDownsideAnalysisDto downsideAnalysis,
        List<RiskInvestigationFindingDto> investigationFindings,
        List<String> governanceDisclaimers,
        Map<String, String> methodologyNotes,
        String calculationRunId,
        String evidenceThresholdPolicyVersion,
        BigDecimal numericalTolerance,
        Map<String, String> metricRegistryMappings,
        PortfolioLookbackSensitivityDto lookbackSensitivity
    ) {
        this(
            evaluationStatus,
            summary,
            covarianceMatrix,
            riskContributions,
            diversification,
            downsideAnalysis,
            investigationFindings,
            governanceDisclaimers,
            methodologyNotes,
            calculationRunId,
            evidenceThresholdPolicyVersion,
            numericalTolerance,
            metricRegistryMappings,
            lookbackSensitivity,
            null,
            null
        );
    }
}
