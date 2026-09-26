# PHASE 2S SCOPE LOCK — EMPIRICAL ANALYTICAL VALIDATION & METHODOLOGY VERIFICATION

> **Document Type:** Authoritative Scope-Lock Specification (Research & Evidence-Building Phase)  
> **Status:** PROPOSED SCOPE — NOT YET AUTHORIZED  
> **Repository Governance:** Project YUKIRA Constitution, [`AGENTS.md`](file:///AGENTS.md), and Frozen Phase 2H Methodology Specification  
> **Preceding Phase Status:** **PHASE 2R CLOSED**  

---

## Executive Summary & Epistemic Purpose

Phase 2S is strictly a **research and evidence-building phase**. Its purpose is to design, establish, and execute an objective empirical validation harness across the **16 metrics in the Phase 2R analytical profile**.

Phase 2S establishes the factual, empirical, and statistical evidence that the Project YUKIRA Governance Committee may subsequently evaluate when considering whether a metric should transition from `CANDIDATE` to `VALIDATED`.

### Core Epistemic Governance Guardrails:
1. **Evidence-Building Only:** Phase 2S generates evidence. It does not perform, assume, imply, or automatically trigger any governance-state transition.
2. **Implementation != Validation != Approval:** Passing all validation modules produces a validation outcome of `PASS`. It does **not** automatically mark any metric as `VALIDATED` or `APPROVED`.
3. **No Financial Advisory / No Scoring:** Phase 2S produces zero composite scores, star ratings, ranking algorithms, or investment recommendations.
4. **Frozen Phase 2R Foundation:** Existing Phase 2R production calculation implementations, endpoints, database schemas, and frontend presentation views remain completely untouched.

---

## 1. Critical Numerical Reconciliation & Baseline Establishment

To ensure that empirical validation is conducted against authoritative point-in-time facts, all figures evaluated in Phase 2S are anchored strictly to the closed **Phase 2R canonical profile** computed on pilot fund **`118955`** (HDFC Flexi Cap Fund, Direct Plan, Growth Option, ISIN: `INF179K01UT0`) across the canonical 3-year PIT evaluation window (`2021-01-15` to `2024-01-15`, 737 market trading observations, knowledge cutoff `2024-01-31`).

Historical discrepancies identified during preliminary scoping audits are categorized using the authoritative five-tier discrepancy taxonomy:
- **Category A:** Same Phase 2R 3-year calculation (e.g. display formatting / precision difference).
- **Category B:** Different historical window (e.g. 5-year observation span cited instead of canonical 3-year window).
- **Category C:** Different methodology version.
- **Category D:** Different return / sign / unit convention (e.g. raw quantile return vs positive loss convention).
- **Category E:** Error (e.g. agent text derivation or heuristic calculation shortcut).

### 1.1 Canonical Baseline Reconciliation Register

| Metric Code | Metric Name | Closed Phase 2R Canonical Value | Previously Cited Preliminary Value | Analysis Window | Knowledge Cutoff | Methodology Version | Source Snapshot / Run ID | Category | Reconciliation Finding & Technical Root Cause |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **`RET-02`** | Simple Period Return (3Y) | **`0.86438865`** (`+86.44%`) | `0.864389` | `2021-01-15` to `2024-01-15` | `2024-01-31` | `CANDIDATE_V1` | `Run 1914` | **A** | Canonical discrete return ($NAV_T / NAV_0 - 1$). Minor display rounding. |
| **`RET-03`** | Annualized Return / CAGR (3Y) | **`0.23075253`** (`+23.08%`) | `0.2308` | `2021-01-15` to `2024-01-15` | `2024-01-31` | `CANDIDATE_V1` | `Run 1914` | **A** | Canonical Julian leap-adjusted compounding ($(NAV_T/NAV_0)^{365.25/1095}-1$). Identical calculation. |
| **`RET-07`** | Active Return (3Y) | **`0.0520`** (`+5.20%`) | `0.060128` (`+6.0128%`) | `2021-01-15` to `2024-01-15` | `2024-01-31` | `CANDIDATE_V1` | `Run 1914` | **E** | **Agent Derivation Error:** Preliminary audit subtracted an unverified preliminary benchmark return ($23.08\% - 17.06\%$). Canonical engine uses authoritative NIFTY 500 TRI return ($23.08\% - 17.88\% = +5.20\%$). |
| **`RSK-01`** | Annualized Volatility (3Y) | **`0.13476625`** (`13.48%`) | `0.1348` | `2021-01-15` to `2024-01-15` | `2024-01-31` | `CANDIDATE_V1` | `Run 1914` | **A** | Sample standard deviation of daily logarithmic returns annualized with $\sqrt{252}$ and `ddof=1`. |
| **`RSK-02`** | Downside Deviation (3Y) | **`0.0910`** (`9.10%`) | `0.0910` | `2021-01-15` to `2024-01-15` | `2024-01-31` | `M2N-03_PROP` | `Run 1914` | **A** | Full sample divisor $N-1$ per Phase 2N resolution. Identical value. |
| **`RSK-03`** | Maximum Drawdown (3Y) | **`-0.12880013`** (`-12.88%`) | `-0.147987` (`-14.80%`) | `2021-01-15` to `2024-01-15` | `2024-01-31` | `CANDIDATE_V1` | `Run 1914` | **B** | **Window Mismatch:** Canonical 3Y peak-to-trough decline is $-12.88\%$. Preliminary report cited the 5Y historical window drawdown ($-14.80\%$). |
| **`RSK-04`** | Max Drawdown Duration (3Y) | **`168 days`** | `297 days` | `2021-01-15` to `2024-01-15` | `2024-01-31` | `CANDIDATE_V1` | `Run 1914` | **B** | **Window Mismatch:** Canonical 3Y duration is 168 calendar days. Preliminary report cited 5Y window recovery span (297 days). |
| **`RSK-05`** | Ulcer Index (3Y) | **`3.91103420`** | `3.62` | `2021-01-15` to `2024-01-15` | `2024-01-31` | `CANDIDATE_V1` | `Run 1914` | **B** | **Window Mismatch:** Canonical 3Y quadratic drawdown mean is 3.9110. Preliminary report cited 5Y window calculation (3.62). |
| **`RSK-06`** | Daily Value at Risk 95% (3Y) | **`0.0210`** (`2.10%`) | `-0.0125` | `2021-01-15` to `2024-01-15` | `2024-01-31` | `CANDIDATE_V1` | `Run 1914` | **D** | **Sign Convention & Sample:** Canonical profile presents positive loss convention ($VaR = -q_{0.05} = +0.0210$). Preliminary report cited raw negative return from an unverified sample. |
| **`RSK-07`** | Expected Shortfall 95% (3Y) | **`0.0345`** (`3.45%`) | `-0.0182` | `2021-01-15` to `2024-01-15` | `2024-01-31` | `CANDIDATE_V1` | `Run 1914` | **D** | **Sign Convention & Sample:** Canonical profile presents positive loss convention ($ES = -\mathbb{E}[R \mid R \le q_{0.05}] = +0.0345$). Preliminary report cited raw negative mean. |
| **`RAT-01`** | Sharpe Ratio (3Y) | **`1.1575`** | `1.1575` | `2021-01-15` to `2024-01-15` | `2024-01-31` | `M2N-05_APP` | `Run 1914` | **A** | Canonical FBIL 91-Day T-bill daily risk-free accrual excess return over annualized volatility. |
| **`RAT-02`** | Treynor Ratio (3Y) | **`0.1822`** | `0.1822` | `2021-01-15` to `2024-01-15` | `2024-01-31` | `M2N-06_APP` | `Run 1914` | **A** | Canonical excess return over CAPM Beta against NIFTY 500 TRI. |
| **`REL-01`** | Beta (3Y) | **`0.8542`** | `0.8542` | `2021-01-15` to `2024-01-15` | `2024-01-31` | `M2N-07_APP` | `Run 1914` | **A** | Canonical excess-return covariance over benchmark excess-return variance. |
| **`REL-04`** | Downside Beta (3Y) | **`0.7812`** | `0.7812` | `2021-01-15` to `2024-01-15` | `2024-01-31` | `M2N-07_APP` | `Run 1914` | **A** | Conditional regression on benchmark down days ($R_b < 0, N_d \ge 100$). |
| **`REL-02`** | Tracking Error (3Y) | **`0.0512`** (`5.12%`) | `0.0512` | `2021-01-15` to `2024-01-15` | `2024-01-31` | `CANDIDATE_V1` | `Run 1914` | **A** | Sample standard deviation of daily active return annualized with $\sqrt{252}$. |
| **`REL-03`** | Jensen's Alpha (3Y) | **`0.0415`** (`+4.15%`) | `0.0415` | `2021-01-15` to `2024-01-15` | `2024-01-31` | `CANDIDATE_V1` | `Run 1914` | **A** | CAPM regression intercept annualized with linear convention ($252 \times \alpha$). |

---

## 2. Two-Stage Execution Architecture: Phase 2S-A & Phase 2S-B

To manage execution complexity and eliminate methodological errors before full-scale deployment, Phase 2S is partitioned into two sequential stages:

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                       PHASE 2S EXECUTION PIPELINE                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  [PHASE 2S-A] Validation Framework Foundation                               │
│   ├─ Implement & verify the 10-module validation harness                   │
│   ├─ Apply strictly to the 6 representative metrics                         │
│   ├─ Independent technical audit of validation harness itself              │
│   └─ Gate: Acceptance of 2S-A framework as fit for 2S-B execution           │
│                                                                             │
│                                      ▼                                      │
│                                                                             │
│  [PHASE 2S-B] Full Profile Empirical Validation                             │
│   ├─ Apply frozen validation harness to all 16 Phase 2R metrics             │
│   ├─ Generate metric-level evidence outcomes: PASS / CONDITIONAL / etc.     │
│   ├─ Assemble cryptographic validation dossiers                             │
│   └─ Gate: Delivery of dossier to Governance Committee                     │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.1 Phase 2S-A: Validation Framework Foundation & Representative Metrics
The explicit purpose of Phase 2S-A is to **validate the validation framework itself** before applying it across all metrics.

Phase 2S-A must build and verify a foundation covering all **10 validation evidence modules**:
1. **Methodology & Specification Compliance:** Line-by-line verification against frozen Phase 2H / Phase 2N formulas.
2. **Independent Implementation Parity:** Dual-engine numerical reconciliation against independent Python reference kernels.
3. **PIT & Provenance Verification:** Verification of observation selection using strict bitemporal cutoff rules (`effective_date <= analysis_cutoff` AND `availability_time <= knowledge_cutoff`).
4. **Boundary & Edge-Case Robustness:** Programmatic stress testing under zero variance, zero beta, negative excess returns, and empty/insufficient series.
5. **Sensitivity Analysis:** Perturbation testing ($\pm 1, \pm 3$ trading days) where mathematically relevant to quantify endpoint sensitivity.
6. **Regime-Based Validation:** Multi-regime historical backtesting against pre-specified macroeconomic windows.
7. **Metric-Specific Uncertainty Methodology:** Application of mathematically appropriate resampling or analytical standard error estimators.
8. **Dependency Analysis:** Descriptive empirical collinearity and structural relationship mapping.
9. **Deterministic Reproducibility:** Fixed-seed, bitwise identical calculation runs verified via cryptographic hashing.
10. **Evidence-Dossier Generation:** Programmatic assembly of structured, auditable validation dossiers linking to input snapshots.

#### The Six Representative Metrics for Phase 2S-A:
Phase 2S-A is restricted strictly to **six representative metrics** selected to span all mathematical archetypes:
1. **`RET-03` (Annualized Return / CAGR):** Representative of non-linear cumulative compounding and calendar sensitivity.
2. **`RSK-01` (Annualized Volatility):** Representative of continuous, unconditioned symmetric dispersion.
3. **`RSK-03` (Maximum Drawdown):** Representative of path-dependent, non-distributional cumulative loss sequences.
4. **`RSK-06` (Daily Value at Risk 95%):** Representative of non-parametric tail quantiles and interpolation behavior.
5. **`RAT-01` (Sharpe Ratio):** Representative of composite risk-adjusted excess returns over an external yield curve.
6. **`REL-01` (Beta):** Representative of dual-series unconditioned covariance over benchmark variance.

*Constraint:* **Do not add additional representative metrics to Phase 2S-A.** The remaining 10 metrics are evaluated exclusively in Phase 2S-B after the framework foundation is accepted.

### 2.2 Phase 2S-B: Full Profile Empirical Validation Scope
Phase 2S-B retains the exact scope of **16 metrics in the Phase 2R analytical profile**:

| Return Metrics (3) | Risk Metrics (7) | Risk-Adjusted Metrics (2) | Relative / Market Metrics (4) |
| :---: | :---: | :---: | :---: |
| `RET-02` (Simple Return) | `RSK-01` (Volatility) | `RAT-01` (Sharpe Ratio) | `REL-01` (Beta) |
| `RET-03` (CAGR) | `RSK-02` (Downside Deviation) | `RAT-02` (Treynor Ratio) | `REL-04` (Downside Beta) |
| `RET-07` (Active Return) | `RSK-03` (Max Drawdown) | | `REL-02` (Tracking Error) |
| | `RSK-04` (Max Drawdown Duration) | | `REL-03` (Jensen's Alpha) |
| | `RSK-05` (Ulcer Index) | | |
| | `RSK-06` (Daily VaR 95%) | | |
| | `RSK-07` (Expected Shortfall 95%) | | |

#### Metric-Level Evidence Outcome Taxonomy:
For each metric, Phase 2S-B produces an explicit **metric-level evidence outcome**:
- **`PASS`:** All mandatory evidence modules executed successfully; no unexplained material discrepancy under the pre-specified tolerance; empirical stability demonstrated.
- **`CONDITIONAL`:** Numerical parity and compliance verified, but specific operational constraints or data limitations identified (e.g. valid only when $N_d \ge 100$).
- **`INSUFFICIENT`:** Available empirical observations are insufficient to reliably evaluate the metric across all required regimes.
- **`BLOCKED`:** Required external data asset or prerequisite dependency is unavailable.
- **`N/A`:** The specific module is mathematically inapplicable to this metric archetype (e.g. bootstrap uncertainty on path-dependent drawdowns).

> [!CRITICAL]
> **Evidence Outcomes Are NOT Governance States:**  
> `PASS`, `CONDITIONAL`, `INSUFFICIENT`, `BLOCKED`, and `N/A` are **evidence and validation outcomes only**. They must **never** be represented as governance states.  
> **No metric automatically becomes `VALIDATED` or `APPROVED` because it receives a `PASS` validation outcome.**

---

## 3. Numerical Parity & Metric-Specific Tolerances

YUKIRA rejects universal numerical parity shortcuts. Any universal requirement for "zero material numerical discrepancy" across divergent computational libraries is mathematically unsound due to differences in interpolation conventions, degrees-of-freedom corrections, and floating-point algorithms.

### 3.1 Core Parity Principle
> **"No unexplained material discrepancy under a metric-specific, pre-specified tolerance."**

- Where exact deterministic equality is mathematically appropriate (e.g. algebraic identities, integer counts, discrete return ratios), **exact equality may be required**.
- Where floating-point approximations, quantile interpolations, or iterative solvers are used, tolerances must be **metric-specific and justified prior to execution**.
- **A universal $10^{-12}$ tolerance is strictly prohibited.**

### 3.2 Pre-Specified Metric-Specific Parity Tolerances

| Metric Archetype | Relevant Metrics | Reference Implementation Target | Source of Legitimate Divergence | Pre-Specified Parity Tolerance | Justification |
| :--- | :--- | :--- | :--- | :---: | :--- |
| **Discrete Arithmetic Return** | `RET-02` | Independent Python ratio ($P_T / P_0 - 1$) | Floating-point IEEE-754 representation | **$< 10^{-14}$** | Exact closed-form formulation. |
| **Compounded Compounding** | `RET-03` | Closed-form exponentiation | Exact Julian year ($365.25$) day-count convention | **$< 10^{-10}$** | Compounding over fractional exponents. |
| **Linear Dispersion** | `RSK-01`, `REL-02` | `numpy.std`, `scipy.stats.tstd` (`ddof=1`) | Unbiased sample divisor ($N-1$) convention | **$< 10^{-12}$** | Machine precision on standard variance kernels. |
| **Downside Semi-Dispersion** | `RSK-02` | Vectorized subset deviation | Full sample divisor ($N-1$) per Phase 2N | **$< 10^{-12}$** | Vectorized threshold subtraction. |
| **Path-Dependent Cumulative Loss** | `RSK-03` | Vectorized running peak drawdown ($P_t / \max(P) - 1$) | Running maximum floating-point precision | **$< 10^{-12}$** | Deterministic sequential path scan. |
| **Integer Duration Count** | `RSK-04` | Running peak calendar duration scan | Calendar date difference logic | **Exact Match (`0 days`)** | Discrete integer count of calendar days. |
| **Quadratic Path Integral** | `RSK-05` | Vectorized quadratic mean of drawdowns | Floating-point summation order | **$< 10^{-10}$** | Root-mean-square of cumulative drawdowns. |
| **Empirical Quantile** | `RSK-06` | `numpy.percentile` (method=`linear`) | Quantile interpolation method (`linear` vs `lower`) | **$< 10^{-4}$** | $1\text{–}2$ bps difference across standard empirical quantile definitions. |
| **Conditional Tail Expectation** | `RSK-07` | Mean of returns below 5th percentile | Boundary inclusion ($\le$ vs $<$) on quantile cutoff | **$< 10^{-6}$** | Discrete sample mean of tail slice. |
| **Risk-Free Excess Ratio** | `RAT-01` | Independent vectorized excess yield kernel | Daily accrual compounding order | **$< 10^{-8}$** | Daily compounded risk-free rate subtractions. |
| **Linear OLS Slope & Intercept** | `REL-01`, `RAT-02`, `REL-03` | `statsmodels.regression.linear_model.OLS` | SVD vs QR decomposition floating-point centering | **$< 10^{-8}$** | Linear regression on excess return vectors. |
| **Conditional Subset Slope** | `REL-04` | Custom conditioned OLS on $R_b < 0$ | Floating-point zero threshold handling ($R_b < 0$) | **$< 10^{-8}$** | Regression on conditioned data subset ($N_d \ge 100$). |

---

## 4. Objective Historical Regime Protocol & Regime Freeze Gate

To prevent data cherry-picking, hindsight bias, and researcher degrees of freedom, Phase 2S enforces a strict **Regime Freeze Gate**.

### 4.1 The Regime Freeze Protocol
Before the first validation result is generated in Phase 2S, the following parameters must be formally frozen:
- **Regime definitions** (macroeconomic and market criteria)
- **Benchmark series** (authoritative NIFTY 500 TRI observation set)
- **Date boundaries** (exact start and end dates)
- **Inclusion/exclusion rules** (minimum trading observation thresholds)
- **Calculation parameters** relevant to the regime test (e.g. rolling window lengths, confidence intervals)

> [!WARNING]
> **Strict Anti-Cherry-Picking Rule:**  
> After validation execution begins, **regime boundaries and rules cannot be modified to improve results**. Post-hoc regime selection, window shifting, or parameter tuning is strictly prohibited. If a metric fails during a pre-specified regime, the failure must be recorded as factual evidence.

### 4.2 Pre-Specified Frozen Validation Regimes

1. **Liquidity Shock & Rapid Rebound Regime (`REG-01`):**
   - *Benchmark Criterion:* NIFTY 500 TRI peak-to-trough collapse exceeding $20\%$, followed by complete index recovery.
   - *Frozen Date Boundaries:* **`2020-02-01` to `2020-11-30`** (10 months, ~210 trading days).
   - *Primary Validation Focus:* Algorithm stability during market circuit breakers, extreme tail volatility spikes, and deep drawdown recovery.
2. **Sustained Secular Bull Market Regime (`REG-02`):**
   - *Benchmark Criterion:* Continuous 12-month period where benchmark annualized return exceeds $+20\%$ with maximum drawdown under $10\%$.
   - *Frozen Date Boundaries:* **`2020-12-01` to `2021-12-31`** (13 months, ~270 trading days).
   - *Primary Validation Focus:* Downside Beta sample sufficiency (verifying whether down-market days $N_d < 100$ safely trigger `INSUFFICIENT_DATA` guards); compounding stability in low-volatility upside trends.
3. **Monetary Tightening & Sideways Consolidation Regime (`REG-03`):**
   - *Benchmark Criterion:* Period initiated by the Reserve Bank of India (RBI) off-cycle repo rate hike (+40 bps in May 2022) through the culmination of rate hikes in 2023, marked by range-bound benchmark movement.
   - *Frozen Date Boundaries:* **`2022-04-01` to `2023-04-30`** (13 months, ~268 trading days).
   - *Primary Validation Focus:* Sharpe ratio behavior under compressed/negative excess returns; daily risk-free accrual stability across a steep rate hike environment ($4.00\% \to 6.50\%$).

*Minimum Observation Threshold:* Every regime test requires at least 126 continuous trading days (6 months). Any sub-period with $N < 126$ is rejected as statistically underpowered.

---

## 5. Metric-by-Metric Statistical Uncertainty & Research Boundaries

YUKIRA establishes that **statistical uncertainty methodology is metric-specific**. The platform explicitly rejects applying a universal bootstrap or resampling method across all financial metrics.

### 5.1 Uncertainty Policy & Archetype Separation
Financial time-series data exhibits serial autocorrelation, volatility clustering, and path dependency. Consequently:
- **Distributional Return & Linear Dispersion Metrics (`RSK-01`, `RSK-02`, `RAT-01`, `RAT-02`, `REL-01`, `REL-04`, `REL-02`, `REL-03`, `RET-07`):**
  - Permitted to use stationary block bootstrap or circular block bootstrap.
  - **Block length must be justified from the observed autocorrelation and dependence structure** (e.g. Politis & White block-length selection) rather than hardcoded universally.
  - Standard errors and 95% confidence intervals (BCa or Studentized) are computed to quantify parameter estimation noise.
- **Path-Dependent Cumulative Metrics (`RSK-03`, `RSK-04`, `RSK-05`):**
  - **Must NOT automatically inherit the same uncertainty procedure as distributional return metrics.**
  - Standard block bootstrapping scrambles drawdown recovery sequences, creating synthetic, physically impossible historical paths.
  - Uncertainty for path-dependent metrics must be evaluated through rolling historical sub-windows or analytical extreme value distributions, not naive return shuffling.

### 5.2 Methodological Research Boundary: M2N-04 Divisor Question
- **Status:** **RESEARCH ONLY**.
- Specification `M2N-04` addresses the unresolved question of whether Downside Semideviation should be normalized by total observations ($N-1$) or conditioned down-market observations ($N_d-1$).
- **Mandate:** Phase 2S may research the mathematical and empirical implications of the semideviation divisor and document comparative evidence in the research dossier.
- **Prohibition:** **Phase 2S must NOT implement M2N-04 in production code, must NOT alter existing calculation kernels, and must NOT adjudicate the methodology.** Formal methodology adoption remains strictly with the Governance Committee.

---

## 6. Data Sufficiency & Explicit Methodological Limitations

Phase 2S validation must maintain complete institutional transparency regarding the boundaries of the empirical test dataset.

### 6.1 Explicit Dataset Limitations
1. **Single-Fund Canonical Population:** Validation is conducted strictly on canonical pilot fund `118955` (HDFC Flexi Cap Fund Direct Growth).
   - *Limitation:* Single-instrument empirical validation verifies mathematical algorithm correctness, numerical stability, and regime behavior. **It does NOT establish institutional-grade cross-sectional robustness across peer mutual funds.**
2. **Historical Observation Window:** Available authentic data span is limited to the 5-year ledger (`2019-01-01` to `2024-01-15`, 1,243 trading days). Multi-decade cycle validation is unavailable.
3. **Unresolved Weekday Calendar Gaps:** 72 non-trading weekdays remain unclassified as official exchange holidays or missing records, pending ingestion of an authoritative NSE/BSE holiday calendar.
4. **Benchmark & Risk-Free Data Coverage:** Benchmark series (NIFTY 500 TRI) is point-in-time aligned for the 5-year pilot span, but lacks an automated continuous daily upstream pipeline.
5. **Zero Cross-Sectional Peer Validation:** The current local database does not contain peer funds within the SEBI Flexi Cap category. Cross-sectional ranking, percentiles, and category distributions cannot be validated.
6. **Survivorship & Generalization Bias:** Validation on an established, surviving large-cap fund does not account for fund merger, liquidation, or category-wide survivorship bias.

---

## 7. Descriptive-Only Dependency & Redundancy Analysis

Phase 2S incorporates empirical dependency analysis to evaluate relationships across the 16 metrics.

### 7.1 Scope of Dependency Analysis
- Dependency analysis is **strictly descriptive**.
- It identifies structural mathematical linkages (e.g. Treynor ratio's direct sensitivity to Beta in the denominator: $T = (R_p - R_f) / \beta$).
- It evaluates empirical rank correlation and collinearity across historical regimes (e.g. correlation between Volatility `RSK-01` and Downside Deviation `RSK-02`).

### 7.2 Strict Operational Prohibitions
Dependency analysis is informational audit context. **It must NOT:**
- Downweight metrics in analytical displays
- Alter, adjust, or composite metric values
- Automatically reject or invalidate metrics
- Alter approved methodology
- Produce portfolio optimization scores or investment recommendations

---

## 8. Authoritative Governance Sequence & Lifecycle Gates

Phase 2S clarifies the operational lifecycle boundary between engineering validation and fiduciary governance.

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                       AUTHORITATIVE GOVERNANCE SEQUENCE                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Step 1: 2S-A Framework is executed and independently audited.             │
│                                                                             │
│  Step 2: 2S-A Framework is accepted as fit for 2S-B execution.              │
│                                                                             │
│  Step 3: 2S-B Validation evidence is generated for all 16 metrics.          │
│                                                                             │
│  Step 4: Comprehensive Validation Dossiers are assembled.                   │
│                                                                             │
│  Step 5: Governance Committee formally reviews the empirical evidence.     │
│                                                                             │
│  Step 6: Governance Committee considers Candidate ──> VALIDATED.           │
│                                                                             │
│  Step 7: APPROVED remains a separate, subsequent fiduciary action.          │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

> [!IMPORTANT]
> **No Pre-Approval / No Automated Promotion:**  
> Governance Committee review is **not** a prerequisite for Phase 2S-B execution. Rather, 2S-A is audited and accepted as fit for 2S-B execution, 2S-B executes across all 16 metrics, and Governance reviews the complete evidence package at the conclusion of Phase 2S.  
> Governance never pre-approves validation results before evidence is generated. Engineering never promotes metrics to `VALIDATED` or `APPROVED`.

---

## 9. Epistemic Language Standards & Terminology

### 9.1 Mandatory Metric Terminology
- **APPROVED:** *"16 Phase 2R analytical metrics"* or *"16 metrics in the Phase 2R analytical profile"*.
- **PROHIBITED:** *"16 institutional metrics"* (presumes institutional certification prior to governance approval).

### 9.2 Epistemic Precision Directives
Financial engineering verifies empirical behavior; it does not establish metaphysical truth.

| Prohibited Unqualified Language | Approved Epistemic Terminology | Rationale |
| :--- | :--- | :--- |
| *"Proves the algorithm is correct"* | *"Provides empirical evidence supporting specification compliance"* | Empirical observations corroborate; they cannot mathematically prove universal validity. |
| *"Proves fitness for production"* | *"Assesses suitability against defined validation criteria"* | Fitness is an ongoing operational condition, not a one-time proof. |
| *"Eliminates model risk"* | *"Reduces specification uncertainty and identifies failure modes"* | Model risk cannot be eliminated; it can only be characterized and bounded. |
| *"Maximum risk reduction"* | *"Substantially addresses identified methodology gaps"* | Claims of "maximum" are unquantifiable rhetoric. |
| *"Catastrophic model risk"* | *"Unquantified parameter sensitivity or regime vulnerability"* | Technical vulnerability description without sensationalism. |
| *"Establishes institutional truth"* | *"Satisfies defined institutional validation criteria"* | Financial metrics are human-constructed estimators, not natural laws. |

---

## 10. Final Definition of Done

Phase 2S is complete when all of the following verifiable conditions are satisfied:

1. **Evidence Modules Executed:** All required evidence modules across the 10-module MVEP framework are executed or explicitly marked with an evidence outcome (`PASS`, `CONDITIONAL`, `INSUFFICIENT`, `BLOCKED`, `N/A`) for all 16 Phase 2R analytical metrics.
2. **Methodology Compliance Assessed:** Line-by-line compliance with frozen Phase 2H / Phase 2N formulas is assessed and documented.
3. **Independent Parity Assessed:** Numerical parity against independent reference implementations is verified under pre-specified, metric-specific tolerances.
4. **No Unexplained Material Discrepancies:** Zero unexplained material discrepancies exist under the pre-specified metric-specific tolerances.
5. **PIT & Provenance Assessed:** Bitemporal observation selection and cryptographic data lineage are verified with zero look-ahead bias.
6. **Applicable Edge Cases Assessed:** Boundary behavior under zero variance, zero beta, negative excess returns, and missing series is verified.
7. **Applicable Regime Sensitivity Assessed:** Performance across the three frozen historical regimes (`REG-01`, `REG-02`, `REG-03`) is executed without post-hoc window adjustments.
8. **Applicable Uncertainty Assessed:** Metric-specific statistical uncertainty (block bootstrap with justified block length or analytical standard errors) is documented; path-dependent metrics are protected from invalid resampling.
9. **Descriptive Dependencies Documented:** Structural and empirical metric dependencies are mapped descriptively without downweighting or scoring.
10. **Deterministic Reproducibility Demonstrated:** All validation scripts execute deterministically with fixed seeds and bitwise reproducible outputs.
11. **Validation Dossier Compiled:** Authoritative markdown dossier `docs/research/validation/PHASE_2S_VALIDATION_DOSSIER.md` is generated, cryptographically signed with input data digests, and archived.
12. **Zero Automatic Governance Transition:** All metrics retain their existing governance status (`CANDIDATE`); zero automated transitions occur.
13. **Phase 2R Implementation Intact:** Existing Phase 2R backend orchestrators, quant calculation kernels, database schema (Flyway V1–V12), and frontend views remain completely unchanged.
14. **Existing Test Suite Passes:** All 551 existing automated tests (backend, quant-engine, frontend) continue to pass.

---

## 11. Explicit Out-of-Scope Declarations

To preserve development discipline, the following activities are strictly out of scope for Phase 2S:
- **Zero New Metrics:** The 14 unimplemented Phase 2H metrics remain candidate specifications.
- **Zero Database Schema Changes:** No Flyway migrations, no table alterations, no DDL modifications.
- **Zero Scoring / Ranking:** No composite fund scoring, star ratings, or rank-ordering models.
- **Zero Investment Advice:** No buy, sell, hold, or allocation recommendations.
- **Zero Frontend UI Modifications:** No UI redesigns, no component changes, no style adjustments.
- **Zero MCP Servers:** No MCP installation, configuration, or integration.
- **Zero Code Refactoring:** No speculative refactoring of closed Phase 2R production code.

---

## 12. Final Document Status & Authorization Gate

```text
PHASE 2R CLOSED
PHASE 2S PROPOSED SCOPE — NOT YET AUTHORIZED
```
