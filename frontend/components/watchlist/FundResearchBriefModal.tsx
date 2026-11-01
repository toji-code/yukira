'use client';

import React, { useState } from 'react';
import { ResearchBrief } from '@/lib/api/research';

interface Props {
  brief?: ResearchBrief | null;
  isOpen: boolean;
  onClose: () => void;
  isLoading?: boolean;
  fund?: any;
  existingProfile?: any;
  initialSection?: string;
  initialIssueId?: string | null;
  userId?: string;
  onReviewChecklistChange?: () => void;
  onEditNote?: (targetFund: any) => void;
  onUpdateStatus?: (sId: any, sOptId: any, st: any) => Promise<any> | void;
}

export function FundResearchBriefModal({ brief, isOpen, onClose, isLoading }: Props) {
  const [copied, setCopied] = useState(false);

  if (!isOpen) return null;

  const handleCopy = () => {
    if (brief?.markdownDossier) {
      navigator.clipboard.writeText(brief.markdownDossier);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-xs p-4">
      <div className="relative w-full max-w-3xl bg-slate-900 border border-slate-800 rounded-2xl shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="p-6 border-b border-slate-800 flex items-start justify-between">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <span className="font-mono text-xs px-2 py-0.5 rounded bg-emerald-950 text-emerald-400 border border-emerald-800 font-semibold">
                RESEARCH DOSSIER
              </span>
              <span className="text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-300">
                Institutional Due Diligence
              </span>
            </div>
            <h2 className="text-xl font-bold text-slate-100">
              {brief?.optionName || 'Fund Research Brief'}
            </h2>
          </div>
          <div className="flex items-center gap-2">
            {brief && (
              <button
                onClick={handleCopy}
                className="px-3 py-1.5 rounded-lg border border-slate-700 bg-slate-800 hover:bg-slate-700 text-xs font-semibold text-slate-200 transition-colors"
              >
                {copied ? '✓ Copied Markdown' : 'Copy Markdown'}
              </button>
            )}
            <button
              onClick={onClose}
              className="text-slate-400 hover:text-slate-100 p-1.5 rounded-lg hover:bg-slate-800 transition-colors"
            >
              ✕
            </button>
          </div>
        </div>

        {/* Content */}
        <div className="p-6 overflow-y-auto space-y-6 text-sm">
          {isLoading ? (
            <div className="p-16 text-center text-slate-400 animate-pulse">
              Compiling comprehensive quantitative due diligence dossier...
            </div>
          ) : !brief ? (
            <div className="p-16 text-center text-slate-400">
              Unable to generate brief: Instrument metadata or history unavailable.
            </div>
          ) : (
            <>
              {/* Epistemic Alert */}
              <div className="p-4 bg-blue-950/30 border border-blue-800/40 rounded-xl text-blue-300 text-xs leading-relaxed">
                <span className="font-semibold block mb-1">Evidence-Based Platform Mandate:</span>
                This research brief is generated entirely from verified historical NAV time-series, official AMFI
                master registries, and AMC monthly portfolio disclosures. Zero LLM approximations, zero simulated returns.
              </div>

              {/* Formatted Markdown Presentation */}
              <div className="bg-slate-950 p-6 rounded-xl border border-slate-800 font-sans text-slate-200 leading-relaxed whitespace-pre-wrap text-xs md:text-sm">
                {brief.markdownDossier}
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
