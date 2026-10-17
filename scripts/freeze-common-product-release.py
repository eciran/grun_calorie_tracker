"""Build-context snapshot of the approved product work, without Git mutations."""
import argparse
import hashlib
import io
import json
import shutil
import subprocess
import zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA='src/main/java/com/grun/calorietracker/'
EXCLUDED={'repository/UserRepository.java','repository/UserDailyActivityRepository.java',
          'security/OwnerErrorCaptureFilter.java','service/impl/AdminUserAnalyticsServiceImpl.java',
          'service/impl/UserServiceImpl.java','service/impl/RecipeServiceImpl.java'}
PREFIXES=('dto/Food','entity/Food','enums/Food','mapper/Food','repository/Food','repository/ProductQuality',
          'service/BrandedProduct','service/ProductQuality','service/impl/AdminFood','service/impl/CatalogPublication',
          'service/impl/Food','service/impl/OpenFoodFacts','service/impl/ProductQuality','service/support/BrandedDuplicate',
          'service/support/Food','service/support/Usda','config/ProductCatalogAudit','controller/AdminProductCatalogAudit',
          'dto/ProductCatalogAudit','entity/ProductCatalogAudit','repository/ProductCatalogAudit',
          'service/impl/ProductCatalogAudit','service/support/ProductCatalogAudit')


def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()


def refresh_fixtures(args):
    destination=args.output.resolve()
    if not destination.is_relative_to(ROOT/'outputs') or not destination.is_dir(): raise ValueError('Owned build snapshot required')
    path=destination.parent/'source-manifest.json'
    manifest=json.loads(path.read_text(encoding='utf-8'))
    if manifest['version']!='COMMON_PRODUCT_RELEASE_SOURCE_V1': raise ValueError('Expected release snapshot')
    assets=['sample-data/food-products-uk-openfoodfacts-small.csv','sample-data/usda-foundation-foods-queries.txt',
            'src/test/java/com/grun/calorietracker/service/FoodProductContributionServiceImplTest.java']
    for file in assets:
        target=destination/file
        target.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(ROOT/file,target)
        manifest['selected']=[row for row in manifest['selected'] if row['file']!=file]+[{'file':file,'sha256':sha(target)}]
    manifest['sourcePath']=str(destination)
    manifest['fixtureRefreshOnly']=True
    path.write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'fixtureFilesCopied':len(assets),'secretsCopied':False}))


def seal(args):
    destination=args.output.resolve()
    if not destination.is_relative_to(ROOT/'outputs') or not destination.is_dir(): raise ValueError('Owned build source required')
    path=destination.parent/'source-manifest.json'
    manifest=json.loads(path.read_text(encoding='utf-8'))
    file=JAVA+'service/impl/RecipeServiceImpl.java'
    original=subprocess.run(['git','show',manifest['baseGitRevision']+':'+file],cwd=ROOT,capture_output=True,text=True,check=True).stdout
    amended=(destination/file).read_text(encoding='utf-8')
    boundary='    private void applyTranslations('
    if original[original.index(boundary):]!=amended[amended.index(boundary):]: raise ValueError('Unapproved recipe translation amendment')
    manifest['partiallyIncluded']={file:{'scope':'snapshot gram/ml/household normalization only; pending translation edits excluded','sha256':sha(destination/file)}}
    manifest['selected']=[{'file':row['file'],'sha256':sha(destination/row['file'])} for row in manifest['selected']]
    manifest['sealedMainSourceHashes']={p.relative_to(destination).as_posix():sha(p) for p in (destination/'src/main').rglob('*') if p.is_file()}
    manifest['sourcePath']=str(destination)
    manifest['sealProducerSha256']=sha(Path(__file__))
    path.write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'status':'ISOLATED_SOURCE_SEALED','mainFiles':len(manifest['sealedMainSourceHashes']),'pendingTranslationsExcluded':True}))


def run(args):
    destination=args.output.resolve()
    if not destination.is_relative_to(ROOT/'outputs') or destination.exists(): raise ValueError('New workspace output required')
    base=subprocess.run(['git','rev-parse','HEAD'],cwd=ROOT,capture_output=True,text=True,check=True).stdout.strip()
    archive=subprocess.run(['git','archive','--format=zip',base,'pom.xml','src'],cwd=ROOT,capture_output=True,check=True).stdout
    destination.mkdir(parents=True)
    with zipfile.ZipFile(io.BytesIO(archive)) as zipped:
        for entry in zipped.infolist():
            target=(destination/entry.filename).resolve()
            if not target.is_relative_to(destination) or entry.is_dir(): continue
            if entry.filename=='src/main/resources/application.yml': raise ValueError('Local config in tracked archive')
            target.parent.mkdir(parents=True,exist_ok=True)
            target.write_bytes(zipped.read(entry))
    status=subprocess.run(['git','status','--porcelain','-uall'],cwd=ROOT,capture_output=True,text=True,check=True).stdout.splitlines()
    paths=[line[3:].replace('\\','/') for line in status]
    selected=[]
    excluded=[]
    for file in paths:
        if file.startswith(JAVA):
            relative=file[len(JAVA):]
            if relative in EXCLUDED or not relative.startswith(PREFIXES): excluded.append(file); continue
        elif file.startswith('src/test/java/'):
            if any(word in file for word in ('AdminUser','OwnerError','RecipeService')): excluded.append(file); continue
        elif file.startswith('src/main/resources/catalog/') or file in (
            'src/main/resources/db/migration/V306__add_resumable_product_catalog_audit.sql',
            'src/main/resources/db/migration/V307__add_reviewed_food_concept_identity.sql',
            'src/main/resources/application-prod.yml','src/main/resources/application-example.yml',
            'src/test/resources/generic-food-approved-seed-v1.csv'):
            pass
        else: excluded.append(file); continue
        source=ROOT/file
        if not source.is_file(): raise ValueError('Selected release file missing')
        target=destination/file
        target.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(source,target)
        selected.append({'file':file,'sha256':sha(source)})
    # Deliberately use the checked-in safe example, never the local secret config.
    shutil.copyfile(destination/'src/main/resources/application-example.yml',destination/'src/main/resources/application.yml')
    required=json.loads((ROOT/'outputs/r5-common-readiness-20261013/common-release/common-plan.json').read_text(encoding='utf-8'))['inputHashes']['runtime']
    for file,value in required.items():
        if sha(ROOT/file)!=value: raise ValueError('Approved live source changed: '+file)
        target=destination/file
        if not target.is_file() or sha(target)!=value:
            target.parent.mkdir(parents=True,exist_ok=True)
            shutil.copyfile(ROOT/file,target)
            if file not in {row['file'] for row in selected}: selected.append({'file':file,'sha256':value})
    if any(not (destination/file).is_file() or sha(destination/file)!=value for file,value in required.items()):
        raise ValueError('Approved common runtime hashes missing from isolated release')
    manifest={'version':'COMMON_PRODUCT_RELEASE_SOURCE_V1','baseGitRevision':base,'selected':selected,'excluded':excluded,
              'resources':{p.relative_to(destination).as_posix():sha(p) for p in (destination/'src/main/resources').rglob('*') if p.is_file()},
              'gitMutations':0,'unrelatedAdminSecurityAnalyticsEditsExcluded':True,'localEnvCopied':False}
    (destination.parent/'source-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'baseGitRevision':base,'productFilesSelected':len(selected),'openPathsExcluded':len(excluded),'output':str(destination),'localEnvCopied':False}))


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--refresh-fixtures',action='store_true')
    parser.add_argument('--seal',action='store_true')
    args=parser.parse_args()
    seal(args) if args.seal else refresh_fixtures(args) if args.refresh_fixtures else run(args)
