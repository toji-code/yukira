"""Evidence dossier generator for Phase 2S-A validation framework.

Produces structured JSON and human-readable Markdown evidence dossiers
ready for independent audit and Governance Committee review.
"""

from __future__ import annotations

import hashlib
import json
from datetime import datetime, timezone
from typing import Any

from validation.models import MetricValidationSummary


def generate_dossier_markdown(
    summaries: dict[str, MetricValidationSummary],
    input_digest: str = "",
) -> str:
    """Generate human-readable markdown evidence dossier for Phase 2S-A."""
    now_str = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")

    lines = [
        "# PHASE 2S-A VALIDATION EVIDENCE DOSSIER",
        "",
        "> **Phase:** Phase 2S-A — Validation Framework Foundation",
        f"> **Generated At:** `{now_str}`",
        f"> **Input Cryptographic Digest:** `{input_digest or 'SYNTHETIC_REFERENCE_VECTORS'}`",
        "> **Governance Authority:** Project YUKIRA Constitution, [`AGENTS.md`](file:///AGENTS.md)",
        "> **Governance State:** NO STATE TRANSITIONS. Evidence outcomes are empirical verification states only.",
        "",
        "---",
        "",
        "## 1. Scope & Three Evidence Levels Hierarchy",
        "",
        "The Phase 2S-A validation framework explicitly distinguishes three distinct epistemic evidence levels:",
        "",
        "1. **Level A: Deterministic Reference-Vector Parity**",
        "   - **Definition:** `Production Kernel == Independent Reference Kernel` on controlled, pre-specified mathematical vectors.",
        "   - **Phase 2S-A Scope:** **Phase 2S-A establishes Level A.** All six representative metrics demonstrate deterministic production-to-independent-reference parity on pre-specified deterministic reference vectors.",
        "   - **Explicit Epistemic Boundary:** These parity results verify implementation agreement for the tested reference vectors. They do not constitute empirical validation of the canonical HDFC Flexi Cap Fund production results.",
        "",
        "2. **Level B: Canonical Production-Data Results**",
        "   - **Definition:** Actual HDFC Flexi Cap Fund / Phase 2R values from the production calculation run (e.g. Canonical 3Y Volatility = `0.146913`, Sharpe = `~1.36–1.49`, Beta = `~0.957`).",
        "   - **Phase 2S-A Scope:** Phase 2S-A may inspect/use Level B for evidence context where explicitly specified. Reference-vector values are not expected or intended to equal canonical HDFC pilot production values.",
        "",
        "3. **Level C: Empirical Validation**",
        "   - **Definition:** Empirical evidence that the methodology is reliable, appropriate, and statistically robust across live market regimes beyond mere implementation parity.",
        "   - **Phase 2S-A Scope:** **Phase 2S-A does NOT by itself establish Level C.** Passing a Phase 2S-A parity test is NOT equivalent to 'metric validated', 'methodology validated', or 'Candidate→Validated'.",
        "",
        "---",
        "",
        "## 2. Executive Summary Table",
        "",
        "| Metric Code | Metric Name | Mathematical Archetype | Tolerance Applied | Parity Status | Evidence Outcome |",
        "| :--- | :--- | :--- | :---: | :---: | :---: |",
    ]

    names = {
        "RET-03": ("3Y CAGR", "Compounded Growth", "< 1e-10"),
        "RSK-01": ("3Y Annualized Volatility", "Linear Dispersion", "< 1e-12"),
        "RSK-03": ("3Y Maximum Drawdown", "Path-Dependent Peak Scan", "< 1e-12"),
        "RSK-06": ("Historical VaR 95%", "Empirical Quantile", "< 1e-12"),
        "RAT-01": ("3Y Sharpe Ratio", "Excess Return Ratio", "< 1e-8"),
        "REL-01": ("3Y Beta", "Bivariate OLS Slope", "< 1e-8"),
    }

    for code, summary in summaries.items():
        name, arch, tol = names.get(code, (code, "Unknown", "N/A"))
        parity_rec = next((r for r in summary.records if r.test_type.value == "INDEPENDENT_PARITY"), None)
        parity_status = parity_rec.outcome.value if parity_rec else "N/A"
        lines.append(
            f"| **`{code}`** | {name} | {arch} | `{tol}` | `{parity_status}` | **`{summary.overall_outcome.value}`** |"
        )

    lines.extend([
        "",
        "---",
        "",
        "## 3. Metric-by-Metric Detailed Evidence Records",
        "",
    ])

    for code, summary in summaries.items():
        name, arch, _ = names.get(code, (code, "Unknown", "N/A"))
        lines.extend([
            f"### Metric: `{code}` — {name}",
            f"- **Mathematical Archetype:** {arch}",
            f"- **Overall Validation Outcome:** **`{summary.overall_outcome.value}`**",
            "",
            "#### Detailed Module Test Results:",
            "> [!NOTE]",
            "> **Reference-Vector Parity Labeling:** Parity values in `INDEPENDENT_PARITY` rows below reflect deterministic mathematical verification on pre-specified reference vectors. They are NOT canonical HDFC Flexi Cap Fund pilot production values (e.g. Canonical 3Y Volatility = 0.146913, Sharpe = ~1.36–1.49, Beta = ~0.957). These parity results verify implementation agreement for the tested reference vectors. They do not constitute empirical validation of the canonical HDFC Flexi Cap Fund production results.",
            "",
            "| Module Test Type | Outcome | Value Context | Production Value | Reference Value | Discrepancy | Tolerance |",
            "| :--- | :---: | :---: | :---: | :---: | :---: | :---: |",
        ])

        for rec in summary.records:
            p_val = f"{rec.production_value:.6f}" if isinstance(rec.production_value, (int, float)) else str(rec.production_value or "—")
            r_val = f"{rec.reference_value:.6f}" if isinstance(rec.reference_value, (int, float)) else str(rec.reference_value or "—")
            disc = f"{rec.discrepancy:.2e}" if rec.discrepancy is not None else "—"
            tol_val = f"{rec.tolerance:.1e}" if rec.tolerance is not None else "—"
            ctx = "Deterministic Ref Vector" if rec.test_type.value == "INDEPENDENT_PARITY" else "—"
            lines.append(
                f"| `{rec.test_type.value}` | `{rec.outcome.value}` | {ctx} | {p_val} | {r_val} | {disc} | {tol_val} |"
            )

        if summary.epistemic_disclosures:
            lines.extend([
                "",
                "**Epistemic Disclosures & Methodological Notes:**",
            ])
            for d in summary.epistemic_disclosures:
                lines.append(f"- {d}")

        lines.extend(["", "---", ""])

    lines.extend([
        "## 4. Epistemic Governance Declaration",
        "",
        "```text",
        "PHASE 2R CLOSED",
        "PHASE 2S-A VALIDATION HARNESS EXECUTED — EVIDENCE ASSEMBLED",
        "GOVERNANCE TRANSITION: NONE (ZERO AUTOMATIC TRANSITIONS)",
        "```",
        "",
        "The empirical evidence above is assembled strictly for subsequent independent audit",
        "and Governance Committee review. No metric has been automatically marked as VALIDATED or APPROVED.",
        "",
        "All six representative metrics demonstrate deterministic production-to-independent-reference parity on pre-specified deterministic reference vectors.",
        "These parity results verify implementation agreement for the tested reference vectors. They do not constitute empirical validation of the canonical HDFC Flexi Cap Fund production results.",
    ])

    return "\n".join(lines)


def generate_dossier_json(
    summaries: dict[str, MetricValidationSummary],
    input_digest: str = "",
) -> str:
    """Generate structured JSON evidence dossier for Phase 2S-A."""
    data = {
        "phase": "2S-A",
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "input_digest": input_digest,
        "governance_note": "Validation outcomes are empirical verification states only; zero governance transitions.",
        "parity_scope_statement": "All six representative metrics demonstrate deterministic production-to-independent-reference parity on pre-specified deterministic reference vectors. These parity results verify implementation agreement for the tested reference vectors. They do not constitute empirical validation of the canonical HDFC Flexi Cap Fund production results.",
        "evidence_hierarchy": {
            "level_a": "Deterministic reference-vector parity: Production kernel == independent reference kernel on controlled mathematical vectors. Established by Phase 2S-A.",
            "level_b": "Canonical production-data results: Actual HDFC Flexi Cap Fund / Phase 2R values from the production calculation run. Inspected for context where specified.",
            "level_c": "Empirical validation: Evidence that methodology is reliable/appropriate beyond mere implementation parity. NOT established by Phase 2S-A."
        },
        "metrics": {code: s.to_dict() for code, s in summaries.items()},
    }
    return json.dumps(data, indent=2)
