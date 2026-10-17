#!/usr/bin/env python3
"""Build a source-only cPanel archive. Never reads live config, media, keys, or DB."""
import argparse, hashlib, json, pathlib, re, subprocess, zipfile
p=argparse.ArgumentParser();p.add_argument('--output',required=True);p.add_argument('--php',default='php');a=p.parse_args()
repo=pathlib.Path(__file__).resolve().parents[2];base='ac784013c9a81d09b4c83dbe301cb4ed6398beaf'
def git(*args):return subprocess.check_output(['git','-C',str(repo),*args])
if git('status','--porcelain','--untracked-files=no').strip():raise SystemExit('Commit/synchronize tracked source changes before packaging.')
files=git('ls-files','-z','server/public').decode().split('\0');entries={}
for name in filter(None,files):
 rel=pathlib.PurePosixPath(name).relative_to('server/public');parts=rel.parts
 # Explicit state exclusion even if a future developer accidentally tracks a live file.
 if parts[0] in ('storage','media') and rel.name not in ('.htaccess','index.html'):continue
 if rel.name in ('config.php','install.lock') or rel.suffix in ('.pem','.log','.dvbk'):raise SystemExit('Refusing sensitive file: '+str(rel))
 entries['divan/'+str(rel)]=(repo/name).read_bytes()
for file in sorted((repo/'server/docs/telegram').glob('*.md')):entries['docs/'+file.name]=file.read_bytes()
code="define('DIVAN',true);require 'server/public/app/telegram/schema.php';echo json_encode(TgSchema::sql());"
statements=json.loads(subprocess.check_output([a.php,'-r',code],cwd=repo))
sql='-- Optional: existing installation with migrations 1..11 only. Default prefix dv_.\n-- Change every dv_ prefix to the existing configured prefix before import.\n-- Normal PHP upgrade already performs this migration. No DROP/DELETE/ALTER.\nSET NAMES utf8mb4;\n\n'
for statement in statements:sql+=re.sub(r'\{([a-z_]+)\}',r'`dv_\1`',statement)+';\n\n'
sql+="INSERT IGNORE INTO `dv_migrations` (version,applied_at) SELECT 12,UTC_TIMESTAMP() WHERE (SELECT COUNT(*) FROM `dv_migrations` WHERE version BETWEEN 1 AND 11)=11;\n"
entries['migration-12.sql']=sql.encode()
entries['source-changes.patch']=git('diff','--binary',base,'--','server','.github/workflows/telegram-tests.yml')
entries['PACKAGE.json']=json.dumps({'name':'Divan Telegram','version':'1.0.0-rc1','base_commit':base,'source_commit':git('rev-parse','HEAD').decode().strip(),'source_tree':git('write-tree').decode().strip(),'migration':12,'production_deployed':False,'install_guide':'docs/INSTALL.fa.md','status':'staging candidate; see test report and limitations'},ensure_ascii=False,indent=2).encode()
entries['SHA256SUMS.txt']=''.join(hashlib.sha256(data).hexdigest()+'  '+name+'\n' for name,data in sorted(entries.items())).encode()
out=pathlib.Path(a.output).resolve();out.mkdir(parents=True,exist_ok=True);target=out/'Divan-Telegram-v1.0.0-rc1-cPanel.zip'
with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
 for name,data in sorted(entries.items()):
  info=zipfile.ZipInfo(name,(2026,10,17,0,0,0));info.external_attr=0o100644<<16;info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,data)
with zipfile.ZipFile(target) as z:
 assert z.testzip() is None
 assert 'divan/app/telegram/auth.php' in z.namelist() and 'divan/storage/.htaccess' in z.namelist()
 assert not any(n.endswith(('storage/config.php','.pem','install.lock')) for n in z.namelist())
checksum=hashlib.sha256(target.read_bytes()).hexdigest();(out/'SHA256SUMS.txt').write_text(checksum+'  '+target.name+'\n')
print(json.dumps({'file':str(target),'bytes':target.stat().st_size,'files':len(entries),'sha256':checksum}))
