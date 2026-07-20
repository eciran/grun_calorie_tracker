#!/usr/bin/env python3
"""Discover Turkish-market Open Food Facts candidates from the bulk TSV dump."""

from __future__ import annotations

import argparse
import csv
import gzip
import hashlib
import json
import re
import sys
import time
import unicodedata
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path


COUNTRY_TERMS = ("turkey", "turkiye", "en:turkey")
TIER_RANK = {"STRICT": 3, "REVIEW": 2, "QUARANTINE": 1}


def normalize_text(value: str | None) -> str:
    if not value:
        return ""
    value = value.lower().replace("\u0131", "i").replace("\u015f", "s").replace("\u011f", "g")
    return "".join(
        char for char in unicodedata.normalize("NFD", value)
        if unicodedata.category(char) != "Mn"
    )


def contains_term(value: str, terms: tuple[str, ...]) -> bool:
    normalized = normalize_text(value)
    return any(term in normalized for term in terms)


def contains_store(value: str, terms: tuple[str, ...]) -> bool:
    normalized_terms = set(terms)
    stores = {
        normalize_text(item).strip()
        for item in re.split(r"[,;|]", value or "")
        if item.strip()
    }
    return bool(stores & normalized_terms)

def decimal(value: str | None) -> float | None:
    if not value:
        return None
    try:
        return float(value.replace(",", "."))
    except ValueError:
        return None


def valid_gtin(code: str) -> bool:
    if not re.fullmatch(r"(?:\d{8}|\d{12}|\d{13}|\d{14})", code or ""):
        return False
    total = 0
    weight = 3
    for digit in reversed(code[:-1]):
        total += int(digit) * weight
        weight = 1 if weight == 3 else 3
    return (10 - total % 10) % 10 == int(code[-1])


def plausible_nutrition(values: tuple[float | None, ...]) -> bool:
    calories, protein, fat, carbs = values
    if any(value is None for value in values):
        return False
    assert calories is not None and protein is not None and fat is not None and carbs is not None
    return (
        0 <= calories <= 1000
        and all(0 <= value <= 100 for value in (protein, fat, carbs))
        and protein + fat + carbs <= 110
    )


def first(row: dict[str, str], *names: str) -> str:
    for name in names:
        value = (row.get(name) or "").strip()
        if value:
            return value
    return ""


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest().upper()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", default="outputs/openfoodfacts-products.csv.gz")
    parser.add_argument("--registry", default="sample-data/manifests/tr-food-source-registry-v1.json")
    parser.add_argument("--output-dir", default="outputs/product-data-readiness/s9-tr-internet")
    parser.add_argument("--max-rows", type=int, default=10_000_000)
    parser.add_argument("--progress-every", type=int, default=250_000)
    args = parser.parse_args()

    input_path = Path(args.input).resolve()
    registry_path = Path(args.registry).resolve()
    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    candidate_path = output_dir / "tr-off-candidates.tsv"
    report_path = output_dir / "tr-off-capacity-report.json"

    registry = json.loads(registry_path.read_text(encoding="utf-8"))
    thresholds = registry["marketEvidence"]["thresholds"]
    signal_scores = {
        signal["id"]: int(signal["score"])
        for signal in registry["marketEvidence"]["signals"]
    }
    store_terms = tuple(
        normalize_text(item)
        for item in registry["marketEvidence"]["storePolicy"]["exactTurkeySpecific"]
    )
    source_hash = sha256(input_path)
    started_at = datetime.now(timezone.utc)
    started = time.perf_counter()
    candidates: dict[str, dict[str, object]] = {}
    issues: Counter[str] = Counter()
    signals: Counter[str] = Counter()
    rows_read = malformed_rows = relevant_rows = 0

    csv.field_size_limit(2_147_483_647)
    opener = gzip.open if input_path.suffix.lower() == ".gz" else open
    with opener(input_path, "rt", encoding="utf-8", errors="replace", newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t", quotechar='"')
        for row in reader:
            if rows_read >= args.max_rows:
                break
            rows_read += 1
            if args.progress_every and rows_read % args.progress_every == 0:
                elapsed = max(time.perf_counter() - started, 0.001)
                print(
                    f"rows={rows_read} relevant={relevant_rows} unique={len(candidates)} "
                    f"rate={rows_read / elapsed:.0f}/s",
                    file=sys.stderr,
                    flush=True,
                )
            if None in row:
                malformed_rows += 1

            code = first(row, "code", "barcode")
            country_text = " ".join(filter(None, (
                first(row, "countries_tags"), first(row, "countries_tags_en"), first(row, "countries")
            )))
            store_text = " ".join(filter(None, (first(row, "stores"), first(row, "stores_tags"))))
            localized_text = " ".join(filter(None, (
                first(row, "product_name_tr"), first(row, "generic_name_tr"), first(row, "ingredients_text_tr")
            )))
            language = first(row, "lc", "lang")
            country_signal = contains_term(country_text, COUNTRY_TERMS)
            store_signal = contains_store(store_text, store_terms)
            localized_signal = bool(localized_text)
            language_signal = normalize_text(language) == "tr"
            prefix_signal = code.startswith(("868", "869"))
            if not any((country_signal, store_signal, localized_signal, language_signal, prefix_signal)):
                continue
            relevant_rows += 1

            active_signals: list[str] = []
            for active, signal_id in (
                (country_signal, "OFF_COUNTRY_TR"),
                (store_signal, "TR_RETAILER_TAG"),
                (localized_signal, "TR_LOCALIZED_CONTENT"),
                (language_signal, "TR_PRIMARY_LANGUAGE"),
                (prefix_signal, "GS1_TR_PREFIX"),
            ):
                if active:
                    active_signals.append(signal_id)
                    signals[signal_id] += 1
            market_score = min(100, sum(signal_scores[item] for item in active_signals))

            name = first(row, "product_name_tr", "product_name", "product_name_en", "generic_name")
            brand = first(row, "brands", "brand")
            calories = decimal(first(row, "energy-kcal_100g", "energy_kcal_100g"))
            protein = decimal(first(row, "proteins_100g", "protein_100g"))
            fat = decimal(first(row, "fat_100g"))
            carbs = decimal(first(row, "carbohydrates_100g", "carbs_100g"))
            row_issues: list[str] = []
            if not valid_gtin(code):
                row_issues.append("INVALID_GTIN")
            if not name:
                row_issues.append("MISSING_NAME")
            if not brand:
                row_issues.append("MISSING_BRAND")
            if calories is None:
                row_issues.append("MISSING_CALORIES")
            if any(value is None for value in (protein, fat, carbs)):
                row_issues.append("MISSING_MACROS")
            if not plausible_nutrition((calories, protein, fat, carbs)):
                row_issues.append("IMPLAUSIBLE_OR_INCOMPLETE_NUTRITION")
            issues.update(row_issues)
            quality_pass = not row_issues
            strong_combination = country_signal or (store_signal and localized_signal)
            if quality_pass and market_score >= int(thresholds["strict"]) and strong_combination:
                tier = "STRICT"
            elif quality_pass and market_score >= int(thresholds["review"]):
                tier = "REVIEW"
            else:
                tier = "QUARANTINE"

            candidate: dict[str, object] = {
                "barcode": code, "name": name, "brand": brand, "tier": tier,
                "marketScore": market_score, "signals": ";".join(active_signals),
                "issues": ";".join(row_issues), "countryTags": country_text,
                "stores": store_text, "language": language, "calories": calories,
                "protein": protein, "fat": fat, "carbs": carbs, "sourceRow": rows_read,
            }
            key = code or f"ROW:{rows_read}"
            existing = candidates.get(key)
            if existing is None or (
                TIER_RANK[tier], market_score
            ) > (
                TIER_RANK[str(existing["tier"])], int(existing["marketScore"])
            ):
                candidates[key] = candidate

    ordered = sorted(
        candidates.values(),
        key=lambda item: (-TIER_RANK[str(item["tier"])], -int(item["marketScore"]), str(item["barcode"])),
    )
    columns = (
        "barcode", "name", "brand", "tier", "marketScore", "signals", "issues",
        "countryTags", "stores", "language", "calories", "protein", "fat", "carbs", "sourceRow",
    )
    with candidate_path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=columns, delimiter="\t", extrasaction="ignore")
        writer.writeheader()
        writer.writerows(ordered)

    tier_counts = Counter(str(item["tier"]) for item in ordered)
    duration_ms = int((time.perf_counter() - started) * 1000)
    report = {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "startedAt": started_at.isoformat(),
        "input": {"path": str(input_path), "sha256": source_hash, "bytes": input_path.stat().st_size},
        "registry": {"path": str(registry_path), "id": registry["registryId"]},
        "analyzer": {"runtime": "python", "version": 1},
        "durationMs": duration_ms,
        "rowsRead": rows_read,
        "malformedRows": malformed_rows,
        "relevantRows": relevant_rows,
        "uniqueCandidates": len(ordered),
        "tierCounts": {tier: tier_counts[tier] for tier in ("STRICT", "REVIEW", "QUARANTINE")},
        "signalCounts": dict(signals),
        "issueCounts": dict(issues),
        "gates": {
            "pilotTarget": 5000,
            "gateTarget": 25000,
            "strictPilotPass": tier_counts["STRICT"] >= 5000,
            "strictGatePass": tier_counts["STRICT"] >= 25000,
        },
        "artifacts": {"candidates": str(candidate_path), "report": str(report_path)},
    }
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
