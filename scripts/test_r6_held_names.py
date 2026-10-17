import copy
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


spec = importlib.util.spec_from_file_location("names", Path(__file__).with_name("prepare-r6-held-names.py"))
names = importlib.util.module_from_spec(spec)
spec.loader.exec_module(names)
proof = names.module("name_proof_tests", "rehearse-r6-held-names.py")


def fixture():
    body = {"fdcId": 171443, "description": "Mayonnaise, reduced fat, with olive oil", "dataType": "SR Legacy"}
    decision = {"id": "49682", "sourceKey": "USDA_FOODDATA:fdc:171443", "currentName": "Olive Oil",
        "sourceDescription": body["description"], "en": "Reduced-Fat Mayonnaise with Olive Oil", "tr": "Zeytinyağlı Az Yağlı Mayonez",
        "decision": "IDENTITY_CORRECTION", "reason": "Not olive oil", "aliasPolicy": "REMOVE_OIL_ONLY"}
    food = {"id": 49682, "name": "Olive Oil", "display_name": "Olive Oil", "source_key": decision["sourceKey"],
        "catalog_type": "GENERIC_INGREDIENT", "data_source": "USDA_FOODDATA", "is_custom": False,
        "publication_status": "PUBLISHED", "calories": 361, "nutrition_reference_unit": "PER_100G", "preparation_state": "UNSPECIFIED"}
    aliases = [{"id": 1, "food_item_id": 49682, "alias": "Olive Oil", "normalized_alias": "olive oil", "language": "EN", "active": True},
               {"id": 2, "food_item_id": 49682, "alias": "Mayonnaise", "normalized_alias": "mayonnaise", "language": "EN", "active": True},
               {"id": 3, "food_item_id": 49682, "alias": "Old Olive Oil", "normalized_alias": "old olive oil", "language": "EN", "active": False}]
    row = {"id": "49682", "food": food, "evidence": [], "assignments": [], "source_tags": [],
        "name_review": {"aliases": aliases, "localizations": [], "options": [{"id": 88, "quantity": 1, "gram_weight": 15}], "option_localizations": []}}
    previous = {"sourceBodySha256": names.held.everyday.fingerprint(body), "holds": ["CURRENT_NAME_SOURCE_IDENTITY_REVIEW", "FULL_NUTRITION_COMPARISON_REQUIRED"]}
    return row, decision, body, previous, []


class HeldNameTests(unittest.TestCase):
    def test_feta_collision_does_not_require_duplicate_alias_when_current_name_is_exact(self):
        inputs = fixture()
        row, decision, body, previous, conflicts = inputs
        body.update(fdcId=173420, description='Cheese, feta')
        decision.update(id='180991', sourceKey='USDA_FOODDATA:fdc:173420', currentName='Feta Cheese',
                        sourceDescription=body['description'], en='Feta Cheese', tr='Feta Peyniri',
                        decision='EQUIVALENT_SHORT_NAME', aliasPolicy=None)
        row['id'] = '180991'
        row['food'].update(id=180991, source_key=decision['sourceKey'], name='Feta Cheese',
                           display_name='Feta Cheese', short_display_name='Feta Cheese', preparation_state='UNSPECIFIED')
        row['name_review']['aliases'] = []
        previous['sourceBodySha256'] = names.held.everyday.fingerprint(body)
        conflicts.append({'id': 807, 'food_item_id': 179893, 'language': 'EN', 'normalized_alias': 'feta cheese',
                          'alias': 'feta cheese', 'active': True})
        result = names.audit(*inputs)
        self.assertNotIn('CROSS_PRODUCT_ALIAS_COLLISION_REVIEW', result['nameApplicationHolds'])
        self.assertEqual(['TR'], [a['language'] for a in result['aliasActions'] if a['action'].startswith('ADD')])
        self.assertEqual([807], [r['id'] for r in result['crossProductAliasConflicts']])
        self.assertEqual([], result['newAliasCollisionIds'])

    def test_conflicting_alias_stays_held_when_proposed_name_is_not_already_searchable(self):
        inputs = fixture()
        label = inputs[1]['en']
        inputs[4].append({'id': 99, 'food_item_id': 123, 'language': 'EN',
                          'normalized_alias': names.aliases.normalize_alias(label), 'active': True})
        result = names.audit(*inputs)
        self.assertIn('CROSS_PRODUCT_ALIAS_COLLISION_REVIEW', result['nameApplicationHolds'])
        self.assertEqual([], result['redundantAliasesOmitted'])

    def test_redundancy_requires_literal_same_language_label_not_lossy_normalization(self):
        captured = {'localizations': [{'active': True, 'language': 'EN', 'display_name': 'Feta Cheese', 'short_display_name': None}]}
        self.assertTrue(names.already_searchable_exact_label({}, captured, 'EN', 'feta cheese'))
        self.assertFalse(names.already_searchable_exact_label({}, captured, 'TR', 'Feta Cheese'))
        self.assertFalse(names.already_searchable_exact_label({'name': 'Crème Cheese'}, captured, 'EN', 'Creme Cheese'))
        self.assertFalse(names.already_searchable_exact_label({'name': 'Rice-Cream'}, captured, 'EN', 'Rice Cream'))
        captured['localizations'][0]['active'] = False
        self.assertFalse(names.already_searchable_exact_label({}, captured, 'EN', 'Feta Cheese'))


    def test_oil_identity_fix_preserves_food_profile_portions_and_valid_aliases(self):
        inputs = fixture()
        before = copy.deepcopy(inputs)
        result = names.audit(*inputs)
        self.assertEqual(before, inputs)
        self.assertEqual([1], [a["before"]["id"] for a in result["aliasActions"] if a["action"].startswith("DEACTIVATE")])
        self.assertEqual([2, 3], result["preserveAliasIds"])
        self.assertEqual({"name", "display_name", "short_display_name"}, set(result["foodNameChanges"]))
        self.assertFalse(result["nutritionWriteAllowed"])
        self.assertFalse(result["portionWriteAllowed"])
        self.assertFalse(result["liveApplyAllowed"])
        self.assertIn("FULL_NUTRITION_COMPARISON_REQUIRED", result["independentDataPrerequisites"])
        self.assertIn("CANONICAL_IDENTITY_MAP_AND_DEPENDENCY_REVIEW_REQUIRED", result["nameApplicationHolds"])

    def test_breast_and_plain_rice_aliases_are_not_kept_as_valid_source_identity(self):
        self.assertTrue(names.unsafe_alias("REMOVE_BREAST", "Hindi göğsü"))
        self.assertFalse(names.unsafe_alias("REMOVE_BREAST", "Roasted Turkey Light Meat"))
        self.assertTrue(names.unsafe_alias("REMOVE_PLAIN_RICE", "Pişmiş Pirinç"))
        self.assertFalse(names.unsafe_alias("REMOVE_PLAIN_RICE", "Suyla Pişirilmiş Pirinç Kreması"))
        self.assertFalse(names.unsafe_alias("REMOVE_OIL_ONLY", "Zeytinyağlı Mayonez"))
        self.assertTrue(names.unsafe_alias("REMOVE_AYRAN", "Ayran Tozu"))
        self.assertTrue(names.unsafe_alias("REMOVE_WHITE_TUNA", "Albacore Tuna"))

    def test_brand_scope_hold_has_no_name_locale_or_alias_operations(self):
        inputs = fixture()
        inputs[1]["decision"] = "SOURCE_BRAND_SCOPE_HOLD"
        result = names.audit(*inputs)
        self.assertEqual({}, result["foodNameChanges"])
        self.assertEqual([], result["localizationActions"])
        self.assertEqual([], result["aliasActions"])
        self.assertFalse(result["semanticNameReviewResolved"])

    def test_specific_cooking_method_is_not_inferred_from_prepared_description(self):
        inputs = fixture()
        inputs[0]["food"]["preparation_state"] = "BOILED"
        result = names.audit(*inputs)
        self.assertIn("SOURCE_PREPARATION_METHOD_REVIEW_REQUIRED", result["nameApplicationHolds"])
        self.assertFalse(result["preparationReview"]["writeAllowed"])

    def test_equivalence_does_not_require_mutating_names_already_correct(self):
        inputs = fixture()
        inputs[1].update(decision="EQUIVALENT_SHORT_NAME", en="Olive Oil", aliasPolicy=None)
        inputs[0]["food"]["short_display_name"] = "Olive Oil"
        result = names.audit(*inputs)
        self.assertEqual({}, result["foodNameChanges"])
        self.assertFalse(any(a["action"].startswith("DEACTIVATE") for a in result["aliasActions"]))

    def test_inactive_alias_and_locale_never_silently_reactivate(self):
        inputs = fixture()
        inputs[0]["name_review"]["aliases"].append({"id": 9, "food_item_id": 49682, "alias": inputs[1]["tr"],
            "normalized_alias": names.aliases.normalize_alias(inputs[1]["tr"]), "language": "TR", "active": False})
        inputs[0]["name_review"]["localizations"] = [{"id": 2, "language": "TR", "active": False}]
        result = names.audit(*inputs)
        self.assertIn("INACTIVE_ALIAS_REACTIVATION_REQUIRES_APPROVAL_TR", result["nameApplicationHolds"])
        self.assertIn("INACTIVE_LOCALIZATION_REACTIVATION_REQUIRES_APPROVAL_TR", result["nameApplicationHolds"])
        self.assertFalse(any(a.get("language") == "TR" for a in result["aliasActions"]))

    def test_source_or_current_name_drift_blocks_preparation(self):
        for field in ("description", "fdcId", "dataType"):
            inputs = fixture()
            inputs[2][field] = "changed"
            with self.assertRaises(ValueError):
                names.audit(*inputs)
        inputs = fixture()
        inputs[0]["food"]["name"] = "Admin renamed"
        with self.assertRaises(ValueError):
            names.audit(*inputs)

    def test_external_alias_collision_requires_review_not_a_merge(self):
        inputs = fixture()
        inputs[4].append({"id": 700, "food_item_id": 90, "language": "EN", "normalized_alias": names.aliases.normalize_alias(inputs[1]["en"]), "active": True})
        result = names.audit(*inputs)
        self.assertEqual(1, len(result["crossProductAliasConflicts"]))
        self.assertIn("CROSS_PRODUCT_ALIAS_COLLISION_REVIEW", result["nameApplicationHolds"])
        self.assertFalse(result["liveApplyAllowed"])

    def test_already_existing_shared_alias_is_not_an_implicit_new_collision(self):
        inputs = fixture()
        normalized = names.aliases.normalize_alias(inputs[1]["en"])
        inputs[0]["name_review"]["aliases"].append({"id": 10, "food_item_id": 49682,
            "alias": inputs[1]["en"], "normalized_alias": normalized, "language": "EN", "active": True})
        inputs[4].append({"id": 700, "food_item_id": 90, "language": "EN", "normalized_alias": normalized, "active": True})
        result = names.audit(*inputs)
        self.assertEqual(1, len(result["crossProductAliasConflicts"]))
        self.assertEqual([], result["newAliasCollisionIds"])
        self.assertNotIn("CROSS_PRODUCT_ALIAS_COLLISION_REVIEW", result["nameApplicationHolds"])

    def test_checked_equivalence_registry_does_not_approve_unapplied_corrections(self):
        registry = names.held.read_json(names.REGISTRY)
        row = next(r for r in registry["entries"] if r["id"] == "180985")
        food = {"id": int(row["id"]), "source_key": row["sourceKey"], "name": row["currentName"]}
        body = {"fdcId": 172183, "description": row["sourceDescription"], "dataType": "SR Legacy"}
        self.assertTrue(names.held.reviewed_name_identity(food, body, registry))
        body["description"] = "Egg, yolk, raw, fresh"
        self.assertFalse(names.held.reviewed_name_identity(food, body, registry))
        row = next(r for r in registry["entries"] if r["id"] == "49682")
        food = {"id": int(row["id"]), "source_key": row["sourceKey"], "name": row["currentName"]}
        body = {"fdcId": 171443, "description": row["sourceDescription"], "dataType": "SR Legacy"}
        self.assertFalse(names.held.reviewed_name_identity(food, body, registry))
        food["name"] = row["en"]
        food["canonical_food_key"] = row["legacyCanonicalFoodKey"]
        self.assertFalse(names.held.reviewed_name_identity(food, body, registry))
        # A future reviewed identity map is a separate gate; renaming alone
        # cannot validate the legacy olive-oil identity.
        row["acceptedCanonicalFoodKeys"] = ["fixture:reviewed:fdc:171443"]
        food["canonical_food_key"] = "fixture:reviewed:fdc:171443"
        self.assertTrue(names.held.reviewed_name_identity(food, body, registry))
        row = next(r for r in registry["entries"] if r["id"] == "181453")
        food.update(id=int(row["id"]), source_key=row["sourceKey"], name=row["en"])
        body.update(fdcId=170904, description=row["sourceDescription"])
        self.assertFalse(names.held.reviewed_name_identity(food, body, registry))

    def test_unknown_alias_language_is_retained_for_review(self):
        inputs = fixture()
        inputs[0]["name_review"]["aliases"][0]["language"] = "DE"
        result = names.audit(*inputs)
        self.assertIn(1, result["preserveAliasIds"])
        self.assertIn("UNSUPPORTED_LANGUAGE_ALIAS_REVIEW", result["nameApplicationHolds"])

    def test_read_query_is_bounded_read_only_and_quotes_alias_inputs(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "selected.jsonl"
            rows = [{"id": 49682, "requestedAliases": [{"language": "EN", "normalized_alias": "o'brien"}]}]
            path.write_text(json.dumps(rows[0]) + "\n", encoding="utf-8")
            sql = names.collector.name_review_sql(path)
            self.assertIn("REPEATABLE READ READ ONLY", sql)
            self.assertIn("'o''brien'", sql)
            self.assertTrue(sql.rstrip().endswith("ROLLBACK;"))
            self.assertNotIn("__SELECTED_IDS__", sql)
            for identifier in (True, "49682); DELETE", -1):
                rows[0]["id"] = identifier
                path.write_text(json.dumps(rows[0]) + "\n", encoding="utf-8")
                with self.assertRaises(ValueError):
                    names.collector.name_review_sql(path)

    def test_normalized_alias_keeps_tr_label_separate_from_search_key(self):
        self.assertEqual("cig hindi gogsu", names.aliases.normalize_alias("Çiğ Hindi Göğsü"))
        self.assertEqual("Zeytinyağlı Az Yağlı Mayonez", fixture()[1]["tr"])

    def test_rehearsal_selection_omits_holds_brand_scope_and_noops(self):
        row = names.audit(*fixture())
        row["nameApplicationHolds"] = []
        row["status"] = "NAME_ALIAS_PREPARATION_COMPLETE_NOT_APPROVED"
        rows = [copy.deepcopy(row) for _ in range(35)]
        for i, value in enumerate(rows):
            value["id"] = str(i + 1)
        plan = {"version": "R6_HELD_NAME_APPLICATION_PREPARATION_V1", "liveApplyAllowed": False, "rows": rows}
        rows[0]["nameApplicationHolds"] = ["CROSS_PRODUCT_ALIAS_COLLISION_REVIEW"]
        rows[1]["decision"] = "SOURCE_BRAND_SCOPE_HOLD"
        rows[2].update(foodNameChanges={}, localizationActions=[], aliasActions=[])
        self.assertEqual(32, len(proof.candidates(plan)))
        plan["liveApplyAllowed"] = True
        with self.assertRaises(ValueError):
            proof.candidates(plan)

    def test_rehearsal_sql_cannot_change_nutrition_or_escape_owned_database(self):
        row = names.audit(*fixture())
        row["nameApplicationHolds"] = []
        row["status"] = "NAME_ALIAS_PREPARATION_COMPLETE_NOT_APPROVED"
        rows = [copy.deepcopy(row) for _ in range(35)]
        for i, value in enumerate(rows):
            value["id"] = str(i + 1)
            value["expectedBefore"]["id"] = value["id"]
            value["expectedBefore"]["food"]["id"] = i + 1
        plan = {"version": "R6_HELD_NAME_APPLICATION_PREPARATION_V1", "liveApplyAllowed": False, "rows": rows}
        sql = proof.apply_sql(plan, "r6-rehearsal-012345abcdef", "2026-10-13T01:00:00+00:00")
        self.assertIn("current_database()<>'r6_rehearsal'", sql)
        self.assertIn("FOR UPDATE", sql)
        rows[0]["foodNameChanges"]["calories"] = 999
        with self.assertRaises(ValueError):
            proof.apply_sql(plan, "r6-rehearsal-012345abcdef", "2026-10-13T01:00:00+00:00")


if __name__ == "__main__":
    unittest.main()
