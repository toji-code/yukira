# YUKIRA Database Architecture Specification

**Document ID:** 005  
**Version:** 0.1  
**Status:** Foundation Draft  
**Project:** YUKIRA  
**Database:** PostgreSQL  
**Primary Focus:** Indian Mutual Funds  

---

## 1. Purpose

This document defines the database architecture and persistence strategy for YUKIRA.

The database must provide a reliable, auditable, reproducible, and extensible foundation for investment intelligence.

It must support:

- Raw financial data storage
- Validated financial data
- Historical time-series data
- Fund and scheme metadata
- Portfolio holdings
- Benchmark data
- Market and macroeconomic data
- Quantitative calculations
- Risk and factor analysis
- Investment scores
- Evidence and provenance
- Methodology versioning
- Analysis results
- Investor-specific analysis
- Auditability and reproducibility

The database is a system of record for YUKIRA's analytical inputs and persisted analytical outputs.

It must not become a dumping ground for calculations that should instead be performed by deterministic analytical services.

---

## 2. Database Principles

The YUKIRA database follows these principles:

1. **Data integrity before convenience**
2. **Historical correctness before simplicity**
3. **Point-in-time correctness**
4. **Traceability of important data**
5. **Explicit provenance**
6. **Versioned methodology**
7. **Deterministic analytical inputs**
8. **Separation of raw, validated, and derived data**
9. **Controlled schema evolution**
10. **Avoidance of unnecessary duplication**
11. **Extensibility without premature complexity**
12. **Auditability of important investment outputs**

The database architecture must support the broader YUKIRA principles of evidence over opinion, probability over prediction, and transparency over marketing.

---

## 3. Database Responsibilities

The database is responsible for persisting:

- Entity definitions
- Financial observations
- Historical observations
- Portfolio composition
- Benchmark information
- Data-source metadata
- Data-quality status
- Analytical inputs
- Analytical outputs
- Methodology versions
- Analysis runs
- Evidence references
- Investor analysis context

The database is not responsible for:

- Complex quantitative calculations
- Machine-learning inference
- Natural-language generation
- Final report composition
- External data retrieval
- Business-process orchestration

Those responsibilities belong to application and analytical services.

---

## 4. Database Technology

The MVP database will use PostgreSQL.

PostgreSQL is selected because it provides:

- Strong relational integrity
- Mature transaction support
- Powerful indexing
- Time-series-friendly relational structures
- JSON/JSONB support where appropriate
- Reliable constraints
- Mature migration tooling
- Strong ecosystem support
- Suitable scalability for the MVP and foreseeable growth

YUKIRA should use standard PostgreSQL capabilities before introducing specialized database infrastructure.

---

## 5. Logical Data Architecture

The logical database architecture is divided into several conceptual layers:

```text
                DATA SOURCES
                     |
                     v
              RAW DATA LAYER
                     |
                     v
            VALIDATED DATA LAYER
                     |
          +----------+----------+
          |                     |
          v                     v
    REFERENCE DATA        TIME-SERIES DATA
          |                     |
          +----------+----------+
                     |
                     v
             ANALYTICAL INPUTS
                     |
                     v
             ANALYTICAL OUTPUTS
                     |
                     v
          DECISION / REPORT DATA
```

The database should preserve sufficient information to reconstruct how an important analytical result was produced.

---

## 6. Core Entity Groups

The initial database model will contain the following conceptual entity groups:

### Reference Entities

- Fund House
- Mutual Fund Scheme
- Plan
- Option
- Benchmark
- Asset
- Security
- Sector
- Category
- Data Source

### Financial Data

- NAV observations
- Return observations
- Benchmark observations
- Portfolio holdings
- Asset prices
- Corporate actions where required
- Distribution/dividend observations where required

### Data Governance

- Data ingestion records
- Data validation records
- Data-quality flags
- Source provenance
- Data timestamps

### Analysis

- Analysis run
- Metric definition
- Metric observation
- Score
- Decision
- Scenario
- Forecast
- Evidence
- Investment thesis

### Investor Context

- Investor profile
- Investment objective
- Risk constraints
- Time horizon
- Investor-fit assessment

### System Metadata

- Methodology version
- Model version
- Application version
- Configuration version
- Audit records

---

## 7. Entity Identity and Master Data

YUKIRA must maintain canonical master records for important investment entities.

Master data should provide stable internal identifiers independent of external data-provider identifiers.

Each important entity should have:

- Internal primary key
- Canonical name
- Entity type
- Status
- Creation timestamp
- Last-update timestamp
- External identifiers where applicable

External identifiers must not be used as the sole internal primary key because providers may change identifiers, merge records, discontinue identifiers, or use different identifier systems.

YUKIRA should maintain mappings between internal entities and external identifiers.

---

## 8. Mutual Fund House

The `fund_house` entity represents an Asset Management Company or equivalent fund-management organization.

Conceptual attributes include:

- Internal fund-house ID
- Legal/display name
- Short name
- Regulatory identifiers where applicable
- Website/reference information where appropriate
- Status
- Effective-from date
- Effective-to date where applicable
- Created timestamp
- Updated timestamp

A fund house may manage multiple schemes.

Historical relationships should be preserved where ownership, management, or organizational structure changes materially.

---

## 9. Mutual Fund Scheme

The `scheme` entity represents the investment scheme itself.

A scheme is distinct from its individual plans and options.

Conceptual attributes include:

- Internal scheme ID
- Scheme name
- Fund-house ID
- Scheme category
- Investment objective
- Scheme type
- Asset-class classification
- Launch date
- Closure date where applicable
- Status
- Regulatory identifiers
- Primary benchmark reference
- Secondary benchmark references where applicable
- Created timestamp
- Updated timestamp

The scheme entity must support historical changes to important attributes.

A change in name, category, benchmark, or other material classification must not silently overwrite historical information that could affect past analysis.

---

## 10. Plan and Option Structure

YUKIRA must distinguish between:

- Scheme
- Plan
- Option

For example, a scheme may contain:

```text
Scheme
├── Direct Plan
│   ├── Growth Option
│   └── IDCW Option
│
└── Regular Plan
    ├── Growth Option
    └── IDCW Option
```

The exact structure may vary by scheme.

The database must therefore avoid assuming that every scheme has the same plan/option structure.

Plan-level and option-level data must remain separately identifiable where their financial characteristics differ.

Examples of characteristics that may differ include:

- NAV
- Expense ratio
- Returns
- Distributions
- Portfolio data
- Inception date
- Investor eligibility

---

## 11. Security and Asset Master Data

YUKIRA must maintain a canonical representation of investable securities and other relevant assets.

The `security` entity may represent:

- Equity
- Debt security
- Government security
- Money-market instrument
- ETF
- REIT/InvIT
- Cash or cash-equivalent instrument
- Other eligible instruments

Conceptual attributes include:

- Internal security ID
- Security name
- Security type
- Issuer
- ISIN where applicable
- Exchange identifiers where applicable
- Currency
- Country/market
- Sector classification where applicable
- Industry classification where applicable
- Status
- Effective dates

External identifiers such as ISIN should be stored as identifiers, not treated as universal internal primary keys.

YUKIRA must allow multiple identifiers for the same security where necessary.

---

## 12. Benchmark Master Data

Benchmarks must be represented as first-class entities.

A benchmark record should contain:

- Internal benchmark ID
- Benchmark name
- Provider/index administrator
- Benchmark type
- Asset-class classification
- Currency
- Return methodology where known
- Total-return or price-return designation
- Effective-from date
- Effective-to date where applicable
- External identifiers
- Status

Benchmark changes are analytically significant.

YUKIRA must preserve historical benchmark relationships so that an analysis of a historical period uses the benchmark applicable to that period rather than automatically applying the current benchmark retrospectively.

Where benchmark methodology changes materially, the change should be recorded and made available to the analytical layer.

---

## 13. Time-Series Data Architecture

YUKIRA will contain substantial time-series data.

Time-series records should generally contain:

- Entity identifier
- Observation date
- Value
- Unit
- Currency where applicable
- Source identifier
- Data-vintage or retrieval timestamp where required
- Validation status
- Created timestamp

Observation date and ingestion/retrieval timestamp must be treated as different concepts.

For example:

```text
Observation Date: 2026-03-31
Retrieved At:    2026-04-02 10:15:00
```

The observation describes when the financial event or measurement occurred.

The retrieval timestamp describes when YUKIRA obtained the information.

This distinction is important for point-in-time analysis and prevention of look-ahead bias.

---

## 14. NAV Data

NAV observations should be stored at the appropriate plan/option level.

A NAV observation should conceptually contain:

- Plan/option ID
- NAV date
- NAV value
- Currency
- Source
- Source observation identifier where available
- Retrieved timestamp
- Validation status

NAV data must preserve the original observation rather than storing only recalculated values.

The database should support:

- Daily NAV observations
- Missing-date detection
- Duplicate detection
- Restatement handling
- Source comparison
- Historical retrieval

NAV records must not be silently overwritten when a source subsequently changes a historical value.

Where a correction occurs, the system should preserve sufficient provenance to determine what changed and when.

---

## 15. Return Data

Returns should generally be treated as derived analytical data rather than primary source data.

The database may persist calculated returns for performance and reproducibility, but every persisted return must identify:

- Security/fund identifier
- Start date
- End date
- Return methodology
- Input-data version
- Calculation methodology version
- Calculation timestamp
- Result
- Quality/status information

Examples of return methodologies include:

- Absolute return
- CAGR
- Annualized return
- Total return
- Rolling return
- Period return

A return value without its methodology and observation period is not considered sufficiently defined for institutional analysis.

The quant engine remains the authoritative component for calculating returns.

---

## 16. Portfolio Holdings

Portfolio holdings represent the composition of a fund at a particular observation date.

A holding record should conceptually contain:

- Scheme/plan/option ID
- Security ID
- Holding date
- Quantity where available
- Market value where available
- Portfolio weight
- Currency
- Source
- Retrieved timestamp
- Validation status

Additional fields may be introduced where required by source data.

The database must distinguish between:

- Reported holdings
- Normalized holdings
- Derived portfolio weights
- Analytical classifications

Reported source values must not be overwritten by normalized or derived values.

Historical holdings must remain queryable so that portfolio construction and concentration analysis can be reconstructed for a historical date.

---

## 17. Data Source and Provenance

Every material external dataset should have an associated source record.

The `data_source` entity should capture information such as:

- Source ID
- Source name
- Provider
- Source type
- Data domain
- URL/reference where appropriate
- Collection method
- Reliability classification
- Active/inactive status
- Terms or access restrictions where relevant

Material observations should be traceable to their source.

The provenance chain should conceptually support:

```text
Source
  ↓
Ingestion Record
  ↓
Raw Observation
  ↓
Validation
  ↓
Normalized Observation
  ↓
Analytical Input
  ↓
Calculation
  ↓
Investment Output
```

This chain is a core requirement for YUKIRA's auditability.

---

## 18. Data Ingestion and Validation Records

YUKIRA should maintain records describing ingestion operations.

An ingestion record may contain:

- Ingestion ID
- Source ID
- Dataset type
- Start time
- Completion time
- Status
- Number of records received
- Number of records accepted
- Number of records rejected
- Number of records quarantined
- Source version or file identifier where available
- Error summary
- Application version

Validation records should capture important quality checks.

Examples include:

- Schema validation
- Required-field validation
- Type validation
- Range validation
- Duplicate detection
- Referential-integrity validation
- Date consistency
- Historical continuity
- Cross-source reconciliation

Records failing critical validation should not silently enter the trusted analytical dataset.

They should either be rejected or placed into a quarantine state for investigation.

---

## 19. Data Quality Architecture

Data quality is a first-class concern in YUKIRA.

The database must distinguish between data that is:

- Trusted
- Validated
- Unvalidated
- Suspect
- Rejected
- Quarantined
- Stale
- Missing

A data-quality status must not be treated as a cosmetic field.

Analytical services must be able to determine whether a dataset is eligible for use.

Important quality dimensions include:

- Completeness
- Accuracy
- Consistency
- Timeliness
- Uniqueness
- Referential integrity
- Historical continuity
- Source reliability

Critical quality failures must prevent affected observations from silently being used in investment analysis.

---

## 20. Historical Data and Versioning

YUKIRA must preserve historical information required to reproduce prior analysis.

Current values must not automatically replace historically applicable values when doing so would change the historical information available at the time.

Versioning may be required for:

- Fund classifications
- Benchmarks
- Portfolio holdings
- Security classifications
- Expense ratios
- Scheme attributes
- Methodology
- Data corrections

Where appropriate, temporal validity should be represented using:

- Effective-from date
- Effective-to date
- Recorded-at timestamp

The distinction between **when a value was applicable** and **when YUKIRA learned about that value** must be preserved where point-in-time analysis requires it.

---

## 21. Analysis Run

Every material YUKIRA analysis should have a corresponding `analysis_run` record.

An analysis run identifies a specific execution of the analytical pipeline.

Conceptual attributes include:

- Analysis run ID
- Subject entity
- Analysis type
- Analysis date
- Input-data version
- Methodology version
- Configuration version
- Application/code version
- Model version where applicable
- Start timestamp
- Completion timestamp
- Status
- Error information where applicable

An analysis run should make it possible to determine what version of the analytical system produced an output.

---

## 22. Metric Definition

Metrics must be represented independently from individual metric observations.

A `metric_definition` record should describe:

- Metric ID
- Metric name
- Description
- Analytical dimension
- Metric category
- Formula or calculation definition
- Required inputs
- Unit
- Directionality where applicable
- Minimum data requirements
- Validity rules
- Methodology version
- Status

Examples include:

- CAGR
- Rolling return
- Maximum drawdown
- Volatility
- Sharpe ratio
- Sortino ratio
- Concentration
- Active share
- Expense ratio

The database must not rely on metric names alone to define methodology.

A methodology version must identify the precise calculation rules applicable to a metric.

---

## 23. Metric Observation

A `metric_observation` represents the result of applying a metric definition to a specific subject and period.

Conceptual attributes include:

- Metric observation ID
- Analysis run ID
- Metric definition ID
- Subject entity ID
- Observation period
- Result value
- Unit
- Input-data reference
- Calculation status
- Calculation timestamp
- Methodology version
- Quality/confidence metadata where applicable

A metric observation must be traceable to the inputs and methodology used to produce it.

Derived metrics should not be treated as primary source facts.

---

## 24. Score Persistence

YUKIRA may persist scores generated by the decision engine.

Scores should be associated with:

- Analysis run
- Score type
- Score value
- Score methodology version
- Input metric set
- Calculation timestamp
- Status

The database should support distinct score types, including:

- Quality/Fund Score
- Opportunity Score
- Investor Fit Score
- Confidence Score

These scores must remain analytically distinct.

A high Quality/Fund Score must not automatically imply a high Opportunity Score, and a high score must not be interpreted as certainty of future returns.

Scores should therefore always be accompanied by their definitions, methodology version, and relevant analytical context.

---

## 25. Decision Persistence

The decision engine may produce a decision outcome for an analysis run.

Supported decision outcomes include:

- BUY
- HOLD
- WATCH
- INVESTIGATE
- REDUCE
- AVOID
- INSUFFICIENT EVIDENCE

A persisted decision should contain:

- Decision ID
- Analysis run ID
- Subject entity
- Decision outcome
- Decision-rule version
- Quality/Fund Score reference
- Opportunity Score reference
- Investor Fit Score reference where applicable
- Confidence Score reference
- Decision timestamp
- Status

The database must preserve the decision as an output of a specific analysis run.

A later analysis must create a new decision record rather than silently replacing the historical decision.

---

## 26. Scenario and Stress-Test Data

Scenario analysis should be represented separately from normal historical observations.

A scenario record should identify:

- Scenario ID
- Scenario name
- Scenario type
- Description
- Assumptions
- Severity classification
- Methodology version
- Creation timestamp
- Status

Examples include:

- Base case
- Bull case
- Bear case
- Severe stress
- Interest-rate shock
- Equity drawdown
- Credit-spread widening
- Liquidity stress

Scenario results should be linked to the analysis run that generated them.

Scenario assumptions must be stored separately from observed historical facts.

---

## 27. Evidence and Research Records

YUKIRA should maintain an evidence layer for material analytical conclusions.

An evidence record may contain:

- Evidence ID
- Evidence type
- Source ID
- Source reference
- Observation date
- Retrieval timestamp
- Subject entity
- Evidence summary
- Reliability classification
- Methodology/reference metadata
- Status

Evidence types may include:

- Primary source
- Regulatory source
- Fund disclosure
- Market data
- Research source
- Derived calculation
- Analytical interpretation

The evidence layer must distinguish observed facts from YUKIRA-generated calculations and interpretations.

---

## 28. Investor Profile and Fit Data

Investor-specific analysis requires a separate representation of investor context.

Conceptual investor-profile attributes may include:

- Investor profile ID
- Investment objective
- Time horizon
- Risk constraints
- Liquidity requirements
- Portfolio constraints
- Investment preferences
- Profile version
- Created timestamp
- Updated timestamp

Investor-specific information must not modify the underlying objective financial facts about a fund.

Instead, investor context should be applied during the Investor Fit and Decision stages.

The system should support versioning of investor profiles so that historical analyses can identify the investor context used at the time.

---

## 29. Audit Trail

Important database operations and analytical outputs should be auditable.

The audit layer should support recording:

- Event ID
- Event type
- Entity type
- Entity ID
- Analysis run where applicable
- Actor or service
- Timestamp
- Previous state where appropriate
- New state where appropriate
- Reason or event metadata

Audit records should be append-oriented.

Critical analytical history must not depend on mutable application logs alone.

The audit trail should help answer:

```text
What changed?
When did it change?
Who or what changed it?
Why did it change?
What analysis was affected?
```

---

## 30. Core Relationship Model

The core database relationships should conceptually follow:

```text
Fund House
    |
    +----< Scheme
              |
              +----< Plan
                       |
                       +----< Option
                                |
                                +----< NAV Observation
                                |
                                +----< Portfolio Holding
                                |
                                +----< Analysis Run
                                           |
                                           +----< Metric Observation
                                           |
                                           +----< Score
                                           |
                                           +----< Scenario Result
                                           |
                                           +----< Decision
                                           |
                                           +----< Evidence Reference
```

Supporting relationships include:

```text
Scheme/Option ----> Benchmark
Holding -----------> Security
Observation -------> Data Source
Analysis Run -------> Methodology Version
Metric Observation -> Metric Definition
Analysis Run -------> Application Version
Analysis Run -------> Model Version
Analysis Run -------> Investor Profile
```

The actual physical schema may normalize or restructure these relationships where required for performance, integrity, or maintainability.

The logical model is authoritative for meaning; the physical schema may evolve as implementation requirements become clearer.

---

## 31. Normalization and Data Modeling

The MVP database should favor a normalized relational model for core entities and financial observations.

Normalization should reduce:

- Duplicate master data
- Conflicting entity definitions
- Update anomalies
- Inconsistent identifiers
- Ambiguous historical relationships

However, normalization should not be pursued at the expense of analytical usability or reasonable query performance.

Derived analytical datasets may use denormalized structures where justified by:

- Query performance
- Reporting requirements
- Repeated analytical workloads
- Materialized views
- Caching

Denormalization must not create conflicting sources of truth.

The canonical normalized records remain authoritative.

---

## 32. Primary Keys, Foreign Keys and Constraints

All core entities should use stable internal primary keys.

Foreign-key relationships should be enforced wherever practical.

The database should use appropriate constraints for:

- Required fields
- Uniqueness
- Referential integrity
- Valid status values
- Valid date relationships
- Non-negative values where logically required
- Valid numeric ranges where appropriate

Business rules that require complex analytical logic should remain in the application or analytical layer rather than being forced into database constraints.

Database constraints should protect fundamental data integrity.

---

## 33. Indexing Strategy

Indexes should be introduced based on actual access patterns.

Likely high-value indexes include combinations involving:

- Scheme/plan/option identifiers
- Observation dates
- Security identifiers
- Benchmark identifiers
- Analysis run identifiers
- Metric identifiers
- Source identifiers
- Effective dates

Time-series queries will commonly filter by:

```text
Entity + Date Range
```

Therefore, indexes should support efficient historical retrieval.

Indexes must be validated against actual query performance rather than added indiscriminately.

Excessive indexing can increase:

- Storage requirements
- Insert/update cost
- Maintenance overhead

Performance optimization should be evidence-driven.

---

## 34. Schema Migrations

All production schema changes must be version-controlled.

The database migration system should support:

- Ordered migrations
- Repeatable deployment
- Environment consistency
- Roll-forward changes
- Migration history
- Developer reproducibility

The `database/migrations` directory is the repository location for database migration artifacts.

Manual production schema modification should be avoided.

A migration should be reviewed before being applied to shared or production environments.

Migration history is part of YUKIRA's technical auditability.

---

## 35. Security, Backup and Recovery

Database access must follow least-privilege principles.

Application services should not use unrestricted database credentials.

The system should distinguish, where practical, between:

- Application access
- Migration/admin access
- Read-only analytical access
- Operational access

Credentials must not be committed to Git.

Development credentials may be provided through local environment configuration, while production credentials must use secure secret-management mechanisms.

The database must have a defined backup and recovery strategy before production deployment.

The recovery strategy should define:

- Backup frequency
- Retention
- Recovery objectives
- Restore procedures
- Backup verification
- Disaster-recovery responsibilities

A backup that has never been successfully restored should not be considered a verified recovery mechanism.

---

## 36. MVP Database Quality Gate

The MVP database architecture is considered ready for implementation when the following conditions are satisfied:

### Data Model

- Core entities are explicitly defined.
- Relationships are understood.
- Primary and foreign-key strategy is defined.
- Historical data requirements are represented.

### Data Integrity

- Validation states are defined.
- Invalid data can be rejected or quarantined.
- Referential integrity is protected.
- Duplicate handling is defined.

### Analytical Reproducibility

- Analysis runs are identifiable.
- Metric definitions are versioned.
- Scores reference their methodology.
- Decisions reference their analysis run.
- Important outputs can be traced to their inputs.

### Historical Correctness

- Observation date is distinguished from retrieval date.
- Material historical changes can be represented.
- Benchmark history can be preserved.
- Point-in-time analysis is supported.

### Operational Readiness

- Migrations are version-controlled.
- Development database runs through the project's Docker environment.
- Credentials are externalized.
- Backup and recovery requirements are documented before production.

### Scope Discipline

The MVP should implement only the database structures required for the initial 25–35 high-value metrics and their supporting analysis workflow.

The schema must be extensible, but future requirements should not justify unnecessary infrastructure or speculative tables.

---

## 37. Database Evolution

The YUKIRA database will evolve as analytical requirements become clearer.

Schema changes should be driven by:

- Validated product requirements
- Quantitative methodology
- Data-source requirements
- Analytical performance
- Auditability requirements
- Production experience

Database design decisions should be documented when they have meaningful architectural consequences.

Breaking changes must be handled through explicit migrations and compatibility planning.

---

## 38. Database Architectural Risks

Important database risks include:

### Historical Overwriting

Replacing historical values with current values can invalidate prior analysis.

### Data-Source Dependency

Dependence on a single external provider can create availability and continuity risk.

### Identifier Instability

External identifiers may change or conflict across providers.

### Data Duplication

Duplicated financial observations can produce conflicting analytical inputs.

### Poor Provenance

Data without source and retrieval information can become difficult to verify.

### Premature Complexity

Introducing specialized databases or infrastructure before the workload requires them can increase operational risk.

### Analytical Contamination

Derived calculations must not accidentally become indistinguishable from source observations.

### Schema Lock-In

Overly rigid early modeling can make future analytical expansion unnecessarily difficult.

YUKIRA should address these risks through controlled schema evolution, provenance, validation, and testing.

---

## 39. Final Database Standard

The YUKIRA database must function as a trustworthy foundation for investment intelligence.

It must preserve the distinction between:

```text
Observed Data
      ↓
Validated Data
      ↓
Derived Calculations
      ↓
Analytical Interpretation
      ↓
Investment Decision
```

Every material analytical output should be reproducible from identifiable inputs, methodology, configuration, and relevant software/model versions.

The database should optimize for:

**Correctness → Integrity → Auditability → Reproducibility → Maintainability → Performance → Scale**

The MVP should remain deliberately simple while establishing the structural foundations required for institutional-grade investment analysis.

Database complexity must be earned by actual product and analytical requirements.

---