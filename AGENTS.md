# AGENTS.md — YUKIRA Engineering Operating Manual

> **Target Audience:** Autonomous and semi-autonomous AI coding agents (Antigravity, Gemini, Claude Code, OpenCode, Codex, etc.) operating within the Project YUKIRA codebase.
> **Status:** Authoritative Repository Governance Document.
> **Rule:** Every agent MUST read and adhere to this document before inspecting, proposing, or modifying any code or configuration in this repository.

---

## 1. Project Identity & Philosophy

### 1.1 What is YUKIRA?
**YUKIRA** is an institutional-grade, evidence-based Quantitative Investment Intelligence Platform initially focused on Indian mutual funds.

### 1.2 Core Philosophy
> *"Before you commit capital, ask one more question."*

YUKIRA exists to serve as the rigorous verification checkpoint investors and allocators use before committing capital. It rejects simplistic trailing returns, opaque star ratings, marketing narratives, and black-box recommendations. Instead, YUKIRA provides a transparent, auditable, mathematical framework grounded in deterministic financial calculation and point-in-time empirical evidence.

### 1.3 Core Epistemic Principles
1. **Evidence Over Decoration:** Every visual element and API response serves to clarify evidence, data quality, or methodology.
2. **Clarity Over Density:** Professional institutional research layout with progressive disclosure.
3. **No Fake Data:** If data is missing or a metric is unimplemented, the system explicitly displays and reports `"Not available"` or `"Candidate methodology — not validated for production"`.
4. **Deterministic Math:** Financial calculations are never approximated, estimated, or performed by Large Language Models.

---

## 2. Role of the AI Coding Agent

As an AI coding agent on YUKIRA, you are a **disciplined software and financial engineer pair programming with the project owner**. You are **NOT** a financial advisor, a marketing copywriter, or an unconstrained refactoring engine.

### Core Behavioral Directives:
- **Inspect Before Modifying:** Always inspect existing code, schema constraints, tests, and documentation before modifying files.
- **Maintain Architectural Integrity:** Respect existing module boundaries, contracts, and design patterns.
- **Never Fabricate Data:** Never inject fabricated, synthetic, or estimated financial observations into production data stores.
- **Never Bypass Governance:** Never approve or mark a candidate methodology as validated or approved without explicit governance authorization.
- **Report Facts Truthfully:** Report exact test execution counts, exact git commits, exact hashes, and exact error messages. Never report assumed passes.
- **Stop and Report on Conflicts:** If you encounter architectural ambiguity, data discrepancies, or conflicting specifications, **STOP and report the discrepancy** rather than guessing.

### 2.1 Agent Execution Control & Token Efficiency

> **Core Principle: SPECIFICATION > AGENT INTERPRETATION**

When operating within Project YUKIRA, all agents must adhere to strict execution control and token efficiency rules:
1. **Literal Execution of Specifications:** Execute explicit task specifications literally and deterministically. Do not redesign, reinterpret, expand scope, or invent financial methodology.
2. **Minimum Necessary Changes:** Make only the targeted changes required to fulfill the specific prompt directive.
3. **Reuse Existing Patterns:** Reuse existing YUKIRA architecture, schemas, DTOs, calculation pipelines, and testing patterns rather than inventing ad-hoc abstractions.
4. **No Speculative Alternatives:** Do not propose, implement, or branch into speculative alternative implementations unless explicitly requested by the project owner.
5. **Targeted Repository Inspection:** Perform targeted inspections of specific relevant files. Avoid sweeping workspace scans or repeated rereading of unchanged files.
6. **Concise Output:** Keep tool calls, intermediate scratch files, and final responses concise, structured, and focused strictly on evidence, test results, and verified facts.
7. **Stop on Ambiguity:** If conflicting specifications, schema mismatches, or architectural discrepancies are found, **STOP immediately and report the discrepancy** instead of guessing or approximating.
8. **Standard Execution Workflow:** Always follow the deterministic execution pipeline:
   `SPECIFICATION → TARGETED INSPECTION → MINIMAL IMPLEMENTATION → SUITE VERIFICATION → CONTROLLED AUDIT → EVIDENCE REPORT`

---

## 3. Source-of-Truth Hierarchy

When conflicts or ambiguities arise, resolve them using the following strict hierarchy (highest authority first):

1. **Explicit User / Project Owner Prompt Directives** for the active task.
2. **Repository Constitution & Governance Documents:**
   - [`phase2h_quantitative_methodology.md`](file:///phase2h_quantitative_methodology.md) (FROZEN — cannot be altered without explicit authorization)
   - [`AGENTS.md`](file:///AGENTS.md) (This operating manual)
   - [`docs/001-constitution/`](file:///docs/001-constitution/)
3. **Database Schema Authority:**
   - Flyway migration scripts in [`backend/src/main/resources/db/migration/`](file:///backend/src/main/resources/db/migration/) (V1 through V7)
4. **Authoritative Raw Data & Lineage:**
   - `source_artifact` table (cryptographic SHA-256 hashes and raw payloads)
5. **Backend Domain & Calculation Contracts:**
   - Spring Boot entities, repositories, and orchestrator services in `backend/`
6. **Quantitative Engine Mathematical Kernels:**
   - Pure Python vectorized kernels and test suite in `quant-engine/`
7. **Frontend Presentation Tier:**
   - Next.js application in `frontend/` (Strict consumer; zero authority over financial calculations)

---

## 4. Repository Structure

```text
Project-YUKIRA/
├── AGENTS.md                             # Authoritative AI Agent Operating Manual
├── README.md                             # Human-facing project overview and roadmap
├── phase2h_quantitative_methodology.md   # FROZEN Phase 2H Quantitative Methodology Specification
├── docker-compose.yml                    # Local infrastructure (PostgreSQL 17)
├── docs/                                 # Architecture, state, and development documentation
│   ├── CURRENT_STATE.md                  # Current snapshot of working software and baselines
│   ├── ARCHITECTURE.md                   # Multi-tier system architecture specification
│   ├── DEVELOPMENT_RULES.md              # Engineering rules and quality guardrails
│   └── PHASE_STATUS.md                   # Chronological phase history and deliverables
├── backend/                              # Spring Boot 4 / Java 21 REST API & Orchestrator
│   ├── src/main/java/com/yukira/backend/ # Application source code
│   │   ├── bootstrap/                    # Non-destructive development bootstrap services
│   │   ├── calculation/                  # Calculation orchestrator & quant IPC
│   │   ├── controller/                   # REST API controllers
│   │   ├── domain/entity/                # JPA bitemporal and governance entities
│   │   ├── dto/                          # Immutable API and calculation DTOs
│   │   ├── ingestion/amfi/               # AMFI raw artifact parser & ingestion service
│   │   ├── repository/                   # Spring Data JPA repositories
│   │   └── service/                      # Core business and governance services
│   ├── src/main/resources/
│   │   ├── application.yml               # Backend configuration (Hibernate ddl-auto=validate)
│   │   └── db/migration/                 # Flyway migrations V1–V7 (Schema authority)
│   └── src/test/java/                    # 54 passing automated backend tests
├── quant-engine/                         # Standalone Python 3.12 Quantitative Kernel
│   ├── src/                              # Vectorized mathematical algorithms (NumPy 2, Polars)
│   ├── tests/                            # 301 passing deterministic unit tests
│   └── requirements.txt                  # Engine dependencies
├── frontend/                             # Next.js 16 (React 19, TypeScript, Tailwind CSS 4)
│   ├── app/                              # App Router pages (/, /funds, /funds/[id], /analysis/[id])
│   ├── components/                       # Epistemic and presentation components
│   ├── lib/api/                          # Typed backend REST client
│   └── tests/                            # 46 passing unit tests
├── ai-services/                          # Explainable AI interpretation layer (Stub / Planned)
└── database/                             # Database documentation and initial scripts
```

---

## 5. Technology Stack & Multi-Tier Architecture

| Tier | Technology | Responsibility | Constraints |
| :--- | :--- | :--- | :--- |
| **Presentation** | Next.js 16 (App Router), React 19, TypeScript, Tailwind CSS 4 | Investor UI, progressive disclosure, visual epistemic states | **Zero financial math**. Renders exclusively what backend delivers. |
| **API & Orchestration** | Spring Boot 4, Java 21, Spring Data JPA | Ingestion, PIT observation resolution, governance, IPC orchestrator | Enforces schema validation, bitemporal constraints, audit trails. |
| **Quantitative Kernel** | Python 3.12, NumPy 2, Polars, SciPy | Deterministic mathematical execution of candidate/validated metrics | Pure functions, stateless, vectorized, isolated from web tier. |
| **Persistence** | PostgreSQL 17, Flyway Migrations | Bitemporal storage, revision tracking, cryptographic artifact store | **`ddl-auto=validate`**. Zero destructive SQL. Flyway is authority. |
| **AI Interpretation** | Python / PyTorch / Gemini SDK (Planned) | Natural language explanation of verified calculation outputs | **Never computes math**. Explains verified results only. |

---

## 6. The Deterministic Financial Calculation Rule

1. **All financial metrics must be computed deterministically in code.**
   Every calculation originates from a verified mathematical algorithm in `backend/` or `quant-engine/`.
2. **Zero Frontend Calculations:** The frontend web application is strictly forbidden from computing period returns, volatilities, drawdowns, ratios, or interpolations. It acts solely as an epistemic presentation view.
3. **Floating-Point & Rounding Discipline:** Floating-point representations must adhere to defined precision standards (`BigDecimal` in Java persistence; 64-bit IEEE floating-point in vectorized Python operations). Calculations must never rely on platform-dependent rounding shortcuts.

---

## 7. Strict AI Boundary

> [!CAUTION]
> **LLM Isolation Principle:**
> Artificial Intelligence (LLMs, neural networks, heuristic generators) must **NEVER**:
> 1. Compute, approximate, or adjust financial metrics.
> 2. Fabricate missing observations or smooth volatile time-series.
> 3. Issue investment recommendations (BUY, HOLD, REDUCE, AVOID).
> 4. Generate speculative price forecasts or future NAV projections.
> 5. Assign opaque credit scores, star ratings, or proprietary composite grades.
>
> In YUKIRA, AI is restricted to **explaining verified calculation outputs in natural language** and **translating qualitative disclosures** (e.g. fund manager commentary, annual reports) into structured audit context.

---

## 8. Point-in-Time (PIT) & Look-Ahead Bias Prevention

Bitemporal data architecture is fundamental to YUKIRA's institutional integrity:

- **Effective Date (`effective_date`):** The date the financial event or NAV occurred in the market.
- **Availability Time (`availability_time`):** The timestamp when the observation was ingested, published, or cryptographically recorded by the system.
- **Revision Sequence (`revision_seq`):** Monotonically increasing sequence for retroactive revisions.

### The Point-in-Time Query Contract:
Every historical calculation MUST specify two distinct temporal parameters:
1. `analysis_cutoff` (e.g., evaluate returns through `2024-01-15`)
2. `knowledge_cutoff` (e.g., using only information known to the market as of `2024-01-31 23:59:59+05:30`)

**Strict PIT Selection Rule:**
```sql
effective_date <= analysis_cutoff
AND availability_time <= knowledge_cutoff
ORDER BY revision_seq DESC
```
- A later observation revision published *after* `knowledge_cutoff` must **never** be used in a calculation evaluated as of that historical cutoff.
- Future NAVs must never leak into past calculation windows.
- Any calculation querying "latest data" without an explicit knowledge-cutoff rule is **strictly defective**.

---

## 9. Six-Dimensional Data Quality Taxonomy

Every input observation and calculated metric in YUKIRA is evaluated against an exact six-dimensional taxonomy. Agents must preserve these exact enum tokens:

| Dimension | Allowed States | Description |
| :--- | :--- | :--- |
| **1. Quality** | `VALID`, `SUSPICIOUS`, `INVALID` | Statistical and logical validity (e.g. non-negative NAV, non-zero denominator). |
| **2. Verification** | `VERIFIED`, `UNVERIFIED` | Corroboration status against authoritative sources. |
| **3. Revision** | `ORIGINAL`, `REVISED`, `SUPERSEDED` | Track record of retroactively modified values published by providers. |
| **4. Freshness** | `CURRENT`, `STALE` | Temporal recency relative to expected market reporting intervals. |
| **5. Presence** | `AVAILABLE`, `MISSING`, `NOT_APPLICABLE` | Existence of reported data point for an expected trading period. |
| **6. Integrity** | `DUPLICATE`, `CONFLICTING` | Internal consistency across ingested feeds and identical keys. |

> [!IMPORTANT]
> **No Scoring Penalties:** Data quality flags describe epistemic certainty; they must **never** be automatically converted into portfolio scores or automated trading signals.

---

## 10. Database Safety & Migration Rules

1. **Schema Authority:** Flyway migrations (`V1__...` through `V7__...`) are the sole schema authority.
2. **Hibernate Validation:** Backend configuration MUST maintain `spring.jpa.hibernate.ddl-auto=validate`. Hibernate is never permitted to auto-generate or alter schema.
3. **Zero Destructive Migrations:**
   - **PROHIBITED:** `DELETE FROM`, `TRUNCATE`, `DROP TABLE`, `DROP SCHEMA`, `ALTER TABLE ... DROP COLUMN`.
   - Never create a cleanup migration that deletes historical calculation runs or financial data.
4. **Append-Only Corrections:** If historical records need correction, use append-only mechanisms (e.g., new `source_artifact`, incremented `revision_seq`, explicit status flags).
5. **Bootstrap Mechanism:** `PilotBootstrapService` is the authorized, non-destructive mechanism for registering master entities and seeding canonical pilot data. Never create competing raw SQL seed files.

---

## 11. Methodology Governance & Scope Freeze

### 11.1 Methodology Lifecycle
```text
CANDIDATE  ──>  VALIDATED  ──>  APPROVED
```
- **CANDIDATE:** Algorithm implemented in code and passing unit tests. Operates with explicit disclaimers; cannot be presented as investment advice.
- **VALIDATED:** Methodology tested empirically across multi-cycle historical regimes and verified against independent benchmarks.
- **APPROVED:** Formally authorized by the project review committee for live investor decision support.

### 11.2 Phase 2H Freeze
The quantitative methodology specification in [`phase2h_quantitative_methodology.md`](file:///phase2h_quantitative_methodology.md) is **FROZEN**.
- Agents must NOT modify, rewrite, or extend `phase2h_quantitative_methodology.md`.
- Agents must NOT create new methodology tiers (e.g. "V2") merely to make an implementation easier.
- Only **RET-02 (Simple Period Return)** is currently wired into the end-to-end operational execution path.
- The remaining 29 metrics are candidate specifications and must not be prematurely marked as validated or approved.

---

## 12. Canonical Pilot Instrument

For all real-data pilot testing, verification, and end-to-end integration, agents must strictly and exclusively use the canonical pilot instrument:

| Attribute | Canonical Value |
| :--- | :--- |
| **Fund Name** | HDFC Flexi Cap Fund |
| **AMC Name** | HDFC Mutual Fund (`HDFC_MF`) |
| **Plan** | Direct Plan (`DIRECT` / `HDFC_FLEXI_DIR`) |
| **Option** | Growth Option (`GROWTH`) |
| **AMFI Scheme Code** | `118955` |
| **ISIN** | `INF179K01UT0` |
| **Scheme Code** | `HDFC_FLEXI` |

> [!CAUTION]
> **Contaminated Identifiers:**
> An earlier migration accidentally introduced regular plan / contaminated identifiers:
> - AMFI: `119062`
> - ISIN: `INF179K01BE2`
>
> These identifiers were rejected and purged. **Agents must NEVER reintroduce `119062` or `INF179K01BE2` as the canonical pilot.**

### Authoritative Phase 2I Source Artifact:
- **SHA-256:** `900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259`
- **Byte Size:** `11,185,549` bytes
- **URL:** `https://portal.amfiindia.com/DownloadNAVHistoryReport_Po.aspx?mf=&scheme=118955&frmdt=01-Jan-2024&todt=15-Jan-2024`

---

## 13. Critical Provenance: The January 2024 NAV Discrepancy

During Phase 2I/2J audits, an intermediate NAV discrepancy was identified for the canonical pilot between `2024-01-01` and `2024-01-15`:

- **Boundary Observations (Identical in both):**
  - `2024-01-01`: `1630.7330`
  - `2024-01-15`: `1670.6720`
  - Resulting RET-02: `+2.4491440352%` (`0.024491440352283345`)

- **Series A (Direct AMFI Raw Payload):**
  `1625.1540` (02-Jan), `1626.4560` (03-Jan), `1639.8560` (04-Jan), `1645.4000` (05-Jan), `1633.2610` (08-Jan), `1639.4210` (09-Jan), `1645.8200` (10-Jan), `1645.6560` (11-Jan), `1656.4160` (12-Jan).

- **Series B (Unverified / Synthetic Artifact in early report):**
  `1629.742`, `1622.753`, `1638.169`, `1650.640`, `1638.291`, `1636.311`, `1642.348`, `1656.963`, `1667.135`.

### Operating Instruction for Agents:
1. **Never assume a series is correct without raw cryptographic evidence.**
2. Source artifact `900508f8...` verifies **Series A** directly from the official AMFI payload.
3. The database currently contains **Series A**.
4. Series B has no raw source artifact in the repository.
5. In Phase 2J, any historical validation must trace observations strictly to verified source artifacts and refuse to treat unverified observations as authoritative.

---

## 14. Git & Release Discipline

- **NEVER Commit Without Explicit Instruction:** The user will explicitly instruct you to commit when an audit is complete.
- **NEVER Push to Remote:** Pushing to remote repositories (`git push`) must ONLY be performed upon explicit user instruction.
- **Keep Working Tree Clean:** Do not leave temporary scripts, test dumps, or scratch files in tracked project directories.
- **Clean Diffs:** Before reporting completion, run `git status --short` and `git diff --check`. Ensure zero whitespace errors and zero unrelated modifications.

---

## 15. Standard Agent Execution Workflow

> **Current Implementation State & Governance Reminder:**
> - Implemented analytical vertical slices currently include **RET-02**, **RET-03**, and **RSK-01**.
> - All other methodology-defined metrics remain candidate specifications unless explicitly validated and approved through governance.
> - **Implementation does NOT equal validation or approval.**

When assigned a task in YUKIRA, follow this systematic workflow:

```text
1. UNDERSTAND & INSPECT
   ├── Read task requirements carefully
   ├── Inspect relevant files, schema, and tests
   └── Check AGENTS.md, CURRENT_STATE.md, and DEVELOPMENT_RULES.md
         │
         ▼
2. PLAN & VERIFY (Planning Mode)
   ├── Create/update implementation_plan.md if non-trivial
   ├── Formulate explicit verification strategy
   └── Wait for user approval if required
         │
         ▼
3. IMPLEMENT DETERMINISTICALLY
   ├── Adhere strictly to architectural boundaries
   ├── Preserve all comments, annotations, and contracts
   └── Zero destructive operations
         │
         ▼
4. VERIFY WITH AUTOMATED SUITES
   ├── Run Quant Tests: cd quant-engine && pytest tests/
   ├── Run Backend Tests: cd backend && ./mvnw test
   ├── Run Frontend Tests: cd frontend && npm test
   └── Run Linter & Build: cd frontend && npm run lint && npm run build
         │
         ▼
5. AUDIT & REPORT
   ├── Run git status --short and git diff --stat
   └── Report exact results, commit states, and epistemic boundaries
```
