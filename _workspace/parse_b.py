"""Parse a Pourline TimemoreBle logcat capture: frame counts by cmd, weight timeline, responses."""
import re, sys, collections
f = sys.argv[1]
frames = []  # (t, hex)
evs = []
for line in open(f, errors="replace"):
    m = re.match(r"\s*([\d.]+)\s+\d+\s+\d+\s+\w\s+TimemoreBle:\s+(RX FFF1|TX FFF2|EV)\s*(.*)", line)
    if not m:
        continue
    t, kind, rest = float(m[1]), m[2], m[3].strip()
    if kind == "EV":
        evs.append((t, rest)); continue
    for p in [x for x in rest.split("A55A") if x]:
        frames.append((t, kind[:2], "A55A" + p))
t0 = min([x[0] for x in frames] + [e[0] for e in evs])
print("events:")
for t, e in evs: print(f"  +{t-t0:7.3f} {e}")
cnt = collections.Counter((k, fr[4:8]) for _, k, fr in frames)
print("frame counts (dir, opcode+cmd):", dict(cnt))
print("non-weight frames:")
for t, k, fr in frames:
    if fr[4:8] != "0101":
        print(f"  +{t-t0:7.3f} {k} {fr}")
w = [(t, k, fr) for t, k, fr in frames if fr[4:8] == "0101" and k == "RX"]
if w:
    span = w[-1][0] - w[0][0]
    print(f"0x01 frames: {len(w)} over {span:.1f}s = {len(w)/max(span,1e-9):.2f} Hz")
    last = None
    for t, k, fr in w:
        pl = fr[12:12 + 18]  # length 0009 -> 9 bytes
        wt = int(pl[0:8], 16); wt = wt - (1 << 32) if wt & 0x80000000 else wt
        flow = int(pl[8:12], 16); timer = int(pl[12:18], 16) if len(pl) >= 18 else None
        key = (wt, flow)
        if key != last:
            print(f"  +{t-t0:7.3f} weight {wt/10:.1f} g flow {flow/10:.1f} g/s timer {timer}")
            last = key
