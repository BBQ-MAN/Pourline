"""B10 judge: reassembled 0x01 frames from first RX after connection to disconnect EV or +600 s."""
import re, sys
rows = []
for line in open(sys.argv[1], errors="replace"):
    m = re.match(r"\s*([\d.]+)\s+\d+\s+\d+\s+\w\s+TimemoreBle:\s+(RX FFF1|TX FFF2|EV)\s*(.*)", line)
    if m:
        rows.append((float(m[1]), m[2], m[3]))
conn = next(t for t, k, r in rows if k == "EV" and r.startswith("연결됨"))
first = next(t for t, k, r in rows if k == "RX FFF1" and t >= conn)
disc = next((t for t, k, r in rows if k == "EV" and t > first and ("connected=false" in r or "close" in r or "블루투스 꺼짐" in r)), None)
end = min(first + 600, disc) if disc else first + 600
n = 0; last = None; gaps = []
for t, k, r in rows:
    if k == "RX FFF1" and first <= t <= end:
        n += sum(1 for p in r.split("A55A")[1:] if p[:4] == "0101")
        if last is not None and t - last > 0.3:
            gaps.append(round(t - last, 3))
        last = t
span = end - first
print(f"window {span:.1f} s (disconnect={'yes' if disc and disc <= first + 600 else 'no'})")
print(f"0x01 frames {n}, expected {span*10:.0f}, ratio {n/(span*10):.4f} -> {'PASS' if n/(span*10) >= 0.99 else 'FAIL'} (>= 0.99)")
print(f"RX gaps > 0.3 s: {len(gaps)} {sorted(gaps)[-5:]}")
