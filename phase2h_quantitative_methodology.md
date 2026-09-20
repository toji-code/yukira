# YUKIRA — PHASE 2H: MVP QUANTITATIVE METHODOLOGY SPECIFICATION

**Document Reference:** `phase2h_quantitative_methodology.md`  
**Phase:** Phase 2H (MVP Quantitative Methodology Specification)  
**Status:** DRAFT SPECIFICATION / CANDIDATE METHODOLOGY INVENTORY  
**Effective Date:** 2026-09-20  
**Governing Axiom:** `COMMON INDUSTRY PRACTICE ≠ YUKIRA-APPROVED METHODOLOGY`  
**Epistemic Core:** `IMPLEMENTED ≠ VALIDATED ≠ APPROVED PRODUCTION METHODOLOGY`

---

## 1. Executive Summary & Purpose

In quantitative financial engineering, software implementations are frequently conflated with financial truth. Code is often written using default conventions—such as 252 trading days, sample variance ($N-1$) versus population variance ($N$), or boundary lookback interpolation—without formal analysis of whether these conventions are appropriate for the specific market, asset class, or regulatory jurisdiction.

In **YUKIRA**, methodology is governed as an explicit, versioned, relational, and auditable asset. No quantitative metric is approved merely because it is widely practiced in commercial factsheets or because an algorithm produces numeric output without runtime errors. Software correctness and methodological validation are strictly separate: passing 100% of unit tests establishes only that the software executes as written; it does not establish that the financial methodology is validated or correct.

Phase 2H defines candidate quantitative methodology specifications for the 30 MVP analytical metrics. It does **not** approve any production methodology.

### Required Epistemic Status:
- **Implemented methodology:** `RET-02 Simple Period Return` only, inherited from Phase 2F.
- **Validated methodology:** `NONE`.
- **Approved production methodology:** `NONE`.
- **Empirical findings:** `ZERO`.
- **All other MVP metric methodologies:** `CANDIDATE / REQUIRES VALIDATION`.

The purpose of Phase 2H is **not** to immediately implement all candidate metrics in code. The purpose is to formally define, document, and govern:
- Exact candidate mathematical definitions and formula specifications
- Input data requirements, frequencies, and observation-date semantics
- Candidate analytical windows and minimum observation thresholds
- Candidate conventions versus unresolved methodology decisions
- Point-in-Time (PIT) requirements and information-set cutoffs
- Missing-data, insufficient-history, and invalid-data policies
- Pre-requisites for empirical validation and human governance sign-off

---

## 2. Epistemic Status & Current-State Alignment

To guarantee absolute epistemic clarity, the system maintains strict separation between what is implemented, what is empirically validated, and what is approved for production presentation.

### Exact Logical Current State:

| Item | Status |
|---|---|
| **RET-02 Simple Period Return** | **Implemented Candidate** |
| **1Y CAGR** | **Candidate / Requires Validation** |
| **3Y CAGR** | **Candidate / Requires Validation** |
| **5Y CAGR** | **Candidate / Requires Validation** |
| **Remaining MVP analytical metrics** | **Candidate / Requires Validation** |
| **Validated methodology** | **None** |
| **Approved production methodology** | **None** |
| **Empirical findings** | **Zero** |

---

## 3. Methodology Governance Invariants

All metrics specified in Phase 2H are bound by the relational governance architecture established in Phase 2G:

1. **Epistemic Invariant:**
   $$\text{IMPLEMENTED} \ne \text{VALIDATED} \ne \text{APPROVED PRODUCTION METHODOLOGY}$$
2. **One-Way State Machine:**
   $$\text{CANDIDATE} \longrightarrow \text{VALIDATED} \longrightarrow \text{APPROVED} \longrightarrow \text{RETIRED}$$
   - Direct promotion from `CANDIDATE` to `APPROVED` is rejected.
   - Demotion from `VALIDATED` or `APPROVED` backwards to `CANDIDATE` is rejected.
   - Demotion from `APPROVED` to `VALIDATED` is rejected.
   - Transition to `VALIDATED` requires an explicit, auditable `validation_evidence_reference`.
   - Transition to `APPROVED` requires formal `approval_record`, designated `approved_by` actor, and `approved_at` timestamp.
3. **Immutability & Locking State:**
   - An unused candidate methodology is mutable during drafting.
   - Once a `MethodologyVersion` is referenced by any historical `CalculationRun` or explicitly locked, its definition, conventions, and parameters become **permanently immutable**.
   - A locked candidate is an immutable historical artifact; locking does **not** imply validation or approval.
4. **Successor Versioning (Forking):** If an immutable methodology definition requires modification, a successor version must be forked (e.g. `CANDIDATE_V2`), linked via `supersedes_version_id`. The successor begins strictly as `CANDIDATE` + `UNVALIDATED`. The predecessor remains historically reproducible.
5. **No Silent Defaults:** No convention (daycount, divisor, hurdle, annualization) may be applied implicitly in procedural code. Every parameter must be explicitly governed in the relational methodology specification.

---

## 4. Exact MVP Analytical Scope

The YUKIRA quantitative architecture preserves exactly the original Phase 2B 30 analytical metrics across seven analytical dimensions (while the approved data-quality taxonomy contains six dimensions).

**CRITICAL INVARIANT:** `RET-02 Simple Period Return` is **NOT** one of these 30 analytical metrics. It is an already implemented supporting calculation primitive inherited from Phase 2F. The 30 analytical metrics remain exactly 30.

### 4.1 The 30 MVP Analytical Candidate Metrics:

| # | Candidate Code | Metric Name | Analytical Dimension | Target Question Answered | Epistemic Status | Existing Codebase Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **RET-01** | 1Y CAGR | Return Quality | Trailing 1-year compounded annualized growth | `CANDIDATE / REQUIRES VALIDATION` | Requires Assembly |
| 2 | **RET-03** | 3Y CAGR | Return Quality | Multi-year compounded annualized growth | `CANDIDATE / REQUIRES VALIDATION` | Distinct Candidate Metric (Not RET-02) |
| 3 | **RET-04** | 5Y CAGR | Return Quality | Full market cycle compounded annualized growth | `CANDIDATE / REQUIRES VALIDATION` | Requires Assembly |
| 4 | **RET-05** | 3Y Rolling Return Mean | Return Quality | Average rolling 3-year annualized return | `CANDIDATE / REQUIRES VALIDATION` | Existing Implementation (Requires Audit) |
| 5 | **RET-06** | Rolling Outperformance % | Return Quality | Proportion of rolling windows beating benchmark | `CANDIDATE / REQUIRES VALIDATION` | Needs Assembly |
| 6 | **RET-07** | 3Y Active Return | Return Quality | Annualized excess return over benchmark | `CANDIDATE / REQUIRES VALIDATION` | Existing Implementation (Requires Audit) |
| 7 | **RSK-01** | Annualized Volatility | Risk & Tail | Total dispersion of periodic returns | `CANDIDATE / REQUIRES VALIDATION` | Existing Implementation (Requires Audit) |
| 8 | **RSK-02** | Downside Semideviation | Risk & Tail | Dispersion of returns below target hurdle | `CANDIDATE / REQUIRES VALIDATION` | Existing Implementation (Conflict Present) |
| 9 | **RSK-03** | Maximum Drawdown 3Y | Risk & Tail | Peak-to-trough worst capital decline over 3Y | `CANDIDATE / REQUIRES VALIDATION` | Existing Implementation (Requires Audit) |
| 10 | **RSK-04** | Maximum Drawdown Duration | Risk & Tail | Calendar days spent underwater from peak to recovery | `CANDIDATE / REQUIRES VALIDATION` | Needs Assembly |
| 11 | **RSK-05** | Ulcer Index | Risk & Tail | Quadratic continuous measure of underwater stress | `CANDIDATE / REQUIRES VALIDATION` | Existing Implementation (Requires Audit) |
| 12 | **RSK-06** | Historical VaR 95% | Risk & Tail | 95th percentile historical daily loss threshold | `CANDIDATE / REQUIRES VALIDATION` | Existing Implementation (Requires Audit) |
| 13 | **RSK-07** | Expected Shortfall 95% | Risk & Tail | Conditional expectation of loss exceeding VaR | `CANDIDATE / REQUIRES VALIDATION` | Existing Implementation (Requires Audit) |
| 14 | **RAT-01** | Sharpe Ratio 3Y | Risk-Adjusted | Annualized excess return per unit of total risk | `CANDIDATE / REQUIRES VALIDATION` | Existing Implementation (Requires Audit) |
| 15 | **RAT-02** | Sortino Ratio 3Y | Risk-Adjusted | Annualized excess return per unit downside risk | `CANDIDATE / REQUIRES VALIDATION` | Existing Implementation (Conflict Present) |
| 16 | **RAT-03** | Treynor Ratio 3Y | Risk-Adjusted | Annualized excess return per unit systematic risk | `CANDIDATE / REQUIRES VALIDATION` | Existing Implementation (Requires Audit) |
| 17 | **RAT-04** | Information Ratio 3Y | Risk-Adjusted | Annualized active return per unit tracking error | `CANDIDATE / REQUIRES VALIDATION` | Existing Implementation (Requires Audit) |
| 18 | **MKT-01** | Beta 3Y | Market Sensitivity | Co-movement sensitivity relative to benchmark | `CANDIDATE / REQUIRES VALIDATION` | Existing Implementation (Requires Audit) |
| 19 | **MKT-02** | Downside Beta | Market Sensitivity | Sensitivity during benchmark down-market regimes | `CANDIDATE / REQUIRES VALIDATION` | Existing Implementation (Requires Audit) |
| 20 | **MKT-03** | Upside Capture | Market Sensitivity | Compounded return captured during up-markets | `CANDIDATE / REQUIRES VALIDATION` | Existing Implementation (Requires Audit) |
| 21 | **MKT-04** | Downside Capture | Market Sensitivity | Compounded loss captured during down-markets | `CANDIDATE / REQUIRES VALIDATION` | Existing Implementation (Requires Audit) |
| 22 | **MKT-05** | Capture Spread | Market Sensitivity | Linear difference: Upside Capture minus Downside Capture | `CANDIDATE / REQUIRES VALIDATION` | Needs Assembly |
| 23 | **REL-01** | Tracking Error 3Y | Benchmark / Alpha | Annualized standard deviation of active returns | `CANDIDATE / REQUIRES VALIDATION` | Existing Implementation (Requires Audit) |
| 24 | **REL-02** | Jensen's Alpha 3Y | Benchmark / Alpha | Annualized intercept over single-index CAPM | `CANDIDATE / REQUIRES VALIDATION` | Existing Implementation (Requires Audit) |
| 25 | **PRT-01** | Top-10 Concentration | Portfolio Structure | Aggregate weight of 10 largest holdings | `CANDIDATE / REQUIRES VALIDATION` | Needs Assembly |
| 26 | **PRT-02** | Effective Number of Holdings | Portfolio Structure | Inverse Herfindahl-Hirschman concentration count | `CANDIDATE / REQUIRES VALIDATION` | Existing Implementation (Requires Audit) |
| 27 | **PRT-03** | Active Share | Portfolio Structure | Proportion of holdings differing from benchmark | `CANDIDATE / REQUIRES VALIDATION` | Existing Implementation (Requires Audit) |
| 28 | **PRT-04** | Monthly Weight Turnover | Portfolio Structure | Portfolio reallocated weight fraction between snapshots | `CANDIDATE / REQUIRES VALIDATION` | Existing Implementation (Requires Audit) |
| 29 | **PRT-05** | Cash & Equivalent Allocation % | Portfolio Structure | Proportion of net assets in cash/cash-equivalents | `CANDIDATE / REQUIRES VALIDATION` | Needs Assembly |
| 30 | **GOV-01** | Direct Plan TER | Governance / Expense | Declared point-in-time Total Expense Ratio | `CANDIDATE / REQUIRES VALIDATION` | Needs Assembly |

### 4.2 Supporting Implemented Calculation Methodology Primitive:

| Primitive Code | Methodology Code | Methodological Purpose | Epistemic Status | Existing Codebase Status |
| :--- | :--- | :--- | :--- | :--- |
| **RET-02** | `RET_02_SIMPLE_RETURN` | Discrete point-to-point unannualized price return over arbitrary period | `IMPLEMENTED CANDIDATE` | Implemented in Phase 2F (`CANDIDATE_V1`); registered in Phase 2G DB (`V7`) |

---

## 5. RET-02 — Correct Identity

### Authoritative Architectural Identity:
In the Phase 2F implementation and the Phase 2G database seed (`V7__create_methodology_governance.sql`), metric `RET-02` is formally registered as:
- **Metric Code:** `RET-02`
- **Methodology Code:** `RET_02_SIMPLE_RETURN`
- **Version Tag:** `CANDIDATE_V1`
- **Identity:** Simple Period Return
- **Formula:**
  $$\text{Return} = \frac{\text{Ending NAV}}{\text{Starting NAV}} - 1$$
- **Equivalent Percentage Form:**
  $$\text{Return \%} = \left(\frac{\text{Ending NAV}}{\text{Starting NAV}} - 1\right) \times 100$$
- **Lifecycle Status:** `CANDIDATE`
- **Validation Status:** `UNVALIDATED`
- **Approval Status:** `NONE / NULL`

### Mandatory Rules:
1. `RET-02` MUST mean **Simple Period Return**.
2. `RET-02` is **NOT** CAGR. Any association of `RET-02` with multi-year compounded annualized growth is strictly prohibited.
3. The existing Phase 2F four-calendar-day preceding NAV lookback for non-trading boundaries remains the candidate supporting methodology already implemented for `RET-02`.
4. Under no circumstances may the Phase 2F calculation engine or database seed be mutated merely to match an inaccurate documentation claim. The specification strictly reflects the true code implementation.

---

## 6. 3Y CAGR — Separate Analytical Metric

### Architectural Separation from RET-02:
3Y CAGR is a separate analytical metric from `RET-02`. CAGR must never be merged into `RET-02`.

- **Candidate Metric Code:** `RET-03 = 3Y CAGR`
- **Candidate Formula:**
  $$\text{CAGR} = \left(\frac{\text{Ending NAV}}{\text{Starting NAV}}\right)^{\frac{365.25}{\text{elapsed\_calendar\_days}}} - 1$$
- **Annualization Governance Rule:**
  > **"365.25-day annualization is a candidate convention requiring validation; no annualization convention is approved for production."**
- **Epistemic Invariant:**
  - `RET-02` = Simple Period Return (Implemented Candidate Primitive)
  - `3Y CAGR` = Separate Analytical Metric (`RET-03`, Candidate / Requires Validation)

---

## 7. Annualization — Exact Status

There is **NO approved annualization convention** in Phase 2H.

The following are **candidate conventions only**:
- 252 trading days
- 365 calendar days
- 365.25 calendar days
- 12 months

For annualized return calculations, the current candidate formulation is:
$$\text{annualized\_return} = \left(\frac{\text{EndingValue}}{\text{StartingValue}}\right)^{\frac{365.25}{\text{elapsed\_calendar\_days}}} - 1$$
**BUT:** $365.25$ remains `CANDIDATE / UNRESOLVED / REQUIRES VALIDATION`.

For volatility and other annualized statistics, 252 trading days is documented strictly as a candidate engineering convention, but it is **NOT approved**.

Language implying that *"YUKIRA uses 252"* or *"YUKIRA uses 365.25"* as an approved production standard is prohibited. Every annualization parameter carries the classification:
$$\text{"Candidate convention; requires validation."}$$

---

## 8. Convention Decision Register

Where multiple defensible conventions exist in mathematical finance or industry practice, the choice must not be silently resolved or declared as approved. Each decision is categorized below:

| Convention Item | Current Candidate Convention | Competing Defensible Alternatives | Current Status | Validation Required |
| :--- | :--- | :--- | :--- | :--- |
| **CAGR Annualization** | Calendar daycount: $(365.25 / D)$ | Fixed 365.0 days; Trading days $(252 / N_{trading})$; actual elapsed days | `CANDIDATE / REQUIRES VALIDATION` | Empirical test against SEBI/AMFI published figures; leap year sensitivity |
| **Volatility Annualization** | Multiplied by $\sqrt{252}$ | Multiplied by $\sqrt{248}$ (actual Indian exchange days); calendar $\sqrt{365}$ | `CANDIDATE / REQUIRES VALIDATION` | Sensitivity analysis of annual volatility divergence across Indian market calendars |
| **Semideviation Divisor** | Under active contradiction in codebase | Sample divisor $N-1$ (`semideviation.py`) vs. Population divisor $N$ (`statistics.py`) vs. Downside count $K$ | `UNRESOLVED / REQUIRES VALIDATION` | Formal statistical audit and governance resolution; code consolidation |
| **Sharpe Risk-Free Rate** | Requires research | FBIL Overnight MIBOR vs. FBIL 91-Day T-Bill Yield | `REQUIRES RESEARCH` | Sourcing, de-annualization, and historical gap analysis |
| **Sortino Hurdle ($MAR$)** | Nominal Zero ($MAR = 0$) | Risk-Free Rate ($MAR = R_f$); Cost of capital hurdle | `UNRESOLVED / REQUIRES VALIDATION` | Methodological alignment with investor decision posture |
| **Treynor Numerator** | Candidate formulation unfinalized | Annualized daily excess return vs. CAGR-based excess return vs. Arithmetic mean excess | `UNRESOLVED / REQUIRES VALIDATION` | Econometric alignment of return and beta observation horizons |
| **Beta Regression Spec** | Raw returns co-movement with intercept | Excess-over-risk-free returns; zero-intercept regression | `UNRESOLVED / REQUIRES VALIDATION` | Econometric audit of CAPM specification consistency |
| **Downside Beta Threshold** | Benchmark return negative ($R_{b,t} < 0$) | Benchmark below risk-free ($R_{b,t} < R_{f,t}$); Bawa-Lindenberg $\min(R_b - \mu_b, 0)$ | `CANDIDATE / REQUIRES VALIDATION` | Sample size adequacy and regime conditioning audit |
| **Capture Compounding** | Geometric product over non-contiguous subset | Arithmetic mean ratio over subset; monthly return ratio | `CANDIDATE / REQUIRES VALIDATION` | Non-contiguous compounding path-dependency study; monthly factsheet parity |
| **Downside Capture Sign** | Positive loss ratio (lower = better) | Signed negative ratio; explicit diagnostic for positive return | `CANDIDATE / REQUIRES VALIDATION` | Fiduciary clarity review regarding negative capture values |
| **Tracking Error Annualizer**| Sample standard deviation $\times \sqrt{252}$| Population standard deviation; Root Mean Square active return | `CANDIDATE / REQUIRES VALIDATION` | Sample bias audit ($N-1$ vs $N$); annualization factor stability |
| **Jensen's Alpha Form** | $R_p - [R_f + \beta(R_m - R_f)]$ | Time-series OLS regression intercept $\alpha_{daily} \times 252$ | `CANDIDATE / REQUIRES VALIDATION` | Econometric confidence intervals vs. factsheet parity |
| **VaR Quantile Method** | Historical empirical 5th percentile | Nearest rank (step quantile); NIST Type 7 default | `CANDIDATE / REQUIRES VALIDATION` | Tail sampling stability across 700+ trading days |
| **Expected Shortfall Tail** | Average loss of observations at or beyond 95% VaR | Strict inequality ($< \text{VaR}$); Acerbi-Tasche coherent weighting | `CANDIDATE / REQUIRES VALIDATION` | Coherence audit and multi-observation tie analysis |
| **Drawdown Duration Recovery**| Elapsed period from prior peak until recovery | Peak-to-trough decline days; ongoing underwater status | `CANDIDATE / REQUIRES VALIDATION` | Edge case validation for unrecovered market crashes |
| **Turnover Definition** | Sum of absolute changes in portfolio weights | SEBI disclosed turnover ratio; drift-adjusted turnover | `CANDIDATE / REQUIRES VALIDATION` | Distinction between passive price drift and active manager trades |
| **Top-10 Concentration Denom**| Portfolio holdings universe defined by source disclosure | Total Scheme Net Assets (NAV) vs. Total Equity Market Value | `CANDIDATE / REQUIRES VALIDATION` | Portfolio disclosure cash-exclusion audit |
| **ENH Portfolio Universe** | Explicitly defined holdings universe ($\sum w_i = 1$) | Normalized equity holdings only vs. Treating cash as single asset | `CANDIDATE / REQUIRES VALIDATION` | Audit of diversification sensitivity to large cash buffers |
| **Cash Allocation vs Drag** | Gross Cash & Equivalent % of AUM | Opportunity cost drag: $w_{cash} \times (R_b - R_{cash})$ | `CANDIDATE / REQUIRES VALIDATION` | Allocation percentage defined; opportunity cost drag is a separate future metric |
| **TER Regulatory Check** | Declared point-in-time Direct Plan TER from source | Dynamic statutory SEBI slab schedule by category/AUM | `CANDIDATE / REQUIRES VALIDATION` | Sourced regulatory verification; NO hardcoded max invalidation rule |

---

## 9. Return Methodology Specifications (Metrics 1 to 6 of Analytical Inventory)

---

### METRIC: RET-01 (1Y CAGR) — Candidate Analytical Metric (1 of 30)
1. **Metric Code:** `RET-01`
2. **Metric Name:** 1-Year Compound Annual Growth Rate
3. **Analytical Dimension:** Return Quality
4. **Purpose:** Measures the annualized geometric rate of return over a trailing 12-calendar-month period.
5. **Candidate Formula:**
   $$\text{CAGR}_{1Y} = \left(\frac{\text{Ending NAV}}{\text{Starting NAV}}\right)^{\frac{365.25}{\text{elapsed\_calendar\_days}}} - 1$$
6. **Annualization Treatment:** **"365.25-day annualization is a candidate convention requiring validation; no annualization convention is approved for production."**
7. **Required Inputs:** Daily NAV series for scheme option.
8. **Required Data Frequency:** Daily NAV observations.
9. **Calculation Frequency:** As-of analysis request date.
10. **Lookback/Window:** 12 calendar months preceding cutoff date.
11. **Minimum Observations:** 240 valid trading days. `CANDIDATE ENGINEERING PARAMETER — REQUIRES VALIDATION`. Proposed to ensure that extensive mid-year data gaps (e.g. reporting halts) prevent misleading annual return calculations.
12. **Observation-Date Semantics:** Boundary resolved: Start = target date $- 1\text{Y}$; End = cutoff date. Preceding lookback for non-trading boundaries.
13. **PIT / Knowledge-Cutoff:** Strictly $T \le \text{cutoff}$.
14. **Benchmark Dependency:** `FALSE`
15. **Risk-Free Dependency:** `FALSE`
16. **Annualization Convention:** Candidate convention; requires validation.
17. **Denominator Convention:** Starting boundary NAV ($NAV_{start} > 0$).
18. **Missing-Data Rule:** Look back up to 4 calendar days preceding target date. If no observation found, halt with insufficient evidence.
19. **Insufficient-History Rule:** Scheme age $< 12$ calendar months returns insufficient history. Metric value is `NULL`.
20. **Invalid-Data Rule:** $NAV \le 0$ rejected as `INVALID`.
21. **Data-Quality Prerequisites:** Observations must have `Quality = VALID` and `Integrity = CLEAN`.
22. **Edge Cases:** Leap years handled by actual elapsed calendar days.
23. **Units:** Decimal percentage ($0.152 = 15.2\%$).
24. **Known Limitations:** 1-year trailing return is heavily subject to single-regime recency bias.
25. **Candidate Alternatives:** Simple discrete 1Y return without fractional day compounding: $(NAV_t / NAV_0) - 1$; fixed $365.0$ daycount.
26. **Unresolved Methodological Questions:** Daycount divisor $365.0$ vs $365.25$ vs trading days.
27. **Validation Required:** Reconciliation against official AMC factsheets on a test cohort of 20 schemes.
28. **Epistemic Status:** `CANDIDATE / REQUIRES VALIDATION`

---

### METRIC: RET-03 (3Y CAGR) — Candidate Analytical Metric (2 of 30)
1. **Metric Code:** `RET-03`
2. **Metric Name:** 3-Year Compound Annual Growth Rate
3. **Analytical Dimension:** Return Quality
4. **Purpose:** Measures the annualized geometric compound growth rate over a 36-calendar-month period.
5. **Candidate Formula:**
   $$\text{CAGR}_{3Y} = \left(\frac{\text{Ending NAV}}{\text{Starting NAV}}\right)^{\frac{365.25}{\text{elapsed\_calendar\_days}}} - 1$$
6. **Annualization Treatment:** **"365.25-day annualization is a candidate convention requiring validation; no annualization convention is approved for production."**
7. **Required Inputs:** Daily NAV series for scheme option.
8. **Required Data Frequency:** Daily NAV.
9. **Calculation Frequency:** As-of analysis request date.
10. **Lookback/Window:** 36 calendar months.
11. **Minimum Observations:** 700 valid trading days. `CANDIDATE ENGINEERING PARAMETER — REQUIRES VALIDATION`. Proposed to ensure continuity across 3 years ($\sim 245$ trading days/year) and detect sustained data dropouts.
12. **Observation-Date Semantics:** Boundary resolved: Start = cutoff $- 3\text{Y}$; End = cutoff.
13. **PIT / Knowledge-Cutoff:** Strictly $T \le \text{cutoff}$.
14. **Benchmark Dependency:** `FALSE`
15. **Risk-Free Dependency:** `FALSE`
16. **Annualization Convention:** Candidate convention; requires validation.
17. **Denominator Convention:** Starting boundary NAV ($NAV_{start} > 0$).
18. **Missing-Data Rule:** 4-calendar-day preceding lookback for boundaries.
19. **Insufficient-History Rule:** Scheme age $< 36$ calendar months returns insufficient history. Metric value is `NULL`.
20. **Invalid-Data Rule:** $NAV \le 0$ halts execution.
21. **Data-Quality Prerequisites:** Clean daily series without unresolved data conflicts.
22. **Edge Cases:** NAV splits or reclassifications.
23. **Units:** Decimal percentage ($0.125 = 12.5\%$).
24. **Known Limitations:** End-point sensitivity: sharp market moves exactly at start or end date distort multi-year performance.
25. **Candidate Alternatives:** 3Y Rolling Return Mean (see `RET-05`).
26. **Unresolved Methodological Questions:** Daycount convention ($365.0$ vs $365.25$ vs trading days).
27. **Validation Required:** Empirical reconciliation against official AMC disclosures.
28. **Epistemic Status:** `CANDIDATE / REQUIRES VALIDATION`

---

### METRIC: RET-04 (5Y CAGR) — Candidate Analytical Metric (3 of 30)
1. **Metric Code:** `RET-04`
2. **Metric Name:** 5-Year Compound Annual Growth Rate
3. **Analytical Dimension:** Return Quality
4. **Purpose:** Measures full-cycle compounded annualized return over a 60-calendar-month horizon.
5. **Candidate Formula:**
   $$\text{CAGR}_{5Y} = \left(\frac{\text{Ending NAV}}{\text{Starting NAV}}\right)^{\frac{365.25}{\text{elapsed\_calendar\_days}}} - 1$$
6. **Annualization Treatment:** **"365.25-day annualization is a candidate convention requiring validation; no annualization convention is approved for production."**
7. **Required Inputs:** Daily NAV series.
8. **Required Data Frequency:** Daily NAV.
9. **Calculation Frequency:** As-of analysis request date.
10. **Lookback/Window:** 60 calendar months.
11. **Minimum Observations:** 1,200 valid trading days. `CANDIDATE ENGINEERING PARAMETER — REQUIRES VALIDATION`. Proposed to verify full 5-year continuity.
12. **Observation-Date Semantics:** Boundary-resolved with preceding lookback.
13. **PIT / Knowledge-Cutoff:** Strictly $T \le \text{cutoff}$.
14. **Benchmark Dependency:** `FALSE`
15. **Risk-Free Dependency:** `FALSE`
16. **Annualization Convention:** Candidate convention; requires validation.
17. **Denominator Convention:** Starting boundary NAV.
18. **Missing-Data Rule:** 4-calendar-day lookback.
19. **Insufficient-History Rule:** Scheme age $< 60$ months returns insufficient history.
20. **Invalid-Data Rule:** Non-positive values halt calculation.
21. **Data-Quality Prerequisites:** Verified daily series.
22. **Edge Cases:** Scheme categorized under SEBI 2018 circular; pre-2018 history may represent a different mandate.
23. **Units:** Decimal percentage.
24. **Known Limitations:** Survivorship bias inherent in surviving 5-year funds.
25. **Candidate Alternatives:** 5Y Rolling Return Mean.
26. **Unresolved Methodological Questions:** Treatment of pre-categorization historical records.
27. **Validation Required:** Cross-scheme reconciliation.
28. **Epistemic Status:** `CANDIDATE / REQUIRES VALIDATION`

---

### METRIC: RET-05 (3Y Rolling Return Mean) — Candidate Analytical Metric (4 of 30)
1. **Metric Code:** `RET-05`
2. **Metric Name:** 3-Year Rolling Return Mean
3. **Analytical Dimension:** Return Quality
4. **Purpose:** Mitigates point-to-point end-point bias by averaging 3-year annualized returns rolled across an observation window.
5. **Candidate Definition:** Calculate rolling 3-year returns across the available historical series, then calculate the arithmetic mean of the resulting rolling-return observations.
   $$\bar{R}_{roll, 3Y} = \frac{1}{M}\sum_{k=1}^M \text{CAGR}_{3Y, k}$$
6. **Required Inputs:** 5-year daily NAV series.
7. **Required Data Frequency:** Daily NAV.
8. **Calculation Frequency:** As-of analysis request date.
9. **Lookback/Window:** 60 months total history (evaluating 3Y windows).
10. **Minimum Observations:** $\ge 450$ distinct rolling 3-year windows. `CANDIDATE ENGINEERING PARAMETER — REQUIRES VALIDATION`. Proposed to ensure sufficient rolling density across varying market phases.
11. **Observation-Date Semantics:** Daily rolling step (candidate methodology item).
12. **PIT / Knowledge-Cutoff:** Strictly $T \le \text{cutoff}$.
13. **Benchmark Dependency:** `FALSE`
14. **Risk-Free Dependency:** `FALSE`
15. **Annualization Convention:** Constituent window annualization uses candidate research convention $(365.25 / D)$; mean across windows is arithmetic. Candidate convention; requires validation.
16. **Denominator Convention:** Starting NAV of each rolling window.
17. **Missing-Data Rule:** Gaps bridged; windows with missing start/end dates beyond 4 days are omitted.
18. **Insufficient-History Rule:** Scheme age $< 60$ months returns insufficient history.
19. **Invalid-Data Rule:** Corrupt NAVs halt execution.
20. **Data-Quality Prerequisites:** Clean continuous time series.
21. **Edge Cases:** Heavy serial autocorrelation across adjacent rolling windows.
22. **Units:** Decimal percentage ($0.141 = 14.1\%$).
23. **Known Limitations:** Adjacent daily rolling returns share $\sim 99.8\%$ identical data; statistical significance tests require Newey-West adjustment.
24. **Candidate Alternatives:** Monthly rolling step (36 to 60 non-overlapping or monthly-stepped windows).
25. **Unresolved Methodological Questions:** Daily rolling step vs monthly rolling step.
26. **Validation Required:** Autocorrelation and sample bias audit.
27. **Epistemic Status:** `CANDIDATE / REQUIRES VALIDATION`

---

### METRIC: RET-06 (Rolling Outperformance %) — Candidate Analytical Metric (5 of 30)
1. **Metric Code:** `RET-06`
2. **Metric Name:** Rolling Outperformance Percentage
3. **Analytical Dimension:** Return Quality
4. **Purpose:** Evaluates consistency of active management by calculating the proportion of rolling 3-year windows where the scheme outperformed its primary benchmark.
5. **Candidate Formulation:** For each eligible rolling 3Y window:
   $$\text{Fund rolling return} > \text{Benchmark rolling return}$$
   Count qualifying windows.
   $$\text{Rolling Outperformance \%} = \frac{\text{qualifying windows}}{\text{eligible windows}} \times 100$$
   **No ties count as outperformance.**
6. **Required Inputs:** Synchronous daily NAV series and Benchmark TRI series over 5 years.
7. **Required Data Frequency:** Daily.
8. **Calculation Frequency:** As-of analysis request date.
9. **Lookback/Window:** 60 months total history (producing $\sim 500$ rolling 3Y windows).
10. **Minimum Observations:** $\ge 450$ paired rolling windows. `CANDIDATE ENGINEERING PARAMETER — REQUIRES VALIDATION`. Proposed to prevent misleading percentages derived from sparse data.
11. **Observation-Date Semantics:** Synchronous paired calendar dates.
12. **PIT / Knowledge-Cutoff:** Strictly $T \le \text{cutoff}$.
13. **Benchmark Dependency:** `TRUE` (Primary benchmark Total Return Index).
14. **Risk-Free Dependency:** `FALSE`
15. **Annualization Convention:** Window CAGRs annualized prior to comparison. Candidate convention; requires validation.
16. **Denominator Convention:** Total evaluated eligible paired windows.
17. **Missing-Data Rule:** If either fund or benchmark observation is missing on a window boundary, that window is omitted from pairing.
18. **Insufficient-History Rule:** $< 60$ months history returns insufficient history.
19. **Invalid-Data Rule:** Non-positive values halt execution.
20. **Data-Quality Prerequisites:** Both fund and benchmark must be verified clean series.
21. **Edge Cases:** Benchmark changed during window; requires tracking spliced benchmark history.
22. **Units:** Percentage ($78.5 = 78.5\%$).
23. **Known Limitations:** Binary indicator ignores magnitude of outperformance or underperformance.
24. **Candidate Alternatives:** Hurdle-adjusted outperformance: $CAGR_{fund} > CAGR_{bm} + \text{Hurdle}$.
25. **Unresolved Methodological Questions:** Strict inequality ($> 0$) vs hurdle rate ($> \text{TER}$).
26. **Validation Required:** Empirical test on historical Indian active fund universe.
27. **Epistemic Status:** `CANDIDATE / REQUIRES VALIDATION`

---

### METRIC: RET-07 (3Y Active Return) — Candidate Analytical Metric (6 of 30)
1. **Metric Code:** `RET-07`
2. **Metric Name:** 3-Year Annualized Active Return
3. **Analytical Dimension:** Return Quality
4. **Purpose:** Measures the annualized excess return generated by the fund over its primary benchmark over 36 months.
5. **Candidate Formulation:**
   $$\text{3Y Active Return} = \text{Fund 3Y return} - \text{Benchmark 3Y return}$$
   **Exact return convention must be identical on both sides.**
6. **Required Inputs:** 3-year daily NAV series and Benchmark TRI series.
7. **Required Data Frequency:** Daily.
8. **Calculation Frequency:** As-of analysis request date.
9. **Lookback/Window:** 36 calendar months.
10. **Minimum Observations:** 700 paired trading days. `CANDIDATE ENGINEERING PARAMETER — REQUIRES VALIDATION`.
11. **Observation-Date Semantics:** Boundary-resolved paired dates.
12. **PIT / Knowledge-Cutoff:** Strictly $T \le \text{cutoff}$.
13. **Benchmark Dependency:** `TRUE` (Primary benchmark TRI).
14. **Risk-Free Dependency:** `FALSE`
15. **Annualization Convention:** Constituent returns annualized geometrically prior to subtraction. Candidate convention; requires validation.
16. **Denominator Convention:** Arithmetic difference (no denominator).
17. **Missing-Data Rule:** Preceding boundary lookback for both series.
18. **Insufficient-History Rule:** $< 36$ months history returns insufficient history.
19. **Invalid-Data Rule:** Missing benchmark halts calculation.
20. **Data-Quality Prerequisites:** Benchmark must be Total Return Index (TRI), not Price Return Index (PRI).
21. **Edge Cases:** Benchmark data revisions.
22. **Units:** Decimal percentage ($0.025 = +2.5\%$).
23. **Known Limitations:** Does not control for systematic market risk (beta).
24. **Candidate Alternatives:** Geometric relative return: $\frac{1 + R_{fund}}{1 + R_{bm}} - 1$.
25. **Unresolved Methodological Questions:** Arithmetic difference vs geometric relative return.
26. **Validation Required:** Factsheet reconciliation across index and active funds.
27. **Epistemic Status:** `CANDIDATE / REQUIRES VALIDATION`

---

## 10. Risk & Tail Methodology Specifications (Metrics 7 to 13 of Analytical Inventory)

---

### METRIC: RSK-01 (Annualized Volatility) — Candidate Analytical Metric (7 of 30)
1. **Metric Code:** `RSK-01`
2. **Metric Name:** Annualized Return Volatility (3Y)
3. **Analytical Dimension:** Risk & Tail
4. **Purpose:** Measures total return dispersion of daily returns around their sample mean over 36 months.
5. **Candidate Formulation:**
   1. Calculate periodic returns: $R_t = (NAV_t / NAV_{t-1}) - 1$.
   2. Calculate sample standard deviation using $N-1$ denominator:
      $$s = \text{sample\_std}(\text{returns}, \text{ddof}=1) = \sqrt{\frac{1}{N-1}\sum_{t=1}^N (R_t - \bar{R})^2}$$
   3. Annualize by multiplying by $\sqrt{252}$:
      $$\sigma_{\text{annual}} = s \times \sqrt{252}$$
6. **Statistical Estimator Governance Rule:**
   > **"N-1 is the conventional sample standard-deviation estimator when estimating population variance from a sample; the production convention remains subject to validation."**
   252 is also a candidate convention, not approved production methodology.
7. **Required Inputs:** Daily NAV series over 36 months.
8. **Required Data Frequency:** Daily NAV.
9. **Calculation Frequency:** As-of analysis request date.
10. **Lookback/Window:** 36 calendar months.
11. **Minimum Observations:** 700 trading days. `CANDIDATE ENGINEERING PARAMETER — REQUIRES VALIDATION`. Proposed to ensure sample variance stability over 3 years.
12. **Observation-Date Semantics:** Daily simple returns.
13. **PIT / Knowledge-Cutoff:** Strictly $T \le \text{cutoff}$.
14. **Benchmark Dependency:** `FALSE`
15. **Risk-Free Dependency:** `FALSE`
16. **Annualization Convention:** Multiplied by $\sqrt{252}$. Candidate convention; requires validation. Unresolved alternative: actual Indian exchange trading days ($\sqrt{248}$) or calendar days ($\sqrt{365}$).
17. **Denominator Convention:** Sample divisor $N - 1$. Candidate convention; requires validation.
18. **Missing-Data Rule:** Multi-day returns across non-trading weekends/holidays are included as single discrete observations.
19. **Insufficient-History Rule:** $< 700$ valid trading days returns insufficient data. Metric value is `NULL`.
20. **Invalid-Data Rule:** Non-finite returns or $NAV \le 0$ halt calculation.
21. **Data-Quality Prerequisites:** Clean series without unverified data revisions.
22. **Edge Cases:** Extended unscheduled exchange closures.
23. **Units:** Decimal percentage ($0.165 = 16.5\%$).
24. **Known Limitations:** Treats upside variance identically to downside variance; assumes identical independent distribution.
25. **Candidate Alternatives:** Exchange-actual trading days annualizer; log returns standard deviation.
26. **Unresolved Methodological Questions:** Fixed 252 vs exchange-actual trading days.
27. **Validation Required:** Empirical sensitivity analysis of $\sqrt{252}$ vs $\sqrt{248}$ across 10 years of Indian equity data.
28. **Epistemic Status:** `CANDIDATE / REQUIRES VALIDATION`

---

### METRIC: RSK-02 (Downside Semideviation) — Candidate Analytical Metric (8 of 30)
1. **Metric Code:** `RSK-02`
2. **Metric Name:** Downside Semideviation (3Y)
3. **Analytical Dimension:** Risk & Tail
4. **Purpose:** Isolates downside risk by measuring the dispersion of returns below a target threshold ($MAR$).
5. **Candidate Formulation:**
   $$\sigma_{down} = \sqrt{\frac{1}{D}\sum_{t=1}^N \min(R_t - MAR, 0)^2} \times A$$
   where:
   - $D$ = unresolved divisor convention (candidate alternatives: $N$, $N-1$, or downside observation count $K = \sum \mathbf{1}_{\{R_t < MAR\}}$)
   - $A$ = candidate annualization factor (current candidate: $\sqrt{252}$)
   - Neither $D$ nor $A$ is approved production methodology.
6. **Divisor & Governance Rules:**
   - **Divisor Convention:** **"Downside semideviation denominator convention (N versus N-1) is unresolved and requires methodological validation."** Do not silently select $N$, $N-1$, or $K$. Do not claim any divisor is mathematically mandatory.
   - **Annualization Convention:** $\sqrt{252}$ is a candidate convention requiring validation; do not silently approve $\sqrt{252}$ or any other annualization factor for production.
   - **MAR Convention:** Target hurdle convention (e.g., $MAR = 0$ vs $MAR = R_f$) remains unresolved and requires validation.
   - **Codebase Conflict:** The conflict between `semideviation.py` ($N-1$ divisor) and `statistics.py` ($N$ divisor via `downside_deviation`) remains active and explicitly documented for future reconciliation.
7. **Required Inputs:** Daily NAV series, target return $MAR$ (candidate default $MAR = 0$).
8. **Required Data Frequency:** Daily.
9. **Calculation Frequency:** As-of analysis request date.
10. **Lookback/Window:** 36 calendar months.
11. **Minimum Observations:** 700 trading days. `CANDIDATE ENGINEERING PARAMETER — REQUIRES VALIDATION`.
12. **Observation-Date Semantics:** Daily simple returns.
13. **PIT / Knowledge-Cutoff:** Strictly $T \le \text{cutoff}$.
14. **Benchmark Dependency:** `FALSE`
15. **Risk-Free Dependency:** Conditional on $MAR$ ($MAR = R_f$ requires risk-free series).
16. **Annualization Convention:** Multiplied by $\sqrt{252}$. Candidate convention; requires validation.
17. **Denominator Convention:** `UNRESOLVED / REQUIRES VALIDATION`. Codebase conflict present between sample divisor $N-1$ (`semideviation.py`) and population divisor $N$ (`statistics.py`). Competing candidate alternatives: $N$, $N-1$, and downside observation count $K = \sum \mathbf{1}_{\{R_t < MAR\}}$.
18. **Missing-Data Rule:** Holiday bridging.
19. **Insufficient-History Rule:** $< 700$ trading days returns insufficient data.
20. **Invalid-Data Rule:** Non-finite returns halt execution.
21. **Data-Quality Prerequisites:** Clean NAV series.
22. **Edge Cases:** Fund has zero down days ($\sigma_{down} = 0$).
23. **Units:** Decimal percentage ($0.112 = 11.2\%$).
24. **Known Limitations:** Sensitive to choice of $MAR$.
25. **Candidate Alternatives:** $MAR = R_{f, daily}$ vs $MAR = 0$.
26. **Unresolved Methodological Questions:** Resolution of $N$ vs $N-1$ vs downside-count divisor conflict.
27. **Validation Required:** Formal reconciliation and code-level consolidation.
28. **Epistemic Status:** `CANDIDATE / REQUIRES VALIDATION` (Conflict Present in Codebase).

---

### METRIC: RSK-03 (Maximum Drawdown 3Y) — Candidate Analytical Metric (9 of 30)
1. **Metric Code:** `RSK-03`
2. **Metric Name:** 3-Year Maximum Drawdown
3. **Analytical Dimension:** Risk & Tail
4. **Purpose:** Measures the worst peak-to-trough percentage capital loss over a 36-month horizon.
5. **Exact Candidate Formulation:** For a NAV/value series:
   $$\text{Running Peak}_t = \max(\text{Value}_1 \dots \text{Value}_t)$$
   $$\text{Drawdown}_t = \frac{\text{Value}_t}{\text{Running Peak}_t} - 1$$
   $$\text{Maximum Drawdown} = \min(\text{Drawdown}_t)$$
6. **Lookback/Window:** 3Y window is the candidate analytical window.
7. **Required Inputs:** Daily NAV series over 36 months.
8. **Required Data Frequency:** Daily NAV.
9. **Calculation Frequency:** As-of analysis request date.
10. **Minimum Observations:** 700 trading days. `CANDIDATE ENGINEERING PARAMETER — REQUIRES VALIDATION`. Proposed to ensure complete daily path evaluation.
11. **Observation-Date Semantics:** Sequential daily valuation series.
12. **PIT / Knowledge-Cutoff:** Strictly $T \le \text{cutoff}$.
13. **Benchmark Dependency:** `FALSE`
14. **Risk-Free Dependency:** `FALSE`
15. **Annualization Convention:** None (unannualized discrete path measure).
16. **Denominator Convention:** Running peak NAV $\text{Running Peak}_t > 0$.
17. **Missing-Data Rule:** Gaps between trading days do not reset peak.
18. **Insufficient-History Rule:** $< 36$ months history returns insufficient history.
19. **Invalid-Data Rule:** Non-positive NAV halts execution.
20. **Data-Quality Prerequisites:** Continuous chronologically ordered NAVs.
21. **Edge Cases:** Peak occurs on first day; trough occurs on last day.
22. **Units:** Decimal percentage (signed negative candidate convention: $-0.245 = -24.5\%$).
23. **Known Limitations:** End-point independent single-event metric; ignores frequency of secondary drawdowns.
24. **Candidate Alternatives:** Positive loss magnitude ($+24.5\%$).
25. **Unresolved Methodological Questions:** Signed negative vs positive loss representation.
26. **Validation Required:** Unit-test verification against quant-engine `risk.py`.
27. **Epistemic Status:** `CANDIDATE / REQUIRES VALIDATION`

---

### METRIC: RSK-04 (Maximum Drawdown Duration) — Candidate Analytical Metric (10 of 30)
1. **Metric Code:** `RSK-04`
2. **Metric Name:** Maximum Drawdown Duration
3. **Analytical Dimension:** Risk & Tail
4. **Purpose:** Measures the length of time the series remains below its previous high-water mark.
5. **Exact Candidate Definition:**
   Maximum elapsed period from a prior peak until recovery to that peak, measured using the observation calendar/time convention defined for the input series. Do not invent a production calendar convention.
   $$\text{Duration} = \text{ElapsedPeriod}(\text{Peak} \to \text{Recovery})$$
6. **Required Inputs:** Daily NAV series.
7. **Required Data Frequency:** Daily NAV.
8. **Calculation Frequency:** As-of analysis request date.
9. **Lookback/Window:** 36 calendar months.
10. **Minimum Observations:** 700 trading days. `CANDIDATE ENGINEERING PARAMETER — REQUIRES VALIDATION`.
11. **Observation-Date Semantics:** Observation dates of identified peak and recovery points.
12. **PIT / Knowledge-Cutoff:** Strictly $T \le \text{cutoff}$.
13. **Benchmark Dependency:** `FALSE`
14. **Risk-Free Dependency:** `FALSE`
15. **Annualization Convention:** None.
16. **Denominator Convention:** None.
17. **Missing-Data Rule:** Handled per series calendar/time convention.
18. **Insufficient-History Rule:** $< 36$ months history returns insufficient history.
19. **Invalid-Data Rule:** Non-positive values halt execution.
20. **Data-Quality Prerequisites:** Validated daily series.
21. **Edge Cases:** Unresolved drawdown at cutoff date: Status is flagged as ongoing underwater; duration measured from peak to knowledge cutoff date.
22. **Units:** Calendar days or trading periods per convention.
23. **Known Limitations:** For unresolved drawdowns, true total recovery duration is right-censored.
24. **Candidate Alternatives:** Peak-to-trough decline days vs peak-to-recovery days.
25. **Unresolved Methodological Questions:** Treatment of right-censored ongoing drawdowns.
26. **Validation Required:** Historical crash scenario testing.
27. **Epistemic Status:** `CANDIDATE / REQUIRES VALIDATION` (Needs Assembly).

---

### METRIC: RSK-05 (Ulcer Index) — Candidate Analytical Metric (11 of 30)
1. **Metric Code:** `RSK-05`
2. **Metric Name:** Ulcer Index (3Y)
3. **Analytical Dimension:** Risk & Tail
4. **Purpose:** Measures the depth and duration of drawdowns quadratically, penalizing prolonged underwater periods.
5. **Exact Candidate Formulation:** For each observation:
   $$\text{Percentage Drawdown}_t = 100 \times \left(\frac{\text{Value}_t}{\text{Running Peak}_t} - 1\right)$$
   $$\text{Ulcer Index} = \sqrt{\text{mean}(\text{Drawdown}_t^2)}$$
   Use the defined candidate analytical window.
6. **Required Inputs:** Daily NAV series over 36 months.
7. **Required Data Frequency:** Daily NAV.
8. **Calculation Frequency:** As-of analysis request date.
9. **Lookback/Window:** 36 calendar months candidate analytical window.
10. **Minimum Observations:** 700 trading days. `CANDIDATE ENGINEERING PARAMETER — REQUIRES VALIDATION`.
11. **Observation-Date Semantics:** Chronological daily NAVs.
12. **PIT / Knowledge-Cutoff:** Strictly $T \le \text{cutoff}$.
13. **Benchmark Dependency:** `FALSE`
14. **Risk-Free Dependency:** `FALSE`
15. **Annualization Convention:** None.
16. **Denominator Convention:** Total observation count $N$.
17. **Missing-Data Rule:** Peak carried across holidays.
18. **Insufficient-History Rule:** $< 36$ months history returns insufficient history.
19. **Invalid-Data Rule:** Non-positive values halt execution.
20. **Data-Quality Prerequisites:** Unbroken time series.
21. **Edge Cases:** Fund at all-time highs every day ($\text{UI} = 0.0$).
22. **Units:** Index points ($4.8 = 4.8\text{ UI points}$).
23. **Known Limitations:** Index value is an abstract score of underwater friction, not an intuitive percentage loss.
24. **Candidate Alternatives:** Decimal fraction representation ($0.048$ instead of $4.8$).
25. **Unresolved Methodological Questions:** Units standardization (percentage points vs decimal fraction).
26. **Validation Required:** Benchmark against quant-engine `ulcer_index.py`.
27. **Epistemic Status:** `CANDIDATE / REQUIRES VALIDATION`

---

### METRIC: RSK-06 (Historical VaR 95%) — Candidate Analytical Metric (12 of 30)
1. **Metric Code:** `RSK-06`
2. **Metric Name:** Historical Value at Risk (95% Confidence, 1-Day Horizon)
3. **Analytical Dimension:** Risk & Tail
4. **Purpose:** Identifies the minimum daily loss magnitude expected on the worst 5% of trading days based on the empirical return distribution.
5. **Exact Candidate Formulation (Historical VaR Only):**
   1. Generate historical portfolio/fund return observations.
   2. Sort observations ascending.
   3. Identify the 5th percentile loss threshold.
   4. Report the 95% historical VaR.
   $$\text{VaR}_{0.95} = -Q_{0.05}(R_1, R_2, \dots, R_N)$$
   **Do not introduce parametric VaR. Do not introduce Monte Carlo VaR. Phase 2H candidate methodology is historical VaR only.**
6. **Required Inputs:** Daily simple return series over 36 months.
7. **Required Data Frequency:** Daily.
8. **Calculation Frequency:** As-of analysis request date.
9. **Lookback/Window:** 36 calendar months.
10. **Minimum Observations:** 700 trading days. `CANDIDATE ENGINEERING PARAMETER — REQUIRES VALIDATION`. Proposed to ensure at least $\sim 35$ observations populate the 5% tail.
11. **Observation-Date Semantics:** Daily simple returns.
12. **PIT / Knowledge-Cutoff:** Strictly $T \le \text{cutoff}$.
13. **Benchmark Dependency:** `FALSE`
14. **Risk-Free Dependency:** `FALSE`
15. **Annualization Convention:** Unannualized 1-day horizon.
16. **Denominator Convention:** Quantile rank position.
17. **Missing-Data Rule:** Multi-day weekend returns handled as single observations.
18. **Insufficient-History Rule:** $< 700$ observations returns insufficient data.
19. **Invalid-Data Rule:** Non-finite values rejected.
20. **Data-Quality Prerequisites:** Clean historical distribution.
21. **Edge Cases:** Severe single-day crash dominates empirical tail.
22. **Units:** Decimal percentage (positive loss magnitude: $0.0185 = 1.85\%$ daily loss).
23. **Known Limitations:** Non-subadditive (not a coherent risk measure; Artzner et al., 1999). Ignores loss magnitude beyond the threshold.
24. **Candidate Alternatives:** Linear interpolation vs nearest rank empirical quantile.
25. **Unresolved Methodological Questions:** Linear interpolation vs nearest rank quantile.
26. **Validation Required:** Quantile estimation stability audit.
27. **Epistemic Status:** `CANDIDATE / REQUIRES VALIDATION`

---

### METRIC: RSK-07 (Expected Shortfall 95%) — Candidate Analytical Metric (13 of 30)
1. **Metric Code:** `RSK-07`
2. **Metric Name:** Historical Expected Shortfall (95% Confidence, 1-Day Horizon)
3. **Analytical Dimension:** Risk & Tail
4. **Purpose:** Measures the average loss experienced on days when returns breach the 95% VaR threshold (Conditional VaR).
5. **Exact Candidate Formulation (Historical Expected Shortfall Only):**
   $$\text{Expected Shortfall} = \text{average loss of observations at or beyond the 95\% VaR threshold.}$$
   $$\text{ES}_{0.95} = -\frac{1}{|T_{tail}|}\sum_{t \in T_{tail}} R_t, \quad T_{tail} = \{t : R_t \le Q_{0.05}\}$$
   **Use historical empirical observations only. Do not introduce parametric or Monte Carlo ES.**
6. **Required Inputs:** Daily simple returns over 36 months.
7. **Required Data Frequency:** Daily.
8. **Calculation Frequency:** As-of analysis request date.
9. **Lookback/Window:** 36 calendar months.
10. **Minimum Observations:** 700 trading days. `CANDIDATE ENGINEERING PARAMETER — REQUIRES VALIDATION`.
11. **Observation-Date Semantics:** Daily simple returns.
12. **PIT / Knowledge-Cutoff:** Strictly $T \le \text{cutoff}$.
13. **Benchmark Dependency:** `FALSE`
14. **Risk-Free Dependency:** `FALSE`
15. **Annualization Convention:** Unannualized 1-day horizon.
16. **Denominator Convention:** Observation count in tail $|T_{tail}|$.
17. **Missing-Data Rule:** Clean distribution.
18. **Insufficient-History Rule:** $< 700$ observations returns insufficient data.
19. **Invalid-Data Rule:** Non-finite values halt execution.
20. **Data-Quality Prerequisites:** Verified distribution.
21. **Edge Cases:** Tied returns at quantile boundary.
22. **Units:** Decimal percentage (positive loss magnitude: $0.0275 = 2.75\%$).
23. **Known Limitations:** Small tail sample size ($\sim 35$ days) increases sampling variance.
24. **Candidate Alternatives:** Acerbi & Tasche (2002) coherent fractional tail-weighting.
25. **Unresolved Methodological Questions:** Weak inequality ($\le \text{VaR}$) vs strict inequality ($< \text{VaR}$) vs coherent weighting.
26. **Validation Required:** Benchmark against quant-engine `expected_shortfall.py`.
27. **Epistemic Status:** `CANDIDATE / REQUIRES VALIDATION`

---

## 11. Risk-Adjusted Methodology Specifications (Metrics 14 to 17 of Analytical Inventory)

---

### METRIC: RAT-01 (Sharpe Ratio 3Y) — Candidate Analytical Metric (14 of 30)
1. **Metric Code:** `RAT-01`
2. **Metric Name:** Sharpe Ratio (3Y Annualized)
3. **Analytical Dimension:** Risk-Adjusted
4. **Purpose:** Measures the average excess return per unit of total risk (volatility) over a 36-month horizon.
5. **Candidate Formulation:**
   $$\text{Sharpe} = \frac{\text{Annualized Return} - \text{Annualized Risk-Free Rate}}{\text{Annualized Volatility}}$$
   **Use the same annualization convention for numerator and denominator.**
   Risk-free source and exact annualization convention are NOT approved.
   Do not ingest or implement a production risk-free series in Phase 2H.
6. **Required Inputs:** NAV series, risk-free rate series.
7. **Required Data Frequency:** Daily.
8. **Calculation Frequency:** As-of analysis request date.
9. **Lookback/Window:** 36 calendar months.
10. **Minimum Observations:** 700 paired trading days. `CANDIDATE ENGINEERING PARAMETER — REQUIRES VALIDATION`.
11. **Observation-Date Semantics:** Synchronous paired dates.
12. **PIT / Knowledge-Cutoff:** Strictly $T \le \text{cutoff}$.
13. **Benchmark Dependency:** `FALSE`
14. **Risk-Free Dependency:** `TRUE` (Requires governed risk-free source; selection remains unresolved).
15. **Annualization Convention:** Candidate convention; requires validation. Must be identical for numerator and denominator.
16. **Denominator Convention:** Annualized sample volatility ($N - 1$). Candidate convention; requires validation.
17. **Missing-Data Rule:** Paired deletion of unaligned dates.
18. **Insufficient-History Rule:** $< 700$ paired days returns insufficient data.
19. **Invalid-Data Rule:** Zero volatility halts with division by zero.
20. **Data-Quality Prerequisites:** Verified risk-free series without negative yield anomalies.
21. **Edge Cases:** Negative excess return: higher risk makes a negative Sharpe ratio less negative, creating an evaluation paradox.
22. **Units:** Dimensionless ratio ($1.15\text{x}$).
23. **Known Limitations:** Penalizes upside volatility; assumes normal distribution.
24. **Candidate Alternatives:** Daily excess return mean vs CAGR difference.
25. **Unresolved Methodological Questions:** Risk-free asset selection (MIBOR vs 91D T-Bill); annualization convention.
26. **Validation Required:** Benchmark against quant-engine `ratios.py`.
27. **Epistemic Status:** `CANDIDATE / REQUIRES VALIDATION`

---

### METRIC: RAT-02 (Sortino Ratio 3Y) — Candidate Analytical Metric (15 of 30)
1. **Metric Code:** `RAT-02`
2. **Metric Name:** Sortino Ratio (3Y Annualized)
3. **Analytical Dimension:** Risk-Adjusted
4. **Purpose:** Measures excess return per unit of downside risk, ignoring upside volatility.
5. **Candidate Formulation:**
   $$\text{Sortino} = \frac{\text{Annualized Return} - \text{Annualized Risk-Free Rate}}{\text{Annualized Downside Deviation}}$$
   **The same return/risk-free/annualization conventions must be used consistently.**
   Downside-deviation denominator convention remains unresolved.
6. **Required Inputs:** Daily NAV series, risk-free series or target return $MAR$.
7. **Required Data Frequency:** Daily.
8. **Calculation Frequency:** As-of analysis request date.
9. **Lookback/Window:** 36 calendar months.
10. **Minimum Observations:** 700 trading days. `CANDIDATE ENGINEERING PARAMETER — REQUIRES VALIDATION`.
11. **Observation-Date Semantics:** Daily simple returns.
12. **PIT / Knowledge-Cutoff:** Strictly $T \le \text{cutoff}$.
13. **Benchmark Dependency:** `FALSE`
14. **Risk-Free Dependency:** Conditional on $MAR$ definition ($MAR = R_f$ requires risk-free series).
15. **Annualization Convention:** Candidate convention; requires validation. Must be internally consistent.
16. **Denominator Convention:** `UNRESOLVED / REQUIRES VALIDATION`. Bound to downside deviation divisor resolution ($N$ vs $N-1$).
17. **Missing-Data Rule:** Paired handling.
18. **Insufficient-History Rule:** $< 700$ days returns insufficient data.
19. **Invalid-Data Rule:** Zero downside deviation halts with division by zero.
20. **Data-Quality Prerequisites:** Clean daily series.
21. **Edge Cases:** Zero down days ($\sigma_{down} = 0$).
22. **Units:** Dimensionless ratio ($1.62\text{x}$).
23. **Known Limitations:** Highly sensitive to chosen $MAR$.
24. **Candidate Alternatives:** $MAR = 0$ vs $MAR = R_f$.
25. **Unresolved Methodological Questions:** Codebase divisor conflict resolution ($N$ vs $N-1$); $MAR$ selection.
26. **Validation Required:** Reconciliation between `ratios.py`, `statistics.py`, and `semideviation.py`.
27. **Epistemic Status:** `CANDIDATE / REQUIRES VALIDATION` (Conflict Present in Codebase).

---

### METRIC: RAT-03 (Treynor Ratio 3Y) — Candidate Analytical Metric (16 of 30)
1. **Metric Code:** `RAT-03`
2. **Metric Name:** Treynor Ratio (3Y Annualized)
3. **Analytical Dimension:** Risk-Adjusted
4. **Purpose:** Measures excess return generated per unit of systematic market risk ($\beta$).
5. **Candidate Mathematical Form:**
   $$\text{Treynor} = \frac{\text{Return} - \text{Risk-Free Rate}}{\text{Beta}}$$
6. **Numerator Governance Rule:**
   > **"Treynor numerator convention = UNRESOLVED. Do NOT silently choose CAGR, arithmetic mean return, annualized return, or excess return as an approved numerator. Document the candidate mathematical form but explicitly mark the numerator/annualization convention as requiring validation."**
7. **Required Inputs:** Fund return series, risk-free rate series, 3Y Beta (`MKT-01`).
8. **Required Data Frequency:** Daily for beta; daily/annualized for rates.
9. **Calculation Frequency:** As-of analysis request date.
10. **Lookback/Window:** 36 calendar months.
11. **Minimum Observations:** 700 trading days for beta estimation. `CANDIDATE ENGINEERING PARAMETER — REQUIRES VALIDATION`.
12. **Observation-Date Semantics:** Synchronous paired series.
13. **PIT / Knowledge-Cutoff:** Strictly $T \le \text{cutoff}$.
14. **Benchmark Dependency:** `TRUE` (Required for beta).
15. **Risk-Free Dependency:** `TRUE` (Required for excess return).
16. **Annualization Convention:** Candidate convention; requires validation.
17. **Denominator Convention:** Portfolio beta ($\beta \ne 0$).
18. **Missing-Data Rule:** Missing beta halts calculation.
19. **Insufficient-History Rule:** $< 36$ months history returns insufficient history.
20. **Invalid-Data Rule:** $\beta = 0$ halts execution. $\beta < 0$ inverts interpretation.
21. **Data-Quality Prerequisites:** Verified fund, benchmark, and risk-free series.
22. **Edge Cases:** Negative beta with positive excess return produces negative Treynor ratio.
23. **Units:** Decimal percentage or dimensionless ratio ($0.085$).
24. **Known Limitations:** Assumes portfolio is fully diversified and idiosyncratic risk is negligible.
25. **Candidate Alternatives:** Annualized daily excess return / beta vs CAGR-based excess return / beta.
26. **Unresolved Methodological Questions:** Numerator convention; negative beta interpretation; risk-free source.
27. **Validation Required:** Benchmark against quant-engine `treynor.py`.
28. **Epistemic Status:** `CANDIDATE / REQUIRES VALIDATION`

---

### METRIC: RAT-04 (Information Ratio 3Y) — Candidate Analytical Metric (17 of 30)
1. **Metric Code:** `RAT-04`
2. **Metric Name:** Information Ratio (3Y Annualized)
3. **Analytical Dimension:** Risk-Adjusted
4. **Purpose:** Measures active return generated per unit of active risk (Tracking Error).
5. **Candidate Formulation:**
   $$\text{Information Ratio} = \frac{\text{Annualized Active Return}}{\text{Annualized Tracking Error}}$$
   **The annualization convention must be internally consistent.**
6. **Required Inputs:** Daily NAV series and Benchmark TRI series over 36 months.
7. **Required Data Frequency:** Daily.
8. **Calculation Frequency:** As-of analysis request date.
9. **Lookback/Window:** 36 calendar months.
10. **Minimum Observations:** 700 paired trading days. `CANDIDATE ENGINEERING PARAMETER — REQUIRES VALIDATION`.
11. **Observation-Date Semantics:** Synchronous paired daily returns.
12. **PIT / Knowledge-Cutoff:** Strictly $T \le \text{cutoff}$.
13. **Benchmark Dependency:** `TRUE` (Primary benchmark TRI).
14. **Risk-Free Dependency:** `FALSE`
15. **Annualization Convention:** Candidate convention; requires validation. Must be internally consistent between numerator and denominator.
16. **Denominator Convention:** Sample tracking error ($N - 1$). Candidate convention; requires validation.
17. **Missing-Data Rule:** Paired deletion.
18. **Insufficient-History Rule:** $< 700$ paired days returns insufficient data.
19. **Invalid-Data Rule:** Zero tracking error halts with division by zero.
20. **Data-Quality Prerequisites:** Synchronous Total Return Index.
21. **Edge Cases:** Index clone with near-zero active return and near-zero tracking error produces numerical instability ($0 / 0$).
22. **Units:** Dimensionless ratio ($0.65\text{x}$).
23. **Known Limitations:** Can be inflated for low-active-risk closet indexers that capture minor timing alpha.
24. **Candidate Alternatives:** Annualized CAGR difference divided by annualized tracking error.
25. **Unresolved Methodological Questions:** Daily mean annualization vs CAGR difference annualization.
26. **Validation Required:** Benchmark against quant-engine `information_ratio.py`.
27. **Epistemic Status:** `CANDIDATE / REQUIRES VALIDATION`

---

## 12. Market Sensitivity Methodology Specifications (Metrics 18 to 22 of Analytical Inventory)

---

### METRIC: MKT-01 (Beta 3Y) — Candidate Analytical Metric (18 of 30)
1. **Metric Code:** `MKT-01`
2. **Metric Name:** Portfolio Beta (3Y)
3. **Analytical Dimension:** Market Sensitivity
4. **Purpose:** Measures systematic sensitivity of scheme returns to benchmark movements over 36 months.
5. **Candidate Formulation:**
   $$\text{Beta} = \frac{\text{Covariance}(\text{Fund Returns}, \text{Benchmark Returns})}{\text{Variance}(\text{Benchmark Returns})}$$
   **Use aligned periodic observations. The exact return frequency and minimum observation requirements remain validation items.**
6. **Required Inputs:** Synchronous daily NAV and Benchmark TRI series over 36 months.
7. **Required Data Frequency:** Daily.
8. **Calculation Frequency:** As-of analysis request date.
9. **Lookback/Window:** 36 calendar months.
10. **Minimum Observations:** 700 paired trading days. `CANDIDATE ENGINEERING PARAMETER — REQUIRES VALIDATION`.
11. **Observation-Date Semantics:** Synchronous paired trading days.
12. **PIT / Knowledge-Cutoff:** Strictly $T \le \text{cutoff}$.
13. **Benchmark Dependency:** `TRUE` (Primary benchmark TRI).
14. **Risk-Free Dependency:** `FALSE` (Candidate raw returns convention; `TRUE` under excess returns).
15. **Annualization Convention:** None (variance ratio; scaling cancels out).
16. **Denominator Convention:** Sample variance of benchmark returns ($N - 1$). Candidate convention; requires validation.
17. **Missing-Data Rule:** Paired deletion of unaligned dates.
18. **Insufficient-History Rule:** $< 700$ paired trading days returns insufficient data.
19. **Invalid-Data Rule:** Benchmark variance zero halts execution.
20. **Data-Quality Prerequisites:** Verified benchmark alignment.
21. **Edge Cases:** Non-synchronous trading holidays between fund and index.
22. **Units:** Dimensionless coefficient ($0.95$).
23. **Known Limitations:** Assumes linear stationary relationship across all market regimes.
24. **Candidate Alternatives:** Excess-return CAPM beta; robust Theil-Sen regression beta.
25. **Unresolved Methodological Questions:** Raw returns vs excess-over-risk-free returns; OLS intercept inclusion.
26. **Validation Required:** Benchmark against quant-engine `beta.py`.
27. **Epistemic Status:** `CANDIDATE / REQUIRES VALIDATION`

---

### METRIC: MKT-02 (Downside Beta) — Candidate Analytical Metric (19 of 30)
1. **Metric Code:** `MKT-02`
2. **Metric Name:** Downside Beta (3Y)
3. **Analytical Dimension:** Market Sensitivity
4. **Purpose:** Measures scheme sensitivity to benchmark movements specifically during benchmark downturn regimes.
5. **Candidate Formulation:**
   Calculate beta using only observations where the benchmark return is below the defined downside threshold.
   $$\beta_{down} = \frac{\text{Cov}(R_{p,t}, R_{b,t} \mid R_{b,t} < \text{Threshold})}{\text{Var}(R_{b,t} \mid R_{b,t} < \text{Threshold})}$$
6. **Downside Threshold Governance Rule:**
   The downside threshold must be explicitly documented as a candidate convention (e.g. $R_{b,t} < 0$). **Do NOT claim that a particular downside threshold is approved.**
7. **Required Inputs:** Synchronous daily NAV and Benchmark TRI series over 36 months.
8. **Required Data Frequency:** Daily.
9. **Calculation Frequency:** As-of analysis request date.
10. **Lookback/Window:** 36 calendar months.
11. **Minimum Observations:** $\ge 100$ benchmark down-days within 36 months. `CANDIDATE ENGINEERING PARAMETER — REQUIRES VALIDATION`. Proposed to prevent high standard errors from sparse samples.
12. **Observation-Date Semantics:** Subsampled paired dates where benchmark return is below threshold.
13. **PIT / Knowledge-Cutoff:** Strictly $T \le \text{cutoff}$.
14. **Benchmark Dependency:** `TRUE`
15. **Risk-Free Dependency:** `FALSE`
16. **Annualization Convention:** None.
17. **Denominator Convention:** Sample variance of benchmark on down-days ($N_{down} - 1$). Candidate convention; requires validation.
18. **Missing-Data Rule:** Paired deletion.
19. **Insufficient-History Rule:** $< 100$ down-market days returns insufficient data.
20. **Invalid-Data Rule:** Zero down-day variance halts execution.
21. **Data-Quality Prerequisites:** Clean benchmark feed.
22. **Edge Cases:** Prolonged bull market with very few down-days creates estimation instability.
23. **Units:** Dimensionless coefficient ($0.82$).
24. **Known Limitations:** Discontinuous conditioning at zero ignores marginal down-days; smaller sample size increases standard error.
25. **Candidate Alternatives:** Bawa-Lindenberg downside beta ($R_{b,t} < \mu_b$); dual-beta regime switching.
26. **Unresolved Methodological Questions:** Conditioning threshold ($R_{b,t} < 0$ vs $R_{b,t} < R_{f,t}$).
27. **Validation Required:** Benchmark against quant-engine `downside_beta.py`.
28. **Epistemic Status:** `CANDIDATE / REQUIRES VALIDATION`

---

### METRIC: MKT-03 (Upside Capture) — Candidate Analytical Metric (20 of 30)
1. **Metric Code:** `MKT-03`
2. **Metric Name:** Upside Capture Ratio (3Y)
3. **Analytical Dimension:** Market Sensitivity
4. **Purpose:** Measures return captured by the scheme during periods when the benchmark generated positive returns.
5. **Exact Candidate Formulation:**
   $$\text{Upside Capture} = \frac{\text{Fund cumulative return during benchmark-positive periods}}{\text{Benchmark cumulative return during those same periods}}$$
   $$\text{UC} = \frac{\prod_{t \in Up} (1 + R_{p,t}) - 1}{\prod_{t \in Up} (1 + R_{b,t}) - 1} \times 100, \quad Up = \{t : R_{b,t} > 0\}$$
   **The exact subset return aggregation and compounding convention is: CANDIDATE / REQUIRES VALIDATION. Do not claim these calculations are validated.**
6. **Required Inputs:** Daily NAV series and Benchmark TRI series over 36 months.
7. **Required Data Frequency:** Daily.
8. **Calculation Frequency:** As-of analysis request date.
9. **Lookback/Window:** 36 calendar months.
10. **Minimum Observations:** $\ge 150$ positive benchmark days. `CANDIDATE ENGINEERING PARAMETER — REQUIRES VALIDATION`.
11. **Observation-Date Semantics:** Subsampled paired trading days where $R_{b,t} > 0$.
12. **PIT / Knowledge-Cutoff:** Strictly $T \le \text{cutoff}$.
13. **Benchmark Dependency:** `TRUE`
14. **Risk-Free Dependency:** `FALSE`
15. **Annualization Convention:** Unannualized cumulative product over subset. Candidate convention; requires validation.
16. **Denominator Convention:** Cumulative benchmark up-market return ($> 0$).
17. **Missing-Data Rule:** Paired exclusion.
18. **Insufficient-History Rule:** $< 150$ positive days returns insufficient data.
19. **Invalid-Data Rule:** Zero cumulative benchmark return halts execution.
20. **Data-Quality Prerequisites:** Synchronous daily dates.
21. **Edge Cases:** Portfolio falls on days when benchmark rises (yields negative Upside Capture).
22. **Units:** Percentage ($105.4 = 105.4\%$).
23. **Known Limitations:** Non-contiguous compounding creates path-dependent cumulative return over an artificial timeline.
24. **Candidate Alternatives:** Monthly return upside capture; arithmetic mean ratio.
25. **Unresolved Methodological Questions:** Daily non-contiguous compounding vs monthly capture ratio.
26. **Validation Required:** Benchmark against quant-engine `capture.py`.
27. **Epistemic Status:** `CANDIDATE / REQUIRES VALIDATION`

---

### METRIC: MKT-04 (Downside Capture) — Candidate Analytical Metric (21 of 30)
1. **Metric Code:** `MKT-04`
2. **Metric Name:** Downside Capture Ratio (3Y)
3. **Analytical Dimension:** Market Sensitivity
4. **Purpose:** Measures return captured by the scheme during periods when the benchmark generated negative returns.
5. **Exact Candidate Formulation:**
   $$\text{Downside Capture} = \frac{\text{Fund cumulative return during benchmark-negative periods}}{\text{Benchmark cumulative return during those same periods}}$$
   $$\text{DC} = \frac{\prod_{t \in Down} (1 + R_{p,t}) - 1}{\prod_{t \in Down} (1 + R_{b,t}) - 1} \times 100, \quad Down = \{t : R_{b,t} < 0\}$$
   **The exact subset return aggregation and compounding convention is: CANDIDATE / REQUIRES VALIDATION. Do not claim these calculations are validated.**
6. **Required Inputs:** Daily NAV series and Benchmark TRI series over 36 months.
7. **Required Data Frequency:** Daily.
8. **Calculation Frequency:** As-of analysis request date.
9. **Lookback/Window:** 36 calendar months.
10. **Minimum Observations:** $\ge 100$ negative benchmark days. `CANDIDATE ENGINEERING PARAMETER — REQUIRES VALIDATION`.
11. **Observation-Date Semantics:** Subsampled paired trading days where $R_{b,t} < 0$.
12. **PIT / Knowledge-Cutoff:** Strictly $T \le \text{cutoff}$.
13. **Benchmark Dependency:** `TRUE`
14. **Risk-Free Dependency:** `FALSE`
15. **Annualization Convention:** Unannualized cumulative product over subset. Candidate convention; requires validation.
16. **Denominator Convention:** Cumulative benchmark down-market return ($< 0$).
17. **Missing-Data Rule:** Paired exclusion.
18. **Insufficient-History Rule:** $< 100$ down-days returns insufficient data.
19. **Invalid-Data Rule:** Zero cumulative benchmark loss halts execution.
20. **Data-Quality Prerequisites:** Synchronous dates.
21. **Edge Cases:** Fund gains during benchmark down-days: ratio is negative. Candidate convention outputs negative ratio accompanied by diagnostic flag `INVERSE_CAPTURE_GAIN`.
22. **Units:** Percentage ($78.2 = 78.2\%$). Lower is superior.
23. **Known Limitations:** Negative values require careful explanation to prevent users interpreting negative capture as severe underperformance.
24. **Candidate Alternatives:** Monthly downside capture; arithmetic mean ratio.
25. **Unresolved Methodological Questions:** Sign representation when fund gains during benchmark declines; daily subset vs monthly capture.
26. **Validation Required:** Benchmark against quant-engine `capture.py`.
27. **Epistemic Status:** `CANDIDATE / REQUIRES VALIDATION`

---

### METRIC: MKT-05 (Capture Spread) — Candidate Analytical Metric (22 of 30)
1. **Metric Code:** `MKT-05`
2. **Metric Name:** Capture Spread (3Y)
3. **Analytical Dimension:** Market Sensitivity
4. **Purpose:** Evaluates capture asymmetry by calculating the net difference between Upside Capture and Downside Capture.
5. **Exact Candidate Formulation:**
   $$\text{Capture Spread} = \text{Upside Capture} - \text{Downside Capture}$$
   $$\text{Spread}_{capture} = \text{UC}_{3Y} - \text{DC}_{3Y}$$
6. **Required Inputs:** Upside Capture (`MKT-03`) and Downside Capture (`MKT-04`).
7. **Required Data Frequency:** Derived.
8. **Calculation Frequency:** As-of analysis request date.
9. **Lookback/Window:** 36 calendar months.
10. **Minimum Observations:** Inherits from `MKT-03` and `MKT-04`.
11. **Observation-Date Semantics:** Derived.
12. **PIT / Knowledge-Cutoff:** Strictly $T \le \text{cutoff}$.
13. **Benchmark Dependency:** `TRUE`
14. **Risk-Free Dependency:** `FALSE`
15. **Annualization Convention:** None (difference between percentages).
16. **Denominator Convention:** None.
17. **Missing-Data Rule:** If either capture metric is missing, spread is `NULL`.
18. **Insufficient-History Rule:** Inherits.
19. **Invalid-Data Rule:** Inherits.
20. **Data-Quality Prerequisites:** Verified constituent capture metrics.
21. **Edge Cases:** Negative Downside Capture expands the spread.
22. **Units:** Percentage points ($+27.2 = +27.2\text{ percentage points}$).
23. **Known Limitations:** Linear spread ignores scale ($120 - 100 = 20$ vs $80 - 60 = 20$).
24. **Candidate Alternatives:** Capture Ratio: $\text{UC} / \text{DC}$.
25. **Unresolved Methodological Questions:** Linear spread vs capture ratio ($\text{UC} / \text{DC}$).
26. **Validation Required:** Assembly and unit testing.
27. **Epistemic Status:** `CANDIDATE / REQUIRES VALIDATION` (Needs Assembly).

---

## 13. Benchmark & Alpha Methodology Specifications (Metrics 23 to 24 of Analytical Inventory)

---

### METRIC: REL-01 (Tracking Error 3Y) — Candidate Analytical Metric (23 of 30)
1. **Metric Code:** `REL-01`
2. **Metric Name:** Tracking Error (3Y Annualized)
3. **Analytical Dimension:** Benchmark / Alpha
4. **Purpose:** Measures active return volatility, quantifying how closely the scheme tracks its primary benchmark.
5. **Exact Candidate Formulation:**
   Tracking Error = standard deviation of active returns
   where:
   $$\text{Active Return}_t = \text{Fund Return}_t - \text{Benchmark Return}_t$$
   $$e_t = R_{p,t} - R_{b,t}$$
   $$\text{TE} = \sqrt{\frac{1}{N-1}\sum_{t=1}^N (e_t - \bar{e})^2} \times \sqrt{K}$$
   **Use sample standard deviation with N-1 as the candidate estimator. Annualization convention remains candidate/unresolved.**
6. **Required Inputs:** Synchronous daily NAV and Benchmark TRI series over 36 months.
7. **Required Data Frequency:** Daily.
8. **Calculation Frequency:** As-of analysis request date.
9. **Lookback/Window:** 36 calendar months.
10. **Minimum Observations:** 700 paired trading days. `CANDIDATE ENGINEERING PARAMETER — REQUIRES VALIDATION`.
11. **Observation-Date Semantics:** Paired trading days.
12. **PIT / Knowledge-Cutoff:** Strictly $T \le \text{cutoff}$.
13. **Benchmark Dependency:** `TRUE` (Primary benchmark TRI).
14. **Risk-Free Dependency:** `FALSE`
15. **Annualization Convention:** Multiplied by $\sqrt{252}$. Candidate convention; requires validation.
16. **Denominator Convention:** Sample divisor $N - 1$. Candidate convention; requires validation.
17. **Missing-Data Rule:** Paired deletion.
18. **Insufficient-History Rule:** $< 700$ paired observations returns insufficient data.
19. **Invalid-Data Rule:** Non-finite returns halt execution.
20. **Data-Quality Prerequisites:** Verified Total Return Index series.
21. **Edge Cases:** Index fund tracking error reflects expense drag, cash drag, and replication friction.
22. **Units:** Decimal percentage ($0.045 = 4.5\%$).
23. **Known Limitations:** In active equity, high tracking error can reflect either unconstrained skill or undisciplined factor risk.
24. **Candidate Alternatives:** Root mean square active return (without subtracting active mean); population divisor ($N$).
25. **Unresolved Methodological Questions:** Sample divisor ($N-1$) vs population divisor ($N$).
26. **Validation Required:** Benchmark against quant-engine `tracking_error.py`.
27. **Epistemic Status:** `CANDIDATE / REQUIRES VALIDATION`

---

### METRIC: REL-02 (Jensen's Alpha 3Y) — Candidate Analytical Metric (24 of 30)
1. **Metric Code:** `REL-02`
2. **Metric Name:** Jensen's Alpha (3Y Annualized)
3. **Analytical Dimension:** Benchmark / Alpha
4. **Purpose:** Measures the average excess return generated above the return predicted by the Capital Asset Pricing Model (CAPM).
5. **Exact Candidate Formulation:**
   $$\text{Jensen Alpha} = R_p - [R_f + \text{Beta} \times (R_m - R_f)]$$
   **Exact return frequency, risk-free rate, beta estimation window, and annualization treatment remain validation items. Do not claim production approval.**
6. **Required Inputs:** NAV series, Benchmark TRI series, risk-free rate series, Beta (`MKT-01`).
7. **Required Data Frequency:** Daily.
8. **Calculation Frequency:** As-of analysis request date.
9. **Lookback/Window:** 36 calendar months.
10. **Minimum Observations:** 700 paired trading days. `CANDIDATE ENGINEERING PARAMETER — REQUIRES VALIDATION`.
11. **Observation-Date Semantics:** Synchronous daily observations.
12. **PIT / Knowledge-Cutoff:** Strictly $T \le \text{cutoff}$.
13. **Benchmark Dependency:** `TRUE` (Primary benchmark TRI).
14. **Risk-Free Dependency:** `TRUE` (Governed risk-free source).
15. **Annualization Convention:** Candidate convention; requires validation.
16. **Denominator Convention:** OLS normal equations / algebraic excess return form.
17. **Missing-Data Rule:** Triplet deletion.
18. **Insufficient-History Rule:** $< 700$ triplets returns insufficient data.
19. **Invalid-Data Rule:** Benchmark excess variance zero halts execution.
20. **Data-Quality Prerequisites:** All three series verified.
21. **Edge Cases:** High beta in bull market falsely appears as alpha if excess returns are omitted.
22. **Units:** Decimal percentage ($0.032 = +3.2\%\text{ annualized alpha}$).
23. **Known Limitations:** Assumes single-index CAPM; does not control for size, value, momentum, or quality factor exposures.
24. **Candidate Alternatives:** Time-series OLS regression intercept $\alpha_{daily} \times 252$ vs ex-post algebraic formula.
25. **Unresolved Methodological Questions:** Time-series OLS regression vs ex-post algebraic formula; multi-factor alpha attribution.
26. **Validation Required:** Benchmark against quant-engine `alpha.py`.
27. **Epistemic Status:** `CANDIDATE / REQUIRES VALIDATION`

---

## 14. Portfolio Structure Methodology Specifications (Metrics 25 to 29 of Analytical Inventory)

---

### METRIC: PRT-01 (Top-10 Concentration) — Candidate Analytical Metric (25 of 30)
1. **Metric Code:** `PRT-01`
2. **Metric Name:** Top-10 Concentration
3. **Analytical Dimension:** Portfolio Structure
4. **Purpose:** Measures issuer concentration by summing the portfolio weights of the 10 largest holdings.
5. **Exact Candidate Formulation:**
   $$\text{Top-10 Concentration} = \text{sum of portfolio weights of the ten largest holdings}$$
   $$C_{10} = \sum_{i=1}^{10} w_{(i)}$$
   **The denominator/universe treatment must use the portfolio holdings universe defined by the source disclosure. Do NOT invent a different denominator. If source holdings do not permit reliable reconstruction, mark the metric unavailable rather than fabricate.**
6. **Required Inputs:** Monthly portfolio holdings disclosure snapshot.
7. **Required Data Frequency:** Monthly portfolio disclosure.
8. **Calculation Frequency:** Monthly as-of portfolio disclosure date.
9. **Lookback/Window:** Latest available monthly snapshot $\le \text{cutoff}$.
10. **Minimum Observations:** 1 complete verified monthly holdings snapshot. `CANDIDATE ENGINEERING PARAMETER — REQUIRES VALIDATION`.
11. **Observation-Date Semantics:** Effective snapshot date (last calendar day of disclosure month).
12. **PIT / Knowledge-Cutoff:** Available strictly after AMC disclosure publication date ($T_{avail} \le \text{cutoff}$).
13. **Benchmark Dependency:** `FALSE`
14. **Risk-Free Dependency:** `FALSE`
15. **Annualization Convention:** None (snapshot point-in-time percentage).
16. **Denominator / Universe Convention:** Portfolio holdings universe defined by source disclosure. Candidate convention; requires validation.
17. **Missing-Data Rule:** If snapshot is unfiled beyond statutory deadline, flag as `STALE_DISCLOSURE`. (Diagnostic flag, not a quality taxonomy state).
18. **Insufficient-History Rule:** No holdings snapshot filed returns insufficient data.
19. **Invalid-Data Rule:** Sum of reported holdings diverging from AUM by $> 2\%$ halts with holdings reconciliation error.
20. **Data-Quality Prerequisites:** Disclosed holdings must parse valid ISINs and positive market values.
21. **Edge Cases:** Single company with multiple share classes (e.g. DVR shares); should be aggregated by issuing corporate parent.
22. **Units:** Percentage ($48.6 = 48.6\%$).
23. **Known Limitations:** Monthly snapshot does not capture intra-month trading, window dressing, or portfolio churn.
24. **Candidate Alternatives:** Renormalizing over equity holdings only vs total net assets.
25. **Unresolved Methodological Questions:** Total NAV denominator vs Total Equity denominator; parent company ISIN aggregation.
26. **Validation Required:** Verification against AMC monthly portfolio disclosures.
27. **Epistemic Status:** `CANDIDATE / REQUIRES VALIDATION` (Needs Assembly).

---

### METRIC: PRT-02 (Effective Number of Holdings) — Candidate Analytical Metric (26 of 30)
1. **Metric Code:** `PRT-02`
2. **Metric Name:** Effective Number of Holdings
3. **Analytical Dimension:** Portfolio Structure
4. **Purpose:** Quantifies portfolio diversification by calculating the inverse Herfindahl-Hirschman Index across portfolio weights.
5. **Exact Candidate Formulation:**
   $$\text{Effective Number of Holdings} = \frac{1}{\sum w_i^2}$$
   where $w_i$ are portfolio weights.
   **Weights must be normalized over the explicitly defined holdings universe. The exact denominator/universe convention remains subject to validation.**
6. **Required Inputs:** Monthly portfolio holdings snapshot.
7. **Required Data Frequency:** Monthly.
8. **Calculation Frequency:** As-of portfolio disclosure date.
9. **Lookback/Window:** Latest available monthly snapshot $\le \text{cutoff}$.
10. **Minimum Observations:** 1 snapshot with $\ge 5$ holdings. `CANDIDATE ENGINEERING PARAMETER — REQUIRES VALIDATION`.
11. **Observation-Date Semantics:** Portfolio disclosure date with publication lag.
12. **PIT / Knowledge-Cutoff:** Strictly $T_{avail} \le \text{cutoff}$.
13. **Benchmark Dependency:** `FALSE`
14. **Risk-Free Dependency:** `FALSE`
15. **Annualization Convention:** None.
16. **Denominator / Universe Convention:** Weights normalized over explicitly defined holdings universe ($\sum w_i = 1.0$). Candidate convention; requires validation.
17. **Missing-Data Rule:** Incomplete holdings snapshot halts calculation.
18. **Insufficient-History Rule:** No valid snapshot returns insufficient data.
19. **Invalid-Data Rule:** Negative weights or non-finite values rejected.
20. **Data-Quality Prerequisites:** Clean parsed holdings.
21. **Edge Cases:** Top-heavy portfolio (e.g. 80 stocks with 50% in top 3) produces low effective count ($\sim 15$).
22. **Units:** Effective stock count (real number: $28.4$).
23. **Known Limitations:** Assumes zero correlation between assets; does not capture sector clustering.
24. **Candidate Alternatives:** Entropy-based diversification measure; sector-adjusted ENH.
25. **Unresolved Methodological Questions:** Renormalization of equity base vs treating cash as a single holding.
26. **Validation Required:** Benchmark against quant-engine `concentration.py`.
27. **Epistemic Status:** `CANDIDATE / REQUIRES VALIDATION`

---

### METRIC: PRT-03 (Active Share) — Candidate Analytical Metric (27 of 30)
1. **Metric Code:** `PRT-03`
2. **Metric Name:** Active Share
3. **Analytical Dimension:** Portfolio Structure
4. **Purpose:** Measures the proportion of fund stock holdings that differ from the primary benchmark holdings.
5. **Exact Candidate Formulation:**
   $$\text{Active Share} = 0.5 \times \sum |w_{fund,i} - w_{benchmark,i}|$$
   **The fund and benchmark holdings must be aligned to the same security universe and date. Benchmark constituent weights are NOT being ingested/approved in Phase 2H. Therefore this remains a candidate methodology only.**
6. **Required Inputs:** Monthly fund portfolio holdings snapshot and synchronous benchmark constituent weights.
7. **Required Data Frequency:** Monthly.
8. **Calculation Frequency:** Monthly as-of disclosure date.
9. **Lookback/Window:** Latest synchronous snapshot.
10. **Minimum Observations:** 1 paired monthly snapshot. `CANDIDATE ENGINEERING PARAMETER — REQUIRES VALIDATION`.
11. **Observation-Date Semantics:** Month-end portfolio date.
12. **PIT / Knowledge-Cutoff:** Strictly $T_{avail} \le \text{cutoff}$ for both sources.
13. **Benchmark Dependency:** `TRUE` (Requires benchmark constituent weights).
14. **Risk-Free Dependency:** `FALSE`
15. **Annualization Convention:** None.
16. **Denominator Convention:** Sum of weights normalized to $1.0$ for both vectors. Candidate convention; requires validation.
17. **Missing-Data Rule:** Missing benchmark constituent data halts calculation.
18. **Insufficient-History Rule:** Absence of benchmark weights returns insufficient data.
19. **Invalid-Data Rule:** Unmapped ISINs exceeding 5% of portfolio halts execution.
20. **Data-Quality Prerequisites:** Accurate security-level ISIN mapping across fund and index.
21. **Edge Cases:** Fund holds unlisted or foreign equity absent from domestic benchmark.
22. **Units:** Decimal percentage ($0.685 = 68.5\%$).
23. **Known Limitations:** Requires full monthly benchmark index constituent weights, which are often proprietary and subject to disclosure lags.
24. **Candidate Alternatives:** Active Share against category median portfolio; sector active share.
25. **Unresolved Methodological Questions:** Sourcing automated monthly benchmark constituent weights; cash treatment in Active Share.
26. **Validation Required:** Benchmark against quant-engine `active_share.py`.
27. **Epistemic Status:** `CANDIDATE / REQUIRES VALIDATION`

---

### METRIC: PRT-04 (Monthly Weight Turnover) — Candidate Analytical Metric (28 of 30)
1. **Metric Code:** `PRT-04`
2. **Metric Name:** Monthly Weight Turnover
3. **Analytical Dimension:** Portfolio Structure
4. **Purpose:** Measures the fraction of portfolio weight reallocated between two consecutive monthly disclosure snapshots.
5. **Exact Candidate Formulation:**
   $$\text{Monthly Weight Turnover} = \text{sum of absolute changes in portfolio weights across holdings}$$
   $$\text{Turnover}_{wt} = \sum_{i=1}^{M} |w_{t,i} - w_{t-1,i}|$$
   **Use consecutive monthly portfolio observations. The exact handling of additions, removals, cash, and security identity mapping remains a validation item.**
6. **Required Inputs:** Two consecutive monthly portfolio holdings snapshots.
7. **Required Data Frequency:** Monthly.
8. **Calculation Frequency:** Monthly.
9. **Lookback/Window:** 2 consecutive calendar months.
10. **Minimum Observations:** 2 consecutive snapshots. `CANDIDATE ENGINEERING PARAMETER — REQUIRES VALIDATION`.
11. **Observation-Date Semantics:** Month-end dates.
12. **PIT / Knowledge-Cutoff:** Strictly $T_{avail, t} \le \text{cutoff}$.
13. **Benchmark Dependency:** `FALSE`
14. **Risk-Free Dependency:** `FALSE`
15. **Annualization Convention:** Multiplied by 12 ($\times 12$) if annualized. Candidate convention; requires validation.
16. **Denominator Convention:** Normalized portfolio weights. Candidate convention; requires validation.
17. **Missing-Data Rule:** Missing intermediate month halts calculation.
18. **Insufficient-History Rule:** $< 2$ consecutive snapshots returns insufficient data.
19. **Invalid-Data Rule:** Non-reconciling holdings halt execution.
20. **Data-Quality Prerequisites:** Consistent ISIN identifiers across snapshots.
21. **Edge Cases:** Large investor subscription/redemption distorts weights without active trading.
22. **Units:** Decimal percentage ($0.082 = 8.2\%\text{ monthly weight turnover}$).
23. **Known Limitations:** Conflates passive price drift with active manager trading; does not capture round-trip trades executed and closed within the month.
24. **Candidate Alternatives:** Half sum: $0.5 \times \sum |w_{t,i} - w_{t-1,i}|$; SEBI disclosed turnover ratio: $\frac{\min(\text{Purchases}, \text{Sales})}{\text{Avg AUM}}$.
25. **Unresolved Methodological Questions:** Passive price drift correction; reconciliation with SEBI factsheet turnover disclosure.
26. **Validation Required:** Benchmark against quant-engine `turnover.py`.
27. **Epistemic Status:** `CANDIDATE / REQUIRES VALIDATION`

---

### METRIC: PRT-05 (Cash & Equivalent Allocation %) — Candidate Analytical Metric (29 of 30)
1. **Metric Code:** `PRT-05`
2. **Metric Name:** Cash & Equivalent Allocation %
3. **Analytical Dimension:** Portfolio Structure
4. **Purpose:** Measures the proportion of scheme net assets allocated to cash, TREPS, reverse repo, and net current assets rather than productive equity holdings.
5. **Exact Candidate Formulation:**
   $$\text{Cash \& Equivalent Allocation \%} = \text{sum of portfolio weights classified as cash or cash-equivalent holdings}$$
   $$\text{Cash}\% = \frac{\text{Value}_{cash} + \text{Value}_{TREPS} + \text{Value}_{net\_current\_assets}}{\text{Total Scheme Net Assets}} \times 100$$
6. **Mandatory Identity Rules:**
   - **Identity:** **Cash & Equivalent Allocation %**.
   - **Allocation vs Drag:** This is strictly an **allocation metric**, not an opportunity-cost calculation. Conflating allocation percentage with opportunity-cost drag is prohibited.
   - If the product later requires opportunity-cost cash drag, that must be formulated as a distinct metric with a separate code and methodology. Do not merge them.
7. **Required Inputs:** Monthly portfolio holdings disclosure snapshot.
8. **Required Data Frequency:** Monthly.
9. **Calculation Frequency:** Monthly as-of disclosure date.
10. **Lookback/Window:** Latest available monthly snapshot $\le \text{cutoff}$.
11. **Minimum Observations:** 1 verified snapshot. `CANDIDATE ENGINEERING PARAMETER — REQUIRES VALIDATION`.
12. **Observation-Date Semantics:** Month-end date.
13. **PIT / Knowledge-Cutoff:** Strictly $T_{avail} \le \text{cutoff}$.
14. **Benchmark Dependency:** `FALSE`
15. **Risk-Free Dependency:** `FALSE`
16. **Annualization Convention:** None (snapshot point-in-time percentage).
17. **Denominator Convention:** Total scheme net asset value. Candidate convention; requires validation.
18. **Missing-Data Rule:** Stale disclosure flagged if unfiled.
19. **Insufficient-History Rule:** No snapshot returns insufficient data.
20. **Invalid-Data Rule:** Negative cash exceeding $-5\%$ (indicating uncollateralized leverage) flagged as `SUSPICIOUS`. (Diagnostic flag, not quality state).
21. **Data-Quality Prerequisites:** Parsed asset allocation breakdown.
22. **Edge Cases:** Derivative margin cash classified under cash vs equity derivatives.
23. **Units:** Percentage ($5.4 = 5.4\%$).
24. **Known Limitations:** Snapshot reflects single month-end day; does not capture intra-month tactical cash deployment.
25. **Candidate Alternatives:** Economic opportunity cost drag: $w_{cash} \times (R_b - R_{cash})$ (separate future research formulation).
26. **Unresolved Methodological Questions:** Definition of cash equivalents (whether sovereign T-bills $< 91\text{D}$ are cash or debt).
27. **Validation Required:** Verification against AMC monthly portfolio asset-allocation summary.
28. **Epistemic Status:** `CANDIDATE / REQUIRES VALIDATION` (Needs Assembly).

---

## 15. Governance / Expense Methodology Specification (Metric 30 of Analytical Inventory)

---

### METRIC: GOV-01 (Direct Plan TER) — Candidate Analytical Metric (30 of 30)
1. **Metric Code:** `GOV-01`
2. **Metric Name:** Direct Plan TER
3. **Analytical Dimension:** Governance / Expense
4. **Purpose:** Measures the annualized percentage fee deducted from scheme net assets for management, administration, and regulatory expenses.
5. **Exact Candidate Methodology:**
   Direct Plan TER is taken from the point-in-time declared authoritative Direct Plan TER disclosure.
   - Synthetic component summation is prohibited; TER is ingested as a single declared ratio.
   - There is no generic hardcoded invalidation threshold.
   - An unusually high value may be flagged as a diagnostic requiring source verification, but is not automatically `INVALID` because of a hardcoded ceiling.
   - The exact regulatory threshold, if ever applied, must be sourced to the applicable statutory regulation and effective date. Phase 2H does not establish a generic threshold.
6. **Required Inputs:** AMC / AMFI daily or monthly TER disclosure feed for the specific Direct Plan scheme code.
7. **Required Data Frequency:** Daily disclosure (as mandated by SEBI).
8. **Calculation Frequency:** As-of analysis cutoff date.
9. **Lookback/Window:** Most recent effective TER $\le \text{cutoff}$.
10. **Minimum Observations:** 1 valid official TER record. `CANDIDATE ENGINEERING PARAMETER — REQUIRES VALIDATION`.
11. **Observation-Date Semantics:** Effective date of declared expense ratio.
12. **PIT / Knowledge-Cutoff:** Available strictly $T_{avail} \le \text{cutoff}$.
13. **Benchmark Dependency:** `FALSE`
14. **Risk-Free Dependency:** `FALSE`
15. **Annualization Convention:** Quoted on an annualized percentage basis under SEBI regulations.
16. **Denominator Convention:** Scheme average daily net assets.
17. **Missing-Data Rule:** Fall back to preceding declared TER within last 30 days. If missing beyond 30 days, halt with diagnostic `STALE_TER_DATA`.
18. **Insufficient-History Rule:** No TER record returns insufficient data.
19. **Invalid-Data Rule:** $\text{TER} \le 0$ rejected as `INVALID`. No hardcoded maximum TER invalidation rule exists.
20. **Data-Quality Prerequisites:** Verified separation of Direct Plan TER from Regular Plan TER.
21. **Edge Cases:** Mid-month TER modification; B30 incentive fee adjustments.
22. **Units:** Percentage points ($0.75 = 0.75\%\text{ per annum}$).
23. **Known Limitations:** Backward-looking TER does not capture future fee hikes; does not reflect portfolio transaction costs (brokerage, STT) not included in TER.
24. **Candidate Alternatives:** 12-month time-weighted average TER vs point-in-time latest TER.
25. **Unresolved Methodological Questions:** Point-in-time latest TER vs 12-month time-weighted average TER.
26. **Validation Required:** Verification against AMFI daily TER portal.
27. **Epistemic Status:** `CANDIDATE / REQUIRES VALIDATION` (Needs Assembly).

---

## 16. Minimum Observation Requirements

Any thresholds specified across candidate metrics:
- 240 observations (1Y window)
- 700 observations (3Y window)
- 1,200 observations (5Y window)
- 100 downside observations (Downside Beta, Downside Capture)
- 150 upside observations (Upside Capture)

**MUST be labelled:**
$$\text{CANDIDATE ENGINEERING PARAMETER — REQUIRES VALIDATION}$$

They are **NOT** established methodological facts. Do not claim that these thresholds have been empirically validated. If a metric lacks enough observations, the correct behavior is to expose insufficient evidence/data (`NULL` value with diagnostic flag) rather than fabricate a result.

---

## 17. Vendor Reconciliation

Morningstar / FactSet / Bloomberg comparisons are **ONLY a future validation design**. No vendor result has been obtained or validated in Phase 2H.

Therefore, this section is explicitly titled and governed as:
> **"CANDIDATE VALIDATION DESIGN — NOT EXECUTED."**

If a future validation compares outputs, identical inputs must be controlled, including:
- security/fund identity
- observation dates
- benchmark
- corporate-action treatment
- missing observations
- return frequency
- annualization convention
- risk-free rate
- PIT knowledge cutoff
- revision handling

A 1 bp tolerance may be used as a future candidate reconciliation criterion, but it is **NOT an established validation result**. Do not claim vendor agreement or access to vendor terminals.

---

## 18. Six-Dimension Data Quality Taxonomy

The approved six-dimensional taxonomy is immutable. Preserve exactly these six dimensions and valid states:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     IMMUTABLE 6-DIMENSIONAL TAXONOMY                    │
├───────────────────┬─────────────────────────────────────────────────────┤
│ 1. QUALITY        │ VALID / INVALID / SUSPICIOUS                        │
│ 2. VERIFICATION   │ VERIFIED / UNVERIFIED                               │
│ 3. REVISION       │ ORIGINAL / REVISED / SUPERSEDED                     │
│ 4. FRESHNESS      │ CURRENT / STALE                                     │
│ 5. PRESENCE       │ AVAILABLE / MISSING / NOT_APPLICABLE                │
│ 6. INTEGRITY      │ DUPLICATE / CONFLICTING                             │
└───────────────────┴─────────────────────────────────────────────────────┘
```

### Taxonomy Governance Invariants:
1. **No New States:** Under no circumstances may new states be added into these six dimensions.
2. **Diagnostic Distinction:** Terms such as:
   - `HISTORICAL_BACKFILL`
   - `TEMPORAL_AVAILABILITY_UNRECORDED`
   - `STALE_DISCLOSURE`
   - `CONFLICTING_AUTHORITY`
   are **diagnostics / temporal metadata only**. They are **NOT** replacements for or additions to the six taxonomy dimensions. Preserve this distinction everywhere.
3. **Never Impute Zero:** An observation with `Quality = INVALID` or `Presence = MISSING` is never substituted with zero.
4. **No Scoring Penalties:** Missing data results in calculation suppression with explicit diagnostic flags, never an automated scoring penalty.

---

## 19. Point-in-Time (PIT) & Look-Ahead Semantics

Preserve the Phase 2C/2F PIT rules. The authoritative rule is:
> **"Bitemporal storage enables PIT reconstruction; it does not by itself prevent look-ahead bias."**

Every calculation must use an explicit knowledge cutoff / information-set rule:
$$\text{Eligible Observations} = \{ \mathcal{O}_i \mid \text{availability\_time}(\mathcal{O}_i) \le \tau_{cutoff} \}$$

### 19.1 Strict Separation of Four Temporal Coordinates:
1. **Effective / Valuation Date ($D_{eff}$):** The financial valuation date to which the data applies.
2. **Factual Source Availability ($T_{avail}$):** The exact real-world instant when the data was published and accessible.
3. **Ingestion Timestamp ($T_{ingest}$):** The instant YUKIRA persisted the record into the database.
4. **Analytical Knowledge Cutoff ($\tau_{cutoff}$):** The analytical boundary simulated by the researcher.

### 19.2 AMFI Historical Batch Data Temporal Convention:
AMFI historical batch files do not provide exact historical publication timestamps. YUKIRA does **not** fabricate publication timestamps. The historical End-of-Day (EOD) cutoff convention is documented strictly as a **CANDIDATE ANALYTICAL CONVENTION**, not factual source availability.

---

## 20. Methodology Validation Framework vs. Software Testing

A fundamental principle of YUKIRA governance is the separation of **Software Testing** from **Methodology Validation**:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          EPISTEMIC SEPARATION                           │
├────────────────────────────────────┬────────────────────────────────────┤
│         SOFTWARE TESTING           │       METHODOLOGY VALIDATION       │
├────────────────────────────────────┼────────────────────────────────────┤
│ • Proves code executes as written  │ • Evaluates financial soundness    │
│ • Unit tests, integration tests    │ • Out-of-sample empirical testing  │
│ • Verifies zero runtime crashes    │ • Stress testing across regimes    │
│ • Verifies DB constraints & hashes │ • Statistical power & stability    │
│ • Verifies API response formats    │ • Economic & behavioral validity   │
├────────────────────────────────────┴────────────────────────────────────┤
│  CRITICAL INVARIANT: Passing 100% of software unit tests establishes    │
│  only that the implementation runs without bugs. It does NOT establish   │
│  that the underlying financial methodology is validated or correct.    │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 21. Summary of Unresolved Methodological Questions

The following critical convention questions remain explicitly **UNRESOLVED / REQUIRES VALIDATION** and barred from silent default adoption:

| Question ID | Analytical Area | Competing Defensible Conventions | Governance Status |
| :--- | :--- | :--- | :--- |
| **Q-01** | CAGR Annualization | $365.25$ calendar daycount vs. Fixed $365.0$ vs. Actual elapsed days | `CANDIDATE / REQUIRES VALIDATION` |
| **Q-02** | Volatility Annualization | Fixed 252 Trading Days vs. Exchange-Actual Days (246–250) vs. Calendar Daycount | `CANDIDATE / REQUIRES VALIDATION` |
| **Q-03** | Semideviation Divisor | Sample ($N-1$) divisor (`semideviation.py`) vs. Population ($N$) divisor (`statistics.py`) vs. Downside count ($K$) | `UNRESOLVED / REQUIRES VALIDATION` |
| **Q-04** | Risk-Free Benchmark | FBIL Overnight MIBOR vs. FBIL 91-Day T-Bill Yield | `REQUIRES RESEARCH` |
| **Q-05** | Sortino Hurdle Rate | Nominal Zero ($MAR = 0$) vs. Risk-Free Rate ($MAR = R_f$) | `UNRESOLVED / REQUIRES VALIDATION` |
| **Q-06** | Treynor Numerator | Annualized daily excess return vs. CAGR-based excess return vs. Arithmetic mean excess | `UNRESOLVED / REQUIRES VALIDATION` |
| **Q-07** | Beta Regression Spec | Raw returns co-movement with intercept vs. Excess-over-risk-free returns | `UNRESOLVED / REQUIRES VALIDATION` |
| **Q-08** | Downside Beta Threshold | $R_{b,t} < 0$ vs. $R_{b,t} < R_{f,t}$ vs. Bawa-Lindenberg $\min(R_b - \mu_b, 0)$ | `CANDIDATE / REQUIRES VALIDATION` |
| **Q-09** | Capture Compounding | Daily geometric cumulative product vs. Monthly capture vs. Arithmetic mean ratio | `CANDIDATE / REQUIRES VALIDATION` |
| **Q-10** | Downside Capture Sign | Positive loss ratio vs. Signed negative ratio vs. Inverted gain diagnostic | `CANDIDATE / REQUIRES VALIDATION` |
| **Q-11** | Jensen's Alpha Form | Time-series OLS regression intercept vs. Ex-post algebraic equation | `CANDIDATE / REQUIRES VALIDATION` |
| **Q-12** | VaR Quantile Method | Continuous empirical linear interpolation vs. Nearest rank vs. NIST Type 7 | `CANDIDATE / REQUIRES VALIDATION` |
| **Q-13** | Expected Shortfall Tail | Weak inequality ($\le \text{VaR}$) vs. Strict ($< \text{VaR}$) vs. Coherent weighting | `CANDIDATE / REQUIRES VALIDATION` |
| **Q-14** | Drawdown Recovery Rule | Total Peak-to-Recovery calendar days vs. Peak-to-trough decline days | `CANDIDATE / REQUIRES VALIDATION` |
| **Q-15** | Turnover Definition | Sum of absolute weight changes vs. Half sum vs. SEBI disclosed turnover ratio | `CANDIDATE / REQUIRES VALIDATION` |
| **Q-16** | Top-10 Concentration Denom| Portfolio holdings universe defined by source disclosure vs. Total NAV vs. Equity | `CANDIDATE / REQUIRES VALIDATION` |
| **Q-17** | ENH Portfolio Universe | Explicitly defined holdings universe vs. Normalized equity only vs. Cash inclusion | `CANDIDATE / REQUIRES VALIDATION` |
| **Q-18** | Cash Allocation vs Drag | Gross Cash & Equivalent % of AUM vs. Opportunity cost drag $w_{cash}(R_b - R_{cash})$ | `CANDIDATE / REQUIRES VALIDATION` |
| **Q-19** | TER Regulatory Threshold | Sourced regulatory verification vs. Generic threshold (no hardcoded invalidation) | `CANDIDATE / REQUIRES VALIDATION` |
