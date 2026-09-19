-- Phase 2F: Pass 2 Correction - Allow Benchmark-Free Calculation Runs
-- RET-02 (Simple Period Return) and single-asset quantitative metrics do not mathematically require a benchmark.
-- Making calculation_run.benchmark_id NULLABLE allows downstream consumers to structurally distinguish:
-- 1. "benchmark not required / standalone calculation" (benchmark_id IS NULL)
-- 2. "benchmark exists and was used" (benchmark_id IS NOT NULL)
-- without creating or relying on synthetic placeholder benchmark entities.

ALTER TABLE calculation_run ALTER COLUMN benchmark_id DROP NOT NULL;
