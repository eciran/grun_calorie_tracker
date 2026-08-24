#!/usr/bin/env python3
"""Prioritize TR evidence candidates and validate evidence-backed promotions."""

from __future__ import annotations

import argparse
import csv
import json
import re
import unicodedata
from collections import Counter, defaultdict
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.parse import urlparse


ALLOWED_EVIDENCE_TYPES = {
    "AUTHORIZED_MANUFACTURER_FEED",
    "AUTHORIZED_RETAILER_FEED",
    "USER_SUBMITTED_LABEL",
}
TURKISH_FOOD_TERMS = {
    "ayran", "bal", "biskuvi", "borek", "bulgur", "cay", "cikolata",
    "corba", "dondurma", "ekmek", "findik", "kahve", "kefir", "kek",
    "ketcap", "lokum", "makarna", "meyve", "nisasta", "pekmez", "peynir",
    "pirinc", "salca", "sos", "sut", "tahin", "tursu", "yogurt", "zeytin",
}


def normalize(value: str | None) -> str:
    value = (value or "").strip().casefold().replace("ı", "i")
    return "".join(
        char for char in unicodedata.normalize("NFD", value)
        if unicodedata.category(char) != "Mn"
    )


def brand_tokens(value: str) -> set[str]:
    return {
        normalize(token)
        for token in re.split(r"[,;/|]", value or "")
        if normalize(token)
    }


def parse_bool(value: str) -> bool:
    return normalize(value) in {"true", "1", "yes"}


def parse_timestamp(value: str) -> datetime | None:
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        return parsed if parsed.tzinfo is not None else None
    except (TypeError, ValueError):
        return None


def valid_gtin(value: str) -> bool:
    if not re.fullmatch(r"(?:\d{8}|\d{12}|\d{13}|\d{14})", value or ""):
        return False
    total = 0
    weight = 3
    for digit in reversed(value[:-1]):
        total += int(digit) * weight
        weight = 1 if weight == 3 else 3
    return (10 - total % 10) % 10 == int(value[-1])


def valid_queue_nutrition(row: dict[str, str]) -> bool:
    try:
        calories, protein, fat, carbs = (
            float(row[field]) for field in ("calories", "protein", "fat", "carbs")
        )
    except (KeyError, TypeError, ValueError):
        return False
    return (
        0 <= calories <= 1000
        and all(0 <= value <= 100 for value in (protein, fat, carbs))
        and protein + fat + carbs <= 110
    )


def valid_evidence_url(value: str) -> bool:
    parsed = urlparse(value)
    return parsed.scheme == "https" and bool(parsed.netloc)


def priority(row: dict[str, str], strict_brand_counts: Counter[str]) -> tuple[int, str, list[str]]:
    score = 10
    reasons = ["VALID_TR_GTIN_PREFIX_AND_COMPLETE_NUTRITION"]
    matching_strict = sum(strict_brand_counts[token] for token in brand_tokens(row["brand"]))
    if matching_strict:
        score += 35
        reasons.append("BRAND_ALREADY_HAS_STRICT_TR_PRODUCT")

    name = row["name"]
    normalized_name = normalize(name)
    if re.search(r"[çğıöşüÇĞİÖŞÜ]", name) or set(re.findall(r"[a-z]+", normalized_name)) & TURKISH_FOOD_TERMS:
        score += 25
        reasons.append("TURKISH_PRODUCT_IDENTITY_SIGNAL")

    clean_identity = (
        2 <= len(name.strip()) <= 180
        and 2 <= len(row["brand"].strip()) <= 120
        and not row["brand"].strip().isdigit()
        and not name.strip().isdigit()
    )
    if clean_identity:
        score += 20
        reasons.append("CLEAN_PRODUCT_AND_BRAND_IDENTITY")
    else:
        reasons.append("SUSPICIOUS_PRODUCT_OR_BRAND_IDENTITY")

    score = min(score, 100)
    level = "HIGH" if score >= 70 else "MEDIUM" if score >= 40 else "LOW"
    return score, level, reasons


def queue_failures(row: dict[str, str], duplicate_count: int) -> list[str]:
    failures: list[str] = []
    if not valid_gtin(row.get("barcode", "")) or not row.get("barcode", "").startswith(("868", "869")):
        failures.append("INVALID_TR_GTIN")
    if not valid_queue_nutrition(row):
        failures.append("INVALID_QUEUE_NUTRITION")
    if row.get("evidenceStatus") != "PENDING_SECOND_SOURCE":
        failures.append("INVALID_QUEUE_EVIDENCE_STATUS")
    if duplicate_count:
        failures.append("DUPLICATE_QUEUE_BARCODE")
    return failures


def promotion_failures(
    evidence: dict[str, str] | None,
    assessed_at: datetime,
    max_evidence_age_days: int,
) -> list[str]:
    if evidence is None:
        return ["MISSING_AUTHORIZED_EVIDENCE"]
    failures: list[str] = []
    if evidence.get("evidenceType") not in ALLOWED_EVIDENCE_TYPES:
        failures.append("UNSUPPORTED_EVIDENCE_TYPE")
    if not str(evidence.get("evidenceSourceId") or "").strip():
        failures.append("MISSING_EVIDENCE_SOURCE_ID")
    if not valid_evidence_url(evidence.get("evidenceUrl", "")):
        failures.append("INVALID_EVIDENCE_URL")
    retrieved_at = parse_timestamp(evidence.get("evidenceRetrievedAt", ""))
    if retrieved_at is None:
        failures.append("INVALID_EVIDENCE_TIMESTAMP")
    elif retrieved_at > assessed_at + timedelta(minutes=5):
        failures.append("FUTURE_EVIDENCE_TIMESTAMP")
    elif retrieved_at < assessed_at - timedelta(days=max_evidence_age_days):
        failures.append("STALE_EVIDENCE")
    if not re.fullmatch(r"[0-9a-fA-F]{64}", evidence.get("evidenceChecksum", "")):
        failures.append("INVALID_EVIDENCE_CHECKSUM")
    if evidence.get("marketRegion") != "TR":
        failures.append("MARKET_REGION_NOT_TR")
    if not parse_bool(evidence.get("commercialUseAllowed", "")):
        failures.append("COMMERCIAL_USE_NOT_ALLOWED")
    if not parse_bool(evidence.get("persistentStorageAllowed", "")):
        failures.append("PERSISTENT_STORAGE_NOT_ALLOWED")
    if evidence.get("reviewDecision") != "APPROVED":
        failures.append("ADMIN_REVIEW_NOT_APPROVED")
    if not str(evidence.get("reviewerId") or "").strip():
        failures.append("MISSING_REVIEWER_ID")
    return failures


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8", newline="") as stream:
        return list(csv.DictReader(stream, delimiter="\t"))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--queue", required=True)
    parser.add_argument("--candidates", required=True)
    parser.add_argument("--evidence")
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--pilot-target", type=int, default=5_000)
    parser.add_argument("--max-evidence-age-days", type=int, default=365)
    args = parser.parse_args()

    queue_path = Path(args.queue).resolve()
    candidates_path = Path(args.candidates).resolve()
    evidence_path = Path(args.evidence).resolve() if args.evidence else None
    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    assessment_path = output_dir / "tr-market-evidence-assessment.tsv"
    promotion_path = output_dir / "tr-market-evidence-promotable.tsv"
    report_path = output_dir / "tr-market-evidence-assessment-report.json"

    queue_rows = read_rows(queue_path)
    candidate_rows = read_rows(candidates_path)
    assessed_at = datetime.now(timezone.utc)
    queue_barcode_counts = Counter(row.get("barcode", "") for row in queue_rows)
    strict_rows = [row for row in candidate_rows if row.get("tier") == "STRICT"]
    strict_brand_counts: Counter[str] = Counter()
    for row in strict_rows:
        strict_brand_counts.update(brand_tokens(row.get("brand", "")))

    evidence_by_barcode: dict[str, dict[str, str]] = {}
    duplicate_evidence = Counter()
    if evidence_path:
        for row in read_rows(evidence_path):
            barcode = str(row.get("barcode") or "").strip()
            if barcode in evidence_by_barcode:
                duplicate_evidence[barcode] += 1
            else:
                evidence_by_barcode[barcode] = row

    assessed: list[dict[str, str]] = []
    promotable: list[dict[str, str]] = []
    priority_counts: Counter[str] = Counter()
    promotion_counts: Counter[str] = Counter()
    blocked_reasons: Counter[str] = Counter()
    brand_impact: Counter[str] = Counter()
    brand_labels: defaultdict[str, Counter[str]] = defaultdict(Counter)

    for row in queue_rows:
        barcode = row["barcode"]
        score, level, reasons = priority(row, strict_brand_counts)
        evidence = evidence_by_barcode.get(barcode)
        failures = queue_failures(row, queue_barcode_counts[barcode] - 1)
        failures.extend(promotion_failures(evidence, assessed_at, args.max_evidence_age_days))
        if duplicate_evidence[barcode]:
            failures.append("DUPLICATE_EVIDENCE_ROWS")
        status = "PROMOTABLE" if not failures else "BLOCKED"
        priority_counts[level] += 1
        promotion_counts[status] += 1
        blocked_reasons.update(failures)
        for raw_brand in re.split(r"[,;/|]", row["brand"]):
            normalized_brand = normalize(raw_brand)
            if normalized_brand:
                brand_impact[normalized_brand] += 1
                brand_labels[normalized_brand][raw_brand.strip()] += 1
        result = dict(row)
        result.update({
            "priorityScore": str(score),
            "priorityLevel": level,
            "priorityReasons": ";".join(reasons),
            "promotionStatus": status,
            "promotionBlockers": ";".join(failures),
            "validatedEvidenceSourceId": evidence.get("evidenceSourceId", "") if evidence else "",
        })
        assessed.append(result)
        if status == "PROMOTABLE":
            promotable.append(result)

    assessed.sort(key=lambda row: (-int(row["priorityScore"]), normalize(row["brand"]), normalize(row["name"]), row["barcode"]))
    columns = list(assessed[0].keys()) if assessed else []
    for path, rows in ((assessment_path, assessed), (promotion_path, promotable)):
        with path.open("w", encoding="utf-8", newline="") as stream:
            writer = csv.DictWriter(stream, fieldnames=columns, delimiter="\t", extrasaction="ignore")
            if columns:
                writer.writeheader()
                writer.writerows(rows)

    strict_baseline = len(strict_rows)
    strict_after = strict_baseline + len(promotable)
    report = {
        "schemaVersion": 1,
        "generatedAt": assessed_at.isoformat(),
        "queueRows": len(queue_rows),
        "strictBaseline": strict_baseline,
        "evidenceRows": len(evidence_by_barcode),
        "priorityCounts": {
            level: priority_counts[level] for level in ("HIGH", "MEDIUM", "LOW")
        },
        "promotionCounts": {
            status: promotion_counts[status] for status in ("PROMOTABLE", "BLOCKED")
        },
        "promotionBlockers": dict(blocked_reasons),
        "strictAfterPromotions": strict_after,
        "pilotTarget": args.pilot_target,
        "pilotGapBefore": max(0, args.pilot_target - strict_baseline),
        "pilotGapAfter": max(0, args.pilot_target - strict_after),
        "strictPilotPass": strict_after >= args.pilot_target,
        "catalogImportAllowed": False,
        "decision": "Only PROMOTABLE rows with authorized, persistent, commercial-use evidence and explicit admin approval may enter the separate import preparation step.",
        "topQueueBrands": [
            {
                "brand": brand_labels[normalized_brand].most_common(1)[0][0],
                "normalizedBrand": normalized_brand,
                "rows": count,
            }
            for normalized_brand, count in sorted(
                brand_impact.items(), key=lambda item: (-item[1], item[0])
            )[:30]
        ],
        "artifacts": {
            "assessment": str(assessment_path),
            "promotable": str(promotion_path),
            "report": str(report_path),
        },
    }
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
