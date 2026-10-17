"""Extract full exact-GTIN bodies in one bounded-memory scan of a cached OFF dump.

EOF/CRC, compressed-file hash and target membership are recorded. Cached dump
provenance/freshness is NOT independently certified by a successful local scan.
Interrupted scans stay .partial and cannot be used by the R5 decision builder.
"""
import argparse
import csv
import gzip
import hashlib
import importlib.util
import json
import re
import time
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

spec = importlib.util.spec_from_file_location("source_options", Path(__file__).with_name("prepare-r5-source-options.py"))
options = importlib.util.module_from_spec(spec)
spec.loader.exec_module(options)
CODE = re.compile(rb'"code"\s*:\s*(?:"([0-9]+)"|([0-9]+)(?=\s*[,}]))')


class DigestReader:
    def __init__(self, stream):
        self.stream, self.digest, self.bytes = stream, hashlib.sha256(), 0

    def read(self, size=-1):
        data = self.stream.read(size)
        self.digest.update(data)
        self.bytes += len(data)
        return data


def target_codes(queues):
    targets = set()
    for path in queues:
        with path.open(encoding="utf-8-sig", newline="") as stream:
            for row in csv.DictReader(stream):
                key = row.get("source_key", "")
                if key.startswith("barcode:") and options.r5.valid_gtin(key[8:]):
                    targets.add(key[8:])
    return targets


def nominated_codes(line):
    # This is only a byte prefilter. Actual root identity is parsed below; a
    # nested ingredient/packaging code can never become a product identity.
    return {next(group for group in match.groups() if group).decode("ascii") for match in CODE.finditer(line)}


def run(args):
    if args.output.exists():
        raise ValueError("Use a fresh output directory; existing evidence is immutable")
    args.output.mkdir(parents=True)
    targets = target_codes(args.queue)
    if not targets:
        raise ValueError("No exact valid GTIN targets")
    before = args.archive.stat()
    counts, matched, seen = Counter(dumpLinesRead=0, matchedProducts=0, targetParseErrors=0), {}, set()
    path = args.output / "products.jsonl.partial"
    started, checked = time.monotonic(), datetime.now(timezone.utc).isoformat()
    complete = True
    with args.archive.open("rb") as raw, path.open("w", encoding="utf-8") as output:
        reader = DigestReader(raw)
        with gzip.GzipFile(fileobj=reader, mode="rb") as source:
            for line in source:
                counts["dumpLinesRead"] += 1
                if args.max_lines and counts["dumpLinesRead"] > args.max_lines:
                    complete = False
                    break
                if not nominated_codes(line) & targets:
                    continue
                try:
                    product = json.loads(line)
                except (ValueError, UnicodeError):
                    counts["targetParseErrors"] += 1
                    continue
                code = str(product.get("code", ""))
                if code not in targets:
                    continue
                if code in seen:
                    raise ValueError("Duplicate root GTIN in archive: " + code)
                seen.add(code)
                record = {"barcode": code, "product": product, "sourceProductFingerprint": options.r5.fingerprint(product),
                          "sourceArchive": str(args.archive), "status": "BULK_EXTRACTED", "extractedAt": checked,
                          "remoteSnapshotTime": None, "sourceFreshnessApproved": False, "applyAllowed": False}
                output.write(json.dumps(record, ensure_ascii=False, allow_nan=False) + "\n")
                matched[code] = options.r5.fingerprint(product)
                counts["matchedProducts"] += 1
                if counts["dumpLinesRead"] % 100000 == 0:
                    print(json.dumps({**counts, "seconds": round(time.monotonic() - started, 1)}), flush=True)
        compressed_hash, bytes_read = reader.digest.hexdigest(), reader.bytes
    after = args.archive.stat()
    if (before.st_size, before.st_mtime_ns) != (after.st_size, after.st_mtime_ns):
        raise ValueError("Archive changed during scan")
    verified = complete and counts["targetParseErrors"] == 0 and bytes_read == after.st_size
    if verified:
        path = path.replace(args.output / "products.jsonl")
    summary = {"ruleset": "R5_OFF_FULL_BODY_BULK_V1", **counts, "completedAt": datetime.now(timezone.utc).isoformat(),
               "seconds": round(time.monotonic() - started, 1), "fullScanComplete": verified,
               "archivePath": str(args.archive), "archiveBytesRead": bytes_read, "archiveBytes": after.st_size,
               "archiveSha256": compressed_hash if verified else None, "productsSha256": options.r5.closure.sha(path),
               "queueHashes": {str(p): options.r5.closure.sha(p) for p in args.queue}, "targetCount": len(targets),
               "targetCodesSha256": options.r5.fingerprint(sorted(targets)), "matchedFingerprints": matched,
               "targetsNotFound": sorted(targets - seen), "origin": "LOCAL_CACHED_OFF_DUMP",
               "sourceFreshnessApproved": False, "remoteSnapshotTime": None, "databaseWrites": 0, "applyAllowed": False}
    (args.output / ("summary.json" if verified else "summary.partial.json")).write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in summary.items() if k not in {"matchedFingerprints", "targetsNotFound"}}, indent=2))
    if not verified:
        raise ValueError("Incomplete scan; .partial artifacts are NOT decision inputs")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--archive", type=Path, required=True)
    parser.add_argument("--queue", action="append", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--max-lines", type=int, default=0, help="Smoke-only limit: output remains incomplete and rejected")
    run(parser.parse_args())
