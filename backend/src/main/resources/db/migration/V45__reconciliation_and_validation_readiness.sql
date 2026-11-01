-- Flyway Migration V45: Implementation Reconciliation & Validation Readiness
-- Authoritative Schema Extension for Tasks 410-459 in Project YUKIRA

-- 1. Canonical Task Requirement & Traceability Reconciliation Table (Tasks 410-413)
CREATE TABLE IF NOT EXISTS v1_task_requirement_reconciliation (
    id BIGSERIAL PRIMARY KEY,
    task_number INT NOT NULL UNIQUE,
    original_title VARCHAR(255) NOT NULL,
    original_objective TEXT NOT NULL,
    required_backend_behavior TEXT,
    required_persistence_behavior TEXT,
    required_api_ui_behavior TEXT,
    reported_title VARCHAR(255),
    discrepancy_type VARCHAR(50) NOT NULL, -- EXACT_MATCH, TITLE_REWORDED, OBJECTIVE_SHIFTED, SCOPE_NARROWED, UNREPORTED
    implementation_status VARCHAR(50) NOT NULL, -- IMPLEMENTED, PARTIALLY_IMPLEMENTED, NOT_IMPLEMENTED, BLOCKED
    evidence_classifications TEXT NOT NULL, -- Comma-separated tokens: SOURCE_IMPLEMENTATION_PRESENT, PERSISTENCE_SCHEMA_PRESENT, etc.
    implementation_artifacts TEXT,
    remaining_gap TEXT,
    reconciliation_version INT DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_task_req_recon_num ON v1_task_requirement_reconciliation(task_number);
CREATE INDEX IF NOT EXISTS idx_task_req_recon_status ON v1_task_requirement_reconciliation(implementation_status);

-- 2. Structured Historical Report Contradiction Register (Task 415)
CREATE TABLE IF NOT EXISTS historical_report_contradiction_record (
    id BIGSERIAL PRIMARY KEY,
    contradiction_key VARCHAR(100) NOT NULL UNIQUE,
    area VARCHAR(100) NOT NULL,
    previous_claim TEXT NOT NULL,
    current_repository_reality TEXT NOT NULL,
    severity VARCHAR(50) NOT NULL, -- HIGH, MEDIUM, LOW
    reconciliation_resolution TEXT NOT NULL,
    reconciled_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_hist_contradiction_key ON historical_report_contradiction_record(contradiction_key);

-- 3. Canonical V1 Validation Readiness Register (Task 459)
CREATE TABLE IF NOT EXISTS v1_validation_readiness_item (
    id BIGSERIAL PRIMARY KEY,
    readiness_area_code VARCHAR(50) NOT NULL UNIQUE,
    area_name VARCHAR(255) NOT NULL,
    relevant_artifacts TEXT NOT NULL,
    required_verification_activity TEXT NOT NULL,
    required_real_data_evidence TEXT NOT NULL,
    expected_acceptance_criteria TEXT NOT NULL,
    dependencies TEXT,
    risk_if_not_verified TEXT NOT NULL,
    responsible_review_area VARCHAR(100) NOT NULL,
    validation_conducted BOOLEAN NOT NULL DEFAULT FALSE,
    readiness_state VARCHAR(50) NOT NULL DEFAULT 'READY_FOR_VALIDATION',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_val_readiness_code ON v1_validation_readiness_item(readiness_area_code);

-- 4. Benchmark Coverage Ledger Reality Columns (Tasks 416, 417, 420)
ALTER TABLE benchmark_historical_coverage_ledger ADD COLUMN IF NOT EXISTS declared_start_date DATE;
ALTER TABLE benchmark_historical_coverage_ledger ADD COLUMN IF NOT EXISTS declared_end_date DATE;
ALTER TABLE benchmark_historical_coverage_ledger ADD COLUMN IF NOT EXISTS declared_observation_count INT DEFAULT 0;
ALTER TABLE benchmark_historical_coverage_ledger ADD COLUMN IF NOT EXISTS actual_observation_count INT DEFAULT 0;
ALTER TABLE benchmark_historical_coverage_ledger ADD COLUMN IF NOT EXISTS actual_start_date DATE;
ALTER TABLE benchmark_historical_coverage_ledger ADD COLUMN IF NOT EXISTS actual_end_date DATE;
ALTER TABLE benchmark_historical_coverage_ledger ADD COLUMN IF NOT EXISTS coverage_reconciliation_status VARCHAR(50) DEFAULT 'DECLARED_VS_ACTUAL_RECONCILED';
ALTER TABLE benchmark_historical_coverage_ledger ADD COLUMN IF NOT EXISTS source_artifact_lineage_status VARCHAR(50) DEFAULT 'SOURCE_LINEAGE_PENDING';

-- 5. Operational Job Dependency Fencing & Retry Columns (Tasks 433, 434, 435)
ALTER TABLE operational_job_dependency ADD COLUMN IF NOT EXISTS fencing_token BIGINT DEFAULT 0;
ALTER TABLE operational_job_dependency ADD COLUMN IF NOT EXISTS last_heartbeat_at TIMESTAMPTZ;
ALTER TABLE operational_job_dependency ADD COLUMN IF NOT EXISTS retry_count INT DEFAULT 0;
ALTER TABLE operational_job_dependency ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMPTZ;

-- 6. Correct Misleading Seed Semantics from V44 (Tasks 416, 417, 419)
UPDATE benchmark_historical_coverage_ledger
SET declared_start_date = '2019-01-01',
    declared_end_date = '2024-01-15',
    declared_observation_count = 1248,
    actual_observation_count = 0,
    actual_start_date = NULL,
    actual_end_date = NULL,
    total_observations = 0,
    ingestion_state = 'EXTERNAL_FEED_PENDING',
    coverage_reconciliation_status = 'OBSERVATIONS_PENDING_LIVE_INGESTION',
    source_artifact_lineage_status = 'SOURCE_LINEAGE_PENDING'
WHERE benchmark_code = 'NIFTY_500_TRI';

-- 7. Seed Initial Contradiction Records (Task 415)
INSERT INTO historical_report_contradiction_record (
    contradiction_key, area, previous_claim, current_repository_reality, severity, reconciliation_resolution
) VALUES
('BENCHMARK_OBSERVATION_COUNT', 'BENCHMARK_COVERAGE',
 'V44 migration seeded 1,248 observations for NIFTY_500_TRI and reported operational status.',
 'Zero rows exist in benchmark_observation table; external NSE connector is pending licensing.',
 'HIGH',
 'Reconciled in V45: Declared coverage (1,248) separated from actual observed coverage (0); status set to EXTERNAL_FEED_PENDING.'),

('TASK_360_409_LEDGER_TITLES', 'GOVERNANCE_LEDGER',
 'Tasks 360-409 completion report reworded task titles in V1ProductReconciliationService ledger.',
 'Original task requirements specified exact canonical titles that were altered in the report ledger.',
 'MEDIUM',
 'Reconciled in V45: Added v1_task_requirement_reconciliation preserving original prompt titles and explicit discrepancy mapping.'),

('SCHEDULER_AUTOMATION_STATE', 'OPERATIONAL_EXECUTION',
 'Previous report described background jobs as scheduled and operational.',
 'application.properties defaults all refresh schedulers (AMFI, Benchmark, FBIL) to enabled=false.',
 'HIGH',
 'Reconciled in V45: Clarified that scheduler beans are compiled and registered, but execution is disabled by default pending explicit environment activation.'),

('XIRR_ROOT_UNIQUENESS', 'QUANTITATIVE_ACCOUNTING',
 'Previous documentation claimed Descartes Rule of Signs proves root count for arbitrary mutual fund cash flows.',
 'Descartes Rule of Signs applies strictly to polynomials with integer powers; irregular cash flows require bounded numerical bracketing in [-0.999, 5.0].',
 'HIGH',
 'Reconciled in V45: Documented mathematical scope limits and enforced bounded bisection fallback in PortfolioValuationAndDriftService.'),

('MULTI_TENANT_CAS_ISOLATION', 'SECURITY_PRIVACY',
 'Earlier implementations accepted investorId from request parameters without checking JWT claims.',
 'JWT authentication is wired, but certain controller endpoints still exposed path-variable based lookups.',
 'HIGH',
 'Reconciled in V45: Enforced principal subject resolution and ownership verification across private CAS and thesis APIs.')
ON CONFLICT (contradiction_key) DO NOTHING;

-- 8. Seed Canonical Validation Readiness Areas (Task 459)
INSERT INTO v1_validation_readiness_item (
    readiness_area_code, area_name, relevant_artifacts, required_verification_activity,
    required_real_data_evidence, expected_acceptance_criteria, dependencies,
    risk_if_not_verified, responsible_review_area, validation_conducted, readiness_state
) VALUES
('VAL-01-QUANT-MATH', 'Quantitative Formula & Numerical Precision Validation',
 'quant-engine/src/, MetricValidationRegistryService.java, calculation_run table',
 'Verify RET-02, RET-03, RSK-01 calculations against independent reference benchmarks (GIPS, SEBI)',
 'Canonical pilot HDFC Flexi Cap 2024-01-01 to 2024-01-15 verified AMFI series A',
 'Numerical error < 1e-8 relative tolerance; zero division handled with explicit abstention',
 'PostgreSQL 17, quant-engine Python 3.12 runtime',
 'Incorrect financial returns or volatility displayed to institutional allocators',
 'QUANTITATIVE_METHODOLOGY_COMMITTEE', FALSE, 'READY_FOR_VALIDATION'),

('VAL-02-PIT-BITEMPORAL', 'Point-in-Time Reconstruction & Look-Ahead Bias Review',
 'NavObservationRepository.java, BenchmarkObservationRepository.java, V44 window partition queries',
 'Audit SQL window queries to confirm revisions published after knowledge cutoff never leak into past windows',
 'Historical retroactive revisions published by AMFI with revision_seq > 1',
 'Exactly 1 observation per trading day; 100% exclusion of future availability timestamps',
 'PostgreSQL SQL window functions, Flyway V44',
 'Look-ahead bias corrupting historical backtests and performance evaluations',
 'DATA_ARCHITECTURE_REVIEW', FALSE, 'READY_FOR_VALIDATION'),

('VAL-03-BENCHMARK-REALITY', 'Benchmark & NAV Source Coverage Verification',
 'BenchmarkHistoricalCoverageLedger.java, BenchmarkCoverageLedgerService.java, NiftyTriParser.java',
 'Verify actual live ingestion of NSE NIFTY 500 TRI series and trace to official source artifacts',
 'NSE Daily TRI index CSV/API feeds with SHA-256 cryptographic hashes',
 'Actual observation count matching exchange trading days; zero gaps without gazetted holiday records',
 'NSE external provider connectivity or official data licensing',
 'Invalid alpha, beta, tracking error, or capture ratios presented to investors',
 'DATA_INGESTION_ENGINEERING', FALSE, 'READY_FOR_VALIDATION'),

('VAL-04-HOLDINGS-PORTFOLIO', 'Holdings Construction & Portfolio Accounting Validation',
 'HoldingObservationRepository.java, PortfolioSnapshotAndTimelineService.java',
 'Verify monthly portfolio disclosures sum to 100% without weight redistribution or synthetic imputation',
 'Monthly portfolio disclosure XLS/PDF from HDFC MF and PPFAS',
 'Raw holdings weight sum within 95%-102% cash-buffer; zero artificial redistribution',
 'Monthly AMC disclosure publishing cadences (10th of month)',
 'Misleading sector exposure, concentration risk, or pairwise overlap metrics',
 'PORTFOLIO_ACCOUNTING_TEAM', FALSE, 'READY_FOR_VALIDATION'),

('VAL-05-TAX-FIFO-AUDIT', 'Statutory Tax Rule Engine & FIFO Lot Continuity Review',
 'IndianMutualFundTaxClassificationService.java, FifoLotLedgerService.java, IndianMutualFundTaxRule.java',
 'Audit tax lot capital gains calculations against Finance (No. 2) Act 2024 statutory provisions',
 'Investor portfolio transaction histories with pre/post 23-Jul-2024 dates and Section 50AA debt lots',
 'Decomposed base tax, surcharge tiers, and 4% cess; accurate holding period thresholds (12M/36M)',
 'Statutory tax schedule updates and Chartered Accountant disclaimer presentation',
 'Inaccurate capital gains estimates misleading investor tax liability planning',
 'TAX_COMPLIANCE_REVIEW', FALSE, 'READY_FOR_VALIDATION'),

('VAL-06-SECURITY-TENANT', 'Investor Data Isolation & CAS Privacy Security Review',
 'SourceArtifactService.java, InvestorPortfolioController.java, SecurityConfig.java',
 'Penetration test multi-tenant boundaries to ensure investor A cannot access investor B private CAS or theses',
 'Auth0 JWT tokens with distinct subject claims; private CAS uploads',
 'HTTP 403 Forbidden on any cross-tenant resource access; 100% PAN masking across logs',
 'Auth0 identity provider configuration, Spring Security JWT validator',
 'Critical PII leakage, regulatory violation of SEBI/IT Act privacy requirements',
 'SECURITY_ARCHITECTURE_AUDIT', FALSE, 'READY_FOR_VALIDATION'),

('VAL-07-OPERATIONAL-SCHED', 'Background Scheduler & Execution Lock Telemetry Verification',
 'OperationalDependencyRegistryService.java, BackgroundJobExecutionRecord.java, Spring Schedulers',
 'Test distributed locking, fencing tokens, and execution logging under multi-worker scenarios',
 'Simulated concurrent job trigger attempts and network failure injections',
 'Zero concurrent duplicate execution; stale locks recovered safely after 30 min lease expiry',
 'PostgreSQL connection pool, Spring TaskScheduler',
 'Runaway ingestion loops, duplicate observations, or corrupted execution states',
 'DEVOPS_RELIABILITY_ENGINEERING', FALSE, 'READY_FOR_VALIDATION'),

('VAL-08-NOTIFICATION-OUTBOX', 'Notification Delivery Outbox Pattern Verification',
 'NotificationDeliveryService.java, MonitoringEvaluationLifecycleService.java, outbox table',
 'Verify at-least-once notification dispatch across In-App, Email, and Webhook channels',
 'Sample monitoring breach events triggering queued outbox deliveries',
 'Accurate delivery status transitions (QUEUED -> ATTEMPTED -> DELIVERED/FAILED); idempotent retries',
 'SMTP credentials, Webhook endpoint listener',
 'Silent failure of critical risk or thesis breach alerts to investors',
 'INTEGRATION_TESTING_TEAM', FALSE, 'READY_FOR_VALIDATION'),

('VAL-09-AI-GROUNDING', 'AI Interpretation Evidence Grounding & Abstention Review',
 'CanonicalEvidencePacketService.java, AiInterpretationService.java, prompt templates',
 'Evaluate AI responses against canonical evidence packets to verify zero speculative hallucination',
 'Evidence packets with complete, incomplete, and missing metrics',
 '100% claim-to-evidence token citation; deterministic abstention on any missing prerequisite',
 'LLM API key (Gemini/OpenAI) or deterministic offline fallback generator',
 'Regulatory violation: unauthorized algorithmic investment advice or false price projections',
 'AI_SAFETY_AND_GOVERNANCE_BOARD', FALSE, 'READY_FOR_VALIDATION'),

('VAL-10-E2E-WORKFLOW', 'End-to-End Investor Workflow & Evidence Chain Review',
 'V1ReconciliationController.java, CanonicalEvidencePacketModal.tsx, Next.js frontend pages',
 'Audit the 6-point evidence chain from frontend UI click down to raw cryptographic SHA-256 bits',
 'Live frontend navigation across Fund Research, Comparison, Portfolio, and Monitoring views',
 'All 6 evidence chain links valid, verifiable, and consistent; zero fake data badges',
 'Full multi-tier stack (Frontend, Backend, Quant Kernel, PostgreSQL)',
 'Broken investor trust from disconnect between interface claims and underlying data bits',
 'PRODUCT_MANAGEMENT_SIGN_OFF', FALSE, 'READY_FOR_VALIDATION')
ON CONFLICT (readiness_area_code) DO NOTHING;

-- 9. Seed Canonical 50-Task Requirement & Traceability Ledger (Tasks 410-413)
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    360, 'Canonical Operational Capability State Model', 'Inspect the existing `OperationalJobDependency`, `OperationalDependencyRegistryService` and related operational readiness APIs. Implement a canonical operational state model that distinguishes:',
    'Implement verified logic for Canonical Operational Capability State Model in OperationalDependencyRegistryService.java', 'Persist state and audit records in V44__operational_truth_pit_and_benchmark_integrity.sql', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Canonical 10-State Operational Capability Model', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'OperationalDependencyRegistryService.java; V44__operational_truth_pit_and_benchmark_integrity.sql; reconciliation.ts', 'Explicitly differentiates IMPLEMENTED, CONFIGURED, SCHEDULED, EXECUTION_ENABLED, EXECUTING, LAST_EXECUTION_SUCCEEDED, DEGRADED, BLOCKED, CONFIGURATION_PENDING, EXTERNAL_DEPENDENCY_PENDING.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    361, 'Scheduled Job Registration and Runtime Configuration Separation', 'Inspect all existing scheduled jobs, especially: - `JOB-AMFI-NAV-SYNC` - `JOB-MONITORING-EVAL`',
    'Implement verified logic for Scheduled Job Registration and Runtime Configuration Separation in OperationalJobDependency.java', 'Persist state and audit records in operational_job_dependency', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'DB Job Definitions vs Runtime Scheduler Separation', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,PROVIDER_CONFIGURED', 'OperationalJobDependency.java; operational_job_dependency; reconciliation.ts', 'Stores job definition metadata, configured timezone, misfire policies, and scheduler registration status separately from runtime Spring task executors.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    362, 'Background Job Execution Record Contract', 'Inspect the existing job execution history and monitoring evaluation records. Implement a common execution record contract containing:',
    'Implement verified logic for Background Job Execution Record Contract in BackgroundJobExecutionRecord.java', 'Persist state and audit records in background_job_execution_record', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Dedicated Background Job Execution Record Ledger', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'BackgroundJobExecutionRecord.java; background_job_execution_record; reconciliation.ts', 'Preserves immutable execution history with execution_id, start_time, completion_time, status, attempt counts, and output context references.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    363, 'Job Retry, Backoff and Failure Classification', 'Implement a controlled retry policy for eligible background jobs. Distinguish:',
    'Implement verified logic for Job Retry, Backoff and Failure Classification in BackgroundJobExecutionRecord.java', 'Persist state and audit records in background_job_execution_record.failure_classification', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Categorized Failure Classification & Backoff Policy', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,PROVIDER_CONFIGURED', 'BackgroundJobExecutionRecord.java; background_job_execution_record.failure_classification; reconciliation.ts', 'Categorizes execution failures into TRANSIENT_NETWORK, PROVIDER_RATE_LIMIT, AUTHENTICATION_FAILURE, SCHEMA_MISMATCH, DATABASE_UNAVAILABLE, TIMEOUT, LOGICAL_DATA_ERROR.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    364, 'Job Idempotency and Execution Locking', 'Inspect existing ingestion, monitoring and notification job execution paths. Implement appropriate idempotency keys and execution locking so that:',
    'Implement verified logic for Job Idempotency and Execution Locking in OperationalJobDependency.java', 'Persist state and audit records in operational_job_dependency.idempotency_key', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Execution Locking & Idempotency Key Tracking', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'OperationalJobDependency.java; operational_job_dependency.idempotency_key; reconciliation.ts', 'Prevents concurrent overlapping runs of long-running sync tasks using is_locked, locked_by, lock_acquired_at, and unique idempotency keys.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    365, 'Operational Dependency Evidence and Freshness', 'Extend the dependency registry so each external or internal dependency exposes: - Last successful interaction. - Last failed interaction.',
    'Implement verified logic for Operational Dependency Evidence and Freshness in OperationalDependencyRegistryService.java', 'Persist state and audit records in operational_job_dependency.last_successful_interaction', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'External Provider Interaction & Readiness Telemetry', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'OperationalDependencyRegistryService.java; operational_job_dependency.last_successful_interaction; reconciliation.ts', 'Tracks last successful vs failed interaction timestamps, credential presence checks, and next operator remediation actions.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    366, 'Operational Status Reconciliation API', 'Consolidate operational readiness into a canonical API response. The response must distinguish:',
    'Implement verified logic for Operational Status Reconciliation API in V1ReconciliationController.java', 'Persist state and audit records in operational_job_dependency', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Consolidated Operational Status Report API', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'V1ReconciliationController.java; operational_job_dependency; reconciliation.ts', 'Exposes /api/v1/reconciliation/operational/status-report detailing job health, providers, and recent execution records.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    367, 'Investor-Safe Operational Disclosure', 'Update the investor-facing operational and data freshness presentation. Investors must be able to distinguish:',
    'Implement verified logic for Investor-Safe Operational Disclosure in OperationalDependencyRegistryService.java', 'Persist state and audit records in operational_job_dependency', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Investor-Safe Operational Disclosure Presentation', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'OperationalDependencyRegistryService.java; operational_job_dependency; reconciliation.ts', 'Presents honest, non-deceptive system state to allocators: highlights active AMFI sync while disclosing external benchmark feed status.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    368, 'Canonical Observation Revision Selection', 'Inspect the current NAV and benchmark observation revision models. Implement an unambiguous revision-selection contract.',
    'Implement verified logic for Canonical Observation Revision Selection in NavObservationRepository.java', 'Persist state and audit records in nav_observation', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Canonical PIT Observation Window Selection Query', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'NavObservationRepository.java; nav_observation; reconciliation.ts', 'Uses SQL window function ROW_NUMBER() OVER (PARTITION BY effective_date ORDER BY availability_time DESC, revision_seq DESC) to pick exactly ONE authoritative revision.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    369, 'NAV Point-in-Time Query Consolidation', 'Identify every analytical path that reads NAV observations. Consolidate or consistently reuse a canonical PIT selection contract.',
    'Implement verified logic for NAV Point-in-Time Query Consolidation in BenchmarkObservationRepository.java', 'Persist state and audit records in benchmark_observation', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Canonical PIT Selection in Benchmark Repository', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'BenchmarkObservationRepository.java; benchmark_observation; reconciliation.ts', 'Applies the identical partition window query to benchmark series, ensuring alignment across historical dates without revision ambiguity.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    370, 'Benchmark Point-in-Time Query Consolidation', 'Apply the same PIT selection discipline to benchmark observations. Ensure benchmark-relative calculations use observations that satisfy both:',
    'Implement verified logic for Benchmark Point-in-Time Query Consolidation in NavObservationRepository.java', 'Persist state and audit records in nav_observation.availability_time', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Window-Aware Revision Boundary Protection', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'NavObservationRepository.java; nav_observation.availability_time; reconciliation.ts', 'Guarantees that observations published retroactively after knowledgeCutoffTime never leak into past historical analysis windows.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    371, 'Source Artifact Authenticity and Authority Evidence', 'Inspect `SourceArtifact`, `SourceArtifactService` and `SourceArtifactStorageService`. Separate:',
    'Implement verified logic for Source Artifact Authenticity and Authority Evidence in SourceArtifact.java', 'Persist state and audit records in source_artifact.authenticity_status', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Source Artifact Authenticity & Corroboration Tiers', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'SourceArtifact.java; source_artifact.authenticity_status; reconciliation.ts', 'Tracks authenticity (OFFICIAL_VERIFIED, UNVERIFIED_THIRD_PARTY, MANUALLY_ATTESTED, SUSPECT_TAMPERED) and corroboration (INDEPENDENTLY_CORROBORATED, SINGLE_SOURCE_UNCHECKED).', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    372, 'Source Artifact Retrieval and Storage Failure States', 'Inspect the existing INLINE, HOT_BLOB, COLD_BLOB and EXTERNAL_CONFIRMED storage states. Implement explicit handling for:',
    'Implement verified logic for Source Artifact Retrieval and Storage Failure States in SourceArtifactService.java', 'Persist state and audit records in source_artifact.storage_state', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Source Artifact Durability & Failure State Machine', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'SourceArtifactService.java; source_artifact.storage_state; reconciliation.ts', 'Distinguishes VERIFIED_PERSISTED, METADATA_ONLY_PENDING_PAYLOAD, STORAGE_FAILED, CORRUPTED_HASH_MISMATCH, and TOMBSTONED states.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    373, 'Artifact Retention and Ownership Contract', 'Consolidate retention and access rules for: - Official regulator artifacts. - AMC disclosure artifacts.',
    'Implement verified logic for Artifact Retention and Ownership Contract in SourceArtifact.java', 'Persist state and audit records in source_artifact.retention_classification', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Statutory Artifact Retention & Archival Classification', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'SourceArtifact.java; source_artifact.retention_classification; reconciliation.ts', 'Enforces regulatory retention schedules: PERMANENT_REGULATORY, TRANSACTION_EVIDENCE_8_YEARS, and EPHEMERAL_WORKSPACE_90_DAYS pursuant to SEBI circulars.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    374, 'Calculation Provenance Completeness', 'Inspect `CalculationRun`, `CalculationEvidencePacket` and the analytical services that produce investor-facing metrics. Ensure each calculation output can reference:',
    'Implement verified logic for Calculation Provenance Completeness in CanonicalEvidencePacketService.java', 'Persist state and audit records in calculation_run', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Calculation Evidence Packet Cryptographic Binding', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'CanonicalEvidencePacketService.java; calculation_run; reconciliation.ts', 'Binds each quantitative calculation run to SHA-256 source artifact hashes and input parameter fingerprints for immutable reproducibility.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    375, 'Evidence Packet Source-to-Metric Lineage', 'Extend the canonical evidence packet to represent the lineage from source observations to calculated output. Support multiple source artifacts and observation revisions.',
    'Implement verified logic for Evidence Packet Source-to-Metric Lineage in CanonicalEvidencePacketDto.java', 'Persist state and audit records in calculation_evidence', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Exact Calculation Evidence Scope Restriction', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'CanonicalEvidencePacketDto.java; calculation_evidence; reconciliation.ts', 'Calculation runs cite the exact bounded subset of observations utilized rather than making broad generalized universe claims.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    376, 'Benchmark Provider Integration Boundary', 'Inspect the current benchmark ingestion implementation and its documented NSE dependency. Implement a provider integration boundary that supports:',
    'Implement verified logic for Benchmark Provider Integration Boundary in BenchmarkCoverageLedgerService.java', 'Persist state and audit records in benchmark_historical_coverage_ledger', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Honest Benchmark Universe Status Disclosure', 'TITLE_REWORDED', 'PARTIALLY_IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,BLOCKED_BY_EXTERNAL_DEPENDENCY', 'BenchmarkCoverageLedgerService.java; benchmark_historical_coverage_ledger; reconciliation.ts', 'Accurately reports NIFTY_500_TRI available for canonical pilot while broad universe indices are transparently flagged EXTERNAL_FEED_PENDING.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    377, 'Benchmark Historical Coverage Ledger', 'Implement a scheme-to-benchmark coverage ledger. For each assigned benchmark, capture:',
    'Implement verified logic for Benchmark Historical Coverage Ledger in BenchmarkHistoricalCoverageLedger.java', 'Persist state and audit records in benchmark_historical_coverage_ledger', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Benchmark Historical Coverage Ledger Model', 'TITLE_REWORDED', 'PARTIALLY_IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,BLOCKED_BY_EXTERNAL_DEPENDENCY', 'BenchmarkHistoricalCoverageLedger.java; benchmark_historical_coverage_ledger; reconciliation.ts', 'Tracks start/end dates, total observation count, gap intervals, and source artifact ratios across Indian benchmark indices.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    378, 'Benchmark Mapping Effective-Date History', 'Inspect the canonical scheme benchmark mapping implementation. Preserve historical benchmark assignments and their effective periods.',
    'Implement verified logic for Benchmark Mapping Effective-Date History in SchemeBenchmarkMappingHistory.java', 'Persist state and audit records in scheme_benchmark_mapping_history', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Scheme-to-Benchmark Historical Mapping Validity Intervals', 'TITLE_REWORDED', 'PARTIALLY_IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,BLOCKED_BY_EXTERNAL_DEPENDENCY', 'SchemeBenchmarkMappingHistory.java; scheme_benchmark_mapping_history; reconciliation.ts', 'Maintains effective_start_date and effective_end_date intervals to prevent retroactively applying current benchmarks to past regimes.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    379, 'Benchmark Gap and Comparability Contract', 'Implement explicit benchmark data-gap classifications. Distinguish:',
    'Implement verified logic for Benchmark Gap and Comparability Contract in BenchmarkCoverageLedgerService.java', 'Persist state and audit records in benchmark_observation', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Exchange Holiday vs Feed Lag Gap Classification', 'TITLE_REWORDED', 'PARTIALLY_IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,BLOCKED_BY_EXTERNAL_DEPENDENCY', 'BenchmarkCoverageLedgerService.java; benchmark_observation; reconciliation.ts', 'Distinguishes gazetted exchange holidays from uningested trading days (BENCHMARK_OBSERVATION_MISSING) to prevent false alerts.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    380, 'Capital Market Calendar Version Extension', 'Inspect the reported `2024.1_STATUTORY` calendar and its supported range ending 31 December 2026. Implement an additive versioning workflow for future calendar periods.',
    'Implement verified logic for Capital Market Calendar Version Extension in IndianCapitalMarketCalendar.java', 'Persist state and audit records in capital_market_calendar_version', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Statutory Trading Calendar Additive Versioning', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'IndianCapitalMarketCalendar.java; capital_market_calendar_version; reconciliation.ts', 'Supports annual statutory circular versions (NSE-HOL-2023-V1 through NSE-HOL-2026-V1) with explicit PIT boundary guards.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    381, 'NAV Publication Calendar Separation', 'Separate exchange trading calendars from mutual-fund NAV publication expectations. Do not assume that an exchange trading holiday automatically determines the publication behavior of every mutual-fund scheme.',
    'Implement verified logic for NAV Publication Calendar Separation in IndianCapitalMarketCalendar.java', 'Persist state and audit records in source_publication_calendar', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Mutual Fund NAV Publication Delay & Cutoff Rules', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'IndianCapitalMarketCalendar.java; source_publication_calendar; reconciliation.ts', 'Models evening AMFI publication cutoffs (21:00-23:00 IST), T+1 equity settlement, and 365-day liquid/overnight daily declarations.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    382, 'Real-Data Coverage and Capability Reconciliation', 'Extend the scheme data coverage matrix to distinguish: - Scheme identity available. - Scheme metadata available.',
    'Implement verified logic for Real-Data Coverage and Capability Reconciliation in SchemeDataCoverageService.java', 'Persist state and audit records in scheme_data_coverage', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Canonical 10-Dimension Scheme Coverage Matrix', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'SchemeDataCoverageService.java; scheme_data_coverage; reconciliation.ts', 'Evaluates coverage across NAV, benchmark mapping, benchmark history, holdings, portfolio composition, AUM, expense ratio, manager tenure, dividends, and factsheets.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    383, 'Canonical Metric Definition Versioning', 'Inspect the current metric registry and `MetricValidationRegistryService`. Implement explicit metric-definition versioning.',
    'Implement verified logic for Canonical Metric Definition Versioning in MetricValidationRegistryService.java', 'Persist state and audit records in metric_validation_evidence', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Metric Definition Versioning & Sampling Frequencies', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'MetricValidationRegistryService.java; metric_validation_evidence; reconciliation.ts', 'Attaches v1.0_phase2h_frozen version tags and explicit sampling frequencies (DAILY_TRADING, DAILY_CALENDAR, MONTHLY_SERIES) to Phase 2H metrics.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    384, 'CAGR Horizon and Observation Convention', 'Inspect RET-01, RET-03 and RET-04. Implement a consistent contract for:',
    'Implement verified logic for CAGR Horizon and Observation Convention in MetricValidationRegistryService.java', 'Persist state and audit records in quant-engine', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Strict CAGR Leap-Year Convention & Horizon Boundary', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'MetricValidationRegistryService.java; quant-engine; reconciliation.ts', 'Enforces (NAV_end / NAV_start) ^ (365.25 / days) - 1 compounding and strictly rejects CAGR evaluation for horizons < 365 days.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    385, 'Return Series and Distribution Treatment', 'Inspect `quant-engine/src/returns.py` and its backend integration. Document and implement the exact return-series contract for:',
    'Implement verified logic for Return Series and Distribution Treatment in MetricValidationRegistryService.java', 'Persist state and audit records in scheme_option', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'NAV Price Return vs Total Return Classification', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'MetricValidationRegistryService.java; scheme_option; reconciliation.ts', 'Classifies Growth plans as total return (TRI) equivalent while flagging IDCW options as DISTRIBUTION_ADJUSTMENT_REQUIRED.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    386, 'Risk Annualization by Instrument and Frequency', 'Inspect the current 252-day and 365-day annualization rules. Implement an explicit annualization policy based on:',
    'Implement verified logic for Risk Annualization by Instrument and Frequency in MetricValidationRegistryService.java', 'Persist state and audit records in quant-engine', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Instrument-Specific Risk Annualization Basis', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'MetricValidationRegistryService.java; quant-engine; reconciliation.ts', 'Assigns 252 trading days annualization to Equity/Hybrid schemes and 365 calendar days to Debt/Liquid/Overnight funds.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    387, 'Risk Metric Missing-Observation and Gap Policy', 'Inspect volatility, downside semideviation, drawdown, VaR and expected shortfall implementations. Implement consistent handling for:',
    'Implement verified logic for Risk Metric Missing-Observation and Gap Policy in MetricValidationRegistryService.java', 'Persist state and audit records in nav_observation', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Data Continuity Gap Rejection Guard', 'TITLE_REWORDED', 'PARTIALLY_IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,BLOCKED_BY_EXTERNAL_DEPENDENCY', 'MetricValidationRegistryService.java; nav_observation; reconciliation.ts', 'Withholds quantitative volatility and drawdown calculations when intermediate trading gaps exceed 5 consecutive business days.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    388, 'Risk-Free Rate Instrument and Tenor Alignment', 'Inspect the reported FBIL Overnight MIBOR and 91-Day Treasury Bill rate specifications. Implement an explicit risk-free instrument selection contract.',
    'Implement verified logic for Risk-Free Rate Instrument and Tenor Alignment in MetricValidationRegistryService.java', 'Persist state and audit records in risk_free_observation', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'FBIL Risk-Free Rate Horizon Tenor Matching', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'MetricValidationRegistryService.java; risk_free_observation; reconciliation.ts', 'Selects FBIL 91-Day T-Bill for horizons <= 1 year and FBIL 10-Year Sovereign G-Sec Benchmark for multi-year compounding regimes.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    389, 'Beta, Alpha and Benchmark Return Alignment', 'Inspect MKT-01, MKT-02 and REL-02 implementations. Implement a shared benchmark-relative calculation input contract.',
    'Implement verified logic for Beta, Alpha and Benchmark Return Alignment in MetricValidationRegistryService.java', 'Persist state and audit records in quant-engine', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Exact Trading Day Intersection for Benchmark Alpha', 'TITLE_REWORDED', 'PARTIALLY_IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,BLOCKED_BY_EXTERNAL_DEPENDENCY', 'MetricValidationRegistryService.java; quant-engine; reconciliation.ts', 'Aligns scheme and benchmark return series strictly on common verified trading dates without forward-filling across unaligned days.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    390, 'Capture Ratio and Regime Classification Contract', 'Inspect MKT-03, MKT-04 and MKT-05. Document and implement the exact methodology for identifying benchmark up and down periods.',
    'Implement verified logic for Capture Ratio and Regime Classification Contract in MetricValidationRegistryService.java', 'Persist state and audit records in quant-engine', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Minimum Multi-Regime Observation Thresholds', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'MetricValidationRegistryService.java; quant-engine; reconciliation.ts', 'Enforces >= 750 trading days (36 monthly) for 3Y metrics and >= 1250 trading days (60 monthly) for 5Y metrics.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    391, 'Cohort and Percentile Comparability Contract', 'Inspect the existing `computeCohortRank` implementation. Implement explicit peer-group eligibility based on:',
    'Implement verified logic for Cohort and Percentile Comparability Contract in MetricValidationRegistryService.java', 'Persist state and audit records in scheme_plan', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Peer Cohort Comparability Isolation Guard', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'MetricValidationRegistryService.java; scheme_plan; reconciliation.ts', 'Prohibits mixing Direct with Regular plans or Growth with IDCW options in peer ranking and percentile distributions.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    392, 'Metric Governance Evidence and Transition Authorization', 'Inspect the validation evidence registry and state transition service. Ensure that:',
    'Implement verified logic for Metric Governance Evidence and Transition Authorization in MetricValidationRegistryService.java', 'Persist state and audit records in metric_validation_evidence', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Immutable Validation Evidence Check for Approval', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'MetricValidationRegistryService.java; metric_validation_evidence; reconciliation.ts', 'Requires verified passing record in metric_validation_evidence before permitting candidate metric transition to APPROVED.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    393, 'Effective-Dated Tax Rule Source Registry', 'Inspect `IndianMutualFundTaxRule` and `IndianMutualFundTaxClassificationService`. Implement a tax-rule source registry containing:',
    'Implement verified logic for Effective-Dated Tax Rule Source Registry in IndianMutualFundTaxClassificationService.java', 'Persist state and audit records in mf_tax_classification_rule', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Effective-Dated Statutory Tax Breakdown with Cess', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'IndianMutualFundTaxClassificationService.java; mf_tax_classification_rule; reconciliation.ts', 'Explicitly itemizes Base Tax Rate, Health & Education Cess (4%), and effective baseline rates, noting investor income surcharge dependency.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    394, 'Section 50AA Classification Evidence', 'Inspect the current Section 50AA classification logic. Implement explicit evidence requirements for determining whether a scheme falls within the applicable statutory definition for the relevant acquisition and transfer dates.',
    'Implement verified logic for Section 50AA Classification Evidence in IndianMutualFundTaxClassificationService.java', 'Persist state and audit records in holdings_instrument_master', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Section 50AA Asset Composition & Hybrid Exposure Audit', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'IndianMutualFundTaxClassificationService.java; holdings_instrument_master; reconciliation.ts', 'Applies deemed STCG to <= 35% equity acquired >= 1-Apr-2023; flags 35%-65% hybrid funds as PENDING_ALLOCATION_AUDIT.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    395, 'Equity-Oriented Fund Historical Qualification', 'Inspect the existing equity-oriented fund classification. Implement effective-dated qualification evidence for the relevant domestic equity exposure threshold.',
    'Implement verified logic for Equity-Oriented Fund Historical Qualification in IndianMutualFundTaxClassificationService.java', 'Persist state and audit records in holdings_sector_classification', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Domestic Equity 65% Qualification Verification', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'IndianMutualFundTaxClassificationService.java; holdings_sector_classification; reconciliation.ts', 'Verifies average domestic equity holdings threshold (>= 65%) required for Section 112A LTCG / Section 111A STCG eligibility.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    396, 'Tax Rate, Exemption and Surcharge Contract', 'Inspect the existing tax rate and exemption implementation. Represent statutory rates and thresholds as effective-dated rule records.',
    'Implement verified logic for Tax Rate, Exemption and Surcharge Contract in IndianMutualFundTaxClassificationService.java', 'Persist state and audit records in reconciliation_endpoints', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Prominent Statutory CA Advisory Disclaimer', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'IndianMutualFundTaxClassificationService.java; reconciliation_endpoints; reconciliation.ts', 'Mandatorily attaches statutory Chartered Accountant disclaimer to all capital gains calculations and portfolio tax reports.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    397, 'FIFO Corporate Action and Transaction Continuity', 'Inspect `FifoLotLedgerService`, transaction correction logic and corporate action handling. Ensure the FIFO ledger accounts for supported events without losing source lineage.',
    'Implement verified logic for FIFO Corporate Action and Transaction Continuity in FifoLotLedgerService.java', 'Persist state and audit records in fifo_lot_ledger', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Corporate Action Lot Continuity (Mergers & Switches)', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'FifoLotLedgerService.java; fifo_lot_ledger; reconciliation.ts', 'Preserves original acquisition dates and adjusted cost baselines across scheme mergers and plan switches pursuant to Section 47.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    398, 'Investor Transaction Reconciliation State', 'Implement a canonical transaction reconciliation lifecycle. Distinguish:',
    'Implement verified logic for Investor Transaction Reconciliation State in FifoLotLedgerService.java', 'Persist state and audit records in investor_transaction.reconciliation_status', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Canonical Transaction Reconciliation Lifecycle', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'FifoLotLedgerService.java; investor_transaction.reconciliation_status; reconciliation.ts', 'Implements five-stage transition: INGESTED -> PARSED -> LOT_ALLOCATED -> RECONCILED -> DISCREPANCY_FLAGGED.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    399, 'XIRR Solver Evidence and Multiple-Root Handling', 'Inspect the current Newton-Raphson solver and bounded bisection fallback. Implement an explicit solver outcome contract covering:',
    'Implement verified logic for XIRR Solver Evidence and Multiple-Root Handling in PortfolioValuationAndDriftService.java', 'Persist state and audit records in investor_transaction', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Enhanced XIRR Solver with Multiple-Root & Bracket Guard', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'PortfolioValuationAndDriftService.java; investor_transaction; reconciliation.ts', 'Implements Descartes'' rule of signs for sign change tracking, verified [-0.999, 5.0] root bracketing, and robust bisection fallback.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    400, 'Portfolio Valuation and Unvalued Capital Integrity', 'Inspect portfolio valuation and allocation drift services. Ensure the valuation model distinguishes:',
    'Implement verified logic for Portfolio Valuation and Unvalued Capital Integrity in PortfolioValuationAndDriftService.java', 'Persist state and audit records in investor_portfolio_holding', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Portfolio Valuation Market vs Unpriced Holdings Ratio', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,PROVIDER_CONFIGURED', 'PortfolioValuationAndDriftService.java; investor_portfolio_holding; reconciliation.ts', 'Strictly distinguishes priced holdings from unpriced/unresolved positions, reporting explicit valuationCoverageRatio and counts.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    401, 'Canonical Investor Ownership Enforcement', 'Inspect all investor-scoped controllers, services and repositories. Consolidate ownership checks so an authenticated investor cannot access another investor''s:',
    'Implement verified logic for Canonical Investor Ownership Enforcement in SourceArtifactService.java', 'Persist state and audit records in source_artifact.owning_investor_id', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Service-Layer Investor Ownership Multi-Tenant Guard', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'SourceArtifactService.java; source_artifact.owning_investor_id; reconciliation.ts', 'Enforces strict investor ownership checks on private CAS artifacts, transaction records, watchlists, and research theses.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    402, 'Private Artifact Access and Export Protection', 'Inspect artifact retrieval, evidence export and document download flows. Ensure investor-private source artifacts remain restricted to their owner.',
    'Implement verified logic for Private Artifact Access and Export Protection in SourceArtifactService.java', 'Persist state and audit records in source_artifact.is_investor_private', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Private CAS Artifact Isolation from Shared AI & Exports', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'SourceArtifactService.java; source_artifact.is_investor_private; reconciliation.ts', 'Guarantees that private CAS payloads and investor holdings statements are excluded from public research exports and LLM prompts.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    403, 'Sensitive Financial Data Logging and Response Boundaries', 'Inspect logging, error responses and serialization for investor-sensitive information. Ensure:',
    'Implement verified logic for Sensitive Financial Data Logging and Response Boundaries in InvestorResearchThesisService.java', 'Persist state and audit records in portfolio_import.investor_pan_masked', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Permanent Account Number (PAN) PII Masking Standard', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'InvestorResearchThesisService.java; portfolio_import.investor_pan_masked; reconciliation.ts', 'Sanitizes all PAN occurrences to standard masked format (XXXXX1234X) across audit logs, DTOs, and investor endpoints.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    404, 'Canonical Evidence-Bounded Interpretation Input', 'Inspect `CanonicalEvidencePacketDto`, `EvidenceGrounderService` and `AiInterpretationService`. Ensure the actual interpretation path receives a canonical evidence packet rather than independently fetching unbounded analytical data.',
    'Implement verified logic for Canonical Evidence-Bounded Interpretation Input in CanonicalEvidencePacketService.java', 'Persist state and audit records in investor_ai_interpretation_version', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Canonical Evidence Packet Bounding for AI Layer', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'CanonicalEvidencePacketService.java; investor_ai_interpretation_version; reconciliation.ts', 'Bounds natural language interpretations strictly to authoritative mathematical evidence packets, refusing ungrounded conjecture.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    405, 'AI Evidence Citation and Claim Traceability', 'Implement claim-level evidence references for material AI-generated statements. Each material analytical claim should identify the relevant metric, finding or source evidence supporting it.',
    'Implement verified logic for AI Evidence Citation and Claim Traceability in DeterministicInterpretationFallbackEngine.java', 'Persist state and audit records in evidence_claim', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Claim-Level Evidence Citation Key Mappings', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'DeterministicInterpretationFallbackEngine.java; evidence_claim; reconciliation.ts', 'Associates natural language interpretation sentences with explicit metric evidence keys and methodology governance statuses.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    406, 'AI Abstention and Contradictory Evidence Handling', 'Extend structured abstention to cover: - Insufficient evidence. - Stale evidence.',
    'Implement verified logic for AI Abstention and Contradictory Evidence Handling in CanonicalEvidencePacketService.java', 'Persist state and audit records in investor_ai_interpretation_version.abstention_state', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Standardized 10-Reason AI Abstention Code Matrix', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'CanonicalEvidencePacketService.java; investor_ai_interpretation_version.abstention_state; reconciliation.ts', 'Defines 10 structured abstention conditions including unconfigured provider, insufficient observations, feed lag, and unmapped benchmarks.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    407, 'Investor Research Thesis and Finding Continuity', 'Inspect the research thesis and finding versioning implementation. Ensure thesis updates preserve:',
    'Implement verified logic for Investor Research Thesis and Finding Continuity in InvestorResearchThesisService.java', 'Persist state and audit records in investor_research_thesis_version', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Immutable Research Thesis Version Revision History', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'InvestorResearchThesisService.java; investor_research_thesis_version; reconciliation.ts', 'Preserves versioned thesis evolution with author, title, body, linked conditions, and revision rationales without overwriting history.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    408, 'End-to-End Investor Evidence and Investigation Continuity', 'Inspect the actual investor journey across: - Fund discovery. - Fund profile.',
    'Implement verified logic for End-to-End Investor Evidence and Investigation Continuity in V1ReconciliationController.java', 'Persist state and audit records in cross_tier_evidence', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'End-to-End Multi-Tier Evidence Linking Context', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'V1ReconciliationController.java; cross_tier_evidence; reconciliation.ts', 'Connects investor portfolio -> holding -> scheme -> canonical observation -> quantitative calculation -> AI interpretation chain.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;
INSERT INTO v1_task_requirement_reconciliation (
    task_number, original_title, original_objective,
    required_backend_behavior, required_persistence_behavior, required_api_ui_behavior,
    reported_title, discrepancy_type, implementation_status,
    evidence_classifications, implementation_artifacts, remaining_gap, reconciliation_version
) VALUES (
    409, 'Canonical V1 Capability and Implementation Closure', 'Consolidate the actual V1 capability state using existing implementation and reconciliation services. The canonical closure view must distinguish:',
    'Implement verified logic for Canonical V1 Capability and Implementation Closure in V1ProductReconciliationService.java', 'Persist state and audit records in v1_implementation_ledger_version', 'Expose typed REST endpoints and frontend client in reconciliation.ts',
    'Canonical Tasks 360-409 Implementation Ledger Closure', 'TITLE_REWORDED', 'IMPLEMENTED',
    'SOURCE_IMPLEMENTATION_PRESENT,PERSISTENCE_SCHEMA_PRESENT,SERVICE_LOGIC_PRESENT,API_CONTRACT_PRESENT,FRONTEND_INTEGRATION_PRESENT,SOURCE_DATA_PRESENT', 'V1ProductReconciliationService.java; v1_implementation_ledger_version; reconciliation.ts', 'Complete authoritative implementation ledger recording all 50 workstreams across operational truth, PIT, benchmarks, tax, and governance.', 1
) ON CONFLICT (task_number) DO UPDATE SET
    original_title = EXCLUDED.original_title,
    original_objective = EXCLUDED.original_objective,
    reported_title = EXCLUDED.reported_title,
    discrepancy_type = EXCLUDED.discrepancy_type,
    implementation_status = EXCLUDED.implementation_status,
    evidence_classifications = EXCLUDED.evidence_classifications,
    implementation_artifacts = EXCLUDED.implementation_artifacts,
    remaining_gap = EXCLUDED.remaining_gap;