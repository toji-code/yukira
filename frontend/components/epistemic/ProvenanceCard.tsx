import React from 'react';
import { CalculationRun } from '@/types/calculation';
import { formatDateTime } from '@/lib/utils/formatters';

interface ProvenanceCardProps {
  run: CalculationRun;
  inputObservationCount?: number;
}

/**
 * Provenance panel.
 *
 * A definition-list grid: label above value, value in an inset well. Digests
 * are selectable and wrap across lines — a SHA-256 is never truncated without an
 * affordance to reveal it.
 */
export function ProvenanceCard({ run, inputObservationCount }: ProvenanceCardProps) {
  const executionDurationMs =
    run.executionStartedAt && run.executionCompletedAt
      ? Math.max(
          1,
          new Date(run.executionCompletedAt).getTime() -
            new Date(run.executionStartedAt).getTime()
        )
      : null;

  return (
    <section className="panel p-5" aria-label="Calculation provenance and audit manifest">
      <div className="panel-header border-b border-border pb-3">
        <div>
          <p className="eyebrow">Calculation provenance</p>
          <h3 className="mt-1 text-[15px] font-semibold text-text-primary">
            Audit manifest
          </h3>
        </div>
        <span className="status-badge state-info">Run #{run.id}</span>
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-2">
        <div className="sm:col-span-2">
          <p className="def-label">Input snapshot SHA-256 digest</p>
          <p
            className="def-value select-all"
            title="SHA-256 hash over deterministic input observation series"
          >
            {run.inputSnapshotSha256 || 'Not available'}
          </p>
        </div>

        <div>
          <p className="def-label">Engine software commit</p>
          <p className="def-value">{run.engineSoftwareVersion || 'Not available'}</p>
        </div>

        <div>
          <p className="def-label">Knowledge cutoff (T_cutoff)</p>
          <p className="def-value">{formatDateTime(run.knowledgeCutoffTime)}</p>
        </div>

        <div>
          <p className="def-label">As-of effective date (D)</p>
          <p className="def-value">{run.asOfDate}</p>
        </div>

        <div>
          <p className="def-label">Execution duration</p>
          <p className="def-value">
            {executionDurationMs !== null ? `${executionDurationMs}ms` : 'In progress'}
          </p>
        </div>

        <div className="sm:col-span-2">
          <p className="def-label">Authoritative input linkage</p>
          <p className="def-value">
            {inputObservationCount !== undefined
              ? `${inputObservationCount} exact PIT observations linked`
              : 'Bitemporal single-revision resolution enforced'}
          </p>
        </div>
      </div>
    </section>
  );
}