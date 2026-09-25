# YUKIRA — Phase 2N Methodology V2

**Document:** `docs/methodology/phase_2n_v2.md`
**Phase:** 2N / V2
**Status:** PROPOSED METHODOLOGY SPECIFICATION (CANDIDATE)
**Implementation Status:** NOT AUTHORIZED

---

# 1. Purpose
Define the proposed methodology specification for 5 metrics identified in Phase 2N research as sufficiently defensible for formal governance review.

---

# 2. Relationship to Phase 2H (Frozen)
This document acts as a proposed successor amendment for specific Phase 2N adjudicated decisions. All definitions in `phase2h_quantitative_methodology.md` remain the Frozen baseline for all Phase 2I–2M implemented metrics.

---

# 3. Phase 2N Governance Basis & Approval Lineage
Approval of this methodology version is formally grounded in:
1. **Phase 2N Empirical Research:** `docs/research/PHASE_2N_METHODOLOGY_RESOLUTION.md`
2. **Phase 2N Governance Adjudication:** `docs/research/PHASE_2N_GOVERNANCE_DECISIONS.md`
3. **Phase 2N Independent Validation Audit:** `docs/research/PHASE_2N_V2_VALIDATION.md`
4. **Phase 2N Formal Governance Approval Record:** `docs/research/PHASE_2N_FORMAL_APPROVAL.md`
5. **Statutory Standards:** Authoritative market/regulatory guidelines (FBIL, FIMMDA, RBI, SEBI).

---

# 4. V2 Decision Register (Approved & Deferred)

| ID | Decision | Approved Working Convention | Validation Result | Approval Status | Lifecycle State |
|---|---|---|---|---|---|
| **M2N-01** | Annualization | Metric-Specific (CAGR: $365.25/D$; Vol/TE/Sharpe: $\sqrt{252}$; Beta: Unannualized) | PASS | APPROVED | APPROVED |
| **M2N-02** | Risk-Free Proxy | FBIL 91-Day T-Bill Yield with $\text{ACT}/365$ linear daily de-annualization ($R_{f,t} = y_{t-1} \times \frac{\Delta d_t}{365}$) | PASS | APPROVED | APPROVED |
| **M2N-05** | Treynor Numerator | Annualized arithmetic mean daily excess return ($\bar{R}_{\text{excess, daily}} \times 252$) over 36M horizon | PASS | APPROVED | APPROVED |
| **M2N-06** | Beta Specification | Single-index excess-return OLS regression with intercept: $(R_{p,t} - R_{f,t}) = \alpha + \beta (R_{b,t} - R_{f,t}) + \epsilon_t$ | PASS | APPROVED | APPROVED |
| **M2N-07** | Downside Beta | Subsample raw-return covariance/variance ratio conditioned on $R_{b,t} < 0$, with $\ge 100$ down-days threshold | PASS | APPROVED | APPROVED |

### Deferred Methodologies (Out of Scope for Implementation):
| ID | Methodology Name | Validation Status | Approval Status | Lifecycle State |
|---|---|---|---|---|
| **M2N-03** | Sortino Ratio / Minimum Acceptable Return (MAR) | Deferred | DEFERRED | DEFERRED |
| **M2N-04** | Downside Semideviation Divisor ($N$ vs $N-1$ vs $K$) | Deferred | DEFERRED | DEFERRED |
| **M2N-08** | Upside/Downside Capture Subset Compounding | Deferred | DEFERRED | DEFERRED |
| **M2N-09** | Capture Spread Calculation | Deferred | DEFERRED | DEFERRED |

---

# 5. Detailed Approved Methodology Specifications

## M2N-01 — Metric-Specific Annualization Framework

### 1. Mathematical Specifications:
- **CAGR (Compound Annual Growth Rate):**
  $$\text{CAGR} = \left(\frac{\text{Ending NAV}}{\text{Starting NAV}}\right)^{\frac{365.25}{\text{elapsed\_calendar\_days}}} - 1$$
  - **Daycount Divisor:** $365.25$ calendar days (Julian year normalization, exactly accounting for 1 leap year every 4-year cycle: $1461 / 4 = 365.25$).
  - **Path Independence:** Relies strictly on boundary observations ($\text{NAV}_{start}, \text{NAV}_{end}$) and elapsed calendar days $D = \text{Date}_{end} - \text{Date}_{start}$.

- **Annualized Return Volatility:**
  $$\sigma_{\text{annual}} = s \times \sqrt{252}, \quad s = \sqrt{\frac{1}{N-1}\sum_{t=1}^N (R_t - \bar{R})^2}$$
  - **Trading Days Factor:** Fixed 252 trading days per annual cycle.
  - **Statistical Estimator:** Unbiased sample standard deviation with Bessel's correction ($N-1$ divisor).

- **Tracking Error:**
  $$\text{TE}_{\text{annual}} = s_{\text{active}} \times \sqrt{252}, \quad s_{\text{active}} = \sqrt{\frac{1}{N-1}\sum_{t=1}^N (R_{\text{active}, t} - \bar{R}_{\text{active}})^2}$$
  where $R_{\text{active}, t} = R_{p,t} - R_{b,t}$.

- **Sharpe Ratio Annualization:**
  $$\text{Sharpe} = \frac{\bar{R}_{\text{excess, daily}}}{\sigma_{\text{excess, daily}}} \times \sqrt{252} = \frac{\bar{R}_{\text{excess, daily}} \times 252}{\sigma_{\text{excess, daily}} \times \sqrt{252}}$$
  where $\bar{R}_{\text{excess, daily}} = \frac{1}{N}\sum_{t=1}^N (R_{p,t} - R_{f,t})$ and $\sigma_{\text{excess, daily}} = \sqrt{\frac{1}{N-1}\sum_{t=1}^N (R_{p,t} - R_{f,t} - \bar{R}_{\text{excess}})^2}$.
  - Conforms to canonical Sharpe (1994) and Andrew Lo (2002) formulations for time-aggregated returns.

- **Beta Scale Invariance:**
  - **Beta is NOT annualized.** Beta is a dimensionless regression coefficient ($\beta = \frac{\text{Cov}(x, y)}{\text{Var}(x)}$). Any scaling factor (e.g. $\sqrt{252}$) applied to periodic returns scales both the covariance numerator and variance denominator by 252, cancelling out identically ($252/252 = 1$).

---

## M2N-02 — Risk-Free Rate (FBIL 91-Day T-Bill)

### 1. Authoritative Source:
- **Benchmark Identity:** Financial Benchmarks India Pvt Ltd (FBIL) 91-Day Treasury Bill benchmark cutoff yield curve.
- **Regulatory Standing:** Official sovereign risk-free benchmark recognized by RBI, SEBI, and FIMMDA for domestic Indian money markets.

### 2. Quote Convention & Daycount Standard:
- **Quoted Rate ($y$):** Annualized money-market yield quoted on an **$\text{ACT}/365$** daycount convention (FIMMDA / FBIL operating standard for Indian Treasury Bills).

### 3. Mathematical Yield-to-Return Conversion:
The periodic risk-free return $R_{f,t}$ applicable to the holding period between observation date $t-1$ and date $t$ is:
$$R_{f,t} = y_{t-1} \times \frac{\Delta d_t}{365.0}$$
where:
- $y_{t-1}$ is the annualized decimal yield published and known at date $t-1$ under PIT cutoff.
- $\Delta d_t = \text{Date}_t - \text{Date}_{t-1}$ is the elapsed calendar days between NAV observations.

### 4. Calendar Alignment & Non-Trading Periods:
- **Single Trading Day Step ($\Delta d_t = 1$):** $R_{f,t} = y_{t-1} / 365.0$.
- **Weekend / Multi-Day Holiday Step ($\Delta d_t > 1$):** For non-trading intervals (e.g., Friday to Monday, $\Delta d_t = 3$), $R_{f,t} = y_{\text{Fri}} \times \frac{3}{365.0}$, which correctly captures 3 days of risk-free interest accrual matching the multi-day NAV return.

### 5. Missing Data, Staleness, and PIT Enforcement:
- **Preceding Lookback:** If a quote is missing on a business day, bridge using the latest available quote within a maximum lookback of 4 calendar days. If no quote exists within 4 calendar days, halt execution with insufficient data.
- **Point-in-Time Constraint:** Strictly $\text{availability\_time} \le \text{knowledge\_cutoff}$. Quotes published at EOD ($\sim 17:30\text{ IST}$) cannot be used if calculation knowledge cutoff is prior to publication time.

---

## M2N-05 — Treynor Ratio Numerator

### 1. Mathematical Formulation:
$$\text{Treynor} = \frac{\text{Annualized Portfolio Excess Return}}{\text{Portfolio Beta}} = \frac{\bar{e}_{p, \text{annual}}}{\beta_p}$$

### 2. Numerator & Component Definitions:
- **Annualized Excess Return ($\bar{e}_{p, \text{annual}}$):**
  $$\bar{e}_{p, \text{annual}} = \bar{R}_{\text{excess, daily}} \times 252 = \left(\frac{1}{N}\sum_{t=1}^N (R_{p,t} - R_{f,t})\right) \times 252$$
  where $R_{f,t}$ is the daily risk-free return resolved per M2N-02.
- **Denominator ($\beta_p$):** Portfolio beta determined via M2N-06 excess-return OLS regression.
- **Analytical Window:** 36 calendar months ($\ge 700$ paired trading days).

### 3. Boundary Conditions:
- If $\beta_p = 0.0$, calculation halts with division-by-zero error.
- If $\beta_p < 0.0$, the result is flagged with an inverted evaluation diagnostic (negative beta paradox).

---

## M2N-06 — Portfolio Beta (Excess-Return Single-Index OLS)

### 1. Econometric Specification:
Single-index excess-return Ordinary Least Squares (OLS) regression with an intercept $\alpha$:
$$(R_{p,t} - R_{f,t}) = \alpha + \beta (R_{b,t} - R_{f,t}) + \epsilon_t$$

### 2. Closed-Form Estimators:
$$\hat{\beta} = \frac{\sum_{t=1}^N (x_t - \bar{x})(y_t - \bar{y})}{\sum_{t=1}^N (x_t - \bar{x})^2} = \frac{\text{Cov}(x, y)}{\text{Var}(x)}, \quad \hat{\alpha} = \bar{y} - \hat{\beta}\bar{x}$$
where:
- Dependent variable: $y_t = R_{p,t} - R_{f,t}$
- Independent variable: $x_t = R_{b,t} - R_{f,t}$
- $R_{b,t}$ is the benchmark Total Return Index (TRI) daily simple return.
- $R_{f,t}$ is the daily risk-free return from M2N-02.

### 3. Window & Observation Thresholds:
- **Analytical Window:** 36 calendar months.
- **Minimum Observations:** $\ge 700$ synchronous paired trading days (inherited from Phase 2H MKT-01). If paired count $< 700$, returns insufficient data (`NULL`).
- **Zero Variance Rule:** If $\text{Var}(x) = 0$ (constant benchmark excess return), halts with division-by-zero error.

---

## M2N-07 — Downside Beta Specification

### 1. Conditioning Rule & Subset Definition:
Downside beta isolates portfolio sensitivity during nominal benchmark market downturns:
$$\mathcal{D} = \{t : R_{b,t} < 0\}$$
- **Strict Inequality:** Observations where benchmark return $R_{b,t} = 0.0$ or $R_{b,t} > 0.0$ are strictly **EXCLUDED** from the downside regression.

### 2. Mathematical Formulation:
$$\beta_{\text{down}} = \frac{\text{Cov}(R_{p,t}, R_{b,t} \mid t \in \mathcal{D})}{\text{Var}(R_{b,t} \mid t \in \mathcal{D})} = \frac{\sum_{t \in \mathcal{D}} (R_{p,t} - \bar{R}_{p,\mathcal{D}})(R_{b,t} - \bar{R}_{b,\mathcal{D}})}{\sum_{t \in \mathcal{D}} (R_{b,t} - \bar{R}_{b,\mathcal{D}})^2}$$
where $\bar{R}_{p,\mathcal{D}} = \frac{1}{|\mathcal{D}|}\sum_{t \in \mathcal{D}} R_{p,t}$ and $\bar{R}_{b,\mathcal{D}} = \frac{1}{|\mathcal{D}|}\sum_{t \in \mathcal{D}} R_{b,t}$.

### 3. Methodological Rationale for Raw Returns:
- Adheres strictly to Phase 2H MKT-02 invariant ($\text{Risk-Free Dependency} = \text{FALSE}$).
- Aligns with empirical asset pricing literature (Ang, Chen, Xing, 2006; Bawa & Lindenberg, 1977) defining downside market regimes relative to nominal zero to measure absolute equity drawdown sensitivity.

### 4. Minimum Observations & Boundary Thresholds:
- **Minimum Downside Observations:** $\ge 100$ benchmark down-days ($|\mathcal{D}| \ge 100$) within the 36-month window (inherited from Phase 2H MKT-02).
- **Insufficient History:** If $|\mathcal{D}| < 100$, calculation returns insufficient history (`NULL`).
- **Zero Variance Rule:** If $\text{Var}(R_b \mid t \in \mathcal{D}) = 0$, halts with division-by-zero error.

---

# 6. Excluded Methodologies (Deferred)
The following remain **DEFERRED** and are strictly **OUT OF SCOPE** for Phase 2O implementation:
- **M2N-03:** Sortino Ratio / Minimum Acceptable Return (MAR)
- **M2N-04:** Downside Semideviation Divisor ($N$ vs $N-1$ vs $K$)
- **M2N-08:** Upside/Downside Capture Subset Compounding
- **M2N-09:** Capture Spread

---

# 7. Important Epistemic Limitations
Formal methodology approval signifies:
> *"The mathematical specifications, daycount conventions, and statistical estimators have been validated for deterministic software implementation in YUKIRA."*

Approval does **NOT** imply:
1. That the calculated metrics possess guaranteed predictive power over future asset prices.
2. That the methodology is empirically proven superior to all alternative academic formulations.
3. That investment performance or risk outcomes are guaranteed.
4. That the methodology is permanently immutable; it remains subject to versioned relational governance.
5. That the four deferred candidate metrics are validated or approved.

---

# 8. Implementation Authorization Boundary
- **Phase 2O Implementation Authorized:** **`YES — FOR APPROVED M2N-01, M2N-02, M2N-05, M2N-06, M2N-07 ONLY`**.
- **Deferred Candidates:** M2N-03, M2N-04, M2N-08, M2N-09 remain strictly unauthorized.
- **Methodologies Validated:** 5
- **Methodologies Approved:** 5
- **Phase 2H Frozen:** **`YES`** (`phase2h_quantitative_methodology.md` ZERO DIFF)
