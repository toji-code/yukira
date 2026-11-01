# TASKS 80–99 REQUIREMENT-TO-IMPLEMENTATION RECONCILIATION REPORT
**Project YUKIRA — Quantitative Investment Intelligence Platform**  
**Document Status:** Authoritative Implementation Reconciliation Record  
**Target Reference:** Tasks 80 through 99  
**Execution Context:** Task 100 Master Directive Compliance  

---

## 1. Executive Summary

This reconciliation document evaluates the actual codebase implementation across Tasks 80–99. Each evaluation is grounded directly in the verified Java entities, Flyway migrations, REST controllers, quant kernels, and Next.js frontend components.

In accordance with YUKIRA Epistemic Principles:
`IMPLEMENTED ≠ VALIDATED ≠ APPROVED ≠ RETIRED`

No assumed passes or unverified completion assertions are made. Where persistence was ephemeral, where DTOs were used as substitutes for database tables, or where API/UI handoffs contained drift, they are catalogued with exact file references and remediation paths.

---

## 2. Requirement Reconciliation Matrix (Tasks 80–99)

### Task 80: Stress Investigation Finding Repository
- **Original Requirement:** Persistent, durable database repository for historical stress-period investigation findings linking investor, portfolio, episode, finding type, severity, and context.
- **Relevant Implementation Location:**
  - `backend/src/main/resources/db/migration/V37__create_stress_investigation_finding_and_signal_repository.sql`
  - `backend/src/main/java/com/yukira/backend/domain/entity/InvestorStressFinding.java`
  - `backend/src/main/java/com/yukira/backend/repository/InvestorStressFindingRepository.java`
  - `backend/src/main/java/com/yukira/backend/service/portfolio/PortfolioHistoricalStressService.java` (lines 3040–3094)
- **Actual Implemented Behaviour:** Findings are persisted upon stress calculation run completion (`persistStressFinding`). Stores `investor_id`, `portfolio_id`, `finding_id`, `finding_version`, `calculation_run_code`, `snapshot_id`, `episode_code`, `finding_type`, `severity`, `headline`, and `detail`.
- **Persistence Status:** PERSISTED (Table `investor_stress_finding` via Flyway V37).
- **API Availability:** Exposed via `GET /api/v1/investors/portfolio/stress/findings` and `PATCH /api/v1/investors/portfolio/stress/findings/{id}`.
- **Frontend Integration:** Consumed by `PortfolioHistoricalStressSection.tsx` and `PortfolioSharedContextBanner.tsx`.
- **Known Limitations:** Previously lacked transition notes and explicit transition audit logging when an investor or system altered lifecycle state.
- **Missing Work:** Version history retrieval and explicit transition audit logging (closed in Task 101/102 via V38).
- **Status:** PARTIALLY_IMPLEMENTED

---

### Task 81: Finding Lifecycle Management & Authorization
- **Original Requirement:** Enforce finding lifecycle states (`ACTIVE`, `SUPERSEDED`, `RESOLVED`, `DISMISSED`) with investor-level authorization preventing cross-investor modification.
- **Relevant Implementation Location:**
  - `InvestorStressFinding.java` (`lifecycleStatus`, `supersededByFindingId`)
  - `PortfolioHistoricalStressService.java` (`updateFindingLifecycleStatus`, `findActiveSimilarFindings`)
  - `InvestorPortfolioController.java` (`updateStressFindingStatus`)
- **Actual Implemented Behaviour:** Investor ID check ensures only the authenticated owner can mutate lifecycle status. Similar active findings from earlier runs are transitioned to `SUPERSEDED` when newer finding versions are persisted.
- **Persistence Status:** PERSISTED (`investor_stress_finding.lifecycle_status`).
- **API Availability:** `PATCH /api/v1/investors/portfolio/stress/findings/{id}` accepting status updates.
- **Frontend Integration:** Action buttons in findings UI allow investors to resolve or dismiss findings.
- **Known Limitations:** Did not capture structured investor reason notes or distinguish investor-driven resolution from system-driven supersession in a dedicated history ledger.
- **Missing Work:** Dedicated transition history entity `InvestorStressFindingTransition` (addressed in Task 102).
- **Status:** PARTIALLY_IMPLEMENTED

---

### Task 82: Data Quality Signal Tracking
- **Original Requirement:** Durable data quality tracking entity linking source artifacts, revision sequences, signal codes, and six-dimensional quality taxonomy to portfolio stress calculations.
- **Relevant Implementation Location:**
  - `V37__create_stress_investigation_finding_and_signal_repository.sql`
  - `backend/src/main/java/com/yukira/backend/domain/entity/InvestorStressDataQualitySignal.java`
  - `backend/src/main/java/com/yukira/backend/repository/InvestorStressDataQualitySignalRepository.java`
- **Actual Implemented Behaviour:** Stores signal code, dimension (`QUALITY`, `VERIFICATION`, `REVISION`, `FRESHNESS`, `PRESENCE`, `INTEGRITY`), constituent code, source artifact ID, and revision sequence.
- **Persistence Status:** PERSISTED (`investor_stress_data_quality_signal`).
- **API Availability:** Available via stress analysis responses and signal repository queries.
- **Frontend Integration:** Displayed in `PortfolioHistoricalStressSection.tsx` and `EvidenceQualitySummary.tsx`.
- **Known Limitations:** Did not store observation interval start/end or first vs latest detection timestamps.
- **Missing Work:** Detection interval dates and source revision supersession awareness (addressed in Task 103).
- **Status:** PARTIALLY_IMPLEMENTED

---

### Task 83: Lineage Verification Contract: Authoritative Source Artifact Linkage and Gap Tracking
- **Original Requirement:** Establish deterministic source artifact hash linkage and calendar gap detection across constituent NAV feeds during stress episode windows.
- **Relevant Implementation Location:**
  - `PortfolioHistoricalStressService.java` (lines 497–550, `buildStressDataQualitySignals`)
  - `NavObservationRepository.java`
- **Actual Implemented Behaviour:** Inspects constituent trading intervals; detects missing trading days, calendar gaps (> 7 days), and flat streaks (≥ 3 identical NAVs). Links findings to authoritative source artifact IDs where available.
- **Persistence Status:** PERSISTED via `source_artifact_id` column.
- **API Availability:** Context DTO embedded in stress response payload.
- **Frontend Integration:** Disclosed in stress data quality signals.
- **Known Limitations:** Where source artifacts were ingested prior to V2 lineage enforcement, artifact references can be null.
- **Missing Work:** Fallback disclosure when raw source artifact hash is absent.
- **Status:** IMPLEMENTED

---

### Task 84: Point-in-Time Knowledge Cutoff Enforcement in Stress Data Selection
- **Original Requirement:** Guarantee that historical stress episode evaluations query observations strictly satisfying `effective_date <= analysis_cutoff` and `availability_time <= knowledge_cutoff`.
- **Relevant Implementation Location:**
  - `PortfolioHistoricalStressService.java`
  - `NavObservationRepository.java`
  - `BenchmarkObservationRepository.java`
- **Actual Implemented Behaviour:** Queries enforce date boundaries; however, some repository queries lacked explicit bitemporal availability timestamp filters, relying on effective date and revision sequence.
- **Persistence Status:** Bitemporal schema exists in V3 migrations.
- **API Availability:** `asOfDate` and `knowledgeCutoff` parameters accepted across stress endpoints.
- **Frontend Integration:** Date pickers and as-of cutoff selectors on portfolio risk/stress tabs.
- **Known Limitations:** Full bitemporal filtering requires availability timestamps on all ingested historical data, which legacy AMFI NAV series lack.
- **Missing Work:** Explicit disclosure of point-in-time reconstruction boundaries (addressed in Task 104/107).
- **Status:** PARTIALLY_IMPLEMENTED

---

### Task 85: Calculation Manifest Persistence & Replayability Boundaries
- **Original Requirement:** Immutable, content-addressed calculation manifest linking calculation run, investor, portfolio snapshot, input hash, methodology version, governance status, and serialized manifest payload.
- **Relevant Implementation Location:**
  - `V37__create_stress_investigation_finding_and_signal_repository.sql`
  - `backend/src/main/java/com/yukira/backend/domain/entity/InvestorStressCalculationManifest.java`
  - `backend/src/main/java/com/yukira/backend/repository/InvestorStressCalculationManifestRepository.java`
  - `PortfolioHistoricalStressService.java` (`persistCalculationManifest`, `getCalculationManifest`)
- **Actual Implemented Behaviour:** Manifest is serialized as JSON and saved to `investor_stress_calculation_manifest` with input fingerprint, snapshot ID, and calculation run code.
- **Persistence Status:** PERSISTED.
- **API Availability:** `GET /api/v1/investors/portfolio/stress/manifest/{runCode}`.
- **Frontend Integration:** Manifest viewer modal and audit verification components.
- **Known Limitations:** Did not explicitly distinguish reproducible parameter context from complete tick-level replayability.
- **Missing Work:** Replayability boundary disclosure (addressed in Task 104).
- **Status:** IMPLEMENTED

---

### Task 86: Lookback Sensitivity & Multi-Horizon Stress Analysis
- **Original Requirement:** Evaluate portfolio sensitivity across distinct historical horizons (3Y, 5Y, 10Y) and multiple benchmark drawdown regimes.
- **Relevant Implementation Location:**
  - `InvestorPortfolioRiskDiversificationService.java`
  - `quant-engine/src/regimes.py`
  - `AnalyticalProfileResponse.java` (`ProfileContext.horizon`)
- **Actual Implemented Behaviour:** Computes risk and stress across 3Y frozen baseline, with candidate extensions for 5Y and 10Y.
- **Persistence Status:** Stored in calculation runs and metric results.
- **API Availability:** `POST /api/v1/analysis/profile?horizon=3Y|5Y|10Y`.
- **Frontend Integration:** Horizon selector pill in fund profile and portfolio analytics.
- **Known Limitations:** Longer horizons require deeper history; pre-inception constituents are excluded with explicit evidence warnings.
- **Missing Work:** None.
- **Status:** IMPLEMENTED

---

### Task 87: Euler Capital-Risk Attribution & Standalone Extremum Disclaimers
- **Original Requirement:** Vectorized Euler risk decomposition isolating constituent marginal risk contributions while explicitly disclosing that standalone extrema do not aggregate linearly.
- **Relevant Implementation Location:**
  - `InvestorPortfolioRiskDiversificationService.java`
  - `quant-engine/src/`
- **Actual Implemented Behaviour:** Calculates Euler marginal volatility contributions; reconciles additive decomposition against portfolio volatility within numerical tolerance; emits `NUMERICALLY_INVALID` if un-reconciled.
- **Persistence Status:** Computed on-demand and logged in calculation runs.
- **API Availability:** `POST /api/v1/investors/portfolio/risk`.
- **Frontend Integration:** `PerformanceAttributionSection.tsx` and `PortfolioRiskDiversificationSection.tsx`.
- **Known Limitations:** Requires full covariance matrix across all included constituents.
- **Missing Work:** None.
- **Status:** IMPLEMENTED

---

### Task 88: Stress Regime Identification & Carino Discrepancy Reconciliation
- **Original Requirement:** Non-overlapping benchmark drawdown episode detection (≥ 10% decline) and Carino logarithmic multi-period attribution with 0.0001% tolerance.
- **Relevant Implementation Location:**
  - `PortfolioHistoricalStressService.java` (`evaluateCarinoLogarithmicFactor`, lines 1120–1200)
- **Actual Implemented Behaviour:** Numerically stable Carino factor $k_t = \ln(1+R_{p,t})/R_{p,t}$ with Taylor series expansion for $|R| < 10^{-6}$. Withholds additive contributions if reconciliation exceeds 0.0001% tolerance.
- **Persistence Status:** Episode outcomes persisted in calculation manifests.
- **API Availability:** Exposed in `PortfolioStressEpisodeDto`.
- **Frontend Integration:** Episode table and Carino reconciliation badge in UI.
- **Known Limitations:** None.
- **Missing Work:** None.
- **Status:** IMPLEMENTED

---

### Task 89: Direct Finding-to-Monitoring Handoff
- **Original Requirement:** Enable one-click handoff from an active stress finding into a continuous monitoring condition requiring explicit investor confirmation.
- **Relevant Implementation Location:**
  - `V37__create_stress_investigation_finding_and_signal_repository.sql` (`portfolio_id`, `stress_finding_id` on `investor_monitoring_condition`)
  - `InvestorMonitoringService.java` (`createConditionFromStressFinding`)
  - `InvestorMonitoringController.java` (`POST /api/v1/monitoring/conditions/from-finding`)
  - `CreateConditionFromFindingRequest.java`
- **Actual Implemented Behaviour:** Accepts finding ID, validates investor ownership, extracts threshold and metric parameters, and creates an active or draft monitoring condition.
- **Persistence Status:** PERSISTED in `investor_monitoring_condition`.
- **API Availability:** `POST /api/v1/monitoring/conditions/from-finding`.
- **Frontend Integration:** "Monitor This Risk" button on stress finding cards.
- **Known Limitations:** Condition activation must strictly require explicit investor confirmation, not activate silently.
- **Missing Work:** Reinforced investor confirmation workflow (Task 128).
- **Status:** IMPLEMENTED

---

### Task 90: Watchlist Thesis Tracking & Association with Findings
- **Original Requirement:** Link saved watchlist items to specific research investigation questions, next steps, and stress finding IDs.
- **Relevant Implementation Location:**
  - `V37__create_stress_investigation_finding_and_signal_repository.sql` (`thesis_status`, `associated_finding_id` on `investor_watchlist`)
  - `InvestorWatchlist.java`
  - `InvestorService.java`
- **Actual Implemented Behaviour:** Watchlist records store `reasonSaved`, `investigationQuestion`, `nextStep`, `thesisStatus`, and `associatedFindingId`.
- **Persistence Status:** PERSISTED in `investor_watchlist`.
- **API Availability:** `POST /api/v1/investor/watchlist/note`, `GET /api/v1/investor/watchlist`.
- **Frontend Integration:** `ResearchNoteModal.tsx`, `WatchlistView.tsx`.
- **Known Limitations:** Updating a thesis overwrote previous notes without version history.
- **Missing Work:** Versioned thesis history table `investor_research_thesis_history` (addressed in Task 120 via V38).
- **Status:** PARTIALLY_IMPLEMENTED

---

### Task 91: Continuous Monitoring Condition Evaluation & Execution Ledger
- **Original Requirement:** Scheduled and on-demand evaluation of investor monitoring conditions against newly ingested data with persistent execution ledger.
- **Relevant Implementation Location:**
  - `V34__create_continuous_monitoring_model.sql`
  - `V35__create_monitoring_execution_ledger.sql`
  - `InvestorMonitoringExecution.java`
  - `InvestorMonitoringEvent.java`
  - `InvestorMonitoringService.java`
- **Actual Implemented Behaviour:** Evaluates conditions against latest metric calculations; records executions and events with full audit lineage.
- **Persistence Status:** PERSISTED.
- **API Availability:** `/api/v1/monitoring/*`.
- **Frontend Integration:** `/monitoring` dashboard.
- **Known Limitations:** Background scheduler requires trigger mechanisms.
- **Missing Work:** Linkage between triggered monitoring events and origin research thesis (Task 128).
- **Status:** IMPLEMENTED

---

### Task 92: Portfolio Historical Stress UI Integration & Shared Context Banner
- **Original Requirement:** Seamless UI integration of stress analysis with shared allocation snapshot context banner.
- **Relevant Implementation Location:**
  - `frontend/components/portfolio/PortfolioSharedContextBanner.tsx`
  - `frontend/components/portfolio/PortfolioHistoricalStressSection.tsx`
- **Actual Implemented Behaviour:** Displays snapshot ID, as-of date, knowledge cutoff, coverage percentage, and active findings.
- **Persistence Status:** UI component consuming API DTOs.
- **API Availability:** Consumes `/api/v1/investors/portfolio/risk` and `/stress`.
- **Frontend Integration:** Integrated into portfolio analytics view.
- **Known Limitations:** Minor banner duplication across risk and stress sub-views.
- **Missing Work:** Harmonize context display to avoid redundant banner rendering (Task 109).
- **Status:** IMPLEMENTED

---

### Task 93: Stress Findings Drawer & Evidence Inspection Component
- **Original Requirement:** Side drawer allowing deep inspection of stress calculation evidence, Carino factors, constituent returns, and benchmark trajectories.
- **Relevant Implementation Location:**
  - `frontend/components/portfolio/`
  - `frontend/components/epistemic/EvidenceDrawer.tsx`
- **Actual Implemented Behaviour:** Opens drawer displaying empirical facts, formula specifications, observation counts, and limitations.
- **Persistence Status:** Presentation tier.
- **API Availability:** Consumes metric registry and calculation run endpoints.
- **Frontend Integration:** Integrated with metric cards and finding items.
- **Known Limitations:** None.
- **Missing Work:** None.
- **Status:** IMPLEMENTED

---

### Task 94: Signal Alerting & Source Revision Notification in UI
- **Original Requirement:** Alert investor when underlying market data has been retroactively revised by the source publisher (e.g. AMFI NAV revision).
- **Relevant Implementation Location:**
  - `frontend/components/portfolio/DataFreshnessIndicator.tsx`
  - `frontend/components/analysis/DataQualityFreshnessSection.tsx`
- **Actual Implemented Behaviour:** Shows data freshness badges, stale warnings, and source revision indicators.
- **Persistence Status:** Backed by `source_artifact` and `investor_stress_data_quality_signal`.
- **API Availability:** `/api/v1/analysis/quality/{schemeOptionId}`.
- **Frontend Integration:** Rendered on fund profile and portfolio screens.
- **Known Limitations:** UI lacked one-click comparison between original and revised observation values.
- **Missing Work:** Enhanced revision context presentation (Task 103/119).
- **Status:** PARTIALLY_IMPLEMENTED

---

### Task 95: Calculation Manifest Viewer & Audit Trail Verification
- **Original Requirement:** Interactive audit viewer displaying raw cryptographic SHA-256 fingerprints, calculation inputs, constituent weights, and methodology policies.
- **Relevant Implementation Location:**
  - `InvestorStressCalculationManifestDto.java`
  - Frontend manifest inspection dialogs.
- **Actual Implemented Behaviour:** Fetches stored manifest JSON by run code and displays structured audit fields.
- **Persistence Status:** PERSISTED in `investor_stress_calculation_manifest`.
- **API Availability:** `GET /api/v1/investors/portfolio/stress/manifest/{runCode}`.
- **Frontend Integration:** Available from calculation provenance buttons.
- **Known Limitations:** None.
- **Missing Work:** None.
- **Status:** IMPLEMENTED

---

### Task 96: Research Workspace Finding-to-Thesis Linkage & Status Transitions
- **Original Requirement:** Research workspace managing thesis lifecycle (`ACTIVE_HYPOTHESIS`, `CONFIRMED`, `REFUTED`, `ABANDONED`) directly tied to empirical findings.
- **Relevant Implementation Location:**
  - `V26__create_investor_research_workspace_model.sql`
  - `V37__create_stress_investigation_finding_and_signal_repository.sql`
  - `frontend/components/watchlist/ResearchFollowUpWorkspace.tsx`
  - `frontend/components/watchlist/ResearchDossierModal.tsx`
- **Actual Implemented Behaviour:** Workspace lists research items with investigation questions, next steps, and linked findings.
- **Persistence Status:** PERSISTED.
- **API Availability:** `/api/v1/investor/watchlist`.
- **Frontend Integration:** Research workspace views and dossier export.
- **Known Limitations:** System did not prevent automated thesis transitions based solely on model outputs (violating investor authorship rules).
- **Missing Work:** Strict investor authorship guardrails (Task 120/128).
- **Status:** PARTIALLY_IMPLEMENTED

---

### Task 97: Comparative Screener Compatibility & Benchmark Integrity
- **Original Requirement:** Multi-fund comparative screener preventing comparisons between incompatible fund categories or substituting PRIs for TRIs.
- **Relevant Implementation Location:**
  - `frontend/components/comparison/ComparativeScreener.tsx`
  - `frontend/components/comparison/ComparisonCompatibilityBanner.tsx`
  - `AnalysisController.java` (`POST /api/v1/analysis/compare`)
- **Actual Implemented Behaviour:** Checks category compatibility, common trading date intersection, and primary benchmark alignment. Displays compatibility warnings when funds belong to divergent SEBI categories.
- **Persistence Status:** Ephemeral comparison calculation leveraging persisted metric results.
- **API Availability:** `POST /api/v1/analysis/compare`.
- **Frontend Integration:** `/compare` page.
- **Known Limitations:** Did not produce structured investigation finding handoffs into research workspace.
- **Missing Work:** Comparison investigation findings handoff (Task 122/123).
- **Status:** PARTIALLY_IMPLEMENTED

---

### Task 98: Fund Profile Epistemic Freshness & Data Quality Indicators
- **Original Requirement:** Comprehensive data quality and freshness section on fund profiles displaying six-dimensional quality states and publishing latency.
- **Relevant Implementation Location:**
  - `frontend/components/analysis/DataQualityFreshnessSection.tsx`
  - `AnalysisController.java` (`GET /api/v1/analysis/quality/{schemeOptionId}`)
- **Actual Implemented Behaviour:** Shows NAV freshness, benchmark freshness, quality score, calendar gaps, and flat streak warnings.
- **Persistence Status:** Backed by `analytical_score` and quality ledgers.
- **API Availability:** `/api/v1/analysis/quality/*`.
- **Frontend Integration:** Tab in `/funds/[id]`.
- **Known Limitations:** Did not map data freshness directly to affected metric calculation dependencies.
- **Missing Work:** Explicit freshness-to-metric dependency mapping (Task 119).
- **Status:** PARTIALLY_IMPLEMENTED

---

### Task 99: Working Investor Experience Consolidation & Baseline End-to-End Audit
- **Original Requirement:** Coherent, unified investor journey from discovery to fund analysis, comparison, research thesis, portfolio risk/stress, and monitoring.
- **Relevant Implementation Location:**
  - Next.js App Router (`/`, `/funds`, `/funds/[id]`, `/compare`, `/watchlist`, `/portfolio`, `/monitoring`, `/data-status`).
  - Spring Boot controllers and DTO contracts.
- **Actual Implemented Behaviour:** Full end-to-end routing exists, but key persistence gaps (allocation snapshots, thesis versioning), contract mismatches, and UI inconsistencies required consolidation.
- **Persistence Status:** Core foundation persisted; snapshot and thesis versioning added in V38.
- **API Availability:** REST APIs across all modules.
- **Frontend Integration:** Full Next.js client suite.
- **Known Limitations:** Fragmented metric labels, occasional zero fallbacks, and disconnected research-to-monitoring handoffs.
- **Missing Work:** Final baseline reconciliation and consolidation (Tasks 101–129).
- **Status:** PARTIALLY_IMPLEMENTED

---

## 3. Reconciliation Summary by Status

| Status | Tasks Count | Tasks |
|:---|:---:|:---|
| **IMPLEMENTED** | 10 | Tasks 83, 85, 86, 87, 88, 89, 91, 92, 93, 95 |
| **PARTIALLY_IMPLEMENTED** | 10 | Tasks 80, 81, 82, 84, 90, 94, 96, 97, 98, 99 |
| **NOT_IMPLEMENTED** | 0 | None (all tasks have existing code artifacts) |
| **REPLACED_BY_EXISTING** | 0 | None |
| **BLOCKED** | 0 | None |

---

## 4. Remediation Directives for Tasks 101–129

1. **Persistence Closures (Tasks 101, 102, 103, 105, 120):**
   - Flyway migration `V38` deployed to create `investor_portfolio_allocation_snapshot`, `investor_portfolio_allocation_snapshot_item`, `investor_stress_finding_transition`, and `investor_research_thesis_history`.
   - Durable entities and repositories implemented.
2. **Point-in-Time & Denominator Discipline (Tasks 104, 106, 107, 108, 109):**
   - Formalize denominator contract (`TOTAL_VALUED_CAPITAL` vs `UNKNOWN_UNVALUED`).
   - Strict bitemporal filtering with explicit disclosure when availability timestamps are bounded.
3. **Canonical Metrics & Governance Alignment (Tasks 110, 111, 112, 113, 114, 115):**
   - Backend authoritative metric registry connected to frontend.
   - Exact mathematical definitions and candidate status preserved.
4. **Investor Experience & Investigation Handoffs (Tasks 116–129):**
   - Benchmark comparability contracts, holdings look-through clarity, freshness dependency mapping.
   - Seamless research-to-monitoring workflow with mandatory investor confirmation.

