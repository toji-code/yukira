# YUKIRA — Phase 2N V2 Independent Methodology Validation Record

**Document Reference:** `docs/research/PHASE_2N_V2_VALIDATION.md`  
**Phase:** 2N  
**Status:** VALIDATION REPORT (GAP RESOLUTION & REFINED AUDIT)  
**Lifecycle State:** CANDIDATE / PROPOSED FOR APPROVAL  
**Implementation Authorization:** NOT AUTHORIZED  
**Validation Status:** 0 VALIDATED (Technical Validation Pass $\ne$ Governance Lifecycle State Validated)  
**Approval Status:** 0 APPROVED  
**Phase 2H Status:** FROZEN (`phase2h_quantitative_methodology.md` ZERO DIFF)  

---

## 1. Validation Scope

This document records the independent quantitative validation of the five refined candidate financial methodologies in **Phase 2N V2** (`docs/methodology/phase_2n_v2.md`), following targeted gap resolution:

1. **M2N-01 — Metric-Specific Annualization Framework** (CAGR: $365.25/D$; Volatility/Tracking Error/Sharpe: $\sqrt{252}$; Beta: Unannualized scale-invariant)
2. **M2N-02 — Risk-Free Proxy** (FBIL 91-Day T-Bill Yield with $\text{ACT}/365$ linear daily de-annualization $R_{f,t} = y_{t-1} \times \frac{\Delta d_t}{365.0}$)
3. **M2N-05 — Treynor Ratio Numerator** (Annualized arithmetic mean daily excess return $\bar{R}_{\text{excess, daily}} \times 252$ over 36M horizon)
4. **M2N-06 — Portfolio Beta Specification** (Excess-Return Single-Index OLS regression with intercept)
5. **M2N-07 — Downside Beta Specification** (Subsample raw-return covariance/variance ratio conditioned on $R_{b,t} < 0$, with $\ge 100$ down-days threshold)

### Excluded / Deferred Scope (Out of Scope for V2):
- **M2N-03:** Sortino / Minimum Acceptable Return (MAR) — *DEFERRED*
- **M2N-04:** Downside Semideviation Divisor ($N$ vs $N-1$ vs $K$) — *DEFERRED*
- **M2N-08:** Upside/Downside Capture Subset Compounding — *DEFERRED*
- **M2N-09:** Capture Spread Calculation — *DEFERRED*

---

## 2. Source Documents & Governance Hierarchy

1. `AGENTS.md` (Authoritative Governance & Operating Manual)
2. `docs/CURRENT_STATE.md` (Current software snapshot & baselines)
3. `docs/ARCHITECTURE.md` (Multi-tier architectural contracts)
4. `docs/DEVELOPMENT_RULES.md` (Engineering rules & PIT standards)
5. `docs/PHASE_STATUS.md` (Phase chronological state)
6. `phase2h_quantitative_methodology.md` (Frozen baseline methodology)
7. `docs/research/PHASE_2N_METHODOLOGY_RESOLUTION.md` (Phase 2N empirical research)
8. `docs/research/PHASE_2N_GOVERNANCE_DECISIONS.md` (Phase 2N governance adjudications)
9. `docs/methodology/phase_2n_v2.md` (Refined Phase 2N V2 candidate specification)

---

## 3. Validation Rules & Epistemic Standards

1. **Separation of Validation from Lifecycle Approval:**
   - **`VALIDATION PASS`** establishes that the mathematical formula, deterministic test vectors, boundary conditions, edge cases, PIT constraints, and independent multi-engine cross-checks are technically verified.
   - **`LIFECYCLE STATE: VALIDATED`** is a formal relational governance status that requires explicit human governance committee sign-off.
   - Therefore, at this stage: **VALIDATED = 0**, **APPROVED = 0**, **Phase 2O Authorization = NO**.
2. **Double Calculation & Independent Cross-Check:** Every quantitative output was calculated across at least two independent implementations (Pure Python, Exact Rational Decimal, and NumPy/SciPy).
3. **Numerical Tolerance:** Machine epsilon float64 IEEE standard ($\text{abs\_tol} \le 10^{-14}$).

---

## 4. M2N-01 Results — Metric-Specific Annualization

### 4.1 Candidate Convention
- **CAGR:** $\text{CAGR} = \left(\frac{\text{Ending NAV}}{\text{Starting NAV}}\right)^{\frac{365.25}{\text{elapsed\_calendar\_days}}} - 1$
- **Daily Volatility:** $\sigma_{\text{annual}} = s \times \sqrt{252}$, where $s = \sqrt{\frac{1}{N-1}\sum_{t=1}^N (R_t - \bar{R})^2}$
- **Tracking Error:** $\text{TE}_{\text{annual}} = s_{\text{active}} \times \sqrt{252}$, where $s_{\text{active}} = \sqrt{\frac{1}{N-1}\sum_{t=1}^N (R_{\text{active}, t} - \bar{R}_{\text{active}})^2}$
- **Sharpe Ratio:** $\text{Sharpe} = \frac{\bar{R}_{\text{excess, daily}}}{\sigma_{\text{excess, daily}}} \times \sqrt{252} = \frac{\bar{R}_{\text{excess, daily}} \times 252}{\sigma_{\text{excess, daily}} \times \sqrt{252}}$ (Lo, 2002; Sharpe, 1994).
- **Beta Scale Invariance:** **Beta is NOT annualized.** Beta is a dimensionless regression coefficient ($\beta = \text{Cov}(x, y)/\text{Var}(x)$); any scaling factor (e.g. $\sqrt{252}$) cancels out identically ($252/252 = 1$).

### 4.2 Test Vectors & Results

| Vector ID | Scenario | Inputs | Expected Result | Actual (Pure Python) | Independent (NumPy/Decimal) | Discrepancy | Validation Result |
|---|---|---|---|---|---|---|---|
| **V1.1** | Exactly 365 Calendar Days | $\text{NAV}_0=100, \text{NAV}_T=110, D=365$ | $0.1000718113835106$ | $0.1000718113835106$ | $0.1000718113835106$ | $0.00$ | PASS |
| **V1.2** | Leap Year (366 Days) | $\text{NAV}_0=100, \text{NAV}_T=110, D=366$ | $0.0997851824583971$ | $0.0997851824583971$ | $0.0997851824583970$ | $1.38 \times 10^{-16}$ | PASS |
| **V1.3** | 3-Year Horizon (1096 Days) | $\text{NAV}_0=100, \text{NAV}_T=150, D=1096$ | $0.1446789525191443$ | $0.1446789525191443$ | $0.1446789525191443$ | $0.00$ | PASS |
| **V1.4** | Daily Volatility ($\sqrt{252}$) | $R = [0.01, -0.005, 0.008, -0.002, 0.004, -0.003]$ | $0.0988817475573728$ | $0.0988817475573728$ | $0.0988817475573728$ | $1.39 \times 10^{-17}$ | PASS |
| **V1.5** | Sharpe Annualization ($\sqrt{252}$) | $R_{excess} = [0.008, -0.007, 0.006, -0.004, 0.002, -0.005]$ | $0.0000000000000000$ | $0.0000000000000000$ | $0.0000000000000000$ | $0.00$ | PASS |
| **V1.6** | Beta Scale Invariance | Daily returns vs Returns scaled by $\sqrt{252}$ | $\beta_{\text{daily}} \equiv \beta_{\text{scaled}}$ | $1.1812725090036016$ | $1.1812725090036016$ | $0.00$ | PASS |

### 4.3 Validation Assessment
- **Validation Result:** **`VALIDATION PASS`**
- **Lifecycle State:** `CANDIDATE / PROPOSED FOR APPROVAL`
- **Resolution:** Removed ambiguous phrasing; explicitly formalized Sharpe numerator annualization ($\times 252$) and denominator annualization ($\times \sqrt{252}$), and confirmed Beta scale invariance without an annualization factor.

---

## 5. M2N-02 Results — Risk-Free Proxy (FBIL 91-Day T-Bill)

### 5.1 Candidate Convention
- **Source:** FBIL 91-Day Treasury Bill cutoff yield published by Financial Benchmarks India Pvt Ltd.
- **Quote Convention:** Annualized money market yield quoted on an **$\text{ACT}/365$** daycount convention (FIMMDA / RBI standard).
- **Mathematical Daily Conversion:**
  $$R_{f,t} = y_{t-1} \times \frac{\Delta d_t}{365.0}$$
  where $y_{t-1}$ is the annualized decimal yield known at date $t-1$ under PIT cutoff, and $\Delta d_t = \text{Date}_t - \text{Date}_{t-1}$ is the elapsed calendar days.
- **Weekend / Multi-Day Holiday Accrual:** $\Delta d_t = 3 \implies R_{f,t} = y_{\text{Fri}} \times \frac{3}{365.0}$.
- **Lookback & Staleness Limit:** Preceding lookback up to 4 calendar days. If gap $> 4$ calendar days, halt with insufficient data.

### 5.2 Test Vectors & Results

| Vector ID | Scenario | Inputs | Expected $R_{f,t}$ | Actual Result | Discrepancy | Validation Result |
|---|---|---|---|---|---|---|
| **V2.1** | Single Weekday Step ($\Delta d = 1$) | $y_{t-1} = 6.95\%$ ($0.0695$) | $0.0001904109589041$ | $0.0001904109589041$ | $0.00$ | PASS |
| **V2.2** | Weekend 3-Day Step ($\Delta d = 3$) | $y_{\text{Fri}} = 6.95\%$ ($0.0695$) | $0.0005712328767123$ | $0.0005712328767123$ | $0.00$ | PASS |
| **V2.3** | Missing Day Lookback ($\le 4\text{d}$) | Quote missing on $T$; prior available $T-1 = 0.0692$ | $0.0692$ resolved | $0.0692$ resolved | $0.00$ | PASS |
| **V2.4** | Stale Observation ($> 4\text{d}$) | Gap $= 6\text{d} > 4\text{d}$ | Insufficient history (`NULL`) | Execution halted | N/A | PASS (Error Handling) |
| **V2.5** | PIT Knowledge Cutoff | Published $17:30\text{ IST}$; Cutoff $15:30\text{ IST}$ | Excluded from calculation | Excluded from calculation | Zero Leakage | PASS (PIT) |

### 5.3 Validation Assessment
- **Validation Result:** **`VALIDATION PASS`**
- **Lifecycle State:** `CANDIDATE / PROPOSED FOR APPROVAL`
- **Resolution:** Grounded in statutory FIMMDA / FBIL $\text{ACT}/365$ daycount rules; established explicit linear daily de-annualization and multi-day calendar accrual formulas with a 4-day staleness bound.

---

## 6. M2N-05 Results — Treynor Ratio Numerator

### 6.1 Candidate Convention
- **Formula:** $\text{Treynor} = \frac{\text{Annualized Excess Return}}{\beta_p} = \frac{\bar{e}_{p, \text{annual}}}{\beta_p}$
- **Numerator:** $\bar{e}_{p, \text{annual}} = \bar{R}_{\text{excess, daily}} \times 252 = \left(\frac{1}{N}\sum_{t=1}^N (R_{p,t} - R_{f,t})\right) \times 252$, where $R_{f,t}$ is resolved via M2N-02.
- **Denominator:** $\beta_p$ from M2N-06 excess-return OLS regression.
- **Analytical Window:** 36 calendar months ($\ge 700$ paired trading days).

### 6.2 Test Vectors & Results

| Vector ID | Scenario | Inputs ($N=10$) | Expected Treynor | Actual Result | Discrepancy | Validation Result |
|---|---|---|---|---|---|---|
| **V5.1** | Positive Excess Return | $\bar{e}_{p, \text{annual}} = 0.110776, \beta = 1.15$ | $0.0963269565217391$ | $0.0963269565217391$ | $0.00$ | PASS |
| **V5.2** | Zero Excess Return | $R_p = R_f$ for all $t$ | $0.0000000000000000$ | $0.0000000000000000$ | $0.00$ | PASS |
| **V5.3** | Negative Excess Return | $\bar{e}_{p, \text{annual}} = -0.035383, \beta = 1.15$ | $-0.0307678260869565$ | $-0.0307678260869565$ | $0.00$ | PASS |
| **V5.4** | Zero Beta ($\beta=0$) | $\beta = 0.0$ | Undefined ($\text{ZeroDivisionError}$) | Rejected | N/A | PASS (Error Handling) |

### 6.3 Validation Assessment
- **Validation Result:** **`VALIDATION PASS`**
- **Lifecycle State:** `CANDIDATE / PROPOSED FOR APPROVAL`
- **Resolution:** Formally anchored numerator to annualized arithmetic mean daily excess return ($\times 252$), maintaining econometric consistency with M2N-06 OLS regression and Treynor (1965) CAPM foundations.

---

## 7. M2N-06 Results — Portfolio Beta (Excess-Return OLS)

### 7.1 Candidate Convention
- **Model:** Single-index excess-return OLS regression with intercept $\alpha$:
  $$(R_{p,t} - R_{f,t}) = \alpha + \beta (R_{b,t} - R_{f,t}) + \epsilon_t$$
- **Closed-Form Estimator:** $\hat{\beta} = \frac{\sum_{t=1}^N (x_t - \bar{x})(y_t - \bar{y})}{\sum_{t=1}^N (x_t - \bar{x})^2} = \frac{\text{Cov}(x, y)}{\text{Var}(x)}$, where $y_t = R_{p,t} - R_{f,t}$ and $x_t = R_{b,t} - R_{f,t}$.
- **Window & Minimum Observations:** 36 calendar months, $\ge 700$ paired trading days (inherited from Phase 2H MKT-01).

### 7.2 Test Vectors & Results

| Vector ID | Scenario | Inputs ($N=5$) | Expected $\hat{\beta} / \hat{\alpha}$ | Actual (Pure Python) | Independent (NumPy / Decimal) | Discrepancy | Validation Result |
|---|---|---|---|---|---|---|---|
| **V6.1** | Standard Paired Returns | $R_p=[0.010, -0.005, 0.015, 0.000, 0.020]$<br>$R_b=[0.008, -0.004, 0.012, 0.001, 0.016]$<br>$R_f=[0.0002] \times 5$ | $\hat{\beta}=1.2765957446808511$<br>$\hat{\alpha}=-0.0003702127659574$ | $\hat{\beta}=1.2765957446808509$<br>$\hat{\alpha}=-0.0003702127659574$ | $\hat{\beta}=1.2765957446808514$<br>$\hat{\alpha}=-0.0003702127659574$ | $\Delta\beta = 4.44 \times 10^{-16}$<br>$\Delta\alpha = 0.00$ | PASS |
| **V6.2** | Benchmark Perfect Replication | $R_p \equiv R_b$ for all $t$ | $\hat{\beta}=1.0000000000000000$<br>$\hat{\alpha}=0.0000000000000000$ | $\hat{\beta}=1.0000000000000000$<br>$\hat{\alpha}=0.0000000000000000$ | $\hat{\beta}=1.0000000000000000$<br>$\hat{\alpha}=0.0000000000000000$ | $0.00$ | PASS |
| **V6.3** | Zero Benchmark Variance | $R_b = [0.005] \times 5 \implies \text{Var}(x)=0$ | Undefined ($\text{ZeroDivisionError}$) | Rejected (Singular Matrix) | Rejected | N/A | PASS (Error Handling) |
| **V6.4** | Insufficient History ($N < 700$) | $N = 650 < 700$ | Insufficient history (`NULL`) | Execution halted | N/A | PASS (Boundary) |

### 7.3 Validation Assessment
- **Validation Result:** **`VALIDATION PASS`**
- **Lifecycle State:** `CANDIDATE / PROPOSED FOR APPROVAL`
- **Resolution:** Reconfirmed exact OLS formulation and codified minimum observation parameter ($\ge 700$ paired trading days) inherited from Phase 2H.

---

## 8. M2N-07 Results — Downside Beta ($R_{b,t} < 0$)

### 8.1 Candidate Convention
- **Conditioning Filter:** Benchmark return $R_{b,t} < 0$ (strict inequality). $R_{b,t} = 0.0$ and $R_{b,t} > 0.0$ are strictly **EXCLUDED**.
- **Downside Subset:** $\mathcal{D} = \{t : R_{b,t} < 0\}$.
- **Formula:** Sample covariance divided by sample variance on raw returns over downside subset:
  $$\beta_{\text{down}} = \frac{\text{Cov}(R_{p,t}, R_{b,t} \mid t \in \mathcal{D})}{\text{Var}(R_{b,t} \mid t \in \mathcal{D})} = \frac{\sum_{t \in \mathcal{D}} (R_{p,t} - \bar{R}_{p,\mathcal{D}})(R_{b,t} - \bar{R}_{b,\mathcal{D}})}{\sum_{t \in \mathcal{D}} (R_{b,t} - \bar{R}_{b,\mathcal{D}})^2}$$
- **Minimum Downside Observations:** $\ge 100$ qualifying downside trading days ($|\mathcal{D}| \ge 100$) within the 36-month window (inherited from Phase 2H MKT-02).

### 8.2 Test Vectors & Results

| Vector ID | Scenario | Inputs | Downside Subsample ($R_b < 0$) | Expected $\beta_{\text{down}}$ | Actual (Pure Python) | Independent (NumPy) | Discrepancy | Validation Result |
|---|---|---|---|---|---|---|---|---|
| **V7.1** | Mixed Market Returns | $R_b=[0.010, -0.005, 0.000, -0.012, 0.008, -0.003, 0.000, 0.015, -0.008, 0.002]$<br>$R_p=[0.012, -0.006, 0.001, -0.014, 0.009, -0.004, -0.001, 0.016, -0.009, 0.003]$ | 4 pairs:<br>$(-0.006, -0.005)$<br>$(-0.014, -0.012)$<br>$(-0.004, -0.003)$<br>$(-0.009, -0.008)$ | $1.1086956521739131$ | $1.1086956521739131$ | $1.1086956521739133$ | $2.22 \times 10^{-16}$ | PASS |
| **V7.2** | Benchmark Zero Returns ($R_b = 0$) | Observations with $R_b=0.000$ | Strictly EXCLUDED | Filtered | Filtered | $0.00$ | PASS |
| **V7.3** | Benchmark Positive Returns ($R_b > 0$) | Observations with $R_b > 0.000$ | Strictly EXCLUDED | Filtered | Filtered | $0.00$ | PASS |
| **V7.4** | Insufficient Down-Days ($|\mathcal{D}| < 100$) | $|\mathcal{D}| = 65 < 100$ | Insufficient history (`NULL`) | Returns `NULL` | Returns `NULL` | N/A | PASS (Boundary) |
| **V7.5** | Zero Downside Variance | $R_b = [-0.005] \times 5 \implies \text{Var}(R_b)=0$ | Undefined ($\text{ZeroDivisionError}$) | Rejected | Rejected | N/A | PASS (Error Handling) |

### 8.3 Validation Assessment
- **Validation Result:** **`VALIDATION PASS`**
- **Lifecycle State:** `CANDIDATE / PROPOSED FOR APPROVAL`
- **Resolution:** Formally codified raw returns regression adhering strictly to Phase 2H MKT-02 ($\text{Risk-Free Dependency} = \text{FALSE}$) and empirical asset pricing literature (Ang, Chen, Xing, 2006), and codified the $\ge 100$ down-day minimum observation threshold.

---

## 9. Cross-Methodology Consistency

The refined validation verified full architectural coherence across the dependency chain:
$$\text{M2N-02 (Risk-Free Proxy)} \longrightarrow \text{M2N-05 (Treynor)} \longrightarrow \text{M2N-06 (Beta)} \longrightarrow \text{M2N-07 (Downside Beta)}$$

1. **Return Horizon & Frequency Consistency:** M2N-02 linear daily de-annualization feeds directly into M2N-06 daily excess-return OLS, which in turn defines $\beta_p$ and mean excess return for M2N-05.
2. **Raw vs. Excess Return Rationale:** M2N-06 correctly uses excess returns $(R_p - R_f, R_b - R_f)$ for CAPM beta estimation, while M2N-07 correctly uses raw returns conditioned on $R_{b,t} < 0$ to isolate nominal equity market drawdown sensitivity per Phase 2H MKT-02.
3. **Scale Invariance Alignment:** M2N-01 explicitly clarifies that Beta is unannualized, resolving the prior misconception.

---

## 10. Point-in-Time (PIT) Validation

- **Effective Date Constraint:** $\text{effective\_date} \le \text{analysis\_cutoff}$ strictly enforced.
- **Availability Time Constraint:** $\text{availability\_time} \le \text{knowledge\_cutoff}$ strictly enforced.
- **Zero Information Leakage:** Historical revisions published after knowledge cutoff are completely excluded from calculations.
- **Status:** **`PIT VALIDATION PASS`**.

---

## 11. Deterministic Reproducibility

- **Concordance:** Multiple consecutive test runs produced bit-for-bit identical floats ($0.0$ variance).
- **Independent Engine Cross-Check:** Pure Python rational estimators and NumPy vectorized implementations matched to within $\le 4.44 \times 10^{-16}$.
- **Status:** **`REPRODUCIBILITY PASS`**.

---

## 12. Validation Deficiencies & Resolutions Summary

| ID | Candidate Name | Previous Validation Result | Refinement & Resolution | Current Validation Result |
|---|---|---|---|---|
| **M2N-01** | Annualization | INCONCLUSIVE | Explicitly defined Sharpe numerator annualization ($\times 252$) and established that Beta is scale-invariant without an annualization factor. | **VALIDATION PASS** |
| **M2N-02** | Risk-Free Proxy | INCONCLUSIVE | Grounded in FIMMDA/FBIL $\text{ACT}/365$ money market rules; codified linear daily de-annualization ($R_{f,t} = y_{t-1} \times \frac{\Delta d_t}{365}$), weekend 3-day accrual, and 4-day staleness bound. | **VALIDATION PASS** |
| **M2N-05** | Treynor Numerator | INCONCLUSIVE | Codified annualized arithmetic mean daily excess return ($\times 252$) over 36M horizon matching M2N-06 OLS beta. | **VALIDATION PASS** |
| **M2N-06** | Beta Specification | PASS | Reconfirmed excess-return OLS regression and codified $\ge 700$ paired trading day threshold. | **VALIDATION PASS** |
| **M2N-07** | Downside Beta | INCONCLUSIVE | Reaffirmed raw-return formulation per Phase 2H MKT-02, verified strict exclusion of $R_b \ge 0$, and codified $\ge 100$ down-day minimum threshold. | **VALIDATION PASS** |

---

## 13. Recommended Governance Outcome

All five Phase 2N V2 candidate methodologies have achieved **`VALIDATION PASS`** and are fully specified, mathematically sound, deterministic, and ready for **Formal Human Governance Committee Adjudication**.

| Candidate ID | Methodology Name | Technical Validation Result | Lifecycle State | Next Governance Milestone |
|---|---|---|---|---|
| **M2N-01** | Metric-Specific Annualization | **VALIDATION PASS** | CANDIDATE / PROPOSED FOR APPROVAL | Human Governance Review |
| **M2N-02** | FBIL 91-Day T-Bill Risk-Free | **VALIDATION PASS** | CANDIDATE / PROPOSED FOR APPROVAL | Human Governance Review |
| **M2N-05** | Treynor Numerator ($R_p - R_f$) | **VALIDATION PASS** | CANDIDATE / PROPOSED FOR APPROVAL | Human Governance Review |
| **M2N-06** | Excess-Return OLS Beta | **VALIDATION PASS** | CANDIDATE / PROPOSED FOR APPROVAL | Human Governance Review |
| **M2N-07** | Downside Beta ($R_b < 0$) | **VALIDATION PASS** | CANDIDATE / PROPOSED FOR APPROVAL | Human Governance Review |

---

## 14. Validation Evidence Index

- Validation Test Suite: `docs/research/validation/run_full_validation.py`
- Test Execution Output: 100% deterministic test vector pass rate, zero division errors caught and handled, float64 precision agreement verified.

---

## 15. Final Status & Lifecycle Sign-off

- **Methodologies Passing Validation:** 5 of 5 (`M2N-01`, `M2N-02`, `M2N-05`, `M2N-06`, `M2N-07`)
- **Methodologies Inconclusive:** 0
- **Methodologies Failed:** 0
- **Deferred Methodologies (Untouched):** 4 (`M2N-03`, `M2N-04`, `M2N-08`, `M2N-09`)
- **Methodologies in Lifecycle State VALIDATED:** **`0`** (Strict governance separation enforced)
- **Methodologies in Lifecycle State APPROVED:** **`0`**
- **Phase 2O Production Implementation Authorized:** **`NO`**
- **Phase 2H Frozen:** **`YES`** (`phase2h_quantitative_methodology.md` ZERO DIFF)
