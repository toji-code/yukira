# Brinson-Fachler Multi-Period Attribution Data Dependency Manifest

> **Document Type:** Institutional Data Acquisition & Governance Manifest  
> **Governance Standard:** Phase 2H / AGENTS.md §7 (Strict AI Boundary) & §11 (Methodology Governance)  
> **Status:** GATED / BLOCKED (`BLOCKED_PENDING_STOCK_RETURNS`)  
> **Authoritative Target:** Multi-Period Constituent-Level Brinson-Fachler Return Attribution  

---

## 1. Executive Summary & Epistemic Boundary

In quantitative portfolio analytics, **benchmark composition diagnostics** (measuring weight divergence $\Delta w_i = w_{p,i} - w_{b,i}$, active share, and sector allocation spreads) are fundamentally distinct from **return attribution** (quantifying allocation vs selection return contributions).

$$\text{Active Return } R_p - R_b = \underbrace{\sum_j (w_{p,j} - w_{b,j})(R_{b,j} - R_b)}_{\text{Allocation Effect}} + \underbrace{\sum_j w_{b,j}(R_{p,j} - R_{b,j})}_{\text{Selection Effect}} + \underbrace{\sum_j (w_{p,j} - w_{b,j})(R_{p,j} - R_{b,j})}_{\text{Interaction Effect}}$$

Evaluating this deterministic relationship without synthetic fabrication requires **three complete empirical inputs**:
1. Exact historical benchmark constituent weights ($w_{b,i,t}$) across all constituents.
2. Daily corporate-action-adjusted total return price series ($R_{i,t}$) for every security in the universe.
3. Authentic point-in-time disclosed portfolio holdings ($w_{p,i,t}$).

As established in YUKIRA's epistemic audits, public index feeds provide **membership without complete weights** (only top 10 heavyweights are disclosed) and public fund portfolios provide **monthly snapshot disclosures rather than daily transaction ledgers**. Computing constituent-level attribution on incomplete data violates `AGENTS.md` §1.3 ("No Fake Data") and §6 ("Deterministic Financial Calculation Rule").

Therefore, the **Constituent Return Attribution Readiness Gate** remains strictly **BLOCKED** until the commercial dependencies specified in this manifest are satisfied.

---

## 2. Dependency Matrix & Current Readiness State

| Dependency ID | Requirement Description | Required Grain & Scope | Current YUKIRA Status | Blocking Level |
| :--- | :--- | :--- | :--- | :--- |
| **DEP-01** | Disclosed Portfolio Holdings | Monthly point-in-time equity weights ($w_{p,i}$) | **PRESENT & VERIFIED** (AMFI/AMC Direct Ingestion) | Operational |
| **DEP-02** | AMFI Market-Cap Classifications | Semi-annual Point-in-Time official rankings | **PRESENT & VERIFIED** (V5 Migration + Seeded History) | Operational |
| **DEP-03** | Benchmark Index Returns | Daily TRI NAV / Level series ($R_b$) | **PRESENT & VERIFIED** (NSE Indices TRI Feed) | Operational |
| **DEP-04** | Benchmark Constituent Membership | Exact point-in-time constituents of NIFTY 500 | **PRESENT & VERIFIED** (NSE Indices Archival & Live Snapshots) | Operational |
| **DEP-05** | Complete Benchmark Weights | Daily or monthly constituent weights ($w_{b,i}$) across all 500 stocks | **PARTIAL** (Top 10 Heavyweights only; tail weights unweighted) | **HARD BLOCKER** |
| **DEP-06** | Adjusted Security Total Returns | Daily corporate-action-adjusted price series ($R_{i,t}$) for all equities | **ABSENT** (Zero individual stock price history currently ingested) | **HARD BLOCKER** |
| **DEP-07** | Corporate Action History | Cash/stock dividends, bonus issues, splits, rights, and demergers | **PARTIAL** (Stock-split ISIN migrations mapped; no dividend yield ledger) | **HARD BLOCKER** |

---

## 3. Detailed Data Specifications for Full Brinson Attribution

### 3.1 DEP-05: Complete Benchmark Constituent Weights
- **Commercial Benchmark:** NIFTY 500 Index / NIFTY 500 TRI.
- **Provider:** NSE Indices Limited (Index Services Division).
- **Required Product:** *NIFTY 500 Daily Index Constituent File with Free-Float Market Capitalization and Weights*.
- **Data Attributes Required:**
  - Index Code / ISIN / Security Name.
  - Number of Shares Outstanding.
  - Investible Weight Factor (IWF) / Free-Float Adjustment Factor.
  - Daily Closing Market Price.
  - Index Constituent Weight Percentage (summing to 100.0000%).
- **Licensing Constraint:**
  - Distribution and analytical computation of proprietary constituent weights require a commercial Index Data Redistribution / Analytical Usage License from NSE Indices Limited.
  - Public web scrapers or free public CSVs only provide unweighted membership or heavyweight summaries. Treating undisclosed weights as zero creates a false 65.39% off-benchmark active weight.

### 3.2 DEP-06: Security Total Return Daily Time-Series
- **Provider Candidates:**
  - NSE Data & Analytics Limited (Official Historical Equity EOD & Corporate Action Product).
  - CRISIL Market Intelligence & Analytics (Mutual Fund & Equity Valuation Engine).
  - Bloomberg / Refinitiv DataScope Select (Institutional Global Feeds).
  - CMIE ProwessIQ (Centre for Monitoring Indian Economy - Corporate & Market Database).
- **Required Product:** End-of-Day Adjusted Price & Total Return Series.
- **Data Attributes Required:**
  - Trading Date ($t$).
  - Primary Exchange ISIN and Trading Symbol.
  - Unadjusted Open, High, Low, Close, Volume.
  - Adjusted Close ($P_{\text{adj},t}$): Back-adjusted for all stock splits, consolidations, and bonus issues.
  - Total Return Factor ($TRF_t$): Incorporating ordinary and extraordinary cash dividend reinvestment.
  - Single-Day Total Return: $R_{i,t} = \frac{TRF_t}{TRF_{t-1}} - 1$.
- **Temporal Alignment Contract:**
  - Fund holdings valuation cutoff vs market close cutoff: Equity prices must reflect official NSE 3:30 PM IST closing prices matching mutual fund NAV determination dates.

### 3.3 DEP-07: Corporate Action Audit Trail
- **Provider:** NSE Data & Analytics / BSE India Corporate Action Feeds.
- **Data Attributes Required:**
  - Ex-Date, Record Date, Effective Date.
  - Corporate Action Type (`SPLIT`, `BONUS`, `RIGHTS`, `DIVIDEND`, `DEMERGER`, `SCHEME_OF_ARRANGEMENT`).
  - Adjustment Multiplier / Ratio (e.g. 10:1 split = factor 0.10).
  - Dividend Amount per Share (INR) and tax adjustment flag.
- **Epistemic Purpose:** Enables deterministic auditability so that when an ISIN migrates or an asset splits, the system can mathematically verify return continuity rather than hallucinating artificial volatility or portfolio exits.

---

## 4. Integration & Engineering Architecture (Once Licensed)

When institutional data feeds are licensed and onboarded, the following vertical slice will be unlocked:

1. **Database Schema Additions (Non-Destructive Flyway Migration):**
   - `security_eod_price`: Bitemporal daily trading records (`security_id`, `trade_date`, `close_price`, `adjusted_close`, `daily_total_return`, `source_artifact_id`).
   - `benchmark_constituent_weight_hist`: Full daily/monthly bitemporal constituent weights (`benchmark_id`, `security_id`, `effective_date`, `free_float_mcap`, `weight_pct`, `revision_seq`).
   - `corporate_action_event`: Auditable corporate action ledger with ratio adjustments and cash distributions.

2. **Quant-Engine Pure Vectorized Attribution Kernel:**
   - Vectorized Polars/NumPy Brinson-Fachler decomposition module (`quant-engine/src/attribution/brinson_fachler.py`).
   - Inputs: $w_{p,j,t}, w_{b,j,t}, R_{p,j,t}, R_{b,j,t}$.
   - Outputs: Allocation effect, Selection effect, Interaction effect, and Multi-period linking (Carino or Menchero geometric smoothing).
   - Zero LLM involvement; 100% deterministic arithmetic verified against CFA Institute GIPS standards.

3. **Backend Orchestrator Integration:**
   - Spring Boot calculation runner executing IPC calls to `quant-engine`.
   - Persistence of calculated attribution runs in `attribution_result` table with SHA-256 lineage to raw source files.

---

## 5. Cost & Commercial Acquisition Directives

1. **Strict Cost Incurrence Prohibition:** In accordance with project governance, no agent or engineer shall register for commercial subscriptions, enter into trial agreements requiring billing data, or incur financial obligations on behalf of the project.
2. **Readiness Gate Governance:**
   - The UI and API must explicitly report `brinsonAttributionStatus: "BLOCKED_PENDING_STOCK_RETURNS"`.
   - Any attempt to compute pseudo-attribution by substituting index returns for stock returns or estimating missing weights must be rejected as unverified methodology.
3. **Current Permissible Capabilities:**
   - Multi-period portfolio-versus-benchmark membership divergence ($\checkmark$ OPERATIONAL).
   - Disclosed heavyweight active position spreads ($\checkmark$ OPERATIONAL).
   - Point-in-time AMFI market-cap category shifts ($\checkmark$ OPERATIONAL).
   - Two-point benchmark index net reconstitution tracking ($\checkmark$ OPERATIONAL).
   - Clear attribution of apparent changes to Fund, Benchmark, or Coverage ($\checkmark$ OPERATIONAL).
