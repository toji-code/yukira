'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import {
  MonitoringEventDto,
  MonitoringAiExplanationDto,
  CLASSIFICATION_META,
  SEVERITY_META,
  LIFECYCLE_META,
  updateEventLifecycle,
  addEventNote,
  getEventAiExplanation,
} from '@/lib/api/monitoring';
import { getMetricEvidenceDefinition } from '@/lib/analysis/metricEvidence';

interface MonitoringEventDetailModalProps {
  isOpen: boolean;
  onClose: () => void;
  event: MonitoringEventDto | null;
  onEventUpdated: (updated: MonitoringEventDto) => void;
}

export function MonitoringEventDetailModal({
  isOpen,
  onClose,
  event,
  onEventUpdated,
}: MonitoringEventDetailModalProps) {
  const [activeTab, setActiveTab] = useState<'evidence' | 'ai_explanation' | 'notes'>('evidence');
  const [newNote, setNewNote] = useState('');
  const [resolutionNote, setResolutionNote] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [aiExplanation, setAiExplanation] = useState<MonitoringAiExplanationDto | null>(null);
  const [loadingAi, setLoadingAi] = useState(false);
  const [aiError, setAiError] = useState<string | null>(null);

  useEffect(() => {
    if (event && isOpen) {
      setNewNote('');
      setResolutionNote(event.resolutionNote || '');
      setAiExplanation(null);
      setAiError(null);
      setActiveTab('evidence');
    }
  }, [event, isOpen]);

  if (!isOpen || !event) return null;

  const classMeta = CLASSIFICATION_META[event.eventClassification] || {
    label: event.eventClassification,
    badgeClass: 'bg-slate-500/20 text-slate-300 border-slate-500/30',
    description: '',
  };

  const sevMeta = SEVERITY_META[event.severity] || {
    label: event.severity,
    badgeClass: 'bg-slate-500/20 text-slate-300 border-slate-500/30',
    dotClass: 'bg-slate-400',
  };

  const lifeMeta = LIFECYCLE_META[event.lifecycleStatus] || {
    label: event.lifecycleStatus,
    badgeClass: 'bg-slate-500/20 text-slate-300 border-slate-500/30',
  };

  const metricDef = getMetricEvidenceDefinition(event.metricOrEvidenceId);

  const handleStatusChange = async (newStatus: 'ACKNOWLEDGED' | 'INVESTIGATING' | 'RESOLVED' | 'DISMISSED') => {
    setIsSubmitting(true);
    try {
      const updated = await updateEventLifecycle(event.id, {
        lifecycleStatus: newStatus,
        resolutionNote: (newStatus === 'RESOLVED' || newStatus === 'DISMISSED') ? resolutionNote : undefined,
      });
      onEventUpdated(updated);
    } catch (err) {
      console.error('Failed to update event lifecycle', err);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleAddNote = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newNote.trim()) return;
    setIsSubmitting(true);
    try {
      const added = await addEventNote(event.id, newNote.trim());
      const updatedNotes = [added, ...(event.notes || [])];
      const updatedEvent = { ...event, notes: updatedNotes };
      onEventUpdated(updatedEvent);
      setNewNote('');
    } catch (err) {
      console.error('Failed to add note', err);
    } finally {
      setIsSubmitting(false);
    }
  };

  const fetchAiExplanation = async () => {
    if (aiExplanation || loadingAi) return;
    setLoadingAi(true);
    setAiError(null);
    try {
      const data = await getEventAiExplanation(event.id);
      setAiExplanation(data);
    } catch (err) {
      console.error('Failed to load AI explanation', err);
      setAiError('Unable to generate explanation. Deterministic evidence is available in the Evidence tab.');
    } finally {
      setLoadingAi(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6 bg-black/80 backdrop-blur-md overflow-y-auto"
      role="dialog"
      aria-modal="true"
      aria-labelledby="monitoring-event-title"
    >
      <div className="relative w-full max-w-4xl max-h-[92vh] flex flex-col rounded-2xl border border-border-gold-subtle bg-surface-midnight shadow-2xl overflow-hidden">
        {/* Modal Header */}
        <div className="flex items-start justify-between p-6 border-b border-border-gold-subtle bg-surface-navy-raised/40">
          <div className="space-y-2 pr-4">
            <div className="flex flex-wrap items-center gap-2">
              <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-[11px] font-mono font-medium border ${classMeta.badgeClass}`}>
                {classMeta.label}
              </span>
              <span className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-mono font-medium border ${sevMeta.badgeClass}`}>
                <span className={`h-1.5 w-1.5 rounded-full ${sevMeta.dotClass}`} />
                {sevMeta.label} SEVERITY
              </span>
              <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-[11px] font-mono font-medium border ${lifeMeta.badgeClass}`}>
                STATUS: {lifeMeta.label.toUpperCase()}
              </span>
              {event.isDataArtifact && (
                <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-[11px] font-mono font-semibold border bg-amber-500/10 text-amber-300 border-amber-500/30">
                  DATA ARTIFACT / PIPELINE
                </span>
              )}
            </div>
            <h2 id="monitoring-event-title" className="text-xl font-bold text-text-cream-bright tracking-tight">
              {event.metricName}
            </h2>
            <p className="text-xs text-text-cream-muted">
              Target: <span className="text-text-cream-bright font-medium">{event.schemeName || 'Portfolio'}</span>
              {event.schemeCode && ` (${event.schemeCode})`}
              {event.planType && ` · ${event.planType}`}
              {event.currentObservationDate && ` · Observation Date: ${event.currentObservationDate}`}
            </p>
          </div>
          <button
            onClick={onClose}
            className="p-2 text-text-cream-muted hover:text-text-cream-bright hover:bg-surface-navy-raised rounded-lg transition-colors"
            aria-label="Close dialog"
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Tab Navigation */}
        <div className="flex border-b border-border-gold-subtle bg-surface-midnight px-6 gap-2">
          <button
            onClick={() => setActiveTab('evidence')}
            className={`py-3 px-4 text-xs font-semibold uppercase tracking-wider border-b-2 transition-all ${
              activeTab === 'evidence'
                ? 'border-primary text-primary font-bold'
                : 'border-transparent text-text-cream-muted hover:text-text-cream-bright'
            }`}
          >
            Evidence & Integrity
          </button>
          <button
            onClick={() => {
              setActiveTab('ai_explanation');
              fetchAiExplanation();
            }}
            className={`py-3 px-4 text-xs font-semibold uppercase tracking-wider border-b-2 transition-all flex items-center gap-1.5 ${
              activeTab === 'ai_explanation'
                ? 'border-primary text-primary font-bold'
                : 'border-transparent text-text-cream-muted hover:text-text-cream-bright'
            }`}
          >
            <span>Institutional Interpretation</span>
            <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-primary/20 text-primary">AI Grounded</span>
          </button>
          <button
            onClick={() => setActiveTab('notes')}
            className={`py-3 px-4 text-xs font-semibold uppercase tracking-wider border-b-2 transition-all ${
              activeTab === 'notes'
                ? 'border-primary text-primary font-bold'
                : 'border-transparent text-text-cream-muted hover:text-text-cream-bright'
            }`}
          >
            Investigation Notes ({event.notes?.length || 0})
          </button>
        </div>

        {/* Tab Content */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {activeTab === 'evidence' && (
            <div className="space-y-6">
              {/* Critical Requirement 4: Real Change vs Data Artifact Banner */}
              {event.isDataArtifact ? (
                <div className="p-4 rounded-xl border border-amber-500/30 bg-amber-500/10 space-y-1.5">
                  <div className="flex items-center gap-2 text-amber-300 font-semibold text-xs tracking-wider uppercase">
                    <svg className="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                    </svg>
                    Data Quality / Pipeline Timing Artifact — Not Confirmed Value Deterioration
                  </div>
                  <p className="text-xs text-text-cream-bright leading-relaxed">
                    {event.artifactExplanation || 'This event was triggered by a delay in source publication, reporting cadence lag, or coverage gap. Do not interpret this as a decline in investment value.'}
                  </p>
                </div>
              ) : (
                <div className="p-4 rounded-xl border border-blue-500/30 bg-blue-500/10 space-y-1.5">
                  <div className="flex items-center gap-2 text-blue-300 font-semibold text-xs tracking-wider uppercase">
                    <svg className="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                    Verified Empirical Investment Evidence Change
                  </div>
                  <p className="text-xs text-text-cream-bright leading-relaxed">
                    This change originates from verified empirical calculation runs or disclosed holdings updates. Both prior and current observations have been cryptographically preserved.
                  </p>
                </div>
              )}

              {/* Observation Shift Grid */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div className="p-4 rounded-xl border border-border-gold-subtle bg-surface-navy-raised/30">
                  <span className="text-[10px] font-mono uppercase tracking-wider text-text-cream-muted">Previous State</span>
                  <div className="mt-1 text-base font-bold text-text-cream-bright font-mono">
                    {event.previousValue || 'Baseline / Unrecorded'}
                  </div>
                  {event.previousObservationDate && (
                    <span className="text-[10px] text-text-cream-muted">Date: {event.previousObservationDate}</span>
                  )}
                </div>

                <div className="p-4 rounded-xl border border-border-gold-subtle bg-surface-navy-raised/30">
                  <span className="text-[10px] font-mono uppercase tracking-wider text-text-cream-muted">Latest Observed State</span>
                  <div className="mt-1 text-base font-bold text-primary font-mono">
                    {event.currentValue || 'Unavailable'}
                  </div>
                  {event.currentObservationDate && (
                    <span className="text-[10px] text-text-cream-muted">Date: {event.currentObservationDate}</span>
                  )}
                </div>

                <div className="p-4 rounded-xl border border-border-gold-subtle bg-surface-navy-raised/30">
                  <span className="text-[10px] font-mono uppercase tracking-wider text-text-cream-muted">Change Magnitude</span>
                  <div className="mt-1 text-base font-bold text-text-cream-bright font-mono">
                    {event.changeMagnitude !== null && event.changeMagnitude !== undefined
                      ? `${event.changeMagnitude > 0 ? '+' : ''}${event.changeMagnitude} ${event.unitOrFormat || ''}`
                      : 'State Transition'}
                  </div>
                  <span className="text-[10px] text-text-cream-muted">Metric: {event.metricOrEvidenceId}</span>
                </div>
              </div>

              {/* Authoritative Metric Registry Specification */}
              <div className="p-4 rounded-xl border border-border-gold-subtle bg-surface-navy-raised/20 space-y-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="px-2 py-0.5 rounded font-mono text-[10px] bg-primary/20 text-primary border border-primary/30 font-bold">
                      {metricDef.code}
                    </span>
                    <span className="text-xs font-bold text-text-cream-bright">
                      {metricDef.canonicalName || metricDef.name}
                    </span>
                  </div>
                  <span className={`px-2 py-0.5 rounded-full text-[10px] font-mono border ${
                    metricDef.governanceStatus === 'OPERATIONAL_BASELINE' || metricDef.governanceStatus === 'APPROVED'
                      ? 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30'
                      : 'bg-amber-500/15 text-amber-300 border-amber-500/30'
                  }`}>
                    {metricDef.governanceStatus}
                  </span>
                </div>

                <p className="text-xs text-text-cream-muted leading-relaxed">
                  {metricDef.whyItMatters}
                </p>

                {metricDef.formulaSpecification && (
                  <div className="p-2.5 rounded-lg bg-surface-midnight/80 border border-border-gold-subtle font-mono text-[11px] text-primary">
                    {metricDef.formulaSpecification}
                  </div>
                )}

                {metricDef.investigationQuestion && (
                  <div className="p-3 rounded-lg border border-primary/25 bg-primary/5 space-y-1">
                    <span className="text-[10px] font-mono uppercase tracking-wider text-primary font-bold">
                      Before Capital Commitment — Ask One More Question:
                    </span>
                    <p className="text-xs text-text-cream-bright italic">
                      &ldquo;{metricDef.investigationQuestion}&rdquo;
                    </p>
                  </div>
                )}
              </div>

              {/* Detailed Description */}
              <div className="space-y-2">
                <h3 className="text-xs font-mono font-semibold uppercase tracking-wider text-text-cream-muted">
                  What Changed & Why Triggered
                </h3>
                <div className="p-4 rounded-xl border border-border-gold-subtle bg-surface-navy-raised/20 text-xs text-text-cream-bright leading-relaxed">
                  {event.explanation}
                </div>
                {event.triggerRationale && (
                  <p className="text-[11px] text-text-cream-muted italic">
                    Trigger rationale: {event.triggerRationale}
                  </p>
                )}
              </div>

              {/* Research Thesis Invalidation Context (Requirement 6) */}
              {event.associatedThesisKey && (
                <div className="p-4 rounded-xl border border-red-500/30 bg-red-500/10 space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-mono font-bold text-red-300 uppercase tracking-wider">
                      Associated Research Thesis Invalidation Criteria
                    </span>
                    <Link
                      href={`/funds/${event.schemeId || ''}`}
                      className="text-xs text-primary underline hover:text-text-cream-bright font-medium"
                    >
                      Open Research Workspace &rarr;
                    </Link>
                  </div>
                  <p className="text-xs text-text-cream-bright leading-relaxed">
                    This event links to your active research thesis key <code className="font-mono text-primary">{event.associatedThesisKey}</code>.
                    A triggered condition indicates you should systematically reassess your hypothesis; it does not automatically invalidate your thesis.
                  </p>
                </div>
              )}

              {/* Audit & Provenance Ledger */}
              <div className="space-y-2">
                <h3 className="text-xs font-mono font-semibold uppercase tracking-wider text-text-cream-muted">
                  Cryptographic Lineage & Calculation Provenance
                </h3>
                <div className="p-4 rounded-xl border border-border-gold-subtle bg-surface-navy-raised/20 space-y-2 font-mono text-[11px]">
                  <div className="flex justify-between">
                    <span className="text-text-cream-muted">Threshold Classification:</span>
                    <span className="text-primary font-bold">{event.thresholdClassification || 'SYSTEM_CANDIDATE_THRESHOLD'}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-text-cream-muted">Evidence State:</span>
                    <span className={`font-bold ${event.evidenceState === 'STALE' ? 'text-amber-300' : event.evidenceState === 'MISSING' ? 'text-red-300' : 'text-emerald-300'}`}>
                      {event.evidenceState || 'AVAILABLE'}
                    </span>
                  </div>
                  {event.effectiveDate && (
                    <div className="flex justify-between">
                      <span className="text-text-cream-muted">Effective Market Date:</span>
                      <span className="text-text-cream-bright">{event.effectiveDate}</span>
                    </div>
                  )}
                  {event.availabilityTime && (
                    <div className="flex justify-between">
                      <span className="text-text-cream-muted">Availability / Recorded Time:</span>
                      <span className="text-text-cream-bright">{event.availabilityTime}</span>
                    </div>
                  )}
                  <div className="flex justify-between">
                    <span className="text-text-cream-muted">Knowledge Cutoff:</span>
                    <span className="text-text-cream-bright">{event.knowledgeCutoff || 'Point-in-time'}</span>
                  </div>
                  {event.executionId && (
                    <div className="flex justify-between">
                      <span className="text-text-cream-muted">Monitoring Execution ID:</span>
                      <span className="text-text-cream-bright">#{event.executionId}</span>
                    </div>
                  )}
                  <div className="flex justify-between">
                    <span className="text-text-cream-muted">Calculation Run ID:</span>
                    <span className="text-text-cream-bright">{event.calculationRunId ? `#${event.calculationRunId}` : 'N/A'}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-text-cream-muted">Metric Registry Version:</span>
                    <span className="text-text-cream-bright">{event.metricRegistryVersion || 'V1'}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-text-cream-muted">Methodology Version:</span>
                    <span className="text-text-cream-bright">{event.methodologyVersion || 'PHASE_2H_FROZEN'}</span>
                  </div>
                  {event.sourceReference && (
                    <div className="flex justify-between">
                      <span className="text-text-cream-muted">Source Hash / Artifact:</span>
                      <span className="text-text-cream-bright truncate max-w-xs">{event.sourceReference}</span>
                    </div>
                  )}
                  {event.dedupHash && (
                    <div className="flex justify-between">
                      <span className="text-text-cream-muted">Dedup Key:</span>
                      <span className="text-text-cream-bright">{event.dedupHash}</span>
                    </div>
                  )}
                </div>
                {event.knownLimitations && (
                  <p className="text-[11px] text-text-cream-muted">
                    <strong className="text-text-cream-bright">Known Limitations:</strong> {event.knownLimitations}
                  </p>
                )}
              </div>
            </div>
          )}

          {activeTab === 'ai_explanation' && (
            <div className="space-y-6">
              {loadingAi && (
                <div className="py-12 text-center space-y-3">
                  <div className="inline-block animate-spin rounded-full h-8 w-8 border-2 border-primary border-t-transparent" />
                  <p className="text-xs text-text-cream-muted font-mono">Synthesizing institutional event interpretation...</p>
                </div>
              )}

              {aiError && (
                <div className="p-4 rounded-xl border border-rose-500/30 bg-rose-500/10 text-xs text-rose-300">
                  {aiError}
                </div>
              )}

              {aiExplanation && (
                <div className="space-y-6">
                  {/* Summary */}
                  <div className="p-4 rounded-xl border border-border-gold-subtle bg-surface-navy-raised/30 space-y-2">
                    <h4 className="text-xs font-mono font-semibold uppercase tracking-wider text-primary">
                      Plain-Language Summary
                    </h4>
                    <p className="text-xs text-text-cream-bright leading-relaxed">
                      {aiExplanation.plainLanguageExplanation}
                    </p>
                  </div>

                  {/* Historical Evidence */}
                  <div className="space-y-2">
                    <h4 className="text-xs font-mono font-semibold uppercase tracking-wider text-text-cream-muted">
                      Historical Empirical Grounding
                    </h4>
                    <p className="text-xs text-text-cream-bright leading-relaxed p-3 rounded-lg bg-surface-navy-raised/20 border border-border-gold-subtle">
                      {aiExplanation.historicalEvidenceSummary}
                    </p>
                  </div>

                  {/* Possible Implications */}
                  {aiExplanation.possibleImplications?.length > 0 && (
                    <div className="space-y-2">
                      <h4 className="text-xs font-mono font-semibold uppercase tracking-wider text-text-cream-muted">
                        Potential Analytical Implications
                      </h4>
                      <ul className="space-y-1 text-xs text-text-cream-bright list-disc list-inside">
                        {aiExplanation.possibleImplications.map((imp, idx) => (
                          <li key={idx} className="leading-relaxed">{imp}</li>
                        ))}
                      </ul>
                    </div>
                  )}

                  {/* Caveats */}
                  {aiExplanation.conflictingEvidenceOrCaveats?.length > 0 && (
                    <div className="space-y-2">
                      <h4 className="text-xs font-mono font-semibold uppercase tracking-wider text-amber-300">
                        Methodological Caveats & Conflicting Signals
                      </h4>
                      <ul className="space-y-1 text-xs text-text-cream-muted list-disc list-inside">
                        {aiExplanation.conflictingEvidenceOrCaveats.map((cav, idx) => (
                          <li key={idx} className="leading-relaxed">{cav}</li>
                        ))}
                      </ul>
                    </div>
                  )}

                  {/* Specific Questions To Investigate */}
                  {aiExplanation.specificInvestigationQuestions?.length > 0 && (
                    <div className="p-4 rounded-xl border border-primary/30 bg-primary/5 space-y-3">
                      <h4 className="text-xs font-mono font-bold uppercase tracking-wider text-primary">
                        Recommended Due-Diligence Questions to Investigate
                      </h4>
                      <div className="space-y-2">
                        {aiExplanation.specificInvestigationQuestions.map((q, idx) => (
                          <div key={idx} className="flex items-start gap-2 text-xs text-text-cream-bright">
                            <span className="font-mono text-primary font-bold shrink-0">{idx + 1}.</span>
                            <span>{q}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Governance Disclaimer */}
                  <div className="text-[10px] text-text-cream-muted italic border-t border-border-gold-subtle pt-3">
                    {aiExplanation.governanceDisclaimer}
                  </div>
                </div>
              )}
            </div>
          )}

          {activeTab === 'notes' && (
            <div className="space-y-6">
              {/* Add Note Form */}
              <form onSubmit={handleAddNote} className="space-y-3">
                <label htmlFor="investigation-note-input" className="block text-xs font-mono font-semibold uppercase tracking-wider text-text-cream-muted">
                  Add Due-Diligence / Investigation Note
                </label>
                <textarea
                  id="investigation-note-input"
                  rows={3}
                  value={newNote}
                  onChange={(e) => setNewNote(e.target.value)}
                  placeholder="Record your empirical observations, thesis questions, or next steps..."
                  className="w-full p-3 rounded-xl border border-border-gold-subtle bg-surface-navy-raised/40 text-xs text-text-cream-bright placeholder-text-cream-muted/50 focus:outline-none focus:ring-1 focus:ring-primary"
                />
                <button
                  type="submit"
                  disabled={isSubmitting || !newNote.trim()}
                  className="px-4 py-2 rounded-lg bg-primary text-surface-midnight font-bold text-xs uppercase tracking-wider hover:bg-primary/90 transition-all disabled:opacity-50"
                >
                  Save Note
                </button>
              </form>

              {/* Notes Timeline */}
              <div className="space-y-3 pt-4 border-t border-border-gold-subtle">
                <h4 className="text-xs font-mono font-semibold uppercase tracking-wider text-text-cream-muted">
                  Investigation Audit Trail
                </h4>
                {(!event.notes || event.notes.length === 0) ? (
                  <p className="text-xs text-text-cream-muted italic py-4">No investigation notes recorded yet.</p>
                ) : (
                  <div className="space-y-3">
                    {event.notes.map((n) => (
                      <div key={n.id} className="p-3 rounded-xl border border-border-gold-subtle bg-surface-navy-raised/30 space-y-1">
                        <div className="flex justify-between items-center text-[10px] font-mono text-text-cream-muted">
                          <span className="font-semibold text-primary">{n.author}</span>
                          <span>{new Date(n.createdAt).toLocaleString()}</span>
                        </div>
                        <p className="text-xs text-text-cream-bright leading-relaxed whitespace-pre-wrap">
                          {n.noteText}
                        </p>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}
        </div>

        {/* Modal Footer: Lifecycle Actions */}
        <div className="p-4 sm:p-6 border-t border-border-gold-subtle bg-surface-navy-raised/60 flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <span className="text-xs font-mono text-text-cream-muted uppercase">Lifecycle Action:</span>
            {event.lifecycleStatus !== 'ACKNOWLEDGED' && (
              <button
                type="button"
                onClick={() => handleStatusChange('ACKNOWLEDGED')}
                disabled={isSubmitting}
                className="px-3 py-1.5 rounded-lg border border-blue-500/40 bg-blue-500/10 text-blue-300 text-xs font-medium hover:bg-blue-500/20 transition-all disabled:opacity-50"
              >
                Acknowledge
              </button>
            )}
            {event.lifecycleStatus !== 'INVESTIGATING' && (
              <button
                type="button"
                onClick={() => handleStatusChange('INVESTIGATING')}
                disabled={isSubmitting}
                className="px-3 py-1.5 rounded-lg border border-purple-500/40 bg-purple-500/10 text-purple-300 text-xs font-medium hover:bg-purple-500/20 transition-all disabled:opacity-50"
              >
                Mark Investigating
              </button>
            )}
            {event.lifecycleStatus !== 'RESOLVED' && (
              <button
                type="button"
                onClick={() => handleStatusChange('RESOLVED')}
                disabled={isSubmitting}
                className="px-3 py-1.5 rounded-lg border border-emerald-500/40 bg-emerald-500/10 text-emerald-300 text-xs font-medium hover:bg-emerald-500/20 transition-all disabled:opacity-50"
              >
                Resolve
              </button>
            )}
            {event.lifecycleStatus !== 'DISMISSED' && (
              <button
                type="button"
                onClick={() => handleStatusChange('DISMISSED')}
                disabled={isSubmitting}
                className="px-3 py-1.5 rounded-lg border border-slate-600/40 bg-slate-600/10 text-slate-400 text-xs font-medium hover:bg-slate-600/20 transition-all disabled:opacity-50"
              >
                Dismiss
              </button>
            )}
          </div>

          <button
            type="button"
            onClick={onClose}
            className="px-4 py-1.5 rounded-lg border border-border-gold-subtle text-text-cream-muted text-xs hover:text-text-cream-bright hover:bg-surface-navy-raised transition-all"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
}

