"""Point-in-Time (PIT) & Provenance verifier for Phase 2S-A validation.

CORE EPISTEMIC DIRECTIVE:
Every validation calculation must respect strict bitemporal isolation:
1. effective_date <= analysis_cutoff
2. availability_time <= knowledge_cutoff
Validation MUST NOT use data unavailable at the declared knowledge cutoff.
"""

from __future__ import annotations

import hashlib
import json
from datetime import datetime, date
from typing import Any, Sequence


def compute_provenance_digest(records: Sequence[dict[str, Any]]) -> str:
    """Compute deterministic SHA-256 digest of input observation records."""
    normalized = []
    for r in records:
        entry = {
            "date": str(r.get("date", r.get("effective_date", ""))),
            "value": float(r.get("value", r.get("nav", 0.0))),
            "avail": str(r.get("availability_time", "")),
        }
        normalized.append(entry)
    payload = json.dumps(normalized, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def _parse_iso_date(val: str | date | datetime) -> date:
    if isinstance(val, datetime):
        return val.date()
    if isinstance(val, date):
        return val
    return date.fromisoformat(str(val).split("T")[0])


def _parse_iso_datetime(val: str | datetime) -> datetime:
    if isinstance(val, datetime):
        return val
    text = str(val)
    # Handle simple date strings as end-of-day
    if "T" not in text and " " not in text:
        text = f"{text}T23:59:59+00:00"
    return datetime.fromisoformat(text)


def filter_by_pit_contract(
    observations: Sequence[dict[str, Any]],
    analysis_cutoff: str,
    knowledge_cutoff: str,
) -> list[dict[str, Any]]:
    """
    Filter observations strictly adhering to the YUKIRA bitemporal PIT query contract:
        effective_date <= analysis_cutoff
        AND availability_time <= knowledge_cutoff
    """
    analysis_d = _parse_iso_date(analysis_cutoff)
    knowledge_dt = _parse_iso_datetime(knowledge_cutoff)

    valid_records = []
    for obs in observations:
        eff_date = _parse_iso_date(obs.get("date", obs.get("effective_date", "1900-01-01")))
        avail_raw = obs.get("availability_time", "1900-01-01T00:00:00+00:00")
        avail_dt = _parse_iso_datetime(avail_raw)

        # Ensure timezone-aware comparison if needed
        if avail_dt.tzinfo is None and knowledge_dt.tzinfo is not None:
            avail_dt = avail_dt.replace(tzinfo=knowledge_dt.tzinfo)
        elif avail_dt.tzinfo is not None and knowledge_dt.tzinfo is None:
            knowledge_dt = knowledge_dt.replace(tzinfo=avail_dt.tzinfo)

        if eff_date <= analysis_d and avail_dt <= knowledge_dt:
            valid_records.append(obs)

    return sorted(valid_records, key=lambda x: str(x.get("date", x.get("effective_date", ""))))


def assert_zero_future_leakage(
    filtered_observations: Sequence[dict[str, Any]],
    analysis_cutoff: str,
    knowledge_cutoff: str,
) -> tuple[bool, list[str]]:
    """
    Audit an observation dataset to verify zero future data leakage.
    Returns (is_clean, violations).
    """
    analysis_d = _parse_iso_date(analysis_cutoff)
    knowledge_dt = _parse_iso_datetime(knowledge_cutoff)

    violations = []
    for i, obs in enumerate(filtered_observations):
        eff_date = _parse_iso_date(obs.get("date", obs.get("effective_date", "1900-01-01")))
        avail_raw = obs.get("availability_time", "1900-01-01T00:00:00+00:00")
        avail_dt = _parse_iso_datetime(avail_raw)

        if avail_dt.tzinfo is None and knowledge_dt.tzinfo is not None:
            avail_dt = avail_dt.replace(tzinfo=knowledge_dt.tzinfo)
        elif avail_dt.tzinfo is not None and knowledge_dt.tzinfo is None:
            knowledge_dt = knowledge_dt.replace(tzinfo=avail_dt.tzinfo)

        if eff_date > analysis_d:
            violations.append(
                f"Record {i} ({obs}) has effective_date {eff_date} > analysis_cutoff {analysis_d}"
            )
        if avail_dt > knowledge_dt:
            violations.append(
                f"Record {i} ({obs}) has availability_time {avail_dt} > knowledge_cutoff {knowledge_dt}"
            )

    return (len(violations) == 0), violations
