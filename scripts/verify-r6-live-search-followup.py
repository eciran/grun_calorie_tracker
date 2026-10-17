"""Source aliases, preparation boundaries, portions and warm timings via GET."""
import argparse
import importlib.util
import json
import os
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, build_opener

spec = importlib.util.spec_from_file_location("acceptance", Path(__file__).with_name("verify-r6-live-search.py"))
acceptance = importlib.util.module_from_spec(spec)
spec.loader.exec_module(acceptance)


def run(args):
    token = os.environ.get("GRUN_CATALOG_ACCEPTANCE_TOKEN")
    if not token:
        raise ValueError("Process-only token required")
    baseline = json.loads(args.baseline.read_text(encoding="utf-8"))
    package = json.loads((args.package / "manifest.json").read_text(encoding="utf-8"))
    receipts = json.loads((args.package / "applied-names/receipt.json").read_text(encoding="utf-8"))["rows"]
    opener = build_opener(acceptance.SameOriginRedirect())
    checks, errors, aliases, warm, preparations, qualifiers, observations = [], [], [], [], [], [], []
    calls = 0

    def check(value, label):
        (checks if value else errors).append(label)

    def get(path, **params):
        nonlocal calls
        if calls >= 50:
            raise ValueError("Follow-up request bound exhausted")
        calls += 1
        started = time.monotonic()
        request = Request(acceptance.ORIGIN + path + "?" + urlencode(params), headers={"Authorization": "Bearer " + token, "Accept": "application/json"})
        try:
            with opener.open(request, timeout=25) as response:
                body = json.load(response)
        except HTTPError as error:
            raise ValueError("API HTTP " + str(error.code) + " at " + path) from None
        elapsed = round((time.monotonic() - started) * 1000)
        time.sleep(.2)
        return body, elapsed

    try:
        for row in receipts:
            body, _ = get("/api/v1/admin/products/" + row["id"] + "/search-aliases", activeOnly="false")
            fields = {"id": "id", "foodItemId": "food_item_id", "alias": "alias", "normalizedAlias": "normalized_alias",
                      "language": "language", "aliasType": "alias_type", "source": "source", "active": "active"}
            actual = {a["id"]: a for a in body}
            expected = row["name_review"]["aliases"]
            check(set(actual) == {a["id"] for a in expected} and all(all(actual[a["id"]].get(k) == a[v] for k, v in fields.items())
                  for a in expected if a["id"] in actual), "EXACT_ALL_ACTIVE_INACTIVE_ALIASES_" + row["id"])
            aliases.append({"id": row["id"], "active": sum(a["active"] for a in body), "inactive": sum(not a["active"] for a in body)})
            for language in ("EN", "TR"):
                options, _ = get("/api/v1/products/" + row["id"] + "/serving-options", language=language)
                expected_options = [o for o in row["name_review"]["options"] if o["user_visible"] and o["quality_status"] == "VERIFIED"]
                by_id = {o["id"]: o for o in options}
                mapping = {"unitType": "unit_type", "quantity": "quantity", "gramWeight": "gram_weight", "mlVolume": "ml_volume",
                           "defaultOption": "is_default", "source": "source", "qualityStatus": "quality_status", "userVisible": "user_visible"}
                check(set(by_id) == {o["id"] for o in expected_options} and all(all(by_id[o["id"]].get(k) == o[v] for k, v in mapping.items())
                      for o in expected_options if o["id"] in by_id), "EXACT_PORTION_VALUES_" + row["id"] + "_" + language)
        for audit in package["aliasAudit"]:
            for change in audit["aliasActions"]:
                alias = change["replaceWith"]
                if not alias:
                    continue
                page, _ = get("/api/v1/products/search", q=alias["alias"], language=alias["language"], region="TR" if alias["language"] == "TR" else "UK_IE",
                              catalogType="GENERIC_INGREDIENT", page=0, size=37)
                ids = [p["id"] for p in page.get("content") or []]
                check(int(audit["id"]) in ids, "QUALIFIED_ALIAS_SEARCH_" + alias["alias"])
                qualifiers.append({"query": alias["alias"], "language": alias["language"], "targetId": int(audit["id"]),
                                   "rank": ids.index(int(audit["id"])) + 1 if int(audit["id"]) in ids else None})
        for query in ("brokoli", "tavuk göğsü", "pirinç"):
            for state in ("RAW", "COOKED"):
                page, _ = get("/api/v1/products/search", q=query, language="TR", region="TR", catalogType="GENERIC_INGREDIENT", preparationState=state, page=0, size=37)
                items = page.get("content") or []
                check(bool(items) and all(p["preparationState"] == state for p in items), "PREPARATION_FILTER_" + query + "_" + state)
                preparations.append({"query": query, "state": state, "total": page.get("totalElements"), "ids": [p["id"] for p in items]})
        for initial in baseline["searches"]:
            if initial["q"] not in ("milk", "rice", "süt", "pirinç"):
                continue
            times = []
            for _ in range(2):
                page, elapsed = get("/api/v1/products/search", q=initial["q"], language=initial["language"], region=initial["region"], page=0, size=37)
                check(page.get("totalElements") == initial["total"], "WARM_COUNT_STABLE_" + initial["q"])
                check([p["id"] for p in (page.get("content") or [])[:5]] == [p["id"] for p in initial["top5"]], "WARM_TOP5_STABLE_" + initial["q"])
                times.append(elapsed)
            warm.append({"query": initial["q"], "firstRequestMs": initial["ms"], "warmMs": times})
        for initial in baseline["searches"]:
            if initial["q"] == "pirinç" and initial["top5"] and "cream" in (initial["top5"][0].get("canonicalName") or "").lower():
                observations.append({"issue": "RICE_CREAM_AHEAD_OF_PLAIN_RICE", "query": initial["q"], "id": initial["top5"][0]["id"],
                                     "categoryAssignmentRollbackJustified": False})
            if initial["ms"] > 1000:
                observations.append({"issue": "SINGLE_FIRST_REQUEST_OVER_1_SECOND", "query": initial["q"], "ms": initial["ms"], "loadTest": False})
    except Exception as error:
        errors.append(str(error))
    result = {"status": "PASS" if not errors else "FAIL", "time": datetime.now(timezone.utc).isoformat(), "requests": calls,
              "passedChecks": len(checks), "checks": checks, "errors": errors, "aliases": aliases, "qualifiedAliasSearch": qualifiers,
              "preparationFilters": preparations, "warmSearch": warm, "qualityObservations": observations,
              "baselineSha256": acceptance.sha(args.baseline), "producerSha256": acceptance.sha(Path(__file__)), "credentialsWritten": False,
              "rankingCertification": False, "httpMethods": ["GET"]}
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": result["status"], "requests": calls, "checks": len(checks), "errors": errors,
                      "warmSearch": warm, "qualityObservations": observations}, ensure_ascii=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    for field in ("package", "baseline", "output"):
        parser.add_argument("--" + field, type=Path, required=True)
    run(parser.parse_args())
