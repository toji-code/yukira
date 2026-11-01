'use client';

import React, { useState } from 'react';
import {
  MonitoringConditionDto,
  MonitoringConditionRequest,
  createMonitoringCondition,
} from '@/lib/api/monitoring';

interface CreateConditionModalProps {
  isOpen: boolean;
  onClose: () => void;
  onCreated: (condition: MonitoringConditionDto) => void;
  initialSchemeId?: number;
  initialSchemeOptionId?: number;
  initialSchemeName?: string;
  initialThesisKey?: string;
  initialGuidance?: string;
}

export function CreateConditionModal({
  isOpen,
  onClose,
  onCreated,
  initialSchemeId,
  initialSchemeOptionId,
  initialSchemeName,
  initialThesisKey,
  initialGuidance,
}: CreateConditionModalProps) {
  const [conditionName, setConditionName] = useState(
    initialSchemeName ? `Reconsideration: ${initialSchemeName}` : ''
  );
  const [targetScope, setTargetScope] = useState<'SCHEME_OPTION' | 'PORTFOLIO'>('SCHEME_OPTION');
  const [evidenceField, setEvidenceField] = useState('RSK-03');
  const [operator, setOperator] = useState('LT');
  const [thresholdValue, setThresholdValue] = useState('-15%');
  const [thresholdUnit, setThresholdUnit] = useState('PERCENTAGE');
  const [timeWindow, setTimeWindow] = useState('3Y');
  const [thesisGuidance, setThesisGuidance] = useState(initialGuidance || '');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleFieldChange = (field: string) => {
    setEvidenceField(field);
    switch (field) {
      case 'RSK-03':
        setOperator('LT');
        setThresholdValue('-15%');
        setThresholdUnit('PERCENTAGE');
        setTimeWindow('3Y');
        break;
      case 'RSK-01':
        setOperator('GT');
        setThresholdValue('20%');
        setThresholdUnit('PERCENTAGE');
        setTimeWindow('3Y');
        break;
      case 'RSK-02':
        setOperator('GT');
        setThresholdValue('15%');
        setThresholdUnit('PERCENTAGE');
        setTimeWindow('3Y');
        break;
      case 'RET-03':
        setOperator('LT');
        setThresholdValue('0%');
        setThresholdUnit('PERCENTAGE');
        setTimeWindow('3Y');
        break;
      case 'MKT-01':
        setOperator('GT');
        setThresholdValue('1.20');
        setThresholdUnit('RATIO');
        setTimeWindow('3Y');
        break;
      case 'MKT-02':
        setOperator('GT');
        setThresholdValue('1.10');
        setThresholdUnit('RATIO');
        setTimeWindow('3Y');
        break;
      case 'PRT-01':
      case 'HOLDINGS_CONCENTRATION_TOP10':
        setOperator('ABS_CHANGE_GT');
        setThresholdValue('3.0%');
        setThresholdUnit('PERCENTAGE');
        setTimeWindow('MONTHLY');
        break;
      case 'CONC-01':
        setOperator('GT');
        setThresholdValue('35.0%');
        setThresholdUnit('PERCENTAGE');
        setTimeWindow('MONTHLY');
        break;
      case 'NAV_FRESHNESS':
        setOperator('GT');
        setThresholdValue('4');
        setThresholdUnit('DAYS');
        setTimeWindow('POINT_IN_TIME');
        break;
      case 'HOLDINGS_FRESHNESS':
        setOperator('GT');
        setThresholdValue('40');
        setThresholdUnit('DAYS');
        setTimeWindow('POINT_IN_TIME');
        break;
      case 'PORTFOLIO_DRIFT':
        setOperator('GT');
        setThresholdValue('5.0%');
        setThresholdUnit('PERCENTAGE');
        setTimeWindow('POINT_IN_TIME');
        break;
      case 'PORTFOLIO_UNVALUED_POSITIONS':
        setOperator('GT');
        setThresholdValue('0');
        setThresholdUnit('COUNT');
        setTimeWindow('POINT_IN_TIME');
        break;
      default:
        setOperator('GT');
        setThresholdValue('0.20');
        setThresholdUnit('PERCENTAGE');
        setTimeWindow('3Y');
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!conditionName.trim()) {
      setError('Please provide a descriptive condition name.');
      return;
    }

    setIsSubmitting(true);
    setError(null);

    try {
      // Parse numeric representation from user input (stripping % or days)
      const cleanNumericStr = thresholdValue.replace(/[^0-9.-]/g, '');
      const numericVal = parseFloat(cleanNumericStr);

      const req: MonitoringConditionRequest = {
        conditionName: conditionName.trim(),
        targetScope,
        schemeId: initialSchemeId,
        schemeOptionId: initialSchemeOptionId,
        evidenceField,
        operator,
        thresholdValue: thresholdValue.trim(),
        thresholdNumeric: !isNaN(numericVal) ? numericVal : undefined,
        thresholdUnit,
        thresholdClassification: 'INVESTOR_DEFINED_THRESHOLD',
        timeWindow,
        associatedThesisKey: initialThesisKey,
        thesisReconsiderationGuidance: thesisGuidance.trim() || undefined,
        isEnabled: true,
      };

      const created = await createMonitoringCondition(req);
      onCreated(created);
      onClose();
    } catch (err: unknown) {
      console.error('Failed to create condition', err);
      setError((err as { message?: string })?.message || 'Failed to save monitoring condition.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md"
      role="dialog"
      aria-modal="true"
      aria-labelledby="create-condition-title"
    >
      <div className="relative w-full max-w-xl rounded-2xl border border-border-gold-subtle bg-surface-midnight shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between p-6 border-b border-border-gold-subtle bg-surface-navy-raised/40">
          <div>
            <h3 id="create-condition-title" className="text-lg font-bold text-text-cream-bright tracking-tight">
              Create Monitoring / Reconsideration Rule
            </h3>
            <p className="text-xs text-text-cream-muted mt-0.5">
              Deterministic rule evaluating continuous evidence drift against authoritative metric standards.
            </p>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 text-text-cream-muted hover:text-text-cream-bright rounded-lg hover:bg-surface-navy-raised"
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {error && (
            <div className="p-3 rounded-lg border border-rose-500/30 bg-rose-500/10 text-xs text-rose-300">
              {error}
            </div>
          )}

          {/* Condition Name */}
          <div className="space-y-1.5">
            <label className="block text-xs font-mono font-semibold uppercase tracking-wider text-text-cream-muted">
              Condition Name *
            </label>
            <input
              type="text"
              value={conditionName}
              onChange={(e) => setConditionName(e.target.value)}
              placeholder="e.g. Invalidate thesis if 3Y maximum drawdown exceeds -15%"
              className="w-full p-2.5 rounded-lg border border-border-gold-subtle bg-surface-navy-raised/40 text-xs text-text-cream-bright placeholder-text-cream-muted/50 focus:outline-none focus:ring-1 focus:ring-primary"
              required
            />
          </div>

          {/* Target Scope & Evidence Field */}
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <label className="block text-xs font-mono font-semibold uppercase tracking-wider text-text-cream-muted">
                Target Scope
              </label>
              <select
                value={targetScope}
                onChange={(e) => setTargetScope(e.target.value as 'SCHEME_OPTION' | 'PORTFOLIO')}
                className="w-full p-2.5 rounded-lg border border-border-gold-subtle bg-surface-navy-raised/40 text-xs text-text-cream-bright focus:outline-none focus:ring-1 focus:ring-primary"
              >
                <option value="SCHEME_OPTION">Watched Fund / Share Class</option>
                <option value="PORTFOLIO">Consolidated Portfolio</option>
              </select>
            </div>

            <div className="space-y-1.5">
              <label className="block text-xs font-mono font-semibold uppercase tracking-wider text-text-cream-muted">
                Evidence Field (Authoritative) *
              </label>
              <select
                value={evidenceField}
                onChange={(e) => handleFieldChange(e.target.value)}
                className="w-full p-2.5 rounded-lg border border-border-gold-subtle bg-surface-navy-raised/40 text-xs text-text-cream-bright focus:outline-none focus:ring-1 focus:ring-primary"
              >
                <optgroup label="Authoritative Risk Metrics">
                  <option value="RSK-03">RSK-03: Maximum Drawdown (3Y)</option>
                  <option value="RSK-01">RSK-01: Annualized Volatility (3Y)</option>
                  <option value="RSK-02">RSK-02: Downside Semideviation (3Y)</option>
                  <option value="RSK-04">RSK-04: Drawdown Duration (Days)</option>
                </optgroup>
                <optgroup label="Authoritative Return & Efficiency Metrics">
                  <option value="RET-03">RET-03: 3Y CAGR (Julian 365.25)</option>
                  <option value="RET-02">RET-02: Simple Period Return</option>
                  <option value="RAT-01">RAT-01: Sharpe Ratio (3Y)</option>
                  <option value="RAT-02">RAT-02: Sortino Ratio (3Y)</option>
                  <option value="MKT-01">MKT-01: Beta (3Y)</option>
                  <option value="MKT-02">MKT-02: Downside Beta (3Y)</option>
                </optgroup>
                <optgroup label="Holdings & Portfolio Construction">
                  <option value="PRT-01">PRT-01: Top-10 Concentration Disclosure Shift</option>
                  <option value="CONC-01">CONC-01: Top-5 Holdings Concentration</option>
                  <option value="PORTFOLIO_DRIFT">PORTFOLIO_DRIFT: Target Allocation Drift</option>
                  <option value="PORTFOLIO_UNVALUED_POSITIONS">PORTFOLIO_UNVALUED_POSITIONS: Unvalued Positions Count</option>
                </optgroup>
                <optgroup label="Data Quality & Freshness">
                  <option value="NAV_FRESHNESS">NAV_FRESHNESS: NAV Observation Freshness (Days)</option>
                  <option value="HOLDINGS_FRESHNESS">HOLDINGS_FRESHNESS: Monthly Disclosure Freshness (Days)</option>
                  <option value="UNCLASSIFIED_EXPOSURE">UNCLASSIFIED_EXPOSURE: Unclassified Holdings Exposure (%)</option>
                </optgroup>
              </select>
            </div>
          </div>

          {/* Unit Convention & Sign Note */}
          {evidenceField === 'RSK-03' && (
            <div className="p-2.5 rounded-lg border border-primary/30 bg-primary/10 text-[11px] text-primary flex items-start gap-2">
              <span className="font-bold">Unit Convention:</span>
              <span>
                Maximum Drawdown uses negative convention. Breaches occur when drawdown deepens past a negative threshold (e.g. &lt; -15.00%).
              </span>
            </div>
          )}

          {/* Operator and Threshold */}
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <label className="block text-xs font-mono font-semibold uppercase tracking-wider text-text-cream-muted">
                Comparison Operator *
              </label>
              <select
                value={operator}
                onChange={(e) => setOperator(e.target.value)}
                className="w-full p-2.5 rounded-lg border border-border-gold-subtle bg-surface-navy-raised/40 text-xs text-text-cream-bright focus:outline-none focus:ring-1 focus:ring-primary"
              >
                <option value="LT">&lt; Less Than (Breach below threshold)</option>
                <option value="LTE">&lt;= Less Than or Equal</option>
                <option value="GT">&gt; Greater Than (Breach above threshold)</option>
                <option value="GTE">&gt;= Greater Than or Equal</option>
                <option value="ABS_CHANGE_GT">Absolute Change &gt;</option>
                <option value="DATA_UNAVAILABLE">Evidence Unavailable / Missing</option>
                <option value="EVIDENCE_STALE">Evidence Becoming Stale</option>
              </select>
            </div>

            <div className="space-y-1.5">
              <label className="block text-xs font-mono font-semibold uppercase tracking-wider text-text-cream-muted">
                Threshold Value *
              </label>
              <input
                type="text"
                value={thresholdValue}
                onChange={(e) => setThresholdValue(e.target.value)}
                placeholder="e.g. -15% or 20%"
                className="w-full p-2.5 rounded-lg border border-border-gold-subtle bg-surface-navy-raised/40 text-xs text-text-cream-bright placeholder-text-cream-muted/50 focus:outline-none focus:ring-1 focus:ring-primary"
                required
              />
            </div>
          </div>

          {/* Reconsideration Guidance */}
          <div className="space-y-1.5">
            <label className="block text-xs font-mono font-semibold uppercase tracking-wider text-text-cream-muted">
              Thesis Reconsideration Guidance (Optional)
            </label>
            <textarea
              rows={2}
              value={thesisGuidance}
              onChange={(e) => setThesisGuidance(e.target.value)}
              placeholder="What should be investigated if this condition triggers? (e.g. verify if drawdown is peer-wide or fund idiosyncratic)"
              className="w-full p-2.5 rounded-lg border border-border-gold-subtle bg-surface-navy-raised/40 text-xs text-text-cream-bright placeholder-text-cream-muted/50 focus:outline-none focus:ring-1 focus:ring-primary"
            />
          </div>

          <div className="pt-2 flex items-center justify-end gap-3">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 rounded-lg border border-border-gold-subtle text-xs text-text-cream-muted hover:text-text-cream-bright"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="px-5 py-2 rounded-lg bg-primary text-surface-midnight font-bold text-xs uppercase tracking-wider hover:bg-primary/90 transition-all disabled:opacity-50"
            >
              {isSubmitting ? 'Saving...' : 'Create Rule'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
