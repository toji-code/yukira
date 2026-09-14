# YUKIRA System Architecture Specification

**Document ID:** 003  
**Version:** 0.1  
**Status:** Draft  
**Last Updated:** 2026-09-14

---

# 1. Purpose

This document defines the high-level technical architecture of YUKIRA.

It establishes:

- Major system components
- Responsibilities and boundaries
- Data flow
- Service boundaries
- Technology direction
- Communication patterns
- Data ownership
- Reliability principles
- Security principles
- Auditability requirements
- Architectural constraints

This document defines **how YUKIRA should be structured**, while detailed implementation decisions belong in dedicated technical specifications and Architecture Decision Records.

---

# 2. Architectural Goals

The architecture must prioritize:

1. Correctness
2. Data integrity
3. Auditability
4. Reproducibility
5. Maintainability
6. Testability
7. Explainability
8. Security
9. Extensibility
10. Operational reliability

The architecture must allow YUKIRA to grow from an MVP into a substantially larger investment intelligence platform without requiring unnecessary rewrites.

---

# 3. Architectural Principles

## 3.1 Deterministic Computation

Financial calculations must be performed by deterministic, testable software.

AI must not be the source of truth for numerical calculations.

## 3.2 Separation of Concerns

Data ingestion, validation, storage, quantitative analysis, scoring, forecasting, AI interpretation, and presentation should have clearly defined responsibilities.

A component should not silently assume responsibilities belonging to another layer.

## 3.3 Evidence Traceability

Important outputs must be traceable from conclusion back to:

- Calculation
- Input data
- Data source
- Timestamp
- Methodology version
- Software version
- Model version where applicable

## 3.4 Versioned Methodology

Investment methodology and scoring logic must be versioned independently from ordinary application releases where practical.

Historical results should remain interpretable after methodology changes.

## 3.5 Failure Transparency

A failed or incomplete upstream process must not silently produce a seemingly valid downstream result.

The system should propagate data-quality and calculation-quality states.

## 3.6 Progressive Complexity

The architecture should support advanced functionality without requiring advanced infrastructure for the MVP.

Start simple.

Introduce complexity only when justified by measurable requirements.

---

# 4. High-Level Architecture

The target architecture is:

```text
                    ┌──────────────────────┐
                    │      Investors       │
                    └──────────┬───────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │   Web Application    │
                    │ React / Next.js      │
                    └──────────┬───────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │      API Layer       │
                    │    Spring Boot       │
                    └──────────┬───────────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
              ▼                ▼                ▼
     ┌────────────────┐ ┌───────────────┐ ┌───────────────┐
     │ Investment     │ │ Quantitative  │ │ AI / Research │
     │ Intelligence   │ │ & Risk Engine │ │ Services      │
     │ Orchestration  │ │ Python        │ │ Python        │
     └───────┬────────┘ └───────┬───────┘ └───────┬───────┘
             │                  │                 │
             └──────────────────┼─────────────────┘
                                │
                                ▼
                    ┌──────────────────────┐
                    │    Data Services     │
                    │ Ingestion/Validation │
                    └──────────┬───────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │      PostgreSQL      │
                    │   System of Record   │
                    └──────────────────────┘

             External Sources
                    │
                    ▼
          ┌────────────────────┐
          │ Market / Fund Data │
          │ Documents / APIs   │
          └────────────────────┘
```

---

# 5. Major Components

YUKIRA should initially consist of the following major components:

1. Frontend
2. Backend API
3. Quantitative and Risk Engine
4. AI and Research Services
5. Data Ingestion and Validation
6. PostgreSQL Database
7. Supporting Infrastructure

---

# 6. Frontend

## 6.1 Responsibility

The frontend provides the investor-facing user experience.

It is responsible for:

- Investment search
- Investment profiles
- Analytical dashboards
- Investment reports
- Risk visualization
- Portfolio visualization
- Scenario presentation
- Evidence presentation
- Methodology explanations
- Investor-specific context

The frontend must not contain authoritative financial calculations.

---

## 6.2 Technology Direction

Preferred technology:

- React
- Next.js
- TypeScript

The frontend communicates with the backend through defined APIs.

---

# 7. Backend API

## 7.1 Responsibility

The backend is the primary application orchestration layer.

It should be responsible for:

- Authentication and authorization
- Investor and portfolio context
- Investment entities
- API orchestration
- Request validation
- Analysis job coordination
- Report retrieval
- Configuration management
- Access control
- Audit metadata
- Communication with analytical services

---

## 7.2 Technology Direction

Preferred technology:

- Java
- Spring Boot
- REST APIs initially

The backend should not duplicate complex quantitative calculations that belong in the quantitative engine.

---

# 8. Quantitative and Risk Engine

## 8.1 Responsibility

The quantitative engine performs deterministic analytical calculations.

Responsibilities may include:

- Return calculations
- CAGR
- Rolling returns
- Volatility
- Drawdowns
- Downside metrics
- Risk-adjusted metrics
- Portfolio statistics
- Factor calculations
- Concentration metrics
- Scenario calculations
- Other validated quantitative measures

---

## 8.2 Technology Direction

Preferred technology:

- Python
- NumPy
- pandas
- SciPy where appropriate
- FastAPI for service exposure

Libraries should be selected based on analytical requirements rather than convenience.

---

## 8.3 Determinism

Given identical:

- Inputs
- Methodology version
- Configuration
- Code version

the quantitative engine should produce the same result.

Randomized models must explicitly record their seeds and relevant configuration when reproducibility is required.

---

# 9. AI and Research Services

## 9.1 Responsibility

AI services support research, interpretation, synthesis, and explanation.

They may:

- Interpret quantitative results
- Summarize evidence
- Explain financial concepts
- Analyze qualitative documents
- Identify anomalies
- Highlight contradictions
- Generate investor-facing explanations
- Assist research workflows

---

## 9.2 AI Boundary

AI must not be treated as the authoritative source for numerical financial values.

The AI layer should consume structured analytical results and verified evidence wherever possible.

AI output should not silently modify validated quantitative results.

---

## 9.3 Grounding

Where AI makes factual claims, the system should provide access to supporting evidence or source references where feasible.

The architecture should support structured evidence retrieval rather than relying solely on free-form model context.

---

# 10. Data Ingestion Layer

## 10.1 Responsibility

The ingestion layer retrieves investment data from supported external sources.

Responsibilities include:

- Source access
- Data retrieval
- Parsing
- Initial normalization
- Source metadata capture
- Retrieval timestamps
- Raw-data preservation where permitted
- Ingestion error handling

---

## 10.2 Source Abstraction

External data sources should be abstracted behind source-specific adapters.

The rest of YUKIRA should not depend directly on the implementation details of a particular provider.

Example:

```text
Data Source
    ↓
Source Adapter
    ↓
Normalized Data
    ↓
Validation
    ↓
Database
```

This allows sources to be replaced or added without rewriting the analytical system.

---

# 11. Data Validation Layer

Data validation must occur before important analytical calculations.

Validation should include, where applicable:

- Schema validation
- Type validation
- Required-field validation
- Range checks
- Date checks
- Duplicate detection
- Missing-data checks
- Cross-source reconciliation
- Historical continuity checks
- Referential integrity

Each important dataset should have an identifiable quality state.

Possible states may include:

```text
VALID
VALID_WITH_WARNINGS
INCOMPLETE
INVALID
STALE
UNAVAILABLE
```

The exact data-quality model will be defined separately.

---

# 12. Data Storage

PostgreSQL should serve as the primary structured system of record for the MVP.

The database should store, where appropriate:

- Investment entities
- Fund schemes
- Historical observations
- Portfolio holdings
- Benchmarks
- Data-source metadata
- Data-quality information
- Calculated metrics
- Methodology versions
- Scoring results
- Analysis results
- Investor context
- Audit information

Raw documents or large external artifacts may eventually require separate object storage.

---

# 13. Data Layers

The data architecture should conceptually distinguish between:

```text
Raw / Source Data
        ↓
Normalized Data
        ↓
Validated Data
        ↓
Derived Data
        ↓
Analytical Results
        ↓
Investment Intelligence
```

Derived and analytical data must not be confused with source data.

This distinction is essential for auditability.

---

# 14. Analysis Pipeline

A standard analysis should follow:

```text
Source Data
    ↓
Ingestion
    ↓
Validation
    ↓
Normalization
    ↓
Persistence
    ↓
Quantitative Calculations
    ↓
Risk / Factor / Portfolio Analysis
    ↓
Qualitative Analysis
    ↓
Valuation Analysis
    ↓
Scenario / Stress Analysis
    ↓
Hierarchical Scoring
    ↓
Confidence Assessment
    ↓
AI Interpretation
    ↓
Investment Report
```

The exact execution mechanism may evolve as the platform grows.

---

# 15. Investment Intelligence Orchestration

The backend or a dedicated orchestration layer should coordinate the analytical workflow.

The orchestrator should:

- Determine required datasets
- Check data availability
- Trigger analytical modules
- Track module status
- Collect results
- Handle failures
- Assemble the final analytical result
- Record methodology and version metadata

The orchestrator should not contain the implementation of every analytical calculation.

---

# 16. Scoring Engine

The scoring engine converts validated analytical outputs into structured decision dimensions.

It should support:

- Hierarchical scoring
- Dimension-level scores
- Metric weighting
- Correlation/redundancy controls
- Missing-data handling
- Confidence adjustments
- Methodology versioning
- Score explainability

The scoring engine must not simply sum hundreds of independent metrics.

The scoring methodology will be defined in the Investment Intelligence Specification.

---

# 17. Decision Engine

The decision engine interprets analytical results into supported decision states.

Potential outcomes include:

```text
BUY
HOLD
WATCH
INVESTIGATE
REDUCE
AVOID
```

The decision engine should consider:

- Quality
- Opportunity
- Investor fit
- Confidence
- Material risks
- Data limitations
- Scenario resilience

Decision logic must be versioned and auditable.

---

# 18. Confidence Engine

Confidence should be calculated separately from investment attractiveness.

It should consider factors such as:

- Data completeness
- Data quality
- Historical sample size
- Model reliability
- Methodological robustness
- Evidence consistency
- Qualitative evidence quality
- Scenario uncertainty

Confidence must not simply increase because more metrics are available.

---

# 19. Evidence and Provenance

Important analytical outputs should retain provenance.

A result should eventually be traceable through:

```text
Conclusion
    ↓
Decision / Score
    ↓
Analytical Metric
    ↓
Calculation
    ↓
Input Dataset
    ↓
Source
    ↓
Retrieval Timestamp
    ↓
Methodology Version
    ↓
Software Version
```

This should be supported at the data-model level rather than implemented only through report text.

---

# 20. Methodology Versioning

Analytical methodologies must be version-controlled.

Examples include:

- Metric definitions
- Calculation formulas
- Benchmark definitions
- Scoring weights
- Thresholds
- Scenario assumptions
- Forecast models

A change to methodology should create a new identifiable version.

Historical analytical results should retain the methodology version under which they were generated.

---

# 21. API Architecture

The MVP should initially use REST APIs.

Conceptual API domains may include:

```text
/api/investments
/api/funds
/api/portfolio
/api/analysis
/api/metrics
/api/reports
/api/sources
/api/methodology
```

Exact endpoints should be defined in the API specification rather than this document.

---

# 22. Service Communication

The MVP should favor simple synchronous communication where practical.

Potential communication:

```text
Frontend
   ↓ REST
Backend
   ↓ HTTP/REST
Quant Engine
   ↓
PostgreSQL
```

Asynchronous processing may be introduced when analysis workloads justify it.

The architecture should not introduce message queues, event buses, or distributed orchestration solely for theoretical scalability.

---

# 23. Background Processing

Some workflows may eventually require asynchronous processing.

Examples include:

- Large historical data ingestion
- Portfolio reconstruction
- Large-scale analysis
- Document processing
- Model training
- Batch recalculation

For the MVP, these should use the simplest reliable mechanism that satisfies actual requirements.

---

# 24. Caching

Caching may be introduced for:

- Frequently accessed reference data
- Expensive analytical results
- Public investment metadata
- Report generation

Cached analytical results must have clear invalidation and version rules.

Stale cached information must not silently appear current.

---

# 25. Security

Security requirements include:

- No credentials in source control
- Environment-based secret configuration
- Secure API authentication
- Authorization checks
- Input validation
- Database access controls
- Secure communication
- Audit logging for sensitive operations
- Protection of investor-specific information

Security architecture will become more detailed as authentication and investor data requirements are finalized.

---

# 26. Observability

The system should provide sufficient observability to diagnose failures.

Important events should be traceable across:

```text
Request
   ↓
Analysis Job
   ↓
Data Retrieval
   ↓
Calculation
   ↓
Scoring
   ↓
Report
```

The platform should eventually support:

- Structured logs
- Error tracking
- Request identifiers
- Analysis identifiers
- Processing timestamps
- Health checks
- Basic performance metrics

---

# 27. Error Handling

Errors must be explicit.

Examples:

```text
DATA_UNAVAILABLE
DATA_INVALID
DATA_STALE
CALCULATION_FAILED
INSUFFICIENT_HISTORY
METHODOLOGY_ERROR
MODEL_ERROR
EXTERNAL_SOURCE_ERROR
```

The system must distinguish between:

- No data
- Bad data
- Insufficient data
- Calculation failure
- System failure

These states should not be collapsed into a generic "analysis unavailable."

---

# 28. Testing Architecture

Testing should exist at multiple levels.

## Unit Tests

For:

- Financial calculations
- Data transformations
- Validation rules
- Scoring functions

## Integration Tests

For:

- Database interactions
- API interactions
- Service communication
- Data pipelines

## Analytical Validation Tests

For:

- Known financial examples
- Expected statistical results
- Historical calculations
- Edge cases

## End-to-End Tests

For:

- Investor search
- Investment analysis
- Report generation

Financial calculations require particularly strong deterministic test coverage.

---

# 29. Reproducibility

An analytical result should be reproducible using recorded:

- Input data version
- Methodology version
- Configuration
- Code version
- Model version where applicable

Reproducibility is a core architectural requirement.

---

# 30. Deployment Direction

The platform should initially be Docker-first.

Conceptually:

```text
Docker Compose / Development Environment

┌─────────────────────────────┐
│ Frontend                    │
├─────────────────────────────┤
│ Backend                     │
├─────────────────────────────┤
│ Quant Engine                │
├─────────────────────────────┤
│ AI Services                 │
├─────────────────────────────┤
│ PostgreSQL                  │
└─────────────────────────────┘
```

Production deployment architecture will be defined separately after MVP requirements are established.

---

# 31. Repository Architecture

The repository should broadly map to system responsibilities:

```text
yukira/
│
├── docs/
│
├── frontend/
│
├── backend/
│
├── quant-engine/
│
├── ai-services/
│
├── database/
│
└── infrastructure/
```

Documentation remains separate from implementation.

---

# 32. MVP Architecture

The MVP should use a deliberately simple architecture:

```text
                    ┌───────────────┐
                    │   Next.js     │
                    │   Frontend    │
                    └───────┬───────┘
                            │
                            ▼
                    ┌───────────────┐
                    │ Spring Boot   │
                    │ Backend API   │
                    └───────┬───────┘
                            │
              ┌─────────────┼─────────────┐
              ▼             ▼             ▼
        PostgreSQL    Quant Engine    AI Service
                           │
                           ▼
                     Python / FastAPI
```

The MVP should avoid premature microservice decomposition.

Logical boundaries should exist even if some components are deployed together initially.

---

# 33. Future Scalability

The architecture should allow future evolution toward:

- Separate analytical services
- Job queues
- Event-driven processing
- Object storage
- Distributed computation
- Feature stores
- Model registries
- Advanced observability
- Larger-scale data processing
- Multi-region deployment

These capabilities should be introduced only when actual scale or reliability requirements justify them.

---

# 34. Architectural Risks

Key risks include:

- Overengineering too early
- Excessive service fragmentation
- Tight coupling between AI and quantitative logic
- Poor data provenance
- Inconsistent methodology versions
- Database schema rigidity
- Hidden calculation dependencies
- Uncontrolled AI-generated facts
- Insufficient test coverage
- Silent data-quality failures
- Premature infrastructure complexity

Architecture decisions should actively minimize these risks.

---

# 35. Architecture Decision Records

Significant architectural decisions should be documented separately as ADRs.

Examples:

- Why PostgreSQL was selected
- Why Java/Spring Boot is used for the application backend
- Why Python is used for quantitative services
- Why REST is initially preferred
- Why microservices are deferred
- Why a particular data source is selected
- Why a particular scoring architecture is adopted

ADRs should record:

- Context
- Decision
- Alternatives considered
- Rationale
- Consequences

---

# 36. Architectural Quality Gate

A proposed architectural component should be evaluated against:

**Necessary → Simple → Testable → Maintainable → Auditable → Scalable**

A technically sophisticated component should not be introduced merely because it is available.

---

# 37. Separation of Authority

The system should maintain clear authority boundaries.

```text
Source Data
    │
    ▼
Data Layer
    │
    ▼
Deterministic Quantitative Engine
    │
    ▼
Scoring / Decision Logic
    │
    ▼
AI Interpretation
    │
    ▼
Investor Presentation
```

AI interpretation occurs after validated analytical computation wherever possible.

The presentation layer must not become the source of analytical truth.

---

# 38. Architecture Evolution

This architecture is intentionally versioned and evolutionary.

The MVP architecture should be considered successful if it provides a reliable foundation for the first working investment intelligence system.

Future architecture changes should be driven by:

- Measured system requirements
- Investor needs
- Data scale
- Reliability requirements
- Analytical complexity
- Operational evidence

Architecture should evolve based on evidence rather than speculation.

---

# 39. Final Architectural Standard

YUKIRA's architecture should make it possible to answer:

> **"Where did this conclusion come from?"**

The system should make that answer technically reconstructable.

The architecture must therefore prioritize:

**Data Integrity → Deterministic Analysis → Traceability → Explainability → Reliable Decision Support**

over unnecessary technological complexity.

---

**End of System Architecture Specification**