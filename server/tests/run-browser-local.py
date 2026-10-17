"""Development-only UI integration harness; launch under a disposable MySQL environment."""
import os,pathlib,subprocess,tempfile,time,socket,shutil
root=pathlib.Path(tempfile.mkdtemp(prefix='divan-ui-'));env=os.environ.copy();env['DIVAN_UI_ROOT']=str(root);env['DIVAN_UI_TEST']='1'
subprocess.run(['php','server/tests/ui-fixture.php',str(root)],env=env,check=True)
subprocess.run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-keyout',str(root/'key.pem'),'-out',str(root/'cert.pem'),'-days','1','-subj','/CN=127.0.0.1'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=True)
log=(root/'php.log').open('w');server=subprocess.Popen(['php','-S','127.0.0.1:9444','server/tests/ui-router.php'],env=env,stdout=log,stderr=log)
try:
 for _ in range(50):
  try:
   with socket.create_connection(('127.0.0.1',9444),.1):break
  except OSError:time.sleep(.1)
 r=subprocess.run(['node','server/tests/browser.mjs'],env=env);print('UI evidence:',root,flush=True)
 if env.get('DIVAN_UI_ARTIFACTS'):
  out=pathlib.Path(env['DIVAN_UI_ARTIFACTS']);out.mkdir(parents=True,exist_ok=True)
  for file in root.glob('*.png'):shutil.copyfile(file,out/file.name)
 if r.returncode:print((root/'php.log').read_text()[-5000:])
 raise SystemExit(r.returncode)
finally:server.terminate();server.wait(timeout=5)
