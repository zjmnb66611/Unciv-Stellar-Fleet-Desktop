from pathlib import Path
import subprocess,json
ROOT=Path(__file__).resolve().parents[2];G=ROOT/'outputs/Unciv';W=ROOT/'work/stellar-fleet';P=ROOT/'outputs/Stellar-Fleet-1.0.0';M=G/'mods'
java=str(G/'jre/bin/java.exe')
compile=subprocess.run([java,'-jar',str(ROOT/'work/toolchain/ecj-3.40.0.jar'),'-21','-encoding','UTF-8','-cp',str(G/'Unciv.jar')+';'+str(P/'extensions/StellarFleet/stellar-fleet-agent.jar'),'-d',str(W/'test-classes'),str(W/'TestStellarFleet.java')],capture_output=True,encoding='utf-8',errors='replace')
if compile.returncode:print(compile.stdout+compile.stderr);raise SystemExit(1)
profiles={'base':[],'standard':[M/'Huaxia Future Mobile',M/'Extra Buildings',M/'Warfare Expanded Lite'],'Continued':[M/'Future Expansion Continued',M/'Huaxia Future Mobile Continued',M/'Extra Buildings',M/'Warfare Expanded Lite'],'Worlds':[M/'Future Worlds',M/'Huaxia Future Mobile Worlds',M/'Expanded Warfare Syntaxed',M/'Warfare Expanded Lite',M/'Extra Buildings',M/'China Military Science',M/'World Cooperation Organization',M/'Nuclear Deterrence']}
import sys
selected=sys.argv[1:] or ['base']
report={}
for name in selected:
    nuclear=name=='nuclear';profile='Worlds' if nuclear else name
    agents=[G/('extensions/NuclearOrganization/nuclear-organization-agent.jar' if nuclear else 'extensions/DongfengOptimization/dongfeng-agent.jar'),G/'extensions/BulkPurchase/bulk-purchase-agent.jar',P/'extensions/StellarFleet/stellar-fleet-agent.jar']
    command=[java,'-Xmx1G','-Djava.awt.headless=true','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8',*['-javaagent:'+str(a) for a in agents],'-cp',str(G/'Unciv.jar')+';'+str(W/'test-classes'),'TestStellarFleet',*[str(p) for p in profiles[profile]],str(P/'mods/Stellar Fleet')]
    result=subprocess.run(command,capture_output=True,encoding='utf-8',errors='replace',timeout=120)
    text=result.stdout+result.stderr;(P/'验证报告'/f'{name}.txt').write_text(text,encoding='utf-8')
    report[name]={'passed':result.returncode==0,'result':[line for line in text.splitlines() if line.startswith('RESULT')]}
    print(name,json.dumps(report[name],ensure_ascii=False),flush=True)
    if result.returncode:print(text[-4500:],flush=True);break
(P/'验证报告/summary.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
if not all(row['passed'] for row in report.values()):raise SystemExit(1)
