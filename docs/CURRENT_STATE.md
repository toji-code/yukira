# Current State Snapshot — Project YUKIRA

> **Document Type:** Factual System & Repository State Snapshot  
> **Last Updated:** September 23, 2026  
> **Authority:** Project YUKIRA Repository State  

---

## 1. Git & Repository Status

- **Current Branch:** `main`
- **Current HEAD Commit:** `18fac5f90b84a5d2d4bd82548d46f7b2ed0e4c62` (Short: `18fac5f`)
- **Commit Message:** `fix: enforce approved downside beta observation threshold`
- **Remote Synchronization:** Local `main` is synchronized with `origin/main` (`ahead 0, behind 0`).
- **Working Tree State:** Clean
- **Latest Completed Phase:** **Phase 2Q** (Risk-Adjusted / Market-Sensitivity Analytical Vertical)
- **Active Current Phase:** Phase 2R (Readiness Assessment / Scope Definition)

---

## 2. Verified Test Baselines

All three architectural tiers maintain independent, passing automated test suites:

| Subsystem / Tier | Test Framework | Passing Tests | Execution Command | Status |
| :--- | :--- | :---: | :--- | :---: |
| **Quantitative Engine** | Pytest 8.x / 9.x (Python 3.12+) | **301** | `cd quant-engine && pytest tests/` | Verified Pass |
| **Backend Service** | JUnit 5 / Spring Boot Test | **122** | `cd backend && ./mvnw test` | Verified Pass |
| **Frontend Application** | Vitest / Testing Library | **46** | `cd frontend && npm test` | Verified Pass |
| **Frontend Code Quality** | ESLint | — | `cd frontend && npm run lint` | Clean (0 errors) |
| **Frontend Production Build** | Next.js Compiler (`next build`) | — | `cd frontend && npm run build` | Successful |

*Note: Baselines reflect actual executed counts verified in repository audit history (416 total passing tests).*

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

### Phase 2J 5-Year Historical Observation Horizon:
- **Canonical Observation Ledger:** **1,243** authentic daily market trading dates spanning `2019-01-01` through `2024-01-15` (1,247 total rows in PostgreSQL `nav_observation`, including 4 development test-fixture revisions on 2021-01-04/05 and 2022-01-03/04).
- **Captured Annual Source Artifacts (Independently Verified):**
  - 2019: Artifact ID #149 (17,808,312 bytes, SHA-256: `d80ed193cba5a6686a26133f97bc2533a9885b660c900a002e2febf6e0e0903d`, 244 dates)
  - 2020: Artifact ID #155 (14,591,393 bytes, SHA-256: `10fe1a4b727777ecf3ba4b3bc0cce3442fb9b6f2375b4eb43f1168da5529875e`, 250 dates)
  - 2021: Artifact ID #150 (11,877,865 bytes, SHA-256: `19c9c5e5c934056782984f301a5567751ae78e150b9ac88d755479db8f24dcb3`, 247 dates)
  - 2022: Artifact ID #151 (8,405,491 bytes, SHA-256: `d84e86eb56f1245899d7fd6b715c8a115fc011d087ea2707b68bc13dd0db5d86`, 247 dates)
  - 2023: Artifact ID #152 (9,183,825 bytes, SHA-256: `30688ec699066df499212a2d30501a92c30312a857ad76187003b8b9386fa9cc`, 244 dates)
  - Jan 2024: Artifact ID #1 (11,185,549 bytes, SHA-256: `900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259`, 11 dates)
- **Data Quality & Verification Semantics:**
  - Observations are marked `VERIFIED` according to YUKIRA's current source-authority verification convention (i.e. successfully ingested from an authentic, structurally valid AMFI source artifact without validation boundary errors). This does **NOT** imply independent corroboration by a secondary external source (e.g. custodian trade ledgers or exchange reports).
  - Continuity diagnostics identify 1,841 calendar days = 526 weekends + 1,243 authentic trading days + 72 unresolved non-trading weekday gaps (coverage ratio: 1,243 / 1,315 weekdays = 94.5247%; potential exchange holidays vs missing observations, awaiting an authoritative market calendar). Raw physical rows in `nav_observation` count 1,247 due to 4 test-fixture revision rows on 2021-01-04/05 and 2022-01-03/04 which do not inflate calendar trading dates.
- **Query Endpoint:** `GET /api/v1/schemes/options/{id}/historical-series?startDate=...&analysisCutoff=...&knowledgeCutoff=...`
- **Epistemic Invariant:** Queries strictly require `knowledgeCutoff`; omission or `analysisCutoff > knowledgeCutoff` is rejected with HTTP 400.

### Baseline RET-02 Calculation Result:
- **Start Observation:** `2024-01-01` = `1630.7330`
- **End Observation:** `2024-01-15` = `1670.6720`
- **RET-02 Value:** `0.024491440352283345` (`+2.4491440352%`)
- **Calculation Status:** `COMPLETED`
- **Methodology Version:** `CANDIDATE_V1`
- **Benchmark Required:** `false` (Standalone Primitive Return)

---

## 5. Provenance Reconciliation: The January 2024 NAV Discrepancy

- **Status:** **RECONCILED, FORMALIZED & SEALED BY AUTOMATED REGRESSION SUITE**
- **The Issue:** During prior audit reporting, two different intermediate observation series were cited for dates `2024-01-02` through `2024-01-12`:
  - **Series A (Direct AMFI Raw Payload):** `1625.1540`, `1626.4560`, `1639.8560`, `1645.4000`, `1633.2610`, `1639.4210`, `1645.8200`, `1645.6560`, `1656.4160`.
  - **Series B (Unverified Synthetic Series):** `1629.742`, `1622.753`, `1638.169`, `1650.640`, `1638.291`, `1636.311`, `1642.348`, `1656.963`, `1667.135`.
- **Findings & Reconciliation:**
  1. Authoritative Source Artifact #1 (`900508f8...`) cryptographically proves that **Series A** is the exact byte stream published by the AMFI portal for scheme `118955`.
  2. The PostgreSQL database stores exclusively **Series A**.
  3. Series B has zero raw source artifacts in the repository and existed solely in prior conversational audit text.
  4. Regression test `January2024ProvenanceDiscrepancyTest.java` programmatically asserts that Series A matches the authoritative raw payload and explicitly asserts that synthetic Series B is rejected.
  5. Both series shared start (`1630.7330`) and end (`1670.6720`) values, yielding an identical RET-02 (+2.449144%) that previously masked intermediate drift.

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
