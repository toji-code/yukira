# Engineering Development Rules & Quality Standards

> **Document Type:** Mandatory Engineering Operating Rules  
> **Status:** Active Architectural Guardrails  
> **Rule:** Every developer and AI coding agent working on Project YUKIRA must strictly comply with these rules.  

---

## 1. Core Engineering Directives

### 1.1 Correctness Before Breadth
- A single mathematically verified, provenance-backed metric is infinitely superior to thirty speculative, untested indicators.
- Never rush to wire new metrics or endpoints without full deterministic test coverage and empirical validation.
- If a calculation cannot be proven correct against authoritative external benchmarks, it must remain marked as an unapproved candidate or be withheld from production.

### 1.2 Deterministic Financial Calculations
- All financial metrics must be computed deterministically in code (pure Java/Python functions).
- Calculations must never rely on platform-dependent approximations or non-deterministic algorithms.
- **Zero Frontend Calculations:** The frontend must never perform financial math, trailing return compounding, or annualization. The frontend exclusively renders what the backend provides.

### 1.3 Zero Fabricated Financial Data
- Never insert fake, synthetic, mock, or estimated financial observations into production or pilot database tables.
- All live pilot data must originate from real, verifiable public source feeds (e.g. AMFI India portal).
- If source data is missing for a given date, the system must explicitly record a `MISSING` presence state or raise a validation issue. Never interpolate or fill gaps silently unless governed by an approved methodology.

---

## 2. Provenance & Point-in-Time Discipline

### 2.1 Cryptographic Raw-Source Lineage
- Every ingested financial observation must trace directly to a `source_artifact` record.
- Every `source_artifact` must record:
  - Source URI (e.g. exact AMFI query URL)
  - Retrieval timestamp (`retrieval_timestamp`)
  - Cryptographic SHA-256 digest of the raw byte payload
  - Exact byte size
- Any observation lacking a verified source artifact foreign key is considered invalid.

### 2.2 Point-in-Time (PIT) Correctness & Zero Look-Ahead Bias
- Bitemporal storage (`effective_date` vs. `availability_time`) must be enforced on all financial time series.
- Every analytical calculation must specify both an `analysis_cutoff` and a `knowledge_cutoff`.
- No calculation may consume observations published or ingested after the specified `knowledge_cutoff`.
- Queries must never query "latest available" data without applying an explicit knowledge-cutoff rule.

### 2.3 Revision Handling
- Financial data providers retroactively revise published NAVs and disclosures.
- Never overwrite historical observation records merely because a newer file contains a different value.
- Store revisions as append-only rows with incremented `revision_seq`.
- Mark superseded observations explicitly (`is_latest_revision = false`, `revision_status = SUPERSEDED`).

---

## 3. Database Safety & Migration Guardrails

### 3.1 Flyway as Schema Authority
- PostgreSQL schema changes are governed strictly and exclusively by versioned Flyway migrations (`backend/src/main/resources/db/migration/`).
- Migrations must be strictly additive, idempotent, and backwards-compatible.

### 3.2 Hibernate Validation
- Backend application configuration MUST maintain `spring.jpa.hibernate.ddl-auto=validate`.
- Hibernate is strictly prohibited from altering, updating, or generating database tables.

### 3.3 Zero Destructive Operations
- **STRICTLY PROHIBITED:**
  - `DELETE FROM` on financial or calculation tables
  - `TRUNCATE`
  - `DROP TABLE`
  - `DROP SCHEMA`
  - Destructive data cleanup migrations
- Never create a cleanup migration that deletes historical calculation runs, metric results, or observations.
- Use append-only corrections, soft-deletes, or status flags to retire erroneous or quarantined records.

### 3.4 Seed Architecture
- `PilotBootstrapService` is the authorized, non-destructive mechanism for seeding master data and the canonical pilot in development environments.
- Never create ad-hoc SQL seed scripts as competing sources of truth.

---

## 4. Methodology Governance & Product Boundaries

### 4.1 Phase 2H Freeze
- The Phase 2H quantitative methodology specification in [`phase2h_quantitative_methodology.md`](file:///phase2h_quantitative_methodology.md) is **FROZEN**.
- Developers and AI agents must not alter, rewrite, or extend formulas in this document without formal authorization from the project owner.

### 4.2 Three-Tier Governance Lifecycle
- **CANDIDATE:** Implemented algorithm passing unit tests. Operates with visible disclaimers; not approved for investor decision support.
- **VALIDATED:** Methodology tested empirically across multi-cycle historical market regimes and vendor datasets.
- **APPROVED:** Formally authorized by the review committee for live production decision support.
- Writing code or specifications does not make a methodology validated. Never present candidate metrics as authoritative advice.

### 4.3 Zero Scoring & Recommendation Bias
- YUKIRA does not assign overall fund ratings, scores, or star badges.
- YUKIRA does not generate BUY, HOLD, REDUCE, or AVOID investment recommendations.
- Data quality dimensions (`VALID`, `SUSPICIOUS`, `INVALID`) represent epistemic states; they must never be converted into automated investment penalties or trading decisions.

---

## 5. Testing & Verification Standards

### 5.1 Test Suite Preservation
- Existing test suites must continue passing after any code modification.
- Never delete, comment out, or weaken assertions merely to make new changes pass.
- Minimum baseline test counts:
  - **Quant Engine:** 301 passing unit tests (`pytest tests/`)
  - **Backend Service:** 54 passing tests (`./mvnw test`)
  - **Frontend Application:** 46 passing unit tests (`npm test`), clean lint (`npm run lint`), successful production build (`npm run build`)

### 5.2 Deterministic Testing
- Tests must be reproducible and deterministic.
- Never rely on network availability in unit tests; use preserved raw byte artifacts or recorded fixtures for offline verification.
- Separate live integration tests using explicit tags (e.g. `@Tag("external-integration")`).

---

## 6. Git, Security & Secrets Discipline

### 6.1 Git Discipline
- **DO NOT Commit Automatically:** Only commit when the project owner explicitly instructs: `"Commit Phase 2X"`.
- **DO NOT Push Automatically:** Never run `git push` unless explicitly ordered by the user.
- **Maintain Clean Trees:** Never leave temporary test dumps, unverified scratch files, or editor artifacts in tracked directories.
- **Check Diffs:** Always inspect `git status --short` and `git diff --check` before completing any task.

### 6.2 Secrets & Credentials
- Never hardcode passwords, API keys, database credentials, or private certificates into source code or Git history.
- Use environment variables or local `.env` files (properly ignored in `.gitignore`).

---

## 7. Stop and Report Criteria

An AI coding agent or engineer **MUST STOP AND REPORT** immediately if any of the following conditions occur:

1. **Destructive Operation Encountered:** A proposed change would require deleting or truncating persisted financial observations.
2. **Data-Quality Conflict:** An ingested dataset contradicts verified raw cryptographic evidence (e.g. the January 2024 intermediate NAV discrepancy).
3. **Contaminated Identifiers:** Any script or test attempts to reintroduce the rejected regular plan identifiers (`119062` / `INF179K01BE2`) as the canonical pilot.
4. **Methodology Ambiguity:** A formula implementation differs from the frozen Phase 2H specification.
5. **Schema Conflict:** Hibernate entity definitions deviate from Flyway migrations causing startup validation failure.
6. **Network Unavailability:** Live source feeds are unreachable and no local cryptographic artifact is preserved.

> *"When in doubt, stop, report the facts, and request clarification. Never invent a resolution."*
