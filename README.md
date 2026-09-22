# YUKIRA — Investment Intelligence Platform

> **Before you commit capital, ask one more question.**

---

## What is YUKIRA?

**YUKIRA** is an AI-powered Investment Intelligence Platform initially focused on institutional-grade analysis of Indian mutual funds.

### Core Idea

YUKIRA is intended to become the verification and checkpoint investors use before making meaningful investment decisions. Rather than relying on simplistic trailing returns or opaque star ratings, YUKIRA provides a transparent, auditable analytical framework combining:

- **Deterministic financial calculations**
- **Quantitative analysis**
- **Risk and tail analysis**
- **Portfolio analysis**
- **Factor analysis**
- **Manager and process analysis**
- **Valuation analysis**
- **Scenario and stress testing**
- **Probabilistic forecasting**
- **Explainable AI interpretation**

> [!IMPORTANT]
> **Strict AI Governance Rule:** Artificial intelligence must never invent, estimate, or override authoritative financial calculations. In YUKIRA, all quantitative calculations are executed by deterministic numerical kernels with formal data lineage. AI is restricted to explaining verified outputs and assisting qualitative inquiry.

---

## Current Project Status

**YUKIRA has progressed beyond the concept stage into a functional multi-tier engineering system.**

### Current Working Capabilities
- **Working Software Architecture:** Multi-tier decoupled architecture with dedicated database, backend, quantitative engine, and presentation layers.
- **PostgreSQL Persistence Foundation:** Bitemporal point-in-time schema (Flyway migrations V1–V7) separating financial observation dates (`effective_date`) from knowledge cutoff timestamps (`availability_time`), ensuring immutable audit trails and revision histories.
- **Spring Boot Backend:** Robust REST API and calculation orchestrator enforcing request parameter validation, point-in-time observation resolution, and auditable data contracts.
- **Python Quantitative Engine:** Vectorized financial calculation kernel powered by NumPy 2, Polars, and SciPy, executing candidate mathematical algorithms in total isolation from the web tier.
- **Next.js Frontend:** Institutional-grade web interface (Next.js 16 App Router, React 19, Tailwind CSS 4, TypeScript) operating under a strict zero-calculation presentation tier.
- **Real AMFI NAV Ingestion:** End-to-end ingestion pipeline for Association of Mutual Funds in India (AMFI) daily and historical NAV datasets, handling multi-megabyte source text parsing, validation, and database storage.
- **Raw-Source Provenance and Hashing:** Ingestion artifacts are tracked with cryptographic SHA-256 digests, HTTP retrieval timestamps, and exact byte counts.
- **Point-in-Time (PIT) / Bitemporal Data Structures:** Lookback window resolution preventing look-ahead bias by strictly filtering observations available as-of a user-defined knowledge cutoff.
- **Deterministic Calculation Infrastructure:** Parameterized calculation execution with input observation snapshot SHA-256 digests and quant engine git commit tracking.
- **One Implemented Candidate Methodology:** **RET-02 Simple Period Return** (supporting primitive: $R = \frac{\text{NAV}_{\text{end}}}{\text{NAV}_{\text{start}}} - 1$) fully wired end-to-end.
- **Methodology Governance:** Formal methodology registry enforcing candidate vs. validated vs. approved lifecycle states.
- **Automated Test Suites:** Complete multi-tier test automation including 301 Python quant engine tests, 54 Spring Boot backend integration/unit tests, and 46 frontend unit tests.
- **Initial Investor-Facing Frontend Routes:** Working pages for Home (`/`), Fund Discovery (`/funds`), Fund Detail (`/funds/[id]`), and Quantitative Analysis Run Audit (`/analysis/[id]`).

---

### Current System Limitations

> [!WARNING]
> **YUKIRA IS NOT YET AN INVESTOR-READY PRODUCTION PLATFORM.**
> The system is currently in active development. Stakeholders must be aware of the following explicit limitations:

- **Candidate Specifications Only:** 29 of the 30 candidate analytical metrics remain specifications; only RET-02 is currently wired into the execution pipeline.
- **No Production-Validated Methodology:** No quantitative methodology is currently validated for production use. All existing calculations operate under candidate governance status.
- **No Scoring System:** YUKIRA does not produce overall fund ratings, scores, or star grades.
- **No Recommendation Engine:** The platform does not issue BUY, HOLD, REDUCE, or AVOID recommendations or any commercial investment advice.
- **AI Layer Not Yet Productionized:** Qualitative AI interpretation services are in early development and not connected to investor views.
- **Broader Data Sources Not Yet Integrated:** Systematic ingestion of official benchmark indices, daily risk-free rates, and monthly portfolio holding disclosures is scheduled for upcoming phases.
- **Production Deployment Not Complete:** System currently operates in containerized and local development environments; cloud production infrastructure is not yet provisioned.
- **Backtesting and Empirical Validation Incomplete:** Formal backtesting across multi-cycle market regimes has not yet commenced.

---

## Project Completion View

The following table summarizes current progress across key technical and product workstreams.

> [!NOTE]
> **PROJECT DELIVERY ESTIMATES:** These figures represent approximate progress indicators based on completed engineering deliverables and specifications. They are **not** objective financial scores or scientific measurements.

| Workstream / Area | Estimated Progress | Deliverable Summary |
| :--- | :---: | :--- |
| **Product Concept & Scope** | **~90%** | Institutional vision, epistemic principles, 30-metric MVP inventory defined. |
| **System Architecture** | **~75%** | Multi-service boundaries, contracts, PIT data flow, Docker orchestration. |
| **Database Foundation** | **~55%** | PostgreSQL bitemporal schema, Flyway migrations V1–V7, data provenance. |
| **Backend Service** | **~35%** | Spring Boot 4, JPA repositories, calculation orchestrator, RET-02 service. |
| **Quantitative Engine** | **~25%** | Python kernel, 301 unit tests, mathematical specifications for 30 metrics. |
| **Data Ingestion** | **~30%** | AMFI real NAV parser, SHA-256 raw artifact archiving, error logging. |
| **Investment Methodology** | **~25%** | Phase 2H specification frozen; RET-02 candidate implemented; validation pending. |
| **Frontend / Investor Website** | **~30%** | Next.js 16 app, design system, fund catalog, analysis audit view. |
| **AI Interpretation Layer** | **~10%** | Architectural boundaries established; execution strictly isolated from financial calculations. |
| **Production Deployment** | **0%** | Cloud hosting, high-availability replication, and monitoring pending. |
| **Investor-Ready MVP** | **~20–25%** | **Overall progress toward first fully validated investor release.** |

---

## What Exists Today

### Actual Architecture Pipeline

```text
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│  Data Sources   │ ────> │ Ingestion Engine│ ────> │  Validation &   │
│  (AMFI NAVs)    │       │ (Raw Text / S3) │       │ Integrity Check │
└─────────────────┘       └─────────────────┘       └─────────────────┘
                                                             │
                                                             ▼
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│ Quantitative    │ <──── │ Point-in-Time   │ <──── │ PostgreSQL Store│
│ Engine (Python) │       │ Resolution      │       │ (Bitemporal)    │
└─────────────────┘       └─────────────────┘       └─────────────────┘
         │
         ▼
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│  Metric Results │ ────> │ REST API        │ ────> │ Next.js Web App │
│  & Audit Trail  │       │ (Spring Boot)   │       │ (Investor UI)   │
└─────────────────┘       └─────────────────┘       └─────────────────┘
```

1. **Data Sources:** Real AMFI daily NAV text feeds and historical NAV archives.
2. **Ingestion:** Raw source archiving with cryptographic SHA-256 verification and storage.
3. **Validation & Integrity:** Six-dimensional data quality inspection (Quality, Verification, Revision, Freshness, Presence, Integrity).
4. **Data Store:** PostgreSQL 17 database maintaining master scheme entities, plans, options, and bitemporal NAV observations.
5. **PIT Resolution:** Observation selection enforcing user-specified knowledge cutoffs and conservative lookback window rules (maximum 4 calendar days).
6. **Quant Engine:** Standalone Python numerical engine executing calculations without side effects.
7. **Metric Results:** Immutable record of calculation runs, input observation hashes, and diagnostics.
8. **REST API:** Spring Boot 4 service exposing clean DTOs and enforcing strict error semantics (no fake zeros).
9. **Next.js Frontend:** Clean, professional research UI providing 3-level progressive disclosure of all results.

### Real AMFI Pilot Capability
The system currently demonstrates real end-to-end data flow using the **HDFC Flexi Cap Fund** (Direct Plan - Growth Option, AMFI Code: `118955`, ISIN: `INF179K01UT0`):
- Ingests real historical NAV data from official AMFI daily data files.
- Records raw source file metadata (11.18 MB source artifact, SHA-256 digest `900508f8...`).
- Resolves boundary NAV observations under point-in-time constraints.
- Calculates deterministic return via the Python engine ($+2.4491\%$ return for 2024-01-01 to 2024-01-15: $1630.733 \rightarrow 1670.672$).
- Exposes full lineage down to raw observation IDs and source hashes.

### Existing Frontend Routes
- **`/` (Home):** Core platform identity, value proposition, epistemic baseline, architecture topology, and system navigation.
- **`/funds` (Fund Discovery):** Searchable catalog displaying registered funds, codes, asset management companies, inception dates, and active share classes. Shows explicit state if backend data is unreachable.
- **`/funds/[id]` (Fund Detail):** Detailed fund overview including fund identity, data status, registered plans and options, and interactive controls to trigger point-in-time quantitative calculations.
- **`/analysis/[id]` (Quantitative Analysis Run Audit):** Auditable presentation of an executed calculation run, featuring RET-02 Simple Period Return, PIT evaluation window, lookback substitution evidence, 6-dimensional data quality assessment, and complete input observation lineage.

---

## Quantitative Methodology Status

Phase 2B froze the MVP candidate inventory at **30 analytical metrics** across **seven analytical dimensions**:

1. **Return Quality (6 metrics):** RET-01 (1Y CAGR), RET-03 (3Y CAGR), RET-04 (5Y CAGR), RET-05 (3Y Rolling Return Mean), RET-06 (Rolling Outperformance %), RET-07 (3Y Active Return).
2. **Risk & Tail (7 metrics):** RSK-01 (Annualized Volatility), RSK-02 (Downside Semideviation), RSK-03 (Maximum Drawdown 3Y), RSK-04 (Maximum Drawdown Duration), RSK-05 (Ulcer Index), RSK-06 (Historical VaR 95%), RSK-07 (Expected Shortfall 95%).
3. **Risk-Adjusted (4 metrics):** RAT-01 (Sharpe Ratio 3Y), RAT-02 (Sortino Ratio 3Y), RAT-03 (Treynor Ratio 3Y), RAT-04 (Information Ratio 3Y).
4. **Market Sensitivity (5 metrics):** MKT-01 (Beta 3Y), MKT-02 (Downside Beta), MKT-03 (Upside Capture), MKT-04 (Downside Capture), MKT-05 (Capture Spread).
5. **Benchmark / Alpha (2 metrics):** REL-01 (Tracking Error 3Y), REL-02 (Jensen's Alpha 3Y).
6. **Portfolio Structure (5 metrics):** PRT-01 (Top-10 Concentration), PRT-02 (Effective Number of Holdings), PRT-03 (Active Share), PRT-04 (Monthly Weight Turnover), PRT-05 (Cash & Equivalent Allocation %).
7. **Governance / Expense (1 metric):** GOV-01 (Direct Plan TER).

*Supporting Implemented Primitive:* **RET-02 (Simple Period Return)** is a standalone calculation primitive ($R = \frac{\text{NAV}_{\text{end}}}{\text{NAV}_{\text{start}}} - 1$) inherited from Phase 2F, and is explicitly separated and not counted among the 30 candidate analytical metrics.

Phase 2H formally documented the mathematical formulations, calendar conventions, missing-data rules, and boundary conditions for all 30 candidate metrics in [`phase2h_quantitative_methodology.md`](file:///phase2h_quantitative_methodology.md).

### Authoritative Governance Baseline

```text
┌─────────────────────────────────────────────────────────────────────────┐
│                      EPISTEMIC GOVERNANCE MANDATE                       │
│                                                                         │
│   Implemented Candidate Methodology : RET-02 Simple Period Return       │
│   Validated Methodology             : NONE                              │
│   Approved Production Methodology   : NONE                              │
│   Empirical Findings                : EXACTLY ZERO                      │
└─────────────────────────────────────────────────────────────────────────┘
```

> [!CAUTION]
> **Implemented ≠ Validated ≠ Approved Production Methodology**
>
> - **Implemented:** Code exists and executes deterministically.
> - **Validated:** Methodology has been tested empirically across multiple market regimes, verified against independent authoritative sources, and audited for statistical soundness.
> - **Approved:** Formally authorized by the methodology review committee for live production investor decision support.
>
> Writing a specification or implementing an algorithm in code does **not** make it validated. YUKIRA will never present unvalidated calculations as authoritative investment guidance.

---

## Website Status

The YUKIRA website foundation is built on Next.js 16 (App Router) and builds cleanly. It is currently an **engineering and transparency foundation**, designed to communicate real data and real limitations rather than presenting a facade of finished functionality.

### Intended Investor Journey

```text
  Landing / Home (/)
         │
         ▼
  Explore Funds (/funds)
         │
         ▼
  Select Fund & Share Class (/funds/[id])
         │
         ▼
  Fund Overview & Data Status
         │
         ├──> Evidence & Data Quality (6 Dimensions)
         ├──> Quantitative Analysis (/analysis/[id])
         ├──> [PLANNED] Risk & Tail Analysis
         ├──> [PLANNED] Portfolio Structure Analysis
         ├──> [PLANNED] Methodology & Validation Audits
         ├──> [PLANNED] Explainable AI Interpretation
         └──> [PLANNED] Investor Decision Support
```

### Design Principles
- **Evidence Over Decoration:** Every visual element serves to clarify evidence, data quality, or methodology.
- **Clarity Over Density:** Professional research layout with progressive disclosure.
- **No Fake Data:** If data is missing or a metric is unimplemented, the system explicitly displays "Not yet available" or "Candidate methodology — not validated for production."
- **Zero Frontend Calculation:** Authoritative calculations originate exclusively from the backend and quant engine.

---

## Project Roadmap

```text
┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│    COMPLETED     │ ──> │       NOW        │ ──> │       NEXT       │ ──> │      LATER       │
└──────────────────┘     └──────────────────┘     └──────────────────┘     └──────────────────┘
• Architecture Core      • Stakeholder Web  • Additional 29    • Production Cloud
• PostgreSQL Schema      • Real AMFI Pilot    Metrics Impl       Deployment
• PIT Bitemporal Data    • Discovery UI     • Benchmark Feeds  • Full MF Universe
• Quant Engine Kernel    • RET-02 Audit UI  • Risk-Free Feeds  • Scenario Engine
• Governance Framework   • Data Quality UX  • Backtesting      • Forecasting
• Phase 2H Spec Freeze   • Honest Epistemic • Validation Study • Multi-Asset Class
                           Presentation     • AI Explanations
```

### Completed
- Core multi-tier architecture, containerization, and repository layout.
- PostgreSQL bitemporal schema with Flyway migrations V1–V7.
- Standalone Python quantitative engine kernel with 301 automated unit tests.
- Spring Boot backend with JPA persistence, observation resolution, and REST contracts.
- AMFI real daily and historical NAV ingestion with SHA-256 provenance tracking.
- Formal methodology governance framework separating candidate, validated, and approved tiers.
- Phase 2H quantitative methodology specification freezing the 30-metric candidate inventory.

### Now (Phase 2I)
- Stakeholder-facing documentation and transparent project communication.
- Initial working investor website connected to real backend APIs.
- Fund discovery catalog and fund detail views with real database integration.
- RET-02 Simple Period Return analysis audit view with complete input observation lineage.
- Visible 6-dimensional data quality presentation.
- Strict epistemic boundary enforcement (no fake metrics, ratings, or advice).

### Next
- Implementation of remaining 29 candidate metrics in the quantitative engine and backend.
- Official broad-market benchmark data ingestion (NIFTY 50 TRI, BSE 500 TRI, etc.).
- Risk-free rate series ingestion (FBIL 91-day T-bill yields).
- Mutual fund monthly portfolio holdings ingestion (SEBI monthly portfolio disclosures).
- Empirical backtesting across multi-year market cycles and vendor data reconciliation.
- Methodology validation and review committee approval process.
- Explainable AI layer for natural-language analysis interpretation.

### Later
- Production cloud infrastructure deployment (AWS / GCP) with automated CI/CD.
- Complete coverage of all Indian mutual fund categories (Equity, Hybrid, Debt, Solution-Oriented).
- Multi-fund portfolio aggregation and portfolio-level risk attribution.
- Interactive macroeconomic scenario analysis and stress-testing engine.
- Expansion to additional asset classes (ETFs, Equities, Fixed Income).

---

## Data Quality Taxonomy

YUKIRA evaluates every input observation and metric calculation across a standardized **six-dimensional data quality taxonomy**:

| Dimension | Primary States | Meaning in Investor Context |
| :--- | :--- | :--- |
| **1. Quality** | `VALID`, `SUSPICIOUS`, `INVALID` | Statistical and physical validity of the observation value (e.g. non-negative NAV). |
| **2. Verification** | `VERIFIED`, `UNVERIFIED`, `CONFLICTING` | Level of external corroboration against authoritative source feeds. |
| **3. Revision** | `ORIGINAL`, `REVISED`, `SUPERSEDED` | Track record of retroactively modified values published by data providers. |
| **4. Freshness** | `CURRENT`, `STALE`, `HISTORICAL_BACKFILL` | Temporal recency of the observation relative to market reporting schedules. |
| **5. Presence** | `PRESENT`, `MISSING`, `INTERPOLATED`, `ZERO_REPORTED` | Whether data was reported directly or required deterministic substitution. |
| **6. Integrity** | `UNCOMPROMISED`, `CORRUPTED`, `HASH_MISMATCH` | Cryptographic data integrity from raw ingest through calculation execution. |

---

## Running Locally

### Prerequisites
- **Java 21+**
- **Python 3.12+**
- **Node.js 20+** & **npm**
- **PostgreSQL 17** (or Docker)

### 1. Database
```bash
# Start PostgreSQL via Docker Compose
docker compose up -d postgres
```

### 2. Backend Service
```bash
cd backend
./mvnw clean spring-boot:run
# Service available at http://localhost:8080
```

### 3. Quantitative Engine
```bash
cd quant-engine
python -m venv .venv
.venv/Scripts/activate     # Windows (.venv\Scripts\activate)
pip install -r requirements.txt
python -m pytest tests/    # Run test suite
```

### 4. Frontend Application
```bash
cd frontend
npm install
npm run dev
# Application accessible at http://localhost:3000
```

---

## Verification & Test Execution

All three tiers maintain independent, automated test suites:

```bash
# Quantitative Engine (301 tests)
cd quant-engine && .venv\Scripts\pytest

# Backend Service (54 tests)
cd backend && ./mvnw test

# Frontend Application (46 unit tests, lint, and production build)
cd frontend && npm test && npm run lint && npm run build
```

---

## License & Epistemic Disclaimer

Copyright © 2026 Project YUKIRA. All rights reserved.

**Regulatory Disclaimer:** YUKIRA is an experimental quantitative investment intelligence platform. Nothing displayed within this software, documentation, or associated web applications constitutes financial advice, investment recommendations, securities endorsements, or commercial solicitations under SEBI (Investment Advisers) Regulations or any international securities regulatory framework.
