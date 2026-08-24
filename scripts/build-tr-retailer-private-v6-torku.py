#!/usr/bin/env python3
"""Build v6 by reusing the validated free-web overlay builder with a v5 base."""

from __future__ import annotations

import argparse
import importlib.util
import sys
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--v5-rich", type=Path, required=True)
    parser.add_argument("--candidates", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()

    builder_path = Path(__file__).with_name("build-tr-retailer-private-v5-web.py")
    spec = importlib.util.spec_from_file_location("private_web_builder", builder_path)
    if spec is None or spec.loader is None:
        raise SystemExit("Unable to load validated free-web builder")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    previous_argv = sys.argv
    try:
        sys.argv = [
            str(builder_path), "--v4-rich", str(args.v5_rich),
            "--candidates", str(args.candidates), "--output-dir", str(args.output_dir),
        ]
        result = int(module.main())
    finally:
        sys.argv = previous_argv
    source = args.output_dir / "tr-retailer-test-rich-v5-import.csv"
    target = args.output_dir / "tr-retailer-test-rich-v6-import.csv"
    source.replace(target)
    return result


if __name__ == "__main__":
    raise SystemExit(main())
