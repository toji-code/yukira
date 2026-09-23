# Current State Snapshot — Project YUKIRA

> **Document Type:** Factual System & Repository State Snapshot  
> **Last Updated:** September 23, 2026  
> **Authority:** Project YUKIRA Repository State  

---

## 1. Git & Repository Status

- **Current Branch:** `main`
- **Current HEAD Commit:** `e6880efc499d39f76171fa94e5dd97a42c8156cf` (Short: `e6880ef`)
- **Commit Message:** `feat: establish phase 2i working investor website and real-data pilot`
- **Remote Synchronization:** Local `main` is synchronized with `origin/main` (`ahead 0, behind 0`).
- **Working Tree State:** Clean (excluding newly created agent context documentation).
- **Latest Completed Phase:** **Phase 2I** (Working Investor Website & Real-Data Pilot).
- **Active Current Phase:** **Phase 2J** (Historical Analytical Data Foundation + Provenance Reconciliation) — In Progress.

---

## 2. Verified Test Baselines

All three architectural tiers maintain independent, passing automated test suites:

| Subsystem / Tier | Test Framework | Passing Tests | Execution Command | Status |
| :--- | :--- | :---: | :--- | :---: |
| **Quantitative Engine** | Pytest 8.x (Python 3.12) | **301** | `cd quant-engine && pytest tests/` | Verified Pass |
| **Backend Service** | JUnit 5 / Spring Boot Test | **54** | `cd backend && ./mvnw test` | Verified Pass |
| **Frontend Application** | Vitest / Testing Library | **46** | `cd frontend && npm test` | Verified Pass |
| **Frontend Code Quality** | ESLint | — | `cd frontend && npm run lint` | Clean (0 errors) |
| **Frontend Production Build** | Next.js Compiler (`next build`) | — | `cd frontend && npm run build` | Successful |

*Note: Baselines reflect actual executed counts verified in repository audit history.*

---

## 3. Subsystem Implementation States

### 3.1 Persistence Tier (PostgreSQL 17)
- **Status:** **OPERATIONAL (Foundation Complete)**
- **Schema Management:** Flyway is the exclusive schema authority (`V1__init_schema.sql` through `V7__methodology_governance.sql`).
- **Hibernate Mode:** `spring.jpa.hibernate.ddl-auto=validate` strictly enforced.
- **Bitemporal Structure:** Tables (`nav_observation`, `benchmark_observation`, `portfolio_snapshot`) store financial `effective_date`, retrieval/publication `availability_time`, monotonic `revision_seq`, and `is_latest_revision` boolean flag.
- **Raw Provenance:** `source_artifact` table stores full HTTP source URI, retrieval timestamp, byte size, cryptographic SHA-256 digest, and optional `payload_blob` (bytea).
- **Active Seed Data:** Zero manual SQL seeds in production. Canonical pilot master hierarchy and initial observation slice are idempotently established via `PilotBootstrapService`.

### 3.2 Backend Service (Spring Boot 4 / Java 21)
- **Status:** **OPERATIONAL (Core REST & RET-02 Operational)**
- **Domain Entities:** Full JPA mappings for `Amc`, `Scheme`, `SchemePlan`, `SchemeOption`, `NavObservation`, `SourceArtifact`, `CalculationRun`, `MetricResult`, and `ValidationIssue`.
- **Ingestion Pipeline:** `AmfiSourceClient` downloads raw payloads directly from AMFI; `AmfiNavParser` parses Windows-1252 semicolon-delimited records; `AmfiNavIngestionService` resolves scheme identities and persists bitemporal observations with SHA-256 linkage.
- **Calculation Orchestration:** `CalculationOrchestratorService` and `PeriodReturnCalculationService` resolve point-in-time observations against `knowledge_cutoff` timestamps and execute calculation runs.
- **REST Endpoints:**
  - `GET /api/v1/health` (System status and methodology governance status)
  - `GET /api/v1/schemes` & `GET /api/v1/schemes/{id}` (Mutual fund master entities)
  - `GET /api/v1/schemes/options` & `GET /api/v1/schemes/{id}/options` (Share classes / plans)
  - `GET /api/v1/analysis/{runId}` (Full calculation run audit response with complete input observation lineage)
  - `POST /api/v1/dev/bootstrap-pilot` (Development-only idempotent bootstrap runner)

### 3.3 Quantitative Engine (Python 3.12 / Vectorized Kernel)
- **Status:** **OPERATIONAL (Candidate Algorithm Library)**
- **Mathematical Stack:** NumPy 2.x, Polars, SciPy.
- **Implemented Candidate Algorithms (30 metrics):**
  - Return metrics: RET-01 to RET-07 (including RET-02 Simple Period Return)
  - Risk & Volatility: RSK-01 to RSK-08 (Standard Deviation, Downside Semideviation, Max Drawdown, VaR, CVaR/Expected Shortfall, Ulcer Index)
  - Risk-Adjusted Ratios: RAT-01 to RAT-05 (Sharpe, Sortino, Treynor, Information Ratio)
  - Relative / Benchmark: REL-01 to REL-07 (Alpha, Beta, Downside Beta, Tracking Error, Up/Down Capture, Active Share, Correlation, Covariance)
  - Portfolio Structure: PRT-01 to PRT-03 (Weighted Return, Concentration, Weight Turnover)
- **Governance Status:** All 30 metrics operate under **CANDIDATE** governance status. Only RET-02 is currently wired into the live end-to-end backend orchestrator.

### 3.4 Frontend Application (Next.js 16 / App Router)
- **Status:** **OPERATIONAL (Institutional Transparency Foundation)**
- **UI Stack:** Next.js 16 (App Router), React 19, TypeScript, Tailwind CSS 4.
- **Zero-Calculation Contract:** The frontend executes zero financial calculations. Every figure displayed originates directly from verified backend REST contracts.
- **Working Investor Routes:**
  - `/` — Landing page detailing institutional philosophy, epistemic guardrails, and system roadmap.
  - `/funds` — Fund discovery catalog displaying registered mutual fund schemes.
  - `/funds/[id]` — Detailed fund share class view with 6-dimensional data quality badges, provenance details, and analysis entry point.
  - `/analysis/[id]` — Authoritative RET-02 analysis audit view showing calculated return, requested vs. selected dates, input observation table with SHA-256 digest, and candidate methodology disclaimer.
  - `/methodology` — Institutional methodology governance transparency center detailing the 30-metric inventory, formula specifications, and Candidate/Validated/Approved lifecycle.

### 3.5 AI Interpretation Layer (`ai-services/`)
- **Status:** **PLANNED / ARCHITECTURAL STUB**
- Code exists as an architectural placeholder. It is intentionally **NOT** connected to calculation execution or live investor pages.

---

## 4. Canonical Pilot Instrument Status

All end-to-end integration and verification in Phase 2I/2J use exclusively the canonical pilot:

- **Fund:** HDFC Flexi Cap Fund
- **AMC:** HDFC Mutual Fund (`HDFC_MF`)
- **Plan:** Direct Plan (`DIRECT` / `HDFC_FLEXI_DIR`)
- **Option:** Growth Option (`GROWTH`)
- **AMFI Scheme Code:** `118955`
- **ISIN:** `INF179K01UT0`
- **Scheme Code:** `HDFC_FLEXI`

### Authoritative Phase 2I Baseline Ingestion:
- **Source Artifact ID:** `1`
- **Artifact Type:** `NAV_HISTORY_TEXT`
- **SHA-256 Digest:** `900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259`
- **Byte Size:** `11,185,549` bytes
- **Retrieved:** `2026-09-21 17:46:57.678304+00`
- **Source URL:** `https://portal.amfiindia.com/DownloadNAVHistoryReport_Po.aspx?mf=&scheme=118955&frmdt=01-Jan-2024&todt=15-Jan-2024`

### Baseline RET-02 Calculation Result:
- **Start Observation:** `2024-01-01` = `1630.7330`
- **End Observation:** `2024-01-15` = `1670.6720`
- **RET-02 Value:** `0.024491440352283345` (`+2.4491440352%`)
- **Calculation Status:** `COMPLETED`
- **Methodology Version:** `CANDIDATE_V1`
- **Benchmark Required:** `false` (Standalone Primitive Return)

---

## 5. Critical Provenance Status: The January 2024 NAV Discrepancy

- **Status:** **INVESTIGATED & IDENTIFIED**
- **The Issue:** During prior audit reporting, two different intermediate observation series were cited for dates `2024-01-02` through `2024-01-12`:
  - **Series A (Direct AMFI Raw Payload):** `1625.1540`, `1626.4560`, `1639.8560`, `1645.4000`, `1633.2610`, `1639.4210`, `1645.8200`, `1645.6560`, `1656.4160`.
  - **Series B (Unverified Synthetic Series):** `1629.742`, `1622.753`, `1638.169`, `1650.640`, `1638.291`, `1636.311`, `1642.348`, `1656.963`, `1667.135`.
- **Findings:**
  1. Authoritative Source Artifact #1 (`900508f8...`) cryptographically proves that **Series A** is the exact byte stream published by the AMFI portal for scheme `118955`.
  2. The PostgreSQL database currently stores **Series A**.
  3. Series B has no raw source artifact, does not exist in any database table, and was an unverified synthetic series cited in conversational audit text.
  4. Both series share the exact same start (`1630.7330`) and end (`1670.6720`) NAVs, which yielded an identical RET-02 (+2.449144%) and masked intermediate drift.
- **Current Action in Phase 2J:** Formalize Series A as authoritative; establish regression tests preventing synthetic Series B reintroduction.

---

## 6. What is Explicitly NOT Implemented

To prevent misrepresentation or premature assumptions, the following features are explicitly **NOT** implemented:

1. **Remaining 29 Metrics in Backend:** Only RET-02 is connected to the Spring Boot orchestrator. 29 metrics exist only as standalone Python candidate algorithms.
2. **Methodology Validation:** Zero methodologies have been validated or approved for production investment advice.
3. **Fund Scoring / Star Ratings:** No rating system exists. YUKIRA will never produce opaque 5-star badges.
4. **Investment Recommendations:** No BUY, HOLD, REDUCE, or AVOID advice is generated.
5. **Broad-Market Benchmarks:** Ingestion pipelines for official benchmark indices (NIFTY 50 TRI, BSE 500 TRI) are scheduled for later phases.
6. **Risk-Free Rate Feeds:** Ingestion of FBIL 91-day Treasury Bill yields is not yet wired to the database.
7. **Monthly Portfolio Disclosures:** Ingestion of SEBI monthly portfolio holding sheets is not yet implemented.
8. **Cloud Infrastructure:** Production Kubernetes, cloud VPCs, and automated CI/CD deployment pipelines are not yet provisioned.
