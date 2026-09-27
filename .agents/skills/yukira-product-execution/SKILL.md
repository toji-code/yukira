---
name: yukira-product-execution
description: >-
  Operational product execution workflow for Project YUKIRA. Guides building real investor-facing functionality
  through complete vertical slices (investor problem -> real market data -> deterministic quant kernel -> Spring Boot API -> Next.js UI -> real-data verification).
  Use whenever implementing features, analytical workflows, scoring models, UI components, or reviewing working investor capabilities in YUKIRA.
---

# YUKIRA Product Execution Skill

> **Role & Purpose:** Teaches AI coding agents and engineers **how to work on YUKIRA**.  
> **Complements:** `AGENTS.md` (Constitutional governance & engineering rules).  
> **Focus:** Translating quantitative intelligence into working investor capabilities.

---

## 1. Product First: Foundation → Productization

YUKIRA has transitioned from foundational infrastructure to **productization**. Do not return to an architecture-first development loop.

### The Primary Execution Loop
```text
Investor Problem
      ↓
Minimum Useful Feature
      ↓
Real Market Data
      ↓
Quant Kernel & Backend Implementation
      ↓
API Contract
      ↓
Investor-Facing UI
      ↓
Real-Data Verification
      ↓
Product Review
      ↓
Identify Weaknesses & Gaps
      ↓
Fix / Refactor Where Justified
      ↓
Regression Test
      ↓
Next Feature
```

### The Deciding Question
When deciding what to build, ask:
> **"What useful thing can an investor do after this change?"**

Do not ask:
> *"What architecture should we design next?"*

Prioritize working investor value over speculative abstractions.

---

## 2. Complete Vertical Slices

Prefer complete, working vertical slices over isolated infrastructure.

### The Vertical Slice Pipeline
Every feature, where applicable, must travel end-to-end:
```text
Real Source Data (AMFI / NSE / FBIL)
      ↓
Point-in-Time Validation & Storage
      ↓
Deterministic Calculation (Quant Engine)
      ↓
Backend Orchestration & REST API
      ↓
Next.js Investor UI
      ↓
Plain-Language Investor Explanation
      ↓
Auditable Evidence & Cryptographic Provenance
```

### Hard Product Boundary
Do **not** consider adding only a database table, service, methodology document, test harness, or abstract interface as delivering a product feature—unless that component is directly required by the active feature being delivered.

---

## 3. Product Direction & Capability Matrix

YUKIRA delivers institutional quantitative intelligence for Indian mutual fund allocators. Build capabilities incrementally through minimal, useful vertical slices:

- **Fund Discovery & Catalog:** Instant scheme search, fund family filtering, plan/option resolution.
- **Fund Profiles:** Master details, NAV history, asset class metadata, data quality health.
- **Screening & Comparison:** Multi-fund head-to-head comparison across empirical horizons.
- **Performance Analysis:** Compound annual growth (CAGR), rolling returns, benchmark excess returns.
- **Risk Analysis:** Downside semideviation, maximum drawdown, drawdown duration, Ulcer Index, VaR/CVaR.
- **Risk-Adjusted Performance:** Sharpe, Sortino, Treynor, Information Ratio, Jensen's Alpha.
- **Benchmark & Market Sensitivity:** OLS Beta, Downside Beta, Tracking Error, Up/Down Capture.
- **Portfolio & Holdings Analysis:** Concentration, sector exposure, weight turnover, liquidity risk.
- **Manager & Strategy Skill:** Tenure tracking, factor attribution, regime persistence.
- **Cost & Fee Drag:** Expense ratio impact, tracking difference, cash drag.
- **Stress & Scenario Analysis:** Historical market crisis replays, rate shock simulations.
- **AI Interpretation & Risk Audit:** Evidence-backed synthesis, risk identification, anomaly alerts.
- **Watchlists & Alerts:** Tracking funds, data quality degradation notices, manager changes.
- **Eventual Decision Support:** Transparent, multi-dimensional YUKIRA scoring.

Do **not** build all capabilities at once. Build the smallest useful vertical slice, evaluate actual usage, and iterate.

---

## 4. Scoring Strategy & Long-Term Objectives

Scoring and decision-support constitute YUKIRA's eventual core differentiator. The future scoring architecture will evaluate distinct, modular dimensions:

- **Quality Score:** Portfolio construction integrity, concentration discipline, process consistency.
- **Return Score:** Multi-cycle rolling performance and hurdle-rate outperformance.
- **Risk Score:** Tail risk, drawdown depth, recovery speed, and capital preservation.
- **Risk-Adjusted Score:** Efficiency of excess returns per unit of total, downside, and systematic risk.
- **Manager Score:** Tenure stability, regime persistence, and factor-isolated alpha.
- **Cost Score:** Total expense ratio competitiveness and transaction drag.
- **Benchmark Relative Score:** Consistency against style-appropriate total return benchmarks.
- **Resilience Score:** Capital defense during historical market drawdowns and liquidity shocks.
- **Sustainability Score:** Environmental, social, and governance transparency and stewardship consistency.
- **Investor Fit Score:** Alignment with investor horizon, risk capacity, and drawdown tolerance.
- **Opportunity Score:** Compelling valuation, factor discount, or asymmetric risk-reward conditions.
- **Confidence Score:** Epistemic certainty based on data freshness, history length, and source reliability.

### The Scoring Guardrail
> [!CAUTION]
> **No Premature Scoring:**
> - Do **NOT** invent scoring formulas, ad-hoc weights, or composite grades before underlying analytical metrics are empirically validated.
> - Never treat correlated metrics as independent votes.
> - Never manufacture false precision (e.g., scoring out of 100 based on sparse observations).
> - Build reliable, verified analytical primitives first.

---

## 5. Quantitative Integrity & AI Boundaries

1. **Deterministic Calculations Only:**
   - All financial numbers originate from pure, deterministic, reproducible code in `backend/` or `quant-engine/`.
   - Floating-point discipline: `BigDecimal` for Java persistence; 64-bit IEEE float for vectorized Python kernels.
   - Zero math in the frontend: Next.js renders exclusively what the backend API delivers.

2. **Strict AI Isolation Principle:**
   - **AI is an interpretation layer, not a calculation engine.**
   - AI may: explain verified metrics, synthesize disclosures, highlight anomalies, summarize evidence, and formulate investigative questions.
   - AI must **NEVER**:
     - Compute or estimate financial metrics.
     - Invent financial facts, returns, benchmark values, or risk-free rates.
     - Alter or smooth quantitative outputs.
     - Override quant engine calculations.
     - Issue investment recommendations (BUY, HOLD, REDUCE, AVOID).
     - Hide uncertainty or missing observations.

3. **Zero Fabricated Financial Data:**
   - Never inject synthetic, interpolated, or estimated observations into production datasets.
   - Missing data must be explicitly reported as `MISSING` or `Not available`.

---

## 6. Point-in-Time (PIT) & Data Lineage

Every analytical computation must eliminate look-ahead bias and maintain cryptographic traceability:

- **Bitemporal Dimensions:** Always respect `effective_date` (market event) vs. `availability_time` (system ingestion).
- **Dual Cutoffs:** Require `analysis_cutoff` and `knowledge_cutoff` on all historical analyses.
- **Zero Look-Ahead Bias:** Future observations published after `knowledge_cutoff` must never enter historical windows.
- **Revision Discipline:** Track provider revisions monotonically via `revision_seq`; never overwrite historical records.
- **Source Lineage:** Trace every observation to its originating `source_artifact` (URI, retrieval timestamp, byte length, SHA-256 digest).
- **Data Quality Visibility:** Evaluate inputs against YUKIRA's 6-dimensional taxonomy (`Quality`, `Verification`, `Revision`, `Freshness`, `Presence`, `Integrity`). Never hide data quality defects.

---

## 7. Progressive Disclosure & Evidence Presentation

Investor trust is earned through transparency. The UI must progressively disclose evidence across six layers:

```text
Layer 1: What YUKIRA Sees   ──> Primary observation, value, or calculated metric
Layer 2: Why                ──> Analytical explanation of what drove the result
Layer 3: Evidence           ──> Direct underlying data, observation counts, date boundaries
Layer 4: Risks              ──> Downside exposures, market sensitivity, factor concentrations
Layer 5: Limitations        ──> Data quality flags, coverage gaps, candidate caveats
Layer 6: What to Investigate──> Targeted due-diligence questions for the allocator
```

Where applicable, expose:
- Source entity and artifact SHA-256 digest
- Observation window start and end dates
- Calculation run ID and methodology version
- Applied benchmark and risk-free series
- Mathematical assumptions and sample size ($N$)

---

## 8. Pragmatic Governance

Governance ensures institutional integrity without stalling product momentum:

```text
IMPLEMENTED  ──>  VALIDATED  ──>  APPROVED  ──>  RETIRED
```

- **Methodology Authorization ≠ Metric Validation:** Approving a formula specification does not validate empirical accuracy.
- **Metric Implementation ≠ Metric Approval:** Wiring an algorithm into code does not authorize it for production decision support.
- **Candidate Metrics for Prototypes:** Candidate metrics may be incorporated into investor-facing prototype features when prominently labeled with epistemic disclaimers.
- **Never Fabricate Governance States:** Never claim a metric is `VALIDATED` or `APPROVED` without formal verification evidence.
- **Do Not Weaponize Governance:** Governance exists to safeguard epistemic honesty, not to paralyze practical development.

---

## 9. Implementation Discipline

Before modifying code:
1. **Inspect Existing Code First:** Understand current patterns, DTOs, entity structures, and test suites.
2. **Find the Smallest Integration Point:** Make the minimum targeted change required to deliver the feature correctly.
3. **Reuse Existing Infrastructure:** Reuse existing quant dispatchers, calculation pipelines, REST clients, and UI components rather than inventing parallel abstractions.
4. **Avoid Speculative Architecture:** Solve the immediate concrete problem. If real implementation exposes an architectural defect, fix the actual defect rather than designing for hypothetical futures.
5. **No Unrelated Refactoring:** Do not reformat, rename, or reorganize code outside the active feature's scope.
6. **Preserve Working Tests:** Never delete, comment out, or weaken existing tests.

---

## 10. Investor-Facing UI & Epistemic States

The investor-facing product is YUKIRA's primary output. The UI must feel authoritative, institutional, and transparent.

### Terminology Rules
- **No Pipeline Jargon:** Do not expose internal technical labels (e.g., `Phase 2S`, `Flyway V7`, `quant IPC`, `dispatcher.py`) in user interfaces.
- **Plain Investor Language:** Translate technical concepts into clear investor terms (e.g., *"3-Year Annualized Return"* instead of *"RET-03 CAGR with 252 annualization factor"*).

### Comprehensive Epistemic States
Every UI view must cleanly support:
- **Loading State:** Skeleton loaders with clear contextual hints.
- **Empty State:** Informative explanations when no data has been configured.
- **Unavailable State:** Explicit `"Not available"` display when metrics lack required inputs.
- **Insufficient Evidence State:** Clear explanation when observation counts fall below statutory thresholds (e.g., `< 700` days for 3Y metrics).
- **Data Quality Limitation State:** Amber/red visual badges showing suspicious or stale observations without hiding the underlying data.
- **Error State:** Actionable error cards explaining what failed without raw stack dumps.
- **Zero Fabrication:** Never display placeholder zeroes (`0.00%`) or dummy figures when data is missing.

---

## 11. Grounded AI Interpretation Framework

When generating or testing natural language AI explanations, ground every sentence in verified calculation outputs.

### The 6-Question Interpretation Test
A valid YUKIRA explanation must answer:
1. **What happened?** (Factual summary of verified numerical results)
2. **What does it mean?** (Economic/analytical significance for an allocator)
3. **What evidence supports it?** (Underlying observations, benchmarks, time windows)
4. **What are the important risks?** (Downside exposures, factor tilt, concentration)
5. **What could invalidate this?** (Regime shift, manager turnover, data quality issues)
6. **What should the investor investigate?** (Concrete due-diligence questions)

Never transform descriptive historical evidence into speculative return forecasts.

---

## 12. Development Verification & Review Checklist

After implementing any vertical slice, execute this end-to-end verification checklist:

- [ ] **Investor Value Check:** Does this change provide immediate, tangible utility to an investor?
- [ ] **Real Data Verification:** Has the feature been verified with real market observations (not just mocks)?
- [ ] **Deterministic Math:** Are all financial calculations executed by verified Java/Python kernels?
- [ ] **PIT Invariance:** Are `analysis_cutoff` and `knowledge_cutoff` strictly respected?
- [ ] **Cryptographic Lineage:** Is the output traceable to source artifacts and calculation runs?
- [ ] **Backend & API Contract:** Does the REST API deliver structured, typed DTOs with error handling?
- [ ] **Frontend Rendering:** Does the UI render cleanly, handle all epistemic states, and avoid internal jargon?
- [ ] **Automated Test Coverage:**
  - Quant: Run `pytest quant-engine/tests/` and report actual passing/failing test counts.
  - Backend: Run `cd backend && ./mvnw test` and report actual passing/failing test counts.
  - Frontend: Run `cd frontend && npm test`, `npm run lint`, and `npm run build` and report actual test counts, zero lint errors, and successful build status.
- [ ] **Regression Safety:** Are all previously passing tests and existing routes preserved?
- [ ] **Clean Diff:** Does `git status --short` and `git diff --stat` contain strictly intended changes?

---

## 13. Strict "What NOT To Do" Guardrails

- ❌ **Do NOT** build architecture for hypothetical future requirements.
- ❌ **Do NOT** write methodology documents without an immediate product requirement.
- ❌ **Do NOT** endlessly expand candidate metric libraries without delivering user features.
- ❌ **Do NOT** build validation harnesses that deliver zero investor-visible capability.
- ❌ **Do NOT** inject mock/synthetic data into production stores to make screens look complete.
- ❌ **Do NOT** generate composite scores or star ratings without defensible, verified models.
- ❌ **Do NOT** let LLMs or neural networks compute financial figures.
- ❌ **Do NOT** hide candidate or unvalidated methodology status.
- ❌ **Do NOT** perform opportunistic or unrelated refactoring.
- ❌ **Do NOT** declare a feature complete merely because unit tests pass in isolation.

---

## 14. Feature Delivery & Handoff Standard

When completing a feature implementation or audit, report concisely:

1. **Investor Capability Delivered:** Exactly what new capability the investor can now execute.
2. **Files Changed:** Concise list of modified and created files.
3. **Real Data Verification:** Source artifacts, evaluation windows, and instrument identifiers used.
4. **Calculations & Results:** Key numerical outputs and empirical comparisons.
5. **Automated Test Suites:** Exact live test execution counts across quant, backend, and frontend (never assume fixed historical counts; report actual verified passes/failures).
6. **UI Verification:** Visual verification of screens, responsive layouts, and epistemic states.
7. **Discovered Limitations:** Any edge cases, data gaps, or caveats identified.
8. **Justified Architecture Adjustments:** Any structural improvements justified by actual implementation.
9. **Next Immediate Step:** The next logical vertical slice in the product pipeline.

Keep reports factual, structured, and concise. Avoid redundant philosophical summaries.

---

## 15. The Core Execution Rule

> **BUILD SOMETHING USEFUL.**  
> **PUT IT IN FRONT OF THE INVESTOR.**  
> **SEE WHERE IT FAILS.**  
> **FIX WHAT ACTUAL USE REVEALS.**  
> **THEN BUILD THE NEXT THING.**  
>  
> *Do not let YUKIRA become an architecture project that happens to contain code.*  
> *It must become a working investment intelligence product.*
