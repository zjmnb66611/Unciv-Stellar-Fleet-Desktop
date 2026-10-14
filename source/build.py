from pathlib import Path
import json, hashlib, zipfile, subprocess, shutil

ROOT=Path(__file__).resolve().parents[2];WORK=ROOT/'work/stellar-fleet'
PACK=ROOT/'outputs/Stellar-Fleet-1.0.0';MOD=PACK/'mods/Stellar Fleet';EXT=PACK/'extensions/StellarFleet';GAME=ROOT/'outputs/Unciv'
for folder in [MOD/'jsons/translations',EXT,PACK/'source',PACK/'验证报告']:
    folder.mkdir(parents=True,exist_ok=True)

units=[]
specs=[('SF Victory Star Destroyer','胜利级歼星舰',6,180,200,3,1500,10,3,2,1,1000),('SF Executor Star Dreadnought','执行者歼星舰',6,260,300,3,3000,20,6,4,2,3000),('SF Death Star','死星',4,500,600,6,6000,40,15,5,4,None)]
for name,zh,movement,strength,ranged,reach,cost,iron,uranium,air,ground,gold in specs:
    uniques=[f'Consumes [{iron}] [Iron]',f'Consumes [{uranium}] [Uranium]',f'Can carry [{air}] [Air] units','Can pass through impassable tiles','Ignores terrain cost','Ranged attacks may be performed over obstacles','Never appears as a Barbarian unit','Only available <after discovering [Advanced Ballistics]>','Only available <after discovering [Robotics]>']
    uniques += ['Cannot be purchased'] if gold is None else [f'Can be purchased for [{gold}] [Gold] [in all cities]']
    units.append(dict(name=name,unitType='Ranged Water',movement=movement,strength=strength,rangedStrength=ranged,range=reach,cost=cost,requiredTech='Stealth',uniques=uniques,attackSound='shipguns',civilopediaText=[{'text':f'所有文明可用。信息时代晚期需隐形技术、机器人学和高级弹道学。空军{air}位、陆军或平民共用{ground}位；载员随母舰移动，母舰摧毁时全部载员摧毁。'},{'text':'完整跨地形移动和地面运输需要随包电脑端扩展。登舰/卸载入口位于单位行动栏；地面舱不占原生军事和平民格。'},{'text':f'每回合移动{movement}格，远程攻击射程{reach}格。使用原版远程海军晋升树。资源按原版规则持续占用；未增加爆炸半径。'}]))
def write_json(path,data):path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
write_json(MOD/'jsons/Units.json',units)
write_json(MOD/'jsons/ModOptions.json',{'isBaseRuleset':False,'modUrl':'','author':'Local Unciv project','modSize':1})
(MOD/'jsons/translations/Simplified_Chinese.properties').write_text('\n'.join(f'{row[0]} = {row[1]}' for row in specs)+'\nStellar Fleet = 星际舰队\n',encoding='utf-8')
write_json(MOD/'Atlases.json',['game'])

java=str(GAME/'jre/bin/java.exe');ecj=str(ROOT/'work/toolchain/ecj-3.40.0.jar');asm=ROOT/'work/china-pack/asm-9.7.1.jar'
classes=WORK/'classes';classes.mkdir(exist_ok=True)
sources=[WORK/n for n in ['StellarRuntime.java','StellarUI.java','StellarAgent.java']]
result=subprocess.run([java,'-jar',ecj,'-21','-encoding','UTF-8','-cp',str(GAME/'Unciv.jar')+';'+str(asm),'-d',str(classes),*map(str,sources)],capture_output=True,encoding='utf-8',errors='replace')
print(result.stdout+result.stderr)
if result.returncode:raise SystemExit(result.returncode)
targets=['com/unciv/logic/map/mapunit/MapUnit','com/unciv/logic/map/mapunit/movement/UnitMovement','com/unciv/logic/map/mapunit/movement/MovementCost','com/unciv/models/ruleset/unit/BaseUnit','com/unciv/ui/screens/worldscreen/unit/actions/UnitActions','com/unciv/ui/screens/worldscreen/unit/actions/UnitActionsTable','com/unciv/logic/civilization/managers/UnitManager']
with zipfile.ZipFile(GAME/'Unciv.jar') as game:hashes={name:hashlib.sha256(game.read(name+'.class')).hexdigest() for name in targets}
agent=EXT/'stellar-fleet-agent.jar'
with zipfile.ZipFile(agent,'w',zipfile.ZIP_DEFLATED) as z:
    z.writestr('META-INF/MANIFEST.MF','Manifest-Version: 1.0\r\nPremain-Class: unciv.stellar.StellarAgent\r\nCan-Retransform-Classes: true\r\nImplementation-Version: 1.0.0\r\n\r\n')
    z.writestr('stellar-targets.properties','\n'.join(f'{k}={v}' for k,v in hashes.items()))
    for p in (classes/'unciv/stellar').glob('*.class'):z.write(p,p.relative_to(classes))
    with zipfile.ZipFile(asm) as a:
        for name in a.namelist():
            if name.startswith('org/objectweb/asm/') and name.endswith('.class'):z.writestr(name,a.read(name))
write_json(EXT/'manifest.json',{'version':'1.0.0','gameVersion':'4.22.6-patch1','gameSha256':hashlib.sha256((GAME/'Unciv.jar').read_bytes()).hexdigest(),'agentSha256':hashlib.sha256(agent.read_bytes()).hexdigest(),'androidSupported':False,'loadOrder':'after existing Dongfeng/Nuclear and BulkPurchase agents','targets':hashes})
for p in sources:shutil.copyfile(p,PACK/'source'/p.name)
print('Built',agent)
