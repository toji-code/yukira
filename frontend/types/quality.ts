export type QualityAssessment = 'VALID' | 'SUSPICIOUS' | 'INVALID';
export type VerificationStatus = 'VERIFIED' | 'UNVERIFIED';
export type RevisionStatus = 'ORIGINAL' | 'REVISED' | 'SUPERSEDED';
export type TemporalStatus = 'CURRENT' | 'STALE';
export type PresenceStatus = 'AVAILABLE' | 'MISSING' | 'NOT_APPLICABLE';
export type IntegrityCondition = 'NONE' | 'DUPLICATE' | 'CONFLICTING';

export interface DataQualityProfile {
  qualityAssessment: QualityAssessment;
  verificationStatus: VerificationStatus;
  revisionStatus: RevisionStatus;
  temporalStatus: TemporalStatus;
  presenceStatus: PresenceStatus;
  integrityCondition: IntegrityCondition;
}

export interface ValidationIssue {
  id: number;
  targetEntityType: string;
  targetEntityId: number;
  checkCode: string;
  qualityAssessment: QualityAssessment;
  verificationStatus: VerificationStatus;
  revisionStatus: RevisionStatus;
  temporalStatus: TemporalStatus;
  presenceStatus: PresenceStatus;
  integrityCondition: IntegrityCondition;
  message: string | null;
  detectedAt: string;
}
