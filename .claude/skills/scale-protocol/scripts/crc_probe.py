#!/usr/bin/env python3
"""Timemore 프레임의 CRC 변형을 실기기 캡처로 판별하고, 판별된 변형으로 명령 프레임을 만든다.

원문(§3)은 poly 0x8005, init 0xFFFF, 계산 범위 "CRC 필드를 뺀 전체", 다중 바이트 big-endian만 적고
반사(reflect) 여부와 xorout은 적지 않는다. 이름("CRC-16/IBM")은 보통 init 0x0000을 뜻해 init과도 충돌한다.
그래서 init × 반사 × xorout × CRC 바이트 순서 × 계산 범위를 모두 대입해 캡처와 맞는 조합을 찾는다.

사용법:
  python3 crc_probe.py A55A01010009<payload 9바이트 hex><CRC 4자리> ...   # 프레임 hex 여러 개(RX로 취급)
  python3 crc_probe.py --file _workspace/04_device_probeA.log              # "RX FFF1 <hex>" 줄만 사용, TX 줄은 제외
  python3 crc_probe.py --build 03 0D "" --variant MODBUS --order big --start 0   # 명령 프레임 생성(App→scale)
  python3 crc_probe.py --selftest
종료 코드: 0 = 모든 프레임에 맞는 조합이 정확히 하나, 1 = 없음, 3 = 둘 이상(더 많은 캡처 필요)
"""
import argparse
import re
import sys

HEADER = bytes.fromhex("A55A")
CATALOG = {  # (init, reflected, xorout) → 공개 카탈로그 이름
    (0x0000, True, 0x0000): "ARC",
    (0xFFFF, True, 0x0000): "MODBUS",
    (0xFFFF, True, 0xFFFF): "USB",
    (0x0000, True, 0xFFFF): "MAXIM",
    (0xFFFF, False, 0x0000): "CMS",
    (0x0000, False, 0x0000): "UMTS",
}
VARIANTS = {CATALOG.get((i, r, x), f"init{i:04X}-{'refl' if r else 'norefl'}-xor{x:04X}"): (i, r, x)
            for i in (0x0000, 0xFFFF) for r in (True, False) for x in (0x0000, 0xFFFF)}


def crc16(data, init, reflected, xorout):
    crc = init
    for b in data:
        if reflected:
            crc ^= b
            for _ in range(8):
                crc = (crc >> 1) ^ 0xA001 if crc & 1 else crc >> 1
        else:
            crc ^= b << 8
            for _ in range(8):
                crc = ((crc << 1) ^ 0x8005) & 0xFFFF if crc & 0x8000 else (crc << 1) & 0xFFFF
    return crc ^ xorout


def reassemble(chunks):
    """알림 조각을 이어 붙여 헤더·길이 필드 기준으로 프레임을 잘라 낸다. (프레임 목록, 버린 바이트 수)"""
    buf, frames, dropped = b"".join(chunks), [], 0
    while True:
        i = buf.find(HEADER)
        if i < 0:
            return frames, dropped + len(buf)
        dropped += i
        buf = buf[i:]
        if len(buf) < 6:
            return frames, dropped + len(buf)
        total = 6 + int.from_bytes(buf[4:6], "big") + 2
        if total > 512:
            buf, dropped = buf[1:], dropped + 1
            continue
        if len(buf) < total:
            return frames, dropped + len(buf)
        frames.append(buf[:total])
        buf = buf[total:]


def matches(frame, name, order, start):
    init, refl, xorout = VARIANTS[name]
    return crc16(frame[start:-2], init, refl, xorout) == int.from_bytes(frame[-2:], order)


def probe(chunks):
    frames, dropped = reassemble(chunks)
    print(f"프레임 {len(frames)}개 검사" + (f", 프레임으로 묶이지 않은 바이트 {dropped}개 제외" if dropped else ""))
    if not frames:
        return 1
    full = []
    for name in VARIANTS:
        for order in ("big", "little"):
            for start in (0, 2):
                n = sum(matches(f, name, order, start) for f in frames)
                if n == len(frames):
                    full.append((name, order, start))
                elif n:
                    print(f"  부분 일치 {n}/{len(frames)}: {name} {order} start={start}")
    for name, order, start in full:
        print(f"완전 일치: --variant {name} --order {order} --start {start}")
    if not full:
        print("완전 일치 조합 없음. 조각 결합 순서나 후보 밖 CRC를 의심한다.")
        return 1
    if len(full) > 1:
        print("모호: 다른 명령·다른 값의 캡처를 더 모아 다시 실행한다.")
        return 3
    return 0


def build(opcode, cmd, payload_hex, name, order, start):
    payload = bytes.fromhex(payload_hex)
    body = HEADER + bytes([opcode, cmd]) + len(payload).to_bytes(2, "big") + payload
    init, refl, xorout = VARIANTS[name]
    return body + crc16(body[start:], init, refl, xorout).to_bytes(2, order)


def tokens_from_log(text):
    """'RX FFF1 <hex>' 줄만 쓴다. TX는 가정한 변형으로 만든 프레임이라 판별 근거가 될 수 없다."""
    out = []
    for line in text.splitlines():
        m = re.search(r"\bRX\s+\S+\s+((?:0x)?[0-9A-Fa-f][0-9A-Fa-f\s:-]*)$", line.strip())
        if m:
            out.append(re.sub(r"0x|[\s:-]", "", m.group(1)))
    return out


def selftest():
    expected = {"ARC": 0xBB3D, "MODBUS": 0x4B37, "USB": 0xB4C8, "MAXIM": 0x44C2, "CMS": 0xAEE7, "UMTS": 0xFEE8}
    for name, check in expected.items():  # 공개 CRC 카탈로그의 check 값("123456789")
        got = crc16(b"123456789", *VARIANTS[name])
        assert got == check, f"{name}: {got:#06x} != {check:#06x}"
    assert len(VARIANTS) == 8
    f1 = build(0x01, 0x01, "000003E8006400050" + "0", "MODBUS", "big", 0)
    f2 = build(0x02, 0x0C, "54494D454D4F52452D444F54", "MODBUS", "big", 0)
    frames, dropped = reassemble([b"\x00" + f1 + f2[:10], f2[10:]])  # 쓰레기 바이트, 결합, 분할
    assert frames == [f1, f2] and dropped == 1, (frames, dropped)
    log = f"RX FFF1 {f1.hex()}\nTX FFF2 {build(3, 0x0D, '', 'CMS', 'big', 0).hex()}\nRX FFF1 {'-'.join(f'{b:02X}' for b in f2)}"
    assert tokens_from_log(log) == [f1.hex(), f2.hex().upper()], tokens_from_log(log)
    print("selftest ok")
    return 0


def main():
    p = argparse.ArgumentParser()
    p.add_argument("frames", nargs="*")
    p.add_argument("--file")
    p.add_argument("--build", nargs=3, metavar=("OPCODE", "CMD", "PAYLOAD_HEX"))
    p.add_argument("--variant", choices=VARIANTS)
    p.add_argument("--order", choices=("big", "little"))
    p.add_argument("--start", type=int, choices=(0, 2), default=0)
    p.add_argument("--selftest", action="store_true")
    a = p.parse_args()
    if a.selftest:
        return selftest()
    if a.build:
        if not (a.variant and a.order):
            p.error("--build에는 판별된 --variant와 --order가 필요하다(probe 결과를 그대로 붙여 쓴다)")
        print(build(int(a.build[0], 16), int(a.build[1], 16), a.build[2], a.variant, a.order, a.start).hex().upper())
        return 0
    tokens = [re.sub(r"0x|[\s:-]", "", t) for t in a.frames]
    if a.file:
        with open(a.file, encoding="utf-8") as fh:
            tokens += tokens_from_log(fh.read())
    bad = [t for t in tokens if len(t) % 2 or re.search(r"[^0-9A-Fa-f]", t)]
    for t in bad:
        print(f"건너뜀(hex 아님 또는 홀수 길이): {t[:40]}")
    return probe([bytes.fromhex(t) for t in tokens if t not in bad])


if __name__ == "__main__":
    sys.exit(main())
