import importlib.util
import json
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('live',Path(__file__).with_name('verify-package-sources-live.py'))
live=importlib.util.module_from_spec(spec); spec.loader.exec_module(live)

class LiveTests(unittest.TestCase):
    def test_product_jsonld(self):
        page=live.Page()
        page.feed('<h1>Milk</h1><script type="application/ld+json">{"@type":"Product","name":"Milk 1 L","gtin13":"123"}</script>')
        self.assertEqual(page.headings,['Milk'])
        self.assertEqual(live.products(json.loads(page.blocks[0]))[0]['gtin13'],'123')
    def test_graph(self):
        self.assertEqual(len(live.products({'@graph':[{'@type':['Product'],'name':'X'}]})),1)
    def test_other_types_not_evidence(self):
        self.assertEqual(live.products({'@type':'Organization','name':'Milk'}),[])

if __name__=='__main__': unittest.main()
