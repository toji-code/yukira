"use client";

import { useState, useMemo } from "react";
import {
  SavedResearchView,
  WorkspaceConfiguration,
  validateViewName,
  generateViewId,
  isViewActive,
  isDefaultWorkspace,
  formatViewSummary,
  persistSavedViews,
} from "@/lib/utils/savedResearchViews";

export interface SavedViewsBarProps {
  currentConfig: WorkspaceConfiguration;
  savedViews: SavedResearchView[];
  activeSavedViewId: string | null;
  userId?: string | null;
  onApplyView: (view: SavedResearchView) => void;
  onResetDefault: () => void;
  onSavedViewsChange: (updatedViews: SavedResearchView[]) => void;
  onSetActiveSavedViewId: (id: string | null) => void;
  onNotify: (message: string, kind?: "success" | "info" | "warning") => void;
}

export function SavedViewsBar({
  currentConfig,
  savedViews,
  activeSavedViewId,
  userId,
  onApplyView,
  onResetDefault,
  onSavedViewsChange,
  onSetActiveSavedViewId,
  onNotify,
}: SavedViewsBarProps) {
  // Modal states
  const [isSaveModalOpen, setIsSaveModalOpen] = useState(false);
  const [isRenameModalOpen, setIsRenameModalOpen] = useState(false);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [isUpdateModalOpen, setIsUpdateModalOpen] = useState(false);

  // Target view for rename / delete / update
  const [selectedTargetView, setSelectedTargetView] = useState<SavedResearchView | null>(null);

  // Form input states
  const [viewNameInput, setViewNameInput] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Dropdown menu state
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);

  // Identify matching view if current configuration matches any saved view strictly
  const strictlyMatchingView = useMemo(() => {
    return savedViews.find((v) => isViewActive(v, currentConfig)) || null;
  }, [savedViews, currentConfig]);

  // Determine if active loaded view has been modified by the investor
  const loadedView = useMemo(() => {
    return savedViews.find((v) => v.id === activeSavedViewId) || null;
  }, [savedViews, activeSavedViewId]);

  const isLoadedViewModified = useMemo(() => {
    if (!loadedView) return false;
    return !isViewActive(loadedView, currentConfig);
  }, [loadedView, currentConfig]);

  const isDefaultActive = isDefaultWorkspace(currentConfig);

  // Open Save View Modal
  const handleOpenSaveModal = () => {
    setViewNameInput("");
    setFormError(null);
    setIsSaveModalOpen(true);
    setIsDropdownOpen(false);
  };

  // Open Rename Modal
  const handleOpenRenameModal = (view: SavedResearchView) => {
    setSelectedTargetView(view);
    setViewNameInput(view.name);
    setFormError(null);
    setIsRenameModalOpen(true);
    setIsDropdownOpen(false);
  };

  // Open Delete Modal
  const handleOpenDeleteModal = (view: SavedResearchView) => {
    setSelectedTargetView(view);
    setIsDeleteModalOpen(true);
    setIsDropdownOpen(false);
  };

  // Open Update Modal
  const handleOpenUpdateModal = (view: SavedResearchView) => {
    setSelectedTargetView(view);
    setIsUpdateModalOpen(true);
    setIsDropdownOpen(false);
  };

  // Submit Save View
  const handleSaveViewSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (isSubmitting) return;

    const validation = validateViewName(viewNameInput, savedViews);
    if (!validation.valid) {
      setFormError(validation.error || "Invalid view name.");
      return;
    }

    setIsSubmitting(true);
    const nowIso = new Date().toISOString();
    const newView: SavedResearchView = {
      id: generateViewId(),
      name: viewNameInput.trim(),
      description: formatViewSummary(currentConfig),
      searchQuery: currentConfig.searchQuery.trim(),
      filterTab: currentConfig.filterTab,
      selectedAmc: currentConfig.selectedAmc,
      sortBy: currentConfig.sortBy,
      createdAt: nowIso,
      updatedAt: nowIso,
    };

    const updated = [newView, ...savedViews];
    const success = persistSavedViews(updated, userId);
    if (success) {
      onSavedViewsChange(updated);
      onSetActiveSavedViewId(newView.id);
      setIsSaveModalOpen(false);
      onNotify(`Saved research view "${newView.name}" created.`, "success");
    } else {
      setFormError("Failed to persist view to local storage. Storage quota may be exceeded.");
    }
    setIsSubmitting(false);
  };

  // Submit Rename View
  const handleRenameViewSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedTargetView || isSubmitting) return;

    const validation = validateViewName(viewNameInput, savedViews, selectedTargetView.id);
    if (!validation.valid) {
      setFormError(validation.error || "Invalid view name.");
      return;
    }

    setIsSubmitting(true);
    const nowIso = new Date().toISOString();
    const updated = savedViews.map((v) =>
      v.id === selectedTargetView.id
        ? {
            ...v,
            name: viewNameInput.trim(),
            updatedAt: nowIso,
          }
        : v
    );

    const success = persistSavedViews(updated, userId);
    if (success) {
      onSavedViewsChange(updated);
      setIsRenameModalOpen(false);
      onNotify(`View renamed to "${viewNameInput.trim()}".`, "info");
    } else {
      setFormError("Failed to persist rename changes.");
    }
    setIsSubmitting(false);
  };

  // Submit Update View Configuration
  const handleConfirmUpdate = () => {
    if (!selectedTargetView || isSubmitting) return;
    setIsSubmitting(true);

    const nowIso = new Date().toISOString();
    const updated = savedViews.map((v) =>
      v.id === selectedTargetView.id
        ? {
            ...v,
            searchQuery: currentConfig.searchQuery.trim(),
            filterTab: currentConfig.filterTab,
            selectedAmc: currentConfig.selectedAmc,
            sortBy: currentConfig.sortBy,
            description: formatViewSummary(currentConfig),
            updatedAt: nowIso,
          }
        : v
    );

    const success = persistSavedViews(updated, userId);
    if (success) {
      onSavedViewsChange(updated);
      setIsUpdateModalOpen(false);
      onNotify(`Updated view "${selectedTargetView.name}" with current search & filters.`, "success");
    } else {
      onNotify("Failed to update saved view in storage.", "warning");
    }
    setIsSubmitting(false);
  };

  // Submit Delete View
  const handleConfirmDelete = () => {
    if (!selectedTargetView || isSubmitting) return;
    setIsSubmitting(true);

    const updated = savedViews.filter((v) => v.id !== selectedTargetView.id);
    const success = persistSavedViews(updated, userId);
    if (success) {
      onSavedViewsChange(updated);
      if (activeSavedViewId === selectedTargetView.id) {
        onSetActiveSavedViewId(null);
      }
      setIsDeleteModalOpen(false);
      onNotify(`Saved view "${selectedTargetView.name}" deleted.`, "info");
    } else {
      onNotify("Failed to delete saved view from storage.", "warning");
    }
    setIsSubmitting(false);
  };

  return (
    <div
      role="region"
      aria-label="Saved Research Views"
      className="p-3 border-b border-border bg-surface-raised/40 flex flex-wrap items-center justify-between gap-2.5 text-xs font-mono"
    >
      {/* Left: View Selector & Saved View Chips */}
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-[10.5px] uppercase font-bold text-accent tracking-wider flex items-center gap-1.5 mr-1">
          <span>📁</span>
          <span>Workspace Views:</span>
        </span>

        {/* Default View Button */}
        <button
          type="button"
          onClick={onResetDefault}
          className={`px-2.5 py-1 rounded text-[11px] font-semibold border transition-all flex items-center gap-1.5 ${
            isDefaultActive && !strictlyMatchingView
              ? "bg-accent/20 border-accent/60 text-accent font-bold ring-1 ring-accent/30"
              : "bg-surface text-text-secondary border-border/80 hover:text-text-primary hover:border-text-secondary"
          }`}
          title="Reset to default unfiltered workspace view"
        >
          <span>All Funds (Default)</span>
          {isDefaultActive && !strictlyMatchingView && (
            <span className="text-[9px] bg-accent/30 text-accent px-1 rounded">ACTIVE</span>
          )}
        </button>

        {/* Top 3 Saved Views as Direct Chips */}
        {savedViews.slice(0, 3).map((view) => {
          const isActive = strictlyMatchingView?.id === view.id || activeSavedViewId === view.id;
          const isModified = activeSavedViewId === view.id && isLoadedViewModified;

          return (
            <div key={view.id} className="inline-flex items-center rounded bg-surface border border-border/80">
              <button
                type="button"
                onClick={() => onApplyView(view)}
                className={`px-2.5 py-1 text-[11px] rounded-l font-semibold transition-all flex items-center gap-1.5 ${
                  isActive
                    ? "bg-accent/20 text-accent font-bold"
                    : "text-text-secondary hover:text-text-primary"
                }`}
                title={`Load view: ${view.name} (${formatViewSummary(view)})`}
              >
                <span>{view.name}</span>
                {isActive && !isModified && (
                  <span className="text-[8.5px] bg-accent text-[#1e1704] font-black px-1 rounded uppercase">
                    ACTIVE
                  </span>
                )}
                {isModified && (
                  <span className="text-[8.5px] bg-amber-500/25 text-amber-300 font-bold px-1 rounded uppercase">
                    MODIFIED
                  </span>
                )}
              </button>

              {/* View options button */}
              <button
                type="button"
                onClick={() => handleOpenRenameModal(view)}
                className="px-1.5 py-1 text-text-muted hover:text-text-primary hover:bg-surface-raised border-l border-border/50 text-[10px]"
                title={`Rename or manage "${view.name}"`}
                aria-label={`Manage view ${view.name}`}
              >
                ✎
              </button>
              <button
                type="button"
                onClick={() => handleOpenDeleteModal(view)}
                className="px-1.5 py-1 text-text-muted hover:text-rose-400 hover:bg-surface-raised border-l border-border/50 text-[10px]"
                title={`Delete view "${view.name}"`}
                aria-label={`Delete view ${view.name}`}
              >
                ✕
              </button>
            </div>
          );
        })}

        {/* All Views Dropdown Trigger (when more than 3 exist) */}
        {savedViews.length > 3 && (
          <div className="relative">
            <button
              type="button"
              onClick={() => setIsDropdownOpen((prev) => !prev)}
              className="btn btn-secondary btn-xs text-[11px] flex items-center gap-1 px-2 py-1"
              aria-haspopup="true"
              aria-expanded={isDropdownOpen}
            >
              <span>More Views ({savedViews.length - 3})</span>
              <span className="text-[9px]">▼</span>
            </button>

            {isDropdownOpen && (
              <div
                className="absolute left-0 mt-1 w-72 rounded-lg bg-surface border border-accent/40 shadow-2xl py-1.5 z-40 space-y-0.5"
                role="menu"
              >
                <div className="px-3 py-1 text-[10px] uppercase font-bold text-text-tertiary border-b border-border/50 flex justify-between items-center">
                  <span>Saved Research Views</span>
                  <span className="text-text-muted font-normal">{savedViews.length} total</span>
                </div>
                <div className="max-h-60 overflow-y-auto">
                  {savedViews.map((view) => {
                    const isActive = strictlyMatchingView?.id === view.id;
                    return (
                      <div
                        key={view.id}
                        className="px-3 py-1.5 hover:bg-surface-raised flex items-center justify-between gap-2 border-b border-border/30 last:border-0"
                      >
                        <button
                          type="button"
                          onClick={() => {
                            onApplyView(view);
                            setIsDropdownOpen(false);
                          }}
                          className="text-left flex-1 min-w-0"
                        >
                          <div className="flex items-center gap-1.5">
                            <span className={`font-semibold truncate text-[11.5px] ${isActive ? "text-accent" : "text-text-primary"}`}>
                              {view.name}
                            </span>
                            {isActive && (
                              <span className="text-[8.5px] bg-accent/25 text-accent px-1 rounded font-bold">
                                ACTIVE
                              </span>
                            )}
                          </div>
                          <p className="text-[10px] text-text-tertiary truncate">
                            {formatViewSummary(view)}
                          </p>
                        </button>
                        <div className="flex items-center gap-1 shrink-0">
                          <button
                            type="button"
                            onClick={() => handleOpenRenameModal(view)}
                            className="p-1 text-text-muted hover:text-text-primary text-[11px]"
                            title="Rename view"
                          >
                            ✎
                          </button>
                          <button
                            type="button"
                            onClick={() => handleOpenDeleteModal(view)}
                            className="p-1 text-text-muted hover:text-rose-400 text-[11px]"
                            title="Delete view"
                          >
                            ✕
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}
          </div>
        )}

        {/* Empty State Prompt if 0 saved views exist */}
        {savedViews.length === 0 && (
          <span className="text-[11px] text-text-tertiary italic">
            No saved views yet. Configure search/filters and click &ldquo;+ Save View&rdquo;.
          </span>
        )}
      </div>

      {/* Right: Actions (Save Current View & Update Loaded View) */}
      <div className="flex items-center gap-2">
        {/* If loaded view has been modified, provide quick "Update View" button */}
        {loadedView && isLoadedViewModified && (
          <button
            type="button"
            onClick={() => handleOpenUpdateModal(loadedView)}
            className="btn btn-secondary btn-xs text-amber-300 border-amber-500/40 hover:bg-amber-500/10 text-[11px] flex items-center gap-1"
            title={`Update saved view "${loadedView.name}" with current search & filters`}
          >
            <span>Update &ldquo;{loadedView.name}&rdquo;</span>
          </button>
        )}

        <button
          type="button"
          onClick={handleOpenSaveModal}
          className="btn btn-secondary btn-xs text-accent border-accent/40 hover:bg-accent/10 text-[11px] flex items-center gap-1 font-semibold"
          title="Save the active search query, filter tab, AMC, and sort order as a reusable view"
        >
          <span>+ Save View</span>
        </button>
      </div>

      {/* MODAL 1: Save Workspace View */}
      {isSaveModalOpen && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="save-view-title"
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-xs animate-in fade-in"
        >
          <div className="panel w-full max-w-md bg-surface border-2 border-accent/60 rounded-xl shadow-2xl p-5 space-y-4 font-mono text-xs">
            <div className="flex items-center justify-between border-b border-border/60 pb-3">
              <div className="flex items-center gap-2">
                <span className="text-accent text-base">💾</span>
                <h3 id="save-view-title" className="text-sm font-bold text-text-cream-bright font-sans">
                  Save Workspace View
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setIsSaveModalOpen(false)}
                className="text-text-muted hover:text-text-primary text-sm p-1"
                aria-label="Close save view dialog"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleSaveViewSubmit} className="space-y-4">
              <div>
                <label htmlFor="save-view-name" className="field-label text-[11.5px] block mb-1">
                  View Name <span className="text-accent">*</span>
                </label>
                <input
                  id="save-view-name"
                  type="text"
                  placeholder="e.g. Needs Follow-Up Inquiries, Direct Growth Large Cap"
                  value={viewNameInput}
                  onChange={(e) => {
                    setViewNameInput(e.target.value);
                    if (formError) setFormError(null);
                  }}
                  autoFocus
                  maxLength={60}
                  className="field field-mono w-full text-xs"
                />
                {formError && (
                  <p className="text-rose-400 text-[11px] mt-1.5 font-sans leading-tight">
                    ⚠️ {formError}
                  </p>
                )}
              </div>

              {/* View Configuration Breakdown */}
              <div className="p-3 rounded-lg bg-surface-raised border border-border/80 space-y-1.5 text-[11px]">
                <span className="text-[10px] uppercase font-bold text-text-tertiary block">
                  Configuration to be saved:
                </span>
                <div className="grid grid-cols-2 gap-2 pt-1 text-[11px]">
                  <div>
                    <span className="text-text-muted block text-[10px]">Filter Tab:</span>
                    <strong className="text-text-primary">{currentConfig.filterTab.replace(/_/g, " ")}</strong>
                  </div>
                  <div>
                    <span className="text-text-muted block text-[10px]">Sort Order:</span>
                    <strong className="text-text-primary">{currentConfig.sortBy.replace(/_/g, " ")}</strong>
                  </div>
                  <div>
                    <span className="text-text-muted block text-[10px]">AMC Scope:</span>
                    <strong className="text-text-primary">{currentConfig.selectedAmc}</strong>
                  </div>
                  <div>
                    <span className="text-text-muted block text-[10px]">Search Query:</span>
                    <strong className="text-accent">
                      {currentConfig.searchQuery.trim() ? `"${currentConfig.searchQuery.trim()}"` : "None"}
                    </strong>
                  </div>
                </div>
              </div>

              {/* Epistemic note */}
              <p className="text-[10.5px] text-text-secondary leading-relaxed font-sans">
                Saved views preserve your filter combination in browser storage for quick resumption. They do not freeze financial calculations, alter evidence baselines, or create copies of research records.
              </p>

              <div className="flex items-center justify-end gap-2 pt-2 border-t border-border/60">
                <button
                  type="button"
                  onClick={() => setIsSaveModalOpen(false)}
                  className="btn btn-secondary btn-sm"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={!viewNameInput.trim() || isSubmitting}
                  className="btn btn-primary btn-sm flex items-center gap-1.5 font-semibold"
                >
                  {isSubmitting ? "Saving..." : "Save View"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL 2: Rename Saved View */}
      {isRenameModalOpen && selectedTargetView && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="rename-view-title"
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-xs animate-in fade-in"
        >
          <div className="panel w-full max-w-sm bg-surface border-2 border-accent/60 rounded-xl shadow-2xl p-5 space-y-4 font-mono text-xs">
            <div className="flex items-center justify-between border-b border-border/60 pb-3">
              <h3 id="rename-view-title" className="text-sm font-bold text-text-cream-bright font-sans">
                Rename View: &ldquo;{selectedTargetView.name}&rdquo;
              </h3>
              <button
                type="button"
                onClick={() => setIsRenameModalOpen(false)}
                className="text-text-muted hover:text-text-primary text-sm p-1"
                aria-label="Close rename dialog"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleRenameViewSubmit} className="space-y-4">
              <div>
                <label htmlFor="rename-view-input" className="field-label text-[11.5px] block mb-1">
                  New View Name
                </label>
                <input
                  id="rename-view-input"
                  type="text"
                  value={viewNameInput}
                  onChange={(e) => {
                    setViewNameInput(e.target.value);
                    if (formError) setFormError(null);
                  }}
                  autoFocus
                  maxLength={60}
                  className="field field-mono w-full text-xs"
                />
                {formError && (
                  <p className="text-rose-400 text-[11px] mt-1.5 font-sans leading-tight">
                    ⚠️ {formError}
                  </p>
                )}
              </div>

              <div className="flex items-center justify-end gap-2 pt-2 border-t border-border/60">
                <button
                  type="button"
                  onClick={() => setIsRenameModalOpen(false)}
                  className="btn btn-secondary btn-sm"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={!viewNameInput.trim() || isSubmitting}
                  className="btn btn-primary btn-sm"
                >
                  {isSubmitting ? "Renaming..." : "Save Name"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL 3: Update View Confirmation */}
      {isUpdateModalOpen && selectedTargetView && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="update-view-title"
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-xs animate-in fade-in"
        >
          <div className="panel w-full max-w-md bg-surface border-2 border-accent/60 rounded-xl shadow-2xl p-5 space-y-4 font-mono text-xs">
            <div className="flex items-center justify-between border-b border-border/60 pb-3">
              <h3 id="update-view-title" className="text-sm font-bold text-text-cream-bright font-sans">
                Update View: &ldquo;{selectedTargetView.name}&rdquo;
              </h3>
              <button
                type="button"
                onClick={() => setIsUpdateModalOpen(false)}
                className="text-text-muted hover:text-text-primary text-sm p-1"
                aria-label="Close update dialog"
              >
                ✕
              </button>
            </div>

            <p className="text-[11.5px] text-text-secondary leading-relaxed font-sans">
              Update saved view <strong>&ldquo;{selectedTargetView.name}&rdquo;</strong> with the current workspace configuration?
            </p>

            <div className="p-3 rounded-lg bg-surface-raised border border-border/80 text-[11px] space-y-1">
              <span className="text-[10px] uppercase font-bold text-accent block">
                New Target Configuration:
              </span>
              <p className="text-text-primary font-mono">{formatViewSummary(currentConfig)}</p>
            </div>

            <div className="flex items-center justify-end gap-2 pt-2 border-t border-border/60">
              <button
                type="button"
                onClick={() => setIsUpdateModalOpen(false)}
                className="btn btn-secondary btn-sm"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleConfirmUpdate}
                disabled={isSubmitting}
                className="btn btn-primary btn-sm"
              >
                {isSubmitting ? "Updating..." : "Confirm Update"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* MODAL 4: Delete View Confirmation */}
      {isDeleteModalOpen && selectedTargetView && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="delete-view-title"
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-xs animate-in fade-in"
        >
          <div className="panel w-full max-w-sm bg-surface border-2 border-rose-500/60 rounded-xl shadow-2xl p-5 space-y-4 font-mono text-xs">
            <div className="flex items-center justify-between border-b border-border/60 pb-3">
              <h3 id="delete-view-title" className="text-sm font-bold text-rose-300 font-sans">
                Delete Saved View
              </h3>
              <button
                type="button"
                onClick={() => setIsDeleteModalOpen(false)}
                className="text-text-muted hover:text-text-primary text-sm p-1"
                aria-label="Close delete dialog"
              >
                ✕
              </button>
            </div>

            <p className="text-[11.5px] text-text-secondary leading-relaxed font-sans">
              Are you sure you want to delete saved view <strong>&ldquo;{selectedTargetView.name}&rdquo;</strong>?
            </p>

            <p className="text-[10.5px] text-text-muted font-sans italic">
              Your saved funds, research notes, and due-diligence questions will remain untouched.
            </p>

            <div className="flex items-center justify-end gap-2 pt-2 border-t border-border/60">
              <button
                type="button"
                onClick={() => setIsDeleteModalOpen(false)}
                className="btn btn-secondary btn-sm"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleConfirmDelete}
                disabled={isSubmitting}
                className="btn btn-sm bg-rose-600 hover:bg-rose-500 text-white font-bold px-3 py-1.5 rounded"
              >
                {isSubmitting ? "Deleting..." : "Delete View"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
