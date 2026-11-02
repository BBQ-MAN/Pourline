# 워크플로 보고서: python3 -I wfreport.py <output.json> <runId> <date> <note> <status-map-json>
import json,sys
src,run,date,note,smap=sys.argv[1:6]; smap=json.loads(smap)
d=json.load(open(src)); r=d["result"]; r=json.loads(r) if isinstance(r,str) else r
L=[f"# 구현 보고 {run} ({date})","",note,"","## 진행 로그",""]+["- "+x for x in d.get("logs",[])]
for m in r["report"]:
    L+=["",f"## {m['id']} — {m['status']} → milestones.json `{smap.get(m['id'],m['status'])}`","",f"- rounds {m.get('rounds')}, missingReviewers {m.get('missingReviewers')}, 스크린샷 {len(m.get('screenshots',[]))}장","","### 완료 조건",""]
    for a in m.get("acceptance",[]): L.append(f"- **{a['result']}** — {a['criterion'][:170]}"+(f"\n  - {a['note'][:300]}" if a.get('note') and a['result']!='pass' else ""))
    for k,t in [("residual","남은 지적(차단)"),("uncertain","불확실"),("lowFindings","low 지적"),("rejected","구현자 거부")]:
        v=m.get(k) or []
        L+=["",f"### {t} ({len(v)})",""]
        for f in v:
            L.append(f"- [{f.get('severity','')}/{f.get('lens','')}] {f['title'][:200]} — `{str(f.get('file',''))[:150]}`")
            if k=="residual": L.append(f"  - 수정안: {f.get('fix','')[:500]}")
            if k=="rejected": L.append(f"  - 근거: {f.get('reason','')[:600]}")
    L+=["","### 구현 요약","",m.get("summary","")]
open(f"_workspace/03_report_{run}.md","w").write("\n".join(L)+"\n")
