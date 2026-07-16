#!/usr/bin/env python3
"""Build a non-importable queue for TR candidates needing second-source evidence."""

from __future__ import annotations

import argparse
import csv
import json
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--candidates", required=True)
    parser.add_argument("--output-dir", required=True)
    args = parser.parse_args()

    source = Path(args.candidates).resolve()
    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    queue_path = output_dir / "tr-market-evidence-queue.tsv"
    report_path = output_dir / "tr-market-evidence-queue-report.json"
    queue: list[dict[str, str]] = []
    excluded = Counter()

    with source.open("r", encoding="utf-8", newline="") as stream:
        for row in csv.DictReader(stream, delimiter="\t"):
            signals = set(filter(None, row["signals"].split(";")))
            if "OFF_COUNTRY_TR" in signals:
                excluded["ALREADY_HAS_STRONG_COUNTRY_EVIDENCE"] += 1
                continue
            if "GS1_TR_PREFIX" not in signals:
                excluded["NO_GS1_TR_PREFIX"] += 1
                continue
            if row["issues"]:
                excluded["QUALITY_GATE_FAILED"] += 1
                continue
            queue.append({
                "barcode": row["barcode"],
                "name": row["name"],
                "brand": row["brand"],
                "calories": row["calories"],
                "protein": row["protein"],
                "fat": row["fat"],
                "carbs": row["carbs"],
                "offMarketScore": row["marketScore"],
                "offSignals": row["signals"],
                "evidenceStatus": "PENDING_SECOND_SOURCE",
                "requiredEvidence": "AUTHORIZED_TR_RETAILER_OR_MANUFACTURER_MARKET_EVIDENCE",
                "evidenceSourceId": "",
                "evidenceUrl": "",
                "evidenceRetrievedAt": "",
                "evidenceChecksum": "",
                "reviewDecision": "PENDING",
            })

    queue.sort(key=lambda item: (item["brand"].casefold(), item["name"].casefold(), item["barcode"]))
    columns = tuple(queue[0].keys()) if queue else (
        "barcode", "name", "brand", "calories", "protein", "fat", "carbs",
        "offMarketScore", "offSignals", "evidenceStatus", "requiredEvidence",
        "evidenceSourceId", "evidenceUrl", "evidenceRetrievedAt", "evidenceChecksum",
        "reviewDecision",
    )
    with queue_path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=columns, delimiter="\t")
        writer.writeheader()
        writer.writerows(queue)

    report = {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "sourceCandidates": str(source),
        "queueRows": len(queue),
        "importAllowed": False,
        "promotionRule": "A queue row may become STRICT only after authorized second-source market evidence and admin review.",
        "excluded": dict(excluded),
        "artifacts": {"queue": str(queue_path), "report": str(report_path)},
    }
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
