# YUKIRA — Phase 2N Methodology V2 Proposal

**Document:** PHASE_2N_METHODOLOGY_V2_PROPOSAL.md
**Phase:** 2N / V2
**Status:** PROPOSED METHODOLOGY SPECIFICATION (CANDIDATE)
**Implementation Status:** NOT AUTHORIZED

---

# 1. Purpose
This document presents the proposed Phase 2N Methodology V2 specifications for five quantitative metrics identified in Phase 2N research as sufficiently defensible for formal governance review.

---

# 2. Relationship to Phase 2H (Frozen)
Phase 2H is the governing foundational specification. Phase 2N V2 acts as a successor amendment candidate.
- All Phase 2H definitions remain in effect for Phase 2I/2J implemented metrics.
- This proposal exclusively details changes for metrics identified in Phase 2N governance.

---

# 3. Decision Register (V2 Candidates)

| ID | Decision | Proposed Convention | Governance Status |
|---|---|---|---|
| M2N-01 | Annualization | Metric-specific (e.g., $\sqrt{252}$) | CANDIDATE |
| M2N-02 | Risk-Free Source | FBIL 91-Day T-Bill | CANDIDATE |
| M2N-05 | Treynor Num | Excess Return (P - Rf) | CANDIDATE |
| M2N-06 | Beta Spec | Excess-Return OLS Regression | CANDIDATE |
| M2N-07 | Downside Beta | Threshold $R_{b,t} < 0$ | CANDIDATE |

*(M2N-03, M2N-04, M2N-08, M2N-09 are DEFERRED / EXCLUDED from V2)*

---

# 4. Methodology V2 Specifications (Proposed)

## M2N-01 — Annualization
- **Resolution:** Annualization applied per metric contract.
- **Rules:**
  - CAGR: 365.25 / elapsed calendar days.
  - Volatility: $\sqrt{252}$ (trading day factor).
  - Tracking Error: $\sqrt{252}$.
- **Governance:** Metric-specific definitions are explicit.

## M2N-02 — Sharpe Risk-Free Source
- **Convention:** FBIL 91-Day Treasury Bill Yield.
- **PIT Rule:** Availability strictly $\le$ knowledge cutoff.

## M2N-05 — Treynor Numerator
- **Convention:** (Portfolio Return - Risk-Free Return) where Risk-Free uses M2N-02.
- **Consistency:** Must be geometrically consistent with annualization conventions.

## M2N-06 — Beta Specification
- **Convention:** OLS intercept (1-factor), excess-return regression:
  $R_{p,t} - R_{f,t} = \alpha + \beta(R_{b,t} - R_{f,t}) + \epsilon_t$
- **PIT Rule:** 700 paired trading days.

## M2N-07 — Downside Beta
- **Convention:** Condition $R_{b,t} < 0$.
- **Formula:** Regression of $\{R_{p,t}\}$ against $\{R_{b,t}\}$ where $R_{b,t} < 0$.

---

# 5. Deferred / Excluded Methodologies
The following remain DEFERRED / EXCLUDED from implementation:
- M2N-03 (Sortino MAR)
- M2N-04 (Semideviation Divisor)
- M2N-08 (Capture Methodology)
- M2N-09 (Capture Spread)

---

# 6. Validation Prerequisites (Phase 2O Gate)
For all PROPOSED FOR APPROVAL:
- Deterministic formula cross-check.
- Boundary condition testing ($N <$ threshold).
- PIT resolution audit.
- Benchmark alignment audit.

---

# 7. Governance Status
**Phase 2H Frozen:** YES
**Methodologies Validated:** 0
**Methodologies Approved:** 0
**Production Implementation Authorized:** NO
