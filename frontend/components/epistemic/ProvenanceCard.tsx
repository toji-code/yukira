import React from 'react';
import { CalculationRun } from '@/types/calculation';
import { formatDateTime } from '@/lib/utils/formatters';

interface ProvenanceCardProps {
  run: CalculationRun;
  inputObservationCount?: number;
}

export function ProvenanceCard({ run, inputObservationCount }: ProvenanceCardProps) {
  return (
    <div className="rounded-lg border border-border bg-surface p-5 font-mono text-xs transition-colors">
      <div className="flex items-center justify-between pb-3 border-b border-border mb-4">
        <div className="flex items-center gap-2">
          <span className="w-2 h-2 rounded-full bg-emerald-500" />
          <span className="font-semibold text-text-primary uppercase tracking-wider text-[11px]">
            Calculation Provenance & Audit Manifest
          </span>
        </div>
        <span className="px-2 py-0.5 rounded bg-surface-elevated border border-border text-text-secondary text-[10px]">
          RUN #{run.id}
        </span>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-text-secondary">
        <div>
          <span className="text-[10px] uppercase text-text-muted block mb-0.5">
            Input Snapshot SHA-256 Digest
          </span>
          <span
            className="font-bold text-text-primary break-all select-all block bg-surface-elevated p-1.5 rounded border border-border"
            title="SHA-256 hash over deterministic input observation series"
          >
            {run.inputSnapshotSha256 || '—'}
          </span>
        </div>

        <div>
          <span className="text-[10px] uppercase text-text-muted block mb-0.5">
            Engine Software Commit SHA
          </span>
          <span className="font-bold text-text-primary block bg-surface-elevated p-1.5 rounded border border-border">
            {run.engineSoftwareVersion || '—'}
          </span>
        </div>

        <div>
          <span className="text-[10px] uppercase text-text-muted block mb-0.5">
            Knowledge Cutoff Timestamp (T_cutoff)
          </span>
          <span className="font-medium text-text-primary">
            {formatDateTime(run.knowledgeCutoffTime)}
          </span>
        </div>

        <div>
          <span className="text-[10px] uppercase text-text-muted block mb-0.5">
            As-Of Effective Date (D)
          </span>
          <span className="font-medium text-text-primary">{run.asOfDate}</span>
        </div>

        <div>
          <span className="text-[10px] uppercase text-text-muted block mb-0.5">
            Execution Duration
          </span>
          <span className="font-medium text-text-primary">
            {run.executionStartedAt && run.executionCompletedAt
              ? `${Math.max(
                  1,
                  new Date(run.executionCompletedAt).getTime() -
                    new Date(run.executionStartedAt).getTime()
                )}ms`
              : 'In progress'}
          </span>
        </div>

        <div>
          <span className="text-[10px] uppercase text-text-muted block mb-0.5">
            Authoritative Input Linkage
          </span>
          <span className="font-medium text-text-primary">
            {inputObservationCount !== undefined
              ? `${inputObservationCount} exact PIT observations linked`
              : 'Bitemporal single-revision resolution enforced'}
          </span>
        </div>
      </div>
    </div>
  );
}
