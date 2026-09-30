#!/usr/bin/env python3
"""Build an idempotent reviewed canonical-brand backfill package."""

from __future__ import annotations

import argparse
import hashlib
import json
import unicodedata
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path


SEPARATORS = " -\u2022\u00b7\u2010\u2011\u2013\u2014\u00a0"


def brand_key(value: str) -> str:
    key = unicodedata.normalize("NFC", value).lower()
    for separator in SEPARATORS:
        key = key.replace(separator, "")
    if len(key) < 3 or not any(character.isalpha() for character in key):
        raise ValueError(f"Invalid brand key for {value!r}")
    return key


def sql_literal(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def file_sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


def load_inventory(path: Path) -> tuple[dict[str, int], dict]:
    payload = json.loads(path.read_text(encoding="utf-8-sig"))
    counts: dict[str, int] = {}
    for record in payload["records"]:
        if record.get("section") != "brand":
            continue
        brand = record.get("brand")
        if brand and brand.strip():
            counts[brand] = int(record["count"])
    return counts, payload["summary"]


def normalized_brand_sql() -> str:
    expression = "lower(brand)"
    for separator in SEPARATORS:
        expression = f"replace({expression}, {sql_literal(separator)}, '')"
    return expression


def normalized_value_sql(column: str) -> str:
    expression = f"lower({column})"
    for separator in SEPARATORS:
        expression = f"replace({expression}, {sql_literal(separator)}, '')"
    return expression


def build_sql(rules: list[dict], expected_total: int) -> str:
    decisions: dict[tuple[str, str], str] = {}
    for rule in rules:
        canonical = rule["canonicalName"].strip()
        canonical_key = brand_key(canonical)
        decisions[(canonical, canonical_key)] = canonical
        for alias in rule.get("aliases", []):
            normalized_alias = brand_key(alias)
            decisions.setdefault((canonical, normalized_alias), alias.strip())
    values = ",\n".join(
        f"    ({sql_literal(canonical)}, {sql_literal(alias)}, {sql_literal(key)})"
        for (canonical, key), alias in decisions.items()
    )
    canonical_key_sql = normalized_value_sql("canonical_name")
    brand_sql = normalized_brand_sql()
    return f"""-- Generated reviewed brand backfill. Legacy brand text is preserved.
BEGIN;
SET LOCAL lock_timeout = '3s';
SET LOCAL statement_timeout = '5min';

CREATE TEMP TABLE reviewed_brand_decisions (
    canonical_name varchar(255) NOT NULL,
    alias varchar(255) NOT NULL,
    normalized_alias varchar(255) NOT NULL,
    PRIMARY KEY (canonical_name, normalized_alias),
    UNIQUE (normalized_alias)
) ON COMMIT DROP;

INSERT INTO reviewed_brand_decisions(canonical_name, alias, normalized_alias) VALUES
{values};

INSERT INTO food_brands(canonical_name, normalized_key, status, source, verified,
                        created_at, updated_at, created_by, updated_by)
SELECT DISTINCT canonical_name, {canonical_key_sql}, 'ACTIVE',
       'OWNER_REVIEWED_ALIAS_V1', true, current_timestamp, current_timestamp,
       'catalog-brand-backfill-v1', 'catalog-brand-backfill-v1'
FROM reviewed_brand_decisions
ON CONFLICT DO NOTHING;

INSERT INTO food_brand_aliases(brand_id, alias, normalized_alias, source, created_at, created_by)
SELECT brand.id, decision.alias, decision.normalized_alias,
       'OWNER_REVIEWED_ALIAS_V1', current_timestamp, 'catalog-brand-backfill-v1'
FROM reviewed_brand_decisions decision
JOIN food_brands brand
  ON brand.canonical_name = decision.canonical_name AND brand.status = 'ACTIVE'
WHERE decision.normalized_alias <> brand.normalized_key
ON CONFLICT (normalized_alias) DO NOTHING;

DO $gate$
DECLARE matched bigint;
BEGIN
    SELECT count(*) INTO matched
    FROM food_items item
    JOIN reviewed_brand_decisions decision ON {brand_sql} = decision.normalized_alias
    WHERE item.is_custom IS NOT TRUE;
    IF matched <> {expected_total} THEN
        RAISE EXCEPTION 'Brand backfill gate expected {expected_total} rows but found %', matched;
    END IF;
END $gate$;

WITH matches AS (
    SELECT item.id, brand.id brand_id
    FROM food_items item
    JOIN reviewed_brand_decisions decision ON {brand_sql} = decision.normalized_alias
    JOIN food_brands brand
      ON brand.canonical_name = decision.canonical_name AND brand.status = 'ACTIVE'
    WHERE item.is_custom IS NOT TRUE
)
UPDATE food_items item
SET brand_id = matches.brand_id
FROM matches
WHERE item.id = matches.id
  AND item.brand_id IS DISTINCT FROM matches.brand_id;

COMMIT;
"""


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--inventory", required=True)
    parser.add_argument("--decisions", required=True)
    parser.add_argument("--output-directory", required=True)
    args = parser.parse_args()
    inventory_path = Path(args.inventory).resolve()
    decisions_path = Path(args.decisions).resolve()
    output = Path(args.output_directory).resolve()
    output.mkdir(parents=True, exist_ok=True)

    raw_counts, summary = load_inventory(inventory_path)
    decisions_payload = json.loads(decisions_path.read_text(encoding="utf-8-sig"))
    if decisions_payload.get("status") != "REVIEWED":
        raise ValueError("Only REVIEWED decision files can generate a backfill package")
    rules = decisions_payload["rules"]
    canonical_keys: set[str] = set()
    alias_owners: dict[str, str] = {}
    counts_by_key: defaultdict[str, int] = defaultdict(int)
    raw_by_key: defaultdict[str, list[dict]] = defaultdict(list)
    invalid_raw_values: list[dict] = []
    for raw_brand, count in raw_counts.items():
        try:
            key = brand_key(raw_brand)
        except ValueError:
            invalid_raw_values.append({"brand": raw_brand, "rows": count})
            continue
        counts_by_key[key] += count
        raw_by_key[key].append({"brand": raw_brand, "rows": count})

    report_rules = []
    expected_total = 0
    for rule in rules:
        canonical = rule["canonicalName"].strip()
        canonical_key = brand_key(canonical)
        if canonical_key in canonical_keys:
            raise ValueError(f"Duplicate canonical key: {canonical_key}")
        canonical_keys.add(canonical_key)
        accepted_keys = {canonical_key, *(brand_key(alias) for alias in rule.get("aliases", []))}
        for key in accepted_keys:
            previous = alias_owners.setdefault(key, canonical)
            if previous != canonical:
                raise ValueError(f"Alias key {key} belongs to both {previous} and {canonical}")
        matched = sum(counts_by_key[key] for key in accepted_keys)
        expected_total += matched
        report_rules.append({
            "canonicalName": canonical,
            "normalizedKey": canonical_key,
            "acceptedKeys": sorted(accepted_keys),
            "matchedRows": matched,
            "rawValues": sorted(
                [row for key in accepted_keys for row in raw_by_key[key]],
                key=lambda row: (-row["rows"], row["brand"]),
            ),
        })

    sql_path = output / "canonical-brand-backfill-v1.sql"
    sql_path.write_text(build_sql(rules, expected_total), encoding="utf-8")
    report = {
        "status": "DRY_RUN_PASS",
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "inventorySha256": file_sha256(inventory_path),
        "decisionsSha256": file_sha256(decisions_path),
        "catalogRows": summary["rows"],
        "canonicalBrands": len(rules),
        "expectedMatchedProducts": expected_total,
        "searchKeyIneligibleRawBrandValues": sorted(
            invalid_raw_values, key=lambda row: (-row["rows"], row["brand"])
        ),
        "rules": report_rules,
        "sql": str(sql_path),
        "sqlSha256": file_sha256(sql_path),
    }
    (output / "canonical-brand-backfill-v1-report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps({key: report[key] for key in (
        "status", "catalogRows", "canonicalBrands", "expectedMatchedProducts", "sqlSha256"
    )}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
