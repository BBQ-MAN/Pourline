import re, sys
start = float(sys.argv[2]) if len(sys.argv) > 2 else 0
for line in open(sys.argv[1]):
    m = re.match(r"\s*\+\s*([\d.]+) weight", line)
    if m and float(m.group(1)) >= start:
        print(line.rstrip())
