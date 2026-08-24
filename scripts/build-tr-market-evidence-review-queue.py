#!/usr/bin/env python3
"""Build a deterministic, non-promotable review queue from frozen TR GTIN references."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlparse


BLOCKERS = (
    "EVIDENCE_CONTENT_NOT_FROZEN",
    "MISSING_EVIDENCE_CONTENT_CHECKSUM",
    "COMMERCIAL_USE_RIGHTS_UNVERIFIED",
    "PERSISTENT_STORAGE_RIGHTS_UNVERIFIED",
    "ADMIN_REVIEW_NOT_APPROVED",
)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest().upper()


def write_tsv(path: Path, fieldnames: list[str], rows: list[dict[str, object]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fieldnames, delimiter="\t")
        writer.writeheader()
        writer.writerows(rows)


def valid_reference_url(value: str) -> bool:
    parsed = urlparse(value)
    return parsed.scheme == "https" and bool(parsed.netloc)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--expected-input-sha256", required=True)
    parser.add_argument("--generated-at")
    args = parser.parse_args()

    input_path = Path(args.input).resolve()
    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    input_hash = sha256(input_path)
    expected_hash = args.expected_input_sha256.strip().upper()
    if input_hash != expected_hash:
        raise SystemExit(f"Input checksum mismatch: expected {expected_hash}, got {input_hash}")

    payload = json.loads(input_path.read_text(encoding="utf-8"))
    products = payload.get("products", [])
    barcodes = [str(product.get("barcode") or "").strip() for product in products]
    if len(products) != 648:
        raise SystemExit(f"Expected 648 products, got {len(products)}")
    if any(not barcode for barcode in barcodes) or len(set(barcodes)) != len(barcodes):
        raise SystemExit("Every product must have one unique non-empty barcode")

    queue_rows: list[dict[str, object]] = []
    reference_rows: list[dict[str, object]] = []
    provider_counts: Counter[str] = Counter()
    evidence_type_counts: Counter[str] = Counter()
    invalid_url_count = 0

    for product in sorted(products, key=lambda item: str(item.get("barcode") or "")):
        barcode = str(product.get("barcode") or "").strip()
        references = sorted(
            product.get("evidence") or [],
            key=lambda item: (str(item.get("provider") or ""), str(item.get("url") or "")),
        )
        providers: list[str] = []
        urls: list[str] = []
        for reference in references:
            provider = str(reference.get("provider") or "UNKNOWN").strip().upper()
            evidence_type = str(reference.get("evidenceType") or "UNKNOWN").strip().upper()
            url = str(reference.get("url") or "").strip()
            url_valid = valid_reference_url(url)
            provider_counts[provider] += 1
            evidence_type_counts[evidence_type] += 1
            invalid_url_count += 0 if url_valid else 1
            providers.append(provider)
            urls.append(url)
            reference_rows.append({
                "barcode": barcode,
                "provider": provider,
                "sourceClassification": "RETAILER_OR_DISTRIBUTOR_PAGE_REFERENCE",
                "evidenceType": evidence_type,
                "evidenceUrl": url,
                "urlValidation": "VALID_HTTPS_REFERENCE" if url_valid else "INVALID_REFERENCE_URL",
                "evidenceContentChecksum": "",
                "commercialUseAllowed": "UNVERIFIED",
                "persistentStorageAllowed": "UNVERIFIED",
                "reviewDecision": "PENDING_OWNER_RIGHTS_AND_ADMIN_REVIEW",
                "promotionStatus": "BLOCKED",
                "promotionBlockers": ";".join(BLOCKERS),
            })

        queue_rows.append({
            "barcode": barcode,
            "name": str(product.get("name") or ""),
            "brand": str(product.get("brand") or ""),
            "offMarketScore": product.get("offMarketScore", ""),
            "offSignals": str(product.get("offSignals") or ""),
            "exactGtinMatch": str(bool(product.get("exactGtinMatch"))).lower(),
            "sourceClassification": "EXACT_GTIN_PAGE_REFERENCES_NOT_AUTHORIZED_EVIDENCE",
            "evidenceReferenceCount": len(references),
            "evidenceProviders": ";".join(providers),
            "evidenceUrls": ";".join(urls),
            "candidateArtifactChecksum": input_hash,
            "evidenceContentChecksum": "",
            "commercialUseAllowed": "UNVERIFIED",
            "persistentStorageAllowed": "UNVERIFIED",
            "reviewDecision": "PENDING_OWNER_RIGHTS_AND_ADMIN_REVIEW",
            "reviewerId": "",
            "promotionStatus": "BLOCKED",
            "promotionBlockers": ";".join(BLOCKERS),
        })

    queue_path = output_dir / "tr-market-evidence-product-review.tsv"
    refs_path = output_dir / "tr-market-evidence-reference-review.tsv"
    report_path = output_dir / "report.json"
    write_tsv(queue_path, list(queue_rows[0]), queue_rows)
    write_tsv(refs_path, list(reference_rows[0]), reference_rows)

    generated_at = args.generated_at or datetime.now(timezone.utc).isoformat()
    report = {
        "schemaVersion": 1,
        "generatedAt": generated_at,
        "classification": "REVIEW_QUEUE_ONLY_NOT_CATALOG_IMPORT",
        "input": {
            "path": str(input_path),
            "sha256": input_hash,
            "products": len(products),
        },
        "counts": {
            "productReviewRows": len(queue_rows),
            "evidenceReferenceRows": len(reference_rows),
            "validHttpsReferenceUrls": len(reference_rows) - invalid_url_count,
            "invalidReferenceUrls": invalid_url_count,
            "rowsWithEvidenceContentChecksum": 0,
            "rightsVerifiedProducts": 0,
            "adminApprovedProducts": 0,
            "promotableProducts": 0,
            "blockedProducts": len(queue_rows),
        },
        "providerReferenceCounts": dict(sorted(provider_counts.items())),
        "evidenceTypeCounts": dict(sorted(evidence_type_counts.items())),
        "blockersAppliedToEveryProduct": list(BLOCKERS),
        "decision": (
            "Exact GTIN page references prioritize review but are not authorized market evidence. "
            "No row may enter the strict catalog until content is lawfully obtained and frozen, "
            "its checksum and reuse/storage rights are recorded, and an admin approves it."
        ),
        "artifacts": {
            "productReview": str(queue_path),
            "productReviewSha256": sha256(queue_path),
            "referenceReview": str(refs_path),
            "referenceReviewSha256": sha256(refs_path),
            "report": str(report_path),
        },
    }
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
