import copy
import base64
import gzip
import hashlib
import json
import unittest
from pathlib import Path
from importlib.util import module_from_spec,spec_from_file_location

spec=spec_from_file_location('approved_common_tests',Path(__file__).with_name('approved-common-catalog-operation.py'))
p=module_from_spec(spec)
spec.loader.exec_module(p)


def manifest():
    return {'version':'APPROVED_COMMON_R5_R6_OPERATION_V1','approvedScope':'EXACT_35_COMBINED_CANDIDATES_AND_2_PROTECTED_COMPANIONS',
            'operation':'common-approved-123456abcdef','pairDigest':'a'*64,'schemaVersion':'307',
            'expiresAt':'2099-01-01T00:00:00+00:00'}


class ApprovedCommonTests(unittest.TestCase):
    def test_combined_receipt_has_no_kind_but_transport_checksum_is_strict(self):
        receipt={key:[] for key in ('rows','commonReceipt','dependencies','optionDependencies','newOptions')}
        receipt['owner']='common-approved-123456abcdef'
        body=(json.dumps(receipt)+'\n').encode()
        records=[{'kind':'transport','chunks':1,'sha256':hashlib.sha256(body).hexdigest()},
                 {'kind':'chunk','sequence':1,'data':base64.b64encode(gzip.compress(body)).decode()}]
        self.assertEqual([receipt],p.decode_receipts(records))
        with self.assertRaises(ValueError): p.decode_receipts(records+[records[1]])
        records[0]['sha256']='0'*64
        with self.assertRaises(ValueError): p.decode_receipts(records)

    def test_explicit_database_session_digest_expiry_and_transaction_lock(self):
        sql=p.guard(manifest())
        for value in ('grun_calorie_db','grun.common.operation','grun.common.pair','common-approved-123456abcdef',
                      'Approved operation expired','pg_advisory_xact_lock(17491,310)','statement_timeout','lock_timeout'):
            self.assertIn(value,sql)
        self.assertNotIn('pg_advisory_xact_lock(190713,',sql)
        self.assertNotIn('r6_rehearsal',sql)
        self.assertNotIn('grun.rehearsal.owner',sql)

    def test_wrong_scope_version_identity_or_digest_refused(self):
        for key,value in [('version','wrong'),('approvedScope','FULL_CATALOG'),('operation','r6-rehearsal-123456abcdef'),
                          ('operation',"x'; DELETE FROM food_items;"),('pairDigest','unknown'),('schemaVersion','306'),('schemaVersion','308')]:
            value_manifest=manifest()
            value_manifest[key]=value
            with self.assertRaises(ValueError): p.guard(value_manifest)

    def test_expiry_must_be_timezone_aware(self):
        value=manifest()
        value['expiresAt']='2099-01-01T00:00:00'
        with self.assertRaises(ValueError): p.guard(value)

    def test_original_rehearsal_prefix_still_rejects_production_marker(self):
        with self.assertRaises(ValueError):
            p.pair.common.names.fixture.prepare.prefix(manifest()['operation'],manifest()['expiresAt'])

    def test_guard_does_not_change_manifest(self):
        value=manifest()
        before=copy.deepcopy(value)
        p.guard(value)
        self.assertEqual(before,value)


if __name__=='__main__': unittest.main()
