import re, sys
rows = []
for line in open(sys.argv[1], errors="replace"):
    m = re.match(r"\s*([\d.]+)\s+\d+\s+\d+\s+\w\s+TimemoreBle:\s+(RX FFF1|TX FFF2|EV)\s*(.*)", line)
    if m:
        rows.append((float(m[1]), m[2], m[3]))
t0 = rows[0][0]
prev_rx = None
for t, k, r in rows:
    if k == "EV" and ("연결" in r or "close" in r):
        print(f"+{t-t0:8.3f} EV {r}   (last RX before: +{(prev_rx or t0)-t0:.3f}, gap {t-(prev_rx or t0):.3f}s)")
    if k == "RX FFF1":
        prev_rx = t
