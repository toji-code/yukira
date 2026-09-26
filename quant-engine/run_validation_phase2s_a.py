"""Executable script for Phase 2S-A validation suite and evidence dossier generation."""

import os
import sys

# Ensure quant-engine root is in sys.path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from validation.harness import Phase2SAValidationHarness
from validation.dossier import generate_dossier_markdown, generate_dossier_json

def main():
    print("=== Executing Phase 2S-A Quantitative Validation Harness ===")
    harness = Phase2SAValidationHarness(seed=42)

    # 1. Execute complete suite across all 6 representative metrics
    summaries = harness.execute_phase2s_a_full_suite()

    print("\n--- Validation Execution Summary ---")
    for code, summary in summaries.items():
        print(f"Metric: {code:<8} | Overall Outcome: {summary.overall_outcome.value:<12} | Records: {len(summary.records)}")
        for rec in summary.records:
            p_val = f"{rec.production_value:.6f}" if isinstance(rec.production_value, (int, float)) else str(rec.production_value or "—")
            r_val = f"{rec.reference_value:.6f}" if isinstance(rec.reference_value, (int, float)) else str(rec.reference_value or "—")
            print(f"   [{rec.test_type.value:<28}] -> {rec.outcome.value:<12} | Prod: {p_val} | Ref: {r_val}")

    # 2. Generate Dossier
    base_dir = os.path.dirname(os.path.abspath(__file__))
    output_dir = os.path.abspath(os.path.join(base_dir, "..", "docs", "research", "validation"))
    os.makedirs(output_dir, exist_ok=True)

    dossier_md_path = os.path.join(output_dir, "PHASE_2S_A_VALIDATION_DOSSIER.md")
    dossier_json_path = os.path.join(output_dir, "phase2s_a_validation_evidence.json")

    md_content = generate_dossier_markdown(summaries)
    json_content = generate_dossier_json(summaries)

    with open(dossier_md_path, "w", encoding="utf-8") as f:
        f.write(md_content)
    with open(dossier_json_path, "w", encoding="utf-8") as f:
        f.write(json_content)

    print(f"\n[OK] Dossier written to: {dossier_md_path}")
    print(f"[OK] Structured JSON written to: {dossier_json_path}")

if __name__ == "__main__":
    main()
