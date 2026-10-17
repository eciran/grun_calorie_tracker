import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REGISTRY = ROOT / "data/catalog/native-catalog-writer-registry-v1.json"


class NativeCatalogWriterFenceTests(unittest.TestCase):
    def test_every_registered_production_writer_uses_application_fence(self):
        registry = json.loads(REGISTRY.read_text(encoding="utf-8"))
        self.assertEqual("NATIVE_CATALOG_WRITER_REGISTRY_V1", registry["version"])
        self.assertEqual("pg_advisory_xact_lock(17491,310)", registry["commonFence"])
        self.assertEqual(3, len(registry["activeProductionWriters"]))
        for relative in registry["activeProductionWriters"]:
            with self.subTest(writer=relative):
                source = (ROOT / relative).read_text(encoding="utf-8")
                self.assertIn("BEGIN ISOLATION LEVEL READ COMMITTED", source)
                self.assertIn(registry["commonFence"], source)
                self.assertNotIn("pg_advisory_xact_lock(190713,", source)

    def test_category_mapping_boundary_is_explicit(self):
        boundary = json.loads(REGISTRY.read_text(encoding="utf-8"))["runtimeCategoryMapping"]
        self.assertFalse(boundary["adminCrudWriterPresent"])
        self.assertEqual("Flyway migrations", boundary["sourceMappingsManagedBy"])
        self.assertEqual("MANDATORY", boundary["resolverTransaction"])
        self.assertEqual(
            {"FoodProductImportServiceImpl", "FoodCategoryBackfillBatchService"},
            set(boundary["productAssignmentsManagedBy"]),
        )


if __name__ == "__main__":
    unittest.main()
