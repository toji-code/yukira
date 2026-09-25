# YUKIRA — Phase 2N Methodology Governance Package

**Document Reference:** `docs/research/PHASE_2N_GOVERNANCE_PACKAGE.md`
**Phase:** 2N
**Status:** PROPOSED GOVERNANCE DECISION PACKAGE (CANDIDATE)
**Lifecycle State:** PENDING GOVERNANCE COMMITTEE REVIEW
**Implementation Status:** NOT AUTHORIZED FOR PHASE 2O IMPLEMENTATION
**Validated Count:** 0
**Approved Count:** 0
**Phase 2H Status:** FROZEN (`phase2h_quantitative_methodology.md` ZERO DIFF)


---

## 1. Purpose

This document records the formal human governance committee approval of the five quantitative financial methodology specifications resolved in Phase 2N and validated under `docs/research/PHASE_2N_V2_VALIDATION.md`.

Approval transitions these five methodologies from `CANDIDATE / PROPOSED FOR APPROVAL` to **`APPROVED`**, establishing the authoritative specification for subsequent Phase 2O software implementation.

---

## 2. Approval Scope

### 2.1 Formally Approved Methodologies (5):
1. **M2N-01 — Annualization Framework:** Metric-specific Julian CAGR ($365.25/D$), sample standard deviation volatility ($\sqrt{252}$), active return tracking error ($\sqrt{252}$), annualized Sharpe ratio ($\sqrt{252}$ on daily excess return ratio), and confirmation of unannualized scale-invariant Beta.
2. **M2N-02 — Risk-Free Proxy:** Sourced from FBIL 91-Day Treasury Bill benchmark cutoff yield curve with statutory FIMMDA/RBI $\text{ACT}/365$ linear daily de-annualization ($R_{f,t} = y_{t-1} \times \frac{\Delta d_t}{365.0}$), weekend 3-day interest accrual, and 4-calendar-day preceding lookback boundaries.
3. **M2N-05 — Treynor Ratio Numerator:** Annualized arithmetic mean daily excess return ($\bar{R}_{\text{excess, daily}} \times 252$) over a 36-month lookback window ($\ge 700$ paired trading days), coupled with M2N-06 OLS Beta.
4. **M2N-06 — Portfolio Beta Specification:** Single-index excess-return Ordinary Least Squares (OLS) regression with intercept: $(R_{p,t} - R_{f,t}) = \alpha + \beta(R_{b,t} - R_{f,t}) + \epsilon_t$, evaluated over 36 calendar months ($\ge 700$ paired trading days).
5. **M2N-07 — Downside Beta Specification:** Subsample raw-return covariance/variance ratio conditioned on nominal benchmark down-days ($R_{b,t} < 0$), strictly excluding $R_b \ge 0.0$, with a minimum $\ge 100$ down-days threshold.

### 2.2 Deferred Scope (Excluded from Implementation) (4):
- **M2N-03:** Sortino Ratio / Minimum Acceptable Return (MAR) — *DEFERRED*
- **M2N-04:** Downside Semideviation Divisor ($N$ vs $N-1$ vs $K$) — *DEFERRED*
- **M2N-08:** Upside/Downside Capture Subset Compounding — *DEFERRED*
- **M2N-09:** Capture Spread Calculation — *DEFERRED*

---

## 3. Source Documents & Governance Hierarchy

1. `AGENTS.md` (Authoritative Governance & Operating Manual)
2. `docs/CURRENT_STATE.md` (Current software snapshot & baselines)
3. `docs/ARCHITECTURE.md` (Multi-tier architectural contracts)
4. `docs/DEVELOPMENT_RULES.md` (Engineering rules & PIT standards)
5. `docs/PHASE_STATUS.md` (Phase chronological state)
6. `phase2h_quantitative_methodology.md` (Frozen baseline methodology)
7. `docs/research/PHASE_2N_METHODOLOGY_RESOLUTION.md` (Phase 2N empirical research)
8. `docs/research/PHASE_2N_GOVERNANCE_DECISIONS.md` (Phase 2N governance adjudications)
9. `docs/research/PHASE_2N_V2_VALIDATION.md` (Independent validation audit record)
10. `docs/methodology/phase_2n_v2.md` (Authoritative Approved Methodology Specification)

---

## 4. Validation Evidence Summary

All approved specifications were independently verified in `docs/research/validation/run_full_validation.py` against exact rational decimal arithmetic and NumPy/SciPy vectorized kernels:
- **Numerical Precision:** Discrepancies across pure Python and NumPy implementations strictly $\le 4.44 \times 10^{-16}$.
- **Point-in-Time Integrity:** Strict adherence to `effective_date <= analysis_cutoff` and `availability_time <= knowledge_cutoff` with zero information leakage.
- **Deterministic Reproducibility:** 100% bit-for-bit identical outputs across consecutive validation runs.

---

## 5. Approved & Deferred Methodology Register

| ID | Methodology Name | Validation Result | Approval Status | Lifecycle State | Action |
|---|---|---|---|---|---|
| **M2N-01** | Metric-Specific Annualization | PASS | **APPROVED** | APPROVED | Authorized for Phase 2O |
| **M2N-02** | FBIL 91-Day T-Bill Risk-Free Proxy | PASS | **APPROVED** | APPROVED | Authorized for Phase 2O |
| **M2N-05** | Treynor Ratio Numerator | PASS | **APPROVED** | APPROVED | Authorized for Phase 2O |
| **M2N-06** | Portfolio Beta (Excess-Return OLS) | PASS | **APPROVED** | APPROVED | Authorized for Phase 2O |
| **M2N-07** | Downside Beta ($R_b < 0$) | PASS | **APPROVED** | APPROVED | Authorized for Phase 2O |
| **M2N-03** | Sortino Ratio / MAR | Deferred | **DEFERRED** | DEFERRED | Excluded from Phase 2O |
| **M2N-04** | Downside Semideviation Divisor | Deferred | **DEFERRED** | DEFERRED | Excluded from Phase 2O |
| **M2N-08** | Capture Ratio Compounding | Deferred | **DEFERRED** | DEFERRED | Excluded from Phase 2O |
| **M2N-09** | Capture Spread | Deferred | **DEFERRED** | DEFERRED | Excluded from Phase 2O |

---

## 6. End-to-End Traceability Matrix

| ID | Phase 2H Baseline | Phase 2N Research Finding | V2 Approved Specification | Validation Evidence | Formal Approval Decision |
|---|---|---|---|---|---|
| **M2N-01** | Unresolved universal 252 vs 365 vs 365.25 assumption. | 12bps sensitivity in volatility mandates explicit metric-specific factors. | CAGR: $365.25/D$; Vol/TE/Sharpe: $\sqrt{252}$; Beta: Unannualized. | Test vectors V1.1–V1.6: bit-for-bit Julian leap year & $\sqrt{252}$ scaling pass. | **APPROVED** |
| **M2N-02** | Unresolved risk-free proxy (MIBOR vs 91D T-Bill). | FBIL 91-Day T-Bill is the statutory sovereign benchmark in India. | FBIL 91D T-Bill with FIMMDA $\text{ACT}/365$ linear daily de-annualization ($y \times \Delta d / 365$). | Test vectors V2.1–V2.5: single day, 3-day weekend accrual, & PIT lookback pass. | **APPROVED** |
| **M2N-05** | Unresolved numerator (CAGR vs arithmetic mean). | CAPM alignment requires expected excess return over risk-free rate. | Annualized arithmetic mean daily excess return ($\bar{R}_{\text{excess, daily}} \times 252$) / OLS Beta. | Test vectors V5.1–V5.4: positive, zero, negative excess return, & zero beta error pass. | **APPROVED** |
| **M2N-06** | Raw returns covariance vs excess-return regression. | Econometric standard is single-index excess-return OLS with intercept. | $(R_{p,t} - R_{f,t}) = \alpha + \beta (R_{b,t} - R_{f,t}) + \epsilon_t$, with $\ge 700$ paired days. | Test vectors V6.1–V6.4: OLS slope concordance to $4.44 \times 10^{-16}$, zero variance pass. | **APPROVED** |
| **M2N-07** | Conditioning threshold ($R_b < 0$ vs $R_b < R_f$). | Raw return regression on nominal down-days captures absolute market drawdown sensitivity. | Subsample raw return covariance/variance ratio conditioned on $R_{b,t} < 0$, $\ge 100$ down-days. | Test vectors V7.1–V7.5: strict exclusion of $R_b \ge 0$, down-day threshold pass. | **APPROVED** |

---

## 7. Lifecycle State Transition

```text
CANDIDATE (Phase 2H)
       │
       ▼
RESEARCHED (Phase 2N Research)
       │
       ▼
PROPOSED FOR APPROVAL (Phase 2N Governance Decisions)
       │
       ▼
VALIDATED (Phase 2N V2 Validation Audit — 5 PASS)
       │
       ▼
APPROVED (Phase 2N Formal Governance Approval — 5 APPROVED)
```

- **Validated Count:** 5
- **Approved Count:** 5
- **Deferred Count:** 4 (M2N-03, M2N-04, M2N-08, M2N-09 remain DEFERRED)

---

## 8. Important Epistemic Limitations

Formal methodology approval establishes:
> *"The approved specifications represent deterministic, mathematically validated, and governed algorithms authorized for implementation in the YUKIRA platform."*

Approval explicitly does **NOT** signify:
1. That the metrics provide predictive forecasting power over future fund returns or market drawdowns.
2. That the methodology is empirically superior to all alternate academic specifications.
3. That investment performance or risk outcomes are guaranteed.
4. That the methodology is permanently immutable; it remains versioned and auditable.
5. That the four deferred candidate metrics are validated or approved.

---

## 9. Implementation Authorization Boundary

- **Phase 2O Implementation Authorization:**  
  **`AUTHORIZED FOR APPROVED M2N-01, M2N-02, M2N-05, M2N-06, M2N-07 ONLY.`**
- **Deferred Metrics:** M2N-03, M2N-04, M2N-08, M2N-09 remain strictly unauthorized for production implementation.
- **Phase 2H Status:** `phase2h_quantitative_methodology.md` remains **FROZEN** with ZERO DIFF.

---

## 10. Governance Version History

| Version | Date | Status | Description | Authorizing Body |
|---|---|---|---|---|
| `2H.0` | 2026-09-20 | FROZEN DRAFT | Initial Candidate Methodology Inventory (30 MVP metrics) | YUKIRA Project Committee |
| `2N.1` | 2026-09-24 | RESEARCH | Empirical research package for 9 unresolved conventions | Quant Research Working Group |
| `2N.2` | 2026-09-24 | CANDIDATE | Phase 2N V2 Candidate Proposal (5 candidates, 4 deferred) | Quantitative Review Panel |
| `2N.3` | 2026-09-25 | VALIDATED | Independent Validation Audit (5 Validation Passes) | Independent Audit Engine |
| **`2N.4`** | **2026-09-25** | **APPROVED** | **Formal Governance Approval (5 Approved, 4 Deferred)** | **YUKIRA Governance Committee** |
