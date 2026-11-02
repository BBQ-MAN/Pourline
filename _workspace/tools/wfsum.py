# 워크플로 결과 요약: python3 -I wfsum.py <output.json>
import json,sys
from collections import Counter
d=json.load(open(sys.argv[1])); r=d["result"]; r=json.loads(r) if isinstance(r,str) else r
print("\n".join(d.get("logs",[])))
for m in r["report"]:
    print("\n==",m["id"],m["status"],"rounds",m.get("rounds"),"missing",m.get("missingReviewers"),m.get("reason",""))
    print(" acc",dict(Counter(a["result"] for a in m.get("acceptance",[]))))
    for a in m.get("acceptance",[]):
        if a["result"] not in("pass","unverified"): print("  ACC",a["result"],a["criterion"][:90],"|",(a.get("note") or "")[:400])
    for f in m.get("residual",[]):
        print("  RES",f.get("severity"),f.get("lens"),f["title"][:150],"| carried" if f.get("carried") else "")
        print("     file:",str(f.get("file"))[:150]); print("     fix:",f.get("fix","")[:350])
    for f in m.get("uncertain",[]): print("  UNC",f["title"][:150])
    for f in m.get("rejected",[]): print("  REJ",f["title"][:120],"::",f.get("reason","")[:350])
    print("  LOW",len(m.get("lowFindings",[])),"shots",len(m.get("screenshots",[])))
    print("  SUM",m.get("summary","")[:1200])
