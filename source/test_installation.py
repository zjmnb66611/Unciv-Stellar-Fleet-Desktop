from pathlib import Path
import os,json,hashlib,subprocess
from PIL import Image

R=Path(__file__).resolve().parents[2];P=R/'outputs/Stellar-Fleet-1.0.0';G=R/'outputs/Unciv';W=R/'work/stellar-fleet';F=W/'installation-fixture';F.mkdir(exist_ok=True)
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def run(script,*args):
    env=os.environ.copy();env.pop('PSMODULEPATH',None)
    p=subprocess.run(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(script),*map(str,args)],capture_output=True,env=env)
    text=(p.stdout+p.stderr).decode('utf-8',errors='replace')
    if p.returncode:raise RuntimeError(text)
    return text
if not (F/'Unciv.jar').exists():os.link(G/'Unciv.jar',F/'Unciv.jar')
for folder in ['DongfengOptimization','NuclearOrganization']:
    target=F/'extensions'/folder/'start.ps1';target.parent.mkdir(parents=True,exist_ok=True)
    text=(G/'extensions'/folder/'start.ps1').read_text(encoding='utf-8-sig')
    target.write_text(text,encoding='utf-8-sig')
(F/'Unciv.json').write_bytes((G/'Unciv.json').read_bytes())
protected={p:sha(p) for p in [F/'Unciv.jar',F/'Unciv.json',F/'extensions/DongfengOptimization/start.ps1',F/'extensions/NuclearOrganization/start.ps1']}
run(P/'install.ps1','-GameDirectory',F)
run(F/'extensions/StellarFleet/start.ps1','-GameDirectory',F,'-ValidateOnly')
for folder in ['DongfengOptimization','NuclearOrganization']:
    text=(F/'extensions'/folder/'start.ps1').read_text(encoding='utf-8-sig')
    assert text.count('# StellarFleet optional extension v1')==1
    assert text.index('stellarGuard')<text.index('Write-Output "已验证')
edited=F/'mods/Stellar Fleet/README.md';edited.write_text('用户安装后的自有备注',encoding='utf-8')
backup=max((F/'extension-backups').glob('StellarFleet-*'),key=lambda x:x.stat().st_mtime)
run(P/'rollback.ps1','-GameDirectory',F,'-BackupDirectory',backup)
assert all(sha(path)==value for path,value in protected.items())
assert edited.read_text(encoding='utf-8')=='用户安装后的自有备注'
assert not (F/'extensions/StellarFleet/stellar-fleet-agent.jar').exists()
assets=[]
for file in (P/'mods/Stellar Fleet/Images/UnitIcons').glob('*.png'):
    image=Image.open(file).convert('RGBA');assert image.size==(128,128)
    pixels=list(image.getdata());assert min(a for r,g,b,a in pixels)==0 and max(a for r,g,b,a in pixels)==255
    body=[(r,g,b) for r,g,b,a in pixels if a>240]
    black=sum(max(color)<25 for color in body)/len(body)
    assert black>0.99,(file,black)
    assets.append({'file':file.name,'size':[128,128],'opaqueBlackFraction':black})
report={'passed':True,'nativeGameJarAndConfigUnchanged':True,'bothOldLaunchersRestored':True,'postInstallUserEditPreserved':True,'newAgentRemovedByRollback':True,'gameStarted':False,'assets':assets}
(P/'验证报告/安装回退与素材检查.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(report,ensure_ascii=False))
