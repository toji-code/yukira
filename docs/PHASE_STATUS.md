# Phase Status & Engineering History — Project YUKIRA

> **Document Type:** Chronological Engineering Phase History  
> **Status:** Authoritative Milestone Audit Record  
> **Rule:** Records only verified facts supported by Git history and repository artifacts.  

---

## 1. Project Milestone Roadmap Overview

```text
┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  Phase 2A–2B │ ──> │  Phase 2C–2E │ ──> │   Phase 2F   │ ──> │  Phase 2G–2H │ ──> │   Phase 2I   │
└──────────────┘     └──────────────┘     └──────────────┘     └──────────────┘     └──────────────┘
 Concept Audit        Multi-Tier Core      Real AMFI Slice      Governance Freeze    Investor Website
 & 30-Metric Freeze   DB + Backend + UI    Vertical Ret-02      Methodology Spec     Real Pilot (e6880ef)
                                                                                           │
                                                                                           ▼
                                                                                    ┌──────────────┐
                                                                                    │   Phase 2J   │
                                                                                    │ (IN PROGRESS)│
                                                                                    └──────────────┘
                                                                                     Historical 5Y NAV
                                                                                     Provenance Reconciliation
```

---

## 2. Completed Phase History

### Phase 2A — Repository & Architecture Audit
- **Focus:** Complete architectural audit of legacy files, constitutional alignment, and repository structure.
- **Key Deliverables:**
  - Establishment of multi-tier directory structure (`backend/`, `frontend/`, `quant-engine/`, `database/`, `ai-services/`).
  - Auditing of dependency versions (Java 21, Python 3.12, Node 20+, PostgreSQL 17).
  - Identification of core institutional requirements and epistemic principles.

### Phase 2B — MVP Scope & Metric Inventory Freeze
- **Focus:** Freezing the 30-metric candidate quantitative inventory for institutional mutual fund analysis.
- **Key Deliverables:**
  - Selection of candidate metrics across Returns (7), Risk & Volatility (8), Risk-Adjusted Ratios (5), Relative/Benchmark (7), and Portfolio Structure (3).
  - Formal prohibition against star ratings, opaque composite scores, and automated BUY/HOLD/AVOID advice.
  - Establishment of the deterministic calculation principle.

### Phase 2C — Database Architecture & Bitemporal Data Contracts
- **Focus:** PostgreSQL bitemporal schema design and Flyway migration framework.
- **Key Deliverables:**
  - Flyway migrations V1–V4 establishing master hierarchy (`amc`, `scheme`, `scheme_plan`, `scheme_option`).
  - Implementation of bitemporal observation tables (`nav_observation`, `benchmark_observation`) separating financial `effective_date` from transaction `availability_time`.
  - Creation of `source_artifact` table with cryptographic SHA-256 storage.
  - Monotonic `revision_seq` and `is_latest_revision` flags for append-only auditability.

### Phase 2D — Backend Foundation & Quantitative Engine Integration
- **Commit:** `92c5f25` (*feat: establish yukira backend quant and frontend foundation*)
- **Focus:** Spring Boot backend architecture and standalone Python quantitative calculation library.
- **Key Deliverables:**
  - Spring Boot 4 REST services and Spring Data JPA repositories.
  - Standalone Python 3.12 mathematical engine using NumPy 2, Polars, and SciPy.
  - 301 automated deterministic unit tests verifying the 30 candidate algorithms.
  - IPC/subprocess orchestration bridge between Java backend and Python engine.

### Phase 2E — Frontend Presentation Foundation
- **Commit:** `92c5f25` (*feat: establish yukira backend quant and frontend foundation*)
- **Focus:** Institutional Next.js web application operating under a zero-calculation contract.
- **Key Deliverables:**
  - Next.js 16 (App Router), React 19, TypeScript, and Tailwind CSS 4 setup.
  - Implementation of institutional design system: dark mode, glassmorphism, responsive data grids.
  - Creation of visual epistemic components (`DataQualityBadge`, `MethodologyBadge`, `StateView`).
  - 46 passing frontend unit tests.

### Phase 2F — Real AMFI Data & End-to-End Vertical Slice
- **Commit:** `3eadee4` (*feat: complete phase 2f real data analysis vertical slice*)
- **Focus:** First live vertical slice connecting real public AMFI NAV data to an end-to-end calculation.
- **Key Deliverables:**
  - `AmfiSourceClient` executing HTTP GET queries against official AMFI portal.
  - `AmfiNavParser` streaming and tokenizing Windows-1252 semicolon-delimited text.
  - End-to-end execution of **RET-02 (Simple Period Return)** on live data.
  - Automated integration test `AmfiRealDataIntegrationTest` verifying end-to-end ingestion and calculation.

### Phase 2G — Methodology Governance Framework
- **Commit:** `ccf1ee0` (*feat: establish phase 2g methodology governance*)
- **Focus:** Formalizing the three-tier governance lifecycle (`CANDIDATE`, `VALIDATED`, `APPROVED`).
- **Key Deliverables:**
  - Flyway migration `V7__methodology_governance.sql`.
  - Methodology registry enforcing governance validation in `backend/` persistence and API layers.
  - Refusal of unapproved methodologies to be presented as validated investment guidance.

### Phase 2H — Quantitative Methodology Specification Freeze
- **Commit:** `809c228` (*docs: establish phase 2h quantitative methodology*)
- **Focus:** Authoritative mathematical freeze of all 30 candidate metrics.
- **Key Deliverables:**
  - Freezing of [`phase2h_quantitative_methodology.md`](file:///phase2h_quantitative_methodology.md) containing formal mathematical definitions, inputs, edge cases, and benchmarks.
  - Strict freeze directive: Methodology cannot be modified or re-versioned without project owner approval.

### Phase 2I — Working Investor Website & Real-Data Pilot
- **Commit:** `e6880ef` (*feat: establish phase 2i working investor website and real-data pilot*)
- **Focus:** Live, auditable stakeholder web presentation backed by real canonical pilot data.
- **Key Deliverables:**
  - Rejection and purge of contaminated identifiers (`119062` / `INF179K01BE2`); acceptance of canonical pilot: HDFC Flexi Cap Fund Direct Plan Growth (AMFI: `118955`, ISIN: `INF179K01UT0`).
  - Non-destructive `PilotBootstrapService` registering master entities and baseline calculation run #1.
  - Working pages: Landing (`/`), Fund Catalog (`/funds`), Fund Detail (`/funds/[id]`), Analysis Audit (`/analysis/[id]`), and Methodology Center (`/methodology`).
  - Verified baseline RET-02 return: `+2.4491440352%` between `2024-01-01` (`1630.7330`) and `2024-01-15` (`1670.6720`).
  - Synchronized `main` branch with GitHub `origin/main`.

---

## 3. Active Current Phase: Phase 2J

### Objective: Historical Analytical Data Foundation + Provenance Reconciliation
- **Status:** **IN PROGRESS (NOT YET COMMITTED)**
- **Scope & Targets:**
  1. **Provenance Reconciliation:** Rigorously investigate and resolve the January 2024 intermediate NAV discrepancy between raw AMFI data (Series A) and unverified audit citations (Series B).
  2. **Historical Data Ingestion:** Extend AMFI ingestion to cover at least 5 years of historical NAV data for the canonical pilot using real source artifacts with SHA-256 tracking.
  3. **Revision & Point-in-Time Handling:** Enforce that retroactive revisions increment sequence numbers without overwriting historical records, respecting `knowledge_cutoff` queries.
  4. **Trading-Date Continuity Diagnostics:** Implement diagnostics distinguishing expected non-trading days (weekends, market holidays), missing observations, duplicates, conflicts, and revisions without relying on unapproved hardcoded holiday calendars.
  5. **Data Quality Propagation:** Propagate the 6-dimensional taxonomy (`VALID`/`SUSPICIOUS`/`INVALID`, `CURRENT`/`STALE`, etc.) without converting quality flags into investment scores.
  6. **Analytical Data Contract:** Define strict Java/Python contracts ensuring subsequent quantitative metric calculations cannot query data without explicit knowledge cutoffs.
  7. **Comprehensive Testing:** Add deterministic tests covering the 15 required verification areas while preserving the 301/54/46 baseline.

> [!NOTE]
> Phase 2J does not implement all 30 metrics or introduce scoring. Its exclusive purpose is to establish a trustworthy, point-in-time historical data substrate for future multi-year quantitative calculations.
