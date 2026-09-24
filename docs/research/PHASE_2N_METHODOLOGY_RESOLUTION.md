# YUKIRA — Phase 2N Methodology Resolution & Governance Research

**Document:** PHASE_2N_METHODOLOGY_RESOLUTION.md
**Phase:** 2N
**Status:** FINAL RESEARCH PACKAGE
**Implementation Status:** NOT AUTHORIZED

---

# 1. Purpose
Define the research evidence base for the 9 unresolved quantitative metrics required before formal governance review.

---

# 2. Decision Register

| ID | Decision | Proposed Convention | Empirics | Status |
|---|---|---|---|---|
| M2N-01 | Annualization | Metric-specific | YES | PROPOSED |
| M2N-02 | Sharpe Risk-Free | FBIL 91-Day T-Bill | N/A | PROPOSED |
| M2N-03 | Sortino / MAR | NO CANDIDATE | N/A | DEFERRED |
| M2N-04 | Semideviation Divisor| NO CANDIDATE | YES | DEFERRED |
| M2N-05 | Treynor Numerator | Excess Return | N/A | PROPOSED |
| M2N-06 | Beta Spec | Excess-Return OLS | YES | PROPOSED |
| M2N-07 | Downside Beta | Threshold $R_{b,t} < 0$ | YES | PROPOSED |
| M2N-08 | Capture | NO CANDIDATE | N/A | DEFERRED |
| M2N-09 | Capture Spread | NO CANDIDATE | N/A | DEFERRED |

---

# 3. Decision Details

## Decision M2N-01 (Annualization)
- **Convention:** Metric-specific.
- **Evidence:** $\sqrt{252}$ is an industry-standard market approximation, but calculation-dependent sensitivity (12bps for volatility) mandates an explicit, documented choice rather than a universal assumption.
- **Proposed Convention:** $\sqrt{252}$ for risk-adjusted metrics; 365.25 for CAGR.
- **Limitations:** Does not account for unscheduled market suspensions.

## Decision M2N-04 (Semideviation Divisor)
- **Empirical Audit (Canonical Pilot):** Divisor N (0.0047) vs N-1 (0.0057).
- **Adjudication:** N-1 is mathematically standard for sample estimation, but the codebase conflict prohibits immediate proposal without formal reconciliation.

---

# 4. Governance Status
- Validated: 0
- Approved: 0
- Production Implementation: AUTHORIZED: NO
