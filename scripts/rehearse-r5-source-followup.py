"""Owned localhost rehearsal of source-validated historical proposals, never live SQL."""
import argparse
import copy
import importlib.util
import json
from datetime import datetime, timedelta, timezone
from pathlib import Path
from types import SimpleNamespace

spec = importlib.util.spec_from_file_location('r5_owned_followup', Path(__file__).with_name('rehearse-r5-controlled-patch.py'))
owned = importlib.util.module_from_spec(spec)
spec.loader.exec_module(owned)


def prepare(path, output):
    source = json.loads(path.read_text(encoding='utf-8'))
    summary = json.loads(path.parent.joinpath('summary.json').read_text())
    if (summary['reviewSha256'] != owned.patch.gate.source.r5.closure.sha(path)
            or source['summary'].get('liveApplyAllowed') is not False
            or source['summary'].get('currentDatabaseMeasured') is not False):
        raise ValueError('Pinned review-only source report required')
    rows = copy.deepcopy([r for r in source['rows'] if r['disposition'] == 'ARCHIVED_CONTRACT_PASS_REBASE_REQUIRED'])
    if not rows or len(rows) > 50: raise ValueError('Bounded source-only projection required')
    for row in rows:
        if (row['status'] != 'REVIEW_ONLY_ARCHIVED_BASELINE' or row['archivedGateStatus'] != 'READY_FOR_BACKUP_REHEARSAL'
                or row.get('applyAllowed') is not False or row.get('changes')
                or row.get('nutritionWriteAllowed') is not False):
            raise ValueError('Append-only historical source proposals required')
        row['status'] = 'READY_FOR_BACKUP_REHEARSAL'  # Disposable fixture only, NOT release approval.
    expiry = (datetime.now(timezone.utc) + timedelta(minutes=20)).isoformat()
    sql, _ = owned.patch.generate(rows, 'r5-srcproof-20261015', expiry)
    guard = "DO $owned$ BEGIN IF current_database() <> 'r5test' THEN RAISE EXCEPTION 'Owned rehearsal database only'; END IF; END $owned$;\n"
    output.mkdir(parents=True, exist_ok=True)
    package = output / 'owned-package'
    package.mkdir(exist_ok=True)
    apply_path = package / 'owned-apply.sql'
    apply_path.write_text('\\set ON_ERROR_STOP on\n' + guard + sql, encoding='utf-8')
    manifest = {'fixtureOnly': True, 'liveApplyAllowed': False, 'expiresAt': expiry,
                'sourceReviewSha256': summary['reviewSha256'], 'batches': [
                    {'operation': 'r5-srcproof-20261015', 'products': len(rows), 'file': apply_path.name,
                     'sha256': owned.patch.gate.source.r5.closure.sha(apply_path)}]}
    package.joinpath('manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    fixture = output / 'owned-projection.json'
    fixture.write_text(json.dumps({'fixtureOnly': True, 'rows': rows}, ensure_ascii=False), encoding='utf-8')
    return SimpleNamespace(review=fixture, package=package, output=output / 'proof')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--source-review', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    owned.run(prepare(args.source_review, args.output))
