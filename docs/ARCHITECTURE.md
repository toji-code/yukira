# System Architecture Specification — Project YUKIRA

> **Document Type:** Multi-Tier Architecture & Data Flow Specification  
> **Status:** Active Architectural Reference  
> **Rule:** Distinguishes explicitly between IMPLEMENTED, PARTIALLY IMPLEMENTED, and PLANNED / NOT IMPLEMENTED components.  

---

## 1. End-to-End Architectural Pipeline

```text
┌────────────────────────┐
│      Data Sources      │   AMFI NAV Portal (Real) [IMPLEMENTED]
│                        │   NSE/BSE Benchmarks, FBIL Risk-Free, SEBI Holdings [PLANNED]
└───────────┬────────────┘
            │ HTTP GET / Stream
            ▼
┌────────────────────────┐
│    Ingestion Engine    │   AmfiSourceClient (Raw Bytes & Cryptographic Hashing) [IMPLEMENTED]
│                        │   Multi-source chunking & rate limit handling [PARTIALLY IMPLEMENTED]
└───────────┬────────────┘
            │ Raw Bytes + Metadata
            ▼
┌────────────────────────┐
│  Validation & Lineage  │   Windows-1252 / Semicolon Parser (AmfiNavParser) [IMPLEMENTED]
│                        │   Six-Dimensional Data Quality Evaluator [PARTIALLY IMPLEMENTED]
└───────────┬────────────┘
            │ Validated Observations + SHA-256
            ▼
┌────────────────────────┐
│       Data Store       │   PostgreSQL 17 Bitemporal Storage (Flyway V1–V7) [IMPLEMENTED]
│                        │   effective_date + availability_time + revision_seq [IMPLEMENTED]
└───────────┬────────────┘
            │ Point-in-Time Query (analysis_cutoff, knowledge_cutoff)
            ▼
┌────────────────────────┐
│      Quant Engine      │   Python 3.12 Stateless Mathematical Kernel [IMPLEMENTED]
│                        │   NumPy 2 / Polars Vectorized Algorithms (301 tests) [IMPLEMENTED]
└───────────┬────────────┘
            │
            ├────────────────────────┐
            │                        │
            ▼                        ▼
┌────────────────────────┐   ┌────────────────────────┐
│   Risk/Factor Engine   │   │  Scoring & Decisions   │
│   [PLANNED]            │   │  [PROHIBITED / NONE]   │
└───────────┬────────────┘   └────────────────────────┘
            │                        │
            ▼                        ▼
┌────────────────────────┐   ┌────────────────────────┐
│ Forecast & Scenarios   │   │   AI Interpretation    │
│ [PLANNED]              │   │   [ARCHITECTURAL STUB] │
└───────────┬────────────┘   └───────────┬────────────┘
            │                            │
            └────────────┬───────────────┘
                         │ Structured Analysis Lineage
                         ▼
┌─────────────────────────────────────────────────────┐
│             Backend Orchestration & API             │   Spring Boot 4 / Java 21 REST API [IMPLEMENTED]
│                                                     │   Calculation Runs & Audit Contracts [IMPLEMENTED]
└──────────────────────────┬──────────────────────────┘
                           │ JSON REST Contract
                           ▼
┌─────────────────────────────────────────────────────┐
│                 Investor Web UI                     │   Next.js 16 App Router / React 19 [IMPLEMENTED]
│                                                     │   Strict Zero-Calculation Presentation [IMPLEMENTED]
└─────────────────────────────────────────────────────┘
```

---

## 2. Implementation Status by Architectural Component

| Component | Status | Description |
| :--- | :---: | :--- |
| **AMFI NAV Ingestion** | **IMPLEMENTED** | Direct HTTP retrieval from AMFI portal, raw payload byte archiving, and SHA-256 computation. |
| **Benchmark Ingestion** | **PLANNED** | Official total return index (TRI) feeds for NIFTY 50 and BSE 500. |
| **Risk-Free Rate Ingestion** | **PLANNED** | FBIL 91-day Treasury Bill daily yield curve ingestion. |
| **Portfolio Holdings Ingestion**| **PLANNED** | SEBI monthly portfolio disclosure sheet parsing and ISIN resolution. |
| **Data Validation & Parsing** | **IMPLEMENTED** | Delimited text tokenization, numeric NAV parsing, and error record tracking. |
| **Data Quality Taxonomy** | **PARTIALLY IMPLEMENTED** | Six-dimensional taxonomy defined in schema and DTOs; automated diagnostics expanding in Phase 2J. |
| **Bitemporal Data Store** | **IMPLEMENTED** | PostgreSQL 17 schema enforcing `effective_date` and `availability_time` separation. |
| **Flyway Schema Authority** | **IMPLEMENTED** | Seven versioned migrations (V1–V7); Hibernate `ddl-auto=validate`. |
| **Quantitative Math Kernel** | **IMPLEMENTED** | Python 3.12 vectorized calculation library with 301 passing deterministic tests. |
| **Backend REST API** | **IMPLEMENTED** | Spring Boot endpoints for schemes, plans, options, health, and analysis runs. |
| **Calculation Orchestration** | **PARTIALLY IMPLEMENTED** | RET-02 operational; remaining 29 candidate metrics not yet wired to backend pipeline. |
| **Methodology Governance** | **IMPLEMENTED** | Formal Candidate / Validated / Approved tier enforcement in persistence and API. |
| **Web Presentation Tier** | **IMPLEMENTED** | Next.js 16 institutional UI operating under a zero-calculation contract. |
| **AI Interpretation Layer** | **PLANNED** | Architectural stub in `ai-services/`; completely isolated from financial calculation. |
| **Multi-Factor Attribution** | **PLANNED** | Fama-French and Carhart factor regression models across market regimes. |
| **Scenario & Stress Testing** | **PLANNED** | Macroeconomic rate shock and liquidity crunch simulations. |
| **Investment Decision Engine** | **PROHIBITED / NONE**| System explicitly does not generate automated BUY/HOLD/AVOID investment advice. |

---

## 3. Subsystem Architecture Deep-Dives

### 3.1 Persistence Tier & Bitemporal Data Model
- **Database:** PostgreSQL 17 (Containerized via Docker Compose).
- **Schema Management:** Flyway manages all migrations. Schema modifications are strictly additive and version-controlled:
  - `V1__init_schema.sql` — Core AMC, scheme, plan, option, and data source tables.
  - `V2__timeseries_schema.sql` — Bitemporal observation tables (`nav_observation`, `benchmark_observation`).
  - `V3__portfolio_schema.sql` — Portfolio holdings and issuer structure tables.
  - `V4__data_quality_schema.sql` — Data validation issue tracking.
  - `V5__methodology_schema.sql` — Methodology definitions and parameters.
  - `V6__calculation_results_schema.sql` — Calculation runs, input observation mapping, and metric results.
  - `V7__methodology_governance.sql` — Formal governance status (`CANDIDATE`, `VALIDATED`, `APPROVED`).

#### The Bitemporal Point-in-Time Mechanics
Every financial observation stored in `nav_observation` maintains two independent timelines:
1. **Valid Time (`effective_date`):** The trading date for which the NAV applies in the Indian financial market.
2. **Transaction / Knowledge Time (`availability_time`):** The timestamp when YUKIRA ingested or received the data point from the official source.

```text
Record Key: (scheme_option_id, effective_date, revision_seq) [UNIQUE]
```

When an analysis is requested with `analysis_cutoff = 2024-01-15` and `knowledge_cutoff = 2024-01-31 23:59:59+05:30`:
- The database filters: `effective_date <= 2024-01-15 AND availability_time <= 2024-01-31 23:59:59+05:30`.
- If multiple revisions exist for the same effective date, the query selects the record with `MAX(revision_seq)` among those available before the knowledge cutoff.
- Any revision ingested after the knowledge cutoff is mathematically invisible, ensuring zero look-ahead bias and 100% historical reproducibility.

---

### 3.2 Ingestion Engine & Provenance Architecture
The ingestion architecture follows an uncompromising raw-evidence-first model:

```text
[AMFI Portal HTTP] 
        │
        ▼ (executeHttpGet)
[Raw Byte Stream] ────> [SHA-256 Digest & Byte Count]
        │                           │
        ▼                           ▼
[AmfiNavParser]              [source_artifact Table]
(Windows-1252 Tokenizer)     (Stores SHA-256, URL, Timestamp, Payload)
        │
        ▼
[AmfiNavRecord DTOs]
(Validation & Identity Resolution)
        │
        ▼
[nav_observation Table]
(FK to source_artifact.id)
```

1. **Exact Byte Capture:** Ingestion captures the raw response stream before decoding.
2. **Cryptographic Hashing:** A SHA-256 digest is computed across the raw bytes.
3. **Source Artifact Registration:** A row is inserted into `source_artifact` recording the exact URL, retrieval timestamp, byte length, SHA-256 hash, and full payload blob.
4. **Observation Linkage:** Ingested observations store a foreign key `source_artifact_id` pointing directly to the originating artifact, ensuring complete auditability from calculation result back to raw network payload.

---

### 3.3 Quantitative Engine & Mathematical Kernels
- **Environment:** Standalone Python 3.12 process.
- **Dependencies:** NumPy 2.x, Polars, SciPy (zero ORM, zero web framework).
- **Contract:** Pure mathematical functions. Input is a structured array/dataframe of verified dates and values; output is an exact numeric value and calculation metadata.
- **Test Automation:** 301 unit tests covering edge cases (zero variance, negative returns, asymmetric downside deviations, non-continuous intervals, extreme drawdowns).
- **Decoupling:** The quant engine does not connect directly to PostgreSQL. It is invoked via structured IPC / subprocess / REST calls from the Spring Boot calculation orchestrator.

---

### 3.4 Backend Orchestrator & Governance Engine
The Spring Boot service (`backend/`) acts as the central coordinator:
1. **Master Identity Management:** Idempotent registration of AMCs, schemes, plans, and options.
2. **Observation Resolution:** Queries bitemporal observations adhering to point-in-time constraints.
3. **Input Snapshot Hashing:** Computes a SHA-256 digest across the serialized input observation set before invoking the calculation.
4. **Governance Enforcement:** Verifies whether the requested methodology version is `CANDIDATE`, `VALIDATED`, or `APPROVED`.
5. **Execution Recording:** Records a `calculation_run` entity with status, error message, git commit, input snapshot hash, and resulting `metric_result` rows.

---

### 3.5 Frontend Web Application
- **Framework:** Next.js 16 (App Router), React 19, TypeScript, Tailwind CSS 4.
- **Strict Zero-Calculation Principle:** The presentation layer formats and displays verified data delivered by the backend. It does not compute returns, ratios, or cumulative statistics.
- **Epistemic State Components:**
  - `DataQualityBadge` — Visual indicator of observation quality (VALID / SUSPICIOUS / INVALID).
  - `MethodologyBadge` — Prominent governance state indicator (CANDIDATE / VALIDATED / APPROVED).
  - `StateView` — Visual disclosure of loading, empty, unverified, and error states.

---

### 3.6 Architectural Boundaries & Isolation Rules

1. **Frontend / Backend Boundary:** All communication occurs over JSON REST APIs. The frontend has no direct database access and no quant engine access.
2. **Backend / Quant Engine Boundary:** The backend handles data persistence, identity resolution, and governance. The quant engine handles pure mathematical execution.
3. **AI / Financial Math Boundary:** AI services are strictly downstream of calculation runs. AI services read verified outputs to draft explanations; they never feed numbers back into the calculation engine.
