#!/usr/bin/env python3
"""WCAG 2.x 명암비 계산. 디자인 토큰의 hex 색 쌍을 받아 비율과 AA 판정을 출력한다.

사용법:
  python3 contrast.py 1C1B1F FFFBFE                  # 전경 배경
  python3 contrast.py 1C1B1F:FFFBFE E6E1E5:1C1B1F    # 여러 쌍
  python3 contrast.py --selftest
"""
import sys


def luminance(hex_color):
    h = hex_color.lstrip("#")
    if len(h) != 6:
        raise ValueError(f"6자리 hex가 아니다: {hex_color}")

    def channel(c):
        c = int(c, 16) / 255
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4

    r, g, b = (channel(h[i:i + 2]) for i in (0, 2, 4))
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def ratio(fg, bg):
    a, b = sorted((luminance(fg), luminance(bg)), reverse=True)
    return (a + 0.05) / (b + 0.05)


def main(argv):
    if argv == ["--selftest"]:
        assert round(ratio("000000", "FFFFFF"), 2) == 21.0
        assert round(ratio("777777", "FFFFFF"), 2) == 4.48
        print("selftest ok")
        return 0
    pairs = [a.split(":") for a in argv] if argv and all(":" in a for a in argv) else [argv[i:i + 2] for i in range(0, len(argv), 2)]
    if not pairs or any(len(p) != 2 for p in pairs):
        print(__doc__)
        return 2
    for fg, bg in pairs:
        r = ratio(fg, bg)
        print(f"{fg} on {bg}: {r:.2f}:1  본문(4.5) {'통과' if r >= 4.5 else '실패'}  큰 글자·UI(3.0) {'통과' if r >= 3 else '실패'}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
