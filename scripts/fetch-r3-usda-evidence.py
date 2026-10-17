"""Freeze exact FDC details in bounded batches, without importing or logging credentials."""
import argparse
import csv
import hashlib
import http.client
import ipaddress
import json
import os
import re
import socket
import ssl
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path


def validate_batch(requested, foods):
    if not isinstance(foods, list):
        raise ValueError("USDA response is not a list")
    ids = [str(food.get("fdcId")) for food in foods]
    if len(ids) != len(set(ids)) or set(ids) != set(requested):
        raise ValueError("USDA exact-id response incomplete, duplicate or unexpected")
    if any(not food.get("description") or not food.get("dataType") for food in foods):
        raise ValueError("USDA metadata missing")
    return foods


def cached_batch_receipt(path, requested, now):
    receipt_path = path.with_suffix(".receipt.json")
    if not receipt_path.exists():
        raise ValueError("Cached USDA batch has no fetch-time receipt; use a new fetch directory")
    receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
    stamp = datetime.fromisoformat(receipt["fetchedAt"].replace("Z", "+00:00"))
    if (receipt.get("ids") != requested or receipt.get("sha256") != hashlib.sha256(path.read_bytes()).hexdigest()
            or stamp.tzinfo is None or not 0 <= (now - stamp).total_seconds() <= 86400):
        raise ValueError("Cached USDA batch changed or expired; do not restamp it as fresh")
    return receipt


def load_key(env_file):
    key = os.environ.get("USDA_FOODDATA_API_KEY", "").strip()
    if not key and env_file.exists():
        for line in env_file.read_text(encoding="utf-8-sig").splitlines():
            if line.startswith("USDA_FOODDATA_API_KEY="):
                key = line.split("=", 1)[1].strip().strip('"').strip("'")
                break
    if not key:
        raise ValueError("USDA_FOODDATA_API_KEY missing; no request sent")
    return key


def fetch(args):
    batch_size = getattr(args, 'batch_size', 20)
    if not isinstance(batch_size, int) or isinstance(batch_size, bool) or not 1 <= batch_size <= 20:
        raise ValueError('USDA evidence batch size must be between 1 and 20')
    with args.catalog.open(encoding="utf-8-sig", newline="") as stream:
        rows = list(csv.DictReader(stream))
    ids = set()
    for row in rows:
        match = re.fullmatch(r"USDA(?:_FOODDATA)?:fdc:([1-9][0-9]{0,17})", row.get("source_key", ""))
        if match:
            ids.add(match.group(1))
        elif row.get("data_source") == "USDA_FOODDATA":
            raise ValueError("Invalid USDA source key in snapshot")
    ids = sorted(ids, key=int)
    if not ids:
        raise ValueError("No USDA ids in snapshot")
    key = load_key(args.env_file)
    opener = urllib.request.build_opener()
    if args.resolved_address:
        ipaddress.ip_address(args.resolved_address)
        # Preserve certificate verification and SNI for the original USDA host.
        class ResolvedConnection(http.client.HTTPSConnection):
            def __init__(self, host, **kwargs):
                if host != "api.nal.usda.gov":
                    raise ValueError("Only the official USDA host can use the DNS fallback")
                super().__init__(host, **kwargs)
                self._create_connection = lambda address, timeout, source_address: socket.create_connection(
                    (args.resolved_address, address[1]), timeout, source_address)

        class ResolvedHandler(urllib.request.HTTPSHandler):
            def https_open(self, request):
                return self.do_open(ResolvedConnection, request, context=ssl.create_default_context())

        opener = urllib.request.build_opener(ResolvedHandler())
    args.output.mkdir(parents=True, exist_ok=True)
    foods = []
    files = []
    for start in range(0, len(ids), batch_size):
        batch = ids[start:start + batch_size]
        path = args.output / f"batch-{start // batch_size + 1:03d}.json"
        if args.resume and path.exists():
            receipt = cached_batch_receipt(path, batch, datetime.now(timezone.utc))
            raw = path.read_bytes()
            result = validate_batch(batch, json.loads(raw))
            files.append({"file": path.name, **receipt})
            foods.extend(result)
            continue
        request = urllib.request.Request("https://api.nal.usda.gov/fdc/v1/foods",
            data=json.dumps({"fdcIds": [int(i) for i in batch], "format": "full"}).encode(),
            headers={"Content-Type": "application/json", "X-Api-Key": key}, method="POST")
        for attempt in range(3):
            try:
                with opener.open(request, timeout=45) as response:
                    raw = response.read()
                result = validate_batch(batch, json.loads(raw))
                break
            except (urllib.error.URLError, TimeoutError) as error:
                print("USDA request status: " + str(getattr(error, "code", type(error).__name__)), flush=True)
                if attempt == 2:
                    raise RuntimeError("USDA batch unavailable; no decision or import produced") from None
                time.sleep(2 ** attempt)
        path.write_bytes(raw)
        receipt = {"sha256": hashlib.sha256(raw).hexdigest(), "ids": batch,
                   "fetchedAt": datetime.now(timezone.utc).isoformat()}
        path.with_suffix(".receipt.json").write_text(json.dumps(receipt), encoding="utf-8")
        files.append({"file": path.name, **receipt})
        foods.extend(result)
        print(f"Exact FDC evidence: {len(foods)}/{len(ids)}", flush=True)
    validate_batch(ids, foods)
    oldest = min(files, key=lambda f: datetime.fromisoformat(f["fetchedAt"].replace("Z", "+00:00")))
    evidence = {"version": "R3_EXACT_FDC_EVIDENCE_V1", "fetchedAt": oldest["fetchedAt"],
                "source": "https://api.nal.usda.gov/fdc/v1/foods", "catalogSha256": hashlib.sha256(args.catalog.read_bytes()).hexdigest(),
                "requestedIds": ids, "matchedCount": len(foods), "batchSize": batch_size, "batchFiles": files, "foods": foods}
    args.output.joinpath("evidence.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"matchedCount": len(foods), "batches": len(files), "credentialsSaved": False}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--catalog", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--env-file", type=Path, default=Path(".env"))
    parser.add_argument("--resolved-address", help="Optional externally verified DNS address; TLS hostname stays verified")
    parser.add_argument("--resume", action="store_true", help="Reuse validated batch files in this evidence directory")
    parser.add_argument('--batch-size', type=int, default=20, help='Use smaller exact-id batches for large source profiles (1..20)')
    fetch(parser.parse_args())
