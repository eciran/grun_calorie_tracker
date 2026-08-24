# TR-BULK-A101-FROZEN-NUTRITION-20260807T1159Z

Result: `PASS_FROZEN_AUDIT_ZERO_NUTRITION`

Audited all 219 v9-net-new A101 targets against exact-GTIN alternate frozen records. Sixteen source files and 289 references were scanned; all 289 resolved to the exact GTIN, but none contained explicit, complete and plausible per-100 kcal/protein/fat/carbs. No promotion. PRIVATE_TEST_ONLY v9 remains 2,867 rich unique products, 7,133 short of 10,000.

Command: `C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe scripts/audit-tr-frozen-cross-source-nutrition.py --queue outputs/product-catalog-aug08/tr-bulk-a101-cross-source-v1-20260807/cross-source-targets.json --out outputs/product-catalog-aug08/tr-bulk-a101-frozen-nutrition-v1-20260807`

Counts: targets 219; files 16; references 289; exact-GTIN rows 289; nutrition-complete 0; net-new 0; rejected/missing 219; collisions 0.

Hashes: script `050385E91CAE311331632E68F3BC0499C9649CE9B5081D2613F9B26439E3C8E0`; candidates `A5338D955B09046EC0B16F3A9625B7955C763AAE07DC722E474E6078745F932F`; review `15B0D06E002B77E6A7C7FAD0F6FB5C426350E6D98074096A911EBEC12A87C764`; report `18A437E27E710FEB779A5D4C1CBF10139919B71B174E0135904D038DBE4F9280`.

Next: bounded checkpointed IYAS page parser, then Cagri/Gurmar/Sariyer if needed.
