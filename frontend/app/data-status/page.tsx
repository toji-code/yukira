import type { Metadata } from 'next';
import { DataStatusDashboard } from '@/components/status/DataStatusDashboard';

export const metadata: Metadata = {
  title: 'Market Data Freshness & Ingestion Status | YUKIRA',
  description:
    'Institutional audit status of mutual fund NAVs, benchmark TRIs, scheme universe synchronization, and holdings disclosure availability.',
};

export default function DataStatusPage() {
  return (
    <main className="min-h-screen bg-surface-midnight text-text-cream-body">
      <DataStatusDashboard />
    </main>
  );
}

