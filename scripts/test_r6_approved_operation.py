import copy
import importlib.util
import json
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("ops", Path(__file__).with_name("r6-approved-catalog-operation.py"))
ops = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ops)


class ApprovedOperationTests(unittest.TestCase):
    def package(self):
        registry = json.loads((ops.prepare.ROOT / "data/catalog/everyday-category-review-r6-v1.json").read_text(encoding="utf-8"))
        audits = []
        for identifier in ("181008", "181466"):
            decision = next(d for d in registry["usdaNames"] if d["id"] == identifier)
            aliases = [(1036, "chicken", "EN"), (2530, "Fırında tavuk göğsü", "TR")] if identifier == "181008" else [
                (2861, "yumurta beyazı omleti", "TR"), (2862, "çırpılmış yumurta beyazı", "TR"),
                (2863, "egg white omelet", "EN"), (2864, "scrambled egg white", "EN")]
            food = {"id": int(identifier), "source_key": decision["sourceKey"], "name": decision["currentName"],
                    "display_name": decision["currentName"], "short_display_name": decision["currentName"],
                    "calories": 100, "nutrition_reference_unit": "GRAM", "preparation_state": "COOKED"}
            row = {"id": identifier, "food": food, "assignments": [], "source_tags": [], "evidence": [], "name_review": {
                "aliases": [{"id": i, "food_item_id": int(identifier), "alias": name, "normalized_alias": ops.prepare.normalize_alias(name),
                             "language": language, "alias_type": "ADMIN_MANUAL", "source": "admin-import", "active": True,
                             "created_at": "2026-10-11T18:00:00"} for i, name, language in aliases],
                "localizations": [{"id": i, "food_item_id": int(identifier), "language": language, "active": True,
                                   "display_name": decision["currentName"], "short_display_name": decision["currentName"],
                                   "source": "admin-import", "created_at": "2026-10-11T18:00:00", "updated_at": "2026-10-11T18:00:00"}
                                  for i, language in ((1, "EN"), (2, "TR"))],
                "options": [] if identifier == "181008" else [{"id": 3264, "gram_weight": 33}], "option_localizations": []}}
            source = {"fdcId": int(decision["sourceKey"].rsplit(":", 1)[-1]), "description": decision["sourceDescription"]}
            audits.append(ops.prepare.audit_name(row, decision, source))
        return {"operation": "r6-approved-012345abcdef", "expiresAt": "2026-10-12T23:00:00+00:00", "aliasAudit": audits,
                "categories": {}, "categoryPlan": {"rows": [{"id": str(i), "targetCategory": {"id": 1},
                    "expectedBefore": {"food": {"id": i}, "source_tags": [], "evidence": []}} for i in range(1, 579)]}}

    def receipts(self, package):
        rows = []
        for audit in package["aliasAudit"]:
            row = copy.deepcopy(audit["expectedBefore"])
            row["food"].update(audit["proposedFoodNames"])
            for locale in row["name_review"]["localizations"]:
                locale.update(display_name=audit["proposedLocalizations"][locale["language"]],
                              short_display_name=audit["proposedLocalizations"][locale["language"]], updated_at="2026-10-12T18:00:00")
            for change in audit["aliasActions"]:
                next(a for a in row["name_review"]["aliases"] if a["id"] == change["before"]["id"])["active"] = False
                if change["replaceWith"]:
                    row["name_review"]["aliases"].append({"id": 1000000 + change["before"]["id"], "food_item_id": int(audit["id"]),
                        **change["replaceWith"], "source": "r6_name_review_v1", "alias_type": "ADMIN_MANUAL", "active": True,
                        "created_at": "2026-10-12T18:00:00"})
            row["name_review"]["aliases"].sort(key=lambda a: a["id"])
            rows.append(row)
        return rows

    def test_name_complete_receipt_and_undo(self):
        package = self.package()
        rows = self.receipts(package)
        ops.validate_names(package, rows)
        sql = ops.name_undo(package, rows)
        self.assertIn("Exact naming rollback failed", sql)
        self.assertNotIn("DELETE FROM food_items", sql)
        self.assertNotIn("UPDATE food_item_serving_options", sql)

    def test_names_reject_nutrition_unit_source_preparation_drift(self):
        package = self.package()
        for field, value in (("calories", 900), ("nutrition_reference_unit", "ML"), ("source_key", "other"), ("preparation_state", "RAW")):
            rows = self.receipts(package)
            rows[0]["food"][field] = value
            with self.assertRaises(ValueError):
                ops.validate_names(package, rows)

    def test_names_reject_missing_extra_or_changed_alias(self):
        package = self.package()
        for mutate in (lambda r: r[0]["name_review"]["aliases"].pop(),
                       lambda r: r[1]["name_review"]["aliases"][-1].update(alias="wrong"),
                       lambda r: r[0]["name_review"]["aliases"][0].update(active=False)):
            rows = self.receipts(package)
            mutate(rows)
            with self.assertRaises(ValueError):
                ops.validate_names(package, rows)

    def test_names_reject_option_or_locale_changes(self):
        package = self.package()
        for mutate in (lambda r: r[1]["name_review"]["options"][0].update(gram_weight=99),
                       lambda r: r[0]["name_review"]["localizations"][0].update(source="other")):
            rows = self.receipts(package)
            mutate(rows)
            with self.assertRaises(ValueError):
                ops.validate_names(package, rows)

    def test_live_gates_and_row_locks(self):
        sql = ops.name_apply(self.package())
        for required in ("current_database()<>'grun_calorie_db'", "grun.r6.operation", "IS DISTINCT FROM '305'",
                         "Approved package expired", "READ COMMITTED", "pg_advisory_xact_lock(17491,310)",
                         "FOR UPDATE OF a", "Exact name/alias/portion state drift"):
            self.assertIn(required, sql)
        self.assertNotIn("pg_advisory_xact_lock(190713,", sql)
        self.assertNotIn("ON CONFLICT", sql)

    def test_categories_cannot_include_name_ids_or_wrong_count(self):
        package = self.package()
        package["categoryPlan"]["rows"][0]["id"] = "181008"
        with self.assertRaises(ValueError):
            ops.category_apply(package)
        package = self.package()
        package["categoryPlan"]["rows"].pop()
        with self.assertRaises(ValueError):
            ops.category_apply(package)

    def test_category_apply_is_assignment_only(self):
        sql = ops.category_apply(self.package())
        self.assertIn("Existing assignment/repeated apply", sql)
        self.assertIn("<>578", sql)
        for forbidden in ("UPDATE food_items", "DELETE FROM", "UPDATE food_categories", "ON CONFLICT"):
            self.assertNotIn(forbidden, sql)

    def test_category_undo_rejects_unowned_or_wrong_category(self):
        package = self.package()
        receipts = [{"food_item_id": int(r["id"]), "category_id": r["targetCategory"]["id"], "primary_category": True,
                     "assignment_source": "ADMIN", "reviewed": True, "confidence_score": None,
                     "created_by": package["operation"], "updated_by": package["operation"]} for r in package["categoryPlan"]["rows"]]
        ops.category_undo(package, receipts)
        for mutate in (lambda r: r.pop(), lambda r: r[0].update(updated_by="admin"), lambda r: r[0].update(category_id=999)):
            rows = copy.deepcopy(receipts)
            mutate(rows)
            with self.assertRaises(ValueError):
                ops.category_undo(package, rows)


if __name__ == "__main__":
    unittest.main()
