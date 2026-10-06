export interface GroundedAiInterpretation {
  summary: string;
  whatHappened: string[];
  interpretation: string[];
  riskFactors: string[];
  dataQualityCaveats: string[];
  invalidationFactors: string[];
  investigationQuestions: string[];
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
}
