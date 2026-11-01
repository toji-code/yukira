export interface EvidenceClaim {
  claimText: string;
  claimType: "OBSERVATION" | "INTERPRETATION" | "RISK" | "LIMITATION" | "HYPOTHESIS";
  supportingMetricCodes: string[];
  horizon?: string;
  methodologyStatus?: string;
}

export interface GroundedAiInterpretation {
  summary: string;
  whatHappened: string[];
  interpretation: string[];
  riskFactors: string[];
  dataQualityCaveats: string[];
  invalidationFactors: string[];
  investigationQuestions: string[];
  conflictingEvidence?: string[];
  evidenceReferences?: Record<string, unknown>;
  calculationRunId?: number | null;
  schemeOptionId?: number | null;
  methodologyVersion?: string;
  asOfDate?: string;
  knowledgeCutoff?: string;
  sourceArtifacts?: string[];
  epistemicStatus: string;
  isFallback: boolean;
  modelProvider?: string;
  generatedAt?: string;
  horizon?: string;
  claims?: EvidenceClaim[];
  returnCharacteristics?: string[];
  riskCharacteristics?: string[];
  horizonInterpretation?: string[];
}
