# PHASE 2F: REAL DATA INTEGRATION — REVISED IMPLEMENTATION PLAN

============================================================
GOVERNANCE STATUS
============================================================
PHASE 2F PLAN STATUS: READY FOR FINAL AUDIT
EXECUTION: NOT AUTHORIZED (PLAN ONLY — DO NOT IMPLEMENT)
YUKIRA EMPIRICAL FINDINGS: EXACTLY ZERO
YUKIRA APPROVED PRODUCTION METHODOLOGY: STRICTLY EMPTY

============================================================
1. OBJECTIVE & BOUNDARIES
============================================================
Design the first real, deterministic end-to-end YUKIRA data vertical slice.

Target pipeline:
REAL SOURCE (AMFI Historical Portal)
→ RAW ARTIFACT (`source_artifact`: Immutable payload blob + SHA-256)
→ DETERMINISTIC PARSING & NORMALIZATION
→ DETERMINISTIC VALIDATION & ANOMALY FLAGGING (`validation_issue`)
→ POINT-IN-TIME (PIT) / REVISION-AWARE OBSERVATION (`nav_observation`)
→ CALCULATION RUN (`calculation_run` + exact input mapping)
→ DETERMINISTIC QUANT ENGINE (`FASTAPI-QUANT`: `RET-02` Period Return)
→ METRIC RESULT (`metric_result`: Exact value + complete lineage)
→ BACKEND REST API
→ FRONTEND INTERFACE (Progressive disclosure + epistemic state)

Scope Boundary:
- Pilot Asset: Single parameterized Indian Mutual Fund Scheme Option (e.g., Growth option of an equity scheme).
- First Metric: `RET-02` (Simple Period Return).
- Strict Invariant: Do NOT attempt the full 30-metric production engine. Maintain strict, uncompromised separation between raw provenance, validated observations, and candidate calculations.

============================================================
2. CURRENT ARCHITECTURE BASELINE (COMMITTED HEAD: 92c5f25)
============================================================
The plan builds strictly upon the committed foundation:
1. Database Schema (Flyway V1–V5):
   - Reference masters: `amc`, `scheme`, `scheme_plan`, `scheme_option`, `market_calendar`.
   - Provenance: `data_source`, `source_artifact` (SHA-256 hash, byte size, raw payload blob).
   - Bitemporal ledger: `nav_observation` with bitemporal coordinates (`effective_date`, `availability_time`, `ingestion_time`), revision tracking (`revision_seq`, `is_latest_revision`), and multidimensional quality columns.
   - Execution manifest: `calculation_run`, `calculation_run_input_observation` (foreign key linkage to exact observations used), `metric_result` (JSONB diagnostics), and `validation_issue`.
2. Quant Engine:
   - FastAPI dispatching service (`quant-engine/src/api/dispatcher.py`) with pure-Python deterministic modules (`src/returns.py`, etc.).
3. Backend:
   - Spring Boot orchestrator (`CalculationOrchestratorService`), native SQL PIT revision queries, and REST controllers (`SchemeController`, `CalculationRunController`, `MetricResultController`).
4. Frontend:
   - Next.js App Router (`frontend/app`), epistemic badges (`DataQualityBadge`, `MethodologyBadge`, `ProvenanceCard`), and strict progressive disclosure.

============================================================
3. SEPARATION OF FACTUAL SOURCE AVAILABILITY FROM ANALYTICAL CUTOFF
============================================================
To prevent lookahead bias and epistemic conflation, YUKIRA formally distinguishes:

A. Source Valuation / Effective Date (`effective_date`, Date):
   - The economic portfolio valuation date reported by the source.

B. Source Publication / Availability Timestamp (`availability_time`, Timestamptz):
   - Factual source metadata representing the physical instant when the valuation was published and legally/publicly accessible to market participants.
   - FACTUAL STATUS IN AMFI HISTORICAL SOURCE: **UNKNOWN**.
   - AMFI historical batch files report only the valuation date (`Date`, e.g., `01-Jan-2024`). The upstream flat-text files do NOT provide the exact publication timestamp of historical NAVs.
   - NON-FABRICATION MANDATE: YUKIRA must NOT invent, guess, or fabricate a factual source availability timestamp (such as arbitrary 21:00 or 23:59 release timestamps). Factual availability metadata remains strictly unknown.

C. Ingestion Timestamp (`ingestion_time`, Timestamptz):
   - The exact system time when the YUKIRA database accepted and committed the record.

D. Analytical Knowledge Cutoff (`knowledge_cutoff_time`, Timestamptz):
   - A YUKIRA-selected analytical convention or simulation time barrier $T_{\text{cutoff}}$ beyond which no data may be read by a calculation run.
   - HANDLING HISTORICAL BACKFILLS:
     - For historical archives where factual publication time is unrecorded by the source, the system must NOT fabricate source availability.
     - Instead, an explicit **analytical/backfill modeling convention** is applied:
       `CONVENTION_EOD_HISTORICAL_CUTOFF`: Defines the analytical knowledge boundary as end-of-effective-date in India Standard Time (`effective_date` 23:59:59.999+05:30).
     - CRITICAL RULE: This analytical cutoff is explicitly documented as a **modeling convention** and must CANNOT be interpreted as evidence of AMFI factual publication time.
     - In the data ledger, this provenance condition is captured via separate provenance metadata (`temporal_status = 'HISTORICAL_BACKFILL'`) and logged in `validation_issue` (`check_code = 'TEMPORAL_AVAILABILITY_UNRECORDED'`), ensuring the analytical convention is programmatically inspectable.

============================================================
4. APPROVED SIX-DIMENSIONAL DATA QUALITY TAXONOMY
============================================================
The six-dimensional data quality model approved in Phase 2C is strictly preserved. Temporal uncertainty and validation status are decoupled from value validity:

1. Quality Assessment (Validity of the numeric value):
   - `VALID`: Passed all range, format, and mathematical sanity checks.
   - `INVALID`: Mathematically or logically corrupt (e.g. NAV <= 0, non-numeric).
   - `SUSPICIOUS`: Statistical or continuity anomaly (e.g., candidate >20% single-day NAV jump) flagged for analyst review but computable.

2. Verification (Validation pipeline progress):
   - `UNVERIFIED`: Stored upon ingestion; pending automated validation rules or cross-source reconciliation.
   - `VERIFIED`: Automated verification rules executed and passed.
   *(Rule: Unknown source publication time does NOT force `verification_status = UNVERIFIED` if the NAV value itself has passed automated verification rules. Quality and Verification apply to the financial data point).*

3. Revision (Source restatement history):
   - `ORIGINAL`: Initial published observation from source.
   - `REVISED`: Restatement delivered by source with altered NAV value. (A revised observation may be the authoritative truth).
   - `SUPERSEDED`: A prior observation superseded by a newer revision sequence for the same effective date.

4. Freshness (Delivery timeliness):
   - `CURRENT`: Delivered within expected regulatory calendar schedule.
   - `STALE`: Expected periodic update delayed past regulatory deadline.

5. Presence (Presence in time series):
   - `AVAILABLE`: Data point exists in ledger.
   - `MISSING`: Explicitly identified calendar gap where data was expected on a trading day.
   - `NOT_APPLICABLE`: Non-trading day (weekend/exchange holiday).

6. Integrity (Relationship & duplicate conditions):
   - `DUPLICATE`: Multiple identical payloads received for the same logical observation key.
   - `CONFLICTING`: Divergent values received from two reputable sources or contradictory rows within a single artifact.

*Implementation & Storage Note:*
The physical columns in `nav_observation` map directly to these dimensions (`quality_assessment`, `verification_status`, `revision_status`, `temporal_status` / Freshness, `presence_status`). The sixth dimension (Integrity: `DUPLICATE`, `CONFLICTING`) and supplementary provenance indicators (such as `temporal_status = 'HISTORICAL_BACKFILL'`) are tracked via dedicated `validation_issue` records and relational constraints.

Non-Penalty Mandate:
Data quality dimensions indicate evidence reliability and triage state. They must NEVER be used to define automated investment score deductions, quantitative model penalties, or autonomous BUY/HOLD/REDUCE/AVOID decisions.

============================================================
5. AMFI SOURCE CONTRACT SPECIFICATION
============================================================
Selected Primary Source: Association of Mutual Funds in India (AMFI)

- Authority: Self-regulatory organization under SEBI regulations; statutory portal for Indian mutual fund NAVs.
- Exact Endpoint:
  - Base URL: `https://portal.amfiindia.com/DownloadNAVHistoryReport_Po.aspx`
  - Query Parameters: `?mf={amc_code}&tp=1&frmdt={dd-MMM-yyyy}&todt={dd-MMM-yyyy}` or scheme-specific `?scheme={amfi_code}&frmdt={dd-MMM-yyyy}&todt={dd-MMM-yyyy}`.
- Protocol & Transport: HTTPS GET, TLS 1.2+, standard HTTP headers.
- Payload Format: Flat semicolon-delimited text (`text/plain`).
- Encoding: Windows-1252 / ISO-8859-1 (fallback to UTF-8).
- Delimiter: Semicolon (`;`).
- Header Record Format:
  `Scheme Code;Scheme Name;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Net Asset Value;Repurchase Price;Sale Price;Date`
- Field Definitions:
  - Field 0 (`Scheme Code`): AMFI unique scheme identifier (numeric string, e.g., `119062`).
  - Field 1 (`Scheme Name`): Full text name of the fund option (free text).
  - Field 2 (`ISIN Div Payout/ ISIN Growth`): ISIN for Growth or Dividend Payout (e.g., `INF179K01BE2`).
  - Field 3 (`ISIN Div Reinvestment`): ISIN for Reinvestment option.
  - Field 4 (`Net Asset Value`): Quoted NAV (decimal string, e.g., `1234.5678`).
  - Field 7 (`Date`): Valuation date in `DD-Mon-YYYY` format (e.g., `01-Jan-2024`, case-insensitive month abbreviation).
- Historical Coverage: Daily valuations available from April 2006 to present.
- Retrieval Behavior & Provenance:
  - Downloader captures exact response bytes.
  - Generates SHA-256 hash across full uncompressed payload.
  - Persists full byte stream to `source_artifact.payload_blob`.
  - Captures HTTP metadata: URL, status code, Content-Type, Content-Length, Last-Modified, and system fetch timestamp.
- Known Revision Behavior:
  - AMFI historical portal does NOT publish an explicit revision history or diff ledger. If an AMC revises historical NAVs, the download portal quietly reflects the corrected number.
  - YUKIRA detects revisions by matching retrieved rows against historical `source_artifact` snapshots and incrementing `revision_seq`.
- Licensing & Terms Considerations:
  - Publicly accessible data provided for investor transparency.
  - Redistribution terms: Internal analysis and display permitted; non-commercial reproduction. Rate limits: polite crawling (crawl delay >= 2000ms; exponential backoff on HTTP 429/503).
- Known Limitations:
  - Semicolon structure contains blank lines and AMC category banner lines without delimiter tokens.
  - String formats may have non-breaking spaces or trailing whitespace.
  - Historical dates occasionally use irregular uppercase month strings (e.g. `01-JAN-2024`).

============================================================
6. PILOT SCHEME PARAMETERIZATION & IDENTITY RESOLUTION
============================================================
Strict Rule: NO hardcoding of scheme identifiers (`INF179K01BE2` or `119062`) in ingestion services, parsers, or calculation engines.

Identity-Resolution Flow:
```text
[Raw Source Row: Scheme Code / ISIN]
  ↓
[Identifier Extractor]
  ↓
[Security / Scheme Lookup Service]
  ↓ Match against database:
  - scheme_option.amfi_code = :schemeCode
  OR
  - scheme_option.isin = :isin
  ↓
[Resolved Canonical SchemeOption ID (Surrogate PK)]
  ↓
[Ingestion & Bitemporal Linking]
```

Pilot Input Configuration:
- For automated testing and verification of the vertical slice, a pilot configuration fixture is provided via configuration or test harness:
  - `pilot.scheme_option_id`: Resolved dynamically from seed master or passed via CLI/REST parameter.
  - Test Target Candidate: Direct Plan Growth Option of an established Equity Fund (e.g. HDFC Flexi Cap Direct Growth).
- If an unmapped scheme code is encountered:
  - Ingestion does NOT abort or crash.
  - Emits a diagnostic warning, stages raw record in `validation_issue` as `UNMAPPED_SOURCE_SCHEME`, and continues processing remaining rows.

============================================================
7. BITEMPORAL REVISION RESOLUTION & PIT SELECTION ALGORITHM
============================================================
Principle: "Bitemporal storage enables PIT reconstruction; it does not by itself prevent look-ahead bias."

`revision_seq` is an internal lineage sequence identifier representing chronological capture order. It must NOT by itself serve as the authoritative PIT-selection rule.

Authoritative Conceptual PIT-Selection Algorithm:
1. Identify Logical Observation:
   - Identify the target entity and market date: `(scheme_option_id, effective_date)`.
2. Gather Immutable Revisions:
   - Retrieve all immutable revision records $R = \{r_1, r_2, \dots, r_m\}$ stored for that logical observation.
3. Apply Knowledge Cutoff Eligibility Filter:
   - Filter $R$ to the eligible subset known as of the analytical cutoff:
     $$R_{\text{eligible}} = \{ r \in R \mid r.\text{availability\_time} \le T_{\text{cutoff}} \}$$
   - Any revision published after $T_{\text{cutoff}}$ is strictly excluded, preventing lookahead bias.
4. Determine Authoritative Version Among Eligible Revisions:
   - Among $R_{\text{eligible}}$, determine the authoritative revision based on source availability and documented authority semantics:
     - The authoritative revision is the latest legally effective restatement known to have been available at $T_{\text{cutoff}}$.
     - Lineage ordering (`revision_seq`) identifies supersession order established by the source.
5. Deterministic Tie-Breaking & Ambiguity Resolution:
   - If two eligible revisions have identical availability timestamps but conflicting values:
     - Ingestion/audit flags an Integrity condition (`CONFLICTING`) in `validation_issue`.
     - Deterministic rule: The system selects the revision tied to the most authoritative source artifact (higher `source_artifact_id` or explicit source precedence hierarchy).
6. Unresolvable Authority / Ambiguity State:
   - If authoritative availability cannot be established (e.g., conflicting dates with unrecorded availability), the system must NOT invent certainty or choose an arbitrary value.
   - The query returns an explicit data-quality ambiguity flag (`quality_assessment = 'SUSPICIOUS'`, `integrity = 'CONFLICTING'`), halting deterministic calculation with an audit diagnostic.

Native SQL Implementation Contract:
```sql
SELECT DISTINCT ON (n.effective_date) n.*
FROM nav_observation n
WHERE n.scheme_option_id = :schemeOptionId
  AND n.effective_date <= :asOfDate
  AND n.availability_time <= :knowledgeCutoffTime
ORDER BY n.effective_date ASC, n.availability_time DESC, n.revision_seq DESC;
```
*Note:* Sorting by `n.availability_time DESC` ensures that the latest revision available prior to the cutoff is selected, with `revision_seq DESC` acting strictly as a deterministic tie-breaker among revisions available at the same instant.

============================================================
8. DETERMINISTIC NAV VALIDATION CONTRACT
============================================================
Every parsed observation must be evaluated against deterministic validation checks.
CRITICAL MANDATE: Validation must NEVER silently delete source evidence or substitute zero for missing/invalid values.

Explicit Validation Matrix:

1. Malformed Row Check:
   - Condition: Line contains fewer required tokens or cannot be delimited.
   - Outcome: Hard invalidation. Row rejected from `nav_observation`. Staged to `validation_issue` (`target_entity_type = 'SOURCE_ARTIFACT'`, `check_code = 'MALFORMED_ROW'`).

2. Numeric Parsing & Boundary Check:
   - Condition: NAV cannot be parsed to finite decimal, OR $\text{NAV} \le 0.0001$.
   - Outcome: Hard invalidation. Row rejected from computable ledger. Logged in `validation_issue` (`check_code = 'INVALID_NUMERIC_NAV'`, `quality_assessment = 'INVALID'`).

3. Date Parsing & Calendar Validity Check:
   - Condition: Date token cannot be parsed into a valid Gregorian calendar date.
   - Outcome: Hard invalidation. Row rejected. Logged in `validation_issue` (`check_code = 'INVALID_DATE'`).

4. Duplicate Observations (Within Same Artifact):
   - Condition: Exact duplicate `(scheme_option_id, effective_date, nav_value)` in single file.
   - Outcome: Warning / Integrity state. First instance retained; duplicate skipped. Logged in `validation_issue` (`check_code = 'DUPLICATE_ROW_IN_ARTIFACT'`, `integrity_condition = 'DUPLICATE'`).

5. Conflicting Duplicate (Within Same Artifact):
   - Condition: Multiple records for same scheme and date with differing NAV in single file.
   - Outcome: Marked `SUSPICIOUS`. Both staged for review; logged in `validation_issue` (`check_code = 'CONFLICTING_ROWS_IN_ARTIFACT'`, `quality_assessment = 'SUSPICIOUS'`, `integrity_condition = 'CONFLICTING'`).

6. Extreme Single-Day Jump Check (Candidate Rule):
   - Condition: Single-day simple return $|(NAV_t / NAV_{t-1}) - 1| > 0.20$ (20% change without split).
   - Outcome: Warning / Quality State. Record IS persisted in `nav_observation` with `quality_assessment = 'SUSPICIOUS'`. Logged in `validation_issue` (`check_code = 'CANDIDATE_SUSPICIOUS_NAV_JUMP'`).
   - Classification: CANDIDATE data-hygiene filter (NOT approved investment methodology).

7. Date Ordering & Monotonicity Check:
   - Condition: Chronological sorting must be strictly monotonic ($Date_t > Date_{t-1}$).
   - Outcome: Automated sorting during staging.

8. Trading Day Gap Check:
   - Condition: Missing observation on a date marked as `is_trading_day = TRUE` in `market_calendar`.
   - Outcome: Warning / Completeness state. Logged in `validation_issue` (`check_code = 'MISSING_TRADING_DAY_OBSERVATION'`, `presence_status = 'MISSING'`).

============================================================
9. RET-02 (SIMPLE PERIOD RETURN) METHODOLOGY SPECIFICATION
============================================================
Chosen Metric for First Vertical Slice: `RET-02` (Simple Period Return)

Mathematical Definition:
$$R = \frac{\text{NAV}_{\text{end}}}{\text{NAV}_{\text{start}}} - 1$$

Observation Selection Contract:
1. Input Parameters:
   - `requested_start_date` ($D_{\text{start}}$)
   - `requested_end_date` ($D_{\text{end}}$)
   - `scheme_option_id`
   - `knowledge_cutoff_time` ($T_{\text{cutoff}}$)
2. Selection Rules:
   - The period return is **observation-date based**, anchored to requested boundary dates.
   - Start NAV Selection ($\text{NAV}_{\text{start}}$):
     - Query authoritative observation at or immediately preceding $D_{\text{start}}$:
       $\max(\text{effective\_date}) \le D_{\text{start}}$ subject to $\text{availability\_time} \le T_{\text{cutoff}}$.
     - Candidate Tolerance Lookback Window: If the exact requested start date falls on a weekend or holiday, select the closest preceding trading day within a maximum 4-calendar-day lookback window. If no valid observation exists within 4 days, abort with status `INSUFFICIENT_DATA`.
     - GOVERNANCE STATUS OF 4-DAY LOOKBACK: **CANDIDATE**. This period-selection rule is versioned under `methodology_version` (tag `CANDIDATE_V1`) and remains strictly unapproved candidate methodology.
   - End NAV Selection ($\text{NAV}_{\text{end}}$):
     - Query authoritative observation at or immediately preceding $D_{\text{end}}$:
       $\max(\text{effective\_date}) \le D_{\text{end}}$ subject to $\text{availability\_time} \le T_{\text{cutoff}}$.
     - Same candidate 4-day preceding lookback applies if $D_{\text{end}}$ is a non-trading day.
   - Sequence Invariant:
     - Effective date of $\text{NAV}_{\text{end}}$ MUST be strictly greater than effective date of $\text{NAV}_{\text{start}}$.
   - Minimum Evidence Requirement:
     - Exactly two valid, non-zero, positive NAV observations required.
3. Quant Engine Module:
   - Executed via `src/returns.py:period_return(start_val, end_val)`.
   - Zero annualization applied.
   - Zero risk-free rate required.
   - Zero benchmark series required.
   - Zero scoring formulas or investment rating rules introduced.

============================================================
10. REPRODUCIBILITY & CALCULATION RUN FLOW
============================================================
Every metric result persisted in YUKIRA must be bit-for-bit reproducible.

Reproducibility Manifest:
A calculation run is fully reproducible if and only if the following tuple is recorded:
1. `source_artifact.sha256_hash` (Verbatim provenance of raw input data).
2. `calculation_run_input_observation` (Exact foreign key IDs and revision sequences of observations passed).
3. `calculation_run.input_snapshot_sha256` (Cryptographic hash of the exact series passed to the engine).
4. `calculation_run.knowledge_cutoff_time` (Temporal knowledge barrier applied).
5. `calculation_run.as_of_date` (Valuation horizon).
6. `methodology_version.version_tag` and `methodology_version.git_commit_hash`.
7. `calculation_run.engine_software_version` (Quant Engine release/commit tag).
8. `metric_result.diagnostics` (JSONB recording exact boundary dates, input counts, and candidate convention disclosures).

Step-by-Step Execution Sequence:
1. Orchestrator receives calculation request for Scheme Option and date range.
2. PIT resolution query fetches eligible `nav_observation` records.
3. Compute SHA-256 digest across formatted input series.
4. Insert `calculation_run` in status `RUNNING`.
5. Insert `calculation_run_input_observation` rows linking the run to each observation ID.
6. Dispatch HTTP POST to Quant Engine `/calculate` endpoint.
7. Quant Engine executes `period_return(start_val, end_val)`.
8. Persist `metric_result` record with numeric value, units (`PERCENTAGE`), status (`CALCULATED`), and diagnostics.
9. Update `calculation_run` status to `COMPLETED` with completion timestamp.

============================================================
11. REST API & FRONTEND DISPLAY CONTRACT
============================================================
API Endpoints:
- `POST /api/ingest/amfi/nav`: Internal/Admin ingestion trigger.
  - Request: `{ "sourceArtifactId": 123 }` OR `{ "amfiCode": "119062", "startDate": "2024-01-01", "endDate": "2024-03-31" }`.
  - Response: `{ "status": "COMPLETED", "observationsIngested": 62, "revisionsCreated": 0, "validationIssues": 0 }`.
- `GET /api/schemes/{id}`: Returns scheme master and options.
- `POST /api/calculations/run`: Triggers calculation run.
- `GET /api/metrics/run/{id}`: Retrieves calculated `metric_result` list.

Frontend Display Requirements:
- Target Route: `/funds/[id]` and `/analysis/[id]`.
- Components Displaying Real Result:
  - Canonical Metadata: Scheme Name, Direct Plan, Growth Option, ISIN, AMFI Code.
  - `MetricValueDisplay`: Renders `RET-02` (+X.XX%) with start date, end date, and observation count.
  - `DataQualityBadge`: Displays multidimensional quality summary (e.g. `VERIFIED`, `ORIGINAL`, `CURRENT`).
  - `MethodologyBadge`: Displays `CANDIDATE` (amber) with explicit disclosure: "Zero Approved Production Methodology".
  - `ProvenanceCard`: Displays `source_artifact` SHA-256 hash, retrieval timestamp, and calculation run UUID.
- Strict Invariant: Zero recommendation labels (BUY/SELL/HOLD), zero stars, zero fabricated metrics.

============================================================
12. FAILURE MODES & RESILIENT BEHAVIOR
============================================================
1. AMFI HTTP Connection Failure / Portal Outage:
   - Abort ingestion; log network error; leave existing database state intact.
2. Incomplete Time Series / Missing Boundary Date:
   - Quant Engine returns `INSUFFICIENT_DATA`; orchestrator persists `metric_result` with status `INSUFFICIENT_DATA` and diagnostic explanation.
3. Corrupt Raw Payload:
   - Parser throws `ParseException`; database transaction rolls back; raw artifact retained with status `PARSE_FAILED`.
4. Discrepancy Across Subsequent Downloads (Restatement):
   - Ingestion appends `revision_seq + 1`; logs `validation_issue`; does not delete previous observation.
5. Quant Engine Crash / Timeout:
   - Orchestrator catches exception; sets `calculation_run.run_status = 'FAILED'`; records stack trace/message.

============================================================
13. SECURITY, LICENSING & RATE LIMITING
============================================================
- Public Data Compliance: AMFI publishes data for public investor dissemination. Commercial redistribution as an original data feed is prohibited; internal computation and analytical display comply with fair use.
- Rate Limiting: Mandatory delay of $\ge 2000\text{ ms}$ between automated requests to AMFI portal.
- Raw Storage Protection: Raw blobs stored in database or secure local storage; zero credentials required for AMFI public access.

============================================================
14. TESTING STRATEGY
============================================================
1. Unit Tests (Backend Java):
   - `AmfiNavParserTest`: Verifies parsing of realistic AMFI semicolon text, handling blank lines, irregular month capitalization, and trailing whitespace.
   - `NavObservationIngestionServiceTest`: Verifies deduplication, revision sequence increments on differing values, and validation issue persistence.
   - `NavObservationPitResolutionTest`: Verifies PIT cutoff query filters out revisions published after knowledge cutoff and tests tie-breaking semantics.
2. Unit Tests (Quant Engine Python):
   - Test `period_return` with two-point real observation series.
   - Test invalid boundaries (negative NAV, zero NAV, end_date <= start_date).
3. Integration Tests:
   - `RealDataVerticalSliceIT`: Loads sample AMFI text file, executes ingestion, triggers `CalculationOrchestratorService`, calls Quant Engine, and verifies exact computed return against hand calculation.
4. Frontend Component Tests:
   - Verify `MetricValueDisplay`, `ProvenanceCard`, and `MethodologyBadge` render real calculation results and epistemic disclosures correctly.

============================================================
15. IMPLEMENTATION SEQUENCE
============================================================
Phase 2F will proceed in 4 strictly sequential steps (when authorized):
- Step 1: Ingestion Parser & Provenance (Java `AmfiNavParser`, `SourceArtifactService`).
- Step 2: Bitemporal Ledger Ingestion & Deterministic Validation (Java `NavObservationIngestionService`, validation rules).
- Step 3: End-to-End Orchestrator Integration & Quant Engine Calculation (`CalculationOrchestratorService` executing `RET-02`).
- Step 4: Frontend Presentation & Epistemic Audit (Connecting `/funds/[id]` to display the verified slice).

============================================================
16. TAXONOMY CLASSIFICATION
============================================================
- Database Schema (V1–V5): **APPROVED ARCHITECTURE**
- Six-Dimensional Data Quality Model: **APPROVED ARCHITECTURE**
- Bitemporal PIT Selection Query: **APPROVED ARCHITECTURE**
- Quant Engine Dispatcher: **APPROVED ARCHITECTURE**
- AMFI Semicolon Parser: **IMPLEMENTATION DETAIL**
- Ingestion Deduplication: **IMPLEMENTATION DETAIL**
- Observation Selection Lookback (4-Day Window): **CANDIDATE**
- 20% NAV Jump Validation Rule: **CANDIDATE**
- EOD Availability Analytical Convention for Backfill: **CANDIDATE**
- Benchmark Observation Ingestion: **DEFERRED**
- Investment Scoring / Recommendation Engine: **STRICTLY FORBIDDEN**

============================================================
17. WORKING TREE & AUDIT STATUS
============================================================
Working tree audit:
- Tracked files modified: 0 (No tracked source modifications).
- Untracked files: Exactly 1 (`phase2f_real_data_integration_plan.md`).
- HEAD commit: `92c5f25` (Clean working tree relative to Git index).

============================================================
18. FINAL STATUS
============================================================
PHASE 2F PLAN STATUS:
READY FOR FINAL AUDIT

EXECUTION:
NOT AUTHORIZED (PLAN ONLY — ZERO CODE MODIFIED)

YUKIRA EMPIRICAL FINDINGS:
EXACTLY ZERO

YUKIRA APPROVED PRODUCTION METHODOLOGY:
STRICTLY EMPTY
