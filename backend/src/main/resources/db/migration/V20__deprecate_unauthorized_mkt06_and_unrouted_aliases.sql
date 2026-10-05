-- V20: Deprecate unauthorized MKT-06 and unrouted legacy aliases
--
-- In accordance with Phase 2H frozen quantitative methodology:
-- 1. MKT-06 (Upside Beta) is unauthorized because it does not exist in Phase 2H methodology.
-- 2. REL-04 (legacy Downside Beta alias superseded by MKT-02) is unrouted and deprecated.
-- 3. REL-05 (legacy Upside Beta alias) is unrouted and deprecated.
-- 4. REL-06 (legacy Active Return alias superseded by REL-03) is unrouted and deprecated.
--
-- Under append-only repository governance and zero-destructive DDL/DML rules:
-- Historical records are preserved. Active analytical queries filtering on canonical
-- dimensions or excluding 'DEPRECATED' will not encounter these metrics.

UPDATE metric_definition
SET analytical_dimension = 'DEPRECATED',
    metric_category = 'DEPRECATED',
    purpose = 'DEPRECATED: Unauthorized metric not present in frozen Phase 2H methodology.'
WHERE metric_code = 'MKT-06';

UPDATE metric_definition
SET analytical_dimension = 'DEPRECATED',
    metric_category = 'DEPRECATED',
    purpose = 'DEPRECATED: Unrouted alias. Superseded by canonical MKT-02.'
WHERE metric_code = 'REL-04';

UPDATE metric_definition
SET analytical_dimension = 'DEPRECATED',
    metric_category = 'DEPRECATED',
    purpose = 'DEPRECATED: Unrouted alias for unauthorized upside beta.'
WHERE metric_code = 'REL-05';

UPDATE metric_definition
SET analytical_dimension = 'DEPRECATED',
    metric_category = 'DEPRECATED',
    purpose = 'DEPRECATED: Unrouted alias. Superseded by canonical REL-03.'
WHERE metric_code = 'REL-06';

-- Retire candidate methodology version rows associated with unrouted legacy aliases
UPDATE methodology_version
SET lifecycle_status = 'RETIRED'
WHERE metric_definition_id IN (
    SELECT id FROM metric_definition WHERE metric_code IN ('REL-04', 'REL-05')
);
