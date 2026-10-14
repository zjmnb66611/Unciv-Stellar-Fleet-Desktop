from pathlib import Path
import subprocess,json
R=Path(__file__).resolve().parents[2];G=R/'outputs/Unciv';P=R/'outputs/Stellar-Fleet-1.0.0';W=R/'work/stellar-fleet'
star=P/'extensions/StellarFleet/stellar-fleet-agent.jar';bulk=G/'extensions/BulkPurchase/bulk-purchase-agent.jar';df=G/'extensions/DongfengOptimization/dongfeng-agent.jar';j36=G/'mods/Huaxia Future Pack/desktop/j36-agent.jar'
summary=json.loads((P/'验证报告/summary.json').read_text(encoding='utf-8'))
for name,agents in [('standalone',[star]),('legacy-agent-order',[df,bulk,j36,star])]:
    command=[str(G/'jre/bin/java.exe'),'-Xmx1G','-Djava.awt.headless=true','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8',*['-javaagent:'+str(p) for p in agents],'-cp',str(G/'Unciv.jar')+';'+str(W/'test-classes')+';'+str(bulk),'TestStellarFleet',str(P/'mods/Stellar Fleet')]
    result=subprocess.run(command,capture_output=True,encoding='utf-8',errors='replace',timeout=90);text=result.stdout+result.stderr
    (P/'验证报告'/f'{name}.txt').write_text(text,encoding='utf-8')
    summary[name]={'passed':result.returncode==0,'result':[line for line in text.splitlines() if line.startswith('RESULT')],'note':'standalone使用购买业务类做交易测试，但不加载购买代理或其他代理' if name=='standalone' else '按本机旧增强入口的实际代理顺序加载'}
    print(name,json.dumps(summary[name],ensure_ascii=False))
    if result.returncode:print(text[-4000:]);raise SystemExit(1)
(P/'验证报告/summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
