import re, sys
rows = []
for line in open(sys.argv[1]):
    m = re.match(r"\s*\+\s*([\d.]+) weight (-?[\d.]+) g flow ([\d.]+)", line)
    if m:
        rows.append(tuple(map(float, m.groups())))
for a, b in [tuple(map(float, x.split(':'))) for x in sys.argv[2:]]:
    seg = [r for r in rows if a <= r[0] <= b]
    if seg:
        print(f"{a:.0f}-{b:.0f}s: start {seg[0][1]:.1f} g, end {seg[-1][1]:.1f} g, max {max(r[1] for r in seg):.1f} g, min {min(r[1] for r in seg):.1f} g, max flow {max(r[2] for r in seg):.1f}")
