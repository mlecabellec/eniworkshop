#!/usr/bin/env python3
"""Traceability & governance auditor inspired by Quasar check_annotations.py and Nunki CS-0010.

Verifies:
1. All first-party Java classes cite structured REQ/FR/TSK/BUG/FIX/CS identifiers.
2. Referenced FR-XXXXX and TSK-XXXXX IDs exist in doc/docs/project/ registries.
"""

import argparse
import json
import pathlib
import re
import sys

REF_PATTERN = re.compile(r"\b(REQ|FR|TSK|BUG|FIX)-\d{5}\b")
CS_PATTERN = re.compile(r"\bCS-\d{4}\b")
FEATURE_ID_PATTERN = re.compile(r"`(FR-\d{5})`")


def load_known_features(features_readme: pathlib.Path) -> set[str]:
    if not features_readme.exists():
        return set()
    return set(FEATURE_ID_PATTERN.findall(features_readme.read_text(encoding="utf-8")))


def load_known_tasks(tasks_dir: pathlib.Path) -> set[str]:
    if not tasks_dir.exists():
        return set()
    return {p.stem for p in tasks_dir.glob("TSK-*.md")}


def audit_repository(root: pathlib.Path) -> dict:
    src_main = root / "src" / "main" / "java"
    src_test = root / "src" / "test" / "java"
    known_features = load_known_features(root / "doc" / "docs" / "project" / "features" / "README.md")
    known_tasks = load_known_tasks(root / "doc" / "docs" / "project" / "tasks")

    total_files = 0
    traced_files = 0
    missing_refs: list[str] = []
    unknown_feature_refs: dict[str, list[str]] = {}
    unknown_task_refs: dict[str, list[str]] = {}
    ref_counts: dict[str, int] = {}

    for base_dir in (src_main, src_test):
        if not base_dir.exists():
            continue
        for java_file in sorted(base_dir.rglob("*.java")):
            if java_file.name == "package-info.java":
                continue
            total_files += 1
            rel = str(java_file.relative_to(root))
            text = java_file.read_text(encoding="utf-8", errors="replace")
            matches = [m.group(0) for m in REF_PATTERN.finditer(text)]
            cs_matches = CS_PATTERN.findall(text)
            if not matches and not cs_matches:
                missing_refs.append(rel)
                continue
            traced_files += 1
            for ref in set(matches):
                ref_counts[ref] = ref_counts.get(ref, 0) + 1
                if ref.startswith("FR-") and known_features and ref not in known_features:
                    unknown_feature_refs.setdefault(ref, []).append(rel)
                if ref.startswith("TSK-") and known_tasks and ref not in known_tasks:
                    unknown_task_refs.setdefault(ref, []).append(rel)

    return {
        "totalJavaFiles": total_files,
        "tracedJavaFiles": traced_files,
        "coveragePercent": round((traced_files / total_files * 100.0) if total_files else 100.0, 2),
        "knownFeatureCount": len(known_features),
        "knownTaskCount": len(known_tasks),
        "missingRefFiles": missing_refs,
        "unknownFeatureRefs": unknown_feature_refs,
        "unknownTaskRefs": unknown_task_refs,
        "refUsageCounts": dict(sorted(ref_counts.items())),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Audit CS-0010 task/feature traceability across ENI Workshop.")
    parser.add_argument("--root", default=".", help="Project root directory (default: .)")
    parser.add_argument("--json", action="store_true", help="Emit JSON audit report")
    parser.add_argument(
        "--strict",
        action="store_true",
        help="Fail with non-zero exit code if any Java class lacks traceability or references unknown FR/TSK",
    )
    args = parser.parse_args()

    report = audit_repository(pathlib.Path(args.root).resolve())
    if args.json:
        print(json.dumps(report, indent=2))
    else:
        print(
            f"Traceability: {report['tracedJavaFiles']}/{report['totalJavaFiles']} "
            f"({report['coveragePercent']}%) | Features={report['knownFeatureCount']} "
            f"| Tasks={report['knownTaskCount']}"
        )
        if report["missingRefFiles"]:
            print("Missing traceability tags:")
            for item in report["missingRefFiles"]:
                print(f"  - {item}")
        if report["unknownFeatureRefs"]:
            print("Unknown Feature references:", report["unknownFeatureRefs"])
        if report["unknownTaskRefs"]:
            print("Unknown Task references:", report["unknownTaskRefs"])

    has_errors = bool(
        report["missingRefFiles"] or report["unknownFeatureRefs"] or report["unknownTaskRefs"]
    )
    return 1 if (args.strict and has_errors) else 0


if __name__ == "__main__":
    sys.exit(main())
